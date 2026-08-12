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

import inetsoft.web.wiz.model.WizDatabaseDefinition;

/**
 * Creates a database inside a folder.
 *
 * <p>The parent folder travels in the body, unlike the update endpoint's {@code path} query
 * parameter, because it does not identify the resource being written — the new database's own path
 * is not known until its name is combined with this folder.</p>
 *
 * @param parentPath the folder to create the database in. Null, empty or {@code "/"} means the root.
 * @param definition the new database.
 */
public record WizDatabaseCreateRequest(String parentPath, WizDatabaseDefinition definition) {
}
