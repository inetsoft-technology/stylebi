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
package inetsoft.web.admin.datasource;

import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.DataSourceRenameException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.InvalidResourceException;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import inetsoft.web.portal.data.SecretIdAuthorizer;
import inetsoft.web.security.auth.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code DataSourceService} implements the core data-source management logic shared by the
 * community admin-ai plugin and (via a thin enterprise wrapper) the enterprise Public REST API.
 * Community has no organization-switching concept, so every method here operates on the caller's
 * current organization -- there is no {@code organizationid} parameter.
 */
@Service("adminDataSourceService")
public class DataSourceService {
   @Autowired
   public DataSourceService(XRepository repository, SecurityEngine securityEngine,
                            DatabaseTypeService databaseTypeService,
                            MD5IdentifierGenerator ids, DataSourceRegistry dataSourceRegistry)
   {
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.databaseTypeService = databaseTypeService;
      this.ids = ids;
      this.dataSourceRegistry = dataSourceRegistry;
      this.secretIdAuthorizer = new SecretIdAuthorizer(securityEngine, dataSourceRegistry);
   }

   /**
    * Gets the list of data sources.
    *
    * @param user a principal that identifies the remote user.
    *
    * @return the list of data sources.
    */
   public DataSourceList getDataSources(Principal user) throws Exception {
      return getDataSources(null, user);
   }

   /**
    * Gets the list of data sources.
    *
    * @param name the data source name filter.
    * @param user a principal that identifies the remote user.
    *
    * @return the list of data sources.
    */
   public DataSourceList getDataSources(String name, Principal user) throws Exception {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      DataSourceList list = new DataSourceList();
      list.setDataSources(Arrays.stream(repository.getDataSourceFullNames(new IdentityID(orgId, orgId)))
                             .filter(d -> name == null || name.equals(d))
                             .filter(d -> checkPermission(d, ResourceAction.READ, user))
                             .map(d -> getDescription(d, orgId))
                             .collect(Collectors.toList()));
      return list;
   }

   /**
    * Gets the full name of a data source.
    *
    * @param id   the unique identifier of the data source.
    * @param user a principal that identifies the remote user.
    *
    * @return the full name of the data source.
    */
   private String getDataSourceFullName(String id, Principal user) throws Exception {
      XDataSource ds = getXDataSource(id, user);
      return ds == null ? null : ds.getFullName();
   }

   /**
    * Gets the properties of a data source.
    *
    * @param id   the unique identifier of the data source.
    * @param user a principal that identifies the remote user.
    *
    * @return the data source properties.
    */
   public DataSourceProperties getDataSource(String id, Principal user) throws Exception {
      XDataSource ds = getXDataSource(id, user);

      switch(ds.getType()) {
      case XDataSource.JDBC:
         return new JdbcDataSourceProperties((JDBCDataSource) ds, id);
      default:
         return new TabularDataSourceProperties(ds, id);
      }
   }

   /**
    * Updates the properties of a data source.
    *
    * @param id         the unique identifier of the data source.
    * @param properties the modified data source properties.
    * @param user       a principal that identifies the remote user.
    */
   public void updateDataSource(String id, DataSourceProperties properties, Principal user)
      throws Exception
   {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_EDIT,
         "", ActionRecord.OBJECT_TYPE_DATASOURCE, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
         "");

      try {
         // name the data source before anything that can throw, so a refused edit is recorded
         // with its name (Bug #77819)
         String name = ids.getName(id);
         record.setObjectName(name != null ? name : id);
         IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
         XDataSource ds = getXDataSource(id, user);
         String oldName = ds.getFullName();
         String newName = properties.getName();
         record.setObjectName(oldName);

         if(newName == null || newName.isEmpty()) {
            newName = oldName;
            properties.setName(newName);
         }

         if(!oldName.equals(newName)) {
            // Bug #77673, a data source moved onto the path of a data source or a data source
            // folder would overwrite it, or take the data sources of the folder as its
            // additional connections
            if(dataSourceRegistry.isDataSourcePathInUse(newName)) {
               throw new ResourceExistsException(newName);
            }

            // a parent folder can't be created at the path of a data source, the moved data
            // source would become an additional connection of that data source
            String dataSource = dataSourceRegistry.getDataSourceAncestor(newName);

            if(dataSource != null) {
               throw new ResourceExistsException(dataSource);
            }

            // Bug #77841, Bug #77725, Bug #77820, a rename that XEngine refuses, checked here
            // because the parent folders are created before XEngine is called
            dataSourceRegistry.checkDataSourceMovePathClash(oldName);
         }

         checkMovePermissions(user, oldName, newName);
         String password = null;

         if(XDataSource.JDBC.equals(ds.getType())) {
            checkSecretId((JDBCDataSource) ds, (JdbcDataSourceProperties) properties, user);
            password = resolvePassword((JDBCDataSource) ds, (JdbcDataSourceProperties) properties);
         }

         switch(ds.getType()) {
         case XDataSource.JDBC:
            updateDataSource((JDBCDataSource) ds, (JdbcDataSourceProperties) properties, password);
            break;
         default:
            updateDataSource(ds, (TabularDataSourceProperties) properties);
         }

         RenameDependencyInfo dinfo = null;

         // Bug #60289, rename transform task is submitted in updateDataSource() for REST
         if(!oldName.equals(newName) && !((ds instanceof ListedDataSource) ||
            ds.getType().startsWith(SourceInfo.REST_PREFIX)))
         {
            dinfo = DependencyTransformer.createDependencyInfo(oldName, ds.getFullName());
         }

         //Create parent datasource folders
         int idx = newName.indexOf('/');

         while (idx != -1) {
            ensureDataSourceFolder(newName.substring(0, idx), pId);
            idx = newName.indexOf('/', idx + 1);
         }

         // Bug #77841, the dependencies are renamed once the data source is moved, a refused
         // rename must not rewrite them (Bug #77704)
         try {
            repository.updateDataSource(ds, oldName, false);
         }
         catch(DataSourceRenameException e) {
            if(dinfo != null && e.isMoved(oldName)) {
               RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
            }

            throw e;
         }

         if(dinfo != null) {
            RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
         }

         ids.updateName(id, oldName, newName);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         record.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   private void checkMovePermissions(Principal user, String oldName, String newName) throws Exception {
      int idx = newName.indexOf('/');
      String parentNName = idx == -1 ? "/" : newName.substring(0, idx);
      idx = oldName.indexOf('/');
      String parentOName = idx == -1 ? "/" : oldName.substring(0, idx);

      if(parentOName.equals(parentNName)) {
         return;
      }

      if(!securityEngine.checkPermission(user, ResourceType.DATA_SOURCE_FOLDER,
                                     newName, ResourceAction.WRITE))
      {
         throw new UnauthorizedAccessException(newName);
      }

      if(!securityEngine.checkPermission(user, ResourceType.DATA_SOURCE,
                                     oldName, ResourceAction.DELETE))
      {
         throw new UnauthorizedAccessException(oldName);
      }
   }

   /**
    * Creates a data-source folder (and any missing ancestor folder in its path), with no other
    * mutation -- the standalone counterpart to the folder auto-creation {@link #updateDataSource}
    * already performs as a rename side-effect (bug 76599). Both paths share the same underlying
    * primitive, {@link #ensureDataSourceFolder}, so they cannot drift apart.
    *
    * @param folderPath the full folder path to create (e.g. {@code "A/B"} creates both "A" and
    *                    "A/B" if missing).
    * @param user        a principal that identifies the remote user.
    *
    * @throws UnauthorizedAccessException if the caller does not have WRITE permission on {@code
    *                                      folderPath}.
    */
   public void createDataSourceFolder(String folderPath, Principal user) throws Exception {
      if(!securityEngine.checkPermission(user, ResourceType.DATA_SOURCE_FOLDER,
                                     folderPath, ResourceAction.WRITE))
      {
         throw new UnauthorizedAccessException(folderPath);
      }

      IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
      int idx = folderPath.indexOf('/');

      while(idx != -1) {
         ensureDataSourceFolder(folderPath.substring(0, idx), pId);
         idx = folderPath.indexOf('/', idx + 1);
      }

      ensureDataSourceFolder(folderPath, pId);
   }

   /**
    * @param folderPath the full folder path to check.
    *
    * @return {@code true} if a data-source folder already exists at {@code folderPath}.
    */
   public boolean dataSourceFolderExists(String folderPath) throws Exception {
      return this.repository.getDataSourceFolder(folderPath) != null;
   }

   /**
    * Removes exactly the requested folder path -- the rollback primitive for {@link
    * #createDataSourceFolder} (bug 76599).
    *
    * @param folderPath the full folder path to remove.
    */
   public void removeDataSourceFolder(String folderPath) throws Exception {
      this.repository.removeDataSourceFolder(folderPath);
   }

   /** Creates {@code folderName} if it does not already exist -- the one primitive shared by
    * {@link #updateDataSource}'s rename-side-effect ancestor creation and {@link
    * #createDataSourceFolder}'s bare create, so the two paths cannot drift apart (bug 76599). */
   private void ensureDataSourceFolder(String folderName, IdentityID owner) throws Exception {
      if(this.repository.getDataSourceFolder(folderName) == null) {
         DataSourceFolder folder = new DataSourceFolder(
            folderName, LocalDateTime.now(), owner != null ? owner.getName() : null);
         this.repository.updateDataSourceFolder(folder, null);
      }
   }

   /**
    * Deletes a data source.
    *
    * @param id    the unique identifier of the report.
    * @param force {@code true} to delete the data source even if it is used by a query
    * @param user  a principal that identifies the remote user.
    */
   public void deleteDataSource(String id, boolean force, Principal user) throws Exception {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_DELETE,
         "", ActionRecord.OBJECT_TYPE_DATASOURCE, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
         "");

      try {
         // name the data source before anything that can throw, so a refused delete is recorded
         // with its name (Bug #77819)
         String name = ids.getName(id);
         record.setObjectName(name != null ? name : id);
         XDataSource ds = getXDataSource(id, ResourceAction.DELETE, user);
         String dxname = ds.getFullName();
         record.setObjectName(dxname);

         if(!force) {
            AssetEntry entry = new AssetEntry(
               AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, dxname, null);
            List<AssetObject> dependencies =
               getDeleteDependencies(dxname, repository.getDataModel(dxname));

            if(!dependencies.isEmpty()) {
               DependencyException depEx = new DependencyException(entry);
               depEx.addDependencies(dependencies.toArray(new Object[0]));
               throw depEx;
            }
         }

         // the registry removes the data source's own permission best-effort, so a data source
         // created later with the same name doesn't inherit it (Bug #77690, #78217)
         repository.removeDataSource(dxname, force);
         ids.removeName(dxname);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         record.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   /**
    * Gets the assets that block a {@code force=false} delete of a data source: the dependents of
    * the data source itself and of each of its logical models. A sheet bound to a logical model is
    * recorded only under that model's key, not the data source's (Bug #76338). The admin-chat
    * delete preflight uses this too, so the two refuse the same deletes.
    *
    * @param dxname    the full name of the data source.
    * @param dataModel the data source's data model, or {@code null} if it has none.
    *
    * @return the dependents, without duplicates; empty if there are none.
    */
   public static List<AssetObject> getDeleteDependencies(String dxname, XDataModel dataModel) {
      Set<AssetObject> dependencies = new LinkedHashSet<>();
      addDependencies(dependencies, dxname, AssetEntry.Type.DATA_SOURCE);

      if(dataModel != null) {
         // the same key the dependency writer builds for a sheet bound to the model
         for(String model : dataModel.getLogicalModelNames()) {
            addDependencies(dependencies, dxname + "/" + model, AssetEntry.Type.LOGIC_MODEL);
         }
      }

      return new ArrayList<>(dependencies);
   }

   private static void addDependencies(Set<AssetObject> dependencies, String path,
                                       AssetEntry.Type type)
   {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      List<AssetObject> found = DependencyTool.getDependencies(entry.toIdentifier());

      if(found != null) {
         dependencies.addAll(found);
      }
   }

   /**
    * Gets the data model of a data source.
    *
    * @param dxname the full name of the data source.
    *
    * @return the data model, or {@code null} if the data source has none.
    */
   public XDataModel getDataModel(String dxname) throws RemoteException {
      return repository.getDataModel(dxname);
   }

   /**
    * Gets the real, decrypted JDBC login password currently stored for a data source -- never the
    * masked literal {@link JdbcDataSourceProperties} returns from {@link #getDataSource}.
    *
    * @param id   the unique identifier of the data source.
    * @param user a principal that identifies the remote user.
    *
    * @return the real password, or {@code null} if the data source is not a JDBC data source, or
    *         does not require login.
    */
   public String getCurrentPassword(String id, Principal user) throws Exception {
      XDataSource ds = getXDataSource(id, user);

      if(!(ds instanceof JDBCDataSource) || !((JDBCDataSource) ds).isRequireLogin()) {
         return null;
      }

      return ((JDBCDataSource) ds).getPassword();
   }

   /**
    * Removes all opaque identifiers that have not been referenced in the last hour.
    */
   @Scheduled(fixedDelay = 300000L) // 15 minutes
   public void removeUnusedIdentifiers() {
      ids.cleanIds(3600000L);
   }

   private DataSourceDescription getDescription(String name, String orgID) {
      try {
         String id = ids.getId(name);
         return new DataSourceDescription(repository.getDataSource(name, orgID), id);
      }
      catch(RemoteException e) {
         throw new RuntimeException("Failed to get data source: " + name, e);
      }
   }

   private XDataSource getXDataSource(String id, Principal user) throws Exception {
      return getXDataSource(id, ResourceAction.WRITE, user);
   }

   private XDataSource getXDataSource(String id, ResourceAction action, Principal user)
      throws Exception
   {
      String name = ids.getName(id);

      if(name == null) {
         throw new MissingResourceException(id);
      }

      XDataSource ds = repository.getDataSource(name);

      if(ds == null) {
         throw new MissingResourceException(name);
      }

      if(!checkPermission(name, action, user)) {
         throw new UnauthorizedAccessException(name);
      }

      return ds;
   }

   /**
    * Checks that the caller may use the secret id that the properties reference (Bug #77157).
    * This is done before the data source is changed, so that the secret id it stores is still
    * known.
    */
   private void checkSecretId(JDBCDataSource ds, JdbcDataSourceProperties properties,
                              Principal user)
   {
      if(properties.isRequireLogin() && Tool.isCloudSecrets()) {
         SecretIdAuthorizer.checkSecretId(
            properties.getCredentialID(), secretIdAuthorizer.createCheck(ds, user));
      }
   }

   /**
    * Bug #77968, gets the password to store for a JDBC data source. The API returns the stored
    * password as {@link Util#PLACEHOLDER_PASSWORD}, so a client that gets a data source, changes
    * it and sends it back sends the placeholder (or {@link JdbcDataSourceProperties#PASSWORD_MASK},
    * the mask older versions returned). It keeps the stored password only for the same URL and
    * user name, so that the stored password isn't sent to another database, and is never stored
    * as the password itself. This is done before the data source is changed, so that the stored
    * URL and user name are still known.
    *
    * @return the password to store.
    *
    * @throws InvalidResourceException if the placeholder is sent with another URL or user name.
    */
   private static String resolvePassword(JDBCDataSource ds, JdbcDataSourceProperties properties)
      throws InvalidResourceException
   {
      String password = properties.getPassword();

      // a data source that uses a credential id gets its password from the secret
      if(!properties.isRequireLogin() || ds.isUseCredentialId() ||
         !JdbcDataSourceProperties.PASSWORD_MASK.equals(password) &&
         !Util.PLACEHOLDER_PASSWORD.equals(password))
      {
         return password;
      }

      if(!Tool.equals(Tool.defaultIfNull(properties.getUrl(), ""),
                      Tool.defaultIfNull(ds.getURL(), "")) ||
         !Tool.equals(Tool.defaultIfNull(properties.getUser(), ""),
                      Tool.defaultIfNull(ds.getUser(), "")))
      {
         throw new InvalidResourceException(
            "The stored password is only kept for the same URL and user name, enter the password");
      }

      return ds.getPassword();
   }

   private void updateDataSource(JDBCDataSource ds, JdbcDataSourceProperties properties,
                                 String password)
   {
      ds.setName(properties.getName());
      ds.setURL(properties.getUrl());
      ds.setDriver(properties.getDriver());
      ds.setDefaultDatabase(properties.getDefaultDatabase());
      ds.setTransactionIsolation(properties.getIsolation().value);
      ds.setAnsiJoin(properties.isAnsiJoin());
      ds.setTableNameOption(getTableOption(properties.getTableName()));

      if(ds.isCustomEditMode() || databaseTypeService
            .getDatabaseTypeForDriver(ds.getDriver()) instanceof CustomDatabaseType)
      {
         ds.setCustomUrl(properties.getUrl());
      }

      if(properties.isRequireLogin()) {
         ds.setUser(properties.getUser());

         if(!ds.isUseCredentialId()) {
            ds.setPassword(password);
         }

         ds.setRequireLogin(true);

         if(Tool.isCloudSecrets()) {
            ds.setCredentialId(properties.getCredentialID());
         }
      }
      else {
         ds.setRequireLogin(false);
         ds.setUser(null);
         ds.setPassword(null);
         ds.setCredentialId(null);
      }
   }

   private int getTableOption(String tableName) {
      return switch(tableName) {
         case JdbcDataSourceProperties.CATALOG_SCHEMA_OPTION -> 0;
         case JdbcDataSourceProperties.SCHEMA_OPTION -> 1;
         case JdbcDataSourceProperties.TABLE_OPTION -> 2;
         case JdbcDataSourceProperties.DEFAULT_OPTION -> 3;
         default -> 3;
      };
   }

   private void updateDataSource(XDataSource ds, TabularDataSourceProperties properties) {
      ds.setName(properties.getName());
   }

   private boolean checkPermission(String dataSource, ResourceAction action, Principal user) {
      try {
         return securityEngine.checkPermission(user, ResourceType.DATA_SOURCE, dataSource, action);
      }
      catch(SecurityException e) {
         throw new RuntimeException(e);
      }
   }

   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DatabaseTypeService databaseTypeService;
   private final MD5IdentifierGenerator ids;
   private final DataSourceRegistry dataSourceRegistry;
   private final SecretIdAuthorizer secretIdAuthorizer;
}
