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
package inetsoft.web.wiz.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79883: a calendar narrowing the date column a chart's date comparison runs on leaves a
 * period with no rows. The guard must say so exactly when the calendar's ranges are disjoint from
 * a period on the same table and column, and stay silent otherwise.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class ComparisonFilterGuardTest {
   // ── the accessor ────────────────────────────────────────────────────────────────────────

   @Test
   void yearPeriodsWithoutToDateCoverTheWholeYears() throws Exception {
      // the reporter's shape: year, one period back, interval all, end 2025-12-31
      List<Date[]> bounds = comparison(XConstants.YEAR_DATE_GROUP, 1, "2025-12-31", true, false,
                                       DateComparisonInfo.VALUE).getPeriodBounds();

      assertEquals(List.of("2025-01-01..2025-12-31", "2024-01-01..2024-12-31"), show(bounds));
   }

   @Test
   void toDateDoesNotChangeTheWholeLevelBounds() throws Exception {
      List<Date[]> bounds = comparison(XConstants.YEAR_DATE_GROUP, 1, "2025-06-15", true, true,
                                       DateComparisonInfo.VALUE).getPeriodBounds();

      assertEquals(List.of("2025-01-01..2025-12-31", "2024-01-01..2024-12-31"), show(bounds));
   }

   @Test
   void notInclusiveDropsTheCurrentPeriod() throws Exception {
      List<Date[]> bounds = comparison(XConstants.YEAR_DATE_GROUP, 2, "2025-12-31", false, false,
                                       DateComparisonInfo.VALUE).getPeriodBounds();

      assertEquals(List.of("2024-01-01..2024-12-31", "2023-01-01..2023-12-31"), show(bounds));
   }

   @Test
   void theExtraPriorPeriodOfAChangeOptionIsNotReported() throws Exception {
      List<Date[]> bounds = comparison(XConstants.YEAR_DATE_GROUP, 1, "2025-12-31", true, false,
                                       DateComparisonInfo.CHANGE).getPeriodBounds();

      assertEquals(2, bounds.size());
   }

   @Test
   void quarterAndMonthPeriodsStepByTheirOwnLevel() throws Exception {
      assertEquals(List.of("2025-04-01..2025-06-30", "2025-01-01..2025-03-31"),
                   show(comparison(XConstants.QUARTER_DATE_GROUP, 1, "2025-05-20", true, false,
                                   DateComparisonInfo.VALUE).getPeriodBounds()));
      assertEquals(List.of("2025-03-01..2025-03-31", "2025-02-01..2025-02-28"),
                   show(comparison(XConstants.MONTH_DATE_GROUP, 1, "2025-03-10", true, false,
                                   DateComparisonInfo.VALUE).getPeriodBounds()));
   }

   @Test
   void customPeriodsAreReportedAsGiven() throws Exception {
      DateComparisonInfo dc = new DateComparisonInfo();
      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(List.of(new DatePeriod(day("2024-03-01"), day("2024-03-15")),
                                     new DatePeriod(day("2025-03-01"), day("2025-03-15"))));
      dc.setDateComparisonPeriods(periods);
      dc.setDateComparisonInterval(new DateComparisonInterval());
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      assertEquals(List.of("2024-03-01..2024-03-15", "2025-03-01..2025-03-15"),
                   show(dc.getPeriodBounds()));
   }

   @Test
   void weekAlignedIntervalsAreNotStated() throws Exception {
      DateComparisonInfo dc = comparison(XConstants.YEAR_DATE_GROUP, 1, "2025-12-31", true, false,
                                         DateComparisonInfo.VALUE);
      dc.getInterval().setContextLevel(XConstants.WEEK_DATE_GROUP);

      assertNull(dc.getPeriodBounds());
   }

   // ── the guard ───────────────────────────────────────────────────────────────────────────

   @Test
   void narrowingToOneYearNamesTheDeadPeriod() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");

      List<String> notes = ComparisonFilterGuard.notes(sheet.vs, "Cal");

      assertEquals(1, notes.size(), notes.toString());
      String note = notes.get(0);
      assertTrue(note.startsWith("Filter note: date comparison on chart 'Sales' "), note);
      assertTrue(note.contains("no data for period 2024-01-01 to 2024-12-31 because calendar " +
                               "'Cal' on D selects only 2025-04-01 to 2025-06-30"), note);
      assertFalse(note.contains("2025-01-01 to 2025-12-31 because"), note);
      // one sentence between "comparison" and "period" with no full stop, as callers read it
      assertFalse(note.substring(0, note.indexOf("period")).contains("."), note);
   }

   @Test
   void aRangeTouchingBothYearsIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2024-12-01", "2025-01-31");

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aSingleCalendarWithOnlyDaysOfOneYearNamesTheOtherYear() throws Exception {
      Sheet sheet = new Sheet();
      sheet.singleCalendar("d2025-3-1", "d2025-3-2", "d2025-3-3");

      List<String> notes = ComparisonFilterGuard.notes(sheet.vs, "Cal");

      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains("period 2024-01-01 to 2024-12-31"), notes.get(0));
   }

   @Test
   void aSelectionAvoidingEveryPeriodNamesEveryPeriod() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2022-01-01", "2022-02-01");

      List<String> notes = ComparisonFilterGuard.notes(sheet.vs, "Cal");

      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains(
         "period 2025-01-01 to 2025-12-31, 2024-01-01 to 2024-12-31"), notes.get(0));
   }

   @Test
   void aCalendarOnAnotherColumnIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");
      sheet.calendarInfo().setDataRef(column("Other"));

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aCalendarOnAnotherTableIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");
      sheet.calendarInfo().setTableName("Elsewhere");

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aDisabledCalendarIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");
      sheet.calendarInfo().setEnabled(false);

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aCalendarWithNoDatesIsSilent() throws Exception {
      Sheet sheet = new Sheet();

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aChartWithoutAComparisonIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");
      sheet.chartInfo().setDateComparisonInfo(null);

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void aChartWithNoDateDimensionHasNoComparisonColumnAndIsSilent() throws Exception {
      Sheet sheet = new Sheet();
      sheet.rangeCalendar("2025-04-01", "2025-06-30");
      sheet.chart.getVSChartInfo().removeXField(0);

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   @Test
   void unknownOrNullInputsNeverThrow() throws Exception {
      Sheet sheet = new Sheet();

      assertEquals(List.of(), ComparisonFilterGuard.notes(null, "Cal"));
      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, null));
      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Missing"));
      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Sales"));
   }

   @Test
   void aPeriodComparisonCalendarWithOneDisjointRangeIsStillCoveredByTheOther() throws Exception {
      Sheet sheet = new Sheet();
      CalendarVSAssemblyInfo info = sheet.calendarInfo();
      info.setViewMode(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
      info.setPeriod(true);
      info.setDates(new String[] { "d2024-2-1", "d2024-2-28", "d2025-2-1", "d2025-2-28" });

      assertEquals(List.of(), ComparisonFilterGuard.notes(sheet.vs, "Cal"));
   }

   // ── fixtures ────────────────────────────────────────────────────────────────────────────

   private static DateComparisonInfo comparison(int level, int preCount, String end,
                                                boolean inclusive, boolean toDate, int option)
      throws Exception
   {
      DateComparisonInfo dc = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(level);
      periods.setPreCount(preCount);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(end);
      periods.setToDate(toDate);
      periods.setInclusive(inclusive);
      dc.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setGranularity(DateComparisonInfo.ALL);
      interval.setContextLevel(level);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(option);
      return dc;
   }

   private static List<String> show(List<Date[]> bounds) {
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      List<String> out = new ArrayList<>();

      for(Date[] b : bounds) {
         out.add(fmt.format(b[0]) + ".." + fmt.format(b[1]));
      }

      return out;
   }

   private static Date day(String text) throws Exception {
      return new SimpleDateFormat("yyyy-MM-dd").parse(text);
   }

   private static ColumnRef column(String name) {
      AttributeRef attr = new AttributeRef(null, name);
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      return column;
   }

   /** A chart comparing 2025 with 2024 on D and a calendar bound to the same column and table. */
   private static final class Sheet {
      Sheet() throws Exception {
         vs = new Viewsheet();

         chart = new ChartVSAssembly(vs, "Sales");
         SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, "T");
         chart.setSourceInfo(source);
         VSChartInfo info = chart.getVSChartInfo();
         VSChartDimensionRef dim = new VSChartDimensionRef();
         dim.setDataRef(column("D"));
         dim.setGroupColumnValue("D");
         dim.setDataType(XSchema.DATE);
         dim.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");
         info.addXField(dim);
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setColumnValue("amount");
         agg.setFormulaValue("Sum");
         info.addYField(agg);
         chartInfo().setDateComparisonInfo(comparison(
            XConstants.YEAR_DATE_GROUP, 1, "2025-12-31", true, false, DateComparisonInfo.VALUE));
         vs.addAssembly(chart);

         calendar = new CalendarVSAssembly(vs, "Cal");
         CalendarVSAssemblyInfo cinfo = calendarInfo();
         cinfo.setTableName("T");
         cinfo.setDataRef(column("D"));
         vs.addAssembly(calendar);
      }

      ChartVSAssemblyInfo chartInfo() {
         return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      }

      CalendarVSAssemblyInfo calendarInfo() {
         return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
      }

      void rangeCalendar(String from, String to) {
         CalendarVSAssemblyInfo info = calendarInfo();
         info.setViewMode(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
         info.setDates(new String[] { token(from), token(to) });
      }

      void singleCalendar(String... dates) {
         calendarInfo().setDates(dates);
      }

      /** "2025-04-01" as the calendar's 0-based-month day token "d2025-3-1". */
      private static String token(String iso) {
         String[] p = iso.split("-");
         return "d" + p[0] + "-" + (Integer.parseInt(p[1]) - 1) + "-" + Integer.parseInt(p[2]);
      }

      final Viewsheet vs;
      final ChartVSAssembly chart;
      final CalendarVSAssembly calendar;
   }
}
