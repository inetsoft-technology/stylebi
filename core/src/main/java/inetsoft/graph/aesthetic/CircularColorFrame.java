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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

/**
 * This class defines a color frame for continuous numeric values using the
 * circular color space.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CircularColorFrame")
public class CircularColorFrame extends RGBCubeColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public CircularColorFrame() {
      super(COLORS);
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public CircularColorFrame(String field) {
      this();
      setField(field);
   }

   private static double[][] COLORS = {
      {0, 0, 1},
      {0, 1, 0},
      {1, 1, 0},
      {1, 0, 0},
      {0, 0, 1}
   };

   private static final long serialVersionUID = 1L;
}
