/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report;

import inetsoft.report.internal.Util;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #80975: decodeLineStyle must accept every name Util.getLineStyleName produces. */
@Tag("core")
class StyleFontLineStyleTest {
   @Test
   void decodeLineStyleRoundTripsEveryNamedStyle() {
      int[] styles = {
         StyleConstants.NO_BORDER, StyleConstants.ULTRA_THIN_LINE, StyleConstants.THIN_THIN_LINE,
         StyleConstants.THIN_LINE, StyleConstants.MEDIUM_LINE, StyleConstants.THICK_LINE,
         StyleConstants.DOUBLE_LINE, StyleConstants.RAISED_3D, StyleConstants.LOWERED_3D,
         StyleConstants.DOUBLE_3D_RAISED, StyleConstants.DOUBLE_3D_LOWERED,
         StyleConstants.DOT_LINE, StyleConstants.DASH_LINE, StyleConstants.MEDIUM_DASH,
         StyleConstants.LARGE_DASH
      };

      for(int style : styles) {
         String name = Util.getLineStyleName(style);
         assertEquals(style, StyleFont.decodeLineStyle(name), name);
      }
   }

   @Test
   void decodeLineStyleAcceptsDouble3DLowered() {
      assertEquals(StyleConstants.DOUBLE_3D_LOWERED,
                   StyleFont.decodeLineStyle("DOUBLE_3D_LOWERED"));
   }
}
