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
import inetsoft.util.Tool;

/**
 * Merged row memorizes the merged row information of a left table and
 * a right table.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MergedRow {
   /**
    * Creates a new instance of <tt>MergedRow</tt>.
    */
   public MergedRow() {
      rows = new int[0][0];
   }
   
   /**
    * Adds a row to the specified table.
    * 
    * @param table the index of the table.
    * @param row   the index of the row to add.
    */
   public void add(int table, int row) {
      if(rows.length <= table) {
         int[][] nrows = new int[table + 1][];
         System.arraycopy(rows, 0, nrows, 0, rows.length);
         
         for(int i = rows.length; i < nrows.length; i++) {
            nrows[i] = new int[0];
         }
         
         rows = nrows;
      }
      
      int[] nrows = new int[rows[table].length + 1];
      System.arraycopy(rows[table], 0, nrows, 0, rows[table].length);
      nrows[nrows.length - 1] = row;
      rows[table] = nrows;
   }

   /**
    * Add one row to the left.
    * @param row the specified row number.
    */
   public void addLeft(int row) {
      add(0, row);
   }

   /**
    * Add one row to the right.
    * @param row the specified row number.
    */
   public void addRight(int row) {
      add(1, row);
   }

   /**
    * Get the left row numbers.
    * @return the left row numbers.
    */
   public final int[] getLeftRows() {
      return getRows(0);
   }

   /**
    * Get the right row numbers.
    * @return the right row numbers.
    */
   public final int[] getRightRows() {
      return getRows(1);
   }
   
   public final int[] getRows(int table) {
      return rows.length <= table ? new int[0] : rows[table];
   }
   
   public final int getTableCount() {
      return rows.length;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public final String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("MergedRow:[");
      
      for(int i = 0; i < rows.length; i++) {
         if(i > 0) {
            sb.append('^');
         }
         
         for(int j = 0; j < rows[i].length; j++) {
            if(j > 0) {
               sb.append(',');
            }
            
            sb.append(rows[i][j]);
         }
      }

      return sb.append(']').toString();
   }
   
   /**
    * Checks to see if a row of the specified table matches this row.
    * 
    * @param src   the first table referenced by this object.
    * @param table the table to check.
    * @param tidx  the index of the table.
    * @param row   the row index of the table to check.
    * @param cols  the columns of the table to check.
    * 
    * @return <tt>true</tt> if the row matches; <tt>false</tt> otherwise.
    */
   public final boolean matches(XTable src, XTable table, int tidx, int row,
                                int[] cols)
   {
      boolean result = true;
      
      if(rows == null || rows.length <= tidx || rows[tidx].length == 0 ||
         cols.length == 0 || !table.moreRows(row) ||
         !src.moreRows(rows[tidx][0]))
      {
         return false;
      }
      
      for(int i = 0; i < cols.length; i++) {
         if(Tool.compare(src.getObject(rows[tidx][0], cols[i]),
                         table.getObject(row, cols[i]), true, true) != 0)
         {
            result = false;
            break;
         }
      }
      
      return result;
   }
   
   protected int[][] rows;
}
