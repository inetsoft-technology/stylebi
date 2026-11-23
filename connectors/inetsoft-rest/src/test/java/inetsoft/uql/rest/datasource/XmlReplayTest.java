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
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.rest.xml.RestXMLDataSource;
import inetsoft.uql.rest.xml.RestXMLQuery;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobDataSource;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs an XML query through the real runtime, XSLT transformer and pagination strategies, with the
 * HTTP responses scripted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class XmlReplayTest {
   @Test
   void anUnpagedQueryReturnsOneRowPerSelectedElement() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("xml.test",
         "<root><item><id>1</id><name>a</name></item><item><id>2</id><name>b</name></item></root>");

      List<Map<String, Object>> rows = run(query("/root/item"), http);

      assertEquals(2, rows.size(), rows.toString());
      assertEquals(List.of(1, 2), column(rows, "item.id"), rows.toString());
      assertEquals(List.of("a", "b"), column(rows, "item.name"), rows.toString());
      assertEquals(1, http.requests().size(), http.requests().toString());
   }

   @Test
   void pageCountPaginationReadsEveryPageTheDocumentAnnounces() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("xml.test",
         "<root><pages>3</pages><item><id>1</id></item></root>",
         "<root><pages>3</pages><item><id>2</id></item></root>",
         "<root><pages>3</pages><item><id>3</id></item></root>");
      RestXMLQuery query = query("/root/item", "https://xml.test/items?p={{page}}");
      query.setPaginationType(PaginationType.PAGE_COUNT);
      query.setPageCountXpathValue("/root/pages");
      query.setPageNumberURLVariableValue("page");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(List.of(1, 2, 3), column(rows, "item.id"), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(http.requests().get(1).contains("p=2"), http.requests().toString());
      assertTrue(http.requests().get(2).contains("p=3"), http.requests().toString());
      assertNotEquals(http.requests().get(1), http.requests().get(2));
   }

   @Test
   void azureBlobContainersAreReadFollowingTheNextMarker() throws Exception {
      // ITERATION paging in XML: the marker in the response is sent back as the "marker" parameter
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("blob.core.windows.net",
         "<EnumerationResults><Containers><Container><Name>a</Name></Container></Containers>" +
            "<NextMarker>m1</NextMarker></EnumerationResults>",
         "<EnumerationResults><Containers><Container><Name>b</Name></Container></Containers>" +
            "<NextMarker/></EnumerationResults>");
      AzureBlobDataSource dataSource = new AzureBlobDataSource();
      dataSource.setAccountName("acct");
      dataSource.setStorageAccountKey("a2V5");
      AzureBlobQuery query = new AzureBlobQuery();
      query.setDataSource(dataSource);
      query.setEndpoint("Containers");
      query.setXpath("/EnumerationResults/Containers/Container");

      List<Map<String, Object>> rows = run(query, http);

      assertEquals(2, http.requests().size(), http.requests().toString());
      assertEquals(2, rows.size(), rows.toString());
      assertFalse(http.requests().get(0).contains("marker="), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("marker=m1"), http.requests().get(1));
   }

   @Test
   void anXmlFileIsReadWithoutAnyRequest(@TempDir Path dir) throws Exception {
      Path file = dir.resolve("data.xml");
      Files.writeString(file, "<root><item><id>1</id></item><item><id>2</id></item></root>",
                        StandardCharsets.UTF_8);
      ScriptedHttpHandler http = new ScriptedHttpHandler();

      List<Map<String, Object>> rows = run(query("/root/item", file.toUri().toString()), http);

      assertEquals(List.of(1, 2), column(rows, "item.id"), rows.toString());
      assertTrue(http.requests().isEmpty(), http.requests().toString());
   }

   private static RestXMLQuery query(String xpath) {
      return query(xpath, "https://xml.test/items");
   }

   private static RestXMLQuery query(String xpath, String url) {
      RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL(url);
      RestXMLQuery query = new RestXMLQuery();
      query.setDataSource(dataSource);
      query.setXpath(xpath);
      return query;
   }

   private static List<Map<String, Object>> run(RestXMLQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.XmlReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      assertNotNull(table, "the query returned no table; see the log for the error");
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

   private static List<Object> column(List<Map<String, Object>> rows, String name) {
      List<Object> values = new ArrayList<>();
      rows.forEach(r -> values.add(r.get(name)));
      return values;
   }
}
