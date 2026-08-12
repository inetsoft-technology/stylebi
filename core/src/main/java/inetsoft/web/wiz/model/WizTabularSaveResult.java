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
 * The outcome of creating or updating a tabular data source.
 *
 * <p>The same shape {@code WizDatabaseSaveResult} has, for the same reason, but reached differently:
 * the JDBC save path reports a business failure as a status string inside a successful call, while
 * the tabular save path throws {@code MessageException} carrying a {@code Catalog}-translated
 * sentence. Either way the client branches on {@code reason} and writes its own message; it must
 * never see or compare the server's wording.</p>
 *
 * @param ok     whether the data source was saved.
 * @param reason null when {@code ok}. Otherwise {@code DUPLICATE_NAME} (a data source already exists
 *               at that path), {@code INVALID_FOLDER} (the parent folder does not exist),
 *               {@code DATASOURCE_LOST} (the data source being updated is no longer there), or
 *               {@code UNKNOWN} — the last meaning StyleBI rejected the save for a reason this
 *               mapping does not recognize, most often an invalid name. The server's own wording is
 *               logged rather than swallowed, so it stays recoverable for anyone diagnosing it.
 * @param path   the saved data source's full repository path, which differs from the requested one
 *               when the connection was renamed. Null on failure.
 */
public record WizTabularSaveResult(boolean ok, String reason, String path) {
}
