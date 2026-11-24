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
package inetsoft.graph.guide.axis;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.LogScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A crowded value axis with a zero line keeps its 0 label when labels are thinned; an axis
 * without a zero line (classic) thins as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultAxisZeroLabelTest {
   private static final Color ZERO = Color.decode("#85827A");
   private static final int[] HEIGHTS = { 60, 70, 80, 90, 100, 110, 120, 140, 160, 180 };
   private static final int[] WIDTHS = { 80, 100, 120, 140, 160, 200, 240 };
   // ranges whose ticks cross zero, at odd and even steps from the bottom tick
   private static final double[][] CROSSING = {
      { -225, 295 }, { -450, 550 }, { -2099.8, 2463.2 }, { -150, 750 }, { -75, 125 },
      { -1.5, 2.5 }, { -50, 250 }, { -250, 50 }, { -10, 90 }, { -90, 10 }, { -120, 380 },
      { -380, 120 } };

   @Test
   void zeroThreeStepsUpIsKept() {
      // ticks -300..300 by 100
      assertEquals(List.of(-200.0, 0.0, 200.0), shown(bar(-225, 295, ZERO, 300, 100)));
   }

   @Test
   void theCrowdedCardRangesKeepZero() {
      // ticks -3000..3000 by 1000, -600..600 by 200, -200..800 by 200
      assertEquals(List.of(-2000.0, 0.0, 2000.0), shown(bar(-2099.8, 2463.2, ZERO, 300, 100)));
      assertEquals(List.of(-400.0, 0.0, 400.0), shown(bar(-450, 550, ZERO, 300, 100)));
      assertEquals(List.of(0.0, 400.0, 800.0), shown(bar(-150, 750, ZERO, 300, 80)));
   }

   @Test
   void classicKeepsTodaysLabels() {
      assertEquals(List.of(-300.0, -100.0, 100.0, 300.0), shown(bar(-225, 295, null, 300, 100)));
      assertEquals(List.of(-300.0, 100.0), shown(bar(-225, 295, null, 300, 60)));
      assertEquals(List.of(-3000.0, -1000.0, 1000.0, 3000.0),
                   shown(bar(-2099.8, 2463.2, null, 300, 100)));
      assertEquals(List.of(-600.0, -200.0, 200.0, 600.0), shown(bar(-450, 550, null, 300, 100)));
      assertEquals(List.of(-200.0, 200.0, 600.0), shown(bar(-150, 750, null, 300, 80)));
      assertEquals(List.of(-300.0, -100.0, 100.0, 300.0),
                   shown(hbar(-225, 295, null, 140)));
   }

   @Test
   void zeroAnEvenStepUpIsUnchanged() {
      // ticks -100..150 by 50
      assertEquals(List.of(-100.0, 0.0, 100.0), shown(bar(-75, 125, null, 300, 80)));
      assertEquals(List.of(-100.0, 0.0, 100.0), shown(bar(-75, 125, ZERO, 300, 80)));
   }

   @Test
   void whereClassicShowsZeroNothingChanges() {
      for(double[] r : CROSSING) {
         for(int h : HEIGHTS) {
            List<Double> classic = shown(bar(r[0], r[1], null, 300, h));

            if(classic.contains(0.0)) {
               assertEquals(classic, shown(bar(r[0], r[1], ZERO, 300, h)),
                            r[0] + ".." + r[1] + " height " + h);
            }
         }

         for(int w : WIDTHS) {
            List<Double> classic = shown(hbar(r[0], r[1], null, w));

            if(classic.contains(0.0)) {
               assertEquals(classic, shown(hbar(r[0], r[1], ZERO, w)),
                            r[0] + ".." + r[1] + " width " + w);
            }
         }
      }
   }

   @Test
   void aRangeThatDoesNotCrossZeroIsUnchanged() {
      assertSameAsClassic(10, 600);
      assertSameAsClassic(-580, -10);
      assertSameAsClassic(-290, -10);
      assertSameAsClassic(120, 790);
   }

   @Test
   void aCrowdedAxisKeepsZero() {
      for(double[] r : CROSSING) {
         for(int h : HEIGHTS) {
            assertKeepsZero(bar(r[0], r[1], ZERO, 300, h), r[0] + ".." + r[1] + " height " + h);
         }
      }
   }

   @Test
   void theOneByOneRemovalNeverDropsZero() {
      // the moved-in end label overlaps 0 once the alternate labels are gone
      // ticks -100..150 by 50
      assertKeepsZeroWhereClassicShows(-75, 125, List.of(-100.0, 100.0));
      // ticks -200..400 by 100
      assertKeepsZeroWhereClassicShows(-120, 380, List.of(-200.0, 200.0));
   }

   @Test
   void aHorizontalValueAxisKeepsZero() {
      assertEquals(List.of(-200.0, 0.0, 200.0), shown(hbar(-225, 295, ZERO, 140)));

      for(double[] r : CROSSING) {
         for(int w : WIDTHS) {
            assertKeepsZero(hbar(r[0], r[1], ZERO, w), r[0] + ".." + r[1] + " width " + w);
         }
      }
   }

   @Test
   void aReversedScaleKeepsZero() {
      LinearScale y = scale(ZERO);
      y.setReversed(true);
      DefaultAxis axis = layout(y, -225, 295, 300, 100, false);

      assertEquals(List.of(200.0, 0.0, -200.0), shown(axis));
   }

   @Test
   void aUserMinOnTheIncrementKeepsZero() {
      LinearScale y = scale(ZERO);
      y.setMin(-300);

      assertEquals(List.of(-200.0, 0.0, 200.0), shown(layout(y, -225, 295, 300, 100, false)));
   }

   @Test
   void aUserMinOffTheIncrementIsUnchanged() {
      for(int h : HEIGHTS) {
         LinearScale modern = scale(ZERO);
         LinearScale classic = scale(null);
         modern.setMin(-250);
         classic.setMin(-250);

         assertEquals(shown(layout(classic, -225, 295, 300, h, false)),
                      shown(layout(modern, -225, 295, 300, h, false)), "height " + h);
      }
   }

   @Test
   void aLogScaleIsUnchanged() {
      for(int h : HEIGHTS) {
         LogScale modern = new LogScale("Quantity");
         LogScale classic = new LogScale("Quantity");
         modern.getAxisSpec().setZeroLineColor(ZERO);

         assertEquals(shown(layout(classic, 1, 90000, 300, h, false)),
                      shown(layout(modern, 1, 90000, 300, h, false)), "height " + h);
      }
   }

   @Test
   void aDecimalZeroIsKept() {
      // the 0 tick of -0.3..0.3 by 0.1 is a rounding error away from 0
      DefaultAxis axis = bar(-0.25, 0.29, ZERO, 300, 100);

      assertEquals(List.of("-0.2", "0", "0.2"), texts(axis));
   }

   private static void assertSameAsClassic(double min, double max) {
      for(int h : HEIGHTS) {
         assertEquals(shown(bar(min, max, null, 300, h)), shown(bar(min, max, ZERO, 300, h)),
                      "height " + h);
      }

      for(int w : WIDTHS) {
         assertEquals(shown(hbar(min, max, null, w)), shown(hbar(min, max, ZERO, w)),
                      "width " + w);
      }
   }

   // modern keeps 0 at every height where classic shows only these labels; scanned, since
   // those heights shift with the font's line height
   private static void assertKeepsZeroWhereClassicShows(double min, double max,
                                                        List<Double> classic)
   {
      int found = 0;

      for(int h = 40; h <= 90; h++) {
         if(classic.equals(shown(bar(min, max, null, 300, h)))) {
            assertKeepsZero(bar(min, max, ZERO, 300, h), min + ".." + max + " height " + h);
            found++;
         }
      }

      assertTrue(found > 0, "premise: classic shows " + classic + " for " + min + ".." + max +
                 " at some height from 40 to 90");
   }

   // 0 is among the shown labels
   private static void assertKeepsZero(DefaultAxis axis, String where) {
      List<Double> shown = shown(axis);

      assertTrue(shown.contains(0.0), where + " " + shown);
   }

   private static LinearScale scale(Color zero) {
      LinearScale y = new LinearScale("Quantity");
      y.getAxisSpec().setZeroLineColor(zero);
      return y;
   }

   private static DefaultAxis bar(double min, double max, Color zero, int width, int height) {
      return layout(scale(zero), min, max, width, height, false);
   }

   private static DefaultAxis hbar(double min, double max, Color zero, int width) {
      return layout(scale(zero), min, max, width, 300, true);
   }

   private static DefaultAxis layout(LinearScale y, double min, double max, int width,
                                     int height, boolean transposed)
   {
      Object[][] rows = { { "State", "Quantity" }, { "A", min }, { "B", max },
                          { "C", (min + max) / 2 } };
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      RectCoord coord = new RectCoord(new CategoricalScale("State"), y);

      if(transposed) {
         coord.transpose();
      }

      graph.setCoordinate(coord);
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
      RectCoord rect = (RectCoord) vgraph.getCoordinate();
      DefaultAxis axis = rect.getAxisAt(transposed ? ICoordinate.BOTTOM_AXIS
                                                   : ICoordinate.LEFT_AXIS);
      assertSame(y, axis.getScale(), "premise: the value axis");
      axis.prepareLabels();
      return axis;
   }

   // the shown labels' values, rounded to the tick precision
   private static List<Double> shown(DefaultAxis axis) {
      List<Double> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            double v = ((Number) label.getValue()).doubleValue();
            list.add(Math.round(v * 1E6) / 1E6 + 0.0);
         }
      }

      return list;
   }

   private static List<String> texts(DefaultAxis axis) {
      List<String> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            list.add(String.join("", label.getDisplayLabel()));
         }
      }

      return list;
   }
}
