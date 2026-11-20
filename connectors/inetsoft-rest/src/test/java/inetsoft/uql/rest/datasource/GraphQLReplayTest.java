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
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.graphql.GraphQLDataSource;
import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs a GraphQL query through the real runtime and pagination strategies, with the HTTP
 * responses scripted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class GraphQLReplayTest {
   @Test
   void aQueryWithoutPaginationSendsTheQueryAndVariablesInTheBody() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("graphql.test", "{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}");
      GraphQLQuery query = query("{ items { id } }", "{\"owner\":\"me\"}");

      List<Map<String, Object>> rows = rows(run(query, http));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals(1, http.requests().size(), http.requests().toString());
      String body = http.bodies().get(0);
      assertTrue(body.contains("\"query\":\"{ items { id } }\""), body);
      assertTrue(body.contains("\"owner\":\"me\""), body);
   }

   @Test
   void pageVariableAdvancesUntilTheCountPathReportsNoRecords() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("graphql.test",
         "{\"data\":{\"count\":2,\"items\":[{\"id\":1},{\"id\":2}]}}",
         "{\"data\":{\"count\":1,\"items\":[{\"id\":3}]}}",
         "{\"data\":{\"count\":0,\"items\":[]}}");
      GraphQLQuery query = query("query($page:Int){ items(page:$page){ id } }", "{}");
      query.setUsePagination(true);
      query.setPaginationVariable("page");
      query.setPaginationCountPath("$.data.count");

      List<Map<String, Object>> rows = rows(run(query, http));

      assertEquals(3, rows.size(), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      int first = pageVariable(http.bodies().get(0), "page");
      assertEquals(first + 1, pageVariable(http.bodies().get(1), "page"), http.bodies().toString());
      assertEquals(first + 2, pageVariable(http.bodies().get(2), "page"), http.bodies().toString());
   }

   @Test
   void cursorVariableCarriesTheLastCursorUntilItStopsChanging() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("graphql.test",
         page(1, "c1"), page(2, "c2"), page(3, "c2"));
      GraphQLQuery query = query("query($cursor:String){ items(after:$cursor){ id } }", "{}");
      query.setUsePagination(true);
      query.setCursorPagination(true);
      query.setPaginationVariable("cursor");
      query.setPaginationCountPath("$.data.endCursor");

      List<Map<String, Object>> rows = rows(run(query, http));

      // the page that echoes the cursor it was asked for is treated as the end and not kept
      assertEquals(List.of(1, 2), ids(rows), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertFalse(http.bodies().get(0).contains("\"cursor\":\"c"), http.bodies().get(0));
      assertTrue(http.bodies().get(1).contains("\"cursor\":\"c1\""), http.bodies().get(1));
      assertTrue(http.bodies().get(2).contains("\"cursor\":\"c2\""), http.bodies().get(2));
   }

   @Test
   void aGetRequestSendsTheQueryAsUrlParameters() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("graphql.test", "{\"data\":{\"items\":[{\"id\":1}]}}");
      GraphQLQuery query = query("{ items { id } }", "{}");
      query.setRequestType("GET");

      List<Map<String, Object>> rows = rows(run(query, http));

      assertEquals(1, rows.size(), rows.toString());
      assertEquals(1, http.requests().size(), http.requests().toString());
      // a GET carries the query and variables as query parameters of the data source
      Map<String, String> sent = new HashMap<>();

      for(inetsoft.uql.tabular.HttpParameter p :
         ((GraphQLDataSource) query.getDataSource()).getQueryHttpParameters())
      {
         sent.put(p.getName(), p.getValue());
      }

      assertEquals("{ items { id } }", sent.get("query"), sent.toString());
      assertEquals("{}", sent.get("variables"), sent.toString());
   }

   private static String page(int id, String cursor) {
      return "{\"data\":{\"endCursor\":\"" + cursor + "\",\"items\":[{\"id\":" + id + "}]}}";
   }

   private static int pageVariable(String body, String name) {
      java.util.regex.Matcher m =
         java.util.regex.Pattern.compile("\"" + name + "\":(\\d+)").matcher(body);
      assertTrue(m.find(), body);
      return Integer.parseInt(m.group(1));
   }

   private static GraphQLQuery query(String queryString, String variables) {
      GraphQLDataSource dataSource = new GraphQLDataSource();
      dataSource.setURL("https://graphql.test/api");
      GraphQLQuery query = new GraphQLQuery();
      query.setDataSource(dataSource);
      query.setQueryString(queryString);
      query.setVariables(variables);
      query.setJsonPath("$.data.items");
      query.setRequestType("POST");
      return query;
   }

   private static XTableNode run(GraphQLQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.GraphQLReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      assertNotNull(table, "the query returned no table; see the log for the error");
      return table;
   }

   private static List<Map<String, Object>> rows(XTableNode table) {
      List<Map<String, Object>> rows = new ArrayList<>();

      while(table.next()) {
         Map<String, Object> row = new LinkedHashMap<>();

         for(int i = 0; i < table.getColCount(); i++) {
            row.put(table.getName(i), table.getObject(i));
         }

         rows.add(row);
      }

      return rows;
   }

   private static List<Object> ids(List<Map<String, Object>> rows) {
      List<Object> ids = new ArrayList<>();
      rows.forEach(r -> ids.add(r.get("id")));
      return ids;
   }
}
