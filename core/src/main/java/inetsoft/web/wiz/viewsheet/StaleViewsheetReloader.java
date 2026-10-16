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

import java.io.PrintWriter;
import java.io.StringWriter;
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
    * session, a saved asset (an unsaved sheet has nothing in storage) and its undo history sits
    * at the save point. Layout edits are not visible here (they checkpoint a separate layout
    * stack that is cleared on a layout switch), so {@link #reload} additionally compares the
    * layout definitions with the stored copy.
    */
   public static boolean isReloadable(RuntimeViewsheet rvs) {
      AssetEntry entry = rvs.getEntry();

      return isDesignSession(rvs) && entry != null &&
         entry.getScope() != AssetRepository.TEMPORARY_SCOPE &&
         rvs.getSavePoint() == rvs.getCurrent();
   }

   /**
    * Load the stored sheet into the same runtime, reset its sandbox and restart its undo
    * history from the loaded sheet so undo/redo cannot bring the stale sheet back.
    *
    * @return {@code false}, with the runtime untouched, when the runtime's layouts differ from
    *         the stored copy (unsaved layout edits that a reload would discard); {@code true}
    *         once reloaded.
    */
   public static boolean reload(RuntimeViewsheet rvs, AssetRepository repository) throws Exception {
      return reload(rvs, repository, true);
   }

   /**
    * @param checkLayouts {@code false} skips the layout comparison, for a runtime that was just
    *                     saved (its storage is by definition its own content)
    */
   public static boolean reload(RuntimeViewsheet rvs, AssetRepository repository,
                                boolean checkLayouts) throws Exception
   {
      AssetEntry entry = rvs.getEntry();
      Principal user = rvs.getUser();
      AbstractSheet stored = repository.getSheet(entry, user, false, AssetContent.ALL);

      if(!(stored instanceof Viewsheet)) {
         throw new IllegalStateException("Stored viewsheet not found: " + entry.toView());
      }

      Viewsheet vs = (Viewsheet) stored.clone();

      if(checkLayouts && !layoutXml(rvs.getViewsheet()).equals(layoutXml(vs))) {
         return false;
      }

      VSEventUtil.syncEmbeddedTableVSAssembly(vs);
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      // resetCheckpoints takes the runtime monitor (synchronized) and must not nest with the box
      // write lock: undo/redo hold the monitor then take the box lock, while CoreLifecycleService
      // and the wizard services hold the box lock then take the monitor, so either nesting can
      // deadlock. Take them one at a time. A concurrent undo between the two steps finds a
      // single-checkpoint history and does nothing.
      rvs.resetCheckpoints(vs.prepareCheckpoint());
      box.ifPresent(ViewsheetSandbox::lockWrite);

      try {
         rvs.setViewsheet(vs);
         rvs.resetRuntime();
         rvs.resetLayoutUndoRedo();
         // a live write: a Composer dialog / binding editor opened before the reload must be
         // refused on commit instead of restoring the pre-reload sheet
         rvs.bumpWriteRevision();
      }
      finally {
         box.ifPresent(ViewsheetSandbox::unlockWrite);
      }

      // resetRuntime can add a checkpoint of its own while the sandbox is re-initialized; restart
      // the history once more (monitor only, not nested) so only the loaded sheet remains
      rvs.resetCheckpoints(vs.prepareCheckpoint());
      return true;
   }

   private static String layoutXml(Viewsheet vs) {
      if(vs == null || vs.getLayoutInfo() == null) {
         return "";
      }

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      vs.getLayoutInfo().writeXML(writer);
      writer.flush();
      return out.toString();
   }
}
