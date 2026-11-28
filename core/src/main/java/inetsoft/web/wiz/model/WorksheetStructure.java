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

import java.util.ArrayList;
import java.util.List;

public class WorksheetStructure {
   private String path;
   private String name;
   private String primaryTable;
   private List<StructureTable> tables = new ArrayList<>();
   // #76519 — only set by getViewsheetStructure: what kind of base the viewsheet actually has
   // ("worksheet" | "logicalModel" | "physicalTable"), and that base entry's own path. null for
   // a plain getWorksheetStructure (worksheet-id) call, which has no viewsheet wrapper to report.
   private String sourceKind;
   private String baseEntryPath;

   public String getPath() { return path; }
   public void setPath(String path) { this.path = path; }
   public String getName() { return name; }
   public void setName(String name) { this.name = name; }
   public String getPrimaryTable() { return primaryTable; }
   public void setPrimaryTable(String primaryTable) { this.primaryTable = primaryTable; }
   public List<StructureTable> getTables() { return tables; }
   public void setTables(List<StructureTable> tables) { this.tables = tables; }
   public String getSourceKind() { return sourceKind; }
   public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
   public String getBaseEntryPath() { return baseEntryPath; }
   public void setBaseEntryPath(String baseEntryPath) { this.baseEntryPath = baseEntryPath; }

   public static class StructureTable {
      private String name;
      private String tableType;
      private List<Column> columns = new ArrayList<>();
      private SourceRef source;               // null unless a bound/sql table
      private List<String> baseTables = new ArrayList<>();
      private List<JoinEdge> joins = new ArrayList<>();
      private List<ConditionLeaf> conditions = new ArrayList<>();
      private AggregateSummary aggregate;     // null unless grouped/aggregated

      public String getName() { return name; }
      public void setName(String name) { this.name = name; }
      public String getTableType() { return tableType; }
      public void setTableType(String tableType) { this.tableType = tableType; }
      public List<Column> getColumns() { return columns; }
      public void setColumns(List<Column> columns) { this.columns = columns; }
      public SourceRef getSource() { return source; }
      public void setSource(SourceRef source) { this.source = source; }
      public List<String> getBaseTables() { return baseTables; }
      public void setBaseTables(List<String> baseTables) { this.baseTables = baseTables; }
      public List<JoinEdge> getJoins() { return joins; }
      public void setJoins(List<JoinEdge> joins) { this.joins = joins; }
      public List<ConditionLeaf> getConditions() { return conditions; }
      public void setConditions(List<ConditionLeaf> conditions) { this.conditions = conditions; }
      public AggregateSummary getAggregate() { return aggregate; }
      public void setAggregate(AggregateSummary aggregate) { this.aggregate = aggregate; }
      private String conditionsCoverage; // sql tables only: full|partial|unavailable
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getConditionsCoverage() { return conditionsCoverage; }
      public void setConditionsCoverage(String v) { this.conditionsCoverage = v; }
      // sql tables only: the aggregate the SQL text itself computes, read from the select list and
      // GROUP BY (never overwrites `aggregate`, which is the table's worksheet AggregateInfo)
      private AggregateSummary sqlAggregate;
      private String sqlAggregateCoverage; // sql tables only: full|partial|unavailable
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public AggregateSummary getSqlAggregate() { return sqlAggregate; }
      public void setSqlAggregate(AggregateSummary v) { this.sqlAggregate = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getSqlAggregateCoverage() { return sqlAggregateCoverage; }
      public void setSqlAggregateCoverage(String v) { this.sqlAggregateCoverage = v; }
   }

   public static class Column {
      private String name; private String alias; private String type;
      private String expression; private int refType; private String description;
      public String getName() { return name; } public void setName(String v) { this.name = v; }
      public String getAlias() { return alias; } public void setAlias(String v) { this.alias = v; }
      public String getType() { return type; } public void setType(String v) { this.type = v; }
      public String getExpression() { return expression; } public void setExpression(String v) { this.expression = v; }
      public int getRefType() { return refType; } public void setRefType(int v) { this.refType = v; }
      public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
   }

   public static class SourceRef {
      private String datasource; private String table; private String sqlText; private int sourceType;
      public String getDatasource() { return datasource; } public void setDatasource(String v) { this.datasource = v; }
      public String getTable() { return table; } public void setTable(String v) { this.table = v; }
      public String getSqlText() { return sqlText; } public void setSqlText(String v) { this.sqlText = v; }
      public int getSourceType() { return sourceType; } public void setSourceType(int v) { this.sourceType = v; }
   }

   public static class JoinEdge {
      private String leftTable; private String rightTable;
      private String leftColumn; private String rightColumn; private String joinType;
      public String getLeftTable() { return leftTable; } public void setLeftTable(String v) { this.leftTable = v; }
      public String getRightTable() { return rightTable; } public void setRightTable(String v) { this.rightTable = v; }
      public String getLeftColumn() { return leftColumn; } public void setLeftColumn(String v) { this.leftColumn = v; }
      public String getRightColumn() { return rightColumn; } public void setRightColumn(String v) { this.rightColumn = v; }
      public String getJoinType() { return joinType; } public void setJoinType(String v) { this.joinType = v; }
   }

   public static class ConditionLeaf {
      private String field; private String operation; private List<String> values = new ArrayList<>();
      private boolean negated; private String junction; private String phase; // pre|post|preRuntime|ranking
      private String of; // ranking only: the aggregate the ranking is ranked by (alias, else column)
      public String getField() { return field; } public void setField(String v) { this.field = v; }
      public String getOperation() { return operation; } public void setOperation(String v) { this.operation = v; }
      public List<String> getValues() { return values; } public void setValues(List<String> v) { this.values = v; }
      public boolean isNegated() { return negated; } public void setNegated(boolean v) { this.negated = v; }
      public String getJunction() { return junction; } public void setJunction(String v) { this.junction = v; }
      public String getPhase() { return phase; } public void setPhase(String v) { this.phase = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getOf() { return of; } public void setOf(String v) { this.of = v; }
      private Boolean equal; // LESS_THAN/GREATER_THAN inclusive (>=, <=) when true
      private String source; // "sql" for leaves parsed from a free-SQL table's text
      private String text; // sql leaves only: the clause as StyleBI renders it
      private Boolean grouped; // true when nested inside a parenthesised group
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public Boolean getEqual() { return equal; } public void setEqual(Boolean v) { this.equal = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getSource() { return source; } public void setSource(String v) { this.source = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getText() { return text; } public void setText(String v) { this.text = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public Boolean getGrouped() { return grouped; } public void setGrouped(Boolean v) { this.grouped = v; }
      // the level of the junction PRECEDING this leaf (null for the first leaf of a phase): StyleBI
      // splits a condition list on the OR junctions of a level, then its AND junctions, and parses a
      // run of deeper junctions as one group (ConditionListHandler)
      private Integer junctionLevel;
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public Integer getJunctionLevel() { return junctionLevel; } public void setJunctionLevel(Integer v) { this.junctionLevel = v; }
      // the column of this table/assembly the condition is on, as it exposes it (alias); only when it differs from `field`
      private String column;
      // every non-literal value (sub-query, field, variable, expression), in value order; null when none
      private List<Operand> operands;
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getColumn() { return column; } public void setColumn(String v) { this.column = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public List<Operand> getOperands() { return operands; } public void setOperands(List<Operand> v) { this.operands = v; }
   }

   public static class Operand {
      private String kind; private String text; private String language;
      public Operand() {}
      public Operand(String kind, String text, String language) { this.kind = kind; this.text = text; this.language = language; }
      public String getKind() { return kind; } public void setKind(String v) { this.kind = v; }
      public String getText() { return text; } public void setText(String v) { this.text = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getLanguage() { return language; } public void setLanguage(String v) { this.language = v; }
   }

   public static class AggregateSummary {
      private List<AggGroupBy> groupBy = new ArrayList<>();
      private List<AggMeasure> aggregates = new ArrayList<>();
      public List<AggGroupBy> getGroupBy() { return groupBy; } public void setGroupBy(List<AggGroupBy> v) { this.groupBy = v; }
      public List<AggMeasure> getAggregates() { return aggregates; } public void setAggregates(List<AggMeasure> v) { this.aggregates = v; }
   }

   public static class AggGroupBy {
      private String field; private String dateLevel;
      public String getField() { return field; } public void setField(String v) { this.field = v; }
      public String getDateLevel() { return dateLevel; } public void setDateLevel(String v) { this.dateLevel = v; }
   }

   public static class AggMeasure {
      private String field; private String formula; private String secondaryField; private String n;
      public String getField() { return field; } public void setField(String v) { this.field = v; }
      public String getFormula() { return formula; } public void setFormula(String v) { this.formula = v; }
      public String getSecondaryField() { return secondaryField; } public void setSecondaryField(String v) { this.secondaryField = v; }
      public String getN() { return n; } public void setN(String v) { this.n = v; }
      private String column; // sqlAggregate only: the output (worksheet) column this item produces
      private String expression; // sqlAggregate only: the item text when it is not one aggregate call
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getColumn() { return column; } public void setColumn(String v) { this.column = v; }
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getExpression() { return expression; } public void setExpression(String v) { this.expression = v; }
      private String sourceField; // worksheet aggregate only: the underlying column read, when it differs from `field`
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
      public String getSourceField() { return sourceField; } public void setSourceField(String v) { this.sourceField = v; }
   }
}
