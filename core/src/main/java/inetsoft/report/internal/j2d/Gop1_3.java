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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

/**
 * Gop1_3 is the JDK1.3 implementation of the same operations in
 * Gop2D class.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Gop1_3 extends Gop2D {
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
         // @by billh, If is CJK string, and font changes without
         // font size change, the actual used font to draw string
         // will not be changed, so here I change the size slightly
         // to work round for jdk1.3
         Font font = g.getFont();

         // optimization
         Font font2 = (Font) fontcache.get(font);

         if(font2 == null) {
            font2 = font.deriveFont((float) (font.getSize() - 0.01));

            if(fontcache.size() > 50) {
               fontcache.clear();
            }

            fontcache.put(font, font2);
         }

         g.setFont(font2);

         Graphics2D g2d = (Graphics2D) g;
         Object hint = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
               RenderingHints.VALUE_ANTIALIAS_OFF);
         }

         g2d.drawString(str, x, y);

         if(hint != null) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
         }

         g.setFont(font);

      }
      catch(ClassCastException e) {
         super.drawString(g, str, x, y);
      }
   }

   private Hashtable fontcache = new Hashtable();

   private static final Logger LOG =
      LoggerFactory.getLogger(Gop1_3.class);
}
