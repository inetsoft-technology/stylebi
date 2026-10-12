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
import java.util.function.BooleanSupplier;
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
 * continuously not-ready (the timer resets the instant the status is observed ready again, it
 * never latches), and once that duration exceeds {@code cluster.stuckExchangeHaltMinutes}, raises
 * the identical synthetic {@link FailureType#SEGMENTATION} failure that {@code
 * MinorityIslandDetector.halt()} already raises for a minority island — funneling into the same
 * halt-and-restart path a genuine segmentation already uses today. It is off by default: {@code
 * cluster.stuckExchangeHaltMinutes} is unset unless an operator opts in.
 * <p>
 * <b>Minority-island carve-out.</b> {@code ClusterHealthService} reports not-ready whenever this
 * node is a confirmed minority island, regardless of {@code cluster.minorityIslandHalt} — that
 * flag only gates whether {@code MinorityIslandDetector} itself halts the node, not whether the
 * health status reflects the condition. To keep the two opt-in knobs functionally independent as
 * documented, this watchdog excludes any tick where the cluster is currently a confirmed minority
 * island from its not-ready timer entirely: such a tick neither starts, advances, nor resets the
 * timer. So a node that is a minority island for longer than {@code
 * cluster.stuckExchangeHaltMinutes} is never halted by this watchdog on that basis alone — only
 * {@code cluster.minorityIslandHalt} can do that. If the island condition later clears while the
 * node is still not-ready for an unrelated reason (e.g. a genuinely stuck exchange), timing
 * resumes from wherever it had already reached before the island excluded it — island time is
 * never counted, but genuine not-ready time already accrued before or after the island is not
 * discarded either.
 */
final class ClusterHealthWatchdog implements AutoCloseable {
   /**
    * @param ignite              the local Ignite instance, used only to raise the segmentation
    *                            failure once the threshold is exceeded.
    * @param statusSupplier      gets the current cluster health status. May be called from the
    *                            watchdog thread only; any failure is treated as an inconclusive
    *                            tick, neither starting nor clearing the not-ready timer.
    * @param minorityIslandSupplier reports whether this node is currently a confirmed minority
    *                            island. May be called from the watchdog thread only; any failure
    *                            is treated as "not a minority island" so the not-ready timer still
    *                            evaluates normally on that tick.
    * @param intervalMillis      the sampling interval, or a value {@code <= 0} for the default.
    * @param thresholdMillis     how long the status must be continuously not-ready, excluding any
    *                            time spent as a confirmed minority island, before this node halts
    *                            itself. Must be positive; callers only construct this watchdog
    *                            when the feature is enabled.
    */
   ClusterHealthWatchdog(Ignite ignite, Supplier<ClusterHealthStatus> statusSupplier,
                         BooleanSupplier minorityIslandSupplier,
                         long intervalMillis, long thresholdMillis)
   {
      if(thresholdMillis <= 0) {
         throw new IllegalArgumentException("thresholdMillis must be positive: " + thresholdMillis);
      }

      this.ignite = ignite;
      this.statusSupplier = statusSupplier;
      this.minorityIslandSupplier = minorityIslandSupplier;
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

      boolean minorityIsland;

      try {
         minorityIsland = minorityIslandSupplier.getAsBoolean();
      }
      catch(Throwable e) {
         // an inconclusive island check shouldn't block the stuck-exchange timer from working;
         // fail open to "not a minority island" and evaluate the tick normally
         LOG.debug("Failed to sample minority-island status", e);
         minorityIsland = false;
      }

      update(status != null && status.isReady(), minorityIsland, System.currentTimeMillis());
   }

   private synchronized void update(boolean ready, boolean minorityIsland, long now) {
      if(ready) {
         notReadySince = -1L;
         excludedMillis = 0L;
         haltPending = false;
         lastTickMillis = now;
         return;
      }

      long elapsed = lastTickMillis < 0 ? 0L : Math.max(0L, now - lastTickMillis);
      lastTickMillis = now;

      if(notReadySince < 0) {
         // the first tick of a new not-ready stretch: nothing has elapsed within this stretch
         // yet, whether or not this very tick happens to be a minority island
         notReadySince = now;
         excludedMillis = 0L;
      }
      else if(minorityIsland) {
         // minority-island halting is governed entirely by cluster.minorityIslandHalt; exclude
         // this tick's elapsed time from the stuck-exchange timer so the two knobs stay
         // independent, without discarding genuine not-ready time already accrued
         excludedMillis += elapsed;
      }

      if(!haltPending && now - notReadySince - excludedMillis >= thresholdMillis) {
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
   private final BooleanSupplier minorityIslandSupplier;
   private final long intervalMillis;
   private final long thresholdMillis;
   private final ScheduledExecutorService executor;
   private long notReadySince = -1L; // guarded by this; start of the current not-ready stretch
   private long excludedMillis; // guarded by this; minority-island time within that stretch
   private long lastTickMillis = -1L; // guarded by this; time of the previous processed tick
   private boolean haltPending; // guarded by this

   private static final long DEFAULT_INTERVAL_MILLIS = 30_000L;
   private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthWatchdog.class);
}
