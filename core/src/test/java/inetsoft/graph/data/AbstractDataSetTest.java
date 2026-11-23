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
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import org.junit.jupiter.api.*;

import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the base API of {@link AbstractDataSet} through {@link DefaultDataSet}: counts,
 * headers, types, calc column management, clone and dispose.
 */
@Tag("core")
class AbstractDataSetTest {
   private static DefaultDataSet prepared(CalcColumn... cols) {
      DefaultDataSet k5 = k5();

      for(CalcColumn col : cols) {
         k5.addCalcColumn(col);
      }

      k5.prepareCalc(null, null, true);
      return k5;
   }

   private static DefaultDataSet single(Object... column) {
      Object[][] rows = new Object[column.length + 1][];
      rows[0] = new Object[] { "c" };

      for(int i = 0; i < column.length; i++) {
         rows[i + 1] = new Object[] { column[i] };
      }

      return new DefaultDataSet(rows);
   }

   @Test
   @DisplayName("row count, column count and headers")
   void countsAndHeaders() {
      DefaultDataSet k5 = k5();

      assertEquals(5, k5.getRowCount());
      assertEquals(2, k5.getColCount());
      assertEquals("k", k5.getHeader(0));
      assertEquals("v", k5.getHeader(1));
   }

   @Test
   @DisplayName("getData by name equals getData by index, an unknown name has index -1 and null data")
   void getDataByNameEqualsByIndex() {
      DefaultDataSet k5 = k5();

      assertEquals(0, k5.indexOfHeader("k"));
      assertEquals(1, k5.indexOfHeader("v"));
      assertEquals(-1, k5.indexOfHeader("zz"));

      for(int r = 0; r < k5.getRowCount(); r++) {
         assertEquals(k5.getData(0, r), k5.getData("k", r));
         assertEquals(k5.getData(1, r), k5.getData("v", r));
      }

      assertNull(k5.getData("zz", 0));
   }

   @Test
   @DisplayName("column type is the class of the first non-null value, String when all are null")
   void columnTypeFromFirstNonNullValue() {
      assertEquals(String.class, k5().getType("k"));
      assertEquals(Double.class, k5().getType("v"));
      assertEquals(Integer.class, single(null, 3, 4).getType("c"));
      assertEquals(String.class, single(null, null).getType("c"));
      assertThrows(ColumnNotFoundException.class, () -> k5().getType("zz"));
   }

   @Test
   @DisplayName("numeric column is a measure, string column is not, unknown column throws")
   void measureOfBaseColumns() {
      DefaultDataSet k5 = k5();

      assertTrue(k5.isMeasure("v"));
      assertFalse(k5.isMeasure("k"));
      assertThrows(ColumnNotFoundException.class, () -> k5.isMeasure("zz"));
   }

   @Test
   @DisplayName("calc column before prepareCalc is listed by header but has no index, values or rows")
   void calcColumnBeforePrepare() {
      DefaultDataSet k5 = k5();
      k5.addCalcColumn(new RunningSum("rt", "v"));

      assertEquals(2, k5.getColCount());
      assertEquals("rt", k5.getHeader(2));
      assertEquals(-1, k5.indexOfHeader("rt"));
      assertEquals(2, k5.indexOfHeader("rt", true));
      assertEquals(Double.class, k5.getType("rt"));

      for(int r = 0; r < 5; r++) {
         assertNull(k5.getData(2, r));
      }
   }

   @Test
   @DisplayName("calc column after prepareCalc is a column of the data set")
   void calcColumnAfterPrepare() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));

      assertEquals(3, k5.getColCount());
      assertEquals(2, k5.indexOfHeader("rt"));
      assertEquals("rt", k5.getHeader(2));
      assertTrue(k5.isMeasure("rt"));
      assertEquals(Double.class, k5.getType("rt"));
   }

   @Test
   @DisplayName("getHeader and indexOfHeader are inverse for every column, the last one is the last calc column")
   void headerIndexRoundTrip() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"), new Recorder("rec"));

      assertEquals(4, k5.getColCount());

      for(int c = 0; c < k5.getColCount(); c++) {
         assertEquals(c, k5.indexOfHeader(k5.getHeader(c)), "column " + c);
      }

      assertEquals("rec", k5.getHeader(k5.getColCount() - 1));
   }

   @Test
   @DisplayName("a calc column with the header of an existing one replaces it")
   void sameHeaderReplacesCalcColumn() {
      DefaultDataSet k5 = k5();
      k5.addCalcColumn(new RunningSum("rt", "v"));
      Recorder replacement = new Recorder("rt");
      k5.addCalcColumn(replacement);

      assertEquals(1, k5.getCalcColumns().size());
      assertSame(replacement, k5.getCalcColumns().get(0));
   }

   @Test
   @DisplayName("after replacing a calc column and removeCalcValues, prepareCalc uses the new column")
   void replacedCalcColumnIsUsedAfterRemoveCalcValues() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));
      k5.addCalcColumn(new Fixed("rt", 7.0, true));
      k5.removeCalcValues();
      k5.prepareCalc(null, null, true);

      assertEquals(Collections.nCopies(5, 7.0), column(k5, "rt"));
   }

   @Test
   @DisplayName("pinned: replacing a calc column without removeCalcValues keeps the old values")
   void replacedCalcColumnKeepsOldValuesWithoutRemoveCalcValues() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));
      k5.addCalcColumn(new Fixed("rt", 7.0, true));
      k5.prepareCalc(null, null, true);

      assertEquals(Arrays.asList(2.0, 3.0, 6.0, 11.0, 15.0), column(k5, "rt"));
   }

   @Test
   @DisplayName("adding a calc column with a new header after prepare clears the calc values")
   void newCalcColumnAfterPrepareClearsValues() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));
      k5.addCalcColumn(new Recorder("rec"));

      assertEquals(2, k5.getColCount());

      for(int r = 0; r < 5; r++) {
         assertNull(k5.getData("rt", r));
         assertNull(k5.getData(2, r));
      }

      k5.prepareCalc(null, null, true);

      assertEquals(4, k5.getColCount());
      assertEquals(Arrays.asList(2.0, 3.0, 6.0, 11.0, 15.0), column(k5, "rt"));
   }

   @Test
   @DisplayName("removeCalcColumns drops the columns at once, also from the cached index")
   void removeCalcColumnsDropsColumns() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));

      assertEquals(2, k5.indexOfHeader("rt"));

      k5.removeCalcColumns();

      assertEquals(2, k5.getColCount());
      assertTrue(k5.getCalcColumns().isEmpty());
      assertEquals(-1, k5.indexOfHeader("rt"));
   }

   @Test
   @DisplayName("a filter drops its cached calc column index when the base removes the calc column")
   void filterIndexFollowsBaseCalcColumnRemoval() {
      DefaultDataSet base = prepared(new RunningSum("rt", "v"));
      AbstractDataSetFilter filter = new AbstractDataSetFilter(base) {
      };

      assertEquals(2, filter.indexOfHeader("rt"));

      base.removeCalcColumns();

      assertEquals(-1, filter.indexOfHeader("rt"));
   }

   @Test
   @DisplayName("getCalcColumns returns a copy")
   void calcColumnsListIsACopy() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));

      k5.getCalcColumns().clear();

      assertEquals(1, k5.getCalcColumns().size());
   }

   @Test
   @DisplayName("removeCalcValues clears calc column and calc row values")
   void removeCalcValuesClearsColumnsAndRows() {
      DefaultDataSet k5 = k5();
      k5.addCalcColumn(new RunningSum("rt", "v"));
      k5.addCalcRow(new AddRows(false, new Object[] { "z", 9.0 }));
      k5.prepareCalc(null, null, true);

      assertEquals(6, k5.getRowCount());

      k5.removeCalcValues();

      assertEquals(2, k5.getColCount());
      assertEquals(5, k5.getRowCount());
      assertEquals(-1, k5.indexOfHeader("rt"));

      for(int r = 0; r < 5; r++) {
         assertNull(k5.getData(2, r));
      }
   }

   @Test
   @DisplayName("a clone has its own calc column list but the same calc values")
   void cloneCopiesCalcState() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));
      DefaultDataSet copy = (DefaultDataSet) k5.clone();

      for(int r = 0; r < 5; r++) {
         assertEquals(k5.getData(2, r), copy.getData(2, r));
      }

      copy.addCalcColumn(new Recorder("rec"));

      assertEquals(1, k5.getCalcColumns().size());
      assertEquals(2, copy.getCalcColumns().size());
   }

   @Test
   @DisplayName("removeCalcValues on a clone leaves the original values")
   void cloneRemoveCalcValuesIsIndependent() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));
      DefaultDataSet copy = (DefaultDataSet) k5.clone();

      copy.removeCalcValues();

      assertEquals(Arrays.asList(2.0, 3.0, 6.0, 11.0, 15.0), column(k5, "rt"));
      assertEquals(2, copy.getColCount());
      assertNull(copy.getData(2, 0));
   }

   @Test
   @DisplayName("clone(true), clone(false) and clone() are other instances with the same cells")
   void cloneVariantsKeepCells() {
      DefaultDataSet k5 = k5();
      DataSet deep = k5.clone(false);
      DataSet shallow = k5.clone(true);
      DefaultDataSet copy = (DefaultDataSet) k5.clone();

      assertNotSame(k5, deep);
      assertNotSame(k5, shallow);

      for(DataSet other : new DataSet[] { deep, shallow, copy }) {
         assertEquals(k5.getRowCount(), other.getRowCount());

         for(int r = 0; r < 5; r++) {
            assertEquals(k5.getData("k", r), other.getData("k", r));
            assertEquals(k5.getData("v", r), other.getData("v", r));
         }
      }
   }

   @Test
   @DisplayName("empty inputs: no rows and no columns, header only, unknown name reads null")
   void emptyInputs() {
      DefaultDataSet none = new DefaultDataSet(new Object[0][]);

      assertEquals(0, none.getRowCount());
      assertEquals(0, none.getColCount());

      DefaultDataSet headerOnly = new DefaultDataSet(new Object[][] { { "k", "v" } });

      assertEquals(0, headerOnly.getRowCount());
      assertEquals(2, headerOnly.getColCount());
      assertDoesNotThrow(() -> assertNull(headerOnly.getData("zz", 0)));
   }

   @Test
   @DisplayName("setComparator on an unknown column is ignored")
   void setComparatorUnknownColumnIsIgnored() {
      DefaultDataSet k5 = k5();

      assertDoesNotThrow(() -> k5.setComparator("zz", nat()));
   }

   @Test
   @DisplayName("setOrder gives an explicit value order and ignores an unknown column")
   void setOrderGivesExplicitOrder() {
      DefaultDataSet k5 = k5();
      k5.setOrder("k", new Object[] { "c", "a", "b" });

      List<String> values = new ArrayList<>(Arrays.asList("a", "a", "b", "b", "c"));
      values.sort(k5.getComparator("k"));

      assertEquals(Arrays.asList("c", "a", "a", "b", "b"), values);
      assertDoesNotThrow(() -> k5.setOrder("zz", new Object[] { "x" }));
      assertNull(k5.getComparator("zz"));
   }

   @Test
   @DisplayName("getData by name of a calc column equals getData by index")
   void calcColumnByNameEqualsByIndex() {
      DefaultDataSet k5 = prepared(new RunningSum("rt", "v"));

      for(int r = 0; r < 5; r++) {
         assertEquals(k5.getData(2, r), k5.getData("rt", r));
      }

      assertEquals(Arrays.asList(2.0, 3.0, 6.0, 11.0, 15.0), column(k5, "rt"));
   }

   @Test
   @DisplayName("column count follows every structure change: 2, 3 after prepare, 2 after remove")
   void columnCountFollowsStructureChanges() {
      DefaultDataSet k5 = k5();

      assertEquals(2, k5.getColCount());

      k5.addCalcColumn(new RunningSum("rt", "v"));

      assertEquals(2, k5.getColCount());

      k5.prepareCalc(null, null, true);

      assertEquals(3, k5.getColCount());

      k5.removeCalcColumns();

      assertEquals(2, k5.getColCount());
   }

   @Test
   @DisplayName("dispose marks the data set, a clone of a fresh data set is not disposed")
   void disposeFlag() {
      DefaultDataSet fresh = k5();
      DataSet copy = (DataSet) fresh.clone();
      DefaultDataSet disposed = k5();

      disposed.dispose();

      assertTrue(disposed.isDisposed());
      assertFalse(fresh.isDisposed());
      assertFalse(copy.isDisposed());
   }
}
