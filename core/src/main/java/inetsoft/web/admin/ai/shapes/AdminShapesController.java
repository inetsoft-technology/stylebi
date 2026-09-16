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
package inetsoft.web.admin.ai.shapes;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.file.StoredAssetPathValidator;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.content.dataspace.model.DataSpaceTreeModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the Custom Shapes admin-chat tools (01-design.md section 2.2/2.3):
 * {@code list_custom_shapes}/{@code preview_custom_shape_changes}/{@code apply_custom_shape_changes}.
 * Placed in {@code community/core}, not {@code enterprise/} -- every backing service
 * ({@link DataSpaceContentSettingsService}, {@code ImageShapes}, {@code DataSpace}) is
 * community-tier (01-design.md section 1.5).
 *
 * <p>Never calls {@code DataSpaceShapeTreeController}/{@code DataSpaceFolderSettingsController}/
 * {@code DataSpaceTreeController} -- {@link #list} resolves the target path itself from
 * {@code scope}/{@code path} and reads directly through {@link DataSpaceContentSettingsService
 * #getTree}, and {@link ShapeChangePlanService}/{@link ShapeChangesetApplyService} replicate the
 * real controllers' shape-aware permission branching and write/delete primitives directly
 * (01-design.md section 1.5). {@code @Secured} below reuses the real resource string as
 * belt-and-suspenders visibility only -- {@link #requireSiteAdmin} plus
 * {@link ShapeChangePlanService#requireShapesPermission} are the real, load-bearing gates, matching
 * every other admin-chat controller's own convention.
 */
@RestController
public class AdminShapesController {
   @Autowired
   public AdminShapesController(DataSpaceContentSettingsService dataSpaceContentSettingsService,
                                ShapeChangePlanService planService,
                                ShapeChangesetApplyService applyService)
   {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
      this.planService = planService;
      this.applyService = applyService;
   }

   /**
    * Never forwards a caller-supplied absolute path to any backing controller -- this is the
    * concrete fix for the {@code DataSpaceShapeTreeController} path-scope gap noted in
    * 01-design.md section 1.1: {@code path} is always resolved relative to {@code scope}'s own
    * shapes root here, then validated, before {@link DataSpaceContentSettingsService#getTree} ever
    * sees it.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/shapes/tree")
   public DataSpaceTreeModel list(@RequestParam("scope") String scope,
                                  @RequestParam(value = "path", required = false) String subPath,
                                  Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      String normalizedScope = ShapeChangePlanService.requireScope("scope", scope);
      String root = ShapeChangePlanService.resolveShapesRoot(normalizedScope);
      planService.requireShapesPermission(user, root);
      String normalizedSubPath = subPath == null
         ? "" : StoredAssetPathValidator.requirePath(subPath, "path");
      String resolvedPath = normalizedSubPath.isEmpty() ? root : root + "/" + normalizedSubPath;
      return dataSpaceContentSettingsService.getTree(resolvedPath);
   }

   /**
    * Resolves a custom-shape change plan ({@code upload}/{@code delete}) without mutating anything.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/shapes/preview")
   public ResolvedPlan preview(@RequestBody ShapeChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed custom-shape change plan. Every plan requires a Tier-2 backup (taken
    * synchronously inside {@link ShapeChangesetApplyService#apply} before any mutation).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/presentation/settings",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/shapes/apply")
   public ApplyResult apply(@RequestBody ShapeApplyRequest req, Principal user) throws Exception {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
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

   @ExceptionHandler(SecurityException.class)
   @ResponseStatus(HttpStatus.FORBIDDEN)
   @ResponseBody
   public Map<String, String> handleSecurityException(SecurityException ex) {
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

   @ExceptionHandler(AdminChangesetApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminChangesetApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private final ShapeChangePlanService planService;
   private final ShapeChangesetApplyService applyService;
}
