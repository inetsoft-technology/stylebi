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
package inetsoft.web.admin.ai.scriptlibrary;

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
 * REST controller for the "Script Library" admin-plugin area (Redmine #76719 Gap 1). Structural
 * CRUD (rename/description-edit/create/delete) over a Script Library entry, matching what
 * Enterprise Manager's own Script Library settings-page editor
 * ({@code repository-script-settings-page}, {@code ScriptSettingsModel}) already does -- the
 * Permissions area already covers granting permissions ON a {@code SCRIPT}/{@code SCRIPT_LIBRARY}
 * resource; this area is the missing structural-CRUD half.
 *
 * <p>Placed in {@code inetsoft.web.admin.ai.scriptlibrary} -- {@code community/core}, not
 * {@code enterprise/} -- because the underlying {@code LibManager} primitive this area wraps is
 * itself community-tier: Enterprise Manager's own Script Library settings page is already
 * available on a community-only deployment, matching Recycle Bin/Materialized Views/Repository
 * asset export-import's own "the underlying feature is community, so this area is too" placement.
 *
 * <p>Talks to {@code LibManager} directly, NOT to {@code RepositoryScriptController} (session-
 * cookie/CSRF EM SPA endpoint) or {@code inetsoft.web.wiz.controller.ScriptLibraryController}
 * (composer-chat's own {@code READ}/{@code WRITE}-permission-scoped surface, the wrong permission
 * model for a site-administrator caller -- this area checks {@code ADMIN} on
 * {@code ResourceType.SCRIPT}, the same pair {@code RepositoryScriptController} itself uses).
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} belt-and-suspenders shape every
 * other area's own controller uses -- copied, not shared. {@code @Secured} uses the same resource
 * string {@code AdminViewsheetController}/{@code RepositoryScriptController} both already use,
 * {@code "settings/content/repository"}, since a script library entry is content that lives under
 * that same repository tree.
 */
@RestController
public class AdminScriptLibraryController {
   @Autowired
   public AdminScriptLibraryController(ScriptLibraryService scriptLibraryService,
                                       ScriptLibraryChangePlanService planService,
                                       ScriptLibraryChangesetApplyService applyService)
   {
      this.scriptLibraryService = scriptLibraryService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/script-library")
   public ScriptLibraryEntryList list(Principal user) {
      requireSiteAdmin(user);
      return new ScriptLibraryEntryList(scriptLibraryService.listEntries(user));
   }

   /** {@code found: false} is a normal 200 response, not a 404 -- matching
    * {@code get_recycle_bin_entry}/{@code get_viewsheet_folder}'s own precedent. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/script-library/entry")
   public GetScriptLibraryEntryResult get(@RequestParam("name") String name, Principal user) {
      requireSiteAdmin(user);
      Optional<ScriptLibraryEntryDetail> entry = scriptLibraryService.getEntry(name, user);
      return entry.map(GetScriptLibraryEntryResult::of)
         .orElseGet(GetScriptLibraryEntryResult::notFound);
   }

   /** Resolves a Script Library change plan without mutating anything. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/script-library/preview")
   public ResolvedPlan preview(@RequestBody ScriptLibraryChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /** Applies a reviewed Script Library changeset, all-or-nothing. Same status contract as
    * {@code AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/script-library/apply")
   public ScriptLibraryApplyResult apply(@RequestBody ScriptLibraryApplyRequest req, Principal user)
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

   private final ScriptLibraryService scriptLibraryService;
   private final ScriptLibraryChangePlanService planService;
   private final ScriptLibraryChangesetApplyService applyService;
}
