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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.util.CoreTool;
import it.unimi.dsi.fastutil.doubles.DoubleAVLTreeSet;
import it.unimi.dsi.fastutil.doubles.DoubleSortedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A linear scale is used to map numeric values on a linear scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LinearScale")
public class LinearScale extends Scale {
   /**
    * Default constructor.
    */
   public LinearScale() {
   }

   /**
    * Create a scale with user specified min and max.
    */
   public LinearScale(double min, double max) {
      setMax0(max, true);
      setMin0(min, true);
   }

   /**
    * Create a scale for the specified column.
    */
   @TernConstructor
   public LinearScale(String... flds) {
      super(flds);
   }

   /**
    * Set the calculation stratgy for finding scale range.
    */
   @TernMethod
   public void setScaleRange(ScaleRange range) {
      this.range = range;
   }

   /**
    * Get the calculation stratgy for finding scale range.
    */
   @TernMethod
   public ScaleRange getScaleRange() {
      return range;
   }

   /**
    * Set whether the scale should be reversed (from largest to smallest).
    */
   @TernMethod
   public void setReversed(boolean reversed) {
      this.reversed = reversed;
   }

   /**
    * Check whether the scale is reversed (from largest to smallest).
    */
   @TernMethod
   public boolean isReversed() {
      return reversed;
   }

   /**
    * Initialize the scale to use the values in the dataset.
    * @param data is chart data table.
    */
   @Override
   public void init(DataSet data) {
      int option = getScaleOption();
      String[] cols = getDataFields();
      this.ticks = null;

      // use fields if data fields are not defined
      if(cols == null || cols.length == 0) {
         cols = getFields();
      }

      boolean empty = data == null || data.getRowCount() == 0;
      ScaleRange calc = range == null ? new LinearRange() : range;
      double[] pair = calc.calculate(data, cols, getGraphDataSelector());

      // use explicitly set min
      if(omin.get() != null) {
         pair[0] = omin.getDouble(Double.NaN);
      }
      else {
         // force 0 in the scale on axis if no specific min
         if((option & ZERO) != 0) {
            pair[0] = Math.min(0, pair[0]);
         }

         if((option & GAPS) != 0) {
            double v = pair[0] - Math.abs(pair[1] * 0.05);

            if(pair[0] >= 0) {
               v = Math.max(0, v);
            }

            pair[0] = v;
         }
      }

      // use explicitly set max
      if(omax.get() != null) {
         pair[1] = omax.getDouble(Double.NaN);
      }
      else {
         if((option & ZERO) != 0) {
            pair[1] = Math.max(0, pair[1]);
         }

         if((option & GAPS) != 0) {
            double v = pair[1] + Math.abs(pair[1] * 0.05);

            if(pair[1] <= 0) {
               v = Math.min(0, v);
            }

            pair[1] = v;
         }
      }

      // if max and min is the same, set the min value is 0
      if(Math.abs(pair[0] - pair[1]) < 0.000001 && pair[0] != 0) {
         if(pair[0] > 0) {
            pair[0] = 0;
         }
         else {
            pair[1] = 0;
         }
      }

      // max < min
      if(pair[1] < pair[0] && (omin.get() == null || omax.get() == null)) {
         if(pair[0] > 0) {
            if(omin.get() == null) {
               pair[0] = 0;
            }
            else {
               pair[1] = pair[0] + 100;
            }
         }
         else {
            if(omin.get() == null) {
               pair[0] = pair[1] - 1;
            }
            else {
               pair[1] = pair[0] + 1;
            }
         }
      }

      // don't set max if shared max is not zero
      if(pair[0] == 0 && pair[1] == 0 && getMax0() == 0 && !empty) {
         // this scale could be shared and don't set it to a large number
         // otherwise other charts' scale may be stretched too large
         pair[1] = 1;
      }

      mergeMin0(pair[0]);
      mergeMax0(pair[1]);

      // find the smallest interval for getUnitCount
      if(cols.length > 0) {
         double[] sorted = getSortedUniqueMappedValues(data, cols[0]);
         double interval = mapValue(pair[1]) - mapValue(pair[0]);

         for(int i = 1; i < sorted.length; i++) {
            interval = Math.min(interval, sorted[i] - sorted[i - 1]);
         }

         // in facet, if a sub has no data, it should not cause others'
         // interval to be set to zero (meaning unknown)
         if(interval > 0) {
            intervalc.apply(interval);
         }
      }
   }

   /**
    * Initialize the values by copying the values from the other scale. This function
    * assumes the two scales have identical options (e.g. reverse).
    */
   public void copyValues(LinearScale scale) {
      ticks = null;
      minc.apply(scale.minc.get());
      maxc.apply(scale.maxc.get());
      intervalc.apply(scale.intervalc.get());
   }

   /**
    * The unit count of a linear scale is the number of smallest intervals on
    * the scale.
    */
   @Override
   @TernMethod
   public int getUnitCount() {
      double interval = intervalc.getDouble(0);

      if(interval == 0) {
         return 1;
      }

      return (int) Math.max(1, (getMax() - getMin()) / interval);
   }

   /**
    * Map a value to a logical position using this scale.
    * @return double represent the logical position of this value;
    */
   @Override
   @TernMethod
   public double mapValue(Object val) {
      if(val == null) {
         return Double.NaN;
      }

      if(!(val instanceof Number)) {
         return Double.NaN;
      }

      return mapValue(range.getValue(val));
   }

   /**
    * Performing any mapping from the original value to the scale value
    * (e.g. log or power).
    */
   protected double mapValue(double val) {
      return val;
   }

   /**
    * This is the reverse of mapValue.
    */
   @TernMethod
   public double unmap(double val) {
      return val;
   }

   /**
    * Get the minimum value on the scale.
    * @return the minimum value of the scale range in logical coordinate.
    */
   @Override
   @TernMethod
   public double getMin() {
      getTicks();
      return reversed ? getMax0() : getMin0();
   }

   /**
    * Get the minimum value on the scale.
    * @return the minimum value of the scale range in logical coordinate.
    */
   double getMin0() {
      Number min = minc.get();
      return min == null ? 0 : min.doubleValue();
   }

   /**
    * Set the minimum value of the scale.
    */
   @TernMethod
   public void setMin(Number min) {
      ticks = null;

      if(reversed) {
         setMax0(min, true);
      }
      else {
         setMin0(min, true);
      }
   }

   /**
    * Set the minimum value of the scale.
    * @param explicit true if the max is set explicitly and should override
    * the calculated value.
    */
   void setMin0(Number min, boolean explicit) {
      minc.set(min);

      if(explicit && min != null) {
         omin.set(min);
      }
   }

   /**
    * Get the maximum value on the scale.
    * @return the maximum value of the scale range in logical coordinate.
    */
   @Override
   @TernMethod
   public double getMax() {
      getTicks();
      return reversed ? getMin0() : getMax0();
   }

   /**
    * Get the maximum value on the scale.
    * @return the maximum value of the scale range in logical coordinate.
    */
   double getMax0() {
      Number max = maxc.get();
      return max == null ? 0 : max.doubleValue();
   }

   /**
    * Set the maximum value of the scale.
    */
   @TernMethod
   public void setMax(Number max) {
      ticks = null;

      if(reversed) {
         setMin0(max, true);
      }
      else {
         setMax0(max, true);
      }
   }

   /**
    * Set the maximum value of the scale.
    * @param explicit true if the max is set explicitly and should override
    * the calculated value.
    */
   void setMax0(Number max, boolean explicit) {
      maxc.set(max);

      if(explicit && max != null) {
         omax.set(max);
      }
   }

   /**
    * set the max if the current max is less than the parameter value.
    */
   private void mergeMax0(Number max) {
      maxc.apply(max);
   }

   /**
    * set the mmin if the current min is greater than the parameter value.
    */
   private void mergeMin0(Number min) {
      minc.apply(min);
   }

   /**
    * Get the tick positions. The values of the ticks are logical coordinate
    * position same as the values returned by map().
    * @return double[] represent the logical position of each tick.
    */
   @Override
   @TernMethod
   public synchronized double[] getTicks() {
      if(ticks == null) {
         double max = getMax0();
         double min = getMin0();
         boolean includeTicks = (getScaleOption() & TICKS) != 0;
         max = Math.max(max, min);
         int n = increment == null ? 5 : (int) ((max - min) / increment.doubleValue());
         // avoid zero if user set the increment is too large
         n = Math.max(n, 1);

         double umin = omin.getDouble(Double.NaN);
         double umax = omax.getDouble(Double.NaN);
         double smin, smax; // calculated min/max
         double inc;
         // force zero if value range span across negative and positive values.
         boolean includeZero = (getScaleOption() & ZERO) != 0 || min < 0 && max > 0;
         boolean alignZero = isAlignZero() && !Double.isNaN(umin) && !Double.isNaN(umax);
         // reverted scale
         boolean reversed = umin > umax;

         if(alignZero) {
            // if align zero, the umin/umax can be odd number, so treat them as data min/max
            // to get a nice increment.
            double[] tuple1 = reversed
               ? GTool.getNiceNumbers(umax, umin, Double.NaN, Double.NaN, n, includeZero)
               : GTool.getNiceNumbers(umin, umax, Double.NaN, Double.NaN, n, includeZero);
            // also try out using the min/max as explicit range.
            double[] tuple2 = reversed
               ? GTool.getNiceNumbers(umax, umin, umax, umin, n, includeZero)
               : GTool.getNiceNumbers(umin, umax, umin, umax, n, includeZero);
            // smin/smax must match the umin/umax for zero to line up
            smin = umin;
            smax = umax;
            double niceInc;
            double inc1 = tuple1[2], inc2 = tuple2[2];
            boolean negRange = umax > 0 && umin < 0;
            int decimal1 = GTool.getDecimalPlace(inc1);
            int decimal2 = GTool.getDecimalPlace(inc2);
            double n1 = (umax - umin) / inc1;
            double n2 = (umax - umin) / inc2;
            // if the number of ticks is in a reasonable range.
            boolean nOk1 = Math.abs(n1 - n) < n;
            boolean nOk2 = Math.abs(n2 - n) < n;

            // pick the nicer increment from tuple1 and tuple2.
            // prefer increment without user min/max since it results in better increment. (58112)
            if(decimal1 > decimal2) {
               niceInc = inc2;
            }
            else if(decimal1 < decimal2) {
               niceInc = inc1;
            }
            // pick increment that produce a nice number of ticks. (58112)
            else if(nOk1 && !nOk2) {
               niceInc = inc1;
            }
            else if(!nOk1 && nOk2) {
               niceInc = inc2;
            }
            // if increment with explicit min/max doesn't result in fraction, use it so the
            // scale range is filled. (58132)
            else if(umax % inc2 == 0 && umin % inc2 == 0) {
               niceInc = inc2;
            }
            else if(umax % inc1 == 0 && umin % inc1 == 0) {
               niceInc = inc1;
            }
            // if increment is larger than (negative) min or max, a fractional min/max will
            // be displayed on axis. choose the one that is smaller to avoid this problem. (58144)
            else if(negRange && inc1 > Math.min(umax, -umin)) {
               niceInc = inc2;
            }
            else if(negRange && inc2 > Math.min(umax, -umin)) {
               niceInc = inc1;
            }
            else {
               niceInc = inc1;
            }

            inc = reversed ? -niceInc : niceInc;
         }
         // min > max
         else if(reversed) {
            double[] tuple = GTool.getNiceNumbers(min, max, umax, umin, n, includeZero);
            smin = tuple[1];
            smax = tuple[0];
            inc = -tuple[2];
         }
         else {
            double[] tuple = GTool.getNiceNumbers(min, max, umin, umax, n, includeZero);
            smin = tuple[0];
            smax = tuple[1];
            inc = tuple[2];
         }

         // as the data may shift when user makes selection, if the data range drops below
         // the increment, the increment should not stretch the max. we give it some space
         // (50%) so the increment is still used if the diff is not too large.
         if(increment != null && increment.doubleValue() < smax * 1.5) {
            inc = increment.doubleValue();
         }

         // if range don't include ticks, use data min/max or user set values
         if(!includeTicks) {
            if(omin.get() == null) {
               smin = min;
            }

            if(omax.get() == null) {
               smax = max;
            }
         }

         // don't lose user set min/max
         if(omin.get() != null) {
            smin = omin.getDouble(0);
         }

         if(omax.get() != null) {
            smax = omax.getDouble(0);
         }

         double len0 = inc == 0 ? 0 : Math.abs((smax - smin) / inc);

         // sanity check
         while(len0 > 1000) {
            inc *= 2;
            len0 = Math.abs((smax - smin) / inc);
         }

         // ignore 6.0000000000001
         if(!GTool.isInteger(len0)) {
            len0 = includeTicks ? Math.ceil(len0) : Math.floor(len0);
         }
         else {
            len0 = Math.round(len0);
         }

         // if min/max fixed (by 2nd y axis), and the min is a fraction, and the increment
         // is integer, round the first tick to integer. we may want to force 0 on the tick
         // if it's in range too.
         boolean roundIt = !Double.isNaN(umin) && !Double.isNaN(umax) &&
            !GTool.isInteger(smin / inc);
         boolean round1st = roundIt && !GTool.isInteger(smin);
         int len = (int) len0 + 1;

         // rounding with 0 in range, make sure 0 is on the ticks
         if(roundIt && smin <= 0 && smax >= 0) {
            List<Double> tickList = new ArrayList<>();
            tickList.add(0.0);

            for(double tick = 0; tick > smin; tick -= inc) {
               double next = tick - inc;

               if(next < smin) {
                  boolean niceTick = smin == tick - inc / 2;

                  // if align zero ignore the min if min is an odd number not a multiple of inc.
                  // otherwise you get strange ticks (e.g. -37.5, 20, 0, 20, 40, 50).
                  // only ignore if there is at least one tick below 0.
                  if(alignZero && Math.abs(smin / inc) > 1 && !niceTick) {
                     break;
                  }

                  next = smin;
               }

               tickList.add(0, next);
            }

            for(double tick = 0; tick < smax; tick += inc) {
               double next = tick + inc;

               if(next > smax) {
                  boolean niceTick = smax == tick + inc / 2;

                  // if align zero ignore the max if max is an odd number not a multiple of inc.
                  // only ignore if there is at least one tick above 0.
                  if(alignZero && tick > 0 && !niceTick) {
                     break;
                  }

                  next = smax;
               }

               tickList.add(next);
            }

            len = tickList.size();
            ticks = tickList.stream().mapToDouble(v -> v).toArray();
         }
         // create ticks from range
         else {
            ticks = new double[len];

            for(int i = 0; i < len; i++) {
               if(i == 0) {
                  ticks[i] = smin;
               }
               else {
                  ticks[i] = ticks[i - 1] + inc;

                  // round, make sure other ticks on boundary
                  if(i == 1 && round1st) {
                     ticks[i] = Math.ceil(ticks[i] / inc) * inc;
                  }
               }
            }
         }

         // don't set min/max if the scale has not been initialized
         if(includeTicks && min != max) {
            maxc = new MaxContainer();
            minc = new MinContainer();

            setMin0(Math.min(min, ticks[0]), false);
            setMax0(Math.max(max, ticks[len - 1]), false);
         }
      }

      return getTicks0(ticks);
   }

   /**
    * Handles the reversed ticks.
    */
   double[] getTicks0(double[] ticks) {
      double[] arr;

      if(reversed) {
         arr = new double[ticks.length];

         for(int i = 0, n = ticks.length - 1; i < arr.length; i++) {
            arr[i] = ticks[n--];
         }
      }
      else {
         arr = ticks.clone();
      }

      return arr;
   }

   /**
    * Get the values at each tick.
    * @return Object[] represent values on each tick.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      double[] ticks = getTicks();
      Object[] values = new Object[ticks.length];

      for(int i = 0; i < ticks.length; i++) {
         values[i] = ticks[i];
      }

      return values;
   }

   /**
    * Get the tick increment.
    * @return the tick increment.
    */
   @TernMethod
   public Number getIncrement() {
      return increment;
   }

   /**
    * Set the tick increment.
    * @param increment the increment to set.
    */
   @TernMethod
   public void setIncrement(Number increment) {
      this.increment = increment;
   }

   /**
    * Get the tick minor increment.
    * @return Returns the minor increment.
    */
   @TernMethod
   public Number getMinorIncrement() {
      return minorinc;
   }

   /**
    * Set the tick minor increment.
    * @param minorinc the minor increment to set.
    */
   @TernMethod
   public void setMinorIncrement(Number minorinc) {
      this.minorinc = minorinc;
   }

   /**
    * Get the minor tick positions. The values of the minor ticks are logical
    * coordinate position same as the values returned by map().
    * @return the minor ticks, null means need not to paint minor ticks.
    */
   @TernMethod
   public double[] getMinorTicks() {
      double[] ticks = getTicks();
      int stepCount = 0;

      if(ticks.length < 2) {
         return null;
      }

      double inc = minorinc == null || minorinc.doubleValue() == 0 ?
         (ticks[1] - ticks[0]) / 2 : minorinc.doubleValue();

      stepCount = (int) ((ticks[1] - ticks[0]) / inc);

      if(stepCount < 0) {
         stepCount = -stepCount;
         inc = -inc;
      }

      double[] minorTicks = new double[stepCount * (ticks.length - 1)];

      for(int i = 0; i < ticks.length - 1; i++) {
         for(int j = 0; j < stepCount; j++) {
            minorTicks[i * stepCount + j] = ticks[i] + inc * (j + 1);
         }
      }

      return minorTicks;
   }

   /**
    * Set whether to share the range across an entire facet graph.
    */
   @TernMethod
   public void setSharedRange(boolean shared) {
      this.shared = shared;
   }

   /**
    * Check whether to share the range across an entire facet graph.
    */
   @TernMethod
   public boolean isSharedRange() {
      return shared;
   }

   /**
    * Clone this object.
    * @param shareRange true to share range, false otherwise.
    */
   public LinearScale clone(boolean shareRange) {
      LinearScale scale = (LinearScale) super.clone();
      scale.increment = increment;
      scale.minorinc = minorinc;

      if(!shareRange || !shared) {
         scale.minc = new MinContainer();
         scale.maxc = new MaxContainer();
         scale.intervalc = new MinContainer();
         scale.omin = omin.clone();
         scale.omax = omax.clone();
         scale.minc.set(omin.get());
         scale.maxc.set(omax.get());

         scale.alignZero = new AtomicBoolean(isAlignZero());
         scale.ticks = null;
      }

      return scale;
   }

   /**
    * Clone this object.
    */
   @Override
   public LinearScale clone() {
      return clone(true);
   }

   /**
    * Get the user defined maximum value on the scale.
    * @return the maximum value of the scale range in logical coordinate.
    */
   @TernMethod
   public Number getUserMax() {
      return omax.get();
   }

   /**
    * Get the user defined minimum value on the scale.
    * @return the minimum value of the scale range in logical coordinate.
    */
   @TernMethod
   public Number getUserMin() {
      return omin.get();
   }

   /**
    * Check whether this axis is aligned at zero position with a secondary axis.
    * @hidden
    */
   public boolean isAlignZero() {
      return alignZero.get();
   }

   /**
    * Set whether this axis is aligned at zero position with a secondary axis.
    * @hidden
    */
   public void setAlignZero(boolean alignZero) {
      this.alignZero.set(alignZero);
   }

   /**
    * Number container.
    */
   abstract static class NumContainer implements Serializable, Cloneable {
      public Number get() {
         return num;
      }

      public double getDouble(double def) {
         return num != null ? num.doubleValue() : def;
      }

      // set value
      public void set(Number num) {
         this.num = num;
      }

      // set or merge with existing value
      public void apply(Number num) {
         if(num != null) {
            this.num = getValue(this.num, num);
         }
         else {
            this.num = num;
         }
      }

      protected abstract Number getValue(Number num1, Number num2);

      @Override
      public NumContainer clone() {
         try {
            return (NumContainer) super.clone();
         }
         catch(Exception ex) {
            LOG.error("Failed to clone scale", ex);
         }

         return null;
      }

      @Override
      public boolean equals(Object obj) {
         try {
            NumContainer n2 = (NumContainer) obj;
            return n2 != null && Objects.equals(num, n2.num);
         }
         catch(ClassCastException ex) {
            return false;
         }
      }

      private Number num;
   }

   /**
    * Minimum value container.
    */
   static class MinContainer extends NumContainer {
      @Override
      protected Number getValue(Number num1, Number num2) {
         if(num1 == null) {
            return num2;
         }
         else if(num2 == null) {
            return num1;
         }
         else {
            double v1 = num1.doubleValue();
            double v2 = num2.doubleValue();
            double result = v1 - v2;
            return result <= 0 ? num1 : num2;
         }
      }
   }

   /**
    * Maxinum value container.
    */
   static class MaxContainer extends NumContainer {
      @Override
      protected Number getValue(Number num1, Number num2) {
         if(num1 == null) {
            return num2;
         }
         else if(num2 == null) {
            return num1;
         }
         else {
            double v1 = num1.doubleValue();
            double v2 = num2.doubleValue();
            double result = v1 - v2;
            return result >= 0 ? num1 : num2;
         }
      }
   }

   @Override
   public String toString() {
      return super.toString() + ",min:" + getMin0() + ",max:" + getMax0() + "," + range;
   }

   /**
    * Create linear scale by shallow clone of this linear scale. The returned
    * result is always an instance of LinearScale (sub class ignored).
    * @hidden
    */
   public LinearScale copyLinearScale() {
      // copy scale attributes
      LinearScale scale = new LinearScale(getDataFields());

      scale.setFields(getFields());
      scale.setAxisSpec(getAxisSpec());
      scale.setScaleOption(getScaleOption());
      // copy linear scale attributes
      scale.ticks = ticks;
      scale.minc = minc;
      scale.maxc = maxc;
      scale.omax = omax;
      scale.omin = omin;
      scale.increment = increment;
      scale.minorinc = minorinc;
      scale.range = range;
      scale.reversed = reversed;

      return scale;
   }

   /**
    * Get the sorted and mapped numeric values as an array.
    */
   private double[] getSortedUniqueMappedValues(DataSet chart, String col) {
      DoubleSortedSet uniqValues = new DoubleAVLTreeSet();

      for(int i = 0; i < chart.getRowCount(); i++) {
         Object val = chart.getData(col, i);

         // ignore null values otherwise brushing causes the range to change
         // (value and __all__value). (58589)
         if(val != null) {
            uniqValues.add(mapValue(range.getValue(val)));
         }
      }

      return uniqValues.toDoubleArray();
   }

   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
      intervalc = new MinContainer();
   }

   public boolean equalsStrict(LinearScale scale) {
      return super.equals(scale) &&
            this.getMin0() == scale.getMin0() &&
            this.getMax0() == scale.getMax0() &&
            Arrays.equals(ticks, scale.ticks) &&
            CoreTool.equals(this.getAxisSpec(), scale.getAxisSpec());
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      try {
         LinearScale scale = (LinearScale) obj;
         return Objects.equals(omax, scale.omax) && Objects.equals(omin, scale.omin) &&
            Objects.equals(increment, scale.increment) &&
            Objects.equals(minorinc, scale.minorinc) &&
            reversed == scale.reversed && shared == scale.shared &&
            Objects.equals(range, scale.range) &&
            alignZero.get() == scale.alignZero.get();
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   private static final long serialVersionUID = 1L;

   double[] ticks = null;
   // in facet, a coordinate will be cloned for times, so as the contained
   // scales. We use number container to share min/max among the cloned
   // scales, then the graph will look better with the same max/min values
   NumContainer minc = new MinContainer();
   NumContainer maxc = new MaxContainer();

   // min/max is shared across facet to support alignZero in facet. they are not merged
   // but shared so setMin/setMax in RectCoord.alignZero would apply to all shared
   // primary axes. (55280)
   private NumContainer omax = new MaxContainer();
   private NumContainer omin = new MinContainer();

   private Number increment = null;
   private Number minorinc = null;
   private boolean reversed = false;
   private ScaleRange range = new LinearRange();
   private boolean shared = true;
   private AtomicBoolean alignZero = new AtomicBoolean(false);
   // smallest interval between values
   private transient NumContainer intervalc = new MinContainer();

   private static final Logger LOG = LoggerFactory.getLogger(LinearScale.class);
}
