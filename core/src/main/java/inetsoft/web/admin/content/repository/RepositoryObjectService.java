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
import inetsoft.report.internal.Util;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.service.DataSourceRegistry;
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
   public RepositoryObjectService(ContentRepositoryTreeService treeService,
                                  SecurityProvider securityProvider,
                                  ResourcePermissionService resourcePermissionService,
                                  XRepository xRepository,
                                  RepositoryDashboardService repositoryDashboardService,
                                  DataModelFolderManagerService dataModelFolderManagerService)
   {
      this.treeService = treeService;
      registryManager = new RepletRegistryManager();
      dataSourceRegistry = DataSourceRegistry.getRegistry();
      this.xRepository = xRepository;
      this.securityProvider = securityProvider;
      this.resourcePermissionService = resourcePermissionService;
      this.repositoryDashboardService = repositoryDashboardService;
      this.dataModelFolderManagerService = dataModelFolderManagerService;
   }

   public ConnectionStatus deleteNodes(TreeNodeInfo[] nodes, Principal principal, boolean force,
                                       boolean permanent) throws MessageException
   {
      ArrayList<TreeNodeInfo> trashNodes = new ArrayList<>();
      ArrayList<TreeNodeInfo> autoSaveNodes = new ArrayList<>();
      RecycleBin recycleBin = RecycleBin.getRecycleBin();

      for(TreeNodeInfo node : nodes) {
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

         try {
            final RepletRegistry registry = RepletRegistry.getRegistry(node.owner());

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
                        assetRepository.getSheet(asset, principal, true, AssetContent.ALL);
                     RecycleBin.Entry binEntry = recycleBin.getEntry(path);
                     AssetEntry dasset = new AssetEntry(
                        binEntry.getOriginalScope(), type, binEntry.getOriginalPath(),
                        node.owner());
                     DependencyHandler.getInstance().updateSheetDependencies(assetSheet, dasset, false);
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
                     return new ConnectionStatus(
                        "corrupt:" + Catalog.getCatalog(principal).getString(
                           "em.content.deleteCorruptConfirm"));
                  }
               }

               break;
            case RepositoryEntry.DATA_SOURCE:
            case RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER:
               ConnectionStatus dataSource = deleteDataSource(node.path(), force, principal);

               if(dataSource != null) {
                  return dataSource;
               }

               break;
            case RepositoryEntry.DATA_SOURCE_FOLDER:
               ConnectionStatus dataSourceFolder =
                  removeDataSourceFolder(node.path(), force, principal);

               if(dataSourceFolder != null) {
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
                               return new ConnectionStatus(msg);
                            }
                        }

                        ConnectionStatus status = removeLogicalModel(dataModel, node.label(), force);

                        if(status != null) {
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
                     }
                     else {
                        dataModel.removeVirtualPrivateModel(node.label());
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
                           }
                        }
                        else if(node.type() == RepositoryEntry.PARTITION) {
                           XPartition physicalView = dataModel.getPartition(extendedModelPath[1]);

                           if(physicalView != null) {
                              physicalView.removePartition(extendedModelPath[2]);
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
               LibManager.getManager().removeScript(node.label());
               LibManager.getManager().save();
               securityProvider.removePermission(ResourceType.SCRIPT, node.label());
               break;
            case RepositoryEntry.TABLE_STYLE:
               int index = node.path().lastIndexOf(LibManager.SEPARATOR);
               String folder = null;

               if(index >= 0) {
                  folder = node.path().substring(0, index);
               }

               for(XTableStyle style : LibManager.getManager().getTableStyles(folder)) {
                  if(node.path().equals(style.getName())) {
                     LibManager.getManager().removeTableStyle(style.getID());
                     break;
                  }
               }

               LibManager.getManager().save();
               break;
            case RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER:
               LibManager.getManager().removeTableStyleFolder(node.path());
               LibManager.getManager().save();
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
               this.repositoryDashboardService.delete(node.path(), node.owner());
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
            }
         }
      }

      return null;
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
      ConnectionStatus status = checkAssetEntryDependencies(dxname, AssetEntry.Type.DATA_SOURCE, force);

      if(status != null) {
         return status;
      }

      final ResourceType type = ResourceType.DATA_SOURCE;

      if(!securityProvider.checkPermission(principal, type, dxname, ResourceAction.DELETE)) {
         return new ConnectionStatus(Catalog.getCatalog(principal).getString(
            "Permission denied to delete datasource"));
      }

      dataSourceRegistry.removeDataSource(dxname);
      securityProvider.removePermission(type, dxname);

      return null;
   }

   public synchronized ConnectionStatus removeDataSourceFolder(String dxname,
                                                               boolean force,
                                                               Principal principal)
   {
      List<String> sources = dataSourceRegistry.getSubDataSourceNames(dxname);

      for(String source : sources) {
         ConnectionStatus status = deleteDataSource(source, force, principal);

         if(status != null) {
            return status;
         }
      }

      dataSourceRegistry.removeDataSourceFolder(dxname);
      securityProvider.removePermission(ResourceType.DATA_SOURCE_FOLDER, dxname);

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

      dataModel.removeLogicalModel(name);
      return null;
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

         if(type == RepositoryEntry.DATA_SOURCE_FOLDER) {
            if(!Tool.isEmptyString(newFolderName)) {
               if(dataSourceRegistry.getDataSourceFolder(newFolderName) != null) {
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

                  if(dataSourceRegistry.getDataSourceFolder(name) == null) {
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

         setResourcePermission(isWorksheetFolder ? ResourceType.ASSET :
                                  type == RepositoryEntry.DATA_SOURCE_FOLDER ?
                                     ResourceType.DATA_SOURCE_FOLDER : ResourceType.REPORT, principal, folderName);
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

   private void setResourcePermission(ResourceType type, Principal principal, String name) {
      AuthorizationProvider authz = securityProvider.getAuthorizationProvider();
      Permission perm = new Permission();
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();
      Set<String> userGrants = new HashSet<>();

      if(principal instanceof XPrincipal && Tool.equals(((XPrincipal) principal).getOrgId(), currentOrgID)) {
         userGrants.add(IdentityID.getIdentityIDFromKey(principal.getName()).name);
      }

      for(ResourceAction action : ResourceAction.values()) {
         perm.setUserGrantsForOrg(action, userGrants, currentOrgID);
      }

      perm.updateGrantAllByOrg(currentOrgID, true);
      authz.setPermission(type, name, perm);
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
            List<RenameDependencyInfo> renameDependencyInfos =
               DependencyTransformer.createDatasourceFolderDependencyInfo(dataSourceRegistry,
                  pathFrom, newPath);
            dataSourceRegistry.renameDataSourceFolder(pathFrom, newPath);

            for(RenameDependencyInfo renameDependencyInfo : renameDependencyInfos) {
               RenameTransformHandler.getTransformHandler().addTransformTask(renameDependencyInfo);
            }
         }
         else if((typeFrom & RepositoryEntry.DATA_SOURCE) == RepositoryEntry.DATA_SOURCE) {
            int pindex = pathFrom.lastIndexOf("/");
            String name = pindex < 0 ? pathFrom : pathFrom.substring(pindex + 1);
            String newPath = "/".equals(pathTo) ? name : pathTo + "/" + name;
            XDataSource ds = xRepository.getDataSource(pathFrom);
            RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
               pathFrom, newPath);
            RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
            ds.setName(newPath);
            xRepository.updateDataSource(ds, pathFrom, false);
         }
         else if(move && ((typeFrom & RepositoryEntry.LOGIC_MODEL) == RepositoryEntry.LOGIC_MODEL ||
            (typeFrom & RepositoryEntry.PARTITION) == RepositoryEntry.PARTITION))
         {
            moveDataModel(pathFrom, pathTo, info, typeFrom);
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
            RepletRegistry registryTo = RepletRegistry.getRegistry(userTo);
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

         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
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
      else if(type == (RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER)) {
         registryManager.checkPermission(resource.getPath(), src, resource.getType(),
                                         actions, true, principal);
      }
      else if((type & RepositoryEntry.FOLDER) != 0) {
         registryManager.checkPermission(src, src, resource.getType(),
            actions, true, principal);
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

   private static final Logger LOG = LoggerFactory.getLogger(RepositoryObjectService.class);
   private final Catalog catalog = Catalog.getCatalog();
   private final RepletRegistryManager registryManager;
   private final DataSourceRegistry dataSourceRegistry;
   private final XRepository xRepository;
   private final ContentRepositoryTreeService treeService;
   private final SecurityProvider securityProvider;
   private final ResourcePermissionService resourcePermissionService;
   private final RepositoryDashboardService repositoryDashboardService;
   private final DataModelFolderManagerService dataModelFolderManagerService;
}
