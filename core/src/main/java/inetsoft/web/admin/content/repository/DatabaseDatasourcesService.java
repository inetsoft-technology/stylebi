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

import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.credential.Credential;
import inetsoft.util.credential.PasswordCredential;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.general.model.DatabaseSettingsModel;
import inetsoft.web.admin.security.*;
import inetsoft.web.portal.data.DeleteDatasourceInfo;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;

@Service
public class DatabaseDatasourcesService {
   @Autowired
   public DatabaseDatasourcesService(DatabaseTypeService databaseTypeService, //NOSONAR dependency injection
                                     SecurityEngine securityEngine,
                                     DatabaseSettingsService databaseSettingsService,
                                     XRepository repository,
                                     ResourcePermissionService resourcePermissionService,
                                     DataSourceStatusService dataSourceStatusService,
                                     IgniteSessionRepository sessionRepository)
   {
      this.databaseTypeService = databaseTypeService;
      this.securityEngine = securityEngine;
      this.databaseSettingsService = databaseSettingsService;
      this.repository = repository;
      this.resourcePermissionService = resourcePermissionService;
      this.dataSourceStatusService = dataSourceStatusService;
      this.sessionRepository = sessionRepository;
   }

   public DriverAvailability getDriverAvailability() {
      DriverAvailability driverAvailability = new DriverAvailability();

      driverAvailability.setDrivers(databaseTypeService.getDrivers());
      driverAvailability.setDriverClasses(JDBCHandler.getDrivers());
      return driverAvailability;
   }

   public ConnectionStatus testDataSourceConnection(String path, DatabaseDefinition model,
                                                    Principal principal, boolean isAdditionalSource)
   {
      JDBCDataSource jdbcDataSource = getDatabase(path, model, false, isAdditionalSource);
      DatabaseSettingsModel databaseSettingsModel = DatabaseSettingsModel.builder()
         .driver(jdbcDataSource.getDriver())
         .databaseURL(jdbcDataSource.getURL())
         .requiresLogin(jdbcDataSource.isRequireLogin())
         .username(jdbcDataSource.getUser())
         .password(jdbcDataSource.getPassword())
         .defaultDB("")
         .build();
      return databaseSettingsService.testConnection(databaseSettingsModel, principal);
   }

   ConnectionStatus testDataSourceConnection(String path, DataSourceSettingsModel model,
                                             Principal principal, boolean isAdditionalSource) {
      return testDataSourceConnection(path, model.dataSource(), principal, isAdditionalSource);
   }

   /**
    * Gets the data source folder at the specified path.
    *
    * @param path      the path to the data source folder.
    * @param principal a principal that identifies the remote user.
    *
    * @return the name of the data source folder, without the preceding path.
    *
    * @throws Exception if the folder could not be obtained.
    */
   public DataSourceFolderSettingsModel getDataSourceFolder(String path, Principal principal)
      throws Exception
   {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      DataSourceFolderSettingsModel.Builder builder = DataSourceFolderSettingsModel.builder();
      boolean root = (path == null || path.isEmpty() || "/".equals(path));

      if(root) {
         securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.ADMIN);

         builder.name(null);
         builder.root(true);

         Arrays.stream(registry.getDataSourceFolderFullNames())
            .filter(f -> !f.contains("/"))
            .forEach(builder::addSiblingFolders);

         Arrays.stream(registry.getDataSourceFullNames())
            .filter(ds -> !ds.contains("/"))
            .forEach(builder::addSiblingDataSources);
      }
      else {
         DataSourceFolder folder = repository.getDataSourceFolder(path);

         if(folder == null) {
            throw new MessageException(
               Catalog.getCatalog().getString("em.common.invalidTreeNode", path));
         }

         builder.name(DataSourceFolder.getDisplayName(folder.getName()));
         builder.root(false);
         String parentFolder = DataSourceFolder.getParentName(path);
         String prefix = parentFolder + "/";
         Arrays.stream(registry.getDataSourceFolderFullNames())
            .filter(f -> parentFolder == null ? DataSourceFolder.getParentName(f) == null : f.startsWith(prefix))
            .map(DataSourceFolder::getDisplayName)
            .forEach(builder::addSiblingFolders);

         Arrays.stream(registry.getDataSourceFullNames())
            .filter(ds -> ds.startsWith(prefix))
            .map(DataSourceFolder::getDisplayName)
            .forEach(builder::addSiblingDataSources);
      }

      builder.permissions(resourcePermissionService.getTableModel(
         path, ResourceType.DATA_SOURCE_FOLDER, ResourcePermissionService.ADMIN_ACTIONS,
         principal));
      return builder.build();
   }

   String setDataSourceFolder(String path, DataSourceFolderSettingsModel model,
                              Principal principal) throws Exception
   {
      return setDataSourceFolder(path, null, model, principal);
   }

   /**
    * Updates the data source folder at the specified path.
    *
    * @param path      the current path to the data source folder.
    * @param model     the updated folder properties.
    * @param principal a principal that identifies the remote user.
    *
    * @return the new, full path to the updated data source folder.
    *
    * @throws Exception if the folder could not be updated.
    */
   String setDataSourceFolder(String path, String auditPath, DataSourceFolderSettingsModel model,
                              Principal principal) throws Exception
   {
      String newPath = path;

      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
          ActionRecord.ACTION_NAME_EDIT, auditPath, ActionRecord.OBJECT_TYPE_FOLDER,
          actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      if(model.root()) {
         securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.ADMIN);
      }
      else {
         securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, path, ResourceAction.ADMIN);

         DataSourceFolder folder = repository.getDataSourceFolder(path, true);

         if(folder == null) {
            throw new RuntimeException("Data space folder does not exist: " + path);
         }

         String parent = DataSourceFolder.getParentName(path);

         if(parent == null) {
            newPath = model.name();
         }
         else {
            newPath = parent + "/" + model.name();
         }

         if(!Objects.requireNonNull(newPath).equals(path)) {
            List<String> childrenSources = new ArrayList<>();
            DependencyTransformer.prepareChildrenSources(path, childrenSources, repository);
            RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
               path, newPath, childrenSources);
            RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
            folder.setName(newPath);

            Permission permission =
               securityEngine.getPermission(ResourceType.DATA_SOURCE_FOLDER, path);
            repository.updateDataSourceFolder(folder, path);

            if(permission != null) {
               securityEngine.setPermission(ResourceType.DATA_SOURCE_FOLDER, newPath, permission);
            }
         }
      }

      if(auditPath == null) {
         auditPath = newPath;
      }

      resourcePermissionService.setResourcePermissions(
         newPath, ResourceType.DATA_SOURCE_FOLDER, auditPath, model.permissions(), principal);
      actionRecord.setActionError("new name: " + model.name());
      actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      Audit.getInstance().auditAction(actionRecord, principal);

      return newPath;
   }

   public DatabaseDefinition[] getAdditionalDatabaseDefinition(String path, Principal principal)
      throws Exception
   {
      XDataSource dataSource = repository.getDataSource(path);
      List<DatabaseDefinition> definitions = new ArrayList<>();

      if(dataSource instanceof JDBCDataSource) {
         JDBCDataSource jdbcDataSource = (JDBCDataSource) dataSource;
         String[] names = jdbcDataSource.getDataSourceNames();

         Arrays.stream(names).forEach((name) -> {
            JDBCDataSource jds = jdbcDataSource.getDataSource(name);

            try {
               DatabaseDefinition def = getDatabaseDefinition(jds, principal);

               if(def != null) {
                  definitions.add(def);
               }
            }
            catch(Exception e) {
               LOG.debug("Failed to add definition for datasource {}", name, e);
            }
         });
      }

      return definitions.toArray(new DatabaseDefinition[0]);
   }

   public DatabaseDefinition getDatabaseDefinition(String path, Principal principal)
      throws Exception
   {
      XDataSource dataSource = repository.getDataSource(path);
      DatabaseDefinition def = getDatabaseDefinition(dataSource, principal);

      if(def != null) {
         def.setDeletable(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE,
            resourcePermissionService.getDataSourceResourceName(path), ResourceAction.DELETE));
      }

      return def;
   }

   public DatabaseDefinition getDatabaseDefinition(XDataSource dataSource, Principal principal)
      throws Exception
   {
      if(dataSource == null) {
         throw new RuntimeException("Datasource does not exist!");
      }
      else if(!(dataSource instanceof JDBCDataSource)) {
         return null;
      }

      JDBCDataSource database = (JDBCDataSource) dataSource;

      String driver = database.getDriver();
      DatabaseType type = databaseTypeService.getDatabaseTypeForDriver(driver);

      return JDBCUtil.buildDatabaseDefinition(database, type);
   }

   @Audited(
      objectType = ActionRecord.OBJECT_TYPE_DATASOURCE
   )
   public ConnectionStatus saveDatabase(String path, DataSourceSettingsModel model,
                                        @AuditActionName String actionName,
                                        @SuppressWarnings("unused") @AuditObjectName String objectName,
                                        @SuppressWarnings("unused") @AuditActionError String actionError,
                                        Principal principal) throws Exception
   {
      return saveDatabase(path, model, actionName, principal);
   }

   @Audited(
      objectType = ActionRecord.OBJECT_TYPE_DATASOURCE
   )
   public ConnectionStatus saveDatabase(String path,
                                        @AuditObjectName("dataSource().getName()") DataSourceSettingsModel model,
                                        @SuppressWarnings("unused") @AuditActionName String actionName,
                                        Principal principal)
      throws Exception
   {
      try {
         DataSourceRegistry.IGNORE_GLOBAL_SHARE.set(true);
         return saveDatabaseDefinition(path, model.dataSource(), actionName, principal,
                                       () -> model.additionalDataSources());
      }
      finally {
         DataSourceRegistry.IGNORE_GLOBAL_SHARE.remove();
      }
   }

   private ConnectionStatus saveDatabaseDefinition(
      String path,
      DatabaseDefinition database,
      String actionName,
      Principal principal,
      Supplier<DatabaseDefinition[]> getAdditionals) throws Exception
   {
      if(database == null) {
         // not a JDBC data source
         return null;
      }

      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      String name = database.getName().trim();
      String fullName = path;
      String oname = fullName;

      if(ActionRecord.ACTION_NAME_EDIT.equals(actionName)) {
         int idx = path.lastIndexOf("/");
         oname = idx == -1 ? path : path.substring(idx + 1);
      }

      XDataSource dataSource = repository.getDataSource(fullName);
      boolean newDataSource = false;

      if(checkDuplicate(actionName, oname, name)) {
         return new ConnectionStatus("Duplicate");
      }

      if(dataSource != null) {
         int index = fullName.lastIndexOf('/');
         String newPath = index == -1 ? name : fullName.substring(0, index) + "/" + name;

         if(registry.getDataSourceFolder(newPath) != null) {
            return new ConnectionStatus("Duplicate Folder");
         }
      }

      if(dataSource == null) {
         // Create the dataSource - path is the parent folder's path
         fullName = fullName.startsWith("/") ? fullName.substring(1) : fullName;
         fullName += !fullName.isEmpty() ? "/" + name : name;

         if(registry.getDataSourceFolder(fullName) != null) {
            return new ConnectionStatus("Duplicate Folder");
         }

         dataSource = createNewDatabase(fullName, principal);
         newDataSource = true;
      }
      else if(!(dataSource instanceof JDBCDataSource)) {
         return null;
      }

      JDBCDataSource jdbcDataSource = (JDBCDataSource) dataSource;
      JDBCDataSource oldDataSource = (JDBCDataSource) Tool.clone(dataSource);

      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.DATA_SOURCE, fullName, null);
      entry = getDataSourceAssetEntry(entry);
      String user = null;
      Date date = null;

      if(entry != null) {
         user = entry.getCreatedUsername();
         date = entry.getCreatedDate();
      }

      JDBCDataSource base = jdbcDataSource.getBaseDatasource();
      String newSrcName = fullName;

      if(base != null) {
         int index = fullName.lastIndexOf('/');

         if(index != -1) {
            newSrcName = fullName.substring(index + 1);
         }
      }

      Permission oldPermission = securityEngine.getPermission(ResourceType.DATA_SOURCE, fullName);
      JDBCDataSource newSrc = getDatabase(newSrcName, database);
      boolean newSourcePermission = false;
      boolean folderPermission = false;
      int index = fullName.lastIndexOf('/');

      if(index < 0) {
         folderPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE);
         newSourcePermission = securityEngine.checkPermission(
            principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
      }
      else {
         String parent = fullName.substring(0, index);
         folderPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, parent, ResourceAction.WRITE);
      }

      if(!newSrc.getFullName().equals(jdbcDataSource.getFullName()) &&
         registry.getDataSource(newSrc.getFullName()) != null)
      {
         if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE,
            resourcePermissionService.getDataSourceResourceName(fullName),
            ResourceAction.DELETE))
         {
            throw new SecurityException(
               "User=" + principal.getName() + ", Path=/api/data/databases/*, Rename=" + fullName);
         }

         if(!folderPermission && !newSourcePermission) {
            throw new SecurityException(
               "User=" + principal.getName() + ", Path=/api/data/databases/*, Rename in=root");
         }
      }
      else if(!newDataSource && !securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE,
         resourcePermissionService.getDataSourceResourceName(fullName), ResourceAction.WRITE))
      {
         throw new SecurityException(
            "User=" + principal.getName() + ", Path=/api/data/databases/*, Update=" + fullName);
      }

      jdbcDataSource.setName(newSrc.getFullName());
      jdbcDataSource.setDescription(newSrc.getDescription());
      jdbcDataSource.setDriver(newSrc.getDriver());
      jdbcDataSource.setURL(newSrc.getURL());
      jdbcDataSource.setCustomEditMode(newSrc.isCustomEditMode());
      jdbcDataSource.setCustomUrl(newSrc.getCustomUrl());
      jdbcDataSource.setRequireLogin(newSrc.isRequireLogin());
      jdbcDataSource.setCredential((PasswordCredential) Tool.clone(newSrc.getCredential()));
      jdbcDataSource.setDBType(newSrc.getDBType());
      jdbcDataSource.setCustom(newSrc.isCustom());
      jdbcDataSource.setUnasgn(newSrc.isUnasgn());
      jdbcDataSource.setPoolProperties(newSrc.getPoolProperties());
      jdbcDataSource.setDefaultDatabase(newSrc.getDefaultDatabase());
      jdbcDataSource.setTransactionIsolation(newSrc.getTransactionIsolation());
      jdbcDataSource.setTableNameOption(newSrc.getTableNameOption());
      jdbcDataSource.setAnsiJoin(newSrc.isAnsiJoin());

      boolean additionalChange = false;

      //Editing additional datasource connection. Delegate to its base.
      if(base != null) {
         if(name.equals(base.getName())) {
            throw new MessageException(Catalog.getCatalog(principal)
               .getString("common.datasource.nameInvalid", name));
         }

         base.removeDatasource(oname);
         jdbcDataSource.setName(name);
         base.addDatasource(jdbcDataSource);
         additionalChange = true;
      }
      else {
         dataSourceStatusService.updateStatus(jdbcDataSource);
         repository.updateDataSource(jdbcDataSource, fullName, false);

         // some kind private datasource of the current user
         if(newDataSource && !folderPermission && newSourcePermission ) {
            if(oldPermission == null) {
               oldPermission = new Permission();
            }

            IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
            String orgId = OrganizationManager.getInstance().getUserOrgId(principal);
            Set<Permission.PermissionIdentity> users = pId == null ? Collections.emptySet() :
               Collections.singleton(new Permission.PermissionIdentity(pId.name, orgId));
            oldPermission.setUserGrants(ResourceAction.READ, users);
            oldPermission.setUserGrants(ResourceAction.WRITE, users);
            oldPermission.setUserGrants(ResourceAction.DELETE, users);
            oldPermission.updateGrantAllByOrg(orgId, true);
         }

         securityEngine.setPermission(
            ResourceType.DATA_SOURCE, jdbcDataSource.getFullName(), oldPermission);
         entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, jdbcDataSource.getFullName(), null);
         entry = getDataSourceAssetEntry(entry);

         if(entry != null) {
            entry.setCreatedUsername(user != null ? user : entry.getCreatedUsername());
            entry.setCreatedDate(date != null ? date : entry.getCreatedDate());
            updateDataSourceAssetEntry(entry);
         }

         DatabaseDefinition[] additionalDataSources = getAdditionals != null
            ? getAdditionals.get()
            : null;

         if(additionalDataSources != null) {
            Map<String, String> additionalNamePasswordMap = new HashMap<>();

            // clear additional ds first
            for(String dataSourceName : jdbcDataSource.getDataSourceNames()) {
               JDBCDataSource source = jdbcDataSource.getDataSource(dataSourceName);
               String dsNameWithoutFolder = dataSourceName.contains("/") ?
                  dataSourceName.substring(dataSourceName.indexOf('/') + 1) : dataSourceName;
               additionalNamePasswordMap.put(dsNameWithoutFolder, source.getPassword());
               jdbcDataSource.removeDatasource(dataSourceName);
            }

            // add newly additional ds
            for(DatabaseDefinition ads : additionalDataSources) {
               String additionalName = ads.getName();
               String oldName = ads.getOldName();

               if(additionalNamePasswordMap.get(oldName) != null && ads.getAuthentication() != null) {
                  AuthenticationDetails authentication = ads.getAuthentication();

                  if((!Tool.isCloudSecrets() || !authentication.isUseCredentialId()) &&
                     Tool.equals(authentication.getPassword(), Util.PLACEHOLDER_PASSWORD))
                  {
                     authentication.setPassword(additionalNamePasswordMap.get(oldName));
                  }
               }

               addAdditionalConnection(jdbcDataSource, ads);

               if(!additionalChange) {
                  additionalChange = true;
               }

               if(oldName != null && !oldName.equals(additionalName)) {
                  renameAdditionalSource(jdbcDataSource, oldName, additionalName);
                  Permission permission = securityEngine.getPermission(ResourceType.DATA_SOURCE,
                     fullName + "::" + oldName);

                  if(permission != null) {
                     securityEngine.setPermission(ResourceType.DATA_SOURCE,
                        fullName + "::" + additionalName, permission);
                  }
               }
            }

            refreshAdditionalSource(jdbcDataSource);
         }
      }

      if(additionalChange) {
         jdbcDataSource.setLastModified(System.currentTimeMillis());
         repository.updateDataSource(jdbcDataSource, fullName, false);
      }

      String type = database.getType();

      if(type.equals(CustomDatabaseType.TYPE)) {
         CustomDatabaseType.CustomDatabaseInfo customInfo =
            (CustomDatabaseType.CustomDatabaseInfo) database.getInfo();
         String testQuery = customInfo.getTestQuery();
         saveTestQuery(fullName, newSrc.getFullName(), testQuery);
      }
      else if(type.equals(AccessDatabaseType.TYPE)) {
         AccessDatabaseType.AccessDatabaseInfo accessInfo =
               (AccessDatabaseType.AccessDatabaseInfo) database.getInfo();
         String testQuery = accessInfo.getTestQuery();
         saveTestQuery(fullName, newSrc.getFullName(), testQuery);
      }

      JDBCDataSource currentDataSource = (JDBCDataSource) Tool.clone(jdbcDataSource);
      transformTables(oldDataSource, currentDataSource);

      return null;
   }

   private void renameAdditionalSource(JDBCDataSource xds, String oname, String nname) {
      XDataModel model = DataSourceRegistry.getRegistry().getDataModel(xds.getFullName());
      String[] names = model.getPartitionNames();

      for(String name : names) {
         XPartition partition = model.getPartition(name);
         XPartition extend = partition.getPartition(oname);

         if(extend != null && extend.getConnection() != null) {
            if(partition.containPartition(nname)) {
               extend.setConnection(null);
            }
            else {
               extend.setConnection(nname);
               extend.setName(nname);
               partition.renamePartition(oname, extend);
            }
         }
      }

      for(String name : model.getLogicalModelNames()) {
         XLogicalModel xlm = model.getLogicalModel(name);
         XLogicalModel lm = xlm.getLogicalModel(oname);

         if(lm != null && Tool.equals(oname, lm.getName())) {
            lm.setName(nname);
            lm.setConnection(nname);
            xlm.renameLogicalModel(oname, lm);
         }
      }
   }

   private void refreshAdditionalSource(JDBCDataSource xds) {
      String[] connectNames = xds.getDataSourceNames();
      XDataModel model = DataSourceRegistry.getRegistry().getDataModel(xds.getFullName());
      String[] names = model.getPartitionNames();

      for(String name : names) {
         XPartition partition = model.getPartition(name);
         String[] extendTables = partition.getPartitionNames();

         for(int i = 0; i < extendTables.length; i++) {
            String tname = extendTables[i];
            XPartition extend = partition.getPartition(tname);
            String connect = extend.getConnection();

            if(extend != null && extend.getConnection() != null) {
               boolean found = false;

               for(int j = 0; j < connectNames.length; j++) {
                  if(Tool.equals(connect, connectNames[j])) {
                     found = true;
                  }
               }

               if(!found) {
                  extend.setConnection(null);
               }
            }
         }
      }
   }

   /**
    * Create a additional connection to base source.
    * @param base base database.
    * @param database additional connection define.
    * @throws FileExistsException
    */
   private void addAdditionalConnection(JDBCDataSource base, DatabaseDefinition database)
      throws FileExistsException
   {
      if(base == null || database == null) {
         return;
      }

      String[] additionalConnections = base.getDataSourceNames();

      if(additionalConnections != null) {
         for(String addCon : additionalConnections) {
            if(addCon != null && addCon.equals(database.getName())) {
               throw new FileExistsException(base.getName() + "/" + database.getName());
            }
         }
      }

      JDBCDataSource additionalConnection = getDatabase(base.getFullName(), database, false, true);
      additionalConnection.setBaseDatasource(base);
      base.addDatasource(additionalConnection);

      DatabaseInfo info = database.getInfo();

      if(info instanceof CustomDatabaseType.CustomDatabaseInfo) {
         String testQuery = ((CustomDatabaseType.CustomDatabaseInfo) info).getTestQuery();
         String fullName = additionalConnection.getFullName();

         try {
            saveTestQuery(fullName, fullName, testQuery);
         }
         catch(Exception e) {
            LOG.warn("Failed to save test query for {}", fullName);
         }
      }
   }

   /**
    * Check if the new datasource name is duplicated with other exist datasources.
    * @param actionName the action name, edit or create.
    * @param odsname    the old datasource name.
    * @param ndsname    the new datsource name.
    * @return  true if duplicated, else false.
    * @throws RemoteException
    */
   private boolean checkDuplicate(String actionName, String odsname, String ndsname)
      throws RemoteException
   {
      if(ActionRecord.ACTION_NAME_EDIT.equals(actionName) && Tool.equals(odsname, ndsname)) {
         return false;
      }

      XRepository repository = XFactory.getRepository();
      String[] existDataSourceNames = repository.getDataSourceNames();

      for(String exitName : existDataSourceNames) {
         if(Tool.equals(ndsname, exitName)) {
            return true;
         }
      }

      return false;
   }

   private void saveTestQuery(String oldSource, String newSource, String testQuery) throws Exception {
      SreeEnv.remove("inetsoft.uql.jdbc.pool." + oldSource + ".connectionTestQuery");

      if(testQuery != null && !testQuery.isEmpty()) {
         SreeEnv.setProperty("inetsoft.uql.jdbc.pool." + newSource +
            ".connectionTestQuery", testQuery);
      }

      SreeEnv.save();
   }

   public DataSourceSettingsModel getDefaultDatabase(Principal principal) {
      DatabaseInfo info = new CustomDatabaseType.CustomDatabaseInfo();
      info.setPoolProperties(new TreeMap<>());
      DatabaseDefinition defaultDef = new DatabaseDefinition();
      defaultDef.setName("");
      defaultDef.setType(CustomDatabaseType.TYPE);
      info.setCustomEditMode(true);
      AuthenticationDetails authentication = new AuthenticationDetails();
      authentication.setRequired(false);
      defaultDef.setAuthentication(authentication);
      defaultDef.setInfo(info);
      defaultDef.setNetwork(null);
      defaultDef.setDeletable(true);
      defaultDef.setUnasgn(false);

      return DataSourceSettingsModel.builder()
         .dataSource(defaultDef)
         .permissions(getDefaultResourcePermissionModel(principal))
         .uploadEnabled(isUploadEnabled(principal))
         .build();
   }

   private ResourcePermissionModel getDefaultResourcePermissionModel(Principal principal) {
      return ResourcePermissionModel.builder()
         .displayActions(ResourcePermissionService.ADMIN_ACTIONS)
         .hasOrgEdited(true)
         .securityEnabled(securityEngine.isSecurityEnabled())
         .derivePermissionLabel(Catalog.getCatalog().getString("Use Parent Permissions"))
         .permissions(getDefaultPermissions(principal))
         .grantReadToAllVisible(false)
         .requiresBoth(Boolean.parseBoolean(SreeEnv.getProperty("permission.andCondition", false, true)))
         .build();
   }

   public boolean isUploadEnabled(Principal principal) {
      try {
         boolean cluster = "server_cluster".equals(SreeEnv.getProperty("server.type")) ||
            ScheduleClient.getScheduleClient().isCluster();
         return !cluster && securityEngine.checkPermission(
            principal, ResourceType.UPLOAD_DRIVERS, "*", ResourceAction.ACCESS);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check permission", e);
         return false;
      }
   }

   public DataSourceSettingsModel getDatabaseFromListing(String listingName,
                                                         Principal principal) throws Exception
   {
      DataSourceListing listing = DataSourceListingService.getDataSourceListing(listingName);
      XDataSource dataSource = null;

      if(listing != null) {
         dataSource = listing.createDataSource();
      }

      return DataSourceSettingsModel.builder()
         .dataSource(getDatabaseDefinition(dataSource, principal))
         .permissions(getDefaultResourcePermissionModel(principal))
         .uploadEnabled(isUploadEnabled(principal))
         .build();
   }

   private List<ResourcePermissionTableModel> getDefaultPermissions(Principal principal) {
      List<ResourcePermissionTableModel> permissions = new ArrayList<>();
      permissions.add(
         ResourcePermissionTableModel.builder()
                                     .identityID(IdentityID.getIdentityIDFromKey(principal.getName()))
                                     .type(Identity.Type.USER)
                                     .actions(ResourcePermissionService.ADMIN_ACTIONS)
                                     .build());
      return permissions;
   }

   /**
    * get the audit path of datasource.
    */
   public String getDataSourceAuditPath(String path, DatabaseDefinition database,
                                        Principal principal)
      throws Exception
   {
      if(database == null) {
         return Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE, path, principal);
      }

      String fullName = getDataSourceFullName(path, database);

      return Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE, fullName, principal);
   }

   public String getDataSourceFullName(String path, DatabaseDefinition database) throws Exception {
      boolean exists = dataSourceExists(path, database.getName());
      String fullName = path;

      // create new dataSource, the path is the parent path.
      if(!exists) {
         fullName = fullName.startsWith("/") ? fullName.substring(1) : fullName;

         if(fullName.isEmpty()) {
            fullName += database.getName();
         }
         else {
            fullName += fullName.endsWith("/") ? database.getName() : "/" + database.getName();
         }
      }

      return fullName;
   }

   public String getActionName(String path, String oldPath) throws Exception {
      return getActionName(dataSourceExists(path, oldPath));
   }

   public String getActionName(boolean exists) {
      return exists ? ActionRecord.ACTION_NAME_EDIT : ActionRecord.ACTION_NAME_CREATE;
   }

   public boolean dataSourceExists(String path, String oldPath) throws Exception {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      XDataSource dataSource = repository.getDataSource(path);
      XDataSource renamed = null;

      if(dataSource != null) {
         int index = path.lastIndexOf("/");
         String newPath = index == -1 ? oldPath : path.substring(0, index) + "/" + oldPath;
         renamed = registry.getDataSource(newPath);
      }

      return dataSource != null || renamed != null;
   }

   public DeleteDatasourceInfo additionalDeletable(String path, DeleteDatasourceInfo deleteInfo)
      throws Exception
   {
      List<String> datasources = deleteInfo.getDatasources();
      DeleteDatasourceInfo response = new DeleteDatasourceInfo();

      for(String additional : datasources) {
         if(additionalDeletable(path, additional)) {
            response.addDatasource(additional);
         }
      }

      return response;
   }

   public boolean additionalDeletable(String path, String additional) throws Exception {
      XDataSource dataSource = repository.getDataSource(path);

      if(dataSource != null && dataSource instanceof JDBCDataSource) {
         JDBCDataSource xds = (JDBCDataSource) dataSource;
         JDBCDataSource ds = xds.getDataSource(additional);

         if(ds == null) {
            return false;
         }

         XDataModel model = DataSourceRegistry.getRegistry().getDataModel(xds.getFullName());

         if(model != null) {
            String[] names = model.getPartitionNames();

            for(String name : names) {
               XPartition par = model.getPartition(name);
               XPartition extend = par.getPartition(ds.getFullName());

               if(extend != null && extend.getConnection() != null) {
                  return true;
               }
            }

            for(String name : model.getLogicalModelNames()) {
               XLogicalModel lm = model.getLogicalModel(name);
               XLogicalModel extend = lm.getLogicalModel(ds.getFullName());

               if(extend != null && extend.getConnection() != null) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check the additional source permission.
    * @param base base source path.
    * @param additional additional source name.
    * @param principal user
    * @param action permission action.
    * @return
    * @throws SecurityException
    */
   public boolean checkAdditionalPermission(String base, String additional,
                                            ResourceAction action,
                                            Principal principal)
      throws SecurityException
   {
      if(StringUtils.isEmpty(base) || StringUtils.isEmpty(additional)) {
         return false;
      }

      String resource = base + "::" + additional;

      return securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, resource, action);
   }

   private JDBCDataSource createNewDatabase(String targetPath, Principal principal) throws Exception {
      int index = targetPath.lastIndexOf('/');
      boolean permitted = false;

      if(index < 0) {
         permitted = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE);

         if(!permitted) {
            permitted = securityEngine.checkPermission(principal,
               ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         }
      }
      else {
         String parent = targetPath.substring(0, index);
         permitted = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, parent, ResourceAction.WRITE);
      }

      if(!permitted) {
         throw new inetsoft.sree.security.SecurityException(
            "User=" + principal.getName() + ", Path=/api/data/databases, Add");
      }

      JDBCDataSource database = new JDBCDataSource();
      database.setURL("");
      database.setDriver("");
      database.setName(targetPath);
      database.setRequireLogin(false);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      database.setCreatedBy(pId.getName());
      database.setLastModifiedBy(pId.getName());
      database.setCreated(System.currentTimeMillis());
      database.setLastModified(System.currentTimeMillis());

      repository.updateDataSource(database, null, false);
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, targetPath, null);
      entry = getDataSourceAssetEntry(entry);
      entry.setCreatedUsername(IdentityID.getIdentityIDFromKey(principal.getName()).name);
      updateDataSourceAssetEntry(entry);

      return database;
   }

   public String buildDatabaseCustomUrl(String path, DatabaseDefinition model) {
      JDBCDataSource jdbcDataSource = getDatabase(path, model, true, false);
      DatabaseDefinition databaseDefinition = JDBCUtil.buildDatabaseDefinition(jdbcDataSource);

      return JDBCUtil.formatUrl(databaseDefinition);
   }

   public DatabaseDefinition buildDatabaseDefinition(DatabaseDefinition definition) {
      String type = definition.getType();
      DatabaseType databaseType = databaseTypeService.getDatabaseType(type);

      if(databaseType != null) {
         DatabaseInfo databaseInfo = databaseType.createDatabaseInfo();
         databaseInfo.setPoolProperties(new TreeMap<>());
         NetworkLocation location = databaseType.parse(databaseType.getDriverClass(definition.getInfo()),
                 definition.getInfo().getCustomUrl() == null ? "" : definition.getInfo().getCustomUrl(), databaseInfo);
         definition.setNetwork(location);
         definition.setInfo(databaseInfo);
      }

      return definition;
   }

   /**
    * Create a new database connection.
    *
    * @param definition new database definition.
    *
    * @return jdbc data source object for the new connection
    */
   private JDBCDataSource getDatabase(String path, DatabaseDefinition definition) {
      return getDatabase(path, definition, false, false);
   }

   /**
    * Create a new database connection.
    *
    * @param definition new database definition.
    *
    * @return jdbc data source object for the new connection
    */
   private JDBCDataSource getDatabase(String path, DatabaseDefinition definition,
                                      boolean buildCustomUrl, boolean isAdditionalSource)
   {
      String name = path.substring(0, path.lastIndexOf('/') + 1) + definition.getName();
      String type = definition.getType();
      DatabaseType databaseType = databaseTypeService.getDatabaseType(type);

      JDBCDataSource xds = new JDBCDataSource();
      xds.setName(isAdditionalSource ? definition.getName() : name);
      xds.setDescription(definition.getDescription());
      xds.setDriver(databaseType.getDriverClass(definition.getInfo()));
      xds.setRequireLogin(definition.getAuthentication().isRequired());
      xds.setPoolProperties(definition.getInfo().getPoolProperties());
      xds.setTableNameOption(definition.getTableNameOption());
      xds.setDefaultDatabase(definition.isChangeDefaultDB() ? definition.getDefaultDatabase() : null);
      xds.setAnsiJoin(definition.isAnsiJoin());
      xds.setTransactionIsolation(definition.getTransactionIsolation());
      xds.setCustomEditMode(definition.getInfo().isCustomEditMode());
      xds.setCustom(type.equals(CustomDatabaseType.TYPE));
      String url = databaseType.formatUrl(definition.getNetwork(), definition.getInfo());

      if(buildCustomUrl && definition.getInfo().isCustomEditMode()) {
         xds.setCustomUrl(url);
         xds.setURL(url);
      }
      else if(definition.getInfo().isCustomEditMode()) {
         xds.setCustomUrl(definition.getInfo().getCustomUrl());
         xds.setURL(definition.getInfo().getCustomUrl());
      }
      else {
         xds.setCustomUrl(null);
         xds.setURL(url);
      }

      if(definition.getAuthentication().isRequired()) {
         if(!Tool.isCloudSecrets() || !definition.getAuthentication().isUseCredentialId()) {
            xds.initCredential(true);
            xds.setUser(definition.getAuthentication().getUserName());
            String oldName = definition.getOldName();
            String password = definition.getAuthentication().getPassword();

            if(!Tool.isEmptyString(oldName) && Tool.equals(password, Util.PLACEHOLDER_PASSWORD)) {
               try {
                  path = Tool.isEmptyString(path) ? oldName : path;
                  XDataSource dataSource = repository.getDataSource(!isAdditionalSource ? path :
                     path + "/" + oldName);

                  if(dataSource instanceof JDBCDataSource) {
                     password = ((JDBCDataSource) dataSource).getPassword();
                  }
               }
               catch(Exception ignore){
               }
            }

            if(!Tool.equals(password, Util.PLACEHOLDER_PASSWORD)) {
               xds.setPassword(password);
            }
         }
         else {
            String credentialId = definition.getAuthentication().getCredentialId();
            String dbType = SQLHelper.getProductName(xds);
            Credential credential =
               Tool.decryptPasswordToCredential(credentialId, xds.getCredential().getClass(), dbType);

            if(credential instanceof PasswordCredential && !credential.isEmpty()) {
               xds.setUser(((PasswordCredential) credential).getUser());
               xds.setPassword(((PasswordCredential) credential).getPassword());
            }

            xds.setCredentialId(credentialId);
            xds.setDBType(dbType);
         }
      }

      xds.setUnasgn(definition.isUnasgn());

      return xds;
   }

   /**
    * Retrieves AssetEntry of a data source from the registry.
    *
    * @param oldEntry the asset entry we are trying to find.
    *
    * @return the asset entry from the repository.
    *
    */
   private AssetEntry getDataSourceAssetEntry(AssetEntry oldEntry) {
      AssetEntry[] entries = DataSourceRegistry.getRegistry()
         .getEntries(oldEntry.getPath(), AssetEntry.Type.DATA_SOURCE);

      for(AssetEntry newEntry : entries) {
         if(newEntry.toIdentifier().equals(oldEntry.toIdentifier())) {
            return newEntry;
         }
      }

      return null;
   }

   /**
    * Updates the created time/date of a data source entry.
    *
    * @param entry the updated asset entry
    */
   private void updateDataSourceAssetEntry(AssetEntry entry) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      try {
         registry.setObject(entry, registry.getObject(entry, true));
      }
      catch(Exception e) {
         LOG.debug("Failed to keep created time/date of updated data source: {}", entry.getName());
      }
   }

   /**
    * Do transform when table option changed, and the following detail jobs will be done:
    *
    *  1. transform parition tables.
    *  2. transform logical models which depends on the partition.
    *  3. transform dependencies depends on the logical model.
    *  4. transform the dependencies keys for physical tables(asset use physical table as direct source).
    *  5. transform vpm.
    *  6. transform query and transform dependencies depends on the query.
    */
   private void transformTables(JDBCDataSource odx, JDBCDataSource ndx) {
      boolean changeTableOption = odx.getTableNameOption() != ndx.getTableNameOption();
      RenameDependencyInfo dinfo = new RenameDependencyInfo();

      if(changeTableOption) {
         AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_SOURCE,
            ndx.getFullName(), null);

         List<AssetObject> list = DependencyTransformer.getDependencies(entry.toIdentifier());
         ChangeTableOptionInfo rinfo = new ChangeTableOptionInfo(ndx.getFullName(),
            odx.getTableNameOption(), ndx.getTableNameOption());

         if (list != null && list.size() != 0) {
            list.stream().forEach(r -> {
               if (r instanceof AssetEntry && ((AssetEntry) r).isPhysicalTable()) {
                  dinfo.addRenameInfo(r, rinfo);
               }
            });
         }

         // add transform task for all partitions.
         AssetEntry pentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.PHYSICAL,
            "ALL_PARTITIONS", null);
         dinfo.addRenameInfo(pentry, rinfo);

         // add transform task for vpm
         AssetEntry vpmEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VPM,
            "ALL_VPMS", null);
         dinfo.addRenameInfo(vpmEntry, rinfo);
         // add transform task for query
         AssetEntry queryEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.QUERY,
            "ALL_Querys", null);
         dinfo.addRenameInfo(queryEntry, rinfo);
         // add transform task for physical table
         AssetEntry phyTableEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
            "ALL_PhysicalTables", null);
         dinfo.addRenameInfo(phyTableEntry, rinfo);
      }


      if(changeTableOption) {
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
      }
   }

   /**
    * Iterate through Additional Connections and propagate name change to Principals
    *
    * @param model DataSourceSettingsModel containing changed information
    */
   public void updateAdditionalConnectionsPrincipalProperties(DataSourceSettingsModel model) {
      for(DatabaseDefinition addConnModel : model.additionalDataSources()) {
         String oname = addConnModel.getOldName();
         String name = addConnModel.getName();

         if(oname != null && name != null && !Tool.equals(oname, name)) {
            for(SRPrincipal p : sessionRepository.getActiveSessions()) {
               //iterate through property names to properly update connection change
               for(String propName : p.getPropertyNames()) {
                  if(propName.contains(":"+oname)) {
                     p.setProperty(propName.replace(":"+oname, ":"+name), p.getProperty(propName));
                     p.setProperty(propName, null);
                  }
               }
            }
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseDatasourcesService.class);
   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DatabaseTypeService databaseTypeService;
   private final DatabaseSettingsService databaseSettingsService;
   private final ResourcePermissionService resourcePermissionService;
   private final DataSourceStatusService dataSourceStatusService;
   private final IgniteSessionRepository sessionRepository;
}
