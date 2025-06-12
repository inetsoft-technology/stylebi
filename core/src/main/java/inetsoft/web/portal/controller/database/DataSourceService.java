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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.SearchComparator;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.viewsheet.DatasourceIgnoreGlobalShare;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DataSourceService provides methods for portal data model.
 *
 * @since 13.3
 */
@Component
public class DataSourceService {
   @Autowired
   public DataSourceService(AssetRepository assetRepository, SecurityEngine securityEngine,
                            XRepository repository)
   {
      this.assetRepository = assetRepository;
      this.securityEngine = securityEngine;
      this.repository = repository;
   }

   /**
    * Determines if the current user has the specified permission on the
    * database.
    * @param databasePath database path.
    * @param action the desired permission.
    *
    * @return <tt>true</tt> if allowed; <tt>false</tt> otherwise.
    */
   public boolean checkPermission(String databasePath, ResourceAction action,
                                     Principal principal)
      throws Exception
   {
      return securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, databasePath,
         action);
   }

   /**
    * Determines if the current user has the specified permission on the physical view.
    * @param databasePath the database path.
    * @param folder       the data model folder.
    * @param action       the desired permission.
    * @param principal    current login user.
    *
    * @return <tt>true</tt> if allowed; <tt>false</tt> otherwise.
    */
   public boolean checkPermission(String databasePath, String folder, ResourceAction action,
                                  Principal principal)
      throws Exception
   {
      if(StringUtils.isEmpty(folder)) {
         return checkPermission(databasePath, action, principal);
      }

      return securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
         SUtil.appendPath(databasePath, folder), action);
   }

   /**
    * Check if a data source with the given name is already present.
    * @param name the name to check
    * @return  true if a data source with that name is present
    * @throws Exception if failed to check the data sources
    */
   public boolean checkDuplicate(String name) throws Exception {
      return repository.getDataSource(name) != null;
   }

   /**
    * Checks if model name is a duplicate.
    * @param database the database name
    * @param name the model name
    * @return true if model name is a duplicate
    * @throws Exception if could not check for duplicate models
    */
   @DatasourceIgnoreGlobalShare
   public boolean isUniqueModelName(String database, String name) throws Exception {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      return dataModel.getLogicalModel(name) != null || dataModel.getPartition(name) != null ||
         dataModel.getVirtualPrivateModel(name) != null;
   }

   /**
    * Checks if extended model name is a duplicate.
    * @param database the database name
    * @param physicalModel physical model name
    * @param  parent base model
    * @param name the model name
    * @return true if model name is a duplicate
    * @throws Exception if could not check for duplicate models
    */
   public boolean isUniqueExtendedLogicalModelName(String database, String physicalModel,
                                                   String parent, String name)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      XLogicalModel logicalModel = dataModel.getLogicalModel(parent);

      if(logicalModel == null || logicalModel.getPartition() == null||
         !logicalModel.getPartition().equals(physicalModel))
      {
         throw new FileNotFoundException(database + "/" + physicalModel + "/" + logicalModel);
      }

      String existNames[] = logicalModel.getLogicalModelNames();

      if(existNames == null) {
         return true;
      }

      for(String existName : existNames) {
         if(existName != null && existName.equals(name)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Checks if extended model name is a duplicate.
    * @param database the database name
    * @param  parent base model
    * @param name the model name
    * @return true if model name is a duplicate
    * @throws Exception if could not check for duplicate models
    */
   public boolean isUniqueExtendedPhysicalModelName(String database, String parent,
                                                    String name)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      XPartition partition = dataModel.getPartition(parent);

      if(partition == null) {
         throw new FileNotFoundException(database + "/" + parent);
      }

      String existNames[] = partition.getPartitionNames();

      if(existNames == null) {
         return true;
      }

      for(String existName : existNames) {
         if(existName != null && existName.equals(name)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Determines if the current user has the specified permission on an asset.
    *
    * @param entry      the asset entry.
    * @param action     the desired permission.
    *
    * @return <tt>true</tt> if allowed; <tt>false</tt> otherwise.
    */
   public boolean checkPermission(AssetEntry entry, ResourceAction action, Principal principal) {
      boolean result = true;

      try {
         assetRepository.checkAssetPermission(principal, entry, action);
      }
      catch(Exception ignore) {
         result = false;
      }

      return result;
   }

   public AssetListBrowseModel getSearchVpmBrowseModel(String database, String query, Principal principal)
      throws Exception
   {
      AssetListBrowseModel model = getVpmBrowseModel(database, principal);
      AssetItem[] items = Arrays.stream(model.getItems())
         .filter((item) -> item.getName().toLowerCase().indexOf(query.toLowerCase()) != -1)
         .sorted((a, b) -> new SearchComparator.StringComparator(query).compare(a.getName(), b.getName()))
         .toArray(AssetItem[]::new);
      model.setItems(items);

      return model;
   }

   public AssetListBrowseModel getSearchVpmBrowseModelNames(String database, String query, Principal principal)
      throws Exception
   {
      AssetListBrowseModel model = getSearchVpmBrowseModel(database, query, principal);
      String[] names = Arrays.stream(model.getItems())
         .map((item) -> item.getName())
         .toArray(String[]::new);
      model.setNames(names);
      model.setItems(null);

      return model;
   }

   /**
    * Retrieves all VPMs for a given database.
    *
    * @param database the database name.
    *
    * @return a list of VPMs.
    */
   public AssetListBrowseModel getVpmBrowseModel(String database, Principal principal)
      throws Exception
   {
      AssetListBrowseModel model = new AssetListBrowseModel();
      XDataModel dataModel = repository.getDataModel(database);
      model.setDateFormat(Tool.getDateFormatPattern());
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      List<Vpm> results = new ArrayList<>();
      AssetEntry[] entries = getModelAssetEntries(database + "/", AssetEntry.Type.VPM);

      if(!checkPermission(database, ResourceAction.WRITE, principal)) {
         return model;
      }

      model.setEditable(true);
      model.setDeletable(true);

      VirtualPrivateModel vmodel = null;

      for(AssetEntry entry : entries) {
         Vpm result = new Vpm();

         if(entry.getCreatedUsername() != null) {
            IdentityID identityID = entry.getUser();
            String organization = identityID == null ? null : identityID.orgID;
            IdentityID createdUserID = new IdentityID(entry.getCreatedUsername(), organization);
            User user = provider.getUser(createdUserID);

            if(user != null) {
               result.setCreatedBy(user.getAlias() == null ? user.getName() : user.getAlias());
            }
            else {
               result.setCreatedBy(SUtil.getUserAlias(new IdentityID(entry.getCreatedUsername(),
                  OrganizationManager.getInstance().getCurrentOrgID())));
            }
         }

         Date date = entry.getCreatedDate();
         LocalDateTime cdate = null;

         if(date != null) {
            cdate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
         }

         String fmt = SreeEnv.getProperty("format.date.time");
         DateTimeFormatter df = DateTimeFormatter.ofPattern(fmt);
         String dateLabel = cdate == null ? "" : df.format(cdate);

         result.setName(entry.getName());
         result.setPath(entry.getPath());
         result.setUrlPath(URLEncoder.encode(entry.getPath(), "UTF-8"));
         result.setEditable(true);
         result.setDeletable(true);
         result.setCreatedDate(date != null ? date.getTime() : -1);
         result.setCreatedDateLabel(dateLabel);
         result.setId(entry.toIdentifier());
         result.setDatabaseName(database);
         vmodel = dataModel.getVirtualPrivateModel(entry.getName());
         result.setDescription(vmodel.getDescription());
         results.add(result);
      }

      model.setItems(results.toArray(new AssetItem[results.size()]));
      return model;
   }

   /**
    * Retrieves all physical models for a given database.
    *
    * @param database the database name.
    *
    * @return a list of physical models.
    */
   public List<PhysicalModel> getPhysicalModels(String database, String folder,
                                                Principal principal, boolean allChild)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(isRootDataModelFolder(folder)) {
         folder = null;
      }

      List<PhysicalModel> results = new ArrayList<>();

      if(!checkPermission(database, folder, ResourceAction.WRITE, principal)) {
         return results;
      }

      AssetEntry[] entries = getModelAssetEntries(database + "/", AssetEntry.Type.PARTITION);

      for(AssetEntry entry : entries) {
         XPartition partition = dataModel.getPartition(entry.getName());
         String folderName = partition.getFolder();

         if(partition == null) {
            continue;
         }

         if(!allChild && Tool.equals(folderName, folder) ||
            // for search
            allChild && (isRootDataModelFolder(folder) ||
            StringUtils.startsWith(folderName, folder)))
         {
            entry.setProperty("prefix", database);
            entry.setProperty("folder", folderName);

            if(!checkPermission(entry, ResourceAction.WRITE, principal)) {
               continue;
            }

            PhysicalModel physicalModel = buildPhysicalModel(entry, database, partition,
               null, folderName, principal, provider);
            physicalModel.setExtendViews(
               getExtendedPhysicalView(entry, database, partition, provider,  principal));
            results.add(physicalModel);
         }
      }

      return results;
   }

   /**
    * Get the partition count of the database.
    *
    * @param database
    * @return
    * @throws Exception
    */
   public int getDatabasePartitionCount(String database) throws Exception {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel != null) {
         return dataModel.getPartitionCount();
      }

      return 0;
   }

   /**
    * Whether the folder is data model root folder(Data Model).
    * @param folderName
    * @return
    */
   public boolean isRootDataModelFolder(String folderName) {
      return folderName == null || "".equals(folderName) || "/".equals(folderName);
   }

   public boolean checkDataModelFolderPermission(String folderFullPath,
                                                  ResourceAction action,
                                                  Principal principal)
      throws SecurityException
   {
      return securityEngine.checkPermission(principal, ResourceType.DATA_MODEL_FOLDER,
         folderFullPath, action);
   }

   private List<PhysicalModel> getExtendedPhysicalView(AssetEntry parent,
                                                       String database,
                                                       XPartition basePartition,
                                                       SecurityProvider provider,
                                                       Principal principal)
      throws Exception
   {
      List<PhysicalModel> result = new ArrayList<>();
      String basePath = parent.getPath();

      if(basePath != null && !basePath.endsWith("/")) {
         basePath += "/";
      }

      AssetEntry[] entries =
         getModelAssetEntries(basePath, AssetEntry.Type.EXTENDED_PARTITION);

      if(entries != null) {
         for(AssetEntry entry : entries) {
            entry.setProperty("prefix", database);
            entry.setProperty("folder", basePartition.getFolder());

            if(!checkPermission(entry, ResourceAction.WRITE, principal)) {
               continue;
            }

            result.add(buildPhysicalModel(entry, database, basePartition, parent.getName(),
               basePartition.getFolder(), principal, provider));
         }
      }

      return result;
   }

   private PhysicalModel buildPhysicalModel(AssetEntry entry,
                                            String database,
                                            XPartition partition,
                                            String parentView,
                                            String folderName,
                                            Principal principal,
                                            SecurityProvider provider)
      throws Exception
   {
      PhysicalModel result = new PhysicalModel();

      if(entry.getCreatedUsername() != null) {
         IdentityID createdUserID = new IdentityID(entry.getCreatedUsername(), entry.getOrgID());
         User user = provider.getUser(createdUserID);

         if(user != null) {
            result.setCreatedBy(user.getAlias() == null ? user.getName() : user.getAlias());
         }
         else {
            result.setCreatedBy(entry.getCreatedUsername());
         }
      }

      Date date = entry.getCreatedDate();
      LocalDateTime cdate = null;

      if(date != null) {
         cdate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
      }

      String fmt = SreeEnv.getProperty("format.date.time");
      DateTimeFormatter df = DateTimeFormatter.ofPattern(fmt);
      String dateLabel = cdate == null ? "" : df.format(cdate);

      result.setName(entry.getName());
      result.setPath(entry.getPath());
      result.setUrlPath(URLEncoder.encode(entry.getPath(), "UTF-8"));
      result.setEditable(checkPermission(entry, ResourceAction.WRITE, principal));
      result.setDeletable(checkPermission(entry, ResourceAction.DELETE, principal));
      result.setCreatedDate(date != null ? date.getTime() : -1);
      result.setCreatedDateLabel(dateLabel);
      result.setId(entry.toIdentifier());
      result.setDatabaseName(database);
      result.setParentView(parentView);
      result.setDescription(partition.getDescription());
      result.setFolderName(folderName);

      return result;
   }

   /**
    * Get the logical models under the data model folder.
    * @param database database path.
    * @param folder data model folder name.
    * @param principal
    * @return
    * @throws Exception
    */
   public List<LogicalModel> getLogicalModels(String database, String folder,
                                              Principal principal, boolean allChild)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      List<LogicalModel> results = new ArrayList<>();

      if(!checkPermission(database, folder, ResourceAction.READ, principal)) {
         return results;
      }

      if(isRootDataModelFolder(folder)) {
         folder = null;
      }

      for(String name : dataModel.getLogicalModelNames()) {
         XLogicalModel lm = dataModel.getLogicalModel(name);
         String folderName = lm.getFolder();
         boolean root = folder == null && (folderName == null || "/".equals(folderName));

         if(!allChild && (root || Tool.equals(folderName, folder)) ||
            // for search
            allChild && (isRootDataModelFolder(folder) ||
            StringUtils.startsWith(folderName, folder)))
         {
            String path = database + "/" + name;
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.LOGIC_MODEL, path, null);
            entry = getModelAssetEntry(entry);
            entry.setProperty("prefix", database);
            entry.setProperty("folder", folderName);

            if(!checkPermission(entry, ResourceAction.READ, principal)) {
               continue;
            }

            LogicalModel model = buildLogicalModel(entry, database, lm, lm.getPartition(),
               null, folderName, null, provider, principal);
            model.setExtendModels(getExtendedModels(entry, lm, lm.getPartition(), database,
               provider, principal));
            results.add(model);
         }
      }

      return results;
   }

   private List<LogicalModel> getExtendedModels(AssetEntry parent,
                                                XLogicalModel baseModel,
                                                String physicalView, String database,
                                                SecurityProvider provider, Principal principal)
      throws Exception
   {
      List<LogicalModel> result = new ArrayList<>();
      String basePath = parent.getPath();

      if(basePath != null && !basePath.endsWith("/")) {
         basePath += "/";
      }

      AssetEntry[] extendModels =
         getModelAssetEntries(basePath, AssetEntry.Type.EXTENDED_LOGIC_MODEL);
      String connection = null;
      XLogicalModel childModel = null;

      if(extendModels != null) {
         for(AssetEntry entry : extendModels) {
            if(baseModel == null) {
               return result;
            }

            entry.setProperty("prefix", database);
            entry.setProperty("folder", baseModel.getFolder());

            if(!checkPermission(entry, ResourceAction.READ, principal)) {
               continue;
            }

            childModel = baseModel.getLogicalModel(entry.getName());

            if(childModel != null) {
               connection = childModel.getConnection();
            }

            entry.setProperty(XUtil.DATASOURCE_ADDITIONAL, connection);
            result.add(buildLogicalModel(entry, database, baseModel, physicalView,
                  parent.getName(), baseModel.getFolder(), connection, provider, principal));
         }
      }

      return result;
   }

   private LogicalModel buildLogicalModel(AssetEntry entry,
                                          String database,
                                          XLogicalModel model,
                                          String physicalView,
                                          String parentModel,
                                          String folderName,
                                          String connection,
                                          SecurityProvider provider,
                                          Principal principal)
      throws Exception
   {
      LogicalModel logicalModel = new LogicalModel();

      if(entry.getCreatedUsername() != null) {
         IdentityID createdUserID = new IdentityID(entry.getCreatedUsername(), entry.getOrgID());
         User user = provider.getUser(createdUserID);

         if(user != null) {
            logicalModel.setCreatedBy(user.getAlias() == null ? user.getName() : user.getAlias());
         }
         else {
            logicalModel.setCreatedBy(entry.getCreatedUsername());
         }
      }

      Date date = entry.getCreatedDate();
      LocalDateTime cdate = null;

      if(date != null) {
         cdate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
      }

      String fmt = SreeEnv.getProperty("format.date.time");
      DateTimeFormatter df = DateTimeFormatter.ofPattern(fmt);
      String dateLabel = cdate == null ? "" : df.format(cdate);

      logicalModel.setName(entry.getName());
      logicalModel.setPath(entry.getPath());
      logicalModel.setUrlPath(URLEncoder.encode(entry.getPath(), "UTF-8"));
      logicalModel.setEditable(checkPermission(entry, ResourceAction.WRITE, principal));
      logicalModel.setDeletable(checkPermission(entry, ResourceAction.DELETE, principal));
      logicalModel.setCreatedDate(date != null ? date.getTime() : -1);
      logicalModel.setCreatedDateLabel(dateLabel);
      logicalModel.setId(entry.toIdentifier());
      logicalModel.setDatabaseName(database);
      logicalModel.setPhysicalModel(physicalView);
      logicalModel.setParentModel(parentModel);
      logicalModel.setDescription(model.getDescription());
      logicalModel.setFolderName(folderName);
      logicalModel.setConnection(connection);

      return logicalModel;
   }

   /**
    * Updates the created time/date of a data source entry.
    *
    * @param entry the updated asset entry
    */
   public void updateDataSourceAssetEntry(AssetEntry entry) {
      try {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         registry.setObject(entry, registry.getObject(entry, true));
      }
      catch(Exception e) {
         LoggerFactory.getLogger(DataSourceService.class).debug(
            "Failed to keep created time/date of updated data source: " + entry.getName());
      }
   }

   /**
    * Retrieves AssetEntry of a model from the repository.
    *
    * @param oldEntry the asset entry we are trying to find.
    *
    * @return the asset entry from the repository.
    */
   public AssetEntry getModelAssetEntry(AssetEntry oldEntry) {
      AssetEntry[] entries = getModelAssetEntries(oldEntry.getParentPath(), oldEntry.getType());

      for(AssetEntry newEntry : entries) {
         if(newEntry.toIdentifier().equals(oldEntry.toIdentifier())) {
            return newEntry;
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
    */
   public AssetEntry[] getModelAssetEntries(String path, AssetEntry.Type type) {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      return registry.getEntries(path, type);
   }

   /**
    * Remove the DefaultMetaDataProvider from cache.
    * @param ds data source.
    * @return
    * @throws Exception
    */
   public DefaultMetaDataProvider removeDefaultMetaDataProviderCache(XDataSource ds)
      throws Exception
   {
      for(int i = 0; i < metaDataProviders.size(); i++) {
         if(metaDataProviders.get(i) != null && Tool.equals(ds, metaDataProviders.get(i).getDataSource())) {
            return metaDataProviders.remove(i);
         }
      }

      return null;
   }

   /**
    * Reuse DefaultMetaDataProvider for same datasource to share the meta data.
    * @throws Exception
    */
   public DefaultMetaDataProvider getDefaultMetaDataProvider(XDataSource ds, XDataModel dm)
      throws Exception
   {
      DefaultMetaDataProvider meta = metaDataProviders.stream()
         .filter(p -> Tool.equals(ds, p.getDataSource()) && ds != null &&
            Tool.equals(ds.getName(), p.getDataSource().getName()))
         .findFirst().orElse(null);

      if(meta == null) {
         meta = new DefaultMetaDataProvider();
         meta.setDataSource(ds);
         meta.setDataModel(dm);
         meta.setPortalData(true);
         metaDataProviders.add(meta);
      }

      return meta;
   }

   /**
    * Get the data model.
    */
   public XDataModel getDataModel(String sourceName) throws Exception{
      return repository.getDataModel(sourceName);
   }

   /**
    * Get the data source for portal data model to Distinguish from another places.
    */
   public XDataSource getDataSource(String sourceName) {
      return getDataSource(sourceName, null);
   }

   /**
    * Get the data source for portal data model to Distinguish from another places.
    * @param sourceName  the datasource name.
    * @param additional  the additional source name.
    */
   public XDataSource getDataSource(String sourceName, String additional) {
      XDataSource dataSource = repository.getDataSource(sourceName, true);

      if(additional != null) {
         dataSource = DatabaseModelUtil.getDatasource(dataSource, additional);
      }

      if(dataSource != null) {
         dataSource.setFromPortal(true);
      }

      return dataSource;
   }

   /**
    * Get SqlHelper for portal data model, don't need to get datasource by principal.
    * @param dx           the specific datasource.
    * @param additional   the additional name.
    * @return
    */
   public SQLHelper getSqlHelper(XDataSource dx, String additional) {
      if(additional == null) {
         additional = XUtil.OUTER_MOSE_LAYER_DATABASE;
      }

      return SQLHelper.getSQLHelper(dx, additional, null);
   }

   private List<DefaultMetaDataProvider> metaDataProviders = new ArrayList<>();
   private final AssetRepository assetRepository;
   private final SecurityEngine securityEngine;
   private final XRepository repository;
}
