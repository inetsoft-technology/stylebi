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
package inetsoft.web.admin.ai.mv;

import inetsoft.mv.MVDef;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.MVSupportService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76672 (mv variant): a per-item throw in {@code apply()}'s loop was unconditionally added to
 * {@code unknownStateFailures}, forcing {@code STATUS_ROLLBACK_FAILED} even when the throwing
 * item's own mutating call ({@code createMV}/{@code setDataCycle}) was never invoked -- same shape
 * as #76567 ({@code LicenseChangesetApplyService}), fixed here the same way: an
 * {@code AtomicBoolean mutationEntered}, set only immediately before the owned mutating call(s),
 * gates whether a caught throw is treated as "unknown state" versus a plain failed-and-never-touched
 * entry.
 *
 * <p>{@code AdminMvGateway} is mocked directly; {@code mvGateway.existsInOrg}/{@code createMV}/
 * {@code dispose} are wired to a small in-memory {@code existingMvs} set, and
 * {@code mvGateway.setDataCycle}/{@code getAnalysisResult} to a small in-memory {@code mvCycles}
 * map, so verification/rollback assertions reflect real state transitions, matching
 * {@code LicenseChangesetApplyServiceTest}'s/{@code StoredAssetChangesetApplyServiceTest}'s own
 * in-memory fake chain. {@code getAnalysisResult(id)} always returns a fresh mock per call (matching
 * production, where every call constructs a new handle), each wired to read the fake's live state at
 * the moment {@code getStatus()} is actually invoked (not baked in at construction), and optionally
 * to throw {@code IllegalStateException("The analysis job is not valid")} from a given call index
 * onward -- the same "eviction lands between the plan's own resolve and this entry's turn in the
 * loop" TOCTOU window bug-76672-mv's diagnosis identifies.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class MvChangesetApplyServiceTest {
   private AdminMvGateway mvGateway;
   private AdminBackupService backupService;
   private Principal user;

   private final Set<String> existingMvs = new HashSet<>();
   private final Map<String, String> mvCycles = new HashMap<>();
   private MvChangePlanService planService;
   private MvChangesetApplyService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() throws Exception {
      mvGateway = mock(AdminMvGateway.class);
      backupService = mock(AdminBackupService.class);
      user = mock(Principal.class);

      planService = new MvChangePlanService(mvGateway);
      service = new MvChangesetApplyService(planService, mvGateway, backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");

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

      orgManagerStatic = mockStatic(OrganizationManager.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      lenient().when(orgManager.getCurrentOrgID(any())).thenReturn("host-org");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      tool.close();
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   /** Wires {@code existsInOrg}/{@code createMV}/{@code dispose} to a shared in-memory set so
    * apply/rollback verification reflects real state transitions. */
   private void wireCreateAndDispose() throws Throwable {
      lenient().when(mvGateway.existsInOrg(anyString(), anyString()))
         .thenAnswer(inv -> existingMvs.contains((String) inv.getArgument(0)));
      lenient().when(mvGateway.createMV(anyList(), anyList(), anyBoolean(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            List<String> names = inv.getArgument(0);
            existingMvs.addAll(names);
            return null;
         });
      lenient().doAnswer(inv -> {
         List<String> names = inv.getArgument(0);
         existingMvs.removeAll(names);
         return null;
      }).when(mvGateway).dispose(anyList());
   }

   /** Wires {@code setDataCycle} to mutate the shared {@code mvCycles} map. */
   private void wireSetDataCycle() {
      lenient().doAnswer(inv -> {
         List<String> names = inv.getArgument(0);
         String cycle = inv.getArgument(2);

         for(String name : names) {
            mvCycles.put(name, cycle);
         }

         return null;
      }).when(mvGateway).setDataCycle(anyList(), any(), anyString(), anyString());
   }

   /**
    * A fresh {@code AnalysisResult} mock per call (matching production). If {@code throwFromCall}
    * is positive and {@code callIndex >= throwFromCall}, {@code getStatus()} throws
    * {@code IllegalStateException("The analysis job is not valid")} -- otherwise it reads
    * {@code mvCycles} live, at the moment it is actually invoked, for each of {@code mvNames}.
    */
   private MVSupportService.AnalysisResult analysisResultFor(int callIndex, int throwFromCall,
                                                              String... mvNames)
   {
      MVSupportService.AnalysisResult result = mock(MVSupportService.AnalysisResult.class);

      if(throwFromCall > 0 && callIndex >= throwFromCall) {
         lenient().when(result.getStatus())
            .thenThrow(new IllegalStateException("The analysis job is not valid"));
      }
      else {
         lenient().when(result.getStatus()).thenAnswer(inv -> Arrays.stream(mvNames)
            .map(n -> mvStatus(n, mvCycles.get(n))).collect(Collectors.toList()));
      }

      return result;
   }

   private MVSupportService.MVStatus mvStatus(String name, String cycle) {
      MVSupportService.MVStatus status = mock(MVSupportService.MVStatus.class);
      MVDef def = mock(MVDef.class);
      lenient().when(def.getName()).thenReturn(name);
      lenient().when(def.getCycle()).thenReturn(cycle);
      lenient().when(status.getDefinition()).thenReturn(def);
      return status;
   }

   private static MvChangeRequest createChange(String analysisId, String mvName) {
      MvChangeRequest c = new MvChangeRequest();
      c.setVerb(MvChangeRequest.VERB_CREATE);
      c.setAnalysisId(analysisId);
      c.setMvNames(List.of(mvName));
      c.setNoData(true);
      c.setRunInBackground(true);
      return c;
   }

   private static MvChangeRequest setCycleChange(String analysisId, String mvName, String cycle) {
      MvChangeRequest c = new MvChangeRequest();
      c.setVerb(MvChangeRequest.VERB_SET_CYCLE);
      c.setAnalysisId(analysisId);
      c.setMvNames(List.of(mvName));
      c.setCycle(cycle);
      return c;
   }

   private static MvChangeRequest deleteChange(String mvName) {
      MvChangeRequest c = new MvChangeRequest();
      c.setVerb(MvChangeRequest.VERB_DELETE);
      c.setMvNames(List.of(mvName));
      return c;
   }

   private static MvChangePlanRequest request(MvChangeRequest... changes) {
      MvChangePlanRequest req = new MvChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static MvApplyRequest applyRequest(String task, String hash, String reviewOutcome,
                                              Boolean acknowledgeIrreversibleDelete,
                                              MvChangeRequest... changes)
   {
      MvApplyRequest req = new MvApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(hash);
      req.setTaskToken(TaskAuditToken.issue(hash, task));
      req.setReviewOutcome(reviewOutcome);
      req.setAcknowledgeIrreversibleDelete(acknowledgeIrreversibleDelete);
      return req;
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   // -------------------------------------------------------------------------
   // applyCreate: pre-mutation throw must not force rollback-failed (round 1 fix, unchanged)
   // -------------------------------------------------------------------------

   @Test void createPreMutationThrowDoesNotForceRollbackFailedWhenRollbackIsClean() throws Throwable {
      AtomicInteger a1Calls = new AtomicInteger();
      AtomicInteger a2Calls = new AtomicInteger();
      lenient().when(mvGateway.getAnalysisResult("A1"))
         .thenAnswer(inv -> analysisResultFor(a1Calls.incrementAndGet(), 0, "MV1"));
      // Calls 1-2 (this test's own hash-computing resolve(), then apply()'s own internal resolve())
      // succeed; call 3 (applyCreate's own re-fetch, strictly before mutationEntered is set) throws
      // -- MV2's own createMV must never be reached.
      lenient().when(mvGateway.getAnalysisResult("A2"))
         .thenAnswer(inv -> analysisResultFor(a2Calls.incrementAndGet(), 3, "MV2"));
      wireCreateAndDispose();

      MvChangeRequest c1 = createChange("A1", "MV1");
      MvChangeRequest c2 = createChange("A2", "MV2");
      String hash = planService.resolve(request(c1, c2), user).planHash();
      MvApplyRequest req = applyRequest("task", hash, "looks good", null, c1, c2);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      assertFalse(existingMvs.contains("MV1"));
      assertFalse(existingMvs.contains("MV2"));
      verify(mvGateway, never()).createMV(eq(List.of("MV2")), anyList(), anyBoolean(), anyBoolean(),
                                          any());
   }

   // -------------------------------------------------------------------------
   // applySetCycle: pre-mutation throw (round 2 revised fix -- an explicit getStatus() check as
   // applySetCycle's own first statement, mirroring applyCreate)
   // -------------------------------------------------------------------------

   @Test void setCyclePreMutationThrowDoesNotForceRollbackFailedWhenRollbackIsClean() throws Exception {
      mvCycles.put("MV1", "cycle-old");
      mvCycles.put("MV2", "cycle-old");
      AtomicInteger a1Calls = new AtomicInteger();
      AtomicInteger a2Calls = new AtomicInteger();
      lenient().when(mvGateway.getAnalysisResult("A1"))
         .thenAnswer(inv -> analysisResultFor(a1Calls.incrementAndGet(), 0, "MV1"));
      // Call 3 for A2 is applySetCycle's own new explicit first-statement getStatus() check --
      // strictly before mvGateway.setDataCycle is ever called for MV2.
      lenient().when(mvGateway.getAnalysisResult("A2"))
         .thenAnswer(inv -> analysisResultFor(a2Calls.incrementAndGet(), 3, "MV2"));
      wireSetDataCycle();

      MvChangeRequest c1 = setCycleChange("A1", "MV1", "cycle-new");
      MvChangeRequest c2 = setCycleChange("A2", "MV2", "cycle-new");
      String hash = planService.resolve(request(c1, c2), user).planHash();
      MvApplyRequest req = applyRequest("task", hash, "looks good", null, c1, c2);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      assertEquals("cycle-old", mvCycles.get("MV1"));
      assertEquals("cycle-old", mvCycles.get("MV2"));
      verify(mvGateway, never()).setDataCycle(eq(List.of("MV2")), any(), anyString(), anyString());
   }

   // -------------------------------------------------------------------------
   // correct-behavior guard: a throw AFTER the mutating call has started still forces
   // rollback-failed
   // -------------------------------------------------------------------------

   @Test void createMutationEnteredFailureStillForcesRollbackFailed() throws Throwable {
      AtomicInteger a1Calls = new AtomicInteger();
      AtomicInteger a2Calls = new AtomicInteger();
      lenient().when(mvGateway.getAnalysisResult("A1"))
         .thenAnswer(inv -> analysisResultFor(a1Calls.incrementAndGet(), 0, "MV1"));
      // A2's own analysis freshness check never fails here -- the throw instead comes from
      // createMV itself, strictly AFTER mutationEntered has been set.
      lenient().when(mvGateway.getAnalysisResult("A2"))
         .thenAnswer(inv -> analysisResultFor(a2Calls.incrementAndGet(), 0, "MV2"));
      wireCreateAndDispose();
      doThrow(new IllegalStateException("boom during real create"))
         .when(mvGateway).createMV(eq(List.of("MV2")), anyList(), anyBoolean(), anyBoolean(), any());

      MvChangeRequest c1 = createChange("A1", "MV1");
      MvChangeRequest c2 = createChange("A2", "MV2");
      String hash = planService.resolve(request(c1, c2), user).planHash();
      MvApplyRequest req = applyRequest("task", hash, "looks good", null, c1, c2);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream().anyMatch(f -> "MV2".equals(f.property())));
      // MV1's own rollback (dispose) succeeded cleanly even though the overall status is
      // rollback-failed.
      assertFalse(existingMvs.contains("MV1"));
   }

   // -------------------------------------------------------------------------
   // applyDelete: a throw must ALWAYS still force rollback-failed (fix-round 2 -- unlike
   // create/set_cycle, delete has no pre-mutation freshness re-check to distinguish; dispose() IS
   // the mutation and is documented irreversible, so a throw here must never be silently excluded
   // from unknownStateFailures the way a genuinely pre-mutation throw is for the other two verbs)
   // -------------------------------------------------------------------------

   @Test void deleteThrowStillForcesRollbackFailedEvenThoughRollbackIsClean() throws Throwable {
      AtomicInteger a1Calls = new AtomicInteger();
      lenient().when(mvGateway.getAnalysisResult("A1"))
         .thenAnswer(inv -> analysisResultFor(a1Calls.incrementAndGet(), 0, "MV1"));
      existingMvs.add("MV2");
      wireCreateAndDispose();
      // dispose() throws for MV2 specifically -- MV1's own dispose (both this entry's create and
      // its later rollback) still goes through the general wireCreateAndDispose() stub.
      doThrow(new IllegalStateException("boom during dispose"))
         .when(mvGateway).dispose(eq(List.of("MV2")));

      MvChangeRequest c1 = createChange("A1", "MV1");
      MvChangeRequest c2 = deleteChange("MV2");
      String hash = planService.resolve(request(c1, c2), user).planHash();
      MvApplyRequest req = applyRequest("task", hash, "looks good", true, c1, c2);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream().anyMatch(f -> "MV2".equals(f.property())));
      // MV1's own rollback (dispose of the create) succeeded cleanly even though the overall
      // status is rollback-failed, and MV2 (never undoable -- delete has no inverse) is still there.
      assertFalse(existingMvs.contains("MV1"));
      assertTrue(existingMvs.contains("MV2"));
   }

   // -------------------------------------------------------------------------
   // applyCreate: background must be gated on noData (bug #76720 -- noData:true is documented as
   // the fast, no-background-job path, but runInBackground was passed through unconditionally,
   // racing existsInOrg against createMV0's async remoteCreatePool dispatch in scheduler-less
   // deployments)
   // -------------------------------------------------------------------------

   @Test void createDefaultsPassBackgroundFalseToGateway() throws Throwable {
      assertBackgroundGating(null, null, false);
   }

   @Test void createExplicitNoDataTrueRunInBackgroundTrueStillPassesBackgroundFalse() throws Throwable {
      assertBackgroundGating(true, true, false);
   }

   @Test void createNoDataFalseRunInBackgroundTruePassesBackgroundTrue() throws Throwable {
      assertBackgroundGating(false, true, true);
   }

   @Test void createNoDataFalseRunInBackgroundFalsePassesBackgroundFalse() throws Throwable {
      assertBackgroundGating(false, false, false);
   }

   private void assertBackgroundGating(Boolean noData, Boolean runInBackground,
                                       boolean expectedBackground) throws Throwable
   {
      AtomicInteger a1Calls = new AtomicInteger();
      lenient().when(mvGateway.getAnalysisResult("A1"))
         .thenAnswer(inv -> analysisResultFor(a1Calls.incrementAndGet(), 0, "MV1"));
      wireCreateAndDispose();

      MvChangeRequest c1 = createChange("A1", "MV1");
      c1.setNoData(noData);
      c1.setRunInBackground(runInBackground);
      String hash = planService.resolve(request(c1), user).planHash();
      MvApplyRequest req = applyRequest("task", hash, "looks good", null, c1);

      try(MockedStatic<Audit> audit = mockAudit()) {
         service.apply(req, user);
      }

      ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
      verify(mvGateway).createMV(eq(List.of("MV1")), anyList(), captor.capture(), anyBoolean(),
                                 any());
      assertEquals(expectedBackground, captor.getValue());
   }
}
