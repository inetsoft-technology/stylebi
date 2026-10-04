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
package inetsoft.uql.jdbc;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.controller.database.*;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77617. {@link JDBCUtil#fixUniformSQLInfo} replaces a wildcard (* or t.*) of the select
 * list with the columns of its table, and the query is then regenerated from that list (plain
 * execution through {@link JDBCQueryCacheNormalizer}, worksheet merge, vpm).
 * <ul>
 * <li>A: without the columns of the table, the wildcard was deleted. The parse is now refused
 * (PARSE_FAILED), so the sql runs as written and is never regenerated.</li>
 * <li>B: a column of the expansion that is also selected explicitly was skipped, so the list
 * lost a position and the columns after it moved. Every column is now kept at its position.</li>
 * </ul>
 *
 * Rows and column names are compared with the original sql run directly on Derby, through the
 * real {@link XSessionManager#getXNodeTableLens} and {@link JDBCHandler}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCUtilWildcardExpansionTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCUtilWildcardExpansionTest {
   private static final String DB = "memory:bug77617";
   private static final String[] T4 = { "ID", "K:s", "C1", "C2" };

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      // the pool returns a Derby data source directly
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = recording(derby());
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // DerbyHelper asks the repository for the product version, the worksheet column list
      // asks it for the output of a sql it can't take from the selection
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "T", "T4", "T5" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table T (A INT, B VARCHAR(10))");
         stmt.executeUpdate("insert into T values (1, 'z'), (2, 'y'), (3, 'x')");
         stmt.executeUpdate("create table T4 (ID INT, K VARCHAR(5), C1 INT, C2 INT)");
         // the orders of ID, K and C1 all differ
         stmt.executeUpdate(
            "insert into T4 values (1, 'a', 30, 0), (2, 'c', 10, 0), (3, 'b', 20, 0), " +
            "(4, 'd', 40, 0)");
         stmt.executeUpdate("create table T5 (X INT)");
         stmt.executeUpdate("insert into T5 values (7), (8)");
      }

      // the worksheet column list of a sql it can't take from the selection
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);
         return "SQL".equals(mtype.getAttribute("type")) ?
            outputType((String) mtype.getAttribute("sql")) : null;
      });
   }

   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
      reset(repository);
   }

   // ---- A: no metadata ----

   /**
    * The reported shape: select T.*, T.A ran as select T.A. Without the columns of T, the
    * parse is refused and the sql runs as written, with and without an ordinal.
    */
   @Test
   void wildcardWithoutMetadataRunsAsWritten() throws Exception {
      for(String sql : new String[] {
         "select T.*, T.A from T",
         "select T.A, T.* from T",
         "select * from T",
         "select T.* from T",
         "select T.*, T.A from T order by 3 desc, 2 desc" })
      {
         UniformSQL usql = fixed(sql, "h2");

         assertRefused(sql, usql);
         assertSameRows(sql, usql, false);
      }
   }

   // control: a query without a wildcard still parses without metadata
   @Test
   void noWildcardWithoutMetadataIsParsed() throws Exception {
      UniformSQL usql = fixed("select T.A, T.B from T", "h2");

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertTrue(XUtil.isQueryMergeable(newQuery(usql)));
   }

   // the h2, postgresql and oracle helpers are refused the same way
   @Test
   void wildcardWithoutMetadataOnOtherHelpers() throws Exception {
      for(String key : new String[] { "h2", "postgresql", "oracle" }) {
         for(String sql : new String[] { "select t.*, t.id from t", "select t.k, t.* from t",
                                         "select * from t", "select \"t\".* from \"t\"" })
         {
            assertRefused(sql, fixed(sql, key), key);
         }
      }
   }

   /**
    * The column fetch of the table failed (the exception is logged and swallowed), so the
    * columns are unknown: refused like a table without metadata.
    */
   @Test
   void failedMetadataFetchIsRefused() throws Exception {
      JDBCDataSource ds = helper("h2");
      UniformSQL usql = parsed("select T.*, T.A from T", ds);
      JDBCUtil.fixUniformSQLInfo(usql, failingRepository(), null, ds);
      assertRefused("select T.*, T.A from T", usql);

      // control: the sql has no wildcard
      usql = parsed("select T.A from T", ds);
      JDBCUtil.fixUniformSQLInfo(usql, failingRepository(), null, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
   }

   /**
    * Partial metadata, T4 known and T5 not: a bare * covers both tables and is refused as a
    * whole, T4.* is expanded.
    */
   @Test
   void partialMetadataIsDecidedPerTable() throws Exception {
      Map<String, String[]> meta = Map.of("T4", T4);

      for(String sql : new String[] { "select * from T4, T5",
                                      "select T4.*, T5.* from T4, T5",
                                      "select T5.*, T4.ID from T4, T5" })
      {
         UniformSQL usql = fixed(sql, "h2", meta);
         assertRefused(sql, usql);
         assertSameRows(sql, usql, false);
      }

      String sql = "select T4.*, T5.X from T4, T5";
      UniformSQL usql = fixed(sql, "h2", meta);

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertEquals("[T4.ID, T4.K, T4.C1, T4.C2, T5.X]", columns(usql));
      assertSameRows(sql, usql, true);
   }

   /**
    * A derived table's wildcard without metadata leaves its select list, and so the fields of
    * the outer query, short. The outer query is refused.
    */
   @Test
   void derivedTableWildcardWithoutMetadataIsRefused() throws Exception {
      for(String sql : new String[] {
         "select s.* from (select T.*, T.A as A2 from T) s",
         "select s.A from (select T.* from T) s" })
      {
         UniformSQL usql = fixed(sql, "h2");
         assertRefused(sql, usql);
         assertSameRows(sql, usql, false);
      }

      // control: with metadata the derived table is expanded
      String sql = "select s.* from (select T4.* from T4) s";
      UniformSQL usql = fixed(sql, "h2", Map.of("T4", T4));
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertEquals("[s.ID, s.K, s.C1, s.C2]", columns(usql));
   }

   /**
    * Amendment 3: a table whose columns are all vpm hidden from the editing user has no field,
    * but its columns are known. Its wildcard expands to the visible columns (none) and the
    * parse stays, so vpm keeps narrowing it. Unknown comes from the metadata fetch only.
    */
   @Test
   void allHiddenColumnsAreKnown() throws Exception {
      withHiddenColumns(Set.of("ID", "K", "C1", "C2"), () -> {
         UniformSQL usql = fixed("select T4.* from T4", "h2", Map.of("T4", T4));
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
         assertEquals("[]", columns(usql));
         assertEquals(0, usql.getFieldList().length);
      });

      withHiddenColumns(Set.of("K"), () -> {
         UniformSQL usql = fixed("select T4.* from T4", "h2", Map.of("T4", T4));
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
         assertEquals("[T4.ID, T4.C1, T4.C2]", columns(usql));
      });
   }

   // ---- B: an explicit column also in the expansion ----

   @Test
   void expansionKeepsEveryColumnAtItsPosition() throws Exception {
      for(String key : new String[] { "h2", "oracle" }) {
         assertEquals("[t.K, t.ID, t.K, t.C1, t.C2]",
                      columns(fixed("select t.k, t.* from t", key, "ID", "K", "C1", "C2")), key);
         assertEquals("[t.ID, t.K, t.C1, t.C2, t.ID]",
                      columns(fixed("select t.*, t.id from t", key, "ID", "K", "C1", "C2")), key);
         assertEquals("[t.ID, t.ID, t.K, t.C1, t.C2, t.C2]",
                      columns(fixed("select t.id, t.*, t.c2 from t", key, "ID", "K", "C1", "C2")),
                      key);

         UniformSQL usql = fixed("select t.k as kk, t.* from t", key, "ID", "K", "C1", "C2");
         assertEquals("[t.K, t.ID, t.K, t.C1, t.C2]", columns(usql), key);
         assertEquals("kk", usql.getSelection().getAlias(0), key);
         assertNull(usql.getSelection().getAlias(2), key);
      }

      // postgresql stores the qualifier quoted
      UniformSQL usql = fixed("select t.k, t.* from t", "postgresql", "id", "k", "c1", "c2");
      assertEquals(5, usql.getSelection().getColumnCount(), columns(usql));
      assertEquals(usql.getSelection().getColumn(0), usql.getSelection().getColumn(2));
   }

   // the regenerated sql re-parses and regenerates to itself
   @Test
   void regeneratedExpansionRoundTrips() throws Exception {
      for(String sql : new String[] { "select t.k, t.* from t", "select t.*, t.id from t",
                                      "select t.k as kk, t.* from t" })
      {
         String generated = regenerate(fixed(sql, "h2", "ID", "K", "C1", "C2"));
         assertEquals(generated, regenerate(fixed(generated, "h2", "ID", "K", "C1", "C2")), sql);
      }

      // the select list is written sorted (JDBCQueryCacheNormalizer), with both copies of t.K
      assertEquals("select t.C1, t.C2, t.ID, t.K as kk, t.K from t",
                   regenerate(fixed("select t.k as kk, t.* from t", "h2", "ID", "K", "C1", "C2")));
   }

   /**
    * Plain execution without an ordinal: the normalizer clears the sql string and runs the
    * regenerated select list. It returned 4 columns, and the columns after the duplicate
    * moved. Compared with an explicit duplicate, which already worked.
    */
   @Test
   void overlapRunsTheColumnsOfTheSql() throws Exception {
      for(String sql : new String[] {
         "select T4.K, T4.* from T4",
         "select T4.*, T4.ID from T4",
         "select T4.K as KK, T4.* from T4",
         "select T4.ID, T4.*, T4.C2 from T4",
         // control: an explicit duplicate
         "select T4.K, T4.ID, T4.K, T4.C1, T4.C2 from T4" })
      {
         UniformSQL usql = fixed(sql, "h2", Map.of("T4", T4));

         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
         assertSameRows(sql, usql, true);
      }
   }

   /**
    * With an ordinal the sql runs as written, but merge and vpm regenerate it. The ordinal is
    * kept (#77570) and now names the same column of the regenerated select list.
    */
   @Test
   void regeneratedOrdinalNamesTheSameColumn() throws Exception {
      for(String sql : new String[] {
         "select T4.K, T4.* from T4 order by 3 desc",
         "select T4.*, T4.ID from T4 order by 5 desc, 2",
         "select T4.ID, T4.* from T4 order by 4" })
      {
         UniformSQL usql = fixed(sql, "h2", Map.of("T4", T4));
         UniformSQL regenerated = usql.clone();
         String generated = regenerate(regenerated);

         assertEquals(direct(sql), direct(generated), sql + " regenerated as " + generated);
         assertEquals(names(sql), names(generated), generated);
      }
   }

   // ---- persistence ----

   @Test
   void xmlRoundTrip() throws Exception {
      String sql = "select T.*, T.A from T";
      UniformSQL loaded = load(toXML(fixed(sql, "h2")));

      assertEquals(UniformSQL.PARSE_FAILED, loaded.getParseResult());
      assertEquals(sql, loaded.getSQLString());
      assertFalse(XUtil.isParsedSQL(loaded));

      UniformSQL usql = fixed("select t.k, t.* from t", "h2", "ID", "K", "C1", "C2");
      String generated = regenerate(usql.clone());
      loaded = load(toXML(usql));

      assertEquals("[t.K, t.ID, t.K, t.C1, t.C2]", columns(loaded));
      assertEquals(generated, regenerate(loaded));
   }

   // ---- a wildcard left in the selection (amendment 4) ----

   /**
    * A parsed query that didn't go through the metadata step keeps its wildcard. Sorted as one
    * item, the map didn't fit the result, so the normalizer must neither sort nor clear it.
    */
   @Test
   void normalizerKeepsWildcardSelection() throws Exception {
      for(String key : new String[] { "h2", "derby", "postgresql", "oracle" }) {
         for(String sql : new String[] { "select t.a as x, t.* from t",
                                         "select \"T\".*, \"T\".\"A\" from \"T\"",
                                         "select * from t" })
         {
            UniformSQL usql = parsed(sql, helper(key));
            assertTrue(UniformSQL.hasWildcard(usql.getSelection()), key + " " + sql);

            assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key + " " + sql);
            assertEquals(sql, usql.getSQLString(), key);

            JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(newQuery(usql));
            assertNull(normalizer.getOriginalColumnMap(), key + " " + sql);
            assertEquals(sql, usql.getSQLString(), key);
         }
      }

      // the rows of an unexpanded wildcard query run through the session
      String sql = "select T.A as X, T.* from T";
      UniformSQL usql = parsed(sql, helper("derby"));
      assertSameRows(sql, usql, false);
   }

   /**
    * The worksheet column list: a wildcard left in the selection isn't a column named *, and a
    * refused query takes its columns from the output, so select * from T without metadata
    * still lists the real columns.
    */
   @Test
   void worksheetColumnsOfWildcard() throws Exception {
      // not through the metadata step
      UniformSQL usql = parsed("select * from T", helper("derby"));
      assertEquals("[A, B]", worksheetColumns(usql));

      usql = parsed("select T.B as X, T.* from T", helper("derby"));
      assertEquals("[X, A, B]", worksheetColumns(usql));

      // refused
      assertEquals("[A, B]", worksheetColumns(fixed("select * from T", "h2")));
      assertEquals("[A, B]", worksheetColumns(fixed("select T.*, T.A from T", "h2")));
   }

   /**
    * Amendment 5: the worksheet names a column by its alias or column name and drops a repeated
    * name, so the expansion lists the same columns as an explicit duplicate did before.
    */
   @Test
   void worksheetColumnsOfOverlap() throws Exception {
      Map<String, String[]> meta = Map.of("T4", T4);

      assertEquals(worksheetColumns(fixed("select T4.K, T4.ID, T4.K, T4.C1, T4.C2 from T4",
                                          "h2", meta)),
                   worksheetColumns(fixed("select T4.K, T4.* from T4", "h2", meta)));
      assertEquals("[K, ID, C1, C2]",
                   worksheetColumns(fixed("select T4.K, T4.* from T4", "h2", meta)));
      assertEquals("[ID, K, C1, C2]",
                   worksheetColumns(fixed("select T4.*, T4.ID from T4", "h2", meta)));
      assertEquals("[KK, ID, K, C1, C2]",
                   worksheetColumns(fixed("select T4.K as KK, T4.* from T4", "h2", meta)));
   }

   // ---- helpers ----

   private static void assertRefused(String sql, UniformSQL usql) {
      assertRefused(sql, usql, "");
   }

   // refused: the sql runs as written, it is not merged, and no wildcard is left in the
   // selection that a regeneration could write
   private static void assertRefused(String sql, UniformSQL usql, String helper) {
      String msg = helper + " " + sql;
      assertEquals(UniformSQL.PARSE_FAILED, usql.getParseResult(), msg);
      assertEquals(sql, usql.getSQLString(), msg);
      assertFalse(XUtil.isParsedSQL(usql), msg);
      assertFalse(XUtil.isQueryMergeable(newQuery(usql)), msg);
      assertFalse(UniformSQL.hasWildcard(usql.getSelection()), msg);
      assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), msg);
      assertTrue(usql.hasSQLString(), msg);
   }

   /**
    * The rows and column names of the query run through XSessionManager, compared with the
    * original sql.
    * @param regenerated <tt>true</tt> if the query runs the sql regenerated from its structure.
    */
   private void assertSameRows(String sql, UniformSQL usql, boolean regenerated)
      throws Exception
   {
      usql.setDataSource(dataSource());
      Run run = run(newSession(), usql);
      List<List<String>> expected = direct(sql);

      assertFalse(expected.isEmpty());
      assertEquals(expected, rows(run.table), sql + " executed as " + run.executedSql);
      assertEquals(names(sql), header(run.table), sql + " executed as " + run.executedSql);
      assertEquals(!regenerated, sql.equals(run.executedSql), run.executedSql);
   }

   private static String columns(UniformSQL usql) {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < usql.getSelection().getColumnCount(); i++) {
         list.add(usql.getSelection().getColumn(i));
      }

      return list.toString();
   }

   private String worksheetColumns(UniformSQL usql) throws Exception {
      usql.setDataSource(dataSource());
      SQLBoundTableAssembly assembly = mock(SQLBoundTableAssembly.class);
      when(assembly.getColumnSelection()).thenReturn(new ColumnSelection());
      when(assembly.getAggregateInfo()).thenReturn(new AggregateInfo());
      QueryManagerService qms = new QueryManagerService(
         mock(RuntimeQueryService.class), repository, mock(DataSourceService.class),
         mock(SecurityEngine.class), mock(ColumnCache.class));
      ColumnSelection columns = qms.getColumnSelection(newQuery(usql), new VariableTable(),
                                                       assembly, null, null);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getName());
      }

      return names.toString();
   }

   // the parsed sql, through the metadata step with no table columns
   private static UniformSQL fixed(String sql, String helper) throws Exception {
      return fixed(sql, helper, Map.of());
   }

   // the parsed sql, through the metadata step with the same columns for every table
   private static UniformSQL fixed(String sql, String helper, String... columns)
      throws Exception
   {
      JDBCDataSource ds = helper(helper);
      UniformSQL usql = parsed(sql, ds);
      JDBCUtil.fixUniformSQLInfo(usql, repository(table -> columns), null, ds);
      return usql;
   }

   // the parsed sql, through the metadata step with the columns of the tables in the map
   private static UniformSQL fixed(String sql, String helper, Map<String, String[]> meta)
      throws Exception
   {
      JDBCDataSource ds = helper(helper);
      UniformSQL usql = parsed(sql, ds);
      JDBCUtil.fixUniformSQLInfo(usql, repository(table -> meta.get(table.toUpperCase())),
                                 null, ds);
      return usql;
   }

   private static UniformSQL parsed(String sql, JDBCDataSource ds) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      return usql;
   }

   /**
    * @param columns the columns of a table by its name, "name:s" for a string column, null
    *                for a table without metadata.
    */
   private static XRepository repository(java.util.function.Function<String, String[]> columns)
      throws Exception
   {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         String name = mtype.getName();
         name = name.substring(name.lastIndexOf('.') + 1).replace("\"", "");
         String[] list = columns.apply(name);
         XTypeNode result = new XTypeNode("Result");

         for(String column : list == null ? new String[0] : list) {
            boolean str = column.endsWith(":s");
            column = str ? column.substring(0, column.length() - 2) : column;
            result.addChild(XSchema.createPrimitiveType(column, str ? String.class : Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // the column fetch throws
   private static XRepository failingRepository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         throw new IOException("connection lost");
      });

      return repository;
   }

   /**
    * Run with a vpm that hides columns of every table from the editing user.
    */
   private static void withHiddenColumns(Set<String> hidden, ThrowingRunnable test) throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      Object old = field.get(null);
      field.set(null, new VpmProcessor() {
         @Override
         public BiFunction<String, String, Boolean> getHiddenColumnsSelector(
            String[] tables, String[] columns, String modelName, String partition,
            VariableTable vars, Principal user)
         {
            return (table, column) -> hidden.contains(column);
         }
      });

      try {
         test.run();
      }
      finally {
         field.set(null, old);
      }
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource helper(String key) {
      String[] spec = switch(key) {
         case "derby" -> new String[] { "org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:" + DB };
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:x" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver",
                                         "jdbc:oracle:thin:@localhost:1521:x" };
         case "postgresql" -> new String[] { "org.postgresql.Driver",
                                             "jdbc:postgresql://localhost/db" };
         default -> throw new IllegalArgumentException(key);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77617" + key + "_" + (++sources) + "_" + RUN);
      ds.setDriver(spec[0]);
      ds.setURL(spec[1]);
      ds.setRequireLogin(false);
      ds.setRuntimeProductName(key);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   private static String regenerate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String toXML(UniformSQL usql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return loaded;
   }

   private static XTypeNode outputType(String sql) throws Exception {
      XTypeNode output = new XTypeNode("table");

      for(String name : names(sql)) {
         output.addChild(XSchema.createPrimitiveType(name, String.class));
      }

      return output;
   }

   private static List<String> names(String sql) throws Exception {
      List<String> names = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         ResultSetMetaData meta = rs.getMetaData();

         for(int i = 1; i <= meta.getColumnCount(); i++) {
            names.add(meta.getColumnLabel(i).toUpperCase());
         }
      }

      return names;
   }

   private static List<List<String>> direct(String sql) throws Exception {
      List<List<String>> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            rows.add(row);
         }
      }

      // the rows of a sql without an order by come back in the same order from Derby
      return rows;
   }

   private static List<String> header(TableLens table) {
      List<String> names = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         String name = String.valueOf(table.getObject(0, c));
         names.add(name.substring(name.lastIndexOf('.') + 1).toUpperCase());
      }

      return names;
   }

   private static List<List<String>> rows(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      List<List<String>> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(String.valueOf(table.getObject(r, c)));
         }

         rows.add(row);
      }

      return rows;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77617");
      query.setDataSource(usql.getDataSource() == null ? dataSource() : usql.getDataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77617");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static XSessionManager newSession() throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> execute(inv.getArgument(1), inv.getArgument(2),
                                    inv.getArgument(3), inv.getArgument(5)));
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);
      return session;
   }

   private Run run(XSessionManager session, UniformSQL usql) throws Exception {
      JDBCQuery query = newQuery(usql);
      Run run = new Run();
      currentRun.set(run);

      try {
         run.table = session.getXNodeTableLens(query, new VariableTable(), null, null, null, -1);
      }
      finally {
         currentRun.remove();
      }

      assertNotNull(run.table, "query failed, see log");
      return run;
   }

   private static XNode execute(XQuery query, VariableTable vars, Principal user,
                                inetsoft.util.DataCacheVisitor visitor) throws Exception
   {
      Run run = currentRun.get();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, user, visitor);
      run.executedSql = executed.get();
      return node;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   /**
    * JDBCHandler generates the sql on a clone of the query, so record the sql that reaches
    * the connection instead.
    */
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         JDBCUtilWildcardExpansionTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               executed.set((String) args[0]);
            }

            Object result;

            try {
               result = method.invoke(target, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(result instanceof Connection && type != Connection.class) {
               return proxy(Connection.class, (Connection) result);
            }

            if(result instanceof Statement && !(result instanceof PreparedStatement)) {
               return proxy(Statement.class, (Statement) result);
            }

            return result;
         });
   }

   private static final class Run {
      TableLens table;
      String executedSql;
   }

   @Autowired
   private XRepository repository;
   private static final long RUN = System.nanoTime();
   private static int sources;
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
}
