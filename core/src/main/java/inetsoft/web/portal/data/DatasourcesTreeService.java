/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.data;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.composer.AssetTreeController;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.database.DatasourceTreeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatasourcesTreeService {
   @Autowired
   public DatasourcesTreeService(SecurityEngine securityEngine,
                                 ContentRepositoryTreeService contentRepositoryTreeService,
                                 AssetRepository assetRepository)
   {
      this.securityEngine = securityEngine;
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.assetRepository = assetRepository;
   }

   public TreeNodeModel getRoot(Principal principal) throws Exception {
      TreeNodeModel.Builder root = TreeNodeModel.builder();
      TreeNodeModel dataSourceNode = getDataSources(principal);

      root.addChildren(getGlobalDataSets(principal));
      root.addChildren(dataSourceNode);

      return root.build();
   }

   private TreeNodeModel getGlobalDataSets(Principal principal) throws Exception {
      Map<AssetEntry, List<AssetEntry>> parentEntries =
         contentRepositoryTreeService.getParentAssetEntryMap();

      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      AssetEntry worksheetRoot = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", user);
      setWorksheetFolderActions(worksheetRoot, principal);

      List<TreeNodeModel> children =
         getGlobalDataSetChildren(worksheetRoot, parentEntries, principal);
      children.add(0, getPrivateDataSets(principal));

      return TreeNodeModel.builder()
         .label(Catalog.getCatalog(principal).getString("Global Worksheet"))
         .data(worksheetRoot)
         .type(PortalDataType.SHARED_WORKSHEETS_FOLDER.name())
         .expanded(false)
         .addAllChildren(children)
         .build();
   }

   private List<TreeNodeModel> getGlobalDataSetChildren(AssetEntry parentEntry,
                                                        Map<AssetEntry, List<AssetEntry>> entries,
                                                        Principal principal)
   {
      return entries.getOrDefault(parentEntry, Collections.emptyList())
         .stream()
         .filter(entry -> checkPermission(entry, principal) && entry.getType().isFolder())
         .sorted()
         .map(entry ->  {
            setWorksheetFolderActions(entry, principal);
            List<TreeNodeModel> children = getGlobalDataSetChildren(entry, entries, principal);
            return TreeNodeModel.builder()
               .label(entry.getName())
               .data(entry)
               .dragName("AssetEntry")
               .type(entry.getType().name())
               .leaf(children == null || children.size() == 0)
               .children(children)
               .materialized(AssetTreeController.getMaterialized(entry))
               .build();
         })
         .collect(Collectors.toList());
   }

   private boolean checkDataModelFolderPermission(String folderPath, ResourceAction action,
                                                  Principal principal)
   {
      try {
         return securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
            folderPath, action);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check data model folder permission for: " + folderPath, ex);
      }

      return false;
   }

   /**
    * Check access permisson for target entry with target user.
    */
   private boolean checkPermission(AssetEntry entry, Principal principal) {
      try {
        return securityEngine.checkPermission(
            principal, ResourceType.ASSET, entry.getPath(), ResourceAction.READ);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + entry.getPath(), ex);
      }

      return false;
   }

   /**
    * Check access permission for target data source folder with target user.
    */
   private boolean checkPermission(DataSourceFolder folder, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder.getFullName(), ResourceAction.READ);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + folder.getFullName(), ex);
      }

      return false;
   }

   /**
    * Check access permission for target data source folder with target user.
    */
   private boolean checkPermission(XDataSource dataSource, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, dataSource.getFullName(), ResourceAction.READ);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + dataSource.getFullName(), ex);
      }

      return false;
   }

   /**
    * Check access permission for target data source with target user.
    */
   private boolean checkPermission(XDataSource dataSource, ResourceAction action,
                                   Principal principal)
   {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, dataSource.getFullName(), action);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + dataSource.getFullName(), ex);
      }

      return false;
   }

   private TreeNodeModel getPrivateDataSets(Principal principal) throws Exception {
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry worksheetRoot = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, "/", user);
      setWorksheetFolderActions(worksheetRoot, principal);
      List<TreeNodeModel> children = getPrivateDataSetChildren(worksheetRoot, principal);
      return TreeNodeModel.builder()
         .label(Catalog.getCatalog(principal).getString("User Worksheet"))
         .data(worksheetRoot)
         .type(PortalDataType.PRIVATE_WORKSHEETS_FOLDER.name())
         .expanded(false)
         .addAllChildren(children)
         .build();
   }

   public List<TreeNodeModel> getPrivateDataSetChildren(AssetEntry parentEntry, Principal principal)
      throws Exception
   {
      AssetEntry.Selector selector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
         AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);
      final AssetEntry[] entries = parentEntry.isFolder() ?
         assetRepository.getEntries(parentEntry, principal, ResourceAction.READ, selector) :
         new AssetEntry[0];
      final List<TreeNodeModel> nodes = new ArrayList<>();

      for(AssetEntry entry : entries) {
         if(RecycleUtils.isInRecycleBin(entry.getPath()) || !entry.getType().isFolder()) {
            continue;
         }

         setWorksheetFolderActions(entry, principal);

         final List<TreeNodeModel> children = getPrivateDataSetChildren(entry, principal);
         TreeNodeModel node =
            TreeNodeModel.builder()
               .label(entry.getName())
               .data(entry)
               .dragName("AssetEntry")
               .type(entry.getType().name())
               .leaf(children == null || children.size() == 0)
               .children(children)
               .materialized(AssetTreeController.getMaterialized(entry))
               .build();
         nodes.add(node);
      }

      return nodes;
   }

   private TreeNodeModel getDataSources(Principal principal) {
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, "/", user);
      setDataSourceFolderActions(entry, principal);

      List<TreeNodeModel> children =
         getDataSourceChildren(principal, null, registry, user);

      return TreeNodeModel.builder()
         .label(Catalog.getCatalog(principal).getString("Data Source"))
         .data(entry)
         .type(PortalDataType.DATA_SOURCE_ROOT_FOLDER.name())
         .expanded(false)
         .addAllChildren(children)
         .build();
   }

   private List<TreeNodeModel> getDataSourceChildren(Principal principal, String parentFolderName,
      DataSourceRegistry registry, IdentityID user)
   {
      //get folderModels
      List<String> folderNames = new ArrayList<>(registry.getSubfolderNames(parentFolderName));
      Collections.sort(folderNames);
      List<DataSourceFolder> dfolderModels = folderNames.stream()
         .map(folderName -> registry.getDataSourceFolder(folderName))
         .filter(folderModel -> folderModel != null && checkPermission(folderModel, principal))
         .sorted()
         .collect(Collectors.toList());

      List<TreeNodeModel> folderModels = dfolderModels.stream()
         .map(folderModel -> {
            List<TreeNodeModel> children = getDataSourceChildren(principal, folderModel.getFullName(), registry, user);
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.DATA_SOURCE_FOLDER, folderModel.getFullName(), user);
            setDataSourceFolderActions(entry, principal);

            return TreeNodeModel.builder()
               .label(folderModel.getName())
               .data(entry)
               .dragName("AssetEntry")
               .type(PortalDataType.DATA_SOURCE_FOLDER.name())
               .leaf(children == null || children.size() == 0)
               .children(children)
               .build();
         })
         .collect(Collectors.toList());

      //get dataSourceModels
      List<String> dataSourceNames = new ArrayList<>(registry.getSubDataSourceNames(parentFolderName));
      Collections.sort(dataSourceNames);
      List<XDataSource> xDataSources = dataSourceNames.stream()
         .map(registry::getDataSource)
         .collect(Collectors.toList());

      List<TreeNodeModel> dataSourceModels = xDataSources.stream()
         .filter(dataSource -> dataSource instanceof JDBCDataSource ||
            dataSource instanceof TabularDataSource || dataSource instanceof XMLADataSource)
         .filter(dataSourceModel -> checkPermission(dataSourceModel, principal))
         .map(dataSourceModel -> {
            TreeNodeModel dataModelNode = getDataModelNode(dataSourceModel.getFullName(),
               registry, user, principal);
            List<TreeNodeModel> models = new ArrayList<>();

            if(dataSourceModel instanceof JDBCDataSource) {
               models.add(dataModelNode);
               boolean selfOrg = principal instanceof SRPrincipal &&
                  ((SRPrincipal) principal).isSelfOrganization();
               boolean enterprise = LicenseManager.getInstance().isEnterprise();

               if(!selfOrg && enterprise) {
                  String virtualModelsLabel = Catalog.getCatalog().getString("Virtual Private Models");
                  AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                                    null, dataSourceModel.getFullName() + "/" + virtualModelsLabel, user);
                  entry.setProperty("databasePath", dataSourceModel.getFullName());
                  setDataSourceTreeModelActions(dataSourceModel.getFullName(), entry, principal);

                  models.add(TreeNodeModel.builder()
                                .label(virtualModelsLabel)
                                .data(entry)
                                .type(PortalDataType.VPM_FOLDER.name())
                                .leaf(true)
                                .build());
               }
            }

            AssetEntry dataEntry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
               dataSourceModel.getFullName(), user);
            dataEntry.setProperty("queryCreatable", isQueryCreatable(dataSourceModel.getFullName(), principal) + "");
            setDataSourceTreeModelActions(dataSourceModel.getFullName(), dataEntry, principal);
            String sourceType = PortalDataType.DATA_SOURCE.name();

            if(dataSourceModel instanceof JDBCDataSource) {
               sourceType = PortalDataType.DATABASE.name();
            }
            else if(dataSourceModel instanceof XMLADataSource) {
               sourceType = PortalDataType.XMLA_SOURCE.name();
            }

            return TreeNodeModel.builder()
               .label(dataSourceModel.getName())
               .data(dataEntry)
               .dragName("AssetEntry")
               .type(sourceType)
               .leaf(models.size() == 0)
               .tooltip(getTooltip(dataSourceModel))
               .children(models)
               .build();
         })
         .collect(Collectors.toList());

       folderModels.addAll(dataSourceModels);

       return folderModels;
   }

   private boolean isQueryCreatable(String name, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS) &&
            securityEngine.checkPermission(
               principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ);
      }
      catch(inetsoft.sree.security.SecurityException ex) {
         LOG.error("Failed to check permission for: " + name, ex);
      }

      return false;
   }

   private String getTooltip(XDataSource dataSourceModel) {
      return Objects.toString(dataSourceModel.getDescription(), "");
   }

   private TreeNodeModel getDataModelNode(String databasePath, DataSourceRegistry registry,
                                          IdentityID user, Principal principal)
   {
      List<TreeNodeModel> dataModelChildren = getDataModelNodes(
         databasePath, registry, user, principal);

      AssetEntry dataModelEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         null, databasePath + "/Data Model", user);
      dataModelEntry.setProperty("databasePath", databasePath);
      setDataSourceTreeModelActions(databasePath, dataModelEntry, principal);

      if(dataModelChildren != null) {
         XDataModel dataModel = registry.getDataModel(databasePath);

         if(dataModel != null) {
            dataModelEntry.setProperty("partitionCount", dataModel.getPartitionCount() + "");
         }
      }

      return TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Data Model"))
         .data(dataModelEntry)
         .type(PortalDataType.DATA_MODEL.name())
         .leaf(dataModelChildren.isEmpty())
         .children(dataModelChildren)
         .build();
   }

   private List<TreeNodeModel> getDataModelNodes(String databasePath, DataSourceRegistry registry,
                                                 IdentityID user, Principal principal)
   {
      List<TreeNodeModel> dataModelChildren = new ArrayList<>();
      XDataModel dataModel = registry.getDataModel(databasePath);
      XDataSource dataSource = registry.getDataSource(databasePath);

      if(dataModel == null || dataSource == null) {
         return dataModelChildren;
      }

      String[] folders = dataModel.getFolders();

      if(folders == null) {
         return dataModelChildren;
      }

      for(String folderName : folders) {
         String folderPath = databasePath + "/" + folderName;

         if(!checkDataModelFolderPermission(folderPath, ResourceAction.READ, principal)) {
            continue;
         }

         AssetEntry folderEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_MODEL_FOLDER, folderPath, user);
         folderEntry.setProperty("databasePath", databasePath);
         folderEntry.setProperty("folder", folderName);
         folderEntry.setProperty("partitionCount", dataModel.getPartitionCount() + "");
         setDataSourceTreeModelActions(databasePath, folderEntry, principal);

         TreeNodeModel folderTreeNode = TreeNodeModel.builder()
            .label(folderName)
            .data(folderEntry)
            .type(PortalDataType.DATA_MODEL_FOLDER.name())
            .leaf(true)
            .build();

         dataModelChildren.add(folderTreeNode);
      }

     return dataModelChildren;
   }

   private boolean checkAdditionalPermission(String base, String additional, ResourceAction action,
                                             Principal principal)
   {
      if("(Default Connection)".equals(additional)) {
         return true;
      }

      if(StringUtils.isEmpty(base) || StringUtils.isEmpty(additional)) {
         return false;
      }

      String resource = base + "::" + additional;

      try {
         return securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, resource, action);
      } catch (SecurityException e) {
         LOG.error(e.getMessage());

         return false;
      }
   }

   /**
    * Set actions for data base tree node.
    * @param databasePath
    * @param dataEntry
    * @param principal
    */
   private void setDataSourceTreeModelActions(String databasePath, AssetEntry dataEntry,
                                              Principal principal)
   {
      if(dataEntry != null && dataEntry.isDataModelFolder()) {
         if(checkDataModelFolderPermission(dataEntry.getPath(), ResourceAction.DELETE, principal)) {
            dataEntry.setProperty(DatasourceTreeAction.DELETE.name(), "true");
         }

         if(checkDataModelFolderPermission(dataEntry.getPath(), ResourceAction.WRITE, principal) &&
            checkDataModelFolderPermission(dataEntry.getPath(), ResourceAction.DELETE, principal))
         {
            dataEntry.setProperty(DatasourceTreeAction.RENAME.name(), "true");
         }

         if(checkDataModelFolderPermission(dataEntry.getPath(), ResourceAction.WRITE, principal)) {
            dataEntry.setProperty(DatasourceTreeAction.EDIT.name(), "true");
            dataEntry.setProperty(DatasourceTreeAction.CREATE_CHILDREN.name(), "true");
         }
      }
      else {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         XDataSource dataSource = registry.getDataSource(databasePath);

         if(dataSource == null) {
            return;
         }

         if(checkPermission(dataSource, ResourceAction.DELETE, principal)) {
            dataEntry.setProperty(DatasourceTreeAction.DELETE.name(), "true");
         }

         if(checkPermission(dataSource, ResourceAction.WRITE, principal) &&
            checkPermission(dataSource, ResourceAction.DELETE, principal))
         {
            dataEntry.setProperty(DatasourceTreeAction.RENAME.name(), "true");
         }

         if(checkPermission(dataSource, ResourceAction.WRITE, principal)) {
            dataEntry.setProperty(DatasourceTreeAction.EDIT.name(), "true");
            dataEntry.setProperty(DatasourceTreeAction.CREATE_CHILDREN.name(), "true");
         }
      }
   }

   private void setDataSourceFolderActions(AssetEntry entry, Principal principal) {
      try {
         if(!Tool.equals(entry.getPath(), "/")) {
            if(securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
               entry.getPath(), ResourceAction.DELETE))
            {
               entry.setProperty(DatasourceTreeAction.DELETE.name(), "true");
            }

            if(securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
                  entry.getPath(), ResourceAction.DELETE) &&
               securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
                  entry.getPath(), ResourceAction.WRITE))
            {
               entry.setProperty(DatasourceTreeAction.RENAME.name(), "true");
            }
         }

         boolean hasCreatPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, entry.getPath(), ResourceAction.WRITE);

         if(!hasCreatPermission && entry.isRoot()) {
            hasCreatPermission = securityEngine.checkPermission(
               principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         }

         if(hasCreatPermission) {
            entry.setProperty(DatasourceTreeAction.CREATE_CHILDREN.name(), "true");
            entry.setProperty(DatasourceTreeAction.NEW_DATASOURCE.name(), "true");
         }
      }
      catch(Exception e) {
         LOG.error("Failed to check permission for: " + entry.getPath(), e);
      }
   }

   private void setWorksheetFolderActions(AssetEntry entry, Principal principal) {
      if(!Tool.equals(entry.getPath(), "/")) {
         if(checkPermission(entry, ResourceAction.DELETE, principal)) {
            entry.setProperty(DatasourceTreeAction.DELETE.name(), "true");
         }
         else if(entry.getProperty(DatasourceTreeAction.DELETE.name()) != null) {
            entry.setProperty(DatasourceTreeAction.DELETE.name(), null);
         }

         if(checkPermission(entry, ResourceAction.WRITE, principal) &&
            checkPermission(entry, ResourceAction.DELETE, principal))
         {
            entry.setProperty(DatasourceTreeAction.RENAME.name(), "true");
         }
         else if(entry.getProperty(DatasourceTreeAction.RENAME.name()) != null) {
            entry.setProperty(DatasourceTreeAction.RENAME.name(), null);
         }
      }

      if(checkPermission(entry, ResourceAction.WRITE, principal)) {
         entry.setProperty(DatasourceTreeAction.CREATE_CHILDREN.name(), "true");
      }
      else if(entry.getProperty(DatasourceTreeAction.CREATE_CHILDREN.name()) != null) {
         entry.setProperty(DatasourceTreeAction.CREATE_CHILDREN.name(), null);
      }

      try {
         if(checkPermission(entry, ResourceAction.WRITE, principal) &&
            securityEngine.checkPermission(
               principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS))
         {
            entry.setProperty(DatasourceTreeAction.NEW_WORKSHEET.name(), "true");
         }
         else if(entry.getProperty(DatasourceTreeAction.NEW_WORKSHEET.name()) != null) {
            entry.setProperty(DatasourceTreeAction.NEW_WORKSHEET.name(), null);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to check permission for: " + entry.getPath(), e);
      }
   }

   /**
    * Retrieves all VPMs for a given database.
    *
    * @param database the database name.
    *
    * @return a list of VPMs.
    */
   public List<TreeNodeModel> getVpms(String database, IdentityID user, Principal principal) {
      List<TreeNodeModel> results = new ArrayList<>();
      XDataModel dataModel = DataSourceRegistry.getRegistry().getDataModel(database);

      AssetEntry[] entries = getModelAssetEntries(database + "/", AssetEntry.Type.VPM);

      for(AssetEntry entry : entries) {
         AssetEntry dataEntry = new AssetEntry(AssetRepository.QUERY_SCOPE, null,
            entry.getPath(), user);
         setDataSourceTreeModelActions(database, dataEntry, principal);
         VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(entry.getName());
         String desc = Objects.toString(vpm.getDescription(), "");
         dataEntry.setProperty("desc", desc);

         results.add(TreeNodeModel.builder()
            .label(entry.getName())
            .data(dataEntry)
            .type(PortalDataType.VPM.name())
            .leaf(true)
            .tooltip(desc)
            .build());
      }

      return results;
   }

   /**
    * Retrieves AssetEntry of a model from the repository.
    *
    * @param oldEntry the asset entry we are trying to find.
    *
    * @return the asset entry from the repository.
    * @throws Exception if unable to retrieve the asset entry.
    */
   private AssetEntry getModelAssetEntry(AssetEntry oldEntry) {
      AssetEntry[] entries = getModelAssetEntries(oldEntry.getParentPath(), oldEntry.getType());

      for(AssetEntry newEntry : entries) {
         if(newEntry.toIdentifier().equals(oldEntry.toIdentifier())) {
            return (AssetEntry) newEntry.clone();
         }
      }

      return null;
   }

   /**
    * Retrieves all Asset Entries for a certain model type.
    *
    * @param path the path of the parent folder.
    *
    * @return the asset entries for the entry type.
    * @throws Exception if unable to retrieve the asset entries.
    */
   private AssetEntry[] getModelAssetEntries(String path, AssetEntry.Type type) {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      AssetEntry[] entries = registry.getEntries(path, type);

      return entries;
   }

   /**
    * Determines if the current user has the specified permission on an asset.
    *
    * @param entry      the asset entry.
    * @param action the desired permission.
    *
    * @return <tt>true</tt> if allowed; <tt>false</tt> otherwise.
    */
   private boolean checkPermission(AssetEntry entry, ResourceAction action, Principal principal) {
      boolean result = true;

      try {
         assetRepository.checkAssetPermission(principal, entry, action);
      }
      catch(Exception ignore) {
         result = false;
      }

      return result;
   }

   private static class PhysicalComparator implements Comparator<TreeNodeModel> {
      @Override
      public int compare(TreeNodeModel node1, TreeNodeModel node2) {
         return Comparator.comparing((TreeNodeModel node) -> node.label().toLowerCase(),
            Comparator.nullsFirst(Comparator.naturalOrder())).compare(node1, node2);
      }
   }

   private final SecurityEngine securityEngine;
   private final ContentRepositoryTreeService contentRepositoryTreeService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(DatasourcesTreeService.class);
}
