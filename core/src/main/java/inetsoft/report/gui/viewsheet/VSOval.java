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
package inetsoft.report.gui.viewsheet;

import inetsoft.graph.internal.GTool;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.Viewsheet;

import java.awt.*;

/**
 * VSOval component for viewsheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSOval extends VSShape {
   /**
    * Contructor.
    */
   public VSOval(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the content.
    */
   @Override
   protected void paintShape(Graphics2D g) {
      g.setColor(getForeground());
      int style = getLineStyle();
      g.setStroke(GTool.getStroke(style));
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);

      if(style == StyleConstants.DOUBLE_LINE) {
         g.setStroke(GTool.getStroke(StyleConstants.THIN_LINE));
         g.drawOval(0, 0, getShapePixelSize().width - 1,
         getShapePixelSize().height - 1);
         g.drawOval(2, 2, getShapePixelSize().width - 5,
         getShapePixelSize().height - 5);
      }
      else {
         int gap = (int) GTool.getLineWidth(style) - 1;
         g.drawOval(gap, gap, getShapePixelSize().width - 1 - 2 * gap,
            getShapePixelSize().height - 1 - 2 * gap);
      }
   }

   /**
    * Draw Background.
    */
   @Override
   protected void drawBackground(Graphics g) {
      Paint color = getGradientColor();

      if(color == null) {
         color = getBackground();
      }

      if(color == null) {
         return;
      }

      ((Graphics2D) g).setPaint(color);
      Dimension size = getShapePixelSize();
      g.fillOval(0, 0, size.width - 1, size.height - 1);
   }
}
