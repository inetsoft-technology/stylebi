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
package inetsoft.uql;

import inetsoft.uql.table.XObjectColumn;
import inetsoft.uql.table.XTableColumnCreator;

/**
 * XTableNode represents a table. Although a table can be easily represented
 * as a tree, with each table row converted to a subtree, it is often more
 * efficient to keep the table semantics if the original data is returned
 * as a table. This is particularly true for SQL data sources.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XTableNode extends XNode {
   /**
    * Create an empty table.
    */
   public XTableNode() {
      super("table");
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   public abstract boolean next();

   /**
    * Get the number of columns in the table.
    */
   public abstract int getColCount();

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   public abstract String getName(int col);

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   public abstract Class getType(int col);

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   public abstract Object getObject(int col);

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   public abstract XMetaInfo getXMetaInfo(int col);

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   public abstract boolean rewind();

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   public abstract boolean isRewindable();

   /**
    * Check if a table is a result of timeout.
    */
   public boolean isTimeoutTable() {
      return false;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public int getAppliedMaxRows() {
      return amax;
   }

   /**
    * Set the applied max rows.
    * @param amax the applied max rows.
    */
   public void setAppliedMaxRows(int amax) {
      this.amax = amax;
   }

   /**
    * This method should be called after the data in the table node is
    * read. If this object holds any connection, they will be released
    * at this point.
    */
   public void close() {
   }

   /**
    * Cancel the query request to the datasource if
    * datasource supports such a call
    */
   public void cancel() {
   }

   /**
    * Check if is cacheable.
    */
   public boolean isCacheable() {
      return true;
   }

   /**
    * Get the table column creator.
    * @param col the specified column index.
    * @return the table column creator.
    */
   public XTableColumnCreator getColumnCreator(int col) {
      return XObjectColumn.getCreator(getType(col));
   }

   /**
    * Get the table column creators.
    * @return the table column creators.
    */
   public XTableColumnCreator[] getColumnCreators() {
      int count = getColCount();
      XTableColumnCreator[] creators = new XTableColumnCreator[count];

      for(int i = 0; i < count; i++) {
         creators[i] = getColumnCreator(i);
      }

      return creators;
   }

   private int amax;
}
