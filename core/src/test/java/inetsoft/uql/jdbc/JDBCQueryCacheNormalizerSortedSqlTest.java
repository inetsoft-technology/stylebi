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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.storage.BlobStorageManager;
import inetsoft.sree.internal.cluster.Cluster;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.erm.vpm.VpmProcessor;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77485. {@link JDBCQueryCacheNormalizer} decides on a column sort map before
 * {@link JDBCHandler} runs, while only JDBCHandler knows whether the executed SQL was sorted.
 * A query whose sql string is kept (lossy, PARSE_INIT) or regenerated after the normalizer ran
 * (VPM conditions) must still return its columns in SELECT order, with headers matching data.
 *
 * The query runs through the real {@link XSessionManager#getXNodeTableLens} (cache visitor,
 * normalizer, buildXTable transform and query cache) and the real {@link JDBCHandler#execute}
 * on embedded Derby. The data service stands in for XEngine.execute, which only calls the
 * handler for a {@link XSessionManager.DataCacheResult} visitor.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCQueryCacheNormalizerSortedSqlTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCQueryCacheNormalizerSortedSqlTest {
   private static final String DB = "memory:bug77485";
   private static final String SQL = "select T.B, T.A from T where T.A = 1";

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

      // the DriverManager in this JVM trips over another driver's static init, so the pool
      // returns a Derby data source directly
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

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   // XSessionManager.finalize() calls tearDown(), which closes the data service. Tear the
   // sessions down here, while their mock data service is alive, so a later finalizer does
   // not call into a garbage collected mock on the Finalizer thread during another test.
   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "T", "T2" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (A INT, B VARCHAR(10))");
            stmt.executeUpdate("insert into " + table + " values (1, 'x')");
         }
      }
   }

   // control: a parsed, not lossy query is regenerated sorted, and the inverse map restores
   // the SELECT order
   @Test
   void parsedNotLossyIsRegeneratedSortedAndRestored() throws Exception {
      Run run = run(newSession(false), parsed(SQL), null);

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // ClickHouse/Databricks map-key access: PARSE_SUCCESS with lossy=true runs as written
   @Test
   void lossyWithParseOnRunsVerbatimInSelectOrder() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setLossy(true);
      Run run = run(newSession(false), usql, null);

      assertEquals(SQL, run.executedSql);
      assertSelectOrder(run.table);
   }

   // a saved query with "parse SQL" off and lossy="true"
   @Test
   void lossyWithParseOffRunsVerbatimInSelectOrder() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setParseSQL(false);
      usql.setLossy(true);
      Run run = run(newSession(false), usql, null);

      assertEquals(SQL, run.executedSql);
      assertSelectOrder(run.table);
   }

   // parse off, not lossy: the normalizer clears the sql string. XUtil.clearComments then
   // generates the sorted sql in JDBCHandler and saves it, before the final generation.
   @Test
   void parseOffNotLossyIsRegeneratedSortedAndRestored() throws Exception {
      Run run = run(newSession(false), parseOffNotLossy(SQL), null);

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   @Test
   void parseOffNotLossyCacheHitKeepsSelectOrder() throws Exception {
      String sql2 = "select T2.B, T2.A from T2 where T2.A = 1";
      XSessionManager session = newSession(true);
      assertSelectOrder(run(session, parseOffNotLossy(sql2), null).table);

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         stmt.executeUpdate("insert into T2 values (1, 'y')");
      }

      Run second = run(session, parseOffNotLossy(sql2), null);
      assertEquals(2, rowCount(second.table), "expected a cache hit");
      assertSelectOrder(second.table);
   }

   // enterprise VpmUtil.applyConditions generates a parse-off query's sql inside
   // JDBCHandler.execute and saves it as the sql string
   @Test
   void parseOffNotLossySavedByVpmIsRestored() throws Exception {
      Run run = run(newSession(false), parseOffNotLossy(SQL), null,
                    u -> u.setSQLString(u.getSQLString(), false));

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // VPM conditions regenerate a lossy query inside JDBCHandler.execute
   @Test
   void lossyRegeneratedByVpmIsRestored() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setLossy(true);
      Run run = run(newSession(false), usql, null, UniformSQL::clearSQLString);

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // no map (maxrow text), then VPM conditions regenerate inside JDBCHandler.execute
   @Test
   void noMapThenRegeneratedByVpmIsNotSorted() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setSQLString(SQL + " fetch first 5 rows only", false);
      Run run = run(newSession(false), usql, null, UniformSQL::clearSQLString);

      assertTrue(norm(run.executedSql).startsWith("select t.b, t.a"), run.executedSql);
      assertSelectOrder(run.table);
   }

   private static UniformSQL parseOffNotLossy(String sql) throws Exception {
      UniformSQL usql = parsed(sql);
      usql.setParseSQL(false);
      usql.setLossy(false);
      return usql;
   }

   // setSQLString(s, false) or an unfinished async parse: PARSE_INIT keeps the sql string
   @Test
   void parseInitRunsVerbatimInSelectOrder() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setLossy(false);
      usql.setParseResult(UniformSQL.PARSE_INIT);
      Run run = run(newSession(false), usql, null);

      assertEquals(SQL, run.executedSql);
      assertSelectOrder(run.table);
   }

   // VPM conditions regenerate a lossy query inside JDBCHandler, after the normalizer kept its
   // sql string. The regenerated sql is sorted and the map must still be applied.
   @Test
   void lossyRegeneratedAfterNormalizerIsRestored() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setLossy(true);
      Run run = run(newSession(false), usql, UniformSQL::clearSQLString);

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // maxrow text makes the normalizer produce no map. If VPM conditions then regenerate the
   // sql, it must not be sorted, since nothing would restore the order.
   @Test
   void noMapThenRegeneratedIsNotSorted() throws Exception {
      UniformSQL usql = parsed(SQL);
      usql.setSQLString(SQL + " fetch first 5 rows only", false);
      Run run = run(newSession(false), usql, UniformSQL::clearSQLString);

      assertTrue(norm(run.executedSql).startsWith("select t.b, t.a"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // a sorted hint left on the source object by an earlier generation must not make the
   // handler treat a verbatim sql string as sorted
   @Test
   void staleSortedHintDoesNotApplyToVerbatimSql() throws Exception {
      UniformSQL usql = parsed(SQL);
      JDBCQuery query = newQuery(usql);
      new JDBCQueryCacheNormalizer(query);
      String generated = query.getSQLAsString();
      assertTrue(norm(generated).startsWith("select t.a, t.b"), generated);
      assertEquals(Boolean.TRUE,
                   usql.getHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, false));

      usql.setSQLString(SQL, false);
      usql.setLossy(true);
      Run run = run(newSession(false), usql, null);

      assertEquals(SQL, run.executedSql);
      assertSelectOrder(run.table);
   }

   // the cached raw table is shared by queries with the same executed sql, and each applies
   // its own transform on a cache hit
   @Test
   void cacheHitKeepsSelectOrder() throws Exception {
      String sql2 = "select T2.B, T2.A from T2 where T2.A = 1";
      String sorted2 = "select T2.A, T2.B from T2 where T2.A = 1";
      XSessionManager session = newSession(true);

      Run first = run(session, parsed(sql2), null);
      assertSelectOrder(first.table);

      DataSource ds = derby();

      try(Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
         stmt.executeUpdate("insert into T2 values (1, 'y')");
      }

      // same query again: cache hit (still one row), order restored
      Run second = run(session, parsed(sql2), null);
      assertEquals(2, rowCount(second.table), "expected a cache hit");
      assertSelectOrder(second.table);

      // the other column order regenerates to the same sql: cache hit, no transform needed
      Run third = run(session, parsed(sorted2), null);
      assertEquals(2, rowCount(third.table), "expected a cache hit");
      assertHeaders(third.table, "A", "B");
      assertEquals(1, ((Number) third.table.getObject(1, 0)).intValue());
      assertEquals("x", third.table.getObject(1, 1));

      // a lossy query runs verbatim, a different key: a miss that sees both rows
      UniformSQL lossy = parsed(sql2);
      lossy.setLossy(true);
      Run fourth = run(session, lossy, null);
      assertEquals(3, rowCount(fourth.table));
      assertSelectOrder(fourth.table);

      // and a cache hit of the lossy query keeps the select order too
      try(Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
         stmt.executeUpdate("insert into T2 values (1, 'z')");
      }

      UniformSQL lossy2 = parsed(sql2);
      lossy2.setLossy(true);
      Run fifth = run(session, lossy2, null);
      assertEquals(3, rowCount(fifth.table), "expected a cache hit");
      assertSelectOrder(fifth.table);
   }

   // Bug #59595 path: VPM hidden columns build the normalizer on a clone, so the query that
   // reaches JDBCHandler still has its sql string. JDBCHandler clears it, the sql is
   // regenerated sorted, and the inverse map must still be applied.
   @Test
   void normalizerOnCloneWithKeptSqlStringIsRegeneratedSortedAndRestored() throws Exception {
      Run run = run(newSession(false), parsed(SQL), usql -> usql.sqlstring = SQL);

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertSelectOrder(run.table);
   }

   // a cache hit of a lossy query that VPM conditions regenerate keeps the select order, and so
   // does a cache hit of a map-less query that VPM conditions regenerate (unsorted)
   @Test
   void cacheHitOfRegeneratedQueriesKeepsSelectOrder() throws Exception {
      XSessionManager session = newSession(true);

      for(int i = 0; i < 2; i++) {
         UniformSQL lossy = parsed(SQL);
         lossy.setLossy(true);
         Run run = run(session, lossy, UniformSQL::clearSQLString);
         // a cache hit executes nothing, so the sql is only checked on the first run
         assertTrue(i == 1 || norm(run.executedSql).startsWith("select t.a, t.b"),
                    run.executedSql);
         assertEquals(2, rowCount(run.table), "header plus one row, a hit on the second run");
         assertSelectOrder(run.table);

         UniformSQL noMap = parsed(SQL);
         noMap.setSQLString(SQL + " fetch first 5 rows only", false);
         run = run(session, noMap, UniformSQL::clearSQLString);
         // a sorted regeneration would share the lossy query's cache entry and execute nothing
         assertTrue(i == 1 || String.valueOf(norm(run.executedSql)).startsWith("select t.b, t.a"),
                    "map-less query must run unsorted, executed: " + run.executedSql);
         assertEquals(2, rowCount(run.table), "header plus one row, a hit on the second run");
         assertSelectOrder(run.table);

         if(i == 0) {
            try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
               stmt.executeUpdate("insert into T values (1, 'y')");
            }
         }
      }
   }

   private static String norm(String sql) {
      return sql == null ? null : sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static int rowCount(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount();
   }

   private static void assertSelectOrder(TableLens table) {
      assertNotNull(table);
      assertTrue(table.moreRows(1));
      assertHeaders(table, "B", "A");
      assertEquals("x", table.getObject(1, 0));
      assertEquals(1, ((Number) table.getObject(1, 1)).intValue());
   }

   private static void assertHeaders(TableLens table, String... names) {
      assertEquals(names.length, table.getColCount());

      for(int i = 0; i < names.length; i++) {
         String header = String.valueOf(table.getObject(0, i)).toUpperCase();
         header = header.substring(header.lastIndexOf('.') + 1);
         assertEquals(names[i], header, "header " + i);
      }
   }

   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertEquals(2, usql.getSelection().getColumnCount());
      return usql;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77485");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77485");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static XSessionManager newSession(boolean cache) throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> execute(inv.getArgument(1), inv.getArgument(2),
                                    inv.getArgument(3), inv.getArgument(5)));
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(cache);
      sessions.add(session);
      return session;
   }

   /**
    * Run a query through XSessionManager. The hook runs on the query after the cache
    * normalizer was built and before JDBCHandler executes it.
    */
   private Run run(XSessionManager session, UniformSQL usql, Consumer<UniformSQL> hook)
      throws Exception
   {
      return run(session, usql, hook, null);
   }

   /**
    * Run a query through XSessionManager. The vpm hook runs inside JDBCHandler.execute, on a
    * clone of the query returned from VpmProcessor.applyConditions, the way the enterprise
    * VpmUtil.applyConditions regenerates (and for parse-off sql saves) the sql string.
    */
   private Run run(XSessionManager session, UniformSQL usql, Consumer<UniformSQL> hook,
                   Consumer<UniformSQL> vpmHook)
      throws Exception
   {
      JDBCQuery query = newQuery(usql);
      Run run = new Run();
      run.hook = hook;
      run.vpmHook = vpmHook;
      currentRun.set(run);

      try {
         run.table = session.getXNodeTableLens(query, new VariableTable(),
                                               vpmHook != null ? USER : null, null, null, -1);
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
      UniformSQL usql = (UniformSQL) ((JDBCQuery) query).getSQLDefinition();

      if(run.hook != null) {
         run.hook.accept(usql);
      }

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, user, visitor);
      run.executedSql = executed.get();
      return node;
   }

   /**
    * Stands in for the enterprise VpmProcessor. Only a run with a vpm hook changes the query.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user)
      {
         Run run = currentRun.get();

         if(run == null || run.vpmHook == null) {
            return query;
         }

         JDBCQuery clone = (JDBCQuery) query.clone();
         run.vpmHook.accept((UniformSQL) clone.getSQLDefinition());
         return clone;
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user) {
         return query;
      }
   }

   private static Field vpmProcessorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   @BeforeAll
   static void installVpmProcessor() throws Exception {
      Field field = vpmProcessorField();
      oldVpmProcessor = field.get(null);
      field.set(null, new TestVpmProcessor());
   }

   @AfterAll
   static void restoreVpmProcessor() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
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
         JDBCQueryCacheNormalizerSortedSqlTest.class.getClassLoader(), new Class<?>[] { type },
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
      Consumer<UniformSQL> hook;
      Consumer<UniformSQL> vpmHook;
   }

   // a vpm hook only runs for a user, as in JDBCHandler.execute
   private static final Principal USER = new XPrincipal(new IdentityID("bug77485", "host-org"));
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
   private static Object oldVpmProcessor;
}
