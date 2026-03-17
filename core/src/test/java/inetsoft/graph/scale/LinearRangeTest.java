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
import inetsoft.graph.element.GraphtDataSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinearRangeTest {

   // Build a single-column dataset with column "value"
   private DefaultDataSet singleCol(Object... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[]{"value"};
      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[]{values[i]};
      }
      return new DefaultDataSet(rows);
   }

   // Build a two-column dataset with columns "a" and "b"
   private DefaultDataSet twoCol(Double[] aVals, Double[] bVals) {
      Object[][] rows = new Object[aVals.length + 1][];
      rows[0] = new Object[]{"a", "b"};
      for(int i = 0; i < aVals.length; i++) {
         rows[i + 1] = new Object[]{aVals[i], bVals[i]};
      }
      return new DefaultDataSet(rows);
   }

   // ---- Basic min/max ----

   @Test
   void minAndMaxFromPositiveValues() {
      DefaultDataSet data = singleCol(3.0, 1.0, 5.0, 2.0, 4.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(1.0, result[0]);
      assertEquals(5.0, result[1]);
   }

   @Test
   void minAndMaxWithNegativeValues() {
      DefaultDataSet data = singleCol(-10.0, 0.0, 5.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(-10.0, result[0]);
      assertEquals(5.0, result[1]);
   }

   @Test
   void singleValueDataSet() {
      DefaultDataSet data = singleCol(7.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      // single value: min == max == 7.0
      assertEquals(7.0, result[0]);
      assertEquals(7.0, result[1]);
   }

   // ---- Empty dataset ----

   @Test
   void emptyDataSetReturnsZeroRange() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{{"value"}});
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(0.0, result[0]);
      assertEquals(0.0, result[1]);
   }

   // ---- Null value filtering ----

   @Test
   void nullValuesAreSkipped() {
      DefaultDataSet data = singleCol(null, 3.0, null, 8.0, null);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(3.0, result[0]);
      assertEquals(8.0, result[1]);
   }

   @Test
   void allNullValuesReturnZeroRange() {
      DefaultDataSet data = singleCol(null, null, null);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(0.0, result[0]);
      assertEquals(0.0, result[1]);
   }

   @Test
   void doubleNaNValuesAreSkipped() {
      // Double.NaN is filtered by val.equals(Double.NaN) check
      DefaultDataSet data = singleCol(Double.NaN, 2.0, 9.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(2.0, result[0]);
      assertEquals(9.0, result[1]);
   }

   // ---- Selector predicate ----

   @Test
   void selectorAcceptsSubsetOfRows() {
      // Rows: 10, 20, 30, 40, 50 — selector accepts only even indices (0, 2, 4)
      DefaultDataSet data = singleCol(10.0, 20.0, 30.0, 40.0, 50.0);
      LinearRange range = new LinearRange();
      String[] cols = new String[]{"value"};
      GraphtDataSelector selector = (ds, row, c) -> row % 2 == 0; // rows 0, 2, 4
      double[] result = range.calculate(data, cols, selector);
      // accepted values: 10.0 (row 0), 30.0 (row 2), 50.0 (row 4)
      assertEquals(10.0, result[0]);
      assertEquals(50.0, result[1]);
   }

   @Test
   void selectorRejectsAllRowsReturnsZeroRange() {
      DefaultDataSet data = singleCol(1.0, 2.0, 3.0);
      LinearRange range = new LinearRange();
      String[] cols = new String[]{"value"};
      GraphtDataSelector selector = (ds, row, c) -> false; // reject all
      double[] result = range.calculate(data, cols, selector);
      assertEquals(0.0, result[0]);
      assertEquals(0.0, result[1]);
   }

   @Test
   void nullSelectorAcceptsAllRows() {
      DefaultDataSet data = singleCol(5.0, 15.0, 25.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(5.0, result[0]);
      assertEquals(25.0, result[1]);
   }

   // ---- Multi-column handling ----

   @Test
   void multipleColumnsMinMaxAcrossAllColumns() {
      DefaultDataSet data = twoCol(
         new Double[]{1.0, 4.0, 7.0},
         new Double[]{-3.0, 10.0, 2.0}
      );
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"a", "b"}, null);
      // Global min = -3.0, global max = 10.0
      assertEquals(-3.0, result[0]);
      assertEquals(10.0, result[1]);
   }

   @Test
   void multipleColumnsWithNullsFiltered() {
      DefaultDataSet data = twoCol(
         new Double[]{null, 5.0},
         new Double[]{2.0, null}
      );
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"a", "b"}, null);
      assertEquals(2.0, result[0]);
      assertEquals(5.0, result[1]);
   }

   // ---- Return value structure ----

   @Test
   void returnsArrayOfLengthTwo() {
      DefaultDataSet data = singleCol(1.0, 2.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertEquals(2, result.length);
   }

   @Test
   void minIsAlwaysLessThanOrEqualToMax() {
      DefaultDataSet data = singleCol(100.0, 5.0, 50.0);
      LinearRange range = new LinearRange();
      double[] result = range.calculate(data, new String[]{"value"}, null);
      assertTrue(result[0] <= result[1]);
   }
}
