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
package inetsoft.uql.service;

import inetsoft.report.XSessionManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XSequenceNode;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77938: {@link JDBCHandler} handlers are created per session and data source, but the
 * metadata cache is shared by all sessions and persisted to disk. A handler that didn't
 * load DBPROPERTIES, SCHEMAS or SCHEMATABLES itself (another session, a recreated handler,
 * or any handler after a restart when those come from the disk cache) used to treat the
 * data source as having no schemas. Unqualified PRIMARYKEY, KEYRELATION, table column and
 * PROCEDURE requests then matched the name in every schema instead of the default schema,
 * procedure lists lost their schema, and the wrong results were cached for everyone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCHandlerDefaultSchemaMetaDataTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCHandlerDefaultSchemaMetaDataTest {
   private static final AtomicBoolean DOWN = new AtomicBoolean(false);
   private static final AtomicReference<String> LOGIN = new AtomicReference<>();

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
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      // embedded Derby behind a proxy that fails getConnection() while DOWN is set, and
      // connects as the LOGIN user while it is set
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         EmbeddedDataSource derby = new EmbeddedDataSource();
         derby.setDatabaseName("memory:bug77938");
         derby.setCreateDatabase("create");
         DataSource ds = (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
            (p, m, a) -> {
               if(m.getName().equals("getConnection") && DOWN.get()) {
                  throw new SQLException("simulated database outage");
               }

               if(m.getName().equals("getConnection") && LOGIN.get() != null) {
                  return derby.getConnection(LOGIN.get(), LOGIN.get());
               }

               try {
                  return m.invoke(derby, a);
               }
               catch(InvocationTargetException e) {
                  throw e.getCause();
               }
            });
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory)
         throws Exception
      {
         // the driver-plugin class loader is empty in the unit test JVM, so fall back to the
         // application class loader for the handler classes
         Drivers drivers = spy(new Drivers(plugins, connectionPoolFactory));
         doAnswer(inv -> {
            Object r = inv.callRealMethod();
            return r != null ? r : Class.forName(inv.getArgument(0));
         }).when(drivers).getDriverClass(anyString());
         return drivers;
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // the real metadata cache, so that handlers share cached DBPROPERTIES through it
      @Bean
      public XEngine xEngine(Cluster cluster, Config config,
                             ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, mock(DataSourceRegistry.class), connectionPoolFactory);
      }

      @Bean
      public XSessionManager xSessionManager() {
         return mock(XSessionManager.class);
      }
   }

   @Autowired
   private XEngine engine;
   private String savedMetadataDir;
   private File metaDir;
   private final Object userName = System.getProperty("user.name");

   @BeforeEach
   void setUp(@TempDir Path tempDir) throws Exception {
      savedMetadataDir = SreeEnv.getProperty("inetsoft.metadata.dir");
      metaDir = tempDir.toFile();
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());
      DOWN.set(false);

      // TP and TF exist in the default schema APP and in S1, ONLYS1 only in S1
      try(Connection c = new JDBCHandler().getConnection(createDataSource("setup"), null);
          Statement s = c.createStatement())
      {
         String[] ddl = {
            "create schema S1",
            "create table S1.TP (PA INT NOT NULL PRIMARY KEY, XS1 INT)",
            "create table APP.TP (PB INT NOT NULL PRIMARY KEY, YAPP INT)",
            "create table S1.TPARENT (ID INT NOT NULL PRIMARY KEY)",
            "create table APP.TPARENT (ID INT NOT NULL PRIMARY KEY)",
            "create table S1.TF (FS1 INT REFERENCES S1.TPARENT(ID))",
            "create table APP.TF (FAPP INT REFERENCES APP.TPARENT(ID))",
            "create table S1.ONLYS1 (OA INT NOT NULL PRIMARY KEY, OB INT)",
            "create procedure APP.PROC1() parameter style java language java no sql " +
               "external name 'java.lang.System.gc'"
         };

         for(String sql : ddl) {
            try { s.executeUpdate(sql); } catch(SQLException ignore) { }
         }
      }
   }

   @AfterEach
   void tearDown() throws Exception {
      DOWN.set(false);
      LOGIN.set(null);
      waitForCacheWrites();

      if(savedMetadataDir == null) {
         SreeEnv.remove("inetsoft.metadata.dir");
      }
      else {
         SreeEnv.setProperty("inetsoft.metadata.dir", savedMetadataDir);
      }
   }

   @Test
   void coldHandlerUsesDefaultSchema() throws Exception {
      JDBCDataSource ds = createDataSource("bug77938cold");
      // DBPROPERTIES is loaded by the handler of the user.name session, so the handlers of
      // the other sessions never load it themselves
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");

      assertEquals(List.of("APP.PB"), getKeys(getMetaData("session1", ds, "TP", "PRIMARYKEY"),
                                              "pkTableSchem", "pkColumnName"));
      assertEquals(List.of("APP.FAPP"), getKeys(getMetaData("session2", ds, "TF", "KEYRELATION"),
                                                "fkTableSchem", "fkColumnName"));
      assertEquals("schema=APP columns=[PB, YAPP]",
                   describeColumns(getMetaData("session3", ds, "TP", null)));
      assertEquals("APP", getMetaData("session4", ds, "PROC1", "PROCEDURE").getAttribute("schema"));
   }

   @Test
   void coldHandlerQualifiesProcedures() throws Exception {
      JDBCDataSource ds = createDataSource("bug77938procs");
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");

      assertEquals(List.of("PROC1(APP)"),
                   describeChildren(getMetaData("session1", ds, null, "SCHEMAPROCEDURES")));
   }

   @Test
   void coldHandlerQualifiesProceduresOfSchema() throws Exception {
      JDBCDataSource ds = createDataSource("bug77938schemaprocs");
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");

      // a request for the schema, as the data source tree sends it
      XNode mtype = new XNode();
      mtype.setAttribute("type", "SCHEMAPROCEDURES");
      mtype.setAttribute("schema", "APP");
      assertEquals(List.of("PROC1(APP)"),
                   describeChildren(engine.getMetaData("session1", ds, mtype, true, null)));
   }

   @Test
   void restartWithDiskCacheUsesDefaultSchema() throws Exception {
      String name = "bug77938restart";
      JDBCDataSource ds = createDataSource(name);
      getMetaData(userName, ds, null, "DBPROPERTIES");
      getMetaData(userName, ds, null, "SCHEMAS");
      waitForCacheWrites();

      // a restart drops the memory cache and the handlers, the metadata files remain
      getMetaDataCache().keySet().removeIf(k -> k.contains("__" + name + "__"));
      getSessionRun().clear();
      List<String> schemas = describeChildren(getMetaData(userName, ds, null, "SCHEMAS"));
      assertTrue(schemas.containsAll(List.of("APP(APP)", "S1(S1)")), "SCHEMAS reloaded from disk: " + schemas);

      assertEquals(List.of("APP.PB"), getKeys(getMetaData(userName, ds, "TP", "PRIMARYKEY"),
                                              "pkTableSchem", "pkColumnName"));
      assertEquals("schema=APP columns=[PB, YAPP]",
                   describeColumns(getMetaData(userName, ds, "TP", null)));
   }

   @Test
   void coldHandlerFailsOnCachedPropertiesFailure() throws Exception {
      JDBCDataSource ds = createDataSource("bug77938outage");

      // DBPROPERTIES fails during an outage and is cached as a failure, then the database
      // comes back inside the failure window
      DOWN.set(true);
      assertTrue(XEngine.isMetaDataFailure(new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES")));
      DOWN.set(false);

      // the unqualified request fails like the derived metadata of Bug #77801 instead of
      // being cached as matching every schema
      XNode keys = getMetaData("session1", ds, "TP", "PRIMARYKEY");
      assertTrue(XEngine.isMetaDataFailure(keys));
      assertEquals(0, keys.getChildCount());
   }

   @Test
   void unqualifiedTableOnlyInOtherSchemaIsNotFound() throws Exception {
      // a cold handler resolves an unqualified name in the default schema like a warm
      // handler, so a table that exists only in another schema has no columns or keys
      JDBCDataSource cold = createDataSource("bug77938onlycold");
      new JDBCHandler().getRootMetaData(cold, "DBPROPERTIES");
      JDBCDataSource warm = createDataSource("bug77938onlywarm");
      getMetaData("session2", warm, null, "SCHEMAS");

      Map<Object, JDBCDataSource> tests = new LinkedHashMap<>();
      tests.put("session1", cold);
      tests.put("session2", warm);

      for(Map.Entry<Object, JDBCDataSource> test : tests.entrySet()) {
         Object session = test.getKey();
         JDBCDataSource ds = test.getValue();
         assertEquals("schema=APP columns=[]",
                      describeColumns(getMetaData(session, ds, "ONLYS1", null)), ds.getName());
         assertEquals(List.of(), getKeys(getMetaData(session, ds, "ONLYS1", "PRIMARYKEY"),
                                         "pkTableSchem", "pkColumnName"), ds.getName());
      }
   }

   @Test
   void loginNameThatIsNotASchemaIsNotUsedAsDefaultSchema() throws Exception {
      String name = "bug77938login";
      JDBCDataSource ds = createDataSource(name);
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");

      // without a default schema lookup for the database type, the login name is the only
      // guess of the default schema, and here no schema has that name
      getMetaDataCache().forEach((key, node) -> {
         if(key.contains("__" + name + "__") && key.contains("DBPROPERTIES")) {
            node.setAttribute("defaultSchema", null);
         }
      });
      LOGIN.set("JDOE");

      // the name isn't qualified with the login name, which would match no table
      assertEquals("schema=null columns=[OA, OB]",
                   describeColumns(getMetaData("session1", ds, "ONLYS1", null)));
      assertEquals(List.of("S1.OA"), getKeys(getMetaData("session1", ds, "ONLYS1", "PRIMARYKEY"),
                                             "pkTableSchem", "pkColumnName"));
   }

   private XNode getMetaData(Object session, JDBCDataSource ds, String name, String type)
      throws Exception
   {
      XNode mtype = name == null ? new XNode() : new XNode(name);

      if(type != null) {
         mtype.setAttribute("type", type);
      }

      return engine.getMetaData(session, ds, mtype, true, null);
   }

   /**
    * Gets the schema and column of each key, including the keys in the sequence that
    * groups keys found in more than one table.
    */
   private static List<String> getKeys(XNode node, String schemaAttr, String columnAttr) {
      List<XNode> items = new ArrayList<>();

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);

         if(child instanceof XSequenceNode) {
            for(int j = 0; j < child.getChildCount(); j++) {
               items.add(child.getChild(j));
            }
         }
         else {
            items.add(child);
         }
      }

      List<String> keys = new ArrayList<>();

      for(XNode item : items) {
         keys.add(item.getAttribute(schemaAttr) + "." + item.getAttribute(columnAttr));
      }

      Collections.sort(keys);
      return keys;
   }

   private static List<String> describeChildren(XNode node) {
      List<String> children = new ArrayList<>();

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         Object schema = child.getAttribute("schema");
         children.add(child.getName() + (schema != null ? "(" + schema + ")" : ""));
      }

      return children;
   }

   private static String describeColumns(XNode node) {
      List<String> columns = new ArrayList<>();
      XNode result = node.getChild("Result");

      for(int i = 0; result != null && i < result.getChildCount(); i++) {
         columns.add(result.getChild(i).getName());
      }

      return "schema=" + node.getAttribute("schema") + " columns=" + columns;
   }

   // metadata files are written by background threads, wait until no more appear and each
   // one is complete, so that they are on disk and closed
   private void waitForCacheWrites() throws Exception {
      long end = System.currentTimeMillis() + 10_000L;
      int count = -1;

      while(System.currentTimeMillis() < end) {
         File[] files = metaDir.listFiles();
         int next = files == null ? 0 : files.length;

         if(next == count && Arrays.stream(files).allMatch(this::isComplete)) {
            break;
         }

         count = next;
         Thread.sleep(500);
      }
   }

   // a file is complete once the build number and the node can be read back, which is
   // only after the buffered stream was closed
   private boolean isComplete(File file) {
      try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
         in.readObject();
         in.readObject();
         return true;
      }
      catch(Exception ex) {
         return false;
      }
   }

   @SuppressWarnings("unchecked")
   private Map<String, XNode> getMetaDataCache() throws Exception {
      Field field = XEngine.class.getDeclaredField("metaDataCache");
      field.setAccessible(true);
      return (Map<String, XNode>) field.get(engine);
   }

   private Map<?, ?> getSessionRun() throws Exception {
      Field field = XEngine.class.getDeclaredField("sessionrun");
      field.setAccessible(true);
      return (Map<?, ?>) field.get(engine);
   }

   private static JDBCDataSource createDataSource(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug77938");
      ds.setRequireLogin(false);
      return ds;
   }
}
