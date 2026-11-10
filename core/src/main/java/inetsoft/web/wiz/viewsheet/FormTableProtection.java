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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a Form table's pending edits alive across a refresh (Bugs #80034, #80482, #80876), and
 * refuses a write that would reset the whole sheet while edits are pending (Bug #80876).
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
    *
    * @return the absolute names of the Form tables that were protected, in sheet order.
    */
   public static List<String> protectBuiltChangedFormTables(ViewsheetSandbox box, Viewsheet vs)
      throws Exception
   {
      return protect(box, vs, true);
   }

   /**
    * Bug #80876: names every Form table of the sheet whose already-built lens holds pending (not
    * yet applied) edits. Unlike {@code FormUtil.pendingDependentForms} this is not scoped to the
    * dependents of one assembly, because a bookmark can change any assembly. It never builds a
    * lens, so it runs no query.
    */
   public static List<String> pendingBuiltForms(RuntimeViewsheet rvs) {
      List<String> pending = new ArrayList<>();
      ViewsheetSandbox box = rvs == null ? null : rvs.getViewsheetSandbox().orElse(null);
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(box == null || vs == null) {
         return pending;
      }

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(assembly instanceof TableVSAssembly table &&
            table.getInfo() instanceof TableVSAssemblyInfo info && info.isForm())
         {
            FormTableLens lens = box.getCachedFormTableLens(table.getAbsoluteName());

            if(lens != null && lens.isChanged()) {
               pending.add(table.getAbsoluteName());
            }
         }
      }

      return pending;
   }

   /**
    * Bug #80876: refuses, before anything is written, an action that resets the whole sheet while
    * a Form table holds pending edits, unless the caller passed discardPending. The Viewer asks
    * first; this is the agent equivalent (see Bug #80482 for the selection writes).
    *
    * @param action what is about to happen, for the message.
    */
   public static void refuseUnlessDiscardable(RuntimeViewsheet rvs, Boolean discardPending,
                                              String action)
   {
      if(Boolean.TRUE.equals(discardPending)) {
         return;
      }

      List<String> pending = pendingBuiltForms(rvs);

      if(!pending.isEmpty()) {
         throw new IllegalArgumentException(
            "Applying " + action + " would discard the pending (not yet applied) row edits of " +
            "Form table " + String.join(", ", pending) + ". It resets the whole sheet, and what " +
            "is dropped cannot be recovered. Nothing was changed. Commit them first with " +
            "form_table_apply, or pass discardPending: true to throw them away.");
      }
   }

   private static List<String> protect(ViewsheetSandbox box, Viewsheet vs, boolean builtOnly)
      throws Exception
   {
      List<String> protectedNames = new ArrayList<>();
      Assembly[] assemblies = vs == null ? null : vs.getAssemblies(true);

      if(assemblies == null) {
         return protectedNames;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof TableVSAssembly table) ||
            !(table.getInfo() instanceof TableVSAssemblyInfo info) || !info.isForm())
         {
            continue;
         }

         FormTableLens lens = builtOnly ? box.getCachedFormTableLens(table.getAbsoluteName())
            : box.getFormTableLens(table.getAbsoluteName());

         if(lens != null && lens.isChanged()) {
            box.addScriptChangedForm(table.getAbsoluteName());
            protectedNames.add(table.getAbsoluteName());
         }
      }

      return protectedNames;
   }
}
