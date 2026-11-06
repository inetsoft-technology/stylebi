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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the staging-cache-mutation bug the round-1 PR review flagged: {@code
 * ScheduleTaskTransferService}'s staging cache hands back the SAME {@code ScheduleTask} instance
 * to every preview/apply call against a given {@code stagingToken}, so {@code
 * ScheduleTaskImportChangesetApplyService#applyOne} must isolate whatever it mutates (the
 * per-action {@code linkURI} rewrite, in particular) from that cached instance -- otherwise a
 * preview taken AFTER an apply shows the apply's own rewrite already baked into {@code
 * proposedValue}, which a preview must never do.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleTaskImportChangesetApplyServiceTest {
   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleService scheduleService;
   @Mock private AdminScheduleGateway scheduleGateway;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;

   private ScheduleTaskTransferService transferService;
   private ScheduleTaskImportChangePlanService planService;
   private ScheduleTaskImportChangesetApplyService applyService;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private Audit auditMock;

   @BeforeEach void setUp() {
      transferService = new ScheduleTaskTransferService(scheduleManager, scheduleService);
      planService = new ScheduleTaskImportChangePlanService(transferService, scheduleManager,
                                                       scheduleTaskService, scheduleGateway);
      applyService = new ScheduleTaskImportChangesetApplyService(planService, transferService,
         scheduleManager, scheduleGateway, scheduleTaskService, backupService);

      // Same rationale as ScheduleChangesetApplyServiceTest's own setUp: writeAudit's
      // Tool.getHost() falls through to SreeEnv when unset.
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            // ViewsheetAction.writeXML always emits an EmailInfo/<MailTo> element, whose own
            // password field round-trips through here even when blank (no email delivery
            // configured) -- only a real taskToken-shaped value needs the TKN: contract below.
            if(s.isEmpty()) {
               return s;
            }

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });
      // Real Tool.replaceLocalhost touches Tool.getIP()/SreeEnv/network interfaces -- not what
      // this test is about, so pass the linkURI through unchanged.
      tool.when(() -> Tool.replaceLocalhost(anyString()))
         .thenAnswer(inv -> inv.getArgument(0));

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
   // Finding 1 regression: apply must not corrupt the staging cache
   // -------------------------------------------------------------------------

   @Test void applyDoesNotLeakItsLinkUriRewriteIntoTheStagedCacheForALaterPreview() throws Exception {
      String originalLinkUri = "http://source-server/";
      ScheduleTask sourceTask = taskWithViewsheetAction("t1", originalLinkUri);
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);

      // Preview #1, before apply.
      ScheduleTaskImportPlanRequest preview1Req =
         planRequest(stagingToken, "preview before apply", importChange(taskId, false));
      ResolvedPlan preview1 = planService.resolve(preview1Req, user, "http://different-server/");
      String proposedBeforeApply = preview1.changes().get(0).proposedValue();

      // Apply from a DIFFERENT server, so updateTaskLinkUri has something to rewrite.
      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)                         // re-resolve inside apply()
         .thenReturn(null)                         // applyOne's existing-task check
         .thenReturn(sreeTask("t1", "admin"))       // apply-time verify
         .thenReturn(sreeTask("t1", "admin"));      // preview #2's existing-task check
      ScheduleTaskImportApplyRequest applyReq = applyRequest(preview1Req, preview1);

      ScheduleTaskImportApplyResult result =
         applyService.apply(applyReq, user, "http://different-server/");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());

      // The cached staged task must still be untouched by the apply's own linkURI rewrite: proof
      // #1, directly on the cached instance...
      ScheduleTask stillCached = transferService.requireStagedTask(stagingToken, taskId);
      ViewsheetAction cachedAction = (ViewsheetAction) stillCached.getAction(0);
      assertEquals(originalLinkUri, cachedAction.getLinkURI(),
         "apply must not mutate the linkURI of the action instance shared by the staging cache");

      // ...and proof #2, the actual user-visible symptom the review reported: a SECOND preview
      // (now requiring overwrite:true, since apply created the task) must reproduce the exact
      // same proposedValue as the first preview, not the apply's own rewrite baked in.
      ScheduleTaskImportPlanRequest preview2Req =
         planRequest(stagingToken, "preview after apply", importChange(taskId, true));
      ResolvedPlan preview2 = planService.resolve(preview2Req, user, "http://different-server/");
      String proposedAfterApply = preview2.changes().get(0).proposedValue();

      assertEquals(proposedBeforeApply, proposedAfterApply,
         "a preview taken after apply must not show apply's own mutation baked into proposedValue");
   }

   // -------------------------------------------------------------------------
   // Finding 6 regression (round-2 review): deepCopy() must isolate CONDITIONS too, not just
   // actions -- the specific gap that reusing ScheduleTask.copyScheduleTask() (deep-copies actions
   // via an XML round-trip, but only Vector.clone()s conditions) would silently reopen.
   // -------------------------------------------------------------------------

   @Test void applyDoesNotLeakSanitizeConditionsMutationIntoTheStagedCache() throws Exception {
      int originalHour = 9;
      ScheduleTask sourceTask = taskWithViewsheetAction("t2", "http://source-server/");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);

      // Simulate what the REAL ScheduleTaskService.sanitizeConditions does: mutate a TimeCondition
      // field in place on the "staged" task it is handed (e.g. clamping the hour back to a
      // permitted default). The mock has no other stubbing, so this is the ONLY behavior under
      // test here -- Finding 1's regression test above already covers the linkURI/action path.
      doAnswer(invocation -> {
         ScheduleTask stagedArg = invocation.getArgument(0);
         TimeCondition tc = (TimeCondition) stagedArg.getCondition(0);
         tc.setHour(1);
         tc.setMinute(30);
         return null;
      }).when(scheduleTaskService).sanitizeConditions(any(ScheduleTask.class), any(ScheduleTask.class), eq(user));

      // Preview, before any stubbing -- like the Finding 1 test above, this relies on the mock's
      // default null return (no existing task yet), matching applyOne's own "re-resolve fresh at
      // apply time" contract.
      ScheduleTaskImportPlanRequest planReq =
         planRequest(stagingToken, "apply", importChange(taskId, false));
      ResolvedPlan plan = planService.resolve(planReq, user, "http://source-server/");
      ScheduleTaskImportApplyRequest applyReq = applyRequest(planReq, plan);

      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null)                          // re-resolve inside apply()
         .thenReturn(null)                          // applyOne's existing-task check
         .thenReturn(sreeTask("t2", "admin"));       // apply-time verify

      ScheduleTaskImportApplyResult result =
         applyService.apply(applyReq, user, "http://source-server/");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());

      // The cached staged task's OWN condition instance must be untouched by sanitizeConditions'
      // in-place mutation of the deep copy passed into it -- this is exactly what a shallow
      // clone()-based copy (or ScheduleTask.copyScheduleTask(), which round-trips actions but not
      // conditions) would fail to isolate.
      ScheduleTask stillCached = transferService.requireStagedTask(stagingToken, taskId);
      TimeCondition cachedCondition = (TimeCondition) stillCached.getCondition(0);
      assertEquals(originalHour, cachedCondition.getHour(),
         "apply must not mutate the condition instance shared by the staging cache");
   }

   // -------------------------------------------------------------------------
   // Bug #79576: the plan must show the task as apply will store it
   // -------------------------------------------------------------------------

   @Test void planProposedValueCarriesTheRewrittenLinkAndMatchesApplyAfter() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t3", "http://old-test-server:8080/sree");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      ScheduleTaskImportPlanRequest req =
         planRequest(stagingToken, "link", importChange(taskId, false));

      ResolvedPlan plan = planService.resolve(req, user, "http://new-server:8080/");
      String proposed = plan.changes().get(0).proposedValue();

      assertTrue(proposed.contains("http://new-server:8080/"), proposed);
      assertFalse(proposed.contains("old-test-server"), proposed);

      when(scheduleManager.getScheduleTask(taskId))
         .thenReturn(null).thenReturn(null).thenReturn(sreeTask("t3", "admin"));
      ScheduleTaskImportApplyResult result = applyService.apply(
         applyRequest(req, plan), user, "http://new-server:8080/");

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(proposed, result.results().get(0).after());
   }

   @Test void planReflectsSanitizedConditionsAndPlanHashDependsOnLinkUri() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t4", "http://old-test-server:8080/sree");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      ScheduleTaskImportPlanRequest req =
         planRequest(stagingToken, "sanitize", importChange(taskId, false));
      String unsanitized =
         planService.resolve(req, user, "http://a/").changes().get(0).proposedValue();

      doAnswer(invocation -> {
         ScheduleTask stagedArg = invocation.getArgument(0);
         ((TimeCondition) stagedArg.getCondition(0)).setHour(1);
         return null;
      }).when(scheduleTaskService).sanitizeConditions(any(ScheduleTask.class), any(ScheduleTask.class), eq(user));

      ResolvedPlan a1 = planService.resolve(req, user, "http://a/");
      ResolvedPlan a2 = planService.resolve(req, user, "http://a/");
      ResolvedPlan b = planService.resolve(req, user, "http://b/");

      assertEquals(a1.planHash(), a2.planHash());
      assertNotEquals(a1.planHash(), b.planHash());
      assertNotEquals(unsanitized, a1.changes().get(0).proposedValue());
   }

   @Test void planRefusesFieldNamedWhenActionSanitizeRejectsTheTask() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t5", "http://x/");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      doThrow(new inetsoft.util.MessageException("secret id not permitted"))
         .when(scheduleTaskService).sanitizeAction(any(), any(), eq(user));
      ScheduleTaskImportPlanRequest req =
         planRequest(stagingToken, "reject", importChange(taskId, false));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(req, user, "http://x/"));
      assertTrue(e.getMessage().startsWith("changes[0].taskId"), e.getMessage());
      assertTrue(e.getMessage().contains("secret id not permitted"), e.getMessage());
   }

   // -------------------------------------------------------------------------
   // Bug #80355 (P1), sibling of #79974: a refusal the save makes before its first write must end
   // rolled-back, not rollback-failed with a spurious "state unknown" failure.
   // -------------------------------------------------------------------------

   @Test void refusalBeforeTheWriteEndsRolledBackAndNeverSaves() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t6", "http://x/");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      ScheduleTaskImportPlanRequest planReq =
         planRequest(stagingToken, "refuse", importChange(taskId, false));
      ResolvedPlan plan = planService.resolve(planReq, user, "http://x/");
      ScheduleTaskImportApplyRequest applyReq = applyRequest(planReq, plan);

      // preview was clean (default no-op); the refusal arrives only at applyOne's own check
      AtomicInteger calls = new AtomicInteger();
      doAnswer(inv -> {
         // 1st call is apply's re-resolve of the plan, 2nd is applyOne's own pre-write check
         if(calls.incrementAndGet() >= 2) {
            throw new SecurityException("principal cannot read viewsheet vs1");
         }

         return null;
      }).when(scheduleManager).checkScheduleTaskSave(eq(taskId), any(), eq(user));

      ScheduleTaskImportApplyResult result = applyService.apply(applyReq, user, "http://x/");

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any(), any());
      assertTrue(result.results().get(0).error().contains("cannot read viewsheet"),
                 String.valueOf(result.results().get(0).error()));
   }

   @Test void mixedPlanWithALaterRefusalUndoesTheEarlierWriteAndIsRolledBack() throws Exception {
      ScheduleTask a = taskWithViewsheetAction("tA", "http://x/");
      ScheduleTask b = taskWithViewsheetAction("tB", "http://x/");
      String idA = a.getTaskId();
      String idB = b.getTaskId();
      String stagingToken = stage(a, b);
      ScheduleTaskImportPlanRequest planReq = planRequest(stagingToken, "mixed",
         importChange(idA, false), importChange(idB, false));
      ResolvedPlan plan = planService.resolve(planReq, user, "http://x/");
      ScheduleTaskImportApplyRequest applyReq = applyRequest(planReq, plan);

      AtomicBoolean aStored = new AtomicBoolean();
      when(scheduleManager.getScheduleTask(idA)).thenAnswer(
         inv -> aStored.get() ? sreeTask("tA", "admin") : null);
      lenient().when(scheduleManager.getScheduleTask(idB)).thenReturn(null);
      doAnswer(inv -> {
         aStored.set(true);
         return null;
      }).when(scheduleManager).setScheduleTask(eq(idA), any(), any(), any());
      doAnswer(inv -> {
         aStored.set(false);
         return null;
      }).when(scheduleGateway).removeScheduleTask(eq(idA), any(), any());

      AtomicInteger bCalls = new AtomicInteger();
      lenient().doAnswer(inv -> {
         // 1st call is apply's re-resolve, 2nd is applyOne's own pre-write check
         if(bCalls.incrementAndGet() >= 2) {
            throw new SecurityException("principal cannot read viewsheet vs1");
         }

         return null;
      }).when(scheduleManager).checkScheduleTaskSave(eq(idB), any(), eq(user));

      ScheduleTaskImportApplyResult result = applyService.apply(applyReq, user, "http://x/");

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(scheduleManager, never()).setScheduleTask(eq(idB), any(), any(), any());
      verify(scheduleGateway).removeScheduleTask(eq(idA), any(), any());
   }

   // -------------------------------------------------------------------------
   // Bug #80355 (P6), sibling of #80085: a read-back that fails after setScheduleTask returned must
   // not be reported as rolled back when the state differs from before the write.
   // -------------------------------------------------------------------------

   @Test void overwriteWhoseReadBackFindsTheTaskGoneReportsRollbackFailed() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t7", "http://x/");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      AtomicBoolean written = new AtomicBoolean();
      when(scheduleManager.getScheduleTask(taskId)).thenAnswer(
         inv -> written.get() ? null : sreeTask("t7", "admin"));
      doAnswer(inv -> {
         written.set(true);
         return null;
      }).when(scheduleManager).setScheduleTask(eq(taskId), any(), any(), any());
      ScheduleTaskImportPlanRequest planReq =
         planRequest(stagingToken, "overwrite", importChange(taskId, true));
      ResolvedPlan plan = planService.resolve(planReq, user, "http://x/");

      ScheduleTaskImportApplyResult result =
         applyService.apply(applyRequest(planReq, plan), user, "http://x/");

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals("schedule task not found after import", result.results().get(0).error());
      assertNotNull(result.rollbackFailures());
      assertEquals("schedule-import:" + taskId, result.rollbackFailures().get(0).property());
   }

   @Test void createWhoseReadBackFindsNothingStaysRolledBack() throws Exception {
      ScheduleTask sourceTask = taskWithViewsheetAction("t8", "http://x/");
      String taskId = sourceTask.getTaskId();
      String stagingToken = stage(sourceTask);
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      ScheduleTaskImportPlanRequest planReq =
         planRequest(stagingToken, "create", importChange(taskId, false));
      ResolvedPlan plan = planService.resolve(planReq, user, "http://x/");

      ScheduleTaskImportApplyResult result =
         applyService.apply(applyRequest(planReq, plan), user, "http://x/");

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private String stage(ScheduleTask... tasks) throws Exception {
      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         for(ScheduleTask task : tasks) {
            task.writeXML(pw);
         }
      }

      String xml = "<schedule>" + sw + "</schedule>";
      String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
      return transferService.stage(base64, user).stagingToken();
   }

   private static ScheduleTask taskWithViewsheetAction(String name, String linkUri) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID("admin", "host-org"));
      task.setEnabled(true);

      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      task.addCondition(condition);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setLinkURI(linkUri);
      task.addAction(action);

      return task;
   }

   private static ScheduleTask sreeTask(String name, String owner) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }

   private static ScheduleTaskImportPlanRequest planRequest(String stagingToken, String taskDesc,
                                                            ScheduleTaskImportChangeRequest... changes)
   {
      ScheduleTaskImportPlanRequest req = new ScheduleTaskImportPlanRequest();
      req.setTask(taskDesc);
      req.setStagingToken(stagingToken);
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScheduleTaskImportChangeRequest importChange(String taskId, boolean overwrite) {
      ScheduleTaskImportChangeRequest change = new ScheduleTaskImportChangeRequest();
      change.setTaskId(taskId);
      change.setOverwrite(overwrite);
      return change;
   }

   private static ScheduleTaskImportApplyRequest applyRequest(ScheduleTaskImportPlanRequest planReq,
                                                               ResolvedPlan plan)
   {
      ScheduleTaskImportApplyRequest req = new ScheduleTaskImportApplyRequest();
      req.setTask(planReq.getTask());
      req.setStagingToken(planReq.getStagingToken());
      req.setChanges(planReq.getChanges());
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved");
      req.setAcknowledgeOverwrite(true);
      return req;
   }
}
