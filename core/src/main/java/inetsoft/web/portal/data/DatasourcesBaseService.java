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
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.model.ws.TabularOAuthParams;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.viewsheet.AuditObjectName;
import inetsoft.web.viewsheet.Audited;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

public abstract class DatasourcesBaseService {
   public DatasourcesBaseService(XRepository repository,
                                 SecurityEngine securityEngine,
                                 DataSourceStatusService dataSourceStatusService)
   {
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.dataSourceStatusService = dataSourceStatusService;
   }

   protected XRepository getRepository() {
      return repository;
   }

   public DataSourceDefinition getDataSourceDefinition(@PermissionPath String path,
                                                       Principal principal)
      throws Exception
   {
      XDataSource dataSource = repository.getDataSource(path);

      if(dataSource == null) {
         throw new FileNotFoundException("Data source not found: " + path);
      }

      return createDataSourceDefinition(dataSource, principal);
   }

   private DataSourceDefinition createDataSourceDefinition(XDataSource dataSource,
                                                           Principal principal)
      throws Exception
   {
      String path = dataSource.getFullName();
      DataSourceDefinition result = new DataSourceDefinition();

      if(path == null) {
         return null;
      }

      String name = path.substring(path.lastIndexOf("/") + 1);
      result.setName(name);
      result.setDescription(dataSource.getDescription());
      String parentPath = path.lastIndexOf("/") > 0 ? path.substring(0, path.lastIndexOf("/")) : "";
      result.setParentPath(parentPath);
      result.setType(dataSource.getType());
      result.setDeletable(securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, path, ResourceAction.DELETE));

      LayoutCreator layoutCreator = new LayoutCreator();
      TabularView tabularView = layoutCreator.createLayout(dataSource);
      TabularUtil.refreshView(tabularView, dataSource);
      result.setTabularView(tabularView);

      if(dataSource instanceof AdditionalConnectionDataSource) {
         AdditionalConnectionDataSource<?> ads = (AdditionalConnectionDataSource<?>) dataSource;

         if(ads.getBaseDatasource() == null) {
            result.setAdditionalConnections(Arrays.stream(ads.getDataSourceNames())
               .map(ads::getDataSource)
               .map(child -> {
                  try {
                     return createDataSourceDefinition(child, principal);
                  }
                  catch(Exception e) {
                     throw new RuntimeException(e);
                  }
               })
               .collect(Collectors.toList()));
         }
         else {
            AdditionalConnectionDataSource<?> parent = ads.getBaseDatasource();
            path = parent.getFullName();
            result.setParentDataSource(path.substring(path.lastIndexOf("/") + 1));
            result.setParentPath(path.lastIndexOf("/") > 0 ? path.substring(0, path.lastIndexOf("/")) : "");
         }
      }

      return result;
   }

   public DataSourceDefinition refreshTabularView(DataSourceDefinition definition) {
      refreshAndGetDataSource(definition);
      return definition;
   }

   public TabularOAuthParams getOAuthParams(DataSourceOAuthParamsRequest request) {
      Object ds = refreshAndGetDataSource(request.dataSource());
      String license = SreeEnv.getProperty("license.key");
      int index = license.indexOf(',');

      if(index >= 0) {
         license = license.substring(0, index);
      }

      TabularOAuthParams.Builder builder = TabularOAuthParams.builder()
         .license(license);

      if(!LicenseManager.getInstance().isEnterprise() && (license == null || license.isEmpty())) {
         return builder.error(Catalog.getCatalog().getString("em.license.communityAPIKeyMissing"))
            .build();
      }

      if(ds != null) {
         Map<String, String> params = TabularUtil.getOAuthParameters(
            request.user(), request.password(), request.clientId(), request.clientSecret(),
            request.scope(), request.authorizationUri(), request.tokenUri(), request.flags(), ds);

         if(params != null) {
            builder
               .user(params.get("user"))
               .password(params.get("password"))
               .clientId(params.get("clientId"))
               .clientSecret(params.get("clientSecret"))
               .authorizationUri(params.get("authorizationUri"))
               .tokenUri(params.get("tokenUri"));

            String scope = params.get("scope");

            if(scope != null) {
               builder.addScope(scope.split(" "));
            }

            String flags = params.get("flags");

            if(flags != null) {
               builder.addFlags(flags.split(" "));
            }
         }
      }

      return builder.build();
   }

   public DataSourceDefinition setOAuthTokens(DataSourceOAuthTokens tokens) {
      Object ds = refreshAndGetDataSource(tokens.dataSource());

      if(ds != null) {
         Tokens params = Tokens.builder()
            .accessToken(tokens.accessToken())
            .refreshToken(tokens.refreshToken())
            .issued(tokens.issued())
            .expiration(tokens.expiration())
            .scope(tokens.scope())
            .properties(tokens.properties())
            .build();
         TabularUtil.setOAuthTokens(
            params, ds, tokens.method(), tokens.dataSource().getTabularView());
      }

      return tokens.dataSource();
   }

   private Object refreshAndGetDataSource(DataSourceDefinition definition) {
      String dsClass = Config.getDataSourceClass(definition.getType());
      Object ds = null;

      try {
         ds = Config.getClass(definition.getType(), dsClass).getConstructor().newInstance();
      }
      catch(Exception e) {
         LOG.error("Failed to create class: " + dsClass, e);
      }

      if(ds != null) {
         if(definition.getTabularView() == null) {
            LayoutCreator layoutCreator = new LayoutCreator();
            definition.setTabularView(layoutCreator.createLayout(ds));
         }

         TabularUtil.refreshView(definition.getTabularView(), ds);
      }

      if(definition.getAdditionalConnections() != null) {
         for(DataSourceDefinition additional : definition.getAdditionalConnections()) {
            refreshAndGetDataSource(additional);
         }
      }

      return ds;
   }

   public void clearDatasourceMetaData(String path) {
      JDBCUtil.clearTableMeta();

      if(repository instanceof XEngine) {
         ((XEngine) repository).removeMetaData(path);
      }
   }

   /**
    * Deletes a data source.
    *
    * @param path the path of the data source being deleted.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_DATASOURCE
   )
   public ConnectionStatus deleteDataSource(
      String path, @SuppressWarnings("unused") @AuditObjectName String objectName, boolean force)
      throws Exception
   {
      repository.removeDataSource(path, force);
      securityEngine.removePermission(ResourceType.DATA_SOURCE, path);
      SreeEnv.remove("inetsoft.uql.jdbc.pool." + path + ".connectionTestQuery");
      SreeEnv.save();
      return null;
   }

   public void checkDataSourceFolderOuterDependencies(String fname) throws Exception {
      String[] sources = repository.getSubDataSourceNames(fname);

      for(String source : sources) {
         checkDataSourceOuterDependencies(source);
      }
   }

   public void checkDataSourceOuterDependencies(String dxname) throws Exception {
      XDataModel model = repository.getDataModel(dxname);

      if(model != null) {
         for(String name : model.getLogicalModelNames()) {
            XLogicalModel lmodel = model.getLogicalModel(name);
            checkModelOuterDependencies(lmodel);
         }
      }
   }

   public void checkModelOuterDependencies(XLogicalModel model) {
      Object[] entries = model.getOuterDependencies();

      if(entries != null && entries.length != 0) {
         DependencyException ex = new DependencyException(model);
         ex.addDependencies(entries);

         if(!ex.isEmpty()) {
            throw ex;
         }
      }
   }

   /**
    * Check if a data source with the given name is already present.
    * @param name the name to check
    * @return  true if a data source with that name is present
    * @throws Exception if failed to check the data sources
    */
   public boolean checkDuplicate(String name) throws Exception {
      DataSourceRegistry.IGNORE_GLOBAL_SHARE.set(true);

      try {
         return repository.getDataSource(name) != null;
      }
      finally {
         DataSourceRegistry.IGNORE_GLOBAL_SHARE.remove();
      }
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_DATASOURCE
   )
   public void createNewDataSource(@AuditObjectName("getName()") BaseDataSourceDefinition definition, boolean isXmla,
                                   Principal principal)
      throws Exception
   {
      String folder = definition.getParentPath();
      boolean newSourcePermission = false;
      boolean folderPermission;

      if(StringUtils.isEmpty(folder) || "/".equals(folder)) {
         folderPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE);

         if(!folderPermission) {
            newSourcePermission = securityEngine.checkPermission(
               principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         }
      }
      else {
         folderPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.WRITE);
      }

      if(!folderPermission && !newSourcePermission) {
         String parentFullPath =
            Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, folder, principal);

         throw new SecurityException(
            "Unauthorized access to resource \"" + parentFullPath + "\" by user " +
               principal);
      }

      XDataSource ds = createDataSource(definition, null);

      if(ds != null) {
         String name = ds.getName();
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         ds.setCreatedBy(pId.getName());
         ds.setLastModifiedBy(pId.getName());
         ds.setCreated(System.currentTimeMillis());
         ds.setLastModified(System.currentTimeMillis());

         dataSourceStatusService.updateStatus(ds);
         repository.updateDataSource(ds, null, false);
         afterUpdateSourceCallback(definition, ds, true);


         boolean isSelfUser = Tool.equals(Organization.getSelfOrganizationID(),
                                          OrganizationManager.getInstance().getCurrentOrgID(principal));

         // some kind private datasource of the current user
         if(isSelfUser || (!folderPermission && newSourcePermission)) {
            String userWithoutOrg = principal.getName() != null ?
               IdentityID.getIdentityIDFromKey(principal.getName()).getName() : null;
            Set<String> users = Collections.singleton(userWithoutOrg);
            Permission permission = new Permission();
            String orgId = OrganizationManager.getInstance().getCurrentOrgID();
            permission.setUserGrantsForOrg(ResourceAction.READ, users, orgId);
            permission.setUserGrantsForOrg(ResourceAction.WRITE, users, orgId);
            permission.setUserGrantsForOrg(ResourceAction.DELETE, users, orgId);
            permission.updateGrantAllByOrg(orgId, true);
            securityEngine.setPermission(
               ResourceType.DATA_SOURCE, ds.getFullName(), permission);
         }

         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, isXmla ? AssetEntry.Type.DOMAIN : AssetEntry.Type.DATA_SOURCE, name, null);
         entry = getDataSourceAssetEntry(entry);

         if(entry != null) {
            entry.setCreatedUsername(principal.getName());
            entry.setCreatedDate(new Date());
            updateDataSourceAssetEntry(entry);
         }

         if(ds instanceof AdditionalConnectionDataSource &&
            definition instanceof DataSourceDefinition)
         {
            saveAdditionalConnections((DataSourceDefinition) definition,
               (AdditionalConnectionDataSource<?>) ds);
         }
      }
   }

   protected void afterUpdateSourceCallback(BaseDataSourceDefinition definition, XDataSource ds,
                                            boolean create)
   {
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_DATASOURCE
   )
   public void updateDataSource(@AuditObjectName String name, BaseDataSourceDefinition definition,
                                Principal principal)
      throws Exception
   {
      String parentPath = "".equals(definition.getParentPath()) ? "" : definition.getParentPath() + "/";
      String oldName = parentPath + name;
      String nName = parentPath + definition.getName();
      XDataSource oldSrc = repository.getDataSource(oldName);
      checkUpdateDatasourcePermission(nName, oldSrc, principal);
      XDataSource newSrc = createDataSource(definition, (XDataSource) Tool.clone(oldSrc));

      if(newSrc != null) {
         if(oldSrc == null) {
            throw new FileNotFoundException(oldName);
         }

         if(newSrc instanceof AdditionalConnectionDataSource &&
            definition instanceof DataSourceDefinition)
         {
            saveAdditionalConnections((DataSourceDefinition) definition,
               (AdditionalConnectionDataSource<?>) newSrc);
         }

         updateDatasource(oldName, newSrc, definition);
      }
   }

   protected void checkUpdateDatasourcePermission(String nName, XDataSource oldSrc,
                                                  Principal principal)
      throws Exception
   {
      String oldName = null;

      if(oldSrc != null) {
         oldName = oldSrc.getFullName();
      }

      if(oldSrc != null && !Tool.equals(oldName, nName)) {
         boolean hasPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, oldName, ResourceAction.DELETE);

         if(!hasPermission) {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "Permission denied to delete datasource folder"));
         }
      }
      else if(oldSrc != null) {
         boolean hasPermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, oldName, ResourceAction.WRITE);

         if(!hasPermission) {
            throw new MessageException(Catalog.getCatalog(principal).getString(
               "Permission denied to write datasource folder"));
         }
      }
   }

   public void updateDatasource(String oldName, XDataSource newSrc,
                                BaseDataSourceDefinition definition)
      throws Exception
   {
      if(newSrc != null) {
         String newName = newSrc.getName();
         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, oldName, null);
         entry = getDataSourceAssetEntry(entry);
         String user = null;
         Date date = null;

         if(entry != null) {
            user = entry.getCreatedUsername();
            date = entry.getCreatedDate();
         }

         newSrc.setLastModified(System.currentTimeMillis());
         dataSourceStatusService.updateStatus(newSrc);
         repository.updateDataSource(newSrc, oldName, false);
         afterUpdateSourceCallback(definition, newSrc, true);
         entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, newName, null);
         entry = getDataSourceAssetEntry(entry);

         if(entry != null) {
            entry.setCreatedUsername(user != null ? user : entry.getCreatedUsername());
            entry.setCreatedDate(date != null ? date : entry.getCreatedDate());
            updateDataSourceAssetEntry(entry);
         }
      }
   }

   private void saveAdditionalConnections(DataSourceDefinition definition,
                                          AdditionalConnectionDataSource<?> parent)
   {
      Set<String> updated = new HashSet<>();

      if(definition.getAdditionalConnections() != null) {
         for(DataSourceDefinition additional : definition.getAdditionalConnections()) {
            updated.add(additional.getName());
            additional.setParentPath(definition.getParentPath());
            additional.setParentDataSource(definition.getName());

            AdditionalConnectionDataSource<?> child = parent.getDataSource(additional.getName());

            if(child == null) {
               child = (AdditionalConnectionDataSource<?>) createDataSource(additional, null);
            }
            else {
               child = (AdditionalConnectionDataSource<?>) createDataSource(additional, child);
            }

            parent.addDatasource(child);
            updateAdditionalPermission(definition, additional);
         }
      }

      for(String child : parent.getDataSourceNames()) {
         if(!updated.contains(child)) {
            parent.removeDatasource(child);
         }
      }
   }

   private void updateAdditionalPermission(DataSourceDefinition parent, DataSourceDefinition additional)
   {
      if(additional.getOldName() == null || Tool.equals(additional.getOldName(), additional.getName())) {
         return;
      }

      String folder = additional.getParentPath();
      folder = folder == null ? "" : folder;
      String oParentName = parent.getOldName();
      oParentName = oParentName == null ? parent.getName() : oParentName;
      String resource = Tool.buildString(folder, oParentName,
                                         XUtil.ADDITIONAL_DS_CONNECTOR, additional.getOldName());
      Permission perm = securityEngine.getPermission(ResourceType.DATA_SOURCE, resource);

      if(perm != null) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE, resource);
         String nresource = Tool.buildString(folder, parent.getName(),
                                             XUtil.ADDITIONAL_DS_CONNECTOR, additional.getName());
         securityEngine.setPermission(ResourceType.DATA_SOURCE, nresource, perm);
      }
   }

   /**
    * Retrieves AssetEntry of a data source from the registry.
    *
    * @param oldEntry the asset entry we are trying to find.
    *
    * @return the asset entry from the repository.
    */
   public AssetEntry getDataSourceAssetEntry(AssetEntry oldEntry) {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      AssetEntry[] entries = registry.getEntries(oldEntry.getPath(), AssetEntry.Type.DATA_SOURCE);

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
      try {
         DataSourceRegistry registry = DataSourceRegistry.getRegistry();
         registry.setObject(entry, registry.getObject(entry, true));
      }
      catch(Exception e) {
         LoggerFactory.getLogger(DataSourceBrowserService.class).debug(
            "Failed to keep created time/date of updated data source: " + entry.getName());
      }
   }

   protected void checkDatasourceNameValid(String oldName, String newName, String parentPath) {
      if(oldName == null || !oldName.equals(newName)) {
         final String dataSourceNameValid = XUtil.isDataSourceNameValid(
            repository, newName, parentPath);

         if(!"Valid".equals(dataSourceNameValid)) {
            throw new MessageException(dataSourceNameValid);
         }
      }
   }

   /**
    * Create a new data source connection.
    *
    * @param definition new data source definition.
    * @param ds existing data source if updating (not new).
    * @return data source object for the new connection
    */
   public abstract XDataSource createDataSource(BaseDataSourceDefinition definition, XDataSource ds);

   public DataSourceDefinition getDataSourceFromListing(String listingName) throws Exception {
      DataSourceListing listing = DataSourceListingService.getDataSourceListing(listingName);
      XDataSource dataSource = null;

      if(listing != null) {
         dataSource = listing.createDataSource();
      }

      if(dataSource == null) {
         throw new FileNotFoundException();
      }

      DataSourceDefinition result = new DataSourceDefinition();
      result.setName(dataSource.getName());
      result.setDescription(dataSource.getDescription());
      result.setType(dataSource.getType());
      result.setDeletable(true);

      LayoutCreator layoutCreator = new LayoutCreator();
      TabularView tabularView = layoutCreator.createLayout(dataSource);
      TabularUtil.refreshView(tabularView, dataSource);
      result.setTabularView(tabularView);
      return result;
   }

   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DataSourceStatusService dataSourceStatusService;
   private static final Logger LOG = LoggerFactory.getLogger(DatasourcesBaseService.class);
}
