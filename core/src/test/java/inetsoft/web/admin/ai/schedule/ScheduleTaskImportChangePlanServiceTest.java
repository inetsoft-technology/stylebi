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
import inetsoft.web.admin.ai.PlanChange;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79574: a staged task whose dependency (Completion parent or BatchAction target) exists
 * neither on the server nor among the tasks selected in the same import must be flagged by name
 * in the preview, without changing risk/signoff and without refusing the plan.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleTaskImportChangePlanServiceTest {
   private static final String LINK_URI = "http://target-server/";
   private static final String MISSING = "admin~;~host-org:qa-st-dep-missing-probe";

   @Mock private ScheduleManager scheduleManager;
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private Principal user;

   private ScheduleTaskTransferService transferService;
   private ScheduleTaskImportChangePlanService planService;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      transferService = new ScheduleTaskTransferService(scheduleManager, scheduleService);
      planService = new ScheduleTaskImportChangePlanService(transferService, scheduleManager,
                                                         scheduleTaskService);
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString())).thenAnswer(inv -> {
         String s = inv.getArgument(0);
         return s.startsWith("TKN:") ? s.substring(4) : s;
      });
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      tool.close();
   }

   @Test void missingCompletionDependencyIsFlaggedByName() throws Exception {
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(MISSING));
      PlanChange change = previewOne(child);

      assertTrue(change.description().contains(MISSING), change.description());
      assertTrue(change.description().toLowerCase().contains("depend"), change.description());
      assertEquals("low", change.risk());
   }

   @Test void completionWordingDoesNotClaimTaskNeverRuns() throws Exception {
      ScheduleTask child = task("child");
      child.addCondition(timeCondition());
      child.addCondition(new CompletionCondition(MISSING));
      String d = previewOne(child).description();

      assertTrue(d.contains("not be triggered by"), d);
      assertTrue(d.contains("other conditions"), d);
      assertFalse(d.contains("never"), d);
   }

   @Test void existingDependencyIsNotFlagged() throws Exception {
      lenient().when(scheduleManager.getScheduleTask(MISSING)).thenReturn(task("qa-st-dep-missing-probe"));
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(MISSING));
      PlanChange change = previewOne(child);

      assertFalse(change.description().toLowerCase().contains("depend"), change.description());
      assertEquals("low", change.risk());
   }

   @Test void dependencySatisfiedByAnotherSelectedTaskIsNotFlagged() throws Exception {
      ScheduleTask parent = task("parent");
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(parent.getTaskId()));
      String token = stage(child, parent);
      // child listed BEFORE parent: satisfaction must not depend on iteration order
      ResolvedPlan plan = planService.resolve(
         request(token, change(child.getTaskId()), change(parent.getTaskId())), user, LINK_URI);

      assertFalse(plan.changes().get(0).description().toLowerCase().contains("depend"));
   }

   @Test void dependencyStagedButNotSelectedIsFlagged() throws Exception {
      ScheduleTask parent = task("parent");
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(parent.getTaskId()));
      String token = stage(child, parent);
      ResolvedPlan plan =
         planService.resolve(request(token, change(child.getTaskId())), user, LINK_URI);

      assertTrue(plan.changes().get(0).description().contains(parent.getTaskId()));
   }

   @Test void multipleMissingDependenciesAreAllNamed() throws Exception {
      String other = "admin~;~host-org:qa-other-missing";
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(MISSING));
      child.addCondition(new CompletionCondition(other));
      String d = previewOne(child).description();

      assertTrue(d.contains(MISSING), d);
      assertTrue(d.contains(other), d);
   }

   @Test void overwriteWithMissingDependencyIsFlaggedAndStaysHighRisk() throws Exception {
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(MISSING));
      lenient().when(scheduleManager.getScheduleTask(child.getTaskId())).thenReturn(task("child"));
      String token = stage(child);
      ResolvedPlan plan = planService.resolve(
         request(token, change(child.getTaskId(), true)), user, LINK_URI);
      PlanChange change = plan.changes().get(0);

      assertTrue(change.description().contains(MISSING), change.description());
      assertEquals("high", change.risk());
   }

   @Test void missingBatchActionTargetIsFlaggedWithoutCompletionWording() throws Exception {
      ScheduleTask batch = task("batch");
      batch.addCondition(timeCondition());
      BatchAction action = new BatchAction();
      action.setTaskId(MISSING);
      batch.addAction(action);
      String d = previewOne(batch).description();

      // a Batch action's taskId is re-homed to the importing organization at stage time, so
      // only the task-name part of the id is stable here
      assertTrue(d.contains("qa-st-dep-missing-probe"), d);
      assertTrue(d.contains("batch action"), d);
      assertFalse(d.contains("completion condition"), d);
   }

   @Test void flagDoesNotChangeThePlanHash() throws Exception {
      ScheduleTask child = task("child");
      child.addCondition(new CompletionCondition(MISSING));
      String token = stage(child);
      ScheduleTaskImportPlanRequest req = request(token, change(child.getTaskId()));
      String missingHash = planService.resolve(req, user, LINK_URI).planHash();
      lenient().when(scheduleManager.getScheduleTask(MISSING)).thenReturn(task("x"));

      assertEquals(missingHash, planService.resolve(req, user, LINK_URI).planHash());
   }

   private PlanChange previewOne(ScheduleTask t) throws Exception {
      String token = stage(t);
      return planService.resolve(request(token, change(t.getTaskId())), user, LINK_URI)
         .changes().get(0);
   }

   private String stage(ScheduleTask... tasks) throws Exception {
      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         for(ScheduleTask t : tasks) {
            t.writeXML(pw);
         }
      }

      String xml = "<schedule>" + sw + "</schedule>";
      String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
      return transferService.stage(base64, user).stagingToken();
   }

   private static ScheduleTask task(String name) {
      ScheduleTask t = new ScheduleTask();
      t.setName(name);
      t.setOwner(new IdentityID("admin", "host-org"));
      t.setEnabled(true);
      return t;
   }

   private static TimeCondition timeCondition() {
      TimeCondition c = new TimeCondition();
      c.setType(TimeCondition.EVERY_DAY);
      c.setHour(9);
      c.setMinute(0);
      c.setSecond(0);
      return c;
   }

   private static ScheduleTaskImportChangeRequest change(String id) {
      return change(id, false);
   }

   private static ScheduleTaskImportChangeRequest change(String id, boolean overwrite) {
      ScheduleTaskImportChangeRequest c = new ScheduleTaskImportChangeRequest();
      c.setTaskId(id);
      c.setOverwrite(overwrite);
      return c;
   }

   private static ScheduleTaskImportPlanRequest request(String token,
                                                         ScheduleTaskImportChangeRequest... cs)
   {
      ScheduleTaskImportPlanRequest r = new ScheduleTaskImportPlanRequest();
      r.setTask("import");
      r.setStagingToken(token);
      r.setChanges(new ArrayList<>(List.of(cs)));
      return r;
   }
}
