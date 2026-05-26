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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.visual.ElementVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StackRangeTest {

   // Build a dataset from a header row + data rows.
   private DefaultDataSet ds(Object[]... rows) {
      return new DefaultDataSet(rows);
   }

   // ---- Basic positive stacking (no group field) ----

   @Test
   void positiveValuesCumulativeSum() {
      // No grouping: all values accumulated together
      // Single measure column "v": 10, 20, 30 → cumulative max = 60
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{10.0},
         new Object[]{20.0},
         new Object[]{30.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"v"}, null);
      assertEquals(60.0, result[1]);
      assertTrue(result[0] <= 0.0, "min should be <= 0 when only positives stacked");
   }

   @Test
   void multipleColumnRowSumPerRow() {
      // With no grouping and two measures, each row sums across cols then accumulates
      // Row 0: a=5, b=3 → sum=8; row 1: a=2, b=4 → sum=6 → total = 14
      DefaultDataSet data = ds(
         new Object[]{"a", "b"},
         new Object[]{5.0, 3.0},
         new Object[]{2.0, 4.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"a", "b"}, null);
      assertEquals(14.0, result[1]);
   }

   // ---- Negative stacking ----

   @Test
   void negativeValuesStackedSeparately() {
      // negGrp=true (default): negatives accumulate downward
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{-5.0},
         new Object[]{-3.0},
         new Object[]{-8.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"v"}, null);
      // All negative → max = 0, min = -16
      assertEquals(0.0, result[1]);
      assertEquals(-16.0, result[0]);
   }

   @Test
   void mixedPositiveNegativeStackedSeparately() {
      // positives accumulate upward, negatives accumulate downward
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{10.0},
         new Object[]{-4.0},
         new Object[]{5.0},
         new Object[]{-6.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"v"}, null);
      // Positives: 10 + 5 = 15; negatives: -4 + -6 = -10
      assertEquals(15.0, result[1]);
      assertEquals(-10.0, result[0]);
   }

   @Test
   void stackNegativeFalseAccumulatesAllValuesTogether() {
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{10.0},
         new Object[]{-4.0},
         new Object[]{5.0}
      );
      StackRange range = new StackRange();
      range.setStackNegative(false);
      double[] result = range.calculate(data, new String[]{"v"}, null);
      // All accumulated together: 10 + (-4) + 5 = 11
      assertEquals(11.0, result[1]);
   }

   @Test
   void stackNegativeGetterSetter() {
      StackRange range = new StackRange();
      assertTrue(range.isStackNegative());
      range.setStackNegative(false);
      assertFalse(range.isStackNegative());
      range.setStackNegative(true);
      assertTrue(range.isStackNegative());
   }

   // ---- Group field ----

   @Test
   void groupFieldStacksPerGroup() {
      // Group by "cat"; cat=A has values 10, 20; cat=B has values 5, 15
      // A: sum=30, B: sum=20 → max=30
      DefaultDataSet data = ds(
         new Object[]{"cat", "v"},
         new Object[]{"A", 10.0},
         new Object[]{"A", 20.0},
         new Object[]{"B", 5.0},
         new Object[]{"B", 15.0}
      );
      StackRange range = new StackRange();
      range.setGroupField("cat");
      double[] result = range.calculate(data, new String[]{"v"}, null);
      assertEquals(30.0, result[1]);
   }

   @Test
   void groupFieldGetterSetter() {
      StackRange range = new StackRange();
      assertNull(range.getGroupField());
      range.setGroupField("cat");
      assertEquals("cat", range.getGroupField());
   }

   // ---- Stack fields filtering ----

   @Test
   void addStackFieldsLimitsStacking() {
      // When stackfields are set: columns in the stack group are stacked,
      // columns not in any stack group use linear range.
      // stack group [a]: a values 10, 20 → stacked max = 30
      // b uses linear: values 5, 8 → linear max = 8
      // Overall max = max(30, 8) = 30
      DefaultDataSet data = ds(
         new Object[]{"a", "b"},
         new Object[]{10.0, 5.0},
         new Object[]{20.0, 8.0}
      );
      StackRange range = new StackRange();
      range.addStackFields("a");
      double[] result = range.calculate(data, new String[]{"a", "b"}, null);
      assertEquals(30.0, result[1]);
   }

   @Test
   void removeAllStackFieldsClearsGroups() {
      StackRange range = new StackRange();
      range.addStackFields("a", "b");
      range.removeAllStackFields();
      // After removal: all columns stacked together (default behavior)
      DefaultDataSet data = ds(
         new Object[]{"a", "b"},
         new Object[]{3.0, 4.0}
      );
      double[] result = range.calculate(data, new String[]{"a", "b"}, null);
      // No stack groups → all in one group → row sum = 7
      assertEquals(7.0, result[1]);
   }

   // ---- ALL_PREFIX brush handling ----

   @Test
   void allPrefixColumnCausesBaseColumnToBeRemovedFromMeasures() {
      // If "__all__v" is present, then "v" is removed from the measures list.
      String allPrefixCol = ElementVO.ALL_PREFIX + "v";
      DefaultDataSet data = ds(
         new Object[]{"v", allPrefixCol},
         new Object[]{10.0, 100.0},
         new Object[]{20.0, 200.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"v", allPrefixCol}, null);
      // "v" is removed; only "__all__v" is stacked: 100 + 200 = 300
      assertEquals(300.0, result[1]);
   }

   // ---- ROWID group field ----

   @Test
   void rowidGroupFieldTreatsEachRowAsItsOwnGroup() {
      // With ROWID, each row is its own group → no accumulation across rows
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{10.0},
         new Object[]{20.0},
         new Object[]{30.0}
      );
      StackRange range = new StackRange();
      range.setGroupField(StackRange.ROWID);
      double[] result = range.calculate(data, new String[]{"v"}, null);
      // Each row is its own group → max is the single-row max = 30.0
      assertEquals(30.0, result[1]);
   }

   // ---- Null values ----

   @Test
   void nullValuesAreSkipped() {
      DefaultDataSet data = ds(
         new Object[]{"v"},
         new Object[]{(Object) null},
         new Object[]{5.0},
         new Object[]{(Object) null},
         new Object[]{10.0}
      );
      StackRange range = new StackRange();
      double[] result = range.calculate(data, new String[]{"v"}, null);
      assertEquals(15.0, result[1]);
   }

   // ---- equals ----

   @Test
   void equalityCheckSameConfiguration() {
      StackRange r1 = new StackRange();
      r1.setGroupField("grp");
      r1.setStackNegative(true);

      StackRange r2 = new StackRange();
      r2.setGroupField("grp");
      r2.setStackNegative(true);

      assertEquals(r1, r2);
   }

   @Test
   void equalityCheckDifferentGroupField() {
      StackRange r1 = new StackRange();
      r1.setGroupField("grp1");

      StackRange r2 = new StackRange();
      r2.setGroupField("grp2");

      assertNotEquals(r1, r2);
   }

   @Test
   void equalityCheckDifferentStackNegative() {
      StackRange r1 = new StackRange();
      r1.setStackNegative(true);

      StackRange r2 = new StackRange();
      r2.setStackNegative(false);

      assertNotEquals(r1, r2);
   }
}
