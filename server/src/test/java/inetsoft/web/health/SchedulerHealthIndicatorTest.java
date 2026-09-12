/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.util.StatusDumpService;
import inetsoft.util.health.HealthStatus;
import inetsoft.util.health.SchedulerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Optional;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerHealthIndicatorTest {
   @Mock
   private ScheduleClient client;
   @Mock
   private StatusDumpService statusDumpService;
   @Mock
   private HealthStatus healthStatus;

   private SchedulerHealthIndicator indicator;

   @BeforeEach
   void setUp() {
      indicator = new SchedulerHealthIndicator(client, statusDumpService);
   }

   @Test
   void health_guardFalse_reportsUnknown() {
      when(client.isCloud()).thenReturn(false);
      when(client.isAutoStart()).thenReturn(false);

      Health health = indicator.health();

      assertEquals(Status.UNKNOWN, health.getStatus());
      verifyNoInteractions(statusDumpService);
   }

   @Test
   void health_statusAbsent_reportsUnknown() throws Exception {
      when(client.isCloud()).thenReturn(true);
      when(client.getHealthStatus()).thenReturn(Optional.empty());

      Health health = indicator.health();

      assertEquals(Status.UNKNOWN, health.getStatus());
   }

   @Test
   void health_getHealthStatusThrows_reportsUnknown() throws Exception {
      when(client.isCloud()).thenReturn(true);
      when(client.getHealthStatus()).thenThrow(new RuntimeException("unreachable"));

      Health health = indicator.health();

      assertEquals(Status.UNKNOWN, health.getStatus());
   }

   @Test
   void health_schedulerHealthy_reportsUp() throws Exception {
      SchedulerStatus schedulerStatus = new SchedulerStatus(
         true, false, false, 0L, 0L, Future.State.RUNNING, 0, 4);
      when(client.isCloud()).thenReturn(true);
      when(client.getHealthStatus()).thenReturn(Optional.of(healthStatus));
      when(healthStatus.getSchedulerStatus()).thenReturn(schedulerStatus);

      Health health = indicator.health();

      assertEquals(Status.UP, health.getStatus());
   }

   @Test
   void health_schedulerUnhealthy_reportsDown() throws Exception {
      SchedulerStatus schedulerStatus = new SchedulerStatus(
         false, true, false, 0L, 0L, Future.State.RUNNING, 0, 4);
      when(client.isCloud()).thenReturn(true);
      when(client.getHealthStatus()).thenReturn(Optional.of(healthStatus));
      when(healthStatus.getSchedulerStatus()).thenReturn(schedulerStatus);

      Health health = indicator.health();

      assertEquals(Status.DOWN, health.getStatus());
      verify(statusDumpService).dumpStatus();
   }
}
