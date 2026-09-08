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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-parsing tests -- no HTTP, no Spring. {@link GraphQLCatalog} takes the {@code __schema} node
 * exactly as {@link GraphQLIntrospectionClient#introspect} would hand it over. See
 * {@code GraphQLRuntimeCatalogTest} for the WireMock-backed cases that need a real round trip
 * (introspection failure modes, the {@code addParams} non-interference checks, and G17 through the
 * real {@link TabularCatalogService}).
 */
class GraphQLCatalogTest {

   private JsonNode schemaRoot(String resource) throws Exception {
      try(InputStream in = getClass().getResourceAsStream(resource)) {
         assertNotNull(in, "missing test resource " + resource);
         JsonNode root = new ObjectMapper().readTree(in);
         return root.path("data").path("__schema");
      }
   }

   // ===================================================================================
   // Enumeration and dataset inclusion/exclusion -- G2, G3
   // ===================================================================================

   @Test
   void listDatasetsIncludesConnectionsAndPlainListsExcludesLookupsAndSingletons() throws Exception {
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("bookstore-schema.json"));
      List<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id).toList();

      assertTrue(ids.contains("orders"), "orders (connection-shaped) must be a dataset");
      assertTrue(ids.contains("customers"), "customers (connection-shaped) must be a dataset");
      assertTrue(ids.contains("products"), "products (plain list) must be a dataset -- G3");
      assertFalse(ids.contains("customer"),
         "customer(id: ID!) is a single-object lookup, never a dataset -- Q1");
      assertFalse(ids.contains("shop"), "shop is a singleton, never a dataset -- Q1");
   }

   @Test
   void listDatasetsOrderIsStableAcrossRepeatedCalls() throws Exception {
      JsonNode schema = schemaRoot("bookstore-schema.json");
      List<String> first = GraphQLCatalog.listDatasets(schema).datasets().stream()
         .map(TabularDatasetRef::id).toList();
      List<String> second = GraphQLCatalog.listDatasets(schema).datasets().stream()
         .map(TabularDatasetRef::id).toList();

      assertEquals(first, second, "TabularCatalog.datasets() order must be stable across calls");
   }

   // ===================================================================================
   // Dataset description -- what Rule A discards -- G4
   // ===================================================================================

   @Test
   void describeDatasetDescriptionNamesTheDroppedDanglingEdgeAndItsTargetType() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");

      assertNotNull(orders.description());
      assertTrue(orders.description().contains("lineItems"),
         "must name the discarded field: " + orders.description());
      assertTrue(orders.description().contains("LineItem"),
         "must name the discarded field's target type: " + orders.description());
      assertTrue(orders.description().contains("no root Query field"),
         "must say WHY it was discarded: " + orders.description());
      // The source's own verbatim description stays a separate paragraph from the
      // connector-composed clauses -- never blended into one sentence (G19).
      assertTrue(orders.description().startsWith("A customer order."),
         "the source's own description must lead, verbatim: " + orders.description());
   }

   @Test
   void describeDatasetDescriptionDoesNotClaimAKeptReferenceWasDiscarded() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");

      // "customer" becomes a real relationship (G10) -- an implementation that dumps every
      // non-scalar field into the "discarded" list indiscriminately would make the previous test
      // pass by accident while lying about "customer".
      assertFalse(orders.description().contains("customer -> dropped"),
         "'customer' is a kept relationship, not a dropped one: " + orders.description());
      assertTrue(orders.description().contains("customer -> relationship"),
         "a kept reference must still be recorded (as kept): " + orders.description());
   }

   // ===================================================================================
   // Columns and type mapping -- G5, G6, G7, G8, G9(keyColumns)
   // ===================================================================================

   @Test
   void valueObjectIsFlattenedToDottedColumnsNeverAsOneJsonColumn() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      List<String> names = orders.columns().stream().map(TabularColumn::name).toList();

      assertTrue(names.contains("shipTo.city"));
      assertTrue(names.contains("shipTo.zip"));
      assertFalse(names.contains("shipTo"), "must never emit the value object itself as a column");
   }

   @Test
   void idColumnTypeIsAValidXSchemaConstantConsistentAcrossDatasets() throws Exception {
      JsonNode schema = schemaRoot("bookstore-schema.json");
      TabularDatasetSchema orders = GraphQLCatalog.describeDataset(schema, "orders");
      TabularDatasetSchema customers = GraphQLCatalog.describeDataset(schema, "customers");

      String ordersIdType = columnType(orders, "id");
      String customersIdType = columnType(customers, "id");

      assertEquals(XSchema.STRING, ordersIdType, "ID serializes as a string on the wire");
      assertEquals(ordersIdType, customersIdType, "the ID mapping must be consistent everywhere");
   }

   @Test
   void unrecognizedCustomScalarLandsOnNamedDefaultAndIsReportedLoudly() throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger(GraphQLCatalog.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      TabularDatasetSchema orders;

      try {
         orders = GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      }
      finally {
         logger.detachAppender(appender);
      }

      assertEquals(GraphQLCatalog.UNKNOWN_SCALAR_DEFAULT, columnType(orders, "total"),
         "an unrecognized custom scalar must still be present, on the named default -- never " +
         "silently absent");

      boolean warned = appender.list.stream().anyMatch(event ->
         event.getLevel() == Level.WARN && event.getFormattedMessage().contains("Money"));
      assertTrue(warned, "must WARN naming the unmapped scalar type ('Money'), got: " +
         appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
   }

   @Test
   void builtinScalarSetIsPinnedAtExactlyFive() {
      assertEquals(5, GraphQLCatalog.BUILTIN_SCALARS.size());
      assertEquals(Set.of("ID", "String", "Int", "Float", "Boolean"), GraphQLCatalog.BUILTIN_SCALARS);
   }

   @Test
   void columnsMayBeIncompleteIsAlwaysFalse() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      assertFalse(orders.columnsMayBeIncomplete(),
         "introspection is declarative schema, never a bounded scan");
   }

   @Test
   void keyColumnsIsAlwaysEmpty_neverGuessedFromAnIdKindField() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      assertEquals(List.of(), orders.keyColumns(),
         "vanilla introspection declares no primary key -- guessing 'id' by name would be the " +
         "exact inference the SPI forbids");
   }

   // ===================================================================================
   // Binding parameters -- G9
   // ===================================================================================

   @Test
   void paramsKeysAreRealGraphQLQueryBeanPropertiesNeverTheCharterParaphrase() throws Exception {
      Set<String> realProperties = TabularUtil.getPropertyMap(GraphQLQuery.class).keySet();
      // The charter's very first draft named this "paginationJsonPath" -- not a real property.
      assertTrue(realProperties.contains("paginationCountPath"));
      assertFalse(realProperties.contains("paginationJsonPath"));

      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");

      for(String key : orders.params().keySet()) {
         assertTrue(realProperties.contains(key), "params key '" + key + "' is not a real " +
            "GraphQLQuery bean property (derived from TabularUtil.getPropertyMap) -- a real " +
            "binding could never fill it");
      }

      assertTrue(orders.params().containsKey("paginationCountPath"));
      assertFalse(orders.params().containsKey("paginationJsonPath"));
   }

   @Test
   void queryStringAndVariablesAreEmptyPaginationParamsAreFilled_connectionShapedDataset()
      throws Exception
   {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      Map<String, String> params = orders.params();

      assertEquals("", params.get("queryString"), "user intent -- must not be catalog-invented");
      assertEquals("", params.get("variables"), "user intent -- Q2");
      assertEquals("true", params.get("usePagination"));
      assertEquals("true", params.get("cursorPagination"));
      assertEquals("after", params.get("paginationVariable"),
         "must be the REAL arg name read off introspection, not a guessed convention name");
      assertFalse(params.get("paginationCountPath").isBlank());
      assertTrue(params.get("jsonPath").contains("orders"));
   }

   @Test
   void plainListDatasetGetsAnExplicitPaginationChoiceNotSilence() throws Exception {
      TabularDatasetSchema products =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "products");
      Map<String, String> params = products.params();

      // products has no args at all -- an explicit "false", never an absent/null key.
      assertEquals("false", params.get("usePagination"));
      assertEquals("", params.get("paginationVariable"));
      assertEquals("", params.get("paginationCountPath"));
      assertTrue(params.containsKey("usePagination"), "the choice must be explicit, not omitted");
   }

   @Test
   void paginationHeuristicFallsThroughSafelyWhenArgsMatchNeitherPattern() throws Exception {
      // "logs" is connection-shaped but its only arg is "batchToken" -- matches neither the
      // cursor (after/cursor) nor the offset (offset/skip/page) pattern. Charter G21.
      TabularDatasetSchema logs =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "logs");
      Map<String, String> params = logs.params();

      assertEquals("false", params.get("usePagination"));
      assertEquals("false", params.get("cursorPagination"));
      assertEquals("", params.get("paginationVariable"));
      assertEquals("", params.get("paginationCountPath"));
      assertFalse(logs.columns().isEmpty(), "must not throw or come back empty");
   }

   // ===================================================================================
   // Relationships -- G10, G11
   // ===================================================================================

   @Test
   void toOneReferenceToADatasetYieldsARelationship() throws Exception {
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("bookstore-schema.json"));
      TabularRelationship rel = relationshipNamed(catalog, "orders.customer");

      assertNotNull(rel, "orders.customer -> customers relationship must exist");
      assertEquals("orders", rel.fromDataset());
      assertEquals("customers", rel.toDataset());
      assertEquals(List.of("customer.id"), rel.fromColumns());
      assertEquals(List.of("id"), rel.toColumns());
   }

   @Test
   void toOneReferenceAlsoAddsTheFlattenedIdColumnOnTheReferencingDataset() throws Exception {
      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      assertEquals(XSchema.STRING, columnType(orders, "customer.id"));
   }

   @Test
   void danglingToManyReferenceNeverAppearsAsARelationshipEndpoint() throws Exception {
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("bookstore-schema.json"));

      for(TabularRelationship rel : catalog.relationships()) {
         assertNotEquals("LineItem", rel.toDataset());
         assertNotEquals("LineItem", rel.fromDataset());
      }

      Set<String> datasetIds = catalog.datasets().stream().map(TabularDatasetRef::id)
         .collect(java.util.stream.Collectors.toSet());

      for(TabularRelationship rel : catalog.relationships()) {
         assertTrue(datasetIds.contains(rel.fromDataset()), "no dangling fromDataset endpoint");
         assertTrue(datasetIds.contains(rel.toDataset()), "no dangling toDataset endpoint");
      }
   }

   @Test
   void toManyReferenceWithATargetBackReferenceIsExpressedFromTheChildSide() throws Exception {
      // Order.reviews: [Review!]! (to-many, plain list) -- Review IS a root-level dataset
      // ("reviews") and carries its own to-one back-reference field "order" pointing at Order.
      // The edge must be built from the CHILD's ("reviews") own side -- reconcile Δ2 case (a).
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("bookstore-schema.json"));
      TabularRelationship rel = relationshipNamed(catalog, "reviews.order");

      assertNotNull(rel, "expected a single 'reviews.order' relationship, got: " +
         catalog.relationships());
      assertEquals("reviews", rel.fromDataset());
      assertEquals("orders", rel.toDataset());
      assertEquals(List.of("order.id"), rel.fromColumns());
      assertEquals(List.of("id"), rel.toColumns());

      // Computed independently from BOTH directions (Order's own to-many field, and Review's own
      // to-one back-reference field) -- must collapse to exactly ONE relationship, not two
      // duplicate entries under the same name (which TabularCatalogService would reject).
      long matching = catalog.relationships().stream()
         .filter(r -> "reviews.order".equals(r.name())).count();
      assertEquals(1, matching, "the two independent computations of the same edge must " +
         "de-duplicate to exactly one relationship");
   }

   @Test
   void toManyReferenceWithNoBackReferenceIsDroppedNotGuessed() throws Exception {
      // Order.tags: [Tag!]! -- Tag IS a root-level dataset ("tags"), but has no field pointing
      // back to Order. Must be dropped and recorded, never emitted with an invented fromColumns.
      // Reconcile Δ2 case (b).
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("bookstore-schema.json"));
      assertNull(relationshipNamed(catalog, "tags.order"));

      for(TabularRelationship rel : catalog.relationships()) {
         assertFalse("orders".equals(rel.fromDataset()) && "tags".equals(rel.toDataset()));
         assertFalse("tags".equals(rel.fromDataset()) && "orders".equals(rel.toDataset()));
      }

      TabularDatasetSchema orders =
         GraphQLCatalog.describeDataset(schemaRoot("bookstore-schema.json"), "orders");
      assertTrue(orders.description().contains("tags -> dropped"),
         "must record the drop: " + orders.description());
      assertTrue(orders.description().contains("no to-one back-reference"),
         "must say WHY: " + orders.description());
      assertTrue(orders.columns().stream().noneMatch(c -> c.name().startsWith("tags")),
         "no invented column for a to-many field with no join column");
   }

   @Test
   void relationshipNamesAreInjectiveOverDatasetAndColumn_dotSeparatorNeverCollides()
      throws Exception
   {
      TabularCatalog catalog = GraphQLCatalog.listDatasets(schemaRoot("naming-collision-schema.json"));

      // Joined with '_' these would BOTH become "orders_customer_id" -- a real collision. Joined
      // with '.' (GraphQL names never contain '.') they stay distinct.
      assertNotNull(relationshipNamed(catalog, "orders_customer.id"));
      assertNotNull(relationshipNamed(catalog, "orders.customer_id"));
      assertNotEquals(relationshipNamed(catalog, "orders_customer.id"),
         relationshipNamed(catalog, "orders.customer_id"));

      Set<String> names = catalog.relationships().stream().map(TabularRelationship::name)
         .collect(java.util.stream.Collectors.toSet());
      assertEquals(catalog.relationships().size(), names.size(), "no name collisions at all");
   }

   // ===================================================================================
   // Depth limits -- G22
   // ===================================================================================

   @Test
   void ofTypeChainDeeperThanSixLevelsLandsOnUnknownScalarDefaultRatherThanCrashing()
      throws Exception
   {
      TabularDatasetSchema deep =
         GraphQLCatalog.describeDataset(schemaRoot("depth-limit-schema.json"), "deepRecords");
      assertEquals(GraphQLCatalog.UNKNOWN_SCALAR_DEFAULT, columnType(deep, "value"),
         "a type nested deeper than MAX_UNWRAP_DEPTH must default, never throw");
   }

   @Test
   void doublyNestedValueObjectIsDroppedNotRecursedIntoOrCrashed() throws Exception {
      TabularDatasetSchema deep =
         GraphQLCatalog.describeDataset(schemaRoot("depth-limit-schema.json"), "deepRecords");
      List<String> names = deep.columns().stream().map(TabularColumn::name).toList();

      assertTrue(names.contains("wrapper.flat"), "one level of flattening must still work");
      assertFalse(names.contains("wrapper.inner"), "a doubly-nested object is not flattened");
      assertFalse(names.contains("wrapper.inner.deep"), "never recursed into");
   }

   // ===================================================================================
   // describeDataset error handling
   // ===================================================================================

   @Test
   void describeDatasetForANonDatasetIdThrows() throws Exception {
      JsonNode schema = schemaRoot("bookstore-schema.json");
      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> GraphQLCatalog.describeDataset(schema, "customer"));
      assertTrue(ex.getMessage().contains("customer"));
   }

   @Test
   void describeDatasetForAnUnknownIdThrows() throws Exception {
      JsonNode schema = schemaRoot("bookstore-schema.json");
      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> GraphQLCatalog.describeDataset(schema, "no-such-dataset"));
      assertTrue(ex.getMessage().contains("no-such-dataset"));
   }

   // ===================================================================================
   // helpers
   // ===================================================================================

   private static String columnType(TabularDatasetSchema schema, String columnName) {
      return schema.columns().stream().filter(c -> columnName.equals(c.name()))
         .map(TabularColumn::type).findFirst()
         .orElseThrow(() -> new AssertionError("no column named '" + columnName + "' in " +
            schema.columns()));
   }

   private static TabularRelationship relationshipNamed(TabularCatalog catalog, String name) {
      return catalog.relationships().stream().filter(r -> name.equals(r.name())).findFirst()
         .orElse(null);
   }
}
