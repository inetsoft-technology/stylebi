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
package inetsoft.report.internal;

import inetsoft.report.Painter;
import inetsoft.report.ReportElement;
import inetsoft.report.painter.ImagePainter;

import java.awt.*;

/**
 * The ImagePainterPaintable encapsulate printing of a image painter.
 *
 * @version 5.1, 9/20/2003
 * @author mikec
 */
public class ImagePainterPaintable extends PainterPaintable {
   /**
    * Default constructor
    */
   public ImagePainterPaintable() {
      super(new ImageElementDef());
   }

   /**
    * Construct an image painter paintable.
    */
   public ImagePainterPaintable(float x, float y,
                                float painterW, float painterH,
                                Dimension pd, int prefW, int prefH,
                                ReportElement elem, Painter painter,
                                int offsetX, int offsetY, int rotation) {
      super(x, y, painterW, painterH, pd, prefW, prefH, elem, painter, offsetX,
            offsetY, rotation);
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   @Override
   protected void paintFg(Graphics g, Painter painter0,
                          float bufferW, float bufferH) {
      if(painter instanceof ImagePainter) {
         ((ImagePainter) painter).setBackground(getBackground());

         if(elem instanceof ImageElementDef) {
            ((ImagePainter) painter).setAspect(((ImageElementDef) elem).isAspect());
         }
      }

      if(painter0 instanceof ImagePainter &&
         ((ImagePainter) painter0).isSVGImage())
      {
         g.setColor(fg);
         painter0.paint(g, (int) (x - getXOffset()), (int) (y - getYOffset()),
                       (int) bufferW, (int) bufferH);

         return;
      }

      super.paintFg(g, painter0, bufferW, bufferH);
   }
}
