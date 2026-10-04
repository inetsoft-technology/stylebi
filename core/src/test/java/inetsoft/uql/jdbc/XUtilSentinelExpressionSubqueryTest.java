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
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77706 (part 1), a parameter in a subquery inside an expression operand of a WHERE or
 * HAVING condition, e.g. a.id = 0 + (select ...), 'x' || (select ...) or
 * a.id = ANY (select ...). The parser keeps such a subquery as the text of the operand, so a
 * sentinel parameter in it was bound as its text, and an unset parameter in it dropped the
 * whole outer condition.
 * <p>
 * The queries run through XSessionManager and JDBCHandler on Derby. Table b has a row holding
 * the text NULL_VALUE, so a missed rewrite returns other rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XUtilSentinelExpressionSubqueryTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelExpressionSubqueryTest {
   private static final String DB = "memory:bug77706a";
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   private static final String UNSET = null;
   private static final String A = "select a.id from a where ";
   private static final String H = "select a.id from a group by a.id having ";

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private
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
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (id int)");
         stmt.executeUpdate("insert into a values (1), (3), (5)");
         // k is VARCHAR: a sentinel bound as text against a numeric column is a type error
         stmt.executeUpdate("create table b (id int, k varchar(20))");
         stmt.executeUpdate("insert into b values (1, '2'), (1, '2'), (3, '2'), (3, '3'), " +
                               "(1, null), (3, null), (5, ''), (5, 'null'), " +
                               "(5, 'NULL_VALUE'), (7, null)");
      }
   }

   // sql, value of p (null = unset), expected rows. b.k is null for b.id 1, 3, 7, '' and
   // 'null' for b.id 5, and the text NULL_VALUE for b.id 5
   static Stream<Arguments> rowCases() {
      return Stream.of(
         // D1, an arithmetic operand
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))", NULL_VALUE,
                      "[1]"),
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))",
                      EMPTY_STRING, "[5]"),
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))",
                      NULL_STRING, "[5]"),
         // 7 rows of b have a b.k, 6 have one that isn't the text NULL_VALUE
         Arguments.of(A + "a.id + 2 = 0 + (select count(*) from b where b.k <> $(p))",
                      NULL_VALUE, "[5]"),
         // D1, a string operand, correlated
         Arguments.of(A + "'x' || (select max(b.k) from b where b.id = a.id and b.k = $(p)) " +
                         "= 'x'", EMPTY_STRING, "[5]"),
         Arguments.of(A + "'x' || (select max(b.k) from b where b.id = a.id and b.k = $(p)) " +
                         "= 'xnull'", NULL_STRING, "[5]"),
         // a literal with a parenthesis and select in the subquery
         Arguments.of(A + "'x' || (select max(b.k) from b where b.id = a.id and " +
                         "b.k <> ')(select' and b.k = $(p)) = 'x'", EMPTY_STRING, "[5]"),
         // a subquery as a BETWEEN bound
         Arguments.of(A + "a.id between 0 + (select min(b.id) from b where b.k = $(p)) and 4",
                      NULL_VALUE, "[1, 3]"),
         // a nested IN subquery in the subquery
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.id in " +
                         "(select c.id from b c where c.k = $(p)))", NULL_VALUE, "[1]"),
         // D2, quantified comparisons
         Arguments.of(A + "a.id = ANY (select b.id from b where b.k = $(p))", NULL_VALUE,
                      "[1, 3]"),
         Arguments.of(A + "a.id = ANY (select b.id from b where b.k = $(p))", EMPTY_STRING,
                      "[5]"),
         Arguments.of(A + "a.id = SOME (select b.id from b where b.k = $(p))", NULL_VALUE,
                      "[1, 3]"),
         Arguments.of(A + "a.id < ALL (select b.id from b where b.k = $(p))", NULL_VALUE,
                      "[]"),
         Arguments.of(A + "a.id <> ALL (select b.id from b where b.k = $(p))", NULL_VALUE,
                      "[5]"),
         Arguments.of(A + "a.id > 0 and a.id = any (select b.id from b where b.k = $(p))",
                      NULL_STRING, "[5]"),
         // HAVING
         Arguments.of(H + "a.id = 0 + (select min(b.id) from b where b.k = $(p))", NULL_VALUE,
                      "[1]"),
         Arguments.of(H + "a.id = ANY (select b.id from b where b.k = $(p))", NULL_VALUE,
                      "[1, 3]"),
         // D3, unset. A single-row aggregate drops the condition of the parameter and keeps
         // the outer condition, also when the subquery has no other condition
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.id > 1 and " +
                         "b.k = $(p))", UNSET, "[3]"),
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))", UNSET,
                      "[1]"),
         Arguments.of(H + "a.id = 0 + (select min(b.id) from b where b.id > 1 and " +
                         "b.k = $(p))", UNSET, "[3]"),
         // a BETWEEN bound (a trinary condition): min(b.id) of b.id > 2 is 3
         Arguments.of(A + "a.id between 0 + (select min(b.id) from b where b.id > 2 and " +
                         "b.k = $(p)) and 5", UNSET, "[3, 5]"),
         // ANY as IN: the condition of the parameter is removed, and the whole condition when
         // the subquery has no other one
         Arguments.of(A + "a.id = ANY (select b.id from b where b.id > 1 and b.k = $(p))",
                      UNSET, "[3, 5]"),
         Arguments.of(A + "a.id = ANY (select b.id from b where b.k = $(p))", UNSET,
                      "[1, 3, 5]"),
         Arguments.of(A + "a.id > 1 and a.id = ANY (select b.id from b where b.id > 3 and " +
                         "b.k = $(p))", UNSET, "[5]"),
         Arguments.of(H + "a.id = ANY (select b.id from b where b.id > 1 and b.k = $(p))",
                      UNSET, "[3, 5]"),
         // a scalar subquery that isn't single-row keeps the condition, the parameter is NULL
         // (removing it would return more than one row: b.id > 4 is 5, 5, 5, 7)
         Arguments.of(A + "a.id = 0 + (select b.id from b where b.id > 4 and b.k = $(p))",
                      UNSET, "[]"),
         // an aggregate in an expression is not taken for single-row (a safe false negative):
         // count of b.k = NULL is 0, + 1
         Arguments.of(A + "a.id = 0 + (select count(*) + 1 from b where b.k = $(p))", UNSET,
                      "[1]"),
         Arguments.of(A + "a.id = 0 + (select coalesce(max(b.id), 1) from b where " +
                         "b.k = $(p))", UNSET, "[1]"),
         // an unset parameter outside the subquery drops the condition, as before
         Arguments.of(A + "a.id = $(p) + (select min(b.id) from b where b.k = '3')", UNSET,
                      "[1, 3, 5]"),
         // a plain value
         Arguments.of(A + "a.id = ANY (select b.id from b where b.k = $(p))", "3", "[3]"),
         Arguments.of(A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))", "3",
                      "[3]"));
   }

   // sql, value of p, value of q (null = unset), expected rows: a sentinel and a parameter
   // without a value in the same subquery, each checked against its IN form
   static Stream<Arguments> sentinelAndUnsetCases() {
      String min = "0 + (select min(b.id) from b where b.k = $(p) and b.id > $(q))";
      String any = "ANY (select b.id from b where b.k = $(p) and b.id > $(q))";
      String in = "in (select b.id from b where b.k = $(p) and b.id > $(q))";

      return Stream.of(
         // single-row: the condition of q is removed, the outer condition kept
         Arguments.of(A + "a.id = " + min, NULL_VALUE, UNSET, "[1]"),
         Arguments.of(A + "a.id = " + min, NULL_STRING, UNSET, "[5]"),
         Arguments.of(A + "a.id = " + min, NULL_VALUE, "1", "[3]"),
         Arguments.of(H + "a.id = " + min, NULL_VALUE, UNSET, "[1]"),
         Arguments.of(A + "a.id + 2 = 0 + (select count(*) from b where b.k <> $(p) and " +
                         "b.id > $(q))", NULL_VALUE, UNSET, "[5]"),
         // ANY as IN
         Arguments.of(A + "a.id = " + any, NULL_VALUE, UNSET, "[1, 3]"),
         Arguments.of(A + "a.id " + in, NULL_VALUE, UNSET, "[1, 3]"),
         Arguments.of(A + "a.id = " + any, EMPTY_STRING, UNSET, "[5]"),
         Arguments.of(H + "a.id = " + any, NULL_VALUE, UNSET, "[1, 3]"),
         Arguments.of(H + "a.id " + in, NULL_VALUE, UNSET, "[1, 3]"),
         // not single-row: the condition of q is kept, q is NULL
         Arguments.of(A + "a.id = 0 + (select b.id from b where b.k = $(p) and b.id > $(q))",
                      NULL_VALUE, UNSET, "[]"));
   }

   @ParameterizedTest
   @MethodSource("sentinelAndUnsetCases")
   void sentinelAndUnset(String sql, String p, String q, String expected) throws Exception {
      VariableTable vars = vars(p);

      if(q != null) {
         vars.put("q", q);
      }

      assertEquals(expected, rows(run(parsed(sql), vars)).toString(),
                   sql + " p=" + p + " q=" + q);
   }

   @ParameterizedTest
   @MethodSource("rowCases")
   void rowsThroughJDBCHandler(String sql, String p, String expected) throws Exception {
      assertEquals(expected, rows(run(parsed(sql), vars(p))).toString(), sql + " p=" + p);
   }

   // the same saved query run with a sentinel, a plain value, the sentinel again and unset:
   // no run keeps the rewrite of another, and the saved query is unchanged
   @Test
   void runSequence() throws Exception {
      String[] sqls = {
         A + "a.id = ANY (select b.id from b where b.k = $(p))",
         A + "a.id = 0 + (select min(b.id) from b where b.k = $(p))"
      };
      String[][] expected = {
         { "[1, 3]", "[3]", "[1, 3]", "[1, 3, 5]", "[5]" },
         { "[1]", "[3]", "[1]", "[1]", "[5]" }
      };
      String[] values = { NULL_VALUE, "3", NULL_VALUE, UNSET, EMPTY_STRING };

      for(int i = 0; i < sqls.length; i++) {
         UniformSQL usql = parsed(sqls[i]);
         String saved = generate(parse(sqls[i]));

         for(int j = 0; j < values.length; j++) {
            assertEquals(expected[i][j], rows(run(usql, vars(values[j]))).toString(),
                         sqls[i] + " p=" + values[j]);
            String now = usql.getSQLString().replaceAll("\\s+", " ").trim();
            assertTrue(now.contains("$(p)"), now);
            assertFalse(now.toUpperCase().contains("IS NULL"), now);
         }

         assertEquals(saved, generate(usql));
      }
   }

   // the condition of the validated query is replaced, the condition and operand of the
   // original query (which the validated query may share them with) are not changed
   @Test
   void originalConditionUnchanged() throws Exception {
      String sql = A + "a.id > 0 and a.id = 0 + (select min(b.id) from b where b.k = $(p))";
      UniformSQL usql = parse(sql);
      XBinaryCondition cond = findSubqueryCondition(usql.getWhere());
      XExpression operand = cond.getExpression2();
      String text = operand.toString();

      String validated = validate(usql, NULL_VALUE, false);
      assertTrue(validated.contains("b.k IS NULL"), validated);
      assertSame(operand, cond.getExpression2());
      assertEquals(text, operand.toString());

      UniformSQL usql2 = parse(sql);
      XBinaryCondition cond2 = findSubqueryCondition(usql2.getWhere());
      XExpression operand2 = cond2.getExpression2();
      String text2 = operand2.toString();
      validate(usql2, UNSET, false);
      assertEquals(text2, operand2.toString());
   }

   // the vpm path rewrites the sentinels only: an unset parameter is kept
   @Test
   void vpmPath() throws Exception {
      String[] sqls = {
         A + "a.id = ANY (select b.id from b where b.k = $(p))",
         A + "a.id = 0 + (select min(b.id) from b where b.id > 1 and b.k = $(p))"
      };

      for(String sql : sqls) {
         String validated = validate(parse(sql), NULL_VALUE, true);
         assertFalse(validated.contains("$(p)"), validated);
         assertTrue(validated.contains("b.k IS NULL"), validated);

         String original = generate(parse(sql));
         assertEquals(original, validate(parse(sql), UNSET, true));
      }
   }

   // a window aggregate returns a row for each row and is not single-row: the condition of
   // an unset parameter is kept (Derby can't run it, the validated sql is checked)
   @Test
   void windowAggregateNotSingleRow() throws Exception {
      String sql = A + "a.id = 0 + (select max(b.id) over (partition by b.k) from b where " +
         "b.id > 4 and b.k = $(p))";
      String original = generate(parse(sql));
      assertEquals(original, validate(parse(sql), UNSET, false));

      // a single aggregate drops it
      String sql2 = A + "a.id = 0 + (select max(b.id) from b where b.id > 4 and b.k = $(p))";
      String validated = validate(parse(sql2), UNSET, false);
      assertFalse(validated.contains("$(p)"), validated);
      assertTrue(validated.contains("0+(select max(b.id) from b where b.id > 4 )"),
                 validated);
   }

   // a subquery whose text isn't generated by the parser as written (here upper case) is left
   // as it is: a sentinel is bound as before, and an unset parameter drops the condition
   @Test
   void failedReparseUnchanged() throws Exception {
      UniformSQL usql = parse("select a.id from a where a.id > 0");
      String text = "0+(SELECT min(b.id) FROM b WHERE b.k = $(p))";
      usql.setWhere(new XBinaryCondition(new XExpression("a.id", XExpression.FIELD),
                                         new XExpression(text, XExpression.EXPRESSION), "="));
      String original = generate(usql);
      assertTrue(original.contains(text), original);
      assertEquals(original, validate(usql, NULL_VALUE, false));

      usql = parse("select a.id from a where a.id > 0");
      usql.setWhere(new XBinaryCondition(new XExpression("a.id", XExpression.FIELD),
                                         new XExpression(text, XExpression.EXPRESSION), "="));
      String validated = validate(usql, UNSET, false);
      assertFalse(validated.toLowerCase().contains("where"), validated);
   }

   // an operand without a parameter in a subquery is not touched
   @Test
   void noParameterUnchanged() throws Exception {
      String[] sqls = {
         A + "a.id = ANY (select b.id from b)",
         A + "$(p) = any (select b.id from b)",
         A + "a.id = 0 + (select min(b.id) from b where b.k = '3')"
      };

      for(String sql : sqls) {
         assertEquals(generate(parse(sql)), validate(parse(sql), "3", false), sql);
      }
   }

   // the condition with the subquery operand
   private static XBinaryCondition findSubqueryCondition(XNode node) {
      if(node instanceof XBinaryCondition &&
         ((XBinaryCondition) node).getExpression2().toString().contains("(select"))
      {
         return (XBinaryCondition) node;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         XBinaryCondition cond = findSubqueryCondition(node.getChild(i));

         if(cond != null) {
            return cond;
         }
      }

      return null;
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:" + DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static VariableTable vars(String p) {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      return vars;
   }

   private static String validate(UniformSQL usql, String p, boolean forVpm) {
      XUtil.validateConditions(null, usql, vars(p), true, forVpm);
      return generate(usql);
   }

   private static String generate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static List<String> rows(TableLens table) {
      List<String> rows = new ArrayList<>();

      for(int r = 1; table.moreRows(r); r++) {
         rows.add(String.valueOf(table.getObject(r, 0)));
      }

      Collections.sort(rows);
      return rows;
   }

   // a new query for every case, the rewrite changes the parsed query in place
   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return sql;
   }

   // a query of a data source as it's saved: parsed, with its sql string
   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource());
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      return usql;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77706a");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   // run a query through XSessionManager and JDBCHandler, with the cache normalizer
   private static TableLens run(UniformSQL usql, VariableTable vars) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77706a");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);

      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery q = inv.getArgument(1);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(q.getDataSource(), inv.getArgument(2));
            return handler.execute(q, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);

      try {
         TableLens table = session.getXNodeTableLens(query, vars, null, null, null, -1);
         assertNotNull(table, "query failed, see log");
         table.moreRows(Integer.MAX_VALUE);
         return table;
      }
      finally {
         session.tearDown();
      }
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
