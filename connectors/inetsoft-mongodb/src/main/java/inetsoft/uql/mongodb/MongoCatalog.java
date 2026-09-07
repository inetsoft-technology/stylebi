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
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonValue;

import java.util.*;

/**
 * Assembles {@code MongoRuntime}'s {@link TabularCatalogProvider} answers. Mirrors the role
 * {@code AerospikeCatalog}/{@code CassandraCatalog} play for their runtimes: package-private,
 * static methods, no state of its own.
 *
 * MongoDB has no declared schema, and no server-side notion of a foreign key/reference between
 * collections, so unlike every JDBC-backed implementer of this SPI (Aerospike, Cassandra, Hive,
 * OrientDB), this one talks to the driver directly rather than through {@code java.sql}. The two
 * methods that actually reach the driver ({@link #listCollectionNames}/{@link #sampleDocuments})
 * are deliberately thin, single-call wrappers with nothing to get wrong — the same split
 * {@code GDataCatalog}/{@code GDataRuntime} use for the same reason (no live account/cluster in
 * this environment to exercise the fluent driver API against in a unit test), so the rest of this
 * class's logic can be unit-tested against fixed {@code List<String>}/{@code List<BsonDocument>}
 * input without mocking {@code FindIterable}/{@code MongoCursor} chains.
 */
final class MongoCatalog {
   private MongoCatalog() {
   }

   /**
    * Documents sampled per {@code describeDataset} call to infer a collection's columns. Matches
    * MongoDB Compass's own schema-analysis default (its "Sample Size" preference) — a precedent
    * for what a reasonable bounded scan looks like against an arbitrarily large collection, absent
    * any equivalent-purpose constant in the driver itself (unlike Aerospike, whose
    * {@code AerospikeSchemaBuilder} already ships a {@code DEFAULT_SCHEMA_BUILDER_MAX_RECORDS}).
    */
   static final int SAMPLE_SIZE = 100;

   static TabularCatalog listDatasets(MongoDatabase db) {
      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(String name : listCollectionNames(db)) {
         // A MongoDB collection name may legally contain '.' -- GridFS buckets (<bucket>.files /
         // <bucket>.chunks) are the common, officially-supported case, and a user is free to name
         // any collection that way. TabularDatasetRef.id's contract forbids '.', and
         // TabularCatalogService.validateDatasetIds aborts the WHOLE listDatasets call -- every
         // other, perfectly nameable collection in this database included -- the moment one
         // dotted name shows up. Excluding those names here, rather than letting one GridFS bucket
         // make an entire real-world database unannotatable, is the deliberate choice: it costs
         // coverage of the dotted collections themselves, never of any other collection.
         if(!name.contains(".")) {
            datasets.add(new TabularDatasetRef(name));
         }
      }

      // No connector-readable table-to-table reference construct exists in MongoDB itself --
      // same "nothing to honestly report" position AerospikeCatalog/CassandraCatalog/HiveCatalog
      // take for their own sources.
      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(MongoDatabase db, String datasetId) throws Exception {
      List<BsonDocument> sample = sampleDocuments(db, datasetId, SAMPLE_SIZE);

      if(sample.isEmpty()) {
         // find() on a nonexistent collection and find() on a genuinely empty one are the same
         // driver response (zero documents, no error) -- MongoDB does not distinguish them without
         // a separate listCollectionNames() round trip, and describeDataset's contract requires a
         // throw either way ("never with an empty column list"), so one message covering both
         // is honest rather than a guess at which one happened.
         throw new Exception("Collection '" + datasetId + "' in database '" + db.getName() +
            "' has no documents to sample (it may not exist, or may be empty); MongoDB has no " +
            "declared schema to fall back to.");
      }

      LinkedHashMap<String, String> columnTypes = new LinkedHashMap<>();
      Set<String> resolved = new HashSet<>();

      for(BsonDocument doc : sample) {
         for(Map.Entry<String, BsonValue> entry : doc.entrySet()) {
            String name = entry.getKey();
            BsonValue value = entry.getValue();

            if(!columnTypes.containsKey(name)) {
               // First-seen order across the scan, not a source-declared column order -- MongoDB
               // has none. Deterministic given a fixed sample, same reasoning AerospikeCatalog
               // documents for its own bin-union ordering.
               columnTypes.put(name, XSchema.STRING);
            }

            // Schema-less means the same key can legitimately hold different types across
            // documents (e.g. a numeric field that started as int32 and is now stored as
            // double). This connector does not detect or widen that: the first non-null value
            // seen for a key, in scan order, decides its reported type, and a later, differently
            // typed value for the same key is silently not reflected -- a real gap beyond what
            // columnsMayBeIncomplete already communicates, recorded as a known degradation.
            if(!resolved.contains(name) && value.getBsonType() != BsonType.NULL) {
               columnTypes.put(name, toXSchemaType(value.getBsonType()));
               resolved.add(name);
            }
         }
      }

      List<TabularColumn> columns = new ArrayList<>();

      for(Map.Entry<String, String> entry : columnTypes.entrySet()) {
         columns.add(new TabularColumn(entry.getKey(), entry.getValue()));
      }

      // MongoDB guarantees every document in a collection carries a unique '_id' -- the one
      // universal, source-guaranteed fact this schema-less store has to offer, not an inference.
      // Still gated on the column actually having been observed, rather than asserted
      // unconditionally, in case a projection or a pathological driver response ever omits it.
      List<String> keyColumns =
         columnTypes.containsKey("_id") ? List.of("_id") : List.of();

      return new TabularDatasetSchema(datasetId, columns, keyColumns,
         Map.of("queryString", aggregateQuery(datasetId)),
         // Every column above came from this same bounded scan, never from declared,
         // source-published metadata -- MongoDB has none to read instead. Same position
         // AerospikeCatalog takes; this connector has no path that would make it false.
         true);
   }

   /**
    * The one query shape {@code MongoRuntime.runQuery}/{@code getQueryResults} actually executes
    * for "every document in this collection" -- {@code MongoQuery}'s own dialog states outright
    * that "the find command is not supported", so an {@code aggregate} pipeline, not a {@code find}
    * command, is the only runnable shape to hand back as this dataset's {@code queryString}.
    * Built through {@code BsonDocument}/{@code toJson} rather than string concatenation so a
    * collection name containing a quote or other JSON-special character cannot produce malformed
    * JSON.
    */
   private static String aggregateQuery(String datasetId) {
      BsonDocument doc = new BsonDocument();
      doc.append("aggregate", new org.bson.BsonString(datasetId));
      doc.append("pipeline", new org.bson.BsonArray());
      return doc.toJson();
   }

   /**
    * An exhaustive switch expression over the driver's own enum: unlike a {@code Class}-keyed
    * mapping (Aerospike/Cassandra/Hive's own type tables), {@code BsonType} is a real Java enum,
    * so the compiler itself — not a hand-maintained count canary — rejects this file the moment a
    * future driver version adds a constant this switch does not name.
    *
    * Only the types a real inserted document can plausibly carry (DOUBLE, STRING, INT32, INT64,
    * BOOLEAN, DATE_TIME, DECIMAL128, OBJECT_ID, and the coarsened DOCUMENT/ARRAY/BINARY) are
    * exercised against a live MongoDB instance in this connector's test suite. The legacy/internal
    * BSON types below (SYMBOL, DB_POINTER, JAVASCRIPT[_WITH_SCOPE], MIN_KEY/MAX_KEY, UNDEFINED,
    * REGULAR_EXPRESSION, END_OF_DOCUMENT) are mapped from the driver's own enum documentation, not
    * from having observed one — the mongo-java-driver client itself does not offer a way to
    * construct most of them, and a real user document is not expected to contain any.
    *
    * Compound/opaque shapes (DOCUMENT, ARRAY, BINARY, ...) are reported as STRING rather than
    * modeled structurally -- XSchema has no nested-document or array type, so this is the same
    * kind of coarsening Cassandra's collection types (List/Map/Set/...) and Hive's ARRAY/MAP/
    * STRUCT already accept in this codebase, not a MongoDB-specific shortcut.
    */
   private static String toXSchemaType(BsonType type) {
      return switch(type) {
         case DOUBLE -> XSchema.DOUBLE;
         case STRING -> XSchema.STRING;
         case DOCUMENT -> XSchema.STRING;
         case ARRAY -> XSchema.STRING;
         case BINARY -> XSchema.STRING;
         case UNDEFINED -> XSchema.STRING;
         case OBJECT_ID -> XSchema.STRING;
         case BOOLEAN -> XSchema.BOOLEAN;
         case DATE_TIME -> XSchema.TIME_INSTANT;
         // A BSON null value never reaches this switch -- describeDataset only calls this method
         // once it has already confirmed getBsonType() != BsonType.NULL for that value.
         case NULL -> throw new IllegalStateException("BSON null has no column type");
         case REGULAR_EXPRESSION -> XSchema.STRING;
         case DB_POINTER -> XSchema.STRING;
         case JAVASCRIPT -> XSchema.STRING;
         case SYMBOL -> XSchema.STRING;
         case JAVASCRIPT_WITH_SCOPE -> XSchema.STRING;
         case INT32 -> XSchema.INTEGER;
         // BSON's internal replication timestamp type, not a user-facing date/time value --
         // confirmed against org.bson.BsonTimestamp: it is an (ordinal-seconds, increment) pair,
         // not a calendar date, so mapping it to TIME_INSTANT would misrepresent its unit.
         case TIMESTAMP -> XSchema.STRING;
         case INT64 -> XSchema.LONG;
         case DECIMAL128 -> XSchema.DECIMAL;
         case MIN_KEY -> XSchema.STRING;
         case MAX_KEY -> XSchema.STRING;
         // Never a real value's type -- an internal BSON parsing marker. Coarsened to STRING,
         // like every other shape this switch cannot model, rather than thrown: a describeDataset
         // call should not fail outright over one column it cannot type precisely.
         case END_OF_DOCUMENT -> XSchema.STRING;
      };
   }

   /**
    * The only place this class calls {@code MongoDatabase.listCollectionNames()} -- kept to a
    * single line so the unit tests below can substitute a fixed {@code List<String>} via
    * {@code MockedStatic<MongoCatalog>} instead of mocking the driver's {@code MongoIterable}.
    */
   static List<String> listCollectionNames(MongoDatabase db) {
      List<String> names = new ArrayList<>();

      for(String name : db.listCollectionNames()) {
         names.add(name);
      }

      return names;
   }

   /**
    * The only place this class runs a query against a collection -- kept to a single line for the
    * same test-seam reason as {@link #listCollectionNames}.
    */
   static List<BsonDocument> sampleDocuments(MongoDatabase db, String collectionName, int limit) {
      return db.getCollection(collectionName, BsonDocument.class)
         .find()
         .limit(limit)
         .into(new ArrayList<>());
   }
}
