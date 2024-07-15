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
package inetsoft.report.composition.graph;

import inetsoft.report.*;
import inetsoft.report.filter.*;

/**
 * Brush summary filter.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BrushSummaryFilter extends DefaultTableFilter
   implements DataTableLens
{
   /**
    * Create a brush summary filter.
    * @param all the specified data without filtering.
    * @param brush the specified data with filtering.
    */
   public BrushSummaryFilter(TableLens all, TableLens brush) {
      super(brush);

      SummaryFilter afilter = GraphUtil.getSummaryFilter(all);
      SummaryFilter bfilter = GraphUtil.getSummaryFilter(brush);

      this.agrands = afilter.getGrandFormulae();
      this.bgrands = bfilter.getGrandFormulae();
      this.percent = new boolean[agrands.length];

      for(int i = 0; i < agrands.length; i++) {
         if(agrands[i] instanceof PercentageFormula) {
            PercentageFormula pform = (PercentageFormula) agrands[i];
            percent[i] = pform.getPercentageType() ==
               StyleConstants.PERCENTAGE_OF_GRANDTOTAL;
         }
      }

      // getGroupColCount is one api defined in GroupedTable. Since v10.1,
      // it's reimplemented in SummaryFilter. To get the group column count
      // to build the summary filter, we need to get the group column count
      // from the summary filter as a result and plus one
      this.gcount = afilter.getGroupColCount() + 1;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      Object obj = super.getObject(r, c);

      if(obj == null || c < gcount || c - gcount >= percent.length ||
         !percent[c - gcount] || !(obj instanceof Number))
      {
         return obj;
      }

      int idx = c - gcount;
      Number val = (Number) obj;
      Number btotal =
         (Number) ((PercentageFormula) bgrands[idx]).getOriginalResult();
      Number atotal =
         (Number) ((PercentageFormula) agrands[idx]).getOriginalResult();

      if(btotal == null || atotal == null || atotal.doubleValue() == 0) {
         return val;
      }

      double dval = val.doubleValue() * btotal.doubleValue() /
         atotal.doubleValue();
      return Double.valueOf(dval);
   }

   /**
    * Get data.
    * @param r row index.
    * @param c column index.
    * @return data in the specified cell.
    */
   @Override
   public Object getData(int r, int c) {
      TableLens table0 = getTable();

      while(table0 instanceof TableFilter) {
         table0 = ((TableFilter) table0).getTable();
      }

      if(table0 instanceof DataTableLens) {
         ((DataTableLens) table0).getData(r, c);
      }

      return getObject(r, c);
   }

   private Formula[] agrands;
   private Formula[] bgrands;
   private boolean[] percent;
   private int gcount;
}
