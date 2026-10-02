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
package inetsoft.web.portal.controller;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.admin.schedule.model.PortalNewTaskRequest;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77379, a new portal task is created in a folder of the user's organization, not in the
 * organization named by the client's folder entry.
 */
@Tag("core")
class ScheduleTaskControllerNewTaskParentTest {
   @Test
   void parentOfOtherOrg_isFolderOfUserOrg() throws Exception {
      ScheduleTaskService taskService = mock(ScheduleTaskService.class);
      ScheduleTaskFolderService folderService = mock(ScheduleTaskFolderService.class);
      when(folderService.checkFolderPermission(anyString(), any(), any())).thenReturn(true);
      ScheduleTaskController controller = new ScheduleTaskController(
         taskService, folderService, mock(SecurityEngine.class), mock(ScheduleManager.class));
      Principal principal = mock(Principal.class);
      PortalNewTaskRequest request = new PortalNewTaskRequest();
      request.setParentEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.SCHEDULE_TASK_FOLDER, "Mine", null,
                                            "orgb"));

      controller.getNewTaskDialogModel(request, principal);

      ArgumentCaptor<PortalNewTaskRequest> captor =
         ArgumentCaptor.forClass(PortalNewTaskRequest.class);
      verify(taskService).getNewTaskDialogModel(captor.capture(), eq(principal));
      AssetEntry parent = captor.getValue().getParentEntry();
      assertEquals("Mine", parent.getPath());
      assertEquals(AssetEntry.Type.SCHEDULE_TASK_FOLDER, parent.getType());
      assertNotEquals("orgb", parent.getOrgID());
      verify(folderService).checkFolderPermission("Mine", principal, ResourceAction.READ);
   }
}
