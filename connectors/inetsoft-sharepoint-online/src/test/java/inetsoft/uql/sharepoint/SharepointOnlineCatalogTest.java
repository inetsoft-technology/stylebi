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

import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.uql.tabular.TabularDatasetSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static inetsoft.uql.sharepoint.SharepointGraphTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Covers charter assertions S1 (listDatasets: full site x list combination, including recursion
 * into subsites) and S2 (describeDataset: real columns, Graph type mapping). Uses real Graph SDK
 * model/page objects and Mockito mocks for the request-builder/request fluent chain — see
 * {@link SharepointGraphTestSupport}.
 */
@Tag("connector")
class SharepointOnlineCatalogTest {

   @Test
   void listDatasets_returnsSiteByListFullCombination_rootSubsiteAndGroupSite() throws Exception {
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Contoso Root"));
      stubChildren(rootBuilder, List.of(site("subsite-1", "Team A")));
      stubLists(rootBuilder, List.of(spList("list-sales", "Sales"), spList("list-hr", "HR")));

      SiteRequestBuilder subsiteBuilder = siteBuilder(client, "subsite-1");
      stubChildren(subsiteBuilder, List.of());
      stubLists(subsiteBuilder, List.of(spList("list-tasks", "Tasks")));

      stubGroups(client, List.of(group("group-1", "Marketing Group")));
      stubGroupSite(client, "group-1", site("group-site-1", "Marketing Site"));
      SiteRequestBuilder groupSiteBuilder = siteBuilder(client, "group-site-1");
      stubChildren(groupSiteBuilder, List.of());
      stubLists(groupSiteBuilder, List.of(spList("list-campaigns", "Campaigns"),
         spList("list-budget", "Budget")));

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularCatalog catalog = SharepointOnlineCatalog.listDatasets(fakeDataSource());

         // 2 (root) + 1 (subsite) + 2 (group site) = 5 — a real sum, not size-per-site * count.
         assertEquals(5, catalog.datasets().size());

         Set<String> ids = new HashSet<>();

         for(TabularDatasetRef ref : catalog.datasets()) {
            assertTrue(ids.add(ref.id()), "duplicate dataset id: " + ref.id());
         }

         assertEquals(5, ids.size());

         // Round-trip every id back to its (site, list) pair and confirm the set of pairs is
         // exactly what was declared above — content-level, not "non-empty".
         Set<String> pairs = new HashSet<>();

         for(String id : ids) {
            SharepointDatasetId.Parsed parsed = SharepointDatasetId.parse(id);
            pairs.add(parsed.site() + "|" + parsed.list());
         }

         assertEquals(Set.of("root|list-sales", "root|list-hr", "subsite-1|list-tasks",
            "group-site-1|list-campaigns", "group-site-1|list-budget"), pairs);
      }
   }

   @Test
   void listDatasets_threeLevelSubsiteRecursion_allLevelsAppear() throws Exception {
      GraphServiceClient client = mock(GraphServiceClient.class);

      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubSiteGet(rootBuilder, site("root", "Root"));
      stubChildren(rootBuilder, List.of(site("level-2", "Level 2")));
      stubLists(rootBuilder, List.of(spList("list-l1", "Level 1 List")));

      SiteRequestBuilder level2Builder = siteBuilder(client, "level-2");
      stubChildren(level2Builder, List.of(site("level-3", "Level 3")));
      stubLists(level2Builder, List.of(spList("list-l2", "Level 2 List")));

      SiteRequestBuilder level3Builder = siteBuilder(client, "level-3");
      stubChildren(level3Builder, List.of());
      stubLists(level3Builder, List.of(spList("list-l3", "Level 3 List")));

      stubGroups(client, List.of());

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularCatalog catalog = SharepointOnlineCatalog.listDatasets(fakeDataSource());

         // The whole point of this case: getChildSites() only went one level deep; the new
         // listSitesOrThrow path must reach level 3, not just level 2.
         assertEquals(3, catalog.datasets().size());

         Set<String> lists = new HashSet<>();

         for(TabularDatasetRef ref : catalog.datasets()) {
            lists.add(SharepointDatasetId.parse(ref.id()).list());
         }

         assertEquals(Set.of("list-l1", "list-l2", "list-l3"), lists);
      }
   }

   @Test
   void describeDataset_mapsAllSevenGraphColumnShapesToXSchema() throws Exception {
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");

      ColumnDefinition text = column("Title", c -> c.text = new TextColumn());
      ColumnDefinition numberLong = column("Quantity", c -> {
         c.number = new NumberColumn();
         c.number.decimalPlaces = "none";
      });
      ColumnDefinition numberDouble = column("Weight", c -> {
         c.number = new NumberColumn();
         c.number.decimalPlaces = "one";
      });
      ColumnDefinition bool = column("IsActive", c -> c.msgraphBoolean = new BooleanColumn());
      ColumnDefinition dateOnly = column("DueDate", c -> {
         c.dateTime = new DateTimeColumn();
         c.dateTime.format = "dateOnly";
      });
      ColumnDefinition dateTimeInstant = column("Modified", c -> {
         c.dateTime = new DateTimeColumn();
         c.dateTime.format = "dateTime";
      });
      ColumnDefinition currency = column("Price", c -> c.currency = new CurrencyColumn());

      stubColumns(rootBuilder, "list-1", List.of(text, numberLong, numberDouble, bool, dateOnly,
         dateTimeInstant, currency));

      String datasetId = SharepointDatasetId.compose("root", "list-1");

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularDatasetSchema schema =
            SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);

         assertEquals(datasetId, schema.datasetId());
         assertEquals(7, schema.columns().size());
         assertEquals("Title", schema.columns().get(0).name());
         assertEquals(XSchema.STRING, schema.columns().get(0).type());
         assertEquals("Quantity", schema.columns().get(1).name());
         assertEquals(XSchema.LONG, schema.columns().get(1).type());
         assertEquals("Weight", schema.columns().get(2).name());
         assertEquals(XSchema.DOUBLE, schema.columns().get(2).type());
         assertEquals("IsActive", schema.columns().get(3).name());
         assertEquals(XSchema.BOOLEAN, schema.columns().get(3).type());
         assertEquals("DueDate", schema.columns().get(4).name());
         assertEquals(XSchema.DATE, schema.columns().get(4).type());
         assertEquals("Modified", schema.columns().get(5).name());
         assertEquals(XSchema.TIME_INSTANT, schema.columns().get(5).type());
         assertEquals("Price", schema.columns().get(6).name());
         assertEquals(XSchema.DOUBLE, schema.columns().get(6).type());

         // TabularDatasetSchema.keyColumns() is documented "Never null"; SharePoint declares no
         // key (the system ID column doesn't surface through this mapping — see class javadoc).
         assertNotNull(schema.keyColumns());
         assertTrue(schema.keyColumns().isEmpty());
      }
   }

   @Test
   void describeDataset_paramsAreSiteAndListNotTheComposedId() throws Exception {
      // Charter A1 reverse: dataset.source (the escaped, composed id) must not leak into params.
      // If the implementation took the lazy route of Map.of("id", datasetId), or used the wrong
      // key names, this Map equality fails outright.
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder rootBuilder = siteBuilder(client, "root");
      stubColumns(rootBuilder, "list-1", List.of(column("Title", c -> c.text = new TextColumn())));

      String datasetId = SharepointDatasetId.compose("root", "list-1");

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularDatasetSchema schema =
            SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);

         assertEquals(Map.of("site", "root", "list", "list-1"), schema.params());
      }
   }

   @Test
   void describeDataset_paramsCarryUnescapedSiteNotTheEscapedIdFragment() throws Exception {
      // A site id containing a dot (the common case — see SharepointDatasetId's javadoc on why
      // real Graph site ids are hostnames) gets percent-escaped inside the composed id. params
      // must carry the connector's real, UNESCAPED property value ("contoso.sharepoint.com"), not
      // the escaped fragment ("contoso%2Esharepoint%2Ecom") that SharepointDatasetId uses
      // internally to satisfy the no-dot id contract. This is the id-encoding-leak check from
      // charter A1's reverse clause, applied to the specific case that motivated the escaping
      // scheme in the first place.
      GraphServiceClient client = mock(GraphServiceClient.class);
      SiteRequestBuilder siteBuilder = siteBuilder(client, "contoso.sharepoint.com");
      stubColumns(siteBuilder, "Sales List",
         List.of(column("Title", c -> c.text = new TextColumn())));

      String datasetId = SharepointDatasetId.compose("contoso.sharepoint.com", "Sales List");

      try(MockedStatic<GraphServiceClient> mocked = mockClientFactory(client)) {
         TabularDatasetSchema schema =
            SharepointOnlineCatalog.describeDataset(fakeDataSource(), datasetId);

         assertEquals("contoso.sharepoint.com", schema.params().get("site"));
         assertEquals("Sales List", schema.params().get("list"));
      }
   }
}
