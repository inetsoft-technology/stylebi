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
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
         { toDate("2021-01-01"), 4},
         { toDate("2022-01-01"), 3}
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
    * Regression test for Bug #74542: PREVIOUS_YEAR lookup must not exclude PART_DATE_GROUP
    * sibling dimensions from the condition. The sibling (QuarterOfYear) is the position
    * discriminator — without it the lookup always returns the first quarter of the previous
    * year instead of the matching quarter.
    */
   @Test
   void testPreviousYearKeepsPartDateGroupSiblingInCondition() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      valueOfColumn.setDim("Year(Date)");
      // innerDim == ndim triggers the sibling-detection branch
      valueOfColumn.setInnerDim("Year(Date)");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "Year(Date)", "QuarterOfYear(Date)", "id" },
         { toDate("2020-01-01"), 1, 5 },  // 2020 Q1
         { toDate("2020-01-01"), 2, 3 },  // 2020 Q2
         { toDate("2020-01-01"), 3, 4 },  // 2020 Q3
         { toDate("2020-01-01"), 4, 2 },  // 2020 Q4
         { toDate("2021-01-01"), 1, 4 },  // 2021 Q1
         { toDate("2021-01-01"), 2, 7 },  // 2021 Q2 ← test row
         { toDate("2021-01-01"), 3, 8 },  // 2021 Q3
         { toDate("2021-01-01"), 4, 1 },  // 2021 Q4
      });

      VSDimensionRef yearVsRef = mock(VSDimensionRef.class);
      when(yearVsRef.getFullName()).thenReturn("Year(Date)");
      VSDimensionRef quarterVsRef = mock(VSDimensionRef.class);
      when(quarterVsRef.getFullName()).thenReturn("QuarterOfYear(Date)");
      vsDataSet = new VSDataSet(tb, new VSDataRef[] { yearVsRef, quarterVsRef });

      // QuarterOfYear(Date) is a PART_DATE_GROUP sibling of Year(Date)
      XDimensionRef quarterDimRef = mock(XDimensionRef.class);
      when(quarterDimRef.getFullName()).thenReturn("QuarterOfYear(Date)");
      when(quarterDimRef.getDateLevel()).thenReturn(XConstants.QUARTER_OF_YEAR_DATE_GROUP);
      XDimensionRef yearDimRef = mock(XDimensionRef.class);
      when(yearDimRef.getFullName()).thenReturn("Year(Date)");
      when(yearDimRef.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);
      valueOfColumn.setDimensions(Arrays.asList(quarterDimRef, yearDimRef));

      // Row 5 = 2021 Q2 (id=7). Correct previous-year value = 2020 Q2 (id=3).
      // Regression: without the fix, QuarterOfYear is stripped from the condition so the
      // lookup returns the first row of 2020 (Q1, id=5) instead of Q2 (id=3).
      Object result = valueOfColumn.calculate(vsDataSet, 5, false, false);
      assertEquals(3, result);
   }

   /**
    * Regression test for Bug #74582: PREVIOUS on a plain string dimension must use the
    * sorted dataset's router (not the root VSDataSet's router) when data is a DataSetFilter.
    *
    * Original data order: C, A, B (intentionally non-alphabetical).
    * Sorted (chart) order: A, B, C.
    *
    * With the bug (root-dataset router, iteration C → A → B):
    *   getValue("A", -1) = "C" (A is at index 1, previous = C at index 0) → returns id=30 (wrong).
    * With the fix (sorted-dataset router, iteration A → B → C):
    *   getValue("A", -1) = INVALID (A is first) → correctly returns INVALID.
    */
   @Test
   void testChangePreviousWithStringDimension_RouterUsesSortedOrder() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("name");
      valueOfColumn.setInnerDim("name");

      // Original row order: C=30, A=10, B=20 (intentionally NOT alphabetical)
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "name", "id" },
         { "C", 30 },
         { "A", 10 },
         { "B", 20 }
      });

      VSDimensionRef mockDRef = mock(VSDimensionRef.class);
      when(mockDRef.getFullName()).thenReturn("name");
      vsDataSet = new VSDataSet(tb, new VSDataRef[]{ mockDRef });

      // SortedDataSet (forceSort=true) sorts "name" alphabetically: A(id=10), B(id=20), C(id=30)
      SortedDataSet sortedDataSet = new SortedDataSet(vsDataSet, "name");
      sortedDataSet.setForceSort(true);

      // Row 0 in sorted order = "A" — first alphabetically, so no previous → INVALID
      Object result = valueOfColumn.calculate(sortedDataSet, 0, true, false);
      assertEquals(CalcColumn.INVALID, result);

      // Row 1 in sorted order = "B" — previous in sorted order is "A" (id=10)
      result = valueOfColumn.calculate(sortedDataSet, 1, false, false);
      assertEquals(10, result);
   }

   /**
    * Regression test: PREVIOUS on an all-data (__all__) column of a brushed chart must find
    * the value on the BrushDataSet, which is the only dataset in the chain that owns the
    * __all__ columns.
    *
    * When the inner dimension is a PART_DATE_GROUP (e.g. MonthOfYear), the sub-dataset lookup
    * unwraps the filter chain so cross-facet rows can be reached. Unwrapping all the way to
    * the root skips past the BrushDataSet, where __all__ columns are created, so the lookup
    * returns null for every row. ChangeColumn then treats the missing previous value as 0 and
    * plots the raw aggregate instead of the change — the all-data area of a brushed chart
    * suddenly spans the full measure range.
    */
   @Test
   void testPreviousOnBrushAllDataColumnFindsValueOnBrushDataSet() {
      // all data (not brushed)
      DefaultTableLens atb = new DefaultTableLens(new Object[][]{
         { "MonthOfYear(Date)", "id" },
         { 1, 10 },
         { 2, 20 },
         { 3, 40 }
      });
      // brushed subset — same months, smaller values
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "MonthOfYear(Date)", "id" },
         { 1, 5 },
         { 2, 7 },
         { 3, 9 }
      });

      VSDimensionRef monthVsRef = mock(VSDimensionRef.class);
      when(monthVsRef.getFullName()).thenReturn("MonthOfYear(Date)");
      VSDimensionRef monthVsRef2 = mock(VSDimensionRef.class);
      when(monthVsRef2.getFullName()).thenReturn("MonthOfYear(Date)");
      VSDataSet adata = new VSDataSet(atb, new VSDataRef[] { monthVsRef });
      VSDataSet data = new VSDataSet(tb, new VSDataRef[] { monthVsRef2 });

      // rows 0-2 are the brushed rows (id), rows 3-5 the all-data rows (__all__id)
      BrushDataSet brushDataSet = new BrushDataSet(adata, data);

      valueOfColumn = new ValueOfColumn(BrushDataSet.ALL_HEADER_PREFIX + "id",
                                        BrushDataSet.ALL_HEADER_PREFIX + "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("MonthOfYear(Date)");
      valueOfColumn.setInnerDim("MonthOfYear(Date)");

      // MonthOfYear is a PART_DATE_GROUP dim, which triggers the root-dataset unwrap
      XDimensionRef monthDimRef = mock(XDimensionRef.class);
      when(monthDimRef.getFullName()).thenReturn("MonthOfYear(Date)");
      when(monthDimRef.getDateLevel()).thenReturn(XConstants.MONTH_OF_YEAR_DATE_GROUP);
      valueOfColumn.setDimensions(List.of(monthDimRef));

      // Row 4 = all-data month 2. Previous month is 1, whose all-data value is 10.
      // Regression: unwrapping past the BrushDataSet loses __all__id and returns null.
      assertEquals(10, valueOfColumn.calculate(brushDataSet, 4, false, false));

      // Row 3 = all-data month 1, the first month → no previous.
      assertEquals(CalcColumn.INVALID, valueOfColumn.calculate(brushDataSet, 3, true, false));
   }

   /**
    * Regression test for Bug #76039: PREVIOUS navigation on a part-date-group dimension
    * (e.g. HourOfDay) must follow the dimension's display sort even when that sort is
    * value-based, as set by a Top-N/Bottom-N "Sort By Value" ranking. The calc has to agree
    * with the order the values are plotted in and with the order scripts see them in via
    * getData() — so the value that is first in display order has no previous value, even
    * though a numerically-earlier one exists elsewhere in the data.
    *
    * This deliberately reverses the follow-up fix for Bug #75664 ("bug-75664-1"), which gave
    * natural calendar order priority over a value-based sort comparator. The two cannot both
    * hold: for a sort-by-value part-date dimension, calendar order and display order differ.
    * The natural-order fallback still applies when no sort is configured — see
    * {@link #testPreviousOnPartDateGroupWithOthersLabel()}.
    *
    * Row order is 5, 2, 11; the ranking comparator puts the hours in descending order
    * (11, 5, 2), which is neither row order nor calendar order.
    */
   @Test
   void testPreviousOnPartDateGroupFollowsRankingSortOrder() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("HourOfDay(order_time)");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "HourOfDay(order_time)", "id" },
         { 5, 10 },
         { 2, 20 },
         { 11, 30 }
      });

      VSDimensionRef hourRef = mock(VSDimensionRef.class);
      when(hourRef.getFullName()).thenReturn("HourOfDay(order_time)");
      when(hourRef.getDateLevel()).thenReturn(XConstants.HOUR_OF_DAY_DATE_GROUP);
      // Simulates a Top-N ranking's "Sort By Value" comparator, ordering the hours 11, 5, 2
      // rather than in natural hour order 2, 5, 11.
      when(hourRef.getOrder()).thenReturn(XConstants.SORT_VALUE_DESC);
      when(hourRef.createComparator(org.mockito.ArgumentMatchers.any()))
         .thenReturn((a, b) -> Integer.compare((Integer) b, (Integer) a));

      vsDataSet = new VSDataSet(tb, new VSDataRef[] { hourRef });

      // Row 0 = hour 5; previous in display order (11, 5, 2) is hour 11 (id=30).
      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(30, result);

      // Row 1 = hour 2; previous in display order is hour 5 (id=10).
      result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(10, result);

      // Row 2 = hour 11, first in display order → no previous → INVALID.
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(CalcColumn.INVALID, result);
   }

   /**
    * Regression test for Bug #75743: PREVIOUS navigation on a part-date-group dimension that
    * carries a plain label sort (Sort Ascending) must follow that sort — including the
    * position of the null group — instead of an internally-imposed numeric/nulls-last order.
    *
    * With "As time series" off there is no ScaleRouter, so navigation falls back to
    * DataSetRouter. Ascending order puts the null group first (matching both the physical
    * row order and the axis), so the smallest non-null second (1) has the null group as its
    * previous value (id=28) rather than no previous value at all.
    */
   @Test
   void testPreviousOnPartDateGroupFollowsLabelSortWithNullGroup() {
      final String dim = "SecondOfMinute(order_datetime)";
      valueOfColumn = new ValueOfColumn("id", "NthSmallest(id, 1)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim(dim);
      valueOfColumn.setInnerDim(dim);

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { dim, "id" },
         { null, 28 },
         { 1, 13 },
         { 10, 8 },
         { 11, 20 }
      });

      VSDimensionRef secRef = mock(VSDimensionRef.class);
      when(secRef.getFullName()).thenReturn(dim);
      when(secRef.getDateLevel()).thenReturn(XConstants.SECOND_OF_MINUTE_DATE_GROUP);
      when(secRef.getOrder()).thenReturn(XConstants.SORT_ASC);
      when(secRef.createComparator(org.mockito.ArgumentMatchers.any()))
         .thenReturn(Comparator.nullsFirst(
            Comparator.comparingInt(v -> ((Number) v).intValue())));

      vsDataSet = new VSDataSet(tb, new VSDataRef[]{ secRef });

      // Row 1 = second 1. Previous in ascending order is the null group (id=28).
      Object result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(28, result);

      // Row 2 = second 10. Previous is second 1 (id=13).
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(13, result);

      // Row 0 = the null group, first in ascending order → no previous → INVALID.
      result = valueOfColumn.calculate(vsDataSet, 0, true, false);
      assertEquals(CalcColumn.INVALID, result);
   }

   /**
    * Regression test for Bug #75743: the natural calendar order applied to an unsorted
    * part-date-group dimension must tolerate non-numeric group labels. A Top-N ranking with
    * "group others" emits the "Others" string into an otherwise Integer part-date column,
    * which previously threw ClassCastException while building the router.
    */
   @Test
   void testPreviousOnPartDateGroupWithOthersLabel() {
      final String dim = "HourOfDay(order_time)";
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim(dim);
      valueOfColumn.setInnerDim(dim);

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { dim, "id" },
         { 5, 10 },
         { 2, 20 },
         { "Others", 30 }
      });

      VSDimensionRef hourRef = mock(VSDimensionRef.class);
      when(hourRef.getFullName()).thenReturn(dim);
      when(hourRef.getDateLevel()).thenReturn(XConstants.HOUR_OF_DAY_DATE_GROUP);

      vsDataSet = new VSDataSet(tb, new VSDataRef[]{ hourRef });

      // Numeric hours keep calendar order (2, 5) with "Others" sorted after them.
      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(20, result);

      // Hour 2 is the earliest → no previous → INVALID.
      result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(CalcColumn.INVALID, result);

      // "Others" sorts last, so its previous is hour 5 (id=10).
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(10, result);
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

   /**
    * With FIRST type and dimension set, should return the value at the first
    * position in that dimension.
    */
   @Test
   void testFirstWithDimension() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      valueOfColumn.setDim("name");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 100},
         {"b", 200},
         {"c", 300}
      });

      vsDataSet = createVSDataSet(tb, "name");

      // All rows return first dim value = "a" → value 100
      Object result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(100, result);
   }

   /**
    * With LAST type and dimension set, should return the value at the last
    * position in that dimension.
    */
   @Test
   void testLastWithDimension() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.LAST);
      valueOfColumn.setDim("name");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 100},
         {"b", 200},
         {"c", 300}
      });

      vsDataSet = createVSDataSet(tb, "name");

      // All rows return last dim value = "c" → value 300
      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(300, result);
   }

   /**
    * With PREVIOUS type and a dimension, the first row (in first dim position)
    * has no previous → INVALID.
    */
   @Test
   void testPreviousFirstRowInDimIsInvalid() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("name");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 10},
         {"b", 20}
      });

      vsDataSet = createVSDataSet(tb, "name");

      // row 0 ("a"): no previous dim value → INVALID
      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(CalcColumn.INVALID, result);

      // row 1 ("b"): previous dim = "a" → value 10
      result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(10, result);
   }

   /**
    * With NEXT type and a dimension, the last row has no next → INVALID.
    */
   @Test
   void testNextLastRowInDimIsInvalid() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.NEXT);
      valueOfColumn.setDim("name");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 10},
         {"b", 20}
      });

      vsDataSet = createVSDataSet(tb, "name");

      // row 0 ("a"): next dim = "b" → value 20
      Object result = valueOfColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(20, result);

      // row 1 ("b"): no next dim value → INVALID
      result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(CalcColumn.INVALID, result);
   }

   /**
    * Field with null values: PREVIOUS pointing to a null row returns null.
    */
   @Test
   void testPreviousPointingToNullValue() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("name");

      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", null},
         {"b", 50}
      });

      vsDataSet = createVSDataSet(tb, "name");
      // row 1's previous dim value is "a" → look up "a" → value is null
      Object result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertNull(result);
   }

   /**
    * Verify complete() clears cache state without throwing.
    */
   @Test
   void testCompleteResetsState() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      valueOfColumn.setDim("name");
      // calling complete() before any calculation should not throw
      valueOfColumn.complete();
   }

   /**
    * supportSortByValue returns true for date-based change types and false for others.
    */
   @Test
   void testSupportSortByValue() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      assertFalse(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      assertFalse(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      assertTrue(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_QUARTER);
      assertTrue(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_MONTH);
      assertTrue(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_WEEK);
      assertTrue(valueOfColumn.supportSortByValue());

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS_RANGE);
      assertTrue(valueOfColumn.supportSortByValue());
   }

   /**
    * Null context or tuplePair returns null from the crosstab path.
    */
   @Test
   void testCalculateWithNullContextReturnsNull() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      valueOfColumn.setDim("name");

      Object result = valueOfColumn.calculate((CrossTabFilter.CrosstabDataContext) null,
         (CrossTabFilter.PairN) null);
      assertNull(result);
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
