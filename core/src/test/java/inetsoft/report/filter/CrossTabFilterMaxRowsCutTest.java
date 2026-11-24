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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82228: when the server's maximum number of query rows ({@code query.runtime.maxrow})
 * cuts a crosstab, the source row at the cut must be applied completely or not at all. What is
 * shown must agree with itself: a row's total is the sum of its shown cells, a group total and
 * the grand total are the sums of the shown rows below them, and no more than maxrow row tuples
 * (the grand total tuple included) are shown.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterMaxRowsCutTest {
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

   // ---------------------------------------------------------------------------------------
   // fixtures
   // ---------------------------------------------------------------------------------------

   /** The reported case: c1..c6 x m1..m3, value = category * 10 + month, ordered by month. */
   private static CrossTabFilter reported(boolean groupRows, int maxRows) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Group", "Cat", "Month", "V" });

      for(int month = 1; month <= 3; month++) {
         for(int cat = 1; cat <= 6; cat++) {
            rows.add(new Object[] { cat <= 3 ? "g1" : "g2", "c" + cat, "m" + month,
                                    (double) (cat * 10 + month) });
         }
      }

      int[] rowh = groupRows ? new int[] { 0, 1 } : new int[] { 1 };
      CrossTabFilter f = create(rows, maxRows, rowh, new int[] { 2 }, new int[] { 3 });
      f.setRowHeaders(groupRows ? List.of("Group", "Cat") : List.of("Cat"));

      if(groupRows) {
         f.setSuppressRowGroupTotal(false, 0);
      }

      return totals(f);
   }

   /**
    * Cat, Month, V, W: (c1,m1,11,1), (c2,m1,21,2), (c3,m2,32,3), (c1,m2,12,4). Month m2 is first
    * seen on the c3 row, the third row tuple.
    */
   private static List<Object[]> newColumnData() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Cat", "Month", "V", "W" });
      rows.add(new Object[] { "c1", "m1", 11.0, 1.0 });
      rows.add(new Object[] { "c2", "m1", 21.0, 2.0 });
      rows.add(new Object[] { "c3", "m2", 32.0, 3.0 });
      rows.add(new Object[] { "c1", "m2", 12.0, 4.0 });
      return rows;
   }

   private static CrossTabFilter newColumn(int maxRows, int... measures) {
      CrossTabFilter f = create(newColumnData(), maxRows, new int[] { 0 }, new int[] { 1 },
                                measures);
      f.setRowHeaders(List.of("Cat"));
      return totals(f);
   }

   private static CrossTabFilter create(List<Object[]> rows, int maxRows, int[] rowh,
                                        int[] colh, int[] dcol)
   {
      SreeEnv.setProperty(PROPERTY, String.valueOf(maxRows));
      Formula[] sums = new Formula[dcol.length];

      for(int k = 0; k < sums.length; k++) {
         sums[k] = new SumFormula();
      }

      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(rows.toArray(new Object[0][])),
                                            rowh, colh, dcol, sums);
      f.setColHeaders(List.of("Month"));
      f.setSummarySideBySide(true);
      return f;
   }

   private static CrossTabFilter totals(CrossTabFilter f) {
      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   // ---------------------------------------------------------------------------------------
   // grid helpers
   // ---------------------------------------------------------------------------------------

   /** The data rows, one line each: the row tuple, then every cell ("_" for none). */
   private static List<String> dump(CrossTabFilter f) {
      List<String> lines = new ArrayList<>();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         StringBuilder line = new StringBuilder(Arrays.toString(f.getRowTuple(r).getRow()));

         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            Object value = f.getObject(r, c);
            line.append('|').append(value instanceof Number ?
               String.valueOf(Math.round(((Number) value).doubleValue())) :
               value == null ? "_" : value.toString());
         }

         lines.add(line.toString());
      }

      return lines;
   }

   /** Every row and column of the whole grid, headers included. */
   private static List<List<Object>> cells(CrossTabFilter f) {
      List<List<Object>> grid = new ArrayList<>();

      for(int r = 0; r < f.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < f.getColCount(); c++) {
            row.add(f.getObject(r, c));
         }

         grid.add(row);
      }

      return grid;
   }

   private static int shownRowTuples(CrossTabFilter f) {
      return f.getRowCount() - f.getHeaderRowCount();
   }

   /**
    * The ways the shown grid disagrees with itself. Every row's total cell of a measure is the
    * sum of that row's shown cells of the measure, and every cell of a group total row or of the
    * grand total row is the sum of the shown detail rows it covers. A total that should hold a
    * value and is empty, or a total row with no shown detail row, is a disagreement too.
    */
   private static List<String> disagreements(CrossTabFilter f, int rowLevels, int measures) {
      List<String> problems = new ArrayList<>();
      int hc = f.getHeaderColCount();
      int hr = f.getHeaderRowCount();

      for(int r = hr; r < f.getRowCount(); r++) {
         Object[] key = f.getRowTuple(r).getRow();
         String label = Arrays.toString(key);

         // the row total of each measure is the sum of the row's cells
         for(int m = 0; m < measures; m++) {
            Double sum = null;
            int totalCol = -1;

            for(int c = hc; c < f.getColCount(); c++) {
               if((c - hc) % measures != m) {
                  continue;
               }

               if(f.getColTuple(c).size() == 0) {
                  totalCol = c;
               }
               else {
                  sum = add(sum, f.getObject(r, c));
               }
            }

            if(totalCol >= 0) {
               check(problems, "row " + label + " measure " + m + " total", sum,
                     f.getObject(r, totalCol));
            }
         }

         if(key.length == rowLevels) {
            continue;
         }

         // a group total or the grand total row is the sum of the detail rows it covers
         boolean covers = false;

         for(int c = hc; c < f.getColCount(); c++) {
            Double sum = null;

            for(int d = hr; d < f.getRowCount(); d++) {
               Object[] detail = f.getRowTuple(d).getRow();

               if(detail.length == rowLevels &&
                  Arrays.equals(key, Arrays.copyOf(detail, key.length)))
               {
                  covers = true;
                  sum = add(sum, f.getObject(d, c));
               }
            }

            check(problems, "total row " + label + " column " + c, sum, f.getObject(r, c));
         }

         if(!covers) {
            problems.add("total row " + label + " covers no shown row");
         }
      }

      // every shown group has its group total row
      if(rowLevels > 1) {
         Set<String> groups = new TreeSet<>();
         Set<String> totals = new TreeSet<>();

         for(int r = hr; r < f.getRowCount(); r++) {
            Object[] key = f.getRowTuple(r).getRow();

            if(key.length == rowLevels) {
               groups.add(String.valueOf(key[0]));
            }
            else if(key.length == 1) {
               totals.add(String.valueOf(key[0]));
            }
         }

         if(!groups.equals(totals)) {
            problems.add("groups " + groups + " but group totals " + totals);
         }
      }

      return problems;
   }

   private static Double add(Double sum, Object value) {
      if(value == null) {
         return sum;
      }

      return (sum == null ? 0 : sum) + ((Number) value).doubleValue();
   }

   private static void check(List<String> problems, String what, Double want, Object shown) {
      if(want == null && shown == null) {
         return;
      }

      if(want == null || shown == null ||
         Math.abs(want - ((Number) shown).doubleValue()) > 1e-9)
      {
         problems.add(what + ": the shown cells add up to " + want + ", it shows " + shown);
      }
   }

   private static void assertConsistent(CrossTabFilter f, int rowLevels, int measures,
                                        String what)
   {
      List<String> problems = disagreements(f, rowLevels, measures);
      assertTrue(problems.isEmpty(), what + ": " + problems.size() + " disagreements:\n" +
         String.join("\n", problems) + "\ngrid:\n" + String.join("\n", dump(f)));
   }

   // ---------------------------------------------------------------------------------------
   // tests
   // ---------------------------------------------------------------------------------------

   /** One row level: 6 categories and the grand total are 7 row tuples. */
   @Test
   void oneRowLevelEveryLimitShowsConsistentTotals() {
      List<List<Object>> full = cells(reported(false, 0));

      for(int limit = 1; limit <= 7; limit++) {
         CrossTabFilter f = reported(false, limit);
         String what = "limit " + limit;

         assertConsistent(f, 1, 1, what);
         assertTrue(shownRowTuples(f) <= limit, what + " shows " + shownRowTuples(f) + "\n" +
            String.join("\n", dump(f)));

         if(limit < 7) {
            assertEquals(limit, f.getAppliedMaxRows(), what);
         }
         else {
            assertEquals(-1, f.getAppliedMaxRows(), what);
            assertEquals(full, cells(f), what);
         }
      }
   }

   /** The reported case: c3 is cut as a whole, and the grand total is c1 + c2. */
   @Test
   void theReportedLimitCutsTheThirdCategoryAsAWhole() {
      CrossTabFilter f = reported(false, 3);

      assertEquals(List.of("[c1]|11|11", "[c2]|21|21", "[]|32|32"), dump(f));
      assertEquals(3, f.getAppliedMaxRows());
   }

   /** Two row levels with a group total: 2 groups, 6 categories and the grand total. */
   @Test
   void twoRowLevelsEveryLimitShowsConsistentGroupAndGrandTotals() {
      List<List<Object>> full = cells(reported(true, 0));

      for(int limit = 1; limit <= 10; limit++) {
         CrossTabFilter f = reported(true, limit);
         String what = "limit " + limit;

         assertConsistent(f, 2, 1, what);
         assertTrue(shownRowTuples(f) <= limit, what + " shows " + shownRowTuples(f) + "\n" +
            String.join("\n", dump(f)));

         if(limit < 9) {
            assertEquals(limit, f.getAppliedMaxRows(), what);
         }
         else {
            assertEquals(-1, f.getAppliedMaxRows(), what);
            assertEquals(full, cells(f), what);
         }
      }
   }

   /** The cut inside a group keeps the group total and the bottom-right grand total cell. */
   @Test
   void theBottomRightGrandTotalCellAgreesWhenTheCutIsInsideAGroup() {
      // [g1], [g1, c1] and [] are the first row's tuples, [g1, c2] the fourth
      CrossTabFilter f = reported(true, 4);
      List<String> lines = dump(f);

      assertEquals(4, lines.size(), String.join("\n", lines));
      assertTrue(lines.containsAll(List.of("[g1, c1]|11|11", "[g1, c2]|21|21", "[g1]|32|32",
                                           "[]|32|32")), String.join("\n", lines));
      assertEquals(32.0, ((Number) f.getObject(f.getRowCount() - 1, f.getColCount() - 1))
         .doubleValue(), 1e-9);
      assertEquals(4, f.getAppliedMaxRows());

      // the first row alone fits: only that row and its totals
      CrossTabFilter three = reported(true, 3);
      lines = dump(three);
      assertEquals(3, lines.size(), String.join("\n", lines));
      assertTrue(lines.containsAll(List.of("[g1, c1]|11|11", "[g1]|11|11", "[]|11|11")),
                 String.join("\n", lines));
   }

   /** When not even the first source row fits, nothing is shown and the cut is reported. */
   @Test
   void aLimitBelowTheFirstSourceRowsTuplesShowsNothingButIsReported() {
      CrossTabFilter flat = reported(false, 1);
      assertEquals(0, shownRowTuples(flat), String.join("\n", dump(flat)));
      assertEquals(1, flat.getAppliedMaxRows());

      for(int limit = 1; limit <= 2; limit++) {
         CrossTabFilter grouped = reported(true, limit);
         assertEquals(0, shownRowTuples(grouped), String.join("\n", dump(grouped)));
         assertEquals(limit, grouped.getAppliedMaxRows());
      }
   }

   /** A column that only the cut row would add is not shown with a lone cell. */
   @Test
   void aColumnFirstSeenOnTheCutRowIsNotShown() {
      CrossTabFilter f = newColumn(3, 2);

      assertEquals(List.of("[c1]|11|11", "[c2]|21|21", "[]|32|32"), dump(f));
      assertEquals(f.getHeaderColCount() + 2, f.getColCount());
      assertEquals(3, f.getAppliedMaxRows());
      assertConsistent(f, 1, 1, "limit 3");
   }

   /** Every measure of the source row at the cut is left out together. */
   @Test
   void twoMeasuresAreCutTogether() {
      CrossTabFilter f = newColumn(3, 2, 3);

      assertEquals(List.of("[c1]|11|1|11|1", "[c2]|21|2|21|2", "[]|32|3|32|3"), dump(f));
      assertEquals(3, f.getAppliedMaxRows());
      assertConsistent(f, 1, 2, "limit 3");

      for(int limit = 1; limit <= 4; limit++) {
         assertConsistent(newColumn(limit, 2, 3), 1, 2, "limit " + limit);
      }
   }

   /** Without totals a cut shows maxrow rows, not maxrow + 1. */
   @Test
   void withoutTotalsTheCutShowsAtMostMaxRowRows() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Cat", "Month", "V" });
      rows.add(new Object[] { "c1", "m1", 11.0 });
      rows.add(new Object[] { "c2", "m1", 21.0 });
      rows.add(new Object[] { "c3", "m1", 99.0 });

      CrossTabFilter cut = create(rows, 2, new int[] { 0 }, new int[] { 1 }, new int[] { 2 });
      cut.setSuppressRowGrandTotal(true);
      cut.setSuppressColumnGrandTotal(true);
      cut.moreRows(Integer.MAX_VALUE);

      assertEquals(List.of("[c1]|11", "[c2]|21"), dump(cut));
      assertEquals(2, cut.getAppliedMaxRows());

      CrossTabFilter fits = create(rows, 3, new int[] { 0 }, new int[] { 1 }, new int[] { 2 });
      fits.setSuppressRowGrandTotal(true);
      fits.setSuppressColumnGrandTotal(true);
      fits.moreRows(Integer.MAX_VALUE);

      assertEquals(List.of("[c1]|11", "[c2]|21", "[c3]|99"), dump(fits));
      assertEquals(-1, fits.getAppliedMaxRows());
   }

   /**
    * Two row and two column levels with every group total and subtotal shown (or every grand
    * total suppressed). A cut grid must be exactly the grid of the source rows before the cut
    * with no limit, and the next source row must not have fit, so the tuples counted before a
    * source row is applied match what that row really adds in every total branch.
    */
   @Test
   void withSubtotalsAndColumnGroupTotalsACutIsExactlyAPrefixOfTheSourceRows() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Group", "Cat", "Quarter", "Month", "V" });

      for(int month = 1; month <= 4; month++) {
         for(int cat = 1; cat <= 4; cat++) {
            rows.add(new Object[] { cat <= 2 ? "g1" : "g2", "c" + cat,
                                    month <= 2 ? "q1" : "q2", "m" + month,
                                    (double) (cat * 10 + month) });
         }
      }

      for(boolean grandTotals : new boolean[] { true, false }) {
         int full = shownRowTuples(allTotals(rows, rows.size() - 1, 0, grandTotals));
         boolean cutSeen = false;

         for(int limit = 1; limit <= full + 1; limit++) {
            CrossTabFilter f = allTotals(rows, rows.size() - 1, limit, grandTotals);
            String what = "grand totals " + grandTotals + ", limit " + limit + "\n" +
               String.join("\n", dump(f));
            assertTrue(shownRowTuples(f) <= limit, what);

            if(limit >= full) {
               assertEquals(-1, f.getAppliedMaxRows(), what);
               assertEquals(cells(allTotals(rows, rows.size() - 1, 0, grandTotals)), cells(f),
                            what);
               continue;
            }

            assertEquals(limit, f.getAppliedMaxRows(), what);
            cutSeen = true;
            List<List<Object>> grid = cells(f);
            int prefix = -1;

            for(int k = 0; k < rows.size(); k++) {
               if(cells(allTotals(rows, k, 0, grandTotals)).equals(grid)) {
                  prefix = k;
                  break;
               }
            }

            assertTrue(prefix >= 0, "not the grid of any prefix of the source rows: " + what);
            assertTrue(shownRowTuples(allTotals(rows, prefix + 1, 0, grandTotals)) > limit,
                       "source row " + (prefix + 1) + " would have fit: " + what);
         }

         assertTrue(cutSeen, "grand totals " + grandTotals);
      }
   }

   /** The first {@code count} source rows with every total shown and the given limit. */
   private static CrossTabFilter allTotals(List<Object[]> rows, int count, int maxRows,
                                           boolean grandTotals)
   {
      SreeEnv.setProperty(PROPERTY, String.valueOf(maxRows));
      Object[][] data = rows.subList(0, count + 1).toArray(new Object[0][]);
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), new int[] { 0, 1 },
                                            new int[] { 2, 3 }, new int[] { 4 },
                                            new Formula[] { new SumFormula() });
      f.setRowHeaders(List.of("Group", "Cat"));
      f.setColHeaders(List.of("Quarter", "Month"));
      f.setSuppressRowGroupTotal(false, 0);
      f.setSuppressColumnGroupTotal(false, 0);
      f.setSuppressRowSubtotal(false);
      f.setSuppressColumnSubtotal(false);
      f.setSuppressRowGrandTotal(!grandTotals);
      f.setSuppressColumnGrandTotal(!grandTotals);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   /** A limit that cuts nothing gives exactly the grid of no limit. */
   @Test
   void aLimitThatDoesNotCutGivesTheSameGridAsNoLimit() {
      List<List<Object>> grouped = cells(reported(true, 0));

      for(int limit : new int[] { 9, 10, 1000 }) {
         CrossTabFilter f = reported(true, limit);
         assertEquals(grouped, cells(f), "limit " + limit);
         assertEquals(-1, f.getAppliedMaxRows(), "limit " + limit);
      }

      List<List<Object>> measures = cells(newColumn(0, 2, 3));

      for(int limit : new int[] { 4, 1000 }) {
         CrossTabFilter f = newColumn(limit, 2, 3);
         assertEquals(measures, cells(f), "limit " + limit);
         assertEquals(-1, f.getAppliedMaxRows(), "limit " + limit);
      }
   }
}
