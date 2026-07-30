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

   /** Builds an apply request whose planHash is correct for the current stubbed state. */
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
      PlanRequest probe = new PlanRequest();
      probe.setTask(task);
      probe.setChanges(changes);
      req.setPlanHash(planService.resolve(probe).planHash());
      return req;
   }

   private static AdminChangeResult result(String property, String before, String after,
                                           String status, String error)
   {
      AdminChangeResult r = new AdminChangeResult();
      r.setProperty(property);
      r.setBeforeValue(before);
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

      assertTrue(applied.transactionId().matches("^chg-[0-9a-f]{8}$"));
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

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, applied.status());
      ApplyOutcome failed = applied.results().get(1);
      assertEquals(AdminChangeRecord.STATUS_FAILED, failed.status());
      assertTrue(failed.error().contains("boom"));
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
