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
import inetsoft.uql.util.XUtil;
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
import inetsoft.web.portal.data.SecretIdAuthorizer;
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
import java.util.function.Predicate;
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
                                     IgniteSessionRepository sessionRepository,
                                     DataSourceRegistry dataSourceRegistry,
                                     RenameTransformHandler renameTransformHandler)
   {
      this.databaseTypeService = databaseTypeService;
      this.securityEngine = securityEngine;
      this.databaseSettingsService = databaseSettingsService;
      this.repository = repository;
      this.resourcePermissionService = resourcePermissionService;
      this.dataSourceStatusService = dataSourceStatusService;
      this.sessionRepository = sessionRepository;
      this.dataSourceRegistry = dataSourceRegistry;
      this.renameTransformHandler = renameTransformHandler;
      this.secretIdAuthorizer = new SecretIdAuthorizer(securityEngine, dataSourceRegistry);
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
      // the path is supplied by the client, so only the secret ids the caller may use elsewhere
      // are resolved
      JDBCDataSource jdbcDataSource = getDatabase(path, model, false, isAdditionalSource,
         secretIdAuthorizer.createCheck(null, principal), principal);
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
      DataSourceRegistry registry = dataSourceRegistry;
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
            // a name with a slash could put the folder into itself, with no parent left
            if(DataSourceRegistry.isSameOrDescendantPath(path, newPath)) {
               throw new MessageException(Catalog.getCatalog(principal).getString(
                  "common.datasource.moveIntoItself", path));
            }

            // renaming onto a path used by a data source or another folder would merge with it
            if(dataSourceRegistry.isDataSourcePathInUse(newPath)) {
               throw new MessageException(Catalog.getCatalog(principal).getString(
                  "common.datasource.moveTargetExists", newPath));
            }

            // a name with a slash could put the folder under a data source
            String dataSource = dataSourceRegistry.getDataSourceAncestor(newPath);

            if(dataSource != null) {
               throw new MessageException(Catalog.getCatalog(principal).getString(
                  "common.datasource.moveUnderDataSource", dataSource));
            }

            List<String> childrenSources = new ArrayList<>();
            DependencyTransformer.prepareChildrenSources(path, childrenSources, repository);
            RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
               path, newPath, childrenSources);
            folder.setName(newPath);

            Permission permission =
               securityEngine.getPermission(ResourceType.DATA_SOURCE_FOLDER, path);
            // Bug #77704, added once the folder is moved. A failed move renames the dependencies
            // of each data source it moved, in updateDataSourceFolder.
            repository.updateDataSourceFolder(folder, path);
            renameTransformHandler.addTransformTask(dinfo);

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
            resourcePermissionService.getDataSourceResourceName(path, dataSourceRegistry), ResourceAction.DELETE));
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

      final DataSourceRegistry registry = dataSourceRegistry;
      String name = database.getName().trim();
      String fullName = path;
      String oname = fullName;

      if(ActionRecord.ACTION_NAME_EDIT.equals(actionName)) {
         int idx = path.lastIndexOf("/");
         oname = idx == -1 ? path : path.substring(idx + 1);
      }

      XDataSource dataSource = getAdditionalConnection(fullName, repository.getDataSource(fullName));
      boolean newDataSource = false;
      Predicate<String> secretIdCheck = secretIdAuthorizer.createCheck(dataSource, principal);
      checkSecretIds(database, getAdditionals.get(), secretIdCheck);

      if(checkDuplicate(actionName, oname, name)) {
         return new ConnectionStatus("Duplicate");
      }

      if(getAdditionals.get() != null) {
         //also check additional connection datasources for duplicates
         String[] additionalConnectionsNames = Arrays.stream(getAdditionals.get())
            .map(DatabaseDefinition::getName)
            .toArray(String[]::new);

         if(checkAdditionalConnectionsDuplicate(oname, additionalConnectionsNames)) {
            return new ConnectionStatus("Duplicate");
         }
      }

      if(dataSource != null) {
         int index = fullName.lastIndexOf('/');
         String newPath = index == -1 ? name : fullName.substring(0, index) + "/" + name;

         // a data source at the new path is reported as "Duplicate" above, so only check for a
         // folder, which isn't filtered by permission (Bug #77691). newPath is the path of the
         // data source itself if it isn't renamed. A data source that shares its path with a
         // folder (older data) isn't saved either: a create in that folder is resolved to the
         // data source, and would rename it.
         if(isDataSourceFolder(newPath) || isDataSourceFolder(fullName)) {
            return new ConnectionStatus("Duplicate Folder");
         }

         // an additional connection is saved without additional connections of its own
         if(dataSource instanceof JDBCDataSource jdbc && jdbc.getBaseDatasource() == null &&
            isAdditionalConnectionPathFolder(newPath, getAdditionals.get()))
         {
            return new ConnectionStatus("Duplicate Folder");
         }
      }

      if(dataSource == null) {
         // Create the dataSource - path is the parent folder's path
         fullName = fullName.startsWith("/") ? fullName.substring(1) : fullName;

         if(!fullName.isEmpty() && !isDataSourceFolder(fullName)) {
            return new ConnectionStatus("Invalid Folder");
         }

         fullName += !fullName.isEmpty() ? "/" + name : name;

         // a data source can't be created under a data source, e.g. in a subfolder of a folder
         // that is also a data source (Bug #77691)
         if(registry.getDataSourceAncestor(fullName) != null) {
            return new ConnectionStatus("Invalid Folder");
         }

         // a data source or a folder at the path, not filtered by permission (Bug #77691)
         if(registry.isDataSourcePathInUse(fullName) ||
            isAdditionalConnectionPathFolder(fullName, getAdditionals.get()))
         {
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
      Permission oldPermission = securityEngine.getPermission(ResourceType.DATA_SOURCE, fullName);
      // an additional connection is named and its stored password is read the way the parent's
      // save does, through its parent
      JDBCDataSource newSrc = base != null ?
         getDatabase(base.getFullName(), database, false, true, secretIdCheck, principal) :
         getDatabase(fullName, database, false, false, secretIdCheck, principal);
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
            resourcePermissionService.getDataSourceResourceName(fullName, dataSourceRegistry),
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
         resourcePermissionService.getDataSourceResourceName(fullName, dataSourceRegistry), ResourceAction.WRITE))
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

         if(!oname.equals(name)) {
            renameAdditionalSource(base, oname, name);
            updateAdditionalPermissions(base.getFullName(), Collections.emptySet(),
                                        Collections.singletonMap(oname, name));
         }
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

            // the names of the kept and renamed additional connections before this save, and the
            // renames, by old name
            Set<String> keptOldNames = new HashSet<>();
            Map<String, String> renames = new LinkedHashMap<>();

            // add newly additional ds
            for(DatabaseDefinition ads : additionalDataSources) {
               String additionalName = ads.getName();
               String oldName = ads.getOldName();

               if(oldName != null) {
                  keptOldNames.add(oldName);
               }

               if(additionalNamePasswordMap.get(oldName) != null && ads.getAuthentication() != null) {
                  AuthenticationDetails authentication = ads.getAuthentication();

                  if((!Tool.isCloudSecrets() || !authentication.isUseCredentialId()) &&
                     Tool.equals(authentication.getPassword(), Util.PLACEHOLDER_PASSWORD))
                  {
                     authentication.setPassword(additionalNamePasswordMap.get(oldName));
                  }
               }

               addAdditionalConnection(jdbcDataSource, ads, secretIdCheck, principal);

               if(!additionalChange) {
                  additionalChange = true;
               }

               if(oldName != null && !oldName.equals(additionalName)) {
                  renameAdditionalSource(jdbcDataSource, oldName, additionalName);
                  renames.put(oldName, additionalName);
               }
            }

            // a new additional connection has no old name, so one that has the name of a removed
            // one is not kept. The parent was renamed above, which moved the permissions of its
            // additional connections, so they are under its new name
            Set<String> removedNames = new HashSet<>(additionalNamePasswordMap.keySet());
            removedNames.removeAll(keptOldNames);
            updateAdditionalPermissions(jdbcDataSource.getFullName(), removedNames, renames);
            refreshAdditionalSource(jdbcDataSource);
         }
      }

      if(additionalChange && base != null) {
         // base.addDatasource() saved the additional connection under its parent. Update the
         // parent as a save of the parent does, since updating the additional connection by its
         // path would rename it to its own name and so move it out of the parent
         XDataSource parent = repository.getDataSource(base.getFullName());

         if(parent != null) {
            parent.setLastModified(System.currentTimeMillis());
            repository.updateDataSource(parent, parent.getFullName(), false);
         }
      }
      else if(additionalChange) {
         jdbcDataSource.setLastModified(System.currentTimeMillis());
         repository.updateDataSource(jdbcDataSource, fullName, false);
      }

      String type = database.getType();

      if(type.equals(CustomDatabaseType.TYPE) || type.equals(AccessDatabaseType.TYPE)) {
         removeLegacyTestQuery(fullName, newSrc.getFullName());
      }

      JDBCDataSource currentDataSource = (JDBCDataSource) Tool.clone(jdbcDataSource);
      transformTables(oldDataSource, currentDataSource);

      return null;
   }

   /**
    * Gets an additional connection with its base data source set. The base data source is not
    * saved with an additional connection, so an additional connection read by its path, as the
    * repository tree's editor does, has none once the registry cache has been cleared.
    *
    * @param path the path of the data source, which is parent/name for an additional connection.
    * @param dataSource the data source read by that path.
    *
    * @return the additional connection read through its parent, or the data source read by the
    *         path if it isn't an additional connection.
    */
   private XDataSource getAdditionalConnection(String path, XDataSource dataSource)
      throws RemoteException
   {
      int index = path.lastIndexOf('/');

      // a data source in a folder is saved with its path as its name, an additional connection
      // with its name alone
      if(index < 0 || !(dataSource instanceof AdditionalConnectionDataSource<?> additional) ||
         additional.getBaseDatasource() != null || path.equals(dataSource.getFullName()))
      {
         return dataSource;
      }

      XDataSource parent = repository.getDataSource(path.substring(0, index));

      if(parent instanceof AdditionalConnectionDataSource<?> base &&
         base.getBaseDatasource() == null)
      {
         XDataSource result = base.getDataSource(path.substring(index + 1));

         if(result != null) {
            // the registry returns its cached instance, which this save changes
            return (XDataSource) result.clone();
         }
      }

      return dataSource;
   }

   /**
    * Updates the permissions of the additional connections of a data source after they are
    * removed or renamed. All old permissions are read before any is removed, so that swapped or
    * chained names keep their own permissions.
    *
    * @param parent       the full name of the data source.
    * @param removedNames the names of the removed additional connections.
    * @param renames      the new names of the renamed additional connections, by old name.
    */
   private void updateAdditionalPermissions(String parent, Set<String> removedNames,
                                            Map<String, String> renames)
   {
      if(removedNames.isEmpty() && renames.isEmpty() || !hasPermissionStore()) {
         return;
      }

      Map<String, Permission> permissions = new HashMap<>();

      for(String oldName : renames.keySet()) {
         permissions.put(oldName, securityEngine.getPermission(ResourceType.DATA_SOURCE,
            parent + XUtil.ADDITIONAL_DS_CONNECTOR + oldName));
      }

      Set<String> oldNames = new HashSet<>(removedNames);
      oldNames.addAll(renames.keySet());

      for(String oldName : oldNames) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE,
            parent + XUtil.ADDITIONAL_DS_CONNECTOR + oldName);
      }

      for(Map.Entry<String, String> rename : renames.entrySet()) {
         Permission permission = permissions.get(rename.getKey());

         if(permission != null) {
            securityEngine.setPermission(ResourceType.DATA_SOURCE,
               parent + XUtil.ADDITIONAL_DS_CONNECTOR + rename.getValue(), permission);
         }
      }
   }

   /**
    * Checks if the security provider stores permissions of its own, i.e. it is not virtual.
    */
   private boolean hasPermissionStore() {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      return provider == null || !provider.isVirtual();
   }

   private void renameAdditionalSource(JDBCDataSource xds, String oname, String nname) {
      XDataModel model = dataSourceRegistry.getDataModel(xds.getFullName());
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
      XDataModel model = dataSourceRegistry.getDataModel(xds.getFullName());
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
    * @param secretIdCheck checks the secret ids the caller may use.
    * @param principal the user saving the connection.
    * @throws FileExistsException
    */
   private void addAdditionalConnection(JDBCDataSource base, DatabaseDefinition database,
                                        Predicate<String> secretIdCheck, Principal principal)
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

      JDBCDataSource additionalConnection =
         getDatabase(base.getFullName(), database, false, true, secretIdCheck, principal);
      additionalConnection.setBaseDatasource(base);
      base.addDatasource(additionalConnection);

      DatabaseInfo info = database.getInfo();

      if(info instanceof CustomDatabaseType.CustomDatabaseInfo) {
         String fullName = additionalConnection.getFullName();

         try {
            removeLegacyTestQuery(fullName, fullName);
         }
         catch(Exception e) {
            LOG.warn("Failed to remove the legacy test query of {}", fullName);
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

      String[] existDataSourceNames = repository.getDataSourceNames();

      for(String exitName : existDataSourceNames) {
         if(Tool.equals(ndsname, exitName)) {
            return true;
         }
      }

      //also check against additional connection datasources to prevent duplicates internally
      for(String exitName : repository.getDataSourceFullNames()) {
         XDataSource ds = repository.getDataSource(exitName);

         if(ds instanceof AdditionalConnectionDataSource ads) {

            for(String additionalDS: ads.getDataSourceNames()) {
               if(Tool.equals(ndsname, additionalDS)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if any additional connections are duplicated with other existing datasources.
    * @param baseDataSource   the name of the parent datasource holding additional connections
    * @param additionalConnections  array of additional connections in updating datasource to check
    * @return  true if duplicated, else false.
    * @throws RemoteException
    */
   private boolean checkAdditionalConnectionsDuplicate(String baseDataSource, String[] additionalConnections)
      throws RemoteException
   {
      String[] existDataSourceNames = repository.getDataSourceNames();
      String[] existDataSourceFullNames = repository.getDataSourceFullNames();

      for(String additionalDS : additionalConnections) {

         for(String exitName : existDataSourceNames) {
            if(Tool.equals(additionalDS, exitName)) {
               return true;
            }
         }

         //also check against other additional connection datasources to prevent duplicates internally
         for(String exitName : existDataSourceFullNames) {
            XDataSource ds = repository.getDataSource(exitName);

            if(ds instanceof AdditionalConnectionDataSource ads && !Tool.equals(ds.getName(), baseDataSource)) {

               for(String dsAdditionalConn: ads.getDataSourceNames()) {
                  if(Tool.equals(dsAdditionalConn, additionalDS)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   /**
    * Checks if a data source folder exists at a path. Unlike
    * {@link DataSourceRegistry#getDataSourceFolder(String)}, the check isn't filtered by
    * permission.
    */
   private boolean isDataSourceFolder(String path) {
      return dataSourceRegistry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null));
   }

   /**
    * Checks if a data source folder exists at the path of one of the additional connections of a
    * data source (Bug #77691). An additional connection is stored at the path of its data source
    * followed by its name.
    *
    * @param parentPath  the path of the data source after it is saved.
    * @param additionals the additional connections of the data source.
    */
   private boolean isAdditionalConnectionPathFolder(String parentPath,
                                                    DatabaseDefinition[] additionals)
   {
      if(additionals != null) {
         for(DatabaseDefinition additional : additionals) {
            if(additional != null && additional.getName() != null &&
               isDataSourceFolder(parentPath + "/" + additional.getName()))
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Removes the test query that an older version saved in SreeEnv. The test query is now saved
    * in the pool properties of the data source by {@link #getPoolProperties}, and the editor
    * shows the SreeEnv value only until the data source is saved.
    */
   private void removeLegacyTestQuery(String oldSource, String newSource) throws Exception {
      JDBCUtil.removeConnectionTestQuery(oldSource);

      // on a rename the registry has already moved the old value to the new name, and the
      // value is now in the pool properties of the data source
      if(!Tool.equals(oldSource, newSource)) {
         JDBCUtil.removeConnectionTestQuery(newSource);
      }

      SreeEnv.save();
   }

   /**
    * Gets the pool properties of a database definition. The test query of a custom or Access
    * database is saved in the connectionTestQuery pool property, which the connection pool
    * uses. An empty test query leaves the pool properties as they are, so that the default
    * test query shown in the editor is never saved.
    */
   private static TreeMap<String, String> getPoolProperties(DatabaseInfo info) {
      String testQuery = null;

      if(info instanceof CustomDatabaseType.CustomDatabaseInfo customInfo) {
         testQuery = customInfo.getTestQuery();
      }
      else if(info instanceof AccessDatabaseType.AccessDatabaseInfo accessInfo) {
         testQuery = accessInfo.getTestQuery();
      }

      if(testQuery == null || testQuery.trim().isEmpty()) {
         return info.getPoolProperties();
      }

      TreeMap<String, String> poolProperties = info.getPoolProperties() == null ?
         new TreeMap<>() : new TreeMap<>(info.getPoolProperties());
      poolProperties.put(JDBCUtil.CONNECTION_TEST_QUERY_PROPERTY, testQuery);
      return poolProperties;
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
         return securityEngine.checkPermission(
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
      final DataSourceRegistry registry = dataSourceRegistry;
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

         XDataModel model = dataSourceRegistry.getDataModel(xds.getFullName());

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
      JDBCDataSource jdbcDataSource = getDatabase(path, model, true, false, null, null);
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
    * Checks the secret ids that a database definition and its additional connections reference
    * before anything is saved.
    */
   private static void checkSecretIds(DatabaseDefinition database, DatabaseDefinition[] additionals,
                                      Predicate<String> secretIdCheck)
   {
      SecretIdAuthorizer.checkSecretId(getCloudSecretId(database), secretIdCheck);

      if(additionals != null) {
         for(DatabaseDefinition additional : additionals) {
            SecretIdAuthorizer.checkSecretId(getCloudSecretId(additional), secretIdCheck);
         }
      }
   }

   /**
    * Gets the secret id that {@link #getDatabase} resolves for a database definition.
    */
   private static String getCloudSecretId(DatabaseDefinition definition) {
      AuthenticationDetails authentication = definition == null ? null :
         definition.getAuthentication();

      if(authentication != null && authentication.isRequired() && Tool.isCloudSecrets() &&
         authentication.isUseCredentialId())
      {
         return authentication.getCredentialId();
      }

      return null;
   }

   /**
    * Create a new database connection.
    *
    * @param definition new database definition.
    * @param secretIdCheck checks the secret ids the caller may use. It may be {@code null} when
    *                      only building the URL, in which case the secret id is not resolved.
    * @param principal the user the password is resolved for. It may be {@code null} when only
    *                  building the URL, in which case a stored password is not looked up.
    *
    * @return jdbc data source object for the new connection
    */
   private JDBCDataSource getDatabase(String path, DatabaseDefinition definition,
                                      boolean buildCustomUrl, boolean isAdditionalSource,
                                      Predicate<String> secretIdCheck, Principal principal)
   {
      String name = path.substring(0, path.lastIndexOf('/') + 1) + definition.getName();
      String type = definition.getType();
      DatabaseType databaseType = databaseTypeService.getDatabaseType(type);

      JDBCDataSource xds = new JDBCDataSource();
      xds.setName(isAdditionalSource ? definition.getName() : name);
      xds.setDescription(definition.getDescription());
      xds.setDriver(databaseType.getDriverClass(definition.getInfo()));
      xds.setRequireLogin(definition.getAuthentication().isRequired());
      xds.setPoolProperties(getPoolProperties(definition.getInfo()));
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

            if(!Tool.isEmptyString(oldName) && Tool.equals(password, Util.PLACEHOLDER_PASSWORD) &&
               principal != null)
            {
               password = getStoredPassword(path, oldName, isAdditionalSource, principal);
            }

            if(!Tool.equals(password, Util.PLACEHOLDER_PASSWORD)) {
               xds.setPassword(password);
            }
         }
         else {
            String credentialId = definition.getAuthentication().getCredentialId();
            String dbType = SQLHelper.getProductName(xds);

            if(secretIdCheck != null) {
               SecretIdAuthorizer.checkSecretId(credentialId, secretIdCheck);
               Credential credential = Tool.decryptPasswordToCredential(
                  credentialId, xds.getCredential().getClass(), dbType);

               if(credential instanceof PasswordCredential && !credential.isEmpty()) {
                  xds.setUser(((PasswordCredential) credential).getUser());
                  xds.setPassword(((PasswordCredential) credential).getPassword());
               }
            }

            xds.setCredentialId(credentialId);
            xds.setDBType(dbType);
         }
      }

      xds.setUnasgn(definition.isUnasgn());

      return xds;
   }

   /**
    * Gets the stored password that the placeholder password in the editor stands for. It is only
    * returned to a user who can edit the data source it is read from, because the caller chooses
    * the URL that it is sent to.
    *
    * @param path the path of the data source, or of the parent of an additional connection.
    * @param oldName the name of the data source or additional connection.
    *
    * @return the stored password, or the placeholder password if there is none.
    *
    * @throws java.lang.SecurityException if the user can't edit the data source.
    */
   private String getStoredPassword(String path, String oldName, boolean isAdditionalSource,
                                    Principal principal)
   {
      path = Tool.isEmptyString(path) ? oldName : path;
      JDBCDataSource parent = null;
      JDBCDataSource stored = null;

      try {
         XDataSource dataSource = repository.getDataSource(path);

         if(dataSource instanceof JDBCDataSource jdbcDataSource) {
            if(!isAdditionalSource) {
               stored = jdbcDataSource;
            }
            // an additional connection is read from its parent, so that path can't name a folder
            // that holds a data source called oldName
            else if(jdbcDataSource.getBaseDatasource() == null) {
               parent = jdbcDataSource;
               stored = parent.getDataSource(oldName);
            }
         }
      }
      catch(Exception ignore) {
      }

      if(isAdditionalSource && parent == null) {
         throw new java.lang.SecurityException(
            "User=" + principal.getName() + ", Path=/api/data/databases/*, Password=" + path);
      }

      String password = stored == null ? null : stored.getPassword();

      if(Tool.isEmptyString(password)) {
         return Util.PLACEHOLDER_PASSWORD;
      }

      boolean writable;

      try {
         writable = securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE,
            resourcePermissionService.getDataSourceResourceName(path, dataSourceRegistry),
            ResourceAction.WRITE);
      }
      catch(SecurityException e) {
         writable = false;
      }

      if(!writable) {
         throw new java.lang.SecurityException(
            "User=" + principal.getName() + ", Path=/api/data/databases/*, Password=" + path);
      }

      return password;
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
      AssetEntry[] entries = dataSourceRegistry
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
      final DataSourceRegistry registry = dataSourceRegistry;

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
         renameTransformHandler.addTransformTask(dinfo);
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
   private final RenameTransformHandler renameTransformHandler;
   private final DatabaseSettingsService databaseSettingsService;
   private final ResourcePermissionService resourcePermissionService;
   private final DataSourceStatusService dataSourceStatusService;
   private final IgniteSessionRepository sessionRepository;
   private final DataSourceRegistry dataSourceRegistry;
   private final SecretIdAuthorizer secretIdAuthorizer;
}
