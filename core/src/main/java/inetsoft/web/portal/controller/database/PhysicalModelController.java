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

import inetsoft.sree.security.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint for managing physical data models.
 */
@RestController
@Lazy
public class PhysicalModelController {

   public PhysicalModelController(RuntimePartitionService runtimePartitionService,
                                  DatabaseTreeService databaseTreeService,
                                  PhysicalModelService physicalModelService,
                                  DataSourceService dataSourceService,
                                  XRepository repository,
                                  PhysicalModelManagerService physicalModelManager)
   {
      this.databaseTreeService = databaseTreeService;
      this.runtimePartitionService = runtimePartitionService;
      this.physicalModelService = physicalModelService;
      this.dataSourceService = dataSourceService;
      this.repository = repository;
      this.physicalModelManager = physicalModelManager;
   }

   @GetMapping(value = "/api/data/physicalmodel/heartbeat")
   public boolean heartBeat(@RequestParam("id") String id) {
      return this.runtimePartitionService.touch(id);
   }

   @GetMapping("/api/data/physicalModel/checkDuplicate")
   public boolean checkLogicalModelDuplicate(@RequestParam("database") String database,
                                             @RequestParam("name") String name)
      throws Exception
   {
      return dataSourceService.isUniqueModelName(database, name);
   }

   @GetMapping("/api/data/physicalModel/extended/checkDuplicate")
   public boolean checkExtendedModelDuplicate(@RequestParam("database") String database,
                                              @RequestParam("parent") String parent,
                                              @RequestParam("name") String name)
      throws Exception
   {
      return !dataSourceService.isUniqueExtendedPhysicalModelName(database, parent, name);
   }

   /**
    * Gets a physical model.
    * @return the physical model.
    *
    * @throws Exception if the model could not be obtained.
    */
   @RequestMapping(value = "/api/data/physicalmodel/model", method = RequestMethod.POST)
   public PhysicalModelDefinition openModel(@RequestBody GetModelEvent event, Principal principal)
      throws Exception
   {
      return physicalModelManager.openModel(
         event.getDatasource(), event.getParent(), event.getPhysicalName(), principal);
   }

   @PostMapping("/api/data/physicalmodel/model/create")
   public PhysicalModelDefinition createNewModel(@RequestBody AddPhysicalModelEvent event,
                                                 Principal principal)
      throws Exception
   {
      return physicalModelManager.createModel(
         event.getDatabase(), event.getParent(), event.getModel(), principal);
   }

   @GetMapping(value = "/api/data/physicalmodel/model/refresh")
   public PhysicalModelDefinition refreshModel(@RequestParam("id") String id) throws Exception {
      if(StringUtils.isEmpty(id)) {
         return null;
      }

      RuntimePartitionService.RuntimeXPartition rp = runtimePartitionService.getRuntimePartition(id);

      if(rp == null) {
         // throw time out
         return null;
      }

      XDataModel dataModel = repository.getDataModel(rp.getDataSource());

      return physicalModelService.createModel(dataModel, rp);
   }

   /**
    * Gets the database tree nodes.
    *
    * @param parentPath the entry's path
    *
    * @return the tree nodes.
    *
    * @throws Exception if the tree nodes could not be obtained.
    */
   @GetMapping("/api/data/physicalmodel/tree/nodes")
   public List<TreeNodeModel> getDatabaseTreeNodes(
      @RequestParam("parentPath") String parentPath,
      @RequestParam(value = "parr", required = false) String parr,
      @RequestParam(value = "additional", required = false) String additional,
      Principal principal)  throws Exception
   {
      List<DatabaseTreeNode> nodes =
         databaseTreeService.getDatabaseNodes(parentPath, parr, additional, false,
            false, principal);

      return nodes.stream()
         .map((node) ->TreeNodeModel.builder()
            .label(node.getName())
            .data(node)
            .leaf(!node.getType().equals(DatabaseTreeNodeType.FOLDER))
            .type(node.getType())
            .cssClass("action-color")
            .build())
         .collect(Collectors.toList());
   }

   /**
    * Gets the database tree nodes.
    *
    * @param database the database path
    *
    * @return the tree nodes.
    *
    * @throws Exception if the tree nodes could not be obtained.
    */
   @GetMapping(value = "/api/data/physicalmodel/tree/allNodes")
   public List<TreeNodeModel> getDatabaseTree(
      @RequestParam("database") String database,
      @RequestParam(value = "additional", required = false) String additional,
      Principal principal) throws Exception
   {
      return this.databaseTreeService.getFullDatabaseTree(database,
         database.replace("/", AssetEntry.PATH_ARRAY_SEPARATOR), additional,
         false, false, principal);
   }

   /**
    * Gets the columns of a table.
    *
    *
    * @return the column names.
    *
    * @throws Exception if the columns could not be obtained.
    */
   @PostMapping(value = "/api/data/physicalmodel/columns")
   public List<String> getTableColumns(@RequestBody GetTableColumnEvent event)
      throws Exception
   {
      return physicalModelManager.loadTableColumns(event.getDatabase(),
         event.getPartitionId(), event.getTableName(), false);
   }

   /**
    * Gets the columns of an inline view.
    *
    * @param event get sql column event.
    *
    * @return the column names.
    *
    * @throws Exception if the columns could not be obtained.
    */
   @PostMapping(value = "/api/data/physicalmodel/views/columns")
   public String[] getViewColumns(@RequestBody GetSqlColumnsEvent event, Principal principal)
      throws Exception
   {
      return physicalModelService.getViewColumns(event.getDatabase(), event.getAdditional(),
         event.getSql(), principal).toArray(new String[0]);
   }

   /**
    * Return options for auto-aliasing for a specific table.
    * @return the list of auto-alias options.
    */
   @GetMapping(value = "/api/data/physicalmodel/aliases/{runtimeId}/**")
   public List<AutoAliasJoinModel> getAliases(@PathVariable("runtimeId") String runtimeId,
                                              @RemainingPath String qualifiedName)
   {
      return physicalModelManager.getAliases(qualifiedName, runtimeId);
   }

   /**
    * Gets the warning messages.
    *
    * @return warning panel data and array with the 0 index being the message,
    *         in the case of unjoined tables 1 index is the first table, 2 index is the second.
    */
   @GetMapping("/api/data/physicalmodel/warnings/{runtimeId}")
   public WarningModel getWarnings(@PathVariable("runtimeId") String runtimeId, Principal principal)
   {
      runtimeId = Tool.byteDecode(runtimeId);
      WarningModel result = new WarningModel();
      XPartition partition = this.runtimePartitionService.getPartition(runtimeId);
      Catalog catalog = Catalog.getCatalog(principal);
      partition = partition.applyAutoAliases();
      int status = partition.getStatus();

      if(status != XPartition.VALID) {
         if(status == XPartition.UNJOINED) {
            String[] unjoined = partition.getUnjoinedTables();
            boolean allowed;

            try {
               allowed = SecurityEngine.getSecurity().checkPermission(
                  principal, ResourceType.CROSS_JOIN, "*", ResourceAction.ACCESS);
            }
            catch(Exception e) {
               allowed = false;
            }

            if(allowed) {
               result.setMessage(catalog.getString(
                  "data.physicalmodel.unjoinedTablesWarning", unjoined[0], unjoined[1]));
            }
            else {
               result.setMessage(catalog.getString(
                  "data.physicalmodel.unjoinedTablesProhibited", unjoined[0], unjoined[1]));
               result.setCanContinue(false);
            }

            result.setTable1(unjoined[0]);
            result.setTable2(unjoined[1]);
         }
         else if(status == XPartition.CYCLE) {
            result.setMessage(catalog.getString("data.physicalmodel.cycleWarning", "", ""));
            result.setTable1("");
            result.setTable2("");
         }

         return result;
      }
      else {
         return null;
      }
   }

   /**
    * Adds a model to a database.
    *
    * @param event add event.
    *
    * @throws Exception if the database could not be added.
    */
   @PostMapping("/api/data/physicalmodel/models")
   @ResponseStatus(HttpStatus.CREATED)
   public void addModelAndSave(@RequestBody AddPhysicalModelEvent event, Principal principal)
      throws Exception
   {
      PhysicalModelDefinition model = event.getModel();
      String folder = model == null ? null : model.getFolder();
      physicalModelManager.createAndSaveModel(
         event.getDatabase(), folder, event.getParent(), model, principal);
   }

   /**
    * @param event   rename event.
    * @throws Exception if the model could not be renamed.
    */
   @PutMapping("/api/data/physicalmodel/rename")
   public void renameModel(@RequestBody RenameModelEvent event, Principal principal)
      throws Exception
   {
      physicalModelManager.renameModel(
         event.getDatabase(), event.getFolder(), event.getOldName(), event.getNewName(),
         event.getDescription(), principal);
   }

   /**
    * Add new joins.
    *
    * @param event edit event.
    */
   @PostMapping(value = "/api/data/physicalmodel/join/exist")
   public boolean checkJoinExist(@RequestBody EditJoinsEvent event) {
      for(EditJoinsEvent.EditJoinEventItem item : event.getJoinItems()) {
         if(this.physicalModelManager.checkJoinExist(event.getId(), item.getJoin(),
            item.getTable().getQualifiedName()))
         {
            return true;

         }
      }

      return false;
   }

   /**
    * Add new joins.
    *
    * @param event edit joins event
    */
   @PostMapping(value = "/api/data/physicalmodel/join/add")
   public void addJoin(@RequestBody EditJoinsEvent event) {
      editJoins(event);
   }

   /**
    * remove joins.
    *
    * @param event edit joins event
    */
   @PutMapping(value = "/api/data/physicalmodel/join/remove")
   public void removeJoin(@RequestBody EditJoinsEvent event) {
      editJoins(event);
   }

   /**
    * modify joins.
    *
    * @param event edit joins event
    */
   @PutMapping(value = "/api/data/physicalmodel/join/modify")
   public void modifyJoin(@RequestBody EditJoinsEvent event) {
      editJoins(event);
   }

   private void editJoins(EditJoinsEvent event) {
      EditJoinsEvent.EditJoinEventItem[] items = event.getJoinItems();

      if(items == null || items.length == 0) {
         return;
      }

      for(EditJoinsEvent.EditJoinEventItem item : items) {
         if(item instanceof EditJoinsEvent.RemoveJoinEventItem) {
            EditJoinsEvent.RemoveJoinEventItem removeEvent =
               (EditJoinsEvent.RemoveJoinEventItem) item;
            physicalModelManager.removeJoin(
               event.getId(), removeEvent.getJoin(), removeEvent.getForeignTable(),
               removeEvent.getTable().getQualifiedName());
         }
         else if(item instanceof EditJoinsEvent.ModifyJoinEventItem) {
            EditJoinsEvent.ModifyJoinEventItem removeEvent =
               (EditJoinsEvent.ModifyJoinEventItem) item;
            physicalModelManager.updateJoin(
               event.getId(), removeEvent.getJoin(), removeEvent.getOldJoin(),
               removeEvent.getTable().getQualifiedName());
         }
         else {
            physicalModelManager.addJoin(
               event.getId(), item.getJoin(), item.getTable().getQualifiedName());
         }
      }
   }

   /**
    * Updates a physical model.
    *
    * @param id editing RID
    */
   @DeleteMapping(value = "/api/data/physicalmodel/destroy")
   public void destroyRuntime(@RequestParam("id") String id) {
      physicalModelManager.closeModel(id);
   }

   /**
    * Updates a physical model.
    *
    * @param event modify event
    *
    * @throws Exception if the model could not be updated.
    */
   @PutMapping(value = "/api/data/physicalmodel/models")
   public void updateModelAndSave(@RequestBody ModifyPhysicalModelEvent event, Principal principal)
      throws Exception
   {
      PhysicalModelDefinition model = event.getModel();
      String folder = model == null ? null : model.getFolder();
      physicalModelManager.updateAndSaveModel(event.getDatabase(), folder, event.getParent(),
         event.getModelName(), event.getModel(), principal);
   }

   /**
    * Deletes a physical model.
    *
    * @param database the database name.
    * @param name     the model name.
    *
    * @throws Exception if the model could not be removed.
    */
   @DeleteMapping(value = "/api/data/physicalmodel/models")
   public boolean removeModel(@RequestParam("database") String database,
                              @RequestParam(value = "folder", required = false) String folder,
                              @RequestParam(value = "parent", required = false) String parent,
                              @RequestParam("name") String name, Principal principal)
      throws Exception
   {
      return physicalModelManager.removeModel(database, folder, name, parent, principal);
   }

   @PostMapping("/api/data/physicalmodel/table/add")
   public void addTable(@RequestBody EditTableEvent event) throws Exception {
      physicalModelManager.addTable(event.getId(), event.getNode(), true);
   }

   @PostMapping("/api/data/physicalmodel/table/remove")
   public void removeTable(@RequestBody EditTableEvent event) {
      String id = event.getId();

      if(StringUtils.isEmpty(id)) {
         return;
      }

      RuntimePartitionService.RuntimeXPartition rp = runtimePartitionService.getRuntimePartition(id);

      if(rp == null) {
         // throw time out.
         return;
      }

      PhysicalTableModel table = event.getTable();

      if(table != null) {
        this.removeTable(id, table.getQualifiedName(), table.getName());
      }
   }

   @PostMapping("/api/data/physicalmodel/tables/remove")
   public void removeTable(@RequestBody RemoveGraphTableEvent event)
   {
      for(RemoveGraphTableEvent.RemoveTableInfo info : event.getTables()) {
         removeTable(event.getRuntimeId(), info.getFullName(), info.getTableName());
      }
   }

   @DeleteMapping("/api/data/physicalmodel/table/remove")
   public void removeTable(@RequestParam String runtimeId, @RequestParam String name,
                           @RequestParam String tableName)
   {
      physicalModelManager.removeTable(runtimeId, name, tableName);
   }

   /**
    * Checks if the data source supports a full outer join.
    * @param database the database name
    * @return true if the datasource supports full outer joins, false otherwise
    * @throws Exception if could not get the datasource
    */
   @RequestMapping(
      value = "/api/data/physicalmodel/fullOuterJoin",
      method = RequestMethod.GET)
   public boolean supportFullOuterJoin(
      @RequestParam("database") String database,
      @RequestParam(value = "additional", required = false) String additional) throws Exception
   {
      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService
         .getDataSource(database);

      if(dataSource != null) {
         SQLHelper helper = dataSourceService.getSqlHelper(dataSource, additional);
         return helper.supportsOperation(SQLHelper.FULL_OUTERJOIN);
      }

      return true;
   }

   /**
    * @param database   the database name.
    * @param model      the physical model
    * @return a list of available columns for auto join
    * @throws Exception if could not generate list of columns for auto join.
    */
   @RequestMapping(
      value = "/api/data/physicalmodel/autoJoin/**",
      method = RequestMethod.POST)
   public AutoJoinColumnsModel getAutoJoinColumns(@RemainingPath String database,
                                                  @RequestBody PhysicalModelDefinition model,
                                                  Principal principal)
      throws Exception
   {
      return physicalModelManager.getAutoJoinColumns(database, model, principal);
   }

   @PutMapping(value = "/api/data/physicalmodel/autoAlias")
   public void setAutoAlias(@RequestBody SetAutoAliasEvent event) {
      physicalModelManager.updateAutoAliasing(event.getId(), event.getTable());
   }

   @PostMapping(value = "/api/data/physicalmodel/alias/add")
   public StringWrapper createTableAlias(@RequestBody EditTableEvent event) throws Exception {
      String invalidMsg = physicalModelManager.createAlias(event.getId(), event.getTable());

      if(invalidMsg != null) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(invalidMsg);
         return wrapper;
      }

      return null;
   }

   @PostMapping(value = "/api/data/physicalmodel/alias/modify")
   public void modifyTableAlias(@RequestBody EditTableEvent event) {
      physicalModelManager.updateAlias(event.getId(), event.getOldName(), event.getTable());
   }

   @PostMapping(value = "/api/data/physicalmodel/inlineView/modify")
   public void editInlineView(@RequestBody EditTableEvent event) {
      physicalModelManager.updateInlineView(event.getId(), event.getOldName(), event.getTable());
   }

   @PostMapping(value = "/api/data/physicalmodel/inlineView/add")
   public void createInlineView(@RequestBody EditTableEvent event) throws Exception {
      physicalModelManager.createInlineView(event.getId(), event.getTable());
   }

   /**
    * Detects cardinality of new join
    *
    * @param event add joins event
    */
   @PostMapping(value = "/api/data/physicalmodel/add/autoJoin")
   public void addAutoJoins(@RequestBody EditJoinsEvent event, Principal principal) {
      EditJoinsEvent.EditJoinEventItem[] items = event.getJoinItems();

      if(items == null || items.length == 0) {
         return;
      }

      for(EditJoinsEvent.EditJoinEventItem item : items) {
         physicalModelManager.addAutoJoin(
            event.getId(), item.getJoin(), item.getTable().getQualifiedName(), principal);
      }
   }

   /**
    * Detects cardinality of new join
    *
    * @param database   the database name
    * @param helper     the cardinality helper
    * @return the join with detected cardinality
    * @throws Exception if could not detect the cardinality
    */
   @RequestMapping(
      value = "/api/data/physicalmodel/cardinality",
      method = RequestMethod.POST)
   @ResponseBody
   public JoinModel getCardinality(@RequestParam("database") String database,
                                   @RequestParam(value = "additional", required = false) String additional,
                                   @RequestBody CardinalityHelper helper, Principal principal)
   {
      return physicalModelService.getCardinality(database, additional, helper, principal);
   }

   /**
    * Get physical views of database.
    *
    * @param database   the database name
    *
    * @return the physical view list.
    */
   @GetMapping("/api/data/physicalmodel/views")
   @ResponseBody
   public List<String> getPhysicalViews(@RequestParam("database") String database,
                                        Principal principal)
      throws Exception
   {
      return physicalModelService.getPhysicalViews(database, principal);
   }

   private final DatabaseTreeService databaseTreeService;
   private final RuntimePartitionService runtimePartitionService;
   private final PhysicalModelService physicalModelService;
   private final DataSourceService dataSourceService;
   private final XRepository repository;
   private final PhysicalModelManagerService physicalModelManager;

}
