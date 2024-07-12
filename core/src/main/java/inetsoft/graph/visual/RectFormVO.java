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
package inetsoft.graph.visual;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.guide.form.RectForm;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Visual object for line form.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RectFormVO extends FormVO {
   /**
    * Constructor.
    */
   public RectFormVO(RectForm form, Rectangle2D rect) {
      super(form);

      setShape(rect);
      setFixedPosition(form.isFixedPosition());
   }

   /**
    * Get a shape transformed with screen transformation.
    */
   @Override
   protected Shape getTransformedShape(Shape shape) {
      RectForm form = (RectForm) getForm();

      if(isFixedPosition()) {
         Point2D p1 = form.getTopLeftPoint();
         Point2D p2 = form.getBottomRightPoint();

         p1 = transformFixedPosition(p1);
         p2 = transformFixedPosition(p2);

         return new Rectangle2D.Double(p1.getX(), p2.getY(),
                                       p2.getX() - p1.getX(),
                                       p1.getY() - p2.getY());
      }

      Rectangle2D rect = (Rectangle2D) getShape();
      boolean fillWidth = rect.getX() == 0 && rect.getWidth() == 1000;
      boolean fillHeight = rect.getY() == 0 && rect.getHeight() == 1000;
      Coordinate coord = getCoordinate();

      shape = super.getTransformedShape(shape);

      // for target, the band may happen to be min/max of the y axis. we check if fillWidth (x)
      // in coord, then donn't fill y, and vise versa. (58892)
      if(fillWidth && fillHeight && coord != null) {
         if(GTool.isHorizontal(coord.getCoordTransform())) {
            fillHeight = false;
         }
         else {
            fillWidth = false;
         }
      }

      // fill width/height, for target fill shape
      if((fillWidth || fillHeight) && coord != null) {
         if(GTool.isRectangular(shape)) {
            Rectangle2D box = shape.getBounds2D();
            Rectangle2D coordBox = coord.getVGraph().getPlotBounds();
            double rotation = GTool.getRotation(coord.getCoordTransform());
            boolean horizontal = (rotation % 180) == 0;
            boolean vertical = ((rotation + 90) % 180) == 0;

            if(horizontal || vertical) {
               if(vertical) {
                  boolean fillHeight0 = fillHeight;
                  fillHeight = fillWidth;
                  fillWidth = fillHeight0;
               }

               if(fillWidth && coordBox != null) {
                  box = new Rectangle2D.Double(coordBox.getX(), box.getY(),
                                               coordBox.getWidth(), box.getHeight());
               }

               if(fillHeight && coordBox != null) {
                  box = new Rectangle2D.Double(box.getX(), coordBox.getY(),
                                               box.getWidth(), coordBox.getHeight());
               }

               shape = box;
            }
         }
      }

      return shape;
   }
}
