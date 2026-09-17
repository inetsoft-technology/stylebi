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
package inetsoft.web.admin.ai.plugins;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.content.plugins.model.DriverList;
import inetsoft.web.admin.content.plugins.model.PluginsModel;
import inetsoft.web.admin.upload.MavenUploadRequest;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the {@code driver_plugin_management} admin-chat tools (03-reconcile.md):
 * {@code list_drivers_and_plugins}/{@code upload_driver_or_plugin}/{@code scan_uploaded_drivers}/
 * {@code install_driver_or_plugin}/{@code remove_driver_or_plugin}. Placed in
 * {@code community/core}, not {@code enterprise/}: it wraps {@link inetsoft.web.admin.content
 * .plugins.PluginsService}/{@link inetsoft.web.admin.upload.UploadService}, both community-tier
 * dependencies -- so, like {@code AdminFileBackupController}, this area works on a
 * community-only deployment too.
 *
 * <p>The plugins-area endpoints (list/scan/install/remove) reuse {@code @Secured}'s real, existing
 * {@code settings/content/drivers-and-plugins} resource, the same one
 * {@code PluginsController} itself uses. The upload endpoint is governed by a *different*
 * permission ({@link ResourceType#UPLOAD_DRIVERS}) that {@code UploadController} checks as a
 * {@code Principal}-based call rather than a {@code @Secured} annotation -- this controller
 * cannot reuse {@code UploadController}'s endpoint directly (it is not mapped under
 * {@code /api/wiz/**}, so it is unreachable through the admin-chat broker's bearer-token
 * transport), so {@link AdminPluginService#upload} replicates that same check explicitly instead.
 */
@RestController
public class AdminPluginController {
   @Autowired
   public AdminPluginController(AdminPluginService pluginService) {
      this.pluginService = pluginService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/drivers-and-plugins",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/plugins")
   public PluginsModel list(Principal user) throws Exception {
      requireSiteAdmin(user);
      return pluginService.list(user);
   }

   /**
    * No {@code @Secured} here -- deliberately checked against {@link ResourceType#UPLOAD_DRIVERS}
    * inside {@link AdminPluginService#upload} instead, matching {@code UploadController}'s own
    * permission story for this exact upload type. Still requires the same bearer/site-admin gate
    * every other admin-chat endpoint requires.
    */
   @PostMapping("/api/wiz/v1/admin/plugins/upload")
   public Map<String, String> upload(@RequestParam("file") MultipartFile file, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return pluginService.upload(file, user);
   }

   /**
    * Same rationale as {@link #upload} -- checked against {@link ResourceType#UPLOAD_DRIVERS}
    * inside {@link AdminPluginService#uploadMaven} instead of a {@code @Secured} annotation.
    */
   @PostMapping("/api/wiz/v1/admin/plugins/upload/maven")
   public Map<String, Object> uploadMaven(@RequestBody MavenUploadRequest request, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return pluginService.uploadMaven(request.gav(), user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/drivers-and-plugins",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/plugins/drivers/scan/{uploadId}")
   public DriverList scan(@PathVariable("uploadId") String uploadId, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return pluginService.scan(uploadId, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/drivers-and-plugins",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/plugins/install")
   public PluginsModel install(@RequestBody AdminInstallDriverOrPluginRequest request,
                                Principal user) throws Exception
   {
      requireSiteAdmin(user);
      return pluginService.install(request, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/drivers-and-plugins",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/plugins/remove")
   public PluginsModel remove(@RequestBody AdminRemoveDriverOrPluginRequest request,
                              Principal user) throws Exception
   {
      requireSiteAdmin(user);
      return pluginService.remove(request, user);
   }

   /**
    * Same rationale and shape as {@code AdminAiController#requireSiteAdmin} - see there.
    */
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

   /**
    * {@link inetsoft.web.admin.upload.UploadService#add(String)} throws this when the given GAV
    * does not resolve to any file -- surfaced as a clear, field-named 400 rather than the
    * generic wrapped MCP tool error.
    */
   @ExceptionHandler(FileNotFoundException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleFileNotFound(FileNotFoundException ex) {
      return Map.of("status", "failed", "error", "gav: could not resolve " + ex.getMessage());
   }

   @ExceptionHandler(SecurityException.class)
   @ResponseStatus(HttpStatus.FORBIDDEN)
   @ResponseBody
   public Map<String, String> handleSecurityException(SecurityException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   private final AdminPluginService pluginService;
}
