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
package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.dep.XAsset;
import inetsoft.util.dep.XAssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82341: a schedule task save checks that the task owner may export every backup action
 * asset it adds, by the check the action makes when the task runs
 * ({@code ScheduleManager.checkBackupAssetsExportable}). Before, only the saving principal was
 * checked by the callers, so a site admin saved a backup task for a user who can't export the
 * asset, and the task failed on every run. Uses the real {@code SecurityEngine} and the real
 * {@code ScheduleManager} bean, the same pattern as {@code ScheduleActionSheetReadTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleBackupAssetOwnerTest {
   private static final String ORG = "sbaeorg";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String SCHEDULE_ROLE = "sbaeSchedRole";
   private static final String SITE_ADMIN_ROLE = "sbaeSiteAdmin";
   private static final String SHEET_PATH = "Reports/BackupSheet";
   private static final String SHEET = "inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^" +
      SHEET_PATH + "^" + ORG;
   private static final String OTHER_SHEET = "inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^" +
      SHEET_PATH + "2^" + ORG;

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();
   private Principal savedPrincipal;
   private Principal savedContextPrincipal;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("sbae", ORG)
         .addRole(SCHEDULE_ROLE, ORG)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG)
         .addUser("plain", ORG, "password")
         .addUser("exporter", ORG, "password")
         .addUser("sadm", ORG, "password")
         .addUserToRole("plain", SCHEDULE_ROLE, ORG)
         .addUserToRole("exporter", SCHEDULE_ROLE, ORG)
         .addUserToRole("sadm", SITE_ADMIN_ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SITE_ADMIN_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.REPORT, SHEET_PATH, ResourceAction.ADMIN,
                          "exporter", Identity.USER, ORG)
         .grantPermission(ResourceType.REPORT, SHEET_PATH + "2", ResourceAction.ADMIN,
                          "exporter", Identity.USER, ORG);
      builder.setup();

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

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getPrincipal();
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal("sadm"));
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(principal("sadm")), "test setup");
   }

   @AfterEach
   void tearDown() {
      securityEngineOverrides.setSecurityEnabled(true);

      for(String orgID : List.of(ORG, HOST_ORG)) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
            scheduleManager.getOrgTaskMap(orgID);
         map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      }

      taskNames.clear();
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
   }

   // the reported case: a site admin creates a backup task for a user who can't export the asset
   @Test
   void newTask_ownerCannotExport_isRefusedNamingAssetAndOwner() {
      ScheduleTask task = backupTask("SbaeNewPlain", "plain", SHEET);

      inetsoft.sree.security.SecurityException ex = assertRefused(task);
      assertTrue(ex.getMessage().contains(SHEET), ex.getMessage());
      assertTrue(ex.getMessage().contains("plain"), ex.getMessage());
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");
   }

   // control: the owner may export the asset, so the same save by the same admin is stored
   @Test
   void newTask_ownerCanExport_isAllowed() throws Exception {
      ScheduleTask task = backupTask("SbaeNewExporter", "exporter", SHEET);

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
   }

   // the check without a save, which is the one the plugin preview and the EM import make
   @Test
   void checkScheduleTaskSave_ownerCannotExport_isRefused() {
      ScheduleTask plain = backupTask("SbaeCheckPlain", "plain", SHEET);
      ScheduleTask exporter = backupTask("SbaeCheckExporter", "exporter", SHEET);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> scheduleManager.checkScheduleTaskSave(
                      plain.getTaskId(), plain, principal("sadm")));
      assertDoesNotThrow(() -> scheduleManager.checkScheduleTaskSave(
         exporter.getTaskId(), exporter, principal("sadm")));
   }

   // only an added asset is checked: an asset kept from the stored task stays savable, so a
   // task whose owner lost the permission can still be renamed or toggled
   @Test
   void storedAsset_keptWhileOwnerCannotExport_staysSavable() throws Exception {
      securityEngineOverrides.setSecurityEnabled(false);
      ScheduleTask stored = store(backupTask("SbaeStored", "plain", SHEET));
      securityEngineOverrides.setSecurityEnabled(true);

      ScheduleTask toggled = stored.clone();
      toggled.setEnabled(false);
      scheduleManager.setScheduleTask(toggled.getTaskId(), toggled, null, principal("sadm"), stored);

      assertFalse(scheduleManager.getScheduleTask(stored.getTaskId(), ORG).isEnabled(), "toggled");
   }

   // an asset added to a stored task is checked, the kept one is not
   @Test
   void storedTask_addedAssetOwnerCannotExport_isRefused() throws Exception {
      securityEngineOverrides.setSecurityEnabled(false);
      ScheduleTask stored = store(backupTask("SbaeStoredAdd", "plain", SHEET));
      securityEngineOverrides.setSecurityEnabled(true);

      ScheduleTask edited = stored.clone();
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setServerPaths(new ServerPathInfo("/tmp/x"));
      action.setAssets(List.of(XAssetUtil.createXAsset(SHEET), XAssetUtil.createXAsset(OTHER_SHEET)));
      edited.setAction(0, action);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> scheduleManager.setScheduleTask(
                      edited.getTaskId(), edited, null, principal("sadm"), stored));
   }

   // an owner change checks every asset against the new owner
   @Test
   void ownerChange_keptAssetNewOwnerCannotExport_isRefused() throws Exception {
      securityEngineOverrides.setSecurityEnabled(false);
      ScheduleTask stored = store(backupTask("SbaeOwnerChange", "exporter", SHEET));
      securityEngineOverrides.setSecurityEnabled(true);

      ScheduleTask edited = stored.clone();
      edited.setOwner(new IdentityID("plain", ORG));

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> scheduleManager.setScheduleTask(
                      edited.getTaskId(), edited, null, principal("sadm"), stored));
   }

   // the execute-as identity doesn't take part: the action exports as the owner
   @Test
   void executeAsChange_ownerCanExport_isAllowed() throws Exception {
      ScheduleTask stored = store(backupTask("SbaeExecuteAs", "exporter", SHEET));
      ScheduleTask edited = stored.clone();
      edited.setIdentity(new User(new IdentityID("plain", ORG)));

      scheduleManager.setScheduleTask(edited.getTaskId(), edited, null, principal("sadm"), stored);

      assertNotNull(scheduleManager.getScheduleTask(edited.getTaskId(), ORG), "stored");
   }

   // nothing is checked without security
   @Test
   void securityDisabled_isNotChecked() throws Exception {
      ScheduleTask task = backupTask("SbaeNoSecurity", "plain", SHEET);
      securityEngineOverrides.setSecurityEnabled(false);
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "test setup");

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
   }

   private ScheduleTask store(ScheduleTask task) throws Exception {
      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));
      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG);
      assertNotNull(stored, "test setup");
      return stored;
   }

   private inetsoft.sree.security.SecurityException assertRefused(ScheduleTask task) {
      inetsoft.sree.security.SecurityException ex = assertThrows(
         inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm")));
      assertTrue(ex.getMessage().contains("exported"), ex.getMessage());
      return ex;
   }

   private SRPrincipal principal(String user) {
      return builder.principalOf(user, ORG);
   }

   private ScheduleTask backupTask(String name, String owner, String assetIdentifier) {
      XAsset asset = XAssetUtil.createXAsset(assetIdentifier);
      assertNotNull(asset, "test setup");
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setServerPaths(new ServerPathInfo("/tmp/x"));
      action.setAssets(List.of(asset));
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID(owner, ORG));
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }
}
