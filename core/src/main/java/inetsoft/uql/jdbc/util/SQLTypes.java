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
package inetsoft.uql.jdbc.util;

import inetsoft.report.XSessionManager;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.*;
import inetsoft.uql.table.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.sql.*;
import java.util.List;
import java.util.*;

/**
 * This class handles JDBC type to Java type conversion and related methods.
 *
 * @author InetSoft Technology Corp
 * @since 5.1
 */
public class SQLTypes {
   /**
    * Factory method used to obtain the SQLTypes implementation for the specified
    * datasource.
    *
    * @param dx the datasource to get the SQLTypes object for.
    *
    * @return a SQLTypes object that is compatible with the specified datasource.
    *
    * @since 6.5
    */
   public static SQLTypes getSQLTypes(JDBCDataSource dx) {
      if(typemap == null) {
         synchronized(SQLTypes.class) {
            if(typemap == null) {
               typemap = new HashMap<>();

               try {
                  InputStream istream = SQLTypes.class.
                     getResourceAsStream("/inetsoft/uql/sqltypes.xml");
                  Document doc = Tool.parseXML(istream);
                  istream.close();

                  NodeList nlist = doc.getElementsByTagName("config");

                  if(nlist != null && nlist.getLength() > 0) {
                     Element node = (Element) nlist.item(0);
                     nlist = Tool.getChildNodesByTagName(node, "types");

                     for(int i = 0; nlist != null &&
                            i < nlist.getLength(); i++) {
                        node = (Element) nlist.item(i);
                        String type = node.getAttribute("type");
                        String cls = node.getAttribute("class");
                        SQLTypes types =
                           (SQLTypes) Class.forName(cls).newInstance();
                        typemap.put(type.toLowerCase(), types);
                     }
                  }
               }
               catch(Exception exc) {
                  LOG.error("Failed to load SQL types configuration", exc);
               }
            }
         }
      }

      SQLTypes types = null;

      if(dx != null) {
         String type = dx.getDatabaseTypeString();

         if(JDBCDataSource.ODBC.equals(type) && "org.h2.Driver".equals(dx.getDriver())) {
            type = "h2";
         }
         else if(JDBCDataSource.ODBC.equals(type) &&
            "mongodb.jdbc.MongoDriver".equals(dx.getDriver()))
         {
            type = "mongo";
         }
         else if(JDBCDataSource.ODBC.equals(type) &&
            "org.sqlite.JDBC".equals(dx.getDriver()))
         {
            type = "sqlite";
         }

         types = typemap.get(type.toLowerCase());
      }

      if(types == null) {
         types = defaultTypes;
      }

      if(dx != null) {
         String[] trans = getTranslateCharsets(dx.getName());

         if(trans != null) {
            types = types.shallowClone();
            types.translations = trans;
         }
      }

      return types;
   }

   /**
    * Creates a new instance of SQLTypes.
    *
    * @since 6.5
    */
   protected SQLTypes() {
      String prop = SreeEnv.getProperty("jdbc.blob.type");
      blobAsText = prop != null && prop.equals("text");
   }

   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   public XTableColumnCreator getXTableColumnCreator(int type, String tname) {
      switch(type) {
      case Types.TINYINT:
         return XShortColumn.getCreator();
      case Types.INTEGER:
      case Types.SMALLINT:
         return XIntegerColumn.getCreator();
      case Types.BIGINT:
         return XLongColumn.getCreator();
      case Types.FLOAT:
      case Types.DOUBLE:
         return XDoubleColumn.getCreator();
      case Types.REAL:
         return XFloatColumn.getCreator();
      case Types.NUMERIC:
      case Types.DECIMAL:
         return XBDDoubleColumn.isRecommended() ?
            XBDDoubleColumn.getCreator() : XObjectColumn.getCreator();
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.NVARCHAR:
         return XStringColumn.getCreator();
      case Types.BOOLEAN:
      case Types.BIT:
         return XBooleanColumn.getCreator();
      case Types.LONGVARCHAR:
      case Types.LONGVARBINARY:
      case Types.BLOB:
      case Types.CLOB:
      case Types.ARRAY:
         return XBigObjectColumn.getCreator();
      case Types.DATE:
         return XDateColumn.getCreator();
      case Types.TIME:
         return XTimeColumn.getCreator();
      case Types.TIMESTAMP:
         return XTimestampColumn.getCreator();
      default:
         return XObjectColumn.getCreator();
      }
   }

   /**
    * Removes all white space characters from the right side of a string.
    *
    * @param input the string to trim.
    *
    * @return the trimmed string.
    */
   public String rtrim(String input) {
      String result = input;

      if(result != null) {
         int length = result.length();
         int olength = length;

         // this is the same logic used by String.trim()
         while(length > 0 && result.charAt(length - 1) <= ' ') {
            --length;
         }

         if(length < olength) {
            result = result.substring(0, length);
         }
      }

      return result;
   }

   /**
    * Get an object from a resultset.
    */
   public Object getObject(ResultSet result, int idx, int type)
      throws Exception
   {
      try {
         switch(type) {
         case Types.CHAR:
            return convert(result.getString(idx), translations);

         case Types.VARCHAR:
         case Types.OTHER:
         case Types.NVARCHAR:
         case Types.SQLXML:
         case -10:
            return convert(result.getString(idx), translations);

         case Types.LONGVARCHAR:
         case Types.CLOB:
         case Types.NCLOB:
            Reader instream = null;

            try {
               instream = result.getCharacterStream(idx);

               // mongo driver requires byte[] for longvarchar, else will be treated as null
               if(Types.LONGVARCHAR == type && instream == null) {
                  return result.getString(idx);
               }
            }
            catch(Throwable e) {
               InputStream binput = result.getBinaryStream(idx);

               if(binput != null) {
                  instream = new InputStreamReader(binput);
               }
            }

            if(instream == null) {
               return "";
            }

            return getText(instream);

         case Types.BLOB:
         case Types.BINARY:
         case Types.VARBINARY:
         case Types.LONGVARBINARY:
            InputStream instreamBlob = result.getBinaryStream(idx);

            if(instreamBlob != null) {
               if(blobAsText) {
                  return getText(instreamBlob);
               }

               return getImage(instreamBlob);
            }
            else {
               return null;
            }

         case Types.TINYINT:
         case Types.SMALLINT:
         case Types.INTEGER:
            /* use the getObject() should be more efficient
            int int_val = result.getInt(idx);
            return int_val != 0 ?
               Integer.valueOf(int_val) :
               (result.wasNull() ? null : Integer.valueOf(0));
            */
            break;

         case Types.FLOAT:
         case Types.REAL:
         case Types.DOUBLE:
         case Types.NUMERIC:
         case Types.DECIMAL:
            // @by larryl 2003-9-23, use getObject() so if BigDecimal is return
            // it would have better accuracy (oracle)
            //return Double.valueOf(result.getDouble(idx));
            break;

         case Types.BIT:
         case Types.BIGINT:
            break;
         case Types.DATE:
            return result.getDate(idx);
         case Types.TIME:
            return result.getTime(idx);
         case Types.TIMESTAMP:
            return result.getTimestamp(idx);
         default:
         }
      }
      catch(IOException e) {
         LOG.error("An I/O error prevented the value of column [" +
            idx + "] from being retrieved", e);

         // pass on exception for image so it's detected in JDBCTableNode
         switch(type) {
         case Types.BLOB:
         case Types.BINARY:
         case Types.VARBINARY:
         case Types.LONGVARBINARY:
            throw new RuntimeException(e);
         }
      }

      return result.getObject(idx);
   }

   /**
    * Get an object from a CallableStatement.
    */
   public Object getObject(CallableStatement cs, int idx, int type)
      throws Exception
   {
      switch(type) {
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.OTHER:
      case Types.SQLXML:
      case Types.LONGVARCHAR:
         return convert(cs.getString(idx), translations);
      case Types.CLOB:
         Reader reader;

         try {
            reader = cs.getClob(idx).getCharacterStream();
            return getText(reader);
         }
         catch(Throwable e) {
            return "";
         }
      case Types.BLOB:
      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
         InputStream instream = new ByteArrayInputStream(cs.getBytes(idx));

         if(blobAsText) {
            return getText(instream);
         }

         return getImage(instream);
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
      case Types.FLOAT:
      case Types.REAL:
      case Types.DOUBLE:
      case Types.NUMERIC:
      case Types.DECIMAL:
      case Types.BIT:
      case Types.BIGINT:
         break;
      case Types.DATE:
         return cs.getDate(idx);
      case Types.TIME:
         return cs.getTime(idx);
      case Types.TIMESTAMP:
         return cs.getTimestamp(idx);
      }

      return cs.getObject(idx);
   }

   /**
    * Convert a SQL type to a XSchema type.
    */
   public String convertToXType(int sqltype) {
      switch(sqltype) {
      case Types.BIT:
      case Types.BOOLEAN:
         return XSchema.BOOLEAN;
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
         return XSchema.INTEGER;
      case Types.BIGINT:
         return XSchema.LONG;
      case Types.FLOAT:
         return XSchema.FLOAT;
      case Types.REAL:
      case Types.DOUBLE:
      case Types.NUMERIC:
      case Types.DECIMAL:
         return XSchema.DOUBLE;
      case Types.CHAR:
      case Types.NCHAR:
      case Types.VARCHAR:
      case Types.NVARCHAR:
      case Types.LONGVARCHAR:
      case Types.CLOB:
      case Types.SQLXML:
      case Types.OTHER:
      case Types.BLOB:
      case Types.LONGVARBINARY:
      case Types.BINARY:
         return XSchema.STRING;
      case Types.DATE:
         return XSchema.DATE;
      case Types.TIMESTAMP:
         return XSchema.TIME_INSTANT;
      case Types.TIME:
         return XSchema.TIME;
      case Types.VARBINARY:

      default:
         break;
      }

      return null;
   }

   /**
    * Fix sql type according to type name.
    */
   public int fixSQLType(int sqltype, String tname) {
      switch(sqltype) {
      case Types.BIT:
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
      case Types.BIGINT:
      case Types.FLOAT:
      case Types.REAL:
      case Types.DOUBLE:
      case Types.NUMERIC:
      case Types.DECIMAL:
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.DATE:
      case Types.TIMESTAMP:
      case Types.TIME:
      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
         return sqltype;
      }

      if(tname == null) {
         return sqltype;
      }
      else if(tname.equalsIgnoreCase("nvarchar")) {
         return Types.VARCHAR;
      }
      else if(tname.equalsIgnoreCase("nchar")) {
         return Types.CHAR;
      }
      else if(tname.equalsIgnoreCase("nclob")) {
         return Types.NCLOB;
      }
      else {
         return sqltype;
      }
   }

   /**
    * Create a XTypeNode for a SQL type.
    */
   public XTypeNode createTypeNode(String name, int sqltype, String tname) {
      sqltype = fixSQLType(sqltype, tname);
      XTypeNode node;

      switch(sqltype) {
      case Types.BIT:
      case Types.BOOLEAN:
         node = new BooleanType(name);
         break;
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
         node = new IntegerType(name);
         break;
      case Types.BIGINT:
         node = new LongType(name);
         break;
      case Types.FLOAT:
         node = new FloatType(name);
         break;
      case Types.REAL:
      case Types.DOUBLE:
      case Types.NUMERIC:
      case Types.DECIMAL:
         node = new DoubleType(name);
         break;
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.NVARCHAR:
      case Types.LONGVARCHAR:
         node = new StringType(name);
         break;
      case Types.DATE:
         node = new DateType(name);
         break;
      case Types.TIMESTAMP:
         node = new TimeInstantType(name);
         break;
      case Types.TIME:
         node = new TimeType(name);
         break;
      case Types.BINARY:
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
      default:
         node = new StringType(name);
         break;
      }

      node.setAttribute("sqltype", sqltype);
      return node;
   }

   /**
    * Convert a SQL type to a Java type (class).
    */
   public Class<?> convertToJava(int sqltype) {
      switch(sqltype) {
      case Types.BIT:
         return Boolean.class;
      case Types.TINYINT:
         return Byte.class;
      case Types.SMALLINT:
         return Short.class;
      case Types.INTEGER:
         return Integer.class;
      case Types.BIGINT:
         return Long.class;
      case Types.FLOAT:
      case Types.DOUBLE:
         return Double.class;
      case Types.REAL:
         return Float.class;
      case Types.NUMERIC:
      case Types.DECIMAL:
         return java.math.BigDecimal.class;
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.NVARCHAR:
      case Types.LONGVARCHAR:
         return String.class;
      case Types.DATE:
         return java.sql.Date.class;
      case Types.TIME:
         return java.sql.Time.class;
      case Types.TIMESTAMP:
         return java.sql.Timestamp.class;
      case Types.VARBINARY:
      case Types.LONGVARBINARY:
         return byte[].class;
      default:
         return String.class;
      }
   }

   /**
    * Get the qualified column name from the physical table column.
    */
   public String getQualifiedColumnName(XNode table, String columnName, JDBCDataSource xds) {
      String fixquote = (String) table.getAttribute("fixquote");
      SQLHelper helper = SQLHelper.getSQLHelper(xds);

      if(columnName != null && columnName.indexOf('.') > -1 && !("false".equals(fixquote))) {
         columnName = XUtil.quoteAlias(columnName, helper);
      }

      return columnName;
   }

   /**
    * Get the fully qualified table name from the table node.
    */
   public String getQualifiedName(XNode table, JDBCDataSource xds) {
      String tblname = table.getName();
      String schema = (String) table.getAttribute("schema");
      String catalog = (String) table.getAttribute("catalog");
      String pkg = (String) table.getAttribute("package");
      String catSep = (String) table.getAttribute("catalogSep");
      catSep = catSep == null ? "." : catSep;
      String additional = (String) table.getAttribute("additional");

      boolean hasPackage = pkg != null && pkg.length() > 0 &&
         !tblname.startsWith(pkg + ".");

      // @by larryl, handle dot in table name
      String fixquote = (String) table.getAttribute("fixquote");
      SQLHelper helper = SQLHelper.getSQLHelper(xds);

      if(catalog != null && catalog.indexOf('.') >= 0 && !("false".equals(fixquote))) {
         catalog = XUtil.quoteAlias(catalog, helper);
      }

      if(schema != null && schema.indexOf('.') >= 0 && !("false".equals(fixquote))) {
         schema = XUtil.quoteAlias(schema, helper);
      }

      if(tblname.indexOf('.') >= 0 && !("false".equals(fixquote))) {
         tblname = XUtil.quoteAlias(tblname, helper);
      }

      if(hasPackage) {
         tblname = pkg + "." + tblname;
      }

      StringBuilder buffer = new StringBuilder();
      boolean hasSchema = getTableNameWithSchema(schema, tblname, xds, buffer);
      tblname = buffer.toString();
      int toption = xds == null ? JDBCDataSource.DEFAULT_OPTION :
         xds.getTableNameOption();

      if(catalog == null) {
         try {
            XNode root = getChildMetaData(xds, false, additional);
            catalog = getDefaultCatalog(root);
         }
         catch(Exception ignore) {
         }
      }

      if(hasSchema && catalog != null && catalog.length() > 0 &&
         (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
          toption == JDBCDataSource.DEFAULT_OPTION))
      {
         tblname = catalog + catSep + tblname;
      }

      return tblname;
   }

   public static XNode getChildMetaData(JDBCDataSource dataSource) {
      return getChildMetaData(dataSource, false);
   }

   public static XNode getChildMetaData(JDBCDataSource dataSource, boolean changeTableOption) {
      return getChildMetaData(dataSource, changeTableOption, null);
   }

   public static XNode getChildMetaData(JDBCDataSource dataSource, boolean changeTableOption,
                                        String additional)
   {
      try {
         XRepository repository = XFactory.getRepository();
         Object session = System.getProperty("user.name");
         XSessionManager.getSessionManager().bind(session);

         XNode query = new XNode();
         query.setAttribute("type", "DBPROPERTIES");
         query.setAttribute("additional", additional);

         if(changeTableOption) {
            query.setAttribute("cache", "false");
         }

         // get child meta-data through repository so that cache is used/updated
         return repository.getMetaData(session, dataSource, query, true, null);
      } catch (Exception e) {
         LOG.error("Failed to get root meta-data for data source: " + dataSource.getName(), e);
      }

      return null;
   }

   public static String getDefaultSchema(XNode root) {
      return (String) root.getAttribute("defaultSchema");
   }

   public static String getDefaultCatalog(XNode root) {
      return (String) root.getAttribute("defaultCatalog");
   }

   /**
    * Strategy pattern method that allows subclasses to change how and when
    * the table name is prefixed with the schema name.
    *
    * @param schema the name of the schema.
    * @param table the name of the table.
    * @param xds the datasource containing the table.
    * @param buffer the buffer to write the qualified table name to.
    *
    * @return <code>true</code> if the schema is used in the table name.
    *
    * @since 6.5
    */
   protected boolean getTableNameWithSchema(String schema, String table,
                                            JDBCDataSource xds,
                                            StringBuilder buffer) {
      boolean hasSchema = false;
      int toption = xds == null ? JDBCDataSource.DEFAULT_OPTION :
         xds.getTableNameOption();

      // @by vincentx, 2004-08-19, fix bug1092283862138
      // include schema if the schema is different from login.
      // @by charvi
      // @fixed bug1111079175980
      // Prepend the schema name to the table name only if the
      // schema name is different from the user name.  This is to
      // ensure that the physical view will not be lost if the
      // datasource connection information is modified to point
      // to a different but identical database.  In addition,
      // use the String's equals method to do the comparison
      // instead of "==".
      if(schema != null && schema.length() > 0) {
         switch(toption) {
         case JDBCDataSource.DEFAULT_OPTION:
            hasSchema = xds == null || !Tool.equals(xds.getUser(), schema, false);
            break;
         case JDBCDataSource.CATALOG_SCHEMA_OPTION:
         case JDBCDataSource.SCHEMA_OPTION:
            hasSchema = true;
            break;
         case JDBCDataSource.TABLE_OPTION:
            hasSchema = false;
            break;
         default:
            throw new RuntimeException("Unsupported option found: " + toption);
         }
      }

      if(hasSchema) {
         buffer.append(schema);
         buffer.append('.');
         buffer.append(table);
      }
      else {
         buffer.append(table);
      }

      return hasSchema;
   }

   /**
    * Generate a table node from a node with a full qualified name.
    * @param tablename a full qualified table name.
    * @param hasCatalog true if catalog may be part of the table name.
    * @param hasSchema true if schema may be part of the table name.
    * @param catalogSeparator catalog separator in the qualified table name.
    */
   public XNode getQualifiedTableNode(String tablename, boolean hasCatalog,
                                      boolean hasSchema,
                                      String catalogSeparator,
                                      JDBCDataSource xds, String catalog,
                                      String schema) {
      int toption = xds == null ? JDBCDataSource.DEFAULT_OPTION :
         xds.getTableNameOption();
      XNode table = getXNodeForTable(tablename, hasCatalog, hasSchema, catalog,
                                     schema, toption, XUtil.getQuote(xds));
      setXNodeAttributes(table, tablename, hasCatalog, hasSchema,
                         catalogSeparator, catalog, schema, toption, XUtil.getQuote(xds));
      return table;
   }

   /**
    * Strategy pattern method that allows subclasses to change the name used for
    * the XNode that represents the specified table.
    * @param table the qualified name of the table.
    * @param hasCatalog true if catalog may be part of the table name.
    * @param hasSchema true if schema may be part of the table name.
    * @param toption the specified table name option.
    * @return an XNode with the proper table name.
    * @since 6.5
    */
   protected XNode getXNodeForTable(String table, boolean hasCatalog,
                                    boolean hasSchema, String catalog,
                                    String schema, int toption, String quoteString) {
      if(hasCatalog && (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
         toption == JDBCDataSource.DEFAULT_OPTION))
      {
         if(catalog != null && catalog.length() > 0) {
            String prefix = catalog;

            if(!table.startsWith(prefix)) {
               prefix = quoteString + catalog + quoteString;

               if(!table.startsWith(prefix)) {
                  prefix = null;
               }
            }

            if(prefix != null && !table.equals(catalog)) {
               table = table.substring(prefix.length() + 1);
            }
         }
         else {
            int dot = indexOfWithQuote(table, ".");

            if(dot > 0) {
               table = table.substring(dot + 1);
            }
         }
      }

      if(hasSchema && (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
         toption == JDBCDataSource.SCHEMA_OPTION ||
         toption == JDBCDataSource.DEFAULT_OPTION))
      {
         if(schema != null && schema.length() > 0) {
            String prefix = schema;

            // @by stephenwebster, Fix bug1402307536011
            // It is possible that the table name actually does start with
            // the schema name, but the tablename is not really pre-fixed.
            // Added "." as separator to make the check more accurate.
            // What literature I read, the schema and table
            // name is always separated by a "."
            if(!table.startsWith(schema + ".")) {
               prefix = '"' + schema + '"';

               if(!table.startsWith(prefix + ".")) {
                  prefix = null;
               }
            }

            if(prefix != null && !table.equals(schema)) {
               table = table.substring(prefix.length() + 1);
            }
         }
         else {
            int dot = indexOfWithQuote(table, ".");

            if(dot > 0) {
               table = table.substring(dot + 1);
            }
         }
      }

      // strip off the quote
      if(table.startsWith("\"") && table.endsWith("\"")) {
         table = table.substring(1, table.length() - 1);
      }

      return new XMetaDataNode(table);
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
    * @param toption the specified table name option.
    *
    * @since 6.5
    */
   protected void setXNodeAttributes(XNode node, String table,
                                     boolean hasCatalog, boolean hasSchema,
                                     String separator, String catalog,
                                     String schema, int toption, String quoteString) {
      if(separator == null) {
         separator = ".";
      }

      if(hasCatalog) {
         node.setAttribute("catalogSep", separator);

         if(catalog != null && catalog.length() > 0) {
            String prefix = catalog;

            if(!table.startsWith(prefix)) {
               prefix = quoteString + catalog + quoteString;

               if(!table.startsWith(prefix)) {
                  prefix = null;
               }
            }

            if(prefix != null && !table.equals(catalog)) {
               table = table.substring(prefix.length() + 1);
            }

            node.setAttribute("catalog", catalog);
         }
         else {
            int idx = (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
               toption == JDBCDataSource.DEFAULT_OPTION) ?
               indexOfWithQuote(table, separator) : -1;

            if(idx > 0) {
               catalog = table.substring(0, idx);

               if(catalog.length() > 2 && catalog.startsWith("\"") &&
                  catalog.endsWith("\""))
               {
                  catalog = catalog.substring(1, catalog.length() - 1);
               }

               node.setAttribute("catalog", catalog);
               table = table.substring(idx + separator.length());
            }
         }
      }

      if(hasSchema) {
         if(schema != null && schema.length() > 0) {
            String prefix = schema;

            if(!table.startsWith(schema)) {
               prefix = '"' + schema + '"';

               if(!table.startsWith(prefix)) {
                  prefix = null;
               }
            }

            if(prefix != null && !table.equals(schema)) {
               table = table.substring(prefix.length() + 1);
            }

            node.setAttribute("schema", schema);
         }
         else {
            int idx = (toption == JDBCDataSource.CATALOG_SCHEMA_OPTION ||
               toption == JDBCDataSource.SCHEMA_OPTION ||
               toption == JDBCDataSource.DEFAULT_OPTION) ?
               indexOfWithQuote(table, ".") : -1;

            if(idx > 0) {
               schema = table.substring(0, idx);

               if(schema.length() > 2 && schema.startsWith("\"") &&
                  schema.endsWith("\""))
               {
                  schema = schema.substring(1, schema.length() - 1);
               }

               node.setAttribute("schema", schema);
            }
         }
      }
   }

   /**
    * Find the delimiter in the string and respect quotes.
    */
   public static int indexOfWithQuote(String str, String delim) {
      return Tool.indexOfWithQuote(str, delim, (char) 0);
   }

   /**
    * Read an image from an input stream.
    */
   protected Object getImage(InputStream is) throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      List<Integer> vk = new ArrayList<>();
      int cnt;
      byte[] buf = new byte[4096];
      int[] key = new int[257];
      int pos = 0;

      while((cnt = is.read(buf)) >= 0) {
         for(int i = 0; i < cnt; i++) {
            int cir = key[pos] >>> 31;
            key[pos] = (key[pos] << 1) + cir;
            key[pos++] += buf[i];

            if(pos == (key.length - 1)) {
               pos = 0;
            }
         }

         key[key.length - 1] += cnt;

         baos.write(buf, 0, cnt);
      }

      for(int i = 0; i < key.length; i++) {
         vk.add(key[i]);
      }

      Image image = blobmap.get(vk);

      if(image != null) {
         return image;
      }

      byte[] image0 = null;

      try{
         image0 = baos.toByteArray();
         InputStream inp = new ByteArrayInputStream(image0);
         image = ImageIO.read(inp);

         if(image != null) {
            blobmap.put(vk, image);
            return image;
         }
      }
      catch(Exception ex) {
         // ChrisS bug1404941804680 2014-7-10
         // Instead of throwing an exception with the image as a data payload
         // (which then gets returned anyway), log the exception as a warning.
         // And let the image0 byte array get returned.
         LOG.warn(ex.getMessage(), ex);
      }
      finally {
         baos.close();
      }

      return image0;
   }

   /**
    * Read text from input stream.
    */
   protected String getText(InputStream inp) {
      // try to read as text
      try {
         return getText(new InputStreamReader(inp));
      }
      catch(Exception ex) {
         LOG.warn("Failed to read text from input", ex);
      }

      return null;
   }

   /**
    * Read text from input stream.
    */
   protected String getText(Reader instream) throws IOException {
      char[] charBuffer = new char[1024];
      StringBuilder clobStr = new StringBuilder();
      int cnt;

      while((cnt = instream.read(charBuffer)) >= 0) {
         clobStr.append(new String(charBuffer, 0, cnt));

         // limit the size to 100K
         if(clobStr.length() > 102400) {
            break;
         }
      }

      return (String) convert(clobStr.toString(), translations);
   }

   /**
    * Converts a string into the target character set.
    *
    * @param text the text to convert.
    *
    * @return the converted text.
    */
   protected String convert(String text) {
      return (String) convert(text, translations);
   }

   /**
    * Create image object cache.
    */
   public void clearBlobmap() {
      blobmap.clear();
   }

   /**
    * String the single or double quotes.
    */
   public String stripQuotes(Object value) {
      if(value == null) {
         return "";
      }

      // handle array object properly
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;

         if(arr.length > 0) {
            value = arr[0];
         }
      }

      // @by larryl, this handles float/double better (without always a period)
      String val0 = Tool.toString(value);

      if(val0.startsWith("'") && val0.endsWith("'")) {
         val0 = val0.substring(1, val0.length() - 1);
      }

      for(int i = 0; i < val0.length(); i++) {
         if(val0.charAt(i) == '\'') {
            val0 = val0.substring(0, i + 1) + "'" + val0.substring(i + 1);
            i++;
         }
      }

      return val0;
   }

   /**
    * Closes a database connection. This method should ensure that the physical
    * connection is closed, if supported by the driver.
    *
    * @param conn the connection to close.
    */
   public void closeConnection(Connection conn) {
      try {
         conn.close();
      }
      catch(Throwable exc) {
         LOG.warn("Failed to close JDBC connection", exc);
      }
   }

   /**
    * Get charsets translation for the specified datasource.
    */
   public static String[] getTranslateCharsets(String datasource) {
      String prop = SreeEnv.getProperty(datasource + ".translate.charsets");
      String[] trans = new String[2];

      if(prop != null) {
         String[] sets = prop.split(";");

         if(sets[0].trim().length() > 0) {
            trans[0] = sets[0].trim();
         }

         if(sets.length > 1 && sets[1].trim().length() > 0) {
            trans[1] = sets[1].trim();
         }
      }

      return trans[0] == null ? null : trans;
   }

   /**
    * Translate string from one chartset to another chartset.
    */
   public static Object convert(Object obj, String[] charsets) {
      if(obj == null || charsets == null || charsets.length == 0) {
         return obj;
      }

      if(obj instanceof Object[]) {
         Object[] arr = (Object[]) obj;

         for(int i = 0; i < arr.length; i++) {
            arr[i] = convert(arr[i], charsets);
         }

         return arr;
      }
      else if(obj instanceof String) {
         String str = (String) obj;
         byte[] bytes;

         try {
            if(charsets[0] == null) {
               bytes = str.getBytes();
            }
            else {
               bytes = str.getBytes(charsets[0]);
            }

            if(charsets[1] == null) {
               return new String(bytes);
            }
            else {
               return new String(bytes, charsets[1]);
            }
         }
         catch(Throwable ex) {
            LOG.warn("Failed to convert string charset", ex);
         }
      }

      return obj;
   }

   /**
    * Shallow clone.
    */
   private SQLTypes shallowClone() {
      try {
         // do not clone blobmap
         return (SQLTypes) clone();
      }
      catch(Throwable ex) {
         // ignore it
      }

      return this;
   }

   private Map<List<Integer>, Image> blobmap = new Hashtable<>();
   protected boolean blobAsText = false;
   private String[] translations;
   private static Map<String, SQLTypes> typemap = null;
   private static SQLTypes defaultTypes = new SQLTypes();
   private static final Logger LOG = LoggerFactory.getLogger(SQLTypes.class);
}
