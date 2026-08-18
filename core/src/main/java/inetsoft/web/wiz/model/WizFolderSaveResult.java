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
package inetsoft.web.wiz.model;

/**
 * The outcome of creating a data source folder.
 *
 * <p>Deliberately the same shape as {@link WizDatabaseSaveResult} and {@link WizTabularSaveResult},
 * reached differently again: {@code addDatasourceFolder} has no business-failure return of its own,
 * so the controller checks {@code checkFolderDuplicate} itself before calling it and reports that
 * as the one failure this operation can have.</p>
 *
 * @param ok     whether the folder was created.
 * @param reason null when {@code ok}. Otherwise {@code DUPLICATE_NAME} — a folder or data source of
 *               that name already exists in the parent — the only business failure this operation
 *               can report; anything else surfaces as an HTTP error instead.
 * @param path   the new folder's full repository path. Null on failure.
 */
public record WizFolderSaveResult(boolean ok, String reason, String path) {
   public static WizFolderSaveResult ok(String path) {
      return new WizFolderSaveResult(true, null, path);
   }

   public static WizFolderSaveResult failed(String reason) {
      return new WizFolderSaveResult(false, reason, null);
   }

   public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
}
