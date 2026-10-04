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
 * A generated ALIAS_N name must not be the output name of another column of the select list,
 * ignoring case (Bug #77716): an unaliased column ALIAS_0, or an alias alias_0 that a helper
 * writes unquoted. Checked on the helpers that reject a long alias, and run on Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  GeneratedAliasOutputNameTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeneratedAliasOutputNameTest {
   private static final String DB = "memory:generatedaliasoutputname";
   private static final String LONG = "a_very_long_alias_name_over_28_chars";

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
         try {
            stmt.executeUpdate("drop table t");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table t (ALIAS_0 int, a int, b int)");
         stmt.executeUpdate("insert into t values (1, 2, 3)");
      }
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   @Test
   void unaliasedColumnOnOracleAndPostgreSQL() throws Exception {
      String q = "select t.ALIAS_0, t.b as " + LONG + " from t";
      String ora = regen(parse(q, oracle()));
      String pg = regen(parse(q, postgresql()));
      assertEquals("select T.ALIAS_0, T.B as \"ALIAS_1\" from t", ora);
      // postgresql reads ALIAS_0 written unquoted as alias_0 (Bug #77643)
      assertEquals("select \"t\".\"alias_0\", \"t\".\"b\" as \"ALIAS_1\" from \"t\"", pg);

      String lower = regen(parse("select t.alias_0, t.b as " + LONG + " from t", oracle()));
      assertEquals("select T.ALIAS_0, T.B as \"ALIAS_1\" from t", lower);

      // Oracle quotes alias_0, an alias in the same case was skipped before too
      assertEquals("select T.A as \"alias_0\", T.B as \"ALIAS_1\" from t",
                   regen(parse("select t.a as alias_0, t.b as " + LONG + " from t", oracle())));
      assertEquals("select T.A as \"ALIAS_0\", T.B as \"ALIAS_1\" from t",
                   regen(parse("select t.a as ALIAS_0, t.b as " + LONG + " from t", oracle())));

      // a derived table, referenced by the outer query
      assertEquals("select s.\"ALIAS_1\" from ( select t.ALIAS_0, t.b as \"ALIAS_1\" from t) s",
                   regen(parse("select s." + LONG + " from (select t.ALIAS_0, t.b as " + LONG +
                               " from t) s", oracle())));
   }

   /**
    * The helpers that write an alias unquoted on a case-insensitive database. DB2 always
    * rejects a long alias, the others with limit.alias.length=true.
    */
   @Test
   void aliasInAnotherCaseOnUnquotedHelpers() throws Exception {
      String q = "select t.a as alias_0, t.b as " + LONG + " from t";
      // DB2 with defaults
      String db2 = regen(parse(q, db2()));
      assertEquals("select t.a as alias_0, t.b as ALIAS_1 from t", db2);

      SreeEnv.setProperty("limit.alias.length", "true");
      SQLHelper[] helpers = { new SQLHelper(), new InformixSQLHelper(), new ExasolHelper(),
                              new H2Helper(), new SQLServerHelper(), new MySQLHelper(),
                              new SnowflakeHelper() };

      for(SQLHelper helper : helpers) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(GenericJDBCDataSource.create());
         sql.parse(q, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
         helper.setUniformSql(sql);
         String gen = helper.generateSentence().replaceAll("\\s+", " ").trim();
         assertTrue(gen.matches("(?i).*as alias_0, .* as \"?ALIAS_1\"? from.*"), gen);
         assertFalse(gen.matches("(?i).* as \"?ALIAS_0\"? from.*"), gen);
      }
   }

   /**
    * Used to fail with "Column name 'ALIAS_0' matches more than one result column".
    */
   @Test
   void derivedTableRunsOnDerby() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = derbySource();
      UniformSQL outer = parseGeneric("select s." + LONG + " from (select t.ALIAS_0, t.b as " +
                                      LONG + " from t) s", ds);
      String outerSql = regen(outer);

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(outerSql))
      {
         assertTrue(rs.next());
         assertEquals(3, rs.getInt(1));
      }

      // an alias in another case
      UniformSQL co = parseGeneric("select t.a as alias_0, t.b as " + LONG + " from t", ds);
      String coSql = regen(co);

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("select s.alias_0 from (" + coSql + ") s"))
      {
         assertTrue(rs.next());
         assertEquals(2, rs.getInt(1));
      }
   }

   /**
    * The column names JDBCTableNode gets from SQLSelection.getColumnNames. Both columns were
    * named after the long alias, the first one with the data of ALIAS_0.
    */
   @Test
   void columnNamesOnDerby() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = derbySource();
      UniformSQL top = parseGeneric("select t.ALIAS_0, t.b as " + LONG + " from t", ds);
      String topSql = regen(top);

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(topSql))
      {
         List<String> labels = new ArrayList<>();

         for(int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            labels.add(rs.getMetaData().getColumnLabel(i));
         }

         String[] names = SQLSelection.getColumnNames(top.getSelection(), rs.getMetaData(),
                                                      ds.getDriver(), conn, null);
         assertTrue(rs.next());
         assertEquals(List.of("ALIAS_0", LONG), Arrays.asList(names), topSql);
         assertEquals(List.of("ALIAS_0", "ALIAS_1"), labels, topSql);
         assertEquals(1, rs.getInt(1));
         assertEquals(3, rs.getInt(2));
      }
   }

   private static UniformSQL parseGeneric(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.setDataSource(ds);
      assertEquals("derby", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return sql;
   }

   private static String regen(UniformSQL sql) {
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
      ds.setName("generatedaliasoutputname_" + product);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      ds.setProductVersion("10.0");
      assertEquals(helper, SQLHelper.getSQLHelper(ds).getClass(), product);
      return ds;
   }

   private static JDBCDataSource derbySource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("generatedaliasoutputname_derby");
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
