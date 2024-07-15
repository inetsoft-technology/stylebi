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
package inetsoft.uql.util;

import inetsoft.uql.*;
import inetsoft.uql.table.*;

/**
 * Combine multiple XTableNode into one XTableNode. This is for optimization.
 * It does break the dependency but to not use PagedTableLens would require
 * re-implementing table data paging and is not as efficient. It will be allowed
 * for optimization purpose.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CompositeTableNode extends XTableNode {
   public CompositeTableNode(XTableNode table) {
      this(table, 0);
   }

   public CompositeTableNode(XTableNode table, int maxrows) {
      setName(table.getName());
      setMaxRows(maxrows);

      names = new String[table.getColCount()];
      types = new Class[names.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = table.getName(i);
         types[i] = table.getType(i);

         if(types[i] == null) {
            types[i] = String.class;
         }
      }

      this.node = table;
      this.table = new XSwappableTable2(names, types);

      try {
         addTable(table);
      }
      catch(UnsupportedOperationException ex) {
         // ignore exception for maxrows, otherwise the constructor fails.
      }
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
    * Set the applied max rows.
    * @param max the applied max rows.
    */
   @Override
   public void setAppliedMaxRows(int max) {
      this.amax = max;
   }

   /**
    * Get the maximum number of rows.
    */
   public int getMaxRows() {
      return maxrows;
   }

   /**
    * Set the maximum number of rows.
    */
   public void setMaxRows(int max) {
      this.maxrows = max;
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      return table.moreRows(idx++ + 1); // table has header row
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
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public Object getObject(int col) {
      return table.getObject(idx, col);
   }

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return node.getXMetaInfo(col);
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public boolean rewind() {
      idx = 0;
      amax = 0;
      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   /**
    * Called after table is loaded.
    */
   public void complete() {
      table.complete();
   }

   /**
    * Get the table lens wrapped in this.
    */
   public XTable getTable() {
      return table;
   }

   /**
    * Add a table node to the table.
    */
   public void addTable(XTableNode xtable) {
      int cnt = table.getRowCount();

      if(cnt < 0) {
         cnt = -cnt - 1;
      }

      if(cnt > 0) {
         cnt--; // remove header row
      }

      int count = getColCount();
      Object[] row = new Object[count];

      while(xtable.next() && !cancelled) {
         if((maxrows > 0 && cnt == maxrows)) {
            amax = maxrows;
            break;
         }

         for(int i = 0; i < row.length; i++) {
            row[i] = xtable.getObject(i);
         }

         table.addRow(row);
         cnt++;
      }

      if(maxrows > 0 && cnt >= maxrows) {
         complete();
         amax = maxrows;
         throw new UnsupportedOperationException("Max rows reached");
      }

      if(cancelled) { // start the loading thread
         complete();
         // cached in parse
         throw new UnsupportedOperationException("Operation cancelled");
      }
   }

   /**
    * Check if the table is full (reached max rows).
    */
   public boolean isFull() {
      if(maxrows > 0) {
	 int cnt = table.getRowCount();

	 if(cnt < 0) {
	    cnt = -cnt - 1;
	 }

	 cnt--; // remove header row

	 return cnt >= maxrows;
      }

      return false;
   }

   /**
    * Cancel the loading of a table.
    */
   @Override
   public void cancel() {
      cancelled = true;
   }

   final class XSwappableTable2 extends XSwappableTable {
      public XSwappableTable2(String[] names, Class[] types) {
         super();
         this.types = types;
         XTableColumnCreator[] creators = new XTableColumnCreator[names.length];

         for(int i = 0; i < names.length; i++) {
            creators[i] = XObjectColumn.getCreator();
         }

         init(creators);
         addRow(names);

         for(int i = 0; i < names.length; i++) {
            setXMetaInfo(names[i], getXMetaInfo(i));
         }
      }

      @Override
      public int getAppliedMaxRows() {
         return amax;
      }

      @Override
      public Class getColType(int col) {
         return types[col];
      }

      private Class[] types;
   }

   private String[] names;
   private Class[] types;
   private XSwappableTable table;
   private XTableNode node;
   private int idx;
   private int maxrows;
   private int amax;
   private boolean cancelled;
   private boolean disposed;
}
