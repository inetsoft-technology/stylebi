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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #77530: {@code ScheduleManager.setScheduleTask()} is the one place every schedule-task save
 * path converges (the EM/portal task editor via {@code ScheduleTaskService.saveTask()} and the
 * viewer's own "Schedule" dialog via {@code ScheduleDialogService.scheduleVS()}), but neither path
 * checked a {@code ViewsheetAction}'s client-suppliable {@code sheet} identifier against the
 * saving principal before persisting it. Uses the real {@code SecurityEngine} /
 * {@code FileAuthenticationProvider} (SecurityTestDataBuilder) and the real {@code ScheduleManager}
 * bean, the same pattern as {@code ScheduleTaskSiteAdminNameOwnerTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleActionOrgBoundaryTest {
   private static final String ORG_A = "saobOrgA";
   private static final String ORG_B = "saobOrgB";
   private static final String SCHEDULE_ROLE = "saobSchedRole";
   private static final String SITE_ADMIN_ROLE = "saobSiteAdmin";

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
         .addOrg("saobA", ORG_A)
         .addOrg("saobB", ORG_B)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG_A)
         .addUser("saobUser", ORG_A, "password")
         .addUser("saobAdmin", ORG_A, "password")
         .addUserToRole("saobUser", SCHEDULE_ROLE, ORG_A)
         .addUserToRole("saobAdmin", SITE_ADMIN_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SITE_ADMIN_ROLE, Identity.ROLE, ORG_A)
         // Bug #78129, a saved sheet must be readable by the saver and the run principal
         .grantPermission(ResourceType.REPORT, "Reports/Dashboard", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleTaskSiteAdminNameOwnerTest / ScheduleIdentityRemovedMissingOwnerTest
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
      OrganizationContextHolder.clear();

      for(String orgID : List.of(ORG_A, ORG_B)) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
            scheduleManager.getOrgTaskMap(orgID);
         map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      }

      taskNames.clear();
   }

   // (a) the ViewsheetAction save path refuses a foreign-org identifier for a non-site-admin
   @Test
   void viewsheetAction_foreignOrgSheet_refusedForNonSiteAdmin() {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");
      ScheduleTask task = newTask("SaobForeign", "1^128^__NULL__^Foreign/Dashboard^" + ORG_B);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A), "not stored");
   }

   // regression: an ordinary same-org sheet is unaffected
   @Test
   void viewsheetAction_sameOrgSheet_isAllowedForNonSiteAdmin() throws Exception {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      ScheduleTask task = newTask("SaobSameOrg", "1^128^__NULL__^Reports/Dashboard^" + ORG_A);

      scheduleManager.setScheduleTask(task.getName(), task, caller);

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG_A);
      assertNotNull(stored, "stored");
      ViewsheetAction action = (ViewsheetAction) stored.getAction(0);
      assertEquals("1^128^__NULL__^Reports/Dashboard^" + ORG_A, action.getViewsheet());
   }

   // (c) a site-admin principal is still allowed to reference a foreign-org sheet
   @Test
   void viewsheetAction_foreignOrgSheet_isAllowedForSiteAdmin() throws Exception {
      SRPrincipal caller = builder.principalOf("saobAdmin", ORG_A);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: site admin");
      ScheduleTask task = newTask("SaobAdminForeign", "1^128^__NULL__^Foreign/Dashboard^" + ORG_B);

      scheduleManager.setScheduleTask(task.getName(), task, caller);

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG_A);
      assertNotNull(stored, "stored even though the sheet is in another org");
   }

   // (review round 1, informational note) distinguishes orgID (the org the task is actually
   // being saved into, which respects an org-context switch) from principal.getOrgId() (the
   // principal's static home org, never overridden by a context switch). Calls the private
   // checkActionOrgBoundary helper directly (via reflection) with an orgID that deliberately
   // differs from the principal's home org, so a future change that silently narrows the
   // implementation back to principal.getOrgId() is caught here, independent of the
   // setScheduleTask() call site. (Exercising this through the full setScheduleTask() path with
   // an actual OrganizationContextHolder switch would also need the scheduler-permission check
   // a few lines above this one in setScheduleTask() to recognize the switched-to org, which is
   // unrelated plumbing this bug doesn't touch -- the direct-helper test below is the precise,
   // minimal way to pin the invariant that matters here.)
   @Test
   void checkActionOrgBoundary_usesThePassedInOrgID_notPrincipalsHomeOrg() {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");
      assertEquals(ORG_A, caller.getOrgId(), "test setup: the principal's home org is ORG_A");
      ScheduleTask task = newTask("SaobDirectOrgIdCheck", "1^128^__NULL__^Reports/Dashboard^" + ORG_B);

      // orgID (ORG_B) matches the sheet's org even though it differs from the principal's own
      // home org (ORG_A) -- must be allowed, the same as an org-context-switched save would be.
      assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
         ScheduleManager.class, "checkActionOrgBoundary", task, ORG_B, caller),
         "must check against the passed-in orgID, not principal.getOrgId()");

      // sanity: the same task, same principal, but orgID now matches the principal's home org
      // instead of the sheet's actual org -- must still be refused, proving the assertion above
      // isn't vacuously true (i.e. the method isn't just allowing everything for this principal)
      // ReflectionTestUtils.invokeMethod wraps a checked exception thrown by the reflectively
      // invoked method in UndeclaredThrowableException, the same as
      // ScheduleTaskOwnerOrgReplaceTest's established pattern for this helper
      assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(
         ScheduleManager.class, "checkActionOrgBoundary", task, ORG_A, caller));
   }

   // (external GitHub review, PR #6100, non-blocking item 4 -- requested independently by both
   // our own round-1 review and this external review) the same orgID-vs-principal.getOrgId()
   // distinction as the direct-helper test above, but exercised end to end through the real
   // setScheduleTask() call with an actual OrganizationContextHolder org-context switch, not just
   // a direct call to the private helper. task.setRemovable(false) skips the unrelated
   // scheduler-permission check a few lines above this one in setScheduleTask() -- that check's
   // own permission resolution is independently sensitive to the current org context, which is
   // not part of what this test (or this bug) is about; ScheduleTaskOwnerOrgIdTest,
   // ScheduleManagerCycleInfoReloadTest and ImportTaskCrossOrgTest already use the same
   // setRemovable(false) technique to isolate a test from that unrelated gate.
   @Test
   void viewsheetAction_matchingContextSwitchedOrg_isAllowedEndToEnd() throws Exception {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");
      assertEquals(ORG_A, caller.getOrgId(), "test setup: the principal's home org is ORG_A");

      ScheduleTask task = newTask("SaobContextSwitchE2E", "1^128^__NULL__^Reports/Dashboard^" + ORG_B);
      task.setRemovable(false);

      OrganizationContextHolder.setCurrentOrgId(ORG_B);

      try {
         assertEquals(ORG_B, OrganizationManager.getInstance().getCurrentOrgID(caller),
                      "test setup: acting in ORG_B via a context switch, not the principal's " +
                      "home org (ORG_A)");

         // Bug #78129, the task's new owner is the caller's name in ORG_B, which isn't a user
         // there, so the sheet READ check refuses it as the run would. That check isn't what this
         // test is about, it's skipped the same way as the scheduler permission check above
         AssetRepository.IGNORE_PERM.set(true);
         scheduleManager.setScheduleTask(task.getName(), task, caller);
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
         OrganizationContextHolder.clear();
      }

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG_B);
      assertNotNull(stored, "stored under the context-switched org, matching the " +
                    "ViewsheetAction's org -- principal.getOrgId() alone would disagree here");
   }

   // (external review, blocking finding) with security.exposeDefaultOrgToAll on, a non-site-admin
   // principal from a non-default org may legitimately open/reference a shared default-org
   // viewsheet (ViewsheetEngine.doSwitchToHostOrg, AbstractAssetEngine.checkAssetPermission0's
   // matching runtime bypass; RepletEngine/ViewsheetSandbox/MVManager all honor this) -- the
   // save-time check must not refuse scheduling/picking such a viewsheet.
   @Test
   void viewsheetAction_defaultOrgSheet_isAllowedWhenGloballyVisible() throws Exception {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");
      String defaultOrg = Organization.getDefaultOrganizationID();
      ScheduleTask task = newTask("SaobDefaultOrgVisible",
                                  "1^128^__NULL__^Shared/Dashboard^" + defaultOrg);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(true);

         scheduleManager.setScheduleTask(task.getName(), task, caller);
      }

      ScheduleTask stored = scheduleManager.getScheduleTask(task.getTaskId(), ORG_A);
      assertNotNull(stored, "allowed: a shared default-org viewsheet, globally visible");
   }

   // regression: the exemption above must not reopen the general hole -- without
   // exposeDefaultOrgToAll (isDefaultVSGloballyVisible false), the very same default-org sheet is
   // still refused for a non-site-admin from a different org, the same as any other foreign org
   @Test
   void viewsheetAction_defaultOrgSheet_refusedWhenNotGloballyVisible() {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      String defaultOrg = Organization.getDefaultOrganizationID();
      ScheduleTask task = newTask("SaobDefaultOrgNotVisible",
                                  "1^128^__NULL__^Shared/Dashboard^" + defaultOrg);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A), "not stored");
   }

   // regression: the default-org exemption must not widen to a genuinely different, non-default
   // foreign org, even when isDefaultVSGloballyVisible() is (implausibly) stubbed true -- the
   // exemption only fires when the entry's own org actually is the default org
   @Test
   void viewsheetAction_nonDefaultForeignOrgSheet_stillRefusedWhenGloballyVisibleStubbed() {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      ScheduleTask task = newTask("SaobForeignNotDefault",
                                  "1^128^__NULL__^Foreign/Dashboard^" + ORG_B);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(true);

         assertThrows(inetsoft.sree.security.SecurityException.class,
            () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
      }
   }

   // a BatchAction's query entry is not checked by this fix, it's refused by the batch query
   // check of bug #77549 (see ScheduleBatchQueryOrgTest)
   @Test
   void batchAction_isRefusedByTheBatchQueryCheck() throws Exception {
      SRPrincipal caller = builder.principalOf("saobUser", ORG_A);
      ScheduleTask task = new ScheduleTask("SaobBatch");
      BatchAction batchAction = new BatchAction();
      batchAction.setQueryEntry(
         inetsoft.uql.asset.AssetEntry.createAssetEntry("1^2^__NULL__^ws1^" + ORG_B));
      task.addAction(batchAction);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(task.getName());

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
   }

   private ScheduleTask newTask(String name, String viewsheet) {
      ScheduleTask task = new ScheduleTask(name);
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(viewsheet);
      task.addAction(action);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }
}
