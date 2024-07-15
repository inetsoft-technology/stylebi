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

import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.*;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.TabularFileModel;
import inetsoft.web.composer.model.ws.TabularOAuthParams;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.notifications.NotificationService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.CheckDependenciesEvent;
import inetsoft.web.security.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DataSourceController {
   @Autowired
   public DataSourceController(DatasourcesService datasourcesService,
                               DataSourceBrowserService dataSourceBrowserService,
                               DatabaseDatasourcesService databaseDatasourcesService,
                               SecurityEngine securityEngine)
   {
      this.datasourcesService = datasourcesService;
      this.dataSourceBrowserService = dataSourceBrowserService;
      this.databaseDatasourcesService = databaseDatasourcesService;
      this.securityEngine = securityEngine;
   }

   /**
    * rename datasource folder.datasources
    * @param request request
    * @param principal user
    * @throws Exception rename failed.
    */
   @PostMapping("api/data/datasources/browser/folder")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE_FOLDER, actions = ResourceAction.WRITE
      )
   })
   public void renameDatasourceFolder(@RequestBody @PermissionPath("path()") RenameFolderRequest request,
                                        Principal principal)
      throws Exception
   {
      String fullPath = Util.getObjectFullPath(
         RepositoryEntry.DATA_SOURCE_FOLDER, request.path(), principal);
      String targetEntityPath = Util.getObjectFullPath(
         RepositoryEntry.DATA_SOURCE_FOLDER, request.name(), principal);
      dataSourceBrowserService.renameFolder(request.path(), request.name(),
         fullPath, targetEntityPath, principal);
   }

   @DeleteMapping("api/data/datasources/browser/folder/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE_FOLDER, actions = ResourceAction.DELETE
      )
   })
   public ConnectionStatus deleteDatasourceFolder(@RemainingPath @PermissionPath String path,
                                                  @RequestParam("force") boolean force,
                                                  Principal principal)
   {
      String fullPath = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, path, principal);
      return dataSourceBrowserService.deleteDataSourceFolder(path, fullPath, force, principal);
   }

   /**
    * Checks if target datasource folder has outer dependencies.
    * @return the dependencies exception string
    */
   @PostMapping("api/data/datasources/browser/folder/checkOuterDependencies")
   public StringWrapper checkDsFolderOuterDependencies(@RequestBody CheckDependenciesEvent event) {
      String path = event == null ? null : event.getDatasourceFolderPath();

      if(path == null) {
         return null;
      }

      try {
         datasourcesService.checkDataSourceFolderOuterDependencies(path);
      }
      catch(Exception ex) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(ex.getMessage());
         return wrapper;
      }

      return null;
   }

   /**
    * add a new data source folder.
    */
   @PostMapping("/api/data/datasources/browser/folder/add")
   public void addDatasourceFolder(
      @RequestBody @PermissionPath("parentPath()") AddFolderRequest request, Principal principal)
      throws Exception
   {
      String path;
      String parentPath = request.parentPath();

      if(parentPath == null || "".equals(parentPath) || "/".equals(parentPath)) {
         path = request.name();
      }
      else {
         path = parentPath + "/" + request.name();
      }

      String fullPath = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, path, principal);
      boolean rootFolder = StringUtils.isEmpty(parentPath) || "/".equals(parentPath);
      boolean hasWritePermission;
      boolean userScope = false;

      if(rootFolder) {
         hasWritePermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE);

         if(!hasWritePermission) {
            userScope = hasWritePermission = securityEngine.checkPermission(principal,
               ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         }
      }
      else {
         hasWritePermission = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, parentPath, ResourceAction.WRITE);
      }

      if(!hasWritePermission) {
         String parentFullPath =
            Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, parentPath, principal);

         throw new SecurityException(
            "Unauthorized access to resource \"" + parentFullPath + "\" by user " + principal);
      }

      dataSourceBrowserService.addDatasourceFolder(path, fullPath, principal, userScope);
   }

   /**
    * Check if datasource folder name is already present.
    */
   @PostMapping("/api/data/datasources/browser/folder/checkDuplicate")
   public CheckDuplicateResponse checkFolderDuplicate(
      @RequestBody @PermissionPath("path()") CheckDuplicateRequest request,
      @SuppressWarnings("unused") @PermissionUser Principal principal)
      throws Exception
   {
      String path;
      String parentPath = request.path();

      if(parentPath == null || "".equals(parentPath) || "/".equals(parentPath)) {
         path = request.newName();
      }
      else {
         path = parentPath + "/" + request.newName();
      }

      return dataSourceBrowserService.checkFolderDuplicate(path);
   }

   @GetMapping("api/data/datasources/browser")
   public DataSourceBrowserModel getDatasourcesBrowser(
         @RequestParam(value="path", required = false) String path,
         @RequestParam(value="root", required = false) boolean root,
         @RequestParam(value="moveFolders", required = false) String moveFolders,
         Principal principal)
         throws Exception
   {
      boolean newDatasourceEnabled = false;

      if(path == null) {
         newDatasourceEnabled = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE);
      }
      else {
         newDatasourceEnabled = securityEngine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, path, ResourceAction.WRITE);
      }

      if((root || path == null || path.isEmpty() || "/".equals(path)) && !newDatasourceEnabled) {
         newDatasourceEnabled = securityEngine.checkPermission(
            principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
      }

      String[] movingFolders = null;

      if(moveFolders != null) {
         movingFolders = moveFolders.split(";");
      }

      boolean isSelfOrg =
         principal instanceof SRPrincipal && ((SRPrincipal) principal).isSelfOrganization();
      boolean enterprise = LicenseManager.getInstance().isEnterprise();

      return DataSourceBrowserModel.builder()
         .dataSourceList(dataSourceBrowserService.getDataSources(path, root, movingFolders, principal))
         .currentFolder(dataSourceBrowserService.getBreadcrumbs(path, root, principal))
         .newDatasourceEnabled(newDatasourceEnabled)
         .newVpmEnabled(!isSelfOrg && enterprise)
         .root(path == null)
         .build();
   }

   @GetMapping("api/data/dataSources/list")
   public List<DataSourceInfo> getDataSource(Principal principal) throws Exception {
      return dataSourceBrowserService.getDataSources(null, false, null, principal);
   }

   @PostMapping("api/data/search/dataSources/names")
   public SearchDataSourceResultsModel getSearchDataSourceNames(
      @RequestBody SearchDataCommand command,
      Principal principal)
      throws Exception
   {
      SearchDataSourceResultsModel resultsModel = new SearchDataSourceResultsModel();
      List<DataSourceInfo> dataSourceInfos =
         dataSourceBrowserService.getSearchDataSources(command.getPath(), command.getQuery(), principal);
      String[] searchResultsList = dataSourceInfos.stream().map(DataSourceInfo::name)
         .toArray(String[]::new);
      resultsModel.setDataSourceNames(searchResultsList);

      return resultsModel;
   }

   @PostMapping("api/data/search/dataSources")
   public SearchDataSourceResultsModel getSearchDataSources(
      @RequestBody SearchDataCommand command,
      Principal principal)
      throws Exception
   {
      SearchDataSourceResultsModel resultsModel = new SearchDataSourceResultsModel();
      List<DataSourceInfo> dataSourceInfos =
         dataSourceBrowserService.getSearchDataSources(command.getPath(), command.getQuery(), principal);
      resultsModel.setDataSourceInfos(dataSourceInfos.toArray(new DataSourceInfo[0]));

      return resultsModel;
   }

   /**
    * Determines whether a connection can be made to the data source
    *
    * @param path data source name.
    */
   @GetMapping("/api/data/datasources/status/**")
   public DataSourceStatus getConnectionStatus(@RemainingPath() String path) throws Exception {
      return dataSourceBrowserService.getDataSourceConnectionStatus(path, true);
   }

   /**
    * Determines whether a connection can be made to the data sources.
    *
    * @param paths data source paths.
    */
   @PostMapping("/api/data/datasources/statuses")
   public List<DataSourceStatus> getConnectionStatuses(@RequestBody List<String> paths)
      throws Exception
   {
      return dataSourceBrowserService.getDataSourceConnectionStatuses(paths);
   }

   /**
    * Refreshes the meta-data of the data source
    *
    * @param path the path of the data source.
    */
   @GetMapping("/api/data/datasources/refresh/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.READ
      )
   })
   public void refreshDataSource(@RemainingPath @PermissionPath String path) {
      datasourcesService.clearDatasourceMetaData(path);
   }

   /**
    * Retrieve the definition of a data source connection.
    *
    * @param path path of data source connection.
    *
    * @return definition the data source definition.
    */
   @GetMapping("/api/portal/data/datasources/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.WRITE
      )
   })
   public DataSourceDefinition getDataSourceDefinition(@RemainingPath @PermissionPath String path,
                                                       Principal principal)
      throws Exception
   {
      return datasourcesService.getDataSourceDefinition(path, principal);
   }

   /**
    * Deletes a data source.
    *
    * @param path the path of the data source being deleted.
    */
   @DeleteMapping("/api/data/datasources/**")
   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.DATA_SOURCE, actions = ResourceAction.DELETE
      )
   })
   public ConnectionStatus deleteDataSource(@RemainingPath @PermissionPath String path,
                                            @RequestParam(value="name") String dataSourceName,
                                            @RequestParam(value="force") boolean force,
                                            Principal principal)
      throws Exception
   {
      DatabaseDefinition databaseDefinition = new DatabaseDefinition();
      databaseDefinition.setName(dataSourceName);
      String fullPath = this.databaseDatasourcesService.getDataSourceAuditPath(path, databaseDefinition, principal);
      return datasourcesService.deleteDataSource(path, fullPath, force);
   }

   /**
    * Deletes list of data sources
    *
    * @param request, list to delete
    */
   @PostMapping("/api/data/datasources/deleteDataSources")
   public void deleteDataSources(@RequestBody SelectedDataSourcesRequest request,
                                 Principal principal) throws Exception
   {

      for (SelectedDataSourceItem d : request.dataSources()) {
         DatabaseDefinition databaseDefinition = new DatabaseDefinition();
         databaseDefinition.setName(d.name());
         String fullPath = this.databaseDatasourcesService.getDataSourceAuditPath(d.path(), databaseDefinition, principal);
         datasourcesService.deleteDataSource(d.path(), fullPath, true);
      }

      for (SelectedDataSourceItem f : request.folders()) {
         String fullPath = Util.getObjectFullPath(RepositoryEntry.DATA_SOURCE_FOLDER, f.path(), principal);
         dataSourceBrowserService.deleteDataSourceFolder(f.path(), fullPath, true, principal);
      }
   }

   /**
    * Checks if selected DataSources has outer dependencies.
    * @return the dependencies exception string
    */
   @PostMapping("api/data/datasources/checkOuterDependencies/selected")
   public StringWrapper checkDsOuterDependenciesSelected(@RequestBody SelectedDataSourcesRequest request) {
      String dependencies = "";

      for(SelectedDataSourceItem f : request.folders()) {
         String path = f.path();

         if(path == null) {
            continue;
         }

         try {
            datasourcesService.checkDataSourceFolderOuterDependencies(path);
         }
         catch(Exception ex) {
            dependencies += "\n"+ex.getMessage();
         }
      }

      for(SelectedDataSourceItem d : request.dataSources()) {
         String dataSource = d.name();

         if(dataSource == null) {
            continue;
         }

         try {
            datasourcesService.checkDataSourceOuterDependencies(dataSource);
         }
         catch(Exception ex) {
            dependencies += "\n"+ex.getMessage();
         }
      }

      if (!"".equals(dependencies)) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(dependencies);
         return wrapper;
      }

      return null;
   }

   /**
    * Checks if target datasource have outer dependencies.
    * @return the dependencies exception string
    */
   @PostMapping("api/data/datasources/checkOuterDependencies")
   public StringWrapper checkOuterDependencies(@RequestBody CheckDependenciesEvent event) {
      String dataSource = event == null ? null : event.getDatabaseName();

      if(dataSource == null) {
         return null;
      }

      try {
         datasourcesService.checkDataSourceOuterDependencies(dataSource);
      }
      catch(Exception ex) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(ex.getMessage());
         return wrapper;
      }

      return null;
   }

   /**
    * Get tree model of root folder when new 'Text and Excel Files' in portal
    * @param property property. not needed for now
    * @param path relative path
    * @param datasource datasource. not needed for now
    */
   @PostMapping("api/portal/data/datasources/browser")
   public TreeNodeModel getRootFolder(
      @RequestParam(value="property") String property,
      @RequestParam(value="path") String path,
      @RequestBody DataSourceDefinition datasource,
      HttpServletRequest request,
      Principal principal) throws Exception
   {
      path = Tool.byteDecode(path);
      File[] fileRoots;
      boolean root = "/".equals(path);

      if(root) {
         // get root folders
         fileRoots = File.listRoots();

         if(fileRoots.length == 1) {
            path = fileRoots[0].getPath();

            if(path.endsWith(File.separator)) {
               path = path.substring(0, path.length() - 1);
            }

            fileRoots = fileRoots[0].listFiles();
            root = false;
         }
      }
      else {
         // get folders of relative to the path.
         fileRoots = FileSystemService.getInstance().getFile(path + File.separator).listFiles();
      }

      if(fileRoots == null) {
         return null;
      }

      TreeNodeModel.Builder rootNodeBuilder = TreeNodeModel.builder(); // root node
      List<TreeNodeModel> folderNodes = new ArrayList<>(); // children nodes

      for(File file : fileRoots) {
         // skip the file when this file is unreadable or hidden
         if(!Files.isReadable(file.toPath())
               // The root disk of windows(C/D/E..) always is hidden.
               || (System.getProperty("os.name").toLowerCase().startsWith("win")
                     ? !root && file.isHidden()
                     : file.isHidden())) {
            continue;
         }

         // just only need folder
         if(file.isDirectory()) {
            // The folder name of windows root disk(C/D/E..) always is "", so use path instead.
            String fileName = file.getName().isEmpty() ? file.getPath() : file.getName();
            // remove separator of end with. e.g. 'c:\' to 'c:'
            fileName = fileName.endsWith(File.separator)
                  ? fileName.substring(0, fileName.lastIndexOf(File.separator))
                  : fileName;
            TabularFileModel tabularFileModel = new TabularFileModel();
            tabularFileModel.setFolder(true);
            tabularFileModel.setPath(root ? fileName : path + File.separator + fileName);
            // ts use "/" to expand to node at init, so replace separator to '/'.
            tabularFileModel.setPath(tabularFileModel.getPath().replace(File.separator, "/"));
            tabularFileModel.setAbsolutePath(file.getAbsolutePath());

            TreeNodeModel treeNode = TreeNodeModel.builder()
                  .data(tabularFileModel)
                  .label(fileName)
                  .leaf(false)
                  .build();

            folderNodes.add(treeNode);
         }
      }

      TabularFileModel tabularFileModel = new TabularFileModel();
      tabularFileModel.setFolder(true);
      tabularFileModel.setPath(path);
      rootNodeBuilder.data(tabularFileModel);
      rootNodeBuilder.addChildren(folderNodes.toArray(new TreeNodeModel[0]));

      return rootNodeBuilder.build();
   }

   /**
    * Refreshes a tabular view.
    *
    * @param definition the data source definition.
    *
    * @return updated data source definition.
    */
   @PostMapping("/api/portal/data/datasources/refreshView")
//   @Secured(permissions = @RequiredPermission(ActionTypes.DATA_TAB))
   public DataSourceDefinition refreshTabularView(@RequestBody DataSourceDefinition definition,
                                                  HttpServletRequest request)
   {
      TabularUtil.setSessionId(request.getSession().getId());
      DataSourceDefinition def2 = datasourcesService.refreshTabularView(definition);
      def2.setSequenceNumber(definition.getSequenceNumber());

      UserMessage msg = CoreTool.getUserMessage();

      if(msg != null) {
         try {
            notificationService.sendNotification(msg.getMessage());
         }
         catch(Exception e) {
            LOG.info("Failed to send notification: ", e);
         }
      }

      return def2;
   }

   @PostMapping("/api/portal/data/datasources/oauth-params")
   public TabularOAuthParams getOAuthParameters(@RequestBody DataSourceOAuthParamsRequest request,
                                                HttpServletRequest httpRequest)
   {
      TabularUtil.setSessionId(httpRequest.getSession().getId());
      return datasourcesService.getOAuthParams(request);
   }

   @PostMapping("/api/portal/data/datasources/oauth-tokens")
   public DataSourceDefinition setOAuthTokens(@RequestBody DataSourceOAuthTokens tokens,
                                              HttpServletRequest request)
   {
      TabularUtil.setSessionId(request.getSession().getId());
      return datasourcesService.setOAuthTokens(tokens);
   }

   /**
    * Creates a new data source.
    *
    * @param definition the data source definition.
    */
   @PostMapping("/api/portal/data/datasources")
   //@Secured(permissions = @RequiredPermission(ActionTypes.DATA_TAB))
   public void createNewDataSource(
      @RequestBody DataSourceDefinition definition, Principal principal)
      throws Exception
   {
      datasourcesService.createNewDataSource(definition, principal);
   }

   /**
    * Check if datasource name is already present.
    *
    * @param name the name of the new datasource
    */
   @GetMapping("/api/portal/data/datasources/checkDuplicate/**")
   //@Secured(permissions = @RequiredPermission(ActionTypes.DATA_TAB))
   public boolean checkDatasourceDuplicate(@RemainingPath String name) throws Exception {
      return datasourcesService.checkDuplicate(name);
   }

   /**
    * Updates a data source.
    *
    * @param name       the name of the data source.
    * @param definition the new data source details.
    */
   @PutMapping("/api/portal/data/datasources/**")
   //@Secured(permissions = @RequiredPermission(ActionTypes.DATA_TAB))
   public void updateDataSource(
      @RemainingPath String name,
      @RequestBody DataSourceDefinition definition, Principal principal)
      throws Exception
   {
      datasourcesService.updateDataSource(name, definition, principal);
   }

   /**
    * Check if any item paths are already present.
    *
    * @param request the check items duplicate command
    */
   @PostMapping("api/data/datasources/move/checkDuplicate")
   public CheckDuplicateResponse checkItemsDuplicate(
      @RequestBody @PermissionPath("path()") CheckMoveDuplicateRequest request, Principal principal)
      throws Exception
   {
      return dataSourceBrowserService.checkItemsDuplicate(request.items(), request.path()
      );
   }

   @PostMapping("api/data/datasources/move")
   public MessageCommand moveDataSource(@RequestBody MoveCommand[] items, Principal principal)
      throws Exception
   {
      MessageCommand messageCommand = null;

      try {
         dataSourceBrowserService.moveDataSource(items, principal);
      }
      catch(Exception ex) {
         if(ex instanceof MessageException) {
            MessageException messageException = (MessageException) ex;

            messageCommand = new MessageCommand();
            messageCommand.setMessage(messageException.getMessage());
            messageCommand.setType(MessageCommand.Type
               .fromCode(messageException.getWarningLevel()));
         }
         else {
            throw ex;
         }
      }

      return messageCommand;
   }

   /**
    * Gets a tabular view from a listing
    */
   @GetMapping("/api/portal/data/datasources/listing/**")
   public DataSourceDefinition getDataSourceFromListing(
      @RemainingPath String listingName,
      HttpServletRequest request) throws Exception
   {
      TabularUtil.setSessionId(request.getSession().getId());
      return datasourcesService.getDataSourceFromListing(listingName);
   }

   @GetMapping("/api/data/datasources/physicalTable")
   public boolean getPhysicalTablePermission(Principal principal) {
      boolean physicalTable;

      try {
         physicalTable = SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);
      }
      catch(Exception ex) {
         physicalTable = false;
      }

      return physicalTable;
   }

   @GetMapping("/api/data/datasources/folderPermission")
   public SourcePermissionModel getFolderPermission(@DecodeParam("path") String folder,
                                                    Principal principal)
   {
      SourcePermissionModel model = new SourcePermissionModel();

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();
         model.setCanDelete(engine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.DELETE));
         boolean root = folder == null || folder.isEmpty() || "/".equals(folder);
         boolean newSourcePermission = root && securityEngine.checkPermission(
            principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         model.setWritable(newSourcePermission || engine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.WRITE));
         model.setReadable(engine.checkPermission(
            principal, ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.READ));
      }
      catch(Exception ignore) {
      }

      return model;
   }

   @PostMapping("/api/portal/data/datasources/grant-password")
   public Tokens getPasswordGrantResponse(@RequestBody TabularOAuthParams request) {
      return AuthorizationClient.doPasswordGrantAuth(
         request.user(), request.password(), request.clientId(), request.clientSecret(),
         request.scope(), request.tokenUri());
   }

   @GetMapping("/api/data/datasources/folder/**")
   public DataSourceInfo getDataSourceFolderInfo(@RemainingPath String path, Principal principal)
      throws Exception
   {
      return dataSourceBrowserService.getDataSourceFolder(path, principal);
   }

   private final DatasourcesService datasourcesService;
   private final DataSourceBrowserService dataSourceBrowserService;
   private final DatabaseDatasourcesService databaseDatasourcesService;
   private final SecurityEngine securityEngine;

   @Autowired
   private NotificationService notificationService;

   private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);
}
