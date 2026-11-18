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

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.VisualObject;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rings of a radar: even steps on unequal spokes, and polygons for a one-measure radar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarWebTest {
   @Test
   void equalSpokesRingAtTheirTicks() {
      double[] max = { 100, 100, 100 };
      int plain = spokes(false, max, GraphConstants.THIN_LINE).get(0).getGridLineCount();
      List<DefaultAxis> spokes = spokes(true, max, GraphConstants.THIN_LINE);

      assertTrue(plain > 1, "premise: several ticks");

      // the layout drops the ring on the outer edge, which the polar border draws
      int drawn = spokes.get(0).getScale().getTicks().length - 1;

      for(DefaultAxis spoke : spokes) {
         assertEquals(plain, spoke.getGridLineCount());
         assertEquals(drawn, spoke.getGridLineCount());
      }

      List<Double> radii = radii(spokes);
      double outer = radii.get(radii.size() - 1);
      double[] ticks = spokes.get(0).getScale().getTicks();

      assertEquals(drawn, radii.size(), "one radius per tick: " + radii);

      for(int i = 0; i < drawn; i++) {
         assertEquals(ticks[i] / ticks[drawn - 1], radii.get(i) / outer, 0.001, "tick " + i);
      }
   }

   @Test
   void unequalSpokesGetQuarterRings() {
      List<DefaultAxis> spokes = spokes(true, new double[] { 100, 7, 2000 },
                                        GraphConstants.THIN_LINE);

      // four rings are made; the layout drops the one on the outer edge, which the border draws
      for(DefaultAxis spoke : spokes) {
         assertEquals(3, spoke.getGridLineCount());
      }

      List<Double> radii = radii(spokes);
      assertEquals(3, radii.size(), "one distinct radius per ring: " + radii);
      double[] expected = { 0.25, 0.5, 0.75 };

      for(int i = 0; i < 3; i++) {
         assertEquals(expected[i] / expected[2], radii.get(i) / radii.get(2), 0.001, "ring " + i);
      }

   }

   @Test
   void unequalSpokesWithoutTheFlagKeepTodaysRings() {
      List<DefaultAxis> spokes = spokes(false, new double[] { 100, 7, 2000 },
                                        GraphConstants.THIN_LINE);
      boolean notFour = false;

      for(DefaultAxis spoke : spokes) {
         assertEquals(spoke.getScale().getTicks().length - 1, spoke.getGridLineCount(),
                      "max " + spoke.getScale().getMax() + " follows its own ticks, less the outer edge");
         notFour |= spoke.getGridLineCount() != 3;
      }

      assertTrue(notFour, "not the even steps");
   }

   @Test
   void aOneMeasureRadarDrawsPolygons() {
      List<Shape> on = ringShapes(oneVar(true));
      List<Shape> off = ringShapes(oneVar(false));

      assertFalse(on.isEmpty(), "premise: ring segments");
      assertFalse(off.isEmpty(), "premise: ring segments");
      assertTrue(on.stream().noneMatch(RadarWebTest::curved), "polygon chords");
      assertTrue(off.stream().allMatch(RadarWebTest::curved), "arcs");
   }

   @Test
   void ringsDrawUnderTheSeries() {
      VGraph vgraph = vgraph(true, new double[] { 100, 7, 2000 }, GraphConstants.THIN_LINE);
      int rings = 0;

      for(DefaultAxis spoke : spokesOf(vgraph)) {
         for(int i = 0; i < spoke.getGridLineCount(); i++) {
            assertEquals(GDefaults.GRIDLINE_Z_INDEX, spoke.getGridLine(i).getZIndex());
            rings++;
         }
      }

      assertEquals(9, rings);
      assertFalse(GTool.getVOs(vgraph).isEmpty(), "premise: a series");

      for(VisualObject vo : GTool.getVOs(vgraph)) {
         assertTrue(vo.getZIndex() > GDefaults.GRIDLINE_Z_INDEX, "series z " + vo.getZIndex());
      }
   }

   @Test
   void classicRadarIsUnchanged() {
      List<DefaultAxis> spokes = spokes(false, new double[] { 100, 7, 2000 }, GraphConstants.NONE);

      assertEquals(3, spokes.size());

      for(DefaultAxis spoke : spokes) {
         assertEquals(0, spoke.getGridLineCount());
      }
   }

   // the distinct ring radii in ascending order; the centre is the mean of the segment starts
   private static List<Double> radii(List<DefaultAxis> spokes) {
      List<Point2D> starts = new ArrayList<>();

      for(DefaultAxis spoke : spokes) {
         for(int i = 0; i < spoke.getGridLineCount(); i++) {
            starts.add(start(spoke.getGridLine(i).getShape()));
         }
      }

      double cx = 0;
      double cy = 0;

      for(Point2D p : starts) {
         cx += p.getX() / starts.size();
         cy += p.getY() / starts.size();
      }

      List<Double> radii = new ArrayList<>();

      for(Point2D p : starts) {
         double r = p.distance(cx, cy);
         boolean dup = false;

         for(double r0 : radii) {
            dup |= Math.abs(r0 - r) < 0.5;
         }

         if(!dup) {
            radii.add(r);
         }
      }

      radii.sort(null);
      return radii;
   }

   // the first point of a shape
   private static Point2D start(Shape shape) {
      double[] pt = new double[6];
      PathIterator it = shape.getPathIterator(null);
      it.currentSegment(pt);
      return new Point2D.Double(pt[0], pt[1]);
   }

   // whether a shape has a curved segment, as an arc does
   private static boolean curved(Shape shape) {
      double[] pt = new double[6];

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         int type = it.currentSegment(pt);

         if(type == PathIterator.SEG_QUADTO || type == PathIterator.SEG_CUBICTO) {
            return true;
         }
      }

      return false;
   }

   private static List<Shape> ringShapes(VGraph vgraph) {
      List<Shape> shapes = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         for(int i = 0; i < axis.getGridLineCount(); i++) {
            shapes.add(axis.getGridLine(i).getShape());
         }
      }

      return shapes;
   }

   private static List<DefaultAxis> spokes(boolean even, double[] max, int gridStyle) {
      return spokesOf(vgraph(even, max, gridStyle));
   }

   // a 400x400 radar over one measure per max, each spoke from 0 to its max
   private static VGraph vgraph(boolean even, double[] max, int gridStyle) {
      int n = max.length;
      String[] names = new String[n];
      Scale[] scales = new Scale[n];
      Object[] low = new Object[n];
      Object[] high = new Object[n];

      for(int i = 0; i < n; i++) {
         names[i] = "m" + i;
         LinearScale scale = new LinearScale(names[i]);
         scale.setMin(0);
         scale.setMax(max[i]);
         scale.getAxisSpec().setGridStyle(gridStyle);
         scales[i] = scale;
         low[i] = max[i] / 4;
         high[i] = max[i] / 2;
      }

      EGraph graph = new EGraph();
      AreaElement area = new AreaElement();

      for(String name : names) {
         area.addDim(name);
      }

      graph.addElement(area);
      ParallelCoord inner = new ParallelCoord(scales);
      inner.setEvenRings(even);
      PolarCoord polar = new PolarCoord(inner);
      polar.setWebGrid(true);
      graph.setCoordinate(polar);
      Object[][] rows = { names, low, high };

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 400);
   }

   private static VGraph oneVar(boolean sides) {
      EGraph graph = new EGraph();
      graph.addElement(new AreaElement("cat", "m"));
      Object[][] rows = new Object[6][];
      rows[0] = new Object[] { "cat", "m" };

      for(int i = 1; i < 6; i++) {
         rows[i] = new Object[] { "c" + i, i * 10.0 };
      }

      DefaultDataSet data = new DefaultDataSet(rows);
      LinearScale scale = new LinearScale("m");
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      OneVarParallelCoord inner = new OneVarParallelCoord("m", "cat", scale);
      inner.init(data);
      PolarCoord polar = new PolarCoord(inner);
      polar.setWebGrid(true);
      polar.setSidesFromSpokes(sides);
      graph.setCoordinate(polar);

      return Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 400);
   }

   private static List<DefaultAxis> spokesOf(VGraph vgraph) {
      List<DefaultAxis> spokes = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof DefaultAxis a) {
            spokes.add(a);
         }
      }

      return spokes;
   }
}
