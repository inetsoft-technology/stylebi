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
package inetsoft.uql.rest.xml.lookup;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobDataSource;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobQuery;
import inetsoft.uql.rest.xml.RestXMLQueryRunner;
import inetsoft.uql.rest.xml.XMLRestDataIteratorStrategyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the Azure Blob "Containers" query with the "Blobs" lookup against scripted responses:
 * a container without a name (Bug #81925), the row limit (Bug #81926) and a container element
 * with the lookup's name (Bug #81927).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, EndpointsTestConfig.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class AzureBlobLookupRunnerTest {
   // Bug #81925
   @Test
   void aContainerWithoutANameIsSkippedAndKept() {
      final RecordingHandler handler = new RecordingHandler(
         containers("<Container><Other>x</Other></Container>", "<Container><Name>c2</Name></Container>"))
         .blobs("c2", blobs("b2"));
      final AzureBlobQuery query = query();
      query.setExpandTop(false);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertTrue(dump(rows).contains("Other=x"), dump(rows));
      assertTrue(dump(rows).contains("b2"), dump(rows));
      assertEquals(2, handler.requests.size(), handler.requests.toString());
      assertTrue(handler.requests.get(0).endsWith("/?comp=list"), handler.requests.toString());
      assertTrue(handler.requests.get(1).contains("/c2?restype=container"), handler.requests.toString());
   }

   // Bug #81926
   @Test
   void oneRowPerContainerStopsLookupsAtTheRowLimit() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(1, handler.blobRequests(), handler.requests.toString());
      assertEquals("c1", rows.get(0).get(CONTAINER + ".Name"));
      assertTrue(dump(rows.subList(0, 1)).contains("b1"), dump(rows));
      assertTrue(dump(rows.subList(0, 1)).contains("b1x"), dump(rows));
   }

   // Bug #81926
   @Test
   void exactExpandedPathStopsLookupsAtTheRowLimit() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setExpandedPath(CONTAINER);
      query.setMaxRows(1);

      rows(run(query, handler));

      assertEquals(1, handler.blobRequests(), handler.requests.toString());
   }

   // Bug #81926
   @Test
   void rowLimitTwoLooksUpTwo() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setMaxRows(2);

      rows(run(query, handler));

      assertEquals(2, handler.blobRequests(), handler.requests.toString());
   }

   // guard: in the default shape the whole page is one row, so every lookup is needed
   @Test
   void defaultShapeKeepsEveryLookupAtRowLimitOne() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(3, handler.blobRequests(), handler.requests.toString());
      assertEquals(1, rows.size(), dump(rows));
      assertTrue(dump(rows).contains("b1") && dump(rows).contains("b2") && dump(rows).contains("b3"), dump(rows));
   }

   // guard: an expanded path above the containers doesn't expand them, so every lookup is needed
   @Test
   void prefixExpandedPathKeepsEveryLookup() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setExpandedPath("EnumerationResults.Containers");
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(3, handler.blobRequests(), handler.requests.toString());
      assertTrue(dump(rows).contains("b1") && dump(rows).contains("b2") && dump(rows).contains("b3"), dump(rows));
   }

   // guard: no row limit, every container is looked up
   @Test
   void noRowLimitLooksUpEveryContainer() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setMaxRows(0);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(3, handler.blobRequests(), handler.requests.toString());
      assertTrue(dump(rows).contains("b1") && dump(rows).contains("b2") && dump(rows).contains("b3"), dump(rows));
   }

   // Bug #81926: an expanded path below the containers still gives each container its own rows
   @Test
   void deeperExpandedPathStopsLookupsAtTheRowLimit() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setExpandedPath(CONTAINER + ".Blobs.EnumerationResults.Blobs.Blob");
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(1, handler.blobRequests(), handler.requests.toString());
      assertFalse(rows.isEmpty(), dump(rows));
      assertEquals("c1", rows.get(0).get(CONTAINER + ".Name"), dump(rows));
      // the row limit keeps the first container's blobs
      assertTrue(dump(rows.subList(0, 1)).contains("b1") && dump(rows.subList(0, 1)).contains("b1x"), dump(rows));
   }

   // guard: an expanded path beside the containers doesn't expand them, so every lookup is needed
   @Test
   void unrelatedExpandedPathKeepsEveryLookup() {
      final RecordingHandler handler = threeContainers();
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setExpandedPath("EnumerationResults.NextMarker");
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(3, handler.blobRequests(), handler.requests.toString());
      assertTrue(dump(rows).contains("b1") && dump(rows).contains("b2") && dump(rows).contains("b3"), dump(rows));
   }

   // Bug #81927
   @Test
   void aLookupKeepsTheContainersOwnBlobsElement() {
      final RecordingHandler handler = new RecordingHandler(
         containers("<Container><Name>c1</Name><Blobs>own value</Blobs></Container>"))
         .blobs("c1", blobs("b1"));
      final AzureBlobQuery query = query();
      query.setExpandTop(false);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(1, rows.size(), dump(rows));
      assertEquals("own value", rows.get(0).get(CONTAINER + ".Blobs (Field)"));
      assertEquals("b1", rows.get(0).get(CONTAINER + ".Blobs.EnumerationResults.Blobs.Blob.Name"));
   }

   // Bug #81926 x #81927: a container past the row limit moves its own Blobs element too
   @Test
   void aContainerPastTheRowLimitAlsoMovesItsBlobs() {
      final RecordingHandler handler = new RecordingHandler(
         containers("<Container><Name>c1</Name><Blobs>own1</Blobs></Container>",
                    "<Container><Name>c2</Name><Blobs>own2</Blobs></Container>"))
         .blobs("c1", blobs("b1"))
         .blobs("c2", blobs("b2"));
      final AzureBlobQuery query = query();
      query.setExpandTop(false);
      query.setMaxRows(1);

      final List<Map<String, Object>> rows = rows(run(query, handler));

      assertEquals(1, handler.blobRequests(), handler.requests.toString());
      assertEquals(2, rows.size(), dump(rows));
      assertEquals("own1", rows.get(0).get(CONTAINER + ".Blobs (Field)"));
      assertEquals("own2", rows.get(1).get(CONTAINER + ".Blobs (Field)"));
      assertFalse(rows.get(1).containsKey(CONTAINER + ".Blobs"), dump(rows));
   }

   // guard: the output without a collision is unchanged
   @Test
   void noCollisionXmlOutputIsUnchanged() {
      final RecordingHandler handler = new RecordingHandler(
         containers("<Container><Name>c1</Name></Container>"))
         .blobs("c1", blobs("b1"));
      final AzureBlobQuery query = query();
      query.setExpandTop(false);

      // the root's ServiceEndpoint attribute is a column too (Bug #79489)
      assertEquals("{EnumerationResults.NextMarker=null;" +
                   "EnumerationResults.@ServiceEndpoint=https://acct.blob.core.windows.net/;" +
                   CONTAINER + ".Name=c1;" +
                   CONTAINER + ".Blobs.EnumerationResults.NextMarker=null;" +
                   CONTAINER + ".Blobs.EnumerationResults.Blobs.Blob.Name=b1;}",
                   dump(rows(run(query, handler))));
   }

   // Bug #81927: the rename is logged once per query, not once per container
   @Test
   void collisionIsWarnedOncePerQuery() {
      final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
         org.slf4j.LoggerFactory.getLogger(RestXMLQueryRunner.class);
      final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
         new ch.qos.logback.core.read.ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         final RecordingHandler handler = new RecordingHandler(
            containers("<Container><Name>c1</Name><Blobs>own1</Blobs></Container>",
                       "<Container><Name>c2</Name><Blobs>own2</Blobs></Container>"))
            .blobs("c1", blobs("b1"))
            .blobs("c2", blobs("b2"));
         final AzureBlobQuery query = query();
         query.setExpandTop(false);

         final List<Map<String, Object>> rows = rows(run(query, handler));

         assertEquals(2, rows.size(), dump(rows));
         assertEquals("own2", rows.get(1).get(CONTAINER + ".Blobs (Field)"));
         assertEquals(1, appender.list.stream()
            .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
            .filter(e -> e.getFormattedMessage().contains("has the name of a lookup"))
            .count(), appender.list.toString());
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   private static AzureBlobQuery query() {
      final AzureBlobDataSource dataSource = new AzureBlobDataSource();
      dataSource.setAccountName("acct");
      dataSource.setStorageAccountKey("a2V5");

      final AzureBlobQuery query = new AzureBlobQuery();
      query.setDataSource(dataSource);
      query.setEndpoint("Containers");
      query.setLookupEndpoint("Blobs", 0);
      return query;
   }

   private static XTableNode run(AzureBlobQuery query, RecordingHandler handler) {
      return new RestXMLQueryRunner(query, new XMLRestDataIteratorStrategyFactory(handler)).run();
   }

   private static RecordingHandler threeContainers() {
      return new RecordingHandler(
         containers("<Container><Name>c1</Name></Container>", "<Container><Name>c2</Name></Container>",
                    "<Container><Name>c3</Name></Container>"))
         .blobs("c1", blobs("b1", "b1x"))
         .blobs("c2", blobs("b2"))
         .blobs("c3", blobs("b3"));
   }

   private static String containers(String... containers) {
      return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
         "<EnumerationResults ServiceEndpoint=\"https://acct.blob.core.windows.net/\"><Containers>" +
         String.join("", containers) + "</Containers><NextMarker/></EnumerationResults>";
   }

   private static String blobs(String... names) {
      final StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><EnumerationResults><Blobs>");

      for(String name : names) {
         sb.append("<Blob><Name>").append(name).append("</Name></Blob>");
      }

      return sb.append("</Blobs><NextMarker/></EnumerationResults>").toString();
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
    * Answers the container list and the scripted blob lists, and records every URL.
    */
   private static final class RecordingHandler implements IHttpHandler {
      RecordingHandler(String containers) {
         this.containers = containers;
      }

      RecordingHandler blobs(String container, String body) {
         blobs.put(container, body);
         return this;
      }

      long blobRequests() {
         return requests.stream().filter(url -> url.contains("restype=container")).count();
      }

      @Override
      public HttpResponse executeRequest(RestRequest request) throws IOException {
         final String url;

         try {
            url = URLCreator.fromRestRequest(request).toString();
         }
         catch(Exception ex) {
            throw new IOException(ex);
         }

         requests.add(url);

         for(Map.Entry<String, String> e : blobs.entrySet()) {
            if(url.contains("/" + e.getKey() + "?restype=container")) {
               return new TestHttpResponse(e.getValue());
            }
         }

         if(url.contains("/?comp=list")) {
            return new TestHttpResponse(containers);
         }

         throw new IOException("Unexpected request: " + url);
      }

      @Override
      public void close() {
         // no-op
      }

      private final String containers;
      private final Map<String, String> blobs = new LinkedHashMap<>();
      private final List<String> requests = new ArrayList<>();
   }

   private static final String CONTAINER = "EnumerationResults.Containers.Container";
}
