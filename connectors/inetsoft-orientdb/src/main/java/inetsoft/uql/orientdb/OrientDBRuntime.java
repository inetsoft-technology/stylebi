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
package inetsoft.uql.orientdb;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

/**
 * Class that provides OrientDB database connection and query execution utility
 */
@SuppressWarnings("unused")
public class OrientDBRuntime extends TabularRuntime {
   /**
    * Execute a OrientDB query.
    *
    * @param query  a tabular query.
    * @param params parameters for query.
    *
    * @return the result of the query.
    */
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      OrientDBQuery query0 = (OrientDBQuery) query;
      OrientDBDataSource ds = (OrientDBDataSource) query0.getDataSource();
      String sql = query0.getQueryString().trim();
      ResultSet res;
      Connection conn = null;

      try{
         conn = getConnection(ds);
         Statement stmt = conn.createStatement();
         res = stmt.executeQuery(sql);
         return new OrientDBTable(res);
      }
      catch (Exception ex) {
         LOG.warn("Failed to execute OrientDB query: " + sql, ex);
         Tool.addUserMessage("Failed to execute OrientDB query: " + sql +
                                " (" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }

      if(conn != null) {
         try {
            conn.close();
         }
         catch(Exception ex) {
            LOG.warn("Failed to close connection: " + ds.getName(), ex);
         }
      }

      return null;
   }

   /**
    * Test if the data source is correct (connection). Throws an exception
    * if the data source can be connected.
    *
    * @param ds OrientDB tabular datasource
    * @param params parameters
    */
   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params)
      throws Exception {
      Connection conn = getConnection((OrientDBDataSource) ds);

      try {
         Statement stmt = conn.createStatement();
         stmt.close();
      }
      finally {
         conn.close();
      }
   }

   /**
    * Obtain a connection from the JDBC driver
    *
    * @return Connection
    * @throws Exception
    */
   private Connection getConnection(OrientDBDataSource ds) throws Exception{
      Driver orientDBDriver = getDriver(ds);
      String url = ds.getUrl();
      Properties props = new Properties();

      String user = ds.getUser();
      String pwd = ds.getPassword();

      props.put("user", user == null ? "" : user);
      props.put("password", pwd == null ? "" : pwd);

      assert orientDBDriver != null;
      return orientDBDriver.connect(url, props);
   }

   /**
    * get the appropriate driver class based on user selection
    *
    * @return JDBC driver class
    */
   private Driver getDriver(OrientDBDataSource ds) throws Exception{
      try {
         return JDBCHandler.getDriver("com.orientechnologies.orient.jdbc.OrientJdbcDriver");
      }
      catch (Exception ex){
         LOG.warn("Failed to obtain OrientDB JDBC driver", ex);
         throw ex;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(OrientDBRuntime.class.getName());
}
