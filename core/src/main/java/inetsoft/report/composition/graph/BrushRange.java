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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SumDataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.scale.ScaleRange;
import inetsoft.graph.scale.StackRange;

import java.util.*;

/**
 * Calculate the range by stacking the values. Handles special brushing columns.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BrushRange implements ScaleRange {
   /**
    * Create a brush range.
    */
   public BrushRange(ScaleRange range) {
      this.range = range;
   }

   /**
    * Get the scale range.
    */
   public ScaleRange getScaleRange() {
      return range;
   }

   /**
    * Get the value.
    */
   @Override
   public double getValue(Object data) {
      return range.getValue(data);
   }

   /**
    * Set whether return absolute value.
    */
   @Override
   public void setAbsoluteValue(boolean abs) {
      range.setAbsoluteValue(abs);
   }

   /**
    * Test if return absolute value.
    */
   @Override
   public boolean isAbsoluteValue() {
      return range.isAbsoluteValue();
   }

   /**
    * Set if all meaures are stacked together.
    */
   public void setStackMeasures(boolean stackMeasures) {
      this.stackMeasures = stackMeasures;
   }

   /**
    * Check if all meaures are stacked together.
    */
   public boolean isStackMeasures() {
      return stackMeasures;
   }

   @Override
   public double[] calculate(DataSet data, String[] cols, GraphtDataSelector selector) {
      Set<String> removeCols = new HashSet<>();

      // for LinearRange, ignore brush column as well, so that there is no
      // difference for range like LinearRange, StackRange, etc.
      // if(!(range instanceof LinearRange)) {
      int prefix = BrushDataSet.ALL_HEADER_PREFIX.length();

      for(int i = 0; i < cols.length; i++) {
         int idx = cols[i].indexOf(SumDataSet.SUM_HEADER_PREFIX);

         // ignore waterfall col
         if(idx > -1) {
            removeCols.add(cols[i]);
         }
         // we mark the all data and brushed data to be stacked separately.
         // we can't ignore the brushed data since when there is negative
         // numbers, the brushed data may be larger than all data.
         else if(range instanceof StackRange && !stackMeasures) {
            StackRange stack = (StackRange) range;
            idx = cols[i].indexOf(BrushDataSet.ALL_HEADER_PREFIX);

            if(idx > -1) {
               String base = cols[i].substring(0, idx) + cols[i].substring(idx + prefix);
               stack.addStackFields(cols[i]);
               stack.addStackFields(base);
            }
         }
      }

      if(removeCols.size() > 0) {
         List<String> flds = new ArrayList<>();

         for(int i = 0; i < cols.length; i++) {
            if(!removeCols.contains(cols[i])) {
               flds.add(cols[i]);
            }
         }

         cols = new String[flds.size()];
         flds.toArray(cols);
      }

      return range.calculate(data, cols, selector);
   }

   @Override
   public void setMeasureRange(String measure, int start, int end) {
      range.setMeasureRange(measure, start, end);
   }

   @Override
   public int[] getMeasureRange(String measure) {
      return range.getMeasureRange(measure);
   }

   @Override
   public Collection<String> getRangeMeasures() {
      return range.getRangeMeasures();
   }

   @Override
   public int getStartRow(DataSet data, String... measures) {
      return range.getStartRow(data, measures);
   }

   @Override
   public int getEndRow(DataSet data, String... measures) {
      return range.getEndRow(data, measures);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + "[" + range.toString() + "]";
   }

   private final ScaleRange range;
   private boolean stackMeasures = false;
}
