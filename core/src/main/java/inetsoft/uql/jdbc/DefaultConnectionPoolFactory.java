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
import java.util.function.Predicate;

/**
 * Default connection pool factory implementation. This implementation creates
 * HikariCP data sources.
 *
 * @since 12.2
 */
class DefaultConnectionPoolFactory implements ConnectionPoolFactory {
   /**
    * Creates a new instance of <tt>DefaultConnectionPoolFactory</tt>.
    */
   public DefaultConnectionPoolFactory() {
      dataSources = new HashMap<>();
   }

   @Override
   public DataSource getConnectionPool(JDBCDataSource jdbcDataSource,
                                       Principal user)
   {
      DataSource result;
      final DataSourceKey key = DataSourceKey.Builder.from(jdbcDataSource).build();

      synchronized(dataSources) {
         result = dataSources.computeIfAbsent(
            key, k -> new HikariDataSource(createDataSourceConfig(jdbcDataSource)));
      }

      if(jdbcDataSource.getDatabaseType() == JDBCDataSource.JDBC_ACCESS &&
         result instanceof HikariDataSource)
      {
         removeInvalidConnections((HikariDataSource) result);
      }

      return result;
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
      Map<String, String> defaultProperties = Config.getDefaultPoolProperties(
         Config.indexOfJDBCDriver(jdbcDataSource.getDriver()));

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
               "You are using a non-JDBC4 driver and have not defined the " +
               "inetsoft.uql.jdbc.pool." + fullName +
               ".connectionTestQuery property. You will not be able to " +
               "connect to the database until you have done so.");
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
      synchronized(dataSources) {
         for(Iterator<DataSource> i = dataSources.values().iterator(); i.hasNext();) {
            if(i.next() == dataSource) {
               i.remove();
               break;
            }
         }
      }

      ((HikariDataSource) dataSource).close();
   }

   @Override
   public void closeConnectionPools(Predicate<DataSource> filter) {
      Set<DataSource> closed = new HashSet<>();

      synchronized(dataSources) {
         for(Iterator<DataSource> i = dataSources.values().iterator(); i.hasNext();) {
            DataSource ds = i.next();

            if(filter.test(ds)) {
               i.remove();
               closed.add(ds);
            }
         }
      }

      for(DataSource ds : closed) {
         ((HikariDataSource) ds).close();
      }
   }

   @Override
   public void closeAllConnectionsPools() {
      synchronized(dataSources) {
         for(Iterator<DataSource> i = dataSources.values().iterator();
             i.hasNext();)
         {
            ((HikariDataSource) i.next()).close();
            i.remove();
         }
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

   private final Map<DataSourceKey, DataSource> dataSources;
   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultConnectionPoolFactory.class);

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
