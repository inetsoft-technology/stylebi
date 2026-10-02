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
import inetsoft.uql.jdbc.util.JDBCUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
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
 * Bug #77510, JDBCUtil.fixWhereInfo turns a join under an OR into a plain condition, and
 * must keep its negation: "a.k = 1 or not (a.id = b.k)" must not become
 * "a.k = 1 or a.id = b.k".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCUtilFixFakeJoinsNotTest {
   private static final String SELECT = "select a.id, a.k, b.id, b.k from ";

   // the original sql and the where clause regenerated after fixWhereInfo
   static Stream<Arguments> negatedJoinUnderOr() {
      return Stream.of(
         Arguments.of(SELECT + "a, b where a.k = 1 or not (a.id = b.k)",
                      "where (a.k = 1 or not (a.id = b.k))"),
         Arguments.of(SELECT + "a left join b on a.id = b.id where a.k = 1 or not (a.id = b.k)",
                      "where (a.k = 1 or not (a.id = b.k))"),
         Arguments.of(SELECT + "a, b where not (a.id = b.k) or a.k = 1",
                      "where (not (a.id = b.k) or a.k = 1)"),
         Arguments.of(SELECT + "a, b where a.k = 1 or not a.id < b.k",
                      "where (a.k = 1 or not (a.id < b.k))"),
         Arguments.of(SELECT + "a, b where a.k = 2 and (a.k = 1 or not (a.id = b.k))",
                      "where a.k = 2 and (a.k = 1 or not (a.id = b.k))"),
         Arguments.of(SELECT + "a, b where a.k = 1 or (b.k = 2 and not (a.id = b.k))",
                      "where (a.k = 1 or (b.k = 2 and not (a.id = b.k)))"),
         Arguments.of(SELECT + "a, b, c where a.id = c.id and (a.k = 1 or not (a.id = b.k))",
                      "where a.id = c.id and (a.k = 1 or not (a.id = b.k))"));
   }

   // shapes fixWhereInfo already handled, they must not change
   static Stream<String> controls() {
      return Stream.of(
         SELECT + "a, b where a.k = 1 or a.id = b.k",
         SELECT + "a, b where a.k = 1 or a.id <> b.k",
         SELECT + "a, b where a.k = 1 or not (a.id = b.k or b.k = 2)",
         SELECT + "a, b where not (a.id = b.k)",
         SELECT + "a, b where a.k = 1 and not (a.id = b.k)",
         SELECT + "a left join b on a.id = b.id where a.k = 1 or a.id = b.k");
   }

   @ParameterizedTest
   @MethodSource("negatedJoinUnderOr")
   void negationIsKept(String text, String where) throws Exception {
      String generated = fixAndRegenerate(text);
      assertTrue(generated.endsWith(" " + where), generated);
      assertEquals(regenerate(text), generated);
      // round trip, re-applying fixWhereInfo as fixUniformSQLInfo does after every parse
      assertEquals(generated, fixAndRegenerate(generated));
      assertSameRows(text, generated);
   }

   @ParameterizedTest
   @MethodSource("controls")
   void otherShapesUnchanged(String text) throws Exception {
      String generated = fixAndRegenerate(text);
      assertEquals(regenerate(text), generated);
      assertEquals(generated, fixAndRegenerate(generated));
      assertSameRows(text, generated);
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77510;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static void assertSameRows(String text, String generated) throws SQLException {
      Random random = new Random(77510);

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77510;create=true")) {
         createTables(conn);

         for(int i = 0; i < 200; i++) {
            fillTables(conn, random);
            assertEquals(rows(conn, text), rows(conn, generated),
                         "dataset " + i + "\noriginal: " + text + "\ngenerated: " + generated);
         }
      }
   }

   private static final String[] TABLES = { "a", "b", "c" };

   private static void createTables(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (id int, k int)");
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
               for(int col = 1; col <= 2; col++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(col, Types.INTEGER);
                  }
                  else {
                     insert.setInt(col, value);
                  }
               }

               insert.addBatch();
            }

            insert.executeBatch();
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

   // the tree is only exposed once the stored sql string is cleared
   private static String regenerate(String text) throws Exception {
      UniformSQL sql = parse(text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String fixAndRegenerate(String text) throws Exception {
      UniformSQL sql = parse(text);
      JDBCUtil.fixWhereInfo(sql);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
