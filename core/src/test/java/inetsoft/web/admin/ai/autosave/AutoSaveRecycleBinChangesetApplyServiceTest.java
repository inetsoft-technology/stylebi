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
package inetsoft.web.admin.ai.autosave;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveServiceProxy;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers three round-1-review findings against {@link AutoSaveRecycleBinChangesetApplyService}:
 *
 * <ul>
 *   <li>Finding 1 (HIGH): a non-compensable restore rollback's "destroyed asset has no live
 *       inverse" advisory used to be computed and then silently discarded -- {@code verified}
 *       stayed {@code true} so the only branch that ever consumed {@code advisory}
 *       ({@code if(!verified)}) never ran. Fixed by threading a rollback-advisories map through
 *       {@code rollback}/{@code rollbackOne} and merging it into the final outcome, mirroring
 *       {@code IdentityChangesetApplyService}/{@code ProviderChangesetApplyService}/
 *       {@code LicenseChangesetApplyService}'s own {@code mergeAdvisory} precedent.
 *   <li>Finding 2 (HIGH): {@code applyRestore} used to gate queuing the rollback {@code Undo} on
 *       {@code !autoSaveRecycleBinService.exists(id, user)} alone, which really only tests whether
 *       the BEST-EFFORT draft cleanup ({@code AutoSaveUtils.deleteAutoSaveFile}, which swallows its
 *       own exceptions) succeeded -- not whether the live sheet was actually created. A live sheet
 *       created but never cleaned up from the draft bucket was reported {@code STATUS_FAILED} with
 *       no {@code Undo} queued, orphaning it. Fixed by verifying the live sheet's own existence via
 *       {@code assetRepository.containsEntry} independently of the draft-cleanup signal.
 *   <li>Finding 5 (LOW): {@code mutationEntered} used to be set to {@code true} BEFORE calling
 *       {@code restoreAutoSaveAssets}, so a clean {@code false} return (the primitive's own
 *       internal duplicate check refusing before ANY mutation) was misreported as "unknown state".
 *       Fixed by only setting the flag after a confirmed {@code true} return, while still setting
 *       it if the call itself throws (state genuinely unknown in that case).
 * </ul>
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AutoSaveRecycleBinChangesetApplyServiceTest {
   @Mock private AutoSaveRecycleBinService autoSaveRecycleBinService;
   @Mock private AutoSaveServiceProxy autoSaveServiceProxy;
   @Mock private AssetRepository assetRepository;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;

   private AutoSaveRecycleBinChangePlanService planService;
   private AutoSaveRecycleBinChangesetApplyService service;
   private MockedStatic<AutoSaveUtils> autoSaveUtils;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      planService = new AutoSaveRecycleBinChangePlanService(autoSaveRecycleBinService);
      service = new AutoSaveRecycleBinChangesetApplyService(planService, autoSaveRecycleBinService,
                                                            autoSaveServiceProxy, assetRepository,
                                                            backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
      // Contains the IdentityID.KEY_DELIMITER ("~;~") so getIdentityIDFromKey takes its own
      // fast delimiter-split path instead of falling through to ThreadContext/OrganizationManager,
      // which is not set up in this unit test.
      lenient().when(user.getName()).thenReturn("admin~;~host-org");

      // ResolvedPlan.resolve -> TaskAuditToken.issue -> Tool.encryptPassword needs a Spring
      // context otherwise -- same workaround RecycleBinChangesetApplyServiceTest uses.
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));

      autoSaveUtils = mockStatic(AutoSaveUtils.class, withSettings().lenient());
      autoSaveUtils.when(() -> AutoSaveUtils.getAutoSavedByName(anyString(), anyBoolean()))
         .thenAnswer(inv -> "recycle/" + inv.getArgument(0));
      // A fresh stream per call -- InputStream is single-use and captureBytes is called once per
      // entry, but stubbing a single shared instance would make a second entry's read return EOF.
      autoSaveUtils.when(() -> AutoSaveUtils.getInputStream(anyString(), any()))
         .thenAnswer(inv -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
   }

   @AfterEach
   void tearDown() {
      autoSaveUtils.close();
      tool.close();
   }

   private static AutoSaveRecycleBinEntryProjection worksheetEntry(String id, String path) {
      return new AutoSaveRecycleBinEntryProjection(id, AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET,
         path, AutoSaveRecycleBinEntryProjection.SCOPE_GLOBAL, "admin~;~host-org", null);
   }

   private static AutoSaveRecycleBinChangeRequest restore(String id, Boolean overwrite) {
      AutoSaveRecycleBinChangeRequest r = new AutoSaveRecycleBinChangeRequest();
      r.setVerb(AutoSaveRecycleBinChangeRequest.VERB_RESTORE);
      r.setId(id);
      r.setOverwrite(overwrite);
      return r;
   }

   private static AutoSaveRecycleBinChangeRequest delete(String id) {
      AutoSaveRecycleBinChangeRequest r = new AutoSaveRecycleBinChangeRequest();
      r.setVerb(AutoSaveRecycleBinChangeRequest.VERB_DELETE);
      r.setId(id);
      return r;
   }

   private static AutoSaveRecycleBinChangePlanRequest planRequest(
      String task, List<AutoSaveRecycleBinChangeRequest> changes)
   {
      AutoSaveRecycleBinChangePlanRequest req = new AutoSaveRecycleBinChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static AutoSaveRecycleBinApplyRequest applyRequest(
      String task, String hash, AutoSaveRecycleBinChangeRequest... changes)
   {
      AutoSaveRecycleBinApplyRequest req = new AutoSaveRecycleBinApplyRequest();
      req.setTask(task);
      req.setChanges(Arrays.asList(changes));
      req.setPlanHash(hash);
      return req;
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   private AutoSaveRecycleBinApplyOutcome outcomeFor(AutoSaveRecycleBinApplyResult result, String key) {
      return result.results().stream().filter(o -> o.property().equals(key)).findFirst()
         .orElseThrow(() -> new AssertionError("no outcome for " + key));
   }

   // -------------------------------------------------------------------------
   // Finding 2: a live sheet actually created must be queued for rollback even when the
   // best-effort draft cleanup silently fails.
   // -------------------------------------------------------------------------

   @Test
   void applyRestoreQueuesUndoWhenLiveSheetCreatedEvenIfDraftCleanupFails() throws Exception {
      AutoSaveRecycleBinEntryProjection entry1 = worksheetEntry("id1", "ws1");
      AutoSaveRecycleBinEntryProjection entry2 = worksheetEntry("id2", "ws2");

      when(autoSaveRecycleBinService.requireEntry("id1", user)).thenReturn(entry1);
      when(autoSaveRecycleBinService.requireEntry("id2", user)).thenReturn(entry2);
      when(autoSaveRecycleBinService.wouldCollide(entry1, "ws1", user)).thenReturn(false);
      when(autoSaveServiceProxy.restoreAutoSaveAssets("id1", "ws1", false, user)).thenReturn(true);
      // The live sheet IS created (containsEntry: true at apply-time check, still true just before
      // rollback's removal attempt, then false once rollback's own removeSheet has run).
      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(true, true, false);
      // But the best-effort draft cleanup never took -- AutoSaveUtils.deleteAutoSaveFile swallows
      // its own exception, so the entry is still visible here even though "restored" succeeded.
      when(autoSaveRecycleBinService.exists("id1", user)).thenReturn(true);
      // A second, independent entry fails outright (still present after delete) so the changeset
      // as a whole fails and rollback of entry1 actually runs.
      when(autoSaveRecycleBinService.exists("id2", user)).thenReturn(true);

      List<AutoSaveRecycleBinChangeRequest> changes =
         Arrays.asList(restore("id1", false), delete("id2"));
      String hash = planService.resolve(planRequest("restore ws1, delete ws2", changes), user)
         .planHash();
      AutoSaveRecycleBinApplyRequest req = applyRequest("restore ws1, delete ws2", hash,
         restore("id1", false), delete("id2"));
      req.setAcknowledgeIrreversibleDelete(true);
      req.setReviewOutcome("looks good");

      AutoSaveRecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());

      AutoSaveRecycleBinApplyOutcome outcome1 = outcomeFor(result, "autosave:id1");
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, outcome1.status());
      assertNotNull(outcome1.advisory());
      assertTrue(outcome1.advisory().contains("stale copy"));

      // The key assertion: even though the draft cleanup "failed" (exists() stayed true), the
      // Undo was still queued, and rollback actually removed the newly-created live sheet -- it
      // was NOT left orphaned.
      verify(assetRepository).removeSheet(any(AssetEntry.class), eq(user), eq(true));
   }

   // -------------------------------------------------------------------------
   // Finding 1: a non-compensable restore rollback's advisory must reach the caller, not be
   // silently dropped, and must NOT be misreported as a rollback failure.
   // -------------------------------------------------------------------------

   @Test
   void rollbackOfNonCompensableRestoreSurfacesDestroyedAssetAdvisoryOnTheOutcome() throws Exception {
      AutoSaveRecycleBinEntryProjection entry1 = worksheetEntry("id1", "ws1");
      AutoSaveRecycleBinEntryProjection entry2 = worksheetEntry("id2", "ws2");

      when(autoSaveRecycleBinService.requireEntry("id1", user)).thenReturn(entry1);
      when(autoSaveRecycleBinService.requireEntry("id2", user)).thenReturn(entry2);
      // Collides at the destination -- overwrite:true accepts it, but that makes the rollback of
      // this restore non-compensable (the destroyed original has no live inverse).
      when(autoSaveRecycleBinService.wouldCollide(entry1, "ws1", user)).thenReturn(true);
      when(autoSaveServiceProxy.restoreAutoSaveAssets("id1", "ws1", true, user)).thenReturn(true);
      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(true);
      when(autoSaveRecycleBinService.exists("id1", user)).thenReturn(false);
      // Second entry fails outright so the changeset rolls back.
      when(autoSaveRecycleBinService.exists("id2", user)).thenReturn(true);

      List<AutoSaveRecycleBinChangeRequest> changes =
         Arrays.asList(restore("id1", true), delete("id2"));
      String hash = planService.resolve(planRequest("restore ws1 overwrite, delete ws2", changes),
         user).planHash();
      AutoSaveRecycleBinApplyRequest req = applyRequest("restore ws1 overwrite, delete ws2", hash,
         restore("id1", true), delete("id2"));
      req.setAcknowledgeIrreversibleDelete(true);
      req.setReviewOutcome("looks good");

      AutoSaveRecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      // The gap the destroyed asset leaves behind is disclosed, but it is not itself a rollback
      // FAILURE -- removing the newly-created sheet would only make things worse, per this area's
      // own design.
      assertNull(result.rollbackFailures());

      AutoSaveRecycleBinApplyOutcome outcome1 = outcomeFor(result, "autosave:id1");
      assertNotNull(outcome1.advisory());
      assertTrue(outcome1.advisory().contains("permanently destroyed"));
      // Previously this half of the advisory was computed by rollbackOne and then silently
      // dropped -- it must now reach the caller.
      assertTrue(outcome1.advisory().contains("no live inverse"));

      // Non-compensable: the newly-created live sheet is left alone, never removed.
      verify(assetRepository, never()).removeSheet(any(AssetEntry.class), any(), anyBoolean());
   }

   // -------------------------------------------------------------------------
   // Finding 5: a clean `false` return (the primitive's OWN pre-mutation duplicate check refusing)
   // must not be misreported as an "unknown state" failure.
   // -------------------------------------------------------------------------

   @Test
   void applyRestoreCleanRefusalIsNotMisreportedAsUnknownState() throws Exception {
      AutoSaveRecycleBinEntryProjection entry3 = worksheetEntry("id3", "ws3");

      when(autoSaveRecycleBinService.requireEntry("id3", user)).thenReturn(entry3);
      when(autoSaveRecycleBinService.wouldCollide(entry3, "ws3", user)).thenReturn(false);
      // Something else created the destination between our own wouldCollide pre-check and the
      // real call -- restoreAutoSaveAssets' own internal duplicate check refuses cleanly, BEFORE
      // any repository.setSheet call.
      when(autoSaveServiceProxy.restoreAutoSaveAssets("id3", "ws3", false, user)).thenReturn(false);

      List<AutoSaveRecycleBinChangeRequest> changes = Arrays.asList(restore("id3", false));
      String hash = planService.resolve(planRequest("restore ws3", changes), user).planHash();
      AutoSaveRecycleBinApplyRequest req = applyRequest("restore ws3", hash, restore("id3", false));

      AutoSaveRecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verifyNoInteractions(assetRepository);
   }

   // -------------------------------------------------------------------------
   // Correct-behavior guard: a genuine exception DURING the restore call (which could have
   // partially mutated) must still be treated as unknown state -- the narrowing must not lose
   // real coverage.
   // -------------------------------------------------------------------------

   @Test
   void applyRestoreStillReportsUnknownStateWhenRestoreItselfThrows() throws Exception {
      AutoSaveRecycleBinEntryProjection entry4 = worksheetEntry("id4", "ws4");

      when(autoSaveRecycleBinService.requireEntry("id4", user)).thenReturn(entry4);
      when(autoSaveRecycleBinService.wouldCollide(entry4, "ws4", user)).thenReturn(false);
      when(autoSaveServiceProxy.restoreAutoSaveAssets("id4", "ws4", false, user))
         .thenThrow(new RuntimeException("simulated storage failure"));

      List<AutoSaveRecycleBinChangeRequest> changes = Arrays.asList(restore("id4", false));
      String hash = planService.resolve(planRequest("restore ws4", changes), user).planHash();
      AutoSaveRecycleBinApplyRequest req = applyRequest("restore ws4", hash, restore("id4", false));

      AutoSaveRecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> f.property().equals("autosave:id4") && f.error().contains("state unknown")));
      verifyNoInteractions(assetRepository);
   }
}
