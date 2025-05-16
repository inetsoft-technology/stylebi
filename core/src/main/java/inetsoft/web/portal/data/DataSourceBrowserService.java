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
package inetsoft.web.portal.data;

import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.RepositoryObjectService;
import inetsoft.web.admin.model.NameLabelTuple;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.controller.SearchComparator;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataSourceBrowserService {
   @Autowired
   public DataSourceBrowserService(SecurityEngine securityEngine,
                                   RepositoryObjectService repositoryObjectService,
                                   XRepository repository,
                                   DataSourceService dataSourceService)
   {
      this.securityEngine = securityEngine;
      this.repository = repository;
      this.repositoryObjectService = repositoryObjectService;
      this.dataSourceService = dataSourceService;
   }

   public List<DataSourceInfo> getDataSources(String path, boolean root, String[] movingFolders,
                                              Principal principal)
      throws Exception
   {
      Locale locale = getLocale(principal);
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      List<DataSourceInfo> dataSources = new ArrayList<>();

      if(path == null && root) {
         DataSourceInfo info = getDSFolderInfo(getRootFolder(principal), movingFolders, principal);
         dataSources.add(info);

         return dataSources;
      }

      for(String name : getDataSources(path, false)) {
         if(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ)) {
            dataSources.add(getDataSourceInfo(registry.getDataSource(name), principal, locale));
         }
      }

      List<String> folders = registry.getSubfolderNames(path);
      Collections.sort(folders);

      for(String folder: folders) {
         if(securityEngine.checkPermission(
               principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.READ)) {
            dataSources.add(getDSFolderInfo(
               registry.getDataSourceFolder(folder), movingFolders, principal));
         }
      }

      return dataSources.stream().filter(Objects::nonNull).collect(Collectors.toList());
   }

   public DataSourceInfo getDataSourceFolder(String path, Principal principal) throws Exception {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      return getDSFolderInfo(registry.getDataSourceFolder(path), null, principal);
   }

   public List<DataSourceInfo> getSearchDataSources(String path, String query, Principal principal)
      throws Exception
   {
      List<DataSourceInfo> dataSourceInfos = getAllSubDataSources(path, principal);

      return dataSourceInfos.stream()
         .filter((dataSourceInfo) -> {
            return dataSourceInfo.name().toLowerCase().indexOf(query.toLowerCase()) != -1;
         })
         .sorted((a, b) -> new SearchComparator.StringComparator(query).compare(a.name(), b.name()))
         .collect(Collectors.toList());
   }

   /**
    * Retrieves all sub data sources under data source path folder.
    *
    * @param path parent folder path
    * @return a list of data sources.
    */
   public List<DataSourceInfo> getAllSubDataSources(String path, Principal principal)
      throws Exception
   {
      Locale locale = getLocale(principal);
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      List<DataSourceInfo> dataSources = new ArrayList<>();

      for(String name : getDataSources(path, true)) {
         if(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ)) {
            dataSources.add(getDataSourceInfo(registry.getDataSource(name), principal, locale));
         }
      }

      List<String> folders = registry.getSubfolderNames(path, true);
      Collections.sort(folders);

      for(String folder: folders) {
         if(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.READ)) {
            dataSources.add(getDSFolderInfo(
               registry.getDataSourceFolder(folder), null, principal));
         }
      }

      return dataSources;
   }

   /**
    * Determines whether there are sub datasource folders.
    *
    * @param dsFolder the parent datasource folder.
    *
    * @param movingFolders the moving folders
    *
    * @return rure if has sub datasource folder
    */
   private boolean hasSubDataSourceFolder(DataSourceFolder dsFolder, String[] movingFolders) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      String folderName = "/".equals(dsFolder.getFullName()) ? null : dsFolder.getFullName();
      List<String> folders = registry.getSubfolderNames(folderName);

      if(folders == null || folders.size() == 0 || movingFolders == null) {
         return false;
      }

      return folders.stream().anyMatch(folder -> !Arrays.asList(movingFolders).contains(folder));
   }

   private DataSourceInfo getDSFolderInfo(DataSourceFolder dsFolder, Principal principal)
      throws Exception
   {
      return getDSFolderInfo(dsFolder, null, principal);
   }

   /**
    * get data source folder info.
    * @param dsFolder data source folder
    * @param principal principal
    */
   private DataSourceInfo getDSFolderInfo(DataSourceFolder dsFolder, String[] movingFolders,
                                          Principal principal)
      throws Exception
   {
      if(dsFolder == null) {
         return null;
      }

      String label = Catalog.getCatalog().getString("DATA SOURCE FOLDER");
      LocalDateTime cdate = dsFolder.getCreatedDate();
      long createDate = cdate == null ? 0 : Tool.getTimestampOfDateTime(cdate);
      String fmt = SreeEnv.getProperty("format.date.time");
      DateTimeFormatter df = DateTimeFormatter.ofPattern(fmt);
      String dateLabel = cdate == null ? "" : df.format(cdate);

      NameLabelTuple type = NameLabelTuple.builder()
            .name(PortalDataType.DATA_SOURCE_FOLDER.name())
            .label(label)
            .build();

      return DataSourceInfo.builder()
            .name(dsFolder.getName())
            .path(dsFolder.getFullName())
            .createdBy(SUtil.getUserAlias(new IdentityID(dsFolder.getCreatedUsername(),
                                                         OrganizationManager.getInstance().getCurrentOrgID())))
            .createdDate(createDate)
            .createdDateLabel(dateLabel)
            .dateFormat(Tool.getDateFormatPattern())
            .type(type)
            .editable(securityEngine.checkPermission(principal,
               ResourceType.DATA_SOURCE_FOLDER, dsFolder.getFullName(), ResourceAction.WRITE))
            .deletable(securityEngine.checkPermission(principal,
               ResourceType.DATA_SOURCE_FOLDER, dsFolder.getFullName(), ResourceAction.DELETE))
            .hasSubFolder(hasSubDataSourceFolder(dsFolder, movingFolders))
            .build();
   }

   public List<TabularDataSourceTypeModel> getTabularDataSources(Principal principal)
      throws Exception
   {
      List<TabularDataSourceTypeModel> models = new ArrayList<>();
      Catalog catalog = Catalog.getCatalog(principal);
      Set<String> existing = new HashSet<>();
      Locale locale;

      if(principal instanceof SRPrincipal && ((SRPrincipal) principal).getLocale() != null) {
         locale = ((SRPrincipal) principal).getLocale();
      }
      else {
         locale = Locale.getDefault();
      }

      for(String dataSourceName : repository.getDataSourceFullNames()) {
         if(checkDataSourcePermission(principal, dataSourceName)) {
            XDataSource dataSource = getDataSource(dataSourceName);

            if(dataSource instanceof TabularDataSource) {
               TabularDataSourceTypeModel model = new TabularDataSourceTypeModel();
               model.setName(dataSource.getType());
               model.setDataSource(dataSourceName);
               model.setLabel(catalog.getString(dataSource.getName()));
               model.setExists(true);
               models.add(model);
               existing.add(dataSource.getType());
            }
         }
      }

      for(String type : Config.getTabularDataSourceTypes()) {
         if(!existing.contains(type)) {
            TabularDataSourceTypeModel model = new TabularDataSourceTypeModel();
            model.setName(type);
            model.setDataSource(null);
            model.setLabel(Config.getDisplayLabel(type, locale));
            model.setExists(false);
            models.add(model);
         }
      }

      models.sort(
         Comparator.comparing(TabularDataSourceTypeModel::isExists).reversed()
            .thenComparing(TabularDataSourceTypeModel::getLabel));

      return models;
   }

   public DatabaseDataSources getDatabaseDataSources(Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog(principal);
      List<DatabaseDataSource> list = Arrays.stream(repository.getDataSourceFullNames())
         .filter(ds -> checkDataSourcePermission(principal, ds))
         .map(this::getDataSource)
         .filter(ds -> ds instanceof JDBCDataSource)
         .map(ds -> DatabaseDataSource.builder()
         .name(ds.getFullName())
         .label(catalog.getString(ds.getName()))
         .build())
         .sorted(Comparator.comparing(DatabaseDataSource::label))
         .collect(Collectors.toList());
      return DatabaseDataSources.builder().dataSources(list).build();
   }

   private boolean checkDataSourcePermission(Principal principal, String name) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ);
      }
      catch(SecurityException e) {
         throw new RuntimeException("Failed to check data source permission", e);
      }
   }

   private XDataSource getDataSource(String name) {
      try {
         return repository.getDataSource(name);
      }
      catch(RemoteException e) {
         throw new RuntimeException("Failed to get data source", e);
      }
   }

   private DataSourceInfo getDataSourceInfo(XDataSource dataSource,
                                            Principal principal,
                                            Locale locale) throws Exception
   {
      boolean isJDBC = dataSource instanceof JDBCDataSource;
      String label = isJDBC ? Catalog.getCatalog().getString("Database") :
         Config.getDisplayLabel(dataSource.getType(), locale);
      String typeName;

      if(isJDBC) {
         typeName = PortalDataType.DATABASE.name();
      }
      else {
         typeName = dataSource instanceof XMLADataSource ? PortalDataType.XMLA_SOURCE.name() :
            PortalDataType.DATA_SOURCE.name();
      }

      NameLabelTuple type = NameLabelTuple.builder()
         .name(typeName)
         .label(Objects.requireNonNull(label))
         .build();

      long cdate = dataSource.getCreated();
      String fmt = SreeEnv.getProperty("format.date.time");
      String dateLabel = cdate == 0 ? "" : new SimpleDateFormat(fmt).format(cdate);

      return DataSourceInfo.builder()
         .name(dataSource.getName())
         .path(dataSource.getFullName())
         .type(type)
         .createdBy(SUtil.getUserAlias(
            new IdentityID(dataSource.getCreatedBy(), OrganizationManager.getInstance().getCurrentOrgID())))
         .createdDate(cdate)
         .createdDateLabel(dateLabel)
         .dateFormat(Tool.getDateFormatPattern())
         .editable(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, dataSource.getFullName(), ResourceAction.WRITE))
         .deletable(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, dataSource.getFullName(), ResourceAction.DELETE))
         .queryCreatable(isQueryCreatable(dataSource, principal))
         .childrenCreatable(isChildrenCreatable(dataSource.getFullName(), principal))
         .hasSubFolder(false)
         .build();
   }

   private boolean isQueryCreatable(XDataSource dataSource, Principal principal) throws SecurityException {
      if(!securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, dataSource.getFullName(), ResourceAction.READ))
      {
         return false;
      }

      if(!(dataSource instanceof TabularDataSource)) {
         return securityEngine.checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);
      }

      return true;
   }

   private boolean isChildrenCreatable(String name, Principal principal) throws SecurityException {
      return securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, name, ResourceAction.WRITE);
   }

   /**
    * rename datasource folder.
    * @param path old folder path
    * @param newName new folder name
    * @param auditPath audit path(fullPath).
    * @param principal user
    * @return new folder path
    * @throws Exception rename failed.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public synchronized String renameFolder(
      String path, String newName, @SuppressWarnings("unused") @AuditObjectName String auditPath,
      @SuppressWarnings("unused") @AuditActionError("'Target Entry: ' + #this") String targetEntityPath,
      Principal principal) throws Exception
   {
      String newPath;
      securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE_FOLDER, path, ResourceAction.ADMIN);
      DataSourceFolder folder = repository.getDataSourceFolder(path, true);

      if(folder == null) {
         throw new RuntimeException("Data space folder does not exist: " + path);
      }

      String parent = DataSourceFolder.getParentName(path);

      if(parent == null) {
         newPath = newName;
      }
      else {
         newPath = parent + "/" + newName;
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

      return newPath;
   }

   /**
    * Gets the browser view for a folder.
    *
    * @param path   the path to the folder.
    *
    * @return the browser view.
    *
    * @throws Exception if the browser view could not be loaded.
    */
   public List<DataSourceInfo> getBreadcrumbs(String path, boolean home, Principal principal)
         throws Exception
   {
      String[] paths;
      List<DataSourceInfo> folders = new ArrayList<>();

      if(!home) {
         DataSourceInfo info = getDSFolderInfo(getRootFolder(principal), principal);
         folders.add(info);
      }

      if(path == null) {
         return folders;
      }
      else {
         paths = path.split("/");
      }

      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      for(int i = 0; i < paths.length; i++) {
         if(i > 0) {
            paths[i] = paths[i - 1] + "/" + paths[i];
         }

         if(registry.getDataSourceFolder(paths[i]) == null) {
            continue;
         }

         DataSourceFolder folder = registry.getDataSourceFolder(paths[i]);

         if(folder == null) {
            LOG.warn("Folder missing from bread crumbs: " + paths[i] + " in " + path);
            break;
         }

         DataSourceInfo info = getDSFolderInfo(registry.getDataSourceFolder(paths[i]), principal);

         if(info == null) {
            break;
         }

         folders.add(info);
      }

      return folders;
   }

   /**
    * delete datasource folder.
    * @param path folder path.
    * @param auditPath audit path(fullPath).
    * @param principal user
    */
   @Audited(
         actionName = ActionRecord.ACTION_NAME_DELETE,
         objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public ConnectionStatus deleteDataSourceFolder(
      String path, @SuppressWarnings("unused") @AuditObjectName String auditPath, boolean force,
      Principal principal)
   {
      return repositoryObjectService.removeDataSourceFolder(path, force, principal);
   }

   private Locale getLocale(Principal principal) {
      Locale locale;

      if(principal instanceof SRPrincipal) {
         locale = ((SRPrincipal) principal).getLocale();
      }
      else {
         String locstr = SreeEnv.getProperty("em.locale");
         locale = Catalog.parseLocale(locstr);
      }

      return locale == null ? Locale.getDefault() : locale;
   }

   /**
    * Retrieves all data sources under data source path folder.
    *
    * @param path parent folder path
    * @return a list of data sources.
    */
   private List<String> getDataSources(String path, boolean search) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      List<String> dsnames = registry.getSubDataSourceNames(path, search);
      Collections.sort(dsnames);
      List<String> dataSources = new ArrayList<>(dsnames);

      dataSources = dataSources.stream()
         .filter(dsName -> {
               try {
                  XDataSource dataSource = registry.getDataSource(dsName);

                  return dataSource instanceof JDBCDataSource ||
                     dataSource instanceof TabularDataSource ||
                     dataSource instanceof XMLADataSource;
               }
               catch(Throwable ex) {
                  return false;
               }
            })
         .collect(Collectors.toList());

      return dataSources;
   }

   /**
    * add datasource folder.
    * @param principal user
    * @throws Exception add folder failed.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_FOLDER
   )
   public void addDatasourceFolder(String path,
                                   @SuppressWarnings("unused") @AuditObjectName String auditPath,
                                   Principal principal, boolean userScope)
      throws Exception
   {
      LocalDateTime time = LocalDateTime.now();
      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      String userName = user != null ? user.getName() : null;
      DataSourceFolder dsFolder = new DataSourceFolder(path, time, userName);

      if(userScope) {
         Set<String> users = Collections.singleton(userName);
         Permission permission = new Permission();
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         permission.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
         permission.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
         permission.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);

         securityEngine.setPermission(
            ResourceType.DATA_SOURCE_FOLDER, dsFolder.getFullName(), permission);
      }

      repository.updateDataSourceFolder(dsFolder, null);
   }

   /**
    * Check if datasource folder name is already present.
    * @param name folder name.
    * @return a result in the form of CheckDuplicateResponse.
    */
   @DatasourceIgnoreGlobalShare
   public CheckDuplicateResponse checkFolderDuplicate(String name) throws Exception {
      boolean folderExist = repository.getDataSourceFolder(name) != null;
      boolean dbExist = repository.getDataSource(name) != null;

      return new CheckDuplicateResponse(folderExist || dbExist);
   }

   @DatasourceIgnoreGlobalShare
   public CheckDuplicateResponse checkItemsDuplicate(DataSourceInfo[] items, String path) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      for(DataSourceInfo item : items) {
         String ipath = item.path();
         String iname = ipath.substring(ipath.lastIndexOf('/') + 1);
         ipath = (path == null || "/".equals(path)) ? iname : path + "/" + iname;

         if(registry.getDataSource(ipath) != null || registry.getDataSourceFolder(ipath) != null) {
            return new CheckDuplicateResponse(true);
         }
      }

      return new CheckDuplicateResponse(false);
   }

   public void moveDataSource(MoveCommand[] items, Principal principal) throws Exception {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      // log action
      String objectName;
      String targetName = "";
      String actionName = ActionRecord.ACTION_NAME_MOVE;
      Timestamp actionTimestamp =  new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, null,
         null, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
         null);

      for(MoveCommand item : items) {
         try {
            if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
                  item.getPath(), ResourceAction.WRITE))
            {
               String path = item.getPath();

               if(path != null) {
                  path = path.replace(item.getName(), "");
                  path = path.replaceAll("[\\\\/]+$", "");
               }

               throw new MessageException(Catalog.getCatalog()
                  .getString("common.writeAuthority", path));
            }

            if(PortalDataType.DATA_SOURCE_FOLDER.name().equals(item.getType())) {
               String oname = item.getOldPath();
               String nname = item.getPath();

               if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
                  oname, ResourceAction.DELETE))
               {
                  throw new MessageException(Catalog.getCatalog(principal).getString(
                     "common.deleteAuthority", oname));
               }

               List<RenameDependencyInfo> renameDependencyInfos =
                  DependencyTransformer.createDatasourceFolderDependencyInfo(getDSRegistry(),
                     oname, nname);
               registry.renameDataSourceFolder(oname, nname);

               for(RenameDependencyInfo renameDependencyInfo : renameDependencyInfos) {
                  RenameTransformHandler.getTransformHandler().addTransformTask(renameDependencyInfo);
               }

               objectName = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER,
                  item.getOldPath(), principal);
               targetName = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER,
                  item.getPath(), principal);

               if(actionRecord != null) {
                  actionRecord.setObjectName(objectName);
                  actionRecord.setObjectType(ActionRecord.OBJECT_TYPE_FOLDER);
               }
            }
            else {
               String oname = item.getOldPath();
               String nname = item.getPath();

               if(!securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE,
                  oname, ResourceAction.DELETE))
               {
                  throw new MessageException(Catalog.getCatalog(principal).getString(
                     "common.deleteAuthority", oname));
               }

               XDataSource ds = repository.getDataSource(oname);
               removeDefaultMetaDataProviderCache(ds);
               ds.setName(nname);

               // Bug #60289, rename transform task is submitted in updateDataSource() for REST
               if(!((ds instanceof ListedDataSource) || ds instanceof TabularDataSource ||
                  ds.getType().startsWith(SourceInfo.REST_PREFIX)))
               {
                  RenameDependencyInfo dinfo = DependencyTransformer.createDependencyInfo(
                     oname, ds.getFullName());
                  RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
               }

               repository.updateDataSource(ds, oname, false);

               objectName = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE,
                  item.getOldPath(), principal);
               targetName = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE,
                  item.getPath(), principal);

               if(actionRecord != null) {
                  actionRecord.setObjectName(objectName);
                  actionRecord.setObjectType(ActionRecord.OBJECT_TYPE_DATASOURCE);
               }
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
               actionRecord.setActionError(
                  "Target Entry: " + targetName);
            }
         }
         catch(Exception ex) {
            if(ex instanceof ConfirmException) {
               actionRecord = null;
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(ex.getMessage() + ", Target Entry: " +
                  targetName);
            }

            throw ex;
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
      }
   }

   private void removeDefaultMetaDataProviderCache(XDataSource ds) throws Exception {
      if(ds == null) {
         return;
      }

      dataSourceService.removeDefaultMetaDataProviderCache(ds);

      if(ds instanceof AdditionalConnectionDataSource) {
         AdditionalConnectionDataSource additionalDataSource = (AdditionalConnectionDataSource) ds;
         String[] dataSourceNames = additionalDataSource.getDataSourceNames();

         if(dataSourceNames == null) {
            return;
         }

         for(String dataSourceName : dataSourceNames) {
            XDataSource dataSource = additionalDataSource.getDataSource(dataSourceName);

            if(dataSource == null) {
               continue;
            }

            dataSourceService.removeDefaultMetaDataProviderCache(dataSource);
         }
      }
   }

   private DataSourceRegistry getDSRegistry() {
      try {
         return DataSourceRegistry.getRegistry();
      }
      catch(Exception ex) {
         throw new RuntimeException("Failed to get data source registry", ex);
      }
   }

   private DataSourceFolder getRootFolder(Principal principal) {
      LocalDateTime time = LocalDateTime.now();
      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());
      return new DataSourceFolder("/", time, user != null ? user.getName() : null);
   }

   private final SecurityEngine securityEngine;
   private final XRepository repository;
   private final RepositoryObjectService repositoryObjectService;
   private final DataSourceService dataSourceService;

   private static final Logger LOG = LoggerFactory.getLogger(DataSourceBrowserService.class);
}
