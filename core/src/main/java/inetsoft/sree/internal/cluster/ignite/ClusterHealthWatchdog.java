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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.util.health.ClusterHealthStatus;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.FailureType;
import org.apache.ignite.internal.IgniteEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Forces a stuck-but-alive server node to restart (Bug #78141).
 * <p>
 * A node that segments cleanly is already recovered today: Ignite's default
 * {@code StopNodeOrHaltFailureHandler} halts the JVM, and the container orchestrator's restart
 * policy brings up a fresh node that rejoins. But a node that restarts, rejoins, and then gets
 * stuck mid partition map exchange is a different shape entirely: {@link
 * inetsoft.util.health.ClusterHealthService} correctly keeps reporting it not-ready (it is a
 * deliberately bounded, non-blocking probe, see that class's own javadoc), but nothing acts on
 * "stuck not-ready" on its own — the node can sit unhealthy indefinitely until a human notices
 * and restarts the container by hand, which is exactly what the reported incident describes.
 * <p>
 * This watchdog samples the cluster health status on a daemon thread, tracks how long it has been
 * continuously not-ready (the timestamp resets the instant the status is observed ready again, it
 * never latches), and once that duration exceeds {@code cluster.stuckExchangeHaltMinutes}, raises
 * the identical synthetic {@link FailureType#SEGMENTATION} failure that {@code
 * MinorityIslandDetector.halt()} already raises for a minority island — funneling into the same
 * halt-and-restart path a genuine segmentation already uses today. It is off by default: {@code
 * cluster.stuckExchangeHaltMinutes} is unset unless an operator opts in.
 */
final class ClusterHealthWatchdog implements AutoCloseable {
   /**
    * @param ignite          the local Ignite instance, used only to raise the segmentation
    *                        failure once the threshold is exceeded.
    * @param statusSupplier  gets the current cluster health status. May be called from the
    *                        watchdog thread only; any failure is treated as an inconclusive tick,
    *                        neither starting nor clearing the not-ready timer.
    * @param intervalMillis  the sampling interval, or a value {@code <= 0} for the default.
    * @param thresholdMillis how long the status must be continuously not-ready before this node
    *                        halts itself. Must be positive; callers only construct this watchdog
    *                        when the feature is enabled.
    */
   ClusterHealthWatchdog(Ignite ignite, Supplier<ClusterHealthStatus> statusSupplier,
                         long intervalMillis, long thresholdMillis)
   {
      if(thresholdMillis <= 0) {
         throw new IllegalArgumentException("thresholdMillis must be positive: " + thresholdMillis);
      }

      this.ignite = ignite;
      this.statusSupplier = statusSupplier;
      this.intervalMillis = intervalMillis > 0 ? intervalMillis : DEFAULT_INTERVAL_MILLIS;
      this.thresholdMillis = thresholdMillis;
      this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread thread = new Thread(r, "IgniteHealthWatchdog");
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * Starts the periodic check.
    */
   void start() {
      executor.scheduleWithFixedDelay(
         this::checkQuietly, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
   }

   @Override
   public void close() {
      executor.shutdownNow();
   }

   private void checkQuietly() {
      try {
         check();
      }
      catch(Throwable e) {
         // never let an error end the periodic task: scheduleWithFixedDelay stops for good
         // after the first exception it sees
         LOG.debug("Cluster health watchdog check failed", e);
      }
   }

   /**
    * Runs one check. Called only on the watchdog thread, or directly by tests.
    */
   void check() {
      ClusterHealthStatus status;

      try {
         status = statusSupplier.get();
      }
      catch(Throwable e) {
         // sampling the status itself failed (e.g. the health service isn't ready to be built
         // yet); this tells us nothing, so don't start or clear the not-ready timer on it
         LOG.debug("Failed to sample cluster health status", e);
         return;
      }

      update(status != null && status.isReady(), System.currentTimeMillis());
   }

   private synchronized void update(boolean ready, long now) {
      if(ready) {
         notReadySince = -1L;
         haltPending = false;
         return;
      }

      if(notReadySince < 0) {
         notReadySince = now;
      }

      if(!haltPending && now - notReadySince >= thresholdMillis) {
         haltPending = true;
      }

      if(haltPending) {
         // retried on the next check if raising the failure itself fails
         haltPending = !halt();
      }
   }

   /**
    * Raises a segmentation failure, so that Ignite's failure handler halts this node.
    *
    * @return {@code true} if the failure was raised, {@code false} if it should be retried.
    */
   private boolean halt() {
      long minutes = TimeUnit.MILLISECONDS.toMinutes(thresholdMillis);
      LOG.error("The cluster has been continuously not ready for more than {} minute(s): " +
                   "raising a segmentation failure so that this node stops and is restarted",
                minutes);

      try {
         ((IgniteEx) ignite).context().failure().process(new FailureContext(
            FailureType.SEGMENTATION, new IgniteException(
               "Cluster health has been continuously not ready for more than " + minutes +
                  " minute(s); forcing a restart (cluster.stuckExchangeHaltMinutes)")));
         return true;
      }
      catch(Throwable e) {
         LOG.error("Failed to raise the segmentation failure, retrying on the next check", e);
         return false;
      }
   }

   private final Ignite ignite;
   private final Supplier<ClusterHealthStatus> statusSupplier;
   private final long intervalMillis;
   private final long thresholdMillis;
   private final ScheduledExecutorService executor;
   private long notReadySince = -1L; // guarded by this
   private boolean haltPending; // guarded by this

   private static final long DEFAULT_INTERVAL_MILLIS = 30_000L;
   private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthWatchdog.class);
}
