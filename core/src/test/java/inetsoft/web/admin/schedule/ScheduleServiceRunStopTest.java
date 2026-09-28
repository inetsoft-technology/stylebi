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
package inetsoft.web.admin.schedule;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Run/stop must only act on tasks that resolve in the caller's organization. A task name from
 * another organization is not found in the caller's org and must be rejected rather than passed
 * through to the global quartz scheduler.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleServiceRunStopTest {
   private static final String ORG_A = "orgA";
   private static final String FOREIGN_TASK = "bob~;~orgB:Nightly";
   private static final String OWN_TASK = "alice~;~orgA:Daily";

   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleClient scheduleClient;
   @Mock
   private Principal principal;
   @Mock
   private OrganizationManager organizationManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private ScheduleService service;

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      lenient().when(organizationManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);
      scheduleManagerStatic = mockStatic(ScheduleManager.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      service = new ScheduleService(null, scheduleManager, scheduleClient, null, null, null,
                                    null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      scheduleManagerStatic.close();
      orgManagerStatic.close();
   }

   @Test
   void runScheduledTask_taskFromOtherOrg_rejectedAndNotRun() throws Exception {
      when(scheduleManager.getScheduleTask(FOREIGN_TASK, ORG_A)).thenReturn(null);
      // scheduler is up, so only the org check can prevent the call
      lenient().when(scheduleClient.isReady()).thenReturn(true);

      assertThrows(MessageException.class,
                   () -> service.runScheduledTask(FOREIGN_TASK, principal));
      verify(scheduleClient, never()).runNow(anyString());
   }

   @Test
   void stopScheduledTask_taskFromOtherOrg_rejectedAndNotStopped() throws Exception {
      when(scheduleManager.getScheduleTask(FOREIGN_TASK, ORG_A)).thenReturn(null);
      // scheduler is up, so only the org check can prevent the call
      lenient().when(scheduleClient.isReady()).thenReturn(true);

      assertThrows(MessageException.class,
                   () -> service.stopScheduledTask(FOREIGN_TASK, principal));
      verify(scheduleClient, never()).stopNow(anyString());
   }

   @Test
   void runScheduledTask_noPermission_throwsSecurityException() throws Exception {
      ScheduleTask task = ownTask();
      when(scheduleManager.getScheduleTask(OWN_TASK, ORG_A)).thenReturn(task);
      permitted(false);

      assertThrows(SecurityException.class, () -> service.runScheduledTask(OWN_TASK, principal));
      verify(scheduleClient, never()).runNow(anyString());
   }

   @Test
   void stopScheduledTask_noPermission_throwsSecurityException() throws Exception {
      ScheduleTask task = ownTask();
      when(scheduleManager.getScheduleTask(OWN_TASK, ORG_A)).thenReturn(task);
      permitted(false);

      assertThrows(SecurityException.class, () -> service.stopScheduledTask(OWN_TASK, principal));
      verify(scheduleClient, never()).stopNow(anyString());
   }

   @Test
   void runScheduledTask_ownPermittedTask_runs() throws Exception {
      ScheduleTask task = ownTask();
      when(scheduleManager.getScheduleTask(OWN_TASK, ORG_A)).thenReturn(task);
      permitted(true);
      when(scheduleClient.isReady()).thenReturn(true);

      // the audit record reads server properties, which are not available in a unit test
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.getActionRecord(
            any(Principal.class), anyString(), anyString(), anyString()))
            .thenReturn(mock(ActionRecord.class));

         service.runScheduledTask(OWN_TASK, principal);
      }

      verify(scheduleClient).runNow(OWN_TASK);
   }

   @Test
   void stopScheduledTask_ownPermittedTask_stops() throws Exception {
      ScheduleTask task = ownTask();
      when(scheduleManager.getScheduleTask(OWN_TASK, ORG_A)).thenReturn(task);
      permitted(true);
      when(scheduleClient.isReady()).thenReturn(true);

      service.stopScheduledTask(OWN_TASK, principal);

      verify(scheduleClient).stopNow(OWN_TASK);
   }

   private ScheduleTask ownTask() {
      ScheduleTask task = new ScheduleTask("Daily");
      task.setOwner(new IdentityID("alice", ORG_A));
      task.setEnabled(true);
      return task;
   }

   private void permitted(boolean allowed) {
      scheduleManagerStatic.when(() -> ScheduleManager.hasTaskPermission(
         any(IdentityID.class), eq(principal), eq(ResourceAction.READ))).thenReturn(allowed);
   }
}
