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
import inetsoft.uql.viewsheet.FileFormatInfo;
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
   private ScheduleChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new ScheduleChangePlanService(scheduleGateway, scheduleManager);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
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
      change.setVerb("update");
      change.setTaskId("t1");
      ScheduleChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("verb"));
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
      assertTrue(plan.requiresStorageBackup());
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

   @Test void knownAndOmittedTimeZonesAreAccepted() throws Exception {
      for(String zone : new String[]{null, "America/New_York", "UTC", "utc"}) {
         TimeCondition condition = timeCondition(9, 0);
         condition.setTimeZone(zone);
         assertEquals(1, resolveCreateOf(specWith(condition)).changes().size(), zone);
      }
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
}
