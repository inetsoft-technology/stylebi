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
package inetsoft.web.portal.controller.database;

import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.util.Catalog;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.AddQueryColumnEvent;
import inetsoft.web.portal.model.database.events.RemoveQueryColumnEvent;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class QueryController extends WorksheetController {
   @Autowired
   public QueryController(RuntimeQueryService runtimeQueryService,
                          QueryManagerService queryManager)
   {
      this.runtimeQueryService = runtimeQueryService;
      this.queryManager = queryManager;
   }

   @GetMapping("/api/data/datasource/query/query-model")
   public AdvancedSQLQueryModel getQueryModel(@RequestParam("runtimeId") String runtimeId) {
      return queryManager.getQueryModel(runtimeId);
   }

   @PostMapping("/api/data/datasource/query/update")
   public void updateQuery(@RequestBody() AdvancedSQLQueryModel model,
                           @RequestParam("runtimeId") String runtimeId,
                           @RequestParam("tab") String tab)
      throws Exception
   {
      queryManager.updateQuery(runtimeId, model, tab, false);
   }

   @GetMapping(value = "/api/data/query/heartbeat")
   public boolean heartBeat(@RequestParam("id") String id) {
      return runtimeQueryService.touch(id);
   }

   @PostMapping(value = "/api/data/datasource/query/column/add")
   public AddColumnInfoResult addColumns(@RequestBody() AddQueryColumnEvent event) {
      return queryManager.addColumns(event.getRuntimeId(), event.getColumns());
   }

   @PostMapping(value = "/api/data/datasource/query/column/remove")
   public void removeColumns(@RequestBody() RemoveQueryColumnEvent event) {
      queryManager.removeColumns(event);
   }

   @PostMapping(value = "/api/data/datasource/query/column/update")
   public void updateColumn(@RequestBody() QueryFieldModel column,
                            @RequestParam("runtimeId") String runtimeId,
                            @RequestParam("type") String type,
                            @RequestParam("oldAlias") String oldAlias)
   {
      queryManager.updateColumn(runtimeId, column, type, oldAlias);
   }

   @GetMapping(value = "/api/data/datasource/query/column/browse")
   public List<String> browseColumnData(@RequestParam("runtimeId") String runtimeId,
                                        @RequestParam("column") String column,
                                        @RequestParam(value = "isAliasColumn", required = false) boolean aliasColumn)
   {
      return queryManager.browseColumnData(runtimeId, column, aliasColumn, false);
   }

   @GetMapping(value = "/api/data/datasource/query/condition/field/browserData")
   public List<String> getBrowseData(@RequestParam("runtimeId") String runtimeId,
                                     @RequestParam("tableName") String tableName,
                                     @RequestParam("columnName") String columnName,
                                     @RequestParam("columnType") String columnType)
      throws Exception
   {

      JDBCQuery query = queryManager.getQuery(runtimeId);

      if(query == null) {
         return new ArrayList<>();
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCDataSource xds = (JDBCDataSource) query.getDataSource();
      List<String> list = queryManager.getBrowseData(sql, tableName, columnName, columnType, xds, true);

      if(list.size() > BrowseDataController.MAX_ROW_COUNT) {
         list = list.subList(0, BrowseDataController.MAX_ROW_COUNT);
         list.add("(" + Catalog.getCatalog().getString("data.truncated") + ")");
      }

      return list;
   }

   @GetMapping(value = "/api/data/datasource/query/column/check/expression")
   public boolean isExpression(@RequestParam("runtimeId") String runtimeId,
                               @RequestParam("column") String column)
   {
      return queryManager.isExpression(runtimeId, column);
   }

   @GetMapping(value = "/api/data/datasource/query/expression/check")
   public boolean checkExpression(@RequestParam("expression") String expression) {
      return queryManager.checkExpression(expression);
   }

   @PostMapping(value = "/api/data/datasource/query/expression/save")
   public String[] saveExpression(@RequestParam("runtimeId") String runtimeId,
                           @RequestParam("expression") String expression,
                           @RequestParam(value = "columnName", required = false) String columnName,
                           @RequestParam(value = "columnAlias", required = false) String columnAlias,
                           @RequestParam("add") boolean add, Principal principal)
   {
      return queryManager.saveExpression(runtimeId, expression, columnName, columnAlias, add,
         principal);
   }

   @PostMapping(value = "/api/data/datasource/query/data-source-tree")
   public TreeNodeModel getDataSourceTreeNode(@RequestParam("dataSource") String dataSource,
                                              @RequestParam("columnLevel") boolean columnLevel,
                                              @RequestBody(required = false) AssetEntry expandedEntry,
                                              Principal principal)
      throws Exception
   {
      return queryManager.getDataSourceTreeNode(dataSource, columnLevel, expandedEntry, principal);
   }

   @GetMapping(value = "/api/data/datasource/query/data-source-fields-tree")
   public TreeNodeModel getDataSourceFieldsTreeNode(@RequestParam("runtimeId") String runtimeId,
                                                    Principal principal)
   {
      return queryManager.getDataSourceFieldsTreeNode(runtimeId, false, principal);
   }

   @GetMapping(value = "/api/data/datasource/query/column/sort/fields-tree")
   public TreeNodeModel getSortPaneFieldsTree(@RequestParam("runtimeId") String runtimeId,
                                              Principal principal)
   {
      return queryManager.getSortPaneFieldsTree(runtimeId, principal);
   }

   @PostMapping("/api/data/datasource/query/field/format")
   public String getFormatString(@RequestBody XFormatInfoModel format) {
      String formatStr = FormatInfoModel.getDurationFormat(format.getFormat(),
         format.isDurationPadZeros());
      XFormatInfo xFormat = new XFormatInfo(formatStr, format.getFormatSpec());
      return xFormat.toString();
   }

   @GetMapping(value = "/api/data/datasource/query/operations")
   public List<Operation> getOperations() {
      return ConditionUtil.getOperationList();
   }


   @GetMapping("/api/data/datasource/query/variables")
   public List<VariableAssemblyModelInfo> getQueryVariables(
                              @RequestParam("runtimeId") String runtimeId,
                              @RequestParam(value = "sqlString", required = false) String sqlString,
                              Principal principal)
      throws RemoteException
   {
      return queryManager.getQueryVariables(runtimeId, sqlString, principal);
   }

   @PostMapping("/api/data/datasource/query/variables/update")
   public void updateQueryVariables(@RequestParam("runtimeId") String runtimeId,
                                    @RequestBody() List<VariableAssemblyModelInfo> variables)
      throws Exception
   {
      queryManager.updateQueryVariables(runtimeId, variables);
   }

   @PostMapping("/api/data/datasource/query/get/columnInfo")
   public GetColumnInfoResult refreshColumnInfo(@RequestBody GetColumnInfoEvent event,
                                                Principal principal)
      throws Exception
   {
      return queryManager.refreshColumnInfo(event, principal);
   }

   @GetMapping("/api/data/datasource/query/clear/columnInfo")
   public void clearColumnInfo(@RequestParam("runtimeId") String runtimeId) {
      queryManager.clearColumnInfo(runtimeId);
   }

   @PostMapping("/api/data/datasource/query/save/freeSQLModel")
   public UpdateFreeFormSQLPaneResult setFreeFormSQLPaneModel(
                                                      @RequestBody UpdateFreeFormSQLPaneEvent event,
                                                      Principal principal)
   {
      String errorMsg = queryManager.setFreeFormSQLPaneModel(event, principal);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(event.getRuntimeId());
      UpdateFreeFormSQLPaneResult result = new UpdateFreeFormSQLPaneResult();
      result.setErrorMsg(errorMsg);
      result.setModel(queryManager.getAdvancedQueryModel(runtimeQuery));
      return result;
   }

   @GetMapping("/api/data/datasource/query/load/data")
   public String[][] loadQueryData(@RequestParam("runtimeId") String runtimeId,
                                               @RequestParam(value = "sqlString", required = false) String sqlString,
                                               Principal principal)
      throws Exception
   {
      return queryManager.loadQueryData(runtimeId, sqlString, principal);
   }

   @DeleteMapping("/api/data/datasource/query/runtime-query/destroy")
   public void destroyRuntimeQuery(@RequestParam("runtimeId") String runtimeId) {
      runtimeQueryService.destroy(runtimeId);
   }

   @PostMapping("/api/data/datasource/query/groupby/check")
   public boolean isValidGroupBy(@RequestParam("runtimeId") String runtimeId,
                                 @RequestBody() String[] groups)
   {
      return queryManager.isValidGroupBy(runtimeId, groups);
   }

   private final RuntimeQueryService runtimeQueryService;
   private final QueryManagerService queryManager;
}
