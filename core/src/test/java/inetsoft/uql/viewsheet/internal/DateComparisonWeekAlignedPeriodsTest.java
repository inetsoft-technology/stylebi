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
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.test.XTableUtil.date;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78454: with weeks aligned (week granularity, interval or context), Standard Periods of
 * Year/Quarter/Month must pick the End Date's period from the End Date's week start, the
 * same full-week convention as the *_OF_FULL_WEEK labels and the interval builder. The
 * periods range used to be anchored on the End Date's calendar period instead, so for an End
 * Date whose week started in the previous period the ANDed conditions kept one week of an
 * extra period plus one full period, instead of the requested number of full periods.
 *
 * <p>Each test evaluates the whole condition list with the real {@link ConditionGroup} on every
 * day of several years and buckets the surviving days by the period of their week start.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonWeekAlignedPeriodsTest {
   /** End Date Saturday 2021-01-02: its week starts 2020-12-27, so 2020 is the current year. */
   @Test
   void yearEndDateInAWeekStartedLastYearComparesTwoFullYears() {
      assertEquals(Map.of("2018", 52, "2019", 52),
                   weeksPerPeriod(weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-02")));
   }

   @Test
   void yearEndDatesAwayFromTheBoundaryAreUnchanged() {
      assertEquals(Map.of("2018", 52, "2019", 52),
                   weeksPerPeriod(weekToDate(XConstants.YEAR_DATE_GROUP, "2020-12-31")));
      assertEquals(Map.of("2019", 52, "2020", 52),
                   weeksPerPeriod(weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-05")));
   }

   @Test
   void inclusiveYearIncludesTheEndDatesFullWeekYear() {
      DateComparisonInfo dc = weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-02");
      ((StandardPeriods) dc.getPeriods()).setInclusive(true);

      assertEquals(Map.of("2018", 52, "2019", 52, "2020", 52), weeksPerPeriod(dc));
   }

   /** End Date Friday 2021-04-02: its week starts 2021-03-28, in Q1. */
   @Test
   void quarterEndDateInAWeekStartedLastQuarterComparesTwoFullQuarters() {
      assertEquals(Map.of("2020Q3", 13, "2020Q4", 13),
                   weeksPerPeriod(weekToDate(XConstants.QUARTER_DATE_GROUP, "2021-04-02")));
   }

   /** End Date Tuesday 2021-03-02: its week starts 2021-02-28, in February. */
   @Test
   void monthEndDateInAWeekStartedLastMonthComparesTwoFullMonths() {
      assertEquals(Map.of("2020-12", 4, "2021-01", 5),
                   weeksPerPeriod(weekToDate(XConstants.MONTH_DATE_GROUP, "2021-03-02")));
   }

   /**
    * The anchor follows week.start: under a Monday start, Sunday 2021-01-03 is in the week of
    * 2020-12-28, while Tuesday 2021-03-02 is in a week that starts in March.
    */
   @Test
   void mondayWeekStartMovesWhichEndDatesAreAffected() {
      WeekStartUtil.withWeekStart("monday", () -> {
         assertEquals(Map.of("2018", 53, "2019", 52),
                      weeksPerPeriod(weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-03")));
         assertEquals(Map.of("2021-01", 4, "2021-02", 4),
                      weeksPerPeriod(weekToDate(XConstants.MONTH_DATE_GROUP, "2021-03-02")));
      });
   }

   /**
    * Change fetches one extra prior period, and getStartDate() is the threshold the chart and
    * crosstab use to hide it. Both must use the week-start anchor, or the first compared year
    * (2018) is hidden as if it were the extra one.
    */
   @Test
   void changeFetchesOneExtraYearAndStartDateHidesOnlyIt() {
      DateComparisonInfo dc = weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-02");
      dc.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      assertEquals(Map.of("2017", 53, "2018", 52, "2019", 52), weeksPerPeriod(dc));
      assertEquals(date("2018-01-01"), dc.getStartDate());
   }

   /** The toDate branch already anchored on the week start; its conditions must not change. */
   @Test
   void toDateConditionsAreUnchangedAndStartDateIsWeekAnchored() {
      DateComparisonInfo dc = weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-02");
      ((StandardPeriods) dc.getPeriods()).setToDate(true);

      assertEquals(Map.of("2018", 51, "2019", 51), weeksPerPeriod(dc));
      assertEquals(date("2018-01-01"), dc.getStartDate());
   }

   /**
    * The toDate conditions are unchanged, but with Change the threshold must hide only the
    * extra 2017: the calendar-anchored 2019-01-01 also hid 2018, one of the compared years.
    */
   @Test
   void toDateChangeStartDateHidesOnlyTheExtraYear() {
      DateComparisonInfo dc = weekToDate(XConstants.YEAR_DATE_GROUP, "2021-01-02");
      ((StandardPeriods) dc.getPeriods()).setToDate(true);
      dc.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      assertEquals(Set.of("2017", "2018", "2019"), weeksPerPeriod(dc).keySet());
      assertEquals(date("2018-01-01"), dc.getStartDate());
   }

   /**
    * Compare Data Of: All has no interval list, so the periods range alone decides what is
    * fetched. It follows the same week-start anchor: the week of 2021-01-02 belongs to 2020,
    * so the previous 2 years are 2018 and 2019 (this used to give 2019 and 2020, putting the
    * End Date's own week in a "previous" year).
    */
   @Test
   void compareAllUsesTheWeekStartAnchor() {
      assertEquals(Map.of("2018", 52, "2019", 52),
                   weeksPerPeriod(compareAllByWeek("2021-01-02")));
      assertEquals(Map.of("2018", 52, "2019", 52),
                   weeksPerPeriod(compareAllByWeek("2020-12-31")));
      assertEquals(Map.of("2019", 52, "2020", 52),
                   weeksPerPeriod(compareAllByWeek("2021-01-05")));
   }

   /** Inclusive Compare All gets the full current year: it used to get only two years. */
   @Test
   void inclusiveCompareAllGetsThreeFullYears() {
      DateComparisonInfo dc = compareAllByWeek("2021-01-02");
      ((StandardPeriods) dc.getPeriods()).setInclusive(true);

      assertEquals(Map.of("2018", 52, "2019", 52, "2020", 52), weeksPerPeriod(dc));
   }

   /** Without week alignment the End Date's calendar period is still the anchor. */
   @Test
   void monthGranularityKeepsTheCalendarAnchor() {
      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods(XConstants.YEAR_DATE_GROUP, "2021-01-02"));

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.MONTH_TO_DATE);
      interval.setGranularity(DateComparisonInfo.MONTH);
      interval.setContextLevel(XConstants.MONTH_DATE_GROUP);
      interval.setEndDayAsToDate(true);
      interval.setInclusive(true);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      assertFalse(dc.alignWeek());
      assertEquals(date("2019-01-01"), dc.getStartDate());

      Set<String> years = new TreeSet<>();
      matchingDays(dc).forEach(day -> years.add(periodKey(day, XConstants.YEAR_DATE_GROUP)));
      assertEquals(Set.of("2019", "2020"), years);
   }

   // -- fixture plumbing --------------------------------------------------------------------

   /**
    * Previous 2 periods ending at {@code endDay} (not inclusive, toDate off), compared by
    * week to date for each week with week granularity, as in the bug's viewsheet.
    */
   private static DateComparisonInfo weekToDate(int periodLevel, String endDay) {
      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods(periodLevel, endDay));

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setGranularity(DateComparisonInfo.WEEK);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      interval.setEndDayAsToDate(true);
      interval.setInclusive(true);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      return dc;
   }

   /** Previous 2 years ending at {@code endDay}, Compare Data Of: All, by week. */
   private static DateComparisonInfo compareAllByWeek(String endDay) {
      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods(XConstants.YEAR_DATE_GROUP, endDay));

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(DateComparisonInfo.WEEK);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      assertTrue(dc.alignWeek());

      return dc;
   }

   private static StandardPeriods periods(int periodLevel, String endDay) {
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(2);
      periods.setDateLevel(periodLevel);
      periods.setToDate(false);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDay);
      periods.setInclusive(false);

      return periods;
   }

   /**
    * The number of distinct weeks with at least one matching day, per period of the week's
    * start day.
    */
   private static Map<String, Integer> weeksPerPeriod(DateComparisonInfo dc) {
      int periodLevel = ((StandardPeriods) dc.getPeriods()).getDateLevel();
      Map<String, Set<Date>> weeks = new TreeMap<>();

      for(Date day : matchingDays(dc)) {
         Date weekStart = weekStart(day);
         weeks.computeIfAbsent(periodKey(weekStart, periodLevel), k -> new HashSet<>())
            .add(weekStart);
      }

      Map<String, Integer> counts = new TreeMap<>();
      weeks.forEach((period, starts) -> counts.put(period, starts.size()));

      return counts;
   }

   private static List<Date> matchingDays(DateComparisonInfo dc) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);
      ConditionList conditions = dc.getDateComparisonConditions(ref);
      assertNotNull(conditions);

      ConditionGroup group = new ConditionGroup(conditions);
      List<Date> days = new ArrayList<>();
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(2015, Calendar.JANUARY, 1);

      while(cal.get(Calendar.YEAR) < 2023) {
         Date day = cal.getTime();

         if(group.evaluate(new Object[] { day })) {
            days.add(day);
         }

         cal.add(Calendar.DATE, 1);
      }

      return days;
   }

   private static Date weekStart(Date day) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(day);

      while(cal.get(Calendar.DAY_OF_WEEK) != Tool.getFirstDayOfWeek()) {
         cal.add(Calendar.DATE, -1);
      }

      return cal.getTime();
   }

   private static String periodKey(Date day, int periodLevel) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(day);
      int year = cal.get(Calendar.YEAR);

      if(periodLevel == XConstants.QUARTER_DATE_GROUP) {
         return year + "Q" + (cal.get(Calendar.MONTH) / 3 + 1);
      }
      else if(periodLevel == XConstants.MONTH_DATE_GROUP) {
         return String.format("%d-%02d", year, cal.get(Calendar.MONTH) + 1);
      }

      return String.valueOf(year);
   }
}
