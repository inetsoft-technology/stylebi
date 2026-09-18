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

import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * track-status/01-design.md section 5a/3a: no "partial" status (single entry only), read-back
 * verification, and the restart-specific partial-failure disclosure
 * ("stopped but not restarted", never a generic "restart failed").
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SchedulerStatusChangesetApplyServiceTest {
   @Mock private SchedulerConfigurationService configService;
   @Mock private XPrincipal user;

   private SchedulerStatusChangePlanService planService;
   private SchedulerStatusChangesetApplyService service;
   private MockedStatic<Tool> tool;
   private int originalReadbackMaxAttempts;
   private long originalReadbackPollIntervalMs;

   @BeforeEach void setUp() {
      planService = new SchedulerStatusChangePlanService(configService);
      service = new SchedulerStatusChangesetApplyService(planService, configService);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString())).thenAnswer(inv -> {
         String s = inv.getArgument(0);

         if(!s.startsWith("TKN:")) {
            throw new IllegalArgumentException("not a token");
         }

         return s.substring(4);
      });
      originalReadbackMaxAttempts = SchedulerStatusChangesetApplyService.READBACK_MAX_ATTEMPTS;
      originalReadbackPollIntervalMs =
         SchedulerStatusChangesetApplyService.READBACK_POLL_INTERVAL_MS;
      // Bug #76763 regression: shrink the (now much wider, real-production) retry budget for
      // every test that doesn't itself care about the exact numbers, so the "read-back never
      // matches" failure-path tests below don't have to burn the real ~30s worst-case budget.
      SchedulerStatusChangesetApplyService.READBACK_MAX_ATTEMPTS = 3;
      SchedulerStatusChangesetApplyService.READBACK_POLL_INTERVAL_MS = 1;
   }

   @AfterEach void tearDown() {
      tool.close();
      SchedulerStatusChangesetApplyService.READBACK_MAX_ATTEMPTS = originalReadbackMaxAttempts;
      SchedulerStatusChangesetApplyService.READBACK_POLL_INTERVAL_MS = originalReadbackPollIntervalMs;
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome / taskToken gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      stubStatus(false);
      SchedulerStatusApplyRequest req = applyRequest("task", "not-the-real-hash", "looks good", start());
      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsOnMissingReviewOutcome() {
      stubStatus(false);
      String hash = planService.resolve(request("task", List.of(start()))).planHash();
      SchedulerStatusApplyRequest req = applyRequest("task", hash, "  ", start());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   @Test void applyThrowsTaskTokenMismatchOnMissingToken() {
      stubStatus(false);
      String hash = planService.resolve(request("task", List.of(start()))).planHash();
      SchedulerStatusApplyRequest req = applyRequest("task", hash, "looks good", start());
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
   }

   // -------------------------------------------------------------------------
   // success -- read-back drives "verified"/"applied", uses a fresh getStatus call
   // -------------------------------------------------------------------------

   @Test void appliesStartAndReportsVerifiedFromFreshReadBack() throws Exception {
      stubStatus(false);
      String hash = planService.resolve(request("task", List.of(start()))).planHash();

      doAnswer(inv -> { stubStatus(true); return null; }).when(configService).setStatus("start");

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", start()), user);

         assertEquals(SchedulerStatusChangesetApplyService.STATUS_APPLIED, result.status());
         assertNull(result.backupRef());
         assertEquals(1, result.results().size());
         SchedulerStatusApplyOutcome outcome = result.results().get(0);
         assertEquals("start", outcome.verb());
         assertEquals("Stopped", outcome.before());
         assertEquals("Running", outcome.after());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, outcome.status());
         assertNull(outcome.error());
         verify(configService).setStatus("start");
      }
   }

   @Test void appliesStopAndReportsVerified() throws Exception {
      stubStatus(true);
      String hash = planService.resolve(request("task", List.of(stop()))).planHash();

      doAnswer(inv -> { stubStatus(false); return null; }).when(configService).setStatus("stop");

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", stop()), user);
         assertEquals(SchedulerStatusChangesetApplyService.STATUS_APPLIED, result.status());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
      }
   }

   // -------------------------------------------------------------------------
   // bug #76763 regression -- stop is an async, cross-process (Ignite topology leave) state
   // change; the old 4-attempts/60ms (~180ms total) read-back budget could give up before it
   // completed, reporting "failed" even though the scheduler really did stop moments later.
   // -------------------------------------------------------------------------

   @Test void appliesStopAndVerifiesAfterADelayThatWouldHaveExceededTheOldOneEightyMsBudget()
      throws Exception
   {
      // Restore the real production budget for this test -- it's the shipped numbers
      // (61 attempts / 500ms) that must survive the delay, not the shrunk per-test default.
      SchedulerStatusChangesetApplyService.READBACK_MAX_ATTEMPTS = originalReadbackMaxAttempts;
      SchedulerStatusChangesetApplyService.READBACK_POLL_INTERVAL_MS = originalReadbackPollIntervalMs;

      // getStatus() is called twice before the read-back even starts: once by this test's own
      // planService.resolve() call (to compute the hash below), and once again by apply()'s own
      // internal re-resolve. Then readBackWithRetry() makes its own immediate read plus one call
      // per retry. Under the OLD budget (READBACK_MAX_ATTEMPTS=4: 1 immediate + 3 retries, i.e.
      // 4 internal calls, overall calls 3-6), all of those must still read "Running" so the old
      // budget exhausts and reports "failed". Only the NEW budget's 4th retry (overall call 7)
      // reaches the "Stopped" read.
      when(configService.getStatus()).thenReturn(
         scheduleStatus(true), scheduleStatus(true), scheduleStatus(true), scheduleStatus(true),
         scheduleStatus(true), scheduleStatus(true), scheduleStatus(false));
      String hash = planService.resolve(request("task", List.of(stop()))).planHash();
      doNothing().when(configService).setStatus("stop");

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", stop()), user);
         assertEquals(SchedulerStatusChangesetApplyService.STATUS_APPLIED, result.status());
         assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
         assertEquals("Stopped", result.results().get(0).after());
      }
   }

   // -------------------------------------------------------------------------
   // failure -- read-back never matches proposed state
   // -------------------------------------------------------------------------

   @Test void overallStatusIsFailedWhenReadBackNeverMatchesProposedState() throws Exception {
      stubStatus(false);
      String hash = planService.resolve(request("task", List.of(start()))).planHash();
      // setStatus("start") does nothing to the mocked status -- read-back stays "Stopped" forever.

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", start()), user);
         assertEquals(SchedulerStatusChangesetApplyService.STATUS_FAILED, result.status());
         assertEquals(AdminChangeRecord.STATUS_FAILED, result.results().get(0).status());
         assertNotNull(result.results().get(0).error());
      }
   }

   // -------------------------------------------------------------------------
   // restart's own documented partial-failure path (section 3a) -- a thrown IllegalStateException
   // from setStatus("restart") must be surfaced plainly, not as a generic "restart failed"
   // -------------------------------------------------------------------------

   @Test void restartTimeoutIsSurfacedAsStoppedNotRestartedNotAGenericFailure() throws Exception {
      stubStatus(true);
      String hash = planService.resolve(request("task", List.of(restart()))).planHash();

      doThrow(new IllegalStateException("Time out waiting for scheduler to stop"))
         .when(configService).setStatus("restart");

      try(MockedStatic<Audit> audit = mockAudit()) {
         var result = service.apply(applyRequest("task", hash, "looks good", restart()), user);

         assertEquals(SchedulerStatusChangesetApplyService.STATUS_FAILED, result.status());
         SchedulerStatusApplyOutcome outcome = result.results().get(0);
         assertEquals(AdminChangeRecord.STATUS_FAILED, outcome.status());
         assertTrue(outcome.error().contains("NOT restarted"));
         assertTrue(outcome.error().contains("Time out waiting for scheduler to stop"));
      }
   }

   // -------------------------------------------------------------------------
   // no "partial" status is possible in this area -- single entry only
   // -------------------------------------------------------------------------

   @Test void applyRefusesMoreThanOneEntry() {
      stubStatus(false);
      SchedulerStatusApplyRequest req = new SchedulerStatusApplyRequest();
      req.setTask("task");
      req.setChanges(List.of(start(), stop()));
      req.setPlanHash("whatever");
      req.setReviewOutcome("looks good");
      assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
   }

   // -------------------------------------------------------------------------
   // audit
   // -------------------------------------------------------------------------

   @Test void writesAnAuditRecordWithSchedulerStatusObjectTypeAndNullBackupRef() {
      stubStatus(false);
      String hash = planService.resolve(request("task", List.of(start()))).planHash();
      Audit auditInstance = mock(Audit.class);
      tool.when(Tool::getHost).thenReturn("test-host");

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         service.apply(applyRequest("task", hash, "looks good", start()), user);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      AdminChangeRecord record = captor.getValue();
      assertEquals("scheduler-status", record.getObjectType());
      assertEquals(AdminChangeRecord.SCOPE_VALUE, record.getSnapshotScope());
      assertEquals(AdminChangeRecord.RISK_HIGH, record.getRiskLevel());
      assertNull(record.getBackupRef());
      assertEquals(AdminChangeRecord.ACTION_APPLY, record.getAction());
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

   private void stubStatus(boolean running) {
      lenient().when(configService.getStatus()).thenReturn(scheduleStatus(running));
   }

   private static ScheduleStatusModel scheduleStatus(boolean running) {
      return ScheduleStatusModel.builder().cluster(false).running(running)
         .externalStorageLocation("/tmp").build();
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

   private static SchedulerStatusApplyRequest applyRequest(
      String task, String planHash, String reviewOutcome, SchedulerStatusChangeRequest... changes)
   {
      SchedulerStatusApplyRequest req = new SchedulerStatusApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(planHash);
      req.setReviewOutcome(reviewOutcome);
      req.setTaskToken(TaskAuditToken.issue(planHash, task));
      return req;
   }
}
