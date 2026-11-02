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

import inetsoft.report.filter.ConditionGroup;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Range arithmetic of {@link DateComparisonInfo} (Bug #79061): a one-day Custom Period, the
 * weekday of a week that straddles New Year in Same Day by Week, and the 13th week of a quarter
 * under a Monday week start. Each test evaluates the real condition list on every day of the
 * data and checks which days the comparison keeps.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInfoRangeFixTest {
   // -- finding 1: one-day Custom Period -----------------------------------------------------

   @Test
   void oneDayCustomPeriodKeepsThatDay() {
      List<LocalDate> kept = keptDays(custom(new String[][] { { "2021-05-05", "2021-05-05" } }));

      assertEquals(List.of(LocalDate.of(2021, 5, 5)), kept);
   }

   @Test
   void oneDayCustomPeriodBesideOtherPeriodsIsKept() {
      List<LocalDate> kept = keptDays(custom(new String[][] {
         { "2021-03-01", "2021-03-03" }, { "2021-05-05", "2021-05-05" },
         { "2021-07-10", "2021-07-11" } }));

      assertEquals(List.of(LocalDate.of(2021, 3, 1), LocalDate.of(2021, 3, 2),
                           LocalDate.of(2021, 3, 3), LocalDate.of(2021, 5, 5),
                           LocalDate.of(2021, 7, 10), LocalDate.of(2021, 7, 11)), kept);
   }

   // -- findings 2 and 7a: Same Day by Week across New Year ----------------------------------

   /**
    * The End Date's own weekday is kept when its week started in the previous year
    * (Saturday 2021-01-02 is in the week of Sunday 2020-12-27).
    */
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday" })
   void sameDayByWeekKeepsTheEndDateInTheYearBoundaryWeek(String weekStart) {
      for(String end : new String[] { "2021-01-01", "2021-01-02" }) {
         List<LocalDate> kept = WeekStartUtil.withWeekStart(weekStart, () -> keptDays(
            standard(XConstants.WEEK_DATE_GROUP, DateComparisonInfo.SAME_DAY,
                     DateComparisonInfo.DAY, XConstants.WEEK_DATE_GROUP, end, true, 2)));
         String msg = weekStart + " end=" + end + " kept " + kept;

         // the End Date itself plus the same weekday of the two previous weeks
         assertEquals(3, kept.size(), msg);
         assertEquals(LocalDate.parse(end), kept.get(2), msg);
         assertEquals(LocalDate.parse(end).minusDays(7), kept.get(1), msg);
         assertEquals(LocalDate.parse(end).minusDays(14), kept.get(0), msg);
      }
   }

   /**
    * Period Year, Same Day by Week: Wednesday 2020-01-01 is in the week that starts in 2019
    * (Sunday 2019-12-29, or Monday 2019-12-30) and so belongs to the 2019 period.
    */
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday" })
   void sameDayByWeekKeepsTheWeekdayOfAWeekThatStartsInThePreviousYear(String weekStart) {
      List<LocalDate> kept = WeekStartUtil.withWeekStart(weekStart, () -> keptDays(
         standard(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.SAME_DAY,
                  DateComparisonInfo.DAY, XConstants.WEEK_DATE_GROUP, "2021-11-10", false, 2)));

      assertTrue(kept.contains(LocalDate.of(2020, 1, 1)), weekStart + " kept " + kept);
      // End Date 2021-11-10 is a Wednesday; every kept day is one.
      assertTrue(kept.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.WEDNESDAY),
                 weekStart + " kept " + kept);
   }

   /** Month context is unchanged: the #75152 overflow skip still applies to it. */
   @Test
   void sameDayByMonthStillKeepsTheEndDateAndNothingAfterIt() {
      List<LocalDate> kept = WeekStartUtil.withWeekStart("sunday", () -> keptDays(
         standard(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.SAME_DAY,
                  DateComparisonInfo.DAY, XConstants.MONTH_DATE_GROUP, "2021-01-02", true, 2)));

      assertTrue(kept.contains(LocalDate.of(2021, 1, 2)), "kept " + kept);
      assertTrue(kept.stream().noneMatch(d -> d.isAfter(LocalDate.of(2021, 1, 2))),
                 "kept " + kept);
   }

   // -- finding 7b: week 13 of a quarter -----------------------------------------------------

   /**
    * Week to Date / Same Week by Week in context Quarter, period Year. A week belongs to the
    * quarter it starts in. Week 13 of Q1 is kept where the year starts on the last day of a
    * week: Monday weeks 2017 (Jan 1 is a Sunday, week 13 starts Mar 27) and Sunday weeks 2022
    * (Jan 1 is a Saturday, week 13 starts Mar 27).
    */
   @ParameterizedTest
   @CsvSource({ "monday,2017", "sunday,2022" })
   void weekOfQuarterThirteenIsKeptWhenTheYearStartsOnTheLastDayOfAWeek(String weekStart, int year) {
      LocalDate week13 = LocalDate.of(year, 3, 27);
      assertEquals(weekStart.equals("monday") ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY,
                   week13.getDayOfWeek());

      for(List<LocalDate> kept : keptWeekOfQuarter(weekStart)) {
         assertTrue(kept.contains(week13), weekStart + " " + year + " kept " + kept);
      }
   }

   /**
    * The comparison keeps week 13 of every quarter (the End Date is in week 13 of its quarter),
    * so a quarter with only 12 weeks that start in it contributes nothing. 2019 Q1 under Monday
    * weeks (first week starts Jan 7, last Mar 25, the next starts Apr 1) and 2018 Q1 under
    * Sunday weeks (first starts Jan 7, last Mar 25, the next starts Apr 1): no day from Jan 1 to
    * Apr 7 is kept, in particular nothing of the week that starts Apr 1.
    */
   @ParameterizedTest
   @CsvSource({ "monday,2019", "sunday,2018" })
   void weekOfQuarterThirteenIsAbsentWhenOnlyTwelveWeeksStartInTheQuarter(String weekStart,
                                                                         int year)
   {
      assertEquals(weekStart.equals("monday") ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY,
                   LocalDate.of(year, 4, 1).getDayOfWeek());

      for(List<LocalDate> kept : keptWeekOfQuarter(weekStart)) {
         String msg = weekStart + " " + year + " kept " + kept;
         // other quarters still contribute their week 13, so the comparison is not empty
         assertFalse(kept.isEmpty(), msg);
         assertTrue(kept.stream().noneMatch(d -> d.getYear() == year && d.getMonthValue() <= 3),
                    msg);
         assertTrue(kept.stream().noneMatch(d -> d.equals(LocalDate.of(year, 4, 1))
                                            || (d.getYear() == year && d.getMonthValue() == 4
                                                && d.getDayOfMonth() <= 7)), msg);
      }
   }

   /**
    * Kept days for Week to Date and Same Week, periods 2016 to 2025, End Date 2025-12-31 or
    * 2026-01-02 (both in week 13 of Q4 2025 under Sunday and Monday weeks).
    */
   private static List<List<LocalDate>> keptWeekOfQuarter(String weekStart) {
      List<List<LocalDate>> result = new ArrayList<>();

      for(int level : new int[] { DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.SAME_WEEK }) {
         for(String end : new String[] { "2025-12-31", "2026-01-02" }) {
            result.add(WeekStartUtil.withWeekStart(weekStart, () -> keptDays(
               standard(XConstants.YEAR_DATE_GROUP, level, DateComparisonInfo.WEEK,
                        XConstants.QUARTER_DATE_GROUP, end, true, 10))));
         }
      }

      return result;
   }

   // -- helpers ------------------------------------------------------------------------------

   private static DateComparisonInfo standard(int periodLevel, int intervalLevel, int granularity,
                                              int contextLevel, String endDay, boolean inclusive,
                                              int preCount)
   {
      DateComparisonInfo dc = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(preCount);
      periods.setDateLevel(periodLevel);
      periods.setToDate(false);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDay);
      periods.setInclusive(inclusive);
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(intervalLevel);
      interval.setGranularity(granularity);
      interval.setContextLevel(contextLevel);
      interval.setEndDayAsToDate(true);
      interval.setInclusive(true);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      return dc;
   }

   private static DateComparisonInfo custom(String[][] ranges) {
      DateComparisonInfo dc = new DateComparisonInfo();
      List<DatePeriod> list = new ArrayList<>();

      for(String[] range : ranges) {
         list.add(new DatePeriod(toDate(LocalDate.parse(range[0])),
                                 toDate(LocalDate.parse(range[1]))));
      }

      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(list);
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(DateComparisonInfo.ALL);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      return dc;
   }

   /** The days of 2015 to 2026 the comparison's condition list keeps. */
   private static List<LocalDate> keptDays(DateComparisonInfo dc) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);
      ConditionList conditions = dc.getDateComparisonConditions(ref);
      assertNotNull(conditions);
      ConditionGroup group = new ConditionGroup(conditions);
      List<LocalDate> days = new ArrayList<>();

      for(LocalDate day = LocalDate.of(2015, 1, 1); day.isBefore(LocalDate.of(2027, 1, 1));
          day = day.plusDays(1))
      {
         if(group.evaluate(new Object[] { toDate(day) })) {
            days.add(day);
         }
      }

      return days;
   }

   private static Date toDate(LocalDate day) {
      return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
   }
}
