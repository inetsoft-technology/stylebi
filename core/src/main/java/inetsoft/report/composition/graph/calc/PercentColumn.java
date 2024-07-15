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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.AbstractDataSetFilter;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.Tuple;
import it.unimi.dsi.fastutil.objects.*;

import java.util.*;

/**
 * PercentColumn calculate an aggregate column as percentage, two options:
 * 1: Grand Total, the percent is calculate on the root data set's total.
 * 2: Sub Total, the percent is calculate on current data set's total, innermost
 *    graph's data set.
 */
public class PercentColumn extends AbstractColumn {
   /**
    * Default constructor.
    */
   public PercentColumn() {
      super();
   }

   /**
    * Constructor.
    * @param field the field name which will be created a calculation on.
    * @param header the column header for this calculation column.
    */
   public PercentColumn(String field, String header) {
      super(field, header);
   }

   /**
    * Set field for calculate total, most case it is same as field.
    */
   public void setTotalField(String tfield) {
      this.tfield = tfield;
   }

   /**
    * Get total field.
    */
   public String getTotalField() {
      return tfield;
   }

   /**
    * Check is grand total.
    */
   public boolean isGrandTotal() {
      return level == PercentCalc.GRAND_TOTAL;
   }

   /**
    * Set the sub-group column.
    */
   public void setDim(String dim) {
      this.dim = dim;
   }

   /**
    * Get the sub-group column.
    */
   public String getDim() {
      return dim;
   }

   /**
    * Set the sub-group column.
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Get the sub-group column.
    */
   public int getLevel() {
      return level;
   }

   @Override
   public boolean isAsPercent() {
      return true;
   }

   /**
    * Calculate the value at the row.
    * @param row the row index of the dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   @Override
   public Object calculate(DataSet data, int row, boolean first, boolean last) {
      Object val = data.getData(field, row);
      Object gval = getGroupKey(data, row, dim == null ? innerDim : dim);

      // null value? treat as null
      if(val == null) {
         return null;
      }
      // invalid data? treat as zero
      else if(!(val instanceof Number)) {
         return ZERO;
      }

      Double sum = getTotal(data, (tfield != null) ? tfield : field, gval, isGrandTotal());

      // invalid summary? treat as null
      if(sum == null || sum == 0) {
         return null;
      }

      return ((Number) val).doubleValue() / sum;
   }

   /**
    * Do in crosstabfilter.
    */
   @Override
   public Object calculate(CrossTabFilter.CrosstabDataContext context,
                           CrossTabFilter.PairN tuplePair)
   {
      return context.getValue(tuplePair);
   }

   @Override
   public boolean supportSortByValue() {
      return true;
   }

   /**
    * Get total value of a aggregate column.
    * @param field the aggregate column name.
    * @param gval sub-group value if sub-group is specified.
    * @param grandTotal the flag for calculate grand total or sub total.
    */
   private Double getTotal(DataSet data, String field, Object gval, boolean grandTotal) {
      if(data instanceof BrushDataSet) {
         data = ((BrushDataSet) data).getDataSet(true);

         // not matter grand total or sub total, use all data
         field = GraphUtil.getOriginalCol(field);
      }

      if(grandTotal && data instanceof AbstractDataSetFilter) {
         return getTotal(((AbstractDataSetFilter) data).getDataSet(), field, gval, grandTotal);
      }
      else {
         return getTotal0(data, field, gval, grandTotal);
      }
   }

   /**
    * Get total value of a aggregate column.
    * @param field the aggregate column name.
    * @param grandTotal the flag for calculate grand total or sub total.
    */
   private Double getTotal0(DataSet data, String field, Object gval, boolean grandTotal) {
      if(data.indexOfHeader(field) < 0) {
         return ZERO;
      }

      // @by stephenwebster, For Bug #17051
      // The use of grandTotal in the key is not sufficient when using sub_total
      // so add in gval into the key so different group totals can be saved.
      int dataHash = data.hashCode();
      boolean all = dim == null && grandTotal;
      Tuple key = new Tuple(field, !all ? gval : "_grandTotal", dataHash);

      if(cached == null) {
         cached = new ObjectOpenHashSet<>();
         cachedTotal = new Object2DoubleOpenHashMap<>();
      }

      synchronized(cachedTotal) {
         if(!cached.contains(data)) {
            // optimization, calculate all totals in one pass instead of calculating one
            // total on each call.
            for(int i = 0; i < data.getRowCount(); i++) {
               // @ankitmathur, For Bug #17051
               // If getting group key for a SUB_TOTAL calculation, we need to
               // do the look-up with the unique innerDim.
               Object rowGval = getGroupKey(data, i, dim == null ? innerDim : dim);
               Tuple rowKey = new Tuple(field, !all ? rowGval : "_grandTotal", dataHash);
               double total = cachedTotal.computeDoubleIfAbsent(rowKey, k -> 0.0);

               Object val = data.getData(field, i);
               cachedTotal.put(rowKey, add(total, val));
            }

            cached.add(data);
         }

         return cachedTotal.get(key);
      }
   }

   /**
    * Add two object as Number.
    */
   private static final Double add(Object total, Object val) {
      if(!(total instanceof Number)) {
         total = ZERO;
      }

      if(!(val instanceof Number)) {
         val = ZERO;
      }

      return ((Number) total).doubleValue() + ((Number) val).doubleValue();
   }

   /**
    * Get the unique key for the groups.
    */
   private Object getGroupKey(DataSet data, int r, String dim) {
      if(dim == null) {
         return null;
      }

      List<Object> key = new ArrayList<>();
      List<XDimensionRef> dims = getDimensions();

      // there are two cases:
      // 1. dim is on the axes
      // 2. dim is a visual frame
      // the following code would include all the parent dims and the dim itself.
      // if the dim is a visual frame, the innermost dim is ignored so the
      // percentage is calculated on the values of the same color in the same
      // facet cell
      for(int i = 0; i < dims.size() - 1; i++) {
         String col = dims.get(i).getFullName();
         key.add(data.getData(col, r));

         if(col.equals(dim)) {
            break;
         }
      }

      // @by stephenwebster, For Bug #17051
      // Ignore the inner most subgroup dimension if level is SUB_TOTAL
      // Note: this.dim is intentionally used in the condition
      // instead of the parameter
      if(isGrandTotal() || this.dim != null) {
         key.add(data.getData(dim, r));
      }

      return key;
   }
/*
   private String getGroupKey(CrossTabFilter filter, int row, String dim) {
      return null;
   }

   private Double getTotal(CrossTabFilter filter, int row, int col, String dim) {
      boolean rowGrandTotal = false;
      boolean colGrandTotal = false;
      boolean singleRowSubTotal = false;
      boolean singleColSubTotal = false;
      boolean rowSubTotal = false;
      boolean colSubTotal = false;

      int dimIndex = -1;
      int aggrIndex = -1;

      String key = getGroupKey(filter, row, dim);

      if(cachedTotal.containsKey(key)) {
         return cachedTotal.get(key);
      }

      Double total = 0.0;

      // if percent by row grand total
      if(rowGrandTotal && !filter.isSuppressRowSubtotal()) {
         Object value = filter.getObject(filter.getRowCount() - 1, col);

         if(!(value instanceof Number)) {
            value = null;
         }

         cachedTotal.put(key, (Double) value);
      }
      else if(rowGrandTotal) {
         for(int i = filter.getHeaderRowCount(); i < filter.getRowCount(); i++) {
            if(isTotalRow(filter, col)) {
               continue;
            }

            Object value = filter.getObject(i, col);

            if(value instanceof Number) {
               total += (Double) value;
            }
         }
      }
      // if percent by column grand total
      else if(colGrandTotal && !filter.isSuppressColumnSubtotal()) {
         Object value = filter.getObject(row,filter.getColCount() - 1);

         if(!(value instanceof Number)) {
            value = null;
         }

         cachedTotal.put(key, (Double) value);
      }
      else if(colGrandTotal) {
         int startCol = 0;
         int endCol = 0;  // should not include grand total

         for(int c = startCol; c < endCol; c++) {
            if(isTotalCol(filter, col)) {
               continue;
            }

            Object value = filter.getObject(row, c);

            if(value instanceof Number) {
               total += (Double) value;
            }
         }
      }
      // if the dimensin is row header, and has total value in filter.
      else if(singleRowSubTotal && !filter.isSuppressRowSubtotal() &&
         !filter.isSuppressRowGroupTotal(dimIndex))
      {
         Object rtuple = CrossTabFilterUtil.getLimitedRowTuple(filter, row, dimIndex);
         Object ctuple = CrossTabFilterUtil.getColTuple(filter, col);
         Object value = CrossTabFilterUtil.getValue(filter, rtuple, ctuple, aggrIndex);

         if(!(value instanceof Number)) {
            value = null;
         }

         cachedTotal.put(key, (Double) value);
      }
      // if the dimensin is row header, but filter has no total value, need to calculate here.
      else if(singleRowSubTotal) {
         Object rtuple = CrossTabFilterUtil.getLimitedRowTuple(filter, row, dimIndex);

         for(int i = row; i < filter.getRowCount(); i++) {
            Object rtuple0 = CrossTabFilterUtil.getLimitedRowTuple(filter, i, dimIndex);

            if(isTotalRow(filter, i) || !CrossTabFilterUtil.isSameGroup(rtuple, rtuple0)) {
               break;
            }

            Object value = filter.getObject(i, col);

            if(value instanceof Number) {
               total += (Double) value;
            }
         }
      }
      // if the dimensin is col header, and has total value in filter.
      else if(singleColSubTotal && !filter.isSuppressColumnSubtotal() &&
         !filter.isSuppressColumnGroupTotal(dimIndex))
      {
         Object ctuple = CrossTabFilterUtil.getLimitedColTuple(filter, col, dimIndex);
         Object rtuple = CrossTabFilterUtil.getRowTuple(filter, row);
         Object value = CrossTabFilterUtil.getValue(filter, rtuple, ctuple, aggrIndex);

         if(!(value instanceof Number)) {
            value = null;
         }

         cachedTotal.put(key, (Double) value);
      }

      // if the dimensin is column header, but filter has no total value, need to calculate here.
      else if(singleColSubTotal) {
         Object ctuple = CrossTabFilterUtil.getLimitedColTuple(filter, col, dimIndex);
         int startCol = 0;
         int endCol = 0;  // should not include grand total

         for(int c = startCol; c < endCol; c++) {
            Object ctuple0 = CrossTabFilterUtil.getLimitedColTuple(filter, c, dimIndex);

            if(isTotalCol(filter, c) || !CrossTabFilterUtil.isSameGroup(ctuple, ctuple0)) {
               break;
            }

            Object value = filter.getObject(row, c);

            if(value instanceof Number) {
               total += (Double) value;
            }
         }
      }

      cachedTotal.put(key, total);

      return total;
   }

   private boolean isTotalRow(CrossTabFilter filter, int row) {
      return filter.isTotalRow(row);
   }

   private boolean isTotalCol(CrossTabFilter filter, int col) {
      return filter.isTotalCol(col);
   }
*/

   private String tfield = null;
   private String dim = null;
   private int level = -1;
   private transient Object2DoubleMap<Tuple> cachedTotal;
   private transient Set<DataSet> cached;
}
