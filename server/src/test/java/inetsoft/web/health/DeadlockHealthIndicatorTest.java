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
package inetsoft.web.health;

import inetsoft.util.StatusDumpService;
import inetsoft.util.health.DeadlockHealthService;
import inetsoft.util.health.DeadlockStatus;
import inetsoft.util.health.DeadlockedThread;
import inetsoft.util.health.StatusDumpLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadlockHealthIndicatorTest {
   @Mock
   private DeadlockHealthService service;
   @Mock
   private StatusDumpService statusDumpService;

   @Test
   void health_unreleasedStall_reportsDown() {
      when(service.getStatus())
         .thenReturn(new DeadlockStatus(0, new DeadlockedThread[0], "stall not released: X"));

      Health health = new DeadlockHealthIndicator(service, statusDumpService).health();

      assertEquals(Status.DOWN, health.getStatus());
      assertEquals(Map.of("reason", "stall not released: X"), health.getDetails().get("lockStall"));
      verify(statusDumpService).dumpStatus();
   }

   @Test
   void health_downOnEveryPoll_dumpsStatusOncePerInterval() {
      when(service.getStatus())
         .thenReturn(new DeadlockStatus(0, new DeadlockedThread[0], "stall not released: X"));
      AtomicLong now = new AtomicLong();
      DeadlockHealthIndicator indicator = new DeadlockHealthIndicator(
         service, statusDumpService, new StatusDumpLimiter(now::get, 600000));

      for(int i = 0; i < 10; i++) {
         assertEquals(Status.DOWN, indicator.health().getStatus());
         now.addAndGet(TimeUnit.SECONDS.toNanos(60));
      }

      verify(statusDumpService, times(1)).dumpStatus();
      assertEquals(Status.DOWN, indicator.health().getStatus());
      verify(statusDumpService, times(2)).dumpStatus();
   }

   @Test
   void health_jvmDeadlockOnEveryPoll_dumpsStatusOnEveryPoll() {
      // as before bug #76967: only a lock stall alone is rate-limited
      when(service.getStatus()).thenReturn(new DeadlockStatus(
         2, new DeadlockedThread[] { new DeadlockedThread("t1", "l1", "t2"),
                                     new DeadlockedThread("t2", "l2", "t1") },
         "stall not released: X"));
      DeadlockHealthIndicator indicator = new DeadlockHealthIndicator(service, statusDumpService);

      for(int i = 0; i < 5; i++) {
         assertEquals(Status.DOWN, indicator.health().getStatus());
      }

      verify(statusDumpService, times(5)).dumpStatus();
   }

   @Test
   void health_downOnEveryPollWithTheDefaultLimiter_dumpsStatusOnce() {
      when(service.getStatus())
         .thenReturn(new DeadlockStatus(0, new DeadlockedThread[0], "stall not released: X"));
      DeadlockHealthIndicator indicator = new DeadlockHealthIndicator(service, statusDumpService);

      for(int i = 0; i < 5; i++) {
         assertEquals(Status.DOWN, indicator.health().getStatus());
      }

      verify(statusDumpService, times(1)).dumpStatus();
   }

   @Test
   void health_noDeadlockNoStall_reportsUp() {
      when(service.getStatus()).thenReturn(new DeadlockStatus());

      Health health = new DeadlockHealthIndicator(service, statusDumpService).health();

      assertEquals(Status.UP, health.getStatus());
      verifyNoInteractions(statusDumpService);
   }
}
