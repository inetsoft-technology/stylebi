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
package inetsoft.uql.rest.datasource.airtable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.ApiTokenCredential;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81472: the Airtable table catalog, through the real {@link AirtableRuntime} entry points
 * and a stubbed Meta API, so the request path (base URL, bearer header, paging, error mapping) is
 * the production one. The JSON bodies follow Airtable's documented "list bases" / "get base
 * schema" responses. No live Airtable account was available: these tests prove the contract, not
 * the live service.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  AirtableCatalogTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@WireMockTest
class AirtableCatalogTest {
   private AirtableDataSource dataSource;
   private AirtableRuntime runtime;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setup(WireMockRuntimeInfo info, TestInfo testInfo) {
      String url = info.getHttpBaseUrl() + "/";
      dataSource = new AirtableDataSource() {
         @Override
         public String getURL() {
            return url;
         }
      };
      dataSource.setName("AirtableCatalogTest/" + testInfo.getDisplayName());
      runtime = new AirtableRuntime();
   }

   // ===================================================================================
   // listDatasets
   // ===================================================================================

   @Test
   void listsTheTablesOfEveryBaseAcrossBasePages() throws Exception {
      // page 1 carries an offset that itself contains the '/' Airtable really uses
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).withQueryParam("offset", absent())
         .willReturn(okJson("{\"bases\":[" + base("appAAA111", "Sales", "create") +
                            "],\"offset\":\"itr1/appAAA111\"}")));
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).withQueryParam("offset", equalTo("itr1/appAAA111"))
         .willReturn(okJson("{\"bases\":[" + base("appBBB222", "Ops", "read") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"), table("Orders", "number"));
      stubTables("appBBB222", table("Tasks", "singleLineText"));

      TabularCatalog catalog = runtime.listDatasets(dataSource);

      assertEquals(List.of("appAAA111/Customers", "appAAA111/Orders", "appBBB222/Tasks"),
                   ids(catalog));
      assertTrue(catalog.relationships().isEmpty());
      verify(2, getRequestedFor(urlPathEqualTo("/v0/meta/bases")));
      verify(1, getRequestedFor(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")));
      verify(1, getRequestedFor(urlPathEqualTo("/v0/meta/bases/appBBB222/tables")));
   }

   @Test
   void sendsTheApiTokenAsABearerHeaderOnEveryCall() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases"))
         .willReturn(okJson("{\"bases\":[" + base("appAAA111", "Sales", "edit") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"));

      runtime.listDatasets(dataSource);

      verify(getRequestedFor(urlPathEqualTo("/v0/meta/bases"))
         .withHeader("Authorization", equalTo("Bearer tok-123")));
      verify(getRequestedFor(urlPathEqualTo("/v0/meta/bases/appAAA111/tables"))
         .withHeader("Authorization", equalTo("Bearer tok-123")));
   }

   @Test
   void idsKeepTableNamesWithSpecialCharactersVerbatim_andDescribeResolvesThemBack()
      throws Exception
   {
      stubFor(get(urlPathEqualTo("/v0/meta/bases"))
         .willReturn(okJson("{\"bases\":[" + base("appAAA111", "Sales", "create") + "]}")));
      // "Foo" and "Foo/Bar" side by side: a table name holding the separator must not be
      // confused with a shorter table, and two '/' and two '&' catch a replaceFirst-style bug.
      stubTables("appAAA111",
         table("Sales & Returns", "singleLineText"),
         table("Order / No.", "number"),
         table("Foo", "singleLineText"),
         table("Foo/Bar", "checkbox"),
         table("A/B/C & D & E", "date"),
         table("Q#1 100% 销售", "singleLineText"));

      List<String> ids = ids(runtime.listDatasets(dataSource));

      assertEquals(List.of("appAAA111/Sales & Returns", "appAAA111/Order / No.",
                           "appAAA111/Foo", "appAAA111/Foo/Bar", "appAAA111/A/B/C & D & E",
                           "appAAA111/Q#1 100% 销售"), ids);
      assertEquals(ids.size(), new HashSet<>(ids).size());

      Map<String, String> expectedFirstFieldType = Map.of(
         "appAAA111/Foo", XSchema.STRING,
         "appAAA111/Foo/Bar", XSchema.BOOLEAN,
         "appAAA111/Order / No.", XSchema.DOUBLE,
         "appAAA111/A/B/C & D & E", XSchema.DATE);

      for(String id : ids) {
         TabularDatasetSchema schema = runtime.describeDataset(dataSource, id);
         assertEquals(id, schema.datasetId());
         String expected = expectedFirstFieldType.get(id);

         if(expected != null) {
            assertEquals(expected, schema.columns().get(4).type(),
                         "describe resolved the wrong table for " + id);
         }
      }
   }

   @Test
   void aBaseTheTokenHasNoAccessToIsSkippedWithoutAbortingTheOthers() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson("{\"bases\":[" +
         base("appNONE000", "Locked", "none") + "," + base("appAAA111", "Sales", "read") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"));

      assertEquals(List.of("appAAA111/Customers"), ids(runtime.listDatasets(dataSource)));
      verify(0, getRequestedFor(urlPathEqualTo("/v0/meta/bases/appNONE000/tables")));
   }

   @Test
   void aTokenThatSeesNoBasesYieldsAnEmptyCatalogNotAnError() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson("{\"bases\":[]}")));

      assertTrue(runtime.listDatasets(dataSource).datasets().isEmpty());
   }

   // ===================================================================================
   // Failure -- never an empty catalog
   // ===================================================================================

   @Test
   void missingSchemaScopeIsOneLineNamingTheScope() {
      // Airtable's real 403 body, with the newline HttpHandler would otherwise carry into the text
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(aResponse().withStatus(403)
         .withHeader("Content-Type", "application/json")
         .withBody("{\"error\":{\"type\":\"INVALID_PERMISSIONS_OR_MODEL_NOT_FOUND\",\n" +
                   "\"message\":\"Invalid permissions\"}}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("schema.bases:read"), ex.getMessage());
      assertTrue(ex.getMessage().contains("403"), ex.getMessage());
      assertFalse(ex.getMessage().contains("\n"), "must be a single line: " + ex.getMessage());
   }

   @Test
   void missingScopeOnAnIndividualBaseAbortsAndNamesTheBase() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson("{\"bases\":[" +
         base("appAAA111", "Sales", "read") + "," + base("appBBB222", "Ops", "read") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"));
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appBBB222/tables"))
         .willReturn(aResponse().withStatus(403)));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("appBBB222"), ex.getMessage());
      assertTrue(ex.getMessage().contains("schema.bases:read"), ex.getMessage());
   }

   @Test
   void aRejectedTokenIsReportedAsATokenProblem() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(aResponse().withStatus(401)));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("401") && ex.getMessage().contains("token"),
                 ex.getMessage());
   }

   @Test
   void rateLimitingIsNamedAsSuch() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(aResponse().withStatus(429)));

      Exception ex = assertThrows(Exception.class, () -> AirtableCatalog.listDatasets(
         new AirtableMetaClient(dataSource, ms -> { })));

      assertTrue(ex.getMessage().contains("429"), ex.getMessage());
   }

   @Test
   void anUnreachableServerThrowsAConnectivityMessage() {
      AirtableDataSource unreachable = new AirtableDataSource() {
         @Override
         public String getURL() {
            return "http://127.0.0.1:1/";
         }
      };
      unreachable.setName("AirtableCatalogTest/unreachable");

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(unreachable));

      assertTrue(ex.getMessage().startsWith("The request to Airtable failed"), ex.getMessage());
   }

   @Test
   void aSuccessResponseWithoutTheBasesArrayThrowsInsteadOfReportingEmpty() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson("{\"unexpected\":true}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("bases"), ex.getMessage());
   }

   @Test
   void aSuccessResponseWithoutTheTablesArrayThrowsInsteadOfDroppingTheBase() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson(
         "{\"bases\":[" + base("appAAA111", "Sales", "read") + "]}")));
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson("{}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("appAAA111") && ex.getMessage().contains("tables"),
                 ex.getMessage());
   }

   @Test
   void aRepeatedPageOffsetStopsInsteadOfLoopingForever() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson(
         "{\"bases\":[],\"offset\":\"same\"}")));

      Exception ex = assertThrows(Exception.class, () -> runtime.listDatasets(dataSource));

      assertTrue(ex.getMessage().contains("offset"), ex.getMessage());
   }

   @Test
   void aBaseIdOutsideTheLettersAndDigitsGrammarIsRefusedRatherThanBreakingTheIdSplit() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson(
         "{\"bases\":[" + base("app/evil", "X", "read") + "]}")));

      assertThrows(IllegalArgumentException.class, () -> runtime.listDatasets(dataSource));
   }

   // ===================================================================================
   // describeDataset
   // ===================================================================================

   @Test
   void describeMapsEveryDocumentedFieldTypeExplicitly() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"id\":\"tbl1\",\"name\":\"Everything\",\"primaryFieldId\":\"fld1\"," +
         "\"fields\":[" + String.join(",", fieldsOfEveryType()) + "],\"views\":[]}]}")));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "appAAA111/Everything");

      Map<String, String> actual = new LinkedHashMap<>();
      schema.columns().forEach(c -> actual.put(c.name(), c.type()));

      Map<String, String> expected = new LinkedHashMap<>();
      expected.put("records.id", XSchema.STRING);
      expected.put("records.createdTime", XSchema.TIME_INSTANT);
      String[][] types = {
         {"singleLineText", XSchema.STRING}, {"email", XSchema.STRING}, {"url", XSchema.STRING},
         {"multilineText", XSchema.STRING}, {"phoneNumber", XSchema.STRING},
         {"richText", XSchema.STRING}, {"singleSelect", XSchema.STRING},
         {"manualSort", XSchema.STRING}, {"number", XSchema.DOUBLE}, {"percent", XSchema.DOUBLE},
         {"currency", XSchema.DOUBLE}, {"duration", XSchema.DOUBLE}, {"count", XSchema.INTEGER},
         {"autoNumber", XSchema.INTEGER}, {"rating", XSchema.INTEGER},
         {"checkbox", XSchema.BOOLEAN}, {"date", XSchema.DATE},
         {"dateTime", XSchema.TIME_INSTANT}, {"createdTime", XSchema.TIME_INSTANT},
         {"lastModifiedTime", XSchema.TIME_INSTANT}, {"multipleSelects", XSchema.STRING},
         {"singleCollaborator", XSchema.STRING}, {"multipleCollaborators", XSchema.STRING},
         {"createdBy", XSchema.STRING}, {"lastModifiedBy", XSchema.STRING},
         {"multipleRecordLinks", XSchema.STRING}, {"multipleAttachments", XSchema.STRING},
         {"multipleLookupValues", XSchema.STRING}, {"barcode", XSchema.STRING},
         {"button", XSchema.STRING}, {"aiText", XSchema.STRING},
         {"externalSyncSource", XSchema.STRING}, {"lookup", XSchema.STRING}
      };

      for(String[] t : types) {
         expected.put("records.fields.f_" + t[0], t[1]);
      }

      // computed fields follow their declared result type
      expected.put("records.fields.f_formula_number", XSchema.DOUBLE);
      expected.put("records.fields.f_formula_checkbox", XSchema.BOOLEAN);
      expected.put("records.fields.f_formula_dateTime", XSchema.TIME_INSTANT);
      expected.put("records.fields.f_formula_invalid", XSchema.STRING);
      expected.put("records.fields.f_rollup_number", XSchema.DOUBLE);
      expected.put("records.fields.f_rollup_text", XSchema.STRING);

      assertEquals(expected, actual);
      assertEquals(List.copyOf(expected.keySet()),
                   schema.columns().stream().map(TabularColumn::name).toList(),
                   "columns must stay in the order Airtable reports the fields");
   }

   @Test
   void everyKnownFieldTypeIsHandledWithoutFallingToTheDefault() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"Everything\",\"fields\":[" +
         String.join(",", fieldsOfEveryType()) + "]}]}")));

      List<String> warnings = captureWarnings(
         () -> runtime.describeDataset(dataSource, "appAAA111/Everything"));

      assertEquals(List.of(), warnings);
   }

   @Test
   void theKnownFieldTypeSetIsPinnedSoANewOneShowsUpInReview() {
      assertEquals(35, AirtableCatalog.KNOWN_FIELD_TYPES.size());
   }

   @Test
   void anUnknownFieldTypeFallsBackToStringWithAWarnAndDoesNotAbort() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"T\",\"fields\":[{\"id\":\"f1\",\"name\":\"Ok\"," +
         "\"type\":\"number\"},{\"id\":\"f2\",\"name\":\"Newer\",\"type\":\"holographicText\"}]}]}")));
      TabularDatasetSchema[] schema = new TabularDatasetSchema[1];

      List<String> warnings = captureWarnings(
         () -> schema[0] = runtime.describeDataset(dataSource, "appAAA111/T"));

      assertEquals(XSchema.STRING, schema[0].columns().get(3).type());
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("holographicText") && warnings.get(0).contains("Newer"),
                 warnings.get(0));
   }

   @Test
   void describeCarriesTheSourcesOwnDescriptionsAndDeclaresNothingItWasNotTold() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"Customers\",\"description\":\"Who buys from us\",\"fields\":[" +
         "{\"id\":\"f1\",\"name\":\"Name\",\"type\":\"singleLineText\",\"description\":\"Legal name\"}," +
         "{\"id\":\"f2\",\"name\":\"Region\",\"type\":\"singleSelect\"}]}," +
         "{\"name\":\"Plain\",\"fields\":[{\"id\":\"f3\",\"name\":\"X\",\"type\":\"number\"}]}]}")));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "appAAA111/Customers");

      assertEquals("Legal name", schema.columns().get(2).description());
      assertNull(schema.columns().get(3).description());
      assertNull(schema.columns().get(0).description());
      assertTrue(schema.description().startsWith("Who buys from us\n\n"), schema.description());
      assertTrue(schema.description().contains("Base ID = appAAA111") &&
                 schema.description().contains("Table Name = Customers"), schema.description());
      assertEquals(List.of(), schema.keyColumns());
      assertEquals(Map.of(), schema.params());
      assertFalse(schema.columnsMayBeIncomplete());
      assertTrue(schema.columns().stream().allMatch(
         c -> c.label() == null && c.isDimension() == null));
      assertTrue(runtime.describeDataset(dataSource, "appAAA111/Plain").description()
         .startsWith("Airtable base appAAA111"));
   }

   @Test
   void describeOfAnUnknownTableNamesTheTablesThatExist() {
      stubTables("appAAA111", table("Customers", "singleLineText"), table("Orders", "number"));

      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> runtime.describeDataset(dataSource, "appAAA111/Nope"));

      assertTrue(ex.getMessage().contains("Nope") && ex.getMessage().contains("Customers") &&
                 ex.getMessage().contains("Orders"), ex.getMessage());
   }

   @Test
   void describeRefusesIdsThatAreNotBaseSlashTable() {
      for(String bad : new String[] { "Customers", "", "ap p/T", "/T" }) {
         assertThrows(IllegalArgumentException.class,
                      () -> runtime.describeDataset(dataSource, bad), "id: '" + bad + "'");
      }

      assertThrows(IllegalArgumentException.class, () -> runtime.describeDataset(dataSource, null));
      verify(0, getRequestedFor(anyUrl()));
   }

   @Test
   void describeOfATableWithoutFieldsThrowsRatherThanReturningNoColumns() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"Empty\",\"fields\":[]}]}")));

      assertThrows(IllegalStateException.class,
                   () -> runtime.describeDataset(dataSource, "appAAA111/Empty"));
   }

   @Test
   void describeReportsTheMissingScopeToo() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables"))
         .willReturn(aResponse().withStatus(403)));

      Exception ex = assertThrows(Exception.class,
         () -> runtime.describeDataset(dataSource, "appAAA111/Customers"));

      assertTrue(ex.getMessage().contains("schema.bases:read"), ex.getMessage());
   }


   // ===================================================================================
   // Retry and schema reuse
   // ===================================================================================

   @Test
   void aRateLimitedCallIsRetriedAfterTheWaitAirtableAsksFor() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).inScenario("429").whenScenarioStateIs(STARTED)
         .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2"))
         .willSetStateTo("limited"));
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).inScenario("429")
         .whenScenarioStateIs("limited")
         .willReturn(okJson("{\"bases\":[" + base("appAAA111", "Sales", "read") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"));
      List<Long> sleeps = new ArrayList<>();

      TabularCatalog catalog = AirtableCatalog.listDatasets(
         new AirtableMetaClient(dataSource, sleeps::add));

      assertEquals(List.of("appAAA111/Customers"), ids(catalog));
      assertEquals(List.of(2000L), sleeps);
      verify(2, getRequestedFor(urlPathEqualTo("/v0/meta/bases")));
   }

   @Test
   void aTransientServerErrorIsRetriedWithTheDefaultBackOff() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).inScenario("503").whenScenarioStateIs(STARTED)
         .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).inScenario("503").whenScenarioStateIs("up")
         .willReturn(okJson("{\"bases\":[]}")));
      List<Long> sleeps = new ArrayList<>();

      assertTrue(AirtableCatalog.listDatasets(new AirtableMetaClient(dataSource, sleeps::add))
         .datasets().isEmpty());
      assertEquals(List.of(1000L), sleeps);
   }

   @Test
   void aCallThatStaysRateLimitedGivesUpLoudlyAfterABoundedNumberOfRetries() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(aResponse().withStatus(429)
         .withHeader("Retry-After", "120")));
      List<Long> sleeps = new ArrayList<>();

      Exception ex = assertThrows(Exception.class, () -> AirtableCatalog.listDatasets(
         new AirtableMetaClient(dataSource, sleeps::add)));

      assertTrue(ex.getMessage().contains("429"), ex.getMessage());
      assertEquals(AirtableMetaClient.MAX_RETRIES, sleeps.size());
      assertTrue(sleeps.stream().allMatch(ms -> ms == AirtableMetaClient.MAX_WAIT_MS),
                 "an oversized Retry-After must be capped: " + sleeps);
      verify(AirtableMetaClient.MAX_RETRIES + 1, getRequestedFor(urlPathEqualTo("/v0/meta/bases")));
   }

   @Test
   void aMissingScopeIsNeverRetried() {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(aResponse().withStatus(403)));
      List<Long> sleeps = new ArrayList<>();

      assertThrows(Exception.class, () -> AirtableCatalog.listDatasets(
         new AirtableMetaClient(dataSource, sleeps::add)));

      assertEquals(List.of(), sleeps);
      verify(1, getRequestedFor(urlPathEqualTo("/v0/meta/bases")));
   }

   @Test
   void describingSeveralTablesOfOneBaseFetchesItsSchemaOnce() throws Exception {
      stubTables("appAAA111", table("Customers", "singleLineText"), table("Orders", "number"));

      runtime.describeDataset(dataSource, "appAAA111/Customers");
      runtime.describeDataset(dataSource, "appAAA111/Orders");

      verify(1, getRequestedFor(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")));
   }

   @Test
   void aListingAlwaysRefetchesSoANewTableIsSeen() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases")).willReturn(okJson(
         "{\"bases\":[" + base("appAAA111", "Sales", "read") + "]}")));
      stubTables("appAAA111", table("Customers", "singleLineText"));
      assertEquals(1, runtime.listDatasets(dataSource).datasets().size());

      stubTables("appAAA111", table("Customers", "singleLineText"), table("Orders", "number"));

      assertEquals(2, runtime.listDatasets(dataSource).datasets().size());
      // and the listing refreshed what a following describe reads
      assertEquals("appAAA111/Orders",
                   runtime.describeDataset(dataSource, "appAAA111/Orders").datasetId());
      verify(2, getRequestedFor(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")));
   }

   @Test
   void aFailedSchemaFetchIsNotCached() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables"))
         .willReturn(aResponse().withStatus(403)));
      assertThrows(Exception.class, () -> runtime.describeDataset(dataSource, "appAAA111/Customers"));

      stubTables("appAAA111", table("Customers", "singleLineText"));

      assertEquals("appAAA111/Customers",
                   runtime.describeDataset(dataSource, "appAAA111/Customers").datasetId());
   }
   // ===================================================================================
   // The declared columns are the columns a Records query really returns
   // ===================================================================================

   @Test
   void declaredColumnsCoverAndTypeMatchWhatARecordsQueryActuallyReturns() throws Exception {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/appAAA111/tables")).willReturn(okJson(
         "{\"tables\":[{\"name\":\"Customers\",\"fields\":[" +
         "{\"id\":\"f1\",\"name\":\"Name\",\"type\":\"singleLineText\"}," +
         "{\"id\":\"f2\",\"name\":\"Active\",\"type\":\"checkbox\"}," +
         "{\"id\":\"f3\",\"name\":\"Credit Limit\",\"type\":\"currency\"}," +
         "{\"id\":\"f4\",\"name\":\"Signup Date\",\"type\":\"date\"}," +
         "{\"id\":\"f5\",\"name\":\"Tags\",\"type\":\"multipleSelects\"}," +
         "{\"id\":\"f6\",\"name\":\"Order / No.\",\"type\":\"singleLineText\"}," +
         "{\"id\":\"f7\",\"name\":\"Visits\",\"type\":\"count\"}," +
         "{\"id\":\"f8\",\"name\":\"Seq\",\"type\":\"autoNumber\"}," +
         "{\"id\":\"f9\",\"name\":\"Stars\",\"type\":\"rating\"}," +
         "{\"id\":\"f10\",\"name\":\"Units\",\"type\":\"number\"}," +
         "{\"id\":\"f11\",\"name\":\"Whole Credit\",\"type\":\"currency\"}]}]}")));
      stubFor(get(urlPathEqualTo("/v0/appAAA111/Customers")).willReturn(okJson(
         "{\"records\":[{\"id\":\"rec1\",\"createdTime\":\"2026-01-01T00:00:00.000Z\",\"fields\":{" +
         "\"Name\":\"Acme\",\"Active\":true,\"Credit Limit\":5000.5,\"Signup Date\":\"2026-01-05\"," +
         "\"Tags\":[\"vip\",\"wholesale\"],\"Order / No.\":\"SO-1\",\"Visits\":3,\"Seq\":7,\"Stars\":4,\"Units\":12,\"Whole Credit\":5000}}," +
         "{\"id\":\"rec2\",\"createdTime\":\"2026-01-02T00:00:00.000Z\",\"fields\":{\"Units\":14,\"Whole Credit\":5000.25}}]}")));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "appAAA111/Customers");

      AirtableQuery query = new AirtableQuery();
      query.setDataSource(dataSource);
      query.setEndpoint("Records");
      RestParameters parameters = query.getParameters();
      parameters.findParameter("Base ID").setValue("appAAA111");
      parameters.findParameter("Table Name").setValue("Customers");
      parameters.findParameter("Time Zone").setValue("UTC");
      parameters.findParameter("User Locale").setValue("en");
      query.setParameters(parameters);
      XTableNode table = runtime.runQuery(query, new VariableTable());

      Map<String, String> declared = new HashMap<>();
      schema.columns().forEach(c -> declared.put(c.name(), c.type()));
      Map<Class<?>, String> byClass = Map.of(
         String.class, XSchema.STRING, Boolean.class, XSchema.BOOLEAN,
         Double.class, XSchema.DOUBLE, Integer.class, XSchema.INTEGER,
         Long.class, XSchema.LONG, java.sql.Date.class, XSchema.DATE,
         java.sql.Timestamp.class, XSchema.TIME_INSTANT);

      assertEquals(13, table.getColCount());

      for(int i = 0; i < table.getColCount(); i++) {
         String name = table.getName(i);
         assertTrue(declared.containsKey(name), "declared columns miss the real column " + name);
         String actual = byClass.get(table.getType(i));

         if(XSchema.DOUBLE.equals(declared.get(name))) {
            // A number column is typed from the rows returned: all-whole values come back
            // Integer, any decimal makes it Double. DOUBLE is the lossless superset.
            assertTrue(XSchema.DOUBLE.equals(actual) || XSchema.INTEGER.equals(actual) ||
                       XSchema.LONG.equals(actual), "type of " + name + ": " + actual);
         }
         else {
            assertEquals(actual, declared.get(name), "type of " + name);
         }
      }

      // pin the inference rule the comment above relies on
      Map<String, Class<?>> actualByName = new HashMap<>();

      for(int i = 0; i < table.getColCount(); i++) {
         actualByName.put(table.getName(i), table.getType(i));
      }

      assertEquals(Integer.class, actualByName.get("records.fields.Units"));
      assertEquals(Double.class, actualByName.get("records.fields.Whole Credit"));
   }

   // ===================================================================================
   // fixtures
   // ===================================================================================

   private static String base(String id, String name, String permission) {
      return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"permissionLevel\":\"" +
         permission + "\"}";
   }

   /** One table with a primary field of {@code firstType}, a second field, and a third. */
   private static String table(String name, String firstType) {
      return "{\"id\":\"tbl" + Math.abs(name.hashCode()) + "\",\"name\":\"" + name +
         "\",\"primaryFieldId\":\"fldP\",\"fields\":[{\"id\":\"fldP\",\"name\":\"Primary\"," +
         "\"type\":\"singleLineText\"},{\"id\":\"fldQ\",\"name\":\"Second\",\"type\":\"number\"," +
         "\"options\":{\"precision\":2}},{\"id\":\"fldR\",\"name\":\"Third\",\"type\":\"" +
         firstType + "\"}],\"views\":[{\"id\":\"viw1\",\"name\":\"Grid view\",\"type\":\"grid\"}]}";
   }

   private static void stubTables(String baseId, String... tables) {
      stubFor(get(urlPathEqualTo("/v0/meta/bases/" + baseId + "/tables"))
         .willReturn(okJson("{\"tables\":[" + String.join(",", tables) + "]}")));
   }

   private static List<String> ids(TabularCatalog catalog) {
      return catalog.datasets().stream().map(TabularDatasetRef::id).toList();
   }

   private static List<String> fieldsOfEveryType() {
      List<String> fields = new ArrayList<>();
      int n = 0;

      for(String type : List.of(
         "singleLineText", "email", "url", "multilineText", "phoneNumber", "richText",
         "singleSelect", "manualSort", "number", "percent", "currency", "duration", "count",
         "autoNumber", "rating", "checkbox", "date", "dateTime", "createdTime",
         "lastModifiedTime", "multipleSelects", "singleCollaborator", "multipleCollaborators",
         "createdBy", "lastModifiedBy", "multipleRecordLinks", "multipleAttachments",
         "multipleLookupValues", "barcode", "button", "aiText", "externalSyncSource", "lookup"))
      {
         fields.add(field(++n, "f_" + type, type, null));
      }

      fields.add(field(++n, "f_formula_number", "formula",
         "{\"isValid\":true,\"formula\":\"1\",\"result\":{\"type\":\"number\"," +
         "\"options\":{\"precision\":1}}}"));
      fields.add(field(++n, "f_formula_checkbox", "formula",
         "{\"isValid\":true,\"result\":{\"type\":\"checkbox\"}}"));
      fields.add(field(++n, "f_formula_dateTime", "formula",
         "{\"isValid\":true,\"result\":{\"type\":\"dateTime\"}}"));
      fields.add(field(++n, "f_formula_invalid", "formula", "{\"isValid\":false}"));
      fields.add(field(++n, "f_rollup_number", "rollup",
         "{\"isValid\":true,\"result\":{\"type\":\"number\"}}"));
      fields.add(field(++n, "f_rollup_text", "rollup",
         "{\"isValid\":true,\"result\":{\"type\":\"singleLineText\"}}"));
      return fields;
   }

   private static String field(int n, String name, String type, String options) {
      return "{\"id\":\"fld" + n + "\",\"name\":\"" + name + "\",\"type\":\"" + type + "\"" +
         (options == null ? "" : ",\"options\":" + options) + "}";
   }

   private static List<String> captureWarnings(ThrowingRunnable action) throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger(AirtableCatalog.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         action.run();
      }
      finally {
         logger.detachAppender(appender);
      }

      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         ApiTokenCredential credential = mock(ApiTokenCredential.class);
         when(credential.getApiToken()).thenReturn("tok-123");
         when(service.createCredential(any(CredentialType.class))).thenReturn(credential);
         when(service.createCredential(any(CredentialType.class), anyBoolean()))
            .thenReturn(credential);
         return service;
      }
   }
}
