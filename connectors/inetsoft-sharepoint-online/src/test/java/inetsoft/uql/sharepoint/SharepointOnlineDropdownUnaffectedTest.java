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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static inetsoft.uql.sharepoint.SharepointGraphTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers charter assertion S5. {@code inetsoft-sharepoint-online} had zero test files before this
 * round, so "existing tests green" is vacuously true and proves nothing — per reconcile, the
 * actual proof is two-part: (1) these characterization tests, pinning today's connect-dialog
 * dropdown behavior as a baseline it did not have before, and (2) confirming by diff that
 * {@code doGetSites}/{@code getChildSites}/{@code doGetLists}/{@code doGetListColumns} were not
 * edited — the SPI path added in this round is new code sitting next to them
 * ({@link SharepointOnlineRuntime#listSitesOrThrow}), not a modification of them. (2) is recorded
 * in 04-build.md, not repeated here as a test — there is no meaningful way to assert "these bytes
 * are unchanged" from inside a JUnit test.
 *
 * The interesting contrast with {@link SharepointOnlineCatalogPermissionFailureTest}: the SPI path
 * must throw on a permission failure; the dropdown path, characterized here, must go on silently
 * degrading exactly as it always has — because that is a real deliberate difference in
 * requirements (annotation vs. an interactive dialog the user can just retry), not an
 * inconsistency to "fix" while touching this connector.
 */
@Tag("connector")
class SharepointOnlineDropdownUnaffectedTest {

   @Test
   void getSites_rootSucceedsGroupsThrows_stillReturnsRootOnlyWithoutThrowing() {
      // Pins the pre-existing catch-and-LOG.warn behavior in doGetSites's groups try/catch, which
      // this round deliberately does NOT touch (contrast with the SPI path's S4b, which must
      // throw in the same scenario).
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of());

      stubGroupsThrows(client, new ClientException("403 Forbidden: Group.Read.All missing", null));

      String[][] result;

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         result = SharepointOnlineRuntime.getSites(fakeDataSource());
      }

      assertEquals(1, result.length);
      assertEquals("Contoso Root", result[0][0]);
      assertEquals("root", result[0][1]);
   }

   @Test
   void getSites_rootThrows_returnsWhateverGroupsContributedWithoutThrowing() {
      // The mirror image: root's own try/catch is independent of groups'. Pins today's behavior
      // that a root failure alone still doesn't surface as an exception to the dropdown.
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGetThrows(rootBuilder, new ClientException("403 Forbidden: Sites.Read.All missing", null));

      stubGroups(client, List.of(group("group-1", "Marketing Group")));
      stubGroupSite(client, "group-1", site("group-site-1", "Marketing Site"));
      SiteRequestBuilder groupSiteBuilder = siteBuilder(client, "group-site-1");
      stubChildren(groupSiteBuilder, List.of());

      String[][] result;

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         result = SharepointOnlineRuntime.getSites(fakeDataSource());
      }

      assertEquals(1, result.length);
      assertEquals("Marketing Site", result[0][0]);
   }

   @Test
   void getSites_grandchildSites_remainUnreachable_nonRecursiveByDesign() {
      // Pins the OTHER known limitation (charter's known-limitations list, item 1): the dropdown's
      // getChildSites() is genuinely one level only. This is deliberately NOT fixed for the
      // dropdown path in this round (only the new SPI path is recursive) — this test documents
      // that today's dropdown behavior did not silently change underneath it.
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of(site("child-1", "Child Site")));

      SiteRequestBuilder childBuilder = siteBuilder(client, "child-1");
      stubChildren(childBuilder, List.of(site("grandchild-1", "Grandchild Site")));

      stubGroups(client, List.of());

      String[][] result;

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         result = SharepointOnlineRuntime.getSites(fakeDataSource());
      }

      List<String> names = List.of(result).stream().map(row -> row[0]).toList();
      assertTrue(names.contains("Contoso Root"));
      assertTrue(names.contains("Child Site"));
      assertFalse(names.contains("Grandchild Site"),
         "dropdown's getChildSites() is one level only — this must stay true for S5");
   }

   @Test
   void getLists_normalCase_returnsListsForTheGivenSite() {
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder siteBuilder = siteBuilder(client, "site-1");
      stubLists(siteBuilder, List.of(spList("list-1", "Sales"), spList("list-2", "HR")));

      String[][] result;

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         result = SharepointOnlineRuntime.getLists(fakeDataSource(), "site-1");
      }

      assertEquals(2, result.length);
      assertEquals("Sales", result[0][0]);
      assertEquals("HR", result[1][0]);
   }
}
