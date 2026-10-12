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
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.ScheduleController;
import inetsoft.web.portal.controller.ScheduleTaskFolderController;
import inetsoft.web.portal.data.TaskFolderBrowserModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77906 tester scenarios on top of ScheduleTaskFolderPathPermissionTest: an admin (every
 * check passes) still gets the full answers, DELETE inherited from a parent on a missing child
 * answers [] without an exception, the root path, and a denied folder gives the same answer
 * before and after it is removed from storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskFolderPathPermissionScenarioTest {
   private static final String ORG = "org77906v";
   private static final IdentityID BOSS = new IdentityID("boss77906v", ORG);
   private static final String PAYROLL = BOSS.convertToKey() + ":PayrollRun";
   private static final String OPEN_JOB = BOSS.convertToKey() + ":OpenJob";

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleTaskFolderController portalFolderController;
   private ScheduleController portalScheduleController;
   private EMScheduleTaskFolderController emController;
   private SRPrincipal user;
   private Principal savedPrincipal;
   private boolean admin;
   // folder path -> actions; a path not listed inherits from its nearest listed ancestor
   private Map<String, Set<ResourceAction>> granted;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      storage = spy(new BlobIndexedStorage(blobStorageManager));
      user = new SRPrincipal(new IdentityID("user77906v", ORG), new IdentityID[0],
                             new String[0], ORG, 0L);
      ThreadContext.setContextPrincipal(user);
      admin = false;
      granted = new HashMap<>();
      granted.put("/", EnumSet.of(ResourceAction.READ));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> admin ||
            inherited(inv.getArgument(2, String.class))
               .contains(inv.getArgument(3, ResourceAction.class)));

      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      when(scheduleManager.getScheduleTasks(anyString())).thenReturn(new Vector<>());
      when(scheduleManager.getScheduleTasks(any(Principal.class), any(), anyString()))
         .thenReturn(new Vector<>());
      when(scheduleManager.hasDependency(any(), anyString()))
         .thenAnswer(inv -> Set.of(PAYROLL, OPEN_JOB).contains(inv.getArgument(1, String.class)));

      ScheduleTaskFolderService folderService = new ScheduleTaskFolderService(
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

      AssetFolder root = new AssetFolder();
      root.addEntry(folder("Open"));
      root.addEntry(folder("Secret"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder open = new AssetFolder();
      open.addEntry(task(OPEN_JOB));
      open.addEntry(folder("Open/Child"));
      storage.putXMLSerializable(folder("Open").toIdentifier(), open);
      storage.putXMLSerializable(folder("Open/Child").toIdentifier(), new AssetFolder());
      AssetFolder secret = new AssetFolder();
      secret.setOwner(BOSS);
      secret.addEntry(task(PAYROLL));
      secret.addEntry(folder("Secret/Sub"));
      storage.putXMLSerializable(folder("Secret").toIdentifier(), secret);
      storage.putXMLSerializable(folder("Secret/Sub").toIdentifier(), new AssetFolder());
      clearInvocations(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // (a) an admin passes every folder check and gets the model, the dependents and the browser
   @Test
   void admin_getsModelDependentsAndBrowser() throws Exception {
      admin = true;

      for(EditTaskFolderDialogModel model : List.of(
         portalFolderController.getFolderEditModel("Secret", user),
         emController.getFolderEditModel("Secret", user)))
      {
         assertEquals("Secret", model.folderName());
         assertEquals(BOSS, model.owner());
      }

      List<String> expected = List.of(SUtil.getTaskNameWithoutOrg(OPEN_JOB),
                                      SUtil.getTaskNameWithoutOrg(PAYROLL));
      assertEquals(expected, portalScheduleController
         .checkScheduleFolderDependency(paths("Open", "Secret"), user).taskNames());
      assertEquals(expected, emController
         .checkScheduledTaskDependency(paths("Open", "Secret"), user).taskNames());

      TaskFolderBrowserModel browser =
         portalFolderController.getDatasourcesBrowser("Secret", false, user);
      assertEquals(List.of("Secret/Sub"), folderPaths(browser.folderList()));
      assertEquals(List.of("Open", "Secret"), folderPaths(
         portalFolderController.getDatasourcesBrowser("/", false, user).folderList()));
   }

   // (b) DELETE inherited from the parent, on a missing child: [] and no exception
   @Test
   void inheritedDelete_missingChild_answersEmpty() throws Exception {
      granted.put("Open", EnumSet.of(ResourceAction.DELETE));

      for(String path : List.of("Open/NoSuch", "Open/NoSuch/Deeper")) {
         assertEquals(List.of(), portalScheduleController
            .checkScheduleFolderDependency(paths(path), user).taskNames(), "portal " + path);
         assertEquals(List.of(), emController
            .checkScheduledTaskDependency(paths(path), user).taskNames(), "EM " + path);
      }

      // the existing empty child answers the same
      assertEquals(List.of(), portalScheduleController
         .checkScheduleFolderDependency(paths("Open/Child"), user).taskNames());
   }

   // (c) the root: editModel needs DELETE and WRITE on / like any other path; the browser
   // shows the root without root READ
   @Test
   void root_editModelChecksPermissionBrowserAlwaysShown() throws Exception {
      assertThrows(SecurityException.class,
                   () -> portalFolderController.getFolderEditModel("/", user));
      assertThrows(SecurityException.class, () -> emController.getFolderEditModel("/", user));
      assertStorageNotRead();

      granted.remove("/");
      TaskFolderBrowserModel model = portalFolderController.getDatasourcesBrowser("/", false, user);
      assertTrue(model.root());
      assertEquals(List.of(), folderPaths(model.folderList()));
   }

   // (d) the same denied path gives the same answer whether it exists or not, on all endpoints
   @Test
   void deniedPath_sameAnswerExistingAndRemoved() throws Exception {
      granted.put("Secret", EnumSet.noneOf(ResourceAction.class));
      List<String> before = answers("Secret");
      List<String> beforeChild = answers("Secret/Sub");

      storage.remove(folder("Secret/Sub").toIdentifier());
      storage.remove(folder("Secret").toIdentifier());
      assertFalse(storage.contains(folder("Secret").toIdentifier()));
      clearInvocations(storage);

      assertEquals(before, answers("Secret"));
      assertEquals(beforeChild, answers("Secret/Sub"));
      assertStorageNotRead();
   }

   private List<String> answers(String path) {
      List<String> result = new ArrayList<>();
      result.add(answer(() -> portalFolderController.getFolderEditModel(path, user)));
      result.add(answer(() -> emController.getFolderEditModel(path, user)));
      result.add(answer(() -> portalScheduleController
         .checkScheduleFolderDependency(paths(path), user)));
      result.add(answer(() -> emController.checkScheduledTaskDependency(paths(path), user)));
      result.add(answer(() -> portalFolderController.getDatasourcesBrowser(path, false, user)));
      return result;
   }

   private static String answer(Callable<?> call) {
      try {
         return "OK " + call.call();
      }
      catch(Exception e) {
         return e.getClass().getName() + ": " + e.getMessage();
      }
   }

   private Set<ResourceAction> inherited(String path) {
      for(String p = path; p != null; p = parent(p)) {
         if(granted.containsKey(p)) {
            return granted.get(p);
         }
      }

      return Set.of();
   }

   private static String parent(String path) {
      if("/".equals(path)) {
         return null;
      }

      int index = path.lastIndexOf('/');
      return index < 0 ? "/" : path.substring(0, index);
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
