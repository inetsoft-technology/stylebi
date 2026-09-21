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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import inetsoft.web.admin.schedule.model.TaskListModel;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
                                     ScheduleService scheduleService,
                                     ScheduleManager scheduleManager,
                                     SecurityEngine securityEngine,
                                     ScheduleTaskService scheduleTaskService)
   {
      this.taskFolderService = taskFolderService;
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.securityEngine = securityEngine;
      this.scheduleTaskService = scheduleTaskService;
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
      createFolder(path, user, new AtomicBoolean());
   }

   /**
    * Same as {@link #createFolder(String, Principal)}, but reports via {@code mutationEntered}
    * whether any of the loop's underlying {@code taskFolderService.addFolder} calls was actually
    * reached -- {@code addFolder} performs its OWN permission check (bug #76856:
    * {@link ScheduleTaskFolderService#addFolder}, {@code WRITE} on the segment's parent path)
    * strictly before its own mutating writes, so a segment whose permission check fails must not
    * be conflated with "nothing was ever created". The check is duplicated here (via {@link
    * ScheduleTaskFolderService#checkFolderPermission}, the same public method {@code addFolder}
    * itself is built on) so {@code mutationEntered} can be set immediately before, not after, each
    * segment's actual mutating call.
    *
    * <p>{@code mutationEntered} is set at most once and never reset: once an earlier segment in
    * the "mkdir -p" walk has actually created a folder, a LATER segment's permission failure is
    * still a genuine partial-mutation risk for the walk as a whole, even though that later segment
    * itself never mutated anything.
    */
   public void createFolder(String path, Principal user, AtomicBoolean mutationEntered) throws Exception {
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

         if(!taskFolderService.checkFolderPermission(parentPath, user, ResourceAction.WRITE)) {
            throw new UnauthorizedAccessException();
         }

         mutationEntered.set(true);
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
      return renameFolder(oldPath, newPath, user, new AtomicBoolean());
   }

   /**
    * Same as {@link #renameFolder(String, String, Principal)}, but reports via {@code
    * mutationEntered} whether the actual mutating call ({@code taskFolderService.renameFolder}'s
    * own {@code changeFolder}) was reached -- {@code taskFolderService.renameFolder} performs its
    * OWN {@code DELETE}/{@code WRITE} permission checks and a folder-existence check (bug #76856:
    * {@link ScheduleTaskFolderService#renameFolder}) strictly before that mutating call, so this
    * gateway's own opaque passthrough would otherwise mark a pure permission/not-found refusal as
    * a partial-mutation risk, the same bug pattern {@link #moveTask(String, String, Principal,
    * AtomicBoolean)} already closes one layer deeper. Both checks are duplicated here (via {@link
    * ScheduleTaskFolderService#checkFolderPermission} and {@link #findFolder}, the same
    * public members {@code renameFolder} itself is built on) so {@code mutationEntered} can be set
    * immediately before, not after, the mutating call.
    */
   public AssetEntry renameFolder(String oldPath, String newPath, Principal user,
                                  AtomicBoolean mutationEntered)
      throws Exception
   {
      String normalizedOldPath = normalizePath(oldPath);

      if(!taskFolderService.checkFolderPermission(normalizedOldPath, user, ResourceAction.DELETE) ||
         !taskFolderService.checkFolderPermission(normalizedOldPath, user, ResourceAction.WRITE))
      {
         throw new UnauthorizedAccessException();
      }

      if(findFolder(normalizedOldPath) == null) {
         throw new FileNotFoundException(normalizedOldPath);
      }

      EditTaskFolderDialogModel model = EditTaskFolderDialogModel.builder()
         .oldPath(normalizedOldPath)
         .folderName(leafOf(newPath))
         .owner(new IdentityID("", null))
         .securityEnabled(false)
         .build();

      mutationEntered.set(true);
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
      moveFolder(path, targetPath, user, new AtomicBoolean());
   }

   /**
    * Same as {@link #moveFolder(String, String, Principal)}, but reports via {@code
    * mutationEntered} whether the actual mutating call ({@code
    * taskFolderService.moveScheduleItems}'s own {@code changeFolder}) was reached -- {@code
    * moveScheduleItems} performs its OWN {@code WRITE}-on-target and, per folder, {@code
    * DELETE}-on-source permission checks (bug #76856: {@link
    * ScheduleTaskFolderService#moveScheduleItems}) strictly before that mutating call, the same
    * bug pattern {@link #moveTask(String, String, Principal, AtomicBoolean)} already closes one
    * layer deeper. Both checks are duplicated here (via {@link
    * ScheduleTaskFolderService#checkFolderPermission}, the same public method {@code
    * moveScheduleItems} itself is built on, with the identical "moving into itself/a descendant
    * skips the DELETE check" condition) so {@code mutationEntered} can be set immediately before,
    * not after, the mutating call.
    */
   public void moveFolder(String path, String targetPath, Principal user, AtomicBoolean mutationEntered)
      throws Exception
   {
      String normalizedPath = normalizePath(path);
      AssetEntry targetEntry = taskFolderService.getFolderEntry(normalizePath(targetPath));

      if(!taskFolderService.checkFolderPermission(targetEntry.getPath(), user, ResourceAction.WRITE)) {
         throw new UnauthorizedAccessException();
      }

      if(!StringUtils.startsWith(targetEntry.getPath(), normalizedPath) &&
         !taskFolderService.checkFolderPermission(normalizedPath, user, ResourceAction.DELETE))
      {
         throw new UnauthorizedAccessException();
      }

      mutationEntered.set(true);
      taskFolderService.moveScheduleItems(null, new String[]{ normalizedPath }, targetEntry, user);
   }

   /**
    * Whether a schedule task with id {@code taskId} exists -- the task-move analog of {@link
    * #folderExists}, used by {@link ScheduleFolderChangePlanService#resolve} to refuse loud at plan
    * time on an unknown taskId, mirroring {@link #folderExists}'s own role for {@code resolveMove}.
    */
   public boolean taskExists(String taskId) {
      return scheduleManager.getScheduleTask(taskId) != null;
   }

   /** The live folder path {@code taskId} currently sits at, or {@code null} if the task does not
    * exist -- used purely for apply-time before/after evidence, not for any decision. */
   public String getTaskPath(String taskId) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskId);
      return task == null ? null : task.getPath();
   }

   /**
    * Relocates the schedule task {@code taskId} to be a child of {@code targetPath} -- wraps the
    * same {@link ScheduleTaskFolderService#moveScheduleItems} primitive {@link #moveFolder} already
    * uses, but with a single-entry {@code taskModels[]} and no {@code folders[]}, closing the gap
    * that primitive's own {@code taskModels} argument has sat unused for (bug #76841): the native
    * EM "Move Task" dialog already calls {@code moveScheduleItems} this same way, via {@code
    * EMScheduleTaskFolderController#moveFolder}.
    *
    * <p>Adds two checks {@code moveScheduleItems} itself does NOT perform for a task move, both
    * confirmed by reading it directly rather than assumed:
    *
    * <ul>
    * <li>Per-task permission -- {@code moveScheduleItems} only checks {@code WRITE} on the TARGET
    * folder, never anything on the task itself; the native controller adds that check one layer up,
    * and silently no-ops (returns) rather than throwing on failure. This method reproduces the same
    * check ({@code WRITE} on the task, or {@link ScheduleTaskService#canDeleteTask}) but throws
    * loud instead, matching every other refusal in this gateway.
    * <li>{@code removable()} -- {@code moveScheduleItems}'s own taskModels loop silently {@code
    * continue}s (no exception, no signal) when a task is not removable (a data-cycle-owned internal
    * task), which would make this method appear to succeed while doing nothing. Checked and refused
    * loud here instead.
    * </ul>
    *
    * <p>Whether {@code targetPath} must already exist is enforced by {@link
    * ScheduleFolderChangePlanService#resolveMoveTask} at PLAN time (mirroring {@code resolveMove}'s
    * own {@code folderExists} guard), not here -- {@code moveScheduleItems}'s own task-move helper
    * silently updates the task's own path even when the target folder was never actually created,
    * producing a task that reports a folder it is not registered under; refusing before this method
    * is ever called is the only place that is caught.
    */
   public void moveTask(String taskId, String targetPath, Principal user) throws Exception {
      moveTask(taskId, targetPath, user, new AtomicBoolean());
   }

   /**
    * Same as {@link #moveTask(String, String, Principal)}, but reports via {@code mutationEntered}
    * whether the actual mutating call ({@code taskFolderService.moveScheduleItems}) was reached --
    * this method's own permission/removable checks above throw strictly BEFORE that call, so a
    * caller (bug #76856: {@link inetsoft.web.admin.ai.schedule.ScheduleFolderChangesetApplyService})
    * that needs to distinguish "nothing was ever touched" from "a real partial-mutation risk" cannot
    * do so by observing only whether this method as a whole threw.
    */
   public void moveTask(String taskId, String targetPath, Principal user, AtomicBoolean mutationEntered)
      throws Exception
   {
      ScheduleTask task = scheduleManager.getScheduleTask(taskId);

      if(task == null) {
         throw new MissingResourceException(taskId);
      }

      if(!(securityEngine.checkPermission(
              user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.WRITE) ||
           scheduleTaskService.canDeleteTask(task, user)))
      {
         throw new UnauthorizedAccessException();
      }

      ScheduleTaskModel model = ScheduleTaskModel.builder()
         .fromTask(task, scheduleService, Catalog.getCatalog())
         .build();

      if(!model.removable()) {
         throw new IllegalArgumentException(
            "taskId: \"" + taskId + "\" is not removable (it belongs to a data cycle) and cannot " +
            "be moved into a folder");
      }

      AssetEntry targetEntry = taskFolderService.getFolderEntry(normalizePath(targetPath));
      mutationEntered.set(true);
      taskFolderService.moveScheduleItems(
         new ScheduleTaskModel[]{ model }, new String[0], targetEntry, user);
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
   private final ScheduleManager scheduleManager;
   private final SecurityEngine securityEngine;
   private final ScheduleTaskService scheduleTaskService;
}
