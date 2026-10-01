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
import inetsoft.util.Tool;
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

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77479, on the ANSI generation path (any outer join, or a data source with the ANSI join
 * option) a column comparison between two tables under an OR or NOT in WHERE, or in HAVING, was
 * moved into the FROM clause and the OR/NOT/HAVING was lost. Also #77509, a top-level !=
 * comparison was dropped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperWhereOrOuterJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperWhereOrOuterJoinTest {
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

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk, b.j bj ";
   private static final String GEN_SEL =
      "select a.id as ai, a.k as ak, b.id as bi, b.j as bj, b.k as bk ";

   // reporter's case (O2) and the OR/NOT shapes, on an outer-joined pair
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join b on a.id = b.id where a.id = b.k or a.k = 1|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1)",
      "from a left join b on a.id = b.id where a.k = 1 or a.id = b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.k = 1 or a.id = b.k)",
      "from a right join b on a.id = b.id where a.id = b.k or a.k = 1|" +
         "from a RIGHT OUTER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1)",
      "from a left join b on a.id = b.id where a.id < b.k or a.k = 1|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.id < b.k or a.k = 1)",
      "from a left join b on a.id = b.id where not (a.id = b.k or a.k = 1)|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (not (a.id = b.k or a.k = 1))",
      "from a left join b on a.id = b.id where not (a.id = b.k and a.k = 1)|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where not (a.id = b.k and a.k = 1)",
      "from a left join b on a.id = b.id where a.k = 2 or (a.id = b.k and b.j = 1)|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.k = 2 or (a.id = b.k and b.j = 1))",
      "from a left join b on a.id = b.id where (a.id = b.k or a.k = 1) and b.j = 1|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1) and b.j = 1",
      "from a left join b on a.id = b.id where a.id = b.k or a.k = b.j|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.id = b.k or a.k = b.j)",
      "from a left join b on a.id = b.id where a.k = 1 or not (a.id = b.k)|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.k = 1 or not (a.id = b.k))",
      // #77509, a top-level != was dropped since it has no ANSI join type
      "from a left join b on a.id = b.id where a.k != b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where a.k != b.k",
      "from a left join b on a.id = b.id where a.k != b.k or a.k = 1|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.k != b.k or a.k = 1)",
   })
   void orAndNotComparisonsStayInWhere(String tail, String expected) throws Exception {
      for(boolean ansi : new boolean[] { false, true }) {
         String generated = generate(SEL + tail, ansi ? h2(true) : null);
         assertEquals(GEN_SEL + expected, generated, "ansi=" + ansi);
         assertRoundTrip(generated, ansi ? h2(true) : null);
      }
   }

   // with the ANSI join option, inner-only queries take the same path (O1, O3, comma OR)
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a join b on a.id = b.id where a.id = b.k or a.k = 1|" +
         "from a INNER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1)",
      "from a, b where a.id = b.id and (a.id = b.k or a.k = 1)|" +
         "from a INNER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1)",
      "from a, b where a.id = b.k or a.k = 1|from a, b where (a.id = b.k or a.k = 1)",
      "from a, b where a.k != b.k|from a, b where a.k != b.k",
   })
   void ansiJoinOptionInnerOnly(String tail, String expected) throws Exception {
      String generated = generate(SEL + tail, h2(true));
      assertEquals(GEN_SEL + expected, generated);
      assertRoundTrip(generated, h2(true));
   }

   // a table linked only by an OR'd comparison stays a comma item after the join chain (X1),
   // and a table is no longer emitted twice (DUPC)
   @Test
   void orLinkedTableIsCommaItem() throws Exception {
      for(boolean ansi : new boolean[] { false, true }) {
         assertEquals("select a.id as ai, a.k as ak, b.id as bi, c.k as ck from a LEFT OUTER " +
                         "JOIN b ON a.id = b.id , c where (a.id = c.k or c.j = 1)",
                      generate("select a.id ai, a.k ak, b.id bi, c.k ck from a left join b on " +
                                  "a.id = b.id, c where a.id = c.k or c.j = 1", ds(ansi)));
         assertEquals("select a.id as ai, b.j as bj, c.k as ck from (a LEFT OUTER JOIN b ON " +
                         "a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id where (a.k = c.k or " +
                         "b.j = 1)",
                      generate("select a.id ai, b.j bj, c.k ck from a left join b on a.id = " +
                                  "b.id left join c on b.id = c.id where a.k = c.k or b.j = 1",
                               ds(ansi)));
      }
   }

   // HAVING comparisons were moved into vJoin after FROM was generated, and disappeared
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join b on a.id = b.id group by a.id, a.k, b.k having a.k = b.k|false|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id group by a.id, a.k, b.k having a.k = b.k",
      "from a left join b on a.id = b.id group by a.id, a.k, b.k having a.k = b.k or a.k = 1|" +
         "false|from a LEFT OUTER JOIN b ON a.id = b.id group by a.id, a.k, b.k having " +
         "a.k = b.k or a.k = 1",
      "from a, b where a.id = b.id group by a.id, a.k, b.k having a.k = b.k|true|" +
         "from a INNER JOIN b ON a.id = b.id group by a.id, a.k, b.k having a.k = b.k",
   })
   void havingComparisonKept(String tail, boolean ansi, String expected) throws Exception {
      String generated = generate("select a.id ai, a.k ak " + tail, ds(ansi));
      assertEquals("select a.id as ai, a.k as ak " + expected, generated);
      assertRoundTrip(generated, ds(ansi));
   }

   // the OR in a derived table is kept too, each level is generated by its own helper
   @Test
   void derivedTable() throws Exception {
      assertEquals("select t.ai from ( select a.id as ai from a LEFT OUTER JOIN b ON " +
                      "a.id = b.id where (a.id = b.k or a.k = 1)) t",
                   generate("select t.ai from (select a.id ai from a left join b on a.id = " +
                               "b.id where a.id = b.k or a.k = 1) t", null));
   }

   // real driver/URL data sources: each dialect keeps the OR in WHERE
   @ParameterizedTest
   @ValueSource(strings = { "h2", "derby", "postgresql", "oracle-ansi" })
   void dialects(String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      String generated = generate(SEL + "from a left join b on a.id = b.id where a.id = b.k " +
                                     "or a.k = 1", ds);
      String expected = type.equals("postgresql") ?
         "from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = \"b\".\"id\" where (\"a\".\"id\" = " +
            "\"b\".\"k\" or \"a\".\"k\" = 1)" :
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.id = b.k or a.k = 1)";
      assertTrue(generated.endsWith(expected), generated);
      assertRoundTrip(generated, ds);
   }

   // Oracle without the ANSI option renders (+) itself and is unchanged
   @Test
   void oracleNonAnsiUnchanged() throws Exception {
      assertEquals("select a.id as \"ai\", a.k as \"ak\", b.id as \"bi\", b.j as \"bj\", " +
                      "b.k as \"bk\" from a, b where a.id = b.id(+) and (a.id = b.k or a.k = 1)",
                   generate(SEL + "from a left join b on a.id = b.id where a.id = b.k or " +
                               "a.k = 1", dataSource("oracle")));
   }

   // controls that were already right are unchanged, including the #77478 fold
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "from a left join b on a.id = b.id where a.k = 1 or b.k = 2|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id where (a.k = 1 or b.k = 2)",
      "from a left join b on a.id = b.id and a.k = b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k",
      "from a left join b on a.id = b.id where a.k = b.k|" +
         "from a LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k",
      "from a, b where not (a.id = b.k)|from a INNER JOIN b ON a.id <> b.k",
   })
   void controlsUnchanged(String tail, String expected) throws Exception {
      assertEquals(GEN_SEL + expected, generate(SEL + tail, h2(true)));
   }

   // joins built by UniformSQL.addJoin with the 'or' merging (physical model join groups)
   // are still written into ON, fresh and after an XML round trip
   @Test
   void orMergedModelJoinGroups() throws Exception {
      UniformSQL group = model("a", "b");
      group.addJoin(join("a.id", "*=", "b.id"), XSet.AND);
      group.addJoin(join("a.k", "*=", "b.k"), XSet.OR);
      group.addJoin(join("a.k", "*=", "b.j"), XSet.OR);
      assertModel("select a.id, b.k from a LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k OR " +
                     "a.k = b.j", group);

      UniformSQL inner = model("a", "b");
      inner.addJoin(join("a.id", "=", "b.id"), XSet.AND);
      inner.addJoin(join("a.k", "=", "b.k"), XSet.OR);
      inner.addJoin(join("a.k", "=", "b.j"), XSet.OR);
      assertModel("select a.id, b.k from a INNER JOIN b ON a.id = b.id AND a.k = b.k OR " +
                     "a.k = b.j", inner);

      UniformSQL single = model("a", "b");
      single.addJoin(join("a.id", "*=", "b.id"), XSet.OR);
      assertModel("select a.id, b.k from a LEFT OUTER JOIN b ON a.id = b.id", single);

      // an 'or' join on a new pair of tables puts the whole where tree in an or set
      UniformSQL newPair = model("a", "b", "c");
      newPair.addJoin(join("a.id", "*=", "b.id"), XSet.AND);
      newPair.addJoin(join("b.k", "=", "c.k"), XSet.OR);
      assertModel("select a.id, b.k from (a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c " +
                     "ON b.k = c.k", newPair);

      UniformSQL newPairInner = model("a", "b", "c");
      newPairInner.addJoin(join("a.id", "=", "b.id"), XSet.AND);
      newPairInner.addJoin(join("b.k", "=", "c.k"), XSet.OR);
      assertModel("select a.id, b.k from (b INNER JOIN c ON b.k = c.k ) INNER JOIN a ON " +
                     "a.id = b.id", newPairInner);
   }

   // row comparison on Derby: the original and the regenerated SQL return the same rows
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a left join b on a.id = b.id where a.id = b.k or a.k = 1",
      SEL + "from a left join b on a.id = b.id where not (a.id = b.k and a.k = 1)",
      SEL + "from a left join b on a.id = b.id where a.k != b.k",
      SEL + "from a, b where a.id = b.k or a.k = 1",
      "select a.id ai, a.k ak, b.id bi, c.k ck from a left join b on a.id = b.id, c where " +
         "a.id = c.k or c.j = 1",
      "select a.id ai, a.k ak from a left join b on a.id = b.id group by a.id, a.k, b.k " +
         "having a.k = b.k",
   })
   void sameRowsOnDerby(String text) throws Exception {
      JDBCDataSource ds = dataSource("derby-ansi");
      assertEquals(0, RowCompare.diffCount(text, generate(text, ds), 120), text);
   }

   private static void assertModel(String expected, UniformSQL model) throws Exception {
      for(boolean xml : new boolean[] { false, true }) {
         UniformSQL sql = xml ? xmlRoundTrip(model) : (UniformSQL) model.clone();
         sql.setDataSource(h2(true));
         sql.clearSQLString();
         assertEquals(expected, normalize(sql.getSQLString()), "xml=" + xml);
      }
   }

   private static UniformSQL xmlRoundTrip(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      return loaded;
   }

   private static UniformSQL model(String... tables) {
      UniformSQL sql = new UniformSQL();

      for(String table : tables) {
         sql.addTable(table);
      }

      sql.getSelection().addColumn("a.id");
      sql.getSelection().addColumn("b.k");
      return sql;
   }

   private static XJoin join(String left, String op, String right) {
      return new XJoin(new XExpression(left, XExpression.FIELD),
                       new XExpression(right, XExpression.FIELD), op);
   }

   private static JDBCDataSource ds(boolean ansi) {
      return ansi ? h2(true) : null;
   }

   private static JDBCDataSource h2(boolean ansi) {
      return RowCompare.dataSource(ansi ? "h2-ansi" : "h2");
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

   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      assertEquals(generated, generate(generated, ds), "round trip");
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
         Random random = new Random(77479);
         Integer[] values = { null, 0, 1, 2 };
         int diff = 0;

         try(Connection con = driver.connect("jdbc:derby:memory:rows77479;create=true",
                                             new Properties());
             Statement st = con.createStatement())
         {
            for(String table : new String[] { "a", "b", "c", "d" }) {
               try {
                  st.execute("drop table " + table);
               }
               catch(SQLException ignore) {
               }

               st.execute("create table " + table + " (id int, k int, j int)");
            }

            for(int n = 0; n < datasets; n++) {
               for(String table : new String[] { "a", "b", "c", "d" }) {
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
               columns.put(meta.getColumnLabel(i).toLowerCase(), i);
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
