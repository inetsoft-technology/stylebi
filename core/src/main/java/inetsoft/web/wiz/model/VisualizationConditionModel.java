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
      @Override
      public String getJunction() {
         return junction;
      }

      public void setJunction(String junction) {
         this.junction = junction;
      }

      public ConditionSpec getCondition() {
         return condition;
      }

      public void setCondition(ConditionSpec condition) {
         this.condition = condition;
      }

      private String junction;
      private ConditionSpec condition;
   }

   /**
    * Group node: parenthesizes its children (equivalent to brackets in logical expressions).
    * Children may be leaves or nested groups.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionGroup implements ConditionNode {
      @Override
      public String getJunction() {
         return junction;
      }

      public void setJunction(String junction) {
         this.junction = junction;
      }

      public List<ConditionNode> getItems() {
         return items;
      }

      public void setItems(List<ConditionNode> items) {
         this.items = items;
      }

      private String junction;
      private List<ConditionNode> items;
   }


   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionSpec {
      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
      }

      /**
       * Aggregation function for aggregate conditions (e.g., Sum, Average, Count, Max, Min).
       */
      public String getAggregateFormula() {
         return aggregateFormula;
      }

      public void setAggregateFormula(String aggregateFormula) {
         this.aggregateFormula = aggregateFormula;
      }

      /**
       * Secondary field for two-column formulas: Correlation, Covariance, WeightedAverage, SumWT.
       */
      public String getSecondaryField() {
         return secondaryField;
      }

      public void setSecondaryField(String secondaryField) {
         this.secondaryField = secondaryField;
      }

      /**
       * N or P parameter for formulas: NthLargest(N), NthSmallest(N), NthMostFrequent(N), PthPercentile(P: 0-100).
       */
      public Integer getNOrP() {
         return nOrP;
      }

      public void setNOrP(Integer nOrP) {
         this.nOrP = nOrP;
      }

      /**
       * Date grouping level for date-level conditions (e.g., Year, Quarter, Month, Week, Day).
       */
      public String getDateGroupLevel() {
         return dateGroupLevel;
      }

      public void setDateGroupLevel(String dateGroupLevel) {
         this.dateGroupLevel = dateGroupLevel;
      }

      public boolean isNegated() {
         return negated;
      }

      public void setNegated(boolean negated) {
         this.negated = negated;
      }

      /**
       * One of: EQUAL_TO, ONE_OF, LESS_THAN, GREATER_THAN, BETWEEN,
       * STARTING_WITH, CONTAINS, LIKE, NULL, DATE_IN
       */
      public String getOperation() {
         return operation;
      }

      public void setOperation(String operation) {
         this.operation = operation;
      }

      /**
       * True means &lt;= or &gt;= (applies to LESS_THAN / GREATER_THAN only).
       */
      public Boolean getEqual() {
         return equal;
      }

      public void setEqual(Boolean equal) {
         this.equal = equal;
      }

      public List<ValueSpec> getValues() {
         return values;
      }

      public void setValues(List<ValueSpec> values) {
         this.values = values;
      }

      private String field;
      private String aggregateFormula;
      private String secondaryField;
      private Integer nOrP;
      private String dateGroupLevel;
      private boolean negated;
      private String operation;
      private Boolean equal;
      private List<ValueSpec> values;
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ValueSpec {
      /**
       * VALUE | EXPRESSION | FIELD | SESSION_DATA
       */
      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public Object getValue() {
         return value;
      }

      public void setValue(Object value) {
         this.value = value;
      }

      private String type;
      private Object value;
   }
}
