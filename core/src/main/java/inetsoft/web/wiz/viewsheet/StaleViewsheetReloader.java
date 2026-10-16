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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;

import java.security.Principal;
import java.util.Optional;

/**
 * Re-reads a saved viewsheet from storage into a live agent runtime (Bug #78662).
 *
 * <p>A cascade that rewrites a stored viewsheet (a table or column rename, a Save-As overwrite)
 * never touches the open {@link RuntimeViewsheet}: {@code resetRuntime()} re-executes the
 * runtime's in-memory sheet and never re-reads storage, so the runtime keeps the old binding.
 * This is the same-runtime-id equivalent of the Composer's ReopenSheetCommand.
 */
public final class StaleViewsheetReloader {
   private StaleViewsheetReloader() {
   }

   /**
    * Whether this is an agent design session (not a portal viewer, a Composer preview or a
    * binding-editor clone). Only design sessions may be replaced from storage: the others carry
    * runtime state (selections, bookmarks) or an open editor over their sheet that a reload
    * would drop.
    */
   public static boolean isDesignSession(RuntimeViewsheet rvs) {
      return !rvs.isRuntime() && !rvs.isPreview() && !rvs.isViewer() && !rvs.isBinding();
   }

   /**
    * Whether the runtime may be overwritten from storage without losing work: it is a design
    * session, a saved asset (an unsaved sheet has nothing in storage), its undo history sits at
    * the save point, and it has no layout edits (those checkpoint on a separate layout stack, so
    * the main save point cannot see them; any layout checkpoint counts as unsaved work).
    */
   public static boolean isReloadable(RuntimeViewsheet rvs) {
      AssetEntry entry = rvs.getEntry();

      return isDesignSession(rvs) && entry != null &&
         entry.getScope() != AssetRepository.TEMPORARY_SCOPE &&
         rvs.getSavePoint() == rvs.getCurrent() && rvs.getLayoutPointsSize() == 0;
   }

   /**
    * Load the stored sheet into the same runtime, reset its sandbox and restart its undo
    * history from the loaded sheet so undo/redo cannot bring the stale sheet back.
    */
   public static void reload(RuntimeViewsheet rvs, AssetRepository repository) throws Exception {
      AssetEntry entry = rvs.getEntry();
      Principal user = rvs.getUser();
      AbstractSheet stored = repository.getSheet(entry, user, false, AssetContent.ALL);

      if(!(stored instanceof Viewsheet)) {
         throw new IllegalStateException("Stored viewsheet not found: " + entry.toView());
      }

      Viewsheet vs = (Viewsheet) stored.clone();
      VSEventUtil.syncEmbeddedTableVSAssembly(vs);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      // Monitor first, then the box lock: undo/redo are synchronized on the runtime and take the
      // box write lock inside, so the opposite order could deadlock against a concurrent undo.
      synchronized(rvs) {
         box.ifPresent(ViewsheetSandbox::lockWrite);

         try {
            rvs.setViewsheet(vs);
            rvs.resetRuntime();
            rvs.resetCheckpoints(vs.prepareCheckpoint());
            rvs.resetLayoutUndoRedo();
            // a live write: a Composer dialog / binding editor opened before the reload must be
            // refused on commit instead of restoring the pre-reload sheet
            rvs.bumpWriteRevision();
         }
         finally {
            box.ifPresent(ViewsheetSandbox::unlockWrite);
         }
      }
   }
}
