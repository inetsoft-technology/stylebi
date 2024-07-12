/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.*;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.data.SearchDataCommand;
import inetsoft.web.portal.model.database.Condition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.viewsheet.*;
import org.apache.commons.io.FileExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint for managing virtual private models.
 */
@RestController
@Lazy
public class VPMController {
   public VPMController(DataRefModelFactoryService dataRefModelFactoryService,
                        DatabaseTreeService databaseTreeService,
                        DataSourceService dataSourceService, XRepository repository,
                        SecurityEngine securityEngine)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.databaseTreeService = databaseTreeService;
      this.dataSourceService = dataSourceService;
      this.repository = repository;
      this.securityEngine = securityEngine;
   }

   /**
    * Gets operations for vpm condition editor.
    *
    * @return the list of operations.
    *
    */
   @GetMapping(value = "/api/data/vpm/operations")
   public List<Operation> getOperations() {
      return ConditionUtil.getOperationList();
   }

   /**
    * Gets physical models of this database as tree nodes.
    *
    * @param database the name of the database being used.
    *
    * @return the names of the physical models as tree node models.
    *
    * @throws Exception if the database is not found.
    */
   @GetMapping(value = "/api/data/vpm/physicalModels/nodes")
   public List<TreeNodeModel> getPhysicalModelNameNodes(@RequestParam("database") String database,
                                                        Principal principal)
      throws Exception
   {
      List<String> names = getPhysicalModelNames(database, principal);
      return names.stream()
         .sorted()
         .map((name) ->
            TreeNodeModel.builder()
               .label(name)
               .data(name)
               .leaf(true)
               .type(DatabaseTreeNodeType.PHYSICAL_VIEW)
               .cssClass("action-color")
               .build()
         )
         .collect(Collectors.toList());
   }

   /**
    * Determines if this user has write access on this datasource.
    *
    * @param name data source name.
    */
   @GetMapping("/api/data/datasources/permissions/**")
   public boolean getParentPermissions(@RemainingPath String name, Principal principal)
      throws Exception
   {
      return dataSourceService.checkPermission(name, ResourceAction.WRITE, principal);
   }

   @GetMapping("/api/data/vpm/checkDuplicate")
   public boolean checkLogicalModelDuplicate(@RequestParam("database") String database,
                                             @RequestParam("name") String name)
      throws Exception
   {
      return dataSourceService.isUniqueModelName(database, name);
   }

   private List<String> getPhysicalModelNames(String database, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      List<String> physicalModels = new ArrayList<>();

      if(dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         Collections.addAll(physicalModels, dataModel.getPartitionNames());
      }

      return physicalModels;
   }

   /**
    * Gets a virtual private model.
    *
    * @param database the database name.
    * @param name     the model name.
    *
    * @return the virtual private model.
    *
    * @throws Exception if the model could not be obtained.
    */
   @GetMapping(value = "/api/data/vpm/models")
   public VPMDefinition getModel(@RequestParam("database") String database,
                                 @RequestParam("vpm") String name, Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(name);

      if(vpm == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      return createVPModel(vpm, database, principal);
   }

   /**
    * Gets the tables of a physical model.
    *
    * @param database the database name.
    * @param name     the model name.
    *
    * @return the list of table names.
    *
    * @throws Exception if the model could not be obtained.
    */
   @GetMapping(value = "/api/data/vpm/physicalModel/tables")
   public List<Column> getPhysicalModelTables(@RequestParam("database") String database,
                                              @RequestParam("tableName") String name,
                                              Principal principal)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      XPartition partition = dataModel.getPartition(name);

      if(partition == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      List<Column> results = new ArrayList<>();

      partition = partition.applyAutoAliases();
      Enumeration<XPartition.PartitionTable> tables =
         partition.getTables();

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable temp = tables.nextElement();

         //PartitionTable.PHYSICAL = 0
         if(temp.getType() == 0) {
            addFields(dataModel, partition, tables.nextElement().getName(), results);
         }
         else {
            addFields(dataModel, partition, temp.getName(), results);
         }
      }

      return results;
   }

   private void addFields(XDataModel dataModel, XPartition partition,
                          String tname, List<Column> results) throws Exception
   {
      String physicalTable = tname;
      boolean view = false;
      XNode node = null;
      XTypeNode[] cols = null;

      if(partition != null && partition.getAliasTable(tname, true) != null) {
         physicalTable = partition.getAliasTable(tname, true);
      }

      if(partition != null) {
         XPartition.PartitionTable partitionTable =
            partition.getPartitionTable(physicalTable);

         if(partitionTable != null && partitionTable.getType() == 1) {
            view = true;
            cols = partitionTable.getColumns();
         }
      }

      if(!view) {
         XRepository repository = XFactory.getRepository();
         JDBCDataSource jdx = (JDBCDataSource) dataSourceService.getDataSource(
            dataModel.getDataSource());
         DefaultMetaDataProvider metaData =
            dataSourceService.getDefaultMetaDataProvider(jdx, dataModel);
         node = metaData.getTable(physicalTable, XUtil.OUTER_MOSE_LAYER_DATABASE);
         XAgent agent = XAgent.getAgent(XDataSource.JDBC);
         cols = agent.getColumns(physicalTable, jdx, metaData.getSession());
      }

      for(int j = 0; cols != null && j < cols.length; j++) {
         if(view || node != null && node.getChild(cols[j].getName()) == null) {
            Column c = new Column();
            c.setName(tname + "." + cols[j].getName());
            c.setColumnName(cols[j].getName());
            c.setTableName(tname);
            c.setType(cols[j].getType());
            results.add(c);
         }
      }
   }

   /**
    * Gets the path of the selected physical model table.
    *
    * @param database         the database name.
    * @param tableNameWrapper the table name.
    *
    * @return the path of the selected table
    *
    * @throws Exception if the model could not be obtained.
    */
   @PostMapping("/api/data/vpm/physicalModel/tablePath/**")
   public String getTablePath(@RemainingPath String database,
                              @RequestBody StringWrapper tableNameWrapper)
      throws Exception
   {
      String tableName = tableNameWrapper.getBody();
      XDataModel dataModel = repository.getDataModel(database);
      XNode node;

      JDBCDataSource jdx = (JDBCDataSource) dataSourceService.
         getDataSource(dataModel.getDataSource());
      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(jdx, dataModel);

      node = metaData.getTable(tableName, XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(node != null) {
         String tablePath = node.getPath();

         if(tablePath != null) {
            return String.join("/", tablePath.split("\\."));
         }
      }

      return null;
   }

   /**
    * Adds a model to a database.
    *
    * @param event save vpm event.
    *
    * @throws Exception if the database could not be added.
    */
   @PostMapping(value = "/api/data/vpm/models")
   @ResponseStatus(HttpStatus.CREATED)
   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   public void addModel(@RequestBody
                        @AuditObjectName("getDatabase() + '/' + getModel().getName()")
                        SaveVpmEvent event,
                        Principal principal) throws Exception
   {
      VPMDefinition model = event.getModel();
      String database = event.getDatabase();
      XDataModel dataModel = repository.getDataModel(database);
      String path = database + "/" + model.getName();

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(dataModel.getVirtualPrivateModel(model.getName()) != null) {
         throw new FileExistsException(path);
      }

      VirtualPrivateModel vpm = createVPM(model);
      dataModel.addVirtualPrivateModel(vpm, true);
      repository.updateDataModel(dataModel);
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM,
                                        path, null);
      entry = dataSourceService.getModelAssetEntry(entry);
      String createdUsername = principal.getName() != null ?
         IdentityID.getIdentityIDFromKey(principal.getName()).getName() : null;
      entry.setCreatedUsername(createdUsername);
      dataSourceService.updateDataSourceAssetEntry(entry);
      DependencyHandler dependencyHandler = DependencyHandler.getInstance();
      dependencyHandler.updateVPMDependencies(vpm, null, database, entry);
   }

   /**
    * @param event   rename event.
    * @throws Exception if the model could not be renamed.
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_RENAME,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   @PutMapping(value = "/api/data/vpm/rename")
   public void renameModel(@RequestBody
                           @AuditObjectName("getDatabase() + '/' + getOldName()")
                           @AuditActionError("'Target Entry: ' + getDatabase() + '/' + getOldName()")
                           RenameModelEvent event,
                           Principal principal)
      throws Exception
   {
      String database = event.getDatabase();
      String oldName = event.getOldName();
      String newName = event.getNewName();
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      VirtualPrivateModel vpm = dataModel.getVirtualPrivateModel(oldName);

      if(vpm == null) {
         throw new FileNotFoundException(oldName);
      }

      String path = database + "/" + vpm.getName();
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);

      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();

      vpm.setName(newName);
      vpm.setDescription(event.getDescription());
      dataModel.renameVirtualPrivateModel(oldName, vpm);
      repository.updateDataModel(dataModel);
      String dataSource = dataModel.getDataSource();
      String oldPath = dataSource + "/" + oldName;
      String newPath = dataSource + "/" + newName;
      RenameInfo rinfo = new RenameInfo(oldPath, newPath, RenameInfo.VPM);
      path = database + "/" + newName;
      AssetEntry nentry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);
      DependencyHandler.getInstance().updateDependencies(entry, nentry);
      RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
      nentry = dataSourceService.getModelAssetEntry(nentry);

      nentry.setCreatedUsername(user);
      nentry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(nentry);
   }

   /**
    * Updates a virtual private model.
    *
    * @param event save vpm event.
    *
    * @throws Exception if the model could not be updated.
    */
   @PutMapping(value = "/api/data/vpm/models")
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   public void updateModel(@RequestBody @AuditObjectName("getDatabase() + '/' + getVpmName()")
                           SaveVpmEvent event,
                           Principal principal)
      throws Exception
   {
      String database = event.getDatabase();
      String name = event.getVpmName();
      VPMDefinition vpm = event.getModel();
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(dataModel.getVirtualPrivateModel(name) == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      String path = database + "/" + vpm.getName();
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);

      entry = dataSourceService.getModelAssetEntry(entry);
      String user = entry.getCreatedUsername();
      Date date = entry.getCreatedDate();
      VirtualPrivateModel ovpm = dataModel.getVirtualPrivateModel(name);
      dataModel.removeVirtualPrivateModel(name);
      VirtualPrivateModel nvpm = createVPM(vpm);
      dataModel.addVirtualPrivateModel(nvpm, true);
      repository.updateDataModel(dataModel);
      DependencyHandler dependencyHandler = DependencyHandler.getInstance();
      dependencyHandler.updateVPMDependencies(nvpm, ovpm, database, entry);

      entry = dataSourceService.getModelAssetEntry(entry);

      entry.setCreatedUsername(user);
      entry.setCreatedDate(date);
      dataSourceService.updateDataSourceAssetEntry(entry);
   }

   /**
    * Deletes a virtual private model.
    *
    * @param event  remove event.
    *
    * @throws Exception if the model could not be removed.
    */
   @PostMapping(value = "/api/data/vpm/remove")
   public void removeModel(@RequestBody RemoveDataModelEvent event,
                           Principal principal)
      throws Exception
   {
      String database = event.getDatabase();
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.WRITE, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      if(event.getItems() == null) {
         return;
      }

      for(AssetItem item : event.getItems()) {
         if(!(item instanceof Vpm)) {
            continue;
         }

         String name = item.getName();
         removeVPM(dataModel, database, name);
         String path = database + "/" + name;
         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM, path, null);
         DependencyHandler.getInstance().deleteDependencies(entry);
      }

      repository.updateDataModel(dataModel);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL
   )
   private void removeVPM(XDataModel dataModel, @AuditObjectName String database,
                          @AuditObjectName String name)
      throws Exception
   {

      if(dataModel.getVirtualPrivateModel(name) == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      dataModel.removeVirtualPrivateModel(name);
   }

   /**
    * Gets the users and roles for purposes of testing.
    *
    * @return the users and roles data.
    *
    */
   @GetMapping(value = "/api/data/vpm/usersRoles")
   public TestData getUsersRoles(Principal principal) {
      IdentityID[] users = securityEngine.getOrgUsers(((XPrincipal) principal).getOrgId());
      Role[] roles = securityEngine.getRolesOrgScoped(OrganizationManager.getInstance().isSiteAdmin(principal));

      if(!SUtil.isMultiTenant()) {
         roles = Arrays.stream(roles).filter(r ->
                !securityEngine.getSecurityProvider().isOrgAdministratorRole(r.getIdentityID()))
            .toArray(Role[]::new);
      }

      if(Organization.getSelfOrganizationID().equals(((XPrincipal) principal).getOrgId())) {
         users = new IdentityID[] { IdentityID.getIdentityIDFromKey(principal.getName()) };
      }

      List<DataItem> usersData = new ArrayList<>();
      List<DataItem> rolesData = new ArrayList<>();

      if(users != null) {
         for(IdentityID user : users) {
            usersData.add(new DataItem(user.name, user.name));
         }
      }

      if(roles != null) {
         for(Role role : roles) {
            rolesData.add(new DataItem(role.getName(), role.getName()));
         }
      }

      usersData.sort(new DefaultComparator());
      rolesData.sort(new DefaultComparator());

      TestData td = new TestData();
      td.setUsers(usersData);
      td.setRoles(rolesData);

      return td;
   }

   /**
    * Gets the results of testing.
    *
    * @param event  test event.
    *
    * @return the test results as a string.
    *
    * @throws Exception if an error prevents data from being obtained.
    */
   @PostMapping(value = "/api/data/vpm/test")
   public String test(@RequestBody VpmTestEvent event) throws Exception {
      String database = event.getDatabase();
      String type = event.getType();
      String name = event.getName();
      VPMDefinition vpModel = event.getVpm();
      SecurityProvider provider = securityEngine.getSecurityProvider();

      IdentityID user;
      IdentityID[] roles;
      String[] groups;
      String orgID = null;
      String userGroup;
      ArrayList<String> groupList = new ArrayList<>(0);

      if("user".equals(type)) {
         user = new IdentityID(name, OrganizationManager.getCurrentOrgName());
         groups = SUtil.getGroups(user);

         if(groups.length != 0) {
            userGroup = groups[0];
            Group currentGroup = provider.getGroup(new IdentityID(userGroup, user.organization));
            String[] parent = currentGroup.getGroups();
            groupList.add(userGroup);

            while (parent.length != 0) {
               groupList.add(parent[0]);
               currentGroup = provider.getGroup(new IdentityID(parent[0], currentGroup.getOrganization()));
               parent = currentGroup.getGroups();
            }

            groups = new String[groupList.size()];
            groups = groupList.toArray(groups);
         }

         roles = securityEngine.getRoles(user);
         User securityUser =  provider.getUser(new IdentityID(name, OrganizationManager.getCurrentOrgName()));

         if(securityUser != null) {
            orgID = securityUser.getOrganization();
         }
      }
      else {
         user = null;
         groups = new String[0];
         roles = new IdentityID[]{new IdentityID(name, OrganizationManager.getCurrentOrgName())};
      }

      user = user == null ? new IdentityID(Identity.UNKNOWN_USER, OrganizationManager.getCurrentOrgName()) : user;
      roles = roles == null ? new IdentityID[0] : roles;
      XDataModel dataModel = repository.getDataModel(database);
      Catalog catalog = Catalog.getCatalog();
      VirtualPrivateModel vpm = createVPM(vpModel);
      String result = null;

      try {
         result = VpmProcessor.getInstance()
            .testVPM(user, groups, roles, orgID, dataModel, catalog, vpm);
      }
      catch(Exception ex) {
         if(!(ex instanceof MessageException)) {
            throw new MessageException(ex.getMessage(), ex);
         }
      }

      return result;
   }

   /**
    * Gets the columns of a table.
    *
    * @param database     the database that contains the table.
    * @param tnameWrapper the name of the table.
    *
    * @return the column names.
    *
    * @throws Exception if the columns could not be obtained.
    */
   @PostMapping(value = "/api/data/vpm/columns/**")
   public List<Column> getTableColumns(@RemainingPath String database,
                                       @RequestBody StringWrapper tnameWrapper,
                                       Principal principal)
      throws Exception
   {
      String tname = tnameWrapper.getBody();
      List<Column> results = new ArrayList<>();

      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      if(!dataSourceService.checkPermission(database, ResourceAction.READ, principal)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + database + "\" by user " +
               principal);
      }

      addFields(dataModel, null, tname, results);

      return results;
   }

   /**
    * Gets the vpms defined for the specified database.
    *
    * @param database the name of the database.
    *
    * @return the models.
    *
    * @throws Exception if an error prevents the models from being listed.
    */
   @GetMapping(value = "/api/data/vpm/browse/**")
   public AssetListBrowseModel getModels(@RemainingPath String database, Principal principal)
      throws Exception
   {
      return dataSourceService.getVpmBrowseModel(database, principal);
   }

   /**
    * Search the vpm names defined for the specified database.
    * @return the models.
    */
   @PostMapping(value = "/api/data/vpm/search/names")
   public AssetListBrowseModel getSearchModelNames(@RequestBody SearchDataCommand command,
                                                   Principal principal)
      throws Exception
   {
      return dataSourceService.getSearchVpmBrowseModelNames(command.getDatabase(), command.getQuery(), principal);
   }

   /**
    * Search the vpms defined for the specified database.
    * @return the models.
    */
   @PostMapping(value = "/api/data/vpm/search")
   public AssetListBrowseModel getSearchModels(@RequestBody SearchDataCommand command,
                                               Principal principal)
      throws Exception
   {
      return dataSourceService.getSearchVpmBrowseModel(command.getDatabase(), command.getQuery(), principal);
   }


   @PostMapping(value = "/api/data/vpm/hiddenColumn/tree")
   public List<TreeNodeModel> getAvailableTreeNodes(@RequestBody DatabaseTreeNode pnode,
                                                    Principal principal)
      throws Exception
   {
      List<DatabaseTreeNode> nodes = new ArrayList<>();
      String parentPath = pnode.getPath();

      if(!databaseTreeService.isAliasNode(parentPath)) {
         nodes =
            this.databaseTreeService.getDatabaseNodes(parentPath, true, true,
               principal);
      }

      nodes.addAll(this.databaseTreeService.getAlias(pnode));

      return nodes.stream()
         .map((node) ->TreeNodeModel.builder()
            .label(node.getName())
            .data(node)
            .leaf(DatabaseTreeNodeType.COLUMN.equals(node.getType()))
            .type(node.getType())
            .cssClass("action-color")
            .build())
         .collect(Collectors.toList());
   }

   @GetMapping(value = "/api/data/vpm/hiddenColumn/fullTree/**")
   public FullDataBaseTreeModel getAvailableTree(@RemainingPath String database, Principal principal)
      throws Exception
   {
      final FullDataBaseTreeModel model = new FullDataBaseTreeModel();
      final Function<Boolean, Boolean> timeOutFunc = timeout -> {
         model.setTimeOut(timeout);

         return timeout;
      };

      List<TreeNodeModel> fullTree = this.databaseTreeService.getFullDatabaseTree(
         database, database.replace("/", AssetEntry.PATH_ARRAY_SEPARATOR), "",
         true, true, principal, timeOutFunc);

      fullTree.add(this.databaseTreeService.getAllAlias(database, principal));
      model.setNodes(fullTree);

      return model;
   }

   /**
    * Gets the nodes for the data source tree
    *
    * @return the updated model.
    */
   @PostMapping("/api/data/vpm/sql-query-dialog/data-source-tree")
   public TreeNodeModel getDataSourceTreeNode(
      @RequestParam("dataSource") String dataSource,
      @RequestBody(required = false) AssetEntry expandedEntry,
      Principal principal) throws Exception
   {
      List<TreeNodeModel> children;
      AssetRepository assetRepository = getAssetRepository();

      if(expandedEntry == null) {
         children = new ArrayList<>();
         AssetEntry query = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.FOLDER, "/", null);
         AssetEntry dsEntry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, dataSource, null);
         AssetEntry dsFolder = dsEntry.getParent();

         if(!dsFolder.isRoot()) {
            query.setProperty("prefix", dsFolder.getPath());
         }

         AssetEntry.Selector selector = new AssetEntry.Selector(
            AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);
         AssetEntry[] dataSourceEntries = assetRepository.getEntries(query,
            principal, ResourceAction.READ, selector);

         for(AssetEntry dataSourceEntry : dataSourceEntries) {
            if(!dataSourceEntry.getPath().equals(dataSource)) {
               continue;
            }

            selector = new AssetEntry.Selector(AssetEntry.Type.DATA,
               AssetEntry.Type.PHYSICAL);
            AssetEntry[] entries = assetRepository.getEntries(dataSourceEntry,
               principal, ResourceAction.READ, selector);

            for(AssetEntry entry : entries) {
               if(entry.getType() == AssetEntry.Type.PHYSICAL_FOLDER) {
                  children.add(TreeNodeModel.builder()
                     .label(entry.getName())
                     .data(entry)
                     .build());
               }
            }

            break;
         }
      }
      else {
         children = getDataSourceChildren(expandedEntry, assetRepository, principal);
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   @PostMapping("/api/data/vpm/sql-query-dialog/table-columns")
   @ResponseBody
   public AssetEntry[] getTableColumns(@RequestBody AssetEntry tableEntry, Principal principal) throws Exception {
      return getAssetRepository().getEntries(tableEntry, principal, ResourceAction.READ);
   }

   /**
    * Browses the available data for the given data ref
    *
    * @return the updated model.
    */
   @PostMapping("/api/data/vpm/sql-query-dialog/browse-data")
   @ResponseBody
   public BrowseDataModel browseData(@RequestParam("dataSource") String dataSource,
                                     @RequestBody ColumnRefModel dataRefModel,
                                     Principal principal)
      throws Exception
   {
      BrowseDataModel dataModel = null;
      DataRef dataRef = dataRefModel.createDataRef();

      XQuery query = new JDBCQuery();
      query.setName(dataSource);
      XDataSource xds = XFactory.getRepository().getDataSource(dataSource);
      query.setDataSource(xds);
      BrowsedData browsedData = new BrowsedData(query, dataRef.getEntity(), dataRef.getAttribute(),
         dataRef.getDataType(), null, null, false, true);
      Object[][] browsedResult = browsedData.getBrowsedData();

      if(browsedResult != null && browsedResult.length > 1) {
         dataModel = BrowseDataModel.builder().values(browsedResult[1]).build();

      }

      return dataModel;
   }

   private List<TreeNodeModel> getDataSourceChildren(AssetEntry expandedEntry, AssetRepository assetRepository,
                                                     Principal principal)
      throws Exception
   {
      List<TreeNodeModel> children = new ArrayList<>();
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.DATA,
         AssetEntry.Type.PHYSICAL);
      AssetEntry[] entries = assetRepository.getEntries(expandedEntry,
         principal, ResourceAction.READ, selector);

      for(AssetEntry entry : entries) {
         TreeNodeModel.Builder child = TreeNodeModel.builder()
            .label(entry.getName())
            .data(entry)
            .leaf(entry.isColumn());

         if(!entry.getType().isActualFolder()) {
            child.dragName(entry.getType().toString());
         }

         children.add(child.build());
      }

      return children;
   }

   private AssetRepository getAssetRepository() {
      AssetRepository rep = null;

      try {
         rep = AssetUtil.getAssetRepository(false);
      }
      catch(Exception ex) {
         LOG.debug("Failed to get asset repository", ex);
      }

      return rep;
   }

   /**
    * Creates a model DTO structure from a virtual private model.
    *
    * @param vpm the virtual private model.
    *
    * @return the model.
    */
   private VPMDefinition createVPModel(VirtualPrivateModel vpm, String datasource, Principal principal) {
      VPMDefinition result = new VPMDefinition();
      result.setName(vpm.getName());
      result.setDescription(vpm.getDescription());

      List<Condition> conditions = new ArrayList<>();

      for(Enumeration<?> e = vpm.getConditions(); e.hasMoreElements();) {
         VpmCondition condition = (VpmCondition) e.nextElement();
         XFilterNode xfn = condition.getCondition();

         HierarchyList hl = new FilterList();

         for(HierarchyItem item :
            XUtil.constructConditionList(xfn))
         {
            hl.append(item);
         }

         hl.validate(false);

         Condition c = new Condition();
         c.setName(condition.getName());

         List<DataConditionItem> clauses = new ArrayList<>();
         XDataSource xDataSource = dataSourceService.getDataSource(datasource);

         for(int i = 0; i < hl.getSize(); i++) {
            HierarchyItem hi = hl.getItem(i);
            int level = hi.getLevel();

            if(hi instanceof XSetItem) {
               Conjunction con = new Conjunction();

               XSet xset = ((XSetItem) hi).getXSet();

               con.setLevel(level);
               con.setValue(xset.toString());
               con.setIsNot(xset.isIsNot());
               con.setConjunction(xset.getRelation());
               con.setJunc(true);
               clauses.add(con);
            }
            else {
               XFilterNode xfilter = ((XFilterNodeItem) hi).getNode();

               Clause clause = JDBCUtil.createCondition(xfilter, xDataSource);
               clause.setLevel(level);
               clauses.add(clause);
            }
         }

         c.setScript(condition.getScript());
         c.setType(condition.getType());
         c.setTableName(condition.getTable());
         c.setClauses(clauses);

         conditions.add(c);
      }

      result.setConditions(conditions);

      try {
         HiddenColumns hc = vpm.getHiddenColumns();
         HiddenColumnsModel hcModel = new HiddenColumnsModel();
         hcModel.setScript(hc.getScript());
         hcModel.setName(hc.getName());
         Enumeration<?> roles = hc.getRoles();
         List<String> roleModels = new ArrayList<>();

         while(roles.hasMoreElements()) {
            roleModels.add(String.valueOf(roles.nextElement()));
         }

         hcModel.setRoles(roleModels);
         Enumeration<?> hiddenColumns = hc.getHiddenColumns();
         List<DataRefModel> hiddenColumnModels = new ArrayList<>();

         while(hiddenColumns.hasMoreElements()) {
            DataRef hiddenColumn = (DataRef) hiddenColumns.nextElement();
            hiddenColumnModels.add(dataRefModelFactoryService.createDataRefModel(hiddenColumn));
         }

         hcModel.setHiddens(hiddenColumnModels);
         result.setHidden(hcModel);
      }
      catch(Exception ignore) {
         // No script
      }

      String lookupScript = vpm.getScript();

      result.setLookup(lookupScript);

      return result;
   }

   private AssetEntry getDataSourceEntry(XDataSource dataSource) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.DATA_SOURCE, dataSource.getFullName(), null);
      entry.setProperty(AssetEntry.PATH_ARRAY,
         dataSource.getFullName().replaceAll("/", "^_^"));
      entry.setProperty("prefix", dataSource.getFullName());
      entry.setProperty("source", dataSource.getFullName());
      entry.setProperty("description", dataSource.getDescription());
      entry.setProperty(
         AssetEntry.DATA_SOURCE_TYPE, dataSource.getType());
      entry.setProperty("mainType", "data source");
      entry.setProperty("subType", dataSource.getType());

      return entry;
   }

   private AssetEntry searchPhysicalTable(AssetEntry dataSourceEntry, AssetRepository assetRepository,
                                          SelectTable table, Principal principal)
      throws Exception
   {
      AssetEntry.Selector selector = new AssetEntry.Selector(
         AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);
      AssetEntry[] entries = assetRepository.getEntries(dataSourceEntry,
         principal, ResourceAction.READ, selector);

      for(AssetEntry entry : entries) {
         if(entry.getType() == AssetEntry.Type.PHYSICAL_FOLDER) {
            AssetEntry tableEntry = searchTable(entry, assetRepository, table, principal);

            if(tableEntry != null) {
               return tableEntry;
            }
         }
      }

      return null;
   }

   private AssetEntry searchTable(AssetEntry parentEntry, AssetRepository assetRepository, SelectTable table,
                                  Principal principal)
      throws Exception
   {
      AssetEntry.Selector selector = new AssetEntry.Selector(AssetEntry.Type.DATA,
         AssetEntry.Type.PHYSICAL);
      AssetEntry[] entries = assetRepository.getEntries(parentEntry, principal, ResourceAction.READ, selector);
      String schema = table.getSchema();

      for(AssetEntry entry : entries) {
         String entrySchema = entry.getProperty(XSourceInfo.SCHEMA);

         if(entry.getType() == AssetEntry.Type.PHYSICAL_FOLDER && Tool.equals(entrySchema, schema)) {
            return searchTable(entry, assetRepository, table, principal);
         }
         else if(entry.getType() == AssetEntry.Type.PHYSICAL_TABLE && Tool.equals(entry.getProperty("source"), table.getName())) {
            return entry;
         }
      }

      return null;
   }

   /**
    * Convert the expression value to string.
    * @param value expression value.
    * @return
    */
   private String convertExpressionToString(Object value) {
      if(value instanceof UniformSQL) {
         return ((UniformSQL) value).getSQLString();
      }
      else if(value instanceof String[]) {
         return String.join(",", ((String[]) value));
      }

      return (String) value;
   }

   /**
    * Convert the expression string to value.
    * @param value string value.
    * @param type expression type.
    * @return
    */
   private Object convertStringToExpressionValue(String value, String type) {
      if(XExpression.SUBQUERY.equals(type)) {
         return new UniformSQL(value, false);
      }

      return value;
   }

   /**
    * Since the Session Data and Variable types get stored as Expression types, this method replaces Expression type with appropriate type.
    *
    * @param value the value of the expression.
    * @param type  the type of the expression.
    *
    * @return real type name.
    */
   private String correctType(Object value, String type) {
      if(value instanceof String) {
         String strValue = (String) value;
         boolean isSession = type.equals(XExpression.EXPRESSION) && ("$(_USER_)".equals(strValue) ||
            "$(_GROUPS_)".equals(value) ||  "$(_ROLES_)".equals(strValue));
         boolean isVariable = type.equals(XExpression.EXPRESSION) && strValue.length() >= 3 &&
            strValue.startsWith("$(");

         if(isSession) {
            return "Session Data";
         }
         else if(isVariable) {
            return XExpression.VARIABLE;
         }
         else {
            return type;
         }
      }

      return type;
   }

   /**
    * Removes $( from variable names.
    *
    * @param value the value of the expression.
    * @param type  the type of the expression.
    *
    * @return converted value.
    */
   private String correctValue(String value, String type) {
      if(type.equals(XExpression.VARIABLE)) {
         return value.substring(2, value.length()-1);
      }
      else {
         return value;
      }
   }

   /**
    * Creates a virtual private model from a model DTO structure.
    *
    * @param model the model to convert.
    *
    * @return the vpm object.
    */
   private VirtualPrivateModel createVPM(VPMDefinition model) throws Exception {
      VirtualPrivateModel vpm = new VirtualPrivateModel(model.getName());
      List<Condition> conditions = model.getConditions();
      vpm.setDescription(model.getDescription());

      for(Condition c : conditions) {
         List<DataConditionItem> clauses = c.getClauses();
         HierarchyList hl = new FilterList();
         HierarchyListModel clm = new HierarchyListModel(hl);

         for(DataConditionItem ci : clauses) {
            if(ci instanceof Clause) {
               XFilterNode xfn = JDBCUtil.createXFilterNode((Clause) ci);
               XFilterNodeItem xfni = new XFilterNodeItem(xfn, ci.getLevel());
               clm.append(xfni);
            }
            else if(ci instanceof Conjunction) {
               XSet xs = new XSet(((Conjunction) ci).getConjunction());
               xs.setIsNot(((Conjunction) ci).isIsNot());
               XSetItem xsi = new XSetItem(xs, ci.getLevel());
               clm.append(xsi);
            }
         }

         clm.fixConditions();

         ConditionListHandler handler = new ConditionListHandler();
         XFilterNode header = handler.createXFilterNode(clm.getHierarchyList());

         VpmCondition newCondition = new VpmCondition();
         newCondition.setName(c.getName());
         newCondition.setCondition(header);
         newCondition.setScript(c.getScript());
         newCondition.setType(c.getType());
         newCondition.setTable(c.getTableName());

         vpm.addCondition(newCondition);
      }

      HiddenColumnsModel hcModel = model.getHidden();

      if(hcModel != null) {
         HiddenColumns hc = new HiddenColumns();
         List<DataRefModel> hiddens = hcModel.getHiddens();

         if(hiddens != null && hiddens.size() > 0) {
            hiddens.forEach(hidden -> hc.addHiddenColumn(hidden.createDataRef()));
         }

         List<String> roles = hcModel.getRoles();

         if(roles != null && roles.size() > 0) {
            roles.forEach(hc::addRole);
         }

         hc.setScript(hcModel.getScript());
         vpm.setHiddenColumns(hc);
      }

      vpm.setScript(model.getLookup());

      return vpm;
   }

   /**
    * Converts Variable type values to variable string format.
    *
    * @param value the value of the expression.
    * @param type  the type of the expression.
    *
    * @return converted name.
    */
   private String checkValue(String value, String type) {
      if(type.equals(XExpression.VARIABLE)) {
         return "$(" + value + ")";
      }
      else {
         return value;
      }
   }

   /**
    * Gets data to put in the browser/suggestions.
    *
    * @param data the information required to get browser data.
    *
    * @return list of browser suggestions.
    */
   @PostMapping(value = "/api/data/vpm/browserData")
   public List<String> getBrowserData(@RequestBody BrowserData data) throws Exception {
      List<String> results = new ArrayList<>();
      XDataModel dataModel = repository.getDataModel(data.getDatabase());
      String tableName = data.getTableName();
      String column = data.getColumnName();
      String type = data.getColumnType();

      if(data.getPartition() != null) {
         XPartition partition = dataModel.getPartition(data.getPartition());
         partition = partition.applyAutoAliases();

         if(partition.getAliasTable(tableName, false) != null) {
            tableName = partition.getAliasTable(tableName, false);
         }

         for(Enumeration<XPartition.PartitionTable> e = partition.getTables(); e.hasMoreElements();) {
            XPartition.PartitionTable table = e.nextElement();

            if(table.getName().equals(tableName) && !table.getSql().isEmpty()) {
               tableName = table.getSql();
               break;
            }
         }
      }

      JDBCDataSource jdx = (JDBCDataSource) dataSourceService.
         getDataSource(dataModel.getDataSource());

      JDBCQuery query = new JDBCQuery();
      UniformSQL sql = createUniformSQL(tableName, column);
      query.setSQLDefinition(sql);
      query.setDataSource(jdx);

      ColumnCache cache = ColumnCache.getColumnCache();
      String[][] bdata = cache.getColumnDataString(
         query, tableName, column, type, null, null, true);

      for(int i = 0; i < bdata[0].length; i++) {
         String val = bdata[0][i];

         if(val != null) {
            results.add(val);
         }
      }

      return results;
   }

   /**
    * {@link JDBCAgent#getQueryData}
    *
    * Note: case: Bug #44293: column name contains '.'
    */
   private UniformSQL createUniformSQL(String tableName, String column) {
      UniformSQL sql = new UniformSQL();

      sql.addTable(tableName);

      JDBCSelection selection = new JDBCSelection();
      selection.addColumn(column);
      selection.setTable(column, tableName);

      sql.setSelection(selection);

      return sql;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(VPMController.class);

   @SuppressWarnings({ "unused", "WeakerAccess" })
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static final class TestData {
      public void setUsers(List<DataItem> users) {
         this.users = users;
      }

      public List<DataItem> getUsers() {
         if(users == null) {
            users = new ArrayList<>();
         }

         return users;
      }

      public void setRoles(List<DataItem> roles) {
         this.roles = roles;
      }

      public List<DataItem> getRoles() {
         if(roles == null) {
            roles = new ArrayList<>();
         }

         return roles;
      }

      private List<DataItem> users;
      private List<DataItem> roles;
   }

   public static final class DataItem implements Comparable<DataItem> {
      public DataItem(String label, String value) {
         this.label = label;
         this.value = value;
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }

      @Override
      public int compareTo(VPMController.DataItem other) {
         if(other == this) {
            return 0;
         }

         if(other == null) {
            return 1;
         }

         return Tool.compare(label, other.label);
      }

      private String label;
      private String value;
   }

   @SuppressWarnings({ "WeakerAccess", "unused" })
   public static final class BrowserData {
      public String getDatabase() {
         return database;
      }

      public void setDatabase(String database) {
         this.database = database;
      }

      public String getPartition() {
         return partition;
      }

      public void setPartition(String partition) {
         this.partition = partition;
      }

      public String getColumnName() {
         return columnName;
      }

      public void setColumnName(String columnName) {
         this.columnName = columnName;
      }

      public String getTableName() {
         return tableName;
      }

      public void setTableName(String tableName) {
         this.tableName = tableName;
      }

      public String getColumnType() {
         return columnType;
      }

      public void setColumnType(String columnType) {
         this.columnType = columnType;
      }

      private String database;
      private String partition;
      private String tableName;
      private String columnName;
      private String columnType;
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final DatabaseTreeService databaseTreeService;
   private final DataSourceService dataSourceService;
   private final XRepository repository;
   private final SecurityEngine securityEngine;
}
