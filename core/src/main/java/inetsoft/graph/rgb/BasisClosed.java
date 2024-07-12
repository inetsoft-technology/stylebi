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
package inetsoft.graph.rgb;

import java.util.function.Function;

public class BasisClosed {
   public static Function<Double, Double> basisClosed(double[] values) {
      int n = values.length;

      return (Double t) -> {
         int i = (int) Math.floor(((t %= 1) < 0 ? ++t : t) * n);
         double v0 = values[(i + n - 1) % n];
         double v1 = values[i % n];
         double v2 = values[(i + 1) % n];
         double v3 = values[(i + 2) % n];
         return Basis.basis((t - i / n) * n, v0, v1, v2, v3);
      };
  }
}
