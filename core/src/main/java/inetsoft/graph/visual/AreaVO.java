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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * This visual object represents a area in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AreaVO extends LineVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    * @param pidxs the row index which to map the subset point on area.
    * @param vars the dims represent as variables.
    */
   public AreaVO(Geometry gobj, Coordinate coord, int[] pidxs, String[] vars, String mname) {
      super(gobj, coord, pidxs, vars, mname);
      setZIndex(GDefaults.VO_Z_INDEX); // area below inner axis

      AreaGeometry obj = (AreaGeometry) gobj;
      basepts = new Point2D.Double[obj.getTupleCount()];

      for(int i = 0; i < basepts.length; i++) {
         double[] tuple = obj.getBaseTuple(i);
         basepts[i] = coord.getPosition(tuple);
      }
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      super.transform(coord);

      for(int i = 0; i < basepts.length; i++) {
         basepts[i] = (Point2D) coord.transformShape(basepts[i]);
      }
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      AreaGeometry gobj = (AreaGeometry) getGeometry();
      AreaElement elem = (AreaElement) gobj.getElement();
      Point2D[] pts = getTransformedPoints();
      Point2D[] basepts = getTransformedBasePoints();
      Graphics2D g2 = (Graphics2D) g.create();
      Color[] colors = new Color[pts.length];
      GTexture[] textures = new GTexture[pts.length];
      GLine[] lines = new GLine[pts.length];
      int[] idxs = gobj.getTupleIndexes();
      // @by larryl, true to draw areas as a single shape to avoid white
      // borders around each area caused by anti-aliasing
      boolean singleColor = true;
      boolean singleTexture = true;
      Object c0 = null, t0 = null;
      // since area also draw lines with line binding, we only apply border color in the
      // simplest case where no color/line information is available.
      Color borderColor = elem.getColorFrame() instanceof StaticColorFrame &&
         elem.getLineFrame() instanceof StaticLineFrame ? elem.getBorderColor() : null;

      for(int i = 0; i < pts.length; i++) {
         colors[i] = gobj.getColor(idxs[i % idxs.length]);
         textures[i] = gobj.getTexture(idxs[i % idxs.length]);
         lines[i] = gobj.getLine(idxs[i % idxs.length]);

         if(i == 0) {
            c0 = colors[i];
            t0 = textures[i];
            continue;
         }

         if(!CoreTool.equals(c0, colors[i])) {
            singleColor = false;
         }

         if(!CoreTool.equals(t0, textures[i])) {
            singleTexture = false;
         }
      }

      boolean singleShape = singleColor && singleTexture;

      // anti-alias may cause a slight border between areas
      if(elem.getType() == LineElement.Type.STEP) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }
      // anti-alias on causing a thin white line between areas when there is gradient. (59544)
      else if(singleShape || GTool.isVectorGraphics(g2) && singleColor) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }
      else {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      }

      Area shapeArea = new Area();
      boolean horizontal = GTool.isHorizontal(getScreenTransform());

      for(int i = 0; i < pts.length; i++) {
         int next = i + 1;

         if(next == pts.length) {
            if(!isClosed()) {
               break;
            }

            next = 0;
         }

         if(LineVO.isNaN(pts[i]) || LineVO.isNaN(pts[next]) ||
            LineVO.isNaN(basepts[i]) || LineVO.isNaN(basepts[next]))
         {
            continue;
         }

         Shape shape = getAreaShape(pts[i], pts[next], basepts[i], basepts[next], horizontal);

         if(shape == null) {
            // ignore
         }
         else if(singleShape) {
            shapeArea.add(new Area(shape));
         }
         else {
            Color startColor = colors[i];
            Color endColor = colors[next];
            GTexture startTexture = textures[i];
            GTexture endTexture = textures[next];

            fillArea(g2, pts[i], pts[next], shape, startColor, endColor,
                     startTexture, endTexture, lines[i], borderColor);
         }
      }

      if(singleShape && pts.length > 0) {
         fillArea(g2, getLowestXPoint(pts), getHighestXPoint(pts), shapeArea,
                  colors[0], colors[0], textures[0], textures[0], lines[0], borderColor);
      }

      g2.dispose();

      if(borderColor == null) {
         paintLine(g, Math.min(1, getAlphaHint() * 1.2));
      }
   }

   /**
    * Fill the area.
    *
    * @param g      the Graphics2D.
    * @param pt1    the top left point of the area.
    * @param pt2    the top right point of the area.
    * @param color1 the top left point color.
    * @param color2 the top right point color.
    */
   private void fillArea(Graphics2D g, Point2D pt1, Point2D pt2,
                         Shape path, Color color1, Color color2,
                         GTexture texture1, GTexture texture2, GLine line, Color borderColor)
   {
      color1 = applyAlpha(color1);
      color2 = applyAlpha(color2);

      Color savedColor = g.getColor();
      g.setColor(color1);

      if(pt1 != null && pt2 != null) {
         Paint paint = GTool.isHorizontal(getScreenTransform())
            ? new GradientPaint(new Point2D.Double(pt1.getX(), 0), color1,
                                new Point2D.Double(pt2.getX(), 0), color2)
            : new GradientPaint(new Point2D.Double(0, pt1.getY()), color1,
                                new Point2D.Double(0, pt2.getY()), color2);
         g.setPaint(paint);
      }

      if(texture2 != null) {
         texture2.paint(g, path);
      }
      else {
         g.fill(path);
      }

      if(borderColor != null) {
         g.setStroke(line != null ? line.getStroke() : new BasicStroke(1));
         g.setColor(borderColor);
         g.draw(path);
      }

      g.setColor(savedColor);
   }

   /**
    * Get the shape of the area through the four points.
    */
   private Shape getAreaShape(Point2D pt1, Point2D pt2, Point2D basept1, Point2D basept2,
                              boolean hor)
   {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      AreaElement elem = (AreaElement) gobj.getElement();

      if(elem.getType() == LineElement.Type.STEP) {
         Rectangle2D rect;

         if(hor) {
            rect = new Rectangle2D.Double(pt1.getX(), basept1.getY(), pt2.getX() - pt1.getX(),
                                          pt1.getY() - basept1.getY());
         }
         else {
            rect = new Rectangle2D.Double(basept1.getX(), pt1.getY(),
                                          pt1.getX() - basept1.getX(),
                                          pt2.getY() - pt1.getY());
         }

         return GTool.normalizeRect(rect);
      }

      GeneralPath path = new GeneralPath();

      path.moveTo((float) pt1.getX(), (float) pt1.getY());
      path.lineTo((float) basept1.getX(), (float) basept1.getY());
      path.lineTo((float) basept2.getX(), (float) basept2.getY());
      path.lineTo((float) pt2.getX(), (float) pt2.getY());
      path.closePath();

      return path;
   }

   /**
    * Get the points after screen transformation.
    */
   private Point2D[] getTransformedBasePoints() {
      Point2D[] pts = new Point2D[basepts.length];

      for(int i = 0; i < basepts.length; i++) {
         pts[i] = getScreenTransform().transform(basepts[i], null);
      }

      return pts;
   }

   /**
    * Get the lowest x point.
    */
   private Point2D getLowestXPoint(Point2D[] points) {
      double x = Double.MAX_VALUE;
      Point2D result = null;

      for(int i = 0; i < points.length; i++) {
         if(points[i].getX() < x) {
            x = points[i].getX();
            result = points[i];
         }
      }

      return result;
   }

   /**
    * Get the highest x point.
    */
   private Point2D getHighestXPoint(Point2D[] points) {
      double x = -Double.MAX_VALUE;
      Point2D result = null;

      for(int i = 0; i < points.length; i++) {
         if(points[i].getX() > x) {
            x = points[i].getX();
            result = points[i];
         }
      }

      return result;
   }

   private Point2D[] basepts; // base (bottom) points
}
