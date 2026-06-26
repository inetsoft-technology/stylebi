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
 * Transport model representing a condition/filter specification sent from the Wiz AI service.
 * Mirrors the TypeScript {@code ConditionModel} structure.
 * Contains two categories of conditions:
 * <ul>
 *   <li>{@code baseConditions}: Applied before aggregation (SQL WHERE equivalent)</li>
 *   <li>{@code aggregateConditions}: Applied after aggregation (SQL HAVING equivalent)</li>
 * </ul>
 * When {@code aggregateConditions} is non-empty, StyleBI pushes groups, aggregates, and all
 * conditions down to the worksheet table level; the visualization then uses pre-processed fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationConditionModel {
   /**
    * Conditions on base fields, applied before aggregation (SQL WHERE equivalent).
    */
   public List<ConditionNode> getBaseConditions() {
      return baseConditions;
   }

   public void setBaseConditions(List<ConditionNode> baseConditions) {
      this.baseConditions = baseConditions;
   }

   /**
    * Conditions on aggregated/grouped fields, applied after aggregation (SQL HAVING equivalent).
    */
   public List<ConditionNode> getAggregateConditions() {
      return aggregateConditions;
   }

   public void setAggregateConditions(List<ConditionNode> aggregateConditions) {
      this.aggregateConditions = aggregateConditions;
   }

   /**
    * Returns true if there are any aggregate conditions that require pushing
    * aggregation to the worksheet level.
    */
   public boolean hasAggregateConditions() {
      return aggregateConditions != null && !aggregateConditions.isEmpty();
   }

   private List<ConditionNode> baseConditions;
   private List<ConditionNode> aggregateConditions;

   /**
    * Polymorphic base for condition tree nodes.
    * Deserialized based on the {@code type} JSON property.
    */
   @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = ConditionLeaf.class)
   @JsonSubTypes({
      @JsonSubTypes.Type(value = ConditionLeaf.class,  name = "condition"),
      @JsonSubTypes.Type(value = ConditionGroup.class, name = "group")
   })
   @JsonIgnoreProperties(ignoreUnknown = true)
   public interface ConditionNode {
      /** "and" | "or" — the logical relationship with the previous sibling; null for the first. */
      String getJunction();
   }

   /**
    * Leaf node: a single filter condition.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionLeaf implements ConditionNode {
      @JsonCreator
      public ConditionLeaf(@JsonProperty("junction") String junction,
                           @JsonProperty("condition") ConditionSpec condition)
      {
         this.junction = junction;
         this.condition = condition;
      }

      @Override
      public String getJunction() {
         return junction;
      }

      public ConditionSpec getCondition() {
         return condition;
      }

      private final String junction;
      private final ConditionSpec condition;
   }

   /**
    * Group node: parenthesizes its children (equivalent to brackets in logical expressions).
    * Children may be leaves or nested groups.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionGroup implements ConditionNode {
      @JsonCreator
      public ConditionGroup(@JsonProperty("junction") String junction,
                            @JsonProperty("items") List<ConditionNode> items)
      {
         this.junction = junction;
         this.items = items;
      }

      @Override
      public String getJunction() {
         return junction;
      }

      public List<ConditionNode> getItems() {
         return items;
      }

      private final String junction;
      private final List<ConditionNode> items;
   }


   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionSpec {
      @JsonCreator
      public ConditionSpec(@JsonProperty("field") String field,
                           @JsonProperty("aggregateFormula") String aggregateFormula,
                           @JsonProperty("secondaryField") String secondaryField,
                           // "norP" (not "nOrP") preserves the property name Jackson derives from
                           // getNOrP(); changing it would alter the existing wire contract.
                           @JsonProperty("norP") Integer nOrP,
                           @JsonProperty("dateGroupLevel") String dateGroupLevel,
                           @JsonProperty("negated") boolean negated,
                           @JsonProperty("operation") String operation,
                           @JsonProperty("equal") Boolean equal,
                           @JsonProperty("values") List<ValueSpec> values)
      {
         this.field = field;
         this.aggregateFormula = aggregateFormula;
         this.secondaryField = secondaryField;
         this.nOrP = nOrP;
         this.dateGroupLevel = dateGroupLevel;
         this.negated = negated;
         this.operation = operation;
         this.equal = equal;
         this.values = values;
      }

      public String getField() {
         return field;
      }

      /**
       * Aggregation function for aggregate conditions (e.g., Sum, Average, Count, Max, Min).
       */
      public String getAggregateFormula() {
         return aggregateFormula;
      }

      /**
       * Secondary field for two-column formulas: Correlation, Covariance, WeightedAverage, SumWT.
       */
      public String getSecondaryField() {
         return secondaryField;
      }

      /**
       * N or P parameter for formulas: NthLargest(N), NthSmallest(N), NthMostFrequent(N), PthPercentile(P: 0-100).
       */
      public Integer getNOrP() {
         return nOrP;
      }

      /**
       * Date grouping level for date-level conditions (e.g., Year, Quarter, Month, Week, Day).
       */
      public String getDateGroupLevel() {
         return dateGroupLevel;
      }

      public boolean isNegated() {
         return negated;
      }

      /**
       * One of: EQUAL_TO, ONE_OF, LESS_THAN, GREATER_THAN, BETWEEN,
       * STARTING_WITH, CONTAINS, LIKE, NULL, DATE_IN
       */
      public String getOperation() {
         return operation;
      }

      /**
       * True means &lt;= or &gt;= (applies to LESS_THAN / GREATER_THAN only).
       */
      public Boolean getEqual() {
         return equal;
      }

      public List<ValueSpec> getValues() {
         return values;
      }

      private final String field;
      private final String aggregateFormula;
      private final String secondaryField;
      private final Integer nOrP;
      private final String dateGroupLevel;
      private final boolean negated;
      private final String operation;
      private final Boolean equal;
      private final List<ValueSpec> values;
   }

   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ValueSpec {
      @JsonCreator
      public ValueSpec(@JsonProperty("type") String type,
                       @JsonProperty("value") Object value,
                       @JsonProperty("subQuery") SubQuery subQuery)
      {
         this.type = type;
         this.value = value;
         this.subQuery = subQuery;
      }

      /**
       * VALUE | EXPRESSION | FIELD | SESSION_DATA | SUBQUERY
       */
      public String getType() {
         return type;
      }

      /** Operand for VALUE, EXPRESSION, FIELD, SESSION_DATA. */
      public Object getValue() {
         return value;
      }

      /** Operand for SUBQUERY: value(s) drawn from another worksheet table's column. */
      public SubQuery getSubQuery() {
         return subQuery;
      }

      private final String type;
      private final Object value;
      private final SubQuery subQuery;
   }

   /**
    * SUBQUERY operand, mirroring the TypeScript {@code SubQueryValue} shape: a value drawn
    * from another worksheet table's column, optionally correlated per-row via {@code where}.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class SubQuery {
      @JsonCreator
      public SubQuery(@JsonProperty("subQueryName") String subQueryName,
                      @JsonProperty("inSubQueryColumn") String inSubQueryColumn,
                      @JsonProperty("where") Where where)
      {
         this.subQueryName = subQueryName;
         this.inSubQueryColumn = inSubQueryColumn;
         this.where = where;
      }

      /** Name of an already-created worksheet table. */
      public String getSubQueryName() {
         return subQueryName;
      }

      /** Column in that table whose value serves as the operand. */
      public String getInSubQueryColumn() {
         return inSubQueryColumn;
      }

      /** Correlated match; null for a global scalar subquery (single-row result). */
      public Where getWhere() {
         return where;
      }

      private final String subQueryName;
      private final String inSubQueryColumn;
      private final Where where;
   }

   /** Correlated subquery filter: subQueryColumn = currentTableColumn. */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class Where {
      @JsonCreator
      public Where(@JsonProperty("subQueryColumn") String subQueryColumn,
                   @JsonProperty("currentTableColumn") String currentTableColumn)
      {
         this.subQueryColumn = subQueryColumn;
         this.currentTableColumn = currentTableColumn;
      }

      public String getSubQueryColumn() {
         return subQueryColumn;
      }

      public String getCurrentTableColumn() {
         return currentTableColumn;
      }

      private final String subQueryColumn;
      private final String currentTableColumn;
   }
}
