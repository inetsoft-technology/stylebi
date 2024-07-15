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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.util.Drivers;

import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.Properties;

/**
 * Wrapper for an Apache Hive JDBC driver. The Hive drivers are incomplete
 * and break the JDBC specification. This class ensures a basic level of JDBC
 * compliance.
 *
 * @since 12.2
 */
public class HiveWrapperDriver implements Driver {
   @Override
   public Connection connect(String url, Properties info) throws SQLException {
      Connection connection = null;

      if(acceptsURL(url)) {
         connection = getDelegate(url).connect(url, info);
         connection = new HiveWrapperConnection(connection);
      }

      return connection;
   }

   @Override
   public boolean acceptsURL(String url) throws SQLException {
      return url != null &&
         (url.startsWith("jdbc:hive:") || url.startsWith("jdbc:hive2:"));
   }

   @Override
   public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
      throws SQLException
   {
      return getDelegate(url).getPropertyInfo(url, info);
   }

   @Override
   public int getMajorVersion() {
      return 12;
   }

   @Override
   public int getMinorVersion() {
      return 2;
   }

   @Override
   public boolean jdbcCompliant() {
      return true;
   }

   @Override
   public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
      return null;
   }

   private synchronized Driver getDelegate(String url) throws SQLException {
      Driver driver = null;

      if(url != null && url.startsWith("jdbc:hive:")) {
         if(hive1Driver == null) {
            hive1Driver = loadDriver("org.apache.hadoop.hive.jdbc.HiveDriver");
         }

         driver = hive1Driver;
      }
      else if(url != null && url.startsWith("jdbc:hive2:")) {
         if(hive2Driver == null) {
            hive2Driver = loadDriver("org.apache.hive.jdbc.HiveDriver");
         }

         driver = hive2Driver;
      }

      return driver;
   }

   private Driver loadDriver(String className) throws SQLException {
      try {
         return (Driver) Drivers.getInstance().getDriverClass(className)
            .getConstructor().newInstance();
      }
      catch(InstantiationException | IllegalAccessException |
         NoSuchMethodException | ClassNotFoundException e)
      {
         throw new SQLException(
            "Failed to instantiate delegate driver", e);
      }
      catch(InvocationTargetException e) {
         Throwable thrown = e.getTargetException();

         if(thrown instanceof SQLException) {
            throw (SQLException) thrown;
         }
         else {
            throw new SQLException(
               "Failed to instantiate delegate driver", thrown);
         }
      }
   }

   private Driver hive1Driver;
   private Driver hive2Driver;
}
