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
package inetsoft.web.admin.security;

import inetsoft.sree.schedule.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.rmi.RemoteException;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] - Bug #79868. removeStorages() is what an org delete and an org id rename-away
 * go through; it must make every node drop its in-memory schedule task caches of the org, also
 * when the scheduler cannot be reached. The removal of the other storages is stopped by a
 * sentinel exception thrown from the dashboard manager, which comes right after the call.
 */
@Tag("core")
class IdentityServiceRemoveStoragesScheduleCacheTest {
   private static final String ORG = "org79868";

   private IdentityService service;
   private ScheduleManager scheduleManager;
   private ScheduleClient scheduleClient;
   private DashboardManager dashboardManager;

   @BeforeEach
   void setUp() {
      scheduleManager = mock(ScheduleManager.class);
      scheduleClient = mock(ScheduleClient.class);
      dashboardManager = mock(DashboardManager.class);
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
      ReflectionTestUtils.setField(service, "scheduleClient", scheduleClient);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "scheduleServer", mock(ScheduleServer.class));
   }

   @Test
   void removeStoragesClearsScheduleCachesOnEveryNode() throws Exception {
      doThrow(new IllegalStateException("stop")).when(dashboardManager)
         .removeDashboardStorage(ORG);

      assertThrows(IllegalStateException.class, () -> service.removeStorages(ORG));

      verify(scheduleManager).orgTasksRemoved(ORG);
   }

   @Test
   void schedulerFailureDoesNotSkipTheClusterClear() throws Exception {
      ScheduleTask task = new ScheduleTask("Task79868");
      when(scheduleManager.getScheduleTasks(ORG)).thenReturn(new Vector<>(java.util.List.of(task)));
      doThrow(new RemoteException("scheduler down")).when(scheduleClient).taskRemoved(any());

      assertThrows(RemoteException.class, () -> service.removeStorages(ORG));

      verify(scheduleManager).orgTasksRemoved(ORG);
      verify(dashboardManager, never()).removeDashboardStorage(any());
   }
}
