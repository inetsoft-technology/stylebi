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
package inetsoft.util.health;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An unreleased lock stall flips the deadlock health DOWN, a resolved one does not
 * (bug #76967).
 */
@Tag("core")
public class DeadlockHealthServiceTest {
   @Test
   public void unreleasedStallIsReported() {
      DeadlockStatus status =
         new DeadlockHealthService(() -> "stall not released: X", NO_DEADLOCK).getStatus();

      assertTrue(status.isStalled());
      assertEquals("stall not released: X", status.getStallReason());
      assertEquals(0, status.getDeadlockedThreadCount());
   }

   @Test
   public void noStallIsHealthy() {
      DeadlockStatus status = new DeadlockHealthService(() -> null, NO_DEADLOCK).getStatus();

      assertFalse(status.isStalled());
      assertNull(status.getStallReason());
      assertEquals(0, status.getDeadlockedThreadCount());
   }

   /**
    * The watchdog also reports a JVM deadlock, which the status already shows as deadlocked
    * threads: it is not repeated as a lock stall, while an unreleased wait still is.
    */
   @Test
   public void jvmDeadlockIsNotRepeatedAsALockStall() {
      DeadlockStatus both = new DeadlockHealthService(
         () -> "stall not released: X, thread dump: a.txt; JVM deadlock of 2 threads",
         ONE_DEADLOCKED).getStatus();

      assertEquals(1, both.getDeadlockedThreadCount());
      assertEquals("stall not released: X, thread dump: a.txt", both.getStallReason());

      DeadlockStatus deadlockOnly = new DeadlockHealthService(
         () -> "JVM deadlock of 2 threads", ONE_DEADLOCKED).getStatus();

      assertEquals(1, deadlockOnly.getDeadlockedThreadCount());
      assertFalse(deadlockOnly.isStalled());
      assertNull(deadlockOnly.getStallReason());
   }

   @Test
   public void jvmDeadlockOfTheWatchdogIsKeptWhenTheStatusHasNone() {
      DeadlockStatus status =
         new DeadlockHealthService(() -> "JVM deadlock of 2 threads", NO_DEADLOCK).getStatus();

      assertEquals(0, status.getDeadlockedThreadCount());
      assertEquals("JVM deadlock of 2 threads", status.getStallReason());
   }

   @Test
   public void stalledStatusMakesHealthDown() {
      assertTrue(health(new DeadlockStatus(0, new DeadlockedThread[0], "stall")).isDown());
      assertFalse(health(new DeadlockStatus()).isDown());
   }

   private static HealthStatus health(DeadlockStatus deadlockStatus) {
      SchedulerStatus scheduler = mock(SchedulerStatus.class);
      when(scheduler.isHealthy()).thenReturn(true);
      return new HealthStatus(new CacheSwapStatus(), deadlockStatus,
                              mock(OutOfMemoryStatus.class), mock(ReportFailureStatus.class),
                              scheduler, mock(SecurityProviderStatus.class),
                              mock(FileSystemStatus.class));
   }

   // injected, so a JVM deadlock left by another test in the same fork cannot change the result
   private static final Supplier<ThreadInfo[]> NO_DEADLOCK = () -> null;
   private static final Supplier<ThreadInfo[]> ONE_DEADLOCKED = () -> new ThreadInfo[] {
      ManagementFactory.getThreadMXBean().getThreadInfo(Thread.currentThread().threadId())
   };
}
