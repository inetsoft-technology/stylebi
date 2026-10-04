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
import inetsoft.uql.XRepository;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.Config;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * The generated ALIAS_N names of a select list, as the helper writes them: generating the sql
 * must not change the query, and a generated name must not be the output name of another
 * column. The Oracle, PostgreSQL and DB2 helpers always reject an alias longer than 28
 * characters. The Derby cases set limit.alias.length=true for that, and are run on Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  JDBCSelectionGeneratedAliasTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCSelectionGeneratedAliasTest {
   private static final String DB = "memory:jdbcselectiongeneratedalias";
   private static final String LONG = "very_long_column_alias_name_over_28";
   private static final String L1 = "first_very_long_column_alias_name_1";
   private static final String L2 = "second_very_long_column_alias_name2";

   @Configuration
   @Import(CredentialService.class)
   static class Beans {
      @Bean
      Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "t", "u" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table t (ALIAS_0 int, x int)");
         stmt.executeUpdate("insert into t values (100, 1)");
         stmt.executeUpdate("create table u (x int)");
         stmt.executeUpdate("insert into u values (7), (8)");
      }
   }

   @AfterEach
   void resetLimitAlias() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   // ---- Bug #77712: generating the sql changes the query ----

   /**
    * Two derived tables both output ALIAS_0, so the second outer column gets a new ALIAS_1.
    * Generating the sql used to set the alias of that column to its column name, which
    * changed the query (its equals, clone and xml) and the sql another helper generates.
    */
   @Test
   void generationDoesNotChangeTheAliases() throws Exception {
      String text = "select s1." + L1 + ", s2." + L2 + " from (select t.x as " + L1 +
         " from t) s1, (select u.x as " + L2 + " from u) s2";
      UniformSQL sql = parse(text, oracle());
      String generated = regenerate(sql);
      assertTrue(generated.startsWith("select s1.\"ALIAS_0\", s2.\"ALIAS_0\" as \"ALIAS_1\" "),
                 generated);

      XSelection selection = sql.getSelection();
      assertNull(selection.getAlias(0), generated);
      assertNull(selection.getAlias(1), generated);
      assertEquals(parse(text, oracle()).getSelection(), selection);
      assertNull(((UniformSQL) sql.clone()).getSelection().getAlias(1));
   }

   /**
    * The same column twice with no alias. The second one gets its own name although both
    * have the same path, and generating the sql doesn't give it an alias.
    */
   @Test
   void sameColumnTwiceWithNoAlias() throws Exception {
      String text = "select s." + LONG + ", s." + LONG + " from (select u.x as " + LONG +
         " from u) s";
      UniformSQL sql = parse(text, oracle());
      String generated = regenerate(sql);
      assertTrue(generated.startsWith("select s.\"ALIAS_0\", s.\"ALIAS_0\" as \"ALIAS_1\" "),
                 generated);
      assertNull(sql.getSelection().getAlias(1), generated);
      // as generated again
      assertEquals(generated, regenerate(sql));
   }

   // ---- Bug #77713: a generated name keyed by a bare column name ----

   /**
    * The second derived-table column gets a new name for its collision with the first. It
    * was keyed by the bare column name, so a column of another table renamed to that name
    * got the same generated name.
    */
   @Test
   void collisionNameIsKeptForItsColumn() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      String text = "select s1." + L1 + ", s2." + L2 + ", t.x as " + L2 + " from (select t.x as " +
         L1 + " from t) s1, (select u.x as " + L2 + " from u) s2, t";
      assertEquals(List.of("s1." + L1, "s2." + L2, L2), headers(text, -1, null, null));
   }

   // ---- Bug #77716: a generated name equal to another column's output name ----

   @Test
   void generatedNameSkipsAColumnName() throws Exception {
      String text = "select t.ALIAS_0, t.b as " + LONG + " from t";
      assertEquals("select T.ALIAS_0, T.B as \"ALIAS_1\" from t", regenerate(parse(text, oracle())));
      // postgresql reads ALIAS_0 written unquoted as alias_0 (Bug #77643)
      assertEquals("select \"t\".\"alias_0\", \"t\".\"b\" as \"ALIAS_1\" from \"t\"",
                   regenerate(parse(text, postgresql())));
      assertEquals("select t.ALIAS_0, t.b as ALIAS_1 from t", regenerate(parse(text, db2())));

      // Oracle and DB2 fold the unquoted alias_0 to ALIAS_0
      String lower = "select t.alias_0, t.b as " + LONG + " from t";
      assertEquals("select T.ALIAS_0, T.B as \"ALIAS_1\" from t", regenerate(parse(lower, oracle())));
      assertEquals("select t.alias_0, t.b as ALIAS_1 from t", regenerate(parse(lower, db2())));
   }

   /**
    * DB2 writes an alias unquoted, so an alias alias_0 is the output name ALIAS_0.
    */
   @Test
   void generatedNameSkipsAnAliasInAnotherCase() throws Exception {
      String text = "select t.a as alias_0, t.b as " + LONG + " from t";
      assertEquals("select t.a as alias_0, t.b as ALIAS_1 from t", regenerate(parse(text, db2())));
      // as before, an alias in the same case
      String upper = "select t.a as ALIAS_0, t.b as " + LONG + " from t";
      assertEquals("select t.a as ALIAS_0, t.b as ALIAS_1 from t", regenerate(parse(upper, db2())));
      assertEquals("select T.A as \"ALIAS_0\", T.B as \"ALIAS_1\" from t",
                   regenerate(parse(upper, oracle())));
   }

   // ---- Bug #77717: a real ALIAS_0 column next to a generated ALIAS_0 ----

   /**
    * The alias of x has a quote, so it is replaced by a generated name. That was ALIAS_0,
    * the name of the real column t.ALIAS_0, and both columns were named after the alias.
    */
   @Test
   void realColumnNextToAGeneratedName() throws Exception {
      assertEquals(List.of("ALIAS_0", "q\"x"),
                   headers("select t.ALIAS_0, t.x from t", 1, "q\"x", List.of(100, 1)));
      // an unqualified column
      assertEquals(List.of("ALIAS_0", "q\"x"),
                   headers("select ALIAS_0, x from t", 1, "q\"x", List.of(100, 1)));

      // a long alias
      SreeEnv.setProperty("limit.alias.length", "true");
      assertEquals(List.of("ALIAS_0", LONG),
                   headers("select t.ALIAS_0, t.x " + LONG + " from t", -1, null, List.of(100, 1)));
   }

   /**
    * ALIAS_0 is generated only inside the derived table s, so the outer labels don't collide.
    * The outer selection inherited ALIAS_0 -> Foo anyway, and the real column t.ALIAS_0 was
    * named Foo too. Unaliased, both columns were output as ALIAS_0.
    */
   @Test
   void realColumnNextToAnInheritedName() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      String s = "(select u.x " + LONG + " from u where u.x = 7) s";
      assertEquals(List.of("Foo", "ALIAS_0"),
                   headers("select t.ALIAS_0, s." + LONG + " Foo from t, " + s, -1, null,
                           List.of(7, 100)));
      assertEquals(List.of("s." + LONG, "ALIAS_0"),
                   headers("select t.ALIAS_0, s." + LONG + " from t, " + s, -1, null,
                           List.of(7, 100)));
   }

   /**
    * A valid alias over the generated ALIAS_0 of a derived table on Oracle, sorted by it. The
    * column has no inherited name now, and the order by refers to it by the derived table's
    * column, which runs and sorts.
    */
   @Test
   void orderByARenamedSubqueryColumn() throws Exception {
      String text = "select s." + LONG + " c from (select u.x " + LONG + " from u) s order by c desc";
      UniformSQL sql = parse(text, oracle());
      String generated = regenerate(sql);
      assertEquals("select s.\"ALIAS_0\" as \"c\" from ( select u.x as \"ALIAS_0\" from u) s " +
                   "order by s.\"ALIAS_0\" desc", generated);
      assertEquals("c", ((JDBCSelection) sql.getSelection()).getOriginalAlias("c"));

      // the oracle sql runs as is on Derby, which folds unquoted names the same way
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(generated))
      {
         assertEquals("c", rs.getMetaData().getColumnLabel(1));
         List<Integer> values = new ArrayList<>();

         while(rs.next()) {
            values.add(rs.getInt(1));
         }

         assertEquals(List.of(8, 7), values);
      }
   }

   /**
    * Parse the sql on Derby, set an alias, generate the sql with the Derby helper, run it and
    * name its columns as JDBCHandler does: JDBCTableNode with a clone of the selection and
    * the sorted column map.
    */
   private static List<String> headers(String text, int col, String alias, List<Integer> row)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      JDBCDataSource ds = derbySource();
      sql.setDataSource(ds);
      assertEquals("derby", SQLHelper.getSQLHelper(ds).getSQLHelperType());

      if(col >= 0) {
         sql.getSelection().setAlias(col, alias);
      }

      String generated = regenerate(sql);
      int[] map = JDBCQueryCacheNormalizer.generateSortedColumnMap(sql);
      XSelection selection = (XSelection) sql.getSelection().clone();
      List<String> headers = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(generated))
      {
         JDBCTableNode node = new JDBCTableNode(rs, conn, stmt, selection, ds, map);
         List<String> labels = new ArrayList<>();

         for(int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            labels.add(rs.getMetaData().getColumnLabel(i).toUpperCase());
         }

         assertEquals(labels.size(), new HashSet<>(labels).size(), "duplicate output names: " +
            generated);
         assertTrue(node.next(), generated);
         List<Object> values = new ArrayList<>();

         for(int i = 0; i < node.getColCount(); i++) {
            headers.add(node.getName(i));
            values.add(((Number) node.getObject(i)).intValue());
         }

         if(row != null) {
            assertEquals(row, values, generated);
         }
      }

      return headers;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static JDBCDataSource oracle() {
      return source("oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                    OracleSQLHelper.class);
   }

   private static JDBCDataSource postgresql() {
      return source("postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db",
                    PostgreSQLHelper.class);
   }

   private static JDBCDataSource db2() {
      return source("db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/db",
                    DB2SQLHelper.class);
   }

   private static JDBCDataSource source(String product, String driver, String url,
                                        Class<? extends SQLHelper> helper)
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("generatedalias_" + product);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the helper asks the repository for it
      ds.setProductVersion("10.0");
      assertEquals(helper, SQLHelper.getSQLHelper(ds).getClass(), product);
      return ds;
   }

   private static JDBCDataSource derbySource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("generatedalias_derby");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setProductVersion("10.17");
      return ds;
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
