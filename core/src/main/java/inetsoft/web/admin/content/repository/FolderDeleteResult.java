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
package inetsoft.web.admin.content.repository;

import inetsoft.web.admin.security.ConnectionStatus;

import java.util.List;

/**
 * The outcome of {@link RepositoryObjectService#removeDataSourceFolder}.
 *
 * Exactly one of the two is meaningful: a non-null {@code status} means the deletion did not
 * happen at all (permission denied, or a dependency conflict without {@code force}) and
 * {@code deletedDataSources} is always empty; a null {@code status} means every check passed and
 * the folder plus everything in {@code deletedDataSources} was actually removed.
 */
public record FolderDeleteResult(ConnectionStatus status, List<DeletedDataSource> deletedDataSources) {
   public static FolderDeleteResult failure(ConnectionStatus status) {
      return new FolderDeleteResult(status, List.of());
   }

   public static FolderDeleteResult success(List<DeletedDataSource> deletedDataSources) {
      return new FolderDeleteResult(null, deletedDataSources);
   }
}
