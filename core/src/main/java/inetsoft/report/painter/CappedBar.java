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
package inetsoft.report.painter;

import java.awt.*;

/**
 * Draws a bar that is cut short because its value is an outlier, with a gap before its last few
 * pixels, so a bar that runs off the scale does not look like one that exactly reaches it.
 */
final class CappedBar {
   // a bar shorter than this is drawn whole, there is no room for the break
   private static final int MIN_LENGTH = 14;
   private static final int GAP = 3;
   private static final int STUB = 4;

   private CappedBar() {
   }

   /**
    * @param g      the graphics to fill in, with the bar's color set.
    * @param x      the edge the bar grows from.
    * @param y      the top of the bar.
    * @param length the length of the bar, in pixels.
    * @param height the height of the bar.
    * @param right  true if the bar grows to the right of x, false for the left.
    */
   static void fill(Graphics g, int x, int y, int length, int height, boolean right) {
      if(length < MIN_LENGTH) {
         g.fillRect(right ? x : x - length, y, length, height);
         return;
      }

      int main = length - GAP - STUB;

      if(right) {
         g.fillRect(x, y, main, height);
         g.fillRect(x + main + GAP, y, STUB, height);
      }
      else {
         g.fillRect(x - main, y, main, height);
         g.fillRect(x - length, y, STUB, height);
      }
   }
}
