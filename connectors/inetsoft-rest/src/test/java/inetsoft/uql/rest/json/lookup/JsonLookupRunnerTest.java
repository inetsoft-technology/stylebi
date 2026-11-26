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
package inetsoft.uql.rest.json.lookup;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs JSON lookups (endpoint and custom REST) against scripted responses: an entity without
 * the lookup key (Bug #81925) and a parent field with the lookup's name (Bug #81927).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class JsonLookupRunnerTest {
   // Bug #81925
   @Test
   void customRowWithoutTheKeySkipsOnlyItsLookup() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"name\":\"no id\"},{\"id\":\"c2\"}]")
         .script("/customers/c2/orders", "[{\"id\":\"o2\"}]");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals("no id", rows.get(0).get("name"));
      assertNull(rows.get(0).get("lookup1.id"));
      assertEquals("c2", rows.get(1).get("id"));
      assertEquals("o2", rows.get(1).get("lookup1.id"));
      assertEquals(List.of("/customers", "/customers/c2/orders"), factory.requests);
   }

   // Bug #81925
   @Test
   void customKeylessRowAtSecondLevelIsSkipped() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\"}]")
         .script("/customers/c1/orders", "[{\"name\":\"no id\"},{\"id\":\"o2\"}]")
         .script("/orders/o2/items", "[{\"item\":\"i2\"}]");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id",
                                              "/orders/{param2}/items", "id");

      final XTableNode table = runCustom(query, factory);

      assertNotNull(table);
      assertTrue(dump(rows(table)).contains("i2"), dump(rows(table)));
      assertTrue(factory.requests.stream().noneMatch(s -> s.contains("null")), factory.requests.toString());
   }

   // Bug #81925: an optional parameter no longer requests the unfiltered list for a keyless row
   @Test
   void customOptionalParamKeylessRowGetsNoUnfilteredData() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"name\":\"no id\"},{\"id\":\"c2\"}]")
         .script("/orders", "[{\"oid\":\"ALL1\"},{\"oid\":\"ALL2\"}]")
         .script("/orders?customer=c2", "[{\"oid\":\"O2\"}]");
      final RestJsonQuery query = customQuery("/customers", "/orders?customer={param1?}", "id");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals("no id", rows.get(0).get("name"));
      assertNull(rows.get(0).get("lookup1.oid"));
      assertEquals("O2", rows.get(1).get("lookup1.oid"));
      assertFalse(factory.requests.contains("/orders"), factory.requests.toString());
   }

   // guard: an empty key with a URL that doesn't use the parameter still looks up every row
   @Test
   void customEmptyKeyWithoutParamStillLooksUp() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\"},{\"id\":\"c2\"}]")
         .script("/settings", "{\"tz\":\"UTC\"}");
      final RestJsonQuery query = customQuery("/customers", "/settings", "");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals("UTC", rows.get(0).get("lookup1.tz"));
      assertEquals("UTC", rows.get(1).get("lookup1.tz"));
      assertEquals(List.of("/customers", "/settings", "/settings"), factory.requests);
   }

   // guard: an empty key at the second level reuses the first level's parameter
   @Test
   void customSecondLevelEmptyKeyReusesParam1() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\"}]")
         .script("/customers/c1/orders", "[{\"id\":\"o1\"}]")
         .script("/customers/c1/profile", "{\"tier\":\"gold\"}");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id",
                                              "/customers/{param1}/profile", "");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(1, rows.size(), rows.toString());
      assertEquals("gold", rows.get(0).get("lookup1.lookup2.tier"));
   }

   // guard: a second-level key that the URL doesn't use doesn't skip the lookup
   @Test
   void customSecondLevelKeyNotUsedByUrlStillLooksUp() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\"}]")
         .script("/customers/c1/orders", "[{\"name\":\"no id\"},{\"id\":\"o1\"}]")
         .script("/customers/c1/profile", "{\"tier\":\"gold\"}");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id",
                                              "/customers/{param1}/profile", "id");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals("gold", rows.get(0).get("lookup1.lookup2.tier"));
      assertEquals("gold", rows.get(1).get("lookup1.lookup2.tier"));
      assertEquals(2, Collections.frequency(factory.requests, "/customers/c1/profile"));
   }

   // Bug #81927
   @Test
   void endpointLookupKeepsTheParentFieldWithTheSameName() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\",\"Customer Orders\":\"own value\"}]")
         .script("/customers/c1/orders", "[{\"id\":\"o1\"}]");

      final List<Map<String, Object>> rows = rows(runEndpoint(factory));

      assertEquals(1, rows.size(), rows.toString());
      assertEquals("own value", rows.get(0).get("Customer Orders (Field)"));
      assertEquals("o1", rows.get(0).get("Customer Orders.id"));
   }

   // Bug #81927
   @Test
   void customLookupKeepsAParentFieldNamedLookup1() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\",\"lookup1\":\"own1\"}]")
         .script("/customers/c1/orders", "[{\"id\":\"o1\"}]");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id");

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(1, rows.size(), rows.toString());
      assertEquals("own1", rows.get(0).get("lookup1 (Field)"));
      assertEquals("o1", rows.get(0).get("lookup1.id"));
   }

   // Bug #81925 x #81927: a row without the key moves its field the same way
   @Test
   void skippedLookupStillMovesTheCollidingField() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"Customer Orders\":\"x\"},{\"id\":\"c2\",\"Customer Orders\":\"y\"}]")
         .script("/customers/c2/orders", "[{\"id\":\"o2\"}]");

      final List<Map<String, Object>> rows = rows(runEndpoint(factory));

      assertEquals(2, rows.size(), rows.toString());
      assertEquals("x", rows.get(0).get("Customer Orders (Field)"));
      assertEquals("y", rows.get(1).get("Customer Orders (Field)"));
      assertEquals("o2", rows.get(1).get("Customer Orders.id"));
      assertFalse(rows.get(0).containsKey("Customer Orders"), rows.toString());
   }

   // Bug #81927: an entity past the row limit gets no lookup but moves its field the same way
   @Test
   void rowCapSkippedEntityStillMovesTheCollidingField() {
      // the nested order list is capped at the row limit; the table doesn't emit the second
      // order's row, so check the parsed entity that the lookup skipped
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\"}]")
         .script("/customers/c1/orders",
                 "[{\"id\":\"o1\",\"lookup2\":\"own1\"},{\"id\":\"o2\",\"lookup2\":\"own2\"}]")
         .script("/orders/o1/items", "[{\"item\":\"i1\"}]")
         .script("/orders/o2/items", "[{\"item\":\"i2\"}]");
      final RestJsonQuery query = customQuery("/customers", "/customers/{param1}/orders", "id",
                                              "/orders/{param2}/items", "id");
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(runCustom(query, factory));

      assertEquals(List.of("/customers", "/customers/c1/orders", "/orders/o1/items"), factory.requests);
      assertEquals(1, rows.size(), rows.toString());
      assertEquals("own1", rows.get(0).get("lookup1.lookup2 (Field)"));
      assertEquals("i1", rows.get(0).get("lookup1.lookup2.item"));

      final Object skippedOrder = JsonTransformer.getJsonProvider()
         .getArrayIndex(factory.parsed.get("/customers/c1/orders"), 1);
      assertEquals(Map.of("id", "o2", "lookup2 (Field)", "own2"), new HashMap<>((Map<?, ?>) skippedOrder));
   }

   // guard: the output without a collision is unchanged
   @Test
   void noCollisionOutputIsUnchanged() {
      final FakeFactory factory = new FakeFactory()
         .script("/customers", "[{\"id\":\"c1\",\"Other\":\"own value\"}]")
         .script("/customers/c1/orders", "[{\"id\":\"o1\"}]");

      assertEquals("{id=c1;Other=own value;Customer Orders.id=o1;}", dump(rows(runEndpoint(factory))));
   }

   // Bug #81927: the rename is logged once per query, not once per entity
   @Test
   void collisionIsWarnedOncePerQuery() {
      final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
         org.slf4j.LoggerFactory.getLogger(RestJsonQueryRunner.class);
      final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
         new ch.qos.logback.core.read.ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         final String customers =
            "[{\"id\":\"c1\",\"Customer Orders\":\"x\"},{\"id\":\"c2\",\"Customer Orders\":\"y\"}]";
         final List<Map<String, Object>> rows = rows(runEndpoint(new FakeFactory().script("/customers", customers)));

         assertEquals(2, rows.size(), rows.toString());
         assertEquals(1, collisionWarnings(appender), appender.list.toString());

         rows(runEndpoint(new FakeFactory().script("/customers", customers)));
         assertEquals(2, collisionWarnings(appender), appender.list.toString());
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   private static long collisionWarnings(
      ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender)
   {
      return appender.list.stream()
         .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
         .filter(e -> e.getFormattedMessage().contains("has the name of a lookup"))
         .count();
   }

   private static XTableNode runCustom(RestJsonQuery query, FakeFactory factory) {
      return new RestJsonQueryRunner(query, factory, new LookupService(), new JsonTransformer()).run();
   }

   private static XTableNode runEndpoint(FakeFactory factory) {
      final TestQuery query = new TestQuery();
      query.setDataSource(new RestJsonDataSource());
      query.setEndpoint("Customers");
      query.setExpandTop(false);

      final JsonLookupQuery customerOrders = new JsonLookupQuery();
      customerOrders.setLookupEndpoint(query.getEndpointMap().get("Customers").getLookups().get(0));
      customerOrders.setExpandArrays(true);
      query.setLookupQueries(Collections.singletonList(customerOrders));

      return new EndpointJsonQueryRunner(query, factory, new LookupService(), new JsonTransformer()).run();
   }

   /**
    * @param lookups pairs of lookup URL and key, one per level.
    */
   private static RestJsonQuery customQuery(String suffix, String... lookups) {
      final RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(new RestJsonDataSource());
      query.setSuffix(suffix);
      query.setJsonPath("$");
      query.setExpanded(true);
      query.setExpandTop(false);

      for(int i = 0; i < lookups.length / 2; i++) {
         query.setLookupURL(i, lookups[i * 2]);
         query.setLookupJsonPath(i, "$");
         query.setLookupKey(i, lookups[i * 2 + 1]);
      }

      return query;
   }

   private static List<Map<String, Object>> rows(XTableNode table) {
      assertNotNull(table, "the query failed");
      final List<Map<String, Object>> rows = new ArrayList<>();

      while(table.next()) {
         final Map<String, Object> row = new LinkedHashMap<>();

         for(int i = 0; i < table.getColCount(); i++) {
            row.put(table.getName(i), table.getObject(i));
         }

         rows.add(row);
      }

      return rows;
   }

   private static String dump(List<Map<String, Object>> rows) {
      final StringBuilder sb = new StringBuilder();

      for(Map<String, Object> row : rows) {
         sb.append('{');
         row.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
         sb.append('}');
      }

      return sb.toString();
   }

   /**
    * Answers each request suffix with a scripted JSON body, or an empty array, and records the suffixes.
    */
   private static final class FakeFactory implements RestDataIteratorStrategyFactory<RestJsonQuery, Object> {
      FakeFactory script(String suffix, String json) {
         responses.put(suffix, json);
         return this;
      }

      @Override
      public RestDataIteratorStrategy<Object> createStrategy(RestJsonQuery query) {
         final String suffix = query.getSuffix();
         requests.add(suffix);
         final String json = responses.getOrDefault(suffix, "[]");
         final Object data = new JsonTransformer().transform(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
         parsed.put(suffix, data);
         return new OneShotStrategy(data);
      }

      private final Map<String, String> responses = new HashMap<>();
      private final List<String> requests = new ArrayList<>();
      private final Map<String, Object> parsed = new HashMap<>();
   }

   private static final class OneShotStrategy
      extends AbstractRestDataIteratorStrategy<AbstractRestQuery, InputTransformer>
   {
      OneShotStrategy(Object data) {
         super(null, null);
         this.data = data;
      }

      @Override
      public boolean hasNext() {
         return data != null;
      }

      @Override
      public Object next() {
         final Object temp = data;
         data = null;
         return temp;
      }

      @Override
      public void close() {
         // no-op
      }

      private Object data;
   }
}
