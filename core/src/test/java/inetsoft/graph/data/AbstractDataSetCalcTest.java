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
package inetsoft.graph.data;

import inetsoft.graph.data.CalcTestFixtures.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the calc column machinery of {@link AbstractDataSet}: prepareCalc, the order the
 * calc columns see the rows, the first/last flags, INVALID results, post calcs.
 */
@Tag("core")
class AbstractDataSetCalcTest {
   private static DefaultDataSet sortedByT() {
      DefaultDataSet g6 = g6();
      g6.setComparator("t", nat());
      return g6;
   }

   private static List<Object> doubles(double... vals) {
      List<Object> list = new ArrayList<>();

      for(double val : vals) {
         list.add(val);
      }

      return list;
   }

   @Test
   @DisplayName("running calc without a dimension follows the base row order")
   void calcWithoutDimensionFollowsBaseOrder() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc(null, null, true);

      assertEquals(doubles(1, 3, 7, 15, 31, 63), column(g6, "rt"));
   }

   @Test
   @DisplayName("calc with a sorted inner dimension sees the sorted order, values stored at the base row")
   void calcFollowsInnerDimensionSortOrder() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc("t", null, true);

      assertEquals(doubles(31, 2, 22, 30, 18, 63), column(g6, "rt"));
   }

   @Test
   @DisplayName("calc over sorted data equals a running sum over an explicit SortedDataSet")
   void calcMatchesExplicitSortedDataSet() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc("t", null, true);

      SortedDataSet sorted = new SortedDataSet(g6, "t");
      double sum = 0;

      for(int i = 0; i < sorted.getRowCount(); i++) {
         int base = sorted.getBaseRow(i);
         sum += (Double) g6.getData("v", base);
         assertEquals(sum, (Double) g6.getData("rt", base), 1e-9, "sorted position " + i);
      }
   }

   @Test
   @DisplayName("a dimension without comparator keeps the original row order for the calc")
   void dimensionWithoutComparatorKeepsOriginalOrder() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc("t", null, true);

      assertEquals(doubles(1, 3, 7, 15, 31, 63), column(g6, "rt"));
   }

   @Test
   @DisplayName("first and last flags with all rows: first only at row 0, last only at the final row")
   void firstAndLastFlagsForAllRows() {
      DefaultDataSet g6 = g6();
      Recorder rec = new Recorder("rec");
      g6.addCalcColumn(rec);
      g6.prepareCalc(null, null, true);

      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), rec.rows());

      for(Call call : rec.calls) {
         assertEquals(call.row == 0, call.first, "first at " + call.row);
         assertEquals(call.row == 5, call.last, "last at " + call.row);
      }
   }

   @Test
   @DisplayName("rows subset: only the listed rows are calculated, first and last follow the subset")
   void rowsSubsetIsCalculatedOnly() {
      DefaultDataSet g6 = g6();
      Recorder rec = new Recorder("rec");
      g6.addCalcColumn(rec);
      g6.prepareCalc(null, new int[] { 1, 2, 3 }, true);

      assertEquals(Arrays.asList(1, 2, 3), rec.rows());

      for(Call call : rec.calls) {
         assertEquals(call.row == 1, call.first, "first at " + call.row);
         assertEquals(call.row == 3, call.last, "last at " + call.row);
      }

      List<Object> cells = column(g6, "rec");
      assertNull(cells.get(0));
      assertEquals(1.0, cells.get(1));
      assertEquals(2.0, cells.get(2));
      assertEquals(3.0, cells.get(3));
      assertNull(cells.get(4));
      assertNull(cells.get(5));
   }

   @Test
   @DisplayName("two calls with disjoint row groups restart at their own first row and keep earlier values")
   void disjointRowGroupsRestartAndKeepEarlierValues() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc("t", new int[] { 0, 1, 2 }, true);
      g6.prepareCalc("t", new int[] { 3, 4, 5 }, true);

      assertEquals(doubles(9, 2, 22, 8, 18, 41), column(g6, "rt"));
   }

   @Test
   @DisplayName("calcMeasures false skips measure calc columns but computes the others")
   void calcMeasuresFalseSkipsMeasureColumns() {
      DefaultDataSet g6 = g6();
      RunningSum rt = new RunningSum("rt", "v");
      g6.addCalcColumn(rt);
      g6.addCalcColumn(new Fixed("txt", "x", false));
      g6.prepareCalc(null, null, false);

      assertEquals(0, rt.calls);

      for(int r = 0; r < g6.getRowCount(); r++) {
         assertNull(g6.getData("rt", r));
         assertEquals("x", g6.getData("txt", r));
      }
   }

   @Test
   @DisplayName("INVALID results read as null, a plain 0.0 result stays 0.0")
   void invalidResultBecomesNull() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new InvalidFrom("rt", "v", 8));
      g6.addCalcColumn(new Fixed("zero", 0.0, true));
      g6.prepareCalc(null, null, true);

      List<Object> rt = column(g6, "rt");
      assertEquals(1.0, rt.get(0));
      assertEquals(2.0, rt.get(1));
      assertEquals(4.0, rt.get(2));
      assertNull(rt.get(3));
      assertNull(rt.get(4));
      assertNull(rt.get(5));
      assertEquals(Collections.nCopies(6, 0.0), column(g6, "zero"));
   }

   @Test
   @DisplayName("containsValue is false for a calc column with only INVALID results")
   void containsValueFalseWhenAllInvalid() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new Fixed("rt", CalcColumn.INVALID, true));
      g6.prepareCalc(null, null, true);

      assertFalse(g6.containsValue("rt"));
      assertTrue(g6.containsValue("v"));
      assertTrue(g6.containsValue("rt", "v"));
   }

   @Test
   @DisplayName("containsValue treats null results and partly valid columns as valid")
   void containsValueTrueForNullAndMixedResults() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new Fixed("nulls", null, true));
      g6.addCalcColumn(new InvalidFrom("mixed", "v", 2));
      g6.prepareCalc(null, null, true);

      assertTrue(g6.containsValue("nulls"));
      assertTrue(g6.containsValue("mixed"));

      assertTrue(g6().containsValue("v"));
   }

   @Test
   @DisplayName("post calc reads the value of another calc column")
   void postCalcReadsOtherCalcColumn() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.addCalcColumn(new Derived("dbl", "rt", 2, 0));
      g6.prepareCalc("t", null, true);

      assertEquals(doubles(62, 4, 44, 60, 36, 126), column(g6, "dbl"));
   }

   @Test
   @DisplayName("a post calc registered before its source column gives the same result as after it")
   void postCalcRegisteredBeforeItsSource() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new Derived("dbl", "rt", 2, 0));
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.prepareCalc("t", null, true);

      assertEquals(doubles(62, 4, 44, 60, 36, 126), column(g6, "dbl"));
   }

   @Test
   @DisplayName("post calc with a rows subset is calculated for those rows only")
   void postCalcWithRowsSubset() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.addCalcColumn(new Derived("dbl", "rt", 2, 0));
      g6.prepareCalc(null, new int[] { 0, 1 }, true);

      List<Object> dbl = column(g6, "dbl");
      assertEquals(2.0, dbl.get(0));
      assertEquals(6.0, dbl.get(1));

      for(int r = 2; r < 6; r++) {
         assertNull(dbl.get(r), "row " + r);
      }
   }

   @Test
   @DisplayName("complete() is called once per run on regular and post calc columns")
   void completeCalledOncePerRun() {
      DefaultDataSet g6 = g6();
      Recorder rec = new Recorder("rec");
      Derived dep = new Derived("dep", "rec", 2, 0);
      g6.addCalcColumn(rec);
      g6.addCalcColumn(dep);
      g6.prepareCalc(null, null, true);

      assertEquals(1, rec.completes);
      assertEquals(1, dep.completes);

      g6.prepareCalc(null, new int[] { 0, 1 }, true);

      assertEquals(2, rec.completes);
      assertEquals(2, dep.completes);
   }

   @Test
   @DisplayName("a dimension that is a calc column is not sorted: the calc sees the data set itself")
   void calcDimensionIsNotSorted() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      Recorder rec = new Recorder("rec");
      g6.addCalcColumn(rec);

      assertDoesNotThrow(() -> g6.prepareCalc("rt", null, true));

      assertEquals(doubles(1, 3, 7, 15, 31, 63), column(g6, "rt"));

      for(Call call : rec.calls) {
         assertSame(g6, call.data);
      }
   }

   @Test
   @DisplayName("a second prepareCalc keeps the values, removeCalcValues makes the next one recompute")
   void secondPrepareDoesNotRecompute() {
      DefaultDataSet g6 = g6();
      Recorder rec = new Recorder("rec");
      g6.addCalcColumn(rec);

      g6.prepareCalc(null, null, true);
      g6.prepareCalc(null, null, true);
      assertEquals(6, rec.calls.size());

      g6.removeCalcValues();
      g6.prepareCalc(null, null, true);
      assertEquals(12, rec.calls.size());
   }

   @Test
   @DisplayName("with a dimension the calc gets a SortedDataSet over the base, without one the base itself")
   void calcReceivesSortedFilterOrBase() {
      DefaultDataSet byDim = sortedByT();
      Recorder recDim = new Recorder("rec");
      byDim.addCalcColumn(recDim);
      byDim.prepareCalc("t", null, true);

      for(Call call : recDim.calls) {
         assertInstanceOf(SortedDataSet.class, call.data);
         assertSame(byDim, ((SortedDataSet) call.data).getDataSet());
      }

      DefaultDataSet noDim = sortedByT();
      Recorder recNone = new Recorder("rec");
      noDim.addCalcColumn(recNone);
      noDim.prepareCalc(null, null, true);

      for(Call call : recNone.calls) {
         assertSame(noDim, call.data);
      }
   }

   @Test
   @DisplayName("the row index given to a calc is the sorted index, stored at the base row")
   void calcRowIndexIsSortedIndexStoredAtBaseRow() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new Recorder("rec"));
      g6.prepareCalc("t", null, true);

      assertEquals(doubles(4, 0, 2, 3, 1, 5), column(g6, "rec"));
   }

   @Test
   @DisplayName("two calc columns keep their own cells and their header order and index")
   void twoCalcColumnsKeepOwnCells() {
      DefaultDataSet g6 = sortedByT();
      g6.addCalcColumn(new RunningSum("rt", "v"));
      g6.addCalcColumn(new Recorder("rec"));
      g6.prepareCalc("t", null, true);

      assertEquals(5, g6.getColCount());
      assertEquals("rt", g6.getHeader(3));
      assertEquals("rec", g6.getHeader(4));
      assertEquals(3, g6.indexOfHeader("rt"));
      assertEquals(4, g6.indexOfHeader("rec"));
      assertEquals(doubles(31, 2, 22, 30, 18, 63), column(g6, "rt"));
      assertEquals(doubles(4, 0, 2, 3, 1, 5), column(g6, "rec"));
   }

   @Test
   @DisplayName("a disposed data set calculates nothing")
   void disposedDataSetCalculatesNothing() {
      DefaultDataSet g6 = g6();
      Recorder rec = new Recorder("rec");
      g6.addCalcColumn(rec);
      g6.dispose();
      g6.prepareCalc(null, null, true);

      assertTrue(rec.calls.isEmpty());
      assertEquals(Collections.nCopies(6, null), column(g6, "rec"));
   }

   @Test
   @DisplayName("a sort over a prepared base reads the base calc values through the mapping")
   void sortedFilterSharesBaseCalcValues() {
      DefaultDataSet base = sortedByT();
      base.addCalcColumn(new RunningSum("rt", "v"));
      base.prepareCalc("t", null, true);
      SortedDataSet sorted = new SortedDataSet(base, "t");

      assertEquals(4, sorted.getColCount());

      for(int i = 0; i < sorted.getRowCount(); i++) {
         assertEquals(base.getData("rt", sorted.getBaseRow(i)), sorted.getData("rt", i),
                      "sorted row " + i);
      }
   }

   @Test
   @DisplayName("NaN results are valid values, like a 0.0 control column")
   void nanResultIsValidValue() {
      DefaultDataSet g6 = g6();
      g6.addCalcColumn(new Fixed("nan", Double.NaN, true));
      g6.addCalcColumn(new Fixed("zero", 0.0, true));
      g6.prepareCalc(null, null, true);

      assertEquals(Collections.nCopies(6, Double.NaN), column(g6, "nan"));
      assertEquals(Collections.nCopies(6, 0.0), column(g6, "zero"));
      assertTrue(g6.containsValue("nan"));
   }

   @Test
   @DisplayName("prepareCalc without calc columns or calc rows changes nothing")
   void prepareCalcWithoutCalcsChangesNothing() {
      DefaultDataSet k5 = k5();

      assertDoesNotThrow(() -> k5.prepareCalc("k", null, true));

      assertEquals(2, k5.getColCount());
      assertEquals(5, k5.getRowCount());
   }

   @Test
   @DisplayName("a pre-column calc row is included in the rows a calc column is run over")
   void calcColumnRunsOverPreColumnCalcRow() {
      DefaultDataSet k5 = k5();
      Recorder rec = new Recorder("rec");
      k5.addCalcRow(new AddRows(true, new Object[] { "p", 5.0 }));
      k5.addCalcColumn(rec);
      k5.prepareCalc(null, null, true);

      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), rec.rows());
      assertTrue(rec.calls.get(5).last);
      assertFalse(rec.calls.get(4).last);
   }
}
