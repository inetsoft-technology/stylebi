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

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.portal.model.database.graph.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static inetsoft.web.portal.model.database.graph.PhysicalGraphLayout.GRAPH_DEFAULT_COLUMN_WIDTH;
import static inetsoft.web.portal.model.database.graph.PhysicalGraphLayout.PORTAL_GRAPH_NODE_HEIGHT;

@Component
public class QueryGraphModelService {
   @Autowired
   public QueryGraphModelService(RuntimeQueryService runtimeQueryService,
                                 DataSourceService dataSourceService,
                                 QueryManagerService queryService)
   {
      this.runtimeQueryService = runtimeQueryService;
      this.dataSourceService = dataSourceService;
      this.queryService = queryService;
   }

   public JoinGraphModel getQueryGraphModel(String runtimeID, TableJoinInfo joinInfo,
                                            Principal principal)
      throws Exception
   {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeID);
      JDBCQuery query = runtimeQuery == null ? null : runtimeQuery.getQuery();

      if(query != null) {
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();
         String database = query.getDataSource().getFullName();

         if(joinInfo == null) {
            return buildGraphViewModel(runtimeQuery, sql, database, principal);
         }

         return buildJoinEditPaneModel(
            runtimeQuery, sql, joinInfo, runtimeID, database, query.getName(), principal);
      }

      return null;
   }

   public void addTables(String id, List<AssetEntry> tables, Point position, Principal principal) {
      if(tables == null || tables.isEmpty()) {
         return;
      }

      JDBCQuery query = getQuery(id);

      if(query != null) {
         SQLDefinition sql = query.getSQLDefinition();

         if(sql == null) {
            sql = new UniformSQL();
            query.setSQLDefinition(sql);
         }

         for(int i = 0; i < tables.size(); i++) {
            String tableAlias =
               queryService.addTable((UniformSQL) sql, tables.get(i), getPosition(position, i));
            runtimeQueryService.getRuntimeQuery(id).addSelectedTable(tableAlias, tables.get(i));
         }

         queryService.fixUniformSQLInfo((UniformSQL) sql, (JDBCDataSource) query.getDataSource(),
            principal);
      }
   }

   public void removeTables(String runtimeId, List<RemoveGraphTableEvent.RemoveTableInfo> tables,
                            Principal principal)
   {
      if(tables == null || tables.isEmpty()) {
         return;
      }

      JDBCQuery query = getQuery(runtimeId);

      if(query != null) {
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();

         for(RemoveGraphTableEvent.RemoveTableInfo table : tables) {
            String tableName = table.getFullName();
            sql.removeTable(tableName);
            runtimeQueryService.getRuntimeQuery(runtimeId).removeSelectedTable(tableName);
         }

         // if no more table, clear the query
         if(sql.getTableCount() == 0) {
            sql.setWhere(null);
            sql.setHaving(null);
            sql.removeAllOrderByFields();
            sql.setGroupBy(null);
            sql.removeAllFields();
            sql.getSelection().clear();
            return;
         }

         Hashtable<String, String> aliasMap = new Hashtable<>();

         for(SelectTable selectTable : sql.getSelectTable()) {
            aliasMap.put(selectTable.getAlias(), selectTable.getAlias());
         }

         sql.syncTableAlias(aliasMap);
         queryService.fixUniformSQLInfo(sql, (JDBCDataSource) query.getDataSource(), principal);
         sql.syncTable();
      }
   }

   public void moveTable(String runtimeId, String tableName, Rectangle rectangle) {
      JDBCQuery query = getQuery(runtimeId);

      if(query != null) {
         SQLDefinition sql = query.getSQLDefinition();
         SelectTable table = null;

         if(sql instanceof UniformSQL) {
            table = getSelectTable((UniformSQL) sql, tableName);
         }

         if(table != null) {
            int x = (int) Math.round(rectangle.getX());
            int y = (int) Math.round(rectangle.getY());
            table.setLocation(new Point(x, y));
         }
      }
   }

   public void clearTable(String runtimeId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);
      runtimeQuery.setSelectedTables(null);
      JDBCQuery query = runtimeQuery.getQuery();

      if(query != null) {
         JDBCQuery newQuery = new JDBCQuery();
         newQuery.setName(query.getName());
         newQuery.setFolder(query.getFolder());
         newQuery.setDescription(query.getDescription());
         newQuery.setDataSource(query.getDataSource());
         newQuery.setSQLDefinition(new UniformSQL());
         runtimeQuery.setQuery(newQuery);
      }
   }

   public void editQueryTableProperties(EditQueryTableEvent event, Principal principal) {
      if(event.getTables() == null || event.getTables().isEmpty()) {
         return;
      }

      String runtimeId = event.getId();
      QueryTableModel table = event.getTables().get(0);
      String oldName = event.getOldName();
      JDBCQuery query = getQuery(runtimeId);

      if(query == null || !(query.getSQLDefinition() instanceof UniformSQL)) {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      Hashtable<String, String> aliasMap = new Hashtable<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable selectTable = sql.getSelectTable(i);
         aliasMap.put(selectTable.getAlias(), selectTable.getAlias());

         if(table.getName().equals(selectTable.getName()) &&
            oldName.equals(selectTable.getAlias()))
         {
            aliasMap.put(selectTable.getAlias(), table.getAlias());
            selectTable.setAlias(table.getAlias());
            UniformSQL newSql = processRenameJoins(sql, oldName, table.getAlias());
            query.setSQLDefinition(newSql);
            runtimeQueryService.getRuntimeQuery(runtimeId).renameSelectedTable(table.getAlias(),
               oldName);
         }
      }

      sql = (UniformSQL) query.getSQLDefinition();
      sql.syncTableAlias(aliasMap);
      queryService.fixUniformSQLInfo(sql, (JDBCDataSource) query.getDataSource(), principal);
      sql.syncTable();
   }

   public void createJoin(TableDetailJoinInfo joinInfo) throws Exception {
      if(joinInfo == null) {
         return;
      }

      JDBCQuery query = getQuery(joinInfo.getRuntimeId());

      if(query != null) {
         SQLDefinition sql = query.getSQLDefinition();
         String database = query.getDataSource().getFullName();
         DefaultMetaDataProvider metaData = getMetaDataProvider(database);
         JoinModel joinModel = makeDefaultJoin(joinInfo.getSourceTable(),
            joinInfo.getSourceColumn(), joinInfo.getTargetTable(),
            joinInfo.getTargetColumn(), metaData);

         if(sql instanceof UniformSQL) {
            ((UniformSQL) sql).addJoin(createXJoin(joinModel));
         }
      }
   }

   public void editJoin(EditJoinEvent event) {
      TableDetailJoinInfo detailJoinInfo = event.getDetailJoinInfo();
      JoinModel joinModel = event.getJoinModel();

      if(detailJoinInfo == null || joinModel == null) {
         return;
      }

      JDBCQuery query = getQuery(detailJoinInfo.getRuntimeId());

      if(query != null) {
         SQLDefinition sql = query.getSQLDefinition();

         if(sql instanceof UniformSQL) {
            UniformSQL newSql = processEditJoin((UniformSQL) sql, detailJoinInfo, joinModel);
            query.setSQLDefinition(newSql);
         }
      }
   }

   public void deleteJoins(TableJoinInfo joinInfo) {
      if(joinInfo == null) {
         return;
      }

      JDBCQuery query = getQuery(joinInfo.getRuntimeId());

      if(query != null) {
         SQLDefinition sql = query.getSQLDefinition();

         if(sql instanceof UniformSQL) {
            UniformSQL newSql = processDeleteJoins((UniformSQL) sql, joinInfo);
            query.setSQLDefinition(newSql);
         }
      }
   }

   public void clearJoins(String runtimeId) {
      JDBCQuery query = getQuery(runtimeId);

      if(query != null) {
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();
         sql.removeAllJoins();
      }
   }

   private JoinGraphModel buildJoinEditPaneModel(RuntimeQueryService.RuntimeXQuery runtimeQuery,
                                                 UniformSQL sql, TableJoinInfo joinInfo,
                                                 String runtimeID, String database,
                                                 String queryName, Principal principal)
      throws Exception
   {
      JoinGraphModel graphModel = new JoinGraphModel(true);

      if(sql.getTableCount() == 0) {
         return graphModel;
      }

      List<TableGraphModel> tables = new ArrayList<>();
      JoinEditPaneModel joinEditPaneModel =
         new JoinEditPaneModel(runtimeID, database, queryName, tables);
      graphModel.setJoinEditPaneModel(joinEditPaneModel);

      String sourceTableName = joinInfo.getSourceTable();
      String targetTableName = joinInfo.getTargetTable();
      SelectTable sourceTable = getSelectTable(sql, sourceTableName);
      SelectTable targetTable = getSelectTable(sql, targetTableName);
      TableGraphModel leftTable =
         buildEditJoinPaneTableGraphModel(sql, sourceTable,
            getSelectTableEntry(runtimeQuery, sourceTable), targetTableName, database, principal);
      TableGraphModel rightTable =
         buildEditJoinPaneTableGraphModel(sql, targetTable,
            getSelectTableEntry(runtimeQuery, targetTable), sourceTableName, database, principal);

      if(joinInfo.isAutoCreateColumnJoin()) {
         autoCreateJoinByColumn(sql, leftTable, rightTable, getMetaDataProvider(database));
      }

      tables.add(leftTable);
      tables.add(rightTable);

      // join edit pane should adjust position.
      fixTablePosition(tables);

      return graphModel;
   }

   private AssetEntry getSelectTableEntry(RuntimeQueryService.RuntimeXQuery runtimeQuery,
                                          SelectTable selectTable)
   {
      if(runtimeQuery == null || selectTable == null) {
         return null;
      }

      String alias = selectTable.getAlias();
      alias = alias == null ? Tool.toString(selectTable.getName()) : alias;
      return runtimeQuery.getSelectedTables().get(alias);
   }

   private TableGraphModel buildEditJoinPaneTableGraphModel(UniformSQL sql, SelectTable table,
                                                            AssetEntry tableEntry,
                                                            String joinTable, String database,
                                                            Principal principal)
      throws Exception
   {
      TableGraphModel tableGraph = new TableGraphModel();
      tableGraph.setName(Tool.toString(table.getAlias()));
      tableGraph.setBounds(getTableBounds(table));
      tableGraph.setColumns(buildColumns(tableEntry, table, database, principal));

      if(StringUtils.hasText(joinTable)) {
         List<JoinModel> joins = getTableJoinModels(sql, table, database);
         joins = joins.stream()
            .filter(j -> joinTable.equals(j.getForeignTable()))
            .collect(Collectors.toList());
         tableGraph.setJoins(joins);
      }

      return tableGraph;
   }

   private JoinGraphModel buildGraphViewModel(RuntimeQueryService.RuntimeXQuery runtimeQuery,
                                              UniformSQL sql, String database, Principal principal)
      throws Exception
   {
      List<GraphModel> graphModels = new ArrayList<>();
      GraphViewModel graph = new GraphViewModel(graphModels);
      JoinGraphModel model = new JoinGraphModel(false, graph);

      if(sql.getTableCount() == 0) {
         return model;
      }
      else {
         JDBCUtil.fixTableLocation(sql);
      }

      for(int i = 0; i < sql.getTableCount(); i++) {
         GraphModel graphModel = new GraphModel();
         SelectTable selectTable = sql.getSelectTable(i);
         AssetEntry entry = getSelectTableEntry(runtimeQuery, selectTable);
         graphModel.setNode(buildGraphNodeModel(entry, selectTable, database));
         graphModel.setEdge(buildGraphNodeEdge(sql, selectTable, database));
         graphModel.setCols(buildColumns(entry, selectTable, database, principal));
         graphModel.setBounds(getTableBounds(selectTable));

         graphModels.add(graphModel);
      }

      return model;
   }

   private GraphNodeModel buildGraphNodeModel(AssetEntry entry, SelectTable selectTable,
                                              String database)
      throws Exception
   {
      GraphNodeModel graphNode = new GraphNodeModel();
      String name, fullName, alias;

      if(selectTable.getName() instanceof UniformSQL) {
         name = fullName = alias = selectTable.getAlias();
      }
      else {
         fullName = Tool.toString(selectTable.getName());
         name = fullName.lastIndexOf(".") > -1 ? entry.getProperty("entity") : fullName;
         alias = selectTable.getAlias() != null ? selectTable.getAlias() : fullName;
      }

      graphNode.setId(alias);
      graphNode.setName(alias);
      graphNode.setTableName(name);
      graphNode.setLabel(alias);
      graphNode.setTooltip(alias);
      graphNode.setTreeLink(getTablePath(entry, selectTable, database));
      graphNode.setAliasSource(fullName);

      return graphNode;
   }

   private String getTablePath(AssetEntry entry, SelectTable selectTable, String database)
      throws Exception
   {
      Object name = selectTable.getName();

      if(name instanceof UniformSQL) {
         return selectTable.getAlias();
      }

      String type = null;
      TableNode tableNode = getTableNode(entry, database);

      if(tableNode != null) {
         type = tableNode.getType() == null ?
            entry.getProperty(XSourceInfo.TABLE_TYPE) : tableNode.getType();
      }

      return JDBCUtil.getTablePath(database, type == null ? "TABLE" : type,
         selectTable.getCatalog(), selectTable.getSchema(), JDBCUtil.getRealTableName(selectTable));
   }

   private GraphEdgeModel buildGraphNodeEdge(UniformSQL sql, SelectTable selectTable,
                                             String database)
   {
      GraphEdgeModel graphEdge = new GraphEdgeModel();
      graphEdge.setOutput(buildOutputJoins(sql, selectTable, database));
      graphEdge.setInput(buildInputJoins(sql, selectTable, database));

      return graphEdge;
   }

   private List<NodeConnectionInfo> buildInputJoins(UniformSQL sql, SelectTable table,
                                                    String database)
   {
      List<NodeConnectionInfo> in = new ArrayList<>();
      List<JoinModel> joins = getTableJoinModels(sql, table, database);

      joins = joins.stream()
         .filter(j -> j.getForeignTable().equals(table.getAlias()) ||
            j.getForeignTable().equals(JDBCUtil.getRealTableName(table)))
         .collect(Collectors.toList());

      for(JoinModel join : joins) {
         in.add(new NodeConnectionInfo(join.getTable(), join));
      }

      return in;
   }

   private List<NodeConnectionInfo> buildOutputJoins(UniformSQL sql, SelectTable table,
                                                     String database)
   {
      List<NodeConnectionInfo> out = new ArrayList<>();
      List<JoinModel> joins = getTableJoinModels(sql, table, database);

      joins = joins.stream()
         .filter(j -> j.getTable().equals(table.getAlias()) ||
            j.getTable().equals(JDBCUtil.getRealTableName(table)))
         .collect(Collectors.toList());

      for(JoinModel join : joins) {
         out.add(new NodeConnectionInfo(join.getTable(), join));
      }

      return out;
   }

   private List<JoinModel> getTableJoinModels(UniformSQL sql, SelectTable table, String database) {
      List<JoinModel> models = new ArrayList<>();
      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
      boolean supportFullOuterJoin = JDBCUtil.supportFullOuterJoin(dataSource, true);
      XJoin[] joins = sql.getJoins();

      if(joins != null) {
         String alias = table.getAlias();
         String realTableName = JDBCUtil.getRealTableName(table);

         joins = Arrays.stream(joins)
            .filter(j -> j.getTable1().equals(alias) || j.getTable1().equals(realTableName) ||
               j.getTable2().equals(alias) || j.getTable2().equals(realTableName))
            .toArray(XJoin[]::new);

         for(XJoin join : joins) {
            JoinModel joinModel = new JoinModel();
            joinModel.setTable(join.getTable1());
            joinModel.setColumn(join.getColumn1(sql));
            joinModel.setForeignTable(join.getTable2());
            joinModel.setForeignColumn(join.getColumn2(sql));
            joinModel.setType(JoinType.forType(join.getOp()));
            joinModel.setMergingRule(MergingRule.forType(XSet.AND));
            joinModel.setOrderPriority(join.getOrder());
            joinModel.setSupportFullOuter(supportFullOuterJoin);
            models.add(joinModel);
         }
      }

      return models;
   }

   private List<GraphColumnInfo> buildColumns(AssetEntry entry, SelectTable selectTable,
                                              String database, Principal principal)
      throws Exception
   {
      List<GraphColumnInfo> cols = new ArrayList<>();
      Object name = selectTable.getName();
      String tableName = selectTable.getAlias() != null ?
         selectTable.getAlias() : Tool.toString(selectTable.getName());

      if(name instanceof UniformSQL) {
         UniformSQL sql1 = (UniformSQL) name;
         XSelection xSelects = sql1.getSelection();

         for(int i = 0; i < xSelects.getColumnCount(); i++) {
            String calias = xSelects.getAlias(i);
            String cname = xSelects.getColumn(i);

            if(calias != null && calias.length() > 0 && !calias.equals(cname)) {
               cname = calias;
            }

            cname = XUtil.getSubQueryColumn(cname, sql1, null);
            GraphColumnInfo colInfo = createColInfo(tableName, cname, xSelects.getType(cname));

            if(!cols.contains(colInfo)) {
               cols.add(colInfo);
            }
         }
      }
      else {
         TableNode tableNode = getTableNode(entry, database);
         String source = entry.getProperty("source");
         BiFunction<String, String, Boolean> vpmHiddenCols = VpmProcessor.getInstance()
            .getHiddenColumnsSelector(
               new String[] { source }, new String[0], database, null, null, principal);

         for(int i = 0; i < tableNode.getColumnCount(); i++) {
            String columnName = tableNode.getColumn(i);
            GraphColumnInfo colInfo = createColInfo(tableName, columnName,
               tableNode.getColumnType(columnName));

            if(!cols.contains(colInfo) && !vpmHiddenCols.apply(source, columnName)) {
               cols.add(colInfo);
            }
         }
      }

      return cols;
   }

   private GraphColumnInfo createColInfo(String tableName, String colName, String colType) {
      GraphColumnInfo colInfo = new GraphColumnInfo();
      colInfo.setId(tableName + GraphColumnInfo.TABLE_COLUMN_SEPARATOR + colName);
      colInfo.setName(colName);
      colInfo.setType(colType);
      colInfo.setTable(tableName);
      return colInfo;
   }

   private Rectangle getTableBounds(SelectTable selectTable) {
      Point location = selectTable.getLocation();
      return new Rectangle(location.x, location.y,
         GRAPH_DEFAULT_COLUMN_WIDTH, PORTAL_GRAPH_NODE_HEIGHT);
   }

   private XDataModel getDataModel(String database) throws Exception {
      return dataSourceService.getDataModel(database);
   }

   private DefaultMetaDataProvider getMetaDataProvider(String database)
      throws Exception
   {
      XDataModel dataModel = getDataModel(database);
      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
      return dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
   }

   public TableNode getTableNode(AssetEntry entry, String database)
      throws Exception
   {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      JDBCDataSource xds = (JDBCDataSource) registry.getDataSource(database);
      String name = entry.getProperty("source");
      SQLTypes sqlTypes = SQLTypes.getSQLTypes(xds);
      XNode node = sqlTypes.getQualifiedTableNode(
         name,
         "true".equals(entry.getProperty("hasCatalog")),
         "true".equals(entry.getProperty("hasSchema")),
         entry.getProperty("catalogSep"), xds,
         entry.getProperty(XSourceInfo.CATALOG),
         entry.getProperty(XSourceInfo.SCHEMA));

      if(entry.getProperty("supportCatalog") != null) {
         node.setAttribute("supportCatalog", entry.getProperty("supportCatalog"));
      }

      SQLHelper helper = SQLHelper.getSQLHelper(xds);

      if(helper != null && "databricks".equals(helper.getSQLHelperType())) {
         String quote = helper.getQuote();

         // for databricks, default is not keyword, default schema should not be quoted.
         if(Tool.equals(node.getAttribute("schema"), Tool.buildString(quote, "default", quote))) {
            node.setAttribute("schema", "default");
         }
      }


      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(database);
      TableNode tableNode = metaDataProvider.getTableMetaData(node);

      if(tableNode.getType() == null) {
         tableNode.setType(getTableType(entry, database));
      }

      return tableNode;
   }

   public void autoLayoutGraph(String runtimeId, Principal principal) throws Exception {
      JoinGraphModel graphModel = getQueryGraphModel(runtimeId, null, principal);
      RuntimeQueryService.RuntimeXQuery query =
         runtimeQueryService.getRuntimeQuery(runtimeId);

      if(graphModel == null || query == null || query.getQuery() == null ||
         !(query.getQuery().getSQLDefinition() instanceof UniformSQL))
      {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getQuery().getSQLDefinition();
      GraphLayout graphLayout = new GraphLayout(graphModel.getGraphViewModel(), false);
      graphLayout.layout();

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable selectTable = sql.getSelectTable(i);
         String name;

         if(selectTable.getName() instanceof UniformSQL) {
            name =  selectTable.getAlias();
         }
         else {
            String fullName = Tool.toString(selectTable.getName());
            name = selectTable.getAlias() != null ? selectTable.getAlias() : fullName;
         }

         selectTable.setLocation(graphLayout.getBounds(name).getLocation());
      }
   }

   private String getTableType(AssetEntry entry, String database) {
      String ppath = entry.getParentPath();

      if(Tool.isEmptyString(ppath)) {
         return null;
      }

      ppath = ppath.substring(database.length() + 1);
      String[] parents = ppath.split("/");
      return parents.length > 0 ? parents[0] : null;
   }

   private SelectTable getSelectTable(UniformSQL sql, String tableName) {
      if(sql != null && tableName != null) {
         SelectTable[] tables = sql.getSelectTable();

         for(SelectTable table : tables) {
            if(tableName.equals(table.getAlias())) {
               return table;
            }
         }
      }

      return null;
   }

   private static void fixTablePosition(List<TableGraphModel> tables) {
      int left = JOIN_EDIT_PANE_TABLE_PADDING_LEFT;

      for(TableGraphModel table : tables) {
         table.getBounds().y = JOIN_EDIT_PANE_TABLE_PADDING_TOP;
         table.getBounds().x = left;
         left += JOIN_EDIT_PANE_TABLE_SPACE;
      }
   }

   private void autoCreateJoinByColumn(UniformSQL sql, TableGraphModel leftTable,
                                       TableGraphModel rightTable, DefaultMetaDataProvider metaData)
   {
      if(CollectionUtils.isEmpty(leftTable.getColumns()) ||
         CollectionUtils.isEmpty(rightTable.getColumns()))
      {
         return;
      }

      List<JoinModel> autoJoins = new ArrayList<>();
      List<GraphColumnInfo> leftTableColumns = leftTable.getColumns();
      List<GraphColumnInfo> rightTableColumns = rightTable.getColumns();
      int rightColsLength = rightTableColumns.size();
      Map<String, Integer> rightColumnsMapping = new HashMap<>(); // column name --> index

      for(int i = 0; i < rightColsLength; i++) {
         rightColumnsMapping.put(rightTableColumns.get(i).getName(), i);
      }

      for(GraphColumnInfo leftColumn : leftTableColumns) {
         Integer index = rightColumnsMapping.get(leftColumn.getName());

         if(index != null && index >= 0) {
            GraphColumnInfo rightColumn = rightTableColumns.get(index);

            if(Tool.equals(leftColumn.getType(), rightColumn.getType(), false)) {
               JoinModel joinModel = makeDefaultJoin(leftTable.getName(),
                  leftColumn.getName(), rightColumn.getTable(), rightColumn.getName(), metaData);
               autoJoins.add(joinModel);

               // just process first join
               break;
            }
         }
      }

      leftTable.getJoins().addAll(autoJoins);
      autoJoins.forEach(joinModel -> sql.addJoin(createXJoin(joinModel)));
   }

   private XJoin createXJoin(JoinModel joinModel) {
      XExpression expression1 =
         new XExpression(joinModel.getTable() + "." + joinModel.getColumn(), XExpression.FIELD);
      XExpression expression2 =
         new XExpression(joinModel.getForeignTable() + "." + joinModel.getForeignColumn(), XExpression.FIELD);
      return new XJoin(expression1, expression2, joinModel.getType().getType());
   }

   private JoinModel makeDefaultJoin(String leftTable, String leftColumn, String rightTable,
                                     String rightColumn, DefaultMetaDataProvider metadata)
   {
      JoinModel join = new JoinModel();

      join.setTable(leftTable);
      join.setColumn(leftColumn);
      join.setForeignColumn(rightColumn);
      join.setForeignTable(rightTable);
      join.setType(JoinType.EQUAL);
      join.setMergingRule(MergingRule.AND);
      join.setSupportFullOuter(
         JDBCUtil.supportFullOuterJoin((JDBCDataSource) metadata.getDataSource(), true));

      return join;
   }

   private UniformSQL processDeleteJoins(UniformSQL sql, TableJoinInfo joinInfo) {
      UniformSQL clone = sql.clone();
      clone.removeAllJoins();
      XJoin[] joins = sql.getJoins();
      String sourceTable = joinInfo.getSourceTable();
      String targetTable = joinInfo.getTargetTable();
      String sourceTableColumn = null;
      String targetTableColumn = null;

      if(joinInfo instanceof TableDetailJoinInfo) {
         sourceTableColumn = sourceTable + "." + ((TableDetailJoinInfo) joinInfo).getSourceColumn();
         targetTableColumn = targetTable + "." + ((TableDetailJoinInfo) joinInfo).getTargetColumn();
      }

      for(XJoin join : joins) {
         boolean delete = false;

         if(sourceTableColumn != null &&
            sourceTableColumn.equals(Tool.toString(join.getExpression1().getValue())) &&
            targetTableColumn.equals(Tool.toString(join.getExpression2().getValue())))
         {
            delete = true;
         }

         if(sourceTableColumn == null &&
            sourceTable.equals(Tool.toString(join.getTable1())) &&
            targetTable.equals(Tool.toString(join.getTable2())))
         {
            delete = true;
         }

         if(!delete) {
            clone.addJoin(join);
         }
      }

      return clone;
   }

   private UniformSQL processEditJoin(UniformSQL sql, TableDetailJoinInfo detailJoinInfo,
                                      JoinModel joinModel)
   {
      UniformSQL clone = sql.clone();
      clone.removeAllJoins();
      XJoin[] joins = sql.getJoins();
      String sourceTableColumn =
         detailJoinInfo.getSourceTable() + "." + detailJoinInfo.getSourceColumn();
      String targetTableColumn =
         detailJoinInfo.getTargetTable() + "." + detailJoinInfo.getTargetColumn();

      for(XJoin join : joins) {
         if(sourceTableColumn.equals(Tool.toString(join.getExpression1().getValue())) &&
            targetTableColumn.equals(Tool.toString(join.getExpression2().getValue())))
         {
            join.setOp(joinModel.getType().getType());
         }

         clone.addJoin(join);
      }

      return clone;
   }

   private UniformSQL processRenameJoins(UniformSQL sql, String oldName, String newName) {
      UniformSQL clone = sql.clone();
      clone.removeAllJoins();
      XJoin[] joins = sql.getJoins();

      if(joins != null) {
         for(XJoin join : joins) {
            XExpression expression1 = join.getExpression1();
            XExpression expression2 = join.getExpression2();
            String value1 = Tool.toString(expression1.getValue());
            String value2 = Tool.toString(expression2.getValue());
            String prefix = oldName + ".";

            if(value1.startsWith(prefix)) {
               expression1.setValue(value1.replace(oldName, newName));
               join.setExpression1(expression1);
            }

            if(value2.startsWith(prefix)) {
               expression2.setValue(value2.replace(oldName, newName));
               join.setExpression2(expression2);
            }

            clone.addJoin(join);
         }
      }

      return clone;
   }

   private JDBCQuery getQuery(String runtimeId) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         runtimeQueryService.getRuntimeQuery(runtimeId);

      if(runtimeQuery == null) {
         return null;
      }

      return runtimeQuery.getQuery();
   }

   private Point getPosition(Point position, int index) {
      int x = Math.max(position.x, 0);
      int y = Math.max(position.y - GRAPH_NODE_HEIGHT / 2, 0);

      if(index > 0) {
         y = y + (GRAPH_NODE_HEIGHT  + ADD_NODE_TOP_GAP) * index;
      }

      return new Point(x, y);
   }

   private final RuntimeQueryService runtimeQueryService;
   private final DataSourceService dataSourceService;
   private final QueryManagerService queryService;
   private static final int JOIN_EDIT_PANE_TABLE_PADDING_TOP = 15;
   private static final int JOIN_EDIT_PANE_TABLE_PADDING_LEFT = 30;
   private static final int JOIN_EDIT_PANE_TABLE_SPACE = 180;
   public static final int DEFAULT_GRAPH_VIEW_PADDING = 10;
   public static final int GRAPH_NODE_HEIGHT = 26;
   public static final int ADD_NODE_TOP_GAP = 15;
}
