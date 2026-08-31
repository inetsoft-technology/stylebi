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
 * A tabular connector's description of its own catalog: which datasets a data source holds, and
 * what one dataset looks like.
 *
 * Implemented by a connector's {@link TabularRuntime} — the catalog is a property of the DATA
 * SOURCE, not of any one query, and the runtime is the connector's existing data-source-level
 * entry point (runQuery/testDataSource/getMetaData all live there).
 *
 * WHAT THIS IS NOT. It is not a way to build a query, and it must not be implemented by delegating
 * to anything that is. In particular it is not {@code TabularQueryParamsSchemaBuilder} /
 * {@code TabularQuerySchema} / a {@code @PropertyEditor(tagsMethod=...)} enumeration: those exist to
 * tell an LLM how to FILL a query's parameters, they carry budgets set for a tool call
 * (a 200-candidate cap, a 5s timeout), and for OData the tags path reaches the service document
 * (names only), not $metadata. See
 * docs/teams/2026-08-28-tabular-metadata-annotation-entry/09-design-defect-schema-builder-misuse.md.
 *
 * An implementation answers from the connector's OWN native metadata endpoint, and every
 * connector-specific representation (an EDMX Document, a table dictionary row, ...) stays inside
 * the connector's module. Only the neutral types below cross this boundary.
 *
 * Implementations must be safe to call concurrently and must not mutate the passed data source
 * beyond what a normal connection already does.
 */
public interface TabularCatalogProvider {
   /**
    * Every dataset in this data source that can be described, plus the relationships the source
    * itself declares between them.
    *
    * No limit and no paging: the caller lists everything and filters. A source with more datasets
    * than a caller wants is the caller's problem to solve, not a reason for this method to
    * truncate silently.
    *
    * @return never {@code null}; may be empty only when the source genuinely holds no datasets.
    * @throws Exception if the catalog could not be read. Throwing is REQUIRED over returning an
    *         empty catalog on failure — an empty result is indistinguishable from success and
    *         produces an annotation run that looks complete and is not.
    */
   TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception;

   /**
    * The columns of one dataset.
    *
    * @param datasetId a {@link TabularDatasetRef#id()} previously returned by
    *                  {@link #listDatasets} for this same data source.
    * @return never {@code null}, and never with an empty column list — throw instead.
    * @throws Exception if the dataset is unknown, or its columns could not be read.
    */
   TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception;
}
