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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for the presentation admin-plugin area (01-spec.md section 10). Placed in
 * {@code community/core}, not {@code enterprise/} -- {@code PresentationSettingsController} and all
 * 16 sub-services live entirely in {@code community/core} with no enterprise Public API layer at all
 * (01-spec.md section 10), the same "no Public API layer" community-tier shape this run's own
 * {@code AdminClusterController}/{@code AdminLicensingController} already establish.
 *
 * <p>Never calls {@code PresentationSettingsController} itself -- wraps the 16 sub-services directly
 * (01-spec.md section 4a): that controller's own {@code @Secured} gate is checkPermission-mediated
 * and therefore inert for a wiz-tagged caller (section 4a), so the real, only gate for this new area
 * is {@link #requireSiteAdmin}, matching {@code AdminLicensingController.requireSiteAdmin}'s exact
 * shape. The {@code @Secured} annotations below reuse the real controller's own resource string as
 * belt-and-suspenders visibility to any tooling that enumerates {@code @Secured} endpoints -- not a
 * load-bearing check (section 4a).
 */
@RestController
public class AdminPresentationController {
   @Autowired
   public AdminPresentationController(PresentationSettingsAccess access,
                                      PresentationChangePlanService planService,
                                      PresentationChangesetApplyService applyService)
   {
      this.access = access;
      this.planService = planService;
      this.applyService = applyService;
   }

   /**
    * Reads one sub-model (when {@code subModel} is given) or all 16 (when omitted) -- mirroring
    * {@code getSettings}'s own full-builder response (01-spec.md section 3). {@code scope} is
    * required, no default. Every sub-model's secret-classified fields
    * ({@link PresentationSubModel#secretFields()}: {@code webMap.mapboxToken}/{@code googleKey} and
    * {@code share.slackUrl}/{@code googleChatUrl}) are masked (01-spec.md section 9) regardless of
    * which is requested -- including the no-{@code subModel} form, which is the shortest path an
    * agent has to all of them at once.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/presentation/settings")
   public PresentationGetResult getSettings(
      @RequestParam("scope") String scope,
      @RequestParam(value = "subModel", required = false) String subModelKey, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      boolean global = requireScopeParam(scope);
      Map<String, JsonNode> subModels = new LinkedHashMap<>();

      if(subModelKey == null) {
         for(PresentationSubModel subModel : PresentationSubModel.values()) {
            subModels.put(subModel.key(), projectedRead(subModel, user, global));
         }
      }
      else {
         PresentationSubModel subModel = PresentationSubModel.require(subModelKey);
         subModels.put(subModel.key(), projectedRead(subModel, user, global));
      }

      return new PresentationGetResult(global ? "global" : "organization", subModels);
   }

   /**
    * Resolves a presentation change plan (16-sub-model catalog, {@code update} verb only) without
    * mutating anything. See {@code AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/presentation/preview")
   public ResolvedPlan preview(@RequestBody PresentationChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed presentation change plan. Every plan requires a Tier-2 backup
    * (01-spec.md section 4/6/7/11 -- unconditional), taken synchronously inside
    * {@link PresentationChangesetApplyService#apply} before any mutation.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/presentation/apply")
   public PresentationApplyResult apply(@RequestBody PresentationApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   private JsonNode projectedRead(PresentationSubModel subModel, Principal user, boolean global)
      throws Exception
   {
      JsonNode node = PresentationJson.toNode(access.read(subModel, user, global));
      return PresentationJson.maskSecrets(subModel, node);
   }

   private static boolean requireScopeParam(String scope) {
      if(scope == null) {
         throw new IllegalArgumentException(
            "scope: required, must be \"global\" or \"organization\"");
      }

      String trimmed = scope.trim();

      if(PresentationChangeRequest.SCOPE_GLOBAL.equalsIgnoreCase(trimmed)) {
         return true;
      }

      if(PresentationChangeRequest.SCOPE_ORGANIZATION.equalsIgnoreCase(trimmed)) {
         return false;
      }

      throw new IllegalArgumentException(
         "scope: must be \"global\" or \"organization\", got \"" + scope + "\"");
   }

   /** Same rationale and shape as {@code AdminLicensingController#requireSiteAdmin} -- see there. */
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

   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final PresentationSettingsAccess access;
   private final PresentationChangePlanService planService;
   private final PresentationChangesetApplyService applyService;
}
