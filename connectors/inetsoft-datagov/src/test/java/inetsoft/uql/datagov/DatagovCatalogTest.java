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
package inetsoft.uql.datagov;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers {@link DatagovCatalog}, driven off {@link DatagovRuntime}'s two extracted static fetch
 * methods ({@link DatagovRuntime#getDiscoveryMetadata}/{@link DatagovRuntime#getSiteMetadata})
 * mocked via {@code MockedStatic} -- the same pattern {@code MongoCatalogTest}/
 * {@code ElasticCatalogTests} use, for the same reason: there is no live Socrata site to exercise
 * in this environment. Fixtures below reproduce the real, live JSON shapes confirmed while this
 * connector was designed (Discovery API pages, {@code meta.view} blocks), not hand-invented ones.
 *
 * <p>Wire-level correctness of {@code getDiscoveryMetadata}/{@code getSiteMetadata} themselves
 * (GET-only, correct auth-header behavior) is covered separately by
 * {@code DatagovMetadataFetchTests}, which cannot be caught here since mocking replaces the method
 * entirely.
 *
 * <p>The Spring context wiring below exists only so {@code new DatagovDataSource()} can construct
 * (its constructor eagerly creates a credential via {@code CredentialService.getInstance()}); no
 * test here calls {@code ds.getUser()}/{@code getPassword()} for real, since every fetch call is
 * mocked away.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, DatagovCatalogTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DatagovCatalogTest {
   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   private MockedStatic<DatagovRuntime> runtimeStatic;

   @AfterEach
   void closeStaticMock() {
      if(runtimeStatic != null) {
         runtimeStatic.close();
      }
   }

   private MockedStatic<DatagovRuntime> ensureStatic() {
      if(runtimeStatic == null) {
         runtimeStatic = mockStatic(DatagovRuntime.class, org.mockito.Mockito.CALLS_REAL_METHODS);
      }

      return runtimeStatic;
   }

   private static DatagovDataSource ds() {
      return ds("https://data.cityofnewyork.us/");
   }

   private static DatagovDataSource ds(String url) {
      DatagovDataSource ds = new DatagovDataSource();
      ds.setURL(url);
      return ds;
   }

   private void stubDiscovery(java.util.function.Function<String, String> byUrl) {
      ensureStatic().when(() -> DatagovRuntime.getDiscoveryMetadata(anyString()))
         .thenAnswer(inv -> byUrl.apply(inv.getArgument(0)));
   }

   private void stubDiscoveryThrows(Exception ex) {
      ensureStatic().when(() -> DatagovRuntime.getDiscoveryMetadata(anyString())).thenThrow(ex);
   }

   private void stubSiteMetadata(String json) {
      ensureStatic().when(() -> DatagovRuntime.getSiteMetadata(any(), anyString()))
         .thenReturn(json);
   }

   private void stubSiteMetadataThrows(Exception ex) {
      ensureStatic().when(() -> DatagovRuntime.getSiteMetadata(any(), anyString())).thenThrow(ex);
   }

   private static String discoveryPage(int resultSetSize, String... datasetEntries) {
      return "{\"resultSetSize\":" + resultSetSize + ",\"results\":[" +
         String.join(",", datasetEntries) + "]}";
   }

   private static String datasetEntry(String id) {
      return datasetEntry(id, "dataset");
   }

   private static String datasetEntry(String id, String type) {
      return "{\"resource\":{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"name\":\"n\"}}";
   }

   private static String rowsJson(String description, Long rowIdentifierColumnId,
                                  String... columns)
   {
      StringBuilder sb = new StringBuilder("{\"meta\":{\"view\":{");

      if(description != null) {
         sb.append("\"description\":\"").append(description).append("\",");
      }

      if(rowIdentifierColumnId != null) {
         sb.append("\"rowIdentifierColumnId\":").append(rowIdentifierColumnId).append(",");
      }

      sb.append("\"columns\":[").append(String.join(",", columns)).append("]}}}");
      return sb.toString();
   }

   private static String column(int id, String name, String dataTypeName) {
      return column(id, name, dataTypeName, null);
   }

   private static String column(int id, String name, String dataTypeName, String description) {
      return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"dataTypeName\":\"" + dataTypeName +
         "\"" + (description != null ? ",\"description\":\"" + description + "\"" : "") + "}";
   }

   // ----- listDatasets -----

   @Test
   void listDatasets_returnsOneRefPerDataset() throws Exception {
      stubDiscovery(url -> discoveryPage(2, datasetEntry("erm2-nwe9"), datasetEntry("8wbx-tsch")));

      TabularCatalog catalog = DatagovCatalog.listDatasets(ds());

      assertEquals(Set.of("erm2-nwe9", "8wbx-tsch"),
         Set.copyOf(catalog.datasets().stream().map(TabularDatasetRef::id).toList()));
      assertEquals(List.of(), catalog.relationships(),
         "the Discovery API is a search index over independently published assets, not a data " +
         "dictionary");
   }

   @Test
   void listDatasets_excludesNonDatasetAssetTypes() throws Exception {
      stubDiscovery(url -> discoveryPage(2,
         datasetEntry("erm2-nwe9", "dataset"), datasetEntry("abcd-wxyz", "chart")));

      TabularCatalog catalog = DatagovCatalog.listDatasets(ds());

      assertEquals(List.of("erm2-nwe9"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_requestsOnlyDatasetsThroughTheServerFilter() throws Exception {
      ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
      ensureStatic().when(() -> DatagovRuntime.getDiscoveryMetadata(urls.capture()))
         .thenReturn(discoveryPage(1, datasetEntry("erm2-nwe9")));

      DatagovCatalog.listDatasets(ds());

      assertTrue(urls.getValue().contains("only=datasets"), urls.getValue());
   }

   @Test
   void listDatasets_fetchesAllPages() throws Exception {
      // resultSetSize (10001) exceeds one page (10000), so a second request at offset=10000 is
      // required to finish enumerating -- this replaces a retired "only one page is fetched"
      // expectation, which under-read TabularCatalogProvider's own "no limit and no paging"
      // contract.
      stubDiscovery(url -> url.contains("offset=0")
         ? discoveryPage(10001, datasetEntry("erm2-nwe9"), datasetEntry("8wbx-tsch"))
         : discoveryPage(10001, datasetEntry("w7w3-xahh")));

      TabularCatalog catalog = DatagovCatalog.listDatasets(ds());

      assertEquals(Set.of("erm2-nwe9", "8wbx-tsch", "w7w3-xahh"),
         Set.copyOf(catalog.datasets().stream().map(TabularDatasetRef::id).toList()));
      runtimeStatic.verify(() -> DatagovRuntime.getDiscoveryMetadata(contains("offset=0")));
      runtimeStatic.verify(() -> DatagovRuntime.getDiscoveryMetadata(contains("offset=10000")));
   }

   @Test
   void listDatasets_exceedsMaxRequests_throws() throws Exception {
      // resultSetSize never shrinks the remaining gap relative to offset, so pagination would
      // never terminate on its own -- this must throw rather than truncate silently.
      int[] calls = {0};
      stubDiscovery(url -> {
         calls[0]++;
         return discoveryPage(Integer.MAX_VALUE, datasetEntry("erm2-nwe9"));
      });

      Exception thrown = assertThrows(Exception.class, () -> DatagovCatalog.listDatasets(ds()));

      assertTrue(thrown.getMessage().contains("50 requests"), thrown.getMessage());
      assertEquals(50, calls[0], "must stop AT the safety ceiling, not silently keep going");
   }

   @Test
   void listDatasets_fetchFailure_propagates() {
      stubDiscoveryThrows(new Exception("connection refused"));

      Exception thrown = assertThrows(Exception.class, () -> DatagovCatalog.listDatasets(ds()));
      assertTrue(thrown.getMessage().contains("connection refused"), thrown.getMessage());
   }

   @Test
   void listDatasets_noResultSetSize_throws() {
      stubDiscovery(url -> "{\"results\":[]}");

      Exception thrown = assertThrows(Exception.class, () -> DatagovCatalog.listDatasets(ds()));
      assertTrue(thrown.getMessage().contains("resultSetSize"), thrown.getMessage());
   }

   @Test
   void listDatasets_noResultsArray_throws() {
      stubDiscovery(url -> "{\"resultSetSize\":0}");

      Exception thrown = assertThrows(Exception.class, () -> DatagovCatalog.listDatasets(ds()));
      assertTrue(thrown.getMessage().contains("results array"), thrown.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "https://data.cityofnewyork.us/",
      "https://data.cityofnewyork.us/api/views/erm2-nwe9/rows.json",
      "https://data.cityofnewyork.us:443/some/path"
   })
   void listDatasets_derivesDomainFromExistingUrlProperty(String url) throws Exception {
      ArgumentCaptor<String> requested = ArgumentCaptor.forClass(String.class);
      ensureStatic().when(() -> DatagovRuntime.getDiscoveryMetadata(requested.capture()))
         .thenReturn(discoveryPage(1, datasetEntry("erm2-nwe9")));

      DatagovCatalog.listDatasets(ds(url));

      assertTrue(requested.getValue().contains("domains=data.cityofnewyork.us"),
         "url='" + url + "': " + requested.getValue());
   }

   // ----- describeDataset: contract-level -----

   @Test
   void describeDataset_echoesDatasetIdAndMarksColumnsComplete() throws Exception {
      stubSiteMetadata(rowsJson(null, null, column(1, "sid", "meta_data")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals("erm2-nwe9", schema.datasetId());
      assertFalse(schema.columnsMayBeIncomplete(),
         "rows.json?max_rows=0 is declared metadata, not a bounded scan");
   }

   @Test
   void describeDataset_noColumns_throws() {
      stubSiteMetadata(rowsJson(null, null));

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovCatalog.describeDataset(ds(), "erm2-nwe9"));
      assertTrue(thrown.getMessage().contains("no columns"), thrown.getMessage());
   }

   @Test
   void describeDataset_unknownDataset_throwsNamingTheId() {
      stubSiteMetadataThrows(new Exception("Datagov site answered HTTP 404 for " +
         "https://data.cityofnewyork.us/api/views/aaaa-bbbb/rows.json?max_rows=0"));

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovCatalog.describeDataset(ds(), "aaaa-bbbb"));
      assertTrue(thrown.getMessage().contains("aaaa-bbbb"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("404"), thrown.getMessage());
   }

   @Test
   void describeDataset_columnOrderFollowsMetaViewColumnsArrayOrder() throws Exception {
      stubSiteMetadata(rowsJson(null, null,
         column(3, "Third", "text"), column(1, "First", "text"), column(2, "Second", "text")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(List.of("Third", "First", "Second"),
         schema.columns().stream().map(TabularColumn::name).toList(),
         "column order must follow the raw JSON array order, not the 'id'/'position' field");
   }

   @Test
   void describeDataset_paramsKeyMatchesDatagovQuerysOwnBeanProperty() throws Exception {
      // Derived, not written out: TabularUtil derives a property name from the getter, so a getter
      // rename must break this assertion rather than leave a literal here green.
      stubSiteMetadata(rowsJson(null, null, column(1, "sid", "meta_data")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      Set<String> datagovQueryProperties = TabularUtil.getPropertyMap(DatagovQuery.class).keySet();
      assertTrue(datagovQueryProperties.containsAll(schema.params().keySet()),
         schema.params().keySet() + " must all be properties of DatagovQuery, which has " +
         datagovQueryProperties);
      assertTrue(schema.params().containsKey("suffix"));
      // Fully filled -- no empty value.
      schema.params().forEach((k, v) -> assertFalse(v == null || v.isBlank(),
         "params['" + k + "'] must not be empty"));
      assertEquals("/api/views/erm2-nwe9/rows.json", schema.params().get("suffix"));
   }

   /**
    * A8's strong round-trip: whatever {@code describeDataset} puts in {@code params} really does
    * reproduce this dataset's rows via a real (locally-served, not mocked) {@code runQuery} --
    * modeled on {@code ElasticCatalogTests.paramsRoundTripThroughTheBindingPathAndReturnEveryRow}.
    * A wrong implementation this catches that the params-shape-only test above cannot: renaming
    * {@code DatagovQuery.suffix}, or folding an extra query parameter into the emitted suffix --
    * both would leave every other {@code DatagovCatalogTest} case green while this one fails,
    * because this is the only test that ever hands the map back to a real {@code DatagovQuery}/
    * {@code DatagovRuntime.runQuery}.
    *
    * <p>Reuses {@code DatagovRuntimeTest}'s own {@code rows.json} fixture for BOTH roles at once:
    * the mocked {@code meta.view} response {@code describeDataset} reads its declared columns
    * from, AND the literal HTTP body a local, in-process server (no live network, no Docker --
    * same {@code com.sun.net.httpserver.HttpServer} approach as {@code DatagovMetadataFetchTests})
    * answers with when {@code runQuery} requests the exact suffix {@code describeDataset}
    * declared. One real payload, not two hand-authored ones that could silently drift apart.
    */
   @Test
   void describeDataset_paramsRoundTripThroughApplyQueryContractAndRunQueryReturnsRows()
      throws Exception
   {
      String fixture;

      try(InputStream in = getClass().getResourceAsStream("rows.json")) {
         fixture = new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8);
      }

      stubSiteMetadata(fixture);
      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");
      List<String> declaredColumns = schema.columns().stream().map(TabularColumn::name).toList();

      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

      try {
         // Exactly the suffix path describeDataset declared -- a suffix pointing anywhere else
         // (a renamed property, an appended query parameter) 404s here instead of passing.
         server.createContext("/api/views/erm2-nwe9/rows.json", exchange -> {
            byte[] body = fixture.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);

            try(OutputStream out = exchange.getResponseBody()) {
               out.write(body);
            }
         });
         server.start();

         DatagovDataSource liveDs =
            ds("http://localhost:" + server.getAddress().getPort() + "/");
         DatagovQuery query = new DatagovQuery();
         query.setDataSource(liveDs);

         TabularQueryContractSupport.applyQueryContract(
            query, TabularUtil.getPropertyMap(query.getClass()),
            new TabularSchemaExtractor().extract(query, DatagovDataSource.TYPE),
            new LinkedHashMap<>(schema.params()), "datagov-test");

         assertEquals(schema.params().get("suffix"), query.getSuffix(),
            "applyQueryContract must write the exact suffix describeDataset declared");

         XTableNode table = new DatagovRuntime().runQuery(query, new VariableTable());
         assertNotNull(table, "the query built from describeDataset's own params must run");

         List<String> actualColumns = new ArrayList<>();

         for(int i = 0; i < table.getColCount(); i++) {
            actualColumns.add(table.getName(i));
         }

         assertEquals(declaredColumns, actualColumns,
            "the columns describeDataset declared must match what runQuery's DatagovTable, fed " +
            "the params describeDataset returned, actually parses");

         int rows = 0;

         while(table.next()) {
            rows++;
         }

         assertEquals(1094, rows,
            "the fixture holds 1094 rows and the binding must return them all");
      }
      finally {
         server.stop(0);
      }
   }

   // ----- describeDataset: malformed id (A7) -----

   @ParameterizedTest
   @ValueSource(strings = {
      "a/b-cdef", "erm2-nwe9/../secret", "erm2 nwe9", "ERM2-NWE9", "erm2-nwe", "erm2-nwe99"
   })
   void describeDataset_malformedId_rejectedBeforeAnyHttpRequest(String id) {
      // Stubbed to blow up loudly (an Error, not an Exception the code under test could catch) if
      // validation fails to short-circuit before a network call -- proves the rejection happens
      // BEFORE any request, not merely that some exception was eventually thrown.
      ensureStatic().when(() -> DatagovRuntime.getSiteMetadata(any(), anyString()))
         .thenThrow(new AssertionError("must not reach the network for a malformed id: " + id));

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovCatalog.describeDataset(ds(), id), "id='" + id + "'");
      assertTrue(thrown.getMessage().contains("not a Socrata 4x4 id"),
         "id='" + id + "': " + thrown.getMessage());
   }

   @ParameterizedTest
   @NullAndEmptySource
   void describeDataset_blankId_rejectedBeforeAnyHttpRequest(String id) {
      ensureStatic().when(() -> DatagovRuntime.getSiteMetadata(any(), anyString()))
         .thenThrow(new AssertionError("must not reach the network for a blank id"));

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovCatalog.describeDataset(ds(), id));
      assertTrue(thrown.getMessage().contains("blank"), thrown.getMessage());
   }

   // ----- describeDataset: blank/null data source URL (review R1, Important 2) -----

   @ParameterizedTest
   @NullAndEmptySource
   void describeDataset_blankUrl_throwsNamedError(String url) {
      // Same precondition listDatasets already guards via requireSiteDomain -- an in-progress
      // data source (URL never configured) must fail with a message naming the data source and
      // what's missing, not a bare NullPointerException out of ds.getURL().trim(). Stubbed to
      // blow up loudly if validation fails to short-circuit before a network call, exactly as
      // describeDataset_malformedId_rejectedBeforeAnyHttpRequest does for a bad id.
      ensureStatic().when(() -> DatagovRuntime.getSiteMetadata(any(), anyString()))
         .thenThrow(new AssertionError("must not reach the network with no URL configured"));
      DatagovDataSource blankUrlDs = new DatagovDataSource();
      blankUrlDs.setURL(url);

      Exception thrown = assertThrows(Exception.class,
         () -> DatagovCatalog.describeDataset(blankUrlDs, "erm2-nwe9"));
      assertTrue(thrown.getMessage().contains("no URL configured"), thrown.getMessage());
      assertFalse(thrown instanceof NullPointerException, thrown.getClass().getName());
   }

   // ----- describeDataset: row-identifier resolution -----

   @Test
   void describeDataset_rowIdentifierNamesABusinessColumn_reportedAsKeyColumn() throws Exception {
      stubSiteMetadata(rowsJson(null, 2L,
         column(1, "sid", "meta_data"), column(2, "Unique Key", "text")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(List.of("Unique Key"), schema.keyColumns());
   }

   @Test
   void describeDataset_noRowIdentifier_keyColumnsEmpty() throws Exception {
      stubSiteMetadata(rowsJson(null, null,
         column(1, "sid", "meta_data"), column(2, "Unique Key", "text")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(List.of(), schema.keyColumns());
   }

   @Test
   void describeDataset_rowIdentifierNamesTheSystemColumn_stillFaithfullyReported()
      throws Exception
   {
      stubSiteMetadata(rowsJson(null, 1L,
         column(1, "sid", "meta_data"), column(2, "Unique Key", "text")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(List.of("sid"), schema.keyColumns());
   }

   // ----- describeDataset: descriptions passed through verbatim -----

   @Test
   void describeDataset_descriptionsAreTrimmedAndCarriedVerbatim() throws Exception {
      stubSiteMetadata(rowsJson("NYC 311 service requests.\\n", null,
         column(1, "Unique Key", "text",
                "Unique identifier of a Service Request (SR) in the open data set\\n")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertTrue(schema.description().startsWith("NYC 311 service requests."),
         schema.description());
      assertEquals("Unique identifier of a Service Request (SR) in the open data set",
         schema.columns().get(0).description());
   }

   @Test
   void describeDataset_blankDescriptions_reportedAsNullNotEmptyString() throws Exception {
      stubSiteMetadata(rowsJson(null, null, column(1, "sid", "meta_data")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertNull(schema.columns().get(0).description());
   }

   // ----- describeDataset: dataTypeName -> XSchema mapping (A4) -----

   @ParameterizedTest(name = "{0} -> {1}")
   @MethodSource("dataTypeMappings")
   void describeDataset_mapsDataTypeNameToXSchema(String dataTypeName, String expectedXSchema)
      throws Exception
   {
      stubSiteMetadata(rowsJson(null, null, column(1, "col", dataTypeName)));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(expectedXSchema, schema.columns().get(0).type(),
         "dataTypeName '" + dataTypeName + "'");
   }

   static Stream<Arguments> dataTypeMappings() {
      return Stream.of(
         Arguments.of("text", XSchema.STRING),
         Arguments.of("number", XSchema.DECIMAL),
         Arguments.of("money", XSchema.DECIMAL),
         Arguments.of("percent", XSchema.DECIMAL),
         Arguments.of("checkbox", XSchema.BOOLEAN),
         Arguments.of("calendar_date", XSchema.TIME_INSTANT),
         Arguments.of("fixed_timestamp", XSchema.TIME_INSTANT),
         Arguments.of("floating_timestamp", XSchema.TIME_INSTANT),
         Arguments.of("point", XSchema.STRING),
         Arguments.of("location", XSchema.STRING),
         Arguments.of("phone", XSchema.STRING),
         Arguments.of("url", XSchema.STRING),
         Arguments.of("email", XSchema.STRING),
         Arguments.of("document", XSchema.STRING),
         Arguments.of("photo", XSchema.STRING),
         Arguments.of("multipolygon", XSchema.STRING),
         Arguments.of("line", XSchema.STRING),
         Arguments.of("multiline", XSchema.STRING),
         Arguments.of("multipoint", XSchema.STRING),
         Arguments.of("polygon", XSchema.STRING),
         Arguments.of("flag", XSchema.STRING),
         Arguments.of("stars", XSchema.STRING),
         Arguments.of("drop_down_list", XSchema.STRING),
         Arguments.of("meta_data", XSchema.STRING));
   }

   @Test
   void describeDataset_unrecognizedDataTypeName_reportedAsStringAndFlaggedInDescription()
      throws Exception
   {
      stubSiteMetadata(rowsJson(null, null, column(1, "futureField", "some_brand_new_type")));

      TabularDatasetSchema schema = DatagovCatalog.describeDataset(ds(), "erm2-nwe9");

      assertEquals(XSchema.STRING, schema.columns().get(0).type());
      assertTrue(schema.description().contains("futureField") &&
                 schema.description().contains("some_brand_new_type"),
         schema.description());
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD))
            .thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false))
            .thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }

      // TabularSchemaExtractor (the round-trip test's applyQueryContract path) reaches Config for
      // a display-label resource bundle via LayoutCreator.createLayout -- a mock returns null for
      // it, which is the branch that leaves the label alone. Same reasoning as
      // ElasticCatalogTests.loadData's identical stub.
      @Bean
      public inetsoft.uql.util.Config config() {
         return mock(inetsoft.uql.util.Config.class);
      }
   }
}
