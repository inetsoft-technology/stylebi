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
import org.apache.ignite.IgniteException;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.FailureType;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.failure.FailureProcessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78141: {@link ClusterHealthWatchdog} must halt the node once the cluster health status has
 * been continuously not-ready for longer than the configured threshold, must never halt while the
 * status is ready, and a brief ready blip must reset the not-ready timer rather than let two
 * sub-threshold not-ready stretches add up to a halt. Review finding I1 (round 1 of community PR
 * #6777): a confirmed minority island must never drive this watchdog's halt by itself, however
 * long it persists (that remains exclusively {@code cluster.minorityIslandHalt}'s job), and once
 * the island condition clears, genuine not-ready time must resume accruing without crediting the
 * excluded island time toward the threshold.
 * <p>
 * This exercises {@link ClusterHealthWatchdog#check()} directly (its own javadoc says it is
 * "called only on the watchdog thread, or directly by tests") against a mocked {@link IgniteEx}
 * that records the {@link FailureContext} passed to its {@link FailureProcessor} instead of
 * actually halting the JVM, rather than standing up a real embedded Ignite cluster the way the
 * heavier, {@code @Tag("slow")} {@code IgniteClusterMinorityIslandTest} does for the sibling
 * {@code MinorityIslandDetector} — the watchdog's {@code Supplier<ClusterHealthStatus>}
 * constructor argument exists specifically to make this decoupling possible.
 */
@Tag("core")
class ClusterHealthWatchdogTest {
   @Test
   void neverHaltsWhileReady() throws InterruptedException {
      Fixture fx = new Fixture(200L);

      for(int i = 0; i < 5; i++) {
         fx.watchdog.check();
         Thread.sleep(50L);
      }

      assertTrue(fx.failures.isEmpty(), "must not halt while the cluster reports ready");
   }

   @Test
   void haltsOnceContinuouslyNotReadyPastTheThreshold() throws InterruptedException {
      Fixture fx = new Fixture(200L);
      fx.ready = false;

      // the first not-ready tick only starts the timer; it must not halt immediately
      fx.watchdog.check();
      assertTrue(fx.failures.isEmpty(), "must not halt before the threshold elapses");

      Thread.sleep(250L);
      fx.watchdog.check();

      assertEquals(1, fx.failures.size(), "must halt once continuously-not-ready exceeds the threshold");
      assertEquals(FailureType.SEGMENTATION, fx.failures.get(0).type());
   }

   @Test
   void aReadyBlipResetsTheNotReadyTimerInsteadOfLatching() throws InterruptedException {
      Fixture fx = new Fixture(200L);

      // first not-ready stretch, well under the threshold
      fx.ready = false;
      fx.watchdog.check();
      Thread.sleep(150L);

      // the blip: back to ready before the threshold is reached
      fx.ready = true;
      fx.watchdog.check();

      // a second not-ready stretch starts fresh; if the timer had not reset, this stretch's
      // elapsed time plus the first stretch's would already be past the 200ms threshold
      fx.ready = false;
      fx.watchdog.check();
      Thread.sleep(150L);
      fx.watchdog.check();

      assertTrue(fx.failures.isEmpty(),
                 "a ready blip must reset the not-ready timer, not let two sub-threshold " +
                    "not-ready stretches accumulate toward a halt");

      // past the threshold measured from the second stretch's own start: the watchdog must
      // still be able to halt once that stretch alone runs long enough
      Thread.sleep(100L);
      fx.watchdog.check();
      assertEquals(1, fx.failures.size(),
                   "the second not-ready stretch must still halt once it alone exceeds the threshold");
   }

   /**
    * Bug #78141 review finding I1: {@code ClusterHealthService} reports not-ready whenever the
    * node is a confirmed minority island, regardless of {@code cluster.minorityIslandHalt} — so a
    * minority island, by itself, must never drive this watchdog's halt no matter how long it
    * persists. Only {@code cluster.minorityIslandHalt} is allowed to act on that condition.
    */
   @Test
   void minorityIslandAloneNeverHaltsNoMatterHowLong() throws InterruptedException {
      Fixture fx = new Fixture(200L);
      fx.ready = false;
      fx.minorityIsland = true;

      for(int i = 0; i < 5; i++) {
         fx.watchdog.check();
         Thread.sleep(100L);
      }
      fx.watchdog.check();

      assertTrue(fx.failures.isEmpty(),
                 "a confirmed minority island must not drive the stuck-exchange halt by itself, " +
                    "however long it persists");
   }

   /**
    * Bug #78141 review finding I1, boundary case: once the minority-island condition clears, if
    * the node is still genuinely not-ready for an unrelated reason, the watchdog must resume
    * timing from that point — neither crediting the time spent excluded as a minority island
    * (it must not halt immediately once the island clears) nor discarding it unfairly (it must
    * still halt after roughly one more full threshold's worth of genuine not-ready time, not
    * threshold-plus-island-duration).
    */
   @Test
   void minorityIslandClearingResumesTimingWithoutCountingIslandTime() throws InterruptedException {
      Fixture fx = new Fixture(200L);

      // not-ready and a confirmed minority island for well over the threshold: must not halt
      fx.ready = false;
      fx.minorityIsland = true;
      fx.watchdog.check();
      Thread.sleep(250L);
      fx.watchdog.check();
      assertTrue(fx.failures.isEmpty(), "must not halt while excluded as a minority island");

      // the island clears, but the node is still genuinely not-ready (e.g. a stuck exchange);
      // this alone must not immediately cross the threshold
      fx.minorityIsland = false;
      Thread.sleep(100L);
      fx.watchdog.check();
      assertTrue(fx.failures.isEmpty(),
                 "resuming after the island clears must not retroactively credit the excluded " +
                    "island time toward the threshold");

      // once genuine not-ready time (excluding the island stretch) itself exceeds the threshold,
      // it must still halt
      Thread.sleep(150L);
      fx.watchdog.check();
      assertEquals(1, fx.failures.size(),
                   "genuine not-ready time accrued after the island clears must still be able " +
                      "to reach the threshold and halt");
   }

   /**
    * Review finding (external review, community PR #6777): a transient failure sampling the
    * cluster health status (e.g. {@code sampleHealthStatus()} looking up the Spring-managed
    * {@code ClusterHealthService} bean before the application context is fully up) must be an
    * inconclusive tick — it must not be mistaken for a genuine not-ready observation, and must
    * not disturb a not-ready stretch already in progress.
    */
   @Test
   void aThrowingStatusSupplierIsInconclusiveAndDoesNotDisturbAnInProgressStretch()
      throws InterruptedException
   {
      Fixture fx = new Fixture(200L);

      // a throwing tick before any not-ready stretch has started: must not itself start one
      fx.throwOnStatus = true;
      fx.watchdog.check();
      fx.throwOnStatus = false;
      fx.ready = false;
      fx.watchdog.check();
      Thread.sleep(250L);

      // a throwing tick in the middle of an already-started not-ready stretch: must not reset it
      fx.throwOnStatus = true;
      fx.watchdog.check();
      fx.throwOnStatus = false;

      fx.watchdog.check();
      assertEquals(1, fx.failures.size(),
                   "a transient status-sampling failure must not reset an in-progress not-ready " +
                      "stretch, nor itself count as having started one");
   }

   /**
    * Review finding (external review, community PR #6777): if raising the segmentation failure
    * itself throws (e.g. a transient issue reaching the failure processor), the watchdog must
    * retry on a later check rather than giving up or crashing the periodic task.
    */
   @Test
   void raisingTheFailureIsRetriedIfItThrows() throws InterruptedException {
      Fixture fx = new Fixture(200L);
      fx.ready = false;
      fx.failProcessOnce = true;

      fx.watchdog.check();
      Thread.sleep(250L);
      fx.watchdog.check();
      assertTrue(fx.failures.isEmpty(),
                 "the halt attempt that threw must not be recorded as a successful failure");

      fx.watchdog.check();
      assertEquals(1, fx.failures.size(),
                   "a halt that failed to raise must be retried on the next check and succeed " +
                      "once the transient problem clears");
   }

   /**
    * Wires a {@link ClusterHealthWatchdog} to a mocked {@link IgniteEx} whose
    * {@link FailureProcessor#process(FailureContext)} just records the context instead of
    * raising a real failure (which would halt this test JVM, per
    * {@code StopNodeOrHaltFailureHandler}'s default behavior), and to a controllable ready/
    * not-ready flag standing in for {@code ClusterHealthService.getStatus()}.
    */
   private static final class Fixture {
      volatile boolean ready = true;
      volatile boolean minorityIsland = false;
      volatile boolean throwOnStatus = false;
      volatile boolean failProcessOnce = false;
      final List<FailureContext> failures = new CopyOnWriteArrayList<>();
      final ClusterHealthWatchdog watchdog;

      Fixture(long thresholdMillis) {
         IgniteEx ignite = mock(IgniteEx.class);
         GridKernalContext context = mock(GridKernalContext.class);
         FailureProcessor failureProcessor = mock(FailureProcessor.class);
         when(ignite.context()).thenReturn(context);
         when(context.failure()).thenReturn(failureProcessor);
         when(failureProcessor.process(any(FailureContext.class))).thenAnswer(invocation -> {
            if(failProcessOnce) {
               failProcessOnce = false;
               throw new IgniteException("transient failure raising the segmentation failure");
            }

            failures.add(invocation.getArgument(0));
            return true;
         });

         // intervalMillis is irrelevant here: check() is called directly, never via the
         // watchdog's own scheduled executor (start() is never called in this test)
         watchdog = new ClusterHealthWatchdog(
            ignite, this::status, () -> minorityIsland, -1L, thresholdMillis);
      }

      private ClusterHealthStatus status() {
         if(throwOnStatus) {
            throw new RuntimeException("transient failure sampling the cluster health status");
         }

         return new ClusterHealthStatus(ready, ready ? "ready" : "not ready");
      }
   }
}
