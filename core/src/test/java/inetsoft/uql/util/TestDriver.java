/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class TestDriver implements Driver {
   @Override
   public Connection connect(String url, Properties info) throws SQLException {
      return null;
   }

   @Override
   public boolean acceptsURL(String url) throws SQLException {
      return false;
   }

   @Override
   public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
      return new DriverPropertyInfo[0];
   }

   @Override
   public int getMajorVersion() {
      return 0;
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
      return null;
   }
}
