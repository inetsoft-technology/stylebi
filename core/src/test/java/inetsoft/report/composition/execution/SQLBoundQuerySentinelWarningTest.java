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
package inetsoft.report.composition.execution;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78386 on the worksheet path: a sql-edited SQL bound table run through
 * SQLBoundQuery.merge and the real XSessionManager.getXNodeTableLens (only the data service is
 * mocked, to call the real JDBCHandler) on Derby. A sentinel parameter kept as written (an
 * item of an IN list with other items, or one value of a multi-value parameter) logs exactly
 * one warning per execution, counted at the root logger on the test thread so that a warning
 * from any class counts, and never per row (500 rows match). The sql sent and the rows are
 * the ones sent before the fix, including the kept sql string (comment and all) that the new
 * multi-value gate in JDBCHandler.rewriteKeptSqlSentinels must still send unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundQuerySentinelWarningTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundQuerySentinelWarningTest {
   private static final String DB = "memory:bug78386ws";
   private static final String NV = XConstants.CONDITION_NULL_VALUE;
   private static final String ES = XConstants.CONDITION_EMPTY_STRING;
   private static final Principal USER = new XPrincipal(new IdentityID("bug78386", "host-org"));
   // a positional ORDER BY keeps the normalizer from clearing the sql string
   private static final String ORDER_BY = " group by b.k order by 1";
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
   private static Object oldVpmProcessor;
   private static ListAppender<ILoggingEvent> appender;

   @Configuration
   static class JdbcConfig {
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

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = proxy(DataSource.class, derby());
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

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeAll
   static void setUp() throws Exception {
      Field field = vpmProcessorField();
      oldVpmProcessor = field.get(null);
      field.set(null, new TestVpmProcessor());

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table b");
         }
         catch(Exception ignore) {
            // first run
         }

         // rows holding the text of the sentinels show a parameter bound as that text
         stmt.executeUpdate("create table b (k int, name varchar(20))");
         stmt.executeUpdate("insert into b values (1, 'n1'), (2, ''), (3, null), " +
                               "(4, 'NULL_VALUE'), (5, 'x'), (6, 'EMPTY_STRING'), (7, 'null')");

         // many rows match 'x', a warning per row would show as hundreds of events
         for(int i = 0; i < 500; i++) {
            stmt.executeUpdate("insert into b values (" + (100 + i) + ", 'x')");
         }
      }
   }

   @AfterAll
   static void tearDown() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
   }

   @BeforeEach
   void attachAppender() {
      appender = new ListAppender<>();
      appender.start();
      rootLogger().addAppender(appender);
   }

   @AfterEach
   void detach() {
      rootLogger().detachAppender(appender);
      appender.stop();
      sessions.forEach(XSessionManager::tearDown);
      sessions.clear();
   }

   // the kept sql string of a sql-edited table that can't be merged
   @Test
   void keptSqlStringWarnsOnce() throws Exception {
      assertRun("select b.k from b where b.name in ($(p), 'x')" + ORDER_BY, NV, null,
                "select b.k from b where b.name in ( ? , 'x') group by b.k order by 1",
                true, List.of(4, 5), "NULL_VALUE value of parameter p is in an IN list");
      assertRun("select b.k from b where b.name in ('x', $(p))" + ORDER_BY, ES, null,
                "select b.k from b where b.name in ('x', ? ) group by b.k order by 1",
                true, List.of(5, 6), "EMPTY_STRING value of parameter p is in an IN list");
      assertRun("select b.k from b where b.name in ('$(p)', 'x')" + ORDER_BY, NV, null,
                "select b.k from b where b.name in ('NULL_VALUE', 'x') group by b.k order by 1",
                true, List.of(4, 5), "NULL_VALUE value of parameter p is in an IN list");
      assertRun("select b.k from b where b.name in ($(p))" + ORDER_BY, new Object[] { NV, "x" },
                null, "select b.k from b where b.name in ( ? , ? ) group by b.k order by 1",
                true, List.of(4, 5), "Parameter p has several values, one of them NULL_VALUE");
   }

   // the multi-value gate lets the query into the rewrite, which must still send the user's
   // exact sql string: the comment is kept and the unset parameter binds NULL
   @Test
   void multiValueGateSendsKeptSqlAsWritten() throws Exception {
      assertRun("select b.k /* note */ from b where b.name in ($(p)) and (b.k = $(u) or 1 = 1)" +
                   ORDER_BY, new Object[] { NV, "x" }, null,
                "select b.k /* note */ from b where b.name in ( ? , ? ) and (b.k = ? or 1 = 1) " +
                   "group by b.k order by 1",
                true, List.of(4, 5), "Parameter p has several values, one of them NULL_VALUE");
   }

   // the normalizer clears the string of a mergeable table
   @Test
   void mergedTableWarnsOnce() throws Exception {
      assertRun("select b.k from b where b.name in ($(p), 'x')", NV, null,
                "select b.k from b where b.name in ( ? ,'x')", false, List.of(4, 5),
                "NULL_VALUE value of parameter p is in an IN list");
      assertRun("select b.k from b where b.name in ($(p))", new Object[] { NV, "x" }, null,
                "select b.k from b where b.name in ( ? , ? )", false, List.of(4, 5),
                "Parameter p has several values, one of them NULL_VALUE");
   }

   // the secured state: the vpm processor returns a clone, the string is kept
   @Test
   void vpmCloneStateWarnsOnce() throws Exception {
      assertRun("select b.k from b where b.name in ($(p), 'x') group by b.k", NV, USER,
                "select b.k from b where b.name in ( ? ,'x') group by b.k", true,
                List.of(4, 5), "NULL_VALUE value of parameter p is in an IN list");
      assertRun("select b.k from b where b.name in ($(p)) group by b.k",
                new Object[] { NV, "x" }, USER,
                "select b.k from b where b.name in ( ? , ? ) group by b.k", true,
                List.of(4, 5), "Parameter p has several values, one of them NULL_VALUE");
   }

   // nothing kept as a sentinel: a plain value, and the single item rewritten to IS NULL
   @Test
   void noWarningWithoutKeptSentinel() throws Exception {
      assertRun("select b.k from b where b.name in ($(p), 'x')" + ORDER_BY, "n1", null,
                "select b.k from b where b.name in ( ? , 'x') group by b.k order by 1",
                true, List.of(1, 5), null);
      assertRun("select b.k from b where b.name in ($(p))" + ORDER_BY,
                new Object[] { "n1", "x" }, null,
                "select b.k from b where b.name in ( ? , ? ) group by b.k order by 1",
                true, List.of(1, 5), null);
      assertRun("select b.k from b where b.name in ($(p))" + ORDER_BY, NV, null,
                "select b.k from b where b.name is null group by b.k order by 1 asc",
                true, List.of(3), null);
   }

   // the same table run three times warns once per run
   @Test
   void sameTableWarnsOncePerRun() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table =
         newTable(ws, "select b.k from b where b.name in ($(p), 'x')" + ORDER_BY);

      for(int i = 0; i < 3; i++) {
         Run run = run(ws, table, NV, null);
         assertEquals(List.of(4, 5), run.ids, "run " + i);
         assertEquals(500, run.filler, "run " + i);
         assertEquals(1, run.warnings.size(), "run " + i + " " + run.warnings);
      }
   }

   private static void assertRun(String sql, Object p, Principal user, String expectedSql,
                                 boolean keptString, List<Integer> ids, String warning)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      Run run = run(ws, newTable(ws, sql), p, user);
      String label = sql + " p=" + Arrays.deepToString(new Object[] { p });
      assertEquals(keptString, run.keptString, label);
      assertEquals(norm(expectedSql), norm(run.sql), label);
      assertEquals(ids, run.ids, label);
      assertEquals(ids.contains(5) ? 500 : 0, run.filler, label);

      if(warning == null) {
         assertEquals(List.of(), run.warnings, label);
      }
      else {
         assertEquals(1, run.warnings.size(), label + " " + run.warnings);
         assertTrue(run.warnings.get(0).contains(warning), run.warnings.get(0));
      }
   }

   private static Run run(Worksheet ws, SQLBoundTableAssembly table, Object p, Principal user)
      throws Exception
   {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      appender.list.clear();
      query.merge(vars);
      JDBCQuery jquery = query.getQuery();
      Run run = new Run();
      run.keptString = ((UniformSQL) jquery.getSQLDefinition()).hasSQLString();

      XRepository dataService = mock(XRepository.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery q = inv.getArgument(1);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(q.getDataSource(), inv.getArgument(2));
            executed.set(null);
            return handler.execute(q, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);

      TableLens lens = session.getXNodeTableLens(jquery, vars, user, null, null, -1);
      assertNotNull(lens, "query failed, see log");
      lens.moreRows(Integer.MAX_VALUE);
      run.sql = executed.get();
      assertNotNull(run.sql, "no sql executed");

      for(int r = 1; r < lens.getRowCount(); r++) {
         int k = ((Number) lens.getObject(r, 0)).intValue();

         if(k >= 100) {
            run.filler++;
         }
         else {
            run.ids.add(k);
         }
      }

      Collections.sort(run.ids);
      String thread = Thread.currentThread().getName();
      run.warnings = appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .filter(e -> thread.equals(e.getThreadName()))
         .map(e -> e.getLoggerName() + ": " + e.getFormattedMessage())
         .toList();
      return run;
   }

   // as SQLQueryDialogService.setUpTableWithSQLString builds a sql-edited table
   private static SQLBoundTableAssembly newTable(Worksheet ws, String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);

      synchronized(usql) {
         usql.setParseSQL(true);
         usql.setSQLString(sql, true);
         usql.wait(10000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug78386");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection selection = new ColumnSelection();
      ColumnRef column = new ColumnRef(new AttributeRef(null, "k"));
      column.setDataType(XSchema.INTEGER);
      selection.addAttribute(column);
      table.setColumnSelection(selection, false);
      table.setColumnSelection(selection, true);
      ws.addAssembly(table);
      return table;
   }

   // VarSQL may put a space after a bound parameter, e.g. "= ? )"
   private static String norm(String sql) {
      return sql == null ? "" : sql.replaceAll("\\s+", " ").replace(" )", ")").trim()
         .toLowerCase(Locale.ROOT);
   }

   private static Logger rootLogger() {
      return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78386ws");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
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
   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         SQLBoundQuerySentinelWarningTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase(Locale.ROOT).startsWith("select"))
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

   private static Field vpmProcessorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   /**
    * Like the enterprise VpmUtil for a user the vpm applies to, without a vpm:
    * applyHiddenColumns returns a clone, so XSessionManager builds its normalizer on the clone.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user)
      {
         return query;
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user) {
         return user == null ? query : (XQuery) query.clone();
      }
   }

   private static final class Run {
      final List<Integer> ids = new ArrayList<>();
      int filler;
      String sql;
      boolean keptString;
      List<String> warnings;
   }
}
