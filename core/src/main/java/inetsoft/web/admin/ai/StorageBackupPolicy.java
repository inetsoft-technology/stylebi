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
package inetsoft.web.admin.ai;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * The single place that decides whether an admin change plan needs the whole-storage Tier-2
 * snapshot ({@link ResolvedPlan#requiresStorageBackup()}).
 *
 * <p>A snapshot is a full export of every key-value and blob store with no restore method, so it
 * is only worth paying for when the plan can alter something that already exists and the live
 * rollback cannot put it back exactly. A change has an <em>exact undo</em> when the in-process
 * rollback restores everything a reader of that object can observe (a pure create whose undo
 * deletes the new object, a setting whose captured pre-image is written back, an upload or delete
 * whose prior bytes are captured at resolve time). A plan skips the snapshot only when EVERY change
 * in it has an exact undo: one change without it (a rename that rewrites dependents, a sub-model
 * whose rollback leaves residue, a whole-file rewrite with no rollback) keeps the snapshot for the
 * whole plan (Bug #80650, Bug #81072).
 *
 * <p>The decision is made from live state at resolve time and carried in the hashed plan, so a
 * change between preview and apply returns a conflict instead of a silent disagreement.
 *
 * <p>Accepted drift, deliberately not treated as a reason to keep the snapshot: a timestamp the
 * undo does not restore (task lastModified, script and shape metadata), and an unset property that
 * the undo leaves pinned to its static default (performance, email, presentation global scope, and
 * an unset {@code replet.cache.clean}, which is pinned to "true"). These are observable only if a
 * product upgrade changes a static default.
 *
 * <p>Skipping the snapshot removes recovery from a crash between write and rollback, from a
 * rollback that itself fails, and from regret after a successful apply; the apply result and the
 * audit trail still name every entry left changed.
 *
 * <p>Plan services use {@link #requiresBackup} to fill the plan flag, and every apply service
 * gates {@code backupService.backup} on the same decision, so preview and apply cannot disagree.
 */
public final class StorageBackupPolicy {
   private StorageBackupPolicy() {
   }

   /**
    * @param changes      every change of the plan.
    * @param hasExactUndo true when the live rollback of the change restores everything a reader
    *                     can observe.
    *
    * @return false only when the plan is non-empty and every change has an exact undo.
    */
   public static <T> boolean requiresBackup(Collection<T> changes, Predicate<T> hasExactUndo) {
      if(changes == null || changes.isEmpty()) {
         return true;
      }

      return !changes.stream().allMatch(hasExactUndo);
   }
}
