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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.*;

/**
 * Checks that a field being bound names a column the assembly can actually bind.
 *
 * <p>Without this, a column the source does not have was accepted in silence. The shelf stored it,
 * the call reported success, and the assembly rendered with that grouping simply missing — a
 * crosstab with no rows at all, which reads as "the data is empty" rather than "that column does
 * not exist". Verified live by binding {@code NO_SUCH_COLUMN_XYZ}, and hit for real by binding a
 * column belonging to a different worksheet than the one the assembly was pointed at.
 *
 * <p>The check is deliberately lenient about <em>where</em> a column lives: the listing groups
 * columns by table and the same name can appear in several, so matching on the name alone across
 * every table is enough to catch a name that exists nowhere without rejecting a legitimate one.
 */
public final class BindableColumns {
   private BindableColumns() {
   }

   /**
    * @throws IllegalArgumentException naming the column and what is available, if a field names a
    *                                  column that appears in no bindable table.
    */
   public static void require(List<BindableTable> tables, String assembly, FieldRef... fields) {
      require(tables, assembly, fields == null ? List.of() : Arrays.asList(fields));
   }

   public static void require(List<BindableTable> tables, String assembly,
                              Collection<FieldRef> fields)
   {
      if(fields == null || fields.isEmpty()) {
         return;
      }

      Set<String> available = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for(BindableTable table : tables == null ? List.<BindableTable>of() : tables) {
         for(var field : table.fields()) {
            if(field.column() != null) {
               available.add(field.column());
            }
         }
      }

      // No listing at all means the tree could not be read — a different failure, and refusing
      // every column on the strength of it would turn a read problem into a write problem.
      if(available.isEmpty()) {
         return;
      }

      for(FieldRef field : fields) {
         if(field == null || field.column() == null || field.column().isBlank()) {
            continue;
         }

         if(!available.contains(field.column())) {
            throw new IllegalArgumentException(
               "'" + field.column() + "' is not a column " + assembly + " can bind. Available: " +
               String.join(", ", available) +
               ". A column the source does not have binds cleanly and then renders nothing, so " +
               "it is refused here rather than left to look like empty data.");
         }
      }
   }
}
