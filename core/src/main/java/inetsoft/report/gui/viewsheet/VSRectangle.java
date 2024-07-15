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
package inetsoft.report.gui.viewsheet;

import inetsoft.graph.internal.GTool;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;

import java.awt.*;

/**
 * VSRectangle component for viewsheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSRectangle extends VSShape {
   /**
    * Contructor.
    */
   public VSRectangle(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the content.
    */
   @Override
   protected void paintShape(Graphics2D g) {
      int style = getLineStyle();
      int linew = (int) GTool.getLineWidth(style);
      int halfw = (int) (linew / 2);
      int corner = getRoundCorner();

      g.setStroke(GTool.getStroke(style));

      /**
       * Background is already drawn, so if there is no border do not draw anything
       */
      if(style == 0) {
         return;
      }

      if(corner > 0) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      g.setColor(getForeground());

      if(style == StyleConstants.DOUBLE_LINE) {
         g.setStroke(GTool.getStroke(StyleConstants.THIN_LINE));
         g.drawRoundRect(0, 0, getShapePixelSize().width - 1,
                         getShapePixelSize().height - 1, corner * 2, corner * 2);
         g.drawRoundRect(2, 2, getShapePixelSize().width - 5,
                         getShapePixelSize().height - 5, corner * 2, corner * 2);
      }
      else if(corner > 0) {
         g.drawRoundRect(halfw, halfw, getShapePixelSize().width - linew ,
                         getShapePixelSize().height - linew, corner * 2, corner * 2);
      }
      else {
         g.drawRect(halfw, halfw, getShapePixelSize().width - linew ,
                    getShapePixelSize().height - linew);
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
      int corner = getRoundCorner();
      // make sure background left and top painted in the border.
      g.fillRoundRect(1, 1, size.width - 1, size.height - 1, corner * 2, corner * 2);
   }

   /**
    * Get the round corner.
    */
   private int getRoundCorner() {
      RectangleVSAssemblyInfo info = (RectangleVSAssemblyInfo) getAssemblyInfo();
      return info.getFormat().getRoundCorner();
   }
}
