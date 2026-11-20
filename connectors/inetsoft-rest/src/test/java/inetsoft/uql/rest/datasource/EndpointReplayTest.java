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

package inetsoft.uql.rest.datasource;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.ReplaySupport;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.rest.datasource.airtable.AirtableDataSource;
import inetsoft.uql.rest.datasource.airtable.AirtableQuery;
import inetsoft.uql.rest.datasource.clockify.ClockifyDataSource;
import inetsoft.uql.rest.datasource.clockify.ClockifyQuery;
import inetsoft.uql.rest.datasource.harvest.HarvestDataSource;
import inetsoft.uql.rest.datasource.harvest.HarvestQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs a real query through the real runtime, pagination and JSON transformer, with the HTTP
 * responses scripted, so no network or credentials are needed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class EndpointReplayTest {
   @Test
   void airtableRecordsAreReadAcrossPagesFollowingTheOffset() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v0/app1/Stories", """
            {"records":[{"id":"rec1","fields":{"Name":"A"}},{"id":"rec2","fields":{"Name":"B"}}],
             "offset":"next-page"}""", """
            {"records":[{"id":"rec3","fields":{"Name":"C"}}]}""");

      List<Map<String, Object>> rows = run(airtable("Records", "$.records",
         Map.of("Base ID", "app1", "Table Name", "Stories", "Time Zone", "America/New_York",
                 "User Locale", "en-us")), http);

      assertEquals(3, rows.size(), rows.toString());
      assertEquals(List.of("rec1", "rec2", "rec3"), column(rows, "id"));
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertFalse(http.requests().get(0).contains("offset="), http.requests().get(0));
      assertTrue(http.requests().get(0).contains("pageSize=100"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("offset=next-page"), http.requests().get(1));
   }

   @Test
   void airtableSingleRecordMakesOneRequest() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v0/app1/Stories/rec9", "{\"id\":\"rec9\",\"fields\":{\"Name\":\"Z\"}}");

      List<Map<String, Object>> rows = run(airtable("Record", "$",
         Map.of("Base ID", "app1", "Table Name", "Stories", "Record ID", "rec9")), http);

      assertEquals(1, rows.size(), rows.toString());
      assertEquals(1, http.requests().size());
   }

   @Test
   void clockifyPagesAreReadUntilAnEmptyPage() throws Exception {
      // the PAGE strategy stops at the first empty page, not at a short one
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/workspaces/ws1/clients", ReplaySupport.array(1, 200), ReplaySupport.array(201, 3),
             "[]");

      List<Map<String, Object>> rows = ReplaySupport.run(clockifyClients(), http);

      assertEquals(203, rows.size());
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(0).contains("page=1") &&
                 http.requests().get(0).contains("page-size=200"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("page=2"), http.requests().get(1));
      assertTrue(http.requests().get(2).contains("page=3"), http.requests().get(2));
   }

   @Test
   void clockifyWithAnEmptyFirstPageReturnsNoTableAfterOneRequest() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("/workspaces/ws1/clients", "[]");

      // a query with no data at all returns no table, not an empty one; this pins that
      assertNull(ReplaySupport.runTable(clockifyClients(), http));
      assertEquals(1, http.requests().size(), http.requests().toString());
   }

   @Test
   void harvestPagesAreReadUpToTheTotalPages() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v2/clients", "{\"clients\":" + ReplaySupport.array(1, 2) + ",\"total_pages\":3}",
             "{\"clients\":" + ReplaySupport.array(3, 2) + ",\"total_pages\":3}",
             "{\"clients\":" + ReplaySupport.array(5, 1) + ",\"total_pages\":3}");
      HarvestDataSource dataSource = new HarvestDataSource();
      dataSource.setAccountId("acct");
      dataSource.setAccessToken("token");

      List<Map<String, Object>> rows = ReplaySupport.run(
         ReplaySupport.query(dataSource, new HarvestQuery(), "Clients", "$.clients", Map.of()),
         http);

      assertEquals(List.of(1, 2, 3, 4, 5), ReplaySupport.column(rows, "id"));
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(2).contains("page=3"), http.requests().get(2));
   }

   private static ClockifyQuery clockifyClients() {
      ClockifyDataSource dataSource = new ClockifyDataSource();
      dataSource.setApiKey("key");
      return ReplaySupport.query(dataSource, new ClockifyQuery(), "Clients", "$",
                                 Map.of("Workspace Id", "ws1"));
   }

   private static AirtableQuery airtable(String endpoint, String jsonPath,
                                         Map<String, String> parameters)
   {
      AirtableDataSource dataSource = new AirtableDataSource();
      dataSource.setApiToken("token");
      return ReplaySupport.query(dataSource, new AirtableQuery(), endpoint, jsonPath, parameters);
   }

   private static List<Map<String, Object>> run(AirtableQuery query, ScriptedHttpHandler http) {
      return ReplaySupport.run(query, http);
   }

   private static List<Object> column(List<Map<String, Object>> rows, String name) {
      return ReplaySupport.column(rows, name);
   }
}
