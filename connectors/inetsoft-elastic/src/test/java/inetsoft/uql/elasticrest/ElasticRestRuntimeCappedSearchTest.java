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
package inetsoft.uql.elasticrest;

import com.sun.net.httpserver.HttpServer;
import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.*;
import inetsoft.util.credential.*;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A search that returns fewer hits than the documents it matched must be disclosed to the caller
 * as a user warning, instead of silently building a partial table.
 */
class ElasticRestRuntimeCappedSearchTest {
   private static final String SOURCE_PATH = "$.hits.hits[*]._source";

   @Test
   void lowerBoundTotalWarnsEvenWhenValueEqualsHitsReturned() {
      // Elasticsearch 7 stops counting at 10000 and reports relation "gte"
      String warning = ElasticRestRuntime.getCappedSearchWarning(
         SOURCE_PATH, total(10000, "gte"), hits(10000));

      assertNotNull(warning);
      assertTrue(warning.contains("returned 10000 of at least 10000"), warning);
      assertFalse(warning.contains("\n"));
   }

   @Test
   void exactTotalAboveHitsReturnedWarns() {
      // no size in the request: 10 hits of 221 matches
      String warning = ElasticRestRuntime.getCappedSearchWarning(
         SOURCE_PATH, total(221, "eq"), hits(10));

      assertNotNull(warning);
      assertTrue(warning.contains("returned 10 of 221"), warning);
   }

   @Test
   void everyMatchReturnedDoesNotWarn() {
      assertNull(ElasticRestRuntime.getCappedSearchWarning(
         SOURCE_PATH, total(221, "eq"), hits(221)));
   }

   @Test
   void bareNumberTotalIsCompared() {
      // Elasticsearch 6, or rest_total_hits_as_int
      assertNotNull(ElasticRestRuntime.getCappedSearchWarning(SOURCE_PATH, 50, hits(10)));
      assertNull(ElasticRestRuntime.getCappedSearchWarning(SOURCE_PATH, 10, hits(10)));
   }

   @Test
   void missingTotalDoesNotWarn() {
      // track_total_hits: false
      assertNull(ElasticRestRuntime.getCappedSearchWarning(SOURCE_PATH, null, hits(10)));
      assertNull(ElasticRestRuntime.getCappedSearchWarning(
         SOURCE_PATH, Map.of("relation", "gte"), hits(10)));
   }

   @Test
   void pathsNotReadingTheHitsDoNotWarn() {
      // an aggregation query reads the buckets and returns no hits on purpose
      assertNull(ElasticRestRuntime.getCappedSearchWarning(
         "$.aggregations.by_type.buckets[*]", total(10000, "gte"), hits(0)));
      assertNull(ElasticRestRuntime.getCappedSearchWarning("$", total(221, "eq"), hits(10)));
      assertNull(ElasticRestRuntime.getCappedSearchWarning("$.hits", total(221, "eq"), hits(10)));
      assertNull(ElasticRestRuntime.getCappedSearchWarning(
         "$.hits.hitsExtra", total(221, "eq"), hits(10)));
   }

   @Test
   void hitPathSpellingsAreRecognized() {
      for(String path : List.of("$.hits.hits", "$.hits.hits[*]", "$['hits']['hits'][*]._source",
                                "$[\"hits\"][\"hits\"]", " $.hits.hits[*]._source "))
      {
         assertNotNull(ElasticRestRuntime.getCappedSearchWarning(
            path, total(221, "eq"), hits(10)), path);
      }
   }

   @Test
   void nonSearchResponseDoesNotWarn() {
      assertNull(ElasticRestRuntime.getCappedSearchWarning(SOURCE_PATH, total(221, "eq"), null));
   }

   @Nested
   class RunQuery {
      private HttpServer server;
      private String responseBody;

      @BeforeEach
      void startServer() throws Exception {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenReturn(mock(LocalPasswordCredential.class));
         ApplicationContext context = mock(ApplicationContext.class);
         when(context.getBean(CredentialService.class)).thenReturn(credentialService);
         // See ElasticCatalogTests.loadData() for why these beans are stubbed.
         PropertiesEngine propertiesEngine = mock(PropertiesEngine.class);
         when(propertiesEngine.getProperty(anyString(), anyString(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(1));
         when(context.getBean(PropertiesEngine.class)).thenReturn(propertiesEngine);
         when(context.getBean(XSwapper.class)).thenReturn(mock(XSwapper.class));
         ConfigurationContext.getContext().setApplicationContext(context);

         server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
         server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);

            try(OutputStream output = exchange.getResponseBody()) {
               output.write(body);
            }
         });
         server.start();
         CoreTool.getUserMessage(); // start from an empty thread-local
      }

      @AfterEach
      void stopServer() {
         server.stop(0);
         CoreTool.getUserMessage();
         ConfigurationContext.getContext().setApplicationContext(null);
      }

      @Test
      void cappedSearchRaisesOneWarning() {
         responseBody = searchResponse(3, "{\"value\":10000,\"relation\":\"gte\"}");
         XTableNode table = runQuery(false);

         assertNotNull(table);
         assertEquals(3, countRows(table));
         UserMessage message = CoreTool.getUserMessage();
         assertNotNull(message);
         assertEquals(ConfirmException.WARNING, message.getLevel());
         assertTrue(message.getMessage().contains("returned 3 of at least 10000"),
                    message.getMessage());
      }

      @Test
      void cappedSearchWarnsForAnExpandedTableToo() {
         responseBody = searchResponse(3, "{\"value\":221,\"relation\":\"eq\"}");
         assertNotNull(runQuery(true));

         UserMessage message = CoreTool.getUserMessage();
         assertNotNull(message);
         assertTrue(message.getMessage().contains("returned 3 of 221"), message.getMessage());
      }

      @Test
      void completeSearchRaisesNothing() {
         responseBody = searchResponse(3, "{\"value\":3,\"relation\":\"eq\"}");
         assertNotNull(runQuery(false));
         assertNull(CoreTool.getUserMessage());
      }

      private XTableNode runQuery(boolean expanded) {
         ElasticRestDataSource dataSource = new ElasticRestDataSource();
         dataSource.setURL("http://127.0.0.1:" + server.getAddress().getPort());
         ElasticRestQuery query = new ElasticRestQuery();
         query.setDataSource(dataSource);
         query.setSuffix("/events/_search?size=3");
         query.setJsonPath(SOURCE_PATH);
         query.setExpanded(expanded);
         return new ElasticRestRuntime().runQuery(query, new VariableTable());
      }

      private static int countRows(XTableNode table) {
         int rows = 0;

         while(table.next()) {
            rows++;
         }

         return rows;
      }

      private static String searchResponse(int hitCount, String total) {
         StringBuilder hits = new StringBuilder();

         for(int i = 1; i <= hitCount; i++) {
            if(i > 1) {
               hits.append(',');
            }

            hits.append("{\"_index\":\"events\",\"_id\":\"").append(i)
               .append("\",\"_source\":{\"seq\":").append(i).append("}}");
         }

         return "{\"took\":1,\"timed_out\":false,\"hits\":{\"total\":" + total +
            ",\"max_score\":1.0,\"hits\":[" + hits + "]}}";
      }
   }

   private static Map<String, Object> total(long value, String relation) {
      return Map.of("value", value, "relation", relation);
   }

   private static List<Object> hits(int count) {
      return Collections.nCopies(count, Map.of());
   }
}
