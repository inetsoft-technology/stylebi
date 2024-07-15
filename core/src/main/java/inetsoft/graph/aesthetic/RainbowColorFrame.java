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

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernConstructor;

/**
 * This class defines a color frame for continuous numeric values using the
 * rainbow color space.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=RainbowColorFrame")
public class RainbowColorFrame extends RGBCubeColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public RainbowColorFrame() {
      super(COLORS);
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public RainbowColorFrame(String field) {
      this();
      setField(field);
   }

   private static double[][] COLORS = {
      {0, 0, 1},
      {0, 1, 1},
      {0, 1, 0},
      {1, 1, 0},
      {1, 0, 0}
   };

   private static final long serialVersionUID = 1L;
}
