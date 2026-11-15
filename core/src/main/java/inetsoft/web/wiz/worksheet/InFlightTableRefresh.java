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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.service.RenderWaitSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bug #81355: stops a table refresh that {@link RenderWaitSupport#awaitOrRetry} timed out on, and
 * left running, from being started again by every later edit.
 *
 * <p>{@code AssetQuerySandbox.refreshColumnSelection} runs a crosstab/rotate/unpivot table's query
 * as a metadata query, which skips {@code AssetDataCache}'s own in-flight de-dup, and
 * {@code awaitOrRetry} neither cancels nor de-dups. So each worksheet edit started one more
 * identical statement while the previous one was still running. This keeps a registry of the
 * refreshes in flight, keyed by (runtime worksheet, table name) and carrying the table's
 * definition ({@code printKey}):</p>
 * <ul>
 *   <li>same table, same definition still in flight: the new caller joins it (waits on it within
 *       its own timeout) instead of starting a second query;</li>
 *   <li>same table, definition changed: the running refresh is stale, so the runtime worksheet's
 *       pending queries are cancelled ({@code QueryManager.cancelForChange}, the same call the
 *       viewsheet side uses for a superseded request) and a fresh one is started;</li>
 *   <li>a dependent (rotate/unpivot/join/mirror/...) of a table that is in flight waits for that
 *       table first, because its own refresh would otherwise run the slow table's SQL a second
 *       time in parallel.</li>
 * </ul>
 *
 * <p>Known limits: {@code QueryManager.cancel*} reaches every pending query of the runtime
 * worksheet, not only the table's; a table refreshed only as part of another table's recursive
 * dependents refresh is not registered under its own name; if the definition cannot be printed the
 * call falls back to the unguarded behaviour.</p>
 */
final class InFlightTableRefresh {
   /** What a caller does when a refresh of the same table is already running. */
   enum Policy {
      /** Join it when the table definition is unchanged; cancel and restart when it changed. */
      JOIN_SAME_DEFINITION,
      /** Always cancel the running one and start fresh (an explicit refresh_data wants new data). */
      SUPERSEDE
   }

   private InFlightTableRefresh() {
   }

   /**
    * Like {@link RenderWaitSupport#awaitOrRetry(Callable, long, int, RenderNotReadyException.Subject)}
    * but never lets two refreshes of the same table definition run at once in one runtime. A
    * caller that joins an in-flight refresh gets {@code null}, not {@code work}'s result.
    *
    * @param onNotRun run when {@code work} is not started at all (joined, or timed out waiting
    *                 for a dependency), so the caller can release what it set up for {@code work}
    *                 to release; may be null.
    */
   static <T> T awaitOrRetry(RuntimeWorksheet rws, String table, Policy policy, Callable<T> work,
                             long timeoutMs, int retryAfterSeconds,
                             RenderNotReadyException.Subject subject, Runnable onNotRun)
      throws Exception
   {
      String fingerprint = fingerprint(rws, table);

      if(fingerprint == null) {
         return RenderWaitSupport.awaitOrRetry(work, timeoutMs, retryAfterSeconds, subject);
      }

      long deadline = System.currentTimeMillis() + timeoutMs;
      Map<String, Entry> inFlight = inFlight(rws);
      boolean started = false;
      int joinFailures = 0;

      try {
         while(true) {
            Entry mine = null;
            Entry waitFor = null;
            boolean sameTable = false;
            boolean cancel = false;

            synchronized(inFlight) {
               Entry current = inFlight.get(table);

               if(current != null && current.done.isDone()) {
                  inFlight.remove(table, current);
                  current = null;
               }

               if(policy == Policy.JOIN_SAME_DEFINITION) {
                  if(current != null && current.fingerprint.equals(fingerprint)) {
                     waitFor = current;
                     sameTable = true;
                  }
                  else if(current == null) {
                     waitFor = runningDependency(rws, table, inFlight);
                  }
               }

               if(waitFor == null) {
                  cancel = current != null;
                  mine = new Entry(fingerprint);
                  inFlight.put(table, mine);
               }
            }

            if(waitFor != null) {
               long remaining = deadline - System.currentTimeMillis();

               try {
                  waitFor.done.get(Math.max(1, remaining), TimeUnit.MILLISECONDS);
               }
               catch(TimeoutException e) {
                  throw new RenderNotReadyException(retryAfterSeconds, subject);
               }
               catch(ExecutionException e) {
                  // The failure (e.g. a cancel by a newer refresh) belongs to the run that was
                  // joined, not to this caller: start this caller's own refresh instead of
                  // propagating it, a bounded number of times. A failed dependency likewise
                  // does not stop this table from running its own refresh.
                  if(sameTable && ++joinFailures > MAX_JOIN_FAILURES) {
                     Throwable cause = e.getCause();

                     if(cause instanceof Exception ex) {
                        throw ex;
                     }

                     throw e;
                  }

                  continue;
               }

               if(sameTable) {
                  return null;
               }

               continue;
            }

            started = true;
            Entry entry = mine;
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
                  synchronized(inFlight) {
                     inFlight.remove(table, entry);
                  }

                  if(failure == null) {
                     entry.done.complete(null);
                  }
                  else {
                     entry.done.completeExceptionally(failure);
                  }
               }
            };

            try {
               if(cancel) {
                  cancelPending(rws, table);
               }

               return RenderWaitSupport.awaitOrRetry(
                  tracked, Math.max(1, deadline - System.currentTimeMillis()), retryAfterSeconds,
                  subject);
            }
            catch(Throwable t) {
               // A RenderNotReadyException means the work was submitted and cleans up after
               // itself. Anything else before it started (cancel or submit failed) would leave
               // this entry registered for good, blocking every later refresh of the table.
               if(!entered.get() && !(t instanceof RenderNotReadyException)) {
                  synchronized(inFlight) {
                     inFlight.remove(table, entry);
                  }

                  entry.done.completeExceptionally(t);
               }

               throw t;
            }
         }
      }
      finally {
         if(!started && onNotRun != null) {
            onNotRun.run();
         }
      }
   }

   // Registry for one runtime worksheet. Entries remove themselves when their work ends.
   private static Map<String, Entry> inFlight(RuntimeWorksheet rws) {
      synchronized(REGISTRY) {
         return REGISTRY.computeIfAbsent(rws, k -> new HashMap<>());
      }
   }

   /**
    * The table definition as StyleBI's own cache key builds it ({@code printKey}); null if it
    * cannot be printed, in which case the caller is not guarded.
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

   // Must hold the lock on inFlight.
   private static Entry runningDependency(RuntimeWorksheet rws, String table,
                                          Map<String, Entry> inFlight)
   {
      if(inFlight.isEmpty()) {
         return null;
      }

      try {
         Worksheet ws = rws.getWorksheet();
         Assembly assembly = ws == null ? null : ws.getAssembly(table);

         if(assembly == null) {
            return null;
         }

         Set<String> visited = new HashSet<>();
         Deque<AssemblyEntry> todo = new ArrayDeque<>();
         todo.add(assembly.getAssemblyEntry());

         while(!todo.isEmpty()) {
            AssemblyRef[] refs = ws.getDependeds(todo.poll());

            if(refs == null) {
               continue;
            }

            for(AssemblyRef ref : refs) {
               String name = ref.getEntry().getName();

               if(!visited.add(name)) {
                  continue;
               }

               Entry entry = inFlight.get(name);

               if(entry != null && !entry.done.isDone()) {
                  return entry;
               }

               todo.add(ref.getEntry());
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Cannot walk the dependencies of table {}", table, e);
      }

      return null;
   }

   private static void cancelPending(RuntimeWorksheet rws, String table) {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      QueryManager qmgr = box == null ? null : box.getQueryManager();

      if(qmgr != null) {
         LOG.debug("Cancelling the pending queries of the runtime for a newer refresh of {}",
                   table);
         qmgr.cancelForChange();
      }
   }

   private static final class Entry {
      Entry(String fingerprint) {
         this.fingerprint = fingerprint;
      }

      final String fingerprint;
      final CompletableFuture<Void> done = new CompletableFuture<>();
   }

   private static final int MAX_JOIN_FAILURES = 3;
   private static final Map<RuntimeWorksheet, Map<String, Entry>> REGISTRY = new WeakHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(InFlightTableRefresh.class);
}
