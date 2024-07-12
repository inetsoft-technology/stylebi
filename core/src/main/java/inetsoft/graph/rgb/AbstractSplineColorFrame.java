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

import java.awt.*;
import java.util.stream.IntStream;

/**
 * This class defines a color frame for numeric values using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public abstract class AbstractSplineColorFrame extends SplineColorFrame {
   /**
    * Create a color frame with the most number of colors in the ramp.
    */
   protected AbstractSplineColorFrame() {
      this(-1);
   }

   /**
    * Create a color frame from color ramps.
    * @param ramp the index of ramps (from getColorRamps) to use for creating color ramp.
    */
   protected AbstractSplineColorFrame(int ramp) {
      String[] ramps = getColorRamps();
      int idx = (ramp < 0) ? ramps.length - 1 : ramp;
      String s = ramps[idx];
      Color[] colors = IntStream.range(0, s.length() / 6)
              .mapToObj(i -> new Color(Integer.parseInt(s.substring(i * 6, (i + 1) * 6), 16)))
              .toArray(Color[]::new);

      setColors(colors);
   }

   /**
    * Get color ramps for creating color frame.
    */
   protected abstract String[] getColorRamps();
}
