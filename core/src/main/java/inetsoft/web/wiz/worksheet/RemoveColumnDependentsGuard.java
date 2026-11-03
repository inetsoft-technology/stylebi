/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.delete.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bug #79526: removing a worksheet column that a SAVED viewsheet or worksheet still uses (a
 * binding, a condition, a calc field, ...) was silent, and the dependent then rendered unfiltered
 * or lost the column. The Composer asks the user to confirm in that case; this is the same check
 * ({@link DeleteDependencyHandler}, as {@code DeleteColumnsService.hasDependency} builds it), run
 * before {@code remove_column} mutates anything, and satisfied by {@code confirmed:true}.
 *
 * <p>Best effort, exactly as in the Composer: a dependent that cannot be loaded, a check that
 * times out, and assembly types the checker does not match are treated as "no dependency", and
 * only SAVED dependents recorded in the dependency index are seen (a table renamed in memory but
 * not yet saved can be missed). It cannot live in {@code Editor}, which has no worksheet entry or
 * repository.</p>
 *
 * <p>Bug #80035 widened this from {@code remove_column} alone to every other op that can produce
 * the same silent data exposure: {@code delete_table} (every column the table exposes),
 * {@code set_column_visibility} (hiding a column stops it filtering, same as removing it), and
 * {@code edit_sql_query} (see {@link #requireNoSavedDependentsForSqlEdit}). It also closed the
 * remaining parity gap noted since #79526: a dependent bound to a join, mirror or concatenation
 * built on {@code table}, rather than {@code table} itself, is now found too -- every edit below
 * walks downstream through {@link WorksheetControllerService#mapToDependentColumn}, the same
 * mirror/join/concat column-identity mapping the in-worksheet loss-conflict walkers
 * ({@code WorksheetControllerService#findColumnReferenceLossConflicts}) already use, so one
 * refusal names every affected saved asset on every reachable table, not just the edited one.</p>
 */
final class RemoveColumnDependentsGuard {
   private RemoveColumnDependentsGuard() {
   }

   static void requireNoSavedDependents(RuntimeWorksheet rws, String table, String column)
      throws PairingException
   {
      requireNoSavedDependents(rws, table, column,
         name -> "remove column '" + name + "' from table '" + table + "'", "changed");
   }

   /**
    * Same check as {@link #requireNoSavedDependents(RuntimeWorksheet, String, String)}, worded
    * for {@code set_column_visibility}'s hide rather than {@code remove_column}'s removal --
    * hiding a column a saved dependent filters or binds on has the same silent effect.
    */
   static void requireNoSavedDependentsForHide(RuntimeWorksheet rws, String table, String column)
      throws PairingException
   {
      requireNoSavedDependents(rws, table, column,
         name -> "hide column '" + name + "' on table '" + table + "'", "hidden");
   }

   /**
    * {@code delete_table}'s shape: every column the table currently exposes is checked, since
    * deleting the table breaks a saved dependent on any one of them. Refuses on the first column
    * with a saved dependent rather than collecting every one, matching how the single-column
    * checks above refuse on the first dependency error {@code DeleteDependencyHandler} reports.
    */
   static void requireNoSavedDependentsForTable(RuntimeWorksheet rws, String table)
      throws PairingException
   {
      if(rws == null || rws.getWorksheet() == null || table == null) {
         return;
      }

      if(!(rws.getWorksheet().getAssembly(table) instanceof TableAssembly t)) {
         return;
      }

      List<String> columns = new ArrayList<>();
      Enumeration<DataRef> refs = t.getColumnSelection().getAttributes();

      while(refs.hasMoreElements()) {
         DataRef ref = refs.nextElement();
         columns.add(ref.getAttribute());
      }

      for(String column : columns) {
         requireNoSavedDependents(rws, table, column,
            name -> "delete table '" + table + "', which still carries column '" + name + "'",
            "deleted");
      }
   }

   /**
    * {@code edit_sql_query}'s shape: every column present in {@code oldColumns} but absent from
    * {@code newColumns} by output name is checked -- the same "dropped" definition
    * {@code WorksheetAgentController#assertSqlEditAllowsColumnRemoval} already uses -- since the
    * rewrite drops it from the table the same way {@code remove_column} would. Called before the
    * rewrite is committed, so {@code table}'s live column selection is still {@code oldColumns}
    * and the single-column check below can resolve each dropped column by its (still current)
    * attribute name. Refuses on the first dropped column with a saved dependent, matching
    * {@link #requireNoSavedDependentsForTable}.
    */
   static void requireNoSavedDependentsForSqlEdit(RuntimeWorksheet rws, String table,
                                                  ColumnSelection oldColumns,
                                                  ColumnSelection newColumns)
      throws PairingException
   {
      if(rws == null || rws.getWorksheet() == null || table == null || oldColumns == null) {
         return;
      }

      Set<String> keptNames = new HashSet<>();

      if(newColumns != null) {
         for(int i = 0; i < newColumns.getAttributeCount(); i++) {
            keptNames.add(newColumns.getAttribute(i).getName());
         }
      }

      for(int i = 0; i < oldColumns.getAttributeCount(); i++) {
         DataRef old = oldColumns.getAttribute(i);

         if(keptNames.contains(old.getName())) {
            continue;
         }

         requireNoSavedDependents(rws, table, old.getAttribute(),
            name -> "apply this SQL edit, which drops column '" + name + "' from table '" +
                    table + "'",
            "changed");
      }
   }

   private static void requireNoSavedDependents(RuntimeWorksheet rws, String table, String column,
                                                java.util.function.Function<String, String> action,
                                                String pastTense)
      throws PairingException
   {
      if(rws == null || rws.getWorksheet() == null || table == null || column == null) {
         return;
      }

      AssetEntry entry = rws.getEntry();

      // an unsaved worksheet has nothing pointing at it
      if(entry == null || entry.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         return;
      }

      Worksheet ws = rws.getWorksheet();

      if(!(ws.getAssembly(table) instanceof TableAssembly t)) {
         return;
      }

      DataRef ref = WorksheetMutationSupport.resolveFieldOrNull(t, column, false);

      // Editor raises its own "Column not found" / composed-table refusal for these, and a
      // dependency error must not be reported for an edit that is refused regardless
      if(!(ref instanceof ColumnRef cr) ||
         t instanceof ComposedTableAssembly && !cr.isExpression())
      {
         return;
      }

      String name = cr.getAlias() != null ? cr.getAlias() : cr.getAttribute();
      List<DeleteInfo> infos = new ArrayList<>();
      infos.add(new DeleteInfo(name, RenameInfo.ASSET | RenameInfo.COLUMN,
                               entry.toIdentifier(), table));

      // Bug #80035: a dependent bound to a join, mirror or concatenation built on `table`, which
      // exposes the column from it under its own identity, is just as exposed as a dependent
      // bound to `table` directly -- walk every such table reachable downstream and add one more
      // DeleteInfo per hop, so the single DeleteDependencyHandler call below reports every
      // affected saved asset in one refusal.
      collectDownstreamDeleteInfos(ws, t, cr, new HashSet<>(), entry, infos);

      DeleteDependencyInfo dinfo =
         DeleteDependencyHandler.createWsDependencyInfo(infos, rws);
      DependencyException ex = DeleteDependencyHandler.checkDependency(dinfo, true);

      if(ex == null || ex.isEmpty()) {
         return;
      }

      throw new PairingException(
         "Cannot " + action.apply(name) + " without confirmation: " + ex.getMessage(false) +
         " The saved asset(s) above use this column (binding, condition, calc field, ...), so " +
         "they would lose it or stop filtering on it. Nothing was " + pastTense + ". Confirm " +
         "with the user before retrying with confirmed:true.");
   }

   /**
    * Recursively walks {@code ws.getDependings} from {@code assembly}, following only
    * {@code TableAssembly} dependents, the same shape as
    * {@code WorksheetControllerService}'s private in-worksheet loss-conflict walker. At each hop,
    * {@link WorksheetControllerService#mapToDependentColumn} resolves {@code ref}'s identity on
    * the dependent (correct for a mirror, a join -- disambiguating same-named columns from
    * different sources by entity -- and a concatenation, whose union-compatible columns share a
    * bare name across every source so the fallback bare-name mapping already finds them); a
    * {@code null} mapping means the dependent does not carry the column at all, and that branch
    * stops. {@code visited} is removed from in a {@code finally} so a diamond-shaped dependency
    * (two branches converging on the same downstream table) is still checked from each branch --
    * only a true cycle within the current path is guarded against.
    */
   private static void collectDownstreamDeleteInfos(
      Worksheet ws, TableAssembly assembly, ColumnRef ref, Set<String> visited,
      AssetEntry entry, List<DeleteInfo> infos)
   {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef assemblyRef : arr) {
         String assemblyName = assemblyRef.getEntry().getName();

         if(!visited.add(assemblyName)) {
            continue;
         }

         try {
            if(!(ws.getAssembly(assemblyName) instanceof TableAssembly dependent)) {
               continue;
            }

            ColumnRef mappedRef =
               WorksheetControllerService.mapToDependentColumn(assembly, dependent, ref);

            if(mappedRef == null) {
               continue;
            }

            String mappedName =
               mappedRef.getAlias() != null ? mappedRef.getAlias() : mappedRef.getAttribute();
            infos.add(new DeleteInfo(mappedName, RenameInfo.ASSET | RenameInfo.COLUMN,
                                     entry.toIdentifier(), dependent.getName()));
            collectDownstreamDeleteInfos(ws, dependent, mappedRef, visited, entry, infos);
         }
         finally {
            visited.remove(assemblyName);
         }
      }
   }
}
