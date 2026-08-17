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

package inetsoft.web.wiz.request;

/**
 * Creates a data source folder inside another folder.
 *
 * <p>The parent folder travels in the body, the same choice {@link WizDatabaseCreateRequest} makes
 * and for the same reason: the new folder's own path is not known until its name is combined with
 * this one.</p>
 *
 * @param parentPath the folder to create the new folder in. Null, empty or {@code "/"} means the
 *                   root.
 * @param name       the new folder's name — a single path segment, not a path.
 */
public record WizFolderCreateRequest(String parentPath, String name) {
}
