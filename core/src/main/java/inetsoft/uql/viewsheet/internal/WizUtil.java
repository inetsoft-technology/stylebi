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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility methods for managing wiz (visualization wizard) viewsheet and worksheet
 * lifecycle: creating temp copies, saving, and cleaning up on close.
 */
public final class WizUtil {

   private WizUtil() {
   }

   // ---------------------------------------------------------------------------
   // Wiz-copy viewsheet lifecycle
   // ---------------------------------------------------------------------------

   /**
    * Copies the viewsheet identified by {@code originalEntry} and saves the copy under a
    * unique name with the {@link #WIZ_COPY_PREFIX} in the same folder.
    *
    * @param originalEntry    the entry of the viewsheet to copy.
    * @param newVisualization {@code true} if this is a brand-new visualization.
    * @param principal        the current user.
    *
    * @return the {@link AssetEntry} of the saved copy, or {@code null} if the original
    * viewsheet could not be loaded.
    */
   public static AssetEntry copyViewsheetForWiz(AssetEntry originalEntry,
                                                boolean newVisualization,
                                                Principal principal)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      Viewsheet vs = (Viewsheet) repository.getSheet(
         originalEntry, principal, false, AssetContent.ALL);

      if(vs == null) {
         return null;
      }

      Viewsheet copy = vs.clone();
      AssetEntry newEntry = createCopyEntryForWiz(originalEntry, newVisualization);
      repository.setSheet(newEntry, copy, principal, true);

      return newEntry;
   }

   /**
    * Builds an {@link AssetEntry} for a wiz copy of {@code originalEntry} without
    * touching the repository.
    *
    * @param originalEntry    the entry of the viewsheet to copy.
    * @param newVisualization {@code true} if this is a brand-new visualization.
    *
    * @return the new {@link AssetEntry}.
    */
   public static AssetEntry createCopyEntryForWiz(AssetEntry originalEntry,
                                                  boolean newVisualization)
   {
      String vizFlag = newVisualization ? "new" : "edit";
      String copyName = WIZ_COPY_PREFIX + vizFlag + "_" +
         UUID.randomUUID().toString().replace("-", "") + "_" + originalEntry.getName();
      AssetEntry newEntry = renameAssetEntry(originalEntry, copyName);
      newEntry.copyProperties(originalEntry);
      newEntry.setProperty("wizOriginalEntry", originalEntry.toIdentifier());

      return newEntry;
   }

   /**
    * Wraps a viewsheet save call with wiz-visualization bookkeeping:
    * updates {@link Viewsheet.WizInfo} visualization references to point at the
    * originals, finalizes the temporary dashboard worksheet (if any), runs the save,
    * then applies every wiz-copy viewsheet back to its original entry.
    *
    * @param rvs       the runtime viewsheet being saved.
    * @param principal the current user.
    * @param entry     the permanent asset entry the viewsheet is being saved to.
    * @param save      the actual save operation (may throw any checked exception).
    */
   public static void saveWizSheet(RuntimeViewsheet rvs, Principal principal,
                                   AssetEntry entry, ThrowingRunnable save)
      throws Exception
   {
      Viewsheet.WizInfo wizInfo = updateWizVisualization(rvs);
      finalizeWizDashWorksheet(rvs, entry, principal);
      save.run();

      if(wizInfo != null && wizInfo.getVisualizations() != null) {
         for(String visualization : wizInfo.getVisualizations()) {
            try {
               applyWizCopyToOriginal(AssetEntry.createAssetEntry(visualization), entry, principal);
            }
            catch(Exception ex) {
               LOG.warn("Failed to apply wiz copy to " + visualization, ex);
            }
         }
      }
   }

   /**
    * Functional interface for save operations that may throw checked exceptions.
    */
   @FunctionalInterface
   public interface ThrowingRunnable {
      void run() throws Exception;
   }

   /**
    * Replaces the original viewsheet with the wiz copy: saves the copy's content over
    * the original entry (derived from the copy name) and then removes the copy.
    *
    * @param wizCopyEntry the entry of the wiz copy that should replace the original.
    * @param entry        the wiz sheet entry.
    * @param principal    the current user.
    *
    * @return the original entry, or {@code null} if the copy could not be applied.
    */
   public static AssetEntry applyWizCopyToOriginal(AssetEntry wizCopyEntry,
                                                   AssetEntry entry,
                                                   Principal principal)
      throws Exception
   {
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      wizCopyEntry = engine.getAssetEntry(wizCopyEntry);

      if(wizCopyEntry == null || !wizCopyEntry.getName().startsWith(WIZ_COPY_PREFIX)) {
         return null;
      }

      AssetEntry originalEntry = createWizOriginalVisualization(wizCopyEntry);
      Viewsheet copy = (Viewsheet) engine.getSheet(wizCopyEntry, principal, false, AssetContent.ALL);

      if(copy == null) {
         return null;
      }

      originalEntry.setProperty("visualizationSheet", entry.toIdentifier());
      engine.setSheet(originalEntry, copy, principal, true);

      try {
         engine.removeSheet(wizCopyEntry, principal, true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to remove wiz copy entry after applying: " + wizCopyEntry.toIdentifier(), ex);
      }

      return originalEntry;
   }

   /**
    * Returns {@code true} if the wiz-copy entry was created as a new visualization
    * (i.e. {@code newVisualization=true} was passed to {@link #copyViewsheetForWiz}).
    *
    * @param wizCopyEntry the wiz-copy entry to test.
    */
   public static boolean isNewVisualization(AssetEntry wizCopyEntry) {
      return wizCopyEntry.getName().startsWith(WIZ_COPY_PREFIX + "new_");
   }

   /**
    * Deletes the wiz-copy viewsheet from the repository.
    *
    * @param wizCopyEntry the entry of the wiz copy to delete.
    * @param principal    the current user.
    */
   public static void deleteWizCopyViewsheet(AssetEntry wizCopyEntry, Principal principal)
      throws Exception
   {
      if(wizCopyEntry == null || !wizCopyEntry.getName().startsWith(WIZ_COPY_PREFIX)) {
         return;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      wizCopyEntry = engine.getAssetEntry(wizCopyEntry);

      if(wizCopyEntry == null) {
         return;
      }

      engine.removeSheet(wizCopyEntry, principal, true);
   }

   /**
    * Derives the original viewsheet entry from a wiz-copy entry.
    * Prefers the stored {@code "wizOriginalEntry"} property; falls back to name parsing
    * for older entries that pre-date the property.
    *
    * @param wizCopyEntry the wiz-copy entry.
    *
    * @return the corresponding original entry.
    */
   public static AssetEntry createWizOriginalVisualization(AssetEntry wizCopyEntry) {
      String originalId = wizCopyEntry.getProperty("wizOriginalEntry");

      if(originalId != null) {
         AssetEntry originalEntry = AssetEntry.createAssetEntry(originalId);

         if(originalEntry != null) {
            originalEntry.copyProperties(wizCopyEntry);
            originalEntry.setProperty("wizOriginalEntry", null);

            return originalEntry;
         }
      }

      // Fall back to name parsing for entries created before the property was introduced.
      // copy name is: __wiz__<new|edit>_<uuid>_<originalName> — strip prefix+flag+uuid
      String copyName = wizCopyEntry.getName();
      String originalName = copyName.replaceFirst(
         "^" + Pattern.quote(WIZ_COPY_PREFIX) + "(new|edit)_[0-9a-f]+_", "");
      AssetEntry originalEntry = renameAssetEntry(wizCopyEntry, originalName);
      originalEntry.copyProperties(wizCopyEntry);
      originalEntry.setProperty("wizOriginalEntry", null);

      return originalEntry;
   }

   /**
    * Returns {@code true} if the given entry is a wiz-copy viewsheet entry —
    * i.e. it was created by {@link #createCopyEntryForWiz}.
    *
    * @param entry the asset entry to test; may be {@code null}.
    */
   public static boolean isWizCopyEntry(AssetEntry entry) throws Exception {
      return isWizCopyEntry(entry, false);
   }

   /**
    * Tests whether an {@link AssetEntry} is a wiz-copy entry.
    *
    * @param entry    the asset entry to test; may be {@code null}.
    * @param resolved {@code true} if the entry has already been fetched from the
    *                 repository, skipping an extra lookup.
    */
   public static boolean isWizCopyEntry(AssetEntry entry, boolean resolved) throws Exception {
      if(entry == null) {
         return false;
      }

      if(!resolved) {
         AssetRepository engine = AssetUtil.getAssetRepository(false);
         entry = engine.getAssetEntry(entry);
      }

      if(entry == null || !entry.getName().startsWith(WIZ_COPY_PREFIX)) {
         return false;
      }

      String originalId = entry.getProperty("wizOriginalEntry");

      if(originalId != null && AssetEntry.createAssetEntry(originalId) == null) {
         LOG.warn("wiz copy entry '{}' has a malformed wizOriginalEntry property: '{}'",
                  entry.toIdentifier(), originalId);
         return false;
      }

      return true;
   }

   // ---------------------------------------------------------------------------
   // Wiz dashboard worksheet lifecycle
   // ---------------------------------------------------------------------------

   /**
    * Creates an {@link AssetEntry} for a temporary wiz dashboard worksheet.
    * The entry is placed in {@link AssetRepository#GLOBAL_SCOPE} under a name prefixed
    * with {@link #WIZ_DASH_WS_PREFIX} so it can be recognised and cleaned up.
    *
    * @param vsEntryName the name of the dashboard viewsheet entry, used as a
    *                    human-readable suffix.
    *
    * @return a new {@link AssetEntry} of type {@link AssetEntry.Type#WORKSHEET}.
    */
   public static AssetEntry createWizDashWorksheetEntry(String vsEntryName) {
      String tempName = WIZ_DASH_WS_PREFIX +
         UUID.randomUUID().toString().replace("-", "") + "_" + vsEntryName;
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, tempName, null);
   }

   /**
    * Deletes the temporary wiz dashboard worksheet from the repository.
    * Silently returns if {@code wsEntry} is {@code null} or is not a temp entry.
    *
    * @param wsEntry   the entry of the temp worksheet to delete.
    * @param principal the current user.
    */
   public static void deleteWizDashWorksheet(AssetEntry wsEntry, Principal principal)
      throws Exception
   {
      if(wsEntry == null || !wsEntry.getName().startsWith(WIZ_DASH_WS_PREFIX)) {
         return;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      wsEntry = engine.getAssetEntry(wsEntry);

      if(wsEntry != null) {
         engine.removeSheet(wsEntry, principal, true);
      }
   }

   /**
    * Finalizes the temporary wiz dashboard worksheet on save.
    * <p>Identifies the temp worksheet via the viewsheet's {@code baseEntry}: if its name
    * starts with {@link #WIZ_DASH_WS_PREFIX}, it is a temp entry that must be promoted
    * to a permanent one.
    * <ol>
    *   <li>Loads the temp worksheet from the repository.</li>
    *   <li>Saves its content to a permanent {@link AssetEntry.Type#WORKSHEET} entry
    *       whose path mirrors {@code savedVsEntry}.</li>
    *   <li>Updates the viewsheet's {@code baseEntry} to the permanent entry.</li>
    *   <li>Removes the temp worksheet from the repository.</li>
    * </ol>
    * Does nothing if the viewsheet is not a wiz sheet or its {@code baseEntry} is not a
    * temp wiz dashboard worksheet.
    *
    * @param rvs          the runtime viewsheet being saved.
    * @param savedVsEntry the permanent asset entry the viewsheet is being saved to.
    * @param principal    the current user.
    */
   public static void finalizeWizDashWorksheet(RuntimeViewsheet rvs,
                                               AssetEntry savedVsEntry,
                                               Principal principal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || vs.getWizInfo() == null || !vs.getWizInfo().isWizSheet()) {
         return;
      }

      AssetEntry tempWsEntry = vs.getBaseEntry();

      if(tempWsEntry == null || !tempWsEntry.getName().startsWith(WIZ_DASH_WS_PREFIX)) {
         return;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      tempWsEntry = engine.getAssetEntry(tempWsEntry);

      if(tempWsEntry == null) {
         LOG.warn("Temp wiz dashboard worksheet not found in repository: {}", vs.getBaseEntry());
         return;
      }

      Worksheet ws = (Worksheet) engine.getSheet(tempWsEntry, principal, false, AssetContent.ALL);

      if(ws == null) {
         LOG.warn("Could not load temp wiz dashboard worksheet: {}", tempWsEntry.toIdentifier());
         return;
      }

      // Permanent WS entry: same path and scope as the saved VS, but WORKSHEET type.
      AssetEntry permWsEntry = new AssetEntry(
         savedVsEntry.getScope(), AssetEntry.Type.WORKSHEET,
         savedVsEntry.getPath(), savedVsEntry.getUser());
      permWsEntry.copyProperties(savedVsEntry);

      engine.setSheet(permWsEntry, ws, principal, true);

      // Point the VS at the permanent WS (overrides whatever the save dialog set).
      vs.setBaseEntry(permWsEntry);

      try {
         engine.removeSheet(tempWsEntry, principal, true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to remove temp wiz dashboard worksheet: {}",
                  tempWsEntry.toIdentifier(), ex);
      }
   }

   // ---------------------------------------------------------------------------
   // Private helpers
   // ---------------------------------------------------------------------------

   /**
    * Before saving, replaces temp wiz-copy identifiers in {@link Viewsheet.WizInfo}
    * with the corresponding original entry identifiers, and returns a snapshot of the
    * original (pre-replacement) {@code WizInfo} for use in post-save cleanup.
    */
   private static Viewsheet.WizInfo updateWizVisualization(RuntimeViewsheet rvs) {
      if(rvs.getViewsheet() != null && rvs.getViewsheet().getWizInfo() != null &&
         rvs.getViewsheet().getWizInfo().isWizSheet())
      {
         Viewsheet.WizInfo wizInfo = rvs.getViewsheet().getWizInfo();
         Viewsheet.WizInfo owizInfo = wizInfo.clone();

         Set<String> visualizations = wizInfo.getVisualizations();

         if(!visualizations.isEmpty()) {
            for(String visualization : visualizations) {
               AssetEntry parsedEntry = AssetEntry.createAssetEntry(visualization);

               if(parsedEntry == null) {
                  LOG.warn("Could not parse visualization identifier: {}", visualization);
                  continue;
               }

               AssetEntry original = createWizOriginalVisualization(parsedEntry);
               wizInfo.removeVisualization(visualization);
               wizInfo.addVisualization(original.toIdentifier());
            }
         }

         return owizInfo;
      }

      return null;
   }

   /**
    * Returns a copy of {@code originalEntry} with its path rewritten to use
    * {@code newName} within the same parent folder.
    */
   private static AssetEntry renameAssetEntry(AssetEntry originalEntry, String newName) {
      String parentPath = originalEntry.getParentPath();

      if(parentPath == null || parentPath.isEmpty()) {
         parentPath = "/";
      }

      String newPath;

      if("/".equals(parentPath)) {
         newPath = newName;
      }
      else if(parentPath.endsWith("/")) {
         newPath = parentPath + newName;
      }
      else {
         newPath = parentPath + "/" + newName;
      }

      return new AssetEntry(
         originalEntry.getScope(), AssetEntry.Type.VIEWSHEET, newPath, originalEntry.getUser());
   }

   // ---------------------------------------------------------------------------
   // Constants
   // ---------------------------------------------------------------------------

   public enum VisualizationScope {
      PUBLIC("public"),
      SHARED("shared"),
      PRIVATE("private");

      VisualizationScope(String value) {
         this.value = value;
      }

      public String getValue() {
         return value;
      }

      /**
       * Returns the scope whose {@link #getValue()} equals {@code value},
       * or {@code null} if no match is found.
       */
      public static VisualizationScope fromValue(String value) {
         for(VisualizationScope scope : values()) {
            if(scope.value.equals(value)) {
               return scope;
            }
         }

         return null;
      }

      private final String value;
   }

   /**
    * Prefix used for wiz-copy viewsheet names.
    */
   public static final String WIZ_COPY_PREFIX = "__wiz__";

   /**
    * Prefix used for temporary wiz dashboard worksheet names.
    */
   public static final String WIZ_DASH_WS_PREFIX = "__wiz_ws__";

   private static final Logger LOG = LoggerFactory.getLogger(WizUtil.class);
}
