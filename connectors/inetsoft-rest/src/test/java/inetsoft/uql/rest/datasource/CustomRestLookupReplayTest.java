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
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonQuery;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lookups of a custom REST JSON query, where the user types the lookup URLs ("/customers/{param1}/
 * orders"), the path of the entities and the key, level by level. It is a third copy of the lookup
 * code (next to the endpoint one and the XML one), so it is asked the same questions: a neighbour
 * that fails, a row without the key, a parent without children, every page, the row limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class CustomRestLookupReplayTest {
   @Test
   void twoLevelsAreReadAndEachLevelGetsTheKeyOfItsParent() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/orders/o1/items", "[{\"sku\":\"s1\"}]")
         .on("/orders/o2/items", "[{\"sku\":\"s2\"},{\"sku\":\"s3\"}]")
         .on("/orders/o3/items", "[{\"sku\":\"s4\"}]")
         .on("/customers/c1/orders", "[{\"id\":\"o1\"},{\"id\":\"o2\"}]")
         .on("/customers/c2/orders", "[{\"id\":\"o3\"}]")
         .on("rest.test/customers", "[{\"id\":\"c1\"},{\"id\":\"c2\"}]");

      String dump = dump(run(customersOrdersItems(), http));

      assertNotNull(dump);
      assertEquals(6, http.requests().size(), http.requests().toString());
      assertTrue(dump.contains("s1") && dump.contains("s2") && dump.contains("s3") &&
                 dump.contains("s4"), dump);
   }

   @Test
   void everyPageOfTheParentGetsItsLookups() throws Exception {
      // the parent pages by a page number; each page's rows have to be looked up
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/customers/c1/orders", "[{\"id\":\"o1\"}]")
         .on("/customers/c2/orders", "[{\"id\":\"o2\"}]")
         .on("rest.test/customers", "{\"pages\":2,\"data\":[{\"id\":\"c1\"}]}",
             "{\"pages\":2,\"data\":[{\"id\":\"c2\"}]}");
      RestJsonQuery query = customersOrders();
      query.setJsonPath("$.data");
      // the path of the lookup entities is applied to the whole response, not to the data
      query.setLookupJsonPath(0, "$.data[*]");
      query.setPaginationType(inetsoft.uql.rest.pagination.PaginationType.PAGE_COUNT);
      query.setTotalPagesParamType(inetsoft.uql.rest.pagination.PaginationParamType.JSON_PATH);
      query.setTotalPagesParamValue("$.pages");
      query.setPageNumberParamToWriteType(inetsoft.uql.rest.pagination.PaginationParamType.QUERY);
      query.setPageNumberParamToWriteValue("page");

      String dump = dump(run(query, http));

      assertNotNull(dump);
      assertTrue(dump.contains("o1") && dump.contains("o2"), dump + "\n" + http.requests());
   }

   @Test
   void aFailingLookupFailsTheWholeQueryEvenForTheHealthyNeighbours() throws Exception {
      // the same as the endpoint and XML runners: nothing is returned and c2 is never asked for
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .onReplies(url -> url.contains("/customers/c1/orders"),
                    ScriptedHttpHandler.failure(new IOException("HTTP 500")))
         .on("/customers/c2/orders", "[{\"id\":\"o2\"}]")
         .on("rest.test/customers", "[{\"id\":\"c1\"},{\"id\":\"c2\"}]");

      String dump = dump(run(customersOrders(), http));

      assertNull(dump, dump);
      assertTrue(http.requests().stream().noneMatch(u -> u.contains("/c2/")),
                 http.requests().toString());
   }

   @Test
   @Disabled("Bug #81925: a row without the lookup key makes the whole custom REST query fail, while the endpoint query skips the row; enable when it is fixed")
   void aRowWithoutTheKeyIsSkippedAndItsNeighboursAreNot() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/customers/c2/orders", "[{\"id\":\"o2\"}]")
         .on("rest.test/customers", "[{\"name\":\"no id\"},{\"id\":\"c2\"}]");

      String dump = dump(run(customersOrders(), http));

      assertNotNull(dump, "one row without the key made the whole query fail");
      assertTrue(dump.contains("o2"), dump);
      assertTrue(http.requests().stream().noneMatch(u -> u.contains("null")),
                 http.requests().toString());
   }

   @Test
   void aParentWithNoChildrenIsNotDropped() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/customers/c1/orders", "[]")
         .on("/customers/c2/orders", "[{\"id\":\"o2\"}]")
         .on("rest.test/customers", "[{\"id\":\"c1\"},{\"id\":\"c2\"}]");

      String dump = dump(run(customersOrders(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("c1"), "the customer without orders is gone: " + dump);
   }

   @Test
   void theRowLimitStopsTheLookupsToo() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/customers/c1/orders", "[{\"id\":\"o1\"}]")
         .on("/customers/c2/orders", "[{\"id\":\"o2\"}]")
         .on("/customers/c3/orders", "[{\"id\":\"o3\"}]")
         .on("rest.test/customers", "[{\"id\":\"c1\"},{\"id\":\"c2\"},{\"id\":\"c3\"}]");
      RestJsonQuery query = customersOrders();
      query.setMaxRows(1);

      run(query, http);

      long orderRequests = http.requests().stream().filter(u -> u.contains("/orders")).count();
      assertEquals(1, orderRequests, "looked up the orders of rows beyond the limit: " +
                   http.requests());
   }

   @Test
   void aListOfPlainIdsIsLookedUpOneByOne() throws Exception {
      // an empty key means that the entity itself, a string, is the id
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/orders/o1", "{\"total\":10}")
         .on("/orders/o2", "{\"total\":20}")
         .on("rest.test/customers", "[{\"id\":\"c1\",\"orders\":[\"o1\",\"o2\"]}]");
      RestJsonQuery query = query("/customers");
      query.setLookupURL(0, "/orders/{param1}");
      query.setLookupJsonPath(0, "$[*].orders[*]");
      query.setLookupKey(0, "");

      String dump = dump(run(query, http));

      assertNotNull(dump);
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(dump.contains("10") && dump.contains("20"), dump);
   }

   @Test
   void aLookupUrlThatIgnoresTheBaseUrlGoesToAnotherHost() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("other.test/orders/c1", "[{\"id\":\"o1\"}]")
         .on("rest.test/customers", "[{\"id\":\"c1\"}]");
      RestJsonQuery query = query("/customers");
      query.setLookupURL(0, "https://other.test/orders/{param1}");
      query.setLookupJsonPath(0, "$[*]");
      query.setLookupKey(0, "id");
      query.setLookupIgnoreBaseUrl(0, true);

      String dump = dump(run(query, http));

      assertNotNull(dump);
      assertTrue(http.requests().contains("https://other.test/orders/c1"),
                 http.requests().toString());
   }

   private static RestJsonQuery customersOrders() {
      RestJsonQuery query = query("/customers");
      query.setLookupURL(0, "/customers/{param1}/orders");
      query.setLookupJsonPath(0, "$[*]");
      query.setLookupKey(0, "id");
      return query;
   }

   private static RestJsonQuery customersOrdersItems() {
      RestJsonQuery query = customersOrders();
      query.setLookupURL(1, "/orders/{param2}/items");
      query.setLookupJsonPath(1, "$[*]");
      query.setLookupKey(1, "id");
      return query;
   }

   private static RestJsonQuery query(String suffix) {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("https://rest.test");
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix(suffix);
      query.setJsonPath("$");
      return query;
   }

   private static XTableNode run(RestJsonQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.RestJsonReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      return table;
   }

   private static String dump(XTableNode table) {
      if(table == null) {
         return null;
      }

      StringBuilder out = new StringBuilder();

      while(table.next()) {
         out.append('{');

         for(int i = 0; i < table.getColCount(); i++) {
            out.append(table.getName(i)).append('=').append(table.getObject(i)).append(';');
         }

         out.append('}');
      }

      return out.toString();
   }
}
