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
package inetsoft.report.script.formula;

import inetsoft.report.internal.table.RuntimeCalcTableLens;
import org.mozilla.javascript.Scriptable;

import java.awt.*;

/**
 * Named cell iterator.
 */
class CalcCellIterator extends CellIterator {
   public CalcCellIterator(RuntimeCalcTableLens calc, String name) {
      this.calc = calc;
      locs = calc.getCalcCellMap().getLocations(name);
      
      if(locs == null) {
         locs = new Point[0];
      }
   }
      
   /**
    * Check if there is more cells to process.
    */
   @Override
   public boolean hasNext() {
      return idx < locs.length - 1;
   }
   
   /**
    * Get the location of the next cell.
    */
   @Override
   public Point next() {
      idx++;
      return locs[idx];
   }
   
   /**
    * Get the value of the current cell.
    */
   @Override
   public Object getValue(Scriptable scope) {
      return calc.getObject(locs[idx].y, locs[idx].x);
   }
   
   /**
    * Get the evaluation scope of the current cell.
    */
   @Override
   public Scriptable getScope() {
      Point loc = locs[idx];
      
      if(scope == null) {
         scope = new CalcCellScope(calc, loc.y, loc.x);
      }
      else {
         scope.setCell(loc.y, loc.x);
      }
      
      return scope;
   }
   
   private RuntimeCalcTableLens calc;
   private CalcCellScope scope;
   private Point[] locs;
   private int idx = -1;
}
