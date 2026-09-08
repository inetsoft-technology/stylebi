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
import inetsoft.sree.security.IdentityID;
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

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

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

   private static inetsoft.sree.schedule.ScheduleTask sreeTask(String name, String owner) {
      inetsoft.sree.schedule.ScheduleTask task = new inetsoft.sree.schedule.ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }
}
