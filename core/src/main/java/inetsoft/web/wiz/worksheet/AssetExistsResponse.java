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
package inetsoft.web.wiz.worksheet;

/**
 * Response body for {@link WorksheetAgentController#assetExists}.
 *
 * @param exists    whether ANY asset of a type {@link inetsoft.uql.asset.internal.AssetUtil
 *                  #isDuplicatedEntry} treats as colliding with the requested type exists at
 *                  the given path/scope -- unchanged from before {@code worksheet}/{@code folder}
 *                  were added, so an older caller reading only this field keeps seeing the same
 *                  answer it always did.
 * @param worksheet whether a WORKSHEET entry specifically exists at the given path/scope. Only
 *                  meaningful when the request's {@code type} was {@code WORKSHEET} or
 *                  {@code FOLDER} (the default, and what {@code add_table}'s no-source form
 *                  checks) -- {@code false} for any other requested type, since those types
 *                  never collide with a worksheet/folder pair in the first place.
 * @param folder    whether a worksheet-tree FOLDER entry specifically exists at the given
 *                  path/scope. Same type restriction as {@code worksheet} above.
 */
public record AssetExistsResponse(boolean exists, boolean worksheet, boolean folder) {
}
