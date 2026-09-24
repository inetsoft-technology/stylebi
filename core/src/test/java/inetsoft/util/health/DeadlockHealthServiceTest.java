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
         new DeadlockHealthService(() -> "stall not released: X").getStatus();

      assertTrue(status.isStalled());
      assertEquals("stall not released: X", status.getStallReason());
      assertEquals(0, status.getDeadlockedThreadCount());
   }

   @Test
   public void noStallIsHealthy() {
      DeadlockStatus status = new DeadlockHealthService(() -> null).getStatus();

      assertFalse(status.isStalled());
      assertNull(status.getStallReason());
      assertEquals(0, status.getDeadlockedThreadCount());
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
}
