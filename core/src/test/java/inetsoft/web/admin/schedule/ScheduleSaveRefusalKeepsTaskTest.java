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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77863: the callers that remove (or rewrite the folder of) a stored task before
 * {@code ScheduleManager.setScheduleTask} saves it in its place must make every refusal of the
 * save first, or a refused save loses the stored task. #77530 added {@code checkActionOrgBoundary}
 * to the save without the pre-check, so a non-site-admin's replace (enterprise public API), rename
 * (EM/portal) or overwriting deploy import of a task stored with a viewsheet in another
 * organization deleted it, and a folder move left the folders and the cached path changed. Uses
 * the real SecurityEngine / FileAuthenticationProvider and the real ScheduleManager bean, and
 * reads the persisted task after clearing the task map cache.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  ScheduleSaveRefusalKeepsTaskTest.RenameConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleSaveRefusalKeepsTaskTest {
   private static final String ORG_A = "ssrkorga";
   private static final String ORG_B = "ssrkorgb";
   private static final String SCHEDULE_ROLE = "ssrkSchedRole";
   private static final String SITE_ADMIN_ROLE = "ssrkSiteAdmin";
   private static final String FOREIGN_SHEET = "1^128^__NULL__^Examples/Census^" + ORG_B;
   private static final String OWN_SHEET = "1^128^__NULL__^Examples/Census^" + ORG_A;

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("ssrkA", ORG_A)
         .addOrg("ssrkB", ORG_B)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG_A)
         .addUser("ssrkUser", ORG_A, "password")
         .addUser("ssrkNoSched", ORG_A, "password")
         .addUser("ssrkAdmin", ORG_A, "password")
         .addUserToRole("ssrkUser", SCHEDULE_ROLE, ORG_A)
         .addUserToRole("ssrkAdmin", SITE_ADMIN_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SITE_ADMIN_ROLE, Identity.ROLE, ORG_A)
         // Bug #78129, a saved sheet must be readable by the saver and the run principal
         .grantPermission(ResourceType.REPORT, "Examples/Census", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleTaskSiteAdminNameOwnerTest
      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
   }

   @AfterAll
   void teardownAll() {
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      for(String orgID : List.of(ORG_A, ORG_B, Organization.getDefaultOrganizationID())) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
            scheduleManager.getOrgTaskMap(orgID);
         map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      }

      taskNames.clear();
   }

   // the enterprise public API (ScheduleApiService.updateTaskInScheduleManager) replaces the
   // task under the same id for every edit, e.g. a new condition
   @Test
   void replace_foreignOrgSheet_keepsTheStoredTask() throws Exception {
      String taskId = storeTask("SsrkReplace", "ssrkUser", FOREIGN_SHEET);
      SRPrincipal caller = builder.principalOf("ssrkUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup");
      ScheduleTask edited = scheduleManager.getScheduleTask(taskId, ORG_A).clone();
      edited.addCondition(TimeCondition.at(2, 30, 0));

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.replaceScheduleTask(taskId, edited, null, caller));

      assertPersisted(taskId);
   }

   // the pre-check refuses nothing the save accepts
   @Test
   void replace_ownOrgSheet_isSaved() throws Exception {
      String taskId = storeTask("SsrkReplaceOwn", "ssrkUser", OWN_SHEET);
      SRPrincipal caller = builder.principalOf("ssrkUser", ORG_A);
      ScheduleTask edited = scheduleManager.getScheduleTask(taskId, ORG_A).clone();
      edited.addCondition(TimeCondition.at(2, 30, 0));

      scheduleManager.replaceScheduleTask(taskId, edited, null, caller);

      assertEquals(2, assertPersisted(taskId).getConditionCount());
   }

   // a site admin is exempt from the check, the same as for the save
   @Test
   void checkScheduleTaskSave_siteAdmin_isNotRefused() throws Exception {
      String taskId = storeTask("SsrkAdminCheck", "ssrkUser", FOREIGN_SHEET);
      ScheduleTask task = scheduleManager.getScheduleTask(taskId, ORG_A);

      assertDoesNotThrow(() -> scheduleManager.checkScheduleTaskSave(
         taskId, task, builder.principalOf("ssrkAdmin", ORG_A)));
      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.checkScheduleTaskSave(
            taskId, task, builder.principalOf("ssrkUser", ORG_A)));
   }

   // the EM/portal rename saves the stored actions before the editor's changes are applied
   @Test
   void rename_foreignOrgSheet_keepsTheStoredTask() throws Exception {
      String taskId = storeTask("SsrkRename", "ssrkUser", FOREIGN_SHEET);
      SRPrincipal caller = builder.principalOf("ssrkUser", ORG_A);
      ScheduleService renameService = new ScheduleService(
         null, scheduleManager, null, new ScheduleConditionService(), null,
         mock(DeployService.class), null, null, null, mock(ScheduleTaskFolderService.class),
         null, null, mock(RenameTransformHandler.class));
      taskNames.add("SsrkRenamed");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> renameService.updateTaskName(taskId, caller.getName() + ":SsrkRenamed", null,
                                            caller));

      assertPersisted(taskId);
   }

   // an overwriting deploy import parsed without the org rewrite (isSiteAdmin false, no
   // restricted importer) is saved as the task owner
   @Test
   void overwritingImport_foreignOrgSheet_keepsTheStoredTask() throws Exception {
      String taskId = storeTask("SsrkImport", "ssrkUser", OWN_SHEET);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> importOverwriting("SsrkImport", "ssrkUser", FOREIGN_SHEET));

      ScheduleTask stored = assertPersisted(taskId);
      assertEquals(OWN_SHEET, ((ViewsheetAction) stored.getAction(0)).getViewsheet());
   }

   // refute amendment 2: the import is saved as the owner, which may have lost the scheduler
   // permission since the task was stored
   @Test
   void overwritingImport_ownerWithoutSchedulerAccess_keepsTheStoredTask() throws Exception {
      String taskId = storeTask("SsrkImportNoSched", "ssrkNoSched", OWN_SHEET);

      assertThrows(IOException.class,
         () -> importOverwriting("SsrkImportNoSched", "ssrkNoSched", OWN_SHEET));

      assertPersisted(taskId);
   }

   // a refused folder move changes neither the folders nor the cached task's path
   @Test
   void folderMove_foreignOrgSheet_changesNothing() throws Exception {
      String taskId = storeTask("SsrkMove", "ssrkUser", FOREIGN_SHEET);
      SRPrincipal caller = builder.principalOf("ssrkUser", ORG_A);
      IndexedStorage storage = mock(IndexedStorage.class);
      ScheduleTaskFolderService folderService = new ScheduleTaskFolderService(
         scheduleManager, null, null, storage, mock(RenameTransformHandler.class));
      AssetEntry taskEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.SCHEDULE_TASK, "/" + taskId, null);
      AssetEntry root = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                       AssetEntry.Type.SCHEDULE_TASK_FOLDER, "/", null);
      AssetEntry target = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.SCHEDULE_TASK_FOLDER, "Target", null);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> folderService.moveTask(target, root, taskEntry, caller));
      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> folderService.changeTaskFolder(taskEntry, target, caller));

      verify(storage, never()).putXMLSerializable(anyString(), any());
      verify(storage, never()).remove(anyString());
      assertNotEquals("Target", scheduleManager.getScheduleTask(taskId, ORG_A).getPath(),
                      "the cached task's path is not changed");
   }

   /**
    * Stores a task the way a site admin (exempt from the checks) or a save before #77530 stored
    * it. The owner gets the permissions on it in its organization. The sheet isn't checked
    * (Bug #78129), as before that check: the owner can't read a sheet of another organization.
    */
   private String storeTask(String name, String owner, String sheet) throws Exception {
      ThreadContext.setContextPrincipal(builder.principalOf(owner, ORG_A));
      ScheduleTask task = newTask(name, owner, sheet);
      AssetRepository.IGNORE_PERM.set(true);

      try {
         scheduleManager.setScheduleTask(task.getTaskId(), task,
                                         builder.principalOf("ssrkAdmin", ORG_A));
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A), "test setup");
      return task.getTaskId();
   }

   private void importOverwriting(String name, String owner, String sheet) throws Exception {
      ScheduleTask imported = newTask(name, owner, sheet);
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      writer.write("<ScheduleTask>");
      imported.writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)),
         config, true, false);
   }

   /**
    * Asserts the task is persisted, read past the task map cache (a refused save may have
    * changed only the cached instance).
    */
   private ScheduleTask assertPersisted(String taskId) {
      ReflectionTestUtils.invokeMethod(scheduleManager.getOrgTaskMap(ORG_A), "clearCache");
      ScheduleTask stored = scheduleManager.getScheduleTask(taskId, ORG_A);
      assertNotNull(stored, "the stored task must not be lost");
      return stored;
   }

   private ScheduleTask newTask(String name, String owner, String sheet) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheet);
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(IdentityID.getIdentityIDFromKey(builder.principalOf(owner, ORG_A).getName()));
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   // the rename looks up the dependencies of the task (ScheduleService.getDependencyInfo)
   @Configuration
   static class RenameConfiguration {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }
}
