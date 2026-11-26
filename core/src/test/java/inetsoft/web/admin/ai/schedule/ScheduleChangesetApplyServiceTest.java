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

import inetsoft.web.api.schedule.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Two structural differences from the properties-area apply-loop tests this mirrors (spec §6):
 * verification is existence-based, not a value-equality check, and the compensating action for a
 * delete is a re-create built from a snapshot captured DURING apply -- both exercised below.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleChangesetApplyServiceTest {
   @Mock private AdminScheduleGateway scheduleGateway;
   @Mock private ScheduleManager scheduleManager;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   private ScheduleChangePlanService planService;
   private ScheduleChangesetApplyService service;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private Audit auditMock;

   @BeforeEach void setUp() {
      planService = new ScheduleChangePlanService(scheduleGateway, scheduleManager);
      service = new ScheduleChangesetApplyService(planService, scheduleGateway, scheduleManager,
                                                   backupService);
      // writeAudit's Tool.getHost() call falls through to SreeEnv.getProperty("local.host.name")
      // when unset; a lenient, unstubbed static mock returns null there instead of throwing
      // ShutdownException for a Spring context that isn't up in this unit test, same as
      // AdminChangesetApplyServiceTest's own setUp.
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            // an empty password (an action that has none) is read back as it is
            if(s.isEmpty()) {
               return s;
            }

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });

      // writeAudit calls the static Audit.getInstance() singleton directly (no injectable
      // AdminChangeService to intercept, unlike properties) -- mock it statically so tests can
      // inspect what task narrative actually reached the audit record.
      auditMock = mock(Audit.class);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(auditMock);
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      tool.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesACreateAndReportsApplied() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      // Not present before, present after -- the existence-based verify this area uses in place
      // of a value-equality check.
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)              // preview-time existence check (resolve, in preview())
         .thenReturn(null)              // re-resolve inside apply()
         .thenReturn(sreeTask("t1", "admin")); // apply-time verify, after addScheduleTask
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      // Bug #80650: a create-only plan takes no whole-storage snapshot.
      assertNull(result.backupRef());
      verify(backupService, never()).backup(anyString());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(user));
      verify(scheduleGateway, never()).removeScheduleTask(anyString(), any(), eq(user));
   }

   @Test void appliesADeleteAndReportsApplied() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing)   // apply: capture before-state
         .thenReturn(null);      // apply: verify gone
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("snap-ref", result.backupRef());
      verify(backupService, times(1)).backup(anyString());
      verify(scheduleGateway).removeScheduleTask(eq("t1"), any(), eq(user));
   }

   // Bug #81073: a delete whose owner is in the caller's organization and whose save dry-run
   // passes is undone by re-creating the captured task, so apply takes no whole-storage snapshot.
   @Test void appliesACompensableDeleteWithoutASnapshot() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing)   // apply: capture before-state
         .thenReturn(null);      // apply: verify gone
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      inetsoft.sree.security.OrganizationManager orgManager =
         mock(inetsoft.sree.security.OrganizationManager.class);
      when(orgManager.getCurrentOrgID(user)).thenReturn("host-org");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));
      ApplyResult result;

      try(MockedStatic<inetsoft.sree.security.OrganizationManager> orgStatic =
             mockStatic(inetsoft.sree.security.OrganizationManager.class))
      {
         orgStatic.when(inetsoft.sree.security.OrganizationManager::getInstance)
            .thenReturn(orgManager);
         result = service.apply(req, user, null);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.backupRef());
      verify(backupService, never()).backup(anyString());
      verify(scheduleGateway).removeScheduleTask(eq("t1"), any(), eq(user));
   }

   // The core regression proof for a create: the audit record's taskDescription must come from
   // the taskToken's embedded (reviewed) narrative, not from this apply request's own (possibly
   // diverged) task field.
   @Test void auditsThePreviewedTaskForACreateEvenWhenApplyTaskDiffers() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: create t1", "totally different apply-time text", createChange(spec));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: create t1".equals(r.getTaskDescription())));
   }

   // Same proof for a delete.
   @Test void auditsThePreviewedTaskForADeleteEvenWhenApplyTaskDiffers() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)
         .thenReturn(existing)
         .thenReturn(existing)
         .thenReturn(null);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: delete t1", "totally different apply-time text", deleteChange("t1"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: delete t1".equals(r.getTaskDescription())));
   }

   // -------------------------------------------------------------------------
   // gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsOnStalePlanHash() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setPlanHash("stale-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user, null));
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), any(), any(), any(), eq(user));
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user, null));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   @Test void rejectsAMissingTaskToken() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user, null));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), any(), any(), any(), eq(user));
   }

   @Test void rejectsABlankTaskToken() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setTaskToken("   ");

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user, null));
   }

   @Test void rejectsATaskTokenIssuedForADifferentPlan() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      CreateScheduleTaskRequest otherSpec = createSpec("t2", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      String otherTaskId =
         ScheduleManager.getTaskId(otherSpec.getOwner().convertToKey(), otherSpec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleManager.getScheduleTask(otherTaskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      when(scheduleGateway.hasDeletePermission(otherTaskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      ScheduleApplyRequest otherPlan = applyRequest("create another task", createChange(otherSpec));
      req.setTaskToken(otherPlan.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user, null));
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), any(), any(), any(), eq(user));
   }

   // -------------------------------------------------------------------------
   // failure / rollback
   // -------------------------------------------------------------------------

   // A throw carries no verifiable before/after evidence -- it must be reported as unknown state,
   // never as rolled back.
   @Test void throwMidApplyIsReportedAsUnknownStateAndRollbackFailed() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");
      doThrow(new IllegalStateException("boom")).when(scheduleGateway)
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), any(), any(), any(), eq(user));

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals(taskId, result.rollbackFailures().get(0).property());
   }

   // Bug #79426, addScheduleTask refuses an owner/execute-as it can't accept before it writes
   // anything; that refusal wrote nothing, so it is rolled back, never rollback-failed.
   // Bug #79974, the save's own refusals (run principal can't read the action viewsheet, ...) all
   // precede its first write, so the preflight reports them as rolled-back, never rollback-failed.
   @Test void createRefusedBySavePreflightIsRolledBackNotRollbackFailed() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");
      // the plan (which apply re-resolves) passes no link, so only the apply-time preflight throws
      lenient().doThrow(new inetsoft.web.security.auth.UnauthorizedAccessException(
         "Unauthorized access to viewsheet X"))
         .when(scheduleGateway).checkAddScheduleTaskSavable(any(), anyBoolean(), anyBoolean(),
            anyLong(), anyLong(), any(), any(), any(), any(), any(), isNotNull(), any(), eq(user));

      ApplyResult result =
         service.apply(applyRequest("create a task", createChange(spec)), user, "http://example.com/");

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway, never()).addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(),
         anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
   }

   @Test void createRefusedBeforeAnyWriteIsRolledBackNotRollbackFailed() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");
      doThrow(new MessageException("The execute-as user nouser does not exist"))
         .when(scheduleGateway).checkAddScheduleTaskAllowed(any(), any(), eq(user));

      ApplyResult result =
         service.apply(applyRequest("create a task", createChange(spec)), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway, never()).addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(),
         anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
   }

   // The before-state capture (the deep copy of the stored task and its permission, bug #78420)
   // is read-only and runs strictly BEFORE the mutating removeScheduleTask call -- a throw there
   // proves nothing was mutated, unlike throwMidApplyIsReportedAsUnknownStateAndRollbackFailed
   // above (whose throw comes FROM the mutating call itself). Must not be folded into the same
   // STATUS_ROLLBACK_FAILED/unknown-state bucket as that case.
   @Test void deletePreflightCaptureFailureIsReportedAsRolledBackNotRollbackFailed() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing);  // apply: capture before-state
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(scheduleGateway.getTaskPermission("t1")).thenThrow(new NullPointerException("boom"));
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway, never()).removeScheduleTask(anyString(), any(), eq(user));
   }

   // applyDelete's OWN before-state read (scheduleManager.getScheduleTask), one statement before
   // the deep copy of the stored task (formerly captureSpec) even runs -- a throw there must
   // still be reported as rolled back, not rollback-failed, since removeScheduleTask was never
   // reached (bug 76856).
   @Test void deleteBeforeStateReadFailurePriorToCaptureSpecIsRolledBackNotRollbackFailed()
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenThrow(new RuntimeException("storage read failed")); // apply: before-state read
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway, never()).removeScheduleTask(anyString(), any(), eq(user));
   }

   // Same gap, a different one of the three unwrapped preamble statements: ScheduleXmlProjection
   // .project(beforeTask) organically NPEs when the before-task's owner is null (ScheduleTask
   // #writeXML unconditionally calls owner.convertToKey()) -- still strictly before
   // removeScheduleTask, so it must still resolve to rolled back.
   @Test void deleteBeforeStateProjectionFailureWithNullOwnerIsRolledBackNotRollbackFailed()
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask nullOwnerTask = new inetsoft.sree.schedule.ScheduleTask();
      nullOwnerTask.setName("t1");
      nullOwnerTask.setEnabled(true);
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)        // preview
         .thenReturn(existing)        // re-resolve inside apply()
         .thenReturn(nullOwnerTask);  // apply: before-state read, owner unset
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway, never()).removeScheduleTask(anyString(), any(), eq(user));
   }

   // The rollback-path regression proof: even when the first change's own apply narrative and the
   // second change's failure trigger a rollback, the rollback's OWN writeAudit call (a separate
   // call site from applyCreate/applyDelete -- see rollback()'s ACTION_ROLLBACK writeAudit calls)
   // must still carry the previewed/reviewed task narrative, not the apply request's own
   // (possibly diverged) task field. Same shape as secondChangeFailingRollsBackTheFirst below, but
   // built via requestWithDivergentApplyTask so a regression that reverted the rollback call site
   // to plan.task() (or the raw request task) would be caught here.
   @Test void auditsThePreviewedTaskForARollbackEvenWhenApplyTaskDiffers() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String createdId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      inetsoft.sree.schedule.ScheduleTask other = sreeTask("t2", "admin");

      when(scheduleManager.getScheduleTask(createdId))
         .thenReturn(null)                       // preview
         .thenReturn(null)                       // re-resolve
         .thenReturn(sreeTask("t1", "admin"))     // apply-time verify: created
         .thenReturn(null);                       // rollback-time verify: deleted
      when(scheduleManager.getScheduleTask("t2"))
         .thenReturn(other, other, other, other); // "exists" the whole time -- delete never verifies

      when(scheduleGateway.hasDeletePermission(createdId, user)).thenReturn(true);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: two changes", "totally different apply-time text",
         createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(scheduleGateway).removeScheduleTask(eq(createdId), any(), eq(user));

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      List<AdminChangeRecord> rollbackRecords = captor.getAllValues().stream()
         .filter(r -> AdminChangeRecord.ACTION_ROLLBACK.equals(r.getAction()))
         .toList();
      assertFalse(rollbackRecords.isEmpty());
      assertTrue(rollbackRecords.stream()
         .allMatch(r -> "reviewed: two changes".equals(r.getTaskDescription())));
   }

   // N-change plan: the first change (create) succeeds, the second (delete of an unrelated task)
   // fails verification -- rollback must undo the first, newest-first (trivially, since it's the
   // only undoable one), by deleting what it created.
   @Test void secondChangeFailingRollsBackTheFirst() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String createdId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      inetsoft.sree.schedule.ScheduleTask other = sreeTask("t2", "admin");

      when(scheduleManager.getScheduleTask(createdId))
         .thenReturn(null)                       // preview
         .thenReturn(null)                       // re-resolve
         .thenReturn(sreeTask("t1", "admin"))     // apply-time verify: created
         .thenReturn(null);                       // rollback-time verify: deleted
      when(scheduleManager.getScheduleTask("t2"))
         .thenReturn(other, other, other, other); // "exists" the whole time -- delete never verifies

      when(scheduleGateway.hasDeletePermission(createdId, user)).thenReturn(true);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("two changes", createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(scheduleGateway).removeScheduleTask(eq(createdId), any(), eq(user));
   }

   // Every undo is attempted regardless, and a failed undo is named, not silently dropped.
   @Test void rollbackItselfFailingIsReportedNamingTheTaskId() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String createdId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      inetsoft.sree.schedule.ScheduleTask other = sreeTask("t2", "admin");

      when(scheduleManager.getScheduleTask(createdId))
         .thenReturn(null, null, sreeTask("t1", "admin"), sreeTask("t1", "admin"));
      when(scheduleManager.getScheduleTask("t2")).thenReturn(other, other, other, other);
      when(scheduleGateway.hasDeletePermission(createdId, user)).thenReturn(true);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      // t2's own delete attempt (forward pass) succeeds mechanically but fails to verify (still
      // "exists" per the getScheduleTask stub above) -- only createdId's ROLLBACK throws.
      doNothing().when(scheduleGateway).removeScheduleTask(eq("t2"), any(), eq(user));
      doThrow(new IllegalStateException("cannot delete")).when(scheduleGateway)
         .removeScheduleTask(eq(createdId), any(), eq(user));

      ScheduleApplyRequest req = applyRequest("two changes", createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals(createdId, result.rollbackFailures().get(0).property());
   }

   // -------------------------------------------------------------------------
   // bug #80355 (P6, sibling of #80085): a delete whose post-write read-back fails must only be a
   // clean "rolled back" when the task still looks exactly as before; otherwise the removal took
   // (at least partly) effect and no undo was queued for it.
   // -------------------------------------------------------------------------

   @Test void deleteWhoseReadBackFindsAModifiedTaskReportsRollbackFailed() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask modified = sreeTask("t1", "admin");
      modified.setEnabled(false);
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing)   // apply: capture before-state
         .thenReturn(modified);  // apply: verify -- still there, but not as it was
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("delete a task", deleteChange("t1")), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(AdminChangeRecord.STATUS_FAILED, result.results().get(0).status());
      assertNotNull(result.rollbackFailures());
      assertEquals("t1", result.rollbackFailures().get(0).property());
   }

   @Test void deleteWhoseReadBackFindsTheTaskUntouchedStaysRolledBack() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(existing);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("delete a task", deleteChange("t1")), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   // -------------------------------------------------------------------------
   // Bug #82274: update, and a create in a folder
   // -------------------------------------------------------------------------

   private static ScheduleChangeRequest updateChange(String taskId, ScheduleTaskPatch patch) {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_UPDATE);
      change.setTaskId(taskId);
      change.setPatch(patch);
      return change;
   }

   private static ScheduleTaskPatch disablePatch() {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);
      return patch;
   }

   private static inetsoft.sree.schedule.ScheduleTask disabledTask() {
      inetsoft.sree.schedule.ScheduleTask task = sreeTask("t1", "admin");
      task.setEnabled(false);
      return task;
   }

   @Test void appliesAnUpdateAndReportsAppliedWhenTheStoredTaskIsThePlannedOne() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      ScheduleTaskPatch patch = disablePatch();
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)         // preview
         .thenReturn(existing)         // re-resolve inside apply()
         .thenReturn(existing)         // apply: capture before-state
         .thenReturn(disabledTask());  // apply: verify
      when(scheduleGateway.buildUpdatedTask(eq("t1"), eq(patch), any(), eq(user)))
         .thenAnswer(inv -> disabledTask());
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("disable a task", updateChange("t1", patch)),
                                         user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway).storeUpdatedTask(eq("t1"), any(), eq(user));
      verify(scheduleGateway, never()).removeScheduleTask(anyString(), any(), any());
      verify(scheduleGateway, never()).addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(),
         anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
   }

   // An update whose stored result is not the planned task is restored from the pre-image captured
   // during apply, in the folder the task was in.
   @Test void restoresThePreImageWhenTheStoredTaskIsNotThePlannedOne() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      ScheduleTaskPatch patch = disablePatch();
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing)   // apply: capture before-state
         .thenReturn(existing)   // apply: verify, still enabled so not the planned task
         .thenReturn(existing);  // rollback verify, the pre-image is back
      when(scheduleGateway.buildUpdatedTask(eq("t1"), eq(patch), any(), eq(user)))
         .thenAnswer(inv -> disabledTask());
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("disable a task", updateChange("t1", patch)),
                                         user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> restored =
         ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq("t1"), restored.capture(), any(), eq(user), any());
      assertTrue(restored.getValue().isEnabled());
      assertNotSame(existing, restored.getValue());
   }

   @Test void reportsRollbackFailedWhenTheRestoredTaskIsNotThePreImage() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      ScheduleTaskPatch patch = disablePatch();
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)
         .thenReturn(existing)
         .thenReturn(existing)
         .thenReturn(existing)           // verify: not the planned task
         .thenReturn(disabledTask());    // rollback verify: still not the pre-image
      when(scheduleGateway.buildUpdatedTask(eq("t1"), eq(patch), any(), eq(user)))
         .thenAnswer(inv -> disabledTask());
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("disable a task", updateChange("t1", patch)),
                                         user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertFalse(result.rollbackFailures().isEmpty());
   }

   private static inetsoft.sree.schedule.ScheduleTask zonedTask(String zone, boolean enabled) {
      inetsoft.sree.schedule.ScheduleTask task = sreeTask("t1", "admin");
      task.setTimeZone(zone);
      task.setEnabled(enabled);
      return task;
   }

   private void applyZoneUpdate(inetsoft.sree.schedule.ScheduleTask existing,
                                ScheduleTaskPatch patch, inetsoft.sree.schedule.ScheduleTask planned,
                                inetsoft.sree.schedule.ScheduleTask readBack, String expectedStatus)
      throws Exception
   {
      lenient().when(scheduleGateway.previewConditionResets(anyList(), eq(user)))
         .thenReturn(java.util.Collections.singletonList(null));
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)    // preview
         .thenReturn(existing)    // re-resolve inside apply()
         .thenReturn(existing)    // apply: capture before-state
         .thenReturn(readBack)    // apply: verify
         .thenReturn(existing);   // rollback verify, when there is one
      when(scheduleGateway.buildUpdatedTask(eq("t1"), eq(patch), any(), eq(user)))
         .thenAnswer(inv -> planned);
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      ApplyResult result = service.apply(applyRequest("zone", updateChange("t1", patch)), user, null);

      assertEquals(expectedStatus, result.status());
   }

   private inetsoft.sree.schedule.ScheduleTask storedByUpdate() throws Exception {
      ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> stored =
         ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleGateway).storeUpdatedTask(eq("t1"), stored.capture(), eq(user));
      return stored.getValue();
   }

   @Test void updateSetsTheTaskTimeZoneAndVerifiesItAgainstThePlan() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("Asia/Tokyo");

      applyZoneUpdate(zonedTask(null, true), patch, zonedTask("Asia/Tokyo", true),
                      zonedTask("Asia/Tokyo", true), AdminChangesetApplyService.STATUS_APPLIED);

      assertEquals("Asia/Tokyo", storedByUpdate().getTimeZone());
   }

   @Test void updateClearsTheTaskTimeZoneAndVerifiesItAgainstThePlan() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("");

      applyZoneUpdate(zonedTask("Asia/Tokyo", true), patch, zonedTask(null, true),
                      zonedTask(null, true), AdminChangesetApplyService.STATUS_APPLIED);

      assertNull(storedByUpdate().getTimeZone());
   }

   // C1: a patch that does not name the zone keeps it, also when it replaces the conditions
   @Test void updateWithoutATimeZoneKeepsTheStoredZoneAcrossAConditionReplacement() throws Exception {
      inetsoft.web.api.schedule.TimeCondition time = new inetsoft.web.api.schedule.TimeCondition();
      time.setHour(5);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);
      patch.setConditions(List.of(time));

      applyZoneUpdate(zonedTask("Europe/Paris", true), patch, zonedTask("Europe/Paris", false),
                      zonedTask("Europe/Paris", false), AdminChangesetApplyService.STATUS_APPLIED);

      assertEquals("Europe/Paris", storedByUpdate().getTimeZone());
   }

   // the zone is part of the pre-image: a failed verification puts the original zone back
   @Test void rollbackOfAnUpdateRestoresTheOriginalTaskTimeZone() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("Asia/Tokyo");
      inetsoft.sree.schedule.ScheduleTask existing = zonedTask("Europe/Paris", true);

      // the store reads back the old zone, so it is not the planned task
      applyZoneUpdate(existing, patch, zonedTask("Asia/Tokyo", true), existing,
                      AdminChangesetApplyService.STATUS_ROLLED_BACK);

      ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> restored =
         ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq("t1"), restored.capture(), any(), eq(user), any());
      assertEquals("Europe/Paris", restored.getValue().getTimeZone());
      assertNotSame(existing, restored.getValue());
   }

   @Test void createsInTheFolderTheChangeNames() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      ScheduleChangeRequest change = createChange(spec);
      change.setPath("Finance/Reports");
      inetsoft.uql.asset.AssetEntry folder = new inetsoft.uql.asset.AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE,
         inetsoft.uql.asset.AssetEntry.Type.SCHEDULE_TASK_FOLDER, "Finance/Reports", null);
      when(scheduleGateway.resolveCreateFolder("Finance/Reports", user)).thenReturn(folder);
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ApplyResult result = service.apply(applyRequest("create in a folder", change), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), eq(folder), any(), any(), eq(user));
   }

   private ScheduleApplyRequest applyRequest(String task, ScheduleChangeRequest... changes)
      throws Exception
   {
      ScheduleChangePlanRequest probe = new ScheduleChangePlanRequest();
      probe.setTask(task);
      probe.setChanges(List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      ScheduleApplyRequest req = new ScheduleApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   /**
    * Builds an apply request whose taskToken was issued for a DIFFERENT task string than the one
    * this request's own {@code task} field carries -- the shape a caller previewing an honest
    * description then applying with different text produces. Mirrors {@code
    * AdminChangesetApplyServiceTest#requestWithDivergentApplyTask}.
    */
   private ScheduleApplyRequest requestWithDivergentApplyTask(String previewTask, String applyTask,
                                                               ScheduleChangeRequest... changes)
      throws Exception
   {
      ScheduleChangePlanRequest preview = new ScheduleChangePlanRequest();
      preview.setTask(previewTask);
      preview.setChanges(List.of(changes));
      ResolvedPlan previewed = planService.resolve(preview, user);

      ScheduleApplyRequest req = new ScheduleApplyRequest();
      req.setTask(applyTask);
      req.setChanges(List.of(changes));
      req.setPlanHash(previewed.planHash());
      req.setTaskToken(previewed.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private static ScheduleChangeRequest createChange(CreateScheduleTaskRequest spec) {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_CREATE);
      change.setSpec(spec);
      return change;
   }

   private static ScheduleChangeRequest deleteChange(String taskId) {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_DELETE);
      change.setTaskId(taskId);
      return change;
   }

   private static CreateScheduleTaskRequest createSpec(String name, String owner) {
      CreateScheduleTaskRequest spec = new CreateScheduleTaskRequest();
      spec.setName(name);
      spec.setOwner(new IdentityID(owner, "host-org"));
      spec.setEnabled(true);
      TimeCondition condition = new TimeCondition();
      condition.setHour(9);
      condition.setMinute(0);
      spec.setConditions(List.of(condition));
      return spec;
   }

   private static inetsoft.sree.schedule.ScheduleTask sreeTask(String name, String owner) {
      inetsoft.sree.schedule.ScheduleTask task = new inetsoft.sree.schedule.ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }

   @Test void createPassesTheTaskTimeZoneToTheGateway() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      ScheduleChangeRequest change = createChange(spec);
      change.setTimeZone("Asia/Tokyo");
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ApplyResult result = service.apply(applyRequest("create with a zone", change), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), any(), eq("Asia/Tokyo"), any(), eq(user));
   }

   @Test void createWithoutATimeZonePassesNone() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      service.apply(applyRequest("create", createChange(spec)), user, null);

      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any(), eq(user));
   }

   // -------------------------------------------------------------------------
   // email zip password (Bug #82325, G1)
   // -------------------------------------------------------------------------

   private static EmailZipPasswordSpec zipSpec(int index, String password) {
      EmailZipPasswordSpec spec = new EmailZipPasswordSpec();
      spec.setActionIndex(index);
      spec.setPassword(password);
      return spec;
   }

   private static inetsoft.web.api.schedule.ViewsheetAction zippedAction(boolean emailZip) {
      inetsoft.web.api.schedule.ViewsheetAction action =
         new inetsoft.web.api.schedule.ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Sales Summary^host-org");
      action.setEmails(List.of("qa@example.com"));
      action.setEmailZip(emailZip);
      return action;
   }

   @Test void aCreateHandsItsZipPasswordsToTheGateway() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(zippedAction(true)));
      ScheduleChangeRequest change = createChange(spec);
      EmailZipPasswordSpec entry = zipSpec(0, "PW-PLACEHOLDER-ZIP-1");
      change.setEmailZipPasswords(List.of(entry));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);

      ApplyResult result = service.apply(applyRequest("create a zipped task", change), user, null);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      // both the no-write save check and the write carry the entries
      verify(scheduleGateway, atLeastOnce()).checkAddScheduleTaskSavable(any(), anyBoolean(),
         anyBoolean(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(),
         eq(List.of(entry)), eq(user));
      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(List.of(entry)),
         eq(user));
      assertFalse(result.toString().contains("PW-PLACEHOLDER-ZIP-1"));
   }

   // C2: a create without entries passes none
   @Test void aCreateWithoutZipPasswordsPassesNone() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)
         .thenReturn(null)
         .thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      service.apply(applyRequest("create a task", createChange(spec)), user, null);

      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull(), eq(user));
   }

   @Test void aCreateWithAZipPasswordOnAnActionWithoutEmailZipIsRefusedBeforeAnyWrite()
      throws Exception
   {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(zippedAction(false)));
      ScheduleChangeRequest change = createChange(spec);
      change.setEmailZipPasswords(List.of(zipSpec(0, "PW-PLACEHOLDER-ZIP-1")));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      lenient().when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      lenient().when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> applyRequest("create a zipped task", change));

      assertTrue(ex.getMessage().contains("emailZip"), ex.getMessage());
      assertFalse(ex.getMessage().contains("PW-PLACEHOLDER-ZIP-1"));
      verify(scheduleGateway, never()).addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(),
         anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
   }

   @Test void anUpdateHandsItsZipPasswordsToTheGatewayInThePatch() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)         // preview
         .thenReturn(existing)         // re-resolve inside apply()
         .thenReturn(existing)         // apply: capture before-state
         .thenReturn(disabledTask());  // apply: verify
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(zippedAction(true)));
      patch.setEmailZipPasswords(List.of(zipSpec(0, "PW-PLACEHOLDER-ZIP-1")));
      when(scheduleGateway.buildUpdatedTask(eq("t1"), eq(patch), any(), eq(user)))
         .thenAnswer(inv -> disabledTask());
      lenient().when(backupService.backup(anyString())).thenReturn("snap-ref");

      service.apply(applyRequest("zip an action", updateChange("t1", patch)), user, null);

      org.mockito.ArgumentCaptor<ScheduleTaskPatch> captor =
         org.mockito.ArgumentCaptor.forClass(ScheduleTaskPatch.class);
      verify(scheduleGateway, atLeastOnce()).buildUpdatedTask(eq("t1"), captor.capture(), any(),
                                                              eq(user));
      assertTrue(captor.getAllValues().stream().allMatch(p -> p.getEmailZipPasswords() != null &&
         p.getEmailZipPasswords().get(0).getActionIndex() == 0));
   }
}
