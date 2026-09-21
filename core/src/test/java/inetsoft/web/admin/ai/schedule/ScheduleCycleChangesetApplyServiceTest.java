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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.api.schedule.TimeCondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Same mocking shape as {@code ScheduleFolderChangesetApplyServiceTest} (a REAL {@link
 * ScheduleCycleChangePlanService} wired to a mocked {@link AdminScheduleCycleGateway}, {@code
 * Tool}/{@code Audit}/{@code OrganizationManager} statics mocked). Covers apply success for each
 * verb, the plan-hash/task-token gates, and -- the load-bearing proof for decision D7's "delete is
 * fully compensable" claim -- a delete-rollback that fully recreates the cycle with its original
 * conditions after a LATER entry in the same changeset fails.
 *
 * <p>{@code currentAsset} is stubbed as a small mutable holder per cycle name, reflecting the
 * cycle's real conceptual state at each point apply/rollback reads it, rather than a fixed
 * sequence of return values -- robust to exactly how many times each phase (preview, apply's own
 * internal re-resolve, apply, rollback) happens to call it.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleCycleChangesetApplyServiceTest {
   // lenient: the rollback test below registers a name-specific createCycle stub (via argThat)
   // alongside calls for a DIFFERENT name that intentionally match no stub at all -- under
   // strict stubbing that mismatch itself throws (PotentialStubbingProblem), which this service's
   // own apply() then (correctly) treats as an apply failure, masking the scenario under test.
   @Mock(lenient = true) private AdminScheduleCycleGateway cycleGateway;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   private ScheduleCycleChangePlanService planService;
   private ScheduleCycleChangesetApplyService service;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setUp() throws Exception {
      planService = new ScheduleCycleChangePlanService(cycleGateway);
      service = new ScheduleCycleChangesetApplyService(planService, cycleGateway, backupService);

      // writeAudit's own Tool.getHost() call reads SreeEnv.getProperty outside a live Spring
      // context -- mocked (unstubbed = null) the same way ScheduleFolderChangesetApplyServiceTest
      // mocks it, purely so Tool.getHost() falls through to its own local try/catch instead of
      // throwing ShutdownException.
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });

      Audit auditMock = mock(Audit.class);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(auditMock);

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      tool.close();
      auditStatic.close();
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // plan hash / task token gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchWhenHashMissing() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);
      ScheduleCycleApplyRequest req = applyRequest(null, null, createChange("NewCycle"));

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsTaskTokenMismatchWhenTokenMissing() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);
      ResolvedPlan preview = planService.resolve(planRequest(createChange("NewCycle")), user);
      ScheduleCycleApplyRequest req = applyRequest(preview.planHash(), null, createChange("NewCycle"));

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);
      ResolvedPlan preview = planService.resolve(planRequest(createChange("NewCycle")), user);
      ScheduleCycleApplyRequest req =
         applyRequest(preview.planHash(), preview.taskToken(), createChange("NewCycle"));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   // -------------------------------------------------------------------------
   // success, one per verb
   // -------------------------------------------------------------------------

   @Test void appliesACreateAndReportsApplied() throws Exception {
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      when(cycleGateway.cycleExists("NewCycle", user)).thenReturn(false);
      DataCycleManager.DataCycleAsset[] state = { null };
      when(cycleGateway.currentAsset(eq("NewCycle"), eq("host-org"))).thenAnswer(inv -> state[0]);
      doAnswer(inv -> { state[0] = asset("NewCycle"); return null; })
         .when(cycleGateway).createCycle(argThat(s -> "NewCycle".equals(s.name())), eq(user));

      ApplyResult result = apply(createChange("NewCycle"));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(cycleGateway).createCycle(any(), eq(user));
   }

   @Test void appliesAnUpdateAndReportsApplied() throws Exception {
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.currentAsset(eq("Cycle1"), eq("host-org"))).thenReturn(asset("Cycle1"));

      ApplyResult result = apply(updateChange("Cycle1"));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(cycleGateway).updateCycle(eq("Cycle1"), any(), eq(user));
   }

   @Test void appliesADeleteAndReportsApplied() throws Exception {
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of());
      DataCycleManager.DataCycleAsset[] state = { asset("Cycle1") };
      when(cycleGateway.currentAsset(eq("Cycle1"), eq("host-org"))).thenAnswer(inv -> state[0]);
      doAnswer(inv -> { state[0] = null; return null; })
         .when(cycleGateway).deleteCycles(eq(List.of("Cycle1")), eq(user));

      ApplyResult result = apply(deleteChange("Cycle1"));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(cycleGateway).deleteCycles(eq(List.of("Cycle1")), eq(user));
   }

   // -------------------------------------------------------------------------
   // rollback -- the load-bearing D7 proof
   // -------------------------------------------------------------------------

   /**
    * Decision D7's own load-bearing claim: a cycle delete is fully compensable. A two-entry
    * changeset deletes "Cycle1" (succeeds) then creates "BadCycle" (fails verification, since its
    * {@code currentAsset} is never populated) -- the failure must roll back the delete by fully
    * recreating "Cycle1" with its ORIGINAL conditions, not merely an empty placeholder.
    */
   @Test void deleteRollbackFullyRecreatesTheCycleWithOriginalConditions() throws Exception {
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of());
      when(cycleGateway.cycleExists("BadCycle", user)).thenReturn(false);

      DataCycleManager.DataCycleAsset original = asset("Cycle1");
      DataCycleManager.DataCycleAsset[] cycle1State = { original };
      when(cycleGateway.currentAsset(eq("Cycle1"), eq("host-org"))).thenAnswer(inv -> cycle1State[0]);
      doAnswer(inv -> { cycle1State[0] = null; return null; })
         .when(cycleGateway).deleteCycles(eq(List.of("Cycle1")), eq(user));
      doAnswer(inv -> { cycle1State[0] = original; return null; })
         .when(cycleGateway).createCycle(argThat(s -> "Cycle1".equals(s.name())), eq(user));

      // "BadCycle"'s own currentAsset is never stubbed -- stays null forever, so applyCreate's
      // own post-creation verification always reports "not found after create", failing this
      // entry and triggering rollback of the (already-applied) delete above.
      when(cycleGateway.currentAsset(eq("BadCycle"), eq("host-org"))).thenReturn(null);

      ApplyResult result = apply(deleteChange("Cycle1"), createChange("BadCycle"));

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(cycleGateway).createCycle(argThat(s -> "Cycle1".equals(s.name()) &&
         s.conditions().size() == original.getConditions().size()), eq(user));
      assertNotNull(cycle1State[0], "Cycle1 must be recreated after rollback");
   }

   /**
    * Review round 1's Finding 1: {@code createCycle} (called by delete-rollback) grants a FRESH
    * default permission to whoever is running the rollback, and stamps a fresh {@code CycleInfo}
    * -- unless the pre-delete permission/info are captured at apply time and re-asserted after
    * {@code createCycle}, any OTHER user/role/group previously granted access to the cycle (and
    * its original createdBy/created metadata) is silently dropped. This test grants "Cycle1" a
    * non-default permission (READ to "secondUser", distinct from the applying "user") before
    * delete, then forces the same delete-then-rollback shape as the test above, and asserts the
    * EXACT captured permission/info objects are the ones threaded into the gateway's own
    * restoration call -- not a fresh default rebuilt at rollback time.
    */
   @Test void deleteRollbackRestoresTheOriginalPermissionAndCycleInfoNotAFreshDefault()
      throws Exception
   {
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      when(cycleGateway.cycleExists("Cycle1", user)).thenReturn(true);
      when(cycleGateway.dependentMvNames("Cycle1")).thenReturn(List.of());
      when(cycleGateway.cycleExists("BadCycle", user)).thenReturn(false);

      DataCycleManager.DataCycleAsset original = asset("Cycle1");
      DataCycleManager.CycleInfo originalInfo = original.getInfo();
      originalInfo.setCreatedBy("originalCreator");

      Permission originalPermission = new Permission();
      originalPermission.setUserGrantsForOrg(ResourceAction.READ, Set.of("secondUser"), "host-org");
      when(cycleGateway.currentPermission(eq("Cycle1"), eq("host-org")))
         .thenReturn(originalPermission);

      DataCycleManager.DataCycleAsset[] cycle1State = { original };
      when(cycleGateway.currentAsset(eq("Cycle1"), eq("host-org"))).thenAnswer(inv -> cycle1State[0]);
      doAnswer(inv -> { cycle1State[0] = null; return null; })
         .when(cycleGateway).deleteCycles(eq(List.of("Cycle1")), eq(user));
      doAnswer(inv -> { cycle1State[0] = original; return null; })
         .when(cycleGateway).createCycle(argThat(s -> "Cycle1".equals(s.name())), eq(user));
      when(cycleGateway.currentAsset(eq("BadCycle"), eq("host-org"))).thenReturn(null);

      ApplyResult result = apply(deleteChange("Cycle1"), createChange("BadCycle"));

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(cycleGateway).restoreCycleState(eq("Cycle1"), eq("host-org"), same(originalInfo),
                                             same(originalPermission));
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   private ApplyResult apply(ScheduleCycleChangeRequest... changes) throws Exception {
      ResolvedPlan preview = planService.resolve(planRequest(changes), user);
      ScheduleCycleApplyRequest req = applyRequest(preview.planHash(), preview.taskToken(), changes);
      req.setReviewOutcome("approved");
      return service.apply(req, user);
   }

   private static DataCycleManager.DataCycleAsset asset(String name) {
      DataCycleManager.DataCycleAsset asset = new DataCycleManager.DataCycleAsset();
      asset.setName(name);
      asset.setOrgId("host-org");
      asset.setEnabled(true);
      asset.setConditions(List.of(inetsoft.sree.schedule.TimeCondition.at(9, 0, 0)));
      asset.setInfo(new DataCycleManager.CycleInfo(name, "host-org"));
      return asset;
   }

   private static TimeCondition everyDay() {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setTimeZone("UTC");
      return condition;
   }

   private static ScheduleCycleChangeRequest createChange(String name) {
      return new ScheduleCycleChangeRequest("create", null,
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(name, List.of(everyDay())));
   }

   private static ScheduleCycleChangeRequest updateChange(String currentName) {
      return new ScheduleCycleChangeRequest("update", currentName,
         new ScheduleCycleChangeRequest.ScheduleCycleSpec(null, List.of(everyDay())));
   }

   private static ScheduleCycleChangeRequest deleteChange(String name) {
      return new ScheduleCycleChangeRequest("delete", name, null);
   }

   private static ScheduleCycleChangePlanRequest planRequest(ScheduleCycleChangeRequest... changes) {
      ScheduleCycleChangePlanRequest req = new ScheduleCycleChangePlanRequest();
      req.setTask("task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScheduleCycleApplyRequest applyRequest(String planHash, String taskToken,
                                                          ScheduleCycleChangeRequest... changes)
   {
      ScheduleCycleApplyRequest req = new ScheduleCycleApplyRequest();
      req.setTask("task");
      req.setChanges(List.of(changes));
      req.setPlanHash(planHash);
      req.setTaskToken(taskToken);
      return req;
   }
}
