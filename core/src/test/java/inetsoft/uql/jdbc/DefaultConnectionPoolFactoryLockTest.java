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
package inetsoft.uql.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Bug #79612. {@link DefaultConnectionPoolFactory} built a missing pool inside a map-wide
 * monitor, so one data source whose connect blocks stalled the pool lookups (cache hits
 * included) and builds of every other JDBC data source.
 *
 * Uses the real factory and real HikariCP; only the JDBC driver is a stub whose connect for
 * the "slow" URLs blocks on a latch.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DefaultConnectionPoolFactoryLockTest.PoolConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultConnectionPoolFactoryLockTest {
   private static final String DRIVER = StubDriver.class.getName();
   private static final long PROMPT = 5;
   private static final long WAIT = 30;

   @Configuration
   static class PoolConfig {
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
         return new DefaultConnectionPoolFactory();
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory) {
            @Override
            public ClassLoader getDriverClassLoader(String classname, String url) {
               return DefaultConnectionPoolFactoryLockTest.class.getClassLoader();
            }
         };
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }
   }

   private DefaultConnectionPoolFactory factory;
   private ExecutorService executor;

   @BeforeEach
   void setUp() {
      StubDriver.reset();
      factory = new DefaultConnectionPoolFactory();
      executor = Executors.newCachedThreadPool();
   }

   @AfterEach
   void tearDown() throws Exception {
      // release anything still blocked so no thread outlives the test
      StubDriver.release();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(WAIT, TimeUnit.SECONDS));
      factory.closeAllConnectionsPools();
   }

   @Test
   void warmHitAndOtherKeyBuildAreNotBlockedByASlowBuild() throws Exception {
      JDBCDataSource fast = source("fast", "jdbc:stub:fast");
      JDBCDataSource other = source("other", "jdbc:stub:other");
      JDBCDataSource slow = source("slow", "jdbc:stub:slow");

      DataSource warm = factory.getConnectionPool(fast, null);
      Future<DataSource> slowBuild = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(StubDriver.awaitBlocked(), "the slow connect never started");

      Future<DataSource> hit = executor.submit(() -> factory.getConnectionPool(fast, null));
      Future<DataSource> build = executor.submit(() -> factory.getConnectionPool(other, null));

      assertSame(warm, hit.get(PROMPT, TimeUnit.SECONDS),
                 "a warm hit waited for another source's build");
      assertNotNull(build.get(PROMPT, TimeUnit.SECONDS),
                    "a build of another source waited for the slow build");
      assertFalse(slowBuild.isDone());

      StubDriver.release();
      assertNotNull(slowBuild.get(WAIT, TimeUnit.SECONDS));
   }

   @Test
   void concurrentRequestsForTheSameKeyShareOnePool() throws Exception {
      JDBCDataSource slow = source("slow", "jdbc:stub:slow");

      Future<DataSource> first = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(StubDriver.awaitBlocked());
      Future<DataSource> second = executor.submit(() -> factory.getConnectionPool(slow, null));
      Future<DataSource> third = executor.submit(() -> factory.getConnectionPool(slow, null));

      // the later requests wait for the build already in flight
      Thread.sleep(200);
      assertFalse(second.isDone());
      assertFalse(third.isDone());
      assertEquals(1, StubDriver.blockedCount(), "a second build was started for the same key");

      StubDriver.release();
      DataSource pool = first.get(WAIT, TimeUnit.SECONDS);
      assertSame(pool, second.get(WAIT, TimeUnit.SECONDS));
      assertSame(pool, third.get(WAIT, TimeUnit.SECONDS));
      assertSame(pool, factory.getConnectionPool(slow, null));
   }

   @Test
   void closeDuringABuildDoesNotLeaveAPoolInTheCache() throws Exception {
      JDBCDataSource slow = source("slow", "jdbc:stub:slow");

      Future<DataSource> build = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(StubDriver.awaitBlocked());

      // closing must not wait for the build
      executor.submit(factory::closeAllConnectionsPools).get(PROMPT, TimeUnit.SECONDS);

      StubDriver.release();
      HikariDataSource built = (HikariDataSource) build.get(WAIT, TimeUnit.SECONDS);
      assertTrue(built.isClosed(), "the pool built after the close was left open");

      // and it was not cached: the next request builds a new, open pool
      HikariDataSource next = (HikariDataSource) factory.getConnectionPool(slow, null);
      assertNotSame(built, next);
      assertFalse(next.isClosed());
   }

   @Test
   void throwingCloseFilterDuringABuildDoesNotHangWaiters() throws Exception {
      JDBCDataSource slow = source("slow", "jdbc:stub:slow");

      Future<DataSource> build = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(StubDriver.awaitBlocked());
      Future<DataSource> joiner = executor.submit(() -> factory.getConnectionPool(slow, null));

      factory.closeConnectionPools(ds -> {
         throw new IllegalStateException("filter failed");
      });

      StubDriver.release();
      HikariDataSource built = (HikariDataSource) build.get(PROMPT * 4, TimeUnit.SECONDS);
      assertSame(built, joiner.get(PROMPT * 4, TimeUnit.SECONDS));
      assertTrue(built.isClosed());

      // the key is usable again
      HikariDataSource next = (HikariDataSource) factory.getConnectionPool(slow, null);
      assertNotSame(built, next);
      assertFalse(next.isClosed());
   }

   @Test
   void failedBuildIsNotCached()throws Exception {
      JDBCDataSource bad = source("bad", "jdbc:stub:bad");

      assertThrows(RuntimeException.class, () -> factory.getConnectionPool(bad, null));
      StubDriver.fail = false;
      assertNotNull(factory.getConnectionPool(bad, null));
   }

   private static JDBCDataSource source(String name, String url) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver(DRIVER);
      ds.setURL(url);
      ds.setRequireLogin(false);
      return ds;
   }

   public static class StubDriver implements Driver {
      static volatile CountDownLatch release = new CountDownLatch(1);
      static final AtomicInteger blocked = new AtomicInteger();
      static final Semaphore started = new Semaphore(0);
      static volatile boolean fail = true;

      static void reset() {
         release = new CountDownLatch(1);
         blocked.set(0);
         started.drainPermits();
         fail = true;
      }

      static void release() {
         release.countDown();
      }

      static boolean awaitBlocked() throws InterruptedException {
         return started.tryAcquire(WAIT, TimeUnit.SECONDS);
      }

      static int blockedCount() {
         return blocked.get();
      }

      @Override
      public Connection connect(String url, Properties info) throws SQLException {
         if(url.contains(":slow")) {
            blocked.incrementAndGet();
            started.release();

            try {
               if(!release.await(WAIT, TimeUnit.SECONDS)) {
                  throw new SQLException("timed out");
               }
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new SQLException(e);
            }
            finally {
               blocked.decrementAndGet();
            }
         }
         else if(url.contains(":bad") && fail) {
            throw new SQLException("unreachable");
         }

         return mock(Connection.class, withSettings().stubOnly());
      }

      @Override
      public boolean acceptsURL(String url) {
         return true;
      }

      @Override
      public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
         return new DriverPropertyInfo[0];
      }

      @Override
      public int getMajorVersion() {
         return 1;
      }

      @Override
      public int getMinorVersion() {
         return 0;
      }

      @Override
      public boolean jdbcCompliant() {
         return false;
      }

      @Override
      public Logger getParentLogger() throws SQLFeatureNotSupportedException {
         throw new SQLFeatureNotSupportedException();
      }
   }
}
