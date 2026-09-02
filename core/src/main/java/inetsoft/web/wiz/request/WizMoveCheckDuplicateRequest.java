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

import java.util.List;

/**
 * Checks whether moving the given items to {@code targetPath} would collide with an existing name.
 *
 * <p>Advisory only, like the native {@code checkItemsDuplicate} endpoint it forwards to: the actual
 * move still re-validates. No permission check applies to this endpoint, for the same reason.</p>
 *
 * @param items      the data sources and/or folders that would be moved.
 * @param targetPath the destination folder. Null, empty or {@code "/"} means the root.
 */
public record WizMoveCheckDuplicateRequest(List<WizDatasourceRef> items, String targetPath) {
}
