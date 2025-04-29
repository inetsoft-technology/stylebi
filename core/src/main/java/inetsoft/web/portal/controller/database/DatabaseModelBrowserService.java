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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.SearchComparator;
import inetsoft.web.portal.data.DataModelBrowserModel;
import inetsoft.web.portal.model.database.*;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

@Service
public class DatabaseModelBrowserService {
   public DatabaseModelBrowserService(DataSourceService dataSourceService,
                                      XRepository repository,
                                      PhysicalModelManagerService physicalModelManagerService,
                                      LogicalModelService modelService,
                                      SecurityEngine securityEngine,
                                      DataModelFolderManagerService folderManagerService)
   {
      this.dataSourceService = dataSourceService;
      this.repository = repository;
      this.physicalModelManagerService = physicalModelManagerService;
      this.modelService = modelService;
      this.securityEngine = securityEngine;
      this.folderManagerService = folderManagerService;
   }

   public DatabaseDataModelBrowserModel getDataModelBrowseModel(String database, String folder,
                                                                Principal principal, boolean allChild)
      throws Exception
   {
      DatabaseDataModelBrowserModel model = new DatabaseDataModelBrowserModel();
      boolean dbEditable =
         dataSourceService.checkPermission(database, ResourceAction.WRITE, principal);
      boolean dbDeletable =
         dataSourceService.checkPermission(database, ResourceAction.DELETE, principal);
      model.setDbEditable(dbEditable);
      model.setDbDeletable(dbDeletable);
      model.setDateFormat(Tool.getDateFormatPattern());

      AssetListBrowseModel listModel = new AssetListBrowseModel();
      boolean isRoot = dataSourceService.isRootDataModelFolder(folder);

      if(isRoot) {
         listModel.setEditable(dbEditable);
         listModel.setDeletable(dbDeletable);
      }
      else {
         String folderPath = database + "/" + folder;
         listModel.setEditable(dataSourceService.checkDataModelFolderPermission(
            folderPath, ResourceAction.WRITE, principal));
         listModel.setDeletable(dataSourceService.checkDataModelFolderPermission(
            folderPath, ResourceAction.DELETE, principal));
      }

      List<AssetItem> result = new ArrayList<>();

      if(isRoot) {
         result.addAll(getDataModelFolders(database, false, principal));
      }

      result.addAll(dataSourceService.getPhysicalModels(database, folder, principal, allChild));
      result.addAll(dataSourceService.getLogicalModels(database, folder, principal, allChild));
      listModel.setItems(result.toArray(new AssetItem[result.size()]));
      listModel.setDbPartitionCount(dataSourceService.getDatabasePartitionCount(database));
      model.setListModel(listModel);

      return model;
   }

   public DatabaseDataModelBrowserModel getSearchDataModelNames(String database, String folder, String query,
                                                                Principal principal, boolean allChild)
      throws Exception
   {
      DatabaseDataModelBrowserModel model = getDataModelBrowseModel(database, folder, principal, allChild);
      AssetListBrowseModel listModel = model.getListModel();
      AssetItem[] items = listModel.getItems();
      String[] names = Arrays.stream(items)
         .flatMap(item -> {
            if(item instanceof PhysicalModel && ((PhysicalModel) item).getExtendViews() != null) {
               return Stream.concat(Stream.of(item),
                  ((PhysicalModel) item).getExtendViews().stream());
            }
            else if(item instanceof LogicalModel &&
               ((LogicalModel) item).getExtendModels() != null)
            {
               return Stream.concat(Stream.of(item),
                  ((LogicalModel) item).getExtendModels().stream());
            }

            return Stream.of(item);
         })
         .filter((item) -> item.getName().toLowerCase().indexOf(query.toLowerCase()) != -1)
         .sorted((a, b) -> new SearchComparator.StringComparator(query).compare(a.getName(), b.getName()))
         .map((item) -> item.getName())
         .distinct()
         .toArray(String[]::new);
      listModel.setNames(names);
      listModel.setItems(null);

      return model;
   }

   public DatabaseDataModelBrowserModel getSearchDataModel(String database, String folder, String query,
                                                           Principal principal)
      throws Exception
   {
      DatabaseDataModelBrowserModel model = getDataModelBrowseModel(database, folder, principal, true);
      AssetListBrowseModel listModel = model.getListModel();
      AssetItem[] items = listModel.getItems();
      items = Arrays.stream(items)
         .map((item) -> filterItem(item, query))
         .filter((item) -> item != null)
         .sorted((a, b) -> new SearchComparator.DataModelBrowserComparator(query).compare(a, b))
         .toArray(AssetItem[]::new);
      listModel.setItems(items);

      return model;
   }

   /**
    * Filter the items and their children.
    * @param items items.
    * @param filter filter string.
    * @param <T>
    * @return
    */
   private <T extends AssetItem> List<T> filterItems(List<T> items, String filter) {
      List<T> result = new ArrayList<>();

      if(items == null || items.isEmpty()) {
         return result;
      }

      for(T item : items) {
         if(filterItem(item, filter) != null) {
            result.add(item);
         }
      }

      return result;
   }

   /**
    * Filter the item and children.
    * @param item item.
    * @param filter filter string.
    * @return
    */
   private AssetItem filterItem(AssetItem item, String filter) {
      if(item.getName().toLowerCase().indexOf(filter.toLowerCase()) != -1) {
         return item;
      }

      if(item instanceof LogicalModel) {
         LogicalModel logicalModel = (LogicalModel) item;
         List<LogicalModel> matchExtend = filterItems(logicalModel.getExtendModels(), filter);

         if(matchExtend != null && !matchExtend.isEmpty()) {
            ((LogicalModel) item).setExtendModels(matchExtend);

            return item;
         }
      }

      if(item instanceof PhysicalModel) {
         PhysicalModel physicalModel = (PhysicalModel) item;
         List<PhysicalModel> matchExtend = filterItems(physicalModel.getExtendViews(), filter);

         if(matchExtend != null && !matchExtend.isEmpty()) {
            ((PhysicalModel) item).setExtendViews(matchExtend);

            return item;
         }
      }

      return null;
   }

   public DataModelBrowserModel getBrowserModel(String database, boolean root, Principal principal)
      throws Exception
   {
      List<DataModelFolder> folders = getDataModelFolders(database, root, principal);

      return DataModelBrowserModel.builder()
         .addAllDataModelList(folders)
         .addAllCurrentFolder(getCurrentFolders(root))
         .root(root)
         .build();
   }

   private List<DataModelFolder> getCurrentFolders(boolean root) {
      List<DataModelFolder> result = new ArrayList<>();

      if(!root) {
         DataModelFolder folder = new DataModelFolder();
         folder.setName("");
         folder.setPath("/");
         result.add(folder);
      }

      return result;
   }

   private List<DataModelFolder> getDataModelFolders(String database, boolean listRoot,
                                                     Principal principal)
      throws Exception
   {
      List<DataModelFolder> result = new ArrayList<>();
      XDataModel dataModel = dataSourceService.getDataModel(database);

      if(dataModel == null) {
         return result;
      }

      if(listRoot) {
         DataModelFolder folderModel = new DataModelFolder();
         folderModel.setName("");
         folderModel.setPath("/");
         result.add(folderModel);
      }
      else {
         if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
            return result;
         }

         String[] folders = dataModel.getFolders();

         for(int i = 0; folders != null && i < folders.length; i++) {
            String folder = folders[i];
            String path = database + "/" + folder;

            if(!securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER, path,
               ResourceAction.READ))
            {
               continue;
            }

            boolean editable = securityEngine.checkPermission(principal,
               ResourceType.DATA_MODEL_FOLDER, path, ResourceAction.WRITE);
            boolean deletable = securityEngine.checkPermission(principal,
               ResourceType.DATA_MODEL_FOLDER, path, ResourceAction.DELETE);

            DataModelFolder folderModel = new DataModelFolder();
            folderModel.setName(folder);
            folderModel.setPath(folder);
            folderModel.setEditable(editable);
            folderModel.setDeletable(deletable);
            folderModel.setDatabaseName(database);
            result.add(folderModel);
         }
      }

      return result;
   }

   public void moveDataModels(String database, List<AssetItem> items, String folder,
                              Principal principal)
      throws Exception
   {
      XDataModel dataModel = this.dataSourceService.getDataModel(database);

      if(database == null || items == null || items.size() == 0 || folder == null) {
         return;
      }

      if(dataSourceService.isRootDataModelFolder(folder)) {
         if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
            throw new SecurityException(String.format("Unauthorized access to resource \"%s\" by %s",
               database, principal));
         }
      }
      else {
         String folderPath = database + "/" + folder;

         if(!securityEngine.checkPermission(principal,
            ResourceType.DATA_MODEL_FOLDER, folderPath, ResourceAction.WRITE))
         {
            throw new SecurityException(String.format("Unauthorized access to resource \"%s\" by %s",
               folderPath, principal));
         }
      }

      String[] folders = dataModel.getFolders();
      boolean isRoot = "/".equals(folder) || "".equals(folder);

      if(!isRoot && !ArrayUtils.contains(folders, folder)) {
         throw new FileExistsException(database + "/" + folder);
      }

      RenameDependencyInfo dinfo = new RenameDependencyInfo();

      for(AssetItem item : items) {
         if(item instanceof LogicalModel &&
            StringUtils.isEmpty(((LogicalModel) item).getParentModel()))
         {
            changeModelFolder(dataModel, item.getName(), folder, principal);
         }
         else if(item instanceof PhysicalModel &&
            StringUtils.isEmpty(((PhysicalModel) item).getParentView()))
         {
            changeViewFolder(dataModel, item.getName(), folder, principal);
         }
      }
   }

   private void changeModelFolder(XDataModel dataModel, String name, String folder,
                                  Principal principal)
      throws Exception
   {
      XLogicalModel logicalModel = dataModel.getLogicalModel(name);

      if(logicalModel == null) {
         return;
      }

      String database = dataModel.getDataSource();
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      String oldFolder = logicalModel.getFolder();
      boolean isRoot = folder == null || "/".equals(folder) || "".equals(folder);
      String oldPath = database + XUtil.DATAMODEL_FOLDER_SPLITER + (oldFolder == null ? name :
         oldFolder + XUtil.DATAMODEL_PATH_SPLITER + name);
      String newPath = database + XUtil.DATAMODEL_FOLDER_SPLITER + (isRoot ? name :
         folder + XUtil.DATAMODEL_PATH_SPLITER + name);
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
         ActionRecord.ACTION_NAME_MOVE, oldPath, ActionRecord.OBJECT_TYPE_LOGICAL_MODEL,
         null, ActionRecord.ACTION_STATUS_SUCCESS,
         "Target Entry: " + newPath);

      try {
         for(ResourceAction action : EnumSet.of(ResourceAction.WRITE, ResourceAction.DELETE)) {
            if(!modelService.checkPermission(database, logicalModel.getFolder(), name, null,
                                             action, principal))
            {
               throw new SecurityException(
                  String.format("Unauthorized access to resource \"%s\" by %s",
                                database + "/" + name, principal));
            }
         }

         if(Tool.equals(logicalModel.getFolder(), oldFolder)) {
            logicalModel.setFolder(isRoot ? null : folder);
            dataModel.addLogicalModel(logicalModel);
         }

         RenameInfo rinfo = new RenameInfo(oldFolder, folder,RenameInfo.LOGIC_MODEL | RenameInfo.FOLDER);
         rinfo.setOldPath(oldPath);
         rinfo.setNewPath(newPath);
         AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.LOGIC_MODEL,logicalModel.getDataSource() + "/" + name,null);
         List<AssetObject> entries = DependencyTransformer.getDependencies(oentry.toIdentifier());

         if(entries == null) {
            return;
         }

         for(AssetObject obj : entries) {
            dinfo.addRenameInfo(obj, rinfo);
         }

         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
         RenameDependencyInfo extendModelDependencyInfo =
            DependencyTransformer.createExtendModelDepInfoForFolderChanged(logicalModel, oldFolder, folder);

         if(extendModelDependencyInfo != null &&
            !ArrayUtils.isEmpty(extendModelDependencyInfo.getAssetObjects()))
         {
            RenameTransformHandler.getTransformHandler().addTransformTask(extendModelDependencyInfo);
         }
      }
      catch(Exception ex) {
         actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + newPath);
         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
            actionRecord.setActionTimestamp(actionTimestamp);
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   private void changeViewFolder(XDataModel dataModel, String name, String folder,
                                 Principal principal)
      throws Exception
   {
      XPartition partition = dataModel.getPartition(name);

      if(partition == null) {
         return;
      }

      boolean isRoot = folder == null || "/".equals(folder) || "".equals(folder);
      String database = dataModel.getDataSource();
      String oldPath = database + (partition.getFolder() == null ? "" :
         XUtil.DATAMODEL_FOLDER_SPLITER + partition.getFolder()) +
         XUtil.DATAMODEL_PATH_SPLITER + name;
      String newPath = database +  (isRoot ? "" : XUtil.DATAMODEL_FOLDER_SPLITER + folder) +
         XUtil.DATAMODEL_PATH_SPLITER + name;
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
          ActionRecord.ACTION_NAME_MOVE, oldPath,ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW,
         null, ActionRecord.ACTION_STATUS_SUCCESS,
         "Target Entry: " + newPath);

      try {
         if(partition.getFolder() == null &&
            !dataSourceService.checkPermission(database, ResourceAction.WRITE, principal) ||
            partition.getFolder() != null &&
            !dataSourceService.checkDataModelFolderPermission(
               database + "/" + partition.getFolder(), ResourceAction.WRITE, principal))
         {
            throw new SecurityException(
               String.format("Unauthorized access to resource \"%s\" by %s",
                  oldPath, principal.getName()));
         }

         RenameDependencyInfo dinfo = new RenameDependencyInfo();
         RenameInfo rinfo = new RenameInfo(partition.getFolder(), folder,
            RenameInfo.PARTITION | RenameInfo.FOLDER);
         rinfo.setOldPath(oldPath);
         rinfo.setNewPath(newPath);
         AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.PARTITION,database + "/" + name,null);
         List<AssetObject> entries = DependencyTransformer.getDependencies(oentry.toIdentifier());

         if(entries == null) {
            return;
         }

         for(AssetObject obj : entries) {
            dinfo.addRenameInfo(obj, rinfo);
         }

         DependencyTransformer.createExtendViewDepInfoForFolderChanged(
            dinfo, partition, oldPath, newPath, rinfo);
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);

         partition.setFolder(isRoot ? null : folder);
         dataModel.addPartition(partition);
      }
      catch(Exception ex) {
         actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + newPath);
         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
            actionRecord.setActionTimestamp(actionTimestamp);
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   public boolean deleteDataModels(String database, List<AssetItem> items, Principal principal)
      throws Exception
   {
      XDataModel dataModel = dataSourceService.getDataModel(database);

      if(dataModel == null) {
         throw new FileExistsException(database);
      }

      List<LogicalModel> logicalModels = new ArrayList<>();
      List<PhysicalModel> physicalViews = new ArrayList<>();
      List<DataModelFolder> folders = new ArrayList<>();

      for(AssetItem item : items) {
         if(item instanceof LogicalModel) {
            logicalModels.add((LogicalModel) item);
         }
         else if(item instanceof PhysicalModel) {
            physicalViews.add((PhysicalModel) item);
         }
         else if(item instanceof DataModelFolder) {
            folders.add((DataModelFolder) item);
         }
      }

      boolean allItemDeleted = true;

      for(DataModelFolder folder : folders) {
         allItemDeleted = allItemDeleted &&
            folderManagerService.deleteDataModelFolder(database, folder.getName(), principal);
      }

      for(LogicalModel logicalModel : logicalModels) {
         if(!StringUtils.isEmpty(logicalModel.getParentModel())) {
            boolean containsBase = logicalModels.stream()
               .anyMatch(model -> Tool.equals(model.getName(), logicalModel.getParentModel()));

            if(containsBase) {
               continue;
            }

            XLogicalModel parentModel = dataModel.getLogicalModel(logicalModel.getParentModel());

            if(parentModel != null) {
               modelService.removeModel(database, parentModel.getFolder(), logicalModel.getName(),
                  logicalModel.getParentModel(), principal);
            }
         }
         else {
            XLogicalModel model = dataModel.getLogicalModel(logicalModel.getName());

            if(model == null) {
               continue;
            }

            modelService.removeModel(database, model.getFolder(), logicalModel.getName(),
               null, principal );
         }
      }

      for(PhysicalModel physicalView : physicalViews) {
         if(!StringUtils.isEmpty(physicalView.getParentView())) {
            boolean containsBase = physicalViews.stream()
               .anyMatch(model -> Tool.equals(model.getName(), physicalView.getParentView()));

            if(containsBase) {
               continue;
            }

            XPartition parentPartition = dataModel.getPartition(physicalView.getParentView());

            if(parentPartition != null) {
               allItemDeleted = allItemDeleted &&
                  physicalModelManagerService.removeModel(database, parentPartition.getFolder(),
                     physicalView.getName(), physicalView.getParentView(), principal);
            }
         }
         else {
            XPartition partition = dataModel.getPartition(physicalView.getName());

            if(partition != null) {
               allItemDeleted = allItemDeleted &&
                  physicalModelManagerService.removeModel(database, partition.getFolder(),
                     physicalView.getName(), physicalView.getParentView(), principal);
            }
         }
      }

      return allItemDeleted;
   }

   public boolean dataModelFolderDuplicateCheck(String databasePath, String folderName)
      throws Exception
   {
      XDataModel dataModel = dataSourceService.getDataModel(databasePath);

      if(dataModel == null || dataModel.getFolders() == null) {
         return false;
      }

      return ArrayUtils.contains(dataModel.getFolders(), folderName);
   }

   /**
    * Rename a data model folder.
    * @param databasePath data base path.
    * @param folderName new folder name.
    * @param oldName old folder name.
    * @param principal
    * @throws Exception
    */
   public void renameDataModelFolder(String databasePath,
                                     String folderName,
                                     String oldName,
                                     Principal principal)
      throws Exception
   {
      XDataModel dataModel = dataSourceService.getDataModel(databasePath);

      if(dataModel == null || dataModel.getFolders() == null) {
         return;
      }

      if(!ArrayUtils.contains(dataModel.getFolders(), oldName)) {
         throw new FileNotFoundException(databasePath + "/" + oldName);
      }

      ActionRecord actionRecord = null;
      String auditOldPath = databasePath + "/" + oldName;
      String auditPath = databasePath + "/" + folderName;

      try {
         String actionName = ActionRecord.ACTION_NAME_RENAME;
         String objectType = ActionRecord.OBJECT_TYPE_FOLDER;
         actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, auditOldPath,
             objectType, null, ActionRecord.ACTION_STATUS_SUCCESS,
            "Target Entry: " + auditPath);
      }
      catch(Exception ignore) {
      }

      try {
         String[] partitions = dataModel.getPartitionNames();

         if(partitions != null) {
            for(String partitionName : partitions) {
               XPartition partition = dataModel.getPartition(partitionName);

               if(partition == null) {
                  continue;
               }

               if(Tool.equals(partition.getFolder(), oldName)) {
                  partition.setFolder(folderName);
                  dataModel.addPartition(partition);
               }
            }
         }

         RenameDependencyInfo dinfo = new RenameDependencyInfo();
         String ds = dataModel.getDataSource();

         for(String logicalModelName : dataModel.getLogicalModelNames()) {
            XLogicalModel logicalModel = dataModel.getLogicalModel(logicalModelName);

            if(logicalModel == null) {
               continue;
            }

            if(Tool.equals(logicalModel.getFolder(), oldName)) {
               logicalModel.setFolder(folderName);
               dataModel.addLogicalModel(logicalModel);
            }

            String opath = ds + XUtil.DATAMODEL_FOLDER_SPLITER + oldName + "^" + logicalModelName;
            String npath = ds + XUtil.DATAMODEL_FOLDER_SPLITER + folderName + "^" + logicalModelName;
            RenameInfo rinfo = new RenameInfo(oldName, folderName,RenameInfo.LOGIC_MODEL | RenameInfo.FOLDER);
            rinfo.setOldPath(opath);
            rinfo.setNewPath(npath);
            AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.LOGIC_MODEL,logicalModel.getDataSource() + "/" + logicalModelName,null);
            List<AssetObject> entries = DependencyTransformer.getDependencies(oentry.toIdentifier());

            if(entries == null) {
               continue;
            }

            for(AssetObject obj : entries) {
               dinfo.addRenameInfo(obj, rinfo);
            }
         }

         for(String partitionName : dataModel.getPartitionNames()) {
            XPartition partitionModel = dataModel.getPartition(partitionName);

            if(partitionModel == null) {
               continue;
            }

            if(Tool.equals(partitionModel.getFolder(), oldName)) {
               partitionModel.setFolder(folderName);
               dataModel.addPartition(partitionModel);
            }

            String opath = ds + XUtil.DATAMODEL_FOLDER_SPLITER + oldName + "^" + partitionName;
            String npath = ds + XUtil.DATAMODEL_FOLDER_SPLITER + folderName + "^" + partitionName;
            RenameInfo rinfo = new RenameInfo(oldName, folderName,RenameInfo.PARTITION | RenameInfo.FOLDER);

            rinfo.setOldPath(opath);
            rinfo.setNewPath(npath);
            AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.PARTITION,dataModel.getDataSource() + "/" + partitionName,null);
            List<AssetObject> entries = DependencyTransformer.getDependencies(oentry.toIdentifier());

            if(entries == null) {
               continue;
            }

            for(AssetObject obj : entries) {
               dinfo.addRenameInfo(obj, rinfo);
            }
         }

         Permission permission = securityEngine.getPermission(
            ResourceType.DATA_MODEL_FOLDER, databasePath + "/" + oldName);
         dataModel.removeFolder(oldName);
         dataModel.addFolder(folderName);
         repository.updateDataModel(dataModel);
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);

         if(permission != null) {
            securityEngine.removePermission(
               ResourceType.DATA_MODEL_FOLDER, databasePath + "/" + oldName);
            securityEngine.setPermission(
               ResourceType.DATA_MODEL_FOLDER, databasePath + "/" + folderName, permission);
         }
      }
      catch(Exception ex) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + auditPath);
         }

         LOG.error("Failed to rename " + auditOldPath + " to " + auditPath, ex);
      }
      finally {
         if(actionRecord != null) {
            Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
            actionRecord.setActionTimestamp(actionTimestamp);
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseModelBrowserService.class);
   private final DataSourceService dataSourceService;
   private final XRepository repository;
   private final PhysicalModelManagerService physicalModelManagerService;
   private final LogicalModelService modelService;
   private final SecurityEngine securityEngine;
   private final DataModelFolderManagerService folderManagerService;
}
