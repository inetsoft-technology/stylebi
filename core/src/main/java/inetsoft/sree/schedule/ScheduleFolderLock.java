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
package inetsoft.sree.schedule;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

/**
 * Cluster-wide lock that serializes the read-modify-write of the schedule task folder blobs of
 * an organization (Bug #81905).
 * <p>
 * The tasks of a folder are the entries of one stored {@code AssetFolder}, which is read, changed
 * and written back as a whole. The monitor of {@code ScheduleManager} only serializes the writers
 * of one node, so writers on different nodes lost each other's entries. There is one lock per
 * organization, not per folder, so that a move that changes two folders needs no lock ordering.
 * <p>
 * The lock is re-entrant. It must be the innermost lock: nothing run under it may take the
 * {@code ScheduleManager} monitor, the extension lock or a task lock, so the sections must only
 * read and write the folder blobs.
 */
public final class ScheduleFolderLock {
   private ScheduleFolderLock() {
   }

   /**
    * Acquires the folder lock of the organization. The caller must unlock it in a finally block.
    *
    * @param orgID the organization of the folders, null for the current organization.
    */
   public static Lock acquire(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      Lock lock = Cluster.getInstance().getLock(LOCK_PREFIX + orgID);
      lock.lock();
      return lock;
   }

   /**
    * Runs the action holding the folder lock of the organization.
    *
    * @param orgID  the organization of the folders, null for the current organization.
    * @param action the read-modify-write of the folder blobs.
    */
   public static <T> T call(String orgID, Callable<T> action) throws Exception {
      Lock lock = acquire(orgID);

      try {
         return action.call();
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * An action that may throw.
    */
   @FunctionalInterface
   public interface Action {
      void run() throws Exception;
   }

   /**
    * Runs the action holding the folder lock of the organization.
    */
   public static void run(String orgID, Action action) throws Exception {
      call(orgID, () -> {
         action.run();
         return null;
      });
   }

   private static final String LOCK_PREFIX = "inetsoft.sree.schedule.ScheduleFolderLock:";
}
