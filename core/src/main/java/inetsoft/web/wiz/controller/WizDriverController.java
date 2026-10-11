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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.content.plugins.PluginsService;
import inetsoft.web.admin.content.plugins.model.CreateDriverPluginRequest;
import inetsoft.web.admin.content.plugins.model.PluginModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.upload.MavenUploadRequest;
import inetsoft.web.admin.upload.UploadFilesResponse;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC driver installation for the wiz portal's "Create Driver" wizard, the equivalent of the
 * native driver wizard (Data portal datasource editor and EM Drivers and Plugins).
 *
 * <p>The native endpoints live under {@code /api/em} and {@code /api/data}, which the wiz bearer
 * broker cannot reach, and the existing {@code AdminPluginController} set is gated for site
 * administrators and shaped for the agent-safety install flow. This controller only delegates to
 * the same {@code UploadService} and {@code PluginsService} the native wizard uses and applies the
 * native gate, {@code UPLOAD_DRIVERS}, on every endpoint except {@code capability}, which reports
 * that same permission the way {@code DataSourceSettingsModel.uploadEnabled} does. Nothing is
 * reimplemented here.</p>
 *
 * <p>A denial leaves as a {@code SecurityException}, which {@code WizControllerErrorHandler}
 * turns into a 403; this class must therefore never declare a local
 * {@code @ExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/wiz/databases/drivers")
public class WizDriverController {
   public WizDriverController(UploadService uploadService, PluginsService pluginsService,
                              DatabaseDatasourcesService databaseDatasourcesService,
                              SecurityEngine securityEngine)
   {
      this.uploadService = uploadService;
      this.pluginsService = pluginsService;
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.securityEngine = securityEngine;
   }

   @GetMapping("/capability")
   public Map<String, Boolean> getCapability(Principal principal) {
      return Map.of("uploadEnabled", databaseDatasourcesService.isUploadEnabled(principal));
   }

   @GetMapping("/plugins")
   public Map<String, List<String>> getPlugins(Principal principal) throws Exception {
      checkUploadDrivers(principal);
      List<String> ids = pluginsService.getModel(principal).plugins().stream()
         .map(PluginModel::id)
         .collect(Collectors.toList());
      return Map.of("pluginIds", ids);
   }

   @PostMapping("/upload")
   public ResponseEntity<Object> upload(
      @RequestParam("uploadedFiles") MultipartFile[] uploadedFiles,
      @RequestParam(name = "id", required = false) String id,
      Principal principal) throws Exception
   {
      checkUploadDrivers(principal);

      if(uploadedFiles.length == 0) {
         return badRequest("No files were uploaded.");
      }

      for(MultipartFile uploadedFile : uploadedFiles) {
         if(uploadedFile.isEmpty()) {
            return badRequest("An uploaded file is empty.");
         }

         String name = uploadedFile.getOriginalFilename();

         if(name == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return badRequest("Only .jar files can be uploaded: " + name);
         }
      }

      List<UploadedFile> files = Arrays.stream(uploadedFiles)
         .map(this::toUploadedFile)
         .collect(Collectors.toList());

      if(id == null) {
         String identifier = uploadService.add(files);
         return ResponseEntity.ok(response(identifier, files));
      }

      try {
         List<UploadedFile> all = uploadService.add(id, files);
         return ResponseEntity.ok(response(id, all));
      }
      catch(FileNotFoundException e) {
         return badRequest("Upload does not exist: " + id);
      }
   }

   @PostMapping("/upload/maven")
   public ResponseEntity<Object> uploadMaven(@RequestBody MavenUploadRequest request,
                                             Principal principal) throws Exception
   {
      checkUploadDrivers(principal);

      try {
         return ResponseEntity.ok(uploadService.add(request.gav()));
      }
      catch(Exception e) {
         return badRequest(e.getMessage() == null ? "Maven resolution failed." : e.getMessage());
      }
   }

   @GetMapping("/maven-search")
   public Map<String, List<String>> searchMaven(@RequestParam("q") String query,
                                                Principal principal) throws Exception
   {
      checkUploadDrivers(principal);
      return Map.of("results", uploadService.search(query));
   }

   @GetMapping("/scan/{id}")
   public Map<String, List<String>> scan(@PathVariable("id") String id, Principal principal)
      throws Exception
   {
      checkUploadDrivers(principal);
      return Map.of("drivers", pluginsService.scanDrivers(id, principal));
   }

   @PostMapping("/create")
   public Map<String, Boolean> create(@RequestBody CreateDriverPluginRequest request,
                                      Principal principal) throws Exception
   {
      checkUploadDrivers(principal);
      pluginsService.createDriverPlugin(request, principal);
      return Map.of("ok", true);
   }

   private void checkUploadDrivers(Principal principal) throws SecurityException {
      if(!securityEngine.checkPermission(
         principal, ResourceType.UPLOAD_DRIVERS, "*", ResourceAction.ACCESS))
      {
         throw new SecurityException("You do not have permission to upload drivers.");
      }
   }

   private UploadedFile toUploadedFile(MultipartFile upload) {
      return UploadedFile.builder()
         .fileName(Objects.requireNonNull(upload.getOriginalFilename()))
         .multipartFile(upload)
         .build();
   }

   private UploadFilesResponse response(String identifier, List<UploadedFile> files) {
      return UploadFilesResponse.builder()
         .identifier(identifier)
         .files(files.stream().map(UploadedFile::fileName).collect(Collectors.toList()))
         .build();
   }

   private ResponseEntity<Object> badRequest(String message) {
      return ResponseEntity.badRequest().body(Map.of("error", message));
   }

   private final UploadService uploadService;
   private final PluginsService pluginsService;
   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final SecurityEngine securityEngine;
}
