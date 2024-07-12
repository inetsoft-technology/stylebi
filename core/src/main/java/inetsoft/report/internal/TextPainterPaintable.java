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
package inetsoft.report.internal;

import inetsoft.report.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * The TextPainterPaintable encapsulate printing of a text painter.
 *
 * @version 5.1, 9/20/2003
 * @author mikec
 */
public class TextPainterPaintable extends PainterPaintable {
   /**
    * Default constructor
    */
   public TextPainterPaintable() {
      super(new TextBoxElementDef());
   }

   /**
    * Construct an image painter paintable.
    */
   public TextPainterPaintable(float x, float y,
                               float painterW, float painterH,
                               Dimension pd, int prefW, int prefH,
                               ReportElement elem, Painter painter,
                               int offsetX, int offsetY, int rotation) {
      super(x, y, painterW, painterH, pd, prefW, prefH, elem, painter, offsetX,
            offsetY, rotation);

      if(elem != null) {
         setDrillHyperlinks(((TextBased) elem).getDrillHyperlinks());
      }
   }

   /**
    * Return the text in the text lens.
    */
   public String getText() {
      return ((TextPainter) getPainter()).getText();
   }

   /**
    * Set the text contained in this text element.
    */
   public void setText(String text) {
      ((TextPainter) getPainter()).setText(text);
   }

   /**
    * Paint the painter paintable.
    * @param g the graphics.
    * @param painter0 the painter.
    * @param bufferW the width of paint buffer.
    * @param bufferH the height of paint buffer.
    */
   @Override
   protected void paintBg(Graphics g, Painter painter0,
                          float bufferW, float bufferH) {
      // handle rounded rect background in text box
      if((painter instanceof TextPainter) &&
         ((TextPainter) painter).getShape() ==
         StyleConstants.BOX_ROUNDED_RECTANGLE)
      {
         Dimension arc = ((TextPainter) painter).getCornerSize();
         int shadowW = ((TextPainter) painter).getShadowWidth();

         arc = (arc != null) ? arc : new Dimension(5, 5);

         g.setColor(getBackground());
         g.fillRoundRect((int) x, (int) y, (int) (bufferW - shadowW - 1),
                         (int) (bufferH - shadowW - 1), arc.width, arc.height);
      }
      else if(painter instanceof TextPainter) {
         TextPainter pt = (TextPainter) painter;
         Insets borders = pt.getBorders();
         int right = (borders == null) ? pt.getBorder() : borders.right;
         int bottom = (borders == null) ? pt.getBorder() : borders.bottom;
         float rightW = Common.getLineWidth(right);
         float bottomW = Common.getLineWidth(bottom);

         rightW = (rightW > 1) ? 0.5f : rightW / 2;
         bottomW = (bottomW > 1) ? 0.5f : bottomW / 2;

         g.setColor(getBackground());

         Common.fillRect(g, x, y, bufferW - rightW, bufferH - bottomW);
      }
      else {
         super.paintBg(g, painter0, bufferW, bufferH);
      }
   }

   /**
    * Check if this paintable must wait for the entire report to be processed.
    * This is true for elements that need information from report, such
    * as page total, table of contents page index.
    */
   @Override
   public boolean isBatchWaiting() {
      return painter instanceof TextPainter &&
             ((TextPainter) painter).getTextLens() instanceof HeaderTextLens;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TextPainterPaintable.class);
}
