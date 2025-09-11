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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.deploy.ExportJarProperties;
import inetsoft.web.session.IgniteSessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
public class ExportAssetController {
   @Autowired
   public ExportAssetController(DeployService deployService,
                                IgniteSessionRepository igniteSessionRepository,
                                ExportAssetServiceProxy exportAssetServiceProxy)
   {
      this.deployService = deployService;
      this.igniteSessionRepository = igniteSessionRepository;
      this.exportAssetServiceProxy = exportAssetServiceProxy;
   }

   @PostConstruct
   public void initializeCache() {
      Cluster.getInstance().getCache(ExportAssetService.CONTENT_CACHE_NAME);
      Cluster.getInstance().getCache(ExportAssetService.FILE_LOCATION_CACHE_NAME);
   }

   @PostMapping("/api/em/content/repository/export/check-permission")
   public void checkAssetPermission(@RequestBody() SelectedAssetModelList assets,
                                    HttpServletRequest request, Principal principal)
   {
      CompletableFuture<SelectedAssetModelList> future = new CompletableFuture<>();
      HttpSession session = request.getSession(true);
      session.setAttribute(PERM_ATTR, future);

      ThreadPool.addOnDemand(() -> {
         try {
            ThreadContext.setPrincipal(principal);
            SelectedAssetModelList list = deployService.filterEntities(assets, principal);
            future.complete(list);
         }
         catch(Exception e) {
            future.completeExceptionally(e);
         }
         finally {
            igniteSessionRepository.setSessionAttributeAndSave(session.getId(), PERM_ATTR, future);
         }
      });
   }

   @GetMapping("/api/em/content/repository/export/check-permission/status")
   public ResponseEntity<ExportStatusModel> getAssetPermissionStatus(HttpServletRequest request) {
      return getStatus(PERM_ATTR, request);
   }

   @GetMapping("/api/em/content/repository/export/check-permission/value")
   public SelectedAssetModelList getAssetPermissionValue(HttpServletRequest request) throws Exception {
      try {
         return getData(PERM_ATTR, request, SelectedAssetModelList.class);
      }
      finally {
         request.getSession(true).removeAttribute(PERM_ATTR);
      }
   }

   @PostMapping("/api/em/content/repository/export/get-dependent-assets")
   public void getDependentAssets(@RequestBody() SelectedAssetModelList selectedEntities,
                                  HttpServletRequest request, Principal principal)
   {
      CompletableFuture<RequiredAssetModelList> future = new CompletableFuture<>();
      HttpSession session = request.getSession(true);
      session.setAttribute(DEPS_ATTR, future);

      ThreadPool.addOnDemand(() -> {
         try {
            ThreadContext.setPrincipal(principal);
            RequiredAssetModelList list =
               deployService.getDependentAssetsList(selectedEntities.selectedAssets(), principal);
            future.complete(list);
         }
         catch(Exception e) {
            future.completeExceptionally(e);
         }
         finally {
            igniteSessionRepository.setSessionAttributeAndSave(session.getId(), DEPS_ATTR, future);
         }
      });
   }

   @GetMapping("/api/em/content/repository/export/get-dependent-assets/status")
   public ResponseEntity<ExportStatusModel> getDependentAssetsStatus(HttpServletRequest request) {
      return getStatus(DEPS_ATTR, request);
   }

   @GetMapping("/api/em/content/repository/export/get-dependent-assets/value")
   public RequiredAssetModelList getDependentAssetsValue(HttpServletRequest request)
      throws Exception
   {
      try {
         return getData(DEPS_ATTR, request, RequiredAssetModelList.class);
      }
      finally {
         request.getSession(true).removeAttribute(DEPS_ATTR);
      }
   }

   @PostMapping("/api/em/content/repository/export/create")
   public String createExport(HttpServletRequest req,
                              @RequestBody() ExportedAssetsModel exportedAssetsModel,
                              Principal principal)
   {
      String jobId = UUID.randomUUID().toString();
      String fileName = exportedAssetsModel.name();
      exportAssetServiceProxy.createExport(jobId, fileName, exportedAssetsModel, principal);
      return jobId;
   }

   @GetMapping("/api/em/content/repository/export/create/status/{exportID}")
   public ResponseEntity<ExportStatusModel> getCreateExportStatus(@PathVariable String exportID) {
      boolean isDone = exportAssetServiceProxy.checkExportStatus(exportID);
      return ResponseEntity.ok(ExportStatusModel.builder().ready(isDone).build());
   }

   @GetMapping("/em/content/repository/export/download/{exportID}")
   public void downloadJar(@PathVariable String exportID, HttpServletRequest req, HttpServletResponse res)
      throws Exception
   {
      System.out.println("downloadJar exportID: " + exportID);
      String filename = exportAssetServiceProxy.getFileNameFromID(exportID) + ".zip";
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

      byte[] fileBytes = exportAssetServiceProxy.getJarFileBytes(exportID);

      if(fileBytes == null || fileBytes.length == 0) {
         res.setStatus(HttpStatus.NOT_FOUND.value());
         return;
      }

      res.setContentLengthLong(fileBytes.length);

      try(OutputStream out = res.getOutputStream()) {
         out.write(fileBytes);
         out.flush();
      }
   }

   private ResponseEntity<ExportStatusModel> getStatus(String attr, HttpServletRequest request) {
      CompletableFuture<?> future =
         (CompletableFuture<?>) request.getSession(true).getAttribute(attr);

      if(future == null) {
         return ResponseEntity.notFound().build();
      }

      return ResponseEntity.ok(ExportStatusModel.builder()
                                  .ready(future.isDone())
                                  .build());
   }

   private <T> T getData(String attr, HttpServletRequest req, Class<T> type) throws Exception {
      CompletableFuture<?> future =
         (CompletableFuture<?>) req.getSession(true).getAttribute(attr);

      try {
         return type.cast(future.get());
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof MessageException) {
            throw (MessageException) e.getCause();
         }
         else {
            throw e;
         }
      }
   }

   private final ExportAssetServiceProxy exportAssetServiceProxy;
   private final DeployService deployService;
   private final IgniteSessionRepository igniteSessionRepository;
   private static final String PERM_ATTR =
      ExportAssetController.class.getName() + ".deployPermissions";
   private static final String DEPS_ATTR =
      ExportAssetController.class.getName() + ".deployDependencies";
   private static final String PROPS_ATTR =
      ExportAssetController.class.getName() + ".deployJarProperties";
}
