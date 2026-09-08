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
 * A tabular data source's whole catalog.
 *
 * @param datasets      every dataset in the source; never null, in the source's own order. This
 *                      order MUST be stable across repeated calls against an unchanged source --
 *                      not merely stable within one call -- so that
 *                      {@link TabularCatalogProvider}'s default paging (and any override built the
 *                      same way) does not skip or repeat a dataset while a caller pages through an
 *                      unchanged catalog. A connector whose native order can vary between two calls
 *                      (e.g. hash-based iteration) MUST impose its own stable secondary order (such
 *                      as sorting by {@link TabularDatasetRef#id()}) before returning -- there is no
 *                      separate ordering hook for the paged method to use instead.
 * @param relationships edges the source declares between datasets in {@code datasets}. Never null;
 *                      empty when the source declares none, or declares none this SPI can express.
 *                      Every fromDataset/toDataset MUST be an id in {@code datasets}.
 */
public record TabularCatalog(List<TabularDatasetRef> datasets,
                             List<TabularRelationship> relationships) {}
