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
import inetsoft.uql.XRepository;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77581, random join shapes parsed with a MongoDB data source. SQLHelperMongoJoinOrderTest
 * pins the report's shapes; this covers the shapes around them: 3 and 4 tables, inner, left
 * and right joins on random earlier tables, comparisons in inner ONs and in WHERE (=, &lt;&gt;,
 * &lt;, !=), and a parenthesized right operand. Before the fix 13 of these shapes returned
 * wrong rows on MongoHelper (the outer-last walk), and their regenerated sql often didn't
 * parse again. A shape that parses must now return the same rows when regenerated, regenerate
 * to a fixed point, and have no parentheses in the from clause unless the text had them,
 * since the unity driver rejects them (56305).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperMongoRandomJoinOrderTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class SQLHelperMongoRandomJoinOrderTest {
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String DB = "jdbc:derby:memory:bug77581random";
   private static final String[] TABLES = { "a", "b", "c", "d" };
   private static final String[] JOINS = { "join", "left join", "left join", "right join" };
   private static final String[] OPS = { "=", "<>", "<", "!=" };

   @Test
   void randomShapesKeepTheRows() throws Exception {
      // the shapes must mostly parse, or the test checks nothing
      int parsed = checkShapes(new Random(4242), false);
      assertTrue(parsed > 700, "parsed " + parsed);
   }

   // a join of a group of tables and a parenthesized group, which MongoHelper writes flat
   // when that keeps the meaning and otherwise with the parentheses of the text only
   @Test
   void nestedGroupShapesKeepTheRows() throws Exception {
      int parsed = checkShapes(new Random(9191), true);
      assertTrue(parsed > 600, "parsed " + parsed);
   }

   private int checkShapes(Random random, boolean nestedOnly) throws Exception {
      int parsed = 0;

      for(int n = 0; n < 400; n++) {
         String text = randomQuery(random, nestedOnly);

         for(boolean ansi : new boolean[] { false, true }) {
            JDBCDataSource ds = mongo(ansi);
            UniformSQL sql = new UniformSQL();
            sql.setDataSource(ds);

            try {
               sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
            }
            catch(Exception ex) {
               // a refused parse runs the original sql, which is always right
               continue;
            }

            if(sql.getParseResult() != UniformSQL.PARSE_SUCCESS || sql.isLossy()) {
               continue;
            }

            parsed++;
            sql.clearSQLString();
            String generated = normalize(sql.getSQLString());
            String message = "ansi=" + ansi + "\ntext: " + text + "\ngenerated: " + generated;

            // no parentheses beyond those of the text, which the unity driver rejects (56305)
            assertTrue(fromParentheses(generated) <= fromParentheses(text), message);

            String again = generate(generated, ds);
            assertEquals(again, generate(again, ds), message);
            assertSameRows(text, generated, message);
         }
      }

      return parsed;
   }

   // the number of opening parentheses in the from clause
   private static long fromParentheses(String sql) {
      String from = sql.substring(sql.indexOf(" from "));
      int where = from.indexOf(" where ");
      return (where < 0 ? from : from.substring(0, where)).chars().filter(c -> c == '(').count();
   }

   private static String randomQuery(Random random, boolean nestedOnly) {
      int tables = nestedOnly ? 4 : 3 + random.nextInt(2);
      boolean nested = tables == 4 && random.nextInt(nestedOnly ? 2 : 4) == 0;
      // a parenthesized left group, e.g. (a left join b on ..) join (c join d on ..) on ..
      boolean leftGroup = nested && nestedOnly && random.nextBoolean();
      StringBuilder query = new StringBuilder(tables == 3 ? "select a.id, b.id, c.id " :
                                              "select a.id, b.id, c.id, d.id ")
         .append(leftGroup ? "from (a" : "from a");

      for(int i = 1; i < tables; i++) {
         String table = TABLES[i];

         if(nested && i == 2 && nestedOnly) {
            // any join type in the group and the step, an on clause on either table of the
            // group, and sometimes a comparison of the group's other table
            query.append(leftGroup ? ")" : "").append(" ")
               .append(JOINS[random.nextInt(JOINS.length)]).append(" (c ")
               .append(JOINS[random.nextInt(JOINS.length)]).append(" d on c.id = d.id) on ")
               .append(TABLES[random.nextInt(2)]).append(".id = ")
               .append(random.nextBoolean() ? "c" : "d").append(".id");

            if(random.nextInt(3) == 0) {
               query.append(" and ").append(TABLES[random.nextInt(2)]).append(".k = ")
                  .append(TABLES[2 + random.nextInt(2)]).append(".k");
            }

            break;
         }

         if(nested && i == 2) {
            // a parenthesized join of c and d on the right of a join
            String inner = random.nextBoolean() ? "join" : "left join";
            String right = random.nextBoolean() ? "c" : "d";
            query.append(" ").append(JOINS[random.nextInt(JOINS.length)]).append(" (c ")
               .append(inner).append(" d on c.id = d.id) on ").append(TABLES[random.nextInt(2)])
               .append(".id = ").append(right).append(".id");
            break;
         }

         String join = JOINS[random.nextInt(JOINS.length)];
         query.append(" ").append(join).append(" ").append(table).append(" on ")
            .append(TABLES[random.nextInt(i)]).append(".id = ").append(table).append(".id");

         // an outer join ON can only have column = column conditions (#77410)
         if(join.equals("join") && random.nextInt(2) == 0) {
            query.append(" and ").append(TABLES[random.nextInt(i)]).append(".k ")
               .append(OPS[random.nextInt(OPS.length)]).append(" ").append(table).append(".k");
         }
      }

      if(random.nextBoolean()) {
         int x = random.nextInt(tables);
         int y = (x + 1 + random.nextInt(tables - 1)) % tables;
         query.append(" where ").append(TABLES[x]).append(".k ")
            .append(OPS[random.nextInt(OPS.length)]).append(" ").append(TABLES[y]).append(".k");
      }

      return query.toString();
   }

   @BeforeAll
   static void createTables() throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB + ";create=true");
          Statement stmt = conn.createStatement())
      {
         for(String table : TABLES) {
            stmt.executeUpdate("create table " + table + " (id int, k int)");
         }
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

   private static JDBCDataSource mongo(boolean ansi) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_mongo" + (ansi ? "_ansi" : ""));
      ds.setProductVersion("19.0");
      ds.setDriver("mongodb.jdbc.MongoDriver");
      ds.setURL("jdbc:mongo://localhost:27017/test");
      ds.setAnsiJoin(ansi);
      assertEquals("mongo", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static String generate(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   // runs both queries on random data with nulls, and requires the same rows from both
   private static void assertSameRows(String expected, String generated, String message)
      throws SQLException
   {
      Random random = new Random(77581);

      try(Connection conn = DriverManager.getConnection(DB)) {
         for(int i = 0; i < 60; i++) {
            fillTables(conn, random);
            assertEquals(rows(conn, expected), rows(conn, generated),
                         "dataset " + i + "\n" + message);
         }
      }
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }

      for(String table : TABLES) {
         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?)"))
         {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               for(int column = 1; column <= 2; column++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(column, Types.INTEGER);
                  }
                  else {
                     insert.setInt(column, value);
                  }
               }

               insert.executeUpdate();
            }
         }
      }
   }

   // the result rows as a sorted multiset
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= columns; i++) {
               row.append(result.getObject(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }
}
