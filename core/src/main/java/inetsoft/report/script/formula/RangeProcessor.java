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
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.UndefinedNameException;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.swap.DataUnavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
   protected RangeProcessor(XTable table, ScriptScope scope) {
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
               ScriptScope tableRow = range.getScope();
               Object rc = strictCondition ?
                  FormulaEvaluator.execStrict(cond, scope, "rowValue", tableRow) :
                  FormulaEvaluator.exec(cond, scope, "rowValue", tableRow);

               if(rc instanceof Boolean && !((Boolean) rc).booleanValue()) {
                  if(breakAfter) {
                     break;
                  }

                  continue;
               }
            }
            catch(Throwable ex) {
               // a stalled condition, or one that read a lost swap file, is not a failed one:
               // the row must be neither selected nor dropped (#77123, #77910)
               DataUnavailable.rethrow(ex);
               // nor has a read or condition stopped by a script timeout or cancel (#78076)
               ScriptTimeoutGuard.rethrowStop(ex);
               // nor has a table that failed to load for a reader that has to fail, e.g. a
               // scheduled run (#78083)
               TableLoadException.rethrow(ex);

               if(strictCondition) {
                  String missing = missingName(ex);

                  if(missing != null) {
                     throw new NamedCellRange.MissingColumnException("Name \"" + missing +
                        "\" is not defined: column \"" + missing + "\" not found in " +
                        "condition '" + cond + "'");
                  }

                  // any other condition-evaluation failure (a TypeError, NPE, ...) must fail
                  // the row loud too, not be taken as a match (#79226)
                  throw new NamedCellRange.ConditionEvaluationException(
                     "Condition '" + cond + "' failed to evaluate: " + ex.getMessage(), ex);
               }

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

   /**
    * Set whether a condition that names something that is not defined, e.g. a column the
    * table does not have, throws a {@link NamedCellRange.MissingColumnException} instead of
    * being logged and leaving the row selected. Default is off (Bug #78685).
    */
   public void setStrictCondition(boolean strictCondition) {
      this.strictCondition = strictCondition;
   }

   /**
    * The name a condition failure says is missing: a guest ReferenceError, or a row
    * field the table does not have. Null for any other failure.
    */
   private static String missingName(Throwable ex) {
      UndefinedNameException undefined = UndefinedNameException.find(ex);

      if(undefined != null) {
         return undefined.getName();
      }

      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(t instanceof ScriptException && t.getMessage() != null) {
            Matcher m = MISSING_FIELD.matcher(t.getMessage());

            if(m.find()) {
               return m.group(1);
            }
         }

         if(t.getCause() == t) {
            break;
         }
      }

      return null;
   }

   public void setProcessCalc(boolean calc) {
      this.processCalc = calc;
   }

   protected boolean processCalc = false;
   private boolean strictCondition = false;
   protected XTable table;
   protected ScriptScope scope;

   private static final Pattern MISSING_FIELD = Pattern.compile("^Field \"(.+?)\" not found in table");
   private static final Logger LOG =
      LoggerFactory.getLogger(RangeProcessor.class);
}
