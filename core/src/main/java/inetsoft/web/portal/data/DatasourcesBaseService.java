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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class DatasourcesBaseService {
   public DatasourcesBaseService(XRepository repository,
                                 SecurityEngine securityEngine,
                                 DataSourceStatusService dataSourceStatusService,
                                 DataSourceRegistry dataSourceRegistry,
                                 Config uqlConfig)
   {
      this.repository = repository;
      this.securityEngine = securityEngine;
      this.dataSourceStatusService = dataSourceStatusService;
      this.dataSourceRegistry = dataSourceRegistry;
      this.uqlConfig = uqlConfig;
      this.secretIdAuthorizer = new SecretIdAuthorizer(securityEngine, dataSourceRegistry);
   }

   protected XRepository getRepository() {
      return repository;
   }

   protected Config getUqlConfig() {
      return uqlConfig;
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
      DraftDataSource draft = refreshDraftDataSource(definition);

      // the OAuth button can't be used until the secret id is resolved, tell the user why
      if(isAuthorizeBlocked(draft, definition)) {
         CoreTool.addUserMessage(getSecretIdWithheldMessage(draft.principal()));
      }

      return definition;
   }

   public TabularOAuthParams getOAuthParams(DataSourceOAuthParamsRequest request) {
      DraftDataSource draft = refreshDraftDataSource(request.dataSource());
      Object ds = draft.dataSource();
      String license = SreeEnv.getProperty("license.key");
      int index = license.indexOf(',');

      if(index >= 0) {
         license = license.substring(0, index);
      }

      TabularOAuthParams.Builder builder = TabularOAuthParams.builder()
         .license(license);

      if(!LicenseManager.isEnterprise() && (license == null || license.isEmpty())) {
         return builder.error(Catalog.getCatalog().getString("em.license.communityAPIKeyMissing"))
            .build();
      }

      if(isAuthorizeBlocked(draft, request.dataSource())) {
         return builder.error(getSecretIdWithheldMessage(draft.principal())).build();
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
      Object ds = refreshDraftDataSource(tokens.dataSource()).dataSource();

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

   /**
    * Creates a data source from a definition that was supplied by the client and refreshes its
    * view. The definition may reference any secret id, so a secret is only resolved if the caller
    * can already see it through a saved data source.
    */
   private DraftDataSource refreshDraftDataSource(DataSourceDefinition definition) {
      Principal principal = ThreadContext.getContextPrincipal();
      Map<String, Boolean> authorized = new HashMap<>();
      Object ds = TabularDataSource.withCredentialFetchGate(
         secretId -> authorized.computeIfAbsent(
            secretId, id -> isSecretIdAuthorized(definition, id, principal)),
         () -> refreshAndGetDataSource(definition));
      // only the data source's own secret id counts, not those of its additional connections
      boolean withheld = ds instanceof TabularDataSource<?> tabular &&
         tabular.isUseCredentialId() && !Tool.isEmptyString(tabular.getCredentialId()) &&
         Boolean.FALSE.equals(authorized.get(tabular.getCredentialId()));
      return new DraftDataSource(ds, withheld, principal);
   }

   /**
    * Gets the message that explains why the secret id of a draft data source was not resolved.
    * Saving the data source resolves it only if the caller may introduce new secret ids.
    */
   private String getSecretIdWithheldMessage(Principal principal) {
      return Catalog.getCatalog().getString(
         secretIdAuthorizer.canIntroduceSecretIds(principal) ?
            "data.datasources.saveBeforeAuthorize" : "data.datasources.secretIdNotAllowed");
   }

   /**
    * Determines if the OAuth button of a draft data source can't be used because its secret id
    * was withheld. A button that uses a hosted OAuth service does not need the secret.
    */
   private static boolean isAuthorizeBlocked(DraftDataSource draft,
                                             DataSourceDefinition definition)
   {
      return draft.secretIdWithheld() && definition.getTabularView() != null &&
         hasVisibleClientOAuthButton(definition.getTabularView().getViews());
   }

   private static boolean hasVisibleClientOAuthButton(TabularView[] views) {
      if(views != null) {
         for(TabularView view : views) {
            TabularButton button = view.getButton();

            if(view.isVisible() && (button != null && button.getType() == ButtonType.OAUTH &&
               Tool.isEmptyString(button.getOauthServiceName()) ||
               hasVisibleClientOAuthButton(view.getViews())))
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Determines if a secret id referenced by a client-supplied definition may be resolved. Secret
    * ids are not scoped to a data source or organization, so an id may only be resolved if a saved
    * data source in the current organization that the caller can write, or one of its additional
    * connections, already references it. The caller can already see the secret by editing that
    * data source.
    */
   private boolean isSecretIdAuthorized(DataSourceDefinition definition, String secretId,
                                        Principal principal)
   {
      return secretIdAuthorizer.isStoredOnWritableDataSource(
         secretId, getSavedDataSourcePath(definition), principal);
   }

   private static String getSavedDataSourcePath(DataSourceDefinition definition) {
      String name = definition.getParentDataSource() != null ?
         definition.getParentDataSource() :
         (definition.getOldName() != null ? definition.getOldName() : definition.getName());

      if(StringUtils.isEmpty(name)) {
         return null;
      }

      String parentPath = definition.getParentPath();
      return StringUtils.isEmpty(parentPath) || "/".equals(parentPath) ?
         name : parentPath + "/" + name;
   }

   /**
    * Creates the data source that a client-supplied definition describes, and its additional
    * connections, so that they can be saved. Each secret id that the definition or one of its
    * additional connections references is only resolved if the caller may use it, and the
    * definition is rejected before anything is saved if the caller may not.
    *
    * @param definition the data source definition.
    * @param ds         the data source to update, or {@code null} to create a new one.
    * @param stored     the data source that is stored at the path being saved, if any.
    * @param principal  the caller.
    *
    * @return the data source and the additional connections to save with it.
    */
   private AuthorizedDataSource createAuthorizedDataSource(BaseDataSourceDefinition definition,
                                                           XDataSource ds, XDataSource stored,
                                                           Principal principal)
   {
      Predicate<String> check = secretIdAuthorizer.createCheck(stored, principal);

      return TabularDataSource.withCredentialFetchGate(check, () -> {
         XDataSource result = createDataSource(definition, ds);
         SecretIdAuthorizer.checkSecretId(SecretIdAuthorizer.getCloudSecretId(result), check);
         List<AdditionalConnectionDataSource<?>> additionals = null;

         // additional connections are added after the data source is saved, so create them now
         // and check the secret ids they reference. The same objects are saved later, so each
         // definition is only applied once.
         if(result instanceof AdditionalConnectionDataSource<?> parent &&
            definition instanceof DataSourceDefinition dsDefinition)
         {
            additionals = createAdditionalConnections(dsDefinition, parent);

            for(AdditionalConnectionDataSource<?> child : additionals) {
               SecretIdAuthorizer.checkSecretId(
                  SecretIdAuthorizer.getCloudSecretId(child), check);
            }
         }

         return new AuthorizedDataSource(result, additionals);
      });
   }

   private Object refreshAndGetDataSource(DataSourceDefinition definition) {
      String dsClass = uqlConfig.getDataSourceClass(definition.getType());
      Object ds = null;

      try {
         ds = uqlConfig.getClass(definition.getType(), dsClass).getConstructor().newInstance();
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
      JDBCUtil.removeConnectionTestQuery(path);
      SreeEnv.save();
      return null;
   }

   public void checkDataSourceFolderOuterDependencies(String fname, Principal principal)
      throws Exception
   {
      String[] sources = repository.getSubDataSourceNames(fname);

      for(String source : sources) {
         // the folder delete refuses a source the user can't delete, so don't report its
         // dependencies
         if(securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE, source, ResourceAction.DELETE))
         {
            checkDataSourceOuterDependencies(source);
         }
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

      if(!StringUtils.isEmpty(folder) && !"/".equals(folder) &&
         dataSourceRegistry.getDataSourceFolder(folder) == null)
      {
         throw new MessageException(
            Catalog.getCatalog().getString("data.datasources.invalidParentFolder"));
      }

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

      AuthorizedDataSource authorized =
         createAuthorizedDataSource(definition, null, null, principal);
      XDataSource ds = authorized.dataSource();

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

         if(authorized.additionalConnections() != null) {
            saveAdditionalConnections((DataSourceDefinition) definition,
               (AdditionalConnectionDataSource<?>) ds, authorized.additionalConnections(), null);
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

      // an additional connection is saved through its parent data source, saving it by its path
      // would turn it into a standalone data source
      if(dataSourceRegistry.isAdditionalConnectionPath(oldName)) {
         throw new MessageException(Catalog.getCatalog(principal).getString(
            "common.datasource.additionalConnectionMove"));
      }

      XDataSource oldSrc = repository.getDataSource(oldName);
      checkUpdateDatasourcePermission(nName, oldSrc, principal);
      AuthorizedDataSource authorized = createAuthorizedDataSource(
         definition, (XDataSource) Tool.clone(oldSrc), oldSrc, principal);
      XDataSource newSrc = authorized.dataSource();

      if(newSrc != null) {
         if(oldSrc == null) {
            throw new MessageException(Catalog.getCatalog().getString(
               "data.datasources.saveDataSourceLost"));
         }

         if(authorized.additionalConnections() != null) {
            saveAdditionalConnections((DataSourceDefinition) definition,
               (AdditionalConnectionDataSource<?>) newSrc, authorized.additionalConnections(),
               oldName);
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

   /**
    * Creates the additional connections that a definition describes, without adding them to the
    * parent data source.
    */
   private List<AdditionalConnectionDataSource<?>> createAdditionalConnections(
      DataSourceDefinition definition, AdditionalConnectionDataSource<?> parent)
   {
      List<AdditionalConnectionDataSource<?>> additionals = new ArrayList<>();

      if(definition.getAdditionalConnections() != null) {
         for(DataSourceDefinition additional : definition.getAdditionalConnections()) {
            additional.setParentPath(definition.getParentPath());
            additional.setParentDataSource(definition.getName());

            AdditionalConnectionDataSource<?> child = parent.getDataSource(additional.getName());

            if(child == null) {
               child = (AdditionalConnectionDataSource<?>) createDataSource(additional, null);
            }
            else {
               child = (AdditionalConnectionDataSource<?>) createDataSource(additional, child);
            }

            additionals.add(child);
         }
      }

      return additionals;
   }

   /**
    * Saves the additional connections created by
    * {@link #createAdditionalConnections(DataSourceDefinition, AdditionalConnectionDataSource)}
    * and removes the ones that the definition no longer contains.
    *
    * @param oldParent the full name of the parent data source before this save, or {@code null}
    *                  if it is created by this save.
    */
   private void saveAdditionalConnections(DataSourceDefinition definition,
                                          AdditionalConnectionDataSource<?> parent,
                                          List<AdditionalConnectionDataSource<?>> additionals,
                                          String oldParent)
   {
      Set<String> updated = new HashSet<>();
      Set<String> removed = new HashSet<>();
      // the new names of the renamed additional connections, by old name
      Map<String, String> renames = new LinkedHashMap<>();
      // the additional connections under the old parent name that this save doesn't keep. A kept
      // one is sent without its old name, a renamed one with it
      Set<String> oldRemoved = getAdditionalConnectionNames(oldParent);

      if(definition.getAdditionalConnections() != null) {
         for(DataSourceDefinition additional : definition.getAdditionalConnections()) {
            String oldName = additional.getOldName();
            oldRemoved.remove(oldName != null ? oldName : additional.getName());

            if(oldName != null && !Tool.equals(oldName, additional.getName())) {
               renames.put(oldName, additional.getName());
            }
         }
      }

      // read before the old names are removed below
      Map<String, Permission> renamedPermissions = getAdditionalPermissions(oldParent, renames);

      if(definition.getAdditionalConnections() != null) {
         for(int i = 0; i < additionals.size(); i++) {
            DataSourceDefinition additional = definition.getAdditionalConnections().get(i);
            updated.add(additional.getName());
            parent.addDatasource(additionals.get(i));
         }
      }

      for(String child : parent.getDataSourceNames()) {
         if(!updated.contains(child)) {
            parent.removeDatasource(child);
            removed.add(child);
         }
      }

      updateAdditionalPermissions(oldParent, parent.getFullName(), oldRemoved, removed, renames,
                                  renamedPermissions);
   }

   /**
    * Gets the names of the additional connections of a data source, read before they are saved.
    */
   private Set<String> getAdditionalConnectionNames(String path) {
      Set<String> names = new HashSet<>();

      if(path != null &&
         dataSourceRegistry.getDataSource(path) instanceof AdditionalConnectionDataSource<?> ads)
      {
         String[] children = ads.getDataSourceNames();

         if(children != null) {
            names.addAll(Arrays.asList(children));
         }
      }

      return names;
   }

   /**
    * Gets the permissions of the additional connections that are renamed, by old name.
    */
   private Map<String, Permission> getAdditionalPermissions(String oldParent,
                                                            Map<String, String> renames)
   {
      Map<String, Permission> permissions = new HashMap<>();

      if(oldParent == null || renames.isEmpty() || !hasPermissionStore()) {
         return permissions;
      }

      for(String oldName : renames.keySet()) {
         permissions.put(oldName, securityEngine.getPermission(ResourceType.DATA_SOURCE,
            oldParent + XUtil.ADDITIONAL_DS_CONNECTOR + oldName));
      }

      return permissions;
   }

   /**
    * Updates the permissions of the additional connections of a data source after they are
    * removed or renamed. The additional connections are saved under the new parent name before
    * the parent is renamed, and the rename moves the permissions of the additional connections
    * still under the old parent name. So the names that are removed or renamed are removed from
    * the old parent name, the removed ones also from the new parent name, and a renamed additional
    * connection gets its permission under the new parent name.
    *
    * @param oldParent   the full name of the parent before this save, or {@code null} if it is
    *                    created by this save.
    * @param newParent   the full name of the parent after this save.
    * @param oldRemoved  the names of the additional connections under the old parent name that
    *                    this save doesn't keep.
    * @param removed     the names of the additional connections removed from the new parent name.
    * @param renames     the new names of the renamed additional connections, by old name.
    * @param permissions the permissions of the renamed additional connections, by old name, read
    *                    before any of them was removed.
    */
   private void updateAdditionalPermissions(String oldParent, String newParent,
                                            Set<String> oldRemoved, Set<String> removed,
                                            Map<String, String> renames,
                                            Map<String, Permission> permissions)
   {
      if(oldParent == null || oldRemoved.isEmpty() && removed.isEmpty() && renames.isEmpty() ||
         !hasPermissionStore())
      {
         return;
      }

      for(String name : oldRemoved) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE,
            oldParent + XUtil.ADDITIONAL_DS_CONNECTOR + name);
      }

      for(String name : removed) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE,
            newParent + XUtil.ADDITIONAL_DS_CONNECTOR + name);
      }

      for(String oldName : renames.keySet()) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE,
            oldParent + XUtil.ADDITIONAL_DS_CONNECTOR + oldName);
      }

      // a kept additional connection is sent without its old name, so the new name of a renamed
      // one may be that of one removed in this save, and must not keep its permission
      for(String newName : renames.values()) {
         securityEngine.removePermission(ResourceType.DATA_SOURCE,
            newParent + XUtil.ADDITIONAL_DS_CONNECTOR + newName);
      }

      for(Map.Entry<String, String> rename : renames.entrySet()) {
         Permission permission = permissions.get(rename.getKey());

         if(permission != null) {
            securityEngine.setPermission(ResourceType.DATA_SOURCE,
               newParent + XUtil.ADDITIONAL_DS_CONNECTOR + rename.getValue(), permission);
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

   /**
    * Retrieves AssetEntry of a data source from the registry.
    *
    * @param oldEntry the asset entry we are trying to find.
    *
    * @return the asset entry from the repository.
    */
   public AssetEntry getDataSourceAssetEntry(AssetEntry oldEntry) {
      DataSourceRegistry registry = dataSourceRegistry;
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
         DataSourceRegistry registry = dataSourceRegistry;
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
   private final DataSourceRegistry dataSourceRegistry;
   private final Config uqlConfig;
   private final SecretIdAuthorizer secretIdAuthorizer;

   /**
    * A data source created from a client-supplied definition.
    *
    * @param dataSource       the data source, or {@code null} if it could not be created.
    * @param secretIdWithheld {@code true} if the secret id of the data source was not resolved
    *                         because the caller may not use it yet.
    * @param principal        the caller.
    */
   private record DraftDataSource(Object dataSource, boolean secretIdWithheld,
                                  Principal principal)
   {
   }

   private record AuthorizedDataSource(XDataSource dataSource,
                                       List<AdditionalConnectionDataSource<?>> additionalConnections)
   {
   }
   private static final Logger LOG = LoggerFactory.getLogger(DatasourcesBaseService.class);
}
