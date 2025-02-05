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
package inetsoft.uql.aerospike;

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
 * Class that provides Aerospike database connection and query execution utility
 */
@SuppressWarnings("unused")
public class AerospikeRuntime extends TabularRuntime {
   /**
    * Execute a Aerospike query.
    *
    * @param query  a tabular query.
    * @param params parameters for query.
    *
    * @return the result of the query.
    */
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      AerospikeQuery query0 = (AerospikeQuery) query;
      AerospikeDataSource ds = (AerospikeDataSource) query0.getDataSource();
      String sql = query0.getQueryString().trim();
      ResultSet res;
      Connection conn = null;

      try{
         conn = getConnection(ds);
         Statement stmt = conn.createStatement();
         res = stmt.executeQuery(sql);
         return new AerospikeTable(res);
      }
      catch (Exception ex) {
         LOG.warn("Failed to execute Aerospike query: " + sql, ex);
         Tool.addUserMessage("Failed to execute Aerospike query: " + sql +
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
    * @param ds Aerospike tabular datasource
    * @param params parameters
    */
   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params)
      throws Exception {
      Connection conn = getConnection((AerospikeDataSource) ds);

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
   private Connection getConnection(AerospikeDataSource ds) throws Exception{
      Driver aerospikeDriver = getDriver(ds);
      String url = constructUrl(ds);
      Properties props = new Properties();

      String user = ds.getUser();
      String pwd = ds.getPassword();

      props.put("user", user == null ? "" : user);
      props.put("password", pwd == null ? "" : pwd);

      assert aerospikeDriver != null;
      return aerospikeDriver.connect(url, props);
   }

   /**
    * construct the JDBC url
    *
    * @return JDBC url
    */
   private String constructUrl(AerospikeDataSource ds) {
      return "jdbc:aerospike:" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getNamespace();
   }

   /**
    * get the appropriate driver class based on user selection
    *
    * @return JDBC driver class
    */
   private Driver getDriver(AerospikeDataSource ds) throws Exception{
      try {
         return JDBCHandler.getDriver("com.aerospike.jdbc.AerospikeDriver");
      }
      catch (Exception ex){
         LOG.warn("Failed to obtain Aerospike JDBC driver", ex);
         throw ex;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(AerospikeRuntime.class.getName());
}
