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
package inetsoft.report.script.formula;

import inetsoft.report.script.TableRow;
import inetsoft.uql.XTable;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.swap.DataUnavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Arrays;

/**
 * Iterator through a range of rows.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
class RowIterator extends CellIterator {
   /**
    * Create an iterator for an expression.
    * @param startRow starting row index.
    * @param endRow last row index (non-inclusive).
    */
   public RowIterator(XTable table, String expr, int startRow, int endRow, 
                      int direction) {
      this(table, 0, startRow, endRow, direction);
      this.expr = expr;
   }
   
   /**
    * Create an iterator for a column.
    * @param startRow starting row index.
    * @param endRow last row index (non-inclusive).
    */
   public RowIterator(XTable table, int col, int startRow, int endRow, 
                      int direction) {
      this.table = table;
      this.startRow = startRow;
      this.endRow = endRow;
      this.direction = direction;
      this.col = col;

      row = startRow - direction;

      // make sure row is not greater than row count
      if(direction < 0) {
         while(row > 0 && !moreRows(table, row - 1)) {
            row--;
         }
      }
   }
     
   /**
    * Create an iterator for a column over an explicit list of rows (direction is forward).
    * @param rows the rows to visit, ascending.
    * @param startRow rows before this row are skipped.
    */
   public RowIterator(XTable table, String expr, int col, int[] rows, int startRow) {
      this(table, col, startRow, Integer.MAX_VALUE, 1);
      this.expr = expr;
      this.rows = rows;
      int pos = Arrays.binarySearch(rows, startRow);
      this.idx = (pos < 0 ? -pos - 1 : pos) - 1;
   }

   @Override
   public boolean hasNext() {
      if(rows != null) {
         // stop at the first listed row beyond the table, same as the range walk
         return idx + 1 < rows.length && moreRows(table, rows[idx + 1]);
      }

      int nrow = row + direction;
      
      return direction > 0 && nrow < endRow && moreRows(table, nrow) ||
         direction < 0 && nrow > endRow;
   }

   /**
    * For optimization. Avoid calls to moreRows on table.
    */
   private boolean moreRows(XTable tbl, int row) {
      if(row < rowcnt) {
         return true;
      }
      
      rowcnt = tbl.getRowCount();

      if(rowcnt < 0) {
         rowcnt = -rowcnt - 1;
      }
      
      return tbl.moreRows(row);
   }

   @Override
   public Point next() {
      if(rows != null) {
         row = rows[++idx];
         return new Point(col, row);
      }

      row += direction;
      return new Point(col, row);
   }

   @Override
   public ScriptScope getScope() {
      if(tableRow == null) {
         tableRow = new TableRow(table, row);
      }
      else {
         tableRow.setRow(row);
      }

      return tableRow;
   }

   @Override
   public Object getValue(ScriptScope scope) {
      if(expr != null) {
         try {
            ScriptScope tableRow = getScope();

            return FormulaEvaluator.exec(expr, scope, "rowValue", tableRow);
         }
         catch(Exception ex) {
            // a stalled table or one whose swap file is lost has no value to return, the
            // failure is not a script value (#77123, #77910)
            DataUnavailable.rethrow(ex);
            // nor has a read or condition stopped by a script timeout or cancel (#78076)
            ScriptTimeoutGuard.rethrowStop(ex);
            // nor has a table that failed to load for a reader that has to fail, e.g. a
            // scheduled run (#78083)
            TableLoadException.rethrow(ex);

            LOG.error("Failed to get row value", ex);
            return null;
         }
      }

      return getData(table, row, col);
   }
 
   private XTable table;
   private int startRow, endRow, direction;
   private TableRow tableRow = null;
   private int row, col;
   private String expr;
   private int rowcnt = 0;
   private int[] rows; // explicit rows to visit, or null to walk the range
   private int idx; // index in rows of the current row

   private static final Logger LOG =
      LoggerFactory.getLogger(RowIterator.class);
}
