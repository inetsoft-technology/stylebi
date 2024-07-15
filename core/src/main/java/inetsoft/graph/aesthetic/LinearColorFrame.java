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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;

import java.awt.*;
import java.lang.reflect.Method;

/**
 * This class defines a color frame for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class LinearColorFrame extends ColorFrame {
   /**
    * Get a color at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   public abstract Color getColor(double ratio);

   /**
    * Get the alpha for the color at the relative scale.
    */
   protected double getAlpha(double ratio) {
      return Math.max(0, minAlpha + ratio * (maxAlpha - minAlpha));
   }

   /**
    * Get a color for the specified value.
    */
   @Override
   @TernMethod
   public Color getColor(Object val) {
      Scale scale = getScale();

      if(scale == null) {
         return getColor(1);
      }

      double v = scale.map(val);

      if(Double.isNaN(v)) {
         return process(null, getBrightness());
      }

      double min = scale.getMin();
      double max = scale.getMax();

      // sanity check. treemap group total could be 0 (from null in child). (51178)
      // account for reversed scale (51313).
      if(v < min && max > min) {
         return getColor(0);
      }

      return getColor(Math.max(0, Math.min(1, (v - min) / (max - min))));
   }

   /**
    * Get the color for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      Object obj = data.getData(getField(), row);
      return getColor(obj);
   }

   /**
    * Legend is always visible.
    */
   @Override
   boolean isMultiItem(Method getter) throws Exception {
      return true;
   }

   /**
    * Get the alpha for the max value color.
    */
   @TernMethod
   public double getMaxAlpha() {
      return maxAlpha;
   }

   /**
    * Set the alpha for the max value color. the alpha is calculated proportional to the value
    * in the scale based on min and max alpha.
    */
   @TernMethod
   public void setMaxAlpha(double maxAlpha) {
      this.maxAlpha = maxAlpha;
   }

   /**
    * Get the alpha for the min value color.
    */
   @TernMethod
   public double getMinAlpha() {
      return minAlpha;
   }

   /**
    * Set the alpha for the min value color.
    */
   @TernMethod
   public void setMinAlpha(double minAlpha) {
      this.minAlpha = minAlpha;
   }

   private double maxAlpha = 1;
   private double minAlpha = 1;

}
