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
package inetsoft.report.internal.table;

import inetsoft.uql.XTable;
import inetsoft.util.BTreeFile;

/**
 * RowKeyEnumeration enumerates a table to get all the keys.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public final class RowKeyEnumeration {
   /**
    * Constructor.
    */
   public RowKeyEnumeration(XTable table) {
      super();

      int[] cols = new int[table.getColCount()];

      for(int i = 0; i < cols.length; i++) {
         cols[i] = i;
      }

      this.table = table;
      this.cols = cols;
      reset();
   }

   /**
    * Constructor.
    */
   public RowKeyEnumeration(XTable table, int[] cols) {
      super();

      this.table = table;
      this.cols = cols;

      reset();
   }

   /**
    * Reset the row key enumeration.
    */
   public void reset() {
      row = table.getHeaderRowCount();
   }

   /**
    * Check if has more keys.
    * @return <tt>true</tt> if more, <tt>false</tt> otherwise.
    */
   public boolean hasNext() {
      return table.moreRows(row);
   }

   /**
    * Get the next key.
    * @return the next key.
    */
   public BTreeFile.Key next() {
      int hash = 1;
      
      for(int i = 0; i < cols.length; i++) {
         Object obj = table.getObject(row, cols[i]);
         
         if(obj instanceof Number) {
            obj = Double.valueOf(obj.toString());
         }
         
         hash = 31 * hash + (obj == null ? 0 : obj.hashCode());
      }
      
      byte[] data = {
         (byte) ((hash >> 24) & 0xff),
         (byte) ((hash >> 16) & 0xff),
         (byte) ((hash >> 8) & 0xff),
         (byte) (hash & 0xff)
      };

      ++row;
      return new BTreeFile.Key(data);
   }

   /**
    * Get last row.
    * @return last row.
    */
   public int getLast() {
      return row - 1;
   }

   private XTable table;
   private int[] cols;
   private int row;
}
