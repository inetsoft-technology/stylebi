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
 * ImportTaskController.importScheduleTask() has real in-controller logic:
 *   - For each task in the session-stored list, checks existence and overwrite permission
 *   - If the task exists, overwriting=true, and it's editable but the principal lacks permission,
 *     the taskId is added to failedList (not imported)
 *   - If the task is new (or overwriting=true with permission), it is imported via
 *     scheduleManager.setScheduleTask()
 *   - If the task already exists and overwriting=false, it is silently skipped
 *
 * setTaskFile() parses XML from a Base64-encoded byte array; that is covered by E2E tests.
 * moveTask() calls SUtil.getUserAlias() (static); to avoid triggering it, all tests set
 * task.getPath() to null so the folder-move branch is never reached.
 *
 * Coverage scope:
 *   [permission denied]     task exists + overwriting + editable + no permission → failedList; no import
 *   [new task imported]     task does not exist + selected → setScheduleTask called
 *   [not overwriting]       task exists + overwriting=false → setScheduleTask never called
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.schedule.*;
import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ImportTaskControllerTest {

   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleTaskFolderService scheduleTaskFolderService;
   @Mock private AnalyticRepository analyticRepository;
   @Mock private ScheduleTask incomingTask;
   @Mock private ScheduleTask existingTask;
   @Mock private HttpServletRequest request;
   @Mock private HttpSession session;
   @Mock private Principal principal;

   private ImportTaskController controller;

   @BeforeEach
   void setUp() {
      controller = new ImportTaskController(scheduleManager, scheduleTaskFolderService, analyticRepository);
      when(request.getSession(true)).thenReturn(session);
      lenient().when(incomingTask.getTaskId()).thenReturn("myTask");
      lenient().when(incomingTask.getPath()).thenReturn(null); // skip moveTask()
   }

   // -------------------------------------------------------------------------
   // importScheduleTask()
   // -------------------------------------------------------------------------

   // [permission denied] task exists + overwriting + editable + no permission → failedList; no import
   @Test
   void importScheduleTask_permissionDenied_addedToFailedList() throws Exception {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(incomingTask)));
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(existingTask);
      when(existingTask.isEditable()).thenReturn(true);
      when(analyticRepository.checkPermission(
         principal, ResourceType.SCHEDULER, "myTask", ResourceAction.ACCESS))
         .thenReturn(false);

      ImportTaskResponse result = controller.importScheduleTask(
         List.of("myTask"), request, true, "http://host", principal);

      assertTrue(result.failedTasks().contains("myTask"));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   // [new task imported] task does not exist + selected → setScheduleTask called
   @Test
   void importScheduleTask_newTask_isImported() throws Exception {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(incomingTask)));
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(null); // task does not exist
      when(incomingTask.getActionStream()).thenReturn(Stream.empty()); // updateTaskInfo needs a stream

      ImportTaskResponse result = controller.importScheduleTask(
         List.of("myTask"), request, false, "http://host", principal);

      assertTrue(result.failedTasks().isEmpty());
      verify(scheduleManager).setScheduleTask("myTask", incomingTask, principal);
   }

   // [not overwriting] task exists + overwriting=false → setScheduleTask never called
   @Test
   void importScheduleTask_existingTaskNotOverwriting_skipped() throws Exception {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(incomingTask)));
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(existingTask);

      ImportTaskResponse result = controller.importScheduleTask(
         List.of("myTask"), request, false, "http://host", principal);

      assertTrue(result.failedTasks().isEmpty());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }
}
