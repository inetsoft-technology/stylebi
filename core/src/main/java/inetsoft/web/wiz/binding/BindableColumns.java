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
 * <p>Scope depends on what the listing knows. When it marks one table {@linkplain BindableTable#current
 * current}, only that table's columns count: an assembly binds fields from exactly one source, and
 * the Composer enforces that by <em>deleting</em> any bound field missing from a newly chosen source
 * ({@code VSAssemblyInfoHandler.validateChartColumns}). The agent write path never runs that
 * validation, so a column belonging to a different table used to land as a ref that resolves to
 * nothing — the same "reads as empty data" failure this class exists to stop, one step further in,
 * and one the name-exists-somewhere check cannot see because such a column is perfectly real.
 *
 * <p>With no table marked current — an unscoped listing, or an assembly with no source yet — it
 * falls back to matching the name across every table. That leniency is load-bearing there rather
 * than lax: without a known source there is nothing to narrow to, and refusing on a guess would
 * block legitimate columns.
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

      List<BindableTable> listed = tables == null ? List.of() : tables;
      String source = currentSourceOf(listed);
      Set<String> available = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for(BindableTable table : listed) {
         if(source != null && !Boolean.TRUE.equals(table.current())) {
            continue;
         }

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
               "'" + field.column() + "' is not a column " + assembly + " can bind" +
               (source == null ? "" : ", which is bound to '" + source + "'") + ". Available: " +
               String.join(", ", available) +
               ". A column the source does not have binds cleanly and then renders nothing, so " +
               "it is refused here rather than left to look like empty data." +
               (source == null ? ""
                  : " Every field on an assembly must come from its one source, so a column of " +
                    "another table is not an option here — either pick one of the above, or " +
                    "repoint the assembly, which discards every field already bound to it."));
         }
      }
   }

   /**
    * Decides which table a write is against, and returns the one to establish as the source.
    *
    * <p>The Composer sets an assembly's source as a side effect of the drag — the drag event carries
    * {@code event.getTable()}, so dropping a column says both "bind this" and "from here" in one
    * gesture ({@code VSChartDndService}). The agent path lost the second half: the listing groups
    * columns <em>by table</em>, so a caller always knew which table it picked from, and the write
    * vocabulary had nowhere to say it. The source was therefore never established and a chart with a
    * correct, readable binding rendered nothing at all. This is that missing half.
    *
    * @param requested the table the caller named, or {@code null} to infer it
    * @return the table to establish, or {@code null} when there is nothing to establish — the
    *         assembly already has a source, nothing is being written, or the listing is empty
    * @throws IllegalArgumentException when the answer is genuinely ambiguous or contradictory.
    *         Never guessed: a coin flip between two tables renders something plausible, which is
    *         worse than refusing, and an implicit repoint would delete every field already bound.
    */
   public static String requireSource(List<BindableTable> tables, String assembly,
                                      String requested, Collection<FieldRef> fields)
   {
      List<BindableTable> listed = tables == null ? List.of() : tables;
      List<FieldRef> named = named(fields);

      // Nothing being written has no source to decide. An empty `listed` used to bail here too,
      // on the theory that no listing meant the tree could not be read (bug #76350, PCB-002) —
      // but resolveSourceTable's own surrounding try/catch already turns a genuine read failure
      // into this same null one layer up, before requireSource ever runs. So an empty `listed`
      // reaching here always means a *successful* listing that is genuinely empty — e.g. a table
      // not yet visible to the viewsheet because it was never saved — which requested != null
      // below already refuses correctly via matchListed's "Available: " message, the same
      // refusal set_chart_source gives for the identical case. Bailing early swallowed that
      // refusal and returned null instead, so the write proceeded with no source and no error,
      // then crashed later on render.
      if(named.isEmpty()) {
         return null;
      }

      String current = currentSourceOf(listed);

      if(current != null) {
         if(requested != null && !requested.equalsIgnoreCase(current)) {
            throw new IllegalArgumentException(
               "'" + assembly + "' is bound to '" + current + "', and '" + requested +
               "' is a different table. Every field on an assembly comes from its one source, so " +
               "this is either the wrong column or a deliberate repoint — and repointing discards " +
               "every field already bound, so it is not done implicitly. Pick a column from '" +
               current + "', or call set_chart_source with force: true to move the assembly.");
         }

         // Already sourced: nothing to establish, and require() narrows the column check to it.
         return null;
      }

      if(requested != null) {
         String resolved = matchListed(listed, requested, assembly);
         requireHasAll(resolved, columnsOf(listed, resolved), named, assembly);

         return resolved;
      }

      List<String> candidates = new ArrayList<>();

      for(BindableTable table : listed) {
         if(hasAll(columnsOf(listed, table.name()), named)) {
            candidates.add(table.name());
         }
      }

      if(candidates.size() == 1) {
         return candidates.get(0);
      }

      if(candidates.isEmpty()) {
         throw new IllegalArgumentException(
            "No single table has " + describe(named) + ", and an assembly binds every field from " +
            "one source. Pick fields that live together in one table — list_bindable_fields groups " +
            "them by table — or bind them in separate assemblies.");
      }

      throw new IllegalArgumentException(
         describe(named) + " exists in more than one table (" + String.join(", ", candidates) +
         "), so which one to bind '" + assembly + "' to cannot be told from the column name alone. " +
         "Pass 'table' to say which, the way dropping a column in the Composer does. Guessing " +
         "would bind the wrong table and render something that looks right.");
   }

   private static List<FieldRef> named(Collection<FieldRef> fields) {
      List<FieldRef> named = new ArrayList<>();

      for(FieldRef field : fields == null ? List.<FieldRef>of() : fields) {
         if(field != null && field.column() != null && !field.column().isBlank()) {
            named.add(field);
         }
      }

      return named;
   }

   private static String matchListed(List<BindableTable> listed, String requested,
                                     String assembly)
   {
      List<String> names = new ArrayList<>();

      for(BindableTable table : listed) {
         names.add(table.name());

         if(requested.equalsIgnoreCase(table.name())) {
            return table.name();
         }
      }

      throw new IllegalArgumentException(
         "'" + assembly + "' cannot bind to '" + requested + "'. Available: " +
         String.join(", ", names) +
         ". A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   private static Set<String> columnsOf(List<BindableTable> listed, String name) {
      Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for(BindableTable table : listed) {
         if(name.equalsIgnoreCase(table.name())) {
            for(var field : table.fields()) {
               if(field.column() != null) {
                  columns.add(field.column());
               }
            }
         }
      }

      return columns;
   }

   private static boolean hasAll(Set<String> columns, List<FieldRef> fields) {
      for(FieldRef field : fields) {
         if(!columns.contains(field.column())) {
            return false;
         }
      }

      return true;
   }

   private static void requireHasAll(String table, Set<String> columns, List<FieldRef> fields,
                                     String assembly)
   {
      for(FieldRef field : fields) {
         if(!columns.contains(field.column())) {
            throw new IllegalArgumentException(
               "'" + field.column() + "' is not a column of '" + table + "', so binding '" +
               assembly + "' to it would leave that field resolving to nothing. Available in '" +
               table + "': " + String.join(", ", columns) + ".");
         }
      }
   }

   private static String describe(List<FieldRef> fields) {
      List<String> columns = new ArrayList<>();

      for(FieldRef field : fields) {
         columns.add("'" + field.column() + "'");
      }

      return columns.size() == 1 ? columns.get(0) : String.join(" + ", columns);
   }

   /** The table the assembly is bound to, or {@code null} when the listing does not say. */
   private static String currentSourceOf(List<BindableTable> tables) {
      for(BindableTable table : tables) {
         if(Boolean.TRUE.equals(table.current())) {
            return table.name();
         }
      }

      return null;
   }
}
