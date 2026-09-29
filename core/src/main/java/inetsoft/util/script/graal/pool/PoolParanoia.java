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
package inetsoft.util.script.graal.pool;

import inetsoft.sree.SreeEnv;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The opt-in paranoid check of the worksheet context pool (bug #77123): when
 * {@value #PROPERTY} is true, every clean that would keep its context is followed by a
 * read-only pass that compares the global with the clean helper's baseline, and a context
 * that still differs is closed instead of reused. Off by default; when off, a release pays
 * one cached boolean read. The JVM system property of the same name also turns it on, since
 * SreeEnv does not see {@code -D} properties (e.g. a whole test suite run with
 * {@code -Dscript.ws.contextPool.paranoid=true}).
 */
public final class PoolParanoia {
   public static final String PROPERTY = "script.ws.contextPool.paranoid";

   private PoolParanoia() {
   }

   /**
    * @return whether the paranoid check is on; the property is re-read at most every 10 s.
    */
   public static boolean enabled() {
      Boolean forced = PoolParanoia.forced;

      if(forced != null) {
         return forced;
      }

      long now = System.currentTimeMillis();

      if(now >= nextRead) {
         enabled = read();
         nextRead = now + REREAD_MILLIS;
      }

      return enabled;
   }

   /**
    * @return the node-wide number of cleaned contexts the check found off their baseline.
    */
   public static long violations() {
      return VIOLATIONS.get();
   }

   /**
    * Compare a cleaned context's global with its baseline, without changing it.
    *
    * @return the mismatching keys, empty if the global is at its baseline.
    */
   static List<String> verify(Context context, CleanHelper cleaner) {
      VERIFIES.incrementAndGet();
      ScriptTimeoutGuard.Guard guard = GUARD.guard(context, CleanHelper.TIMEOUT);
      List<String> keys;

      try(guard) {
         keys = cleaner.verify();
      }
      catch(RuntimeException ex) {
         return List.of("<verify failed: " + ex.getMessage() + ">");
      }

      return guard.interruptTimedOut() ? List.of("<verify timed out>") : keys;
   }

   /**
    * Check a slot whose clean succeeded. Caller holds its lock.
    *
    * @return whether the slot may be kept.
    */
   static boolean accept(Slot slot) {
      Consumer<Slot> hook = beforeVerifyHook;

      if(hook != null) {
         hook.accept(slot);
      }

      List<String> keys = verify(slot.engine().context(), slot.cleaner());

      if(keys.isEmpty()) {
         return true;
      }

      long n = VIOLATIONS.incrementAndGet();

      if(n % 100 == 1) {
         LOG.error("A cleaned worksheet script context is not at its baseline ({} so far); " +
                   "it is closed instead of reused: {}", n, keys);
      }

      return false;
   }

   private static boolean read() {
      if(Boolean.getBoolean(PROPERTY)) {
         return true;
      }

      try {
         return "true".equalsIgnoreCase(SreeEnv.getProperty(PROPERTY, "false"));
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Drop the cached property value, so the next {@link #enabled()} reads it. Tests only.
    */
   static void refresh() {
      nextRead = 0L;
   }

   /**
    * Test hook: when non-null, {@link #enabled()} returns it and never reads the property.
    * Null in production.
    */
   static volatile Boolean forced;
   /**
    * Test hook run on a cleaned slot right before the release's check, to plant a defect the
    * clean did not see; null in production.
    */
   static volatile Consumer<Slot> beforeVerifyHook;
   // test hook: the number of verify passes run
   static final AtomicLong VERIFIES = new AtomicLong();

   private static final long REREAD_MILLIS = 10_000L;
   private static final AtomicLong VIOLATIONS = new AtomicLong();
   private static final ScriptTimeoutGuard GUARD = new ScriptTimeoutGuard();
   private static volatile boolean enabled;
   private static volatile long nextRead;

   private static final Logger LOG = LoggerFactory.getLogger(PoolParanoia.class);
}
