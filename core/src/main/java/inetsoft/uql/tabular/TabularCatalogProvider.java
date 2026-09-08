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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

   /**
    * A filtered, paged view of the same catalog {@link #listDatasets(TabularDataSource)} returns in
    * full -- for a caller that wants to browse or search rather than receive everything.
    *
    * This default implementation calls the full {@link #listDatasets(TabularDataSource)}, filters
    * it in memory by {@code request.nameContains()}, and slices out one page. That is CORRECT for
    * every connector -- the two calls describe the same catalog -- but it is NOT CHEAP: every page,
    * including the first, re-enumerates the whole source. A connector that can push
    * {@code nameContains} and paging down to its own source (a {@code WHERE ... LIKE} clause, a
    * REST API's own cursor/query parameters, ...) should override this method rather than rely on
    * the default -- the smaller the source, the less this matters, which is why no community
    * connector needs to today.
    *
    * <p><b>Order.</b> Paging is only coherent if {@link #listDatasets(TabularDataSource)} returns
    * the same datasets in the same relative order across repeated calls against an unchanged source
    * -- not merely a stable order within one call. Every existing implementer already satisfies this
    * incidentally (a SQL dictionary query, a REST list endpoint, and a metadata document all return
    * a deterministic order in practice); a connector whose native order can vary between two calls
    * (e.g. hash-based iteration) MUST impose its own stable secondary order (such as sorting by
    * {@link TabularDatasetRef#id()}) before returning from {@link #listDatasets(TabularDataSource)}
    * -- there is no separate ordering hook for the paged method to use instead.
    *
    * <p><b>Filtering.</b> {@code nameContains} matches a {@link TabularDatasetRef} id as a
    * case-insensitive substring ({@link Locale#ROOT}, not the platform default locale, so
    * this does not depend on where the JVM runs) -- not a prefix, not a whole-name match. {@code
    * null} or blank means every dataset matches; it never means match nothing.
    *
    * <p><b>Paging.</b> {@code request.cursor()} is opaque; the caller passes back exactly the
    * {@link TabularCatalogPage#nextCursor()} a previous call returned, together with the SAME
    * {@code nameContains} it was minted under. This default implementation's cursor happens to be a
    * decimal offset into the filtered list, but that is an implementation detail an overriding
    * connector is free to ignore -- nothing in this contract lets a caller tell which kind of cursor
    * it is holding, or exploit that if it could. A cursor this default implementation cannot parse
    * throws {@link IllegalArgumentException}; a syntactically valid cursor that no longer lands
    * inside the (possibly re-filtered) list -- because the underlying catalog changed, or the caller
    * mixed up two browsing sessions -- is answered with an empty, exhausted page rather than an
    * exception, the same answer a caller gets from simply over-paging past the real end.
    *
    * <p><b>Stated residual.</b> Because this default implementation re-runs
    * {@link #listDatasets(TabularDataSource)} on every call, its cursor is, structurally, an offset
    * into a freshly recomputed list -- the same shape of weakness an offset-based contract would
    * have had: a dataset added or removed between two page calls can be skipped or repeated. This is
    * not a regression this method introduces; {@link #listDatasets(TabularDataSource)} never
    * promised a snapshot across two separate calls either. What paging through an opaque cursor buys
    * over a literal offset in the CONTRACT is that an overriding connector is not committed to the
    * same weakness: a keyset-style cursor over a source with a stable native ordering (SAP's last
    * TABNAME seen, say) is NOT vulnerable to insertions or deletions elsewhere in the keyspace the
    * way an integer offset is. This default implementation does not need that extra robustness --
    * see the class javadoc's cost argument -- so it does not build it.
    *
    * @param request never null.
    * @return never null. {@link TabularCatalogPage#nextCursor()} is null exactly on the last page.
    * @throws Exception whatever {@link #listDatasets(TabularDataSource)} throws, unchanged, plus
    *         {@link IllegalArgumentException} for an unparseable cursor.
    */
   default TabularCatalogPage listDatasets(TabularDataSource<?> dataSource,
                                           TabularCatalogRequest request) throws Exception
   {
      TabularCatalog catalog = listDatasets(dataSource);
      List<TabularDatasetRef> all = catalog.datasets() == null ? List.of() : catalog.datasets();

      String normalizedNameContains = request.nameContains() == null || request.nameContains().isBlank()
         ? null : request.nameContains().toUpperCase(Locale.ROOT);

      List<TabularDatasetRef> filtered = all.stream()
         .filter(ref -> matchesNameContains(ref, normalizedNameContains))
         .collect(Collectors.toUnmodifiableList());

      int start = decodeCursor(request.cursor());

      if(start < 0 || start >= filtered.size()) {
         // Over-paged, or a cursor from a browsing session whose filter/underlying catalog has
         // since moved on -- answered the same way as genuine exhaustion. See "Paging" above.
         return new TabularCatalogPage(List.of(), null);
      }

      // start + request.limit() as int arithmetic can overflow -- limit has no upper bound, only
      // limit > 0 -- so the addition is done as long before clamping back down to filtered.size().
      long end = Math.min((long) start + request.limit(), filtered.size());
      List<TabularDatasetRef> page = List.copyOf(filtered.subList(start, (int) end));
      String nextCursor = end < filtered.size() ? encodeCursor((int) end) : null;

      return new TabularCatalogPage(page, nextCursor);
   }

   private static boolean matchesNameContains(TabularDatasetRef ref, String normalizedNameContains) {
      if(normalizedNameContains == null) {
         return true;
      }

      return ref.id() != null && ref.id().toUpperCase(Locale.ROOT).contains(normalizedNameContains);
   }

   private static int decodeCursor(String cursor) {
      if(cursor == null) {
         return 0;
      }

      try {
         int index = Integer.parseInt(cursor);

         if(index < 0) {
            throw new NumberFormatException("negative cursor");
         }

         return index;
      }
      catch(NumberFormatException e) {
         throw new IllegalArgumentException(
            "TabularCatalogRequest.cursor '" + cursor + "' was not minted by this default " +
            "TabularCatalogProvider.listDatasets(TabularDataSource, TabularCatalogRequest) and " +
            "cannot be resumed from.", e);
      }
   }

   private static String encodeCursor(int nextStart) {
      return Integer.toString(nextStart);
   }

   /**
    * The relationships the source declares that lie entirely within {@code datasetIds} -- both
    * endpoints. An edge with one endpoint inside {@code datasetIds} and one outside is dropped, not
    * just the outside endpoint; an edge with neither endpoint inside is dropped too. See
    * {@code TabularCatalogService.validateRelationshipEndpoints} -- this method's whole reason to
    * exist both-endpoints-only is that it is what keeps that unchanged validation correct by
    * construction for a caller that annotates a chosen SUBSET of a source's datasets rather than the
    * whole catalog: a relationship into a dataset the caller never selected has no home to attach to.
    *
    * This default implementation calls the full {@link #listDatasets(TabularDataSource)} and
    * filters its relationships -- correct, and no more expensive than the paging default above, but
    * for a connector whose relationships can ONLY be produced by describing every dataset one at a
    * time (Salesforce's {@code describeGlobal} is the motivating case: today it has to
    * describe every sobject just to report the lookup fields between them), overriding this
    * method to describe only {@code datasetIds} is a real cost win that overriding the paging method
    * above is not.
    *
    * <p>No directional promise: some connectors can report an edge cheaply when it points OUT of a
    * dataset in {@code datasetIds} (a lookup field is visible on the referencing object's own
    * description) but not when it points IN from outside; this contract only promises "the edges the
    * source declares with both endpoints in {@code datasetIds}", not that both directions cost the
    * same to discover.
    *
    * @param datasetIds the caller's chosen subset. A null or empty collection is treated
    *                    identically -- both yield an empty relationship list, never every edge and
    *                    never an exception.
    * @return never null; empty when the source declares no relationship satisfying the
    *         both-endpoints rule, or none at all.
    */
   default List<TabularRelationship> listRelationships(TabularDataSource<?> dataSource,
                                                        Collection<String> datasetIds) throws Exception
   {
      if(datasetIds == null || datasetIds.isEmpty()) {
         return List.of();
      }

      TabularCatalog catalog = listDatasets(dataSource);
      List<TabularRelationship> relationships = catalog.relationships();

      if(relationships == null || relationships.isEmpty()) {
         return List.of();
      }

      Set<String> ids = datasetIds instanceof Set<String> set ? set : new HashSet<>(datasetIds);

      return relationships.stream()
         .filter(rel -> rel != null && ids.contains(rel.fromDataset()) && ids.contains(rel.toDataset()))
         .collect(Collectors.toUnmodifiableList());
   }
}
