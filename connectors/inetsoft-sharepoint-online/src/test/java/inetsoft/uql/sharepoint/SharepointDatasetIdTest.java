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

import com.microsoft.graph.models.ColumnDefinition;
import com.microsoft.graph.models.TextColumn;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteRequestBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static inetsoft.uql.sharepoint.SharepointGraphTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Covers charter assertion S3 and the reverse assertion "no {@code .} in a SharePoint composite
 * id, including a component that naturally carries one" (the SharePoint site id's hostname half —
 * see {@link SharepointDatasetId}'s javadoc).
 *
 * Two layers of coverage, per reconcile's instruction that a round-trip alone is not enough: a
 * pure {@link SharepointDatasetId} round-trip (fast, no Graph involved), AND — the assertion that
 * actually matters — driving {@link SharepointOnlineCatalog#describeDataset} end to end with a
 * mocked Graph client and verifying the *decoded* site/list values the client actually received. A
 * wrong decode that happens not to throw would pass a "no exception" check; it cannot pass
 * {@code verify(client).sites(originalValue)}.
 */
@Tag("connector")
class SharepointDatasetIdTest {

   // ----- Pure round-trip (no Graph client) -----

   @Test
   void composeParse_plainGuidPair_roundTrips() {
      String id = SharepointDatasetId.compose("11111111-1111-1111-1111-111111111111",
         "22222222-2222-2222-2222-222222222222");

      assertFalse(id.contains("."));

      SharepointDatasetId.Parsed parsed = SharepointDatasetId.parse(id);
      assertEquals("11111111-1111-1111-1111-111111111111", parsed.site());
      assertEquals("22222222-2222-2222-2222-222222222222", parsed.list());
   }

   @Test
   void composeParse_distinctDottedSiteIdsDoNotCollide() {
      // The concrete failure mode a lossy "strip the dots" encoding would produce: two distinct
      // inputs colliding on the same composed id.
      String id1 = SharepointDatasetId.compose("a.b.c", "list-1");
      String id2 = SharepointDatasetId.compose("abc", "list-1");

      assertNotEquals(id1, id2);
   }

   @Test
   void composeParse_componentContainingLiteralPercentSequence_doesNotMisdecode() {
      // Adversarial input containing the literal substring "%2E" — not an escaped dot, just those
      // three characters. Exercises the escape-%-first / unescape-%-last ordering discipline: a
      // naive implementation that escapes '.' before '%' would corrupt this round trip.
      String site = "weird%2Ename";
      String list = "list-1";

      String id = SharepointDatasetId.compose(site, list);
      assertFalse(id.contains("."));

      SharepointDatasetId.Parsed parsed = SharepointDatasetId.parse(id);
      assertEquals(site, parsed.site());
      assertEquals(list, parsed.list());
   }

   @Test
   void parse_notASharepointId_throwsIllegalArgumentException() {
      assertThrows(IllegalArgumentException.class,
         () -> SharepointDatasetId.parse("no-separator-here"));
   }

   // ----- S3: verified against the Graph client the decoded value actually reaches -----

   @Test
   void describeDataset_regularCase_graphClientReceivesOriginalSiteAndListValues() throws Exception {
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder siteBuilder = siteBuilder(client, "site-1");
      stubColumns(siteBuilder, "list-1", List.of(titleColumn()));

      String datasetId = SharepointDatasetId.compose("site-1", "list-1");

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);
      }

      verify(client).sites("site-1");
      verify(siteBuilder).lists("list-1");
   }

   @Test
   void describeDataset_siteComponentNaturallyContainingDots_graphClientReceivesTheDottedValue()
      throws Exception
   {
      // The realistic case, not an extreme one: SharepointOnlineRuntime.getSiteId() truncates a
      // Graph site id to its hostname, e.g. "contoso.sharepoint.com" — dots on every real tenant.
      String dottedSite = "contoso.sharepoint.com";

      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder siteBuilder = siteBuilder(client, dottedSite);
      stubColumns(siteBuilder, "list-1", List.of(titleColumn()));

      String datasetId = SharepointDatasetId.compose(dottedSite, "list-1");
      assertFalse(datasetId.contains("."), "composed id must not contain '.': " + datasetId);

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);
      }

      // Not "no exception" — the client must have been asked for the exact original dotted
      // hostname, proving the decode reconstructed it rather than mangling or dropping the dots.
      verify(client).sites(dottedSite);
   }

   @Test
   void describeDataset_componentsContainingTheSeparatorItself_graphClientReceivesOriginalValues()
      throws Exception
   {
      // The separator is SharepointDatasetId's implementation detail, not part of its public
      // contract, so it's discovered from a real composed id rather than hardcoded — if the
      // implementation ever changes it, this test keeps working as long as some character is
      // chosen and escaped consistently.
      String separator = discoverSeparator();
      String siteWithSeparator = "site" + separator + "name";
      String listWithSeparator = "list" + separator + "42";

      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder siteBuilder = siteBuilder(client, siteWithSeparator);
      stubColumns(siteBuilder, listWithSeparator, List.of(titleColumn()));

      String datasetId = SharepointDatasetId.compose(siteWithSeparator, listWithSeparator);

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);
      }

      // Must not have been misparsed into more than two segments at the embedded separator.
      verify(client).sites(siteWithSeparator);
      verify(siteBuilder).lists(listWithSeparator);
   }

   private static ColumnDefinition titleColumn() {
      return column("Title", c -> c.text = new TextColumn());
   }

   /**
    * Composes two known values, then finds the run of characters in the composed id that is
    * neither value nor a percent-escape, on the assumption that {@code compose} is
    * {@code escape(site) + SEPARATOR + escape(list)} and neither {@code "AAAA"} nor {@code "BBBB"}
    * needs escaping.
    */
   private static String discoverSeparator() {
      String id = SharepointDatasetId.compose("AAAA", "BBBB");
      int aEnd = id.indexOf("AAAA") + "AAAA".length();
      int bStart = id.indexOf("BBBB");
      assertTrue(aEnd <= bStart, "unexpected composed id shape: " + id);
      return id.substring(aEnd, bStart);
   }
}
