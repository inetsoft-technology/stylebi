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
package inetsoft.report.script.formula;

import inetsoft.report.internal.Util;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import inetsoft.util.script.FormulaContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A cell range handling the [row,col]:[row,col] syntax.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class PositionalCellRange extends CellRange {
   /**
    * Create a cell range. All indices are inclusive.
    */
   public PositionalCellRange(int row1, int col1, int row2, int col2) {
      this.row1 = row1;
      this.col1 = Integer.valueOf(col1);
      this.row2 = row2;
      this.col2 = Integer.valueOf(col2);
   }

   /**
    * Parse a cell range. The supported syntax are: [1,1]:[2,2] or 
    * [1,state]:[2,state].
    */
   public PositionalCellRange(String range) throws Exception {
      int colon = range.indexOf(":");

      if(colon > 0) {
         String s1 = range.substring(0, colon).trim();
         String s2 = range.substring(colon + 1).trim();

         if(!s1.startsWith("[") || !s1.endsWith("]") ||
            !s2.startsWith("[") || !s2.endsWith("]"))
         {
            throw new Exception("Cell range format error: " + range);
         }

         s1 = s1.substring(1, s1.length() - 1).trim();
         s2 = s2.substring(1, s2.length() - 1).trim();

         int c1 = s1.indexOf(",");
         int c2 = s2.indexOf(",");

         if(c1 < 0 || c2 < 0) {
            throw new Exception("Cell range format error: " + range);
         }

         String rstr1 = s1.substring(0, c1).trim();
         String rstr2 = s2.substring(0, c2).trim();

         row1 = rstr1.equals("*")
            ? Integer.MAX_VALUE: Integer.parseInt(rstr1);
         row2 = rstr2.equals("*")
            ? Integer.MAX_VALUE: Integer.parseInt(rstr2);

         String cstr1 = s1.substring(c1 + 1).trim();
         String cstr2 = s2.substring(c2 + 1).trim();

         // get column index
         try {
            if(cstr1.equals("*")) {
               col1 = Integer.valueOf(Integer.MAX_VALUE);
            }
            else {
               col1 = Integer.valueOf(cstr1);
            }
         }
         catch(Exception ex) {
            col1 = cstr1;
         }

         // get column index
         try {
            if(cstr2.equals("*")) {
               col2 = Integer.valueOf(Integer.MAX_VALUE);
            }
            else {
               col2 = Integer.valueOf(cstr2);
            }
         }
         catch(Exception ex) {
            col2 = cstr2;
         }
      }
   }

   /**
    * Initialize the dimensions.
    */
   private void init() {
      // handle relative positions
      if(row1 < 0 || row2 < 0) {
         Point loc = FormulaContext.getCellLocation();
            
         if(loc == null) {
            throw new RuntimeException("Formula is executed in a cell, " +
                                       "relative index is not supported: "
                                       + this);
         }

         if(row1 < 0) {
            row1 += loc.y;
         }

         if(row2 < 0) {
            row2 += loc.y;
         }
      }
         
      top = Math.min(row1, row2);
      bottom = Math.max(row1, row2);

      if(col1 instanceof Integer && col2 instanceof Integer) {
         int ci1 = ((Integer) col1).intValue();
         int ci2 = ((Integer) col2).intValue();

         if(ci1 < 0 || ci2 < 0) {
            Point loc = FormulaContext.getCellLocation();
            
            if(loc == null) {
               throw new RuntimeException("Formula is executed in a cell, " +
                                          "relative index is not supported: "
                                          + this);
            }

            if(ci1 < 0) {
               ci1 += loc.x;
            }
               
            if(ci2 < 0) {
               ci2 += loc.x;
            }
         }
            
         left = Math.min(ci1, ci2);
         right = Math.max(ci1, ci2);
      }
   }

   /**
    * Get the region of cells in the range.
    */
   public Insets getCellRegion(XTable table) throws Exception {
      PositionalCellRange range = (PositionalCellRange) this.clone();
         
      // resolve column names
      if(!(range.col1 instanceof Integer)) {
         int idx = Util.findColumn(table, range.col1);

         if(idx >= 0) {
            range.col1 = Integer.valueOf(idx);
         }
         else {
            throw new Exception("Column not found: " + range.col1);
         }
      }

      if(!(range.col2 instanceof Integer)) {
         int idx = Util.findColumn(table, range.col2);

         if(idx >= 0) {
            range.col2 = Integer.valueOf(idx);
         }
         else {
            throw new Exception("Column not found: " + range.col2);
         }
      }

      range.init();

      return new Insets(range.top, range.left, range.bottom, range.right);
   }

   /**
    * Get all cells in the range.
    * @param position true to return cell position (Point), false to return value.
    */
   @Override
   public Collection getCells(XTable table, boolean position) throws Exception {
      int ncol = table.getColCount();
      ArrayList cells = new ArrayList();
      Insets region = getCellRegion(table);

      for(int i = Math.max(0, region.top); 
          table.moreRows(i) && i <= region.bottom; i++) 
      {
         for(int j = Math.max(0, region.left); 
             j <= region.right && j < ncol; j++) 
         {
            if(position) {
               cells.add(new Point(j, i));
            }
            else {
               cells.add(table.getObject(i, j));
            }
         }
      }

      return cells;
   }

   /**
    * Adjust the row and column index when row/col is moved.
    * @param row row position before the insert/remove.
    * @param col column position before the insert/remove.
    * @param rdiff amount row is moved. Negative is moving up.
    * @param cdiff amount column is moved. Negative is moving left.
    */
   @Override
   public void adjustIndex(int row, int col, int rdiff, int cdiff) {
      if(row <= top) {
         if(top != Integer.MAX_VALUE) {
            top += rdiff;
         }

         if(bottom != Integer.MAX_VALUE) {
            bottom += rdiff;
         }
      }
      else if(row <= bottom) {
         if(bottom != Integer.MAX_VALUE) {
            bottom += rdiff;
         }
      }

      row1 = top;
      row2 = bottom;

      if(col1 instanceof Integer && col2 instanceof Integer) {
         if(col <= left) {
            if(left != Integer.MAX_VALUE) {
               left += cdiff;
            }

            if(right != Integer.MAX_VALUE) {
               right += cdiff;
            }
         }
         else if(col <= right) {
            if(right != Integer.MAX_VALUE) {
               right += cdiff;
            }
         }

         col1 = Integer.valueOf(left);
         col2 = Integer.valueOf(right);
      }
   }

   /**
    * Convert to a string representation. The string can be later parsed
    * back.
    */
   public String toString() {
      return "[" + indexToString(row1) + "," + indexToString(col1) + "]:[" +
         indexToString(row2) + "," + indexToString(col2) + "]";
   }
      
   // handle * as index
   private static final String indexToString(int idx) {
      return (idx == Integer.MAX_VALUE) ? "*" : Integer.toString(idx);
   }

   // handle * as index
   private static final String indexToString(Object idx) {
      if(Tool.equals(idx, Integer.valueOf(Integer.MAX_VALUE))) {
         return "*";
      }

      return idx.toString();
   }

   private int row1, row2;
   private Object col1, col2; // int or String
   private int top, bottom, left, right;
}

