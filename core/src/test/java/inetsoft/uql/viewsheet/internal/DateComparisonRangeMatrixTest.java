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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * surviving days are bucketed by period. Two checks run on every combination:</p>
 *
 * <ul>
 *   <li><b>Invariants</b> that do not depend on a snapshot: no day outside the requested
 *   periods (an extra year, a spill into the next year), no requested period without days, no
 *   day past the End Date for All / To Date intervals. A violation is written into the
 *   snapshot as a {@code !} line and fails {@link #noInvariantViolations}.</li>
 *   <li>A <b>snapshot</b> of each period's first and last day, weeks, days and gaps, plus the
 *   derived grouping shape (week alignment, series, previous-period type, temp groups). It is
 *   reviewed once and afterwards only its diff is reviewed.</li>
 * </ul>
 *
 * <p>Update the snapshots with {@code -Ddc.snapshot.update=true}.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonRangeMatrixTest {
   private static final String SNAPSHOT_DIR = "inetsoft/uql/viewsheet/internal/dc-matrix/range/";
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

   static List<DcShape> shapes() {
      return DcShape.all();
   }

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

                     if(knownGap(shape, weekStart, end, inclusive, change)) {
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

                        Set<LocalDate> missing = new TreeSet<>(model);
                        missing.removeAll(kept);
                        Set<LocalDate> extra = new TreeSet<>(kept);
                        extra.removeAll(model);

                        return shape + " " + weekStart + " end=" + end + (inclusive ? " incl" : "") +
                           (change ? " change" : "") + ": missing " + missing.size() +
                           (missing.isEmpty() ? "" : " from " + ((TreeSet<LocalDate>) missing).first()) +
                           ", extra " + extra.size() +
                           (extra.isEmpty() ? "" : " from " + ((TreeSet<LocalDate>) extra).first());
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
    * Combinations where the product differs from the wording of the dialog and the difference is
    * a finding of Bug #79061, so the model test leaves them out until it is decided:
    * <ul>
    *   <li>finding 2: Same Day by Week, End Date Saturday 2021-01-02, current period included;</li>
    *   <li>finding 7: a week that starts in a period and ends in the next one loses its days
    *   there. Same Day by Week of a Year, Quarter or Month period drops the weekday when it
    *   falls in the next period; Week to Date / Same Week of a quarter drops a whole week whose
    *   to-date part crosses the quarter end (Change at 2021-01-02, Monday weeks).</li>
    * </ul>
    */
   private static boolean knownGap(DcShape shape, String weekStart, String end, boolean inclusive,
                                   boolean change)
   {
      boolean sameDayByWeek = shape.intervalUnit() == XConstants.DAY_DATE_GROUP &&
         shape.contextLevel() == XConstants.WEEK_DATE_GROUP;

      return sameDayByWeek && "2021-01-02".equals(end) && inclusive ||
         sameDayByWeek && shape.periodLevel() != XConstants.WEEK_DATE_GROUP ||
         shape.periodLevel() == XConstants.YEAR_DATE_GROUP &&
            shape.intervalUnit() == XConstants.WEEK_DATE_GROUP &&
            shape.contextLevel() == XConstants.QUARTER_DATE_GROUP &&
            "monday".equals(weekStart) && "2021-01-02".equals(end) && change;
   }

   /**
    * The weekday of a week that starts in the period is kept even when it falls in the next
    * period: the week Sunday 2019-12-29 to 2020-01-04 is a 2019 week, so Wednesday 2020-01-01 is
    * the Wednesday of its last week, as Compare All by Week takes the whole week.
    */
   @Test
   @Disabled("Bug #79061 (finding 7): a week that starts in a period loses its days in the next period")
   void sameWeekdayOfAWeekThatStartsInThePeriodIsKept() {
      DcShape shape = DcShape.byId("Y-sameD-gD-cW");
      List<LocalDate> kept = WeekStartUtil.withWeekStart("sunday", () -> DcDays.kept(
         shape.toInfo(DATATEST_END, false, false, DateComparisonInfo.VALUE, 2, true)));

      assertTrue(kept.contains(LocalDate.of(2020, 1, 1)), "kept days " + kept);
   }

   /** A snapshot of a shape that no longer exists is removed, not left to rot. */
   @Test
   void noSnapshotWithoutAShape() throws Exception {
      Set<String> files = new TreeSet<>();
      Path dir = Path.of(System.getProperty("basedir", "."), "src", "test", "resources",
                         SNAPSHOT_DIR);

      try(Stream<Path> list = Files.list(dir)) {
         list.forEach(file -> files.add(file.getFileName().toString()));
      }

      Set<String> expected = new TreeSet<>();
      DcShape.all().forEach(shape -> expected.add(shape.id() + ".txt"));

      assertEquals(expected, files);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("shapes")
   void rangeSnapshot(DcShape shape) {
      DcSnapshot.verify(SNAPSHOT_DIR + shape.id() + ".txt", snapshotLines(shape));
   }

   /** The invariants hold for every combination. */
   @Test
   void noInvariantViolations() {
      List<String> violations = new ArrayList<>();

      for(DcShape shape : DcShape.all()) {
         for(String line : snapshotLines(shape)) {
            if(line.contains(" !") || line.startsWith("!") || line.contains("ERROR")) {
               violations.add(shape + ": " + line);
            }
         }
      }

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
    * By full weeks of the year that is week 45, so 2019-11-10..13 and 2020-11-08..11; the
    * product matches the same week of the month instead (the 'wy' encoding): 2019-11-03..06 and
    * 2020-11-01..04. Which one is intended?
    */
   @Test
   @Disabled("Bug #79061 (finding 3): Year / Week to Date / Week matches week of month, not week of year - dev to decide")
   void yearWeekToDateMatchesTheSameWeekOfYear() {
      DcShape shape = DcShape.byId("Y-wtd-gW-cY");

      assertEquals("2019=2019-11-10..2019-11-13/4d, 2020=2020-11-08..2020-11-11/4d",
                   WeekStartUtil.withWeekStart("sunday", () -> anchorPeriods(
                      shape, variant("2021-11-10", false, false))));
   }

   /**
    * Same Day by Week with the End Date on the last day of its week (Saturday 2021-01-02) and
    * the current period included: the current week's own Saturday is the End Date itself, but
    * the product returns the two previous weeks only.
    */
   @Test
   @Disabled("Bug #79061 (finding 2): Same Day / Week misses the End Date's own week when it is a Saturday")
   void sameDayKeepsTheEndDatesOwnWeek() {
      DcShape shape = DcShape.byId("W-sameD-gD-cW");

      assertEquals("wk2020-12-13=2020-12-19..2020-12-19/1d, wk2020-12-20=2020-12-26..2020-12-26/1d, " +
                   "wk2020-12-27=2021-01-02..2021-01-02/1d",
                   WeekStartUtil.withWeekStart("sunday", () -> anchorPeriods(
                      shape, variant("2021-01-02", false, true))));
   }

   // -- snapshot ---------------------------------------------------------------------------

   private static final Map<String, List<String>> LINES = new java.util.concurrent.ConcurrentHashMap<>();

   /** The snapshot of a shape, computed once for the snapshot and the invariant tests. */
   private static List<String> snapshotLines(DcShape shape) {
      return LINES.computeIfAbsent(shape.id(), id -> computeSnapshotLines(shape));
   }

   private static List<String> computeSnapshotLines(DcShape shape) {
      List<String> lines = new ArrayList<>();
      lines.add("# " + shape.id());

      for(String weekStart : WEEK_STARTS) {
         lines.add("[ws=" + weekStart + "]");
         WeekStartUtil.withWeekStart(weekStart, () -> {
            if(shape.isCustom()) {
               addCustomLines(shape, lines);
            }
            else {
               addStandardLines(shape, lines, weekStart);
            }
         });
      }

      return lines;
   }

   private static void addStandardLines(DcShape shape, List<String> lines, String weekStart) {
      lines.add("shape value:  " + shapeLine(shape, DateComparisonInfo.VALUE));
      lines.add("shape change: " + shapeLine(shape, DateComparisonInfo.CHANGE));

      // toDate and inclusive both off / both on at every End Date, the mixed pairs at two
      for(String end : END_DAYS) {
         for(boolean both : new boolean[] { false, true }) {
            addPair(shape, lines, end, both, both, 2, true);
         }
      }

      for(String end : new String[] { "2021-01-02", DATATEST_END }) {
         addPair(shape, lines, end, false, true, 2, true);
         addPair(shape, lines, end, true, false, 2, true);
      }

      // the number of periods back
      for(int preCount : new int[] { 1, 3 }) {
         addPair(shape, lines, DATATEST_END, false, false, preCount, true);
      }

      // an interval whose own End Date is not included
      if(shape.isToDate()) {
         for(String end : new String[] { "2021-01-02", DATATEST_END }) {
            addPair(shape, lines, end, false, false, 2, false);
         }
      }
   }

   /**
    * The Value line and the Change line of one setting. Change fetches one more earlier period,
    * so when its periods are the Value periods plus earlier ones only the extra ones are
    * written, after a {@code +}.
    */
   private static void addPair(DcShape shape, List<String> lines, String end, boolean toDate,
                               boolean inclusive, int preCount, boolean intervalInclusive)
   {
      Line value = variantParts(shape, variant(end, toDate, inclusive, DateComparisonInfo.VALUE,
                                               preCount, intervalInclusive));
      Line change = variantParts(shape, variant(end, toDate, inclusive, DateComparisonInfo.CHANGE,
                                                preCount, intervalInclusive));
      lines.add(value.render(null));
      lines.add(change.render(value.periods()));
   }

   private static void addCustomLines(DcShape shape, List<String> lines) {
      for(String[][] periods : CUSTOM_PERIODS) {
         lines.add(customLine(shape, periods));
      }
   }

   private static final String[][][] CUSTOM_PERIODS = {
      { { "2019-01-01", "2019-03-31" }, { "2020-01-01", "2020-03-31" } },
      { { "2020-12-28", "2021-01-10" }, { "2021-02-01", "2021-02-28" } },
      { { "2020-02-27", "2020-03-02" }, { "2021-02-27", "2021-03-02" } } };

   // -- one variant -----------------------------------------------------------------------

   private record Variant(String endDay, boolean toDate, boolean inclusive, int option,
                          int preCount, boolean intervalInclusive)
   {
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

   /** The parts of a variant's line. */
   private record Line(String head, List<String> periods, String tail) {
      /** With {@code valuePeriods}, a period list that only adds earlier periods is shortened. */
      String render(List<String> valuePeriods) {
         List<String> shown = periods;
         String mark = "";

         if(valuePeriods != null && !valuePeriods.isEmpty() && periods.size() > valuePeriods.size() &&
            periods.subList(periods.size() - valuePeriods.size(), periods.size())
               .equals(valuePeriods))
         {
            shown = periods.subList(0, periods.size() - valuePeriods.size());
            mark = " +";
         }

         StringBuilder line = new StringBuilder(head).append(mark);

         for(String period : shown) {
            line.append(" | ").append(period);
         }

         return line.append(tail).toString();
      }
   }

   /** The periods the variant selects, then any violated invariant. */
   private static Line variantParts(DcShape shape, Variant v) {
      StringBuilder head = new StringBuilder();
      head.append("end=").append(v.endDay())
         .append(" td=").append(v.toDate() ? "on" : "off")
         .append(" inc=").append(v.inclusive() ? "on" : "off")
         .append(" opt=").append(v.option() == DateComparisonInfo.VALUE ? "val" : "chg");

      if(v.preCount() != 2) {
         head.append(" pre=").append(v.preCount());
      }

      if(!v.intervalInclusive()) {
         head.append(" intInc=off");
      }

      DateComparisonInfo dc = info(shape, v);
      head.append(" start=").append(DcPeriods.toLocal(dc.getStartDate()));

      List<LocalDate> days;

      try {
         days = DcDays.kept(dc);
      }
      catch(RuntimeException ex) {
         return new Line(head.toString(), List.of(),
                         " ERROR " + ex);
      }

      DcPeriods periods = new DcPeriods(shape.periodLevel());
      boolean aligned = aligned(shape);
      TreeMap<Long, List<LocalDate>> byPeriod = new TreeMap<>();

      for(LocalDate day : days) {
         byPeriod.computeIfAbsent(periods.ordinal(aligned ? periods.weekStart(day) : day),
                                  k -> new ArrayList<>()).add(day);
      }

      List<String> texts = new ArrayList<>();

      for(Map.Entry<Long, List<LocalDate>> e : byPeriod.entrySet()) {
         texts.add(periods.label(e.getKey()) + " " + summary(e.getValue(), periods));
      }

      StringBuilder tail = new StringBuilder();
      invariants(shape, v, aligned, periods, byPeriod, days).forEach(
         violation -> tail.append(violation.startsWith("?") ? " " : " !").append(violation));

      return new Line(head.toString(), texts, tail.toString());
   }

   /** first..last (the year of the last day only when it differs), weeks, days, gaps if any. */
   private static String summary(List<LocalDate> days, DcPeriods periods) {
      LocalDate first = days.get(0);
      LocalDate last = days.get(days.size() - 1);
      long weeks = days.stream().map(periods::weekStart).distinct().count();
      long gaps = last.toEpochDay() - first.toEpochDay() + 1 - days.size();

      String range = first.getYear() == last.getYear() ?
         first + ".." + last.toString().substring(5) : first + ".." + last;

      return range + " " + weeks + "w" + days.size() + "d" + (gaps > 0 ? " g" + gaps : "");
   }

   /**
    * Hard violations start with {@code !} and fail {@link #noInvariantViolations}; a {@code ?}
    * marks a requested period without days under an interval where that can be legitimate
    * (the Nth week of a month that has no Nth week) and is left for review in the snapshot.
    */
   private static List<String> invariants(DcShape shape, Variant v, boolean aligned,
                                          DcPeriods periods, Map<Long, List<LocalDate>> byPeriod,
                                          List<LocalDate> days)
   {
      List<String> violations = new ArrayList<>();
      LocalDate end = LocalDate.parse(v.endDay());
      LocalDate anchor = aligned ? periods.weekStart(end) : end;
      long current = periods.ordinal(anchor);
      int back = v.preCount() + (v.option() == DateComparisonInfo.VALUE ? 0 : 1);
      long first = current - back;
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

      // I2: every requested period has days
      for(long ordinal = first; ordinal <= last; ordinal++) {
         if(!byPeriod.containsKey(ordinal)) {
            violations.add((shape.intervalLevel() == DateComparisonInfo.ALL ? "I2" : "?I2") +
                           " empty " + periods.label(ordinal));
         }
      }

      // I3: All and To Date intervals never reach past the End Date
      if(!shape.isSame() && days.stream().anyMatch(day -> day.isAfter(end))) {
         violations.add("I3 after end");
      }

      return violations;
   }

   // -- Custom Periods --------------------------------------------------------------------

   private static String customLine(DcShape shape, String[][] ranges) {
      StringBuilder line = new StringBuilder("custom");

      for(String[] range : ranges) {
         line.append(' ').append(range[0]).append("..").append(range[1]);
      }

      Set<LocalDate> expected = expectedCustomDays(ranges);
      List<LocalDate> days;

      try {
         days = customDays(shape.granularity(), ranges);
      }
      catch(RuntimeException ex) {
         return line + " ERROR " + ex;
      }

      line.append(" -> ").append(days.size()).append("d");

      if(!new TreeSet<>(days).equals(expected)) {
         Set<LocalDate> missing = new TreeSet<>(expected);
         missing.removeAll(days);
         Set<LocalDate> extra = new TreeSet<>(days);
         extra.removeAll(expected);
         line.append(" !C1 missing=").append(missing.size()).append(" extra=").append(extra.size());
      }

      return line.toString();
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
   @Disabled("Bug #79061 (finding 1): a one-day Custom Period is dropped")
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
