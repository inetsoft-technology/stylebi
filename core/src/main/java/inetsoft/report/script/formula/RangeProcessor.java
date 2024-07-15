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

import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collection;

/**
 * RangeProcessor provides the implementation for cell selection based on
 * condition and group.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public abstract class RangeProcessor {
   /**
    * Returned from match if the row matches selection.
    */
   public static final int YES = 1;
   /**
    * Returned from match if the row doesn't matches selection.
    */
   public static final int NO = 2;
   /**
    * Returned from match if the row doesn't matches selection, and
    * the row selection should terminate.
    */
   public static final int BREAK = 3;
   /**
    * Returned from match if the row matches selection, and
    * the row selection should terminate after this row is processed.
    */
   public static final int BREAK_AFTER = 4;

   /**
    * Create a processor for a table.
    */
   protected RangeProcessor(XTable table, Scriptable scope) {
      this.table = table;
      this.scope = scope;
   }

   /**
    * Select cells from a range of rows.
    * @param cells collection to add selected cells.
    * @param range cell iterator.
    * @param selector row selector.
    * @param cond condition to be evaluated on each row.
    * @param location true to add cell location (Point) to the collection,
    * false to add the cell values.
    */
   protected void selectCells(Collection cells, CellIterator range,
                              RangeSelector selector, String cond,
                              boolean location) {
      select_loop:
      while(range.hasNext()) {
         Point loc = range.next();
         int row = loc.y;
         int col = loc.x;
         boolean breakAfter = false;

         if(selector != null) {
            int rc = selector.match(table, row, col);

            switch(rc) {
            case YES:
               break;
            case NO:
               continue select_loop;
            case BREAK:
               break select_loop;
            case BREAK_AFTER:
               breakAfter = true;
               break;
            }
         }

         if(cond != null && cond.length() > 0) {
            try {
               Scriptable tableRow = range.getScope();
               Object rc = FormulaEvaluator.exec(cond, scope, "rowValue", tableRow);

               if(rc instanceof Boolean && !((Boolean) rc).booleanValue()) {
                  if(breakAfter) {
                     break;
                  }

                  continue;
               }
            }
            catch(Throwable ex) {
               LOG.error("Error occurred when finding rows matching condition: " +
                  cond, ex);
            }
         }

         Object val = location ? loc : range.getValue(scope);

         cells.add(val);

         if(breakAfter) {
            break;
         }
      }
   }

   public void setProcessCalc(boolean calc) {
      this.processCalc = calc;
   }

   protected boolean processCalc = false;
   protected XTable table;
   protected Scriptable scope;

   private static final Logger LOG =
      LoggerFactory.getLogger(RangeProcessor.class);
}
