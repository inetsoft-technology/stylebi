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

import java.util.function.BiFunction;
import java.util.function.Function;

public class ColorUtil {
   public static Function<Double, Double> linear(double a, double d) {
      return (Double t) -> {
         return a + t * d;
      };
   }

   public static Function<Double, Double> exponential(double a, double b, double y) {
      double a2 = Math.pow(a, y);
      double b2 = Math.pow(b, y) - a2;
      double y2 = 1 / y;

      return (t) -> {
         return Math.pow(a2 + t * b2, y2);
      };
   }

   public static Function<Double, Double> constant(double x) {
      return (t) -> x;
   }

   public static Function<Double, Double> hue(double a, double b) {
      double d = b - a;
      return d != 0 ? linear(a, d > 180 || d < -180 ? d - 360 * Math.round(d / 360) : d)
         : constant(Double.isNaN(a) ? b : a);
   }

   public static BiFunction<Double, Double, Function<Double, Double>> gamma(double y) {
      return y == 1
         ? (Double a, Double b) -> nogamma(a, b)
         : (Double a, Double b) -> {
         return b - a != 0 ? exponential(a, b, y) : constant(Double.isNaN(a) ? b : a);
      };
   }

   public static Function<Double, Double> nogamma(double a, double b) {
      double d = b - a;
      return d != 0 ? linear(a, d) : constant(Double.isNaN(a) ? b : a);
   }
}
