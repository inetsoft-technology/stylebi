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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ThreadContext;
import inetsoft.util.dep.ScheduleTaskAsset;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #78120, #78129: a schedule task save checks that the saving principal and the task's run
 * principal can READ every viewsheet action sheet and batch action query it adds or changes, by
 * the asset READ check the run uses ({@code ScheduleManager.checkActionSheetRead}). Before, only
 * the organization of a sheet was checked, so another user's private sheet of the same
 * organization (#78129), or a host-org user's private sheet under exposeDefaultOrgToAll
 * (#78120), was stored. Uses the real {@code SecurityEngine} / {@code FileAuthenticationProvider}
 * (SecurityTestDataBuilder), the real asset engine and the real {@code ScheduleManager} bean, the
 * same pattern as {@code ScheduleActionOrgBoundaryTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleActionSheetReadTest {
   private static final String ORG = "sasrorg";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String SCHEDULE_ROLE = "sasrSchedRole";
   private static final String ORG_ADMIN_ROLE = "sasrOrgAdmin";
   private static final String SITE_ADMIN_ROLE = "sasrSiteAdmin";
   private static final String GLOBAL_SHEET = "1^128^__NULL__^Reports/Dashboard^" + ORG;
   private static final String PRIVATE_SHEET = "4^128^user0~;~" + ORG + "^vs1^" + ORG;
   private static final String PRIVATE_WS = "4^2^user0~;~" + ORG + "^ws1^" + ORG;
   private static final String HOST_PRIVATE_SHEET =
      "4^128^hostOwner~;~" + HOST_ORG + "^B78120Private^" + HOST_ORG;
   private static final String HOST_SHARED_SHEET =
      "1^128^__NULL__^Shared/Dashboard^" + HOST_ORG;

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
         .addOrg("sasr", ORG)
         .addRole(SCHEDULE_ROLE, ORG)
         .addOrgAdminRole(ORG_ADMIN_ROLE, ORG)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG)
         .addUser("ub1", ORG, "password")
         .addUser("user0", ORG, "password")
         .addUser("oadm", ORG, "password")
         .addUser("sadm", ORG, "password")
         .addUserToRole("ub1", SCHEDULE_ROLE, ORG)
         .addUserToRole("user0", SCHEDULE_ROLE, ORG)
         .addUserToRole("oadm", SCHEDULE_ROLE, ORG)
         .addUserToRole("oadm", ORG_ADMIN_ROLE, ORG)
         .addUserToRole("sadm", SITE_ADMIN_ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SITE_ADMIN_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.REPORT, "Reports/Dashboard", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleActionOrgBoundaryTest
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
      // the save runs in the caller's organization, as a request does
      ThreadContext.setContextPrincipal(principal("ub1"));
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(principal("ub1")), "test setup");
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(principal("oadm")), "test setup");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(principal("sadm")), "test setup");
   }

   @AfterEach
   void tearDown() {
      securityEngineOverrides.setSecurityEnabled(true);
      AssetRepository.IGNORE_PERM.remove();

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

   // (a) the reported case: ub1 saves a task with user0's private sheet
   @Test
   void newTask_otherUsersPrivateSheet_isRefused() {
      ScheduleTask task = viewsheetTask("SasrNewPrivate", "ub1", PRIVATE_SHEET);

      assertRefused(() -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1")));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");
   }

   // (a) the same for a batch action worksheet query
   @Test
   void newTask_otherUsersPrivateWorksheetQuery_isRefused() {
      ScheduleTask task = batchTask("SasrNewPrivateWs", "ub1",
                                    AssetEntry.createAssetEntry(PRIVATE_WS));

      assertRefused(() -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1")));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");
   }

   // (a) a table query reads the worksheet that holds it, as BatchAction.run does
   @Test
   void newTask_tableQueryOfOtherUsersPrivateWorksheet_isRefused() throws Exception {
      AssetEntry table = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.TABLE,
                                        "ws1/T1", new IdentityID("user0", ORG), ORG);
      assertTrue(table.isTable(), "test setup");
      ScheduleTask task = batchTask("SasrNewPrivateTable", "ub1", table);

      assertRefused(() -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1")));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");

      // control: the owner of the worksheet saves the same query
      ThreadContext.setContextPrincipal(principal("user0"));
      ScheduleTask own = batchTask("SasrOwnPrivateTable", "user0", table);
      scheduleManager.setScheduleTask(own.getTaskId(), own, principal("user0"));
      assertNotNull(scheduleManager.getScheduleTask(own.getTaskId(), ORG), "stored");
   }

   // (b) shared task: ub1 changes the sheet of user0's task to user0's private sheet, the run
   // principal user0 can read it, the saver can't
   @Test
   void otherUsersTask_sheetChangedToOwnersPrivateSheet_isRefused() throws Exception {
      ScheduleTask stored = store(viewsheetTask("SasrShared", "user0", GLOBAL_SHEET), "user0");
      ScheduleTask edited = stored.clone();
      edited.setAction(0, viewsheetAction(PRIVATE_SHEET));

      assertRefused(() -> scheduleManager.setScheduleTask(
         edited.getTaskId(), edited, null, principal("ub1"), stored));
      assertEquals(GLOBAL_SHEET, sheetOf(assertPersisted(stored.getTaskId())));
   }

   // (c) refuter's case: ub1 takes ownership of user0's task, keeps execute-as user0 and adds
   // user0's private sheet in the same save, the run principal user0 can read it, the saver can't
   @Test
   void ownershipTake_keepingExecuteAs_withAddedPrivateSheet_isRefused() throws Exception {
      ScheduleTask task = viewsheetTask("SasrTake", "user0", GLOBAL_SHEET);
      task.setIdentity(new User(new IdentityID("user0", ORG)));
      ScheduleTask stored = store(task, "user0");
      ScheduleTask edited = stored.clone();
      edited.setOwner(new IdentityID("ub1", ORG));
      edited.setAction(0, viewsheetAction(PRIVATE_SHEET));

      assertRefused(() -> scheduleManager.setScheduleTask(
         edited.getTaskId(), edited, null, principal("ub1"), stored));
      assertNull(scheduleManager.getScheduleTask(edited.getTaskId(), ORG), "not stored");
   }

   // (c) control: the same ownership take without changing the sheet isn't refused by this
   // check, the sheet isn't added and the run principal user0 can read it
   @Test
   void ownershipTake_keepingExecuteAs_withUnchangedPrivateSheet_isNotRefused() throws Exception {
      ScheduleTask task = viewsheetTask("SasrTakeKeep", "user0", PRIVATE_SHEET);
      task.setIdentity(new User(new IdentityID("user0", ORG)));
      ScheduleTask stored = store(task, "user0");
      ScheduleTask edited = stored.clone();
      edited.setOwner(new IdentityID("ub1", ORG));
      taskNames.add(edited.getName());

      scheduleManager.setScheduleTask(edited.getTaskId(), edited, null, principal("ub1"), stored);

      assertNotNull(scheduleManager.getScheduleTask(edited.getTaskId(), ORG), "stored");
   }

   // (d) #78120: a user of another org than the host org saves a host-org user's private sheet
   // under exposeDefaultOrgToAll, the org boundary lets a host-org sheet through, the READ check
   // refuses it
   @Test
   void exposeDefaultOrgToAll_hostOrgPrivateSheet_isRefused() throws Exception {
      ScheduleTask task = viewsheetTask("SasrHostPrivate", "ub1", HOST_PRIVATE_SHEET);
      ScheduleTask shared = viewsheetTask("SasrHostShared", "ub1", HOST_SHARED_SHEET);

      withDefaultOrgExposed(() -> {
         assertRefused(
            () -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1")));

         // control: a host-org shared sheet is still stored
         scheduleManager.setScheduleTask(shared.getTaskId(), shared, principal("ub1"));
      });

      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");
      assertNotNull(scheduleManager.getScheduleTask(shared.getTaskId(), ORG), "stored");
   }

   // (e) the owner saves their own private sheet
   @Test
   void owner_ownPrivateSheet_isAllowed() throws Exception {
      ThreadContext.setContextPrincipal(principal("user0"));
      ScheduleTask task = viewsheetTask("SasrOwn", "user0", PRIVATE_SHEET);

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("user0"));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
   }

   // (e) an org admin adds user0's private sheet to user0's own task, which runs as user0
   @Test
   void orgAdmin_userPrivateSheetToTheUsersTask_isAllowed() throws Exception {
      ScheduleTask task = viewsheetTask("SasrAdminOwners", "user0", PRIVATE_SHEET);

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("oadm"));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
   }

   // (e) an org admin adds user0's private sheet to ub1's task, which runs as ub1
   @Test
   void orgAdmin_userPrivateSheetToAnotherUsersTask_isRefused() {
      ScheduleTask task = viewsheetTask("SasrAdminOthers", "ub1", PRIVATE_SHEET);

      assertRefused(() -> scheduleManager.setScheduleTask(task.getTaskId(), task, principal("oadm")));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "not stored");
   }

   // (e) a task stored before this check with a sheet its owner can't read can still be
   // changed and renamed, the sheet isn't checked when it's kept
   @Test
   void legacyTask_unreadableSheet_canBeToggledAndRenamed() throws Exception {
      ScheduleTask task = viewsheetTask("SasrLegacy", "ub1", PRIVATE_SHEET);
      AssetRepository.IGNORE_PERM.set(true);

      try {
         scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1"));
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG);
      assertNotNull(stored, "test setup");
      ScheduleTask toggled = stored.clone();
      toggled.setEnabled(false);
      scheduleManager.setScheduleTask(toggled.getTaskId(), toggled, principal("ub1"));
      assertFalse(assertPersisted(task.getTaskId()).isEnabled(), "toggled");

      ScheduleTask renamed = scheduleManager.getScheduleTask(task.getTaskId(), ORG).clone();
      renamed.setName("SasrLegacyRenamed");
      taskNames.add(renamed.getName());
      scheduleManager.replaceScheduleTask(task.getTaskId(), renamed, null, principal("ub1"));
      assertNotNull(assertPersisted(renamed.getTaskId()), "renamed");
   }

   // (e) an owner change checks every sheet against the new run principal
   @Test
   void ownerChange_keptSheetUnreadableByNewOwner_isRefused() throws Exception {
      ScheduleTask stored = store(viewsheetTask("SasrOwnerChange", "user0", PRIVATE_SHEET),
                                  "user0");
      ScheduleTask edited = stored.clone();
      edited.setOwner(new IdentityID("ub1", ORG));

      assertRefused(() -> scheduleManager.setScheduleTask(
         edited.getTaskId(), edited, null, principal("oadm"), stored));
      assertNull(scheduleManager.getScheduleTask(edited.getTaskId(), ORG), "not stored");
   }

   // (e) a site admin saver isn't checked, the run principal still is
   @Test
   void siteAdmin_userPrivateSheetToTheUsersTask_isAllowed() throws Exception {
      ScheduleTask task = viewsheetTask("SasrSiteAdmin", "user0", PRIVATE_SHEET);
      ScheduleTask other = viewsheetTask("SasrSiteAdminOthers", "ub1", PRIVATE_SHEET);

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));
      assertRefused(() -> scheduleManager.setScheduleTask(
         other.getTaskId(), other, principal("sadm")));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
      assertNull(scheduleManager.getScheduleTask(other.getTaskId(), ORG), "not stored");
   }

   // (e) nothing is checked without security
   @Test
   void securityDisabled_isNotChecked() throws Exception {
      ScheduleTask task = viewsheetTask("SasrNoSecurity", "ub1", PRIVATE_SHEET);
      securityEngineOverrides.setSecurityEnabled(false);
      assertFalse(SecurityEngine.getSecurity().isSecurityEnabled(), "test setup");

      scheduleManager.setScheduleTask(task.getTaskId(), task, principal("ub1"));

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "stored");
   }

   // (f) the replace pre-check refuses before the stored task is removed
   @Test
   void replace_addedPrivateSheet_keepsTheStoredTask() throws Exception {
      ScheduleTask stored = store(viewsheetTask("SasrReplace", "ub1", GLOBAL_SHEET), "ub1");
      ScheduleTask edited = stored.clone();
      edited.setName("SasrReplaced");
      edited.setAction(0, viewsheetAction(PRIVATE_SHEET));
      taskNames.add(edited.getName());

      assertRefused(() -> scheduleManager.checkReplaceScheduleTask(
         stored.getTaskId(), edited.getTaskId(), edited, principal("ub1")));
      assertRefused(() -> scheduleManager.replaceScheduleTask(
         stored.getTaskId(), edited, null, principal("ub1")));

      assertEquals(GLOBAL_SHEET, sheetOf(assertPersisted(stored.getTaskId())));
      assertNull(scheduleManager.getScheduleTask(edited.getTaskId(), ORG), "not stored");
   }

   // (h) the EM task import pre-check (ImportTaskController) refuses a plain delegate's import
   // of a task with another user's private sheet, so the task is reported, not saved
   @Test
   void emImportPreCheck_delegateWithOtherUsersPrivateSheet_isRefused() throws Exception {
      ScheduleTask task = viewsheetTask("SasrEmImport", "user0", PRIVATE_SHEET);

      assertRefused(() -> scheduleManager.checkScheduleTaskSave(
         task.getTaskId(), task, principal("ub1")));
      assertDoesNotThrow(() -> scheduleManager.checkScheduleTaskSave(
         task.getTaskId(), task, principal("oadm")), "the owner's admin may import it");
   }

   // (h) a deploy import is saved as the task owner: a task with a sheet its owner reads is
   // imported, one with another user's private sheet is refused
   @Test
   void deployImport_isCheckedAsTheOwner() throws Exception {
      deployImport(viewsheetTask("SasrDeployOwn", "user0", PRIVATE_SHEET));
      assertNotNull(assertPersisted(new IdentityID("user0", ORG).convertToKey() +
                                       ":SasrDeployOwn"), "imported");

      assertRefused(() -> deployImport(viewsheetTask("SasrDeployOther", "ub1", PRIVATE_SHEET)));
      assertNull(scheduleManager.getScheduleTask(
         new IdentityID("ub1", ORG).convertToKey() + ":SasrDeployOther", ORG), "not imported");
   }

   private ScheduleTask store(ScheduleTask task, String saver) throws Exception {
      scheduleManager.setScheduleTask(task.getTaskId(), task, principal(saver));
      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG);
      assertNotNull(stored, "test setup");
      return stored;
   }

   private void deployImport(ScheduleTask task) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      writer.write("<ScheduleTask>");
      task.writeXML(writer);
      writer.write("</ScheduleTask>");
      writer.flush();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      new ScheduleTaskAsset().parseContent(
         new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)),
         config, true, false);
   }

   private ScheduleTask assertPersisted(String taskId) {
      ReflectionTestUtils.invokeMethod(scheduleManager.getOrgTaskMap(ORG), "clearCache");
      ScheduleTask stored = scheduleManager.getScheduleTask(taskId, ORG);
      assertNotNull(stored, "the task must be stored");
      return stored;
   }

   private static void assertRefused(Executable executable) {
      inetsoft.sree.security.SecurityException ex =
         assertThrows(inetsoft.sree.security.SecurityException.class, executable::run);
      assertTrue(ex.getMessage().contains("isn't readable"), ex.getMessage());
   }

   private void withDefaultOrgExposed(Executable executable) throws Exception {
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         SreeEnv.setProperty("security.exposeDefaultOrgToAll", "true");
         SreeEnv.save();

         try {
            assertTrue(SUtil.isDefaultVSGloballyVisible(principal("ub1")), "test setup");
            executable.run();
         }
         finally {
            SreeEnv.remove("security.exposeDefaultOrgToAll");
            SreeEnv.save();
         }
      }
   }

   private static String sheetOf(ScheduleTask task) {
      return ((ViewsheetAction) task.getAction(0)).getViewsheet();
   }

   private SRPrincipal principal(String user) {
      return builder.principalOf(user, ORG);
   }

   private ScheduleTask viewsheetTask(String name, String owner, String sheet) {
      return newTask(name, owner, viewsheetAction(sheet));
   }

   private ScheduleTask batchTask(String name, String owner, AssetEntry query) {
      BatchAction action = new BatchAction();
      action.setQueryEntry(query);
      return newTask(name, owner, action);
   }

   private static ViewsheetAction viewsheetAction(String sheet) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheet);
      return action;
   }

   private ScheduleTask newTask(String name, String owner, ScheduleAction action) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID(owner, ORG));
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   @FunctionalInterface
   private interface Executable {
      void run() throws Exception;
   }
}
