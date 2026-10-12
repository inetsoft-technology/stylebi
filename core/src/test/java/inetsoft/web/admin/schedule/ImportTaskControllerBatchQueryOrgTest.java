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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77549, ScheduleManager.setScheduleTask refuses a task whose batch action query is in
 * another organization. ImportTaskController.importScheduleTask saves the tasks one by one with
 * no handler of its own (Bug #77503), so the refusal is checked first: the refused task is
 * reported as failed and not saved, and the other tasks are still imported. Since Bug #77972
 * every refusal of the save is checked first (ScheduleManager.checkScheduleTaskSave), e.g. a
 * batch action target task the caller may not see, or the scheduler permission (IOException).
 *
 * <p>The refusal is mocked: an uploaded task is parsed with parseImportedTask, which moves the
 * query to the caller's organization, so a foreign-org query isn't expected on this path. The
 * test pins the loop-continues contract for the check, not that a foreign-org upload is refused.
 */
@Tag("core")
class ImportTaskControllerBatchQueryOrgTest {
   private static final String ORG = "orgx";

   private ScheduleManager scheduleManager;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private HttpSession session;
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      ScheduleTaskFolderService folderService = mock(ScheduleTaskFolderService.class);
      AnalyticRepository repository = mock(AnalyticRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      controller = new ImportTaskController(scheduleManager, folderService, repository,
                                            securityEngine);

      session = mock(HttpSession.class);
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);
      principal = mock(Principal.class);

      when(folderService.checkFolderExists(anyString())).thenReturn(true);
      when(folderService.checkFolderPermission(anyString(), any(), eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(null);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserAlias(any())).thenReturn("alias");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
   }

   @Test
   void refusedQuery_taskIsReportedAndNotSaved_otherTasksAreImported() throws Exception {
      ScheduleTask refused = task("t1");
      ScheduleTask other = task("t2");
      String refusedId = refused.getTaskId();
      doThrow(new inetsoft.sree.security.SecurityException("Unauthorized access to query"))
         .when(scheduleManager)
         .checkScheduleTaskSave(eq(refusedId), same(refused), eq(principal));

      ImportTaskResponse response = assertDoesNotThrow(
         () -> importTasks(refused, other), "the refusal escaped importScheduleTask");

      verify(scheduleManager, never()).setScheduleTask(eq(refused.getTaskId()), any(), any());
      verify(scheduleManager).setScheduleTask(other.getTaskId(), other, principal);
      assertTrue(response.failedTasks().contains(refused.getTaskId()),
                 "the refused task must be reported as failed: " + response.failedTasks());
      assertFalse(response.failedTasks().contains(other.getTaskId()), response.failedTasks().toString());
   }

   // Bug #77972, the other refusals of the save fail only their own task too
   @Test
   void refusedSave_ioException_taskIsReportedAndNotSaved_otherTasksAreImported()
      throws Exception
   {
      ScheduleTask refused = task("t1");
      ScheduleTask other = task("t2");
      String refusedId = refused.getTaskId();
      doThrow(new java.io.IOException("User 'orgadmin' doesn't have schedule permission."))
         .when(scheduleManager)
         .checkScheduleTaskSave(eq(refusedId), same(refused), eq(principal));

      ImportTaskResponse response = assertDoesNotThrow(
         () -> importTasks(refused, other), "the refusal escaped importScheduleTask");

      verify(scheduleManager, never()).setScheduleTask(eq(refused.getTaskId()), any(), any());
      verify(scheduleManager).setScheduleTask(other.getTaskId(), other, principal);
      assertEquals(List.of(refusedId), response.failedTasks());
   }

   private ImportTaskResponse importTasks(ScheduleTask... tasks) throws Exception {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(tasks)));
      List<String> selected = Arrays.stream(tasks).map(ScheduleTask::getTaskId).toList();
      return controller.importScheduleTask(selected, request, false, "http://host", principal);
   }

   private static ScheduleTask task(String name) {
      IdentityID ownerId = new IdentityID("orgadmin", ORG);
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(ownerId.convertToKey() + ":" + name);
      when(task.getName()).thenReturn(name);
      when(task.getType()).thenReturn(ScheduleTask.Type.NORMAL_TASK);
      when(task.getOwner()).thenReturn(ownerId);
      when(task.getPath()).thenReturn("Folder1");
      when(task.isRemovable()).thenReturn(true);
      when(task.getActionStream()).thenReturn(Stream.empty());
      return task;
   }
}
