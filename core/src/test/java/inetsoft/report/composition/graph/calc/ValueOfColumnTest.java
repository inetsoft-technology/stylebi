/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValueOfColumnTest {
   private  ValueOfColumn valueOfColumn;
   private VSDataSet vsDataSet;

   @Test
   void testCalculateWithDataSet() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      // brush is false, return row value
      valueOfColumn.setChangeType(ValueOfCalc.LAST);
      valueOfColumn.setDim("name");

      DefaultTableLens tb1 = new DefaultTableLens ( new Object[][]{
         { "name", "id"},
         { "a", 4},
         { "b", 3},
         { "b", 1},
         { "c", 2}
      });

      vsDataSet = createVSDataSet(tb1, "name");
      Object result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(2, result);

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(4, result);

      valueOfColumn.setChangeType(ValueOfCalc.NEXT);
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(2, result);

      //check brush base dataset, return the frist value, note
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);
      result = valueOfColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(4, result);
   }

   @Test
   void testCalculateWithDataSetOfPreviousYear() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      valueOfColumn.setDim("date");
      DefaultTableLens tb = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { new Date(2021-1900, 0, 0), 4},
         { new Date(2022-1900, 0, 0), 3}
      });

      vsDataSet = createVSDataSet(tb, "date");

      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(CalcColumn.INVALID, result);  // 4.9E-324 is the default value for Date, which is null. the previous year of 2021-01-01 is null

      result = valueOfColumn.calculate(vsDataSet, 1, true, false);
      assertEquals(4, result);  // the previous year of 2022-01-01 is 2021-01-01, which value is 4
   }

   /**
    * check previous range of a dc values
    */
   @Test
   void testCalculateWithDataSetOfPreviousRange() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_RANGE);
      valueOfColumn.setDim("dateRange");

      DefaultTableLens tb3 = new DefaultTableLens ( new Object[][]{
         { "dateRange", "id"},
         { "2019:2020", 4},
         { "2021:2022", 3},
         { "2023:2024", 1}
      });

      vsDataSet = createVSDataSet(tb3, "dateRange");

      Object result = valueOfColumn.calculate(vsDataSet, 1, true, false);
      assertEquals(CalcColumn.INVALID, result);  // dcPeriods is null, return INVALID
      List<DatePeriod> datePeriods = Arrays.asList(
         new DatePeriod(toDate("2019-01-01"), toDate("2020-01-01")),
         new DatePeriod(toDate("2021-01-01"), toDate("2022-01-01")),
         new DatePeriod(toDate("2023-01-01"), toDate("2024-01-01"))
      );

      valueOfColumn.setDcPeriods(datePeriods);
      result = valueOfColumn.calculate(vsDataSet, 1, true, false);

      assertEquals(4, result);  //check dcPeriods not null
   }

   /**
    * check calculate with CrossTabFilter.CrosstabDataContext
    */
   @Test
   void testCalculateWithCrosstabCellOfStaticValue() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(List.of(new String[]{"name", "sum(id)"}));

      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { "a" });
      CrossTabFilter.PairN pairN = createCrosstabFilterPairN("a", null);

      valueOfColumn.setDim("name");
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      List<Object> names = Arrays.asList("a", "b", "c");
      when(mockContext.getValues(rowTuple, "", 0,true)).thenReturn(names);

      Object result = valueOfColumn.calculate(mockContext, pairN);
      assertEquals(0.0, result);
   }

   /**
    * check calculate with crosstabfilter on PREVIOUS_MONTH
    */
   @Test
   void testCalculateWithCrosstabFilterOfDynamicValue() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(List.of(new String[]{"date1", "sum(id)"}));

      valueOfColumn.setDim("date1");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_MONTH);

      List<Object> values = Arrays.asList(
         toDate("2020-12-01"),
         toDate("2021-01-01"),
         toDate("2022-01-01"));

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN(toDate("2021-01-01") , null);
      CrossTabFilter.PairN npairN = createCrosstabFilterPairN(toDate("2020-12-01"), null);
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { toDate("2021-01-01") });

      when(mockContext.getValues(rowTuple, "", 0,true)).thenReturn(values);
      when(mockContext.isPairExist(npairN)).thenReturn(true);
      when(mockContext.getValue(npairN)).thenReturn(2);

      Object result = valueOfColumn.calculate(mockContext, pairN);
      assertEquals(2, result);
   }

   @Test
   void testCalculateWithCrosstabFilterOfDC() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(List.of(new String[]{"date2", "sum(id)"}));

      valueOfColumn.setDim("date2");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_RANGE);

      List<DatePeriod> datePeriods = Arrays.asList(
         new DatePeriod(toDate("2020-01-01"), toDate("2021-01-01")),
         new DatePeriod(toDate("2022-01-01"), toDate("2023-01-01")),
         new DatePeriod(toDate("2024-01-01"), toDate("2025-01-01"))
      );
      valueOfColumn.setDcPeriods(datePeriods);

      List<Object> values = Arrays.asList(
         "2020:2021", "2022:2023", "2024:2025");
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] {  "2022:2023" });

      CrossTabFilter.PairN pairN2 = createCrosstabFilterPairN("2020:2021", null);
      when(mockContext.getValues(rowTuple, "", 0, true)).thenReturn(values);
      when(mockContext.isPairExist(pairN2)).thenReturn(true);
      when(mockContext.getValue(pairN2)).thenReturn(12);

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN("2022:2023", null);
      Object result = valueOfColumn.calculate(mockContext, pairN);

      assertEquals(12, result);
   }

   /**
    * check some basic functions
    */
   @Test
   void testSetGetFunctions() {
      valueOfColumn = new ValueOfColumn("col1", "col1");

      assertEquals(ValueOfCalc.FIRST, valueOfColumn.getChangeType());
      valueOfColumn.setChangeType(ValueOfCalc.LAST);
      assertEquals(ValueOfCalc.LAST, valueOfColumn.getChangeType());

      valueOfColumn.setDim("dim1");
      assertEquals("dim1", valueOfColumn.getDim());

      VSDimensionRef vsDimensionRef = mock(VSDimensionRef.class);
      when(vsDimensionRef.isDcRange()).thenReturn(true);
      when(vsDimensionRef.getName()).thenReturn("date2");
      valueOfColumn.setDateComparisonDims(Arrays.asList(vsDimensionRef));
      assertEquals("date2", valueOfColumn.getDCRangePeriodDim().getName());

      valueOfColumn.setDcTempGroups(Arrays.asList(vsDimensionRef));
      assertEquals("date2", valueOfColumn.getDcTempGroups().getFirst().getName());

      valueOfColumn.setFirstWeek(true);
   }

   /**
    * use dataset to check week groups, check previous week
    */
   @Test
   void testCalculateWithDatasetOfDateWeekGroup1() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_WEEK);
      valueOfColumn.setDim("date");

      DefaultTableLens tb1 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2025-06-03"), 4},
         { toDate("2025-06-10"), 3}
      });

      vsDataSet = createVSDataSet(tb1, "date");
      Object result = valueOfColumn.calculate(vsDataSet, 1, false, false);

      assertEquals(4, result);
   }

   @Test
   void testCalculateWithDatesetOnAllLevel() {
      Object result;

      //check PREVIOUS_WEEK
      DefaultTableLens tb1 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2024-06-11"), 2},
         { toDate("2025-06-10"), 7}
      });
      result = testCalculateWithDatasetOfWeekGroup(tb1, ValueOfCalc.PREVIOUS_YEAR, false);
      assertEquals(2, result);

      //check PREVIOUS_QUARTER
      DefaultTableLens tb2 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2025-04-27"), 8},
         { toDate("2025-08-01"), 3}
      });
      result = testCalculateWithDatasetOfWeekGroup(tb2, ValueOfCalc.PREVIOUS_QUARTER, true);
      assertEquals(8, result);

      //check PREVIOUS_MONTH
      DefaultTableLens tb3 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2025-05-04"), 6},  // previous:05-04
         { toDate("2025-06-01"), 3}
      });
      result = testCalculateWithDatasetOfWeekGroup(tb3, ValueOfCalc.PREVIOUS_MONTH, true);
      assertEquals(6, result);
   }

   private Object testCalculateWithDatasetOfWeekGroup(DefaultTableLens tableLens, int level, Boolean isFirstWeek) {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setDim("date");

      vsDataSet = createVSDataSet(tableLens, "date");
      valueOfColumn.setChangeType(level);

      VSDimensionRef vsDimensionRef = mock(VSDimensionRef.class);
      when(vsDimensionRef.getName()).thenReturn("date");
      when(vsDimensionRef.getDateLevel()).thenReturn(XConstants.WEEK_DATE_GROUP);

      valueOfColumn.setDateComparisonDims(Arrays.asList(vsDimensionRef));
      valueOfColumn.setFirstWeek(isFirstWeek);

      return valueOfColumn.calculate(vsDataSet, 1, false, false);
   }

   private VSDataSet createVSDataSet(DefaultTableLens tableLens, String name) {
      VSDimensionRef mockDRef = mock(VSDimensionRef.class);
      when(mockDRef.getFullName()).thenReturn(name);
      vsDataSet = new VSDataSet(tableLens, new VSDataRef[] { mockDRef });

      return vsDataSet;
   }

   private CrossTabFilter.PairN createCrosstabFilterPairN(Object rowValue, Object colValue) {
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { rowValue });
      CrossFilter.Tuple colTuple = new CrossFilter.Tuple(new Object[] { colValue });
      CrossTabFilter.PairN pairN = new CrossTabFilter.PairN(rowTuple, colTuple, 0);

      return pairN;
   }
   private java.util.Date toDate(String localDate) {
      ZoneId zoneId = ZoneId.systemDefault();
      return java.util.Date.from(LocalDate.parse(localDate)
                                    .atStartOfDay(zoneId)
                                    .toInstant());
   }
}
