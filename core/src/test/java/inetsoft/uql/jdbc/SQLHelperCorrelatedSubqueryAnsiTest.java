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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77480, on the ANSI generation path (any outer join in the subquery, or a data source with
 * the ANSI join option) the correlation of a correlated subquery was moved into the subquery's
 * FROM clause. The outer table was joined inside the subquery and a join of the subquery was
 * dropped, or the correlation itself was dropped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperCorrelatedSubqueryAnsiTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperCorrelatedSubqueryAnsiTest {
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

   private static final String SEL = "select a.id ai, a.k ak from a where ";
   private static final String GEN_SEL = "select a.id as ai, a.k as ak from a where ";

   // reporter's case (A) and the other subquery forms, with and without the ANSI join option
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "exists (select 1 from b left join c on b.id = c.id where b.id = a.id)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where b.id = a.id)",
      "exists (select 1 from b left join c on b.id = c.id where a.id = b.id)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where a.id = b.id)",
      "exists (select 1 from b left join c on b.id = c.id where c.id = a.id)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where c.id = a.id)",
      "not exists (select 1 from b left join c on b.id = c.id where b.id = a.id)|" +
         "not (EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where b.id = a.id))",
      "a.id in (select b.id from b left join c on b.id = c.id where b.k = a.k)|" +
         "a.id IN ( select b.id from b LEFT OUTER JOIN c ON b.id = c.id where b.k = a.k)",
      "exists (select 1 from b left join c on b.id = c.id where b.id > a.id)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where b.id > a.id)",
      "exists (select 1 from b left join c on b.id = c.id where b.id = a.id and b.k = a.k)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where b.id = a.id and " +
         "b.k = a.k)",
      "exists (select 1 from b left join c on b.id = c.id where b.id = a.id or b.k = 1)|" +
         "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where (b.id = a.id or " +
         "b.k = 1))",
      // aliases, and an inner table with the same name as the outer table
      "exists (select 1 from b x left join c y on x.id = y.id where x.id = a.id)|" +
         "EXISTS ( select 1 from b x LEFT OUTER JOIN c y ON x.id = y.id where x.id = a.id)",
      "exists (select 1 from a a2 left join b on a2.id = b.id where a2.id = a.id)|" +
         "EXISTS ( select 1 from a a2 LEFT OUTER JOIN b ON a2.id = b.id where a2.id = a.id)",
      // quoted names, the column quotes are kept (#77558), and so are the table quotes
      // (#77569). The outer table a is not quoted, so neither is its qualifier
      "exists (select 1 from \"b\" left join \"c\" on \"b\".\"id\" = \"c\".\"id\" where " +
         "\"b\".\"id\" = \"a\".\"id\")|" +
         "EXISTS ( select 1 from \"b\" LEFT OUTER JOIN \"c\" ON \"b\".\"id\" = \"c\".\"id\" " +
         "where \"b\".\"id\" = a.\"id\")",
      // the correlation is in a derived table's outer level
      "exists (select 1 from (select b.id bid from b left join c on b.id = c.id) t where " +
         "t.bid = a.id)|EXISTS ( select 1 from ( select b.id as bid from b LEFT OUTER JOIN c " +
         "ON b.id = c.id) t where t.bid = a.id)",
   })
   void correlationStaysInWhere(String tail, String expected) throws Exception {
      for(String type : new String[] { "default", "h2-ansi", "derby" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds);
         assertEquals(GEN_SEL + expected, generated, type);
         assertEquals(generated, generate(generated, ds), "round trip " + type);
      }
   }

   // with the ANSI join option, inner-only and single-table correlated subqueries take the
   // same path: the correlation was dropped or the outer table was pulled into the subquery
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "exists (select 1 from b join c on b.id = c.id where b.id = a.id)|" +
         "EXISTS ( select 1 from b INNER JOIN c ON b.id = c.id where b.id = a.id)",
      "exists (select 1 from b where b.id = a.id)|" +
         "EXISTS ( select 1 from b where b.id = a.id)",
   })
   void ansiJoinOptionInnerOnly(String tail, String expected) throws Exception {
      for(String type : new String[] { "h2-ansi", "derby-ansi", "oracle-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(SEL + tail, ds).replace("\"", "");
         assertEquals(GEN_SEL + expected, generated, type);
      }
   }

   @Test
   void ansiJoinOptionOuterQueryCorrelatedToNullSide() throws Exception {
      assertEquals("select a.id as ai, b.k as bk from a LEFT OUTER JOIN b ON a.id = b.id where " +
                      "EXISTS ( select 1 from c where c.id = b.id)",
                   generate("select a.id ai, b.k bk from a left join b on a.id = b.id where " +
                               "exists (select 1 from c where c.id = b.id)",
                            dataSource("h2-ansi")));
   }

   // two levels deep, correlated to both outer levels, and to two outer tables
   @Test
   void nestedAndMultipleOuterTables() throws Exception {
      for(String type : new String[] { "default", "h2-ansi" }) {
         assertEquals("select a.id as ai from a where EXISTS ( select 1 from b where b.k = a.k " +
                         "and EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id where " +
                         "c.id = b.id and c.j = a.j))",
                      generate("select a.id ai from a where exists (select 1 from b where b.k = " +
                                  "a.k and exists (select 1 from c left join d on c.id = d.id " +
                                  "where c.id = b.id and c.j = a.j))", dataSource(type)));
         String from = type.equals("default") ? "from a, b where a.id = b.id and " :
            "from a INNER JOIN b ON a.id = b.id where ";
         assertEquals("select a.id as ai, b.id as bi " + from +
                         "EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id where " +
                         "c.k = a.k and c.j = b.j)",
                      generate("select a.id ai, b.id bi from a, b where a.id = b.id and exists " +
                                  "(select 1 from c left join d on c.id = d.id where c.k = a.k " +
                                  "and c.j = b.j)", dataSource(type)));
      }
   }

   // an aliased outer table, and schema-qualified names
   @Test
   void outerAliasAndSchema() throws Exception {
      assertEquals("select t.id as ai from a t where EXISTS ( select 1 from b LEFT OUTER JOIN c " +
                      "ON b.id = c.id where b.id = t.id)",
                   generate("select t.id ai from a t where exists (select 1 from b left join c " +
                               "on b.id = c.id where b.id = t.id)", null));
      assertEquals("select s.a.id as ai from s.a where EXISTS ( select 1 from s.b LEFT OUTER " +
                      "JOIN s.c ON s.b.id = s.c.id where s.b.id = s.a.id)",
                   generate("select s.a.id ai from s.a where exists (select 1 from s.b left join " +
                               "s.c on s.b.id = s.c.id where s.b.id = s.a.id)", null));
   }

   @Test
   void postgresql() throws Exception {
      String generated = generate(SEL + "exists (select 1 from b left join c on b.id = c.id " +
                                     "where b.id = a.id)", dataSource("postgresql"));
      // only the structure is asserted: the outer table's column in the correlation is not
      // quoted (a.id), which is pre-existing field quoting for a table outside this level
      assertTrue(generated.startsWith("select \"a\".\"id\" as \"ai\", \"a\".\"k\" as " +
                                         "\"ak\" from \"a\" where EXISTS ( select 1 from \"b\" " +
                                         "LEFT OUTER JOIN \"c\" ON \"b\".\"id\" = \"c\".\"id\" " +
                                         "where \"b\".\"id\" = "), generated);
      assertFalse(generated.contains("JOIN a ") || generated.contains("JOIN \"a\""), generated);
   }

   // Oracle without the ANSI option renders (+) itself, and its output is unchanged
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "exists (select 1 from b left join c on b.id = c.id where b.id = a.id)|" +
         "EXISTS ( select 1 from b, c where b.id = c.id(+) and b.id = a.id)",
      "a.id in (select b.id from b left join c on b.id = c.id where b.k = a.k)|" +
         "a.id IN ( select b.id from b, c where b.id = c.id(+) and b.k = a.k)",
      "exists (select 1 from b join c on b.id = c.id where b.id = a.id)|" +
         "EXISTS ( select 1 from b, c where b.id = c.id and b.id = a.id)",
      "exists (select 1 from b where b.id = a.id)|EXISTS ( select 1 from b where b.id = a.id)",
      "exists (select 1 from a a2 left join b on a2.id = b.id where a2.id = a.id)|" +
         "EXISTS ( select 1 from a a2, b where a2.id = b.id(+) and a2.id = a.id)",
   })
   void oracleNonAnsiUnchanged(String tail, String expected) throws Exception {
      assertEquals("select a.id as \"ai\", a.k as \"ak\" from a where " + expected,
                   generate(SEL + tail, dataSource("oracle")));
   }

   // a subquery that isn't correlated is unchanged
   @Test
   void uncorrelatedUnchanged() throws Exception {
      assertEquals(GEN_SEL + "EXISTS ( select 1 from b LEFT OUTER JOIN c ON b.id = c.id where " +
                      "b.k = 1)",
                   generate(SEL + "exists (select 1 from b left join c on b.id = c.id where " +
                               "b.k = 1)", dataSource("h2-ansi")));
   }

   // scalar subqueries (select list and where) and a FULL join inside the subquery take the
   // same path: the outer table was joined inside the subquery and the outer join dropped
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.id ai, (select count(*) from b left join c on b.id = c.id where b.id = a.id) " +
         "cnt from a|select (select count(*) from b LEFT OUTER JOIN c ON b.id = c.id where " +
         "b.id = a.id ) as cnt, a.id as ai from a|true",
      "select a.id ai from a where (select count(*) from b left join c on b.id = c.id where " +
         "b.id = a.id) > 1|select a.id as ai from a where ( select count(*) from b LEFT OUTER " +
         "JOIN c ON b.id = c.id where b.id = a.id) > 1|true",
      "select a.id ai from a where exists (select 1 from b full outer join c on b.id = c.id " +
         "where b.id = a.id)|select a.id as ai from a where EXISTS ( select 1 from b FULL OUTER " +
         "JOIN c ON b.id = c.id where b.id = a.id)|false",
   })
   void scalarAndFullJoinSubqueries(String text, String expected, boolean rows)
      throws Exception
   {
      for(String type : new String[] { "default", "h2-ansi", "derby" }) {
         JDBCDataSource ds = dataSource(type);
         String generated = generate(text, ds);
         assertEquals(expected, generated, type);
         assertEquals(generated, generate(generated, ds), "round trip " + type);
      }

      // Derby has no FULL join
      if(rows) {
         assertEquals(0, diffCount(text, generate(text, dataSource("derby")), 120), text);
      }
   }

   // row comparison on Derby: the original and the regenerated SQL return the same rows
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "exists (select 1 from b left join c on b.id = c.id where b.id = a.id)",
      SEL + "exists (select 1 from b left join c on b.id = c.id where c.id = a.id)",
      SEL + "a.id in (select b.id from b left join c on b.id = c.id where b.k = a.k)",
      SEL + "exists (select 1 from b join c on b.id = c.id where b.id = a.id)",
      SEL + "exists (select 1 from b where b.id = a.id)",
      SEL + "exists (select 1 from a a2 left join b on a2.id = b.id where a2.id = a.id)",
      // Bug #77440, a correlation and a filter of the null side, and a right join
      SEL + "not exists (select 1 from c left join d on d.id = c.id where a.id = c.id and " +
         "d.k = 1)",
      SEL + "exists (select 1 from c right join d on d.id = c.id where d.id = a.id)",
      "select a.id ai from a where exists (select 1 from b where b.k = a.k and exists " +
         "(select 1 from c left join d on c.id = d.id where c.id = b.id and c.j = a.j))",
   })
   void sameRowsOnDerby(String text) throws Exception {
      assertEquals(0, diffCount(text, generate(text, dataSource("derby-ansi")), 120), text);
   }

   private static String generate(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a RIGHT or FULL join mixed with an inner join (here the
      // correlation) without a data source
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // a data source with a real driver and URL, so the right SQL helper is used
   private static JDBCDataSource dataSource(String type) {
      if(type.equals("default")) {
         return null;
      }

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
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   // number of random small datasets (with nulls) on which the two queries return different
   // rows, in an in-memory Derby database
   private static int diffCount(String original, String generated, int datasets)
      throws Exception
   {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      Random random = new Random(77480);
      Integer[] values = { null, 0, 1, 2 };
      String[] tables = { "a", "b", "c", "d" };
      int diff = 0;

      try(Connection con = driver.connect("jdbc:derby:memory:rows77480;create=true",
                                          new Properties());
          Statement st = con.createStatement())
      {
         for(String table : tables) {
            try {
               st.execute("drop table " + table);
            }
            catch(SQLException ignore) {
            }

            st.execute("create table " + table + " (id int, k int, j int)");
         }

         for(int n = 0; n < datasets; n++) {
            for(String table : tables) {
               st.execute("delete from " + table);

               for(int r = random.nextInt(5); r > 0; r--) {
                  st.execute("insert into " + table + " values (" + values[random.nextInt(4)] +
                                ", " + values[random.nextInt(4)] + ", " +
                                values[random.nextInt(4)] + ")");
               }
            }

            if(!rows(con, original).equals(rows(con, generated))) {
               diff++;
            }
         }
      }

      return diff;
   }

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
