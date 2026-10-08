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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XNode;
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
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77801: complements {@link XEngineDerivedMetaDataFailureTest}. Metadata keys built on
 * DBPROPERTIES (TABLETYPES, SCHEMAS, SCHEMATABLES_*, SCHEMAPROCEDURES) that are fetched while
 * a cached DBPROPERTIES failure is still inside its retry window must not return a wrong
 * result or write one to the on-disk metadata cache, both when DBPROPERTIES fails first and
 * when the derived keys failed first in the outage and are retried inside the window. Once
 * the windows end, every key, also when reloaded from disk, matches a data source that never
 * had an outage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XEngineDerivedMetaDataDiskCacheTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEngineDerivedMetaDataDiskCacheTest {
   private static final AtomicBoolean DOWN = new AtomicBoolean(false);

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

      // embedded Derby behind a proxy that fails getConnection() while DOWN is set, which
      // is where a real pool fails during an outage
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         EmbeddedDataSource derby = new EmbeddedDataSource();
         derby.setDatabaseName("memory:bug77801disk");
         derby.setCreateDatabase("create");
         DataSource ds = (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
            (p, m, a) -> {
               if(m.getName().equals("getConnection") && DOWN.get()) {
                  throw new SQLException("simulated database outage");
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

      // the real metadata cache, so that JDBCHandler.getRootMetaData() goes through it
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

   private static final String[] DERIVED =
      { "TABLETYPES", "SCHEMAS", "SCHEMATABLES_TABLE", "SCHEMAPROCEDURES" };
   private static final AtomicInteger CONTROL_COUNT = new AtomicInteger();

   @Autowired
   private XEngine engine;
   private String savedMetadataDir;
   private File metaDir;
   private final Object session = System.getProperty("user.name");

   @BeforeEach
   void setUp(@TempDir Path tempDir) throws Exception {
      savedMetadataDir = SreeEnv.getProperty("inetsoft.metadata.dir");
      metaDir = tempDir.toFile();
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());
      DOWN.set(false);

      try(Connection c = new JDBCHandler().getConnection(createDataSource("setup"), null);
          Statement s = c.createStatement())
      {
         try { s.executeUpdate("create schema S1"); } catch(SQLException ignore) { }
         try { s.executeUpdate("create table S1.T1 (A INT)"); } catch(SQLException ignore) { }
         try { s.executeUpdate("create table APP.T0 (A INT)"); } catch(SQLException ignore) { }

         try {
            s.executeUpdate("create procedure APP.P0() parameter style java language java " +
                            "external name 'java.lang.System.gc'");
         }
         catch(SQLException ignore) {
         }
      }
   }

   @AfterEach
   void tearDown() {
      DOWN.set(false);

      if(savedMetadataDir == null) {
         SreeEnv.remove("inetsoft.metadata.dir");
      }
      else {
         SreeEnv.setProperty("inetsoft.metadata.dir", savedMetadataDir);
      }
   }

   @Test
   void propertiesFailureFirst() throws Exception {
      Map<String, String> expected = getControlResults();
      String name = "bug77801diskprops";
      JDBCDataSource ds = createDataSource(name);

      // a direct DBPROPERTIES request fails during the outage, then the database comes back
      DOWN.set(true);
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");
      DOWN.set(false);

      assertWindowFetchesNotWrong(name, ds, expected);
      assertRecovered(name, ds, expected);
   }

   @Test
   void derivedFailureFirst() throws Exception {
      Map<String, String> expected = getControlResults();
      String name = "bug77801diskderived";
      JDBCDataSource ds = createDataSource(name);

      // the derived keys fail at getConnection() during the outage, then DBPROPERTIES fails
      DOWN.set(true);

      for(String type : DERIVED) {
         getMetaData(ds, type);
      }

      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");
      DOWN.set(false);

      // the derived keys' windows end first, so they are retried while DBPROPERTIES is
      // still a cached failure
      getFailureTimes().replaceAll((k, v) -> k.contains("DBPROPERTIES") ? v : v - 10_000_000L);

      assertWindowFetchesNotWrong(name, ds, expected);
      assertRecovered(name, ds, expected);
   }

   /**
    * Gets the derived metadata of a data source without an outage, and checks that it is
    * correct, written to disk and reloaded unchanged from disk.
    */
   private Map<String, String> getControlResults() throws Exception {
      String name = "bug77801diskcontrol" + CONTROL_COUNT.incrementAndGet();
      JDBCDataSource ds = createDataSource(name);
      Map<String, String> results = new LinkedHashMap<>();
      new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");

      for(String type : DERIVED) {
         results.put(type, describe(getMetaData(ds, type), name));
      }

      assertTrue(getFailureTimes().keySet().stream().noneMatch(k -> isKeyOf(k, name)));
      assertTrue(results.get("TABLETYPES").contains("hasSchema=true defaultSchema=APP"),
                 results.get("TABLETYPES"));
      assertTrue(results.get("SCHEMAS").contains("APP(APP)") &&
                 results.get("SCHEMAS").contains("S1(S1)"), results.get("SCHEMAS"));
      assertTrue(results.get("SCHEMATABLES_TABLE").endsWith("children=[T0(APP)]"),
                 results.get("SCHEMATABLES_TABLE"));
      assertTrue(results.get("SCHEMAPROCEDURES").endsWith("children=[P0(APP)]"),
                 results.get("SCHEMAPROCEDURES"));
      waitForCacheFiles(name, DERIVED.length + 1);
      assertReloadedFromDisk(name, ds, results);
      return results;
   }

   /**
    * Inside the DBPROPERTIES failure window, each derived key must be a recorded, empty
    * failure rather than a result built on the empty properties, and nothing of the data
    * source may be written to the metadata directory.
    */
   private void assertWindowFetchesNotWrong(String name, JDBCDataSource ds,
                                            Map<String, String> expected)
      throws Exception
   {
      for(String type : DERIVED) {
         XNode node = getMetaData(ds, type);
         String result = describe(node, name);
         boolean failed = getFailureTimes().keySet().stream()
            .anyMatch(k -> isKeyOf(k, name) && k.contains(type));

         assertTrue(failed && node.getChildCount() == 0 && result.contains("attrs={}") ||
                    result.equals(expected.get(type)),
                    type + " inside the window: " + result + ", expected: " + expected.get(type));
      }

      // give the asynchronous cache writers time to run
      Thread.sleep(500);
      assertEquals(List.of(), getCacheFiles(name));
   }

   /**
    * Once the failure windows end, every derived key matches the control, in memory and
    * after reloading from disk.
    */
   private void assertRecovered(String name, JDBCDataSource ds, Map<String, String> expected)
      throws Exception
   {
      getFailureTimes().replaceAll((k, v) -> v - 10_000_000L);

      for(String type : DERIVED) {
         assertEquals(expected.get(type), describe(getMetaData(ds, type), name), type);
      }

      waitForCacheFiles(name, DERIVED.length + 1);
      assertReloadedFromDisk(name, ds, expected);
   }

   private void assertReloadedFromDisk(String name, JDBCDataSource ds,
                                       Map<String, String> expected)
      throws Exception
   {
      getMetaDataCache().keySet().removeIf(k -> isKeyOf(k, name));

      for(String type : DERIVED) {
         assertEquals(expected.get(type), describe(getMetaData(ds, type), name),
                      type + " reloaded from disk");
      }
   }

   private XNode getMetaData(JDBCDataSource ds, String type) throws Exception {
      XNode mtype = new XNode();
      mtype.setAttribute("type", type);

      if(type.startsWith("SCHEMATABLES_")) {
         mtype.setAttribute("tableType", type.substring("SCHEMATABLES_".length()));
      }

      return engine.getMetaData(session, ds, mtype, true, null);
   }

   /**
    * Describes the parts of a metadata node that are derived from DBPROPERTIES: the root
    * name and properties, and the children with their schema qualification.
    */
   private static String describe(XNode node, String name) {
      StringBuilder result = new StringBuilder();
      result.append("name=").append(name.equals(node.getName()) ? "DS" : node.getName())
         .append(" attrs={");
      List<String> attrs = new ArrayList<>();

      for(String attr : new String[] { "hasSchema", "defaultSchema", "defaultCatalog" }) {
         if(node.getAttribute(attr) != null) {
            attrs.add(attr + "=" + node.getAttribute(attr));
         }
      }

      List<String> children = new ArrayList<>();

      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         Object schema = child.getAttribute("schema");
         children.add(child.getName() + (schema != null ? "(" + schema + ")" : ""));
      }

      Collections.sort(children);
      return result.append(String.join(" ", attrs)).append("} children=")
         .append(children).toString();
   }

   private static boolean isKeyOf(String key, String name) {
      return key.contains("__" + name + "__");
   }

   private List<String> getCacheFiles(String name) {
      List<String> files = new ArrayList<>();
      File[] list = metaDir.listFiles();

      if(list != null) {
         for(File file : list) {
            if(isKeyOf(file.getName(), name)) {
               files.add(file.getName());
            }
         }
      }

      return files;
   }

   private void waitForCacheFiles(String name, int count) throws Exception {
      long end = System.currentTimeMillis() + 10_000L;

      while(getCacheFiles(name).size() < count && System.currentTimeMillis() < end) {
         Thread.sleep(50);
      }

      assertEquals(count, getCacheFiles(name).size(), getCacheFiles(name).toString());
   }

   @SuppressWarnings("unchecked")
   private Map<String, Long> getFailureTimes() throws Exception {
      Field field = XEngine.class.getDeclaredField("metaDataFailureTimes");
      field.setAccessible(true);
      return (Map<String, Long>) field.get(engine);
   }

   @SuppressWarnings("unchecked")
   private Map<String, XNode> getMetaDataCache() throws Exception {
      Field field = XEngine.class.getDeclaredField("metaDataCache");
      field.setAccessible(true);
      return (Map<String, XNode>) field.get(engine);
   }

   private static JDBCDataSource createDataSource(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug77801disk");
      ds.setRequireLogin(false);
      return ds;
   }
}
