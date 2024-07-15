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
import com.inetsoft.build.tern.TernMethod;

import java.awt.*;

/**
 * This class defines a continuous color frame that returns colors varying on
 * the Saturation of the color.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=SaturationColorFrame")
public class SaturationColorFrame extends HSLColorFrame {
   /**
    * Create a color frame. The field needs to be set by calling setField.
    */
   public SaturationColorFrame() {
      setDefaultColor(new Color(-8013589));
   }

   /**
    * Create a color frame.
    * @param field field to get value to map to color.
    */
   public SaturationColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the color.
    * @param ratio the value between 0 and 1,
    * and is the position of the value in a linear scale.
    */
   @Override
   @TernMethod
   public Color getColor(double ratio) {
      int alpha = (int) (getAlpha(ratio) * 255);
      ratio = MIN + (MAX - MIN) * ratio;

      int[] rgb = hslToRGB(hue, ratio, brightness);
      return process(new Color(rgb[0], rgb[1], rgb[2], alpha), getBrightness());
   }

   private static final double MIN = 0.1;
   private static final double MAX = 1;
   private static final long serialVersionUID = 1L;
}
