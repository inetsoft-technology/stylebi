/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.portal.controller.ScheduleController;
import inetsoft.web.portal.controller.ScheduleTaskFolderController;
import inetsoft.web.portal.data.TaskFolderBrowserModel;
import inetsoft.web.composer.model.TreeNodeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77906, the schedule folder endpoints that read a caller-supplied folder path check the
 * folder permission of the operation they come before, before any storage read:
 * folder/editModel (portal and EM) needs DELETE and WRITE, as rename does, and is refused
 * without it; folder/check-dependency (portal and EM) skips paths without the DELETE that
 * folder/remove needs; the portal task-folder-browser needs READ and is refused without it.
 * Without the permission an existing folder and a missing one get the same answer.
 *
 * The storage is a real BlobIndexedStorage, spied on to show that a denied request reads nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskFolderPathPermissionTest {
   private static final String ORG = "org77906";
   private static final IdentityID BOSS = new IdentityID("boss77906", ORG);
   private static final String PAYROLL = BOSS.convertToKey() + ":PayrollRun";
   private static final String OPEN_JOB = BOSS.convertToKey() + ":OpenJob";

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleTaskFolderController portalFolderController;
   private ScheduleController portalScheduleController;
   private EMScheduleTaskFolderController emController;
   private ScheduleTaskFolderService folderService;
   private SRPrincipal user;
   private Principal savedPrincipal;
   // folder path -> the SCHEDULE_TASK_FOLDER actions the user has on it; none if not listed
   private Map<String, Set<ResourceAction>> granted;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      storage = spy(new BlobIndexedStorage(blobStorageManager));
      user = new SRPrincipal(new IdentityID("user77906", ORG), new IdentityID[0],
                             new String[0], ORG, 0L);
      ThreadContext.setContextPrincipal(user);
      granted = new HashMap<>();
      granted.put("/", EnumSet.of(ResourceAction.READ));
      granted.put("Open", EnumSet.allOf(ResourceAction.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> granted.getOrDefault(inv.getArgument(2, String.class), Set.of())
            .contains(inv.getArgument(3, ResourceAction.class)));

      // the caller's visible tasks depend on PayrollRun and OpenJob
      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks(anyString())).thenReturn(new Vector<>());
      when(scheduleManager.getScheduleTasks(any(Principal.class), any(), anyString()))
         .thenReturn(new Vector<>());
      when(scheduleManager.hasDependency(any(), anyString()))
         .thenAnswer(inv -> Set.of(PAYROLL, OPEN_JOB).contains(inv.getArgument(1, String.class)));

      folderService = new ScheduleTaskFolderService(
         scheduleManager, securityEngine, mock(SecurityProvider.class), storage,
         mock(RenameTransformHandler.class));
      ScheduleService scheduleService = new ScheduleService(
         null, scheduleManager, null, null, null, null, null, null, securityEngine,
         folderService, storage, null, mock(RenameTransformHandler.class));
      portalFolderController = new ScheduleTaskFolderController(folderService, scheduleService);
      portalScheduleController =
         new ScheduleController(null, scheduleManager, scheduleService, null);
      emController = new EMScheduleTaskFolderController(
         folderService, scheduleService, mock(ScheduleTaskService.class), securityEngine,
         scheduleManager);

      // / -> Open (task OpenJob), Secret (owner boss, task PayrollRun, subfolders Sub, Hidden)
      AssetFolder root = new AssetFolder();
      root.addEntry(folder("Open"));
      root.addEntry(folder("Secret"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder open = new AssetFolder();
      open.addEntry(task(OPEN_JOB));
      storage.putXMLSerializable(folder("Open").toIdentifier(), open);
      AssetFolder secret = new AssetFolder();
      secret.setOwner(BOSS);
      secret.addEntry(task(PAYROLL));
      secret.addEntry(folder("Secret/Sub"));
      secret.addEntry(folder("Secret/Hidden"));
      storage.putXMLSerializable(folder("Secret").toIdentifier(), secret);
      storage.putXMLSerializable(folder("Secret/Sub").toIdentifier(), new AssetFolder());
      storage.putXMLSerializable(folder("Secret/Hidden").toIdentifier(), new AssetFolder());
      clearInvocations(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // editModel without DELETE and WRITE is refused the same way for existing and missing
   // folders, through both controllers, with no storage read
   @Test
   void editModel_withoutDeleteAndWrite_isRefusedForExistingAndMissing() {
      for(Set<ResourceAction> actions : List.of(
         EnumSet.noneOf(ResourceAction.class), EnumSet.of(ResourceAction.READ, ResourceAction.WRITE),
         EnumSet.of(ResourceAction.READ, ResourceAction.DELETE)))
      {
         granted.put("Secret", actions);
         granted.put("NoSuch", actions);

         for(String path : List.of("Secret", "NoSuch")) {
            assertThrows(SecurityException.class,
                         () -> portalFolderController.getFolderEditModel(path, user),
                         "portal " + path + " " + actions);
            assertThrows(SecurityException.class,
                         () -> emController.getFolderEditModel(path, user),
                         "EM " + path + " " + actions);
         }
      }

      assertStorageNotRead();
   }

   // positive control: with DELETE and WRITE the model is returned, and a missing folder is
   // still not found, as rename answers
   @Test
   void editModel_withDeleteAndWrite_returnsModel() throws Exception {
      granted.put("Secret", EnumSet.of(ResourceAction.DELETE, ResourceAction.WRITE));
      granted.put("NoSuch", EnumSet.of(ResourceAction.DELETE, ResourceAction.WRITE));

      EditTaskFolderDialogModel portal = portalFolderController.getFolderEditModel("Secret", user);
      EditTaskFolderDialogModel em = emController.getFolderEditModel("Secret", user);

      for(EditTaskFolderDialogModel model : List.of(portal, em)) {
         assertEquals("Secret", model.folderName());
         assertEquals("Secret", model.oldPath());
         assertEquals(BOSS, model.owner());
      }

      assertThrows(FileNotFoundException.class,
                   () -> portalFolderController.getFolderEditModel("NoSuch", user));
   }

   // check-dependency skips paths without DELETE before reading them, so existing and missing
   // denied folders both answer [] through both controllers
   @Test
   void checkDependency_withoutDelete_answersEmptyForExistingAndMissing() throws Exception {
      granted.put("Secret", EnumSet.of(ResourceAction.READ, ResourceAction.WRITE));

      for(String path : List.of("Secret", "NoSuch")) {
         assertEquals(List.of(), portalScheduleController
            .checkScheduleFolderDependency(paths(path), user).taskNames(), "portal " + path);
         assertEquals(List.of(), emController
            .checkScheduledTaskDependency(paths(path), user).taskNames(), "EM " + path);
      }

      assertStorageNotRead();
   }

   // a mixed selection still gets the permitted folder's dependents, and the denied one isn't read
   @Test
   void checkDependency_mixedSelection_answersPermittedFolderOnly() throws Exception {
      String expected = SUtil.getTaskNameWithoutOrg(OPEN_JOB);

      assertEquals(List.of(expected), portalScheduleController
         .checkScheduleFolderDependency(paths("Open", "Secret"), user).taskNames());
      assertEquals(List.of(expected), emController
         .checkScheduledTaskDependency(paths("Secret", "Open"), user).taskNames());
      verify(storage, never()).getXMLSerializable(eq(folder("Secret").toIdentifier()), any());
   }

   // control: with DELETE the folder's dependents are returned, as before
   @Test
   void checkDependency_withDelete_answersDependents() throws Exception {
      granted.put("Secret", EnumSet.of(ResourceAction.DELETE));

      assertEquals(List.of(SUtil.getTaskNameWithoutOrg(PAYROLL)), portalScheduleController
         .checkScheduleFolderDependency(paths("Secret"), user).taskNames());
   }

   // with DELETE on a missing folder, the answer is [] rather than an error
   @Test
   void checkDependency_withDeleteOnMissing_answersEmpty() throws Exception {
      granted.put("NoSuch", EnumSet.of(ResourceAction.DELETE));

      assertEquals(List.of(), portalScheduleController
         .checkScheduleFolderDependency(paths("NoSuch"), user).taskNames());
      assertEquals(List.of(), emController
         .checkScheduledTaskDependency(paths("NoSuch"), user).taskNames());
   }

   // task-folder-browser without READ is refused the same way for existing and missing folders
   @Test
   void browser_withoutRead_isRefusedForExistingAndMissing() {
      granted.put("Secret", EnumSet.of(ResourceAction.WRITE, ResourceAction.DELETE));

      for(String path : List.of("Secret", "NoSuch", "Secret/Sub")) {
         assertThrows(SecurityException.class,
                      () -> portalFolderController.getDatasourcesBrowser(path, false, user), path);
      }

      assertStorageNotRead();
   }

   // with READ the browser lists the READ-permitted subfolders and the breadcrumbs, as before
   @Test
   void browser_withRead_listsReadableSubfolders() throws Exception {
      granted.put("Secret", EnumSet.of(ResourceAction.READ));
      granted.put("Secret/Sub", EnumSet.of(ResourceAction.READ));

      TaskFolderBrowserModel model = portalFolderController.getDatasourcesBrowser("Secret", false, user);

      assertEquals(List.of("Secret/Sub"), folderPaths(model.folderList()));
      assertEquals(List.of("/", "Secret"), folderPaths(model.paths()));
      assertFalse(model.root());
   }

   // the root is always browsable, as in the schedule tree, and lists only readable folders
   @Test
   void browser_root_listsReadableFoldersWithoutRootRead() throws Exception {
      granted.remove("/");

      TaskFolderBrowserModel model = portalFolderController.getDatasourcesBrowser("/", false, user);

      assertEquals(List.of("Open"), folderPaths(model.folderList()));
      assertTrue(model.root());
      assertEquals(List.of("/"), folderPaths(
         portalFolderController.getDatasourcesBrowser(null, true, user).folderList()));
   }

   private void assertStorageNotRead() {
      assertTrue(mockingDetails(storage).getInvocations().isEmpty(),
                 () -> "storage was read: " + mockingDetails(storage).getInvocations());
   }

   private static List<String> folderPaths(List<TreeNodeModel> nodes) {
      return nodes.stream()
         .map(n -> ((AssetEntry) n.data()).getPath())
         .collect(Collectors.toList());
   }

   private static TaskListModel paths(String... paths) {
      return TaskListModel.builder().addTaskNames(paths).build();
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static AssetEntry task(String taskName) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + taskName, null, ORG);
   }
}
