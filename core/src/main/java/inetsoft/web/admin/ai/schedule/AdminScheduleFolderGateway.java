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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import inetsoft.web.admin.schedule.model.TaskListModel;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code AdminScheduleFolderGateway} implements, directly against the EXISTING, already-audited
 * {@link ScheduleTaskFolderService} (design §2) -- not a reimplementation of any of its logic, only
 * a narrow translation layer between the admin-plugin's request/response shapes and these existing
 * service calls, plus the ONE piece of safety logic that does not exist anywhere yet: a
 * non-empty-folder-delete count ({@link #countContainedTasks}), computed by a read-only recursive
 * walk of {@link ScheduleTaskFolderService#getTaskFolder}, the same shape {@code
 * ScheduleService#checkScheduledTaskDependency0}'s own walk already uses.
 *
 * <p>Every method here calls straight into {@code ScheduleTaskFolderService}'s own {@code
 * checkFolderPermission}/{@code hasFolderPermission}-gated methods, which are the REAL,
 * owner/group-share-aware authorization surface for this data (design §3 decision 1) -- diverging
 * from it (e.g. gating on the coarser {@code SCHEDULER}/{@code "*"}/{@code ACCESS} flat check the
 * schedule-TASK area uses) would under-authorize compared to what Enterprise Manager's own console
 * enforces.
 */
@Service
public class AdminScheduleFolderGateway {
   @Autowired
   public AdminScheduleFolderGateway(ScheduleTaskFolderService taskFolderService,
                                     ScheduleService scheduleService)
   {
      this.taskFolderService = taskFolderService;
      this.scheduleService = scheduleService;
   }

   /**
    * Root-normalized: {@code null}/blank/{@code "/"} all mean the root folder. A non-root path
    * never carries a leading slash anywhere in {@code ScheduleTaskFolderService}/{@code
    * AssetEntry} (e.g. {@code moveScheduleItems} builds a nested path as {@code
    * targetEntry.getPath() + "/" + folderEntry.getName()}, never prefixing a leading slash) -- a
    * caller-supplied path WITH a leading slash (a natural, unambiguous convention an LLM caller may
    * reasonably use) is stripped down to the same canonical form, so {@code "/Finance/Reports"} and
    * {@code "Finance/Reports"} resolve to the identical folder rather than being silently treated
    * as two different, unrelated identities.
    */
   public static String normalizePath(String path) {
      if(path == null || path.isBlank()) {
         return "/";
      }

      String trimmed = path.trim();

      while(trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }

      return trimmed.isEmpty() ? "/" : trimmed;
   }

   public static boolean isRootPath(String path) {
      return "/".equals(normalizePath(path));
   }

   /** The parent path of {@code path} (root if {@code path} has no {@code /}). */
   public static String parentOf(String path) {
      String normalized = normalizePath(path);

      if(isRootPath(normalized)) {
         return "/";
      }

      int idx = normalized.lastIndexOf('/');
      return idx < 0 ? "/" : normalized.substring(0, idx);
   }

   /** The leaf (own) name of {@code path}. */
   public static String leafOf(String path) {
      String normalized = normalizePath(path);

      if(isRootPath(normalized)) {
         return "/";
      }

      int idx = normalized.lastIndexOf('/');
      return idx < 0 ? normalized : normalized.substring(idx + 1);
   }

   /** Joins a parent path and a leaf name into a full path -- root-aware (no leading slash when
    * the parent is root, matching {@code ScheduleTaskFolderService.moveScheduleItems}'s own
    * convention for a path relative to root). */
   public static String joinPath(String parentPath, String leafName) {
      return isRootPath(parentPath) ? leafName : normalizePath(parentPath) + "/" + leafName;
   }

   /**
    * Resolves the {@link AssetFolder} at {@code path}, or {@code null} if none exists there --
    * {@code getTaskFolder} itself has no documented "not found" contract (it round-trips through
    * {@code IndexedStorage}, which can either return {@code null} or throw depending on the
    * underlying provider), so both are folded into the same "not found" answer here, once, rather
    * than leaving every caller to guess which it might see.
    */
   public AssetFolder findFolder(String path) {
      AssetEntry entry = taskFolderService.getFolderEntry(normalizePath(path));

      try {
         return taskFolderService.getTaskFolder(entry.toIdentifier());
      }
      catch(Exception e) {
         return null;
      }
   }

   public boolean folderExists(String path) {
      return findFolder(path) != null;
   }

   /** The owner a NOT-YET-existing {@code path} would inherit if created now (mkdir -p walks up
    * to the nearest existing ancestor and inherits ITS owner, recursively, exactly the way {@code
    * ScheduleTaskFolderService#addFolder} inherits from its immediate parent) -- preview-only, used
    * to build a create's PROPOSED projection; never used to actually create anything. */
   public IdentityID resolveInheritedOwner(String path) {
      String current = normalizePath(path);

      while(true) {
         AssetFolder folder = findFolder(current);

         if(folder != null) {
            return folder.getOwner();
         }

         if(isRootPath(current)) {
            return null;
         }

         current = parentOf(current);
      }
   }

   public ScheduleFolderView getFolder(String path, Principal user) throws Exception {
      String normalized = normalizePath(path);
      AssetFolder folder = findFolder(normalized);

      if(folder == null) {
         return ScheduleFolderView.notFound();
      }

      if(!isRootPath(normalized) &&
         !taskFolderService.checkFolderPermission(normalized, user, ResourceAction.READ))
      {
         throw new UnauthorizedAccessException();
      }

      List<String> childFolderPaths = Arrays.stream(folder.getEntries())
         .filter(AssetEntry::isScheduleTaskFolder)
         .map(AssetEntry::getPath)
         .sorted()
         .collect(Collectors.toList());

      String owner = folder.getOwner() == null ? null : folder.getOwner().convertToKey();
      return ScheduleFolderView.of(normalized, owner, childFolderPaths);
   }

   /**
    * Creates {@code path}, auto-creating any missing ancestor folder along the way ("mkdir -p" --
    * design §3 decision 4), since {@link ScheduleTaskFolderService#addFolder} itself NPEs on a
    * missing parent rather than creating one. Each already-existing ancestor is left untouched;
    * each newly-created ancestor inherits its own immediate parent's owner, the same rule {@code
    * addFolder} itself applies at the leaf.
    */
   public void createFolder(String path, Principal user) throws Exception {
      String normalized = normalizePath(path);
      String[] segments = normalized.split("/");
      StringBuilder built = new StringBuilder();

      for(String segment : segments) {
         if(segment.isEmpty()) {
            continue;
         }

         String parentPath = built.length() == 0 ? "/" : built.toString();

         if(built.length() > 0) {
            built.append('/');
         }

         built.append(segment);
         String currentPath = built.toString();

         if(folderExists(currentPath)) {
            continue;
         }

         AssetEntry parentEntry = taskFolderService.getFolderEntry(parentPath);
         taskFolderService.addFolder(
            parentEntry, currentPath, parentPath, AssetRepository.GLOBAL_SCOPE, user);
      }
   }

   /**
    * Renames {@code oldPath}'s own leaf name so its full path becomes {@code newPath} -- NAME ONLY
    * (design §3 decision 2): the owner is deliberately preserved, never reassigned, even though the
    * underlying {@code renameFolder} primitive supports changing it in the same call.
    *
    * <p>Preserving the owner here is NOT simply "pass null" -- {@code
    * ScheduleTaskFolderService#changeFolder}'s own owner-resolution reads: inherit the prior owner
    * only when the given {@code owner} is non-null AND has a blank {@code name}; a null {@code
    * owner} instead falls through to its {@code else} branch and gets set AS the new owner, i.e.
    * WIPES it to null. An {@link IdentityID} with an empty name (not a null reference) is the only
    * value that actually triggers the "keep the existing owner" branch -- this is exactly the kind
    * of native-primitive footgun the repo CLAUDE.md's "tool-misuse is a plugin gap" rule exists for,
    * closed here rather than left for a future caller to trip over.
    */
   public AssetEntry renameFolder(String oldPath, String newPath, Principal user) throws Exception {
      EditTaskFolderDialogModel model = EditTaskFolderDialogModel.builder()
         .oldPath(normalizePath(oldPath))
         .folderName(leafOf(newPath))
         .owner(new IdentityID("", null))
         .securityEnabled(false)
         .build();

      return taskFolderService.renameFolder(model, user);
   }

   /**
    * Relocates {@code path} to be a child of {@code targetPath}. Wraps {@code
    * ScheduleTaskFolderService#moveScheduleItems} with a single-entry {@code folders[]} and no
    * {@code tasks[]} (Track A's own folders-only scope, design §6.1) -- NOT a passthrough of every
    * behavior that primitive has, though: {@code
    * ScheduleFolderChangePlanService#requireNotSelfOrDescendantMove} re-derives a CORRECT,
    * segment-boundary-aware self/descendant guard at preview time, because {@code
    * moveScheduleItems}'s own guard (a raw {@code StringUtils.startsWith} test) both silently no-ops
    * on a true self/descendant move AND false-positives on an unrelated sibling whose name happens
    * to share the same string prefix (design §6.1's own load-bearing finding) -- neither this
    * gateway nor the plan service touches that shared primitive to fix it.
    *
    * <p>Separately, {@code moveScheduleItems} unconditionally calls the 3-argument {@code
    * changeFolder(oentry, nentry, principal)} overload for a folder move, which passes {@code
    * newOwner=null} -- by the same owner-resolution rule documented on {@link #renameFolder}, a
    * null owner is NOT "keep the current owner", it WIPES the folder's owner to {@code null}. This
    * is a genuine, pre-existing side effect of the underlying primitive itself (not introduced by
    * this gateway, and not something this gateway can suppress without reimplementing {@code
    * moveScheduleItems}) -- disclosed here and in the wiz tool description rather than silently
    * hidden, matching this design's own "flag pre-existing StyleBI defects, don't silently fix the
    * shared service" posture (§3 decision 3, §6.1).
    */
   public void moveFolder(String path, String targetPath, Principal user) throws Exception {
      AssetEntry targetEntry = taskFolderService.getFolderEntry(normalizePath(targetPath));
      taskFolderService.moveScheduleItems(null, new String[]{ normalizePath(path) }, targetEntry, user);
   }

   /**
    * Recursively counts every schedule task contained under {@code path}, directly or via nested
    * folders -- read-only, the non-empty-delete safety net this design puts in THIS gateway rather
    * than in the shared {@code ScheduleService#removeScheduleFolders} (design §3 decision 3), which
    * itself has no such check at all (design §0.2).
    */
   public int countContainedTasks(String path) throws Exception {
      AssetFolder folder = findFolder(path);
      return folder == null ? 0 : countContainedTasks0(folder);
   }

   private int countContainedTasks0(AssetFolder folder) throws Exception {
      int count = 0;

      for(AssetEntry entry : folder.getEntries()) {
         if(entry.isScheduleTaskFolder()) {
            AssetFolder child = taskFolderService.getTaskFolder(entry.toIdentifier());
            count += child == null ? 0 : countContainedTasks0(child);
         }
         else {
            count++;
         }
      }

      return count;
   }

   /**
    * Deletes {@code path}, recursively -- {@code ScheduleService#removeScheduleFolders} itself is
    * unconditional (design §0.2: no non-empty check, no {@code force} parameter at the Java layer
    * at all); the {@code force} gate this area exposes is enforced entirely at {@link
    * ScheduleFolderChangePlanService#resolve} time, before this method is ever called.
    */
   public void deleteFolder(String path, Principal user) throws Exception {
      TaskListModel model = TaskListModel.builder().addTaskNames(normalizePath(path)).build();
      scheduleService.removeScheduleFolders(model, user);
   }

   private final ScheduleTaskFolderService taskFolderService;
   private final ScheduleService scheduleService;
}
