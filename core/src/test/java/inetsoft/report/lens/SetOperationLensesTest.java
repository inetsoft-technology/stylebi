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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.internal.table.MergedTable;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The set operation lenses (union, intersect, minus) and the distinct lens, driven on in-memory
 * tables. The set lenses merge on a worker thread, so a few tests hold that thread with a
 * gated visitor in a {@link SetTableLens} subclass of the test.
 *
 * <p>Not covered: the order of an intersect or minus result. It is the order of the merge's
 * B-tree (by the hash of a row), which no caller depends on, so the tests compare it as a set.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SetOperationLensesTest {
   private static final String MAX_ROW = "DistinctTableLens.maxrow";
   private final String maxRow = SreeEnv.getProperty(MAX_ROW);
   private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
      Thread thread = new Thread(runnable, "set-operations-test");
      thread.setDaemon(true);
      return thread;
   });

   @AfterEach
   void restore() {
      SreeEnv.setProperty(MAX_ROW, maxRow);
      executor.shutdownNow();
   }

   // ----------------------------------------------------------------------------------------
   // helpers

   /**
    * A table of one header row and the rows given.
    */
   private static DefaultTableLens table(Object[]... rows) {
      return new DefaultTableLens(rows);
   }

   private static List<List<Object>> rows(XTable table) {
      table.moreRows(XTable.EOT);
      List<List<Object>> result = new ArrayList<>();

      for(int r = 0; table.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(table.getObject(r, c));
         }

         result.add(row);
      }

      return result;
   }

   private static List<Object> row(Object... cells) {
      return Arrays.asList(cells);
   }

   @SafeVarargs
   private static List<List<Object>> list(List<Object>... rows) {
      return Arrays.asList(rows);
   }

   /**
    * The rows after the header, as a set.
    */
   private static Set<List<Object>> body(XTable table, int headers) {
      return new HashSet<>(rows(table).subList(headers, rows(table).size()));
   }

   @SafeVarargs
   private static Set<List<Object>> set(List<Object>... rows) {
      return new HashSet<>(Arrays.asList(rows));
   }

   // ----------------------------------------------------------------------------------------
   // union

   @Test
   void unionDistinctDefault() {
      TableLens left = table(
         new Object[] {"id", "name"}, new Object[] {1, "ann"}, new Object[] {2, "bob"},
         new Object[] {2, "bob"});
      TableLens right = table(
         new Object[] {"ID", "NAME"}, new Object[] {2, "bob"}, new Object[] {3, "cy"},
         new Object[] {1, "ann"}, new Object[] {4, "di"});
      UnionTableLens union = new UnionTableLens(left, right);

      assertTrue(union.isDistinct());
      // the header comes from the first table, then the first table's rows, then the rows of the
      // second table that are not there yet; a row repeated inside one table is kept once too
      assertEquals(list(row("id", "name"), row(1, "ann"), row(2, "bob"), row(3, "cy"),
                        row(4, "di")),
                   rows(union));
      assertEquals(5, union.getRowCount());
      assertEquals(1, union.getHeaderRowCount());

      // a table united with a copy of itself, as the old suite's concatenate case does with a
      // table of products, is the table: 13 products and the header
      Object[][] products = new Object[14][];
      products[0] = new Object[] {"PRODUCT_NAME", "PRICE", "INSTOCK", "PRODUCT_ID"};

      for(int i = 1; i < products.length; i++) {
         products[i] = new Object[] {"product " + i, 10 * i, i % 2 == 0, i};
      }

      UnionTableLens same = new UnionTableLens(new DefaultTableLens(products),
                                               new DefaultTableLens(products));
      assertEquals(Arrays.stream(products).map(Arrays::asList).collect(Collectors.toList()),
                   rows(same));
      assertEquals(14, same.getRowCount());
   }

   @Test
   void unionAll() {
      TableLens left = table(
         new Object[] {"id", "name"}, new Object[] {1, "ann"}, new Object[] {2, "bob"});
      TableLens right = table(
         new Object[] {"ID", "NAME"}, new Object[] {2, "bob"}, new Object[] {1, "ann"},
         new Object[] {2, "bob"});
      UnionTableLens union = new UnionTableLens(left, right);
      union.setDistinct(false);

      assertFalse(union.isDistinct());
      // the left rows, then every right row after the right header: nothing is removed
      assertEquals(list(row("id", "name"), row(1, "ann"), row(2, "bob"), row(2, "bob"),
                        row(1, "ann"), row(2, "bob")),
                   rows(union));
      // the row count is the sum of the two, less the header of the second
      assertEquals(left.getRowCount() + right.getRowCount() - 1, union.getRowCount());
      assertEquals(6, union.getRowCount());
      assertEquals("id", union.getObject(0, 0));
   }

   @Test
   void columnCountIsMinimum() throws Exception {
      TableLens wide = table(
         new Object[] {"a", "b", "c"}, new Object[] {1, 2, 3}, new Object[] {4, 5, 6});
      TableLens narrow = table(
         new Object[] {"X", "Y"}, new Object[] {1, 2}, new Object[] {7, 8});

      UnionTableLens union = new UnionTableLens(wide, narrow);
      assertEquals(2, union.getColCount());
      assertEquals(list(row("a", "b"), row(1, 2), row(4, 5), row(7, 8)), rows(union));

      // the minimum does not depend on which side is wider
      UnionTableLens reversed = new UnionTableLens(narrow, wide);
      assertEquals(2, reversed.getColCount());
      assertEquals(list(row("X", "Y"), row(1, 2), row(7, 8), row(4, 5)), rows(reversed));

      // the operations that match rows compare only the shared columns
      IntersectTableLens intersect = new IntersectTableLens(wide, narrow);
      assertEquals(2, intersect.getColCount());
      assertEquals(row("a", "b"), rows(intersect).get(0));
      assertEquals(set(row(1, 2)), body(intersect, 1));

      MinusTableLens minus = new MinusTableLens(wide, narrow);
      assertEquals(2, minus.getColCount());
      assertEquals(row("a", "b"), rows(minus).get(0));
      assertEquals(set(row(4, 5)), body(minus, 1));
   }

   @Test
   void unionRowCountWhileLoading() {
      LoadingTable left = new LoadingTable(3, 3, "a", "b");
      LoadingTable right = new LoadingTable(3, 2, "c", "d");
      UnionTableLens union = new UnionTableLens(left, right);
      union.setDistinct(false);

      // the second table has loaded only its header and one row: the union is not complete, so
      // the count is negative
      assertTrue(right.getRowCount() < 0);
      // 4 rows are loaded (3 of the left, the header and 1 of the right), so at least -4 - 1
      assertTrue(union.getRowCount() <= -5, "a loading source makes the count negative");
      // the rows that are there can be read
      assertTrue(union.moreRows(3));
      assertEquals("b", union.getObject(2, 0));
      assertEquals("c", union.getObject(3, 0));

      // after moreRows(EOT) every source has finished and the count is the true one
      assertFalse(union.moreRows(XTable.EOT));
      assertEquals(5, union.getRowCount());
      assertEquals(list(row("H"), row("a"), row("b"), row("c"), row("d")), rows(union));

      // a fresh union asked for a row that neither table has yet: the first table is complete
      // and the second is still loading, so the row is not there
      UnionTableLens fresh = new UnionTableLens(new LoadingTable(3, 3, "a", "b"),
                                                new LoadingTable(3, 2, "c", "d"));
      fresh.setDistinct(false);
      assertFalse(fresh.moreRows(4));
      assertTrue(fresh.moreRows(3));
   }

   /**
    * A table of one column that reports its rows as still loading until {@code moreRows(EOT)}.
    */
   private static final class LoadingTable extends AbstractTableLens {
      /**
       * @param total  the rows once loaded, the header included.
       * @param loaded the rows that are loaded now, the header included.
       * @param values the values of the rows after the header.
       */
      LoadingTable(int total, int loaded, Object... values) {
         this.total = total;
         this.loaded = loaded;
         this.complete = loaded >= total;
         this.values = values;
      }

      @Override
      public int getRowCount() {
         return complete ? total : -loaded - 1;
      }

      @Override
      public int getColCount() {
         return 1;
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public boolean moreRows(int row) {
         if(row == EOT) {
            loaded = total;
            complete = true;
            return false;
         }

         return row < loaded;
      }

      @Override
      public Object getObject(int r, int c) {
         return r == 0 ? "H" : values[r - 1];
      }

      private final int total;
      private volatile int loaded;
      private volatile boolean complete;
      private final Object[] values;
   }

   /**
    * The convention of {@code TableLens.getRowCount}: while loading, the negative number of
    * loaded rows minus one. A union counts the rows it can show, which are the rows of the
    * first table and those of the second without its header.
    */
   @Disabled("Bug #80538: a non-distinct union counts one row too many while loading; "
             + "fixed in 13.8, not in StyleBI yet")
   @Test
   void unionRowCountWhileLoadingFollowsTheConvention() {
      LoadingTable left = new LoadingTable(3, 3, "a", "b");
      LoadingTable right = new LoadingTable(3, 2, "c", "d");
      UnionTableLens union = new UnionTableLens(left, right);
      union.setDistinct(false);

      // left 3 rows + right 2 loaded rows - the right header = 4 loaded rows
      assertEquals(-4 - 1, union.getRowCount());

      LoadingTable first = new LoadingTable(3, 2, "a", "b");
      UnionTableLens loadingFirst = new UnionTableLens(first, new LoadingTable(3, 3, "c", "d"));
      loadingFirst.setDistinct(false);
      assertEquals(-2 - 1, loadingFirst.getRowCount());
   }

   // ----------------------------------------------------------------------------------------
   // intersect and minus

   @Test
   void intersect() throws Exception {
      TableLens a = table(
         new Object[] {"id", "name"}, new Object[] {1, "ann"}, new Object[] {2, "bob"},
         new Object[] {3, "cy"}, new Object[] {2, "bob"});
      TableLens b = table(
         new Object[] {"id", "name"}, new Object[] {2, "bob"}, new Object[] {3, "cy"},
         new Object[] {4, "di"});
      TableLens c = table(
         new Object[] {"id", "name"}, new Object[] {3, "cy"}, new Object[] {1, "ann"},
         new Object[] {5, "ed"});

      IntersectTableLens two = new IntersectTableLens(a, b);
      assertEquals(row("id", "name"), rows(two).get(0));
      assertEquals(3, rows(two).size(), "the header and the two rows both tables have");
      assertEquals(set(row(2, "bob"), row(3, "cy")), body(two, 1));

      // with three tables a row has to be in every one of them
      IntersectTableLens three = new IntersectTableLens(a, b);
      three.setTable(2, c);
      assertEquals(2, rows(three).size(), "the header and the one row all three tables have");
      assertEquals(set(row(3, "cy")), body(three, 1));

      // nothing in common: only the header is left
      IntersectTableLens none = new IntersectTableLens(
         table(new Object[] {"id"}, new Object[] {1}),
         table(new Object[] {"id"}, new Object[] {2}));
      assertEquals(list(row("id")), rows(none));
   }

   @Test
   void minus() throws Exception {
      TableLens left = table(
         new Object[] {"id", "name"}, new Object[] {1, "ann"}, new Object[] {2, "bob"},
         new Object[] {3, "cy"}, new Object[] {3, "cy"}, new Object[] {5, "ed"});
      TableLens right = table(
         new Object[] {"id", "name"}, new Object[] {2, "bob"}, new Object[] {4, "di"});
      TableLens third = table(
         new Object[] {"id", "name"}, new Object[] {5, "ed"});

      MinusTableLens minus = new MinusTableLens(left, right);
      assertEquals(row("id", "name"), rows(minus).get(0));
      // 4 is only on the right and is not added; 3 is on the left twice and is kept once
      assertEquals(4, rows(minus).size());
      assertEquals(set(row(1, "ann"), row(3, "cy"), row(5, "ed")), body(minus, 1));

      // a row that is in any of the other tables is removed
      MinusTableLens both = new MinusTableLens(left, right);
      both.setTable(2, third);
      assertEquals(3, rows(both).size());
      assertEquals(set(row(1, "ann"), row(3, "cy")), body(both, 1));

      // minus an equal table leaves the header
      assertEquals(list(row("id", "name")), rows(new MinusTableLens(left, left)));
   }

   // ----------------------------------------------------------------------------------------
   // distinct

   private static final Object[] HEADER = {"k", "v", "w"};

   @Test
   void distinctAllColumns() {
      DefaultTableLens base = table(
         HEADER, new Object[] {"b", 2, "x"}, new Object[] {"a", 1, "y"}, new Object[] {"b", 2, "x"},
         new Object[] {"a", 1, "z"}, new Object[] {"a", 1, "y"});
      DistinctTableLens distinct = new DistinctTableLens(base);

      assertArrayEquals(new int[] {0, 1, 2}, distinct.getCols());
      assertEquals(1, distinct.getHeaderRowCount());
      // no column list: a row has to repeat in every column to be removed; the header stays and
      // the other rows come out sorted
      assertEquals(list(row("k", "v", "w"), row("a", 1, "y"), row("a", 1, "z"), row("b", 2, "x")),
                   rows(distinct));
      assertEquals(4, distinct.getRowCount());
   }

   @Test
   void distinctSubsetColumns() {
      Object[][] data = {
         {"c0", "c1", "c2", "c3"},
         {"a", 1, "x", 10}, {"a", 2, "x", 20}, {"a", 3, "y", 30}, {"b", 4, "x", 40},
         {"b", 5, "x", 50}, {"b", 6, "y", 60}, {"a", 7, "y", 70}
      };
      DistinctTableLens distinct = new DistinctTableLens(new DefaultTableLens(data),
                                                         new int[] {0, 2});
      List<List<Object>> result = rows(distinct);

      assertEquals(row("c0", "c1", "c2", "c3"), result.get(0));
      assertEquals(5, result.size(), "the header and one row for each of the four (c0, c2) pairs");
      List<List<Object>> pairs = new ArrayList<>();
      List<List<Object>> sources = new ArrayList<>();

      for(Object[] source : Arrays.copyOfRange(data, 1, data.length)) {
         sources.add(Arrays.asList(source));
      }

      for(List<Object> picked : result.subList(1, result.size())) {
         pairs.add(row(picked.get(0), picked.get(2)));
         // the row that stands for a pair is one of the source rows with that pair
         assertTrue(sources.contains(picked), picked.toString());
      }

      assertEquals(set(row("a", "x"), row("a", "y"), row("b", "x"), row("b", "y")),
                   new HashSet<>(pairs));
      assertEquals(4, new HashSet<>(pairs).size());
   }

   @Test
   void distinctStableOrder() {
      DefaultTableLens base = table(
         new Object[] {"k", "v"}, new Object[] {"b", 2}, new Object[] {"a", 1},
         new Object[] {"b", 2}, new Object[] {"a", 3}, new Object[] {"a", 1});

      // stable: hashing, the first occurrence of each row, in source order
      DistinctTableLens stable = new DistinctTableLens(base, new int[] {0, 1}, true);
      assertEquals(list(row("k", "v"), row("b", 2), row("a", 1), row("a", 3)), rows(stable));

      // not stable and two columns: sorted distinct
      DistinctTableLens sorted = new DistinctTableLens(base, new int[] {0, 1}, false);
      assertEquals(list(row("k", "v"), row("a", 1), row("a", 3), row("b", 2)), rows(sorted));

      // one column is hashed even when it is not stable: source order
      DistinctTableLens single = new DistinctTableLens(base, new int[] {0}, false);
      assertEquals(list(row("k", "v"), row("b", 2), row("a", 1)), rows(single));
   }

   @Test
   void distinctDimensionComparers() {
      Object[][] data = {
         {"when", "kind"},
         {date(2026, Calendar.JANUARY, 5), "A"}, {date(2026, Calendar.JANUARY, 20), "A"},
         {date(2026, Calendar.FEBRUARY, 3), "A"}, {date(2026, Calendar.JANUARY, 7), "B"},
         {date(2026, Calendar.FEBRUARY, 9), "B"}, {date(2026, Calendar.FEBRUARY, 11), "B"}
      };
      DefaultTableLens base = new DefaultTableLens(data);

      // by raw value every row is different
      assertEquals(7, rows(new DistinctTableLens(base, new int[] {0, 1})).size());

      // a comparer for the month makes two timestamps of one month the same row
      Comparator<Object> month = Comparator.comparingInt(
         value -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime((java.util.Date) value);
            return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH);
         });
      DistinctTableLens byMonth = new DistinctTableLens(
         base, new int[] {0, 1}, new int[] {DataRef.NONE, DataRef.NONE},
         new Comparator[] {month, null});
      List<List<Object>> result = rows(byMonth);

      assertEquals(5, result.size(), "the header and one row for each month and kind");
      Set<List<Object>> monthKind = new HashSet<>();

      for(List<Object> picked : result.subList(1, result.size())) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTime((java.util.Date) picked.get(0));
         monthKind.add(row(calendar.get(Calendar.MONTH), picked.get(1)));
      }

      assertEquals(set(row(Calendar.JANUARY, "A"), row(Calendar.FEBRUARY, "A"),
                       row(Calendar.JANUARY, "B"), row(Calendar.FEBRUARY, "B")),
                   monthKind);
   }

   private static java.sql.Timestamp date(int year, int month, int day) {
      Calendar calendar = Calendar.getInstance();
      calendar.clear();
      calendar.set(year, month, day, 12, 0, 0);
      return new java.sql.Timestamp(calendar.getTimeInMillis());
   }

   @Test
   void distinctMaxRows() {
      Object[][] data = new Object[101][];
      data[0] = new Object[] {"n"};

      for(int i = 1; i < data.length; i++) {
         data[i] = new Object[] {i};
      }

      DefaultTableLens base = new DefaultTableLens(data);
      assertEquals(101, rows(new DistinctTableLens(base)).size(), "no limit: every row");

      // pins the current behavior: the scan stops at source row 5, the header and the four
      // rows before it, and a non-numeric value leaves the header only (both are design)
      SreeEnv.setProperty(MAX_ROW, "5");
      DistinctTableLens limited = new DistinctTableLens(base);
      assertEquals(list(row("n"), row(1), row(2), row(3), row(4)), rows(limited));

      // not a number: no row is read, the lens ends with its header
      SreeEnv.setProperty(MAX_ROW, "x");
      DistinctTableLens invalid = new DistinctTableLens(base);
      assertEquals(list(row("n")), rows(invalid));
      assertEquals(1, invalid.getRowCount());
   }

   @Test
   void distinctReusesSortedBase() {
      DefaultTableLens data = table(
         new Object[] {"a", "b"}, new Object[] {1, "y"}, new Object[] {2, "x"},
         new Object[] {1, "y"}, new Object[] {2, "z"}, new Object[] {1, "x"});

      // a base sorted on the distinct columns, descending, is used as it is: the rows come out
      // in its order, where the lens' own sort would give ascending rows
      SortFilter descending = new SortFilter(data, new int[] {0, 1}, false);
      DistinctTableLens reused = new DistinctTableLens(descending, new int[] {0, 1});
      assertEquals(list(row("a", "b"), row(2, "z"), row(2, "x"), row(1, "y"), row(1, "x")),
                   rows(reused));

      // columns that are not in the base's sort are sorted by the lens itself
      SortFilter byFirst = new SortFilter(data, new int[] {0}, false);
      DistinctTableLens own = new DistinctTableLens(byFirst, new int[] {0, 1});
      assertEquals(list(row("a", "b"), row(1, "x"), row(1, "y"), row(2, "x"), row(2, "z")),
                   rows(own));
   }

   /**
    * A base sorted by (a, b, c) puts the equal (b, c) pairs of different a next to nothing, but
    * the lens takes it as sorted for (b, c) because they are in the same order.
    */
   @Test
   void distinctOnNonPrefixOfSortedBase() {
      DefaultTableLens data = table(
         new Object[] {"a", "b", "c"}, new Object[] {1, "x", "p"}, new Object[] {1, "y", "q"},
         new Object[] {2, "x", "p"}, new Object[] {2, "y", "q"});
      SortFilter sorted = new SortFilter(data, new int[] {0, 1, 2});
      DistinctTableLens distinct = new DistinctTableLens(sorted, new int[] {1, 2});
      List<List<Object>> result = rows(distinct);
      Set<List<Object>> pairs = new HashSet<>();

      for(List<Object> picked : result.subList(1, result.size())) {
         assertTrue(pairs.add(row(picked.get(1), picked.get(2))),
                    "a (b, c) pair is repeated: " + result);
      }

      assertEquals(2, pairs.size());
   }

   // ----------------------------------------------------------------------------------------
   // distinct over a column map of a sorted base (bug #80549)

   private static DefaultTableLens abc() {
      return table(
         new Object[] {"a", "b", "c"}, new Object[] {1, "x", "p"}, new Object[] {1, "y", "q"},
         new Object[] {1, "z", "p"}, new Object[] {2, "x", "q"}, new Object[] {2, "y", "q"});
   }

   /**
    * A column map reports the sort columns it still shows, at their mapped index, and stops at
    * the first one it hides: a sort is a prefix, so what follows a hidden column is not sorted.
    */
   @Test
   void columnMapMapsTheSortColumnsOfItsBase() {
      SortFilter sorted = new SortFilter(abc(), new int[] {0, 1, 2});
      assertArrayEquals(new int[] {1, 0, 2},
                        new ColumnMapFilter(sorted, new int[] {1, 0, 2}).getSortCols());
      assertArrayEquals(new int[] {0, 1, 2},
                        new ColumnMapFilter(sorted, new int[] {0, 1, 2}).getSortCols());
      assertArrayEquals(new int[] {0}, new ColumnMapFilter(sorted, new int[] {0, 2}).getSortCols(),
                        "the hidden b ends the prefix, c is not a leading sort column any more");
      assertArrayEquals(new int[] {1}, new ColumnMapFilter(sorted, new int[] {2, 0}).getSortCols());
      assertArrayEquals(new int[0], new ColumnMapFilter(sorted, new int[] {1, 2}).getSortCols(),
                        "a hidden first sort column leaves no sorted prefix");
      assertArrayEquals(new int[] {0, 1},
                        new ColumnMapFilter(sorted, new int[] {0, 1}).getSortCols());
   }

   /**
    * A base sorted by (a, b, c) with b hidden is not sorted by (a, c), but the stale index of b
    * made the column map report (a, c-as-b, c) and the distinct reused the base.
    */
   @Test
   void distinctOverColumnMapWithHiddenMiddleSortColumn() {
      SortFilter sorted = new SortFilter(abc(), new int[] {0, 1, 2});
      ColumnMapFilter hidden = new ColumnMapFilter(sorted, new int[] {0, 2});
      DistinctTableLens distinct = new DistinctTableLens(hidden, new int[] {0, 1});
      List<List<Object>> result = rows(distinct);

      assertEquals(list(row("a", "c"), row(1, "p"), row(1, "q"), row(2, "q")), result);
   }

   /**
    * Random sorted bases, column maps (hide, reorder) and distinct columns: the distinct rows
    * are always the true distinct tuples. The seed is fixed, so a failure repeats.
    */
   @Test
   void distinctOverRandomColumnMapsOfSortedBases() {
      Random random = new Random(80549);

      for(int n = 0; n < 400; n++) {
         int rowCount = 2 + random.nextInt(14);
         Object[][] data = new Object[rowCount + 1][];
         data[0] = new Object[] {"a", "b", "c", "d"};

         for(int r = 1; r <= rowCount; r++) {
            data[r] = new Object[] {random.nextInt(3), random.nextInt(3), random.nextInt(3),
                                    random.nextInt(3)};
         }

         // a sort on a random prefix of a random order of the columns
         List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
         Collections.shuffle(order, random);
         int[] sortCols = order.subList(0, 1 + random.nextInt(4)).stream()
            .mapToInt(Integer::intValue).toArray();

         // a random map: some columns hidden, the rest in random order
         Collections.shuffle(order, random);
         int[] map = order.subList(0, 1 + random.nextInt(4)).stream()
            .mapToInt(Integer::intValue).toArray();

         // random distinct columns of the visible ones
         List<Integer> visible = new ArrayList<>();

         for(int i = 0; i < map.length; i++) {
            visible.add(i);
         }

         Collections.shuffle(visible, random);
         int[] distinctCols = visible.subList(0, 1 + random.nextInt(visible.size())).stream()
            .mapToInt(Integer::intValue).toArray();

         SortFilter sorted = new SortFilter(table(data), sortCols);
         ColumnMapFilter mapped = new ColumnMapFilter(sorted, map);
         DistinctTableLens distinct = new DistinctTableLens(mapped, distinctCols);

         Set<List<Object>> expected = new HashSet<>();

         for(int r = 1; r <= rowCount; r++) {
            List<Object> key = new ArrayList<>();

            for(int c : distinctCols) {
               key.add(data[r][map[c]]);
            }

            expected.add(key);
         }

         List<List<Object>> result = rows(distinct);
         Set<List<Object>> actual = new HashSet<>();

         for(List<Object> picked : result.subList(1, result.size())) {
            List<Object> key = new ArrayList<>();

            for(int c : distinctCols) {
               key.add(picked.get(c));
            }

            assertTrue(actual.add(key), "case " + n + ", a tuple is repeated, sort "
                       + Arrays.toString(sortCols) + ", map " + Arrays.toString(map)
                       + ", distinct " + Arrays.toString(distinctCols) + ": " + result);
         }

         assertEquals(expected, actual, "case " + n + ", sort " + Arrays.toString(sortCols)
                      + ", map " + Arrays.toString(map) + ", distinct "
                      + Arrays.toString(distinctCols));
      }
   }

   // ----------------------------------------------------------------------------------------
   // the background merge

   /**
    * A set lens whose merge adds a left row only when the test gives it a permit, so the
    * merge is in progress for as long as the test wants. The visitor holds the merged table
    * while it waits, so this lens is only for tests that do not cancel or dispose it.
    */
   private static final class GatedSet extends SetTableLens {
      GatedSet(TableLens left, TableLens right) {
         super(left, right);
      }

      @Override
      protected MergedTable.Visitor getVisitor(Pass pass) {
         return val -> {
            if(val.getRows(0).length == 0) {
               return;
            }

            permits.acquire();
            pass.add(new Row(0, val.getRows(0)[0]));
         };
      }

      final Semaphore permits = new Semaphore(0);
   }

   /**
    * A table that counts the calls of its owner. When {@code gated}, reading its first row
    * waits for the test.
    */
   private static final class CountingTable extends DefaultTableLens
      implements CancellableTableLens
   {
      CountingTable(Object[][] data, boolean gated) {
         super(data);
         this.gated = gated;
      }

      @Override
      public boolean moreRows(int row) {
         if(gated && row == 1) {
            entered.countDown();

            try {
               gate.await(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.moreRows(row);
      }

      @Override
      public void cancel() {
         cancels.incrementAndGet();
      }

      @Override
      public boolean isCancelled() {
         return cancels.get() > 0;
      }

      @Override
      public void dispose() {
         disposes.incrementAndGet();
      }

      final boolean gated;
      final CountDownLatch entered = new CountDownLatch(1);
      final CountDownLatch gate = new CountDownLatch(1);
      final AtomicInteger cancels = new AtomicInteger();
      final AtomicInteger disposes = new AtomicInteger();
   }

   private static CountingTable ten(boolean gated) {
      Object[][] data = new Object[11][];
      data[0] = new Object[] {"n"};

      for(int i = 1; i < data.length; i++) {
         data[i] = new Object[] {i};
      }

      return new CountingTable(data, gated);
   }

   @Test
   void setOperationBackgroundMerge() throws Exception {
      GatedSet set = new GatedSet(ten(false), ten(false));

      // the merge has not given a row yet: only the header is there and the count says loading
      assertEquals(-1 - 1, set.getRowCount());
      assertEquals(1, set.getHeaderRowCount());

      // a reader of the next row waits for it
      Future<Boolean> reader = executor.submit(() -> set.moreRows(1));

      assertThrows(TimeoutException.class, () -> reader.get(300, TimeUnit.MILLISECONDS),
                   "moreRows waits while the merge is slow");
      assertTrue(set.getRowCount() < 0, "the count of a table in progress is negative");

      // one row is merged: the reader is released and the count follows the loaded rows
      set.permits.release(1);
      assertTrue(reader.get(10, TimeUnit.SECONDS));
      assertEquals(-2 - 1, set.getRowCount());

      // the rest: moreRows(EOT) waits for the whole merge, then the count is the true one
      Future<Boolean> end = executor.submit(() -> set.moreRows(XTable.EOT));
      assertThrows(TimeoutException.class, () -> end.get(300, TimeUnit.MILLISECONDS));
      set.permits.release(100);
      assertFalse(end.get(10, TimeUnit.SECONDS), "EOT is never a row");
      assertEquals(11, set.getRowCount());
      assertFalse(set.moreRows(11), "past the last row, without waiting");
      assertEquals(10, rows(set).size() - 1);
   }






   /**
    * A reader that asks for a row of a lens that was cancelled while it was loading its tables
    * is not kept waiting.
    */

   /**
    * A reader that waits for a row of a lens that was cancelled while it was merging must be
    * released: the merge was discarded and will never give it the row. The test fabricates, by
    * cancelling from the merge itself, the state a cancel from another thread leaves a lens in
    * when it lands mid-merge, and which hangs readers there.
    */

   /**
    * The merge of a lens cancelled while merging completes the rows found so far, and a repeat
    * cancel (the viewer and the sandbox both cancel) must not report them as the whole table.
    */

   // ----------------------------------------------------------------------------------------
   // invalidate and dispose against a running merge (bug #80548)

   /**
    * The merge of a pass that invalidate() replaced must not add its rows to the next pass:
    * invalidate() no longer waits for the whole visit before the next read can start one.
    */
}
