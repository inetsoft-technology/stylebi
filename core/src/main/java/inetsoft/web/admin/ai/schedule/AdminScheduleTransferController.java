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

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the schedule-task export/import area (Redmine #76719 Gap 3) -- a separate
 * surface from {@link AdminScheduleController}'s own schedule-task create/delete/run/stop area,
 * the same "own controller per area" convention every other feature in this plugin follows.
 *
 * <p>Export is a single, bare, synchronous action, matching {@code AdminAssetExportController}'s
 * own precedent -- nothing is mutated, so no preview/apply/planHash. Import goes through the full
 * stage/preview/apply/planHash/taskToken discipline, matching {@code AdminAssetImportController}'s
 * own precedent -- see {@link ScheduleTaskTransferService}'s own javadoc for why.
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's own
 * controller -- copied, not shared, matching this codebase's own precedent for that duplication.
 */
@RestController
public class AdminScheduleTransferController {
   @Autowired
   public AdminScheduleTransferController(ScheduleTaskTransferService transferService,
                                          ScheduleTaskImportChangePlanService planService,
                                          ScheduleTaskImportChangesetApplyService applyService)
   {
      this.transferService = transferService;
      this.planService = planService;
      this.applyService = applyService;
   }

   /**
    * Resolves the selection, walks dependencies, and produces a base64 XML export -- a single
    * synchronous call, matching {@code AdminAssetExportController#export}. Unlike that area's own
    * zip-shaped export, the result is returned directly in the response body (no separate
    * download step): a schedule-task export file is small XML text, not a multi-megabyte archive.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/transfer/export")
   public ScheduleTaskExportResult export(@RequestBody ScheduleTaskExportRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return transferService.export(req.getTaskIds(),
         Boolean.TRUE.equals(req.getIncludeDependencies()));
   }

   /**
    * Uploads and parses an export file -- no mutation yet. Base64-in-JSON, not multipart -- see
    * {@link ScheduleTaskStageRequest}'s own javadoc for why this area differs from {@code
    * AdminAssetImportController#stage}'s own multipart upload.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/transfer/stage")
   public ScheduleTaskStagingResult stage(@RequestBody ScheduleTaskStageRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);

      if(req == null || req.getXml() == null || req.getXml().trim().isEmpty()) {
         throw new IllegalArgumentException("xml: required and must not be empty");
      }

      return transferService.stage(req.getXml(), user);
   }

   /**
    * Resolves a schedule-task import plan without mutating anything. See {@link
    * AdminScheduleController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/transfer/preview")
   public ResolvedPlan preview(@RequestBody ScheduleTaskImportPlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed schedule-task import plan, all-or-nothing. Same status contract as {@link
    * AdminScheduleController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed},
    * never a non-200 meaning "partially applied".
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/schedule/transfer/apply")
   public ScheduleTaskImportApplyResult apply(@RequestBody ScheduleTaskImportApplyRequest req,
                                              @LinkUri String linkURI, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user, linkURI);
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

   @ExceptionHandler(MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(MissingResourceException ex) {
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

   private final ScheduleTaskTransferService transferService;
   private final ScheduleTaskImportChangePlanService planService;
   private final ScheduleTaskImportChangesetApplyService applyService;
}
