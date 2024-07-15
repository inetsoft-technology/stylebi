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
package inetsoft.graph.visual;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.GeoCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.LinearScale;
import inetsoft.util.CoreTool;
import net.jafama.FastMath;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;

import static inetsoft.graph.element.LineElement.Type;
import static inetsoft.graph.element.LineElement.Type.*;

/**
 * This visual object represents a line in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LineVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    * @param pidxs the row index which to map the subset point on line.
    * @param vars the dims represent as variables.
    */
   public LineVO(Geometry gobj, Coordinate coord, int[] pidxs, String[] vars, String mname) {
      super(gobj, mname);

      // line could be covered by axis line (polar inner axis), make sure it's on top.
      setZIndex(GDefaults.VO_Z_INDEX + 2);

      LineGeometry obj = (LineGeometry) gobj;
      GraphElement elem = obj.getElement();

      this.pidxs = pidxs;
      points = new Point2D.Double[obj.getTupleCount()];

      // row index count might be less than point count, e.g. radar
      if(this.pidxs == null || this.pidxs.length != points.length) {
         this.pidxs = new int[points.length];

         for(int i = 0; i < points.length; i++) {
            this.pidxs[i] = pidxs[0];
         }
      }

      vtexts = new VOText[points.length];
      // track labels at each point
      Set<String> ptlabels = new HashSet<>();
      int[] tidxs = obj.getTupleIndexes();
      GeoCoord.startShape(coord, obj.getMinX(coord), obj.getMaxX(coord));

      for(int i = 0; i < points.length; i++) {
         points[i] = coord.getPosition(obj.getTuple(i));

         // only one scale, style is line or area, move the point to center
         if(isRect1(coord)) {
            points[i] = new Point2D.Double(coord.getMaxWidth() / 2, points[i].getY());
         }

         Object label;

         // regular line with dim and var
         if(vars == null) {
            // the visual model uses sorted dataset so should use tuple index instead of
            // base index (pidxs). (52258)
            label = obj.getText(tidxs[i]);
         }
         // from multi variable binding (radar)
         else {
            VisualModel model = obj.getVisualModel();
            label = model.getText(vars[i], tidxs[i % tidxs.length]);
         }

         if(label != null) {
            String key = label.toString() + points[i];

            // if the same label is show at the same location (e.g. path binding
            // in map), we can safely ignore it
            if(ptlabels.contains(key)) {
               continue;
            }

            ptlabels.add(key);

            // see LineElement.createGeometry()
            if(elem.getVarCount() == 0) {
               int didx = i % elem.getDimCount();
               int[] rowIdxs = obj.getSubRowIndexes();
               vtexts[i] = new VOText(label, this, elem.getDim(didx), coord.getDataSet(),
                                      rowIdxs[didx % rowIdxs.length], obj);
            }
            else {
               vtexts[i] = new VOText(label, this, getMeasureName(),
                                      coord.getDataSet(), obj.getSubRowIndexes()[i], obj);
            }

            vtexts[i].setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtexts[i].setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }
      }

      GeoCoord.endShape();
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      double alpha = getAlphaHint();
      LineGeometry gobj = (LineGeometry) getGeometry();
      LineElement elem = (LineElement) gobj.getElement();

      if(elem.getOutlineColor() != null) {
         paintLine(g, alpha, new BorderLineInfo(elem.getOutlineColor()));
      }

      paintLine(g, alpha, lineInfo);
   }

   /**
    * Paint the line shape.
    * @param alpha the transparency, 0-1.
    */
   protected void paintLine(Graphics2D g, double alpha) {
      paintLine(g, alpha, lineInfo);
   }

   static void paintLine(Graphics2D g, double alpha, LineInfo lineInfo) {
      Graphics2D g2 = (Graphics2D) g.create();

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // optimization, for large number of lines use simple drawing if possible
      if(isSimpleLine(lineInfo)) {
         paintLineSimple(g2, alpha, lineInfo);
      }
      else {
         paintLine0(g2, alpha, lineInfo);
      }

      g2.dispose();
   }

   /**
    * Check if lines have same line, color, and style.
    */
   private static boolean isSimpleLine(LineInfo lineInfo) {
      Color color = null;
      double size = 0;
      GLine line = null;

      for(int i = 0; i < lineInfo.getPoints().length; i++) {
         Color color2 = lineInfo.getColor(i);
         double size2 = lineInfo.getSize(i);
         GLine line2 = lineInfo.getLine(i);

         if(i == 0) {
            color = color2;
            size = size2;
            line = line2;
         }
         else if(isNaN(lineInfo.getPoints()[i]) && lineInfo.getFillLineStyle() != 0) {
            return false;
         }
         else if(!CoreTool.equals(color, color2) ||
                 !CoreTool.equals(line, line2) || size != size2)
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Paint the line shape assuming the line has same color, size, and style.
    * @param alpha the transparency, 0-1.
    */
   private static void paintLineSimple(Graphics2D g2, double alpha, LineInfo lineInfo) {
      Point2D[] pts = lineInfo.getTransformedPoints();

      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

      // if we don't have two points to draw a line, we should draw something
      // to indicate the presence of data. Otherwise we may get blank output
      // and make people think there is an error
      if(pts.length == 1) {
         Color color = lineInfo.getColor(0);
         double size = getLineSize(lineInfo.getSize(0));

         g2.setColor(color);
         g2.fill(getPointShape(pts[0], Math.max(3, size)));
         return;
      }
      else if(pts.length == 0) {
         return;
      }

      Path2D path = new Path2D.Double();
      GLine style = lineInfo.getLine(0);
      Color color = lineInfo.getColor(0);
      double size = getLineSize(lineInfo.getSize(0));

      if(style == null) {
         style = GLine.THIN_LINE;
      }

      if(alpha != 1) {
         color = GTool.getColor(color, alpha);
      }

      g2.setColor(color);

      boolean horizontal = GTool.isHorizontal(lineInfo.getScreenTransform());
      boolean ignoreNull = lineInfo.isIgnoreNull();
      boolean dotend = style.getDash() != 0 && !lineInfo.isClosed() || lineInfo.getType() == JUMP;
      boolean samepos = true; // all points at same position
      boolean overplotx = pts.length > 200; // overplotted on x direction
      boolean first = true;
      Point2D lastPt = null;

      for(int i = 0; i < pts.length; i++) {
         double x = pts[i].getX();
         double y = pts[i].getY();

         // dimension will never be NaN, measure may be NaN and may be both x or y
         // @by jasonshobe, Bug #19994, on linux, calling path.lineTo(x, NaN) has weird results so
         // explicitly skip it here, regardless of the value of ignoreNull.
         if(isNaN(pts[i])) {
            if(!first && !ignoreNull) {
               first = true;
               g2.fill(getPointShape(pts[i - 1], Math.max(3, size)));
            }
         }
         else if(first) {
            if(dotend && i == 0) {
               double angle = GTool.getAngle(pts[i], pts[i + 1]);
               x = x + size / 2 * FastMath.cos(angle);
               y = y + size / 2 * FastMath.sin(angle);
               g2.fill(getPointShape(pts[i], size));
            }
            else if(i == pts.length - 1) {
               g2.fill(getPointShape(pts[i], Math.max(3, size)));
            }

            path.moveTo(x, y);
            first = false;
         }
         else {
            samepos = samepos && pts[i].equals(pts[i - 1]);

            if(overplotx) {
               double diff = pts[i].getX() - pts[i - 1].getX();
               overplotx = Math.abs(diff) < 0.5;
            }

            if(dotend && isLastValid(i, pts)) {
               double angle = GTool.getAngle(pts[i], pts[i - 1]);
               x = x + size / 2 * FastMath.cos(angle);
               y = y + size / 2 * FastMath.sin(angle);
               lineTo(lineInfo.getType(), path, lastPt, x, y, horizontal);
               g2.fill(getPointShape(pts[i], Math.max(3, size)));
            }
            else {
               lineTo(lineInfo.getType(), path, lastPt, x, y, horizontal);
            }
         }

         lastPt = new Point2D.Double(x, y);
      }

      if(lineInfo.isClosed() && !first) {
         path.closePath();
         overplotx = false;
      }

      g2.setStroke(getStroke(style, size, !dotend, lineInfo.getType()));

      if(overplotx) {
         // optimization, avoid too many drawLine in output if lines overlap
         paintLineOverplot(g2, pts, lineInfo);
      }
      else {
         g2.draw(path);
      }

      // if all points at same position, it's same as if there is one point
      // and we draw a point to mark the data
      if(samepos) {
         g2.fill(getPointShape(pts[0], Math.max(3, size)));
      }

      if(pts.length > 1) {
         int last = pts.length - 1;
         drawArrows(g2, 0, pts[0], pts[1], color, color, size, size, lineInfo);
         drawArrows(g2, last - 1, pts[last - 1], pts[last], color, color, size, size, lineInfo);
      }
   }

   private static void lineTo(Type type, Path2D path, Point2D lastPt, double x, double y, boolean hor) {
      switch(type) {
      case JUMP:
      case STEP:
         if(hor) {
            path.lineTo(x, lastPt.getY());
         }
         else {
            path.lineTo(lastPt.getX(), y);
         }

         if(type == JUMP) {
            path.moveTo(x, y);
         }
         else {
            path.lineTo(x, y);
         }
         break;
      default:
         path.lineTo(x, y);
         break;
      }
   }

   /**
    * Paint overplotted line.
    */
   private static void paintLineOverplot(Graphics2D g2, Point2D[] pts, LineInfo lineInfo) {
      double x = Double.NaN;
      double ymin = Integer.MAX_VALUE;
      double ymax = Integer.MIN_VALUE;
      Path2D path = new Path2D.Double();
      boolean ignoreNull = lineInfo.isIgnoreNull();
      boolean first = true;

      for(Point2D pt : pts) {
         if(isNaN(pt) && !ignoreNull) {
            x = Double.NaN;
            first = true;
            continue;
         }

         double x0 = Math.round(pt.getX());

         if(x0 != x) {
            if(!Double.isNaN(x)) {
               if(Double.isNaN(pt.getY()) && !ignoreNull) {
                  if(!first) {
                     path.lineTo((float) x, (float) ymin);
                     path.lineTo((float) x, (float) ymax);
                  }

                  first = true;
                  x = Double.NaN;
                  continue;
               }
               else if(first) {
                  path.moveTo((float) x, (float) ymin);
                  path.moveTo((float) x, (float) ymax);
                  first = false;
               }
               else {
                  path.lineTo((float) x, (float) ymin);
                  path.lineTo((float) x, (float) ymax);
               }
            }

            x = x0;
            ymin = ymax = pt.getY();
         }
         else {
            ymin = Math.min(ymin, pt.getY());
            ymax = Math.max(ymax, pt.getY());
         }
      }

      if(!first) {
         path.lineTo((float) x, (float) ymin);
         path.lineTo((float) x, (float) ymax);
      }

      g2.draw(path);
   }

   /**
    * Get the stroke and change the join to round.
    */
   private static BasicStroke getStroke(GLine line, double size, boolean round,
                                        LineElement.Type type)
   {
      boolean step = type == JUMP || type == STEP;
      double linew = Math.max(line.getLineWidth(), size);
      BasicStroke s = (BasicStroke) line.getStroke(linew);
      int cap = step ? BasicStroke.CAP_BUTT : (round ? BasicStroke.CAP_ROUND : s.getEndCap());

      return new BasicStroke(s.getLineWidth(), cap,
                             step ? BasicStroke.JOIN_MITER : BasicStroke.JOIN_ROUND,
                             s.getMiterLimit(), s.getDashArray(), s.getDashPhase());
   }

   /**
    * Check if the point is missing value.
    */
   public static boolean isNaN(Point2D pt) {
      return Double.isNaN(pt.getX()) || Double.isNaN(pt.getY());
   }

   /**
    * Determine if the point at the specified index is the last valid (non-null/NaN) point in the
    * series.
    *
    * @param index the index of the current point.
    * @param pts   the array of points.
    *
    * @return {@code true} if the last valid point; {@code false} otherwise.
    */
   private static boolean isLastValid(int index, Point2D[] pts) {
      boolean result = true;

      for(int i = index + 1; i < pts.length; i++) {
         if(!isNaN(pts[i])) {
            result = false;
            break;
         }
      }

      return result;
   }

   /**
    * Paint the line shape.
    * @param alpha the transparency, 0-1.
    */
   private static void paintLine0(Graphics2D g2, double alpha, LineInfo lineInfo) {
      Point2D[] pts = lineInfo.getTransformedPoints();
      boolean jump = lineInfo.getType() == JUMP;
      boolean horizontal = GTool.isHorizontal(lineInfo.getScreenTransform());

      // if we don't have two points to draw a line, we should draw something
      // to indicate the presence of data. Otherwise we may get blank output
      // and make people think there is an error
      if(pts.length == 1) {
         Color color = lineInfo.getColor(0);
         double size = getLineSize(lineInfo.getSize(0));

         g2.setColor(color);
         g2.fill(getPointShape(pts[0], Math.max(3, size)));
         return;
      }

      boolean ignoreNull = lineInfo.isIgnoreNull();
      Shape lastLineShape = null;
      Shape endPointShape = null;
      Shape firstLineShape = null;
      int fillLineStyle = lineInfo.getFillLineStyle();
      boolean nullLine = false;

      for(int i = 0; i < pts.length; i++) {
         if(isNaN(pts[i])) {
            continue;
         }

         int next = i + 1;

         if(ignoreNull || fillLineStyle != 0) {
            while(next < pts.length && isNaN(pts[next])) {
               nullLine = true;
               next++;
            }
         }

         if(next == pts.length) {
            if(!lineInfo.isClosed()) {
               break;
            }

            next = 0;
         }

         boolean skipnext = isNaN(pts[next]);
         Color startColor = lineInfo.getColor(i);
         GLine startLine = lineInfo.getLine(i);
         double startSize = getLineSize(lineInfo.getSize(i));
         Color endColor0 = lineInfo.getColor(next);
         GLine endLine0 = lineInfo.getLine(next);
         double endSize0 = getLineSize(lineInfo.getSize(next));

         if(nullLine && fillLineStyle != 0) {
            startLine = endLine0 = new GLine(fillLineStyle);
            startColor = endColor0 = GTool.getColor(startColor, lineInfo.getFillLineAlpha());
            nullLine = false;
         }

         if(startLine == null) {
            startLine = GLine.THIN_LINE;
         }
         else {
            startSize = Math.max(startSize, startLine.getLineWidth());
         }

         if(endLine0 == null) {
            endLine0 = GLine.THIN_LINE;
         }
         else {
            endSize0 = Math.max(endSize0, endLine0.getLineWidth());
         }

         if(alpha != 1) {
            startColor = GTool.getColor(startColor, alpha);
            endColor0 = GTool.getColor(endColor0, alpha);
         }

         Shape lastPointShape;
         Color endColor = jump ? startColor : endColor0;
         GLine endLine = jump ? startLine : endLine0;
         double endSize = jump ? startSize : endSize0;

         // subtract line from point so the drawing doesn't overlap. The
         // overlapping would cause the point and line having different
         // color if alpha is not 1
         lastPointShape = getPointShape(pts[i], startSize);

         if(!lineInfo.isClosed() && i == pts.length - 2 && !skipnext) {
            endPointShape = getPointShape(pts[next], endSize);
         }

         if(lineInfo.isClosed() && i == pts.length - 1 && lastLineShape != null &&
            lastLineShape.getBounds().x > 0 && lastLineShape.getBounds().y > 0)
         {
            Area area = new Area(lastLineShape);
            area.add(new Area(firstLineShape));
            lastLineShape = area;
         }

         if(!skipnext) {
            lastLineShape = drawLine(g2, i, pts[i], pts[next],
                                     startColor, endColor,
                                     startSize, endSize,
                                     startLine, endLine,
                                     endColor0, lastLineShape,
                                     (startSize > 1) ? lastPointShape : null,
                                     (endSize > 1) ? endPointShape : null,
                                     alpha != 1, lineInfo.getType(), horizontal);
         }
         else {
            g2.setColor(startColor);
            g2.fill(lastPointShape);
            lastLineShape = lastPointShape;
         }

         if(firstLineShape == null) {
            firstLineShape = lastLineShape;
         }

         if(!skipnext) {
            drawArrows(g2, i, pts[i], pts[next], startColor, endColor, startSize, endSize, lineInfo);
         }
      }
   }

   /**
    * Draw the line segment from p1 to p2.
    * @param no_overlap true if end points shouldn't overlap with line.
    */
   private static Shape drawLine(Graphics2D g2, int idx, Point2D p1, Point2D p2,
                          Color c1, Color c2, double size1, double size2,
                          GLine gline1, GLine gline2, Color endColor,
                          Shape lastLineShape, Shape lastPointShape, Shape endPointShape,
                          boolean no_overlap, Type type, boolean hor)
   {
      boolean step = type == STEP;
      boolean jump = type == JUMP;
      Point2D end1 = p2;
      Point2D start2 = null;

      if(step || jump) {
         if(hor) {
            start2 = new Point2D.Double(p2.getX(), p1.getY());
            end1 = new Point2D.Double(p2.getX() + size2 / 2, p1.getY());
         }
         else {
            start2 = new Point2D.Double(p1.getX(), p2.getY());
            end1 = new Point2D.Double(p1.getX(), p2.getY() + size2 / 2);
         }
      }

      Shape shape = GLine.getShape(p1, end1, size1, size2, gline1, gline2);

      if(!no_overlap) {
         if(lastPointShape != null) {
            g2.setColor(c1);
            g2.fill(lastPointShape);
         }

         if(endPointShape != null) {
            g2.setColor(endColor);
            g2.fill(endPointShape);
         }
      }
      else if(lastPointShape != null) {
         Area area = new Area(shape);
         area.add(new Area(lastPointShape));

         if(endPointShape != null) {
            area.add(new Area(endPointShape));
         }

         if(no_overlap && lastLineShape != null) {
            area.subtract(new Area(lastLineShape));
         }

         shape = area;
      }

      GLine.paint(g2, p1, end1, c1, c2, shape);

      if(step) {
         drawLine(g2, idx, start2, p2, c2, c2, size2, size2, gline2, gline2, c2,
                  shape, shape, endPointShape, true, STRAIGHT, hor);
      }

      return shape;
   }

   /**
    * Draw arrows at ends.
    */
   private static void drawArrows(Graphics2D g2, int idx, Point2D p1, Point2D p2,
                           Color c1, Color c2, double size1, double size2, LineInfo lineInfo)
   {
      if(idx == 0 && lineInfo.isStartArrow()) {
         g2.setColor(c1);
         GTool.drawArrow(g2, p2, p1, 4 + size1);
      }

      if(idx == lineInfo.getPoints().length - 2 && lineInfo.isEndArrow()) {
         g2.setColor(c2);
         GTool.drawArrow(g2, p1, p2, 4 + size2);
      }
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      LineGeometry gobj = (LineGeometry) getGeometry();
      LineElement elem = (LineElement) gobj.getElement();
      int placement = gobj.getLabelPlacement();
      Point2D[] pts = getTransformedPoints();
      ColorFrame cframe = elem.getColorFrame();
      boolean radar = isClosed;
      // only keep the last label if same text for all. radar points are separate so this
      // shouldn't apply.
      boolean lastOnly = !radar && isTextIdentical();
      int[] idxs = gobj.getTupleIndexes();

      for(int i = 0; i < vtexts.length; i++) {
         if(vtexts[i] == null) {
            continue;
         }

         vgraph.removeVisual(vtexts[i]);

         if(!lastOnly || i == vtexts.length - 1) {
            int vidx = idxs[i % idxs.length];
            double radius = getLineSize(gobj.getSize(vidx));

            if(lastOnly) {
               TextSpec tspec = vtexts[i].getTextSpec();

               // display the text in the same color as the line
               if(cframe instanceof CategoricalColorFrame &&
                  cframe.getValues().length > 1 &&
                  tspec.getColor().equals(GDefaults.DEFAULT_TEXT_COLOR))
               {
                  Color lastColor = gobj.getColor(vidx);
                  tspec = tspec.clone();
                  tspec.setColor(lastColor);
                  vtexts[i].setTextSpec(tspec);
               }

               placement = GraphConstants.RIGHT;
               vtexts[i].setCollisionModifier(VOText.MOVE_UP);
            }

            vgraph.addVisual(vtexts[i]);
            layoutText0(vtexts[i], pts[i], radius, placement, vgraph);
         }
      }
   }

   /**
    * Check if all text labels have same value on a line.
    */
   private boolean isTextIdentical() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      LineElement elem = (LineElement) gobj.getElement();
      TextFrame tframe = elem.getTextFrame();
      boolean same = vtexts.length > 1 &&
         tframe != null && tframe.getField() != null &&
            !(tframe.getScale() instanceof LinearScale) &&
         // don't draw only label per line for stacked line or it
         // looks like some labels are missing
         !elem.isStack();

      if(same) {
         Object first = vtexts[0] == null ? null : vtexts[0].getLabel();

         for(VOText vtext : vtexts) {
            Object val = vtext == null ? null : vtext.getLabel();

            if(!CoreTool.equals(first, val)) {
               return false;
            }
         }
      }

      return same;
   }

   /**
    * Get the point shape.
    * @param size the point size.
    */
   private static Ellipse2D getPointShape(Point2D p, double size) {
      double r = size / 2;
      return new Ellipse2D.Double(p.getX() - r, p.getY() - r, size, size);
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      for(int i = 0; i < points.length; i++) {
         points[i] = (Point2D) coord.transformShape(points[i]);
      }
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      return lineInfo.getBounds();
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      LineGeometry gobj = (LineGeometry) getGeometry();
      int[] idxs = gobj.getTupleIndexes();
      double v = 0;
      double size = 0;

      switch(pos) {
      case GraphConstants.TOP:
      case GraphConstants.RIGHT:
         v = Integer.MIN_VALUE;
         break;
      case GraphConstants.LEFT:
      case GraphConstants.BOTTOM:
         v = Integer.MAX_VALUE;
         break;
      }

      for(int i = 0; i < points.length; i++) {
         int vidx = idxs[i % idxs.length];
         Point2D pt = getScreenTransform().transform(points[i], null);
         double radius = getLineSize(gobj.getSize(vidx)) / 2;

         switch(pos) {
         case GraphConstants.TOP:
            if(v < pt.getY() + radius) {
               v = pt.getY() + radius;
               size = radius;
            }
            break;
         case GraphConstants.RIGHT:
            if(v < pt.getX() + radius) {
               v = pt.getX() + radius;
               size = radius;
            }
            break;
         case GraphConstants.BOTTOM:
            if(v > pt.getY() - radius) {
               v = pt.getY() - radius;
               size = radius;
            }
         case GraphConstants.LEFT:
            if(v > pt.getX() - radius) {
               v = pt.getX() - radius;
               size = radius;
            }
            break;
         }
      }

      return size + 0.5;
   }

   /**
    * Set text position and size.
    */
   private void layoutText0(VOText vtext, Point2D pt, double r,
                            int placement, VGraph vgraph)
   {
      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = vtext.getPreferredHeight();
      double tx, ty;
      Point2D rpt;

      switch(placement) {
      case GraphConstants.BOTTOM:
         tx = pt.getX() - prefw / 2;
         ty = pt.getY() - r - prefh - TEXT_GAP;
         rpt = new Point2D.Double(pt.getX(), pt.getY() - r);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         tx = pt.getX() - r - prefw - TEXT_GAP;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX() - r, pt.getY());
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         tx = pt.getX() + r + TEXT_GAP;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX() + r, pt.getY());
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.CENTER:
         tx = pt.getX() - prefw / 2;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX(), pt.getY());
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.AUTO:
         placement = GraphConstants.TOP;
      case GraphConstants.TOP:
      default:
         tx = pt.getX() - prefw / 2;
         ty = pt.getY() + r + TEXT_GAP;
         rpt = new Point2D.Double(pt.getX(), pt.getY() + r);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      }

      vtext.setPosition(new Point2D.Double(tx, ty));
      vtext.setSize(new DimensionD(prefw, prefh));

      Point2D offset = vtext.getRotationOffset(rpt, placement);
      vtext.setOffset(offset);
      vtext.setPlacement(placement);
   }

   /**
    * Get the points on the line.
    */
   public Point2D[] getPoints() {
      return points;
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return MIN_WIDTH;
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      double w = Arrays.stream(vtexts)
         .filter(Objects::nonNull)
         .map(Visualizable::getPreferredWidth)
         .max(Double::compareTo)
         .orElse(0D);
      return Math.max(w, PREFERRED_WIDTH);
   }

   /**
    * Get the visual object's texts.
    * @return the VLabels of this visual object.
    */
   @Override
   public VOText[] getVOTexts() {
      return vtexts;
   }

   /**
    * Set row index which to map the subset point on line.
    */
   public void setIndexes(int[] ridx) {
      this.pidxs = ridx;
   }

   /**
    * Get row index which to map the subset point on line.
    */
   public int[] getIndexes() {
      return pidxs;
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      return lineInfo.getShapes();
   }

   /**
    * Size from the frame is treated as 0.5 point for 1.
    */
   private static double getLineSize(double size) {
      return 1 + (size - 1) * 0.5;
   }

   /**
    * Set the col index.
    * @param cidx index the specified column index.
    */
   @Override
   public void setColIndex(int cidx) {
      super.setColIndex(cidx);

      if(vtexts != null) {
         for(VOText vtext : vtexts) {
            if(vtext != null) {
               vtext.setColIndex(cidx);
            }
         }
      }
   }

   /**
    * Set the line is closed(circle) or not.
    * @param isClosed the line is closed(circle) or not.
    */
   public void setClosed(boolean isClosed) {
      this.isClosed = isClosed;
   }

   /**
    * Check the line is closed(circle) or not.
    * @return <tt>true</tt> if line is closed, <tt>false</tt> otherwise.
    */
   public boolean isClosed() {
      return isClosed;
   }

   /**
    * Get the points after screen transformation.
    */
   public Point2D[] getTransformedPoints() {
      return lineInfo.getTransformedPoints();
   }

   public boolean isRadar() {
      LineGeometry obj = (LineGeometry) getGeometry();
      LineElement elem = (LineElement) obj.getElement();

      return elem.isRadar();
   }

   @Override
   public void flip(VOText votext, double by) {
      Rectangle2D bounds = votext.getBounds();
      double y = bounds.getY() - bounds.getHeight() - VisualObject.TEXT_GAP;
      votext.setPosition(new Point2D.Double(bounds.getX(), y));
      votext.setPlacement(GraphConstants.BOTTOM);
   }

   @Override
   public Object clone() {
      LineVO vo = (LineVO) super.clone();
      vo.lineInfo = vo. new LineInfo0();
      vo.vtexts = cloneVOTexts(vtexts);
      return vo;
   }

   public interface LineInfo {
      public Point2D[] getPoints();
      public Color getColor(int idx);
      public double getSize(int idx);
      public GLine getLine(int idx);
      public AffineTransform getScreenTransform();

      default Point2D[] getTransformedPoints() {
         Point2D[] points = getPoints();
         Point2D[] pts = new Point2D[points.length];

         for(int i = 0; i < points.length; i++) {
            pts[i] = getScreenTransform().transform(points[i], null);
         }

         return pts;
      }

      default Rectangle2D getBounds() {
         Rectangle2D rect = null;
         Point2D[] pts = getTransformedPoints();

         for(int i = 0; i < pts.length; i++) {
            Point2D pt = pts[i];

            // null point is not drawn and should be ignored
            if(Double.isNaN(pt.getX()) || Double.isNaN(pt.getY())) {
               continue;
            }

            double radius = Math.max(getLineSize(getSize(i)), MIN_POINTSIZE) / 2 + 0.5;
            Rectangle2D rect0 = new Rectangle2D.Double(
               pt.getX() - radius, pt.getY() - radius,
               2 * radius, 2 * radius);

            if(rect == null) {
               rect = rect0;
            }
            else {
               rect = rect.createUnion(rect0);
            }
         }

         return rect;
      }

      default Shape[] getShapes() {
         Point2D[] points = getPoints();
         Shape[] shapes = new Shape[points.length];
         Point2D[] pts = new Point2D[points.length];

         for(int i = 0; i < points.length; i++) {
            pts[i] = getScreenTransform().transform(points[i], null);
            double size = Math.max(getLineSize(getSize(i)), MIN_POINTSIZE);
            shapes[i] = getPointShape(pts[i], size);
         }

         return shapes;
      }

      default LineElement.Type getType() {
         return STRAIGHT;
      }

      default boolean isIgnoreNull() {
         return true;
      }

      default boolean isStartArrow() {
         return false;
      }

      default boolean isEndArrow() {
         return false;
      }

      default boolean isClosed() {
         return false;
      }

      default int getFillLineStyle() {
         return 0;
      }

      default double getFillLineAlpha() {
         return 1;
      }
   }

   private class LineInfo0 implements LineInfo {
      @Override
      public Point2D[] getPoints() {
         return points;
      }

      @Override
      public Color getColor(int idx) {
         // VisualModel initialized using sorted data set, so should use tuple index instead
         // of the base (pidxs). @see LineElement.createGeometry().
         int[] pidxs = gobj().getTupleIndexes();
         return gobj().getColor(pidxs[idx % pidxs.length]);
      }

      @Override
      public double getSize(int idx) {
         int[] pidxs = gobj().getTupleIndexes();
         return gobj().getSize(pidxs[idx % pidxs.length]);
      }

      @Override
      public GLine getLine(int idx) {
         int[] pidxs = gobj().getTupleIndexes();
         return gobj().getLine(pidxs[idx % pidxs.length]);
      }

      @Override
      public boolean isIgnoreNull() {
         return elem().isIgnoreNull();
      }

      @Override
      public boolean isStartArrow() {
         return elem().isStartArrow();
      }

      @Override
      public boolean isEndArrow() {
         return elem().isEndArrow();
      }

      @Override
      public AffineTransform getScreenTransform() {
         return LineVO.this.getScreenTransform();
      }

      @Override
      public boolean isClosed() {
         return isClosed;
      }

      @Override
      public LineElement.Type getType() {
         return elem().getType();
      }

      @Override
      public int getFillLineStyle() {
         return elem().getFillLineStyle();
      }

      @Override
      public double getFillLineAlpha() {
         return elem().getFillLineAlpha();
      }

      private LineGeometry gobj() {
         return (LineGeometry) getGeometry();
      }

      private LineElement elem() {
         return (LineElement) gobj().getElement();
      }
   };

   private class BorderLineInfo extends LineInfo0 {
      public BorderLineInfo(Color borderColor) {
         this.borderColor = borderColor;
      }

      @Override
      public Color getColor(int idx) {
         return borderColor;
      }

      @Override
      public double getSize(int idx) {
         return super.getSize(idx) + 2;
      }

      private final Color borderColor;
   }

   private static final double MIN_POINTSIZE = 1;
   private static final double MIN_WIDTH = 5;
   private static final double PREFERRED_WIDTH = 10;
   private Point2D[] points; // the line points
   private VOText[] vtexts;
   private int[] pidxs;
   private boolean isClosed;
   private LineInfo lineInfo = new LineInfo0();
}
