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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.JoinDef;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;

import java.util.*;

/**
 * Turns the Auto Join candidates of the Portal's backend into the joins the Portal's Auto Join
 * Tables dialog builds in the browser from the columns the user ticks. The backend only lists
 * candidate columns; this class is the dialog's assembly step.
 *
 * <p>Name mode: every ticked column name gives one join per pair of the tables that share it.
 * Metadata mode: every ticked {@code table.column} gives one join per key it references. A pair
 * already joined on the same columns (in either direction) is skipped, so is a self join and a
 * key that points at a table that is not in the view; each skip is listed. Joins carry no
 * cardinality: the writer fills it from the primary keys, as the dialog's server call does.
 * Unlike the dialog, which starts with nothing ticked, no {@code columns} means all candidates.</p>
 *
 * <p>Pure: no service, no runtime. The order of every list is fixed (tables in the order of the
 * view, columns by name), because the server collects candidates in a hash map.</p>
 */
final class WizAutoJoinProposal {
   static final String MODE_NAME = "name";
   static final String MODE_METADATA = "metadata";

   private WizAutoJoinProposal() {
   }

   /**
    * @param tables   the table names of the view, in the order the proposal keeps.
    * @param existing the joins the view already has.
    * @param mode     {@code name}, {@code metadata} or null for metadata when available.
    * @param columns  the candidate labels to join on, or null/empty for all.
    *
    * @throws WizDataModelErrors.DmFailure {@code INVALID} for an unknown mode or column label,
    *                                      or metadata mode without key metadata.
    */
   static AutoJoinResponse propose(List<NameColumn> nameColumns, List<MetaColumn> metaColumns,
                                   boolean metaAvailable, List<String> tables,
                                   List<JoinDef> existing, String mode, List<String> columns)
   {
      String resolved = resolveMode(mode, metaAvailable);
      Map<String, Integer> order = new HashMap<>();

      for(String table : tables) {
         order.putIfAbsent(table, order.size());
      }

      List<NameColumn> names = sortedNames(nameColumns, order);
      List<MetaColumn> metas = sortedMetas(metaColumns);
      Set<String> wanted = requireKnown(resolved, columns, names, metas);
      Set<String> joined = joinedPairs(existing);
      List<ProposedJoin> joins = new ArrayList<>();
      List<SkippedJoin> skipped = new ArrayList<>();
      List<String> notes = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      if(MODE_NAME.equals(resolved)) {
         for(NameColumn candidate : names) {
            if(!wanted.isEmpty() && !wanted.contains(candidate.column())) {
               continue;
            }

            List<String> shared = candidate.tables();

            if(shared.size() > 2) {
               notes.add(shared.size() + " tables share column \"" + candidate.column() +
                         "\": every pair is proposed, drop the ones that are not wanted");
            }

            for(int i = 0; i < shared.size(); i++) {
               for(int j = i + 1; j < shared.size(); j++) {
                  add(joins, skipped, seen, joined, shared.get(i), candidate.column(),
                      shared.get(j), candidate.column());
               }
            }
         }
      }
      else {
         for(MetaColumn candidate : metas) {
            if(!wanted.isEmpty() &&
               !wanted.contains(candidate.table() + "." + candidate.column()))
            {
               continue;
            }

            for(ColumnRef ref : candidate.references()) {
               if(candidate.table().equals(ref.table())) {
                  skipped.add(new SkippedJoin("SELF_JOIN", candidate.table(), candidate.column(),
                                              ref.table(), ref.column()));
               }
               else if(!order.containsKey(ref.table())) {
                  skipped.add(new SkippedJoin("TABLE_NOT_IN_VIEW", candidate.table(),
                                              candidate.column(), ref.table(), ref.column()));
               }
               else {
                  add(joins, skipped, seen, joined, candidate.table(), candidate.column(),
                      ref.table(), ref.column());
               }
            }
         }
      }

      if(joins.isEmpty() && skipped.isEmpty()) {
         notes.add(names.isEmpty() && metas.isEmpty() ?
                      "no candidate columns: the tables share no column name and carry no key " +
                         "metadata that links them" :
                      "no join to propose for the chosen columns");
      }

      return new AutoJoinResponse(true, resolved, metaAvailable, names, metas, joins, skipped,
                                  notes);
   }

   private static void add(List<ProposedJoin> joins, List<SkippedJoin> skipped, Set<String> seen,
                           Set<String> joined, String table1, String column1, String table2,
                           String column2)
   {
      String key = key(table1, column1, table2, column2);

      if(joined.contains(key)) {
         skipped.add(new SkippedJoin("ALREADY_JOINED", table1, column1, table2, column2));
      }
      else if(seen.add(key)) {
         joins.add(new ProposedJoin(table1, column1, table2, column2, "EQUAL"));
      }
   }

   private static String resolveMode(String mode, boolean metaAvailable) {
      String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);

      if(value.isEmpty()) {
         return metaAvailable ? MODE_METADATA : MODE_NAME;
      }

      if(!MODE_NAME.equals(value) && !MODE_METADATA.equals(value)) {
         throw invalid("mode", "MODE_INVALID",
                       "mode \"" + mode + "\" is not known; valid modes: name, metadata");
      }

      if(MODE_METADATA.equals(value) && !metaAvailable) {
         throw invalid("mode", "MODE_UNAVAILABLE",
                       "mode metadata needs key metadata and none of the tables has any; " +
                          "use mode name");
      }

      return value;
   }

   /** The labels asked for; an unknown one fails the call naming the valid ones. */
   private static Set<String> requireKnown(String mode, List<String> columns,
                                           List<NameColumn> names, List<MetaColumn> metas)
   {
      Set<String> wanted = new LinkedHashSet<>();

      if(columns == null || columns.isEmpty()) {
         return wanted;
      }

      List<String> valid = new ArrayList<>();

      if(MODE_NAME.equals(mode)) {
         names.forEach(c -> valid.add(c.column()));
      }
      else {
         metas.forEach(c -> valid.add(c.table() + "." + c.column()));
      }

      List<String> unknown = new ArrayList<>();

      for(String label : columns) {
         if(valid.contains(label)) {
            wanted.add(label);
         }
         else {
            unknown.add(label);
         }
      }

      if(!unknown.isEmpty()) {
         throw invalid("columns", "COLUMN_NOT_CANDIDATE",
                       "not a candidate in mode " + mode + ": " + unknown + "; candidates: " +
                          (valid.size() > 50 ? valid.subList(0, 50) + " ..." : valid));
      }

      return wanted;
   }

   private static List<NameColumn> sortedNames(List<NameColumn> names, Map<String, Integer> order) {
      List<NameColumn> result = new ArrayList<>();

      for(NameColumn name : names == null ? List.<NameColumn>of() : names) {
         List<String> tables = new ArrayList<>(new LinkedHashSet<>(name.tables()));
         tables.sort(Comparator.<String>comparingInt(t -> order.getOrDefault(t, Integer.MAX_VALUE))
                        .thenComparing(Comparator.naturalOrder()));
         result.add(new NameColumn(name.column(), tables));
      }

      result.sort(Comparator.comparing(NameColumn::column));
      return result;
   }

   private static List<MetaColumn> sortedMetas(List<MetaColumn> metas) {
      List<MetaColumn> result = new ArrayList<>();

      for(MetaColumn meta : metas == null ? List.<MetaColumn>of() : metas) {
         List<ColumnRef> refs = new ArrayList<>(meta.references());
         refs.sort(Comparator.comparing(ColumnRef::table).thenComparing(ColumnRef::column));
         result.add(new MetaColumn(meta.table(), meta.column(), refs));
      }

      result.sort(Comparator.comparing(MetaColumn::table).thenComparing(MetaColumn::column));
      return result;
   }

   private static Set<String> joinedPairs(List<JoinDef> existing) {
      Set<String> pairs = new HashSet<>();

      for(JoinDef join : existing == null ? List.<JoinDef>of() : existing) {
         pairs.add(key(join.fromTable(), join.fromColumn(), join.toTable(), join.toColumn()));
      }

      return pairs;
   }

   /** The two endpoints without a direction: a join and its reverse are the same pair. */
   private static String key(String table1, String column1, String table2, String column2) {
      String a = table1 + "|" + column1;
      String b = table2 + "|" + column2;
      return a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a;
   }

   /** An {@code INVALID} answer about one argument of the request. */
   static WizDataModelErrors.DmFailure invalid(String field, String code, String message) {
      return new WizDataModelErrors.DmFailure(
         WizDataModelWire.INVALID, field, message, List.of(new Err(field, message, code)), null);
   }
}
