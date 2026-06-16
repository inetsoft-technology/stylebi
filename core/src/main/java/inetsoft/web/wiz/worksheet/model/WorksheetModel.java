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
package inetsoft.web.wiz.worksheet.model;

import java.util.List;

/**
 * Read-model DTO representing a worksheet's structure as seen by the agent.
 *
 * <p>All fields are plain Java records — no Spring / JPA dependencies — so the
 * class can be constructed anywhere (tests, service, controller) without a
 * container.</p>
 */
public record WorksheetModel(List<TableModel> tables) {

   /**
    * A single table assembly inside the worksheet.
    *
    * @param name               assembly name
    * @param type               one of {@code "EMBEDDED"}, {@code "JOIN"},
    *                           {@code "MIRROR"}, {@code "UNPIVOT"},
    *                           {@code "ROTATED"}, {@code "TABLE"}
    * @param columns            visible (public) columns
    * @param joins              join predicates; non-null, empty for non-join tables
    * @param preConditions      pre-aggregate filter conditions
    * @param postConditions     post-aggregate filter conditions
    * @param rankingConditions  ranking / top-N conditions
    * @param aggregates         group-by / aggregate info; {@code null} when none is set
    * @param sorts              sort directives; empty when none
    */
   public record TableModel(
      String name,
      String type,
      List<ColumnModel> columns,
      List<JoinModel> joins,
      List<FilterModel> preConditions,
      List<FilterModel> postConditions,
      List<FilterModel> rankingConditions,
      AggregateModel aggregates,
      List<SortModel> sorts
   ) {}

   /**
    * A single visible column in a table.
    *
    * @param name       attribute (column) name
    * @param type       XSchema data-type string (e.g. {@code "string"}, {@code "integer"})
    * @param alias      display alias; may be {@code null} or empty
    * @param expression script expression when the column is an expression column;
    *                   {@code null} for plain attribute columns
    */
   public record ColumnModel(String name, String type, String alias, String expression) {}

   /**
    * A join predicate between two tables.
    *
    * @param leftTable  name of the left-hand table assembly
    * @param leftKey    attribute name from the left table
    * @param rightTable name of the right-hand table assembly
    * @param rightKey   attribute name from the right table
    * @param op         join operator name (e.g. {@code "INNER_JOIN"}, {@code "LEFT_JOIN"})
    */
   public record JoinModel(
      String leftTable,
      String leftKey,
      String rightTable,
      String rightKey,
      String op
   ) {}

   /**
    * A single filter condition item.
    *
    * @param field     attribute name the condition applies to
    * @param operation numeric operation code from {@link inetsoft.uql.XCondition}
    *                  formatted as a string (e.g. {@code "1"} for EQUAL_TO)
    * @param values    literal value(s) used in the condition
    * @param junction  {@code "AND"} or {@code "OR"} — the junction that precedes
    *                  this condition in the list (may be {@code null} for the first item)
    */
   public record FilterModel(
      String field,
      String operation,
      List<String> values,
      String junction
   ) {}

   /**
    * Aggregation / group-by configuration for a table.
    *
    * @param groups     group-by dimension names
    * @param aggregates measure aggregate definitions
    */
   public record AggregateModel(List<String> groups, List<AggregateRefModel> aggregates) {

      /**
       * A single aggregate measure.
       *
       * @param column  source column name
       * @param formula formula name (e.g. {@code "Sum"}, {@code "Count"})
       */
      public record AggregateRefModel(String column, String formula) {}
   }

   /**
    * A sort directive on a single column.
    *
    * @param field column name
    * @param order {@code "ASC"} or {@code "DESC"}
    */
   public record SortModel(String field, String order) {}
}
