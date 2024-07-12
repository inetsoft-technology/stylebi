/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.deploy.ExportJarProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
public class ExportAssetController {
   @Autowired
   public ExportAssetController(DeployService deployService) {
      this.deployService = deployService;
   }

   @PostMapping("/api/em/content/repository/export/check-permission")
   public void checkAssetPermission(@RequestBody() SelectedAssetModelList assets,
                                    HttpServletRequest request, Principal principal)
   {
      CompletableFuture<SelectedAssetModelList> future = new CompletableFuture<>();
      request.getSession(true).setAttribute(PERM_ATTR, future);

      ThreadPool.addOnDemand(() -> {
         try {
            ThreadContext.setPrincipal(principal);
            SelectedAssetModelList list = deployService.filterEntities(assets, principal);
            future.complete(list);
         }
         catch(Exception e) {
            future.completeExceptionally(e);
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
      request.getSession(true).setAttribute(DEPS_ATTR, future);

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
   public void createExport(HttpServletRequest req,
                            @RequestBody() ExportedAssetsModel exportedAssetsModel,
                            Principal principal)
   {
      CompletableFuture<ExportJarProperties> future = new CompletableFuture<>();
      req.getSession(true).setAttribute(PROPS_ATTR, future);

      ThreadPool.addOnDemand(() -> {
         try {
            ThreadContext.setPrincipal(principal);
            ExportJarProperties properties = deployService.createExport(exportedAssetsModel, principal);
            future.complete(properties);
         }
         catch(Exception e) {
            Catalog catalog = Catalog.getCatalog();
            future.completeExceptionally(new MessageException(
               catalog.getString("common.repletAction.exportFailed", exportedAssetsModel.name()) +
                  " " + catalog.getString("repository.fileDeleted"), e));
         }
      });
   }

   @GetMapping("/api/em/content/repository/export/create/status")
   public ResponseEntity<ExportStatusModel> getCreateExportStatus(HttpServletRequest request) {
      return getStatus(PROPS_ATTR, request);
   }

   @GetMapping("/em/content/repository/export/download")
   public void downloadJar(HttpServletRequest req, HttpServletResponse res)
      throws Exception
   {
      ExportJarProperties properties = getData(PROPS_ATTR, req, ExportJarProperties.class);
      String filePath = properties.zipFilePath();
      File file = FileSystemService.getInstance().getFile(filePath);
      String filename = file.getName();
      String agent = req.getHeader("USER-AGENT");

      // @by stone, fix bug1240661668234, the problem still exist in IE7
      // need to download a patch named 322389
      if(SUtil.isIE(agent)) {
         filename = Tool.replaceAll(Tool.encodeWebURL(filename), "+", " ");
      }
      else if(SUtil.isMozilla(agent)) {
         filename =
            new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
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

      res.setHeader("Content-disposition", header);
      res.setHeader("Cache-Control", "");
      res.setHeader("Pragma", "");

      deployService.downloadJar(properties, in -> {
         try(OutputStream out = res.getOutputStream()) {
            Tool.copyTo(in, out);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to copy export JAR to HTTP response", e);
         }
      });
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

   private final DeployService deployService;
   private static final String PERM_ATTR =
      ExportAssetController.class.getName() + ".deployPermissions";
   private static final String DEPS_ATTR =
      ExportAssetController.class.getName() + ".deployDependencies";
   private static final String PROPS_ATTR =
      ExportAssetController.class.getName() + ".deployJarProperties";
}
