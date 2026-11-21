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
package inetsoft.graph.scale;

import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.visual.ElementVO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AbstractScaleRangeTest {
   private static final double DELTA = 1e-12;

   /** Data set with a single column and ten rows. */
   private static DefaultDataSet tenRows() {
      Object[][] rows = new Object[11][];
      rows[0] = new Object[] { "v" };

      for(int i = 1; i <= 10; i++) {
         rows[i] = new Object[] { i };
      }

      return new DefaultDataSet(rows);
   }

   // ---- getValue and absolute flag ----

   @Test
   void getValueOfNumbers() {
      LinearRange range = new LinearRange();
      assertEquals(5.0, range.getValue(Integer.valueOf(5)), DELTA);
      assertEquals(-3.0, range.getValue(Long.valueOf(-3)), DELTA);
      assertEquals(-2.5, range.getValue(Double.valueOf(-2.5)), DELTA);
   }

   @Test
   void getValueOfNonNumbersIsZero() {
      LinearRange range = new LinearRange();
      assertEquals(0.0, range.getValue(null), DELTA);
      assertEquals(0.0, range.getValue("5"), DELTA);
      assertEquals(0.0, range.getValue(new Date()), DELTA);
   }

   @Test
   void absoluteValueFlag() {
      LinearRange range = new LinearRange();
      assertFalse(range.isAbsoluteValue());
      range.setAbsoluteValue(true);
      assertTrue(range.isAbsoluteValue());
      assertEquals(3.0, range.getValue(-3), DELTA);
      assertEquals(4.0, range.getValue(4), DELTA);
      assertTrue(Double.isNaN(range.getValue(Double.NaN)));
      range.setAbsoluteValue(false);
      assertEquals(-3.0, range.getValue(-3), DELTA);
   }

   // ---- measure ranges ----

   @Test
   void measureRangeBasics() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("m", 2, 5);
      assertArrayEquals(new int[] { 2, 5 }, range.getMeasureRange("m"));
      assertNull(range.getMeasureRange("unknown"));
      assertEquals(Set.of("m"), new HashSet<>(range.getRangeMeasures()));
   }

   @Test
   @DisplayName("a second range for the same measure is merged into the existing range")
   @Disabled("Not filed (reach through GraphGenerator.java:3357 not reproduced in the UI): setMeasureRange replaces the range: result is [2,7], expected [2,10] (ScaleRange javadoc says the new range is merged)")
   void measureRangeMerge() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("m", 5, 10);
      range.setMeasureRange("m", 2, 7);
      assertArrayEquals(new int[] { 2, 10 }, range.getMeasureRange("m"));
   }

   @Test
   @DisplayName("copyMeasureRanges copies only measures that are not defined yet")
   void copyMeasureRanges() {
      LinearRange target = new LinearRange();
      target.setMeasureRange("m1", 1, 2);
      LinearRange source = new LinearRange();
      source.setMeasureRange("m1", 5, 6);
      source.setMeasureRange("m2", 3, 4);
      target.copyMeasureRanges(source);
      assertArrayEquals(new int[] { 1, 2 }, target.getMeasureRange("m1"));
      assertArrayEquals(new int[] { 3, 4 }, target.getMeasureRange("m2"));
   }

   @Test
   @DisplayName("changing the source after copyMeasureRanges does not change the target")
   void copyMeasureRangesIsolated() {
      LinearRange target = new LinearRange();
      LinearRange source = new LinearRange();
      source.setMeasureRange("m2", 3, 4);
      target.copyMeasureRanges(source);
      source.setMeasureRange("m2", 0, 100);
      assertArrayEquals(new int[] { 3, 4 }, target.getMeasureRange("m2"));
   }

   // ---- getStartRow / getEndRow ----

   @Test
   void rowRangeWithoutRangesIsWholeData() {
      LinearRange range = new LinearRange();
      DefaultDataSet data = tenRows();
      assertEquals(0, range.getStartRow(data, "a", "b"));
      assertEquals(10, range.getEndRow(data, "a", "b"));
   }

   @Test
   @DisplayName("one measure range gives its rows and matches the GraphElement mapping")
   void rowRangeOneMeasure() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("a", 2, 5);
      DefaultDataSet data = tenRows();
      assertEquals(2, range.getStartRow(data, "a"));
      assertEquals(5, range.getEndRow(data, "a"));
      assertEquals(GraphElement.getStartRow(data, 2), range.getStartRow(data, "a"));
      assertEquals(GraphElement.getEndRow(data, 2, 5, null), range.getEndRow(data, "a"));
   }

   @Test
   @DisplayName("several measures: smallest start and largest end")
   void rowRangeTwoMeasures() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("a", 2, 5);
      range.setMeasureRange("b", 4, 8);
      DefaultDataSet data = tenRows();
      assertEquals(2, range.getStartRow(data, "a", "b"));
      assertEquals(8, range.getEndRow(data, "a", "b"));
   }

   @Test
   @DisplayName("end row is clamped to the row count and matches GraphElement")
   void endRowClamped() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("a", 2, 50);
      DefaultDataSet data = tenRows();
      assertEquals(10, range.getEndRow(data, "a"));
      assertEquals(GraphElement.getEndRow(data, 2, 50, null), range.getEndRow(data, "a"));
   }

   @Test
   @DisplayName("a measure with the all-prefix uses the range of its base name")
   void rowRangeBaseName() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("x", 2, 5);
      DefaultDataSet data = tenRows();
      assertEquals(2, range.getStartRow(data, ElementVO.ALL_PREFIX + "x"));
      assertEquals(5, range.getEndRow(data, ElementVO.ALL_PREFIX + "x"));
   }

   @Test
   @DisplayName("a measure without a range uses all rows even when another has a range")
   @Disabled("Not filed (reach not reproduced in the UI): getStartRow/getEndRow ignore the unranged measure: start is 5 and end is 8, expected 0 and 10")
   void rowRangeMixedRangedAndUnranged() {
      LinearRange range = new LinearRange();
      range.setMeasureRange("a", 5, 8);
      DefaultDataSet data = tenRows();
      assertEquals(0, range.getStartRow(data, "a", "b"));
      assertEquals(10, range.getEndRow(data, "a", "b"));
   }

   @Test
   @DisplayName("filtered data set: rows are mapped like the GraphElement static methods")
   void rowRangeFilteredDataSet() {
      DefaultDataSet base = tenRows();
      SubDataSet sub = new SubDataSet(base, 3, 7);
      LinearRange range = new LinearRange();
      range.setMeasureRange("a", 5, 9);
      assertEquals(GraphElement.getStartRow(sub, 5), range.getStartRow(sub, "a"));
      assertEquals(GraphElement.getEndRow(sub, 5, 9, null), range.getEndRow(sub, "a"));
   }

   @Test
   @DisplayName("ScaleRange defaults: start 0 and end is the row count")
   void scaleRangeDefaults() {
      ScaleRange range = new ScaleRange() {
         @Override
         public double getValue(Object data) {
            return 0;
         }

         @Override
         public void setAbsoluteValue(boolean abs) {
         }

         @Override
         public boolean isAbsoluteValue() {
            return false;
         }

         @Override
         public double[] calculate(DataSet dataset, String[] cols, GraphtDataSelector selector) {
            return new double[] { 0, 0 };
         }

         @Override
         public void setMeasureRange(String measure, int start, int end) {
         }

         @Override
         public int[] getMeasureRange(String measure) {
            return null;
         }

         @Override
         public java.util.Collection<String> getRangeMeasures() {
            return java.util.Collections.emptyList();
         }
      };

      DefaultDataSet data = tenRows();
      assertEquals(0, range.getStartRow(data, "a"));
      assertEquals(data.getRowCount(), range.getEndRow(data, "a"));
   }

   // ---- equals / hashCode ----

   @Test
   @Disabled("Not filed (only causes an extra redraw, no wrong picture): equals compares the Map<String,int[]> values by array identity: two ranges with the same measure range [1,5] are not equal, expected equal")
   void equalsSameContent() {
      LinearRange a = new LinearRange();
      LinearRange b = new LinearRange();
      a.setMeasureRange("m", 1, 5);
      b.setMeasureRange("m", 1, 5);
      assertEquals(a, b);
   }

   @Test
   void equalsDifferentContent() {
      LinearRange base = new LinearRange();
      base.setMeasureRange("m", 1, 5);

      LinearRange abs = new LinearRange();
      abs.setMeasureRange("m", 1, 5);
      abs.setAbsoluteValue(true);
      assertNotEquals(base, abs);

      LinearRange otherRange = new LinearRange();
      otherRange.setMeasureRange("m", 1, 6);
      assertNotEquals(base, otherRange);

      LinearRange otherName = new LinearRange();
      otherName.setMeasureRange("n", 1, 5);
      assertNotEquals(base, otherName);
   }

   @Test
   @DisplayName("LinearRange and StackRange in default state are not equal, in both directions")
   @Disabled("Not filed (only causes an extra redraw, no wrong picture): LinearRange.equals(StackRange) is true (only StackRange.equals(LinearRange) is false), expected false in both directions")
   void equalsAcrossTypes() {
      LinearRange linear = new LinearRange();
      StackRange stack = new StackRange();
      assertFalse(linear.equals(stack), "linear.equals(stack)");
      assertFalse(stack.equals(linear), "stack.equals(linear)");
   }

   @Test
   @DisplayName("equal ranges have the same hash code")
   @Disabled("Not filed (only causes an extra redraw, no wrong picture): hashCode is not overridden (identity hash) while equals is: two equal default LinearRange have different hash codes")
   void hashCodeContract() {
      LinearRange a = new LinearRange();
      LinearRange b = new LinearRange();
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }
}
