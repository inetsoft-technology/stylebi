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
package inetsoft.web.admin.ai.file;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.content.dataspace.DataSpaceFolderSettingsController;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the stored-asset admin-chat area (Redmine #76603 Track 1,
 * 01-design.md/03-reconcile.md). Wraps {@link DataSpaceContentSettingsService}/{@link
 * DataSpaceFolderSettingsController}'s own already-public methods directly for the two binary
 * downloads, and {@link AdminFileContentService}/{@link StoredAssetChangePlanService}/{@link
 * StoredAssetChangesetApplyService} for everything else -- never re-implementing their logic, per
 * 03-reconcile.md's own citation of {@code AdminFileBackupController} as the template this
 * mirrors.
 *
 * <p>{@code @Secured} names {@code settings/content/data-space}, the same resource {@code
 * DataSpaceFileSettingsController}/{@code DataSpaceTreeController} already use -- but per
 * 01-design.md Flagged Decision 5, this new endpoint is reachable under a DIFFERENT URL prefix
 * ({@code /api/wiz/v1/admin/file/content/*}, not {@code /api/em/content/data-space/**}), so it
 * gets none of whatever route-level protection the EM prefix has; {@link #requireSiteAdmin} is the
 * real, load-bearing gate for every endpoint below, not this annotation.
 */
@RestController
public class AdminFileContentController {
   @Autowired
   public AdminFileContentController(AdminFileContentService contentService,
                                     StoredAssetChangePlanService planService,
                                     StoredAssetChangesetApplyService applyService,
                                     DataSpaceContentSettingsService dataSpaceContentSettingsService,
                                     DataSpaceFolderSettingsController folderSettingsController)
   {
      this.contentService = contentService;
      this.planService = planService;
      this.applyService = applyService;
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
      this.folderSettingsController = folderSettingsController;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/file/content/tree")
   public StoredAssetListResult list(
      @RequestParam(value = "path", required = false, defaultValue = "") String path, Principal user)
   {
      requireSiteAdmin(user);
      return contentService.list(path);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/file/content/node")
   public StoredAssetNode getNode(@RequestParam("path") String path, Principal user) {
      requireSiteAdmin(user);
      return contentService.getNode(path);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/file/content/text")
   public StoredAssetContent getContent(
      @RequestParam("path") String path,
      @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
      Principal user)
   {
      requireSiteAdmin(user);
      return contentService.getContent(path, preview);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/file/content/download")
   public void download(@RequestParam("path") String rawPath, Principal user,
                        HttpServletRequest request, HttpServletResponse response)
      throws Exception
   {
      requireSiteAdmin(user);
      String path = StoredAssetPathValidator.requirePath(rawPath, "path");

      if(path.isEmpty()) {
         throw new IllegalArgumentException("path: required, and cannot be the DataSpace root");
      }

      contentService.requireExistingFile(path);
      dataSpaceContentSettingsService.downloadFile(
         path, AdminFileContentService.baseName(path), response, request);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/file/content/download-folder")
   public void downloadFolder(
      @RequestParam(value = "path", required = false, defaultValue = "") String rawPath,
      Principal user, HttpServletResponse response)
      throws Exception
   {
      requireSiteAdmin(user);
      String path = StoredAssetPathValidator.requirePath(rawPath, "path");
      contentService.requireExistingFolder(path);
      String downloadPath = path.isEmpty() ? "/" : path;
      String name = path.isEmpty() ? "Storage" : AdminFileContentService.baseName(path);
      folderSettingsController.downloadDataSpaceFolder(downloadPath, name, response);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/file/content/preview")
   public ResolvedPlan preview(@RequestBody StoredAssetChangePlanRequest req, Principal user) {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/file/content/apply")
   public StoredAssetApplyResult apply(@RequestBody StoredAssetApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminFileBackupController#requireSiteAdmin} -- see there. */
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

   @ExceptionHandler(StoredAssetNotFoundException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleNotFound(StoredAssetNotFoundException ex) {
      return Map.of("status", "not-found", "path", ex.path());
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

   private final AdminFileContentService contentService;
   private final StoredAssetChangePlanService planService;
   private final StoredAssetChangesetApplyService applyService;
   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private final DataSpaceFolderSettingsController folderSettingsController;
}
