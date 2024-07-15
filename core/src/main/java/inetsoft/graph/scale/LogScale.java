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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.*;
import inetsoft.graph.internal.GTool;

import java.util.ArrayList;

/**
 * A log scale maps numeric values on a logarithmic scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LogScale")
public class LogScale extends LinearScale {
   /**
    * Default constructor.
    */
   public LogScale() {
   }

   /**
    * Create a scale with user specified min and max.
    */
   public LogScale(double min, double max) {
      super(min, max);
   }

   /**
    * Create a scale for the specified columns.
    */
   @TernConstructor
   public LogScale(String... fields) {
      super(fields);
   }

   /**
    * Set the base of the log. The default is 10.
    */
   @TernMethod
   public void setBase(int base) {
      if(base <= 0) {
         throw new RuntimeException("Base must be a positive number: " + base);
      }

      this.base = base;
   }

   /**
    * Get the base of the log.
    */
   @TernMethod
   public int getBase() {
      return base;
   }

   /**
    * Return the log value.
    */
   @Override
   protected double mapValue(double val) {
      return getLogValue(val);
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
      v1 = (v1 == 0 || Double.isNaN(v1)) ? 0 : getPowerValue(v1);
      v2 = (v2 == 0 || Double.isNaN(v2)) ? 0 : getPowerValue(v2);
      return map(Double.valueOf(v1 + v2));
   }

   /**
    * Get the log value.
    */
   private double getLogValue(double v) {
      int sign = 1;

      if(v < 0) {
         v = -v;
         sign = -1;
      }

      if(v >= 0 && v <= 1) {
         v = 1;
      }

      double log = sign * Math.log(v) / Math.log(base);

      // avoid 2.99999
      if(GTool.isInteger(log)) {
         log = Math.round(log);
      }

      return log;
   }

   /**
    * This is the reverse of mapValue.
    */
   @Override
   @TernMethod
   public double unmap(double val) {
      return getPowerValue(val);
   }

   /**
    * Get the base to the power of v.
    */
   private double getPowerValue(double v) {
      int sign = 1;

      if(v < 0) {
         v = -v;
         sign = -1;
      }

      return sign * Math.pow(base, v);
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
	 min = getLogValue(min);
	 max = getLogValue(max);

         // have not being initialized, don't set min/max or it will not be
         // initialized in visual frame
	 if(!inited) {
	    // return min because this condition could also be caused by
	    // user explicitly set min/max and set should display the tick
	    return new double[] {min};
         }

	 if(includeTicks) {
	    if(getUserMax() == null) {
	       // 3.0000001
	       max = Math.ceil(max - 0.0001);
	    }

            if(getUserMin() == null) {
               // 1.9999999
               min = Math.floor(min + 0.0001);
            }
         }

	 maxc = new MaxContainer(); // do not share log max
	 minc = new MinContainer(); // do not share log min
	 max = max == min ? max + 1 : max; // at least two ticks

         if(max < 0 && (getScaleOption() & ZERO) != 0) {
            max = 0;
         }

	 setMax0(Double.valueOf(max), false);
	 setMin0(Double.valueOf(min), false);

         int n = (int) Math.max(Math.ceil(Math.abs(max - min)) + 1, 2);
         ticks = new double[n];

         for(int i = 0; i < ticks.length; i++) {
            ticks[i] = (min > max ? -i : i) + min;

            // enforce user specified max
	    if(getUserMax() != null || !includeTicks) {
               ticks[i] = (min > max) ? Math.max(ticks[i], max)
                  : Math.min(ticks[i], max);
            }
         }

         if((getScaleOption() & ZERO) != 0) {
            addZero(ticks);
         }

	 if(includeTicks && getUserMax() == null) {
            setMax0(ticks[ticks.length - 1], false);
         }
      }

      return getTicks0(ticks);
   }

   /**
    * Make sure zero is on ticks if the range covers zero.
    */
   static void addZero(double[] ticks) {
      if(ticks.length <= 2) {
         return;
      }

      double min = ticks[0];
      double max = ticks[ticks.length - 1];

      if(min != 0 && max != 0 && (min < 0 && max > 0 || min > 0 && max < 0)) {
	 double zero_diff = Integer.MAX_VALUE;
	 int zero_idx = -1; // track zero position

         // don't replace the min/max value
         for(int i = 1; i < ticks.length - 1; i++) {
            if(Math.abs(ticks[i]) < zero_diff) {
               zero_diff = Math.abs(ticks[i]);
               zero_idx = i;
            }
         }

         // make sure a zero is on the ticks if the range has both positive
         // and negative
         if(zero_diff != 0) {
            ticks[zero_idx] = 0;
         }
      }
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
         values[i] = Double.valueOf(getPowerValue(ticks[i]));
      }

      return values;
   }

   /**
    * Minor ticks are not supported by log scale.
    */
   @Override
   @TernMethod
   public double[] getMinorTicks() {
      return getMinorTicks(this, base);
   }

   /**
    * Get minor ticks.
    * @param ticks major ticks.
    * @param n the number of minor ticks between the major ticks.
    */
   static double[] getMinorTicks(LinearScale scale, double base) {
      Double[] ticks = (Double[]) scale.getValues();

      if(ticks.length < 2) {
         return null;
      }

      Number minorinc = scale.getMinorIncrement();
      double inc = (minorinc == null) ? base / 2 : minorinc.doubleValue();

      if(inc == 0) {
         return null;
      }

      int n = (int) Math.ceil(base / inc);

      ArrayList<Double> mticks = new ArrayList();

      for(int i = 0; i < ticks.length - 1; i++) {
         inc = (ticks[i + 1] - ticks[i]) / n;

         for(double v = ticks[i] + inc; v < ticks[i + 1]; v += inc) {
            mticks.add(v);
         }
      }

      double[] arr = new double[mticks.size()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = scale.map(mticks.get(i));
      }

      return arr;
   }

   private int base = 10;
   private static final long serialVersionUID = 1L;
}
