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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterValueTest {
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
