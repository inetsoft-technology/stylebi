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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.visual.ElementVO;
import inetsoft.util.CoreTool;
import inetsoft.util.DefaultComparator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculate the range by stacking the values. The stacking could be applied
 * to all values, or each group.
 */
@TernClass(url = "#cshid=StackRange")
public class StackRange extends AbstractScaleRange {
   /**
    * A special column name to return an unique row id for group field.
    */
   @TernField
   public static final String ROWID = "__ROWID__";

   /**
    * Get the group field.
    */
   @TernMethod
   public String getGroupField() {
      return gfield;
   }

   /**
    * Set the group field. If the group field is set, data is grouped and
    * stacking range is calculated for each group. This is usually the
    * column bound to the X axis.
    * @param gfield the group field column name, or ROWID to treat each
    * row as a group.
    */
   @TernMethod
   public void setGroupField(String gfield) {
      this.gfield = gfield;
   }

   /**
    * Check if negative values are stacked separately.
    */
   @TernMethod
   public boolean isStackNegative() {
      return negGrp;
   }

   /**
    * Set whether negative values are stacked separately. If true (default),
    * the negative values are stacked downward and positive values are
    * stacked upward. Otherwise, all values are accumulated together.
    */
   @TernMethod
   public void setStackNegative(boolean negGrp) {
      this.negGrp = negGrp;
   }

   /**
    * Remove all stack fields.
    */
   @TernMethod
   public void removeAllStackFields() {
      stackfields.clear();
   }

   /**
    * Set the fields to stack together. If this is not called, all fields
    * are stacked. For example, a range for two measures, A and B, will be
    * calculated as the sum of all values in A and B by default. If they are
    * add to stack fields as [A] and [B], then the range is the max of the
    * sum of A and sum of B instead.
    */
   @TernMethod
   public void addStackFields(String... cols) {
      stackfields.add(cols);
   }

   /**
    * Calculate the min and max.
    */
   @Override
   public double[] calculate(DataSet data, String[] cols, GraphtDataSelector selector) {
      List<String> measures = new ArrayList<>(Arrays.asList(cols));

      // if brushed, don't stack var and __all__var. (59788)
      for(String col : cols) {
         if(col.startsWith(ElementVO.ALL_PREFIX)) {
            measures.remove(col.substring(ElementVO.ALL_PREFIX.length()));
         }
      }

      List<String[]> fields = new ArrayList<>(stackfields);
      List<String> all = new ArrayList<>(measures);

      for(int i = 0; i < fields.size(); i++) {
         all.removeAll(Arrays.asList(fields.get(i)));
      }

      double minValue = Double.MAX_VALUE;
      double maxValue = 0;
      double[] range = new double[] {minValue, maxValue};

      // if stackfields are set and some columns are not included, assume
      // it's not stacked (use linear), otherwise add it to stacked calc
      if(all.size() > 0 && stackfields.size() == 0) {
         fields.add(all.toArray(new String[all.size()]));
      }
      else {
         String[] cols2 = all.toArray(new String[all.size()]);
         double[] range2 = new LinearRange().calculate(data, cols2, selector);

         range[0] = Math.min(range[0], range2[0]);
         range[1] = Math.max(range[1], range2[1]);
      }

      for(int i = 0; i < fields.size(); i++) {
         double[] minmax = calculate0(data, fields.get(i), selector);
         range[0] = Math.min(range[0], minmax[0]);
         range[1] = Math.max(range[1], minmax[1]);
      }

      return range;
   }

   /**
    * Sort data by group field.
    * @param measures the measures used for calculation.
    */
   private DataSet sortDataSet(DataSet data, String... measures) {
      DataSet data0 = data;

      // the data returned by some db might not be sorted by group
      if(gfield != null && !gfield.equals(ROWID)) {
         // sort the rows for each measure range.
         data0 = getSortedDataSet(data0, measures);

         // if rows are not sorted (no range is defined for the measures),
         // then sort the entire dataset.
         if(data0 == data) {
            data0 = getSortedDataSet(data);
         }
      }

      return data0;
   }

   // Sort the data on the group column.
   // @param measure if a row range is set for the measure, only the rows in the range are
   // sorted, otherwise the data is not sorted. if measure is passed as null,
   // the entire data set is sorted.
   private DataSet getSortedDataSet(DataSet data, String ...measures) {
      SortedDataSet sdata = null;

      for(String measure : measures) {
         int[] rowRange = getMeasureRange(measure);

         if(rowRange == null) {
            return data;
         }

         sdata = new SortedDataSet(sdata != null ? sdata : data, gfield);
         sdata.setStartRow(GraphElement.getStartRow(data, rowRange[0]));
         sdata.setEndRow(GraphElement.getEndRow(data, rowRange[0], rowRange[1], null));
      }

      if(sdata == null) {
         sdata = new SortedDataSet(data, gfield);
      }

      Comparator comp = sdata.getComparator(gfield);

      if(comp == null) {
         comp = new DefaultComparator();
         sdata.setComparator(gfield, comp);
      }

      return sdata;
   }

   private double[] calculate0(DataSet data, String[] cols, GraphtDataSelector selector) {
      data = sortDataSet(data, cols);

      double minValue = Double.MAX_VALUE;
      double maxValue = 0;
      Object groupValue = null;
      double groupSum = 0;
      double groupNegSum = 0;
      int start = getStartRow(data, cols), end = getEndRow(data, cols);

      for(int i = start; i < end; i++) {
         if(selector != null && !selector.accept(data, i, cols)) {
            continue;
         }

         // if there is a single data column, stack the values in the column.
         // Otherwise stack the values of all data columns at the same row
         double sum = 0;
         double negsum = 0;

         for(int j = 0; j < cols.length; j++) {
            Object val = data.getData(cols[j], i);

            if(val == null) {
               continue;
            }

            // use get value to map object properly
            double v = getValue(val);

            if(v < 0 && negGrp) {
               negsum += v;
            }
            else {
               sum += v;
            }
         }

         Object groupVal2 = (gfield == null) ? null :
            (gfield.equals(ROWID) ? Double.valueOf(i) : data.getData(gfield, i));
         boolean change = !CoreTool.equals(groupValue, groupVal2);

         // group existing and new group
         if(change) {
            // previous group
            if(i != 0) {
               // it seems that we need not consider sum/negsum
               // when update maxValue/minValue here
               maxValue = Math.max(maxValue, groupSum);
               minValue = Math.min(minValue, groupNegSum);
            }

            groupValue = groupVal2;
            groupSum = sum;
            groupNegSum = negGrp ? negsum : sum;

            // last group
            if(i == data.getRowCount() - 1 || i == end - 1) {
               maxValue = Math.max(maxValue, groupSum);
               minValue = Math.min(minValue, groupNegSum);
            }
         }
         // group existing and old group
         else if(gfield != null) {
            groupSum += sum;
            groupNegSum += negGrp ? negsum : sum;

            if(i == end - 1) {
               maxValue = Math.max(maxValue, groupSum);
               minValue = Math.min(minValue, groupNegSum);
            }
         }
         // no grouping, use overall sums
         else {
            groupSum += sum;
            groupNegSum += negGrp ? negsum : sum;
            maxValue = Math.max(maxValue, groupSum);
            minValue = Math.min(minValue, groupNegSum);
         }

         // accumulative negative total in the middle may become bigger later
         minValue = Math.min(minValue, maxValue);
      }

      minValue = Math.min(minValue, maxValue);
      return new double[] {minValue, maxValue};
   }

   @Override
   public String toString() {
      return "StackRange" + System.identityHashCode(this) + "(" + gfield + ":" +
         stackfields.stream().map(a -> Arrays.toString(a)).collect(Collectors.joining(";")) + ")";
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof StackRange)) {
         return false;
      }

      StackRange range = (StackRange) obj;
      return negGrp == range.negGrp && Objects.equals(gfield, range.gfield) &&
         stackfields.equals(range.stackfields);
   }

   private String gfield;
   private boolean negGrp = true;
   private List<String[]> stackfields = new Vector<>();
}
