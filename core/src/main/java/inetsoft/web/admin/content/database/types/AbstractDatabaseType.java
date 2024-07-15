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
package inetsoft.web.admin.content.database.types;

import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.web.admin.content.database.*;

/**
 * Base implementation for database type classes that are associated with a
 * single JDBC driver.
 *
 * @param <T> the <tt>DatabaseInfo</tt> implementation associated with the
 *            type implementation.
 */
public abstract class AbstractDatabaseType<T extends DatabaseInfo> extends DatabaseType<T>
{
   /**
    * Creates a new instance of <tt>AbstractDatabaseType</tt>.
    *
    * @param type        the identifier for the database type.
    * @param driverClass the fully-qualified class name of the JBDC driver.
    */
   public AbstractDatabaseType(String type, String driverClass) {
      super(type);
      this.driverClass = driverClass;
   }

   @Override
   public final NetworkLocation parse(String driverClass, String url, T info) {
      return parseUrl(url, info);
   }

   /**
    * Parses a JDBC URL.
    *
    * @param url  the URL to parse.
    * @param info the object in which the database properties will be set.
    *
    * @return the network location, if available.
    */
   protected abstract NetworkLocation parseUrl(String url, T info);

   @Override
   public final String getDriverClass(T info) {
      return driverClass;
   }

   @Override
   public final boolean supportsDriverClass(String driverClass) {
      return this.driverClass.equals(driverClass);
   }

   @Override
   public boolean isDriverInstalled() {
      return JDBCHandler.isDriverAvailable(driverClass);
   }

   public static String joinPropertiesChar(String type) {
      switch(type) {
         case SQLServerDatabaseType.TYPE:
         case AccessDatabaseType.TYPE:
            return ";";
         case InformixDatabaseType.TYPE:
            return ":";
         default:
            return "?";
      }
   }

   private final String driverClass;
}
