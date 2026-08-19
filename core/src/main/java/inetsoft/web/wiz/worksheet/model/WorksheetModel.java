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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Read-model DTO representing a worksheet's structure as seen by the agent.
 *
 * <p>All fields are plain Java records — no Spring / JPA dependencies — so the
 * class can be constructed anywhere (tests, service, controller) without a
 * container.</p>
 *
 * <p>Every record here omits its null fields, matching the sibling DTOs under
 * {@code inetsoft.web.wiz.model}. This payload is read by an LLM, and a field that is absent says
 * exactly what a field that is {@code null} says while costing nothing —
 * {@code "concatType": null, "concatCompatible": null, "autoUpdate": null} on every plain table
 * adds up. {@code NON_NULL} and deliberately not {@code NON_EMPTY}: an empty {@code sources} or
 * {@code joins} list is a meaningful answer ("built on nothing", "no predicates") and must stay on
 * the wire.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorksheetModel(List<TableModel> tables, List<VariableModel> variables,
                             List<NamedGroupModel> namedGroups) {

   /**
    * A single table assembly inside the worksheet.
    *
    * @param name               assembly name
    * @param type               one of {@code "EMBEDDED"}, {@code "JOIN"}, {@code "CONCAT"},
    *                           {@code "MIRROR"}, {@code "UNPIVOT"},
    *                           {@code "ROTATED"}, {@code "TABLE"}
    * @param columns            the table's columns, hidden ones included — see
    *                           {@link ColumnModel#visible()}
    * @param joins              join predicates; non-null, empty for non-join tables
    * @param sources            the assemblies this one is built from, <b>in order</b>; empty for a
    *                           table with no sources. Order carries meaning: for a
    *                           {@code CONCAT} the first entry supplies the whole column list (see
    *                           {@code ConcatenatedTableAssembly.getDefaultColumnSelection}) and,
    *                           for a MINUS, decides which table is subtracted from which. Without
    *                           this an agent cannot tell which tables a concatenation combines,
    *                           cannot supply the full subtable list that reordering requires, and
    *                           cannot tell that editing a table will reshape a concatenation
    *                           downstream. A cross or merge join also reports its sources here,
    *                           since those carry no join predicates and so leave {@code joins}
    *                           empty — an empty {@code joins} means "no predicates", never "no
    *                           sources". A {@code MIRROR}, {@code ROTATED} or {@code UNPIVOT}
    *                           reports the single table it is built on.
    * @param concatType         {@code "UNION"}, {@code "INTERSECT"} or {@code "MINUS"} for a
    *                           {@code CONCAT}; {@code null} otherwise. A concatenation carries one
    *                           operation per adjacent pair of {@code sources} and they need not
    *                           agree, so {@code "MIXED"} is reported when they differ (the
    *                           per-pair operations are not exposed).
    * @param concatCompatible   for a {@code CONCAT}, whether its sources line up by type as well as
    *                           by count; {@code null} otherwise. Sources are combined by position,
    *                           so a pair that lines up numerically but not by type produces a
    *                           column carrying two unrelated kinds of value. {@code false} is what
    *                           Composer draws as a warning on the connection, and it is the only
    *                           way to see the problem in a concatenation the agent did not build —
    *                           {@code add_concatenation} refuses to create one.
    * @param autoUpdate         a mirror's <b>effective</b> auto-update flag; {@code null} for
    *                           anything that is not a mirror. Effective, not stored: a mirror whose
    *                           source is in the same worksheet always reports {@code true} however
    *                           the flag was set, since {@code MirrorAssemblyImpl} answers
    *                           {@code auto || !isOuterMirror()}.
    * @param preConditions      pre-aggregate filter conditions
    * @param postConditions     post-aggregate filter conditions
    * @param rankingConditions  ranking / top-N conditions
    * @param aggregates         group-by / aggregate info; {@code null} when none is set
    * @param sorts              sort directives; empty when none
    * @param primary            {@code true} if this is the worksheet's primary assembly
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record TableModel(
      String name,
      String type,
      List<ColumnModel> columns,
      List<JoinModel> joins,
      List<String> sources,
      String concatType,
      Boolean concatCompatible,
      Boolean autoUpdate,
      List<FilterModel> preConditions,
      List<FilterModel> postConditions,
      List<FilterModel> rankingConditions,
      AggregateModel aggregates,
      List<SortModel> sorts,
      boolean primary
   ) {}

   /**
    * A single column in a table.
    *
    * <p>Hidden columns are included: the list is read from the private column selection, not the
    * public one, which is why {@code visible} is needed to tell them apart.</p>
    *
    * @param name        attribute (column) name
    * @param type        XSchema data-type string (e.g. {@code "string"}, {@code "integer"})
    * @param alias       display alias; may be {@code null} or empty
    * @param expression  script expression when the column is an expression column;
    *                    {@code null} for plain attribute columns
    * @param description user-defined column description; may be {@code null}
    * @param visible     {@code false} once a column is hidden. A hidden column stays in this list
    *                    but drops out of the assembly's data, and is excluded from the public
    *                    column selection that a concatenation counts when it checks its sources
    *                    match — so anything comparing column counts has to filter on this rather
    *                    than take the list length, and anything comparing the model against real
    *                    rows has to expect hidden columns to be missing from the data.
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record ColumnModel(String name, String type, String alias, String expression,
                             String description, boolean visible) {}

   /**
    * A join predicate between two tables.
    *
    * @param leftTable  name of the left-hand table assembly
    * @param leftKey    attribute name from the left table
    * @param rightTable name of the right-hand table assembly
    * @param rightKey   attribute name from the right table
    * @param op         join operator name (e.g. {@code "INNER_JOIN"}, {@code "LEFT_JOIN"})
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
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
    * @param operation human-readable operator string — one of {@code "="}, {@code "!="},
    *                  {@code "<"}, {@code "<="}, {@code ">"}, {@code ">="},
    *                  {@code "BETWEEN"}, {@code "ONE_OF"}, {@code "NOT_ONE_OF"},
    *                  {@code "STARTING_WITH"}, {@code "CONTAINS"}, {@code "LIKE"},
    *                  {@code "NULL"}, {@code "NOT_NULL"}.
    *                  These are exactly the strings accepted by the {@code operation}
    *                  parameter of {@code add_filter} / {@code edit_condition}.
    * @param values    literal value(s) used in the condition
    * @param junction  {@code "AND"} or {@code "OR"} — the junction that precedes
    *                  this condition in the list (may be {@code null} for the first item)
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record FilterModel(
      String field,
      String operation,
      List<String> values,
      String junction
   ) {}

   /**
    * Aggregation / group-by configuration for a table.
    *
    * @param groups     group-by dimensions
    * @param aggregates measure aggregate definitions
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record AggregateModel(List<GroupModel> groups, List<AggregateRefModel> aggregates) {

      /**
       * A single group-by dimension.
       *
       * @param field     source column name
       * @param dateLevel the date grouping level applied directly to this group (e.g.
       *                  {@code "QUARTER"}), same vocabulary as set_group_aggregate's
       *                  {@code dateLevel} / add_date_range_column's {@code dateOption};
       *                  {@code null} for a plain (non-date-bucketed) group
       */
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public record GroupModel(String field, String dateLevel) {}

      /**
       * A single aggregate measure.
       *
       * @param column  source column name
       * @param formula formula name (e.g. {@code "Sum"}, {@code "Count"})
       * @param alias   output alias; may be {@code null}
       */
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public record AggregateRefModel(String column, String formula, String alias) {}
   }

   /**
    * A sort directive on a single column.
    *
    * @param field column name
    * @param order {@code "ASC"} or {@code "DESC"}
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record SortModel(String field, String order) {}

   /**
    * A variable assembly in the worksheet.
    *
    * @param name         assembly (variable) name
    * @param label        display label; may be {@code null}
    * @param type         XSchema data-type string; may be {@code null}
    * @param defaultValue stringified default value; may be {@code null}
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record VariableModel(String name, String label, String type, String defaultValue) {}

   /**
    * A named group assembly in the worksheet.
    *
    * @param name          assembly name
    * @param table         the source table the grouping is attached to
    * @param column        the column the grouping is attached to
    * @param groupMappings list of group name to values mappings
    * @param groupOthers   {@code true} if unmapped values are grouped as "Others"
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record NamedGroupModel(
      String name,
      String table,
      String column,
      List<GroupMappingModel> groupMappings,
      boolean groupOthers
   ) {}

   /**
    * A single group mapping inside a named group.
    *
    * @param groupName the name of the group
    * @param values    the values assigned to this group
    */
   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record GroupMappingModel(String groupName, List<String> values) {}
}
