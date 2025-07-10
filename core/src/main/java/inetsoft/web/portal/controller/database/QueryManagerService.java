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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.*;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.*;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.RemoveQueryColumnEvent;

import java.awt.*;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QueryManagerService {

   @Autowired
   public QueryManagerService(RuntimeQueryService runtimeQueryService,
                              XRepository repository,
                              DataSourceService dataSourceService,
                              SecurityEngine securityEngine)
   {
      this.runtimeQueryService = runtimeQueryService;
      this.repository = repository;
      this.dataSourceService = dataSourceService;
      this.securityEngine = securityEngine;
   }

   public void updateQuery(String runtimeId, BasicSQLQueryModel queryModel,
                           String datasource, Principal principal)
      throws Exception
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);

      if(runtimeQuery == null) {
         throw new RuntimeException("runtime query do not exist");
      }

      JDBCQuery query = createQueryByBaseModel(queryModel, datasource, principal);
      runtimeQuery.setQuery(query);
   }

   public JDBCQuery createQueryByBaseModel(BasicSQLQueryModel queryModel, String dataSource,
                                           Principal principal)
      throws Exception
   {
      String[] selectedColumns = queryModel.getSelectedColumns();
      JDBCDataSource jdbcDataSource = (JDBCDataSource) repository.getDataSource(dataSource);
      XJoin[] xjoins = queryModel.toXJoins();
      UniformSQL sql = JDBCUtil.createSQL(jdbcDataSource, queryModel.getTables(), selectedColumns,
         xjoins, queryModel.getConditionList(), principal);
      JDBCQuery query = new JDBCQuery();
      query.setUserQuery(true);
      query.setDataSource(jdbcDataSource);

      if(queryModel.isSqlEdited()) {
         sql.setSQLString(queryModel.getSqlString());
      }

      query.setSQLDefinition(sql);
      return query;
   }

   public void updateQuery(String runtimeId, AdvancedSQLQueryModel queryModel,
                           String tab, boolean all)
      throws Exception
   {
      JDBCQuery query = getQuery(runtimeId);

      if(query == null || query.getSQLDefinition() == null) {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();

      if(DatabaseQueryTabs.FIELDS.getTab().equals(tab) || all) {
         // only need to update the order of fields and 'distinct' property
         QueryFieldPaneModel fieldPaneModel = queryModel.getFieldPaneModel();
         JDBCSelection newSelection = new JDBCSelection();
         JDBCSelection oldSelection = (JDBCSelection) sql.getSelection();
         List<QueryFieldModel> list = fieldPaneModel.getFields();

         for(int i = 0; list != null && i < list.size(); i++) {
            QueryFieldModel field = list.get(i);
            String name = field.getName();
            String alias = field.getAlias();
            int columnIndex = getSelectedColumnIndex(oldSelection, name, alias);

            if(columnIndex >= 0) {
               int newIndex = newSelection.addColumn(name);
               newSelection.setAlias(newIndex, alias);
               newSelection.setTable(name, oldSelection.getTable(name));
               newSelection.setType(name, oldSelection.getType(name));
               newSelection.setXMetaInfo(newIndex, oldSelection.getXMetaInfo(columnIndex));
               newSelection.setDescription(name, oldSelection.getDescription(name));
               newSelection.setExpression(newIndex, oldSelection.isExpression(columnIndex));
            }
         }

         sql.setSelection(newSelection);
         sql.setDistinct(fieldPaneModel.isDistinct());
      }

      if(DatabaseQueryTabs.CONDITIONS.getTab().equals(tab) || all) {
         QueryConditionPaneModel conditionPaneModel = queryModel.getConditionPaneModel();
         List<DataConditionItem> conditions = conditionPaneModel.getConditions();

         if(conditions != null && conditions.size() > 0) {
            XFilterNode filterNode = createConditionXFilterNode(conditions);
            setQueryCondition(sql, filterNode, CONDITION_WHERE);
         }
         else {
            setQueryCondition(sql, null, CONDITION_WHERE);
         }
      }

      if(DatabaseQueryTabs.SORT.getTab().equals(tab) || all) {
         QuerySortPaneModel sortPaneModel = queryModel.getSortPaneModel();
         List<String> fields = sortPaneModel.getFields();
         List<String> orders = sortPaneModel.getOrders();
         sql.removeAllOrderByFields();
         sql.clearOrderDBFields();

         for(int i = 0; i < fields.size(); i++) {
            if(!sql.isTableColumn(fields.get(i)) && !sql.getSelection().isAlias(fields.get(i))) {
               sql.addOrderDBField(fields.get(i));
            }

            sql.setOrderBy(fields.get(i), orders.get(i));
         }
      }

      if(DatabaseQueryTabs.GROUPING.getTab().equals(tab) || all) {
         QueryGroupingPaneModel groupingPaneModel = queryModel.getGroupingPaneModel();
         List<String> groupByFields = groupingPaneModel.getGroupByFields();
         List<String> groups = new ArrayList<>();
         sql.clearGroupDBFields();

         for(int i = 0; groupByFields != null && i < groupByFields.size(); i++) {
            String groupName = groupByFields.get(i);

            if(!sql.isTableColumn(groupName) && !sql.getSelection().isAlias(groupName)) {
               sql.addGroupDBField(groupName);
            }

            groups.add(groupName);
         }

         sql.setGroupBy(groups.toArray());

         QueryConditionPaneModel havingConditionsModel = groupingPaneModel.getHavingConditions();
         List<DataConditionItem> conditions = havingConditionsModel.getConditions();

         if(conditions != null) {
            XFilterNode filterNode = createConditionXFilterNode(conditions);
            setQueryCondition(sql, filterNode, CONDITION_HAVING);
         }
      }
   }

   public AdvancedSQLQueryModel getQueryModel(String runtimeId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);

      if(runtimeQuery != null) {
         return getAdvancedQueryModel(runtimeQuery);
      }

      return null;
   }

   public AdvancedSQLQueryModel getAdvancedQueryModel(
      RuntimeQueryService.RuntimeXQuery runtimeQuery)
   {
      if(runtimeQuery == null || runtimeQuery.getQuery() == null) {
         return null;
      }

      JDBCQuery query = runtimeQuery.getQuery();
      AdvancedSQLQueryModel model = new AdvancedSQLQueryModel();
      model.setName(query.getName());
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      QueryLinkPaneModel linkPaneModel = new QueryLinkPaneModel();
      List<QueryTableModel> tables = new ArrayList<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         QueryTableModel tableModel = new QueryTableModel();
         SelectTable selectTable = sql.getSelectTable(i);
         tableModel.setName(selectTable.getName().toString());
         tableModel.setAlias(selectTable.getAlias());
         tableModel.setQualifiedName(selectTable.getName().toString());
         tableModel.setCatalog(selectTable.getCatalog());
         tableModel.setSchema(selectTable.getSchema());
         tableModel.setLocation(selectTable.getLocation());
         tables.add(tableModel);
      }

      linkPaneModel.setTables(tables);
      model.setLinkPaneModel(linkPaneModel);

      QueryFieldPaneModel fieldPaneModel = new QueryFieldPaneModel();
      fieldPaneModel.setDistinct(sql.isDistinct());
      List<QueryFieldModel> queryFields = new ArrayList<>();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         QueryFieldModel fieldModel = new QueryFieldModel();
         String name = selection.getColumn(i);
         fieldModel.setName(name);
         fieldModel.setAlias(selection.getAlias(i));
         fieldModel.setDataType(selection.getType(name));
         fieldModel.setDrillInfo(getAutoDrillInfo(selection.getXMetaInfo(i)));
         fieldModel.setFormat(getFormatInfo(selection.getXMetaInfo(i)));
         queryFields.add(fieldModel);
      }

      fieldPaneModel.setFields(queryFields);
      model.setFieldPaneModel(fieldPaneModel);

      QueryConditionPaneModel conditionPaneModel = new QueryConditionPaneModel();
      List<Column> fields = new ArrayList<>();
      XField[] fieldList = sql.getFieldList(false);

      for(XField field : fieldList) {
         Column column = new Column();
         column.setName(field.getTable() + "." + field.getName());
         column.setColumnName(field.getName().toString());
         column.setTableName(field.getTable());
         String ptable = field.getPhysicalTable() != null ?
            field.getPhysicalTable() : field.getTable();
         Object obj = sql.getTableName(ptable);
         column.setPhysicalTableName(obj == null ? null : obj.toString());
         column.setType(field.getType());
         fields.add(column);
      }

      List<DataConditionItem> conditions = createConditions(sql, query.getDataSource(), CONDITION_WHERE);
      conditionPaneModel.setFields(fields);
      conditionPaneModel.setConditions(conditions);
      model.setConditionPaneModel(conditionPaneModel);

      XTypeNode metadata = runtimeQuery.getMetadata();
      FreeFormSQLPaneModel freeFormSQLPaneModel = new FreeFormSQLPaneModel();
      freeFormSQLPaneModel.setHasSqlString(sql.hasSQLString());
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      freeFormSQLPaneModel.setSqlString(sql.getSQLString());
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false);
      freeFormSQLPaneModel.setParseSql(sql.isParseSQL());
      freeFormSQLPaneModel.setParseResult(sql.getParseResult());
      freeFormSQLPaneModel.setHasColumnInfo(metadata != null && metadata.getChildCount() > 0);
      SQLHelper helper = SQLHelper.getSQLHelper(sql);
      freeFormSQLPaneModel.setGeneratedSqlString(helper.generateSentence());
      model.setFreeFormSQLPaneModel(freeFormSQLPaneModel);

      QuerySortPaneModel sortPaneModel = new QuerySortPaneModel();
      List<String> sortFields = new ArrayList<>();
      List<String> orders = new ArrayList<>();
      OrderByItem[] orderByItems = sql.getOrderByItems();

      for(int i = 0; i < orderByItems.length; i++) {
         OrderByItem item = orderByItems[i];
         sortFields.add(Tool.toString(item.getField()));
         orders.add(item.getOrder());
      }

      sortPaneModel.setFields(sortFields);
      sortPaneModel.setOrders(orders);
      model.setSortPaneModel(sortPaneModel);

      QueryGroupingPaneModel groupingPaneModel = new QueryGroupingPaneModel();
      List<String> groupByFields = new ArrayList<>();
      Object[] groups = sql.getGroupBy();

      if(groups != null) {
         for(Object group : groups) {
            groupByFields.add(Tool.toString(group));
         }
      }

      List<Column> havingFields = new ArrayList<>();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         Column column = new Column();
         String columnPath = selection.getColumn(i);
         column.setName(columnPath);
         String tableName = selection.getTable(columnPath);
         column.setTableName(tableName);
         String columnName = selection.isExpression(i) || tableName == null ?
            columnPath : columnPath.substring(tableName.length() + 1);
         column.setColumnName(columnName);
         Object obj = sql.getTableName(tableName);
         column.setPhysicalTableName(obj == null ? null : obj.toString());
         column.setType(selection.getType(columnPath));
         havingFields.add(column);
      }

      QueryConditionPaneModel havingConditionsPaneModel = new QueryConditionPaneModel();
      List<DataConditionItem> havingConditions =
         createConditions(sql, query.getDataSource(), CONDITION_HAVING);
      havingConditionsPaneModel.setFields(havingFields);
      havingConditionsPaneModel.setConditions(havingConditions);

      groupingPaneModel.setGroupByFields(groupByFields);
      groupingPaneModel.setHavingConditions(havingConditionsPaneModel);
      model.setGroupingPaneModel(groupingPaneModel);

      return model;
   }

   public SQLQueryDialogModel clearQuery(String runtimeId, String dataSource, String tableName,
                                         boolean advancedEdit, Principal principal)
      throws Exception
   {
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setRuntimeId(runtimeId);
      model.setName(tableName);
      model.setDataSource(dataSource);
      model.setAdvancedEdit(advancedEdit);

      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         createNewRuntimeQuery(runtimeId, tableName, dataSource);

      if(advancedEdit) {
         AdvancedSQLQueryModel advancedModel = getAdvancedQueryModel(runtimeQuery);
         model.setAdvancedModel(advancedModel);
      }
      else {
         BasicSQLQueryModel basicModel = getBasicSQLQueryModel(runtimeQuery, null);
         model.setSimpleModel(basicModel);
      }

      SecurityEngine security = SecurityEngine.getSecurity();

      for(String dsName : repository.getDataSourceFullNames()) {
         if(repository.getDataSource(dsName) instanceof JDBCDataSource) {
            if(security.checkPermission(principal, ResourceType.DATA_SOURCE, dsName,
               ResourceAction.READ))
            {
               model.getDataSources().add(dsName);
               model.getSupportsFullOuterJoin().add(supportsFullOuterJoin(dsName));
            }
         }
      }

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

   public String addTable(UniformSQL sql, AssetEntry table, Point position) {
      String name = table.getProperty("source_with_no_quote");
      String alias = getTableAlias(sql, name);
      SelectTable stable = sql.addTable(alias, name, position, null);
      stable.setCatalog(table.getProperty(XSourceInfo.CATALOG));
      stable.setSchema(table.getProperty(XSourceInfo.SCHEMA));
      return alias;
   }

   /**
    * Add columns to the query.
    * @return a map of column names and aliases.(key -> alias, value -> name)
    */
   public AddColumnInfoResult addColumns(String runtimeId, List<AssetEntry> columns) {
      AddColumnInfoResult result = new AddColumnInfoResult();

      if(columns == null || columns.isEmpty()) {
         return result;
      }

      int max = Util.getOrganizationMaxColumn();
      JDBCQuery query = getQuery(runtimeId);

      if(query == null) {
         return result;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      SQLHelper sqlHelper = SQLHelper.getSQLHelper(sql);
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      Map<String, String> nameAliasMap = new LinkedHashMap<>();
      List<String> aliases = getColumnAliases(selection);

      for(AssetEntry col : columns) {
         if(selection.getColumnCount() >= max) {
            result.setLimitMessage(Util.getColumnLimitMessage());
            break;
         }

         String alias = col.getProperty("attribute");
         int counter = 0;

         if(aliases.contains(alias)) {
            counter++;

            while(aliases.contains(alias + "_" + counter)) {
               counter++;
            }
         }

         alias = counter > 0 ? alias + "_" + counter : alias;
         aliases.add(alias);
         String path = getColumnPath(col);
         int index = selection.addColumn(path);
         selection.setTable(path, XUtil.getTablePart(path, sql));
         selection.setAlias(index, selection.getValidAlias(index, alias, sqlHelper));
         selection.setType(path, getColumnDataType(col));
         nameAliasMap.put(alias, path);
      }

      result.setColumnMap(nameAliasMap);

      return result;
   }

   public void removeColumns(RemoveQueryColumnEvent event) {
      if(event.getNames() == null || event.getNames().isEmpty()) {
         return;
      }

      JDBCQuery query = getQuery(event.getRuntimeId());

      if(query == null) {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      JDBCSelection newSelection = new JDBCSelection();
      sql.setSelection(newSelection);
      List<String> names = event.getNames();
      List<String> aliases = event.getAliases();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String selectionName = selection.getColumn(i);
         String selectionAlias = selection.getAlias(i);
         boolean remove = false;

         for(int j = 0; j < names.size(); j++) {
            String name = names.get(j);
            String alias = aliases.get(j);

            if(Tool.equals(selectionName, name) && Tool.equals(selectionAlias, alias)) {
               remove = true;
               break;
            }
         }

         if(!remove) {
            int index = newSelection.addColumn(selectionName);
            newSelection.setTable(selectionName, selection.getTable(selectionName));
            newSelection.setAlias(index, selectionAlias);
            newSelection.setType(selectionName, selection.getType(selectionName));
            newSelection.setXMetaInfo(index, selection.getXMetaInfo(i));
            newSelection.setExpression(index, selection.isExpression(i));
         }
      }
   }

   public void updateColumn(String runtimeId, QueryFieldModel column, String type, String oldAlias)
   {
      if(column == null || type == null) {
         return;
      }

      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryService.getRuntimeQuery(runtimeId);

      if(runtimeQuery == null) {
         return;
      }

      JDBCQuery query = runtimeQuery.getQuery();

      if(query == null) {
         return;
      }

      Map<String, String> aliasMapping = runtimeQuery.getAliasMapping();
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      String name = column.getName();
      String alias = column.getAlias();

      if("alias".equals(type)) {
         for(int i = 0; i < selection.getColumnCount(); i++) {
            String selectionName = selection.getColumn(i);
            String selectionAlias = selection.getAlias(i);

            if(Tool.equals(selectionName, name) && Tool.equals(selectionAlias, oldAlias)) {
               String originalAlias = getOriginalAlias(aliasMapping, oldAlias);

               if(originalAlias != null) {
                  runtimeQuery.putAliasMapping(originalAlias, alias);
               }

               selection.setAlias(i, alias);
               break;
            }
         }

         sql.updateOrderAndGroupFields(oldAlias, alias);
      }
      else if("dataType".equals(type)) {
         selection.setType(name, column.getDataType());
         fixUniformSQLInfo(sql, (JDBCDataSource) query.getDataSource(), ThreadContext.getContextPrincipal());
      }
      else if("drill".equals(type)) {
         XDrillInfo xDrillInfo = DatabaseModelUtil.createXDrillInfo(column.getDrillInfo());
         XMetaInfo metaInfo = getSelectedColumnMetaInfo(selection, name, alias);

         if(metaInfo == null) {
            metaInfo = new XMetaInfo();
            int index = getSelectedColumnIndex(selection, name, alias);
            selection.setXMetaInfo(index, metaInfo);
         }

         metaInfo.setXDrillInfo(xDrillInfo);
      }
      else if("format".equals(type)) {
         XFormatInfo formatInfo = getXFormatInfo(column.getFormat());
         XMetaInfo metaInfo = getSelectedColumnMetaInfo(selection, name, alias);

         if(metaInfo == null) {
            metaInfo = new XMetaInfo();
            int index = getSelectedColumnIndex(selection, name, alias);
            selection.setXMetaInfo(index, metaInfo);
         }

         metaInfo.setXFormatInfo(formatInfo);
      }
   }

   public List<String> browseColumnData(String runtimeId, String col, boolean aliasColumn,
                                        boolean quote)
   {
      List<String> values = new ArrayList<>();
      JDBCQuery query = getQuery(runtimeId);

      if(query == null) {
         return values;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCDataSource xds = (JDBCDataSource) query.getDataSource();

      try {
         if(aliasColumn) {
            JDBCSelection selection = (JDBCSelection) sql.getSelection();
            String realCol = selection.getAliasColumn(col);

            // use real name instead of alias
            if(realCol != null) {
               col = realCol;
            }
         }

         int dot = col.lastIndexOf('.');

         if(dot < 0 || sql.getSelection().isExpression(col)) {
            // could be an expression, ignore
            return null;
         }

         String column = col.substring(dot + 1);
         String ctbl = sql.getFieldByPath(col).getTable();
         String dataType = Tool.isEmptyString(sql.getSelection().getType(col)) ?
            XField.STRING_TYPE : sql.getSelection().getType(col);

         if(ctbl != null && col.startsWith(ctbl + ".")) {
            column = col.substring(ctbl.length() + 1);
         }

         // if table is empty, this is an expression
         if(ctbl == null || ctbl.length() == 0) {
            return null;
         }

         Object tableName = sql.getTableName(ctbl);

         if((tableName instanceof String) && tableName.toString().length() > 0) {
            ctbl = tableName.toString();
         }

         return getBrowseData(sql, ctbl, column, dataType, xds, quote);
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return values;
   }

   public List<String> getBrowseData(UniformSQL sql, String tableName, String columnName, String columnType,
                                     JDBCDataSource xds, boolean quote)
      throws Exception
   {
      List<String> results = new ArrayList<>();
      JDBCQuery query = new JDBCQuery();
      String colPath = tableName + "." + columnName;
      UniformSQL browseSql = createUniformSQL(sql, tableName, colPath);
      query.setSQLDefinition(browseSql);
      query.setDataSource(xds);

      ColumnCache cache = ColumnCache.getColumnCache();
      String[][] data = cache.getColumnDataString(
         query, tableName, colPath, columnType, null, null, quote);

      if(XSchema.STRING.equals(columnType)) {
         SQLHelper helper = SQLHelper.getSQLHelper(xds);

         for(int i = 0; i < data[0].length; i++) {
            data[0][i] = helper.fixStringLiteral(data[0][i]);
            data[1][i] = helper.fixStringLiteral(data[1][i]);
         }
      }

      boolean isDate = XSchema.isDateType(columnType);

      for(int i = 0; i < data[0].length; i++) {
         String val = data[0][i];

         if(val != null) {
            results.add(isDate && !quote ? Tool.toString(Tool.getData(columnType, val)) : val);
         }
      }

      return results;
   }

   public SQLQueryDialogModel getSqlQueryDialogModel(RuntimeWorksheet rws, String assemblyName,
                                                     String dataSource, Principal principal)
      throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      SQLBoundTableAssembly assembly = (SQLBoundTableAssembly) ws.getAssembly(assemblyName);
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      JDBCQuery query = null;

      if(assembly != null) {
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) assembly.getTableInfo();
         query = info.getQuery();
      }

      query = query == null ? createNewQuery(null, dataSource) : query;
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.createRuntimeQuery(rws, query, dataSource, principal);
      query = runtimeQuery.getQuery();
      runtimeQuery.initQueryAliasMapping();
      initQuerySelectedTables(runtimeQuery);
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCUtil.fixTableLocation(sql);
      fixUniformSQLInfo(sql, (JDBCDataSource) query.getDataSource(), principal);

      model.setRuntimeId(runtimeQuery.getId());
      model.setDataSource(query.getDataSource() == null ? null :
         query.getDataSource().getFullName());

      if(assembly != null) {
         model.setAdvancedEdit(assembly.isAdvancedEditing());

         if(assembly.isAdvancedEditing()) {
            AdvancedSQLQueryModel advancedModel = getAdvancedQueryModel(runtimeQuery);
            model.setAdvancedModel(advancedModel);
         }
         else {
            BasicSQLQueryModel basicModel = getBasicSQLQueryModel(runtimeQuery, assembly);
            model.setSimpleModel(basicModel);
         }
      }
      else {
         model.setSimpleModel(new BasicSQLQueryModel());
      }

      model.setName(assemblyName);
      model.setDataSource(dataSource);
      model.setVariableNames(getVariableList(rws, principal));

      SecurityEngine security = SecurityEngine.getSecurity();

      for(String dsName : repository.getDataSourceFullNames()) {
         if(repository.getDataSource(dsName) instanceof JDBCDataSource) {
            if(security.checkPermission(principal, ResourceType.DATA_SOURCE, dsName,
               ResourceAction.READ))
            {
               model.getDataSources().add(dsName);
               model.getSupportsFullOuterJoin().add(supportsFullOuterJoin(dsName));
            }
         }
      }

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

   /**
    * Get a list of variable assembly names.
    */
   private String[] getVariableList(RuntimeWorksheet rws, Principal principal) {
      Worksheet ws = rws.getWorksheet();
      ArrayList<String> list = new ArrayList<>();
      Set<String> added = new HashSet<>();

      for(Assembly assembly : ws.getAssemblies()) {
         WSAssembly wsAssembly = (WSAssembly) assembly;

         if(!wsAssembly.isVariable() || !wsAssembly.isVisible()) {
            continue;
         }

         VariableAssembly vassembly = (VariableAssembly) wsAssembly;
         UserVariable var = vassembly.getVariable();

         if(var != null && !added.contains(var.getName())) {
            added.add(var.getName());
            list.add(var.getName());
         }
      }

      // for bug1291823096435, display the parameters which is defined in
      // SRPrincipal
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
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

   /**
    * Check if the given datasource supports full outer join.
    */
   private boolean supportsFullOuterJoin(String dataSource) {
      try {
         XDataSource dx = repository.getDataSource(dataSource);

         if(dx != null) {
            SQLHelper helper = SQLHelper.getSQLHelper(dx);

            if(!helper.supportsOperation(SQLHelper.FULL_OUTERJOIN)) {
               return false;
            }
         }
      }
      catch(Exception ex) {
         // do nothing
      }

      return true;
   }

   private BasicSQLQueryModel getBasicSQLQueryModel(RuntimeQueryService.RuntimeXQuery runtimeQuery,
                                                    SQLBoundTableAssembly assembly)
   {
      BasicSQLQueryModel model = new BasicSQLQueryModel();

      if(runtimeQuery == null || assembly == null) {
         return model;
      }

      model.setSqlParseResult(Catalog.getCatalog().getString("designer.qb.parseInit"));

      JDBCQuery query = runtimeQuery.getQuery();

      if(query.getDataSource() != null) {
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();
         model.setSqlEdited(assembly.isSQLEdited());
         model.setSqlString(sql.getSQLString());
         model.setSqlParseResult(JDBCUtil.getParseResult(sql.getParseResult()));

         if(!assembly.isSQLEdited()) {
            model.convertColumnInfo(getSelectedColumns(sql));
            model.convertJoins(sql.getJoins(), sql);
            model.convertTables(runtimeQuery.getSelectedTables().values().toArray(new AssetEntry[0]));
            model.setConditionList(createConditions(sql, query.getDataSource(), CONDITION_WHERE));
         }
      }
      else {
         throw new MessageException(
            Catalog.getCatalog().getString("common.sqlquery.datasourceNotFound"));
      }

      return model;
   }

   public BasicSQLQueryModel convertToSimpleQueryModel(SQLQueryDialogModel model,
                                                       String runtimeQueryId)
      throws Exception
   {
      AdvancedSQLQueryModel advancedModel = model.getAdvancedModel();
      updateQuery(runtimeQueryId, advancedModel, null, true);

      return processConvertToSimpleQuery(runtimeQueryId);
   }

   public AdvancedSQLQueryModel convertToAdvancedQueryModel(RuntimeWorksheet rws,
                                                            SQLQueryDialogModel model,
                                                            Principal principal)
      throws Exception
   {
      String name = model.getName();
      String database = model.getDataSource();
      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(model.getRuntimeId());

      JDBCQuery query = createNewQuery(name, database);
      runtimeQuery.setQuery(query);
      TableAssembly assembly = (TableAssembly) rws.getWorksheet().getAssembly(model.getName());

      if(assembly != null) {
         int queryPreviewMaxrow = Util.getQueryPreviewMaxrow();
         int maxrow = assembly.getMaxDisplayRows();
         runtimeQuery.setMaxPreviewRow(Math.max(queryPreviewMaxrow, maxrow));
      }

      BasicSQLQueryModel simpleModel = model.getSimpleModel();

      if(!simpleModel.isSqlEdited()) {
         String[] selectedColumns =
            simpleModel.getColumns() != null ? simpleModel.getSelectedColumns() : new String[0];
         UniformSQL sql = JDBCUtil.createSQL(dataSource, simpleModel.getTables(),
            selectedColumns, simpleModel.toXJoins(), simpleModel.getConditionList(), principal);
         JDBCUtil.fixTableLocation(sql);
         fixUniformSQLInfo(sql, dataSource, principal);
         query.setSQLDefinition(sql);

         if(simpleModel.getTables() != null) {
            runtimeQuery.setSelectedTables(simpleModel.getTables());
         }
      }
      else {
         parseSqlString(model.getRuntimeId(), simpleModel.getSqlString(), false, true, principal);
      }

      AdvancedSQLQueryModel sqlQueryModel = getAdvancedQueryModel(runtimeQuery);
      AdvancedSQLQueryModel oldSqlQueryModel = model.getAdvancedModel();

      if(oldSqlQueryModel != null && oldSqlQueryModel.getConditionPaneModel() != null) {
         sqlQueryModel.setConditionPaneModel(oldSqlQueryModel.getConditionPaneModel());
      }

      return sqlQueryModel;
   }

   public boolean isExpression(String runtimeId, String column) {
      JDBCQuery query = getQuery(runtimeId);

      if(query == null) {
         return false;
      }

      JDBCSelection select = (JDBCSelection) query.getSQLDefinition().getSelection();
      String realColumn = select.getAliasColumn(column);
      return realColumn.lastIndexOf('.') < 0 || select.isExpression(realColumn);
   }

   public boolean checkExpression(String expression) {
      expression = expression.trim();
      SQLLexer lexer = new SQLLexer(new StringReader(expression));
      SQLParser parser = new SQLParser(lexer);

      try {
         parser.value_exp();
      }
      catch(Exception ex) {
         return false;
      }

      String token = null;

      try {
         token = lexer.nextToken().toString();
      }
      catch(Exception ex) {
         return false;
      }

      if(token != null && !token.toLowerCase().contains("null")) {
         return false;
      }

      return true;
   }

   public String[] saveExpression(String runtimeId, String expression, String columnName,
                              String columnAlias, boolean add, Principal principal)
   {
      if(expression == null || expression.isEmpty() || expression.trim().isEmpty()) {
         return null;
      }

      JDBCQuery query = getQuery(runtimeId);

      if(query == null) {
         return null;
      }

      expression = expression.trim();
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      if(add) {
         return addExpression(sql, selection, expression, (JDBCDataSource) query.getDataSource(),
            principal);
      }
      else {
         return editExpression(sql, selection, expression, columnName, columnAlias);
      }
   }

   public String[] addExpression(UniformSQL sql, JDBCSelection selection, String expression,
                                 JDBCDataSource dataSource, Principal principal)
   {
      int index = selection.addColumn(expression);
      fixUniformSQLInfo(sql, dataSource, principal);
      selection = (JDBCSelection) sql.getSelection();
      List<String> columnAliases = getColumnAliases(selection);
      SQLHelper sqlHelper = SQLHelper.getSQLHelper(sql);
      boolean tableColumn = sql.isTableColumn(expression);
      String alias = null;

      if(!tableColumn) {
         int counter = 1;
         alias = "exp_";

         while(columnAliases.contains(alias + counter)) {
            counter++;
         }

         alias = alias + counter;
         alias = selection.getValidAlias(index, alias, sqlHelper);
         selection.setAlias(index, alias);
         selection.setType(expression, XField.STRING_TYPE);
         selection.setExpression(index, true);
         XField fld = new XField(null, expression, "", XField.STRING_TYPE);
         sql.addField(fld);
      }
      else {
         int counter = 0;
         alias = expression.substring(expression.lastIndexOf(".") + 1);

         if(columnAliases.contains(alias)) {
            counter++;

            while(columnAliases.contains(alias + "_" + counter)) {
               counter++;
            }
         }

         alias = counter > 0 ? alias + "_" + counter : alias;
         alias = selection.getValidAlias(index, alias, sqlHelper);
         String table = XUtil.getTablePart(expression, sql);
         selection.setAlias(index, alias);
         selection.setTable(expression, table);
         selection.setType(expression, selection.getType(expression));
      }

      return new String[] {alias, expression};
   }

   public String[] editExpression(UniformSQL sql, JDBCSelection selection, String expression,
                              String columnName, String columnAlias)
   {
      int columnIndex = getSelectedColumnIndex(selection, columnName, columnAlias);

      if(columnIndex == -1) {
         return null;
      }

      String type = selection.getType(columnName);
      XMetaInfo meta = sql.getSelection().getXMetaInfo(columnIndex);
      XField xf = sql.getFieldByPath(columnName);

      if(xf == null) {
         xf = sql.getField(columnAlias);
      }

      if(xf.getTable().length() == 0) { // was an expression
         xf.setName(expression);
      }
      else {  // was a column
         sql.addField(new XField(columnAlias, expression, "", type));
      }

      sql.setAlias(columnIndex, columnAlias);
      selection.setColumn(columnIndex, expression);
      selection.setType(expression, type);
      selection.setXMetaInfo(columnIndex, meta);

      return new String[] {columnAlias, expression};
   }

   public void fixUniformSQLInfo(UniformSQL sql, JDBCDataSource xds, Principal principal) {
      if(xds == null) {
         return;
      }

      String session = System.getProperty("user.name");
      sql.removeAllFields();

      try {
         JDBCUtil.fixUniformSQLInfo(sql, repository, session, xds, principal);
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   public void destroyRuntimeQuery(String runtimeId) {
      runtimeQueryService.destroy(runtimeId);
   }

   public void clearRuntimeQuery() {
      runtimeQueryService.clear();
   }

   private JDBCQuery createNewQuery(String name, String database) {
      JDBCQuery query = new JDBCQuery(OrganizationManager.getInstance().getCurrentOrgID());
      query.setName(name);
      query.setUserQuery(true);
      query.setDataSource(dataSourceService.getDataSource(database));
      query.setSQLDefinition(new UniformSQL());
      return query;
   }

   private BasicSQLQueryModel processConvertToSimpleQuery(String runtimeQueryId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeQueryId);
      JDBCQuery query = runtimeQuery.getQuery();
      BasicSQLQueryModel model = new BasicSQLQueryModel();

      if(query.getDataSource() == null) {
         return model;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      model.setSqlString(sql.getSQLString());
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false);
      model.setSqlParseResult(JDBCUtil.getParseResult(sql.getParseResult()));
      model.setSqlEdited(true);

      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         selection.setXMetaInfo(i, new XMetaInfo());
      }

      return model;
   }

   private String getTableAlias(UniformSQL sql, String name) {
      String alias = name;

      if(sql.getTableIndex(alias) < 0) {
         return alias;
      }

      for(int j = 1;; j++) {
         if(sql.getTableIndex(name + j) < 0) {
            alias = name + j;
            break;
         }
      }

      return alias;
   }

   private List<String> getColumnAliases(JDBCSelection selection) {
      List<String> aliases = new ArrayList<>();

      if(selection == null) {
         return aliases;
      }

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String alias = selection.getAlias(i);

         if(!StringUtils.isEmpty(alias)) {
            aliases.add(alias);
         }
      }

      return aliases;
   }

   private String getColumnPath(AssetEntry entry) {
      String alias = entry.getProperty(SOURCE_ALIAS);
      alias = alias == null ? entry.getProperty("source_with_no_quote") : alias;

      return alias + "." + entry.getProperty("attribute");
   }

   private String getColumnDataType(AssetEntry entry) {
      String type = XField.STRING_TYPE;
      return entry.getProperty("dtype") != null ? entry.getProperty("dtype") : type;
   }

   public JDBCQuery getQuery(String runtimeId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = getRuntimeQuery(runtimeId);

      if(runtimeQuery == null) {
         return null;
      }

      return runtimeQuery.getQuery();
   }

   public RuntimeQueryService.RuntimeXQuery getRuntimeQuery(String runtimeId) {
      return runtimeQueryService.getRuntimeQuery(runtimeId);
   }

   private AutoDrillInfo getAutoDrillInfo(XMetaInfo metaInfo) {
      if(metaInfo == null || metaInfo.isXDrillInfoEmpty()) {
         return new AutoDrillInfo();
      }

      XDrillInfo xDrillInfo = metaInfo.getXDrillInfo();
      AutoDrillInfo drillInfo = new AutoDrillInfo();
      Enumeration<DrillPath> drillPaths = xDrillInfo.getDrillPaths();
      List<AutoDrillPathModel> paths = new ArrayList<>();

      while(drillPaths.hasMoreElements()) {
         DrillPath path = drillPaths.nextElement();
         AutoDrillPathModel autoPath = new AutoDrillPathModel();
         autoPath.setName(path.getName());
         autoPath.setLink(path.getLink());
         autoPath.setTargetFrame(path.getTargetFrame());
         autoPath.setTip(path.getToolTip());
         autoPath.setPassParams(path.isSendReportParameters());
         autoPath.setDisablePrompting(path.isDisablePrompting());
         autoPath.setLinkType(path.getLinkType());

         Enumeration<String> parameters = path.getParameterNames();
         List<DrillParameterModel> params = new ArrayList<>();

         while(parameters.hasMoreElements()) {
            DrillParameterModel param = new DrillParameterModel();
            String name = parameters.nextElement();
            param.setName(name);
            param.setField(path.getParameterField(name));
            param.setType(path.getParameterType(name));
            params.add(param);
         }

         autoPath.setParams(params);
         autoPath.setQuery(DatabaseModelUtil.createDrillSubQueryModel(path.getQuery()));
         paths.add(autoPath);
      }

      drillInfo.setPaths(paths);
      return drillInfo;
   }

   private XFormatInfoModel getFormatInfo(XMetaInfo metaInfo) {
      if(metaInfo == null || metaInfo.isXFormatInfoEmpty()) {
         XFormatInfoModel xFormatInfoModel = new XFormatInfoModel();
         xFormatInfoModel.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

         return xFormatInfoModel;
      }

      XFormatInfo xFormatInfo = metaInfo.getXFormatInfo();
      XFormatInfoModel formatInfo = new XFormatInfoModel();
      formatInfo.setFormat(xFormatInfo.getFormat());
      formatInfo.setFormatSpec(xFormatInfo.getFormatSpec());
      formatInfo.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

      return formatInfo;
   }

   private XFormatInfo getXFormatInfo(XFormatInfoModel xformat) {
      if(xformat == null) {
         return new XFormatInfo();
      }

      String format = FormatInfoModel.getDurationFormat(xformat.getFormat(),
         xformat.isDurationPadZeros());

      return new XFormatInfo(format, xformat.getFormatSpec());
   }

   private int getSelectedColumnIndex(JDBCSelection selection, String columnName,
                                      String columnAlias)
   {
      if(selection == null || columnName == null) {
         return -1;
      }

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String column = selection.getColumn(i);
         String alias = selection.getAlias(i);

         if(Tool.equals(columnName, column) && Tool.equals(columnAlias, alias)) {
            return i;
         }
      }

      return -1;
   }

   private XMetaInfo getSelectedColumnMetaInfo(JDBCSelection selection, String columnName,
                                               String columnAlias)
   {
      int columnIndex = getSelectedColumnIndex(selection, columnName, columnAlias);

      if(columnIndex >= 0) {
         return selection.getXMetaInfo(columnIndex, false);
      }

      return null;
   }

   private void setQueryCondition(UniformSQL sql, XFilterNode filterNode, int conditionType) {
      switch(conditionType) {
         case CONDITION_WHERE:
            synchronized(sql) {
               XJoin[] joins = sql.getJoins();
               sql.setWhere(filterNode);

               if(joins != null) {
                  for(int i = 0; i < joins.length; i++) {
                     sql.addJoin(joins[i]);
                  }
               }

               break;
            }
         case CONDITION_HAVING:
            sql.setHaving(filterNode);
            break;
      }
   }

   private XFilterNode getQueryCondition(UniformSQL sql, int conditionType) {
      if(sql == null) {
         return null;
      }

      switch(conditionType) {
         case CONDITION_WHERE:
            XFilterNode oldWhere = sql.getWhere();

            if(oldWhere != null) {
               nameInt = 0;
               setNodesName(oldWhere);
               XFilterNode newWhere = (XFilterNode) oldWhere.clone();
               newWhere.removeAllJoins();

               if(newWhere instanceof XJoin) {
                  return null;
               }

               return newWhere;
            }

            break;
         case CONDITION_HAVING:
            return sql.getHaving();
         default:
            return null;
      }

      return null;
   }

   private void setNodesName(XNode node) {
      node.setName("con" + (++nameInt));

      for(int i = 0; i < node.getChildCount(); i++) {
         setNodesName(node.getChild(i));
      }
   }

   private List<DataConditionItem> createConditions(UniformSQL sql, XDataSource datasource,
                                                    int conditionType)
   {
      List<DataConditionItem> conditions = new ArrayList<>();
      XFilterNode queryCondition = getQueryCondition(sql, conditionType);
      HierarchyList hl = new FilterList();

      for(HierarchyItem item : XUtil.constructConditionList(queryCondition)) {
         hl.append(item);
      }

      hl.validate(false);

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
            conditions.add(con);
         }
         else {
            XFilterNode xfilter = ((XFilterNodeItem) hi).getNode();
            Clause clause = JDBCUtil.createCondition(xfilter, datasource);
            clause.setLevel(level);
            conditions.add(clause);
         }
      }

      return conditions;
   }

   private XFilterNode createConditionXFilterNode(List<DataConditionItem> conditions)
      throws Exception
   {
      HierarchyList hl = new FilterList();
      HierarchyListModel clm = new HierarchyListModel(hl);

      for(DataConditionItem ci : conditions) {
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
      return handler.createXFilterNode(clm.getHierarchyList());
   }

   public List<VariableAssemblyModelInfo> getQueryVariables(String runtimeId, String nSqlString,
                                                            Principal principal)
      throws RemoteException
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);
      JDBCQuery query = runtimeQuery.getQuery();

      if(!StringUtils.isEmpty(nSqlString)) {
         query = query.clone();
         UniformSQL nsql = new UniformSQL();
         query.setSQLDefinition(nsql);
         nsql.setParseSQL(false);
         nsql.setSQLString(!StringUtils.isEmpty(nSqlString) ? nSqlString : query.getSQLAsString());
      }

      XDataService service = XFactory.getDataService();
      UserVariable[] vars = service.getQueryParameters(principal.getName(), query, true);

      if(vars == null) {
         return null;
      }

      List<UserVariable> list = new ArrayList<>();
      VariableTable vtable = runtimeQuery.getVariables();

      for(int i = 0; i < vars.length; i++) {
         if(principal instanceof XPrincipal) {
            XPrincipal xuser = (XPrincipal) principal;

            if(vars[i].getName().startsWith("_Db_") && xuser.getProperty(vars[i].getName()) != null)
            {
               continue;
            }
         }

         if(!vtable.contains(vars[i].getName())) {
            list.add(vars[i]);
         }
      }

      return list.stream()
         .map(VariableAssemblyModelInfo::new)
         .collect(Collectors.toList());
   }

   public void updateQueryVariables(String runtimeId, List<VariableAssemblyModelInfo> variables)
      throws Exception
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryService.getRuntimeQuery(runtimeId);
      VariableTable vtable = runtimeQuery.getVariables();
      vtable.addAll(VariableAssemblyModelInfo.getVariableTable(variables));
   }

   public GetColumnInfoResult refreshColumnInfo(GetColumnInfoEvent event,
                                                Principal principal)
      throws Exception
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(event.getRuntimeId());
      String sqlString = event.getSqlString();
      JDBCQuery query = runtimeQuery.getQuery();
      JDBCQuery nquery = query.clone();
      UniformSQL nsql = new UniformSQL();
      nsql.setParseSQL(false);
      nsql.setSQLString(sqlString);
      nquery.setSQLDefinition(nsql);

      VariableTable vtable = runtimeQuery.getVariables();
      XTypeNode parseResult = new XTypeNode("table");
      parseResult.setMinOccurs(0);
      parseResult.setMaxOccurs(XTypeNode.STAR);
      parseResult = nquery.getOutputTypeForNonParseableSQL(parseResult, vtable, principal.getName());
      runtimeQuery.setMetadata(parseResult);
      SQLDefinition sqlDefinition = query.getSQLDefinition();
      UniformSQL sql = (UniformSQL) sqlDefinition;

      synchronized(sql) {
         sql.setSQLString(sqlString);

         // it is waiting for the parser finish parsing the sql string.
         if(!Tool.equals(sqlString, sql.getSQLString())) {
            try {
               sql.wait();
            }
            catch(InterruptedException e) {
            }
         }
      }

      GetColumnInfoResult result = new GetColumnInfoResult();

      if(parseResult != null && parseResult.getChildCount() > 0) {
         XField[] flds = new XField[parseResult.getChildCount()];

         for(int i = 0; i < flds.length; i++) {
            XTypeNode node = (XTypeNode) parseResult.getChild(i);
            flds[i] = new XField(node.getName());
            flds[i].setType(node.getType());
         }

         sql.setColumnInfo(flds);
         sql.getSelection().clear();
         result.setHasColumnInfo(true);
      }
      else {
         sql.setColumnInfo(null);
      }

      initQuerySelectedTables(runtimeQuery);

      return result;
   }

   public void clearColumnInfo(String runtimeId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);
      JDBCQuery query = runtimeQuery.getQuery();
      SQLDefinition sqlDefinition = query.getSQLDefinition();
      UniformSQL sql = (UniformSQL) sqlDefinition;
      sql.setColumnInfo(null);
   }

   public String setFreeFormSQLPaneModel(UpdateFreeFormSQLPaneEvent event, Principal principal) {
      String runtimeId = event.getRuntimeId();
      boolean executeQuery = event.isExecuteQuery();
      FreeFormSQLPaneModel paneModel = event.getFreeFormSqlPaneModel();
      boolean parseSql = paneModel.isParseSql();
      String sqlString = paneModel.getSqlString();

      return parseSqlString(runtimeId, sqlString, executeQuery, parseSql, principal);
   }

   public String parseSqlString(String runtimeId, String nsqlString, boolean executeQuery,
                                boolean parseSql, Principal principal)
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);
      JDBCQuery query = runtimeQuery.getQuery();
      SQLDefinition sqlDefinition = query.getSQLDefinition();
      UniformSQL sql = (UniformSQL) sqlDefinition;
      sql.setParseSQL(parseSql);

      if(!parseSql) {
         XTypeNode metadata = runtimeQuery.getMetadata();
         if(metadata != null && metadata.getChildCount() > 0) {
            XField[] flds = new XField[metadata.getChildCount()];

            for(int i = 0; i < flds.length; i++) {
               XTypeNode node = (XTypeNode) metadata.getChild(i);
               flds[i] = new XField(node.getName());
               flds[i].setType(node.getType());
            }

            sql.setColumnInfo(flds);
            sql.getSelection().clear();
         }
         else {
            sql.setColumnInfo(null);
         }
      }

      // @by larryl, if text is not changed, don't parse sql
      // @by stevenkuo bug1418866999436 2015-1-8
      // added condition to check for initial parsing
      if(sql.hasSQLString() && Tool.equals(sql.getSQLString(), nsqlString) &&
         sql.getParseResult() != -1 && !executeQuery &&
         sql.getParseResult() != UniformSQL.PARSE_FAILED)
      {
         return null;
      }

      // if sql is cleared, clear out the UniformSQL
      if(StringUtils.isEmpty(nsqlString) && !executeQuery) {
         sql.setSQLString(null);
         sql.setParseResult(UniformSQL.PARSE_FAILED);
         return null;
      }

      JDBCDataSource xds = (JDBCDataSource) query.getDataSource();

      try {
         XRepository repository = XFactory.getRepository();
         XNode node = new XNode();
         node.setAttribute("type", "DBPROPERTIES");
         node = repository.getMetaData(
            principal.getName(), xds, node, true, null);
         sql.setMetaDataNode(node);
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      UniformSQL osql = sql.clone();
      sql.setDataSource(xds);

      synchronized(sql) {
         sql.setSQLString(nsqlString);

         // it is waiting for the parser finish parsing the sql string.
         try {
            sql.wait();
         }
         catch(InterruptedException e) {
         }
      }

      // @by larryl, don't re-generate sql otherwise user format would be lost.
      // The generated sql may also be different from the original syntax.
      // The sql should only be re-generated if the query is modified in the
      // structured view.
      // @by ankurp, must execute this if sql has changed to update tables,
      // links, etc. gui.
      // @by larryl, don't parse if user explicitly disabled it
      /* @by larryl, I don't see how the following is necessary. The
         setSQLString() already called with user entered sql string. By
         setting it again with a sql string generated from the parse result
         would in the best case reset the UniformSQL to the same structure.
         In the worst case (if the parsing is incorrect), it would wipe out
         parts of sql that user has entered. Please don't put it back unless
         provided a valid explanation and a solution that avoids the stated
         problems.
      if(change && parseCB.isSelected() &&
         ((UniformSQL) sql).getParseResult() == UniformSQL.PARSE_SUCCESS)
      {
         SQLHelper helper = SQLHelper.getSQLHelper((UniformSQL) sql);
         ((UniformSQL) sql).setSQLString(helper.generateSentence());
      }
      */

      try {
         XRepository repository = XFactory.getRepository();

         // run query to get column info
         if(sql.getParseResult() == UniformSQL.PARSE_FAILED || executeQuery) {
            if(!executeQuery) {
               return Catalog.getCatalog().getString("designer.qb.jdbc.unableParseSql");
            }

            XNode result = execute(query, runtimeQuery.getVariables(), principal.getName());

            if(result instanceof JDBCTableNode) {
               JDBCTableNode jresult = (JDBCTableNode) result;

               // clear old paths which added by failed parsing process before adding new paths(60343).
               if(jresult.getColCount() > 0) {
                  sql.getSelection().clear();
               }

               for(int i = 0; i < jresult.getColCount(); i++) {
                  String column = jresult.getName(i);
                  sql.getSelection().addColumn(column);
               }

               jresult.close();
            }
         }

         JDBCUtil.fixUniformSQLInfo(sql, repository, principal.getName(), xds);
         JDBCUtil.fixUniformSQLSelection(osql, sql);
         JDBCUtil.fixTableLocation(sql);
         initQuerySelectedTables(runtimeQuery);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return null;
   }

   /**
    * Execute a query.
    */
   private XNode execute(XQuery xquery, VariableTable variableTable, String user) throws Exception {
      XDataService service = XFactory.getDataService();
      XNode result = null;
      int maxRows = 0;

      try {
         maxRows = xquery.getMaxRows();
         xquery.setMaxRows(1);
         result = service.execute(user, xquery, variableTable,
            ThreadContext.getContextPrincipal(), false, null);
      }
      finally {
         xquery.setMaxRows(maxRows);
      }

      return result;
   }

   public String[][] loadQueryData(String runtimeId, String sqlString,
                                               Principal principal)
      throws Exception
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);
      JDBCQuery query = runtimeQuery.getQuery();

      if(sqlString != null) {
         parseSqlString(runtimeId, sqlString, false, true, principal);
         query = runtimeQuery.getQuery();
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();

      if(sql.isParseSQL() && !sql.isLossy()) {
         query = query.clone();
         sql = (UniformSQL) query.getSQLDefinition();
         sql.clearSQLString();
      }

      VariableTable vtable = runtimeQuery.getVariables();
      vtable = vtable.clone();

      int maxRow = Integer.MAX_VALUE;
      int queryPreviewMaxRow = Util.getQueryPreviewMaxrow();

      if(queryPreviewMaxRow > 0) {
         maxRow = Math.min(maxRow, queryPreviewMaxRow);
      }

      if(runtimeQuery.getMaxPreviewRow() > 0) {
         maxRow = Math.min(maxRow, runtimeQuery.getMaxPreviewRow());
      }

      if(maxRow > 0 && maxRow < Integer.MAX_VALUE) {
         vtable.put(XQuery.HINT_MAX_ROWS, "" + maxRow);
      }

      XDataService service = XFactory.getDataService();
      XNode result = service.execute(principal.getName(), query, vtable,
         principal, true, null);

      if(result == null) {
         return null;
      }

      XNodeTableLens lens = new XNodeTableLens(result);
      int loadRows = Integer.MAX_VALUE;

      if(Util.getOrganizationMaxRow() > 0) {
         loadRows = Math.min(loadRows, Util.getOrganizationMaxRow() + 1);
      }

      lens.moreRows(loadRows);
      int start = 0;
      int end = lens.getRowCount();
      int colCount = lens.getColCount();

      String[][] values = new String[end][colCount];

      for(int row = start; row < end; row++) {
         for(int col = 0; col < colCount; col++) {
            Object value = lens.getObject(row, col);
            values[row][col] = value == null ? "" : Tool.getDataString(value);
         }
      }

      return values;
   }

   public ColumnSelection getColumnSelection(JDBCQuery query, VariableTable vars,
                                             SQLBoundTableAssembly assembly, Object session,
                                             Map<String, String> aliasMapping)
      throws Exception
   {
      ColumnSelection oldColumns = assembly.getColumnSelection();
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      ColumnSelection columns = new ColumnSelection();

      if(sql.getParseResult() == UniformSQL.PARSE_SUCCESS ||
         sql.getParseResult() == UniformSQL.PARSE_PARTIALLY)
      {
         JDBCSelection selection = (JDBCSelection) sql.getSelection();

         for(int i = 0; i < selection.getColumnCount(); i++) {
            String fullname = selection.getColumn(i);
            String alias = selection.getAlias(i);
            String name = alias != null && !alias.isEmpty() ? alias
               : fullname.substring(fullname.lastIndexOf('.') + 1);
            AttributeRef attributeRef = new AttributeRef(name);
            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(selection.getType(fullname));
            String oldAlias = getOriginalAlias(aliasMapping, alias);
            DataRef oldRef = getOldAttributeRef(oldAlias, fullname, oldColumns, selection);

            if(oldRef instanceof ColumnRef) {
               ColumnRef oldCol = (ColumnRef) oldRef;
               ref.setAlias(oldCol.getAlias());
               ref.setDescription(oldCol.getDescription());
               ref.setOldName(oldCol.getDisplayName());
            }

            columns.addAttribute(ref);
         }
      }
      else {
         XTypeNode cinfo = new XTypeNode("table");
         cinfo.setMinOccurs(0);
         cinfo.setMaxOccurs(XTypeNode.STAR);
         JDBCQuery clone = query.clone();
         cinfo = clone.getOutputTypeForNonParseableSQL(cinfo, vars, session);

         for(int i = 0; i < cinfo.getChildCount(); i++) {
            XTypeNode node = (XTypeNode) cinfo.getChild(i);
            AttributeRef attributeRef = new AttributeRef(node.getName());
            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(node.getType());
            DataRef oldRef = oldColumns.findAttribute(ref);

            if(oldRef instanceof ColumnRef) {
               ref.setAlias(((ColumnRef) oldRef).getAlias());
               ref.setDescription(((ColumnRef) oldRef).getDescription());
            }

            columns.addAttribute(ref);
         }
      }

      // sync formula field, data range ref.
      syncColumnRef(columns, oldColumns, aliasMapping);
      syncAggregateInfo(columns, assembly.getAggregateInfo(), aliasMapping);

      return columns;
   }

   private void syncAggregateInfo(ColumnSelection columns, AggregateInfo aggregateInfo, Map<String, String> aliasMapping) {
      if(aggregateInfo == null || aliasMapping == null || aliasMapping.isEmpty()) {
         return;
      }

      syncGroupRefs(columns, aggregateInfo, aliasMapping);
      syncAggregateRefs(columns, aggregateInfo, aliasMapping);
   }

   private void syncGroupRefs(ColumnSelection columns, AggregateInfo aggregateInfo, Map<String, String> aliasMapping) {
      GroupRef[] groups = aggregateInfo.getGroups();
      List<GroupRef> glist = new ArrayList<>();

      for(GroupRef group : groups) {
         ColumnRef columnRef = (ColumnRef) group.getDataRef();
         DataRef dataRef = columnRef.getDataRef();

         if(dataRef instanceof AttributeRef) {
            String newAlias = aliasMapping.get(dataRef.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               glist.add(group);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               columnRef.setDataRef(new AttributeRef(newAlias));
               glist.add(group);
            }
         }
         else if(dataRef instanceof DateRangeRef) {
            DateRangeRef rangeRef = (DateRangeRef) dataRef;
            DataRef ref = rangeRef.getDataRef();
            String newAlias = aliasMapping.get(ref.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               glist.add(group);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               rangeRef.setDataRef(new AttributeRef(newAlias));
               glist.add(group);
            }
         }
         else if(dataRef instanceof NumericRangeRef) {
            NumericRangeRef rangeRef = (NumericRangeRef) dataRef;
            DataRef attr = rangeRef.getDataRef();
            String newAlias = aliasMapping.get(attr.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               glist.add(group);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               NumericRangeRef newRef = new NumericRangeRef(rangeRef.getAttribute(), new AttributeRef(newAlias));
               newRef.setValueRangeInfo(rangeRef.getValueRangeInfo());
               group.setDataRef(newRef);
               glist.add(group);
            }
         }
         else if(dataRef instanceof ExpressionRef exprRef) {
            String newExpr = renameExpression(exprRef.getExpression(), aliasMapping);
            exprRef.setExpression(newExpr);
            columnRef.setDataRef(exprRef);
            glist.add(group);
         }
      }

      aggregateInfo.setGroups(glist.toArray(GroupRef[]::new));
   }

   private void syncAggregateRefs(ColumnSelection columns, AggregateInfo aggregateInfo,
                                  Map<String, String> aliasMapping)
   {
      AggregateRef[] aggregates = aggregateInfo.getAggregates();
      List<AggregateRef> aggList = new ArrayList<>();

      for(AggregateRef agg : aggregates) {
         ColumnRef columnRef = (ColumnRef) agg.getDataRef();
         DataRef dataRef = columnRef.getDataRef();

         if(dataRef instanceof AttributeRef) {
            String newAlias = aliasMapping.get(dataRef.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               aggList.add(agg);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               columnRef.setDataRef(new AttributeRef(newAlias));
               aggList.add(agg);
            }
         }
         else if(dataRef instanceof DateRangeRef) {
            DateRangeRef rangeRef = (DateRangeRef) dataRef;
            DataRef ref = rangeRef.getDataRef();
            String newAlias = aliasMapping.get(ref.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               aggList.add(agg);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               rangeRef.setDataRef(new AttributeRef(newAlias));
               aggList.add(agg);
            }
         }
         else if(dataRef instanceof NumericRangeRef) {
            NumericRangeRef rangeRef = (NumericRangeRef) dataRef;
            DataRef attr = rangeRef.getDataRef();
            String newAlias = aliasMapping.get(attr.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            if(Tool.equals(newAlias, dataRef.getAttribute())) {
               aggList.add(agg);
            }
            else if(!Tool.equals(newAlias, dataRef.getAttribute()) && newAlias != null) {
               NumericRangeRef newRef = new NumericRangeRef(rangeRef.getAttribute(), new AttributeRef(newAlias));
               newRef.setValueRangeInfo(rangeRef.getValueRangeInfo());
               agg.setDataRef(newRef);
               aggList.add(agg);
            }
         }
         else if(dataRef instanceof ExpressionRef exprRef) {
            String newExpr = renameExpression(exprRef.getExpression(), aliasMapping);
            exprRef.setExpression(newExpr);
            columnRef.setDataRef(exprRef);
            aggList.add(agg);
         }
      }

      aggregateInfo.setAggregates(aggList.toArray(AggregateRef[]::new));
   }


   /**
    * sync formula field, data range ref.
    */
   private void syncColumnRef(ColumnSelection columns, ColumnSelection oldColumns, Map<String, String> aliasMapping) {
      if(aliasMapping == null || aliasMapping.isEmpty()) {
         return;
      }

      for(int i = 0; i < oldColumns.getAttributeCount(); i++) {
         ColumnRef columnRef = (ColumnRef) oldColumns.getAttribute(i);
         DataRef dataRef = columnRef.getDataRef();

         if(dataRef instanceof AttributeRef) {
            continue;
         }

         if(dataRef instanceof DateRangeRef) {
            DateRangeRef dateRangeRef = (DateRangeRef) dataRef;
            DataRef ref = dateRangeRef.getDataRef();
            String newAlias = aliasMapping.get(ref.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            AttributeRef attributeRef = new AttributeRef(newAlias);
            dateRangeRef.setDataRef(attributeRef);
            columnRef.setDataRef(dateRangeRef);
         }
         else if(dataRef instanceof NumericRangeRef) {
            NumericRangeRef rangeRef = (NumericRangeRef) dataRef;
            DataRef attr = rangeRef.getDataRef();
            String newAlias = aliasMapping.get(attr.getAttribute());

            if(findColumnIndexWithAlias(newAlias, columns) == -1) {
               continue;
            }

            NumericRangeRef newRef = new NumericRangeRef(rangeRef.getAttribute(), new AttributeRef(newAlias));
            newRef.setValueRangeInfo(rangeRef.getValueRangeInfo());
            columnRef.setDataRef(newRef);
         }
         else if(dataRef instanceof ExpressionRef exprRef) {
            String newExpr = renameExpression(exprRef.getExpression(), aliasMapping);
            exprRef.setExpression(newExpr);
            columnRef.setDataRef(exprRef);
         }

         columns.addAttribute(columnRef);
      }
   }

   private int findColumnIndexWithAlias(String alias, ColumnSelection cols) {
      int count = cols.getAttributeCount();

      for(int i = 0; i < count; i++) {
         ColumnRef ref = (ColumnRef) cols.getAttribute(i);
         DataRef dataRef = ref.getDataRef();

         if(dataRef instanceof AttributeRef && Tool.equals(alias, dataRef.getAttribute())) {
            return i;
         }
      }

      return -1;
   }

   private String getOriginalAlias(Map<String, String> aliasMapping, String lastAlias) {
      if(Tool.isEmptyString(lastAlias) || aliasMapping == null || aliasMapping.isEmpty()) {
         return null;
      }

      String originalAlias = null;

      for(String key : aliasMapping.keySet()) {
         if(Tool.equals(aliasMapping.get(key), lastAlias)) {
            return key;
         }
      }

      return originalAlias;
   }

   private String renameExpression(String expression, Map<String, String> aliasMapping) {
      ScriptIterator iterator = new ScriptIterator(expression);
      final StringBuilder sb = new StringBuilder();

      ScriptIterator.ScriptListener listener = new ScriptIterator.ScriptListener() {
         @Override
         public void nextElement(ScriptIterator.Token token, ScriptIterator.Token pref,
                                 ScriptIterator.Token cref)
         {
            boolean nonField = !"field".equals(token.val);
            String nval = null;

            if(pref != null && "field".equals(pref.val)) {
               nval = aliasMapping.get(token.val);
            }

            if(token.isRef() && !sb.toString().endsWith(".") && nonField &&
               nval != null && !nval.equals(token.val))
            {
               if(Tool.isValidIdentifier(nval)) {
                  sb.append(new ScriptIterator.Token(token.type, nval, token.length));
               }
               else {
                  if(sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
                     sb.deleteCharAt(sb.length() - 1);
                  }

                  sb.append(new ScriptIterator.Token(token.type, nval, token.length));
               }
            }
            else {
               sb.append(token);
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();

      return sb.toString();
   }

   /**
    * Check if group by is valid considering sql selection.
    *
    * @param groups the specified group by columns
    * @return true if is, false otherwise
    */
   public boolean isValidGroupBy(String runtimeId, String[] groups) {
      JDBCQuery query = getQuery(runtimeId);

      if(query == null || groups == null) {
         return true;
      }

      // @by billh, if columns(except expression) contained in sql selection
      // but not contained in group by, we take the case to be invalid.
      // But it can only be used as suggestion not command, for so many db
      // vendors violate ansi sql
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection sel = (JDBCSelection) sql.getSelection();

      for(int i = 0; i < sel.getColumnCount(); i++) {
         String name = sel.getColumn(i);
         String alias = sel.getAlias(i);

         if(XUtil.isQualifiedName(name)) {
            boolean contains = false;

            for(int j = 0; j < groups.length; j++) {
               String groupFieldName = groups[j];

               if(Tool.equals(groupFieldName, alias) || Tool.equals(groupFieldName, name)) {
                  contains = true;
                  break;
               }
            }

            if(!contains) {
               return false;
            }
         }
      }

      return true;
   }

   private void initQuerySelectedTables(RuntimeQueryService.RuntimeXQuery runtimeQuery)
      throws Exception
   {
      runtimeQuery.setSelectedTables(new HashMap<>());
      JDBCQuery query = runtimeQuery.getQuery();
      JDBCDataSource jdbcDataSource = (JDBCDataSource) query.getDataSource();

      if(jdbcDataSource == null) {
         return;
      }

      dataSourceService.removeDefaultMetaDataProviderCache(jdbcDataSource);
      String dataSourceName = jdbcDataSource.getFullName();
      DefaultMetaDataProvider metaData = getMetaDataProvider(dataSourceName);

      if(metaData == null) {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      SelectTable[] selectTables = sql.getSelectTable();

      for(SelectTable selectTable : selectTables) {
         Object name = selectTable.getName();
         String tableName = null;
         String tablePath = null;
         String pathArray = null;

         if(name instanceof UniformSQL) {
            tableName = tablePath = selectTable.getAlias();
         }
         else {
            tableName = String.valueOf(selectTable.getName());
            XNode node = metaData.getTable(tableName, XUtil.OUTER_MOSE_LAYER_DATABASE);

            if(node != null) {
               tablePath = JDBCUtil.getTablePath(jdbcDataSource, node);
               pathArray = getEntryPath(node);
            }
            else {
               String realTableName = JDBCUtil.getRealTableName(selectTable);
               tablePath = JDBCUtil.getTablePath(jdbcDataSource.getFullName(), "TABLE",
                  selectTable.getCatalog(), selectTable.getSchema(), realTableName);
               pathArray = getTableEntryPath(jdbcDataSource.getFullName(), "TABLE",
                  selectTable.getCatalog(), selectTable.getSchema(), realTableName);
            }
         }

         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE, tablePath, null);
         entry.setProperty("source", tableName);
         entry.setProperty("prefix", dataSourceName);
         entry.setProperty(AssetEntry.PATH_ARRAY, pathArray);
         entry.setProperty(XSourceInfo.CATALOG, selectTable.getCatalog());
         entry.setProperty(XSourceInfo.SCHEMA, selectTable.getSchema());
         entry.setProperty("source_with_no_quote", tableName);
         String alias = selectTable.getAlias();
         alias = alias == null ? tableName : alias;
         runtimeQuery.addSelectedTable(alias, entry);
      }
   }

   private String getTableEntryPath(String datasource, Object type, Object catalog, Object schema,
                                    String tableName)
   {
      StringBuilder path =
         new StringBuilder().append(datasource).append(AssetEntry.PATH_ARRAY_SEPARATOR);
      path.append(type).append(AssetEntry.PATH_ARRAY_SEPARATOR);

      if(catalog != null) {
         path.append(catalog).append(AssetEntry.PATH_ARRAY_SEPARATOR);
      }

      if(schema != null) {
         path.append(schema).append(AssetEntry.PATH_ARRAY_SEPARATOR);
      }

      path.append(tableName);
      return path.toString();
   }

   private String getEntryPath(XNode node) {
      List<String> list = new ArrayList<>();

      if(node == null) {
         return null;
      }

      list.add(node.getName());
      XNode parent = node.getParent();

      while(parent != null) {
         list.add(parent.getName());
         parent = parent.getParent();
      }

      StringBuilder path = new StringBuilder();

      for(int i = list.size() - 1; i >= 0 ; i--) {
         if(i != list.size() - 1) {
            path.append(AssetEntry.PATH_ARRAY_SEPARATOR);
         }

         path.append(list.get(i));
      }

      return path.toString();
   }

   private DefaultMetaDataProvider getMetaDataProvider(String database) {
      try {
         JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
         XDataModel dataModel = dataSourceService.getDataModel(database);
         return dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
      }
      catch(Exception e) {
         return null;
      }
   }

   private ItemList getSelectedColumns(UniformSQL sql) {
      ItemList itemList = new ItemList();

      if(sql != null) {
         JDBCSelection selection = (JDBCSelection) sql.getSelection();

         for(int i = 0; i < selection.getColumnCount(); i++) {
            itemList.addItem(selection.getColumn(i));
         }
      }

      return itemList;
   }

   private UniformSQL createUniformSQL(UniformSQL sql, String tableName, String colPath) {
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn(colPath);
      selection.setTable(colPath, tableName);

      UniformSQL bsql = new UniformSQL();
      bsql.setSelection(selection);

      if(sql == null || sql.getSelectTable(tableName) == null) {
         bsql.addTable(tableName);
      }
      else {
         bsql.addTable(sql.getSelectTable(tableName));
      }

      return bsql;
   }

   private RuntimeQueryService.RuntimeXQuery createNewRuntimeQuery(String runtimeId, String tableName,
                                                                   String dataSource)
      throws Exception
   {
      JDBCQuery newQuery = createNewQuery(tableName, dataSource);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.createRuntimeQuery(null, newQuery, dataSource, null);

      if(runtimeId != null) {
         String newId = runtimeQuery.getId();
         destroyRuntimeQuery(newId);
         runtimeQuery.setId(runtimeId);
         runtimeQueryService.saveRuntimeQuery(runtimeQuery);
      }

      return runtimeQuery;
   }

   private DataRef getOldAttributeRef(String oldAlias, String fullname, ColumnSelection oldColumns,
                                      JDBCSelection selection)
   {
      String name = !Tool.isEmptyString(oldAlias) ? oldAlias
         : fullname.substring(fullname.lastIndexOf('.') + 1);
      AttributeRef attributeRef = new AttributeRef(name);
      ColumnRef ref = new ColumnRef(attributeRef);
      ref.setDataType(selection.getType(fullname));

      return oldColumns.findAttribute(ref);
   }

   public TreeNodeModel getDataSourceTreeNode(String dataSource, boolean columnLevel,
                                              AssetEntry expandedEntry,
                                              Principal principal)
      throws Exception
   {
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      List<TreeNodeModel> children = null;

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
            if(dataSourceEntry.getPath().equals(dataSource)) {
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
      }
      else {
         children = getDataSourceChildren(dataSource, expandedEntry,
            assetRepository, columnLevel, principal);
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   public TreeNodeModel getDataSourceFieldsTreeNode(String runtimeId, boolean sort,
                                                    Principal principal)
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryService.getRuntimeQuery(runtimeId);
      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);

      if(runtimeQuery != null) {
         Map<String, AssetEntry> selectedTables = runtimeQuery.getSelectedTables();

         if(selectedTables == null || selectedTables.isEmpty()) {
            return null;
         }

         JDBCQuery query = runtimeQuery.getQuery();
         SQLHelper helper = SQLHelper.getSQLHelper(query.getDataSource());
         helper.setUniformSql((UniformSQL) query.getSQLDefinition());
         List<TreeNodeModel> tables = new ArrayList<>();

         selectedTables.forEach((alias, entry) -> {
            String source = entry.getProperty("source_with_no_quote");
            String quoteTableName = null;

            if(!Tool.equals(alias, source)) {
               entry = (AssetEntry) entry.clone();
               entry.setAlias(alias);
               quoteTableName = XUtil.quoteAlias(alias, helper);
            }
            else {
               quoteTableName = helper.quoteTableName(source);
            }

            entry.setProperty("quoteTableName", quoteTableName);

            List<TreeNodeModel> columns = new ArrayList<>();

            try {
               columns = getDataSourceChildren(runtimeQuery.getDataSource(),
                  entry, assetRepository, true, principal);
            }
            catch(Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }

            tables.add(TreeNodeModel.builder()
               .label(alias)
               .data(entry)
               .expanded(!sort)
               .children(columns)
               .dragName(entry.getType().toString())
               .build());
         });

         return TreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Database fields"))
            .expanded(sort)
            .children(tables)
            .build();
      }

      return null;
   }

   private List<TreeNodeModel> getDataSourceChildren(String datasource,
                                                     AssetEntry expandedEntry,
                                                     AssetRepository assetRepository,
                                                     boolean columnLevel,
                                                     Principal principal)
      throws Exception
   {
      List<TreeNodeModel> children = new ArrayList<>();
      AssetEntry.Selector selector =
         new AssetEntry.Selector(AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);
      AssetEntry[] entries = assetRepository.getEntries(
         expandedEntry, principal, ResourceAction.READ, selector);
      String alias = expandedEntry.getAlias();
      SQLHelper helper = SQLHelper.getSQLHelper(dataSourceService.getDataSource(datasource));

      for(AssetEntry entry : entries) {
         if(!StringUtils.isEmpty(alias)) {
            entry.setProperty(QueryManagerService.SOURCE_ALIAS, alias);
         }

         entry.setProperty("quoteTableName", expandedEntry.getProperty("quoteTableName"));
         entry.setProperty("quoteColumnName", XUtil.quoteNameSegment(entry.getName(), helper));
         TreeNodeModel.Builder child = TreeNodeModel.builder()
            .label(entry.getName())
            .data(entry)
            .leaf(!columnLevel && entry.getType() == AssetEntry.Type.PHYSICAL_TABLE ||
               columnLevel && entry.isColumn());

         if(!entry.getType().isActualFolder()) {
            child.dragName(entry.getType().toString());
         }

         children.add(child.build());
      }

      return children;
   }

   public TreeNodeModel getSortPaneFieldsTree(String runtimeId, Principal principal) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryService.getRuntimeQuery(runtimeId);

      if(runtimeQuery != null) {
         JDBCSelection selection = (JDBCSelection) runtimeQuery.getQuery().getSelection();
         List<TreeNodeModel> queryFields = new ArrayList<>();

         for(int i = 0; i < selection.getColumnCount(); i++) {
            AssetEntry entry = new AssetEntry(
               AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN, "", null);
            String name = selection.getColumn(i);
            String alias = selection.getAlias(i);
            alias = !StringUtils.isEmpty(alias) ? alias : name;
            entry.setProperty("attribute", alias);
            entry.setProperty("dtype", selection.getType(name));
            entry.setProperty("isAliasColumn", "true");
            queryFields.add(TreeNodeModel.builder()
               .label(alias)
               .tooltip(name)
               .data(entry)
               .leaf(true)
               .dragName(entry.getType().toString())
               .build());
         }

         TreeNodeModel queryTreeNode = TreeNodeModel.builder()
            .label(Catalog.getCatalog().getString("Query fields"))
            .children(queryFields)
            .expanded(true)
            .build();
         TreeNodeModel dsTreeNode = getDataSourceFieldsTreeNode(runtimeId, true, principal);
         List<TreeNodeModel> children = new ArrayList<>();
         children.add(queryTreeNode);
         children.add(dsTreeNode);

         return TreeNodeModel.builder()
            .children(children)
            .build();
      }

      return null;
   }

   public enum DatabaseQueryTabs {
      LINKS("links"),
      FIELDS("fields"),
      CONDITIONS("conditions"),
      SORT("sort"),
      GROUPING("grouping"),
      SQL_STRING("sql-string"),
      PREVIEW("preview");

      private final String tab;

      DatabaseQueryTabs(String tab) {
         this.tab = tab;
      }

      public String getTab() {
         return tab;
      }
   }

   public static final String SOURCE_ALIAS = "source_alias";
   public static final int CONDITION_WHERE = 0; // 'where' clause condition type.
   public static final int CONDITION_HAVING = 1; // 'having' clause condition type.
   private int nameInt = 0;
   private final RuntimeQueryService runtimeQueryService;
   private final XRepository repository;
   private final SecurityEngine securityEngine;
   private final DataSourceService dataSourceService;
   private static final Logger LOG = LoggerFactory.getLogger(QueryManagerService.class);
}
