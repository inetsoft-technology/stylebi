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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77284: delete and rename acted on a raw client task id. {@code removeScheduleTask}
 * resolved the task in the organization named by the id prefix, so an org A caller could
 * unschedule org B's quartz job (JobKeys are global), and {@code isSiteAdminOtherOrg} skipped the
 * delete permission check based on the task owner only, for any caller.
 *
 * Uses the real SecurityEngine / DefaultCheckPermissionStrategy (SecurityTestDataBuilder) and the
 * real ScheduleManager bean; only SUtil.isMultiTenant() and the injected mock ScheduleClient are
 * test doubles. User principals are marked {@code __internal__=true}, as
 * SecurityEngine.authenticate() marks every login principal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleRemoveCrossOrgTest {
   private static final String ORG_A = "rmxorga";
   private static final String ORG_B = "rmxorgb";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   // a site admin of org A; "sadm~;~rmxorgb" is not a user, it is the owner of a task the site
   // admin created while switched into org B (Bug #70087)
   private static final IdentityID SADM_IN_B = new IdentityID("sadm", ORG_B);
   private static final String VICTIM_JOB = BOB.convertToKey() + ":Nightly";
   private static final String SADM_JOB = SADM_IN_B.convertToKey() + ":Job2";
   private static final String OWN_JOB = ALICE.convertToKey() + ":Daily";
   private static final String LEGACY_JOB = "Nightly";

   private static SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   private MockedStatic<SUtil> sutilStatic;
   private ScheduleClient scheduleClient;
   private ScheduleService service;
   private SRPrincipal alice;   // org admin of org A
   private SRPrincipal carol;   // plain user of org A
   private SRPrincipal sadm;    // site admin (org A)
   private SRPrincipal dave;    // org admin of org B
   private SRPrincipal erin;    // plain user of org B

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("rmxOrgA", ORG_A)
         .addOrg("rmxOrgB", ORG_B)
         .addOrgAdminRole("rmxOrgAdminA", ORG_A)
         .addOrgAdminRole("rmxOrgAdminB", ORG_B)
         .addSysAdminRole("rmxSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUser("dave", ORG_B, "password")
         .addUser("erin", ORG_B, "password")
         .addUserToRole("alice", "rmxOrgAdminA", ORG_A)
         .addUserToRole("sadm", "rmxSiteAdmin", ORG_A)
         .addUserToRole("dave", "rmxOrgAdminB", ORG_B)
         // the owner grant setScheduleTask() stores for a saved task
         .grantPermission(ResourceType.SCHEDULE_TASK, VICTIM_JOB, ResourceAction.DELETE,
                          "bob", Identity.USER, ORG_B);
      builder.setup();
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      alice = loginPrincipalOf("alice", ORG_A);
      carol = loginPrincipalOf("carol", ORG_A);
      sadm = loginPrincipalOf("sadm", ORG_A);
      dave = loginPrincipalOf("dave", ORG_B);
      erin = loginPrincipalOf("erin", ORG_B);

      // org A: alice's own task
      ScheduleTask own = new ScheduleTask("Daily");
      own.setOwner(ALICE);
      assertEquals(OWN_JOB, own.getTaskId());

      // org B: bob's task and a task created by the site admin while in org B
      ScheduleTask bobs = new ScheduleTask("Nightly");
      bobs.setOwner(BOB);
      ScheduleTask sadms = new ScheduleTask("Job2");
      sadms.setOwner(SADM_IN_B);
      assertEquals(VICTIM_JOB, bobs.getTaskId());
      assertEquals(SADM_JOB, sadms.getTaskId());

      withContext(alice, () -> scheduleManager.save(List.of(own), ORG_A));
      withContext(dave, () -> scheduleManager.save(List.of(bobs, sadms), ORG_B));

      scheduleClient = (ScheduleClient) ReflectionTestUtils.getField(scheduleManager, "scheduleClient");
      reset(scheduleClient);
      when(scheduleClient.isReady()).thenReturn(true);

      ScheduleTaskFolderService folderService = mock(ScheduleTaskFolderService.class);
      service = new ScheduleService(null, scheduleManager, scheduleClient, null, null, null,
                                    null, null, null, folderService, null, null, null);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.values().removeIf(t -> t != null && Set.of("Nightly", "Daily", "Job2", "Renamed")
            .contains(t.getName()));
      }
   }

   // --- cross-org delete / rename is rejected -------------------------------------------------

   @Test
   void orgAdmin_deleteOtherOrgTask_rejected() throws Exception {
      withContextPrincipal(alice);

      assertThrows(Exception.class, () -> deleteTask(VICTIM_JOB, alice));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(scheduleManager.getScheduleTask(VICTIM_JOB, ORG_B), "bob's task survives");
   }

   @Test
   void orgAdmin_removeScheduleTaskWithOtherOrgId_rejected() throws Exception {
      // central check, e.g. through RepletEngine.removeScheduleTask or ScheduleTaskAsset
      withContextPrincipal(alice);

      assertThrows(IOException.class, () -> scheduleManager.removeScheduleTask(VICTIM_JOB, alice));

      verify(scheduleClient, never()).taskRemoved(anyString());
   }

   @Test
   void orgAdmin_renameWithOtherOrgId_isNotFound() throws Exception {
      // Bug #77356: the legacy fallback does not resolve another org's id "bob~;~orgB:Nightly"
      // to org A's legacy "Nightly", so neither org's task is removed
      saveLegacyTaskInOrgA();
      withContextPrincipal(alice);
      reset(scheduleClient);

      assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(
         service, "renameTask", VICTIM_JOB, ALICE.convertToKey() + ":Renamed", ALICE, alice));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(scheduleManager.getScheduleTask(VICTIM_JOB, ORG_B), "bob's task survives");
      assertNotNull(scheduleManager.getScheduleTask(LEGACY_JOB, ORG_A),
                    "org A's legacy task survives");
   }

   @Test
   void orgAdmin_renameWithOwnOrgLegacyId_actsOnlyOnResolvedTask() throws Exception {
      // the legacy fallback resolves an own-org prefixed id to org A's "Nightly"
      saveLegacyTaskInOrgA();
      withContextPrincipal(alice);
      reset(scheduleClient);

      try {
         ReflectionTestUtils.invokeMethod(service, "renameTask",
                                          ALICE.convertToKey() + ":" + LEGACY_JOB,
                                          ALICE.convertToKey() + ":Renamed", ALICE, alice);
      }
      catch(Exception ignore) {
         // saving the renamed task is not what is verified here
      }

      verify(scheduleClient, never()).taskRemoved(VICTIM_JOB);
      verify(scheduleClient).taskRemoved(LEGACY_JOB);
      assertNotNull(scheduleManager.getScheduleTask(VICTIM_JOB, ORG_B), "bob's task survives");
   }

   private void saveLegacyTaskInOrgA() throws Exception {
      withContext(alice, () -> {
         ScheduleTask legacy = new ScheduleTask(LEGACY_JOB, ScheduleTask.Type.INTERNAL_TASK);
         legacy.setOwner(ALICE);
         scheduleManager.save(List.of(legacy), ORG_A);
      });
   }

   @Test
   void orgAdmin_renameOtherOrgTaskWithoutLocalMatch_rejected() throws Exception {
      withContextPrincipal(alice);

      assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(
         service, "renameTask", VICTIM_JOB, ALICE.convertToKey() + ":Renamed", ALICE, alice));

      verify(scheduleClient, never()).taskRemoved(anyString());
   }

   @Test
   void passwordLoginPrincipal_isMarkedInternal() {
      Principal login = login(ALICE);

      assertNotNull(login, "password login");
      assertTrue(SUtil.isInternalUser(login), "a password login principal is __internal__");
   }

   @Test
   void passwordLoginOrgAdmin_removeScheduleTaskWithOtherOrgId_rejected() throws Exception {
      // the principal of a real web login (AuthenticationService -> SecurityEngine.authenticate)
      Principal login = login(ALICE);
      assertNotNull(login, "password login");
      withContextPrincipal(login);

      assertThrows(IOException.class, () -> scheduleManager.removeScheduleTask(VICTIM_JOB, login));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(scheduleManager.getScheduleTask(VICTIM_JOB, ORG_B), "bob's task survives");
   }

   // --- isSiteAdminOtherOrg requires the caller to be the site admin -------------------------

   @Test
   void plainUser_otherOrgSiteAdminTask_rejected() throws Exception {
      withContextPrincipal(carol);

      assertThrows(Exception.class, () -> deleteTask(SADM_JOB, carol));
      assertThrows(IOException.class, () -> scheduleManager.removeScheduleTask(SADM_JOB, carol));

      verify(scheduleClient, never()).taskRemoved(anyString());
   }

   @Test
   void plainUser_siteAdminTaskInOwnOrg_noOwnerOnlyBypass() throws Exception {
      withContextPrincipal(erin);

      assertThrows(IOException.class, () -> scheduleManager.removeScheduleTask(SADM_JOB, erin));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(scheduleManager.getScheduleTask(SADM_JOB, ORG_B));
   }

   // --- legitimate callers keep working -------------------------------------------------------

   @Test
   void siteAdmin_ownTaskCreatedInOtherOrg_removedOnCompletion() throws Exception {
      // Bug #70087: JobCompletionListener removes the task with the owner principal
      Principal ownerPrincipal = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);
      withContextPrincipal(ownerPrincipal);

      scheduleManager.removeScheduleTask(SADM_JOB, ownerPrincipal);

      verify(scheduleClient).taskRemoved(SADM_JOB);
      assertNull(scheduleManager.getScheduleTask(SADM_JOB, ORG_B));
   }

   @Test
   void importOverwrite_siteAdminPhantomOwnerPrincipal_hostOrgContext_allowed() throws Exception {
      // the site admin imports while in the host organization
      withContextPrincipal(sadm);

      importOverwriteRemove(SADM_IN_B, SADM_JOB);
   }

   @Test
   void importOverwrite_siteAdminPhantomOwnerPrincipal_switchedIntoOrgB_allowed()
      throws Exception
   {
      SRPrincipal switched = loginPrincipalOf("sadm", ORG_A);
      switched.setProperty("curr_org_id", ORG_B);
      withContextPrincipal(switched);

      importOverwriteRemove(SADM_IN_B, SADM_JOB);
   }

   @Test
   void importOverwrite_siteAdminPhantomOwnerPrincipal_orgContextB_allowed() throws Exception {
      OrganizationContextHolder.setCurrentOrgId(ORG_B);

      try {
         importOverwriteRemove(SADM_IN_B, SADM_JOB);
      }
      finally {
         OrganizationContextHolder.clear();
      }
   }

   @Test
   void phantomOwnerNameInOtherOrg_rejected() throws Exception {
      // same name as the owner, but not the owner identity: not accepted as the owner
      SRPrincipal other = new SRPrincipal(new IdentityID("sadm", "rmxorgc"));
      other.setIgnoreLogin(true);
      withContextPrincipal(other);

      assertThrows(IOException.class, () -> scheduleManager.removeScheduleTask(SADM_JOB, other));

      verify(scheduleClient, never()).taskRemoved(anyString());
      assertNotNull(scheduleManager.getScheduleTask(SADM_JOB, ORG_B));
   }

   @Test
   void siteAdmin_removeTaskOfOtherOrg_allowed() throws Exception {
      withContextPrincipal(sadm);

      scheduleManager.removeScheduleTask(VICTIM_JOB, sadm);

      verify(scheduleClient).taskRemoved(VICTIM_JOB);
   }

   @Test
   void orgAdmin_deleteSiteAdminCreatedTaskInOwnOrg_allowed() throws Exception {
      withContextPrincipal(dave);

      deleteTask(SADM_JOB, dave);

      verify(scheduleClient).taskRemoved(SADM_JOB);
      assertNull(scheduleManager.getScheduleTask(SADM_JOB, ORG_B));
   }

   @Test
   void owner_deleteOwnTask_allowed() throws Exception {
      withContextPrincipal(alice);

      deleteTask(OWN_JOB, alice);

      verify(scheduleClient).taskRemoved(OWN_JOB);
      assertNull(scheduleManager.getScheduleTask(OWN_JOB, ORG_A));
   }

   @Test
   void noPrincipal_internalCleanup_allowed() throws Exception {
      scheduleManager.removeScheduleTask(VICTIM_JOB, null);

      verify(scheduleClient).taskRemoved(VICTIM_JOB);
   }

   @Test
   void owner_removedOnCompletionWithOwnerPrincipal_allowed() throws Exception {
      // JobCompletionListener delete-if-no-more-run cleanup of an ordinary task
      Principal ownerPrincipal = SUtil.getScheduleTaskOwnerPrincipal(BOB, null, false);
      withContextPrincipal(ownerPrincipal);

      scheduleManager.removeScheduleTask(VICTIM_JOB, ownerPrincipal);

      verify(scheduleClient).taskRemoved(VICTIM_JOB);
      assertNull(scheduleManager.getScheduleTask(VICTIM_JOB, ORG_B));
   }

   @Test
   void executeAsVirtualPrincipal_crossOrgExempt() {
      // JobCompletionListener builds a virtual principal for a group/role execute-as identity;
      // a global role (no org) resolves to the default organization
      Principal role = SUtil.getPrincipal(new Role(new IdentityID("rmxGlobalRole", null)), null,
                                          false);
      Principal group = SUtil.getPrincipal(new Group(new IdentityID("rmxGroup", ORG_A)), null,
                                           false);

      assertEquals("true", ((XPrincipal) role).getProperty("virtual"));
      assertEquals(Boolean.TRUE, isCrossOrgRemoveAllowed(role));
      assertEquals(Boolean.TRUE, isCrossOrgRemoveAllowed(group));
   }

   @Test
   void crossOrgExemption_onlyForSystemVirtualAndSiteAdminCallers() {
      // the system principal (SchedulerMonitoringService) and no principal are exempt from the
      // org check; the remaining delete permission checks are unchanged for them
      Principal system = SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, ORG_A), null, false);

      assertEquals(Boolean.TRUE, isCrossOrgRemoveAllowed(system));
      assertEquals(Boolean.TRUE, isCrossOrgRemoveAllowed(null));
      assertEquals(Boolean.TRUE, isCrossOrgRemoveAllowed(sadm));
      assertEquals(Boolean.FALSE, isCrossOrgRemoveAllowed(alice));
      assertEquals(Boolean.FALSE, isCrossOrgRemoveAllowed(carol));
      assertEquals(Boolean.FALSE, isCrossOrgRemoveAllowed(login(ALICE)));
   }

   private void importOverwriteRemove(IdentityID owner, String taskId) throws Exception {
      // ScheduleTaskAsset.parseContent (overwrite) removes the existing task with a principal
      // built from the stored owner, for a site admin phantom owner it has no roles
      SRPrincipal principal = new SRPrincipal(owner);
      principal.setIgnoreLogin(true);

      scheduleManager.removeScheduleTask(taskId, principal);

      verify(scheduleClient).taskRemoved(taskId);
      assertNull(scheduleManager.getScheduleTask(taskId, owner.getOrgID()));
   }

   private static SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }

   private static Principal login(IdentityID id) {
      // the package-private login used by AuthenticationService.authenticate (web login)
      return ReflectionTestUtils.invokeMethod(SecurityEngine.getSecurity(), "authenticate",
         new ClientInfo(id, "127.0.0.1"), new DefaultTicket(id, "password"));
   }

   private Boolean isCrossOrgRemoveAllowed(Principal principal) {
      return ReflectionTestUtils.invokeMethod(scheduleManager, "isCrossOrgRemoveAllowed",
                                              principal);
   }

   private void deleteTask(String taskName, Principal principal) {
      ReflectionTestUtils.invokeMethod(service, "deleteTask", taskName, principal,
                                       new Vector<ScheduleTask>());
   }

   private static void withContextPrincipal(Principal principal) {
      ThreadContext.setContextPrincipal(principal);
   }

   private static void withContext(Principal principal, ThrowingRunnable runnable) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         runnable.run();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
