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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.FunctionObject2;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DBScriptable encapsulates a JDBC connection.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class DBScriptable extends ScriptableObject {
   /**
    * Constructor.
    * @param source data source name.
    * @param user user name to be logined.
    * @param passwd password to be logined.
    * @param principal the user who request database connection.
    */
   public DBScriptable(String source, String user, String passwd,
                       Principal principal) {
      init(source, user, passwd, principal);
   }

   /**
    * Init the connection.
    */
   public void init(String source, String user, String passwd,
                    Principal principal)
   {
      XDataSource xds = null;

      try {
         XRepository rep = XFactory.getRepository();
         xds = XUtil.getDatasource(principal, rep.getDataSource(source, true));
      }
      catch(Throwable ex) {
         LOG.error("Failed to get data source: " + source, ex);
      }

      if(!(xds instanceof JDBCDataSource)) {
         throw new RuntimeException(
            "DBScriptable only supports JDBC datasource!");
      }

      jdbcSrc = (JDBCDataSource) xds;

      if(user != null) {
         jdbcSrc.setUser(user);
      }

      if(passwd != null) {
         jdbcSrc.setPassword(passwd);
      }

      try {
         conn =
            JDBCHandler.getConnectionPool(jdbcSrc, principal).getConnection();
      }
      catch(SQLException e) {
         throw new RuntimeException("Failed to open database connection", e);
      }

      addFunctions();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "DBScriptable";
   }

   /**
    * Execute select sql.
    * @param sql the specified select sql sentence.
    * @return TableArray of executed result.
    */
   public Object executeSelect(String sql) throws Throwable {
      try {
         Object pstmt = prepareStatement(sql);
         return executeQuery((PreparedStatementScriptable) pstmt);
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute select SQL: " + sql, ex);
         throw ex;
      }
   }

   /**
    * Execute select sql.
    * @param pstmt the specified PreparedStatementScriptable.
    * @return TableArray of executed result.
    */
   public Object executeQuery(PreparedStatementScriptable pstmt)
      throws Throwable
   {
      try {
         return pstmt.executeQuery();
      }
      catch(Throwable ex) {
         LOG.error("Falied to execute prepared statement", ex);
         throw ex;
      }
   }

   /**
    * Execute update sql.
    * @param sql the specified update sql sentence.
    */
   public void executeUpdate(String sql) throws Throwable {
      try {
         Object pstmt = prepareStatement(sql);
         update((PreparedStatementScriptable) pstmt);
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute update SQL: " + sql, ex);
         throw ex;
      }
   }

   /**
    * Execute update sql.
    * @param pstmt the specified PreparedStatementScriptable.
    */
   public void update(PreparedStatementScriptable pstmt)
      throws Throwable
   {
      if(scriptOver) {
         return;
      }

      try {
         pstmt.executeUpdate();
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute prepared statement update", ex);
         throw ex;
      }
   }

   /**
    * Commit.
    */
   public void commit() throws Throwable {
      if(scriptOver) {
         return;
      }

      try {
         conn.commit();
         Thread.sleep(1000);
         AssetDataCache.removeCacheDependence(jdbcSrc.getName());
      }
      catch(Throwable ex) {
         LOG.error("Failed to commit transaction", ex);
         throw ex;
      }
   }

   /**
    * Roll back.
    */
   public void rollback() throws Throwable {
      if(scriptOver) {
         return;
      }

      try {
         conn.rollback();
      }
      catch(Throwable ex) {
         LOG.error("Failed to roll back transaction", ex);
         throw ex;
      }
   }

   /**
    * Close connection.
    */
   public void close() throws Throwable {
      if(scriptOver) {
         return;
      }

      Tool.closeQuietly(conn);
      conn = null;
   }

   /**
    * Prepare statement.
    * @param sql the specified sql definition.
    * @return PreparedStatementScriptable which encapsulates a
    * PreparedStatement.
    */
   public Object prepareStatement(String sql) throws Throwable {
      try {
         return new PreparedStatementScriptable(sql, conn, jdbcSrc);
      }
      catch(Throwable ex) {
         LOG.error("Failed to prepare statment: " + sql, ex);
         throw ex;
      }
   }

   /**
    * Prepare call.
    * @param sql the specified sql definition.
    * @return CallableStatementScriptable which encapsulates a
    * CallableStatement.
    */
   public Object prepareCall(String sql) throws Throwable {
      return new CallableStatementScriptable(sql, conn, jdbcSrc);
   }

   /**
    * Add methods to write to database.
    */
   private void addFunctions() {
      try {
         FunctionObject func = new FunctionObject2(this, getClass(), "executeSelect", String.class);
         put("executeSelect", this, func);

         func = new FunctionObject2(this, getClass(), "executeQuery",
                                    PreparedStatementScriptable.class);
         put("executeQuery", this, func);

         func = new FunctionObject2(this, getClass(), "executeUpdate", String.class);
         put("executeUpdate", this, func);

         func = new FunctionObject2(this, getClass(), "update", PreparedStatementScriptable.class);
         put("update", this, func);

         func = new FunctionObject2(this, getClass(), "commit");
         put("commit", this, func);

         func = new FunctionObject2(this, getClass(), "rollback");
         put("rollback", this, func);

         func = new FunctionObject2(this, getClass(), "close");
         put("close", this, func);

         func = new FunctionObject2(this, getClass(), "prepareStatement", String.class);
         put("prepareStatement", this, func);

         func = new FunctionObject2(this, getClass(), "prepareCall", String.class);
         put("prepareCall", this, func);
      }
      catch(Exception e) {
         LOG.warn("Failed to register database properties and functions", e);
      }
   }

   /**
    * Check if the script already run once.
    */
   public boolean isScriptOver() {
      return scriptOver;
   }

   /**
    * Set the script whether run once.
    */
   public void setScriptOver(boolean scriptOver) {
      this.scriptOver = scriptOver;
   }

   private Connection conn;
   private JDBCDataSource jdbcSrc;
   private boolean scriptOver = false;
   private static final Logger LOG =
      LoggerFactory.getLogger(DBScriptable.class);
}
