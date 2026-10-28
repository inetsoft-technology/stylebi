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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78592, an import that overwrites a stored task keeps the task in the folder it is stored
 * in, and the rollback of the overwrite restores it in that folder. Runs the real apply, plan and
 * staging services on the real schedule manager and storage. A folder is listed by its stored
 * entries (as {@code ScheduleService.getScheduleTaskList} does), so the task must be in its
 * folder's entries only, not also in the root folder's.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskImportChangesetApplyServiceFolderTest {
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

      scheduleTaskService = mock(ScheduleTaskService.class);
      AdminBackupService backupService = mock(AdminBackupService.class);
      when(backupService.backup(anyString())).thenReturn("snap");
      transferService = new ScheduleTaskTransferService(scheduleManager, mock(ScheduleService.class));
      planService = new ScheduleTaskImportChangePlanService(transferService, scheduleManager,
                                                       scheduleTaskService);
      service = new ScheduleTaskImportChangesetApplyService(
         planService, transferService, scheduleManager, mock(AdminScheduleGateway.class),
         scheduleTaskService, backupService);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(String taskId : taskIds) {
            if(scheduleManager.getScheduleTask(taskId) != null) {
               scheduleManager.removeScheduleTask(taskId, admin);
            }
         }

         IndexedStorage storage = IndexedStorage.getIndexedStorage();
         AssetFolder root = getFolder("/");

         for(String path : folders) {
            String identifier = folderEntry(path).toIdentifier();

            if(storage.contains(identifier, ORG)) {
               storage.remove(identifier);
            }

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
         folders.clear();
         ThreadContext.setPrincipal(oldPrincipal);
         ThreadContext.setContextPrincipal(oldContextPrincipal);
      }
   }

   @Test
   void overwriteKeepsTheTaskInItsFolder() throws Exception {
      String taskId = storeTask("i78592flat", "I78592F");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, importOverwrite(taskId).status());

      assertEquals(11, hourOf(taskId), "the task is overwritten");
      assertFiledIn(taskId, "I78592F");
   }

   @Test
   void overwriteKeepsTheTaskInItsNestedFolder() throws Exception {
      addFolder("I78592N");
      String taskId = storeTask("i78592nested", "I78592N/Sub");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, importOverwrite(taskId).status());

      assertEquals(11, hourOf(taskId), "the task is overwritten");
      assertFiledIn(taskId, "I78592N/Sub");
   }

   @Test
   void overwriteKeepsARootTaskInTheRootFolder() throws Exception {
      String taskId = storeTask("i78592root", null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, importOverwrite(taskId).status());

      assertEquals(11, hourOf(taskId), "the task is overwritten");
      ScheduleTask task = load(taskId);
      assertEquals("/", task.getPath(), "path");
      assertTrue(listedIn("/", taskId), "listed in the root folder");
   }

   /** The preview also runs the sanitizers now, so fail only the call apply makes while writing (the third: preview, apply re-resolve, write). */
   private void failApplyOfTask(String failId) throws Exception {
      java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
      doAnswer(inv -> {
         if(calls.incrementAndGet() > 2) {
            throw new IllegalStateException("forced failure");
         }

         return null;
      }).when(scheduleTaskService).sanitizeConditions(
         argThat((ScheduleTask t) -> t != null && failId.equals(t.getTaskId())),
         any(ScheduleTask.class), any());
   }

   @Test
   void rollbackOfOverwriteRestoresTheTaskInItsFolder() throws Exception {
      String taskId = storeTask("i78592undo", "I78592R");
      ScheduleTask other = task("i78592new", 10);
      String otherId = other.getTaskId();
      taskIds.add(otherId);
      // the import of the second, new task fails, so the overwrite of the first is rolled back
      failApplyOfTask(otherId);

      ScheduleTaskImportApplyResult result = importTasks(
         List.of(task("i78592undo", 11), other), List.of(change(taskId, true), change(otherId, false)));

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(result.rollbackFailures() == null || result.rollbackFailures().isEmpty(),
                 "rollback failures " + result.rollbackFailures());
      assertNull(scheduleManager.getScheduleTask(otherId), "the new task is not imported");
      assertEquals(9, hourOf(taskId), "the stored task is restored");
      assertFiledIn(taskId, "I78592R");
   }

   @Test
   void overwriteKeepsTheStoredFolderWhenTheExportNamesAnotherFolder() throws Exception {
      String taskId = storeTask("i78592export", "I78592E");
      addFolder("I78592EOther");
      // the import does not move a task into the folder its export names
      ScheduleTask staged = task("i78592export", 11);
      staged.setPath("I78592EOther");

      ScheduleTaskImportApplyResult result =
         importTasks(List.of(staged), List.of(change(taskId, true)));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(11, hourOf(taskId), "the task is overwritten");
      assertFiledIn(taskId, "I78592E");
      assertFalse(listedIn("I78592EOther", taskId), "listed in the folder of the export");
   }

   @Test
   void rollbackOfSeveralOverwritesRestoresEachTaskInItsFolder() throws Exception {
      String flatId = storeTask("i78592multi1", "I78592M1");
      addFolder("I78592M2");
      String nestedId = storeTask("i78592multi2", "I78592M2/Sub");
      ScheduleTask other = task("i78592multinew", 10);
      String otherId = other.getTaskId();
      taskIds.add(otherId);
      // the import of the third, new task fails, so both overwrites are rolled back
      failApplyOfTask(otherId);

      ScheduleTaskImportApplyResult result = importTasks(
         List.of(task("i78592multi1", 11), task("i78592multi2", 12), other),
         List.of(change(flatId, true), change(nestedId, true), change(otherId, false)));

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(result.rollbackFailures() == null || result.rollbackFailures().isEmpty(),
                 "rollback failures " + result.rollbackFailures());
      assertNull(scheduleManager.getScheduleTask(otherId), "the new task is not imported");
      assertEquals(9, hourOf(flatId), "the first stored task is restored");
      assertEquals(9, hourOf(nestedId), "the second stored task is restored");
      assertFiledIn(flatId, "I78592M1");
      assertFiledIn(nestedId, "I78592M2/Sub");
   }

   /** Stores a task (at hour 9) in a folder, as a new task saved in that folder. */
   private String storeTask(String name, String folder) throws Exception {
      ScheduleTask task = task(name, 9);
      String taskId = task.getTaskId();
      taskIds.add(taskId);
      AssetEntry parent = null;

      if(folder != null) {
         addFolder(folder);
         parent = folderEntry(folder);
      }

      scheduleManager.setScheduleTask(taskId, task, parent, admin);
      ScheduleTask stored = load(taskId);
      assertEquals(folder == null ? "/" : folder, stored.getPath(), "stored path");
      assertEquals(folder == null, listedIn("/", taskId), "stored task listed in root");

      if(folder != null) {
         assertTrue(listedIn(folder, taskId), "stored task listed in " + folder);
      }

      return taskId;
   }

   /** Imports the same task at hour 11 over the stored task. */
   private ScheduleTaskImportApplyResult importOverwrite(String taskId) throws Exception {
      ScheduleTask stored = load(taskId);
      return importTasks(List.of(task(stored.getName(), 11)), List.of(change(taskId, true)));
   }

   private ScheduleTaskImportApplyResult importTasks(List<ScheduleTask> tasks,
                                                     List<ScheduleTaskImportChangeRequest> changes)
      throws Exception
   {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         writer.print("<schedule>");

         for(ScheduleTask task : tasks) {
            task.writeXML(writer);
         }

         writer.print("</schedule>");
      }

      String base64 = Base64.getEncoder().encodeToString(
         buffer.toString().getBytes(StandardCharsets.UTF_8));
      String stagingToken = transferService.stage(base64, admin).stagingToken();

      ScheduleTaskImportPlanRequest planRequest = new ScheduleTaskImportPlanRequest();
      planRequest.setTask("import over a stored task");
      planRequest.setStagingToken(stagingToken);
      planRequest.setChanges(changes);
      ResolvedPlan plan = planService.resolve(planRequest, admin, "http://localhost:8080/");

      ScheduleTaskImportApplyRequest request = new ScheduleTaskImportApplyRequest();
      request.setTask(planRequest.getTask());
      request.setStagingToken(stagingToken);
      request.setChanges(changes);
      request.setPlanHash(plan.planHash());
      request.setTaskToken(plan.taskToken());
      request.setReviewOutcome("approved");
      request.setAcknowledgeOverwrite(true);
      return service.apply(request, admin, "http://localhost:8080/");
   }

   /** Asserts that a task is in the folder and listed in it only, not also in the root folder. */
   private void assertFiledIn(String taskId, String folder) throws Exception {
      ScheduleTask task = load(taskId);
      assertAll(
         () -> assertEquals(folder, task.getPath(), "path"),
         () -> assertTrue(listedIn(folder, taskId), "listed in " + folder),
         () -> assertFalse(listedIn("/", taskId), "listed in the root folder"));
   }

   /** Whether the task is among the tasks of a folder, the lookup the task list of a folder does. */
   private boolean listedIn(String path, String taskId) throws Exception {
      AssetFolder folder = getFolder(path);

      return folder != null &&
         scheduleManager.getScheduleTasks(folder.getEntries(), false, false, ORG).stream()
            .anyMatch(task -> taskId.equals(task.getTaskId()));
   }

   private void addFolder(String path) throws Exception {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      AssetEntry entry = folderEntry(path);
      folders.add(path);
      storage.putXMLSerializable(entry.toIdentifier(), new AssetFolder());
      String parentPath = entry.getParentPath();
      AssetFolder parent = getFolder(parentPath);

      if(parent == null) {
         parent = new AssetFolder();
      }

      parent.addEntry(entry);
      storage.putXMLSerializable(folderEntry(parentPath).toIdentifier(), parent);
   }

   private static AssetFolder getFolder(String path) throws Exception {
      return (AssetFolder) IndexedStorage.getIndexedStorage()
         .getXMLSerializable(folderEntry(path).toIdentifier(), null, ORG);
   }

   private ScheduleTask load(String taskId) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskId);
      assertNotNull(task, taskId);
      return task;
   }

   private int hourOf(String taskId) {
      return ((TimeCondition) load(taskId).getCondition(0)).getHour();
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static ScheduleTask task(String name, int hour) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(OWNER);
      task.setEnabled(true);
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setHour(hour);
      condition.setMinute(0);
      condition.setSecond(0);
      task.addCondition(condition);
      return task;
   }

   private static ScheduleTaskImportChangeRequest change(String taskId, boolean overwrite) {
      ScheduleTaskImportChangeRequest change = new ScheduleTaskImportChangeRequest();
      change.setTaskId(taskId);
      change.setOverwrite(overwrite);
      return change;
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID OWNER = new IdentityID("admin", ORG);
   private final List<String> taskIds = new ArrayList<>();
   private final List<String> folders = new ArrayList<>();
   private SRPrincipal admin;
   private Principal oldPrincipal;
   private Principal oldContextPrincipal;
   private ScheduleTaskService scheduleTaskService;
   private ScheduleTaskTransferService transferService;
   private ScheduleTaskImportChangePlanService planService;
   private ScheduleTaskImportChangesetApplyService service;
}
