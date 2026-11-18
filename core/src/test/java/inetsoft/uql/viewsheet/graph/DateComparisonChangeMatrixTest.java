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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.test.dc.DcDays;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.test.dc.DcPeriods;
import inetsoft.test.dc.DcShape;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every row of a Date Comparison chart's Change / Percent column is the row's value minus (or
 * relative to) the value of the same part one period earlier, for every legal combination of
 * the dialog.
 *
 * <p>The chart runs through the real pipeline over an in-memory table
 * ({@link DcInMemoryViewsheetFixture}); the judge reads the chart's data set and does not call
 * the product's Change code. Daily values are pseudo-random, so no two periods have the same
 * total. For each row (period p, part k, value v) it expects the Change to be v minus the value
 * of the row (p - 1, k), or empty when there is none. The previous period p - 1 is worked out
 * here from the period level of the combination, never taken from the product: the one date
 * column is the row's period (its year, quarter, month or week by that level; a week belongs to
 * the period it starts in) and every other grouping column is the part.</p>
 *
 * <p>Besides the Change it checks, from the days the condition list keeps, that each period's
 * Value total is the sum of that period's days and that the periods are consecutive, so a row
 * lost from a period cannot hide behind a skipped Change. Two kinds of result are not judged
 * here: a part the previous period lacks (its Change is the whole value, see
 * changeOfAPartTheEarlierPeriodLacksIsTheValue()).
 * Everything else must have rows, and the share of rows not judged is bounded.</p>
 *
 * <p>Combinations: every shape at the datatest End Date; the shapes that use weeks also at
 * 2021-01-02 and with Monday weeks; one shape of each kind with toDate + inclusive and Percent;
 * the Week-period shapes at the two daylight saving End Dates (they need the surefire zone
 * America/New_York); the SAMPLE shapes also with Change + Value and Percent + Value, which plot
 * the value as well as the Change from the same data set.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DateComparisonChangeMatrixTest {
   private static final String[] DST_END_DAYS = { "2021-03-14", "2021-11-07" };
   /** The largest share of rows whose part the previous period lacks (small charts have a few). */
   private static final double MAX_UNJUDGED = 0.5;
   private static final LocalDate FROM = LocalDate.of(2017, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);

   private record Case(DcShape shape, String weekStart, String endDay, boolean toDate,
                       boolean inclusive, int option)
   {
      @Override
      public String toString() {
         return shape.id() + " " + weekStart + " end=" + endDay +
            (toDate ? " toDate" : "") + (inclusive ? " incl" : "") +
            (percent() ? " percent" : " change") + (valuePlus() ? "+value" : "");
      }

      boolean percent() {
         return option == DateComparisonInfo.PERCENT || option == DateComparisonInfo.PERCENT_VALUE;
      }

      /** Change and value together: the value is plotted too, the Change on top of it. */
      boolean valuePlus() {
         return option == DateComparisonInfo.CHANGE_VALUE ||
            option == DateComparisonInfo.PERCENT_VALUE;
      }

      DateComparisonInfo info() {
         return shape.toInfo(endDay, toDate, inclusive, option, 2, true);
      }
   }

   /**
    * Kept near a minute for the whole class: add End Dates only for shapes that need them.
    */
   static Stream<Case> cases() {
      List<Case> cases = new ArrayList<>();

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         cases.add(new Case(shape, "sunday", "2021-11-10", false, false, DateComparisonInfo.CHANGE));

         if(shape.usesWeeks()) {
            cases.add(new Case(shape, "sunday", "2021-01-02", false, false,
                               DateComparisonInfo.CHANGE));
            cases.add(new Case(shape, "monday", "2021-01-02", false, false,
                               DateComparisonInfo.CHANGE));
            cases.add(new Case(shape, "monday", "2021-11-10", false, false,
                               DateComparisonInfo.CHANGE));
         }

         if(shape.periodLevel() == XConstants.WEEK_DATE_GROUP) {
            for(String end : DST_END_DAYS) {
               cases.add(new Case(shape, "sunday", end, false, false, DateComparisonInfo.CHANGE));
            }
         }

         if(DcShape.SAMPLE.contains(shape.id())) {
            cases.add(new Case(shape, "sunday", "2021-11-10", true, true,
                               DateComparisonInfo.CHANGE));
            cases.add(new Case(shape, "sunday", "2021-11-10", false, false,
                               DateComparisonInfo.PERCENT));

            // Change and Percent together with the value
            cases.add(new Case(shape, "sunday", "2021-11-10", false, false,
                               DateComparisonInfo.CHANGE_VALUE));
            cases.add(new Case(shape, "sunday", "2021-11-10", false, false,
                               DateComparisonInfo.PERCENT_VALUE));
         }
      }

      return cases.stream();
   }

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void changeIsAgainstThePreviousPeriod(Case c) {
      if(Arrays.asList(DST_END_DAYS).contains(c.endDay())) {
         assumeTrue("America/New_York".equals(ZoneId.systemDefault().getId()),
                    "the daylight saving weeks need the surefire zone America/New_York");
      }

      List<String> problems = withWeekStart(c, () -> judge(c));

      assertTrue(problems.isEmpty(),
                 c + ": " + problems.size() + " problems, first:\n" +
                 String.join("\n", problems.subList(0, Math.min(8, problems.size()))));
   }

   /**
    * Quarter / Quarter to Date / Week with the End Date in the first days of the year (here
    * 2021-01-02) left the chart without a single row, for both first days of week (#79347).
    * The exact ranges are asserted in DateComparisonWeekToDateRangeTest.
    */
   @Test
   void weekToDateWithAnEndDateInTheFirstDaysHasData() {
      for(String shapeId : new String[] { "Q-qtd-gW-cQ", "Q-mtd-gW-cQ" }) {
         for(String ws : new String[] { "sunday", "monday" }) {
            Case c = new Case(DcShape.byId(shapeId), ws, "2021-01-02", false, false,
                              DateComparisonInfo.CHANGE);
            int rows = withWeekStart(c, () -> data(c).getRowCount());

            assertTrue(rows > 0, c + " has no rows");
         }
      }
   }

   /**
    * Compare All by day, Year periods: 2020 has a day 366 and 2019 has none. The product's
    * Change for that day is its whole value (the missing 2019 value counts as 0); the matrix
    * does not judge such rows. The earlier period has the row with an empty value, and
    * ChangeColumn counts an empty earlier value as 0, as for any empty part.
    */
   @Test
   void changeOfAPartTheEarlierPeriodLacksIsTheValue() {
      Case c = new Case(DcShape.byId("Y-all-gD"), "sunday", "2021-11-10", false, false,
                        DateComparisonInfo.CHANGE);
      DataSet data = withWeekStart(c, () -> data(c));
      int columns = data.getColCount();
      Object value = null;
      Object change = "no row";

      for(int r = 0; r < data.getRowCount(); r++) {
         // day 366 of 2020, the only one with a value
         if(Integer.valueOf(366).equals(data.getData(0, r)) && data.getData(columns - 2, r) != null) {
            value = data.getData(columns - 2, r);
            change = data.getData(columns - 1, r);
         }
      }

      assertNotNull(value, "no value on day 366");
      assertEquals(((Number) value).doubleValue(), ((Number) change).doubleValue(), 1e-9);
   }

   private <T> T withWeekStart(Case c, Callable<T> action) {
      return WeekStartUtil.withWeekStart(c.weekStart(), () -> {
         try {
            return action.call();
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      });
   }

   /** The data set of the chart the case configures. */
   private DataSet data(Case c) throws Exception {
      return pair(c).getData();
   }

   private VGraphPair pair(Case c) throws Exception {
      return fixture.chart(c.info(), XConstants.YEAR_DATE_GROUP, FROM, TO,
                           DcInMemoryViewsheetFixture::dayValue);
   }

   /** The variables the graph plots, element by element, in order. */
   private static List<String> plottedVars(EGraph graph) {
      List<String> vars = new ArrayList<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         for(int v = 0; v < graph.getElement(i).getVarCount(); v++) {
            vars.add(graph.getElement(i).getVar(v));
         }
      }

      return vars;
   }

   /** What is wrong with the chart of a case; empty when it is as expected. */
   private List<String> judge(Case c) throws Exception {
      DcShape shape = c.shape();
      VGraphPair pair = pair(c);
      DataSet data = pair.getData();

      if(data.getRowCount() == 0) {
         return List.of("empty chart: " + headers(data));
      }

      int changeCol = data.getColCount() - 1;
      int valueCol = data.getColCount() - 2;

      // exactly one grouping column holds dates: it is the row's period
      int dateCol = -1;

      for(int col = 0; col < valueCol; col++) {
         Object first = null;

         for(int r = 0; r < data.getRowCount() && first == null; r++) {
            first = data.getData(col, r);
         }

         if(first instanceof Date) {
            if(dateCol >= 0) {
               return List.of("more than one date column in " + headers(data));
            }

            dateCol = col;
         }
      }

      if(dateCol < 0) {
         return List.of("no date column in " + headers(data));
      }

      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = shape.usesWeeks() && shape.periodLevel() != XConstants.WEEK_DATE_GROUP;
      // a day-level date is in the period its week starts in when weeks are aligned; the others
      // (year, quarter, month, week dates) already are the start of their unit
      boolean dayDate = String.valueOf(data.getHeader(dateCol)).startsWith("Day");
      Map<String, Integer> rows = new HashMap<>();
      TreeMap<Long, Double> totals = new TreeMap<>();
      List<String> problems = new ArrayList<>();

      // Change alone plots the Change; with the value, the value is plotted too and the Change
      // on top of it, from the same data set
      List<String> plotted = plottedVars(pair.getEGraph());
      List<String> expectedPlotted = c.valuePlus() ?
         List.of(data.getHeader(valueCol), data.getHeader(changeCol)) :
         List.of(data.getHeader(changeCol));

      if(!plotted.equals(expectedPlotted)) {
         problems.add("plots " + plotted + ", expected " + expectedPlotted +
                      " (the order matters: the value, then the Change)");
      }

      long[] periodOf = new long[data.getRowCount()];
      String[] partOf = new String[data.getRowCount()];

      for(int r = 0; r < data.getRowCount(); r++) {
         if(data.getData(valueCol, r) == null) {
            continue; // a filled-in empty cell
         }

         StringBuilder part = new StringBuilder();

         for(int col = 0; col < valueCol; col++) {
            if(col != dateCol) {
               part.append(data.getData(col, r)).append('|');
            }
         }

         LocalDate date = DcPeriods.toLocal((Date) data.getData(dateCol, r));
         partOf[r] = part.toString();
         periodOf[r] = periods.ordinal(aligned && dayDate ? periods.weekStart(date) : date);
         totals.merge(periodOf[r], number(data.getData(valueCol, r)), Double::sum);

         if(rows.put(partOf[r] + periodOf[r], r) != null) {
            problems.add("row " + r + " repeats part " + partOf[r] + " of period " + periodOf[r]);
         }
      }

      // the periods are consecutive: no period lost between the first and the last
      for(long period = totals.firstKey(); period <= totals.lastKey(); period++) {
         if(!totals.containsKey(period)) {
            problems.add("no row in period " + periods.label(period));
         }
      }

      // each period's Value total is the sum of the days the condition list keeps for it
      Map<Long, Double> expectedTotals = new TreeMap<>();

      for(LocalDate day : DcDays.kept(c.info())) {
         expectedTotals.merge(periods.ordinal(aligned ? periods.weekStart(day) : day),
                              (double) DcInMemoryViewsheetFixture.dayValue(day), Double::sum);
      }

      for(Map.Entry<Long, Double> e : expectedTotals.entrySet()) {
         Double actual = totals.get(e.getKey());

         if(actual == null || Math.abs(actual - e.getValue()) > 1E-6) {
            problems.add("period " + periods.label(e.getKey()) + " total " + actual +
                         ", the days add up to " + e.getValue());
         }
      }

      boolean percent = c.percent();
      int judged = 0;
      int unjudged = 0;

      for(int r = 0; r < data.getRowCount(); r++) {
         if(partOf[r] == null) {
            continue;
         }

         Integer previous = rows.get(partOf[r] + (periodOf[r] - 1));

         // A part the previous period does not have (day 366 after a common year, day 31 after
         // a 30-day month, week 53): the product's Change is the whole value, as if the previous
         // were 0 (ChangeColumn counts an empty earlier value as 0). These rows are not judged
         // here; see changeOfAPartTheEarlierPeriodLacksIsTheValue().
         if(previous == null && totals.containsKey(periodOf[r] - 1)) {
            unjudged++;
            continue;
         }

         judged++;
         double value = number(data.getData(valueCol, r));
         Double expected = null;

         if(previous != null) {
            double before = number(data.getData(valueCol, previous));
            // Percent is the ratio; the percent format is applied when it is displayed
            expected = percent ? (value - before) / before : value - before;
         }

         Object actual = data.getData(changeCol, r);
         boolean same = expected == null ? actual == null :
            actual instanceof Number && Math.abs(((Number) actual).doubleValue() - expected) < 1E-6;

         if(!same) {
            problems.add(data.getData(dateCol, r) + " " + partOf[r] + " value=" + value +
                         " expected=" + expected + " actual=" + actual);
         }
      }

      // only the extra period fetched for Change is hidden: the rows of the requested periods
      // are plotted and the earlier ones are not (DateComparisonUtil.applyDateRange)
      GraphtDataSelector selector = pair.getEGraph().getElementCount() > 0 ?
         pair.getEGraph().getElement(0).getGraphDataSelector() : null;
      LocalDate endDay = LocalDate.parse(c.endDay());
      long firstRequested = periods.ordinal(aligned ? periods.weekStart(endDay) : endDay) - 2;

      for(int r = 0; r < data.getRowCount(); r++) {
         if(partOf[r] != null) {
            boolean visible = selector == null || selector.accept(data, r, null);

            if(visible != periodOf[r] >= firstRequested) {
               problems.add("row of " + periods.label(periodOf[r]) + " is " +
                            (visible ? "plotted" : "hidden") + ", the requested periods start at " +
                            periods.label(firstRequested));
               break;
            }
         }
      }

      if(judged == 0 || unjudged > MAX_UNJUDGED * (judged + unjudged)) {
         problems.add("judged " + judged + " rows, left " + unjudged + " unjudged");
      }

      return problems;
   }

   private static double number(Object value) {
      return ((Number) value).doubleValue();
   }

   private static String headers(DataSet data) {
      StringBuilder sb = new StringBuilder();

      for(int col = 0; col < data.getColCount(); col++) {
         sb.append(data.getHeader(col)).append(" | ");
      }

      return sb.toString();
   }
}
