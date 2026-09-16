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
package inetsoft.web.admin.ai.schedule;

import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of schedule-task FOLDER changes into a {@link ResolvedPlan} and hashes
 * it -- the folder-shaped analog of {@link ScheduleChangePlanService}, replicated rather than
 * shared (same precedent that class's own javadoc documents for why it doesn't share {@code
 * AdminChangePlanService}).
 *
 * <p>Every entry's {@code property} key is the folder path being acted on (the ORIGINAL path for
 * rename/move/delete, the newly-created path for create) -- the same "one entry per identity,
 * never an implicit batch" convention viewsheet folder rename already uses.
 *
 * <p>Per-entry {@code risk} varies exactly the way Viewsheets' own folder verbs do: LOW for
 * create/rename/move (nothing is destroyed), and for delete, LOW when the folder is empty or HIGH
 * when it is not (mirrors {@code ViewsheetChangesetApplyService#applyFolderDelete}'s own {@code
 * contents.length == 0 ? RISK_LOW : RISK_HIGH} exactly). This is independent of {@link
 * ResolvedPlan#requiresAgentSignoff}, which this area hardcodes {@code true} UNCONDITIONALLY for
 * the whole plan regardless of any entry's own risk (design §4) -- unlike Viewsheets, where
 * signoff genuinely varies by verb/risk.
 */
@Component
public class ScheduleFolderChangePlanService {
   @Autowired
   public ScheduleFolderChangePlanService(AdminScheduleFolderGateway folderGateway) {
      this.folderGateway = folderGateway;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation of any folder, but DOES read live folder
    * state (existence/emptiness checks below).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, a field used on the wrong verb, a
    *                                 root-path target, a self/descendant move, a name collision, a
    *                                 missing source folder, or a non-empty delete without {@code
    *                                 force}.
    */
   public ResolvedPlan resolve(ScheduleFolderChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenPaths = new HashSet<>();
      int index = 0;

      for(ScheduleFolderChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, seenPaths));
      }

      String planHash = hash(changes);
      String task = req.getTask().trim();
      // requiresStorageBackup: unconditionally true, every verb here mutates the schedule-task
      // folder tree. requiresAgentSignoff: unconditionally true (design §4) -- this area has no
      // task-facing low-risk "update" verb the way Viewsheets does, and a non-empty delete is
      // unconditionally catastrophic, so collapsing to "always signoff" is the simpler, safer
      // default for this first cut.
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true, planHash,
                              TaskAuditToken.issue(planHash, task));
   }

   private PlanChange resolveOne(String label, ScheduleFolderChangeRequest change, Principal user,
                                 Set<String> seenPaths)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = normalizeVerb(label, change.getVerb());

      switch(verb) {
      case ScheduleFolderChangeRequest.VERB_CREATE:
         return resolveCreate(label, change, seenPaths);
      case ScheduleFolderChangeRequest.VERB_RENAME:
         return resolveRename(label, change, seenPaths);
      case ScheduleFolderChangeRequest.VERB_MOVE:
         return resolveMove(label, change, seenPaths);
      default:
         return resolveDelete(label, change, seenPaths);
      }
   }

   /** Accepts the four canonical verbs verbatim; the tool layer normalizes natural aliases before
    * this point (same convention {@code ScheduleChangePlanService#normalizeVerb} documents), so
    * anything reaching here that is not exactly one of the four is a genuine caller error. */
   private static String normalizeVerb(String label, String verb) {
      if(ScheduleFolderChangeRequest.VERB_CREATE.equals(verb) ||
         ScheduleFolderChangeRequest.VERB_RENAME.equals(verb) ||
         ScheduleFolderChangeRequest.VERB_MOVE.equals(verb) ||
         ScheduleFolderChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ScheduleFolderChangeRequest.VERB_CREATE + "\", \"" +
         ScheduleFolderChangeRequest.VERB_RENAME + "\", \"" + ScheduleFolderChangeRequest.VERB_MOVE +
         "\", or \"" + ScheduleFolderChangeRequest.VERB_DELETE + "\", got " + String.valueOf(verb));
   }

   private PlanChange resolveCreate(String label, ScheduleFolderChangeRequest change,
                                    Set<String> seenPaths)
      throws Exception
   {
      requireUnused(label, "path", change.getPath(), "create");
      requireUnused(label, "newPath", change.getNewPath(), "create");
      requireUnused(label, "targetPath", change.getTargetPath(), "create");

      if(change.getFolderName() == null || change.getFolderName().isBlank()) {
         throw new IllegalArgumentException(label + ".folderName: required for verb=create");
      }

      if(change.getFolderName().contains("/")) {
         throw new IllegalArgumentException(
            label + ".folderName: must be a leaf name, not a path (\"" + change.getFolderName() +
            "\") -- use parentPath for the containing folder");
      }

      String parentPath = AdminScheduleFolderGateway.normalizePath(change.getParentPath());
      String fullPath = AdminScheduleFolderGateway.joinPath(parentPath, change.getFolderName().trim());

      requireUnseen(label, fullPath, seenPaths);

      if(folderGateway.folderExists(fullPath)) {
         throw new IllegalArgumentException(
            label + ": a schedule-task folder already exists at \"" + fullPath + "\"");
      }

      AssetFolder proposed = new AssetFolder();
      proposed.setOwner(folderGateway.resolveInheritedOwner(parentPath));
      String proposedProjection = ScheduleFolderXmlProjection.project(fullPath, proposed);

      return new PlanChange(fullPath, null, null, proposedProjection, AdminChangeRecord.RISK_LOW,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "create schedule task folder \"" + fullPath + "\"");
   }

   private PlanChange resolveRename(String label, ScheduleFolderChangeRequest change,
                                    Set<String> seenPaths)
      throws Exception
   {
      requireUnused(label, "parentPath", change.getParentPath(), "rename");
      requireUnused(label, "folderName", change.getFolderName(), "rename");
      requireUnused(label, "targetPath", change.getTargetPath(), "rename");

      String oldPath = requirePath(label, change, "rename");

      if(change.getNewPath() == null || change.getNewPath().isBlank()) {
         throw new IllegalArgumentException(label + ".newPath: required for verb=rename");
      }

      String newPath = AdminScheduleFolderGateway.normalizePath(change.getNewPath());
      requireNotRoot(label, "path", oldPath);
      // §6.2: a rename's newPath resolving to root is refused too -- a folder cannot rename
      // itself into becoming root.
      requireNotRoot(label, "newPath", newPath);
      requireUnseen(label, oldPath, seenPaths);

      AssetFolder folder = folderGateway.findFolder(oldPath);

      if(folder == null) {
         throw new IllegalArgumentException(
            label + ".path: no schedule-task folder exists at \"" + oldPath + "\"");
      }

      if(!newPath.equals(oldPath) && folderGateway.folderExists(newPath)) {
         throw new IllegalArgumentException(
            label + ".newPath: a schedule-task folder already exists at \"" + newPath + "\"");
      }

      String current = ScheduleFolderXmlProjection.project(oldPath, folder);
      String proposed = ScheduleFolderXmlProjection.project(newPath, folder);
      return new PlanChange(oldPath, null, current, proposed, AdminChangeRecord.RISK_LOW,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "rename schedule task folder \"" + oldPath + "\" to \"" + newPath + "\"");
   }

   private PlanChange resolveMove(String label, ScheduleFolderChangeRequest change,
                                  Set<String> seenPaths)
      throws Exception
   {
      requireUnused(label, "parentPath", change.getParentPath(), "move");
      requireUnused(label, "folderName", change.getFolderName(), "move");
      requireUnused(label, "newPath", change.getNewPath(), "move");

      String path = requirePath(label, change, "move");

      if(change.getTargetPath() == null || change.getTargetPath().isBlank()) {
         throw new IllegalArgumentException(label + ".targetPath: required for verb=move");
      }

      String targetPath = AdminScheduleFolderGateway.normalizePath(change.getTargetPath());
      // §6.2: root can never be the thing MOVED, but moving something TO the top level
      // (targetPath="/") is a legitimate, ordinary operation and must remain allowed.
      requireNotRoot(label, "path", path);
      requireUnseen(label, path, seenPaths);

      AssetFolder folder = folderGateway.findFolder(path);

      if(folder == null) {
         throw new IllegalArgumentException(
            label + ".path: no schedule-task folder exists at \"" + path + "\"");
      }

      if(!AdminScheduleFolderGateway.isRootPath(targetPath) && !folderGateway.folderExists(targetPath)) {
         throw new IllegalArgumentException(
            label + ".targetPath: no schedule-task folder exists at \"" + targetPath + "\"");
      }

      requireNotSelfOrDescendantMove(label, path, targetPath);

      String resultingPath = AdminScheduleFolderGateway.joinPath(targetPath, AdminScheduleFolderGateway.leafOf(path));

      if(!resultingPath.equals(path) && folderGateway.folderExists(resultingPath)) {
         throw new IllegalArgumentException(
            label + ": a schedule-task folder already exists at \"" + resultingPath +
            "\" (moving \"" + path + "\" into \"" + targetPath + "\" would collide with it)");
      }

      String current = ScheduleFolderXmlProjection.project(path, folder);
      String proposed = ScheduleFolderXmlProjection.project(resultingPath, folder);
      return new PlanChange(path, null, current, proposed, AdminChangeRecord.RISK_LOW,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "move schedule task folder \"" + path + "\" into \"" + targetPath + "\"");
   }

   private PlanChange resolveDelete(String label, ScheduleFolderChangeRequest change,
                                    Set<String> seenPaths)
      throws Exception
   {
      requireUnused(label, "parentPath", change.getParentPath(), "delete");
      requireUnused(label, "folderName", change.getFolderName(), "delete");
      requireUnused(label, "newPath", change.getNewPath(), "delete");
      requireUnused(label, "targetPath", change.getTargetPath(), "delete");

      String path = requirePath(label, change, "delete");
      requireNotRoot(label, "path", path);
      requireUnseen(label, path, seenPaths);

      AssetFolder folder = folderGateway.findFolder(path);

      if(folder == null) {
         throw new IllegalArgumentException(
            label + ".path: no schedule-task folder exists at \"" + path + "\"");
      }

      // The non-empty-delete safety net this design puts in the wiz gateway only (§0.2, §3
      // decision 3) -- ScheduleService#removeScheduleFolders itself is unconditional, no force
      // gate, no non-empty check, at the Java layer at all.
      int containedTaskCount = folderGateway.countContainedTasks(path);

      if(containedTaskCount > 0 && !change.isForce()) {
         throw new IllegalArgumentException(
            label + ".force: required (true) -- folder \"" + path + "\" contains " +
            containedTaskCount + " schedule task(s), recursively; deleting it PERMANENTLY deletes " +
            "every one of them too, with no recycle bin and no undo");
      }

      String current = ScheduleFolderXmlProjection.project(path, folder);
      String risk = containedTaskCount == 0 ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;
      String description = containedTaskCount == 0
         ? "delete empty schedule task folder \"" + path + "\""
         : "delete schedule task folder \"" + path + "\", permanently destroying " +
           containedTaskCount + " contained schedule task(s), recursively";

      return new PlanChange(path, null, current, null, risk, AdminChangeRecord.SCOPE_STORAGE, true,
                            description);
   }

   private static String requirePath(String label, ScheduleFolderChangeRequest change, String verb) {
      if(change.getPath() == null || change.getPath().isBlank()) {
         throw new IllegalArgumentException(label + ".path: required for verb=" + verb);
      }

      return AdminScheduleFolderGateway.normalizePath(change.getPath());
   }

   private static void requireUnused(String label, String field, String value, String verb) {
      if(value != null && !value.isBlank()) {
         throw new IllegalArgumentException(
            label + "." + field + ": not used for verb=" + verb + "; remove it");
      }
   }

   private static void requireNotRoot(String label, String field, String path) {
      if(AdminScheduleFolderGateway.isRootPath(path)) {
         throw new IllegalArgumentException(
            label + "." + field + ": cannot target the root schedule-task folder (\"/\") -- " +
            "rename/delete/move only apply to a folder below the root");
      }
   }

   private static void requireUnseen(String label, String path, Set<String> seenPaths) {
      if(!seenPaths.add(path)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for folder \"" + path + "\"; list each folder at most once");
      }
   }

   /**
    * Re-derives, in TypeScript's own words (design §6.1), the CORRECT segment-boundary-aware
    * self/descendant guard {@code ScheduleTaskFolderService#moveScheduleItems}'s own {@code
    * StringUtils.startsWith(targetEntry.getPath(), folderPath)} check should have been, but is not:
    * that raw string-prefix test both silently no-ops on a true self/descendant move (no exception,
    * no signal -- an HTTP 200 with nothing having happened) AND false-positives on an unrelated
    * sibling whose name happens to share the same string prefix (moving folder "A" to target
    * "AB/X", not a descendant of "A" at all). This plan service runs the correct check itself,
    * before the underlying primitive is ever called, so admin-chat's own behavior is correct
    * regardless of whether the underlying Java bug is ever fixed -- the same defense-in-depth this
    * design asks the wiz-side tool layer to also provide independently (not a replacement for that
    * check, a second, cheap copy of it at the layer the HTTP endpoint itself is exposed through).
    */
   static void requireNotSelfOrDescendantMove(String label, String path, String targetPath) {
      if(targetPath.equals(path) || targetPath.startsWith(path + "/")) {
         throw new IllegalArgumentException(
            label + ".targetPath: \"" + targetPath + "\" is \"" + path + "\" itself or a " +
            "descendant of it -- a folder cannot be moved into itself or into one of its own " +
            "subfolders");
      }
   }

   /**
    * SHA-256 over the canonical plan. Same field-order/control-character contract as {@code
    * ScheduleChangePlanService#hash} -- changing it invalidates every outstanding preview, which is
    * safe (apply is refused with 409) but forces re-review.
    */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a schedule folder change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final AdminScheduleFolderGateway folderGateway;
}
