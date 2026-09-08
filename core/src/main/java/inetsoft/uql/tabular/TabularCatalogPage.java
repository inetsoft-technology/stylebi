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
package inetsoft.uql.tabular;

import java.util.List;

/**
 * One page of {@link TabularDatasetRef}s, in answer to
 * {@link TabularCatalogProvider#listDatasets(TabularDataSource, TabularCatalogRequest)}.
 *
 * @param datasets   this page's datasets. Never null; may be empty. In a stable relative order.
 * @param nextCursor the cursor to pass back as {@link TabularCatalogRequest#cursor()} to fetch the
 *                   following page. {@code null} means this was the LAST page. Opaque.
 */
public record TabularCatalogPage(List<TabularDatasetRef> datasets, String nextCursor) {}
