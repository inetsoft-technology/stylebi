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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.XNode;
import inetsoft.uql.schema.UserDefinedType;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.XTableColumnCreator;
import inetsoft.uql.table.XTimestampColumn;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;

/**
 * SQLTypes specialization that handles Oracle databases.
 *
 * @author  InetSoft Technology
 * @since   6.5
 */
public final class OracleSQLTypes extends SQLTypes {
   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getXTableColumnCreator(int type, String tname) {
      switch(type) {
      case Types.DATE:
      case -101: // TIMESTAMPTZ
      case -102: // TIMESTAMPLTZ
         return XTimestampColumn.getCreator();
      }

      return super.getXTableColumnCreator(type, tname);
   }

   /**
    * Get an object from a resultset.
    */
   @Override
   public Object getObject(ResultSet result, int idx, int type)
         throws Exception
   {
      Object val = null;

      try {
         switch(type) {
         case Types.DATE:
            return result.getTimestamp(idx);
         case Types.TIMESTAMP: // check for sql TIMESTAMP
         case -101: // oracle TIMESTAMPTZ
         case -102: // oracle TIMESTAMPLTZ
            val = result.getObject(idx);

            if(val == null || (val instanceof java.util.Date)) {
               return val;
            }

            if(val.getClass().getName().equals("oracle.sql.TIMESTAMP") ||
               val.getClass().getName().equals("oracle.sql.DATE"))
            {
               val = XUtil.call(val, "oracle.sql.Datum", "timestampValue",
                                new Class[] {}, new Object[] {});
            }
            else if(val.getClass().getName().equals("oracle.sql.TIMESTAMPTZ") ||
                    val.getClass().getName().equals("oracle.sql.TIMESTAMPLTZ"))
            {
               Statement stmt = result.getStatement();

               if(stmt instanceof Wrapper) {
                  stmt = stmt.unwrap(Statement.class);
               }

               String cls = val.getClass().getName();
               byte[] buf = (byte[]) XUtil.call(val, cls, "toBytes",
                                                new Class[]{}, new Object[]{});

               val = XUtil.call(val, cls, "toTimestamp",
                                new Class[] { Connection.class, byte[].class },
                                new Object[] { stmt.getConnection(), buf });

               // @by larryl, for some unknown reason, the first call to
               // toTimestamp always returns null, all subsequent calls are
               // fine. There is likely some initialization involved but
               // could not find any api or documentation for it
               if(val == null) {
                  val = XUtil.call(val, cls, "toTimestamp",
                                   new Class[] { Connection.class, byte[].class },
                                   new Object[] { stmt.getConnection(), buf });
               }
            }

            return val;
         case -104: // oracle INTERVALDAYTOSECOND
            try {
               val = XUtil.call(result, "oracle.sql.OracleResultSet",
                                "getINTERVALDS", new Class[] {int.class},
                                new Object[] {Integer.valueOf(idx)});
               return val;
            }
            catch(Throwable ex) {
               LOG.error("Failed to get Oracle " +
                  "INTERVALDAYTOSECOND value from column " + idx, ex);
            }
         // for clobs and blobs, deadlocks can occur in the oracle driver.
         // this is a workaround that prevents the deadlock.
         case Types.LONGVARCHAR:
         case Types.CLOB:
            synchronized(result.getStatement().getConnection()) {
               Reader instream = null;

               try {
                  instream = result.getCharacterStream(idx);
               }
               catch(Throwable e) {
                  LOG.debug("Failed to read CLOB", e);

                  InputStream binput = result.getBinaryStream(idx);

                  if(binput != null) {
                     instream = new InputStreamReader(binput);
                  }
               }

               if(instream == null) {
                  val = "";
               }
               else {
                  val = getText(instream);
               }
            }

            return val;

         case Types.BLOB:
         case Types.BINARY:
         case Types.VARBINARY:
         case Types.LONGVARBINARY:
            synchronized(result.getStatement().getConnection()) {
               InputStream instreamBlob = result.getBinaryStream(idx);

               if(instreamBlob != null) {
                  if(blobAsText) {
                     val = getText(instreamBlob);
                  }
                  else {
                     val = getImage(instreamBlob);
                  }
               }
               else {
                  val = null;
               }
            }

            return val;
         }
      }
      catch(SQLException sqle) {
         throw sqle;
      }
      catch(Exception exc) {
         LOG.error("Failed to get column value [" + idx + "]", exc);
      }

      return super.getObject(result, idx, type);
   }

   /**
    * Fix sql type according to type name.
    */
   @Override
   public int fixSQLType(int sqltype, String tname) {
      if(tname != null && tname.toUpperCase().startsWith("TIMESTAMP")) {
         return Types.TIMESTAMP;
      }

      return super.fixSQLType(sqltype, tname);
   }

   /**
    * Get an object from a CallableStatement.
    */
   @Override
   public Object getObject(CallableStatement cs, int idx, int type)
      throws Exception
   {
      Object val = null;

      try {
         switch(type) {
         case Types.DATE:
            return cs.getTimestamp(idx);
         case Types.TIMESTAMP:
         case -101:
         case -102:
            val = cs.getObject(idx);

            if(val == null || (val instanceof java.util.Date)) {
               return val;
            }

            if(val.getClass().getName().equals("oracle.sql.TIMESTAMP") ||
               val.getClass().getName().equals("oracle.sql.DATE")) {
               val = XUtil.call(val, "oracle.sql.Datum", "timestampValue",
                                new Class[] {}, new Object[] {});
            }
            else if(val.getClass().getName().equals("oracle.sql.TIMESTAMPTZ")) {
               byte[] buf = (byte[]) XUtil.call(val, "oracle.sql.TIMESTAMPTZ",
                                                "toBytes", new Class[] {},
                                                new Object[] {});

               val = XUtil.call(val, "oracle.sql.TIMESTAMPTZ", "toTimestamp",
                  new Class[] {Connection.class, byte[].class},
                  new Object[] {cs.getConnection(), buf});

               // @by larryl, for some unknown reason, the first call to
               // toTimestamp always returns null, all subsequent calls are
               // fine. There is likely some initialization involved but
               // could not find any api or documentation for it
               if(val == null) {
                  val = XUtil.call(val, "oracle.sql.TIMESTAMPTZ", "toTimestamp",
                     new Class[] {Connection.class, byte[].class},
                     new Object[] {cs.getConnection(), buf});
               }
            }
            else if(val.getClass().getName().equals("oracle.sql.TIMESTAMPTZ")) {
               byte[] buf = (byte[]) XUtil.call(val, "oracle.sql.TIMESTAMPTZ",
                  "toBytes", new Class[] {}, new Object[] {});

               val = XUtil.call(val, "oracle.sql.TIMESTAMPTZ", "toTimestamp",
                  new Class[] {Connection.class, byte[].class},
                  new Object[] {cs.getConnection(), buf});

               if(val == null) {
                  val = XUtil.call(val, "oracle.sql.TIMESTAMPTZ", "toTimestamp",
                     new Class[] {Connection.class, byte[].class},
                     new Object[] {cs.getConnection(), buf});
               }
            }

            return val;
         case Types.CLOB:
            synchronized(cs.getConnection()) {
               Reader instream = null;

               try {
                  instream = cs.getClob(idx).getCharacterStream();
                  val = getText(instream);
               }
               catch(Throwable e) {
                  val = "";
               }
            }

            return val;

         case Types.BLOB:
         case Types.BINARY:
         case Types.VARBINARY:
         case Types.LONGVARBINARY:
            synchronized(cs.getConnection()) {
               Blob blob = cs.getBlob(idx);
               InputStream instreamBlob = blob.getBinaryStream();

               if(instreamBlob != null) {
                  if(blobAsText) {
                     val = getText(instreamBlob);
                  }
                  else {
                     val = getImage(instreamBlob);
                  }
               }
               else {
                  val = null;
               }
            }

            return val;
         }
      }
      catch(SQLException sqle) {
         throw sqle;
      }
      catch(Exception exc) {
         LOG.error("Failed to get callable statement parameter " +
            "value [" + idx + "]", exc);
      }

      return super.getObject(cs, idx, type);
   }

   /**
    * Create a XTypeNode for a SQL type.
    */
   @Override
   public XTypeNode createTypeNode(String name, int sqltype, String tname) {
      sqltype = fixSQLType(sqltype, tname);

      if(sqltype == Types.OTHER) {
         return new UserDefinedType(name);
      }

      return super.createTypeNode(name, sqltype, tname);
   }

   /**
    * Strategy pattern method that allows subclasses to set the attributes of
    * the XNode that represents a table.
    *
    * @param node the XNode object to modify.
    * @param table the fully qualified name of the database table.
    * @param hasCatalog true if catalog may be part of the table name.
    * @param hasSchema true if schema may be part of the table name.
    * @param separator the delimiter used to separate the components of the
    *                  table name.
    *
    * @since 6.5
    */
   protected void setXNodeAttributes(XNode node, String table,
                                     boolean hasCatalog, boolean hasSchema,
                                     String separator, String catalog,
                                     String schema) {
      if(hasCatalog) {
	 if(separator == null) {
	    separator = ".";
	 }

         node.setAttribute("catalogSep", separator);

         if(catalog != null && table.startsWith(catalog + separator)) {
            node.setAttribute("catalog", catalog);
            table = table.substring(catalog.length() + separator.length());
         }
         else {
            int idx = indexOfWithQuote(table, separator);

            if(idx > 0) {
               node.setAttribute("catalog", table.substring(0, idx));
               table = table.substring(idx + separator.length());
            }
         }
      }

      if(hasSchema) {
         if(schema != null && table.startsWith(schema + ".")) {
            node.setAttribute("schema", schema);
            table = table.substring(schema.length() + 1);
         }
         else {
            int idx = indexOfWithQuote(table, ".");

            if(idx > 0) {
               schema = table.substring(0, idx);
               node.setAttribute("schema", schema);
               table = table.substring(idx + 1);
            }
         }
      }

      int idx = indexOfWithQuote(table, ".");

      if(idx > 0) {
         String pkg = table.substring(0, idx);
         node.setAttribute("package", pkg);
         node.setAttribute("procedure", table.substring(idx + 1));
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void closeConnection(Connection conn) {
      try {
         Class<?> iface = Class.forName("oracle.jdbc.OracleConnection");

         if(iface.isAssignableFrom(conn.getClass())) {
            Method method = iface.getMethod("close", int.class);
            Field field = iface.getField("INVALID_CONNECTION");
            Object value = field.get(null);
            method.invoke(conn, value);
         }
         else {
            super.closeConnection(conn);
         }
      }
      catch(Throwable exc) {
         super.closeConnection(conn);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(OracleSQLTypes.class);
}
