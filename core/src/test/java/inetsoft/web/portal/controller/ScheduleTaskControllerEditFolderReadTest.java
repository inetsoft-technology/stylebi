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
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.junit.jupiter.api.*;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79866, GET /api/portal/schedule/edit must refuse a task in a folder the user can't read,
 * as the EM edit does.
 */
@Tag("core")
class ScheduleTaskControllerEditFolderReadTest {
   private static final IdentityID ALICE = new IdentityID("alice", "orgx");
   private final Principal principal = () -> ALICE.convertToKey();
   private ScheduleTaskService taskService;
   private ScheduleTaskFolderService folderService;
   private ScheduleManager scheduleManager;
   private ScheduleTaskController controller;
   private ScheduleTask task;

   @BeforeEach
   void setUp() {
      taskService = mock(ScheduleTaskService.class);
      folderService = mock(ScheduleTaskFolderService.class);
      scheduleManager = mock(ScheduleManager.class);
      controller = new ScheduleTaskController(
         taskService, folderService, mock(SecurityEngine.class), scheduleManager);

      String id = ALICE.convertToKey() + ":nightly";
      task = new ScheduleTask(id, ScheduleTask.Type.NORMAL_TASK);
      task.setOwner(ALICE);
      task.setPath("A7Folder");
      when(scheduleManager.getScheduleTask(id)).thenReturn(task);
   }

   @Test
   void edit_noReadOnTaskFolder_isRefused() throws Exception {
      when(folderService.checkFolderPermission("A7Folder", principal, ResourceAction.READ))
         .thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> controller.getDialogModel(task.getTaskId(), principal));

      verify(taskService, never()).getDialogModel(anyString(), any());
   }

   // positive control, the same request is served once the folder can be read
   @Test
   void edit_readOnTaskFolder_isServed() throws Exception {
      when(folderService.checkFolderPermission("A7Folder", principal, ResourceAction.READ))
         .thenReturn(true);

      controller.getDialogModel(task.getTaskId(), principal);

      verify(taskService).getDialogModel(task.getTaskId(), principal);
   }
}
