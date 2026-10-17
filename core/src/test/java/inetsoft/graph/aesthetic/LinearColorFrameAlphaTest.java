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
package inetsoft.graph.aesthetic;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78735: an out-of-range max/min alpha set from script must not crash colour generation. */
@Tag("core")
class LinearColorFrameAlphaTest {
   @Test
   void maxAlphaAboveOneIsClamped() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setMaxAlpha(50);

      for(int i = 0; i <= 4; i++) {
         final double ratio = i / 4.0;
         Color color = assertDoesNotThrow(() -> frame.getColor(ratio));
         assertTrue(color.getAlpha() >= 0 && color.getAlpha() <= 255);
      }

      assertEquals(255, frame.getColor(1).getAlpha());
   }

   @Test
   void negativeMinAlphaIsClamped() {
      BrightnessColorFrame frame = new BrightnessColorFrame();
      frame.setMinAlpha(-0.5);

      assertDoesNotThrow(() -> frame.getColor(0));
      assertEquals(0, frame.getColor(0).getAlpha());
   }
}
