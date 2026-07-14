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

import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.RelationEdgeGeometry;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.mxgraph.model.mxGeometry;
import inetsoft.graph.mxgraph.model.mxICell;
import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.util.graphics.SVGSupport;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Visualizable object for a relation edge.
 *
 * @version 13.6
 * @author InetSoft Technology Corp
 */
public class RelationEdgeVO extends ElementVO {
   public RelationEdgeVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      setZIndex(GDefaults.VO_Z_INDEX - 1); // edges below nodes
      RelationEdgeGeometry obj = (RelationEdgeGeometry) gobj;
      this.edges = obj.getEdges();
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      RelationEdgeGeometry gobj = (RelationEdgeGeometry) getGeometry();
      RelationElement elem = (RelationElement) gobj.getElement();

      SVGSupport svg = SVGSupport.isSVGContext(g) ? SVGSupport.getInstance() : null;

      if(svg != null) {
         Map<String, String> edgeAttrs = new LinkedHashMap<>();
         edgeAttrs.put(SVGSupport.ATTR_ROW, String.valueOf(gobj.getRowIndex()));
         mxCell cell = gobj.getEdge();
         if(cell != null && cell.getSource() != null && cell.getTarget() != null) {
            edgeAttrs.put(SVGSupport.ATTR_SOURCE, cell.getSource().getId());
            edgeAttrs.put(SVGSupport.ATTR_TARGET, cell.getTarget().getId());
         }
         svg.beginAnnotationGroup(g, SVGSupport.ANNOTATION_RELATION_EDGE, edgeAttrs);
      }

      try {
         Graphics2D g2 = (Graphics2D) g.create();
         Color color = gobj.getColor(0);
         GLine line = gobj.getLine(0);
         double size = gobj.getSize(0);

         color = applyAlpha(color);
         g2.setColor(color);

         if(line != null) {
            g2.setStroke(line.getStroke(size));
         }
         else {
            g2.setStroke(new BasicStroke((float) size));
         }

         if(elem.getAlgorithm() == RelationElement.Algorithm.COMPACT_TREE) {
            GTool.setRenderingHint(g2, false);
         }
         else {
            GTool.setRenderingHint(g2, true);
         }

         Shape clipped = getShapeClippedEdge(gobj, elem);

         if(clipped != null) {
            g2.draw(clipped);
         }
         else {
            for(Shape edge : edges) {
               edge = getScreenTransform().createTransformedShape(edge);
               g2.draw(edge);
            }
         }

         g2.dispose();
      }
      finally {
         if(svg != null) {
            svg.endAnnotationGroup(g);
         }
      }
   }

   /**
    * For nodes painted with a custom (non-rectangular) shape, the layout's connector attaches the
    * edge to the midpoint of the node's bounding-box side. That only lines up with the painted node
    * for shapes that touch their box at the side midpoints (circle, diamond, square); for shapes
    * such as a triangle it lands in the empty corner, leaving a visible gap (Bug #75655).
    *
    * This recomputes a straight edge from center to center and clips each end to the actual painted
    * node shape boundary. The shapes are rebuilt in screen space using the same transform and
    * GShape.getShape() call as RelationVO, so the geometry (including the flipY orientation) matches
    * the painted node exactly.
    *
    * @return the clipped screen-space edge, or null to fall back to the default edge drawing
    * (default/rounded nodes, NIL shape, or layout-routed multi-point edges).
    */
   private Shape getShapeClippedEdge(RelationEdgeGeometry gobj, RelationElement elem) {
      GShape nodeShape = elem.getNodeShape();

      if(nodeShape == null) {
         return null;
      }

      mxCell edge = gobj.getEdge();

      if(edge == null || edge.getSource() == null || edge.getTarget() == null) {
         return null;
      }

      // smooth (curved) edges are only produced for the CIRCLE layout; leave them to the default
      // curve drawing (mirrors the condition in RelationEdgeGeometry.getEdges()).
      boolean smooth = elem.isSmoothEdges()
         && elem.getAlgorithm() == RelationElement.Algorithm.CIRCLE
         && elem.getLayoutCenter() != null;

      if(smooth) {
         return null;
      }

      // only handle straight edges; routed layouts (tree/hierarchical) keep the existing behavior.
      mxGeometry egeo = edge.getGeometry();
      List<mxPoint> pts = egeo != null ? egeo.getPoints() : null;

      if(pts != null && !pts.isEmpty()) {
         return null;
      }

      Shape srcShape = getScreenNodeShape(nodeShape, edge.getSource());
      Shape tgtShape = getScreenNodeShape(nodeShape, edge.getTarget());

      if(srcShape == null || tgtShape == null) {
         return null;
      }

      Rectangle2D sb = srcShape.getBounds2D();
      Rectangle2D tb = tgtShape.getBounds2D();
      Point2D sc = new Point2D.Double(sb.getCenterX(), sb.getCenterY());
      Point2D tc = new Point2D.Double(tb.getCenterX(), tb.getCenterY());

      Point2D p1 = clipToBoundary(srcShape, sc, tc);
      Point2D p2 = clipToBoundary(tgtShape, tc, sc);

      if(p1 == null || p2 == null) {
         return null;
      }

      return new Line2D.Double(p1, p2);
   }

   // Build the painted node shape in screen space, matching RelationVO.paint(). Returns null when
   // the shape is NIL (getShape returns null), in which case the node is painted as a rectangle.
   private Shape getScreenNodeShape(GShape nodeShape, mxICell node) {
      mxGeometry geo = node.getGeometry();

      if(geo == null) {
         return null;
      }

      Rectangle2D b = getScreenTransform().createTransformedShape(geo.getRectangle()).getBounds2D();
      return nodeShape.getShape(b.getX(), b.getY(), b.getWidth(), b.getHeight());
   }

   // Find where the ray from 'from' toward 'to' exits the shape boundary. Uses a flattening path
   // iterator so it works for polygons and curved shapes alike. Returns the intersection farthest
   // from 'from' (the outermost crossing, correct for concave shapes such as star/cross).
   private static Point2D clipToBoundary(Shape shape, Point2D from, Point2D to) {
      PathIterator it = shape.getPathIterator(null, 0.5);
      double[] coords = new double[6];
      double px = 0, py = 0, sx = 0, sy = 0;
      Point2D best = null;
      double bestDistSq = -1;

      for(; !it.isDone(); it.next()) {
         int type = it.currentSegment(coords);
         double ex, ey; // segment end point

         if(type == PathIterator.SEG_MOVETO) {
            px = sx = coords[0];
            py = sy = coords[1];
            continue;
         }
         else if(type == PathIterator.SEG_LINETO) {
            ex = coords[0];
            ey = coords[1];
         }
         else if(type == PathIterator.SEG_CLOSE) {
            ex = sx;
            ey = sy;
         }
         else {
            continue;
         }

         Point2D ip = segIntersect(from, to, px, py, ex, ey);

         if(ip != null) {
            double d = from.distanceSq(ip);

            if(d > bestDistSq) {
               bestDistSq = d;
               best = ip;
            }
         }

         px = ex;
         py = ey;
      }

      return best;
   }

   // Intersection point of segment p1-p2 and segment (x3,y3)-(x4,y4), or null if they don't cross.
   private static Point2D segIntersect(Point2D p1, Point2D p2, double x3, double y3,
                                       double x4, double y4)
   {
      double x1 = p1.getX(), y1 = p1.getY(), x2 = p2.getX(), y2 = p2.getY();
      double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

      if(denom == 0) {
         return null;
      }

      double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
      double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / denom;

      if(t < 0 || t > 1 || u < 0 || u > 1) {
         return null;
      }

      return new Point2D.Double(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
   }

   @Override
   public void transform(Coordinate coord) {
   }

   @Override
   public Rectangle2D getBounds() {
      Rectangle2D bounds = null;

      for(Shape edge : edges) {
         edge = getScreenTransform().createTransformedShape(edge).getBounds2D();
         bounds = bounds == null ? edge.getBounds2D() : bounds.createUnion(edge.getBounds2D());
      }

      return bounds;
   }

   @Override
   protected double getMinWidth0() {
      return 1;
   }

   @Override
   protected double getPreferredWidth0() {
      return getMinWidth0();
   }

   @Override
   public VOText[] getVOTexts() {
      return new VOText[0];
   }

   @Override
   public Shape[] getShapes() {
      return new Shape[0];
   }

   @Override
   public void layoutText(VGraph vgraph) {
   }

   private List<Shape> edges = new ArrayList<>();
   private static final Color DEFAULT_LINE_COLOR = new Color(0xafafad);

}
