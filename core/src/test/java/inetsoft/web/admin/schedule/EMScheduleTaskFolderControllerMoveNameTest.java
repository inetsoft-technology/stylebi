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

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77503, EMScheduleTaskFolderController.moveFolder must check the permission on the task
 * that ScheduleTaskFolderService.moveScheduleItems moves. The move resolves the task by
 * getTaskName(taskModel), which prefixes the client-sent owner to a bare name, so a request
 * {name: N, owner: victim} moves the task stored under victim:N, not the task stored under N.
 *
 * The real ScheduleTaskFolderService.getMovableTask is used (only the move itself is stubbed),
 * so the check and the move resolve the task the same way as in production.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskFolderControllerMoveNameTest {
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleManager scheduleManager;
   @Mock private Principal principal;

   private static final IdentityID VICTIM = new IdentityID("bob", "orgx");
   private static final String VICTIM_TASK_ID = VICTIM.convertToKey() + ":nightly";

   private ScheduleTaskFolderService folderService;
   private EMScheduleTaskFolderController controller;
   private ScheduleTask legacyOwnTask;
   private ScheduleTask victimTask;
   private MoveTaskFolderRequest request;

   @BeforeEach
   void setUp() throws Exception {
      folderService = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, null, null));
      lenient().doNothing().when(folderService).moveScheduleItems(any(), any(), any(), any());
      controller = new EMScheduleTaskFolderController(
         folderService, scheduleService, scheduleTaskService, securityEngine, scheduleManager);

      // a pre-13.1 task stored under the bare key that the caller may delete, and the
      // victim's task stored under the owner-prefixed key that the caller may not delete
      legacyOwnTask = mock(ScheduleTask.class, "legacyOwnTask(nightly)");
      victimTask = mock(ScheduleTask.class, "victimTask(" + VICTIM_TASK_ID + ")");
      lenient().when(victimTask.getTaskId()).thenReturn(VICTIM_TASK_ID);
      lenient().when(victimTask.getOwner()).thenReturn(VICTIM);
      lenient().when(victimTask.isRemovable()).thenReturn(true);
      lenient().when(victimTask.getType()).thenReturn(ScheduleTask.Type.NORMAL_TASK);

      lenient().when(scheduleManager.getScheduleTask("nightly")).thenReturn(legacyOwnTask);
      lenient().when(scheduleManager.getScheduleTask(VICTIM_TASK_ID)).thenReturn(victimTask);
      lenient().when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                                    any(ResourceAction.class))).thenReturn(false);
      lenient().when(scheduleTaskService.canDeleteTask(legacyOwnTask, principal)).thenReturn(true);

      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .name("nightly").owner(VICTIM).ownerAlias("bob").path("/").label("").description("")
         .editable(true).removable(true).enabled(true).schedule("").build();

      // the name the move itself resolves
      Method getTaskName =
         ScheduleTaskFolderService.class.getDeclaredMethod("getTaskName", ScheduleTaskModel.class);
      getTaskName.setAccessible(true);
      assertEquals(VICTIM_TASK_ID, getTaskName.invoke(null, model));

      request = mock(MoveTaskFolderRequest.class);
      when(request.getTasks()).thenReturn(new ScheduleTaskModel[] { model });
      lenient().when(request.getFolders()).thenReturn(new String[0]);
      lenient().when(request.getTarget()).thenReturn(
         ContentRepositoryTreeNode.builder().label("Target").path("Target").type(0).build());
   }

   @Test
   void moveFolder_checksTheTaskThatIsMoved() throws Exception {
      when(scheduleTaskService.canDeleteTask(victimTask, principal)).thenReturn(false);

      // Bug #77813, the refusal is an error, not a 200 as if the move had worked
      assertThrows(MessageException.class, () -> controller.moveFolder(request, principal));

      verify(folderService, never().description(
         "moveFolder passed the permission check on the task stored under 'nightly' and moved " +
         VICTIM_TASK_ID + ", which the caller may not delete"))
         .moveScheduleItems(any(), any(), any(), any());
      verify(securityEngine).checkPermission(
         principal, ResourceType.SCHEDULE_TASK, VICTIM_TASK_ID, ResourceAction.WRITE);
   }

   // positive control, the move goes ahead when the caller may delete the task that is moved
   @Test
   void moveFolder_movesWhenTheMovedTaskCanBeDeleted() throws Exception {
      when(scheduleTaskService.canDeleteTask(victimTask, principal)).thenReturn(true);

      controller.moveFolder(request, principal);

      verify(folderService).moveScheduleItems(any(), any(), any(), eq(principal));
   }
}
