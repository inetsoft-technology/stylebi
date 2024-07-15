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

import inetsoft.report.*;

import java.awt.*;

/**
 * The PainterPaintable encapsulate printing of a presenter painter.
 *
 * @version 5.1, 9/20/2003
 * @author mikec
 */
public class PresenterPainterPaintable extends PainterPaintable {
   /**
    * Default constructor
    */
   public PresenterPainterPaintable() {
      super();
   }

   public PresenterPainterPaintable(float x, float y,
                                    float painterW, float painterH,
                                    Dimension pd, int prefW, int prefH,
                                    ReportElement elem, Painter painter,
                                    int offsetX, int offsetY, int rotation) {
      super(x, y, painterW, painterH, pd, prefW, prefH, elem, painter, offsetX,
            offsetY, rotation);
   }

   /**
    * Process Hyperlink.
    */
   @Override
   protected void processHyperlink() {
      Hyperlink link = ((HyperlinkSupport) elem).getHyperlink();

      if(link != null) {
         setHyperlink(new Hyperlink.Ref(link));
      }
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   @Override
   protected void paintBg(Graphics g, Painter painter0, float bufferW,
                          float bufferH)
   {
      g.setColor(bg);

      if(elem instanceof TextBoxElementDef) {
         TextBoxElementDef textbox = (TextBoxElementDef) elem;

         if(textbox.getShape() == StyleConstants.BOX_ROUNDED_RECTANGLE) {
            Dimension corner = textbox.getCornerSize();
            corner = (corner != null) ? corner : new Dimension(5, 5);
            g.fillRoundRect((int) x, (int) y, (int) (bufferW - 1),
                         (int) (bufferH -  1), corner.width, corner.height);
            g.setColor(textbox.getBorderColor());
            g.drawRoundRect((int) x, (int) y, (int) (bufferW - 1),
                         (int) (bufferH -  1), corner.width, corner.height);
            g.setColor(bg);

            return;
         }
      }

      g.fillRect((int) x, (int) y, (int) bufferW, (int) bufferH);
   }
}

