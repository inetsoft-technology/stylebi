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

import inetsoft.web.api.schedule.ScheduleTaskList;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the schedule-task admin-plugin area (spec §10). Same {@code
 * requireSiteAdmin}/{@code AdminAiCallerGuard} shape as {@code AdminAiController}/{@code
 * AdminChangesetController} -- copied, not shared, matching the existing precedent for that
 * duplication (neither of those two share it with each other).
 */
@RestController
public class AdminScheduleController {
   @Autowired
   public AdminScheduleController(AdminScheduleGateway scheduleGateway,
                                  ScheduleChangePlanService planService,
                                  ScheduleChangesetApplyService applyService)
   {
      this.scheduleGateway = scheduleGateway;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/schedule/tasks")
   public ScheduleTaskList listTasks(Principal user) throws Exception {
      requireSiteAdmin(user);
      // No organizationid filter exposed in this cut (spec §11: list_schedule_tasks takes no
      // arguments) -- getScheduleTasks is already permission-scoped by repository.getScheduleTasks
      // regardless of this parameter.
      return scheduleGateway.getScheduleTasks(null, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/schedule/tasks/{taskId}")
   public ScheduleTaskView getTask(@PathVariable("taskId") String taskId, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return new ScheduleTaskView(taskId,
         scheduleGateway.getScheduleTask(taskId, null, user),
         scheduleGateway.getTaskConditionsLenient(taskId, null, user),
         scheduleGateway.getTaskActionsLenient(taskId, null, user));
   }

   /**
    * Resolves a schedule-task change plan without mutating anything. See {@code
    * AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/preview")
   public ResolvedPlan preview(@RequestBody ScheduleChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed schedule-task change plan, all-or-nothing. Same status contract as {@code
    * AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}, never a
    * non-200 meaning "partially applied".
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/apply")
   public ApplyResult apply(@RequestBody ScheduleApplyRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminAiController#requireSiteAdmin} -- see there. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(inetsoft.web.security.auth.MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(inetsoft.web.security.auth.MissingResourceException ex) {
      return Map.of("status", "not-found", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   @ExceptionHandler(AdminChangesetApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminChangesetApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final AdminScheduleGateway scheduleGateway;
   private final ScheduleChangePlanService planService;
   private final ScheduleChangesetApplyService applyService;
}
