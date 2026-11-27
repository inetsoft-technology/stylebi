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

package inetsoft.report.script.formula;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82397: the cached group lookup used for {@code data['col@group:value']} on a detail
 * (non-grouped) table must keep a null group apart from an empty-string group.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CachedRowSelectorTest {
   /**
    * Region/Total = (null, 1.0), ("", 2.0), ("a", 4.0).
    */
   private static XTable nullAndEmptyTable() {
      return new DefaultTableLens(new Object[][] {
         { "Region", "Total" },
         { null, 1.0 },
         { "", 2.0 },
         { "a", 4.0 }
      });
   }

   private static List<Object> cells(String spec, XTable table) throws Exception {
      return new ArrayList<>(new NamedCellRange(spec).getCells(table, false));
   }

   @Test
   void emptyStringLiteralSelectsOnlyTheEmptyStringRows() throws Exception {
      assertEquals(List.of(2.0), cells("Total@Region:''", nullAndEmptyTable()));
   }

   @Test
   void nullExpressionSelectsOnlyTheNullRows() throws Exception {
      assertEquals(List.of(1.0), cells("Total@Region:=null", nullAndEmptyTable()));
   }

   @Test
   void otherGroupValuesAreUnchanged() throws Exception {
      assertEquals(List.of(4.0), cells("Total@Region:a", nullAndEmptyTable()));
   }

   @Test
   void nullAndEmptyGroupKeysSelectDifferentRows() {
      XTable table = nullAndEmptyTable();

      assertEquals(Set.of(1), matchingRows(table, null));
      assertEquals(Set.of(2), matchingRows(table, ""));
      assertEquals(Set.of(3), matchingRows(table, "a"));
   }

   /**
    * Select rows the way a {@code $Region} group reference does: a GroupSpec holding the
    * unwrapped cell value, which is null for the null group.
    */
   private static Set<Integer> matchingRows(XTable table, Object groupValue) {
      Map<String, NamedCellRange.GroupSpec> specs = new HashMap<>();
      specs.put("Region", new NamedCellRange.GroupSpec(groupValue));
      CachedRowSelector selector = CachedRowSelector.getSelector(table, specs);
      Set<Integer> rows = new TreeSet<>();

      try {
         selector.prepare(specs);

         for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
            if(selector.match(table, r, 1) == RangeProcessor.YES) {
               rows.add(r);
            }
         }
      }
      finally {
         selector.endProcess();
      }

      return rows;
   }

   // ---- Bug #82482: the rows of a group are visited from the exact row list of the group, not
   // by walking the interval between its first and last row. The walk is kept here as the
   // reference: it is what getTableCells and rowList did before.

   /** Region/Cat/Idx, Idx is the row index so a value tells the row it came from. */
   private static DefaultTableLens table(int headers, int rows, int groups, boolean mixed) {
      Object[][] data = new Object[rows + headers][];

      for(int h = 0; h < headers; h++) {
         data[h] = new Object[] { "Region", "Cat", "Idx" };
      }

      for(int i = 0; i < rows; i++) {
         int g = mixed ? i % groups : (int) ((long) i * groups / rows);
         data[headers + i] = new Object[] { "R" + g, "C" + (i % 3), (double) (headers + i) };
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(headers);
      return lens;
   }

   /** The old behavior: the bounding interval of the group walked with match. */
   private static List<Point> walk(XTable table, Map<String, NamedCellRange.GroupSpec> specs,
                                   String colexpr, boolean expression, String cond)
   {
      CachedRowSelector selector = CachedRowSelector.getSelector(table, specs);
      List<Point> out = new ArrayList<>();

      try {
         selector.prepare(specs);
         new TableRangeProcessor(table, null).selectCells(
            out, colexpr, expression,
            Math.max(table.getHeaderRowCount(), selector.getStartRow()),
            selector.getEndRow() + 1, 1, selector, cond, true);
      }
      finally {
         selector.endProcess();
      }

      return out;
   }

   /** The new behavior: the row list of the group, null group falling back to the walk. */
   private static List<Point> listed(XTable table, Map<String, NamedCellRange.GroupSpec> specs,
                                     String colexpr, boolean expression, String cond)
   {
      CachedRowSelector selector = CachedRowSelector.getSelector(table, specs);
      List<Point> out = new ArrayList<>();

      try {
         selector.prepare(specs);
         int[] rows = selector.getGroupRows();
         TableRangeProcessor proc = new TableRangeProcessor(table, null);

         if(rows == null) {
            proc.selectCells(out, colexpr, expression, table.getHeaderRowCount(),
                             selector.getEndRow() + 1, 1, selector, cond, true);
         }
         else {
            proc.selectCells(out, colexpr, expression, rows, table.getHeaderRowCount(),
                             selector, cond, true);
         }
      }
      finally {
         selector.endProcess();
      }

      return out;
   }

   private static Map<String, NamedCellRange.GroupSpec> specs(Object... keyValues) {
      Map<String, NamedCellRange.GroupSpec> specs = new HashMap<>();

      for(int i = 0; i < keyValues.length; i += 2) {
         specs.put((String) keyValues[i], new NamedCellRange.GroupSpec(keyValues[i + 1]));
      }

      return specs;
   }

   private static List<Integer> rowsOf(List<Point> points) {
      List<Integer> rows = new ArrayList<>();
      points.forEach(p -> rows.add(p.y));
      return rows;
   }

   private static List<Integer> expected(XTable table, int col, Object value) {
      List<Integer> rows = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         if(Objects.equals(table.getObject(r, col), value)) {
            rows.add(r);
         }
      }

      return rows;
   }

   private static void assertSame(XTable table, Map<String, NamedCellRange.GroupSpec> specs,
                                  List<Integer> expectedRows)
   {
      List<Point> old = walk(table, specs, "Idx", false, null);
      List<Point> now = listed(table, specs, "Idx", false, null);
      assertEquals(old, now, "same cells as the interval walk");
      assertEquals(expectedRows, rowsOf(now), "the rows of the group, ascending");
   }

   @Test
   void sortedGroupsSelectTheSameRowsAsTheIntervalWalk() {
      DefaultTableLens t = table(1, 60, 6, false);

      for(int g = 0; g < 6; g++) {
         assertSame(t, specs("Region", "R" + g), expected(t, 0, "R" + g));
      }
   }

   @Test
   void interleavedGroupsSelectTheSameRowsAsTheIntervalWalk() {
      DefaultTableLens t = table(1, 60, 6, true);

      for(int g = 0; g < 6; g++) {
         List<Integer> exp = expected(t, 0, "R" + g);
         assertEquals(10, exp.size());
         assertTrue(exp.get(exp.size() - 1) - exp.get(0) > 50, "the bounding interval is wide");
         assertSame(t, specs("Region", "R" + g), exp);
      }
   }

   @Test
   void nestedKeySetsSelectTheSameRowsAsTheIntervalWalk() {
      DefaultTableLens t = table(1, 90, 5, true);

      for(int g = 0; g < 5; g++) {
         for(int c = 0; c < 3; c++) {
            List<Integer> exp = new ArrayList<>();

            for(int r = 1; t.moreRows(r); r++) {
               if(("R" + g).equals(t.getObject(r, 0)) && ("C" + c).equals(t.getObject(r, 1))) {
                  exp.add(r);
               }
            }

            assertSame(t, specs("Region", "R" + g, "Cat", "C" + c), exp);
         }
      }
   }

   @Test
   void aGroupInTheLastRowAndInTheFirstDataRow() {
      DefaultTableLens t = new DefaultTableLens(new Object[][] {
         { "Region", "Idx" }, { "first", 1.0 }, { "a", 2.0 }, { "a", 3.0 }, { "last", 4.0 } });
      t.setHeaderRowCount(1);

      assertSame(t, specs("Region", "first"), List.of(1));
      assertSame(t, specs("Region", "last"), List.of(4));
      assertSame(t, specs("Region", "a"), List.of(2, 3));
   }

   @Test
   void aMissingGroupValueSelectsNothing() {
      DefaultTableLens t = table(1, 30, 3, true);

      assertSame(t, specs("Region", "nope"), List.of());
   }

   @Test
   void aMissingGroupColumnSelectsNothing() {
      DefaultTableLens t = table(1, 30, 3, true);

      assertSame(t, specs("NoSuchColumn", "R1"), List.of());
   }

   @Test
   void nullAndEmptyGroupKeysStayApart() {
      DefaultTableLens t = new DefaultTableLens(new Object[][] {
         { "Region", "Idx" }, { null, 1.0 }, { "", 2.0 }, { "a", 3.0 }, { null, 4.0 },
         { "", 5.0 } });
      t.setHeaderRowCount(1);

      assertSame(t, specs("Region", null), List.of(1, 4));
      assertSame(t, specs("Region", ""), List.of(2, 5));
      assertSame(t, specs("Region", "a"), List.of(3));
   }

   @Test
   void numberKeysSelectTheSameRowsAsTheIntervalWalk() {
      Object[][] data = new Object[31][];
      data[0] = new Object[] { "Key", "Idx" };

      for(int i = 0; i < 30; i++) {
         data[i + 1] = new Object[] { (double) (i % 4), (double) (i + 1) };
      }

      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);

      for(int k = 0; k < 4; k++) {
         assertSame(t, specs("Key", (double) k), expected(t, 0, (double) k));
      }
   }

   @Test
   void dateKeysSelectTheSameRowsAsTheIntervalWalk() {
      Object[][] data = new Object[31][];
      data[0] = new Object[] { "When", "Idx" };

      for(int i = 0; i < 30; i++) {
         data[i + 1] = new Object[] { java.sql.Date.valueOf("2021-01-0" + (1 + i % 5)),
                                      (double) (i + 1) };
      }

      DefaultTableLens t = new DefaultTableLens(data);
      t.setHeaderRowCount(1);

      for(int k = 1; k <= 5; k++) {
         assertSame(t, specs("When", java.sql.Date.valueOf("2021-01-0" + k)),
                    expected(t, 0, java.sql.Date.valueOf("2021-01-0" + k)));
      }
   }

   @Test
   void headerRowCountsZeroAndMoreThanOne() {
      for(int headers : new int[] { 0, 2, 3 }) {
         DefaultTableLens t = table(headers, 40, 4, true);

         for(int g = 0; g < 4; g++) {
            List<Point> old = walk(t, specs("Region", "R" + g), "Idx", false, null);
            List<Point> now = listed(t, specs("Region", "R" + g), "Idx", false, null);
            assertEquals(old, now, headers + " header rows, group " + g);

            if(headers > 0) {
               // the header rows hold the column names, they are never a group member
               assertEquals(expected(t, 0, "R" + g), rowsOf(now), headers + " header rows");
               assertTrue(rowsOf(now).stream().allMatch(r -> r >= t.getHeaderRowCount()));
            }
         }
      }
   }

   @Test
   void theExpressionColumnVariantVisitsTheSameRows() {
      DefaultTableLens t = table(1, 40, 4, true);

      for(int g = 0; g < 4; g++) {
         List<Point> old = walk(t, specs("Region", "R" + g), "1", true, null);
         List<Point> now = listed(t, specs("Region", "R" + g), "1", true, null);
         assertEquals(old, now);
         assertEquals(expected(t, 0, "R" + g), rowsOf(now));
      }
   }

   @Test
   void theRowListIsAscendingAndTheSelectorIsEmptyForAMissingGroup() {
      DefaultTableLens t = table(1, 40, 4, true);
      Map<String, NamedCellRange.GroupSpec> found = specs("Region", "R2");
      Map<String, NamedCellRange.GroupSpec> missing = specs("Region", "nope");
      CachedRowSelector selector = CachedRowSelector.getSelector(t, found);

      try {
         selector.prepare(found);
         int[] rows = selector.getGroupRows();
         assertNotNull(rows);
         assertEquals(10, rows.length);

         for(int i = 1; i < rows.length; i++) {
            assertTrue(rows[i] > rows[i - 1]);
         }

         assertEquals(rows[0], selector.getStartRow());
         assertEquals(rows[rows.length - 1], selector.getEndRow());

         selector.prepare(missing);
         assertNull(selector.getGroupRows(), "no group, no row list: callers use the match protocol");
         assertEquals(RangeProcessor.BREAK, selector.match(t, 0, 0));
         assertEquals(0, selector.getStartRow());
         assertEquals(0, selector.getEndRow());
      }
      finally {
         selector.endProcess();
         selector.endProcess();
      }
   }

   @Test
   void twoPreparesBackToBackKeepTheirOwnRows() {
      DefaultTableLens t = table(1, 40, 4, true);
      CachedRowSelector selector = CachedRowSelector.getSelector(t, specs("Region", "R0"));

      try {
         selector.prepare(specs("Region", "R0"));
         int[] first = selector.getGroupRows();
         int[] firstCopy = first.clone();

         selector.prepare(specs("Region", "R1"));
         int[] second = selector.getGroupRows();

         assertArrayEquals(firstCopy, first, "the snapshot of the first group is unchanged");
         assertEquals(expected(t, 0, "R0"), Arrays.stream(first).boxed().toList());
         assertEquals(expected(t, 0, "R1"), Arrays.stream(second).boxed().toList());

         selector.prepare(specs("Region", "R0"));
         assertArrayEquals(firstCopy, selector.getGroupRows());
      }
      finally {
         selector.endProcess();
         selector.endProcess();
         selector.endProcess();
      }
   }

   /** A table whose row count can be cut after the group map was built. */
   private static final class CuttableLens extends DefaultTableLens {
      CuttableLens(Object[][] data) {
         super(data);
      }

      @Override
      public boolean moreRows(int row) {
         return row < limit && super.moreRows(row);
      }

      @Override
      public int getRowCount() {
         return Math.min(limit, super.getRowCount());
      }

      private int limit = Integer.MAX_VALUE;
   }

   @Test
   void theRowCountCutOffStopsAtTheFirstListedRowBeyondTheTable() {
      Object[][] data = new Object[21][];
      data[0] = new Object[] { "Region", "Cat", "Idx" };

      for(int i = 0; i < 20; i++) {
         data[i + 1] = new Object[] { "R" + (i % 4), "C", (double) (i + 1) };
      }

      CuttableLens t = new CuttableLens(data);
      t.setHeaderRowCount(1);
      Map<String, NamedCellRange.GroupSpec> specs = specs("Region", "R1");

      // build the group map over the whole table
      assertEquals(List.of(2, 6, 10, 14, 18), rowsOf(listed(t, specs, "Idx", false, null)));

      // then shrink the table: the rows listed beyond it are not visited
      t.limit = 12;
      assertEquals(List.of(2, 6, 10), rowsOf(listed(t, specs, "Idx", false, null)));
      assertEquals(rowsOf(walk(t, specs, "Idx", false, null)),
                   rowsOf(listed(t, specs, "Idx", false, null)));

      t.limit = 3;
      assertEquals(List.of(2), rowsOf(listed(t, specs, "Idx", false, null)));
      assertEquals(rowsOf(walk(t, specs, "Idx", false, null)),
                   rowsOf(listed(t, specs, "Idx", false, null)));
   }

   // ---- the two consumers: NamedCellRange.getCells and FormulaFunctions.rowList

   @Test
   void getCellsAndRowListGiveTheRowsOfTheGroupOnSortedAndInterleavedTables() throws Exception {
      for(boolean mixed : new boolean[] { false, true }) {
         DefaultTableLens t = table(1, 60, 6, mixed);

         for(int g = 0; g < 6; g++) {
            List<Object> exp = new ArrayList<>();
            expected(t, 0, "R" + g).forEach(r -> exp.add((double) r));

            assertEquals(exp, cells("Idx@Region:R" + g, t), "getCells, mixed=" + mixed);

            Object[] values = ((TableValueList) FormulaFunctions.rowList(
               t, "Idx@Region:R" + g, "distinct=false")).getValues();
            assertEquals(exp, Arrays.asList(values), "rowList, mixed=" + mixed);
         }

         assertEquals(List.of(), cells("Idx@Region:nope", t));
         assertEquals(0, ((TableValueList) FormulaFunctions.rowList(
            t, "Idx@Region:nope", "distinct=false")).getValues().length);
      }
   }
}
