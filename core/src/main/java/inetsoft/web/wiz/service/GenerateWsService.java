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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.wiz.model.DatasourceType;
import inetsoft.web.wiz.model.GenerateWsResponse;
import inetsoft.web.wiz.model.WorksheetConstructionModel;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class GenerateWsService {
   public GenerateWsService(ViewsheetService viewsheetService, MetadataApiService metadataApiService,
                            InnerJoinService innerJoinService,
                            LayoutGraphService layoutGraphService)
   {
      this.viewsheetService = viewsheetService;
      this.metadataApiService = metadataApiService;
      this.innerJoinService = innerJoinService;
      this.layoutGraphService = layoutGraphService;
   }

   private Worksheet getWorksheet(WorksheetConstructionModel model, Principal user) throws Exception {
      if(model.getFields() == null) {
         return null;
      }

      List<WorksheetConstructionModel.QueryField> fields = model.getFields();

      if(fields.isEmpty()) {
         return null;
      }

      WorksheetConstructionModel.QueryField queryField = fields.getFirst();

      if(queryField == null || queryField.getTable() == null ||
         queryField.getTable().getSource() == null ||
         !isWorksheet(queryField.getTable().getSource().getType()))
      {
         return null;
      }

      AssetEntry assetEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, queryField.getTable().getSource().getName(), null);

      return (Worksheet) viewsheetService.getAssetRepository().getSheet(assetEntry, user, true,
                                                                        AssetContent.ALL);

   }

   private boolean isWorksheet(String type) {
      return DatasourceType.WORKSHEET.equals(type);
   }

   public GenerateWsResponse generateWs(WorksheetConstructionModel model, Principal user)
      throws Exception
   {
      Worksheet originWs = getWorksheet(model, user);
      GenerateWsResponse generateWsResponse = new GenerateWsResponse();

      if(model.getFields() == null || model.getFields().isEmpty()) {
         throw new RuntimeException("Doesn't select any field!");
      }

      Worksheet worksheet = originWs;

      if(worksheet == null) {
         worksheet = new Worksheet();
      }

      AbstractTableAssembly table = null;

      if(model.getJoinPaths() == null) {
         if(originWs != null) {
            WSAssembly baseTable = (WSAssembly) originWs.getAssembly(model.getFields().getFirst().getTable().getName());
            table = new MirrorTableAssembly(originWs, model.getName(), baseTable);
         }
         else {
            table = new PhysicalBoundTableAssembly(worksheet, model.getName());
            applyColumnSelection(table, model.getFields());
         }
      }
      else if(model.getJoinPaths() != null) {
         boolean containsMergeJoin = false;//Todo
         List<WorksheetConstructionModel.JoinPath> joinPaths = model.getJoinPaths();

         if(containsMergeJoin) {
            Map<String, String> tableMapping = new HashMap<>();

            for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
               AbstractTableAssembly joinTable = createJoinTable(worksheet, joinPath, model.getFields(), tableMapping);
               worksheet.addAssembly(joinTable);

               if(joinPath == joinPaths.getLast()) {
                  table = joinTable;
               }
            }
         }
         else {
            final TableAssemblyOperator noperator = new TableAssemblyOperator();
            final Set<TableAssembly> tableAssemblies = new HashSet<>();

            for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
               noperator.addOperator(createJoinOperator(worksheet, joinPath, model.getFields()));
               tableAssemblies.add((TableAssembly) worksheet.getAssembly(joinPath.getLeftTable().getName()));
               tableAssemblies.add((TableAssembly) worksheet.getAssembly(joinPath.getRightTable().getName()));
            }

            RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(worksheet, "mainTable", tableAssemblies.toArray(new TableAssembly[0]), new TableAssemblyOperator[0]);
            worksheet.addAssembly(joinTable);

            innerJoinService.editExistingJoinTable(worksheet, joinTable, noperator, true);
            initCompositeTableAssemblyColumnSelection(joinTable);
            table = joinTable;
         }
      }

      if(model.getTableSetOperations() != null) {
         List<WorksheetConstructionModel.TableSetOperation> tableSetOperations = model.getTableSetOperations();
         List<WorksheetConstructionModel.QueryField> fields = model.getFields();
         Map<String, String> tableMapping = new HashMap<>();

         for(WorksheetConstructionModel.TableSetOperation tableSetOperation : tableSetOperations) {
            AbstractTableAssembly concatenatedTable = createConcatenatedTable(worksheet, tableSetOperation, fields, tableMapping);

            worksheet.addAssembly(concatenatedTable);
            tableMapping.put(getTableInfoKey(tableSetOperation.getLeftTable()), concatenatedTable.getName());
            tableMapping.put(getTableInfoKey(tableSetOperation.getRightTable()), concatenatedTable.getName());

            if(tableSetOperations.size() == 1 || tableSetOperation == tableSetOperations.getLast()) {
               table = concatenatedTable;
            }
         }
      }

      if(table == null) {
         throw new RuntimeException("can not generate worksheet");
      }

      worksheet.setPrimaryAssembly(table.getName());

      if(model.getFilters() != null) {
         applyCondition(table, model.getFilters());
      }
      if(model.getOrderBy() != null) {
         applyOrderBy(table, model.getOrderBy());
      }

      layoutGraph(worksheet);
      AssetEntry assetEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, model.getName(), null);
      viewsheetService.setWorksheet(worksheet, assetEntry, user, true, true);
      generateWsResponse.setWsId(assetEntry.toIdentifier());

      return generateWsResponse;
   }

   private void layoutGraph(Worksheet worksheet) throws Exception {
      WSLayoutGraphEvent.Builder builder = new WSLayoutGraphEvent.Builder();
      Assembly[] assemblies = worksheet.getAssemblies();
      int[] heights = new int[assemblies.length];
      int[] widths = new int[assemblies.length];
      String[] tablas = new String[assemblies.length];

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];
         heights[i] = 62;
         widths[i] = 150;
         tablas[i] = assembly.getName();
      }

      builder.heights(heights);
      builder.widths(widths);
      builder.names(tablas);
      layoutGraphService.layoutGraph(worksheet, builder.build());
   }

   private TableAssemblyOperator.Operator createJoinOperator(Worksheet worksheet, WorksheetConstructionModel.JoinPath joinPath,
                                                             List<WorksheetConstructionModel.QueryField> allFields) throws Exception
   {
      AbstractTableAssembly leftTable = createJoinBaseTable(worksheet, allFields, joinPath.getLeftTable(), joinPath.getLeftKey(), new HashMap<>());
      AbstractTableAssembly rightTable = createJoinBaseTable(worksheet, allFields, joinPath.getRightTable(), joinPath.getRightKey(), new HashMap<>());
      TableAssemblyOperator.Operator operator = new TableAssemblyOperator.Operator();
      operator.setLeftAttribute(leftTable.getColumnSelection(true).getAttribute(joinPath.getLeftKey()));
      operator.setRightAttribute(rightTable.getColumnSelection(true).getAttribute(joinPath.getRightKey()));
      operator.setLeftTable(leftTable.getName());
      operator.setRightTable(rightTable.getName());
      operator.setOperation(getJoinOperation(joinPath.getJoinType(), joinPath.getJoinOperator()));

      return operator;
   }

   private int getConcatenatedOperationType(WorksheetConstructionModel.SetOperationType operation) {
      return switch(operation) {
         case UNION -> TableAssemblyOperator.UNION;
         case EXCEPT -> TableAssemblyOperator.MINUS;
         case INTERSECT -> TableAssemblyOperator.INTERSECT;
      };
   }

   private AbstractTableAssembly createConcatenatedTable(Worksheet worksheet, WorksheetConstructionModel.TableSetOperation tableSetOperation,
                                                         List<WorksheetConstructionModel.QueryField> allFields, Map<String, String> tableMapping) throws Exception
   {
      AbstractTableAssembly leftTable = createConcatenatedBaseTable(worksheet, allFields, tableSetOperation.getLeftTable(), tableMapping);
      AbstractTableAssembly rightTable = createConcatenatedBaseTable(worksheet, allFields, tableSetOperation.getRightTable(), tableMapping);
      TableAssemblyOperator.Operator operator = new TableAssemblyOperator.Operator();
      operator.setOperation(getConcatenatedOperationType(tableSetOperation.getOperation()));
      TableAssemblyOperator tableAssemblyOperator = new TableAssemblyOperator();
      tableAssemblyOperator.addOperator(operator);

      String tableName = Tool.buildString(leftTable.getName(), "_concatenated_", rightTable.getName());
      tableMapping.put(getTableInfoKey(tableSetOperation.getLeftTable()), tableName);
      tableMapping.put(getTableInfoKey(tableSetOperation.getRightTable()), tableName);
      ConcatenatedTableAssembly table = new ConcatenatedTableAssembly(worksheet, tableName,
                                                                      new TableAssembly[]{ leftTable, rightTable }, new TableAssemblyOperator[]{ tableAssemblyOperator });
      initCompositeTableAssemblyColumnSelection(table);

      return table;

   }

   private AbstractTableAssembly createJoinTable(Worksheet worksheet, WorksheetConstructionModel.JoinPath joinPath,
                                                 List<WorksheetConstructionModel.QueryField> allFields, Map<String, String> tableMapping) throws Exception
   {
      AbstractTableAssembly leftTable = createJoinBaseTable(worksheet, allFields, joinPath.getLeftTable(), joinPath.getLeftKey(), tableMapping);
      AbstractTableAssembly rightTable = createJoinBaseTable(worksheet, allFields, joinPath.getRightTable(), joinPath.getRightKey(), tableMapping);
      TableAssemblyOperator.Operator operator = new TableAssemblyOperator.Operator();
      operator.setLeftAttribute(leftTable.getColumnSelection(true).getAttribute(joinPath.getLeftKey()));
      operator.setRightAttribute(rightTable.getColumnSelection(true).getAttribute(joinPath.getRightKey()));
      operator.setLeftTable(leftTable.getName());
      operator.setRightTable(rightTable.getName());
      operator.setOperation(getJoinOperation(joinPath.getJoinType(), joinPath.getJoinOperator()));
      TableAssemblyOperator tableAssemblyOperator = new TableAssemblyOperator();
      tableAssemblyOperator.addOperator(operator);
      TableAssemblyOperator[] operators = new TableAssemblyOperator[]{ tableAssemblyOperator };

      String tableName = Tool.buildString(leftTable.getName(), "_join_", rightTable.getName());
      tableMapping.put(getTableInfoKey(joinPath.getLeftTable()), tableName);
      tableMapping.put(getTableInfoKey(joinPath.getRightTable()), tableName);
      RelationalJoinTableAssembly table = new RelationalJoinTableAssembly(worksheet, tableName, new TableAssembly[]{ leftTable, rightTable }, operators);
      initCompositeTableAssemblyColumnSelection(table);

      return table;
   }

   private void initCompositeTableAssemblyColumnSelection(CompositeTableAssembly compositeTableAssembly) {
      if(compositeTableAssembly == null) {
         return;
      }

      ColumnSelection columnSelection = new ColumnSelection();
      TableAssembly[] tableAssemblies = compositeTableAssembly.getTableAssemblies();

      for(TableAssembly tableAssembly : tableAssemblies) {
         ColumnSelection baseColumnSelection = tableAssembly.getColumnSelection(true);

         for(int i = 0; i < baseColumnSelection.getAttributeCount(); i++) {
            DataRef attribute = baseColumnSelection.getAttribute(i);
            AttributeRef attributeRef = new AttributeRef(tableAssembly.getName(), attribute.getAttribute());
            attributeRef.setDataType(attribute.getDataType());
            ColumnRef col = new ColumnRef(attributeRef);
            col.setVisible(needAddColumn(compositeTableAssembly, columnSelection, tableAssembly.getName(), attribute.getAttribute()));
            columnSelection.addAttribute(col);
         }
      }

      compositeTableAssembly.setColumnSelection(columnSelection, false);
   }

   private boolean needAddColumn(CompositeTableAssembly compositeTableAssembly, ColumnSelection columnSelection, String tableName, String column) {
      if(compositeTableAssembly instanceof RelationalJoinTableAssembly joinTableAssembly) {
         if(columnSelection.getAttribute(column) == null) {
            return true;
         }

         Enumeration<TableAssemblyOperator> operators = joinTableAssembly.getOperators();

         while(operators.hasMoreElements()) {
            TableAssemblyOperator tableOperator = operators.nextElement();

            for(TableAssemblyOperator.Operator operator : tableOperator.getOperators()) {
               if(Tool.equals(operator.getLeftAttribute(), operator.getRightAttribute()) &&
                  (Tool.equals(tableName, operator.getLeftTable()) || Tool.equals(tableName, operator.getRightTable())) &&
                  (operator.getOperation() == TableAssemblyOperator.INNER_JOIN ||
                     operator.getOperation() == TableAssemblyOperator.RIGHT_JOIN ||
                     operator.getOperation() == TableAssemblyOperator.LEFT_JOIN ||
                     operator.getOperation() == TableAssemblyOperator.FULL_JOIN))
               {
                  return false;
               }
            }

         }
      }

      return true;
   }

   private String getTableInfoKey(WorksheetConstructionModel.TableInfo tableInfo) {
      WorksheetConstructionModel.SourceInfo source = tableInfo.getSource();

      return Tool.buildString(source.getType(), source.getName(), source.getSchema(), source.getCatalog(), tableInfo.getName());
   }

   private int getJoinOperation(String joinType, String joinOp) {
      switch(joinType) {
      case WorksheetConstructionModel.JoinType.FULL -> {
         return TableAssemblyOperator.FULL_JOIN;
      }
      case WorksheetConstructionModel.JoinType.CROSS -> {
         return TableAssemblyOperator.CROSS_JOIN;
      }
      case WorksheetConstructionModel.JoinType.LEFT -> {
         return TableAssemblyOperator.LEFT_JOIN;
      }
      case WorksheetConstructionModel.JoinType.RIGHT -> {
         return TableAssemblyOperator.RIGHT_JOIN;
      }
      case null, default -> {
         return switch(joinOp) {
            case WorksheetConstructionModel.JoinOperator.NOT_EQUALS ->
               TableAssemblyOperator.NOT_EQUAL_JOIN;
            case WorksheetConstructionModel.JoinOperator.GREATER ->
               TableAssemblyOperator.GREATER_JOIN;
            case WorksheetConstructionModel.JoinOperator.GREATER_EQUALS ->
               TableAssemblyOperator.GREATER_EQUAL_JOIN;
            case WorksheetConstructionModel.JoinOperator.LESS -> TableAssemblyOperator.LESS_JOIN;
            case WorksheetConstructionModel.JoinOperator.LESS_EQUALS ->
               TableAssemblyOperator.LESS_EQUAL_JOIN;
            case null, default -> TableAssemblyOperator.INNER_JOIN;
         };
      }
      }
   }

   private AbstractTableAssembly createJoinBaseTable(Worksheet worksheet,
                                                     List<WorksheetConstructionModel.QueryField> allFields,
                                                     WorksheetConstructionModel.TableInfo tableInfo,
                                                     String joinKey, Map<String, String> tableMapping) throws Exception
   {
      String joinLeafTableName = getBaseTableName(tableInfo, tableMapping);

      if(joinLeafTableName == null) {
         AbstractTableAssembly table;

         if(isWorksheet(tableInfo.getSource().getType()) || worksheet.getAssembly(tableInfo.getName()) != null) {
            return (AbstractTableAssembly) worksheet.getAssembly(tableInfo.getName());
         }
         GetDatabaseTableMetaRequest request = new GetDatabaseTableMetaRequest();
         request.setDsName(tableInfo.getSource().getName());
         request.setCatalog(tableInfo.getSource().getCatalog());
         request.setSchema(tableInfo.getSource().getSchema());
         request.setTableName(tableInfo.getName());
         inetsoft.web.wiz.model.DatabaseTableMeta metaData = metadataApiService.getMetaData(request);

         table = new PhysicalBoundTableAssembly(worksheet, tableInfo.getName());
         applySourceInfo(table, tableInfo);
         WorksheetConstructionModel.QueryField queryField = new WorksheetConstructionModel.QueryField();
         queryField.setName(joinKey);
         queryField.setTable(tableInfo);
         Set<WorksheetConstructionModel.QueryField> tableFields = getTableFields(allFields, tableInfo);
         tableFields.add(queryField);
         applyColumnSelection(table, tableFields.stream().toList(), metaData);
         worksheet.addAssembly(table);

         return table;
      }
      else {
         return (AbstractTableAssembly) worksheet.getAssembly(joinLeafTableName);
      }
   }

   private AbstractTableAssembly createConcatenatedBaseTable(Worksheet worksheet,
                                                             List<WorksheetConstructionModel.QueryField> allFields,
                                                             WorksheetConstructionModel.TableInfo tableInfo,
                                                             Map<String, String> tableMapping) throws Exception
   {
      String joinLeafTableName = getBaseTableName(tableInfo, tableMapping);

      if(joinLeafTableName == null) {
         if(isWorksheet(tableInfo.getSource().getType())) {
            return (AbstractTableAssembly) worksheet.getAssembly(tableInfo.getName());
         }

         AbstractTableAssembly table = new PhysicalBoundTableAssembly(worksheet, tableInfo.getName());
         applySourceInfo(table, tableInfo);
         Set<WorksheetConstructionModel.QueryField> tableFields = getTableFields(allFields, tableInfo);
         applyColumnSelection(table, tableFields.stream().toList());
         worksheet.addAssembly(table);

         return table;
      }
      else {
         return (AbstractTableAssembly) worksheet.getAssembly(joinLeafTableName);
      }
   }

   private String getBaseTableName(WorksheetConstructionModel.TableInfo tableInfo,
                                   Map<String, String> tableMapping)
   {
      String tableName = tableMapping.get(getTableInfoKey(tableInfo));

      if(!Tool.isEmptyString(tableName)) {
         return getBaseTableName0(tableName, tableMapping);
      }

      return null;
   }

   private String getBaseTableName0(String baseTableName, Map<String, String> tableMapping) {
      String tableName = tableMapping.get(baseTableName);

      if(!Tool.isEmptyString(tableName)) {
         return getBaseTableName0(tableName, tableMapping);
      }

      return baseTableName;
   }

   private Set<WorksheetConstructionModel.QueryField> getTableFields(List<WorksheetConstructionModel.QueryField> allFields,
                                                                     WorksheetConstructionModel.TableInfo tableInfo)
   {
      Set<WorksheetConstructionModel.QueryField> result = new HashSet<>();

      for(WorksheetConstructionModel.QueryField field : allFields) {
         if(Tool.equals(field.getTable(), tableInfo)) {
            result.add(field);
         }
      }

      return result;
   }

   private void applyColumnSelection(AbstractTableAssembly table,
                                     List<WorksheetConstructionModel.QueryField> fields) throws Exception
   {
      applyColumnSelection(table, fields, null);
   }

   private void applyColumnSelection(AbstractTableAssembly table,
                                     List<WorksheetConstructionModel.QueryField> fields,
                                     inetsoft.web.wiz.model.DatabaseTableMeta metaData)
      throws Exception
   {
      WorksheetConstructionModel.TableInfo selectTable = null;
      ColumnSelection columns = new ColumnSelection();

      for(WorksheetConstructionModel.QueryField field : fields) {
         if(field == null) {
            continue;
         }

         if(selectTable == null) {
            selectTable = field.getTable();
         }

         columns.addAttribute(convertToDataRef(field, table instanceof PhysicalBoundTableAssembly, metaData));
      }

      table.setColumnSelection(columns);

      if(selectTable != null && table instanceof PhysicalBoundTableAssembly) {
         applySourceInfo(table, selectTable);
      }
   }

   private DataRef convertToDataRef(WorksheetConstructionModel.QueryField field, boolean boundTable, inetsoft.web.wiz.model.DatabaseTableMeta metaData) {
      DataRef dataRef;

      if(Tool.isEmptyString(field.getExpression())) {
         String colType = null;

         if(boundTable) {

            if(metaData != null) {
               Optional<inetsoft.web.wiz.model.DatabaseTableMeta.ColumnMeta> metaCol = metaData.getColumns().stream()
                  .filter(c -> Tool.equals(c.getName(), field.getName()))
                  .findFirst();

               if(metaCol.isPresent()) {
                  colType = metaCol.get().getType();
               }
            }
         }

         String attr = field.getName();
         attr = AssetUtil.trimEntity(attr, null);
         AttributeRef ref = new AttributeRef(null, attr);
         ref.setDataType(colType);
         dataRef = ref;
      }
      else {
         ExpressionRef expressionRef = new ExpressionRef(null, field.getName());
         expressionRef.setExpression(field.getExpression());
         dataRef = expressionRef;
      }

      return new ColumnRef(dataRef);
   }

   private void applySourceInfo(AbstractTableAssembly table,
                                WorksheetConstructionModel.TableInfo selectTable) throws Exception
   {
      if(selectTable == null) {
         return;
      }

      WorksheetConstructionModel.SourceInfo source = selectTable.getSource();
      JDBCDataSource jdbcDatasource = metadataApiService.getJDBCDatasource(source.getName());
      XNode tableMetaData = metadataApiService.getTableMetaData(jdbcDatasource, source.getCatalog(), source.getSchema(), table.getName());

      if(tableMetaData == null) {
         throw new RuntimeException("Table:" + table.getName() + " does not exist!");
      }

      String qname = SQLTypes.getSQLTypes(jdbcDatasource).
         getQualifiedName(tableMetaData, jdbcDatasource);
      SourceInfo sinfo = new SourceInfo(
         SourceInfo.PHYSICAL_TABLE, source.getName(), qname);

      sinfo.setProperty(SourceInfo.SCHEMA, source.getSchema());
      sinfo.setProperty(SourceInfo.CATALOG, source.getCatalog());
      sinfo.setProperty(SourceInfo.TABLE_TYPE, (String) tableMetaData.getAttribute("type"));
      table.setSourceInfo(sinfo);
   }

   private void applyCondition(AbstractTableAssembly table,
                               List<WorksheetConstructionModel.Condition> conditions)
   {
      ConditionList conditionList = new ConditionList();
      table.setPreConditionList(conditionList);
      ColumnSelection columnSelection = table.getColumnSelection();

      for(WorksheetConstructionModel.Condition filter : conditions) {
         DataRef attributeRef = columnSelection.getAttribute(filter.getField());

         if(attributeRef == null) {
            continue;
         }

         WorksheetConstructionModel.FilterOperator filterOperator = filter.getOperator();
         List<Integer> conditionOperator = getConditionOperator(filterOperator);

         for(Integer op : conditionOperator) {
            AssetCondition assetCondition = new AssetCondition();

            if(filter.getValue() instanceof List list) {
               for(Object v : list) {
                  assetCondition.addValue(v);
               }
            }
            else {
               assetCondition.addValue(filter.getValue());
            }

            assetCondition.setNegated(filter.isNegated());
            assetCondition.setOperation(op);
            assetCondition.setType(attributeRef.getDataType());
            conditionList.append(new ConditionItem(attributeRef, assetCondition, filter.getConditionLevel()));
         }
      }

      table.setPreConditionList(conditionList);
   }

   private List<Integer> getConditionOperator(WorksheetConstructionModel.FilterOperator filterOperator) {
      List<Integer> ops = new ArrayList<>();

      if(Objects.requireNonNull(filterOperator) == WorksheetConstructionModel.FilterOperator.EQ) {
         ops.add(XCondition.EQUAL_TO);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.GT) {
         ops.add(XCondition.GREATER_THAN);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.GE) {
         ops.add(XCondition.EQUAL_TO);
         ops.add(XCondition.GREATER_THAN);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.LT) {
         ops.add(XCondition.LESS_THAN);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.LE) {
         ops.add(XCondition.EQUAL_TO);
         ops.add(XCondition.LESS_THAN);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.IN) {
         ops.add(XCondition.CONTAINS);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.BETWEEN) {
         ops.add(XCondition.BETWEEN);
      }
      else if(filterOperator == WorksheetConstructionModel.FilterOperator.LIKE) {
         ops.add(XCondition.LIKE);
      }
      else {
         throw new IllegalArgumentException("Unknown filter operator: " + filterOperator);
      }

      return ops;
   }

   private void applyOrderBy(AbstractTableAssembly table,
                             List<WorksheetConstructionModel.OrderByInfo> orders)
   {
      SortInfo sortInfo = table.getSortInfo();
      ColumnSelection columnSelection = table.getColumnSelection();

      for(WorksheetConstructionModel.OrderByInfo order : orders) {
         DataRef column = columnSelection.getAttribute(order.getField());

         if(column == null) {
            throw new RuntimeException("can not find the column: " + order.getField());
         }

         SortRef sortRef = new SortRef(column);
         sortRef.setOrder(getOrder(order.getDirection()));
         sortInfo.addSort(sortRef);
      }

      table.setSortInfo(sortInfo);
   }

   private int getOrder(WorksheetConstructionModel.Direction direction) {
      return switch(direction) {
         case ASC -> XConstants.SORT_ASC;
         case DESC -> XConstants.SORT_DESC;
      };
   }


   private final ViewsheetService viewsheetService;
   private final MetadataApiService metadataApiService;
   private final InnerJoinService innerJoinService;
   private final LayoutGraphService layoutGraphService;
}
