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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import inetsoft.test.*;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79485: creating the pool of a data source that is slow to connect must not block the
 * pool lookups of the other data sources.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DefaultConnectionPoolFactoryConcurrencyTest.CredentialServiceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultConnectionPoolFactoryConcurrencyTest {
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private static final Duration NOT_BLOCKED = Duration.ofSeconds(5);

   private final CountDownLatch slowEntered = new CountDownLatch(1);
   private final CountDownLatch slowRelease = new CountDownLatch(1);
   private final Map<String, AtomicInteger> creations = new ConcurrentHashMap<>();
   private final TestFactory factory = new TestFactory();
   private final ExecutorService executor = Executors.newCachedThreadPool();

   @AfterEach
   void tearDown() throws Exception {
      slowRelease.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      factory.closeAllConnectionsPools();
   }

   @Test
   void otherSourcesAreNotBlockedWhileASlowPoolIsCreated() throws Exception {
      JDBCDataSource healthy = source("healthy", "jdbc:fake:healthy");
      DataSource cached = factory.getConnectionPool(healthy, null);
      Future<DataSource> slow = executor.submit(
         () -> factory.getConnectionPool(source("slow", "jdbc:fake:slow"), null));
      assertTrue(slowEntered.await(10, TimeUnit.SECONDS));

      assertTimeoutPreemptively(NOT_BLOCKED, () -> {
         assertSame(cached, factory.getConnectionPool(healthy, null));
         assertNotNull(factory.getConnectionPool(source("other", "jdbc:fake:other"), null));
      }, "a pool lookup waited for the creation of another data source's pool");

      assertFalse(slow.isDone());
      slowRelease.countDown();
      assertNotNull(slow.get(10, TimeUnit.SECONDS));
   }

   @Test
   void concurrentCallersOfOneSourceShareOnePool() throws Exception {
      List<Future<DataSource>> results = new ArrayList<>();

      for(int i = 0; i < 4; i++) {
         results.add(executor.submit(
            () -> factory.getConnectionPool(source("slow", "jdbc:fake:slow"), null)));
      }

      assertTrue(slowEntered.await(10, TimeUnit.SECONDS));
      Thread.sleep(200);
      slowRelease.countDown();
      DataSource pool = results.get(0).get(10, TimeUnit.SECONDS);

      for(Future<DataSource> result : results) {
         assertSame(pool, result.get(10, TimeUnit.SECONDS));
      }

      assertEquals(1, creations("jdbc:fake:slow"));
   }

   @Test
   void closeAllDuringCreationDoesNotInstallTheStalePool() throws Exception {
      closeDuringCreationDoesNotInstallTheStalePool(f -> f.closeAllConnectionsPools());
   }

   @Test
   void closeFilteredDuringCreationDoesNotInstallTheStalePool() throws Exception {
      // a pool still being created can't be tested by the filter, so it is always discarded
      closeDuringCreationDoesNotInstallTheStalePool(f -> f.closeConnectionPools(ds -> false));
   }

   private void closeDuringCreationDoesNotInstallTheStalePool(
      Consumer<DefaultConnectionPoolFactory> close) throws Exception
   {
      JDBCDataSource slowSource = source("slow", "jdbc:fake:slow");
      Future<DataSource> slow = executor.submit(() -> factory.getConnectionPool(slowSource, null));
      assertTrue(slowEntered.await(10, TimeUnit.SECONDS));

      // the close never waits for a pool that is being created
      assertTimeoutPreemptively(NOT_BLOCKED, () -> close.accept(factory));

      slowRelease.countDown();
      HikariDataSource pool = (HikariDataSource) slow.get(10, TimeUnit.SECONDS);

      assertEquals(2, creations("jdbc:fake:slow"), "the pool was not created again after the close");
      // the probe and the pool's own connection of the discarded pool are all closed
      List<Connection> stale = factory.dataSources.get(0).opened;
      assertTrue(stale.size() >= 2, "the discarded pool opened no connection: " + stale.size());

      for(Connection connection : stale) {
         assertTrue(connection.isClosed(), "the discarded pool was not closed");
      }

      assertFalse(pool.isClosed());
      assertSame(pool, factory.getConnectionPool(slowSource, null));
   }

   @Test
   void failedCreationIsRethrownToEveryCallerAndNotCached() throws Exception {
      JDBCDataSource failing = source("failing", "jdbc:fake:slow-fail");
      List<Future<DataSource>> results = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         results.add(executor.submit(() -> factory.getConnectionPool(failing, null)));
      }

      assertTrue(slowEntered.await(10, TimeUnit.SECONDS));
      Thread.sleep(200);
      slowRelease.countDown();

      for(Future<DataSource> result : results) {
         ExecutionException e =
            assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
         assertInstanceOf(HikariPool.PoolInitializationException.class, e.getCause());
      }

      int failed = creations("jdbc:fake:slow-fail");
      assertThrows(HikariPool.PoolInitializationException.class,
                   () -> factory.getConnectionPool(failing, null));
      assertEquals(failed + 1, creations("jdbc:fake:slow-fail"), "the failed pool was cached");
   }

   private int creations(String url) {
      return creations.getOrDefault(url, new AtomicInteger()).get();
   }

   private static JDBCDataSource source(String name, String url) {
      JDBCDataSource source = new JDBCDataSource();
      source.setName("b79485-" + name);
      source.setDriver(SlowDriver.class.getName());
      source.setURL(url);
      source.setRequireLogin(false);
      return source;
   }

   /**
    * Builds the pool config on a fake driver instead of a driver plugin. The probe connect is
    * kept, so the creation connects twice, like the real factory.
    */
   private final class TestFactory extends DefaultConnectionPoolFactory {
      @Override
      HikariConfig createDataSourceConfig(JDBCDataSource jdbcDataSource, boolean forceJdbc4) {
         creations.computeIfAbsent(jdbcDataSource.getURL(), k -> new AtomicInteger())
            .incrementAndGet();
         DriverDataSource dataSource =
            new DriverDataSource(new SlowDriver(), jdbcDataSource.getURL());
         dataSources.add(dataSource);
         HikariConfig config = new HikariConfig();
         config.setDataSource(dataSource);
         config.setMaximumPoolSize(1);

         try(Connection ignored = dataSource.getConnection()) {
            // probe
         }
         catch(SQLException ignore) {
         }

         return config;
      }

      // the data source of every pool config created, in order
      private final List<DriverDataSource> dataSources = new CopyOnWriteArrayList<>();
   }

   /**
    * Fake driver. A "slow" URL blocks until the test releases it, a "fail" URL fails, the
    * others connect to an in-memory Derby database.
    */
   private final class SlowDriver implements Driver {
      @Override
      public Connection connect(String url, Properties info) throws SQLException {
         if(url.contains("slow")) {
            slowEntered.countDown();

            try {
               if(!slowRelease.await(30, TimeUnit.SECONDS)) {
                  throw new SQLException("Timed out waiting to connect");
               }
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new SQLException("Interrupted", e);
            }
         }

         if(url.contains("fail")) {
            throw new SQLException("Connection refused: " + url);
         }

         return DriverManager.getConnection("jdbc:derby:memory:b79485;create=true", info);
      }

      @Override
      public boolean acceptsURL(String url) {
         return url.startsWith("jdbc:fake:");
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

   private static final class DriverDataSource implements DataSource {
      DriverDataSource(Driver driver, String url) {
         this.driver = driver;
         this.url = url;
      }

      @Override
      public Connection getConnection() throws SQLException {
         Connection connection = driver.connect(url, new Properties());
         opened.add(connection);
         return connection;
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
         return getConnection();
      }

      @Override
      public PrintWriter getLogWriter() {
         return null;
      }

      @Override
      public void setLogWriter(PrintWriter out) {
      }

      @Override
      public void setLoginTimeout(int seconds) {
      }

      @Override
      public int getLoginTimeout() {
         return 0;
      }

      @Override
      public Logger getParentLogger() throws SQLFeatureNotSupportedException {
         throw new SQLFeatureNotSupportedException();
      }

      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         throw new SQLException("Does not wrap " + iface);
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) {
         return false;
      }

      private final Driver driver;
      private final String url;
      private final List<Connection> opened = new CopyOnWriteArrayList<>();
   }
}
