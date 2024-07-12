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

import inetsoft.report.filter.CrossTabFilter;
import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;

import java.awt.*;
import java.util.Collection;
import java.util.Iterator;

/**
 * Iterator through all cells of a crosstab.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
class CrosstabIterator extends CellIterator {
   public CrosstabIterator(XTable table, boolean processCalc, Collection<Point> cells) {
      super();
      this.table = table;
      row = processCalc ? 0 : table.getHeaderRowCount();
      col = -1;
      colcnt = table.getColCount();
      this.cells = (cells != null) ? cells.iterator() : null;
   }

   @Override
   public boolean hasNext() {
      if(cells != null) {
         return cells.hasNext();
      }
      
      return table.moreRows(row) && col < colcnt;
   }

   @Override
   public Point next() {
      if(cells != null) {
         Point pt = cells.next();
         col = pt.x;
         row = pt.y; 
         return pt;
      }

      col++;

      if(col >= colcnt) {
         col = 0;
         row++;
      }

      return new Point(col, row);
   }

   @Override
   public Scriptable getScope() {
      if(tableRow == null) {
         tableRow = new CrosstabCellScope((CrossTabFilter) table, row, col);
      }
      else {
         tableRow.setCell(row, col);
      }

      return tableRow;
   }

   @Override
   public Object getValue(Scriptable scope) {
      return table.getObject(row, col);
   }

   private XTable table;
   private CrosstabCellScope tableRow = null;
   private int row = 0, col = -1;
   private int colcnt;
   private Iterator<Point> cells;
}
