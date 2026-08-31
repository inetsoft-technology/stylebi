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
 *
 *           <p><b>Must not contain a {@code .} character.</b> This id is carried verbatim into
 *           {@code OsiDataset.source} on the wiz side ({@code TabularCatalogService.toDataset}
 *           does {@code dataset.setSource(schema.datasetId())}). wiz's own
 *           {@code bareTableName(source, category)}
 *           ({@code wiz-services/src/v1/services/tabularBinding.ts:68-71}) treats {@code source}
 *           as a qualified name and splits it on {@code "."} for every category except
 *           {@code FILE} — which now includes {@code METADATA} — keeping only the last segment;
 *           its sibling {@code sourceMatches}
 *           ({@code wiz-services/src/services/tableDocResolver.ts:27-33}) does the same
 *           unconditional split with no category parameter at all. That is correct for a JDBC
 *           source, where the qualifier really is there to be stripped, and wrong for an opaque
 *           connector-native id: two ids {@code "A.B"} and {@code "C.B"} would both bare-reduce to
 *           {@code "B"} and be reported as duplicates of each other, and lookups could resolve to
 *           the wrong dataset. A connector whose native identity is composite (e.g. SharePoint's
 *           {@code site → list}, named in this SPI's design doc as the first case expected to need
 *           one) must therefore join its parts with something other than {@code .} — or this
 *           constraint has to be revisited together with making those two wiz-side helpers treat a
 *           METADATA id as opaque instead of qualified, which is the more correct fix and is
 *           deliberately not done as a partial patch here, since {@code sourceMatches} has no
 *           category parameter and fixing only dedup while leaving resolution wrong would be worse
 *           than fixing neither.
 */
public record TabularDatasetRef(String id) {}
