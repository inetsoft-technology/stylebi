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
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77350, the EM schedule task import must move only the tasks it actually imports into
 * the folder named in the xml, and must not trust the xml's removable flag for internal tasks.
 */
@Tag("core")
class ImportTaskControllerUnselectedMoveTest {
   private static final String FOLDER = "Evil";
   private static final String ORG = "orgx";
   private static final String BACKUP = InternalScheduledTaskService.ASSET_FILE_BACKUP;

   private ScheduleManager scheduleManager;
   private ScheduleTaskFolderService folderService;
   private AnalyticRepository repository;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private HttpSession session;
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      folderService = mock(ScheduleTaskFolderService.class);
      repository = mock(AnalyticRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      // security disabled, the owner and execute-as checks are unrestricted
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      controller = new ImportTaskController(scheduleManager, folderService, repository,
                                            securityEngine);

      session = mock(HttpSession.class);
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);
      principal = mock(Principal.class);

      when(folderService.checkFolderExists(FOLDER)).thenReturn(true);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserAlias(any())).thenReturn("alias");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
   }

   @Test
   void unselectedExistingTask_isNotMoved() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "t1", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(mock(ScheduleTask.class));

      importTasks(List.of(), false, task);

      verifyNotMoved();
   }

   @Test
   void selectedExistingTaskNotOverwriting_isNotMoved() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "t1", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(mock(ScheduleTask.class));

      importTasks(List.of(task.getTaskId()), false, task);

      verifyNotMoved();
   }

   @Test
   void unselectedInternalTask_isNotMoved() throws Exception {
      ScheduleTask task = internalTask(true);
      ScheduleTask stored = mock(ScheduleTask.class);
      when(stored.isRemovable()).thenReturn(false);
      when(scheduleManager.getScheduleTask(BACKUP)).thenReturn(stored);

      importTasks(List.of(), false, task);

      verifyNotMoved();
   }

   // no phantom task entry is added to the folder for a task that doesn't exist
   @Test
   void unselectedNonExistentTask_isNotMoved() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "ghost", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(null);

      importTasks(List.of(), false, task);

      verifyNotMoved();
   }

   @Test
   void unselectedTaskOfOtherSameOrgUser_isNotMoved() throws Exception {
      ScheduleTask task = normalTask("bob", "nightly", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(mock(ScheduleTask.class));

      importTasks(List.of(), true, task);

      verifyNotMoved();
   }

   // positive control, a selected new task still lands in the xml's folder
   @Test
   void selectedNewTask_isMovedToXmlFolder() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "t1", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(null);

      importTasks(List.of(task.getTaskId()), false, task);

      verify(scheduleManager).setScheduleTask(task.getTaskId(), task, principal);
      ScheduleTaskModel model = captureMove();
      assertEquals(task.getTaskId(), model.name());
      assertEquals("/", model.path());
      assertTrue(model.removable());
   }

   // an overwritten task is moved to the xml's folder too
   @Test
   void selectedOverwrittenTask_isMovedToXmlFolder() throws Exception {
      ScheduleTask task = normalTask("orgadmin", "t1", true);
      when(scheduleManager.getScheduleTask(task.getTaskId())).thenReturn(mock(ScheduleTask.class));

      importTasks(List.of(task.getTaskId()), true, task);

      verify(scheduleManager).setScheduleTask(task.getTaskId(), task, principal);
      assertTrue(captureMove().removable());
   }

   // an imported internal task is never folder-moved, even if the xml says it's removable
   @Test
   void selectedInternalTask_isImportedButNotRemovable() throws Exception {
      ScheduleTask task = internalTask(true);
      when(scheduleManager.getScheduleTask(BACKUP)).thenReturn(null);

      importTasks(List.of(BACKUP), false, task);

      verify(scheduleManager).setScheduleTask(BACKUP, task, principal);
      assertFalse(captureMove().removable());
   }

   // the reported attack, an org admin (refused SCHEDULE_TASK WRITE on internal tasks) imports
   // its own task with the host internal task left unselected in the same file, only its own
   // task is imported and moved
   @Test
   void orgAdminMixedImport_movesOnlyOwnTaskNotHostInternalTask() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK), eq(BACKUP),
                                      eq(ResourceAction.WRITE))).thenReturn(false);
      ScheduleTask own = normalTask("orgadmin", "t1", true);
      ScheduleTask internal = internalTask(true);
      ScheduleTask stored = mock(ScheduleTask.class);
      when(stored.isRemovable()).thenReturn(false);
      when(scheduleManager.getScheduleTask(own.getTaskId())).thenReturn(null);
      when(scheduleManager.getScheduleTask(BACKUP)).thenReturn(stored);

      importTasks(List.of(own.getTaskId()), false, internal, own);

      verify(scheduleManager).setScheduleTask(own.getTaskId(), own, principal);
      verify(scheduleManager, never()).setScheduleTask(eq(BACKUP), any(ScheduleTask.class),
                                                       any(Principal.class));
      assertEquals(own.getTaskId(), captureMove().name());
   }

   private void importTasks(List<String> selected, boolean overwriting, ScheduleTask... tasks)
      throws Exception
   {
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(tasks)));
      controller.importScheduleTask(selected, request, overwriting, "http://host", principal);
   }

   private void verifyNotMoved() throws Exception {
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   private ScheduleTaskModel captureMove() throws Exception {
      ArgumentCaptor<ScheduleTaskModel[]> models = ArgumentCaptor.forClass(ScheduleTaskModel[].class);
      ArgumentCaptor<AssetEntry> target = ArgumentCaptor.forClass(AssetEntry.class);
      verify(folderService).moveScheduleItems(models.capture(), any(), target.capture(), any());
      assertEquals(FOLDER, target.getValue().getPath());
      assertEquals(1, models.getValue().length);
      return models.getValue()[0];
   }

   private static ScheduleTask normalTask(String owner, String name, boolean removable) {
      IdentityID ownerId = new IdentityID(owner, ORG);
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(ownerId.convertToKey() + ":" + name);
      when(task.getName()).thenReturn(name);
      when(task.getType()).thenReturn(ScheduleTask.Type.NORMAL_TASK);
      when(task.getOwner()).thenReturn(ownerId);
      when(task.getPath()).thenReturn(FOLDER);
      when(task.isRemovable()).thenReturn(removable);
      when(task.getActionStream()).thenReturn(Stream.empty());
      return task;
   }

   private static ScheduleTask internalTask(boolean removable) {
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(BACKUP);
      when(task.getName()).thenReturn(BACKUP);
      when(task.getType()).thenReturn(ScheduleTask.Type.INTERNAL_TASK);
      when(task.getOwner()).thenReturn(
         new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()));
      when(task.getPath()).thenReturn(FOLDER);
      when(task.isRemovable()).thenReturn(removable);
      when(task.getActionStream()).thenReturn(Stream.empty());
      return task;
   }
}
