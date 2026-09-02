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
package inetsoft.uql.orientdb;

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
 * Covers charter assertions A1, A2, A3, A5, A8 against {@link OrientDBCatalog}, driven entirely off
 * mocked {@code Connection}/{@code DatabaseMetaData}/{@code ResultSet} -- all three are {@code
 * java.sql} JDK interfaces, so no orientdb-jdbc driver class is on the compile/test classpath for
 * this file (D2: mock JDK interfaces, no test-scope driver dependency).
 *
 * <p>The {@code catalog}/{@code schema} arguments {@link OrientDBCatalog} passes to {@code
 * getTables}/{@code getColumns}/{@code getPrimaryKeys} are {@code catalog=database, schema=null}
 * (see {@code OrientDBCatalog}'s class javadoc) -- confirmed from the driver that neither method
 * actually reads either argument, so those two are stubbed with {@code any()} throughout rather
 * than asserted; the arguments this driver's behavior actually depends on ({@code types} and
 * {@code tableNamePattern}) are asserted with {@link ArgumentCaptor} instead of inferred from
 * return values, per this round's design note that a stub returns whatever it was told to
 * regardless of what argument it was called with.
 *
 * <p>{@code SQLTypes} (the superclass {@link OrientDBSQLTypes} inherits {@code convertToXType}
 * from) has a one-time static field initializer that reads {@code SreeEnv.getProperty} ->
 * {@code PropertiesEngine.getInstance()}, so the first reference to either class anywhere in this
 * JVM needs a Spring-free stand-in for that singleton (mirrors {@code AerospikeCatalogTest}'s own
 * {@code mockStatic(PropertiesEngine.class)} pattern).
 */
class OrientDBCatalogTest {
   private static final String DATABASE = "test";

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
   void listDatasets_returnsClasses_inDriverOrder() throws Exception {
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Orders"),
         row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Customers")));
      Connection conn = connectionReturningTables(rs);

      TabularCatalog catalog = OrientDBCatalog.listDatasets(conn);

      assertEquals(List.of("Orders", "Customers"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
      assertEquals(List.of(), catalog.relationships());
   }

   @Test
   void listDatasets_passesExplicitTableTypesArray_notNull() throws Exception {
      // A8: OrientJdbcDatabaseMetaData's own TABLE_TYPES default (used when types == null) is
      // {"TABLE", "SYSTEM TABLE"} -- passing null hands OUser/ORole/OFunction and friends to the
      // annotator as business tables. Asserted on the argument actually passed: a mock returns its
      // stubbed rows regardless of what it was called with, so a return-value assertion could not
      // falsify this.
      Connection conn = connectionReturningTables(mockResultSet(List.of()));

      OrientDBCatalog.listDatasets(conn);

      DatabaseMetaData meta = conn.getMetaData();
      ArgumentCaptor<String[]> typesCaptor = ArgumentCaptor.forClass(String[].class);
      verify(meta).getTables(any(), any(), any(), typesCaptor.capture());
      assertArrayEquals(new String[]{"TABLE"}, typesCaptor.getValue());
   }

   @Test
   void listDatasets_excludesSystemTableRows_evenIfDriverReturnsThem() throws Exception {
      // Defensive re-check on TABLE_TYPE, independent of the types argument above: if a row this
      // driver itself classifies as "SYSTEM TABLE" is ever returned anyway, this connector's own
      // loop must not fold it into the catalog.
      ResultSet rs = mockResultSet(List.of(
         row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Orders"),
         row("TABLE_TYPE", "SYSTEM TABLE", "TABLE_NAME", "OUser")));
      Connection conn = connectionReturningTables(rs);

      TabularCatalog catalog = OrientDBCatalog.listDatasets(conn);

      assertEquals(List.of("Orders"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());
   }

   @Test
   void listDatasets_emptyDatabase_returnsEmptyCatalog() throws Exception {
      Connection conn = connectionReturningTables(mockResultSet(List.of()));

      TabularCatalog catalog = OrientDBCatalog.listDatasets(conn);

      assertEquals(List.of(), catalog.datasets());
   }

   // ---- describeDataset: existence -----------------------------------------------------------

   @Test
   void describeDataset_classNotFound_throwsWithDatasetId() throws Exception {
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      ResultSet tablesRs = mockResultSet(List.of());
      when(conn.getMetaData()).thenReturn(meta);
      when(conn.getCatalog()).thenReturn(DATABASE);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(tablesRs);

      Exception ex = assertThrows(Exception.class,
         () -> OrientDBCatalog.describeDataset(conn, "NoSuchClass"));
      assertTrue(ex.getMessage().contains("NoSuchClass"));
   }

   @Test
   void describeDataset_caseMismatch_treatedAsNotFound() throws Exception {
      // getTables itself matches case-insensitively (equalsIgnoreCase), but datasetId must be
      // exactly what listDatasets returned -- a caller passing the wrong case gets "not found",
      // not a silent match.
      ResultSet rs = mockResultSet(List.of(row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Orders")));
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(conn.getCatalog()).thenReturn(DATABASE);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);

      Exception ex = assertThrows(Exception.class,
         () -> OrientDBCatalog.describeDataset(conn, "orders"));
      assertTrue(ex.getMessage().contains("orders"));
   }

   @Test
   void describeDataset_getTables_passesLiteralDatasetIdAndTableTypesArray() throws Exception {
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      OrientDBCatalog.describeDataset(conn, "Orders");

      DatabaseMetaData meta = conn.getMetaData();
      ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String[]> types = ArgumentCaptor.forClass(String[].class);
      verify(meta).getTables(any(), any(), pattern.capture(), types.capture());
      assertEquals("Orders", pattern.getValue());
      assertArrayEquals(new String[]{"TABLE"}, types.getValue());
   }

   // ---- describeDataset: schema-full branch --------------------------------------------------

   @Test
   void describeDataset_schemaFull_columnsNotIncomplete_selectStarQuery() throws Exception {
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertFalse(schema.columnsMayBeIncomplete());
      assertEquals(Map.of("queryString", "SELECT * FROM `Orders`"), schema.params());
   }

   @Test
   void describeDataset_backtickInClassName_escapedWithBackslash() throws Exception {
      // A3: OrientDB's escape for an embedded backtick is a single backslash (OIdentifier's own
      // value round-trip), not doubling -- unlike Hive's doubled backticks or Aerospike's doubled
      // double quotes.
      String className = "Weird`Class";
      Connection conn = connectionWithClass(className,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, className);

      assertEquals("SELECT * FROM `Weird\\`Class`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_spaceInClassName_wrappedWithoutEscaping() throws Exception {
      String className = "My Class";
      Connection conn = connectionWithClass(className,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, className);

      assertEquals("SELECT * FROM `My Class`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_reservedWordClassName_unconditionalBacktickWrap() throws Exception {
      String className = "Order";
      Connection conn = connectionWithClass(className,
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, className);

      assertEquals("SELECT * FROM `Order`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_columnsOrderedByOrdinalPosition_evenIfResultSetRowsOutOfOrder()
      throws Exception
   {
      Connection conn = connectionWithClass("Orders", List.of(
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 3),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "customer_id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of("id", "customer_id", "total"),
         schema.columns().stream().map(TabularColumn::name).toList());
   }

   @Test
   void describeDataset_getColumns_passesLiteralDatasetIdAndNullColumnNamePattern()
      throws Exception
   {
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));

      OrientDBCatalog.describeDataset(conn, "Orders");

      DatabaseMetaData meta = conn.getMetaData();
      ArgumentCaptor<String> tableNamePattern = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> columnNamePattern = ArgumentCaptor.forClass(String.class);
      verify(meta).getColumns(any(), any(), tableNamePattern.capture(), columnNamePattern.capture());
      assertEquals("Orders", tableNamePattern.getValue());
      assertNull(columnNamePattern.getValue());
   }

   @Test
   void describeDataset_getColumnsOverMatchedRow_discardedByExactMatchFilter() throws Exception {
      // OrientJdbcUtils.like rewrites "_" to "." and matches case-insensitively, so a
      // tableNamePattern of "my_class" over-matches "myXclass", and "Orders" over-matches
      // "orders". A mock stub returns whatever it is told to regardless of the pattern actually
      // passed, so this must be exercised by feeding an over-matched row through the mock and
      // asserting the exact-match post-filter discards it -- nothing else in this file would catch
      // a regression that dropped that filter.
      ResultSet columnsRs = mockResultSet(List.of(
         row("TABLE_NAME", "Orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("TABLE_NAME", "orders", "COLUMN_NAME", "bogus",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));
      Connection conn = connectionWithClassExists("Orders");
      DatabaseMetaData meta = conn.getMetaData();
      ResultSet pkRs = mockResultSet(List.of());
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of("id"), schema.columns().stream().map(TabularColumn::name).toList());
   }

   // ---- describeDataset: A2 (type mapping via SQLTypes.convertToXType) ------------------------

   @Test
   void convertToXType_booleanSqlType_mapsToXSchemaBoolean() {
      // The Hive-round regression, repeated here as its own regression: the two-hop
      // convertToJava -> CoreTool.getDataType chain has no Types.BOOLEAN branch and would land on
      // XSchema.STRING. convertToXType merges BIT/BOOLEAN and returns XSchema.BOOLEAN directly.
      assertEquals(XSchema.BOOLEAN, new OrientDBSQLTypes().convertToXType(Types.BOOLEAN));
   }

   @Test
   void describeDataset_columnTypesComeFromConvertToXType_notANewSwitch() throws Exception {
      Connection conn = connectionWithClass("Orders", List.of(
         row("COLUMN_NAME", "is_active", "DATA_TYPE", Types.BOOLEAN, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "total", "DATA_TYPE", Types.DECIMAL, "ORDINAL_POSITION", 2),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 3)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      Map<String, String> typesByName = schema.columns().stream()
         .collect(Collectors.toMap(TabularColumn::name, TabularColumn::type));
      assertEquals(XSchema.BOOLEAN, typesByName.get("is_active"));
      assertEquals(XSchema.DOUBLE, typesByName.get("total"));
      assertEquals(XSchema.INTEGER, typesByName.get("id"));
   }

   @Test
   void describeDataset_unmatchedColumnType_fallsBackToXSchemaString() throws Exception {
      // Types.ARRAY is one of convertToXType's unmatched codes (falls to its default, returning
      // null) -- OrientDBCatalog's own null -> XSchema.STRING bridge is what should produce this
      // result, not a second, competing mapping. Also stands in for OrientDB's own OType.LINKBAG,
      // which OrientJdbcResultSetMetaData's DATA_TYPE map does not cover either (see design §8).
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "tags", "DATA_TYPE", Types.ARRAY, "ORDINAL_POSITION", 1)));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(XSchema.STRING, schema.columns().get(0).type());
   }

   // ---- describeDataset: getPrimaryKeys ------------------------------------------------------

   @Test
   void describeDataset_zeroUniqueIndexes_keyColumnsEmpty() throws Exception {
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      ResultSet pkRs = mockResultSet(List.of());
      when(conn.getMetaData().getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of(), schema.keyColumns());
   }

   @Test
   void describeDataset_oneUniqueIndex_keyColumnsOrderedByKeySeq() throws Exception {
      Connection conn = connectionWithClass("Orders", List.of(
         row("COLUMN_NAME", "tenant_id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 2)));
      ResultSet pkRs = mockResultSet(List.of(
         row("PK_NAME", "Orders_unique", "COLUMN_NAME", "id", "KEY_SEQ", (short) 2),
         row("PK_NAME", "Orders_unique", "COLUMN_NAME", "tenant_id", "KEY_SEQ", (short) 1)));
      when(conn.getMetaData().getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of("tenant_id", "id"), schema.keyColumns());
   }

   @Test
   void describeDataset_twoUniqueIndexes_keyColumnsEmpty_doesNotMergeKeySeqAcrossIndexes()
      throws Exception
   {
      // Both indexes restart KEY_SEQ at 1 -- a TreeMap<KEY_SEQ, name> collection (the Aerospike
      // shape) would let the second index's KEY_SEQ=1 row silently overwrite the first's, and the
      // one-and-only pk field would look mangled instead of the whole call recognizing there is no
      // single declared key. Not present in AerospikeCatalogTest -- unique to this driver's
      // multi-unique-index-per-class behavior.
      Connection conn = connectionWithClass("Orders", List.of(
         row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1),
         row("COLUMN_NAME", "email", "DATA_TYPE", Types.VARCHAR, "ORDINAL_POSITION", 2)));
      ResultSet pkRs = mockResultSet(List.of(
         row("PK_NAME", "Orders_id_unique", "COLUMN_NAME", "id", "KEY_SEQ", (short) 1),
         row("PK_NAME", "Orders_email_unique", "COLUMN_NAME", "email", "KEY_SEQ", (short) 1)));
      when(conn.getMetaData().getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of(), schema.keyColumns());
   }

   @Test
   void describeDataset_getPrimaryKeysThrows_keyColumnsEmpty_restOfSchemaStillReturned()
      throws Exception
   {
      Connection conn = connectionWithClass("Orders",
         List.of(row("COLUMN_NAME", "id", "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      // Not narrowed to SQLFeatureNotSupportedException: an unsupported-method response is not
      // guaranteed to surface as that specific JDK subtype.
      when(conn.getMetaData().getPrimaryKeys(any(), any(), any()))
         .thenThrow(new SQLException("not supported"));

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Orders");

      assertEquals(List.of(), schema.keyColumns());
      assertEquals(1, schema.columns().size());
   }

   @Test
   void describeDataset_getColumnsThrows_propagates() throws Exception {
      // Isolation must be two independent test cases, not one shared try/catch: a getColumns
      // failure is almost always a real connection/permission problem and must not be swallowed
      // the way a getPrimaryKeys failure is.
      Connection conn = connectionWithClassExists("Orders");
      when(conn.getMetaData().getColumns(any(), any(), any(), any()))
         .thenThrow(new SQLException("permission denied"));

      Exception ex = assertThrows(Exception.class,
         () -> OrientDBCatalog.describeDataset(conn, "Orders"));
      assertTrue(ex.getMessage().contains("permission denied") || ex instanceof SQLException);
   }

   // ---- describeDataset: schema-less branch --------------------------------------------------

   @Test
   void describeDataset_schemaLess_ridColumn_columnsMayBeIncomplete() throws Exception {
      Connection conn = connectionWithClass("Event", List.of());

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Event");

      assertEquals(List.of("@rid"), schema.columns().stream().map(TabularColumn::name).toList());
      assertEquals(XSchema.STRING, schema.columns().get(0).type());
      assertTrue(schema.columnsMayBeIncomplete());
      assertEquals(List.of(), schema.keyColumns());
   }

   @Test
   void describeDataset_schemaLess_selectsRidNotStar() throws Exception {
      Connection conn = connectionWithClass("Event", List.of());

      TabularDatasetSchema schema = OrientDBCatalog.describeDataset(conn, "Event");

      assertEquals("SELECT @rid FROM `Event`", schema.params().get("queryString"));
   }

   @Test
   void describeDataset_schemaLess_neverCallsGetPrimaryKeys() throws Exception {
      Connection conn = connectionWithClass("Event", List.of());

      OrientDBCatalog.describeDataset(conn, "Event");

      verify(conn.getMetaData(), never()).getPrimaryKeys(any(), any(), any());
   }

   // ---- describeDataset: mixed schema-full / schema-less database (A5) ------------------------

   @Test
   void listDatasets_thenDescribeDataset_mixedSchemaFullAndSchemaLessClasses_bothCorrect()
      throws Exception
   {
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(conn.getCatalog()).thenReturn(DATABASE);

      ResultSet allTablesRs = mockResultSet(List.of(
         row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Orders"),
         row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Event")));
      ResultSet ordersTableRs = mockResultSet(
         List.of(row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Orders")));
      ResultSet eventTableRs = mockResultSet(
         List.of(row("TABLE_TYPE", "TABLE", "TABLE_NAME", "Event")));
      ResultSet ordersColumnsRs = mockResultSet(List.of(
         row("TABLE_NAME", "Orders", "COLUMN_NAME", "id",
            "DATA_TYPE", Types.INTEGER, "ORDINAL_POSITION", 1)));
      ResultSet eventColumnsRs = mockResultSet(List.of());
      ResultSet pkRs = mockResultSet(List.of());

      when(meta.getTables(any(), any(), eq("%"), any())).thenReturn(allTablesRs);
      when(meta.getTables(any(), any(), eq("Orders"), any())).thenReturn(ordersTableRs);
      when(meta.getTables(any(), any(), eq("Event"), any())).thenReturn(eventTableRs);
      when(meta.getColumns(any(), any(), eq("Orders"), any())).thenReturn(ordersColumnsRs);
      when(meta.getColumns(any(), any(), eq("Event"), any())).thenReturn(eventColumnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      TabularCatalog catalog = OrientDBCatalog.listDatasets(conn);
      assertEquals(List.of("Orders", "Event"),
         catalog.datasets().stream().map(TabularDatasetRef::id).toList());

      TabularDatasetSchema ordersSchema = OrientDBCatalog.describeDataset(conn, "Orders");
      assertFalse(ordersSchema.columnsMayBeIncomplete());
      assertEquals(List.of("id"),
         ordersSchema.columns().stream().map(TabularColumn::name).toList());

      TabularDatasetSchema eventSchema = OrientDBCatalog.describeDataset(conn, "Event");
      assertTrue(eventSchema.columnsMayBeIncomplete());
      assertEquals(List.of("@rid"),
         eventSchema.columns().stream().map(TabularColumn::name).toList());
   }

   // ---- fixtures ------------------------------------------------------------------------------

   private static Connection connectionReturningTables(ResultSet rs) throws Exception {
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      when(conn.getMetaData()).thenReturn(meta);
      when(conn.getCatalog()).thenReturn(DATABASE);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(rs);
      return conn;
   }

   /**
    * A connection where {@code getTables} confirms {@code className} exists (for both the exact
    * pattern and any other pattern this class might be asked with) and {@code getColumns}/
    * {@code getPrimaryKeys} return the given column rows / no primary keys -- the common case for
    * a {@code describeDataset} test that only cares about the columns/keys/query string outcome.
    */
   private static Connection connectionWithClass(String className,
                                                   List<Map<String, Object>> columnRows)
      throws Exception
   {
      Connection conn = connectionWithClassExists(className);
      DatabaseMetaData meta = conn.getMetaData();

      List<Map<String, Object>> withTableName = columnRows.stream()
         .map(r -> {
            Map<String, Object> withKey = new LinkedHashMap<>(r);
            withKey.putIfAbsent("TABLE_NAME", className);
            return withKey;
         })
         .collect(Collectors.toList());
      ResultSet columnsRs = mockResultSet(withTableName);
      ResultSet pkRs = mockResultSet(List.of());
      when(meta.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
      when(meta.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);

      return conn;
   }

   private static Connection connectionWithClassExists(String className) throws Exception {
      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      Connection conn = mock(Connection.class);
      ResultSet tablesRs = mockResultSet(List.of(row("TABLE_TYPE", "TABLE", "TABLE_NAME", className)));
      when(conn.getMetaData()).thenReturn(meta);
      when(conn.getCatalog()).thenReturn(DATABASE);
      when(meta.getTables(any(), any(), any(), any())).thenReturn(tablesRs);
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
