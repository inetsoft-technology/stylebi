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
package inetsoft.web.admin.content.plugins;

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.jdbc.drivers.DriverPluginGenerator;
import inetsoft.uql.jdbc.drivers.DriverScanner;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.web.admin.content.plugins.model.*;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PluginsService {
   @Autowired
   public PluginsService(UploadService uploadService, SecurityEngine securityEngine) {
      this.uploadService = uploadService;
      this.securityEngine = securityEngine;
      plugins = Plugins.getInstance();
   }

   public PluginsModel getModel(Principal principal) throws Exception {
      checkPermission(principal);
      return PluginsModel.builder()
         .clustered(isCluster())
         .plugins(getPlugins())
         .build();
   }

   private List<PluginModel> getPlugins() {
      return plugins.getPlugins().stream()
         .filter(plugin -> !plugin.getId().startsWith("inetsoft.mv."))
         .map(plugin -> PluginModel.builder()
            .id(plugin.getId())
            .name(plugin.getName())
            .vendor(plugin.getVendor())
            .version(plugin.getVersion())
            .readOnly(plugin.isReadOnly())
            .build()
         )
         .collect(Collectors.toList());
   }

   public void installPluginsForTester(String testerPluginsDir, Principal principal) throws Exception {
      if(StringUtils.isEmpty(testerPluginsDir)) {
         return;
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();
      File plugins = fileSystemService.getFile(testerPluginsDir);

      if(!plugins.exists()) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("Directory not exist: {0}", testerPluginsDir));
      }

      if(!plugins.isDirectory()) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("Not a directory: {0}", testerPluginsDir));
      }

      File[] files = plugins.listFiles();
      List<UploadedFile> list = new ArrayList<>();

      for(int i = 0; i < files.length; i++) {
         list.add(UploadedFile.builder()
                     .file(files[i])
                     .fileName(files[i].getName())
                     .build());
      }

      String uploadId = uploadService.add(list);
      installPlugins(uploadId, principal);
   }

   /**
    * Installs plugins that user has uploaded
    */
   void installPlugins(String uploadId, Principal principal) throws Exception {
      checkPermission(principal);
      checkCluster();
      List<UploadedFile> pluginFiles = uploadService.get(uploadId)
         .orElseThrow(() -> new IllegalArgumentException("No uploaded files"));
      int installedPlugins = 0;

      try {
         for(UploadedFile pluginFile : pluginFiles) {
            if(installPluginFile(pluginFile, principal)) {
               ++installedPlugins;
            }
         }
      }
      finally {
         uploadService.remove(uploadId);
      }

      if(installedPlugins > 0) {
         plugins.validatePlugins();
      }

      if(installedPlugins < pluginFiles.size()) {
         throw new RuntimeException("Not all plugins were installed. See the log for details.");
      }
   }

   /**
    * Installs plugin file.
    */
   public boolean installPluginFile(UploadedFile fileInfo, Principal principal) throws Exception {
      checkPermission(principal);
      checkCluster();
      String name = fileInfo.fileName();

      // for IE11 full path
      if(name.contains("\\")) {
         name = name.substring(name.lastIndexOf("\\") + 1);
      }

      try(InputStream input = new FileInputStream(fileInfo.file())) {
         return plugins.installPlugin(input, name, false);
      }
   }

   /**
    * Delete plugin
    *
    * @param model Structure for encapsulating plugin information for deletion
    */
   void uninstallPlugins(PluginsModel model, Principal principal) throws Exception {
      checkPermission(principal);
      checkCluster();
      ArrayList<PluginModel> pluginsList = new ArrayList<>(model.plugins());
      String actionName = ActionRecord.ACTION_NAME_DELETE;
      String objectType = ActionRecord.OBJECT_TYPE_PLUG;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, null,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_FAILURE, null);

      for(PluginModel plugin : pluginsList) {
         if(actionRecord != null) {
            String objectName = plugin.name() == null ? "" : plugin.name();
            actionRecord.setObjectName(objectName);
         }

         try {
            Config.removePlugin(plugins.getPlugin(plugin.id()));
            plugins.uninstallPlugin(plugin.id());
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);

         }
         catch(IOException e) {
            actionRecord = null;
            LOG.warn("There was an error uninstalling plugin: " + plugin.name());
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
      }
   }

   List<String> scanDrivers(String uploadId, Principal principal) throws Exception {
      checkPermission(principal);
      Set<String> drivers = new TreeSet<>();
      Optional<List<UploadedFile>> files = uploadService.get(uploadId);

      if(files.isPresent()) {
         DriverScanner scanner = new DriverScanner();

         for(UploadedFile upload : files.get()) {
            drivers.addAll(scanner.scan(upload.file()));
         }
      }

      return new ArrayList<>(drivers);
   }

   void createDriverPlugin(CreateDriverPluginRequest request, Principal principal)
      throws Exception
   {
      checkPermission(principal);
      checkCluster();
      Optional<List<UploadedFile>> files = uploadService.get(request.uploadId());

      if(files.isPresent()) {
         File pluginFile = FileSystemService.getInstance().getCacheTempFile("plugin", ".zip");

         try {
            UploadedFile[] jars = files.get().toArray(new UploadedFile[0]);
            new DriverPluginGenerator().generatePlugin(
               request.pluginId(), request.pluginVersion(), request.pluginName(),
               request.drivers().toArray(new String[0]), jars, pluginFile);

            try(InputStream input = Files.newInputStream(pluginFile.toPath())) {
               plugins.installPlugin(input, request.pluginId() + "-" +
                  request.pluginVersion() + ".zip", false);
            }
         }
         finally {
            Tool.deleteFile(pluginFile);
         }
      }
      else {
         throw new IllegalStateException("Upload does not exist: " + request.uploadId());
      }
   }

   private void checkPermission(Principal principal) throws UnauthorizedAccessException {
      try {
         boolean allowed =  securityEngine.checkPermission(
            principal, ResourceType.EM, "*", ResourceAction.ACCESS) &&
            securityEngine.checkPermission(
               principal, ResourceType.EM_COMPONENT, "/settings/content/drivers-and-plugins", ResourceAction.ACCESS) ||
            securityEngine.checkPermission(
               principal, ResourceType.UPLOAD_DRIVERS, "*", ResourceAction.ACCESS);

         if(!allowed) {
            throw new UnauthorizedAccessException("Not authorized");
         }
      }
      catch(SecurityException e) {
         throw new UnauthorizedAccessException("Failed to check authorization", e);
      }
   }

   private boolean isCluster() {
      return "server_cluster".equals(SreeEnv.getProperty("server.type")) ||
         ScheduleClient.getScheduleClient().isCluster();
   }

   private void checkCluster() {
      if(isCluster()) {
         throw new IllegalStateException(
            "Plugins cannot be installed at runtime in a clustered environment, you will need " +
            "to do this manually on each instance.");
      }
   }

   private final Plugins plugins;
   private final UploadService uploadService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(PluginsService.class);
}
