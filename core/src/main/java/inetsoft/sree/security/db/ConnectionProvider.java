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

import com.zaxxer.hikari.HikariDataSource;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

class ConnectionProvider implements AutoCloseable {
   public ConnectionProvider(Supplier<ConnectionProperties> propertiesSupplier) {
      this.propertiesSupplier = propertiesSupplier;
      this.connectionRetryInterval = Long.parseLong(SreeEnv.getProperty(
         "database.security.connection.retryInterval", "600000"));
   }

   public Connection getConnection() throws Exception {
      Connection connection = null;
      lock.lock();

      try {
         ConnectionProperties connectionProperties = propertiesSupplier.get();

         if(connectionValid == null || shouldRetryConnection()) {
            try {
               testConnection();
               connectionValid = true;
            }
            catch(Exception e) {
               connectionValid = false;
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
               properties.setProperty("user", connectionProperties.username());
               properties.setProperty("password", connectionProperties.password());

               try {
                  Driver driver = JDBCHandler.getDriver(connectionProperties.driverClass());
                  connectionPool.setDataSource(new AuthenticationDataSource(
                     driver, connectionProperties.url(), properties));
               }
               catch(Exception e) {
                  LOG.warn("Failed to create default data source", e);
                  connectionPool.setDriverClassName(connectionProperties.driverClass());
                  connectionPool.setJdbcUrl(connectionProperties.url());
                  connectionPool.setUsername(connectionProperties.username());
                  connectionPool.setPassword(connectionProperties.password());
               }
            }

            connection = connectionPool.getConnection();
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

   public void testConnection() throws Exception {
      ConnectionProperties props = propertiesSupplier.get();
      checkConnectionProperties(props);
      Driver driver = JDBCHandler.getDriver(props.driverClass());
      Properties properties = new Properties();
      properties.setProperty("user", props.username());
      properties.setProperty("password", props.password());

      try(Connection connection = driver.connect(props.url(), properties)) {
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
         connectionLastTested.isBefore(Instant.now().minusMillis(connectionRetryInterval));
   }

   private void checkConnectionProperties(ConnectionProperties properties) throws SQLException {
      if(properties.driverClass().isEmpty()) {
         throw new SQLException("Failed to make a connection, the driver class is not defined.");
      }

      if(properties.url().isEmpty()) {
         throw new SQLException("Failed to make a connection, the JDBC URL is not defined.");
      }

      if(properties.username().isEmpty() && properties.requiresLogin()) {
         throw new SQLException("Failed to make a connection, user name is not defined.");
      }

      if(!JDBCHandler.isDriverAvailable(Tool.convertUserClassName(properties.driverClass()))) {
         throw new SQLException("Failed to make a connection, cannot find the driver class");
      }
   }

   private final Supplier<ConnectionProperties> propertiesSupplier;
   private final Lock lock = new ReentrantLock();

   private final long connectionRetryInterval;
   private Boolean connectionValid;
   private Instant connectionLastTested = Instant.MAX;
   private HikariDataSource connectionPool;

   private static final Logger LOG = LoggerFactory.getLogger(ConnectionProvider.class);
}
