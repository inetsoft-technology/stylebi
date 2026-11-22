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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Size frames: the linear frame (value to size by position in the scale range), the categorical
 * frame (value order to size, explicit sizes), and the treemap frame (sizes of at least 1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class SizeFramesTest {
   private static final double EPS = 1e-9;

   private static LinearSizeFrame linear() {
      LinearSizeFrame frame = new LinearSizeFrame("v");
      frame.setScale(new LinearScale(0, 100));
      return frame;
   }

   private static DefaultDataSet column(String header, Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { header };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static CategoricalSizeFrame categorical(Object... values) {
      CategoricalSizeFrame frame = new CategoricalSizeFrame("g");
      frame.init(column("g", values));
      return frame;
   }

   private static TreemapSizeFrame treemap(double min, double max) {
      TreemapSizeFrame frame = new TreemapSizeFrame("v");
      frame.setScale(new LinearScale(min, max));
      return frame;
   }

   // ---- LinearSizeFrame ----

   @Test
   void linearSizeInterpolatesBetweenSmallestAndLargest() {
      LinearSizeFrame frame = linear();
      double[] values = { 0, 25, 50, 75, 100 };
      double[] expected = { 1, 8.25, 15.5, 22.75, 30 };

      for(int i = 0; i < values.length; i++) {
         assertEquals(expected[i], frame.getSize(values[i]), EPS, "value " + values[i]);
      }
   }

   @Test
   void linearSizeUsesCustomRange() {
      LinearSizeFrame frame = linear();
      frame.setSmallest(10);
      frame.setLargest(50);

      assertEquals(10, frame.getSize(0), EPS);
      assertEquals(30, frame.getSize(50), EPS);
      assertEquals(50, frame.getSize(100), EPS);
   }

   @Test
   void linearSizeWithInvertedRangeInterpolatesBetweenTheTwoNumbers() {
      LinearSizeFrame frame = linear();
      frame.setSmallest(10);
      frame.setLargest(5);

      assertEquals(10, frame.getSize(0), EPS);
      assertEquals(5, frame.getSize(100), EPS);
   }

   @Test
   void linearSizeWithoutScaleIsTheLargest() {
      assertEquals(30, new LinearSizeFrame("v").getSize("x"), EPS);
   }

   @Test
   void linearSizeOfNullAndTextIsTheSmallest() {
      LinearSizeFrame frame = linear();

      assertEquals(1, frame.getSize((Object) null), EPS);
      assertEquals(1, frame.getSize("abc"), EPS);
   }

   @Test
   void linearSizeAboveTheMaximumIsClampedToTheLargest() {
      assertEquals(30, linear().getSize(150), EPS);
   }

   @Test
   @Disabled("Bug #82046: Linear frames do not clamp a value below the scale minimum")
   void linearSizeBelowTheMinimumNeverGoesBelowTheSmallest() {
      LinearSizeFrame frame = linear();

      assertEquals(1, frame.getSize(-50), EPS);
      assertTrue(frame.getSize(-50) >= frame.getSmallest());
   }

   @Test
   void linearSizeOnReversedScaleRunsBackwards() {
      LinearScale scale = new LinearScale(0, 100);
      scale.setReversed(true);
      LinearSizeFrame frame = new LinearSizeFrame("v");
      frame.setScale(scale);

      assertEquals(30, frame.getSize(0), EPS);
      assertEquals(1, frame.getSize(100), EPS);
   }

   @Test
   void linearSizeFromDataSetEqualsTheValuePath() {
      DefaultDataSet data = column("v", 0, 50, 100);
      LinearSizeFrame frame = new LinearSizeFrame("v");
      frame.init(data);

      double[] expected = { 1, 15.5, 30 };

      for(int row = 0; row < 3; row++) {
         assertEquals(expected[row], frame.getSize(data, null, row), EPS, "row " + row);
         assertEquals(frame.getSize(data.getData("v", row)), frame.getSize(data, "other", row),
                      EPS);
      }
   }

   @Test
   void linearSizeOfEqualValuesIsLargestAndFinite() {
      DefaultDataSet data = column("v", 5, 5, 5);
      LinearSizeFrame frame = new LinearSizeFrame("v");
      frame.init(data);
      double size = frame.getSize(5);

      assertTrue(Double.isFinite(size));
      assertEquals(30, size, EPS);
   }

   @Test
   void sizeFrameContentEqualityFollowsLargestSmallestAndMax() {
      LinearScale scale = new LinearScale(0, 100);
      LinearSizeFrame a = new LinearSizeFrame("v");
      LinearSizeFrame b = new LinearSizeFrame("v");
      a.setScale(scale);
      b.setScale(scale);

      assertTrue(a.equalsContent(b));

      b.setLargest(20);
      assertFalse(a.equalsContent(b));
      b.setLargest(a.getLargest());
      assertTrue(a.equalsContent(b));

      b.setSmallest(2);
      assertFalse(a.equalsContent(b));
      b.setSmallest(a.getSmallest());
      assertTrue(a.equalsContent(b));

      b.setMax(99);
      assertFalse(a.equalsContent(b));
      b.setMax(a.getMax());
      assertTrue(a.equalsContent(b));
   }

   // ---- CategoricalSizeFrame ----

   @Test
   void categoricalSizeFollowsValueOrder() {
      CategoricalSizeFrame frame = categorical("A", "B", "C");

      assertEquals(1, frame.getSize("A"), EPS);
      assertEquals(15.5, frame.getSize("B"), EPS);
      assertEquals(30, frame.getSize("C"), EPS);
   }

   @Test
   void categoricalSizeOfTwoFourAndOneValues() {
      CategoricalSizeFrame two = categorical("A", "B");
      assertEquals(1, two.getSize("A"), EPS);
      assertEquals(30, two.getSize("B"), EPS);

      CategoricalSizeFrame four = categorical("A", "B", "C", "D");
      assertEquals(1, four.getSize("A"), 1e-4);
      assertEquals(10.6667, four.getSize("B"), 1e-4);
      assertEquals(20.3333, four.getSize("C"), 1e-4);
      assertEquals(30, four.getSize("D"), 1e-4);

      CategoricalSizeFrame one = categorical("A");
      assertEquals(30, one.getSize("A"), EPS);
   }

   @Test
   void categoricalSizeUsesCustomRange() {
      CategoricalSizeFrame frame = new CategoricalSizeFrame("g");
      frame.setSmallest(10);
      frame.setLargest(20);
      frame.init(column("g", "A", "B", "C"));

      assertEquals(10, frame.getSize("A"), EPS);
      assertEquals(15, frame.getSize("B"), EPS);
      assertEquals(20, frame.getSize("C"), EPS);
   }

   @Test
   void categoricalExplicitSizeWinsAndNaNRemovesIt() {
      CategoricalSizeFrame frame = categorical("A", "B", "C");

      frame.setSize("B", 7);
      assertEquals(7, frame.getSize("B"), EPS);
      assertTrue(frame.isStatic("B"));

      frame.setSize("B", Double.NaN);
      assertEquals(15.5, frame.getSize("B"), EPS);
      assertFalse(frame.isStatic("B"));
   }

   @Test
   void categoricalSizeOfUnknownIsSmallestAndNullTakesItsOwnSlot() {
      CategoricalSizeFrame frame = categorical("A", "B", "C");
      assertEquals(1, frame.getSize("Z"), EPS);

      CategoricalSizeFrame withNull = categorical("A", null, "B");
      assertEquals(15.5, withNull.getSize((Object) null), EPS);
      assertEquals(30, withNull.getSize("B"), EPS);
   }

   @Test
   void categoricalSizeFromMeasureNames() {
      CategoricalSizeFrame frame = new CategoricalSizeFrame();
      frame.init(new Object[] { "m1", "m2", "m3" }, new double[] { 5, 10, 15 });

      assertEquals(10, frame.getSize("m2"), EPS);
      assertEquals(15, frame.getSize(column("x", 1), "m3", 0), EPS);
   }

   @Test
   void categoricalCloneEqualsAndUniqueIdSeeAssignments() {
      CategoricalSizeFrame frame = categorical("A", "B", "C");
      CategoricalSizeFrame copy = (CategoricalSizeFrame) frame.clone();

      assertEquals(frame, copy);
      assertEquals(frame.getUniqueId(), copy.getUniqueId());

      frame.setSize("A", 9);
      assertNotEquals(frame, copy);
      assertNotEquals(frame.getUniqueId(), copy.getUniqueId());

      copy.setSize("B", 3);
      assertEquals(15.5, frame.getSize("B"), EPS);
      assertFalse(frame.isStatic("B"));
   }

   // ---- TreemapSizeFrame ----

   @Test
   void treemapSizeFromZeroMinimumScalesToAThousand() {
      TreemapSizeFrame frame = treemap(0, 100);

      assertEquals(1, frame.getSmallest(), EPS);
      assertEquals(1000, frame.getLargest(), EPS);
      assertEquals(1, frame.getSize(0), EPS);
      assertEquals(500.5, frame.getSize(50), EPS);
      assertEquals(1000, frame.getSize(100), EPS);
   }

   @Test
   void treemapSizeFromMinimumAtLeastOneEqualsTheValue() {
      TreemapSizeFrame frame = treemap(5, 50);

      assertEquals(5, frame.getSmallest(), EPS);
      assertEquals(50, frame.getLargest(), EPS);
      assertEquals(5, frame.getSize(5), EPS);
      assertEquals(27.5, frame.getSize(27.5), EPS);
      assertEquals(50, frame.getSize(50), EPS);
   }

   @Test
   void treemapSizeShiftsNegativeValues() {
      TreemapSizeFrame frame = treemap(-10, 10);

      assertEquals(1, frame.getSmallest(), EPS);
      assertEquals(21, frame.getLargest(), EPS);
      assertEquals(1, frame.getSize(-10), EPS);
      assertEquals(11, frame.getSize(0), EPS);
      assertEquals(21, frame.getSize(10), EPS);
   }

   @Test
   void treemapSizeWithFractionalMinimum() {
      // scale [0.5, 4]: largest = max(4, min(1000, 4 / 0.5)) = 8. (A LinearScale given [0.5, 10]
      // reports max 11.4, so a range that the scale keeps exactly is used.)
      TreemapSizeFrame frame = treemap(0.5, 4);

      assertEquals(1, frame.getSmallest(), EPS);
      assertEquals(8, frame.getLargest(), EPS);
      assertEquals(1, frame.getSize(0.5), EPS);
      assertEquals(8, frame.getSize(4), EPS);
   }

   @Test
   void treemapSizeWithMinimumExactlyOne() {
      TreemapSizeFrame frame = treemap(1, 100);

      assertEquals(1, frame.getSmallest(), EPS);
      assertEquals(100, frame.getLargest(), EPS);
      assertEquals(1, frame.getSize(1), EPS);
      assertEquals(100, frame.getSize(100), EPS);
   }

   @Test
   void treemapSizeWithoutScaleUsesTheDefaultRange() {
      TreemapSizeFrame frame = new TreemapSizeFrame("v");

      assertEquals(1, frame.getSmallest(), EPS);
      assertEquals(30, frame.getLargest(), EPS);
      assertEquals(30, frame.getSize("x"), EPS);
   }

   @Test
   void treemapSizeIgnoresTheUserRange() {
      TreemapSizeFrame frame = treemap(0, 100);
      frame.setSmallest(7);
      frame.setLargest(9);

      assertEquals(1, frame.getSize(0), EPS);
      assertEquals(1000, frame.getSize(100), EPS);
   }

   @ParameterizedTest(name = "scale [{0}, {1}]")
   @CsvSource({ "0, 100", "-10, 10", "0.5, 4", "5, 50", "-1000, -5" })
   void treemapSizesAreAtLeastOneAndNeverAboveTheLargest(double min, double max) {
      TreemapSizeFrame frame = treemap(min, max);

      for(double v = Math.ceil(min); v <= max; v++) {
         double size = frame.getSize(v);

         assertTrue(size >= 1 - EPS, "size " + size + " of " + v + " is below 1");
         assertTrue(size <= frame.getLargest() + EPS,
                    "size " + size + " of " + v + " is above the largest");
      }
   }

   // ---- properties across sizes ----

   @Test
   void categoricalSizesGrowStrictlyWithTheValueOrder() {
      CategoricalSizeFrame frame = categorical("A", "B", "C", "D", "E");
      Object[] values = frame.getValues();

      assertEquals(5, values.length);
      assertEquals(frame.getSmallest(), frame.getSize(values[0]), EPS);
      assertEquals(frame.getLargest(), frame.getSize(values[4]), EPS);

      for(int i = 1; i < values.length; i++) {
         assertTrue(frame.getSize(values[i]) > frame.getSize(values[i - 1]),
                    "size does not grow at " + values[i]);
      }
   }

   @Test
   void linearSizesAtTheLegendValuesAreNonDecreasingAndInRange() {
      LinearSizeFrame frame = linear();
      Object[] values = frame.getValues();

      assertTrue(values.length >= 2);
      double previous = Double.NEGATIVE_INFINITY;

      for(Object v : values) {
         double size = frame.getSize(v);

         assertTrue(size >= previous, "size decreases at " + v);
         assertTrue(size >= frame.getSmallest() - EPS && size <= frame.getLargest() + EPS);
         previous = size;
      }
   }
}
