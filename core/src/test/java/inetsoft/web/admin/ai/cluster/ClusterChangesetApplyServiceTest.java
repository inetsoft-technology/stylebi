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

import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.cluster.ClusterEnabledModel;
import inetsoft.web.admin.cluster.ClusterService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 6 (apply, per-server read-back, the {@code "partial"} changeset-status
 * vocabulary -- this area's own structural divergence from every prior area: no undo list, no
 * reverse-order pass, apply continues through per-server failures rather than stopping and
 * unwinding).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ClusterChangesetApplyServiceTest {
   @Mock private ClusterService clusterService;
   @Mock private ServerClusterClient client;
   @Mock private XPrincipal user;

   private ClusterChangePlanService planService;
   private ClusterChangesetApplyService service;

   @BeforeEach void setUp() {
      planService = new ClusterChangePlanService(clusterService, client);
      service = new ClusterChangesetApplyService(planService, clusterService, client);
      lenient().when(clusterService.getClusterEnabled())
         .thenReturn(ClusterEnabledModel.builder().enabled(true).pauseEnabled(true).build());
      lenient().when(client.getConfiguredServers()).thenReturn(Set.of("s1", "s2"));
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      ClusterApplyRequest req = applyRequest("task", "not-the-real-hash", "looks good",
                                             pause("s1"));
      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsOnMissingReviewOutcome() {
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      String hash = planService.resolve(request("task", List.of(pause("s1")))).planHash();
      ClusterApplyRequest req = applyRequest("task", hash, "  ", pause("s1"));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   // -------------------------------------------------------------------------
   // success -- read-back drives "verified", uses a FRESH getStatus call, not the pre-apply snapshot
   // -------------------------------------------------------------------------

   @Test void appliesAPauseAndReportsVerifiedFromFreshReadBack() {
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      String hash = planService.resolve(request("task", List.of(pause("s1")))).planHash();

      // After pauseServers() is called, the NEXT getStatus() read (the read-back) reflects the
      // server as paused -- simulating the real state transition; the plan's own pre-apply snapshot
      // (captured moments earlier inside apply()'s re-resolve) must not be reused for verification.
      doAnswer(inv -> {
         stubStatus("s1", ServerClusterStatus.Status.OK, true);
         return null;
      }).when(clusterService).pauseServers(new String[] { "s1" });

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", pause("s1")), user);

         assertEquals(ClusterChangesetApplyService.STATUS_APPLIED, result.status());
         assertNull(result.backupRef());
         assertEquals(1, result.results().size());
         ClusterApplyOutcome outcome = result.results().get(0);
         assertEquals("s1", outcome.property());
         assertEquals("Running", outcome.before());
         assertEquals("Paused", outcome.after());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, outcome.status());
         assertNull(outcome.error());
         verify(clusterService).pauseServers(new String[] { "s1" });
      }
   }

   // -------------------------------------------------------------------------
   // failure does not stop subsequent entries -- item 4's structural divergence
   // -------------------------------------------------------------------------

   @Test void aSingleEntryFailureDoesNotStopProcessingOfSubsequentEntries() {
      stubStatus("s1", ServerClusterStatus.Status.DOWN, false);
      stubStatus("s2", ServerClusterStatus.Status.OK, false);
      String hash = planService.resolve(
         request("task", List.of(pause("s1"), pause("s2")))).planHash();

      // s1 is DOWN and stays DOWN after the (silently-failing) pause call -- read-back label stays
      // "Stopped", never matching the proposed "Paused". s2 succeeds normally.
      doAnswer(inv -> {
         stubStatus("s2", ServerClusterStatus.Status.OK, true);
         return null;
      }).when(clusterService).pauseServers(new String[] { "s2" });

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(
            applyRequest("task", hash, "looks good", pause("s1"), pause("s2")), user);

         assertEquals(ClusterChangesetApplyService.STATUS_PARTIAL, result.status());
         assertEquals(2, result.results().size());
         assertEquals(AdminChangeRecord.STATUS_FAILED, result.results().get(0).status());
         assertNotNull(result.results().get(0).error());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(1).status());
         // Both were attempted -- s2's pauseServers call happened despite s1 already failing.
         verify(clusterService).pauseServers(new String[] { "s1" });
         verify(clusterService).pauseServers(new String[] { "s2" });
      }
   }

   // -------------------------------------------------------------------------
   // overall status vocabulary -- applied / partial / failed (item 6)
   // -------------------------------------------------------------------------

   @Test void overallStatusIsFailedWhenEveryEntryFails() {
      stubStatus("s1", ServerClusterStatus.Status.DOWN, false);
      String hash = planService.resolve(request("task", List.of(pause("s1")))).planHash();

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", pause("s1")), user);
         assertEquals(ClusterChangesetApplyService.STATUS_FAILED, result.status());
      }
   }

   @Test void overallStatusIsAppliedWhenEveryEntrySucceeds() {
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      stubStatus("s2", ServerClusterStatus.Status.OK, false);
      String hash = planService.resolve(
         request("task", List.of(pause("s1"), pause("s2")))).planHash();

      doAnswer(inv -> { stubStatus("s1", ServerClusterStatus.Status.OK, true); return null; })
         .when(clusterService).pauseServers(new String[] { "s1" });
      doAnswer(inv -> { stubStatus("s2", ServerClusterStatus.Status.OK, true); return null; })
         .when(clusterService).pauseServers(new String[] { "s2" });

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(
            applyRequest("task", hash, "looks good", pause("s1"), pause("s2")), user);
         assertEquals(ClusterChangesetApplyService.STATUS_APPLIED, result.status());
      }
   }

   // -------------------------------------------------------------------------
   // a throw mid-apply is recorded as failed, never stops remaining entries, never rolled back
   // -------------------------------------------------------------------------

   @Test void aThrowDuringApplyIsRecordedAsFailedAndDoesNotAbortRemainingEntries() {
      stubStatus("s1", ServerClusterStatus.Status.OK, false);
      stubStatus("s2", ServerClusterStatus.Status.OK, false);
      String hash = planService.resolve(
         request("task", List.of(pause("s1"), pause("s2")))).planHash();

      doThrow(new RuntimeException("cluster message bus unavailable"))
         .when(clusterService).pauseServers(new String[] { "s1" });
      doAnswer(inv -> { stubStatus("s2", ServerClusterStatus.Status.OK, true); return null; })
         .when(clusterService).pauseServers(new String[] { "s2" });

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(
            applyRequest("task", hash, "looks good", pause("s1"), pause("s2")), user);

         assertEquals(ClusterChangesetApplyService.STATUS_PARTIAL, result.status());
         assertEquals(AdminChangeRecord.STATUS_FAILED, result.results().get(0).status());
         assertTrue(result.results().get(0).error().contains("cluster message bus unavailable"));
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(1).status());
         verify(clusterService).pauseServers(new String[] { "s2" });
      }
   }

   // -------------------------------------------------------------------------
   // audit (section 8) -- OBJECT_TYPE_CLUSTER, snapshotScope=value, backupRef=null, every outcome
   // -------------------------------------------------------------------------

   @Test void writesAnAuditRecordWithClusterObjectTypeAndNullBackupRefForEveryOutcome() {
      stubStatus("s1", ServerClusterStatus.Status.DOWN, false);
      String hash = planService.resolve(request("task", List.of(pause("s1")))).planHash();
      Audit auditInstance = mock(Audit.class);

      // Tool.getHost() reads SreeEnv, which throws ShutdownException with no Spring context
      // available (the same "harmless log noise" every prior area's own apply-service test hits) --
      // stubbed here too, specifically so the audit write actually reaches Audit.getInstance()
      // .auditAdminChange(...) and this test can assert on the record's real field values, rather
      // than only proving the write was attempted the way every prior area's test settles for.
      try(MockedStatic<Audit> audit = mockStatic(Audit.class);
          MockedStatic<Tool> tool = mockStatic(Tool.class, CALLS_REAL_METHODS))
      {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         tool.when(Tool::getHost).thenReturn("test-host");
         service.apply(applyRequest("task", hash, "looks good", pause("s1")), user);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      AdminChangeRecord record = captor.getValue();
      assertEquals(ActionRecord.OBJECT_TYPE_CLUSTER, record.getObjectType());
      assertEquals(AdminChangeRecord.SCOPE_VALUE, record.getSnapshotScope());
      assertEquals(AdminChangeRecord.RISK_HIGH, record.getRiskLevel());
      assertNull(record.getBackupRef());
      assertEquals(AdminChangeRecord.ACTION_APPLY, record.getAction());
      assertEquals(AdminChangeRecord.STATUS_FAILED, record.getStatus());
      assertNull(record.getOrganizationId());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
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

   private static ClusterChangePlanRequest request(String task, List<ClusterChangeRequest> changes) {
      ClusterChangePlanRequest req = new ClusterChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static ClusterApplyRequest applyRequest(String task, String planHash, String reviewOutcome,
                                                    ClusterChangeRequest... changes)
   {
      ClusterApplyRequest req = new ClusterApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(planHash);
      req.setReviewOutcome(reviewOutcome);
      return req;
   }
}
