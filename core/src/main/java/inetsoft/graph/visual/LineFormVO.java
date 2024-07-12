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
import inetsoft.graph.guide.form.LineForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;

/**
 * Visual object for line form.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LineFormVO extends FormVO {
   /**
    * Constructor.
    * @param form is the Line Form object contains line information.
    */
   public LineFormVO(LineForm form, Point2D[] points) {
      super(form);
      this.points = points;

      setShape(createLine(points));
      setFixedPosition(form.isFixedPosition());
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      Graphics2D g2 = (Graphics2D) g.create();
      Shape shape = getShape();
      LineForm form = (LineForm) getForm();
      Point2D[] pts = new Point2D[points.length];
      boolean fillWidth = "true".equals(form.getHint("fill")) &&
         points.length == 2 && points[0].getX() == 0 && points[1].getX() == 1000;
      boolean fillHeight = "true".equals(form.getHint("fill")) &&
         points.length == 2 && points[0].getY() == 0 && points[1].getY() == 1000;

      for(int i = 0; i < points.length; i++) {
         if(isFixedPosition()) {
            pts[i] = transformFixedPosition(points[i]);
         }
         else {
            pts[i] = getScreenTransform().transform(points[i], null);
         }
      }

      // apply offset
      for(int i = 0; i < pts.length; i++) {
         pts[i] = new Point2D.Double(pts[i].getX() + form.getXOffset(),
                                     pts[i].getY() + form.getYOffset());
      }

      Coordinate coord = getCoordinate();

      // fill width/height, for target fill shape
      if((fillWidth || fillHeight) && coord != null && !form.isFill()) {
         Rectangle2D coordBox = coord.getVGraph().getPlotBounds();
         double rotation = Math.toDegrees(GTool.getRotation(coord.getCoordTransform()));
         boolean horizontal = (rotation % 180) == 0;
         boolean vertical = ((rotation + 90) % 180) == 0;

         if(horizontal || vertical) {
            if(vertical) {
               boolean fillHeight0 = fillHeight;
               fillHeight = fillWidth;
               fillWidth = fillHeight0;
            }

            if(fillWidth) {
               pts[0] = new Point2D.Double(coordBox.getX(), pts[0].getY());
               pts[1] = new Point2D.Double(coordBox.getX() + coordBox.getWidth(), pts[1].getY());
            }

            if(fillHeight) {
               pts[0] = new Point2D.Double(pts[0].getX(), coordBox.getY());
               pts[1] = new Point2D.Double(pts[1].getX(), coordBox.getY() + coordBox.getHeight());
            }
         }
      }

      Color color = getColor();
      g2.setColor(color != null ? color : GDefaults.DEFAULT_LINE_COLOR);

      // this should match the AxisLine/GridLine so lines drawn at the same location won't
      // create a slight fuzzy effect.
      if(isAntiAlias(pts) || GTool.isVectorGraphics(g2)) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }
      else {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      if(form.isFill()) {
         if(isFixedPosition()) {
            shape = createLine(pts);
         }
         else {
            shape = getTransformedShape(shape);
         }

         g2.fill(shape);
      }
      else {
         g2.setStroke(GTool.getStroke(form.getLine()));

         // we don't use shape here since GeneralPath stores the point values
         // as float, and rounding error could cause it to be different from
         // points, which are doubles. Since line needs to be lined up perfectly
         // with axis line, it requires more precision than other forms.
         for(int i = 1; i < pts.length; i++) {
            GTool.drawLine(g2, new Line2D.Double(pts[i - 1], pts[i]));
         }

         if(form.isStartArrow() && pts.length > 1) {
            GTool.drawArrow(g2, pts[1], pts[0], 5);
         }

         if(form.isEndArrow() && pts.length > 1) {
            GTool.drawArrow(g2, pts[pts.length - 2], pts[pts.length - 1], 5);
         }
      }

      g2.dispose();
   }

   // fill is not in bounds.
   @Override
   public boolean isPaintInBounds() {
      return false;
   }

   /**
    * Check if set antialiasing on.
    */
   private boolean isAntiAlias(Point2D[] points) {
      if(points.length == 2) {
         Point2D pos1 = points[0];
         Point2D pos2 = points[1];

         if(pos1.getX() == pos2.getX() || pos1.getY() == pos2.getY()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Create a line shape.
    */
   private Shape createLine(Point2D[] points) {
      GeneralPath shape = new GeneralPath();

      for(int i = 0; i < points.length; i++) {
         if(i == 0) {
            shape.moveTo((float) points[i].getX(), (float) points[i].getY());
         }
         else {
            shape.lineTo((float) points[i].getX(), (float) points[i].getY());
         }
      }

      return shape;
   }

   /**
    * Make sure line is not clipped outside of plot.
    */
   @Override
   public Rectangle2D getBounds() {
      Rectangle2D box = super.getBounds();

      if(box != null) {
         if(box.getWidth() == 0) {
            box.add(box.getMinX() - 1, box.getMinY());
         }
         else if(box.getHeight() == 0) {
            box.add(box.getMinX(), box.getMinY() - 1);
         }
      }

      return box;
   }

   /**
    * Get the points on the line.
    */
   public Point2D[] getPoints() {
      return points;
   }

   private Point2D[] points;

   private static final Logger LOG =
      LoggerFactory.getLogger(LineFormVO.class);
}
