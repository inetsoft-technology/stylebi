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
package inetsoft.web.admin.ai;

import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.AdminChangeRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N property writes through SreeEnv cannot be made atomic, so failure is handled by compensation:
 * restore each recorded beforeValue in reverse. Two paths matter most, and both leave the server
 * partially changed if mishandled:
 *
 *  - A change can FAIL BY THROWING, not only by returning status "failed". Letting an exception
 *    escape mid-sequence would abandon earlier changes in an applied state.
 *  - A rollback can itself fail. Every undo must still be attempted, and the caller must be told
 *    which properties are still changed - it cannot fix what it is not told about.
 *
 * Rollback restores the beforeValue the SERVER recorded during the apply, not the plan's
 * currentValue: the server snapshots inside the same operation, so it is authoritative.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminChangesetApplyServiceTest {
   @Mock private AdminChangeService changeService;
   @Mock private AdminBackupService backupService;
   @Mock private Principal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private AdminChangesetApplyService service;
   private AdminChangePlanService planService;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      AdminPropertyCatalog catalog = new AdminPropertyCatalog();
      planService = new AdminChangePlanService(catalog, new AdminRiskClassifier(catalog));
      service = new AdminChangesetApplyService(planService, changeService, backupService);
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   /**
    * Builds an apply request whose planHash is correct for the current stubbed state.
    *
    * <p>Defaults reviewOutcome to "approved" so tests that are not about Finding 5b's signoff
    * gate do not need to know or care whether their properties happen to be high risk; tests that
    * ARE about the gate override it afterwards.
    */
   private ApplyRequest request(String task, String... propertyValuePairs) {
      List<PlanRequest.Change> changes = new ArrayList<>();

      for(int i = 0; i < propertyValuePairs.length; i += 2) {
         PlanRequest.Change change = new PlanRequest.Change();
         change.setProperty(propertyValuePairs[i]);
         change.setValue(propertyValuePairs[i + 1]);
         changes.add(change);
      }

      ApplyRequest req = new ApplyRequest();
      req.setTask(task);
      req.setChanges(changes);
      req.setReviewOutcome("approved");
      PlanRequest probe = new PlanRequest();
      probe.setTask(task);
      probe.setChanges(changes);
      req.setPlanHash(planService.resolve(probe).planHash());
      return req;
   }

   /** Models a change whose snapshot read succeeded - the normal case for every result below. */
   private static AdminChangeResult result(String property, String before, String after,
                                           String status, String error)
   {
      AdminChangeResult r = new AdminChangeResult();
      r.setProperty(property);
      r.setBeforeValue(before);
      r.setBeforeRead(true);
      r.setAfterValue(after);
      r.setStatus(status);
      r.setError(error);
      return r;
   }

   private void stub(String key, String value) {
      sreeEnv.when(() -> SreeEnv.getProperty(key, false, false)).thenReturn(value);
   }

   // ── success ──────────────────────────────────────────────────────────────

   @Test
   void appliesEveryChangeAndReportsApplied() throws Exception {
      stub("query.runtime.maxrow", "100");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(request("t", "max.rows", "500"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, applied.status());
      assertNull(applied.backupRef());
      assertNull(applied.rollbackFailures());
      assertEquals(1, applied.results().size());
      assertNotNull(applied.transactionId());
      verify(backupService, never()).backup(anyString());
   }

   @Test
   void mintsATransactionIdAndPassesItToEveryChange() throws Exception {
      stub("query.runtime.maxrow", "100");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(request("t", "max.rows", "500"), principal);

      // Finding 8: 16 hex chars from a 64-bit long, not 8 from a 32-bit int - transactionId is the
      // audit correlation key, and a narrower id collides too easily across many changesets.
      assertTrue(applied.transactionId().matches("^chg-[0-9a-f]{16}$"));
      verify(changeService).applyChange(argThat(
         req -> applied.transactionId().equals(req.getTransactionId())
            && AdminChangeRecord.ACTION_APPLY.equals(req.getAction())
            && "500".equals(req.getValue())), eq(principal));
   }

   @Test
   void forwardsTheReviewOutcomeToTheAuditRecord() throws Exception {
      stub("query.runtime.maxrow", "100");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null));
      ApplyRequest req = request("t", "max.rows", "500");
      req.setReviewOutcome("approved");

      service.apply(req, principal);

      verify(changeService).applyChange(
         argThat(r -> "approved".equals(r.getReviewOutcome())), eq(principal));
   }

   // ── concurrency ──────────────────────────────────────────────────────────

   /**
    * Finding 1: without a lock serializing the whole body of {@code apply}, two concurrent
    * applies can both proceed past the plan resolution/hash check and both call
    * {@code changeService.applyChange} while the other is mid-flight - which is exactly the
    * interleaving that lets one apply's rollback clobber the other's committed write. This test
    * does not exercise real SreeEnv contention (changeService is mocked); it proves the narrower,
    * directly-testable claim that {@code apply}'s critical section never runs concurrently with
    * itself, which is the property {@code APPLY_LOCK} exists to guarantee.
    */
   @Test
   void serializesConcurrentApplies() throws Exception {
      AdminChangePlanService mockPlanService = mock(AdminChangePlanService.class);
      PlanChange change = new PlanChange("query.runtime.maxrow", null, "100", "500",
         AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_VALUE, true, null);
      ResolvedPlan plan = new ResolvedPlan("t", List.of(change), false, false, "fixed-hash");
      when(mockPlanService.resolve(any())).thenReturn(plan);
      AdminChangesetApplyService concurrentService =
         new AdminChangesetApplyService(mockPlanService, changeService, backupService);

      AtomicInteger concurrent = new AtomicInteger();
      AtomicInteger maxConcurrent = new AtomicInteger();
      when(changeService.applyChange(any(), eq(principal))).thenAnswer(inv -> {
         int c = concurrent.incrementAndGet();
         maxConcurrent.updateAndGet(m -> Math.max(m, c));

         try {
            Thread.sleep(150);
         }
         finally {
            concurrent.decrementAndGet();
         }

         return result("query.runtime.maxrow", "100", "500",
                        AdminChangeRecord.STATUS_VERIFIED, null);
      });

      ApplyRequest req = new ApplyRequest();
      req.setTask("t");
      req.setChanges(List.of());
      req.setPlanHash("fixed-hash");

      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         List<Future<ApplyResult>> futures = List.of(
            pool.submit(() -> concurrentService.apply(req, principal)),
            pool.submit(() -> concurrentService.apply(req, principal)));

         for(Future<ApplyResult> future : futures) {
            assertEquals(AdminChangesetApplyService.STATUS_APPLIED, future.get(5, TimeUnit.SECONDS).status());
         }
      }
      finally {
         pool.shutdown();
      }

      assertEquals(1, maxConcurrent.get(), "two applies ran their critical section concurrently");
   }

   // ── review gate ──────────────────────────────────────────────────────────

   @Test
   void refusesAMismatchedPlanHashWithoutApplyingAnything() throws Exception {
      stub("query.runtime.maxrow", "100");
      ApplyRequest req = request("t", "max.rows", "500");
      req.setPlanHash("deadbeef");

      AdminChangesetApplyService.PlanHashMismatchException thrown = assertThrows(
         AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, principal));

      assertNotNull(thrown.current());
      assertEquals("query.runtime.maxrow", thrown.current().changes().get(0).property());
      verify(changeService, never()).applyChange(any(), any());
   }

   @Test
   void refusesAMissingPlanHash() {
      stub("query.runtime.maxrow", "100");
      ApplyRequest req = request("t", "max.rows", "500");
      req.setPlanHash(null);

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                   () -> service.apply(req, principal));
   }

   @Test
   void refusesWhenTheCurrentValueDriftedAfterPreview() throws Exception {
      // The drift case: hash computed against "100", but the property is now "999".
      stub("query.runtime.maxrow", "100");
      ApplyRequest req = request("t", "max.rows", "500");
      stub("query.runtime.maxrow", "999");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                   () -> service.apply(req, principal));
      verify(changeService, never()).applyChange(any(), any());
   }

   // ── agent signoff ────────────────────────────────────────────────────────

   @Test
   void refusesAHighRiskChangesetWithoutAReviewOutcome() throws Exception {
      // Finding 5b: requiresAgentSignoff was computed but never enforced. mail.smtp.host is high
      // risk (see the catalog), so this plan requires signoff.
      stub("mail.smtp.host", "old");
      ApplyRequest req = request("t", "mail.smtp.host", "new");
      req.setReviewOutcome(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, principal));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
      verify(changeService, never()).applyChange(any(), any());
      verify(backupService, never()).backup(anyString());
   }

   @Test
   void refusesAHighRiskChangesetWithABlankReviewOutcome() throws Exception {
      stub("mail.smtp.host", "old");
      ApplyRequest req = request("t", "mail.smtp.host", "new");
      req.setReviewOutcome("   ");

      assertThrows(IllegalArgumentException.class, () -> service.apply(req, principal));
      verify(changeService, never()).applyChange(any(), any());
   }

   @Test
   void proceedsWithAHighRiskChangesetWhenAReviewOutcomeIsProvided() throws Exception {
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("mail.smtp.host", "old", "new",
                            AdminChangeRecord.STATUS_VERIFIED, null));
      ApplyRequest req = request("t", "mail.smtp.host", "new");
      req.setReviewOutcome("approved");

      ApplyResult applied = service.apply(req, principal);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, applied.status());
   }

   @Test
   void doesNotRequireAReviewOutcomeForALowRiskChangeset() throws Exception {
      stub("query.runtime.maxrow", "100");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null));
      ApplyRequest req = request("t", "max.rows", "500");
      req.setReviewOutcome(null);

      ApplyResult applied = service.apply(req, principal);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, applied.status());
   }

   // ── storage backup ───────────────────────────────────────────────────────

   @Test
   void takesAStorageBackupBeforeApplyingAnything() throws Exception {
      stub("security.exposedefaultorgtoall", "false");
      when(backupService.backup(anyString())).thenReturn("admin-chg-1.zip");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("security.exposedefaultorgtoall", "false", "true",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied =
         service.apply(request("t", "security.exposedefaultorgtoall", "true"), principal);

      assertEquals("admin-chg-1.zip", applied.backupRef());
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, applied.status());
      var order = inOrder(backupService, changeService);
      order.verify(backupService).backup(applied.transactionId());
      order.verify(changeService).applyChange(any(), eq(principal));
      verify(changeService).applyChange(
         argThat(req -> "admin-chg-1.zip".equals(req.getBackupRef())), eq(principal));
   }

   @Test
   void appliesNothingWhenTheBackupFails() throws Exception {
      stub("security.exposedefaultorgtoall", "false");
      when(backupService.backup(anyString())).thenThrow(new IllegalStateException("no storage"));

      assertThrows(IllegalStateException.class,
         () -> service.apply(request("t", "security.exposedefaultorgtoall", "true"), principal));

      verify(changeService, never()).applyChange(any(), any());
   }

   // ── rollback ─────────────────────────────────────────────────────────────

   @Test
   void rollsBackVerifiedChangesInReverseWhenOneReportsFailed() throws Exception {
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenReturn(result("mail.smtp.host", "old", "old",
                            AdminChangeRecord.STATUS_FAILED, "did not take"))
         .thenReturn(result("query.runtime.maxrow", "500", "100",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(
         request("t", "max.rows", "500", "mail.smtp.host", "new"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
      assertNull(applied.rollbackFailures());
      verify(changeService).applyChange(argThat(
         req -> AdminChangeRecord.ACTION_ROLLBACK.equals(req.getAction())
            && "query.runtime.maxrow".equals(req.getProperty())
            && "100".equals(req.getValue())), eq(principal));
   }

   @Test
   void stopsAtTheFirstFailureInsteadOfApplyingTheRest() throws Exception {
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "100",
                            AdminChangeRecord.STATUS_FAILED, "nope"));

      ApplyResult applied = service.apply(
         request("t", "max.rows", "500", "mail.smtp.host", "new"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
      assertEquals(1, applied.results().size());
      // Nothing verified, so nothing to undo: exactly one call.
      verify(changeService, times(1)).applyChange(any(), eq(principal));
   }

   @Test
   void rollsBackWhenAnApplyThrows() throws Exception {
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenThrow(new IllegalStateException("boom"))
         .thenReturn(result("query.runtime.maxrow", "500", "100",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(
         request("t", "max.rows", "500", "mail.smtp.host", "new"), principal);

      // NOTE (deviation from the reviewer's literal instruction - see task-6-report.md
      // "Post-review follow-up" section): a thrown apply carries no before/after evidence, so
      // per the SAME review's Finding 1 rule ("must NOT be silently reported as rolled back ...
      // so the final status becomes rollback-failed"), this scenario cannot be "rolled-back"
      // either - mail.smtp.host's true state is unknown even though max.rows was cleanly undone.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals(1, applied.rollbackFailures().size());
      assertEquals("mail.smtp.host", applied.rollbackFailures().get(0).property());
      ApplyOutcome failed = applied.results().get(1);
      assertEquals(AdminChangeRecord.STATUS_FAILED, failed.status());
      assertTrue(failed.error().contains("boom"));
      verify(changeService).applyChange(argThat(
         r -> AdminChangeRecord.ACTION_ROLLBACK.equals(r.getAction())
            && "query.runtime.maxrow".equals(r.getProperty())
            && "100".equals(r.getValue())), eq(principal));
   }

   @Test
   void undoesAChangeThatMovedStateEvenThoughItReportedFailed() throws Exception {
      // Path A: SreeEnv.save() succeeded and then a side-effect hook threw, so the property IS
      // changed while the status says failed. Keying the undo set on VERIFIED alone would leave it
      // applied and still report "rolled-back".
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_FAILED, "side effect exploded"))
         .thenReturn(result("query.runtime.maxrow", "500", "100",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(request("t", "max.rows", "500"), principal);

      verify(changeService).applyChange(argThat(
         r -> AdminChangeRecord.ACTION_ROLLBACK.equals(r.getAction())
            && "query.runtime.maxrow".equals(r.getProperty())
            && "100".equals(r.getValue())), eq(principal));
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
   }

   @Test
   void reportsRollbackFailedWhenAnApplyThrewAndTheStateIsUnknown() throws Exception {
      // Path B: the outcome is unknown - there is no before/after to undo from - so claiming
      // "rolled-back" would be a false assurance.
      stub("query.runtime.maxrow", "100");
      when(changeService.applyChange(any(), eq(principal)))
         .thenThrow(new IllegalStateException("audit exploded after save"));

      ApplyResult applied = service.apply(request("t", "max.rows", "500"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals(1, applied.rollbackFailures().size());
      assertEquals("query.runtime.maxrow", applied.rollbackFailures().get(0).property());
   }

   @Test
   void restoresTheServerReportedBeforeValueNotThePlansCurrentValue() throws Exception {
      // The value drifted between preview and the actual write; the server's snapshot wins.
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "drifted", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenReturn(result("mail.smtp.host", "old", "old",
                            AdminChangeRecord.STATUS_FAILED, "no"))
         .thenReturn(result("query.runtime.maxrow", "500", "drifted",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      service.apply(request("t", "max.rows", "500", "mail.smtp.host", "new"), principal);

      verify(changeService).applyChange(argThat(
         req -> AdminChangeRecord.ACTION_ROLLBACK.equals(req.getAction())
            && "drifted".equals(req.getValue())), eq(principal));
   }

   @Test
   void reportsRollbackFailedAndNamesThePropertiesStillChanged() throws Exception {
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      stub("log.detail.level", "warn");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenReturn(result("log.detail.level", "warn", "info",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenReturn(result("mail.smtp.host", "old", "old",
                            AdminChangeRecord.STATUS_FAILED, "no"))
         // rollback of log.detail.level throws; the undo of max.rows must still be attempted
         .thenThrow(new IllegalStateException("undo exploded"))
         .thenReturn(result("query.runtime.maxrow", "500", "100",
                            AdminChangeRecord.STATUS_VERIFIED, null));

      ApplyResult applied = service.apply(request("t", "max.rows", "500",
         "log.detail.level", "info", "mail.smtp.host", "new"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals(1, applied.rollbackFailures().size());
      assertEquals("log.detail.level", applied.rollbackFailures().get(0).property());
      assertTrue(applied.rollbackFailures().get(0).error().contains("exploded"));
      // 3 applies + 2 rollback attempts: every undo attempted despite the failure.
      verify(changeService, times(5)).applyChange(any(), eq(principal));
   }

   @Test
   void doesNotUndoAChangeWhoseBeforeValueWasNeverActuallyRead() throws Exception {
      // Finding 3: if SreeEnv.getProperty throws on the FIRST read inside
      // AdminChangeService.applyChange (so beforeValue stays null and no write happened), but the
      // catch block's re-read succeeds, the result looks like before=null, after=<value> - the
      // same shape as the NORMAL case of setting a previously-unset property. beforeRead=false is
      // the only way to tell them apart, and only a confirmed read makes "moved" safe to undo:
      // undoing this one would remove a property that was never touched.
      stub("query.runtime.maxrow", "100");
      AdminChangeResult unreadBefore = new AdminChangeResult();
      unreadBefore.setProperty("query.runtime.maxrow");
      unreadBefore.setBeforeValue(null);
      unreadBefore.setBeforeRead(false);
      unreadBefore.setAfterValue("500");
      unreadBefore.setStatus(AdminChangeRecord.STATUS_FAILED);
      unreadBefore.setError("snapshot read failed");

      when(changeService.applyChange(any(), eq(principal))).thenReturn(unreadBefore);

      ApplyResult applied = service.apply(request("t", "max.rows", "500"), principal);

      // Exactly 1 call: the failed apply. Without the beforeRead gate, before=null != after="500"
      // would look "moved" and trigger an undo call with value null - removing a property that
      // was never touched.
      verify(changeService, times(1)).applyChange(any(), eq(principal));
      verify(changeService, never()).applyChange(argThat(
         req -> AdminChangeRecord.ACTION_ROLLBACK.equals(req.getAction())), eq(principal));
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
   }

   @Test
   void treatsARollbackThatReportsFailedAsARollbackFailure() throws Exception {
      stub("query.runtime.maxrow", "100");
      stub("mail.smtp.host", "old");
      when(changeService.applyChange(any(), eq(principal)))
         .thenReturn(result("query.runtime.maxrow", "100", "500",
                            AdminChangeRecord.STATUS_VERIFIED, null))
         .thenReturn(result("mail.smtp.host", "old", "old",
                            AdminChangeRecord.STATUS_FAILED, "no"))
         .thenReturn(result("query.runtime.maxrow", "500", "500",
                            AdminChangeRecord.STATUS_FAILED, "could not restore"));

      ApplyResult applied = service.apply(
         request("t", "max.rows", "500", "mail.smtp.host", "new"), principal);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, applied.status());
      assertEquals("query.runtime.maxrow", applied.rollbackFailures().get(0).property());
   }
}
