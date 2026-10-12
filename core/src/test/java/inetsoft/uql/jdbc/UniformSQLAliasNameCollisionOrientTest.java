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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77547, an outer join whose joined table's name equals an earlier table's alias
 * (from s.b a left join a x on a.id = x.pid) was recorded with the joined table first
 * (x.pid *= a.id), so every regeneration preserved the wrong side. The parser looked up the
 * outer join type by the ON qualifier string, and the joined table was keyed by its alias and
 * its name, so the earlier table's alias a hit the entry of the joined table a. The same
 * happened in a self join (#77516). Fixed by #6034, which orients an outer join by the FROM
 * index each qualifier resolves to.
 * <p>
 * The tests assert the recorded joins and the rows of the regenerated sql, not the regenerated
 * FROM text, which may change with the text-order join step (#77546).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLAliasNameCollisionOrientTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLAliasNameCollisionOrientTest {
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

   private static final String[] TABLES = { "a", "b", "c", "s.b" };
   private static final int DATASETS = 100;
   private static Connection con;

   @BeforeAll
   static void createTables() throws Exception {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      con = driver.connect("jdbc:derby:memory:bug77547;create=true", new Properties());

      try(Statement st = con.createStatement()) {
         st.execute("create schema s");

         for(String table : TABLES) {
            st.execute("create table " + table + " (id int, pid int, k int)");
         }
      }
   }

   @AfterAll
   static void dropDatabase() throws Exception {
      if(con != null) {
         con.close();
      }

      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77547;drop=true").close();
      }
      catch(SQLException ignore) {
         // dropping an in-memory database always ends with an exception
      }
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // reporter's query, the ON in both orders, and the other outer join spellings
      "select a.id ai, x.pid xp from s.b a left join a x on a.id = x.pid | a.id *= x.pid",
      "select a.id ai, x.pid xp from s.b a left join a x on x.pid = a.id | a.id *= x.pid",
      "select a.id ai, x.pid xp from s.b a left outer join a x on a.id = x.pid | a.id *= x.pid",
      "select a.id ai, x.pid xp from s.b a right join a x on a.id = x.pid | a.id =* x.pid",
      "select a.id ai, x.pid xp from s.b a right join a x on x.pid = a.id | a.id =* x.pid",
      // without the schema
      "select a.id ai, x.pid xp from b a left join a x on a.id = x.pid | a.id *= x.pid",
      "select a.id ai, x.pid xp from b a right join a x on a.id = x.pid | a.id =* x.pid",
      // a chain whose second ON hits the collision
      "select c.id ci, a.id ai, x.pid xp from c left join s.b a on c.id = a.id " +
         "left join a x on a.id = x.pid | c.id *= a.id, a.id *= x.pid",
      "select a.id ai, x.pid xp, y.pid yp from s.b a left join a x on a.id = x.pid " +
         "left join c y on a.id = y.pid | a.id *= x.pid, a.id *= y.pid",
      // the colliding table is the third table
      "select a.id ai, x.pid xp, y.pid yp from s.b a left join c x on a.id = x.pid " +
         "left join a y on a.id = y.pid | a.id *= x.pid, a.id *= y.pid",
      "select a.id ai, x.pid xp, y.pid yp from s.b a left join c x on a.id = x.pid " +
         "right join a y on a.id = y.pid | a.id *= x.pid, a.id =* y.pid",
      // an inner join first
      "select a.id ai, x.pid xp, y.k yk from s.b a join c y on a.id = y.id " +
         "left join a x on a.id = x.pid | a.id = y.id, a.id *= x.pid",
      // two conditions, each one is oriented
      "select a.id ai, x.pid xp from s.b a left join a x on a.id = x.pid and a.k = x.k | " +
         "a.id *= x.pid, a.k *= x.k",
      "select a.id ai, x.pid xp from s.b a left join a x on a.id = x.pid and x.k = a.k | " +
         "a.id *= x.pid, a.k *= x.k",
      // quoted and upper case names
      "select \"A\".id ai, x.pid xp from \"S\".\"B\" \"A\" left join \"A\" x " +
         "on \"A\".id = x.pid | A.id *= x.pid",
      "select a.id ai, x.pid xp from S.B a left join A X on A.ID = X.PID | A.ID *= X.PID",
      // the earlier alias equals the joined table's name, the joined table has no alias
      "select c.id ci, x.pid xp from s.b c left join c x on c.id = x.pid | c.id *= x.pid",
      // the earlier table is a derived table
      "select a.id ai, a2.pid xp from (select id, pid from b) a left join a a2 " +
         "on a.id = a2.pid | a.id *= a2.pid",
      // #77516, a self join and an earlier alias equal to the joined table's name
      "select a.id ai, a2.pid xp from a left join a a2 on a.id = a2.pid | a.id *= a2.pid",
      "select b.id bi, x.pid xp from c b left join b x on b.id = x.pid | b.id *= x.pid"
   })
   void outerJoinPreservesEarlierTable(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(expected, joins(sql));

      for(XJoin join : sql.getJoins()) {
         if(!"=".equals(join.getOp())) {
            assertTrue(fromIndex(sql, join.getTable1(sql)) < fromIndex(sql, join.getTable2(sql)),
                       "the earlier table is table1 in " + join);
         }
      }

      List<String> generated = new ArrayList<>();

      for(String helper : new String[] { null, "derby", "derby-ansi", "h2-ansi" }) {
         String query = regenerate(sql, helper);
         generated.add(query);

         // the regenerated sql parses back to the same joins
         assertEquals(expected, joins(parse(query)), helper + ": " + query);
      }

      assertEquals(Collections.nCopies(generated.size(), 0), diffCounts(text, generated),
                   String.join("\n", generated));
   }

   // Oracle without the ANSI option writes the joins in the where clause, the (+) marks the
   // joined table's column
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.id ai, x.pid xp from s.b a left join a x on a.id = x.pid | a.id = x.pid(+)",
      "select a.id ai, x.pid xp from s.b a left join a x on x.pid = a.id | a.id = x.pid(+)",
      "select a.id ai, x.pid xp from b a left join a x on a.id = x.pid | a.id = x.pid(+)",
      "select a.id ai, a2.pid xp from a left join a a2 on a.id = a2.pid | a.id = a2.pid(+)",
      "select b.id bi, x.pid xp from c b left join b x on b.id = x.pid | b.id = x.pid(+)"
   })
   void oracleMarksJoinedTable(String text, String expected) throws Exception {
      String generated = regenerate(parse(text), "oracle");
      assertTrue(generated.contains(" where " + expected), generated);
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static String regenerate(UniformSQL sql, String helper) {
      UniformSQL clone = (UniformSQL) sql.clone();
      clone.setDataSource(helper == null ? null : dataSource(helper));
      clone.clearSQLString();
      return clone.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String joins(UniformSQL sql) {
      return Arrays.stream(sql.getJoins())
         .map(j -> j.getExpression1().getValue() + " " + j.getOp() + " " +
            j.getExpression2().getValue())
         .collect(Collectors.joining(", "));
   }

   // the FROM table a qualifier refers to, by its alias, or by its name if it has none
   private static int fromIndex(UniformSQL sql, String qualifier) {
      String name = qualifier.replace("\"", "");

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);
         String exposed = table.getAlias() != null ? table.getAlias() : table.getName() + "";

         if(exposed.replace("\"", "").equalsIgnoreCase(name)) {
            return i;
         }
      }

      fail("no FROM table for " + qualifier);
      return -1;
   }

   private static JDBCDataSource dataSource(String type) {
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
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType(),
                   "helper for " + type);
      return ds;
   }

   // for each generated query, the number of random datasets (null, 0, 1 or 2 in each
   // column) on which it returns different rows than the original query
   private static List<Integer> diffCounts(String original, List<String> generated)
      throws Exception
   {
      Random random = new Random(77547);
      Integer[] values = { null, 0, 1, 2 };
      Integer[] diffs = new Integer[generated.size()];
      Arrays.fill(diffs, 0);

      for(int n = 0; n < DATASETS; n++) {
         for(String table : TABLES) {
            try(Statement st = con.createStatement()) {
               st.execute("delete from " + table);
            }

            try(PreparedStatement insert =
                   con.prepareStatement("insert into " + table + " values (?, ?, ?)"))
            {
               for(int r = random.nextInt(5); r > 0; r--) {
                  for(int col = 1; col <= 3; col++) {
                     insert.setObject(col, values[random.nextInt(4)], Types.INTEGER);
                  }

                  insert.executeUpdate();
               }
            }
         }

         List<String> expected = rows(original);

         for(int i = 0; i < diffs.length; i++) {
            if(!expected.equals(rows(generated.get(i)))) {
               diffs[i]++;
            }
         }
      }

      return Arrays.asList(diffs);
   }

   // rows as a sorted multiset, with columns ordered by label since regeneration can reorder
   // the select list
   private static List<String> rows(String query) throws SQLException {
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
