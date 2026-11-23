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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scales, weights, positions and API of {@link MekkoCoord} without a chart layout.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class MekkoCoordTest {
   private static final double EPS = 1e-6;

   /** Rows are (group, stack, value) triples. */
   private static DefaultDataSet data(Object... rv) {
      Object[][] rows = new Object[rv.length / 3 + 1][];
      rows[0] = new Object[] { "R", "P", "S" };

      for(int i = 0; i < rv.length / 3; i++) {
         rows[i + 1] = new Object[] { rv[3 * i], rv[3 * i + 1], rv[3 * i + 2] };
      }

      return new DefaultDataSet(rows);
   }

   /** East 40, West 60, North 100. */
   private static DefaultDataSet standard() {
      return data("East", "x", 30.0, "East", "y", 10.0, "West", "x", 20.0, "West", "y", 40.0,
                  "North", "x", 60.0, "North", "y", 40.0);
   }

   private static MekkoCoord coord(DefaultDataSet data) {
      MekkoCoord coord = new MekkoCoord("R", "S");
      coord.init(data);
      return coord;
   }

   private static CategoricalScale xs(MekkoCoord coord) {
      return (CategoricalScale) coord.getScales()[0];
   }

   private static CategoricalScale x2(MekkoCoord coord) {
      return (CategoricalScale) coord.getScales()[2];
   }

   private static double x(MekkoCoord coord, Object group) {
      return coord.getPosition(new double[] { xs(coord).map(group) }).getX();
   }

   /** Width of each group slot, in scale order: next start (or GWIDTH) minus start. */
   private static double[] widths(MekkoCoord coord) {
      Object[] groups = xs(coord).getValues();
      double[] w = new double[groups.length];

      for(int i = 0; i < w.length; i++) {
         double next = i + 1 < w.length ? x(coord, groups[i + 1]) : Coordinate.GWIDTH;
         w[i] = next - x(coord, groups[i]);
      }

      return w;
   }

   private static double sum(double[] arr) {
      return Arrays.stream(arr).sum();
   }

   @Test
   @DisplayName("accessors after init: dim R, var S, one dimension, scales x, y(S%), x2(S)")
   void accessors() {
      MekkoCoord coord = coord(standard());

      assertEquals("R", coord.getDim());
      assertEquals("S", coord.getVar());
      assertEquals(1, coord.getDimCount());

      Scale[] scales = coord.getScales();
      assertEquals(3, scales.length);
      assertArrayEquals(new String[] { "R" }, scales[0].getFields());
      assertArrayEquals(new String[] { "R" }, ((CategoricalScale) scales[0]).getDataFields());
      assertArrayEquals(new String[] { "S%" }, scales[1].getFields());
      assertArrayEquals(new String[] { "S" }, scales[2].getFields());
   }

   @Test
   @DisplayName("an empty coord has no dim and no var but three scales")
   void emptyCoord() {
      MekkoCoord coord = new MekkoCoord();

      assertNull(coord.getDim());
      assertNull(coord.getVar());
      assertEquals(3, coord.getScales().length);

      for(Scale scale : coord.getScales()) {
         assertNotNull(scale);
      }
   }

   @Test
   @DisplayName("y axis defaults to a percentage axis 0..1 with ticks every 0.2, single style")
   void yAxisDefaults() {
      Scale y = new MekkoCoord().getScales()[1];

      assertEquals(0, y.getMin(), EPS);
      assertEquals(1, y.getMax(), EPS);
      assertArrayEquals(new double[] { 0, 0.2, 0.4, 0.6, 0.8, 1.0 }, y.getTicks(), EPS);
      assertEquals(AxisSpec.AXIS_SINGLE, y.getAxisSpec().getAxisStyle());
   }

   @Test
   @DisplayName("weight of a group is the sum of its values: East 40, West 60, North 100")
   void weightsAreGroupTotals() {
      CategoricalScale xs = xs(coord(standard()));

      assertEquals(40, xs.getWeight("East"), EPS);
      assertEquals(60, xs.getWeight("West"), EPS);
      assertEquals(100, xs.getWeight("North"), EPS);
   }

   @Test
   @DisplayName("slots are contiguous from 0, proportional to the totals and fill the width")
   void widthsProportional() {
      MekkoCoord coord = coord(standard());
      CategoricalScale xs = xs(coord);
      Object[] groups = xs.getValues();
      double[] w = widths(coord);

      assertEquals(3, groups.length);
      assertEquals(0, x(coord, groups[0]), EPS);
      assertEquals(Coordinate.GWIDTH, sum(w), EPS);

      for(int i = 0; i < groups.length; i++) {
         assertEquals(0, coord.getPosition(new double[] { xs.map(groups[i]) }).getY(), EPS);
         assertEquals(Coordinate.GWIDTH * xs.getWeight(groups[i]) / 200, w[i], EPS,
                      "width of " + groups[i]);
      }
   }

   @Test
   @DisplayName("negative values count by absolute value: East total 40, widths 400 and 600")
   void negativeValues() {
      MekkoCoord coord = coord(data("East", "x", 30.0, "East", "y", -10.0,
                                    "West", "x", 20.0, "West", "y", 40.0));
      CategoricalScale xs = xs(coord);

      assertEquals(40, xs.getWeight("East"), EPS);
      assertEquals(60, xs.getWeight("West"), EPS);
      assertArrayEquals(new double[] { 400, 600 }, widths(coord), EPS);
   }

   @Test
   @DisplayName("null measures add nothing: East 30, North 60, West has width 0, widths sum to GWIDTH")
   void nullMeasures() {
      MekkoCoord coord = coord(data("East", "x", 30.0, "East", "y", null,
                                    "West", "x", null, "West", "y", null,
                                    "North", "x", 60.0));
      CategoricalScale xs = xs(coord);
      Object[] groups = xs.getValues();
      double[] w = widths(coord);

      assertEquals(30, xs.getWeight("East"), EPS);
      assertEquals(60, xs.getWeight("North"), EPS);
      assertEquals(Coordinate.GWIDTH, sum(w), EPS);

      for(int i = 0; i < groups.length; i++) {
         if("West".equals(groups[i])) {
            assertEquals(0, w[i], EPS);
         }
      }
   }

   @Test
   @DisplayName("NO_NULL: the null group has no slot and the other groups fill the width")
   void noNullIgnoresNullGroup() {
      MekkoCoord coord = new MekkoCoord("R", "S");
      coord.getScales()[0].setScaleOption(Scale.NO_NULL);
      coord.init(data("East", "x", 30.0, null, "x", 50.0, "West", "x", 20.0));
      CategoricalScale xs = xs(coord);

      assertFalse(Arrays.asList(xs.getValues()).contains(null));
      assertTrue(Double.isNaN(xs.map(null)));
      assertEquals(Coordinate.GWIDTH, widthOf(coord, "East") + widthOf(coord, "West"), EPS);
      assertEquals(Coordinate.GWIDTH, sum(widths(coord)), EPS);
   }

   private static double widthOf(MekkoCoord coord, Object group) {
      Object[] groups = xs(coord).getValues();
      double[] w = widths(coord);

      for(int i = 0; i < groups.length; i++) {
         if(Arrays.asList(groups[i]).equals(Arrays.asList(group))) {
            return w[i];
         }
      }

      return Double.NaN;
   }

   @Test
   @DisplayName("control: without NO_NULL the null group gets weight 50 and its own slot")
   void nullGroupWithoutNoNull() {
      MekkoCoord coord = coord(data("East", "x", 30.0, null, "x", 50.0, "West", "x", 20.0));
      CategoricalScale xs = xs(coord);

      assertTrue(Arrays.asList(xs.getValues()).contains(null));
      assertEquals(50, xs.getWeight(null), EPS);
      assertEquals(500, widthOf(coord, null), EPS);
      assertEquals(Coordinate.GWIDTH, sum(widths(coord)), EPS);
   }

   @Test
   @DisplayName("top axis aligned with the bottom axis for distinct and for equal totals")
   void topAxisAligned() {
      DefaultDataSet[] sets = {
         standard(),
         data("East", "x", 30.0, "East", "y", 20.0, "West", "x", 20.0, "West", "y", 30.0,
              "North", "x", 60.0, "North", "y", 40.0)
      };

      for(DefaultDataSet set : sets) {
         MekkoCoord coord = coord(set);
         CategoricalScale xs = xs(coord);
         CategoricalScale x2 = x2(coord);

         assertArrayEquals(xs.getTicks(), x2.getTicks(), EPS);
         assertEquals(xs.getMax(), x2.getMax(), EPS);
         assertEquals(xs.getValues().length, x2.getValues().length);
      }
   }

   @Test
   @DisplayName("group totals of exactly 1 give equal widths of a third")
   void totalsOfOne() {
      MekkoCoord coord = coord(data("A", "x", 1.0, "B", "x", 1.0, "C", "x", 1.0));

      assertArrayEquals(new double[] { 1000.0 / 3, 1000.0 / 3, 1000.0 / 3 },
                        widths(coord), EPS);
   }

   @Test
   @DisplayName("all totals zero: no exception and every group position is finite")
   void allZero() {
      MekkoCoord coord = new MekkoCoord("R", "S");
      DefaultDataSet set = data("East", "x", 0.0, "West", "x", 0.0, "North", "x", 0.0);
      assertDoesNotThrow(() -> coord.init(set));

      for(Object group : xs(coord).getValues()) {
         assertTrue(Double.isFinite(x(coord, group)), "position of " + group);
      }
   }

   @Test
   @DisplayName("dimension dropped (var only): init and getPosition do not throw")
   void noDimension() {
      MekkoCoord coord = new MekkoCoord();
      coord.setVar("S");

      assertDoesNotThrow(() -> coord.init(standard()));
      assertDoesNotThrow(() -> coord.getPosition(new double[] { 0 }));
   }

   @Test
   @DisplayName("measure dropped (dim only): init does not throw")
   void noMeasure() {
      MekkoCoord coord = new MekkoCoord();
      coord.setDim("R");

      assertDoesNotThrow(() -> coord.init(standard()));
   }

   @Test
   @DisplayName("header-only data: no exception and no values on the bottom and top scales")
   void emptyData() {
      MekkoCoord coord = new MekkoCoord("R", "S");

      assertDoesNotThrow(() -> coord.init(data()));
      assertEquals(0, xs(coord).getValues().length);
      assertEquals(0, x2(coord).getValues().length);
   }

   @Test
   @DisplayName("axis spec setters: getter returns the same spec, the matching scale uses it")
   void axisSpecSetters() {
      MekkoCoord coord = new MekkoCoord();
      AxisSpec[] before = {
         coord.getScales()[0].getAxisSpec(), coord.getScales()[1].getAxisSpec(),
         coord.getScales()[2].getAxisSpec() };
      AxisSpec xspec = new AxisSpec();

      coord.setXAxisSpec(xspec);
      assertSame(xspec, coord.getXAxisSpec());
      assertSame(xspec, coord.getScales()[0].getAxisSpec());
      assertSame(before[1], coord.getYAxisSpec());
      assertSame(before[2], coord.getX2AxisSpec());

      AxisSpec yspec = new AxisSpec();
      coord.setYAxisSpec(yspec);
      assertSame(yspec, coord.getYAxisSpec());
      assertSame(yspec, coord.getScales()[1].getAxisSpec());
      assertSame(xspec, coord.getXAxisSpec());

      AxisSpec x2spec = new AxisSpec();
      coord.setX2AxisSpec(x2spec);
      assertSame(x2spec, coord.getX2AxisSpec());
      assertSame(x2spec, coord.getScales()[2].getAxisSpec());
      assertSame(yspec, coord.getYAxisSpec());
   }

   @Test
   @DisplayName("before createAxis there are no axes and every position gives null")
   void axesBeforeCreate() {
      MekkoCoord coord = new MekkoCoord();

      assertEquals(0, coord.getAxes(false).length);

      for(int pos = 0; pos <= 4; pos++) {
         assertEquals(0, coord.getAxesAt(pos).length, "axes at " + pos);
         assertNull(coord.getAxisAt(pos), "axis at " + pos);
      }
   }

   @Test
   @DisplayName("pinned behavior: all four unit sizes are 200")
   void unitSizesPinned() {
      MekkoCoord coord = new MekkoCoord();

      assertEquals(200, coord.getUnitMinWidth(), 0);
      assertEquals(200, coord.getUnitMinHeight(), 0);
      assertEquals(200, coord.getUnitPreferredWidth(), 0);
      assertEquals(200, coord.getUnitPreferredHeight(), 0);
   }

   @Test
   @DisplayName("pinned behavior: interval size is 0")
   void intervalSizePinned() {
      MekkoCoord coord = new MekkoCoord();

      assertEquals(0, coord.getIntervalSize(0), 0);
      assertEquals(0, coord.getIntervalSize(5), 0);
   }
}
