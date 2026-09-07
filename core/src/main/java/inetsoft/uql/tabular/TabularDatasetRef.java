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
 * @param id the connector's own handle for this dataset, fully opaque to core. It is the ONLY
 *           token that travels: the caller hands it back verbatim to
 *           {@link TabularCatalogProvider#describeDataset}, and it is what identifies the dataset
 *           in every {@link TabularRelationship} of the same catalog. Must be non-blank and unique
 *           within one catalog, and must remain stable across calls for an unchanged source.
 *
 *           <p><b>May contain a {@code .} character.</b> A connector-native id is not a qualified
 *           name, and a dot in it is not special — an Elasticsearch ILM/rollover index name
 *           ({@code logs-2026.09.04}) is one ordinary example. This id is carried verbatim into
 *           {@code OsiDataset.source} on the wiz side ({@code TabularCatalogService.toDataset}
 *           does {@code dataset.setSource(schema.datasetId())}), and wiz treats a METADATA
 *           source as opaque end to end: {@code bareTableName}/{@code sourceMatches}
 *           (wiz's {@code tabularBinding.ts}/{@code tableDocResolver.ts}) no longer split a
 *           METADATA {@code source} on {@code "."} the way they still do for a JDBC source, whose
 *           id genuinely is a qualified name meant to have its qualifier stripped.
 *
 *           <p>This used to be a hard constraint — "must not contain a {@code .} character" — and
 *           {@code TabularCatalogService} enforced it by throwing, aborting an entire catalog for
 *           any single dotted id. That was a producer-side guard against a consumer that could not
 *           yet tell an opaque id from a qualified one; the consumer has since been fixed
 *           directly (wiz's opaque-id handling above), so the guard was removed rather than kept
 *           as a redundant, and now wrong, restriction on every connector. A connector whose
 *           native identity is composite (e.g. SharePoint's {@code site → list}) may still choose
 *           to join its parts with a separator other than {@code .} — nothing requires a dot — but
 *           nothing in the SPI forbids one either.
 */
public record TabularDatasetRef(String id) {}
