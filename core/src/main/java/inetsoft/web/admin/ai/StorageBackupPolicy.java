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
 * is only worth paying for when the plan can overwrite, delete or otherwise alter something that
 * already exists. A <em>pure create</em> makes a brand-new object, overwrites nothing, touches no
 * other record, and has a live undo that deletes it; the snapshot adds nothing the undo does not
 * already give. A plan skips the snapshot only when EVERY change in it is a pure create: a plan
 * that mixes a create with a delete/update/overwrite keeps the snapshot (Bug #80650).
 *
 * <p>Plan services use {@link #requiresBackup} to fill the plan flag, and the apply services that
 * re-resolve entries themselves (stored assets, shapes) gate {@code backupService.backup} on the
 * same decision, so preview and apply cannot disagree.
 */
public final class StorageBackupPolicy {
   private StorageBackupPolicy() {
   }

   /**
    * @param changes      every change of the plan.
    * @param isPureCreate true when the change creates a new object that did not exist, with a live
    *                     undo and no side effect on another record.
    *
    * @return false only when the plan is non-empty and every change is a pure create.
    */
   public static <T> boolean requiresBackup(Collection<T> changes, Predicate<T> isPureCreate) {
      if(changes == null || changes.isEmpty()) {
         return true;
      }

      return !changes.stream().allMatch(isPureCreate);
   }
}
