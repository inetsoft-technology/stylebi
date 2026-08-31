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

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteRequestBuilder;
import inetsoft.uql.tabular.TabularCatalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static inetsoft.uql.sharepoint.SharepointGraphTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Covers charter assertion S4 — "本轮最重要的一条" — and the reverse assertion that
 * {@code listDatasets} must never return a partial/empty catalog on a permission or transport
 * failure. This is the assertion the existing {@code doGetSites}/{@code getChildSites} degrade-
 * and-warn behavior structurally cannot satisfy, which is exactly why
 * {@link SharepointOnlineRuntime#listSitesOrThrow} is new code that never calls them.
 *
 * S4b is the case most likely to give a false pass: root succeeding means the catalog is
 * already non-empty by the time groups() fails, so a naive implementation could let the root
 * site's data through as a "successful" (but silently partial) result. Every case here asserts
 * the specific exception thrown, not merely "something was thrown" — and S4d proves the fix
 * didn't overcorrect into treating a legitimately empty list as a failure too.
 */
@Tag("connector")
class SharepointOnlineCatalogPermissionFailureTest {

   @Test
   void listDatasets_rootSiteFetchThrows_propagatesRatherThanReturningEmptyCatalog() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      ClientException failure = new ClientException("403 Forbidden: Sites.Read.All missing", null);
      stubSiteGetThrows(rootBuilder, failure);

      Exception thrown;

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         thrown = assertThrows(ClientException.class,
            () -> SharepointOnlineCatalog.listDatasets(fakeDataSource()));
      }

      assertSame(failure, thrown);
      verify(client).sites("root");
   }

   @Test
   void listDatasets_groupsCallThrowsAfterRootSucceeds_propagatesRatherThanReturningRootOnly() {
      // S4b: the sharpest case. Root succeeds and contributes data to the catalog before groups()
      // fails — a "did it throw" check that runs AFTER catalog.datasets() is already non-empty
      // would not be caught by the top-level empty-catalog check.
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of());
      stubLists(rootBuilder, List.of(spList("list-sales", "Sales")));

      ClientException failure = new ClientException("403 Forbidden: Group.Read.All missing", null);
      stubGroupsThrows(client, failure);

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         ClientException thrown = assertThrows(ClientException.class,
            () -> SharepointOnlineCatalog.listDatasets(fakeDataSource()));
         assertSame(failure, thrown);
      }
   }

   @Test
   void listDatasets_aSiteListsCallThrowsMidRecursion_propagatesRatherThanReturningPartialResult() {
      // S4c: doGetLists already throws (no catch) — this proves the SPI path doesn't add a new
      // try/catch on top of it that would turn that into a silent skip.
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of(site("subsite-1", "Team A")));
      stubLists(rootBuilder, List.of(spList("list-sales", "Sales")));

      SiteRequestBuilder subsiteBuilder = siteBuilder(client, "subsite-1");
      stubChildren(subsiteBuilder, List.of());
      ClientException failure = new ClientException("429 Too Many Requests", null);
      stubListsThrows(subsiteBuilder, failure);

      stubGroups(client, List.of());

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         ClientException thrown = assertThrows(ClientException.class,
            () -> SharepointOnlineCatalog.listDatasets(fakeDataSource()));
         assertSame(failure, thrown);
      }
   }

   @Test
   void listDatasets_oneSiteHasZeroLists_isLegalAndDoesNotThrow() throws Exception {
      // S4d: the "real" empty case — a site that genuinely has no lists must NOT be treated as a
      // failure. Overcorrecting S4a-c into "any zero-sized Graph result is an error" would make
      // this legitimate case fail too.
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of(site("empty-site", "Empty Site")));
      stubLists(rootBuilder, List.of(spList("list-sales", "Sales")));

      SiteRequestBuilder emptySiteBuilder = siteBuilder(client, "empty-site");
      stubChildren(emptySiteBuilder, List.of());
      stubLists(emptySiteBuilder, List.of());   // legitimately zero lists — not an error

      stubGroups(client, List.of());

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularCatalog catalog = SharepointOnlineCatalog.listDatasets(fakeDataSource());

         // Exactly root's one dataset; empty-site contributed zero, but is not the reason for
         // failure — and root's dataset must still be present.
         assertEquals(1, catalog.datasets().size());
         assertEquals("list-sales", SharepointDatasetId.parse(catalog.datasets().get(0).id()).list());
      }
   }
}
