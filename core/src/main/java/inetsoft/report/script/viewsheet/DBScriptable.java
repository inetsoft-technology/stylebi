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
package inetsoft.report.script.viewsheet;

import inetsoft.util.script.graal.ScriptFunction;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.uql.util.ConnectionProcessor;
import inetsoft.util.Tool;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DBScriptable encapsulates a JDBC connection.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class DBScriptable implements ScriptScope {
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
         XRepository rep = XRepository.getRepository();
         xds = ConnectionProcessor.getInstance().getDatasource(principal, rep.getDataSource(source, true));
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
   public String getClassName() {
      return "DBScriptable";
   }

   @Override
   public Object getMember(String name) {
      return members.get(name);
   }

   @Override
   public boolean hasMember(String name) {
      return members.containsKey(name);
   }

   @Override
   public void putMember(String name, Object value) {
      members.put(name, value);
   }

   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray();
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
         AssetDataCache.getCache().removeCacheDependence(jdbcSrc.getName());
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
      // Feature #75423: native functions exposed via ScriptFunction (GraalJS).
      try {
         ScriptFunction func = new ScriptFunction(this, getClass(), "executeSelect", String.class);
         members.put("executeSelect", func);

         func = new ScriptFunction(this, getClass(), "executeQuery",
                                    PreparedStatementScriptable.class);
         members.put("executeQuery", func);

         func = new ScriptFunction(this, getClass(), "executeUpdate", String.class);
         members.put("executeUpdate", func);

         func = new ScriptFunction(this, getClass(), "update", PreparedStatementScriptable.class);
         members.put("update", func);

         func = new ScriptFunction(this, getClass(), "commit");
         members.put("commit", func);

         func = new ScriptFunction(this, getClass(), "rollback");
         members.put("rollback", func);

         func = new ScriptFunction(this, getClass(), "close");
         members.put("close", func);

         func = new ScriptFunction(this, getClass(), "prepareStatement", String.class);
         members.put("prepareStatement", func);

         func = new ScriptFunction(this, getClass(), "prepareCall", String.class);
         members.put("prepareCall", func);
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
   private final Map<String, Object> members = new LinkedHashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(DBScriptable.class);
}
