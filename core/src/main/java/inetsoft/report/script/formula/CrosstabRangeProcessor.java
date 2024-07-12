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

import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;

import java.awt.*;
import java.util.Collection;

/**
 * CrosstabRangeProcessor provides the implementation for range selection for
 * crosstab.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class CrosstabRangeProcessor extends RangeProcessor {
   /**
    * Create a processor for a table.
    */
   public CrosstabRangeProcessor(XTable table, Scriptable scope) {
      super(table, scope);
   }

   /**
    * Select cells from a crosstab.
    * @param cells collection to add selected cells.
    * @param selector cell selector.
    * @param cond condition to be evaluated on each row.
    * @param position true to return cell position (Point), false to return value.
    */
   public void selectCells(Collection cells, RangeSelector selector,
                           String cond, boolean position) 
   {
      Collection<Point> grpcells = (selector instanceof CrosstabGroupSelector)
         ? ((CrosstabGroupSelector) selector).getGroupCells() : null;
      CellIterator iter = new CrosstabIterator(table, processCalc, grpcells);
      super.selectCells(cells, iter, selector, cond, position);
   }
}
