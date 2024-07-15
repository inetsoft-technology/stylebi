/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.visual;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.guide.form.ShapeForm;

import java.awt.*;
import java.awt.geom.*;

/**
 * Visual object for shape form.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ShapeFormVO extends FormVO {
   /**
    * Constructor.
    */
   public ShapeFormVO(ShapeForm form, Point2D pos) {
      super(form);

      this.pos = pos;
      this.shape = form.getShape();

      setFixedPosition(form.isFixedPosition());

      if(shape != null) {
         setShape(createShape(pos));
      }
   }

   /**
    * Paint the shape.
    */
   @Override
   protected void paint(Graphics2D g, Shape shp) {
      ShapeForm form = (ShapeForm) getForm();

      if(form.getRotation() != 0) {
         Rectangle2D bounds = shp.getBounds2D();

         g = (Graphics2D) g.create();
         g.rotate(Math.toRadians(form.getRotation()),
                  bounds.getCenterX(), bounds.getCenterY());
         
         shape.paint(g, shp);
         g.dispose();
      }
      else {
         shape.paint(g, shp);
      }
   }

   /**
    * Get a shape transformed with screen transformation.
    */
   @Override
   protected Shape getTransformedShape(Shape shape) {
      Point2D pt = isFixedPosition() ? transformFixedPosition(pos)
         : getScreenTransform().transform(pos, null);

      // recreate shape means we treat the size as fixed size and only position
      // is transformed
      return createShape(pt);
   }

   /**
    * Create a shape at the positino.
    */
   protected Shape createShape(Point2D pos) {
      double x = pos.getX();
      double y = pos.getY();
      ShapeForm form = (ShapeForm) getForm();
      Dimension2D size = form.getSize();
      double min = shape.getMinSize();
      double w = (size == null) ? min : size.getWidth();
      double h = (size == null) ? min : size.getHeight();

      switch(form.getAlignmentX()) {
      case GraphConstants.LEFT_ALIGNMENT:
         break;
      case GraphConstants.CENTER_ALIGNMENT:
         x -= w / 2;
         break;
      case GraphConstants.RIGHT_ALIGNMENT:
         x -= w;
         break;
      }

      switch(form.getAlignmentY()) {
      case GraphConstants.TOP_ALIGNMENT:
         y -= h;
         break;
      case GraphConstants.MIDDLE_ALIGNMENT:
         y -= h / 2;
         break;
      case GraphConstants.BOTTOM_ALIGNMENT:
         break;
      }
         
      return applyOffset(shape.getShape(x, y, w, h));
   }

   private Point2D pos;
   private GShape shape;
}
