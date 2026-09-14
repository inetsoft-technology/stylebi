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
package inetsoft.web.admin.ai.repository;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the repository asset import area (track-d-import-export/01-design.md
 * section 4; 03-reconcile.md). Import mutates the repository and can overwrite existing assets,
 * so -- unlike export -- it goes through this plugin's full stage/preview/apply/planHash/
 * taskToken discipline, the same as every other mutating admin-chat area (03-reconcile.md: this
 * is the one point where the two independent designs disagreed, resolved in favor of the full
 * discipline specifically to avoid reopening the confirm-then-swap gap Schedule Tasks' own
 * taskToken digest exists to close).
 *
 * <p>Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's own
 * controller. Community, not enterprise -- see {@code AdminAssetExportController}'s own javadoc
 * for the shared placement rationale.
 */
@RestController
public class AdminAssetImportController {
   @Autowired
   public AdminAssetImportController(ImportAssetServiceProxy importService,
                                     RepositoryImportChangePlanService planService,
                                     AdminAssetImportApplyService applyService)
   {
      this.importService = importService;
      this.planService = planService;
      this.applyService = applyService;
   }

   /**
    * Uploads and parses a zip -- no mutation yet. Multipart, not base64-in-JSON (03-reconcile.md):
    * matches the one binary-upload precedent this plugin already has ({@code import_csv_table}/
    * {@code import_excel_table}), and avoids ~33% base64 inflation on the wire -- the upload is
    * still translated to base64 HERE, at the boundary, because the wrapped {@code
    * ImportAssetServiceProxy.setJarFile} itself takes a {@link FileData} (base64-in-JSON); no
    * change to that method.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping(value = "/api/wiz/v1/admin/repository/import/stage",
               consumes = "multipart/form-data")
   public ExportedAssetsModel stage(@RequestParam("file") MultipartFile file, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);

      if(file == null || file.isEmpty()) {
         throw new IllegalArgumentException("file: required and must not be empty");
      }

      String stagingToken = UUID.randomUUID().toString();
      FileData fileData = FileData.builder()
         .name(file.getOriginalFilename() == null ? "import.zip" : file.getOriginalFilename())
         .content(Base64.getEncoder().encodeToString(file.getBytes()))
         .build();
      return importService.setJarFile(stagingToken, fileData, user);
   }

   /**
    * Resolves the plan, writes nothing. {@code newerVersion: true} on the returned {@code
    * jarInfo} is relayed as a plain field, not a refusal -- the underlying server still accepts
    * it (03-reconcile.md).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/repository/import/preview")
   public RepositoryImportPlan preview(@RequestBody RepositoryImportRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed import plan. {@code overwrite: true} additionally requires {@code
    * acknowledgeOverwrite: true} whenever the resolved plan would overwrite any existing asset --
    * refused loud, naming the field and the affected paths, when omitted.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/repository/import/apply")
   public RepositoryImportApplyResult apply(@RequestBody RepositoryImportApplyRequest req,
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

   @ExceptionHandler(AdminAssetImportApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminAssetImportApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   @ExceptionHandler(AdminAssetImportApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminAssetImportApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final ImportAssetServiceProxy importService;
   private final RepositoryImportChangePlanService planService;
   private final AdminAssetImportApplyService applyService;
}
