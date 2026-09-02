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

/**
 * Assembles AerospikeRuntime's TabularCatalogProvider answers from standard java.sql.
 * DatabaseMetaData calls. Mirrors HiveCatalog's role: package-private, static methods, no state of
 * its own.
 *
 * ASSUMPTION, not independently confirmed against the driver (no jar available this round): the
 * Aerospike JDBC driver maps namespace to the JDBC catalog argument and set to table, the reverse
 * of Hive's database-as-schema pairing. Every getTables/getColumns/getPrimaryKeys call below passes
 * the configured Namespace as catalog and null as schema on that assumption. If it is wrong, these
 * calls need catalog and schema swapped.
 *
 * Every DatabaseMetaData row is additionally filtered by an exact match on TABLE_CAT/TABLE_NAME
 * against what the caller asked for, rather than trusting the driver's catalog/schemaPattern/
 * tableNamePattern arguments to have been matched as literal equality: JDBC allows these to be
 * treated as LIKE patterns, and namespace/datasetId are not guaranteed free of underscore/percent.
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
      // ASSUMPTION, not independently confirmed against the driver: no quoting is applied to
      // datasetId. Hive's backtick-wrap was justified by a specific, cited Hive configuration
      // default; no equivalent citation is available for Aerospike's SQL layer, and set names are
      // constrained enough by the platform itself that quoting is very unlikely to be load-bearing
      // even if this guess turns out to be syntactically wrong.
      String query = "SELECT * FROM " + datasetId;

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

      try(ResultSet rs = meta.getColumns(namespace, null, datasetId, "%")) {
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
