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

import inetsoft.web.api.schedule.ScheduleTask;
import inetsoft.web.api.schedule.ScheduleTaskList;
import inetsoft.web.api.schedule.UnsupportedScheduleItem;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Same gate shape as {@code AdminChangesetControllerTest} -- the bearer-token backstop and
 * site-admin check are copied verbatim, so these tests mirror that file's coverage of them. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminScheduleControllerTest {
   @Mock private AdminScheduleGateway scheduleGateway;
   @Mock private ScheduleChangePlanService planService;
   @Mock private ScheduleChangesetApplyService applyService;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;
   private AdminScheduleController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setup() {
      controller = new AdminScheduleController(scheduleGateway, planService, applyService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   @Test void listTasksThrowsForbiddenWithoutBearerToken() {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex =
         assertThrows(ResponseStatusException.class, () -> controller.listTasks(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(scheduleGateway);
   }

   @Test void listTasksThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex =
         assertThrows(ResponseStatusException.class, () -> controller.listTasks(principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(scheduleGateway);
   }

   @Test void listTasksDelegatesToScheduleGateway() throws Exception {
      ScheduleTaskList list = new ScheduleTaskList();
      when(scheduleGateway.getScheduleTasks(isNull(), eq(principal))).thenReturn(list);

      ScheduleTaskList result = controller.listTasks(principal);

      assertSame(list, result);
   }

   // Bug #76351 (ST-001): a built-in system task (e.g. "__balance tasks__") has no API DTO for its
   // condition/action, which used to make getTaskConditions/getTaskActions throw and this endpoint
   // 500. getTask must use the lenient variants instead, so it always returns 200 -- this test does
   // not itself exercise the placeholder substitution (that's a gateway-level concern), only that
   // the controller wires to the lenient methods and returns their result untouched.
   @Test void getTaskDelegatesToLenientConditionsAndActions() throws Exception {
      String taskId = "__balance tasks__";
      ScheduleTask task = new ScheduleTask();
      List<Object> conditions = List.of(new UnsupportedScheduleItem("inetsoft.sree.schedule.TaskBalancerCondition"));
      List<Object> actions = Collections.emptyList();
      when(scheduleGateway.getScheduleTask(taskId, null, principal)).thenReturn(task);
      when(scheduleGateway.getTaskConditionsLenient(taskId, null, principal)).thenReturn(conditions);
      when(scheduleGateway.getTaskActionsLenient(taskId, null, principal)).thenReturn(actions);

      ScheduleTaskView result = controller.getTask(taskId, principal);

      assertSame(task, result.getTask());
      assertEquals(conditions, result.getConditions());
      assertEquals(actions, result.getActions());
      verify(scheduleGateway, never()).getTaskConditions(anyString(), any(), any());
      verify(scheduleGateway, never()).getTaskActions(anyString(), any(), any());
   }

   @Test void previewThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.preview(new ScheduleChangePlanRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(planService);
   }

   @Test void previewDelegatesToPlanService() throws Exception {
      ScheduleChangePlanRequest req = new ScheduleChangePlanRequest();
      ResolvedPlan plan = new ResolvedPlan("t", Collections.emptyList(), false, false, "hash", "token");
      when(planService.resolve(req, principal)).thenReturn(plan);

      ResolvedPlan result = controller.preview(req, principal);

      assertSame(plan, result);
   }

   @Test void applyThrowsForbiddenForNonSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.apply(new ScheduleApplyRequest(), principal));
      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      verifyNoInteractions(applyService);
   }

   @Test void applyDelegatesToApplyService() throws Exception {
      ScheduleApplyRequest req = new ScheduleApplyRequest();
      ApplyResult applied = new ApplyResult("tx-1", AdminChangesetApplyService.STATUS_APPLIED,
                                            null, Collections.emptyList(), null);
      when(applyService.apply(req, principal)).thenReturn(applied);

      ApplyResult result = controller.apply(req, principal);

      assertSame(applied, result);
   }

   @Test void handleIllegalArgumentReturnsFailedStatus() {
      var body = controller.handleIllegalArgument(new IllegalArgumentException("bad input"));

      assertEquals("failed", body.get("status"));
      assertEquals("bad input", body.get("error"));
   }

   // PlanHashMismatchException strips taskToken from the plan it carries (see its own javadoc: a
   // 409 conflict must never hand back a token, which would let a caller retry with an unreviewed
   // narrative) -- so the returned plan is a new instance with taskToken nulled, not the same
   // instance as fresh, matching AdminAiControllerTest's own coverage of this exception.
   @Test void handlePlanHashMismatchReturnsConflictWithFreshPlan() {
      ResolvedPlan fresh = new ResolvedPlan("t", Collections.emptyList(), false, false, "new-hash", "new-token");
      var body = controller.handlePlanHashMismatch(
         new AdminChangesetApplyService.PlanHashMismatchException(fresh));

      assertEquals("conflict", body.get("status"));
      ResolvedPlan returnedPlan = (ResolvedPlan) body.get("plan");
      assertEquals(fresh.task(), returnedPlan.task());
      assertEquals(fresh.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }
}
