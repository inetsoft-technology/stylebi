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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77488, on the ANSI generation path (any outer join, or a data source with the ANSI join
 * option) a column comparison whose tables are not both in the FROM clause (a bare column, an
 * unknown qualifier, a table name hidden by its alias, a bare name for a schema-qualified table)
 * was moved into the FROM clause. The missing side was written as an empty or invented table
 * ("from INNER JOIN ON x = y , a"), or, with no outer join, the comparison was dropped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperUnresolvedInnerJoinAnsiTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperUnresolvedInnerJoinAnsiTest {
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

   // the reported shapes and the variants of an unresolved side, as input|ANSI output|non-ANSI
   // output. A comparison with an unresolved side stays a where condition.
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // reported shapes
      "select x from a where x = y|select x from a where x = y|select x from a where x = y",
      "select a.x from a, b where id = bid|select a.x from a, b where id = bid|" +
         "select a.x from a, b where id = bid",
      "select a.x from a left join b on a.id = b.id where x = y|" +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id where x = y|" +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id where x = y",
      "select a.x from a left join b on a.id = b.id join c on cid = id|" +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id , c where cid = id|" +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id , c where cid = id",
      // no outer join: the comparison was dropped
      "select a.x from a join b on a.id = b.id where x = y|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where x = y|" +
         "select a.x from a, b where a.id = b.id and x = y",
      "select a.x from a, b where a.id = b.id and x = y|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where x = y|" +
         "select a.x from a, b where a.id = b.id and x = y",
      "select a.x from a join b on a.id = b.id where x = y or a.z = 1|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where (x = y or a.z = 1)|" +
         "select a.x from a, b where a.id = b.id and (x = y or a.z = 1)",
      "select a.x from a join b on a.id = b.id where not (x = y)|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where not (x = y)|" +
         "select a.x from a, b where a.id = b.id and not (x = y)",
      "select a.x from a join b on a.id = b.id where x <> y|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where x <> y|" +
         "select a.x from a, b where a.id = b.id and x <> y",
      // unknown qualifier
      "select a.x from a, b where a.id = zz.id|select a.x from a, b where a.id = zz.id|" +
         "select a.x from a, b where a.id = zz.id",
      "select a.x from a join b on a.id = b.id where a.k = zz.k|" +
         "select a.x from a INNER JOIN b ON a.id = b.id where a.k = zz.k|" +
         "select a.x from a, b where a.id = b.id and a.k = zz.k",
      // a table name hidden by its alias, and a bare name for a schema-qualified table
      "select t1.x from a t1, b where a.id = b.id|select t1.x from a t1, b where a.id = b.id|" +
         "select t1.x from a t1, b where a.id = b.id",
      "select a.x from s.a, b where a.id = b.id|select a.x from s.a, b where a.id = b.id|" +
         "select a.x from s.a, b where a.id = b.id",
      // half qualified, alias qualified and quoted
      "select a.x from a join b on a.id = bid|select a.x from a, b where a.id = bid|" +
         "select a.x from a, b where a.id = bid",
      "select a.x from a, b where a.id = bid|select a.x from a, b where a.id = bid|" +
         "select a.x from a, b where a.id = bid",
      "select t1.x from a t1, b t2 where t1.id = id|" +
         "select t1.x from a t1, b t2 where t1.id = id|" +
         "select t1.x from a t1, b t2 where t1.id = id",
      "select \"a\".\"x\" from \"a\", \"b\" where \"a\".\"id\" = bid|" +
         "select a.x from a, b where a.id = bid|select a.x from a, b where a.id = bid",
      // other operators
      "select a.x from a, b where id > bid|select a.x from a, b where id > bid|" +
         "select a.x from a, b where id > bid",
      // derived table
      "select t.x from (select x from a) t, b where t.x = y|" +
         "select t.x from ( select x from a) t, b where t.x = y|" +
         "select t.x from ( select x from a) t, b where t.x = y",
      // an unresolved ON with no outer join
      "select a.x from a join b on a.id = b.id join c on cid = id|" +
         "select a.x from a INNER JOIN b ON a.id = b.id , c where cid = id|" +
         "select a.x from a, b, c where a.id = b.id and cid = id",
      // controls: resolved comparisons are still joins
      "select a.x from a, b where a.id < b.id|select a.x from a INNER JOIN b ON a.id < b.id|" +
         "select a.x from a, b where a.id < b.id",
      "select a.x from a join b on a.id = b.id join c on c.id = a.id where x = y|" +
         "select a.x from (a INNER JOIN b ON a.id = b.id ) INNER JOIN c ON c.id = a.id " +
         "where x = y|select a.x from a, b, c where a.id = b.id and c.id = a.id and x = y",
   })
   void unresolvedComparisonStaysInWhere(String text, String ansiExpected,
                                         String plainExpected) throws Exception
   {
      for(boolean ansi : new boolean[] { true, false }) {
         JDBCDataSource ds = dataSource(ansi ? "h2-ansi" : "h2");
         String generated = generate(text, ds);
         assertEquals(ansi ? ansiExpected : plainExpected, generated, "ansi=" + ansi);
         assertEquals(generated, generate(generated, ds), "round trip, ansi=" + ansi);
      }
   }

   // row comparison on Derby, on tables a(aid, ax), b(bid, bx), c(cid), d(did). The silent
   // drop cases returned different rows, the others failed to run
   @ParameterizedTest
   @ValueSource(strings = {
      // silent drops
      "select a.ax from a join b on a.aid = b.bid where ax = bx",
      "select a.ax from a, b where a.aid = b.bid and ax = bx",
      "select a.ax from a join b on a.aid = b.bid where not (ax = bx)",
      "select a.ax from a join b on a.aid = b.bid where ax <> bx",
      "select a.ax from a join b on a.aid = b.bid where ax = bx or a.aid = 1",
      // invalid sql
      "select ax from a where ax = aid",
      "select a.ax from a, b where aid = bid",
      "select a.ax, b.bx from a left join b on a.aid = b.bid join c on cid = aid",
      "select a.ax from a join b on a.aid = bid",
      "select a.ax from a, b where aid < bid",
      "select a.ax from a join b on a.aid = b.bid join c on cid = aid",
      // already right
      "select a.ax from a left join b on a.aid = b.bid where ax = bx",
   })
   void sameRowsOnDerby(String text) throws Exception {
      String generated = generate(text, dataSource("derby-ansi"));
      assertEquals(0, RowCompare.diffCount(text, generated, 200), text + " -> " + generated);
   }

   static String generate(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77488_" + type);
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
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
   }

   /**
    * Compares the rows of two queries on random small datasets in an in-memory Derby database.
    * The tables have distinct column names, so a bare column is not ambiguous.
    */
   static final class RowCompare {
      private static final String[][] TABLES = {
         { "a", "aid", "ax" }, { "b", "bid", "bx" }, { "c", "cid" }, { "d", "did" } };

      static int diffCount(String original, String generated, int datasets) throws Exception {
         Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
            .getDeclaredConstructor().newInstance();
         Random random = new Random(77488);
         Integer[] values = { null, 0, 1, 2, 3 };
         int diff = 0;

         try(Connection con = driver.connect("jdbc:derby:memory:rows77488;create=true",
                                             new Properties());
             Statement st = con.createStatement())
         {
            for(String[] table : TABLES) {
               try {
                  st.execute("drop table " + table[0]);
               }
               catch(SQLException ignore) {
               }

               st.execute("create table " + table[0] + " (" +
                          String.join(" int, ", Arrays.copyOfRange(table, 1, table.length)) +
                          " int)");
            }

            for(int n = 0; n < datasets; n++) {
               for(String[] table : TABLES) {
                  st.execute("delete from " + table[0]);

                  for(int r = random.nextInt(5); r > 0; r--) {
                     StringJoiner row = new StringJoiner(", ");

                     for(int c = 1; c < table.length; c++) {
                        row.add(String.valueOf(values[random.nextInt(values.length)]));
                     }

                     st.execute("insert into " + table[0] + " values (" + row + ")");
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
      static List<String> rows(Connection con, String query) throws SQLException {
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
