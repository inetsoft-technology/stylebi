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
package inetsoft.uql;

import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.XHandler;
import inetsoft.util.DataCacheVisitor;

import java.rmi.RemoteException;
import java.security.Principal;

/**
 * This interface defines the API of the runtime query engine. It can be
 * used to find the query parameters and execute queries. An instance of
 * of the XDataService can be obtained from the XFactory class.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface XDataService extends AutoCloseable {
   /**
    * Connect to the data service.
    * @param uinfo user info.
    * @return session object.
    */
   Object bind(Object uinfo) throws RemoteException;

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param query query name.
    * @return list of variables, or null if no user variable is needed for
    * the connection.
    */
   UserVariable[] getConnectionParameters(Object session, String query)
      throws RemoteException;

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param xquery query to get the connection parameters.
    * @return list of variables, or null if no user variable is needed for
    * the connection.
    */
   UserVariable[] getConnectionParameters(Object session, XQuery xquery)
      throws RemoteException;

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param dx data source to get connection parameters.
    */
   UserVariable[] getConnectionParameters(Object session, XDataSource dx)
      throws RemoteException;

   /**
    * Get the parameters for a query. The parameters should be filled in
    * and passed to execute().
    * @param session session object.
    * @param xquery query to get parameters.
    * @param promptOnly true if only include the user variables that
    * are declared as 'Prompt User'.
    * @return list of variables, or null if no user variable is needed for
    * the query.
    */
   UserVariable[] getQueryParameters(Object session, XQuery xquery,
                                     boolean promptOnly)
      throws RemoteException;

   /**
    * Test a data source connection. The data source connection is
    * shared by a session.
    * @param session session object.
    * @param dx datasource.
    * @param params connection parameters.
    */
   void testDataSource(Object session, XDataSource dx,
                       VariableTable params)
      throws Exception;

   /**
    * Initialize a data source connection. The data source connection is
    * shared by a session. A connection does not have to be created
    * directly. If a connection is not created by the time a query is
    * executed, a connection will be created in the execute() call. However,
    * it the data source uses any parameters to establish a connection,
    * it is strongly recommended that the connection parameters and
    * connection itself being made explicitly before any query is executed.
    * @param session session object.
    * @param query query name.
    * @param params connection parameters.
    */
   void connect(Object session, String query, VariableTable params)
      throws Exception;

   /**
    * Initialize a data source connection. The data source connection is
    * shared by a session.
    * @param session session object.
    * @param dx specified data source.
    * @param params connection parameters.
    */
   void connect(Object session, XDataSource dx, VariableTable params)
      throws Exception;

   /**
    * Get the handler.
    * @param session the specified session object.
    * @param dx the specified data source.
    * @param params the specified variable table.
    */
   XHandler getHandler(Object session, XDataSource dx,
                       VariableTable params)
      throws Exception;

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, String query, VariableTable vars)
      throws Exception;

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, String query, VariableTable vars,
                 boolean resetVariables) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, String query, VariableTable vars,
                 Principal user) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, String query, VariableTable vars,
                 Principal user, boolean resetVariables) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @param visitor used to process cache facility.
    */
   XNode execute(Object session, String query, VariableTable vars,
                 Principal user, boolean resetVariables, DataCacheVisitor visitor)
      throws Exception;

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, XQuery xquery, VariableTable vars)
      throws Exception;

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, XQuery xquery, VariableTable vars,
                 boolean resetVariables) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, XQuery xquery, VariableTable vars,
                 Principal user) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   XNode execute(Object session, XQuery xquery, VariableTable vars,
                 Principal user, boolean resetVariables) throws Exception;

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @param visitor used to process cache facility.
    */
   XNode execute(Object session, XQuery xquery, VariableTable vars,
                 Principal user, boolean resetVariables, DataCacheVisitor visitor)
      throws Exception;

   /**
    * Close an active session.
    * @param session session object.
    */
   void close(Object session) throws RemoteException;
}

