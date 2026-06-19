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
package inetsoft.web.wiz.worksheet;

import java.util.List;

/**
 * Union request body for all worksheet edit operations.
 *
 * <p>The {@code op} discriminator selects the mutation to apply.  Only the
 * fields relevant to that operation need to be present; all others default to
 * {@code null} / {@code false}.</p>
 *
 * <p>Supported {@code op} values:</p>
 * <ul>
 *   <li>{@code add_column} — {@code table}, {@code name}, {@code type}</li>
 *   <li>{@code remove_column} — {@code table}, {@code column}</li>
 *   <li>{@code rename_column} — {@code table}, {@code column}, {@code newName}</li>
 *   <li>{@code add_filter} — {@code table}, {@code field}, {@code operation}, {@code values}</li>
 *   <li>{@code remove_filter} — {@code table}, {@code field}</li>
 *   <li>{@code set_group_aggregate} — {@code table}, {@code groups}, {@code aggregates}</li>
 *   <li>{@code add_expression_column} — {@code table}, {@code name}, {@code expression}, {@code type}, {@code sql}</li>
 *   <li>{@code set_sort} — {@code table}, {@code field}, {@code direction} ("ASC" | "DESC")</li>
 *   <li>{@code add_join} — {@code name}, {@code leftTable}, {@code leftKey}, {@code rightTable}, {@code rightKey}, {@code joinType}</li>
 *   <li>{@code remove_join} — {@code name}</li>
 *   <li>{@code add_table} — {@code table}</li>
 *   <li>{@code edit_condition} — {@code table}, {@code field}, {@code operation}, {@code values}</li>
 *   <li>{@code edit_expression} — {@code table}, {@code name}, {@code expression}, {@code type}, {@code sql}</li>
 *   <li>{@code edit_join} — {@code name}, {@code leftKey}, {@code rightKey}, {@code joinType}</li>
 * </ul>
 */
public record EditRequest(
   /** Discriminator — one of the op values listed above. */
   String op,
   /** Target assembly name (most ops). */
   String table,
   /** Column attribute name (remove_column, rename_column). */
   String column,
   /** New column / join assembly / expression column name (add_column, rename_column, add_join, add_expression_column). */
   String name,
   /** Data type string, e.g. {@code "string"}, {@code "integer"} (add_column, add_expression_column). */
   String type,
   /** New alias for rename_column. */
   String newName,
   /** Column name for filter / sort operations. */
   String field,
   /** Comparison operator for add_filter, e.g. {@code "="}, {@code "!="}. */
   String operation,
   /** Literal values for add_filter. */
   List<String> values,
   /** Sort direction — {@code "ASC"} or {@code "DESC"} — for set_sort. */
   String direction,
   /** Group-by column names for set_group_aggregate. */
   List<String> groups,
   /** Aggregate measure specs for set_group_aggregate. */
   List<WorksheetMutationSupport.AggregateSpec> aggregates,
   /** Expression body for add_expression_column. */
   String expression,
   /** {@code true} if the expression is SQL rather than script (add_expression_column). */
   boolean sql,
   /** Left source table name for add_join. */
   String leftTable,
   /** Join key column from the left table for add_join. */
   String leftKey,
   /** Right source table name for add_join. */
   String rightTable,
   /** Join key column from the right table for add_join. */
   String rightKey,
   /** Join type for add_join — {@code "INNER"}, {@code "LEFT"}, {@code "RIGHT"}, {@code "FULL"}. */
   String joinType
) {}
