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
import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77801: a DBPROPERTIES request that failed during a database outage is cached by
 * {@link XEngine} as an empty node for {@code META_DATA_FAILURE_RETRY_INTERVAL}. A SCHEMAS,
 * SCHEMATABLES_* or TABLETYPES key fetched for the first time inside that window, after the
 * database came back, used to be built on the empty node (no schemas, unqualified tables, no
 * root properties) and then cached permanently in memory and on disk. The derived fetch must
 * fail like any other metadata failure instead, so it is retried once the window ends.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XEngineDerivedMetaDataFailureTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEngineDerivedMetaDataFailureTest {
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
         derby.setDatabaseName("memory:bug77801");
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

   @Autowired
   private XEngine engine;
   @Autowired
   private Cluster cluster;
   private String savedMetadataDir;
   private final Object session = System.getProperty("user.name");

   @BeforeEach
   void setUp(@TempDir Path tempDir) throws Exception {
      savedMetadataDir = SreeEnv.getProperty("inetsoft.metadata.dir");
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());
      DOWN.set(false);

      try(Connection c = new JDBCHandler().getConnection(createDataSource("setup"), null);
          Statement s = c.createStatement())
      {
         try { s.executeUpdate("create schema S1"); } catch(SQLException ignore) { }
         try { s.executeUpdate("create table S1.T1 (A INT)"); } catch(SQLException ignore) { }
         try { s.executeUpdate("create table APP.T0 (A INT)"); } catch(SQLException ignore) { }
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
   void derivedMetaDataIsNotBuiltOnCachedPropertiesFailure() throws Exception {
      JDBCDataSource ds = createDataSource("bug77801outage");

      // a direct DBPROPERTIES request fails during the outage and is cached as a failure
      DOWN.set(true);
      XNode props = new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES");
      assertTrue(XEngine.isMetaDataFailure(props));

      // the database comes back inside the failure window
      DOWN.set(false);
      assertTrue(XEngine.isMetaDataFailure(new JDBCHandler().getRootMetaData(ds, "DBPROPERTIES")),
                 "the cached DBPROPERTIES failure is still served inside its window");

      // first fetches of the derived keys inside the window fail instead of being built on
      // the empty properties node
      for(String type : new String[] { "TABLETYPES", "SCHEMAS", "SCHEMATABLES_TABLE" }) {
         XNode node = getMetaData(ds, type);
         assertTrue(XEngine.isMetaDataFailure(node), type + " should be a cached failure");
         assertEquals(0, node.getChildCount(), type);
      }

      // the derived failures are memory-only and expire, they weren't written as real results
      Map<String, Long> failureTimes = getFailureTimes();
      assertEquals(4, failureTimes.keySet().stream()
         .filter(k -> k.contains("bug77801outage")).count(), failureTimes.keySet().toString());

      // once the windows end, the derived keys are retried and correct
      failureTimes.replaceAll((k, v) -> v - 10_000_000L);

      XNode tableTypes = getMetaData(ds, "TABLETYPES");
      assertFalse(XEngine.isMetaDataFailure(tableTypes));
      assertEquals("true", tableTypes.getAttribute("hasSchema"));
      assertEquals("APP", tableTypes.getAttribute("defaultSchema"));
      assertTrue(tableTypes.getChildCount() > 0);

      XNode schemas = getMetaData(ds, "SCHEMAS");
      assertFalse(XEngine.isMetaDataFailure(schemas));
      Set<String> schemaNames = new HashSet<>();

      for(int i = 0; i < schemas.getChildCount(); i++) {
         schemaNames.add(schemas.getChild(i).getName());
      }

      assertTrue(schemaNames.containsAll(Set.of("APP", "S1")), schemaNames.toString());

      // without a schema, only the default schema's tables are listed, qualified
      XNode tables = getMetaData(ds, "SCHEMATABLES_TABLE");
      assertFalse(XEngine.isMetaDataFailure(tables));
      assertEquals(1, tables.getChildCount());
      assertEquals("T0", tables.getChild(0).getName());
      assertEquals("APP", tables.getChild(0).getAttribute("schema"));
   }

   /**
    * Backstop in XEngine: a handler that returns a result rooted on another key's cached
    * failure doesn't get that result written to the permanent cache.
    */
   @Test
   void resultRootedOnCachedFailureIsNotCached() throws Exception {
      XEngine testEngine = new XEngine(cluster, null, mock(DataSourceRegistry.class),
                                       mock(ConnectionPoolFactory.class))
      {
         @Override
         protected XNode getMetaDataInternal(Object session, XDataSource dx, XNode mtype)
            throws Exception
         {
            XNode base = new XNode();
            base.setAttribute("type", "BASE");

            if("BASE".equals(mtype.getAttribute("type"))) {
               throw new SQLException("simulated database outage");
            }

            // build on the (cloned) cached failure of the BASE key
            XNode root = getMetaData(session, dx, base, true, null);
            root.addChild(new XNode("CHILD"));
            return root;
         }
      };
      XDataSource dx = mock(XDataSource.class);
      when(dx.getFullName()).thenReturn("Bug77801BackstopDataSource");
      when(dx.isFromPortal()).thenReturn(true);

      XNode base = new XNode();
      base.setAttribute("type", "BASE");
      assertTrue(XEngine.isMetaDataFailure(testEngine.getMetaData(null, dx, base, true, null)));

      XNode derived = new XNode();
      derived.setAttribute("type", "DERIVED");
      XNode result = testEngine.getMetaData(null, dx, derived, true, null);
      assertTrue(XEngine.isMetaDataFailure(result));
      assertEquals(0, result.getChildCount());

      Field timesField = XEngine.class.getDeclaredField("metaDataFailureTimes");
      timesField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Long> failureTimes = (Map<String, Long>) timesField.get(testEngine);
      assertEquals(2, failureTimes.size(), failureTimes.keySet().toString());
   }

   private XNode getMetaData(JDBCDataSource ds, String type) throws Exception {
      XNode mtype = new XNode();
      mtype.setAttribute("type", type);

      if(type.startsWith("SCHEMATABLES_")) {
         mtype.setAttribute("tableType", type.substring("SCHEMATABLES_".length()));
      }

      return engine.getMetaData(session, ds, mtype, true, null);
   }

   @SuppressWarnings("unchecked")
   private Map<String, Long> getFailureTimes() throws Exception {
      Field field = XEngine.class.getDeclaredField("metaDataFailureTimes");
      field.setAccessible(true);
      return (Map<String, Long>) field.get(engine);
   }

   private static JDBCDataSource createDataSource(String name) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:bug77801");
      ds.setRequireLogin(false);
      return ds;
   }
}
