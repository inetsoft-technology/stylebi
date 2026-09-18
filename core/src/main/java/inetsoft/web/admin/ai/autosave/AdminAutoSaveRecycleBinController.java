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
package inetsoft.web.admin.ai.autosave;

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
import java.util.Optional;

/**
 * REST controller for the "Auto Save Recycle Bin" admin-plugin area (Redmine #76719 Gap 2). Lists,
 * restores, and deletes crash/disconnect recovery drafts -- EM's "Auto Saved Asset Info" pane
 * (`auto-save-recycle-bin` components) -- a distinct feature from the ordinary (already-covered)
 * Recycle Bin area: these are in-progress, never-saved edits, not deleted assets.
 *
 * <p>Placed in {@code inetsoft.web.admin.ai.autosave} -- {@code community/core}, not
 * {@code enterprise/} -- because the underlying {@code AutoSaveUtils}/
 * {@code inetsoft.web.AutoSaveService} primitives this area wraps are themselves community-tier:
 * Enterprise Manager's own Auto Save Recycle Bin page is already available on a community-only
 * deployment, matching Recycle Bin/Script Library/Materialized Views' own "the underlying feature
 * is community, so this area is too" placement.
 *
 * <p>Talks to {@code AutoSaveUtils}/{@code AutoSaveServiceProxy} directly, NOT to
 * {@code AutoSaveController} (session-cookie/CSRF EM SPA endpoint under
 * {@code /api/em/content/repository/autosave/*}) -- the same "reuse the real service, not the
 * internal controller" rule every other wiz admin-chat area follows.
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} belt-and-suspenders shape every other
 * area's own controller uses. {@code @Secured} uses the same resource string
 * {@code AdminViewsheetController}/{@code AdminRecycleBinController} both already use,
 * {@code "settings/content/repository"}, since an auto-saved draft is content that lives under that
 * same repository tree before it is either saved for real or discarded.
 */
@RestController
public class AdminAutoSaveRecycleBinController {
   @Autowired
   public AdminAutoSaveRecycleBinController(AutoSaveRecycleBinService autoSaveRecycleBinService,
                                            AutoSaveRecycleBinChangePlanService planService,
                                            AutoSaveRecycleBinChangesetApplyService applyService)
   {
      this.autoSaveRecycleBinService = autoSaveRecycleBinService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/autosave-recyclebin")
   public AutoSaveRecycleBinEntryList list(Principal user) {
      requireSiteAdmin(user);
      return new AutoSaveRecycleBinEntryList(autoSaveRecycleBinService.listEntries(user));
   }

   /** {@code found: false} is a normal 200 response, not a 404 -- matching
    * {@code get_recycle_bin_entry}/{@code get_script_library_entry}'s own precedent. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/autosave-recyclebin/entry")
   public GetAutoSaveRecycleBinEntryResult get(@RequestParam("id") String id, Principal user) {
      requireSiteAdmin(user);
      Optional<AutoSaveRecycleBinEntryProjection> entry = autoSaveRecycleBinService.getEntry(id, user);
      return entry.map(GetAutoSaveRecycleBinEntryResult::of)
         .orElseGet(GetAutoSaveRecycleBinEntryResult::notFound);
   }

   /** Resolves an Auto Save Recycle Bin change plan without mutating anything. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/autosave-recyclebin/preview")
   public ResolvedPlan preview(@RequestBody AutoSaveRecycleBinChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /** Applies a reviewed Auto Save Recycle Bin changeset, all-or-nothing. Same status contract as
    * {@code AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/autosave-recyclebin/apply")
   public AutoSaveRecycleBinApplyResult apply(@RequestBody AutoSaveRecycleBinApplyRequest req,
                                              Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

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

   private final AutoSaveRecycleBinService autoSaveRecycleBinService;
   private final AutoSaveRecycleBinChangePlanService planService;
   private final AutoSaveRecycleBinChangesetApplyService applyService;
}
