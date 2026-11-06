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
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
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
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79149: the label of every bar of the reporter's chart (Quarter / Same Week / Week of Month
 * / Month context), as {@link DateComparisonUtil#formatPartMergeCell} makes it, is the real week
 * start of its part. The week 5 of December 2020 starts 2021-01-03 (Monday weeks: 2021-01-04) and
 * must be labelled with that day, as week 5 of November (2020-11-29) and February (2021-03-07)
 * already are, not moved back a week into December's week 4.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonSpilledWeekLabelTest {
   private static final LocalDate FROM = LocalDate.of(2017, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);
   private static final String WEEK = "WeekOfMonth(Date)";
   private static final String QUARTER = "QuarterOfWeek(Date)";

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   /** Change shape (the December week is the hidden prior), Value shapes (it is shown), and an
    * End Date in week 1 (no spill at all), Sunday and Monday weeks. */
   @ParameterizedTest
   @CsvSource({
      "sunday,2021-08-29,CHANGE,false", "monday,2021-08-30,CHANGE,false",
      "sunday,2021-06-01,VALUE,false", "monday,2021-06-01,VALUE,false",
      "sunday,2021-06-01,VALUE,true", "sunday,2021-02-01,VALUE,true",
      "sunday,2021-11-10,CHANGE,false", "monday,2021-11-10,VALUE,false" })
   void everyLabelIsTheRealWeekStart(String weekStart, String endDate, String option,
                                     boolean inclusive) throws Exception
   {
      DayOfWeek first = weekStart.equals("sunday") ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
      int opt = option.equals("CHANGE") ? DateComparisonInfo.CHANGE : DateComparisonInfo.VALUE;
      DataSet data = chart(weekStart, endDate, opt, inclusive);
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      boolean sawDec = false;

      for(int r = 0; r < data.getRowCount(); r++) {
         MergePartCell cell = (MergePartCell) data.getData(WEEK, r);
         Date period = (Date) data.getData(QUARTER, r);
         String[] part = String.valueOf(cell).split("-");
         LocalDate quarter = new java.sql.Date(period.getTime()).toLocalDate();
         LocalDate month = quarter.plusMonths(Integer.parseInt(part[0]) - 1);
         LocalDate expected = month.withDayOfMonth(1).with(TemporalAdjusters.nextOrSame(first))
            .plusWeeks(Integer.parseInt(part[1]) - 1);

         // the label decodes the week with the week start of the moment it is formatted
         String label = WeekStartUtil.withWeekStart(
            weekStart, () -> DateComparisonUtil.formatPartMergeCell(cell, period, fmt));

         assertEquals(expected.toString(), label, "row " + cell + " / " + quarter);
         sawDec |= part[0].equals("3") && part[1].equals("5") && quarter.getMonthValue() == 10 &&
            quarter.getYear() == 2020;
      }

      // the shapes that have the December week 5 row must actually reach the case under test
      if(endDate.equals("2021-08-29") || endDate.equals("2021-08-30") ||
         endDate.equals("2021-06-01"))
      {
         assertTrue(sawDec, "no 3-5 row of the quarter 2020-10-01");
      }
   }

   private DataSet chart(String weekStart, String endDate, int option, boolean inclusive)
      throws Exception
   {
      return WeekStartUtil.withWeekStart(weekStart, () -> {
         try {
            DateComparisonInfo dc = DcShape.byId("Q-sameW-gW-cM")
               .toInfo(endDate, false, inclusive, option, 2, true);
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
}
