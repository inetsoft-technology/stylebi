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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.SQLBoundQuery;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.util.JDBCUtil;
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
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78383. A SQL bound table saved with a wildcard of a table left in its parsed selection
 * (select "CUSTOMERS".* ..., parseResult 0) went through the metadata step of #77617 when the
 * worksheet was opened. When the wildcard wasn't expanded there (e.g. the stored alias of the
 * table differs from its name, so the qualifier isn't resolved), the parse was failed and vpm
 * refused the query of a user with hidden columns. The stored sql is now parsed again from its
 * sql string, the way the sql dialog parses it, on a copy that replaces it.
 * <p>
 * The metadata of the mocked repository answers only the request for public.CUSTOMERS.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  StoredWildcardReparseTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StoredWildcardReparseTest {
   private static final String SQL = "select \"CUSTOMERS\".* from \"public\".\"CUSTOMERS\"";
   private static final String[] CUSTOMERS = {
      "CUSTOMER_ID", "COMPANY_NAME", "ADDRESS", "CITY", "STATE", "ZIP", "REP_ID", "CUST_TYPE" };
   private static final String ALIAS = "<alias><![CDATA[\"public\".\"CUSTOMERS\"]]></alias>";
   private static final String NAME = "<![CDATA[\"public\".\"CUSTOMERS\"]]></name>";

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

   @Autowired
   DataSourceRegistry registry;

   @AfterEach
   void resetMocks() {
      reset(repository, registry);
   }

   /**
    * The stored shapes: a 1.1 save with the data source (alias and name quoted), a hand-made
    * table whose alias differs from its name, and a 1.1 save without the data source (alias
    * and name unquoted). Each is parsed again with the columns of CUSTOMERS, CITY included.
    */
   @Test
   void storedWildcardIsExpanded() throws Exception {
      for(Map.Entry<String, String> shape : shapes().entrySet()) {
         String label = shape.getKey();
         JDBCDataSource ds = dataSource();
         UniformSQL stored = load(shape.getValue());
         String before = toXML(stored);

         UniformSQL nsql = JDBCUtil.reparseStoredWildcard(stored, strict(true), null, ds);

         assertNotNull(nsql, label);
         assertNotSame(stored, nsql, label);
         assertEquals(UniformSQL.PARSE_SUCCESS, nsql.getParseResult(), label);
         assertTrue(XUtil.isParsedSQL(nsql), label);
         assertEquals(SQL, nsql.getSQLString(), label);
         assertEquals(List.of(CUSTOMERS), columnNames(nsql), label);
         // the stored sql isn't changed
         assertEquals(before, toXML(stored), label);
         // parsed again, nothing is left to do
         assertNull(JDBCUtil.reparseStoredWildcard(nsql, strict(true), null, dataSource()),
                    label);
      }
   }

   // the reported symptom: without the re-parse, the alias that differs from the name fails
   // the parse of the stored sql even though the columns are known
   @Test
   void unresolvedStoredQualifierFailedTheParse() throws Exception {
      JDBCDataSource ds = dataSource();
      UniformSQL stored = load(shapes().get("alias != name"));
      JDBCUtil.fixUniformSQLInfo(stored, strict(true), null, ds);

      assertEquals(UniformSQL.PARSE_FAILED, stored.getParseResult());
      assertFalse(XUtil.isParsedSQL(stored));
   }

   // without the columns of the table, the re-parsed sql is still refused (#77617)
   @Test
   void noMetadataIsStillRefused() throws Exception {
      for(Map.Entry<String, String> shape : shapes().entrySet()) {
         String label = shape.getKey();
         UniformSQL nsql = JDBCUtil.reparseStoredWildcard(
            load(shape.getValue()), strict(false), null, dataSource());

         assertNotNull(nsql, label);
         assertEquals(UniformSQL.PARSE_FAILED, nsql.getParseResult(), label);
         assertFalse(XUtil.isParsedSQL(nsql), label);
         assertFalse(UniformSQL.hasWildcard(nsql.getSelection()), label);
         assertEquals(SQL, nsql.getSQLString(), label);
         // not parsed again on the next call
         assertNull(JDBCUtil.reparseStoredWildcard(nsql, strict(true), null, dataSource()),
                    label);
      }
   }

   // only a parsed sql with a sql string and a wildcard of a table is parsed again
   @Test
   void otherSqlIsNotReparsed() throws Exception {
      XRepository repo = strict(true);

      UniformSQL explicit = load(toXML(parsed(
         "select \"CUSTOMERS\".\"CITY\" from \"public\".\"CUSTOMERS\"", dataSource())));
      assertNull(JDBCUtil.reparseStoredWildcard(explicit, repo, null, dataSource()));

      UniformSQL count = load(toXML(parsed(
         "select count(*) from \"public\".\"CUSTOMERS\"", dataSource())));
      assertNull(JDBCUtil.reparseStoredWildcard(count, repo, null, dataSource()));

      // a query of the graphical builder has no sql string
      UniformSQL structure = load(shapes().get("1.1 with data source"));
      structure.clearSQLString();
      assertNull(JDBCUtil.reparseStoredWildcard(structure, repo, null, dataSource()));

      UniformSQL off = load(shapes().get("1.1 with data source"));
      off.setParseSQL(false);
      assertNull(JDBCUtil.reparseStoredWildcard(off, repo, null, dataSource()));

      UniformSQL failed = load(shapes().get("1.1 with data source"));
      failed.setParseResult(UniformSQL.PARSE_FAILED);
      assertNull(JDBCUtil.reparseStoredWildcard(failed, repo, null, dataSource()));

      verify(repo, never()).getMetaData(any(), any(), any(), anyBoolean(), any());
   }

   // a lossy sql (a USING join) is still lossy when it's parsed again
   @Test
   void lossySqlStaysLossy() throws Exception {
      String sql = "select \"CUSTOMERS\".* from \"public\".\"CUSTOMERS\" join " +
         "\"public\".\"ORDERS\" using (\"CUSTOMER_ID\")";
      UniformSQL stored = load(toXML(parsed(sql, dataSource())));
      assertTrue(UniformSQL.hasWildcard(stored.getSelection()));

      UniformSQL nsql = JDBCUtil.reparseStoredWildcard(stored, strict(true), null, dataSource());

      assertNotNull(nsql);
      assertEquals(sql, nsql.getSQLString());
      assertTrue(nsql.isLossy());
   }

   /**
    * Review: a parse in place on the stored sql left it without its sql string, and with its
    * wildcard, until the single parser thread was done. A copy taken meanwhile (e.g. for an
    * execution) must be the stored sql, with its string.
    */
   @Test
   void storedSqlIsNotChangedWhileParsing() throws Exception {
      UniformSQL stored = load(shapes().get("1.1 with data source"));
      String before = toXML(stored);
      XRepository repo = strict(true);

      // keep the parser thread busy, as another parse would
      Field field = UniformSQL.class.getDeclaredField("parserPool");
      field.setAccessible(true);
      Object pool = field.get(null);
      pool.getClass().getMethod("add", Runnable.class).invoke(pool, (Runnable) () -> {
         try {
            Thread.sleep(1500);
         }
         catch(InterruptedException ignore) {
            // done
         }
      });

      AtomicReference<UniformSQL> result = new AtomicReference<>();
      Thread heal = new Thread(() -> result.set(
         JDBCUtil.reparseStoredWildcard(stored, repo, null, dataSource())));
      heal.start();
      Thread.sleep(300);

      while(heal.isAlive()) {
         UniformSQL copy = stored.clone();
         assertTrue(copy.hasSQLString());
         assertEquals(SQL, copy.getSQLString());
         assertEquals(UniformSQL.PARSE_SUCCESS, copy.getParseResult());
         Thread.sleep(50);
      }

      heal.join();
      assertEquals(before, toXML(stored));
      assertNotNull(result.get());
      assertEquals(List.of(CUSTOMERS), columnNames(result.get()));
   }

   // the variables of the table are collected (open, refresh, save): the stored sql is replaced
   @Test
   void allVariablesReplaceTheStoredSql() throws Exception {
      JDBCDataSource ds = dataSource();
      SQLBoundTableAssembly table = newTable(ds, shapes().get("alias != name"));
      JDBCQuery query = ((SQLBoundTableAssemblyInfo) table.getTableInfo()).getQuery();
      UniformSQL stored = (UniformSQL) query.getSQLDefinition();
      strict(true);

      table.getAllVariables();

      UniformSQL nsql = (UniformSQL) query.getSQLDefinition();
      assertNotSame(stored, nsql);
      assertTrue(XUtil.isParsedSQL(nsql));
      assertEquals(List.of(CUSTOMERS), columnNames(nsql));
   }

   // a table executed without its variables collected first is expanded before the query is
   // copied for the execution
   @Test
   void sqlBoundQueryExpandsTheStoredSql() throws Exception {
      JDBCDataSource ds = dataSource();
      SQLBoundTableAssembly table = newTable(ds, shapes().get("alias != name"));
      strict(true);

      SQLBoundQuery query = new SQLBoundQuery(AssetQuerySandbox.RUNTIME_MODE,
         new AssetQuerySandbox(table.getWorksheet()), table, false, false);

      UniformSQL nsql = (UniformSQL) query.getQuery().getSQLDefinition();
      assertTrue(XUtil.isParsedSQL(nsql));
      assertFalse(UniformSQL.hasWildcard(nsql.getSelection()));
      assertEquals(List.of(CUSTOMERS), columnNames(nsql));
      assertSame(nsql, ((SQLBoundTableAssemblyInfo) table.getTableInfo()).getQuery()
         .getSQLDefinition());
   }

   // ---- helpers ----

   private SQLBoundTableAssembly newTable(JDBCDataSource ds, String xml) throws Exception {
      when(registry.getDataSource(eq(ds.getName()), any())).thenReturn(ds);
      when(registry.getDataSource(eq(ds.getName()))).thenReturn(ds);

      JDBCQuery query = new JDBCQuery();
      query.setName("bug78383");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(load(xml));

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      ws.addAssembly(table);
      return table;
   }

   // the stored xml of the shapes, from the parse of the sql before the metadata step
   private static Map<String, String> shapes() throws Exception {
      String xml = toXML(parsed(SQL, dataSource()));
      assertTrue(xml.contains(ALIAS), xml);
      assertTrue(xml.contains(NAME), xml);
      // written before the quoted segments and the schema of a table were saved
      String legacy = xml.replaceAll(" quotedSegments=\"[^\"]*\"", "")
         .replaceAll(" schema=\"[^\"]*\"", "").replaceAll(" catalog=\"[^\"]*\"", "");
      String unquoted = "<alias><![CDATA[public.CUSTOMERS]]></alias>";

      Map<String, String> shapes = new LinkedHashMap<>();
      shapes.put("1.1 with data source", legacy);
      shapes.put("alias != name", legacy.replace(ALIAS, unquoted));
      shapes.put("1.1 without data source", legacy.replace(ALIAS, unquoted)
         .replace(NAME, "<![CDATA[public.CUSTOMERS]]></name>")
         .replace("<![CDATA[\"CUSTOMERS\".*]]>", "<![CDATA[CUSTOMERS.*]]>"));
      return shapes;
   }

   /**
    * The columns of public.CUSTOMERS, only for a request with that name and schema.
    */
   private XRepository strict(boolean metadata) throws Exception {
      reset(repository);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         // the properties of a postgresql database, the table is looked up by its schema
         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            XNode properties = new XNode("properties");
            properties.setAttribute("hasCatalog", "false");
            properties.setAttribute("hasSchema", "true");
            properties.setAttribute("catalogSep", ".");
            return properties;
         }

         XTypeNode result = new XTypeNode("Result");

         if(metadata && "CUSTOMERS".equals(mtype.getName()) &&
            "public".equals(mtype.getAttribute("schema")))
         {
            for(String column : CUSTOMERS) {
               result.addChild(XSchema.createPrimitiveType(column, String.class));
            }
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
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
   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds78383_" + (++sources) + "_" + RUN);
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost/db");
      ds.setRequireLogin(false);
      ds.setRuntimeProductName("postgresql");
      ds.setProductVersion("10.0");
      return ds;
   }

   private static final long RUN = System.nanoTime();
   private static int sources;
}
