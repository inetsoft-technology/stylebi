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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79866, EMScheduleTaskFolderController.moveFolder must check READ on the folder a moved
 * task is taken from, as the portal move does. The check is in the controller, not in
 * ScheduleTaskFolderService.moveScheduleItems, which the task import also calls.
 *
 * The real ScheduleTaskFolderService (spied, only the move itself is stubbed) and a real
 * ScheduleTask are used, so the source folder is the one the task is stored in.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskFolderControllerMoveSourceReadTest {
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleManager scheduleManager;

   private static final IdentityID ALICE = new IdentityID("alice", "orgx");

   private final Principal principal = () -> ALICE.convertToKey();
   private ScheduleTaskFolderService folderService;
   private EMScheduleTaskFolderController controller;
   private MoveTaskFolderRequest request;

   @BeforeEach
   void setUp() throws Exception {
      folderService = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, null, null));
      lenient().doNothing().when(folderService).moveScheduleItems(any(), any(), any(), any());
      controller = new EMScheduleTaskFolderController(
         folderService, scheduleService, scheduleTaskService, securityEngine, scheduleManager);

      ScheduleTask task = new ScheduleTask(
         ALICE.convertToKey() + ":nightly", ScheduleTask.Type.NORMAL_TASK);
      task.setOwner(ALICE);
      task.setPath("A7Folder");
      lenient().when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(task);
      lenient().when(scheduleTaskService.canDeleteTask(task, principal)).thenReturn(true);
      lenient().when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                                    any(ResourceAction.class))).thenReturn(false);

      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .name("nightly").owner(ALICE).ownerAlias("alice").path("A7Folder").label("")
         .description("").editable(true).removable(true).enabled(true).schedule("").build();
      request = mock(MoveTaskFolderRequest.class);
      when(request.getTasks()).thenReturn(new ScheduleTaskModel[] { model });
      lenient().when(request.getFolders()).thenReturn(new String[0]);
      lenient().when(request.getTarget()).thenReturn(
         ContentRepositoryTreeNode.builder().label("Target").path("Target").type(0).build());
   }

   @Test
   void moveFolder_noReadOnSourceFolder_isRefusedAndMovesNothing() throws Exception {
      MessageException e =
         assertThrows(MessageException.class, () -> controller.moveFolder(request, principal));

      assertTrue(e.getMessage().contains("A7Folder"), e.getMessage());
      verify(securityEngine).checkPermission(
         principal, ResourceType.SCHEDULE_TASK_FOLDER, "A7Folder", ResourceAction.READ);
      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   // positive control, the same move goes ahead once the source folder can be read
   @Test
   void moveFolder_readOnSourceFolder_moves() throws Exception {
      when(securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK_FOLDER, "A7Folder", ResourceAction.READ))
         .thenReturn(true);

      controller.moveFolder(request, principal);

      verify(folderService).moveScheduleItems(any(), any(), any(), eq(principal));
   }
}
