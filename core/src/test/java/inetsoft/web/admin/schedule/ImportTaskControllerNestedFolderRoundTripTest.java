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
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77796, the export to import round trip of tasks in nested folders: real tasks are
 * written with ScheduleTask.writeXML (what the EM export writes), uploaded through setTaskFile
 * and imported with the real ScheduleTaskFolderService over a storage keyed by the folder
 * identifier, so the path the import checks is the path attribute the export wrote.
 */
@Tag("core")
class ImportTaskControllerNestedFolderRoundTripTest {
   private static final String PARENT = "Parent";
   private static final String CHILD = "Parent/Child";
   private static final String DEEP = "Parent/Child/Deep";
   private static final String ORG = "orgx";

   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private ScheduleTaskFolderService folderService;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private final Map<String, Object> sessionAttrs = new HashMap<>();
   private Principal principal;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<OrganizationManager> orgStatic;

   @BeforeEach
   void setUp() throws Exception {
      // the folder identifiers and the parsed task owners are in the caller's organization
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      scheduleManager = mock(ScheduleManager.class);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      // each parent lists its children by their full path, the way addFolder writes them
      IndexedStorage storage = mock(IndexedStorage.class);
      storeFolder(storage, "/", PARENT);
      storeFolder(storage, PARENT, CHILD);
      storeFolder(storage, CHILD, DEEP);
      storeFolder(storage, DEEP);

      folderService = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, storage, null));
      doNothing().when(folderService).moveScheduleItems(any(), any(), any(), any());

      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      controller = new ImportTaskController(scheduleManager, folderService, repository,
                                            securityEngine);

      HttpSession session = mock(HttpSession.class);
      doAnswer(inv -> sessionAttrs.put(inv.getArgument(0), inv.getArgument(1)))
         .when(session).setAttribute(anyString(), any());
      doAnswer(inv -> sessionAttrs.remove(inv.getArgument(0)))
         .when(session).removeAttribute(anyString());
      when(session.getAttribute(anyString()))
         .thenAnswer(inv -> sessionAttrs.get(inv.getArgument(0)));
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);
      principal = mock(Principal.class);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserAlias(any())).thenReturn("alias");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      orgStatic.close();
   }

   // exported tasks in 2- and 3-level folders are moved back into those folders, a top-level
   // folder task too, and a root task is saved without a folder check or a move
   @Test
   void exportedNestedTasks_areImportedIntoTheirFolders() throws Exception {
      allowWrite(PARENT);
      allowWrite(CHILD);
      allowWrite(DEEP);
      List<TaskDependencyModel> rows = upload(exported("top", PARENT), exported("nested", CHILD),
                                              exported("deep", DEEP), exported("root", "/"));

      ImportTaskResponse response = importRows(rows);

      assertEquals(List.of(), response.failedTasks());
      verify(scheduleManager, times(4)).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                        eq(principal));
      verifyMovedTo(PARENT);
      verifyMovedTo(CHILD);
      verifyMovedTo(DEEP);
      verify(folderService, times(3)).moveScheduleItems(any(), any(), any(), any());
      verify(securityEngine, never()).checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                                      eq("/"), any());
   }

   // an exported nested task the caller can't write the folder of fails visibly and isn't
   // saved, the other tasks are imported
   @Test
   void exportedNestedTask_unwritableFolder_fails() throws Exception {
      allowWrite(PARENT);
      List<TaskDependencyModel> rows = upload(exported("top", PARENT), exported("nested", CHILD),
                                              exported("root", "/"));
      String nestedId = new IdentityID("admin", ORG).convertToKey() + ":nested";

      ImportTaskResponse response = importRows(rows);

      assertEquals(List.of(nestedId), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(eq(nestedId), any(), any());
      verify(scheduleManager, times(2)).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                        eq(principal));
      verifyMovedTo(PARENT);
      verify(folderService, times(1)).moveScheduleItems(any(), any(), any(), any());
   }

   private void verifyMovedTo(String path) throws Exception {
      verify(folderService).moveScheduleItems(
         any(), eq(new String[0]), argThat(e -> path.equals(e.getPath())), eq(principal));
   }

   private void allowWrite(String path) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          eq(path), eq(ResourceAction.WRITE)))
         .thenReturn(true);
   }

   // a task stored in a folder, its path is the folder entry path (changeTaskFolder)
   private static String exported(String name, String path) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID("admin", ORG));
      task.setPath(path);
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      task.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private List<TaskDependencyModel> upload(String... tasks) throws Exception {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><schedule>" +
         String.join("", tasks) + "</schedule>";
      FileData file = FileData.builder()
         .name("tasks.xml")
         .content(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))
         .build();
      return controller.setTaskFile(file, request, principal).tasks();
   }

   // the selection the EM import dialog builds from the setTaskFile rows
   private ImportTaskResponse importRows(List<TaskDependencyModel> rows) throws Exception {
      List<String> selected = rows.stream()
         .map(row -> row.taskId() != null ? row.taskId() : row.task())
         .toList();
      return controller.importScheduleTask(selected, request, false, "http://host", principal);
   }

   private static void storeFolder(IndexedStorage storage, String path, String... children)
      throws Exception
   {
      AssetFolder folder = new AssetFolder();

      for(String child : children) {
         folder.addEntry(folderEntry(child));
      }

      when(storage.getXMLSerializable(eq(folderEntry(path).toIdentifier()), any()))
         .thenReturn(folder);
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }
}
