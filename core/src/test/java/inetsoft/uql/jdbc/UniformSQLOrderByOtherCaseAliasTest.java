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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77644. On a helper that doesn't quote every name, an unquoted order by name that
 * differs from a select alias only in case (order by a, with k as A or k as "A") was matched
 * with the alias by its exact text, and then found as the table column of that name ignoring
 * case, so the metadata step rewrote it to the column (order by t.A). The database resolves
 * an order by name to the select alias first, so the regenerated sql sorted by another column,
 * or failed when the query groups by an expression. The name is now the select alias as
 * stored, as if written in its case.
 *
 * The rows of the original and the regenerated sql are compared on Derby and H2, which fold an
 * unquoted name to upper case and resolve an order by name to a select alias first. H2 isn't
 * on the core test classpath, add it with -Dmaven.test.additionalClasspath to compare on H2.
 * The derby helper isn't used, its metadata step needs the root metadata of a real session.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLOrderByOtherCaseAliasTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOrderByOtherCaseAliasTest {
   @BeforeAll
   static void createTables() throws Exception {
      Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver");
      urls = new ArrayList<>(List.of(DERBY_URL));

      // H2 isn't on the core test classpath, add it with -Dmaven.test.additionalClasspath
      try {
         Class.forName("org.h2.Driver");
         urls.add(H2_URL);
      }
      catch(ClassNotFoundException ex) {
         // derby only
      }

      for(String url : urls) {
         try(Connection conn = DriverManager.getConnection(
                url.equals(DERBY_URL) ? url + ";create=true" : url);
             Statement stmt = conn.createStatement())
         {
            stmt.execute("create table t (id int, k int, a int)");
            stmt.execute("insert into t values (1, 1, 30), (2, 2, 20), (3, 3, 10)");
         }
      }
   }

   @AfterAll
   static void dropTables() throws Exception {
      if(urls.contains(H2_URL)) {
         try(Connection conn = DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement())
         {
            stmt.execute("drop table t");
         }
      }

      try {
         DriverManager.getConnection(DERBY_URL + ";drop=true");
      }
      catch(SQLException ex) {
         // derby reports a successful drop as an exception
      }
   }

   /**
    * The order by name in another case than the select alias sorts by the alias, on the
    * helpers of databases that fold an unquoted name to upper case or ignore its case.
    */
   @Test
   void otherCaseAliasSortsByTheAlias() throws Exception {
      for(String helper : HELPERS) {
         for(String query : ALIAS_QUERIES) {
            String generated = fixed(helper, query, UPPER_COLUMNS);
            String label = helper + ": " + query + " -> " + generated;

            assertFalse(generated.contains("order by t.A"), label);
            assertFalse(generated.contains("order by \"t\".\"A\""), label);
         }
      }

      // the alias as if written in its case: its table column, or the alias of an expression
      assertEquals("select t.K as A from t order by t.K desc",
                   fixed("h2", "select k as \"A\" from t order by a desc", UPPER_COLUMNS));
      assertEquals("select t.K as a from t order by t.K desc",
                   fixed("h2", "select k as a from t order by A desc", UPPER_COLUMNS));
      assertEquals("select count(*) as n, id+1 as A from t group by id+1 order by A desc",
                   fixed("h2", "select id + 1 as A, count(*) as n from t group by id + 1 order by a desc",
                         UPPER_COLUMNS));
      assertEquals("select t.K as \"A\" from t order by t.K desc",
                   fixed("oracle", "select k as \"A\" from t order by a desc", UPPER_COLUMNS));
   }

   /**
    * The original and the regenerated sql return the same rows in the same order on Derby
    * and H2. The regenerated sql of the generic, h2 and oracle helpers runs on both.
    */
   @Test
   void regeneratedSqlReturnsTheRowsOfTheSql() throws Exception {
      for(String helper : new String[] { "xyzdb", "h2", "oracle" }) {
         for(String query : ALIAS_QUERIES) {
            String generated = fixed(helper, query, UPPER_COLUMNS);

            for(String url : urls) {
               List<Map<String, Object>> rows = rows(url, query);

               // derby rejects the column of the alias K as ambiguous with the alias A, h2
               // sorts by the alias A
               if(query.contains("a as K") && url.equals(DERBY_URL)) {
                  continue;
               }

               assertFalse(rows.get(0).containsKey("ERROR"), url + ": " + query + " " + rows);
               assertEquals(rows, rows(url, generated),
                            helper + " " + url + ": " + query + " -> " + generated);
            }
         }
      }
   }

   /**
    * The regenerated sql parses and regenerates to itself.
    */
   @Test
   void roundTrip() throws Exception {
      for(String query : ALIAS_QUERIES) {
         String generated = fixed("h2", query, UPPER_COLUMNS);
         assertEquals(generated, fixed("h2", generated, UPPER_COLUMNS), query);
      }
   }

   /**
    * A name in the case of the alias, or qualified by the table, is unchanged.
    */
   @Test
   void sameCaseAliasAndQualifiedColumnUnchanged() throws Exception {
      for(String helper : HELPERS) {
         assertTrue(fixed(helper, "select k as A from t order by A desc", UPPER_COLUMNS)
                       .endsWith(" order by t.K desc"), helper);
         String generated = fixed(helper, "select k as A from t order by t.a desc", UPPER_COLUMNS);
         assertTrue(generated.endsWith(" order by t.A desc"), helper + ": " + generated);
      }

      for(String url : urls) {
         String query = "select k as A from t order by t.a desc";
         assertEquals(rows(url, query), rows(url, fixed("h2", query, UPPER_COLUMNS)), url);
      }
   }

   /**
    * Group by isn't changed: Derby and Oracle resolve a group by name to a table column
    * first, so group by a stays the table column.
    */
   @Test
   void groupByUnchanged() throws Exception {
      for(String helper : new String[] { "xyzdb", "h2" }) {
         assertEquals("select count(*) as n, t.K as A from t group by t.A",
                      fixed(helper, "select k as A, count(*) as n from t group by a", UPPER_COLUMNS),
                      helper);
         assertEquals("select count(*) as n, t.K as A from t group by t.A",
                      fixed(helper, "select k as \"A\", count(*) as n from t group by a", UPPER_COLUMNS),
                      helper);
      }
   }

   /**
    * Clickhouse names are case-sensitive, a in another case than the alias A is the table
    * column a, as before. Sybase (ase names are case-sensitive) keeps the column too, and so
    * does informix, which folds a name to lower case.
    */
   @Test
   void caseSensitiveAndLowerCaseHelpersKeepTheColumn() throws Exception {
      for(String helper : new String[] { "clickhouse", "sybase" }) {
         assertEquals("select t.k as A from t order by t.a desc",
                      fixed(helper, "select k as A from t order by a desc", LOWER_COLUMNS), helper);
         assertEquals("select t.k as A from t order by t.a desc",
                      fixed(helper, "select k as \"A\" from t order by a desc", LOWER_COLUMNS), helper);
      }

      for(String helper : new String[] { "clickhouse", "sybase", "informix", "postgresql", "snowflake",
                                         "exasol" })
      {
         assertFalse(helper(helper).isAliasCaseInsensitive(), helper);
      }

      for(String helper : HELPERS) {
         assertTrue(helper(helper).isAliasCaseInsensitive(), helper);
      }
   }

   /**
    * Postgresql, snowflake and exasol quote every name and resolve the alias by its recorded
    * quoting (Bug #77616), unchanged.
    */
   @Test
   void caseSensitiveHelpersUnchanged() throws Exception {
      assertEquals("select \"k\" as \"A\" from \"t\"",
                   fixed("postgresql", "select k as \"A\" from t order by a desc", UPPER_COLUMNS));
      assertEquals("select \"k\" as \"A\" from \"t\" order by \"t\".a desc",
                   fixed("postgresql", "select k as \"A\" from t order by a desc", LOWER_COLUMNS));
      assertEquals("select \"k\" as \"A\" from \"t\" order by \"k\" desc",
                   fixed("postgresql", "select k as A from t order by a desc", LOWER_COLUMNS));

      for(String helper : new String[] { "snowflake", "exasol" }) {
         assertEquals("select \"k\" as A from \"t\" order by \"k\" desc",
                      fixed(helper, "select k as \"A\" from t order by a desc", UPPER_COLUMNS),
                      helper);
      }
   }

   private static SQLHelper helper(String product) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(product));
      return SQLHelper.getSQLHelper(sql);
   }

   private static String fixed(String helper, String query, String... columns) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource(helper));
      sql.parse(query, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), query);
      assertFalse(sql.isLossy(), query);
      JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, sql.getDataSource());
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the rows in order, each by column label, the select columns may be reordered. A query
   // that fails gives its error
   private static List<Map<String, Object>> rows(String url, String query) {
      List<Map<String, Object>> rows = new ArrayList<>();

      try(Connection conn = DriverManager.getConnection(url);
          Statement stmt = conn.createStatement();
          ResultSet result = stmt.executeQuery(query))
      {
         int count = result.getMetaData().getColumnCount();

         while(result.next()) {
            Map<String, Object> row = new TreeMap<>();

            for(int i = 1; i <= count; i++) {
               row.put(result.getMetaData().getColumnLabel(i).toUpperCase(Locale.ROOT),
                       result.getObject(i));
            }

            rows.add(row);
         }
      }
      catch(SQLException ex) {
         return List.of(Map.of("ERROR", ex.getMessage()));
      }

      return rows;
   }

   // the table metadata is cached by data source, every query gets another one
   private static JDBCDataSource dataSource(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77644" + product + RUN + "_" + (++sources));
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:x:" + product);
      ds.setRuntimeProductName(product);
      ds.setProductVersion("10.0");
      return ds;
   }

   private static XRepository repository(String[] columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   // the order by name in another case than the alias, also of an expression grouped by
   private static final String[] ALIAS_QUERIES = {
      "select k as \"A\" from t order by a desc",
      "select k as A from t order by a desc",
      "select k as a from t order by A desc",
      "select id + 1 as A from t order by a desc",
      "select id + 1 as A, count(*) as n from t group by id + 1 order by a desc",
      "select k as A, a as K from t order by a desc",
   };
   // the helpers of databases that fold an unquoted name to upper case or ignore its case
   private static final String[] HELPERS = { "xyzdb", "h2", "oracle", "mysql", "sql server" };
   // the stored case of the columns on derby, h2 and oracle
   private static final String[] UPPER_COLUMNS = { "ID", "K", "A" };
   private static final String[] LOWER_COLUMNS = { "id", "k", "a" };
   private static final String DERBY_URL = "jdbc:derby:memory:orderByOtherCaseAlias77644";
   private static final String H2_URL = "jdbc:h2:mem:orderByOtherCaseAlias77644;DB_CLOSE_DELAY=-1";
   private static final long RUN = System.nanoTime();
   private static int sources;
   private static List<String> urls;
}
