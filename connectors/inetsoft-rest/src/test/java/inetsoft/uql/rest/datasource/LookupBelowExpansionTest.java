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
import inetsoft.test.ReplaySupport;
import inetsoft.uql.rest.datasource.square.SquareDataSource;
import inetsoft.uql.rest.datasource.square.SquareQuery;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81938: a lookup whose JSON path has more array levels than the table expands is requested
 * but its column is lost (the lookup result is stringified into the parent array's cell). The
 * query says nothing. These tests pin how many array levels each combination of row path, Top Level
 * Only and Expanded Array Path expands, and that the user is told once exactly when a lookup
 * column is lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class LookupBelowExpansionTest {
   private static final String DEEP = "$.objects[*].variations[*]";
   private static final String ROW = "$.objects[*]";

   @BeforeEach
   @AfterEach
   void clearMessages() {
      CoreTool.getUserMessage();
   }

   @Test
   void rowPathRootTopLevelOnlyLosesTheDeepLookup() {
      Result r = run("$", DEEP, true, null);
      assertFalse(r.lookupColumn, r.dump);
      assertTrue(r.warned(), r.dump);
   }

   @Test
   void rowPathObjectsTopLevelOnlyLosesTheDeepLookup() {
      Result r = run("$.objects", DEEP, true, null);
      assertFalse(r.lookupColumn, r.dump);
      assertTrue(r.warned(), r.dump);
   }

   @Test
   void rowPathWildcardTopLevelOnlyLosesTheDeepLookup() {
      Result r = run(ROW, DEEP, true, null);
      assertFalse(r.lookupColumn, r.dump);
      assertTrue(r.warned(), r.dump);
      assertEquals(2, r.lookupRequests, "the lookups are still requested");
   }

   @Test
   void rowLevelLookupKeepsItsColumnWithoutAWarning() {
      for(String row : new String[] { "$", "$.objects", ROW }) {
         Result r = run(row, ROW, true, null);
         assertTrue(r.lookupColumn, row + " " + r.dump);
         assertFalse(r.warned(), row + " " + r.dump);
      }
   }

   @Test
   void topLevelOnlyOffWithoutAPathExpandsEveryLevel() {
      for(String row : new String[] { "$", "$.objects", ROW }) {
         Result r = run(row, DEEP, false, null);
         assertTrue(r.lookupColumn, row + " " + r.dump);
         assertFalse(r.warned(), row + " " + r.dump);
      }
   }

   @Test
   void expandedPathOnTheLookupArrayKeepsTheColumn() {
      Result r = run(ROW, DEEP, false, "variations");
      assertTrue(r.lookupColumn, r.dump);
      assertFalse(r.warned(), r.dump);

      r = run("$", DEEP, false, "objects.variations");
      assertTrue(r.lookupColumn, r.dump);
      assertFalse(r.warned(), r.dump);
   }

   @Test
   void expandedPathOffTheLookupArrayLosesTheColumn() {
      Result r = run(ROW, DEEP, false, "other");
      assertFalse(r.lookupColumn, r.dump);
      assertTrue(r.warned(), r.dump);
   }

   @Test
   void lookupOnANestedObjectIsNotWarnedAbout() {
      Result r = run(ROW, "$.objects[*].meta", true, null);
      assertFalse(r.warned(), r.dump);
   }

   @Test
   void unexpandedQueryIsNotWarnedAbout() {
      RestJsonQuery q = query(ROW, DEEP);
      q.setExpanded(false);
      run(q, handler());
      assertNull(CoreTool.getUserMessage());
   }

   @Test
   void theWarningIsAddedOncePerRunNotPerPageOrLookup() {
      ScriptedHttpHandler http = handler();
      RestJsonQuery q = query(ROW, DEEP);
      q.setLookupURL(1, "/inv/{param2}");
      q.setLookupJsonPath(1, DEEP);
      q.setLookupKey(1, "id");
      UserMessage m = run(q, http).message;
      assertNotNull(m);
      assertEquals(m.getMessage().indexOf("Top Level Only"),
                   m.getMessage().lastIndexOf("Top Level Only"), m.getMessage());
   }

   @Test
   void endpointQueryWithADeepLookupIsWarnedAboutOnce() {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v2/inventory/V1", "{\"counts\":[{\"quantity\":\"16\"}]}")
         .on("/v2/catalog/list", "{\"objects\":[{\"type\":\"ITEM\",\"id\":\"I1\","
             + "\"item_data\":{\"variations\":[{\"id\":\"V1\"}]}}]}");
      SquareDataSource ds = new SquareDataSource();
      ds.setAccessToken("t");
      ds.setSandbox(true);
      SquareQuery q = ReplaySupport.query(ds, new SquareQuery(), "List Catalog", "$.objects[*]",
                                          Map.of("Types", "ITEM"));
      q.setLookupEndpoint("Inventory Count", 0);
      ReplaySupport.runTable(q, http);
      UserMessage m = CoreTool.getUserMessage();
      assertNotNull(m);
      assertEquals(m.getMessage().indexOf("Top Level Only"),
                   m.getMessage().lastIndexOf("Top Level Only"), m.getMessage());
   }

   private static ScriptedHttpHandler handler() {
      return new ScriptedHttpHandler()
         .on("/inv/", "{\"count\":5}")
         .on("rest.test/catalog", "{\"objects\":[{\"id\":\"I1\",\"meta\":{\"a\":1},"
             + "\"variations\":[{\"id\":\"V1\"}]},{\"id\":\"I2\",\"meta\":{\"a\":2},"
             + "\"variations\":[{\"id\":\"V2\"}]}]}");
   }

   private static RestJsonQuery query(String rowPath, String lookupPath) {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("https://rest.test");
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix("/catalog");
      query.setJsonPath(rowPath);
      query.setLookupURL(0, "/inv/{param1}");
      query.setLookupJsonPath(0, lookupPath);
      query.setLookupKey(0, "id");
      query.setExpanded(true);
      return query;
   }

   private static Result run(String rowPath, String lookupPath, boolean topLevelOnly,
                             String expandedPath)
   {
      RestJsonQuery q = query(rowPath, lookupPath);
      q.setExpandTop(topLevelOnly);
      q.setExpandedPath(expandedPath);
      return run(q, handler());
   }

   private static Result run(RestJsonQuery q, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.RestJsonReplayRuntime(http)
         .runQuery(q, new VariableTable());
      http.assertNoFailures();
      Result r = new Result();
      StringBuilder out = new StringBuilder();

      for(int i = 0; table != null && i < table.getColCount(); i++) {
         out.append(table.getName(i)).append(' ');
         r.lookupColumn |= table.getName(i).contains("lookup1");
      }

      r.lookupRequests = (int) http.requests().stream().filter(u -> u.contains("/inv/")).count();
      r.dump = out + " requests=" + http.requests();
      r.message = CoreTool.getUserMessage();
      return r;
   }

   private static final class Result {
      boolean warned() {
         return message != null && message.getMessage() != null &&
            message.getMessage().contains("Top Level Only");
      }

      boolean lookupColumn;
      int lookupRequests;
      String dump;
      UserMessage message;
   }
}
