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
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.composer.model.LoadAssetTreeNodesEvent;
import inetsoft.web.composer.model.LoadAssetTreeNodesValidator;
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
      return assetTreeService.getNodes(true, false, true,
                                       false, false, false,
                                       false, false,
                                       true, true, event, principal);
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

      XNode tableMetaData = getTableMetaData(jdbcDataSource, data.getCatalog(), data.getSchema(), data.getTableName());

      if(tableMetaData == null) {
         throw new Exception(Tool.buildString("Table ", data.getTableName(), " not found in data source ", dsName));
      }

      String tableName = tableMetaData.getName();
      String catalog = (String) tableMetaData.getAttribute("catalog");
      String schema = (String) tableMetaData.getAttribute("schema");
      String source = buildSource(catalog, schema, tableName);

      OsiDataset dataset = new OsiDataset();
      dataset.setName(tableName);
      dataset.setSource(source);
      dataset.setCustomExtensions(List.of(buildDatasetExtension(dsName, catalog, schema, source)));

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
         extData.put("type", type);
         extData.put("length", length);

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

      if(!(sheet instanceof Worksheet)) {
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
   private static final Logger log = LoggerFactory.getLogger(MetadataApiService.class);
}
