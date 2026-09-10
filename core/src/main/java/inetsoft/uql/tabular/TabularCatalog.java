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
 * @param truncated     true when the enumeration behind {@code datasets} stopped at an internal
 *                      bound (an entry cap, a request-count cap) rather than exhausting the source.
 *                      {@code datasets} is then a genuine, usable PREFIX -- never a signal to
 *                      discard it -- but callers of the unpaged {@link TabularCatalogProvider}
 *                      caller ({@code TabularCatalogService#listTables(String)}) must not treat it
 *                      as the whole source. Default {@code false} via the 2-arg compatibility
 *                      constructor below: every connector that reads a bounded, fully-enumerable
 *                      source (a JDBC-shaped catalog, an EDMX document) never truncates, so "this is
 *                      everything" is the true fact about them, not a placeholder. Added for the
 *                      OneDrive catalog SPI round: {@code BrowsableQuery#browseChildren}'s own
 *                      {@code maxEntries}/internal-request-count bound has no field to report through
 *                      once a connector enumerates through this SPI instead of the file pipeline's
 *                      own {@code enumerateTabularFileTargets}, which this replaces for such a
 *                      connector and which already carried an equivalent {@code truncated} flag on
 *                      its own {@code EnumeratedTabularFiles} result.
 */
public record TabularCatalog(List<TabularDatasetRef> datasets,
                             List<TabularRelationship> relationships, boolean truncated) {
   /**
    * Compatibility constructor for callers written before {@code truncated} existed -- every
    * existing connector's construction site (Aerospike, Cassandra, Datagov, Elasticsearch,
    * GoogleSheets, Hive, MongoDB, OData, OrientDB, GraphQL, ServerFile, SharePoint Online, ...).
    * Defaults to {@code false}: none of them enumerate through a bounded walk that can stop short --
    * they read a fully-described catalog (a driver's metadata call, a JDBC-shaped listing) in one
    * shot, so "not truncated" is the true fact about them, not just a placeholder.
    */
   public TabularCatalog(List<TabularDatasetRef> datasets, List<TabularRelationship> relationships) {
      this(datasets, relationships, false);
   }
}
