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

import inetsoft.report.internal.Util;
import inetsoft.uql.XTable;
import inetsoft.util.script.JavaScriptEngine;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * TableRangeProcessor provides the implementation for range processing for
 * regular table and grouped tables.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class TableRangeProcessor extends RangeProcessor {
   /**
    * Create a processor for a table.
    */
   public TableRangeProcessor(XTable table, Scriptable scope) {
      super(table, scope);
   }

   /**
    * Select cells from a range of rows.
    * @param cells collection to add selected cells.
    * @param startRow the first row in the range.
    * @param endRow the last row (non-inclusive).
    * @param selector row selector.
    * @param cond condition to be evaluated on each row.
    * @param location true to add cell location (Point) to the collection,
    * false to add the cell values.
    */
   public void selectCells(Collection cells, int col,
                           int startRow, int endRow, int direction,
                           RangeSelector selector, String cond,
                           boolean location) {
      CellIterator iter = new RowIterator(table, col, startRow, endRow,
                                          direction);

      select0(cells, iter, selector, cond, location, direction);
   }

   /**
    * Select cells from a range of rows.
    * @param cells collection to add selected cells.
    * @param colexpr column name or expression.
    * @param expression true if the colexpr is an expression.
    * @param startRow the first row in the range.
    * @param endRow the last row (non-inclusive).
    * @param selector row selector.
    * @param cond condition to be evaluated on each row.
    * @param location true to add cell location (Point) to the collection,
    * false to add the cell values.
    */
   public void selectCells(Collection cells, String colexpr, boolean expression,
                           int startRow, int endRow, int direction,
                           RangeSelector selector, String cond,
                           boolean location) {
      CellIterator iter = null;

      if(JavaScriptEngine.errorsExceeded(colexpr)) {
         return;
      }

      if(expression) {
         iter = new RowIterator(table, colexpr, startRow, endRow, direction);
      }
      else {
         int col = Util.findColumn(table, colexpr);

         if(col < 0) {
            if(colexpr.equals("*")) {
               col = 0;
            }
            else {
               //throw new RuntimeException("Column not found in table: " + colexpr);
               if(JavaScriptEngine.getExecScriptable() != null) {
                  JavaScriptEngine.incrementError(colexpr);
               }

               LOG.debug("Column not found in table: {}", colexpr);
               return;
            }
         }

         iter = new RowIterator(table, col, startRow, endRow, direction);
      }

      select0(cells, iter, selector, cond, location, direction);
   }

   /**
    * Select cells base on selector and condition.
    */
   private void select0(Collection cells, CellIterator iter,
                        RangeSelector selector, String cond,
                        boolean location, int direction) {
      Vector result = new Vector();

      super.selectCells(result, iter, selector, cond, location);

      // @by billh, for backward direction, we memorize values
      // in stack then add them to formula in forward direction
      // for some formula result depends on the direction
      if(direction < 0) {
         Collections.reverse(result);
      }

      cells.addAll(result);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableRangeProcessor.class);
}
