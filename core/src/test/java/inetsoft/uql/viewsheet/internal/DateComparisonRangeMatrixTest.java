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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.*;
import inetsoft.test.dc.DcDays;
import inetsoft.test.dc.DcModel;
import inetsoft.test.dc.DcPeriods;
import inetsoft.test.dc.DcShape;
import inetsoft.test.dc.DcSnapshot;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which days each Date Comparison period takes, for every legal combination of the dialog
 * (period level x interval level x granularity x context level, plus Custom Periods), both
 * first days of week, a spread of End Dates around year / quarter / month / week boundaries
 * and the leap day, toDate and inclusive on and off, Value and Change.
 *
 * <p>Only the public entry points {@link DateComparisonInfo#getDateComparisonConditions} and
 * {@link DateComparisonInfo#getStartDate()} are called. The whole condition list is evaluated
 * with the real {@link ConditionGroup} on every day of a window around the End Date, and the
 * surviving days are bucketed by period. What is checked:</p>
 *
 * <ul>
 *   <li><b>Invariants</b> on every combination: no day outside the requested periods (an extra
 *   year, a spill into the next year), no requested period without days (for Compare All), no
 *   day past the End Date for All / To Date intervals. A violation fails
 *   {@link #noInvariantViolations}; a requested period without days under another interval is
 *   not asserted.</li>
 *   <li>The <b>model</b> of the dialog wording ({@link #keptDaysMatchTheDialogWording}) and
 *   <b>hand-computed anchors</b> ({@link #handComputedAnchors}).</li>
 *   <li>A <b>snapshot of the derived grouping shape</b> of every shape (week alignment, series,
 *   previous-period type, temp groups): {@code dc-matrix/dc-shapes.txt}, reviewed once and
 *   afterwards only its diff is reviewed.</li>
 * </ul>
 *
 * <p>Update the snapshot with {@code -Ddc.snapshot.update=true}.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DateComparisonRangeMatrixTest {
   private static final String SHAPES_SNAPSHOT = "inetsoft/uql/viewsheet/internal/dc-matrix/dc-shapes.txt";
   private static final String[] WEEK_STARTS = { "sunday", "monday" };
   /**
    * 2020-12-31 / 2021-01-05 around a year start, 2021-01-02 a Saturday whose week starts in
    * the previous year, 2021-03-02 / 2021-04-02 whose weeks start in the previous month /
    * quarter, 2021-03-31 a quarter end, 2020-02-29 the leap day, 2021-11-10 the datatest day.
    */
   private static final String[] END_DAYS = {
      "2020-12-31", "2021-01-02", "2021-01-05", "2021-03-02", "2021-03-31", "2021-04-02",
      "2020-02-29", "2021-11-10" };
   private static final String DATATEST_END = "2021-11-10";

   /** The generator reproduces the dialog's 105 Standard Periods shapes and 6 Custom ones. */
   @Test
   void shapeCountsPerPeriodLevel() {
      Map<Integer, Long> counts = DcShape.all().stream()
         .collect(Collectors.groupingBy(DcShape::periodLevel, Collectors.counting()));

      assertEquals(55, counts.get(XConstants.YEAR_DATE_GROUP));
      assertEquals(30, counts.get(XConstants.QUARTER_DATE_GROUP));
      assertEquals(14, counts.get(XConstants.MONTH_DATE_GROUP));
      assertEquals(5, counts.get(XConstants.WEEK_DATE_GROUP));
      assertEquals(1, counts.get(XConstants.DAY_DATE_GROUP));
      assertEquals(6, counts.get(-1));
   }

   /** Every generated shape is one the dialog's own validity check accepts. */
   @Test
   void everyShapeIsValid() {
      for(DcShape shape : DcShape.all()) {
         if(!shape.isCustom()) {
            assertFalse(info(shape, variant(DATATEST_END, false, false)).invalid(),
                        () -> shape + " is invalid");
         }
      }
   }

   /**
    * The matrix finds the kept days by evaluating the real condition list once per stretch
    * between range bounds ({@link DcDays#kept}); that must give the days of evaluating it on
    * every day.
    */
   @Test
   void keptDaysFoundPerStretchAreTheDaysFoundPerDay() {
      LocalDate from = LocalDate.of(2015, 1, 1);
      LocalDate to = LocalDate.of(2023, 12, 31);

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : shape.usesWeeks() ? WEEK_STARTS : new String[] { "sunday" }) {
            for(boolean inclusive : new boolean[] { false, true }) {
               String end = inclusive ? "2021-01-02" : DATATEST_END;
               DateComparisonInfo dc = info(shape, variant(end, false, inclusive));
               List<LocalDate> days = WeekStartUtil.withWeekStart(weekStart, () -> {
                  assertEquals(DcDays.keptDayByDay(dc, from, to), DcDays.kept(dc),
                               () -> shape + " " + weekStart + " end=" + end);
                  return null;
               });
            }
         }
      }
   }

   /** The oracle derives week alignment from the shape; the product has to agree. */
   @Test
   void alignWeekMatchesTheShape() {
      for(DcShape shape : DcShape.all()) {
         if(!shape.isCustom()) {
            assertEquals(aligned(shape), info(shape, variant(DATATEST_END, false, false)).alignWeek(),
                         shape::toString);
         }
      }
   }

   /**
    * The days the comparison keeps are the days {@link DcModel} works out from the wording of the
    * dialog: for every shape the model covers, with the End Dates of the matrix, both first days
    * of week where weeks matter, the current period included or not, and Change at two End Dates.
    */
   @Test
   void keptDaysMatchTheDialogWording() {
      List<String> mismatches = new ArrayList<>();
      int checked = 0;

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : shape.usesWeeks() ? WEEK_STARTS : new String[] { "sunday" }) {
            for(String end : END_DAYS) {
               for(boolean inclusive : new boolean[] { false, true }) {
                  for(boolean change : new boolean[] { false, true }) {
                     if(change && (inclusive || !(end.equals("2021-01-02") || end.equals(DATATEST_END)))) {
                        continue;
                     }

                     String message = WeekStartUtil.withWeekStart(weekStart, () -> {
                        LocalDate endDay = LocalDate.parse(end);
                        Set<LocalDate> model = DcModel.expected(shape, endDay, inclusive, change, 2);

                        if(model == null) {
                           return "";
                        }

                        Set<LocalDate> kept = new TreeSet<>(DcDays.kept(shape.toInfo(
                           end, false, inclusive,
                           change ? DateComparisonInfo.CHANGE : DateComparisonInfo.VALUE, 2, true)));

                        if(kept.equals(model)) {
                           return null;
                        }

                        return shape + " " + weekStart + " end=" + end +
                           (inclusive ? " incl" : "") + (change ? " change" : "") + ": " +
                           DcModel.difference(model, kept);
                     });

                     if(message == null) {
                        checked++;
                     }
                     else if(!message.isEmpty()) {
                        mismatches.add(message);
                     }
                  }
               }
            }
         }
      }

      assertTrue(checked > 1000, "the model covers only " + checked + " combinations");
      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " of " + (checked + mismatches.size()) + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(40, mismatches.size()))));
   }

   /**
    * The weekday of a week that starts in the period is kept even when it falls in the next
    * period: the week Sunday 2019-12-29 to 2020-01-04 is a 2019 week, so Wednesday 2020-01-01 is
    * the Wednesday of its last week, as Compare All by Week takes the whole week.
    */
   @Test
   void sameWeekdayOfAWeekThatStartsInThePeriodIsKept() {
      DcShape shape = DcShape.byId("Y-sameD-gD-cW");
      List<LocalDate> kept = WeekStartUtil.withWeekStart("sunday", () -> DcDays.kept(
         shape.toInfo(DATATEST_END, false, false, DateComparisonInfo.VALUE, 2, true)));

      assertTrue(kept.contains(LocalDate.of(2020, 1, 1)), "kept days " + kept);
   }

   /**
    * What the chart derives from each combination of the dialog (week alignment, whether a period
    * series is created, the previous-period type of Change, the date level and the temp groups),
    * one line per shape. This is the test that the derivation of the temp groups lost (Bug #76388).
    */
   @Test
   void derivedShapeOfEveryCombination() {
      List<String> lines = new ArrayList<>();

      for(DcShape shape : DcShape.all()) {
         if(!shape.isCustom()) {
            lines.add(shape.id() + " | value: " + shapeLine(shape, DateComparisonInfo.VALUE) +
                      " | change: " + shapeLine(shape, DateComparisonInfo.CHANGE));
         }
      }

      assertTrue(lines.stream().noneMatch(line -> line.contains("ERROR")), String.join("\n", lines));
      DcSnapshot.verify(SHAPES_SNAPSHOT, lines);
   }

   /** The invariants hold for every combination. */
   @Test
   void noInvariantViolations() {
      List<String> violations = new ArrayList<>();
      DcShape.all().forEach(shape -> violations.addAll(violations(shape)));

      assertTrue(violations.isEmpty(),
                 violations.size() + " violations, first:\n" +
                 violations.stream().limit(20).collect(Collectors.joining("\n")));
   }

   // -- hand-computed anchors --------------------------------------------------------------

   /**
    * Cells worked out by hand from the dialog's own wording (for example "Quarter to Date for
    * Same Quarter of Each Year As Of", "Same Week of Month As"), independent of the snapshot.
    * Weeks are the full weeks that start inside the period, so a week belongs to the period its
    * first day is in. Format: {@code period=first..last/days}, one per compared period.
    */
   @ParameterizedTest(name = "{0} {1} end={2} td={3} inc={4} {5} pre={6}")
   @CsvSource(delimiter = ';', textBlock = """
      # shape;            ws;     end;        td;  inc; opt; pre; expected
      Y-sameD-gD-cY;      sunday; 2021-01-05; off; off; val; 2;   2019=2019-01-05..2019-01-05/1d, 2020=2020-01-05..2020-01-05/1d
      Y-sameD-gD-cM;      sunday; 2021-03-15; off; off; val; 2;   2019=2019-01-15..2019-12-15/12d, 2020=2020-01-15..2020-12-15/12d
      Y-sameM-gD-cY;      sunday; 2021-03-15; off; off; val; 2;   2019=2019-03-01..2019-03-31/31d, 2020=2020-03-01..2020-03-31/31d
      Y-sameQ-gM-cY;      sunday; 2021-11-10; off; off; val; 2;   2019=2019-10-01..2019-12-31/92d, 2020=2020-10-01..2020-12-31/92d
      Y-ytd-gM-cY;        sunday; 2021-11-10; off; off; val; 2;   2019=2019-01-01..2019-11-10/314d, 2020=2020-01-01..2020-11-10/315d
      Y-all-gM;           sunday; 2021-11-10; off; off; val; 2;   2019=2019-01-01..2019-12-31/365d, 2020=2020-01-01..2020-12-31/366d
      Y-all-gM;           sunday; 2021-11-10; off; on;  val; 2;   2019=2019-01-01..2019-12-31/365d, 2020=2020-01-01..2020-12-31/366d, 2021=2021-01-01..2021-11-10/314d
      Y-all-gM;           sunday; 2021-11-10; off; off; chg; 2;   2018=2018-01-01..2018-12-31/365d, 2019=2019-01-01..2019-12-31/365d, 2020=2020-01-01..2020-12-31/366d
      Y-all-gW;           sunday; 2021-01-03; off; off; val; 2;   2019=2019-01-06..2020-01-04/364d, 2020=2020-01-05..2021-01-02/364d
      Y-all-gW;           monday; 2021-01-03; off; off; val; 2;   2018=2018-01-01..2019-01-06/371d, 2019=2019-01-07..2020-01-05/364d
      Q-all-gM;           sunday; 2021-04-02; off; off; val; 2;   2020Q4=2020-10-01..2020-12-31/92d, 2021Q1=2021-01-01..2021-03-31/90d
      Q-all-gW;           sunday; 2021-04-02; off; off; val; 2;   2020Q3=2020-07-05..2020-10-03/91d, 2020Q4=2020-10-04..2021-01-02/91d
      Q-qtd-gM-cQ;        sunday; 2021-11-10; off; off; val; 2;   2021Q2=2021-04-01..2021-05-10/40d, 2021Q3=2021-07-01..2021-08-10/41d
      Q-mtd-gD-cQ;        sunday; 2021-11-10; off; off; val; 2;   2021Q2=2021-05-01..2021-05-10/10d, 2021Q3=2021-08-01..2021-08-10/10d
      Q-wtd-gW-cQ;        sunday; 2021-11-10; off; off; val; 2;   2021Q2=2021-05-09..2021-05-12/4d, 2021Q3=2021-08-08..2021-08-11/4d
      M-sameW-gD-cM;      sunday; 2021-11-10; off; off; val; 2;   2021-09=2021-09-05..2021-09-11/7d, 2021-10=2021-10-03..2021-10-09/7d
      W-wtd-gD-cW;        sunday; 2021-11-10; off; off; val; 2;   wk2021-10-24=2021-10-24..2021-10-27/4d, wk2021-10-31=2021-10-31..2021-11-03/4d
      W-wtd-gD-cW;        monday; 2021-11-10; off; off; val; 2;   wk2021-10-25=2021-10-25..2021-10-27/3d, wk2021-11-01=2021-11-01..2021-11-03/3d
      W-sameD-gD-cW;      sunday; 2021-11-10; off; off; val; 2;   wk2021-10-24=2021-10-27..2021-10-27/1d, wk2021-10-31=2021-11-03..2021-11-03/1d
      """)
   void handComputedAnchors(String shapeId, String weekStart, String end, String toDate,
                            String inclusive, String option, int preCount, String expected)
   {
      DcShape shape = DcShape.byId(shapeId);
      Variant v = variant(end, "on".equals(toDate), "on".equals(inclusive),
                          "chg".equals(option) ? DateComparisonInfo.CHANGE : DateComparisonInfo.VALUE,
                          preCount, true);

      assertEquals(expected, WeekStartUtil.withWeekStart(weekStart, () -> anchorPeriods(shape, v)));
   }

   private static String anchorPeriods(DcShape shape, Variant v) {
      DateComparisonInfo dc = info(shape, v);
      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = aligned(shape);
      TreeMap<Long, List<LocalDate>> byPeriod = new TreeMap<>();

      for(LocalDate day : DcDays.kept(dc)) {
         byPeriod.computeIfAbsent(periods.ordinal(aligned ? periods.weekStart(day) : day),
                                  k -> new ArrayList<>()).add(day);
      }

      return byPeriod.entrySet().stream()
         .map(e -> periods.label(e.getKey()) + "=" + e.getValue().get(0) + ".." +
            e.getValue().get(e.getValue().size() - 1) + "/" + e.getValue().size() + "d")
         .collect(Collectors.joining(", "));
   }

   // -- results that differ from the hand computation: dev to confirm ------------------------

   /**
    * "Week to Date for Same Week of Each Year As Of" a Wednesday in the week of 2021-11-07.
    * The same week of the same month is matched, not the week of the year (#63990): the week of
    * 2021-11-10 is the first full week of November, so 2019-11-03..06 and 2020-11-01..04.
    */
   @Test
   void yearWeekToDateMatchesTheSameWeekOfMonth() {
      DcShape shape = DcShape.byId("Y-wtd-gW-cY");

      assertEquals("2019=2019-11-03..2019-11-06/4d, 2020=2020-11-01..2020-11-04/4d",
                   WeekStartUtil.withWeekStart("sunday", () -> anchorPeriods(
                      shape, variant("2021-11-10", false, false))));
   }

   /**
    * Same Day by Week with the End Date on the last day of its week (Saturday 2021-01-02) and
    * the current period included: the current week's own Saturday is the End Date itself, but
    * the product returns the two previous weeks only.
    */
   @Test
   void sameDayKeepsTheEndDatesOwnWeek() {
      DcShape shape = DcShape.byId("W-sameD-gD-cW");

      assertEquals("wk2020-12-13=2020-12-19..2020-12-19/1d, wk2020-12-20=2020-12-26..2020-12-26/1d, " +
                   "wk2020-12-27=2021-01-02..2021-01-02/1d",
                   WeekStartUtil.withWeekStart("sunday", () -> anchorPeriods(
                      shape, variant("2021-01-02", false, true))));
   }

   // -- invariants of every combination ---------------------------------------------------

   private static final String[][][] CUSTOM_PERIODS = {
      { { "2019-01-01", "2019-03-31" }, { "2020-01-01", "2020-03-31" } },
      { { "2020-12-28", "2021-01-10" }, { "2021-02-01", "2021-02-28" } },
      { { "2020-02-27", "2020-03-02" }, { "2021-02-27", "2021-03-02" } } };

   private record Variant(String endDay, boolean toDate, boolean inclusive, int option,
                          int preCount, boolean intervalInclusive)
   {
      @Override
      public String toString() {
         return "end=" + endDay + " td=" + toDate + " inc=" + inclusive +
            " opt=" + (option == DateComparisonInfo.VALUE ? "val" : "chg") + " pre=" + preCount +
            (intervalInclusive ? "" : " intInc=false");
      }
   }

   private static Variant variant(String endDay, boolean toDate, boolean inclusive) {
      return variant(endDay, toDate, inclusive, DateComparisonInfo.VALUE, 2, true);
   }

   private static Variant variant(String endDay, boolean toDate, boolean inclusive, int option,
                                  int preCount, boolean intervalInclusive)
   {
      return new Variant(endDay, toDate, inclusive, option, preCount, intervalInclusive);
   }

   /**
    * Whether weeks are aligned: a week shape whose period is not itself a week. Taken from the
    * shape, so that the check does not follow {@link DateComparisonInfo#alignWeek()}; see
    * {@link #alignWeekMatchesTheShape()}.
    */
   private static boolean aligned(DcShape shape) {
      return shape.usesWeeks() && shape.periodLevel() != XConstants.WEEK_DATE_GROUP;
   }

   private static DateComparisonInfo info(DcShape shape, Variant v) {
      return shape.toInfo(v.endDay(), v.toDate(), v.inclusive(), v.option(), v.preCount(),
                          v.intervalInclusive());
   }

   /** The settings a Standard Periods shape is checked with, each as Value and as Change. */
   private static List<Variant> variants(DcShape shape) {
      List<Variant> list = new ArrayList<>();
      int[] options = { DateComparisonInfo.VALUE, DateComparisonInfo.CHANGE };

      for(int option : options) {
         // toDate and inclusive both off / both on at every End Date, the mixed pairs at two
         for(String end : END_DAYS) {
            for(boolean both : new boolean[] { false, true }) {
               list.add(variant(end, both, both, option, 2, true));
            }
         }

         for(String end : new String[] { "2021-01-02", DATATEST_END }) {
            list.add(variant(end, false, true, option, 2, true));
            list.add(variant(end, true, false, option, 2, true));

            // an interval whose own End Date is not included
            if(shape.isToDate()) {
               list.add(variant(end, false, false, option, 2, false));
            }
         }

         // the number of periods back
         for(int preCount : new int[] { 1, 3 }) {
            list.add(variant(DATATEST_END, false, false, option, preCount, true));
         }
      }

      return list;
   }

   /** What breaks an invariant in the combinations of a shape, with both first days of week. */
   private static List<String> violations(DcShape shape) {
      List<String> found = new ArrayList<>();

      for(String weekStart : WEEK_STARTS) {
         WeekStartUtil.withWeekStart(weekStart, () -> {
            if(shape.isCustom()) {
               for(String[][] ranges : CUSTOM_PERIODS) {
                  customViolation(shape, ranges).ifPresent(v -> found.add(shape + " " + weekStart + ": " + v));
               }
            }
            else {
               for(Variant v : variants(shape)) {
                  for(String violation : violations(shape, v)) {
                     found.add(shape + " " + weekStart + " " + v + ": " + violation);
                  }
               }
            }
         });
      }

      return found;
   }

   private static List<String> violations(DcShape shape, Variant v) {
      List<LocalDate> days;

      try {
         days = DcDays.kept(info(shape, v));
      }
      catch(RuntimeException ex) {
         return List.of("ERROR " + ex);
      }

      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = aligned(shape);
      Map<Long, List<LocalDate>> byPeriod = new TreeMap<>();

      for(LocalDate day : days) {
         byPeriod.computeIfAbsent(periods.ordinal(aligned ? periods.weekStart(day) : day),
                                  k -> new ArrayList<>()).add(day);
      }

      List<String> violations = new ArrayList<>();
      LocalDate end = LocalDate.parse(v.endDay());
      long current = periods.ordinal(aligned ? periods.weekStart(end) : end);
      long first = current - v.preCount() - (v.option() == DateComparisonInfo.VALUE ? 0 : 1);
      long last = v.inclusive() ? current : current - 1;

      // I1: nothing outside the requested periods (no extra period, no spill into the next one).
      // With toDate on, the Nth week of a shorter period can run into the next one: that
      // calendar-variation bucket (#76391's "12-2") is by design, but never a full week.
      for(Map.Entry<Long, List<LocalDate>> e : byPeriod.entrySet()) {
         long ordinal = e.getKey();
         boolean spill = v.toDate() && aligned && ordinal == last + 1 && e.getValue().size() < 7;

         if((ordinal < first || ordinal > last) && !spill) {
            violations.add("I1 outside " + periods.label(ordinal));
         }
      }

      // I2: Compare All has days in every requested period. Other intervals can legitimately
      // have none (the Nth week of a month that has no Nth week), or may be Bug #79061.
      for(long ordinal = first; ordinal <= last && shape.intervalLevel() == DateComparisonInfo.ALL;
          ordinal++)
      {
         if(!byPeriod.containsKey(ordinal)) {
            violations.add("I2 empty " + periods.label(ordinal));
         }
      }

      // I3: All and To Date intervals never reach past the End Date
      if(!shape.isSame() && days.stream().anyMatch(day -> day.isAfter(end))) {
         violations.add("I3 after end");
      }

      return violations;
   }

   // -- Custom Periods --------------------------------------------------------------------

   private static Optional<String> customViolation(DcShape shape, String[][] ranges) {
      Set<LocalDate> expected = expectedCustomDays(ranges);
      Set<LocalDate> days;

      try {
         days = new TreeSet<>(customDays(shape.granularity(), ranges));
      }
      catch(RuntimeException ex) {
         return Optional.of("ERROR " + ex);
      }

      if(days.equals(expected)) {
         return Optional.empty();
      }

      Set<LocalDate> missing = new TreeSet<>(expected);
      missing.removeAll(days);
      Set<LocalDate> extra = new TreeSet<>(days);
      extra.removeAll(expected);

      return Optional.of("C1 " + Arrays.deepToString(ranges) + " missing " + missing.size() +
                         ", extra " + extra.size());
   }

   /** Custom periods select exactly the days from each start to its end, both included. */
   private static Set<LocalDate> expectedCustomDays(String[][] ranges) {
      Set<LocalDate> expected = new TreeSet<>();

      for(String[] range : ranges) {
         for(LocalDate d = LocalDate.parse(range[0]); !d.isAfter(LocalDate.parse(range[1]));
             d = d.plusDays(1))
         {
            expected.add(d);
         }
      }

      return expected;
   }

   private static List<LocalDate> customDays(int granularity, String[][] ranges) {
      DateComparisonInfo dc = new DateComparisonInfo();
      List<DatePeriod> list = new ArrayList<>();

      for(String[] range : ranges) {
         list.add(new DatePeriod(DcPeriods.toDate(LocalDate.parse(range[0])),
                                 DcPeriods.toDate(LocalDate.parse(range[1]))));
      }

      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(list);
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(granularity);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      return DcDays.kept(dc, LocalDate.parse("2019-01-01"), LocalDate.parse("2021-12-31"));
   }

   /**
    * A one-day Custom Period (start = end) is accepted by {@link DateComparisonInfo#invalid()}
    * but skipped by the periods condition, and when it is the only period the comparison keeps
    * every day of the data.
    */
   @Test
   void customOneDayPeriodKeepsThatDay() {
      String[][] ranges = { { "2021-05-05", "2021-05-05" } };

      List<LocalDate> days = customDays(DateComparisonInfo.ALL, ranges);

      assertEquals("2021-05-05", days.size() == 1 ? days.get(0).toString() : days.size() + " days");
   }

   // -- the grouping shape ----------------------------------------------------------------

   /**
    * What the chart derives from the combination: week alignment, whether a period series is
    * created, the previous-period type of Change, and the date level / temp groups.
    */
   private static String shapeLine(DcShape shape, int option) {
      try {
         DateComparisonInfo dc = info(shape, variant(DATATEST_END, false, false, option, 2, true));
         Viewsheet vs = new Viewsheet();
         boolean series = dc.isDateSeries();
         StringBuilder sb = new StringBuilder();
         sb.append("alignWeek=").append(dc.alignWeek())
            .append(" dateSeries=").append(series)
            .append(" parent=").append(DcShape.levelName(dc.getGranularityParentLevel()));

         VSDimensionRef periodDim = dateDim();
         periodDim.setDateLevelValue(dc.getPeriodDateLevel() + "");
         Calculator calc = dc.createComparisonCalc(periodDim, series, List.of());
         sb.append(" from=").append(
            calc instanceof ValueOfCalc ? fromName(((ValueOfCalc) calc).getFrom()) : "-");

         VSDimensionRef updated = (VSDimensionRef) dc.updateDateDimensionLevel(
            dateDim(), "ds", vs, true);
         sb.append(" dateDim=").append(updated.getFullName());

         XDimensionRef[] temps = DateComparisonUtil.getAllTempDateGroupRef(
            dc, "ds", dateDim(), vs);
         sb.append(" temp=").append(Arrays.stream(temps).map(XDimensionRef::getFullName)
                                       .collect(Collectors.toList()));

         return sb.toString();
      }
      catch(RuntimeException ex) {
         return "ERROR " + ex;
      }
   }

   private static String fromName(int from) {
      switch(from) {
      case ValueOfCalc.PREVIOUS_YEAR:
         return "PREVIOUS_YEAR";
      case ValueOfCalc.PREVIOUS_QUARTER:
         return "PREVIOUS_QUARTER";
      case ValueOfCalc.PREVIOUS_MONTH:
         return "PREVIOUS_MONTH";
      case ValueOfCalc.PREVIOUS_WEEK:
         return "PREVIOUS_WEEK";
      case ValueOfCalc.PREVIOUS_RANGE:
         return "PREVIOUS_RANGE";
      case ValueOfCalc.PREVIOUS:
         return "PREVIOUS";
      default:
         return "from" + from;
      }
   }

   private static VSDimensionRef dateDim() {
      AttributeRef attr = new AttributeRef("date");
      attr.setDataType(XSchema.DATE);

      return new VSDimensionRef(attr);
   }
}
