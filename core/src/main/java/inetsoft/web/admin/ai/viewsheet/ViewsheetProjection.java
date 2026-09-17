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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.Sheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds the canonical, human-readable projection of a viewsheet or folder -- used as both the
 * plan hash input (01-spec.md section 5) and the audit before/after value (section 8). No
 * secret-classified field exists in this area's scope (section 9), so full disclosure is used
 * throughout, unlike {@code DataSourceProjection}'s password substitution.
 */
final class ViewsheetProjection {
   private ViewsheetProjection() {
   }

   /** {@code null} when the viewsheet was not found (section 5's hash still needs a stable,
    * distinguishable marker for "did not exist"). */
   static String projectViewsheet(Sheet s) {
      if(s == null) {
         return null;
      }

      return "type=viewsheet" +
         ";asset=" + canonical(s.getAsset()) +
         ";path=" + canonical(s.getPath()) +
         ";label=" + canonical(s.getLabel()) +
         ";global=" + s.isGlobal() +
         ";owner=" + canonical(s.getUser() == null ? null : s.getUser().convertToKey());
   }

   /** A synthetic "proposed" projection for a viewsheet rename -- the real new identifier is not
    * knowable until the rename actually succeeds (section 2's identity-changes-on-rename finding),
    * so {@code asset} is a fixed pending marker, never a guessed value. */
   static String projectProposedRename(String newPath, boolean global, IdentityID owner) {
      return "type=viewsheet" +
         ";asset=" + PENDING_MARKER +
         ";path=" + canonical(newPath) +
         ";label=" + canonical(newPath) +
         ";global=" + global +
         ";owner=" + canonical(owner == null ? null : owner.convertToKey());
   }

   /** Parallel to {@link #projectViewsheet}, for a worksheet (Track B/Redmine #76604 Gap7). No
    * alias/description here -- those are new only for the {@code update} verb, see {@link
    * #projectWorksheetUpdate}. */
   static String projectWorksheet(Sheet s) {
      if(s == null) {
         return null;
      }

      return "type=worksheet" +
         ";asset=" + canonical(s.getAsset()) +
         ";path=" + canonical(s.getPath()) +
         ";label=" + canonical(s.getLabel()) +
         ";global=" + s.isGlobal() +
         ";owner=" + canonical(s.getUser() == null ? null : s.getUser().convertToKey());
   }

   /** Parallel to {@link #projectProposedRename}, for a worksheet rename. */
   static String projectProposedWorksheetRename(String newPath, boolean global, IdentityID owner) {
      return "type=worksheet" +
         ";asset=" + PENDING_MARKER +
         ";path=" + canonical(newPath) +
         ";label=" + canonical(newPath) +
         ";global=" + global +
         ";owner=" + canonical(owner == null ? null : owner.convertToKey());
   }

   /** Track B: the {@code verb: "update"} projection for a viewsheet OR worksheet -- reused for
    * both the "before" (pass the CURRENT alias/description) and "proposed"/"after" (pass the
    * MERGED alias/description) values, since an update never changes the sheet's own identity
    * fields (asset/path/label/global/owner). */
   static String projectViewsheetUpdate(Sheet current, String alias, String description) {
      return projectSheetUpdate("viewsheet", current, alias, description);
   }

   /** Same as {@link #projectViewsheetUpdate}, for a worksheet. */
   static String projectWorksheetUpdate(Sheet current, String alias, String description) {
      return projectSheetUpdate("worksheet", current, alias, description);
   }

   private static String projectSheetUpdate(String type, Sheet current, String alias,
                                            String description)
   {
      return "type=" + type +
         ";asset=" + canonical(current.getAsset()) +
         ";path=" + canonical(current.getPath()) +
         ";label=" + canonical(current.getLabel()) +
         ";global=" + current.isGlobal() +
         ";owner=" + canonical(current.getUser() == null ? null : current.getUser().convertToKey()) +
         ";alias=" + canonical(alias) +
         ";description=" + canonical(description);
   }

   static String projectFolder(GetViewsheetFolderResult r) {
      return "type=folder" +
         ";found=" + r.found() +
         ";path=" + canonical(r.path()) +
         ";owner=" + canonical(r.owner()) +
         ";alias=" + canonical(r.alias()) +
         ";description=" + canonical(r.description());
   }

   /** Section 0.2/5: the dependency-preflight result folds into the plan hash for a viewsheet
    * delete, so a concurrent change that adds a new dependency between preview and apply also
    * perturbs it. */
   static String projectDependencies(AssetEntry[] dependencies) {
      if(dependencies == null || dependencies.length == 0) {
         return "(none)";
      }

      return Arrays.stream(dependencies)
         .map(e -> e.getType() + ":" + e.getPath())
         .sorted()
         .collect(Collectors.joining(","));
   }

   /** Renders a {@code findFolderContents} result for caller-facing text without disclosing any
    * entry the caller has no READ permission on (bug #76469 follow-up review finding 4): {@code
    * all} is the full, permission-bypassed content array used for the count/risk gate; {@code
    * visible} (see {@code ViewsheetChangePlanService#visibleFolderContents}) is the permission-
    * respecting subset of it. Entries only present in {@code all} are represented solely by an
    * anonymous count -- never by type or path. */
   static String projectVisibleContents(AssetEntry[] all, AssetEntry[] visible) {
      int hidden = all.length - visible.length;

      if(hidden == 0) {
         return projectDependencies(visible);
      }

      String hiddenNote = hidden + " of which you do not have permission to view";
      return visible.length == 0 ? hiddenNote : projectDependencies(visible) + "; " + hiddenNote;
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private static final String PENDING_MARKER = "(pending)";
}
