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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.BatchActionModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77531: a {@code BatchAction} may not be saved with one of the three internal tasks
 * (asset file backup, task balancer, update assets dependencies) as its target unless the
 * saving principal is a site admin. Those internal actions ignore the principal they run with
 * and have no permission check of their own, so letting an ordinary task's BatchAction name one
 * as its target lets any user who can save a schedule task trigger it on demand.
 */
@Tag("core")
class ScheduleServiceBatchActionTest {
   @BeforeEach
   void setUp() {
      service = new ScheduleService(null, null, null, null, null, mock(DeployService.class), null,
                                    null, null, null, null, null, null);
   }

   @Test
   void refusesInternalTaskTargetForNonSiteAdmin() {
      BatchActionModel model = batchActionModel(InternalScheduledTaskService.ASSET_FILE_BACKUP);
      Principal principal = mock(Principal.class);

      try(MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<OrganizationManager> orgManager = mockStatic(OrganizationManager.class))
      {
         scheduleManager.when(() -> ScheduleManager.isInternalTask(
            InternalScheduledTaskService.ASSET_FILE_BACKUP)).thenReturn(true);

         OrganizationManager organizationManager = mock(OrganizationManager.class);
         orgManager.when(OrganizationManager::getInstance).thenReturn(organizationManager);
         when(organizationManager.isSiteAdmin(principal)).thenReturn(false);

         assertThrows(SecurityException.class,
                      () -> service.getActionFromModel(model, null, principal, "http://host/"));
      }
   }

   @Test
   void allowsInternalTaskTargetForSiteAdmin() throws Exception {
      BatchActionModel model = batchActionModel(InternalScheduledTaskService.ASSET_FILE_BACKUP);
      Principal principal = mock(Principal.class);

      try(MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<OrganizationManager> orgManager = mockStatic(OrganizationManager.class))
      {
         scheduleManager.when(() -> ScheduleManager.isInternalTask(
            InternalScheduledTaskService.ASSET_FILE_BACKUP)).thenReturn(true);

         OrganizationManager organizationManager = mock(OrganizationManager.class);
         orgManager.when(OrganizationManager::getInstance).thenReturn(organizationManager);
         when(organizationManager.isSiteAdmin(principal)).thenReturn(true);

         BatchAction action = (BatchAction) service.getActionFromModel(
            model, null, principal, "http://host/");
         assertEquals(InternalScheduledTaskService.ASSET_FILE_BACKUP, action.getTaskId());
      }
   }

   @Test
   void allowsOrdinaryTaskTargetForNonSiteAdmin() throws Exception {
      BatchActionModel model = batchActionModel("ordinary-task");
      Principal principal = mock(Principal.class);

      try(MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<OrganizationManager> orgManager = mockStatic(OrganizationManager.class))
      {
         scheduleManager.when(() -> ScheduleManager.isInternalTask("ordinary-task"))
            .thenReturn(false);

         OrganizationManager organizationManager = mock(OrganizationManager.class);
         orgManager.when(OrganizationManager::getInstance).thenReturn(organizationManager);
         when(organizationManager.isSiteAdmin(principal)).thenReturn(false);

         BatchAction action = (BatchAction) service.getActionFromModel(
            model, null, principal, "http://host/");
         assertEquals("ordinary-task", action.getTaskId());
      }
   }

   private static BatchActionModel batchActionModel(String taskName) {
      BatchActionModel model = mock(BatchActionModel.class);
      when(model.actionType()).thenReturn("BatchAction");
      when(model.taskName()).thenReturn(taskName);
      when(model.queryEnabled()).thenReturn(false);
      when(model.embeddedEnabled()).thenReturn(false);
      return model;
   }

   private ScheduleService service;
}
