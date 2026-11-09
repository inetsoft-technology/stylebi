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
    */
   public static void protectChangedFormTables(ViewsheetSandbox box, Viewsheet vs)
      throws Exception
   {
      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof TableVSAssembly table) ||
            !(table.getInfo() instanceof TableVSAssemblyInfo info) || !info.isForm())
         {
            continue;
         }

         FormTableLens lens = box.getFormTableLens(table.getAbsoluteName());

         if(lens != null && lens.isChanged()) {
            box.addScriptChangedForm(table.getAbsoluteName());
         }
      }
   }
}
