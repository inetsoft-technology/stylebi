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
package inetsoft.uql.rest.datasource.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.test.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.credential.LocalPasswordCredential;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Charter G12/G13/G16/G17, through the real {@link GraphQLRuntime} entry points (as {@link
 * TabularCatalogService} calls them), against a stubbed introspection endpoint. Complements {@link
 * GraphQLCatalogTest}'s pure-parsing cases with the ones meaningless without a real HTTP round
 * trip: the three introspection failure modes, that the catalog path never disturbs the data
 * source's own query state, and G17 through the real service (not just the connector's own return
 * value in isolation).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  GraphQLRuntimeCatalogTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class GraphQLRuntimeCatalogTest {
   private GraphQLDataSource dataSource;
   private GraphQLRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setupDataSource(WireMockRuntimeInfo info, TestInfo testInfo) {
      dataSource = new GraphQLDataSource();
      dataSource.setURL(info.getHttpBaseUrl() + "/graphql");
      dataSource.setName("GraphQLRuntimeCatalogTest/" + testInfo.getDisplayName());
      runtime = new GraphQLRuntime();
   }

   // ===================================================================================
   // Real round trip -- the pure-parsing result must survive going through actual HTTP/JSON
   // ===================================================================================

   @Test
   void listDatasetsReturnsDatasetsFromARealIntrospectionResponse() throws Exception {
      stubIntrospection(okIntrospectionBody());

      TabularCatalog catalog = runtime.listDatasets(dataSource);

      List<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id).toList();
      assertTrue(ids.contains("orders"));
      assertTrue(ids.contains("products"));
      assertFalse(ids.contains("shop"));
   }

   @Test
   void describeDatasetReturnsRealColumnsThroughARealIntrospectionResponse() throws Exception {
      stubIntrospection(okIntrospectionBody());

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "products");

      assertEquals("products", schema.datasetId());
      assertEquals(List.of("id", "name", "price", "sku"),
         schema.columns().stream().map(TabularColumn::name).toList());
   }

   // ===================================================================================
   // Failure classification -- G12, three distinct shapes
   // ===================================================================================

   @Test
   void listDatasetsThrowsWithAnAuthFailureMessage_httpLevelRejection() {
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(401)));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
      assertTrue(ex.getMessage().contains("401"), "must name the HTTP status: " + ex.getMessage());
   }

   @Test
   void listDatasetsThrowsADistinguishableMessage_introspectionDisabled() {
      // HTTP 200 with a GraphQL-level errors array and no usable data.__schema -- the single most
      // common production shape for "introspection is turned off", and the one most likely to be
      // mistaken for a genuine empty catalog if the implementation only checks HTTP status.
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(okJson(
         "{\"data\": null, \"errors\": [{\"message\": \"Introspection is disabled\"}]}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
      assertTrue(ex.getMessage().toLowerCase().contains("introspection"),
         "must name introspection-disabled as the likely cause: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("Introspection is disabled"),
         "must quote the server's own error text: " + ex.getMessage());
   }

   @Test
   void listDatasetsThrowsRatherThanReturningEmptyWhenIntrospectionResponseIsMalformed() {
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(okJson("this is not valid json {")));

      assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
   }

   @Test
   void listDatasetsProceedsOnAUsableSchemaEvenAlongsideANonEmptyErrorsArray() throws Exception {
      // Legal per the GraphQL spec: a 200 response MAY carry a fully usable "data" AND a
      // non-empty "errors" array at once (partial success -- e.g. an unrelated resolver's own
      // warning). The errors array's mere presence must never be treated as fatal on its own --
      // only an unusable data.__schema decides that (I2).
      stubIntrospection(withUnrelatedResolverWarning(okIntrospectionBody()));

      TabularCatalog catalog = runtime.listDatasets(dataSource);

      List<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id).toList();
      assertTrue(ids.contains("orders"), "a usable __schema must still be used: " + ids);
   }

   @Test
   void listDatasetsQuotesTheServersOwnErrorWhenANonSuccessStatusCarriesAParseableErrorsBody() {
      // Reproduces a real managed GraphQL gateway's query-depth-limit rejection (found live
      // against countries.trevorblades.com during P4 verification): HTTP 413, body is a
      // perfectly parseable GraphQL errors array. The server's own message must reach the
      // exception text rather than being discarded in favor of a bare status code.
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(413)
         .withHeader("Content-Type", "application/json")
         .withBody("{\"errors\":[{\"message\":\"Query depth limit exceeded\"," +
            "\"extensions\":{\"code\":\"GCDN_QUERY_DEPTH_LIMIT\"}}]}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
      assertTrue(ex.getMessage().contains("413"), "must still name the HTTP status: " +
         ex.getMessage());
      assertTrue(ex.getMessage().contains("Query depth limit exceeded"),
         "must quote the server's own error text rather than a generic message: " +
         ex.getMessage());
   }

   @Test
   void listDatasetsThrowsAConnectivityMessage_unreachable() {
      // Point at a URL WireMock is not listening on -- the JVM connection itself fails, never
      // reaching the HTTP-status or GraphQL-errors classification branches at all.
      dataSource.setURL("http://127.0.0.1:1/graphql");

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));
      assertTrue(ex.getMessage().contains("connect"),
         "must read as a connectivity failure, not a generic message: " + ex.getMessage());
   }

   @Test
   void theThreeFailureMessagesAreMutuallyDistinguishable() {
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(aResponse().withStatus(403)));
      String authFailureMessage =
         assertThrows(Exception.class, () -> runtime.listDatasets(dataSource)).getMessage();

      resetAllRequests();
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(okJson(
         "{\"data\": null, \"errors\": [{\"message\": \"nope\"}]}")));
      String disabledMessage =
         assertThrows(Exception.class, () -> runtime.listDatasets(dataSource)).getMessage();

      dataSource.setURL("http://127.0.0.1:1/graphql");
      String unreachableMessage =
         assertThrows(Exception.class, () -> runtime.listDatasets(dataSource)).getMessage();

      assertNotEquals(authFailureMessage, disabledMessage);
      assertNotEquals(authFailureMessage, unreachableMessage);
      assertNotEquals(disabledMessage, unreachableMessage);
   }

   // ===================================================================================
   // The catalog path must never disturb the data source's own query state -- G13, G16
   // ===================================================================================

   @Test
   void catalogCallsNeverInvokeAddParams_httpParametersUnchanged() throws Exception {
      // A realistic pre-existing configuration, as a user would actually have it set up for real
      // data querying -- not an empty/default array, which would pass even an implementation that
      // clobbers an unconfigured data source.
      HttpParameter existing = HttpParameter.builder()
         .type(HttpParameter.ParameterType.HEADER).name("Authorization").value("Bearer abc123")
         .build();
      dataSource.setQueryHttpParameters(new HttpParameter[] { existing });
      HttpParameter[] before = dataSource.getQueryHttpParameters().clone();

      stubIntrospection(okIntrospectionBody());
      runtime.listDatasets(dataSource);
      runtime.describeDataset(dataSource, "products");

      assertArrayEquals(before, dataSource.getQueryHttpParameters(),
         "a catalog call must never call GraphQLRuntime#addParams / " +
         "setQueryHttpParameters -- that would silently corrupt the user's configured query");
   }

   // G17 through the real TabularCatalogService lives in a SEPARATE class,
   // GraphQLCatalogServiceListTablesTest, declared in TabularCatalogService's own package
   // (inetsoft.web.wiz.service) so it can reach its package-private test-seam constructor --
   // core cannot depend on this connector module, so that class lives here in inetsoft-rest,
   // same trick RestSourceTypesAnnotationClassTest uses for WizDatabaseController's
   // package-private classifyQueryClass.

   // ===================================================================================
   // helpers
   // ===================================================================================

   private void stubIntrospection(String responseBody) {
      stubFor(post(urlPathEqualTo("/graphql")).willReturn(okJson(responseBody)));
   }

   private static String withUnrelatedResolverWarning(String introspectionBody) throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = (ObjectNode) mapper.readTree(introspectionBody);
      ArrayNode errors = root.putArray("errors");
      errors.addObject().put("message", "some unrelated field's resolver logged a warning");
      return mapper.writeValueAsString(root);
   }

   private static String okIntrospectionBody() throws IOException {
      // The SAME fixture GraphQLCatalogTest parses directly -- this class exists to confirm it
      // still comes out the same after a real HTTP/JSON round trip, not to define new schema
      // shapes.
      try(InputStream in = GraphQLRuntimeCatalogTest.class.getResourceAsStream(
         "bookstore-schema.json"))
      {
         assertNotNull(in, "missing test resource bookstore-schema.json");
         return IOUtils.toString(in, StandardCharsets.UTF_8);
      }
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS, false))
            .thenAnswer(invocation -> mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
