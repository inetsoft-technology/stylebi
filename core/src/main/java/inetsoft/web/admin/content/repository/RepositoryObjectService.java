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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.DataSourceRenameException;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.LogLevel;
import inetsoft.web.*;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static inetsoft.uql.util.XUtil.DATAMODEL_FOLDER_SPLITER;

@Service
public class RepositoryObjectService {
   @Autowired
   public RepositoryObjectService(RepletRegistryService registryManager,
                                  ContentRepositoryTreeService treeService,
                                  SecurityProvider securityProvider,
                                  ResourcePermissionService resourcePermissionService,
                                  XRepository xRepository,
                                  RepositoryDashboardService repositoryDashboardService,
                                  DataModelFolderManagerService dataModelFolderManagerService,
                                  DataSourceRegistry dataSourceRegistry,
                                  LibManagerProvider libManagerProvider,
                                  RecycleBin recycleBin,
                                  DependencyHandler dependencyHandler,
                                  RenameTransformHandler renameTransformHandler,
                                  RepletRegistryManager repletRegistryManager,
                                  DashboardRegistryManager dashboardRegistryManager)
   {
      this.registryManager = registryManager;
      this.treeService = treeService;
      this.libManagerProvider = libManagerProvider;
      this.dataSourceRegistry = dataSourceRegistry;
      this.xRepository = xRepository;
      this.securityProvider = securityProvider;
      this.resourcePermissionService = resourcePermissionService;
      this.repositoryDashboardService = repositoryDashboardService;
      this.dataModelFolderManagerService = dataModelFolderManagerService;
      this.recycleBin = recycleBin;
      this.dependencyHandler = dependencyHandler;
      this.renameTransformHandler = renameTransformHandler;
      this.repletRegistryManager = repletRegistryManager;
      this.dashboardRegistryManager = dashboardRegistryManager;
   }

   public ConnectionStatus deleteNodes(TreeNodeInfo[] nodes, Principal principal, boolean force,
                                       boolean permanent) throws MessageException
   {
      ArrayList<TreeNodeInfo> trashNodes = new ArrayList<>();
      ArrayList<TreeNodeInfo> autoSaveNodes = new ArrayList<>();

      for(TreeNodeInfo node : nodes) {
         // checked for every node type before anything is deleted, so a batch that mixes an
         // own-org node with a node owned by another organization is refused as a whole
         RepositoryOwnerOrgCheck.checkOwnerOrg(node.owner(), principal);

         if(node.type() == RepositoryEntry.TRASHCAN) {
            trashNodes.add(node);
         }

         if(node.type() == RepositoryEntry.AUTO_SAVE_VS ||
            node.type() == RepositoryEntry.AUTO_SAVE_WS)
         {
            autoSaveNodes.add(node);
         }

         String path = node.path();

         if(node.type() == RepositoryEntry.VIEWSHEET || node.type() == RepositoryEntry.WORKSHEET) {
            AssetEntry entry = new AssetEntry(treeService.getAssetScope(node.path()),
               node.type() == RepositoryEntry.VIEWSHEET ?
                  AssetEntry.Type.VIEWSHEET : AssetEntry.Type.WORKSHEET,
               treeService.getUnscopedPath(node.path()), node.owner());
            path = node.path() + "&identifier=" + entry.toIdentifier();
         }

         checkPermission(node.type(), path, EnumSet.of(ResourceAction.DELETE), principal);
      }

      // Bug #77725, a data source or folder whose path is shared by the other one, checked for
      // every node before anything is deleted. A data source and the folder at its path that are
      // both selected are deleted together.
      Set<String> dataSourceFolders = new HashSet<>();
      Set<String> dataSources = new HashSet<>();

      for(TreeNodeInfo node : nodes) {
         if(isDataSourceFolderNode(node.type())) {
            dataSourceFolders.add(node.path());
         }
         else if(isDataSourceNode(node.type())) {
            dataSources.add(node.path());
         }
      }

      for(TreeNodeInfo node : nodes) {
         if(!dataSourceFolders.contains(node.path()) || !dataSources.contains(node.path())) {
            checkDataSourcePathClash(node.type(), node.path(), true);
         }
      }

      deleteAutoSaveNodes(autoSaveNodes, principal);
      List<TreeNodeInfo> list = new ArrayList<TreeNodeInfo>();

      for(TreeNodeInfo cnode : nodes) {
         boolean hasParentNode = false;

         for(TreeNodeInfo parent : nodes) {
            if(cnode.type() == RepositoryEntry.WORKSHEET_FOLDER &&
               parent.type() == RepositoryEntry.WORKSHEET_FOLDER &&
               cnode.path() != null && parent.path() != null &&
               cnode.path().contains(parent.path() + "/"))
            {
               hasParentNode = true;
               break;
            }
         }

         if(!hasParentNode) {
            list.add(cnode);
         }
      }

      nodes = list.toArray(new TreeNodeInfo[0]);

      for(TreeNodeInfo node : nodes) {
         String nodePath = node.path();

         if(nodePath.indexOf(IdentityID.KEY_DELIMITER) > 0) {
            String[] parts = Tool.split(nodePath, '^');

            if(parts.length > 2 && parts[2].contains(IdentityID.KEY_DELIMITER)) {
               parts[2] = IdentityID.getIdentityIDFromKey(parts[2]).name;
               nodePath = String.join("^", parts);
            }
         }

         String objectName = Util.getObjectFullPath(node.type(), nodePath, principal, node.owner());
         ActionRecord actionRecord = SUtil.getActionRecord(principal,
            ActionRecord.ACTION_NAME_DELETE, objectName, getActionRecordType(node.type()));
         ActionRecord dataSourceRecord = null;

         try {
            final RepletRegistry registry = repletRegistryManager.getRegistry(node.owner());

            switch(node.type()) {
            case RepositoryEntry.VIEWSHEET:
            case RepositoryEntry.WORKSHEET:
               AssetEntry.Type type = node.type() == RepositoryEntry.VIEWSHEET ?
                  AssetEntry.Type.VIEWSHEET : AssetEntry.Type.WORKSHEET;

               int scope = RecycleUtils.isInRecycleBin(node.path()) && node.owner() != null ?
                  AssetRepository.USER_SCOPE : treeService.getAssetScope(node.path());
               String path = treeService.getUnscopedPath(node.path());

               AssetEntry asset = new AssetEntry(scope, type, path, node.owner());

               // make sure it's a viewsheet or snapshot
               if(registryManager.getAssetEntry(asset.toIdentifier(), principal) == null) {
                  asset = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET_SNAPSHOT, path, node.owner());
               }

               if(permanent || RecycleUtils.isInRecycleBin(node.path())) {
                  AssetRepository assetRepository = AssetUtil.getAssetRepository(false);

                  try {
                     AbstractSheet assetSheet =
                        assetRepository.getSheet(asset, principal, true, AssetContent.CONTEXT);
                     RecycleBin.Entry binEntry = recycleBin.getEntry(path);
                     AssetEntry dasset = new AssetEntry(
                        binEntry.getOriginalScope(), type, binEntry.getOriginalPath(),
                        node.owner());
                     this.dependencyHandler.updateSheetDependencies(assetSheet, dasset, false);
                  }
                  catch(MissingAssetClassNameException e) {
                     LOG.error(
                        "Cannot update dependencies for corrupt asset {}", asset.getPath(), e);
                  }

                  assetRepository.removeSheet(asset, principal, true);
                  recycleBin.removeEntry(node.path());
               }
               else {
                  try {
                     //move sheet to bin
                     RecycleUtils.moveSheetToRecycleBin(asset, principal, recycleBin, force);
                  }
                  catch(MissingAssetClassNameException e) {
                     LOG.error("Cannot move corrupt asset {} to recycle bin", asset.getPath(), e);
                     actionRecord = null;
                     return new ConnectionStatus(
                        "corrupt:" + Catalog.getCatalog(principal).getString(
                           "em.content.deleteCorruptConfirm"));
                  }
               }

               break;
            case RepositoryEntry.DATA_SOURCE:
            case RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER:
               // Bug #77725, deleted with the folder at its path, and audited with it (Bug #77819)
               if(dataSourceFolders.contains(node.path())) {
                  actionRecord = null;
                  break;
               }

               ConnectionStatus dataSource = deleteDataSource(node.path(), force, principal);

               if(dataSource != null) {
                  actionRecord = getDeleteStatusRecord(actionRecord, dataSource);
                  return dataSource;
               }

               break;
            case RepositoryEntry.DATA_SOURCE_FOLDER:
               // Bug #77819, a data source at the path of the folder is deleted with it, so it is
               // audited with the outcome of the folder delete
               if(dataSources.contains(node.path())) {
                  dataSourceRecord = SUtil.getActionRecord(
                     principal, ActionRecord.ACTION_NAME_DELETE,
                     Util.getObjectFullPath(
                        RepositoryEntry.DATA_SOURCE, nodePath, principal, node.owner()),
                     ActionRecord.OBJECT_TYPE_DATASOURCE);
               }

               ConnectionStatus dataSourceFolder = removeDataSourceFolder(
                  node.path(), force, principal, dataSources.contains(node.path()));

               if(dataSourceFolder != null) {
                  actionRecord = getDeleteStatusRecord(actionRecord, dataSourceFolder);
                  return dataSourceFolder;
               }

               break;
            case RepositoryEntry.LOGIC_MODEL:
            case RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER:
            case RepositoryEntry.PARTITION:
            case RepositoryEntry.PARTITION | RepositoryEntry.FOLDER:
            case RepositoryEntry.VPM:
               String dataModelPath = node.path();

               if((node.type() & RepositoryEntry.FOLDER) != 0 ||
                  node.type() == RepositoryEntry.VPM)
               {
                  int idx = dataModelPath.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

                  if(idx > 0) {
                     dataModelPath = dataModelPath.substring(0, idx);
                  }
                  else {
                     dataModelPath = dataModelPath.contains("^") ?
                        dataModelPath.substring(0, dataModelPath.lastIndexOf("^")) : dataModelPath;
                  }

                  XDataModel dataModel = dataSourceRegistry.getDataModel(dataModelPath);

                  if(dataModel != null) {
                     if((node.type() & RepositoryEntry.LOGIC_MODEL) == RepositoryEntry.LOGIC_MODEL) {
                        XLogicalModel logicalModel = dataModel.getLogicalModel(node.label());

                        if(!force && logicalModel != null) {
                            String[] extendedLogicalModels = logicalModel.getLogicalModelNames();

                            if(extendedLogicalModels != null && extendedLogicalModels.length > 0) {
                               String msg = catalog.getString("Extended Model") +
                                  catalog.getString("common.datasource.goonAndmodelsDeleted",
                                                       String.join(",", extendedLogicalModels));
                               actionRecord = null;
                               return new ConnectionStatus(msg);
                            }
                        }

                        ConnectionStatus status = removeLogicalModel(dataModel, node.label(), force);

                        if(status != null) {
                           actionRecord = getDeleteStatusRecord(actionRecord, status);
                           return status;
                        }
                     }
                     else if((node.type() & RepositoryEntry.PARTITION) == RepositoryEntry.PARTITION) {
                        XPartition physicalView = dataModel.getPartition(node.label());

                        if(!force && physicalView != null) {
                           String[] extendedViews = physicalView.getPartitionNames();

                           if(extendedViews != null && extendedViews.length > 0) {
                              String msg = catalog.getString("Extended View") +
                                 catalog.getString("common.datasource.goonAndmodelsDeleted",
                                                   String.join(",", extendedViews));
                              actionRecord = null;
                              return new ConnectionStatus(msg);
                           }
                        }

                        boolean found = false;

                        for(String name : dataModel.getLogicalModelNames()) {
                           XLogicalModel lmodel = dataModel.getLogicalModel(name);

                           if(node.label().equals(lmodel.getPartition())) {
                              found = true;
                              break;
                           }
                        }

                        if(!found) {
                           String[] names = dataModel.getVirtualPrivateModelNames();

                           for(String name : names) {
                              VirtualPrivateModel vpm =
                                 dataModel.getVirtualPrivateModel(name);
                              Enumeration<VpmCondition> conds = vpm.getConditions();

                              while(conds.hasMoreElements()) {
                                 VpmCondition cond = conds.nextElement();

                                 if(cond.getType() == VpmCondition.PHYSICMODEL &&
                                    node.label().equals(cond.getTable()))
                                 {
                                    found = true;
                                    break;
                                 }
                              }
                           }
                        }

                        if(found) {
                           throw new MessageException(catalog.getString("common.datasource.viewUsedByLogicalModel"));
                        }

                        dataModel.removePartition(node.label());
                        removeDataModelDependencies(
                           dataModelPath + "/" + node.label(), AssetEntry.Type.PARTITION, true);
                     }
                     else {
                        dataModel.removeVirtualPrivateModel(node.label());
                        removeDataModelDependencies(
                           dataModelPath + "/" + node.label(), AssetEntry.Type.VPM, false);
                     }
                  }
               }
               else {
                  String[] extendedModelPath = null;
                  String datasource;
                  String baseModel;
                  String extendModel;

                  String[] folderSplit = dataModelPath.split(Pattern.quote(DATAMODEL_FOLDER_SPLITER));

                  if(folderSplit.length == 2) {
                     extendedModelPath = folderSplit[1].split("\\^");
                     datasource = folderSplit[0];
                  }
                  else {
                     extendedModelPath = dataModelPath.split("\\^");
                     datasource = extendedModelPath[0];
                  }

                  baseModel = extendedModelPath[1];
                  extendModel = extendedModelPath[2];

                  if(extendedModelPath.length == 3 && datasource != null && baseModel != null &&
                     extendModel != null)
                  {
                     XDataModel dataModel = dataSourceRegistry.getDataModel(datasource);

                     if(dataModel != null) {
                        if(node.type() == RepositoryEntry.LOGIC_MODEL) {
                           XLogicalModel logicalModel = dataModel.getLogicalModel(baseModel);

                           if(logicalModel != null) {
                              logicalModel.removeLogicalModel(extendModel);
                              removeDataModelDependencies(
                                 datasource + "/" + baseModel + "/" + extendModel,
                                 AssetEntry.Type.LOGIC_MODEL, true);
                           }
                        }
                        else if(node.type() == RepositoryEntry.PARTITION) {
                           XPartition physicalView = dataModel.getPartition(baseModel);

                           if(physicalView != null) {
                              physicalView.removePartition(extendModel);
                              removeDataModelDependencies(
                                 datasource + "/" + baseModel + "/" + extendModel,
                                 AssetEntry.Type.PARTITION, true);
                           }
                        }
                     }
                  }
               }

               break;
            case RepositoryEntry.QUERY | RepositoryEntry.FOLDER:
               removeQueryFolder(node.label(), node.path());
               break;
            case RepositoryEntry.SCRIPT:
               libManagerProvider.getManager(principal).removeScript(node.label());
               libManagerProvider.getManager(principal).save();
               securityProvider.removePermission(ResourceType.SCRIPT, node.label());
               break;
            case RepositoryEntry.TABLE_STYLE:
               int index = node.path().lastIndexOf(LibManager.SEPARATOR);
               String folder = null;

               if(index >= 0) {
                  folder = node.path().substring(0, index);
               }

               for(XTableStyle style : libManagerProvider.getManager().getTableStyles(folder)) {
                  if(node.path().equals(style.getName())) {
                     libManagerProvider.getManager().removeTableStyle(style.getID());
                     break;
                  }
               }

               libManagerProvider.getManager().save();
               break;
            case RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER:
               AssetEventUtil.removeStyleFolder(node.path(), libManagerProvider.getManager());
               libManagerProvider.getManager().save();
               break;
            case RepositoryEntry.FOLDER:
            case RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER:
               if(RecycleUtils.isInRecycleBin(node.path())) {
                  removeFolder(node, registry);
               }
               else {
                  RecycleUtils.moveRepositoryFolderToRecycleBin(node.path(),
                     node.label(), node.owner(), principal, recycleBin);
               }

               break;
            case RepositoryEntry.WORKSHEET_FOLDER:
               if(RecycleUtils.isInRecycleBin(node.path())) {
                  removeWorksheetFolder(principal, node, force);
                  recycleBin.removeEntry(node.path());
               }
               else {
                  //move worksheet folder to recycle bin
                  String wsFolderPath = node.owner() != null ?
                     treeService.getUnscopedPath(node.path()) : node.path();
                  RecycleUtils.moveAssetFolderToRecycleBin(wsFolderPath, node.owner(),
                     principal, recycleBin, force);
               }

               break;
            case RepositoryEntry.DASHBOARD:
               this.repositoryDashboardService.delete(node.path(), node.owner(), principal);
               break;
            case RepositoryEntry.DATA_MODEL | RepositoryEntry.FOLDER:
               deleteDataModelFolder(node, principal);

               break;
            }

            if(RecycleUtils.isInRecycleBin(node.path())) {
               recycleBin.removeEntry(node.path());
            }
         }
         catch(ConfirmException confirmException) {
            // Bug #77819, a prompt to confirm the delete, the confirmed retry is audited
            actionRecord = null;
            String message = confirmException.getMessage();
            return new ConnectionStatus(message);
         }
         catch(MessageException msgException) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(msgException.getMessage());
            throw msgException;
         }
         catch(Exception e) {
            String errorMessage = e.getMessage();
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(errorMessage);
            LOG.error(errorMessage, e);
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);

               if(dataSourceRecord != null) {
                  dataSourceRecord.setActionStatus(actionRecord.getActionStatus());
                  dataSourceRecord.setActionError(actionRecord.getActionError());
                  Audit.getInstance().auditAction(dataSourceRecord, principal);
               }
            }
         }
      }

      return null;
   }

   /**
    * Gets the audit record of a delete that returned a status instead of deleting (Bug #77819).
    * A refusal is audited as a failure. A prompt to confirm the delete, e.g. of an item that has
    * dependencies, is not audited, the delete is audited when it's confirmed.
    *
    * @param record the audit record of the delete.
    * @param status the status returned by the delete.
    *
    * @return the record to audit, or null if it is not audited.
    */
   public static ActionRecord getDeleteStatusRecord(ActionRecord record, ConnectionStatus status) {
      if(!(status instanceof RefusedStatus)) {
         return null;
      }

      record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
      record.setActionError(status.getStatus());
      return record;
   }

   private void deleteDataModelFolder(TreeNodeInfo node, Principal principal) throws Exception {
      if(node == null || StringUtils.isEmpty(node.path())) {
         LOG.warn("Delete empty data model folder");
         return;
      }

      int splitIndex = node.path().lastIndexOf("/");

      if(splitIndex >= 0) {
         String dataBasePath = node.path().substring(0, splitIndex);
         String folderName = node.path().substring(splitIndex + 1);
         dataModelFolderManagerService.deleteDataModelFolder(dataBasePath, folderName, principal);
      }
      else {
         LOG.warn("Can't find data model folder: {}", node.path());
      }
   }

   private synchronized ConnectionStatus deleteDataSource(String dxname,
                                                          boolean force,
                                                          Principal principal)
   {
      ConnectionStatus status = checkDataSourceDelete(dxname, force, principal);

      if(status != null) {
         return status;
      }

      removeDataSource(dxname);

      return null;
   }

   /**
    * Checks that a data source may be deleted: it has no dependencies, unless forced, and the
    * user may delete it.
    *
    * @return the reason it may not be deleted, or null if it may.
    */
   private ConnectionStatus checkDataSourceDelete(String dxname, boolean force,
                                                  Principal principal)
   {
      ConnectionStatus status = checkAssetEntryDependencies(dxname, AssetEntry.Type.DATA_SOURCE, force);

      if(status != null) {
         return status;
      }

      if(!securityProvider.checkPermission(
         principal, ResourceType.DATA_SOURCE, dxname, ResourceAction.DELETE))
      {
         return new RefusedStatus(Catalog.getCatalog(principal).getString(
            "Permission denied to delete datasource"));
      }

      return null;
   }

   /**
    * Checks that a data source or data source folder node may be deleted or moved, if a data
    * source and a data source folder share its path (Bug #77725). Other nodes are not checked.
    *
    * @param delete {@code true} for a delete, {@code false} for a move.
    *
    * @throws MessageException if the operation would act on the other one's entries.
    */
   private void checkDataSourcePathClash(int type, String path, boolean delete) {
      if(isDataSourceFolderNode(type)) {
         if(delete) {
            dataSourceRegistry.checkDataSourceFolderDeletePathClash(path);
         }
         else {
            dataSourceRegistry.checkDataSourceFolderPathClash(path);
         }
      }
      else if(isDataSourceNode(type)) {
         dataSourceRegistry.checkDataSourcePathClash(path);
      }
   }

   private static boolean isDataSourceFolderNode(int type) {
      return (type & RepositoryEntry.DATA_SOURCE_FOLDER) == RepositoryEntry.DATA_SOURCE_FOLDER;
   }

   private static boolean isDataSourceNode(int type) {
      return !isDataSourceFolderNode(type) &&
         (type & RepositoryEntry.DATA_SOURCE) == RepositoryEntry.DATA_SOURCE;
   }

   private void removeDataSource(String dxname) {
      dataSourceRegistry.removeDataSource(dxname);
      securityProvider.removePermission(ResourceType.DATA_SOURCE, dxname);
   }

   public ConnectionStatus removeDataSourceFolder(String dxname, boolean force,
                                                  Principal principal)
   {
      return removeDataSourceFolder(dxname, force, principal, false);
   }

   /**
    * Removes a data source folder with its data sources and subfolders.
    *
    * @param withDataSource {@code true} to also remove a data source at the path of the folder
    *                       (older data, Bug #77691). Otherwise the delete is refused if that
    *                       data source has additional connections or data models (Bug #77725).
    *
    * @return the reason it may not be deleted, or null if it was deleted.
    */
   public synchronized ConnectionStatus removeDataSourceFolder(String dxname, boolean force,
                                                               Principal principal,
                                                               boolean withDataSource)
   {
      // Bug #77725, a data source at the path of the folder
      if(!withDataSource) {
         dataSourceRegistry.checkDataSourceFolderDeletePathClash(dxname);
      }

      // every data source and subfolder at any depth is checked before anything is deleted, as
      // the registry deletes them all (Bug #77731)
      List<String> sources = new ArrayList<>(
         dataSourceRegistry.getFolderTreeDataSourceNames(dxname));

      if(withDataSource && dataSourceRegistry.isDataSourcePathClash(dxname)) {
         sources.add(dxname);
      }

      for(String source : sources) {
         ConnectionStatus status = checkDataSourceDelete(source, force, principal);

         if(status != null) {
            return status;
         }
      }

      for(String folder : dataSourceRegistry.getFolderTreeSubfolderNames(dxname)) {
         if(!securityProvider.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.DELETE))
         {
            return new RefusedStatus(Catalog.getCatalog(principal).getString(
               "Permission denied to delete datasource folder"));
         }
      }

      for(String source : sources) {
         // Bug #77725, a data source at the path of the folder or of a subfolder is removed by
         // the registry together with that folder
         if(!dataSourceRegistry.isDataSourcePathClash(source)) {
            removeDataSource(source);
         }
      }

      // the registry removes the permission of each removed folder, this one too, only once the
      // stored index no longer lists it
      dataSourceRegistry.removeDataSourceFolder(dxname, withDataSource);

      return null;
   }

   private ConnectionStatus checkAssetEntryDependencies(String path, AssetEntry.Type type,
                                                        boolean force)
   {
      AssetEntry entry =
         new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      String entryId = entry.toIdentifier();
      List<AssetObject> dependencies = DependencyTool.getDependencies(entryId);

      if(dependencies != null && dependencies.size() > 0 && !force) {
         DependencyException depEx = new DependencyException(entry);
         depEx.addDependencies(dependencies.toArray(new AssetObject[0]));
         String message = depEx.getMessage(true);
         return new ConnectionStatus(message);
      }

      if(type == AssetEntry.Type.DATA_SOURCE) {
         try {
            XDataModel dataModel = xRepository.getDataModel(path);

            if(dataModel != null) {
               for(String name : dataModel.getLogicalModelNames()) {
                  XLogicalModel lmodel = dataModel.getLogicalModel(name);
                  String lpath = lmodel.getDataSource() + "/" + lmodel.getName();
                  return checkAssetEntryDependencies(lpath, AssetEntry.Type.LOGIC_MODEL, force);
               }
            }
         }
         catch(Exception e) {
            // Ignore
         }
      }

      return null;
   }

   private ConnectionStatus removeLogicalModel(XDataModel dataModel, String name, boolean force) {
      String path = dataModel.getDataSource() + "/" + name;
      ConnectionStatus status = checkAssetEntryDependencies(path, AssetEntry.Type.LOGIC_MODEL, force);

      if(status != null) {
         return status;
      }

      // read the folder before the model is removed, it is part of the permission resource
      XLogicalModel logicalModel = dataModel.getLogicalModel(name);
      String folder = logicalModel == null ? null : logicalModel.getFolder();
      dataModel.removeLogicalModel(name);
      removeDataModelDependencies(path, AssetEntry.Type.LOGIC_MODEL, true);

      if(logicalModel != null) {
         securityProvider.removePermission(ResourceType.QUERY,
            XUtil.getLogicalModelResourceName(dataModel.getDataSource(), folder, name));
      }

      return null;
   }

   /**
    * Removes the dependency information of a deleted data model object so that it is no longer
    * reported as a dependency of its data source, physical view or logical model.
    *
    * @param path       the path of the deleted object, in the form of "datasource/name".
    * @param type       the asset type of the deleted object.
    * @param removeKey  <tt>true</tt> to also remove the dependencies stored for the object itself.
    */
   private void removeDataModelDependencies(String path, AssetEntry.Type type, boolean removeKey) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      dependencyHandler.deleteDependencies(entry);

      if(removeKey) {
         dependencyHandler.deleteDependenciesKey(entry);
      }
   }

   public void deleteAutoSaveNodes(List<TreeNodeInfo> nodes, Principal principal) throws MessageException {
      try {
         for(int i = 0; i < nodes.size(); i++) {
            String id = nodes.get(i).path();
            AutoSaveUtils.deleteAutoSaveFile(id, principal);
         }

         if(nodes.size() > 0) {
            AssetRepository rep = AssetUtil.getAssetRepository(false);
            ((AbstractAssetEngine)rep).fireAutoSaveEvent(null);
         }
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
   }

   public ContentRepositoryTreeNode addFolder(NewRepositoryFolderRequest parentInfo, boolean isWorksheetFolder,
                         Principal principal)
      throws Exception
   {
      RepositoryOwnerOrgCheck.checkOwnerOrg(parentInfo.getOwner(), principal);
      ActionRecord actionRecord = null;

      try {
         String parentFolder = parentInfo.getParentFolder();
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         IdentityID user = parentInfo.getOwner();
         String folderName = "".equals(parentFolder) || "/".equals(parentFolder) ?
            "" : parentFolder + "/";
         int type = parentInfo.getType();

         actionRecord = SUtil.getActionRecord(principal,
                                              ActionRecord.ACTION_NAME_CREATE,
                                              folderName,
                                              ActionRecord.OBJECT_TYPE_FOLDER);
         String newFolderName = parentInfo.getFolderName();

         // Bug #77733, a name with a slash would create the folder under another parent than the
         // one the permission is checked on
         Tool.checkFolderNameSeparator(newFolderName);

         if(type == RepositoryEntry.DATA_SOURCE_FOLDER) {
            String dsParent = parentFolder == null || parentFolder.isEmpty() ? "/" : parentFolder;

            if(!securityProvider.checkPermission(
               principal, ResourceType.DATA_SOURCE_FOLDER, dsParent, ResourceAction.WRITE))
            {
               throw new MessageException(Catalog.getCatalog().getString(
                  "em.common.security.no.permission", dsParent));
            }

            // a folder must not be created under a data source (Bug #77691). getDataSourceAncestor
            // checks every segment of "parent/", including the parent itself
            String dsAncestor = dataSourceRegistry.getDataSourceAncestor(folderName);

            if(dsAncestor != null) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "common.datasource.createUnderDataSource",
                  folderName + (Tool.isEmptyString(newFolderName) ? "Folder1" : newFolderName),
                  dsAncestor));
            }

            if(!Tool.isEmptyString(newFolderName)) {
               // a data source or a folder at the path, not filtered by permission
               if(dataSourceRegistry.isDataSourcePathInUse(folderName + newFolderName)) {
                  throw new RuntimeException("Folder already exists");
               }

               folderName += newFolderName;
               dataSourceRegistry.setDataSourceFolder(new DataSourceFolder(
                  folderName, LocalDateTime.now(), pId != null ? pId.getName() : null));
               String fullPath = Util.getObjectFullPath(type, folderName, principal);
               actionRecord.setObjectName(fullPath);
            }
            else {
               for(int i = 1; i < Integer.MAX_VALUE; i++) {
                  String name = folderName + "Folder" + i;

                  if(!dataSourceRegistry.isDataSourcePathInUse(name)) {
                     dataSourceRegistry.setDataSourceFolder(new DataSourceFolder(
                        name, LocalDateTime.now(), pId != null ? pId.getName() : null));
                     folderName = name;
                     String fullPath = Util.getObjectFullPath(type, folderName, principal);
                     actionRecord.setObjectName(fullPath);
                     break;
                  }
               }
            }
         }
         else {
            if(isWorksheetFolder) {
               parentFolder = registryManager.splitWorksheetPath(parentFolder, user != null);
               folderName = registryManager.splitWorksheetPath(folderName, user != null);

               if(user != null) {
                  folderName = registryManager.splitMyReportPath(folderName);
               }

               if("".equals(parentFolder)) {
                  parentFolder = "/";
               }

               AssetEntry[] folderEntries =
                  registryManager.getWorksheetFolders(parentFolder, user, principal);

               if(!Tool.isEmptyString(newFolderName)) {
                  for(AssetEntry folderEntry : folderEntries) {
                     if(newFolderName.equals(folderEntry.getPath())) {
                        throw new RuntimeException("Folder already exists");
                     }
                  }

                  folderName += newFolderName;
               }
               else {
                  for(int i = 1; i < Integer.MAX_VALUE; i++) {
                     String name = folderName + "Folder" + i;
                     boolean isExist = false;

                     for(AssetEntry folderEntry : folderEntries) {
                        if(name.equals(folderEntry.getPath())) {
                           isExist = true;
                           break;
                        }
                     }

                     if(isExist) {
                        continue;
                     }

                     folderName = name;
                     break;
                  }
               }

               if(folderName.startsWith("/")) {
                  folderName = folderName.substring(1);
               }

               if(!registryManager.addWorksheetFolder(folderName,null, user, principal)) {
                  throw new IllegalArgumentException(catalog.getString("Duplicate Name"));
               }
            }
            else {
               try {
                  registryManager.checkPermission(parentFolder, ResourceType.REPORT, ResourceAction.WRITE, principal);
               }
               catch(MessageException e) {
                  throw new MessageException(Catalog.getCatalog().getString(
                     "em.common.security.no.permission",
                     parentFolder));
               }


               if(!Tool.isEmptyString(newFolderName)) {
                  if(registryManager.isDuplicatedName(folderName + newFolderName, null, user)) {
                     throw new RuntimeException("Folder already exists");
                  }

                  folderName += newFolderName;
               }
               else {
                  for(int i = 1; i < Integer.MAX_VALUE; i++) {
                     String name = folderName + "Folder" + i;

                     if(!registryManager.isDuplicatedName(name, null, user)) {
                        folderName = name;
                        break;
                     }
                  }
               }

               registryManager.addRepositoryFolder(folderName, null, null, user);
               AssetRepository repository = AssetUtil.getAssetRepository(false);
               Principal folderOwner = user != null ? new XPrincipal(user) : null;
            }
         }

         actionRecord.setObjectName(Util.getObjectFullPath(isWorksheetFolder ?
             RepositoryEntry.WORKSHEET_FOLDER : type, folderName, principal, user));

         if(user != null) {
            if((type & ~RepositoryEntry.USER) == RepositoryEntry.WORKSHEET_FOLDER) {
               folderName = Tool.MY_DASHBOARD + "/Worksheets/" + folderName;
            }
         }

         return treeService.getFolderNode(folderName, type, user);
      }
      catch(Exception e) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(e.getMessage());
         }

         throw e;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   private void removeFolder(TreeNodeInfo node, RepletRegistry registry) throws Exception {
      final String folder = node.path();

      if(!registry.removeFolder(folder)) {
         LOG.error(Catalog.getCatalog().getString(
            "em.registry.deleteFolderError", folder));
      }
      else {
         registry.save();
      }
   }

   private void removeWorksheetFolder(Principal principal, TreeNodeInfo node, boolean force) {
      String folder = treeService.getUnscopedPath(node.path());
      final IdentityID owner = node.owner();

      if(owner != null) {
         int index = folder.indexOf(Tool.WORKSHEET);

         if(index >= 0) {
            folder = folder.substring(Tool.WORKSHEET.length() + 1);
         }
      }

      try {
         registryManager.removeWorksheetFolder(folder, owner, force, principal);
      }
      catch(DependencyException e) {
         throw e;
      }
      catch(Exception ex) {
         LOG.error("Failed to remove worksheet folder " + folder + " for user " + owner, ex);
      }
   }

   private void removeQueryFolder(String folderName, String folderPath) throws Exception {
      String dataSourceName = null;

      // name::path
      if(folderPath.indexOf("::") != -1) {
         dataSourceName = folderPath.substring(folderPath.indexOf("::") + 2);
      }
      else {
         dataSourceName = folderPath.replace("/" + folderName, "");
      }

      XDataSource dataSource = xRepository.getDataSource(dataSourceName);
      dataSource.removeFolder(folderName);
      xRepository.updateDataSource((XDataSource) dataSource.clone(), null, true);
   }

   public void moveFiles(MoveCopyTreeNodesRequest request, boolean move, Principal principal)
      throws Exception
   {
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_MOVE,
                                                        null, null);
      List<ContentRepositoryTreeNode> source = request.source();
      int typeTo = request.destination().type();
      String pathTo = request.destination().path();
      IdentityID userTo = request.destination().owner();

      String[] pathFroms = source.stream().map((node) -> {
         if(node.type() == RepositoryEntry.VIEWSHEET || node.type() == RepositoryEntry.WORKSHEET) {
            AssetEntry entry = new AssetEntry(treeService.getAssetScope(node.path()),
                                              node.type() == RepositoryEntry.VIEWSHEET ?
                                                 AssetEntry.Type.VIEWSHEET : AssetEntry.Type.WORKSHEET,
                                              treeService.getUnscopedPath(node.path()), node.owner());
            return node.path() + "&identifier=" + entry.toIdentifier();
         }
         else {
            return node.path();
         }
      }).toArray(String[]::new);

      IdentityID[] userFroms = source.stream().map(ContentRepositoryTreeNode::owner).toArray(IdentityID[]::new);
      // the destination and every source owner select a per-owner registry
      RepositoryOwnerOrgCheck.checkOwnerOrg(userTo, principal);

      for(IdentityID userFrom : userFroms) {
         RepositoryOwnerOrgCheck.checkOwnerOrg(userFrom, principal);
      }

      String[] typeFroms = source.stream().map(ContentRepositoryTreeNode::type)
         .map(String::valueOf).toArray(String[]::new);

      if(pathFroms.length != userFroms.length ||
         pathFroms.length != typeFroms.length)
      {
         return;
      }

      Map<String, List<String>> infos = new HashMap<>();
      infos.put("info", new ArrayList<>());
      infos.put("warning", new ArrayList<>());
      infos.put("error", new ArrayList<>());

      checkPermission(pathFroms, typeFroms, pathTo, typeTo, move, principal);

      // an additional connection belongs to its parent data source, moving it by its path would
      // turn it into a standalone data source. Check all nodes before moving any of them.
      for(int i = 0; i < pathFroms.length; i++) {
         int typeFrom = Integer.parseInt(typeFroms[i]);

         if((typeFrom & RepositoryEntry.DATA_SOURCE) == RepositoryEntry.DATA_SOURCE &&
            dataSourceRegistry.isAdditionalConnectionPath(pathFroms[i]))
         {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "common.datasource.additionalConnectionMove"));
         }
      }

      // Bug #77727, a data source that can't be loaded (its connector isn't installed or its
      // definition is damaged) is moved only with its folder. Check all nodes before moving any
      // of them.
      for(int i = 0; i < pathFroms.length; i++) {
         int typeFrom = Integer.parseInt(typeFroms[i]);

         if((typeFrom & RepositoryEntry.DATA_SOURCE) == RepositoryEntry.DATA_SOURCE &&
            dataSourceRegistry.getDataSource(pathFroms[i]) == null)
         {
            throw new MessageException(getUnloadableMoveMessage(pathFroms[i], principal));
         }
      }

      // a data source or data source folder moved onto a path that is already used by a data
      // source or a data source folder would overwrite or merge with it. Check all nodes before
      // moving any of them.
      Set<String> dataSourceTargets = new HashSet<>();

      for(int i = 0; i < pathFroms.length; i++) {
         int typeFrom = Integer.parseInt(typeFroms[i]);

         if((typeFrom & RepositoryEntry.DATA_SOURCE_FOLDER) != RepositoryEntry.DATA_SOURCE_FOLDER &&
            (typeFrom & RepositoryEntry.DATA_SOURCE) != RepositoryEntry.DATA_SOURCE)
         {
            continue;
         }

         String pathFrom = pathFroms[i] == null ? "" : pathFroms[i];
         int pindex = pathFrom.lastIndexOf("/");
         String name = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
         String newPath = "/".equals(pathTo) ? name : pathTo + "/" + name;

         if(newPath.equals(pathFrom)) {
            continue;
         }

         // a folder moved into itself or one of its subfolders would be left with no parent
         if((typeFrom & RepositoryEntry.DATA_SOURCE_FOLDER) == RepositoryEntry.DATA_SOURCE_FOLDER &&
            newPath.startsWith(pathFrom + "/"))
         {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "common.datasource.moveIntoItself", pathFrom));
         }

         if(!dataSourceTargets.add(newPath) || dataSourceRegistry.isDataSourcePathInUse(newPath)) {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "common.datasource.moveTargetExists", newPath));
         }

         // moved under a data source, it would become an additional connection of it
         String dataSource = dataSourceRegistry.getDataSourceAncestor(newPath);

         if(dataSource != null) {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "common.datasource.moveUnderDataSource", dataSource));
         }
      }

      // Bug #77725, a data source or folder whose path is shared by the other one. Check all
      // nodes before moving any of them.
      for(int i = 0; i < pathFroms.length; i++) {
         String pathFrom = pathFroms[i] == null ? "" : pathFroms[i];
         int pindex = pathFrom.lastIndexOf("/");
         String name = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
         String newPath = "/".equals(pathTo) ? name : pathTo + "/" + name;

         if(!newPath.equals(pathFrom)) {
            checkDataSourcePathClash(Integer.parseInt(typeFroms[i]), pathFrom, false);
         }
      }

      // Bug #77721, a worksheet or report folder dropped onto itself or one of its subfolders
      // would be copied into itself without end. Check all nodes before moving any of them.
      for(int i = 0; i < pathFroms.length; i++) {
         int typeFrom = Integer.parseInt(typeFroms[i]);

         if((typeFrom & RepositoryEntry.FOLDER) != RepositoryEntry.FOLDER ||
            (typeFrom & RepositoryEntry.DATA_SOURCE_FOLDER) == RepositoryEntry.DATA_SOURCE_FOLDER ||
            (typeFrom & RepositoryEntry.LOGIC_MODEL) == RepositoryEntry.LOGIC_MODEL ||
            (typeFrom & RepositoryEntry.PARTITION) == RepositoryEntry.PARTITION ||
            !Tool.equals(userFroms[i], userTo))
         {
            continue;
         }

         if(Tool.isSameOrDescendantPath(pathFroms[i], pathTo)) {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "common.folder.moveIntoItself", pathFroms[i]));
         }
      }

      for(int i = 0; i < pathFroms.length; i++) {
         Map<String, List<String>> info = new HashMap<>();
         info.put("info", new ArrayList<>());
         info.put("warning", new ArrayList<>());
         info.put("error", new ArrayList<>());
         IdentityID userFrom = userFroms[i];
         String pathFrom = pathFroms[i] == null ? "" : pathFroms[i];
         int typeFrom = Integer.parseInt(typeFroms[i]);
         String identifier = null;

         if((typeFrom & RepositoryEntry.VIEWSHEET) != 0 ||
            (typeFrom & RepositoryEntry.WORKSHEET) != 0) {
            int iindex = pathFrom.lastIndexOf("&identifier=");
            identifier = iindex < 0 ? "" : pathFrom.substring(iindex + 12);
            identifier = Tool.byteDecode(identifier);
            pathFrom = iindex < 0 ? pathFrom : pathFrom.substring(0, iindex);
         }

         String fullPathFrom = Util.getObjectFullPath(typeFrom, pathFrom, principal, userFrom);
         String fullPathTo = Util.getObjectFullPath(typeTo, pathTo, principal, userTo);
         actionRecord.setObjectType(getActionRecordType(typeFrom));
         actionRecord.setObjectName(fullPathFrom);
         actionRecord.setActionError("Target Entry: " + fullPathTo);

         if((typeFrom & RepositoryEntry.DATA_SOURCE_FOLDER) == RepositoryEntry.DATA_SOURCE_FOLDER) {
            int pindex = pathFrom.lastIndexOf("/");
            String name = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
            String newPath = "/".equals(pathTo) ? name : pathTo + "/" + name;
            Map<String, RenameDependencyInfo> renameDependencyInfos =
               DependencyTransformer.createDatasourceFolderDependencyInfoMap(dataSourceRegistry,
                  pathFrom, newPath);

            // Bug #77704, a failed move renames the dependencies of the data sources it moved
            try {
               dataSourceRegistry.renameDataSourceFolder(pathFrom, newPath);
            }
            catch(Exception ex) {
               if(ex instanceof DataSourceRenameException renameException) {
                  renameDependencyInfos.forEach((dataSource, renameDependencyInfo) -> {
                     if(renameException.isMoved(dataSource)) {
                        this.renameTransformHandler.addTransformTask(renameDependencyInfo);
                     }
                  });
               }

               auditMoveFailure(actionRecord, ex, fullPathTo, principal);
               throw ex;
            }

            for(RenameDependencyInfo renameDependencyInfo : renameDependencyInfos.values()) {
               this.renameTransformHandler.addTransformTask(renameDependencyInfo);
            }
         }
         else if((typeFrom & RepositoryEntry.DATA_SOURCE) == RepositoryEntry.DATA_SOURCE) {
            int pindex = pathFrom.lastIndexOf("/");
            String name = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
            String newPath = "/".equals(pathTo) ? name : pathTo + "/" + name;
            XDataSource ds = xRepository.getDataSource(pathFrom);

            // Bug #77727, it could be loaded when the nodes were checked
            if(ds == null) {
               MessageException ex =
                  new MessageException(getUnloadableMoveMessage(pathFrom, principal));
               auditMoveFailure(actionRecord, ex, fullPathTo, principal);
               throw ex;
            }

            RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
               pathFrom, newPath);
            ds.setName(newPath);

            // Bug #77704, the dependencies are renamed once the data source is moved
            try {
               xRepository.updateDataSource(ds, pathFrom, false);
            }
            catch(Exception ex) {
               if(ex instanceof DataSourceRenameException renameException &&
                  renameException.isMoved(pathFrom))
               {
                  this.renameTransformHandler.addTransformTask(dinfo);
               }

               auditMoveFailure(actionRecord, ex, fullPathTo, principal);
               throw ex;
            }

            this.renameTransformHandler.addTransformTask(dinfo);
         }
         else if(move && ((typeFrom & RepositoryEntry.LOGIC_MODEL) == RepositoryEntry.LOGIC_MODEL ||
            (typeFrom & RepositoryEntry.PARTITION) == RepositoryEntry.PARTITION))
         {
            moveDataModel(pathFrom, pathTo, info, typeFrom);
            renameDataModelPermission(pathFrom, pathTo);
         }
         else {
            // do nothing when folder path is not changed.
            if(checkFolderMoved(pathFrom, typeFrom, pathTo, userFrom, userTo)) {
               boolean copied = registryManager.copyFile(
                  identifier, pathFrom, userFrom, typeFrom,
                  pathTo == null ? "" : pathTo, userTo, typeTo, move, info,
                  principal);
               boolean isWS = (typeFrom & RepositoryEntry.WORKSHEET) != 0;

               //if move the replet in same user, we change the old replet to new one.
               //so it is no need to remove it.
               //use changeSheet for vs like ws now, so it no need to remove it.

               if(copied && move && !isWS && ((typeFrom & RepositoryEntry.VIEWSHEET) == 0
                  || !Tool.equals(userFrom, userTo)))
               {
                  registryManager.removeFile(pathFrom, userFrom, typeFrom, info, principal);
               }
            }
         }

         String errorMesssage = getMoveErrorMesssage(info);

         if(!errorMesssage.isEmpty()) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(errorMesssage);
         }

         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }

         updateInfos(infos, info, "info");
         updateInfos(infos, info, "warning");
         updateInfos(infos, info, "error");
      }

      String errorMesssage = getMoveErrorMesssage(infos);

      if(!errorMesssage.isEmpty()) {
         LogLevel level = LogLevel.INFO;
         List<String> warnings = infos.get("warning");

         if(warnings != null && warnings.size() > 0) {
            level = LogLevel.WARN;
         }

         List<String> errors = infos.get("error");

         if(errors != null && errors.size() > 0) {
            level = LogLevel.ERROR;
         }

         throw new MessageException(errorMesssage, level, false);
      }
      else {
         if(move) {
            RepletRegistry registryTo = repletRegistryManager.getRegistry(userTo);
            registryTo.save();
         }
      }
   }

   /**
    * Whether the folder moved.
    * @param oldPath old folder path.
    * @param oldType type.
    * @param pathTo target folder.
    * @param oldUser old user.
    * @param newUser new user.
    * @return
    */
   private boolean checkFolderMoved(String oldPath, int oldType, String pathTo, IdentityID oldUser,
                                    IdentityID newUser)
   {
      if(!((oldType & RepositoryEntry.FOLDER) == RepositoryEntry.FOLDER)) {
         return true;
      }

      String oldName = oldPath;

      if(oldPath != null) {
         int index = oldPath.lastIndexOf("/");

         if(index >= 0 && index < oldPath.length() - 1) {
            oldName = oldPath.substring(index + 1);
         }
      }

      if(oldName != null) {
         String newPath = (Tool.isEmptyString(pathTo) || "/".equals(pathTo) ? "" :
            pathTo + "/") + oldName;

         return !Tool.equals(oldPath, newPath) || !Tool.equals(oldUser, newUser);
      }

      return false;
   }

   /**
    * Moving folder for data model.
    */
   private void moveDataModel(String pathFrom, String pathTo, Map<String, List<String>> infos, int type)
      throws Exception
   {
      int idx = pathFrom.indexOf("^");
      String dsName = idx == -1 ? null : pathFrom.substring(0, idx);

      if(dsName != null && (pathTo == null || !pathTo.startsWith(dsName))) {
         infos.get("error").add(catalog.getString("em.database.drag.dataModel.source.note"));
         return;
      }

      XDataModel dataModel = dsName == null ? null : xRepository.getDataModel(dsName);

      if(dataModel == null) {
         infos.get("error").add(catalog.getString("notFind.database.byPath", pathFrom));
         return;
      }

      String folder = null;

      if(!Tool.equals(pathTo, dsName) && (dsName.length() + 1) < pathTo.length()) {
         folder = pathTo.substring(dsName.length() + 1);
      }

      idx = pathFrom.lastIndexOf("^");
      String name = idx + 1 < pathFrom.length() ? pathFrom.substring(idx + 1) : null;

      if((type & RepositoryEntry.LOGIC_MODEL) == RepositoryEntry.LOGIC_MODEL) {
         XLogicalModel lg = name == null ? null : dataModel.getLogicalModel(name);

         if(lg == null) {
            infos.get("error").add(catalog.getString("notFind.dataModel", name));
            return;
         }

         RenameDependencyInfo dinfo = new RenameDependencyInfo();
         String oldFolder = lg.getFolder();
         String database = dataModel.getDataSource();
         boolean isRoot = folder == null || "/".equals(folder) || "".equals(folder);
         String oldPath = database + "/" + (oldFolder == null ? name :
            oldFolder + "/" + name);
         String newPath = database + "/" + (isRoot ? name : folder + "/" + name);

         if(Tool.equals(lg.getFolder(), oldFolder)) {
            lg.setFolder(folder);
            dataModel.addLogicalModel(lg);
         }

         RenameInfo rinfo = new RenameInfo(oldFolder, folder,
            RenameInfo.LOGIC_MODEL | RenameInfo.FOLDER);
         rinfo.setOldPath(oldPath);
         rinfo.setNewPath(newPath);
         AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.LOGIC_MODEL, lg.getDataSource() + "/" + name, null);
         List<AssetObject> entries = DependencyTransformer.getDependencies(oentry.toIdentifier());

         if(entries == null) {
            return;
         }

         for(AssetObject obj : entries) {
            dinfo.addRenameInfo(obj, rinfo);
         }

         this.renameTransformHandler.addTransformTask(dinfo);
      }
      else if((type & RepositoryEntry.PARTITION) == RepositoryEntry.PARTITION) {
         XPartition view = name == null ? null : dataModel.getPartition(name);

         if(view == null) {
            infos.get("error").add(catalog.getString("notFind.dataModel", name));
            return;
         }

         view.setFolder(folder);
         dataModel.addPartition(view);
      }

      xRepository.updateDataModel(dataModel);
   }

   private void renameDataModelPermission(String pathFrom, String pathTo) {
      String resourceID = pathFrom.substring(pathFrom.lastIndexOf("^"));
      String resourcePathFrom = ResourcePermissionService.getLogicalModelResourceName(pathFrom).getPath();
      Permission permission =
         securityProvider.getAuthorizationProvider().getPermission(ResourceType.QUERY, resourcePathFrom);

      //pathTo with folder does not contain DATAMODEL_FOLDER_SPLITER yet but needs to here for permissions
      pathTo = handleDataModelToContainsFolder(pathFrom, pathTo);

      if(permission != null) {
         String resourcePathTo = ResourcePermissionService.getLogicalModelResourceName(pathTo + resourceID).getPath();
         securityProvider.getAuthorizationProvider().setPermission(ResourceType.QUERY, resourcePathTo, permission);
         securityProvider.getAuthorizationProvider().removePermission(ResourceType.QUERY, resourcePathFrom);
      }
   }

   private String handleDataModelToContainsFolder(String pathFrom, String pathTo) {
      int idx = pathFrom.indexOf("^");
      String dsName = idx == -1 ? null : pathFrom.substring(0, idx);

      if(dsName != null && pathTo.substring(dsName.length()).contains("/")) {
         int folderSplitterIdx = pathTo.lastIndexOf("/");
         return pathTo.substring(0, folderSplitterIdx) + DATAMODEL_FOLDER_SPLITER + pathTo.substring(folderSplitterIdx+1);
      }

      return pathTo;
   }

   private void checkPermission(String[] pathFroms, String[] typeFroms,
                                String pathTo, int typeTo, boolean move, Principal principal)
   {
      if(move) {
         for(int i = 0; i < pathFroms.length; i++) {
            //for My Report, ignore permission check
            if(Tool.MY_DASHBOARD.equals(pathFroms[i]) ||
               pathFroms[i].startsWith(Tool.MY_DASHBOARD +  "/"))
            {
               continue;
            }

            EnumSet.of(ResourceAction.WRITE, ResourceAction.DELETE);
            int type = Integer.parseInt(typeFroms[i]);
            String src = pathFroms[i];
            checkPermission(type, src, EnumSet.of(ResourceAction.WRITE, ResourceAction.DELETE),
               principal);
         }
      }

      //for My Report, ignore permission check
      if(pathTo != null && !(Tool.MY_DASHBOARD.equals(pathTo) ||
         pathTo.startsWith(Tool.MY_DASHBOARD +"/")))
      {
         if((typeTo & RepositoryEntry.WORKSHEET_FOLDER) ==
            RepositoryEntry.WORKSHEET_FOLDER) {
            String dest = registryManager.splitWorksheetPath(pathTo, false);
            Resource resource =
               resourcePermissionService.getRepositoryResourceType(typeTo, dest);
            registryManager.checkPermission(
               dest, resource.getType(), ResourceAction.WRITE, false, principal); //dest
         }
         else {
            Resource resource =
               resourcePermissionService.getRepositoryResourceType(typeTo, pathTo);
            registryManager.checkPermission(
               pathTo, resource.getType(), ResourceAction.WRITE, false, principal); //dest
         }
      }
   }

   private void checkPermission(int type, String src, EnumSet<ResourceAction> actions,
                                   Principal principal)
   {
      AssetEntry.Type newType = AssetEntry.Type.UNKNOWN;
      boolean isWS = false;

      if((type & RepositoryEntry.WORKSHEET) != 0) {
         newType = AssetEntry.Type.WORKSHEET;
         isWS = true;
      }
      else if((type & RepositoryEntry.VIEWSHEET) != 0) {
         newType = type != RepositoryEntry.VIEWSHEET ?
            AssetEntry.Type.VIEWSHEET_SNAPSHOT : AssetEntry.Type.VIEWSHEET;
      }

      if(newType == AssetEntry.Type.VIEWSHEET ||
         newType == AssetEntry.Type.VIEWSHEET_SNAPSHOT ||
         (newType == AssetEntry.Type.WORKSHEET && isWS))
      {
         int index = src.lastIndexOf("&identifier=");
         src = index < 0 ? src : src.substring(0, index);
      }

      Resource resource = resourcePermissionService.getRepositoryResourceType(type, src);

      if(newType == AssetEntry.Type.VIEWSHEET ||
         newType == AssetEntry.Type.VIEWSHEET_SNAPSHOT ||
         newType == AssetEntry.Type.WORKSHEET)
      {
         if(newType == AssetEntry.Type.WORKSHEET) {
            src = registryManager.splitWorksheetPath(src, false);
            resource = resourcePermissionService.getRepositoryResourceType(type, src);
            registryManager.checkPermission(src, src, resource.getType(), actions,
               true, principal);
         }
         else {
            registryManager.checkPermission(src, src, resource.getType(), actions,
               true, principal);
         }
      }
      //only handle ws folder entry, not handle folder entry.
      else if ((type & RepositoryEntry.WORKSHEET_FOLDER) ==
         RepositoryEntry.WORKSHEET_FOLDER)
      {
         src = registryManager.splitWorksheetPath(src, false);
         resource = resourcePermissionService.getRepositoryResourceType(type, src);
         registryManager.checkPermission(src, src, resource.getType(), actions, true,
            principal);
      }
      else if(type == (RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER) ||
         type == (RepositoryEntry.PARTITION | RepositoryEntry.FOLDER))
      {
         registryManager.checkPermission(resource.getPath(), src, resource.getType(),
                                         actions, true, principal);
      }
      else if((type & RepositoryEntry.FOLDER) != 0) {
         registryManager.checkPermission(src, src, resource.getType(),
            actions, true, principal);
      }
      else if(type == RepositoryEntry.DASHBOARD) {
         IdentityID principalID = IdentityID.getIdentityIDFromKey(principal.getName());
         String dashboardName = SUtil.isMyDashboard(src) ? SUtil.getUnscopedPath(src) : src;
         DashboardRegistry userRegistry = dashboardRegistryManager.getRegistry(principalID);
         boolean isOwnDashboard = SUtil.isMyDashboard(src) && userRegistry.getDashboard(dashboardName) != null;

         if(!isOwnDashboard &&
            !securityProvider.checkPermission(
               principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN))
         {
            throw new MessageException(Catalog.getCatalog().getString(
               "em.common.security.no.permission", src));
         }
      }
   }

   private String getActionRecordType(int repositoryType) {
      return repositoryType == RepositoryEntry.VIEWSHEET ? ActionRecord.OBJECT_TYPE_DASHBOARD :
         repositoryType == RepositoryEntry.AUTO_SAVE_VS ? ActionRecord.OBJECT_TYPE_ASSET:
         repositoryType == RepositoryEntry.AUTO_SAVE_WS ? ActionRecord.OBJECT_TYPE_ASSET:
         repositoryType == RepositoryEntry.WORKSHEET ? ActionRecord.OBJECT_TYPE_WORKSHEET :
         repositoryType == RepositoryEntry.DASHBOARD ? ActionRecord.OBJECT_TYPE_DASHBOARD :
         (repositoryType & RepositoryEntry.DATA_SOURCE) != 0 ? ActionRecord.OBJECT_TYPE_DATASOURCE :
         repositoryType == RepositoryEntry.QUERY ? ActionRecord.OBJECT_TYPE_QUERY :
         repositoryType == RepositoryEntry.SCRIPT ? ActionRecord.OBJECT_TYPE_SCRIPT :
         repositoryType == RepositoryEntry.TABLE_STYLE ? ActionRecord.OBJECT_TYPE_TABLE_STYLE :
         repositoryType == RepositoryEntry.VPM ? ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL :
         repositoryType == RepositoryEntry.PROTOTYPE ? ActionRecord.OBJECT_TYPE_PROTOTYPE :
         (repositoryType & RepositoryEntry.FOLDER) != 0 ? ActionRecord.OBJECT_TYPE_FOLDER :
            ActionRecord.OBJECT_TYPE_REPORT;
   }

   // Bug #77704, a move that throws is audited as failed, as the portal does
   private void auditMoveFailure(ActionRecord actionRecord, Exception ex, String fullPathTo,
                                 Principal principal)
   {
      if(actionRecord != null) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + fullPathTo);
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   /**
    * Gets the message that refuses the move of a data source that can't be loaded on its own, or
    * that is no longer stored.
    */
   private String getUnloadableMoveMessage(String path, Principal principal) {
      if(!dataSourceRegistry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null)))
      {
         return Catalog.getCatalog(principal).getString("data.datasources.findDataSourceError");
      }

      return Catalog.getCatalog(principal).getString("common.datasource.moveUnloadable", path);
   }

   private String getMoveErrorMesssage(Map<String, List<String>> infos) {
      StringBuilder buf = new StringBuilder();
      List<String> messages = infos.get("info");

      if(messages.size() > 0) {
         buf.append(catalog.getString("Information")).append(":\n");
      }

      for(String mess : messages) {
         buf.append("   ").append(mess).append("\n");
      }

      List<String> warnings = infos.get("warning");

      if(warnings.size() > 0) {
         buf.append(catalog.getString("Warning")).append(":\n");
      }

      for(String warning : warnings) {
         buf.append("   ").append(warning).append("\n");
      }

      List<String> errors = infos.get("error");

      if(errors.size() > 0) {
         buf.append(catalog.getString("Error")).append(":\n");
      }

      for(String error : errors) {
         buf.append("   ").append(error).append("\n");
      }

      return buf.toString();
   }

   private void updateInfos(Map<String, List<String>> allInfos, Map<String, List<String>> info,
                            String key)
   {
      if(info.containsKey(key) && allInfos.containsKey(key) && info.get(key).size() > 0) {
         allInfos.get(key).addAll(info.get(key));
      }
   }

   /**
    * The status of a delete that is refused, as opposed to a prompt to confirm it (Bug #77819).
    */
   private static final class RefusedStatus extends ConnectionStatus {
      RefusedStatus(String status) {
         super(status);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(RepositoryObjectService.class);
   private final Catalog catalog = Catalog.getCatalog();
   private final RepletRegistryService registryManager;
   private final DataSourceRegistry dataSourceRegistry;
   private final XRepository xRepository;
   private final ContentRepositoryTreeService treeService;
   private final SecurityProvider securityProvider;
   private final ResourcePermissionService resourcePermissionService;
   private final RepositoryDashboardService repositoryDashboardService;
   private final DataModelFolderManagerService dataModelFolderManagerService;
   private final LibManagerProvider libManagerProvider;
   private final RecycleBin recycleBin;
   private final DependencyHandler dependencyHandler;
   private final RenameTransformHandler renameTransformHandler;
   private final RepletRegistryManager repletRegistryManager;
   private final DashboardRegistryManager dashboardRegistryManager;
}
