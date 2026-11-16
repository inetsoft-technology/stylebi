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

package inetsoft.web.wiz.service;

import inetsoft.uql.XNode;
import inetsoft.uql.service.XEngine;
import inetsoft.util.ThreadContext;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the blocking JDBC metadata work of a data source on a bounded pool of daemon threads and
 * lets the caller stop waiting at a deadline.
 *
 * <p>A connect to an unreachable host blocks in the driver and cannot be interrupted, so the
 * budget is enforced on the caller's side ({@code Future.get(timeout)}); the worker may keep
 * running after the caller has moved on. To avoid sending every following request into the same
 * blocked connect, a data source that exceeded its budget is answered immediately for
 * {@link #RETRY_INTERVAL_MS} (the same window {@code XEngine} keeps a failed metadata result).</p>
 */
final class MetadataCallGuard {
   private MetadataCallGuard() {
   }

   /**
    * Gets the time budget of one metadata request, in milliseconds. It stays below the
    * 30 s default timeout of the plugin client, so the caller receives the answer.
    */
   static long budgetMillis() {
      return Long.getLong(BUDGET_PROPERTY, DEFAULT_BUDGET_MS);
   }

   /**
    * Gets the deadline, in {@code System.nanoTime()} units, for a request starting now.
    */
   static long newDeadline() {
      return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis());
   }

   /**
    * Runs the task within the budget of a single request.
    */
   static <T> T call(String datasource, Callable<T> task) throws Exception {
      if(IN_GUARD.get()) {
         // already on a guard thread under the caller's deadline; a nested pool task could
         // starve the pool of threads
         return task.call();
      }

      return await(datasource, submit(datasource, task), newDeadline());
   }

   /**
    * Starts the task on the guard pool, running with the caller's principal and session.
    */
   static <T> Pending<T> submit(String datasource, Callable<T> task) {
      Principal principal = ThreadContext.getContextPrincipal();
      Thread caller = Thread.currentThread();
      AtomicBoolean started = new AtomicBoolean();

      Future<T> future = POOL.submit(() -> {
         try {
            started.set(true);
            IN_GUARD.set(true);
            ThreadContext.setContextPrincipal(principal);
            ThreadContext.inheritSession(caller);
            return task.call();
         }
         finally {
            IN_GUARD.remove();
            ThreadContext.setContextPrincipal(null);
         }
      });

      return new Pending<>(future, started);
   }

   /**
    * A submitted task, and whether a pool thread has picked it up yet.
    */
   record Pending<T>(Future<T> future, AtomicBoolean started) {
   }

   /**
    * Waits for the task until the deadline.
    *
    * @throws DatasourceUnreachableException if the task did not finish in time, or the data
    *         source already timed out recently.
    */
   static <T> T await(String datasource, Pending<T> pending, long deadlineNanos)
      throws Exception
   {
      Future<T> future = pending.future();
      Long timedOutAt = TIMED_OUT.get(datasource);

      if(timedOutAt != null) {
         long age = System.currentTimeMillis() - timedOutAt;

         if(age < RETRY_INTERVAL_MS) {
            future.cancel(true);
            throw new DatasourceUnreachableException(datasource,
               "it did not respond " + (age / 1000) + " s ago; not retrying for another " +
               ((RETRY_INTERVAL_MS - age) / 1000 + 1) + " s");
         }

         TIMED_OUT.remove(datasource, timedOutAt);
      }

      try {
         T result = future.get(Math.max(0L, deadlineNanos - System.nanoTime()),
                               TimeUnit.NANOSECONDS);
         TIMED_OUT.remove(datasource);
         return result;
      }
      catch(TimeoutException e) {
         future.cancel(true);

         if(!pending.started().get()) {
            // still queued behind other sources: the source itself was never tried
            throw new DatasourceUnreachableException(datasource,
               "it was not reached within the " + (budgetMillis() / 1000) + " s time budget");
         }

         TIMED_OUT.put(datasource, System.currentTimeMillis());
         throw new DatasourceUnreachableException(datasource,
            "it did not answer within " + (budgetMillis() / 1000) + " s");
      }
      catch(ExecutionException e) {
         Throwable cause = e.getCause();

         if(cause instanceof Exception ex) {
            throw ex;
         }

         if(cause instanceof Error err) {
            throw err;
         }

         throw e;
      }
      catch(InterruptedException e) {
         future.cancel(true);
         Thread.currentThread().interrupt();
         throw e;
      }
   }

   /**
    * Fails with a data-source-named error when the metadata result is the placeholder XEngine
    * caches for a failed retrieval, rather than letting the empty result read as a missing table.
    */
   static XNode requireMetaData(XNode node, String datasource) throws DatasourceUnreachableException {
      if(XEngine.isMetaDataFailure(node)) {
         throw new DatasourceUnreachableException(datasource,
            "its metadata could not be retrieved (see the server log for the connection error)");
      }

      return node;
   }

   /**
    * Forgets recorded timeouts. For tests.
    */
   static void reset() {
      TIMED_OUT.clear();
   }

   static final long RETRY_INTERVAL_MS = 60000L;
   static final String BUDGET_PROPERTY = "wiz.metadata.budget.ms";
   private static final long DEFAULT_BUDGET_MS = 20000L;
   private static final int POOL_SIZE = 16;
   private static final ThreadLocal<Boolean> IN_GUARD = ThreadLocal.withInitial(() -> false);
   private static final Map<String, Long> TIMED_OUT = new ConcurrentHashMap<>();
   private static final ThreadPoolExecutor POOL;

   static {
      AtomicInteger counter = new AtomicInteger();
      POOL = new ThreadPoolExecutor(
         POOL_SIZE, POOL_SIZE, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
         r -> {
            Thread t = new Thread(r, "wiz-metadata-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
         });
      POOL.allowCoreThreadTimeOut(true);
   }
}
