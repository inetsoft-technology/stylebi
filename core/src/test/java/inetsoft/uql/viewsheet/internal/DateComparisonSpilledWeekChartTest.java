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

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.test.dc.DcShape;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79149 through the real chart pipeline (the DC query conditions, ChartDcProcessor and
 * GraphGenerator) for the reporter's chart: Date by Year x Sum(Paid), period Quarter, interval
 * Same Week, granularity Week of Month, context Month, one row per day with a pseudo-random Paid.
 *
 * <p>The chart labels the days of 2021-01-03..09 (Sunday weeks) as week 5 of December 2020, part
 * {@code 3-5} of the quarter that starts 2020-10-01: December has only four weeks that start in
 * it, so its week 5 rolls into the next month, as February's does into March. That row must be
 * in the data set; it is the counterpart that the Change of Q1 2021 {@code 3-5} is computed
 * from.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonSpilledWeekChartTest {
   private static final LocalDate FROM = LocalDate.of(2017, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);
   private static final String WEEK = "WeekOfMonth(Date)";
   private static final String QUARTER = "QuarterOfWeek(Date)";
   private static final String SUM = "Sum(Paid)";
   private static final String CHANGE =
      "Change from previous quarter of QuarterOfWeek(Date): Sum(Paid)";

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   /**
    * Change, two periods back, non-inclusive: Q4 2020 is the hidden prior quarter. Its
    * {@code 3-5} row is the sum of the days of week 5 of December 2020 (computed here from the
    * day values, not read from the data set) and the Q1 2021 {@code 3-5} Change is the
    * difference to it.
    */
   @ParameterizedTest
   @CsvSource({ "sunday,2021-08-29", "monday,2021-08-30" })
   void changeHasTheHiddenPriorQuartersWeekFive(String weekStart, String endDate)
      throws Exception
   {
      DayOfWeek first = weekStart.equals("sunday") ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
      DataSet data = chart(weekStart, endDate, DateComparisonInfo.CHANGE, false, 2);

      long decWeek5 = sum(weekOfMonth(YearMonth.of(2020, 12), first, 5));
      long marWeek5 = sum(weekOfMonth(YearMonth.of(2021, 3), first, 5));
      // the day values must not be accidentally equal, or the Change would prove nothing
      assertNotEquals(decWeek5, marWeek5);

      int prior = row(data, "3-5", "2020-10-01");
      int shown = row(data, "3-5", "2021-01-01");
      assertTrue(prior >= 0, "no 3-5 row for the quarter of 2020-10-01: " + dump(data));
      assertTrue(shown >= 0, "no 3-5 row for the quarter of 2021-01-01: " + dump(data));
      assertEquals(Double.valueOf(decWeek5), number(data, SUM, prior), dump(data));
      assertEquals(Double.valueOf(marWeek5), number(data, SUM, shown), dump(data));
      assertEquals(Double.valueOf(marWeek5 - decWeek5), number(data, CHANGE, shown), dump(data));
   }

   /** The whole data set of the reporter's chart (Sunday weeks): 8 rows, not 7. */
   @Test
   void changeHasEightRowsForTheReportersChart() throws Exception {
      DataSet data = chart("sunday", "2021-08-29", DateComparisonInfo.CHANGE, false, 2);

      // 1-5, 2-5 and 3-5 of the quarters 2020-10-01 and 2021-01-01, and 1-5 and 2-5 of the quarter
      // 2021-04-01 (its 3-5 is June's week 5, which starts 2021-07-04, after the last period's end)
      assertEquals(8, data.getRowCount(), dump(data));
   }

   /**
    * Value shows the quarters 2020-10-01 and 2021-01-01 (End Date 2021-06-01, two back): the
    * 2020-10-01 quarter's week 5 of December is data of a shown period.
    */
   @Test
   void valueShowsTheShownQuartersWeekFive() throws Exception {
      DataSet data = chart("sunday", "2021-06-01", DateComparisonInfo.VALUE, false, 2);
      long decWeek5 = sum(weekOfMonth(YearMonth.of(2020, 12), DayOfWeek.SUNDAY, 5));

      int prior = row(data, "3-5", "2020-10-01");
      assertTrue(prior >= 0, "no 3-5 row for the quarter of 2020-10-01: " + dump(data));
      assertEquals(Double.valueOf(decWeek5), number(data, SUM, prior), dump(data));
   }

   private DataSet chart(String weekStart, String endDate, int option, boolean inclusive,
                         int preCount) throws Exception
   {
      return WeekStartUtil.withWeekStart(weekStart, () -> {
         try {
            DateComparisonInfo dc = DcShape.byId("Q-sameW-gW-cM")
               .toInfo(endDate, false, inclusive, option, preCount, true);
            VGraphPair pair = fixture.chart(dc, XConstants.YEAR_DATE_GROUP, FROM, TO,
                                            DcInMemoryViewsheetFixture::dayValue);
            DataSet data = pair.getData();
            assertNotNull(data);

            return data;
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      });
   }

   /** The row of a week-of-month part and quarter (a {@code yyyy-MM-dd} date), or -1. */
   private static int row(DataSet data, String week, String quarter) {
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");

      for(int r = 0; r < data.getRowCount(); r++) {
         if(week.equals(String.valueOf(data.getData(WEEK, r))) &&
            quarter.equals(fmt.format((Date) data.getData(QUARTER, r))))
         {
            return r;
         }
      }

      return -1;
   }

   /** The value of a column of a row; null (a missing Change) fails the comparison. */
   private static Double number(DataSet data, String column, int row) {
      Object value = data.getData(column, row);

      return value == null ? null : ((Number) value).doubleValue();
   }

   private static String dump(DataSet data) {
      StringBuilder sb = new StringBuilder("data set:");

      for(int r = 0; r < data.getRowCount(); r++) {
         sb.append("\n  ").append(data.getData(WEEK, r)).append(" | ")
            .append(data.getData(QUARTER, r)).append(" | ").append(data.getData(SUM, r))
            .append(" | ").append(data.getData(CHANGE, r));
      }

      return sb.toString();
   }

   private static long sum(LocalDate[] days) {
      long total = 0;

      for(LocalDate day : days) {
         total += DcInMemoryViewsheetFixture.dayValue(day);
      }

      return total;
   }

   /** The days of week n of a month: the weeks that start in it, a missing number rolling over. */
   private static LocalDate[] weekOfMonth(YearMonth month, DayOfWeek first, int n) {
      LocalDate start = month.atDay(1).with(TemporalAdjusters.nextOrSame(first)).plusWeeks(n - 1);
      LocalDate[] days = new LocalDate[7];

      for(int i = 0; i < 7; i++) {
         days[i] = start.plusDays(i);
      }

      return days;
   }
}
