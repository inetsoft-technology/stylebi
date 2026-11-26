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
package inetsoft.report.composition;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.GroupedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finds the holder of the edit lock of an asset among the runtime sheets of the other nodes of the
 * cluster.
 * <p>
 * The lock is not stored, it is the editable runtime sheet of the asset that was opened first (see
 * {@link WorksheetEngine#getLockOwner}). A runtime sheet lives on the node that owns its runtime id,
 * which has nothing to do with the node the user is logged in to, so two users editing the same
 * asset usually have sheets on different nodes and a lookup of the local node alone does not see
 * the other user.
 * <p>
 * The lookup is only a safeguard against two users overwriting each other, so it must never make an
 * open or a save slower than a short time box or fail: when a node does not answer in time, the
 * pool is full or the call fails, the other nodes are ignored and the answer is the one of the local
 * node, which is what the lock did before the lookup existed. Nothing is cached or stored, so
 * there is no state to go stale when a node stops.
 */
final class SheetLockLookup implements AutoCloseable {
   /**
    * @param cluster   the cluster.
    * @param cacheName the name of the runtime sheet cache, the nodes that hold its partitions are
    *                  the nodes that hold runtime sheets.
    */
   SheetLockLookup(Cluster cluster, String cacheName) {
      this(cluster, cacheName, getConfiguredTimeoutMillis());
   }

   /**
    * @param timeoutMillis the longest time to wait for the other nodes.
    */
   SheetLockLookup(Cluster cluster, String cacheName, long timeoutMillis) {
      this.cluster = cluster;
      this.cacheName = cacheName;
      this.timeoutMillis = timeoutMillis;
      // Waiting for a node that is starting can last minutes (the cluster call timeout is 5
      // minutes) and the call can not be limited, so it runs here and the caller stops waiting.
      // The pool is small and rejects when it is full, which ends the lookup at once.
      this.executor = new ThreadPoolExecutor(
         0, MAX_THREADS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            GroupedThread thread = new GroupedThread(r, "WorksheetLockLookup");
            thread.setDaemon(true);
            return thread;
         });
   }

   /**
    * Find the holder of the lock of an asset on the other nodes of the cluster.
    *
    * @param entry           the entry of the asset.
    * @param includeReadOnly {@code true} to count the sheets that are not editable too.
    *
    * @return the holder that opened its sheet first, or {@code null} if no other node has a sheet
    *         that holds the lock, there is only one node, or the nodes could not be asked.
    */
   SheetLockHolder findRemote(AssetEntry entry, boolean includeReadOnly) {
      try {
         if(cluster.getServerClusterNodes().size() <= 1) {
            return null;
         }

         Task task = new Task(entry, includeReadOnly, cluster.getLocalMember());
         Future<List<SheetLockHolder>> future =
            executor.submit(() -> cluster.affinityCallAll(cacheName, task));
         SheetLockHolder holder = null;

         try {
            for(SheetLockHolder remote : future.get(timeoutMillis, TimeUnit.MILLISECONDS)) {
               holder = SheetLockHolder.earlier(holder, remote);
            }
         }
         catch(Exception ex) {
            future.cancel(true);
            throw ex;
         }

         return holder;
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
         logFailure(entry, ex);
      }
      catch(Exception ex) {
         logFailure(entry, ex);
      }

      return null;
   }

   private void logFailure(AssetEntry entry, Exception ex) {
      String message = "Could not ask the other nodes who holds the edit lock of {}, using the " +
         "runtime sheets of this node only: {}";
      long now = System.currentTimeMillis();
      long last = lastWarning.get();

      // a node that is down or slow fails every lookup, do not fill the log with it
      if(now - last > WARNING_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
         LOG.warn(message, entry == null ? null : entry.getPath(), ex.toString());
      }
      else {
         LOG.debug(message, entry == null ? null : entry.getPath(), ex.toString(), ex);
      }
   }

   private static long getConfiguredTimeoutMillis() {
      String property = SreeEnv.getProperty("worksheet.lock.lookup.timeout");

      if(property != null && !property.isBlank()) {
         try {
            long timeout = Long.parseLong(property.trim());

            if(timeout > 0) {
               return timeout;
            }
         }
         catch(NumberFormatException ignore) {
            // fall back to the default
         }

         LOG.warn("Invalid value for worksheet.lock.lookup.timeout: {}", property);
      }

      return DEFAULT_TIMEOUT_MILLIS;
   }

   @Override
   public void close() {
      executor.shutdownNow();
   }

   /**
    * Looks up the lock holder among the runtime sheets of the node it runs on.
    */
   static final class Task implements AffinityCallable<SheetLockHolder> {
      Task(AssetEntry entry, boolean includeReadOnly, String callerNode) {
         this.entry = entry;
         this.includeReadOnly = includeReadOnly;
         this.callerNode = callerNode;
      }

      @Override
      public SheetLockHolder call() {
         WorksheetEngine engine = (WorksheetEngine) WorksheetEngine.getWorksheetService();

         // the caller looked at its own sheets already, it only wants the other nodes'
         if(Objects.equals(callerNode, engine.cluster.getLocalMember())) {
            return null;
         }

         return engine.findLocalLockHolder(entry, includeReadOnly);
      }

      private final AssetEntry entry;
      private final boolean includeReadOnly;
      private final String callerNode;
      private static final long serialVersionUID = 1L;
   }

   private final Cluster cluster;
   private final String cacheName;
   private final long timeoutMillis;
   private final ExecutorService executor;
   private final AtomicLong lastWarning = new AtomicLong();

   static final long DEFAULT_TIMEOUT_MILLIS = 3000L;
   private static final int MAX_THREADS = 8;
   private static final long WARNING_INTERVAL_MILLIS = 60_000L;
   private static final Logger LOG = LoggerFactory.getLogger(SheetLockLookup.class);
}
