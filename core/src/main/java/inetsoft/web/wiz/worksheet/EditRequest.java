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
import java.util.Map;

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
 *   <li>{@code add_join} — {@code name}, {@code leftTable}, {@code leftKey}, {@code rightTable}, {@code rightKey}, {@code joinType}; for multi-key joins use {@code leftKeys}/{@code rightKeys} instead of single key fields</li>
 *   <li>{@code remove_join} — {@code name}</li>
 *   <li>{@code add_table} — {@code table}, optional {@code datasource} (when provided, creates a bound table from the named datasource)</li>
 *   <li>{@code edit_condition} — {@code table}, {@code field}, {@code operation}, {@code values}</li>
 *   <li>{@code edit_expression} — {@code table}, {@code name}, {@code expression}, {@code type}, {@code sql}</li>
 *   <li>{@code edit_join} — {@code name}, {@code leftKey}, {@code rightKey}, {@code joinType}; for multi-key joins use {@code leftKeys}/{@code rightKeys}</li>
 *   <li>{@code delete_table} — {@code table}</li>
 *   <li>{@code rename_table} — {@code table}, {@code newName}</li>
 *   <li>{@code set_column_visibility} — {@code table}, {@code column}, {@code visible}</li>
 *   <li>{@code change_column_type} — {@code table}, {@code column}, {@code type}</li>
 *   <li>{@code add_concatenation} — {@code name}, {@code tables} (list), {@code concatType} (UNION|INTERSECT|MINUS)</li>
 *   <li>{@code add_mirror} — {@code name}, {@code source}</li>
 *   <li>{@code set_conditions} — {@code table}, {@code conditions} (condition tree)</li>
 *   <li>{@code set_post_conditions} — {@code table}, {@code conditions} (condition tree, post-aggregate/HAVING)</li>
 *   <li>{@code set_ranking} — {@code table}, {@code ranking}</li>
 *   <li>{@code add_rotate} — {@code name}, {@code source}</li>
 *   <li>{@code add_unpivot} — {@code name}, {@code source}, {@code headerColumns}</li>
 *   <li>{@code add_date_range_column} — {@code table}, {@code column}, {@code dateOption}</li>
 *   <li>{@code add_numeric_range_column} — {@code table}, {@code column}, {@code boundaries}</li>
 *   <li>{@code edit_cell} — {@code table}, {@code row}, {@code col}, {@code value}</li>
 *   <li>{@code insert_row} — {@code table}, {@code index}</li>
 *   <li>{@code delete_row} — {@code table}, {@code index}</li>
 *   <li>{@code set_table_properties} — {@code table}, {@code alias}, {@code description}, {@code maxRows}, {@code distinct}</li>
 *   <li>{@code add_cross_join} — {@code name}, {@code leftTable}, {@code rightTable}</li>
 *   <li>{@code add_merge_join} — {@code name}, {@code tables}</li>
 *   <li>{@code reorder_columns} — {@code table}, {@code columnOrder}</li>
 *   <li>{@code add_concat_subtable} — {@code table} (concat assembly), {@code name} (subtable to add)</li>
 *   <li>{@code remove_concat_subtable} — {@code table} (concat assembly), {@code name} (subtable to remove)</li>
 *   <li>{@code add_named_group} — {@code name}, {@code table}, {@code column}, {@code groupMappings}, {@code groupOthers}</li>
 *   <li>{@code set_column_description} — {@code table}, {@code column}, {@code description}</li>
 *   <li>{@code set_variable_values} — {@code variableValues} (map of variable name → value)</li>
 *   <li>{@code set_mirror_auto_update} — {@code table}, {@code visible} (true=auto-update on, false=off)</li>
 *   <li>{@code convert_to_embedded} — {@code table}</li>
 *   <li>{@code set_assembly_position} — {@code table}, {@code x}, {@code y}</li>
 *   <li>{@code duplicate_assembly} — {@code table} (source), {@code name} (new name)</li>
 *   <li>{@code set_primary_assembly} — {@code table}</li>
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
   String joinType,
   /** Column visibility for set_column_visibility ({@code true} = visible, {@code false} = hidden). */
   Boolean visible,
   /** Source table names for add_concatenation (at least two required). */
   List<String> tables,
   /** Source assembly name for add_mirror. */
   String source,
   /** Concatenation type for add_concatenation — {@code "UNION"}, {@code "INTERSECT"}, {@code "MINUS"}. */
   String concatType,
   /** Condition tree nodes for set_conditions / set_post_conditions. */
   List<WorksheetMutationSupport.ConditionNode> conditions,
   /** Ranking spec for set_ranking. */
   WorksheetMutationSupport.RankingSpec ranking,
   /** Number of header columns for add_unpivot. */
   Integer headerColumns,
   /** Date grouping option for add_date_range_column. */
   String dateOption,
   /** Numeric bucket boundaries for add_numeric_range_column. */
   double[] boundaries,
   /** Datasource name for add_table (when provided, creates a PhysicalBoundTableAssembly). */
   String datasource,
   /** Schema name for add_table (e.g. "SA", "dbo", "public"). */
   String schema,
   /** Catalog name for add_table. */
   String catalog,
   /** Multi-key join: left column names for add_join / edit_join. */
   List<String> leftKeys,
   /** Multi-key join: right column names for add_join / edit_join. */
   List<String> rightKeys,
   /** Row index for edit_cell (0-based data row). */
   Integer row,
   /** Column index for edit_cell (0-based). */
   Integer col,
   /** Cell value for edit_cell. */
   String value,
   /** Row index for insert_row / delete_row (0-based data row). */
   Integer index,
   /** Table alias for set_table_properties. */
   String alias,
   /** Table description for set_table_properties. */
   String description,
   /** Max rows for set_table_properties. */
   Integer maxRows,
   /** Distinct flag for set_table_properties. */
   Boolean distinct,
   /** Ordered list of column names for reorder_columns. */
   List<String> columnOrder,
   /** Group name → value list mappings for add_named_group. */
   List<WorksheetMutationSupport.GroupMapping> groupMappings,
   /** Whether to group unmapped values as "Others" for add_named_group. */
   Boolean groupOthers,
   /** Variable name → value mappings for set_variable_values. */
   Map<String, String> variableValues,
   /** X pixel coordinate for set_assembly_position. */
   Integer x,
   /** Y pixel coordinate for set_assembly_position. */
   Integer y
) {}
