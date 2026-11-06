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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79149, the second week-of-month label branch of
 * {@link DateComparisonUtil#formatPartMergeCell}: the {@code WeekOfYear(} part that encodes
 * month * 10 + week of month (a Year period compared by Week to Date with Day granularity and
 * Year context, End Date in week 5 of December 2019). Week 5 of December 2016 starts 2017-01-01
 * (Monday weeks 2017-01-02); its label must be that day, as for every other month, not the
 * same day of December's week 4.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonSpilledWeekYearLabelTest {
   private static final LocalDate FROM = LocalDate.of(2016, 1, 1);
   private static final LocalDate TO = LocalDate.of(2022, 1, 1);

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   @ParameterizedTest
   @CsvSource({ "sunday,2019-12-29", "monday,2019-12-30" })
   void everyLabelOfAYearPeriodIsTheRealDay(String weekStart, String endDate) throws Exception {
      DayOfWeek first = weekStart.equals("sunday") ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
      DataSet data = WeekStartUtil.withWeekStart(weekStart, () -> {
         try {
            DateComparisonInfo dc = DcShape.byId("Y-wtd-gD-cY")
               .toInfo(endDate, false, false, DateComparisonInfo.CHANGE, 2, true);
            VGraphPair pair = fixture.chart(dc, XConstants.YEAR_DATE_GROUP, FROM, TO,
                                            DcInMemoryViewsheetFixture::dayValue);
            return pair.getData();
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      });

      assertNotNull(data);
      int periodCol = data.indexOfHeader("YearOfWeek(Date)");
      assertTrue(periodCol >= 0, "no YearOfWeek(Date) period column");
      assertTrue(data.indexOfHeader("WeekOfYear(Date)") >= 0, "no WeekOfYear(Date) part column");
      boolean sawSpill = false;

      for(int r = 0; r < data.getRowCount(); r++) {
         // the cell is the week of year part, encoded month * 10 + week of month, and the day
         MergePartCell cell = (MergePartCell) data.getData(0, r);
         Date period = (Date) data.getData(periodCol, r);
         String[] part = String.valueOf(cell).split("-");
         int year = new java.sql.Date(period.getTime()).toLocalDate().getYear();
         int month = Integer.parseInt(part[0]) / 10;
         int week = Integer.parseInt(part[0]) % 10;
         int day = Integer.parseInt(part[1]);
         // the day is Calendar.DAY_OF_WEEK (Sunday 1): its offset from the week's first day
         int offset = (day - (first == DayOfWeek.SUNDAY ? 1 : 2) + 7) % 7;
         LocalDate expected = YearMonth.of(year, month).atDay(1)
            .with(TemporalAdjusters.nextOrSame(first)).plusWeeks(week - 1).plusDays(offset);
         String label = WeekStartUtil.withWeekStart(
            weekStart, () -> DateComparisonUtil.formatPartMergeCell(cell, period, null));

         assertEquals(expected.toString(), label, "row " + cell + " / " + year);
         sawSpill |= expected.getYear() > year;
      }

      // December 2016's week 5 starts in 2017: the case under test must be reached
      assertTrue(sawSpill, "no row whose week of month rolls into the next year");
   }
}
