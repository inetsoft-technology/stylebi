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

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.XNode;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77571, a NULL_VALUE parameter replaces the comparison with IS NULL and keeps the
 * negation of the condition. &lt;&gt; and != carry their negation in the operator, which was
 * dropped, so a.id &lt;&gt; $(p) became a.id IS NULL, and not (a.id &lt;&gt; $(p)) became
 * not (a.id IS NULL). A literal a.id &lt;&gt; 'NULL_VALUE' was generated as a.id IS NULL too.
 * The other operators (&gt;, &lt;, &gt;=, &lt;=, LIKE) still mean IS NULL.
 * <p>
 * Every case parses a new query, the rewrite changes the tree in place (#77606).
 * The rows of IS [NOT] UNKNOWN shapes are compared on PostgreSQL only, Derby has no IS
 * UNKNOWN, in {@link #samePostgresRows}, which runs when the system property
 * sentinel.pg.url names a database, e.g.
 * -Dsentinel.pg.url=jdbc:postgresql://localhost:5432/postgres -Dsentinel.pg.password=..
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelNotEqualTest {
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String SELECT = "select a.id, a.k, a.name from a ";
   private static final String GROUP = "select a.k, count(*) from a group by a.k ";
   // SQLHelper and OracleSQLHelper, with and without ANSI joins
   private static final String[] TYPES = { "default", "derby", "derby-ansi", "oracle",
                                           "oracle-ansi" };

   // sql with $(p), parameter p, hand-written sql with the expected rows
   static Stream<Arguments> paramCases() {
      return Stream.of(
         Arguments.of(SELECT + "where a.k = 1 and a.id <> $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id != $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id <> $(p))", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id != $(p))", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 2 or a.id <> $(p)", NULL_VALUE,
                      SELECT + "where a.k = 2 or a.id IS NOT NULL"),
         Arguments.of(SELECT + "where not (a.k = 2 or a.id <> $(p))", NULL_VALUE,
                      SELECT + "where not (a.k = 2 or a.id IS NOT NULL)"),
         Arguments.of(GROUP + "having count(*) > 1 and min(a.name) <> $(p)", NULL_VALUE,
                      GROUP + "having count(*) > 1 and min(a.name) IS NOT NULL"),
         Arguments.of(GROUP + "having count(*) > 1 and not (min(a.name) != $(p))", NULL_VALUE,
                      GROUP + "having count(*) > 1 and min(a.name) IS NULL"),
         Arguments.of("select t.id from (select a.id, a.k from a where a.id <> $(p)) t " +
                         "where t.k = 1", NULL_VALUE,
                      "select t.id from (select a.id, a.k from a where a.id IS NOT NULL) t " +
                         "where t.k = 1"),
         Arguments.of("select a.id, b.x from a join b on a.id = b.id and b.x <> $(p)", NULL_VALUE,
                      "select a.id, b.x from a join b on a.id = b.id and b.x IS NOT NULL"),
         // EMPTY_STRING keeps the operator
         Arguments.of(SELECT + "where a.k = 1 and a.name <> $(p)", EMPTY_STRING,
                      SELECT + "where a.k = 1 and a.name <> ''"),
         Arguments.of(SELECT + "where not (a.name != $(p))", EMPTY_STRING,
                      SELECT + "where a.name = ''"),
         // the other operators mean IS NULL, only the negation is kept
         Arguments.of(SELECT + "where a.k = 1 and a.id = $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id > $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id >= $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id < $(p))", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.name like $(p)", NULL_VALUE,
                      SELECT + "where a.name IS NULL"),
         Arguments.of(SELECT + "where a.name not like $(p)", NULL_VALUE,
                      SELECT + "where a.name IS NOT NULL"));
   }

   @ParameterizedTest
   @MethodSource("paramCases")
   void paramSameRows(String sql, String p, String expected) throws Exception {
      try(Connection conn = connect()) {
         List<String> expectedRows = rows(conn, expected);

         for(String type : TYPES) {
            for(boolean forVpm : new boolean[] { false, true }) {
               String generated = validate(parse(sql, type), p, forVpm);
               assertEquals(expectedRows, rows(conn, generated),
                            type + " forVpm=" + forVpm + "\ngenerated: " + generated);
            }
         }
      }
   }

   // sql, the expected clause tail on the default helper, same on both paths
   static Stream<Arguments> paramText() {
      return Stream.of(
         Arguments.of(SELECT + "where a.k = 1 and a.id <> $(p)",
                      "where a.k = 1 and not (a.id IS NULL)"),
         Arguments.of(SELECT + "where a.k = 1 and a.id != $(p)",
                      "where a.k = 1 and not (a.id IS NULL)"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id <> $(p))",
                      "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and (a.id <> $(p) or a.k = 2)",
                      "where a.k = 1 and (not (a.id IS NULL) or a.k = 2)"),
         Arguments.of(GROUP + "having count(*) > 1 and min(a.name) <> $(p)",
                      "having count(*) > 1 and not (min(a.name) IS NULL)"),
         Arguments.of(SELECT + "where a.k = 1 and a.id > $(p)",
                      "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.name like $(p)",
                      "where a.k = 1 and a.name IS NULL"));
   }

   @ParameterizedTest
   @MethodSource("paramText")
   void paramGenerated(String sql, String expected) throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         assertEndsWith(expected, validate(parse(sql, "default"), NULL_VALUE, forVpm));
      }
   }

   // a WHERE that is a single condition rather than a set, as built by code
   @Test
   void bareConditionRoot() throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         UniformSQL usql = parse(SELECT + "where a.k = 1 and a.id <> $(p)", "default");
         usql.setWhere(findLeaf(usql.getWhere(), "a.id"));
         assertEndsWith("from a where not (a.id IS NULL)", validate(usql, NULL_VALUE, forVpm));

         usql = parse(SELECT + "where a.k = 1 and a.id <> $(p)", "default");
         usql.setWhere(findLeaf(usql.getWhere(), "a.id"));
         usql.getWhere().setIsNot(true);
         assertEndsWith("from a where a.id IS NULL", validate(usql, NULL_VALUE, forVpm));
      }
   }

   // the IN (subquery) is only visited on the jdbc path
   @Test
   void subquery() throws Exception {
      String sql = SELECT + "where a.k = 1 and a.id IN " +
         "(select b.id from b where b.k = 2 and b.x <> $(p))";
      String expected = SELECT + "where a.k = 1 and a.id IN " +
         "(select b.id from b where b.k = 2 and b.x IS NOT NULL)";

      try(Connection conn = connect()) {
         for(String type : TYPES) {
            String generated = validate(parse(sql, type), NULL_VALUE, false);
            assertEquals(rows(conn, expected), rows(conn, generated),
                         type + "\ngenerated: " + generated);
         }
      }

      assertTrue(validate(parse(sql, "default"), NULL_VALUE, true).contains("b.x <> $(p)"));
   }

   // the operand of a truth test is rewritten in place. Only the tree is checked here, the
   // text and the rows are in IS_SHAPES.
   @Test
   void truthTestOperand() throws Exception {
      String[][] cases = {
         { SELECT + "where (a.id <> $(p)) is true", "true" },
         { SELECT + "where (a.id != $(p)) is not false", "true" },
         { SELECT + "where (not (a.id <> $(p))) is true", "false" },
         { SELECT + "where (a.id = $(p)) is unknown", "false" },
         { SELECT + "where not ((a.id <> $(p)) is true)", "true" },
      };

      for(String[] c : cases) {
         for(boolean forVpm : new boolean[] { false, true }) {
            UniformSQL usql = parse(c[0], "default");
            validate(usql, NULL_VALUE, forVpm);
            XSet test = findTruthTest(usql.getWhere());
            assertNotNull(test, c[0]);
            XBinaryCondition operand = (XBinaryCondition) test.getChild(0);
            assertEquals("IS NULL", operand.getExpression2().toString().trim(), c[0]);
            assertEquals(Boolean.parseBoolean(c[1]), operand.isIsNot(),
                         c[0] + " forVpm=" + forVpm);
         }
      }
   }

   // with the IS operand parenthesized (#77572), the NOT stays inside the test
   @Test
   void truthTestUnknownText() throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         assertEndsWith("where ((not (a.id IS NULL)) is unknown)",
                        validate(parse(SELECT + "where (a.id <> $(p)) is unknown", "default"),
                                 NULL_VALUE, forVpm));
      }
   }

   // truth test shapes and the hand-written sql with the expected rows
   private static final String[][] IS_SHAPES = {
      { "(a.id <> $(p)) is unknown", "(a.id IS NOT NULL) is unknown" },
      { "(a.id != $(p)) is not unknown", "(a.id IS NOT NULL) is not unknown" },
      { "(a.id <> $(p)) is true", "(a.id IS NOT NULL) is true" },
      { "(a.id <> $(p)) is not true", "(a.id IS NOT NULL) is not true" },
      { "(not (a.id <> $(p))) is true", "(a.id IS NULL) is true" },
      { "not ((a.id <> $(p)) is false)", "not ((a.id IS NOT NULL) is false)" },
      { "(a.id = $(p)) is false", "(a.id IS NULL) is false" },
      { "(a.id <> 'NULL_VALUE') is unknown", "(a.id IS NOT NULL) is unknown" },
      { "(not (a.id <> 'NULL_VALUE')) is true", "(a.id IS NULL) is true" },
   };

   @Test
   @EnabledIfSystemProperty(named = "sentinel.pg.url", matches = ".+")
   void samePostgresRows() throws Exception {
      try(Connection conn = DriverManager.getConnection(
         System.getProperty("sentinel.pg.url"), System.getProperty("sentinel.pg.user", "postgres"),
         System.getProperty("sentinel.pg.password")))
      {
         createTables(conn, true);
         List<String> failures = new ArrayList<>();

         for(String[] shape : IS_SHAPES) {
            List<String> expected = rows(conn, SELECT + "where " + shape[1]);

            for(String type : new String[] { "default", "oracle", "oracle-ansi" }) {
               for(boolean forVpm : new boolean[] { false, true }) {
                  String generated = validate(parse(SELECT + "where " + shape[0], type),
                                              NULL_VALUE, forVpm);

                  // oracle non-ansi has no literal NULL_VALUE branch
                  if(!generated.contains("'NULL_VALUE'") &&
                     !expected.equals(rows(conn, generated)))
                  {
                     failures.add(type + " forVpm=" + forVpm + ": " + generated);
                  }
               }
            }
         }

         assertEquals("[]", failures.toString());
      }
   }

   // sql with the literal, hand-written sql with the expected rows
   static Stream<Arguments> literalCases() {
      return Stream.of(
         Arguments.of(SELECT + "where a.k = 1 and a.id <> 'NULL_VALUE'",
                      SELECT + "where a.k = 1 and a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id != 'NULL_VALUE'",
                      SELECT + "where a.k = 1 and a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id <> 'NULL_VALUE')",
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id = 'NULL_VALUE'",
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.id > 'NULL_VALUE'",
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where a.k = 1 and a.name like 'NULL_VALUE'",
                      SELECT + "where a.k = 1 and a.name IS NULL"));
   }

   // the literal is replaced by the helper, OracleSQLHelper without ANSI joins doesn't
   @ParameterizedTest
   @MethodSource("literalCases")
   void literalSameRows(String sql, String expected) throws Exception {
      try(Connection conn = connect()) {
         for(String type : new String[] { "default", "derby", "derby-ansi", "oracle-ansi" }) {
            String generated = generate(parse(sql, type));
            assertFalse(generated.contains("NULL_VALUE"), generated);
            assertEquals(rows(conn, expected), rows(conn, generated),
                         type + "\ngenerated: " + generated);
         }

         assertTrue(generate(parse(sql, "oracle")).contains("'NULL_VALUE'"));
      }
   }

   @Test
   void literalGenerated() throws Exception {
      assertEndsWith("where a.k = 1 and a.id IS NOT NULL",
                     generate(parse(SELECT + "where a.k = 1 and a.id <> 'NULL_VALUE'", "default")));
      assertEndsWith("where a.k = 1 and a.id IS NOT NULL",
                     generate(parse(SELECT + "where a.k = 1 and a.id != 'NULL_VALUE'", "default")));
      assertEndsWith("where a.k = 1 and not (a.id IS NOT NULL)",
                     generate(parse(SELECT + "where a.k = 1 and not (a.id <> 'NULL_VALUE')",
                                    "default")));
      assertEndsWith("where a.k = 1 and a.id IS NULL",
                     generate(parse(SELECT + "where a.k = 1 and a.id > 'NULL_VALUE'", "default")));
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77571;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static Connection connect() throws SQLException {
      Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77571;create=true");
      createTables(conn, false);
      return conn;
   }

   // temp tables hide any real a and b, and are dropped with the connection
   private static void createTables(Connection conn, boolean temp) throws SQLException {
      String create = temp ? "create temp table " : "create table ";

      try(Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b" }) {
            try {
               if(!temp) {
                  stmt.executeUpdate("drop table " + table);
               }
            }
            catch(SQLException ignore) {
               // first run
            }
         }

         stmt.executeUpdate(create + "a (id int, k int, name varchar(20))");
         stmt.executeUpdate(create + "b (id int, k int, x int)");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'NULL_VALUE'), (6, 1, null), " +
                               "(7, 3, null), (8, 3, null), (9, 2, '')");
         stmt.executeUpdate("insert into b values (1, 2, null), (3, 2, 7), (5, 2, 4), " +
                               "(6, 2, null), (9, 1, 2)");
      }
   }

   private static List<String> rows(Connection conn, String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getString(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // the condition on the column
   private static XFilterNode findLeaf(XNode node, String column) {
      if(node instanceof XBinaryCondition &&
         column.equals(((XBinaryCondition) node).getExpression1().toString().trim()))
      {
         return (XFilterNode) node;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         XFilterNode leaf = findLeaf(node.getChild(i), column);

         if(leaf != null) {
            return leaf;
         }
      }

      return null;
   }

   private static XSet findTruthTest(XNode node) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         return set;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         XSet test = findTruthTest(node.getChild(i));

         if(test != null) {
            return test;
         }
      }

      return null;
   }

   private static String validate(UniformSQL usql, String p, boolean forVpm) {
      VariableTable vars = new VariableTable();
      vars.put("p", p);
      XUtil.validateConditions(null, usql, vars, true, forVpm);
      return generate(usql);
   }

   private static String generate(UniformSQL usql) {
      usql.clearSQLString();
      return normalize(usql.getSQLString());
   }

   private static void assertEndsWith(String expected, String sql) {
      assertTrue(sql.endsWith(" " + expected), "expected ..." + expected + "\nactual " + sql);
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").replace("( ", "(").trim();
   }

   // a new query for every case and run, the rewrite changes the parsed tree in place
   private static UniformSQL parse(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return sql;
   }
}
