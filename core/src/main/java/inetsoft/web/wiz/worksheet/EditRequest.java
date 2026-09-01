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
 *   <li>{@code add_column} — {@code table}, {@code type}; {@code name} is required unless
 *       {@code table} is an embedded table, in which case a blank {@code name} auto-generates
 *       the next available {@code "col" + N} (matching the Composer UI's insert-column
 *       behavior)</li>
 *   <li>{@code remove_column} — {@code table}, {@code column}</li>
 *   <li>{@code rename_column} — {@code table}, {@code column}, {@code newName}</li>
 *   <li>{@code add_filter} — {@code table}, {@code field}, {@code operation}, {@code values}</li>
 *   <li>{@code remove_filter} — {@code table}, {@code field}</li>
 *   <li>{@code set_group_aggregate} — {@code table}, {@code groups} (each a column name or
 *       {@code {field, dateLevel}}), {@code aggregates}, optional {@code crosstab} (true =
 *       display as a crosstab, matching the Group and Aggregate dialog's "Switch to
 *       Crosstab" toggle; defaults to false). Per
 *       {@link inetsoft.uql.asset.AggregateInfo#isCrosstab}, the
 *       request is accepted either way but the crosstab actually takes effect only with at
 *       least 2 {@code groups} and at least 1 {@code aggregates} entry — fewer than that and
 *       the table silently reads back as a non-crosstab table on the next read, with no
 *       error from this call.</li>
 *   <li>{@code add_expression_column} — {@code table}, {@code name}, {@code expression}, {@code type}, {@code sql}</li>
 *   <li>{@code set_sort} — {@code table}, {@code field}, {@code direction} ("ASC" | "DESC")</li>
 *   <li>{@code add_join} — {@code name}, {@code leftTable}, {@code leftKey}, {@code rightTable}, {@code rightKey}, {@code joinType}; for multi-key joins use {@code leftKeys}/{@code rightKeys} instead of single key fields. For three or more tables joined in a single call, supply {@code joinPaths} instead (each a {leftTable, leftKey, rightTable, rightKey, joinType} edge) — {@code leftTable}/{@code leftKey}/{@code rightTable}/{@code rightKey}/{@code joinType}/{@code leftKeys}/{@code rightKeys} are ignored when {@code joinPaths} is present</li>
 *   <li>{@code remove_join} — {@code name}</li>
 *   <li>{@code add_table} — {@code table}, optional {@code datasource} (when provided, creates a bound table from the named datasource); optional {@code logicalModel} (when provided alongside datasource, {@code table} is an entity name within that logical model); optional {@code endpoint} (+ optional {@code parameters}/{@code lookup}/{@code lookupExpandArrays}/{@code lookupTopLevelOnly}) to bind a named REST/JSON connector's pre-built endpoint (and, optionally, one of its pre-built "Join With" lookup chains) instead of a physical table or logical model entity — {@code table} then names the NEW worksheet table rather than a physical path; optional {@code suffix} (+ optional {@code customLookups}) to bind a GENERIC/CUSTOM REST-JSON datasource's hand-authored URL suffix (and, optionally, up to 5 hand-authored custom lookup levels) instead — mutually exclusive with {@code endpoint}/{@code parameters}/{@code lookup}</li>
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
 *   <li>{@code set_ranking} — {@code table}, {@code ranking} (replaces the whole ranking list
 *       with this one entry)</li>
 *   <li>{@code set_rankings} — {@code table}, {@code rankings} (list; replaces the whole
 *       ranking list with these entries, in order — for more than one independent ranked
 *       field in a single call)</li>
 *   <li>{@code add_rotate} — {@code name}, {@code source}</li>
 *   <li>{@code add_unpivot} — {@code name}, {@code source}, {@code headerColumns}</li>
 *   <li>{@code add_date_range_column} — {@code table}, {@code column}, {@code dateOption}</li>
 *   <li>{@code add_numeric_range_column} — {@code table}, {@code column}, {@code boundaries},
 *       optional {@code labels} (one more entry than {@code boundaries})</li>
 *   <li>{@code edit_date_range_column} — {@code table}, {@code column} (the existing range
 *       column's own name), {@code dateOption}; the column is renamed to match, since its name
 *       encodes its option (see {@code add_date_range_column})</li>
 *   <li>{@code edit_numeric_range_column} — {@code table}, {@code column} (the existing range
 *       column's own name), {@code boundaries}, optional {@code labels} (replaces any existing
 *       labels; omitted/empty clears them back to the default auto-generated range text)</li>
 *   <li>{@code edit_cell} — {@code table}, {@code row}, {@code col}, {@code value}</li>
 *   <li>{@code insert_row} — {@code table}, {@code index}</li>
 *   <li>{@code delete_row} — {@code table}, {@code index}</li>
 *   <li>{@code set_table_properties} — {@code table}; any of {@code newName} (or {@code alias},
 *       its accepted spelling), {@code description}, {@code maxRows}, {@code distinct},
 *       {@code mergeable}, {@code visibleInViewsheet}</li>
 *   <li>{@code add_cross_join} — {@code name}, {@code leftTable}, {@code rightTable}</li>
 *   <li>{@code add_merge_join} — {@code name}, {@code tables}</li>
 *   <li>{@code reorder_columns} — {@code table}, {@code columnOrder}</li>
 *   <li>{@code add_concat_subtable} — {@code table} (concat assembly), {@code name} (subtable to add)</li>
 *   <li>{@code remove_concat_subtable} — {@code table} (concat assembly), {@code name} (subtable to remove)</li>
 *   <li>{@code add_named_group} — {@code name}, {@code groupMappings} (each mapping's
 *       {@code operation} is any operator accepted by
 *       {@link WorksheetMutationSupport#parseOperation}, e.g. {@code "STARTING_WITH"};
 *       defaults to {@code EQUAL_TO} when omitted), {@code groupOthers}; exactly one of:
 *       {@code table} + {@code column} (attach to a column on an existing worksheet table);
 *       {@code datasource} + {@code sourceTable} + {@code attribute} (+ optional
 *       {@code logicalModel}, or {@code schema}/{@code catalog} for a physical table) to scope
 *       directly to a datasource/logical-model or physical-table path, matching what a human
 *       produces via the Composer's own "Add Grouping" dialog; or {@code type} (standalone
 *       grouping, matched by data type; defaults to {@code "string"})</li>
 *   <li>{@code set_column_description} — {@code table}, {@code column}, {@code description}</li>
 *   <li>{@code set_variable_values} — {@code variableValues} (map of variable name → value)</li>
 *   <li>{@code set_mirror_auto_update} — {@code table}, {@code visible} (true=auto-update on, false=off)</li>
 *   <li>{@code convert_to_embedded} — {@code table}</li>
 *   <li>{@code set_assembly_position} — {@code table}, {@code x}, {@code y}</li>
 *   <li>{@code duplicate_assembly} — {@code table} (source), {@code name} (new name)</li>
 *   <li>{@code set_primary_assembly} — {@code table}</li>
 *   <li>{@code edit_variable} — {@code name}, {@code type}, {@code label}, {@code defaultValue};
 *       optional {@code choices} (the enumerated "Values" picker — embedded list or query
 *       source, plus display style — matching the Composer's own Variable dialog; {@code null}
 *       leaves it unchanged, see {@link WorksheetMutationSupport.VariableChoicesSpec})</li>
 *   <li>{@code rename_variable} — {@code name}, {@code newName}</li>
 *   <li>{@code delete_variable} — {@code name}</li>
 *   <li>{@code edit_named_group} — {@code name}, {@code groupMappings} (see {@code add_named_group}
 *       for the {@code operation} field), {@code groupOthers}</li>
 *   <li>{@code edit_sql_query} — {@code table}, {@code expression} (new SQL string)</li>
 *   <li>{@code update_mirror} — {@code table}</li>
 *   <li>{@code set_table_mode} — {@code table}, {@code mode} ({@code "live"}, {@code "default"}, {@code "full"}, {@code "detail"}, {@code "edit"})</li>
 *   <li>{@code edit_unpivot} — {@code table}, {@code headerColumns}</li>
 *   <li>{@code insert_column} — {@code table}, {@code index}, {@code insert} (true=before index, false=append after index)</li>
 *   <li>{@code reorder_concat_subtables} — {@code table} (parent composite assembly), {@code subtables} (new order)</li>
 *   <li>{@code auto_layout} — no required fields (lays out all assemblies)</li>
 *   <li>{@code refresh_data} — {@code table} (optional, if omitted refreshes all)</li>
 * </ul>
 */
public record EditRequest(
   /** Discriminator — one of the op values listed above. */
   String op,
   /** Target assembly name (most ops). */
   String table,
   /** Column attribute name (remove_column, rename_column). */
   String column,
   /**
    * New column / join assembly / expression column name (add_column, rename_column,
    * add_join, add_expression_column). Required for all of these EXCEPT add_column on
    * an embedded table, where a blank value auto-generates {@code "col" + N}.
    */
   String name,
   /** Data type string, e.g. {@code "string"}, {@code "integer"} (add_column, add_expression_column). */
   String type,
   /**
    * The new name for a rename: the column's for {@code rename_column}, the table's for
    * {@code set_table_properties} -- where it is a rename because a worksheet table has no display
    * name of its own, the same shape as the Composer's table-properties dialog, whose model carries
    * {@code newName}/{@code oldName} beside the other properties.
    */
   String newName,
   /** Column name for filter / sort operations. */
   String field,
   /** Comparison operator for add_filter, e.g. {@code "="}, {@code "!="}. */
   String operation,
   /** Literal values for add_filter / edit_condition. */
   List<String> values,
   /** Sort direction — {@code "ASC"} or {@code "DESC"} — for set_sort. */
   String direction,
   /**
    * Group-by column specs for set_group_aggregate. Each entry is either a bare column
    * name string, or an object {@code {"field": ..., "dateLevel": ...}} where
    * {@code dateLevel} groups a date column directly at a coarser granularity (e.g.
    * {@code "QUARTER"}) — same option strings as add_date_range_column's dateOption.
    */
   List<WorksheetMutationSupport.GroupSpec> groups,
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
   /** Numeric bucket boundaries for add_numeric_range_column / edit_numeric_range_column. */
   double[] boundaries,
   /** Datasource name for add_table (when provided, creates a PhysicalBoundTableAssembly). */
   String datasource,
   /** Schema name for add_table (e.g. "SA", "dbo", "public"). */
   String schema,
   /** Catalog name for add_table. */
   String catalog,
   /** Logical model name for add_table (when provided alongside datasource, creates a BoundTableAssembly from the logical model entity). */
   String logicalModel,
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
   /**
    * Accepted spelling of {@code newName} for {@code set_table_properties}, kept because callers
    * reach for it. A worksheet table has no display name apart from its name, so setting an alias
    * is a rename; {@code newName} wins when both are given.
    */
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
   Integer y,
   /** Display label for edit_variable. */
   String label,
   /** Default value for edit_variable. */
   String defaultValue,
   /** Table mode string for set_table_mode — {@code "live"}, {@code "default"}, {@code "full"}, {@code "detail"}, {@code "edit"}. */
   String mode,
   /** True = insert before index, false = append after index (insert_column). */
   Boolean insert,
   /** New order of subtable names for reorder_concat_subtables. */
   List<String> subtables,
   /**
    * Entity name (when {@code logicalModel} is given) or physical table name (otherwise) for
    * add_named_group, when scoping the grouping directly to a datasource path — matching what a
    * human produces via the Composer's own "Add Grouping" dialog ("Only For") — instead of
    * attaching to a column on an existing worksheet table. Requires {@code datasource} and
    * {@code attribute}; mutually exclusive with {@code table}/{@code column} and {@code type}.
    */
   String sourceTable,
   /**
    * Attribute/column name within {@code sourceTable} for add_named_group's datasource-scoped
    * mode (see {@code sourceTable}).
    */
   String attribute,
   /**
    * Endpoint name for add_table when binding a NAMED REST/JSON connector's pre-built endpoint
    * catalogue (see {@code list_endpoint_lookups}) — a pre-built endpoint from that connector's
    * own catalogue. When provided, {@code datasource} must name a tabular/REST datasource with
    * an endpoint catalogue, {@code table} is used as the NEW worksheet table's name (not a
    * physical table path — an endpoint has no physical path), and {@code schema}/{@code catalog}/
    * {@code logicalModel}/{@code suffix} must be absent.
    */
   String endpoint,
   /**
    * Parameter values for {@code endpoint}, keyed by the endpoint's own parameter name (e.g.
    * {@code {"owner": "inetsoft-technology", "repo": "stylebi"}} for GitHub's
    * {@code Repository Issue Events}). Only meaningful with {@code endpoint} set; a name the
    * endpoint does not declare is rejected rather than dropped, and a required parameter with no
    * supplied value is rejected rather than guessed. Not yet supported on the generic/custom
    * {@code suffix} path -- see {@code suffix}'s own doc comment.
    */
   Map<String, String> parameters,
   /**
    * Ordered "Join With" lookup chain to graft onto {@code endpoint}, one pre-built connector
    * lookup name per nesting level (e.g. {@code ["Issue Event"]}, or
    * {@code ["Repositories", "Contributors"]} for a two-level chain). Each name must be one of
    * the CURRENT position's valid choices — {@code list_endpoint_lookups} reports them. Max
    * depth 5. Only for add_table with {@code endpoint} set; authoring a brand-new lookup a
    * connector does not already ship uses {@code customLookups} instead.
    */
   List<String> lookup,
   /**
    * Only meaningful with {@code lookup}: whether the LAST lookup's matched array expands into
    * extra rows. Omit to keep the connector's own default ({@code true}).
    */
   Boolean lookupExpandArrays,
   /**
    * Only meaningful with {@code lookup} and {@code lookupExpandArrays}: whether only the
    * top-level array is expanded. Omit to keep the connector's own default ({@code true}).
    */
   Boolean lookupTopLevelOnly,
   /**
    * URL suffix template for add_table on a GENERIC/CUSTOM REST-JSON datasource (one with no
    * predefined endpoint catalogue — see {@code list_endpoint_lookups}' {@code hasEndpointCatalog}
    * flag). Mutually exclusive with {@code endpoint}; use exactly one of the two. Unlike
    * {@code endpoint}, this path has no declared parameter contract to validate against, so
    * {@code parameters} does not apply here -- any {@code {name}} placeholder must already be
    * filled in {@code suffix} itself.
    */
   String suffix,
   /**
    * Ordered custom "Join With" lookup chain for a GENERIC/CUSTOM REST-JSON datasource's
    * {@code suffix}-defined endpoint. Unlike {@code lookup} (named-connector chains, by name),
    * each entry here hand-authors one level: {@code url} (must contain the literal placeholder
    * {@code {param1}} for level 0, {@code {param2}} for level 1, etc. — 1-indexed by position —
    * to receive the id extracted from the parent row), {@code jsonPath} (selects the parent
    * row's array/entity to iterate), {@code key} (extracts each item's id from that
    * {@code jsonPath}), {@code ignoreBaseUrl} (true if {@code url} is a full URL rather than a
    * suffix appended to the datasource's base URL). Max 5 entries. Only valid together with
    * {@code suffix}.
    */
   List<WorksheetMutationSupport.CustomLookupSpec> customLookups,
   /**
    * {@code true} to display set_group_aggregate's result as a crosstab (row/column
    * headers) rather than a flat grouped table — the Composer's own Group and Aggregate
    * dialog "Switch to Crosstab" toggle. Defaults to {@code false} when omitted. Takes visible
    * effect only once {@code groups} has at least 2 entries and {@code aggregates} at least 1;
    * with fewer, it is accepted but silently has no effect (same as the Composer dialog itself).
    */
   Boolean crosstab,
   /**
    * Optional custom bucket labels for add_numeric_range_column / edit_numeric_range_column —
    * one more entry than {@code boundaries} (below the first, one between each pair, above the
    * last). Omitted or empty keeps the engine's default auto-generated range text.
    */
   List<String> labels,
   /**
    * The variable's enumerated "Values" picker for edit_variable — either an embedded list or
    * a query against an existing worksheet table's columns. {@code null} leaves it unchanged.
    * See {@link WorksheetMutationSupport.VariableChoicesSpec}.
    */
   WorksheetMutationSupport.VariableChoicesSpec choices,
   /**
    * Join edges for an N-ary add_join (three or more tables joined into one assembly in a
    * single call). When present, this supersedes {@code leftTable}/{@code leftKey}/
    * {@code rightTable}/{@code rightKey}/{@code joinType}/{@code leftKeys}/{@code rightKeys}.
    */
   List<WorksheetMutationSupport.JoinPathSpec> joinPaths,
   /**
    * Whether the table's query is merged into a single SQL statement for set_table_properties —
    * the Composer's own table-properties dialog "Mergeable" checkbox
    * ({@link inetsoft.uql.asset.TableAssembly#setSQLMergeable}). {@code null} leaves it
    * unchanged, matching {@code distinct}.
    */
   Boolean mergeable,
   /**
    * Whether the table is exposed to viewsheets bound to this worksheet, for
    * set_table_properties — the Composer's own table-properties dialog "Visible in Viewsheet"
    * checkbox ({@link inetsoft.uql.asset.TableAssembly#setVisibleTable}). {@code null} leaves it
    * unchanged, matching {@code distinct}/{@code mergeable}. Distinct from {@code visible}, which
    * is a per-column flag for set_column_visibility.
    */
   Boolean visibleInViewsheet,
   /**
    * Multiple ranking conditions for {@code set_rankings} (plural) — {@code set_ranking}
    * (singular, {@code ranking} above) still replaces the table's whole ranking list with a
    * single entry, unchanged, for every existing caller. {@code set_rankings} replaces it with
    * as many entries as given, in order, so more than one independent ranked field can be
    * established in a single call instead of the second overwriting the first. {@code null} or
    * absent is equivalent to an empty list (clears all ranking).
    */
   List<WorksheetMutationSupport.RankingSpec> rankings
) {
   /**
    * Compatibility constructor for callers built before {@code rankings} was added —
    * defaults it to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups, Boolean crosstab,
      List<String> labels, WorksheetMutationSupport.VariableChoicesSpec choices,
      List<WorksheetMutationSupport.JoinPathSpec> joinPaths, Boolean mergeable,
      Boolean visibleInViewsheet)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, crosstab, labels, choices, joinPaths,
           mergeable, visibleInViewsheet, null);
   }

   /**
    * Compatibility constructor for callers built before {@code visibleInViewsheet} was added —
    * defaults it to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups, Boolean crosstab,
      List<String> labels, WorksheetMutationSupport.VariableChoicesSpec choices,
      List<WorksheetMutationSupport.JoinPathSpec> joinPaths, Boolean mergeable)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, crosstab, labels, choices, joinPaths,
           mergeable, null, null);
   }

   /**
    * Compatibility constructor for callers built before {@code mergeable} was added —
    * defaults it to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups, Boolean crosstab,
      List<String> labels, WorksheetMutationSupport.VariableChoicesSpec choices,
      List<WorksheetMutationSupport.JoinPathSpec> joinPaths)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, crosstab, labels, choices, joinPaths, null);
   }

   /**
    * Compatibility constructor for callers built before {@code joinPaths} was added —
    * defaults it to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups, Boolean crosstab,
      List<String> labels, WorksheetMutationSupport.VariableChoicesSpec choices)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, crosstab, labels, choices, null);
   }

   /**
    * Compatibility constructor for callers built before {@code crosstab} was added —
    * defaults {@code crosstab} and {@code labels} (added later, same reason) to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, null, null);
   }

   /**
    * Compatibility constructor for callers built before {@code choices} was added —
    * defaults it to {@code null}.
    */
   public EditRequest(
      String op, String table, String column, String name, String type, String newName,
      String field, String operation, List<String> values, String direction,
      List<WorksheetMutationSupport.GroupSpec> groups,
      List<WorksheetMutationSupport.AggregateSpec> aggregates, String expression, boolean sql,
      String leftTable, String leftKey, String rightTable, String rightKey, String joinType,
      Boolean visible, List<String> tables, String source, String concatType,
      List<WorksheetMutationSupport.ConditionNode> conditions,
      WorksheetMutationSupport.RankingSpec ranking, Integer headerColumns, String dateOption,
      double[] boundaries, String datasource, String schema, String catalog, String logicalModel,
      List<String> leftKeys, List<String> rightKeys, Integer row, Integer col, String value,
      Integer index, String alias, String description, Integer maxRows, Boolean distinct,
      List<String> columnOrder, List<WorksheetMutationSupport.GroupMapping> groupMappings,
      Boolean groupOthers, Map<String, String> variableValues, Integer x, Integer y, String label,
      String defaultValue, String mode, Boolean insert, List<String> subtables,
      String sourceTable, String attribute, String endpoint, Map<String, String> parameters,
      List<String> lookup, Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups, Boolean crosstab,
      List<String> labels)
   {
      this(op, table, column, name, type, newName, field, operation, values, direction, groups,
           aggregates, expression, sql, leftTable, leftKey, rightTable, rightKey, joinType,
           visible, tables, source, concatType, conditions, ranking, headerColumns, dateOption,
           boundaries, datasource, schema, catalog, logicalModel, leftKeys, rightKeys, row, col,
           value, index, alias, description, maxRows, distinct, columnOrder, groupMappings,
           groupOthers, variableValues, x, y, label, defaultValue, mode, insert, subtables,
           sourceTable, attribute, endpoint, parameters, lookup, lookupExpandArrays,
           lookupTopLevelOnly, suffix, customLookups, crosstab, labels, null);
   }
}
