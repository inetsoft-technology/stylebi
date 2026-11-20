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
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.airtable.AirtableDataSource;
import inetsoft.uql.rest.datasource.airtable.AirtableQuery;
import inetsoft.uql.rest.datasource.clockify.ClockifyDataSource;
import inetsoft.uql.rest.datasource.copper.CopperDataSource;
import inetsoft.uql.rest.datasource.copper.CopperQuery;
import inetsoft.uql.rest.datasource.clockify.ClockifyQuery;
import inetsoft.uql.rest.datasource.github.GitHubDataSource;
import inetsoft.uql.rest.datasource.github.GitHubQuery;
import inetsoft.uql.rest.datasource.harvest.HarvestDataSource;
import inetsoft.uql.rest.datasource.harvest.HarvestQuery;
import inetsoft.uql.rest.datasource.jira.JiraDataSource;
import inetsoft.uql.rest.datasource.jira.JiraQuery;
import inetsoft.uql.rest.datasource.toggl.TogglDataSource;
import inetsoft.uql.rest.datasource.toggl.TogglQuery;
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

   @Test
   void jiraPagesAreReadByOffsetUntilTheTotal() throws Exception {
      // TOTAL_COUNT_AND_OFFSET: the first request has no offset, the next one has startAt set by
      // the page size, and the total is in the body
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on(url -> !url.contains("startAt"), "{\"total\":14,\"values\":" + ReplaySupport.array(1, 10) + "}")
         .on("startAt=10", "{\"total\":14,\"values\":" + ReplaySupport.array(11, 4) + "}");
      JiraDataSource dataSource = new JiraDataSource();
      dataSource.setServerType("local");
      dataSource.setLocalUrl("https://jira.test");
      dataSource.setApiToken("token");

      List<Map<String, Object>> rows = ReplaySupport.run(
         ReplaySupport.query(dataSource, new JiraQuery(), "Application Roles", "$.values",
                             Map.of()), http);

      assertEquals(14, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(0).contains("maxResults=10") &&
                 !http.requests().get(0).contains("startAt"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("startAt=10"), http.requests().get(1));
   }

   @Test
   void jiraReadsAnotherPageWhenOnlyOneRecordIsLeft() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on(url -> !url.contains("startAt"), "{\"total\":11,\"values\":" +
                                              ReplaySupport.array(1, 10) + "}")
         .on("startAt=10", "{\"total\":11,\"values\":" + ReplaySupport.array(11, 1) + "}");

      List<Map<String, Object>> rows = ReplaySupport.run(jiraRoles(), http);

      assertEquals(11, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Test
   void jiraMakesOneRequestWhenTheTotalIsExactlyOnePage() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/applicationrole", "{\"total\":10,\"values\":" + ReplaySupport.array(1, 10) + "}");

      List<Map<String, Object>> rows = ReplaySupport.run(jiraRoles(), http);

      assertEquals(10, rows.size());
      assertEquals(1, http.requests().size(), http.requests().toString());
   }

   private static JiraQuery jiraRoles() {
      JiraDataSource dataSource = new JiraDataSource();
      dataSource.setServerType("local");
      dataSource.setLocalUrl("https://jira.test");
      dataSource.setApiToken("token");
      return ReplaySupport.query(dataSource, new JiraQuery(), "Application Roles", "$.values",
                                 Map.of());
   }

   @Test
   void togglPagesAreReadUntilTheTotalCount() throws Exception {
      // TOTAL_COUNT_AND_PAGE: the first request has no page number, the next one asks for page 2
      // while 50 records per page do not cover the total in the body
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on(url -> !url.contains("page="), "{\"total_count\":60,\"data\":" + ReplaySupport.array(1, 50) + "}")
         .on("page=2", "{\"total_count\":60,\"data\":" + ReplaySupport.array(51, 10) + "}");
      TogglDataSource dataSource = new TogglDataSource();
      dataSource.setApiToken("token");

      List<Map<String, Object>> rows = ReplaySupport.run(
         ReplaySupport.query(dataSource, new TogglQuery(), "Workspace Tasks", "$.data",
                             Map.of("Workspace ID", "w1")), http);

      assertEquals(60, rows.size(), rows.toString());
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(1).contains("page=2"), http.requests().get(1));
   }

   @Test
   void githubPagesFollowTheLinkHeader() throws Exception {
      // LINK_ITERATION: the next page is the URL of the rel="next" entry of the Link header
      String next = "https://api.github.com/events?page=2";
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .onReplies(url -> !url.contains("page=2"), ScriptedHttpHandler.reply(
            ReplaySupport.array(1, 2), "Link", "<" + next + ">; rel=\"next\""))
         .on("page=2", ReplaySupport.array(3, 1));
      GitHubDataSource dataSource = new GitHubDataSource();
      dataSource.setAccessToken("token");

      List<Map<String, Object>> rows = ReplaySupport.run(
         ReplaySupport.query(dataSource, new GitHubQuery(), "Public Events", "$", Map.of()), http);

      assertEquals(List.of(1, 2, 3), ReplaySupport.column(rows, "id"));
      assertEquals(next, http.requests().get(1));
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Test
   void aLookupRunsOneRequestPerParentRowAndAttachesTheResult() throws Exception {
      // Workspaces -> Clients: each workspace id is passed to the clients endpoint
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v1/workspaces/w1/clients", "[{\"name\":\"c1\"}]", "[]")
         .on("/v1/workspaces/w2/clients", "[{\"name\":\"c2\"},{\"name\":\"c3\"}]", "[]")
         .on("/v1/workspaces", "[{\"id\":\"w1\",\"name\":\"One\"},{\"id\":\"w2\",\"name\":\"Two\"}]");

      XTableNode table = ReplaySupport.runTable(clockifyWorkspacesWithClients(), http);

      assertNotNull(table);
      assertEquals(5, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(0).endsWith("/v1/workspaces"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("/workspaces/w1/clients"), http.requests().toString());
      assertTrue(http.requests().get(3).contains("/workspaces/w2/clients"), http.requests().toString());
      List<String> columns = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         columns.add(table.getName(i));
      }

      assertTrue(columns.contains("id") && columns.contains("name"), columns.toString());
      assertTrue(columns.stream().anyMatch(c -> c.startsWith("Clients")), columns.toString());
   }

   @Test
   void aLookupIsSkippedForARowWithoutTheKey() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v1/workspaces/w2/clients", "[{\"name\":\"c2\"}]", "[]")
         .on("/v1/workspaces", "[{\"name\":\"No id\"},{\"id\":\"w2\",\"name\":\"Two\"}]");

      assertNotNull(ReplaySupport.runTable(clockifyWorkspacesWithClients(), http));
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(1).contains("/workspaces/w2/clients"), http.requests().toString());
   }

   @Test
   void aFailedRequestEndsTheQueryWithoutThrowing() throws Exception {
      // a server error is logged and the query returns what it has; here, nothing
      ScriptedHttpHandler http = new ScriptedHttpHandler().onReplies(
         url -> true, ScriptedHttpHandler.failure(new java.io.IOException("HTTP 500")));

      XTableNode table = ReplaySupport.runTable(clockifyClients(), http);

      assertEquals(1, http.requests().size(), http.requests().toString());

      if(table != null) {
         assertFalse(table.next(), "a failed request must not produce rows");
      }
   }

   @Test
   void aFailureOnALaterPageKeepsTheRowsAlreadyRead() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().onReplies(
         url -> true, new ScriptedHttpHandler.Reply(ReplaySupport.array(1, 2), List.of(), null),
         ScriptedHttpHandler.failure(new java.io.IOException("HTTP 503")));

      XTableNode table = ReplaySupport.runTable(clockifyClients(), http);

      assertNotNull(table);
      int rows = 0;

      while(table.next()) {
         rows++;
      }

      assertEquals(2, rows);
      assertEquals(2, http.requests().size(), http.requests().toString());
   }

   @Test
   void aBodyThatIsNotJsonDoesNotThrow() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("/clients", "<html>oops</html>");

      XTableNode table = ReplaySupport.runTable(clockifyClients(), http);

      if(table != null) {
         assertFalse(table.next());
      }
   }

   @Test
   void copperPostsTheBodyOnEveryPageAndReadsTheTotalFromAHeader() throws Exception {
      // a POST search endpoint: the request body is sent with each request, the total comes in the
      // X-PW-TOTAL response header, and the page number and size are query parameters
      ScriptedHttpHandler http = new ScriptedHttpHandler().onReplies(url -> true,
         ScriptedHttpHandler.reply(ReplaySupport.array(1, 200), "X-PW-TOTAL", "203"),
         ScriptedHttpHandler.reply(ReplaySupport.array(201, 3), "X-PW-TOTAL", "203"));
      CopperDataSource dataSource = new CopperDataSource();
      dataSource.setApiKey("key");
      dataSource.setUserEmail("user@example.com");
      CopperQuery query = ReplaySupport.query(dataSource, new CopperQuery(), "Users", "$",
                                              Map.of());
      query.setRequestBody("{\"name\":\"Ann\"}");

      List<Map<String, Object>> rows = ReplaySupport.run(query, http);

      assertEquals(203, rows.size());
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(0).contains("/v1/users/search"), http.requests().get(0));
      assertTrue(http.requests().get(0).contains("page_size=200"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("page_number=2"), http.requests().get(1));
      assertEquals(List.of("{\"name\":\"Ann\"}", "{\"name\":\"Ann\"}"), http.bodies());
   }

   @Test
   void copperGetEndpointSendsNoBody() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v1/account", "{\"id\":1,\"name\":\"Acme\"}");
      CopperDataSource dataSource = new CopperDataSource();
      dataSource.setApiKey("key");
      dataSource.setUserEmail("user@example.com");

      List<Map<String, Object>> rows = ReplaySupport.run(
         ReplaySupport.query(dataSource, new CopperQuery(), "Account Details", "$", Map.of()),
         http);

      assertEquals(1, rows.size());
      assertNull(http.bodies().get(0));
   }

   private static ClockifyQuery clockifyWorkspacesWithClients() {
      ClockifyDataSource dataSource = new ClockifyDataSource();
      dataSource.setApiKey("key");
      ClockifyQuery query = ReplaySupport.query(dataSource, new ClockifyQuery(), "Workspaces", "$",
                                                Map.of());
      inetsoft.uql.rest.json.lookup.JsonLookupQuery lookup =
         new inetsoft.uql.rest.json.lookup.JsonLookupQuery();
      lookup.setJsonPath("$.[*]");
      lookup.setLookupEndpoint(inetsoft.uql.rest.json.lookup.JsonLookupEndpoint.builder()
         .endpoint("Clients").parameterName("Workspace Id").jsonPath("$.[*]").key("id").build());
      query.setLookupQueries(List.of(lookup));
      return query;
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
