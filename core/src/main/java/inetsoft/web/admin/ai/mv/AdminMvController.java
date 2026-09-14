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
package inetsoft.web.admin.ai.mv;

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
import java.util.List;
import java.util.Map;

/**
 * REST controller for the materialized-view admin-plugin area. Same {@code requireSiteAdmin}/
 * {@code AdminAiCallerGuard} shape as {@code AdminScheduleController}/{@code AdminAiController} --
 * copied, not shared, matching the existing per-area precedent.
 *
 * <p>Community-tier (unlike Viewsheets/Data Sources/Identities/Licensing, which are enterprise-
 * gated): the wrapped {@code MVController}/{@code MVService}/{@code MVSupportService} carry no
 * enterprise license check anywhere, so this area is not gated to enterprise either -- matching
 * the same "match where the capability really lives" precedent Track A's recycle-bin area used.
 */
@RestController
public class AdminMvController {
   @Autowired
   public AdminMvController(AdminMvGateway mvGateway, MvChangePlanService planService,
                            MvChangesetApplyService applyService)
   {
      this.mvGateway = mvGateway;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/mv/status")
   public MvStatusView getStatus(
      @RequestParam(name = "assetIds", required = false) List<String> assetIds, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return mvGateway.getStatus(assetIds, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/mv/analyze")
   public AnalysisIdView analyze(@RequestBody MvAnalyzeRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);

      if(req == null || req.getViewsheetAssetId() == null || req.getViewsheetAssetId().isBlank()) {
         throw new IllegalArgumentException("viewsheetAssetId: a non-empty asset identifier is required");
      }

      String analysisId = mvGateway.analyze(req.getViewsheetAssetId(), req.isExpandGroups(),
         req.isBypassVpm(), req.isFullData(), req.isApplyParentVsParameters(), user);
      return new AnalysisIdView(analysisId);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/mv/analyze/{analysisId}")
   public MvAnalysisView getAnalysis(@PathVariable("analysisId") String analysisId, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return mvGateway.getAnalysis(analysisId, user);
   }

   /**
    * Resolves an MV change plan without mutating anything. See {@code
    * AdminScheduleController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/mv/preview")
   public ResolvedPlan preview(@RequestBody MvChangePlanRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed MV change plan, all-or-nothing. Same status contract as {@code
    * AdminScheduleController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed},
    * never a non-200 meaning "partially applied".
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/mv/apply")
   public ApplyResult apply(@RequestBody MvApplyRequest req, Principal user) throws Exception {
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

   @ExceptionHandler(AnalysisExpiredException.class)
   @ResponseStatus(HttpStatus.GONE)
   @ResponseBody
   public Map<String, String> handleAnalysisExpired(AnalysisExpiredException ex) {
      return Map.of("status", "analysisExpired", "error", String.valueOf(ex.getMessage()));
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

   private final AdminMvGateway mvGateway;
   private final MvChangePlanService planService;
   private final MvChangesetApplyService applyService;
}
