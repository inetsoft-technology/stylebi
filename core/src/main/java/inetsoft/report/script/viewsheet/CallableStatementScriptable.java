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

import java.math.BigDecimal;
import java.sql.*;

/**
 * CallableStatementScriptable encapsulates a CallableStatement.
 *
 * @version 12.0
 * @author InetSoft Technology Corp
 */
public class CallableStatementScriptable extends ScriptableObject {
   public CallableStatementScriptable(String sql, Connection conn,
                                      JDBCDataSource jdbcSrc) throws Throwable
   {
      this.conn = conn;
      this.jdbcSrc = jdbcSrc;
      addFunctions();
      cstmt = conn.prepareCall(sql);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "CallableStatementScriptable";
   }

   /**
    * Add methods to write to database.
    */
   private void addFunctions() {
      try {
         FunctionObject func = new FunctionObject2(this, getClass(), "clearParameters");
         put("clearParameters", this, func);

         func = new FunctionObject2(this, getClass(), "execute");
         put("execute", this, func);

         func = new FunctionObject2(this, getClass(), "executeQuery");
         put("executeQuery", this, func);

         func = new FunctionObject2(this, getClass(), "executeUpdate");
         put("executeUpdate", this, func);

         func = new FunctionObject2(this, getClass(), "getBigDecimal", Object.class);
         put("getBigDecimal", this, func);

         func = new FunctionObject2(this, getClass(), "getBoolean", Object.class);
         put("getBoolean", this, func);

         func = new FunctionObject2(this, getClass(), "getByte", Object.class);
         put("getByte", this, func);

         func = new FunctionObject2(this, getClass(), "getBytes", Object.class);
         put("getBytes", this, func);

         func = new FunctionObject2(this, getClass(), "getClob", Object.class);
         put("getClob", this, func);

         func = new FunctionObject2(this, getClass(), "getDate", Object.class);
         put("getDate", this, func);

         func = new FunctionObject2(this, getClass(), "getDouble", Object.class);
         put("getDouble", this, func);

         func = new FunctionObject2(this, getClass(), "getFloat", Object.class);
         put("getFloat", this, func);

         func = new FunctionObject2(this, getClass(), "getInt", Object.class);
         put("getInt", this, func);

         func = new FunctionObject2(this, getClass(), "getLong", Object.class);
         put("getLong", this, func);

         func = new FunctionObject2(this, getClass(), "getNClob", Object.class);
         put("getNClob", this, func);

         func = new FunctionObject2(this, getClass(), "getNString", Object.class);
         put("getNString", this, func);

         func = new FunctionObject2(this, getClass(), "getObject", Object.class);
         put("getObject", this, func);

         func = new FunctionObject2(this, getClass(), "getRef", Object.class);
         put("getRef", this, func);

         func = new FunctionObject2(this, getClass(), "getRowId", Object.class);
         put("getRowId", this, func);

         func = new FunctionObject2(this, getClass(), "getShort", Object.class);
         put("getShort", this, func);

         func = new FunctionObject2(this, getClass(), "getSQLXML", Object.class);
         put("getSQLXML", this, func);

         func = new FunctionObject2(this, getClass(), "getString", Object.class);
         put("getString", this, func);

         func = new FunctionObject2(this, getClass(), "getTime", Object.class);
         put("getTime", this, func);

         func = new FunctionObject2(this, getClass(), "getTimestamp", Object.class);
         put("getTimestamp", this, func); 
         
         func = new FunctionObject2(this, getClass(), "registerOutParameter",
                                    Object.class, int.class, Object.class);
         put("registerOutParameter", this, func);

         func = new FunctionObject2(this, getClass(), "setBigDecimal",
                     Object.class, Object.class);
         put("setBigDecimal", this, func);

         func = new FunctionObject2(this, getClass(), "setBoolean", Object.class, boolean.class);
         put("setBoolean", this, func);

         func = new FunctionObject2(this, getClass(), "setByte", Object.class, Object.class);
         put("setByte", this, func);

         func = new FunctionObject2(this, getClass(), "setDate", Object.class, Object.class);
         put("setDate", this, func);

         func = new FunctionObject2(this, getClass(), "setDouble", Object.class, double.class);
         put("setDouble", this, func);

         func = new FunctionObject2(this, getClass(), "setFloat", Object.class, Object.class);
         put("setFloat", this, func);

         func = new FunctionObject2(this, getClass(), "setInt", Object.class, int.class);
         put("setInt", this, func);

         func = new FunctionObject2(this, getClass(), "setLong", Object.class, Object.class);
         put("setLong", this, func);

         func = new FunctionObject2(this, getClass(), "setNull", Object.class, int.class);
         put("setNull", this, func);

         func = new FunctionObject2(this, getClass(), "setNull", Object.class, int.class,
                                    String.class);
         put("setNull", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", Object.class, Object.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", Object.class, Object.class,
                                    int.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setObject", Object.class,
                                    Object.class, int.class, int.class);
         put("setObject", this, func);

         func = new FunctionObject2(this, getClass(), "setShort", Object.class, Object.class);
         put("setShort", this, func);

         func = new FunctionObject2(this, getClass(), "setString", Object.class, String.class);
         put("setString", this, func);

         func = new FunctionObject2(this, getClass(), "setTime", Object.class, Object.class);
         put("setTime", this, func);

         func = new FunctionObject2(this, getClass(), "setTimestamp", Object.class, Object.class);
         put("setTimestamp", this, func);

         func = new FunctionObject2(this, getClass(), "wasNull");
         put("wasNull", this, func);
      }
      catch(Exception ex) {
         LOG.error("Failed to add functions to Callable " +
               "Statement", ex);
      }
   }

   public void clearParameters() throws Throwable {
      cstmt.clearParameters();
   }

   public Object execute() throws Throwable {
      return cstmt.execute();
   }

   public Object executeQuery() throws Throwable {
      ResultSet result = cstmt.executeQuery();
      JDBCTableNode root = new JDBCTableNode(result, conn, cstmt,
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

   public Object executeUpdate() throws Throwable {
      return cstmt.executeUpdate();
   }

   public BigDecimal getBigDecimal(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getBigDecimal(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getBigDecimal((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getBigDecimal must" +
               " be of class String or Num.");
      }
   }

   public boolean getBoolean(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getBoolean(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getBoolean((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getBoolean must " +
               "be of class String or Num.");
      }
   }

   public byte getByte(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getByte(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getByte((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getByte must " +
               "be of class String or Num.");
      }
   }

   public byte[] getBytes(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getBytes(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getBytes((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getBytes must " +
               "be of class String or Num.");
      }
   }

   public Clob getClob(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getClob(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getClob((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getClob must " +
               "be of class String or Num.");
      }
   }

   public Date getDate(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getDate(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getDate((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getDate must " +
               "be of class String or Num.");
      }
   }

   public double getDouble(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getDouble(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getDouble((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getDouble must " +
               "be of class String or Num.");
      }
   }

   public float getFloat(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getFloat(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getFloat((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getFloat must " +
               "be of class String or Num.");
      }
   }

   public int getInt(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getInt(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getInt((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getInt must " +
               "be of class String or Num.");
      }
   }

   public long getLong(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getLong(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getLong((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getLong must " +
               "be of class String or Num.");
      }
   }

   public NClob getNClob(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getNClob(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getNClob((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getNClob must " +
               "be of class String or Num.");
      }
   }

   public String getNString(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getNString(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getNString((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getNString must " +
               "be of class String or Num.");
      }
   }

   public Object getObject(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getObject(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getObject((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getObject must " +
               "be of class String or Num.");
      }
   }

   public Ref getRef(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getRef(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getRef((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getRef must " +
               "be of class String or Num.");
      }
   }

   public RowId getRowId(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getRowId(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getRowId((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getRowId must " +
               "be of class String or Num.");
      }
   }

   public short getShort(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getShort(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getShort((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getShort must " +
               "be of class String or Num.");
      }
   }

   public SQLXML getSQLXML(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getSQLXML(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getSQLXML((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getSQLMXL must " +
               "be of class String or Num.");
      }
   }

   public String getString(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getString(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getString((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getString must " +
               "be of class String or Num.");
      }
   }

   public Time getTime(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getTime(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getTime((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getTime must " +
               "be of class String or Num.");
      }
   }

   public Timestamp getTimestamp(Object parameter) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         return cstmt.getTimestamp(((Number) parameter).intValue());
      }
      else if(parameter instanceof String) {
         return cstmt.getTimestamp((String) parameter);
      }
      else {
         throw new RuntimeException("First parameter of getTimestamp must " +
               "be of class String or Num.");
      }
   }
   
   public void registerOutParameter(Object parameter, int sqlType,
                                    Object parameter2) throws Throwable
   {
      parameter = JavaScriptEngine.unwrap(parameter);
      parameter2 = JavaScriptEngine.unwrap(parameter2);

      if(parameter instanceof Number) {
         if(parameter2 == null) {
            cstmt.registerOutParameter(((Number) parameter).intValue(),
               sqlType);
         }
         else if(parameter2 instanceof Number) {
            cstmt.registerOutParameter(((Number) parameter).intValue(),
               sqlType, ((Number) parameter2).intValue());
         }
         else if(parameter2 instanceof String) {
            cstmt.registerOutParameter(((Double) parameter).intValue(),
               sqlType, (String) parameter2);
         }
      }
      else if(parameter instanceof String) {
         if(parameter2 == null) {
            cstmt.registerOutParameter((String) parameter, sqlType);
         }
         else if(parameter2 instanceof Number) {
            cstmt.registerOutParameter((String) parameter, sqlType,
               ((Number) parameter2).intValue());
         }
         else if(parameter2 instanceof String) {
            cstmt.registerOutParameter((String) parameter, sqlType,
               (String) parameter2);
         }
      }
   }

   public void setBigDecimal(Object parameter, Object x) throws Throwable
   {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         String val = numberToString(x);
         cstmt.setBigDecimal(((Number) parameter).intValue(),
            new BigDecimal(val));
      }
      else if(parameter instanceof String) {
         String val = numberToString(x);
         cstmt.setBigDecimal((String) parameter, new BigDecimal(val));
      }
   }

   public void setBoolean(Object parameter, boolean x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setBoolean(((Number) parameter).intValue(), x);
      }
      else if(parameter instanceof String) {
         cstmt.setBoolean((String) parameter, x);
      }
   }

   public void setByte(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         String val = numberToString(x);
         cstmt.setByte(((Number) parameter).intValue(), Byte.parseByte(val));
      }
      else if(parameter instanceof String) {
         String val = numberToString(x);
         cstmt.setByte((String) parameter, Byte.parseByte(val));
      }
   }

   public void setDate(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setDate(((Number) parameter).intValue(), getDateFromObj(x));
      }
      else if(parameter instanceof String) {
         cstmt.setDate((String) parameter, getDateFromObj(x));
      }
   }

   public void setDouble(Object parameter, double x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setDouble(((Number) parameter).intValue(), x);
      }
      else if(parameter instanceof String) {
         cstmt.setDouble((String) parameter, x);
      }
   }

   public void setFloat(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         String val = numberToString(x);
         cstmt.setFloat(((Number) parameter).intValue(),
            Float.parseFloat(val));
      }
      else if(parameter instanceof String) {
         String val = numberToString(x);
         cstmt.setFloat((String) parameter, Float.parseFloat(val));
      }
   }

   public void setInt(Object parameter, int x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setInt(((Number) parameter).intValue(), x);
      }
      else if(parameter instanceof String) {
         cstmt.setInt((String) parameter, x);
      }
   }

   public void setLong(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter); 

      if(parameter instanceof Number) {
         String val = numberToString(x);
         cstmt.setLong(((Number) parameter).intValue(), Long.parseLong(val));
      }
      else if(parameter instanceof String) {
         String val = numberToString(x);
         cstmt.setLong((String) parameter, Long.parseLong(val));
      }
   }

   public void setNull(Object parameter, int sqlType) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setNull(((Number) parameter).intValue(), sqlType);
      }
      else if(parameter instanceof String) {
         cstmt.setNull((String) parameter, sqlType);
      }
   }

   public void setNull(Object parameter, int sqlType, String typeName)
         throws Throwable
   {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setNull(((Number) parameter).intValue(), sqlType, typeName);
      }
      else if(parameter instanceof String) {
         cstmt.setNull((String) parameter, sqlType, typeName);
      }
   }

   public void setObject(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setObject(((Number) parameter).intValue(),
            JavaScriptEngine.unwrap(x));
      }
      else if(parameter instanceof String) {
         cstmt.setObject((String) parameter, JavaScriptEngine.unwrap(x));
      }
   }

   public void setObject(Object parameter, Object x, int targetSqlType)
         throws Throwable
   { 
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setObject(((Number) parameter).intValue(),
            JavaScriptEngine.unwrap(x), targetSqlType);
      }
      else if(parameter instanceof String) {
         cstmt.setObject((String) parameter, JavaScriptEngine.unwrap(x),
            targetSqlType);
      }
   }

   public void setObject(Object parameter, Object x, int targetSqlType,
                         int scale) throws Throwable
   {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setObject(((Number) parameter).intValue(),
            JavaScriptEngine.unwrap(x), targetSqlType, scale);
      }
      else if(parameter instanceof String) {
         cstmt.setObject((String) parameter, JavaScriptEngine.unwrap(x),
            targetSqlType, scale);
      }
   }

   public void setShort(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         String val = numberToString(x);
         cstmt.setShort(((Number) parameter).intValue(),
            Short.parseShort(val));
      }
      else if(parameter instanceof String) {
         String val = numberToString(x);
         cstmt.setShort((String) parameter, Short.parseShort(val));
      }
   }

   public void setString(Object parameter, String x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setString(((Number) parameter).intValue(), x);
      }
      else if(parameter instanceof String) {
         cstmt.setString((String) parameter, x);
      }
   }

   public void setTime(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setTime(((Number) parameter).intValue(),
            new Time(getDateFromObj(x).getTime()));
      }
      else if(parameter instanceof String) {
         cstmt.setTime((String) parameter,
            new Time(getDateFromObj(x).getTime()));
      }
   }

   public void setTimestamp(Object parameter, Object x) throws Throwable {
      parameter = JavaScriptEngine.unwrap(parameter);

      if(parameter instanceof Number) {
         cstmt.setTimestamp(((Number) parameter).intValue(),
            new Timestamp(getDateFromObj(x).getTime()));
      }
      else if(parameter instanceof String) {
         cstmt.setTimestamp((String) parameter,
            new Timestamp(getDateFromObj(x).getTime()));
      }
   }

   public boolean wasNull() throws Throwable {
      return cstmt.wasNull();
   }

   private String numberToString(Object x) throws Exception {
      if(!JavaScriptEngine.isNumber(x)) {
         throw new RuntimeException("Invalid number object found: " + x);
      }

      return JavaScriptEngine.numberToString(x);
   }

   private Date getDateFromObj(Object x) throws Exception {
      if(!JavaScriptEngine.isDate(x)) {
         throw new RuntimeException("Invalid date found: " + x);
      }

      return new Date(JavaScriptEngine.getDate(x).getTime());
   }

   private String sql;
   private Connection conn;
   private JDBCDataSource jdbcSrc;
   private CallableStatement cstmt;
   private static final Logger LOG =
      LoggerFactory.getLogger(CallableStatementScriptable.class);
}
