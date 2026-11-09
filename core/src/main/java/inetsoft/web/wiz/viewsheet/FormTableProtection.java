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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;

/**
 * Keeps a Form table's pending edits alive across a whole-sheet refresh (Bugs #80034, #80482).
 */
public final class FormTableProtection {
   private FormTableProtection() {
   }

   /**
    * Marks every Form table that already has an uncommitted edit (appended/changed/deleted row)
    * as script-changed, so the refresh's own {@code ViewsheetSandbox.syncFormData} call does not
    * drop its cached FormTableLens and lose that edit. The caller must call
    * {@link ViewsheetSandbox#clearScriptChangedFormSet()} in a finally block once the refresh is
    * done, so the protection does not leak into a later refresh.
    *
    * <p>The protected lens keeps its old base data, so a refresh that would have pulled fresh
    * worksheet rows leaves that Form table showing the rows it had.
    *
    * <p>This variant builds a table's lens (runs its query) when none is cached yet, which is the
    * Bug #80034 script-run behaviour. A whole-sheet refresh must use
    * {@link #protectBuiltChangedFormTables} instead.
    */
   public static void protectChangedFormTables(ViewsheetSandbox box, Viewsheet vs)
      throws Exception
   {
      protect(box, vs, false);
   }

   /**
    * Same as {@link #protectChangedFormTables}, but only looks at lenses that are already built.
    * A Form table whose lens was never built holds no pending edits, so nothing is lost by
    * skipping it, and a refresh neither runs an extra query nor fails because a Form table's
    * source query currently fails.
    */
   public static void protectBuiltChangedFormTables(ViewsheetSandbox box, Viewsheet vs)
      throws Exception
   {
      protect(box, vs, true);
   }

   private static void protect(ViewsheetSandbox box, Viewsheet vs, boolean builtOnly)
      throws Exception
   {
      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof TableVSAssembly table) ||
            !(table.getInfo() instanceof TableVSAssemblyInfo info) || !info.isForm())
         {
            continue;
         }

         FormTableLens lens = builtOnly ? box.getCachedFormTableLens(table.getAbsoluteName())
            : box.getFormTableLens(table.getAbsoluteName());

         if(lens != null && lens.isChanged()) {
            box.addScriptChangedForm(table.getAbsoluteName());
         }
      }
   }
}
