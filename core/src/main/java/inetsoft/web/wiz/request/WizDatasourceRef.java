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
 * One data source or data source folder targeted by a delete, move or dependency-check request.
 *
 * <p>Deliberately the same shape {@code WizDatasourceEntry} already gives the portal (path/name/
 * folder), so the portal can build a request directly from the rows it already has selected,
 * without any extra lookup.</p>
 *
 * @param path   the full repository path.
 * @param name   the last path segment; needed by
 *               {@code DatabaseDatasourcesService.getDataSourceAuditPath}.
 * @param folder discriminator: {@code true} routes to the folder delete/check path, {@code false}
 *               to the data source path.
 */
public record WizDatasourceRef(String path, String name, boolean folder) {
}
