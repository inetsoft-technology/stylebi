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

/*
 * Test strategy
 *
 * EMScheduleTaskController has real in-controller logic in three methods:
 *   getNewTaskDialogModel — folder permission guard before delegation
 *   getDialogModel        — multi-step permission check (WRITE+READ required; DELETE for external tasks)
 *   toggleTaskEnabled     — per-task permission loop; early return on first denial
 *
 * saveTask is a pure-delegation method and is tested as such.
 *
 * Coverage scope:
 *   [getNewTaskDialogModel: no folder permission]  checkFolderPermission returns false → SecurityException
 *   [getNewTaskDialogModel: with permission]        permission granted → service model returned
 *   [getDialogModel: no write permission]           checkTaskPermission(WRITE) false → SecurityException
 *   [getDialogModel: external task, all perms]      WRITE+READ+DELETE granted → service model returned
 *   [saveTask]                                      pure delegation to scheduleTaskService.saveTask()
 *   [toggleTaskEnabled: no permission]              first task fails check → empty response, no toggle
 *   [toggleTaskEnabled: with permission]            permission granted → toggles enabled state
 *
 * Static singletons (ScheduleManager.isInternalTask, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskControllerTest {

   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private ScheduleTaskFolderService scheduleTaskFolderService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleTask scheduleTask;
   @Mock private ScheduleTaskDialogModel dialogModel;
   @Mock private ScheduleTaskEditorModel editorModel;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private EMScheduleTaskController controller;

   private MockedStatic<ScheduleManager> scheduleManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new EMScheduleTaskController(
         scheduleTaskService, scheduleTaskFolderService, securityEngine, scheduleManager);

      scheduleManagerStatic = mockStatic(ScheduleManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("msg");
      lenient().when(catalog.getString(anyString(), any())).thenReturn("msg");
      // default: tasks are not internal
      scheduleManagerStatic.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(false);
   }

   @AfterEach
   void tearDown() {
      scheduleManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // getNewTaskDialogModel()
   // -------------------------------------------------------------------------

   // [no folder permission] checkFolderPermission on "/" returns false → SecurityException
   @Test
   void getNewTaskDialogModel_noFolderPermission_throwsSecurityException() throws Exception {
      when(scheduleTaskFolderService.checkFolderPermission("/", principal, ResourceAction.READ))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.getNewTaskDialogModel("UTC", null, null, principal));

      verify(scheduleTaskService, never()).getNewTaskDialogModel(
         any(), any(Principal.class), anyBoolean(), anyBoolean(), any(), anyString(), any());
   }

   // [with permission] permission granted on root "/" → delegates to scheduleTaskService
   @Test
   void getNewTaskDialogModel_withPermission_delegatesToService() throws Exception {
      when(scheduleTaskFolderService.checkFolderPermission("/", principal, ResourceAction.READ))
         .thenReturn(true);
      when(scheduleTaskService.getNewTaskDialogModel(
         isNull(), eq(principal), eq(true), eq(true), isNull(), eq("UTC"), isNull()))
         .thenReturn(dialogModel);

      ScheduleTaskDialogModel result =
         controller.getNewTaskDialogModel("UTC", null, null, principal);

      assertSame(dialogModel, result);
   }

   // -------------------------------------------------------------------------
   // getDialogModel()
   // -------------------------------------------------------------------------

   // [no write permission] checkTaskPermission(WRITE) false → canEdit=false → SecurityException
   @Test
   void getDialogModel_noWritePermission_throwsSecurityException() throws Exception {
      when(scheduleTaskService.checkTaskPermission("myTask", principal, ResourceAction.WRITE))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.getDialogModel("myTask", principal));

      verify(scheduleTaskService, never()).getDialogModel(anyString(), any(Principal.class), anyBoolean());
   }

   // [external task, all perms] WRITE+READ+DELETE all granted → delegates to service
   @Test
   void getDialogModel_externalTaskAllPermissions_delegatesToService() throws Exception {
      when(scheduleTaskService.checkTaskPermission("myTask", principal, ResourceAction.WRITE))
         .thenReturn(true);
      when(scheduleTaskService.checkTaskPermission("myTask", principal, ResourceAction.READ))
         .thenReturn(true);
      when(scheduleTaskService.checkTaskPermission("myTask", principal, ResourceAction.DELETE))
         .thenReturn(true);
      scheduleManagerStatic.when(() -> ScheduleManager.isInternalTask("myTask")).thenReturn(false);
      when(scheduleTaskService.getDialogModel("myTask", principal, true)).thenReturn(dialogModel);

      ScheduleTaskDialogModel result = controller.getDialogModel("myTask", principal);

      assertSame(dialogModel, result);
   }

   // -------------------------------------------------------------------------
   // saveTask()
   // -------------------------------------------------------------------------

   // [delegation] delegates to scheduleTaskService.saveTask() with em=true flag
   @Test
   void saveTask_delegatesToService() throws Exception {
      when(scheduleTaskService.saveTask(editorModel, "http://host", principal, true))
         .thenReturn(dialogModel);

      ScheduleTaskDialogModel result =
         controller.saveTask(editorModel, "http://host", principal);

      assertSame(dialogModel, result);
      verify(scheduleTaskService).saveTask(editorModel, "http://host", principal, true);
   }

   // -------------------------------------------------------------------------
   // toggleTaskEnabled()
   // -------------------------------------------------------------------------

   // [no permission] first task fails both permission checks → returns empty response; no toggle
   @Test
   void toggleTaskEnabled_noPermission_returnsEmptyResponseWithoutToggling() throws Exception {
      TaskListModel list = TaskListModel.builder().taskNames(List.of("myTask")).build();
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, "myTask", ResourceAction.WRITE))
         .thenReturn(false);
      when(scheduleTaskService.canDeleteTask(scheduleTask, principal)).thenReturn(false);

      ToggleTaskResponse result = controller.toggleTaskEnabled(list, principal);

      assertNotNull(result);
      verify(scheduleTaskService, never()).setTaskEnabled(anyString(), anyBoolean(), any(Principal.class));
   }

   // [with permission] SCHEDULE_TASK WRITE granted → toggles enabled state
   @Test
   void toggleTaskEnabled_withPermission_togglesTask() throws Exception {
      TaskListModel list = TaskListModel.builder().taskNames(List.of("myTask")).build();
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, "myTask", ResourceAction.WRITE))
         .thenReturn(true);
      when(scheduleTaskService.isTaskEnabled("myTask")).thenReturn(true);

      controller.toggleTaskEnabled(list, principal);

      // enabled was true → toggled to false
      verify(scheduleTaskService).setTaskEnabled("myTask", false, principal);
   }
}
