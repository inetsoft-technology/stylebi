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
 * Creates a tabular data source inside a folder.
 *
 * <p>Shaped like {@code WizDatabaseCreateRequest} and for the same reason: the parent folder travels
 * in the body because it does not identify the resource being written, whereas the update endpoint's
 * {@code path} query parameter does. The asymmetry between the two bodies is deliberate.</p>
 *
 * @param parentPath the folder to create the data source in. Null, empty or {@code "/"} means the
 *                   root. Overwrites whatever the definition carries — the definition's own
 *                   {@code parentPath} is never trusted, since the permission check is made against
 *                   this field.
 * @param definition the new data source, passed through to StyleBI verbatim. Unlike the JDBC
 *                   contract, nothing here is reduced or rewritten: the {@code tabularView} tree
 *                   <em>is</em> the form the user filled in, and every editor's current value lives
 *                   inside it.
 */
public record WizTabularCreateRequest(String parentPath, DataSourceDefinition definition) {
}
