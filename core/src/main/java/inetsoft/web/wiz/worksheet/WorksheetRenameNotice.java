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

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Bug #80656: what an in-place worksheet save renamed, independent of whether any STORED asset
 * depends on it. The rename cascade only rewrites stored dependents, so a connected viewsheet
 * that was never saved (or has unsaved edits) keeps binding the old name; this is the signal
 * that lets the save say so.
 */
final class WorksheetRenameNotice {
   private WorksheetRenameNotice() {
   }

   /**
    * The table and column renames pending in the worksheet since it was opened or last saved,
    * as "old -> new" descriptions. Uses the same logic as the rename cascade so a column rename
    * counts as well as a table rename; a rename that was undone by renaming back is not listed.
    * Must run before the worksheet's old-name markers are reset.
    */
   static List<String> pendingRenames(Worksheet ws, String id) {
      List<RenameInfo> infos = new ArrayList<>();
      RenameDependencyInfo scratch = new RenameDependencyInfo();

      for(Assembly assembly : ws.getAssemblies()) {
         DependencyTransformer.addAssemblyRenameInfo(assembly, infos, id, scratch);
      }

      List<String> renames = new ArrayList<>();

      for(RenameInfo info : infos) {
         String name = info.isColumn() && info.getTable() != null ?
            info.getTable() + "." + info.getOldName() : info.getOldName();
         String text = (info.isColumn() ? "column " : "table ") + name + " -> " +
            info.getNewName();

         if(!renames.contains(text)) {
            renames.add(text);
         }
      }

      return renames;
   }

   /**
    * Whether a connected runtime is a design session that must be kept and reported instead of
    * just reset: a design session after a stored dependent was rewritten ({@code reload}), or
    * after a rename that no stored dependent received. Viewer, preview and binding-editor
    * runtimes, and any save that renamed nothing, keep the silent reset.
    */
   static boolean keepsSessionAndReports(boolean designSession, boolean reload,
                                         List<String> renames)
   {
      return designSession && (reload || !renames.isEmpty());
   }

   /**
    * The warning for open viewsheet session(s) that were not reloaded.
    *
    * @param ids     runtime ids of the sessions
    * @param renames pending renames from {@link #pendingRenames}; empty when the save renamed
    *                nothing
    */
   static String staleWarning(List<String> ids, List<String> renames) {
      String renamed = renames.isEmpty() ? "" :
         " This save renamed " + renames + ", and the open viewsheet(s) are not rebound " +
         "automatically (if a rename was undone, nothing needs to change).";

      return "Open viewsheet session(s) " + ids + " still hold the previous in-memory sheet " +
         "and were not reloaded, so their bindings may still use the old table/column names." +
         renamed + " Calling save_viewsheet on a saved one applies the pending rename and " +
         "reloads it. An unsaved one has nothing in storage to reload and no pending rename: " +
         "rebind it (set_table_source / set_chart_source / set_selection_source) to the new " +
         "name before saving.";
   }
}
