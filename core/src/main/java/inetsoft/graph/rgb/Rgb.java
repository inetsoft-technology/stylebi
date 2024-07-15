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
package inetsoft.graph.rgb;

import java.awt.*;
import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Rgb implements Serializable {
   public static BiFunction<Color, Color, Function<Double, Color>> interpolateRgb() {
      Function<Double, BiFunction<Color, Color, Function<Double, Color>>> f = (Double y) -> {
         BiFunction<Double, Double, Function<Double, Double>> color = ColorUtil.gamma(y);

         BiFunction<Color, Color, Function<Double, Color>> rgb = (Color start, Color end) -> {
            Function<Double, Double> r = color.apply((double) start.getRed(),
                                                     (double) end.getRed());
            Function<Double, Double> g = color.apply((double) start.getGreen(),
                                                     (double) end.getGreen());
            Function<Double, Double> b = color.apply((double) start.getBlue(),
                                                     (double) end.getBlue());
            Function<Double, Double> opacity = ColorUtil.nogamma(start.getAlpha(), end.getAlpha());

            return (Double t) -> {
               int red = r.apply(t).intValue();
               int green = g.apply(t).intValue();
               int blue = b.apply(t).intValue();
               int alpha = opacity.apply(t).intValue();
               return new Color(red, green, blue, alpha);
            };
         };

         //rgb.gamma = rgbGamma;
         return rgb;
      };

      return f.apply((double) 1);
   }

   public static Function<Color[], Function<Double, Color>> rgbSpline(
      Function<double[], Function<Double, Double>> spline)
   {
      return (Color[] colors) -> {
         int n = colors.length;
         double[] r = new double[n];
         double[] g = new double[n];
         double[] b = new double[n];

         for(int i = 0; i < n; ++i) {
            Color color = colors[i];
            r[i] = color.getRed();
            g[i] = color.getGreen();
            b[i] = color.getBlue();
         }

         Function<Double, Double> r_func = spline.apply(r);
         Function<Double, Double> g_func = spline.apply(g);
         Function<Double, Double> b_func = spline.apply(b);

         return (Double t) -> {
            double rval = r_func.apply(t);
            double gval = g_func.apply(t);
            double bval = b_func.apply(t);
            return new Color((int) rval, (int) gval, (int) bval);
         };
      };
   }

   public static final Function<Color[], Function<Double, Color>> rgbBasis =
      rgbSpline(Basis::interpolateBasis);
   public static final Function<Color[], Function<Double, Color>> rgbBasisClosed =
      rgbSpline(BasisClosed::basisClosed);
}
