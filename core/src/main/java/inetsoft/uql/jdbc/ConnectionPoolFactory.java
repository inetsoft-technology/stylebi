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

import javax.sql.DataSource;
import java.security.Principal;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Interface for classes that handle creating the connection pools for data
 * sources.
 *
 * @since 12.2
 */
public interface ConnectionPoolFactory extends AutoCloseable {
   /**
    * Gets the connection pool for a JDBC data source.
    *
    * @param jdbcDataSource the JDBC data source.
    * @param user           the principal that identifies the user for whom the
    *                       connection pool is being requested.
    *
    * @return the connection pool.
    */
   DataSource getConnectionPool(JDBCDataSource jdbcDataSource, Principal user);

   /**
    * Closes a connection pool when it is no longer needed.
    *
    * @param dataSource the connection pool to close.
    */
   void closeConnectionPool(DataSource dataSource);

   /**
    * Closes all connections pools that are matched by a filter.
    *
    * @param filter a predicate that determines if a pool should be closed.
    */
   void closeConnectionPools(Predicate<DataSource> filter);

   /**
    * Closes all connection pools.
    */
   void closeAllConnectionsPools();

   /**
    * Determines if a data source uses the specified driver class.
    *
    * @param dataSource      the data source to check.
    * @param driverClassName the fully qualified class name of the driver.
    *
    * @return {@code} true if the driver is used or {@code false} if not.
    */
   boolean isDriverUsed(DataSource dataSource, String driverClassName);
}
