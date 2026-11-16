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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.TimedQueue;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.service.RenderWaitSupport;
import inetsoft.web.wiz.service.RuntimeHeartbeatKeepAlive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bug #81415: bounds the live build of one table's model in the post-edit worksheet broadcast, and
 * keeps the build that outlives its bound from being started again by every later edit.
 *
 * <p>Building a table's model executes the table. A table downstream of an assembly that needs
 * script evaluation (a mirror, join, ... of a table with a script expression column) therefore
 * runs that script on the HTTP request thread that carries the edit. {@link #run} gives the build
 * a time limit on {@link RenderWaitSupport}'s executor, so the request thread is released when the
 * limit passes; the build itself is not cancelled (a script busy loop cannot be) and keeps running
 * in the background.</p>
 *
 * <p>Every edit invalidates the table's cached result, so without a guard each later broadcast
 * would start another full evaluation while the previous one is still running. The guard keeps one
 * entry per (runtime worksheet, table name), carrying the table's definition ({@code printKey},
 * which for a composed table includes the tables it is built on):</p>
 * <ul>
 *   <li>same definition still building: no new build is started, the caller falls back to a model
 *       built without executing the table;</li>
 *   <li>definition changed (the user fixed the slow column): the running build is stale, a new one
 *       is started, so the corrected table gets its row count and columns. At most one build per
 *       distinct definition is left running.</li>
 * </ul>
 *
 * <p>The runtime's heartbeat is kept fresh while a build runs (Bug #79074, see
 * {@link RuntimeHeartbeatKeepAlive}), but for at most {@link #heartbeatCapMs}: a script that never
 * ends must not pin the runtime worksheet in memory for ever. Known limits: the abandoned build
 * keeps one CPU until the script ends, and the runtime may be recycled while it still runs; if the
 * definition cannot be printed the build is bounded but not de-duplicated.</p>
 */
final class InFlightModelBuild {
   private InFlightModelBuild() {
   }

   /**
    * Runs {@code work} (a live model build) for at most {@code timeoutMs}.
    *
    * @return the built model, or empty when the caller must fall back to a model built without
    *         executing the table: the limit passed, or the same definition is still building from
    *         an earlier call. A failure of {@code work} itself is thrown.
    */
   static <T> Optional<T> run(RuntimeWorksheet rws, String table, Callable<T> work, long timeoutMs)
      throws Exception
   {
      String fingerprint = fingerprint(rws, table);
      Map<String, Entry> inFlight = inFlight(rws);
      Entry entry = new Entry(fingerprint);

      synchronized(inFlight) {
         Entry current = inFlight.get(table);

         if(current != null && current.done.isDone()) {
            inFlight.remove(table, current);
            current = null;
         }

         if(current != null && fingerprint != null && fingerprint.equals(current.fingerprint)) {
            return Optional.empty();
         }

         // A running build of an older definition stays registered under its own Entry object and
         // removes only itself when it ends.
         inFlight.put(table, entry);
      }

      TimedQueue.TimedRunnable heartbeat = RuntimeHeartbeatKeepAlive.start(rws);
      TimedQueue.TimedRunnable cap = TimedQueue.add(new TimedQueue.TimedRunnable(heartbeatCapMs) {
         @Override
         public void run() {
            RuntimeHeartbeatKeepAlive.stop(heartbeat);
         }
      });
      AtomicBoolean entered = new AtomicBoolean();
      Callable<T> tracked = () -> {
         entered.set(true);
         Throwable failure = null;

         try {
            return work.call();
         }
         catch(Throwable t) {
            failure = t;
            throw t;
         }
         finally {
            finish(inFlight, table, entry, heartbeat, cap, failure);
         }
      };

      try {
         return Optional.ofNullable(
            RenderWaitSupport.awaitOrRetry(
               tracked, Math.max(1, timeoutMs), 1, RenderNotReadyException.Subject.TABLE));
      }
      catch(RenderNotReadyException e) {
         // The build was submitted and releases its entry and heartbeat itself when it ends.
         return Optional.empty();
      }
      catch(Throwable t) {
         // Anything else before the callable ran (submit failed) would leave the entry registered
         // and the heartbeat running for good.
         if(!entered.get()) {
            finish(inFlight, table, entry, heartbeat, cap, t);
         }

         throw t;
      }
   }

   private static void finish(Map<String, Entry> inFlight, String table, Entry entry,
                              TimedQueue.TimedRunnable heartbeat, TimedQueue.TimedRunnable cap,
                              Throwable failure)
   {
      synchronized(inFlight) {
         inFlight.remove(table, entry);
      }

      RuntimeHeartbeatKeepAlive.stop(heartbeat);
      TimedQueue.remove(cap);

      if(failure == null) {
         entry.done.complete(null);
      }
      else {
         entry.done.completeExceptionally(failure);
      }
   }

   // Registry for one runtime worksheet. Entries remove themselves when their build ends.
   private static Map<String, Entry> inFlight(RuntimeWorksheet rws) {
      synchronized(REGISTRY) {
         return REGISTRY.computeIfAbsent(rws, k -> new HashMap<>());
      }
   }

   /**
    * The table definition as StyleBI's own cache key builds it ({@code printKey}); null if it
    * cannot be printed, in which case the build is not de-duplicated.
    */
   static String fingerprint(RuntimeWorksheet rws, String table) {
      try {
         Worksheet ws = rws.getWorksheet();
         Assembly assembly = ws == null ? null : ws.getAssembly(table);

         if(!(assembly instanceof TableAssembly ta)) {
            return null;
         }

         StringWriter sw = new StringWriter();
         PrintWriter writer = new PrintWriter(sw);

         if(!ta.printKey(writer)) {
            return null;
         }

         writer.flush();
         return sw.toString();
      }
      catch(Exception e) {
         LOG.debug("Cannot fingerprint table {}", table, e);
         return null;
      }
   }

   /**
    * How long the runtime's heartbeat is kept fresh for one build. Non-final solely so a unit test
    * can shrink it; production code never reassigns it.
    */
   static long heartbeatCapMs = 10 * 60 * 1000L;

   // Holds only the definition and a completion flag, never the runtime worksheet (the map key) or
   // the build, so a running build does not pin the key.
   private static final class Entry {
      Entry(String fingerprint) {
         this.fingerprint = fingerprint;
      }

      final String fingerprint;
      final CompletableFuture<Void> done = new CompletableFuture<>();
   }

   private static final Logger LOG = LoggerFactory.getLogger(InFlightModelBuild.class);
   private static final Map<RuntimeWorksheet, Map<String, Entry>> REGISTRY = new WeakHashMap<>();
}
