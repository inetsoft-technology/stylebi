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
package inetsoft.report.composition.graph;

import inetsoft.report.TableLens;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Change and Percent of a Date Comparison crosstab (the date on the rows) against the Value
 * crosstab of the same comparison, through the real crosstab pipeline (the DC query conditions,
 * CrosstabDcProcessor, the CrossTabFilter calculation and hiding the extra first period).
 *
 * <p>The Value crosstab is read with one period more, so the period before the first visible
 * one is there. For every row (period p, part k) of the Change crosstab the expected Change is
 * the value of (p, k) minus the value of (p - 1, k) in the Value crosstab, or empty when there
 * is none; Percent is that over the earlier value. The periods are counted here from the period
 * level of the shape, never taken from the product, and the visible periods must be the Value
 * crosstab's without its first. Each period's Value total is also checked against the days the
 * condition list keeps, so a lost row cannot hide behind a skipped Change.</p>
 *
 * <p>A part the earlier period lacks (day 366 after a common year, week 53) is not judged: it
 * is Bug #79061, finding 4, and the share of such rows is bounded.</p>
 *
 * <p>Combinations: every shape at the datatest End Date with Change, the shapes that use weeks
 * also with Monday weeks, and one shape of each kind with Percent and with toDate + inclusive.
 * The same days as the chart are judged by {@code DateComparisonChangeMatrixTest}; this class
 * covers what the crosstab adds.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonCrosstabChangeMatrixTest {
   private static final String END = "2021-11-10";
   private static final double MAX_UNJUDGED = 0.5;
   private static final LocalDate FROM = LocalDate.of(2017, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);
   private static final int PRE_COUNT = 2;
   /** The part of the subtotal row of a period. */
   private static final String TOTAL = "Total|";

   /** One shape of each kind of period / interval / context, for the Percent and toDate runs. */
   private static final Set<String> SAMPLE = Set.of(
      "Y-all-gM", "Y-ytd-gW-cY", "Y-sameW-gD-cQ", "Y-sameD-gD-cM", "Q-qtd-gM-cQ", "Q-mtd-gW-cM",
      "M-wtd-gW-cM", "M-all-gD", "W-wtd-gD-cW", "D-all-gD");

   /** A year, a quarter and a month period with parts, for the totals. */
   private static final Set<String> WITH_TOTALS = Set.of("Y-all-gM", "Q-qtd-gM-cQ", "M-all-gD");

   private record Case(DcShape shape, String weekStart, boolean toDate, boolean inclusive,
                       boolean percent, boolean onRows, boolean totals)
   {
      @Override
      public String toString() {
         return shape.id() + " " + weekStart + (toDate ? " toDate" : "") +
            (inclusive ? " incl" : "") + (percent ? " percent" : " change") +
            (onRows ? "" : " on columns") + (totals ? " totals" : "");
      }

      DateComparisonInfo info(int option, int preCount) {
         return shape.toInfo(END, toDate, inclusive, option, preCount, true);
      }
   }

   static Stream<Case> cases() {
      List<Case> cases = new ArrayList<>();

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         cases.add(new Case(shape, "sunday", false, false, false, true, false));

         if(shape.usesWeeks()) {
            cases.add(new Case(shape, "monday", false, false, false, true, false));
         }

         if(SAMPLE.contains(shape.id())) {
            cases.add(new Case(shape, "sunday", false, false, true, true, false));
            cases.add(new Case(shape, "sunday", true, true, false, true, false));

            // the date on the columns hides the first period by columns, not by rows
            cases.add(new Case(shape, "sunday", false, false, false, false, false));
            cases.add(new Case(shape, "sunday", false, false, true, false, false));
         }

         // the total of each period with the Trend and Comparison computed for totals
         if(WITH_TOTALS.contains(shape.id())) {
            cases.add(new Case(shape, "sunday", false, false, false, true, true));
            cases.add(new Case(shape, "sunday", false, false, true, true, true));
            cases.add(new Case(shape, "sunday", false, false, false, false, true));
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
      List<String> problems = withWeekStart(c, () -> judge(c));

      assertTrue(problems.isEmpty(),
                 c + ": " + problems.size() + " problems, first:\n" +
                 String.join("\n", problems.subList(0, Math.min(8, problems.size()))));
   }

   private static <T> T withWeekStart(Case c, Callable<T> action) {
      return WeekStartUtil.withWeekStart(c.weekStart(), () -> {
         try {
            return action.call();
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      });
   }

   /** The rows of a crosstab: period ordinal, part, value. */
   private record Rows(List<long[]> periods, List<String> parts, List<Object> values) {
   }

   private List<String> judge(Case c) throws Exception {
      DcShape shape = c.shape();
      List<Object[]> data = new ArrayList<>();

      for(LocalDate day = FROM; day.isBefore(TO); day = day.plusDays(1)) {
         data.add(new Object[] { java.sql.Date.valueOf(day),
                                 DcInMemoryViewsheetFixture.dayValue(day) });
      }

      DcInMemoryViewsheetFixture.Sheet sheet = fixture.open("Date", "Paid", data);
      int level = dateLevel(shape);
      DateComparisonInfo valueInfo = c.info(DateComparisonInfo.VALUE, PRE_COUNT + 1);
      DateComparisonInfo changeInfo = c.info(
         c.percent() ? DateComparisonInfo.PERCENT : DateComparisonInfo.CHANGE, PRE_COUNT);
      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = shape.usesWeeks() && shape.periodLevel() != XConstants.WEEK_DATE_GROUP;
      Rows value = read(fixture.crosstab(sheet, "Value", valueInfo, "Date", "Paid", level,
                                       c.onRows(), c.totals()),
                        shape, c.onRows());
      Rows change = read(fixture.crosstab(sheet, "Change", changeInfo, "Date", "Paid", level,
                                        c.onRows(), c.totals()),
                         shape, c.onRows());
      List<String> problems = new ArrayList<>();

      if(value.values().isEmpty()) {
         return List.of("empty Value crosstab");
      }

      if(change.values().isEmpty()) {
         return List.of("empty Change crosstab");
      }

      // the value of (period, part), and each period's total
      Map<String, Double> valueOf = new HashMap<>();
      TreeMap<Long, Double> totals = new TreeMap<>();

      for(int i = 0; i < value.values().size(); i++) {
         Double v = number(value.values().get(i));

         if(v == null) {
            continue;
         }

         long period = value.periods().get(i)[0];
         boolean total = TOTAL.equals(value.parts().get(i));

         if(valueOf.put(period + "|" + value.parts().get(i), v) != null) {
            problems.add("Value crosstab repeats part " + value.parts().get(i) + " of period " +
                         periods.label(period));
         }

         if(!total) {
            totals.merge(period, v, Double::sum);
         }
      }

      // each period's total is the sum of the days the condition list keeps for it
      Map<Long, Double> expectedTotals = new TreeMap<>();

      for(LocalDate day : DcDays.kept(valueInfo)) {
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

      // the visible periods are the Value crosstab's without its first
      TreeSet<Long> changePeriods = new TreeSet<>();
      change.periods().forEach(p -> changePeriods.add(p[0]));
      TreeSet<Long> expectedPeriods = new TreeSet<>(totals.keySet());
      expectedPeriods.remove(expectedPeriods.first());

      if(!changePeriods.equals(expectedPeriods)) {
         problems.add("Change shows periods " + labels(periods, changePeriods) + ", expected " +
                      labels(periods, expectedPeriods));
      }

      int judged = 0;
      int unjudged = 0;

      for(int i = 0; i < change.values().size(); i++) {
         long period = change.periods().get(i)[0];
         String part = change.parts().get(i);
         Double current = valueOf.get(period + "|" + part);
         Double before = valueOf.get((period - 1) + "|" + part);

         if(current == null) {
            problems.add("Change row " + periods.label(period) + "/" + part + " has no value");
            continue;
         }

         // a part the earlier period lacks: see the class comment
         if(before == null && totals.containsKey(period - 1)) {
            unjudged++;
            continue;
         }

         judged++;
         Double expected = before == null ? null :
            c.percent() ? (current - before) / before : current - before;
         Double actual = number(change.values().get(i));
         boolean same = expected == null ? actual == null :
            actual != null && Math.abs(actual - expected) < 1E-6;

         if(!same) {
            problems.add(periods.label(period) + "/" + part + ": " +
                         (c.percent() ? "percent " : "change ") + actual + ", expected " +
                         expected + " (" + current + " vs " + before + ")");
         }
      }

      if(judged == 0 || unjudged > judged * MAX_UNJUDGED) {
         problems.add("judged " + judged + " rows, left " + unjudged + " unjudged");
      }

      return problems;
   }

   /**
    * The records of the crosstab data. With the date on the rows a record is a row after the
    * header row: the period date, the part columns, the value. On the columns it is a column
    * after the label column: the period date, the part rows, the value.
    */
   private static Rows read(TableLens data, DcShape shape, boolean onRows) {
      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = shape.usesWeeks() && shape.periodLevel() != XConstants.WEEK_DATE_GROUP;
      // a day-level date is in the period its week starts in when weeks are aligned; the others
      // (year, quarter, month, week dates) already are the start of their unit
      boolean dayDate = shape.periodLevel() == XConstants.DAY_DATE_GROUP;
      int records = onRows ? data.getRowCount() : data.getColCount();
      int fields = onRows ? data.getColCount() : data.getRowCount();
      List<long[]> ordinals = new ArrayList<>();
      List<String> parts = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      for(int record = 1; record < records; record++) {
         Object date = field(data, onRows, record, 0);

         if(!(date instanceof Date)) {
            continue;
         }

         LocalDate day = DcPeriods.toLocal((Date) date);
         StringBuilder part = new StringBuilder();

         for(int f = 1; f < fields - 1; f++) {
            part.append(field(data, onRows, record, f)).append('|');
         }

         ordinals.add(new long[] { periods.ordinal(aligned && dayDate ? periods.weekStart(day)
                                                                       : day) });
         parts.add(part.toString());
         values.add(field(data, onRows, record, fields - 1));
      }

      return new Rows(ordinals, parts, values);
   }

   private static Object field(TableLens data, boolean onRows, int record, int field) {
      return onRows ? data.getObject(record, field) : data.getObject(field, record);
   }

   private static int dateLevel(DcShape shape) {
      switch(shape.granularity()) {
      case DateComparisonInfo.YEAR:
         return XConstants.YEAR_DATE_GROUP;
      case DateComparisonInfo.QUARTER:
         return XConstants.QUARTER_DATE_GROUP;
      case DateComparisonInfo.MONTH:
         return XConstants.MONTH_DATE_GROUP;
      case DateComparisonInfo.WEEK:
         return XConstants.WEEK_DATE_GROUP;
      default:
         return XConstants.DAY_DATE_GROUP;
      }
   }

   private static Double number(Object value) {
      return value instanceof Number ? ((Number) value).doubleValue() : null;
   }

   private static List<String> labels(DcPeriods periods, Collection<Long> ordinals) {
      List<String> labels = new ArrayList<>();
      ordinals.forEach(o -> labels.add(periods.label(o)));

      return labels;
   }
}
