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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Bug #79612, further scenarios for {@link DefaultConnectionPoolFactory}: a failed build seen by
 * waiting callers, closes racing builds and cache hits, an interrupted waiter, the real caller
 * {@link JDBCHandler#getConnectionPool} and the cache key fields.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DefaultConnectionPoolFactoryRaceTest.PoolConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultConnectionPoolFactoryRaceTest {
   private static final String DRIVER = GateDriver.class.getName();
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
               return DefaultConnectionPoolFactoryRaceTest.class.getClassLoader();
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
      GateDriver.reset();
      factory = new DefaultConnectionPoolFactory();
      executor = Executors.newCachedThreadPool();
   }

   @AfterEach
   void tearDown() throws Exception {
      GateDriver.release();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(WAIT, TimeUnit.SECONDS));
      factory.closeAllConnectionsPools();
      ConnectionPoolFactory.getInstance().closeAllConnectionsPools();
   }

   /** Waiters of a build that fails get the failure, and the next request retries. */
   @Test
   void waitersOfAFailedBuildGetTheFailureAndALaterRequestRetries() throws Exception {
      JDBCDataSource gate = source("gate", "jdbc:gate:one");
      GateDriver.fail.set(true);

      Future<DataSource> first = executor.submit(() -> factory.getConnectionPool(gate, null));
      assertTrue(GateDriver.started.tryAcquire(WAIT, TimeUnit.SECONDS));
      Future<DataSource> second = executor.submit(() -> factory.getConnectionPool(gate, null));
      Future<DataSource> third = executor.submit(() -> factory.getConnectionPool(gate, null));
      Thread.sleep(200);
      assertFalse(second.isDone());

      GateDriver.release();

      for(Future<DataSource> f : List.of(first, second, third)) {
         ExecutionException e =
            assertThrows(ExecutionException.class, () -> f.get(WAIT, TimeUnit.SECONDS));
         assertInstanceOf(RuntimeException.class, e.getCause());
      }

      // no stuck placeholder: a later request builds again and succeeds
      GateDriver.fail.set(false);
      DataSource ok = executor.submit(() -> factory.getConnectionPool(gate, null))
         .get(WAIT, TimeUnit.SECONDS);
      assertNotNull(ok);
      assertSame(ok, factory.getConnectionPool(gate, null));
   }

   /** The caller sees the exception type and driver message HikariCP raised. */
   @Test
   void failureSeenByCallerCarriesTheDriverMessage() {
      JDBCDataSource gate = source("gate", "jdbc:gate:msg");
      GateDriver.fail.set(true);
      GateDriver.release();

      RuntimeException e = assertThrows(RuntimeException.class,
                                        () -> factory.getConnectionPool(gate, null));
      Throwable t = e;
      boolean found = false;

      while(t != null) {
         if(t.getMessage() != null && t.getMessage().contains("gate unreachable")) {
            found = true;
         }

         t = t.getCause();
      }

      assertTrue(found, "driver message lost: " + e);
      assertEquals("com.zaxxer.hikari.pool.HikariPool$PoolInitializationException",
                   e.getClass().getName());
   }

   /** Closing a built pool, or a non-matching filter, does not wait for or hurt another build. */
   @Test
   void closeOfABuiltPoolWhileAnotherBuildIsInFlight() throws Exception {
      JDBCDataSource fast = source("fast", "jdbc:gate:fast");
      JDBCDataSource slow = source("slow", "jdbc:gate:slow");
      HikariDataSource warm = (HikariDataSource) factory.getConnectionPool(fast, null);

      Future<DataSource> build = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(GateDriver.started.tryAcquire(WAIT, TimeUnit.SECONDS));

      executor.submit(() -> factory.closeConnectionPool(warm)).get(5, TimeUnit.SECONDS);
      assertTrue(warm.isClosed());

      // a filter that does not match the pool under construction leaves it alone
      executor.submit(() -> factory.closeConnectionPools(ds -> ds == warm))
         .get(5, TimeUnit.SECONDS);
      GateDriver.release();
      HikariDataSource built = (HikariDataSource) build.get(WAIT, TimeUnit.SECONDS);
      assertFalse(built.isClosed());
      assertSame(built, factory.getConnectionPool(slow, null));

      // the closed pool was removed, so the next request builds a new open one
      HikariDataSource again = (HikariDataSource) factory.getConnectionPool(fast, null);
      assertNotSame(warm, again);
      assertFalse(again.isClosed());
   }

   /** Closes racing builds and hits: no deadlock, and no pool is left open after the last close. */
   @Test
   void closesRacingBuildsAndHitsLeaveNoOpenPool() throws Exception {
      GateDriver.release();
      List<JDBCDataSource> sources = new ArrayList<>();

      for(int i = 0; i < 6; i++) {
         sources.add(source("s" + i, "jdbc:gate:s" + i));
      }

      Set<DataSource> seen = ConcurrentHashMap.newKeySet();
      AtomicBoolean stop = new AtomicBoolean();
      List<Future<?>> workers = new ArrayList<>();

      for(int t = 0; t < 8; t++) {
         final int id = t;
         workers.add(executor.submit(() -> {
            Random r = new Random(id);

            while(!stop.get()) {
               seen.add(factory.getConnectionPool(sources.get(r.nextInt(sources.size())), null));
            }

            return null;
         }));
      }

      for(int t = 0; t < 3; t++) {
         final int id = t;
         workers.add(executor.submit(() -> {
            Random r = new Random(100 + id);

            while(!stop.get()) {
               switch(r.nextInt(3)) {
               case 0:
                  factory.closeAllConnectionsPools();
                  break;
               case 1:
                  factory.closeConnectionPools(ds -> r.nextBoolean());
                  break;
               default:
                  for(DataSource ds : seen) {
                     if(r.nextInt(4) == 0) {
                        factory.closeConnectionPool(ds);
                        break;
                     }
                  }
               }
            }

            return null;
         }));
      }

      Thread.sleep(3000);
      stop.set(true);

      for(Future<?> f : workers) {
         f.get(WAIT, TimeUnit.SECONDS); // a deadlock or an exception fails here
      }

      factory.closeAllConnectionsPools();

      for(DataSource ds : seen) {
         assertTrue(((HikariDataSource) ds).isClosed(), "a pool leaked open after the last close");
      }

      // nothing resurrected: the cache is empty, so the next request builds a fresh open pool
      HikariDataSource fresh = (HikariDataSource) factory.getConnectionPool(sources.get(0), null);
      assertFalse(seen.contains(fresh));
      assertFalse(fresh.isClosed());
   }

   /** A waiter that is cancelled with an interrupt does not disturb the build or the cache. */
   @Test
   void interruptedWaiterDoesNotDisturbTheBuild() throws Exception {
      JDBCDataSource slow = source("slow", "jdbc:gate:slow2");
      Future<DataSource> first = executor.submit(() -> factory.getConnectionPool(slow, null));
      assertTrue(GateDriver.started.tryAcquire(WAIT, TimeUnit.SECONDS));

      Future<DataSource> waiter = executor.submit(() -> factory.getConnectionPool(slow, null));
      Thread.sleep(200);
      assertFalse(waiter.isDone());
      assertTrue(waiter.cancel(true));

      GateDriver.release();
      DataSource pool = first.get(WAIT, TimeUnit.SECONDS);
      assertNotNull(pool);
      assertSame(pool, factory.getConnectionPool(slow, null));
   }

   /** The real caller, with the real factory bean and the stub driver. */
   @Test
   void jdbcHandlerGetConnectionPoolUsesTheFactory() throws Exception {
      GateDriver.release();
      JDBCDataSource ds = source("h", "jdbc:gate:handler");
      DataSource a = JDBCHandler.getConnectionPool(ds, null);
      assertSame(a, JDBCHandler.getConnectionPool(ds, null));
      assertNotNull(a.getConnection());
   }

   /** Each key field distinguishes a pool; equal keys share one. */
   @Test
   void cacheKeyFields() throws Exception {
      GateDriver.release();
      JDBCDataSource base = source("k", "jdbc:gate:key");
      DataSource p = factory.getConnectionPool(base, null);
      assertSame(p, factory.getConnectionPool(source("k", "jdbc:gate:key"), null));

      JDBCDataSource url = source("k", "jdbc:gate:key2");
      JDBCDataSource name = source("k2", "jdbc:gate:key");
      JDBCDataSource iso = source("k", "jdbc:gate:key");
      iso.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      JDBCDataSource cat = source("k", "jdbc:gate:key");
      cat.setDefaultDatabase("other");
      JDBCDataSource login = source("k", "jdbc:gate:key");
      login.setRequireLogin(true);
      login.setUser("u");
      login.setPassword("p");
      JDBCDataSource login2 = source("k", "jdbc:gate:key");
      login2.setRequireLogin(true);
      login2.setUser("u2");
      login2.setPassword("p");
      JDBCDataSource drv = source("k", "jdbc:gate:key");
      drv.setDriver(GateDriver2.class.getName());

      Set<DataSource> pools = Collections.newSetFromMap(new IdentityHashMap<>());
      pools.add(p);

      for(JDBCDataSource d : List.of(url, name, iso, cat, login, login2, drv)) {
         assertTrue(pools.add(factory.getConnectionPool(d, null)),
                    "key collision for " + d.getName() + " " + d.getURL());
      }
   }

   private static JDBCDataSource source(String name, String url) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver(DRIVER);
      ds.setURL(url);
      ds.setRequireLogin(false);
      return ds;
   }

   public static class GateDriver2 extends GateDriver {
   }

   public static class GateDriver implements Driver {
      static volatile CountDownLatch release = new CountDownLatch(1);
      static final Semaphore started = new Semaphore(0);
      static final AtomicBoolean fail = new AtomicBoolean();

      static void reset() {
         release = new CountDownLatch(1);
         started.drainPermits();
         fail.set(false);
      }

      static void release() {
         release.countDown();
      }

      @Override
      public Connection connect(String url, Properties info) throws SQLException {
         if(url.contains(":slow") || fail.get()) {
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

            if(fail.get()) {
               throw new SQLException("gate unreachable");
            }
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
