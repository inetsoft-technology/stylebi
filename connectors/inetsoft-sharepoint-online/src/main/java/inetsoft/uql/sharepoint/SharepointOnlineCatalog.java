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
package inetsoft.uql.sharepoint;

import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Assembles {@link SharepointOnlineRuntime}'s {@link TabularCatalogProvider} answers out of the
 * connector's own Graph calls. Mirrors {@code ODataCatalog}'s role: package-private, static
 * methods, no state of its own.
 *
 * No caching (unlike OData's {@code ODataCatalogCache}): OData caches because one {@code $metadata}
 * document answers both SPI phases for every entity set at once; SharePoint has no analogous
 * document — {@code listDatasets} is inherently O(sites) Graph calls with nothing shared across
 * sites, and {@code describeDataset} is exactly one Graph call for the one list asked about, with
 * nothing shared between two different calls. A cache here would have nothing to save.
 *
 * No relationships this round: SharePoint lookup columns carry the target list id and a display
 * column, not a pair of column names whose values are meant to be equal the way OData's referential
 * constraints do. The closest candidate — pairing a lookup column against the target list's system
 * "ID" field — is not safe to emit, because {@code doGetListColumns}'s type-mapping switch has no
 * branch for that column shape, so "ID" is not expected to appear in any {@code describeDataset}
 * result [ASSUME — unverified against a real Graph {@code columns()} response]. A relationship
 * whose {@code toColumns} names a column absent from the target's own reported schema would be a
 * permanently unresolvable declared edge, worse than no edge at all. Honest-drop, same rule OData's
 * own design already established: when no candidate pairing can be verified, drop it and say so.
 *
 * <p><b>{@code describeDataset} does NOT validate that {@code datasetId} was one this same data
 * source's own {@code listDatasets} actually enumerated</b> — a real, deliberate gap, not an
 * oversight (raised in P5 review r1). {@link SharepointDatasetId#parse} rejects anything that could
 * not possibly have come out of {@link SharepointDatasetId#compose} (blank site/list, or a string
 * that doesn't round-trip), but a caller-supplied id whose (site, list) shape is well-formed and
 * simply never appeared in this data source's own catalog is not rejected before its {@code site}/
 * {@code list} values reach a live {@code client.sites(site).lists(list).columns()} Graph call.
 * Unlike OData's {@code ODataRuntime.describeDataset}, which resolves against a bounded snapshot
 * built by that same source's own prior {@code listDatasets}/{@code $metadata} fetch (so an
 * unrecognized id can only ever answer "not found"), SharePoint has no analogous snapshot to check
 * against: full membership validation would require enumerating every site's lists first — which
 * IS {@code listDatasets} itself, the exact O(sites) cost this class deliberately does not cache
 * (see above). Doing that enumeration on every {@code describeDataset} call to validate one id
 * would reverse that no-cache decision for a marginal gain.
 *
 * <p>This is NOT a privilege-escalation risk — but the reason is a stronger, more directly
 * verifiable one than "the dropdown reaches the same credential" (P5 review r2 corrected this):
 * {@code SharepointOnlineQuery.setSite}/{@code setList} are plain, unvalidated {@code String}
 * setters, and {@code @PropertyEditor(tagsMethod = "getSites"/"getLists")} only populates a UI
 * dropdown — it enforces nothing server-side. So anyone who already holds whatever permission lets
 * them author and run a query against this data source can point {@code runQuery} at ANY
 * {@code (site, list)} string the connector's credential can reach, with no dependency on
 * {@code listDatasets}/the dropdown ever having enumerated it first — and that path returns actual
 * row data, not just a column schema. {@code describeDataset}'s unvalidated {@code datasetId}
 * therefore grants strictly LESS than a capability that already exists, unmediated, elsewhere in
 * this same connector.
 *
 * <p>The dropdown specifically cannot be cited as the reachability ceiling: its enumeration walks
 * the same site tree {@code listSitesOrThrow} does (root, its subsites, each group's root site and
 * subsites), so a site reachable only through a permission grant outside that tree — e.g. a Graph
 * {@code Sites.Selected} grant on a site not linked under root or under any group — is invisible to
 * both the dropdown and {@code listDatasets}, yet still reachable by a raw
 * {@code client.sites(id)} call under the identical credential. Citing "the dropdown already
 * reaches it" as the safety argument would therefore be false in exactly the case that matters;
 * citing the already-unmediated {@code runQuery} path does not have that hole.
 *
 * <p>The caller must also already hold READ on this data source ({@code MetadataApiService} checks
 * that before either {@code listTables} or {@code describeTable} is reached). Full membership
 * validation (and the cache it would require) is a candidate for a future round if this
 * connector's enumeration cost ever needs amortizing for other reasons.
 */
final class SharepointOnlineCatalog {
   private SharepointOnlineCatalog() {
   }

   static TabularCatalog listDatasets(SharepointOnlineDataSource ds) throws Exception {
      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(SharepointOnlineRuntime.SiteRef site : SharepointOnlineRuntime.listSitesOrThrow(ds)) {
         for(String[] list : SharepointOnlineRuntime.getLists(ds, site.id())) {
            datasets.add(new TabularDatasetRef(SharepointDatasetId.compose(site.id(), list[1])));
         }
      }

      // No relationships this round — see the class javadoc.
      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(SharepointOnlineDataSource ds, String datasetId)
      throws Exception
   {
      SharepointDatasetId.Parsed parsed = SharepointDatasetId.parse(datasetId);
      XTypeNode[] columns =
         SharepointOnlineRuntime.getListColumns(ds, parsed.site(), parsed.list());

      return new TabularDatasetSchema(datasetId,
         Arrays.stream(columns).map(n -> new TabularColumn(n.getName(), n.getType())).toList(),
         List.of());       // keyColumns — no system key column surfaces through getListColumns
   }
}
