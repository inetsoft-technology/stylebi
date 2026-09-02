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

import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.jdbc.util.HiveSQLTypes;
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
 * Covers charter assertions A1-A5 against {@link HiveCatalog}, driven entirely off mocked
 * {@code Connection}/{@code DatabaseMetaData}/{@code ResultSet} — all three are {@code java.sql}
 * JDK interfaces, so no hive-jdbc driver class is on the compile/test classpath for this file.
 *
 * <p>The {@code catalog}/{@code schema} arguments {@link HiveCatalog} passes to
 * {@code getTables}/{@code getColumns}/{@code getPrimaryKeys} are asserted here as
 * {@code catalog=null, schema=dbName} — not merely "internally consistent" — because decompiling
 * the actual {@code hive-jdbc-4.0.1-standalone.jar} bytecode shows
 * {@code HiveDatabaseMetaData.getColumns}/{@code getPrimaryKeys} both forward
 * their second argument to {@code TGetColumnsReq}/{@code TGetPrimaryKeysReq}'s
 * {@code setSchemaName}, and {@code getTables} forwards its second argument to
 * {@code TGetTablesReq.setSchemaName} while never reading its first (catalog) argument at all.
 *
 * <p>{@code SQLTypes} (the superclass {@link HiveSQLTypes} inherits {@code convertToXType} from)
 * has a one-time static field initializer that reads {@code SreeEnv.getProperty} ->
 * {@code PropertiesEngine.getInstance()}, so the first reference to either class anywhere in this
 * JVM needs a Spring-free stand-in for that singleton (mirrors {@code SreeEnvTest}'s own
 * {@code mockStatic(PropertiesEngine.class)} pattern in core, not a Hive-specific requirement).
 */
class HiveCatalogTest {
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
   void listDatasets_returnsTablesAndViews_scopedToConfiguredDatabase() throws Exception {
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders"),
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "customers")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(isNull(), eq("sales"), eq("%"), any())).thenReturn(rs);

      TabularCatalog catalog = HiveCatalog.listDatasets(conn, "sales");

      assertEquals(List.of("orders", "customers"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasets_passesTableViewAndMaterializedViewTypesToGetTables() throws Exception {
      // "MATERIALIZED_VIEW" is the underscore form that ClassicTableTypeMapping$ClassicTableTypes.
      // MATERIALIZED_VIEW.toString() actually produces (that enum has no toString() override, so
      // it equals name()) -- not the space form "MATERIALIZED VIEW" that HiveDatabaseMetaData.
      // toJdbcTableType uses elsewhere for an unrelated client-side purpose. ClassicTableTypeMapping
      // is what HiveServer2 uses under the default hive.server2.table.type.mapping=CLASSIC, and it
      // keeps MATERIALIZED_VIEW as its own client-visible type rather than folding it into VIEW
      // like it does for external tables and TABLE.
      ResultSet rs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      HiveCatalog.listDatasets(conn, "sales");

      ArgumentCaptor<String[]> typesCaptor = ArgumentCaptor.forClass(String[].class);
      verify(meta).getTables(any(), any(), any(), typesCaptor.capture());
      assertArrayEquals(new String[]{"TABLE", "VIEW", "MATERIALIZED_VIEW"},
         typesCaptor.getValue());
   }

   @Test
   void listDatasets_includesMaterializedViewTypedRow() throws Exception {
      // Regression sentinel, not a fail-without-fix case: listDatasets never filtered on
      // TABLE_TYPE itself (see the passing test above for what actually gates a materialized
      // view's presence), so a MATERIALIZED_VIEW-typed row already passed through unchanged
      // before this round's fix. This locks that in explicitly per the review's request.
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "TABLE_TYPE", "TABLE"),
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "mv_totals", "TABLE_TYPE",
            "MATERIALIZED_VIEW")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      TabularCatalog catalog = HiveCatalog.listDatasets(conn, "sales");

      assertEquals(List.of("orders", "mv_totals"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_filtersOutRowsFromDifferentSchema_evenIfDriverReturnsThem() throws Exception {
      // A defensive filter: JDBC allows schemaPattern to be treated as a LIKE pattern, so a driver
      // that over-matches must not be trusted to have already scoped its rows to "sales".
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders"),
         row("TABLE_SCHEM", "sales_archive", "TABLE_NAME", "old_orders")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      TabularCatalog catalog = HiveCatalog.listDatasets(conn, "sales");

      assertEquals(List.of("orders"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_emptyDatabase_returnsEmptyCatalog() throws Exception {
      ResultSet rs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      TabularCatalog catalog = HiveCatalog.listDatasets(conn, "sales");

      assertEquals(List.of(), catalog.datasets());
   }

   // ---- describeDataset: A1 / D4 (queryString param) -------------------------------------------

   @Test
   void describeDataset_paramsHasLowercaseQueryStringKey_backtickWrappedSelect() throws Exception {
      Connection conn = connectionWithColumns("sales", "orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      // Reverse: not "QueryString", not "sql", not the @Property(label=) text "Enter SQL" — must
      // be exactly the HiveQuery bean property name TabularUtil.getPropertyMap derives.
      assertEquals(Map.of("queryString", "SELECT * FROM `orders`"), schema.params());
   }

   @Test
   void describeDataset_mixedCaseTableName_unconditionalBacktickWrap() throws Exception {
      Connection conn = connectionWithColumns("sales", "UserEvents",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "UserEvents");

      assertEquals("SELECT * FROM `UserEvents`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_simpleLowercaseTableName_stillBacktickWrapped() throws Exception {
      // The discriminating case: a plain lowercase identifier needs no escaping under a
      // "quote only when necessary" design, so only this input tells D4's unconditional wrap
      // apart from that alternative design.
      Connection conn = connectionWithColumns("sales", "orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      assertEquals("SELECT * FROM `orders`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_underscoreTableName_unconditionalBacktickWrap() throws Exception {
      Connection conn = connectionWithColumns("sales", "user_events",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "user_events");

      assertEquals("SELECT * FROM `user_events`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_backtickInTableName_escapesByDoubling() throws Exception {
      // hive.support.special.characters.tablename defaults to true (MetastoreConf.ConfVars.
      // SUPPORT_SPECICAL_CHARACTERS_IN_TABLE_NAMES), and MetaStoreUtils.
      // SPECIAL_CHARACTERS_IN_TABLE_NAMES' 32nd entry is a literal backtick -- so a table named
      // with an embedded backtick is legal under stock configuration, not just some unusual
      // opt-in. An unescaped wrap would break out of the identifier quoting after the embedded
      // backtick and produce malformed SQL.
      String tableName = "weird`table";
      Connection conn = connectionWithColumns("sales", tableName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", tableName);

      assertEquals("SELECT * FROM `weird``table`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_spaceInTableName_wrappedWithoutEscaping() throws Exception {
      // Space is also in SPECIAL_CHARACTERS_IN_TABLE_NAMES and legal by default, but unlike a
      // backtick it needs no escaping inside a backtick-quoted identifier -- this is a cheap
      // assertion that the doubling fix does not disturb the plain wrap for characters that
      // don't need it.
      String tableName = "weird table";
      Connection conn = connectionWithColumns("sales", tableName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", tableName);

      assertEquals("SELECT * FROM `weird table`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_semicolonInTableName_wrappedWithoutEscaping() throws Exception {
      // Same rationale as the space case above, for the other SPECIAL_CHARACTERS_IN_TABLE_NAMES
      // entry the review named.
      String tableName = "weird;table";
      Connection conn = connectionWithColumns("sales", tableName,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", tableName);

      assertEquals("SELECT * FROM `weird;table`", schema.params().get("queryString"));
   }

   // ---- describeDataset: column ordering (open point 4) ----------------------------------------

   @Test
   void describeDataset_columnsOrderedByOrdinalPosition_evenIfResultSetRowsOutOfOrder()
      throws Exception
   {
      Connection conn = connectionWithColumns("sales", "orders", List.of(
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 3),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "customer_id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      assertEquals(List.of("id", "customer_id", "total"),
         schema.columns().stream().map(TabularColumn::name).toList());
   }

   // ---- describeDataset: A3 (type mapping via HiveSQLTypes.convertToXType) --------------------

   @Test
   void convertToXType_booleanSqlType_mapsToXSchemaBoolean() {
      // The reconcile finding this round exists to fix: the two-hop convertToJava ->
      // CoreTool.getDataType chain has no Types.BOOLEAN branch and would land on
      // XSchema.STRING. convertToXType merges BIT/BOOLEAN and returns XSchema.BOOLEAN directly.
      assertEquals(XSchema.BOOLEAN, new HiveSQLTypes().convertToXType(Types.BOOLEAN));
   }

   @Test
   void describeDataset_columnTypesComeFromConvertToXType_notANewSwitch() throws Exception {
      Connection conn = connectionWithColumns("sales", "orders", List.of(
         row("COLUMN_NAME", "is_active", "DATA_TYPE", Types.BOOLEAN, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "is_flagged", "DATA_TYPE", Types.BIT, "ORDINAL_POSITION", 2),
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 3),
         row("COLUMN_NAME", "count", "DATA_TYPE", Types.NUMERIC, "ORDINAL_POSITION", 4),
         row("COLUMN_NAME", "big_id", "DATA_TYPE", Types.BIGINT, "ORDINAL_POSITION", 5),
         row("COLUMN_NAME", "created_at", "DATA_TYPE", Types.TIMESTAMP, "ORDINAL_POSITION", 6),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 7)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

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
      // null) -- HiveCatalog's own null -> XSchema.STRING bridge is what should produce this
      // result, not a second, competing mapping.
      Connection conn = connectionWithColumns("sales", "orders",
         List.of(row("COLUMN_NAME", "tags", "DATA_TYPE", Types.ARRAY, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      assertEquals(XSchema.STRING, schema.columns().get(0).type());
   }

   // ---- describeDataset: D5 (getPrimaryKeys isolation) ------------------------------------------

   @Test
   void describeDataset_getPrimaryKeysThrows_keyColumnsEmpty_restOfSchemaStillReturned()
      throws Exception
   {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      // Not narrowed to SQLFeatureNotSupportedException: HiveServer2's "method not implemented"
      // response is not guaranteed to surface as that specific JDK subtype (D5, reconcile section
      // 3.2).
      when(meta.getPrimaryKeys(any(), any(), any())).thenThrow(new SQLException("not supported"));

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      assertEquals(List.of(), schema.keyColumns());
      assertEquals(1, schema.columns().size());
   }

   @Test
   void describeDataset_getColumnsThrows_propagates() throws Exception {
      // D5's isolation must be two independent test cases, not one shared try/catch: a getColumns
      // failure is almost always a real connection/permission problem and must not be swallowed
      // the way a getPrimaryKeys failure is.
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any()))
         .thenThrow(new SQLException("permission denied"));

      Exception ex = assertThrows(Exception.class,
         () -> HiveCatalog.describeDataset(conn, "sales", "orders"));
      assertTrue(ex.getMessage().contains("permission denied") ||
         ex instanceof SQLException);
   }

   @Test
   void describeDataset_keyColumnsOrderedByKeySeq() throws Exception {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "tenant_id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));
      ResultSet pkRs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "KEY_SEQ", (short) 2),
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "tenant_id",
            "KEY_SEQ", (short) 1)));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = HiveCatalog.describeDataset(conn, "sales", "orders");

      assertEquals(List.of("tenant_id", "id"), schema.keyColumns());
   }

   @Test
   void describeDataset_tableNotFound_throws() throws Exception {
      Connection conn = connectionWithColumns("sales", "no_such_table", List.of());

      Exception ex = assertThrows(Exception.class,
         () -> HiveCatalog.describeDataset(conn, "sales", "no_such_table"));
      assertTrue(ex.getMessage().contains("no_such_table"));
   }

   // ---- open point 2: catalog/schema argument semantics -----------------------------------------

   @Test
   void describeDataset_catalogAndSchemaArgs_matchDecompiledDriverSemantics() throws Exception {
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_SCHEM", "sales", "TABLE_NAME", "orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      ResultSet pkRs = mockResultSet(List.of());
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      HiveCatalog.describeDataset(conn, "sales", "orders");

      ArgumentCaptor<String> columnsCatalog = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> columnsSchema = ArgumentCaptor.forClass(String.class);
      verify(meta).getColumns(columnsCatalog.capture(), columnsSchema.capture(), eq("orders"),
         any());
      assertNull(columnsCatalog.getValue());
      assertEquals("sales", columnsSchema.getValue());

      ArgumentCaptor<String> pkCatalog = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> pkSchema = ArgumentCaptor.forClass(String.class);
      verify(meta).getPrimaryKeys(pkCatalog.capture(), pkSchema.capture(), eq("orders"));
      assertNull(pkCatalog.getValue());
      assertEquals("sales", pkSchema.getValue());
   }

   // ---- fixtures --------------------------------------------------------------------------------

   private static Connection connectionWithColumns(String dbName, String tableName,
                                                    List<Map<String, Object>> columnRows)
      throws Exception
   {
      List<Map<String, Object>> withSchemaAndTable = columnRows.stream()
         .map(r -> {
            Map<String, Object> withKeys = new LinkedHashMap<>(r);
            withKeys.putIfAbsent("TABLE_SCHEM", dbName);
            withKeys.putIfAbsent("TABLE_NAME", tableName);
            return withKeys;
         })
         .collect(Collectors.toList());
      ResultSet columnsRs = mockResultSet(withSchemaAndTable);
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
