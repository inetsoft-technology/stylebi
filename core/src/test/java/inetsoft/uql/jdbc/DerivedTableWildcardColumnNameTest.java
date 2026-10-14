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

import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78409. {@link JDBCUtil#fixUniformSQLInfo} names the columns of a FROM derived table by
 * its select items ({@link XUtil#getSubQueryColumn}), and a wildcard over the derived table is
 * expanded to those names. On a case-sensitive helper (PostgreSQL) the parser stores the
 * qualifier of an inner item quoted in the text ({@code "c".CITY} for alias {@code c}), which
 * wasn't stripped, so {@code select d.* from (select c."CITY" from t c) d} was generated as
 * {@code select d."""c"".CITY" ...}, which no database accepts. Without table metadata the
 * Derby helper kept the qualifier the same way ({@code d."c.CITY"}).
 *
 * The rows of the generated sql are compared with the original sql on Derby. The PostgreSQL
 * helper's output is run on Derby too, for the shapes whose identifiers both read the same
 * way (upper-case aliases).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  DerivedTableWildcardColumnNameTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DerivedTableWildcardColumnNameTest {
   private static final String DB = "memory:bug78409";
   private static final long RUN = System.nanoTime();
   private static int sources;

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
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         DataSource derby = derby();
         when(factory.getConnectionPool(any(), any())).thenReturn(derby);
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

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }

      // the root metadata of the data source (SQLTypes) is read through
      // XSessionManager.getSessionManager()
      @Bean
      public XSessionManager xSessionManager() {
         XSessionManager sessionManager = mock(XSessionManager.class);
         when(sessionManager.getSession()).thenReturn("bug78409");
         return sessionManager;
      }
   }

   @BeforeEach
   void setRepository() {
      repository = repositoryBean;
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         // the reported sql reads "public"."CUSTOMERS"
         stmt.executeUpdate("create schema \"public\"");

         for(String table : new String[] { "CUSTOMERS", "\"public\".CUSTOMERS" }) {
            stmt.executeUpdate("create table " + table + " (CUSTOMER_ID INT, CITY VARCHAR(20))");
            stmt.executeUpdate("insert into " + table + " values (1, 'Paris'), (2, 'Oslo')");
         }

         stmt.executeUpdate("create table MIX (\"City\" VARCHAR(20), \"A.B\" INT, ID INT)");
         stmt.executeUpdate("insert into MIX values ('Rome', 7, 1), ('Bern', 8, 2)");

         for(String table : new String[] { "ORDERS", "\"public\".ORDERS" }) {
            stmt.executeUpdate("create table " + table + " (ORDER_ID INT, CUSTOMER_ID INT)");
            stmt.executeUpdate("insert into " + table + " values (10, 1), (11, 2), (12, 2)");
         }
      }
   }

   /**
    * A quoted inner column keeps its quotes: a mixed-case name, and a name with a dot in it,
    * which must not be split at the dot.
    */
   @Test
   void quotedInnerColumnKeepsItsName() throws Exception {
      for(boolean meta : new boolean[] { true, false }) {
         assertExpanded("postgresql", meta,
            "select d.* from (select c.\"City\", c.ID from MIX c) d",
            "[d.\"ID\", d.City]",
            "select d.\"ID\", d.\"City\" from");
         assertExpanded("postgresql", meta,
            "select d.* from (select c.\"A.B\", c.ID from MIX c) d",
            "[d.\"A.B\", d.\"ID\"]",
            "select d.\"A.B\", d.\"ID\" from");

         for(String sql : new String[] {
            "select D.* from (select C.\"City\", C.ID from MIX C) D",
            "select D.* from (select C.\"A.B\", C.ID from MIX C) D" })
         {
            assertSameRows(sql, fixed(sql, "postgresql", meta));
         }

         // the default helper writes the mixed-case name unquoted (d.City), before and after
         // the fix, so only the dotted name is compared
         String sql = "select d.* from (select c.\"A.B\", c.ID from MIX c) d";
         assertSameRows(sql, fixed(sql, "derby", meta));
      }
   }

   /**
    * The reported sql on the PostgreSQL helper, with the inner items quoted and unquoted, a
    * quoted alias, the wildcard written d.* and *, and a nested derived table.
    */
   @Test
   void postgresqlNamesTheInnerColumns() throws Exception {
      for(boolean meta : new boolean[] { true, false }) {
         assertExpanded("postgresql", meta,
            "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"public\".\"CUSTOMERS\" c) d",
            "[d.CITY, d.CUSTOMER_ID]",
            "select d.\"CITY\", d.\"CUSTOMER_ID\" from");
         assertExpanded("postgresql", meta,
            "select * from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"public\".\"CUSTOMERS\" c) d",
            "[d.CITY, d.CUSTOMER_ID]",
            "select d.\"CITY\", d.\"CUSTOMER_ID\" from");
         // the usual way to write it on PostgreSQL
         assertExpanded("postgresql", meta,
            "select d.* from (select c.customer_id, c.city from customers c) d",
            "[d.\"city\", d.\"customer_id\"]",
            "select d.\"city\", d.\"customer_id\" from");
         assertExpanded("postgresql", meta,
            "select d.* from (select \"c\".\"CUSTOMER_ID\", \"c\".\"CITY\" from \"CUSTOMERS\" \"c\") d",
            "[d.CITY, d.CUSTOMER_ID]",
            "select d.\"CITY\", d.\"CUSTOMER_ID\" from");
      }
   }

   // the other helpers that store the qualifier quoted
   @Test
   void snowflakeAndExasolNameTheInnerColumns() throws Exception {
      for(String key : new String[] { "snowflake", "exasol" }) {
         assertTrue(SQLHelper.getSQLHelper(helper(key)).isCaseSensitive(), key);

         for(boolean meta : new boolean[] { true, false }) {
            assertExpanded(key, meta,
               "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"CUSTOMERS\" c) d",
               "[d.CITY, d.CUSTOMER_ID]", null);
            assertExpanded(key, meta,
               "select * from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d",
               "[d.\"CITY\", d.\"CUSTOMER_ID\"]", null);
         }
      }
   }

   /**
    * The PostgreSQL helper's sql runs on Derby when the aliases are upper case, and returns
    * the rows of the original sql.
    */
   @Test
   void postgresqlRowsMatchTheOriginal() throws Exception {
      for(boolean meta : new boolean[] { true, false }) {
         for(String sql : new String[] {
            "select D.* from (select C.\"CUSTOMER_ID\", C.\"CITY\" from \"CUSTOMERS\" C) D",
            "select * from (select C.\"CUSTOMER_ID\", C.\"CITY\" from \"CUSTOMERS\" C) D",
            "select D.* from (select C.CUSTOMER_ID, C.CITY from CUSTOMERS C) D",
            "select D.* from (select C.CITY as TOWN, C.CUSTOMER_ID from CUSTOMERS C) D",
            "select D.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from CUSTOMERS) D",
            "select E.* from (select D.* from (select C.CUSTOMER_ID, C.CITY from CUSTOMERS C) D) E" })
         {
            assertSameRows(sql, fixed(sql, "postgresql", meta));
         }
      }
   }

   /**
    * The default (Derby) helper: the names were right with metadata and are unchanged.
    * Without metadata the qualifier was kept (d."c.CITY").
    */
   @Test
   void derbyNamesTheInnerColumns() throws Exception {
      for(boolean meta : new boolean[] { true, false }) {
         assertExpanded("derby", meta,
            "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"CUSTOMERS\" c) d",
            "[d.CITY, d.CUSTOMER_ID]", null);
         assertExpanded("derby", meta,
            "select * from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d",
            "[d.CITY, d.CUSTOMER_ID]", null);
         assertExpanded("derby", meta,
            "select d.* from (select c.CITY as TOWN, c.CUSTOMER_ID from CUSTOMERS c) d",
            "[d.CUSTOMER_ID, d.TOWN]", null);
         assertExpanded("derby", meta,
            "select e.* from (select d.* from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d) e",
            "[e.CITY, e.CUSTOMER_ID]", null);
      }

      for(boolean meta : new boolean[] { true, false }) {
         for(String sql : new String[] {
            "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"CUSTOMERS\" c) d",
            "select * from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d",
            "select d.* from (select C.customer_id, C.city from customers C) d",
            "select d.* from (select c.CITY as TOWN, c.CUSTOMER_ID from CUSTOMERS c) d",
            "select d.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from CUSTOMERS) d",
            "select d.* from (select c.* from CUSTOMERS c) d",
            "select e.* from (select d.* from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d) e" })
         {
            if(meta || !sql.contains("c.*")) {
               assertSameRows(sql, fixed(sql, "derby", meta));
            }
         }
      }
   }

   // an expression of an inner column is not named as the column
   @Test
   void expressionIsNotStripped() throws Exception {
      for(String key : new String[] { "derby", "postgresql" }) {
         UniformSQL inner = parsed(
            "select c.CITY || c.CITY from CUSTOMERS c", helper(key));

         assertEquals(inner.getSelection().getColumn(0),
                      XUtil.getSubQueryColumn(inner.getSelection().getColumn(0), inner, null),
                      key);
      }
   }

   /**
    * An inner table without an alias is referenced by its last name segment (the reopened
    * report: "CUSTOMERS"."CITY" from "public"."CUSTOMERS"), or by more segments than the FROM
    * writes. The derived table's columns are named as when the table has an alias.
    */
   @Test
   void unaliasedTableNamesTheInnerColumns() throws Exception {
      for(boolean meta : new boolean[] { true, false }) {
         // the reported sql
         assertExpanded("postgresql", meta,
            "select d.* from (select \"CUSTOMERS\".\"CUSTOMER_ID\", \"CUSTOMERS\".\"CITY\" from \"public\".\"CUSTOMERS\") d",
            "[d.CITY, d.CUSTOMER_ID]",
            "select d.\"CITY\", d.\"CUSTOMER_ID\" from");
         assertExpanded("derby", meta,
            "select d.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from \"public\".CUSTOMERS) d",
            "[d.CITY, d.CUSTOMER_ID]", null);
         // the usual way to write it on PostgreSQL
         assertExpanded("postgresql", meta,
            "select d.* from (select customers.customer_id, customers.city from public.customers) d",
            "[d.\"city\", d.\"customer_id\"]",
            "select d.\"city\", d.\"customer_id\" from");
      }

      // qualifier form x FROM form, each the same as the aliased form of the sql
      String[][] pairs = {
         { "select d.* from (select \"CUSTOMERS\".\"CUSTOMER_ID\", \"CUSTOMERS\".\"CITY\" from \"public\".\"CUSTOMERS\") d",
           "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"public\".\"CUSTOMERS\" c) d" },
         { "select * from (select \"CUSTOMERS\".\"CUSTOMER_ID\", \"CUSTOMERS\".\"CITY\" from \"public\".\"CUSTOMERS\") d",
           "select * from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"public\".\"CUSTOMERS\" c) d" },
         { "select d.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from \"public\".CUSTOMERS) d",
           "select d.* from (select c.CUSTOMER_ID, c.CITY from \"public\".CUSTOMERS c) d" },
         { "select * from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from public.CUSTOMERS) d",
           "select * from (select c.CUSTOMER_ID, c.CITY from public.CUSTOMERS c) d" },
         { "select d.* from (select \"CUSTOMERS\".\"CITY\" from \"public\".CUSTOMERS) d",
           "select d.* from (select c.\"CITY\" from \"public\".CUSTOMERS c) d" },
         // catalog.schema.table
         { "select d.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from DB1.\"public\".CUSTOMERS) d",
           "select d.* from (select c.CUSTOMER_ID, c.CITY from DB1.\"public\".CUSTOMERS c) d" },
         { "select d.* from (select \"public\".CUSTOMERS.CITY from DB1.\"public\".CUSTOMERS) d",
           "select d.* from (select c.CITY from DB1.\"public\".CUSTOMERS c) d" },
         // the item is qualified more than the table
         { "select d.* from (select \"public\".\"CUSTOMERS\".\"CUSTOMER_ID\", \"public\".\"CUSTOMERS\".\"CITY\" from \"CUSTOMERS\") d",
           "select d.* from (select c.\"CUSTOMER_ID\", c.\"CITY\" from \"CUSTOMERS\" c) d" },
         { "select d.* from (select APP.CUSTOMERS.CUSTOMER_ID, APP.CUSTOMERS.CITY from CUSTOMERS) d",
           "select d.* from (select c.CUSTOMER_ID, c.CITY from CUSTOMERS c) d" },
         // an inner alias and an expression keep their names
         { "select d.* from (select CUSTOMERS.CITY as TOWN, CUSTOMERS.CUSTOMER_ID from \"public\".CUSTOMERS) d",
           "select d.* from (select c.CITY as TOWN, c.CUSTOMER_ID from \"public\".CUSTOMERS c) d" },
         // two tables with the same column are named the same way as with aliases, and the
         // database rejects the duplicate either way
         { "select d.* from (select CUSTOMERS.CUSTOMER_ID, ORDERS.CUSTOMER_ID, ORDERS.ORDER_ID from \"public\".CUSTOMERS, \"public\".ORDERS) d",
           "select d.* from (select a.CUSTOMER_ID, b.CUSTOMER_ID, b.ORDER_ID from \"public\".CUSTOMERS a, \"public\".ORDERS b) d" },
      };

      for(String key : new String[] { "postgresql", "derby", "mysql", "microsoft sql server" }) {
         for(boolean meta : new boolean[] { true, false }) {
            for(String[] pair : pairs) {
               String expected = sorted(fixed(pair[1], key, meta));
               assertFalse(expected.contains("c.") || expected.contains("a.") ||
                           expected.contains("b."), key + ": " + expected);
               assertExpanded(key, meta, pair[0], expected, null);
            }
         }
      }
   }

   /**
    * The rows of the unaliased forms on Derby. Derby reads a bare CUSTOMERS qualifier as the
    * table in the default schema APP, so only that schema runs as written.
    */
   @Test
   void unaliasedTableRowsMatchTheOriginal() throws Exception {
      for(String key : new String[] { "derby", "postgresql" }) {
         for(boolean meta : new boolean[] { true, false }) {
            for(String sql : new String[] {
               "select D.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from APP.CUSTOMERS) D",
               "select * from (select \"CUSTOMERS\".\"CUSTOMER_ID\", \"CUSTOMERS\".\"CITY\" from \"APP\".\"CUSTOMERS\") D",
               "select D.* from (select APP.CUSTOMERS.CUSTOMER_ID, APP.CUSTOMERS.CITY from CUSTOMERS) D",
               "select d.* from (select customers.customer_id, customers.city from app.customers) d",
               "select D.* from (select CUSTOMERS.CITY as TOWN, ORDERS.ORDER_ID from APP.CUSTOMERS, APP.ORDERS where CUSTOMERS.CUSTOMER_ID = ORDERS.CUSTOMER_ID) D",
               "select E.* from (select D.* from (select CUSTOMERS.CUSTOMER_ID, CUSTOMERS.CITY from APP.CUSTOMERS) D) E" })
            {
               // the PostgreSQL helper quotes lower-case names, which Derby reads as written
               if(!"postgresql".equals(key) || !sql.contains("customers")) {
                  assertSameRows(sql, fixed(sql, key, meta));
               }
            }
         }
      }
   }

   // only the qualifier of an unaliased table is matched by its segments
   @Test
   void unaliasedTableQualifierIsMatchedBySegments() throws Exception {
      for(String key : new String[] { "derby", "postgresql" }) {
         // an expression is not stripped
         assertNotStripped(key, "select CUSTOMERS.CITY || CUSTOMERS.CITY from \"public\".CUSTOMERS");
         // a table with an alias is referenced by the alias only
         assertNotStripped(key, "select CUSTOMERS.CITY from \"public\".CUSTOMERS c");
         // a qualifier that ends with another table name
         assertNotStripped(key, "select ORDERS.CITY from \"public\".CUSTOMERS");
         assertNotStripped(key, "select CUSTOMERS.ORDERS.CITY from \"public\".ORDERS");
      }
   }

   private static void assertNotStripped(String key, String sql) throws Exception {
      UniformSQL inner = parsed(sql, helper(key));
      String column = inner.getSelection().getColumn(0);

      assertEquals(column, XUtil.getSubQueryColumn(column, inner, null), key + ": " + sql);
   }

   private static void assertExpanded(String key, boolean meta, String sql, String columns,
                                      String generatedPrefix)
      throws Exception
   {
      UniformSQL usql = fixed(sql, key, meta);
      String label = key + " meta=" + meta + ": " + sql;

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), label);
      assertEquals(columns, sorted(usql), label);

      String generated = regenerate(usql);

      if(generatedPrefix != null) {
         assertTrue(generated.startsWith(generatedPrefix), label + " generated " + generated);
      }

      // round trip: the generated sql parses, and generates to itself from then on (the
      // postgresql helper quotes the outer qualifier, "d"."CITY", on the first pass)
      String again = regenerate(parsed(generated, helper(key)));
      assertEquals(again, regenerate(parsed(again, helper(key))), label);
   }

   private static void assertSameRows(String sql, UniformSQL usql) throws Exception {
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      String generated = regenerate(usql);

      assertFalse(generated.contains("*"), generated);
      assertEquals(rows(sql), rows(generated), sql + " generated as " + generated);
   }

   private static String sorted(UniformSQL usql) {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < usql.getSelection().getColumnCount(); i++) {
         list.add(usql.getSelection().getColumn(i));
      }

      Collections.sort(list);
      return list.toString();
   }

   private static UniformSQL fixed(String sql, String key, boolean meta) throws Exception {
      JDBCDataSource ds = helper(key);
      UniformSQL usql = parsed(sql, ds);
      JDBCUtil.fixUniformSQLInfo(usql, repository(meta), null, ds);
      return usql;
   }

   private static UniformSQL parsed(String sql, JDBCDataSource ds) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      return usql;
   }

   // the metadata of Derby, or no table metadata. SQLTypes reads the root metadata through
   // the repository bean
   private static XRepository repository(boolean meta) throws Exception {
      reset(repository);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if(!meta && mtype != null && !"DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            XTypeNode node = new XTypeNode("meta");
            node.addChild(new XTypeNode("Result"));
            return node;
         }

         JDBCHandler handler = new JDBCHandler();
         handler.setRepository(repository);
         handler.connect(inv.<XDataSource>getArgument(1), new VariableTable());
         return handler.getMetaData(mtype);
      });

      return repository;
   }

   // a unique name per case, JDBCUtil caches the columns of a table per data source
   private static JDBCDataSource helper(String key) {
      // Derby answers the metadata of both, PostgreSQLHelper generates the sql of the
      // postgresql source
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds78409" + key + "_" + (++sources) + "_" + RUN);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      ds.setRuntimeProductName(key);
      ds.setProductVersion("10.0");
      return ds;
   }

   private static String regenerate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the rows in a stable order
   private static List<String> rows(String sql) throws Exception {
      List<String> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         ResultSetMetaData meta = rs.getMetaData();
         int count = meta.getColumnCount();

         while(rs.next()) {
            SortedMap<String, String> row = new TreeMap<>();

            for(int i = 1; i <= count; i++) {
               row.put(meta.getColumnLabel(i).toUpperCase(Locale.ROOT),
                       String.valueOf(rs.getObject(i)));
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   @Autowired
   private XRepository repositoryBean;
   private static XRepository repository;
}
