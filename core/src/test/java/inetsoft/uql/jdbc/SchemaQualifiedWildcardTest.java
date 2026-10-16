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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78466. The metadata node of a table name written with more qualifiers than the table
 * name option of the data source expects kept the extra qualifiers in the table name, e.g.
 * public"."CUSTOMERS with the Table option, or public"."CUSTOMERS in schema orders for
 * "orders"."public"."CUSTOMERS" on postgresql (no catalogs). No columns were found, the
 * wildcard wasn't expanded and the parse was failed (#77617), so vpm refused the query of a
 * user with hidden columns.
 * <p>
 * The mocked repository answers like postgresql: database orders, the tables
 * public.CUSTOMERS (8 columns), other.CUSTOMERS (column X) and public."my.t" (column Y), an
 * unqualified name is looked up in public.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SchemaQualifiedWildcardTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SchemaQualifiedWildcardTest {
   private static final List<String> CUSTOMERS = List.of(
      "CUSTOMER_ID", "COMPANY_NAME", "ADDRESS", "CITY", "STATE", "ZIP", "REP_ID", "CUST_TYPE");
   private static final int[] OPTIONS = {
      JDBCDataSource.DEFAULT_OPTION, JDBCDataSource.CATALOG_SCHEMA_OPTION,
      JDBCDataSource.SCHEMA_OPTION, JDBCDataSource.TABLE_OPTION };

   @Configuration
   static class JdbcConfig {
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
         return mock(ConnectionPoolFactory.class);
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

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }
   }

   @Autowired
   XRepository repository;

   @AfterEach
   void resetMocks() {
      reset(repository);
   }

   // the reported shapes, and the qualified shapes that already worked, under every option
   @Test
   void qualifiedWildcardIsExpandedUnderEveryOption() throws Exception {
      String[] sqls = {
         "select c.* from \"public\".\"CUSTOMERS\" c",
         "select * from public.\"CUSTOMERS\"",
         "select * from public.CUSTOMERS",
         "select * from \"public\".CUSTOMERS",
         "select \"CUSTOMERS\".* from \"public\".\"CUSTOMERS\"",
         "select c.* from \"orders\".\"public\".\"CUSTOMERS\" c",
         "select c.* from orders.public.CUSTOMERS c",
         "select c.* from \"CUSTOMERS\" c",
      };
      List<String> failures = new ArrayList<>();

      for(int option : OPTIONS) {
         for(String sql : sqls) {
            UniformSQL usql = fix(sql, option, "orders", false);

            if(usql.getParseResult() != UniformSQL.PARSE_SUCCESS ||
               !CUSTOMERS.equals(columnNames(usql)))
            {
               failures.add(option + ": " + sql + " -> " + usql.getParseResult() + " " +
                               columnNames(usql));
            }
         }
      }

      assertEquals(List.of(), failures);
   }

   // the written schema is looked up, never the table of the same name in another schema
   @Test
   void writtenSchemaIsKept() throws Exception {
      for(int option : OPTIONS) {
         UniformSQL other = fix("select c.* from \"other\".\"CUSTOMERS\" c", option, "orders",
                                false);
         assertEquals(UniformSQL.PARSE_SUCCESS, other.getParseResult(), "" + option);
         assertEquals(List.of("X"), columnNames(other), "" + option);

         UniformSQL nosuch = fix("select c.* from \"nosuch\".\"CUSTOMERS\" c", option, "orders",
                                 false);
         assertEquals(UniformSQL.PARSE_FAILED, nosuch.getParseResult(), "" + option);
         assertFalse(XUtil.isParsedSQL(nosuch), "" + option);
      }
   }

   // a catalog other than the database of the connection isn't resolved
   @Test
   void otherCatalogFailsClosed() throws Exception {
      for(int option : OPTIONS) {
         String sql = "select c.* from \"elsewhere\".\"public\".\"CUSTOMERS\" c";
         assertEquals(UniformSQL.PARSE_FAILED,
                      fix(sql, option, "orders", false).getParseResult(), "" + option);

         // a quoted catalog keeps its case
         sql = "select c.* from \"ORDERS\".\"public\".\"CUSTOMERS\" c";
         assertEquals(UniformSQL.PARSE_FAILED,
                      fix(sql, option, "orders", false).getParseResult(), "" + option);

         // the database of the connection is unknown
         sql = "select c.* from \"orders\".\"public\".\"CUSTOMERS\" c";
         assertEquals(UniformSQL.PARSE_FAILED,
                      fix(sql, option, null, false).getParseResult(), "" + option);
      }
   }

   // a dot in a quoted table name is not a qualifier
   @Test
   void quotedDotIsOneName() throws Exception {
      for(int option : OPTIONS) {
         UniformSQL usql = fix("select * from \"my.t\"", option, "orders", false);
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), "" + option);
         assertEquals(List.of("Y"), columnNames(usql), "" + option);

         usql = fix("select * from \"public\".\"my.t\"", option, "orders", false);
         assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), "" + option);
         assertEquals(List.of("Y"), columnNames(usql), "" + option);
      }
   }

   // a dotted table name stored unquoted (not by the parser) is still found as one name
   @Test
   void unquotedDottedNameStillResolves() throws Exception {
      JDBCDataSource ds = dataSource(JDBCDataSource.TABLE_OPTION);
      String xml = toXML(parsed("select * from \"my.t\"", ds));
      String legacy = xml.replace("<![CDATA[\"my.t\"]]>", "<![CDATA[my.t]]>")
         .replaceAll(" quotedSegments=\"[^\"]*\"", "");
      assertNotEquals(xml, legacy);
      UniformSQL usql = load(legacy);
      usql.setDataSource(ds);

      JDBCUtil.fixUniformSQLInfo(usql, repository(null, false, "orders"), null, ds);

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      assertEquals(List.of("Y"), columnNames(usql));
   }

   // the node of a metadata name is not changed: my.t is one name with the Table option
   @Test
   void metadataNameIsNotSplit() {
      JDBCDataSource ds = dataSource(JDBCDataSource.TABLE_OPTION);
      XNode node = SQLTypes.getSQLTypes(ds).getQualifiedTableNode(
         "my.t", false, false, ".", ds, null, null);
      assertEquals("my.t", node.getName());
      assertNull(node.getAttribute("schema"));
      assertNull(node.getAttribute("catalog"));
   }

   @Test
   void writtenNode() {
      JDBCDataSource table = dataSource(JDBCDataSource.TABLE_OPTION);
      JDBCDataSource schema = dataSource(JDBCDataSource.SCHEMA_OPTION);
      SQLTypes types = SQLTypes.getSQLTypes(table);
      XNode noCatalog = properties(false, false, "orders");

      // no more qualifiers than expected
      assertNull(types.getWrittenTableNode("\"CUSTOMERS\"", noCatalog, table));
      assertNull(types.getWrittenTableNode("\"my.t\"", noCatalog, table));
      assertNull(types.getWrittenTableNode("\"public\".\"CUSTOMERS\"",
                                           properties(false, true, "orders"), schema));
      // too many qualifiers, or an empty one
      assertNull(types.getWrittenTableNode("a.b.c.d", noCatalog, table));
      assertNull(types.getWrittenTableNode("a..d", noCatalog, table));

      XNode node = types.getWrittenTableNode("\"public\".\"my.t\"", noCatalog, table);
      assertEquals("my.t", node.getName());
      assertEquals("public", node.getAttribute("schema"));

      node = types.getWrittenTableNode("\"a\"\"b\".\"CUSTOMERS\"", noCatalog, table);
      assertEquals("CUSTOMERS", node.getName());
      assertEquals("a\"b", node.getAttribute("schema"));

      // the catalog of the connection is dropped when the database has no catalogs
      node = types.getWrittenTableNode("ORDERS.public.CUSTOMERS", noCatalog, table);
      assertEquals("CUSTOMERS", node.getName());
      assertEquals("public", node.getAttribute("schema"));
      assertNull(node.getAttribute("catalog"));

      // and kept when it has, whatever it is
      XNode catalogs = properties(true, false, "orders");
      node = types.getWrittenTableNode("\"db2\".\"dbo\".\"T\"", catalogs,
                                       dataSource(JDBCDataSource.DEFAULT_OPTION));
      assertEquals("T", node.getName());
      assertEquals("dbo", node.getAttribute("schema"));
      assertEquals("db2", node.getAttribute("catalog"));
      assertEquals(".", node.getAttribute("catalogSep"));
   }

   // the expanded columns are qualified with the written name, and the sql is unchanged
   @Test
   void expandedSqlKeepsTheWrittenQualification() throws Exception {
      String sql = "select * from public.\"CUSTOMERS\"";
      UniformSQL usql = fix(sql, JDBCDataSource.TABLE_OPTION, "orders", false);

      assertEquals(sql, usql.getSQLString());
      assertTrue(XUtil.isParsedSQL(usql));

      for(int i = 0; i < usql.getSelection().getColumnCount(); i++) {
         assertTrue(usql.getSelection().getColumn(i).startsWith("\"public\".\"CUSTOMERS\"."),
                    usql.getSelection().getColumn(i));
      }
   }

   // ---- helpers ----

   private UniformSQL fix(String sql, int option, String currentCatalog, boolean catalogs)
      throws Exception
   {
      JDBCDataSource ds = dataSource(option);
      UniformSQL usql = parsed(sql, ds);
      // the database properties of postgresql for the option
      boolean schema = option != JDBCDataSource.TABLE_OPTION;
      JDBCUtil.fixUniformSQLInfo(usql, repository(schema, catalogs, currentCatalog), null, ds);
      return usql;
   }

   private XRepository repository(Boolean schema, boolean catalogs, String currentCatalog)
      throws Exception
   {
      reset(repository);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);
         JDBCDataSource ds = inv.getArgument(1);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return properties(catalogs, schema != null ? schema :
               ds.getTableNameOption() != JDBCDataSource.TABLE_OPTION, currentCatalog);
         }

         XTypeNode result = new XTypeNode("Result");
         Object cat = mtype.getAttribute("catalog");
         // postgresql looks up an unqualified name in public
         Object sch = mtype.getAttribute("schema") == null ?
            "public" : mtype.getAttribute("schema");
         List<String> columns = List.of();

         if(cat == null && "public".equals(sch) && "CUSTOMERS".equals(mtype.getName())) {
            columns = CUSTOMERS;
         }
         else if(cat == null && "other".equals(sch) && "CUSTOMERS".equals(mtype.getName())) {
            columns = List.of("X");
         }
         else if(cat == null && "public".equals(sch) && "my.t".equals(mtype.getName())) {
            columns = List.of("Y");
         }

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, String.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   private static XNode properties(boolean catalogs, boolean schema, String currentCatalog) {
      XNode properties = new XNode("properties");
      properties.setAttribute("hasCatalog", "" + catalogs);
      properties.setAttribute("supportCatalog", "" + catalogs);
      properties.setAttribute("hasSchema", "" + schema);
      properties.setAttribute("catalogSep", ".");

      if(currentCatalog != null) {
         properties.setAttribute("currentCatalog", currentCatalog);
      }

      return properties;
   }

   private static UniformSQL parsed(String sql, JDBCDataSource ds) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      return usql;
   }

   private static List<String> columnNames(UniformSQL usql) {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < usql.getSelection().getColumnCount(); i++) {
         String column = usql.getSelection().getColumn(i);
         list.add(column.substring(column.lastIndexOf('.') + 1).replace("\"", ""));
      }

      return list;
   }

   private static String toXML(UniformSQL usql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return loaded;
   }

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource dataSource(int option) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds78466_" + (++sources) + "_" + RUN);
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost/orders");
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("postgresql");
      ds.setProductVersion("10.0");
      ds.setTableNameOption(option);
      return ds;
   }

   private static final long RUN = System.nanoTime();
   private static int sources;
}
