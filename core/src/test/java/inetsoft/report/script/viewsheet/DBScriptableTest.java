/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.script.viewsheet;

import com.zaxxer.hikari.HikariDataSource;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81679, DBScriptable must not leak pooled connections. {@code init} (reached by every
 * ViewsheetScope.createConnection call after the first) replaces the connection field and must
 * close the one it replaces. {@code close()} must close the connection even while the sandbox has
 * set scriptOver, while commit/rollback/update stay no-ops under scriptOver.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  DBScriptableTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DBScriptableTest {
   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
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

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return new DefaultConnectionPoolFactory();
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         final ClassLoader loader = DBScriptableTest.class.getClassLoader();

         return new Drivers(plugins, connectionPoolFactory) {
            @Override
            public ClassLoader getDriverClassLoader(String classname, String url) {
               return loader;
            }

            @Override
            public SQLExecutor getSQLExecutor(String classname, String url) {
               return null;
            }

            @Override
            public Class<?> getDriverClass(String className) throws ClassNotFoundException {
               return Class.forName(className, true, loader);
            }

            @Override
            public <T> Class<? extends T> getDriverClass(String className, Class<T> expected)
               throws ClassNotFoundException
            {
               return Class.forName(className, false, loader).asSubclass(expected);
            }
         };
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }

      @Bean
      public XRepository xRepository(Cluster cluster, Config config,
                                     DataSourceRegistry dataSourceRegistry,
                                     ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, dataSourceRegistry, connectionPoolFactory);
      }

      @Bean
      public ColumnCache columnCache(DataSourceRegistry dataSourceRegistry, XRepository repository) {
         return new ColumnCache(dataSourceRegistry, repository);
      }

      @Bean
      public XSessionManager xSessionManager(XRepository repository, XSessionService sessionService,
                                             DataSourceRegistry dataSourceRegistry)
         throws Exception
      {
         return new XSessionManager(repository, sessionService, dataSourceRegistry);
      }
   }

   @Autowired
   DataSourceRegistry dataSourceRegistry;

   private JDBCDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new JDBCDataSource();
      ds.setName("ds81679");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:b81679;create=true");
      ds.setRequireLogin(false);
      when(dataSourceRegistry.getDataSource(eq("ds81679"))).thenReturn(ds);
   }

   private int active() {
      HikariDataSource pool = (HikariDataSource) JDBCHandler.getConnectionPool(ds, null);
      return pool.getHikariPoolMXBean().getActiveConnections();
   }

   @Test
   void initClosesReplacedConnection() throws Throwable {
      int base = active();
      DBScriptable db = new DBScriptable("ds81679", null, null, null);
      assertEquals(base + 1, active());

      // ViewsheetScope.createConnection calls init on every call after the first
      db.init("ds81679", null, null, null);
      db.init("ds81679", null, null, null);
      assertEquals(base + 1, active(), "re-init must close the replaced connection");

      db.close();
      assertEquals(base, active());
   }

   @Test
   void closeClosesUnderScriptOver() throws Throwable {
      int base = active();
      DBScriptable db = new DBScriptable("ds81679", null, null, null);
      db.setScriptOver(true);
      db.close();
      assertEquals(base, active(), "close() must release the connection under scriptOver");

      // later no-op calls of the same run must not fail after the connection is gone
      db.commit();
      db.rollback();
      db.update(null);

      // idempotent
      db.close();
      assertEquals(base, active());
   }

   @Test
   void initUnderScriptOverReleasesPrevious() throws Throwable {
      int base = active();
      DBScriptable db = new DBScriptable("ds81679", null, null, null);
      db.setScriptOver(true);
      db.init("ds81679", null, null, null);
      assertEquals(base + 1, active());
      db.close();
      assertEquals(base, active());
   }

   @Test
   void commitAndUpdateStayNoOpsUnderScriptOver() throws Throwable {
      DBScriptable db = new DBScriptable("ds81679", null, null, null);
      db.setScriptOver(true);
      db.commit();
      db.rollback();
      db.update(null);
      db.setScriptOver(false);
      db.close();
   }
}
