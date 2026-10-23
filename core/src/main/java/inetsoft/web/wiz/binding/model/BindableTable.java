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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One source table and the columns it offers.
 *
 * @param current whether this is the table the scoped assembly is bound to right now, or
 *                {@code null} when no assembly was named. Every field on an assembly's shelves has
 *                to come from its single source — the Composer enforces that by deleting any bound
 *                field absent from a newly chosen source — and listing all the tables is still
 *                correct, since an assembly may be repointed to any of them. So the flag is what
 *                makes the constraint followable without a second call to read the binding.
 *                {@code null} rather than {@code false} for an unscoped call: there is no assembly
 *                to be current for, and {@code false} would assert the opposite of the truth.
 * @param warnings why this table's {@code fields} cannot be trusted to be complete (bug #78640), for
 *                 instance a viewsheet component whose script failed so it has no runtime columns.
 *                 Omitted from the JSON when empty. A warned table means "columns unknown", not
 *                 "has no columns".
 * @param sourceType {@code "viewsheet"} when this table is another component of the same
 *                   viewsheet (a Table/Crosstab/Calc Table whose rendered output is bound as a
 *                   source, the Composer's "Components" folder), and omitted for a worksheet table
 *                   or model entity. The name alone cannot say which: a component and a worksheet
 *                   table may share one, and each is stored as a different kind of source.
 *                   {@code "viewsheet_aggregate"} when it is a Chart or Crosstab whose aggregated
 *                   rows a range slider filters on (the range slider's "Components" node); listed
 *                   only for a range slider, and its columns are aggregate names such as
 *                   {@code Sum(Product:Total)}.
 */
public record BindableTable(String name, Boolean current, List<BindableField> fields,
                            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> warnings,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String sourceType)
{
   public static final String VIEWSHEET = "viewsheet";
   public static final String VIEWSHEET_AGGREGATE = "viewsheet_aggregate";

   public BindableTable {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
   }

   public BindableTable(String name, Boolean current, List<BindableField> fields,
                        List<String> warnings)
   {
      this(name, current, fields, warnings, null);
   }

   public BindableTable(String name, Boolean current, List<BindableField> fields) {
      this(name, current, fields, List.of(), null);
   }

   /** Whether this table is another component of the viewsheet rather than a worksheet table. */
   public boolean isComponent() {
      return VIEWSHEET.equals(sourceType);
   }

   /** Whether this table is a Chart/Crosstab's aggregated output, a range slider source. */
   public boolean isAggregate() {
      return VIEWSHEET_AGGREGATE.equals(sourceType);
   }

   /** Whether this table's column list is known to be incomplete. */
   public boolean hasWarnings() {
      return !warnings.isEmpty();
   }
}
