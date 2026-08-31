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
 * The columns of one dataset.
 *
 * @param datasetId echoes the id that was asked for, so a result is self-identifying.
 * @param columns   non-empty and in source order. An implementation that finds no columns throws
 *                  rather than returning an empty list — see TabularCatalogProvider.
 * @param keyColumns names, from {@code columns}, that the source declares as this dataset's key;
 *                  empty when the source declares none. Never null.
 */
public record TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                                   List<String> keyColumns) {}
