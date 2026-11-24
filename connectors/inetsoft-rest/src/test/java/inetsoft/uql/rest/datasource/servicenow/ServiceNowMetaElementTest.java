/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.rest.datasource.servicenow;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.EndpointJsonQueryRunner;
import inetsoft.uql.rest.RestDataIteratorStrategy;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.lookup.LookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ServiceNow Change Management API appends a trailing {@code {"__meta": {...}}} element to
 * the {@code result} array. It must not become a row or a {@code __meta.*} column. Uses the real
 * endpoints.json and the real endpoint runner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ServiceNowMetaElementTest {
   private static final String META =
      "{\"__meta\":{\"encoded_query\":\"\",\"paging\":{\"limit\":1000}}}";

   private static String record(int i) {
      return "{\"number\":\"CHG" + i + "\",\"state\":\"new\"}";
   }

   private static String result(int records, boolean meta) {
      StringBuilder items = new StringBuilder();

      for(int i = 1; i <= records; i++) {
         items.append(i > 1 ? "," : "").append(record(i));
      }

      if(meta) {
         items.append(records > 0 ? "," : "").append(META);
      }

      return "{\"result\":[" + items + "]}";
   }

   @Test
   void twoRecordsAndMetaGiveTwoRows() throws Exception {
      for(String endpoint : List.of("Normal Changes", "Emergency Changes", "Standard Changes",
                                    "Change Templates", "Change Tasks"))
      {
         Result r = run(query(endpoint), result(2, true));
         assertEquals(2, r.rows, endpoint + " " + r.columns);
         assertNoMeta(r);
      }
   }

   @Test
   void oneRecordAndMetaGiveOneRow() throws Exception {
      Result r = run(query("Normal Changes"), result(1, true));
      assertEquals(1, r.rows);
      assertNoMeta(r);
   }

   @Test
   void explicitJsonPathAndExpandedVariantsAreCovered() throws Exception {
      for(String path : List.of("$.result", "$.result[*]")) {
         for(boolean expanded : List.of(false, true)) {
            ServiceNowQuery query = query("Normal Changes");
            query.setJsonPath(path);
            query.setExpanded(expanded);
            Result r = run(query, result(2, true));
            assertEquals(2, r.rows, path + " expanded=" + expanded + " " + r.columns);
            assertNoMeta(r);
         }
      }
   }

   @Test
   void recordCarryingMetaBesideOtherKeysIsKept() throws Exception {
      Result r = run(query("Normal Changes"),
                     "{\"result\":[" + record(1) + ",{\"number\":\"CHG2\",\"__meta\":{\"a\":1}}," +
                        META + "]}");
      assertEquals(2, r.rows, r.columns.toString());
      assertTrue(r.cells.contains("CHG2"), r.cells.toString());
   }

   @Test
   void responseWithoutMetaIsUnchanged() throws Exception {
      Result r = run(query("Normal Changes"), result(3, false));
      assertEquals(3, r.rows);
      assertNoMeta(r);
   }

   @Test
   void customEndpointWithoutTemplateStillDropsMeta() throws Exception {
      ServiceNowQuery query = new ServiceNowQuery();
      query.setEndpoint("CUSTOM");
      query.setCustomEndpt("/sn_chg_rest/change");
      assertTrue(query.resolveExcludedJsonPaths().stream().anyMatch(p -> p.contains("__meta")),
                 query.resolveExcludedJsonPaths().toString());
   }

   private static void assertNoMeta(Result r) {
      assertFalse(r.columns.stream().anyMatch(c -> c.contains("__meta")), r.columns.toString());
   }

   private static ServiceNowQuery query(String endpoint) {
      ServiceNowQuery query = new ServiceNowQuery();
      query.setEndpoint(endpoint);
      return query;
   }

   private static Result run(ServiceNowQuery query, String body) throws Exception {
      JsonTransformer transformer = new JsonTransformer();
      XTableNode table = new EndpointJsonQueryRunner(
         query, q -> new OnePage(transformer, body), new LookupService(), transformer).run();
      Result r = new Result();

      if(table == null) {
         return r;
      }

      while(table.next()) {
         r.rows++;

         if(r.columns.isEmpty()) {
            for(int i = 0; i < table.getColCount(); i++) {
               r.columns.add(table.getName(i));
            }
         }

         for(int i = 0; i < table.getColCount(); i++) {
            r.cells.add(table.getObject(i));
         }
      }

      return r;
   }

   private static final class Result {
      final List<String> columns = new ArrayList<>();
      final List<Object> cells = new ArrayList<>();
      int rows;
   }

   private static final class OnePage implements RestDataIteratorStrategy<Object> {
      OnePage(JsonTransformer transformer, String body) {
         try {
            this.json = transformer.transform(
               new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      }

      @Override
      public boolean hasNext() {
         return !served;
      }

      @Override
      public Object next() {
         served = true;
         return json;
      }

      @Override
      public void close() {
      }

      private final Object json;
      private boolean served;
   }
}
