/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.util.swap.SwapLostTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossTabFilterTest {
   @Test
   public void testSerialize() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{ new SumFormula(), new AverageFormula() };
      CrossTabFilter originalTable = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                        dcol, formulas);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossTabFilter.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeCalcFieldFormula() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      String expr = "new Date().getTime()";
      Worksheet ws = new Worksheet();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      CalcFieldFormula calcFieldFormula = new CalcFieldFormula(expr, new String[0], new Formula[0],
                                                               new int[0], box.getScriptEnv(),
                                                               box.getScope());
      Formula[] formulas = new Formula[]{ new SumFormula(), calcFieldFormula };
      CrossTabFilter originalTable = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                        dcol, formulas);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossTabFilter.class, deserializedTable.getClass());
   }

   /**
    * A lost swap file of the base fails the read of the crosstab with the swap file read
    * failure, not with a NullPointerException of the missing data (bug #77651). Nothing is
    * kept, so the next read fails the same way while the file is lost.
    */
   @Test
   public void lostSwapFileOfTheBaseFailsEveryRead() throws Exception {
      SwapFileReadException lost = swapLost();
      CrossTabFilter crosstab = crosstab(new LostTable(values(40), 21, lost));

      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)), "first read");
      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)), "second read");
   }

   /**
    * A wrapped lost swap file is found in the cause chain.
    */
   @Test
   public void wrappedLostSwapFileOfTheBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      CrossTabFilter crosstab =
         crosstab(new LostTable(values(40), 21, new RuntimeException("wrapped", lost)));

      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)));
   }

   private static CrossTabFilter crosstab(TableLens base) {
      return new CrossTabFilter(base, new int[] { 0 }, new int[0], new int[] { 1 },
                                new Formula[] { new SumFormula() });
   }

   /**
    * {@code rows} rows of {@code key, value}, value {@code i} for row {@code i}.
    */
   private static Object[][] values(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }

   /**
    * The value of every cell of a plain crosstab, compared with an independent computation: the
    * answer for a cell is the aggregate of the base rows that match the cell's row and column
    * tuple, a subtotal is the aggregate of the rows that match a shorter tuple, and a grand total
    * is the aggregate of all rows. The oracle is a plain loop over the base table; it shares no
    * code with {@link CrossTabFilter}.
    *
    * The crosstab is wired as the viewsheet query wires it: header names, side by side or
    * stacked measures, subtotals and grand totals shown. The base table is sparse (not every
    * row / column combination exists), so empty cells and uneven groups are part of every run.
    *
    * Formulas: Sum, Count, Average, Max and Min, one measure column each.
    */
   @Nested
   class GoldenValues {
      // columns: 0..3 dimensions A, B, C, D; 4..8 measures M1..M5
      private static final String[] DIMS = { "A", "B", "C", "D" };
      private static final String[] MEASURES = { "M1", "M2", "M3", "M4", "M5" };
      private static final String[] KINDS = { "Sum", "Count", "Average", "Max", "Min" };

      static Stream<org.junit.jupiter.params.provider.Arguments> layouts() {
         List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

         for(int rl = 1; rl <= 2; rl++) {
            for(int cl = 1; cl <= 2; cl++) {
               for(boolean sideBySide : new boolean[] { true, false }) {
                  for(boolean subtotals : new boolean[] { true, false }) {
                     list.add(org.junit.jupiter.params.provider.Arguments.of(
                        rl, cl, sideBySide, subtotals));
                  }
               }
            }
         }

         return list.stream();
      }

      @ParameterizedTest(name = "rows={0} cols={1} sideBySide={2} subtotals={3}")
      @MethodSource("layouts")
      void everyCellMatchesTheIndependentAggregate(int rowLevels, int colLevels, boolean sideBySide,
                                                   boolean subtotals)
      {
         Object[][] data = baseTable(new Random(rowLevels * 100 + colLevels * 10 + (sideBySide ? 1 : 0)));
         CrossTabFilter f = build(data, rowLevels, colLevels, sideBySide, subtotals);

         List<String> problems = new ArrayList<>();
         Set<String> rowTuples = new TreeSet<>();
         Set<String> colTuples = new TreeSet<>();
         TableDataDescriptor desc = f.getDescriptor();

         for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
            Object[] rowKey = f.getRowTuple(r).getRow();
            rowTuples.add(Arrays.toString(rowKey));

            for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
               Object[] colKey = f.getColTuple(c).getRow();
               colTuples.add(Arrays.toString(colKey));
               String measure = lastPath(desc.getCellDataPath(r, c));
               int m = Arrays.asList(MEASURES).indexOf(measure);

               if(m < 0) {
                  problems.add("cell (" + r + "," + c + ") has no measure path: " + measure);
                  continue;
               }

               Double expected = aggregate(data, m, rowKey, colKey);
               Object actual = f.getObject(r, c);

               if(!same(expected, actual)) {
                  problems.add(String.format("%s row=%s col=%s expected=%s actual=%s",
                                             KINDS[m] + "(" + measure + ")", Arrays.toString(rowKey),
                                             Arrays.toString(colKey), expected, actual));
               }
            }
         }

         assertTrue(problems.isEmpty(), problems.size() + " cells differ:\n"
            + String.join("\n", problems.subList(0, Math.min(15, problems.size()))));

         assertEquals(expectedTuples(data, 0, rowLevels, subtotals), rowTuples, "row tuples");
         assertEquals(expectedTuples(data, 2, colLevels, subtotals), colTuples, "column tuples");
      }

      // ---- crosstab under test ----

      private static CrossTabFilter build(Object[][] data, int rowLevels, int colLevels,
                                          boolean sideBySide, boolean subtotals)
      {
         int[] rows = new int[rowLevels];
         int[] cols = new int[colLevels];

         for(int i = 0; i < rowLevels; i++) {
            rows[i] = i;
         }

         for(int i = 0; i < colLevels; i++) {
            cols[i] = 2 + i;
         }

         int[] dcol = { 4, 5, 6, 7, 8 };
         Formula[] formulas = {
            new SumFormula(), new CountFormula(), new AverageFormula(), new MaxFormula(),
            new MinFormula()
         };
         CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), rows, cols, dcol, formulas);
         List<String> rowHeaders = new ArrayList<>();
         List<String> colHeaders = new ArrayList<>();

         for(int i = 0; i < rowLevels; i++) {
            rowHeaders.add(DIMS[i]);
         }

         for(int i = 0; i < colLevels; i++) {
            colHeaders.add(DIMS[2 + i]);
         }

         f.setRowHeaders(rowHeaders);
         f.setColHeaders(colHeaders);

         for(int level = 0; level < rowLevels - 1; level++) {
            f.setSuppressRowGroupTotal(!subtotals, level);
         }

         for(int level = 0; level < colLevels - 1; level++) {
            f.setSuppressColumnGroupTotal(!subtotals, level);
         }

         f.setSuppressRowGrandTotal(false);
         f.setSuppressColumnGrandTotal(false);
         f.setSummarySideBySide(sideBySide);
         f.moreRows(Integer.MAX_VALUE);
         return f;
      }

      // ---- independent oracle ----

      /** A sparse table: not every (A, B, C, D) combination has a row, some have several. */
      private static Object[][] baseTable(Random rnd) {
         String[] a = { "a1", "a2", "a3" };
         String[] b = { "b1", "b2" };
         String[] c = { "c1", "c2", "c3" };
         String[] d = { "d1", "d2" };
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { "A", "B", "C", "D", "M1", "M2", "M3", "M4", "M5" });

         for(int i = 0; i < 70; i++) {
            rows.add(new Object[] {
               a[rnd.nextInt(a.length)], b[rnd.nextInt(b.length)], c[rnd.nextInt(c.length)],
               d[rnd.nextInt(d.length)],
               (double) rnd.nextInt(100), (double) rnd.nextInt(100), (double) rnd.nextInt(100),
               (double) rnd.nextInt(100), (double) rnd.nextInt(100)
            });
         }

         return rows.toArray(new Object[0][]);
      }

      /**
       * The aggregate of measure m over the base rows matching the first rowKey.length row
       * dimensions and the first colKey.length column dimensions; null when no row matches.
       */
      private static Double aggregate(Object[][] data, int m, Object[] rowKey, Object[] colKey) {
         List<Double> values = new ArrayList<>();

         for(int i = 1; i < data.length; i++) {
            if(matches(data[i], 0, rowKey) && matches(data[i], 2, colKey)) {
               values.add((Double) data[i][4 + m]);
            }
         }

         if(values.isEmpty()) {
            return null;
         }

         switch(KINDS[m]) {
         case "Sum":
            return values.stream().mapToDouble(Double::doubleValue).sum();
         case "Count":
            return (double) values.size();
         case "Average":
            return values.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
         case "Max":
            return values.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
         default:
            return values.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
         }
      }

      private static boolean matches(Object[] row, int firstDim, Object[] key) {
         for(int i = 0; i < key.length; i++) {
            if(!Objects.equals(row[firstDim + i], key[i])) {
               return false;
            }
         }

         return true;
      }

      /**
       * The tuples the axis must have: the full tuple of every existing combination, the prefix
       * tuples when subtotals are shown, and the empty tuple for the grand total.
       */
      private static Set<String> expectedTuples(Object[][] data, int firstDim, int levels,
                                                boolean subtotals)
      {
         Set<String> set = new TreeSet<>();

         for(int i = 1; i < data.length; i++) {
            for(int len = 0; len <= levels; len++) {
               boolean grand = len == 0;
               boolean full = len == levels;
               boolean prefix = !grand && !full;

               if(grand || full || (prefix && subtotals)) {
                  set.add(Arrays.toString(Arrays.copyOfRange(data[i], firstDim, firstDim + len)));
               }
            }
         }

         return set;
      }

      private static boolean same(Double expected, Object actual) {
         if(expected == null) {
            return actual == null || (actual instanceof Number && ((Number) actual).doubleValue() == 0)
               || "".equals(actual);
         }

         return actual instanceof Number
            && Math.abs(((Number) actual).doubleValue() - expected) < 1e-9;
      }

      private static String lastPath(TableDataPath path) {
         return path == null || path.getPath().length == 0 ? null :
            path.getPath()[path.getPath().length - 1];
      }
   }

   /**
    * The server's maximum number of query rows ({@code query.runtime.maxrow}, Enterprise Manager
    * Performance settings, 0 means no limit) cuts a crosstab while the filter aggregates. The
    * result is allowed to be incomplete, a warning tells the user so, but what is shown must
    * agree with itself: a row's total is the sum of its shown cells, and a grand total is the sum
    * of the shown rows.
    *
    * Caller: CrossTabFilter.insertPair through genTuples, for every report or viewsheet crosstab;
    * the setting is read once per filter ({@code Util.getRuntimeMaxRows}).
    */
   @Nested
   class RuntimeMaxRows {
      private static final String PROPERTY = "query.runtime.maxrow";
      private String original;

      @BeforeEach
      void rememberTheSetting() {
         original = SreeEnv.getProperty(PROPERTY);
      }

      @AfterEach
      void restoreTheSetting() {
         SreeEnv.setProperty(PROPERTY, original == null ? "" : original);
      }

      /** c1..c6 x m1..m3, value = category * 10 + month, base rows ordered by month. */
      private static CrossTabFilter crosstab(boolean groupRows) {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { "Group", "Cat", "Month", "V" });

         for(int month = 1; month <= 3; month++) {
            for(int cat = 1; cat <= 6; cat++) {
               rows.add(new Object[] { cat <= 3 ? "g1" : "g2", "c" + cat, "m" + month,
                                       (double) (cat * 10 + month) });
            }
         }

         int[] rowh = groupRows ? new int[] { 0, 1 } : new int[] { 1 };
         CrossTabFilter f = new CrossTabFilter(
            new DefaultTableLens(rows.toArray(new Object[0][])), rowh, new int[] { 2 },
            new int[] { 3 }, new Formula[] { new SumFormula() });
         f.setRowHeaders(groupRows ? List.of("Group", "Cat") : List.of("Cat"));
         f.setColHeaders(List.of("Month"));

         if(groupRows) {
            f.setSuppressRowGroupTotal(false, 0);
         }

         f.setSuppressRowGrandTotal(false);
         f.setSuppressColumnGrandTotal(false);
         f.setSummarySideBySide(true);
         f.moreRows(Integer.MAX_VALUE);
         return f;
      }

      private static CrossTabFilter crosstab(boolean groupRows, int maxRows) {
         SreeEnv.setProperty(PROPERTY, String.valueOf(maxRows));
         return crosstab(groupRows);
      }

      private static double number(Object value) {
         return value instanceof Number ? ((Number) value).doubleValue() : 0;
      }

      static Stream<org.junit.jupiter.params.provider.Arguments> limits() {
         List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

         for(boolean groupRows : new boolean[] { false, true }) {
            for(int limit = 2; limit <= 6; limit++) {
               list.add(org.junit.jupiter.params.provider.Arguments.of(groupRows, limit));
            }
         }

         return list.stream();
      }

      /** A limit above the number of tuples changes nothing and reports no limit applied. */
      @Test
      void aLimitAboveTheTupleCountChangesNothing() {
         CrossTabFilter unlimited = crosstab(true, 0);
         CrossTabFilter limited = crosstab(true, 1000);

         assertEquals(-1, limited.getAppliedMaxRows());
         assertEquals(unlimited.getRowCount(), limited.getRowCount());
         assertEquals(unlimited.getColCount(), limited.getColCount());

         for(int r = 0; r < unlimited.getRowCount(); r++) {
            for(int c = 0; c < unlimited.getColCount(); c++) {
               assertEquals(unlimited.getObject(r, c), limited.getObject(r, c), r + "," + c);
            }
         }
      }

      /** A result that has exactly as many row tuples as the limit is within the limit. */
      @ParameterizedTest(name = "groupRows={0}")
      @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
      void aLimitEqualToTheRowTupleCountDoesNotCut(boolean groupRows) {
         CrossTabFilter unlimited = crosstab(groupRows, 0);
         int tuples = unlimited.getRowCount() - unlimited.getHeaderRowCount();
         CrossTabFilter limited = crosstab(groupRows, tuples);

         assertEquals(-1, limited.getAppliedMaxRows());
         assertEquals(unlimited.getRowCount(), limited.getRowCount());
         assertEquals(unlimited.getColCount(), limited.getColCount());
      }

      @ParameterizedTest(name = "groupRows={0} limit={1}")
      @MethodSource("limits")
      void aLimitBelowTheTupleCountIsReportedAndCutsTheResult(boolean groupRows, int limit) {
         CrossTabFilter unlimited = crosstab(groupRows, 0);
         int full = unlimited.getRowCount() * unlimited.getColCount();
         CrossTabFilter f = crosstab(groupRows, limit);

         assertEquals(limit, f.getAppliedMaxRows());
         // the grand total tuple counts toward the limit, so the cut can fall after the last row
         // and take the columns that the later base rows would have added
         assertTrue(f.getRowCount() * f.getColCount() < full,
                    f.getRowCount() + " x " + f.getColCount() + " cells, unlimited has " + full);
      }

      /**
       * What is shown agrees with itself: every detail row's Grand Total cell is the sum of its
       * shown cells, and every cell of the Grand Total row is the sum of the shown rows above it.
       */
      @ParameterizedTest(name = "groupRows={0} limit={1}")
      @MethodSource("limits")
      void totalsAgreeWithTheShownCellsWhenTheResultIsCut(boolean groupRows, int limit) {
         CrossTabFilter f = crosstab(groupRows, limit);
         int rowLevels = groupRows ? 2 : 1;
         int totalCol = f.getColCount() - 1;
         Map<Integer, Double> columnSums = new TreeMap<>();
         List<String> problems = new ArrayList<>();
         double grandTotalRow = Double.NaN;

         for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
            int size = f.getRowTuple(r).size();
            String label = Arrays.toString(f.getRowTuple(r).getRow());

            if(size == rowLevels) {
               double rowSum = 0;
               boolean any = false;

               for(int c = f.getHeaderColCount(); c < totalCol; c++) {
                  Object value = f.getObject(r, c);
                  rowSum += number(value);
                  any = any || value != null;
                  columnSums.merge(c, number(value), Double::sum);
               }

               Object total = f.getObject(r, totalCol);

               if(any && (total == null || Math.abs(number(total) - rowSum) > 1e-9)) {
                  problems.add("row " + label + ": cells add up to " + rowSum + ", total is " + total);
               }
            }
            else if(size == 0) {
               for(int c = f.getHeaderColCount(); c < totalCol; c++) {
                  double want = columnSums.getOrDefault(c, 0.0);

                  if(Math.abs(number(f.getObject(r, c)) - want) > 1e-9) {
                     problems.add("grand total of column " + c + ": the rows add up to " + want
                                     + ", it shows " + f.getObject(r, c));
                  }
               }
            }
         }

         assertTrue(problems.isEmpty(), problems.size() + " disagreements:\n"
            + String.join("\n", problems.subList(0, Math.min(8, problems.size()))));
      }
   }

   /**
    * The calculated measures (Change, Value Of, Running Total, Moving, Percent of total) of a
    * crosstab whose innermost row dimension has Top N with "Group all others together": the
    * Others row must show what the calculation gives for the sum of the other groups. The oracle is
    * the same data with the groups outside the top N relabeled "Others" in the base table and no
    * Top N; a plain group has no Others handling of its own, so the cells of both crosstabs must be
    * equal (details, the Others row, totals).
    *
    * The crosstab is wired like CrossTabFilterPercentCalcTotalTest wires it (the way
    * AbstractCrosstabVSAQuery does for a viewsheet crosstab): rows [Cat] or [Outer, Cat], columns
    * [Month] or [Year, Month], the calculation along Month, "Compute trend and comparison for
    * totals" off.
    */
   @Nested
   class OthersCalculators {
      private static final String SUM = "Sum(Total)";
      private static final String CALC = "C Sum(Total)";
      private static final String CALC_PATH = CALC + ".1";
      private static final String[] KINDS = { "ChangePct", "ChangeAbs", "ValueOf", "Running", "Moving",
                                              "Percent" };

      static Stream<org.junit.jupiter.params.provider.Arguments> configs() {
         List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

         for(String kind : KINDS) {
            for(int rl = 1; rl <= 2; rl++) {
               for(int cl = 1; cl <= 2; cl++) {
                  for(boolean aggTopN : new boolean[] { false, true }) {
                     list.add(org.junit.jupiter.params.provider.Arguments.of(kind, rl, cl, aggTopN));
                  }
               }
            }
         }

         return list.stream();
      }

      @ParameterizedTest(name = "{0} rows={1} cols={2} aggregateTopN={3}")
      @MethodSource("configs")
      void othersRowShowsTheCalculationOfTheMergedGroups(String kind, int rowLevels, int colLevels,
                                                         boolean aggTopN)
      {
         List<Object[]> rows = baseRows();
         Map<String, Object> actual = cells(build(rows, kind, rowLevels, colLevels, true, aggTopN));
         Map<String, Object> expected =
            cells(build(relabelOthers(rows, rowLevels), kind, rowLevels, colLevels, false, aggTopN));
         List<String> problems = new ArrayList<>();
         int others = 0;

         for(Map.Entry<String, Object> e : actual.entrySet()) {
            assertTrue(expected.containsKey(e.getKey()), "no expected cell for " + e.getKey());

            if(e.getKey().contains("Others")) {
               others++;
            }

            if(!same(expected.get(e.getKey()), e.getValue())) {
               problems.add(e.getKey() + " expected=" + expected.get(e.getKey()) + " actual="
                               + e.getValue());
            }
         }

         assertTrue(others > 0, "the crosstab has no Others cell");
         assertTrue(problems.isEmpty(), problems.size() + " cells differ:\n"
            + String.join("\n", problems.subList(0, Math.min(10, problems.size()))));
      }

      // ---- crosstab under test ----

      /** Outer {b1, b2} x Cat {a1..a6} x Year {2022, 2023} x Month {1..3}, sparse, no ties in Sum. */
      private static List<Object[]> baseRows() {
         Random rnd = new Random(2);
         List<Object[]> rows = new ArrayList<>();
         int cats = 6;

         for(String outer : new String[] { "b1", "b2" }) {
            for(int c = 0; c < cats; c++) {
               for(int year = 2022; year <= 2023; year++) {
                  for(int month = 1; month <= 3; month++) {
                     if(rnd.nextInt(5) == 0) {
                        continue;
                     }

                     double base = (cats - c) * 100 + ("b1".equals(outer) ? 0 : 7) * (c + 1);
                     rows.add(new Object[] {
                        outer, "a" + (c + 1), year, month,
                        base + rnd.nextInt(50) + (year - 2022) * 3 + month
                     });
                  }
               }
            }
         }

         return rows;
      }

      /** The categories outside the top 2 (by Sum within the outer group) become Others. */
      private static List<Object[]> relabelOthers(List<Object[]> rows, int rowLevels) {
         Map<String, Map<String, Double>> totals = new TreeMap<>();

         for(Object[] row : rows) {
            String parent = rowLevels == 1 ? "*" : (String) row[0];
            totals.computeIfAbsent(parent, k -> new HashMap<>())
               .merge((String) row[1], (Double) row[4], Double::sum);
         }

         Map<String, Set<String>> kept = new HashMap<>();

         for(Map.Entry<String, Map<String, Double>> e : totals.entrySet()) {
            List<Map.Entry<String, Double>> list = new ArrayList<>(e.getValue().entrySet());
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            Set<String> set = new HashSet<>();

            for(int i = 0; i < Math.min(2, list.size()); i++) {
               set.add(list.get(i).getKey());
            }

            kept.put(e.getKey(), set);
         }

         List<Object[]> out = new ArrayList<>();

         for(Object[] row : rows) {
            Object[] copy = row.clone();
            String parent = rowLevels == 1 ? "*" : (String) row[0];

            if(!kept.get(parent).contains(row[1])) {
               copy[1] = CrossFilter.OTHERS;
            }

            out.add(copy);
         }

         return out;
      }

      private static CrossTabFilter build(List<Object[]> rowsIn, String kind, int rowLevels,
                                          int colLevels, boolean topN, boolean aggTopN)
      {
         List<Object[]> all = new ArrayList<>();
         all.add(new Object[] { "Outer", "Cat", "Year", "Month", "Total" });
         all.addAll(rowsIn);
         int[] rows = rowLevels == 1 ? new int[] { 1 } : new int[] { 0, 1 };
         int[] cols = colLevels == 1 ? new int[] { 3 } : new int[] { 2, 3 };
         Formula second = new SumFormula();

         if("Percent".equals(kind)) {
            ((PercentageFormula) second).setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
         }

         CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(all.toArray(new Object[0][])),
                                               rows, cols, new int[] { 4, 4 },
                                               new Formula[] { new SumFormula(), second });
         f.setRowHeaders(rowLevels == 1 ? List.of("Cat") : List.of("Outer", "Cat"));
         f.setColHeaders(colLevels == 1 ? List.of("Month") : List.of("Year", "Month"));

         if(rowLevels == 2) {
            f.setSuppressRowGroupTotal(false, 0);
         }

         if(colLevels == 2) {
            f.setSuppressColumnGroupTotal(false, 0);
         }

         if("Percent".equals(kind)) {
            f.setPercentageDirection(XConstants.PERCENTAGE_BY_COL);
         }

         f.setSuppressRowGrandTotal(false);
         f.setSuppressColumnGrandTotal(false);
         f.setSummarySideBySide(true);
         f.setAggregateTopN(aggTopN);

         if(topN) {
            f.setRowTopN(rowLevels - 1, 0, 2, false, true);
         }

         f.setSortOthersLast(true);
         CalcColumn calc = calc(kind);

         if(calc instanceof AbstractColumn) {
            ((AbstractColumn) calc).setCalculateTotal(false);
         }

         calc.setColIndex(1);
         List<CalcColumn> calcs = new ArrayList<>();
         calcs.add(calc);
         f.setCalcs(calcs, false);
         Map<String, String> headers = new HashMap<>();
         headers.put(CALC, SUM);
         f.setMeasureNames(new String[] { SUM, CALC }, headers);
         f.moreRows(Integer.MAX_VALUE);
         return f;
      }

      private static CalcColumn calc(String kind) {
         String inner = AbstractCalc.COLUMN_INNER;

         switch(kind) {
         case "ChangePct": {
            ChangeColumn c = new ChangeColumn(SUM, CALC);
            c.setChangeType(ValueOfCalc.PREVIOUS);
            c.setAsPercent(true);
            c.setDim("Month");
            return c;
         }
         case "ChangeAbs": {
            ChangeColumn c = new ChangeColumn(SUM, CALC);
            c.setChangeType(ValueOfCalc.PREVIOUS);
            c.setAsPercent(false);
            c.setDim("Month");
            return c;
         }
         case "ValueOf": {
            ValueOfColumn c = new ValueOfColumn(SUM, CALC);
            c.setChangeType(ValueOfCalc.PREVIOUS);
            c.setDim("Month");
            return c;
         }
         case "Running": {
            RunningTotalColumn c = new RunningTotalColumn(SUM, CALC);
            c.setFormula(new SumFormula());
            c.setBreakBy(inner);
            return c;
         }
         case "Moving": {
            MovingColumn c = new MovingColumn(SUM, CALC);
            c.setFormula(new SumFormula());
            c.setInnerDim(inner);
            c.setPreCnt(1);
            c.setIncludeCurrent(true);
            return c;
         }
         default: {
            PercentColumn c = new PercentColumn(SUM, CALC);
            c.setLevel(PercentCalc.GRAND_TOTAL);
            return c;
         }
         }
      }

      /** The calc cells, keyed by row tuple and column tuple. */
      private static Map<String, Object> cells(CrossTabFilter f) {
         Map<String, Object> map = new TreeMap<>();
         TableDataDescriptor desc = f.getDescriptor();

         for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
            for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
               TableDataPath path = desc.getCellDataPath(r, c);

               if(path != null && path.getPath().length > 0 &&
                  CALC_PATH.equals(path.getPath()[path.getPath().length - 1]))
               {
                  map.put(Arrays.toString(f.getRowTuple(r).getRow()) + " x " +
                             Arrays.toString(f.getColTuple(c).getRow()), f.getObject(r, c));
               }
            }
         }

         return map;
      }

      private static boolean same(Object want, Object have) {
         if(want == null || have == null) {
            return want == have;
         }

         if(want instanceof Number && have instanceof Number) {
            double w = ((Number) want).doubleValue();
            return Math.abs(w - ((Number) have).doubleValue()) < 1e-9 * Math.max(1, Math.abs(w));
         }

         return want.equals(have);
      }
   }

   // ------------------------------------------------------------------------------------
   // Shared helpers of the groups below: a crosstab wired as the viewsheet query wires it,
   // read back as (row tuple, column tuple, value) and compared with loops over the base rows.
   // ------------------------------------------------------------------------------------

   /** One cell of the crosstab body. */
   private record Cell(Object[] rowKey, Object[] colKey, String measure, Object value) {
      @Override
      public String toString() {
         return "row=" + Arrays.toString(rowKey) + " col=" + Arrays.toString(colKey) + " " +
            measure + "=" + value;
      }
   }

   /** The body cells of a crosstab, with the tuple of their row and column and measure name. */
   private static List<Cell> readCells(CrossTabFilter f) {
      List<Cell> cells = new ArrayList<>();
      TableDataDescriptor desc = f.getDescriptor();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            TableDataPath path = desc.getCellDataPath(r, c);
            String measure = path == null || path.getPath().length == 0 ? null :
               path.getPath()[path.getPath().length - 1];

            if(measure == null) {
               continue;
            }

            cells.add(new Cell(f.getRowTuple(r).getRow(), f.getColTuple(c).getRow(), measure,
                               f.getObject(r, c)));
         }
      }

      return cells;
   }

   /** The base rows whose first dims columns equal the key (a shorter key matches a group). */
   private static List<Object[]> matching(Object[][] data, int[] rowCols, Object[] rowKey,
                                          int[] colCols, Object[] colKey)
   {
      List<Object[]> list = new ArrayList<>();

      for(int i = 1; i < data.length; i++) {
         if(keyMatches(data[i], rowCols, rowKey) && keyMatches(data[i], colCols, colKey)) {
            list.add(data[i]);
         }
      }

      return list;
   }

   private static boolean keyMatches(Object[] row, int[] cols, Object[] key) {
      for(int i = 0; i < key.length; i++) {
         if(!Objects.equals(row[cols[i]], key[i])) {
            return false;
         }
      }

      return true;
   }

   private static double sumOf(List<Object[]> rows, int col) {
      return rows.stream().filter(r -> r[col] != null)
         .mapToDouble(r -> ((Number) r[col]).doubleValue()).sum();
   }

   /** A crosstab with every group total and both grand totals shown, side by side. */
   private static CrossTabFilter crosstab(Object[][] data, int[] rowCols, int[] colCols,
                                          int[] measureCols, Formula[] formulas)
   {
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), rowCols, colCols,
                                            measureCols, formulas);
      List<String> rowNames = new ArrayList<>();
      List<String> colNames = new ArrayList<>();

      for(int c : rowCols) {
         rowNames.add(String.valueOf(data[0][c]));
      }

      for(int c : colCols) {
         colNames.add(String.valueOf(data[0][c]));
      }

      f.setRowHeaders(rowNames);
      f.setColHeaders(colNames);

      for(int level = 0; level < rowCols.length - 1; level++) {
         f.setSuppressRowGroupTotal(false, level);
      }

      for(int level = 0; level < colCols.length - 1; level++) {
         f.setSuppressColumnGroupTotal(false, level);
      }

      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(true);
      return f;
   }

   private static boolean sameNumber(Object expected, Object actual) {
      if(expected == null) {
         return actual == null || "".equals(actual) ||
            actual instanceof Number && ((Number) actual).doubleValue() == 0;
      }

      return actual instanceof Number && Math.abs(
         ((Number) actual).doubleValue() - ((Number) expected).doubleValue()) < 1e-9;
   }

   private static Object sumPercent(int percentageType) {
      SumFormula formula = new SumFormula();
      formula.setPercentageType(percentageType);
      return formula;
   }

   // ------------------------------------------------------------------------------------

   /**
    * Percentage of group: a cell is its value divided by the value of its parent group in the
    * same column (rows) or row (columns); a subtotal and a grand total divide by the grand
    * total. The oracle sums the base rows.
    */
   @Nested
   class PercentOfGroup {
      /** Year x Month x Cat with one missing combination so that groups are uneven. */
      private Object[][] salesData() {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { "Year", "Month", "Cat", "V" });

         for(int year = 2020; year <= 2023; year++) {
            for(int month = 1; month <= 2; month++) {
               for(String cat : new String[] { "A", "B" }) {
                  if(year == 2021 && month == 2 && cat.equals("B")) {
                     continue;
                  }

                  rows.add(new Object[] { year, month, cat,
                                          (double) ((year - 2019) * 10 + month * 3 +
                                             (cat.equals("A") ? 1 : 5)) });
               }
            }
         }

         return rows.toArray(new Object[0][]);
      }

      @Test
      void rowsDivideEachCellByItsYearGroupInTheSameColumn() {
         Object[][] data = salesData();
         int[] rowCols = { 0, 1 };
         int[] colCols = { 2 };
         CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 3 },
                                     new Formula[] { (Formula) sumPercent(XConstants.PERCENTAGE_OF_GROUP) });
         f.setPercentageDirection(XConstants.PERCENTAGE_BY_ROW);
         f.moreRows(Integer.MAX_VALUE);

         assertPercentOfParent(f, data, rowCols, colCols, true);
      }

      @Test
      void columnsDivideEachCellByItsYearGroupInTheSameRow() {
         Object[][] data = salesData();
         int[] rowCols = { 2 };
         int[] colCols = { 0, 1 };
         CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 3 },
                                     new Formula[] { (Formula) sumPercent(XConstants.PERCENTAGE_OF_GROUP) });
         f.setPercentageDirection(XConstants.PERCENTAGE_BY_COL);
         f.moreRows(Integer.MAX_VALUE);

         assertPercentOfParent(f, data, rowCols, colCols, false);
      }

      private void assertPercentOfParent(CrossTabFilter f, Object[][] data, int[] rowCols,
                                         int[] colCols, boolean byRow)
      {
         List<String> problems = new ArrayList<>();
         int checked = 0;

         for(Cell cell : readCells(f)) {
            Object[] key = byRow ? cell.rowKey() : cell.colKey();
            Object[] parent = key.length > 1 ? Arrays.copyOf(key, key.length - 1) : new Object[0];
            List<Object[]> own = byRow
               ? matching(data, rowCols, key, colCols, cell.colKey())
               : matching(data, rowCols, cell.rowKey(), colCols, key);
            List<Object[]> base = byRow
               ? matching(data, rowCols, parent, colCols, cell.colKey())
               : matching(data, rowCols, cell.rowKey(), colCols, parent);
            Double expected = own.isEmpty() ? null : sumOf(own, 3) / sumOf(base, 3);
            checked++;

            if(!sameNumber(expected, cell.value())) {
               problems.add(cell + " expected=" + expected);
            }
         }

         assertTrue(checked > 0, "no cell was read");
         assertTrue(problems.isEmpty(), problems.size() + " cells differ: " +
            problems.subList(0, Math.min(8, problems.size())));
      }

      @ParameterizedTest(name = "byRow={0}")
      @ValueSource(booleans = { true, false })
      void withOneHeaderLevelPercentOfGroupEqualsPercentOfGrandTotal(boolean byRow) {
         Object[][] data = salesData();
         int[] oneLevel = { 2 };
         int[] other = { 0 };
         List<List<Object>> results = new ArrayList<>();

         for(int type : new int[] { XConstants.PERCENTAGE_OF_GROUP,
                                    XConstants.PERCENTAGE_OF_GRANDTOTAL })
         {
            CrossTabFilter f = crosstab(data, byRow ? oneLevel : other, byRow ? other : oneLevel,
                                        new int[] { 3 },
                                        new Formula[] { (Formula) sumPercent(type) });
            f.setPercentageDirection(byRow ? XConstants.PERCENTAGE_BY_ROW
                                           : XConstants.PERCENTAGE_BY_COL);
            f.moreRows(Integer.MAX_VALUE);
            List<Object> values = new ArrayList<>();
            readCells(f).forEach(c -> values.add(c.value()));
            results.add(values);
         }

         assertFalse(results.get(0).isEmpty());
         assertEquals(results.get(1), results.get(0));
      }

      @ParameterizedTest(name = "type={0} byRow={1}")
      @MethodSource("typesAndDirections")
      void aGroupSummingToZeroAndANullMeasureGiveNoNanOrInfinity(int type, boolean byRow) {
         Object[][] data = {
            { "Year", "Cat", "V" },
            { 2022, "A", 5.0 }, { 2022, "B", -5.0 }, { 2023, "A", 4.0 }, { 2023, "B", null },
            { 2024, "A", 0.0 }
         };
         CrossTabFilter f = crosstab(data, byRow ? new int[] { 0, 1 } : new int[] { 1 },
                                     byRow ? new int[] { 1 } : new int[] { 0, 1 },
                                     new int[] { 2 }, new Formula[] { (Formula) sumPercent(type) });
         f.setPercentageDirection(byRow ? XConstants.PERCENTAGE_BY_ROW
                                        : XConstants.PERCENTAGE_BY_COL);
         f.moreRows(Integer.MAX_VALUE);

         List<Cell> cells = readCells(f);
         assertFalse(cells.isEmpty());

         for(Cell cell : cells) {
            if(cell.value() instanceof Number) {
               double v = ((Number) cell.value()).doubleValue();
               assertFalse(Double.isNaN(v) || Double.isInfinite(v), cell.toString());
            }
            else {
               assertTrue(cell.value() == null || "".equals(cell.value()), cell.toString());
            }
         }
      }

      static Stream<Arguments> typesAndDirections() {
         return Stream.of(XConstants.PERCENTAGE_OF_GROUP, XConstants.PERCENTAGE_OF_GRANDTOTAL)
            .flatMap(t -> Stream.of(Arguments.of(t, true), Arguments.of(t, false)));
      }
   }

   // ------------------------------------------------------------------------------------

   /**
    * Calculators on total cells with "Compute Trend and Comparison for Totals" on. The
    * expectations are properties of the calculator definitions, checked against the plain
    * Sum cells of the same crosstab; none is copied from an output.
    */
   @Nested
   class CalculatorTotalCells {
      private static final int YEARS = 2;
      private static final int MONTHS = 3;

      /** Year {2022, 2023} x Month {1..3} x Cat {A, B}; values differ in every cell. */
      private Object[][] data() {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { "Year", "Month", "Cat", "V" });

         for(int y = 0; y < YEARS; y++) {
            for(int m = 1; m <= MONTHS; m++) {
               for(int c = 0; c < 2; c++) {
                  rows.add(new Object[] { 2022 + y, m, c == 0 ? "A" : "B",
                                          (double) (13 * (y + 1) + 7 * m * m + 29 * c) });
               }
            }
         }

         return rows.toArray(new Object[0][]);
      }

      /** The crosstab with the calculator on a second measure, Sum first. */
      private CrossTabFilter build(CalcColumn calc, boolean byRow, boolean sideBySide) {
         Object[][] data = data();
         CrossTabFilter f = crosstab(data, byRow ? new int[] { 0, 1 } : new int[] { 2 },
                                     byRow ? new int[] { 2 } : new int[] { 0, 1 },
                                     new int[] { 3, 3 },
                                     new Formula[] { new SumFormula(), new SumFormula() });
         f.setSummarySideBySide(sideBySide);

         if(calc instanceof AbstractColumn) {
            ((AbstractColumn) calc).setCalculateTotal(true);
         }

         calc.setColIndex(1);
         f.setCalcs(new ArrayList<>(List.of(calc)), true);
         Map<String, String> calcHeaders = new HashMap<>();
         calcHeaders.put(CALC, SUM);
         f.setMeasureNames(new String[] { SUM, CALC }, calcHeaders);
         f.moreRows(Integer.MAX_VALUE);
         return f;
      }

      private static final String SUM = "Sum(V)";
      private static final String CALC = "C Sum(V)";

      /** (rowKey, colKey) as a string -> {calc value, sum value}. */
      private Map<String, Object[]> grid(CrossTabFilter f) {
         Map<String, Object[]> map = new HashMap<>();
         TableDataDescriptor desc = f.getDescriptor();
         boolean sbs = f.isSummarySideBySide();

         for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
            for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
               TableDataPath path = desc.getCellDataPath(r, c);
               String last = path == null || path.getPath().length == 0 ? null :
                  path.getPath()[path.getPath().length - 1];

               if(!(CALC + ".1").equals(last)) {
                  continue;
               }

               int sr = sbs ? r : r - 1;
               int sc = sbs ? c - 1 : c;
               map.put(key(f.getRowTuple(r).getRow(), f.getColTuple(c).getRow()),
                       new Object[] { f.getObject(r, c), f.getObject(sr, sc) });
            }
         }

         return map;
      }

      private static String key(Object[] rowKey, Object[] colKey) {
         return Arrays.toString(rowKey) + "|" + Arrays.toString(colKey);
      }

      private static Object[] row(Object... values) {
         return values;
      }

      private static double num(Object value) {
         assertInstanceOf(Number.class, value);
         return ((Number) value).doubleValue();
      }

      private CalcColumn running(boolean byRow) {
         RunningTotalColumn c = new RunningTotalColumn(SUM, CALC);
         c.setFormula(new SumFormula());
         c.setBreakBy(byRow ? AbstractCalc.ROW_INNER : AbstractCalc.COLUMN_INNER);
         return c;
      }

      @ParameterizedTest(name = "byRow={0} sideBySide={1}")
      @MethodSource("layouts")
      void runningTotalOfAGroupTotalIsItsSumAndTheLastRunningValueOfTheGroup(boolean byRow,
                                                                             boolean sbs)
      {
         CrossTabFilter f = build(running(byRow), byRow, sbs);
         Map<String, Object[]> grid = grid(f);
         int checked = 0;

         // the running axis has [Year, Month]; the other axis has [Cat] or is a total
         for(boolean otherTotal : new boolean[] { false, true }) {
            for(String cat : otherTotal ? new String[] { null } : new String[] { "A", "B" }) {
               Object[] other = cat == null ? row() : row(cat);

               for(int year = 2022; year < 2022 + YEARS; year++) {
                  Object[] yearTotal = row(year);
                  Object[] last = grid.get(key(byRow ? row(year, MONTHS) : other,
                                               byRow ? other : row(year, MONTHS)));
                  Object[] total = grid.get(key(byRow ? yearTotal : other,
                                                byRow ? other : yearTotal));
                  assertNotNull(total, "year total cell " + year + " " + Arrays.toString(other));
                  assertNotNull(last, "last month cell");
                  assertEquals(num(total[1]), num(total[0]), 1e-9, "year total = its Sum cell");
                  assertEquals(num(last[0]), num(total[0]), 1e-9,
                               "year total = last running value of the year");
                  checked++;
               }

               Object[] grand = grid.get(key(byRow ? row() : other, byRow ? other : row()));
               assertNotNull(grand);
               assertEquals(num(grand[1]), num(grand[0]), 1e-9, "grand total = its Sum cell");
            }
         }

         assertEquals(YEARS * 3, checked);
      }

      @ParameterizedTest(name = "byRow={0} sideBySide={1}")
      @MethodSource("layouts")
      void runningTotalOfATotalColumnIsTheSumOverTheCategoriesOfTheirRunningValues(
         boolean byRow, boolean sbs)
      {
         CrossTabFilter f = build(running(byRow), byRow, sbs);
         Map<String, Object[]> grid = grid(f);
         int checked = 0;

         for(int year = 2022; year < 2022 + YEARS; year++) {
            for(int month = 1; month <= MONTHS; month++) {
               Object[] axis = row(year, month);
               double expected = 0;

               for(String cat : new String[] { "A", "B" }) {
                  expected += num(grid.get(key(byRow ? axis : row(cat),
                                               byRow ? row(cat) : axis))[0]);
               }

               Object[] total = grid.get(key(byRow ? axis : row(), byRow ? row() : axis));
               assertNotNull(total);
               assertEquals(expected, num(total[0]), 1e-9, "year " + year + " month " + month);
               checked++;
            }
         }

         assertEquals(YEARS * MONTHS, checked);
      }

      @ParameterizedTest(name = "byRow={0} sideBySide={1}")
      @MethodSource("layouts")
      void valueOfPreviousMonthOnATotalCellIsTheSumOfThePreviousMonth(boolean byRow, boolean sbs) {
         ValueOfColumn c = new ValueOfColumn(SUM, CALC);
         c.setChangeType(ValueOfCalc.PREVIOUS);
         c.setDim("Month");
         Map<String, Object[]> grid = grid(build(c, byRow, sbs));
         int checked = 0;

         for(int year = 2022; year < 2022 + YEARS; year++) {
            for(int month = 1; month <= MONTHS; month++) {
               for(String cat : new String[] { null, "A", "B" }) {
                  Object[] other = cat == null ? row() : row(cat);
                  Object[] cell = grid.get(key(byRow ? row(year, month) : other,
                                               byRow ? other : row(year, month)));
                  assertNotNull(cell);

                  if(month == 1) {
                     assertNull(cell[0], "no previous month before month 1 of " + year);
                  }
                  else {
                     Object[] previous = grid.get(key(byRow ? row(year, month - 1) : other,
                                                      byRow ? other : row(year, month - 1)));
                     assertEquals(num(previous[1]), num(cell[0]), 1e-9,
                                  "year " + year + " month " + month + " " + cat);
                  }

                  if(cat == null) {
                     checked++;
                  }
               }
            }

            // a year subtotal has no Month, so it has no previous month
            for(String cat : new String[] { null, "A", "B" }) {
               Object[] other = cat == null ? row() : row(cat);
               Object[] cell = grid.get(key(byRow ? row(year) : other, byRow ? other : row(year)));
               assertNotNull(cell);
               assertNull(cell[0], "year subtotal " + year + " " + cat);
            }
         }

         assertEquals(YEARS * MONTHS, checked);
      }

      @ParameterizedTest(name = "byRow={0} sideBySide={1}")
      @MethodSource("layouts")
      void movingSumOfThePreviousAndCurrentMonthOnATotalCellAddsTheTwoSumCells(boolean byRow,
                                                                               boolean sbs)
      {
         MovingColumn c = new MovingColumn(SUM, CALC);
         c.setFormula(new SumFormula());
         c.setInnerDim(byRow ? AbstractCalc.ROW_INNER : AbstractCalc.COLUMN_INNER);
         c.setPreCnt(1);
         c.setIncludeCurrent(true);
         Map<String, Object[]> grid = grid(build(c, byRow, sbs));
         int checked = 0;

         for(int year = 2022; year < 2022 + YEARS; year++) {
            for(int month = 1; month <= MONTHS; month++) {
               // the cell of the total of the other axis
               Object[] cell = grid.get(key(byRow ? row(year, month) : row(),
                                            byRow ? row() : row(year, month)));
               assertNotNull(cell);
               double expected = num(cell[1]);

               if(month > 1) {
                  Object[] previous = grid.get(key(byRow ? row(year, month - 1) : row(),
                                                   byRow ? row() : row(year, month - 1)));
                  expected += num(previous[1]);
               }

               assertEquals(expected, num(cell[0]), 1e-9, "year " + year + " month " + month);
               checked++;
            }
         }

         assertEquals(YEARS * MONTHS, checked);
      }

      static Stream<Arguments> layouts() {
         return Stream.of(Arguments.of(true, true), Arguments.of(true, false),
                          Arguments.of(false, true), Arguments.of(false, false));
      }
   }

   // ------------------------------------------------------------------------------------

   /**
    * Group totals with a single null value in the group are removed when the crosstab ignores
    * null totals (the result of a drill); a group with a null and a value keeps its total, and
    * the grand total is never removed.
    */
   @Nested
   class NullGroupTotals {
      @ParameterizedTest(name = "totalOnTop={0} ignoreNullTotals={1}")
      @MethodSource("flags")
      void aGroupOfOneNullMemberHasNoSubtotal(boolean totalOnTop, boolean ignoreNullTotals) {
         Object[][] data = {
            { "Cat", "Sub", "Col", "V" },
            { "A", null, "x", 1.0 },
            { "B", null, "x", 2.0 }, { "B", "s1", "x", 3.0 },
            { "C", "s1", "x", 4.0 }, { "C", "s2", "x", 5.0 }
         };
         CrossTabFilter f = crosstab(data, new int[] { 0, 1 }, new int[] { 2 }, new int[] { 3 },
                                     new Formula[] { new SumFormula() });
         f.setRowTotalOnTop(totalOnTop);
         f.setIgnoreNullTotals(ignoreNullTotals);
         f.moreRows(Integer.MAX_VALUE);

         Set<String> subtotals = new TreeSet<>();
         boolean grand = false;

         for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
            Object[] key = f.getRowTuple(r).getRow();

            if(key.length == 1) {
               subtotals.add(String.valueOf(key[0]));
            }
            else if(key.length == 0) {
               grand = true;
            }
         }

         assertTrue(grand, "the grand total is never removed");
         assertEquals(ignoreNullTotals ? Set.of("B", "C") : Set.of("A", "B", "C"), subtotals);
      }

      static Stream<Arguments> flags() {
         return Stream.of(Arguments.of(true, true), Arguments.of(false, true),
                          Arguments.of(true, false), Arguments.of(false, false));
      }
   }

   // ------------------------------------------------------------------------------------

   /**
    * Top N on the outer row dimension: the ranking has no parent group, so the groups are
    * ranked among all outer groups by their Sum.
    */
   @Nested
   class OuterDimensionTopN {
      @ParameterizedTest(name = "aggregateTopN={0}")
      @ValueSource(booleans = { true, false })
      void onlyTheOuterGroupWithTheLargestSumStaysAndTotalsFollowTheAggregateOption(
         boolean aggTopN)
      {
         // Outer o1 sums to 30, o2 to 100, o3 to 60; the largest is o2
         Object[][] data = {
            { "Outer", "Cat", "Col", "V" },
            { "o1", "a", "x", 10.0 }, { "o1", "b", "x", 20.0 },
            { "o2", "a", "x", 40.0 }, { "o2", "b", "y", 60.0 },
            { "o3", "a", "x", 25.0 }, { "o3", "c", "y", 35.0 }
         };
         int[] rowCols = { 0, 1 };
         int[] colCols = { 2 };
         CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 3 },
                                     new Formula[] { new SumFormula() });
         f.setAggregateTopN(aggTopN);
         f.setRowTopN(0, 0, 1, false, false);
         f.moreRows(Integer.MAX_VALUE);

         Set<Object> outers = new TreeSet<>();
   
         for(Cell cell : readCells(f)) {
            if(cell.rowKey().length > 0) {
               outers.add(cell.rowKey()[0]);
            }
         }

         assertEquals(Set.of("o2"), outers, "the outer groups that stay");

         for(Cell cell : readCells(f)) {
            Object[] rowKey = cell.rowKey();
            List<Object[]> rows = matching(
               data, rowCols, rowKey.length == 0 && aggTopN ? new Object[] { "o2" } : rowKey,
               colCols, cell.colKey());
            // grand total: the rows of the kept group when only the top N is aggregated
            Double expected = rows.isEmpty() ? null : sumOf(rows, 3);

            assertTrue(sameNumber(expected, cell.value()),
                       cell + " expected=" + expected + " aggregateTopN=" + aggTopN);
         }
      }
   }

   // ------------------------------------------------------------------------------------

   /**
    * Formulas that need all rows of a tuple (a second column, a rank or a spread) must give
    * the same result in a group total as the formula fed with the rows of that tuple.
    */
   @Nested
   class AggregateOverTheRowsOfATuple {
      // columns: R1, R2, C1, X, Y; two rows per existing (R1, R2, C1) combination
      private Object[][] data() {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[] { "R1", "R2", "C1", "X", "Y" });
         int n = 0;

         for(String r1 : new String[] { "a", "b" }) {
            for(String r2 : new String[] { "p", "q" }) {
               for(String c1 : new String[] { "c", "d" }) {
                  if(r1.equals("b") && r2.equals("q") && c1.equals("d")) {
                     continue;
                  }

                  for(int k = 0; k < 2; k++) {
                     n++;
                     rows.add(new Object[] { r1, r2, c1, (double) ((n * 37) % 23 + k),
                                             (double) ((n * 11) % 17 + 2 * k) });
                  }
               }
            }
         }

         return rows.toArray(new Object[0][]);
      }

      private void assertEveryCellEqualsTheOracle(Supplier<Formula> crosstabFormula,
                                                  Function<List<Object[]>, Object> oracle)
      {
         Object[][] data = data();
         int[] rowCols = { 0, 1 };
         int[] colCols = { 2 };
         CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 3 },
                                     new Formula[] { crosstabFormula.get() });
         f.moreRows(Integer.MAX_VALUE);
         int totals = 0;

         for(Cell cell : readCells(f)) {
            List<Object[]> rows = matching(data, rowCols, cell.rowKey(), colCols, cell.colKey());
            Object expected = rows.isEmpty() ? null : oracle.apply(rows);

            if(cell.rowKey().length < 2 || cell.colKey().length < 1) {
               totals++;
            }

            if(expected instanceof Number || expected == null) {
               assertTrue(sameNumber(expected, cell.value()), cell + " expected=" + expected);
            }
            else {
               assertEquals(expected, cell.value(), cell.toString());
            }
         }

         assertTrue(totals > 0, "no total cell was read");
      }

      private static Object feed(Formula formula, List<Object[]> rows, boolean pair) {
         for(Object[] r : rows) {
            formula.addValue(pair ? new Object[] { r[3], r[4] } : r[3]);
         }

         return formula.getResult();
      }

      @Test
      void covarianceOfATotalIsTheCovarianceOfTheRowsOfTheTuple() {
         assertEveryCellEqualsTheOracle(() -> new CovarianceFormula(4),
                                        rows -> feed(new CovarianceFormula(4), rows, true));
      }

      @Test
      void secondSmallestOfATotalIsTheSecondSmallestOfTheRowsOfTheTuple() {
         assertEveryCellEqualsTheOracle(() -> new NthSmallestFormula(2), rows -> {
            List<Double> sorted = new ArrayList<>();
            rows.forEach(r -> sorted.add(((Number) r[3]).doubleValue()));
            Collections.sort(sorted);
            return sorted.size() < 2 ? null : sorted.get(1);
         });
      }

      @Test
      void standardDeviationOfATotalIsTheDeviationOfTheRowsOfTheTuple() {
         assertEveryCellEqualsTheOracle(StandardDeviationFormula::new,
                                        rows -> feed(new StandardDeviationFormula(), rows, false));
      }

      @Test
      void productOfATotalIsTheProductOfTheRowsOfTheTuple() {
         assertEveryCellEqualsTheOracle(ProductFormula::new, rows -> {
            double product = 1;

            for(Object[] r : rows) {
               product *= ((Number) r[3]).doubleValue();
            }

            return product;
         });
      }

      @Test
      void concatOfATotalIsTheConcatOfTheRowsOfTheTuple() {
         assertEveryCellEqualsTheOracle(ConcatFormula::new,
                                        rows -> feed(new ConcatFormula(), rows, false));
      }
   }

   // ------------------------------------------------------------------------------------

   /** Empty and null inputs. */
   @Nested
   class EmptyAndNullInputs {
      @ParameterizedTest(name = "{0}")
      @ValueSource(strings = { "Sum", "PercentOfGroup", "PercentOfGrandTotal", "Running" })
      void aHeaderOnlyBaseTableGivesNoBodyCellsAndNoException(String kind) {
         Object[][] data = { { "Cat", "Sub", "V" } };
         Formula formula = new SumFormula();

         if(kind.startsWith("Percent")) {
            formula = (Formula) sumPercent("PercentOfGroup".equals(kind)
               ? XConstants.PERCENTAGE_OF_GROUP : XConstants.PERCENTAGE_OF_GRANDTOTAL);
         }

         CrossTabFilter f = crosstab(data, new int[] { 0 }, new int[] { 1 }, new int[] { 2 },
                                     new Formula[] { formula });

         if(kind.startsWith("Percent")) {
            f.setPercentageDirection(XConstants.PERCENTAGE_BY_ROW);
         }

         if(kind.equals("Running")) {
            RunningTotalColumn c = new RunningTotalColumn("Sum(V)", "R Sum(V)");
            c.setFormula(new SumFormula());
            c.setBreakBy(AbstractCalc.ROW_INNER);
            c.setCalculateTotal(true);
            c.setColIndex(0);
            f.setCalcs(new ArrayList<>(List.of(c)), true);
         }

         f.moreRows(Integer.MAX_VALUE);

         assertEquals(f.getHeaderRowCount(), f.getRowCount(), "only the header rows");
         assertTrue(readCells(f).isEmpty());
      }

      @Test
      void sumAndAverageIgnoreNullMeasuresAndAnAllNullGroupIsEmpty() {
         Object[][] data = {
            { "Cat", "Col", "V", "W" },
            { "g1", "x", null, null }, { "g1", "x", null, null },
            { "g2", "x", null, null }, { "g2", "x", 5.0, 5.0 }, { "g2", "x", 7.0, 7.0 }
         };
         int[] rowCols = { 0 };
         int[] colCols = { 1 };
         CrossTabFilter f = crosstab(data, rowCols, colCols, new int[] { 2, 3 },
                                     new Formula[] { new SumFormula(), new AverageFormula() });
         f.moreRows(Integer.MAX_VALUE);
         int checked = 0;

         for(Cell cell : readCells(f)) {
            List<Object[]> rows = matching(data, rowCols, cell.rowKey(), colCols, cell.colKey());
            boolean sum = "V".equals(cell.measure());
            int col = sum ? 2 : 3;
            List<Double> values = new ArrayList<>();
            rows.stream().filter(r -> r[col] != null)
               .forEach(r -> values.add(((Number) r[col]).doubleValue()));
            Double expected = values.isEmpty() ? null
               : sum ? values.stream().mapToDouble(Double::doubleValue).sum()
               : values.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
            assertTrue(sameNumber(expected, cell.value()), cell + " expected=" + expected);
            checked++;
         }

         assertEquals(12, checked, "g1, g2 and the grand total, by column x and total, of two measures");
      }
   }

   // ------------------------------------------------------------------------------------

   /** The month gap of a time series row header is filled with a row of empty cells. */
   @Nested
   class TimeSeriesFill {
      @Test
      void aMissingMonthGetsARowOfEmptyCellsAndTotalsAreUnchanged() {
         Object[][] data = {
            { "Month", "Cat", "V" },
            { java.sql.Date.valueOf("2024-01-01"), "A", 10.0 },
            { java.sql.Date.valueOf("2024-01-01"), "B", 20.0 },
            { java.sql.Date.valueOf("2024-04-01"), "A", 30.0 }
         };
         CrossTabFilter f = new CrossTabFilter(
            new DefaultTableLens(data), new int[] { 0 }, new int[] { 1 }, new int[] { 2 },
            new Formula[] { new SumFormula() }, true, false, XConstants.MONTH_DATE_GROUP,
            XConstants.NONE_DATE_GROUP);
         f.setRowHeaders(List.of("Month"));
         f.setColHeaders(List.of("Cat"));
         f.setSuppressRowGrandTotal(false);
         f.setSuppressColumnGrandTotal(false);
         f.setSummarySideBySide(true);
         f.moreRows(Integer.MAX_VALUE);

         List<Integer> months = new ArrayList<>();
         double grand = -1;

         for(Cell cell : readCells(f)) {
            if(cell.rowKey().length == 0) {
               if(cell.colKey().length == 0) {
                  grand = ((Number) cell.value()).doubleValue();
               }

               continue;
            }

            int month = new java.util.Date(((java.util.Date) cell.rowKey()[0]).getTime()).toInstant()
               .atZone(java.time.ZoneId.systemDefault()).getMonthValue();

            if(cell.colKey().length == 0) {
               months.add(month);
            }

            if(month == 2 || month == 3) {
               assertTrue(sameNumber(null, cell.value()), "the filled month is empty: " + cell);
            }
         }

         assertEquals(List.of(1, 2, 3, 4), months, "months in calendar order, gaps filled");
         assertEquals(60.0, grand, 1e-9, "the grand total is unchanged by the fill");
      }
   }

   // ------------------------------------------------------------------------------------

   /**
    * With side by side measures the viewsheet paints the column header of each member over the
    * columns of all its measures.
    */
   @Nested
   class SideBySideHeaderSpans {
      @Test
      void aColumnMemberSpansTheColumnsOfAllItsMeasures() {
         Object[][] data = {
            { "Cat", "Col", "V", "W" },
            { "a", "x", 1.0, 2.0 }, { "a", "y", 3.0, 4.0 }, { "b", "z", 5.0, 6.0 }
         };
         CrossTabFilter f = crosstab(data, new int[] { 0 }, new int[] { 1 }, new int[] { 2, 3 },
                                     new Formula[] { new SumFormula(), new SumFormula() });
         f.moreRows(Integer.MAX_VALUE);
         f.createVSSpan();

         int first = f.getHeaderColCount();
         int measures = 2;
         int members = 4; // x, y, z and the total
         assertEquals(first + measures * members, f.getColCount());

         for(int member = 0; member < members; member++) {
            for(int offset = 0; offset < measures; offset++) {
               java.awt.Rectangle span = f.getVSSpan(0, first + member * measures + offset);
               assertNotNull(span, "member " + member + " offset " + offset);
               assertEquals(new java.awt.Rectangle(-offset, 0, measures, 1), span,
                            "member " + member + " offset " + offset);
            }
         }
      }
   }
}
