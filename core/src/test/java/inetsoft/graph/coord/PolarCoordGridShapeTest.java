/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.PointVO;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82032: a horizontal grid line drawn as a shape on a polar chart was transformed into an
 * arc with twice the radius of the data at the same y value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PolarCoordGridShapeTest {
   private static final double EPS = 1e-6;

   /**
    * A horizontal line at y is transformed into an arc with the radius a point at y gets, and
    * the arc starts and ends at the transformed end points of the line.
    */
   @Test
   void horizontalLineArcMatchesPointRadius() {
      for(int type : new int[] { PolarCoord.THETA_RHO, PolarCoord.RHO, PolarCoord.PLUS }) {
         PolarCoord coord = new PolarCoord(new RectCoord());
         coord.setType(type);

         double y = 600;
         Point2D p1 = new Point2D.Double(100, y);
         Point2D p2 = new Point2D.Double(400, y);
         Shape arc = (Shape) coord.transformShape(new Line2D.Double(p1, p2));
         Point2D t1 = (Point2D) coord.transformShape(p1);
         Point2D t2 = (Point2D) coord.transformShape(p2);

         assertTrue(arc instanceof Arc2D, "type " + type + ": " + arc);
         Arc2D arc2 = (Arc2D) arc;
         assertEquals(t1.distance(0, 0), arc2.getWidth() / 2, EPS, "type " + type);

         // RHO maps every x to the same angle, so only the radius is comparable
         if(type == PolarCoord.RHO) {
            continue;
         }

         assertEquals(t1.getX(), arc2.getStartPoint().getX(), EPS, "type " + type);
         assertEquals(t1.getY(), arc2.getStartPoint().getY(), EPS, "type " + type);
         assertEquals(t2.getX(), arc2.getEndPoint().getX(), EPS, "type " + type);
         assertEquals(t2.getY(), arc2.getEndPoint().getY(), EPS, "type " + type);
      }
   }

   /**
    * End to end: the grid ring at a tick value passes through the plotted point with that
    * value, on a polar point chart with a radius axis and the default grid-as-shape setting,
    * at two chart sizes.
    */
   @Test
   void gridRingPassesThroughPointAtTickValue() {
      for(int size : new int[] { 400, 800 }) {
         Object[][] rows = { { "x", "y" }, { "a", 50 }, { "b", 50 }, { "c", 50 }, { "d", 50 } };
         EGraph graph = new EGraph();
         graph.addElement(new PointElement("x", "y"));
         LinearScale yscale = createScale("y");
         CategoricalScale xscale = new CategoricalScale("x");
         graph.setScale("y", yscale);
         graph.setScale("x", xscale);
         graph.setCoordinate(new PolarCoord(new RectCoord(xscale, yscale)));

         VGraph vgraph = Plotter.getPlotter(graph)
            .plotAndLayout(new DefaultDataSet(rows), 0, 0, size, size);
         List<Point2D> points = new ArrayList<>();

         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            if(vgraph.getVisual(i) instanceof PointVO vo) {
               points.add(vo.getScreenTransform().transform(vo.getPosition(), null));
            }
         }

         assertEquals(4, points.size());
         assertGridRingThroughPoints(vgraph, points, "polar " + size);
      }
   }

   /**
    * End to end on a radar (polar over parallel coord) with grid lines: the grid ring at 50
    * passes through the line vertices at 50 on each axis.
    */
   @Test
   void radarGridRingPassesThroughVertexAtTickValue() {
      for(int size : new int[] { 400, 800 }) {
         Object[][] rows = { { "m1", "m2", "m3" }, { 50, 50, 50 } };
         EGraph graph = new EGraph();
         LineElement elem = new LineElement();
         Scale[] scales = new Scale[3];

         for(int i = 0; i < scales.length; i++) {
            String field = "m" + (i + 1);
            elem.addDim(field);
            scales[i] = createScale(field);
            graph.setScale(field, scales[i]);
         }

         elem.setClosed(true);
         graph.addElement(elem);
         graph.setCoordinate(new PolarCoord(new ParallelCoord(scales)));

         VGraph vgraph = Plotter.getPlotter(graph)
            .plotAndLayout(new DefaultDataSet(rows), 0, 0, size, size);
         List<Point2D> points = new ArrayList<>();

         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            if(vgraph.getVisual(i) instanceof LineVO vo) {
               for(Point2D pt : vo.getPoints()) {
                  points.add(vo.getScreenTransform().transform(pt, null));
               }
            }
         }

         assertEquals(3, points.size());
         assertGridRingThroughPoints(vgraph, points, "radar " + size);
      }
   }

   private static LinearScale createScale(String field) {
      LinearScale scale = new LinearScale(field);
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      assertTrue(scale.getAxisSpec().isGridAsShape());
      return scale;
   }

   /**
    * The points are at the same value, evenly spread around the center, so the center is
    * their mean. Some painted grid line must have every path point at the points' radius.
    */
   private static void assertGridRingThroughPoints(VGraph vgraph, List<Point2D> points,
                                                   String label)
   {
      double cx = points.stream().mapToDouble(Point2D::getX).average().orElseThrow();
      double cy = points.stream().mapToDouble(Point2D::getY).average().orElseThrow();
      double pointRadius = points.get(0).distance(cx, cy);
      List<String> grids = new ArrayList<>();
      boolean found = false;

      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         Axis axis = vgraph.getAxis(i);

         for(int k = 0; k < axis.getGridLineCount(); k++) {
            GridLine line = axis.getGridLine(k);
            Shape shape = axis.getGridLineTransform().createTransformedShape(line.getShape());
            double min = Double.MAX_VALUE;
            double max = 0;

            // flatten so the points of a curve are on the curve itself
            for(PathIterator it = shape.getPathIterator(null, 0.01); !it.isDone(); it.next()) {
               double[] c = new double[6];

               if(it.currentSegment(c) != PathIterator.SEG_CLOSE) {
                  double r = Point2D.distance(c[0], c[1], cx, cy);
                  min = Math.min(min, r);
                  max = Math.max(max, r);
               }
            }

            grids.add(String.format("%.2f-%.2f", min, max));
            found = found || Math.abs(min - pointRadius) < 0.5 &&
               Math.abs(max - pointRadius) < 0.5;
         }
      }

      assertTrue(found, label + ": no grid line at the radius of the points at 50 (" +
                 pointRadius + "), grid line radii: " + grids);
   }
}
