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

import java.util.List;

/**
 * The outcome of a {@code /datasources/delete} call — one entry per requested item, in request
 * order, whether or not the item was actually deleted. A partial failure in a bulk delete is
 * reported here, not thrown, so the portal can show exactly which items succeeded.
 *
 * @param results one outcome per requested item.
 */
public record WizDatasourceDeleteResult(List<WizDatasourceDeleteItemResult> results) {
}
