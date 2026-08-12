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

import inetsoft.web.portal.data.DataSourceDefinition;

/**
 * Body of {@code POST /api/wiz/tabular/create}.
 *
 * <p>{@code parentPath} rides beside the definition rather than only inside it because the
 * permission gate needs the target folder <em>before</em> anything is read or written, and a
 * definition that arrived over the wire is caller-supplied data.</p>
 *
 * @param parentPath folder to create the data source in. Empty or null means the repository root.
 * @param definition the data source to create.
 */
public record WizTabularCreateRequest(String parentPath, DataSourceDefinition definition) {
}
