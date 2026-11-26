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
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.sree.schedule.ServerPathInfo;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The plan hash is the review gate for schedule tasks the same way it is for properties (spec §5)
 * -- these tests focus on what is NEW here: a total (writeXML) projection rather than a scalar,
 * the two inverse-permission preflights that have no properties-area analog (spec §4), and the
 * unsupported-type refusals (spec §1).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleChangePlanServiceTest {
   @Mock private AdminScheduleGateway scheduleGateway;
   @Mock private ScheduleManager scheduleManager;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private ScheduleChangePlanService service;
   private MockedStatic<Tool> tool;
   private MockedStatic<OrganizationManager> orgStatic;

   @BeforeEach void setUp() {
      service = new ScheduleChangePlanService(scheduleGateway, scheduleManager);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      orgStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID(user)).thenReturn("host-org");
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
   }

   @AfterEach void tearDown() {
      orgStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ScheduleChangePlanRequest req = request("   ", List.of(deleteChange("t1")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      ScheduleChangePlanRequest req = request("do something", List.of());

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() throws Exception {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb("rename");
      change.setTaskId("t1");
      ScheduleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("verb"));
      assertTrue(ex.getMessage().contains("update"));
   }

   @Test void resolveThrowsOnDuplicateTaskId() throws Exception {
      lenient().when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      lenient().when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user)))
         .thenReturn(true);
      ScheduleChangePlanRequest req = request("task", List.of(deleteChange("t1"), deleteChange("t1")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // create
   // -------------------------------------------------------------------------

   @Test void resolveCreateThrowsWhenSpecMissing() {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_CREATE);
      ScheduleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveCreateThrowsOnUnsupportedConditionType() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(new CompletionCondition()));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("conditions"));
   }

   // bug: an "AT"-type condition with a null date sailed through preview and only failed at apply
   // time inside AdminScheduleGateway, where the generic exception handling misclassified the
   // failure as rollback-failed even though nothing had been written -- catch it here instead.
   @Test void resolveCreateThrowsOnAtConditionWithNullDate() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(atCondition(null)));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("date"));
      assertTrue(ex.getMessage().contains("AT"));
   }

   // positive control: an "AT" condition with a non-null date must still resolve successfully --
   // guards against the new check above being overly strict.
   @Test void resolveCreateSucceedsWithAtConditionAndNonNullDate() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(atCondition(java.time.OffsetDateTime.now())));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      ScheduleChangePlanRequest req = request("create a task", List.of(createChange(spec)));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
   }

   // bug #78902: an unknown time range name used to be dropped silently at apply time.
   @Test void resolveCreateThrowsOnUnknownTimeRange() {
      try(MockedStatic<TimeRange> ranges = definedRanges("Overnight", "Morning")) {
         TimeCondition second = timeCondition(10, 0);
         second.setTimeRange("Nope");
         CreateScheduleTaskRequest spec = createSpec("t1", "admin");
         spec.setConditions(List.of(timeCondition(9, 0), second));
         ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

         IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
         assertEquals("changes[0].spec.conditions[1].timeRange: \"Nope\" is not a defined " +
                      "time range (available: Overnight, Morning)", ex.getMessage());
      }
   }

   @Test void resolveCreateSaysNoneWhenNoTimeRangeIsDefined() {
      try(MockedStatic<TimeRange> ranges = definedRanges()) {
         TimeCondition condition = timeCondition(9, 0);
         condition.setTimeRange("Morning");
         CreateScheduleTaskRequest spec = createSpec("t1", "admin");
         spec.setConditions(List.of(condition));
         ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

         IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
         assertTrue(ex.getMessage().endsWith("(available: none)"));
      }
   }

   @Test void resolveCreateAcceptsDefinedBlankAndAbsentTimeRange() throws Exception {
      try(MockedStatic<TimeRange> ranges = definedRanges("Morning")) {
         TimeCondition defined = timeCondition(9, 0);
         defined.setTimeRange("Morning");
         TimeCondition blank = timeCondition(10, 0);
         blank.setTimeRange("");
         CreateScheduleTaskRequest spec = createSpec("t1", "admin");
         spec.setConditions(List.of(defined, blank, timeCondition(11, 0)));
         String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
         when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
         when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
         ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

         assertEquals(1, service.resolve(req, user).changes().size());
      }
   }

   private static MockedStatic<TimeRange> definedRanges(String... names) {
      MockedStatic<TimeRange> ranges = mockStatic(TimeRange.class);
      ranges.when(TimeRange::getTimeRanges).thenReturn(
         java.util.Arrays.stream(names)
            .map(n -> new TimeRange(n, "06:00", "12:00", false))
            .collect(java.util.stream.Collectors.toList()));
      return ranges;
   }

   @Test void resolveCreateThrowsOnUnsupportedActionType() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(new BatchAction()));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("actions"));
   }

   @Test void resolveCreateThrowsWhenTaskAlreadyExists() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("nightly-refresh", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(sreeTask("nightly-refresh", "admin"));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   // A create's rollback is a delete -- refusing the plan up front when the caller could not
   // perform that delete is the concrete instance of spec §4's per-verb parity requirement.
   @Test void resolveCreateThrowsWhenCallerLacksDeletePermissionForRollback() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(false);
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("DELETE"));
   }

   // Bug #79426, an execute-as identity that doesn't resolve is refused by apply; the plan must
   // refuse it too, naming the field, instead of planning a change apply can only throw on.
   @Test void resolveCreateThrowsWhenExecuteAsIdentityDoesNotResolve() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      ScheduleTask.ExecuteAsID executeAs = new ScheduleTask.ExecuteAsID(
         ScheduleTask.ExecuteAsID.Type.USER, new IdentityID("nouser", "host-org"));
      spec.setExecuteAsID(executeAs);
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      doThrow(new MessageException("The execute-as user nouser does not exist"))
         .when(scheduleGateway).requireExecuteAsResolves(spec.getOwner(), executeAs);
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("spec.executeAsID"));
      assertTrue(ex.getMessage().contains("does not exist"));
   }

   // Bug #79828, an owner that isn't an existing user is refused by apply; the plan must refuse it
   // too, naming the field, before the owner is turned into a task id.
   @Test void resolveCreateThrowsWhenOwnerIsNotAnExistingUser() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "no-such-user");
      doThrow(new MessageException("user \"no-such-user\" does not exist in organization " +
                                   "\"host-org\" (a group or role cannot own a task)"))
         .when(scheduleGateway).requireOwnerResolves(spec.getOwner(), user);
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("spec.owner"));
      assertTrue(ex.getMessage().contains("no-such-user"));
      verify(scheduleManager, never()).getScheduleTask(anyString());
   }

   // Bug #79974, the save refuses a task whose run principal can't read an action viewsheet; the
   // plan must refuse it too, naming the owner and viewsheet, instead of planning it.
   @Test void resolveCreateThrowsWhenSaveWouldBeRefused() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      doThrow(new inetsoft.web.security.auth.UnauthorizedAccessException(
         "Unauthorized access to viewsheet X"))
         .when(scheduleGateway).checkAddScheduleTaskSavable(any(), anyBoolean(), anyBoolean(),
            anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), eq(user));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(createChange(spec))), user));
      assertTrue(ex.getMessage().contains("spec"));
      assertTrue(ex.getMessage().contains("owner \"" + spec.getOwner().getName() + "\""));
      assertTrue(ex.getMessage().contains("Unauthorized access to viewsheet X"));
   }

   @Test void resolveCreateSucceedsWhenExecuteAsIdentityResolves() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      ScheduleTask.ExecuteAsID executeAs = new ScheduleTask.ExecuteAsID(
         ScheduleTask.ExecuteAsID.Type.USER, new IdentityID("someone", "host-org"));
      spec.setExecuteAsID(executeAs);
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      service.resolve(request("task", List.of(createChange(spec))), user);

      verify(scheduleGateway).requireExecuteAsResolves(spec.getOwner(), executeAs);
   }

   @Test void resolveCreateSucceedsWithNullCurrentAndNonNullProposed() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      ScheduleChangePlanRequest req = request("create a task", List.of(createChange(spec)));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
      PlanChange change = plan.changes().get(0);
      assertEquals(taskId, change.property());
      assertNull(change.currentValue());
      assertNotNull(change.proposedValue());
      assertTrue(change.proposedValue().contains("t1"));
      assertEquals("high", change.risk());
      assertEquals("storage", change.snapshotScope());
      // Bug #80650: a create-only plan makes a new object with a live undo -- no snapshot needed.
      assertFalse(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
      assertNotNull(plan.planHash());
   }

   // -------------------------------------------------------------------------
   // delete
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenTaskIdMissing() {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_DELETE);
      ScheduleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("taskId"));
   }

   @Test void resolveDeleteThrowsWhenSpecPresent() {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_DELETE);
      change.setTaskId("t1");
      change.setSpec(createSpec("t1", "admin"));
      ScheduleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveDeleteThrowsWhenTaskDoesNotExist() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(null);
      ScheduleChangePlanRequest req = request("task", List.of(deleteChange("t1")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no task exists"));
   }

   // Bug #76598: a built-in/internal task (or a synthesized data-cycle task) is removable=false
   // unconditionally -- the same flag that disables the Delete button in the EM task list -- and
   // must be refused before the owner-ADMIN check even runs, since no caller (not even a Site
   // Administrator, who bypasses that ADMIN check entirely) can delete it.
   @Test void resolveDeleteThrowsWhenTaskIsNotRemovable() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = sreeTask("__balance tasks__", "admin");
      task.setRemovable(false);
      when(scheduleManager.getScheduleTask("__balance tasks__")).thenReturn(task);
      ScheduleChangePlanRequest req = request("task", List.of(deleteChange("__balance tasks__")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("cannot be deleted"));
      verify(scheduleGateway, never()).hasOwnerAdminPermission(any(), any(), any());
   }

   // A delete's rollback is a re-create -- requires ADMIN over the owner, strictly stronger than
   // the DELETE the verb itself needs (spec §4's asymmetric-gap finding).
   @Test void resolveDeleteThrowsWhenCallerLacksOwnerAdminPermissionForRollback() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(false);
      ScheduleChangePlanRequest req = request("task", List.of(deleteChange("t1")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("ADMIN"));
   }

   @Test void resolveDeleteSucceedsWithNonNullCurrentAndNullProposed() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      ScheduleChangePlanRequest req = request("delete a task", List.of(deleteChange("t1")));

      ResolvedPlan plan = service.resolve(req, user);

      PlanChange change = plan.changes().get(0);
      assertEquals("t1", change.property());
      assertNotNull(change.currentValue());
      assertNull(change.proposedValue());
      // Bug #81073: the owner is in the caller's org and the save dry-run passes, so the rollback
      // provably re-creates the task and the whole-storage snapshot is skipped.
      assertFalse(plan.requiresStorageBackup());
      verify(scheduleManager).checkScheduleTaskSave(eq("t1"), any(), eq(user));
   }

   // Bug #81073: the rollback's owner-organization check re-reads the live (deleted) task, which a
   // dry run taken while the task is stored cannot predict, so an owner in another organization
   // than the caller's current one keeps the snapshot.
   @Test void resolveDeleteKeepsBackupWhenOwnerOrgDiffersFromCallerOrg() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(orgManager.getCurrentOrgID(user)).thenReturn("other-org");

      ResolvedPlan plan = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertTrue(plan.requiresStorageBackup());
      verify(scheduleManager, never()).checkScheduleTaskSave(any(), any(), any());
   }

   // A dry-run refusal (e.g. an action sheet of another organization) keeps the snapshot and must
   // not turn a valid delete into a refused plan.
   @Test void resolveDeleteKeepsBackupWhenSaveDryRunThrows() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      doThrow(new MessageException("sheet not readable"))
         .when(scheduleManager).checkScheduleTaskSave(eq("t1"), any(), eq(user));

      ResolvedPlan plan = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertEquals(1, plan.changes().size());
      assertTrue(plan.requiresStorageBackup());
   }

   @Test void resolveDeleteKeepsBackupWhenCallerOrgCannotBeResolved() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      when(orgManager.getCurrentOrgID(user)).thenThrow(new ClassCastException("not an XPrincipal"));

      ResolvedPlan plan = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertTrue(plan.requiresStorageBackup());
   }

   // One non-compensable delete keeps the snapshot for the whole plan (one snapshot per apply).
   @Test void resolveMixedCompensableAndNonCompensableDeleteKeepsStorageBackup() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleManager.getScheduleTask("t2")).thenReturn(sreeTask("t2", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      doThrow(new MessageException("refused"))
         .when(scheduleManager).checkScheduleTaskSave(eq("t2"), any(), eq(user));

      ResolvedPlan plan = service.resolve(
         request("two deletes", List.of(deleteChange("t1"), deleteChange("t2"))), user);

      assertTrue(plan.requiresStorageBackup());
   }

   @Test void resolveMixedCreateAndCompensableDeleteSkipsStorageBackup() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      String newId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      when(scheduleManager.getScheduleTask(newId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(newId, user)).thenReturn(true);
      when(scheduleManager.getScheduleTask("t2")).thenReturn(sreeTask("t2", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);
      ScheduleChangePlanRequest req =
         request("mixed", List.of(createChange(spec), deleteChange("t2")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(2, plan.changes().size());
      assertFalse(plan.requiresStorageBackup());
   }

   // -------------------------------------------------------------------------
   // hash stability -- closes the collision SpikeHashProbe found (spec §5)
   // -------------------------------------------------------------------------

   @Test void hashChangesWhenAConditionIsAdded() throws Exception {
      CreateScheduleTaskRequest approved = createSpec("t1", "admin");
      approved.setConditions(List.of(timeCondition(9, 0)));

      CreateScheduleTaskRequest drifted = createSpec("t1", "admin");
      drifted.setConditions(List.of(timeCondition(9, 0), timeCondition(23, 0)));

      String taskId = ScheduleManager.getTaskId(approved.getOwner().convertToKey(), approved.getName());
      lenient().when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      lenient().when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ResolvedPlan approvedPlan = service.resolve(request("t", List.of(createChange(approved))), user);
      ResolvedPlan driftedPlan = service.resolve(request("t", List.of(createChange(drifted))), user);

      assertNotEquals(approvedPlan.planHash(), driftedPlan.planHash());
   }

   @Test void hashIsStableForIdenticalRequests() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);

      ResolvedPlan first = service.resolve(request("delete", List.of(deleteChange("t1"))), user);
      ResolvedPlan second = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void issuesATaskTokenBoundToThePlanHash() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);

      ResolvedPlan plan = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertEquals("TKN:" + plan.planHash() + "delete", plan.taskToken());
   }

   // task is a free-text, audit-only label (bug 76445) -- a caller that does not replay it
   // byte-for-byte between preview and apply must not see a false planHash conflict.
   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);

      ResolvedPlan first = service.resolve(request("delete the nightly task", List.of(deleteChange("t1"))), user);
      ResolvedPlan second = service.resolve(request("remove nightly-refresh task", List.of(deleteChange("t1"))), user);

      assertEquals(first.planHash(), second.planHash());
   }

   // Bug 76726: a saveToServerFilePaths password is re-encrypted (fresh IV) on every writeXML
   // call, so a delete plan's hash must not depend on that ciphertext -- override the class's
   // deterministic Tool.encryptPassword stub with a genuinely varying one so this actually
   // exercises the bug rather than passing on the fixed "TKN:" stub alone.
   @Test void hashIsStableForDeleteWhenPasswordIsReencryptedBetweenCalls() throws Exception {
      AtomicInteger callCount = new AtomicInteger();
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "IV" + callCount.incrementAndGet() + ":" + inv.getArgument(0));

      inetsoft.sree.schedule.ScheduleTask task = sreeTask("t1", "admin");
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF,
         new ServerPathInfo("/exports/report.pdf", "scheduler", "s3cret"));
      task.addAction(action);

      when(scheduleManager.getScheduleTask("t1")).thenReturn(task);
      when(scheduleGateway.hasOwnerAdminPermission(any(), any(), eq(user))).thenReturn(true);

      ResolvedPlan first = service.resolve(request("delete", List.of(deleteChange("t1"))), user);
      ResolvedPlan second = service.resolve(request("delete", List.of(deleteChange("t1"))), user);

      assertEquals(first.planHash(), second.planHash());
      // Round 2 (sentinel-value refinement): the password is normalized to a fixed "SET" value
      // rather than stripped outright, so the fact that a password is saved must still survive
      // into currentValue -- not just the hash staying stable.
      assertTrue(first.changes().get(0).currentValue().contains("password=\"SET\""));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private ResolvedPlan resolveCreateOf(CreateScheduleTaskRequest spec) throws Exception {
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), spec.getName());
      lenient().when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      lenient().when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      return service.resolve(request("t", List.of(createChange(spec))), user);
   }

   private static TimeCondition everyHour(int hourEnd, float hourlyInterval) {
      TimeCondition condition = timeCondition(8, 0);
      condition.setType(TimeCondition.Type.EVERY_HOUR);
      condition.setHourEnd(hourEnd);
      condition.setHourlyInterval(hourlyInterval);
      condition.setDaysOfWeek(new int[]{2});
      return condition;
   }

   private static TimeCondition monthly(int dayOfWeek) {
      TimeCondition condition = timeCondition(9, 0);
      condition.setType(TimeCondition.Type.EVERY_MONTH);
      condition.setWeekOfMonth(2);
      condition.setDayOfWeek(dayOfWeek);
      condition.setMonthsOfYear(new int[]{0});
      return condition;
   }

   private static CreateScheduleTaskRequest specWith(TimeCondition condition) {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(condition));
      return spec;
   }

   // bug #79043
   @Test void everyHourWindowIsHashedAndShown() throws Exception {
      ResolvedPlan a = resolveCreateOf(specWith(everyHour(18, 2f)));
      ResolvedPlan b = resolveCreateOf(specWith(everyHour(23, 6f)));
      ResolvedPlan c = resolveCreateOf(specWith(everyHour(18, 6f)));

      assertNotEquals(a.planHash(), b.planHash());
      assertNotEquals(a.planHash(), c.planHash());
      String shown = a.changes().get(0).proposedValue();
      assertTrue(shown.contains("hourEnd=18"), shown);
      assertTrue(shown.contains("hourlyInterval=2.0"), shown);
      assertTrue(shown.contains("minuteEnd=0"), shown);
      assertTrue(shown.contains("secondEnd=0"), shown);
   }

   @Test void monthlyDayOfWeekIsHashed() throws Exception {
      ResolvedPlan tuesday = resolveCreateOf(specWith(monthly(3)));
      ResolvedPlan friday = resolveCreateOf(specWith(monthly(6)));

      assertNotEquals(tuesday.planHash(), friday.planHash());
      assertTrue(tuesday.changes().get(0).proposedValue().contains("dayOfWeek=3"));
   }

   // bug #79424
   private static final String[] CONDITION_FIELDS = {
      "hour", "minute", "second", "date", "interval", "daysOfWeek", "dayOfMonth", "weekOfMonth",
      "dayOfWeek", "monthsOfYear", "weekdayOnly", "hourEnd", "minuteEnd", "secondEnd",
      "hourlyInterval"
   };

   private static TimeCondition everyFieldSet(TimeCondition.Type type, int weekOfMonth) {
      TimeCondition condition = timeCondition(7, 5);
      condition.setType(type);
      condition.setSecond(9);
      condition.setInterval(3);
      condition.setDaysOfWeek(new int[]{2, 4});
      condition.setDayOfMonth(11);
      condition.setWeekOfMonth(weekOfMonth);
      condition.setDayOfWeek(5);
      condition.setMonthsOfYear(new int[]{1, 6});
      condition.setWeekdayOnly(true);
      condition.setHourEnd(18);
      condition.setMinuteEnd(17);
      condition.setSecondEnd(16);
      condition.setHourlyInterval(2.5f);

      if(type == TimeCondition.Type.AT) {
         condition.setDate(java.time.OffsetDateTime.parse("2030-01-02T03:04:05Z"));
      }

      return condition;
   }

   private void assertShowsOnly(TimeCondition condition, String... shownFields) throws Exception {
      String shown = resolveCreateOf(specWith(condition)).changes().get(0).proposedValue();
      List<String> expected = List.of(shownFields);

      for(String field : CONDITION_FIELDS) {
         boolean present = shown.contains("," + field + "=");
         assertEquals(expected.contains(field), present,
                      condition.getType() + " field " + field + " in: " + shown);
      }
   }

   @Test void planShowsOnlyTheFieldsEachConditionTypePersists() throws Exception {
      assertShowsOnly(everyFieldSet(TimeCondition.Type.AT, 0), "date");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.EVERY_HOUR, 0),
         "hour", "minute", "second", "daysOfWeek", "hourEnd", "minuteEnd", "secondEnd",
         "hourlyInterval");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.EVERY_DAY, 0),
         "hour", "minute", "second", "interval", "weekdayOnly");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.EVERY_WEEK, 0),
         "hour", "minute", "second", "interval", "daysOfWeek");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.DAY_OF_WEEK, 0),
         "hour", "minute", "second", "interval", "daysOfWeek");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.EVERY_MONTH, 0),
         "hour", "minute", "second", "dayOfMonth", "monthsOfYear");
      assertShowsOnly(everyFieldSet(TimeCondition.Type.EVERY_MONTH, 2),
         "hour", "minute", "second", "weekOfMonth", "dayOfWeek", "monthsOfYear");
   }

   @Test void everyDayPlanHasNoDaysOfWeekText() throws Exception {
      TimeCondition condition = timeCondition(9, 0);
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setDaysOfWeek(new int[]{2, 4});
      String shown = resolveCreateOf(specWith(condition)).changes().get(0).proposedValue();

      assertFalse(shown.contains("daysOfWeek"), shown);
   }

   private static inetsoft.web.api.schedule.ViewsheetAction vsAction() {
      inetsoft.web.api.schedule.ViewsheetAction action =
         new inetsoft.web.api.schedule.ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Sales Summary^host-org");
      action.setEmails(List.of("qa@example.com"));
      return action;
   }

   private String shownActionOf(inetsoft.web.api.schedule.ViewsheetAction action)
      throws Exception
   {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(action));
      return resolveCreateOf(spec).changes().get(0).proposedValue();
   }

   // bug #79033
   @Test void viewsheetActionBooleansAreShown() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setNotifies(List.of("ops@example.com"));
      action.setNotifyIfFailed(true);
      action.setNotifyLink(true);
      action.setEmailZip(true);
      action.setEmailLink(true);
      action.setHtmlMessage(true);

      String shown = shownActionOf(action);

      assertTrue(shown.contains("notifyIfFailed=true,"), shown);
      assertTrue(shown.contains("notifyLink=true,"), shown);
      assertTrue(shown.contains("emailZip=true"), shown);
      assertTrue(shown.contains("emailLink=true"), shown);
      assertTrue(shown.contains("htmlMessage=true"), shown);
      assertFalse(shown.contains("ignored"), shown);
   }

   @Test void notifyFlagsWithoutNotifiesAreShownAsIgnored() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setNotifyIfFailed(true);
      action.setNotifyLink(false);

      String shown = shownActionOf(action);

      assertTrue(shown.contains("notifyIfFailed=true(ignored:no notifies)"), shown);
      assertTrue(shown.contains("notifyLink=false(ignored:no notifies)"), shown);
   }

   @Test void nullBooleansAreOmittedAndPrimitivesAlwaysShown() throws Exception {
      String shown = shownActionOf(vsAction());

      assertFalse(shown.contains("emailZip"), shown);
      assertFalse(shown.contains("emailLink"), shown);
      assertFalse(shown.contains("notifyIfFailed"), shown);
      assertTrue(shown.contains("htmlMessage=false"), shown);
      assertTrue(shown.contains("saveToServerMatch=true"), shown);
      assertTrue(shown.contains("saveExportAllTabbedTables=false"), shown);
   }

   @Test void everyActionBooleanChangesTheHash() throws Exception {
      java.util.Set<String> hashes = new java.util.HashSet<>();
      List<java.util.function.Consumer<inetsoft.web.api.schedule.ViewsheetAction>> mutators =
         List.of(
            a -> {},
            a -> a.setEmailZip(true),
            a -> a.setEmailLink(true),
            a -> a.setHtmlMessage(true),
            a -> a.setEmailMatch(false),
            a -> a.setSaveToServerMatch(false),
            a -> a.setSaveExportAllTabbedTables(true),
            a -> a.setSender("a@example.com"));

      for(var mutator : mutators) {
         inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
         mutator.accept(action);
         CreateScheduleTaskRequest spec = createSpec("t1", "admin");
         spec.setActions(List.of(action));
         hashes.add(resolveCreateOf(spec).planHash());
      }

      assertEquals(mutators.size(), hashes.size());
   }

   @Test void ftpPasswordIsNeverShown() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath separate =
         new inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath();
      separate.setFormat(inetsoft.web.api.schedule.ViewsheetAction.Format.PDF);
      separate.setPath("ftp://host.example.com/out");
      separate.setUsername("alice");
      separate.setPassword("S3cretPw!");
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath embedded =
         new inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath();
      embedded.setFormat(inetsoft.web.api.schedule.ViewsheetAction.Format.EXCEL);
      embedded.setPath("sftp://bob:Emb3ddedPw@host.example.com/out");
      action.setSaveToServerFilePaths(List.of(separate, embedded));

      String shown = shownActionOf(action);

      assertFalse(shown.contains("S3cretPw!"), shown);
      assertFalse(shown.contains("Emb3ddedPw"), shown);
      assertTrue(shown.contains("path=ftp://host.example.com/out"), shown);
      assertTrue(shown.contains("username=alice"), shown);
      assertTrue(shown.contains("path=sftp://bob@host.example.com/out"), shown);
      assertTrue(shown.contains("password=set"), shown);
   }

   private static inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath filePath(
      String path)
   {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p =
         new inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath();
      p.setFormat(inetsoft.web.api.schedule.ViewsheetAction.Format.PDF);
      p.setPath(path);
      return p;
   }

   @Test void usernameAndPasswordAreMarkedIgnoredWithUseCredential() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p = filePath("/data/out");
      p.setUseCredential(true);
      p.setSecretId("secret-1");
      p.setUsername("alice");
      p.setPassword("S3cretPw!");
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(p));

      String shown = shownActionOf(action);

      assertTrue(shown.contains("username=alice(ignored:useCredential)"), shown);
      assertTrue(shown.contains("password=set(ignored:useCredential)"), shown);
      assertTrue(shown.contains("secretId=secret-1"), shown);
      assertFalse(shown.contains("S3cretPw!"), shown);
   }

   @Test void usernameIsEffectiveWithoutUseCredential() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p =
         filePath("ftp://host.example.com/out");
      p.setUsername("alice");
      p.setPassword("S3cretPw!");
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(p));

      String shown = shownActionOf(action);

      assertTrue(shown.contains("username=alice,") || shown.contains("username=alice}"), shown);
      assertTrue(shown.contains("password=set}"), shown);
      assertFalse(shown.contains("ignored"), shown);
   }

   @Test void pathPasswordOverridesUsernameFieldAndSecret() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p =
         filePath("ftp://bob:Emb3ddedPw@host.example.com/out");
      p.setUsername("alice");
      p.setUseCredential(true);
      p.setSecretId("secret-1");
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(p));

      String shown = shownActionOf(action);

      assertTrue(shown.contains("username=alice(ignored:path password)"), shown);
      assertTrue(shown.contains("secretId=secret-1(ignored:path password)"), shown);
      assertTrue(shown.contains("password=set"), shown);
      assertFalse(shown.contains("Emb3ddedPw"), shown);
   }

   @Test void placeholderPasswordIsNotReportedAsSet() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p = filePath("/data/out");
      p.setPassword(inetsoft.report.internal.Util.PLACEHOLDER_PASSWORD);
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(p));

      assertFalse(shownActionOf(action).contains("password="));
   }

   @Test void singularBookmarkFieldsAreMarkedIgnoredWhenBookmarkNamesIsSet() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setBookmarkName("single");
      action.setBookmarkType(inetsoft.web.api.schedule.ViewsheetAction.BookmarkType.PRIVATE);
      action.setBookmarkNames(List.of("a"));
      action.setBookmarkUsers(List.of(new inetsoft.sree.security.IdentityID("admin", "host-org")));

      String shown = shownActionOf(action);

      assertTrue(shown.contains("bookmarkName=single(ignored:bookmarkNames)"), shown);
      assertTrue(shown.contains("bookmarkType=") && shown.contains("(ignored:bookmarkNames),"), shown);
      assertFalse(shown.contains("bookmarkUsers=[admin~;~host-org](ignored"), shown);
   }

   @Test void pluralBookmarkFieldsAreMarkedIgnoredWithoutBookmarkNames() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setBookmarkName("single");
      action.setBookmarkTypes(List.of(inetsoft.web.api.schedule.ViewsheetAction.BookmarkType.PRIVATE));

      String shown = shownActionOf(action);

      assertFalse(shown.contains("bookmarkName=single(ignored"), shown);
      assertTrue(shown.contains("bookmarkTypes=[private](ignored:no bookmarkNames)"), shown);
   }

   @Test void secretIdIsShown() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath path =
         new inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath();
      path.setFormat(inetsoft.web.api.schedule.ViewsheetAction.Format.PDF);
      path.setPath("/data/out");
      path.setUseCredential(true);
      path.setSecretId("secret-1");
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(path));

      assertTrue(shownActionOf(action).contains("useCredential=true,secretId=secret-1"));
   }

   @Test void parameterValuesAreShownOnlyAsDigest() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction a = vsAction();
      a.setParameters(new java.util.HashMap<>(java.util.Map.of("token", "TopSecretValue")));
      inetsoft.web.api.schedule.ViewsheetAction b = vsAction();
      b.setParameters(new java.util.HashMap<>(java.util.Map.of("token", "OtherSecretValue")));

      String shownA = shownActionOf(a);

      assertFalse(shownA.contains("TopSecretValue"), shownA);
      assertTrue(shownA.contains("parameters={token=sha256:"), shownA);
      assertNotEquals(shownA, shownActionOf(b));
   }

   @Test void alertsAndEmailSettingsAreShown() throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setAlerts(List.of(new ScheduleAlert("Chart1", "High"), new ScheduleAlert("x", null)));
      CSVConfig csv = new CSVConfig();
      csv.setDelimiter(";");
      action.setEmailCSVConfig(csv);
      action.setCCAddresses("cc@example.com");

      String shown = shownActionOf(action);

      assertTrue(shown.contains("alerts=[Chart1:High]"), shown);
      assertTrue(shown.contains("delimiter=';'"), shown);
      assertTrue(shown.contains("ccAddresses=cc@example.com"), shown);
   }

   // bug #79020
   @Test void missingViewsheetIsRefusedNamingTheActionAndValue() throws Exception {
      String missing = "1^128^__NULL__^Nope/Missing^host-org";
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setViewsheet(missing);
      spec.setActions(List.of(vsAction(), action));
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      lenient().when(scheduleGateway.viewsheetExists(missing)).thenReturn(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("t", List.of(createChange(spec))), user));

      assertTrue(ex.getMessage().startsWith(
         "changes[0].spec.actions[1].viewsheet: \"" + missing + "\""), ex.getMessage());
   }

   @Test void existingViewsheetIsAccepted() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(vsAction()));

      assertEquals(1, resolveCreateOf(spec).changes().size());
   }

   @Test void unknownTimeZoneIsRefused() {
      TimeCondition condition = timeCondition(9, 0);
      condition.setTimeZone("Mars/Olympus");
      CreateScheduleTaskRequest spec = specWith(condition);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveCreateOf(spec));

      assertTrue(ex.getMessage().startsWith(
         "changes[0].spec.conditions[0].timeZone: \"Mars/Olympus\""), ex.getMessage());
   }

   // bug #79524
   @Test void outOfRangeTimeOfDayIsRefusedNamingTheField() {
      CreateScheduleTaskRequest spec = specWith(timeCondition(25, 0));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveCreateOf(spec));

      assertTrue(ex.getMessage().startsWith("changes[0].spec.conditions[0].hour: "),
                 ex.getMessage());
   }

   @Test void knownAndOmittedTimeZonesAreAccepted() throws Exception {
      for(String zone : new String[]{null, "America/New_York", "UTC", "utc"}) {
         TimeCondition condition = timeCondition(9, 0);
         condition.setTimeZone(zone);
         assertEquals(1, resolveCreateOf(specWith(condition)).changes().size(), zone);
      }
   }

   // Bug #79424: the permission-based sanitize resets a value the caller may not set (e.g. a
   // timeRange after switch_organization), so the plan must refuse it instead of showing it.
   @Test void conditionValueThePermissionSanitizeWouldResetIsRefusedNamingTheField() {
      when(scheduleGateway.previewConditionResets(anyList(), eq(user)))
         .thenReturn(java.util.Collections.singletonList("timeRange: it would be reset to none"));
      CreateScheduleTaskRequest spec = specWith(timeCondition(9, 0));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveCreateOf(spec));

      assertTrue(ex.getMessage().startsWith(
         "changes[0].spec.conditions[0].timeRange: "), ex.getMessage());
   }

   @Test void conditionNothingResetsIsAccepted() throws Exception {
      when(scheduleGateway.previewConditionResets(anyList(), eq(user)))
         .thenReturn(java.util.Collections.singletonList(null));

      assertEquals(1, resolveCreateOf(specWith(timeCondition(9, 0))).changes().size());
   }

   // -------------------------------------------------------------------------
   // Bug #82274: completion conditions, batch/backup actions, folder, update
   // -------------------------------------------------------------------------

   private static CompletionCondition completion(String owner, String taskName) {
      CompletionCondition condition = new CompletionCondition();
      condition.setOwner(owner);
      condition.setTaskName(taskName);
      return condition;
   }

   @Test void resolveCreateRefusesACompletionConditionWithoutATaskName() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(completion("admin", " ")));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("changes[0].spec.conditions[0].taskName"));
   }

   @Test void resolveCreateRefusesACompletionConditionWithoutAnOwner() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(completion(null, "first")));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("conditions[0].owner"));
   }

   @Test void resolveCreateRefusesACompletionConditionOnATaskThatDoesNotExist() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(completion("admin", "missing")));
      lenient().when(scheduleManager.getScheduleTask(anyString())).thenReturn(null);
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no task exists"));
   }

   @Test void resolveCreateRefusesATaskThatWaitsOnItself() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(completion("admin", "t1")));
      ScheduleChangePlanRequest req = request("task", List.of(createChange(spec)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("cannot wait on itself"));
   }

   @Test void resolveUpdateRefusesACompletionCycleThroughAnotherTask() throws Exception {
      String selfId = ScheduleManager.getTaskId(new IdentityID("admin", "host-org").convertToKey(), "a");
      String otherId = ScheduleManager.getTaskId(new IdentityID("admin", "host-org").convertToKey(), "b");
      inetsoft.sree.schedule.ScheduleTask self = sreeTask("a", "admin");
      inetsoft.sree.schedule.ScheduleTask other = sreeTask("b", "admin");
      other.addCondition(new inetsoft.sree.schedule.CompletionCondition(selfId));
      when(scheduleManager.getScheduleTask(selfId)).thenReturn(self);
      when(scheduleManager.getScheduleTask(otherId)).thenReturn(other);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setConditions(List.of(completion("admin", "b")));
      ScheduleChangePlanRequest req = request("task", List.of(updateChange(selfId, patch)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("wait for each other"));
      verify(scheduleGateway, never()).buildUpdatedTask(any(), any(), any(), any());
   }

   @Test void resolveCreateAcceptsACompletionConditionOnAnExistingTask() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setConditions(List.of(completion("admin", "first")));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "t1");
      String firstId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "first");
      when(scheduleManager.getScheduleTask(firstId)).thenReturn(sreeTask("first", "admin"));
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ResolvedPlan plan = service.resolve(request("task", List.of(createChange(spec))), user);

      assertTrue(plan.changes().get(0).proposedValue()
                    .contains("condition[type=COMPLETION,taskName=first,owner=admin]"));
   }

   @Test void resolveCreateAcceptsBatchAndBackupActionsAndNeverShowsAPassword() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      BatchAction batch = new BatchAction();
      batch.setTaskName("first");
      batch.setOwner("admin");
      batch.setQueryEntry("1^128^__NULL__^q");
      batch.setEmbeddedParameters(List.of());
      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setAssetIdentifiers(List.of("1^2^__NULL__^ws"));
      inetsoft.web.api.schedule.ServerPathInfo info = new inetsoft.web.api.schedule.ServerPathInfo();
      info.setPath("ftp://host/dir");
      info.setUserName("u");
      info.setPassword("s3cret");
      backup.setPathInfo(info);
      spec.setActions(List.of(batch, backup));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "t1");
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ResolvedPlan plan = service.resolve(request("task", List.of(createChange(spec))), user);

      String proposed = plan.changes().get(0).proposedValue();
      assertTrue(proposed.contains("action[batch,taskName=first,owner=admin"));
      assertTrue(proposed.contains("action[backup,assets=[1^2^__NULL__^ws]"));
      assertTrue(proposed.contains("password=set"));
      assertFalse(proposed.contains("s3cret"));
   }

   @Test void resolveCreateRefusesABatchActionWithoutATargetTask() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      BatchAction batch = new BatchAction();
      batch.setOwner("admin");
      batch.setQueryEntry("q");
      batch.setEmbeddedParameters(List.of());
      spec.setActions(List.of(batch));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(createChange(spec))), user));
      assertTrue(ex.getMessage().contains("changes[0].spec.actions[0].taskName"));
   }

   @Test void resolveCreateRefusesABackupActionWithoutAssets() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(new IndividualAssetBackupAction()));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(createChange(spec))), user));
      assertTrue(ex.getMessage().contains("actions[0].assetIdentifiers"));
   }

   @Test void resolveCreateRefusesABackupActionWithoutPathInfo() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setAssetIdentifiers(List.of("1^2^__NULL__^ws"));
      spec.setActions(List.of(backup));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(createChange(spec))), user));
      assertTrue(ex.getMessage().contains("changes[0].spec.actions[0].pathInfo.path"));
   }

   @Test void resolveCreateRefusesABackupActionWithABlankPath() {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setAssetIdentifiers(List.of("1^2^__NULL__^ws"));
      inetsoft.web.api.schedule.ServerPathInfo info = new inetsoft.web.api.schedule.ServerPathInfo();
      info.setPath("  ");
      backup.setPathInfo(info);
      spec.setActions(List.of(backup));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(createChange(spec))), user));
      assertTrue(ex.getMessage().contains("changes[0].spec.actions[0].pathInfo.path"));
   }

   @Test void resolveUpdateRefusesABackupActionWithoutPathInfo() throws Exception {
      String taskId = ScheduleManager.getTaskId(new IdentityID("admin", "host-org").convertToKey(), "a");
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(sreeTask("a", "admin"));
      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setAssetIdentifiers(List.of("1^2^__NULL__^ws"));
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(backup));
      ScheduleChangePlanRequest req = request("task", List.of(updateChange(taskId, patch)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("changes[0].patch.actions[0].pathInfo.path"));
   }

   @Test void resolveCreateNamesTheFolderPathWhenTheFolderIsRefused() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      ScheduleChangeRequest change = createChange(spec);
      change.setPath("Finance/Reports");
      when(scheduleGateway.resolveCreateFolder(eq("Finance/Reports"), eq(user)))
         .thenThrow(new IllegalArgumentException("path: no schedule task folder exists at \"Finance/Reports\""));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().startsWith("changes[0].path:"));
   }

   @Test void resolveCreateShowsTheFolderInThePlan() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      ScheduleChangeRequest change = createChange(spec);
      change.setPath("/Finance/Reports");
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "t1");
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      assertTrue(plan.changes().get(0).proposedValue().endsWith(";path=Finance/Reports"));
      verify(scheduleGateway).resolveCreateFolder("/Finance/Reports", user);
   }

   @Test void resolveDeleteRefusesATaskAnotherTaskWaitsOn() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(task);
      when(scheduleManager.getScheduleTasks("host-org")).thenReturn(new java.util.Vector<>());
      when(scheduleManager.hasDependency(any(), eq("t1"))).thenReturn(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteChange("t1"))), user));
      assertTrue(ex.getMessage().contains("waits on"));
      verify(scheduleGateway, never()).hasOwnerAdminPermission(any(), any(), any());
   }

   private static ScheduleChangeRequest updateChange(String taskId, ScheduleTaskPatch patch) {
      ScheduleChangeRequest change = new ScheduleChangeRequest();
      change.setVerb(ScheduleChangeRequest.VERB_UPDATE);
      change.setTaskId(taskId);
      change.setPatch(patch);
      return change;
   }

   private static ScheduleTaskPatch enabledPatch(boolean enabled) {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(enabled);
      return patch;
   }

   @Test void resolveUpdateRefusesAMissingTaskId() {
      ScheduleChangeRequest change = updateChange(null, enabledPatch(false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("changes[0].taskId"));
   }

   @Test void resolveUpdateRefusesASpecAndAPath() {
      ScheduleChangeRequest withSpec = updateChange("t1", enabledPatch(false));
      withSpec.setSpec(createSpec("t1", "admin"));
      ScheduleChangeRequest withPath = updateChange("t1", enabledPatch(false));
      withPath.setPath("Finance");

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(withSpec)), user))
         .getMessage().contains("changes[0].spec"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(withPath)), user))
         .getMessage().contains("changes[0].path"));
   }

   @Test void resolveUpdateRefusesAnEmptyPatch() {
      ScheduleChangeRequest change = updateChange("t1", new ScheduleTaskPatch());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("changes[0].patch"));
   }

   @Test void resolveUpdateRefusesATaskThatDoesNotExist() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", enabledPatch(false)))), user));
      assertTrue(ex.getMessage().contains("no task exists"));
   }

   @Test void resolveUpdateRefusesABuiltInTask() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = sreeTask("__balance tasks__", "admin");
      task.setRemovable(false);
      when(scheduleManager.getScheduleTask("__balance tasks__")).thenReturn(task);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.resolve(
         request("task", List.of(updateChange("__balance tasks__", enabledPatch(false)))), user));
      assertTrue(ex.getMessage().contains("built-in"));
   }

   @Test void resolveUpdateShowsTheWholeTaskBeforeAndAfter() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      candidate.setEnabled(false);
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = enabledPatch(false);
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);

      ResolvedPlan plan = service.resolve(request("task", List.of(updateChange("t1", patch))), user);

      PlanChange change = plan.changes().get(0);
      assertEquals("t1", change.property());
      assertNotEquals(change.currentValue(), change.proposedValue());
      assertTrue(change.currentValue().contains("enabled=\"true\""));
      assertTrue(change.proposedValue().contains("enabled=\"false\""));
      assertTrue(plan.requiresAgentSignoff());
      // the dry-run save of the candidate and of the pre-image (the rollback) both passed
      assertFalse(plan.requiresStorageBackup());
      verify(scheduleManager).checkScheduleTaskSave("t1", candidate, user);
   }

   @Test void resolveUpdateKeepsTheBackupWhenTheRollbackCannotBePredicted() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      candidate.setEnabled(false);
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = enabledPatch(false);
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);
      when(orgManager.getCurrentOrgID(user)).thenReturn("other-org");

      ResolvedPlan plan = service.resolve(request("task", List.of(updateChange("t1", patch))), user);

      assertTrue(plan.requiresStorageBackup());
   }

   @Test void resolveUpdateRefusesAPatchThatChangesNothing() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = enabledPatch(true);
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user))
         .thenReturn(sreeTask("t1", "admin"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));
      assertTrue(ex.getMessage().contains("nothing to update"));
   }

   @Test void resolveUpdateNamesThePatchWhenTheGatewayRefusesIt() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      ScheduleTaskPatch patch = enabledPatch(false);
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user))
         .thenThrow(new inetsoft.web.security.auth.UnauthorizedAccessException());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));
      assertTrue(ex.getMessage().startsWith("changes[0].patch: the update would be refused"));
   }

   @Test void resolveUpdateHashChangesWithThePatch() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask off = sreeTask("t1", "admin");
      off.setEnabled(false);
      inetsoft.sree.schedule.ScheduleTask described = sreeTask("t1", "admin");
      described.setDescription("nightly");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch disable = enabledPatch(false);
      ScheduleTaskPatch describe = new ScheduleTaskPatch();
      describe.setDescription("nightly");
      when(scheduleGateway.buildUpdatedTask("t1", disable, null, user)).thenReturn(off);
      when(scheduleGateway.buildUpdatedTask("t1", describe, null, user)).thenReturn(described);

      String first = service.resolve(request("task", List.of(updateChange("t1", disable))), user).planHash();
      String second = service.resolve(request("task", List.of(updateChange("t1", describe))), user).planHash();

      assertNotEquals(first, second);
   }

   private static ScheduleChangePlanRequest request(String task, List<ScheduleChangeRequest> changes) {
      ScheduleChangePlanRequest req = new ScheduleChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
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
      spec.setConditions(List.of(timeCondition(9, 0)));
      return spec;
   }

   private static TimeCondition timeCondition(int hour, int minute) {
      TimeCondition condition = new TimeCondition();
      condition.setHour(hour);
      condition.setMinute(minute);
      return condition;
   }

   private static TimeCondition atCondition(java.time.OffsetDateTime date) {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.AT);
      condition.setDate(date);
      return condition;
   }

   private static inetsoft.sree.schedule.ScheduleTask sreeTask(String name, String owner) {
      inetsoft.sree.schedule.ScheduleTask task = new inetsoft.sree.schedule.ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }

   // -------------------------------------------------------------------------
   // task-level time zone (the portal Options tab's Time Zone)
   // -------------------------------------------------------------------------

   private ResolvedPlan resolveCreateWithZone(String zone) throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      ScheduleChangeRequest change = createChange(spec);
      change.setTimeZone(zone);
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "t1");
      lenient().when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      lenient().when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      return service.resolve(request("task", List.of(change)), user);
   }

   @Test void resolveCreateShowsTheTaskTimeZoneInThePlan() throws Exception {
      ResolvedPlan plan = resolveCreateWithZone("America/New_York");

      assertTrue(plan.changes().get(0).proposedValue().endsWith(";timeZone=America/New_York"));
   }

   @Test void resolveCreateWithoutATaskTimeZoneKeepsTheProjectionAndHash() throws Exception {
      String none = resolveCreateWithZone(null).changes().get(0).proposedValue();
      String blank = resolveCreateWithZone("  ").changes().get(0).proposedValue();

      assertFalse(none.contains(";timeZone="));
      assertEquals(none, blank);
      assertNotEquals(none, resolveCreateWithZone("Asia/Tokyo").changes().get(0).proposedValue());
   }

   @Test void resolveCreateRefusesAnUnknownTaskTimeZoneNamingTheField() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveCreateWithZone("Mars/Base"));

      assertTrue(ex.getMessage().startsWith("changes[0].timeZone: \"Mars/Base\""),
                 ex.getMessage());
      assertThrows(IllegalArgumentException.class, () -> resolveCreateWithZone("GMT+99"));
   }

   // A5 differential: the task zone and a time condition's zone accept and refuse the same ids
   @Test void taskTimeZoneAndConditionTimeZoneAreValidatedIdentically() throws Exception {
      String[] ids = {"America/New_York", "UTC", "utc", "GMT", "Etc/UTC", "GMT+8", "Asia/Tokyo",
                      "Mars/Base", "GMT+99", "Not/AZone", "new_york"};

      for(String id : ids) {
         boolean taskAccepts;
         boolean conditionAccepts;

         try {
            resolveCreateWithZone(id);
            taskAccepts = true;
         }
         catch(IllegalArgumentException e) {
            taskAccepts = false;
         }

         try {
            TimeCondition condition = timeCondition(9, 0);
            condition.setTimeZone(id);
            resolveCreateOf(specWith(condition));
            conditionAccepts = true;
         }
         catch(IllegalArgumentException e) {
            conditionAccepts = false;
         }

         assertEquals(conditionAccepts, taskAccepts, id);
      }
   }

   @Test void resolveUpdateAcceptsAPatchOfOnlyATimeZone() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      candidate.setTimeZone("Asia/Tokyo");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("Asia/Tokyo");
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);

      PlanChange change = service.resolve(
         request("task", List.of(updateChange("t1", patch))), user).changes().get(0);

      assertFalse(change.currentValue().contains("timeZone="));
      assertTrue(change.proposedValue().contains("timeZone=\"Asia/Tokyo\""),
                 change.proposedValue());
   }

   @Test void resolveUpdateWithTheSameTaskTimeZoneIsTheNoOpRefusal() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      stored.setTimeZone("Asia/Tokyo");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      candidate.setTimeZone("Asia/Tokyo");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("Asia/Tokyo");
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));
      assertTrue(ex.getMessage().contains("nothing to update"));
   }

   @Test void resolveUpdateWithABlankTaskTimeZoneIsAClearNotAnEmptyPatch() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      stored.setTimeZone("Asia/Tokyo");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("");
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);

      PlanChange change = service.resolve(
         request("task", List.of(updateChange("t1", patch))), user).changes().get(0);

      assertTrue(change.currentValue().contains("timeZone=\"Asia/Tokyo\""));
      assertFalse(change.proposedValue().contains("timeZone="));
   }

   @Test void resolveUpdateRefusesAnUnknownTaskTimeZoneNamingTheField() {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setTimeZone("Mars/Base");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));

      assertTrue(ex.getMessage().startsWith("changes[0].patch.timeZone: \"Mars/Base\""),
                 ex.getMessage());
   }

   @Test void resolveUpdateAndDeleteRefuseAChangeLevelTimeZone() {
      ScheduleChangeRequest update = updateChange("t1", enabledPatch(false));
      update.setTimeZone("Asia/Tokyo");
      ScheduleChangeRequest delete = deleteChange("t1");
      delete.setTimeZone("Asia/Tokyo");

      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(update)), user))
         .getMessage().startsWith("changes[0].timeZone:"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(delete)), user))
         .getMessage().startsWith("changes[0].timeZone:"));
   }

   // -------------------------------------------------------------------------
   // email zip password (Bug #82325, G1)
   // -------------------------------------------------------------------------

   private static final String ZIP_PW = "PW-PLACEHOLDER-ZIP-1";

   private static EmailZipPasswordSpec zipSpec(int index, String password, Boolean useCredential,
                                               String secretId)
   {
      EmailZipPasswordSpec spec = new EmailZipPasswordSpec();
      spec.setActionIndex(index);
      spec.setPassword(password);
      spec.setUseCredential(useCredential);
      spec.setSecretId(secretId);
      return spec;
   }

   private static inetsoft.web.api.schedule.ViewsheetAction zippedAction(boolean emailZip) {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setEmailZip(emailZip);
      return action;
   }

   private ResolvedPlan resolveCreateWithZipPasswords(List<ScheduleAction> actions,
                                                      EmailZipPasswordSpec... specs)
      throws Exception
   {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(actions);
      ScheduleChangeRequest change = createChange(spec);
      change.setEmailZipPasswords(List.of(specs));
      String taskId = ScheduleManager.getTaskId(spec.getOwner().convertToKey(), "t1");
      lenient().when(scheduleManager.getScheduleTask(taskId)).thenReturn(null);
      lenient().when(scheduleGateway.hasDeletePermission(taskId, user)).thenReturn(true);
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      return service.resolve(request("task", List.of(change)), user);
   }

   private String zipRefusal(List<ScheduleAction> actions, EmailZipPasswordSpec... specs) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> resolveCreateWithZipPasswords(actions, specs));
      assertFalse(ex.getMessage().contains(ZIP_PW), "the refusal must not echo the password");
      return ex.getMessage();
   }

   @Test void createPlanShowsTheZipPasswordTokenWithoutTheValue() throws Exception {
      ResolvedPlan plan = resolveCreateWithZipPasswords(List.of(zippedAction(true)),
         zipSpec(0, ZIP_PW, null, null));

      String proposed = plan.changes().get(0).proposedValue();
      assertTrue(proposed.contains(";emailZipPasswords=[0:set]"), proposed);
      assertFalse(proposed.contains(ZIP_PW));
      assertFalse(plan.toString().contains(ZIP_PW));
   }

   @Test void createPlanShowsTheSecretIdModeAndTheClearToken() throws Exception {
      String proposed = resolveCreateWithZipPasswords(
         List.of(zippedAction(true), zippedAction(true)),
         zipSpec(0, null, true, "secret-1"), zipSpec(1, "", null, null))
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains(
         ";emailZipPasswords=[0:useCredential,secretId=secret-1, 1:cleared]"), proposed);
   }

   // the existing hashes must not move when the field is absent
   @Test void createPlanWithoutZipPasswordsHasNoZipToken() throws Exception {
      CreateScheduleTaskRequest spec = createSpec("t1", "admin");
      spec.setActions(List.of(zippedAction(true)));

      assertFalse(resolveCreateOf(spec).changes().get(0).proposedValue()
                     .contains("emailZipPasswords"));
   }

   @Test void createPlanWithAnEmptyZipPasswordListHasNoZipToken() throws Exception {
      assertFalse(resolveCreateWithZipPasswords(List.of(zippedAction(true)))
                     .changes().get(0).proposedValue().contains("emailZipPasswords"));
   }

   @Test void createPlanRefusesAZipPasswordOnAnActionWithoutEmailZip() {
      String message = zipRefusal(List.of(zippedAction(false)), zipSpec(0, ZIP_PW, null, null));

      assertTrue(message.startsWith("changes[0].emailZipPasswords[0]"), message);
      assertTrue(message.contains("emailZip"), message);
   }

   @Test void createPlanRefusesAZipPasswordOnAnActionWithEmailZipUnset() {
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();

      assertTrue(zipRefusal(List.of(action), zipSpec(0, ZIP_PW, null, null)).contains("emailZip"));
   }

   @Test void createPlanRefusesAnActionIndexOutOfRangeOrNotAViewsheet() {
      String outOfRange = zipRefusal(List.of(zippedAction(true)), zipSpec(3, ZIP_PW, null, null));
      assertTrue(outOfRange.contains("emailZipPasswords[0].actionIndex"), outOfRange);

      BatchAction batch = new BatchAction();
      batch.setTaskName("b");
      batch.setOwner("admin");
      batch.setQueryEntry("q");
      batch.setEmbeddedParameters(List.of());
      assertTrue(zipRefusal(List.of(batch), zipSpec(0, ZIP_PW, null, null))
                    .contains("actionIndex"));
      assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(-1, ZIP_PW, null, null))
                    .contains("actionIndex"));
   }

   @Test void createPlanRefusesTwoEntriesForOneAction() {
      String message = zipRefusal(List.of(zippedAction(true)),
         zipSpec(0, ZIP_PW, null, null), zipSpec(0, "PW-PLACEHOLDER-ZIP-2", null, null));

      assertTrue(message.contains("emailZipPasswords[1].actionIndex"), message);
      assertFalse(message.contains("PW-PLACEHOLDER-ZIP-2"));
   }

   @Test void createPlanRefusesAPasswordTogetherWithASecretId() {
      // useCredential true with a password
      String credentialAndPassword = zipRefusal(List.of(zippedAction(true)),
         zipSpec(0, ZIP_PW, true, "secret-1"));
      assertTrue(credentialAndPassword.contains("emailZipPasswords[0].password"),
                 credentialAndPassword);

      // a password with a secret id but without useCredential
      String secretWithoutFlag = zipRefusal(List.of(zippedAction(true)),
         zipSpec(0, ZIP_PW, null, "secret-1"));
      assertTrue(secretWithoutFlag.contains("emailZipPasswords[0].secretId"), secretWithoutFlag);
   }

   @Test void createPlanRefusesUseCredentialWithoutASecretId() {
      assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(0, null, true, null))
                    .contains("emailZipPasswords[0].secretId"));
      assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(0, null, true, "  "))
                    .contains("emailZipPasswords[0].secretId"));
   }

   @Test void createPlanRefusesASecretIdWithoutUseCredentialTrue() {
      assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(0, null, false, "secret-1"))
                    .contains("emailZipPasswords[0].secretId"));
      assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(0, null, null, "secret-1"))
                    .contains("useCredential"));
   }

   @Test void zipPasswordIsRefusedInFipsModeButAClearIsNot() throws Exception {
      try(MockedStatic<inetsoft.util.PasswordEncryption> fips =
             mockStatic(inetsoft.util.PasswordEncryption.class))
      {
         fips.when(inetsoft.util.PasswordEncryption::isFipsCompliant).thenReturn(true);

         String setMessage = zipRefusal(List.of(zippedAction(true)),
                                        zipSpec(0, ZIP_PW, null, null));
         assertTrue(setMessage.contains("FIPS"), setMessage);
         assertTrue(setMessage.contains("emailZipPasswords[0]"), setMessage);
         assertTrue(zipRefusal(List.of(zippedAction(true)), zipSpec(0, null, true, "secret-1"))
                       .contains("FIPS"));

         // clearing stays possible, the stored value can still be removed
         assertTrue(resolveCreateWithZipPasswords(List.of(zippedAction(true)),
                       zipSpec(0, "", null, null))
                       .changes().get(0).proposedValue().contains("[0:cleared]"));
      }
   }

   @Test void zipPasswordIsAcceptedWhenNotInFipsMode() throws Exception {
      try(MockedStatic<inetsoft.util.PasswordEncryption> fips =
             mockStatic(inetsoft.util.PasswordEncryption.class))
      {
         fips.when(inetsoft.util.PasswordEncryption::isFipsCompliant).thenReturn(false);

         assertNotNull(resolveCreateWithZipPasswords(List.of(zippedAction(true)),
                          zipSpec(0, ZIP_PW, null, null)));
      }
   }

   @Test void createPlanPassesTheZipPasswordsToTheSaveCheck() throws Exception {
      EmailZipPasswordSpec entry = zipSpec(0, ZIP_PW, null, null);
      resolveCreateWithZipPasswords(List.of(zippedAction(true)), entry);

      verify(scheduleGateway).checkAddScheduleTaskSavable(any(), anyBoolean(), anyBoolean(),
         anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), eq(List.of(entry)),
         eq(user));
   }

   @Test void updateRefusesZipPasswordsWithoutPatchActions() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setDescription("d");
      patch.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));

      assertTrue(ex.getMessage().contains("patch.emailZipPasswords"), ex.getMessage());
      assertTrue(ex.getMessage().contains("patch.actions"), ex.getMessage());
      assertFalse(ex.getMessage().contains(ZIP_PW));
      verify(scheduleGateway, never()).buildUpdatedTask(any(), any(), any(), any());
   }

   @Test void updatePatchWithOnlyZipPasswordsStillNeedsActions() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));

      assertTrue(ex.getMessage().contains("patch.actions"), ex.getMessage());
   }

   @Test void updateRefusesAZipPasswordOnAnActionWithoutEmailZip() throws Exception {
      when(scheduleManager.getScheduleTask("t1")).thenReturn(sreeTask("t1", "admin"));
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(zippedAction(false)));
      patch.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateChange("t1", patch))), user));

      assertTrue(ex.getMessage().startsWith("changes[0].patch.emailZipPasswords[0]"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("emailZip"));
      assertFalse(ex.getMessage().contains(ZIP_PW));
   }

   @Test void updateWithZipPasswordsAndActionsReachesTheGateway() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = sreeTask("t1", "admin");
      inetsoft.sree.schedule.ScheduleTask candidate = sreeTask("t1", "admin");
      candidate.setEnabled(false);
      when(scheduleManager.getScheduleTask("t1")).thenReturn(stored);
      lenient().when(scheduleGateway.viewsheetExists(anyString())).thenReturn(true);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(zippedAction(true)));
      patch.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));
      when(scheduleGateway.buildUpdatedTask("t1", patch, null, user)).thenReturn(candidate);

      ResolvedPlan plan = service.resolve(request("task", List.of(updateChange("t1", patch))), user);

      assertFalse(plan.toString().contains(ZIP_PW));
      verify(scheduleGateway).buildUpdatedTask("t1", patch, null, user);
   }

   @Test void deleteAndUpdateRefuseTheChangeLevelZipPasswords() throws Exception {
      ScheduleChangeRequest delete = deleteChange("t1");
      delete.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));
      IllegalArgumentException onDelete = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(delete)), user));
      assertTrue(onDelete.getMessage().startsWith("changes[0].emailZipPasswords:"),
                 onDelete.getMessage());
      assertFalse(onDelete.getMessage().contains(ZIP_PW));

      ScheduleChangeRequest update = updateChange("t1", enabledPatch(false));
      update.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));
      IllegalArgumentException onUpdate = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(update)), user));
      assertTrue(onUpdate.getMessage().contains("patch.emailZipPasswords"),
                 onUpdate.getMessage());
   }

   @Test void zipPasswordSpecToStringNeverPrintsThePassword() {
      assertFalse(zipSpec(0, ZIP_PW, null, null).toString().contains(ZIP_PW));
      assertTrue(zipSpec(0, ZIP_PW, null, null).toString().contains("password=set"));
      assertTrue(zipSpec(1, "", null, null).toString().contains("password=cleared"));
      assertTrue(zipSpec(1, inetsoft.report.internal.Util.PLACEHOLDER_PASSWORD, null, null)
                    .toString().contains("password=kept"));
   }

   // -------------------------------------------------------------------------
   // a placeholder password in a server location (Bug #82325, G2)
   // -------------------------------------------------------------------------

   private String shownFtpPlaceholder(String path, String username) throws Exception {
      inetsoft.web.api.schedule.ViewsheetAction.SaveToServerFilePath p = filePath(path);
      p.setUsername(username);
      p.setPassword(inetsoft.report.internal.Util.PLACEHOLDER_PASSWORD);
      inetsoft.web.api.schedule.ViewsheetAction action = vsAction();
      action.setSaveToServerFilePaths(List.of(p));
      return shownActionOf(action);
   }

   private static MockedStatic<inetsoft.sree.internal.SUtil> oneServerLocation() {
      MockedStatic<inetsoft.sree.internal.SUtil> sutil = mockStatic(
         inetsoft.sree.internal.SUtil.class, withSettings().lenient()
            .defaultAnswer(Answers.CALLS_REAL_METHODS));
      sutil.when(inetsoft.sree.internal.SUtil::getServerLocationsWithPasswords).thenReturn(List.of(
         inetsoft.web.admin.schedule.model.ServerLocation.builder()
            .path("ftp://host/reports").label("Finance share")
            .pathInfoModel(inetsoft.web.admin.schedule.model.ServerPathInfoModel.builder()
               .path("ftp://host/reports").username("ftpuser").password("PW-PLACEHOLDER-FTP-LOC")
               .ftp(true).build())
            .build()));
      return sutil;
   }

   @Test void aPlaceholderInsideAServerLocationShowsThePasswordAsSetNeverItsValue()
      throws Exception
   {
      try(MockedStatic<inetsoft.sree.internal.SUtil> sutil = oneServerLocation()) {
         String shown = shownFtpPlaceholder("ftp://host/reports/daily", "ftpuser");

         assertTrue(shown.contains("password=set(server location)"), shown);
         assertFalse(shown.contains("PW-PLACEHOLDER-FTP-LOC"), shown);
      }
   }

   // C2: outside every location the placeholder is still not reported as a password
   @Test void aPlaceholderOutsideEveryServerLocationIsStillNotReportedAsSet() throws Exception {
      try(MockedStatic<inetsoft.sree.internal.SUtil> sutil = oneServerLocation()) {
         assertFalse(shownFtpPlaceholder("ftp://host/other", "ftpuser").contains("password="));
         assertFalse(shownFtpPlaceholder("ftp://host/reports/daily", "someoneelse")
                        .contains("password="));
      }
   }
}
