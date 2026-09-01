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
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.schema.*;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers charter assertions A1-A4 against {@link CassandraCatalog}, driven entirely off mocked
 * driver interfaces ({@code CqlSession}/{@code Metadata}/{@code KeyspaceMetadata}/
 * {@code TableMetadata}/{@code ColumnMetadata} are all interfaces in driver 4.x — see
 * docs/teams/2026-09-01-tabular-catalog-cassandra/04-build.md — so no {@code MockedStatic} seam is
 * needed the way SharePoint's Graph SDK required). {@code CqlIdentifier} is a real (final) value
 * type throughout, never mocked.
 */
@Tag("connector")
class CassandraCatalogTest {

   @Test
   void listDatasets_returnsAllTablesInKeyspace() throws Exception {
      TableMetadata users = table("users", List.of(), Map.of());
      TableMetadata orders = table("orders", List.of(), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(users, orders));

      TabularCatalog catalog = CassandraCatalog.listDatasets(session);

      assertEquals(2, catalog.datasets().size());
      assertEquals(List.of("users", "orders"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasets_datasetIdIsBareTableName_noKeyspaceQualifier() throws Exception {
      TableMetadata users = table("users", List.of(), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(users));

      TabularCatalog catalog = CassandraCatalog.listDatasets(session);

      assertEquals("users", catalog.datasets().get(0).id());
      assertFalse(catalog.datasets().get(0).id().contains("."));
   }

   @Test
   void listDatasets_keyspaceNotVisibleWithConfiguredCredentials_throws() {
      CqlSession session = mock(CqlSession.class);
      CqlIdentifier ksId = CqlIdentifier.fromInternal("myks");
      Metadata metadata = mock(Metadata.class);
      when(session.getKeyspace()).thenReturn(Optional.of(ksId));
      when(session.getMetadata()).thenReturn(metadata);
      // Driver represents "not visible" as an empty Optional, not an exception — see
      // CassandraCatalog.boundKeyspace's javadoc.
      when(metadata.getKeyspace(ksId)).thenReturn(Optional.empty());

      Exception ex = assertThrows(Exception.class, () -> CassandraCatalog.listDatasets(session));
      assertTrue(ex.getMessage().contains("myks"));
   }

   @Test
   void describeDataset_paramsHasLowercaseQueryStringKey_valueIsExecutableSelect()
      throws Exception
   {
      TableMetadata users = table("users",
         List.of(column("user_id", ProtocolConstants.DataType.INT)), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(users));

      TabularDatasetSchema schema = CassandraCatalog.describeDataset(session, "users");

      // Reverse: not "Query", not "queryStr", not the @Property(label=) text "Enter Query" — must
      // be exactly the CassandraQuery bean property name TabularUtil.getPropertyMap derives.
      assertEquals(Map.of("queryString", "SELECT * FROM users"), schema.params());
   }

   @Test
   void describeDataset_uppercaseTableName_generatesQuotedCql() throws Exception {
      // A2's reverse case: a lowercase-only fixture would pass even with the polarity of
      // asCql(boolean) backwards.
      TableMetadata mixedCase = table("Users",
         List.of(column("user_id", ProtocolConstants.DataType.INT)), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(mixedCase));

      TabularDatasetSchema schema = CassandraCatalog.describeDataset(session, "Users");

      assertEquals("SELECT * FROM \"Users\"", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_lowercaseTableName_noQuotesEmitted() throws Exception {
      TableMetadata users = table("users",
         List.of(column("user_id", ProtocolConstants.DataType.INT)), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(users));

      TabularDatasetSchema schema = CassandraCatalog.describeDataset(session, "users");

      assertEquals("SELECT * FROM users", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_keyColumnsIsPartitionKeyPlusClusteringColumns_inOrder() throws Exception {
      ColumnMetadata tenantId = column("tenant_id", ProtocolConstants.DataType.INT);
      ColumnMetadata region = column("region", ProtocolConstants.DataType.VARCHAR);
      ColumnMetadata createdAt = column("created_at", ProtocolConstants.DataType.TIMESTAMP);
      ColumnMetadata payload = column("payload", ProtocolConstants.DataType.VARCHAR);

      TableMetadata events = mock(TableMetadata.class);
      when(events.getName()).thenReturn(CqlIdentifier.fromInternal("events"));
      // Partition key has two columns and there are clustering columns — the case where
      // "partition key alone" and "full primary key" actually differ.
      when(events.getPartitionKey()).thenReturn(List.of(tenantId, region));
      Map<ColumnMetadata, ClusteringOrder> clustering = new LinkedHashMap<>();
      clustering.put(createdAt, ClusteringOrder.DESC);
      when(events.getClusteringColumns()).thenReturn(clustering);
      Map<CqlIdentifier, ColumnMetadata> allColumns = new LinkedHashMap<>();
      allColumns.put(tenantId.getName(), tenantId);
      allColumns.put(region.getName(), region);
      allColumns.put(createdAt.getName(), createdAt);
      allColumns.put(payload.getName(), payload);
      when(events.getColumns()).thenReturn(allColumns);

      CqlSession session = sessionBoundTo("myks", List.of(events));

      TabularDatasetSchema schema = CassandraCatalog.describeDataset(session, "events");

      assertEquals(List.of("tenant_id", "region", "created_at"), schema.keyColumns());
   }

   @Test
   void describeDataset_columnTypesComeFromCassandraTableGetType_notANewSwitch() throws Exception {
      // UUID is one of the eight Class<?> values CoreTool.getDataType has no dedicated branch
      // for, so it falls to STRING (see docs/teams/2026-09-01-tabular-catalog-cassandra/04-build.md
      // §"known coarsening"). If this ever produced anything other than STRING, either
      // CassandraTable.getType(DataType) stopped being the thing that ran, or a second, competing
      // mapping was added here in violation of A3.
      TableMetadata t = table("t", List.of(
         column("id", ProtocolConstants.DataType.BIGINT),
         column("token", ProtocolConstants.DataType.UUID)), Map.of());
      CqlSession session = sessionBoundTo("myks", List.of(t));

      TabularDatasetSchema schema = CassandraCatalog.describeDataset(session, "t");

      Map<String, String> typesByName = schema.columns().stream()
         .collect(java.util.stream.Collectors.toMap(TabularColumn::name, TabularColumn::type));
      assertEquals(XSchema.LONG, typesByName.get("id"));
      assertEquals(XSchema.STRING, typesByName.get("token"));
   }

   @Test
   void describeDataset_tableNotFound_throws() throws Exception {
      CqlSession session = sessionBoundTo("myks", List.of());

      Exception ex = assertThrows(Exception.class,
         () -> CassandraCatalog.describeDataset(session, "no_such_table"));
      assertTrue(ex.getMessage().contains("no_such_table"));
   }

   // ---- fixtures ----------------------------------------------------------------------------

   private static CqlSession sessionBoundTo(String keyspace, List<TableMetadata> tables) {
      CqlSession session = mock(CqlSession.class);
      CqlIdentifier ksId = CqlIdentifier.fromInternal(keyspace);
      when(session.getKeyspace()).thenReturn(Optional.of(ksId));

      Metadata metadata = mock(Metadata.class);
      when(session.getMetadata()).thenReturn(metadata);

      KeyspaceMetadata ks = mock(KeyspaceMetadata.class);
      when(ks.getName()).thenReturn(ksId);
      Map<CqlIdentifier, TableMetadata> tableMap = new LinkedHashMap<>();

      for(TableMetadata table : tables) {
         tableMap.put(table.getName(), table);
      }

      when(ks.getTables()).thenReturn(tableMap);
      when(metadata.getKeyspace(ksId)).thenReturn(Optional.of(ks));

      return session;
   }

   private static TableMetadata table(String name, List<ColumnMetadata> columns,
                                      Map<String, String> unused)
   {
      TableMetadata table = mock(TableMetadata.class);
      CqlIdentifier tableName = CqlIdentifier.fromInternal(name);
      when(table.getName()).thenReturn(tableName);
      when(table.getPartitionKey()).thenReturn(List.of());
      when(table.getClusteringColumns()).thenReturn(Map.of());

      Map<CqlIdentifier, ColumnMetadata> columnMap = new LinkedHashMap<>();

      for(ColumnMetadata col : columns) {
         columnMap.put(col.getName(), col);
      }

      when(table.getColumns()).thenReturn(columnMap);

      return table;
   }

   private static ColumnMetadata column(String name, int protocolCode) {
      ColumnMetadata col = mock(ColumnMetadata.class);
      when(col.getName()).thenReturn(CqlIdentifier.fromInternal(name));
      DataType type = mock(DataType.class);
      when(type.getProtocolCode()).thenReturn(protocolCode);
      when(col.getType()).thenReturn(type);

      return col;
   }
}
