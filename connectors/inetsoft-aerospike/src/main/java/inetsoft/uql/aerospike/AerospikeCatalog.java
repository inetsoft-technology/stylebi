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
package inetsoft.uql.aerospike;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Assembles AerospikeRuntime's TabularCatalogProvider answers from standard java.sql.
 * DatabaseMetaData calls. Mirrors HiveCatalog's role: package-private, static methods, no state of
 * its own.
 *
 * Confirmed against the driver (com.aerospike:aerospike-jdbc:1.10.1): AerospikeDatabaseMetadata.
 * getCatalogTerm() returns "namespace", getSchemaTerm() returns "", and getTables/getColumns/
 * getPrimaryKeys never reference their schema/schemaPattern argument. Every call below passes the
 * configured Namespace as catalog and null as schema.
 *
 * Every DatabaseMetaData row is additionally filtered by an exact match on TABLE_CAT/TABLE_NAME
 * against what the caller asked for, rather than trusting the driver's catalog/schemaPattern/
 * tableNamePattern arguments to have been matched as literal equality: JDBC allows these to be
 * treated as LIKE patterns, and namespace/datasetId are not guaranteed free of underscore/percent.
 * getColumns goes further than a LIKE-pattern risk: the driver compiles tableNamePattern as a Java
 * regex, and -- more importantly for cost, not just correctness -- runs one live "SELECT ... LIMIT
 * 1" against the cluster per regex-matched table (AerospikeDatabaseMetadata.getMetadata), unlike
 * getTables/getPrimaryKeys, which only filter already-fetched, cached cluster metadata in memory.
 * Passing "%" there unconditionally matches every set in the namespace and turns one
 * describeDataset call into one live query per set in the namespace; see describeColumns below for
 * how the pattern actually passed avoids that in the common case, and the one case (a literal '%'
 * in datasetId) where it cannot and falls back to the expensive-but-correct "%". This exact-match
 * filter is retained regardless, as the correctness backstop independent of which pattern was
 * passed.
 */
final class AerospikeCatalog {
   private AerospikeCatalog() {
   }

   static TabularCatalog listDatasets(Connection conn, String namespace) throws Exception {
      DatabaseMetaData meta = conn.getMetaData();
      List<TabularDatasetRef> datasets = new ArrayList<>();

      // null table types: Aerospike has no known equivalent of a view, and there is no way to
      // confirm any driver-specific TABLE_TYPE string without the jar. A wrong guess would
      // silently empty this list (the exact failure Hive's own TABLE_TYPES comment documents);
      // null is the JDBC-spec-guaranteed "table type not used to narrow the search" choice.
      try(ResultSet rs = meta.getTables(namespace, null, "%", null)) {
         while(rs.next()) {
            if(!namespace.equalsIgnoreCase(rs.getString("TABLE_CAT"))) {
               continue;
            }

            datasets.add(new TabularDatasetRef(rs.getString("TABLE_NAME")));
         }
      }

      // Aerospike exposes no driver-readable table-to-table reference/foreign-key construct --
      // same "nothing to honestly report" position CassandraCatalog/HiveCatalog take.
      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(Connection conn, String namespace, String datasetId)
      throws Exception
   {
      DatabaseMetaData meta = conn.getMetaData();
      List<TabularColumn> columns = describeColumns(meta, namespace, datasetId);

      if(columns.isEmpty()) {
         // getColumns() reports zero rows for an unknown set rather than throwing -- translate
         // that into the explicit throw TabularCatalogProvider#describeDataset requires for an
         // unknown dataset.
         throw new Exception("Set '" + datasetId + "' not found in namespace '" + namespace + "'.");
      }

      List<String> keyColumns = primaryKeyColumnNames(meta, namespace, datasetId);
      // Confirmed against the driver (com.aerospike:aerospike-jdbc:1.10.1):
      // AerospikeDatabaseMetadata.getIdentifierQuoteString() returns "\"" -- the driver declares
      // double quote as its identifier-quoting character -- and the driver's own internal metadata
      // query construction (AerospikeDatabaseMetadata.getMetadata) uses that exact quoted shape:
      // format("SELECT * FROM \"%s.%s\" LIMIT 1", namespace, table). The query text is parsed with
      // a full Calcite SQL grammar (AerospikeQuery.parse -> org.apache.calcite.sql.parser.
      // SqlParser), which has reserved keywords and requires quoting for identifiers containing
      // spaces or most special characters. Quoting unconditionally, doubling any embedded double
      // quote, mirrors the driver's own convention rather than guessing around it.
      //
      // This does not solve a set name containing a literal '.': AerospikeQuery.setTable
      // re-splits the already-parsed identifier text on a literal dot regardless of quoting -- a
      // driver-level limitation this connector does not attempt to work around.
      String query = "SELECT * FROM \"" + datasetId.replace("\"", "\"\"") + "\"";

      // Every column returned here came from the same bounded scan (AerospikeSchemaBuilder unions
      // bin names across at most 1000 records), never from declared, source-published metadata --
      // this connector has no path that would make columnsMayBeIncomplete false.
      return new TabularDatasetSchema(datasetId, columns, keyColumns,
         Map.of("queryString", query), true);
   }

   private static List<TabularColumn> describeColumns(DatabaseMetaData meta, String namespace,
                                                        String datasetId) throws Exception
   {
      AerospikeSQLTypes sqlTypes = new AerospikeSQLTypes();
      // Explicitly sorted by ORDINAL_POSITION rather than trusted from the driver's own row order.
      // ASSUMPTION: for a bin-unioned, Map-backed schema (per AerospikeSchemaBuilder), this
      // ordinal is at best an artifact of the driver's internal iteration order, not a real,
      // source-declared column order the way it is for Hive -- still the right implementation
      // choice (deterministic given a fixed ResultSet), but not confirmed meaningful.
      TreeMap<Integer, TabularColumn> byPosition = new TreeMap<>();

      // Confirmed against the driver: AerospikeDatabaseMetadata.getColumns compiles
      // tableNamePattern as a Java regex (only "%" is translated, to ".*"; every other regex
      // metacharacter passes through unescaped) and matches it with Matcher.matches() against each
      // real table name -- not a SQL LIKE pattern -- and then runs one live query per match
      // (getMetadata), so the pattern passed here also controls how many live queries this call
      // makes, not just correctness. columnsTableNamePattern(datasetId) resolves that: it quotes
      // datasetId so only the one real set matches (restoring a single live query), except for the
      // one datasetId shape that cannot be quoted through the driver's own "%"->".*" substitution,
      // where it falls back to the driver-matches-everything "%" from an earlier fix round. Either
      // way, the exact-match filter below (as listDatasets already does for getTables) is what
      // actually guarantees only this dataset's columns are returned.
      try(ResultSet rs = meta.getColumns(namespace, null, columnsTableNamePattern(datasetId), "%")) {
         while(rs.next()) {
            if(!namespace.equalsIgnoreCase(rs.getString("TABLE_CAT")) ||
               !datasetId.equals(rs.getString("TABLE_NAME")))
            {
               continue;
            }

            String type = sqlTypes.convertToXType(rs.getInt("DATA_TYPE"));
            byPosition.put(rs.getInt("ORDINAL_POSITION"),
               new TabularColumn(rs.getString("COLUMN_NAME"), type != null ? type : XSchema.STRING));
         }
      }

      return new ArrayList<>(byPosition.values());
   }

   // Confirmed against the driver: AerospikeDatabaseMetadata.getColumns/getTables both apply
   // tableNamePattern.replace("%", ".*") to the raw string *before* Pattern.compile ever sees it --
   // so any "%" character in the resulting string becomes a wildcard, whatever put it there,
   // including one Pattern.quote's own \Q...\E wrapper was relying on to be taken literally.
   // Pattern.quote(datasetId) contains a "%" only when datasetId itself does (none of the
   // characters \Q...\E adds is "%"), so for every datasetId without a literal "%" the quoted form
   // survives that substitution unchanged and Matcher.matches() accepts only the exact set name --
   // restoring the single-live-query cost getColumns had before an earlier fix round widened the
   // pattern to "%" to fix a different (regex-metacharacter) bug. Verified with a standalone
   // harness that a datasetId containing a literal "%" cannot be rescued the same way: quoting
   // "orders%2024" produces "\Qorders%2024\E", and replacing "%" there turns it into
   // "\Qorders.*2024\E" -- inside the now-corrupted \Q...\E wrapper, ".*" is required as two
   // literal characters, not a wildcard, so the resulting pattern no longer matches the real set
   // name "orders%2024" at all (and does not accidentally match some other real set instead; it
   // matches nothing). There is no way to escape a literal "%" through this driver's own
   // pre-substitution, so that one case falls back to "%", accepting the O(n)-live-queries-in-an-
   // n-set-namespace cost this connector's caller (describeColumns) already documents, with the
   // exact-match filter there as the correctness backstop.
   private static String columnsTableNamePattern(String datasetId) {
      return datasetId.contains("%") ? "%" : Pattern.quote(datasetId);
   }

   private static List<String> primaryKeyColumnNames(DatabaseMetaData meta, String namespace,
                                                       String datasetId)
   {
      TreeMap<Short, String> byKeySeq = new TreeMap<>();

      try(ResultSet rs = meta.getPrimaryKeys(namespace, null, datasetId)) {
         while(rs.next()) {
            if(!namespace.equalsIgnoreCase(rs.getString("TABLE_CAT")) ||
               !datasetId.equals(rs.getString("TABLE_NAME")))
            {
               continue;
            }

            byKeySeq.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
         }
      }
      catch(Exception ex) {
         // Whether an unsupported-method response surfaces as SQLFeatureNotSupportedException
         // specifically or a generic SQLException is driver-dependent and unconfirmed here, so a
         // broad catch is deliberate -- mirrors HiveCatalog.primaryKeyColumnNames. Treated as "this
         // set declares no primary key" rather than propagated -- unlike getTables/getColumns,
         // whose failures are almost always a real connection/permission problem and must
         // propagate.
         return List.of();
      }

      return new ArrayList<>(byKeySeq.values());
   }
}
