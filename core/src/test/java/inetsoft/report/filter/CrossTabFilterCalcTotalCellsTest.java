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

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static inetsoft.report.filter.CrossTabFilterTestSupport.*;

/**
 * Calculators on total cells with "Compute Trend and Comparison for Totals" on. The
 * expectations are properties of the calculator definitions, checked against the plain
 * Sum cells of the same crosstab; none is copied from an output.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterCalcTotalCellsTest {
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
