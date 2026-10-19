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

import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.CompositeTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.WorksheetControllerService.AggregateInputLossConflict;
import inetsoft.web.composer.ws.WorksheetControllerService.ColumnReferenceLossConflict;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.uql.asset.internal.AssetUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bug #78357: what a column that is about to disappear from a table is still used for. Shared by
 * {@code remove_column}'s confirmation text and the replace-import refusal, so both name
 * dependents the same way. Bug #78527: also by the edit_sql_query / edit_table refusal and the
 * edit_date_range_column join-key guard, each checking its own {@link Scope} of dependents.
 */
final class ColumnLossReport {
   private ColumnLossReport() {
   }

   /**
    * The downstream tables that would break, as {@code 'T' (aggregate/group-by on a, b); 'U'
    * (pre-condition on a)}, or an empty string when there are none.
    */
   static String dependentsClause(List<AggregateInputLossConflict> aggregateConflicts,
                                  List<ColumnReferenceLossConflict> referenceConflicts)
   {
      StringBuilder sb = new StringBuilder();

      for(AggregateInputLossConflict conflict : aggregateConflicts) {
         if(sb.length() > 0) {
            sb.append("; ");
         }

         sb.append("'").append(conflict.dependentAssemblyName()).append("' (aggregate/group-by on ")
            .append(String.join(", ", conflict.lostColumns())).append(")");
      }

      for(ColumnReferenceLossConflict conflict : referenceConflicts) {
         if(sb.length() > 0) {
            sb.append("; ");
         }

         sb.append("'").append(conflict.dependentAssemblyName()).append("' (")
            .append(String.join(", ", conflict.references())).append(")");
      }

      return sb.toString();
   }

   /**
    * Which kinds of dependent a column loss is checked against (Bug #78527). Downstream
    * conditions, rankings, expressions and range columns are always checked; the rest depends
    * on the write.
    *
    * @param keys       a join or composite table keyed on the column
    * @param aggregates a downstream aggregate or group-by on the column
    * @param ownState   the table's own conditions and sort
    * @param ownDerived the table's own expression and range columns, which the write keeps
    */
   record Scope(boolean keys, boolean aggregates, boolean ownState, boolean ownDerived) {
      /** replace-import: every dependent, including the derived columns the replace keeps. */
      static final Scope IMPORT = new Scope(true, true, true, true);

      /**
       * edit_sql_query / edit_table: downstream references and the table's own expression and
       * range columns, which both edits keep (Bug #78681). Join keys and key types are
       * refused by the caller's own checks; an aggregate stays accept-and-warn (WSQ-009).
       */
      static final Scope QUERY_EDIT = new Scope(false, false, false, true);

      /**
       * edit_unpivot shrinking the header columns: the melted column's name leaves the table, so
       * downstream references and aggregates, the table's own conditions and sort, and its own
       * expression and range columns all break. Join keys are refused first by the caller, never
       * confirmable.
       */
      static final Scope UNPIVOT_EDIT = new Scope(false, true, true, true);

      /**
       * update_mirror re-syncing an outer mirror with its source (Bug #78814): the columns the
       * source no longer has leave the mirror, so downstream references and aggregates and the
       * mirror's own conditions, sort, expression and range columns break. Join keys are refused
       * first by the caller, never confirmable.
       */
      static final Scope RESYNC = new Scope(false, true, true, true);
   }

   /**
    * Refuses a replace-import that would drop a column something else still uses: a join keyed
    * on it, a downstream condition, ranking, expression, range, or aggregate/group-by, or the
    * table's own condition, sort, expression or range column. Everything is listed in one
    * message. Nothing has been mutated when this runs.
    *
    * @param keptNames the names the table's columns will have after the import
    */
   static void requireImportKeepsReferencedColumns(
      Worksheet ws, TableAssembly assembly, String tableName,
      List<ColumnRef> oldColumns, Set<String> keptNames) throws PairingException
   {
      requireNoDependentLoss(
         ws, assembly, tableName, oldColumns, keptNames, Scope.IMPORT,
         "Replacing the data of '" + tableName + "'",
         "Keep these columns, under the same names, in the file, or remove or rewrite those " +
         "dependents first. Nothing was changed.");
   }

   /**
    * Refuses a write that would drop a column of {@code oldColumns} whose name is not in
    * {@code keptNames} while a dependent in {@code scope} still uses it. The message is
    * {@code <lead> would drop column(s) "a" that other tables still use: <dependents>.
    * <remedy>}. Nothing has been mutated when this runs.
    */
   static void requireNoDependentLoss(
      Worksheet ws, TableAssembly assembly, String tableName, List<ColumnRef> oldColumns,
      Set<String> keptNames, Scope scope, String lead, String remedy) throws PairingException
   {
      Set<String> dropped = new LinkedHashSet<>();
      List<String> parts = new ArrayList<>();

      for(ColumnRef old : oldColumns) {
         if(keptNames.contains(old.getName())) {
            continue;
         }

         List<String> keyDependents = new ArrayList<>();
         boolean keyBlocked = false;

         if(scope.keys()) {
            collectKeyDependents(ws, assembly, old, keyDependents, new LinkedHashSet<>());
            keyBlocked = !WorksheetControllerService.allowsDeletion(ws, assembly, old);
         }

         List<AggregateInputLossConflict> aggregates = scope.aggregates()
            ? WorksheetControllerService.findAggregateInputLossConflicts(ws, assembly, old)
            : List.of();
         List<ColumnReferenceLossConflict> references =
            WorksheetControllerService.findColumnReferenceLossConflicts(ws, assembly, old);
         Set<String> ownSet = new LinkedHashSet<>();

         if(scope.ownState()) {
            ownSet.addAll(WorksheetControllerService.findOwnColumnReferences(assembly, old));
         }

         if(scope.ownDerived()) {
            ownSet.addAll(WorksheetControllerService.findOwnDerivedColumnReferences(assembly, old));
         }

         List<String> own = new ArrayList<>(ownSet);

         if(!keyBlocked && aggregates.isEmpty() && references.isEmpty() &&
            own.isEmpty())
         {
            continue;
         }

         dropped.add("\"" + old.getName() + "\"");

         if(keyBlocked && keyDependents.isEmpty()) {
            parts.add("a join or composite table keyed on \"" + old.getName() + "\"");
         }

         for(String dependent : keyDependents) {
            parts.add("'" + dependent + "' (join key on \"" + old.getName() + "\")");
         }

         String downstream = dependentsClause(aggregates, references);

         if(!downstream.isEmpty()) {
            parts.add(downstream);
         }

         if(!own.isEmpty()) {
            parts.add("table '" + tableName + "' itself (" + String.join(", ", own) + ")");
         }
      }

      if(dropped.isEmpty()) {
         return;
      }

      throw new PairingException(
         lead + " would drop column(s) " + String.join(", ", dropped) +
         " that other tables still use: " + String.join("; ", parts) + ". " + remedy);
   }

   /**
    * Refuses a write that renames {@code ref} while a join or composite table is keyed on it
    * (Bug #78527), naming those tables: {@code <lead>: it is a join key of 'J' (join key on
    * "a"). <remedy>}. Nothing has been mutated when this runs.
    */
   static void requireNotJoinKey(Worksheet ws, TableAssembly table, ColumnRef ref, String lead,
                                 String remedy) throws PairingException
   {
      if(WorksheetControllerService.allowsDeletion(ws, table, ref)) {
         return;
      }

      List<String> keyDependents = new ArrayList<>();
      collectKeyDependents(ws, table, ref, keyDependents, new LinkedHashSet<>());
      String named = keyDependents.isEmpty()
         ? "a join or composite table"
         : String.join(", ", keyDependents.stream().map(d -> "'" + d + "'").toList());

      throw new PairingException(
         lead + ": it is a join key of " + named + " (join key on \"" + ref.getName() + "\"). " +
         remedy);
   }

   /**
    * Names the composite tables (joins, unions, ...) keyed on {@code ref}, following the same
    * dependency walk as {@link WorksheetControllerService#allowsDeletion}, which only answers
    * yes or no.
    */
   private static void collectKeyDependents(Worksheet ws, TableAssembly table, ColumnRef ref,
                                            List<String> out, Set<String> visited)
   {
      if(!visited.add(table.getName())) {
         return;
      }

      for(AssemblyRef assemblyRef : ws.getDependings(table.getAssemblyEntry())) {
         String name = assemblyRef.getEntry().getName();
         Assembly dependent = ws.getAssembly(name);

         if(dependent instanceof CompositeTableAssembly composite &&
            composite.isColumnUsed(table, ref) && !out.contains(name))
         {
            out.add(name);
         }

         if(dependent instanceof TableAssembly dtable) {
            DataRef outer = AssetUtil.getOuterAttribute(name, ref);
            ColumnRef mapped =
               AssetUtil.getColumnRefFromAttribute(dtable.getColumnSelection(), outer);

            if(mapped != null) {
               collectKeyDependents(ws, dtable, mapped, out, visited);
            }
         }
      }
   }
}
