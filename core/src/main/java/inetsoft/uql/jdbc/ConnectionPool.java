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

import inetsoft.uql.XDataSource;

import java.security.Principal;
import java.sql.Connection;

/**
 * This interface defines the API of a connection pool provider. If a
 * connection pool is supplied, all JDBC datasource uses the connection
 * pool to obtain connections. A connection pool can be specified by
 * passing an object implementing the connection pool to the 
 * JDBCHandler.setConnectionPool().
 *
 * @deprecated use {@link ConnectionPoolFactory} instead.
 */
@Deprecated
public interface ConnectionPool {
   /**
    * Get a connection from the connection pool. If there is no more connection
    * in the pool, wait for a connection to be released.
    *
    * @param xds jdbc datasource. A connection pool can support multiple
    *            datasource by checking for the datasource name.
    * @param user a Principal object that identifies the user for whom the
    *             connection is being retrieved.
    */
   Connection getConnection(XDataSource xds, Principal user);
   
   /**
    * Release a connection back to the connection pool.
    *
    * @param xds jdbc datasource.
    * @param conn database connection.
    */
   void releaseConnection(XDataSource xds, Connection conn);
}

