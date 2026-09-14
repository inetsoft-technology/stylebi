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
package inetsoft.web.wiz.binding.model;

/**
 * One disambiguated {@code set_column_labels} entry — the {@code entries} sibling to the plain
 * {@code labels: {column: label}} map, needed when a column is bound more than once (a
 * Year/Quarter drill on the same column) and a bare column name cannot say which occurrence a
 * label belongs to. Mirrors {@code set_table_options}' {@code suppressGroupTotal} array shape.
 *
 * @param shelf  which shelf the column is on ({@code "rows"}/{@code "cols"}/{@code "aggregates"}
 *               for a crosstab, {@code "details"} for a table) — optional; omitted searches every
 *               shelf the same way a bare {@code labels} key does.
 * @param column the column's name, or (aggregates only) either the bare column name or its full
 *               {@code "formula(column)"} display name (e.g. {@code "Sum(Sales)"}).
 * @param index  which occurrence, when {@code column} is bound more than once on the resolved
 *               shelf — the position {@code get_table_binding} reports it at.
 * @param label  the new header text, or an empty string to remove a previously-set label.
 */
public record ColumnLabelEntry(String shelf, String column, Integer index, String label) {
}
