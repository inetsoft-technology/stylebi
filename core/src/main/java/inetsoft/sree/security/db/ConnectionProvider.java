/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

class ConnectionProvider implements AutoCloseable {
   public ConnectionProvider(DatabaseAuthenticationProvider provider) {
      this.provider = provider;
      this.connectionRetryInterval = Long.parseLong(SreeEnv.getProperty(
         "database.security.connection.retryInterval", "600000"));
      this.retryInterval = connectionRetryInterval;
   }

   public Connection getConnection() throws Exception {
      Connection connection = null;
      lock.lock();

      try {
         ConnectionProperties connectionProperties = provider.getConnectionProperties();

         if(connectionValid == null || shouldRetryConnection()) {
            try {
               testConnectionBounded(getProbeTimeout());
               connectionValid = true;
               lastGoodProperties = connectionProperties;
            }
            catch(Exception e) {
               connectionValid = false;
               // A configuration that has connected before is a database outage, not a bad
               // configuration. Re-test it after the short window, so that recovery is noticed
               // quickly, also after the cache loader reset the connection (Bug #78800).
               retryInterval = Objects.equals(connectionProperties, lastGoodProperties) ?
                  getPoolFailureRetryInterval() : connectionRetryInterval;
               throw e;
            }
            finally {
               connectionLastTested = Instant.now();
            }
         }

         if(connectionValid) {
            if(connectionPool == null) {
               connectionPool = new HikariDataSource();

               Properties properties = new Properties();

               if(connectionProperties.requiresLogin()) {
                  properties.setProperty("user", connectionProperties.username());
                  properties.setProperty("password", connectionProperties.password());
               }

               try {
                  Driver driver = provider.getDriver(connectionProperties.driverClass());
                  connectionPool.setDataSource(new AuthenticationDataSource(
                     driver, connectionProperties.url(), properties));
               }
               catch(Exception e) {
                  LOG.warn("Failed to create default data source", e);
                  connectionPool.setDriverClassName(connectionProperties.driverClass());
                  connectionPool.setJdbcUrl(connectionProperties.url());

                  if(connectionProperties.requiresLogin()) {
                     connectionPool.setUsername(connectionProperties.username());
                     connectionPool.setPassword(connectionProperties.password());
                  }
               }
            }

            try {
               connection = connectionPool.getConnection();
            }
            catch(SQLException e) {
               handlePoolFailure(e);
               throw e;
            }
         }
      }
      finally {
         lock.unlock();
      }

      if(connection == null) {
         connectionValid = false;
         throw new SQLException("Failed to connect");
      }

      return connection;
   }

   /**
    * Called when the pool could not supply a connection (Hikari throws after its connection
    * timeout, 30 s by default). That happens both when the database is unreachable and when the
    * pool is exhausted by a healthy database, so probe the database directly and only remember
    * the failure when the probe fails too. While the failure is remembered, calls fail fast
    * instead of each waiting for the pool timeout again (Bug #78800).
    */
   private void handlePoolFailure(SQLException poolException) {
      try {
         testConnectionBounded(getProbeTimeout());
         LOG.debug(
            "Failed to get a connection from the pool for security provider \"{}\", " +
            "but the database is reachable", provider.getProviderName(), poolException);
      }
      catch(Exception e) {
         long interval = getPoolFailureRetryInterval();
         connectionValid = false;
         connectionLastTested = Instant.now();
         retryInterval = interval;
         LOG.warn(
            "Database for security provider \"{}\" is unreachable, failing fast for {} ms: {}",
            provider.getProviderName(), interval, e.getMessage());
      }
   }

   /**
    * Tests the connection like {@link #testConnection()}, but gives up after {@code timeout} ms.
    * The driver's {@code connect} runs on a daemon thread, because it can block until the OS
    * gives up on an unreachable host, and this is called while holding {@link #lock}. A timed out
    * probe thread is interrupted, but it may linger until the driver returns.
    */
   private void testConnectionBounded(long timeout) throws Exception {
      ConnectionProperties props = provider.getConnectionProperties();
      checkConnectionProperties(props);
      Driver driver = provider.getDriver(props.driverClass());
      Properties properties = getDriverProperties(props);
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      Future<?> future = PROBE_EXECUTOR.submit(() -> {
         Thread thread = Thread.currentThread();
         thread.setContextClassLoader(loader);

         try {
            connect(driver, props.url(), properties);
         }
         finally {
            thread.setContextClassLoader(null);
         }

         return null;
      });

      try {
         future.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException e) {
         future.cancel(true);
         throw new SQLException("Connection test timed out after " + timeout + " ms", "08001");
      }
      catch(InterruptedException e) {
         future.cancel(true);
         Thread.currentThread().interrupt();
         throw e;
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof Exception cause) {
            throw cause;
         }

         throw e;
      }
   }

   private long getProbeTimeout() {
      long timeout;

      try {
         // the pool's connection timeout, or the configured/default one before there is a pool
         timeout = connectionPool != null ?
            connectionPool.getConnectionTimeout() : new HikariConfig().getConnectionTimeout();
      }
      catch(RuntimeException e) {
         timeout = MAX_PROBE_TIMEOUT;
      }

      return timeout > 0 ? Math.min(timeout, MAX_PROBE_TIMEOUT) : MAX_PROBE_TIMEOUT;
   }

   private static long getPoolFailureRetryInterval() {
      try {
         return Long.parseLong(SreeEnv.getProperty(
            "database.security.connection.poolFailureRetryInterval", "30000"));
      }
      catch(NumberFormatException e) {
         return 30000L;
      }
   }

   public void testConnection() throws Exception {
      ConnectionProperties props = provider.getConnectionProperties();
      checkConnectionProperties(props);
      Driver driver = provider.getDriver(props.driverClass());
      connect(driver, props.url(), getDriverProperties(props));
   }

   private static Properties getDriverProperties(ConnectionProperties props) {
      Properties properties = new Properties();

      if(props.requiresLogin()) {
         properties.setProperty("user", props.username());
         properties.setProperty("password", props.password());
      }

      return properties;
   }

   private static void connect(Driver driver, String url, Properties properties) throws Exception {
      try(Connection connection = driver.connect(url, properties)) {
         if(connection == null) {
            throw new MessageException(Catalog.getCatalog().getString("Connection failed"));
         }
      }
   }

   public void resetConnection() {
      lock.lock();

      try {
         if(connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
         }

         connectionValid = null;
         connectionLastTested = Instant.MAX;
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public void close() {
      lock.lock();

      try {
         if(connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
         }
      }
      finally {
         lock.unlock();
      }
   }

   private boolean shouldRetryConnection() {
      return Boolean.FALSE.equals(connectionValid) &&
         connectionLastTested.isBefore(Instant.now().minusMillis(retryInterval));
   }

   private void checkConnectionProperties(ConnectionProperties properties) throws SQLException {
      if(StringUtils.isBlank(properties.driverClass())) {
         throw new SQLException("Failed to make a connection, the driver class is not defined.");
      }

      if(StringUtils.isBlank(properties.url())) {
         throw new SQLException("Failed to make a connection, the JDBC URL is not defined.");
      }

      if(properties.requiresLogin() && StringUtils.isBlank(properties.username())) {
         throw new SQLException("Failed to make a connection, user name is not defined.");
      }

      if(!provider.isDriverAvailable(properties.driverClass())) {
         throw new SQLException("Failed to make a connection, cannot find the driver class");
      }
   }

   private final DatabaseAuthenticationProvider provider;
   private final Lock lock = new ReentrantLock();
   private final long connectionRetryInterval;
   private Boolean connectionValid;
   private Instant connectionLastTested = Instant.MAX;
   // How long a failed connection is remembered: connectionRetryInterval for a configuration
   // that never connected, the shorter pool failure interval for an outage of a working database.
   private long retryInterval;
   // the connection properties of the last successful connection test
   private ConnectionProperties lastGoodProperties;
   private HikariDataSource connectionPool;

   private static final long MAX_PROBE_TIMEOUT = 10000L;
   private static final ExecutorService PROBE_EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "DatabaseSecurityConnectionProbe");
      thread.setDaemon(true);
      return thread;
   });

   private static final Logger LOG = LoggerFactory.getLogger(ConnectionProvider.class);
}
