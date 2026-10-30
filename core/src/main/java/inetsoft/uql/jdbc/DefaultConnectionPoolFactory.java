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
import com.zaxxer.hikari.pool.HikariProxyConnection;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.*;
import inetsoft.util.Tool;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * Default connection pool factory implementation. This implementation creates
 * HikariCP data sources.
 *
 * @since 12.2
 */
public class DefaultConnectionPoolFactory implements ConnectionPoolFactory {
   /**
    * Creates a new instance of <tt>DefaultConnectionPoolFactory</tt>.
    */
   public DefaultConnectionPoolFactory() {
      dataSources = new ConcurrentHashMap<>();
   }

   @Override
   public DataSource getConnectionPool(JDBCDataSource jdbcDataSource,
                                       Principal user)
   {
      HikariDataSource result = null;
      final DataSourceKey key = DataSourceKey.Builder.from(jdbcDataSource).build();

      // Bug #79485, the pool is created outside of any map-wide lock (creating it connects to
      // the database), so a data source that is slow to connect only blocks its own callers.
      // A null result means that the pool was closed while it was created, so create it again.
      while(result == null) {
         PoolEntry entry = dataSources.get(key);

         if(entry == null) {
            PoolEntry created = new PoolEntry();
            entry = dataSources.putIfAbsent(key, created);

            if(entry == null) {
               result = createConnectionPool(key, created, jdbcDataSource);
               continue;
            }
         }

         result = entry.await(jdbcDataSource);
      }

      if(jdbcDataSource.getDatabaseType() == JDBCDataSource.JDBC_ACCESS) {
         removeInvalidConnections(result);
      }

      return result;
   }

   /**
    * Creates the pool of an entry that this thread added to the map, and completes the entry.
    *
    * @return the pool, or <tt>null</tt> if the entry was closed while the pool was created.
    */
   private HikariDataSource createConnectionPool(DataSourceKey key, PoolEntry entry,
                                                 JDBCDataSource jdbcDataSource)
   {
      HikariDataSource pool;

      try {
         pool = new HikariDataSource(createDataSourceConfig(jdbcDataSource));
      }
      catch(RuntimeException | Error e) {
         // a failed pool is not cached, the next call tries again
         dataSources.remove(key, entry);
         entry.future.completeExceptionally(e);
         throw e;
      }

      if(entry.install(pool)) {
         entry.future.complete(pool);
         return pool;
      }

      // the pool was created from a definition that was closed in the meantime, discard it
      entry.future.complete(null);
      pool.close();
      return null;
   }

   private HikariConfig createDataSourceConfig(JDBCDataSource jdbcDataSource) {
      return createDataSourceConfig(jdbcDataSource, false);
   }

   HikariConfig createDataSourceConfig(JDBCDataSource jdbcDataSource, boolean forceJdbc4) {
      HikariConfig config = new HikariConfig();
      String url = jdbcDataSource.getURL();
      boolean odbc = "sun.jdbc.odbc.JdbcOdbcDriver".equals(jdbcDataSource.getDriver());
      boolean jdbc4 = forceJdbc4;
      String userName = null;
      String password = null;

      if(odbc) {
         LOG.warn(
            "ODBC data sources are deprecated. This driver has been " +
            "removed in Java 8. You should find a native JDBC driver " +
            "implementation for your database.");
      }

      if(jdbcDataSource.isRequireLogin()) {
         userName = jdbcDataSource.getUser();
         password = jdbcDataSource.getPassword();
      }

      try {
         config.setDataSource(new DefaultDataSource(
            JDBCHandler.getDriver(jdbcDataSource.getDriver()),
            url, userName, password));
         Connection connection = null;

         try {
            connection = config.getDataSource().getConnection();

            try {
               connection.isValid(5);
               jdbc4 = true;
            }
            catch(Throwable ignore) {
            }
         }
         finally {
            Tool.closeQuietly(connection);
         }
      }
      catch(Exception e) {
         LOG.warn(
            "Failed to instantiate JDBC data source {}", jdbcDataSource.getFullName(), e);
         config.setJdbcUrl(url);
         JDBCHandler.setDriverClassName(config, jdbcDataSource.getDriver());
         Tool.addUserMessage(
            "Failed to instantiate JDBC data source " + jdbcDataSource.getFullName() + ".");

         if(jdbcDataSource.isRequireLogin()) {
            config.setUsername(userName);
            config.setPassword(password);
         }
      }

      switch(jdbcDataSource.getTransactionIsolation()) {
      case Connection.TRANSACTION_NONE:
         config.setTransactionIsolation("TRANSACTION_NONE");
         break;

      case Connection.TRANSACTION_READ_COMMITTED:
         config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
         break;

      case Connection.TRANSACTION_READ_UNCOMMITTED:
         config.setTransactionIsolation("TRANSACTION_READ_UNCOMMITTED");
         break;

      case Connection.TRANSACTION_REPEATABLE_READ:
         config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
         break;

      case Connection.TRANSACTION_SERIALIZABLE:
         config.setTransactionIsolation("TRANSACTION_SERIALIZABLE");
         break;
      }

      if(jdbcDataSource.getDefaultDatabase() != null) {
         config.setCatalog(jdbcDataSource.getDefaultDatabase());
      }

      // Make sure that embedded Derby classpath is explicitly set to
      // read-only, otherwise there will be an error when connections are
      // set up.
      if(url != null && (url.startsWith("jdbc:derby:classpath:") ||
         url.startsWith("jdbc:derby:jar:")))
      {
         config.setReadOnly(true);
      }

      String fullName = jdbcDataSource.getFullName();
      Map<String, String> defaultProperties = Config.getConfig().getDefaultPoolProperties(
         Config.getConfig().indexOfJDBCDriver(jdbcDataSource.getDriver()));

      if(!(url != null && url.startsWith("jdbc:databricks:"))) {
         config.setAutoCommit("true".equals(defaultProperties.get("autoCommit")));
      }

      // default properties
      XUtil.applyProperties(config, defaultProperties, "", fullName, jdbc4);
      // global properties
      XUtil.applyProperties(config, SreeEnv.getProperties(), "hikari.", "<All>", jdbc4);
      // per source properties
      XUtil.applyProperties(config, jdbcDataSource.getPoolProperties(), "", fullName, jdbc4);

      if(!jdbc4 && config.getConnectionTestQuery() == null) {
         SQLHelper helper = SQLHelper.getSQLHelper(
            SQLHelper.getProductName(jdbcDataSource, true));
         String connectionTestQuery = helper.getConnectionTestQuery();

         if(connectionTestQuery == null) {
            throw new RuntimeException(
               "You are using a non-JDBC4 driver and have not defined a connection " +
               "test query for the " + fullName + " data source. Set the Test Query " +
               "of the data source or its connectionTestQuery pool property. You will " +
               "not be able to connect to the database until you have done so.");
         }

         config.setConnectionTestQuery(connectionTestQuery);
      }

      return config;
   }

   /**
    * Remove the invalid session for access database.
    * because the ucanaccess will close session when database file is modified.
    * @param databasePool Hikari pool.
    */
   private void removeInvalidConnections(HikariDataSource databasePool) {
      if(databasePool == null) {
         return;
      }

      Connection connection = null;

      try {
         while(connection == null) {
            connection = databasePool.getConnection();

            if(!(connection instanceof HikariProxyConnection)) {
               return;
            }

            Class<?> accessConnectionClass =
               Drivers.getInstance().getDriverClass("net.ucanaccess.jdbc.UcanaccessConnection");

            Object accessConnection = connection.unwrap(accessConnectionClass);

            if(accessConnection == null) {
               return;
            }

            Object jdbcConnectionObj = invokeMethod(accessConnection, "getHSQLDBConnection");
            Class<?> jdbcConnectionClass =
               Drivers.getInstance().getDriverClass("org.hsqldb.jdbc.JDBCConnection");

            if(!jdbcConnectionClass.isInstance(jdbcConnectionObj)) {
               return;
            }

            Object session = invokeMethod(jdbcConnectionObj, "getSession");

            if(session == null) {
               return;
            }

            Object isClosed = invokeMethod(session, "isClosed");

            if(Boolean.parseBoolean(isClosed == null ? "false" : isClosed.toString())) {
               connection.close();
               databasePool.evictConnection(connection);
               connection = null;
            }
         }
      }
      catch(SQLException ignore) {
      }
      catch(Exception exp) {
         LOG.warn("Remove closed access session failed", exp);
      }
      finally {
         if(connection != null) {
            try {
               connection.close();
            }
            catch(SQLException ignore) {
            }
         }
      }
   }

   /**
    * Invoke a method of the obj by reflect.
    * @param obj object will be invoke.
    * @param methodName invoke method name.
    * @param args method arguments.
    * @return method return.
    */
   private Object invokeMethod(Object obj, String methodName, Object... args)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
   {
      if(obj == null) {
         throw new NullPointerException("invoke object is NULL");
      }

      Method method = obj.getClass().getDeclaredMethod(methodName);
      return method.invoke(obj, args);
   }

   @Override
   public void closeConnectionPool(DataSource dataSource) {
      for(Map.Entry<DataSourceKey, PoolEntry> e : dataSources.entrySet()) {
         if(e.getValue().getPool() == dataSource) {
            if(dataSources.remove(e.getKey(), e.getValue())) {
               e.getValue().close();
            }

            break;
         }
      }

      ((HikariDataSource) dataSource).close();
   }

   @Override
   public void closeConnectionPools(Predicate<DataSource> filter) {
      closePools(filter);
   }

   @Override
   public void closeAllConnectionsPools() {
      closePools(ds -> true);
   }

   /**
    * Removes the pools that match the filter from the map and closes them outside of any lock.
    */
   private void closePools(Predicate<DataSource> filter) {
      Set<DataSource> closed = new HashSet<>();

      for(Map.Entry<DataSourceKey, PoolEntry> e : dataSources.entrySet()) {
         PoolEntry entry = e.getValue();
         HikariDataSource pool = entry.getPool();

         // a pool that is still being created can't be tested, it is discarded when it is done.
         // Never wait for it here, the caller may hold a lock that the creation needs.
         if((pool == null || filter.test(pool)) && dataSources.remove(e.getKey(), entry)) {
            pool = entry.close();

            if(pool != null) {
               closed.add(pool);
            }
         }
      }

      for(DataSource ds : closed) {
         ((HikariDataSource) ds).close();
      }
   }

   @Override
   public void close() throws Exception {
      closeAllConnectionsPools();
   }

   @Override
   public boolean isDriverUsed(DataSource dataSource, String driverClassName) {
      if(dataSource instanceof HikariDataSource ds) {
         if(Objects.equals(driverClassName, ds.getDriverClassName())) {
            return true;
         }

         if(ds.getDataSource() instanceof DefaultDataSource) {
            return ((DefaultDataSource) ds.getDataSource()).driver
               .getClass().getName().equals(driverClassName);
         }
      }

      return false;
   }

   private final ConcurrentMap<DataSourceKey, PoolEntry> dataSources;
   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultConnectionPoolFactory.class);

   /**
    * The pool of a data source. The thread that adds the entry to the map creates the pool,
    * the other callers of the same data source wait for it.
    */
   private static final class PoolEntry {
      /**
       * Sets the created pool, unless the entry was closed.
       *
       * @return <tt>true</tt> if the pool was set.
       */
      synchronized boolean install(HikariDataSource pool) {
         if(closed) {
            return false;
         }

         this.pool = pool;
         return true;
      }

      /**
       * Gets the pool, or <tt>null</tt> if it has not been created.
       */
      synchronized HikariDataSource getPool() {
         return pool;
      }

      /**
       * Marks the entry closed, so that a pool still being created is discarded.
       *
       * @return the pool to close, or <tt>null</tt> if it has not been created.
       */
      synchronized HikariDataSource close() {
         closed = true;
         return pool;
      }

      /**
       * Waits for the pool created by another thread.
       *
       * @return the pool, or <tt>null</tt> if the entry was closed while the pool was created.
       */
      HikariDataSource await(JDBCDataSource jdbcDataSource) {
         try {
            return future.join();
         }
         catch(CompletionException e) {
            // the user message of the failure is thread local, add it for this caller too
            Tool.addUserMessage(
               "Failed to instantiate JDBC data source " + jdbcDataSource.getFullName() + ".");

            if(e.getCause() instanceof RuntimeException cause) {
               throw cause;
            }

            if(e.getCause() instanceof Error cause) {
               throw cause;
            }

            throw e;
         }
      }

      private final CompletableFuture<HikariDataSource> future = new CompletableFuture<>();
      private HikariDataSource pool;
      private boolean closed;
   }

   /**
    * Data source implementation that gets the JDBC driver from JDBC handler.
    */
   private static final class DefaultDataSource implements DataSource {
      /**
       * Creates a new instance of <tt>DefaultDataSource</tt>.
       *
       * @param driver   the JDBC driver for the database.
       * @param url      the JDBC URL for the database.
       * @param userName the database user name.
       * @param password the database password.
       */
      public DefaultDataSource(Driver driver, String url, String userName,
                               String password)
      {
         this.driver = driver;
         this.url = url;
         properties = new Properties();

         if(userName != null) {
            properties.setProperty("user", userName);
         }

         if(password != null) {
            properties.setProperty("password", password);
         }

         if(url != null && url.startsWith("jdbc:clickhouse:")) {
            properties.setProperty("result_overflow_mode", "break");
         }
      }

      @Override
      public Connection getConnection() throws SQLException {
         return driver.connect(url, properties);
      }

      @Override
      public Connection getConnection(String username, String password)
         throws SQLException
      {
         Properties userProperties = new Properties(properties);
         userProperties.setProperty("user", username);
         userProperties.setProperty("password", password);
         return driver.connect(url, properties);
      }

      @Override
      public PrintWriter getLogWriter() throws SQLException {
         return logWriter;
      }

      @Override
      public void setLogWriter(PrintWriter out) throws SQLException {
         this.logWriter = out;
      }

      @Override
      public void setLoginTimeout(int seconds) throws SQLException {
         this.loginTimeout = seconds;
      }

      @Override
      public int getLoginTimeout() throws SQLException {
         return loginTimeout;
      }

      @SuppressWarnings("unchecked")
      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         if(isWrapperFor(iface)) {
            return (T) this;
         }

         throw new SQLException("Does not wrap " + iface);
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) throws SQLException {
         return (iface != null && iface.isAssignableFrom(this.getClass()));
      }

      @Override
      public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
         throw new SQLFeatureNotSupportedException();
      }

      private final Driver driver;
      private final String url;
      private final Properties properties;
      private PrintWriter logWriter = null;
      private int loginTimeout = 0;
   }

   @Value.Immutable
   interface DataSourceKey {
      @Nullable
      String url();
      boolean isRequireLogin();
      @Nullable
      String userName();
      @Nullable
      String password();
      @Nullable
      String driver();
      int transactionIsolation();
      @Nullable
      String defaultDatabase();
      String fullName();

      final class Builder extends ImmutableDataSourceKey.Builder {
         static Builder from(JDBCDataSource ds) {
            final Builder builder = new Builder();

            if(ds.isRequireLogin()) {
               builder
                  .userName(ds.getUser())
                  .password(ds.getPassword());
            }

            return builder
               .url(ds.getURL())
               .isRequireLogin(ds.isRequireLogin())
               .driver(ds.getDriver())
               .transactionIsolation(ds.getTransactionIsolation())
               .defaultDatabase(ds.getDefaultDatabase())
               .fullName(ds.getFullName());
         }
      }
   }
}
