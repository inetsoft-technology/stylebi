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
package inetsoft.uql.odata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WireMockTest
class ODataRuntimeTests {
   private ODataDataSource dataSource;
   private ODataRuntime runtime;

   @BeforeEach
   void setupDataSource(WireMockRuntimeInfo info) {
      dataSource = new ODataDataSource();
      String url = info.getHttpBaseUrl() + "/V4/OData/OData.svc/";
      dataSource.setURL(url);
      dataSource.setName("OData Test");
      runtime = new ODataRuntime();
   }

   @Test
   void shouldReturnAllResultsWithNoFilter() throws Exception {
      Object[][] expected = readExpected("shouldReturnAllResultsWithNoFilter.expected.json");

      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/Products"))
                 .willReturn(okJson(readJson("shouldReturnAllResultsWithNoFilter.json"))));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void shouldReturnNoResultsWithFilter() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/Products"))
                 .withQueryParam("$filter", equalTo("ID gt 10"))
                 .willReturn(okJson(readJson("shouldReturnNoResultsWithFilter.json"))));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");
      query.setFilter("ID gt 10");

      XNodeTableLens table = getTable(query);

      try {
         Object[][] expected = { {} };
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }

      verify(getRequestedFor(urlPathEqualTo("/V4/OData/OData.svc/Products"))
                .withQueryParam("$filter", equalTo("ID gt 10")));
   }

   @Test
   void shouldReturnAllResultsWithNullValues() throws Exception {
      Object[][] expected = readExpected("shouldReturnAllResultsWithNullValues.expected.json");

      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/Components"))
                 .willReturn(okJson(readJson("shouldReturnAllResultsWithNullValues.json"))));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Components");

      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   private Object[][] readExpected(String file) throws IOException {
      try(InputStream input = getClass().getResourceAsStream(file)) {
         ObjectMapper mapper = new ObjectMapper();
         ArrayNode root = (ArrayNode) mapper.readTree(input);
         Object[][] expected = new Object[root.size()][];

         for(int i = 0; i < expected.length; i++) {
            ArrayNode array = (ArrayNode) root.get(i);
            expected[i] = mapper.treeToValue(array, Object[].class);
         }

         return expected;
      }
   }

   private String readJson(String file) throws IOException {
      try(InputStream input = getClass().getResourceAsStream(file)) {
         assert input != null;
         return IOUtils.toString(input, StandardCharsets.UTF_8);
      }
   }

   private XNodeTableLens getTable(ODataQuery query) {
      XTableNode node = runtime.runQuery(query, new VariableTable());
      assertNotNull(node);

      XNodeTableLens table = new XNodeTableLens(node);

      return table;
   }

   private Object[][] getData(XNodeTableLens table) {
      table.moreRows(XTable.EOT);
      Object[][] actual = new Object[table.getRowCount()][];

      for(int i = 0; table.moreRows(i); i++) {
         actual[i] = new Object[table.getColCount()];

         for(int j = 0; j < table.getColCount(); j++) {
            actual[i][j] = table.getObject(i, j);
         }
      }

      return actual;
   }
}