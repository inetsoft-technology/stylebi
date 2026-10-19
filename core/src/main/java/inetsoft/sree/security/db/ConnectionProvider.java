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
import java.util.concurrent.atomic.AtomicBoolean;
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
               connectionLastTested = Instant.now();
            }
            catch(Exception e) {
               if(isInterruption(e)) {
                  // the caller was interrupted, that says nothing about the database: keep the
                  // state as it was, so the next call tests again
                  throw e;
               }

               connectionValid = false;
               // A configuration that has connected before is a database outage, not a bad
               // configuration. Re-test it after the short window, so that recovery is noticed
               // quickly, also after the cache loader reset the connection (Bug #78800).
               retryInterval = Objects.equals(connectionProperties, lastGoodProperties) ?
                  getPoolFailureRetryInterval() : connectionRetryInterval;
               connectionLastTested = Instant.now();
               throw e;
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
      if(isInterruption(poolException)) {
         // the caller was interrupted while waiting for the pool, not a database failure
         return;
      }

      try {
         testConnectionBounded(getProbeTimeout());
         LOG.debug(
            "Failed to get a connection from the pool for security provider \"{}\", " +
            "but the database is reachable", provider.getProviderName(), poolException);
      }
      catch(Exception e) {
         if(isInterruption(e)) {
            return;
         }

         long interval = getPoolFailureRetryInterval();
         connectionValid = false;
         connectionLastTested = Instant.now();
         retryInterval = interval;
         LOG.warn(
            "Database for security provider \"{}\" is unreachable, failing fast for {} ms: {}",
            provider.getProviderName(), interval, String.valueOf(e));
      }
   }

   /**
    * Checks if a failure is caused by interrupting the calling thread. Hikari wraps an
    * interrupt during connection acquisition in an {@code SQLException} and keeps the thread's
    * interrupt flag set.
    */
   private static boolean isInterruption(Throwable e) {
      if(Thread.currentThread().isInterrupted()) {
         return true;
      }

      for(Throwable t = e; t != null; t = t.getCause()) {
         if(t instanceof InterruptedException) {
            return true;
         }

         if(t.getCause() == t) {
            break;
         }
      }

      return false;
   }

   /**
    * Tests the connection like {@link #testConnection()}, but gives up after {@code timeout} ms.
    * The driver's {@code connect} runs on a daemon thread, because it can block until the OS
    * gives up on an unreachable host, and this is called while holding {@link #lock}. A timed out
    * probe thread is interrupted, but it may linger until the driver returns. While it does,
    * another test fails at once instead of starting a second probe, so a hanging database costs
    * at most one probe thread per provider (one per {@link #MAX_PROBE_AGE} if it never returns)
    * and doesn't stall the callers again on every re-test.
    */
   private void testConnectionBounded(long timeout) throws Exception {
      ConnectionProperties props = provider.getConnectionProperties();
      checkConnectionProperties(props);

      if(pendingProbe != null && !pendingProbe.isDone() &&
         System.currentTimeMillis() - pendingProbeStarted < MAX_PROBE_AGE)
      {
         throw new SQLException("The previous connection test has not finished", "08001");
      }

      pendingProbe = null;
      Driver driver = provider.getDriver(props.driverClass());
      Properties properties = getDriverProperties(props);
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      CompletableFuture<Void> probeDone = new CompletableFuture<>();
      AtomicBoolean running = new AtomicBoolean();
      long started = System.currentTimeMillis();

      Future<?> future = PROBE_EXECUTOR.submit(() -> {
         running.set(true);
         Thread thread = Thread.currentThread();
         thread.setContextClassLoader(loader);

         try {
            connect(driver, props.url(), properties);
         }
         finally {
            thread.setContextClassLoader(null);
            probeDone.complete(null);
         }

         return null;
      });

      try {
         future.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException e) {
         abandonProbe(future, running, probeDone, started);
         throw new SQLException("Connection test timed out after " + timeout + " ms", "08001");
      }
      catch(InterruptedException e) {
         // not a sign of a hanging database, so this probe doesn't block the next one
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

   private void abandonProbe(Future<?> future, AtomicBoolean running,
                             CompletableFuture<Void> probeDone, long started)
   {
      future.cancel(true);

      if(running.get()) {
         // cancel() marks the future done at once, so track the probe's own completion
         pendingProbe = probeDone;
         pendingProbeStarted = started;
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

      // not below MIN_PROBE_TIMEOUT, so a slow (TLS) connect isn't taken for an outage
      return timeout > 0 ?
         Math.max(MIN_PROBE_TIMEOUT, Math.min(timeout, MAX_PROBE_TIMEOUT)) : MAX_PROBE_TIMEOUT;
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

   // a probe that timed out and whose thread hasn't returned yet, and when it started
   private Future<?> pendingProbe;
   private long pendingProbeStarted;

   private static final long MIN_PROBE_TIMEOUT = 2000L;
   private static final long MAX_PROBE_TIMEOUT = 10000L;
   // a timed out probe still running after this long is abandoned and no longer blocks a new one
   private static final long MAX_PROBE_AGE = 300000L;
   private static final ExecutorService PROBE_EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "DatabaseSecurityConnectionProbe");
      thread.setDaemon(true);
      return thread;
   });

   private static final Logger LOG = LoggerFactory.getLogger(ConnectionProvider.class);
}
