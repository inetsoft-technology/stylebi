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
 *
 * <p>A table that carries {@linkplain BindableTable#warnings warnings} has unknown columns, not no
 * columns (bug #78640: a component whose script failed lists none). Where the check would have to
 * trust such a table's field list it lets the write through, the way a listing failure already
 * does, rather than refuse every field with "Available: ."
 *
 * <p>A {@code "$(ComponentName)"} or {@code "=script"} shelf field is the one exception: it is a
 * dynamic reference ({@code VSUtil.isDynamic}), not a column name at all, and is resolved against
 * a real value only at render time
 * ({@code VSUtil.isVariableValue}/{@code DynamicValue}) — checking it against the source's schema
 * here would refuse a legitimate binding as if it were a typo'd or wrong-table column (#76641).
 * The same exception applies when the assembly has no source established yet: {@link
 * #requireSource} decides which table to bind by inferring from the fields' names ({@code hasAll})
 * or checking them against an explicitly named table ({@code requireHasAll}), and a dynamic
 * reference is not evidence either way there either — it neither disqualifies a candidate table nor
 * makes an otherwise-wrong one match, so it is skipped in both checks the same way.
 */
public final class BindableColumns {
   private BindableColumns() {
   }

   public static final String CHART_REPOINT_TOOL = "set_chart_source";
   public static final String TABLE_REPOINT_TOOL = "set_table_source";

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
      require(tables, assembly, CHART_REPOINT_TOOL, fields);
   }

   /**
    * @param repointTool the tool that moves this kind of assembly to another source, named in the
    *                    refusal so it is not a dead end ({@code set_chart_source} refuses a
    *                    crosstab, {@code set_table_source} refuses a chart)
    */
   public static void require(List<BindableTable> tables, String assembly, String repointTool,
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

         if(table.hasWarnings()) {
            // Columns unknown, so a missing column proves nothing: fail open.
            return;
         }

         for(var field : table.fields()) {
            if(field.column() != null) {
               available.add(field.column());
            }
         }
      }

      for(FieldRef field : fields) {
         if(field == null || field.column() == null || field.column().isBlank()) {
            continue;
         }

         if(isDynamicColumn(field.column())) {
            // "$(ComponentName)" or "=script" -- not a real column name to check against the
            // source's schema;
            // resolved at render time instead (#76641).
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
                    "repoint the assembly with " + repointTool + "."));
         }
      }
   }

   /**
    * Replaces each field's column with the source's real spelling, so a name that only differs by
    * case is stored as the column it unambiguously identifies (#78436).
    *
    * <p>{@link #require} matches case-insensitively, so a mis-cased name used to pass validation
    * and then be persisted verbatim; anything keyed by the exact stored spelling (for example a
    * crosstab's {@code suppressGroupTotal}) then silently stopped matching. The comparison here
    * is against a case-sensitive column list scoped like {@link #require}/{@link #requireSource}
    * (the current table, else the table just resolved as the source, else every table): an exact
    * match always wins, a single case-insensitive match is replaced by its real spelling, several
    * case-insensitive matches with no exact one are refused, and a name with no match at all is
    * left untouched for {@link #require} to refuse. Dynamic {@code "$(...)"} references and
    * {@code "=script"} names are not column names and are never rewritten.
    *
    * @param resolvedSource the table {@link #requireSource} resolved, or {@code null}
    */
   public static List<FieldRef> canonicalize(List<BindableTable> tables, String assembly,
                                             String resolvedSource, List<FieldRef> fields)
   {
      if(fields == null || fields.isEmpty()) {
         return fields;
      }

      List<BindableTable> listed = tables == null ? List.of() : tables;
      String scope = currentSourceOf(listed);

      if(scope == null) {
         scope = resolvedSource;
      }

      Set<String> columns = new LinkedHashSet<>();

      for(BindableTable table : listed) {
         if(scope != null && !isKey(table, scope)) {
            continue;
         }

         for(var field : table.fields()) {
            if(field.column() != null) {
               columns.add(field.column());
            }
         }
      }

      List<FieldRef> result = new ArrayList<>(fields.size());

      for(FieldRef field : fields) {
         result.add(canonicalize(columns, assembly, field));
      }

      return result;
   }

   private static FieldRef canonicalize(Set<String> columns, String assembly, FieldRef field) {
      if(field == null) {
         return field;
      }

      String column = canonicalName(columns, assembly, field.column());
      String secondary = canonicalName(columns, assembly, field.secondaryColumn());

      if(Objects.equals(column, field.column()) &&
         Objects.equals(secondary, field.secondaryColumn()))
      {
         return field;
      }

      return field.withColumns(column, secondary);
   }

   /**
    * The real spelling of {@code name}, or {@code name} itself when it is not a plain column name
    * or matches nothing. Applies to {@code secondaryColumn} too: the runtime resolves it with a
    * case-sensitive {@code ColumnSelection.getAttribute}, so a mis-cased one would silently lose
    * the second column of a two-column aggregate.
    */
   private static String canonicalName(Set<String> columns, String assembly, String name) {
      if(name == null || name.isBlank() || isDynamicVariableReference(name) ||
         name.startsWith("=") || columns.contains(name))
      {
         return name;
      }

      List<String> matches = new ArrayList<>();

      for(String column : columns) {
         if(column.equalsIgnoreCase(name)) {
            matches.add(column);
         }
      }

      if(matches.size() > 1) {
         throw new IllegalArgumentException(
            "'" + name + "' is ambiguous for " + assembly + ": it matches " +
            String.join(", ", matches) + " only by case. Use the exact spelling of one of them.");
      }

      return matches.isEmpty() ? name : matches.get(0);
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
      return requireSource(tables, assembly, requested, CHART_REPOINT_TOOL, fields);
   }

   /**
    * @param repointTool the tool that moves this kind of assembly to another source
    *                    ({@code set_chart_source} or {@code set_table_source}), named in the
    *                    repoint refusal
    */
   public static String requireSource(List<BindableTable> tables, String assembly,
                                      String requested, String repointTool,
                                      Collection<FieldRef> fields)
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
         // The bare name of the bound source is that source, even when a worksheet table and a
         // component share it: naming what is already bound is not a repoint.
         // Resolve the request first, then compare exactly: Customers and CUSTOMERS are two tables,
         // so naming one while bound to the other is a different table (bug #79192).
         if(requested != null && !current.equals(lookup(listed, requested)) &&
            !requested.equals(current) &&
            !requested.equals(BindingSources.displayName(current)))
         {
            throw new IllegalArgumentException(
               "'" + assembly + "' is bound to '" + BindingSources.displayName(current) +
               "', and '" + requested +
               "' is a different table. Every field on an assembly comes from its one source, so " +
               "this is either the wrong column or a deliberate repoint — and repointing discards " +
               "the bound fields the new table does not have, so it is not done implicitly. " +
               "Pick a column from '" + BindingSources.displayName(current) + "', or call " +
               repointTool + " with force: true to move the assembly.");
         }

         // Already sourced: nothing to establish, and require() narrows the column check to it.
         return null;
      }

      if(requested != null) {
         String resolved = matchListed(listed, requested, assembly);

         if(!hasWarnings(listed, resolved)) {
            requireHasAll(resolved, columnsOf(listed, resolved), named, assembly);
         }

         return resolved;
      }

      List<String> candidates = new ArrayList<>();
      List<String> labels = new ArrayList<>();

      for(BindableTable table : listed) {
         if(hasAll(columnsOf(listed, keyOf(table)), named)) {
            candidates.add(keyOf(table));
            // A viewsheet component may share a worksheet table's name; say which is which.
            labels.add(table.isComponent() ? table.name() + " (viewsheet component)"
                                           : table.name());
         }
      }

      if(candidates.size() == 1) {
         return candidates.get(0);
      }

      if(candidates.isEmpty()) {
         if(listed.stream().anyMatch(BindableTable::hasWarnings)) {
            // The column may live in a table whose columns are unknown; do not guess.
            return null;
         }

         throw new IllegalArgumentException(
            "No single table has " + describe(named) + ", and an assembly binds every field from " +
            "one source. Pick fields that live together in one table — list_bindable_fields groups " +
            "them by table — or bind them in separate assemblies.");
      }

      throw new IllegalArgumentException(
         describe(named) + " exists in more than one table (" + String.join(", ", labels) +
         "), so which one to bind '" + assembly + "' to cannot be told from the column name alone. " +
         "Pass 'table' to say which, the way dropping a column in the Composer does" +
         (candidates.stream().anyMatch(BindingSources::isComponent) ?
            " (a worksheet table by its name, a viewsheet component as '" +
            BindingSources.componentSource("<name>") + "')" : "") +
         ". Guessing would bind the wrong table and render something that looks right.");
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
      String key = lookup(listed, requested);

      if(key != null) {
         return key;
      }

      List<String> names = new ArrayList<>();

      for(BindableTable table : listed) {
         names.add(table.name());
      }

      throw new IllegalArgumentException(
         "'" + assembly + "' cannot bind to '" + requested + "'. Available: " +
         String.join(", ", names) +
         ". A source the assembly cannot see binds nothing and renders an empty assembly.");
   }

   /**
    * The listed table a caller-stated name selects, as its {@link #keyOf key}, or {@code null}.
    * A worksheet table and a viewsheet component may share a name (bug #79022): the bare name
    * selects the worksheet table, as {@code set_table_source} does, and the component's stored,
    * {@code __vs_assembly__}-prefixed name selects the component. A bare name no worksheet table
    * has selects the component of that name.
    */
   static String lookup(List<BindableTable> listed, String requested) {
      if(requested == null) {
         return null;
      }

      List<BindableTable> components = new ArrayList<>();
      List<BindableTable> worksheet = new ArrayList<>();

      for(BindableTable table : listed) {
         (table.isComponent() ? components : worksheet).add(table);
      }

      if(BindingSources.isComponent(requested)) {
         BindableTable table = TableNames.resolveTable(components,
                                                       BindingSources.displayName(requested));
         return table == null ? null : keyOf(table);
      }

      // Exact beats case-insensitive (bug #79192): worksheet tables Customers and CUSTOMERS are
      // two tables, and a name only differing by case is ambiguous rather than a first match.
      BindableTable table = TableNames.resolveTable(worksheet, requested);

      if(table != null && table.name().equals(requested)) {
         return keyOf(table);
      }

      BindableTable component = TableNames.resolveTable(components, requested);

      if(component != null && component.name().equals(requested)) {
         return keyOf(component);
      }

      table = table != null ? table : component;
      return table == null ? null : keyOf(table);
   }

   /**
    * A listed table's identity: a viewsheet component's stored, {@code __vs_assembly__}-prefixed
    * source name, else its own name. The bare name alone cannot tell a component from a
    * worksheet table of the same name.
    */
   static String keyOf(BindableTable table) {
      return table.isComponent() ? BindingSources.componentSource(table.name()) : table.name();
   }

   private static boolean isKey(BindableTable table, String key) {
      // Exact: the key is already a resolved listed name, and two tables may differ only by case.
      return key != null && key.equals(keyOf(table));
   }

   private static boolean hasWarnings(List<BindableTable> listed, String name) {
      for(BindableTable table : listed) {
         if(isKey(table, name) && table.hasWarnings()) {
            return true;
         }
      }

      return false;
   }

   private static Set<String> columnsOf(List<BindableTable> listed, String name) {
      Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for(BindableTable table : listed) {
         if(isKey(table, name)) {
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
         if(isDynamicColumn(field.column())) {
            // "$(ComponentName)" carries no information about which table is correct -- it is not
            // a schema column of any table, so it must not veto a candidate table the other,
            // literal fields do match (#76641).
            continue;
         }

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
         if(isDynamicColumn(field.column())) {
            // "$(ComponentName)" or "=script" -- not a real column name to check against the
            // source's schema;
            // resolved at render time instead (#76641).
            continue;
         }

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

   /**
    * Mirrors {@code inetsoft.uql.viewsheet.internal.VSUtil.isVariableValue(String)} exactly
    * ({@code "$(" ... ")"} test) rather than calling it, for the same reason
    * {@code PropertyPath.isDynamicVariableReference} does: {@code VSUtil}'s static initializer
    * reaches for a live Spring {@code ApplicationContext} ({@code ConfigurationContext.getSpringBean}
    * via a {@code DataCache} field), which throws on the first static call into the class from this
    * file's plain {@code BindableColumnsTest} (no Spring context bootstrapped). Duplicating this
    * one-line, stable predicate avoids forcing this whole test file onto a Spring-backed harness.
    */
   private static boolean isDynamicVariableReference(String text) {
      return text != null && text.startsWith("$(") && text.endsWith(")");
   }

   static boolean isDynamicColumn(String text) {
      return isDynamicVariableReference(text) || text != null && text.startsWith("=");
   }

   /** The table the assembly is bound to, or {@code null} when the listing does not say. */
   private static String currentSourceOf(List<BindableTable> tables) {
      for(BindableTable table : tables) {
         if(Boolean.TRUE.equals(table.current())) {
            return keyOf(table);
         }
      }

      return null;
   }
}
