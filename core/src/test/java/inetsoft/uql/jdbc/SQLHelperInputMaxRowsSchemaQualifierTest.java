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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77860. With the input row limit (worksheet live preview), each table of the from
 * clause is changed to a subquery with the row limit, and a table without an alias is given
 * its name as the alias ("public.CUSTOMERS"). A column qualified by the end of the table name
 * (CUSTOMERS.CITY with from public.CUSTOMERS) was left as written, so it named nothing:
 * "missing FROM-clause entry for table CUSTOMERS" on PostgreSQL. The end of the name is now
 * replaced by the alias, except where another table, of the query or of a subquery, is
 * referred to by the same name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLHelperInputMaxRowsSchemaQualifierTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperInputMaxRowsSchemaQualifierTest {
   private static final String DB = "memory:bug77860";

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   // the reported query, and the end of the name in every clause, quoted and not
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select \"CUSTOMERS\".\"CITY\", \"CUSTOMERS\".\"CUSTOMER_ID\" from public.\"CUSTOMERS\"|" +
         "select \"public.CUSTOMERS\".\"CITY\", \"public.CUSTOMERS\".\"CUSTOMER_ID\" from ( select * from \"public\".\"CUSTOMERS\" limit 50000) \"public.CUSTOMERS\"",
      "select customers.city from public.customers|" +
         "select \"public.customers\".\"city\" from ( select * from \"public\".\"customers\" limit 50000) \"public.customers\"",
      "select \"CUSTOMERS\".\"CITY\" from public.\"CUSTOMERS\" where \"CUSTOMERS\".\"CITY\" = 'x' order by \"CUSTOMERS\".\"CITY\"|" +
         "select \"public.CUSTOMERS\".\"CITY\" from ( select * from \"public\".\"CUSTOMERS\" limit 50000) \"public.CUSTOMERS\" where \"public.CUSTOMERS\".\"CITY\" = 'x' order by \"public.CUSTOMERS\".\"CITY\" asc",
      "select upper(\"CUSTOMERS\".\"CITY\") from public.\"CUSTOMERS\"|" +
         "select upper(\"public.CUSTOMERS\".\"CITY\") from ( select * from \"public\".\"CUSTOMERS\" limit 50000) \"public.CUSTOMERS\"",
      "select \"CUSTOMERS\".\"CITY\", public.\"CUSTOMERS\".\"CUSTOMER_ID\" from db.public.\"CUSTOMERS\"|" +
         "select \"db.public.CUSTOMERS\".\"CITY\", \"db.public.CUSTOMERS\".\"CUSTOMER_ID\" from ( select * from \"db\".\"public\".\"CUSTOMERS\" limit 50000) \"db.public.CUSTOMERS\"",
      // the full name, as before
      "select public.\"CUSTOMERS\".\"CITY\" from public.\"CUSTOMERS\"|" +
         "select \"public.CUSTOMERS\".\"CITY\" from ( select * from \"public\".\"CUSTOMERS\" limit 50000) \"public.CUSTOMERS\"",
   })
   void postgresEndOfNameReplaced(String text, String expected) throws Exception {
      assertEquals(expected, generate("postgresql", false, text, true));
      assertEquals(expected, generate("postgresql", true, text, true));
   }

   // every helper with an input row limit wraps the table the same way
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "mysql", "sql server", "oracle", "derby" })
   void otherHelpersEndOfNameReplaced(String product) throws Exception {
      String sql = generate(product, false,
         "select customers.city, customers.id from sales.customers where customers.id > 0", true);
      String alias = sql.substring(sql.lastIndexOf(") ") + 2).split(" ")[0];

      assertTrue(alias.contains("sales.customers"), sql);
      assertEquals(3, count(sql, alias + "."), sql);
      assertFalse(sql.matches("(?is).*[^.\"`]customers\\..*"), sql);
   }

   // a correlated subquery that refers to the outer table by the end of its name
   @Test
   void correlatedSubqueryEndOfNameReplaced() throws Exception {
      String sql = generate("postgresql", false,
         "select customers.id from sales.customers where exists " +
         "(select 1 from orders o where o.cid = customers.id)", true);

      assertEquals("select \"sales.customers\".\"id\" from ( select * from \"sales\".\"customers\" limit 50000) \"sales.customers\" " +
                   "where EXISTS ( select 1 from ( select * from \"orders\" limit 50000) o where \"o\".\"cid\" = \"sales.customers\".\"id\")",
                   sql);
   }

   // the table of a subquery hides the outer table of the same name: its references are the
   // subquery's own, and are not replaced by the outer alias (that silently returned other rows)
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select customers.id from sales.customers where exists (select 1 from customers where customers.id = 1)|1",
      "select customers.id from sales.customers where customers.id in (select customers.id from customers where customers.city = 'x')|2",
      "select (select max(customers.id) from customers) from sales.customers|0",
      "select customers.id from sales.customers where exists (select 1 from orders customers where customers.id = 1)|1",
   })
   void subqueryTableNotReplaced(String text, int outer) throws Exception {
      for(String product : List.of("postgresql", "derby")) {
         String sql = generate(product, false, text, true);
         String sub = operand(sql);

         assertFalse(sub.contains("sales.customers"), sql);
         assertEquals(outer, count(sql, "sales.customers\".") - count(sub, "sales.customers\"."),
                      sql);
      }
   }

   // a name that also refers to another table of the query is not replaced
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = {
      "select t.id from public.t, other.t",
      "select t.id from public.t, other.u t",
      "select t.id from public.t, t",
      "select items.id from public.items, other.ITEMS",
   })
   void collisionNotReplaced(String text) throws Exception {
      Locale locale = Locale.getDefault();

      try {
         for(Locale loc : List.of(Locale.US, new Locale("tr", "TR"))) {
            Locale.setDefault(loc);
            String sql = generate("postgresql", false, text, true);
            String qualifier = text.substring(7, text.indexOf('.'));

            assertTrue(sql.startsWith("select \"" + qualifier + "\".") ||
                       sql.startsWith("select " + qualifier + "."), sql);
         }
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // the rows with and without the input row limit, of the sql generated for postgresql, on
   // hsqldb, which like postgresql refers to a table by the end of its name (derby doesn't):
   // the end of the name, a correlated subquery, and subqueries over the same-named table of
   // the default schema, which hides the outer table in the subquery
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = {
      "select \"CUSTOMERS\".\"ID\", \"CUSTOMERS\".\"CITY\" from S77860.\"CUSTOMERS\"",
      "select \"CUSTOMERS\".\"ID\" from S77860.\"CUSTOMERS\" where \"CUSTOMERS\".\"CITY\" = 'b' order by \"CUSTOMERS\".\"ID\"",
      "select CUSTOMERS.ID from S77860.CUSTOMERS where CUSTOMERS.CITY = 'b'",
      "select \"CUSTOMERS\".\"ID\" from S77860.\"CUSTOMERS\" where exists (select 1 from \"ORDERS\" O where O.\"CID\" = \"CUSTOMERS\".\"ID\")",
      "select \"CUSTOMERS\".\"ID\" from S77860.\"CUSTOMERS\" where exists (select 1 from \"CUSTOMERS\" where \"CUSTOMERS\".\"ID\" = 1)",
      "select \"CUSTOMERS\".\"ID\" from S77860.\"CUSTOMERS\" where \"CUSTOMERS\".\"ID\" in (select \"CUSTOMERS\".\"ID\" from \"CUSTOMERS\" where \"CUSTOMERS\".\"CITY\" = 'x')",
      "select \"CUSTOMERS\".\"ID\", (select max(\"CUSTOMERS\".\"ID\") from \"CUSTOMERS\") from S77860.\"CUSTOMERS\"",
   })
   void hsqldbRows(String text) throws Exception {
      createTables();
      String plain = generate("postgresql", false, text, false);
      String limited = generate("postgresql", false, text, true);

      assertNotEquals(plain, limited);
      assertEquals(rows(plain), rows(limited), limited);
   }

   private static void createTables() throws Exception {
      try(Connection conn = hsqldb().getConnection(); Statement stmt = conn.createStatement()) {
         stmt.executeUpdate("drop schema S77860 if exists cascade");
         stmt.executeUpdate("drop table CUSTOMERS if exists");
         stmt.executeUpdate("drop table ORDERS if exists");
         stmt.executeUpdate("create schema S77860");
         stmt.executeUpdate("create table S77860.CUSTOMERS (ID INT, CITY VARCHAR(20))");
         stmt.executeUpdate("insert into S77860.CUSTOMERS values (1, 'a'), (2, 'b'), (3, 'x'), (4, 'b')");
         // the same-named table of the default schema, with other rows
         stmt.executeUpdate("create table CUSTOMERS (ID INT, CITY VARCHAR(20))");
         stmt.executeUpdate("insert into CUSTOMERS values (7, 'x'), (9, 'y')");
         stmt.executeUpdate("create table ORDERS (CID INT)");
         stmt.executeUpdate("insert into ORDERS values (2), (3)");
      }
   }

   private static List<List<Object>> rows(String sql) throws Exception {
      List<List<Object>> rows = new ArrayList<>();

      try(Connection conn = hsqldb().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         int n = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<Object> row = new ArrayList<>();

            for(int i = 1; i <= n; i++) {
               row.add(rs.getObject(i));
            }

            rows.add(row);
         }
      }
      catch(SQLException ex) {
         throw new SQLException(sql, ex);
      }

      rows.sort(Comparator.comparing(Object::toString));
      return rows;
   }

   // the subquery operand of the sql, not a derived table of the input row limit
   private static String operand(String sql) {
      java.util.regex.Matcher matcher =
         java.util.regex.Pattern.compile("\\(\\s*select (?!\\* from)").matcher(sql);
      assertTrue(matcher.find(), sql);
      int depth = 0;

      for(int i = matcher.start(); i < sql.length(); i++) {
         depth += sql.charAt(i) == '(' ? 1 : sql.charAt(i) == ')' ? -1 : 0;

         if(depth == 0) {
            return sql.substring(matcher.start(), i + 1);
         }
      }

      return sql.substring(matcher.start());
   }

   private static int count(String text, String part) {
      int n = 0;

      for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
         n++;
      }

      return n;
   }

   private static String generate(String product, boolean ansi, String text, boolean limit)
      throws Exception
   {
      JDBCDataSource ds = dataSource(product);
      ds.setAnsiJoin(ansi);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      usql.clearSQLString();

      if(limit) {
         usql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "50000");
      }

      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77860-" + product);

      switch(product) {
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:" + DB);
         ds.setRequireLogin(false);
      }
      default -> throw new IllegalArgumentException(product);
      }

      ds.setRuntimeProductName(product);
      Class<?> helper = switch(product) {
         case "mysql" -> MySQLHelper.class;
         case "postgresql" -> PostgreSQLHelper.class;
         case "sql server" -> SQLServerHelper.class;
         case "oracle" -> OracleSQLHelper.class;
         default -> DerbyHelper.class;
      };
      assertEquals(helper, SQLHelper.getSQLHelper(ds).getClass(), product);
      return ds;
   }

   private static DataSource hsqldb() {
      org.hsqldb.jdbc.JDBCDataSource ds = new org.hsqldb.jdbc.JDBCDataSource();
      ds.setUrl("jdbc:hsqldb:mem:bug77860");
      ds.setUser("SA");
      ds.setPassword("");
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
