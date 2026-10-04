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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * Bug #77551, a parameter holding NULL_VALUE, EMPTY_STRING or NULL_STRING must rewrite only
 * the condition that references it. XUtil.validateConditions used to replace the whole
 * WHERE/HAVING clause with the rewritten condition and drop its NOT.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilSentinelConditionTest {
   private static final String NULL_VALUE = XConstants.CONDITION_NULL_VALUE;
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   private static final String NULL_STRING = XConstants.CONDITION_NULL_STRING;
   private static final String SELECT = "select a.id, a.k, a.name from a ";

   // sql, parameter p, parameter q (null for unset), expected clause tail. Same on both paths.
   static Stream<Arguments> bothPaths() {
      return Stream.of(
         // AND child
         Arguments.of(SELECT + "where a.k = 1 and a.id = $(p)", NULL_VALUE, null,
                      "where a.k = 1 and a.id IS NULL"),
         // NOT on the leaf
         Arguments.of(SELECT + "where not (a.id = $(p))", NULL_VALUE, null,
                      "where not (a.id IS NULL)"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.id = $(p))", NULL_VALUE, null,
                      "where a.k = 1 and not (a.id IS NULL)"),
         // OR child
         Arguments.of(SELECT + "where a.k = 1 or a.id = $(p)", NULL_VALUE, null,
                      "where (a.k = 1 or a.id IS NULL)"),
         // nested set
         Arguments.of(SELECT + "where a.k = 1 and (a.k = 2 or a.id = $(p))", NULL_VALUE, null,
                      "where a.k = 1 and (a.k = 2 or a.id IS NULL)"),
         // NOT on a set
         Arguments.of(SELECT + "where not (a.k = 1 and a.id = $(p))", NULL_VALUE, null,
                      "where not (a.k = 1 and a.id IS NULL)"),
         Arguments.of(SELECT + "where not (a.k = 1 or a.id = $(p))", NULL_VALUE, null,
                      "where (not (a.k = 1 or a.id IS NULL))"),
         // several sentinels in one clause
         Arguments.of(SELECT + "where a.id = $(p) and a.k = $(q)", NULL_VALUE, NULL_VALUE,
                      "where a.id IS NULL and a.k IS NULL"),
         Arguments.of(SELECT + "where a.name = $(p) or a.name = $(q)", EMPTY_STRING, NULL_STRING,
                      "where (a.name = '' or a.name = 'null')"),
         // EMPTY_STRING and NULL_STRING keep the operator
         Arguments.of(SELECT + "where a.k = 1 and a.name LIKE $(p)", EMPTY_STRING, null,
                      "where a.k = 1 and a.name LIKE ''"),
         Arguments.of(SELECT + "where a.k = 1 and a.name <> $(p)", EMPTY_STRING, null,
                      "where a.k = 1 and a.name <> ''"),
         Arguments.of(SELECT + "where not (a.name = $(p))", EMPTY_STRING, null,
                      "where not (a.name = '')"),
         Arguments.of(SELECT + "where a.k = 1 and a.name = $(p)", NULL_STRING, null,
                      "where a.k = 1 and a.name = 'null'"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.name = $(p))", NULL_STRING, null,
                      "where a.k = 1 and not (a.name = 'null')"),
         // HAVING
         Arguments.of("select a.k, max(a.id) from a group by a.k " +
                         "having count(*) > 1 and max(a.id) = $(p)", NULL_VALUE, null,
                      "having count(*) > 1 and max(a.id) IS NULL"),
         Arguments.of("select a.k, max(a.id) from a group by a.k " +
                         "having count(*) > 1 and not (max(a.id) = $(p))", NULL_VALUE, null,
                      "having count(*) > 1 and not (max(a.id) IS NULL)"),
         Arguments.of("select a.k, max(a.name) from a group by a.k " +
                         "having count(*) > 1 or max(a.name) = $(p)", EMPTY_STRING, null,
                      "having count(*) > 1 or max(a.name) = ''"),
         Arguments.of("select a.k, max(a.name) from a group by a.k " +
                         "having count(*) > 1 and max(a.name) = $(p)", NULL_STRING, null,
                      "having count(*) > 1 and max(a.name) = 'null'"),
         // WHERE and HAVING in one query
         Arguments.of("select a.k, max(a.id) from a where a.k = 3 and a.id = $(q) group by a.k " +
                         "having max(a.id) = $(p)", NULL_VALUE, NULL_STRING,
                      "where a.k = 3 and a.id = 'null' group by a.k having max(a.id) IS NULL"),
         // controls, no sentinel
         Arguments.of(SELECT + "where a.k = 1 and a.id = $(p)", "x", null,
                      "where a.k = 1 and a.id = $(p)"),
         Arguments.of(SELECT + "where not (a.id = $(p))", "x", null,
                      "where not (a.id = $(p))"));
   }

   @ParameterizedTest
   @MethodSource("bothPaths")
   void jdbcPath(String sql, String p, String q, String expected) {
      assertEndsWith(expected, validate(parse(sql), p, q, false));
   }

   @ParameterizedTest
   @MethodSource("bothPaths")
   void vpmPath(String sql, String p, String q, String expected) {
      assertEndsWith(expected, validate(parse(sql), p, q, true));
   }

   // the HAVING rewrite was lost when another conjunct's parameter is unset (jdbc path only,
   // the vpm path keeps conditions with unset parameters)
   @Test
   void havingKeptWhenOtherParameterUnset() {
      String sql = "select a.k, max(a.id) from a group by a.k " +
         "having max(a.id) = $(p) and count(*) > $(q)";
      assertEndsWith("having max(a.id) IS NULL", validate(parse(sql), NULL_VALUE, null, false));
      assertEndsWith("having max(a.id) IS NULL and count(*) > $(q)",
                     validate(parse(sql), NULL_VALUE, null, true));
   }

   // a sentinel in an IN (subquery) dropped the whole IN predicate on the jdbc path
   @Test
   void subqueryPredicateKept() {
      String sql = SELECT + "where a.k = 1 and a.id IN " +
         "(select b.id from b where b.k = 2 and b.x = $(p))";
      assertEndsWith("where a.k = 1 and a.id IN (select b.id from b where b.k = 2 and b.x IS NULL)",
                     validate(parse(sql), NULL_VALUE, null, false));
      assertEndsWith("where a.k = 1 and a.id IN (select b.id from b where b.k = 2 and b.x = '')",
                     validate(parse(sql), EMPTY_STRING, null, false));

      sql = SELECT + "where a.id IN (select b.id from b where b.x = $(p))";
      assertEndsWith("where a.id IN (select b.id from b where b.x IS NULL)",
                     validate(parse(sql), NULL_VALUE, null, false));
   }

   // a sub-query whose WHERE is a bare condition: the rewrite must not be reported as
   // changed, removeNoParamConditions() would take the sub-query for one without condition
   @Test
   void bareConditionSubqueryKept() {
      UniformSQL usql = parse(SELECT + "where a.k = 1 and a.id IN " +
                                 "(select b.id from b where b.k = 2 and b.x = $(p))");
      XBinaryCondition in = (XBinaryCondition) findLeaf(usql.getWhere(), "a.id");
      UniformSQL sub = (UniformSQL) in.getExpression2().getValue();
      sub.setWhere(findLeaf(sub.getWhere(), "b.x"));
      assertEndsWith("where a.k = 1 and a.id IN (select b.id from b where b.x IS NULL)",
                     validate(usql, NULL_VALUE, null, false));
   }

   // a WHERE/HAVING that is a single condition rather than a set, as built by code
   // rather than the parser
   @Test
   void bareConditionRoot() {
      // NOT on the bare leaf itself
      UniformSQL usql = parse(SELECT + "where a.k = 1 and a.id = $(p)");
      usql.setWhere(findLeaf(usql.getWhere(), "a.id"));
      usql.getWhere().setIsNot(true);
      assertEndsWith("from a where not (a.id IS NULL)", validate(usql, NULL_VALUE, null, false));
      assertInstanceOf(XBinaryCondition.class, usql.getWhere());

      usql = parse(SELECT + "where a.k = 1 and a.name = $(p)");
      usql.setWhere(findLeaf(usql.getWhere(), "a.name"));
      assertEndsWith("from a where a.name = 'null'", validate(usql, NULL_STRING, null, true));

      usql = parse("select a.k, max(a.name) from a group by a.k " +
                      "having count(*) > 1 and max(a.name) <> $(p)");
      usql.setHaving(findLeaf(usql.getHaving(), "max(a.name)"));
      assertEndsWith("group by a.k having max(a.name) <> ''",
                     validate(usql, EMPTY_STRING, null, false));
      assertInstanceOf(XBinaryCondition.class, usql.getHaving());
   }

   // the rewritten condition keeps the name of the original condition
   @Test
   void nameKept() {
      UniformSQL usql = parse(SELECT + "where a.k = 1 and a.id = $(p)");
      XFilterNode leaf = findLeaf(usql.getWhere(), "a.id");
      leaf.setName("cond1");
      validate(usql, NULL_VALUE, null, true);
      XFilterNode rewritten = findLeaf(usql.getWhere(), "a.id");
      assertNotSame(leaf, rewritten);
      assertEquals("cond1", rewritten.getName());
   }

   // a HAVING-only rewrite must drop the cached sql string
   @Test
   void cachedStringCleared() {
      UniformSQL usql = parse("select a.k, max(a.id) from a group by a.k " +
                                 "having count(*) > 1 and max(a.id) = $(p)");
      usql.setCacheable(true);
      String before = usql.toString();
      assertTrue(before.contains("$(p)"), before);
      // the vpm path never reports changed
      validate(usql, NULL_VALUE, null, true);
      assertTrue(normalize(usql.toString()).endsWith("max(a.id) IS NULL"), usql.toString());
   }

   // a HAVING-only rewrite in a derived table must reach the regenerated sql of the outer
   // query once JDBCHandler drops the outer cached string, even when every string was cached
   // before validation
   @Test
   void derivedTableCachedString() {
      String sql = "select t.k from (select a.k, max(a.id) m from a group by a.k " +
         "having count(*) > 1 and max(a.id) = $(p)) t";

      for(boolean forVpm : new boolean[] { false, true }) {
         UniformSQL usql = parse(sql);
         usql.setCacheable(true);
         UniformSQL sub = (UniformSQL) usql.getSelectTable()[0].getName();
         sub.setCacheable(true);
         assertTrue(usql.toString().contains("$(p)"), usql.toString());
         validate(usql, NULL_VALUE, null, forVpm);
         // as JDBCHandler.execute does before getSQLAsString()
         usql.clearCachedString();
         assertTrue(normalize(usql.toString())
                       .endsWith("having count(*) > 1 and max(a.id) IS NULL) t"),
                    "forVpm=" + forVpm + " " + usql);
      }
   }

   // the regenerated sql returns the rows of the hand-written expected sql
   static Stream<Arguments> rowCases() {
      return Stream.of(
         Arguments.of(SELECT + "where a.k = 1 and a.id = $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 and a.id IS NULL"),
         Arguments.of(SELECT + "where not (a.id = $(p))", NULL_VALUE,
                      SELECT + "where a.id IS NOT NULL"),
         Arguments.of(SELECT + "where a.k = 1 or a.id = $(p)", NULL_VALUE,
                      SELECT + "where a.k = 1 or a.id IS NULL"),
         Arguments.of(SELECT + "where not (a.k = 2 or a.id = $(p))", NULL_VALUE,
                      SELECT + "where not (a.k = 2 or a.id IS NULL)"),
         Arguments.of(SELECT + "where a.k = 1 and a.name = $(p)", EMPTY_STRING,
                      SELECT + "where a.k = 1 and a.name = ''"),
         Arguments.of(SELECT + "where a.k = 1 and not (a.name = $(p))", NULL_STRING,
                      SELECT + "where a.k = 1 and a.name <> 'null'"),
         Arguments.of("select a.k, count(*) from a group by a.k " +
                         "having count(*) > 5 and min(a.name) = $(p)", NULL_VALUE,
                      "select a.k, count(*) from a group by a.k " +
                         "having count(*) > 5 and min(a.name) IS NULL"));
   }

   @ParameterizedTest
   @MethodSource("rowCases")
   void sameRows(String sql, String p, String expected) throws SQLException {
      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77551;create=true")) {
         createTable(conn);

         for(boolean forVpm : new boolean[] { false, true }) {
            String generated = validate(parse(sql), p, null, forVpm);
            assertEquals(rows(conn, expected), rows(conn, generated),
                         "forVpm=" + forVpm + "\ngenerated: " + generated);
         }
      }
   }

   // the IN (subquery) is only visited on the jdbc path
   @Test
   void subquerySameRows() throws SQLException {
      String sql = SELECT + "where a.k = 1 and a.id IN (select b.id from b where b.k = 2 and b.x = $(p))";
      String expected = SELECT + "where a.k = 1 and a.id IN " +
         "(select b.id from b where b.k = 2 and b.x IS NULL)";

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77551;create=true")) {
         createTable(conn);
         String generated = validate(parse(sql), NULL_VALUE, null, false);
         assertEquals(rows(conn, expected), rows(conn, generated), generated);
      }
   }

   // Bug #77708, XUtil.rewriteSentinels rewrites only the sentinels, keeps the conditions of an
   // unset parameter, and returns whether the generated sql changed
   @Test
   void rewriteSentinelsContract() throws Exception {
      String sql = SELECT + "where a.name = $(p) and a.k = $(q)";
      VariableTable vars = new VariableTable();
      vars.put("p", EMPTY_STRING);
      UniformSQL usql = parse(sql);
      assertTrue(XUtil.rewriteSentinels(usql, vars));
      String generated = normalize(usql.getSQLString());
      assertTrue(generated.contains("a.name = ''"), generated);
      assertTrue(generated.contains("$(q)"), "the unset condition was removed: " + generated);

      // a value, or only an unset parameter: nothing changes
      for(String p : new String[] { "n1", null }) {
         vars = new VariableTable();

         if(p != null) {
            vars.put("p", p);
         }

         usql = parse(sql);
         String before = usql.getSQLString();
         assertFalse(XUtil.rewriteSentinels(usql, vars), "p=" + p);
         assertEquals(before, usql.getSQLString(), "p=" + p);
      }

      // a query that still holds its sql string is left as it is
      vars = new VariableTable();
      vars.put("p", NULL_VALUE);
      usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      // as SQLProcessor keeps the string after parsing
      usql.setSQLString(sql, false);
      assertFalse(XUtil.rewriteSentinels(usql, vars));
      assertEquals(sql, usql.getSQLString());

      // the subquery of a WHERE condition is walked (Bug #77706)
      usql = parse(SELECT + "where a.id IN (select b.id from b where b.x = $(p))");
      assertTrue(XUtil.rewriteSentinels(usql, vars));
      generated = normalize(usql.getSQLString());
      assertFalse(generated.contains("$(p)"), generated);
      assertTrue(generated.contains("b.x IS NULL"), generated);
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77551;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static void createTable(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table a");
            stmt.executeUpdate("drop table b");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("create table b (id int, k int, x int)");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'null'), (6, 1, null), " +
                               "(7, 3, null), (8, 3, null), (9, 2, '')");
         stmt.executeUpdate("insert into b values (1, 2, null), (3, 2, 7), (5, 3, null), " +
                               "(6, 2, null)");
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

   private static String validate(UniformSQL usql, String p, String q, boolean forVpm) {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      if(q != null) {
         vars.put("q", q);
      }

      XUtil.validateConditions(null, usql, vars, true, forVpm);
      return normalize(usql.getSQLString());
   }

   private static void assertEndsWith(String expected, String sql) {
      assertTrue(sql.endsWith(" " + expected), "expected ..." + expected + "\nactual " + sql);
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").replace("( ", "(").trim();
   }

   // the tree is only exposed once the stored sql string is cleared
   private static UniformSQL parse(String text) {
      UniformSQL sql = new UniformSQL();

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception ex) {
         throw new IllegalStateException(text, ex);
      }

      sql.clearSQLString();
      return sql;
   }
}
