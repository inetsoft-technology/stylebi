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

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78454 through the real chart pipeline ({@code ViewsheetSandbox#getVGraphPair}: the DC
 * query conditions, ChartDcProcessor's full-week period/part columns and
 * GraphGenerator's applyDateRange() hide selector) for the bug's chart: Year(Date) x Sum(Paid),
 * Previous 2 Years, Week to Date for each week, granularity Week, End Date Saturday 2021-01-02
 * (its week starts 2020-12-27, so 2020 is the current full-week year). Every compared year must
 * plot all of its weeks; only the extra prior year fetched for Change is hidden.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonWeekAlignedChartTest {
   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();

   @BeforeEach
   void setUp() {
      fixture.setUp();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   /** Used to plot 53 weeks: the 2018-12-30 week of 2018 plus 2019. */
   @Test
   void valuePlotsTwoFullYears() throws Exception {
      assertEquals(Map.of("2018-01-01", 52, "2019-01-01", 52),
                   visibleWeeksPerYear(chart(DateComparisonInfo.VALUE, false)));
   }

   /** Used to show only 2019: the hide threshold was the calendar-anchored 2019-01-01. */
   @Test
   void changeHidesOnlyTheExtraPriorYear() throws Exception {
      assertEquals(Map.of("2018-01-01", 52, "2019-01-01", 52),
                   visibleWeeksPerYear(chart(DateComparisonInfo.CHANGE, false)));
   }

   /**
    * The toDate query was already week-anchored (2017, 2018 and 2019 up to week 51), but the
    * calendar-anchored threshold hid 2018 too.
    */
   @Test
   void toDateChangeHidesOnlyTheExtraPriorYear() throws Exception {
      assertEquals(Map.of("2018-01-01", 51, "2019-01-01", 51),
                   visibleWeeksPerYear(chart(DateComparisonInfo.CHANGE, true)));
   }

   /** The number of plotted (not hidden) rows, one per week, per full-week year. */
   private static Map<String, Integer> visibleWeeksPerYear(VGraphPair pair) {
      DataSet data = pair.getData();
      EGraph graph = pair.getEGraph();
      assertNotNull(data);
      assertNotNull(graph);
      GraphtDataSelector selector = graph.getElementCount() > 0 ?
         graph.getElement(0).getGraphDataSelector() : null;
      String yearCol = "YearOfWeek(Date)";
      assertTrue(data.indexOfHeader(yearCol) >= 0, "the period column is the full-week year");

      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      Map<String, Integer> counts = new TreeMap<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         if(selector == null || selector.accept(data, r, null)) {
            counts.merge(fmt.format((Date) data.getData(yearCol, r)), 1, Integer::sum);
         }
      }

      return counts;
   }

   private VGraphPair chart(int option, boolean toDate) throws Exception {
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCount(2);
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setToDate(toDate);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-01-02");
      periods.setInclusive(false);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setGranularity(DateComparisonInfo.WEEK);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      interval.setEndDayAsToDate(true);
      interval.setInclusive(true);

      DateComparisonInfo dc = new DateComparisonInfo();
      dc.setDateComparisonPeriods(periods);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(option);

      return fixture.yearChart(dc, LocalDate.of(2016, 1, 1), LocalDate.of(2022, 1, 1));
   }
}
