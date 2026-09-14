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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.content.repository.ExportAssetServiceProxy;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.service.BinaryTransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the repository asset export area (track-d-import-export/01-design.md
 * section 4). Same {@code requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior
 * area's own controller -- copied, not shared, matching this codebase's own precedent for that
 * duplication.
 *
 * <p><b>Community, not enterprise</b> (03-reconcile.md, 01-design.md section 5): {@code
 * ExportAssetController}/{@code ExportAssetService}/{@code DeployService} all physically live in
 * {@code community/core} -- confirmed by reading their actual file paths, not assumed -- so this
 * area works on a community-only deployment, unlike Viewsheets/Identities/Permissions/Data
 * Sources, which are all enterprise-gated because THEIR underlying service lives in {@code
 * enterprise}.
 */
@RestController
public class AdminAssetExportController {
   @Autowired
   public AdminAssetExportController(AdminAssetExportService exportService,
                                     ExportAssetServiceProxy exportAssetServiceProxy,
                                     BinaryTransferService binaryTransferService)
   {
      this.exportService = exportService;
      this.exportAssetServiceProxy = exportAssetServiceProxy;
      this.binaryTransferService = binaryTransferService;
   }

   /**
    * Resolves the selection, permission-filters it, computes dependents, and creates the export
    * zip -- a single synchronous call ({@code ExportAssetService.createExport} is confirmed
    * synchronous under the hood), not a kickoff+poll pair. Read-only from the product's own data
    * point of view: nothing is mutated, so no preview/apply/planHash (matches {@code
    * backup_storage}'s own precedent).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/repository/export")
   public RepositoryExportResult export(@RequestBody RepositoryExportRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return exportService.export(req, user);
   }

   /**
    * Streams the zip created by {@link #export}, then evicts the cache entry and deletes the temp
    * file server-side (the SAME one-shot behavior {@code ExportAssetController.downloadJar}
    * already has, replicated here rather than shared -- placed under {@code /api/wiz/**} so {@code
    * AdminAiCallerGuard}/the bearer-auth filter apply to it, matching every other admin-chat
    * binary/streaming endpoint's own placement).
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/repository/export/download/{exportId}")
   public void download(@PathVariable String exportId, HttpServletRequest req,
                        HttpServletResponse res, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      String filename = exportAssetServiceProxy.getFileNameFromID(exportId) + ".zip";
      String agent = req.getHeader("USER-AGENT");

      if(SUtil.isIE(agent)) {
         filename = Tool.replaceAll(Tool.encodeWebURL(filename), "+", " ");
      }
      else if(SUtil.isMozilla(agent)) {
         filename = new String(filename.getBytes(StandardCharsets.UTF_8));
      }

      res.setHeader("extension", "zip");
      res.setContentType("application/zip;charset=utf-8");

      if(!Tool.isFilePathValid(filename)) {
         filename = "invalid";
      }

      String header = "attachment; filename=\"" + filename + "\"";

      if(!SUtil.isHttpHeadersValid(header)) {
         header = "";
      }

      res.setHeader(HttpHeaders.CONTENT_DISPOSITION, header);
      res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      res.setHeader("Pragma", "no-cache");
      res.setHeader("Expires", "0");

      BinaryTransfer data = exportAssetServiceProxy.getJarFileBytes(exportId);

      if(data == null) {
         res.setStatus(HttpStatus.NOT_FOUND.value());
         return;
      }

      binaryTransferService.writeData(data, res.getOutputStream());
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

   private final AdminAssetExportService exportService;
   private final ExportAssetServiceProxy exportAssetServiceProxy;
   private final BinaryTransferService binaryTransferService;
}
