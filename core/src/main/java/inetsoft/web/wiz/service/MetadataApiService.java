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
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.composer.model.LoadAssetTreeNodesEvent;
import inetsoft.web.composer.model.LoadAssetTreeNodesValidator;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
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

   public OsiDataset getMetaData(GetDatabaseTableMetaRequest data) throws Exception {
      String dsName = data.getDsName();
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
      dataset.setCustomExtensions(List.of(buildDatasetExtension(dsName, catalog, schema, source)));

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
                                                     String schema, String path)
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
      return filterWizTree(node, new HashMap<>());
   }

   private TreeNodeModel filterWizTree(TreeNodeModel node, Map<String, SystemFilter> filterCache) {
      if(node == null) {
         return null;
      }

      AssetEntry entry = node.data() instanceof AssetEntry assetEntry ? assetEntry : null;

      if(shouldHideWizTreeNode(entry, filterCache)) {
         return null;
      }

      TreeNodeModel.Builder builder = TreeNodeModel.builder().from(node);
      builder.children(new ArrayList<>());

      for(TreeNodeModel child : node.children()) {
         TreeNodeModel filteredChild = filterWizTree(child, filterCache);

         if(filteredChild != null) {
            builder.addChildren(filteredChild);
         }
      }

      return builder.build();
   }

   private boolean shouldHideWizTreeNode(AssetEntry entry, Map<String, SystemFilter> filterCache) {
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

      SystemFilter filter = getSystemFilter(parts[0], filterCache);

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
      return !Tool.isEmptyString(name) && systemNames.contains(name.toUpperCase(Locale.ROOT));
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

   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
   private final AssetRepository assetRepository;
   private final AssetTreeService assetTreeService;
   private final ObjectMapper objectMapper;
   // Only TABLE and VIEW are meaningful for data modelling; other types (PROCEDURE, SYNONYM,
   // ALIAS, GLOBAL TEMPORARY, etc.) are excluded.
   private static final Set<String> SUPPORTED_TABLE_TYPES = Set.of("TABLE", "VIEW");
   private static final Logger log = LoggerFactory.getLogger(MetadataApiService.class);
}
