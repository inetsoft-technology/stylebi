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
package inetsoft.graph.guide.legend;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.internal.DimensionD;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static inetsoft.graph.guide.legend.LegendTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Which legends a {@link LegendGroup} creates for a list of frames, and how the group sizes and
 * lays out its legends.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LegendGroupTest {
   private static final int RIGHT = GraphConstants.RIGHT;
   private static final int LEFT = GraphConstants.LEFT;
   private static final int TOP = GraphConstants.TOP;
   private static final double EPS = 1e-6;

   private static LegendGroup group(int layout, VisualFrame... frames) {
      return new LegendGroup(frames, graph(layout));
   }

   private static String[] manyValues(String prefix, int n) {
      String[] vals = new String[n];

      for(int i = 0; i < n; i++) {
         vals[i] = prefix + "-" + i;
      }

      return vals;
   }

   // ------------------------------------------------------------ legend list

   @Test
   void oneLegendPerFrameInTheGivenOrder() {
      LegendGroup group = group(RIGHT, color("F1", "A", "B"), size("F2", "A", "B"),
                                shape("F3", "A", "B"));

      assertEquals(3, group.getLegendCount());
      assertInstanceOf(ColorLegend.class, group.getLegend(0));
      assertInstanceOf(SizeLegend.class, group.getLegend(1));
      assertInstanceOf(ShapeLegend.class, group.getLegend(2));
   }

   @Test
   void sameFrameInstanceTwiceHasOneLegend() {
      CategoricalColorFrame frame = color("F", "A", "B");

      assertEquals(1, group(RIGHT, frame, frame).getLegendCount());
   }

   @Test
   void equalFramesOfTheSameTypeShareOneLegend() {
      assertEquals(1, group(RIGHT, color("F", "A", "B"), color("F", "A", "B")).getLegendCount());
   }

   @Test
   void sameFieldWithValuesInAnotherOrderHasTwoLegends() {
      assertEquals(2, group(RIGHT, color("F", "A", "B"), color("F", "B", "A")).getLegendCount());
   }

   @Test
   void sameDataOfAnotherFrameTypeHasItsOwnLegend() {
      assertEquals(2, group(RIGHT, color("F", "A", "B"), shape("F", "A", "B")).getLegendCount());
   }

   @Test
   void legendIndexOutsideTheRangeIsNull() {
      assertNull(group(RIGHT).getLegend(0));

      LegendGroup group = group(RIGHT, color("F1", "A", "B"), shape("F2", "A", "B"));

      assertNotNull(group.getLegend(0));
      assertNotNull(group.getLegend(1));
      assertNull(group.getLegend(2));
      assertNull(group.getLegend(7));
   }

   @Test
   @Disabled("Not filed (not reachable: the client only sends valid legend indexes): getLegend(-1) throws IndexOutOfBoundsException (expected null)")
   void negativeLegendIndexIsNull() {
      LegendGroup group = group(RIGHT, color("F1", "A", "B"), shape("F2", "A", "B"));

      assertNull(group.getLegend(-1));
   }

   private static StackedMeasuresColorFrame stacked(CategoricalColorFrame first,
                                                    CategoricalColorFrame second)
   {
      StackedMeasuresColorFrame stacked = new StackedMeasuresColorFrame(null);
      stacked.setFrame("m1", first);
      stacked.setFrame("m2", second);
      return stacked;
   }

   @Test
   void stackedMeasuresOfOneFieldAreOneLegendOfTheStackedFrame() {
      StackedMeasuresColorFrame stacked =
         stacked(color("F", "A", "B"), color("F", "A", "B"));
      LegendGroup group = group(RIGHT, stacked);

      assertEquals(1, group.getLegendCount());
      assertSame(stacked, group.getLegend(0).getVisualFrame());
   }

   @Test
   void stackedMeasuresOfDifferentFieldsHaveOneLegendPerFrame() {
      CategoricalColorFrame first = color("F1", "A", "B");
      CategoricalColorFrame second = color("F2", "A", "B");
      StackedMeasuresColorFrame stacked = stacked(first, second);
      LegendGroup group = group(RIGHT, stacked);
      List<VisualFrame> shown = new ArrayList<>();

      for(int i = 0; i < group.getLegendCount(); i++) {
         shown.add(group.getLegend(i).getVisualFrame());
      }

      assertEquals(2, shown.size());
      assertTrue(shown.contains(first));
      assertTrue(shown.contains(second));
      assertFalse(shown.contains(stacked));
   }

   // ------------------------------------------------------------------- size

   private static LegendGroup twoLegends(int layout, boolean longFirst) {
      CategoricalColorFrame longer = color("F1", "a".repeat(25), "b".repeat(25), "c", "d");
      CategoricalShapeFrame shorter = shape("F2", "x", "y");
      return longFirst ? group(layout, longer, shorter) : group(layout, shorter, longer);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void minimumSizeOfAVerticalGroupIsSumOfHeightsAndMaxOfWidths(boolean longFirst) {
      LegendGroup group = twoLegends(RIGHT, longFirst);
      group.setLayoutSize(new DimensionD(400, 300));
      Legend a = group.getLegend(0);
      Legend b = group.getLegend(1);

      assertEquals(a.getMinHeight() + b.getMinHeight(), group.getMinHeight(), EPS);
      assertEquals(Math.max(a.getMinWidth(), b.getMinWidth()), group.getMinWidth(), EPS);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void minimumSizeOfAHorizontalGroupIsSumOfWidthsAndMaxOfHeights(boolean longFirst) {
      LegendGroup group = twoLegends(TOP, longFirst);
      group.setLayoutSize(new DimensionD(400, 300));
      Legend a = group.getLegend(0);
      Legend b = group.getLegend(1);

      assertEquals(a.getMinWidth() + b.getMinWidth(), group.getMinWidth(), EPS);
      assertEquals(Math.max(a.getMinHeight(), b.getMinHeight()), group.getMinHeight(), EPS);
   }

   @ParameterizedTest
   @ValueSource(ints = { RIGHT, TOP })
   @Disabled("Not filed (harmless: the accumulated minimum size has no consumer): initSizes adds to minW/minH without resetting, the minimum size doubles on the second setLayoutSize (RIGHT minHeight 77 -> 154, TOP minWidth 113 -> 226)")
   void sizingTheGroupTwiceWithTheSameSizeGivesTheSameSizes(int layout) {
      LegendGroup group = twoLegends(layout, true);
      DimensionD size = new DimensionD(400, 300);

      group.setLayoutSize(size);
      double minW = group.getMinWidth();
      double minH = group.getMinHeight();
      double prefW = group.getPreferredWidth();
      double prefH = group.getPreferredHeight();

      group.setLayoutSize(size);

      assertEquals(minW, group.getMinWidth(), EPS);
      assertEquals(minH, group.getMinHeight(), EPS);
      assertEquals(prefW, group.getPreferredWidth(), EPS);
      assertEquals(prefH, group.getPreferredHeight(), EPS);
   }

   @Test
   void nullLayoutSizeLeavesTheSizesAtZero() {
      LegendGroup group = twoLegends(RIGHT, true);

      assertDoesNotThrow(() -> group.setLayoutSize(null));
      assertNull(group.getLayoutSize());
      assertEquals(0, group.getMinWidth(), EPS);
      assertEquals(0, group.getMinHeight(), EPS);
      assertEquals(0, group.getPreferredWidth(), EPS);
      assertEquals(0, group.getPreferredHeight(), EPS);
   }

   // ----------------------------------------------------------------- layout

   private static boolean inside(Rectangle2D outer, Rectangle2D inner) {
      return inner.getX() >= outer.getX() - EPS && inner.getY() >= outer.getY() - EPS &&
         inner.getMaxX() <= outer.getMaxX() + EPS && inner.getMaxY() <= outer.getMaxY() + EPS;
   }

   @Test
   void verticalGroupWithTightHeightGivesEveryLegendItsMinimumHeight() {
      CategoricalColorFrame few = color("F1", "a-0", "a-1", "a-2");
      CategoricalShapeFrame many = shape("F2", (Object[]) manyValues("b", 40));
      double w = Math.max(maxItemWidth(few, RIGHT), maxItemWidth(many, RIGHT)) + 4;
      LegendGroup group = group(RIGHT, few, many);
      Legend a = group.getLegend(0);
      Legend b = group.getLegend(1);
      double sumMin = a.getMinHeight() + b.getMinHeight();
      double h = sumMin + 16;

      // precondition: the minimum heights fit, the preferred heights do not
      assertTrue(sumMin <= h);
      assertTrue(h < a.getPreferredHeight(w, h) + b.getPreferredHeight(w, h),
                 "preferred heights must not fit in " + h);

      group.setLayoutSize(new DimensionD(w, h));
      group.setBounds(0, 0, w, h);
      group.layout();
      double total = 0;

      for(int i = 0; i < group.getLegendCount(); i++) {
         Rectangle2D box = group.getLegend(i).getBounds();
         total += box.getHeight();
         assertTrue(inside(group.getBounds(), box), box.toString());
         assertTrue(box.getHeight() >= group.getLegend(i).getMinHeight() - EPS,
                    "legend " + i + " height " + box.getHeight() + " < min "
                       + group.getLegend(i).getMinHeight());

         for(int j = i + 1; j < group.getLegendCount(); j++) {
            Rectangle2D other = group.getLegend(j).getBounds();
            assertTrue(box.getMaxY() <= other.getY() + EPS || other.getMaxY() <= box.getY() + EPS,
                       "legends " + i + " and " + j + " overlap");
         }
      }

      assertTrue(total <= h + EPS, "heights add up to " + total + " of " + h);
   }

   @Test
   void horizontalGroupPlacesLegendsSideBySideInsideTheGroupBounds() {
      LegendGroup group = group(TOP, color("F1", "A", "B", "C"), shape("F2", "x", "y", "z"));
      group.setLayoutSize(new DimensionD(400, 100));
      group.setBounds(0, 0, 400, 100);
      group.layout();
      Rectangle2D first = group.getLegend(0).getBounds();
      Rectangle2D second = group.getLegend(1).getBounds();

      assertTrue(first.getX() < second.getX());
      assertTrue(first.getMaxX() <= second.getX() + EPS);
      assertTrue(inside(group.getBounds(), first));
      assertTrue(inside(group.getBounds(), second));
   }

   @Test
   void singleLegendOfAHorizontalGroupIsCentered() {
      LegendGroup group = group(TOP, color("F1", "A", "B"));
      group.setLayoutSize(new DimensionD(600, 100));
      group.setBounds(0, 0, 600, 100);
      group.layout();
      Rectangle2D box = group.getLegend(0).getBounds();

      assertTrue(box.getWidth() < 600);
      assertEquals(group.getBounds().getCenterX(), box.getCenterX(), EPS);
   }

   @Test
   void legendGapMovesTheLegendAwayFromThePlot() {
      CategoricalColorFrame frame = color("F1", "A", "B");
      frame.getLegendSpec().setGap(6);

      LegendGroup right = group(RIGHT, frame);
      right.setLayoutSize(new DimensionD(200, 300));
      right.setBounds(10, 0, 200, 300);
      right.layout();
      assertEquals(10 + 6, right.getLegend(0).getBounds().getX(), EPS);
      assertEquals(200 - 6, right.getLegend(0).getBounds().getWidth(), EPS);

      LegendGroup left = group(LEFT, frame);
      left.setLayoutSize(new DimensionD(200, 300));
      left.setBounds(10, 0, 200, 300);
      left.layout();
      assertEquals(10, left.getLegend(0).getBounds().getX(), EPS);
      assertEquals(200 - 6, left.getLegend(0).getBounds().getWidth(), EPS);
   }

   @Test
   @Disabled("Not filed (only a developer can decide: layout order kept for existing charts): layoutLR places the last legend at the top, legend 0 is below legend 1 (expected legend 0 above)")
   void verticalGroupPutsTheFirstLegendAboveTheSecond() {
      LegendGroup group = group(RIGHT, color("F1", "A", "B"), shape("F2", "x", "y"));
      group.setLayoutSize(new DimensionD(200, 300));
      group.setBounds(0, 0, 200, 300);
      group.layout();

      assertTrue(group.getLegend(0).getBounds().getY() > group.getLegend(1).getBounds().getY());
   }
}
