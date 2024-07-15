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

import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;

import java.util.Collection;

/**
 * RangeProcessor provides the implementation for cell selection based on 
 * condition and group.
 * 
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class CalcRangeProcessor extends RangeProcessor {
   public CalcRangeProcessor(XTable table, Scriptable scope) {
      super(table, scope);
   }

   /**
    * Select cells from a crosstab.
    * @param cells collection to add selected cells.
    * @param selector cell selector.
    * @param cond condition to be evaluated on each row.
    * @param position true to return cell position (Point), false to return value.
    */
   public void selectCells(Collection cells, String colname, 
                           RangeSelector selector, String cond,
                           boolean position) {
      CellIterator iter = new CalcCellIterator((RuntimeCalcTableLens) table, colname);

      super.selectCells(cells, iter, selector, cond, position);
   }
}
