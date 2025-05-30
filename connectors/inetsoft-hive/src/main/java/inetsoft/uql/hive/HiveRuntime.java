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
package inetsoft.uql.hive;

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
 * Class that provides Hive database connection and query execution utility
 */
@SuppressWarnings("unused")
public class HiveRuntime extends TabularRuntime {
   /**
    * Execute a Hive query.
    *
    * @param query  a tabular query.
    * @param params parameters for query.
    *
    * @return the result of the query.
    */
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      HiveQuery query0 = (HiveQuery) query;
      HiveDataSource ds = (HiveDataSource) query0.getDataSource();
      String sql = query0.getQueryString().trim();
      ResultSet res;
      Connection conn = null;

      try{
         conn = getConnection(ds);
         Statement stmt = conn.createStatement();
         res = stmt.executeQuery(sql);
         return new HiveTable(res);
      }
      catch (Exception ex) {
         LOG.warn("Failed to execute Hive query: " + sql, ex);
         Tool.addUserMessage("Failed to execute Hive query: " + sql +
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
    * @param ds Hive tabular datasource
    * @param params parameters
    */
   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params)
      throws Exception {
      Connection conn = getConnection((HiveDataSource) ds);

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
   private Connection getConnection(HiveDataSource ds) throws Exception{
      Driver hiveDriver = getDriver(ds);
      String url = constructUrl(ds);
      Properties props = new Properties();

      String user = ds.getUser();
      String pwd = ds.getPassword();

      props.put("user", user == null ? "" : user);
      props.put("password", pwd == null ? "" : pwd);

      assert hiveDriver != null;
      return hiveDriver.connect(url, props);
   }

   /**
    * construct the JDBC url
    *
    * @return JDBC url
    */
   private String constructUrl(HiveDataSource ds) {
      return "jdbc:hive2://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
   }

   /**
    * get the appropriate driver class based on user selection
    *
    * @return JDBC driver class
    */
   private Driver getDriver(HiveDataSource ds) throws Exception{
      try {
         return new org.apache.hive.jdbc.HiveDriver();
      }
      catch (Exception ex){
         LOG.warn("Failed to obtain Hive JDBC driver", ex);
         throw ex;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(HiveRuntime.class.getName());
}
