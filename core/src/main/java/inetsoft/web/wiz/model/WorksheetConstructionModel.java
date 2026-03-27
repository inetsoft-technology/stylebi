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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorksheetConstructionModel {
   private String name; // ws table name
   private List<QueryField> fields;
   private List<JoinPath> joinPaths;
   private List<TableSetOperation> tableSetOperations;
   private List<Condition> filters;
   private List<OrderByInfo> orderBy;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<QueryField> getFields() {
      return fields;
   }

   public void setFields(List<QueryField> fields) {
      this.fields = fields;
   }

   public List<JoinPath> getJoinPaths() {
      return joinPaths;
   }

   public void setJoinPaths(List<JoinPath> joinPaths) {
      this.joinPaths = joinPaths;
   }

   public List<TableSetOperation> getTableSetOperations() {
      return tableSetOperations;
   }

   public void setTableSetOperations(List<TableSetOperation> tableSetOperations) {
      this.tableSetOperations = tableSetOperations;
   }

   public List<Condition> getFilters() {
      return filters;
   }

   public void setFilters(List<Condition> filters) {
      this.filters = filters;
   }

   public List<OrderByInfo> getOrderBy() {
      return orderBy;
   }

   public void setOrderBy(List<OrderByInfo> orderBy) {
      this.orderBy = orderBy;
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class QueryField {
      private String fieldName;
      private String alias;
      private TableInfo table;
      private String expression;
      private String description;

      public QueryField() {
      }

      public QueryField(TableInfo table, String fieldName) {
         this.table = table;
         this.fieldName = fieldName;
      }

      public String getFieldName() {
         return fieldName;
      }

      public void setFieldName(String fieldName) {
         this.fieldName = fieldName;
      }

      public String getAlias() {
         return alias;
      }

      public void setAlias(String alias) {
         this.alias = alias;
      }

      public TableInfo getTable() {
         return table;
      }

      public void setTable(TableInfo table) {
         this.table = table;
      }

      public String getExpression() {
         return expression;
      }

      public void setExpression(String expression) {
         this.expression = expression;
      }

      public String getDescription() {
         return description;
      }

      public void setDescription(String description) {
         this.description = description;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         QueryField that = (QueryField) o;

         // description is intentionally excluded: field identity is based on name/alias/table/expression,
         // not its human-readable description. Two fields with the same identity but different descriptions
         // are treated as duplicates and only the first is kept.
         return Objects.equals(fieldName, that.fieldName) && Objects.equals(alias, that.alias) &&
            Objects.equals(table, that.table) && Objects.equals(expression, that.expression);
      }

      @Override
      public int hashCode() {
         return Objects.hash(fieldName, alias, table, expression);
      }
   }

   public static class JoinPath {
      private TableInfo leftTable;
      private String leftKey;
      private TableInfo rightTable;
      private String rightKey;
      private String joinType;
      private String joinOperator;

      public TableInfo getLeftTable() {
         return leftTable;
      }

      public void setLeftTable(TableInfo leftTable) {
         this.leftTable = leftTable;
      }

      public String getLeftKey() {
         return leftKey;
      }

      public void setLeftKey(String leftKey) {
         this.leftKey = leftKey;
      }

      public TableInfo getRightTable() {
         return rightTable;
      }

      public void setRightTable(TableInfo rightTable) {
         this.rightTable = rightTable;
      }

      public String getRightKey() {
         return rightKey;
      }

      public void setRightKey(String rightKey) {
         this.rightKey = rightKey;
      }

      public String getJoinType() {
         return joinType;
      }

      public void setJoinType(String joinType) {
         this.joinType = joinType;
      }

      public String getJoinOperator() {
         return joinOperator;
      }

      public void setJoinOperator(String joinOperator) {
         this.joinOperator = joinOperator;
      }
   }

   public static class JoinType {
      public static final String INNER = "inner";
      public static final String LEFT = "left";
      public static final String RIGHT = "right";
      public static final String FULL = "full";
      public static final String CROSS = "cross";
   }

   public static class JoinOperator {
      public static final String EQUALS = "=";
      public static final String GREATER = ">";
      public static final String LESS = "<";
      public static final String GREATER_EQUALS = ">=";
      public static final String LESS_EQUALS = "<=";
      public static final String NOT_EQUALS = "<>";
   }

   public static class TableSetOperation {
      private TableInfo leftTable;
      private TableInfo rightTable;
      private SetOperationType operation;

      public TableInfo getLeftTable() {
         return leftTable;
      }

      public void setLeftTable(TableInfo leftTable) {
         this.leftTable = leftTable;
      }

      public TableInfo getRightTable() {
         return rightTable;
      }

      public void setRightTable(TableInfo rightTable) {
         this.rightTable = rightTable;
      }

      public SetOperationType getOperation() {
         return operation;
      }

      public void setOperation(SetOperationType operation) {
         this.operation = operation;
      }
   }

   public static enum SetOperationType {
      UNION, INTERSECT, EXCEPT
   }

   public static class Condition {
      private String field;
      private FilterOperator operator;
      private Object value; // String or String[]
      private ConditionOperator conditionOperator;
      private int conditionLevel;
      private boolean negated;

      public boolean isNegated() {
         return negated;
      }

      public void setNegated(boolean negated) {
         this.negated = negated;
      }

      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      public FilterOperator getOperator() {
         return operator;
      }

      public void setOperator(FilterOperator operator) {
         this.operator = operator;
      }

      public Object getValue() {
         return value;
      }

      public void setValue(Object value) {
         this.value = value;
      }

      public ConditionOperator getConditionOperator() {
         return conditionOperator;
      }

      public void setConditionOperator(ConditionOperator conditionOperator) {
         this.conditionOperator = conditionOperator;
      }

      public int getConditionLevel() {
         return conditionLevel;
      }

      public void setConditionLevel(int conditionLevel) {
         this.conditionLevel = conditionLevel;
      }
   }

   public static enum ConditionOperator {
      AND, OR
   }

   public static enum FilterOperator {
      EQ, GT, GE, LT, LE, IN, BETWEEN, LIKE
   }

   public static class OrderByInfo {
      private String field;
      private Direction direction;

      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      public Direction getDirection() {
         return direction;
      }

      public void setDirection(Direction direction) {
         this.direction = direction;
      }
   }

   public static enum Direction {
      ASC, DESC
   }

   public static class TableInfo {
      private String name;
      private SourceInfo source;

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         TableInfo tableInfo = (TableInfo) o;
         return Objects.equals(name, tableInfo.name) && Objects.equals(source, tableInfo.source);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, source);
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public SourceInfo getSource() {
         return source;
      }

      public void setSource(SourceInfo source) {
         this.source = source;
      }
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class SourceInfo {
      private String type;   // ws or db
      private String path;   // db path or ws table
      private String catalog;
      private String schema;

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         SourceInfo that = (SourceInfo) o;
         return Objects.equals(type, that.type) && Objects.equals(path, that.path) &&
            Objects.equals(catalog, that.catalog) && Objects.equals(schema, that.schema);
      }

      @Override
      public int hashCode() {
         return Objects.hash(type, path, catalog, schema);
      }

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public String getPath() {
         return path;
      }

      public void setPath(String path) {
         this.path = path;
      }

      public String getCatalog() {
         return catalog;
      }

      public void setCatalog(String catalog) {
         this.catalog = catalog;
      }

      public String getSchema() {
         return schema;
      }

      public void setSchema(String schema) {
         this.schema = schema;
      }
   }
}
