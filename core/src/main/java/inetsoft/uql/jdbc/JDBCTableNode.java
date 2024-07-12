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
package inetsoft.uql.jdbc;

import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.table.XTableColumnCreator;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class converts a JDBC resultset to a table node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCTableNode extends XTableNode {
   /**
    * Create a table node from a JDBC resultset.
    */
   @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
   public JDBCTableNode(ResultSet result, Connection conn, Statement stmt,
                        XSelection xselect, XDataSource xds)
      throws SQLException
   {
      this(result, conn, stmt, xselect, xds, null);
   }

   /**
    * Create a table node from a JDBC resultset.
    */
   @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
   public JDBCTableNode(ResultSet result, Connection conn, Statement stmt,
                        XSelection xselect, XDataSource xds, int[] columnMap)
      throws SQLException
   {
      setName("table");
      this.conn = conn;
      this.result = result;
      this.stmt = stmt;
      this.xds = xds;
      this.sqlTypesHelper = SQLTypes.getSQLTypes((JDBCDataSource) xds);
      ResultSetMetaData meta = null;
      rewindable = false;

      try {
         meta = result.getMetaData();
      }
      catch(Exception ex) {
         // statement may be closed if the query is cancelled
         LOG.debug("Failed to get result metadata", ex);
      }

      // may be unsupported
      try {
         int type = (Integer) XUtil.call(result, "java.sql.ResultSet", "getType", null, null);
         Integer forward = (Integer) XUtil.field(ResultSet.class, "TYPE_FORWARD_ONLY");
         rewindable = forward != null && type != forward;
      }
      catch(Throwable ignore) {
      }

      int max = stmt.getMaxRows();
      this.max = max > 0 ? max - 1 : max;

      ncol = meta == null ? 0 : meta.getColumnCount();
      types = new Class[ncol];
      creators = new XTableColumnCreator[ncol];
      sqltypes = new int[ncol];
      sizes = new int[ncol];
      minfos = new XMetaInfo[ncol];
      row = new Object[ncol];

      synchronized(xselect) {
         names = SQLSelection.getColumnNames(xselect, meta, ((JDBCDataSource) xds).getDriver(),
                                             conn, columnMap);
         boolean selectOK = ncol == xselect.getColumnCount();

         for(int i = 0; i < ncol; i++) {
            try {
               sizes[i] = meta.getColumnDisplaySize(i + 1);
            }
            // mysql execute a query in getColumnDisplaySize, which would
            // fail in stream mode because only one resultset is allowed
            // to be open when streaming
            catch(Exception ignore) {
               sizes[i] = 100;
            }

            sqltypes[i] = meta.getColumnType(i + 1);
            String tname = null;

            try {
               tname = meta.getColumnTypeName(i + 1);
            }
            catch(SQLException ignore) {
               // certain versions of the Sybase driver will fail on this,
               // ignore it, the type name is not necessary in this case
            }

            creators[i] = sqlTypesHelper. getXTableColumnCreator(sqltypes[i], tname);
            sqltypes[i] = sqlTypesHelper.fixSQLType(sqltypes[i], tname);
            types[i] = sqlTypesHelper.convertToJava(sqltypes[i]);
            String colName = meta == null ? null : meta.getColumnName(i + 1);
            minfos[i] = selectOK ?
               xselect.getXMetaInfo(columnMap != null ? columnMap[i] : i) :
               xselect.getXMetaInfo(colName);
         }

         xselect.notifyAll();
      }
   }

   /**
    * Get the table column creator.
    * @param col the specified column index.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return creators[col];
   }

   /**
    * Create an empty JDBCTableNode.
    */
   protected JDBCTableNode() {
      super();
   }

   /**
    * Close the resultset when the object is garbage collected.
    */
   @Override
   public void finalize() throws Throwable {
      close();
      super.finalize();
   }

   /**
    * Close all connections.
    */
   @Override
   public void close() {
      synchronized(this) {
         cancelled = true;

         try {
            // the result set need to be closed to release the ResultSet obj
            if(result != null) {
               result.close();
            }
         }
         catch(Throwable ex) {
            LOG.debug("Failed to close result set", ex);
         }

         try {
            if(stmt != null) {
               stmt.close();
            }
         }
         catch(Throwable ex) {
            // user canceled while getting the rows, no need to report as error
            if(ex.getMessage() != null && ex.getMessage().contains("user requested cancel")) {
               LOG.debug("Query cancelled", ex);
            }
            else {
               LOG.warn("Failed to close statement", ex);
            }
         }
      }

      try {
         synchronized(this) {
            if(conn != null) {
               releaseConnection();
            }
         }
      }
      catch(Throwable ex) {
         LOG.warn("Failed to release connection to pool", ex);
      }
      finally {
         // Do not rely on garbage collector mechanics to remove the statement
         // from the QueryManager. Remove it explicitly to prevent issues.
         for(QueryManager manager : queryManagers) {
            manager.removePending(stmt);
         }

         synchronized(this) {
            stmt = null;
            result = null;
            conn = null;
         }
      }
   }

   /**
    * Release connection when close.
    */
   protected void releaseConnection() throws Exception {
      Tool.closeQuietly(conn);
      conn = null;
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      boolean closeStmt = false;

      try {
         synchronized(this) {
            if(result != null) {
               if(!cancelled && stmt != null && count % 80 == 0 && QueryManager.isCancelled(stmt)) {
                  cancel();
               }

               boolean more = !cancelled && result.next();

               if(max > 0 && count == max) {
                  more = false;
                  amax = max;
               }

               rready = false;

               if(!more) {
                  closeStmt = true;
                  // clear the static blobmap
                  sqlTypesHelper.clearBlobmap();

                  if(!cancelled) {
                     LOG.debug("Query result loaded: " + count + " rows");
                  }
               }
               else {
                  count++;
               }

               return more;
            }
         }
      }
      catch(SQLException e) {
         // user canceled while getting the rows, no need to report as error
         if(JDBCUtil.isCancelled(e, stmt)) {
            LOG.debug("Query cancelled", e);
            sqlTypesHelper.clearBlobmap();
            return false;
         }

         LOG.error("Database error occurred while reading result: " + e.getMessage(), e);
         closeStmt = true;

         // clear the static blobmap
         sqlTypesHelper.clearBlobmap();
         throw new RuntimeException(e + "");
      }
      finally {
         if(closeStmt) {
            close();
         }
      }

      return false;
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public synchronized boolean rewind() {
      try {
         if(result != null) {
            Boolean first = (Boolean) XUtil.call(result, "java.sql.ResultSet",
               "isBeforeFirst", null, null);

            if(!first) {
               XUtil.call(result, "java.sql.ResultSet", "beforeFirst", null,
                  null);
            }

            count = 0;
            amax = 0;
            return true;
         }
      }
      catch(Throwable ignore) {
      }

      return false;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return rewindable;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return ncol;
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return names[col];
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      return types[col];
   }

   /**
    * Get the column SQL type.
    * @param col column index.
    * @return column data type.
    */
   public int getSQLType(int col) {
      return sqltypes[col];
   }

   /**
    * Get the display size of the column.
    */
   public int getLength(int col) {
      return sizes[col];
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public synchronized Object getObject(int col) {
      if(!cancelled && result != null) {
         // in sql server, columns must be accessed in order. therefore
         // we always read in the whole row so no matter what the order
         // getObject() is called, we can always get all the data
         if(!rready) {
            int i = 0;
            ClassLoader oloader = Thread.currentThread().getContextClassLoader();
            ClassLoader loader = result.getClass().getClassLoader();
            Thread.currentThread().setContextClassLoader(loader);

            try {
               while(i < ncol) {
                  try {
                     for(; i < ncol; i++) {
                        row[i] = sqlTypesHelper.getObject(result, i + 1, sqltypes[i]);
                     }
                  }
                  catch(Exception ex) {
                     // ChrisS bug1404941804680 2014-7-10
                     // Do not change sqltypes[i], as the user can have text
                     // data in a BLOB field marked as image.  In that case,
                     // the following rows should be returned the same as the
                     // first row.
                  /* // error reading as image, change the type so the
                  // helper will just return resultset.getObject()
                  if(sqltypes[i] == Types.BLOB ||
                     sqltypes[i] == Types.BINARY ||
                     sqltypes[i] == Types.VARBINARY ||
                     sqltypes[i] == Types.LONGVARBINARY)
                  {
                     sqltypes[i] = Types.BIT;

                     if(ex instanceof SQLTypes.SQLImageException) {
                        SQLTypes.SQLImageException iex =
                           (SQLTypes.SQLImageException) ex;
                        row[i] = iex.getImage();
                     }
                  }
                  else {*/
                     if(!cancelled) {
                        LOG.error("Failed to get query result value: " +
                                     ex.getMessage(), ex);
                     }
                     else {
                        throw new RuntimeException(ex);
                     }

                     //avoid endless loop, add the index.
                     i++;
                  }
               }
            }
            finally {
               Thread.currentThread().setContextClassLoader(oloader);
            }

            rready = true;
         }

         return row[col];
      }

      return null;
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return minfos == null ? null : minfos[col];
   }

   @Override
   public int getAppliedMaxRows() {
      return amax;
   }

   @Override
   public synchronized void cancel() {
      try {
         cancelled = true;

         if(stmt != null) {
            stmt.cancel();
         }
      }
      catch(Throwable e) {
         LOG.debug("Unable to cancel JDBC statement at database level", e);
      }
   }

   /**
    * The hint max row maybe in the inner sql, set the hint max row to make sure
    * max row warning can be prompted(for Bug #45916).
    */
   public void setHintMaxRow(int max) {
      this.max = max;
   }

   /**
    * Check if is cacheable.
    */
   @Override
   public boolean isCacheable() {
      return false;
   }

   public void addQueryManager(QueryManager queryManager) {
      if(queryManager == null) {
         return;
      }

      queryManagers.add(queryManager);
   }

   private boolean cancelled = false;
   private int ncol;
   private String[] names;
   private XTableColumnCreator[] creators;
   private Class[] types;
   private int[] sqltypes;
   private int[] sizes;
   private XMetaInfo[] minfos;
   private ResultSet result;
   private boolean rewindable = true;
   private int max;
   private int count;
   private int amax;
   private final transient List<QueryManager> queryManagers = new ArrayList<>();

   protected transient Statement stmt;
   protected transient Connection conn = null;
   protected transient XDataSource xds = null;
   protected transient SQLTypes sqlTypesHelper = null;
   private transient Object[] row = null;
   private transient boolean rready = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(JDBCTableNode.class);
}
