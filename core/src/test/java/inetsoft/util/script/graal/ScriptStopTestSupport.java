/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util.script.graal;

import inetsoft.sree.SreeEnv;
import inetsoft.util.script.ScriptException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

/**
 * Support for tests of a script stopped by its timeout (bug #77949): a real
 * {@code script.execution.timeout} of 1 s, and {@link Stops}, which an env subclass asks at
 * each exec whether to run {@code while(true){}} in place of a chosen exec of a chosen script,
 * so the real timeout stops it, or to throw a stopped script exception there itself.
 */
public final class ScriptStopTestSupport {
   private ScriptStopTestSupport() {
   }

   /**
    * Set the script timeout to {@code seconds} (null to unset) and make the engine read it now.
    *
    * @return the previous value, for {@link #setTimeout(String)} after the test.
    */
   public static String setTimeout(String seconds) throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", seconds);
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
      return previous;
   }

   /**
    * A stopped script exception, as the engine builds it for a timeout.
    */
   public static ScriptException stopped() {
      ScriptException stop = new ScriptException("Execution got interrupted.");
      stop.setStopped(true);
      return stop;
   }

   /**
    * Which execs are stopped: the execs (from 1) of the scripts compiled from a source that
    * contains a marker.
    */
   public static final class Stops {
      /**
       * Stop the {@code nth} execs of the scripts whose source contains {@code marker}, by a
       * real timeout, or by throwing {@link #stopped()} if {@code inject}.
       */
      public void reset(String marker, IntPredicate nth, boolean inject) {
         this.marker = marker;
         this.nth = nth;
         this.inject = inject;
         calls.set(0);
         stops.set(0);
         execs.set(0);
      }

      /**
       * Record the source a script was compiled from.
       */
      public Object compiled(String source, Object script) {
         if(script != null) {
            sources.put(script, source);
         }

         return script;
      }

      /**
       * The script to run for {@code script}: itself, or {@code loop} if this exec is stopped
       * by the timeout. Throws if the stop is injected.
       */
      public Object select(Object script, Object loop) {
         execs.incrementAndGet();
         String source = sources.get(script);

         if(marker == null || source == null || !source.contains(marker) ||
            !nth.test(calls.incrementAndGet()))
         {
            return script;
         }

         stops.incrementAndGet();

         if(inject) {
            throw stopped();
         }

         return loop;
      }

      /** The execs of the marked scripts. */
      public int calls() {
         return calls.get();
      }

      /** The execs that were stopped. */
      public int stops() {
         return stops.get();
      }

      /** All execs. */
      public int execs() {
         return execs.get();
      }

      private final Map<Object, String> sources = new ConcurrentHashMap<>();
      private final AtomicInteger calls = new AtomicInteger();
      private final AtomicInteger stops = new AtomicInteger();
      private final AtomicInteger execs = new AtomicInteger();
      private volatile String marker;
      private volatile IntPredicate nth = n -> false;
      private volatile boolean inject;
   }

   /**
    * A real env whose execs are stopped as {@link Stops} says.
    */
   public static class StoppingEnv extends GraalJavaScriptEnv {
      public StoppingEnv(Stops stops) {
         this.stops = stops;
      }

      @Override
      public Object compile(String cmd, boolean fieldOnly) throws Exception {
         return stops.compiled(cmd, super.compile(cmd, fieldOnly));
      }

      @Override
      public Object exec(Object script, Object scope, Object rscope, Object target)
         throws Exception
      {
         if(loop == null) {
            loop = super.compile(LOOP, false);
         }

         return super.exec(stops.select(script, loop), scope, rscope, target);
      }

      private final Stops stops;
      private Object loop;
   }

   /** The script a stopped exec runs: only the timeout ends it. */
   public static final String LOOP = "while(true){}";
}
