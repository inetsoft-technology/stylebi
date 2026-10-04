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

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
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
 * Bug #77708. A sql-edited SQL bound worksheet table that is parsed and not lossy, but can't
 * be merged (GROUP BY, an aggregate, DISTINCT), keeps its sql string, and
 * XUtil.validateConditions returns early for a query with a sql string. A sentinel parameter
 * (NULL_VALUE, EMPTY_STRING, NULL_STRING) was then bound as its text. JDBCHandler now
 * rewrites the sentinels on a copy without the string, used only if the sql changes.
 *
 * Each table is built as SQLQueryDialogService builds it and run through SQLBoundQuery.merge
 * and the real XSessionManager.getXNodeTableLens (only the data service is mocked, to call
 * the real JDBCHandler) on Derby. Without a user the normalizer clears the sql string of the
 * common shapes, so these tests use the shapes it leaves alone (a positional ORDER BY,
 * DISTINCT). With a user the test VpmProcessor returns a clone from applyHiddenColumns, as
 * the enterprise VpmUtil does, so the normalizer clears the clone's string and every
 * non-mergeable shape keeps its string.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundQueryKeptSqlSentinelTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundQueryKeptSqlSentinelTest {
   private static final String DB = "memory:bug77708";
   private static final String ES = XConstants.CONDITION_EMPTY_STRING;
   private static final String NV = XConstants.CONDITION_NULL_VALUE;
   private static final String NS = XConstants.CONDITION_NULL_STRING;
   private static final Principal USER = new XPrincipal(new IdentityID("bug77708", "host-org"));
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
   private static Object oldVpmProcessor;

   private static final String ORDER_BY_1 =
      "select a.k from a where a.name = $(p) group by a.k order by 1";

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
         for(String table : new String[] { "a", "t3" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'null'), (6, 1, null), " +
                               "(null, 3, ''), (null, 3, ''), (9, 2, '')");
         stmt.executeUpdate("create table t3 (x int)");
         stmt.executeUpdate("insert into t3 values (1), (2), (3)");
      }
   }

   @AfterAll
   static void restoreVpmProcessor() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
   }

   // XSessionManager.finalize() calls tearDown(), which closes the data service
   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
   }

   // the shape demonstrated on the worksheet path: the positional ORDER BY keeps the
   // normalizer from clearing the string
   @Test
   void positionalOrderByRewritesSentinels() throws Exception {
      assertRows(ORDER_BY_1, ES, null, "1", "2", "3");
      assertRows(ORDER_BY_1, NV, null, "1", "2");
      assertRows(ORDER_BY_1, NS, null, "1", "2");
      assertRows(ORDER_BY_1, new Object[] { ES }, null, "1", "2", "3");
   }

   @Test
   void aggregateAndDistinctRewriteSentinels() throws Exception {
      String count = "select count(*) c from a where a.name = $(p) order by 1";
      assertRows(count, ES, null, "4");
      assertRows(count, NV, null, "2");
      assertRows(count, NS, null, "2");

      String distinct =
         "select distinct a.k, count(*) c from a where a.name = $(p) group by a.k";
      assertRows(distinct, ES, null, "1/1", "2/1", "3/2");
      assertRows(distinct, NV, null, "1/1", "2/1");
   }

   // a sentinel in HAVING, and NOT / <> on a kept string
   @Test
   void havingAndNegationRewriteSentinels() throws Exception {
      assertRows("select a.k from a group by a.k having min(a.name) = $(p) order by 1",
                 ES, null, "1", "2", "3");
      assertRows("select a.k from a where a.name <> $(p) group by a.k order by 1",
                 NV, null, "1", "2", "3");
   }

   // the secured enterprise state: the normalizer is built on a clone, so the sorted column
   // map is set while the executed query keeps its string. The common GROUP BY and aggregate
   // shapes are rewritten, and the select order of the headers and data is kept.
   @Test
   void vpmCloneStateRewritesSentinelsInSelectOrder() throws Exception {
      assertRows("select a.k from a where a.name = $(p) group by a.k", ES, USER,
                 "1", "2", "3");
      assertRows("select count(*) c from a where a.name = $(p)", NV, USER, "2");

      String sql = "select count(*) c, a.k from a where a.name = $(p) group by a.k";
      Run run = run(sql, ES, USER);
      assertTrue(run.keptString, "the merge cleared the sql string, the gate isn't reached");
      // the sorted column map is set, so the copy is generated sorted and the order restored
      assertTrue(norm(run.sql).startsWith("select a.k, count(*)"), run.sql);
      assertHeaders(run.table, "C", "K");
      assertEquals(List.of("1/1", "1/2", "2/3"), rows(run.table));
      assertFalse(run.sql.contains("EMPTY_STRING"), run.sql);
   }

   // without a sorted column map the copy must not be generated sorted
   @Test
   void positionalOrderByKeepsSelectOrder() throws Exception {
      Run run = run("select count(*) c, a.k from a where a.name = $(p) group by a.k order by 2",
                    ES, null);
      assertTrue(run.keptString);
      assertTrue(norm(run.sql).startsWith("select count(*)"), run.sql);
      assertHeaders(run.table, "C", "K");
      assertEquals(List.of("1/1", "1/2", "2/3"), rows(run.table));
   }

   // the diagnosis path: the merged query run by JDBCHandler without a cache visitor
   @Test
   void directJdbcHandlerRewritesSentinels() throws Exception {
      String sql = "select a.k from a where a.name = $(p) group by a.k";
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, "k");
      VariableTable vars = new VariableTable();
      vars.put("p", ES);
      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(vars);
      JDBCQuery jquery = query.getQuery();
      assertTrue(((UniformSQL) jquery.getSQLDefinition()).hasSQLString());

      JDBCHandler handler = new JDBCHandler();
      handler.connect(jquery.getDataSource(), vars);
      XNode node = handler.execute(jquery, vars, null, null);
      assertEquals(List.of("1", "2", "3"), rows(new XNodeTableLens(node)));
      // the rewrite is on a copy, the table's query keeps its string
      assertEquals(sql, ((UniformSQL) jquery.getSQLDefinition()).getSQLString());
   }

   // a query without a sentinel runs the user's exact sql: a value, an unset parameter (the
   // condition is kept and binds NULL, as before), and the shapes the gate leaves alone
   @Test
   void withoutSentinelKeptSqlRunsAsWritten() throws Exception {
      String comment = "select a.k /* note */ from a where a.name = $(p) group by a.k order by 1";

      assertExact(ORDER_BY_1, "n1", null, "1");
      assertExact(ORDER_BY_1, null, null);
      assertExact(comment, "n1", null, "1");
      assertExact("select a.k from a where a.name = $(p) group by a.k", "n1", USER, "1");
      assertExact("select a.k from a where a.name = $(p) group by a.k", null, USER);
      assertExact("select count(*) c from a where a.name = $(p) order by 1", null, null, "0");
   }

   // shapes that keep the sentinel bound as text, as before: an optimizer hint or a MySQL
   // executable comment would be dropped by the generated sql, an embedded parameter isn't
   // safe to regenerate, and a lossy or unparsed string isn't described by the structure
   @Test
   void excludedShapesKeepSqlString() throws Exception {
      assertExact("select /*+ hint */ a.k from a where a.name = $(p) group by a.k order by 1",
                  ES, null);
      assertExact("select /*!1 */ a.k from a where a.name = $(p) group by a.k order by 1",
                  ES, null);
      assertExact("select a.k from a --+ hint\nwhere a.name = $(p) group by a.k order by 1",
                  ES, null);
      assertExact("select a.k from a where a.name = $(p) and a.k > $(@q) group by a.k " +
                  "order by 1", ES, null);
      assertExact("select a.k from a where a.name = $(p) group by a.k order by 1 " +
                  "fetch first 2 rows only", ES, null);

      Run run = run(ORDER_BY_1, ES, null, false);
      assertEquals(norm(ORDER_BY_1.replace("$(p)", "?")), norm(run.sql));
      assertEquals(List.of(), rows(run.table));
   }

   // the copy is generated from the structure, so a comment is dropped only on a gated run
   @Test
   void commentIsDroppedOnlyOnRewrittenRun() throws Exception {
      String sql = "select a.k /* note */ from a where a.name = $(p) group by a.k order by 1";
      Run run = run(sql, ES, null);
      assertEquals(List.of("1", "2", "3"), rows(run.table));
      assertFalse(run.sql.contains("note"), run.sql);

      run = run(sql, "n1", null);
      assertTrue(run.sql.contains("/* note */"), run.sql);
   }

   // the rewrite is on JDBCHandler's copy, so nothing sticks to the table across runs
   @Test
   void runSequenceOnTheSameTable() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, ORDER_BY_1, "k");
      Object[] values = { ES, "n1", ES, null, NV };
      List<List<String>> expected = List.of(List.of("1", "2", "3"), List.of("1"),
                                            List.of("1", "2", "3"), List.of(), List.of("1", "2"));

      for(int i = 0; i < values.length; i++) {
         Run run = run(ws, table, values[i], null);
         assertTrue(run.keptString);
         assertEquals(expected.get(i), rows(run.table), "run " + i + " p=" + values[i]);
      }
   }

   // a sentinel and an unset parameter in the same query: the sentinel is rewritten, and the
   // condition with the unset parameter is kept and binds NULL, as for the sql string, so no
   // row matches. Only the sentinel rewrite runs on the copy, a full validateConditions would
   // drop the unset condition (rows 1, 2, 3).
   @Test
   void unsetParameterNextToSentinelStillBindsNull() throws Exception {
      Run run = run("select a.k from a where a.name = $(p) and a.id = $(u) group by a.k " +
                    "order by 1", ES, null);
      assertTrue(run.keptString);
      assertEquals(List.of(), rows(run.table));
      assertTrue(norm(run.sql).contains("a.name = ''"), run.sql);
      assertTrue(norm(run.sql).contains("a.id = ?"), run.sql);
   }

   // the gate finds a quoted '$(p)' whatever operand shapes the rewrite handles, so the run
   // does what the sentinel rewrite does on the structure: the kept sql while the rewrite
   // leaves the quoted operand alone, the rewritten sql once it handles it (Bug #77709)
   @Test
   void quotedSentinelFollowsTheRewrite() throws Exception {
      String sql = "select a.k from a where a.name = '$(p)' group by a.k order by 1";
      VariableTable vars = new VariableTable();
      vars.put("p", ES);
      assertTrue(XUtil.hasSentinelParameter(sql, vars));

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, "k");
      UniformSQL structure = (UniformSQL)
         ((SQLBoundTableAssemblyInfo) table.getTableInfo()).getQuery().getSQLDefinition();
      structure = structure.clone();
      structure.clearSQLString();
      boolean rewritable = XUtil.rewriteSentinels(structure, vars);

      Run run = run(ws, table, ES, null);
      assertTrue(run.keptString);

      if(rewritable) {
         assertTrue(norm(run.sql).contains("a.name = ''"), run.sql);
         assertEquals(List.of("1", "2", "3"), rows(run.table));
      }
      else {
         assertEquals(norm(sql.replace("$(p)", ES)), norm(run.sql));
         assertEquals(List.of(), rows(run.table));
      }
   }

   // a failure inside the rewrite falls back to the kept sql string, as before
   @Test
   void rewriteFailureSendsKeptSql() throws Exception {
      String sql = "select a.k from a where a.name = $(p) group by a.k";
      UniformSQL usql = new FailingUniformSQL();
      usql.setDataSource(dataSource());

      synchronized(usql) {
         usql.setParseSQL(true);
         usql.setSQLString(sql, true);
         usql.wait(10000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertTrue(usql.hasSQLString());
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77708");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      VariableTable vars = new VariableTable();
      vars.put("p", ES);

      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, null, null);
      assertEquals(norm(bound(sql)), norm(executed.get()));
      assertEquals(List.of(), rows(new XNodeTableLens(node)));
   }

   // the sentinel walk doesn't descend into a WHERE condition subquery yet (Bug #77706), so
   // the rewrite changes nothing and the kept string is sent as written
   @Test
   void conditionSubqueryIsNotRewritten() throws Exception {
      assertExact("select a.k from a where a.k in (select b.k from a b where b.name = $(p)) " +
                  "group by a.k order by 1", ES, null);
   }

   // the tables of the user's sql are kept on a rewritten run, remove.useless.joinTable
   // doesn't drop the unused cross joined table (the sum 9 times 3 rows)
   @Test
   void rewrittenRunKeepsUnusedTables() throws Exception {
      String old = SreeEnv.getProperty("remove.useless.joinTable");
      SreeEnv.setProperty("remove.useless.joinTable", "true");

      try {
         assertRows("select sum(a.k) c from a, t3 where a.name = $(p) order by 1", ES, null,
                    "27");
      }
      finally {
         if(old == null) {
            SreeEnv.remove("remove.useless.joinTable");
         }
         else {
            SreeEnv.setProperty("remove.useless.joinTable", old);
         }
      }
   }

   private static void assertRows(String sql, Object value, Principal user, String... expected)
      throws Exception
   {
      Run run = run(sql, value, user);
      assertTrue(run.keptString, "the merge cleared the sql string, the gate isn't reached");
      assertEquals(List.of(expected), rows(run.table), sql + " p=" + Arrays.deepToString(
         new Object[] { value }));
   }

   // the executed sql is the kept string with its parameter bound, and the rows are those
   private static void assertExact(String sql, Object value, Principal user, String... expected)
      throws Exception
   {
      Run run = run(sql, value, user);
      assertTrue(run.keptString);
      assertEquals(norm(bound(sql)), norm(run.sql), "the kept sql string was not sent");
      assertEquals(List.of(expected), rows(run.table), sql + " p=" + value);
   }

   // VarSQL binds $(p) as a parameter, and embeds $(@q)
   private static String bound(String sql) {
      return sql.replace("$(p)", "?").replace("$(@q)", "0");
   }

   private static Run run(String sql, Object value, Principal user) throws Exception {
      return run(sql, value, user, true);
   }

   private static Run run(String sql, Object value, Principal user, boolean parse)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      String[] columns = sql.contains("count(*) c, a.k") ? new String[] { "c", "k" } :
         sql.contains("a.k, count(*) c") ? new String[] { "k", "c" } :
         sql.startsWith("select count(*)") || sql.startsWith("select sum(") ?
         new String[] { "c" } : new String[] { "k" };
      return run(ws, newTable(ws, sql, parse, columns), value, user);
   }

   private static Run run(Worksheet ws, SQLBoundTableAssembly table, Object value,
                          Principal user)
      throws Exception
   {
      VariableTable vars = new VariableTable();
      vars.put("q", "0");

      if(value != null) {
         vars.put("p", value);
      }

      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(vars);
      JDBCQuery jquery = query.getQuery();
      Run run = new Run();
      run.keptString = ((UniformSQL) jquery.getSQLDefinition()).hasSQLString();

      XDataService dataService = mock(XDataService.class);
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

      run.table = session.getXNodeTableLens(jquery, vars, user, null, null, -1);
      run.sql = executed.get();
      assertNotNull(run.table, "query failed, see log");
      assertNotNull(run.sql, "no sql executed");
      return run;
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sql, String... columns)
      throws Exception
   {
      return newTable(ws, sql, true, columns);
   }

   // as SQLQueryDialogService.setUpTableWithSQLString builds a sql-edited table
   private static SQLBoundTableAssembly newTable(Worksheet ws, String sql, boolean parse,
                                                 String... columns)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);

      if(parse) {
         synchronized(usql) {
            usql.setParseSQL(true);
            usql.setSQLString(sql, true);
            usql.wait(10000);
         }
      }
      else {
         usql.setParseSQL(false);
         usql.setSQLString(sql, false);
      }

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77708");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection selection = new ColumnSelection();

      for(String name : columns) {
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XSchema.INTEGER);
         selection.addAttribute(column);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection(selection, true);
      ws.addAssembly(table);
      return table;
   }

   private static List<String> rows(TableLens lens) {
      lens.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         StringBuilder row = new StringBuilder();

         for(int c = 0; c < lens.getColCount(); c++) {
            row.append(c > 0 ? "/" : "").append(lens.getObject(r, c));
         }

         rows.add(row.toString());
      }

      Collections.sort(rows);
      return rows;
   }

   private static void assertHeaders(TableLens table, String... names) {
      assertEquals(names.length, table.getColCount());

      for(int i = 0; i < names.length; i++) {
         String header = String.valueOf(table.getObject(0, i)).toUpperCase(Locale.ROOT);
         header = header.substring(header.lastIndexOf('.') + 1);
         assertEquals(names[i], header, "header " + i);
      }
   }

   // VarSQL may put a space after a bound parameter, e.g. "= ? )"
   private static String norm(String sql) {
      return sql == null ? "" : sql.replaceAll("\\s+", " ").replace(" )", ")").trim()
         .toLowerCase(Locale.ROOT);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77708");
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
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         SQLBoundQueryKeptSqlSentinelTest.class.getClassLoader(), new Class<?>[] { type },
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

   // a query whose sql can't be generated from its structure, so the rewrite on the copy fails
   private static final class FailingUniformSQL extends UniformSQL {
      @Override
      public synchronized String getSQLString() {
         if(!hasSQLString()) {
            throw new IllegalStateException("the sql can't be generated");
         }

         return super.getSQLString();
      }
   }

   private static final class Run {
      TableLens table;
      String sql;
      boolean keptString;
   }
}
