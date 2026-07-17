/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.jdbc.*;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.composer.model.LoadAssetTreeNodesEvent;
import inetsoft.web.composer.model.LoadAssetTreeNodesValidator;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import inetsoft.web.wiz.request.SchemaSearchRequest;

import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class MetadataApiService {
   public MetadataApiService(XRepository xrepository, DataSourceService dataSourceService,
                             AssetRepository assetRepository, AssetTreeService assetTreeService,
                             ObjectMapper objectMapper)
   {
      this.xrepository = xrepository;
      this.dataSourceService = dataSourceService;
      this.assetRepository = assetRepository;
      this.assetTreeService = assetTreeService;
      this.objectMapper = objectMapper;
   }

   /**
    * Gets the child nodes of the specified parent node.
    *
    * @return the child nodes.
    */
   public LoadAssetTreeNodesValidator getNodes(LoadAssetTreeNodesEvent event, Principal principal)
      throws Exception
   {
      LoadAssetTreeNodesValidator result =
         assetTreeService.getNodes(true, false, true,
                                   false, false, false,
                                   false, false,
                                   true, true, event, principal);
      TreeNodeModel filteredTree = filterWizTree(result.treeNodeModel());

      return LoadAssetTreeNodesValidator.builder()
         .treeNodeModel(filteredTree)
         .parameters(result.parameters())
         .build();
   }

   public OsiDataset getMetaData(GetDatabaseTableMetaRequest data, Principal principal) throws Exception {
      String dsName = data.getDsName();

      if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, principal)) {
         throw new SecurityException("Access denied to data source: " + dsName);
      }

      JDBCDataSource jdbcDataSource = getJDBCDatasource(dsName);
      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(dsName);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + dsName);
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(rootMetaData == null) {
         throw new Exception("No meta data found for data source " + dsName);
      }

      SQLTypes sqlTypes = SQLTypes.getSQLTypes(jdbcDataSource);
      XNode node = sqlTypes.getQualifiedTableNode(data.getTableName(),
         "true".equals(rootMetaData.getAttribute("hasCatalog")),
         "true".equals(rootMetaData.getAttribute("hasSchema")),
         (String) rootMetaData.getAttribute("catalogSep"), jdbcDataSource,
         data.getCatalog(), data.getSchema());

      if(rootMetaData.getAttribute("supportCatalog") != null) {
         node.setAttribute("supportCatalog", rootMetaData.getAttribute("supportCatalog"));
      }

      XNode tableData = metaDataProvider.getMetaData(node, true);
      XNode tableNode = tableData != null ? tableData.getChild(0) : null;

      if(tableNode == null) {
         throw new Exception(Tool.buildString("Table ", data.getTableName(), " not found in data source ", dsName));
      }

      // Derive table name/catalog/schema from the request directly to avoid a second DB round-trip.
      String tableName = data.getTableName();
      String catalog = Tool.isEmptyString(data.getCatalog()) ? null : data.getCatalog();
      String schema = Tool.isEmptyString(data.getSchema()) ? null : data.getSchema();
      String source = buildSource(catalog, schema, tableName);

      OsiDataset dataset = new OsiDataset();
      dataset.setName(tableName);
      dataset.setSource(source);
      String datasourceType = SQLHelper.getProductName(jdbcDataSource);
      String datasourceVersion = null;

      try {
         datasourceVersion = SQLHelper.getSQLHelper(jdbcDataSource).getProductVersion();
      }
      catch(Exception e) {
         log.debug("Could not retrieve database product version for '{}': {}", dsName, e.getMessage());
      }

      dataset.setCustomExtensions(List.of(buildDatasetExtension(dsName, catalog, schema, source, datasourceType, datasourceVersion)));

      Object synonyms = tableData.getAttribute("synonyms");
      boolean hasSynonyms = synonyms instanceof String s ? !s.isEmpty()
         : synonyms instanceof Collection<?> c ? !c.isEmpty()
         : synonyms != null;

      if(hasSynonyms) {
         Map<String, Object> aiContext = new LinkedHashMap<>();
         aiContext.put("synonyms", synonyms);
         dataset.setAiContext(aiContext);
      }

      List<String> primaryKeys = new ArrayList<>();
      List<OsiField> fields = new ArrayList<>();

      for(int i = 0; i < tableNode.getChildCount(); i++) {
         XNode columnNode = tableNode.getChild(i);

         if(columnNode == null) {
            continue;
         }

         String columnName = columnNode.getName();
         String columnType = columnNode instanceof XTypeNode typeNode ? typeNode.getType() : null;
         boolean isPK = "true".equals(columnNode.getAttribute("PrimaryKey"));
         Integer length = (Integer) columnNode.getAttribute("length");
         String comment = (String) columnNode.getAttribute("comment");
         List<String[]> foreignKeys = extractForeignKeys(columnNode);

         if(isPK) {
            primaryKeys.add(columnName);
         }

         OsiField field = new OsiField();
         field.setName(columnName);
         field.setExpression(buildExpression(tableName, columnName));
         field.setDescription(Tool.isEmptyString(comment) ? null : comment);

         if(XSchema.isDateType(columnType)) {
            field.setDimension(new OsiDimension(true));
         }

         field.setCustomExtensions(List.of(buildFieldExtension(columnType, length, foreignKeys)));
         fields.add(field);
      }

      dataset.setPrimaryKey(primaryKeys.isEmpty() ? null : primaryKeys);
      dataset.setFields(fields);

      return dataset;
   }

   private String buildSource(String catalog, String schema, String table) {
      StringBuilder sb = new StringBuilder();

      if(!Tool.isEmptyString(catalog)) {
         sb.append(catalog).append(".");
      }

      if(!Tool.isEmptyString(schema)) {
         sb.append(schema).append(".");
      }

      sb.append(table);
      return sb.toString();
   }

   private OsiExpression buildExpression(String tableName, String columnName) {
      OsiDialectExpression dialectExpr = new OsiDialectExpression();
      // NOTE: ANSI_SQL double-quote quoting is used for all databases. Databases such as MySQL
      // (backtick) and SQL Server (square brackets) use different identifier quoting and will
      // require dialect-specific handling if multi-database support is added in the future.
      dialectExpr.setDialect("ANSI_SQL");
      // Escape embedded double-quotes by doubling them before wrapping in double-quotes.
      String quotedTable = "\"" + tableName.replace("\"", "\"\"") + "\"";
      String quotedColumn = "\"" + columnName.replace("\"", "\"\"") + "\"";
      dialectExpr.setExpression(quotedTable + "." + quotedColumn);

      OsiExpression expression = new OsiExpression();
      expression.setDialects(List.of(dialectExpr));
      return expression;
   }

   private OsiCustomExtension buildDatasetExtension(String dsName, String catalog,
                                                     String schema, String path,
                                                     String datasourceType,
                                                     String datasourceVersion)
   {
      try {
         Map<String, Object> extData = new LinkedHashMap<>();
         extData.put("dsName", dsName);

         // Only include catalog/schema when non-null to keep the extension compact.
         if(!Tool.isEmptyString(catalog)) {
            extData.put("catalog", catalog);
         }

         if(!Tool.isEmptyString(schema)) {
            extData.put("schema", schema);
         }

         extData.put("path", path);

         if(!Tool.isEmptyString(datasourceType)) {
            extData.put("datasourceType", datasourceType);
         }

         if(!Tool.isEmptyString(datasourceVersion)) {
            extData.put("datasourceVersion", datasourceVersion);
         }

         OsiCustomExtension ext = new OsiCustomExtension();
         ext.setVendorName("COMMON");
         ext.setData(objectMapper.writeValueAsString(extData));
         return ext;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to serialize dataset custom extension", e);
      }
   }

   private OsiCustomExtension buildFieldExtension(String type, Integer length,
                                                   List<String[]> foreignKeys)
   {
      try {
         Map<String, Object> extData = new LinkedHashMap<>();

         if(type != null) {
            extData.put("type", type);
         }

         if(length != null) {
            extData.put("length", length);
         }

         if(!foreignKeys.isEmpty()) {
            extData.put("foreignKeys", foreignKeys);
         }

         OsiCustomExtension ext = new OsiCustomExtension();
         ext.setVendorName("COMMON");
         ext.setData(objectMapper.writeValueAsString(extData));
         return ext;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to serialize field custom extension", e);
      }
   }

   private List<String[]> extractForeignKeys(XNode columnNode) {
      Object attr = columnNode.getAttribute("ForeignKey");

      if(attr instanceof Vector<?> vector) {
         List<String[]> foreignKeys = new ArrayList<>();

         for(Object item : vector) {
            if(item instanceof String[] arr) {
               foreignKeys.add(arr);
            }
         }

         return foreignKeys;
      }

      return Collections.emptyList();
   }

   public JDBCDataSource getJDBCDatasource(String dsName) throws Exception {
      XDataSource dataSource = xrepository.getDataSource(dsName);

      if(!(dataSource instanceof JDBCDataSource jdbcDataSource)) {
         throw new Exception("Data source " + dsName + " not found.");
      }

      return jdbcDataSource;
   }

   public XNode getTableMetaData(JDBCDataSource jdbcDatasource, String catalog, String schema, String tableName) throws Exception {
      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(jdbcDatasource);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + jdbcDatasource.getFullName());
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(rootMetaData == null) {
         throw new Exception("No meta data found for data source " + jdbcDatasource.getFullName());
      }

      return metaDataProvider.getTable(Tool.isEmptyString(catalog) ? null : catalog, Tool.isEmptyString(schema) ? null : schema, tableName, false);
   }

   public inetsoft.web.wiz.model.WorksheetMeta getWorksheetMetaData(String wsId, XPrincipal principal)
      throws Exception
   {
      if(wsId == null || Tool.isEmptyString(wsId)) {
         throw new Exception("Invalid request.");
      }

      AssetEntry entry = AssetEntry.createAssetEntry(wsId);
      AbstractSheet sheet = assetRepository.getSheet(entry, principal, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new Exception("Worksheet " + wsId + " not found.");
      }

      List<WorksheetTableMeta> tables = new ArrayList<>();

      for(Assembly assembly : sheet.getAssemblies()) {
         if(!(assembly instanceof TableAssembly tableAssembly)) {
            continue;
         }

         tables.add(getWorksheetTableMeta(tableAssembly));
      }

      inetsoft.web.wiz.model.WorksheetMeta worksheetMeta = new inetsoft.web.wiz.model.WorksheetMeta();
      worksheetMeta.setPath(entry.getPath());
      worksheetMeta.setName(entry.getName());
      worksheetMeta.setDescription(sheet.getDescription());
      worksheetMeta.setTables(tables);

      WSAssembly primaryAssembly = worksheet.getPrimaryAssembly();

      if(primaryAssembly != null) {
         worksheetMeta.setPrimaryTable(primaryAssembly.getName());
      }

      return worksheetMeta;
   }

   private WorksheetTableMeta getWorksheetTableMeta(TableAssembly tableAssembly) {
      WorksheetTableMeta tableMeta;

      switch(tableAssembly) {
      case AbstractJoinTableAssembly joinTable ->
         tableMeta = getJoinTableMetadata(joinTable);
      case UnpivotTableAssembly unpivotTable ->
         tableMeta = getUnpivotTableMetadata(unpivotTable);
      case MirrorTableAssembly mirrorTable ->
         tableMeta = getMirrorTableMetadata(mirrorTable);
      case RotatedTableAssembly rotatedTableAssembly ->
         tableMeta = getRotatedTableMetadata(rotatedTableAssembly);
      default -> tableMeta = new WorksheetTableMeta();
      }

      populateRegularTableMetadata(tableAssembly, tableMeta);

      return tableMeta;
   }

   /**
    * Populate metadata for regular table assemblies
    */
   private void populateRegularTableMetadata(TableAssembly tableAssembly,
                                             WorksheetTableMeta tableMeta)
   {
      tableMeta.setName(tableAssembly.getName());
      ColumnSelection columnSelection = tableAssembly.getColumnSelection(true);

      if(columnSelection != null) {
         List<WorksheetTableMeta.WSColumnMeta> columns = new ArrayList<>();
         int columnCount = columnSelection.getAttributeCount();

         for(int i = 0; i < columnCount; i++) {
            DataRef columnRef = columnSelection.getAttribute(i);

            if(columnRef != null) {
               WorksheetTableMeta.WSColumnMeta WSColumnMeta = createColumnMeta((ColumnRef) columnRef);
               columns.add(WSColumnMeta);
            }
         }

         tableMeta.setColumns(columns);
      }
   }

   /**
    * Populate metadata for join table assemblies
    */
   private JoinTableMeta getJoinTableMetadata(AbstractJoinTableAssembly joinTable) {
      JoinTableMeta joinTableMeta = new JoinTableMeta();
      Enumeration<TableAssemblyOperator> operators = joinTable.getOperators();

      if(operators != null) {
         List<JoinTableMeta.JoinInfo> joins = new ArrayList<>();

         while(operators.hasMoreElements()) {
            TableAssemblyOperator operator = operators.nextElement();

            if(operator == null) {
               continue;
            }

            for(TableAssemblyOperator.Operator operatorOperator : operator.getOperators()) {
               if(operatorOperator == null) {
                  continue;
               }

               String leftTable = operatorOperator.getLeftTable();
               String rightTable = operatorOperator.getRightTable();

               if(leftTable != null && rightTable != null) {
                  JoinTableMeta.JoinInfo joinInfo =
                     createJoinInfo(operatorOperator, leftTable, rightTable);
                  joins.add(joinInfo);
               }
            }
         }

         joinTableMeta.setJoins(joins);
      }

      return joinTableMeta;
   }

   /**
    * Populate metadata for unpivot table assemblies
    */
   private UnpivotTableMeta getUnpivotTableMetadata(UnpivotTableAssembly unpivotTable) {
      UnpivotTableMeta unpivotMeta = new UnpivotTableMeta();
      TableAssembly sourceTable = unpivotTable.getTableAssembly();

      if(sourceTable != null) {
         unpivotMeta.setSourceTable(sourceTable.getName());
      }

      unpivotMeta.setHeaderColumns(unpivotTable.getHeaderColumns());

      return unpivotMeta;
   }

   /**
    * Populate metadata for mirror table assemblies
    */
   private MirrorTableMeta getMirrorTableMetadata(MirrorTableAssembly mirrorTable) {
      MirrorTableMeta mirrorMeta = new MirrorTableMeta();
      Assembly baseAssembly = mirrorTable.getAssembly();

      if(baseAssembly != null) {
         mirrorMeta.setBaseTable(baseAssembly.getName());
      }

      return mirrorMeta;
   }

   /**
    * Populate metadata for rotated table assemblies
    */
   private RotatedTableMeta getRotatedTableMetadata(RotatedTableAssembly rotatedTable) {
      RotatedTableMeta rotatedTableMeta = new RotatedTableMeta();

      Assembly baseAssembly = rotatedTable.getTableAssembly();

      if(baseAssembly != null) {
         rotatedTableMeta.setSourceTable(baseAssembly.getName());
      }

      return rotatedTableMeta;
   }

   /**
    * Create column metadata from DataRef
    */
   private WorksheetTableMeta.WSColumnMeta createColumnMeta(ColumnRef columnRef) {
      WorksheetTableMeta.WSColumnMeta columnMeta = new WorksheetTableMeta.WSColumnMeta();
      columnMeta.setName(columnRef.getAttribute());
      columnMeta.setAlias(columnRef.getAlias());
      columnMeta.setDescription(columnRef.getDescription());

      if(columnRef.getTypeNode() != null) {
         columnMeta.setType(columnRef.getTypeNode().getType());
      }

      if(columnRef.isExpression() &&
         columnRef.getDataRef() instanceof ExpressionRef expressionRef)
      {
         columnMeta.setExpression(expressionRef.getExpression());
      }

      columnMeta.setRefType(columnRef.getRefType());

      return columnMeta;
   }

   /**
    * Create join information from operator
    */
   private JoinTableMeta.JoinInfo createJoinInfo(TableAssemblyOperator.Operator operator, String leftTable, String rightTable) {
      JoinTableMeta.JoinInfo joinInfo = new JoinTableMeta.JoinInfo();

      joinInfo.setLeftTable(leftTable);
      joinInfo.setRightTable(rightTable);
      joinInfo.setJoinType(operator.getName());

      if(operator.getLeftAttribute() != null) {
         joinInfo.setLeftColumn(operator.getLeftAttribute().getAttribute());
      }

      if(operator.getRightAttribute() != null) {
         joinInfo.setRightColumn(operator.getRightAttribute().getAttribute());
      }

      return joinInfo;
   }

   /**
    * Walks a worksheet's table assemblies into a full structural description — columns,
    * source, base tables, joins, conditions, and aggregate — for the wiz worksheet-structure
    * introspection endpoint. Mirrors {@link #getWorksheetMetaData} but captures per-table
    * provenance detail rather than just names/columns.
    */
   public WorksheetStructure getWorksheetStructure(String wsId, XPrincipal principal)
      throws Exception
   {
      if(wsId == null || Tool.isEmptyString(wsId)) {
         throw new Exception("Invalid request.");
      }

      AssetEntry entry = AssetEntry.createAssetEntry(wsId);
      AbstractSheet sheet = assetRepository.getSheet(entry, principal, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new Exception("Worksheet " + wsId + " not found.");
      }

      List<WorksheetStructure.StructureTable> tables = new ArrayList<>();

      for(Assembly assembly : sheet.getAssemblies()) {
         if(!(assembly instanceof TableAssembly tableAssembly)) {
            continue;
         }

         try {
            tables.add(buildStructureTable(tableAssembly));
         }
         catch(Exception ex) {
            // Best-effort: one malformed/unexpected assembly shouldn't fail the whole call.
            WorksheetStructure.StructureTable partial = new WorksheetStructure.StructureTable();
            partial.setName(tableAssembly.getName());
            tables.add(partial);
            log.debug("Failed to fully map worksheet assembly '{}'", tableAssembly.getName(), ex);
         }
      }

      WorksheetStructure structure = new WorksheetStructure();
      structure.setPath(entry.getPath());
      structure.setName(entry.getName());
      structure.setTables(tables);

      WSAssembly primaryAssembly = worksheet.getPrimaryAssembly();

      if(primaryAssembly != null) {
         structure.setPrimaryTable(primaryAssembly.getName());
      }

      return structure;
   }

   private WorksheetStructure.StructureTable buildStructureTable(TableAssembly tableAssembly) {
      WorksheetStructure.StructureTable table = new WorksheetStructure.StructureTable();
      table.setName(tableAssembly.getName());
      table.setTableType(structureTableType(tableAssembly));
      table.setColumns(extractStructureColumns(tableAssembly));
      table.setSource(extractStructureSource(tableAssembly));
      table.setBaseTables(extractStructureBaseTables(tableAssembly));
      table.setJoins(extractStructureJoins(tableAssembly));
      table.setConditions(extractStructureConditions(tableAssembly));
      table.setAggregate(extractStructureAggregate(tableAssembly.getAggregateInfo()));
      return table;
   }

   /**
    * Mirrors {@link WorksheetTableService}'s private {@code getTableType} strings exactly, so
    * the construction and introspection endpoints speak the same vocabulary.
    */
   static String structureTableType(TableAssembly tableAssembly) {
      if(tableAssembly instanceof PhysicalBoundTableAssembly) {
         return "physical table";
      }
      else if(tableAssembly instanceof MirrorTableAssembly) {
         return "mirror table";
      }
      else if(tableAssembly instanceof RelationalJoinTableAssembly) {
         return "relational join table";
      }
      else if(tableAssembly instanceof SQLBoundTableAssembly) {
         return "sql query table";
      }

      return tableAssembly.getClass().getSimpleName();
   }

   static List<WorksheetStructure.Column> extractStructureColumns(TableAssembly tableAssembly) {
      List<WorksheetStructure.Column> columns = new ArrayList<>();
      ColumnSelection selection = tableAssembly.getColumnSelection(true);

      if(selection == null) {
         return columns;
      }

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(ref instanceof ColumnRef columnRef) {
            columns.add(createStructureColumn(columnRef));
         }
      }

      return columns;
   }

   private static WorksheetStructure.Column createStructureColumn(ColumnRef columnRef) {
      WorksheetStructure.Column column = new WorksheetStructure.Column();
      column.setName(columnRef.getAttribute());
      column.setAlias(columnRef.getAlias());
      column.setRefType(columnRef.getRefType());
      // ColumnRef.getDescription() returns "" (not null) when unset; normalize to null so an absent
      // description behaves like alias/expression (null when not applicable), matching createColumnMeta.
      column.setDescription(Tool.isEmptyString(columnRef.getDescription()) ? null : columnRef.getDescription());

      if(columnRef.getTypeNode() != null) {
         column.setType(columnRef.getTypeNode().getType());
      }

      if(columnRef.isExpression() && columnRef.getDataRef() instanceof ExpressionRef expressionRef) {
         column.setExpression(expressionRef.getExpression());
      }

      return column;
   }

   /**
    * Source of a bound/sql table. Returns {@code null} for tables with no direct physical
    * source (mirror/join/rotated/unpivot/embedded), matching {@code StructureTable.source}'s
    * documented "null unless a bound/sql table" contract.
    */
   static WorksheetStructure.SourceRef extractStructureSource(TableAssembly tableAssembly) {
      if(!(tableAssembly instanceof BoundTableAssembly boundTable) ||
         boundTable.getSourceInfo() == null)
      {
         return null;
      }

      SourceInfo sourceInfo = boundTable.getSourceInfo();
      WorksheetStructure.SourceRef source = new WorksheetStructure.SourceRef();
      source.setDatasource(sourceInfo.getPrefix());
      source.setTable(sourceInfo.getSource());
      source.setSourceType(sourceInfo.getType());

      if(tableAssembly instanceof SQLBoundTableAssembly &&
         tableAssembly.getInfo() instanceof SQLBoundTableAssemblyInfo sqlInfo &&
         sqlInfo.getQuery() != null && sqlInfo.getQuery().getSQLDefinition() != null)
      {
         source.setSqlText(sqlInfo.getQuery().getSQLDefinition().getSQLString());
      }

      return source;
   }

   /**
    * Names of the tables a composed assembly (mirror/join/rotated/unpivot) is built from.
    * Reuses the same {@code ComposedTableAssembly.getTableNames()} accessor as
    * {@code WorksheetTableService#getBaseTables}.
    */
   static List<String> extractStructureBaseTables(TableAssembly tableAssembly) {
      if(!(tableAssembly instanceof ComposedTableAssembly composed)) {
         return new ArrayList<>();
      }

      String[] names = composed.getTableNames();
      return names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
   }

   static List<WorksheetStructure.JoinEdge> extractStructureJoins(TableAssembly tableAssembly) {
      List<WorksheetStructure.JoinEdge> joins = new ArrayList<>();

      if(!(tableAssembly instanceof AbstractJoinTableAssembly joinTable)) {
         return joins;
      }

      Enumeration<TableAssemblyOperator> operators = joinTable.getOperators();

      if(operators == null) {
         return joins;
      }

      while(operators.hasMoreElements()) {
         TableAssemblyOperator operator = operators.nextElement();

         if(operator == null) {
            continue;
         }

         for(TableAssemblyOperator.Operator operatorOperator : operator.getOperators()) {
            if(operatorOperator == null || operatorOperator.getLeftTable() == null ||
               operatorOperator.getRightTable() == null)
            {
               continue;
            }

            WorksheetStructure.JoinEdge join = new WorksheetStructure.JoinEdge();
            join.setLeftTable(operatorOperator.getLeftTable());
            join.setRightTable(operatorOperator.getRightTable());

            if(operatorOperator.getLeftAttribute() != null) {
               join.setLeftColumn(operatorOperator.getLeftAttribute().getAttribute());
            }

            if(operatorOperator.getRightAttribute() != null) {
               join.setRightColumn(operatorOperator.getRightAttribute().getAttribute());
            }

            // Same accessor createJoinInfo() uses for its JoinInfo.joinType (operator display
            // name, e.g. "Inner Join"/"Left Outer Join").
            join.setJoinType(operatorOperator.getName());
            joins.add(join);
         }
      }

      return joins;
   }

   static List<WorksheetStructure.ConditionLeaf> extractStructureConditions(
      TableAssembly tableAssembly)
   {
      List<WorksheetStructure.ConditionLeaf> conditions = new ArrayList<>();
      collectStructureConditions(tableAssembly.getPreConditionList(), "pre", conditions);
      collectStructureConditions(
         tableAssembly.getPreRuntimeConditionList(), "preRuntime", conditions);
      collectStructureConditions(tableAssembly.getPostConditionList(), "post", conditions);
      collectStructureConditions(tableAssembly.getRankingConditionList(), "ranking", conditions);
      return conditions;
   }

   /**
    * Walks one {@link ConditionListWrapper}'s alternating ConditionItem/JunctionOperator list.
    * Non-{@link Condition} {@link XCondition} implementations (e.g. correlated/asset conditions)
    * still yield a leaf with field/operation/negated/phase — just no values, since
    * {@code getValueCount()}/{@code getValue(int)} aren't declared on the {@code XCondition}
    * interface itself.
    */
   static void collectStructureConditions(ConditionListWrapper wrapper, String phase,
                                          List<WorksheetStructure.ConditionLeaf> out)
   {
      if(wrapper == null || wrapper.getConditionList() == null) {
         return;
      }

      ConditionList list = wrapper.getConditionList();

      for(int i = 0; i < list.getSize(); i++) {
         if(!list.isConditionItem(i)) {
            continue;
         }

         ConditionItem item = list.getConditionItem(i);
         XCondition xCondition = item.getXCondition();

         if(xCondition == null) {
            continue;
         }

         WorksheetStructure.ConditionLeaf leaf = new WorksheetStructure.ConditionLeaf();
         leaf.setField(item.getAttribute() != null ? item.getAttribute().getAttribute() : null);
         leaf.setOperation(structureOperationName(xCondition.getOperation()));
         leaf.setNegated(xCondition.isNegated());
         leaf.setPhase(phase);

         // The junction PRECEDING this item (joining it to the previous leaf) sits at i - 1;
         // the first leaf in a phase has none.
         if(i > 0 && list.isJunctionOperator(i - 1)) {
            leaf.setJunction(list.getJunction(i - 1) == JunctionOperator.OR ? "OR" : "AND");
         }

         if(xCondition instanceof Condition condition) {
            List<String> values = new ArrayList<>();

            for(int v = 0; v < condition.getValueCount(); v++) {
               Object value = condition.getValue(v);

               // Only stringify plain scalar values; skip parameter/session/sub-query objects
               // (e.g. UserVariable, DataRef) rather than dumping an opaque toString().
               if(value instanceof String || value instanceof Number ||
                  value instanceof Boolean || value instanceof java.util.Date)
               {
                  values.add(String.valueOf(value));
               }
            }

            leaf.setValues(values);
         }

         out.add(leaf);
      }
   }

   /**
    * No StyleBI helper converts an {@link XCondition} operation int to a name (confirmed: no
    * {@code getOperationName} exists anywhere in this codebase; {@link Condition#toString()}
    * inlines a private, localized switch). Uses the {@link XCondition} constant names
    * themselves so the vocabulary is stable and self-documenting for API consumers.
    */
   private static String structureOperationName(int operation) {
      return switch(operation) {
         case XCondition.EQUAL_TO -> "EQUAL_TO";
         case XCondition.ONE_OF -> "ONE_OF";
         case XCondition.LESS_THAN -> "LESS_THAN";
         case XCondition.GREATER_THAN -> "GREATER_THAN";
         case XCondition.BETWEEN -> "BETWEEN";
         case XCondition.STARTING_WITH -> "STARTING_WITH";
         case XCondition.CONTAINS -> "CONTAINS";
         case XCondition.NULL -> "NULL";
         case XCondition.TOP_N -> "TOP_N";
         case XCondition.BOTTOM_N -> "BOTTOM_N";
         case XCondition.DATE_IN -> "DATE_IN";
         case XCondition.PSEUDO -> "PSEUDO";
         case XCondition.LIKE -> "LIKE";
         default -> "UNKNOWN(" + operation + ")";
      };
   }

   /**
    * Uses {@link WizDateLevelUtil#getDateGroupLevelName(int)} (package-private in this same
    * package) for date-level names, so the introspection endpoint reports the same vocabulary
    * as the rest of the wiz service layer.
    */
   static WorksheetStructure.AggregateSummary extractStructureAggregate(
      AggregateInfo aggregateInfo)
   {
      if(aggregateInfo == null || aggregateInfo.isEmpty()) {
         return null;
      }

      WorksheetStructure.AggregateSummary summary = new WorksheetStructure.AggregateSummary();

      for(GroupRef groupRef : aggregateInfo.getGroups()) {
         WorksheetStructure.AggGroupBy groupBy = new WorksheetStructure.AggGroupBy();
         groupBy.setField(groupRef.getName());
         groupBy.setDateLevel(WizDateLevelUtil.getDateGroupLevelName(groupRef.getDateGroup()));
         summary.getGroupBy().add(groupBy);
      }

      for(AggregateRef aggregateRef : aggregateInfo.getAggregates()) {
         WorksheetStructure.AggMeasure measure = new WorksheetStructure.AggMeasure();
         measure.setField(aggregateRef.getName());
         measure.setFormula(
            aggregateRef.getFormula() != null ? aggregateRef.getFormula().getName() : null);

         if(aggregateRef.getSecondaryColumn() != null) {
            measure.setSecondaryField(aggregateRef.getSecondaryColumn().getName());
         }

         if(aggregateRef.getN() > 0) {
            measure.setN(String.valueOf(aggregateRef.getN()));
         }

         summary.getAggregates().add(measure);
      }

      return summary;
   }

   /**
    * Gets all tables and their FK relationships for the specified datasource.
    *
    * @param dsName    the datasource name/path.
    * @param principal the current user.
    * @return tables with catalog/schema/type, plus foreign key relationships.
    */
   public DatasourceTablesResponse getDatabaseTables(String dsName, Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, principal)) {
         throw new SecurityException("Access denied to data source: " + dsName);
      }

      // getJDBCDatasource throws clearly if the source doesn't exist or isn't JDBC; call it
      // first so we fail fast before the more expensive metadata connection is opened.
      JDBCDataSource jdbcDataSource = getJDBCDatasource(dsName);
      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(dsName);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + dsName);
      }

      // Partition probe: identify Postgres partition/inheritance children up-front
      // so we can drop them from the response (they're implementation detail,
      // not user-facing tables). On any probe error we fall open — log a warning
      // and proceed with an empty set, so a transient catalog issue doesn't block
      // the whole metadata fetch.
      Set<String> partitionChildren = Set.of();
      if(isPostgresDriver(jdbcDataSource)) {
         try(Connection probeConn = new JDBCHandler().getConnection(jdbcDataSource, principal)) {
            partitionChildren = findPostgresPartitionChildren(jdbcDataSource, probeConn);
         }
         catch(Exception e) {
            log.warn("Partition probe failed for '{}'; proceeding without partition filter", dsName, e);
         }
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      XNode typeQuery = new XNode();
      typeQuery.setAttribute("type", "TABLETYPES");
      XNode tableTypeList = metaDataProvider.getMetaData(typeQuery, true);

      XNode schemaQuery = new XNode();
      schemaQuery.setAttribute("type", "SCHEMAS");
      XNode schemaList = metaDataProvider.getMetaData(schemaQuery, true);
      schemaList = filterSystemSchemaTree(schemaList, jdbcDataSource);

      // If the database has no schema hierarchy, schemaList itself becomes the single leaf node
      // (with no schema attributes), which causes the table query to run without a schema filter.
      List<XNode> schemaNodes = new ArrayList<>();
      collectLeafNodes(schemaList, schemaNodes);

      List<DatabaseTableInfo> tables = new ArrayList<>();

      for(XNode schemaNode : schemaNodes) {
         for(int i = 0; i < tableTypeList.getChildCount(); i++) {
            XNode typeNode = tableTypeList.getChild(i);
            String tableType = typeNode.getName();

            if(!SUPPORTED_TABLE_TYPES.contains(tableType)) {
               continue;
            }

            XNode tableQuery = new XNode();
            tableQuery.setAttribute("type", "SCHEMATABLES_" + tableType);
            tableQuery.setAttribute("tableType", tableType);
            copySchemaAttribute(schemaNode, tableQuery, "supportCatalog");
            copySchemaAttribute(schemaNode, tableQuery, "catalog");
            copySchemaAttribute(schemaNode, tableQuery, "catalogSep");
            copySchemaAttribute(schemaNode, tableQuery, "schema");

            XNode tableList = metaDataProvider.getMetaData(tableQuery, true);

            for(int j = 0; j < tableList.getChildCount(); j++) {
               XNode tableNode = tableList.getChild(j);
               String tableName = tableNode.getName();
               String catalog = (String) tableNode.getAttribute("catalog");
               String schema = (String) tableNode.getAttribute("schema");

               if(isPartitionChild(schema, tableName, partitionChildren)) {
                  continue;
               }

               DatabaseTableInfo info = new DatabaseTableInfo();
               info.setType(tableType);
               info.setDatabase(dsName);
               info.setCatalog(catalog);
               info.setSchema(schema);
               info.setTable(tableName);
               info.setAssetData(buildTableAssetEntry(
                  jdbcDataSource, tableNode, dsName, catalog, schema, tableName, rootMetaData));
               tables.add(info);
            }
         }
      }

      List<OsiRelationship> relationships = buildRelationships(metaDataProvider, tables);

      DatasourceTablesResponse response = new DatasourceTablesResponse();
      response.setTables(tables);
      response.setRelationships(relationships);
      return response;
   }

   private TreeNodeModel filterWizTree(TreeNodeModel node) {
      return filterWizTree(node, new HashMap<>(), new HashMap<>());
   }

   private TreeNodeModel filterWizTree(
      TreeNodeModel node,
      Map<String, SystemFilter> filterCache,
      Map<String, Set<String>> partitionCache)
   {
      if(node == null) {
         return null;
      }

      AssetEntry entry = node.data() instanceof AssetEntry assetEntry ? assetEntry : null;

      if(shouldHideWizTreeNode(entry, filterCache, partitionCache)) {
         return null;
      }

      TreeNodeModel.Builder builder = TreeNodeModel.builder().from(node);
      builder.children(new ArrayList<>());

      for(TreeNodeModel child : node.children()) {
         TreeNodeModel filteredChild = filterWizTree(child, filterCache, partitionCache);

         if(filteredChild != null) {
            builder.addChildren(filteredChild);
         }
      }

      return builder.build();
   }

   private boolean shouldHideWizTreeNode(
      AssetEntry entry,
      Map<String, SystemFilter> filterCache,
      Map<String, Set<String>> partitionCache)
   {
      if(entry == null || Tool.isEmptyString(entry.getPath())) {
         return false;
      }

      String[] parts = entry.getPath().split("/");

      if(parts.length < 3) {
         return false;
      }

      String tableType = parts[1];

      if(!"TABLE".equalsIgnoreCase(tableType) && !"VIEW".equalsIgnoreCase(tableType)) {
         return false;
      }

      String dsName = parts[0];

      // Partition-child check (Postgres only): TABLE leaves whose qualified name
      // appears in the per-datasource partition set are dropped. Cached lazily
      // because the asset tree can include many tables per datasource.
      if("TABLE".equalsIgnoreCase(tableType)) {
         Set<String> partitions = partitionCache.computeIfAbsent(
            dsName, this::probePartitionsForTree);
         if(isPartitionChild(entry.getPath(), partitions)) {
            return true;
         }
      }

      SystemFilter filter = getSystemFilter(dsName, filterCache);

      if(filter.isEmpty()) {
         return false;
      }

      String catalogOrSchema = parts[2];

      if(matchesSystemName(catalogOrSchema, filter.catalogs) ||
         matchesSystemName(catalogOrSchema, filter.schemas))
      {
         return true;
      }

      return parts.length > 3 && matchesSystemName(parts[3], filter.schemas);
   }

   /**
    * Lazy partition probe for the asset-tree filter. Fail-open: returns empty
    * set on any error so the tree still renders, mirroring getDatabaseTables.
    */
   private Set<String> probePartitionsForTree(String dsName) {
      try {
         JDBCDataSource ds = getJDBCDatasource(dsName);
         if(!isPostgresDriver(ds)) {
            return Set.of();
         }
         // Unlike getDatabaseTables, there's no Principal parameter here: this runs
         // inside a computeIfAbsent lambda during the tree walk, so we resolve the
         // caller from the request-scoped ThreadContext instead.
         try(Connection conn = new JDBCHandler()
               .getConnection(ds, ThreadContext.getContextPrincipal())) {
            return findPostgresPartitionChildren(ds, conn);
         }
      }
      catch(Exception e) {
         log.warn("Partition probe failed for asset tree '{}'; tree will include partitions", dsName, e);
         return Set.of();
      }
   }

   private XNode filterSystemSchemaTree(XNode schemas, JDBCDataSource jdbcDataSource) {
      Set<String> systemCatalogs = getSystemNameSet(jdbcDataSource.getSystemCatalogs());
      Set<String> systemSchemas = getSystemNameSet(jdbcDataSource.getSystemSchemas());

      if(systemCatalogs.isEmpty() && systemSchemas.isEmpty()) {
         return schemas;
      }

      XNode filtered = cloneNodeWithoutChildren(schemas);

      for(int i = 0; i < schemas.getChildCount(); i++) {
         XNode child = filterSystemSchemaNode(schemas.getChild(i), systemCatalogs, systemSchemas);

         if(child != null) {
            filtered.addChild(child, true, false);
         }
      }

      return filtered;
   }

   private XNode filterSystemSchemaNode(XNode node, Set<String> systemCatalogs,
                                        Set<String> systemSchemas)
   {
      String catalogName = (String) node.getAttribute("catalog");
      String schemaName = (String) node.getAttribute("schema");
      String nodeName = node.getName();

      if(matchesSystemName(catalogName, systemCatalogs) ||
         matchesSystemName(schemaName, systemSchemas) ||
         matchesSystemName(nodeName, systemCatalogs) ||
         matchesSystemName(nodeName, systemSchemas))
      {
         return null;
      }

      XNode filtered = cloneNodeWithoutChildren(node);

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = filterSystemSchemaNode(node.getChild(i), systemCatalogs, systemSchemas);

         if(child != null) {
            filtered.addChild(child, true, false);
         }
      }

      return filtered;
   }

   private XNode cloneNodeWithoutChildren(XNode node) {
      XNode copy = new XNode(node.getName());
      Enumeration<String> attrNames = node.getAttributeNames();

      while(attrNames.hasMoreElements()) {
         String attrName = attrNames.nextElement();
         copy.setAttribute(attrName, node.getAttribute(attrName));
      }

      return copy;
   }

   private Set<String> getSystemNameSet(String[] values) {
      Set<String> result = new HashSet<>();

      if(values != null) {
         for(String value : values) {
            if(!Tool.isEmptyString(value)) {
               result.add(value.toUpperCase(Locale.ROOT));
            }
         }
      }

      return result;
   }

   private String[] getJdbcSystemCatalogs(String dsName) {
      try {
         return getJDBCDatasource(dsName).getSystemCatalogs();
      }
      catch(Exception e) {
         log.debug("Failed to get system catalogs for '{}'", dsName, e);
         return new String[0];
      }
   }

   private String[] getJdbcSystemSchemas(String dsName) {
      try {
         return getJDBCDatasource(dsName).getSystemSchemas();
      }
      catch(Exception e) {
         log.debug("Failed to get system schemas for '{}'", dsName, e);
         return new String[0];
      }
   }

   private boolean matchesSystemName(String name, Set<String> systemNames) {
      if(Tool.isEmptyString(name)) {
         return false;
      }

      String upper = name.toUpperCase(Locale.ROOT);

      for(String pattern : systemNames) {
         if(pattern.endsWith("*")) {
            if(upper.startsWith(pattern.substring(0, pattern.length() - 1))) {
               return true;
            }
         }
         else if(upper.equals(pattern)) {
            return true;
         }
      }

      return false;
   }

   private SystemFilter getSystemFilter(String dsName, Map<String, SystemFilter> filterCache) {
      return filterCache.computeIfAbsent(dsName, key -> new SystemFilter(
         getSystemNameSet(getJdbcSystemCatalogs(key)),
         getSystemNameSet(getJdbcSystemSchemas(key))));
   }

   private static final class SystemFilter {
      private final Set<String> catalogs;
      private final Set<String> schemas;

      private SystemFilter(Set<String> catalogs, Set<String> schemas) {
         this.catalogs = catalogs;
         this.schemas = schemas;
      }

      private boolean isEmpty() {
         return catalogs.isEmpty() && schemas.isEmpty();
      }
   }

   /**
    * Builds a PHYSICAL_TABLE AssetEntry from the given table node, mirroring the pattern
    * used in QueryManagerService when adding selected tables to a runtime query.
    */
   private AssetEntry buildTableAssetEntry(JDBCDataSource jdbcDataSource, XNode tableNode,
                                            String dsName, String catalog, String schema,
                                            String tableName, XNode rootMetaData)
   {
      String tablePath = JDBCUtil.getTablePath(jdbcDataSource, tableNode);
      String pathArray = buildTablePathArray(
         dsName, tableNode.getAttribute("type"), catalog, schema, tableName);

      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE, tablePath, null);
      entry.setProperty("source", tableName);
      entry.setProperty("prefix", dsName);
      entry.setProperty(AssetEntry.PATH_ARRAY, pathArray);
      entry.setProperty(XSourceInfo.CATALOG, catalog);
      entry.setProperty(XSourceInfo.SCHEMA, schema);
      entry.setProperty("source_with_no_quote", tableName);

      if(rootMetaData != null) {
         entry.setProperty("hasSchema", Tool.toString(rootMetaData.getAttribute("hasSchema")));
         entry.setProperty("defaultSchema", Tool.toString(rootMetaData.getAttribute("defaultSchema")));
         entry.setProperty("supportCatalog", Tool.toString(rootMetaData.getAttribute("supportCatalog")));
         entry.setProperty("hasCatalog", Tool.toString(rootMetaData.getAttribute("hasCatalog")));
      }

      return entry;
   }

   private String buildTablePathArray(String datasource, Object type, Object catalog,
                                       Object schema, String tableName)
   {
      StringBuilder path = new StringBuilder()
         .append(datasource).append(AssetEntry.PATH_ARRAY_SEPARATOR)
         .append(type).append(AssetEntry.PATH_ARRAY_SEPARATOR);

      if(catalog != null) {
         path.append(catalog).append(AssetEntry.PATH_ARRAY_SEPARATOR);
      }

      if(schema != null) {
         path.append(schema).append(AssetEntry.PATH_ARRAY_SEPARATOR);
      }

      path.append(tableName);
      return path.toString();
   }

   /**
    * Queries KEYRELATION for each table and builds OSI Relationship objects from foreign key info.
    * Rows belonging to the same FK constraint (FK table, PK table, and FK constraint name) are
    * grouped together so that composite keys are represented as multi-column relationships.
    * When the driver does not populate FK_NAME (fkName is empty), rows are grouped by table pair
    * only — multiple unnamed constraints between the same pair will be merged as a known limitation.
    * System-generated constraint names (e.g. SYS_C007890, FK__orders__cust__3A81B327) are used
    * as-is since they faithfully reflect what the database reports.
    */
   private List<OsiRelationship> buildRelationships(DefaultMetaDataProvider metaDataProvider,
                                                     List<DatabaseTableInfo> tables)
   {
      // LinkedHashMap preserves insertion order.
      // Key is a List<String> of [fkTable, pkTable, pkCatalog, pkSchema, fkName] — avoids delimiter
      // collision that would occur with string concatenation when table names contain separators.
      Map<List<String>, OsiRelationship> relMap = new LinkedHashMap<>();

      for(DatabaseTableInfo table : tables) {
         XNode query = new XNode(table.getTable());
         query.setAttribute("type", "KEYRELATION");

         if(table.getCatalog() != null) {
            query.setAttribute("catalog", table.getCatalog());
         }

         if(table.getSchema() != null) {
            query.setAttribute("schema", table.getSchema());
         }

         XNode keyRelResult;

         try {
            keyRelResult = metaDataProvider.getMetaData(query, true);
         }
         catch(Exception e) {
            log.warn("Failed to get key relationships for table '{}'", table.getTable(), e);
            continue;
         }

         for(int i = 0; i < keyRelResult.getChildCount(); i++) {
            XNode keyNode = keyRelResult.getChild(i);
            String pkTableName = (String) keyNode.getAttribute("pkTableName");
            String pkColumnName = (String) keyNode.getAttribute("pkColumnName");
            String fkTableName = (String) keyNode.getAttribute("fkTableName");
            String fkColumnName = (String) keyNode.getAttribute("fkColumnName");

            if(Tool.isEmptyString(pkTableName) || Tool.isEmptyString(fkTableName)) {
               continue;
            }

            String fkName = Tool.defaultIfNull((String) keyNode.getAttribute("fkName"), "");

            // Group rows by (fkTable, pkTable, pkCatalog, pkSchema, fkName) to handle composite FK
            // constraints while keeping separate constraints between the same table pair distinct.
            List<String> relKey = List.of(
               fkTableName,
               pkTableName,
               Tool.defaultIfNull((String) keyNode.getAttribute("pkTableCat"), ""),
               Tool.defaultIfNull((String) keyNode.getAttribute("pkTableSchem"), ""),
               fkName);

            OsiRelationship rel = relMap.computeIfAbsent(relKey, k -> {
               OsiRelationship r = new OsiRelationship();
               r.setName(!fkName.isEmpty() ? fkName : fkTableName + "_" + pkTableName + "_fk");
               r.setFrom(fkTableName);
               r.setTo(pkTableName);
               r.setFromColumns(new ArrayList<>());
               r.setToColumns(new ArrayList<>());
               return r;
            });

            if(!Tool.isEmptyString(fkColumnName) && !rel.getFromColumns().contains(fkColumnName)) {
               rel.getFromColumns().add(fkColumnName);
            }

            if(!Tool.isEmptyString(pkColumnName) && !rel.getToColumns().contains(pkColumnName)) {
               rel.getToColumns().add(pkColumnName);
            }
         }
      }

      return new ArrayList<>(relMap.values());
   }

   private void collectLeafNodes(XNode node, List<XNode> leaves) {
      if(node.getChildCount() == 0) {
         leaves.add(node);
      }
      else {
         for(int i = 0; i < node.getChildCount(); i++) {
            collectLeafNodes(node.getChild(i), leaves);
         }
      }
   }

   private void copySchemaAttribute(XNode from, XNode to, String attribute) {
      Object value = from.getAttribute(attribute);

      if(value != null) {
         to.setAttribute(attribute, value);
      }
   }

   private DefaultMetaDataProvider getMetaDataProvider(String database) {
      try {
         JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
         return getMetaDataProvider(dataSource);
      }
      catch(Exception e) {
         log.warn("Failed to get meta data provider for data source '{}'", database, e);
         return null;
      }
   }

   private DefaultMetaDataProvider getMetaDataProvider(JDBCDataSource jdbcDatasource) {
      try {
         XDataModel dataModel = dataSourceService.getDataModel(jdbcDatasource.getFullName());
         return dataSourceService.getDefaultMetaDataProvider(jdbcDatasource, dataModel);
      }
      catch(Exception e) {
         log.warn("Failed to get meta data provider for data source '{}'", jdbcDatasource.getFullName(), e);
         return null;
      }
   }

   /**
    * Returns true when the datasource uses the Postgres JDBC driver. Package-private
    * (not private) so the partition filter unit tests can call it without standing
    * up the full service graph.
    */
   static boolean isPostgresDriver(JDBCDataSource ds) {
      if(ds == null) {
         return false;
      }
      String driver = ds.getDriver();
      return "org.postgresql.Driver".equals(driver);
   }

   /**
    * Authoritative partition-children probe for Postgres. Queries pg_inherits
    * (inheritance children, including pre-PG10 inheritance-based partitioning AND
    * PG10+ declarative partitioning). Returns lowercased "schema.table" keys.
    *
    * Package-private static for direct unit testing — the only side effect besides
    * returning is consuming the supplied Connection.
    *
    * On error: this method does NOT swallow exceptions. Callers must catch
    * SQLException and decide whether to log and continue with an empty filter
    * (current policy: leak partitions through rather than block the whole metadata
    * request).
    */
   static Set<String> findPostgresPartitionChildren(
      JDBCDataSource ds, Connection conn) throws SQLException
   {
      if(!isPostgresDriver(ds)) {
         return Set.of();
      }

      // pg_inherits is the implementation mechanism for both pre-PG10 inheritance-
      // based partitioning AND PG10+ declarative partitioning, so this single
      // subquery covers both eras without referencing pg_class.relispartition
      // (PG10+ only — would break catalog queries on older Postgres).
      // relkind IN ('r','p') restricts to ordinary and partitioned tables.
      // pg_inherits also records partitioned-index inheritance (PG11+), so without
      // this filter index oids would leak in as "schema.indexname" keys.
      String sql =
         "SELECT n.nspname || '.' || c.relname AS qualified " +
         "FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid " +
         "WHERE c.relkind IN ('r', 'p') " +
         "AND c.oid IN (SELECT inhrelid FROM pg_inherits)";

      Set<String> out = new HashSet<>();

      try(PreparedStatement ps = conn.prepareStatement(sql);
          ResultSet rs = ps.executeQuery())
      {
         while(rs.next()) {
            String qualified = rs.getString(1);
            if(qualified != null) {
               // Locale.ROOT avoids Turkish-locale "I" → dotless-ı conversion, which
               // would produce keys that don't match the comparison side downstream.
               out.add(qualified.toLowerCase(Locale.ROOT));
            }
         }
      }

      return Set.copyOf(out);
   }

   /**
    * True when (schema, tableName) refers to a row in the partition-children set.
    * Used by getDatabaseTables, which already has schema and table broken out.
    */
   static boolean isPartitionChild(String schema, String tableName, Set<String> partitions) {
      if(partitions == null || partitions.isEmpty() || tableName == null) {
         return false;
      }
      // Locale.ROOT matches the lowercasing convention used in findPostgresPartitionChildren.
      String qualified = ((schema == null ? "" : schema + ".") + tableName).toLowerCase(Locale.ROOT);
      return partitions.contains(qualified);
   }

   /**
    * True when a wiz asset path ("dsName/TABLE/schema/tableName") refers to a
    * partition child. Used by filterWizTree, which only has the path string.
    * Returns false for paths that aren't TABLE leaves (VIEW, folder paths, etc.)
    * so the predicate is safe to call on any node.
    */
   static boolean isPartitionChild(String assetPath, Set<String> partitions) {
      if(assetPath == null || assetPath.isEmpty() || partitions == null || partitions.isEmpty()) {
         return false;
      }
      String[] parts = assetPath.split("/");
      // Need at minimum: dsName/TABLE/schema/tableName
      if(parts.length < 4) {
         return false;
      }
      if(!"TABLE".equalsIgnoreCase(parts[1])) {
         return false;
      }
      return isPartitionChild(parts[2], parts[3], partitions);
   }

   /**
    * Gets column metadata for a specific table in a datasource.
    *
    * @param dsName    the datasource name.
    * @param tableName the fully qualified table name (may include catalog.schema.table).
    * @param catalog   optional catalog name.
    * @param schema    optional schema name.
    * @param principal the current user.
    * @return column metadata for the table.
    */
   public DatabaseTableMeta getTableDetails(String dsName, String tableName,
                                            String catalog, String schema,
                                            Principal principal)
      throws Exception
   {
      if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, principal)) {
         throw new SecurityException("Access denied to data source: " + dsName);
      }

      JDBCDataSource jdbcDataSource = getJDBCDatasource(dsName);
      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(dsName);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + dsName);
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(rootMetaData == null) {
         throw new Exception("No meta data found for data source " + dsName);
      }

      SQLTypes sqlTypes = SQLTypes.getSQLTypes(jdbcDataSource);
      XNode node = sqlTypes.getQualifiedTableNode(tableName,
         "true".equals(rootMetaData.getAttribute("hasCatalog")),
         "true".equals(rootMetaData.getAttribute("hasSchema")),
         (String) rootMetaData.getAttribute("catalogSep"), jdbcDataSource,
         catalog, schema);

      if(rootMetaData.getAttribute("supportCatalog") != null) {
         node.setAttribute("supportCatalog", rootMetaData.getAttribute("supportCatalog"));
      }

      XNode tableData = metaDataProvider.getMetaData(node, true);
      XNode tableNode = tableData != null ? tableData.getChild(0) : null;

      if(tableNode == null) {
         throw new Exception(
            Tool.buildString("Table ", tableName, " not found in data source ", dsName));
      }

      DatabaseTableMeta meta = new DatabaseTableMeta();
      meta.setName(tableName);
      meta.setCatalog(Tool.isEmptyString(catalog) ? null : catalog);
      meta.setSchema(Tool.isEmptyString(schema) ? null : schema);

      List<DatabaseTableMeta.ColumnMeta> columns = new ArrayList<>();

      for(int i = 0; i < tableNode.getChildCount(); i++) {
         XNode columnNode = tableNode.getChild(i);

         if(columnNode == null) {
            continue;
         }

         DatabaseTableMeta.ColumnMeta col = new DatabaseTableMeta.ColumnMeta();
         col.setName(columnNode.getName());
         col.setType(columnNode instanceof XTypeNode typeNode ? typeNode.getType() : null);
         col.setPrimaryKey("true".equals(columnNode.getAttribute("PrimaryKey")));

         Integer length = (Integer) columnNode.getAttribute("length");
         col.setLength(length != null ? length : 0);

         String comment = (String) columnNode.getAttribute("comment");
         col.setComment(Tool.isEmptyString(comment) ? null : comment);

         List<String[]> foreignKeys = extractForeignKeys(columnNode);
         col.setForeignKeys(foreignKeys.isEmpty() ? null : foreignKeys);

         columns.add(col);
      }

      meta.setColumns(columns);
      return meta;
   }

   /**
    * Searches for tables and columns matching a keyword across all datasources.
    *
    * @param request   the search request containing query and optional field names.
    * @param principal the current user.
    * @return matching tables with their matched columns.
    */
   public SchemaSearchResponse searchSchema(SchemaSearchRequest request, Principal principal)
      throws Exception
   {
      String query = request.getQuery();

      if(Tool.isEmptyString(query)) {
         SchemaSearchResponse response = new SchemaSearchResponse();
         response.setResults(Collections.emptyList());
         return response;
      }

      String queryLower = query.toLowerCase(Locale.ROOT);
      List<String> fields = request.getFields();
      boolean searchColumns = fields != null && !fields.isEmpty();
      Set<String> fieldNamesLower = new HashSet<>();

      if(searchColumns) {
         for(String f : fields) {
            fieldNamesLower.add(f.toLowerCase(Locale.ROOT));
         }
      }

      String[] dsNames = xrepository.getDataSourceFullNames();
      List<SchemaSearchResponse.SchemaSearchResult> results = new ArrayList<>();

      for(String dsName : dsNames) {
         try {
            if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, principal)) {
               continue;
            }

            XDataSource dataSource = xrepository.getDataSource(dsName);

            if(!(dataSource instanceof JDBCDataSource)) {
               continue;
            }

            DatasourceTablesResponse tablesResponse = getDatabaseTables(dsName, principal);

            for(DatabaseTableInfo tableInfo : tablesResponse.getTables()) {
               boolean tableMatches = tableInfo.getTable().toLowerCase(Locale.ROOT)
                  .contains(queryLower);

               // If searching by field names, look for column matches
               List<SchemaSearchResponse.ColumnMatch> columnMatches = null;

               if(searchColumns) {
                  columnMatches = findColumnMatches(
                     dsName, tableInfo, fieldNamesLower, principal);
               }

               // Include table if its name matches the query, or if any requested columns match
               if(tableMatches || (columnMatches != null && !columnMatches.isEmpty())) {
                  SchemaSearchResponse.SchemaSearchResult result =
                     new SchemaSearchResponse.SchemaSearchResult();
                  result.setDatasource(dsName);
                  result.setCatalog(tableInfo.getCatalog());
                  result.setSchema(tableInfo.getSchema());
                  result.setTable(tableInfo.getTable());
                  result.setType(tableInfo.getType());
                  result.setMatchedColumns(columnMatches);
                  results.add(result);
               }

               if(results.size() >= MAX_SEARCH_RESULTS) {
                  break;
               }
            }
         }
         catch(Exception e) {
            log.debug("Skipping datasource '{}' during schema search: {}", dsName, e.getMessage());
         }

         if(results.size() >= MAX_SEARCH_RESULTS) {
            break;
         }
      }

      SchemaSearchResponse response = new SchemaSearchResponse();
      response.setResults(results);
      return response;
   }

   /**
    * Finds columns in a table that match the given field name set.
    */
   private List<SchemaSearchResponse.ColumnMatch> findColumnMatches(
      String dsName, DatabaseTableInfo tableInfo, Set<String> fieldNamesLower,
      Principal principal)
   {
      try {
         DatabaseTableMeta meta = getTableDetails(
            dsName, tableInfo.getTable(), tableInfo.getCatalog(),
            tableInfo.getSchema(), principal);

         List<SchemaSearchResponse.ColumnMatch> matches = new ArrayList<>();

         for(DatabaseTableMeta.ColumnMeta col : meta.getColumns()) {
            String colNameLower = col.getName().toLowerCase(Locale.ROOT);

            for(String fieldName : fieldNamesLower) {
               if(colNameLower.contains(fieldName) || fieldName.contains(colNameLower)) {
                  SchemaSearchResponse.ColumnMatch match = new SchemaSearchResponse.ColumnMatch();
                  match.setName(col.getName());
                  match.setType(col.getType());
                  matches.add(match);
                  break;
               }
            }
         }

         return matches.isEmpty() ? null : matches;
      }
      catch(Exception e) {
         log.debug("Failed to get columns for table '{}' in '{}': {}",
            tableInfo.getTable(), dsName, e.getMessage());
         return null;
      }
   }

   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
   private final AssetRepository assetRepository;
   private final AssetTreeService assetTreeService;
   private final ObjectMapper objectMapper;
   // Only TABLE and VIEW are meaningful for data modelling; other types (PROCEDURE, SYNONYM,
   // ALIAS, GLOBAL TEMPORARY, etc.) are excluded.
   private static final Set<String> SUPPORTED_TABLE_TYPES = Set.of("TABLE", "VIEW");
   private static final int MAX_SEARCH_RESULTS = 100;
   private static final Logger log = LoggerFactory.getLogger(MetadataApiService.class);
}
