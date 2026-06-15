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
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.service.GenerateWsService.WORKSHEET_ROOT_FOLDER_PATH;
import static inetsoft.web.wiz.service.WizDateLevelUtil.getDateGroupLevel;

/**
 * Implements the incremental worksheet-table creation endpoint (/ws/table).
 * <p>
 * Each call handles one table assembly:
 * <ul>
 *   <li>{@code physical table} — {@link PhysicalBoundTableAssembly} referencing a DB table</li>
 *   <li>{@code mirror table}   — {@link MirrorTableAssembly} over an existing worksheet table,
 *       with optional aggregation and expression columns</li>
 *   <li>{@code relational join table} — {@link RelationalJoinTableAssembly} over existing tables</li>
 * </ul>
 */
@Service
public class WorksheetTableService {

   public WorksheetTableService(ViewsheetService viewsheetService,
                                MetadataApiService metadataApiService,
                                InnerJoinService innerJoinService,
                                LayoutGraphService layoutGraphService,
                                ObjectMapper objectMapper)
   {
      this.viewsheetService = viewsheetService;
      this.metadataApiService = metadataApiService;
      this.innerJoinService = innerJoinService;
      this.layoutGraphService = layoutGraphService;
      this.objectMapper = objectMapper;
   }

   // ─── Public entry point ───────────────────────────────────────────────────

   public WorksheetTableResponse createTable(WorksheetTableRequest request, Principal user)
      throws Exception
   {
      // 1. Load or create the worksheet.
      Worksheet worksheet;
      AssetEntry worksheetEntry;

      if(request.getWorksheetId() != null) {
         worksheetEntry = AssetEntry.createAssetEntry(request.getWorksheetId());
         AbstractSheet sheet = viewsheetService.getAssetRepository()
            .getSheet(worksheetEntry, user, false, AssetContent.ALL);

         if(!(sheet instanceof Worksheet ws)) {
            throw new IllegalArgumentException(
               sheet == null
                  ? "Worksheet not found: " + request.getWorksheetId()
                  : "worksheetId does not reference a worksheet: " + request.getWorksheetId());
         }

         worksheet = ws;
      }
      else {
         worksheet = new Worksheet();
         worksheetEntry = null;
      }

      // 2. Build the table assembly.
      AbstractTableAssembly table = buildTable(worksheet, request, user);

      // 3. Apply pre-aggregate conditions (WHERE).
      if(request.getPreAggregateCondition() != null && !request.getPreAggregateCondition().isEmpty()) {
         ConditionList preList = buildConditionList(
            table.getColumnSelection(true), request.getPreAggregateCondition(), worksheet, false);
         table.setPreConditionList(preList);
      }

      // 4. Apply aggregation (GROUP BY + aggregates).
      if(request.getAggregateInfo() != null) {
         applyAggregateInfo(table, request.getAggregateInfo());
      }

      // 5. Apply post-aggregate conditions (HAVING).
      if(request.getPostAggregateCondition() != null && !request.getPostAggregateCondition().isEmpty()) {
         ConditionList postList = buildConditionList(
            table.getColumnSelection(true), request.getPostAggregateCondition(), worksheet, true);
         table.setPostConditionList(postList);
      }

      // 6. Apply ranking / top-N conditions.
      if(request.getRankingCondition() != null && !request.getRankingCondition().isEmpty()) {
         ConditionList rankList = buildConditionList(
            table.getColumnSelection(true), request.getRankingCondition(), worksheet, false);
         table.setRankingConditionList(rankList);
      }

      // 7. Persist the worksheet.
      WsServiceHelper.layoutGraph(layoutGraphService, worksheet);

      if(worksheetEntry == null) {
         worksheetEntry = WsServiceHelper.persistWorksheet(viewsheetService, worksheet, user);
      }
      else {
         viewsheetService.getAssetRepository().setSheet(worksheetEntry, worksheet, user, true);
      }

      // 8. Extract column info for the response.
      List<WorksheetTableResponse.ColumnData> columns = extractColumnsFromSelection(table);

      WorksheetTableResponse response = new WorksheetTableResponse();
      response.setWsId(worksheetEntry.toIdentifier());
      response.setTableName(table.getName());
      response.setColumns(columns);
      response.setSuccess(true);
      return response;
   }

   // ─── Table builders ───────────────────────────────────────────────────────

   private AbstractTableAssembly buildTable(Worksheet worksheet, WorksheetTableRequest request,
                                            Principal user)
      throws Exception
   {
      String tableType = request.getTableType();

      if(tableType == null) {
         throw new IllegalArgumentException("tableType is required");
      }

      AbstractTableAssembly table = switch(tableType) {
         case "physical table"        -> buildPhysicalTable(worksheet, request, user);
         case "mirror table"          -> buildMirrorTable(worksheet, request);
         case "relational join table" -> buildJoinTable(worksheet, request);
         default -> throw new IllegalArgumentException("Unknown tableType: " + tableType);
      };

      if(request.isAsPrimaryTable()) {
         worksheet.setPrimaryAssembly(table.getName());
      }

      return table;
   }

   private AbstractTableAssembly buildPhysicalTable(Worksheet worksheet,
                                                    WorksheetTableRequest request,
                                                    Principal user)
      throws Exception
   {
      PhysicalBoundTableAssembly table =
         new PhysicalBoundTableAssembly(worksheet, request.getTableName());

      WorksheetTableRequest.PhysicalSource src = request.getPhysicalSource();

      if(src == null) {
         throw new IllegalArgumentException("physicalSource is required for physical table");
      }

      // Apply source info (datasource + qualified table name).
      JDBCDataSource ds = metadataApiService.getJDBCDatasource(src.getDatasourcePath());
      XNode tableMetaData = metadataApiService.getTableMetaData(
         ds, src.getCatalog(), src.getSchema(), src.getTableName());

      if(tableMetaData == null) {
         throw new IllegalArgumentException(
            "Table not found: " + src.getTableName() +
            " (datasource=" + src.getDatasourcePath() +
            ", schema=" + src.getSchema() +
            ", catalog=" + src.getCatalog() + ")");
      }

      String qname = SQLTypes.getSQLTypes(ds).getQualifiedName(tableMetaData, ds);
      SourceInfo sinfo = new SourceInfo(SourceInfo.PHYSICAL_TABLE, src.getDatasourcePath(), qname);
      sinfo.setProperty(SourceInfo.SCHEMA, src.getSchema());
      sinfo.setProperty(SourceInfo.CATALOG, src.getCatalog());
      sinfo.setProperty(SourceInfo.TABLE_TYPE, (String) tableMetaData.getAttribute("type"));
      table.setSourceInfo(sinfo);

      // Build column selection.
      if(request.getColumns() != null && !request.getColumns().isEmpty()) {
         // Explicit column list from the LLM.
         ColumnSelection cs = buildColumnSelection(request.getColumns());
         table.setColumnSelection(cs);
      }
      else {
         // No explicit columns → fetch all from datasource metadata.
         GetDatabaseTableMetaRequest metaReq = new GetDatabaseTableMetaRequest();
         metaReq.setDsName(src.getDatasourcePath());
         metaReq.setCatalog(src.getCatalog());
         metaReq.setSchema(src.getSchema());
         metaReq.setTableName(src.getTableName());
         OsiDataset metaData = metadataApiService.getMetaData(metaReq);
         ColumnSelection cs = buildColumnSelectionFromMeta(metaData);
         table.setColumnSelection(cs);
      }

      // Expression columns are only meaningful on non-aggregated mirror tables;
      // log a warning but don't fail if someone passes them here.
      applyExpressionColumns(table, request.getExpressionColumns());

      worksheet.addAssembly(table);
      return table;
   }

   private AbstractTableAssembly buildMirrorTable(Worksheet worksheet,
                                                  WorksheetTableRequest request)
   {
      List<String> bases = request.getBaseTables();

      if(bases == null || bases.isEmpty()) {
         throw new IllegalArgumentException("Mirror table requires baseTables[0]");
      }

      String baseTableName = bases.get(0);
      WSAssembly baseAssembly = (WSAssembly) worksheet.getAssembly(baseTableName);

      if(baseAssembly == null) {
         throw new IllegalArgumentException(
            "Base table '" + baseTableName + "' not found in worksheet");
      }

      MirrorTableAssembly mirror =
         new MirrorTableAssembly(worksheet, request.getTableName(), baseAssembly);

      // Expression columns are only valid when there is no aggregation.
      boolean hasAggregation = request.getAggregateInfo() != null &&
         ((request.getAggregateInfo().getGroups() != null && !request.getAggregateInfo().getGroups().isEmpty()) ||
          (request.getAggregateInfo().getAggregates() != null && !request.getAggregateInfo().getAggregates().isEmpty()));

      if(!hasAggregation) {
         applyExpressionColumns(mirror, request.getExpressionColumns());
      }

      worksheet.addAssembly(mirror);
      return mirror;
   }

   private AbstractTableAssembly buildJoinTable(Worksheet worksheet,
                                                WorksheetTableRequest request)
      throws Exception
   {
      List<String> bases = request.getBaseTables();
      List<WorksheetTableRequest.JoinPathInfo> joinPaths = request.getJoinPaths();

      if(bases == null || bases.isEmpty()) {
         throw new IllegalArgumentException("Relational join table requires baseTables");
      }

      if(joinPaths == null || joinPaths.isEmpty()) {
         throw new IllegalArgumentException("Relational join table requires joinPaths");
      }

      // Collect distinct base table assemblies in declaration order.
      Set<TableAssembly> tableSet = new LinkedHashSet<>();

      for(String name : bases) {
         WSAssembly asm = (WSAssembly) worksheet.getAssembly(name);

         if(asm == null) {
            throw new IllegalArgumentException(
               "Table '" + name + "' not found in worksheet");
         }

         if(!(asm instanceof TableAssembly)) {
            throw new IllegalArgumentException(
               "Assembly '" + name + "' is not a table assembly");
         }

         tableSet.add((TableAssembly) asm);
      }

      // Build the composite operator.
      TableAssemblyOperator noperator = new TableAssemblyOperator();

      for(WorksheetTableRequest.JoinPathInfo path : joinPaths) {
         TableAssembly left = (TableAssembly) worksheet.getAssembly(path.getLeftTable());
         TableAssembly right = (TableAssembly) worksheet.getAssembly(path.getRightTable());

         if(left == null || right == null) {
            throw new IllegalArgumentException(
               "Join path references table not in worksheet: " +
               path.getLeftTable() + " → " + path.getRightTable());
         }

         DataRef leftAttr = left.getColumnSelection(true).getAttribute(path.getLeftKey());
         DataRef rightAttr = right.getColumnSelection(true).getAttribute(path.getRightKey());

         if(leftAttr == null) {
            throw new IllegalArgumentException(
               "Left join key '" + path.getLeftKey() + "' not found in table '" + path.getLeftTable() + "'");
         }

         if(rightAttr == null) {
            throw new IllegalArgumentException(
               "Right join key '" + path.getRightKey() + "' not found in table '" + path.getRightTable() + "'");
         }

         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setLeftAttribute(leftAttr);
         op.setRightAttribute(rightAttr);
         op.setLeftTable(path.getLeftTable());
         op.setRightTable(path.getRightTable());
         op.setOperation(getJoinOperation(path.getJoinType(), path.getJoinOperator()));
         noperator.addOperator(op);
      }

      RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(
         worksheet, request.getTableName(),
         tableSet.toArray(new TableAssembly[0]),
         new TableAssemblyOperator[0]);

      worksheet.addAssembly(joinTable);
      innerJoinService.editExistingJoinTable(worksheet, joinTable, noperator, true);
      WsServiceHelper.initCompositeColumnSelection(joinTable);

      return joinTable;
   }

   // ─── Column selection helpers ─────────────────────────────────────────────

   private ColumnSelection buildColumnSelection(List<WorksheetTableRequest.ColumnInfo> cols) {
      ColumnSelection cs = new ColumnSelection();

      for(WorksheetTableRequest.ColumnInfo col : cols) {
         AttributeRef ref = new AttributeRef(null, AssetUtil.trimEntity(col.getName(), null));

         if(col.getType() != null) {
            ref.setDataType(col.getType());
         }

         ColumnRef colRef = new ColumnRef(ref);

         if(col.getAlias() != null) {
            colRef.setAlias(col.getAlias());
         }

         if(Boolean.FALSE.equals(col.getVisible())) {
            colRef.setVisible(false);
         }

         cs.addAttribute(colRef);
      }

      return cs;
   }

   private ColumnSelection buildColumnSelectionFromMeta(OsiDataset metaData) {
      ColumnSelection cs = new ColumnSelection();

      if(metaData == null || metaData.getFields() == null) {
         return cs;
      }

      for(OsiField field : metaData.getFields()) {
         String type = WsServiceHelper.extractFieldType(objectMapper, field);
         AttributeRef ref = new AttributeRef(null, field.getName());

         if(type != null) {
            ref.setDataType(type);
         }

         cs.addAttribute(new ColumnRef(ref));
      }

      return cs;
   }

   private void applyExpressionColumns(AbstractTableAssembly table,
                                       List<WorksheetTableRequest.ExpressionColumnInfo> exprCols)
   {
      if(exprCols == null || exprCols.isEmpty()) {
         return;
      }

      ColumnSelection cs = table.getColumnSelection(false);

      for(WorksheetTableRequest.ExpressionColumnInfo col : exprCols) {
         String colName = col.getAlias() != null ? col.getAlias() : col.getName();
         ExpressionRef expr = new ExpressionRef(null, colName);
         expr.setExpression(col.getExpression() != null ? col.getExpression() : "");
         ColumnRef colRef = new ColumnRef(expr);

         if(col.getAlias() != null) {
            colRef.setAlias(col.getAlias());
         }

         if(Boolean.FALSE.equals(col.getVisible())) {
            colRef.setVisible(false);
         }

         cs.addAttribute(colRef);
      }

      table.setColumnSelection(cs, false);
   }

   // ─── Aggregate info ───────────────────────────────────────────────────────

   private void applyAggregateInfo(AbstractTableAssembly table,
                                   WorksheetTableRequest.AggregateInfo aggInfo)
   {
      if(aggInfo == null) {
         return;
      }

      List<WorksheetTableRequest.GroupByFieldInfo> groups = aggInfo.getGroups();
      List<WorksheetTableRequest.AggregateFieldInfo> aggregates = aggInfo.getAggregates();

      if((groups == null || groups.isEmpty()) && (aggregates == null || aggregates.isEmpty())) {
         return;
      }

      AggregateInfo info = new AggregateInfo();
      ColumnSelection cs = table.getColumnSelection(true);
      ColumnSelection privateCs = table.getColumnSelection(false);

      if(groups != null) {
         for(WorksheetTableRequest.GroupByFieldInfo grp : groups) {
            DataRef ref = cs.getAttribute(grp.getFieldName());

            if(!(ref instanceof ColumnRef column)) {
               continue;
            }

            if(grp.getDateGroupLevel() != null) {
               String colName = column.getName();
               int dgroup = getDateGroupLevel(grp.getDateGroupLevel());
               String name = DateRangeRef.getName(colName, dgroup);
               DateRangeRef rangeRef = new DateRangeRef(name, column.getDataRef(), dgroup);
               rangeRef.setOriginalType(column.getDataType());
               ColumnRef dateColumn = new ColumnRef(rangeRef);
               dateColumn.setDataType(rangeRef.getDataType());

               // Insert the DateRangeRef column into the column selection before the base
               // column so the aggregate engine can resolve it (mirrors processDateGrouping).
               int baseIdx = privateCs.indexOfAttribute(column);

               if(baseIdx >= 0) {
                  privateCs.addAttribute(baseIdx, dateColumn);
               }
               else {
                  privateCs.addAttribute(dateColumn);
               }

               column = dateColumn;
            }

            info.addGroup(new GroupRef(column));
         }
      }

      if(aggregates != null) {
         for(WorksheetTableRequest.AggregateFieldInfo agg : aggregates) {
            DataRef column = cs.getAttribute(agg.getFieldName());

            if(column == null) {
               continue;
            }

            AggregateFormula formula = AggregateFormula.getFormula(agg.getFormula());

            if(formula == null) {
               formula = AggregateFormula.SUM;
            }

            DataRef secondaryCol = null;

            if(agg.getSecondaryField() != null && formula.isTwoColumns()) {
               secondaryCol = cs.getAttribute(agg.getSecondaryField());
            }

            AggregateRef aggRef = new AggregateRef(column, secondaryCol, formula);

            if(agg.getN() != null && formula.hasN()) {
               aggRef.setN(agg.getN());
            }

            info.addAggregate(aggRef);

            // AggregateRef has no setAlias(). Expose the alias via a ColumnRef alias so
            // downstream steps can resolve this aggregate column by its friendly name.
            if(agg.getAlias() != null) {
               DataRef col = privateCs.getAttribute(agg.getFieldName());

               if(col instanceof ColumnRef columnRef) {
                  columnRef.setAlias(agg.getAlias());
               }
            }
         }
      }

      if(!info.isEmpty()) {
         table.setAggregateInfo(info);
         table.setColumnSelection(privateCs, false);
      }
   }

   // ─── Condition list ───────────────────────────────────────────────────────

   /**
    * Converts the flat {@link WorksheetTableRequest.ConditionItem} list emitted by
    * the wiz-services condition-tree normaliser into a StyleBI {@link ConditionList}.
    *
    * <p>Each item carries:
    * <ul>
    *   <li>{@code conditionLevel} — nesting depth of the condition itself.</li>
    *   <li>{@code junction} — logical operator connecting this item to the preceding one
    *       ({@code null} for the first item).</li>
    *   <li>{@code conditionJunctionLevel} — level at which the {@link JunctionOperator} is
    *       inserted; equals {@code conditionLevel} for same-level siblings but equals the
    *       outer level when this item is the first element of a group that is itself a sibling
    *       of the preceding group (falls back to {@code conditionLevel} when absent).</li>
    * </ul>
    *
    * @param columns   column selection used to resolve field names
    * @param items     flat condition list from the request
    * @param worksheet the worksheet (needed for SUBQUERY value resolution)
    * @param isHaving  true when building a HAVING (post-aggregate) condition list;
    *                  fields with {@code aggregateFormula} are wrapped in {@link AggregateRef}
    */
   private ConditionList buildConditionList(ColumnSelection columns,
                                            List<WorksheetTableRequest.ConditionItem> items,
                                            Worksheet worksheet,
                                            boolean isHaving)
   {
      ConditionList list = new ConditionList();

      for(WorksheetTableRequest.ConditionItem item : items) {
         // Insert a junction operator before each non-first item.
         if(item.getJunction() != null) {
            int junctionType = "or".equalsIgnoreCase(item.getJunction())
               ? JunctionOperator.OR : JunctionOperator.AND;
            list.append(new JunctionOperator(junctionType, item.resolveJunctionLevel()));
         }

         appendConditionItem(list, item, columns, worksheet, isHaving);
      }

      return list;
   }

   private void appendConditionItem(ConditionList list,
                                    WorksheetTableRequest.ConditionItem item,
                                    ColumnSelection columns,
                                    Worksheet worksheet,
                                    boolean isHaving)
   {
      if(item.getField() == null || item.getOperation() == null) {
         return;
      }

      // Resolve the column reference.
      DataRef ref = columns.getAttribute(item.getField());

      if(ref == null) {
         return;
      }

      // For HAVING conditions, wrap the column in an AggregateRef when a formula is present.
      if(isHaving && item.getAggregateFormula() != null &&
         !"none".equalsIgnoreCase(item.getAggregateFormula()))
      {
         AggregateFormula formula = AggregateFormula.getFormula(item.getAggregateFormula());

         if(formula == null) {
            formula = AggregateFormula.COUNT_ALL;
         }

         DataRef secondary = item.getSecondaryField() != null
            ? columns.getAttribute(item.getSecondaryField()) : null;
         AggregateRef aggRef = new AggregateRef(ref, secondary, formula);

         if(item.getNOrP() != null && formula.hasN()) {
            aggRef.setN(item.getNOrP());
         }

         ref = aggRef;
      }

      // Determine the XCondition operation code(s).  LESS/GREATER with equal=true expand to two.
      List<Integer> ops = mapOperation(item.getOperation(), item.getEqual());
      String dataType = ref.getDataType() != null ? ref.getDataType() : XSchema.STRING;

      boolean firstOp = true;

      for(int op : ops) {
         if(!firstOp) {
            // LESS_THAN/GREATER_THAN with equal=true expand to two ops joined by OR (e.g. < OR =).
            list.append(new JunctionOperator(JunctionOperator.OR, item.getConditionLevel()));
         }

         firstOp = false;
         AssetCondition ac = new AssetCondition();
         ac.setOperation(op);
         ac.setType(dataType);
         ac.setNegated(item.isNegated());

         if(item.getValues() != null) {
            for(WorksheetTableRequest.WorksheetConditionValue v : item.getValues()) {
               addConditionValue(ac, v, columns, worksheet);
            }
         }

         list.append(new ConditionItem(ref, ac, item.getConditionLevel()));
      }
   }

   private List<Integer> mapOperation(String operation, Boolean equal) {
      boolean isEqual = Boolean.TRUE.equals(equal);
      List<Integer> ops = new ArrayList<>();

      switch(operation) {
         case "EQUAL_TO"      -> ops.add(XCondition.EQUAL_TO);
         case "ONE_OF"        -> ops.add(XCondition.ONE_OF);
         case "LESS_THAN"     -> {
            ops.add(XCondition.LESS_THAN);
            if(isEqual) ops.add(XCondition.EQUAL_TO);
         }
         case "GREATER_THAN"  -> {
            ops.add(XCondition.GREATER_THAN);
            if(isEqual) ops.add(XCondition.EQUAL_TO);
         }
         case "BETWEEN"       -> ops.add(XCondition.BETWEEN);
         case "STARTING_WITH" -> ops.add(XCondition.STARTING_WITH);
         case "CONTAINS"      -> ops.add(XCondition.CONTAINS);
         case "LIKE"          -> ops.add(XCondition.LIKE);
         case "NULL"          -> ops.add(XCondition.NULL);
         case "DATE_IN"       -> ops.add(XCondition.DATE_IN);
         default -> throw new IllegalArgumentException("Unknown condition operation: " + operation);
      }

      return ops;
   }

   private void addConditionValue(AssetCondition condition,
                                  WorksheetTableRequest.WorksheetConditionValue v,
                                  ColumnSelection columns,
                                  Worksheet worksheet)
   {
      if(v == null || v.getType() == null) {
         return;
      }

      switch(v.getType()) {
         case "VALUE" -> condition.addValue(v.getValue());

         case "EXPRESSION" -> {
            ExpressionValue ev = new ExpressionValue();
            ev.setExpression(v.getValue() != null ? v.getValue().toString() : "");
            ev.setType(ExpressionValue.JAVASCRIPT);
            condition.addValue(ev);
         }

         case "SESSION_DATA" -> {
            // Session variables are stored as UserVariable references.
            UserVariable uv = new UserVariable(
               v.getValue() != null ? v.getValue().toString() : "");
            condition.addValue(uv);
         }

         case "FIELD" -> {
            ExpressionValue ev = new ExpressionValue();
            String expressoin = v.getValue() != null ? v.getValue().toString() : "";
            ev.setExpression(expressoin);
            ev.setType(ExpressionValue.JAVASCRIPT);
            condition.addValue(ev);
         }

         case "SUBQUERY" -> {
            WorksheetTableRequest.SubQueryInfo sq = v.getSubQuery();

            if(sq == null || sq.getSubQueryName() == null) {
               return;
            }

            SubQueryValue subQuery = new SubQueryValue();
            subQuery.setQuery(sq.getSubQueryName());

            TableAssembly queryTable = (TableAssembly) worksheet.getAssembly(sq.getSubQueryName());

            if(queryTable != null) {
               ColumnSelection queryCs = queryTable.getColumnSelection(true);
               DataRef attrRef = queryCs.getAttribute(sq.getInSubQueryColumn());
               subQuery.setAttribute(attrRef);

               // Correlated subquery: per-row filter linking subquery to main table.
               WorksheetTableRequest.SubQueryWhere where = sq.getWhere();

               if(where != null) {
                  DataRef subAttrRef = queryCs.getAttribute(where.getSubQueryColumn());
                  subQuery.setSubAttribute(subAttrRef);
                  DataRef mainAttrRef = columns.getAttribute(where.getCurrentTableColumn());
                  subQuery.setMainAttribute(mainAttrRef);
               }
            }

            condition.addValue(subQuery);
         }

         default -> condition.addValue(v.getValue());
      }
   }

   // ─── Column extraction for response ──────────────────────────────────────
   private List<WorksheetTableResponse.ColumnData> extractColumnsFromSelection(
      AbstractTableAssembly table)
   {
      ColumnSelection cs = table.getColumnSelection(true);
      List<WorksheetTableResponse.ColumnData> result = new ArrayList<>(cs.getAttributeCount());

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef attr = cs.getAttribute(i);

         if(attr instanceof ColumnRef cr && cr.isVisible()) {
            String name = cr.getName();
            String type = cr.getDataType();
            result.add(new WorksheetTableResponse.ColumnData(name, type));
         }
      }

      return result;
   }

   // ─── Join operation mapping ───────────────────────────────────────────────

   private int getJoinOperation(String joinType, String joinOp) {
      if(joinType == null) {
         joinType = WorksheetConstructionModel.JoinType.INNER;
      }

      return switch(joinType) {
         case WorksheetConstructionModel.JoinType.FULL  -> TableAssemblyOperator.FULL_JOIN;
         case WorksheetConstructionModel.JoinType.CROSS -> TableAssemblyOperator.CROSS_JOIN;
         case WorksheetConstructionModel.JoinType.LEFT  -> TableAssemblyOperator.LEFT_JOIN;
         case WorksheetConstructionModel.JoinType.RIGHT -> TableAssemblyOperator.RIGHT_JOIN;
         default -> joinOp == null ? TableAssemblyOperator.INNER_JOIN :
            switch(joinOp) {
               case WorksheetConstructionModel.JoinOperator.NOT_EQUALS    -> TableAssemblyOperator.NOT_EQUAL_JOIN;
               case WorksheetConstructionModel.JoinOperator.GREATER       -> TableAssemblyOperator.GREATER_JOIN;
               case WorksheetConstructionModel.JoinOperator.GREATER_EQUALS-> TableAssemblyOperator.GREATER_EQUAL_JOIN;
               case WorksheetConstructionModel.JoinOperator.LESS          -> TableAssemblyOperator.LESS_JOIN;
               case WorksheetConstructionModel.JoinOperator.LESS_EQUALS   -> TableAssemblyOperator.LESS_EQUAL_JOIN;
               default                                                     -> TableAssemblyOperator.INNER_JOIN;
            };
      };
   }

   // ─── Dependencies ─────────────────────────────────────────────────────────

   private final ViewsheetService viewsheetService;
   private final MetadataApiService metadataApiService;
   private final InnerJoinService innerJoinService;
   private final LayoutGraphService layoutGraphService;
   private final ObjectMapper objectMapper;
}
