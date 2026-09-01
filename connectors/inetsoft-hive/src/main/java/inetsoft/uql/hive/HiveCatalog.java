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
package inetsoft.uql.hive;

import inetsoft.uql.jdbc.util.HiveSQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Assembles HiveRuntime's TabularCatalogProvider answers from standard java.sql.DatabaseMetaData
 * calls. Mirrors CassandraCatalog's role: package-private, static methods, no state of its own.
 *
 * Unlike Cassandra's driver types, Connection/DatabaseMetaData/ResultSet are JDK interfaces, not
 * hive-jdbc's own -- every method used here is mockable without the hive-jdbc driver jar on the
 * test classpath at all (only HiveRuntime's own getConnection/getDriver need the real driver to
 * compile, unchanged from before this SPI existed).
 *
 * Every DatabaseMetaData row is additionally filtered by an exact match on TABLE_SCHEM/TABLE_NAME
 * against what the caller asked for, rather than trusting the driver's catalog/schemaPattern/
 * tableNamePattern arguments to have been matched as literal equality: JDBC allows these to be
 * treated as LIKE patterns, and dbName/datasetId are not guaranteed free of underscore/percent.
 * Without this, a LIKE-pattern reading of the schema argument could leak tables from a second,
 * unintended database into listDatasets -- which would silently violate the "does not cross
 * databases" guarantee.
 */
final class HiveCatalog {
   private HiveCatalog() {
   }

   // "MATERIALIZED_VIEW" is the underscore form that ClassicTableTypeMapping$ClassicTableTypes.
   // MATERIALIZED_VIEW.toString() actually produces (that enum has no toString() override, so it
   // equals name()) -- not the space form "MATERIALIZED VIEW" that HiveDatabaseMetaData.
   // toJdbcTableType uses elsewhere for an unrelated client-side purpose. ClassicTableTypeMapping
   // is what HiveServer2 uses under its default hive.server2.table.type.mapping=CLASSIC, and
   // keeps materialized views as their own client-visible type rather than folding them into
   // VIEW the way it folds external tables into TABLE; omitting this entry silently drops every
   // materialized view from listDatasets under that default configuration.
   //
   // These three strings are ClassicTableTypeMapping client-type names, and this array only works
   // as intended because that is HiveServer2's compiled-in default mapping. If a deployment
   // overrides hive.server2.table.type.mapping to HIVE instead, HiveTableTypeMapping.mapToHiveType
   // resolves each string via TableType.valueOf(name); "TABLE" and "VIEW" have no matching native
   // TableType constant, so valueOf throws, the exception is caught, and the literal input string
   // is returned unchanged -- which then matches no real table's type. Under that configuration,
   // listDatasets silently returns an empty catalog for every ordinary table and view (no
   // exception, no log from this class); "MATERIALIZED_VIEW" happens to still resolve, since it is
   // coincidentally also a real TableType constant name, but that is not a general solution.
   private static final String[] TABLE_TYPES = {"TABLE", "VIEW", "MATERIALIZED_VIEW"};

   static TabularCatalog listDatasets(Connection conn, String dbName) throws Exception {
      DatabaseMetaData meta = conn.getMetaData();
      List<TabularDatasetRef> datasets = new ArrayList<>();

      try(ResultSet rs = meta.getTables(null, dbName, "%", TABLE_TYPES)) {
         while(rs.next()) {
            if(!dbName.equalsIgnoreCase(rs.getString("TABLE_SCHEM"))) {
               continue;
            }

            datasets.add(new TabularDatasetRef(rs.getString("TABLE_NAME")));
         }
      }

      // Hive exposes no driver-readable table-to-table reference/foreign-key construct -- same
      // "nothing to honestly report" position CassandraCatalog takes.
      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(Connection conn, String dbName, String datasetId)
      throws Exception
   {
      DatabaseMetaData meta = conn.getMetaData();
      List<TabularColumn> columns = describeColumns(meta, dbName, datasetId);

      if(columns.isEmpty()) {
         // getColumns() reports zero rows for an unknown table rather than throwing -- translate
         // that into the explicit throw TabularCatalogProvider#describeDataset requires for an
         // unknown dataset.
         throw new Exception("Table '" + datasetId + "' not found in database '" + dbName + "'.");
      }

      List<String> keyColumns = primaryKeyColumnNames(meta, dbName, datasetId);
      // hive.support.special.characters.tablename defaults to true (MetastoreConf.ConfVars.
      // SUPPORT_SPECICAL_CHARACTERS_IN_TABLE_NAMES), and its char[] of legal special characters
      // includes a literal backtick -- so datasetId is not guaranteed backtick-free even though
      // it is always a real, existing table name. An embedded backtick is escaped by doubling it,
      // which the Hive project documents as the escaping convention for backtick-quoted
      // identifiers (ASF JIRA HIVE-6013, "Supporting Quoted Identifiers in Column Names"). That
      // convention has not been exercised here against a live Hive parser -- the grammar that
      // would parse it ships in hive-exec, not in the hive-jdbc client artifact this module
      // compiles against.
      String query = "SELECT * FROM `" + datasetId.replace("`", "``") + "`";

      return new TabularDatasetSchema(datasetId, columns, keyColumns,
         Map.of("queryString", query));
   }

   private static List<TabularColumn> describeColumns(DatabaseMetaData meta, String dbName,
                                                        String datasetId) throws Exception
   {
      HiveSQLTypes sqlTypes = new HiveSQLTypes();
      // Explicitly sorted by ORDINAL_POSITION rather than trusted from the driver's own row
      // order -- JDBC's spec promises getColumns() is ordered by
      // TABLE_CAT, TABLE_SCHEM, TABLE_NAME, ORDINAL_POSITION, but that promise is not re-verified
      // here for this driver.
      TreeMap<Integer, TabularColumn> byPosition = new TreeMap<>();

      try(ResultSet rs = meta.getColumns(null, dbName, datasetId, "%")) {
         while(rs.next()) {
            if(!dbName.equalsIgnoreCase(rs.getString("TABLE_SCHEM")) ||
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

   private static List<String> primaryKeyColumnNames(DatabaseMetaData meta, String dbName,
                                                       String datasetId)
   {
      TreeMap<Short, String> byKeySeq = new TreeMap<>();

      try(ResultSet rs = meta.getPrimaryKeys(null, dbName, datasetId)) {
         while(rs.next()) {
            if(!dbName.equalsIgnoreCase(rs.getString("TABLE_SCHEM")) ||
               !datasetId.equals(rs.getString("TABLE_NAME")))
            {
               continue;
            }

            byKeySeq.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
         }
      }
      catch(Exception ex) {
         // Hive does not enforce primary keys, and hive-jdbc versions/deployments vary in their
         // support for getPrimaryKeys -- this failure could be either a JDK-standard
         // SQLFeatureNotSupportedException or a plain SQLException a HiveServer2 deployment wraps
         // an unsupported-method response in, so a broad catch is used deliberately rather than a
         // narrow SQLFeatureNotSupportedException one. Treated as "this table declares no primary
         // key" rather than propagated -- unlike getTables/getColumns, whose failures are almost
         // always a real connection/permission problem and must propagate.
         return List.of();
      }

      return new ArrayList<>(byKeySeq.values());
   }
}
