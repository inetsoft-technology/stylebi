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
package inetsoft.web.admin.ai.schedulerstatus;

import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * track-status/01-design.md section 5a: single-entry-only plans, the clustered-deployment
 * whole-plan refusal, and the verb-in-hash decision (see
 * {@link SchedulerStatusChangePlanService#resolve}'s own comment).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SchedulerStatusChangePlanServiceTest {
   @Mock private SchedulerConfigurationService configService;
   private SchedulerStatusChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new SchedulerStatusChangePlanService(configService);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("  ", List.of(start()))));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      assertThrows(IllegalArgumentException.class, () -> service.resolve(request("task", List.of())));
   }

   @Test void resolveThrowsOnMoreThanOneChange() {
      stubNonCluster(false);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(start(), stop()))));
      assertTrue(ex.getMessage().contains("exactly one target"));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      SchedulerStatusChangeRequest change = new SchedulerStatusChangeRequest();
      change.setVerb("shutdown");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change))));
      assertTrue(ex.getMessage().contains("verb"));
   }

   // -------------------------------------------------------------------------
   // clustered deployment -- refused entirely, not just narrowed (section 3a)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsWhenDeploymentIsClustered() {
      when(configService.getStatus())
         .thenReturn(ScheduleStatusModel.builder().cluster(true)
            .externalStorageLocation("/tmp").build());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(start()))));
      assertTrue(ex.getMessage().contains("clustered"));
   }

   // -------------------------------------------------------------------------
   // current/proposed labels per verb
   // -------------------------------------------------------------------------

   @Test void startProposesRunningRegardlessOfCurrentState() {
      stubNonCluster(false);
      PlanChange change = service.resolve(request("task", List.of(start()))).changes().get(0);
      assertEquals("Stopped", change.currentValue());
      assertEquals("Running", change.proposedValue());
   }

   @Test void stopProposesStoppedRegardlessOfCurrentState() {
      stubNonCluster(true);
      PlanChange change = service.resolve(request("task", List.of(stop()))).changes().get(0);
      assertEquals("Running", change.currentValue());
      assertEquals("Stopped", change.proposedValue());
   }

   @Test void restartProposesRunningEvenWhenAlreadyRunning() {
      stubNonCluster(true);
      PlanChange change = service.resolve(request("task", List.of(restart()))).changes().get(0);
      assertEquals("Running", change.currentValue());
      assertEquals("Running", change.proposedValue());
   }

   // -------------------------------------------------------------------------
   // risk/scope axes -- hardcoded, mirroring ClusterChangePlanService's own precedent
   // -------------------------------------------------------------------------

   @Test void everyChangeIsRiskHighScopeValueAndOrgIdNull() {
      stubNonCluster(false);
      PlanChange change = service.resolve(request("task", List.of(start()))).changes().get(0);
      assertEquals(AdminChangeRecord.RISK_HIGH, change.risk());
      assertEquals(AdminChangeRecord.SCOPE_VALUE, change.snapshotScope());
      assertNull(change.orgId());
      assertTrue(change.recognized());
   }

   @Test void resolvedPlanNeverRequiresStorageBackupButAlwaysRequiresAgentSignoff() {
      stubNonCluster(false);
      ResolvedPlan plan = service.resolve(request("task", List.of(start())));
      assertFalse(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
   }

   // -------------------------------------------------------------------------
   // hash -- must distinguish verbs even when current/proposed labels coincide (the plan service's
   // own documented reason for using the verb as PlanChange.property())
   // -------------------------------------------------------------------------

   @Test void hashDiffersBetweenStartAndRestartFromAStoppedScheduler() {
      stubNonCluster(false);
      String startHash = service.resolve(request("task", List.of(start()))).planHash();
      String restartHash = service.resolve(request("task", List.of(restart()))).planHash();
      assertNotEquals(startHash, restartHash);
   }

   @Test void hashIsStableAcrossIdenticalResolves() {
      stubNonCluster(false);
      String hash1 = service.resolve(request("task", List.of(start()))).planHash();
      String hash2 = service.resolve(request("task", List.of(start()))).planHash();
      assertEquals(hash1, hash2);
   }

   @Test void hashIsUnaffectedByDifferentTaskStrings() {
      stubNonCluster(false);
      String hash1 = service.resolve(request("start it up", List.of(start()))).planHash();
      String hash2 = service.resolve(request("please start the scheduler", List.of(start()))).planHash();
      assertEquals(hash1, hash2);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubNonCluster(boolean running) {
      lenient().when(configService.getStatus()).thenReturn(
         ScheduleStatusModel.builder().cluster(false).running(running)
            .externalStorageLocation("/tmp").build());
   }

   private static SchedulerStatusChangeRequest start() {
      SchedulerStatusChangeRequest c = new SchedulerStatusChangeRequest();
      c.setVerb(SchedulerStatusChangeRequest.VERB_START);
      return c;
   }

   private static SchedulerStatusChangeRequest stop() {
      SchedulerStatusChangeRequest c = new SchedulerStatusChangeRequest();
      c.setVerb(SchedulerStatusChangeRequest.VERB_STOP);
      return c;
   }

   private static SchedulerStatusChangeRequest restart() {
      SchedulerStatusChangeRequest c = new SchedulerStatusChangeRequest();
      c.setVerb(SchedulerStatusChangeRequest.VERB_RESTART);
      return c;
   }

   private static SchedulerStatusChangePlanRequest request(
      String task, List<SchedulerStatusChangeRequest> changes)
   {
      SchedulerStatusChangePlanRequest req = new SchedulerStatusChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }
}
