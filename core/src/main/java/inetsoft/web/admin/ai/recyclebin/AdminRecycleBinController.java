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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.security.*;
import inetsoft.web.RecycleBin;
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
import java.util.Optional;

/**
 * REST controller for the "Recycle Bin" admin-plugin area (track-a-recycle-bin/01-design.md
 * section 3, 03-reconcile.md). Placed in {@code inetsoft.web.admin.ai.recyclebin} --
 * {@code community/core}, NOT {@code enterprise/} -- because the underlying {@code RecycleBin}/
 * {@code RecycleUtils}/{@code RepositoryRecycleBinController} primitives this area wraps are
 * themselves community-tier: the EM console's own Recycle Bin page is already available on a
 * community-only deployment, so gating this new capability to enterprise-only would be an
 * artificial restriction relative to what the underlying feature actually is (a user decision
 * recorded in 03-reconcile.md, revising 01-design.md's own original enterprise placement). This
 * makes "Recycle Bin" the plugin's SECOND area not gated to enterprise-only -- the first being the
 * mixed-gating Repository-maintenance/backup area {@code AdminFileBackupController} already is.
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} belt-and-suspenders shape every
 * other area's own controller uses -- copied, not shared. {@code @Secured} uses the SAME resource
 * string {@code AdminViewsheetController}/{@code RepositoryObjectController}/{@code
 * RepositoryFolderController} all already use, {@code "settings/content/repository"}, since a
 * recycle bin entry is content that lived under that same repository tree before it was recycled.
 *
 * <p>{@code path} is a query parameter, not a path segment, on both GETs -- a recycle-bin path is
 * itself slash-delimited (e.g. {@code "Recycle Bin/<uuid>"}), and embedding it in the URL path
 * would 400 against a real embedded-Tomcat connector for the same encoded-slash reason {@code
 * AdminViewsheetController.getFolder}'s own javadoc documents.
 */
@RestController
public class AdminRecycleBinController {
   @Autowired
   public AdminRecycleBinController(RecycleBinService recycleBinService,
                                    RecycleBinChangePlanService planService,
                                    RecycleBinChangesetApplyService applyService)
   {
      this.recycleBinService = recycleBinService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/recycle-bin")
   public RecycleBinEntryList list(Principal user) {
      requireSiteAdmin(user);
      return new RecycleBinEntryList(recycleBinService.listEntries(user));
   }

   /**
    * {@code found: false} is a normal 200 response, not a 404 (matching {@code
    * get_viewsheet_folder}/{@code get_permission_grant}'s own precedent) -- true both when no
    * entry exists at {@code path} AND when one exists but is not visible to the caller (section 4
    * risk 5: never distinguishable from the outside).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/recycle-bin/entry")
   public GetRecycleBinEntryResult get(@RequestParam("path") String path, Principal user) {
      requireSiteAdmin(user);
      Optional<RecycleBin.Entry> entry = recycleBinService.getEntry(path, user);
      return entry.map(e -> GetRecycleBinEntryResult.of(recycleBinService.project(e)))
         .orElseGet(GetRecycleBinEntryResult::notFound);
   }

   /**
    * Resolves a recycle-bin change plan without mutating anything. See {@code
    * AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/recycle-bin/preview")
   public ResolvedPlan preview(@RequestBody RecycleBinChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed recycle-bin changeset, all-or-nothing. Same status contract as {@code
    * AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/recycle-bin/apply")
   public RecycleBinApplyResult apply(@RequestBody RecycleBinApplyRequest req, Principal user)
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

   private final RecycleBinService recycleBinService;
   private final RecycleBinChangePlanService planService;
   private final RecycleBinChangesetApplyService applyService;
}
