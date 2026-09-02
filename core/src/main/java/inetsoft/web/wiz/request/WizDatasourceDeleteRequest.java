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
 * Deletes one or more data sources and/or data source folders in a single call.
 *
 * <p>Single-item and bulk delete are the same request shape — the portal never has a
 * single-item-only code path to justify a separate route.</p>
 *
 * @param items the data sources and/or folders to delete, one flat list.
 * @param force {@code false} refuses an item whose contents have an outer dependency; {@code true}
 *              deletes it anyway. Matches the native {@code force} parameter's semantics — there is
 *              no separate "non-empty folder" concept.
 */
public record WizDatasourceDeleteRequest(List<WizDatasourceRef> items, boolean force) {
}
