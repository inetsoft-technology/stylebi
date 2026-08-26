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
import java.util.Map;

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
   /**
    * Expression (derived) columns. Honored on {@code physical table} and on a {@code mirror table}
    * that does NOT aggregate. Not applied on an aggregating table, a {@code relational join table},
    * or a {@code sql query table} (write the expression into {@code sqlExpression} instead);
    * rejected outright on a {@code tabular table}. In every unsupported case the way to get a
    * derived column is a mirror table over that table, in a later call.
    */
   private List<ExpressionColumnInfo> expressionColumns;
   /** Structured window (analytic) function columns, e.g. ROW_NUMBER/RANK/NTILE/LAG/SUM OVER(...). */
   private List<WindowColumnInfo> windowColumns;

   // ─── SQL-query-table field ────────────────────────────────────────────────

   /**
    * Raw SQL SELECT for {@code tableType == "sql query table"}. Bound as a
    * {@link inetsoft.uql.asset.SQLBoundTableAssembly} against
    * {@code physicalSource.datasourcePath}, so window functions / CTEs / any
    * dialect SQL execute directly on the database. Other tables can join/mirror it.
    */
   private String sqlExpression;

   // ─── Tabular-table field ──────────────────────────────────────────────────

   /**
    * Source of a {@code tableType == "tabular table"}: one endpoint of a SaaS/REST connector,
    * plus the values for that endpoint's parameters.
    *
    * <p>Separate from {@link PhysicalSource} rather than an overload of it, because the two name
    * different things. A physical source names a table that already exists with a known column
    * list; a tabular source names an API CALL, and its column list does not exist until the call
    * has been made — which is why {@code columns} is not honored for this table type, and why the
    * response's column list is the first time anyone learns what the table holds.</p>
    */
   private TabularSource tabularSource;

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

   public TabularSource getTabularSource() { return tabularSource; }
   public void setTabularSource(TabularSource tabularSource) { this.tabularSource = tabularSource; }

   public List<ColumnInfo> getColumns() { return columns; }
   public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }

   public List<ExpressionColumnInfo> getExpressionColumns() { return expressionColumns; }
   public void setExpressionColumns(List<ExpressionColumnInfo> expressionColumns) { this.expressionColumns = expressionColumns; }

   public List<WindowColumnInfo> getWindowColumns() { return windowColumns; }
   public void setWindowColumns(List<WindowColumnInfo> windowColumns) { this.windowColumns = windowColumns; }

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

   // ─── Nested: tabular source ───────────────────────────────────────────────

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class TabularSource {
      private String datasourcePath;
      private String targetKind;
      private String target;
      private Map<String, String> params;
      private Map<String, String> parameters;
      private Map<String, Object> queryParams;
      private String jsonPath;
      private Boolean expanded;
      private String expandedPath;
      private Integer maxRows;
      private Integer sampleRows;
      private List<String> lookup;
      private Boolean lookupExpandArrays;
      private Boolean lookupTopLevelOnly;

      /** Full repository path of the connector INSTANCE, e.g. "SaaS/Stripe Prod". */
      public String getDatasourcePath() { return datasourcePath; }
      public void setDatasourcePath(String datasourcePath) { this.datasourcePath = datasourcePath; }

      /**
       * Which KIND of thing {@link #getTarget()} names, and therefore which contract the rest of
       * this object has to satisfy:
       *
       * <ul>
       *   <li>{@code "endpoint"} — a SaaS/REST connector's endpoint. {@code target} is the endpoint
       *       name and {@code parameters}/{@code jsonPath}/{@code expanded}/{@code expandedPath}
       *       apply.</li>
       *   <li>{@code "file"} — a path-addressed connector's file (ServerFile today). {@code target}
       *       is the path RELATIVE to the connector's root folder, optionally suffixed
       *       {@code "#<sheet>"} for a workbook, and {@code params} carries the parsing options.</li>
       * </ul>
       *
       * <p>Omitted means {@code "endpoint"}. That is not a default chosen for convenience: endpoint
       * was the only kind this object could express before {@code target} existed, so a request
       * that does not mention a kind cannot mean anything else. Matched case-insensitively; an
       * unrecognized value is refused by name rather than falling through to one of the two, which
       * would build a table against a contract the caller did not ask for.</p>
       */
      public String getTargetKind() { return targetKind; }
      public void setTargetKind(String targetKind) { this.targetKind = targetKind; }

      /**
       * WHAT to bind, read according to {@link #getTargetKind()}.
       *
       * <p>For {@code "endpoint"}: the connector's own name for the endpoint, e.g. {@code "Charges"}.
       * Matched exactly against the connector's endpoint map, which is keyed by that name and
       * rejects duplicates ({@code EndpointJsonQuery.Endpoints.toMap}).</p>
       *
       * <p>For {@code "file"}: the path relative to the connector's root folder, e.g.
       * {@code "2024/q1.csv"} — never absolute, and never containing {@code ".."}, because the root
       * folder is the whole of what the data source grants access to. A workbook may name its sheet
       * with a {@code "#"} suffix ({@code "2024/sales.xlsx#Q1"}), which is the same identity the
       * annotation stores for the table, so the two cannot drift apart.</p>
       *
       * <p>{@code endpoint} is accepted as an alias so a caller written against the pre-generalized
       * shape still binds — it named the same thing.</p>
       */
      @JsonAlias("endpoint")
      public String getTarget() { return target; }
      public void setTarget(String target) { this.target = target; }

      /**
       * Connector parsing options for {@code targetKind == "file"}, by the connector's own property
       * name — {@code excelSheet}, {@code encoding}, {@code delimiter}, {@code tab},
       * {@code headerColumnCount}, {@code firstRowHeader}, {@code removeQuotation},
       * {@code unpivotData} for ServerFile. Omitted keys keep the connector's own default, which is
       * a working default for a well-formed CSV.
       *
       * <p>Validated against the connector's declared properties rather than a fixed list, so a
       * name the connector does not have is refused with the names it does have — the same stance
       * {@code parameters} takes for an endpoint, and for the same reason: a dropped option parses
       * the file DIFFERENTLY and still reports success.</p>
       *
       * <p>Distinct from {@link #getParameters()}, which carries URL-suffix values for an endpoint.
       * The two are never both applicable, and supplying the wrong one for the kind is refused.</p>
       */
      public Map<String, String> getParams() { return params; }
      public void setParams(Map<String, String> params) { this.params = params; }

      /**
       * Values by parameter NAME — the name part of a <code>{...}</code> token in the endpoint's URL
       * suffix template, which is what {@code RestParameter.getName()} answers. A name this endpoint
       * does not declare is rejected rather than ignored: silently dropping it would run a request
       * narrower than the one that was asked for and report success.
       */
      public Map<String, String> getParameters() { return parameters; }
      public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

      /**
       * The whole query, by the connector's own property names, for {@code targetKind == "query"}.
       *
       * <p>Where {@link #getParams()} and {@link #getParameters()} each carry one slice of one kind
       * of connector, this carries all of any connector: the names and the values come from the
       * parameter contract {@code GET /api/wiz/tabular/query-schema} publishes for this data source,
       * which is derived from the connector's own {@code @Property} declarations. That is what lets
       * a connector with neither endpoints nor files — a document store, a cloud analytics API, a
       * search index — be bound at all, and it is where the two older kinds are headed. Until they
       * move they are untouched and keep their own contracts.</p>
       *
       * <p>TYPED, unlike the two older maps. A parameter can be a number, a flag, or one of a fixed
       * set of names, and the schema says which. Declaring everything a string would put the
       * conversion in the caller and, worse, make a wrong guess indistinguishable from a value the
       * connector chose to ignore.</p>
       *
       * <p>Three things are checked before the query runs, because each of them otherwise fails
       * quietly: a name the connector does not declare, a value that does not reach the bean, and a
       * parameter that does not apply to the rest of what was sent.</p>
       */
      public Map<String, Object> getQueryParams() { return queryParams; }
      public void setQueryParams(Map<String, Object> queryParams) { this.queryParams = queryParams; }

      /** JSON path to the row array, e.g. "$.data[*]". Null keeps the connector's default. */
      public String getJsonPath() { return jsonPath; }
      public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

      public Boolean getExpanded() { return expanded; }
      public void setExpanded(Boolean expanded) { this.expanded = expanded; }
      public String getExpandedPath() { return expandedPath; }
      public void setExpandedPath(String expandedPath) { this.expandedPath = expandedPath; }

      /**
       * Row cap for this query, persisted ON THE QUERY so EVERY later execution is bounded — not
       * just the column-discovery one. Required in practice: {@code createTables} sets
       * {@code designMaxRows = 0} (unlimited) for wiz analytics, which on a paginated metered API
       * means paging to the end of the customer's data on every render.
       */
      public Integer getMaxRows() { return maxRows; }
      public void setMaxRows(Integer maxRows) { this.maxRows = maxRows; }

      /**
       * How many rows of DATA to report back in {@code WorksheetTableResponse.sampleRows}. Null or
       * 0 reports none, which is the default: the rows are of no use to a caller that only needed
       * the column list, and they are paid for in that caller's response.
       *
       * <p>NOT a row cap and unrelated to {@link #getMaxRows}. This bounds what is REPORTED from the
       * one request the build already makes; maxRows bounds what every later render FETCHES.</p>
       *
       * <p>Capped by {@code rest.sample.rows}, so a request larger than the deployment allows is
       * clamped rather than refused, and a deployment that sets it to 0 returns no rows whatever is
       * asked for here.</p>
       */
      public Integer getSampleRows() { return sampleRows; }
      public void setSampleRows(Integer sampleRows) { this.sampleRows = sampleRows; }

      /**
       * Ordered "Join With" lookup chain to graft onto the endpoint named by {@link #getTarget()}
       * (only meaningful for {@code targetKind == "endpoint"}), one pre-built connector lookup
       * name per nesting level, e.g. {@code ["Issue Event"]}, or
       * {@code ["Repositories", "Contributors"]} for a two-level chain. Each name must be one of
       * the CURRENT position's valid choices, which the connector's own endpoint catalogue
       * declares. Max depth 5.
       */
      public List<String> getLookup() { return lookup; }
      public void setLookup(List<String> lookup) { this.lookup = lookup; }

      /**
       * Only meaningful with {@link #getLookup()}: whether the LAST lookup's matched array
       * expands into extra rows. Null keeps the connector's own default ({@code true}).
       */
      public Boolean getLookupExpandArrays() { return lookupExpandArrays; }
      public void setLookupExpandArrays(Boolean lookupExpandArrays) {
         this.lookupExpandArrays = lookupExpandArrays;
      }

      /**
       * Only meaningful with {@link #getLookup()} and {@link #getLookupExpandArrays()}: whether
       * only the top-level array is expanded. Null keeps the connector's own default
       * ({@code true}).
       */
      public Boolean getLookupTopLevelOnly() { return lookupTopLevelOnly; }
      public void setLookupTopLevelOnly(Boolean lookupTopLevelOnly) {
         this.lookupTopLevelOnly = lookupTopLevelOnly;
      }
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

   // ─── Nested: window (analytic) function column ───────────────────────────

   /**
    * Structured window (analytic) function column, e.g.
    * {@code ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)}. Consumed by
    * {@code WorksheetTableService.applyWindowColumns}, which builds a
    * {@link inetsoft.uql.asset.WindowExpressionRef}-backed column — the structured
    * counterpart of {@link ExpressionColumnInfo}'s free-text {@code sql:true} expression.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class WindowColumnInfo {
      private String name;
      /** Window function name, e.g. "ROW_NUMBER", "RANK", "NTILE", "LAG", "LEAD", "SUM". */
      private String fn;
      /** Column argument for LAG/LEAD/SUM/AVG/COUNT/MIN/MAX/FIRST_VALUE; omit for ROW_NUMBER/RANK/etc. */
      private String column;
      /** NTILE bucket count, or LAG/LEAD offset; omit/`<= 0` means unspecified. */
      private Integer n;
      /** PARTITION BY column names, in order; may be omitted/empty. */
      private List<String> partitionBy;
      /** ORDER BY clauses, in order; may be omitted/empty. */
      private List<OrderByInfo> orderBy;
      private String type;
      /** Business meaning of this window column (english); persisted onto the output column. */
      private String description;
      /** ROWS frame, e.g. {@code 2 PRECEDING .. CURRENT ROW}; omit for the function's default. */
      private WindowFrameInfo frame;

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }
      public String getFn() { return fn; }
      public void setFn(String fn) { this.fn = fn; }
      public String getColumn() { return column; }
      public void setColumn(String column) { this.column = column; }
      public Integer getN() { return n; }
      public void setN(Integer n) { this.n = n; }
      public List<String> getPartitionBy() { return partitionBy; }
      public void setPartitionBy(List<String> partitionBy) { this.partitionBy = partitionBy; }
      public List<OrderByInfo> getOrderBy() { return orderBy; }
      public void setOrderBy(List<OrderByInfo> orderBy) { this.orderBy = orderBy; }
      public String getType() { return type; }
      public void setType(String type) { this.type = type; }
      public String getDescription() { return description; }
      public void setDescription(String description) { this.description = description; }
      public WindowFrameInfo getFrame() { return frame; }
      public void setFrame(WindowFrameInfo frame) { this.frame = frame; }
   }

   /**
    * Structured frame for a {@link WindowColumnInfo}, e.g.
    * {@code {"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"}} for
    * {@code ROWS BETWEEN 2 PRECEDING AND CURRENT ROW}.
    * <p>
    * Bound values: {@code UNBOUNDED_PRECEDING} | {@code PRECEDING} | {@code CURRENT_ROW} |
    * {@code FOLLOWING} | {@code UNBOUNDED_FOLLOWING}. {@code startOffset}/{@code endOffset} are
    * required (and must be positive) when the corresponding bound is {@code PRECEDING} or
    * {@code FOLLOWING}; ignored otherwise.
    * <p>
    * {@code mode}: {@code ROWS} | {@code RANGE} | {@code GROUPS}; {@code null} defaults to
    * {@code ROWS} (Phase 3 behavior, byte-parity). {@code offsetUnit}: a date interval unit
    * (e.g. {@code "day"}) for a date-valued {@code RANGE} frame's {@code PRECEDING}/
    * {@code FOLLOWING} offset; {@code null} for a bare-integer offset (ROWS, GROUPS, and
    * numeric RANGE frames) — see {@code WorksheetTableService.applyWindowColumns} for the
    * validation rules.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class WindowFrameInfo {
      private String startBound;
      private Integer startOffset;
      private String endBound;
      private Integer endOffset;
      private String mode;
      private String offsetUnit;

      public String getStartBound() { return startBound; }
      public void setStartBound(String startBound) { this.startBound = startBound; }
      public Integer getStartOffset() { return startOffset; }
      public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }
      public String getEndBound() { return endBound; }
      public void setEndBound(String endBound) { this.endBound = endBound; }
      public Integer getEndOffset() { return endOffset; }
      public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }
      public String getMode() { return mode; }
      public void setMode(String mode) { this.mode = mode; }
      public String getOffsetUnit() { return offsetUnit; }
      public void setOffsetUnit(String offsetUnit) { this.offsetUnit = offsetUnit; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class OrderByInfo {
      private String field;
      /** "ASC" | "DESC" (default DESC when unrecognized, matching StyleBI's descending-default sort UX). */
      private String direction;

      public String getField() { return field; }
      public void setField(String field) { this.field = field; }
      public String getDirection() { return direction; }
      public void setDirection(String direction) { this.direction = direction; }
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
      /**
       * Output-column name for this group, mirroring AggregateFieldInfo.alias.
       *
       * Matters most for a dateGroupLevel group: without an alias its output name is the RENDERED
       * expression DateRangeRef produces ("Month(T.due_date)"), which is not a SQL alias and therefore
       * cannot be referenced from a downstream sql:true expression column at all — the canonical
       * COALESCE(left_key, right_key) over a FULL join had no way to name its own join keys.
       */
      private String alias;
      /** Business meaning of the group-by output column (english); persisted onto the output column. */
      private String description;

      public String getFieldName() { return fieldName; }
      public void setFieldName(String fieldName) { this.fieldName = fieldName; }
      public String getDateGroupLevel() { return dateGroupLevel; }
      public void setDateGroupLevel(String dateGroupLevel) { this.dateGroupLevel = dateGroupLevel; }
      public String getAlias() { return alias; }
      public void setAlias(String alias) { this.alias = alias; }
      public String getDescription() { return description; }
      public void setDescription(String description) { this.description = description; }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class AggregateFieldInfo {
      private String fieldName;
      /** "Sum" | "Count" | "Average" | "Max" | "Min" | "DistinctCount" | etc. */
      private String formula;
      private String alias;
      private String secondaryField;
      private Integer n;
      /** Business meaning of the aggregated output column (english); persisted onto the output column. */
      private String description;

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
      public String getDescription() { return description; }
      public void setDescription(String description) { this.description = description; }
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
