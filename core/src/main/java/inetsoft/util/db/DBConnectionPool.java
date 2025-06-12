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
package inetsoft.util.db;

import com.zaxxer.hikari.HikariDataSource;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.util.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.PrintWriter;
import java.net.*;
import java.sql.*;
import java.util.*;

/**
 * DBConnectionPool, defines the common APIs of a database connection pool.
 * Here we provide a default implementation, and other implementations such as
 * JNDI implementations could be provided as well. The database connection pool
 * is not a readonly connection pool used in JDBCQuery, but an I/O connection
 * pool commonly used in our product. Hence transaction is supported naturally,
 * and any connection is always used exclusively.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class DBConnectionPool {
   private DBConnectionPool() {
   }

   public static DataSource createStandalonePool(DatabaseConfig config) {
      HikariDataSource ds = new HikariDataSource();

      DatabaseConfig.DatabaseType db = config.getType();
      String driverClass = config.getDriverClassName();
      String url = config.getJdbcUrl();
      String defaultdb = config.getDefaultDatabase();

      if(url == null || url.isEmpty()) {
         String msg = "The url of database DataSpace is empty. " +
            "Please config the property properly.";
         LOG.error(msg, new Exception(msg));
      }
      else if(url.contains("$(sree.home)")) {
         url = url.replace(
            "$(sree.home)",
            ConfigurationContext.getContext().getHome().replace('\\', '/'));
      }

      if(db == DatabaseConfig.DatabaseType.SQL_SERVER) {
         url = url + ";SelectMethod=cursor";
      }
      else if(db == DatabaseConfig.DatabaseType.H2 && url != null &&
         url.contains(";AUTO_SERVER=TRUE") &&
         System.getProperty("h2.bindAddress") == null)
      {
         System.setProperty("h2.bindAddress", Tool.getIP());
      }

      Properties properties = new Properties();
      String user = config.getUsername();
      String password = config.getPassword();

      if(user != null && !user.isEmpty()) {
         properties.setProperty("user", user);
      }

      if(password != null && !password.isEmpty()) {
         properties.setProperty("password", password);
      }

      configureDriver(
         ds, config, driverClass, url, user, password, properties);

      if(config.getTransactionIsolationLevel() != null) {
         ds.setTransactionIsolation(config.getTransactionIsolationLevel().levelName());
      }

      if(!(url != null && url.startsWith("jdbc:databricks:"))) {
         ds.setAutoCommit(false);
      }

      String dbStr = Tool.convertUserParameter(defaultdb);

      // postgres driver starts a new transaction in Connection.isValid(), causing a
      // 'Cannot change transaction isolation level in the middle of a transaction' error.
      // https://github.com/brettwooldridge/HikariCP/issues/102
      if(url != null && url.startsWith("jdbc:postgresql")) {
         ds.setConnectionTestQuery("SELECT 1");
         ds.setIsolateInternalQueries(true);
      }

      // fix bug1334747754371, same as JDBCHandler
      if(dbStr != null && !dbStr.isEmpty()) {
         ds.setCatalog(dbStr);
      }

      XUtil.applyProperties(ds, config.getPool(), "", "CONFIG");
      return ds;
   }

   private static void configureDriver(HikariDataSource ds, DatabaseConfig config,
                                       String driverClass, String url, String user, String password,
                                       Properties properties)
   {
      try {
         Class<?> clazz = getDriverClassLoader(config).loadClass(driverClass);
         Driver driver = (Driver) clazz.getConstructor().newInstance();

         if(!config.isRequiresLogin()) {
            ds.setDataSource(new DefaultDataSource(driver, url, new Properties()));
         }
         else {
            ds.setDataSource(new DefaultDataSource(driver, url, properties));
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to create default data source", e);
         setDriverClassName(ds, config, driverClass);
         ds.setJdbcUrl(url);
         ds.setUsername(user);
         ds.setPassword(password);
      }
   }

   /**
    * Resets all connection pools.
    */
   public static synchronized void reset() {
      DataSource pool = ConfigurationContext.getContext().remove(POOL_KEY);

      if(pool instanceof HikariDataSource) {
         ((HikariDataSource) pool).close();
      }
   }

   public static synchronized boolean isClosed(DataSource pool) {
      return (pool instanceof HikariDataSource) && ((HikariDataSource) pool).isClosed();
   }

   private static void setDriverClassName(HikariDataSource ds, DatabaseConfig config,
                                          String driverClass)
   {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(getDriverClassLoader(config));

      try {
         ds.setDriverClassName(driverClass);
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   private static ClassLoader getDriverClassLoader(DatabaseConfig config) {
      String[] classpath = config.getDriverClasspath();
      ClassLoader baseLoader = Thread.currentThread().getContextClassLoader();

      if(baseLoader == null) {
         baseLoader = DBConnectionPool.class.getClassLoader();
      }

      if(classpath != null && classpath.length > 0) {
         List<URL> urls = new ArrayList<>();

         for(String entry : classpath) {
            try {
               if(entry.endsWith("/*")) {
                  File[] files = new File(entry.substring(0, entry.length() - 2)).listFiles();

                  if(files != null) {
                     for(File file : files) {
                        if(file.getName().toLowerCase().endsWith(".jar")) {
                           urls.add(file.toURI().toURL());
                        }
                     }
                  }
               }
               else {
                  urls.add(new File(entry).toURI().toURL());
               }
            }
            catch(MalformedURLException e) {
               throw new RuntimeException("Failed to add classpath entry: " + entry, e);
            }
         }

         return new URLClassLoader(urls.toArray(new URL[0]), baseLoader);
      }

      return baseLoader;
   }

   private static final String POOL_KEY = DBConnectionPool.class.getName() + ".pool";
   private static final Logger LOG = LoggerFactory.getLogger(DBConnectionPool.class);

   /**
    * Default implementation of <tt>javax.sql.DataSource</tt>.
    */
   private static final class DefaultDataSource implements DataSource {
      /**
       * Creates a new instance of <tt>DefaultDataSource</tt>.
       *
       * @param driver     the JDBC driver.
       * @param url        the JDBC URL for the database.
       * @param properties the connection properties.
       */
      DefaultDataSource(Driver driver, String url, Properties properties)
      {
         this.driver = driver;
         this.url = url;
         this.properties = properties;
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
         return driver.connect(url, userProperties);
      }

      @Override
      public PrintWriter getLogWriter() {
         return logWriter;
      }

      @Override
      public void setLogWriter(PrintWriter out) {
         this.logWriter = out;
      }

      @Override
      public void setLoginTimeout(int seconds) {
         loginTimeout = seconds;
      }

      @Override
      public int getLoginTimeout() {
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
      public boolean isWrapperFor(Class<?> iface) {
         return (iface != null && iface.isAssignableFrom(this.getClass()));
      }

      @Override
      public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
         return driver.getParentLogger();
      }

      private final Driver driver;
      private final String url;
      private final Properties properties;

      private PrintWriter logWriter = null;
      private int loginTimeout = 0;
   }

}
