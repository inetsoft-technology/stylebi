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
package inetsoft.uql.util;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.table.XTableColumnCreator;

/**
 * XTableNodeWrapper, wraps an xtable node to filter out redundant rows.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XTableNodeWrapper extends XTableNode {
   /**
    * Create an instance of of <tt>XTableNodeWrapper</tt>.
    */
   public XTableNodeWrapper(XTableNode table) {
      super();
      this.table = table;
   }

   /**
    * Get the max rows.
    * @return the max rows.
    */
   public int getMaxRows() {
      return this.max;
   }

   /**
    * Set the max rows.
    * @param max the specified max rows.
    */
   public void setMaxRows(int max) {
      this.max = max;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   @Override
   public int getAppliedMaxRows() {
      return amax;
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      boolean next = table.next();

      if(next) {
         if(max > 0 && count == max) {
            amax = max;
            return false;
         }

         count++;
      }

      return next;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return table.getColCount();
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return table.getName(col);
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      return table.getType(col);
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public Object getObject(int col) {
      return table.getObject(col);
   }

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return table.getXMetaInfo(col);
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public boolean rewind() {
      count = 0;
      amax = 0;
      return table.rewind();
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return table.isRewindable();
   }

   /**
    * This method should be called after the data in the table node is
    * read. If this object holds any connection, they will be released
    * at this point.
    */
   @Override
   public void close() {
      table.close();
   }

   /**
    * Cancel the query request to the datasource if
    * datasource supports such a call
    */
   @Override
   public void cancel() {
      table.cancel();
   }

   /**
    * Get the table column creator.
    * @param col the specified column index.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return table.getColumnCreator(col);
   }

   /**
    * Get the table column creators.
    * @return the table column creators.
    */
   @Override
   public XTableColumnCreator[] getColumnCreators() {
      return table.getColumnCreators();
   }

   private int max;
   private int amax;
   private int count;
   private XTableNode table;
}
