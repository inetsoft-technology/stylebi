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
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77571, a VPM condition built in the condition editor, e.g. a.id &lt;&gt; $(p), with
 * p = NULL_VALUE must keep the rows where a.id is not null. It goes through
 * VpmCondition.evaluate, which rewrites the sentinel before generating the condition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionSentinelNotEqualTest {
   private static final String SELECT = "select a.id, a.k, a.name from a where ";

   // op, negated, sentinel, hand-written condition with the expected rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "<>|false|NULL_VALUE|a.id IS NOT NULL",
      "!=|false|NULL_VALUE|a.id IS NOT NULL",
      "<>|true|NULL_VALUE|a.id IS NULL",
      "!=|true|NULL_VALUE|a.id IS NULL",
      "=|false|NULL_VALUE|a.id IS NULL",
      "=|true|NULL_VALUE|a.id IS NOT NULL",
      ">|false|NULL_VALUE|a.id IS NULL",
      "<|false|NULL_VALUE|a.id IS NULL",
      "<>|false|EMPTY_STRING|a.name <> ''",
   })
   void sameRows(String op, boolean negated, String p, String expected) throws Exception {
      String column = expected.startsWith("a.name") ? "a.name" : "a.id";

      try(Connection conn = connect()) {
         List<String> expectedRows = rows(conn, SELECT + "a.k <> 1 and " + expected);

         for(String type : new String[] { "default", "oracle", "oracle-ansi", "derby-ansi" }) {
            XBinaryCondition cond = new XBinaryCondition(
               new XExpression(column, XExpression.FIELD),
               new XExpression("$(p)", XExpression.EXPRESSION), op);
            cond.setIsNot(negated);
            XSet conds = new XSet(XSet.AND);
            conds.addChild(new XBinaryCondition(new XExpression("a.k", XExpression.FIELD),
                                                new XExpression("1", XExpression.VALUE), "<>"));
            conds.addChild(cond);

            VpmCondition vpm = new VpmCondition("c");
            vpm.setTable("a");
            vpm.setCondition(conds);
            VariableTable vars = new VariableTable();
            vars.put("p", p);
            JDBCDataSource ds = "default".equals(type) ? null :
               SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
            String where = vpm.evaluate(null, new String[] { "a" }, new String[] { "a" },
                                        new String[] { "id", "k", "name" }, ds, vars, null,
                                        false);

            assertNotNull(where, type);
            assertEquals(expectedRows, rows(conn, SELECT + where), type + ": " + where);
         }
      }
   }

   // an op that is null or padded with spaces, as code may build it
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "|a.id IS NULL",
      "' <> '|not (a.id IS NULL)",
      "' != '|not (a.id IS NULL)",
   })
   void unusualOp(String op, String expected) throws Exception {
      for(boolean forVpm : new boolean[] { false, true }) {
         UniformSQL usql = new UniformSQL();
         usql.parse(SELECT + "a.k = 1 and a.id = $(p)", UniformSQL.PARSE_ALL,
                    UniformSQL.PARSE_PERIOD);
         findLeaf(usql.getWhere()).setOp(op);
         VariableTable vars = new VariableTable();
         vars.put("p", XConstants.CONDITION_NULL_VALUE);
         XUtil.validateConditions(null, usql, vars, true, forVpm);
         usql.clearSQLString();
         String sql = usql.getSQLString().replaceAll("\\s+", " ").trim();
         assertTrue(sql.endsWith("a.k = 1 and " + expected), sql);
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:vpm77571;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static XBinaryCondition findLeaf(XNode node) {
      if(node instanceof XBinaryCondition bin &&
         "a.id".equals(bin.getExpression1().toString().trim()))
      {
         return bin;
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         XBinaryCondition leaf = findLeaf(node.getChild(i));

         if(leaf != null) {
            return leaf;
         }
      }

      return null;
   }

   private static Connection connect() throws SQLException {
      Connection conn = DriverManager.getConnection("jdbc:derby:memory:vpm77571;create=true");

      try(Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table a");
         }
         catch(SQLException ignore) {
            // first run
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'NULL_VALUE'), (null, 3, 'x'), " +
                               "(10, null, 'y'), (11, 3, ''), (null, null, null)");
      }

      return conn;
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
}
