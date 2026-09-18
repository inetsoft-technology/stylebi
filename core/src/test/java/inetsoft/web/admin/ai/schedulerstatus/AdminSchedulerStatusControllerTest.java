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

import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.SchedulerConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors {@code AdminClusterControllerTest}'s own scope: the exception-handler wiring that maps
 * this area's own thrown exceptions to the correct structured HTTP responses. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminSchedulerStatusControllerTest {
   @Mock private SchedulerConfigurationService configService;
   @Mock private SchedulerStatusChangePlanService planService;
   @Mock private SchedulerStatusChangesetApplyService applyService;
   @Mock private SchedulerDiagnosticsService diagnosticsService;
   private AdminSchedulerStatusController controller;

   @BeforeEach void setUp() {
      controller = new AdminSchedulerStatusController(
         configService, planService, applyService, diagnosticsService);
   }

   @Test void handleIllegalArgumentReturnsFailedStatus() {
      Map<String, String> actual =
         controller.handleIllegalArgument(new IllegalArgumentException("changes: at least one change is required"));
      assertEquals("failed", actual.get("status"));
      assertEquals("changes: at least one change is required", actual.get("error"));
   }

   @Test void handlePlanHashMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("start scheduler", List.of(), false, true,
                                              "hash123", "token123");
      AdminChangesetApplyService.PlanHashMismatchException ex =
         new AdminChangesetApplyService.PlanHashMismatchException(current);

      Map<String, Object> actual = controller.handlePlanHashMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      ResolvedPlan returnedPlan = (ResolvedPlan) actual.get("plan");
      assertEquals(current.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("restart scheduler", List.of(), false, true,
                                              "hash456", "token456");
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current,
            "taskToken: does not match the current plan; re-review before applying");

      Map<String, Object> actual = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      ResolvedPlan returnedPlan = (ResolvedPlan) actual.get("plan");
      assertEquals(current.task(), returnedPlan.task());
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminSchedulerStatusController.class
         .getMethod("handleTaskTokenMismatch", AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleTaskTokenMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }

   @Test void handlePlanHashMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminSchedulerStatusController.class
         .getMethod("handlePlanHashMismatch", AdminChangesetApplyService.PlanHashMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handlePlanHashMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}
