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
package inetsoft.web.admin.schedule;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.web.cluster.ServerClusterClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77358: SchedulerMonitoringService.runTask()/stopTask() (JMX ScheduleMonitorMBean) looked
 * the task up with ScheduleManager.getScheduleTask(name) and then passed the RAW client name to
 * scheduleClient.runNow()/stopNow(), even when the lookup found nothing or resolved (via the
 * legacy ':' fallback) to a different task. They must run/stop the resolved task id and refuse
 * with "task not found" when the lookup returns null, as ScheduleService does after #77262.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchedulerMonitoringServiceRunStopRawNameTest {
   private static final String RAW_NAME = "bob~;~orgB:Nightly";   // other org's quartz job
   private static final String RESOLVED_ID = "Nightly";           // this org's internal task
   private static final String UNKNOWN_NAME = "eve~;~orgC:Ghost";
   private static final String HOST_ID = "admin~;~host-org:Daily";
   private static final String INTERNAL_ID = "__balance tasks__";
   private static final String LEGACY_NAME = "admin~;~host-org:Nightly";

   @Mock private ScheduleManager scheduleManager;
   @Mock private DataCycleManager cycleManager;
   @Mock private SecurityProvider securityProvider;
   @Mock private ServerClusterClient client;
   @Mock private Cluster cluster;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleClient scheduleClient;
   @Mock private ScheduleTask resolvedTask;
   @Mock private ScheduleTask hostTask;
   @Mock private ScheduleTask internalTask;

   private SchedulerMonitoringService service;

   @BeforeEach
   void setUp() throws Exception {
      when(scheduleClient.isReady()).thenReturn(true);

      when(resolvedTask.getTaskId()).thenReturn(RESOLVED_ID);
      when(resolvedTask.isEnabled()).thenReturn(true);
      when(hostTask.getTaskId()).thenReturn(HOST_ID);
      when(hostTask.isEnabled()).thenReturn(true);
      when(internalTask.getTaskId()).thenReturn(INTERNAL_ID);
      when(internalTask.isEnabled()).thenReturn(true);

      // legacy fallback: the other-org name / a prefixed legacy name resolve to this org's
      // bare-id "Nightly"
      when(scheduleManager.getScheduleTask(RAW_NAME)).thenReturn(resolvedTask);
      when(scheduleManager.getScheduleTask(LEGACY_NAME)).thenReturn(resolvedTask);
      when(scheduleManager.getScheduleTask(UNKNOWN_NAME)).thenReturn(null);
      when(scheduleManager.getScheduleTask(HOST_ID)).thenReturn(hostTask);
      when(scheduleManager.getScheduleTask(INTERNAL_ID)).thenReturn(internalTask);

      service = new SchedulerMonitoringService(scheduleManager, cycleManager, securityProvider,
                                               client, cluster, securityEngine, scheduleClient);
   }

   @Test
   void runTask_resolvedToDifferentTask_runsResolvedIdNotRawName() throws Exception {
      service.runTask(RAW_NAME);

      verify(scheduleClient, never()).runNow(RAW_NAME);
      verify(scheduleClient).runNow(RESOLVED_ID);
   }

   @Test
   void stopTask_resolvedToDifferentTask_stopsResolvedIdNotRawName() throws Exception {
      service.stopTask(RAW_NAME);

      verify(scheduleClient, never()).stopNow(RAW_NAME);
      verify(scheduleClient).stopNow(RESOLVED_ID);
   }

   @Test
   void runTask_taskNotFound_throwsAndRunsNothing() throws Exception {
      Exception ex = assertThrows(Exception.class, () -> service.runTask(UNKNOWN_NAME));

      assertEquals(Catalog.getCatalog().getString("scheduleManager.taskNotFound"),
                   ex.getMessage());
      verify(scheduleClient, never()).runNow(anyString());
   }

   @Test
   void stopTask_taskNotFound_throwsAndStopsNothing() throws Exception {
      Exception ex = assertThrows(Exception.class, () -> service.stopTask(UNKNOWN_NAME));

      assertEquals(Catalog.getCatalog().getString("scheduleManager.taskNotFound"),
                   ex.getMessage());
      verify(scheduleClient, never()).stopNow(anyString());
   }

   @Test
   void runAndStop_hostOrgTaskId_passesThroughUnchanged() throws Exception {
      service.runTask(HOST_ID);
      service.stopTask(HOST_ID);

      verify(scheduleClient).runNow(HOST_ID);
      verify(scheduleClient).stopNow(HOST_ID);
   }

   @Test
   void runAndStop_internalTask_usesItsOwnId() throws Exception {
      service.runTask(INTERNAL_ID);
      service.stopTask(INTERNAL_ID);

      verify(scheduleClient).runNow(INTERNAL_ID);
      verify(scheduleClient).stopNow(INTERNAL_ID);
   }

   @Test
   void runAndStop_legacyPrefixedName_usesResolvedBareId() throws Exception {
      service.runTask(LEGACY_NAME);
      service.stopTask(LEGACY_NAME);

      verify(scheduleClient).runNow(RESOLVED_ID);
      verify(scheduleClient).stopNow(RESOLVED_ID);
      verify(scheduleClient, never()).runNow(LEGACY_NAME);
      verify(scheduleClient, never()).stopNow(LEGACY_NAME);
   }

   @Test
   void runTask_disabledTask_throwsFailedRunWithResolvedId() throws Exception {
      when(resolvedTask.isEnabled()).thenReturn(false);

      Exception ex = assertThrows(Exception.class, () -> service.runTask(RAW_NAME));

      assertEquals(Catalog.getCatalog().getString("em.schedule.task.failedRun", RESOLVED_ID),
                   ex.getMessage());
      verify(scheduleClient, never()).runNow(anyString());
   }

   @Test
   void stopTask_nullName_isNoOp() throws Exception {
      service.stopTask(null);

      verifyNoInteractions(scheduleManager);
      verify(scheduleClient, never()).stopNow(anyString());
   }
}
