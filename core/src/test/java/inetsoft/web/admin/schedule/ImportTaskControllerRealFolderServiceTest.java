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
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
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
 * Bug #77503, the EM task import with the real ScheduleTaskFolderService (only its storage and
 * the security engine are mocked), so the folder write check before the save is the same
 * SCHEDULE_TASK_FOLDER WRITE check that moveScheduleItems refuses the move with, instead of a
 * stubbed refusal.
 */
@Tag("core")
class ImportTaskControllerRealFolderServiceTest {
   private static final String LOCKED = "Locked";
   private static final String ORG = "orgx";

   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private ScheduleTaskFolderService folderService;
   private ImportTaskController controller;
   private HttpSession session;
   private HttpServletRequest request;
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      // the root folder holds the top-level folder Locked, which the caller can't write
      AssetFolder root = new AssetFolder();
      root.addEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                   AssetEntry.Type.SCHEDULE_TASK_FOLDER, LOCKED, null));
      IndexedStorage storage = mock(IndexedStorage.class);
      when(storage.getXMLSerializable(anyString(), any())).thenReturn(root);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          eq(LOCKED), eq(ResourceAction.WRITE)))
         .thenReturn(false);

      folderService = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, storage, null));

      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      controller = new ImportTaskController(scheduleManager, folderService, repository,
                                            securityEngine);

      session = mock(HttpSession.class);
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);
      principal = mock(Principal.class);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserAlias(any())).thenReturn("alias");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
   }

   // the refused task fails and isn't saved, the next task (and a data cycle task, which is
   // never moved, in the same unwritable folder) is still imported
   @Test
   void unwritableFolder_refusedTaskFailsAndRestIsImported() throws Exception {
      assertTrue(folderService.checkFolderExists(LOCKED));

      ScheduleTask refused = task("t1", LOCKED, ScheduleTask.Type.NORMAL_TASK);
      ScheduleTask cycle = task("t2", LOCKED, ScheduleTask.Type.CYCLE_TASK);
      ScheduleTask atRoot = task("t3", "/", ScheduleTask.Type.NORMAL_TASK);
      when(session.getAttribute(INFO_ATTR))
         .thenReturn(new ArrayList<>(List.of(refused, cycle, atRoot)));
      List<String> selected =
         List.of(refused.getTaskId(), cycle.getTaskId(), atRoot.getTaskId());

      ImportTaskResponse response = assertDoesNotThrow(
         () -> controller.importScheduleTask(selected, request, false, "http://host", principal));

      assertEquals(List.of(refused.getTaskId()), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(eq(refused.getTaskId()), any(), any());
      verify(scheduleManager).setScheduleTask(cycle.getTaskId(), cycle, principal);
      verify(scheduleManager).setScheduleTask(atRoot.getTaskId(), atRoot, principal);
      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   private static ScheduleTask task(String name, String path, ScheduleTask.Type type) {
      IdentityID owner = new IdentityID("orgadmin", ORG);
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(owner.convertToKey() + ":" + name);
      when(task.getName()).thenReturn(name);
      when(task.getType()).thenReturn(type);
      when(task.getOwner()).thenReturn(owner);
      when(task.getPath()).thenReturn(path);
      when(task.isRemovable()).thenReturn(true);
      when(task.getActionStream()).thenReturn(Stream.empty());
      return task;
   }
}
