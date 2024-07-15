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
package inetsoft.uql.jdbc;

import java.util.Set;

/**
 * This class is the API of a service for JDBC driver.
 *
 * @version 12.2, 1/26/2017
 * @author InetSoft Technology Corp
 */
public interface DriverService {
   /**
    * Check if this driver matches the data source.
    * @param driver JDBC driver class name.
    * @param url JDBC URL.
    */
   boolean matches(String driver, String url);

   /**
    * Get the executor to execute SQL queries.
    */
   SQLExecutor getSQLExecutor();

   /**
    * Gets the drivers provided by this service.
    *
    * @return the fully-qualified names of the JDBC drivers.
    */
   Set<String> getDrivers();
}
