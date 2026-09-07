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
package inetsoft.uql.elasticrest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ApplicationContext;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ElasticCatalog} against a real Elasticsearch, which is the reason this connector was
 * chosen for the SPI round: every other implementer's tests can only check the shape of what the
 * connector builds, and these can check it against what the server actually says and what
 * {@code runQuery} actually returns.
 *
 * <p><b>Why a system property and not {@code @Disabled}.</b> These need a Docker daemon, so they
 * must not run in a build that has none — which is why {@code ElasticRestRuntimeTests} beside them
 * was written {@code @Disabled}. The cost of {@code @Disabled} is that the only way to run them at
 * all is to edit the source first, so they rot unnoticed. A condition costs nothing extra to skip
 * and leaves them runnable as they stand:
 *
 * <pre>./mvnw -pl connectors/inetsoft-elastic -am -Delastic.tests=true test</pre>
 *
 * <p>{@code @Tag("integration")} would NOT achieve this, though it looks like it should: this
 * module's surefire configuration writes {@code <excludedGroups>integration,slow</excludedGroups>}
 * into the POM, and POM configuration wins over the matching command-line property, so a tagged
 * test cannot be re-enabled without editing the POM.
 *
 * <p>Every fact these tests pin was measured against Elasticsearch 7.9.2 while the catalog was
 * written, rather than assumed from the mapping documentation.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "elastic.tests", matches = "true")
class ElasticCatalogTests {
   @Container
   static final ElasticsearchContainer container =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.9.2");

   private ElasticRestRuntime runtime = null;
   private ElasticRestDataSource dataSource = null;

   @BeforeAll
   static void loadData() throws Exception {
      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(CredentialType.PASSWORD))
         .thenReturn(mock(LocalPasswordCredential.class));
      when(credentialService.createCredential(CredentialType.PASSWORD, false))
         .thenReturn(mock(LocalPasswordCredential.class));
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(CredentialService.class)).thenReturn(credentialService);
      // TabularSchemaExtractor, which runBinding uses to build the query schema the way
      // WorksheetTableService does, reaches Config for a display-label resource bundle. A mock
      // returns null for it, which is the branch that leaves the label alone.
      when(context.getBean(Config.class)).thenReturn(mock(Config.class));
      // runQuery (through XQuery.getMaxRows) reaches StyleFont.getDefaultFontFamily, which reads
      // SreeEnv.getProperty -> PropertiesEngine.getInstance -> ConfigurationContext.getSpringBean.
      // With no stub, the mocked ApplicationContext answers getBean(PropertiesEngine.class) with
      // null, and ConfigurationContext's Caffeine bean cache rejects a null value outright --
      // NullPointerException out of a static initializer, which permanently breaks
      // inetsoft.report.internal.Util for the rest of the JVM (NoClassDefFoundError on every
      // later reference). Stubbing getProperty to answer with its own "def" argument reproduces
      // what a real PropertiesEngine holding no stored properties would do.
      PropertiesEngine propertiesEngine = mock(PropertiesEngine.class);
      when(propertiesEngine.getProperty(anyString(), anyString(), anyBoolean()))
         .thenAnswer(invocation -> invocation.getArgument(1));
      when(context.getBean(PropertiesEngine.class)).thenReturn(propertiesEngine);
      // JsonTable.loadStreamed (runQuery's row loader) reaches XSwappableObjectList.add, which
      // calls XSwapper.getSwapper() -- the same getSpringBean/Caffeine trap as PropertiesEngine
      // above, one bean further down the same call path.
      when(context.getBean(XSwapper.class)).thenReturn(mock(XSwapper.class));
      ConfigurationContext.getContext().setApplicationContext(context);

      loadEarthquakes();

      // Every field shape the catalog has a rule for, in one index: a declared object (flattened
      // with dots), a nested array (one column), a field alias and a geo_point (neither a column),
      // a binary (a column), and a field carrying a source-declared meta.description.
      request("PUT", "/orders", """
         {"mappings": {
            "_meta": {"description": "Customer orders, one document per placed order."},
            "properties": {
              "orderId":  {"type": "keyword"},
              "orderRef": {"type": "alias", "path": "orderId"},
              "placedAt": {"type": "date"},
              "total":    {"type": "scaled_float", "scaling_factor": 100,
                           "meta": {"description": "Order total in USD."}},
              "shipTo":   {"properties": {"city": {"type": "keyword"},
                                          "zip":  {"type": "keyword"},
                                          "geo":  {"type": "geo_point"}}},
              "items":    {"type": "nested", "properties": {"sku":   {"type": "keyword"},
                                                            "qty":   {"type": "integer"},
                                                            "price": {"type": "double"}}},
              "region":   {"type": "geo_shape"},
              "blob":     {"type": "binary"},
              "active":   {"type": "boolean"},
              "wordCount": {"type": "token_count", "analyzer": "standard"}
            }}}""");
      request("POST", "/orders/_doc?refresh=true", """
         {"orderId": "A-1", "placedAt": "2026-03-04T10:00:00Z", "total": 123.45,
          "shipTo": {"city": "Boston", "zip": "02110", "geo": {"lat": 42.35, "lon": -71.05}},
          "items": [{"sku": "X1", "qty": 2, "price": 10.5},
                    {"sku": "X2", "qty": 1, "price": 102.45}],
          "blob": "U29tZSBieXRlcw==", "active": true,
          "wordCount": "four little words here"}""");

      request("PUT", "/.internal-probe", null);
      request("PUT", "/parked", null);
      request("POST", "/parked/_close", null);
      request("POST", "/_aliases", """
         {"actions": [{"add": {"index": "orders", "alias": "all-data"}},
                      {"add": {"index": "earthquakes", "alias": "all-data"}}]}""");

      // G1: an ILM/rollover-style index name -- Elastic's own documented convention -- must
      // enumerate and describe like any other index now that TabularCatalogService no longer
      // rejects a dotted TabularDatasetRef.id.
      request("POST", "/logs-2026.09.04/_doc?refresh=true", """
         {"level": "INFO", "message": "service started"}""");
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void createDataSource() {
      runtime = new ElasticRestRuntime();
      dataSource = new ElasticRestDataSource();
      dataSource.setURL(getUrl());
   }

   @Test
   void listDatasetsReturnsOpenUserIndexesInNameOrder() throws Exception {
      TabularCatalog catalog = runtime.listDatasets(dataSource);
      List<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id).toList();

      assertEquals(List.of("earthquakes", "logs-2026.09.04", "orders"), ids,
                   "only the open, non-internal indexes, sorted by name -- including the dotted, " +
                   "ILM-style index name (G1)");
      // The cluster's own bookkeeping indexes must not reach the annotation list, and a closed
      // index must not either -- POST /parked/_search answers 400 index_closed_exception, so
      // emitting it would produce a dataset whose own params can never run.
      assertFalse(ids.stream().anyMatch(id -> id.startsWith(".")),
                  "no internal index should be reported: " + ids);
      assertFalse(ids.contains("parked"), "a closed index should not be reported");
      // Elasticsearch declares no edges between indexes.
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasetsIsNotConfusedByAnAlias() throws Exception {
      // 'all-data' spans two indexes but is not an index, so _cat/indices never mentions it.
      assertFalse(runtime.listDatasets(dataSource).datasets().stream()
                     .anyMatch(d -> "all-data".equals(d.id())),
                  "an alias is not a dataset");
   }

   @Test
   void earthquakeColumnsAndTypesComeFromTheServersOwnMapping() throws Exception {
      Map<String, String> declared = serverFieldTypes("earthquakes");
      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "earthquakes");

      assertEquals("earthquakes", schema.datasetId(), "describeDataset must echo the id it was given");
      assertFalse(schema.columnsMayBeIncomplete(),
                  "_mapping is a declared schema, not a bounded scan");
      assertEquals(List.of(), schema.keyColumns(), "_id is not in _source, so there is no key column");

      // The column list, and its ORDER, is whatever the mapping response carried -- asserted
      // against that response rather than against a list written here. Note this is NOT the order
      // runQuery produces: _mapping returns its properties sorted by field name, while a row's
      // columns follow _source's own field order. Both are correct; they are different questions.
      assertEquals(new ArrayList<>(declared.keySet()),
                   schema.columns().stream().map(TabularColumn::name).toList());

      // ... and the same 12 fields the runtime test asserts runQuery produces, as a set.
      assertEquals(Set.of("@timestamp", "latitude", "longitude", "depth", "magnitude", "magType",
                          "nbStations", "gap", "distance", "rms", "source", "eventId"),
                   new HashSet<>(declared.keySet()));

      for(TabularColumn column : schema.columns()) {
         String esType = declared.get(column.name());
         assertEquals(EXPECTED_XSCHEMA.get(esType), column.type(),
                      "column '" + column.name() + "', which the server types '" + esType + "'");
         assertNull(column.isDimension(),
                    "an Elasticsearch mapping declares no dimension/measure split, so '" +
                    column.name() + "' must say nothing rather than guess");
      }
   }

   @Test
   void timestampIsMappedAsTextNotDate() throws Exception {
      // Measured, not assumed. The fixture's timestamps read 2016/01/04T21:18:48.64Z -- slashes
      // with a T separator -- which the server's default date detection does not recognize, so
      // dynamic mapping makes the field text. A catalog that assumed 'date' here would report a
      // type the index does not have, and toDataset would mark the column a dimension on the
      // strength of it.
      assertEquals("text", serverFieldTypes("earthquakes").get("@timestamp"));
      assertEquals(XSchema.STRING, column(runtime.describeDataset(dataSource, "earthquakes"),
                                          "@timestamp").type());
   }

   @Test
   void keywordSubfieldIsNotAColumn() throws Exception {
      // magType is text with a keyword multi-field. magType.keyword exists on the index side for
      // sorting and aggregation but is not in _source, so runQuery never produces it.
      assertTrue(serverMapping("earthquakes").path("magType").has("fields"),
                 "the fixture must actually have a multi-field for this test to mean anything");
      assertFalse(runtime.describeDataset(dataSource, "earthquakes").columns().stream()
                     .anyMatch(c -> c.name().endsWith(".keyword")),
                  "a multi-field is not in _source and must not be reported");
   }

   @Test
   void declaredObjectFlattensWithDotsAndArrayStaysOneColumn() throws Exception {
      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "orders");
      List<String> names = schema.columns().stream().map(TabularColumn::name).toList();

      // A declared object: the mapping names the leaves walkRecord will produce, so it is
      // flattened the same way.
      assertTrue(names.containsAll(List.of("shipTo.city", "shipTo.zip")), names.toString());
      assertFalse(names.contains("shipTo"), "an object is not a column of its own");

      // A nested array: one column holding the raw array, because expanded is false.
      assertTrue(names.contains("items"), names.toString());
      assertFalse(names.stream().anyMatch(n -> n.startsWith("items.")),
                  "expanded=false means the array's sub-fields are not columns: " + names);
      assertEquals("false", schema.params().get("expanded"),
                   "the column list above is only correct while expansion is off");

      String itemsDescription = column(schema, "items").description();
      assertNotNull(itemsDescription);
      assertTrue(itemsDescription.contains("sku") && itemsDescription.contains("qty") &&
                 itemsDescription.contains("price"),
                 "the substructure must still reach the reader: " + itemsDescription);
   }

   @Test
   void arrayColumnIsOneCellAtRuntime() throws Exception {
      // The claim the column list rests on, checked against runQuery rather than reasoned about.
      XTableNode table = runBinding(runtime.describeDataset(dataSource, "orders"));
      List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      assertTrue(names.contains("items"), names.toString());
      assertFalse(names.stream().anyMatch(n -> n.startsWith("items.")), names.toString());
      assertTrue(names.containsAll(List.of("shipTo.city", "shipTo.zip")), names.toString());
   }

   @Test
   void fieldAliasIsNotAColumn() throws Exception {
      // orderRef is declared 'alias' over orderId. It is resolved at query time and never appears
      // in _source, so it is excluded for the same reason a .keyword subfield is.
      assertEquals("alias", serverFieldTypes("orders").get("orderRef"));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "orders");
      List<String> names = schema.columns().stream().map(TabularColumn::name).toList();

      assertFalse(names.contains("orderRef"), names.toString());
      assertTrue(names.contains("orderId"), names.toString());
      assertFalse(schema.description().contains("orderRef"),
                  "an alias is absent from _source entirely, so it is not an excluded field to " +
                  "explain -- it is simply not part of the data");
   }

   @Test
   void objectShapedFieldsAreDescribedRatherThanColumned() throws Exception {
      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "orders");
      List<String> names = schema.columns().stream().map(TabularColumn::name).toList();

      // geo_point/geo_shape accept several _source forms and the mapping does not say which a
      // document used -- written as an object, shipTo.geo becomes shipTo.geo.lat and
      // shipTo.geo.lon at runtime, so 'shipTo.geo' addresses nothing.
      assertFalse(names.contains("shipTo.geo"), names.toString());
      assertFalse(names.contains("region"), names.toString());
      assertTrue(schema.description().contains("shipTo.geo (geo_point)"), schema.description());
      assertTrue(schema.description().contains("region (geo_shape)"), schema.description());

      // binary is the counter-case in the same family: _source always carries the base64 string
      // the document was indexed with, so it has one predictable form and IS a column.
      assertTrue(names.contains("blob"), names.toString());
      assertEquals(XSchema.STRING, column(schema, "blob").type());
   }

   @Test
   void objectShapedFieldsReallyDoSplitAtRuntime() throws Exception {
      // The measurement the exclusion above rests on.
      XTableNode table = runBinding(runtime.describeDataset(dataSource, "orders"));
      List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      assertTrue(names.containsAll(List.of("shipTo.geo.lat", "shipTo.geo.lon")),
                 "a geo_point written as an object splits into two columns the mapping never " +
                 "named: " + names);
      assertFalse(names.contains("shipTo.geo"), names.toString());
   }

   @Test
   void sourceDeclaredDescriptionsArePassedThroughVerbatim() throws Exception {
      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "orders");

      // A field-level meta.description is the source's own words and survives _mapping unchanged.
      assertTrue(column(schema, "total").description().startsWith("Order total in USD."),
                 column(schema, "total").description());
      // A mapping-level _meta.description opens the dataset description.
      assertTrue(schema.description()
                    .startsWith("Customer orders, one document per placed order."),
                 schema.description());
      // A field the index says nothing about, and that needs no note, says nothing.
      assertNull(column(schema, "orderId").description());
   }

   @Test
   void unrecognizedTypeIsReportedAsStringAndFlaggedInTheDatasetDescriptionNotTheColumn()
      throws Exception
   {
      // wordCount is mapped token_count with an analyzer -- a type 7.9.2 genuinely accepts (M20)
      // and this connector's TYPES table does not cover, so this is a live fixture for the A15
      // default arm, not a hand-built type string.
      assertEquals("token_count", serverFieldTypes("orders").get("wordCount"));

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "orders");
      TabularColumn column = column(schema, "wordCount");

      assertEquals(XSchema.STRING, column.type());
      // The signal lives on the DATASET description, never on the column's own -- so it can never
      // collide with a verbatim meta.description on the same field.
      assertNull(column.description(),
                 "wordCount has no meta.description, so its own column description must say " +
                 "nothing rather than carry a connector-composed note");
      assertTrue(schema.description().contains("wordCount") &&
                 schema.description().contains("token_count"),
                 "the dataset description must name the field and its unrecognized ES type: " +
                 schema.description());
   }

   @Test
   void describeDatasetColumnsAreASubsetOfWhatRunQueryProduces() throws Exception {
      // N7 (clarified at P2/R4): describeDataset's column set is a SUBSET of runQuery's, never
      // asserted equal -- A26's exclusions make runQuery a strict superset by design (shipTo.geo
      // splits into shipTo.geo.lat/shipTo.geo.lon at runtime, neither of which describeDataset
      // ever reports), so equality would fail a correct implementation on this exact fixture.
      for(String index : List.of("earthquakes", "orders")) {
         TabularDatasetSchema schema = runtime.describeDataset(dataSource, index);
         Set<String> described = schema.columns().stream().map(TabularColumn::name)
            .collect(Collectors.toSet());

         XTableNode table = runBinding(schema);
         Set<String> produced = new HashSet<>();

         for(int i = 0; i < table.getColCount(); i++) {
            produced.add(table.getName(i));
         }

         assertTrue(produced.containsAll(described),
                    "'" + index + "': describeDataset reported a column runQuery never " +
                    "produces -- described=" + described + ", produced=" + produced);
      }
   }

   @Test
   void dottedIndexNameIsEnumeratedAndDescribedEndToEnd() throws Exception {
      // G1: an index whose name contains '.' -- Elastic's own documented ILM/rollover naming
      // convention -- must work end to end now that TabularCatalogService.validateDatasetIds no
      // longer rejects a dotted TabularDatasetRef.id (G2).
      List<String> ids = runtime.listDatasets(dataSource).datasets().stream()
         .map(TabularDatasetRef::id).toList();
      assertTrue(ids.contains("logs-2026.09.04"), ids.toString());

      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "logs-2026.09.04");
      assertEquals("logs-2026.09.04", schema.datasetId(), "the dotted id must be echoed verbatim");
      assertTrue(schema.columns().stream().anyMatch(c -> "level".equals(c.name())));
   }

   @Test
   void malformedDatasetIdIsRejectedBeforeAnyHttpRequest() {
      // I1: TabularCatalogService.describeTable passes a caller-supplied target straight through
      // with no proof it ever came from listDatasets, so each of these would otherwise be
      // embedded verbatim into a live request URL by ElasticRestRuntime.getMetadata. Asserting on
      // OUR OWN message text (not just "it threw") is what proves validateIndexName caught this
      // before any request went out -- a real round trip against a malformed URL would surface a
      // connection-level or Elasticsearch-native error instead, never this literal phrase.
      List<String> malformed = List.of(
         "a/b", "a?b", "a#b", "a b", "a\"b", "a<b", "a>b", "a|b", "a,b", "a*b", "a\\b",
         ".", "..", "+leading");

      for(String id : malformed) {
         Exception thrown = assertThrows(Exception.class,
            () -> runtime.describeDataset(dataSource, id), "id='" + id + "'");
         assertTrue(thrown.getMessage().contains("not a legal Elasticsearch index name"),
                    "id='" + id + "': " + thrown.getMessage());
      }
   }

   @Test
   void aLegitimateDottedNameIsNotRejectedByValidation() throws Exception {
      // The whole point of this round was to PERMIT a dot in a dataset id (G1) -- a validator
      // that rejected one here would be a silent regression of that, not a safety fix. A non-
      // leading, non-".."/"." dot must sail through untouched.
      assertDoesNotThrow(() -> runtime.describeDataset(dataSource, "logs-2026.09.04"));
   }

   @Test
   void paramsUseTheQueryBeansOwnPropertyNames() throws Exception {
      // Derived, not written out: TabularUtil derives a property name from the getter through
      // java.beans.Introspector, so a getter rename must break this assertion rather than leave a
      // literal here green.
      Set<String> beanProperties = TabularUtil.getPropertyMap(ElasticRestQuery.class).keySet();
      Map<String, String> params = runtime.describeDataset(dataSource, "earthquakes").params();

      assertTrue(beanProperties.containsAll(params.keySet()),
                 params.keySet() + " must all be properties of ElasticRestQuery, which has " +
                 beanProperties);
      // Fully filled -- no empty value. wiz's buildMetadataTableBinding branches on that: with an
      // empty value it would tell the caller to fill something, and there is nothing to fill.
      params.forEach((key, value) -> assertFalse(value == null || value.isBlank(),
                                                 "params['" + key + "'] must not be empty"));
   }

   @Test
   void paramsRoundTripThroughTheBindingPathAndReturnEveryRow() throws Exception {
      TabularDatasetSchema schema = runtime.describeDataset(dataSource, "earthquakes");
      XTableNode table = runBinding(schema);
      int rows = 0;

      while(table.next()) {
         rows++;
      }

      // The whole point of size= in the suffix. Elasticsearch answers a search that does not ask
      // for more with 10 hits, and nothing in this connector raises that -- TabularQuery's own
      // max-rows caps the table after the fact and is never sent to the server. Without size= this
      // is permanently 10 no matter how many documents the index holds.
      assertEquals(221, rows, "the fixture holds 221 documents and the binding must return them all");
      assertTrue(schema.params().get("suffix").contains("size="), schema.params().toString());
   }

   @Test
   void aMultiIndexAliasIsRefusedRatherThanResolvedToOne() throws Exception {
      // GET /all-data/_mapping answers with one key per concrete index. Taking the first would
      // silently describe the wrong index under the requested name.
      Exception thrown = assertThrows(
         Exception.class, () -> runtime.describeDataset(dataSource, "all-data"));

      assertTrue(thrown.getMessage().contains("earthquakes") &&
                 thrown.getMessage().contains("orders"),
                 "the message must name what it did resolve to: " + thrown.getMessage());
   }

   @Test
   void anUnknownIndexFailsWithTheServersOwnExplanation() {
      Exception thrown = assertThrows(
         Exception.class, () -> runtime.describeDataset(dataSource, "nosuchindex"));

      assertTrue(thrown.getMessage().contains("404") &&
                 thrown.getMessage().contains("index_not_found_exception"),
                 thrown.getMessage());
   }

   @Test
   void metadataEndpointsAreReadWithGet() throws Exception {
      // The reason ElasticRestRuntime.getMetadata exists rather than reusing getConnection, which
      // calls setDoOutput(true) -- forcing a POST -- and writes the query's filter as the body.
      assertEquals(405, statusOf("POST", "/_cat/indices?format=json", null),
                   "_cat/indices is GET-only");
      assertEquals(400, statusOf("POST", "/earthquakes/_mapping", null),
                   "a bodyless POST to _mapping is refused");

      // And the sharper reason: POST to _mapping WITH a body is not a failed read, it is the
      // mapping-update API, and it writes. Proven on a throwaway index so the fixture is
      // untouched; the name starts with a dot only so listDatasets filters it and this test does
      // not change what the other tests see.
      request("PUT", "/.post-probe", null);
      assertEquals(200, statusOf("POST", "/.post-probe/_mapping",
                                 "{\"properties\":{\"writtenByPost\":{\"type\":\"keyword\"}}}"));
      assertTrue(serverFieldTypes(".post-probe").containsKey("writtenByPost"),
                 "a POST to _mapping modifies the index, so a catalog read must never use one");

      // The catalog reads both endpoints anyway, which it could not do through getConnection.
      assertFalse(runtime.listDatasets(dataSource).datasets().isEmpty());
      assertFalse(runtime.describeDataset(dataSource, "earthquakes").columns().isEmpty());
   }

   /**
    * Fills a query from a schema's own {@code params} through the same path
    * {@code WorksheetTableService} uses to build a binding, then runs it. The point is that
    * nothing here translates a name or a value: whatever {@code describeDataset} put in
    * {@code params} is what the fill receives.
    */
   private XTableNode runBinding(TabularDatasetSchema schema) throws Exception {
      ElasticRestQuery query = new ElasticRestQuery();
      query.setDataSource(dataSource);

      TabularQueryContractSupport.applyQueryContract(
         query, TabularUtil.getPropertyMap(query.getClass()),
         new TabularSchemaExtractor().extract(query, ElasticRestDataSource.TYPE),
         new LinkedHashMap<>(schema.params()), "elastic-test");

      assertEquals(schema.params().get("suffix"), query.getSuffix());
      assertEquals(schema.params().get("jsonPath"), query.getJsonPath());
      // params carries strings; the fill coerces this one back to the boolean the setter takes.
      assertFalse(query.isExpanded(), "expanded=false must survive the round trip as a boolean");

      XTableNode table = runtime.runQuery(query, new VariableTable());
      assertNotNull(table, "the query built from the catalog's own params must run");
      return table;
   }

   private static TabularColumn column(TabularDatasetSchema schema, String name) {
      return schema.columns().stream().filter(c -> name.equals(c.name())).findFirst()
         .orElseThrow(() -> new AssertionError(
            "no column '" + name + "' in " +
            schema.columns().stream().map(TabularColumn::name).toList()));
   }

   /** The {@code properties} block the server itself returns, in the order it returns it. */
   private static JsonNode serverMapping(String index) throws Exception {
      return MAPPER.readTree(request("GET", "/" + index + "/_mapping", null))
         .path(index).path("mappings").path("properties");
   }

   private static Map<String, String> serverFieldTypes(String index) throws Exception {
      JsonNode properties = serverMapping(index);
      Map<String, String> types = new LinkedHashMap<>();
      properties.fieldNames().forEachRemaining(
         name -> types.put(name, properties.path(name).path("type").asText(null)));
      return types;
   }

   private static void loadEarthquakes() throws IOException {
      try(InputStream input =
             ElasticCatalogTests.class.getResourceAsStream("earthquakes.ndjson")) {
         assertNotNull(input);
         StringBuilder bulk = new StringBuilder();

         try(LineIterator it = IOUtils.lineIterator(input, StandardCharsets.UTF_8)) {
            while(it.hasNext()) {
               String line = it.next();

               if(!line.trim().isEmpty()) {
                  bulk.append("{\"index\":{}}\n").append(line).append('\n');
               }
            }
         }

         request("POST", "/earthquakes/_bulk?refresh=true", bulk.toString(),
                 "application/x-ndjson");
      }
   }

   private static String request(String method, String path, String body) throws IOException {
      return request(method, path, body, "application/json");
   }

   private static String request(String method, String path, String body, String contentType)
      throws IOException
   {
      HttpURLConnection conn = (HttpURLConnection) new URL(getUrl() + path).openConnection();
      conn.setRequestMethod(method);
      conn.setRequestProperty("Content-Type", contentType);

      if(body != null) {
         conn.setDoOutput(true);

         try(OutputStream output = conn.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
         }
      }

      int status = conn.getResponseCode();

      try(InputStream input = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
         String response = input == null ? "" : IOUtils.toString(input, StandardCharsets.UTF_8);
         assertTrue(status < 400, method + " " + path + " answered " + status + ": " + response);
         return response;
      }
   }

   private static int statusOf(String method, String path, String body) throws IOException {
      HttpURLConnection conn = (HttpURLConnection) new URL(getUrl() + path).openConnection();
      conn.setRequestMethod(method);
      conn.setRequestProperty("Content-Type", "application/json");

      if(body != null) {
         conn.setDoOutput(true);

         try(OutputStream output = conn.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
         }
         catch(IOException ignore) {
            // A refused method can fail on the write rather than the read; the status is what
            // this method reports either way.
         }
      }

      return conn.getResponseCode();
   }

   private static String getUrl() {
      return "http://" + container.getHost() + ":" + container.getMappedPort(9200);
   }

   /**
    * Elasticsearch type to expected {@link XSchema} constant, written here independently of
    * {@code ElasticCatalog}'s own table so that a wrong entry there fails rather than agreeing
    * with itself. Which field has which Elasticsearch type is never written down — that comes
    * from the live mapping response.
    */
   private static final Map<String, String> EXPECTED_XSCHEMA = Map.of(
      "text", XSchema.STRING,
      "keyword", XSchema.STRING,
      "long", XSchema.LONG,
      "integer", XSchema.INTEGER,
      "float", XSchema.FLOAT,
      "double", XSchema.DOUBLE,
      "scaled_float", XSchema.DOUBLE,
      "boolean", XSchema.BOOLEAN,
      "date", XSchema.TIME_INSTANT,
      "binary", XSchema.STRING);

   private static final ObjectMapper MAPPER = new ObjectMapper();
}
