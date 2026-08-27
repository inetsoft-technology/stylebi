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
package inetsoft.web.admin.ai.cluster;

import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.cluster.ClusterEnabledModel;
import inetsoft.web.admin.cluster.ClusterService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 2 (unrecognized-server hard refusal), section 5 (no-op detection, the
 * {@code cluster.pause.enabled} whole-plan gate, the hash), and section 11 (duplicate vs.
 * contradictory same-server entries).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ClusterChangePlanServiceTest {
   @Mock private ClusterService clusterService;
   @Mock private ServerClusterClient client;
   private ClusterChangePlanService service;

   @BeforeEach void setUp() {
      service = new ClusterChangePlanService(clusterService, client);
      lenient().when(clusterService.getClusterEnabled())
         .thenReturn(ClusterEnabledModel.builder().enabled(true).pauseEnabled(true).build());
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ClusterChangePlanRequest req = request("  ", List.of(pause("s1")));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req))
                    .getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of())));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      ClusterChangeRequest change = pause("s1");
      change.setVerb("rename");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change))));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnBlankServer() {
      ClusterChangeRequest change = pause("  ");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change))));
      assertTrue(ex.getMessage().contains("server"));
   }

   // -------------------------------------------------------------------------
   // unrecognized server -- section 2's own decision: a hard refusal, not downgrade-and-proceed
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnUnrecognizedServer() {
      stubConfigured("s1");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(pause("typo-server")))));
      assertTrue(ex.getMessage().contains("typo-server"));
   }

   // -------------------------------------------------------------------------
   // duplicate vs. contradictory entries (section 11)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnDuplicateSameServerSameVerb() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(pause("s1"), pause("s1")))));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   @Test void resolveThrowsOnContradictorySameServerOppositeVerbs() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(pause("s1"), resume("s1")))));
      assertTrue(ex.getMessage().contains("contradictory"));
   }

   @Test void resolveAllowsSameVerbOnDifferentServers() {
      stubConfigured("s1", "s2");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      stubStatus("s2", ServerClusterStatus.Status.OK, false);
      ResolvedPlan plan = service.resolve(request("task", List.of(pause("s1"), pause("s2"))));
      assertEquals(2, plan.changes().size());
   }

   // -------------------------------------------------------------------------
   // cluster.pause.enabled -- checked once per plan, for either verb (item 10)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsWhenPauseDisabledForPauseVerb() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      when(clusterService.getClusterEnabled())
         .thenReturn(ClusterEnabledModel.builder().enabled(true).pauseEnabled(false).build());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(pause("s1")))));
      assertTrue(ex.getMessage().contains("cluster.pause.enabled"));
   }

   @Test void resolveThrowsWhenPauseDisabledForResumeVerbToo() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.PAUSED, true);
      when(clusterService.getClusterEnabled())
         .thenReturn(ClusterEnabledModel.builder().enabled(true).pauseEnabled(false).build());
      // Item 10's sharper finding: the EM UI hides BOTH buttons when the property is off, so resume
      // is refused too, not just pause.
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(resume("s1")))));
   }

   // -------------------------------------------------------------------------
   // no-op detection (item 4) -- still produces a PlanChange, never silently dropped
   // -------------------------------------------------------------------------

   @Test void resolvePauseAlreadyPausedIsNoOpButStillProducesAPlanChange() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, true);
      ResolvedPlan plan = service.resolve(request("task", List.of(pause("s1"))));
      PlanChange change = plan.changes().get(0);
      assertEquals("Paused", change.currentValue());
      assertEquals("Paused", change.proposedValue());
      assertTrue(change.description().contains("no-op"));
   }

   @Test void resolveResumeAlreadyRunningIsNoOpButStillProducesAPlanChange() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      ResolvedPlan plan = service.resolve(request("task", List.of(resume("s1"))));
      PlanChange change = plan.changes().get(0);
      assertEquals("Running", change.currentValue());
      assertEquals("Running", change.proposedValue());
      assertTrue(change.description().contains("no-op"));
   }

   @Test void resolvePauseOnRunningServerProposesPaused() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      ResolvedPlan plan = service.resolve(request("task", List.of(pause("s1"))));
      PlanChange change = plan.changes().get(0);
      assertEquals("Running", change.currentValue());
      assertEquals("Paused", change.proposedValue());
      assertFalse(change.description().contains("no-op"));
   }

   @Test void resolveResumeOnPausedReachableServerProposesRunning() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, true);
      ResolvedPlan plan = service.resolve(request("task", List.of(resume("s1"))));
      assertEquals("Running", plan.changes().get(0).proposedValue());
   }

   @Test void resolveResumeOnPausedUnreachableServerProposesStopped() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.DOWN, true);
      ResolvedPlan plan = service.resolve(request("task", List.of(resume("s1"))));
      assertEquals("Stopped", plan.changes().get(0).proposedValue());
   }

   // -------------------------------------------------------------------------
   // risk/scope axes -- hardcoded, per 03-reconcile.md's precision line (never AdminRiskClassifier
   // .classify())
   // -------------------------------------------------------------------------

   @Test void everyChangeIsRiskHighScopeValueAndOrgIdNull() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      PlanChange change = service.resolve(request("task", List.of(pause("s1")))).changes().get(0);
      assertEquals(AdminChangeRecord.RISK_HIGH, change.risk());
      assertEquals(AdminChangeRecord.SCOPE_VALUE, change.snapshotScope());
      assertNull(change.orgId());
      assertTrue(change.recognized());
   }

   @Test void resolvedPlanNeverRequiresStorageBackupButAlwaysRequiresAgentSignoff() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      ResolvedPlan plan = service.resolve(request("task", List.of(pause("s1"))));
      assertFalse(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
   }

   // -------------------------------------------------------------------------
   // hash (section 5)
   // -------------------------------------------------------------------------

   @Test void hashChangesWhenCurrentStatusReadChangesBetweenTwoResolveCalls() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      String hash1 = service.resolve(request("task", List.of(pause("s1")))).planHash();

      stubStatus("s1", ServerClusterStatus.Status.OK, true);
      String hash2 = service.resolve(request("task", List.of(pause("s1")))).planHash();

      assertNotEquals(hash1, hash2);
   }

   @Test void hashIsStableAcrossIdenticalResolves() {
      stubConfigured("s1");
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      String hash1 = service.resolve(request("task", List.of(pause("s1")))).planHash();
      String hash2 = service.resolve(request("task", List.of(pause("s1")))).planHash();
      assertEquals(hash1, hash2);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubConfigured(String... servers) {
      lenient().when(client.getConfiguredServers()).thenReturn(Set.of(servers));
   }

   private void stubStatus(String server, ServerClusterStatus.Status status, boolean paused) {
      ServerClusterStatus s = new ServerClusterStatus();
      s.setStatus(status);
      s.setPaused(paused);
      lenient().when(client.getStatus(server)).thenReturn(s);
   }

   private static ClusterChangeRequest pause(String server) {
      ClusterChangeRequest c = new ClusterChangeRequest();
      c.setVerb(ClusterChangeRequest.VERB_PAUSE);
      c.setServer(server);
      return c;
   }

   private static ClusterChangeRequest resume(String server) {
      ClusterChangeRequest c = new ClusterChangeRequest();
      c.setVerb(ClusterChangeRequest.VERB_RESUME);
      c.setServer(server);
      return c;
   }

   private static ClusterChangePlanRequest request(String task, List<ClusterChangeRequest> changes) {
      ClusterChangePlanRequest req = new ClusterChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }
}
