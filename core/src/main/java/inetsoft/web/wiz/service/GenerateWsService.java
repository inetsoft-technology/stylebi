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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.model.osi.*;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.service.WizDateLevelUtil.getDateGroupLevel;

@Service
public class GenerateWsService {
   public GenerateWsService(ViewsheetService viewsheetService, MetadataApiService metadataApiService,
                            InnerJoinService innerJoinService,
                            LayoutGraphService layoutGraphService,
                            WsMergeService wsMergeService,
                            ObjectMapper objectMapper)
   {
      this.viewsheetService = viewsheetService;
      this.metadataApiService = metadataApiService;
      this.innerJoinService = innerJoinService;
      this.layoutGraphService = layoutGraphService;
      this.wsMergeService = wsMergeService;
      this.objectMapper = objectMapper;
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

      AssetEntry assetEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, queryField.getTable().getSource().getPath(), null);

      AbstractSheet sheet = viewsheetService.getAssetRepository().getSheet(assetEntry, user, true,
                                                                           AssetContent.ALL);

      if(sheet == null) {
         throw new IllegalArgumentException("Worksheet not found: " + queryField.getTable().getSource().getPath());
      }

      if(!(sheet instanceof Worksheet)) {
         throw new IllegalStateException("Asset is not a worksheet: " + queryField.getTable().getSource().getPath());
      }

      return (Worksheet) sheet;

   }

   private boolean isWorksheet(String type) {
      return DatasourceType.WORKSHEET.equals(type);
   }

   public GenerateWsResponse generateWs(WorksheetConstructionModel model, Principal user)
      throws Exception
   {
      WorksheetBuildResult buildResult = buildFreshWorksheet(model, user);
      Worksheet worksheet = buildResult.worksheet();
      AbstractTableAssembly table = buildResult.primaryTable();
      GenerateWsResponse generateWsResponse = new GenerateWsResponse();

      if(model.getWorksheetId() != null) {
         // Incremental path: merge the new query into the existing worksheet
         AssetEntry existingEntry;

         try {
            existingEntry = AssetEntry.createAssetEntry(model.getWorksheetId());
         }
         catch(Exception e) {
            throw new IllegalArgumentException("Invalid worksheetId: " + model.getWorksheetId(), e);
         }

         AbstractSheet sheet = viewsheetService.getAssetRepository()
            .getSheet(existingEntry, user, false, AssetContent.ALL);

         if(!(sheet instanceof Worksheet dashWS)) {
            throw new IllegalArgumentException(
               sheet == null
                  ? "Worksheet not found: " + model.getWorksheetId()
                  : "worksheetId does not reference a worksheet: " + model.getWorksheetId());
         }

         String vizSuffix = wsMergeService.computeUniqueSuffix(model.getName(), dashWS);
         Map<String, String> wsRenameMap = wsMergeService.mergeWorksheet(worksheet, dashWS, vizSuffix, new HashMap<>());
         String finalTableName = wsRenameMap.getOrDefault(table.getName(), table.getName());
         // By design, the primary assembly always tracks the most recently added query.
         // Callers (e.g. WizVsService) bind to the primary assembly name returned in
         // the response, so downstream VS bindings remain consistent with the last request.
         dashWS.setPrimaryAssembly(finalTableName);
         WsServiceHelper.layoutGraph(layoutGraphService, dashWS);
         viewsheetService.getAssetRepository().setSheet(existingEntry, dashWS, user, true);
         generateWsResponse.setWsId(existingEntry.toIdentifier());
         generateWsResponse.setPrimaryTable(finalTableName);
         generateWsResponse.setPrimaryTableFields(extractPrimaryTableFields(dashWS, table, model));
      }
      else {
         // New worksheet path
         worksheet.setPrimaryAssembly(table.getName());
         WsServiceHelper.layoutGraph(layoutGraphService, worksheet);
         AssetEntry assetEntry = WsServiceHelper.persistWorksheet(viewsheetService, worksheet, user);
         generateWsResponse.setWsId(assetEntry.toIdentifier());
         generateWsResponse.setPrimaryTable(worksheet.getPrimaryAssemblyName());
         generateWsResponse.setPrimaryTableFields(extractPrimaryTableFields(worksheet, table, model));
      }

      return generateWsResponse;
   }

   private record WorksheetBuildResult(Worksheet worksheet, AbstractTableAssembly primaryTable) {
   }

   private WorksheetBuildResult buildFreshWorksheet(WorksheetConstructionModel model, Principal user)
      throws Exception
   {
      Worksheet originWs = getWorksheet(model, user);

      if(model.getFields() == null || model.getFields().isEmpty()) {
         throw new IllegalArgumentException("At least one field must be selected.");
      }

      Worksheet worksheet = originWs != null ? originWs : new Worksheet();

      // #75456: wiz produces analytical charts that aggregate over the worksheet's output. The
      // WorksheetInfo constructor seeds inputmax from the design-mode sample cap
      // (asset.sample.maxrows, default 50000); when the underlying query returns more detail rows
      // than that, the chart aggregates only the capped prefix and Sum/Count silently under-count
      // (averages/proportions survive, so it hides). Wiz analytics default to full data — 0 =
      // unlimited. (Sampled-preview mode will pass a non-zero cap here in a follow-up.)
      worksheet.getWorksheetInfo().setDesignMaxRows(0);
      AbstractTableAssembly table = null;
      List<WorksheetConstructionModel.QueryField> fields = new ArrayList<>(model.getFields());

      if(model.getJoinPaths() == null || model.getJoinPaths().isEmpty()) {
         if(originWs != null) {
            WSAssembly baseTable = (WSAssembly) originWs.getAssembly(fields.getFirst().getTable().getName());
            table = new MirrorTableAssembly(originWs, model.getName(), baseTable);
         }
         else {
            table = new PhysicalBoundTableAssembly(worksheet, model.getName());
            applyColumnSelection(table, fields);
         }

         worksheet.addAssembly(table);
      }
      else {
         boolean containsMergeJoin = false; // Todo To be implemented later
         List<WorksheetConstructionModel.JoinPath> joinPaths = model.getJoinPaths();
         fixJoinPathKey(joinPaths, fields);
         fields = deduplicateByJoinKeys(fields, joinPaths);

         for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
            addJoinKeyField(fields, joinPath.getLeftTable(), joinPath.getLeftKey());
            addJoinKeyField(fields, joinPath.getRightTable(), joinPath.getRightKey());
         }

         if(containsMergeJoin) {
            Map<String, String> tableMapping = new HashMap<>();

            for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
               AbstractTableAssembly joinTable = createJoinTable(worksheet, joinPath, fields, tableMapping);
               worksheet.addAssembly(joinTable);

               if(joinPath == joinPaths.getLast()) {
                  table = joinTable;
               }
            }
         }
         else {
            final TableAssemblyOperator noperator = new TableAssemblyOperator();
            // LinkedHashSet (not HashSet): the base-table order must be deterministic and follow
            // the join direction (left before right). The SQL generator (JoinQuery.mergeFrom) binds
            // each operator attribute to a table by this array's position, so a non-deterministic
            // order can pair a key with the wrong table.
            final Set<TableAssembly> tableAssemblies = new LinkedHashSet<>();

            for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
               noperator.addOperator(createJoinOperator(worksheet, joinPath, fields));
               tableAssemblies.add((TableAssembly) worksheet.getAssembly(joinPath.getLeftTable().getName()));
               tableAssemblies.add((TableAssembly) worksheet.getAssembly(joinPath.getRightTable().getName()));
            }

            RelationalJoinTableAssembly joinTable = new RelationalJoinTableAssembly(worksheet, "mainTable", tableAssemblies.toArray(new TableAssembly[0]), new TableAssemblyOperator[0]);
            worksheet.addAssembly(joinTable);

            innerJoinService.editExistingJoinTable(worksheet, joinTable, noperator, true);
            WsServiceHelper.initCompositeColumnSelection(joinTable);
            applyFieldVisibility(joinTable, fields);
            table = joinTable;
         }
      }

      if(model.getTableSetOperations() != null) {
         List<WorksheetConstructionModel.TableSetOperation> tableSetOperations = model.getTableSetOperations();
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
         throw new IllegalStateException("Failed to generate worksheet: no table assembly was produced.");
      }

      if(model.getFilters() != null) {
         applyCondition(table, model.getFilters());
      }

      if(model.getOrderBy() != null) {
         applyOrderBy(table, model.getOrderBy());
      }

      // Apply GROUP BY and aggregate info
      // Apply HAVING conditions (stored in postconds)
      if(model.getAggregates() != null && model.getHaving() != null) {
         applyAggregateInfo(table, model.getGroupBy(), model.getAggregates(), model.getHaving());
         applyHavingCondition(table, model.getHaving());
      }

      return new WorksheetBuildResult(worksheet, table);
   }

   private static boolean isJoinKeyRepresented(Collection<WorksheetConstructionModel.QueryField> fields,
                                               WorksheetConstructionModel.TableInfo table, String key)
   {
      if(Tool.isEmptyString(key)) {
         return true;
      }

      String tableName = table != null ? table.getName() : null;
      return fields.stream().anyMatch(f ->
         Objects.equals(f.getTable(), table) &&
         (Objects.equals(f.getAlias(), key) ||
          Objects.equals(f.getFieldName(), key) ||
          Objects.equals(Tool.buildString(tableName, ".", f.getFieldName()), key))
      );
   }

   static void addJoinKeyField(Collection<WorksheetConstructionModel.QueryField> fields,
                               WorksheetConstructionModel.TableInfo table, String key)
   {
      if(!isJoinKeyRepresented(fields, table, key)) {
         WorksheetConstructionModel.QueryField keyField =
            new WorksheetConstructionModel.QueryField(table, key);
         // A synthesized key exists only to satisfy the join; hide it so the
         // worksheet output (and autoBinding, which binds every visible column)
         // never sees it as a bindable field.
         keyField.setVisible(false);
         fields.add(keyField);
      }
   }

   private void fixJoinPathKey(List<WorksheetConstructionModel.JoinPath> joinPaths,
                               List<WorksheetConstructionModel.QueryField> fields)
   {
      if(joinPaths == null || joinPaths.isEmpty()) {
         return;
      }

      Map<String, String> keyToAliasMap = buildKeyToAliasMap(fields);

      joinPaths.forEach(joinPath -> {
         joinPath.setLeftKey(qualifyKey(joinPath.getLeftTable(), joinPath.getLeftKey(), keyToAliasMap));
         joinPath.setRightKey(qualifyKey(joinPath.getRightTable(), joinPath.getRightKey(), keyToAliasMap));
      });
   }

   /**
    * Remove duplicate fields that map to the same underlying DB column in the same table.
    *
    * When the same DB column appears with multiple aliases (e.g. "customer_id" and
    * "c.customer_id" both for CUSTOMERS.CUSTOMER_ID), ColumnSelection.addAttribute
    * deduplicates by the base attribute name and silently drops every field after the first.
    * fixJoinPathKey resolves the join key to a specific alias; this method ensures only that
    * alias survives so that applyColumnSelection registers the correct column name and
    * getAttribute(joinKey) can subsequently find it.
    *
    * Strategy per (tableName, unqualifiedFieldName) group:
    *  - One field  → keep as-is.
    *  - Multiple fields → keep the one whose alias is a resolved join key.
    *    If none match, keep the last entry ("last writer wins", consistent with
    *    buildKeyToAliasMap's map.put behaviour).
    */
   private List<WorksheetConstructionModel.QueryField> deduplicateByJoinKeys(
      List<WorksheetConstructionModel.QueryField> fields,
      List<WorksheetConstructionModel.JoinPath> joinPaths)
   {
      // Collect all resolved join-key aliases (e.g. "c.customer_id", "CONTACTS.CUSTOMER_ID")
      Set<String> joinKeyAliases = new HashSet<>();

      for(WorksheetConstructionModel.JoinPath joinPath : joinPaths) {
         if(!Tool.isEmptyString(joinPath.getLeftKey())) {
            joinKeyAliases.add(joinPath.getLeftKey());
         }

         if(!Tool.isEmptyString(joinPath.getRightKey())) {
            joinKeyAliases.add(joinPath.getRightKey());
         }
      }

      // Canonical key: "tableName::unqualifiedFieldName"
      // LinkedHashMap preserves original field order for non-duplicate entries.
      Map<String, WorksheetConstructionModel.QueryField> canonicalMap = new LinkedHashMap<>();

      for(WorksheetConstructionModel.QueryField field : fields) {
         if(field.getTable() == null || field.getFieldName() == null) {
            continue;
         }

         String colKey = field.getTable().getName() + "::" + field.getUnqualifiedFieldName();
         WorksheetConstructionModel.QueryField existing = canonicalMap.get(colKey);

         if(existing == null) {
            canonicalMap.put(colKey, field);
         }
         else {
            boolean existingIsJoinKey = joinKeyAliases.contains(existing.getAlias());
            boolean newIsJoinKey = joinKeyAliases.contains(field.getAlias());

            if(newIsJoinKey && !existingIsJoinKey) {
               // Replace: the incoming field carries the join-key alias; the current one does not.
               canonicalMap.put(colKey, field);
            }
            else if(!existingIsJoinKey) {
               // Neither is a join key — "last writer wins" to stay consistent with buildKeyToAliasMap.
               canonicalMap.put(colKey, field);
            }
            // else: existing already carries the join-key alias — keep it.
         }
      }

      return new ArrayList<>(canonicalMap.values());
   }

   /**
    * Build a lookup map from fully-qualified field name (and its alias variants) to alias.
    * Each field is indexed by:
    * 1. "tableName.fieldName" (if fieldName has no dot) — qualified key avoids cross-table collision
    * 2. Its original fieldName (e.g. "table.col" if fieldName already contains a dot)
    * 3. Its alias (if the alias differs from the fieldName) — for alias-as-key lookups
    */
   private Map<String, String> buildKeyToAliasMap(List<WorksheetConstructionModel.QueryField> fields) {
      if(fields == null || fields.isEmpty()) {
         return Collections.emptyMap();
      }

      Map<String, String> map = new HashMap<>(fields.size() * 2);

      for(WorksheetConstructionModel.QueryField field : fields) {
         String alias = field.getAlias();
         String fieldName = field.getFieldName();

         if(fieldName == null || field.getTable() == null) {
            continue;
         }

         if(!fieldName.contains(".")) {
            // Index by qualified name only; unqualified names can collide across tables
            String fullName = Tool.buildString(field.getTable().getName(), ".", fieldName);
            map.put(fullName, alias);
         }
         else {
            // fieldName already qualified (e.g. "table.col")
            map.putIfAbsent(fieldName, alias);
         }

         if(alias != null && !alias.equals(fieldName)) {
            map.putIfAbsent(alias, alias);
         }
      }

      return map;
   }

   /**
    * Qualify a raw key with the table prefix if needed, then resolve to its alias.
    */
   private String qualifyKey(WorksheetConstructionModel.TableInfo table, String key, Map<String, String> keyToAliasMap) {
      if(Tool.isEmptyString(key) || table == null) {
         return key;
      }

      if(!key.contains(".")) {
         // For unqualified keys, qualify with the table name first to avoid cross-table collision
         String qualifiedKey = Tool.buildString(table.getName(), ".", key);

         if(keyToAliasMap.containsKey(qualifiedKey)) {
            String alias = keyToAliasMap.get(qualifiedKey);
            return alias != null ? alias : qualifiedKey;
         }

         // Fallback: direct lookup handles alias-as-key (e.g. key is already an alias)
         String alias = keyToAliasMap.get(key);
         return alias != null ? alias : qualifiedKey;
      }

      // Already-qualified "table.col": direct lookup
      String alias = keyToAliasMap.get(key);
      return alias != null ? alias : key;
   }

   /**
    * Resolve a join key to its column on a join base table.
    *
    * Uses the PRIVATE column selection on purpose: a synthesized join key (one not also
    * selected as an output field) is added invisible by {@link #addJoinKeyField}, and
    * {@code AbstractTableAssembly.setColumnSelection} drops invisible columns from the
    * PUBLIC selection. Resolving against the public selection therefore returns null for
    * such keys, yielding an operator with a null attribute that the join engine then
    * mis-pairs to the wrong table. The private selection retains the hidden key.
    */
   static DataRef resolveJoinKeyAttribute(AbstractTableAssembly table, String key) {
      if(table == null || key == null) {
         return null;
      }

      return table.getColumnSelection(false).getAttribute(key);
   }

   private TableAssemblyOperator.Operator createJoinOperator(Worksheet worksheet, WorksheetConstructionModel.JoinPath joinPath,
                                                             List<WorksheetConstructionModel.QueryField> allFields) throws Exception
   {
      AbstractTableAssembly leftTable = createJoinBaseTable(worksheet, allFields, joinPath.getLeftTable(), joinPath.getLeftKey(), new HashMap<>());
      AbstractTableAssembly rightTable = createJoinBaseTable(worksheet, allFields, joinPath.getRightTable(), joinPath.getRightKey(), new HashMap<>());
      DataRef leftAttr = resolveJoinKeyAttribute(leftTable, joinPath.getLeftKey());
      DataRef rightAttr = resolveJoinKeyAttribute(rightTable, joinPath.getRightKey());

      if(leftAttr == null) {
         throw new IllegalArgumentException(
            "Left join key '" + joinPath.getLeftKey() + "' not found in table '" + leftTable.getName() + "'");
      }

      if(rightAttr == null) {
         throw new IllegalArgumentException(
            "Right join key '" + joinPath.getRightKey() + "' not found in table '" + rightTable.getName() + "'");
      }

      TableAssemblyOperator.Operator operator = new TableAssemblyOperator.Operator();
      operator.setLeftAttribute(leftAttr);
      operator.setRightAttribute(rightAttr);
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
      WsServiceHelper.initCompositeColumnSelection(table);

      return table;

   }

   private AbstractTableAssembly createJoinTable(Worksheet worksheet, WorksheetConstructionModel.JoinPath joinPath,
                                                 List<WorksheetConstructionModel.QueryField> allFields, Map<String, String> tableMapping) throws Exception
   {
      AbstractTableAssembly leftTable = createJoinBaseTable(worksheet, allFields, joinPath.getLeftTable(), joinPath.getLeftKey(), tableMapping);
      AbstractTableAssembly rightTable = createJoinBaseTable(worksheet, allFields, joinPath.getRightTable(), joinPath.getRightKey(), tableMapping);
      DataRef leftAttr = resolveJoinKeyAttribute(leftTable, joinPath.getLeftKey());
      DataRef rightAttr = resolveJoinKeyAttribute(rightTable, joinPath.getRightKey());

      if(leftAttr == null) {
         throw new IllegalArgumentException(
            "Left join key '" + joinPath.getLeftKey() + "' not found in table '" + leftTable.getName() + "'");
      }

      if(rightAttr == null) {
         throw new IllegalArgumentException(
            "Right join key '" + joinPath.getRightKey() + "' not found in table '" + rightTable.getName() + "'");
      }

      TableAssemblyOperator.Operator operator = new TableAssemblyOperator.Operator();
      operator.setLeftAttribute(leftAttr);
      operator.setRightAttribute(rightAttr);
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
      WsServiceHelper.initCompositeColumnSelection(table);

      return table;
   }

   /**
    * Apply visibility settings from model fields to the composite table's column selection.
    * Fields with visible=false in the model will be set to invisible in the joinTable,
    * but base table visibility is not affected.
    */
   private void applyFieldVisibility(CompositeTableAssembly compositeTable,
                                     List<WorksheetConstructionModel.QueryField> modelFields)
   {
      if(compositeTable == null || modelFields == null) {
         return;
      }

      ColumnSelection columnSelection = compositeTable.getColumnSelection(false);

      if(columnSelection == null) {
         return;
      }

      for(WorksheetConstructionModel.QueryField field : modelFields) {
         if(Boolean.FALSE.equals(field.getVisible())) {
            String fieldName = field.getAlias() != null ? field.getAlias() : field.getUnqualifiedFieldName();
            WorksheetConstructionModel.TableInfo table = field.getTable();
            fieldName = table != null ? table.getName() + "." + fieldName : fieldName;
            DataRef ref = columnSelection.getAttribute(fieldName);

            if(ref instanceof ColumnRef colRef) {
               colRef.setVisible(false);
            }
         }
      }

      compositeTable.setColumnSelection(columnSelection, false);
   }

   private String getTableInfoKey(WorksheetConstructionModel.TableInfo tableInfo) {
      WorksheetConstructionModel.SourceInfo source = tableInfo.getSource();

      return Tool.buildString(source.getType(), source.getPath(), source.getSchema(), source.getCatalog(), tableInfo.getName());
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
         request.setDsName(tableInfo.getSource().getPath());
         request.setCatalog(tableInfo.getSource().getCatalog());
         request.setSchema(tableInfo.getSource().getSchema());
         request.setTableName(tableInfo.getName());
         OsiDataset metaData = metadataApiService.getMetaData(request);

         table = new PhysicalBoundTableAssembly(worksheet, tableInfo.getName());
         applySourceInfo(table, tableInfo);
         Set<WorksheetConstructionModel.QueryField> tableFields = getTableFields(allFields, tableInfo);

         addJoinKeyField(tableFields, tableInfo, joinKey);

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
                                     OsiDataset metaData)
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

   private DataRef convertToDataRef(WorksheetConstructionModel.QueryField field, boolean boundTable, OsiDataset metaData) {
      DataRef dataRef;

      if(Tool.isEmptyString(field.getExpression())) {
         String colType = null;
         String fieldName = null;

         if(boundTable) {
            if(metaData != null && metaData.getFields() != null) {
               Optional<OsiField> osiField = metaData.getFields().stream()
                  .filter(f -> Tool.equals(f.getName(), field.getUnqualifiedFieldName()))
                  .findFirst();

               if(osiField.isPresent()) {
                  fieldName = osiField.get().getName();
                  colType = WsServiceHelper.extractFieldType(objectMapper, osiField.get());
               }
            }
         }

         if(colType == null) {
            colType = field.getType();
         }

         String attr = fieldName;

         if(attr == null) {
            attr = field.getUnqualifiedFieldName();
         }

         attr = AssetUtil.trimEntity(attr, null);
         AttributeRef ref = new AttributeRef(null, attr);
         ref.setDataType(colType);
         dataRef = ref;
      }
      else {
         ExpressionRef expressionRef = new ExpressionRef(null, resolveExpressionName(field));
         expressionRef.setExpression(field.getExpression());
         dataRef = expressionRef;
      }

      ColumnRef columnRef = new ColumnRef(dataRef);

      if(!Tool.isEmptyString(field.getAlias())) {
         columnRef.setAlias(field.getAlias());
      }

      if(!Tool.isEmptyString(field.getDescription())) {
         columnRef.setDescription(field.getDescription());
      }

      return columnRef;
   }

   /**
    * Resolve the name for an expression column: fieldName, falling back to alias.
    * An expression column with neither is unnamed ("Column [0]") and can never be
    * matched by fieldConfigs, so reject it at validation time instead.
    */
   static String resolveExpressionName(WorksheetConstructionModel.QueryField field) {
      String name = !Tool.isEmptyString(field.getFieldName())
         ? field.getFieldName() : field.getAlias();

      if(Tool.isEmptyString(name)) {
         throw new IllegalArgumentException(
            "Expression field requires a fieldName or alias: " + field.getExpression());
      }

      return name;
   }

   private void applySourceInfo(AbstractTableAssembly table,
                                WorksheetConstructionModel.TableInfo selectTable) throws Exception
   {
      if(selectTable == null) {
         return;
      }

      WorksheetConstructionModel.SourceInfo source = selectTable.getSource();
      JDBCDataSource jdbcDatasource = metadataApiService.getJDBCDatasource(source.getPath());
      XNode tableMetaData = metadataApiService.getTableMetaData(jdbcDatasource, source.getCatalog(), source.getSchema(), selectTable.getName());

      if(tableMetaData == null) {
         throw new IllegalArgumentException("Table does not exist: " + selectTable.getName() +
            " (source: " + source.getPath() +
            ", catalog: " + source.getCatalog() +
            ", schema: " + source.getSchema() + ")");
      }

      String qname = SQLTypes.getSQLTypes(jdbcDatasource).
         getQualifiedName(tableMetaData, jdbcDatasource);
      SourceInfo sinfo = new SourceInfo(
         SourceInfo.PHYSICAL_TABLE, source.getPath(), qname);

      sinfo.setProperty(SourceInfo.SCHEMA, source.getSchema());
      sinfo.setProperty(SourceInfo.CATALOG, source.getCatalog());
      sinfo.setProperty(SourceInfo.TABLE_TYPE, (String) tableMetaData.getAttribute("type"));
      table.setSourceInfo(sinfo);
   }

   private void applyCondition(AbstractTableAssembly table,
                               List<WorksheetConstructionModel.Condition> conditions)
   {
      ConditionList conditionList = new ConditionList();
      ColumnSelection columnSelection = table.getColumnSelection();
      boolean appended = false;

      for(WorksheetConstructionModel.Condition filter : conditions) {
         DataRef attributeRef = columnSelection.getAttribute(filter.getField());

         if(attributeRef == null) {
            continue;
         }

         int level = filter.getConditionLevel();

         // A ConditionList must alternate ConditionItem / JunctionOperator entries. Insert the
         // junction joining this condition to the previous one (AND/OR from the model) before the
         // item — without it, StyleBI casts the next ConditionItem as a JunctionOperator and a
         // multi-condition filter fails with a ClassCastException.
         if(appended) {
            int junction = filter.getConditionOperator() == WorksheetConstructionModel.ConditionOperator.OR
               ? JunctionOperator.OR : JunctionOperator.AND;
            conditionList.append(new JunctionOperator(junction, level));
         }

         AssetCondition assetCondition = new AssetCondition();
         String dataType = attributeRef.getDataType();
         assetCondition.setType(dataType);
         // #75457: the filter carries the operation vocabulary used across the wiz condition API
         // (EQUAL_TO/GREATER_THAN/ONE_OF/BETWEEN/...) with an explicit `equal` inclusive-bound flag.
         assetCondition.setOperation(filterOperation(filter.getOperation()));
         assetCondition.setEqual(Boolean.TRUE.equals(filter.getEqual()));
         assetCondition.setNegated(filter.isNegated());
         // #75457: convert each value to the column's declared type (e.g. a date string -> Timestamp)
         // rather than passing a raw string and relying on string-vs-typed coercion at SQL-gen time.
         // A multi-value ONE_OF / BETWEEN carries every element.
         addTypedConditionValue(assetCondition, filter.getValue(), dataType);
         conditionList.append(new ConditionItem(attributeRef, assetCondition, level));
         appended = true;
      }

      table.setPreConditionList(conditionList);
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

   /**
    * Apply aggregate info (GROUP BY and AGGREGATES) to the table assembly.
    * This sets up the grouping fields and aggregate fields in the AggregateInfo.
    *
    * @param table      the table assembly to apply aggregate info to
    * @param groupBy    the list of group by fields
    * @param aggregates the list of aggregate fields for SELECT clause (e.g., MAX(price), AVG(price))
    * @param having     the list of having conditions (used to determine aggregate fields for filtering)
    */
   private void applyAggregateInfo(AbstractTableAssembly table,
                                   List<WorksheetConstructionModel.GroupByField> groupBy,
                                   List<WorksheetConstructionModel.AggregateField> aggregates,
                                   List<WorksheetConstructionModel.HavingCondition> having)
   {
      if((groupBy == null || groupBy.isEmpty()) &&
         (aggregates == null || aggregates.isEmpty()) &&
         (having == null || having.isEmpty()))
      {
         return;
      }

      AggregateInfo aggregateInfo = new AggregateInfo();
      ColumnSelection columnSelection = table.getColumnSelection();

      // Add group by fields
      if(groupBy != null) {
         for(WorksheetConstructionModel.GroupByField groupField : groupBy) {
            DataRef ref = columnSelection.getAttribute(groupField.getFieldName());

            if(!(ref instanceof ColumnRef column)) {
               continue;
            }

            if(groupField.getDateGroupLevel() != null) {
               String colName = column.getName();
               int dgroup = getDateGroupLevel(groupField.getDateGroupLevel());
               String name = DateRangeRef.getName(colName, dgroup);
               DateRangeRef rangeRef = new DateRangeRef(name, column.getDataRef(), dgroup);
               rangeRef.setOriginalType(column.getDataType());
               String dtype = rangeRef.getDataType();
               columnSelection.removeAttribute(column);
               column = new ColumnRef(rangeRef);
               column.setDataType(dtype);
               columnSelection.addAttribute(column);
            }

            GroupRef groupRef = new GroupRef(column);
            aggregateInfo.addGroup(groupRef);
         }
      }

      // Add aggregate fields from the aggregates list (SELECT clause aggregations)
      if(aggregates != null) {
         for(WorksheetConstructionModel.AggregateField aggField : aggregates) {
            DataRef column = columnSelection.getAttribute(aggField.getFieldName());

            if(column == null) {
               continue;
            }

            AggregateFormula formula = AggregateFormula.getFormula(aggField.getFormula());

            if(formula == null) {
               formula = AggregateFormula.SUM;
            }

            // Handle secondary field for two-column formulas (e.g., Correlation, WeightedAverage)
            DataRef secondaryColumn = null;

            if(aggField.getSecondaryField() != null && formula.isTwoColumns()) {
               secondaryColumn = columnSelection.getAttribute(aggField.getSecondaryField());
            }

            AggregateRef aggregateRef = new AggregateRef(column, secondaryColumn, formula);

            // Handle N parameter for Nth formulas (setN is on AggregateRef)
            if(aggField.getN() != null && formula.hasN()) {
               aggregateRef.setN(aggField.getN());
            }

            aggregateInfo.addAggregate(aggregateRef);
         }
      }

      // Add aggregate fields from having conditions (if not already added via aggregates)
      if(having != null) {
         for(WorksheetConstructionModel.HavingCondition havingCond : having) {
            DataRef column = columnSelection.getAttribute(havingCond.getField());

            if(column == null) {
               continue;
            }

            AggregateFormula formula = AggregateFormula.getFormula(havingCond.getAggregateFormula());

            if(formula == null) {
               formula = AggregateFormula.COUNT_ALL;
            }

            // Handle secondary field for two-column formulas (e.g., Correlation, WeightedAverage)
            DataRef secondaryColumn = null;

            if(havingCond.getSecondaryField() != null && formula.isTwoColumns()) {
               secondaryColumn = columnSelection.getAttribute(havingCond.getSecondaryField());
            }

            AggregateRef aggregateRef = new AggregateRef(column, secondaryColumn, formula);

            // Handle N parameter for Nth formulas (setN is on AggregateRef)
            if(havingCond.getN() != null && formula.hasN()) {
               aggregateRef.setN(havingCond.getN());
            }

            // Only add if not already present from aggregates list.
            // Use equalsAggregate() to properly compare AggregateRefs and avoid
            // ClassCastException when CompositeAggregateRef is present.
            boolean aggregateExists = false;

            for(int i = 0; i < aggregateInfo.getAggregateCount(); i++) {
               if(aggregateRef.equalsAggregate(aggregateInfo.getAggregate(i))) {
                  aggregateExists = true;
                  break;
               }
            }

            if(!aggregateExists) {
               aggregateInfo.addAggregate(aggregateRef);
            }
         }
      }

      if(!aggregateInfo.isEmpty()) {
         table.setAggregateInfo(aggregateInfo);
      }
   }

   /**
    * Apply HAVING conditions to the table assembly.
    * HAVING conditions are stored in the post condition list (postconds).
    *
    * @param table  the table assembly to apply having conditions to
    * @param having the list of having conditions
    */
   private void applyHavingCondition(AbstractTableAssembly table,
                                     List<WorksheetConstructionModel.HavingCondition> having)
   {
      if(having == null || having.isEmpty()) {
         return;
      }

      ConditionList conditionList = new ConditionList();
      ColumnSelection columnSelection = table.getColumnSelection();

      for(WorksheetConstructionModel.HavingCondition havingCond : having) {
         DataRef column = columnSelection.getAttribute(havingCond.getField());

         if(column == null) {
            continue;
         }

         AggregateFormula formula = AggregateFormula.getFormula(havingCond.getAggregateFormula());

         if(formula == null) {
            formula = AggregateFormula.COUNT_ALL;
         }

         // Handle secondary field for two-column formulas
         DataRef secondaryColumn = null;

         if(havingCond.getSecondaryField() != null && formula.isTwoColumns()) {
            secondaryColumn = columnSelection.getAttribute(havingCond.getSecondaryField());
         }

         // Create aggregate ref for the condition
         AggregateRef aggregateRef = new AggregateRef(column, secondaryColumn, formula);

         // Handle N parameter for Nth formulas (setN is on AggregateRef)
         if(havingCond.getN() != null && formula.hasN()) {
            aggregateRef.setN(havingCond.getN());
         }

         // Get list of operations (GE/LE require two separate conditions)
         WorksheetConstructionModel.HavingOperator operator = havingCond.getOperator();
         List<Integer> conditionOperators = getHavingConditionOperator(operator);

         // Determine the data type based on the formula result type
         String dataType = formula.getDataType();

         if(dataType == null) {
            dataType = column.getDataType();
         }

         if(dataType == null) {
            dataType = XSchema.DOUBLE;
         }

         // Each HavingCondition carries its own junction (default AND).
         // GE/LE sub-conditions expanded from a single entry are joined with OR within that entry.
         int junctionType = "OR".equalsIgnoreCase(havingCond.getJunction())
            ? JunctionOperator.OR : JunctionOperator.AND;
         boolean firstItemForThisCond = true;

         // Create condition(s) for each operator
         for(Integer op : conditionOperators) {
            // ConditionList requires alternating ConditionItem / JunctionOperator entries.
            // Insert a junction before every item except the very first in the list.
            if(!conditionList.isEmpty()) {
               int junction = firstItemForThisCond ? junctionType : JunctionOperator.OR;
               conditionList.append(new JunctionOperator(junction, 0));
            }

            AssetCondition assetCondition = new AssetCondition();
            addConditionValue(assetCondition, havingCond.getValue());
            assetCondition.setOperation(op);
            assetCondition.setType(dataType);

            // Set negation for NE operator
            if(operator == WorksheetConstructionModel.HavingOperator.NE) {
               assetCondition.setNegated(true);
            }

            conditionList.append(new ConditionItem(aggregateRef, assetCondition, 0));
            firstItemForThisCond = false;
         }
      }

      if(!conditionList.isEmpty()) {
         table.setPostConditionList(conditionList);
      }
   }

   private void addConditionValue(AssetCondition condition, Object value) {
      if(value instanceof List list) {
         for(Object v : list) {
            condition.addValue(v);
         }
      }
      else {
         condition.addValue(value);
      }
   }

   /**
    * #75457: map the wiz filter operation vocabulary (shared with apply_filter / preAggregateCondition)
    * to a StyleBI XCondition operation. The inclusive-bound (>= / <=) case is carried by the separate
    * `equal` flag, not by a distinct operation.
    */
   private int filterOperation(String operation) {
      if(operation == null) {
         throw new IllegalArgumentException("filter operation is required");
      }

      return switch(operation) {
         case "EQUAL_TO" -> XCondition.EQUAL_TO;
         case "ONE_OF" -> XCondition.ONE_OF;
         case "GREATER_THAN" -> XCondition.GREATER_THAN;
         case "LESS_THAN" -> XCondition.LESS_THAN;
         case "BETWEEN" -> XCondition.BETWEEN;
         case "LIKE" -> XCondition.LIKE;
         case "STARTING_WITH" -> XCondition.STARTING_WITH;
         case "CONTAINS" -> XCondition.CONTAINS;
         case "NULL" -> XCondition.NULL;
         case "DATE_IN" -> XCondition.DATE_IN;
         default -> throw new IllegalArgumentException("Unknown filter operation: " + operation);
      };
   }

   /**
    * #75457: add filter value(s) converted to the column's declared type. Every element of a list
    * value is added (so ONE_OF / BETWEEN carry all values).
    */
   private void addTypedConditionValue(AssetCondition condition, Object value, String dataType) {
      if(value instanceof List<?> list) {
         for(Object v : list) {
            condition.addValue(convertConditionValue(v, dataType));
         }
      }
      else if(value != null) {
         condition.addValue(convertConditionValue(value, dataType));
      }
   }

   /**
    * #75457: parse the JSON value (a String over the wire) into the column's declared type using
    * StyleBI's own converter, so a date column gets a Date rather than a raw String — removing the
    * string-vs-typed coercion guesswork at SQL-generation time. Falls back to the raw value when the
    * string can't be parsed for that type (the query engine still coerces it), so this never
    * regresses a value that previously round-tripped.
    */
   private Object convertConditionValue(Object value, String dataType) {
      if(value == null || dataType == null) {
         return value;
      }

      try {
         Object converted = Tool.getData(dataType, value);
         return converted != null ? converted : value;
      }
      catch(Exception ex) {
         return value;
      }
   }

   /**
    * Convert HavingOperator to XCondition operation codes.
    * Returns a list because GE and LE require two separate conditions.
    */
   private List<Integer> getHavingConditionOperator(WorksheetConstructionModel.HavingOperator operator) {
      List<Integer> ops = new ArrayList<>();

      if(operator == null) {
         ops.add(XCondition.EQUAL_TO);
         return ops;
      }

      switch(operator) {
         case GT -> ops.add(XCondition.GREATER_THAN);
         case LT -> ops.add(XCondition.LESS_THAN);
         case GE -> {
            ops.add(XCondition.EQUAL_TO);
            ops.add(XCondition.GREATER_THAN);
         }
         case LE -> {
            ops.add(XCondition.EQUAL_TO);
            ops.add(XCondition.LESS_THAN);
         }
         case NE -> ops.add(XCondition.EQUAL_TO);
         case EQ -> ops.add(XCondition.EQUAL_TO);
      }

      return ops;
   }

   /**
    * Extract all columns from the primary table assembly into a flat list.
    * <p>
    * {@link WorksheetColumnInfo} semantics:
    * <ul>
    *   <li>{@code name} – the <em>underlying</em> DB column name, obtained via
    *       {@code ColumnRef.getDataRef().getAttribute()} to bypass the alias override that
    *       {@code ColumnRef.getAttribute()} applies.  Matched against {@code FieldInfo.name}
    *       in {@code selectFieldMappings} on the TypeScript side.</li>
    *   <li>{@code alias} – the value that {@code ColumnRef.getAttribute()} would return
    *       (alias when one is set, attribute name otherwise).  This is the key that
    *       {@code WizAutoBindingService.autoBinding} uses in its column filter
    *       ({@code configMap.containsKey(c.getAttribute())}).  Set to null when equal
    *       to {@code name} to signal "no aliasing".</li>
    * </ul>
    * {@code alias ?? name} therefore always equals what {@code c.getAttribute()} returns,
    * making it the correct key for the {@code configMap} lookup in {@code WizAutoBindingService.autoBinding}.
    */
   private List<WorksheetColumnInfo> extractPrimaryTableFields(
      Worksheet worksheet,
      AbstractTableAssembly primaryTable,
      WorksheetConstructionModel model)
   {
      // For a physical primary table the DB table name is resolved from the construction model's
      // fields (falling back to the assembly name); WsServiceHelper applies the assembly-name
      // fallback when the override is null.
      String physicalDbTableNameOverride = model.getFields() == null ? null :
         model.getFields().stream()
            .filter(f -> f.getTable() != null)
            .map(f -> f.getTable().getName())
            .findFirst()
            .orElse(null);

      return WsServiceHelper.extractPrimaryTableFields(worksheet, primaryTable, physicalDbTableNameOverride);
   }

   private final ViewsheetService viewsheetService;
   private final MetadataApiService metadataApiService;
   private final InnerJoinService innerJoinService;
   private final LayoutGraphService layoutGraphService;
   private final WsMergeService wsMergeService;
   private final ObjectMapper objectMapper;

   // UUID suffix prevents name collision with user-created folders.
   public static final String WORKSHEET_ROOT_FOLDER_PATH =
      "worksheets-7a3f9c2e-8b5d-4f6a-b1c4-3e7d0a9f2b8c";

   // Saved visualization worksheets — parallel to VISUALIZATION_COMPONENTS_FOLDER_PATH.
   public static final String WORKSHEET_COMPONENTS_FOLDER_PATH =
      "worksheet-components-e5c7a1b2-9f3d-4e8a-b6c0-2d4f8e1a5b9c";
}
