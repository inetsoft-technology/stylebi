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
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77543. For sql that isn't parsed (parse off, or parse on with PARSE_INIT or
 * PARSE_PARTIALLY), the enterprise VpmUtil writes its row condition into the sql string.
 * {@link JDBCHandler#execute} must not null that string and regenerate the sql from the
 * structure because of a {@link UniformSQL#HINT_CLEARED_SQL_STRING} hint copied from an
 * earlier generation, or the row condition is dropped.
 *
 * The query runs through the real {@link XSessionManager#getXNodeTableLens} (cache visitor
 * and normalizer) and the real {@link JDBCHandler#execute} on embedded Derby, with a
 * {@link VpmProcessor} that rewrites the query the way the enterprise VpmUtil does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCHandlerVpmSqlStringTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCHandlerVpmSqlStringTest {
   private static final String DB = "memory:bug77543";
   private static final String SQL = "select T.B, T.A from T where T.A > 0";
   // the regenerated sql of a lossy query would drop the second condition
   private static final String LOSSY_SQL = SQL + " and T.B = 'x'";
   // the row condition the test vpm adds
   private static final String VPM_CONDITION = "T.A = 1";

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
   // sessions down here, while their mock data service is alive.
   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
   }

   @BeforeEach
   void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table T");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table T (A INT, B VARCHAR(10))");
         stmt.executeUpdate("insert into T values (1, 'x'), (2, 'y')");
      }
   }

   // the reported shape: parse off, with the full structure of an earlier parse
   @Test
   void parseOffWithFullStructureIsFiltered() throws Exception {
      Run run = run(parseOff());

      assertFiltered(run);
   }

   // a CLEARED hint left by an earlier generation, without the SORTED_COLUMN hint
   @Test
   void staleClearedHintWithParseOffIsFiltered() throws Exception {
      UniformSQL usql = parseOff();
      usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      Run run = run(usql);

      assertFiltered(run);
   }

   @ParameterizedTest
   @ValueSource(ints = { UniformSQL.PARSE_INIT, UniformSQL.PARSE_PARTIALLY })
   void staleClearedHintWithUnparsedParseOnIsFiltered(int parseResult) throws Exception {
      UniformSQL usql = parsed();
      usql.setParseResult(parseResult);
      usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      Run run = run(usql);

      assertFiltered(run);
   }

   // without a DataCacheResult visitor, a stale sorted hint makes JDBCHandler test a
   // normalizer on the query, and a stale CLEARED hint must not drop the vpm text either
   @Test
   void staleHintsWithoutCacheNormalizerAreFiltered() throws Exception {
      UniformSQL usql = parsed();
      usql.setParseResult(UniformSQL.PARSE_INIT);
      usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      usql.setHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, true);
      JDBCQuery query = newQuery(usql);
      VariableTable vars = new VariableTable();

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, USER, null);

      Run run = new Run();
      run.executedSql = executed.get();
      run.table = new XNodeTableLens(node);
      assertFiltered(run);
   }

   // a lossy parsed query keeps its sql string, the structure doesn't describe all of it, so
   // a stale CLEARED hint must not regenerate the sql from the structure
   @Test
   void staleClearedHintWithLossyParsedSqlKeepsSqlString() throws Exception {
      UniformSQL usql = lossy();
      usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      Run run = run(usql);

      assertLossySqlExecuted(run);
   }

   // same as above, without a DataCacheResult visitor
   @Test
   void staleHintsWithLossyParsedSqlWithoutCacheNormalizerKeepSqlString() throws Exception {
      UniformSQL usql = lossy();
      usql.setHint(UniformSQL.HINT_CLEARED_SQL_STRING, true);
      usql.setHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, true);
      JDBCQuery query = newQuery(usql);
      VariableTable vars = new VariableTable();

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, USER, null);

      Run run = new Run();
      run.executedSql = executed.get();
      run.table = new XNodeTableLens(node);
      assertLossySqlExecuted(run);
   }

   // Bug #59595: a parsed query keeps its sql string when vpm builds the normalizer on a
   // clone. JDBCHandler still regenerates it sorted, and the select order is restored.
   @Test
   void parsedQueryIsRegeneratedSortedAndRestored() throws Exception {
      Run run = run(parsed());

      assertTrue(norm(run.executedSql).startsWith("select t.a, t.b"), run.executedSql);
      assertHeaders(run.table, "B", "A");
      assertEquals(3, rowCount(run.table));
   }

   private static void assertFiltered(Run run) {
      assertTrue(norm(run.executedSql).contains(norm(VPM_CONDITION)),
                 "vpm condition dropped, executed: " + run.executedSql);
      assertHeaders(run.table, "B", "A");
      assertEquals(2, rowCount(run.table), "header plus the one row the vpm allows");
      assertEquals("x", run.table.getObject(1, 0));
      assertEquals(1, ((Number) run.table.getObject(1, 1)).intValue());
   }

   private static void assertLossySqlExecuted(Run run) {
      assertEquals(norm(LOSSY_SQL), norm(run.executedSql), "lossy sql string regenerated");
      assertHeaders(run.table, "B", "A");
      assertEquals(2, rowCount(run.table), "header plus the one row the sql string allows");
      assertEquals("x", run.table.getObject(1, 0));
   }

   private static void assertHeaders(TableLens table, String... names) {
      assertEquals(names.length, table.getColCount());

      for(int i = 0; i < names.length; i++) {
         String header = String.valueOf(table.getObject(0, i)).toUpperCase();
         header = header.substring(header.lastIndexOf('.') + 1);
         assertEquals(names[i], header, "header " + i);
      }
   }

   private static int rowCount(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount();
   }

   private static String norm(String sql) {
      return sql == null ? "" : sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static UniformSQL parsed() throws Exception {
      UniformSQL usql = new UniformSQL();
      // the source is set first, a source set later re-derives a lossy set by a test
      usql.setDataSource(dataSource());
      usql.parse(SQL, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(SQL, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertEquals(2, usql.getSelection().getColumnCount());
      return usql;
   }

   // a parsed query whose sql string has more than its structure (the structure is from SQL),
   // like a dialect construct the parser can't represent (e.g. Bug #72243)
   private static UniformSQL lossy() throws Exception {
      UniformSQL usql = parsed();
      usql.setSQLString(LOSSY_SQL, false);
      usql.setLossy(true);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertTrue(XUtil.isParsedSQL(usql));
      return usql;
   }

   // a query parsed while parse was on, then switched to parse off
   private static UniformSQL parseOff() throws Exception {
      UniformSQL usql = parsed();
      usql.setParseSQL(false);
      return usql;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77543");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77543");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   /**
    * Run a query by a vpm user through XSessionManager. The data service stands in for
    * XEngine.execute, which only calls the handler for a DataCacheResult visitor.
    */
   private static Run run(UniformSQL usql) throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery query = inv.getArgument(1);
            VariableTable vars = inv.getArgument(2);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(query.getDataSource(), vars);
            executed.set(null);
            return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);

      Run run = new Run();
      run.table = session.getXNodeTableLens(newQuery(usql), new VariableTable(), USER,
                                            null, null, -1);
      run.executedSql = executed.get();
      assertNotNull(run.table, "query failed, see log");
      return run;
   }

   /**
    * Rewrites the query like the enterprise VpmUtil for a user the vpm applies to:
    * applyHiddenColumns returns a clone, and applyConditions adds the row condition to the
    * sql string of a query that isn't parsed and saves it without parsing. A parsed query
    * gets no condition here, VpmUtil adds it to the structure.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user)
      {
         if(user == null) {
            return query;
         }

         JDBCQuery clone = (JDBCQuery) query.clone();
         UniformSQL usql = (UniformSQL) clone.getSQLDefinition();

         if(!XUtil.isParsedSQL(usql)) {
            usql.setSQLString(usql.getSQLString() + " and " + VPM_CONDITION, false);
         }

         return clone;
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user) {
         return user == null ? query : (XQuery) query.clone();
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
         JDBCHandlerVpmSqlStringTest.class.getClassLoader(), new Class<?>[] { type },
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

   private static final Principal USER = new XPrincipal(new IdentityID("bug77543", "host-org"));
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
   private static Object oldVpmProcessor;
}
