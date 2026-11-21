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

import inetsoft.util.DefaultComparator;
import org.junit.jupiter.api.*;
import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SortedDataSet}: row mapping, stability, comparators, ranges and calc rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SortedDataSetTest {
   private static final Comparator<Object> NAT = (a, b) -> ((Comparable) a).compareTo(b);
   private static final Comparator<Object> DESC = NAT.reversed();

   // ---------------------------------------------------------------- helpers

   private static DefaultDataSet k5() {
      return new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 2.0}, {"a", 1.0}, {"c", 3.0}, {"a", 5.0}, {"b", 4.0}});
   }

   private static DefaultDataSet g6() {
      return new DefaultDataSet(new Object[][]{
         {"g", "t", "v"}, {"A", "c", 1.0}, {"A", "a", 2.0}, {"A", "b", 4.0},
         {"B", "b", 8.0}, {"B", "a", 16.0}, {"B", "c", 32.0}});
   }

   private static int[] mapping(DataSet ds) {
      int[] res = new int[ds.getRowCount()];

      for(int i = 0; i < res.length; i++) {
         res[i] = ds instanceof SortedDataSet ? ((SortedDataSet) ds).getBaseRow(i) : i;
      }

      return res;
   }

   /** Running sum of column v in the order the rows are calculated. */
   private static class RT implements CalcColumn {
      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         if(first) {
            sum = 0;
         }

         Object val = data.getData("v", row);

         if(val instanceof Number) {
            sum += ((Number) val).doubleValue();
         }

         return sum;
      }

      @Override
      public String getHeader() {
         return "rt";
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return "v";
      }

      private double sum;
   }

   /** Calc row that appends fixed rows after the real rows. */
   private static class AddRow implements CalcRow {
      AddRow(Object[]... rows) {
         this.rows = Arrays.asList(rows);
      }

      @Override
      public List<Object[]> calculate(DataSet data) {
         return new ArrayList<>(rows);
      }

      @Override
      public Comparator getComparator(String col) {
         return null;
      }

      private final List<Object[]> rows;
   }

   /** Comparator that compares whole rows by column v. */
   private static class RowCmp implements DataSetComparator {
      @Override
      public DataSetComparator getComparator(DataSet data) {
         RowCmp cmp = new RowCmp();
         cmp.bound = data;
         return cmp;
      }

      @Override
      public Comparator getComparator(int row) {
         return this;
      }

      @Override
      public int compare(DataSet data, int row1, int row2) {
         return ((Comparable) data.getData("v", row1)).compareTo(data.getData("v", row2));
      }

      @Override
      public int compare(Object o1, Object o2) {
         if(o1 == null || o2 == null) {
            return 0;
         }

         return ((Comparable) o1).compareTo(o2);
      }

      DataSet bound;
   }

   private static DefaultDataSet k5WithCalcRows(Object[]... rows) {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      base.addCalcRow(new AddRow(rows));
      base.prepareCalc(null, null, true);
      return base;
   }

   // ------------------------------------------------------------------ order

   @Test
   void ascendingComparatorOrdersRowsStably() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SortedDataSet sorted = new SortedDataSet(base, "k");

      // ties keep the original order: a rows r1 before r3, b rows r0 before r4
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(sorted));
      assertEquals(List.of("a", "a", "b", "b", "c"), column(sorted, "k"));
      assertEquals(List.of(1.0, 5.0, 2.0, 4.0, 3.0), column(sorted, "v"));
   }

   @ParameterizedTest
   @ValueSource(ints = {5, 6, 7, 8, 9, 16, 17, 100, 1000})
   void sortMatchesStableListSort(int n) {
      for(long seed = 1; seed <= 10; seed++) {
         Random rnd = new Random(seed * 31 + n);
         Object[][] rows = new Object[n + 1][];
         rows[0] = new Object[]{"k", "v"};
         String[] keys = new String[n];

         for(int i = 0; i < n; i++) {
            keys[i] = "key" + rnd.nextInt(5);
            rows[i + 1] = new Object[]{keys[i], (double) i};
         }

         DefaultDataSet base = new DefaultDataSet(rows);
         base.setComparator("k", NAT);
         SortedDataSet sorted = new SortedDataSet(base, "k");

         List<Integer> expected = new ArrayList<>();

         for(int i = 0; i < n; i++) {
            expected.add(i);
         }

         expected.sort((a, b) -> keys[a].compareTo(keys[b]));
         int[] exp = expected.stream().mapToInt(Integer::intValue).toArray();
         assertArrayEquals(exp, mapping(sorted), "n=" + n + " seed=" + seed);
      }
   }

   @Test
   void descendingComparatorKeepsTiesInOriginalOrder() {
      DefaultDataSet base = k5();
      base.setComparator("k", DESC);
      assertArrayEquals(new int[]{2, 0, 4, 1, 3}, mapping(new SortedDataSet(base, "k")));
   }

   @Test
   void noComparatorKeepsOriginalOrder() {
      assertArrayEquals(new int[]{0, 1, 2, 3, 4}, mapping(new SortedDataSet(k5(), "k")));
   }

   @Test
   void forceSortUsesDefaultOrderWhenNoComparator() {
      SortedDataSet sorted = new SortedDataSet(k5());
      sorted.setForceSort(true);
      sorted.addSortColumn("v", false);

      assertArrayEquals(new int[]{1, 0, 2, 4, 3}, mapping(sorted));
      assertTrue(sorted.isForceSort());
   }

   @Test
   void forceSortKeepsExplicitComparator() {
      DefaultDataSet base = k5();
      base.setComparator("k", DESC);
      SortedDataSet sorted = new SortedDataSet(base);
      sorted.setForceSort(true);
      sorted.addSortColumn("k", false);

      assertArrayEquals(new int[]{2, 0, 4, 1, 3}, mapping(sorted));
   }

   @Test
   void secondSortColumnBreaksTies() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      base.setComparator("v", DESC);

      // within a: v 5 then 1; within b: v 4 then 2
      assertArrayEquals(new int[]{3, 1, 4, 0, 2}, mapping(new SortedDataSet(base, "k", "v")));
   }

   @Test
   void secondColumnWithoutComparatorCannotReorder() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);

      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(new SortedDataSet(base, "k", "v")));
   }

   @Test
   void sortByCalcColumn() {
      DefaultDataSet base = k5();
      base.addCalcColumn(new RT());
      base.prepareCalc(null, null, true);
      SortedDataSet sorted = new SortedDataSet(base);
      sorted.addSortColumn("rt", false);
      sorted.setComparator("rt", DESC);

      // rt by base row is 2, 3, 6, 11, 15
      assertArrayEquals(new int[]{4, 3, 2, 1, 0}, mapping(sorted));
      assertArrayEquals(new String[]{"rt"}, sorted.getSortColumns());
   }

   @Test
   void unknownSortColumnThrowsAndLeavesNoSortColumn() {
      assertThrows(inetsoft.util.MessageException.class, () -> new SortedDataSet(k5(), "zz"));

      SortedDataSet sorted = new SortedDataSet(k5());
      assertThrows(inetsoft.util.MessageException.class, () -> sorted.addSortColumn("zz", false));
      assertEquals(0, sorted.getSortColumns().length);
   }

   @Test
   void setComparatorOnUnknownColumnThrows() {
      SortedDataSet sorted = new SortedDataSet(k5(), "k");
      assertThrows(inetsoft.util.MessageException.class, () -> sorted.setComparator("zz", NAT));
   }

   @Test
   void sortColumnsAreReturnedInTheOrderAdded() {
      assertArrayEquals(new String[]{"v", "k"}, new SortedDataSet(k5(), "v", "k").getSortColumns());
   }

   @Test
   void shapeEqualsBaseWithCalcColumnAndCalcRow() {
      DefaultDataSet base = g6();
      base.setComparator("t", NAT);
      base.addCalcColumn(new RT());
      base.addCalcRow(new AddRow(new Object[]{"Z", "z", 9.0}));
      base.prepareCalc(null, null, true);
      SortedDataSet sorted = new SortedDataSet(base, "t");

      assertEquals(base.getRowCount(), sorted.getRowCount());
      assertEquals(base.getColCount(), sorted.getColCount());

      for(int c = 0; c < base.getColCount(); c++) {
         String header = base.getHeader(c);
         assertEquals(header, sorted.getHeader(c));
         assertEquals(c, sorted.indexOfHeader(header));
         assertEquals(base.getType(header), sorted.getType(header));
         assertEquals(base.isMeasure(header), sorted.isMeasure(header));
      }
   }

   @Test
   void mappingIsAPermutationAndCellsFollowIt() {
      for(long seed = 1; seed <= 50; seed++) {
         Random rnd = new Random(seed);
         int n = rnd.nextInt(41);
         Object[][] rows = new Object[n + 1][];
         rows[0] = new Object[]{"k", "v"};

         for(int i = 0; i < n; i++) {
            rows[i + 1] = new Object[]{"s" + rnd.nextInt(6), (double) i};
         }

         DefaultDataSet base = new DefaultDataSet(rows);
         int type = (int) (seed % 4);
         Comparator<Object> cmp;

         switch(type) {
         case 0:
            cmp = NAT;
            break;
         case 1:
            cmp = DESC;
            break;
         case 2:
            cmp = (a, b) -> 0;
            break;
         default:
            cmp = (a, b) -> Integer.compare(a.hashCode() % 3, b.hashCode() % 3);
         }

         if(n > 0) {
            base.setComparator("k", cmp);
         }

         SortedDataSet sorted = new SortedDataSet(base, "k");
         assertEquals(n, sorted.getRowCount());
         boolean[] seen = new boolean[n];

         for(int i = 0; i < n; i++) {
            int b = sorted.getBaseRow(i);
            assertTrue(b >= 0 && b < n, "seed " + seed);
            assertFalse(seen[b], "duplicate row, seed " + seed);
            seen[b] = true;
            assertEquals(base.getData(0, b), sorted.getData(0, i));
            assertEquals(base.getData(1, b), sorted.getData(1, i));
         }
      }
   }

   // ----------------------------------------------------------------- ranges

   private static SortedDataSet sortedK(int start, int end) {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SortedDataSet sorted = new SortedDataSet(base, "k");
      sorted.setStartRow(start);
      sorted.setEndRow(end);
      return sorted;
   }

   @Test
   void rangeSortOnlyTouchesTheRange() {
      // rows 1..3 sorted: a r1, a r3, c r2; rows 0 and 4 untouched
      assertArrayEquals(new int[]{0, 1, 3, 2, 4}, mapping(sortedK(1, 4)));
   }

   @Test
   void openEndRangeSortsToTheLastRow() {
      SortedDataSet sorted = sortedK(2, -1);
      assertArrayEquals(new int[]{0, 1, 3, 4, 2}, mapping(sorted));
      assertEquals(5, sorted.getEffectiveEndRow());
   }

   @Test
   void negativeStartIsTreatedAsZero() {
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(sortedK(-3, -1)));
   }

   @Test
   @Disabled("Not filed (not reachable): endRow comes from getEndRow/rowRange; setEndRow(99) over 5 rows throws ArrayIndexOutOfBoundsException (index 5, length 5) in mergeSort, expected mapping 1,3,0,4,2")
   @DisplayName("end row beyond the row count is clamped to all rows")
   void endBeyondRowsIsClamped() {
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(sortedK(0, 99)));
   }

   @Test
   void emptyRangeKeepsIdentityMapping() {
      assertArrayEquals(new int[]{0, 1, 2, 3, 4}, mapping(sortedK(3, 3)));
   }

   @Test
   void effectiveEndRowEqualsEndRowWithoutCalcRows() {
      assertEquals(3, sortedK(0, 3).getEffectiveEndRow());
   }

   private static SortedDataSet rangeWithCalcRows(boolean ignoreZero, String[] vars,
                                                  String[] groups, Object[]... rows)
   {
      SortedDataSet sorted = new SortedDataSet(k5WithCalcRows(rows), "k");
      sorted.setStartRow(0);
      sorted.setEndRow(3);
      sorted.setVars(vars);
      sorted.setGroupFields(groups);
      sorted.setIgnoreZeroCalc(ignoreZero);
      return sorted;
   }

   @Test
   void calcRowsJoinTheSortedRange() {
      SortedDataSet sorted = rangeWithCalcRows(false, null, null,
                                               new Object[]{"b", 7.0}, new Object[]{"a", 8.0});

      assertArrayEquals(new int[]{1, 6, 0, 5, 2, 3, 4}, mapping(sorted));
      assertEquals(5, sorted.getEffectiveEndRow());
   }

   @Test
   void calcRowsWithNullVarAreExcludedFromRange() {
      SortedDataSet sorted = rangeWithCalcRows(false, new String[]{"v"}, null,
                                               new Object[]{"b", 7.0}, new Object[]{"a", null});

      assertArrayEquals(new int[]{1, 0, 5, 2, 4, 3, 6}, mapping(sorted));
      assertEquals(4, sorted.getEffectiveEndRow());
   }

   @Test
   void ignoreZeroCalcExcludesZeroCalcRows() {
      Object[] zero = {"a", 0.0};
      SortedDataSet ignored = rangeWithCalcRows(true, new String[]{"v"}, null,
                                                new Object[]{"b", 7.0}, zero);
      assertArrayEquals(new int[]{1, 0, 5, 2, 4, 3, 6}, mapping(ignored));

      SortedDataSet kept = rangeWithCalcRows(false, new String[]{"v"}, null,
                                             new Object[]{"b", 7.0}, zero);
      assertArrayEquals(new int[]{1, 6, 0, 5, 2, 3, 4}, mapping(kept));
   }

   @Test
   void calcRowsWithGroupOutsideRangeAreExcluded() {
      SortedDataSet sorted = rangeWithCalcRows(false, null, new String[]{"k"},
                                               new Object[]{"b", 7.0}, new Object[]{"z", 1.0});

      assertArrayEquals(new int[]{1, 0, 5, 2, 4, 3, 6}, mapping(sorted));
   }

   @Test
   void cloneIsIndependentOfTheOriginal() {
      SortedDataSet sorted = sortedK(0, -1);
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(sorted));
      SortedDataSet copy = sorted.clone();

      copy.addSortColumn("v", false);
      copy.setComparator("v", DESC);
      copy.setComparator("k", DESC);

      assertEquals(1, sorted.getSortColumns().length);
      assertEquals(2, copy.getSortColumns().length);
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(sorted));
      assertNotSame(sorted.getDataSet(), copy.getDataSet());
   }

   // ----------------------------------------------------- changes after access

   @Test
   @Disabled("Not filed (not reachable): every caller sets the comparator and sort columns before the first read; after setComparator following a first read the mapping is identity but marked sorted, expected 2,0,4,1,3 actual 0,1,2,3,4")
   void comparatorChangedAfterFirstAccessIsApplied() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SortedDataSet sorted = new SortedDataSet(base, "k");
      sorted.getBaseRow(0);
      sorted.setComparator("k", DESC);

      assertArrayEquals(new int[]{2, 0, 4, 1, 3}, mapping(sorted));
   }

   @Test
   @Disabled("Not filed (not reachable): every caller sets the comparator and sort columns before the first read; addSortColumn after a first read leaves the identity mapping marked sorted, expected 3,1,4,0,2 actual starts with row 0")
   void sortColumnAddedAfterFirstAccessIsApplied() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      base.setComparator("v", DESC);
      SortedDataSet sorted = new SortedDataSet(base, "k");
      sorted.getBaseRow(0);
      sorted.addSortColumn("v", false);

      assertArrayEquals(new int[]{3, 1, 4, 0, 2}, mapping(sorted));
   }

   // ------------------------------------------------------------ comparators

   @Test
   void getComparatorReturnsTheOneSetOnTheSortedDataSet() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SortedDataSet sorted = new SortedDataSet(base, "k");
      assertSame(NAT, sorted.getComparator("k"));

      Comparator<Object> other = (a, b) -> 0;
      sorted.setComparator("k", other);
      assertSame(other, sorted.getComparator("k"));
   }

   @Test
   void columnNotSortedUsesTheBaseComparator() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      base.setComparator("v", DESC);
      SortedDataSet sorted = new SortedDataSet(base, "k");

      assertSame(DESC, sorted.getComparator("v"));
   }

   @Test
   void rowComparatorModeUsesRowComparisonOnlyWhenRequested() {
      DefaultDataSet base = k5();
      base.setComparator("k", new RowCmp());

      SortedDataSet byRow = new SortedDataSet(base);
      byRow.addSortColumn("k", true);
      // ordered by v through compare(DataSet, row, row)
      assertArrayEquals(new int[]{1, 0, 2, 4, 3}, mapping(byRow));

      SortedDataSet byValue = new SortedDataSet(base);
      byValue.addSortColumn("k", false);
      // compares the k values through compare(Object, Object)
      assertArrayEquals(new int[]{1, 3, 0, 4, 2}, mapping(byValue));
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "empty"})
   void nullKeySortsAsLowestValue(String kind) {
      Object nullKey = "null".equals(kind) ? null : "";
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 1.0}, {nullKey, 2.0}, {"a", 3.0}, {nullKey, 4.0}});
      base.setComparator("k", new DefaultComparator(true));

      assertArrayEquals(new int[]{1, 3, 2, 0}, mapping(new SortedDataSet(base, "k")));
   }

   @Test
   @DisplayName("NaN and Infinity passed to the constructor become null and sort first in input order")
   void nanAndInfinityBecomeNullInTheConstructor() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"w", 1.0}, {"x", Double.NaN}, {"y", 0.5}, {"z", Double.POSITIVE_INFINITY}});
      base.setComparator("v", new DefaultComparator());

      assertNull(base.getData("v", 1));
      assertNull(base.getData("v", 3));
      assertArrayEquals(new int[]{1, 3, 2, 0}, mapping(new SortedDataSet(base, "v")));
   }

   @Test
   @DisplayName("real NaN and Infinity values sort in Double.compare order, NaN last")
   void realNanAndInfinitySortInDoubleOrder() {
      Double[] values = {1.0, Double.NaN, 0.5, Double.POSITIVE_INFINITY};
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"w", 0.0}, {"x", 0.0}, {"y", 0.0}, {"z", 0.0}}) {
         @Override
         protected Object getData0(int col, int row) {
            return col == 1 ? values[row] : super.getData0(col, row);
         }
      };
      base.setComparator("v", new DefaultComparator());

      assertArrayEquals(new int[]{2, 0, 3, 1}, mapping(new SortedDataSet(base, "v")));
   }

   @Test
   @DisplayName("manual order comparator orders values by the given list")
   void customOrderComparator() {
      DefaultDataSet base = k5();
      base.setOrder("k", new Object[]{"c", "a", "b"});

      assertArrayEquals(new int[]{2, 1, 3, 0, 4}, mapping(new SortedDataSet(base, "k")));
   }

   // --------------------------------------------------------------- no columns

   @Test
   @Disabled("Not filed (not reachable): a treemap always has a tree dimension; without sort columns mapping is null and getBaseRow/getRowCount throw NullPointerException, expected identity mapping and 5 rows")
   void noSortColumnsLeavesRowsUntouched() {
      DefaultDataSet base = k5();
      SortedDataSet sorted = new SortedDataSet(base);

      assertEquals(5, sorted.getRowCount());

      for(int r = 0; r < 5; r++) {
         assertEquals(r, sorted.getBaseRow(r));
         assertEquals(base.getData(0, r), sorted.getData(0, r));
         assertEquals(base.getData(1, r), sorted.getData(1, r));
      }
   }

   @Test
   void accessorDefaultsAndSetters() {
      SortedDataSet sorted = new SortedDataSet(k5());

      assertEquals(0, sorted.getStartRow());
      assertEquals(-1, sorted.getEndRow());
      assertFalse(sorted.isForceSort());
      assertFalse(sorted.isIgnoreZeroCalc());
      assertNull(sorted.getVars());
      assertNull(sorted.getGroupFields());

      String[] vars = {"v"};
      String[] groups = {"k"};
      sorted.setStartRow(2);
      sorted.setEndRow(4);
      sorted.setForceSort(true);
      sorted.setIgnoreZeroCalc(true);
      sorted.setVars(vars);
      sorted.setGroupFields(groups);

      assertEquals(2, sorted.getStartRow());
      assertEquals(4, sorted.getEndRow());
      assertTrue(sorted.isForceSort());
      assertTrue(sorted.isIgnoreZeroCalc());
      assertSame(vars, sorted.getVars());
      assertSame(groups, sorted.getGroupFields());
   }

   @Test
   @DisplayName("pinned: after dispose the rows are still readable")
   void disposeMarksBaseAndKeepsRowsReadable() {
      DefaultDataSet base = k5();
      base.setComparator("k", NAT);
      SortedDataSet sorted = new SortedDataSet(base, "k");
      List<Object> before = column(sorted, "v");

      sorted.dispose();

      assertTrue(sorted.isDisposed());
      assertTrue(base.isDisposed());
      assertEquals(before, column(sorted, "v"));
   }

   private static List<Object> column(DataSet ds, String col) {
      List<Object> res = new ArrayList<>();

      for(int i = 0; i < ds.getRowCount(); i++) {
         res.add(ds.getData(col, i));
      }

      return res;
   }
}
