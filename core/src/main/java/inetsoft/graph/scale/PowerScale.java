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

/**
 * A power scale maps numeric values by raising it to the power of a specified
 * number.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=PowerScale")
public class PowerScale extends LinearScale {
   /**
    * Default constructor.
    */
   public PowerScale() {
   }

   /**
    * Create a scale with user specified min and max.
    */
   public PowerScale(double min, double max) {
      super(min, max);
   }

   /**
    * Create a scale for the specified columns.
    */
   @TernConstructor
   public PowerScale(String... fields) {
      super(fields);
   }

   /**
    * Set the power to raise to. The default is 0.5.
    */
   @TernMethod
   public void setExponent(double power) {
      this.power = power;
   }

   /**
    * Get the power to raise to. The default is 0.5.
    */
   @TernMethod
   public double getExponent() {
      return power;
   }

   /**
    * Return the power value.
    */
   @Override
   protected double mapValue(double val) {
      // NaN is no value (the row is skipped); getPowerValue turns it into 0 for add()
      return Double.isNaN(val) ? Double.NaN : getPowerValue(val, power);
   }

   /**
    * This is the reverse of mapValue.
    */
   @Override
   @TernMethod
   public double unmap(double val) {
      return getPowerValue(val, 1 / power);
   }

   /**
    * Add two mapped values to get the total value.
    * @param v1 the specified mapped value a.
    * @param v2 the specified mapped value b.
    * @return the new total value.
    */
   @Override
   @TernMethod
   public double add(double v1, double v2) {
      v1 = getPowerValue(v1, 1 / power);
      v2 = getPowerValue(v2, 1 / power);
      return map(Double.valueOf(v1 + v2));
   }

   /**
    * Get the value v to the power of power.
    */
   private double getPowerValue(double v, double power) {
      int sign = 1;

      if(v < 0) {
         v = -v;
         sign = -1;
      }
      else if(Double.isNaN(v)) {
	 v = 0;
      }

      return sign * Math.pow(v, power);
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
         double min = getMin0();
         double max = getMax0();
	 boolean includeTicks = (getScaleOption() & TICKS) != 0;
	 boolean inited = min != max;
         min = getPowerValue(min, power);
         max = getPowerValue(max, power);

         // have not being initialized, don't set min/max or it will not be
         // initialized in visual frame
	 if(!inited) {
	    // return min because this condition could also be caused by
	    // user explicitly set min/max and set should display the tick
	    return new double[] {min};
         }

	 maxc = new MaxContainer(); // do not share log max
	 minc = new MinContainer(); // do not share log min

	 if(includeTicks) {
            if(getUserMax() == null) {
               // 3.0001
               max = Math.ceil(max - 0.0001);
            }

            if(getUserMin() == null) {
               // 0.99999
               min = Math.floor(min + 0.0001);
            }
         }

	 max = max == min ? max + 1 : max; // at least two ticks

         if(max < 0 && (getScaleOption() & ZERO) != 0) {
            max = 0;
         }

         setMax0(max, false);
         setMin0(min, false);

         // the range is counted in double, so a wide range is thinned to about 7 ticks
         // instead of rejected. Only a range that cannot be split into ticks is rejected,
         // as before: a bound that overflowed (e.g. a huge exponent) or a range reversed by
         // two or more (e.g. a negative exponent)
         double n = Math.ceil(max - min) + 1;

         if(!Double.isFinite(n) || n < 0) {
            throw new RuntimeException("Exponent is too large, ignored.");
         }

         // same as Math.round(n / 6.0), without its overflow at Long.MAX_VALUE
         double inc = Math.max(1, Math.floor(n / 6.0 + 0.5));
         // a user bound, or the data range when TICKS is off, is not rounded to inc:
         // it is the end tick and stays the range of the scale
         boolean clampMax = getUserMax() != null || !includeTicks;
         boolean clampMin = getUserMin() != null || !includeTicks;
         double bmax = max;
         double bmin = min;

         min = Math.floor(min / inc) * inc;
         max = Math.ceil(max / inc) * inc;
         // min and max are multiples of inc: rint, not floor, so a wide range does not lose
         // its last tick to a quotient such as 6.9999999
         double count = Math.rint(Math.abs(max - min) / inc) + 1;

         // rounding a bound near Double.MAX_VALUE to inc can overflow
         if(!(count <= 1000)) {
            throw new RuntimeException("Exponent is too large, ignored.");
         }

         ticks = new double[(int) count];

         if(bmin > bmax) {
            // a range reversed by less than two (e.g. a negative exponent), at most two
            // ticks: kept as it was
            if(includeTicks) {
               setMax0(max, false);
               setMin0(min, false);
            }

            for(int i = 0; i < ticks.length; i++) {
               ticks[i] = (min > max ? -i : i) * inc + min;

               // enforce user specified max
               if(clampMax) {
                  ticks[i] = (min > max) ? Math.max(ticks[i], max)
                     : Math.min(ticks[i], max);
               }
            }
         }
         else {
            // the range rounded to inc is the scale range only on a side with no bound
            if(!clampMax) {
               setMax0(max, false);
            }

            if(!clampMin) {
               setMin0(min, false);
            }

            for(int i = 0; i < ticks.length; i++) {
               ticks[i] = i * inc + min;
            }

            // only the end ticks can pass a bound, and they are set to the bound itself, the
            // same double as getMax()/getMin(). Without a bound the last tick is max itself
            // (+ 0.0 turns -0.0 into 0), as a sum can drift from it on a wide range
            ticks[ticks.length - 1] = clampMax ? bmax : max + 0.0;

            if(clampMin) {
               ticks[0] = bmin;
            }
         }

         if((getScaleOption() & ZERO) != 0) {
            LogScale.addZero(ticks);
         }

         if(bmin <= bmax) {
            ticks = mergeBoundTicks(ticks, inc, clampMin, clampMax);
         }
      }

      return getTicks0(ticks);
   }

   /**
    * Drop the rounded tick next to a bound end tick when it is closer than half a step to
    * the bound, so the two don't get the same label. The end ticks are kept.
    */
   private static double[] mergeBoundTicks(double[] ticks, double inc, boolean clampMin,
                                           boolean clampMax)
   {
      int last = ticks.length - 1;

      if(clampMax && ticks.length > 2 &&
         isCloseTick(ticks[last] - ticks[last - 1], ticks[last - 1], inc))
      {
         ticks = removeTick(ticks, last - 1);
      }

      if(clampMin && ticks.length > 2 && isCloseTick(ticks[1] - ticks[0], ticks[1], inc)) {
         ticks = removeTick(ticks, 1);
      }

      return ticks;
   }

   /**
    * Check if the tick next to a bound is too close to it to be kept. A 0 tick is kept, as
    * on LinearScale, unless the bound is 0 within the tolerance of GTool.isInteger.
    * @param gap the distance from the tick to the bound.
    */
   private static boolean isCloseTick(double gap, double tick, double inc) {
      return gap < inc / 2 && (tick != 0 || gap < inc * 1e-6);
   }

   private static double[] removeTick(double[] ticks, int idx) {
      double[] arr = new double[ticks.length - 1];
      System.arraycopy(ticks, 0, arr, 0, idx);
      System.arraycopy(ticks, idx + 1, arr, idx, arr.length - idx);
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
      Double[] values = new Double[ticks.length];

      for(int i = 0; i < ticks.length; i++) {
         values[i] = Double.valueOf(getPowerValue(ticks[i], 1 / power));
      }

      return values;
   }

   /**
    * Get the minor tick positions. They split the values between two ticks evenly.
    */
   @Override
   @TernMethod
   public double[] getMinorTicks() {
      return LogScale.getMinorTicks(this, 1 / power);
   }

   private double power = 0.5;
   private static final long serialVersionUID = 1L;
}
