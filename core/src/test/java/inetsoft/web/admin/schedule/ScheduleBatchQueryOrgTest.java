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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.BatchActionModel;
import inetsoft.web.admin.schedule.model.ScheduleActionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
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
 * Bug #77549: the query entry of a batch action comes from the client (the EM and portal task
 * editor, {@code POST /api/em/schedule/task/save} and {@code /api/portal/schedule/save}, both
 * {@code ScheduleTaskService.saveTask}) with its organization and properties as sent. A query in
 * another organization than the task's is refused when the task is saved, unless the caller is a
 * site admin, and the auto-save properties (openAutoSaved/autoFileName/isRecycle), which make
 * {@code AbstractAssetEngine.getSheet} read an auto-saved file without a permission check, are
 * never stored. Uses the real SecurityEngine / FileAuthenticationProvider (SecurityTestDataBuilder),
 * the real ScheduleManager bean and the real ScheduleService model conversion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  ScheduleBatchQueryOrgTest.RenameConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleBatchQueryOrgTest {
   private static final String ORG_A = "sbqorga";
   private static final String ORG_B = "sbqorgb";
   private static final String SCHEDULE_ROLE = "sbqSchedRole";
   private static final String SITE_ADMIN_ROLE = "sbqSiteAdmin";
   private static final String VICTIM_FILE = "4^WORKSHEET^sbqVictim~;~" + ORG_A + "^Private^~";

   private SecurityTestDataBuilder builder;
   private ScheduleService scheduleService;
   private final List<String> taskNames = new ArrayList<>();

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("sbqA", ORG_A)
         .addOrg("sbqB", ORG_B)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG_A)
         .addUser("sbqUser", ORG_A, "password")
         .addUser("sbqAdmin", ORG_A, "password")
         .addUserToRole("sbqUser", SCHEDULE_ROLE, ORG_A)
         .addUserToRole("sbqAdmin", SITE_ADMIN_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SITE_ADMIN_ROLE, Identity.ROLE, ORG_A)
         // Bug #78129, a saved query must be readable by the saver and the run principal
         .grantPermission(ResourceType.ASSET, "Sales", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleTaskSiteAdminNameOwnerTest
      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);

      scheduleService = new ScheduleService(null, scheduleManager, null,
                                            new ScheduleConditionService(), null,
                                            mock(DeployService.class), null, null, null, null,
                                            null, null, null);
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

   @Test
   void foreignOrgQuery_isRefusedForNonSiteAdmin() {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup");
      ScheduleTask task = newTask("SbqForeign", "1^2^__NULL__^Secret^" + ORG_B);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A), "not stored");
      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.checkBatchQueryOrganization(task.getTaskId(), task, caller));
   }

   // a foreign-org table entry is refused too, the same as the enterprise public API
   @Test
   void foreignOrgTableQuery_isRefusedForNonSiteAdmin() {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      ScheduleTask task = newTask("SbqForeignTable", "1^" + AssetEntry.Type.TABLE.id() +
         "^__NULL__^Secret/Query1^" + ORG_B);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
   }

   @Test
   void sameOrgQuery_isAllowed_ignoringCase() throws Exception {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      ScheduleTask task = newTask("SbqSameOrg", "1^2^__NULL__^Sales^" + ORG_A);
      // an org id may be mixed case, the org of an imported entry is lower case
      ScheduleTask otherCase = newTask("SbqSameOrgCase", "1^2^__NULL__^Sales^" +
         ORG_A.toUpperCase());
      assertEquals(ORG_A.toUpperCase(),
                   ((BatchAction) otherCase.getAction(0)).getQueryEntry().getOrgID(), "test setup");

      assertDoesNotThrow(
         () -> scheduleManager.checkBatchQueryOrganization(otherCase.getTaskId(), otherCase,
                                                           caller));
      scheduleManager.setScheduleTask(task.getName(), task, caller);
      scheduleManager.setScheduleTask(otherCase.getName(), otherCase, caller);

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A));
      assertNotNull(scheduleManager.getScheduleTask(otherCase.getTaskId(), ORG_A));
   }

   @Test
   void foreignOrgQuery_isAllowedForSiteAdmin() throws Exception {
      SRPrincipal caller = builder.principalOf("sbqAdmin", ORG_A);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup");
      ScheduleTask task = newTask("SbqAdminForeign", "1^2^__NULL__^Secret^" + ORG_B);

      scheduleManager.setScheduleTask(task.getName(), task, caller);

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A));
   }

   // a trusted (internal) save is not checked
   @Test
   void foreignOrgQuery_trustedSave_isNotChecked() throws Exception {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      ScheduleTask task = newTask("SbqTrusted", "1^2^__NULL__^Secret^" + ORG_B);

      assertDoesNotThrow(
         () -> scheduleManager.setScheduleTask(task.getName(), task, null, true, caller));
   }

   // the EM and portal save: the client JSON is converted by ScheduleService.getActionFromModel
   // (ScheduleTaskService.applyContent) and saved by ScheduleService.saveTask
   @Test
   void editorSave_foreignOrgQuery_isRefused() throws Exception {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      BatchAction action = actionFromClient("1^2^__NULL__^Secret^" + ORG_B);
      assertEquals(ORG_B, action.getQueryEntry().getOrgID(), "the org is kept as sent");
      ScheduleTask task = newTask("SbqEditorForeign", action);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleService.saveTask(task.getName(), task, caller));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A), "not stored");
   }

   @Test
   void editorSave_autoSaveProperties_areNotStored() throws Exception {
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      BatchAction action = actionFromClient("1^2^__NULL__^Sales^" + ORG_A);
      ScheduleTask task = newTask("SbqEditorAutoSave", action);

      scheduleService.saveTask(task.getName(), task, caller);

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG_A);
      assertNotNull(stored);
      AssetEntry query = ((BatchAction) stored.getAction(0)).getQueryEntry();
      assertAutoSavePropertiesRemoved(query);
      assertEquals("kept", query.getProperty("other"), "other properties are kept");

      // and they are not written to the stored task
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      ((BatchAction) stored.getAction(0)).writeXML(writer);
      writer.flush();
      assertFalse(xml.toString().contains("autoFileName"), xml.toString());
      assertFalse(xml.toString().contains("openAutoSaved"), xml.toString());
   }

   // objection 2: an overwriting deploy import removes the stored task before it saves the
   // imported one, the refusal must come before the removal
   @Test
   void overwritingImport_refusedQuery_keepsTheStoredTask() throws Exception {
      SRPrincipal admin = builder.principalOf("sbqAdmin", ORG_A);
      // the import runs in the importer's organization, the owner gets the delete permission
      // on the stored task when it's saved there
      ThreadContext.setContextPrincipal(builder.principalOf("sbqUser", ORG_A));
      ScheduleTask existing = newTask("SbqImported", "1^2^__NULL__^Sales^" + ORG_A);
      existing.setOwner(IdentityID.getIdentityIDFromKey(
         builder.principalOf("sbqUser", ORG_A).getName()));
      scheduleManager.setScheduleTask(existing.getTaskId(), existing, admin);
      String taskId = existing.getTaskId();
      assertNotNull(scheduleManager.getScheduleTask(taskId, ORG_A), "test setup");

      // the same task with a query in another organization, parsed without the site admin
      // org rewrite (isSiteAdmin false, no restricted importer), saved as its owner
      ScheduleTask imported = newTask("SbqImported", "1^2^__NULL__^Secret^" + ORG_B);
      imported.setOwner(existing.getOwner());
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      writer.write("<ScheduleTask>");
      imported.writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      assertNotNull(scheduleManager.getScheduleTask(taskId), "test setup: the import finds it");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> new ScheduleTaskAsset().parseContent(
            new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)),
            config, true, false));

      ScheduleTask stored = scheduleManager.getScheduleTask(taskId, ORG_A);
      assertNotNull(stored, "the stored task must not be lost");
      assertEquals(ORG_A, ((BatchAction) stored.getAction(0)).getQueryEntry().getOrgID());
   }

   // review r1 finding 1: a rename (an editor save with a new name or owner) removes the stored
   // task before it saves the renamed one, the refusal must come before the removal
   @Test
   void rename_refusedQuery_keepsTheStoredTask() throws Exception {
      String taskId = storeForeignQueryTask("SbqRename");
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
      ScheduleService renameService = new ScheduleService(
         null, scheduleManager, null, new ScheduleConditionService(), null,
         mock(DeployService.class), null, null, null, mock(ScheduleTaskFolderService.class),
         null, null, mock(RenameTransformHandler.class));
      taskNames.add("SbqRenamed");

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> renameService.updateTaskName(taskId, caller.getName() + ":SbqRenamed", null,
                                            caller));

      assertNotNull(scheduleManager.getScheduleTask(taskId, ORG_A),
                    "the stored task must not be lost");
   }

   // review r1 finding 2: a refused move doesn't change the folders or the stored task's path
   @Test
   void folderMove_refusedQuery_changesNothing() throws Exception {
      String taskId = storeForeignQueryTask("SbqMove");
      SRPrincipal caller = builder.principalOf("sbqUser", ORG_A);
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
      assertNotEquals("Target", scheduleManager.getScheduleTask(taskId, ORG_A).getPath());
   }

   /**
    * Stores a task owned by sbqUser with a query in another organization, the way a site admin
    * (exempt) or a save before this fix stored it. The owner gets the delete permission on it.
    */
   private String storeForeignQueryTask(String name) throws Exception {
      // the caller works in its organization, the owner gets the permissions there
      ThreadContext.setContextPrincipal(builder.principalOf("sbqUser", ORG_A));
      ScheduleTask task = newTask(name, "1^2^__NULL__^Secret^" + ORG_B);
      task.setOwner(IdentityID.getIdentityIDFromKey(
         builder.principalOf("sbqUser", ORG_A).getName()));
      // Bug #78129, a task saved before the query READ check, its owner can't read the query
      AssetRepository.IGNORE_PERM.set(true);

      try {
         scheduleManager.setScheduleTask(task.getTaskId(), task,
                                         builder.principalOf("sbqAdmin", ORG_A));
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId()), "test setup");
      return task.getTaskId();
   }

   private BatchAction actionFromClient(String identifier) throws Exception {
      // the BatchActionModel the EM/portal editor posts, with client-chosen entry properties
      String json = "{\"actionType\":\"BatchAction\",\"actionClass\":\"BatchActionModel\"," +
         "\"taskName\":\"sbqUser~;~" + ORG_A + ":Child\",\"queryEnabled\":true," +
         "\"queryEntry\":{\"identifier\":\"" + identifier + "\",\"properties\":{" +
         "\"openAutoSaved\":\"true\",\"autoFileName\":\"" + VICTIM_FILE + "\"," +
         "\"isRecycle\":\"true\",\"other\":\"kept\"}}," +
         "\"queryParameters\":[]}";
      ScheduleActionModel model = new ObjectMapper().readValue(json, ScheduleActionModel.class);
      assertInstanceOf(BatchActionModel.class, model);
      assertEquals("true", ((BatchActionModel) model).queryEntry().getProperty("openAutoSaved"),
                   "test setup: the client properties are deserialized");

      return (BatchAction) scheduleService.getActionFromModel(
         model, null, builder.principalOf("sbqUser", ORG_A), "");
   }

   private ScheduleTask newTask(String name, String queryIdentifier) {
      BatchAction action = new BatchAction();
      action.setTaskId("sbqUser~;~" + ORG_A + ":Child");
      action.setQueryEntry(AssetEntry.createAssetEntry(queryIdentifier));
      return newTask(name, action);
   }

   private ScheduleTask newTask(String name, BatchAction action) {
      ScheduleTask task = new ScheduleTask(name);
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   private static void assertAutoSavePropertiesRemoved(AssetEntry entry) {
      assertNull(entry.getProperty("openAutoSaved"));
      assertNull(entry.getProperty("autoFileName"));
      assertNull(entry.getProperty("isRecycle"));
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
