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
public class HiveRuntime extends TabularRuntime implements TabularCatalogProvider {
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

   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      HiveDataSource ds = (HiveDataSource) dataSource;
      requireDbName(ds);

      try(Connection conn = getConnection(ds)) {
         return HiveCatalog.listDatasets(conn, normalizedDbName(ds));
      }
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      HiveDataSource ds = (HiveDataSource) dataSource;
      requireDbName(ds);

      try(Connection conn = getConnection(ds)) {
         return HiveCatalog.describeDataset(conn, normalizedDbName(ds), datasetId);
      }
   }

   /**
    * A4: an unconfigured database must throw, not silently enumerate nothing. Checked before
    * {@link #getConnection} is even called -- {@link #constructUrl} would otherwise happily build
    * a "jdbc:hive2://host:port/" URL from a blank/null database, which some Hive deployments
    * interpret as an implicit default database rather than a connection failure.
    */
   private static void requireDbName(HiveDataSource ds) throws Exception {
      if(ds.getDbName() == null || ds.getDbName().isBlank()) {
         throw new Exception("Data source '" + ds.getName() +
            "' has no database configured; cannot enumerate its tables.");
      }
   }

   /**
    * Hive normalizes identifiers to lowercase internally, so a database name configured with
    * mixed case (e.g. "MyDB") is matched against the metastore's lowercase form. This is Hive's
    * well-documented external behavior; whether hive-jdbc 4.0.1 itself additionally normalizes
    * the schema pattern it is given has not been independently verified against the driver.
    */
   private static String normalizedDbName(HiveDataSource ds) {
      return ds.getDbName().toLowerCase();
   }

   private static final Logger LOG = LoggerFactory.getLogger(HiveRuntime.class.getName());
}
