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

import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers charter assertions A1, A2, and A4 against {@link AerospikeCatalog}, driven entirely off
 * mocked {@code Connection}/{@code DatabaseMetaData}/{@code ResultSet} — all three are
 * {@code java.sql} JDK interfaces, so no aerospike-jdbc driver class is on the compile/test
 * classpath for this file (D7: mock JDK interfaces, no test-scope driver dependency).
 *
 * <p>The {@code catalog}/{@code schema} arguments {@link AerospikeCatalog} passes to
 * {@code getTables}/{@code getColumns}/{@code getPrimaryKeys} are asserted here as
 * {@code catalog=namespace, schema=null} per the charter's own (unverified against the driver —
 * no jar available) finding that this driver maps namespace to catalog and set to table, the
 * reverse of Hive's database-as-schema pairing.
 *
 * <p>{@code SQLTypes} (the superclass {@link AerospikeSQLTypes} inherits {@code convertToXType}
 * from) has a one-time static field initializer that reads {@code SreeEnv.getProperty} ->
 * {@code PropertiesEngine.getInstance()}, so the first reference to either class anywhere in this
 * JVM needs a Spring-free stand-in for that singleton (mirrors {@code HiveCatalogTest}'s own
 * {@code mockStatic(PropertiesEngine.class)} pattern).
 */
class AerospikeCatalogTest {
   private static MockedStatic<PropertiesEngine> propertiesEngineStatic;

   @BeforeAll
   static void mockPropertiesEngine() {
      PropertiesEngine engine = mock(PropertiesEngine.class);
      when(engine.getProperty(anyString(), anyBoolean())).thenReturn(null);
      propertiesEngineStatic = mockStatic(PropertiesEngine.class);
      propertiesEngineStatic.when(PropertiesEngine::getInstance).thenReturn(engine);
   }

   @AfterAll
   static void resetPropertiesEngine() {
      propertiesEngineStatic.close();
   }

   // ---- listDatasets --------------------------------------------------------------------------

   @Test
   void listDatasets_returnsSets_scopedToConfiguredNamespace() throws Exception {
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders"),
         row("TABLE_CAT", "test", "TABLE_NAME", "customers")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(eq("test"), isNull(), eq("%"), isNull())).thenReturn(rs);

      TabularCatalog catalog = AerospikeCatalog.listDatasets(conn, "test");

      assertEquals(List.of("orders", "customers"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasets_passesNullTableTypes_notAGuessedArray() throws Exception {
      // Opposite of Hive's explicit three-string TABLE_TYPES array: Aerospike has no known
      // equivalent of a view, and a wrong guess at a driver-specific TABLE_TYPE string would
      // silently empty this list (the exact failure Hive's own comment warns about). null is the
      // JDBC-spec-guaranteed "table type not used to narrow the search" choice.
      ResultSet rs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      AerospikeCatalog.listDatasets(conn, "test");

      ArgumentCaptor<String[]> typesCaptor = ArgumentCaptor.forClass(String[].class);
      verify(meta).getTables(any(), any(), any(), typesCaptor.capture());
      assertNull(typesCaptor.getValue());
   }

   @Test
   void listDatasets_filtersOutRowsFromDifferentNamespace_evenIfDriverReturnsThem()
      throws Exception
   {
      // A defensive filter: JDBC allows the catalog argument to be treated as a LIKE pattern, so a
      // driver that over-matches must not be trusted to have already scoped its rows to "test".
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders"),
         row("TABLE_CAT", "test_archive", "TABLE_NAME", "old_orders")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      TabularCatalog catalog = AerospikeCatalog.listDatasets(conn, "test");

      assertEquals(List.of("orders"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_emptyNamespace_returnsEmptyCatalog() throws Exception {
      ResultSet rs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      TabularCatalog catalog = AerospikeCatalog.listDatasets(conn, "test");

      assertEquals(List.of(), catalog.datasets());
   }

   // ---- describeDataset: A1 (queryString param) -------------------------------------------------

   @Test
   void describeDataset_paramsHasLowercaseQueryStringKey_unqualifiedQuotedSelect()
      throws Exception
   {
      Connection conn = connectionWithColumns("test", "orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      // Reverse: not "QueryString", not "sql", not the @Property(label=) text "Enter SQL" -- must
      // be exactly the AerospikeQuery bean property name TabularUtil.getPropertyMap derives.
      assertEquals(Map.of("queryString", "SELECT * FROM \"orders\""), schema.params());
   }

   @Test
   void describeDataset_reservedWordSetName_unconditionalQuoteWrap() throws Exception {
      // "order" parses as a reserved keyword under the Calcite SQL grammar AerospikeQuery.parse
      // actually uses -- an unquoted "SELECT * FROM order" fails to parse. This is the
      // discriminating case that tells an unconditional wrap apart from a "quote only when
      // necessary" design, mirroring HiveCatalogTest's simple-lowercase-name case.
      Connection conn = connectionWithColumns("test", "order",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "order");

      assertEquals("SELECT * FROM \"order\"", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_spaceInSetName_wrappedWithoutEscaping() throws Exception {
      String setName = "my set";
      Connection conn = connectionWithColumns("test", setName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", setName);

      assertEquals("SELECT * FROM \"my set\"", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_doubleQuoteInSetName_escapesByDoubling() throws Exception {
      // AerospikeDatabaseMetadata.getIdentifierQuoteString() returns "\"" -- an unescaped wrap
      // would break out of the identifier quoting after the embedded quote and produce malformed
      // SQL, the same hazard HiveCatalogTest's embedded-backtick case guards against.
      String setName = "weird\"set";
      Connection conn = connectionWithColumns("test", setName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", setName);

      assertEquals("SELECT * FROM \"weird\"\"set\"", schema.params().get("queryString"));
   }

   // ---- describeDataset: getColumns tableNamePattern is a driver regex, not a literal -----------

   @Test
   void describeColumns_regexMetacharacterSetName_passesLiteralPercentNotDatasetId()
      throws Exception
   {
      // The real driver (AerospikeDatabaseMetadata.getColumns) compiles tableNamePattern as a Java
      // regex and matches it with Matcher.matches() -- a mock that only stubs the return value
      // would happily return rows regardless of what pattern it was handed and could not catch a
      // regression here, so this test asserts on the argument actually passed instead.
      String setName = "orders(2024)";
      Connection conn = connectionWithColumns("test", setName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      AerospikeCatalog.describeDataset(conn, "test", setName);

      DatabaseMetaData meta = conn.getMetaData();
      ArgumentCaptor<String> tableNamePattern = ArgumentCaptor.forClass(String.class);
      verify(meta).getColumns(any(), any(), tableNamePattern.capture(), any());
      assertEquals("%", tableNamePattern.getValue());
   }

   // ---- describeDataset: column ordering ---------------------------------------------------------

   @Test
   void describeDataset_columnsOrderedByOrdinalPosition_evenIfResultSetRowsOutOfOrder()
      throws Exception
   {
      Connection conn = connectionWithColumns("test", "orders", List.of(
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 3),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "customer_id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      assertEquals(List.of("id", "customer_id", "total"),
         schema.columns().stream().map(TabularColumn::name).toList());
   }

   // ---- describeDataset: A2 (type mapping via SQLTypes.convertToXType) --------------------------

   @Test
   void convertToXType_booleanSqlType_mapsToXSchemaBoolean() {
      // The Hive-round regression, repeated here as its own regression: the two-hop
      // convertToJava -> CoreTool.getDataType chain has no Types.BOOLEAN branch and would land on
      // XSchema.STRING. convertToXType merges BIT/BOOLEAN and returns XSchema.BOOLEAN directly.
      assertEquals(XSchema.BOOLEAN, new AerospikeSQLTypes().convertToXType(Types.BOOLEAN));
   }

   @Test
   void describeDataset_columnTypesComeFromConvertToXType_notANewSwitch() throws Exception {
      Connection conn = connectionWithColumns("test", "orders", List.of(
         row("COLUMN_NAME", "is_active", "DATA_TYPE", Types.BOOLEAN, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "is_flagged", "DATA_TYPE", Types.BIT, "ORDINAL_POSITION", 2),
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 3),
         row("COLUMN_NAME", "count", "DATA_TYPE", Types.NUMERIC, "ORDINAL_POSITION", 4),
         row("COLUMN_NAME", "big_id", "DATA_TYPE", Types.BIGINT, "ORDINAL_POSITION", 5),
         row("COLUMN_NAME", "created_at", "DATA_TYPE", Types.TIMESTAMP, "ORDINAL_POSITION", 6),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 7)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      Map<String, String> typesByName = schema.columns().stream()
         .collect(Collectors.toMap(TabularColumn::name, TabularColumn::type));
      assertEquals(XSchema.BOOLEAN, typesByName.get("is_active"));
      assertEquals(XSchema.BOOLEAN, typesByName.get("is_flagged"));
      assertEquals(XSchema.DOUBLE, typesByName.get("total"));
      assertEquals(XSchema.DOUBLE, typesByName.get("count"));
      assertEquals(XSchema.LONG, typesByName.get("big_id"));
      assertEquals(XSchema.TIME_INSTANT, typesByName.get("created_at"));
      assertEquals(XSchema.INTEGER, typesByName.get("id"));
   }

   @Test
   void describeDataset_unmatchedColumnType_fallsBackToXSchemaString() throws Exception {
      // Types.ARRAY is one of convertToXType's unmatched codes (falls to its default, returning
      // null) -- AerospikeCatalog's own null -> XSchema.STRING bridge is what should produce this
      // result, not a second, competing mapping.
      Connection conn = connectionWithColumns("test", "orders",
         List.of(row("COLUMN_NAME", "tags", "DATA_TYPE", Types.ARRAY, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      assertEquals(XSchema.STRING, schema.columns().get(0).type());
   }

   // ---- describeDataset: dataset-level incompleteness signal (A3, L1) ---------------------------

   @Test
   void describeDataset_columnsMayBeIncomplete_alwaysTrue() throws Exception {
      // Every column here came from the same bounded scan (AerospikeSchemaBuilder unions bin
      // names across at most 1000 records), never from declared, source-published metadata -- this
      // connector has no path that would make it false.
      Connection conn = connectionWithColumns("test", "orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      assertTrue(schema.columnsMayBeIncomplete());
   }

   // ---- describeDataset: getPrimaryKeys isolation -------------------------------------------------

   @Test
   void describeDataset_getPrimaryKeysThrows_keyColumnsEmpty_restOfSchemaStillReturned()
      throws Exception
   {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      // Not narrowed to SQLFeatureNotSupportedException: an unsupported-method response is not
      // guaranteed to surface as that specific JDK subtype.
      when(meta.getPrimaryKeys(any(), any(), any())).thenThrow(new SQLException("not supported"));

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      assertEquals(List.of(), schema.keyColumns());
      assertEquals(1, schema.columns().size());
   }

   @Test
   void describeDataset_getColumnsThrows_propagates() throws Exception {
      // Isolation must be two independent test cases, not one shared try/catch: a getColumns
      // failure is almost always a real connection/permission problem and must not be swallowed
      // the way a getPrimaryKeys failure is.
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any()))
         .thenThrow(new SQLException("permission denied"));

      Exception ex = assertThrows(Exception.class,
         () -> AerospikeCatalog.describeDataset(conn, "test", "orders"));
      assertTrue(ex.getMessage().contains("permission denied") ||
         ex instanceof SQLException);
   }

   @Test
   void describeDataset_keyColumnsOrderedByKeySeq() throws Exception {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "tenant_id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));
      ResultSet pkRs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "KEY_SEQ", (short) 2),
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "tenant_id",
            "KEY_SEQ", (short) 1)));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = AerospikeCatalog.describeDataset(conn, "test", "orders");

      assertEquals(List.of("tenant_id", "id"), schema.keyColumns());
   }

   @Test
   void describeDataset_setNotFound_throws() throws Exception {
      Connection conn = connectionWithColumns("test", "no_such_set", List.of());

      Exception ex = assertThrows(Exception.class,
         () -> AerospikeCatalog.describeDataset(conn, "test", "no_such_set"));
      assertTrue(ex.getMessage().contains("no_such_set"));
   }

   // ---- catalog/schema argument semantics (stated assumption, see class javadoc) -----------------

   @Test
   void describeDataset_catalogAndSchemaArgs_namespaceAsCatalogSchemaNull() throws Exception {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_CAT", "test", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      ResultSet pkRs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      AerospikeCatalog.describeDataset(conn, "test", "orders");

      ArgumentCaptor<String> columnsCatalog = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> columnsSchema = ArgumentCaptor.forClass(String.class);
      // tableNamePattern is "%", not "orders" -- see describeColumns_regexMetacharacterSetName_
      // passesLiteralPercentNotDatasetId above for why. This test only cares about catalog/schema.
      verify(meta).getColumns(columnsCatalog.capture(), columnsSchema.capture(), eq("%"),
         any());
      assertEquals("test", columnsCatalog.getValue());
      assertNull(columnsSchema.getValue());

      ArgumentCaptor<String> pkCatalog = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> pkSchema = ArgumentCaptor.forClass(String.class);
      verify(meta).getPrimaryKeys(pkCatalog.capture(), pkSchema.capture(), eq("orders"));
      assertEquals("test", pkCatalog.getValue());
      assertNull(pkSchema.getValue());
   }

   // ---- fixtures --------------------------------------------------------------------------------

   private static Connection connectionWithColumns(String namespace, String setName,
                                                    List<Map<String, Object>> columnRows)
      throws Exception
   {
      List<Map<String, Object>> withCatalogAndTable = columnRows.stream()
         .map(r -> {
            Map<String, Object> withKeys = new LinkedHashMap<>(r);
            withKeys.putIfAbsent("TABLE_CAT", namespace);
            withKeys.putIfAbsent("TABLE_NAME", setName);
            return withKeys;
         })
         .collect(Collectors.toList());
      ResultSet columnsRs = mockResultSet(withCatalogAndTable);
      ResultSet pkRs = mockResultSet(List.of());

      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      return conn;
   }

   private static ResultSet mockResultSet(List<Map<String, Object>> rows) throws SQLException {
      ResultSet rs = mock(ResultSet.class);
      int[] index = {-1};
      when(rs.next()).thenAnswer(inv -> ++index[0] < rows.size());
      when(rs.getString(anyString())).thenAnswer(inv ->
         rows.get(index[0]).get((String) inv.getArgument(0)));
      when(rs.getInt(anyString())).thenAnswer(inv -> {
         Object v = rows.get(index[0]).get((String) inv.getArgument(0));
         return v == null ? 0 : ((Number) v).intValue();
      });
      when(rs.getShort(anyString())).thenAnswer(inv -> {
         Object v = rows.get(index[0]).get((String) inv.getArgument(0));
         return v == null ? (short) 0 : ((Number) v).shortValue();
      });
      return rs;
   }

   private static Map<String, Object> row(Object... kv) {
      Map<String, Object> m = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         m.put((String) kv[i], kv[i + 1]);
      }

      return m;
   }
}
