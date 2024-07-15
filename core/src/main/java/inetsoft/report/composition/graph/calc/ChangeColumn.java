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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.DataSet;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.sree.SreeEnv;

/**
 * ChangeColumn computes the differences between values. Optionally, the
 * differences can be expressed as percentages.
 */
public class ChangeColumn extends ValueOfColumn {
   /**
    * Default constructor.
    */
   public ChangeColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public ChangeColumn(String field, String header) {
      super(field, header);
   }

   /**
    * Set display as percentage.
    */
   public void setAsPercent(boolean asPercent) {
      this.asPercent = asPercent;
   }

   @Override
   public boolean isAsPercent() {
      return asPercent;
   }

   /**
    * Calculate the value at the row.
    * @param row the row index of the sorted dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   @Override
   public Object calculate(DataSet data, int row, boolean first, boolean last) {
      Object cval = super.calculate(data, row, first, last);

      if(isBrushData(data)) {
         data = getBrushData(data);

         if(data == null) {
            return null;
         }

         row = ((SubDataSet2) data).getRow(row);

         if(row < 0) {
            return null;
         }
      }

      // invalid point, null
      if(cval == INVALID) {
         return INVALID;
      }

      Object val = data.getData(field, row);
      return getValue(val, cval);
   }

   /**
    * Calculate the value for the crosstab cell.
    */
   @Override
   public Object calculate(CrossTabFilter.CrosstabDataContext context,
                           CrossTabFilter.PairN tuplePair)
   {
      Object cval = super.calculate(context, tuplePair);

      return calculate(context, tuplePair, cval);
   }

   /**
    * Get the value at previous and next row.
    */
   private Object calculate(CrossTabFilter.CrosstabDataContext context,
                            CrossTabFilter.PairN tuplePair, Object cval)
   {
      if(cval == INVALID) {
         return INVALID;
      }

      Object val = context.getValue(tuplePair);

      if(val == null && missingAsZero) {
         val = getMissingValue();
      }

      return getValue(val, cval);
   }

   /**
    * @param val    the current row value.
    * @param cval   the calcuate value.
    */
   private Object getValue(Object val, Object cval) {
      if(val == null) {
         if(missingAsZero) {
            val = 0;
         }
         else {
            return null;
         }
      }

      double d = val instanceof Number ? ((Number) val).doubleValue() : NULL;
      double cd = cval instanceof Number ? ((Number) cval).doubleValue() : NULL;

      // metad data mode? all datas are same, this will cause the chart
      // empty, it is strange, here fix it
      if(isMetaDataMode()) {
         return asPercent ? 1 : cd;
      }

      if(cd == 0 && asPercent) {
         return null;
      }

      return asPercent ? (d - cd) / cd : d - cd;
   }

   @Override
   protected Object getMissingValue() {
      return missingAsZero ? 0 : super.getMissingValue();
   }

   private boolean asPercent;
   // undocumented property that could be used to force previous behavior.
   // to be removed later if no need to make it permanent. (13.5)
   private boolean missingAsZero =
      "true".equals(SreeEnv.getProperty("calc.change.missingAsZero", "true"));
}
