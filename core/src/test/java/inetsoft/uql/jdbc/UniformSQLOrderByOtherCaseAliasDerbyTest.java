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
 * Bug #77644, on the derby helper. An unquoted order by name in another case than a select
 * alias (order by a, with k as A or k as "A") was rewritten to the table column of that name
 * (order by t.A), while Derby resolves it to the alias. The derby helper reads the product
 * version from the repository and qualifies a table by the default schema of the root
 * metadata, so both are given here. See UniformSQLOrderByOtherCaseAliasTest for the other
 * helpers. H2 isn't on the core test classpath, add it with -Dmaven.test.additionalClasspath
 * to compare on H2 too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLOrderByOtherCaseAliasDerbyTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOrderByOtherCaseAliasDerbyTest {
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
   /**
    * The derby helper resolves the order by name in another case than the select alias to
    * the alias, and the regenerated sql returns the rows of the sql on Derby (and H2).
    */
   @Test
   void derbyHelperSortsByTheAlias() throws Exception {
      String[] queries = {
         "select k as \"A\" from t order by a desc",
         "select k as A from t order by a desc",
         "select k as a from t order by A desc",
         "select id + 1 as A from t order by a desc",
      };

      for(String query : queries) {
         String generated = fixed("derby", query, UPPER_COLUMNS);
         assertFalse(generated.contains("order by t.A"), query + " -> " + generated);

         for(String url : urls) {
            List<Map<String, Object>> rows = rows(url, query);
            assertFalse(rows.get(0).containsKey("ERROR"), url + ": " + query + " " + rows);
            assertEquals(rows, rows(url, generated), url + ": " + query + " -> " + generated);
         }
      }

      assertEquals("select t.K as A from t order by t.K desc",
                   fixed("derby", "select k as \"A\" from t order by a desc", UPPER_COLUMNS));
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
      ds.setName("ds77644derby" + product + RUN + "_" + (++sources));
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:x:" + product);
      ds.setRuntimeProductName(product);
      ds.setProductVersion("10.0");
      // the derby types qualify a table by the default schema of the root metadata otherwise
      ds.setTableNameOption(JDBCDataSource.TABLE_OPTION);
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

   // a JDBCDataSource creates its credential when it is constructed,
   // JDBCUtil.fixUniformSQLInfo looks up the driver type, and the derby helper reads the
   // product version through the repository
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      XRepository xRepository() throws Exception {
         XRepository repository = mock(XRepository.class);
         when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            XNode node = new XNode("properties");
            node.setAttribute("DBProductVersion", "10.17.1.0");
            return node;
         });
         return repository;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   // the stored case of the columns on derby, h2 and oracle
   private static final String[] UPPER_COLUMNS = { "ID", "K", "A" };
   private static final String DERBY_URL = "jdbc:derby:memory:orderByOtherCaseAliasDerby77644";
   private static final String H2_URL = "jdbc:h2:mem:orderByOtherCaseAliasDerby77644;DB_CLOSE_DELAY=-1";
   private static final long RUN = System.nanoTime();
   private static int sources;
   private static List<String> urls;
}
