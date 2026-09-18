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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the viewsheets admin-plugin area (01-spec.md section 10). Same {@code
 * requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's own controller --
 * copied, not shared, matching the existing precedent for that duplication.
 *
 * <p>{@code @Secured} uses {@code ResourceType.EM_COMPONENT}/{@code "settings/content/repository"}/
 * {@code ResourceAction.ACCESS} -- the SAME resource string {@code RepositoryObjectController}/
 * {@code RepositoryFolderController} both already use (section 10's instruction), confirmed by
 * reading both classes' own {@code @Secured} annotations directly in this checkout. This is
 * belt-and-suspenders only (section 4a): {@code checkPermission} (which {@code @Secured} ultimately
 * resolves through) is a no-op for every admin-chat caller, so {@code requireSiteAdmin}'s direct
 * {@code isSiteAdmin} read below is the real gate for this caller population.
 *
 * <p>{@code GET .../folder} takes {@code path} as a query parameter, not a path segment/variable --
 * see {@link #getFolder} for why (a path-segment shape 400s against a real embedded-Tomcat
 * connector for any non-root folder path; fixed R1, 07-fix-r1-java.md).
 */
@RestController
public class AdminViewsheetController {
   @Autowired
   public AdminViewsheetController(ViewsheetService viewsheetApiService,
                                   ViewsheetFolderService folderService,
                                   ViewsheetChangePlanService planService,
                                   ViewsheetChangesetApplyService applyService)
   {
      this.viewsheetApiService = viewsheetApiService;
      this.folderService = folderService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/viewsheets")
   public SheetList listViewsheets(Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return viewsheetApiService.getViewsheets(user);
   }

   /**
    * Track B/Redmine #76604 Gap7: lists worksheets the same way {@link #listViewsheets} lists
    * viewsheets, but reads through {@link ViewsheetChangePlanService#getFilteredWorksheets}
    * rather than {@code WorksheetService.getWorksheets} directly -- the latter does not filter
    * recycle-bin entries or replicate the {@code security.exposedefaultorgtoall} host-org branch
    * the way {@code ViewsheetService.getViewsheets} already does (section 4.3/R3), and this
    * area's own list_worksheets tool must not expose a soft-deleted worksheet as a live,
    * targetable assetId.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/worksheets")
   public SheetList listWorksheets(Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.getFilteredWorksheets(user);
   }

   /**
    * Wraps the new {@link ViewsheetFolderService#getFolder}, not a {@code ViewsheetService}
    * method (section 3 -- no folder read method exists there at all). {@code found: false} is a
    * normal 200 response, not a 404 (matching C.4's own {@code get_permission_grant} precedent).
    *
    * <p>{@code path} is a query parameter, not a path segment: a folder path is itself
    * slash-delimited (e.g. {@code "Examples/Old Folder"}), and embedding it in the URL path
    * requires the caller to send it {@code encodeURIComponent}-escaped, which turns {@code /} into
    * {@code %2F}. Embedded Tomcat rejects a literal {@code %2F} in the request URI by default
    * ({@code encodedSolidusHandling}), so a path-segment shape 400s before Spring MVC ever runs for
    * any non-root folder -- fixed R1 (07-fix-r1-java.md) by moving {@code path} to a query
    * parameter, matching every other identifier in this area (e.g. {@code owner} below).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/viewsheets/folder")
   public GetViewsheetFolderResult getFolder(@RequestParam("path") String path,
                                             @RequestParam(value = "owner", required = false)
                                             String owner,
                                             Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      IdentityID ownerId = ViewsheetFolderService.parseOwner(owner);
      String normalizedPath = ViewsheetFolderService.normalizeFolderPath(path, ownerId);
      return folderService.getFolder(normalizedPath, ownerId);
   }

   /**
    * Resolves a viewsheet/folder change plan without mutating anything. See {@code
    * AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/viewsheets/preview")
   public ResolvedPlan preview(@RequestBody ViewsheetChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed viewsheet/folder change plan, all-or-nothing. Same status contract as
    * {@code AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/viewsheets/apply")
   public ViewsheetApplyResult apply(@RequestBody ViewsheetApplyRequest req, Principal user)
      throws Exception
   {
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

   private final ViewsheetService viewsheetApiService;
   private final ViewsheetFolderService folderService;
   private final ViewsheetChangePlanService planService;
   private final ViewsheetChangesetApplyService applyService;
}
