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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.coord.Rect25Coord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.*;
import inetsoft.test.*;
import java.awt.*;
import java.util.*;

/**
 * {@link Bar3DVO}: front face geometry, and the colors of the top and right faces found by
 * pixel probes inside the painted faces of a 3D bar chart plotted with {@code Plotter}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class Bar3DVOTest {
   private static final double EPS = 1e-6;
   private static final Color COLOR = new Color(100, 150, 200);
   private static final Color BACKGROUND = new Color(200, 200, 200);
   /** translation of VGraph.paintGraph for a graph of height 400 at y 0. */
   private static final double FLIP = 399;

   /** 3D bar chart: categories A, B, C over a linear y scale. */
   private static class Chart {
      double[] values = { 40, 80, 60 };
      double min = 0;
      double max = 100;
      double depth = 15;
      boolean sizeFrame;
      boolean threeD = true;
      VGraph vg;

      Chart plot() {
         EGraph graph = new EGraph();
         IntervalElement elem = new IntervalElement("Cat", "m");
         CategoricalScale xs = new CategoricalScale("Cat");
         LinearScale ys = new LinearScale("m");
         Object[][] rows = new Object[values.length + 1][];
         rows[0] = new Object[] { "Cat", "m", "S" };

         for(int i = 0; i < values.length; i++) {
            rows[i + 1] = new Object[] { "" + (char) ('A' + i), values[i], (double) (i + 1) };
         }

         ys.setMin(min);
         ys.setMax(max);
         elem.setColorFrame(new StaticColorFrame(COLOR));

         if(sizeFrame) {
            elem.setSizeFrame(new LinearSizeFrame("S"));
         }

         graph.addElement(elem);
         graph.setScale("Cat", xs);
         graph.setScale("m", ys);

         if(threeD) {
            Rect25Coord coord = new Rect25Coord(xs, ys);
            coord.setDepth(depth);
            graph.setCoordinate(coord);
         }
         else {
            graph.setCoordinate(new RectCoord(xs, ys));
         }

         vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
         return this;
      }

      List<BarVO> bars() {
         List<BarVO> list = new ArrayList<>();

         for(VisualObject vo : GTool.getVOs(vg)) {
            if(vo instanceof BarVO) {
               list.add((BarVO) vo);
            }
         }

         return list;
      }

      Bar3DVO bar(int i) {
         return (Bar3DVO) bars().get(i);
      }
   }

   /** Paint the way VGraph.paintGraph does: the visual objects use y up, the image y down. */
   private static BufferedImage paint(BarVO vo) {
      BufferedImage img = new BufferedImage(700, 500, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();

      g.setColor(BACKGROUND);
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.translate(0, FLIP);
      g.scale(1, -1);
      vo.paint(g);
      g.dispose();
      return img;
   }

   /** The pixel at the chart-coordinate point (x, y). */
   private static Color pixel(BufferedImage img, double x, double y) {
      return new Color(img.getRGB((int) Math.floor(x), (int) Math.floor(FLIP - y)));
   }

   private static Rectangle2D front(BarVO vo) {
      return vo.getShapes()[0].getBounds2D();
   }

   private static void assertColor(Color expected, Color actual, String what) {
      assertEquals(expected.getRGB(), actual.getRGB(),
                   what + ": expected " + expected + " but was " + actual);
   }

   /** Check the top face, right face and front face colors of a bar with the given depth. */
   private static void assertFaces(Bar3DVO vo, double depth) {
      BufferedImage img = paint(vo);
      Rectangle2D f = front(vo);

      assertColor(COLOR, pixel(img, f.getCenterX(), f.getCenterY()), "front face");
      assertColor(GTool.darken(COLOR),
                  pixel(img, f.getMaxX() + depth / 2, f.getCenterY() + depth / 2), "right face");
      assertColor(GTool.brighten(COLOR),
                  pixel(img, f.getCenterX() + depth / 2, f.getMaxY() + depth / 2), "top face");
   }

   // ---------------------------------------------------------------- front face

   @Test
   @DisplayName("front face heights are in the ratio 40:80:60 and widths are equal")
   void frontFaceHeights() {
      Chart chart = new Chart().plot();
      Rectangle2D a = front(chart.bar(0));
      Rectangle2D b = front(chart.bar(1));
      Rectangle2D c = front(chart.bar(2));

      assertEquals(3, chart.bars().size());
      assertEquals(40.0 / 80, a.getHeight() / b.getHeight(), 1e-4);
      assertEquals(60.0 / 80, c.getHeight() / b.getHeight(), 1e-4);
      assertEquals(a.getWidth(), b.getWidth(), 1e-4);
      assertEquals(b.getWidth(), c.getWidth(), 1e-4);
   }

   @Test
   @DisplayName("the front face is painted in the element color")
   void frontColor() {
      Chart chart = new Chart().plot();
      BufferedImage img = paint(chart.bar(1));
      Rectangle2D f = front(chart.bar(1));

      assertColor(COLOR, pixel(img, f.getCenterX(), f.getCenterY()), "front face");
   }

   @Test
   @DisplayName("the right face is the darkened color, not the front color")
   void rightFaceDarker() {
      Chart chart = new Chart().plot();
      BufferedImage img = paint(chart.bar(1));
      Rectangle2D f = front(chart.bar(1));
      Color right = pixel(img, f.getMaxX() + 7.5, f.getCenterY() + 7.5);

      assertColor(GTool.darken(COLOR), right, "right face");
      assertNotEquals(COLOR.getRGB(), right.getRGB());
   }

   @Test
   @DisplayName("the top face is the brightened color, not the front color")
   void topFaceLighter() {
      Chart chart = new Chart().plot();
      BufferedImage img = paint(chart.bar(1));
      Rectangle2D f = front(chart.bar(1));
      Color top = pixel(img, f.getCenterX() + 7.5, f.getMaxY() + 7.5);

      assertColor(GTool.brighten(COLOR), top, "top face");
      assertNotEquals(COLOR.getRGB(), top.getRGB());
   }

   @Test
   @DisplayName("the side faces are sheared parallelograms: the corners of their bounding boxes "
      + "stay background")
   void facesAreSheared() {
      Chart chart = new Chart().plot();
      BufferedImage img = paint(chart.bar(1));
      Rectangle2D f = front(chart.bar(1));

      assertColor(BACKGROUND, pixel(img, f.getMaxX() + 7.5, f.getY() + 1), "right face, low corner");
      assertColor(BACKGROUND, pixel(img, f.getCenterX(), f.getMaxY() + 15 + 3), "above the top face");
   }

   @Test
   @DisplayName("depth 30 makes the right face reach a point that depth 15 leaves as background")
   void depthChangesFaces() {
      Chart shallow = new Chart().plot();
      Chart deep = new Chart();
      deep.depth = 30;
      deep.plot();

      Rectangle2D f15 = front(shallow.bar(1));
      Rectangle2D f30 = front(deep.bar(1));

      assertColor(BACKGROUND,
                  pixel(paint(shallow.bar(1)), f15.getMaxX() + 25, f15.getCenterY() + 25),
                  "depth 15");
      assertColor(GTool.darken(COLOR),
                  pixel(paint(deep.bar(1)), f30.getMaxX() + 25, f30.getCenterY() + 25),
                  "depth 30");
   }

   @Test
   @DisplayName("a negative bar has a darker right face and a lighter top face as well")
   void negativeBar() {
      Chart chart = new Chart();
      chart.values = new double[] { -30, 20, 40 };
      chart.min = -50;
      chart.max = 50;
      chart.plot();

      assertFaces(chart.bar(0), 15);
   }

   @Test
   @DisplayName("a bar lower than the depth still shows a darker right face and a lighter top face")
   void smallBar() {
      Chart chart = new Chart();
      chart.values = new double[] { 1, 80, 60 };
      chart.plot();

      assertTrue(front(chart.bar(0)).getHeight() < 15);
      assertFaces(chart.bar(0), 15);
   }

   /** Width of the front face of the largest bar divided by the distance between bar centres. */
   private static double largestBarRatio(boolean threeD) {
      Chart chart = new Chart();
      chart.sizeFrame = true;
      chart.threeD = threeD;
      chart.plot();

      List<Rectangle2D> boxes = new ArrayList<>();

      for(BarVO vo : chart.bars()) {
         boxes.add(front(vo));
      }

      boxes.sort(Comparator.comparingDouble(Rectangle2D::getCenterX));

      Rectangle2D largest = boxes.get(2);
      double slot = boxes.get(2).getCenterX() - boxes.get(1).getCenterX();

      return largest.getWidth() / slot;
   }

   @Test
   @DisplayName("the largest 3D bar is two logical units narrower than its slot; the 2D bar fills it")
   void largestBarWidth() {
      assertEquals(1.0, largestBarRatio(false), 1e-3);
      assertEquals(1 - 2 / (1000.0 / 3), largestBarRatio(true), 1e-3);
   }
   @Test
   void boundsCoverFaces() {
      for(boolean horizontal : new boolean[] { false, true }) {
         int w = horizontal ? 600 : 2000;
         int h = horizontal ? 2000 : 600;
         VGraph vg = bar(40, horizontal, false, false, w, h);
         List<Bar3DVO> bars = Chart3DTestUtil.collect(vg, Bar3DVO.class);
         assertEquals(40, bars.size());

         for(Bar3DVO vo : bars) {
            Rectangle2D bounds = vo.getBounds();
            Rectangle2D paintBounds = vo.getPaintBounds();
            Rectangle2D front = vo.getShapes()[0].getBounds2D();

            // getBounds() stays the front face (plus the half border)
            assertTrue(grow(front, 0.5 + 1e-6).contains(bounds),
                       "bounds " + bounds + " larger than the front face " + front);
            assertTrue(paintBounds.contains(bounds));

            // every painted pixel (front, right and top faces) is in the paint bounds
            Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, w, h);
            Rectangle2D img = Chart3DTestUtil.toImage(vg, paintBounds);
            assertTrue(grow(img, 1).contains(painted),
                       (horizontal ? "horizontal" : "vertical") + " bar painted " + painted +
                       " outside " + img);
         }
      }
   }

   @Test
   void tileCoveringOnlySideFacePaintsBar() {
      // few wide bars, so the right face of a bar is in the gap before the next bar
      VGraph vg = bar(6, false, false, false, 2000, 600);
      Bar3DVO vo = Chart3DTestUtil.collect(vg, Bar3DVO.class).get(2);
      Rectangle2D front = Chart3DTestUtil.toImage(vg, vo.getBounds());
      Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, 2000, 600);
      int x0 = (int) Math.ceil(front.getMaxX()) + 2;
      assertTrue(painted.getMaxX() > x0 + 5, "no right face painted");

      Rectangle tile = new Rectangle(x0, 0, (int) painted.getMaxX() - x0, 600);
      assertEquals(0, Chart3DTestUtil.missingPixels(vg, 2000, 600, tile),
                   "tile holding only the side face lost its pixels");
   }

   @Test
   void tilesPaintVerticalBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(40, false, false, false, 2000, 600),
                                                 2000, 600));
   }

   @Test
   void tilesPaintHorizontalBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(40, true, false, false, 600, 2000),
                                                 600, 2000));
   }

   @Test
   void tilesPaintStackedBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(30, false, true, false, 2000, 600),
                                                 2000, 600));
   }

   @Test
   void tilesPaintFacetedBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(20, false, false, true, 2400, 600),
                                                 2400, 600));
   }

   private static VGraph bar(int n, boolean horizontal, boolean stack, boolean facet,
                             int w, int h)
   {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "x", "y", "c", "g" });
      int groups = facet ? 3 : 1;

      for(int gi = 0; gi < groups; gi++) {
         for(int i = 0; i < n; i++) {
            double v = 20 + (i * 37 + gi * 11) % 80;
            rows.add(new Object[] { "C" + i, v, "s1", "G" + gi });

            if(stack) {
               rows.add(new Object[] { "C" + i, v / 2, "s2", "G" + gi });
            }
         }
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("x", "y");

      if(stack) {
         elem.setCollisionModifier(GraphElement.MOVE_STACK);
         CategoricalColorFrame colors = new CategoricalColorFrame("c");
         colors.getLegendSpec().setVisible(false);
         elem.setColorFrame(colors);
      }

      graph.addElement(elem);
      // no axes or legend: the tiles only need the bars, and their text needs the SVG support
      CategoricalScale xscale = new CategoricalScale("x");
      xscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      LinearScale yscale = new LinearScale("y");
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);

      if(stack) {
         yscale.setScaleRange(new StackRange());
      }

      Rect25Coord coord = new Rect25Coord(xscale, yscale);

      if(horizontal) {
         coord.transpose();
      }

      if(facet) {
         CategoricalScale g = new CategoricalScale("g");
         g.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
         RectCoord outer = horizontal ? new RectCoord(null, g) : new RectCoord(g, null);
         graph.setCoordinate(new FacetCoord(outer, coord));
      }
      else {
         graph.setCoordinate(coord);
      }

      return Plotter.getPlotter(graph).plotAndLayout(
         new DefaultDataSet(rows.toArray(new Object[0][])), 0, 0, w, h);
   }

   private static Rectangle2D grow(Rectangle2D r, double d) {
      return new Rectangle2D.Double(r.getX() - d, r.getY() - d, r.getWidth() + 2 * d,
                                    r.getHeight() + 2 * d);
   }
}
