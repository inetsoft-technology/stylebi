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

import inetsoft.uql.XTable;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.script.XTableArray;
import inetsoft.uql.util.XNodeTable;
import inetsoft.util.script.FunctionObject2;
import inetsoft.util.script.JavaScriptEngine;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * PreparedStatementScriptable encapsulates a PreparedStatement.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class PreparedStatementScriptable extends ScriptableObject {
   /**
    * Constructor.
    * @param sql the specified sql definition.
    * @param conn JDBC Connection.
    * @param jdbcSrc JDBC data source.
    */
   public PreparedStatementScriptable(String sql, Connection conn,
                                      JDBCDataSource jdbcSrc) throws Throwable
   {
      this.conn = conn;
      this.jdbcSrc = jdbcSrc;
      addFunctions();

      try {
         pstmt = conn.prepareStatement(sql);
      }
      catch(Throwable ex) {
         LOG.error("Failed to prepare statement: " + sql, ex);
         throw ex;
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "PreparedStatementScriptable";
   }

   /**
    * Add methods to write to database.
    */
   private void addFunctions() {
      try {
         FunctionObject func = new FunctionObject2(this, getClass(), "addBatch");
         put("addBatch", this, func);

         func = new FunctionObject2(this, getClass(), "clearParameters");
         put("clearParameters", this, func);

         func = new FunctionObject2(this, getClass(), "execute");
         put("execute", this, func);

         func = new FunctionObject2(this, getClass(), "executeBatch");
         put("executeBatch", this, func);

         func = new FunctionObject2(this, getClass(), "executeQuery");
         put("executeQuery", this, func);

         func = new FunctionObject2(this, getClass(), "executeUpdate");
         put("executeUpdate", this, func);

         func = new FunctionObject2(this, getClass(), "setBigDecimal", int.class, Object.class);
         put("setBigDecimal", this, func);

         func = new FunctionObject2(this, getClass(), "setBoolean", int.class, boolean.class);
         put("setBoolean", this, func);

         func = new FunctionObject2(this, getClass(), "setByte", int.class, Object.class);
         put("setByte", this, func);

         func = new FunctionObject2(this, getClass(), "setDate", int.class, Object.class);
         put("setDate", this, func);

         func = new FunctionObject2(this, getClass(), "setDouble", int.class, double.class);
         put("setDouble", this, func);

         func = new FunctionObject2(this, getClass(), "setFloat", int.class, Object.class);
         put("setFloat", this, func);

         func = new FunctionObject2(this, getClass(), "setInt", int.class, int.class);
         put("setInt", this, func);

         func = new FunctionObject2(this, getClass(), "setLong", int.class, Object.class);
         put("setLong", this, func);

         func = new FunctionObject2(this, getClass(), "setNull", int.class, int.class);
         put("setNull", this, func);

         func = new FunctionObject2(this, getClass(), "setNull", int.class, int.class, String.class);
         put("setNull", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", int.class, Object.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", int.class, Object.class, int.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", int.class, Object.class, int.class, int.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setShort", int.class, Object.class);
         put("setShort", this, func);

         func = new FunctionObject2(this, getClass(), "setString", int.class, String.class);
         put("setString", this, func);

         func = new FunctionObject2(this, getClass(), "setTime", int.class, Object.class);
         put("setTime", this, func);

         func = new FunctionObject2(this, getClass(), "setTimestamp", int.class, Object.class);
         put("setTimestamp", this, func);

         func = new FunctionObject2(this, getClass(), "setBinaryStream", int.class, String.class);
         put("setBinaryStream", this, func);
      }
      catch(Exception e) {
         LOG.warn("Failed to register prepared statement properties and functions",
            e);
      }
   }

   public void addBatch() throws Throwable {
      try {
         pstmt.addBatch();
      }
      catch(Throwable ex) {
         LOG.error("Failed to add batch", ex);
         throw ex;
      }
   }

   public void clearParameters() throws Throwable {
      try {
         pstmt.clearParameters();
      }
      catch(Throwable ex) {
         LOG.error("Failed to clear parameters", ex);
         throw ex;
      }
   }

   public Object execute() throws Throwable {
      try {
         return pstmt.execute();
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute statement", ex);
         throw ex;
      }
   }

   public Object executeBatch() throws Throwable {
      try {
         return pstmt.executeBatch();
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute batch", ex);
         throw ex;
      }
   }

   public Object executeQuery() throws Throwable {
      try {
         ResultSet result = pstmt.executeQuery();
         JDBCTableNode root = new JDBCTableNode(result, conn, pstmt,
            new JDBCSelection(), jdbcSrc) {
               @Override
               protected void releaseConnection() throws Exception {
                  // do nothing since here we will call script close
               }
            };
         XNodeTable delegate = new XNodeTable();
         delegate.setNode(root);
         XTable table = delegate.getTable();

         return new XTableArray(table);
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute query", ex);
         throw ex;
      }
   }

   public Object executeUpdate() throws Throwable {
      try {
         return pstmt.executeUpdate();
      }
      catch(Throwable ex) {
         LOG.error("Failed to execute update", ex);
         throw ex;
      }
   }

   public void setBigDecimal(int parameterIndex, Object x) throws Throwable
   {
      try {
         String val = numberToString(x);
         pstmt.setBigDecimal(parameterIndex, new BigDecimal(val));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set big decimal parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setBoolean(int parameterIndex, boolean x) throws Throwable {
      try {
         pstmt.setBoolean(parameterIndex, x);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set boolean parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setByte(int parameterIndex, Object x) throws Throwable {
      try {
         String val = numberToString(x);
         pstmt.setByte(parameterIndex, Byte.valueOf(val).byteValue());
      }
      catch(Throwable ex) {
         LOG.error("Failed to set byte parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setDate(int parameterIndex, Object x) throws Throwable {
      try {
         pstmt.setDate(parameterIndex, getDate(x));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set date parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setDouble(int parameterIndex, double x) throws Throwable {
      try {
         pstmt.setDouble(parameterIndex, x);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set double parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setFloat(int parameterIndex, Object x) throws Throwable {
      try {
         String val = numberToString(x);
         pstmt.setFloat(parameterIndex, Float.parseFloat(val));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set float parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setInt(int parameterIndex, int x) throws Throwable {
      try {
         pstmt.setInt(parameterIndex, x);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set int parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setLong(int parameterIndex, Object x) throws Throwable {
      try {
         String val = numberToString(x);
         pstmt.setLong(parameterIndex, Long.parseLong(val));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set long parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setNull(int parameterIndex, int sqlType) throws Throwable {
      try {
         pstmt.setNull(parameterIndex, sqlType);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set null parameter [" +
            parameterIndex + "]", ex);
         throw ex;
      }
   }

   public void setNull(int parameterIndex, int sqlType, String typeName)
      throws Throwable
   {
      try {
         pstmt.setNull(parameterIndex, sqlType, typeName);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set byte parameter [" +
            parameterIndex + "]: " + sqlType, ex);
         throw ex;
      }
   }

   public void setObject(int parameterIndex, Object x) throws Throwable {
      try {
         pstmt.setObject(parameterIndex, JavaScriptEngine.unwrap(x));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set object parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setObject(int parameterIndex, Object x, int targetSqlType)
      throws Throwable
   {
      try {
         pstmt.setObject(parameterIndex, JavaScriptEngine.unwrap(x),
                         targetSqlType);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set byte parameter [" +
            parameterIndex + "]: " + x + ", type=" + targetSqlType, ex);
         throw ex;
      }
   }

   public void setObject(int parameterIndex, Object x, int targetSqlType,
                         int scale) throws Throwable {
      try {
         pstmt.setObject(parameterIndex, JavaScriptEngine.unwrap(x),
                         targetSqlType, scale);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set byte parameter [" +
            parameterIndex + "]: " + x + ", type=" + targetSqlType +
            ", scale=" + scale, ex);
         throw ex;
      }
   }

   public void setShort(int parameterIndex, Object x) throws Throwable {
      try {
         String val = numberToString(x);
         pstmt.setShort(parameterIndex, Short.parseShort(val));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set short parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setString(int parameterIndex, String x) throws Throwable {
      try {
         pstmt.setString(parameterIndex, x);
      }
      catch(Throwable ex) {
         LOG.error("Failed to set string parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setTime(int parameterIndex, Object x) throws Throwable {
      try {
         pstmt.setTime(parameterIndex, new Time(getDate(x).getTime()));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set time parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   public void setBinaryStream(int parameterIndex, String in)
      throws Throwable
   {
      try {
         byte[] bytes = in.getBytes(StandardCharsets.UTF_8.name());
         InputStream stream = new ByteArrayInputStream(bytes);
         pstmt.setBinaryStream(parameterIndex, stream, bytes.length);
      }
      catch(Throwable ex) {
         LOG.error(
            "Failed to set binaryStream parameter [" +
            parameterIndex + "]: " + in, ex);
         throw ex;
      }
   }

   public void setTimestamp(int parameterIndex, Object x) throws Throwable {
      try {
         pstmt.setTimestamp(parameterIndex,
                            new Timestamp(getDate(x).getTime()));
      }
      catch(Throwable ex) {
         LOG.error("Failed to set timestamp parameter [" +
            parameterIndex + "]: " + x, ex);
         throw ex;
      }
   }

   private String numberToString(Object x) throws Exception {
      if(!JavaScriptEngine.isNumber(x)) {
         throw new RuntimeException("Invalid number object found: " + x);
      }

      return JavaScriptEngine.numberToString(x);
   }

   private Date getDate(Object x) throws Exception {
      if(!JavaScriptEngine.isDate(x)) {
         throw new RuntimeException("Invalid date found: " + x);
      }

      return new Date(JavaScriptEngine.getDate(x).getTime());
   }

   private String sql;
   private Connection conn;
   private JDBCDataSource jdbcSrc;
   private PreparedStatement pstmt;
   private static final Logger LOG =
      LoggerFactory.getLogger(PreparedStatementScriptable.class);
}
