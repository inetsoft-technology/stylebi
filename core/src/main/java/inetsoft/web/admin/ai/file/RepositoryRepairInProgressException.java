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
package inetsoft.web.admin.ai.file;

/**
 * Thrown by {@link AdminRepositoryMaintenanceController#repairRepositoryFolders} when a
 * {@code repair_repository_folders} kick-off this controller itself already issued is still
 * outstanding. {@code FileService.repairRepositoryFolders} has no lock/dedup of its own -- unlike
 * {@code rebuildDependencies}'s real cluster-wide lock -- so this guard is enforced entirely in
 * this wrapper, before the wrapped method is ever called.
 */
public class RepositoryRepairInProgressException extends RuntimeException {
   public RepositoryRepairInProgressException(String outstandingToken) {
      super(outstandingToken == null
         ? "A repository folder repair is already starting. Check its status or wait for it to " +
            "complete before starting another."
         : "A repository folder repair (token " + outstandingToken + ") is already in " +
            "progress. Check its status or wait for it to complete before starting another.");
   }
}
