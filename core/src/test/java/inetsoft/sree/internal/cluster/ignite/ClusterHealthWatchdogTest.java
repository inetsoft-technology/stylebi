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
 * sub-threshold not-ready stretches add up to a halt.
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
    * Wires a {@link ClusterHealthWatchdog} to a mocked {@link IgniteEx} whose
    * {@link FailureProcessor#process(FailureContext)} just records the context instead of
    * raising a real failure (which would halt this test JVM, per
    * {@code StopNodeOrHaltFailureHandler}'s default behavior), and to a controllable ready/
    * not-ready flag standing in for {@code ClusterHealthService.getStatus()}.
    */
   private static final class Fixture {
      volatile boolean ready = true;
      final List<FailureContext> failures = new CopyOnWriteArrayList<>();
      final ClusterHealthWatchdog watchdog;

      Fixture(long thresholdMillis) {
         IgniteEx ignite = mock(IgniteEx.class);
         GridKernalContext context = mock(GridKernalContext.class);
         FailureProcessor failureProcessor = mock(FailureProcessor.class);
         when(ignite.context()).thenReturn(context);
         when(context.failure()).thenReturn(failureProcessor);
         when(failureProcessor.process(any(FailureContext.class))).thenAnswer(invocation -> {
            failures.add(invocation.getArgument(0));
            return true;
         });

         // intervalMillis is irrelevant here: check() is called directly, never via the
         // watchdog's own scheduled executor (start() is never called in this test)
         watchdog = new ClusterHealthWatchdog(ignite, this::status, -1L, thresholdMillis);
      }

      private ClusterHealthStatus status() {
         return new ClusterHealthStatus(ready, ready ? "ready" : "not ready");
      }
   }
}
