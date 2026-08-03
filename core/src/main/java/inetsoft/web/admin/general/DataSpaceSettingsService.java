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
package inetsoft.web.admin.general;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.InternalScheduledTaskService;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.DriverService;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularService;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.general.model.BackupDataModel;
import inetsoft.web.admin.general.model.DataSpaceSettingsModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DataSpaceSettingsService extends BackupSupport {
   @Autowired
   public DataSpaceSettingsService(SecurityEngine securityEngine,
                                   FileSystemService fileSystemService,
                                   KeyValueEngine keyValueEngine,
                                   BlobEngine blobEngine,
                                   ExternalStorageService externalStorageService)
   {
      this.securityEngine = securityEngine;
      this.fileSystemService = fileSystemService;
      this.keyValueEngine = keyValueEngine;
      this.blobEngine = blobEngine;
      this.externalStorageService = externalStorageService;
   }

   public DataSpaceSettingsModel getModel(Principal principal) throws Exception {
      InetsoftConfig config = InetsoftConfig.getInstance();

      boolean assetWritePermission = securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, InternalScheduledTaskService.ASSET_FILE_BACKUP,
         ResourceAction.WRITE) && securityEngine.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/schedule/tasks",
         ResourceAction.ACCESS);
      String assetName = assetWritePermission ? InternalScheduledTaskService.ASSET_FILE_BACKUP : "";

      return DataSpaceSettingsModel.builder()
         .keyValueType(config.getKeyValue().getType())
         .blobType(config.getBlob().getType())
         .assetBackupTaskName(assetName)
         .build();
   }

   public static String backup(BackupDataModel model) {
      return ConfigurationContext.getContext().getSpringBean(DataSpaceSettingsService.class)
         .doBackup(model).status();
   }

   public BackupResult doBackup(BackupDataModel model) {
      String status;
      Catalog catalog = Catalog.getCatalog();
      File file = null;
      backupLock.lock();
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      Principal principal = ThreadContext.getContextPrincipal();
      ActionRecord record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_BACKUP,
         "Storage", ActionRecord.OBJECT_TYPE_STORAGE, actionTimestamp,
         ActionRecord.ACTION_STATUS_FAILURE, "");
      String path;

      try {
         // Pruning trims `backup/` to asset.backup.count on the assumption that this call is about
         // to add a file back to that same folder. An AI snapshot writes to ai-snapshots/ instead,
         // so pruning here would delete an operator's backup and replace it with nothing.
         if(model == null || !model.aiSnapshot()) {
            deleteRedundantBackupFiles();
         }

         // For the same backup, use the same timestamp
         String stamp = createBackupTimestamp();
         file = this.fileSystemService.getCacheTempFile("backup", ".zip");

         try(OutputStream output = new FileOutputStream(file)) {
            StorageTransfer.create(this.keyValueEngine, this.blobEngine).exportContents(output);
         }

         path = getBackFile(model != null ? model.dataspace() : null, stamp,
                             model != null && model.aiSnapshot());
         this.externalStorageService.write(path, file.toPath(), null);

         if(model != null && model.aiSnapshot()) {
            deleteRedundantAiSnapshotFiles();
         }

         status = catalog.getString("Success");
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         LOG.error("Failed to back up storage", e);
         status = "Failed to back up storage: " + e.getMessage();
         record.setActionError(status);
         return new BackupResult(status, null);
      }
      finally {
         if(file != null && file.exists()) {
            Tool.deleteFile(file);
         }

         backupLock.unlock();
         Audit.getInstance().auditAction(record, principal);
      }

      return new BackupResult(status, path);
   }

   /**
    * backup count control by property "asset.backup.count",
    */
   private void deleteRedundantBackupFiles() {
      String backupCountProp = SreeEnv.getProperty("asset.backup.count");
      int backupCount = -1;

      try {
         backupCount = Integer.parseInt(backupCountProp);
      }
      catch(Exception ignore) {
      }

      if(backupCount < 1) {
         return;
      }

      List<String> zips = this.externalStorageService.listFiles(BACKUP_FOLDER).stream()
         .filter(f -> f.endsWith(".zip") && f.contains(BACKUP_PATH_SPLIT))
         .sorted((z1, z2) -> {
            long z1Time = getTimestamp(z1);
            long z2Time = getTimestamp(z2);

            return (int) (z1Time - z2Time);
         })
         .toList();


      if(zips.size() < backupCount) {
         return;
      }

      int deleteCount = zips.size() - backupCount;

      for(int i = 0; i <= deleteCount; i++) {
         try {
            this.externalStorageService.delete(BACKUP_FOLDER + File.separator + zips.get(i));
         }
         catch(IOException e) {
            LOG.error("Failed to delete backup file {}", zips.get(i), e);
         }
      }
   }

   /**
    * Bounds {@link #AI_SNAPSHOT_FOLDER}, which nothing else prunes - {@link
    * #deleteRedundantBackupFiles} only lists {@link #BACKUP_FOLDER}, and an admin-chat apply can
    * write one snapshot per call, forever, if uncatalogued (storage-scoped by default -
    * see AdminRiskClassifier) properties are touched repeatedly.
    *
    * <p>Deliberately a SEPARATE method from {@link #deleteRedundantBackupFiles} rather than a
    * shared helper: that method has a pre-existing off-by-one (its loop runs {@code i <=
    * deleteCount}, so it deletes one file more than {@code asset.backup.count} implies) that is
    * out of scope for this change - see the final-review deferred-findings note. This method
    * keeps exactly the configured count.
    *
    * <p>Retention is controlled by {@code ai.snapshot.count}, defaulting to 10 when absent or
    * unparsable; a value below 1 disables pruning entirely, matching {@code
    * deleteRedundantBackupFiles}'s convention for {@code asset.backup.count}.
    */
   private void deleteRedundantAiSnapshotFiles() {
      String countProp = SreeEnv.getProperty("ai.snapshot.count");
      int count = 10;

      if(countProp != null) {
         try {
            count = Integer.parseInt(countProp.trim());
         }
         catch(Exception ignore) {
         }
      }

      if(count < 1) {
         return;
      }

      List<String> zips = this.externalStorageService.listFiles(AI_SNAPSHOT_FOLDER).stream()
         .filter(f -> f.endsWith(".zip") && f.contains(BACKUP_PATH_SPLIT))
         .sorted((z1, z2) -> {
            long z1Time = getTimestamp(z1);
            long z2Time = getTimestamp(z2);

            return (int) (z1Time - z2Time);
         })
         .toList();

      if(zips.size() <= count) {
         return;
      }

      int deleteCount = zips.size() - count;

      for(int i = 0; i < deleteCount; i++) {
         try {
            this.externalStorageService.delete(AI_SNAPSHOT_FOLDER + File.separator + zips.get(i));
         }
         catch(IOException e) {
            LOG.error("Failed to delete AI snapshot file {}", zips.get(i), e);
         }
      }
   }

   private static long getTimestamp(String fileName) {
      int index = fileName.lastIndexOf(".");

      if(index >= 0 && fileName.substring(0, index).contains(BACKUP_PATH_SPLIT)) {
         fileName = fileName.substring(0, index);
      }

      String[] pathParts = fileName.split(BACKUP_PATH_SPLIT);

      if(pathParts.length < 1) {
         return -1;
      }

      String timestamp = pathParts[pathParts.length - 1];

      try {
         return Long.parseLong(timestamp);
      }
      catch(Exception ignore) {
      }

      return -1;
   }

   private String getBackFile(String name, String timestamp, boolean aiSnapshot) {
      name = name == null ? "data" : name;
      int idx = name.indexOf(".zip");

      if(StringUtils.isEmpty(timestamp)) {
         timestamp = createBackupTimestamp();
      }

      if(idx < 0) {
         name += BACKUP_PATH_SPLIT + timestamp + ".zip";
      }
      else {
         String prefix = name.substring(0, idx);
         name = prefix + BACKUP_PATH_SPLIT + timestamp + ".zip";
      }

      name = (aiSnapshot ? AI_SNAPSHOT_FOLDER : BACKUP_FOLDER) + "/" + name;
      name = this.externalStorageService.getAvailableFile(name, 1);
      return name;
   }

   // Backups are in a fixed folder to ensure that we exclude backup files on our second backup.
   private final SecurityEngine securityEngine;
   private final FileSystemService fileSystemService;
   private final KeyValueEngine keyValueEngine;
   private final BlobEngine blobEngine;
   private final ExternalStorageService externalStorageService;

   private static final String BACKUP_FOLDER = "backup";

   /**
    * Admin-chat snapshots live in their own folder so that {@link #deleteRedundantBackupFiles},
    * which lists only {@link #BACKUP_FOLDER}, cannot delete a snapshot an audit record still
    * references. Nothing currently prunes this folder - see the retention note in the admin-chat
    * design doc.
    */
   private static final String AI_SNAPSHOT_FOLDER = "ai-snapshots";

   private static final String BACKUP_PATH_SPLIT = "-";

   private static final Lock backupLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(DataSpaceSettingsService.class);
}
