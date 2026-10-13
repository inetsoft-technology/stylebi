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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.report.lens.DataSetTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78446: the date comparison runtime calc fields created by
 * DateComparisonInfo.createCalcRefForNonSupportPartLevel() are registered viewsheet-wide per
 * source table, so their names must never collide with a normal date level dimension's full
 * name (e.g. WeekOfYear(Date)). The names are built, recognized and stripped by one helper
 * in DateComparisonUtil, and the marker is never shown to users.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonCalcPartNameTest {
   private static final String[] PARTS = {
      DateComparisonUtil.DC_CALC_DAY_OF_YEAR, DateComparisonUtil.DC_CALC_WEEK_OF_YEAR,
      DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER,
      DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER_OF_WEEK,
      DateComparisonUtil.DC_CALC_WEEK_OF_QUARTER, DateComparisonUtil.DC_CALC_DAY_OF_QUARTER,
      DateComparisonUtil.DC_CALC_WEEK_OF_MONTH
   };

   /**
    * Every generated name round-trips through the helper, and none equals or is a substring
    * of any DateRangeRef full name (VSAQuery.removeUnusedCalcField uses String.contains).
    */
   @Test
   void allSevenNamesRoundTripAndNeverCollideWithDateLevelNames() {
      List<String> levelNames = new ArrayList<>();

      for(int level : new int[] {
         DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_INTERVAL,
         DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_INTERVAL,
         DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL,
         DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART,
         DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART,
         DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART,
         DateRangeRef.DAY_OF_YEAR_PART, DateRangeRef.DAY_OF_QUARTER_PART,
         DateRangeRef.WEEK_OF_MONTH_PART, DateRangeRef.WEEK_OF_QUARTER_PART,
         DateRangeRef.MONTH_OF_QUARTER_PART, DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART,
         DateRangeRef.NONE_INTERVAL })
      {
         levelNames.add(DateRangeRef.getName("Date", level));
      }

      for(String part : PARTS) {
         String name = DateComparisonUtil.getDCCalcPartName(part, "Date");

         assertEquals(part, DateComparisonUtil.getDCCalcPart(name), name);
         assertTrue(DateComparisonUtil.isDCCalcPartName(name, part), name);
         assertTrue(DateComparisonUtil.isDCCalcDatePartRef(name), name);
         assertEquals("Date", DateComparisonUtil.getDCCalcPartBaseColumn(name), name);
         assertEquals("Date", DateComparisonUtil.getBaseName(name), name);
         assertEquals(part + "(Date)", DateComparisonUtil.getDCCalcPartDisplayName(name));

         for(String levelName : levelNames) {
            assertNotEquals(levelName, name);
            assertFalse(levelName.contains(name), levelName + " contains " + name);
         }

         // a normal date level name / user name of the old form is not a DC calc name
         assertNull(DateComparisonUtil.getDCCalcPart(part + "(Date)"));
         assertFalse(DateComparisonUtil.isDCCalcDatePartRef(part + "(Date)"));
         assertEquals(part + "(Date)",
                      DateComparisonUtil.getDCCalcPartDisplayName(part + "(Date)"));
      }

      // the two month-of-quarter parts are told apart
      assertEquals(DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER_OF_WEEK,
                   DateComparisonUtil.getDCCalcPart(DateComparisonUtil.getDCCalcPartName(
                      DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER_OF_WEEK, "a(b)")));
      assertEquals("a(b)", DateComparisonUtil.getBaseName(DateComparisonUtil.getDCCalcPartName(
         DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER, "a(b)")));
      // the non-DC fallback is unchanged
      assertEquals("Date", DateComparisonUtil.getBaseName("WeekOfYear(Date)"));
      assertEquals("Date", DateComparisonUtil.getBaseName("Year(Date)"));
   }

   /**
    * The reporter's DC config (Year period, Week To Date, For Each = Week, Granularity = Day)
    * through the real ChartDcProcessor: the registered calc is not named WeekOfYear(Date),
    * and the DC dimensions keep their numeric type and their user-facing label.
    */
   @Test
   void reporterConfigRegistersNonCollidingWeekOfYearCalc() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = process(vs, XConstants.YEAR_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                                 DateComparisonInfo.DAY, XConstants.WEEK_DATE_GROUP);

      CalculateRef[] calcs = vs.getCalcFields("Query1");
      assertNotNull(calcs);
      List<String> calcNames = new ArrayList<>();

      for(CalculateRef calc : calcs) {
         assertTrue(calc.isDcRuntime(), calc.getName());
         calcNames.add(calc.getName());
      }

      String expected = DateComparisonUtil.getDCCalcPartName(
         DateComparisonUtil.DC_CALC_WEEK_OF_YEAR, "Date");
      assertEquals(List.of(expected), calcNames);
      assertFalse(calcNames.contains("WeekOfYear(Date)"));

      XDimensionRef[] temps = info.getDcTempGroups();
      assertNotNull(temps);
      assertEquals(1, temps.length);
      VSDimensionRef temp = (VSDimensionRef) temps[0];
      assertEquals(expected, temp.getFullName());
      assertTrue(DateComparisonUtil.isDCCalcDatePartRef(temp));
      assertEquals("WeekOfYear(Date)", temp.toView());
      assertEquals(XSchema.INTEGER, temp.getDataRef().getDataType());
      assertEquals(XSchema.INTEGER, temp.getDataType());
   }

   /**
    * Year period, Year To Date, Granularity = Day: the visible X dimension itself is the
    * DayOfYear calc. Its type stays numeric (the old DC_CALC_FIELD_PREFIX STRING branch is
    * gone), it is recognized as the DC part column, and its label has no marker.
    */
   @Test
   void visibleDayOfYearCalcDimensionIsNumericAndLabeledWithoutMarker() {
      Viewsheet vs = new Viewsheet();
      VSChartInfo info = process(vs, XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR_TO_DATE,
                                 DateComparisonInfo.DAY, XConstants.YEAR_DATE_GROUP);

      String expected = DateComparisonUtil.getDCCalcPartName(
         DateComparisonUtil.DC_CALC_DAY_OF_YEAR, "Date");
      VSChartDimensionRef part = null;

      for(ChartRef ref : info.getRTXFields()) {
         if(ref instanceof VSChartDimensionRef && expected.equals(ref.getFullName())) {
            part = (VSChartDimensionRef) ref;
         }
      }

      assertNotNull(part, "rt x fields " + Arrays.toString(info.getRTXFields()));
      assertTrue(DateComparisonUtil.isDCCalcDatePartRef(part));
      assertEquals(expected, DateComparisonUtil.getDcPartCol(info));
      assertEquals("DayOfYear(Date)", part.toView());
      assertEquals(XSchema.INTEGER, part.getDataRef().getDataType());
      assertNotEquals(XSchema.STRING, part.getDataType());
      assertTrue(DateComparisonUtil.isShowFullDay(part));
   }

   /** Table/crosstab headers shown to users (and exported) never carry the marker. */
   @Test
   void localizedTableHeaderHasNoMarker() {
      String name = DateComparisonUtil.getDCCalcPartName(
         DateComparisonUtil.DC_CALC_DAY_OF_YEAR, "Date");
      DefaultTableLens lens = new DefaultTableLens(new Object[][] {
         { name, "Year(Date)" },
         { 45, 2021 }
      });
      lens.setHeaderRowCount(1);

      Tool.localizeHeader(lens);

      assertEquals("DayOfYear(Date)", lens.getObject(0, 0));
      assertEquals("Year(Date)", lens.getObject(0, 1));
   }

   /** Merged-part label of a DayOfYear calc part decodes the day (non-week level). */
   @Test
   void dayOfYearMergedPartLabelIsTheDay() {
      MergePartCell cell = mergePartCell(DateComparisonUtil.DC_CALC_DAY_OF_YEAR, 45,
                                         date(2021, Calendar.JANUARY, 1));
      String label = DateComparisonUtil.formatPartMergeCell(
         cell, (Date) cell.getDateGroupValue(), false, null);

      assertEquals(XUtil.getDefaultDateFormat(DateRangeRef.DAY_INTERVAL)
                      .format(date(2021, Calendar.FEBRUARY, 14)), label);
   }

   /** Merged-part label of a MonthOfQuarter calc part decodes the month (non-week level). */
   @Test
   void monthOfQuarterMergedPartLabelIsTheMonth() {
      MergePartCell cell = mergePartCell(DateComparisonUtil.DC_CALC_MONTH_OF_QUARTER, 2,
                                         date(2021, Calendar.APRIL, 1));
      String label = DateComparisonUtil.formatPartMergeCell(
         cell, (Date) cell.getDateGroupValue(), false, null);

      assertEquals(XUtil.getDefaultDateFormat(DateRangeRef.MONTH_INTERVAL)
                      .format(date(2021, Calendar.MAY, 1)), label);
   }

   private static MergePartCell mergePartCell(String part, int value, Date dateGroupValue) {
      String partName = DateComparisonUtil.getDCCalcPartName(part, "date");
      CalculateRef calc = new CalculateRef();
      calc.setDcRuntime(true);
      calc.setName(partName);
      ExpressionRef expr = new ExpressionRef();
      expr.setName(partName);
      expr.setDataType(XSchema.INTEGER);
      calc.setDataRef(expr);

      VSDimensionRef partRef = new VSDimensionRef();
      partRef.setDataRef(calc);
      VSDimensionRef dateGroupRef = new VSDimensionRef();
      dateGroupRef.setDataRef(new AttributeRef("date"));

      DataSetTable base = new DataSetTable(new DefaultDataSet(new Object[][] {
         { partName, "date" },
         { value, dateGroupValue }
      }));
      DCMergeDatePartFilter filter = new DCMergeDatePartFilter(
         base, Collections.emptyList(), partRef, dateGroupRef, null);
      Object cell = filter.getObject(base.getHeaderRowCount(), 0);

      assertInstanceOf(MergePartCell.class, cell);
      return (MergePartCell) cell;
   }

   private static VSChartInfo process(Viewsheet vs, int periodLevel, int intervalLevel,
                                      int granularity, int context)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setContextLevel(context);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      AttributeRef dateAttr = new AttributeRef("Date");
      dateAttr.setDataType(XSchema.DATE);
      VSChartDimensionRef dateDim = new VSChartDimensionRef(dateAttr);
      dateDim.setDateLevel(XConstants.YEAR_DATE_GROUP);
      dateDim.setDataType(XSchema.DATE);

      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("Paid"));
      measure.setColumnValue("Paid");

      VSChartInfo info = new VSChartInfo();
      info.setRTXFields(new ChartRef[] { dateDim });
      info.setRTYFields(new ChartRef[] { measure });

      new ChartDcProcessor(info, dcInfo).process("Query1", vs);
      return info;
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = new GregorianCalendar(year, month, day);
      return cal.getTime();
   }
}
