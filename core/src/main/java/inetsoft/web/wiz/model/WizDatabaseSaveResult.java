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
 * The outcome of creating or updating a database.
 *
 * <p>StyleBI reports a business failure here as a bare English status string inside an HTTP 200, so
 * the controller maps that string onto the stable {@code reason} codes below. The client branches on
 * the code and writes its own message; it must never compare the underlying string.</p>
 *
 * @param ok     whether the database was saved.
 * @param reason null when {@code ok}. Otherwise one of {@code DUPLICATE_NAME} (a data source of that
 *               name already exists), {@code DUPLICATE_FOLDER} (a <em>folder</em> of that name
 *               already exists), {@code DATASOURCE_LOST} (the database being updated is no longer
 *               there), {@code INVALID_FOLDER} (the parent folder does not exist), or
 *               {@code UNKNOWN} — the last meaning StyleBI reported a status this mapping does not
 *               recognize, which is logged verbatim server-side rather than swallowed.
 * @param path   the saved database's full repository path, which differs from the requested path
 *               when the connection was renamed. Null on failure.
 */
public record WizDatabaseSaveResult(boolean ok, String reason, String path) {
}
