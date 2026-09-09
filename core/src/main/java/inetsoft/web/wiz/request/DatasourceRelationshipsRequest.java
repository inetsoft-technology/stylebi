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
 * Body of {@code POST /datasource/relationships}: the caller's already-chosen subset of one
 * tabular data source's datasets, whose declared relationships are wanted.
 *
 * @param dsPath     the datasource name/path, same slot {@code GetDatabaseTableMetaRequest.dsName}
 *                    and {@code /datasource/tables}'s {@code dsPath} query param use.
 * @param datasetIds the caller's chosen {@link inetsoft.uql.tabular.TabularDatasetRef#id()} subset.
 *                    {@code null} or empty is legal -- see
 *                    {@link inetsoft.uql.tabular.TabularCatalogProvider#listRelationships} -- and
 *                    answers with an empty relationships list, not an error. A POST body (not a GET
 *                    query string) because the id list can be large and ids are opaque, possibly
 *                    containing {@code .}/{@code /} -- a query string handles that badly (charter Q3).
 */
public record DatasourceRelationshipsRequest(String dsPath, List<String> datasetIds) {
}
