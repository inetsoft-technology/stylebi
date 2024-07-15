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
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;

/**
 * VSLine component for viewsheet.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSLine extends VSShape {
   private static final double LENGTH = 5 * Math.sqrt(2);

   /**
    * The enlarge value of the image to paint the arrow.
    */
   public static final int ARROW_GAP = (int) (LENGTH / Math.sqrt(2));

   /**
    * Contructor.
    */
   public VSLine(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the content.
    */
   @Override
   protected void paintShape(Graphics2D g) {
      int style = getLineStyle();
      g.setStroke(GTool.getStroke(style));
      g.setColor(getForeground());

      Object[] newInfo = VSUtil.refreshLineInfo(getViewsheet(), getInfo());
      Point start = scalePoint((Point) newInfo[2]);
      Point end = scalePoint((Point) newInfo[3]);
      int startStyle = getInfo().getBeginArrowStyle();
      int endStyle = getInfo().getEndArrowStyle();

      if(start.x == end.x || start.y == end.y ||
         // dot line antialiased may look like a solid line
         (style & StyleConstants.DASH_MASK) != 0)
      {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
      }
      else {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
      }

      if(style == StyleConstants.DOUBLE_LINE) {
         drawDoubleLine(g, start, end);
      }
      else {
         g.drawLine(start.x, start.y, end.x, end.y);
      }

      double lineAngle = getAngle(start, end);
      drawArrow(g, start, Math.PI + lineAngle, startStyle);
      drawArrow(g, end, lineAngle, endStyle);
   }

   /**
    * Scale the start/end point.
    */
   private Point scalePoint(Point point) {
      if(getInfo() instanceof AnnotationLineVSAssemblyInfo) {
         return point;
      }

      Point sPoint = new Point();
      sPoint.x = (int) Math.floor(
         point.x * getInfo().getScalingRatio().getWidth());
      sPoint.y = (int) Math.floor(
         point.y * getInfo().getScalingRatio().getHeight());

      return sPoint;
   }

   /**
    * Draw double line.
    * @param g the graphics.
    * @param start the start point.
    * @param start the end point.
    */
   private void drawDoubleLine(Graphics2D g, Point start, Point end) {
      double angle = getAngle(end, start);
      g.setStroke(GTool.getStroke(StyleConstants.THIN_LINE));
      int quadrant = (int) (angle / (Math.PI / 2));

      if(angle == 0 || angle == Math.PI) {
         g.drawLine(start.x, start.y - 1, end.x, end.y - 1);
         g.drawLine(start.x, start.y + 1, end.x, end.y + 1);
      }
      else if(angle == Math.PI / 2 || angle == 3 * Math.PI / 2) {
         g.drawLine(start.x - 1, start.y, end.x - 1, end.y);
         g.drawLine(start.x + 1, start.y, end.x + 1, end.y);
      }
      else if(quadrant % 2 != 0) {
         g.drawLine(start.x - 1, start.y - 1, end.x - 1, end.y - 1);
         g.drawLine(start.x + 1, start.y + 1, end.x + 1, end.y + 1);
      }
      else {
         g.drawLine(start.x - 1, start.y + 1, end.x - 1, end.y + 1);
         g.drawLine(start.x + 1, start.y - 1, end.x + 1, end.y - 1);
      }
   }

   /**
    * Draw Background.
    */
   @Override
   protected void drawBackground(Graphics g) {
      // do nothing.
   }

   /**
    * Get the angle (radian) from p1 to p2.  copy from GTool
    */
   private double getAngle(Point p1, Point p2) {
      double v = 0;

      if(p1.x == p2.x) {
         v = (p2.y > p1.y) ? Math.PI / 2 : -Math.PI / 2;
      }
      else {
         v = Math.atan((double) (p2.y - p1.y) / (p2.x - p1.x));

         if(p2.x < p1.x) {
            v += Math.PI;
         }
      }

      while(v < 0) {
         v += Math.PI * 2;
      }

      return v;
   }

   /**
    * draw arrrow.
    * @param opoint the point needed draw line.
    * @param angle the line slope.
    * @param style the arrow style.
    */
   private void drawArrow(Graphics2D g, Point opoint, double angle, int style)
   {
      g = (Graphics2D) g.create();
      g.setStroke(GTool.getStroke(StyleConstants.THIN_LINE));
      Point lpt = getArrowPoint(opoint, angle - INCLINE);
      Point rpt = getArrowPoint(opoint, angle + INCLINE);

      // clip region is a right angled isosceles triangle
      Point clpt= getClipPoint(opoint, angle - INCLINE * 2);
      Point crpt = getClipPoint(opoint, angle + INCLINE * 2);
      Polygon polygon;

      if(style == StyleConstants.NO_BORDER) {
         return;
      }

//      if(getLineStyle() == StyleConstants.DOUBLE_LINE) {
//         Color savedColor = g.getColor();
//         g.setColor(Color.white);
//         polygon = new Polygon();
//         polygon.addPoint(opoint.x, opoint.y);
//         polygon.addPoint(clpt.x, clpt.y);
//         polygon.addPoint(lpt.x, lpt.y);
//         g.fillPolygon(polygon);
//         g.drawPolygon(polygon);
//
//         polygon = new Polygon();
//         polygon.addPoint(opoint.x, opoint.y);
//         polygon.addPoint(crpt.x, crpt.y);
//         polygon.addPoint(rpt.x, rpt.y);
//         g.fillPolygon(polygon);
//         g.drawPolygon(polygon);
//         g.setColor(savedColor);
//      }

      switch(style) {
      case(StyleConstants.ARROW_LINE_1):
         polygon = new Polygon();
         polygon.addPoint(opoint.x, opoint.y);
         polygon.addPoint(lpt.x, lpt.y);
         polygon.addPoint(rpt.x, rpt.y);
         g.fillPolygon(polygon);
         g.drawPolygon(polygon);
         break;
      case(StyleConstants.ARROW_LINE_2):
         g.drawLine(opoint.x, opoint.y, lpt.x, lpt.y);
         g.drawLine(opoint.x, opoint.y, rpt.x, rpt.y);
         break;
      case(StyleConstants.ARROW_LINE_3):
         polygon = new Polygon();
         polygon.addPoint(opoint.x, opoint.y);
         polygon.addPoint(lpt.x, lpt.y);
         polygon.addPoint(rpt.x, rpt.y);
         Color saveColor = g.getColor();
         g.setColor(Color.white);
         g.fillPolygon(polygon);
         g.setColor(saveColor);
         g.drawPolygon(polygon);
         break;
      }
   }

   /**
    * Get the arrow line point.
    * @param opoint the origin point.
    * @param angle the line slope.
    */
   private Point getArrowPoint(Point opoint, double angle) {
      return new Point((int) (opoint.x - Math.cos(angle) * LENGTH),
                       (int) (opoint.y - Math.sin(angle) * LENGTH));
   }

   /**
    * Get the arrow line clip point for doule line.
    * @param point the origin point.
    * @param angle the line slope.
    */
   private Point getClipPoint(Point point, double angle) {
      return new Point((int) (point.x - Math.cos(angle) * ARROW_GAP),
                       (int) (point.y - Math.sin(angle) * ARROW_GAP));
   }

   /**
    * Get the round corner.
    */
   private LineVSAssemblyInfo getInfo() {
      return (LineVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Get original size of the image.
    * @return the content height.
    */
   @Override
   protected Dimension getImageSize() {
      Object[] newInfo = VSUtil.refreshLineInfo(getViewsheet(), getInfo());
      Dimension imageSize = (Dimension) newInfo[1];

      if(getInfo() instanceof AnnotationLineVSAssemblyInfo) {
         return imageSize;
      }

      imageSize.width = (int) Math.floor(
         imageSize.width * getInfo().getScalingRatio().getWidth());
      imageSize.height = (int) Math.floor(
         imageSize.height * getInfo().getScalingRatio().getHeight());

      return imageSize;
   }

   private static double INCLINE = Math.PI / 4;
}
