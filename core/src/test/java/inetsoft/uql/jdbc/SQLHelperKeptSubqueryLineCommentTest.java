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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.vpm.VpmCondition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77753. A kept subquery text (the user's sql string, as the VPM editor, a query
 * builder condition, an sql view or a FROM select string keeps it) that ends in a line
 * comment commented out the closing paren SQLHelper wrote on the same line, so the query was
 * a syntax error. The paren now goes on a new line when the last line of kept text holds a
 * line comment (--, # or //, at the start of the line or after a space). Regenerated text,
 * and a -- glued to the text before it (the x-- 1 the parser wrote for x - -1, #77754), keep
 * the paren on the same line, so such a query stays a syntax error.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperKeptSubqueryLineCommentTest {
   private static final String DB = "jdbc:derby:memory:kept77753";
   // the rows of b with k = 'n1' are 1 and 5
   private static final String N1 = "select b.id from b where b.k = 'n1'";
   private static final List<String> N1_ROWS = List.of("1", "5");

   @BeforeAll
   static void createDatabase() throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB + ";create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.executeUpdate("create table a (id int)");
         stmt.executeUpdate("insert into a values (1), (3), (5), (7), (9)");
         stmt.executeUpdate("create table b (id int, a int, k varchar(20))");
         stmt.executeUpdate("insert into b values (1, 0, 'n1'), (3, 3, 'n2'), (5, 4, 'n1')");
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   @AfterEach
   void resetFormat() {
      SreeEnv.remove("sql.format");
   }

   // site 1: a condition subquery saved unparsed (the VPM editor) or parsed with its text
   // kept (a query builder condition), with and without sql formatting
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void conditionSubquery(boolean format) throws Exception {
      setFormat(format);

      for(String text : new String[] { N1 + " -- my comment", N1 + "\n-- my comment" }) {
         for(boolean parsed : new boolean[] { false, true }) {
            UniformSQL sub = kept(text, parsed);
            String sql = inSubquery(sub);

            assertTrue(sql.contains(text + "\n)"), sql);
            assertEquals(N1_ROWS, rows(sql), sql);
         }
      }

      // a newline the user typed is trimmed with formatting, and kept without
      String sql = inSubquery(kept(N1 + " -- my comment\n", false));
      assertEquals(N1_ROWS, rows(sql), sql);
   }

   // site 2: a derived table held as a kept UniformSQL. An sql view keeps its text unparsed
   // (XPartition.getRunTimeTable)
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void fromSqlView(boolean format) throws Exception {
      setFormat(format);
      String text = N1 + " -- my comment";
      String sql = fromSubquery(kept(text, false), 0);

      assertTrue(sql.contains(text + "\n)"), sql);
      assertEquals(N1_ROWS, rows(sql), sql);
   }

   // site 3: a FROM table given as a select string
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void fromSelectString(boolean format) throws Exception {
      setFormat(format);
      String text = N1 + " -- my comment";
      String sql = fromSubquery(text, 0);

      assertTrue(sql.contains(text + "\n)"), sql);
      assertEquals(N1_ROWS, rows(sql), sql);
   }

   // site 4: the select string limited by the input max rows (getFromTable)
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void fromSelectStringMaxRows(boolean format) throws Exception {
      setFormat(format);
      String text = N1 + " -- my comment";
      String sql = fromSubquery(text, 2);

      assertTrue(sql.contains("(" + text + "\n) t"), sql);
      assertEquals(N1_ROWS, rows(sql), sql);
   }

   // the condition of a VPM, as VpmUtil puts it in parens, with no enterprise code
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void vpmCondition(boolean format) throws Exception {
      setFormat(format);
      XBinaryCondition cond = new XBinaryCondition(
         new XExpression("a.id", XExpression.FIELD),
         new XExpression(new UniformSQL(N1 + " -- my comment", false), XExpression.SUBQUERY),
         "IN");
      VpmCondition vpm = new VpmCondition("c");
      vpm.setTable("a");
      vpm.setCondition(cond);
      String where = vpm.evaluate(null, new String[] { "a" }, new String[] { "a" },
                                  new String[] { "id" }, derby(), new VariableTable(), null,
                                  false);

      assertNotNull(where);
      String sql = "select a.id from a where (" + where + ")";
      assertEquals(N1_ROWS, rows(sql), sql);
   }

   // a # comment (mysql, mariadb, bigquery, clickhouse) and a // comment (snowflake)
   @ParameterizedTest
   @ValueSource(strings = { "mysql|# my comment", "snowflake|// my comment" })
   void otherLineComments(String param) throws Exception {
      String[] pair = param.split("\\|");
      JDBCDataSource ds = "mysql".equals(pair[0]) ?
         dataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test", "mysql") :
         dataSource("net.snowflake.client.jdbc.SnowflakeDriver",
                    "jdbc:snowflake://x.snowflakecomputing.com", "snowflake");
      String text = N1 + " " + pair[1];

      assertTrue(inSubquery(ds, kept(text, false)).contains(text + "\n)"), pair[0]);
      assertTrue(fromSubquery(ds, kept(text, false), 0).contains(text + "\n)"), pair[0]);
   }

   // no line comment on the last line: the paren follows the text as before
   @ParameterizedTest
   @ValueSource(strings = {
      N1,
      "select b.id from b -- my comment\nwhere b.k = 'n1'",
      N1 + " /* my comment */",
      "select b.id from b where b.k <> '-- x'",
      "select b.id from b where b.k <> 'a # x' and b.k <> 'b // x'",
      "select b.id from b b#1 where b.k = 'n1'",
   })
   void noLineCommentUnchanged(String text) throws Exception {
      for(boolean format : new boolean[] { true, false }) {
         setFormat(format);
         String sql = inSubquery(kept(text, false));

         assertTrue(sql.contains(text + ")"), sql);
         // the in check doesn't add a second paren
         assertEquals(1, sql.split("\\(", -1).length - 1, sql);
         sql = fromSubquery(kept(text, false), 0);
         assertTrue(sql.contains(text + ") v"), sql);
         sql = fromSubquery(text, 0);
         assertTrue(sql.contains(text + ") v"), sql);
      }
   }

   // regenerated text holds no user comment. The only -- it can hold is the x-- 1 the parser
   // wrote for x - -1 before #77754 (still in saved models), which must stay an error, not
   // comment out the rest of the predicate
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void regeneratedSubqueryStaysLoud(boolean format) throws Exception {
      setFormat(format);
      UniformSQL sub = new UniformSQL();
      sub.setDataSource(derby());
      sub.parse("select c.id from b c where c.k = 'n1' and c.id = c.a", UniformSQL.PARSE_ALL,
                UniformSQL.PARSE_PERIOD);
      sub.clearSQLString();
      setOperand2(sub.getWhere(), "c.a", "c.a-- 1");
      assertFalse(sub.hasSQLString());

      String sql = inSubquery(sub);
      assertTrue(sql.matches("(?s).*c\\.a-- 1 *\\).*"), sql);
      assertThrows(SQLException.class, () -> rows(sql), sql);
   }

   // kept text with a -- glued to the text before it, as the query builder condition dialog
   // saves the regenerated c.a-- 1 again, stays loud; a -- after a space is a comment
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void gluedDashStaysLoud(boolean format) throws Exception {
      setFormat(format);
      String glued = "select c.id from b c where c.id = c.a-- 1";

      for(String text : new String[] { glued, glued + " -- my comment" }) {
         String sql = inSubquery(kept(text, false));
         assertTrue(sql.contains(text + ")"), sql);
         assertThrows(SQLException.class, () -> rows(sql), sql);
      }

      // b has one row with id = a
      String sql = inSubquery(kept("select c.id from b c where c.id = c.a -- note", false));
      assertEquals(List.of("3"), rows(sql), sql);
   }

   // the HAVING wrapper of a one-column subquery (select alias from (...) innerN) still nests
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void havingSubquery(boolean format) throws Exception {
      setFormat(format);

      String n1 = "select b.id as x from b where b.k = 'n1'";

      for(String text : new String[] { n1, n1 + " -- my comment" }) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(derby());
         sql.parse("select a.id from a group by a.id having max(a.id) in (select b.id from b)",
                   UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
         setSubquery(sql.getHaving(), kept(text, true));
         sql.clearSQLString();
         String gen = sql.getSQLString();

         assertTrue(gen.contains("(select x from"), gen);
         assertEquals(N1_ROWS, rows(gen), gen);
      }
   }

   private static void setFormat(boolean format) {
      SreeEnv.setProperty("sql.format", format + "");
   }

   // a subquery whose text is kept: unparsed (VPM editor, sql view), or parsed (a query
   // builder condition typed as sql keeps the typed text)
   private static UniformSQL kept(String text, boolean parsed) throws Exception {
      UniformSQL sub;

      if(parsed) {
         // as the dialog parses it (JDBCUtil.createXExpression), keeping the text
         sub = new UniformSQL();
         sub.setDataSource(derby());
         new SQLProcessor(sub).parse(text);
         assertEquals(UniformSQL.PARSE_SUCCESS, sub.getParseResult(), text);
      }
      else {
         sub = new UniformSQL(text, false);
      }

      assertTrue(sub.hasSQLString(), text);
      return sub;
   }

   private static String inSubquery(UniformSQL sub) throws Exception {
      return inSubquery(derby(), sub);
   }

   private static String inSubquery(JDBCDataSource ds, UniformSQL sub) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse("select a.id from a where a.id in (select b.id from b)", UniformSQL.PARSE_ALL,
                UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      setSubquery(sql.getWhere(), sub);
      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static String fromSubquery(Object table, int maxrows) throws Exception {
      return fromSubquery(derby(), table, maxrows);
   }

   private static String fromSubquery(JDBCDataSource ds, Object table, int maxrows)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse("select v.id from a v", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      sql.getSelectTable(0).setName(table);

      if(maxrows > 0) {
         sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, maxrows + "");
      }

      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static void setSubquery(XNode node, UniformSQL sub) {
      if(node instanceof XBinaryCondition cond &&
         cond.getExpression2().getValue() instanceof UniformSQL)
      {
         cond.setExpression2(new XExpression(sub, XExpression.SUBQUERY));
         return;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         setSubquery(node.getChild(i), sub);
      }
   }

   // replace an operand text of the regenerated model, as an older parse saved it
   private static void setOperand2(XNode node, String from, String to) {
      if(node instanceof XBinaryCondition cond &&
         from.equals(String.valueOf(cond.getExpression2().getValue())))
      {
         cond.setExpression2(new XExpression(to, XExpression.EXPRESSION));
         return;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         setOperand2(node.getChild(i), from, to);
      }
   }

   private static JDBCDataSource derby() {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource("derby");
   }

   private static JDBCDataSource dataSource(String driver, String url, String helper) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77753_" + helper);
      ds.setDriver(driver);
      ds.setURL(url);
      assertEquals(helper, SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static List<String> rows(String sql) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Connection conn = DriverManager.getConnection(DB);
          Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql))
      {
         while(rs.next()) {
            rows.add(rs.getString(1));
         }
      }

      Collections.sort(rows);
      return rows;
   }
}
