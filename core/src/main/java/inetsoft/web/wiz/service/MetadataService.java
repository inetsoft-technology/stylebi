/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.request.GetWorksheetMetaRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.*;

@Service
public class MetadataService {
   public MetadataService(XRepository xrepository, DataSourceService dataSourceService, AssetRepository assetRepository) {
      this.xrepository = xrepository;
      this.dataSourceService = dataSourceService;
      this.assetRepository = assetRepository;
   }

   public DatabaseTableMeta getMetaData(GetDatabaseTableMetaRequest data) throws Exception {
      String dsName = data.getDsName();
      XDataSource dataSource = xrepository.getDataSource(dsName);

      if(!(dataSource instanceof JDBCDataSource jdbcDataSource)) {
         throw new Exception("Data source " + dsName + " not found.");
      }

      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(dsName);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + dsName);
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(rootMetaData == null) {
         throw new Exception("No meta data found for data source " + dsName);
      }

      SQLTypes sqlTypes = SQLTypes.getSQLTypes(jdbcDataSource);
      XNode node = sqlTypes.getQualifiedTableNode(data.getTableName(), data.isHasCatalog(),
                                                  data.isHasSchema(), data.getCatalogSep(), jdbcDataSource,
                                                  data.getCatalog(), data.getSchema());

      if(rootMetaData.getAttribute("supportCatalog") != null) {
         node.setAttribute("supportCatalog", rootMetaData.getAttribute("supportCatalog"));
      }

      XNode tableNode = metaDataProvider.getMetaData(node, true);

      if(tableNode == null) {
         throw new Exception("Table " + data.getTableName() + " not found in data source " + dsName);
      }

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setName(tableNode.getName());
      tableMeta.setCatalog(data.getCatalog());
      tableMeta.setSchema(data.getSchema());
      List<DatabaseTableMeta.ColumnMeta> columns = new ArrayList<>();
      tableMeta.setColumns(columns);

      for(int i = 0; i < tableNode.getChildCount(); i++) {
         XNode columnNode = tableNode.getChild(i);

         if(columnNode == null) {
            continue;
         }

         DatabaseTableMeta.ColumnMeta columnMeta = new DatabaseTableMeta.ColumnMeta();
         columnMeta.setName(columnNode.getName());

         if(columnNode instanceof XTypeNode typeNode) {
            columnMeta.setType(typeNode.getType());
         }

         columnMeta.setPrimaryKey("true".equals(columnNode.getAttribute("PrimaryKey")));
         columnMeta.setLength((Integer) columnNode.getAttribute("length"));

         if(columnNode.getAttribute("ForeignKey") != null) {
            Vector<String[]> foreignKeys = (Vector<String[]>) columnNode.getAttribute("ForeignKey");
            List<String[]> foreignKeyList = new ArrayList<>(foreignKeys);
            columnMeta.setForeignKeys(foreignKeyList);
         }

         columns.add(columnMeta);
      }

      return tableMeta;
   }

   public WorksheetMeta getWorksheetMetaData(String wsId, XPrincipal principal)
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

      WorksheetMeta worksheetMeta = new WorksheetMeta();
      worksheetMeta.setName(entry.getPath());
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
      tableMeta.setTableName(tableAssembly.getName());
      ColumnSelection columnSelection = tableAssembly.getColumnSelection();

      if(columnSelection != null) {
         List<WorksheetTableMeta.ColumnMeta> columns = new ArrayList<>();
         int columnCount = columnSelection.getAttributeCount();

         for(int i = 0; i < columnCount; i++) {
            DataRef columnRef = columnSelection.getAttribute(i);

            if(columnRef != null) {
               WorksheetTableMeta.ColumnMeta columnMeta = createColumnMeta((ColumnRef) columnRef);
               columns.add(columnMeta);
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
    * Populate metadata for mirror table assemblies
    */
   private RotatedTableMeta getRotatedTableMetadata(RotatedTableAssembly mirrorTable) {
      RotatedTableMeta rotatedTableMeta = new RotatedTableMeta();

      Assembly baseAssembly = mirrorTable.getTableAssembly();

      if(baseAssembly != null) {
         rotatedTableMeta.setSourceTable(baseAssembly.getName());
      }

      return rotatedTableMeta;
   }

   /**
    * Create column metadata from DataRef
    */
   private WorksheetTableMeta.ColumnMeta createColumnMeta(ColumnRef columnRef) {
      WorksheetTableMeta.ColumnMeta columnMeta = new WorksheetTableMeta.ColumnMeta();
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
         XDataModel dataModel = dataSourceService.getDataModel(database);
         return dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
      }
      catch(Exception e) {
         return null;
      }
   }

   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
   private final AssetRepository assetRepository;
}
