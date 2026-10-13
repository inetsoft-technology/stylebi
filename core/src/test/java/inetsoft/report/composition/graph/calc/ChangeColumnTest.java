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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ChangeColumnTest {
   private ChangeColumn changeColumn;
   private VSDataSet vsDataSet;
   private DefaultTableLens tableLens;

   @BeforeEach
   void setUp() {
      tableLens = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 10},
         {"b", 20},
         {"c", 0},
         {"d", null}
      });
   }

   /**
    * check calculate with vsdataset,  change  of previous column
    */
   @Test
   void testCalculateWithVSDataSet() {
      changeColumn = new ChangeColumn("id", "sum(id)");

      vsDataSet = createVSDataSet(tableLens, "name");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS);

      assertFalse(changeColumn.isAsPercent());

      Object result = changeColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(CalcColumn.INVALID, result); // 10-null = null

       result = changeColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(10.0, result); // 20 - 10 = 10

      result = changeColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(-20.0, result); // 0 - 20 = -20

      result = changeColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(0.0, result); // value is null, return 0.0
      assertEquals(0, changeColumn.getMissingValue());
   }

   /**
    * check calculate with brush dataset, % change  of first column
    */
   @Test
   void testCalculateWithBrushDataSet() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(tableLens, "name");
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);

      changeColumn.setAsPercent(true);
      changeColumn.setChangeType(ValueOfCalc.FIRST);
      Object result = changeColumn.calculate(brushDataSet, 0, false, false);
      assertEquals(0.0, result); // (10-10)/10=0.0

      result = changeColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(1.0, result); // (20-10)/10=1.0

      result = changeColumn.calculate(brushDataSet, 2, false, false);
      assertEquals(-1.0, result); // (0-10)/10=-1

      result = changeColumn.calculate(brushDataSet, 3, false, false);
      assertEquals(-1.0, result);  // value is null, return 0, so (0-10)/10=1.0
   }

   @Test
   void testCalculateWithCrosstabFilter() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      changeColumn.setDim("date");
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);

      List<Object> values = Arrays.asList(
         toDate("2020-01-01"),
         toDate("2021-01-01"),
         toDate("2023-01-01"));

      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { toDate("2021-01-01") });
      CrossTabFilter.PairN nPairN = createCrosstabFilterPairN(toDate("2020-01-01"), null);

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);

      when(mockContext.getRowHeaders()).thenReturn(List.of(new String[]{ "date", "sum(id)"}));
      when(mockContext.getValues(rowTuple, "", 0,true)).thenReturn(values);
      when(mockContext.isPairExist(nPairN)).thenReturn(true);
      when(mockContext.getValue(nPairN)).thenReturn(13);

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN(toDate("2021-01-01"), null);
      Object result = changeColumn.calculate(mockContext, pairN);

      assertEquals(-13.0, result);
   }

   /**
    * Regression test: change-from-previous on the all-data (__all__) column of a brushed
    * chart must produce the change, not the raw aggregate.
    *
    * The __all__ columns exist only on the BrushDataSet. When the previous-value lookup
    * unwrapped the filter chain past it (done for PART_DATE_GROUP inner dims so cross-facet
    * rows can be reached), the lookup returned null for every row; a null previous value is
    * treated as 0, so the change collapsed to the raw value and the all-data area of a
    * brushed chart grew to span the full measure range.
    */
   @Test
   void testChangePreviousOnBrushAllDataColumnReturnsChangeNotRawValue() {
      DefaultTableLens atb = new DefaultTableLens(new Object[][]{
         { "MonthOfYear(Date)", "id" },
         { 1, 10 },
         { 2, 20 },
         { 3, 40 }
      });
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

      // rows 0-2 are the brushed rows (id), rows 3-5 the all-data rows (__all__id)
      BrushDataSet brushDataSet = new BrushDataSet(
         new VSDataSet(atb, new VSDataRef[] { monthVsRef }),
         new VSDataSet(tb, new VSDataRef[] { monthVsRef2 }));

      changeColumn = new ChangeColumn(BrushDataSet.ALL_HEADER_PREFIX + "id",
                                      BrushDataSet.ALL_HEADER_PREFIX + "sum(id)");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS);
      changeColumn.setDim("MonthOfYear(Date)");
      changeColumn.setInnerDim("MonthOfYear(Date)");

      XDimensionRef monthDimRef = mock(XDimensionRef.class);
      when(monthDimRef.getFullName()).thenReturn("MonthOfYear(Date)");
      when(monthDimRef.getDateLevel()).thenReturn(XConstants.MONTH_OF_YEAR_DATE_GROUP);
      changeColumn.setDimensions(List.of(monthDimRef));

      // row 4 = all-data month 2: 20 - 10 = 10 (regression returned the raw 20)
      assertEquals(10.0, changeColumn.calculate(brushDataSet, 4, false, false));

      // row 5 = all-data month 3: 40 - 20 = 20 (regression returned the raw 40)
      assertEquals(20.0, changeColumn.calculate(brushDataSet, 5, false, false));
   }

   /**
    * Regression test for Bug #78448: change-from-previous-year on the all-data (__all__) column
    * of a brushed chart must find the previous year in the all-data rows.
    *
    * The brush excludes the earliest year (e.g. the extra prior period of a date comparison
    * chart). The "first period" guard took its minimum date from the root dataset, which below
    * a BrushDataSet is the brushed data only, so every all-data row of the brushed year became
    * INVALID and its point disappeared.
    */
   @Test
   void testChangePreviousYearOnBrushAllDataColumnBeforeBrushedRange() {
      DefaultTableLens atb = new DefaultTableLens(new Object[][]{
         { "Year(Date)", "id" },
         { toDate("2020-01-01"), 10 },
         { toDate("2021-01-01"), 30 },
         { toDate("2022-01-01"), 60 }
      });
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "Year(Date)", "id" },
         { toDate("2021-01-01"), 30 }
      });

      VSDimensionRef yearVsRef = mock(VSDimensionRef.class);
      when(yearVsRef.getFullName()).thenReturn("Year(Date)");
      VSDimensionRef yearVsRef2 = mock(VSDimensionRef.class);
      when(yearVsRef2.getFullName()).thenReturn("Year(Date)");

      // row 0 is the brushed row (id), rows 1-3 the all-data rows (__all__id)
      BrushDataSet brushDataSet = new BrushDataSet(
         new VSDataSet(atb, new VSDataRef[] { yearVsRef }),
         new VSDataSet(tb, new VSDataRef[] { yearVsRef2 }));

      changeColumn = new ChangeColumn(BrushDataSet.ALL_HEADER_PREFIX + "id",
                                      BrushDataSet.ALL_HEADER_PREFIX + "sum(id)");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      changeColumn.setDim("Year(Date)");
      changeColumn.setInnerDim("Year(Date)");

      // row 1 = all-data 2020: the first year has no previous year
      assertEquals(CalcColumn.INVALID, changeColumn.calculate(brushDataSet, 1, false, false));
      // row 2 = all-data 2021: 30 - 10 = 20 (regression returned INVALID)
      assertEquals(20.0, changeColumn.calculate(brushDataSet, 2, false, false));
      // row 3 = all-data 2022: 60 - 30 = 30
      assertEquals(30.0, changeColumn.calculate(brushDataSet, 3, false, false));

      // the brushed data has no 2020, so the brushed 2021 change stays invalid
      ChangeColumn brushColumn = new ChangeColumn("id", "sum(id)");
      brushColumn.setAsPercent(false);
      brushColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      brushColumn.setDim("Year(Date)");
      brushColumn.setInnerDim("Year(Date)");

      assertEquals(CalcColumn.INVALID, brushColumn.calculate(brushDataSet, 0, false, false));
   }

   /**
    * Regression test for Bug #78448 in the reported chart's shape: QuarterOfYear x Year with
    * QuarterOfYear as the facet (outer) dimension, brushed to 2021. The calc runs on a facet
    * cell filtered from the BrushDataSet, so the "first period" guard must look through the
    * facet filter (#76890) and stop at the BrushDataSet, where the all-data rows still hold 2020.
    */
   @Test
   void testChangePreviousYearOnBrushAllDataColumnInFacetCell() {
      DefaultTableLens atb = new DefaultTableLens(new Object[][]{
         { "Year(Date)", "QuarterOfYear(Date)", "id" },
         { toDate("2020-01-01"), 1, 10 },
         { toDate("2020-01-01"), 2, 15 },
         { toDate("2021-01-01"), 1, 30 },
         { toDate("2021-01-01"), 2, 20 },
         { toDate("2022-01-01"), 1, 60 },
         { toDate("2022-01-01"), 2, 50 }
      });
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { "Year(Date)", "QuarterOfYear(Date)", "id" },
         { toDate("2021-01-01"), 1, 30 },
         { toDate("2021-01-01"), 2, 20 }
      });

      BrushDataSet brushDataSet = new BrushDataSet(
         new VSDataSet(atb, createYearQuarterRefs()), new VSDataSet(tb, createYearQuarterRefs()));

      XDimensionRef quarterDimRef = mock(XDimensionRef.class);
      when(quarterDimRef.getFullName()).thenReturn("QuarterOfYear(Date)");
      when(quarterDimRef.getDateLevel()).thenReturn(XConstants.QUARTER_OF_YEAR_DATE_GROUP);
      XDimensionRef yearDimRef = mock(XDimensionRef.class);
      when(yearDimRef.getFullName()).thenReturn("Year(Date)");
      when(yearDimRef.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      changeColumn = new ChangeColumn(BrushDataSet.ALL_HEADER_PREFIX + "id",
                                      BrushDataSet.ALL_HEADER_PREFIX + "sum(id)");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      changeColumn.setDim("Year(Date)");
      changeColumn.setInnerDim("Year(Date)");
      changeColumn.setDimensions(List.of(quarterDimRef, yearDimRef));

      // facet cell Q2: the brushed 2021 row, then the all-data 2020, 2021 and 2022 rows
      DataSetIndex index = new DataSetIndex(brushDataSet, Set.of("QuarterOfYear(Date)"), true);
      DataSet facetCellQ2 = index.createSubDataSet(Map.of("QuarterOfYear(Date)", 2), false);
      assertEquals(4, facetCellQ2.getRowCount());

      // all-data 2020 Q2: the first year has no previous year
      assertEquals(CalcColumn.INVALID, changeColumn.calculate(facetCellQ2, 1, false, false));
      // all-data 2021 Q2: 20 - 15 = 5 (regression returned INVALID)
      assertEquals(5.0, changeColumn.calculate(facetCellQ2, 2, false, false));
      // all-data 2022 Q2: 50 - 20 = 30
      assertEquals(30.0, changeColumn.calculate(facetCellQ2, 3, false, false));

      // the brushed data has no 2020, so the brushed 2021 change stays invalid
      ChangeColumn brushColumn = new ChangeColumn("id", "sum(id)");
      brushColumn.setAsPercent(false);
      brushColumn.setChangeType(ValueOfCalc.PREVIOUS_YEAR);
      brushColumn.setDim("Year(Date)");
      brushColumn.setInnerDim("Year(Date)");
      brushColumn.setDimensions(List.of(quarterDimRef, yearDimRef));

      assertEquals(CalcColumn.INVALID, brushColumn.calculate(facetCellQ2, 0, false, false));
   }

   private VSDataRef[] createYearQuarterRefs() {
      VSDimensionRef yearVsRef = mock(VSDimensionRef.class);
      when(yearVsRef.getFullName()).thenReturn("Year(Date)");
      VSDimensionRef quarterVsRef = mock(VSDimensionRef.class);
      when(quarterVsRef.getFullName()).thenReturn("QuarterOfYear(Date)");

      return new VSDataRef[] { yearVsRef, quarterVsRef };
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

   /**
    * Verify that absolute change (not percent) is computed correctly.
    */
   @Test
   void testAbsoluteChange() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(tableLens, "name");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.FIRST);

      // row 0: value=10, first value=10 → 10 - 10 = 0
      Object result = changeColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(0.0, result);

      // row 1: value=20, first value=10 → 20 - 10 = 10
      result = changeColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(10.0, result);

      // row 2: value=0, first value=10 → 0 - 10 = -10
      result = changeColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(-10.0, result);
   }

   /**
    * When denominator is zero and asPercent=true, result must be null.
    */
   @Test
   void testZeroDenominatorWithPercent() {
      // Build a dataset where first value is 0 so percent change is undefined.
      DefaultTableLens zeroFirstLens = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 0},
         {"b", 50}
      });
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(zeroFirstLens, "name");
      changeColumn.setAsPercent(true);
      changeColumn.setChangeType(ValueOfCalc.FIRST);

      // first value denominator = 0 → null
      Object result = changeColumn.calculate(vsDataSet, 1, false, false);
      assertNull(result);
   }

   /**
    * Percent change calculation: (new - old) / old.
    */
   @Test
   void testPercentChangeFromFirst() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(tableLens, "name");
      changeColumn.setAsPercent(true);
      changeColumn.setChangeType(ValueOfCalc.FIRST);

      // row 0: (10-10)/10 = 0
      Object result = changeColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(0.0, result);

      // row 1: (20-10)/10 = 1.0
      result = changeColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(1.0, result);

      // row 2: (0-10)/10 = -1.0
      result = changeColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(-1.0, result);
   }

   /**
    * When the current value is null and missingAsZero is true (default),
    * the column should treat null as 0 for the change calculation.
    */
   @Test
   void testNullCurrentValueWithMissingAsZero() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(tableLens, "name");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS);

      // row 3 is null; previous row 2 is 0. missing-as-zero → 0 - 0 = 0
      Object result = changeColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(0.0, result);
   }

   /**
    * isAsPercent getter reflects the setter correctly.
    */
   @Test
   void testIsAsPercentProperty() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      assertFalse(changeColumn.isAsPercent());
      changeColumn.setAsPercent(true);
      assertTrue(changeColumn.isAsPercent());
   }

   /**
    * With PREVIOUS direction on the first row (no prior value), result
    * should be INVALID because there is no previous row.
    */
   @Test
   void testPreviousDirectionAtFirstRow() {
      changeColumn = new ChangeColumn("id", "sum(id)");
      vsDataSet = createVSDataSet(tableLens, "name");
      changeColumn.setAsPercent(false);
      changeColumn.setChangeType(ValueOfCalc.PREVIOUS);

      // row 0: no previous → INVALID from ValueOfColumn, ChangeColumn propagates INVALID
      Object result = changeColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(CalcColumn.INVALID, result);
   }

}
