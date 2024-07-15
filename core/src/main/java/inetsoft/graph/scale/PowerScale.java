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
      return getPowerValue(val, power);
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

         int n = (int) Math.ceil(max - min) + 1;
         int inc = Math.max(1, (int) Math.round(n / 6.0));

         if(n < 0 || n > 1000) {
            throw new RuntimeException("Exponent is too large, ignored.");
         }

         n = (int) Math.ceil(n / (double) inc);
         min = Math.floor(min / inc) * inc;
         max = Math.ceil(max / inc) * inc;
         n = (int) Math.floor(Math.abs(max - min) / inc + 1);
         ticks = new double[n];

         if(includeTicks) {
            setMax0(max, false);
            setMin0(min, false);
         }

         for(int i = 0; i < ticks.length; i++) {
            ticks[i] = (min > max ? -i : i) * inc + min;

            // enforce user specified max
	    if(getUserMax() != null || !includeTicks) {
               ticks[i] = (min > max) ? Math.max(ticks[i], max)
                  : Math.min(ticks[i], max);
            }
         }

         if((getScaleOption() & ZERO) != 0) {
            LogScale.addZero(ticks);
         }
      }

      return getTicks0(ticks);
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
    * Minor ticks are not supported by power scale.
    */
   @Override
   @TernMethod
   public double[] getMinorTicks() {
      return LogScale.getMinorTicks(this, 1 / power);
   }

   private double power = 0.5;
   private static final long serialVersionUID = 1L;
}
