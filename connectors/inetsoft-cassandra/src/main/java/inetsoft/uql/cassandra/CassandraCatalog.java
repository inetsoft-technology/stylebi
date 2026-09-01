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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assembles {@link CassandraRuntime}'s {@link TabularCatalogProvider} answers from the session's
 * own schema metadata. Mirrors {@code ODataCatalog}'s/{@code SharepointOnlineCatalog}'s role:
 * package-private, static methods, no state of its own.
 *
 * <p><b>Column types come from schema metadata, not from a live query.</b> An earlier design for
 * this round ran {@code SELECT * FROM t LIMIT 1} and fed the {@code ResultSet} to
 * {@link CassandraTable}'s constructor to reuse its (then-unextracted) {@code getType(int)}. That
 * was overturned: an unqualified {@code LIMIT 1} is a cross-node range scan on Cassandra (a known
 * anti-pattern, not just "one extra round trip"), annotation reading user data is a side effect
 * this SPI should not have, and it would have tangled {@code CassandraTable}'s session ownership
 * (it closes the session it's given once its result set is exhausted) into a method that never
 * meant to hand off that ownership. {@code TableMetadata.getColumns()} ->
 * {@code ColumnMetadata.getType()} gives the same {@code DataType} that a live query's
 * {@code ColumnDefinition.getType()} would, so {@link CassandraTable#getType(DataType)} (extracted
 * for exactly this reuse — see {@code CassandraTableTypeEquivalenceTest}) still applies unchanged.
 *
 * <p>No relationships this round: Cassandra has no driver-readable foreign-key/reference construct
 * between tables — same "nothing to honestly report" position {@code ODataCatalog} and
 * {@code SharepointOnlineCatalog} take when a source has no candidate they can verify.
 */
final class CassandraCatalog {
   private CassandraCatalog() {
   }

   static TabularCatalog listDatasets(CqlSession session) throws Exception {
      KeyspaceMetadata ks = boundKeyspace(session);
      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(TableMetadata table : ks.getTables().values()) {
         datasets.add(new TabularDatasetRef(table.getName().asInternal()));
      }

      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(CqlSession session, String datasetId)
      throws Exception
   {
      KeyspaceMetadata ks = boundKeyspace(session);
      TableMetadata table = ks.getTables().get(CqlIdentifier.fromInternal(datasetId));

      if(table == null) {
         throw new Exception("Table '" + datasetId + "' not found in keyspace '" +
            ks.getName().asInternal() + "'.");
      }

      List<TabularColumn> columns = describeColumns(table);
      List<String> keyColumns = primaryKeyColumnNames(table);

      // No explicit column list and no LIMIT: TabularUtil.getMaxRows applies the row cap
      // independently in CassandraRuntime.runQuery (CassandraRuntime.java:45), and an explicit
      // column list would freeze today's columns into the generated CQL, breaking silently once
      // the table's own columns change. table.getName() is already the CqlIdentifier this
      // TableMetadata carries — asCql(true) is called on it directly rather than re-deriving one
      // via CqlIdentifier.fromInternal(datasetId), which would just be a needless
      // asInternal()->fromInternal() round trip of the same identifier.
      String cql = "SELECT * FROM " + table.getName().asCql(true);

      return new TabularDatasetSchema(datasetId, columns, keyColumns,
         Map.of("queryString", cql));
   }

   /**
    * The keyspace the session is bound to, resolved to its live schema metadata.
    *
    * @throws Exception if the session has no bound keyspace (defensive — {@code getSession}
    *                    always calls {@code .withKeyspace(...)}), or if that keyspace is not
    *                    visible in the driver's metadata (unknown keyspace, or a permission
    *                    restriction the driver represents by simply omitting it rather than
    *                    raising an error of its own).
    */
   private static KeyspaceMetadata boundKeyspace(CqlSession session) throws Exception {
      CqlIdentifier ksId = session.getKeyspace().orElseThrow(() ->
         new Exception("Session has no bound keyspace."));

      return session.getMetadata().getKeyspace(ksId).orElseThrow(() ->
         new Exception("Keyspace '" + ksId.asInternal() +
            "' not found or not visible with the configured credentials."));
   }

   private static List<TabularColumn> describeColumns(TableMetadata table) {
      List<TabularColumn> columns = new ArrayList<>();

      for(ColumnMetadata col : table.getColumns().values()) {
         columns.add(new TabularColumn(col.getName().asInternal(),
            CoreTool.getDataType(CassandraTable.getType(col.getType()))));
      }

      return columns;
   }

   /**
    * The dataset's full primary key — partition key columns, then clustering columns, in that
    * order — not the partition key alone. A partition key by itself does not identify a single
    * row when the table has clustering columns; the full primary key does. Mirrors OData's
    * {@code keyColumns}, which reports the complete {@code <Key>}, not part of it.
    */
   private static List<String> primaryKeyColumnNames(TableMetadata table) {
      List<String> names = new ArrayList<>();

      for(ColumnMetadata col : table.getPartitionKey()) {
         names.add(col.getName().asInternal());
      }

      for(ColumnMetadata col : table.getClusteringColumns().keySet()) {
         names.add(col.getName().asInternal());
      }

      return names;
   }
}
