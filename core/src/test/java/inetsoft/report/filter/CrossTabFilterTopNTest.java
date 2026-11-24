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
package inetsoft.report.filter;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Top N / Bottom N on a crosstab row or column dimension, with and without "Group all others
 * together", compared with independent computations over the base table.
 *
 * <ul>
 * <li>With Others: the groups outside the top N are relabeled "Others" in a copy of the base
 * table and aggregated by a plain crosstab; every cell of the Top N crosstab (details,
 * Others, subtotals, grand totals) must equal the cell of that crosstab.</li>
 * <li>Without Others: a detail cell must equal the cell of the crosstab of all rows; a
 * total must equal the total of all rows, or, when "aggregate top N only" is on, of the rows
 * that stayed in the top N.</li>
 * </ul>
 *
 * The ranking is by Sum of the first measure within the parent group (rows: within the outer
 * dimension; columns: within the year), computed here with a plain loop. The wiring is the one
 * AbstractCrosstabVSAQuery uses: setRowTopN / setColTopN with the ranking's group-others flag
 * and setAggregateTopN.
 *
 * Callers: CrosstabVSAQuery (viewer crosstab) through AbstractCrosstabVSAQuery:1097 and 1100;
 * the Others on an outer dimension is not offered by the binding editor and not covered.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterTopNTest {
   // columns: 0 Outer, 1 Cat, 2 Year, 3 Month, 4..8 measures
   private static final String[] MEASURES = { "Sum", "Count", "Avg", "Max", "Min" };
   private static final int CAT = 1;
   private static final int MONTH = 3;

   // ---- values ----

   static Stream<org.junit.jupiter.params.provider.Arguments> rowConfigs() {
      List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

      for(int rl = 1; rl <= 2; rl++) {
         for(int cl = 1; cl <= 2; cl++) {
            for(int n : new int[] { 1, 3 }) {
               for(boolean reverse : new boolean[] { false, true }) {
                  for(boolean others : new boolean[] { true, false }) {
                     for(boolean aggTopN : new boolean[] { false, true }) {
                        list.add(org.junit.jupiter.params.provider.Arguments.of(
                           rl, cl, n, reverse, others, aggTopN));
                     }
                  }
               }
            }
         }
      }

      return list.stream();
   }

   /** Top N on the innermost row dimension. */
   @ParameterizedTest(name = "rows={0} cols={1} n={2} bottom={3} others={4} aggregateTopN={5}")
   @MethodSource("rowConfigs")
   void rowTopNMatchesTheIndependentComputation(int rowLevels, int colLevels, int n,
                                                boolean reverse, boolean others, boolean aggTopN)
   {
      List<Object[]> rows = baseRows();
      boolean parentLevel = rowLevels == 2;
      Map<String, Set<Object>> kept = keptGroups(rows, CAT, 0, n, reverse, parentLevel);
      Map<String, Object> actual =
         cells(build(rows, rowLevels, colLevels, n, null, reverse, others, aggTopN));
      Map<String, Object> expected = new TreeMap<>();

      if(others) {
         expected = cells(build(relabelOthers(rows, CAT, kept, 0, parentLevel), rowLevels,
                                colLevels, null, null, false, false, false));
      }
      else {
         Map<String, Object> all = cells(build(rows, rowLevels, colLevels, null, null, false,
                                               false, false));
         Map<String, Object> top = cells(build(onlyKept(rows, CAT, kept, 0, parentLevel),
                                               rowLevels, colLevels, null, null, false, false, false));

         for(String key : actual.keySet()) {
            boolean total = isTotal(key, rowLevels, colLevels);
            expected.put(key, (total && aggTopN ? top : all).get(key));
         }
      }

      assertSame(expected, actual, others || aggTopN);
   }

   static Stream<org.junit.jupiter.params.provider.Arguments> columnConfigs() {
      List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

      for(int rl = 1; rl <= 2; rl++) {
         for(int cl = 1; cl <= 2; cl++) {
            for(int n : new int[] { 1, 3 }) {
               for(boolean reverse : new boolean[] { false, true }) {
                  for(boolean aggTopN : new boolean[] { false, true }) {
                     list.add(org.junit.jupiter.params.provider.Arguments.of(
                        rl, cl, n, reverse, aggTopN));
                  }
               }
            }
         }
      }

      return list.stream();
   }

   /** Top N on the innermost column dimension, without Others. */
   @ParameterizedTest(name = "rows={0} cols={1} n={2} bottom={3} aggregateTopN={4}")
   @MethodSource("columnConfigs")
   void columnTopNMatchesTheIndependentComputation(int rowLevels, int colLevels, int n,
                                                   boolean reverse, boolean aggTopN)
   {
      List<Object[]> rows = baseRows();
      boolean parentLevel = colLevels == 2;
      Map<String, Set<Object>> kept = keptGroups(rows, MONTH, 2, n, reverse, parentLevel);
      Map<String, Object> actual =
         cells(build(rows, rowLevels, colLevels, null, n, reverse, false, aggTopN));
      Map<String, Object> all = cells(build(rows, rowLevels, colLevels, null, null, false, false,
                                            false));
      Map<String, Object> top = cells(build(onlyKept(rows, MONTH, kept, 2, parentLevel),
                                            rowLevels, colLevels, null, null, false, false, false));
      Map<String, Object> expected = new TreeMap<>();

      for(String key : actual.keySet()) {
         boolean total = isTotal(key, rowLevels, colLevels);
         // a row whose data all lies in removed columns stays as an empty row; it is not in the
         // crosstab of the kept rows
         Map<String, Object> source = total && aggTopN ? top : all;
         expected.put(key, source.containsKey(key) ? source.get(key) : actual.get(key));
      }

      assertSame(expected, actual, aggTopN);
   }

   /** Top N with Others on the innermost column dimension. */
   @ParameterizedTest(name = "rows={0} cols={1} n={2} bottom={3} aggregateTopN={4}")
   @MethodSource("columnConfigs")
   void columnOthersMatchTheIndependentComputation(int rowLevels, int colLevels, int n,
                                                   boolean reverse, boolean aggTopN)
   {
      List<Object[]> rows = baseRows();
      boolean parentLevel = colLevels == 2;
      Map<String, Set<Object>> kept = keptGroups(rows, MONTH, 2, n, reverse, parentLevel);
      Map<String, Object> actual =
         cells(build(rows, rowLevels, colLevels, null, n, reverse, true, aggTopN));
      Map<String, Object> expected =
         cells(build(relabelOthers(rows, MONTH, kept, 2, parentLevel), rowLevels, colLevels, null,
                     null, false, false, false));

      assertSame(expected, actual, true);
   }

   /** The Others group is the last row, after every kept group of its parent. */
   @ParameterizedTest(name = "rows={0}")
   @ValueSource(ints = { 1, 2 })
   void othersIsTheLastRowOfItsGroup(int rowLevels) {
      CrossTabFilter f = build(baseRows(), rowLevels, 1, 2, null, false, true, false);
      List<String> labels = new ArrayList<>();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         CrossFilter.Tuple tuple = f.getRowTuple(r);

         if(tuple.size() == rowLevels) {
            labels.add(tuple instanceof CrossFilter.MergedTuple ? "Others" : "detail");
         }
         else if(tuple.size() > 0) {
            labels.add("subtotal");
         }
      }

      for(int i = 0; i < labels.size(); i++) {
         if("Others".equals(labels.get(i))) {
            assertTrue(i + 1 == labels.size() || !"detail".equals(labels.get(i + 1)),
                       "a detail row follows Others: " + labels);
         }
      }

      assertTrue(labels.contains("Others"), labels.toString());
   }

   // ---- where the Others row goes ----

   /**
    * Cat x1..x6 with totals 500, 40, 300, 20, 100, 60: the top two are x1 (500) and x3 (300), the
    * Others group is 220.
    */
   private static List<String> orderOfRows(boolean sortOthersLast, Boolean sortByValueAscending) {
      Object[][] data = {
         { "Cat", "Month", "V" },
         { "x1", "m1", 500.0 }, { "x2", "m1", 40.0 }, { "x3", "m1", 300.0 },
         { "x4", "m1", 20.0 }, { "x5", "m1", 100.0 }, { "x6", "m1", 60.0 }
      };
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), new int[] { 0 },
                                            new int[] { 1 }, new int[] { 2 },
                                            new Formula[] { new SumFormula() });
      f.setRowHeaders(List.of("Cat"));
      f.setColHeaders(List.of("Month"));
      f.setSuppressRowGrandTotal(true);
      f.setSuppressColumnGrandTotal(true);
      f.setSummarySideBySide(true);
      f.setAggregateTopN(true);
      f.setRowTopN(0, 0, 2, false, true);
      f.setSortOthersLast(sortOthersLast);

      if(sortByValueAscending != null) {
         f.setRowSortByValInfo(0, 0, sortByValueAscending);
      }

      f.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         Object[] tuple = f.getRowTuple(r).getRow();
         rows.add(tuple[0] + "=" + ((Number) f.getObject(r, f.getHeaderColCount())).intValue());
      }

      return rows;
   }

   @ParameterizedTest(name = "sortByValueAscending={0}")
   @ValueSource(strings = { "none", "asc", "desc" })
   void othersIsLastWhenSortOthersLastIsOn(String sort) {
      List<String> rows = orderOfRows(true, sortFlag(sort));

      assertEquals("Others=220", rows.get(rows.size() - 1), rows.toString());
      assertEquals(3, rows.size());
   }

   /** With the option off, Others is sorted by its value like any group. */
   @Test
   void othersIsSortedByValueWhenSortOthersLastIsOff() {
      assertEquals(List.of("Others=220", "x3=300", "x1=500"), orderOfRows(false, true));
      assertEquals(List.of("x1=500", "x3=300", "Others=220"), orderOfRows(false, false));
   }

   /** With the option off and no sort by value, the label "Others" is sorted as a label. */
   @Test
   void othersIsSortedByItsLabelWhenSortOthersLastIsOffAndThereIsNoSortByValue() {
      assertEquals(List.of("Others=220", "x1=500", "x3=300"), orderOfRows(false, null));
   }

   private static Boolean sortFlag(String sort) {
      return "none".equals(sort) ? null : "asc".equals(sort);
   }

   // ---- cells without data ----

   private static CrossTabFilter emptyCellCrosstab(Integer n, boolean others, boolean aggTopN,
                                                   boolean fillZero)
   {
      // the cell a2 x m1 has no base row
      Object[][] data = {
         { "Cat", "Month", "Sales", "Qty", "Dc", "Pr", "Hi", "Lo" },
         { "a1", "m1", 10.0, 1.0, 1.0, 5.0, 7.0, 3.0 },
         { "a1", "m1", 20.0, 2.0, 2.0, 6.0, 8.0, 4.0 },
         { "a2", "m2", 30.0, 3.0, 3.0, 7.0, 9.0, 5.0 },
         { "a3", "m1", 40.0, 4.0, 4.0, 8.0, 1.0, 2.0 }
      };
      CrossTabFilter f = new CrossTabFilter(
         new DefaultTableLens(data), new int[] { 0 }, new int[] { 1 },
         new int[] { 2, 3, 4, 5, 6, 7 },
         new Formula[] { new SumFormula(), new CountFormula(), new DistinctCountFormula(),
                         new AverageFormula(), new MaxFormula(), new MinFormula() });
      f.setRowHeaders(List.of("Cat"));
      f.setColHeaders(List.of("Month"));
      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(true);
      f.setFillBlankWithZero(fillZero);
      f.setAggregateTopN(aggTopN);

      if(n != null) {
         f.setRowTopN(0, 0, n, false, others);
      }

      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   /** The value of the empty cell a2 x m1 for the measure with the data path name. */
   private static Object emptyCell(CrossTabFilter f, String measure) {
      TableDataDescriptor desc = f.getDescriptor();
      int row = -1;

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         if("[a2]".equals(Arrays.toString(f.getRowTuple(r).getRow()))) {
            row = r;
         }
      }

      assertTrue(row >= 0, "the row a2 must exist");

      for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
         if("[m1]".equals(Arrays.toString(f.getColTuple(c).getRow())) &&
            measure.equals(lastPath(desc.getCellDataPath(row, c))))
         {
            return f.getObject(row, c);
         }
      }

      throw new AssertionError("no cell for " + measure);
   }

   private static final String[] ALL = { "Sales", "Qty", "Dc", "Pr", "Hi", "Lo" };
   private static final String[] COUNTS = { "Qty", "Dc" };
   private static final String[] OTHERS_SAFE = { "Sales", "Pr", "Hi", "Lo" };

   static Stream<org.junit.jupiter.params.provider.Arguments> emptyCellOptions() {
      return Stream.of(
         org.junit.jupiter.params.provider.Arguments.of(null, false, false),
         org.junit.jupiter.params.provider.Arguments.of(100, false, false),
         org.junit.jupiter.params.provider.Arguments.of(100, true, false),
         org.junit.jupiter.params.provider.Arguments.of(100, false, true),
         org.junit.jupiter.params.provider.Arguments.of(100, true, true));
   }

   /**
    * Cells without data are blank for Sum, Average, Max and Min: without Top N, with a Top N that
    * keeps everything, with Others and with "aggregate top N" (which the viewer always sets).
    */
   @ParameterizedTest(name = "topN={0} others={1} aggregateTopN={2}")
   @MethodSource("emptyCellOptions")
   void emptyCellsOfTheNonCountFormulasAreBlank(Integer n, boolean others, boolean aggTopN) {
      CrossTabFilter f = emptyCellCrosstab(n, others, aggTopN, false);

      for(String measure : OTHERS_SAFE) {
         assertNull(emptyCell(f, measure), measure);
      }
   }

   /**
    * Without a Top N, and with one that does not aggregate top N or group Others, Count is blank:
    * pinned current behavior, Bug #82226 asks the developers whether blank or 0 is intended.
    */
   @ParameterizedTest(name = "topN={0}")
   @ValueSource(ints = { 0, 100 })
   void emptyCountCellsAreBlankWithoutAggregateTopNOrOthers(int n) {
      CrossTabFilter f = emptyCellCrosstab(n == 0 ? null : n, false, false, false);

      for(String measure : COUNTS) {
         assertNull(emptyCell(f, measure), measure);
      }
   }

   /**
    * Count and Count Distinct give the same value in a cell without data with and without a Top N.
    * The value itself (0 or blank) is for the developers to decide, so the expected value is the
    * one the crosstab without Top N gives. With Others, or with "aggregate top N" (always on in the
    * viewer), the cell holds an empty formula clone and shows 0.
    */
   @ParameterizedTest(name = "others={0} aggregateTopN={1}")
   @MethodSource("emptyCountOptions")
   @Disabled("Bug #82226 (Dev feedback needed): a cell without data shows 0 for Count and Count "
      + "Distinct when the crosstab has a Top N with aggregate top N (always on in the viewer) or "
      + "with Others, and blank without a Top N; CrossTabFilter.resetGrandTotal puts an empty "
      + "formula clone into every missing cell")
   void emptyCountCellsAreTheSameWithAndWithoutTopN(boolean others, boolean aggTopN) {
      CrossTabFilter withoutTopN = emptyCellCrosstab(null, false, false, false);
      CrossTabFilter withTopN = emptyCellCrosstab(100, others, aggTopN, false);

      for(String measure : COUNTS) {
         assertEquals(emptyCell(withoutTopN, measure), emptyCell(withTopN, measure), measure);
      }
   }

   static Stream<org.junit.jupiter.params.provider.Arguments> emptyCountOptions() {
      return Stream.of(
         org.junit.jupiter.params.provider.Arguments.of(true, false),
         org.junit.jupiter.params.provider.Arguments.of(false, true),
         org.junit.jupiter.params.provider.Arguments.of(true, true));
   }

   /** "Fill blank with zero" turns every empty cell into 0, with or without Top N. */
   @ParameterizedTest(name = "topN={0} others={1} aggregateTopN={2}")
   @MethodSource("emptyCellOptions")
   void fillBlankWithZeroFillsEveryEmptyCell(Integer n, boolean others, boolean aggTopN) {
      CrossTabFilter f = emptyCellCrosstab(n, others, aggTopN, true);

      for(String measure : ALL) {
         Object value = emptyCell(f, measure);
         assertInstanceOf(Number.class, value, measure);
         assertEquals(0.0, ((Number) value).doubleValue(), measure);
      }
   }

   // ---- crosstab under test ----

   private static CrossTabFilter build(List<Object[]> rowsIn, int rowLevels, int colLevels,
                                       Integer rowTopN, Integer colTopN, boolean reverse,
                                       boolean others, boolean aggTopN)
   {
      List<Object[]> all = new ArrayList<>();
      all.add(new Object[] { "Outer", "Cat", "Year", "Month", "M1", "M2", "M3", "M4", "M5" });
      all.addAll(rowsIn);
      int[] rows = rowLevels == 1 ? new int[] { CAT } : new int[] { 0, CAT };
      int[] cols = colLevels == 1 ? new int[] { MONTH } : new int[] { 2, MONTH };
      Formula[] formulas = {
         new SumFormula(), new CountFormula(), new AverageFormula(), new MaxFormula(),
         new MinFormula()
      };
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(all.toArray(new Object[0][])),
                                            rows, cols, new int[] { 4, 5, 6, 7, 8 }, formulas);
      f.setRowHeaders(rowLevels == 1 ? List.of("Cat") : List.of("Outer", "Cat"));
      f.setColHeaders(colLevels == 1 ? List.of("Month") : List.of("Year", "Month"));

      if(rowLevels == 2) {
         f.setSuppressRowGroupTotal(false, 0);
      }

      if(colLevels == 2) {
         f.setSuppressColumnGroupTotal(false, 0);
      }

      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(true);
      f.setAggregateTopN(aggTopN);

      if(rowTopN != null) {
         f.setRowTopN(rowLevels - 1, 0, rowTopN, reverse, others);
      }

      if(colTopN != null) {
         f.setColTopN(colLevels - 1, 0, colTopN, reverse, others);
      }

      f.setSortOthersLast(true);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   // ---- independent computation ----

   /** Outer {b1, b2} x Cat {a1..a6} x Year {2022, 2023} x Month {1..4}, sparse, no ties in Sum. */
   private static List<Object[]> baseRows() {
      Random rnd = new Random(1);
      List<Object[]> rows = new ArrayList<>();
      int cats = 6;

      for(String outer : new String[] { "b1", "b2" }) {
         for(int c = 0; c < cats; c++) {
            for(int year = 2022; year <= 2023; year++) {
               for(int month = 1; month <= 4; month++) {
                  for(int k = 0; k < 1 + rnd.nextInt(2); k++) {
                     if(rnd.nextInt(5) == 0) {
                        continue;
                     }

                     double base = (cats - c) * 1000 + ("b1".equals(outer) ? 0 : 70) * (c + 1) +
                        month * 13 + (year - 2022) * 97;
                     rows.add(new Object[] {
                        outer, "a" + (c + 1), year, month, base + rnd.nextInt(40),
                        (double) (1 + rnd.nextInt(9)), (double) rnd.nextInt(500),
                        (double) rnd.nextInt(500), (double) rnd.nextInt(500)
                     });
                  }
               }
            }
         }
      }

      return rows;
   }

   /**
    * The groups of dimension {@code dim} that stay in the top N (or bottom N) by Sum of the first
    * measure, per value of the parent dimension, or in total when there is no parent.
    */
   private static Map<String, Set<Object>> keptGroups(List<Object[]> rows, int dim, int parentDim,
                                                       int n, boolean reverse, boolean hasParent)
   {
      Map<String, Map<Object, Double>> totals = new TreeMap<>();

      for(Object[] row : rows) {
         String parent = hasParent ? String.valueOf(row[parentDim]) : "*";
         totals.computeIfAbsent(parent, k -> new HashMap<>())
            .merge(row[dim], (Double) row[4], Double::sum);
      }

      Map<String, Set<Object>> kept = new HashMap<>();

      for(Map.Entry<String, Map<Object, Double>> e : totals.entrySet()) {
         List<Map.Entry<Object, Double>> list = new ArrayList<>(e.getValue().entrySet());
         list.sort((a, b) -> reverse ? Double.compare(a.getValue(), b.getValue())
            : Double.compare(b.getValue(), a.getValue()));
         Set<Object> set = new HashSet<>();

         for(int i = 0; i < Math.min(n, list.size()); i++) {
            set.add(list.get(i).getKey());
         }

         kept.put(e.getKey(), set);
      }

      return kept;
   }

   private static List<Object[]> relabelOthers(List<Object[]> rows, int dim,
                                               Map<String, Set<Object>> kept, int parentDim,
                                               boolean hasParent)
   {
      List<Object[]> out = new ArrayList<>();

      for(Object[] row : rows) {
         Object[] copy = row.clone();
         String parent = hasParent ? String.valueOf(row[parentDim]) : "*";

         if(!kept.get(parent).contains(row[dim])) {
            copy[dim] = CrossFilter.OTHERS;
         }

         out.add(copy);
      }

      return out;
   }

   private static List<Object[]> onlyKept(List<Object[]> rows, int dim,
                                          Map<String, Set<Object>> kept, int parentDim,
                                          boolean hasParent)
   {
      List<Object[]> out = new ArrayList<>();

      for(Object[] row : rows) {
         String parent = hasParent ? String.valueOf(row[parentDim]) : "*";

         if(kept.get(parent).contains(row[dim])) {
            out.add(row);
         }
      }

      return out;
   }

   /** Every measure cell, keyed by measure, row tuple and column tuple. */
   private static Map<String, Object> cells(CrossTabFilter f) {
      Map<String, Object> map = new TreeMap<>();
      TableDataDescriptor desc = f.getDescriptor();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            String measure = lastPath(desc.getCellDataPath(r, c));

            if(Arrays.asList("M1", "M2", "M3", "M4", "M5").contains(measure)) {
               map.put(measure + " " + Arrays.toString(f.getRowTuple(r).getRow()) + " x " +
                          Arrays.toString(f.getColTuple(c).getRow()), f.getObject(r, c));
            }
         }
      }

      return map;
   }

   private static boolean isTotal(String key, int rowLevels, int colLevels) {
      String[] parts = key.substring(key.indexOf('[')).split(" x ");
      int rowSize = tupleSize(parts[0]);
      int colSize = tupleSize(parts[1]);
      return rowSize < rowLevels || colSize < colLevels;
   }

   private static int tupleSize(String tuple) {
      return "[]".equals(tuple) ? 0 : tuple.split(",").length;
   }

   private static String lastPath(TableDataPath path) {
      return path == null || path.getPath().length == 0 ? null :
         path.getPath()[path.getPath().length - 1];
   }

   /**
    * Every cell of actual equals the cell of expected. A Count cell that has no data may be blank or
    * 0 when the crosstab ranks with Others or with "aggregate top N" (Bug #82226 is tested on its
    * own).
    */
   private static void assertSame(Map<String, Object> expected, Map<String, Object> actual,
                                  boolean emptyCountMayBeZero)
   {
      List<String> problems = new ArrayList<>();

      for(Map.Entry<String, Object> e : actual.entrySet()) {
         if(!expected.containsKey(e.getKey())) {
            problems.add(e.getKey() + " has no expected cell");
            continue;
         }

         Object want = expected.get(e.getKey());
         Object have = e.getValue();
         boolean emptyCount = emptyCountMayBeZero && e.getKey().startsWith("M2 ") && want == null &&
            have instanceof Number && ((Number) have).doubleValue() == 0;

         if(!emptyCount && !same(want, have)) {
            problems.add(e.getKey() + " expected=" + want + " actual=" + have);
         }
      }

      assertTrue(problems.isEmpty(), problems.size() + " cells differ:\n"
         + String.join("\n", problems.subList(0, Math.min(10, problems.size()))));
      assertFalse(actual.isEmpty());
   }

   private static boolean same(Object want, Object have) {
      if(want == null || have == null) {
         return want == have;
      }

      if(want instanceof Number && have instanceof Number) {
         double w = ((Number) want).doubleValue();
         return Math.abs(w - ((Number) have).doubleValue()) < 1e-6 * Math.max(1, Math.abs(w));
      }

      return want.equals(have);
   }
}
