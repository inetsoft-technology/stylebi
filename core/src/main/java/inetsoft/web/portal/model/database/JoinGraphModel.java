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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.DatabaseModelUtil;
import inetsoft.web.portal.controller.database.PhysicalModelService;
import inetsoft.web.portal.model.database.events.GetGraphModelEvent;
import inetsoft.web.portal.model.database.graph.NodeConnectionInfo;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.uql.erm.XPartition.OUTGOING_TABLE_SEPARATOR;
import static inetsoft.web.portal.model.database.graph.PhysicalGraphLayout.PORTAL_GRAPH_NODE_HEIGHT;

@JsonIgnoreProperties
public class JoinGraphModel {

   public JoinGraphModel() {
   }

   public JoinGraphModel(boolean joinEdit) {
      this(joinEdit, null);
   }

   public JoinGraphModel(boolean joinEdit, GraphViewModel graph) {
      this.joinEdit = joinEdit;
      this.graphViewModel = graph;
   }

   /**
    * @return true if current view for join edit in physical view.
    */
   public boolean isJoinEdit() {
      return joinEdit;
   }

   public void setJoinEdit(boolean joinEdit) {
      this.joinEdit = joinEdit;
   }

   public GraphViewModel getGraphViewModel() {
      return graphViewModel;
   }

   public void setGraphViewModel(GraphViewModel graphViewModel) {
      this.graphViewModel = graphViewModel;
   }

   public JoinEditPaneModel getJoinEditPaneModel() {
      return joinEditPaneModel;
   }

   public void setJoinEditPaneModel(JoinEditPaneModel joinEditPaneModel) {
      this.joinEditPaneModel = joinEditPaneModel;
   }

   public static JoinGraphModel convertModel(PhysicalModelDefinition pmModel,
                                             GetGraphModelEvent event,
                                             XPartition partition)
   {
      return convertModel(pmModel, event, partition, true);
   }

   public static JoinGraphModel convertModel(PhysicalModelDefinition pmModel,
                                             GetGraphModelEvent event,
                                             XPartition partition,
                                             boolean runtime)
   {
      if(pmModel == null) {
         return null;
      }

      if(event != null && event.getTableJoinInfo() != null) {
         return buildJoinEditPaneModel(pmModel, event, partition);
      }

      return buildGraphModel(pmModel, event == null ? null : event.getDatasource(),
         runtime, partition);
   }

   private static JoinGraphModel buildGraphModel(PhysicalModelDefinition pmModel,
                                                 String database, boolean runtime,
                                                 XPartition partition)
   {
      List<PhysicalTableModel> tables = pmModel.getTables();
      List<GraphModel> graphModels = new ArrayList<>();
      GraphViewModel graph = new GraphViewModel(graphModels);
      JoinGraphModel pgm = new JoinGraphModel(false, graph);

      if(CollectionUtils.isEmpty(tables)) {
         return pgm;
      }

      boolean autoAlias;
      XPartition originalPartition = partition;
      partition = partition.applyAutoAliases();

      for(PhysicalTableModel table : tables) {
         GraphModel graphModel = new GraphModel();

         String aliasSource = table.getAliasSource();
         autoAlias = table.isAutoAliasesEnabled();

         if(runtime && !autoAlias) {
            autoAlias = StringUtils.hasText(aliasSource)
               && isAutoAliasTables(partition, table, aliasSource);
         }

         graphModel.setAlias(StringUtils.hasText(table.getAlias()));
         boolean isOutgoing = graphModel.isAlias()
            && !originalPartition.isAlias(table.getAlias()) && !autoAlias;
         graphModel.setAutoAliasByOutgoing(isOutgoing);
         graphModel.setNode(buildGraphNodeModel(
            table, database, autoAlias, isOutgoing, pmModel, runtime, partition));
         graphModel.setEdge(buildGraphNodeEdge(
            table, tables, originalPartition, partition, runtime));
         graphModel.setCols(buildColumns(table));
         graphModel.setAutoAlias(autoAlias);
         graphModel.setSql(StringUtils.hasText(table.getSql()));
         graphModel.setBaseTable(table.isBaseTable());

         Rectangle bounds = fixBounds(table.getBounds().getBounds(), runtime);
         graphModel.setBounds(bounds);

         if(graphModel.isAlias()) {
            String sourceTable = DatabaseModelUtil.getOutgoingAutoAliasSource(table.getAlias(),
               originalPartition, partition);

            if(sourceTable != null) {
               graphModel.setDesignModeAlias(originalPartition.isAlias(sourceTable));
               graphModel.setOutgoingAutoAliasSource(sourceTable);
            }
         }

         graphModels.add(graphModel);
      }

      return pgm;
   }

   private static boolean isAutoAliasTables(XPartition partition, PhysicalTableModel table,
                                            String aliasSource)
   {
      String qualifiedName = table.getQualifiedName();
      boolean result = aliasSource.equals(partition.getAllAutoAliasTable(qualifiedName));

      if(result) {
         // auto alias table
         return true;
      }

      // check outgoing table.
      AutoAlias autoAlias;
      AutoAlias.IncomingJoin incomingJoin;

      for(JoinModel joinModel: table.getJoins()) {
         String aliasTable = joinModel.getForeignTable();
         String realTable = partition.getRealTable(aliasTable, true);
         autoAlias = partition.getAutoAlias(realTable);

         if(autoAlias != null && autoAlias.getIncomingJoinCount() > 0) {
            for (int i = 0; i < autoAlias.getIncomingJoinCount(); i++) {
               incomingJoin = autoAlias.getIncomingJoin(i);

               if(incomingJoin.isKeepOutgoing() &&
                  qualifiedName.equals(incomingJoin.getPrefix()
                     + OUTGOING_TABLE_SEPARATOR + aliasSource))
               {
                  result = true;
                  break;
               }
            }
         }
      }

      return result;
   }

   /**
    * Scale location and change height to portal graph height.
    */
   private static Rectangle fixBounds(Rectangle bounds, boolean runtime) {
      if(!runtime) {
         return bounds;
      }

      int x = (int) Math.round(bounds.x * SCALE_X);
      int y = (int) Math.round(bounds.y * SCALE_Y);
      int width = (int) Math.round(bounds.width * SCALE_X);

      return new Rectangle(x, y, width, PORTAL_GRAPH_NODE_HEIGHT);
   }

   private static List<GraphColumnInfo> buildColumns(PhysicalTableModel table) {
      return table.getCols();
   }

   private static GraphNodeModel buildGraphNodeModel(PhysicalTableModel table, String database,
                                                     boolean autoAlias, boolean isOutgoing,
                                                     PhysicalModelDefinition pmModel,
                                                     boolean runtime, XPartition partition)
   {
      GraphNodeModel graphNode = new GraphNodeModel();

      graphNode.setId(table.getQualifiedName());
      graphNode.setName(table.getQualifiedName());
      graphNode.setTableName(table.getName());
      String label = StringUtils.hasText(table.getAlias())
         ? table.getAlias()
         : table.getQualifiedName();
      graphNode.setLabel(label);
      graphNode.setTooltip(getTitleToolTip(table.getQualifiedName(), !StringUtils.isEmpty(table.getAlias()),
         table.getAliasSource()));
      graphNode.setAliasSource(table.getAliasSource()); // alias and auto alias
      graphNode.setOutgoingAliasSource(table.getOutgoingAliasSource());
      boolean extendView = partition.getBasePartition() != null;

      if(autoAlias && runtime) {
         PhysicalTableModel physicalTable
            = getPhysicalTable(pmModel.getTables(), table.getAlias());
         graphNode.setTreeLink(extendView && table.getOutgoingAliasSource() != null
            && table.getPath() != null && table.getPath().startsWith(partition.getConnection() + "/")
            ? database + table.getPath().substring(table.getPath().indexOf("/")) : physicalTable.getPath());
      }
      else if(table.getAlias() != null && !isOutgoing) {
         graphNode.setTreeLink(database + "/" + table.getAlias());
      }
      else if(extendView && table.getPath() != null &&
         table.getPath().startsWith(partition.getConnection() + "/"))
      {
         graphNode.setTreeLink(database + table.getPath().substring(table.getPath().indexOf("/")));
      }
      else {
         graphNode.setTreeLink(table.getPath());
      }

      return graphNode;
   }

   /**
    * Gets the tooltip for the table.
    *
    * @return the tooltip text.
    */
   private static String getTitleToolTip(String tableName, boolean alias, String aliasSource) {
      return alias ? "-> " + aliasSource : tableName;
   }

   private static GraphEdgeModel buildGraphNodeEdge(PhysicalTableModel table,
                                                    List<PhysicalTableModel> tables,
                                                    XPartition originalPartition,
                                                    XPartition partition,
                                                    boolean runtime)
   {
      GraphEdgeModel graphEdge = new GraphEdgeModel();

      graphEdge.setOutput(
         buildOutputJoins(table, originalPartition, partition, runtime));
      graphEdge.setInput(
         buildInputJoins(table, tables, originalPartition, partition, runtime));

      return graphEdge;
   }

   private static List<NodeConnectionInfo> buildInputJoins(final PhysicalTableModel table,
                                                           List<PhysicalTableModel> tables,
                                                           XPartition originalPartition,
                                                           XPartition partition,
                                                           boolean runtime)
   {
      List<NodeConnectionInfo> in = new ArrayList<>();

      final String currentTableName = table.getQualifiedName();

      tables.stream()
         .filter(t -> t != table) // filter self
         .flatMap(t -> t.getJoins().stream()) // map to join model
         .forEach(join -> {
            if(currentTableName.equals(join.getForeignTable())) {
               String dependentTable = join.getRelationship().getDependentTable();

               if(runtime && !originalPartition.isRealTable(dependentTable)
                  && partition.isRealTable(dependentTable))
               {
                  // auto alias
                  dependentTable = partition.getAlias(dependentTable);
               }

               NodeConnectionInfo nodeConnectionInfo =
                  new NodeConnectionInfo(dependentTable, join);
               in.add(nodeConnectionInfo);
            }
         });

      return in;
   }

   private static List<NodeConnectionInfo> buildOutputJoins(PhysicalTableModel table,
                                                            XPartition originalPartition,
                                                            XPartition partition,
                                                            boolean runtime)
   {
      List<NodeConnectionInfo> out = new ArrayList<>();

      List<JoinModel> joins = table.getJoins();

      for(JoinModel join : joins) {
         String foreignTable = join.getForeignTable();
         join.setTable(join.getTable());

         if(runtime && !originalPartition.isRealTable(foreignTable)
            && partition.isRealTable(foreignTable))
         {
            // auto alias
            join.setForeignTable(partition.getAlias(foreignTable));
         }

         NodeConnectionInfo nodeConnectionInfo
            = new NodeConnectionInfo(table.getQualifiedName(), join);

         out.add(nodeConnectionInfo);
      }

      return out;
   }

   private static JoinGraphModel buildJoinEditPaneModel(PhysicalModelDefinition pmModel,
                                                        GetGraphModelEvent event,
                                                        XPartition partition)
   {
      TableJoinInfo tableJoinInfo = event.getTableJoinInfo();
      List<PhysicalTableModel> pmTables = pmModel.getTables();
      JoinGraphModel pgm = new JoinGraphModel(true);
      String sourceTable = tableJoinInfo.getSourceTable();
      String targetTable = tableJoinInfo.getTargetTable();

      if(CollectionUtils.isEmpty(pmTables) || StringUtils.isEmpty(sourceTable)
         || StringUtils.isEmpty(targetTable))
      {
         return pgm;
      }

      XPartition applyAutoAliasPartition = partition.applyAutoAliases();
      sourceTable = DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(sourceTable, partition,
         applyAutoAliasPartition);
      targetTable = DatabaseModelUtil.getOutgoingAutoAliasSourceOrTable(targetTable, partition,
         applyAutoAliasPartition);
      List<TableGraphModel> tables = new ArrayList<>();
      JoinEditPaneModel physicalJoinEditPaneModel = new JoinEditPaneModel(
         event.getRuntimeID(), event.getDatasource(), event.getPhysicalName(), tables);
      pgm.setJoinEditPaneModel(physicalJoinEditPaneModel);

      PhysicalTableModel sourceModel = getPhysicalTable(pmTables, sourceTable);
      PhysicalTableModel targetModel = getPhysicalTable(pmTables, targetTable);

      TableGraphModel leftTable
         = buildEditJoinPaneTableGraphModel(sourceModel, targetTable);
      TableGraphModel rightTable
         = buildEditJoinPaneTableGraphModel(targetModel, sourceTable);

      // auto create inner join by same column name and type
      if(tableJoinInfo.isAutoCreateColumnJoin()) {
         autoCreateJoinByColumn(leftTable, rightTable, partition, pmModel.getMetaData());
      }

      tables.add(leftTable);
      tables.add(rightTable);

      // join edit pane should adjust position.
      fixTablePosition(tables);

      return pgm;
   }

   private static void autoCreateJoinByColumn(TableGraphModel leftTable,
                                              TableGraphModel rightTable,
                                              XPartition partition,
                                              DefaultMetaDataProvider metaData)
   {
      if(CollectionUtils.isEmpty(leftTable.getColumns())
         || CollectionUtils.isEmpty(rightTable.getColumns()))
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
               JoinModel joinModel = makeDefaultJoin(
                  leftTable.getName(), leftColumn.getName(), rightColumn.getTable(),
                  rightColumn.getName(), metaData, partition);
               autoJoins.add(joinModel);

               // just process first join
               break;
            }
         }
      }

      leftTable.getJoins().addAll(autoJoins);

      autoJoins.stream().forEach(joinModel -> {
         XRelationship join = PhysicalModelService.createJoin(leftTable.getName(), joinModel);

         if(!joinExist(partition, join)) {
            partition.addRelationship(join);
         }
      });
   }

   /**
    * Check whether the join base on same table and column is exist.
    * @param partition
    * @param join
    * @return
    */
   private static boolean joinExist(XPartition partition, XRelationship join) {
      Enumeration<XRelationship> relationships = partition.getRelationships();

      while(relationships != null && relationships.hasMoreElements()) {
         XRelationship relationship = relationships.nextElement();

         if(Tool.equals(join.getDependentTable(), relationship.getDependentTable()) &&
            Tool.equals(join.getIndependentTable(), relationship.getIndependentTable()) &&
            Tool.equals(join.getDependentColumn(), relationship.getDependentColumn()) &&
            Tool.equals(join.getIndependentColumn(), relationship.getIndependentColumn()))
         {
            return true;
         }

         if(Tool.equals(join.getDependentTable(), relationship.getIndependentTable()) &&
            Tool.equals(join.getIndependentTable(), relationship.getDependentTable()) &&
            Tool.equals(join.getDependentColumn(), relationship.getIndependentColumn()) &&
            Tool.equals(join.getIndependentColumn(), relationship.getDependentColumn()))
         {
            return true;
         }
      }

      return false;
   }

   public static JoinModel makeDefaultJoin(String leftTable, String leftColumn, String rightTable,
                                           String rightColumn, DefaultMetaDataProvider metadata,
                                           XPartition partition)
   {
      JoinModel join = new JoinModel();

      join.setTable(leftTable);
      join.setColumn(leftColumn);
      join.setForeignColumn(rightColumn);
      join.setForeignTable(rightTable);
      join.setType(JoinType.EQUAL);
      join.setOrderPriority(DEFAULT_JOIN_ORDER_PRIORITY);
      join.setMergingRule(MergingRule.AND);
      join.setCardinality(getCardinality(leftTable, leftColumn, rightTable, rightColumn,
         metadata, partition));
      join.setSupportFullOuter(
         JDBCUtil.supportFullOuterJoin((JDBCDataSource) metadata.getDataSource(), true));

      return join;
   }

   private static JoinCardinality getCardinality(String leftTable, String leftColumn,
                                                 String rightTable, String rightColumn,
                                                 DefaultMetaDataProvider metadata,
                                                 XPartition partition)
   {
      boolean dpk = false;
      boolean ipk = false;

      try {
         TableNode sourceMetaData = PhysicalModelService.getTableNode(partition, leftTable, metadata);
         TableNode targetMetaData = PhysicalModelService.getTableNode(partition, rightTable, metadata);

         XNode dpks = metadata.getPrimaryKeys((XNode) sourceMetaData.getUserObject());
         XNode ipks =  metadata.getPrimaryKeys((XNode) targetMetaData.getUserObject());
         dpk = XUtil.isPrimaryKey(leftColumn, dpks);
         ipk = XUtil.isPrimaryKey(rightColumn, ipks);
      }
      catch(Exception exp) {
      }

      if(dpk && ipk) {
         return JoinCardinality.ONE_TO_ONE;
      }
      else if(dpk && !ipk) {
         return JoinCardinality.ONE_TO_MANY;
      }
      else if(!dpk && ipk) {
         return JoinCardinality.MANY_TO_ONE;
      }
      else {
         return JoinCardinality.MANY_TO_MANY;
      }
   }

   private static void fixTablePosition(List<TableGraphModel> tables) {
      int left = JOIN_EDIT_PANE_TABLE_PADDING_LEFT;

      for(TableGraphModel table : tables) {
         table.getBounds().y = JOIN_EDIT_PANE_TABLE_PADDING_TOP;
         table.getBounds().x = left;
         left += (table.getBounds().width + JOIN_EDIT_PANE_TABLE_SPACE);
      }
   }

   private static PhysicalTableModel getPhysicalTable(List<PhysicalTableModel> pmTables,
                                                      String targetTable)
   {
      return pmTables.stream()
         .filter(table -> targetTable.equals(table.getQualifiedName()))
         .findFirst().orElse(null);
   }

   private static TableGraphModel buildEditJoinPaneTableGraphModel(
      PhysicalTableModel pmTable, final String joinTable)
   {
      if(pmTable == null) {
         return null;
      }

      TableGraphModel physicalTableGraphModel = new TableGraphModel();
      physicalTableGraphModel.setName(pmTable.getQualifiedName());
      physicalTableGraphModel.setBounds(pmTable.getBounds().getBounds());
      physicalTableGraphModel.setColumns(pmTable.getCols());

      if(StringUtils.hasText(joinTable)) {
         List<JoinModel> joins = pmTable.getJoins().stream()
            .filter(joinModel -> joinTable.equals(joinModel.getForeignTable()))
            .collect(Collectors.toList());

         physicalTableGraphModel.setJoins(joins);
      }

      return physicalTableGraphModel;
   }

   private boolean joinEdit;
   private GraphViewModel graphViewModel;
   private JoinEditPaneModel joinEditPaneModel;

   public static final double SCALE_X = 1.5d;
   public static final double SCALE_Y = 1.5d;

   private static final int DEFAULT_JOIN_ORDER_PRIORITY = 1;
   private static final int JOIN_EDIT_PANE_TABLE_SPACE = 180;
   private static final int JOIN_EDIT_PANE_TABLE_PADDING_TOP = 15;
   private static final int JOIN_EDIT_PANE_TABLE_PADDING_LEFT = 30;
}
