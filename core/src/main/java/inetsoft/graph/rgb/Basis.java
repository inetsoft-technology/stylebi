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

import java.util.function.Function;

public class Basis {
   public static double basis(double t1, double v0, double v1, double v2, double v3) {
      double t2 = t1 * t1;
      double t3 = t2 * t1;
      return ((1 - 3 * t1 + 3 * t2 - t3) * v0
              + (4 - 6 * t2 + 3 * t3) * v1
              + (1 + 3 * t1 + 3 * t2 - 3 * t3) * v2
              + t3 * v3) / 6;
   }

   public static Function<Double, Double> interpolateBasis(final double[] values) {
      int n = values.length - 1;

      return (Double t) -> {
         int i = 0;

         if(t <= 0) {
            t = 0.0;
            i = 0;
         }
         else if(t >= 1) {
            t = 1.0;
            i = n - 1;
         }
         else {
            i = (int) Math.floor(t * n);
         }

         double v1 = values[i];
         double v2 = values[i + 1];
         double v0 = i > 0 ? values[i - 1] : (2 * v1 - v2);
         double v3 = i < n - 1 ? values[i + 2] : (2 * v2 - v1);
         return basis((t - i * 1.0 / n) * n, v0, v1, v2, v3);
      };
   }
}
