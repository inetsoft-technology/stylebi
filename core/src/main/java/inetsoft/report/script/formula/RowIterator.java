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

import inetsoft.report.script.TableRow;
import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

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
     
   @Override
   public boolean hasNext() {
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
      row += direction;
      return new Point(col, row);
   }

   @Override
   public Scriptable getScope() {
      if(tableRow == null) {
         tableRow = new TableRow(table, row);
      }
      else {
         tableRow.setRow(row);
      }

      return tableRow;
   }

   @Override
   public Object getValue(Scriptable scope) {
      if(expr != null) {
         try {
            Scriptable tableRow = getScope();

            return FormulaEvaluator.exec(expr, scope, "rowValue", tableRow);
         }
         catch(Exception ex) {
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

   private static final Logger LOG =
      LoggerFactory.getLogger(RowIterator.class);
}
