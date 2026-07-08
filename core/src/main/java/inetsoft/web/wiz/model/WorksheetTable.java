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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.*;

import java.util.List;

/**
 * One table definition within a POST /api/wiz/ws/table batch request.
 * <p>
 * Each entry describes exactly one table assembly to add to the worksheet
 * identified by {@code WorksheetTableRequest.worksheetId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorksheetTable {

   // ─── Table identity ───────────────────────────────────────────────────────

   private String tableName;

   /**
    * "physical table" | "mirror table" | "relational join table"
    * (constant strings from StyleBI's TableType enum)
    */
   private String tableType;

   private String description;

   // ─── Physical-table fields ────────────────────────────────────────────────

   private PhysicalSource physicalSource;
   /** Explicit column selection for physical tables; null = include all columns. */
   private List<ColumnInfo> columns;
   /** Expression columns (only valid on non-aggregated mirror tables). */
   private List<ExpressionColumnInfo> expressionColumns;

   // ─── SQL-query-table field ────────────────────────────────────────────────

   /**
    * Raw SQL SELECT for {@code tableType == "sql query table"}. Bound as a
    * {@link inetsoft.uql.asset.SQLBoundTableAssembly} against
    * {@code physicalSource.datasourcePath}, so window functions / CTEs / any
    * dialect SQL execute directly on the database. Other tables can join/mirror it.
    */
   private String sqlExpression;

   // ─── Mirror / join base tables ────────────────────────────────────────────

   /** Names of already-created tables in this worksheet to use as bases. */
   private List<String> baseTables;
   /** Join definitions (required for relational join tables). */
   private List<JoinPathInfo> joinPaths;
   private boolean asPrimaryTable;

   // ─── Aggregation ─────────────────────────────────────────────────────────

   private AggregateInfo aggregateInfo;

   // ─── Conditions ──────────────────────────────────────────────────────────
   // Flat ConditionList format: each item carries conditionLevel (depth) and
   // junction (AND/OR link to the preceding item), produced by the wiz-services
   // condition-tree normaliser from the LLM-facing WorksheetConditionNode tree.

   /** WHERE-equivalent: applied before GROUP BY. */
   private List<ConditionItem> preAggregateCondition;
   /** HAVING-equivalent: applied after GROUP BY. */
   private List<ConditionItem> postAggregateCondition;
   /** Top / bottom-N ranking filter, applied last. */
   private List<ConditionItem> rankingCondition;

   // ─── Getters / setters ────────────────────────────────────────────────────

   public String getTableName() { return tableName; }
   public void setTableName(String tableName) { this.tableName = tableName; }

   public String getTableType() { return tableType; }
   public void setTableType(String tableType) { this.tableType = tableType; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public PhysicalSource getPhysicalSource() { return physicalSource; }
   public void setPhysicalSource(PhysicalSource physicalSource) { this.physicalSource = physicalSource; }

   public List<ColumnInfo> getColumns() { return columns; }
   public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }

   public List<ExpressionColumnInfo> getExpressionColumns() { return expressionColumns; }
   public void setExpressionColumns(List<ExpressionColumnInfo> expressionColumns) { this.expressionColumns = expressionColumns; }

   public String getSqlExpression() { return sqlExpression; }
   public void setSqlExpression(String sqlExpression) { this.sqlExpression = sqlExpression; }

   public List<String> getBaseTables() { return baseTables; }
   public void setBaseTables(List<String> baseTables) { this.baseTables = baseTables; }

   public List<JoinPathInfo> getJoinPaths() { return joinPaths; }
   public void setJoinPaths(List<JoinPathInfo> joinPaths) { this.joinPaths = joinPaths; }

   public boolean isAsPrimaryTable() { return asPrimaryTable; }
   public void setAsPrimaryTable(boolean asPrimaryTable) { this.asPrimaryTable = asPrimaryTable; }

   public AggregateInfo getAggregateInfo() { return aggregateInfo; }
   public void setAggregateInfo(AggregateInfo aggregateInfo) { this.aggregateInfo = aggregateInfo; }

   public List<ConditionItem> getPreAggregateCondition() { return preAggregateCondition; }
   public void setPreAggregateCondition(List<ConditionItem> c) { this.preAggregateCondition = c; }

   public List<ConditionItem> getPostAggregateCondition() { return postAggregateCondition; }
   public void setPostAggregateCondition(List<ConditionItem> c) { this.postAggregateCondition = c; }

   public List<ConditionItem> getRankingCondition() { return rankingCondition; }
   public void setRankingCondition(List<ConditionItem> c) { this.rankingCondition = c; }

   // ─── Nested: physical source ──────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class PhysicalSource {
      private String datasourcePath;
      private String schema;
      private String tableName;
      private String catalog;

      public String getDatasourcePath() { return datasourcePath; }
      public void setDatasourcePath(String datasourcePath) { this.datasourcePath = datasourcePath; }
      public String getSchema() { return schema; }
      public void setSchema(String schema) { this.schema = schema; }
      public String getTableName() { return tableName; }
      public void setTableName(String tableName) { this.tableName = tableName; }
      public String getCatalog() { return catalog; }
      public void setCatalog(String catalog) { this.catalog = catalog; }
   }

   // ─── Nested: column info ─────────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ColumnInfo {
      private String name;
      private String alias;
      private String description;
      private String type;
      private Boolean visible;

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }
      public String getAlias() { return alias; }
      public void setAlias(String alias) { this.alias = alias; }
      public String getDescription() { return description; }
      public void setDescription(String description) { this.description = description; }
      public String getType() { return type; }
      public void setType(String type) { this.type = type; }
      public Boolean getVisible() { return visible; }
      public void setVisible(Boolean visible) { this.visible = visible; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ExpressionColumnInfo extends ColumnInfo {
      /** JavaScript expression. Reference worksheet columns as field['TableName.col']. */
      private String expression;
      /** true = SQL expression inlined into the query; false (default) = JavaScript expression. */
      private boolean sql = false;

      public String getExpression() { return expression; }
      public void setExpression(String expression) { this.expression = expression; }
      public boolean isSql() { return sql; }
      public void setSql(boolean sql) { this.sql = sql; }
   }

   // ─── Nested: join path ────────────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class JoinPathInfo {
      private String leftTable;
      private String leftKey;
      private String rightTable;
      private String rightKey;
      /** "inner" | "left" | "right" | "full" | "cross" */
      private String joinType;
      /** "=" | ">" | "<" | ">=" | "<=" | "<>" */
      private String joinOperator;

      public String getLeftTable() { return leftTable; }
      public void setLeftTable(String leftTable) { this.leftTable = leftTable; }
      public String getLeftKey() { return leftKey; }
      public void setLeftKey(String leftKey) { this.leftKey = leftKey; }
      public String getRightTable() { return rightTable; }
      public void setRightTable(String rightTable) { this.rightTable = rightTable; }
      public String getRightKey() { return rightKey; }
      public void setRightKey(String rightKey) { this.rightKey = rightKey; }
      public String getJoinType() { return joinType; }
      public void setJoinType(String joinType) { this.joinType = joinType; }
      public String getJoinOperator() { return joinOperator; }
      public void setJoinOperator(String joinOperator) { this.joinOperator = joinOperator; }
   }

   // ─── Nested: aggregate info ───────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class AggregateInfo {
      private List<GroupByFieldInfo> groups;
      private List<AggregateFieldInfo> aggregates;

      public List<GroupByFieldInfo> getGroups() { return groups; }
      public void setGroups(List<GroupByFieldInfo> groups) { this.groups = groups; }
      public List<AggregateFieldInfo> getAggregates() { return aggregates; }
      public void setAggregates(List<AggregateFieldInfo> aggregates) { this.aggregates = aggregates; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class GroupByFieldInfo {
      private String fieldName;
      private String dateGroupLevel;

      public String getFieldName() { return fieldName; }
      public void setFieldName(String fieldName) { this.fieldName = fieldName; }
      public String getDateGroupLevel() { return dateGroupLevel; }
      public void setDateGroupLevel(String dateGroupLevel) { this.dateGroupLevel = dateGroupLevel; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class AggregateFieldInfo {
      private String fieldName;
      /** "Sum" | "Count" | "Average" | "Max" | "Min" | "DistinctCount" | etc. */
      private String formula;
      private String alias;
      private String secondaryField;
      private Integer n;

      public String getFieldName() { return fieldName; }
      public void setFieldName(String fieldName) { this.fieldName = fieldName; }
      public String getFormula() { return formula; }
      public void setFormula(String formula) { this.formula = formula; }
      public String getAlias() { return alias; }
      public void setAlias(String alias) { this.alias = alias; }
      public String getSecondaryField() { return secondaryField; }
      public void setSecondaryField(String secondaryField) { this.secondaryField = secondaryField; }
      public Integer getN() { return n; }
      public void setN(Integer n) { this.n = n; }
   }

   // ─── Nested: flat condition item ──────────────────────────────────────────

   /**
    * One entry in a flat ConditionList, mirroring StyleBI's ConditionList.java format.
    * <p>
    * The wiz-services condition-tree normaliser converts the LLM-facing
    * {@code WorksheetConditionNode} tree into this flat representation before
    * posting to {@code /api/wiz/ws/table}.
    * <ul>
    *   <li>{@code conditionLevel} — nesting depth of the condition itself (0 = top-level).</li>
    *   <li>{@code junction} — logical operator connecting this item to the preceding item;
    *       {@code null} for the first item.</li>
    *   <li>{@code conditionJunctionLevel} — the level at which the {@link JunctionOperator}
    *       should be emitted.  Equals {@code conditionLevel} for same-level siblings but is
    *       {@code conditionLevel - 1} when this item is the first element of a group that is
    *       itself a sibling of the preceding group (i.e. the junction connects two groups at
    *       the outer level, not two leaves within a group).</li>
    * </ul>
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionItem {
      /** Nesting depth of the condition: 0 = top-level, 1 = inside a group, … */
      private int conditionLevel;
      /** "and" | "or" — links this item to the preceding item. Null for the first item. */
      private String junction;
      /**
       * Level at which the junction operator is inserted into the {@link ConditionList}.
       * Differs from {@code conditionLevel} when this item is the first element of a group
       * that is itself a sibling (the junction operator belongs at the outer level).
       * Falls back to {@code conditionLevel} when absent.
       */
      private Integer conditionJunctionLevel;

      // ── Condition payload ────────────────────────────────────────────────
      private String field;
      /**
       * "EQUAL_TO" | "ONE_OF" | "LESS_THAN" | "GREATER_THAN" | "BETWEEN" |
       * "STARTING_WITH" | "CONTAINS" | "LIKE" | "NULL" | "DATE_IN"
       * — for preAggregateCondition / postAggregateCondition.
       *
       * "TOP_N" | "BOTTOM_N"
       * — for rankingCondition only; paired with a single VALUE (integer N).
       */
      private String operation;
      private boolean negated;
      /** For LESS_THAN / GREATER_THAN: true → ≤ / ≥ (inclusive). */
      private Boolean equal;
      private String dateGroupLevel;
      /** For HAVING-style conditions: the aggregate function applied to the field. */
      private String aggregateFormula;
      private String secondaryField;
      private Integer nOrP;
      private List<WorksheetConditionValue> values;

      public int getConditionLevel() { return conditionLevel; }
      public void setConditionLevel(int conditionLevel) { this.conditionLevel = conditionLevel; }
      public String getJunction() { return junction; }
      public void setJunction(String junction) { this.junction = junction; }
      public Integer getConditionJunctionLevel() { return conditionJunctionLevel; }
      public void setConditionJunctionLevel(Integer conditionJunctionLevel) { this.conditionJunctionLevel = conditionJunctionLevel; }
      /** Returns the junction level, falling back to conditionLevel when not set. */
      public int resolveJunctionLevel() {
         return conditionJunctionLevel != null ? conditionJunctionLevel : conditionLevel;
      }
      public String getField() { return field; }
      public void setField(String field) { this.field = field; }
      public String getOperation() { return operation; }
      public void setOperation(String operation) { this.operation = operation; }
      public boolean isNegated() { return negated; }
      public void setNegated(boolean negated) { this.negated = negated; }
      public Boolean getEqual() { return equal; }
      public void setEqual(Boolean equal) { this.equal = equal; }
      public String getDateGroupLevel() { return dateGroupLevel; }
      public void setDateGroupLevel(String dateGroupLevel) { this.dateGroupLevel = dateGroupLevel; }
      public String getAggregateFormula() { return aggregateFormula; }
      public void setAggregateFormula(String aggregateFormula) { this.aggregateFormula = aggregateFormula; }
      public String getSecondaryField() { return secondaryField; }
      public void setSecondaryField(String secondaryField) { this.secondaryField = secondaryField; }
      public Integer getNOrP() { return nOrP; }
      public void setNOrP(Integer nOrP) { this.nOrP = nOrP; }
      public List<WorksheetConditionValue> getValues() { return values; }
      public void setValues(List<WorksheetConditionValue> values) { this.values = values; }
   }

   // ─── Nested: condition value ──────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class WorksheetConditionValue {
      /** "VALUE" | "FIELD" | "EXPRESSION" | "SESSION_DATA" | "SUBQUERY" */
      private String type;
      /** Operand for VALUE, FIELD, EXPRESSION, SESSION_DATA. */
      private Object value;
      /** Operand for SUBQUERY. */
      private SubQueryInfo subQuery;

      public String getType() { return type; }
      public void setType(String type) { this.type = type; }
      public Object getValue() { return value; }
      public void setValue(Object value) { this.value = value; }
      public SubQueryInfo getSubQuery() { return subQuery; }
      public void setSubQuery(SubQueryInfo subQuery) { this.subQuery = subQuery; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class SubQueryInfo {
      /** Name of an already-created worksheet table. */
      private String subQueryName;
      /** Column in that table whose value serves as the operand. */
      private String inSubQueryColumn;
      /**
       * Correlated match: per-row filter on the subquery.
       * Omit for a global scalar subquery (single-row result).
       */
      private SubQueryWhere where;

      public String getSubQueryName() { return subQueryName; }
      public void setSubQueryName(String subQueryName) { this.subQueryName = subQueryName; }
      public String getInSubQueryColumn() { return inSubQueryColumn; }
      public void setInSubQueryColumn(String inSubQueryColumn) { this.inSubQueryColumn = inSubQueryColumn; }
      public SubQueryWhere getWhere() { return where; }
      public void setWhere(SubQueryWhere where) { this.where = where; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class SubQueryWhere {
      private String subQueryColumn;
      private String currentTableColumn;

      public String getSubQueryColumn() { return subQueryColumn; }
      public void setSubQueryColumn(String subQueryColumn) { this.subQueryColumn = subQueryColumn; }
      public String getCurrentTableColumn() { return currentTableColumn; }
      public void setCurrentTableColumn(String currentTableColumn) { this.currentTableColumn = currentTableColumn; }
   }
}
