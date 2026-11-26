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

import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.dialog.ReorderSubtableService;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.List;

/**
 * Bug #82402: reorders the tables of a join, as Composer's "Reorder Tables" button does for a
 * join (ReorderSubtableService.reorderSubtables). The join's columns follow its table order, so
 * the order decides which of two same-named columns keeps the plain name and which gets the
 * {@code _1} alias.
 */
final class JoinReorderSupport {
   private JoinReorderSupport() {
   }

   /**
    * Validates {@code names} as a permutation of the join's tables, rewrites the operators for
    * the new order and reorders the tables. Nothing is changed when it throws. The caller
    * refreshes the join's column selection afterwards.
    */
   static void reorder(Worksheet ws, AbstractJoinTableAssembly join, List<String> names)
      throws PairingException
   {
      if(join instanceof MergeJoinTableAssembly) {
         throw new PairingException(
            "Tables of a merge join cannot be reordered: " + join.getName() +
            ". Nothing was changed.");
      }

      TableAssembly[] reordered = ConcatReorderGuard.resolvePermutation(ws, join, names);
      ReorderSubtableService.updateOperators(join, names.toArray(new String[0]));

      if(!join.reorderTableAssemblies(reordered)) {
         throw new PairingException(
            "'" + join.getName() + "' did not accept the new table order.");
      }
   }
}
