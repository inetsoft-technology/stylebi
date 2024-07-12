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

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RgbTest {
   void setUp() {
   }

   @Test
   void testResult() {
      Color[] colors = //new Color[] {Color.red, Color.blue};
         new Color[] {new Color(  222, 235, 247), new Color(158,202,225), new Color(  49,130,189)};

      Function<Double, Color> gen = Rgb.rgbBasis.apply(colors);
      Color result0 = gen.apply(0.0);
      Color result05 = gen.apply(0.5);
      Color result1 = gen.apply(1.0);
      assertEquals(new Color(222, 235, 247), result0);
      assertEquals(new Color(150, 195, 222), result05);
      assertEquals(new Color(49, 130, 189), result1);
   }
}
