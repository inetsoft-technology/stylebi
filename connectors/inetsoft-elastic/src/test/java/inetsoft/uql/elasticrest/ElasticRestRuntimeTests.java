/*
 * inetsoft-elastic - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.elasticrest;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.junit.jupiter.api.*;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@Disabled
class ElasticRestRuntimeTests {
   @Container
   static final ElasticsearchContainer container =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.9.2");
   private ElasticRestRuntime runtime = null;
   private ElasticRestDataSource dataSource = null;

   @BeforeAll
   static void loadData() throws IOException {
      try(InputStream input = ElasticRestRuntimeTests.class.getResourceAsStream("earthquakes.ndjson")) {
         assert input != null;

         try(LineIterator it = IOUtils.lineIterator(input, StandardCharsets.UTF_8)) {
            while(it.hasNext()) {
               String line = it.next();

               if(!line.trim().isEmpty()) {
                  URL url = new URL(getUrl() + "/earthquakes/_doc");
                  HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                  conn.setRequestMethod("POST");
                  conn.setRequestProperty("Content-Type", "application/json");
                  conn.setDoOutput(true);

                  try(OutputStream output = conn.getOutputStream()) {
                     output.write(line.getBytes(StandardCharsets.UTF_8));
                  }

                  int rc = conn.getResponseCode();
                  assert rc < 400;
               }
            }
         }
      }
   }

   @BeforeEach
   void createDataSource() {
      runtime = new ElasticRestRuntime();
      dataSource = new ElasticRestDataSource();
      dataSource.setURL(getUrl());
   }

   @Test
   void runQueryShouldReturnResults() {
      ElasticRestQuery query = new ElasticRestQuery();
      query.setDataSource(dataSource);
      query.setSuffix("/earthquakes/_search?q=*:*");
      query.setJsonPath("$.hits.hits[*]._source");
      XTableNode tableNode = runtime.runQuery(query, new VariableTable());

      assertNotNull(tableNode);

      assertEquals(12, tableNode.getColCount());
      assertEquals("@timestamp", tableNode.getName(0));
      assertEquals("latitude", tableNode.getName(1));
      assertEquals("longitude", tableNode.getName(2));
      assertEquals("depth", tableNode.getName(3));
      assertEquals("magnitude", tableNode.getName(4));
      assertEquals("magType", tableNode.getName(5));
      assertEquals("nbStations", tableNode.getName(6));
      assertEquals("gap", tableNode.getName(7));
      assertEquals("distance", tableNode.getName(8));
      assertEquals("rms", tableNode.getName(9));
      assertEquals("source", tableNode.getName(10));
      assertEquals("eventId", tableNode.getName(11));

      int rowCount = 0;

      while(tableNode.next()) {
         ++rowCount;
      }

      assertEquals(10, rowCount);
   }

   private static String getUrl() {
      return "http://" + container.getHost() + ":" + container.getMappedPort(9200);
   }
}