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
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.delete.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.List;

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
 */
final class RemoveColumnDependentsGuard {
   private RemoveColumnDependentsGuard() {
   }

   static void requireNoSavedDependents(RuntimeWorksheet rws, String table, String column)
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

      if(!(rws.getWorksheet().getAssembly(table) instanceof TableAssembly t)) {
         return;
      }

      DataRef ref = WorksheetMutationSupport.resolveFieldOrNull(t, column, false);

      // Editor raises its own "Column not found" / composed-table refusal for these, and a
      // dependency error must not be reported for a removal that is refused regardless
      if(!(ref instanceof ColumnRef cr) ||
         t instanceof ComposedTableAssembly && !cr.isExpression())
      {
         return;
      }

      String name = cr.getAlias() != null ? cr.getAlias() : cr.getAttribute();
      DeleteInfo info = new DeleteInfo(name, RenameInfo.ASSET | RenameInfo.COLUMN,
                                       entry.toIdentifier(), table);
      DeleteDependencyInfo dinfo =
         DeleteDependencyHandler.createWsDependencyInfo(List.of(info), rws);
      DependencyException ex = DeleteDependencyHandler.checkDependency(dinfo, true);

      if(ex == null || ex.isEmpty()) {
         return;
      }

      throw new PairingException(
         "Cannot remove column '" + name + "' from table '" + table + "' without confirmation: " +
         ex.getMessage(false) + " The saved asset(s) above use this column (binding, " +
         "condition, calc field, ...), so they would lose it or stop filtering on it. " +
         "Nothing was changed. Confirm with the user before retrying with confirmed:true.");
   }
}
