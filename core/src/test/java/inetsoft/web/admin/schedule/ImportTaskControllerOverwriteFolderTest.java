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
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.*;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78740, the EM import that overwrites a stored task files the task in the folder of the
 * xml, and only there. Runs the real controller, schedule manager, folder service and storage; a
 * folder is listed by its stored entries, the way the folder's task list is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ImportTaskControllerOverwriteFolderTest {
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID OWNER = new IdentityID("admin", ORG);
   private static final String[] FOLDERS = { "I78740A", "I78740B" };

   @Autowired
   ScheduleManager scheduleManager;

   @BeforeEach
   void setUp() throws Exception {
      oldPrincipal = ThreadContext.getPrincipal();
      oldContextPrincipal = ThreadContext.getContextPrincipal();
      admin = new SRPrincipal(OWNER, new IdentityID[0], new String[0], ORG,
                              Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
      ThreadContext.setPrincipal(admin);
      ThreadContext.setContextPrincipal(admin);

      for(String folder : FOLDERS) {
         addFolder(folder);
      }

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      ScheduleTaskFolderService folderService = new ScheduleTaskFolderService(
         scheduleManager, securityEngine, null, IndexedStorage.getIndexedStorage(),
         mock(RenameTransformHandler.class));
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);
      controller = new ImportTaskController(scheduleManager, folderService, repository,
                                            securityEngine);
      session = mock(HttpSession.class);
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);

      // the storage of the dependencies is not part of this context
      dependencies = mockStatic(DependencyTransformer.class);
      dependencies.when(() -> DependencyTransformer.getDependencies(anyString()))
         .thenReturn(new ArrayList<>());
   }

   @AfterEach
   void tearDown() throws Exception {
      dependencies.close();

      try {
         for(String taskId : taskIds) {
            if(scheduleManager.getScheduleTask(taskId) != null) {
               scheduleManager.removeScheduleTask(taskId, admin);
            }
         }

         IndexedStorage storage = IndexedStorage.getIndexedStorage();
         AssetFolder root = getFolder("/");

         for(String path : FOLDERS) {
            storage.remove(folderEntry(path).toIdentifier());

            if(root != null) {
               root.removeEntry(folderEntry(path));
            }
         }

         if(root != null) {
            storage.putXMLSerializable(folderEntry("/").toIdentifier(), root);
         }
      }
      finally {
         taskIds.clear();
         ThreadContext.setPrincipal(oldPrincipal);
         ThreadContext.setContextPrincipal(oldContextPrincipal);
      }
   }

   // the xml names the root folder
   @Test
   void xmlRoot_taskEndsInRootOnly() throws Exception {
      String taskId = storeTask("i78740root", "I78740A");

      importOverwrite("i78740root", "/");

      assertFiledIn(taskId, "/");
   }

   // the xml names another existing folder
   @Test
   void xmlOtherFolder_taskEndsInThatFolderOnly() throws Exception {
      String taskId = storeTask("i78740other", "I78740A");

      importOverwrite("i78740other", "I78740B");

      assertFiledIn(taskId, "I78740B");
   }

   // the xml names a folder that does not exist on this server
   @Test
   void xmlMissingFolder_taskEndsInRootOnly() throws Exception {
      String taskId = storeTask("i78740missing", "I78740A");

      importOverwrite("i78740missing", "NoSuchFolder78740");

      assertFiledIn(taskId, "/");
   }

   // the xml names the folder the task is already in
   @Test
   void xmlSameFolder_taskStaysInItsFolderOnly() throws Exception {
      String taskId = storeTask("i78740same", "I78740A");

      importOverwrite("i78740same", "I78740A");

      assertFiledIn(taskId, "I78740A");
   }

   // an overwritten root task that the xml names a folder for moves there
   @Test
   void rootTask_xmlFolder_movesThere() throws Exception {
      String taskId = storeTask("i78740fromroot", null);

      importOverwrite("i78740fromroot", "I78740B");

      assertFiledIn(taskId, "I78740B");
   }

   // a re-save through ScheduleManager that names no folder keeps the task in its folder
   @Test
   void threeArgResaveOfAFolderedTask_keepsItsFolder() throws Exception {
      String taskId = storeTask("i78740resave", "I78740A");
      ScheduleTask stored = scheduleManager.getScheduleTask(taskId);

      scheduleManager.setScheduleTask(taskId, stored, admin);

      assertFiledIn(taskId, "I78740A");
   }

   private String storeTask(String name, String folder) throws Exception {
      ScheduleTask task = task(name);
      String taskId = task.getTaskId();
      taskIds.add(taskId);
      scheduleManager.setScheduleTask(taskId, task, folder == null ? null : folderEntry(folder),
                                      admin);
      assertFiledIn(taskId, folder == null ? "/" : folder);
      return taskId;
   }

   private void importOverwrite(String name, String xmlPath) throws Exception {
      ScheduleTask incoming = task(name);
      incoming.setPath(xmlPath);
      when(session.getAttribute(INFO_ATTR)).thenReturn(new ArrayList<>(List.of(incoming)));
      ImportTaskResponse response = controller.importScheduleTask(
         List.of(incoming.getTaskId()), request, true, "http://host", admin);
      assertTrue(response.failedTasks().isEmpty(), "failed " + response.failedTasks());
   }

   /** Asserts that the task is in the folder and listed in it only. */
   private void assertFiledIn(String taskId, String folder) throws Exception {
      assertEquals(folder, scheduleManager.getScheduleTask(taskId).getPath(), "path");

      for(String path : new String[] { "/", FOLDERS[0], FOLDERS[1] }) {
         assertEquals(path.equals(folder), listedIn(path, taskId),
                      "listed in " + path + ", expected only in " + folder);
      }
   }

   private boolean listedIn(String path, String taskId) throws Exception {
      AssetFolder folder = getFolder(path);

      return folder != null &&
         scheduleManager.getScheduleTasks(folder.getEntries(), false, false, ORG).stream()
            .anyMatch(task -> taskId.equals(task.getTaskId()));
   }

   private void addFolder(String path) throws Exception {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      AssetEntry entry = folderEntry(path);
      storage.putXMLSerializable(entry.toIdentifier(), new AssetFolder());
      AssetFolder root = getFolder("/");

      if(root == null) {
         root = new AssetFolder();
      }

      root.addEntry(entry);
      storage.putXMLSerializable(folderEntry("/").toIdentifier(), root);
   }

   private static AssetFolder getFolder(String path) throws Exception {
      return (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(folderEntry(path).toIdentifier(), null, ORG);
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static ScheduleTask task(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(OWNER);
      task.setEnabled(true);
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      task.addCondition(condition);
      return task;
   }

   private final List<String> taskIds = new ArrayList<>();
   private SRPrincipal admin;
   private Principal oldPrincipal;
   private Principal oldContextPrincipal;
   private ImportTaskController controller;
   private HttpSession session;
   private HttpServletRequest request;
   private MockedStatic<DependencyTransformer> dependencies;
}
