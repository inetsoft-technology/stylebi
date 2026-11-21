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
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the calc row machinery of {@link AbstractDataSet}: appended rows, the pre/post
 * column phases, removal, and the interaction with sorting and sub data sets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractDataSetCalcRowTest {
   private static class RowA extends AddRows {
      RowA() {
         super(false, new Object[] { "a1", 1.0 });
      }
   }

   private static class RowA2 extends RowA {
   }

   private static class RowB extends AddRows {
      RowB() {
         super(false, new Object[] { "b1", 2.0 });
      }
   }

   private static DefaultDataSet withPostRow() {
      DefaultDataSet k5 = k5();
      k5.addCalcRow(new AddRows(false, new Object[] { "z", 9.0 }));
      return k5;
   }

   @Test
   @DisplayName("a calc row is appended after prepareCalc and counted as a calc row")
   void calcRowIsAppended() {
      DefaultDataSet k5 = withPostRow();
      k5.prepareCalc(null, null, true);

      assertEquals(6, k5.getRowCount());
      assertEquals(6, k5.getRowCountUnprojected());
      assertEquals(1, k5.getCalcRowCount());
      assertEquals("z", k5.getData(0, 5));
      assertEquals(9.0, k5.getData(1, 5));
   }

   @Test
   @DisplayName("before prepareCalc the calc row is not part of the data")
   void calcRowNotPresentBeforePrepare() {
      DefaultDataSet k5 = withPostRow();

      assertEquals(5, k5.getRowCount());
      assertEquals(0, k5.getCalcRowCount());
   }

   @Test
   @DisplayName("calc rows are appended in registration order")
   void calcRowsKeepRegistrationOrder() {
      DefaultDataSet k5 = k5();
      k5.addCalcRow(new AddRows(false, new Object[] { "a1", 1.0 }));
      k5.addCalcRow(new AddRows(false, new Object[] { "b1", 2.0 }));
      k5.prepareCalc(null, null, true);

      assertEquals(7, k5.getRowCount());
      assertEquals("a1", k5.getData("k", 5));
      assertEquals("b1", k5.getData("k", 6));
   }

   @Test
   @DisplayName("the same calc row added twice is kept once")
   void sameCalcRowIsAddedOnce() {
      DefaultDataSet k5 = k5();
      AddRows row = new AddRows(false, new Object[] { "z", 9.0 });
      k5.addCalcRow(row);
      k5.addCalcRow(row);

      assertEquals(1, k5.getCalcRows().size());
   }

   @Test
   @DisplayName("removeCalcRows(Class) removes the type and its subclasses and keeps other rows")
   void removeCalcRowsByTypeRemovesSubclasses() {
      DefaultDataSet k5 = k5();
      RowB b = new RowB();
      k5.addCalcRow(new RowA());
      k5.addCalcRow(new RowA2());
      k5.addCalcRow(b);

      k5.removeCalcRows(RowA.class);

      assertEquals(1, k5.getCalcRows().size());
      assertSame(b, k5.getCalcRows().get(0));
   }

   @Test
   @DisplayName("removeCalcRows then removeCalcRowValues drops definitions and computed rows")
   void removeCalcRowsAndValuesDropRows() {
      DefaultDataSet k5 = withPostRow();
      k5.prepareCalc(null, null, true);

      k5.removeCalcRows();
      k5.removeCalcRowValues();

      assertTrue(k5.getCalcRows().isEmpty());
      assertEquals(5, k5.getRowCount());
   }

   @Test
   @DisplayName("pinned: removeCalcRows alone keeps the computed row until removeCalcRowValues")
   void removeCalcRowsAloneKeepsComputedRow() {
      DefaultDataSet k5 = withPostRow();
      k5.prepareCalc(null, null, true);

      k5.removeCalcRows();

      assertTrue(k5.getCalcRows().isEmpty());
      assertEquals(6, k5.getRowCount());
   }

   @Test
   @DisplayName("removeCalcRowValues removes the rows and keeps the column values")
   void removeCalcRowValuesKeepsColumnValues() {
      DefaultDataSet k5 = withPostRow();
      k5.addCalcColumn(new RunningSum("rt", "v"));
      k5.prepareCalc(null, null, true);

      k5.removeCalcRowValues();

      assertEquals(5, k5.getRowCount());
      assertEquals(Arrays.asList(2.0, 3.0, 6.0, 11.0, 15.0), column(k5, "rt"));
   }

   @Test
   @DisplayName("removeCalcColValues removes the column values and keeps the rows")
   void removeCalcColValuesKeepsRows() {
      DefaultDataSet k5 = withPostRow();
      k5.addCalcColumn(new RunningSum("rt", "v"));
      k5.prepareCalc(null, null, true);

      k5.removeCalcColValues();

      assertEquals(6, k5.getRowCount());
      assertEquals(-1, k5.indexOfHeader("rt"));
      assertNull(k5.getData(2, 0));
   }

   @Test
   @DisplayName("a pre-column calc row is calculated for by calc columns, including the last flag")
   void preColumnRowIsSeenByCalcColumn() {
      DefaultDataSet k5 = k5();
      Recorder rec = new Recorder("rec");
      k5.addCalcRow(new AddRows(true, new Object[] { "p", 5.0 }));
      k5.addCalcColumn(rec);
      k5.prepareCalc(null, null, true);

      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), rec.rows());
      assertTrue(rec.calls.get(5).last);
   }

   @Test
   @DisplayName("a post-column calc row is not seen by calc columns but is counted afterwards")
   void postColumnRowIsNotSeenByCalcColumn() {
      DefaultDataSet k5 = withPostRow();
      Recorder rec = new Recorder("rec");
      k5.addCalcColumn(rec);
      k5.prepareCalc(null, null, true);

      assertEquals(Arrays.asList(0, 1, 2, 3, 4), rec.rows());
      assertTrue(rec.calls.get(4).last);
      assertEquals(6, k5.getRowCount());
   }

   @Test
   @DisplayName("pinned: a calc column cell on the pre row is computed, on the post row it is null")
   void calcCellOfCalcRows() {
      DefaultDataSet pre = k5();
      pre.addCalcRow(new AddRows(true, new Object[] { "p", 5.0 }));
      pre.addCalcColumn(new Recorder("rec"));
      pre.prepareCalc(null, null, true);

      assertEquals(5.0, pre.getData("rec", 5));

      DefaultDataSet post = withPostRow();
      post.addCalcColumn(new Recorder("rec"));
      post.prepareCalc(null, null, true);

      assertNull(post.getData("rec", 5));
   }

   @Test
   @DisplayName("a calc row sees the sorted data when the inner dimension has a comparator")
   void calcRowSeesSortedData() {
      DefaultDataSet sorted = k5();
      sorted.setComparator("k", nat());
      sorted.addCalcRow(new CopyLastRow());
      sorted.prepareCalc("k", null, true);

      assertEquals("c", sorted.getData("k", 5));
      assertEquals(3.0, sorted.getData("v", 5));

      DefaultDataSet plain = k5();
      plain.setComparator("k", nat());
      plain.addCalcRow(new CopyLastRow());
      plain.prepareCalc(null, null, true);

      assertEquals("b", plain.getData("k", 5));
      assertEquals(4.0, plain.getData("v", 5));
   }

   @Test
   @DisplayName("a calc row shorter than the columns reads null in the missing cells")
   void shortCalcRowReadsNull() {
      DefaultDataSet k5 = k5();
      k5.addCalcRow(new AddRows(false, new Object[] { "z" }));
      k5.prepareCalc(null, null, true);

      assertEquals("z", k5.getData(0, 5));
      assertDoesNotThrow(() -> assertNull(k5.getData(1, 5)));
   }

   @Test
   @DisplayName("a SortedDataSet puts the calc row in the sorted order")
   void sortedDataSetOrdersCalcRows() {
      DefaultDataSet base = withPostRow();
      base.setComparator("k", nat());
      base.prepareCalc(null, null, true);
      SortedDataSet sorted = new SortedDataSet(base, "k");

      assertEquals(6, sorted.getRowCount());
      assertEquals(5, sorted.getBaseRow(5));
      assertEquals("z", sorted.getData("k", 5));
   }

   @Test
   @DisplayName("a clone has its own calc row list and keeps the calculated rows")
   void cloneCopiesCalcRows() {
      DefaultDataSet k5 = withPostRow();
      k5.prepareCalc(null, null, true);
      DefaultDataSet copy = (DefaultDataSet) k5.clone();

      assertEquals(6, copy.getRowCount());

      copy.addCalcRow(new AddRows(false, new Object[] { "y", 8.0 }));

      assertEquals(1, k5.getCalcRows().size());
      assertEquals(2, copy.getCalcRows().size());
   }

   @Test
   @DisplayName("adding a calc row clears the calculated rows until the next prepareCalc")
   void addCalcRowResetsValues() {
      DefaultDataSet k5 = withPostRow();
      k5.prepareCalc(null, null, true);

      assertEquals(6, k5.getRowCount());

      k5.addCalcRow(new AddRows(false, new Object[] { "y", 8.0 }));

      assertEquals(5, k5.getRowCount());

      k5.prepareCalc(null, null, true);

      assertEquals(7, k5.getRowCount());
   }

   @Test
   @DisplayName("calc rows are added once over repeated group calls")
   void calcRowsAddedOnceOverRepeatedGroupCalls() {
      DefaultDataSet k5 = k5();
      AddRows pre = new AddRows(true, new Object[] { "p", 5.0 });
      AddRows post = new AddRows(false, new Object[] { "q", 6.0 });
      k5.addCalcRow(pre);
      k5.addCalcRow(post);

      k5.prepareCalc("k", new int[] { 0, 1 }, true);
      k5.prepareCalc("k", new int[] { 2, 3, 4 }, true);

      assertEquals(7, k5.getRowCount());
      assertEquals(1, pre.calculated);
      assertEquals(1, post.calculated);
   }

   @Test
   @DisplayName("a SubDataSet condition also matches the calc row")
   void subDataSetMatchesCalcRow() {
      DefaultDataSet base = k5();
      base.addCalcRow(new AddRows(false, new Object[] { "b", 7.0 }));
      base.prepareCalc(null, null, true);

      SubDataSet sub = new SubDataSet(base, Collections.singletonMap("k", "b"));

      assertEquals(3, sub.getRowCount());
      assertEquals(0, sub.getBaseRow(0));
      assertEquals(4, sub.getBaseRow(1));
      assertEquals(5, sub.getBaseRow(2));
   }

   @Test
   @DisplayName("the waterfall total row: Total header, total of the measure in the sum column")
   void sumDataSetTotalRow() {
      SumDataSet sum = new SumDataSet(k5(), new String[] { "v" }, "k");
      sum.prepareCalc(null, null, true);

      assertEquals(6, sum.getRowCount());
      assertEquals("Total", sum.getData("k", 5));
      assertNull(sum.getData("v", 5));
      assertEquals(15.0, sum.getData(SumDataSet.SUM_HEADER_PREFIX + "v", 5));
   }

   @Test
   @DisplayName("the waterfall total row copies non-measure columns from the last row")
   void sumDataSetCopiesNonMeasureFromLastRow() {
      DefaultDataSet base = new DefaultDataSet(new Object[][] {
         { "k", "g", "v" },
         { "a", "x", 1.0 },
         { "b", "y", 2.0 }
      });
      SumDataSet sum = new SumDataSet(base, new String[] { "v" }, "k");
      sum.prepareCalc(null, null, true);

      assertEquals(3, sum.getRowCount());
      assertEquals("Total", sum.getData("k", 2));
      assertEquals("y", sum.getData("g", 2));
      assertNull(sum.getData("v", 2));
      assertEquals(3.0, sum.getData(SumDataSet.SUM_HEADER_PREFIX + "v", 2));
   }
}
