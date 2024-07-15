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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;

/**
 * This class defines a color frame for continuous numeric values using the
 * heat color space.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=HeatColorFrame")
public class HeatColorFrame extends RGBCubeColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public HeatColorFrame() {
      super(COLORS);
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public HeatColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the minimum ratio to use in the color path.
    */
   @Override
   @TernMethod
   protected double getMinRatio() {
      return 0.2;
   }

   /**
    * Get the maximum ratio to use in the color path.
    */
   @Override
   @TernMethod
   protected double getMaxRatio() {
      return 0.8;
   }

   private static double[][] COLORS = {
      {0, 0, 0},
      {1, 0.5, 0},
      {1, 1, 1}
   };
   private static final long serialVersionUID = 1L;
}
