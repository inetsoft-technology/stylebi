/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.security.Principal;
import java.sql.Connection;

/**
 * Abstract base class for the application server connection pool bridge classes.
 *
 * @author  InetSoft Technology
 * @since   6.1
 */
public abstract class JNDIConnectionPool implements ConnectionPool {
   /**
    * Get a connection from the connection pool. If there are no available
    * connections, block until one is available.
    *
    * @param xds the JDBC datasource to get a connection to.
    * @param user a Principal object that identifies the user for whom the
    *             connection is being retrieved.
    *
    * @return a connection to the specified datasource.
    */
   @Override
   public Connection getConnection(XDataSource xds, Principal user) {
      Connection conn = null;

      try {
         Context ctx = getInitialContext();
         DataSource ds =
            (DataSource) ctx.lookup(getDataSourcePrefix() + xds.getFullName());

         if(ds != null) {
            conn = ds.getConnection();
         }
         else {
            LOG.warn(String.format(
               "Unable to find JNDI datasource %s%s",
               getDataSourcePrefix(), xds.getFullName()));
         }
      }
      catch(Exception exc) {
         // fix customer bug: bug1286828514751
         if(user == null || ((XPrincipal) user).getProperty("__test__") == null)
         {
            LOG.error("Failed to connect to database", exc);
         }
      }

      return conn;
   }

   /**
    * Release the specified connection back into the connection pool.
    *
    * @param xds the JDBC datasource that the connection is to.
    * @param conn the JDBC connection object to release.
    */
   @Override
   public void releaseConnection(XDataSource xds, Connection conn) {
      try {
         conn.close();
      }
      catch(Exception exc) {
         LOG.error("Failed to release connection to pool", exc);
      }
   }

   /**
    * Get the initial JNDI context used to lookup up the datasource.
    *
    * @return the initial JNDI context.
    *
    * @throws NamingException if an error occurs while creating the context.
    */
   protected abstract Context getInitialContext() throws NamingException;

   /**
    * Get the path that should be prepended to the datasource name when looking
    * it up in JNDI.
    *
    * @return the path to the datasource.
    */
   protected abstract String getDataSourcePrefix();

   private static final Logger LOG =
      LoggerFactory.getLogger(JNDIConnectionPool.class);
}
