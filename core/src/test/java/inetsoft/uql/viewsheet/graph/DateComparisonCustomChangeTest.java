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

import inetsoft.graph.data.DataSet;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.test.dc.DcPeriods;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Change / Percent of Custom Periods ("Change from previous range"), judged from the chart data
 * set, for every granularity (All, Year, Quarter, Month, Week, Day).
 *
 * <p>The expected rows are worked out here from the days of each range. A range has one row per
 * unit of the granularity that it touches (a week, month ... clipped to the range), in date order,
 * and its value is the sum of the days of the range in that unit. The Change of the k-th row of a
 * range is its value minus the value of the k-th row of the previous range in the list, empty for
 * the first range or when the previous range has fewer rows: the ranges are compared by the
 * position of the unit in the range, which is also how the merged axis label joins the first
 * unit of every range (for example "2020 Jan / 2020 Dec / 2021 Mar"). Percent is the ratio.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonCustomChangeTest {
   private static final String[][][] RANGE_SETS = {
      // ranges with different numbers of units, one crossing a year and a quarter
      { { "2020-01-06", "2020-02-16" }, { "2020-12-28", "2021-02-07" }, { "2021-03-01", "2021-04-11" } },
      // the same month of two years
      { { "2019-03-01", "2019-03-31" }, { "2020-03-01", "2020-03-31" } },
      // around a leap day and a common-year February
      { { "2020-02-28", "2020-03-02" }, { "2021-02-27", "2021-03-02" } },
      // adjacent ranges
      { { "2020-01-01", "2020-01-31" }, { "2020-02-01", "2020-02-29" } },
      // overlapping ranges
      { { "2020-01-01", "2020-03-31" }, { "2020-02-15", "2020-05-15" } } };
   private static final int[] GRANULARITIES = {
      DateComparisonInfo.ALL, DateComparisonInfo.YEAR, DateComparisonInfo.QUARTER,
      DateComparisonInfo.MONTH, DateComparisonInfo.WEEK, DateComparisonInfo.DAY };
   private static final LocalDate FROM = LocalDate.of(2019, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);

   private static final int OVERLAPPING_SET = 4;

   /**
    * Bug #79140 finding 1: with Change, the units of a range that the next range has no unit for
    * (the 6th week of March 2019 against 5 in March 2020; January 30 and 31 against February's
    * 29 days) are missing from the data set; with Value they are there.
    */
   private static boolean earlierRangeIsLonger(int set, int granularity) {
      return set == 1 && granularity == DateComparisonInfo.WEEK ||
         set == 3 && granularity == DateComparisonInfo.DAY;
   }

   private record Case(int set, int granularity, String weekStart, int option) {
      @Override
      public String toString() {
         return "set" + set + " g" + granularity + " " + weekStart +
            (option == DateComparisonInfo.PERCENT ? " percent" :
               option == DateComparisonInfo.VALUE ? " value" : " change");
      }
   }

   static Stream<Case> cases() {
      List<Case> cases = new ArrayList<>();

      // the overlapping set is only in overlappingRangesEachKeepTheirDays
      for(int set = 0; set < OVERLAPPING_SET; set++) {
         for(int granularity : GRANULARITIES) {
            if(earlierRangeIsLonger(set, granularity)) {
               continue;
            }

            cases.add(new Case(set, granularity, "sunday", DateComparisonInfo.CHANGE));

            if(granularity == DateComparisonInfo.WEEK) {
               cases.add(new Case(set, granularity, "monday", DateComparisonInfo.CHANGE));
            }
         }
      }

      // Value only, to tell a row missing from the data set from a row missing for the Change
      cases.add(new Case(1, DateComparisonInfo.WEEK, "sunday", DateComparisonInfo.VALUE));
      cases.add(new Case(3, DateComparisonInfo.DAY, "sunday", DateComparisonInfo.VALUE));

      // Percent is the ratio of the same pairs
      cases.add(new Case(0, DateComparisonInfo.MONTH, "sunday", DateComparisonInfo.PERCENT));
      cases.add(new Case(0, DateComparisonInfo.DAY, "sunday", DateComparisonInfo.PERCENT));

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
   void changeIsAgainstThePreviousRange(Case c) {
      List<String> problems = problems(c);

      assertTrue(problems.isEmpty(),
                 c + ": " + problems.size() + " problems, first:\n" +
                 String.join("\n", problems.subList(0, Math.min(8, problems.size()))));
   }

   @Test
   @Disabled("Bug #79140 (finding 1): Change drops the units of an earlier range that the next range has no unit for")
   void changeKeepsTheUnitsOfAnEarlierLongerRange() {
      // January 1-31 and February 1-29 by day: January 30 and 31 have no counterpart
      List<String> problems = problems(new Case(3, DateComparisonInfo.DAY, "sunday",
                                                DateComparisonInfo.CHANGE));

      assertTrue(problems.isEmpty(), problems.toString());
   }

   @Test
   @Disabled("Bug #79140 (finding 2): the days two overlapping Custom Periods share belong to the first one only")
   void overlappingRangesEachKeepTheirDays() {
      // Jan 1 - Mar 31 and Feb 15 - May 15: the second range loses Feb 15 - Mar 31, with Value too
      List<String> problems = problems(new Case(OVERLAPPING_SET, DateComparisonInfo.ALL, "sunday",
                                                DateComparisonInfo.VALUE));

      assertTrue(problems.isEmpty(), problems.toString());
   }

   private List<String> problems(Case c) {
      return WeekStartUtil.withWeekStart(c.weekStart(), () -> {
         try {
            return judge(c);
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      });
   }

   private List<String> judge(Case c) throws Exception {
      String[][] ranges = RANGE_SETS[c.set()];
      List<DatePeriod> list = new ArrayList<>();

      for(String[] range : ranges) {
         list.add(new DatePeriod(DcPeriods.toDate(LocalDate.parse(range[0])),
                                 DcPeriods.toDate(LocalDate.parse(range[1]))));
      }

      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(list);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(c.granularity());
      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(c.option());

      DataSet data = fixture.chart(dc, XConstants.YEAR_DATE_GROUP, FROM, TO,
                                   DcInMemoryViewsheetFixture::dayValue).getData();
      boolean hasChange = c.option() != DateComparisonInfo.VALUE;
      int changeCol = hasChange ? data.getColCount() - 1 : -1;
      int valueCol = hasChange ? changeCol - 1 : data.getColCount() - 1;
      int labelCol = valueCol - 1;
      List<String> problems = new ArrayList<>();

      // the rows of each range, in the order of the data set; the labels sort like the ranges
      Map<String, List<Integer>> rowsOf = new TreeMap<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         rowsOf.computeIfAbsent(String.valueOf(data.getData(labelCol, r)), k -> new ArrayList<>())
            .add(r);
      }

      if(rowsOf.size() != ranges.length) {
         return List.of("expected " + ranges.length + " ranges, the data set has " + rowsOf.keySet());
      }

      List<List<Integer>> rows = new ArrayList<>(rowsOf.values());
      DcPeriods weeks = new DcPeriods(XConstants.WEEK_DATE_GROUP);
      List<List<Double>> expected = new ArrayList<>();

      for(String[] range : ranges) {
         expected.add(unitSums(LocalDate.parse(range[0]), LocalDate.parse(range[1]),
                               c.granularity(), weeks));
      }

      boolean percent = c.option() == DateComparisonInfo.PERCENT;

      for(int range = 0; range < ranges.length; range++) {
         List<Double> sums = expected.get(range);

         if(rows.get(range).size() != sums.size()) {
            problems.add("range " + range + " has " + rows.get(range).size() + " rows, expected " +
                         sums.size());
            continue;
         }

         for(int k = 0; k < sums.size(); k++) {
            int row = rows.get(range).get(k);
            double value = ((Number) data.getData(valueCol, row)).doubleValue();
            Double previous = range > 0 && k < expected.get(range - 1).size() ?
               expected.get(range - 1).get(k) : null;
            Double change = previous == null ? null :
               percent ? (sums.get(k) - previous) / previous : sums.get(k) - previous;
            Object actual = hasChange ? data.getData(changeCol, row) : null;

            if(Math.abs(value - sums.get(k)) > 1E-6) {
               problems.add("range " + range + " unit " + k + " value " + value + ", the days add up to " +
                            sums.get(k));
            }

            if(!hasChange) {
               continue;
            }

            boolean same = change == null ? actual == null :
               actual instanceof Number && Math.abs(((Number) actual).doubleValue() - change) < 1E-6;

            if(!same) {
               problems.add("range " + range + " unit " + k + " change " + actual + ", expected " + change);
            }
         }
      }

      return problems;
   }

   /** The sums of the days of a range per unit of the granularity, in date order. */
   private static List<Double> unitSums(LocalDate from, LocalDate to, int granularity,
                                        DcPeriods weeks)
   {
      Map<Long, Double> sums = new TreeMap<>();

      for(LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
         sums.merge(unit(day, granularity, weeks), (double) DcInMemoryViewsheetFixture.dayValue(day),
                    Double::sum);
      }

      return new ArrayList<>(sums.values());
   }

   private static long unit(LocalDate day, int granularity, DcPeriods weeks) {
      switch(granularity) {
      case DateComparisonInfo.YEAR:
         return day.getYear();
      case DateComparisonInfo.QUARTER:
         return day.getYear() * 4L + (day.getMonthValue() - 1) / 3;
      case DateComparisonInfo.MONTH:
         return day.getYear() * 12L + day.getMonthValue() - 1;
      case DateComparisonInfo.WEEK:
         return weeks.ordinal(day);
      case DateComparisonInfo.DAY:
         return day.toEpochDay();
      default:
         return 0;
      }
   }
}
