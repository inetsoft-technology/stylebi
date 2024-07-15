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
package inetsoft.web.admin.content.database;

/**
 * Base class for classes that define a type of database.
 *
 * @param <T> the <tt>DatabaseInfo</tt> implementation for this type.
 */
public abstract class DatabaseType<T extends DatabaseInfo> {
   /**
    * Creates a new instance of <tt>DatabaseType</tt>.
    *
    * @param type the identifier for the database type.
    */
   public DatabaseType(String type) {
      this.type = type;
   }

   /**
    * Gets the identifier for this database type.
    *
    * @return the type identifier.
    */
   public final String getType() {
      return type;
   }

   /**
    * Creates a property container for this database type.
    *
    * @return a new database info instance.
    */
   public abstract T createDatabaseInfo();

   /**
    * Parses the database properties from the driver class name and the JDBC
    * URL.
    *
    * @param driverClass the fully-qualified class name of the JDBC driver.
    * @param url         the URL to be parsed.
    * @param info        the object in which the database properties will be
    *                    set.
    *
    * @return the network location, if available.
    */
  public abstract NetworkLocation parse(String driverClass, String url, T info);

   /**
    * Formats a JDBC URL.
    *
    * @param network the network location, if available.
    * @param info    the database-specific properties.
    *
    * @return the JDBC URL.
    */
   public abstract String formatUrl(NetworkLocation network, T info);

   /**
    * Gets the fully-qualified class name of the JDBC driver.
    *
    * @param info the database-specific properties.
    *
    * @return the JDBC driver class name.
    */
   public abstract String getDriverClass(T info);

   /**
    * Determines if this type supports the specified driver class.
    *
    * @param driverClass the fully-qualified class name of the JDBC driver.
    *
    * @return <tt>true</tt> if the driver is supported; <tt>false</tt>
    *         otherwise.
    */
   public abstract boolean supportsDriverClass(String driverClass);

   /**
    * Determines if the driver class for this database type is installed.
    *
    * @return <tt>true</tt> if installed; <tt>false</tt> otherwise.
    */
   public abstract boolean isDriverInstalled();

   /**
    * Gets the default port number for this database type.
    *
    * @return the default port number
    */
   public abstract int getDefaultPort();

   private final String type;
}
