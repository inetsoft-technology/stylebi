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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77972: a batch action runs its target task as the target's owner with the parameters of
 * the batch action, but neither the save nor the run checked that the target is a task the
 * holder may see. A user could plant a batch action that runs another user's task. The target
 * must be one that the holder task's owner, and the saving principal, may see
 * (RepletEngine.hasTaskPermission, the rule that lists the tasks a batch action may target), at
 * save time (ScheduleManager.checkSaveRefusals, for added targets only) and at run time
 * (BatchAction.run, against the owner ScheduleTask.doRun hands down).
 *
 * Uses the real SecurityEngine / FileAuthenticationProvider, the real ScheduleManager bean and
 * storage, and the real task editor service save (ScheduleTaskService.saveTask). The persisted
 * task is read after clearing the task map cache.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  BatchActionTargetPermissionTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchActionTargetPermissionTest {
   private static final String ORG = "batporg";
   private static final String SCHEDULE_ROLE = "batpSched";
   private static final String ORG_ADMIN_ROLE = "batpOrgAdmin";
   private static final String SITE_ADMIN_ROLE = "batpSiteAdmin";
   private static final String TEAM = "batpTeam";
   private static final String LINK = "http://host/";
   private static final String ALICE_TASK = "BatpAliceNightly";
   private static final String ALICE_TASK_ID = "alice~;~" + ORG + ":" + ALICE_TASK;

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();
   private String shareInGroup;
   private String taskFailedMail;
   private Principal savedPrincipal;
   private ScheduleTaskService service;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @Autowired
   AnalyticRepository analyticRepository;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("batpOrg", ORG)
         .addRole(SCHEDULE_ROLE, ORG)
         .addOrgAdminRole(ORG_ADMIN_ROLE, ORG)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG)
         .addGroup(TEAM, ORG)
         .addUser("alice", ORG, "password")
         .addUser("mallory", ORG, "password")
         .addUser("mate", ORG, "password")
         .addUser("granted", ORG, "password")
         .addUser("oadmin", ORG, "password")
         .addUser("sadm", ORG, "password")
         .addUserToRole("alice", SCHEDULE_ROLE, ORG)
         .addUserToRole("mallory", SCHEDULE_ROLE, ORG)
         .addUserToRole("mate", SCHEDULE_ROLE, ORG)
         .addUserToRole("granted", SCHEDULE_ROLE, ORG)
         .addUserToRole("oadmin", SCHEDULE_ROLE, ORG)
         .addUserToRole("oadmin", ORG_ADMIN_ROLE, ORG)
         .addUserToRole("sadm", SCHEDULE_ROLE, ORG)
         .addUserToRole("sadm", SITE_ADMIN_ROLE, ORG)
         .addUserToGroup("alice", TEAM, ORG)
         .addUserToGroup("mate", TEAM, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULE_TASK, ALICE_TASK_ID, ResourceAction.READ,
                          "granted", Identity.USER, ORG)
         .grantPermission(ResourceType.SCHEDULE_TASK, ALICE_TASK_ID, ResourceAction.WRITE,
                          "granted", Identity.USER, ORG)
         .grantPermission(ResourceType.SCHEDULE_TASK, ALICE_TASK_ID, ResourceAction.DELETE,
                          "granted", Identity.USER, ORG);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleSaveRefusalKeepsTaskTest
      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);

      shareInGroup = SreeEnv.getProperty("schedule.options.shareTaskInGroup");
      taskFailedMail = SreeEnv.getProperty("schedule.options.taskFailed");
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "false");
      // a refused run fails the task, don't try to mail it
      SreeEnv.setProperty("schedule.options.taskFailed", "false");
   }

   @AfterAll
   void teardownAll() {
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", shareInGroup);
      SreeEnv.setProperty("schedule.options.taskFailed", taskFailedMail);
      SreeEnv.setProperty("schedule.task.listener", null);
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      ScheduleConditionService conditionService = new ScheduleConditionService();
      ScheduleService scheduleService = new ScheduleService(
         analyticRepository, scheduleManager, null, conditionService,
         securityEngine.getSecurityProvider(), mock(DeployService.class), null, null,
         securityEngine, mock(ScheduleTaskFolderService.class), null, null,
         mock(RenameTransformHandler.class));
      service = spy(new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, conditionService,
         securityEngine.getSecurityProvider(), null, securityEngine));
      doNothing().when(service).setTaskOptions(any(), any(), any());
      doReturn(null).when(service).getDialogModel(anyString(), any(), anyBoolean());
      // stored without the owner grants, which would replace the grants of the builder
      storeRawTask(newTask(ALICE_TASK, "alice", null), ORG);
      RecordingListener.RUNS.clear();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "false");
      SreeEnv.setProperty("schedule.task.listener", null);
      securityEngineOverrides.setSecurityEnabled(true);

      for(String orgID : List.of(ORG, Organization.getDefaultOrganizationID())) {
         taskMap(orgID).values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      }

      taskNames.clear();
   }

   // --- save: the task editor (portal and EM) -------------------------------------------------

   // the reported case, a plain user plants a batch action that runs alice's task
   @Test
   void portalSave_targetTheUserMayNotSee_isRefused() throws Exception {
      String holderId = storeTask("BatpPortalHolder", "mallory", null);
      SRPrincipal mallory = principal("mallory");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(mallory, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK,
                                                  mallory, false)));

      assertEquals(0, assertPersisted(holderId).getActionCount(), "nothing was saved");
   }

   @Test
   void emSave_targetTheUserMayNotSee_isRefused() throws Exception {
      String holderId = storeTask("BatpEmHolder", "mallory", null);
      SRPrincipal mallory = principal("mallory");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(mallory, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK,
                                                  mallory, true)));

      assertEquals(0, assertPersisted(holderId).getActionCount(), "nothing was saved");
   }

   @Test
   void emSave_ownTarget_isSaved() throws Exception {
      String targetId = storeTask("BatpMalloryTarget", "mallory", null);
      String holderId = storeTask("BatpOwnHolder", "mallory", null);
      SRPrincipal mallory = principal("mallory");

      as(mallory, () -> service.saveTask(batchModel(holderId, targetId), LINK, mallory, true));

      assertEquals(targetId, target(assertPersisted(holderId)));
   }

   // an org admin administers alice, the EM batch target list offers alice's task to it
   @Test
   void emSave_orgAdminTargetsItsUsersTask_isSaved() throws Exception {
      String holderId = storeTask("BatpOrgAdminHolder", "oadmin", null);
      SRPrincipal oadmin = principal("oadmin");
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(oadmin), "test setup");

      as(oadmin, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK, oadmin, true));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   // schedule.options.shareTaskInGroup, a member of alice's group sees alice's task
   @Test
   void emSave_shareGroupMemberTargetsGroupTask_isSaved() throws Exception {
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", "true");
      String holderId = storeTask("BatpMateHolder", "mate", null);
      SRPrincipal mate = principal("mate");

      as(mate, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK, mate, true));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   // an explicit SCHEDULE_TASK read, write and delete grant on alice's task
   @Test
   void emSave_explicitGrantOnTarget_isSaved() throws Exception {
      String holderId = storeTask("BatpGrantedHolder", "granted", null);
      SRPrincipal granted = principal("granted");

      as(granted, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK, granted,
                                         true));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   // e.g. an import of the holder before its target, the run-time check covers it
   @Test
   void save_missingTarget_isSaved() throws Exception {
      String holderId = storeTask("BatpMissingHolder", "mallory", null);
      SRPrincipal mallory = principal("mallory");
      String missing = "alice~;~" + ORG + ":BatpNoSuchTask";

      as(mallory, () -> service.saveTask(batchModel(holderId, missing), LINK, mallory, true));

      assertEquals(missing, target(assertPersisted(holderId)));
   }

   // a site admin editing mallory's task may see alice's task, but the task runs for mallory,
   // who may not, so it would be refused at every run
   @Test
   void siteAdminSaver_intoUsersTask_targetTheOwnerMayNotSee_isRefused() throws Exception {
      String holderId = storeTask("BatpSiteAdminEdit", "mallory", null);
      SRPrincipal sadm = principal("sadm");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(sadm), "test setup");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(sadm, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK,
                                               sadm, true)));

      assertEquals(0, assertPersisted(holderId).getActionCount(), "nothing was saved");
   }

   @Test
   void siteAdminOwnedHolder_targetsUsersTask_isSaved() throws Exception {
      String holderId = storeTask("BatpSiteAdminHolder", "sadm", null);
      SRPrincipal sadm = principal("sadm");

      as(sadm, () -> service.saveTask(batchModel(holderId, ALICE_TASK_ID), LINK, sadm, true));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   // --- save: a target the stored task already holds -----------------------------------------

   // the stored batch action (e.g. saved before the check, or the owner lost the access since)
   // is kept, so the task's other parts can still be changed
   @Test
   void keptTarget_conditionOnlyEdit_isSaved() throws Exception {
      String holderId = storeTask("BatpKeptEdit", "mallory", ALICE_TASK_ID);
      SRPrincipal mallory = principal("mallory");
      ScheduleTask edited = scheduleManager.getScheduleTask(holderId, ORG).clone();
      edited.addCondition(TimeCondition.at(2, 30, 0));

      as(mallory, () -> {
         scheduleManager.setScheduleTask(holderId, edited, mallory);
         return null;
      });

      ScheduleTask stored = assertPersisted(holderId);
      assertEquals(2, stored.getConditionCount());
      assertEquals(ALICE_TASK_ID, target(stored));
   }

   // the enterprise public API replaces the task for every edit (ScheduleApiService)
   @Test
   void keptTarget_replace_isSaved() throws Exception {
      String holderId = storeTask("BatpKeptReplace", "mallory", ALICE_TASK_ID);
      SRPrincipal mallory = principal("mallory");
      ScheduleTask edited = scheduleManager.getScheduleTask(holderId, ORG).clone();
      edited.addCondition(TimeCondition.at(2, 30, 0));

      as(mallory, () -> {
         scheduleManager.replaceScheduleTask(holderId, edited, null, mallory);
         return null;
      });

      ScheduleTask stored = assertPersisted(holderId);
      assertEquals(2, stored.getConditionCount());
      assertEquals(ALICE_TASK_ID, target(stored));
   }

   @Test
   void addedTarget_replace_isRefusedAndKeepsTheStoredTask() throws Exception {
      String holderId = storeTask("BatpAddedReplace", "mallory", null);
      SRPrincipal mallory = principal("mallory");
      ScheduleTask edited = scheduleManager.getScheduleTask(holderId, ORG).clone();
      edited.addAction(batchAction(ALICE_TASK_ID));

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(mallory, () -> {
            scheduleManager.replaceScheduleTask(holderId, edited, null, mallory);
            return null;
         }));

      assertEquals(0, assertPersisted(holderId).getActionCount());
   }

   // the rename removes the stored task before the renamed task is saved
   @Test
   void keptTarget_rename_isSaved() throws Exception {
      String holderId = storeTask("BatpKeptRename", "mallory", ALICE_TASK_ID);
      SRPrincipal mallory = principal("mallory");
      String renamedId = "mallory~;~" + ORG + ":BatpKeptRenamed";
      taskNames.add("BatpKeptRenamed");

      as(mallory, () -> renameService().updateTaskName(holderId, renamedId, null, mallory));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(renamedId)));
   }

   // an owner change checks the kept targets against the new owner, before the stored task is
   // removed
   @Test
   void keptTarget_renameToOwnerWhoMayNotSeeIt_isRefusedAndKeepsTheTask() throws Exception {
      String holderId = storeTask("BatpOwnerChange", "oadmin", ALICE_TASK_ID);
      SRPrincipal sadm = principal("sadm");
      IdentityID mallory = new IdentityID("mallory", ORG);
      String renamedId = mallory.convertToKey() + ":BatpOwnerChange";

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(sadm, () -> renameService().updateTaskName(holderId, renamedId, mallory,
                                                             sadm)));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   @Test
   void keptTarget_folderMove_isSaved() throws Exception {
      String holderId = storeTask("BatpKeptMove", "mallory", ALICE_TASK_ID);
      SRPrincipal mallory = principal("mallory");
      ScheduleTaskFolderService folderService = new ScheduleTaskFolderService(
         scheduleManager, null, null, mock(IndexedStorage.class),
         mock(RenameTransformHandler.class));
      AssetEntry taskEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.SCHEDULE_TASK, "/" + holderId, null);
      AssetEntry target = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.SCHEDULE_TASK_FOLDER, "Target", null);

      as(mallory, () -> {
         folderService.changeTaskFolder(taskEntry, target, mallory);
         return null;
      });

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   // an overwriting deploy import removes the stored task before it saves the imported one
   @Test
   void keptTarget_overwritingImport_isSaved() throws Exception {
      String holderId = storeTask("BatpKeptImport", "mallory", ALICE_TASK_ID);

      importOverwriting(newTask("BatpKeptImport", "mallory", ALICE_TASK_ID));

      assertEquals(ALICE_TASK_ID, target(assertPersisted(holderId)));
   }

   @Test
   void addedTarget_overwritingImport_isRefusedAndKeepsTheTask() throws Exception {
      String holderId = storeTask("BatpAddedImport", "mallory", null);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> importOverwriting(newTask("BatpAddedImport", "mallory", ALICE_TASK_ID)));

      assertEquals(0, assertPersisted(holderId).getActionCount());
   }

   // --- run ------------------------------------------------------------------------------------

   // a batch action stored before the check (or whose owner lost the access since) is refused
   // when the task runs, and the target task is not run
   @Test
   void run_targetTheOwnerMayNotSee_isRefused() throws Throwable {
      String holderId = storeTask("BatpRunForeign", "mallory", ALICE_TASK_ID);
      ScheduleTask holder = scheduleManager.getScheduleTask(holderId, ORG);

      Throwable thrown = assertThrows(Throwable.class, () -> runTask(holder));

      assertInstanceOf(inetsoft.sree.security.SecurityException.class, thrown);
      assertEquals(List.of("BatpRunForeign"), startedTasks(), "the target task is not run");
   }

   @Test
   void run_ownTarget_runsTheTarget() throws Throwable {
      String targetId = storeTask("BatpRunOwnTarget", "mallory", null);
      String holderId = storeTask("BatpRunOwn", "mallory", targetId);

      runTask(scheduleManager.getScheduleTask(holderId, ORG));

      assertEquals(List.of("BatpRunOwn", "BatpRunOwnTarget"), startedTasks());
   }

   // each level is checked against the owner of the task that holds it: the org admin may run
   // mallory's task, but mallory's task may not run alice's
   @Test
   void run_nestedBatch_checksTheOwnerOfEachLevel() throws Throwable {
      String midId = storeTask("BatpRunMid", "mallory", ALICE_TASK_ID);
      String holderId = storeTask("BatpRunTop", "oadmin", midId);

      Throwable thrown = assertThrows(Throwable.class,
         () -> runTask(scheduleManager.getScheduleTask(holderId, ORG)));

      assertInstanceOf(inetsoft.sree.security.SecurityException.class, thrown);
      assertEquals(List.of("BatpRunTop", "BatpRunMid"), startedTasks(),
                   "alice's task is not run");
   }

   // an action that isn't run by ScheduleTask.doRun has no owner, the run principal is checked
   @Test
   void run_withoutOwner_checksTheRunPrincipal() {
      BatchAction batch = batchAction(ALICE_TASK_ID);
      SRPrincipal mallory = principal("mallory");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(mallory, () -> {
            batch.run(mallory);
            return null;
         }));
   }

   // --- internal targets and security disabled -------------------------------------------------

   // Bug #77531 alone decides an internal target, a site admin's batch action to one is saved and
   // runs
   @Test
   void siteAdminHolder_internalTarget_isSavedAndRuns() throws Throwable {
      String internalId = InternalScheduledTaskService.ASSET_FILE_BACKUP;
      storeInternalTask(internalId);
      String holderId = storeTask("BatpInternalHolder", "sadm", null);
      SRPrincipal sadm = principal("sadm");

      as(sadm, () -> service.saveTask(batchModel(holderId, internalId), LINK, sadm, true));
      assertEquals(internalId, target(assertPersisted(holderId)));
      // the saved action has no parameters, run one that has
      storeRawTask(newTask("BatpInternalHolder", "sadm", internalId), ORG);

      runTask(scheduleManager.getScheduleTask(holderId, ORG));

      assertEquals(List.of("BatpInternalHolder", internalId), startedTasks());
   }

   @Test
   void userHolder_internalTarget_isStillRefused() throws Exception {
      String holderId = storeTask("BatpInternalUser", "mallory", null);
      SRPrincipal mallory = principal("mallory");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> as(mallory, () -> service.saveTask(
            batchModel(holderId, InternalScheduledTaskService.ASSET_FILE_BACKUP), LINK, mallory,
            true)));
   }

   // without security there are no identities to check, the batch action is saved and runs
   @Test
   void securityDisabled_targetOfAnotherOwner_isSavedAndRuns() throws Throwable {
      String holderId = storeTask("BatpNoSecurity", "mallory", null);
      SRPrincipal mallory = principal("mallory");
      securityEngineOverrides.setSecurityEnabled(false);
      ScheduleTask edited = scheduleManager.getScheduleTask(holderId, ORG).clone();
      edited.addAction(batchAction(ALICE_TASK_ID));

      as(mallory, () -> {
         scheduleManager.setScheduleTask(holderId, edited, mallory);
         return null;
      });
      ScheduleTask holder = assertPersisted(holderId);
      assertEquals(ALICE_TASK_ID, target(holder));

      runTask(holder);

      assertEquals(List.of("BatpNoSecurity", ALICE_TASK), startedTasks());
   }

   // --- helpers --------------------------------------------------------------------------------

   private ScheduleService renameService() {
      return new ScheduleService(
         null, scheduleManager, null, new ScheduleConditionService(), null,
         mock(DeployService.class), null, null, null, mock(ScheduleTaskFolderService.class),
         null, null, mock(RenameTransformHandler.class));
   }

   /**
    * Stores a task, its batch action with no check, as a task stored before this fix (or by an
    * older version). The owner gets the permissions on it, as for any save.
    *
    * @param target the target task of a batch action of the task, or {@code null} for none.
    */
   private String storeTask(String name, String owner, String target) throws Exception {
      ScheduleTask task = newTask(name, owner, null);
      ThreadContext.setContextPrincipal(principal(owner));

      try {
         scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));

         if(target != null) {
            storeRawTask(newTask(name, owner, target), ORG);
         }
      }
      finally {
         ThreadContext.setContextPrincipal(savedPrincipal);
      }

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "test setup");
      return task.getTaskId();
   }

   /**
    * Stores a task with no check and no permission.
    */
   private void storeRawTask(ScheduleTask task, String orgID) {
      String key = ReflectionTestUtils.invokeMethod(
         scheduleManager, "getTaskIdentifier", task.getTaskId(), orgID);
      taskMap(orgID).put(key, task);
   }

   @SuppressWarnings("unchecked")
   private Map<String, ScheduleTask> taskMap(String orgID) {
      return (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(orgID);
   }

   private void storeInternalTask(String taskId) {
      ScheduleTask task = new ScheduleTask(taskId, ScheduleTask.Type.INTERNAL_TASK);
      task.setOwner(new IdentityID(inetsoft.uql.XPrincipal.SYSTEM,
                                   Organization.getDefaultOrganizationID()));
      storeRawTask(task, Organization.getDefaultOrganizationID());
      taskNames.add(taskId);
   }

   private ScheduleTask newTask(String name, String owner, String target) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID(owner, ORG));
      task.addCondition(TimeCondition.at(1, 30, 0));

      if(target != null) {
         task.addAction(batchAction(target));
      }

      taskNames.add(name);
      return task;
   }

   private static BatchAction batchAction(String target) {
      BatchAction batch = new BatchAction();
      batch.setTaskId(target);
      batch.setEmbeddedParameters(new ArrayList<>(List.of(new HashMap<>(Map.of("region", "x")))));
      return batch;
   }

   private ScheduleTaskEditorModel batchModel(String holderId, String target) {
      BatchActionModel action = BatchActionModel.builder()
         .taskName(target)
         .actionType("BatchAction")
         .actionClass("BatchActionModel")
         .build();
      return ScheduleTaskEditorModel.builder()
         .taskName(holderId)
         .oldTaskName(holderId)
         .options(mock(TaskOptionsPaneModel.class))
         // a task without a condition isn't loaded (ScheduleTask.parseXML)
         .addConditions(new ScheduleConditionService().getConditionModel(
            TimeCondition.at(1, 30, 0), principal("sadm")))
         .addActions(action)
         .build();
   }

   private static String target(ScheduleTask task) {
      assertEquals(1, task.getActionCount(), "one batch action");
      return ((BatchAction) task.getAction(0)).getTaskId();
   }

   private void importOverwriting(ScheduleTask imported) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      writer.write("<ScheduleTask>");
      imported.writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      ThreadContext.setContextPrincipal(principal(imported.getOwner().getName()));
      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)),
         config, true, false);
   }

   /**
    * Runs a task the way the scheduler runs it (ScheduleTask.run -> doRun), with its run
    * principal, and records the tasks that are started.
    */
   private void runTask(ScheduleTask task) throws Throwable {
      SreeEnv.setProperty("schedule.task.listener", RecordingListener.class.getName());
      Principal principal = SUtil.getScheduleTaskRunPrincipal(task, null, false);
      ThreadContext.setContextPrincipal(principal);
      task.run(principal);
   }

   private static List<String> startedTasks() {
      return new ArrayList<>(RecordingListener.RUNS);
   }

   private ScheduleTask assertPersisted(String taskId) {
      ReflectionTestUtils.invokeMethod(scheduleManager.getOrgTaskMap(ORG), "clearCache");
      ScheduleTask stored = scheduleManager.getScheduleTask(taskId, ORG);
      assertNotNull(stored, "the stored task must not be lost");
      return stored;
   }

   private SRPrincipal principal(String user) {
      return builder.principalOf(user, ORG);
   }

   private static <T> T as(Principal principal, ThrowingCallable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         return call.call();
      }
      catch(Exception | Error e) {
         throw e;
      }
      catch(Throwable e) {
         throw new RuntimeException(e);
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   @FunctionalInterface
   private interface ThrowingCallable<T> {
      T call() throws Throwable;
   }

   /** Records the name of each schedule task that is started (ScheduleTask.doRun). */
   public static final class RecordingListener implements TaskListener {
      @Override
      public void taskStarted(ScheduleTask task, Principal user) {
         RUNS.add(task.getName());
      }

      @Override
      public void taskCompleted(ScheduleTask task, Principal user, List<Throwable> errors) {
      }

      static final List<String> RUNS = Collections.synchronizedList(new ArrayList<>());
   }

   /**
    * The rename looks up the dependencies of the task (ScheduleService.getDependencyInfo), and a
    * batch action child task gets its locale (SUtil.applyScheduleTaskLocale).
    */
   @Configuration
   static class Config {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      LocaleService localeService(SecurityEngine securityEngine) {
         return new LocaleService(securityEngine);
      }
   }
}
