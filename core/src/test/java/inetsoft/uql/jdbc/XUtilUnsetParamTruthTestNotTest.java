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
import inetsoft.uql.XNode;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77738, XUtil.removeNoParamConditions removes the condition of an unset parameter.
 * <ul>
 * <li>A truth test, x IS [NOT] TRUE/FALSE/UNKNOWN, was taken for a junction: its operand was
 * removed and the bare truth value was moved up, so a.id &gt; 1 and (a.k = $(p)) is false
 * became a.id &gt; 1 and false. The whole truth test is removed now.</li>
 * <li>A set left with one condition is replaced by it, which dropped the NOT of the set, so
 * not (a.name = 'n1' and a.k = $(p)) became a.name = 'n1' instead of not (a.name = 'n1').
 * The issue called not (a.k = 1 and a.id = $(p)) -&gt; a.k = 1 correct, it isn't. The NOT
 * is kept on the remaining condition now, also for a having root.</li>
 * </ul>
 * The two interact: without the NOT fix a removed truth test drops the NOT of its set, and
 * without the truth test fix a NOT on a collapsed truth value keeps no group.
 * <p>
 * A statement with a truth test fails to parse (#77735) and runs as written, so a truth-test
 * tree comes only from a saved parse without its sql string (or condition text). Those cases
 * build the tree of such a parse, raw and after an XML round trip. The rows are compared on
 * Derby, which has no IS TRUE, so the conditions that keep a truth test are checked as text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilUnsetParamTruthTestNotTest {
   private static final String SELECT = "select a.id from a";
   private static final String GROUP = "select a.name from a group by a.name";
   private static final String[] TYPES = { "default", "derby", "derby-ansi" };

   // the where (w) or having (h) condition with $(p) unset, the expected condition, and the
   // expected rows of a, see createTable
   static Stream<Arguments> notCases() {
      return Stream.of(
         // the #77738 control
         Arguments.of("w", "not (a.id = 1 and a.k = $(p))", "where not (a.id = 1)",
                      "2,3,4,5,6"),
         Arguments.of("w", "not (a.name = 'n1' and a.k = $(p))", "where not (a.name = 'n1')",
                      "3,4"),
         Arguments.of("w", "a.id > 1 and not (a.name = 'n1' and a.k = $(p))",
                      "where a.id > 1 and not (a.name = 'n1')", "3,4"),
         Arguments.of("w", "a.id > 1 and not (a.name = 'n1' or a.k = $(p))",
                      "where a.id > 1 and not (a.name = 'n1')", "3,4"),
         Arguments.of("w", "a.id > 1 or not (a.name = 'n1' or a.k = $(p))",
                      "where (a.id > 1 or not (a.name = 'n1'))", "2,3,4,5,6"),
         Arguments.of("w", "a.id > 1 or not (a.name = 'n2' and a.k = $(p))",
                      "where (a.id > 1 or not (a.name = 'n2'))", "1,2,3,4,5,6"),
         Arguments.of("w", "(a.id > 1 or a.id < 0) and not (a.name = 'n1' and a.k = $(p))",
                      "where (a.id > 1 or a.id < 0) and not (a.name = 'n1')", "3,4"),
         // nested NOT
         Arguments.of("w", "a.id > 1 and not (a.name = 'n1' and not (a.id = 3 and a.k = $(p)))",
                      "where a.id > 1 and not (a.name = 'n1' and not (a.id = 3))", "3,4"),
         Arguments.of("w", "a.id > 1 and not (a.id < 6 and not (a.name = 'n1' and a.k = $(p)))",
                      "where a.id > 1 and not (a.id < 6 and not (a.name = 'n1'))", "2,5,6"),
         // double negation
         Arguments.of("w", "a.id > 1 and not (not (a.name = 'n1') and a.k = $(p))",
                      "where a.id > 1 and a.name = 'n1'", "2,5"),
         Arguments.of("w", "not (not (a.name = 'n1' and a.k = $(p)))",
                      "where a.name = 'n1'", "1,2,5"),
         // the remaining condition is a set
         Arguments.of("w", "a.id > 1 and not ((a.name = 'n1' and a.id < 5) and a.k = $(p))",
                      "where a.id > 1 and not (a.name = 'n1' and a.id < 5)", "3,4,5,6"),
         Arguments.of("w", "a.id > 0 and not ((a.name = 'n1' or a.id = 4) or a.k = $(p))",
                      "where a.id > 0 and (not (a.name = 'n1' or a.id = 4))", "3"),
         // nothing left
         Arguments.of("w", "not (a.k = $(p))", "from a", "1,2,3,4,5,6"),
         Arguments.of("w", "a.id > 1 and not (a.k = $(p) and a.name = $(p))",
                      "where a.id > 1", "2,3,4,5,6"),
         // the having root is replaced by the remaining condition
         Arguments.of("h", "not (count(*) > 2 and max(a.k) = $(p))",
                      "having not (count(*) > 2)", "n2,null"),
         Arguments.of("h", "count(*) > 0 and not (count(*) > 2 and max(a.k) = $(p))",
                      "having count(*) > 0 and not (count(*) > 2)", "n2,null"),
         Arguments.of("h", "not (not (count(*) > 2) or max(a.k) = $(p))",
                      "having count(*) > 2", "n1"));
   }

   // the truth test cases, rows are null for a condition that keeps a truth test
   static Stream<Arguments> truthTestCases() {
      return Stream.of(
         Arguments.of("w", "a.id > 1 and (a.k = $(p)) is false", "where a.id > 1",
                      "2,3,4,5,6"),
         Arguments.of("w", "(a.k = $(p)) is false", "from a", "1,2,3,4,5,6"),
         Arguments.of("w", "(a.k <> $(p)) is not true", "from a", "1,2,3,4,5,6"),
         Arguments.of("w", "a.id > 1 or (a.k = $(p)) is unknown", "where a.id > 1",
                      "2,3,4,5,6"),
         Arguments.of("w", "a.id > 1 and (not (a.k = $(p))) is true", "where a.id > 1",
                      "2,3,4,5,6"),
         // a NOT on the truth test
         Arguments.of("w", "a.id > 1 and not ((a.k = $(p)) is false)", "where a.id > 1",
                      "2,3,4,5,6"),
         // a truth test in a NOT set
         Arguments.of("w", "a.id > 1 and not (a.name = 'n1' and (a.k = $(p)) is false)",
                      "where a.id > 1 and not (a.name = 'n1')", "3,4"),
         Arguments.of("w", "a.id > 1 and not ((a.name = 'n1') is true and a.k = $(p))",
                      "where a.id > 1 and not (((a.name = 'n1') is true))", null),
         // a NOT set in a truth test
         Arguments.of("w", "a.id > 1 and (not (a.name = 'n1' and a.k = $(p))) is true",
                      "where a.id > 1 and ((not (a.name = 'n1')) is true)", null),
         Arguments.of("w", "a.id > 1 and (not (a.name = 'n1' and a.k = $(p))) is not false",
                      "where a.id > 1 and ((not (a.name = 'n1')) is not false)", null),
         // a compound operand keeps the partial removal of a set
         Arguments.of("w", "a.id > 1 and (a.name = 'n1' and a.k = $(p)) is false",
                      "where a.id > 1 and ((a.name = 'n1') is false)", null),
         Arguments.of("h", "not ((max(a.k) = $(p)) is true)", "group by a.name",
                      "n1,n2,null"),
         // the having root is replaced by a truth test, whatever its relation
         Arguments.of("h", "not ((count(*) > 2) is true and max(a.k) = $(p))",
                      "having not ((count(*) > 2) is true)", null),
         Arguments.of("h", "count(*) > 2 and (max(a.k) = $(p)) is true",
                      "having count(*) > 2", "n1"));
   }

   @ParameterizedTest
   @MethodSource("notCases")
   void notIsKept(String clause, String condition, String expected, String rows)
      throws Exception
   {
      String text = statement(clause) + " " + ("w".equals(clause) ? "where " : "having ") +
         condition;

      try(Connection conn = connect()) {
         for(String type : TYPES) {
            String generated = validate(parse(text, type), null);

            if("default".equals(type)) {
               assertTrue(generated.endsWith(" " + expected), generated);
            }

            assertEquals(rows, rows(conn, generated), type + ": " + generated);
            // with p set the condition is kept
            assertEquals(generate(parse(text, type)), validate(parse(text, type), "1"), type);
         }
      }
   }

   @ParameterizedTest
   @MethodSource("truthTestCases")
   void truthTestIsRemoved(String clause, String condition, String expected, String rows)
      throws Exception
   {
      try(Connection conn = connect()) {
         for(String mode : new String[] { "raw", "saved" }) {
            for(String type : TYPES) {
               String generated = validate(savedTree(clause, condition, type, mode), null);
               String msg = mode + " " + type + ": " + generated;

               if("default".equals(type)) {
                  assertTrue(generated.endsWith(" " + expected), msg);
               }

               // no bare truth value is left, such as "and false" or "not (true)"
               assertFalse(generated.matches(
                  "(?i).*(\\bwhere|\\bhaving|\\band|\\bor|\\() *(true|false|unknown)\\b.*"), msg);

               if(rows != null) {
                  assertEquals(rows, rows(conn, generated), msg);
               }

               // with p set the truth test is kept
               UniformSQL kept = savedTree(clause, condition, type, mode);
               String unchanged = generate(savedTree(clause, condition, type, mode));
               assertEquals(unchanged, validate(kept, "1"), msg);
            }
         }
      }
   }

   private static String statement(String clause) {
      return "w".equals(clause) ? SELECT : GROUP;
   }

   // a parse saved before the refusal (#77735): the statement is parsed without the
   // condition, which is parsed as condition text as the statement parse did. Without a sql
   // string, so the tree is generated, raw or after an XML round trip
   private static UniformSQL savedTree(String clause, String condition, String type,
                                       String mode)
      throws Exception
   {
      UniformSQL refused = new UniformSQL();
      new SQLProcessor(refused).parse(statement(clause) + ("w".equals(clause) ?
         " where " : " having ") + condition);
      assertEquals(UniformSQL.PARSE_FAILED, refused.getParseResult(), condition);

      UniformSQL sql = parse(statement(clause), type);
      XFilterNode node = new SQLParser(new SQLLexer(new StringReader(condition)))
         .search_condition();

      if("w".equals(clause)) {
         node.setClause(XFilterNode.WHERE);
         sql.combineWhereByAnd(node);
      }
      else {
         node.setClause(XFilterNode.HAVING);
         sql.setHaving(node);
      }

      sql.clearSQLString();

      if(!"saved".equals(mode)) {
         return sql;
      }

      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      loaded.setDataSource(sql.getDataSource());
      assertFalse(loaded.hasSQLString());
      assertNotNull(findTruthTest("w".equals(clause) ? loaded.getWhere() : loaded.getHaving()),
                    condition);
      return loaded;
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

   // p unset if null
   private static String validate(UniformSQL sql, String p) {
      VariableTable vars = new VariableTable();

      if(p != null) {
         vars.put("p", p);
      }

      XUtil.validateConditions(null, sql, vars, true, false);
      return generate(sql);
   }

   private static String generate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").replace("( ", "(").trim();
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77738;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static Connection connect() throws SQLException {
      Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77738;create=true");

      try(Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table a");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.executeUpdate("create table a (id int, k varchar(10), name varchar(10))");
         stmt.executeUpdate("insert into a values (1, '1', 'n1'), (2, '2', 'n1'), " +
                               "(3, '1', 'n2'), (4, '2', 'n2'), (5, null, 'n1'), (6, '3', null)");
      }

      return conn;
   }

   // the first column of every row, sorted
   private static String rows(Connection conn, String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         while(rs.next()) {
            rows.add(String.valueOf(rs.getString(1)));
         }
      }

      Collections.sort(rows);
      return String.join(",", rows);
   }
}
