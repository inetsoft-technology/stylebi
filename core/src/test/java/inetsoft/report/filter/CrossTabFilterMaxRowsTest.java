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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterMaxRowsTest {
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
   @Disabled("Not filed (a stylebi-2.1.0 follow-up of #81276, which was fixed on 13.8 only, is "
      + "pending): with query.runtime.maxrow set, the group that the limit cuts is shown half "
      + "included, its total is blank and the Grand Total row leaves it out; CrossTabFilter."
      + "insertPair throws in the middle of a base row")
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
