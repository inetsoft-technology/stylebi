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

/**
 * One dataset a tabular data source holds.
 *
 * @param id the connector's own handle for this dataset, opaque to core. It is the ONLY token that
 *           travels: the caller hands it back verbatim to
 *           {@link TabularCatalogProvider#describeDataset}, and it is what identifies the dataset
 *           in every {@link TabularRelationship} of the same catalog. Must be non-blank and unique
 *           within one catalog, and must remain stable across calls for an unchanged source.
 */
public record TabularDatasetRef(String id) {}
