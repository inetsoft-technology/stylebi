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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.data.SumDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.form.LineForm;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern waterfall keeps a tiny step at least 2px, grown from its start, and keeps its bridge
 * lines at their usual gap from bars the width clamp narrowed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WaterfallModernTest {
   @Test
   void aTinyStepIsTwoPixelsFromItsStart() {
      Waterfall chart = waterfall(true, STEPS);
      Rectangle2D first = chart.bars().get(0).getPath().getBounds2D();
      BarVO tiny = chart.bars().get(1);
      Rectangle2D box = tiny.getPath().getBounds2D();

      assertTrue(first.getHeight() > 10, "premise: the +100 step is tall " + first);
      assertEquals(2, box.getHeight(), 0.01, "the step's height");
      // the step starts where the +100 step ends, and grows down from there
      assertEquals(first.getMaxY(), box.getMaxY(), 0.01, "the step's start edge");
      assertEquals(ends(chart.bridges().get(0))[1].getY(), box.getMaxY(), 0.01,
                   "the incoming bridge meets the step's start");
      assertEquals(box, tiny.getPreRoundingBounds(), "the region matches the drawn step");
      assertEquals(box, tiny.getShapes()[0].getBounds2D(), "the linkable shape");
   }

   @Test
   void aZeroStepDrawsNoBar() {
      Waterfall chart = waterfall(true, STEPS);
      BarVO zero = chart.bars().get(2);

      assertEquals(0, zero.getPath().getBounds2D().getHeight(), 0, "the zero step's height");
   }

   @Test
   void bridgesKeepTheirGapFromClampedBars() {
      Waterfall chart = waterfall(true, STEPS);
      double slot = chart.bars().get(0).getSizeFactor();

      assertTrue(slot / 2 > 32, "premise: an unclamped bar is wider than the cap " + slot);

      for(int i = 0; i < chart.bars().size(); i++) {
         assertEquals(32, chart.bars().get(i).getPath().getBounds2D().getWidth(), 0.5,
                      "premise: bar " + i + " is clamped");
      }

      assertBridgeGaps(chart, (0.13 - 0.06 * CORNER) * slot);
   }

   @Test
   void anUnclampedTotalKeepsItsEnd() {
      Waterfall chart = waterfall(true, false, false, STEPS);
      double slot = chart.bars().get(0).getSizeFactor();
      Rectangle2D total = chart.bars().get(4).getPath().getBounds2D();

      assertEquals(32, chart.bars().get(3).getPath().getBounds2D().getWidth(), 0.5,
                   "premise: the last step is clamped");
      assertEquals(0.5 * slot, total.getWidth(), 0.5, "premise: the Total is not clamped");
      assertBridgeGaps(chart, (0.13 - 0.06 * CORNER) * slot);
   }

   @Test
   void aHorizontalWaterfallKeepsItsStepAndGaps() {
      Waterfall chart = waterfall(true, true, true, STEPS);
      Rectangle2D first = chart.bars().get(0).getPath().getBounds2D();
      Rectangle2D box = chart.bars().get(1).getPath().getBounds2D();
      double slot = chart.bars().get(0).getSizeFactor();

      assertEquals(32, box.getHeight(), 0.5, "premise: the step is clamped");
      assertEquals(2, box.getWidth(), 0.01, "the step's length");
      // a fall grows toward the lower values, from where the +100 step ends
      assertEquals(first.getMaxX(), box.getMaxX(), 0.01, "the step's start edge");
      assertBridgeGaps(chart, (0.13 - 0.06 * CORNER) * slot);
   }

   @Test
   void aBridgeWithNoRoomBetweenWidenedBarsIsNotDrawn() {
      double[] steps = new double[150];
      Arrays.fill(steps, 10);
      Waterfall chart = waterfall(true, steps);
      double slot = chart.bars().get(0).getSizeFactor();

      assertTrue(slot < 4, "premise: the bars widen to 3px and leave no gap " + slot);
      assertFalse(chart.bridges().isEmpty(), "premise: slot units leave room for the bridges");

      for(LineFormVO bridge : waterfall(false, steps).bridges()) {
         assertEquals(2, bridge.getScreenPoints().length, "premise: a classic bridge is drawn");
      }

      for(LineFormVO bridge : chart.bridges()) {
         Point2D[] ends = bridge.getScreenPoints();
         assertEquals(0, ends.length, "a bridge is drawn " + Arrays.toString(ends));
         assertFalse(paints(bridge), "a bridge paints");
      }
   }

   @Test
   void classicWaterfallUnchanged() {
      Waterfall chart = waterfall(false, STEPS);
      double slot = chart.bars().get(0).getSizeFactor();
      double tiny = chart.bars().get(1).getPath().getBounds2D().getHeight();

      assertTrue(tiny > 0 && tiny < 1, "the -0.01 step " + tiny);

      for(int i = 0; i < chart.bars().size(); i++) {
         assertEquals(0.5 * slot, chart.bars().get(i).getPath().getBounds2D().getWidth(), 0.5,
                      "bar " + i + " is half its slot");
      }

      for(LineFormVO bridge : chart.bridges()) {
         assertSame(LineForm.class, bridge.getForm().getClass(), "a bridge's form");
         assertSame(LineFormVO.class, bridge.getClass(), "a bridge's visual");
      }

      // the gap addBridgeForm leaves beyond a bar with square corners
      assertBridgeGaps(chart, 0.13 * slot);
   }

   // each bridge ends the gap away from the bars it joins, and its bounds hold its ends
   private static void assertBridgeGaps(Waterfall chart, double gap) {
      List<BarVO> bars = chart.bars();
      assertEquals(bars.size() - 1, chart.bridges().size(),
                   "premise: a bridge between each pair of bars");

      for(int i = 0; i < chart.bridges().size(); i++) {
         LineFormVO bridge = chart.bridges().get(i);
         Point2D[] ends = ends(bridge);
         Rectangle2D bounds = bridge.getBounds();

         assertEquals(gap, gap(bars.get(i), ends[0]), 0.5, "bridge " + i + " from its bar");
         assertEquals(gap, gap(bars.get(i + 1), ends[1]), 0.5, "bridge " + i + " to its bar");

         for(Point2D end : ends) {
            assertTrue(bounds.getMinX() - 0.01 <= end.getX() &&
                       bounds.getMaxX() + 0.01 >= end.getX() &&
                       bounds.getMinY() - 0.01 <= end.getY() &&
                       bounds.getMaxY() + 0.01 >= end.getY(),
                       "bridge " + i + "'s bounds " + bounds + " hold " + end);
         }
      }
   }

   // how far a point lies outside a bar's drawn bounds
   private static double gap(BarVO bar, Point2D end) {
      Rectangle2D box = bar.getPath().getBounds2D();
      return Math.max(Math.max(box.getMinX() - end.getX(), end.getX() - box.getMaxX()),
                      Math.max(box.getMinY() - end.getY(), end.getY() - box.getMaxY()));
   }

   private record Waterfall(List<BarVO> bars, List<LineFormVO> bridges) {
   }

   private static Waterfall waterfall(boolean modern, double[] steps) {
      return waterfall(modern, modern, false, steps);
   }

   // a waterfall as GraphGenerator builds it, with the Total drawn by its own element
   private static Waterfall waterfall(boolean modern, boolean totalClamp, boolean transposed,
                                      double[] steps)
   {
      Object[][] rows = new Object[steps.length + 1][];
      rows[0] = new Object[] { STEP, DELTA };

      for(int i = 0; i < steps.length; i++) {
         rows[i + 1] = new Object[] { "S" + i, steps[i] };
      }

      IntervalElement bar = new IntervalElement(STEP, DELTA);
      IntervalElement sumBar = new IntervalElement(STEP, TOTAL);

      for(IntervalElement elem : new IntervalElement[] { bar, sumBar }) {
         elem.setWaterfall(true);
         elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
         elem.setRoundAllCorners(true);

         if(modern) {
            elem.setCornerRadius(CORNER);
            elem.setCornerFloor(true);
            elem.setWidthClamp(true);
            elem.setMinStepPx(2);
         }
      }

      // e.g. an author's size on the Total
      sumBar.setWidthClamp(totalClamp);
      bar.setStackNegative(false);
      bar.setStackGroup(false);
      bar.setVisualStacked(false);
      bar.setBridgeLine(null, new GLine(GraphConstants.THIN_LINE));

      LinearScale yscale = new LinearScale(DELTA, TOTAL);
      StackRange range = new StackRange();
      range.setStackNegative(false);
      yscale.setScaleRange(range);
      RectCoord coord = new RectCoord(new CategoricalScale(STEP), yscale);

      if(transposed) {
         coord.transpose();
      }

      EGraph graph = new EGraph();
      graph.addElement(bar);
      graph.addElement(sumBar);
      graph.setCoordinate(coord);
      SumDataSet data = new SumDataSet(new DefaultDataSet(rows), new String[] { DELTA }, STEP);
      // a horizontal chart is as tall as a vertical one is wide, so its bars need the cap too
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(data, 0, 0, transposed ? 240 : 400, transposed ? 400 : 240);
      List<BarVO> bars = new ArrayList<>();
      List<LineFormVO> bridges = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO vo) {
            bars.add(vo);
         }
         else if(vgraph.getVisual(i) instanceof LineFormVO vo) {
            bridges.add(vo);
         }
      }

      // in row order; the Total is the last row
      bars.sort(Comparator.comparingInt(BarVO::getSubRowIndex));
      bridges.sort(Comparator.comparingDouble(b -> b.getPoints()[0].getX()));
      assertEquals(steps.length + 1, bars.size(), "premise: a bar per step and the Total");
      return new Waterfall(bars, bridges);
   }

   // the bridge's drawn ends, the start first
   private static Point2D[] ends(LineFormVO bridge) {
      Point2D[] ends = bridge.getScreenPoints();
      assertEquals(2, ends.length, "premise: a bridge has two ends");
      return ends;
   }

   // whether painting the bridge leaves a pixel (a drawn level line needs SVG support)
   private static boolean paints(LineFormVO bridge) {
      BufferedImage image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      bridge.paint(g);
      g.dispose();

      for(int x = 0; x < image.getWidth(); x++) {
         for(int y = 0; y < image.getHeight(); y++) {
            if((image.getRGB(x, y) >>> 24) != 0) {
               return true;
            }
         }
      }

      return false;
   }

   private static final String STEP = "Step";
   private static final String DELTA = "Delta";
   private static final String TOTAL = SumDataSet.SUM_HEADER_PREFIX + DELTA;
   private static final double[] STEPS = { 100, -0.01, 0, 50 };
   private static final double CORNER = 0.08;
}
