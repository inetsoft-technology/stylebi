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
package inetsoft.uql.mongodb;

import com.mongodb.client.MongoDatabase;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.bson.*;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers {@link MongoCatalog}, driven off its two extracted static fetch methods
 * ({@link MongoCatalog#listCollectionNames}/{@link MongoCatalog#sampleDocuments}) mocked via
 * {@code MockedStatic} -- the same pattern {@code GDataCatalogTest} uses for {@code GDataRuntime},
 * for the same reason: there is no live MongoDB cluster to exercise the driver's fluent
 * {@code FindIterable}/{@code MongoCursor} API against in this environment. The honest ceiling
 * this suite holds to is contract correct, unit tests discriminating, degradations recorded --
 * separately, {@code MongoRuntimeTest} exercises the same code path against a real
 * testcontainers-launched MongoDB instance.
 */
class MongoCatalogTest {
   private MockedStatic<MongoCatalog> catalogStatic;

   @AfterEach
   void closeStaticMock() {
      if(catalogStatic != null) {
         catalogStatic.close();
      }
   }

   private MongoDatabase fakeDb() {
      MongoDatabase db = mock(MongoDatabase.class);
      when(db.getName()).thenReturn("test");
      return db;
   }

   private void stubCollectionNames(MongoDatabase db, List<String> names) {
      catalogStatic = mockStatic(MongoCatalog.class, org.mockito.Mockito.CALLS_REAL_METHODS);
      catalogStatic.when(() -> MongoCatalog.listCollectionNames(db)).thenReturn(names);
   }

   private void stubSample(MongoDatabase db, String collection, List<BsonDocument> docs) {
      if(catalogStatic == null) {
         catalogStatic = mockStatic(MongoCatalog.class, org.mockito.Mockito.CALLS_REAL_METHODS);
      }

      catalogStatic.when(() -> MongoCatalog.sampleDocuments(eq(db), eq(collection), any(Integer.class)))
         .thenReturn(docs);
   }

   // ----- listDatasets -----

   @Test
   void listDatasets_returnsOneRefPerCollection() {
      MongoDatabase db = fakeDb();
      stubCollectionNames(db, List.of("orders", "customers"));

      TabularCatalog catalog = MongoCatalog.listDatasets(db);

      assertEquals(Set.of("orders", "customers"),
         Set.copyOf(catalog.datasets().stream().map(TabularDatasetRef::id).toList()));
      assertTrue(catalog.relationships().isEmpty());
   }

   @Test
   void listDatasets_excludesDottedNames_keepsEveryOtherCollection() {
      // GridFS's own naming convention (<bucket>.files / <bucket>.chunks) is the common real-world
      // case a dotted collection name comes from -- TabularDatasetRef.id's contract forbids '.',
      // and TabularCatalogService aborts the WHOLE listDatasets call over a single violation, so
      // this asserts the two GridFS-shaped names are dropped without taking "orders" down with them.
      MongoDatabase db = fakeDb();
      stubCollectionNames(db, List.of("orders", "fs.files", "fs.chunks"));

      TabularCatalog catalog = MongoCatalog.listDatasets(db);

      assertEquals(List.of("orders"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_emptyDatabase_returnsEmptyCatalog() {
      // TabularCatalogService.listTables, not this method, is responsible for rejecting an empty
      // catalog -- listDatasets itself must not manufacture a fake dataset to avoid returning
      // empty.
      MongoDatabase db = fakeDb();
      stubCollectionNames(db, List.of());

      TabularCatalog catalog = MongoCatalog.listDatasets(db);

      assertTrue(catalog.datasets().isEmpty());
   }

   // ----- describeDataset: contract-level -----

   @Test
   void describeDataset_noSampledDocuments_throws() {
      MongoDatabase db = fakeDb();
      stubSample(db, "empty_or_missing", List.of());

      Exception ex = assertThrows(Exception.class,
         () -> MongoCatalog.describeDataset(db, "empty_or_missing"));

      assertTrue(ex.getMessage().contains("empty_or_missing"));
      assertTrue(ex.getMessage().contains("no documents to sample"));
   }

   @Test
   void describeDataset_echoesDatasetIdAndMarksColumnsIncomplete() throws Exception {
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument("_id", new BsonObjectId(new ObjectId()));
      stubSample(db, "orders", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "orders");

      assertEquals("orders", schema.datasetId());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_idPresent_reportedAsKeyColumn() throws Exception {
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("name", new BsonString("AT&T"));
      stubSample(db, "orders", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "orders");

      assertEquals(List.of("_id"), schema.keyColumns());
   }

   @Test
   void describeDataset_paramsKeyMatchesMongoQuerysOwnBeanProperty() throws Exception {
      // Verifies the "queryString" literal used to key params actually is MongoQuery's own bean
      // property name (java.beans.Introspector.decapitalize("QueryString") has no
      // both-first-two-chars-uppercase surprise here, but derive it rather than trust that by
      // inspection alone) -- a mismatched key would make StyleBI's applyQueryContract silently
      // refuse to fill it.
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument("_id", new BsonObjectId(new ObjectId()));
      stubSample(db, "orders", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "orders");

      Set<String> mongoQueryProperties = TabularUtil.getPropertyMap(MongoQuery.class).keySet();
      assertTrue(schema.params().keySet().stream().allMatch(mongoQueryProperties::contains),
         "params key(s) " + schema.params().keySet() + " must be among MongoQuery's own bean " +
            "properties " + mongoQueryProperties);
      assertTrue(schema.params().containsKey("queryString"));
   }

   @Test
   void describeDataset_paramsGivesAnAggregatePipeline_notAFindCommand() throws Exception {
      // MongoQuery's own dialog states "the find command is not supported" -- the generated
      // queryString must be shaped as MongoRuntime.getQueryResults actually dispatches it.
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument("_id", new BsonObjectId(new ObjectId()));
      stubSample(db, "orders", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "orders");
      Document parsed = Document.parse(schema.params().get("queryString"));

      assertEquals("orders", parsed.get("aggregate"));
      assertTrue(parsed.get("pipeline") instanceof List);
   }

   @Test
   void describeDataset_collectionNameWithQuote_stillProducesValidJson() throws Exception {
      MongoDatabase db = fakeDb();
      String weirdName = "weird\"name";
      BsonDocument doc = new BsonDocument("_id", new BsonObjectId(new ObjectId()));
      stubSample(db, weirdName, List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, weirdName);

      // Built through BsonDocument/toJson rather than string concatenation -- would throw here
      // (or silently mis-scope) if it were naive concatenation instead.
      Document parsed = Document.parse(schema.params().get("queryString"));
      assertEquals(weirdName, parsed.get("aggregate"));
   }

   // ----- describeDataset: type mapping -----

   @Test
   void describeDataset_mapsCommonBsonTypes() throws Exception {
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("aDouble", new BsonDouble(1.5))
         .append("aString", new BsonString("x"))
         .append("anInt32", new BsonInt32(7))
         .append("anInt64", new BsonInt64(7L))
         .append("aBool", new BsonBoolean(true))
         .append("aDate", new BsonDateTime(0L))
         .append("aDecimal", new BsonDecimal128(new Decimal128(42)))
         .append("aNestedDoc", new BsonDocument("k", new BsonString("v")))
         .append("anArray", new BsonArray(List.of(new BsonInt32(1))))
         .append("aBinary", new BsonBinary(new byte[] {1, 2, 3}));
      stubSample(db, "t", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "t");
      Map<String, String> types = new HashMap<>();
      schema.columns().forEach(c -> types.put(c.name(), c.type()));

      assertEquals(XSchema.STRING, types.get("_id"));
      assertEquals(XSchema.DOUBLE, types.get("aDouble"));
      assertEquals(XSchema.STRING, types.get("aString"));
      assertEquals(XSchema.INTEGER, types.get("anInt32"));
      assertEquals(XSchema.LONG, types.get("anInt64"));
      assertEquals(XSchema.BOOLEAN, types.get("aBool"));
      assertEquals(XSchema.TIME_INSTANT, types.get("aDate"));
      assertEquals(XSchema.DECIMAL, types.get("aDecimal"));
      // Compound/opaque shapes have no XSchema equivalent -- coarsened to STRING, the same
      // treatment Cassandra/Hive's own uncovered types already receive in this codebase.
      assertEquals(XSchema.STRING, types.get("aNestedDoc"));
      assertEquals(XSchema.STRING, types.get("anArray"));
      assertEquals(XSchema.STRING, types.get("aBinary"));
   }

   @Test
   void describeDataset_unionsKeysAcrossSampledDocuments() throws Exception {
      MongoDatabase db = fakeDb();
      BsonDocument doc1 = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("onlyInFirst", new BsonString("a"));
      BsonDocument doc2 = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("onlyInSecond", new BsonInt32(2));
      stubSample(db, "t", List.of(doc1, doc2));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "t");
      Set<String> names = new HashSet<>();
      schema.columns().forEach(c -> names.add(c.name()));

      assertEquals(Set.of("_id", "onlyInFirst", "onlyInSecond"), names);
   }

   @Test
   void describeDataset_nullInEarlierDocument_doesNotBlockLaterTypeResolution() throws Exception {
      // A key that is null in the first document sampled and a real value in a later one must
      // still resolve to that later value's type, not fall back to the STRING placeholder a
      // never-resolved key would get.
      MongoDatabase db = fakeDb();
      BsonDocument doc1 = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("qty", new BsonNull());
      BsonDocument doc2 = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("qty", new BsonInt64(5L));
      stubSample(db, "t", List.of(doc1, doc2));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "t");
      String qtyType = schema.columns().stream()
         .filter(c -> c.name().equals("qty")).findFirst().orElseThrow().type();

      assertEquals(XSchema.LONG, qtyType);
   }

   @Test
   void describeDataset_keyNeverNonNull_defaultsToString() throws Exception {
      MongoDatabase db = fakeDb();
      BsonDocument doc = new BsonDocument()
         .append("_id", new BsonObjectId(new ObjectId()))
         .append("alwaysNull", new BsonNull());
      stubSample(db, "t", List.of(doc));

      TabularDatasetSchema schema = MongoCatalog.describeDataset(db, "t");
      String type = schema.columns().stream()
         .filter(c -> c.name().equals("alwaysNull")).findFirst().orElseThrow().type();

      assertEquals(XSchema.STRING, type);
   }
}
