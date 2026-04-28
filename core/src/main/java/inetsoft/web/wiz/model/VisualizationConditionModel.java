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

/**
 * Transport model representing a condition/filter specification sent from the Wiz AI service.
 * Mirrors the TypeScript {@code ConditionModel} structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationConditionModel {
   public List<Entry> getConditions() {
      return conditions;
   }

   public void setConditions(List<Entry> conditions) {
      this.conditions = conditions;
   }

   private List<Entry> conditions;

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class Entry {
      /**
       * "and" | "or" — ignored for the first entry.
       */
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

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ConditionSpec {
      public String getField() {
         return field;
      }

      public void setField(String field) {
         this.field = field;
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
      private boolean negated;
      private String operation;
      private Boolean equal;
      private List<ValueSpec> values;
   }

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ValueSpec {
      /**
       * VALUE | VARIABLE | EXPRESSION | FIELD | SESSION_DATA
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
