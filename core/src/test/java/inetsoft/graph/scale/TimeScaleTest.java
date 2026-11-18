/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.scale;

import inetsoft.test.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import inetsoft.graph.data.DefaultDataSet;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeScaleTest {

   // Jan 1 – Mar 31, 2024 (91 calendar days, range triggers maxTicks guard for DAY type)
   private static final long JAN_1_2024 =
      new GregorianCalendar(2024, Calendar.JANUARY, 1).getTimeInMillis();
   private static final long MAR_31_2024 =
      new GregorianCalendar(2024, Calendar.MARCH, 31).getTimeInMillis();

   @Test
   void allTicksTruePreservesDailyTickDensityForLargeRange() {
      // With allTicks=false the maxTicks guard increments n for a 90+ day range
      // (maxTicks=50 for DAY), yielding ~45 ticks. With allTicks=true the guard
      // must be skipped so every day gets a tick.
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().setAllTicks(true);
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length >= 89,
         "allTicks=true should produce one tick per day for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksFalseAppliesMaxTicksGuardForLargeRange() {
      // The default (allTicks=false) maxTicks guard should still cap daily ticks for
      // large ranges to prevent overly dense axes.
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length <= 55,
         "allTicks=false should apply maxTicks guard (~50) for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksTrueBypassesLabelDeduplicationWhenFormatCollapsesToMonth() {
      // Verifies the line 521 fix: allTicks=true must force hasDup=false even when
      // isFormattedToHigherInterval() returns true. Without the fix, only ~3 month-
      // boundary ticks would be produced instead of one per day.
      // isFormattedToHigherInterval() compares today vs. today+1 using the format;
      // skip on the last day of a month where they cross a month boundary.
      Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() < LocalDate.now().lengthOfMonth(),
         "Skipped: month boundary would make isFormattedToHigherInterval return false");
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().setAllTicks(true);
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("MMM"));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length >= 89 && ticks.length <= 93,
         "allTicks=true with MMM format should produce daily ticks for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksFalseWithFormatAppliesLabelDeduplication() {
      // Companion: without allTicks=true the MMM format triggers hasDup=true and
      // deduplication keeps only the first tick per distinct month label (~3 ticks).
      Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() < LocalDate.now().lengthOfMonth(),
         "Skipped: month boundary would make isFormattedToHigherInterval return false");
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("MMM"));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length <= 3,
         "allTicks=false with MMM format should deduplicate to 3 month ticks, got: " + ticks.length);
   }

   /**
    * A day-level label format over data whose smallest gap is a second (e.g. two gantt tasks
    * starting a second apart) must not visit every second of the range to find the ticks.
    */
   @Test
   void dayFormatOnSecondIntervalDataIsBounded() {
      Assumptions.assumeTrue(LocalTime.now().isBefore(LocalTime.of(23, 59, 58)),
         "Skipped: midnight boundary would make isFormattedToHigherInterval return false");
      long start = date(2016, Calendar.JANUARY, 6, 8, 0, 0);
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "start" },
         { new Date(start) },
         { new Date(start + 1000) },
         { new Date(date(2026, Calendar.JANUARY, 6, 8, 0, 0)) }
      });
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      TimeScale scale = new TimeScale("start");
      scale.getAxisSpec().getTextSpec().setFormat(fmt);

      double[] ticks = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         scale.init(data);
         return scale.getTicks();
      });

      assertEquals(TimeScale.SECOND, scale.getType());
      assertTrue(ticks.length > 1 && ticks.length <= 52, "tick count: " + ticks.length);
      Set<String> labels = new HashSet<>();

      for(double tick : ticks) {
         assertTrue(labels.add(fmt.format(new Date((long) tick))), "duplicate label at " + tick);
      }
   }

   /**
    * Daily data formatted by month places a tick at the first value and then at the start
    * of each following month (Bug #63834).
    */
   @Test
   void monthFormatOnDayIntervalDataTicksAtMonthStarts() {
      Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() < LocalDate.now().lengthOfMonth(),
         "Skipped: month boundary would make isFormattedToHigherInterval return false");
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "start" });

      for(int day = 15; day <= 31 + 28 + 31 + 10; day++) {
         rows.add(new Object[] { new Date(date(2025, Calendar.JANUARY, day, 0, 0, 0)) });
      }

      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM");
      TimeScale scale = new TimeScale("start");
      scale.getAxisSpec().getTextSpec().setFormat(fmt);
      scale.init(new DefaultDataSet(rows.toArray(new Object[0][])));

      assertEquals(TimeScale.DAY, scale.getType());
      assertArrayEquals(new double[] {
         date(2025, Calendar.JANUARY, 15, 0, 0, 0),
         date(2025, Calendar.FEBRUARY, 1, 0, 0, 0),
         date(2025, Calendar.MARCH, 1, 0, 0, 0),
         date(2025, Calendar.APRIL, 1, 0, 0, 0),
      }, scale.getTicks());
   }

   /**
    * SimpleDateFormat is not thread-safe, so scales rendered by different threads must not
    * share a default label format.
    */
   @Test
   void defaultLabelFormatIsNotSharedBetweenScales() {
      Format fmt1 = getDefaultLabelFormat();
      Format fmt2 = getDefaultLabelFormat();

      assertNotNull(fmt1);
      assertNotSame(fmt1, fmt2);
      assertEquals("2025-01-06", fmt1.format(new Date(date(2025, Calendar.JANUARY, 6, 0, 0, 0))));
   }

   private static Format getDefaultLabelFormat() {
      TimeScale scale = new TimeScale("start");
      scale.init(new DefaultDataSet(new Object[][] {
         { "start" },
         { new Date(date(2025, Calendar.JANUARY, 6, 0, 0, 0)) },
         { new Date(date(2025, Calendar.JANUARY, 7, 0, 0, 0)) }
      }));
      // the default format is only assigned once graph generation has started
      scale.getUnitCount();
      return scale.getAxisSpec().getTextSpec().getFormat();
   }

   private static long date(int year, int month, int day, int hour, int minute, int second) {
      return new GregorianCalendar(year, month, day, hour, minute, second).getTimeInMillis();
   }

   private static final long JAN_31_2024 =
      new GregorianCalendar(2024, Calendar.JANUARY, 31).getTimeInMillis();

   private static TimeScale filledScale() {
      TimeScale scale = new TimeScale("Date");
      scale.setFill(true);
      scale.init(new inetsoft.graph.data.DefaultDataSet(new Object[][] {
         { "Date" },
         { new java.util.Date(JAN_1_2024) },
         { new java.util.Date(JAN_31_2024) } }));
      return scale;
   }

   @Test
   void anEndPadExtendsOnlyTheMax() {
      TimeScale plain = filledScale();
      TimeScale padded = filledScale();
      padded.setEndPadRatio(0.1);

      assertEquals(JAN_31_2024 + 3L * 86400000L, padded.getMax(), 3600000);
      assertEquals(plain.getMin(), padded.getMin(), 0);
      assertArrayEquals(plain.getTicks(), padded.getTicks(), 0);
   }

   @Test
   void aMinPastTheDataGetsNoNegativePad() {
      TimeScale plain = filledScale();
      TimeScale padded = filledScale();
      java.util.Date min = new java.util.Date(JAN_31_2024 + 10L * 86400000L);
      plain.setMin(min);
      padded.setMin(min);
      padded.setEndPadRatio(0.1);

      assertEquals(plain.getMax(), padded.getMax(), 0);
   }

   @Test
   void anAuthorMaxTurnsThePadOff() {
      TimeScale scale = filledScale();
      scale.setEndPadRatio(0.1);
      java.util.Date max = new java.util.Date(JAN_31_2024 + 10L * 86400000L);
      scale.setMax(max);

      assertEquals(max.getTime(), scale.getMax(), 0);
   }

   @Test
   void aZeroRangeOrNarrowPlotGetsNoPad() {
      TimeScale plain = new TimeScale("Date");
      plain.setFill(true);
      TimeScale padded = new TimeScale("Date");
      padded.setFill(true);
      padded.setEndPadRatio(0.1);
      inetsoft.graph.data.DefaultDataSet data = new inetsoft.graph.data.DefaultDataSet(
         new Object[][] { { "Date" }, { new java.util.Date(JAN_1_2024) } });
      plain.init(data);
      padded.init(data);

      assertTrue(Double.isFinite(padded.getMax()));
      assertEquals(plain.getMax(), padded.getMax(), 0);
   }

   private static final long DAY_MS = 86400000L;
   private static final long HOUR_MS = 3600000L;

   @Test
   void aDailySpanOfFourMonthsGetsMonths() {
      assertArrayEquals(new int[] { TimeScale.MONTH, 1 },
                        TimeScale.chooseFitUnit(120 * DAY_MS, DAY_MS, 7));
   }

   @Test
   void aDailySpanOfThreeWeeksGetsDays() {
      assertArrayEquals(new int[] { TimeScale.DAY, 1 },
                        TimeScale.chooseFitUnit(21 * DAY_MS, DAY_MS, 30));
      assertArrayEquals(new int[] { TimeScale.WEEK, 1 },
                        TimeScale.chooseFitUnit(21 * DAY_MS, DAY_MS, 10));
   }

   @Test
   void anIntradaySpanKeepsHours() {
      assertArrayEquals(new int[] { TimeScale.HOUR, 3 },
                        TimeScale.chooseFitUnit(10 * HOUR_MS, HOUR_MS, 7));
   }

   @Test
   void neverFinerThanTheData() {
      assertArrayEquals(new int[] { TimeScale.WEEK, 1 },
                        TimeScale.chooseFitUnit(3 * DAY_MS, 7 * DAY_MS, 50));
   }

   @Test
   void aZeroRangeOrTinyBudgetStillPicksAUnit() {
      assertArrayEquals(new int[] { TimeScale.DAY, 1 },
                        TimeScale.chooseFitUnit(0, DAY_MS, 7));
      assertArrayEquals(new int[] { TimeScale.YEAR, 100 },
                        TimeScale.chooseFitUnit(500 * 365 * DAY_MS, DAY_MS, 2));
   }

   private static TimeScale dailyScale(boolean fit) {
      TimeScale scale = new TimeScale("Date");

      if(fit) {
         scale.setLabelFit(300, 45);
      }

      scale.init(dailyData());
      return scale;
   }

   private static DefaultDataSet dailyData() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Date" });
      long start = date(2026, Calendar.JANUARY, 1, 0, 0, 0);
      long end = date(2026, Calendar.APRIL, 30, 0, 0, 0);

      for(long t = start; t <= end; t += DAY_MS) {
         rows.add(new Object[] { new Date(t) });
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   @Test
   void theFitSetsTheTypeAndStep() {
      TimeScale scale = dailyScale(true);

      assertEquals(TimeScale.MONTH, scale.getType());
      assertEquals(1, scale.getFitStep());
      assertArrayEquals(new double[] {
         date(2026, Calendar.JANUARY, 1, 0, 0, 0),
         date(2026, Calendar.FEBRUARY, 1, 0, 0, 0),
         date(2026, Calendar.MARCH, 1, 0, 0, 0),
         date(2026, Calendar.APRIL, 1, 0, 0, 0),
      }, scale.getTicks());
   }

   @Test
   void anAuthorIncrementTurnsTheFitOff() {
      TimeScale scale = new TimeScale("Date");
      scale.setLabelFit(300, 45);
      scale.setIncrement(7);
      scale.init(dailyData());

      assertEquals(TimeScale.DAY, scale.getType());
      assertEquals(0, scale.getFitStep());
   }

   @Test
   void anExplicitTypeTurnsTheFitOff() {
      TimeScale scale = new TimeScale("Date");
      scale.setLabelFit(300, 45);
      scale.setType(TimeScale.WEEK);
      scale.init(dailyData());

      assertEquals(TimeScale.WEEK, scale.getType());
      assertEquals(0, scale.getFitStep());
   }

   @Test
   void noFitIsTodaysTicks() {
      TimeScale plain = dailyScale(false);
      TimeScale off = new TimeScale("Date");
      off.setLabelFit(0, 0);
      off.init(dailyData());

      assertEquals(0, plain.getFitStep());
      assertEquals(TimeScale.DAY, plain.getType());
      assertArrayEquals(plain.getTicks(), off.getTicks(), 0);
      assertTrue(plain.getTicks().length > 4);
   }

   private static DefaultDataSet datesFrom(long start, long step, int count) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Date" });

      for(int i = 0; i < count; i++) {
         rows.add(new Object[] { new Date(start + i * step) });
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   private static TimeScale fitted(DefaultDataSet data, double plot, double label) {
      TimeScale scale = new TimeScale("Date");
      scale.setLabelFit(plot, label);
      scale.init(data);
      return scale;
   }

   @Test
   void quarterTicksSitOnQuarterStarts() {
      long start = date(2026, Calendar.FEBRUARY, 16, 0, 0, 0);
      TimeScale scale = fitted(datesFrom(start, DAY_MS, 400), 302, 49);

      assertEquals(TimeScale.QUARTER, scale.getType(), "premise");
      Calendar cal = Calendar.getInstance();

      for(double tick : scale.getTicks()) {
         cal.setTimeInMillis((long) tick);
         assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));
         assertEquals(0, cal.get(Calendar.MONTH) % 3, new Date((long) tick).toString());
      }

      // the data's own start plus the half-quarter gap, not widened to the aligned tick
      assertTrue(scale.getMin() > date(2026, Calendar.JANUARY, 1, 0, 0, 0));
      assertEquals(date(2026, Calendar.APRIL, 1, 0, 0, 0), scale.getTicks()[0], 0);
   }

   @Test
   void hourSixTicksSitOnSixHourMarks() {
      long start = date(2026, Calendar.JANUARY, 5, 5, 0, 0);
      TimeScale scale = fitted(datesFrom(start, HOUR_MS, 25), 300, 50);

      assertEquals(TimeScale.HOUR, scale.getType());
      assertEquals(6, scale.getFitStep());
      Calendar cal = Calendar.getInstance();

      for(double tick : scale.getTicks()) {
         cal.setTimeInMillis((long) tick);
         assertEquals(0, cal.get(Calendar.HOUR_OF_DAY) % 6);
      }

      assertEquals(start, scale.getMin(), HOUR_MS);
   }

   @Test
   void yearFiveTicksSitOnMultiplesOfFive() {
      long start = date(2013, Calendar.MARCH, 3, 0, 0, 0);
      TimeScale scale = fitted(datesFrom(start, 30 * DAY_MS, 12 * 30), 300, 50);

      assertEquals(TimeScale.YEAR, scale.getType());
      assertTrue(scale.getFitStep() > 1, "premise: " + scale.getFitStep());
      Calendar cal = Calendar.getInstance();

      for(double tick : scale.getTicks()) {
         cal.setTimeInMillis((long) tick);
         assertEquals(0, cal.get(Calendar.YEAR) % scale.getFitStep());
      }
   }

   @Test
   void theFitUsesTheAuthorRange() {
      long start = date(2024, Calendar.JANUARY, 1, 0, 0, 0);
      TimeScale scale = new TimeScale("Date");
      scale.setLabelFit(300, 50);
      scale.setMin(new Date(date(2026, Calendar.MARCH, 1, 0, 0, 0)));
      scale.setMax(new Date(date(2026, Calendar.MARCH, 31, 0, 0, 0)));
      scale.init(datesFrom(start, DAY_MS, 730));

      int budget = 6;
      assertTrue(scale.getTicks().length >= 2, scale.getTicks().length + " ticks");
      assertTrue(scale.getTicks().length <= budget, scale.getTicks().length + " ticks");
   }

   @Test
   void theFitUsesTheAuthorMaximum() {
      long start = date(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
      TimeScale scale = new TimeScale("Date");
      scale.setLabelFit(300, 50);
      scale.setMax(new Date(date(2026, Calendar.DECEMBER, 31, 0, 0, 0)));
      scale.init(datesFrom(start, DAY_MS, 20));

      assertTrue(scale.getTicks().length >= 2, scale.getTicks().length + " ticks");
      assertTrue(scale.getTicks().length <= 6, scale.getTicks().length + " ticks "
                 + scale.getType() + "x" + scale.getFitStep());
   }
}
