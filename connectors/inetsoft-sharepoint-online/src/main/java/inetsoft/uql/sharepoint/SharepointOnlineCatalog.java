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
