/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.json.JsonTransformer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pages of the ServiceNow Change Management API (/sn_chg_rest/): {@code sysparm_offset} is the
 * 1-based position of the first record, a list (changes, templates) ends its {@code result} with
 * a {@code {"__meta": ...}} element, a task or CI list does not. Runs the real endpoints.json,
 * pagination strategy and endpoint runner against a model of that server.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ServiceNowChangeApiPagingTest {
   private static final String META = "{\"__meta\":{\"paging\":{\"limit\":1000}}}";

   /** What the server answers for an offset after the last record. */
   private enum PastEnd {
      EMPTY,
      REPEAT_LAST
   }

   @Test
   void listWithTwoRecordsGivesTwoRowsAndOneRequest() throws Exception {
      for(String endpoint : List.of("Normal Changes", "Emergency Changes", "Standard Changes",
                                    "Change Templates"))
      {
         for(PastEnd pastEnd : PastEnd.values()) {
            for(int maxRows : new int[] { 100, 300 }) {
               Run r = run(endpoint, 2, true, pastEnd, maxRows);
               String where = endpoint + " " + pastEnd + " maxRows " + maxRows;
               assertEquals(2, r.rows.size(), where);
               assertEquals(List.of("1"), r.offsets, where);
               assertFalse(r.capped, where);
            }
         }
      }
   }

   @Test
   void tasksWithoutMetaGiveEachTaskOnce() throws Exception {
      for(PastEnd pastEnd : PastEnd.values()) {
         Run r = run("Change Tasks", 3, false, pastEnd, 100);
         assertEquals(3, r.rows.size(), pastEnd.toString());
         assertEquals(List.of("1"), r.offsets, pastEnd.toString());
         assertFalse(r.capped);
      }
   }

   @Test
   void oneConfigurationItemGivesOneRow() throws Exception {
      for(PastEnd pastEnd : PastEnd.values()) {
         Run r = run("Change Cls", 1, false, pastEnd, 100);
         assertEquals(1, r.rows.size(), pastEnd.toString());
         assertEquals(List.of("1"), r.offsets, pastEnd.toString());
      }
   }

   @Test
   void listWithOnlyMetaGivesNoRows() throws Exception {
      Run r = run("Normal Changes", 0, true, PastEnd.EMPTY, 100);
      assertEquals(0, r.rows.size());
      assertEquals(List.of("1"), r.offsets);
      assertFalse(r.capped);
   }

   @Test
   void fullPageIsFollowedByTheNextOffset() throws Exception {
      // 1000 records fill the page, so the next page is asked for at offset 1001
      Run r = run("Normal Changes", 1000, true, PastEnd.EMPTY, 0);
      assertEquals(1000, r.rows.size());
      assertEquals(List.of("1", "1001"), r.offsets);
      assertFalse(r.capped);
   }

   @Test
   void morePagesAreReadAtTheirOffsets() throws Exception {
      Run r = run("Change Tasks", 2300, false, PastEnd.EMPTY, 0);
      assertEquals(2300, r.rows.size());
      assertEquals(List.of("1", "1001", "2001"), r.offsets);
      assertEquals(2300, new HashSet<>(r.ids).size(), "each record once");
      assertFalse(r.capped);
   }

   @Test
   void recordCountPathCountsEverythingButTheMetaElement() throws Exception {
      JsonTransformer transformer = new JsonTransformer();
      String path = ServiceNowQuery.CHANGE_API_RECORD_COUNT_PATH;
      assertEquals(2, count(transformer, path, "{\"result\":[{\"a\":1},{\"a\":2}," + META + "]}"));
      assertEquals(2, count(transformer, path, "{\"result\":[{\"a\":1},{\"a\":2}]}"));
      assertEquals(1, count(transformer, path, "{\"result\":[{\"a\":1}," + META + "]}"));
      assertEquals(1, count(transformer, path, "{\"result\":[{\"a\":1}]}"));
      assertEquals(0, count(transformer, path, "{\"result\":[" + META + "]}"));
      assertEquals(0, count(transformer, path, "{\"result\":[]}"));
      // a record that carries __meta beside other keys is a record
      assertEquals(2, count(transformer, path,
                            "{\"result\":[{\"a\":1},{\"a\":2,\"__meta\":{}}," + META + "]}"));
   }

   @Test
   void tableApiEndpointsKeepZeroBasedPaging() throws Exception {
      ServiceNowQuery query = new ServiceNowQuery();
      query.setDataSource(new ServiceNowDataSource());
      query.setEndpoint("Table Records");
      assertEquals(0, query.getPaginationSpec().getFirstPageIndex());
      assertFalse(query.getPaginationSpec().isStopOnShortPage());
      assertEquals("$.result.length()", query.getPaginationSpec().getRecordCountPath());
   }

   private static int count(JsonTransformer transformer, String path, String body)
      throws Exception
   {
      Object json = transformer.transform(
         new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
      Object result = transformer.transform(json, path);
      return ((Collection<?>) result).size();
   }

   private static Run run(String endpoint, int records, boolean meta, PastEnd pastEnd,
                          int maxRows)
      throws Exception
   {
      ServiceNowDataSource ds = new ServiceNowDataSource();
      ds.setURL("http://host");
      ServiceNowQuery query = ReplaySupport.query(
         ds, new ServiceNowQuery(), endpoint, "$.result[*]", Map.of("Change ID", "c1", "Association Type", "affected"));
      query.setExpanded(true);
      query.setMaxRows(maxRows);

      Run run = new Run();
      IHttpHandler handler = new IHttpHandler() {
         @Override
         public HttpResponse executeRequest(RestRequest request) {
            String offset = request.queryParameters().get("sysparm_offset");
            run.offsets.add(offset);

            if(run.offsets.size() > MAX_REQUESTS) {
               run.capped = true;
               return new TestHttpResponse("{\"result\":[]}");
            }

            return new TestHttpResponse(page(Integer.parseInt(offset), records, meta, pastEnd));
         }

         @Override
         public void close() {
         }
      };

      XTableNode table =
         new ScriptedHttpHandler.Runtime(handler).runQuery(query, new VariableTable());

      if(table != null) {
         run.rows.addAll(ReplaySupport.rows(table));
      }

      for(Map<String, Object> row : run.rows) {
         run.ids.add(String.valueOf(row.get("id")));
      }

      return run;
   }

   /** The server: offset is the 1-based position of the first record; 0 reads like 1. */
   private static String page(int offset, int records, boolean meta, PastEnd pastEnd) {
      int start = Math.max(offset, 1) - 1;
      List<Integer> ids = new ArrayList<>();

      for(int i = start; i < Math.min(records, start + 1000); i++) {
         ids.add(i + 1);
      }

      if(ids.isEmpty() && records > 0 && pastEnd == PastEnd.REPEAT_LAST) {
         ids.add(records);
      }

      List<String> items = new ArrayList<>();

      for(int id : ids) {
         items.add("{\"id\":" + id + "}");
      }

      if(meta) {
         items.add(META);
      }

      return "{\"result\":[" + String.join(",", items) + "]}";
   }

   private static final int MAX_REQUESTS = 60;

   private static final class Run {
      final List<String> offsets = new ArrayList<>();
      final List<Map<String, Object>> rows = new ArrayList<>();
      final List<String> ids = new ArrayList<>();
      boolean capped;
   }
}
