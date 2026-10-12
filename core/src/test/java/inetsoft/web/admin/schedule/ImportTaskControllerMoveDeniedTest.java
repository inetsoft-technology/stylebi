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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77503, ImportTaskController.importScheduleTask moves each imported task to its xml folder
 * with ScheduleTaskFolderService.moveScheduleItems, which throws a MessageException when the
 * caller lacks WRITE on the target folder. The refusal must not abort the import: the refused
 * task is reported as failed and not saved, and the other tasks are still imported.
 */
@Tag("core")
class ImportTaskControllerMoveDeniedTest {
   private static final String LOCKED = "Locked";
   private static final String OPEN = "Open";
   private static final String ORG = "orgx";

   private ScheduleManager scheduleManager;
   private ScheduleTaskFolderService folderService;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private HttpSession session;
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      folderService = mock(ScheduleTaskFolderService.class);
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
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(null);

      // the caller has WRITE on Open but not on Locked
      when(folderService.checkFolderPermission(eq(OPEN), any(), eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(folderService.checkFolderPermission(eq(LOCKED), any(), eq(ResourceAction.WRITE)))
         .thenReturn(false);

      // same refusal as ScheduleTaskFolderService.moveScheduleItems when the caller has no
      // WRITE on the target folder
      ArgumentMatcher<AssetEntry> locked = e -> e != null && LOCKED.equals(e.getPath());
      doThrow(new MessageException("You do not have write permission on " + LOCKED))
         .when(folderService).moveScheduleItems(any(), any(), argThat(locked), any());

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserAlias(any())).thenReturn("alias");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
   }

   @Test
   void moveRefusedForFirstTask_secondTaskStillImportedAndFirstReported() throws Exception {
      ScheduleTask first = normalTask("orgadmin", "t1", LOCKED, true);
      ScheduleTask second = normalTask("orgadmin", "t2", OPEN, true);

      ImportTaskResponse response = assertDoesNotThrow(
         () -> importTasks(first, second),
         "the folder-move refusal for the first task escaped importScheduleTask");

      verify(scheduleManager).setScheduleTask(second.getTaskId(), second, principal);
      verify(folderService).moveScheduleItems(
         any(), any(), argThat(e -> OPEN.equals(e.getPath())), eq(principal));
      assertTrue(response.failedTasks().contains(first.getTaskId()),
                 "the task whose folder move was refused must be reported as failed: " +
                 response.failedTasks());
      assertFalse(response.failedTasks().contains(second.getTaskId()),
                  "the second task was imported: " + response.failedTasks());
   }

   // the refused task is not saved at the root folder either
   @Test
   void moveRefused_refusedTaskIsNotSaved() throws Exception {
      ScheduleTask first = normalTask("orgadmin", "t1", LOCKED, true);
      ScheduleTask second = normalTask("orgadmin", "t2", OPEN, true);

      importTasks(first, second);

      verify(scheduleManager, never()).setScheduleTask(eq(first.getTaskId()), any(), any());
      verify(folderService, never()).moveScheduleItems(
         any(), any(), argThat(e -> e != null && LOCKED.equals(e.getPath())), any());
   }

   // a task that is never moved (not removable) isn't refused for a folder it doesn't enter,
   // it's imported at the root folder as before
   @Test
   void nonRemovableTaskWithUnwritableFolder_isImportedWithoutMove() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "t1", LOCKED, false);

      ImportTaskResponse response = importTasks(task);

      verify(scheduleManager).setScheduleTask(task.getTaskId(), task, principal);
      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
      assertFalse(response.failedTasks().contains(task.getTaskId()),
                  "the non-removable task must not be reported as failed: " +
                  response.failedTasks());
   }

   private ImportTaskResponse importTasks(ScheduleTask... tasks) throws Exception {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(tasks)));
      List<String> selected = Arrays.stream(tasks).map(ScheduleTask::getTaskId).toList();
      return controller.importScheduleTask(selected, request, false, "http://host", principal);
   }

   private static ScheduleTask normalTask(String owner, String name, String path,
                                          boolean removable)
   {
      IdentityID ownerId = new IdentityID(owner, ORG);
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(ownerId.convertToKey() + ":" + name);
      when(task.getName()).thenReturn(name);
      when(task.getType()).thenReturn(ScheduleTask.Type.NORMAL_TASK);
      when(task.getOwner()).thenReturn(ownerId);
      when(task.getPath()).thenReturn(path);
      when(task.isRemovable()).thenReturn(removable);
      when(task.getActionStream()).thenReturn(Stream.empty());
      return task;
   }
}
