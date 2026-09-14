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
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.plugins.PluginsService;
import inetsoft.web.admin.content.plugins.model.*;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Backing service for the {@code driver_plugin_management} admin-chat tools (03-reconcile.md).
 * Thinly delegates to the existing, real, community-tier {@link PluginsService}/
 * {@link UploadService} beans -- no upload/install/scan/delete logic is reimplemented here.
 *
 * <p>{@link PluginsService#installPlugins}/{@code uninstallPlugins}/{@code scanDrivers}/
 * {@code createDriverPlugin} were package-private (called only from
 * {@code PluginsController}, in the same package); they were widened to {@code public} so this
 * class -- deliberately placed under {@code web/admin/ai/plugins}, matching every other
 * admin-chat area's own package convention, rather than in {@code content.plugins} itself -- can
 * call them directly instead of duplicating their logic.
 */
@Service
public class AdminPluginService {
   @Autowired
   public AdminPluginService(PluginsService pluginsService, UploadService uploadService,
                              SecurityEngine securityEngine)
   {
      this.pluginsService = pluginsService;
      this.uploadService = uploadService;
      this.securityEngine = securityEngine;
   }

   public PluginsModel list(Principal principal) throws Exception {
      return pluginsService.getModel(principal);
   }

   /**
    * Uploads a single local file for later scan/install. {@code uploadType} is always the literal
    * {@code "driver"} (the only value the generic {@code /api/em/upload} endpoint's own
    * {@code UploadController.checkUploadPermission} accepts for this area) -- checked against
    * {@link ResourceType#UPLOAD_DRIVERS} here explicitly, since that check is a
    * {@code Principal}-based one on {@code UploadController}, not a {@code @Secured} annotation
    * this controller could otherwise inherit by reuse.
    */
   public Map<String, String> upload(MultipartFile file, Principal principal) throws Exception {
      requireUploadDriversPermission(principal);

      if(file == null || file.isEmpty()) {
         throw new IllegalArgumentException("file: required and must not be empty");
      }

      String fileName = Objects.requireNonNull(file.getOriginalFilename(), "file: missing name");

      if(!hasExtension(fileName, ".jar") && !hasExtension(fileName, ".zip")) {
         throw new IllegalArgumentException(
            "file: must be a .jar (raw JDBC driver) or .zip (StyleBI plugin) file, got: " + fileName);
      }

      UploadedFile uploadedFile = UploadedFile.builder()
         .fileName(fileName)
         .multipartFile(file)
         .build();
      String uploadId = uploadService.add(List.of(uploadedFile));

      return Map.of("uploadId", uploadId, "fileName", fileName);
   }

   /**
    * Only meaningful for a bare {@code .jar} upload -- refuses loud, rather than silently
    * returning an empty list, both when the upload id itself is unknown/expired and when it scans
    * clean of any driver class (e.g. because it was actually a full plugin zip, which does not
    * need scanning at all).
    */
   public DriverList scan(String uploadId, Principal principal) throws Exception {
      if(uploadService.get(uploadId).isEmpty()) {
         throw new IllegalArgumentException(
            "uploadId: not found (it may have expired, or already been consumed by install)");
      }

      List<String> drivers = pluginsService.scanDrivers(uploadId, principal);

      if(drivers.isEmpty()) {
         throw new IllegalArgumentException(
            "uploadId: no JDBC driver classes found in this upload -- if this is a full StyleBI " +
            "plugin zip (not a bare driver jar), call install_driver_or_plugin directly instead " +
            "of scanning it first");
      }

      return DriverList.builder().drivers(drivers).build();
   }

   public PluginsModel install(AdminInstallDriverOrPluginRequest request, Principal principal)
      throws Exception
   {
      if(request.getUploadId() == null || request.getUploadId().isBlank()) {
         throw new IllegalArgumentException("uploadId: required");
      }

      if(!request.isAcknowledgeServerCodeExecution()) {
         throw new IllegalArgumentException(
            "acknowledgeServerCodeExecution: required and must be true -- installing a plugin or " +
            "driver executes arbitrary code as the server. Set this to true only after the human " +
            "reviewing this action has confirmed it.");
      }

      requireReviewOutcome(request.getReviewOutcome());

      AdminDriverPluginSpec spec = request.getAsDriverPlugin();
      String objectName = spec != null ? spec.getPluginId() : request.getUploadId();
      String status = AdminChangeRecord.STATUS_FAILED;

      try {
         if(spec != null) {
            CreateDriverPluginRequest createRequest = CreateDriverPluginRequest.builder()
               .uploadId(request.getUploadId())
               .pluginId(spec.getPluginId())
               .pluginName(spec.getPluginName())
               .pluginVersion(spec.getPluginVersion())
               .drivers(spec.getDrivers() == null ? List.of() : spec.getDrivers())
               .build();
            pluginsService.createDriverPlugin(createRequest, principal);
         }
         else {
            pluginsService.installPlugins(request.getUploadId(), principal);
         }

         PluginsModel result = pluginsService.getModel(principal);
         status = AdminChangeRecord.STATUS_VERIFIED;
         return result;
      }
      finally {
         writeInstallAudit(request, objectName, status, principal);
      }
   }

   /**
    * Installing a plugin/driver executes arbitrary code as the server -- unlike every other
    * verb in this class, its wrapped {@link PluginsService} methods write no audit trail of
    * their own, so this is the only place {@code task}/{@code reviewOutcome} (solicited and
    * validated above) are actually recorded. Uses {@link AdminChangeRecord} rather than a bare
    * {@link ActionRecord} because it has dedicated {@code taskDescription}/{@code reviewOutcome}
    * fields, matching every other admin-chat apply service in this package (e.g.
    * {@code ScheduleChangesetApplyService.writeAudit}).
    */
   private void writeInstallAudit(AdminInstallDriverOrPluginRequest request, String objectName,
                                   String status, Principal principal)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(request.getUploadId());
         record.setTaskDescription(request.getTask());
         record.setProperty(objectName);
         record.setObjectType(ActionRecord.OBJECT_TYPE_PLUG);
         record.setAction(ActionRecord.ACTION_NAME_CREATE);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setReviewOutcome(request.getReviewOutcome());
         record.setUserName(principal == null ? null : principal.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, principal);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every changeset
         // apply service in this package follows.
         LOG.error("Failed to write plugin install admin change audit record for upload {}",
                   request.getUploadId(), auditFailure);
      }
   }

   public PluginsModel remove(AdminRemoveDriverOrPluginRequest request, Principal principal)
      throws Exception
   {
      if(request.getPluginIds() == null || request.getPluginIds().isEmpty()) {
         throw new IllegalArgumentException("pluginIds: required and must not be empty");
      }

      if(!request.isAcknowledgeIrreversibleRemove()) {
         throw new IllegalArgumentException(
            "acknowledgeIrreversibleRemove: required and must be true -- set this to true only " +
            "after the human reviewing this action has confirmed it.");
      }

      requireReviewOutcome(request.getReviewOutcome());

      PluginsModel current = pluginsService.getModel(principal);
      Map<String, PluginModel> byId = new HashMap<>();

      for(PluginModel plugin : current.plugins()) {
         byId.put(plugin.id(), plugin);
      }

      List<PluginModel> toRemove = new ArrayList<>();

      for(String id : request.getPluginIds()) {
         PluginModel plugin = byId.get(id);

         if(plugin == null) {
            throw new IllegalArgumentException("pluginIds: not currently installed: " + id);
         }

         toRemove.add(plugin);
      }

      String status = AdminChangeRecord.STATUS_FAILED;

      try {
         pluginsService.uninstallPlugins(
            PluginsModel.builder().plugins(toRemove).build(), principal);
         status = AdminChangeRecord.STATUS_VERIFIED;
      }
      finally {
         writeRemoveAudit(request, toRemove, status, principal);
      }

      return pluginsService.getModel(principal);
   }

   /**
    * {@link PluginsService#uninstallPlugins} already writes one {@link ActionRecord} per plugin
    * removed -- this adds a second, additional record at this layer carrying {@code task}/
    * {@code reviewOutcome} (which {@code uninstallPlugins}'s own {@code ActionRecord} has no
    * field for), rather than widening {@code ActionRecord} itself or touching
    * {@code PluginsService}'s existing, deliberately minimal diff.
    */
   private void writeRemoveAudit(AdminRemoveDriverOrPluginRequest request,
                                  List<PluginModel> removed, String status, Principal principal)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(String.join(",", request.getPluginIds()));
         record.setTaskDescription(request.getTask());
         record.setProperty(
            removed.stream().map(PluginModel::id).collect(Collectors.joining(",")));
         record.setObjectType(ActionRecord.OBJECT_TYPE_PLUG);
         record.setAction(ActionRecord.ACTION_NAME_DELETE);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setReviewOutcome(request.getReviewOutcome());
         record.setUserName(principal == null ? null : principal.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, principal);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write plugin remove admin change audit record for {}",
                   request.getPluginIds(), auditFailure);
      }
   }

   private void requireReviewOutcome(String reviewOutcome) {
      if(reviewOutcome == null || reviewOutcome.isBlank()) {
         throw new IllegalArgumentException(
            "reviewOutcome: required and must not be blank -- a short note on what the human " +
            "reviewer confirmed before this action was taken.");
      }
   }

   private void requireUploadDriversPermission(Principal principal) throws SecurityException {
      if(!securityEngine.checkPermission(
         principal, ResourceType.UPLOAD_DRIVERS, "*", ResourceAction.ACCESS))
      {
         throw new SecurityException("You do not have permission to upload files.");
      }
   }

   private static boolean hasExtension(String fileName, String extension) {
      return fileName.toLowerCase(Locale.ROOT).endsWith(extension);
   }

   private final PluginsService pluginsService;
   private final UploadService uploadService;
   private final SecurityEngine securityEngine;

   private static final Logger LOG = LoggerFactory.getLogger(AdminPluginService.class);
}
