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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.*;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.RenameColumnController;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SaveWorksheetCommand;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that provides the REST endpoints for the sql query dialog.
 *
 * @since 12.3
 */
@Controller
public class SQLQueryDialogController extends WorksheetController {
   public SQLQueryDialogController(QueryGraphModelService queryGraphService) {
      this.queryGraphService = queryGraphService;
   }

   /**
    * Gets the model of the sql query dialog
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public SQLQueryDialogModel getModel(@RequestParam("runtimeId") String runtimeId,
                                       @RequestParam(name = "tableName", required = false) String tableName,
                                       @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      return queryManagerService.getSqlQueryDialogModel(rws, tableName, dataSource, principal);
   }

   /**
    * Gets the model of the sql query dialog
    *
    * @return the model object.
    */
   @RequestMapping(
      value = "/api/data/condition/subquery/sql-query-dialog-model",
      method = RequestMethod.GET)
   @ResponseBody
   public SQLQueryDialogModel getSubQueryModel(
      @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setAdvancedEdit(false);
      model.setDataSource(dataSource);
      model.setSimpleModel(new BasicSQLQueryModel());
      List<String> datasources = new ArrayList<>();
      datasources.add(dataSource);
      model.setDataSources(datasources);

      try {
         model.setPhysicalTablesEnabled(securityEngine.checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS));
         model.setFreeFormSqlEnabled(securityEngine.checkPermission(
            principal, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS));
      }
      catch(Exception ignore) {
         model.setPhysicalTablesEnabled(false);
         model.setFreeFormSqlEnabled(false);
      }

      return model;
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/clear")
   @ResponseBody
   public SQLQueryDialogModel clearQuery(@RequestParam("runtimeId") String runtimeId,
                                         @RequestParam("dataSource") String dataSource,
                                         @RequestParam("tableName") String tableName,
                                         @RequestParam("advancedEdit") boolean advancedEdit,
                                         Principal principal)
      throws Exception
   {
      return queryManagerService.clearQuery(runtimeId, dataSource, tableName, advancedEdit, principal);
   }

   /**
    * Browses the available data for the given data ref
    *
    * @param runtimeId the runtime identifier of the worksheet.
    * @return the updated model.
    */
   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/browse-data",
      method = RequestMethod.POST)
   @ResponseBody
   public BrowseDataModel browseData(@RequestParam("runtimeId") String runtimeId,
                                     @RequestParam("dataSource") String dataSource,
                                     @RequestBody ColumnRefModel dataRefModel,
                                     Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(runtimeId, principal);
      BrowseDataController browseDataController = new BrowseDataController();
      DataRef dataRef = dataRefModel.createDataRef();

      if(!(dataRef instanceof ColumnRef)) {
         dataRef = new ColumnRef(dataRef);
      }

      browseDataController.setColumn((ColumnRef) dataRef);
      browseDataController.setDataSource(dataSource);
      return browseDataController.process(rws.getAssetQuerySandbox());
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/get-sql-string",
      method = RequestMethod.POST)
   @ResponseBody
   public String getSQLString(@RequestBody SQLQueryDialogModel model, Principal principal)
      throws Exception
   {
      BasicSQLQueryModel sqlQueryModel = model.getSimpleModel();
      String dataSource = model.getDataSource();
      String[] selectedColumns = sqlQueryModel.getSelectedColumns();
      XJoin[] joins = sqlQueryModel.toXJoins();
      JDBCDataSource jdbcDataSource =
         (JDBCDataSource) xrepository.getDataSource(dataSource);
      UniformSQL sql = JDBCUtil.createSQL(jdbcDataSource, sqlQueryModel.getTables(),
         selectedColumns, joins, sqlQueryModel.getConditionList(), principal);
      return sql.getSQLString();
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/get-sql-parse-result",
      method = RequestMethod.POST)
   @ResponseBody
   public String getSqlParseResult(@RequestBody String sqlParseResult, Principal principal)
      throws Exception
   {
      UniformSQL sql = new UniformSQL(sqlParseResult);

      synchronized(sql) {
         if(sql.getParseResult() == UniformSQL.PARSE_INIT) {
            try {
               sql.wait();
            }
            catch(InterruptedException e) {
            }
         }

      }

      return JDBCUtil.getParseResult(sql.getParseResult());
   }

   @RequestMapping(
      value = "/api/composer/ws/sql-query-dialog/table-columns",
      method = RequestMethod.POST)
   @ResponseBody
   public AssetEntry[] getTableColumns(@RequestBody AssetEntry tableEntry,
                                       Principal principal)
      throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      return engine.getAssetRepository().getEntries(tableEntry, principal, ResourceAction.READ);
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/change-edit-mode")
   @ResponseBody
   public SQLQueryDialogModel changeEditMode(@RequestBody SQLQueryDialogModel model,
                                             @RequestParam("runtimeWsId") String runtimeWsId,
                                             @RequestParam("runtimeQueryId") String runtimeQueryId,
                                             @RequestParam("advancedEdit") boolean advancedEdit,
                                             Principal principal)
      throws Exception
   {
      model.setAdvancedEdit(advancedEdit);

      if(advancedEdit && model.getSimpleModel() != null) {
         RuntimeWorksheet rws = StringUtils.isEmpty(runtimeWsId) ?
            null : super.getWorksheetEngine().getWorksheet(runtimeWsId, principal);
         model.setAdvancedModel(
            queryManagerService.convertToAdvancedQueryModel(rws, model, principal));
         RuntimeQueryService.RuntimeXQuery runtimeQuery =
            queryManagerService.getRuntimeQuery(runtimeQueryId);

         if(runtimeQuery != null) {
            queryGraphService.autoLayoutGraph(runtimeQueryId, principal);
         }
      }
      else if(!advancedEdit && model.getAdvancedModel() != null) {
         model.setSimpleModel(
            queryManagerService.convertToSimpleQueryModel(model, runtimeQueryId));
      }

      return model;
   }

   @PostMapping("/api/composer/ws/sql-query-dialog/query/update")
   @ResponseBody
   public void updateQueryBySample(@RequestBody() BasicSQLQueryModel model,
                                   @RequestParam("runtimeId") String runtimeId,
                                   @RequestParam("datasource") String datasource,
                                   Principal principal)
      throws Exception
   {
      queryManagerService.updateQuery(runtimeId, model, datasource, principal);
   }

   /**
    * Get a list of variable assembly names.
    */
   private String[] getVariableList(RuntimeWorksheet rws, Principal principal) {
      Worksheet ws = rws.getWorksheet();
      ArrayList<String> list = new ArrayList<>();
      Set<String> added = new HashSet<>();

      for(Assembly assembly : ws.getAssemblies()) {
         WSAssembly wsAssembly = (WSAssembly) assembly;

         if(wsAssembly.isVariable() && wsAssembly.isVisible()) {
            VariableAssembly vassembly = (VariableAssembly) wsAssembly;
            UserVariable var = vassembly.getVariable();

            if(var != null && !added.contains(var.getName())) {
               added.add(var.getName());
               list.add(var.getName());
            }
         }
      }

      // for bug1291823096435, display the parameters which is defined in
      // SRPrincipal
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
//      XPrincipal principal = (XPrincipal) ThreadContext.getContextPrincipal();
      box.setBaseUser(principal);
      VariableTable vtable = box.getVariableTable();
      Enumeration keys = vtable.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();

         if(!list.contains(key)) {
            list.add(key);
         }
      }

      return list.toArray(new String[list.size()]);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/sql-query-dialog-model")
   public void setModel(@Payload SQLQueryDialogModel model,
                        Principal principal,
                        CommandDispatcher commandDispatcher)
      throws Exception
   {
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      if(!sqlEnabled) {
         throw new MessageException(
            Catalog.getCatalog().getString("composer.nopermission.physicalTable"));
      }

      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = model.getName();
      SQLBoundTableAssembly assembly = name == null ? null :
         (SQLBoundTableAssembly) ws.getAssembly(name);
      VariableTable vars = rws.getAssetQuerySandbox().getVariableTable();

      if(assembly == null) {
         name = model.getName() == null ?
            AssetUtil.getNextName(ws, "SQL Query") : model.getName();
         assembly = new SQLBoundTableAssembly(ws, name);
         assembly.setPixelOffset(new Point(25, 25));
         assembly.setAdvancedEditing(model.isAdvancedEdit());
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);

         if(!model.isAdvancedEdit()) {
            if(model.getSimpleModel().isSqlEdited()) {
               setUpTableWithSQLString(assembly, model, model.getDataSource(),
                  vars, commandDispatcher);
            }
            else {
               setUpTable(assembly, model, model.getDataSource(), vars, principal);
            }
         }
         else {
            setUpAdvancedQueryModel(ws, assembly, model, vars, principal);
         }

         ws.addAssembly(assembly);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         WorksheetEventUtil.focusAssembly(assembly.getName(), commandDispatcher);
      }
      else {
         assembly.setAdvancedEditing(model.isAdvancedEdit());
         AggregateInfo aginfo =
            (AggregateInfo) assembly.getAggregateInfo().clone();

         if(!model.isAdvancedEdit()) {
            if(model.getSimpleModel().isSqlEdited()) {
               setUpTableWithSQLString(assembly, model, model.getDataSource(),
                  vars, commandDispatcher);
            }
            else {
               setUpTable(assembly, model, model.getDataSource(), vars, principal);
            }
         }
         else {
            setUpAdvancedQueryModel(ws, assembly, model, vars, principal);
         }

         fixGrouping(assembly, aginfo);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      }

      if(model.isMashUpData()) {
         ws.getWorksheetInfo().setMashupMode();
      }

      if(model.isCloseDialog()) {
         queryManagerService.clearRuntimeQuery();
      }

      WorksheetEventUtil.refreshColumnSelection(rws, name, true);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
      WorksheetEventUtil.refreshVariables(rws, super.getWorksheetEngine(), name, commandDispatcher);

      if(ws.getWorksheetInfo().isSingleQueryMode()) {
         SaveWorksheetCommand command = new SaveWorksheetCommand();
         command.setClose(model.isCloseDialog());
         commandDispatcher.sendCommand(command);
      }
   }

   private void setUpAdvancedQueryModel(Worksheet ws, SQLBoundTableAssembly assembly, SQLQueryDialogModel model,
                                        VariableTable vars, Principal principal)
      throws Exception
   {
      String runtimeId = model.getRuntimeId();
      AdvancedSQLQueryModel advancedModel = model.getAdvancedModel();

      if(advancedModel.isSqlEdited()) {
         UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
         event.setRuntimeId(runtimeId);
         event.setFreeFormSqlPaneModel(advancedModel.getFreeFormSQLPaneModel());
         queryManagerService.setFreeFormSQLPaneModel(event, principal);
      }
      else {
         queryManagerService.updateQuery(runtimeId, advancedModel, null, true);
      }

      RuntimeQueryService.RuntimeXQuery runtimeQuery = queryManagerService.getRuntimeQuery(runtimeId);
      JDBCQuery query = runtimeQuery == null ? null : runtimeQuery.getQuery();
      String dataSource = model.getDataSource();
      SourceInfo sinfo = new SourceInfo(SourceInfo.DATASOURCE, dataSource, dataSource);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) assembly.getInfo();
      info.setSourceInfo(sinfo);
      info.setQuery(query.clone());
      Map<String, String> aliasMapping = runtimeQuery.getAliasMapping();
      ColumnSelection oldColumns = (ColumnSelection) Tool.clone(assembly.getColumnSelection());
      ColumnSelection selection =
         queryManagerService.getColumnSelection(query, vars, assembly, getSession(), aliasMapping);
      assembly.setColumnSelection(selection);
      updateSqlBoundAssemblyCondition(assembly, selection, aliasMapping);
      renameColumnRef(ws, oldColumns, assembly, aliasMapping);
   }

   private void renameColumnRef(Worksheet ws, ColumnSelection oldColumns, SQLBoundTableAssembly assembly,
                                Map<String, String> aliasMapping)
   {
      ColumnSelection columnSelection = assembly.getColumnSelection();
      Set<String> originalAlias = aliasMapping.keySet();

      for(String original : originalAlias) {
         String newAlias = aliasMapping.get(original);

         if(Tool.equals(original, newAlias)) {
            continue;
         }

         ColumnRef originalRef = (ColumnRef) oldColumns.getAttribute(original);
         ColumnRef newRef = (ColumnRef) columnSelection.getAttribute(aliasMapping.get(original));
         RenameColumnController.renameTableColumn(ws, assembly, originalRef, newRef);
      }
   }

   private void setUpTable(SQLBoundTableAssembly assembly, SQLQueryDialogModel sqlQueryDialogModel,
                           String dataSource, VariableTable vars, Principal principal)
      throws Exception
   {
      BasicSQLQueryModel model = sqlQueryDialogModel.getSimpleModel();
      String[] selectedColumns = model.getSelectedColumns();
      JDBCDataSource jdbcDataSource =
         (JDBCDataSource) xrepository.getDataSource(dataSource);
      XJoin[] xjoins = model.toXJoins();
      UniformSQL sql = JDBCUtil.createSQL(jdbcDataSource,
         model.getTables(), selectedColumns, xjoins, model.getConditionList(), principal);
      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(jdbcDataSource);
      query.setSQLDefinition(sql);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) assembly
         .getInfo();
      info.setQuery(query);
      SourceInfo sinfo = new SourceInfo(SourceInfo.DATASOURCE, dataSource,
         dataSource);
      info.setSourceInfo(sinfo);
      ItemList cols = new ItemList();
      cols.addAllItems((Arrays.stream(model.getColumns())
         .map(SQLQueryDialogColumnModel::name)
         .collect(Collectors.toList())));

      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         queryManagerService.getRuntimeQuery(sqlQueryDialogModel.getRuntimeId());
      Map<String, String> aliasMapping = runtimeQuery == null ? null : runtimeQuery.getAliasMapping();
      ColumnSelection selection =
         queryManagerService.getColumnSelection(query, vars, assembly, getSession(), aliasMapping);
      assembly.setColumnSelection(selection);
      assembly.setSQLEdited(false);
   }

   private void setUpTableWithSQLString(SQLBoundTableAssembly assembly, SQLQueryDialogModel model,
                                        String dataSource, VariableTable vars,
                                        CommandDispatcher commandDispatcher)
      throws Exception
   {
      BasicSQLQueryModel simpleModel = model.getSimpleModel();
      String sqlString = simpleModel.getSqlString();
      XDataSource xds = xrepository.getDataSource(dataSource);
      UniformSQL sql = new UniformSQL();
      sql.setDataSource((JDBCDataSource) xds);

      // Block until sql string is parsed to prevent race condition when sql.getSQLString() is
      // called during getColumnSelection(). SQLProcessor calls notifyAll() on sql in the finally
      // block of its parsing method.
      synchronized(sql) {
         sql.setParseSQL(true);
         sql.setSQLString(sqlString, true);
         sql.wait();
      }

      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(xrepository.getDataSource(dataSource));
      query.setSQLDefinition(sql);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) assembly.getInfo();
      SQLBoundTableAssemblyInfo oldInfo = (SQLBoundTableAssemblyInfo) info.clone();
      info.setQuery(query);
      SourceInfo sinfo = new SourceInfo(SourceInfo.PHYSICAL_TABLE, dataSource, dataSource);
      info.setSourceInfo(sinfo);

      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         queryManagerService.getRuntimeQuery(model.getRuntimeId());
      Map<String, String> aliasMapping = runtimeQuery == null ? null : runtimeQuery.getAliasMapping();
      ColumnSelection columns =
         queryManagerService.getColumnSelection(query, vars, assembly, getSession(), aliasMapping);

      if(columns.isEmpty()) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString(
            "common.sqlquery.syntaxError"));
         messageCommand.setType(MessageCommand.Type.WARNING);
         messageCommand.setAssemblyName(assembly.getName());
         commandDispatcher.sendCommand(messageCommand);
      }

      Object session = new DefaultMetaDataProvider().getSession();
      JDBCUtil.fixUniformSQLInfo(sql, xrepository, session,
                                 (JDBCDataSource) query.getDataSource());

      // get again to make sure * is expanded
      columns = queryManagerService.getColumnSelection(query, vars, assembly, getSession(), aliasMapping);

      if(!allowsDeletion(columns, assembly.getColumnSelection(), assembly, assembly.getWorksheet(),
         commandDispatcher))
      {
         info.setQuery(oldInfo.getQuery());
         info.setSourceInfo(oldInfo.getSourceInfo());

         return;
      }

      assembly.setColumnSelection(columns);
      assembly.setSQLEdited(true);
   }

   private boolean allowsDeletion(ColumnSelection newColumns, ColumnSelection oldColumns,
                                  TableAssembly assembly, Worksheet ws,
                                  CommandDispatcher dispatcher)
   {
      if(oldColumns == null || oldColumns.getAttributeCount() == 0) {
         return true;
      }

      if(newColumns == null) {
         newColumns = new ColumnSelection();
      }

      for(int i = 0; i < oldColumns.getAttributeCount(); i++) {
         ColumnRef attribute = (ColumnRef) oldColumns.getAttribute(i);

         if(!newColumns.containsAttribute(attribute) && !allowsDeletion(ws, assembly, attribute)) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.columnDependency", attribute.getAttribute()));
            command.setType(MessageCommand.Type.WARNING);
            command.setAssemblyName(assembly.getName());
            dispatcher.sendCommand(command);

            return false;
         }
      }

      return true;
   }

   /**
    * Check if the old groupings still apply to the new column selection
    * and if so then add them back in.
    */
   private void fixGrouping(TableAssembly table, AggregateInfo oldInfo) {
      AggregateInfo newInfo = table.getAggregateInfo();
      ColumnSelection newCols = table.getColumnSelection();
      GroupRef[] oldGroups = oldInfo.getGroups();

      for(GroupRef oldGroup : oldGroups) {
         if(!newInfo.containsGroup(oldGroup)) {
            Enumeration e = oldGroup.getAttributes();
            boolean exists = true;

            while(exists && e.hasMoreElements()) {
               Object ref = e.nextElement();

               if(ref instanceof DataRef) {
                  exists = newCols.containsAttribute((DataRef) ref);
               }
               else if(ref instanceof String) {
                  exists = false;

                  for(int i = 0; i < newCols.getAttributeCount(); i++) {
                     if(newCols.getAttribute(i).getAttribute().equals(ref)) {
                        exists = true;
                        break;
                     }
                  }
               }
               else {
                  exists = false;
               }
            }

            if(exists) {
               newInfo.addGroup(oldGroup);
            }
         }
      }

      // make sure group columns are in the list or they will be lost
      for(GroupRef group : newInfo.getGroups()) {
         newCols.addAttribute(group.getDataRef());
      }
   }

   private void updateSqlBoundAssemblyCondition(SQLBoundTableAssembly assembly, ColumnSelection selection,
                                                Map<String, String> aliasMapping)
   {
      updateCondition(assembly.getPreConditionList(), selection, aliasMapping);
      updateCondition(assembly.getPostConditionList(), selection, aliasMapping);
      updateCondition(assembly.getRankingConditionList(), selection, aliasMapping);
      updateCondition(assembly.getMVUpdatePreConditionList(), selection, aliasMapping);
      updateCondition(assembly.getMVUpdatePostConditionList(), selection, aliasMapping);
      updateCondition(assembly.getMVDeletePreConditionList(), selection, aliasMapping);
      updateCondition(assembly.getMVDeletePostConditionList(), selection, aliasMapping);
   }

   private void updateCondition(ConditionListWrapper conditionListWrapper, ColumnSelection selection,
                                Map<String, String> aliasMapping)
   {
      if(conditionListWrapper == null || conditionListWrapper.getConditionList() == null ||
         conditionListWrapper.getConditionList().isEmpty())
      {
         return;
      }

      ConditionList conditionList = conditionListWrapper.getConditionList();

      for(int i = 0; i < conditionList.getSize(); i++) {
         ConditionItem conditionItem = conditionList.getConditionItem(i);
         DataRef ref = conditionItem.getAttribute();
         ColumnRef colRef = getColumnRef(ref);

         if(colRef != null) {
            DataRef dataRef = colRef.getDataRef();

            if(dataRef instanceof AttributeRef attRef) {
               String attr = attRef.getAttribute();
               String newAlias = aliasMapping.get(attr);
               DataRef newCol = selection.getAttribute(newAlias);

               if(ref instanceof ColumnRef) {
                  conditionItem.setAttribute(newCol);
               }
               else {
                  updateColumnRef(ref, (ColumnRef) newCol);
                  conditionItem.setAttribute(ref);
               }
            }
         }
      }
   }

   private ColumnRef getColumnRef(DataRef ref) {
      if(ref instanceof ColumnRef) {
         return ((ColumnRef) ref);
      }
      else if(ref instanceof GroupRef) {
         DataRef dataRef = ((GroupRef) ref).getDataRef();
         return getColumnRef(dataRef);
      }
      else if(ref instanceof AggregateRef) {
         DataRef dataRef = ((AggregateRef) ref).getDataRef();
         return getColumnRef(dataRef);
      }

      return null;
   }

   private void updateColumnRef(DataRef ref, ColumnRef newCol) {
      if(ref instanceof GroupRef) {
         GroupRef groupRef = (GroupRef) ref;
         DataRef dataRef = groupRef.getDataRef();

         if(dataRef instanceof ColumnRef) {
            groupRef.setDataRef(newCol);
         }
         else {
            updateColumnRef(dataRef, newCol);
         }
      }
      else if(ref instanceof AggregateRef) {
         AggregateRef aggregateRef = (AggregateRef) ref;
         DataRef dataRef = ((AggregateRef) ref).getDataRef();

         if(dataRef instanceof ColumnRef) {
            aggregateRef.setDataRef(newCol);
         }
         else {
            updateColumnRef(dataRef, newCol);
         }
      }
   }

   private Object getSession() throws Exception {
      return super.getWorksheetEngine().getAssetRepository().getSession();
   }

   @Autowired
   public void setDataRefModelFactoryService(DataRefModelFactoryService dataRefModelFactoryService) {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   @Autowired
   public void setXRepository(XRepository xrepository) {
      this.xrepository = xrepository;
   }

   @Autowired
   public void setSecurityEngine(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @Autowired
   public void setQueryManagerService(QueryManagerService queryManagerService) {
      this.queryManagerService = queryManagerService;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
   private XRepository xrepository;
   private SecurityEngine securityEngine;
   private QueryManagerService queryManagerService;
   private final QueryGraphModelService queryGraphService;
}
