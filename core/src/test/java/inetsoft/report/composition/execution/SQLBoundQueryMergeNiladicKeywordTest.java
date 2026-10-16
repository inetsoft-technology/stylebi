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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
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
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78489. A niladic keyword function written unquoted in a SQL edited worksheet table
 * ({@code current_date as d}) is parsed as an expression, but the merge
 * (SQLBoundQuery.merge) rebuilds the select list from the worksheet columns and took the
 * expression mark from the worksheet column only. The column carries it
 * (queryExpressionField) only when the query dialog built it, so an imported or older
 * worksheet merged the keyword as a column, and the helper quoted it as an identifier:
 * {@code "current_date" as "d"}, which is no column of the table.
 * <p>
 * The worksheet columns are built as an imported worksheet has them: named as the query
 * dialog names them, without queryExpressionField. The merged query is executed by
 * JDBCHandler on Derby, as XSessionManager does, and generated for postgresql, mysql and
 * sql server.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundQueryMergeNiladicKeywordTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundQueryMergeNiladicKeywordTest {
   private static final String DB = "memory:bug78489merge";

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
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table qt");
         }
         catch(Exception ignore) {
            // first run
         }

         // columns named as the keywords, written as quoted identifiers
         stmt.executeUpdate("create table qt (amt int, \"current_date\" int, \"user\" int)");
         stmt.executeUpdate("insert into qt values (10, 100, 1000), (20, 200, 2000)");
      }
   }

   // ---- select list (mergeColumn) ----

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = {
      "select o.amt, current_date as d from qt o",
      "select o.amt, current_date from qt o",
      "select current_date from qt",
      "select current_date as d from qt",
      "select o.amt, current_timestamp as ts, current_user as cu from qt o",
      "select o.amt, user as u, session_user from qt o"
   })
   void keywordInSelect(String sql) throws Exception {
      Result r = run(sql, null);
      assertKeywordsUnquoted(r);
      assertEquals(2, r.rows.size(), r.msg());

      for(List<Object> row : r.rows) {
         assertFalse(row.contains(null), r.msg());
      }
   }

   // the merge writes into the table's own query, a second run merges the merged query
   @Test
   void secondRunKeepsKeywordUnquoted() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, "select o.amt, current_date as d from qt o",
                                             dataSource());

      for(int i = 0; i < 2; i++) {
         VariableTable vars = new VariableTable();
         SQLBoundQuery query = new SQLBoundQuery(
            AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
         query.merge(vars);
         executed.set(null);
         List<List<Object>> rows = execute(query.getQuery(), vars, new ArrayList<>());
         String sql = normalize(executed.get());
         assertEquals(2, rows.size(), "run " + i + ": " + sql);
         assertFalse(sql.contains("\"current_date\""), "run " + i + ": " + sql);
      }
   }

   // ---- worksheet sort, group, aggregate and condition on the keyword column ----

   @Test
   void worksheetSortOnKeyword() throws Exception {
      Result r = run("select o.amt, current_date as d from qt o",
                     t -> sort(t, "d", XConstants.SORT_DESC));
      assertKeywordsUnquoted(r);
      assertEquals(2, r.rows.size(), r.msg());
   }

   @Test
   void worksheetGroupOnKeyword() throws Exception {
      Result r = run("select o.amt, current_date as d from qt o",
                     t -> aggregate(t, "d", "amt"));
      assertKeywordsUnquoted(r);
      assertNotNull(r.rows, r.msg());
   }

   @Test
   void worksheetSumByKeyword() throws Exception {
      Result r = run("select o.amt, current_date as d from qt o",
                     t -> aggregate(t, "amt", "d"));
      assertKeywordsUnquoted(r);
      assertNotNull(r.rows, r.msg());
   }

   @Test
   void worksheetConditionOnKeyword() throws Exception {
      Result r = run("select o.amt, current_date as d from qt o",
                     t -> condition(t, "d", XSchema.DATE, java.sql.Date.valueOf("2000-01-01")));
      assertKeywordsUnquoted(r);
      assertEquals(2, r.rows.size(), r.msg());
   }

   // a sort on a keyword column that is hidden in the worksheet
   @Test
   void worksheetSortOnHiddenKeyword() throws Exception {
      Result r = run("select o.amt, current_date as d from qt o", t -> {
         sort(t, "d", XConstants.SORT_DESC);
         ((ColumnRef) ref(t, "d")).setVisible(false);
      });
      assertKeywordsUnquoted(r);
      assertEquals(2, r.rows.size(), r.msg());
   }

   // ---- a keyword written as a quoted identifier is a column, it stays quoted ----

   @Test
   void quotedKeywordColumnStaysQuoted() throws Exception {
      Result r = run("select q.\"current_date\", q.amt from qt q", null);
      assertEquals(List.of(List.of(100, 10), List.of(200, 20)), sorted(r), r.msg());
      assertTrue(r.sql.contains("\"current_date\""), r.msg());

      r = run("select \"user\", amt from qt", null);
      assertEquals(List.of(List.of(1000, 10), List.of(2000, 20)), sorted(r), r.msg());
      assertTrue(r.sql.contains("\"user\""), r.msg());

      r = run("select q.\"current_date\", q.amt from qt q",
              t -> sort(t, "current_date", XConstants.SORT_DESC));
      assertNotNull(r.rows, r.msg());
      assertEquals(200, ((Number) r.rows.get(0).get(0)).intValue(), r.msg());
      assertEquals(100, ((Number) r.rows.get(1).get(0)).intValue(), r.msg());
   }

   // the function and the column of the same name in one select
   @Test
   void keywordAndQuotedColumnTogether() throws Exception {
      Result r = run("select current_date as cd, q.\"current_date\" as qc from qt q",
                     t -> sort(t, "qc", XConstants.SORT_DESC));
      assertNotNull(r.rows, r.msg());
      assertTrue(r.sql.contains("current_date as cd"), r.msg());
      assertTrue(r.sql.contains("q.\"current_date\""), r.msg());
      assertEquals(2, r.rows.size(), r.msg());
      int qc = r.headers.indexOf("qc");
      assertTrue(qc >= 0, r.headers + " " + r.msg());
      assertEquals(200, ((Number) r.rows.get(0).get(qc)).intValue(), r.msg());
      assertEquals(100, ((Number) r.rows.get(1).get(qc)).intValue(), r.msg());
   }

   // ---- the merged sql of other helpers ----

   @Test
   void dialectMergedSql() throws Exception {
      String sql = "select o.amt, current_date as d from qt o";
      String[][] cases = {
         { "postgresql", sql, null, null },
         { "postgresql", "select current_date from qt", null, null },
         { "postgresql", "select o.amt, current_timestamp as ts, current_user as cu, " +
           "localtimestamp as lt from qt o", null, null },
         { "postgresql", sql, "sort", "d" },
         { "postgresql", sql, "group", "d" },
         { "postgresql", sql, "cond", "d" },
         { "mysql", sql, null, null },
         { "mysql", sql, "sort", "d" },
         { "sql server", sql, null, null },
         { "sql server", sql, "sort", "d" },
         { "sql server", "select o.amt, current_user as cu from qt o", null, null },
      };

      for(String[] c : cases) {
         String out = sqlFor(c[0], c[1], c[2], c[3]);
         String msg = c[0] + " " + c[2] + ": " + out;
         assertTrue(out.matches("(?i).*\\bcurrent_\\w+.*"), msg);
         assertFalse(out.matches("(?is).*[\"`\\[](current_\\w+|localtimestamp)[\"`\\]].*"), msg);
      }

      // the user quoted keyword column stays quoted
      String out = sqlFor("postgresql", "select q.\"current_date\", q.amt from qt q", null, null);
      assertTrue(out.contains("\"current_date\""), out);
      out = sqlFor("mysql", "select q.`current_date`, q.amt from qt q", null, null);
      assertTrue(out.contains("`current_date`"), out);
   }

   private static void assertKeywordsUnquoted(Result r) {
      assertNotNull(r.sql, r.msg());
      assertFalse(r.sql.matches("(?is).*\"(current_\\w+|user|session_user)\".*"), r.msg());
      assertNull(r.error, r.msg());
   }

   private static String sqlFor(String db, String sql, String op, String column)
      throws Exception
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78489" + db);
      ds.setRuntimeProductName(db);

      switch(db) {
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost/db");
         ds.setProductVersion("10.0");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/db");
      }
      default -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=db0");
      }
      }

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, ds);

      if("sort".equals(op)) {
         sort(table, column, XConstants.SORT_DESC);
      }
      else if("cond".equals(op)) {
         condition(table, column, XSchema.DATE, java.sql.Date.valueOf("2000-01-01"));
      }
      else if("group".equals(op)) {
         aggregate(table, column, "amt");
      }

      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(new VariableTable());
      UniformSQL copy = (UniformSQL) query.getQuery().getSQLDefinition().clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString()).replaceAll("dataselcond[0-9_]+", "X");
   }

   // the integer rows, sorted
   private static List<List<Integer>> sorted(Result r) {
      assertNotNull(r.rows, r.msg());
      List<List<Integer>> rows = new ArrayList<>();

      for(List<Object> row : r.rows) {
         rows.add(row.stream().map(v -> ((Number) v).intValue()).toList());
      }

      rows.sort(Comparator.comparing(Object::toString));
      return rows;
   }

   // ---- harness, as SQLBoundQueryMergeQuotedColumnTest ----

   private record Result(String sql, List<String> headers, List<List<Object>> rows,
                         String error)
   {
      String msg() {
         return sql + (error != null ? " error: " + error : "");
      }
   }

   private static Result run(String sql, Consumer<SQLBoundTableAssembly> customize)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, dataSource());

      if(customize != null) {
         customize.accept(table);
      }

      VariableTable vars = new VariableTable();
      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(vars);
      executed.set(null);
      List<String> headers = new ArrayList<>();

      try {
         List<List<Object>> rows = execute(query.getQuery(), vars, headers);
         return new Result(normalize(executed.get()), headers, rows, null);
      }
      catch(Throwable ex) {
         return new Result(normalize(executed.get()), headers, null, ex.toString());
      }
   }

   private static String normalize(String sql) {
      return sql == null ? null : sql.trim().replaceAll("\\s+", " ");
   }

   // the worksheet column of a name, postgresql stores some names with their quotes
   private static DataRef ref(SQLBoundTableAssembly table, String name) {
      ColumnSelection cols = table.getColumnSelection(false);
      DataRef ref = cols.getAttribute(name);

      for(int i = 0; ref == null && i < cols.getAttributeCount(); i++) {
         if(cols.getAttribute(i).getName().replace("\"", "").equals(name)) {
            ref = cols.getAttribute(i);
         }
      }

      assertNotNull(ref, name);
      return ref;
   }

   private static void sort(SQLBoundTableAssembly table, String name, int order) {
      SortRef sort = new SortRef(ref(table, name));
      sort.setOrder(order);
      SortInfo info = new SortInfo();
      info.addSort(sort);
      table.setSortInfo(info);
   }

   private static void condition(SQLBoundTableAssembly table, String name, String type,
                                 Object value)
   {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(type);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref(table, name), cond, 0));
      table.setPreConditionList(list);
   }

   private static void aggregate(SQLBoundTableAssembly table, String group, String sum) {
      AggregateInfo info = new AggregateInfo();

      if(group != null) {
         info.addGroup(new GroupRef(ref(table, group)));
      }

      info.addAggregate(new AggregateRef(ref(table, sum), AggregateFormula.SUM));
      table.setAggregateInfo(info);
      table.setAggregate(true);
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sqlText,
                                                 JDBCDataSource ds) throws Exception
   {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, sqlText, 0, 4000L);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sqlText);
      assertFalse(usql.isLossy(), sqlText);
      usql.setSQLString(sqlText, false);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug78489");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      // the worksheet columns are named as QueryManagerService.getColumnSelection names them,
      // but without queryExpressionField, as an imported or older worksheet has them
      ColumnSelection columns = new ColumnSelection();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String alias = selection.getAlias(i);
         String name = alias != null && !alias.isEmpty() ? alias :
            path.substring(path.lastIndexOf('.') + 1);
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XUtil.isNiladicKeywordFunction(path) ? XSchema.DATE :
                               XSchema.INTEGER);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }

   // the merged query run as AssetQuery runs it, through XSessionManager and JDBCHandler
   private static List<List<Object>> execute(JDBCQuery query, VariableTable vars,
                                             List<String> headers) throws Exception
   {
      XRepository dataService = mock(XRepository.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            JDBCHandler handler = new JDBCHandler();
            XQuery xquery = inv.getArgument(1);
            handler.connect(xquery.getDataSource(), inv.getArgument(2));
            return handler.execute(xquery, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);

      try {
         TableLens lens = session.getXNodeTableLens(query, vars, null, null, null, -1);
         assertNotNull(lens, "query failed: " + executed.get());
         lens.moreRows(Integer.MAX_VALUE);
         List<List<Object>> rows = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            headers.add(String.valueOf(lens.getObject(0, c)));
         }

         for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               row.add(lens.getObject(r, c));
            }

            rows.add(row);
         }

         return rows;
      }
      finally {
         session.tearDown();
      }
   }

   // JDBCHandler generates the sql on a clone of the query, record the sql that runs
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         SQLBoundQueryMergeNiladicKeywordTest.class.getClassLoader(), new Class<?>[] { type },
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

   private static final ThreadLocal<String> executed = new ThreadLocal<>();

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78489merge");
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
}
