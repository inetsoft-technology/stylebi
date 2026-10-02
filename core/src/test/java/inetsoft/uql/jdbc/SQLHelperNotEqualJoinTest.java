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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77509, the parser records a column comparison written with != as a join with the op
 * "!=", which had no ANSI join type. An inner join ON != (or not (.. != ..)) on the
 * null-supplying side of an outer join was moved to WHERE, which removes the null-extended
 * rows. A != join in an ON must be generated like the same join written with &lt;&gt;, and a
 * != in WHERE stays in WHERE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperNotEqualJoinTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk, c.id ci, c.k ck ";
   private static final String GEN_SEL =
      "select a.id as ai, a.k as ak, b.id as bi, b.k as bk, c.id as ci, c.k as ck ";
   private static final String SEL2 = "select a.id ai, a.k ak, b.id bi, b.k bk ";

   // the defect: an inner join ON != on the null-supplying side of a RIGHT or nested outer
   // join, and its negation. The ANSI option doesn't matter since the outer join forces ANSI.
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join (b join c on b.id = c.id and b.k != c.k) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k != c.k ) RIGHT OUTER JOIN a ON " +
         "a.id = b.id",
      "from a join c on a.id = c.id and a.k != c.k right join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k != c.k ) RIGHT OUTER JOIN b ON " +
         "a.id = b.id",
      "from a left join (b join c on b.id = c.id and not (b.k != c.k)) on a.id = b.id|" +
         "from (b INNER JOIN c ON b.id = c.id AND b.k = c.k ) RIGHT OUTER JOIN a ON " +
         "a.id = b.id",
      "from a join c on a.id = c.id and not (a.k != c.k) right join b on a.id = b.id|" +
         "from (a INNER JOIN c ON a.id = c.id AND a.k = c.k ) RIGHT OUTER JOIN b ON " +
         "a.id = b.id",
   })
   void innerOnNotEqualOnNullSupplyingSide(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      assertEquals(0, RowCompare.diffCount(SEL + tail, generate(SEL + tail, dataSource("derby")),
                                           120), tail);
   }

   // the operand swap of the nested round trip is the same with = only, so it's not caused
   // by the != join
   @Test
   void nestedRoundTripSwapsOuterOperands() throws Exception {
      JDBCDataSource ds = dataSource("h2");
      String generated = generate(SEL + "from a left join (b join c on b.id = c.id) on " +
                                     "a.id = b.id", ds);
      assertEquals("from (b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id",
                   from(generated));
      assertEquals("from (b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON b.id = a.id",
                   from(generate(generated, ds)));
   }

   // every != join is generated like the same join written with <> (the op itself is kept)
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a left join (b join c on b.id = c.id and b.k != c.k) on a.id = b.id",
      SEL + "from a join c on a.id = c.id and a.k != c.k right join b on a.id = b.id",
      SEL + "from a left join (b join c on b.id = c.id and not (b.k != c.k)) on a.id = b.id",
      SEL + "from a left join b on a.id = b.id join c on a.id = c.id and a.k != c.k",
      SEL + "from a left join b on a.id = b.id join c on b.id = c.id and b.k != c.k",
      SEL2 + "from a join b on a.k != b.k",
      SEL2 + "from a join b on a.id = b.id and a.k != b.k",
      SEL2 + "from a join b on a.id = b.id and not (a.k != b.k)",
   })
   void onNotEqualLikeLessGreater(String text) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby-ansi", "postgresql-ansi",
                                       "oracle-ansi", "mongo" })
      {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(text, ds);
         String twin = generate(text.replace("!=", "<>"), ds);
         assertEquals(twin.replace("<>", "!="), generated, type);
         assertRoundTrip(generated, ds);
      }
   }

   // the reporter's two cases, already right on main since #6064/#6080: a WHERE != stays a
   // WHERE condition, with and without the ANSI option, on every helper
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join b on a.id = b.id where a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where a.k != b.k|from a, b where a.k != b.k",
      "from a, b where a.id = b.id and a.k != b.k|" +
         "from a INNER JOIN b ON a.id = b.id where a.k != b.k",
      "from a inner join b on a.id = b.id where a.k != b.k|" +
         "from a INNER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where not (a.k != b.k)|from a, b where not (a.k != b.k)",
   })
   void reporterCasesStayInWhere(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2-ansi", "derby-ansi", "mongo-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL2 + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      for(String type : new String[] { "derby", "derby-ansi" }) {
         assertEquals(0, RowCompare.diffCount(SEL2 + tail, generate(SEL2 + tail,
                                                                   dataSource(type)), 120),
                      type + ": " + tail);
      }
   }

   // a WHERE != stays in WHERE next to WHERE-syntax outer joins (*= and Oracle (+)), which
   // turn off the text join order
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a, b where a.id *= b.id and a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
      "from a, b where a.id = b.id(+) and a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
   })
   void whereOuterJoinSyntax(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL2 + tail, ds);
         assertEquals(expected, from(generated), type);
         assertRoundTrip(generated, ds);
      }

      String generated = generate(SEL2 + tail, dataSource("postgresql"));
      assertEquals("from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\" where " +
                      "\"a\".\"k\" != \"b\".\"k\"",
                   from(generated));
      assertEquals(0, RowCompare.diffCount(
         SEL2 + "from a left join b on a.id = b.id where a.k != b.k",
         generate(SEL2 + tail, dataSource("derby")), 120), tail);
   }

   // MongoHelper has no text join order, so a WHERE != between the tables of an inner join
   // and an outer join must stay in WHERE, or the inner joined table is written twice
   @Test
   void mongoWhereNotEqual() throws Exception {
      String text = SEL + "from a left join b on a.id = b.id join c on a.id = c.id where " +
         "b.k != c.k";
      String generated = generate(text, dataSource("mongo"));
      assertEquals(GEN_SEL + "from a INNER JOIN c ON a.id = c.id LEFT OUTER JOIN b ON " +
                      "a.id = b.id where b.k != c.k", generated);
      assertEquals(0, RowCompare.diffCount(text, generated, 120));
   }

   // Oracle without the ANSI option writes (+) and the != in WHERE, unchanged
   @Test
   void oracleNonAnsiUnchanged() throws Exception {
      assertEquals("select a.id as \"ai\", a.k as \"ak\", b.id as \"bi\", b.k as \"bk\" from " +
                      "a, b where a.id = b.id(+) and a.k != b.k",
                   generate(SEL2 + "from a left join b on a.id = b.id where a.k != b.k",
                            dataSource("oracle")));
      assertEquals("select a.id as \"ai\", a.k as \"ak\", b.id as \"bi\", b.k as \"bk\" from " +
                      "a, b where a.id = b.id and a.k != b.k",
                   generate(SEL2 + "from a join b on a.id = b.id and a.k != b.k",
                            dataSource("oracle")));
   }

   // the other ON != shapes return the same rows as the original
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a left join b on a.id = b.id join c on a.id = c.id and a.k != c.k",
      SEL + "from a left join b on a.id = b.id join c on b.id = c.id and b.k != c.k",
      SEL2 + "from a join b on a.k != b.k",
      SEL2 + "from a join b on a.id = b.id and not (a.k != b.k)",
   })
   void sameRowsOnDerby(String text) throws Exception {
      for(String type : new String[] { "derby", "derby-ansi" }) {
         assertEquals(0, RowCompare.diffCount(text, generate(text, dataSource(type)), 120),
                      type + ": " + text);
      }
   }

   // the from and where clauses, without the select list (Oracle quotes the aliases)
   private static String from(String generated) {
      return generated.substring(generated.indexOf(" from ") + 1);
   }

   private static JDBCDataSource dataSource(String type) {
      return RowCompare.dataSource(type);
   }

   static String generate(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the regenerated SQL re-parses to itself. The one exception is the RIGHT OUTER JOIN written
   // for a nested left join, whose ON operands are swapped once on the first round trip for
   // every op (see nestedRoundTripSwapsOuterOperands), so the second generation is checked
   // to be the fixed point
   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      String again = generate(generated, ds);

      if(!again.equals(generated)) {
         String unswapped = again.replaceAll(
            "RIGHT OUTER JOIN (\"?a\"?) ON (\"?b\"?\\.\"?id\"?) = (\"?a\"?\\.\"?id\"?)",
            "RIGHT OUTER JOIN $1 ON $3 = $2");
         assertEquals(generated, unswapped, "round trip");
         assertEquals(again, generate(again, ds), "second round trip");
      }
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   /**
    * Builds data sources with real drivers and URLs, and compares the rows of two queries on
    * random small datasets in an in-memory Derby database.
    */
   static final class RowCompare {
      static JDBCDataSource dataSource(String type) {
         JDBCDataSource ds = new JDBCDataSource();
         ds.setName("ds_" + type);
         ds.setProductVersion("19.0");

         switch(type.replace("-ansi", "")) {
         case "h2" -> {
            ds.setDriver("org.h2.Driver");
            ds.setURL("jdbc:h2:mem:test");
         }
         case "derby" -> {
            ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
            ds.setURL("jdbc:derby:memory:test;create=true");
            ds.setProductVersion("10.17");
         }
         case "postgresql" -> {
            ds.setDriver("org.postgresql.Driver");
            ds.setURL("jdbc:postgresql://localhost:5432/test");
         }
         case "oracle" -> {
            ds.setDriver("oracle.jdbc.OracleDriver");
            ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
         }
         case "mongo" -> {
            ds.setDriver("mongodb.jdbc.MongoDriver");
            ds.setURL("jdbc:mongo://localhost:27017/test");
         }
         default -> throw new IllegalArgumentException(type);
         }

         ds.setAnsiJoin(type.endsWith("-ansi"));
         String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
         assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
         return ds;
      }

      static int diffCount(String original, String generated, int datasets) throws Exception {
         Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
            .getDeclaredConstructor().newInstance();
         Random random = new Random(77509);
         Integer[] values = { null, 0, 1, 2 };
         int diff = 0;

         try(Connection con = driver.connect("jdbc:derby:memory:rows77509;create=true",
                                             new Properties());
             Statement st = con.createStatement())
         {
            for(String table : new String[] { "a", "b", "c" }) {
               try {
                  st.execute("drop table " + table);
               }
               catch(SQLException ignore) {
               }

               st.execute("create table " + table + " (id int, k int, j int)");
            }

            for(int n = 0; n < datasets; n++) {
               for(String table : new String[] { "a", "b", "c" }) {
                  st.execute("delete from " + table);

                  for(int r = random.nextInt(5); r > 0; r--) {
                     st.execute("insert into " + table + " values (" +
                                   values[random.nextInt(4)] + ", " + values[random.nextInt(4)] +
                                   ", " + values[random.nextInt(4)] + ")");
                  }
               }

               if(!rows(con, original).equals(rows(con, generated))) {
                  diff++;
               }
            }
         }

         return diff;
      }

      // rows as a sorted multiset, with columns ordered by label since regeneration can
      // reorder the select list
      private static List<String> rows(Connection con, String query) throws SQLException {
         List<String> rows = new ArrayList<>();

         try(Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            TreeMap<String, Integer> columns = new TreeMap<>();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               columns.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
            }

            while(rs.next()) {
               StringBuilder row = new StringBuilder();

               for(int i : columns.values()) {
                  row.append(rs.getObject(i)).append('|');
               }

               rows.add(row.toString());
            }
         }

         Collections.sort(rows);
         return rows;
      }
   }
}
