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
 * (names only), not $metadata — routing this SPI through that path would silently truncate or
 * under-describe a catalog that this interface's own contract requires to be complete.
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
    *         Note that an empty result is NOT a way to signal success at annotating nothing:
    *         {@code TabularCatalogService.listTables}, the sole production caller, rejects an
    *         empty catalog with a named "no datasets to annotate" error rather than completing the
    *         annotation run having done no work — the same reasoning wiz's own
    *         {@code handelAnnotateDatabase} applies to an empty JDBC table list. Returning empty is
    *         legal from this interface's point of view; it is answered with an error one layer up.
    * @throws Exception if the catalog could not be read. Throwing is REQUIRED over returning an
    *         empty catalog on failure — an empty result is indistinguishable from success and
    *         produces an annotation run that looks complete and is not.
    */
   TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception;

   /**
    * The columns this connector can report for one dataset.
    *
    * A connector that can only infer columns from a bounded scan or a single response — rather
    * than reading them from declared, source-published metadata — is a legitimate implementer of
    * this method. Sampled or inferred columns satisfy this contract.
    *
    * What such a connector must NOT do is present that list as if it were authoritative. The
    * imprecision belongs at the DATASET level, not the column level: every column returned by one
    * {@code describeDataset} call comes from the same scan and therefore shares the same trust
    * level, so a per-column marker would just repeat one dataset-wide fact on every row. Record it
    * once, on the dataset.
    *
    * That dataset-level "this list may be incomplete" signal is {@link TabularDatasetSchema#columnsMayBeIncomplete()}.
    * Set it true when this method could only infer columns from a bounded scan rather than read
    * them from the source's own declared metadata — {@code AerospikeCatalog} is the first
    * implementer to do so. wiz surfaces a true value as one caveat sentence composed into the
    * annotation prompt, the same way a truncated-sample-rows caveat is already surfaced today, so
    * an LLM reading this data does not describe an inferred column list as a complete enumeration.
    *
    * Before reaching for sampling, check whether the source already publishes declared metadata —
    * if it does, read that instead of inferring from samples. This is not hypothetical: a
    * REST source can carry its column list in the very same response body as the data (e.g. a
    * `meta.view.columns` field alongside a `data` array); a spreadsheet can declare columns via
    * its header row and per-column cell-format metadata; a document store can expose a mapping
    * API describing its fields (e.g. Elasticsearch's `_mapping`). Falling back to sampling when a
    * declaration exists manufactures uncertainty the source did not actually have.
    *
    * @param datasetId a {@link TabularDatasetRef#id()} previously returned by
    *                  {@link #listDatasets} for this same data source.
    * @return never {@code null}, and never with an empty column list — throw instead.
    * @throws Exception if the dataset is unknown, or its columns could not be read.
    */
   TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception;
}
