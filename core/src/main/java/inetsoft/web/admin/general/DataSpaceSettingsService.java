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
   public DataSpaceSettingsModel getModel(Principal principal) throws Exception {
      InetsoftConfig config = InetsoftConfig.getInstance();

      boolean assetWritePermission = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.SCHEDULE_TASK, InternalScheduledTaskService.ASSET_FILE_BACKUP,
         ResourceAction.WRITE);
      String assetName = assetWritePermission ? InternalScheduledTaskService.ASSET_FILE_BACKUP : "";

      return DataSpaceSettingsModel.builder()
         .keyValueType(config.getKeyValue().getType())
         .blobType(config.getBlob().getType())
         .assetBackupTaskName(assetName)
         .build();
   }

   public static String backup(BackupDataModel model) {
      String status;
      Catalog catalog = Catalog.getCatalog();
      File file = null;
      backupLock.lock();
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      Principal principal = ThreadContext.getContextPrincipal();
      ActionRecord record = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_BACKUP,
         "Storage", ActionRecord.OBJECT_TYPE_STORAGE, actionTimestamp,
         ActionRecord.ACTION_STATUS_FAILURE, "");

      try {
         deleteRedundantBackupFiles();

         // For the same backup, use the same timestamp
         String stamp = createBackupTimestamp();
         file = FileSystemService.getInstance().getCacheTempFile("backup", ".zip");
         boolean mapdbStorage = "mapdb".equals(InetsoftConfig.getInstance().getKeyValue().getType());

         try(OutputStream output = new FileOutputStream(file)) {
            KeyValueEngine keyValueEngine = KeyValueEngine.getInstance();
            BlobEngine blobEngine = BlobEngine.getInstance();
            StorageTransfer storageTransfer = mapdbStorage ? new ClusterStorageTransfer() :
               new DirectStorageTransfer(keyValueEngine, blobEngine);
            storageTransfer.exportContents(output);
         }

         String path = getBackFile(model != null ? model.dataspace() : null, stamp);
         ExternalStorageService.getInstance().write(path, file.toPath(), null);

         status = catalog.getString("Success");
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         LOG.error("Failed to back up storage: " + e.getMessage(), e);
         status = "Failed to back up storage: " + e.getMessage();
         record.setActionError(status);
         return status;
      }
      finally {
         if(file != null && file.exists()) {
            Tool.deleteFile(file);
         }

         backupLock.unlock();
         Audit.getInstance().auditAction(record, principal);
      }

      return status;
   }

   /**
    * backup count control by property "asset.backup.count",
    */
   private static void deleteRedundantBackupFiles() {
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

      ExternalStorageService storageService = ExternalStorageService.getInstance();
      List<String> zips = storageService.listFiles(BACKUP_FOLDER).stream()
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
            storageService.delete(BACKUP_FOLDER + File.separator + zips.get(i));
         }
         catch(IOException e) {
            LOG.error("Failed to delete backup file {}", zips.get(i), e);
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

   private static String getBackFile(String name, String timestamp) {
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

      name = BACKUP_FOLDER + "/" + name;
      name = ExternalStorageService.getInstance().getAvailableFile(name, 1);
      return name;
   }

   /**
    * Returns a set of included keys for a store ID. If a set is null or empty then all
    * blobs keys are allowed.
    */
   public static Set<String> getBlobIncludedKeys(String storeID) {
      Set<String> includedKeys = new HashSet<>();

      // only get plugins for which there is a data source created
      if("plugins".equals(storeID)) {
         try {
            XRepository rep = XFactory.getRepository();
            String[] names = rep.getDataSourceFullNames();

            for(String name : names) {
               XDataSource ds = rep.getDataSource(name);

               for(Plugin plugin : Plugins.getInstance().getPlugins()) {
                  if(ds instanceof JDBCDataSource) {
                     List<DriverService> services = plugin.getServices(DriverService.class);

                     if(!services.isEmpty()) {
                        for(DriverService service : services) {
                           if(service.matches(((JDBCDataSource) ds).getDriver(), null)) {
                              includedKeys.add(plugin.getId());
                           }
                        }
                     }
                  }
                  else if(ds instanceof TabularDataSource) {
                     List<TabularService> services = plugin.getServices(TabularService.class);

                     if(!services.isEmpty()) {
                        for(TabularService service : services) {
                           if(Tool.equals(service.getDataSourceType(), ds.getType())) {
                              includedKeys.add(plugin.getId());
                           }
                        }
                     }
                  }
               }
            }
         }
         catch(RemoteException e) {
            throw new RuntimeException(e);
         }
      }

      return includedKeys;
   }

   // Backups are in a fixed folder to ensure that we exclude backup files on our second backup.
   private static final String BACKUP_FOLDER = "backup";
   private static final String BACKUP_PATH_SPLIT = "-";

   private static final Lock backupLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(DataSpaceSettingsService.class);
}
