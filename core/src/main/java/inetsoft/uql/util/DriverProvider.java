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
package inetsoft.uql.util;

import inetsoft.uql.jdbc.SQLExecutor;

import java.net.URL;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Set;

/**
 * {@code DriverProvider} is the interface for classes that handle finding the classpath for
 * JDBC driver instances.
 */
interface DriverProvider {

   /**
    * Gets the class loader for a specific JDBC driver.
    *
    * @param classname the fully qualified class name of the driver.
    * @param url       the JDBC URL of the database.
    *
    * @return the class loader.
    */
   ClassLoader getDriverClassLoader(String classname, String url);

   /**
    * Gets the executor for a JDBC data source.
    *
    * @param classname the fully qualified class name of the JDBC driver.
    * @param url       the JDBC connection URL.
    *
    * @return the executor or {@code null} if there is none.
    */
   SQLExecutor getSQLExecutor(String classname, String url);

   /**
    * Gets the available JDBC drivers.
    *
    * @return a set of fully qualified JDBC driver class names.
    */
   Set<String> getDrivers();

   /**
    * Gets the driver for the specified JDBC URL.
    *
    * @param url the URL.
    *
    * @return the driver or {@code null} if not found.
    */
   Driver getDriver(String url) throws SQLException;

   /**
    * Loads a class from the driver class loader.
    *
    * @param className the name of the class.
    *
    * @return the class.
    *
    * @throws ClassNotFoundException if the class could not be found.
    */
   Class<?> getDriverClass(String className) throws ClassNotFoundException;

   /**
    * Gets the URL of a resource from the driver class loader.
    *
    * @param name the name of the resource.
    * @return the resource URL or <tt>null</tt> if not found.
    */
   URL getDriverResource(String name);
}
