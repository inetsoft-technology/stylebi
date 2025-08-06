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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Properties;

class AuthenticationDataSource implements DataSource {
   public AuthenticationDataSource(Driver driver, String url, Properties properties) {
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
