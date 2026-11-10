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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.schema.DoubleType;
import inetsoft.uql.schema.LongType;
import inetsoft.uql.schema.StringType;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, ODataRuntimeTests.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class ODataRuntimeTests {
   private ODataDataSource dataSource;
   private ODataRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

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

   @Test
   void getOutputColumnsReturnsScalarPropertiesButNotNavigationProperties() throws Exception {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml(readXml("getOutputColumns.metadata.xml"))));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      XTypeNode[] columns = query.getOutputColumns();

      assertEquals(3, columns.length);
      assertEquals("ID", columns[0].getName());
      assertEquals("Name", columns[1].getName());
      assertEquals("Price", columns[2].getName());
      assertTrue(Arrays.stream(columns).noneMatch(c -> "Category".equals(c.getName())));

      // Edm.Int32 -> LongType, Edm.String -> StringType (default), Edm.Double -> DoubleType
      assertInstanceOf(LongType.class, columns[0]);
      assertInstanceOf(StringType.class, columns[1]);
      assertInstanceOf(DoubleType.class, columns[2]);

      verify(exactly(1), getRequestedFor(urlPathEqualTo("/V4/OData/OData.svc/%24metadata")));
   }

   @Test
   void getOutputColumnsDoesNotThrowWhenMetadataRequestFails() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(aResponse().withStatus(500)));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      XTypeNode[] columns = query.getOutputColumns();

      assertNotNull(columns);
      assertEquals(0, columns.length);
   }

   @Test
   void getOutputColumnsDoesNotThrowWhenMetadataIsMalformed() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(okXml("this is not well-formed xml <edmx:Edmx>")));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      XTypeNode[] columns = query.getOutputColumns();

      assertNotNull(columns);
      assertEquals(0, columns.length);
   }

   @Test
   void getFunctionsDoesNotThrowWhenMetadataRequestFails() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/%24metadata"))
                 .willReturn(aResponse().withStatus(500)));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      String[][] functionLabels = query.getFunctions();

      assertNotNull(functionLabels);
      assertEquals(1, functionLabels.length);
      assertArrayEquals(new String[]{ " ", "" }, functionLabels[0]);
   }

   // An entity with a stream property: with minimal metadata the service sends only the media
   // etag, which Olingo cannot bind ("Null link provided"); full metadata adds the media link.
   private static final String STREAM_ROWS_MINIMAL =
      "{\"value\":[{\"PersonID\":0,\"Phone\":\"555\",\"Photo@odata.mediaEtag\":\"W/\\\"1\\\"\"}]}";
   private static final String STREAM_ROWS_FULL =
      "{\"value\":[{\"@odata.id\":\"PersonDetails(0)\",\"PersonID\":0,\"Phone\":\"555\"," +
      "\"Photo@odata.mediaEtag\":\"W/\\\"1\\\"\"," +
      "\"Photo@odata.mediaEditLink\":\"PersonDetails(0)/Photo\"}]}";

   private void stubStreamEntitySet(String path, String minimal, String full) {
      stubFor(get(urlPathEqualTo(path))
                 .atPriority(5)
                 .willReturn(okJson(minimal)));
      stubFor(get(urlPathEqualTo(path))
                 .atPriority(1)
                 .withHeader("Accept", containing("odata.metadata=full"))
                 .willReturn(okJson(full)));
   }

   @Test
   void shouldReadEntitiesWithStreamPropertyByRequestingFullMetadata() {
      dataSource.setODataVersion("4.0");
      stubStreamEntitySet("/V4/OData/OData.svc/PersonDetails", STREAM_ROWS_MINIMAL,
                          STREAM_ROWS_FULL);

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("PersonDetails");

      XNodeTableLens table = getTable(query);

      try {
         assertEquals(2, getData(table).length);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void shouldRequestFullMetadataForFollowingPages(WireMockRuntimeInfo info) {
      dataSource.setODataVersion("4.0");
      String base = info.getHttpBaseUrl() + "/V4/OData/OData.svc/PersonDetails";
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/PersonDetails"))
                 .atPriority(5)
                 .willReturn(okJson(STREAM_ROWS_MINIMAL)));
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/PersonDetails"))
                 .atPriority(1)
                 .withHeader("Accept", containing("odata.metadata=full"))
                 .withQueryParam("page", absent())
                 .willReturn(okJson(STREAM_ROWS_FULL.replace("{\"value\"",
                    "{\"@odata.nextLink\":\"" + base + "?page=2\",\"value\""))));
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/PersonDetails"))
                 .atPriority(1)
                 .withHeader("Accept", containing("odata.metadata=full"))
                 .withQueryParam("page", equalTo("2"))
                 .willReturn(okJson(STREAM_ROWS_FULL)));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("PersonDetails");

      XNodeTableLens table = getTable(query);

      try {
         assertEquals(3, getData(table).length);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void shouldRaiseSingleLineErrorWhenRequestFails() {
      stubFor(get(urlPathEqualTo("/V4/OData/OData.svc/Products"))
                 .willReturn(aResponse().withStatus(500)));

      ODataQuery query = new ODataQuery();
      query.setDataSource(dataSource);
      query.setName("OData Test Query");
      query.setEntity("Products");

      RuntimeException ex = assertThrows(
         RuntimeException.class, () -> runtime.runQuery(query, new VariableTable()));

      assertTrue(ex.getMessage().startsWith("Failed to execute OData query: OData Test Query"),
                 ex.getMessage());
      assertTrue(ex.getMessage().lines().count() == 1, ex.getMessage());
   }

   @Test
   void singleLineJoinsTheLinesOfAMessage() {
      assertEquals("a b c", ODataRuntime.singleLine("a\n  b \r\n\r\nc"));
   }

   private String readXml(String file) throws IOException {
      return readJson(file);
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

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD)).thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false)).thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}