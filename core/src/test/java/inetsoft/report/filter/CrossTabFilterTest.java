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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
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
import static inetsoft.report.filter.CrossTabFilterTestSupport.*;
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
      CrossTabFilter crosstab = crosstabOver(new LostTable(values(40), 21, lost));

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
         crosstabOver(new LostTable(values(40), 21, new RuntimeException("wrapped", lost)));

      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)));
   }

   private static CrossTabFilter crosstabOver(TableLens base) {
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
