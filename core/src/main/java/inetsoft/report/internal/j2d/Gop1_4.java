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
package inetsoft.report.internal.j2d;

import inetsoft.report.io.ArabicTextUtil;

import java.awt.*;
import java.text.Bidi;

/**
 * Gop1_4 is the JDK1.4 implementation of the same operations in
 * Gop2D/Gop1_3 classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Gop1_4 extends Gop1_3 {

   /**
    * Draw string on graphics.
    * @param g graphics context.
    * @param str string contents.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   @Override
   public void drawString(Graphics g, String str, float x, float y) {
      try {
         Graphics2D g2d = (Graphics2D) g;
         Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
         Object hintFM = g2d.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                                 RenderingHints.VALUE_FRACTIONALMETRICS_ON);
         }

         g2d.drawString(str, x, y);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
         }

         if(hintFM != null) {
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, hintFM);
         }
      }
      catch(ClassCastException e) {
         super.drawString(g, str, x, y);
      }
   }

   /**
    * Reorder the characters in the string according to bidi rules if
    * applicable. This is only supported in jdk 1.4 and later.
    */
   @Override
   public String reorderBidi(String str) {
      char[] chars = str.toCharArray();

      if(Bidi.requiresBidi(chars, 0, chars.length)) {
         ArabicTextUtil arabicTextUtil = ArabicTextUtil.getInstance();
         Bidi bidi = new Bidi(str, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
         boolean baseLtoR = bidi.baseIsLeftToRight();
         boolean lToR = baseLtoR;
         StringBuilder buf = new StringBuilder();

         for(int i = 0; i < bidi.getRunCount(); i++) {
            int si = bidi.getRunStart(i);
            int ei = bidi.getRunLimit(i);

            if(!lToR) { // if right to left, reverse the order
               for(int i1 = si, i2 = ei - 1; i2 >= i1; i1++, i2--) {
                  // Bug #59512, char in the middle when the # of chars is odd
                  if(i2 == i1) {
                     chars[i1] = arabicTextUtil.getMirrorCharacter(chars[i1]);
                  }
                  else {
                     char tmp = chars[i1];
                     chars[i1] = arabicTextUtil.getMirrorCharacter(chars[i2]);
                     chars[i2] = arabicTextUtil.getMirrorCharacter(tmp);
                  }
               }
            }

            if(baseLtoR) { // base direction LtoR, append to end
               buf.append(chars, si, ei - si);
            }
            else { // base direction RtoL, insert to front
               buf.insert(0, chars, si, ei - si);
            }

            lToR = !lToR;
         }

         return buf.toString();
      }

      return str;
   }
}

