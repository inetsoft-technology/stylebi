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
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("snap-ref", result.backupRef());
      assertNull(result.rollbackFailures());
      verify(scheduleGateway).addScheduleTask(any(), eq(true), anyBoolean(), anyLong(), anyLong(),
         any(), any(), any(), any(), any(), any(), any(), eq(user));
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
      when(scheduleGateway.getScheduleTask(eq("t1"), any(), eq(user)))
         .thenReturn(dtoTask("t1", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t1"), any(), eq(user)))
         .thenReturn(new ScheduleConditionList());
      when(scheduleGateway.getTaskActions(eq("t1"), any(), eq(user)))
         .thenReturn(new ScheduleActionList());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
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
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: create t1", "totally different apply-time text", createChange(spec));

      ApplyResult result = service.apply(req, user);

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
      when(scheduleGateway.getScheduleTask(eq("t1"), any(), eq(user)))
         .thenReturn(dtoTask("t1", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t1"), any(), eq(user)))
         .thenReturn(new ScheduleConditionList());
      when(scheduleGateway.getTaskActions(eq("t1"), any(), eq(user)))
         .thenReturn(new ScheduleActionList());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: delete t1", "totally different apply-time text", deleteChange("t1"));

      ApplyResult result = service.apply(req, user);

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
                  () -> service.apply(req, user));
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), eq(user));
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
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
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), eq(user));
   }

   @Test void rejectsABlankTaskToken() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));
      req.setTaskToken("   ");

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
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
         () -> service.apply(req, user));
      verify(scheduleGateway, never())
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), eq(user));
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
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      doThrow(new IllegalStateException("boom")).when(scheduleGateway)
         .addScheduleTask(any(), anyBoolean(), anyBoolean(), anyLong(), anyLong(), any(), any(),
                          any(), any(), any(), any(), any(), eq(user));

      ScheduleApplyRequest req = applyRequest("create a task", createChange(spec));

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals(taskId, result.rollbackFailures().get(0).property());
   }

   // captureSpec is read-only and runs strictly BEFORE the mutating removeScheduleTask call --
   // a throw there proves nothing was mutated, unlike throwMidApplyIsReportedAsUnknownStateAndRollbackFailed
   // above (whose throw comes FROM the mutating call itself). Must not be folded into the same
   // STATUS_ROLLBACK_FAILED/unknown-state bucket as that case.
   @Test void deletePreflightCaptureFailureIsReportedAsRolledBackNotRollbackFailed() throws Exception {
      inetsoft.sree.schedule.ScheduleTask existing = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1"))
         .thenReturn(existing)   // preview
         .thenReturn(existing)   // re-resolve inside apply()
         .thenReturn(existing);  // apply: capture before-state
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(scheduleGateway.getScheduleTask(eq("t1"), any(), eq(user)))
         .thenReturn(dtoTask("t1", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t1"), any(), eq(user)))
         .thenThrow(new NullPointerException("boom"));
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("delete a task", deleteChange("t1"));

      ApplyResult result = service.apply(req, user);

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
      when(scheduleGateway.getScheduleTask(eq("t2"), any(), eq(user)))
         .thenReturn(dtoTask("t2", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleConditionList());
      when(scheduleGateway.getTaskActions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleActionList());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: two changes", "totally different apply-time text",
         createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user);

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
      // captureSpec's three round trips, so the delete's own attempt reaches (and fails) its
      // existence verify rather than NPE-ing on an unstubbed read.
      when(scheduleGateway.getScheduleTask(eq("t2"), any(), eq(user)))
         .thenReturn(dtoTask("t2", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleConditionList());
      when(scheduleGateway.getTaskActions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleActionList());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleApplyRequest req = applyRequest("two changes", createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user);

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
      when(scheduleGateway.getScheduleTask(eq("t2"), any(), eq(user)))
         .thenReturn(dtoTask("t2", "admin"));
      when(scheduleGateway.getTaskConditions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleConditionList());
      when(scheduleGateway.getTaskActions(eq("t2"), any(), eq(user)))
         .thenReturn(new ScheduleActionList());
      when(backupService.backup(anyString())).thenReturn("snap-ref");
      // t2's own delete attempt (forward pass) succeeds mechanically but fails to verify (still
      // "exists" per the getScheduleTask stub above) -- only createdId's ROLLBACK throws.
      doNothing().when(scheduleGateway).removeScheduleTask(eq("t2"), any(), eq(user));
      doThrow(new IllegalStateException("cannot delete")).when(scheduleGateway)
         .removeScheduleTask(eq(createdId), any(), eq(user));

      ScheduleApplyRequest req = applyRequest("two changes", createChange(spec), deleteChange("t2"));

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals(createdId, result.rollbackFailures().get(0).property());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

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

   private static ScheduleTask dtoTask(String name, String owner) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }
}
