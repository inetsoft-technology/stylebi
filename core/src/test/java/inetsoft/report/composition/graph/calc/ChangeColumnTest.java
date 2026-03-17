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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
