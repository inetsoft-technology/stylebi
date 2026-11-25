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

import inetsoft.graph.VGraph;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.AxisSizeList;
import inetsoft.graph.internal.AxisSizeStrategy;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.AxisSpec;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TileCoord} (the container of the inner coordinates of one facet cell): axis routing
 * between the stacked or side-by-side inner coordinates, label and tick visibility, position
 * hints, keys, cloning and propagation to the children.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class TileCoordTest {
   private static final int TOP = ICoordinate.TOP_AXIS;
   private static final int LEFT = ICoordinate.LEFT_AXIS;
   private static final int BOTTOM = ICoordinate.BOTTOM_AXIS;
   private static final int RIGHT = ICoordinate.RIGHT_AXIS;
   private static final int[] AXES = { TOP, LEFT, BOTTOM, RIGHT };

   /**
    * Test coordinate: a treemap coordinate (one unit, no depth, no axes of its own) whose
    * axis sizes, minimum and preferred sizes, axes and unit sizes are plain fields.
    */
   static class SC extends TreemapCoord {
      final double[] size = new double[5];
      final double[] min = new double[5];
      final double[] pref = new double[5];
      final DefaultAxis[][] axes = new DefaultAxis[5][];
      double unitMinW, unitMinH, unitPrefW, unitPrefH;
      final List<double[]> fits = new ArrayList<>();

      @Override
      public double getAxisSize(int axis) {
         return size[axis];
      }

      @Override
      public void setAxisSize(int axis, double val) {
         size[axis] = val;
      }

      @Override
      public double getAxisMinSize(int axis) {
         return min[axis];
      }

      @Override
      public double getAxisPreferredSize(int axis) {
         return pref[axis];
      }

      @Override
      public DefaultAxis[] getAxesAt(int axis) {
         return axes[axis] == null ? new DefaultAxis[0] : axes[axis];
      }

      @Override
      public double getUnitMinWidth() {
         return unitMinW;
      }

      @Override
      public double getUnitMinHeight() {
         return unitMinH;
      }

      @Override
      public double getUnitPreferredWidth() {
         return unitPrefW;
      }

      @Override
      public double getUnitPreferredHeight() {
         return unitPrefH;
      }

      @Override
      public void fit(double x, double y, double w, double h) {
         fits.add(new double[] { x, y, w, h });
      }
   }

   private static SC[] scs(int n) {
      SC[] arr = new SC[n];

      for(int i = 0; i < n; i++) {
         arr[i] = new SC();
      }

      return arr;
   }

   private static TileCoord vertical(SC... coords) {
      return TileCoord.vertical(coords);
   }

   private static TileCoord horizontal(SC... coords) {
      return TileCoord.horizontal(coords);
   }

   private static double[] arrayOf(SC[] cs, int axis, String kind) {
      double[] vals = new double[cs.length];

      for(int i = 0; i < cs.length; i++) {
         vals[i] = switch(kind) {
            case "size" -> cs[i].size[axis];
            case "min" -> cs[i].min[axis];
            default -> cs[i].pref[axis];
         };
      }

      return vals;
   }

   private static RectCoord rect() {
      return rect("Cat", "m");
   }

   private static RectCoord rect(String xfield, String yfield) {
      CategoricalScale xs = new CategoricalScale(xfield);
      xs.init("A", "B", "C");
      LinearScale ys = new LinearScale(yfield);
      ys.setMin(0);
      ys.setMax(100);
      return new RectCoord(xs, ys);
   }

   private static RectCoord rect(Scale xs, Scale ys) {
      return new RectCoord(xs, ys);
   }

   private static void assertLabels(Coordinate coord, boolean top, boolean left, boolean bottom,
                                    boolean right)
   {
      assertEquals(top, coord.isAxisLabelVisible(TOP), "top label");
      assertEquals(left, coord.isAxisLabelVisible(LEFT), "left label");
      assertEquals(bottom, coord.isAxisLabelVisible(BOTTOM), "bottom label");
      assertEquals(right, coord.isAxisLabelVisible(RIGHT), "right label");
   }

   private static void assertTicks(Coordinate coord, boolean top, boolean left, boolean bottom,
                                   boolean right)
   {
      assertEquals(top, coord.isAxisTickVisible(TOP), "top tick");
      assertEquals(left, coord.isAxisTickVisible(LEFT), "left tick");
      assertEquals(bottom, coord.isAxisTickVisible(BOTTOM), "bottom tick");
      assertEquals(right, coord.isAxisTickVisible(RIGHT), "right tick");
   }

   private static void hideAll(TileCoord tile) {
      for(int axis : AXES) {
         tile.setAxisLabelVisible(axis, false);
         tile.setAxisTickVisible(axis, false);
      }
   }

   // ---------------------------------------------------------------- axis sizes

   @Test
   @DisplayName("vertical tile: top axis size is the last tile's, bottom axis size is the first's")
   void verticalAxisSizeTopAndBottom() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      cs[0].size[TOP] = 10;
      cs[1].size[TOP] = 20;
      cs[2].size[TOP] = 30;
      assertEquals(30, tile.getAxisSize(TOP));

      cs[0].size[BOTTOM] = 10;
      cs[1].size[BOTTOM] = 20;
      cs[2].size[BOTTOM] = 30;
      assertEquals(10, tile.getAxisSize(BOTTOM));
   }

   @Test
   @DisplayName("vertical tile: setting left and right axis size reaches every tile")
   void verticalSetLeftRightAxisSizeReachesAll() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisSize(LEFT, 42);
      tile.setAxisSize(RIGHT, 17);

      for(SC c : cs) {
         assertEquals(42, c.getAxisSize(LEFT));
         assertEquals(17, c.getAxisSize(RIGHT));
      }
   }

   @Test
   @DisplayName("vertical tile: top axis size goes to the last tile, bottom to the first")
   void verticalSetTopBottomAxisSizeReachesEndTilesOnly() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisSize(TOP, 9);
      tile.setAxisSize(BOTTOM, 8);

      assertArrayEquals(new double[] { 0, 0, 9 }, arrayOf(cs, TOP, "size"));
      assertArrayEquals(new double[] { 8, 0, 0 }, arrayOf(cs, BOTTOM, "size"));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { TOP, LEFT, BOTTOM, RIGHT })
   @DisplayName("vertical tile: an axis size that is set is read back")
   void verticalAxisSizeRoundTrip(int axis) {
      TileCoord tile = vertical(scs(3));
      tile.setAxisSize(axis, 33);
      assertEquals(33, tile.getAxisSize(axis));
   }

   @Test
   @DisplayName("vertical tile: left minimum size is the widest tile's")
   void verticalLeftMinSizeIsMax() {
      SC[] cs = scs(3);
      double[] vals = { 5, 9, 7 };

      for(int i = 0; i < 3; i++) {
         cs[i].min[LEFT] = vals[i];
      }

      assertEquals(9, vertical(cs).getAxisMinSize(LEFT));
   }

   @Test
   @DisplayName("vertical tile: right minimum is the max, top the last tile's, bottom the first's")
   void verticalMinSizeRightTopBottom() {
      SC[] cs = scs(3);
      double[] vals = { 5, 9, 7 };

      for(int i = 0; i < 3; i++) {
         for(int axis : AXES) {
            cs[i].min[axis] = vals[i];
         }
      }

      TileCoord tile = vertical(cs);
      assertEquals(9, tile.getAxisMinSize(RIGHT));
      assertEquals(7, tile.getAxisMinSize(TOP));
      assertEquals(5, tile.getAxisMinSize(BOTTOM));
   }

   @Test
   @DisplayName("vertical tile: preferred size is the max for left and right, last for top, "
      + "first for bottom")
   void verticalPreferredSize() {
      SC[] cs = scs(3);
      double[] vals = { 5, 9, 7 };

      for(int i = 0; i < 3; i++) {
         for(int axis : AXES) {
            cs[i].pref[axis] = vals[i];
         }
      }

      TileCoord tile = vertical(cs);
      assertEquals(9, tile.getAxisPreferredSize(LEFT));
      assertEquals(9, tile.getAxisPreferredSize(RIGHT));
      assertEquals(7, tile.getAxisPreferredSize(TOP));
      assertEquals(5, tile.getAxisPreferredSize(BOTTOM));
   }

   @Test
   @DisplayName("horizontal tile: setting top and bottom axis size reaches every tile")
   void horizontalSetTopBottomAxisSizeReachesAll() {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      tile.setAxisSize(TOP, 9);
      tile.setAxisSize(BOTTOM, 8);

      for(SC c : cs) {
         assertEquals(9, c.getAxisSize(TOP));
         assertEquals(8, c.getAxisSize(BOTTOM));
      }
   }

   @Test
   @DisplayName("horizontal tile: left axis size goes to the first tile, right to the last")
   void horizontalSetLeftRightAxisSizeReachesEndTilesOnly() {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      tile.setAxisSize(LEFT, 42);
      tile.setAxisSize(RIGHT, 17);

      assertArrayEquals(new double[] { 42, 0, 0 }, arrayOf(cs, LEFT, "size"));
      assertArrayEquals(new double[] { 0, 0, 17 }, arrayOf(cs, RIGHT, "size"));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { TOP, LEFT, BOTTOM, RIGHT })
   @DisplayName("horizontal tile: an axis size that is set is read back")
   void horizontalAxisSizeRoundTrip(int axis) {
      TileCoord tile = horizontal(scs(3));
      tile.setAxisSize(axis, 33);
      assertEquals(33, tile.getAxisSize(axis));
   }

   @Test
   @DisplayName("horizontal tile: min and preferred sizes (top and bottom max, left first, "
      + "right last)")
   void horizontalMinAndPreferredSize() {
      SC[] cs = scs(3);
      double[] vals = { 5, 9, 7 };

      for(int i = 0; i < 3; i++) {
         for(int axis : AXES) {
            cs[i].min[axis] = vals[i];
            cs[i].pref[axis] = vals[i];
         }
      }

      TileCoord tile = horizontal(cs);
      assertEquals(9, tile.getAxisMinSize(TOP));
      assertEquals(9, tile.getAxisMinSize(BOTTOM));
      assertEquals(5, tile.getAxisMinSize(LEFT));
      assertEquals(7, tile.getAxisMinSize(RIGHT));
      assertEquals(9, tile.getAxisPreferredSize(TOP));
      assertEquals(9, tile.getAxisPreferredSize(BOTTOM));
      assertEquals(5, tile.getAxisPreferredSize(LEFT));
      assertEquals(7, tile.getAxisPreferredSize(RIGHT));
   }

   // ---------------------------------------------------------------- axes

   private static DefaultAxis[] giveAxes(SC[] cs) {
      DefaultAxis[] mocks = new DefaultAxis[cs.length];

      for(int i = 0; i < cs.length; i++) {
         mocks[i] = Mockito.mock(DefaultAxis.class);

         for(int axis : AXES) {
            cs[i].axes[axis] = new DefaultAxis[] { mocks[i] };
         }
      }

      return mocks;
   }

   @Test
   @DisplayName("vertical tile: axes at top are the last tile's, bottom the first's, "
      + "left and right all in order")
   void verticalAxesAt() {
      SC[] cs = scs(3);
      DefaultAxis[] a = giveAxes(cs);
      TileCoord tile = vertical(cs);

      assertArrayEquals(new DefaultAxis[] { a[2] }, tile.getAxesAt(TOP));
      assertArrayEquals(new DefaultAxis[] { a[0] }, tile.getAxesAt(BOTTOM));
      assertArrayEquals(a, tile.getAxesAt(LEFT));
      assertArrayEquals(a, tile.getAxesAt(RIGHT));
   }

   @Test
   @DisplayName("horizontal tile: axes at left are the first tile's, right the last's, "
      + "top and bottom all in order")
   void horizontalAxesAt() {
      SC[] cs = scs(3);
      DefaultAxis[] a = giveAxes(cs);
      TileCoord tile = horizontal(cs);

      assertArrayEquals(new DefaultAxis[] { a[0] }, tile.getAxesAt(LEFT));
      assertArrayEquals(new DefaultAxis[] { a[2] }, tile.getAxesAt(RIGHT));
      assertArrayEquals(a, tile.getAxesAt(TOP));
      assertArrayEquals(a, tile.getAxesAt(BOTTOM));
   }

   @Test
   @DisplayName("vertical tile: anyAxisAt agrees with anyMatch over getAxesAt")
   void verticalAnyAxisAtMatchesAxesAt() {
      SC[] cs = scs(3);
      DefaultAxis only = Mockito.mock(DefaultAxis.class);

      for(int axis : AXES) {
         cs[1].axes[axis] = new DefaultAxis[] { only };
      }

      TileCoord tile = vertical(cs);
      Predicate<inetsoft.graph.guide.axis.Axis> pred = ax -> ax == only;

      assertFalse(tile.anyAxisAt(TOP, pred));
      assertFalse(tile.anyAxisAt(BOTTOM, pred));
      assertTrue(tile.anyAxisAt(LEFT, pred));
      assertTrue(tile.anyAxisAt(RIGHT, pred));

      for(int axis : AXES) {
         assertEquals(Arrays.stream(tile.getAxesAt(axis)).anyMatch(pred),
                      tile.anyAxisAt(axis, pred));
      }
   }

   @Test
   @DisplayName("horizontal tile: anyAxisAt agrees with anyMatch over getAxesAt")
   void horizontalAnyAxisAtMatchesAxesAt() {
      SC[] cs = scs(3);
      DefaultAxis only = Mockito.mock(DefaultAxis.class);

      for(int axis : AXES) {
         cs[1].axes[axis] = new DefaultAxis[] { only };
      }

      TileCoord tile = horizontal(cs);
      Predicate<inetsoft.graph.guide.axis.Axis> pred = ax -> ax == only;

      assertFalse(tile.anyAxisAt(LEFT, pred));
      assertFalse(tile.anyAxisAt(RIGHT, pred));
      assertTrue(tile.anyAxisAt(TOP, pred));
      assertTrue(tile.anyAxisAt(BOTTOM, pred));

      for(int axis : AXES) {
         assertEquals(Arrays.stream(tile.getAxesAt(axis)).anyMatch(pred),
                      tile.anyAxisAt(axis, pred));
      }
   }

   // ---------------------------------------------------------------- label and tick visibility

   private static boolean[] labelVis(SC[] cs, int axis) {
      boolean[] vis = new boolean[cs.length];

      for(int i = 0; i < cs.length; i++) {
         vis[i] = cs[i].isAxisLabelVisible(axis);
      }

      return vis;
   }

   private static boolean[] tickVis(SC[] cs, int axis) {
      boolean[] vis = new boolean[cs.length];

      for(int i = 0; i < cs.length; i++) {
         vis[i] = cs[i].isAxisTickVisible(axis);
      }

      return vis;
   }

   private static final boolean[] ALL = { true, true, true };
   private static final boolean[] NONE = { false, false, false };
   private static final boolean[] FIRST = { true, false, false };
   private static final boolean[] LAST = { false, false, true };

   @Test
   @DisplayName("vertical tile: showing the top axis label shows it on the last tile only")
   void verticalTopLabelOnLastOnly() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      hideAll(tile);
      tile.setAxisLabelVisible(TOP, true);
      assertArrayEquals(LAST, labelVis(cs, TOP));
   }

   @Test
   @DisplayName("vertical tile: bottom label shows on the first tile only, left and right on all")
   void verticalBottomLabelFirstOnlyLeftRightAll() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      hideAll(tile);
      tile.setAxisLabelVisible(BOTTOM, true);
      tile.setAxisLabelVisible(LEFT, true);
      tile.setAxisLabelVisible(RIGHT, true);

      assertArrayEquals(FIRST, labelVis(cs, BOTTOM));
      assertArrayEquals(ALL, labelVis(cs, LEFT));
      assertArrayEquals(ALL, labelVis(cs, RIGHT));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { TOP, LEFT, BOTTOM, RIGHT })
   @DisplayName("vertical tile: hiding a label or tick hides it on every tile")
   void verticalHidingIsNotLimitedToEndTiles(int axis) {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisLabelVisible(axis, false);
      tile.setAxisTickVisible(axis, false);
      assertArrayEquals(NONE, labelVis(cs, axis));
      assertArrayEquals(NONE, tickVis(cs, axis));
   }

   @Test
   @DisplayName("vertical tile: tick visibility follows the same end-tile rules as labels")
   void verticalTickVisibilityRules() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      hideAll(tile);
      tile.setAxisTickVisible(TOP, true);
      tile.setAxisTickVisible(BOTTOM, true);
      tile.setAxisTickVisible(LEFT, true);
      tile.setAxisTickVisible(RIGHT, true);

      assertArrayEquals(LAST, tickVis(cs, TOP));
      assertArrayEquals(FIRST, tickVis(cs, BOTTOM));
      assertArrayEquals(ALL, tickVis(cs, LEFT));
      assertArrayEquals(ALL, tickVis(cs, RIGHT));
   }

   @Test
   @DisplayName("horizontal tile: label visibility (left first tile, right last tile, "
      + "top and bottom all)")
   void horizontalLabelVisibility() {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      hideAll(tile);

      for(int axis : AXES) {
         tile.setAxisLabelVisible(axis, true);
      }

      assertArrayEquals(FIRST, labelVis(cs, LEFT));
      assertArrayEquals(LAST, labelVis(cs, RIGHT));
      assertArrayEquals(ALL, labelVis(cs, TOP));
      assertArrayEquals(ALL, labelVis(cs, BOTTOM));

      for(int axis : AXES) {
         tile.setAxisLabelVisible(axis, false);
      }

      for(int axis : AXES) {
         assertArrayEquals(NONE, labelVis(cs, axis));
      }
   }

   @Test
   @DisplayName("horizontal tile: tick visibility follows the same pattern as labels")
   void horizontalTickVisibility() {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      hideAll(tile);

      for(int axis : AXES) {
         tile.setAxisTickVisible(axis, true);
      }

      assertArrayEquals(FIRST, tickVis(cs, LEFT));
      assertArrayEquals(LAST, tickVis(cs, RIGHT));
      assertArrayEquals(ALL, tickVis(cs, TOP));
      assertArrayEquals(ALL, tickVis(cs, BOTTOM));

      for(int axis : AXES) {
         tile.setAxisTickVisible(axis, false);
      }

      for(int axis : AXES) {
         assertArrayEquals(NONE, tickVis(cs, axis));
      }
   }

   @Test
   @DisplayName("vertical tile: first tile's own left label does not make the tile visible (#74377)")
   void verticalFirstCoordinateDoesNotCountForLeft() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisLabelVisible(LEFT, false);
      cs[0].setAxisLabelVisible(LEFT, true);
      cs[1].setAxisLabelVisible(LEFT, false);
      cs[2].setAxisLabelVisible(LEFT, false);

      assertFalse(tile.isAxisLabelVisible(LEFT));
   }

   @Test
   @DisplayName("vertical tile: a visible second tile makes left visible and leaves right hidden")
   void verticalSecondCoordinateCountsForLeft() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisLabelVisible(LEFT, false);
      tile.setAxisLabelVisible(RIGHT, false);
      cs[1].setAxisLabelVisible(LEFT, true);

      assertTrue(tile.isAxisLabelVisible(LEFT));
      assertFalse(tile.isAxisLabelVisible(RIGHT));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { TOP, BOTTOM })
   @DisplayName("vertical tile: top and bottom visibility ignore the children")
   void verticalTopBottomIgnoreChildren(int axis) {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);
      tile.setAxisLabelVisible(TOP, false);
      tile.setAxisLabelVisible(BOTTOM, false);
      cs[1].setAxisLabelVisible(axis, true);

      assertFalse(tile.isAxisLabelVisible(axis));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { TOP, BOTTOM })
   @DisplayName("horizontal tile: top and bottom label visible only through a coordinate "
      + "after the first (#74377)")
   void horizontalTopBottomVisibilityFromSecondCoordinate(int axis) {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      tile.setAxisLabelVisible(BOTTOM, false);
      tile.setAxisLabelVisible(TOP, false);
      cs[0].setAxisLabelVisible(axis, true);
      assertFalse(tile.isAxisLabelVisible(axis));

      cs[0].setAxisLabelVisible(axis, false);
      cs[1].setAxisLabelVisible(axis, true);
      assertTrue(tile.isAxisLabelVisible(axis));
   }

   @ParameterizedTest(name = "axis {0}")
   @ValueSource(ints = { LEFT, RIGHT })
   @DisplayName("horizontal tile: left and right visibility ignore the children")
   void horizontalLeftRightIgnoreChildren(int axis) {
      SC[] cs = scs(3);
      TileCoord tile = horizontal(cs);
      tile.setAxisLabelVisible(LEFT, false);
      tile.setAxisLabelVisible(RIGHT, false);
      cs[1].setAxisLabelVisible(axis, true);

      assertFalse(tile.isAxisLabelVisible(axis));
   }

   // ---------------------------------------------------------------- setHint

   private static TileCoord single(RectCoord rc) {
      return TileCoord.vertical(rc);
   }

   @Test
   @DisplayName("setHint all-most with the default double axis style: left and bottom labels, "
      + "all ticks")
   void hintAllMostDefaultStyle() {
      RectCoord rc = rect();
      single(rc).setHint(ICoordinate.ALL_MOST);
      assertLabels(rc, false, true, true, false);
      assertTicks(rc, true, true, true, true);
   }

   @Test
   @DisplayName("setHint without left-most hides the left label and tick")
   void hintWithoutLeftMost() {
      RectCoord rc = rect();
      single(rc).setHint(ICoordinate.ALL_MOST & ~ICoordinate.LEFT_MOST);
      assertEquals(false, rc.isAxisLabelVisible(LEFT));
      assertEquals(false, rc.isAxisTickVisible(LEFT));
      assertTrue(rc.isAxisLabelVisible(BOTTOM));
      assertTrue(rc.isAxisTickVisible(TOP));
      assertTrue(rc.isAxisTickVisible(RIGHT));
      assertTrue(rc.isAxisTickVisible(BOTTOM));
   }

   @Test
   @DisplayName("setHint without bottom-most hides the bottom label and tick")
   void hintWithoutBottomMost() {
      RectCoord rc = rect();
      single(rc).setHint(ICoordinate.ALL_MOST & ~ICoordinate.BOTTOM_MOST);
      assertFalse(rc.isAxisLabelVisible(BOTTOM));
      assertFalse(rc.isAxisTickVisible(BOTTOM));
      assertTrue(rc.isAxisLabelVisible(LEFT));
      assertTrue(rc.isAxisTickVisible(LEFT));
      assertTrue(rc.isAxisTickVisible(TOP));
      assertTrue(rc.isAxisTickVisible(RIGHT));
   }

   @Test
   @DisplayName("setHint with a single2 x axis style moves the x label from bottom to top")
   void hintSingle2XAxis() {
      RectCoord rc = rect();
      rc.getScaleAt(BOTTOM).getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      single(rc).setHint(ICoordinate.ALL_MOST);
      assertTrue(rc.isAxisLabelVisible(TOP));
      assertFalse(rc.isAxisLabelVisible(BOTTOM));
      assertTicks(rc, true, true, true, true);
   }

   @Test
   @DisplayName("setHint with a single2 y axis style moves the y label from left to right")
   void hintSingle2YAxis() {
      RectCoord rc = rect();
      rc.getScaleAt(LEFT).getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      single(rc).setHint(ICoordinate.ALL_MOST);
      assertTrue(rc.isAxisLabelVisible(RIGHT));
      assertFalse(rc.isAxisLabelVisible(LEFT));
   }

   @Test
   @DisplayName("setHint with a secondary y scale shows the right label only when right-most")
   void hintSecondaryYScale() {
      LinearScale other = new LinearScale("m2");
      RectCoord rc = rect();
      rc.setYScale2(other);
      single(rc).setHint(ICoordinate.ALL_MOST);
      assertTrue(rc.isAxisLabelVisible(RIGHT));
      assertTrue(rc.isAxisLabelVisible(LEFT));

      RectCoord rc2 = rect();
      rc2.setYScale2(other);
      single(rc2).setHint(ICoordinate.ALL_MOST & ~ICoordinate.RIGHT_MOST);
      assertFalse(rc2.isAxisLabelVisible(RIGHT));
      assertTrue(rc2.isAxisLabelVisible(LEFT));
   }

   @Test
   @DisplayName("setHint on a vertical tile uses each inner coordinate's own y axis style")
   void hintMixedYStyles() {
      RectCoord c0 = rect();
      RectCoord c1 = rect();
      c1.getScaleAt(LEFT).getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      TileCoord.vertical(c0, c1).setHint(ICoordinate.ALL_MOST);

      assertTrue(c0.isAxisLabelVisible(LEFT));
      assertFalse(c0.isAxisLabelVisible(RIGHT));
      assertFalse(c1.isAxisLabelVisible(LEFT));
      assertTrue(c1.isAxisLabelVisible(RIGHT));
   }

   @Test
   @DisplayName("setHint on a horizontal tile uses each inner coordinate's own x axis style")
   void hintMixedXStyles() {
      RectCoord c0 = rect();
      RectCoord c1 = rect();
      c1.getScaleAt(BOTTOM).getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE2);
      TileCoord.horizontal(c0, c1).setHint(ICoordinate.ALL_MOST);

      assertTrue(c0.isAxisLabelVisible(BOTTOM));
      assertFalse(c0.isAxisLabelVisible(TOP));
      assertFalse(c1.isAxisLabelVisible(BOTTOM));
      assertTrue(c1.isAxisLabelVisible(TOP));
   }

   private static TileCoord[][] grid(int rows, int cols) {
      TileCoord[][] tiles = new TileCoord[rows][cols];

      for(int r = 0; r < rows; r++) {
         for(int c = 0; c < cols; c++) {
            tiles[r][c] = TileCoord.vertical(rect());
         }
      }

      return tiles;
   }

   private static Coordinate inner(TileCoord[][] grid, int r, int c) {
      return grid[r][c].getCoordinates()[0];
   }

   @Test
   @DisplayName("setHint on a 2x3 grid: edge tiles keep the ticks and labels of their sides")
   void gridHintTwoByThree() {
      TileCoord[][] grid = grid(2, 3);
      TileCoord.setHint(grid, ICoordinate.ALL_MOST);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 3; c++) {
            Coordinate in = inner(grid, r, c);
            String at = "row " + r + " col " + c;
            assertEquals(c == 0, in.isAxisTickVisible(LEFT), at + " left tick");
            assertEquals(c == 2, in.isAxisTickVisible(RIGHT), at + " right tick");
            assertEquals(r == 0, in.isAxisTickVisible(BOTTOM), at + " bottom tick");
            assertEquals(r == 1, in.isAxisTickVisible(TOP), at + " top tick");
            assertEquals(c == 0, in.isAxisLabelVisible(LEFT), at + " left label");
            assertEquals(r == 0, in.isAxisLabelVisible(BOTTOM), at + " bottom label");
         }
      }
   }

   @Test
   @DisplayName("setHint on a 1x1 grid shows all four ticks")
   void gridHintOneByOne() {
      TileCoord[][] grid = grid(1, 1);
      TileCoord.setHint(grid, ICoordinate.ALL_MOST);
      assertTicks(inner(grid, 0, 0), true, true, true, true);
   }

   @Test
   @DisplayName("setHint with hint 0 on a 2x2 grid hides every tick and label")
   void gridHintZero() {
      TileCoord[][] grid = grid(2, 2);
      TileCoord.setHint(grid, 0);

      for(int r = 0; r < 2; r++) {
         for(int c = 0; c < 2; c++) {
            assertTicks(inner(grid, r, c), false, false, false, false);
            assertLabels(inner(grid, r, c), false, false, false, false);
         }
      }
   }

   @Test
   @DisplayName("setHint on a 3x1 grid: left and right ticks on every row, top and bottom at "
      + "the ends")
   void gridHintThreeByOne() {
      TileCoord[][] grid = grid(3, 1);
      TileCoord.setHint(grid, ICoordinate.ALL_MOST);

      for(int r = 0; r < 3; r++) {
         Coordinate in = inner(grid, r, 0);
         assertTrue(in.isAxisTickVisible(LEFT));
         assertTrue(in.isAxisTickVisible(RIGHT));
         assertEquals(r == 0, in.isAxisTickVisible(BOTTOM), "row " + r + " bottom");
         assertEquals(r == 2, in.isAxisTickVisible(TOP), "row " + r + " top");
      }
   }

   // ---------------------------------------------------------------- copyAxisVisibility

   @Test
   @DisplayName("copyAxisVisibility copies the own and the children's bits")
   void copyAxisVisibilityCopiesOwnAndChildren() {
      TileCoord a = TileCoord.vertical(rect(), rect());
      a.setAxisLabelVisible(BOTTOM, false);
      Coordinate[] ac = a.getCoordinates();
      ac[0].setAxisLabelVisible(LEFT, false);
      ac[1].setAxisTickVisible(TOP, false);

      TileCoord b = TileCoord.vertical(rect(), rect());
      b.copyAxisVisibility(a);
      Coordinate[] bc = b.getCoordinates();

      assertFalse(b.isAxisLabelVisible(BOTTOM));
      assertFalse(bc[0].isAxisLabelVisible(LEFT));
      assertFalse(bc[1].isAxisTickVisible(TOP));

      for(int i = 0; i < 2; i++) {
         for(int axis : AXES) {
            assertEquals(ac[i].isAxisLabelVisible(axis), bc[i].isAxisLabelVisible(axis));
            assertEquals(ac[i].isAxisTickVisible(axis), bc[i].isAxisTickVisible(axis));
         }
      }
   }

   @Test
   @DisplayName("copyAxisVisibility with different child counts copies the common part only")
   void copyAxisVisibilityDifferentLengths() {
      TileCoord a = TileCoord.vertical(rect(), rect());

      for(Coordinate c : a.getCoordinates()) {
         c.setAxisLabelVisible(LEFT, false);
      }

      TileCoord b = TileCoord.vertical(rect(), rect(), rect());
      b.copyAxisVisibility(a);
      assertFalse(b.getCoordinates()[0].isAxisLabelVisible(LEFT));
      assertFalse(b.getCoordinates()[1].isAxisLabelVisible(LEFT));
      assertTrue(b.getCoordinates()[2].isAxisLabelVisible(LEFT));

      TileCoord c3 = TileCoord.vertical(rect(), rect(), rect());

      for(Coordinate c : c3.getCoordinates()) {
         c.setAxisLabelVisible(LEFT, false);
      }

      TileCoord d = TileCoord.vertical(rect(), rect());
      assertDoesNotThrow(() -> d.copyAxisVisibility(c3));
      assertFalse(d.getCoordinates()[0].isAxisLabelVisible(LEFT));
      assertFalse(d.getCoordinates()[1].isAxisLabelVisible(LEFT));
   }

   // ---------------------------------------------------------------- clone and equalsContent

   @Test
   @DisplayName("clone keeps the kind and length and copies the children")
   void cloneCopiesChildren() {
      TileCoord tile = TileCoord.vertical(rect(), rect());
      TileCoord copy = (TileCoord) tile.clone();

      assertNotSame(tile, copy);
      assertSame(tile.getClass(), copy.getClass());
      assertEquals(2, copy.getCoordinates().length);

      for(int i = 0; i < 2; i++) {
         assertNotSame(tile.getCoordinates()[i], copy.getCoordinates()[i]);
         assertTrue(tile.getCoordinates()[i].equalsContent(copy.getCoordinates()[i]));
      }
   }

   @Test
   @DisplayName("changing a child of a clone does not change the original")
   void cloneIsIsolated() {
      TileCoord tile = TileCoord.vertical(rect(), rect());
      TileCoord copy = (TileCoord) tile.clone();
      copy.getCoordinates()[0].setAxisLabelVisible(LEFT, false);

      assertTrue(tile.getCoordinates()[0].isAxisLabelVisible(LEFT));
      assertFalse(copy.getCoordinates()[0].isAxisLabelVisible(LEFT));
   }

   @Test
   @DisplayName("equalsContent: equal build, other direction, other length, other field, null, "
      + "non-tile")
   void equalsContentCases() {
      TileCoord tile = TileCoord.vertical(rect(), rect());

      assertTrue(tile.equalsContent(TileCoord.vertical(rect(), rect())));
      assertFalse(tile.equalsContent(TileCoord.horizontal(rect(), rect())));
      assertFalse(tile.equalsContent(TileCoord.vertical(rect())));
      assertFalse(tile.equalsContent(TileCoord.vertical(rect(), rect("Cat", "other"))));
      assertFalse(tile.equalsContent(null));
      assertFalse(tile.equalsContent(rect()));
   }

   // ---------------------------------------------------------------- depth

   private static Rect25Coord r25(double depth) {
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init("A", "B", "C");
      LinearScale ys = new LinearScale("m");
      Rect25Coord coord = new Rect25Coord(xs, ys);
      coord.setDepth(depth);
      return coord;
   }

   @Test
   @DisplayName("getDepth is the first rect coordinate's depth, non-rect children are skipped")
   void depthFromRectChild() {
      assertEquals(15, TileCoord.vertical(r25(15)).getDepth());
      assertEquals(0, TileCoord.vertical(new SC()).getDepth());
      assertEquals(7, TileCoord.vertical(new SC(), r25(7)).getDepth());
   }

   @Test
   @DisplayName("getDepth of a tile holding a facet comes from the facet")
   void depthFromNestedFacet() {
      FacetCoord facet = new FacetCoord(null, r25(15));
      assertEquals(15, TileCoord.vertical(facet).getDepth());
   }

   // ---------------------------------------------------------------- units, propagation

   @Test
   @DisplayName("unit min and preferred sizes of a tile are the largest of its children")
   void unitSizesAreMax() {
      SC[] cs = scs(3);
      double[] vals = { 10, 40, 25 };

      for(int i = 0; i < 3; i++) {
         cs[i].unitMinW = vals[i];
         cs[i].unitMinH = vals[i];
         cs[i].unitPrefW = vals[i];
         cs[i].unitPrefH = vals[i];
      }

      for(TileCoord tile : new TileCoord[] { vertical(cs), horizontal(cs) }) {
         assertEquals(40, tile.getUnitMinWidth());
         assertEquals(40, tile.getUnitMinHeight());
         assertEquals(40, tile.getUnitPreferredWidth());
         assertEquals(40, tile.getUnitPreferredHeight());
      }
   }

   @Test
   @DisplayName("setVGraph, setParentCoordinate and addParentValue reach every child")
   void propagationToChildren() {
      SC[] spies = new SC[3];

      for(int i = 0; i < 3; i++) {
         spies[i] = Mockito.spy(new SC());
      }

      TileCoord tile = vertical(spies);
      VGraph vg = Mockito.mock(VGraph.class);
      SC parent = new SC();
      tile.setVGraph(vg);
      tile.setParentCoordinate(parent);
      tile.addParentValue("f", "v", true);

      for(SC c : spies) {
         assertSame(vg, c.getVGraph());
         assertSame(parent, c.getParentCoordinate());
         Mockito.verify(c).addParentValue("f", "v", true);
      }

      assertSame(spies[0].getVGraph(), tile.getVGraph());
   }

   @Test
   @DisplayName("layoutCompleted clears the cache of every child")
   void layoutCompletedClearsChildCaches() {
      SC[] cs = scs(3);
      TileCoord tile = vertical(cs);

      for(SC c : cs) {
         c.getCoordCache().put("k", "v");
      }

      tile.layoutCompleted();

      for(SC c : cs) {
         assertTrue(c.getCoordCache().isEmpty());
      }
   }

   @Test
   @DisplayName("fit passes each child its own coordinate bounds")
   void fitUsesChildBounds() {
      SC[] cs = scs(2);
      cs[0].setCoordBounds(new java.awt.geom.Rectangle2D.Double(1, 2, 30, 40));
      cs[1].setCoordBounds(new java.awt.geom.Rectangle2D.Double(5, 6, 70, 80));
      vertical(cs).fit();

      assertEquals(1, cs[0].fits.size());
      assertArrayEquals(new double[] { 1, 2, 30, 40 }, cs[0].fits.get(0));
      assertArrayEquals(new double[] { 5, 6, 70, 80 }, cs[1].fits.get(0));
   }

   @Test
   @DisplayName("fit with a cancelled graph fits no child")
   void fitSkipsWhenCancelled() {
      SC[] cs = scs(2);

      for(SC c : cs) {
         c.setCoordBounds(new java.awt.geom.Rectangle2D.Double(0, 0, 10, 10));
      }

      VGraph vg = Mockito.mock(VGraph.class);
      Mockito.when(vg.isCancelled()).thenReturn(true);
      TileCoord tile = vertical(cs);
      tile.setVGraph(vg);
      tile.fit();

      for(SC c : cs) {
         assertTrue(c.fits.isEmpty());
      }
   }

   // ---------------------------------------------------------------- createAxisMap

   @Test
   @DisplayName("createAxisMap registers a visible rect tile under its key")
   void axisMapRegistersTile() {
      TileCoord tile = TileCoord.vertical(rect());
      Map<Object, Object> map = new HashMap<>();
      tile.createAxisMap(true, map, new AxisSizeStrategy(LEFT));

      assertEquals(1, map.size());
      AxisSizeList list = (AxisSizeList) map.get(tile.createKey(true));
      assertNotNull(list);
      assertEquals(1, list.getCount());
   }

   @Test
   @DisplayName("tiles with equal scales share one axis size list")
   void axisMapSharedKey() {
      Map<Object, Object> map = new HashMap<>();
      AxisSizeStrategy strategy = new AxisSizeStrategy(LEFT);
      TileCoord.vertical(rect()).createAxisMap(true, map, strategy);
      TileCoord.vertical(rect()).createAxisMap(true, map, strategy);

      assertEquals(1, map.size());
      assertEquals(2, ((AxisSizeList) map.values().iterator().next()).getCount());
   }

   @Test
   @DisplayName("tiles with different y fields get separate axis size lists")
   void axisMapDifferentKeys() {
      Map<Object, Object> map = new HashMap<>();
      AxisSizeStrategy strategy = new AxisSizeStrategy(LEFT);
      TileCoord.vertical(rect("Cat", "m1")).createAxisMap(true, map, strategy);
      TileCoord.vertical(rect("Cat", "m2")).createAxisMap(true, map, strategy);

      assertEquals(2, map.size());
   }

   @Test
   @DisplayName("createAxisMap skips a tile with hidden labels and a non-rect tile")
   void axisMapSkipsHiddenAndNonRect() {
      Map<Object, Object> map = new HashMap<>();
      AxisSizeStrategy strategy = new AxisSizeStrategy(LEFT);
      TileCoord hidden = TileCoord.vertical(rect());
      hidden.setAxisLabelVisible(LEFT, false);
      hidden.createAxisMap(true, map, strategy);
      assertTrue(map.isEmpty());

      vertical(new SC()).createAxisMap(true, map, strategy);
      assertTrue(map.isEmpty());
   }

   // ---------------------------------------------------------------- createKey

   @Test
   @DisplayName("vertical tile key: first child's key for false, all children's keys joined "
      + "for true")
   void verticalKey() {
      RectCoord c0 = rect("Cat", "m1");
      RectCoord c1 = rect("Cat", "m2");
      TileCoord tile = TileCoord.vertical(c0, c1);

      assertEquals(c0.createKey(false), tile.createKey(false));

      List<Object> expected = new ArrayList<>((List<?>) c0.createKey(true));
      expected.addAll((List<?>) c1.createKey(true));
      assertEquals(expected, tile.createKey(true));
   }

   @Test
   @DisplayName("vertical tiles that differ only in a later child's y field have different "
      + "vertical keys")
   void verticalKeyCollision() {
      TileCoord t1 = TileCoord.vertical(rect("Cat", "m1"), rect("Cat", "m2"));
      TileCoord t2 = TileCoord.vertical(rect("Cat", "m1"), rect("Cat", "m3"));

      assertNotEquals(t1.createKey(true), t2.createKey(true));
      assertEquals(t1.createKey(false), t2.createKey(false));
   }

   @Test
   @DisplayName("horizontal tile key: first child's key for true, all children's keys joined "
      + "for false")
   void horizontalKey() {
      RectCoord c0 = rect("x1", "m");
      RectCoord c1 = rect("x2", "m");
      TileCoord tile = TileCoord.horizontal(c0, c1);

      assertEquals(c0.createKey(true), tile.createKey(true));

      List<Object> expected = new ArrayList<>((List<?>) c0.createKey(false));
      expected.addAll((List<?>) c1.createKey(false));
      assertEquals(expected, tile.createKey(false));
   }

   @Test
   @DisplayName("horizontal tiles that differ only in a later child's x field have different "
      + "horizontal keys")
   void horizontalKeyCollision() {
      TileCoord t1 = TileCoord.horizontal(rect("x1", "m"), rect("x2", "m"));
      TileCoord t2 = TileCoord.horizontal(rect("x1", "m"), rect("x3", "m"));

      assertNotEquals(t1.createKey(false), t2.createKey(false));
      assertEquals(t1.createKey(true), t2.createKey(true));
   }
}
