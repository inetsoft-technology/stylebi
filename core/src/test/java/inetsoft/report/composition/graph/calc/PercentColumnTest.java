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

import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PercentColumnTest {
   private  PercentColumn percentColumn;
   VSDataSet vsDataSet;

   @Test
   void testCalculateWithVSDataset() {
      vsDataSet = createVSDataSet(tableLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");

      percentColumn.setTotalField("id");
      percentColumn.setDim("name");
      percentColumn.setLevel(PercentCalc.SUB_TOTAL);

      assertTrue(percentColumn.isAsPercent());

      // test null value, treat as null
      Object result = percentColumn.calculate(vsDataSet, 4, false, false);
      assertNull(result);

      //test invalid data, treat as 0
      result = percentColumn.calculate(vsDataSet, 5, false, false);
      assertEquals(0.0,  result);

      //test valid data
      result = percentColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(0.44,  roundToTwoDecimal(result)); // 16/(20+16) = 0.44
   }

   @Test
   void testCalculateWithBrushDataset() {
      vsDataSet = createVSDataSet(tableLens, "name");
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);
      percentColumn = new PercentColumn("id", "sum(id)");

      percentColumn.setTotalField("id");
      percentColumn.setDim("name");
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);

      assertTrue(percentColumn.isAsPercent());
      assertTrue(percentColumn.supportSortByValue());

      Object result = percentColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(0.56,  roundToTwoDecimal(result)); // 20/(20+16) = 0.56

      IntervalDataSet intervalDataSet = new IntervalDataSet(vsDataSet);
      result = percentColumn.calculate(intervalDataSet, 1, false, false);
      assertEquals(0.56,  roundToTwoDecimal(result)); // 20/(20+16) = 0.56
   }

   @Test
   void testCalculateWithCrosstabFilter() {
      percentColumn = new PercentColumn("id", "sum(id)");

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN("a", null);
      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.isFillBlankWithZero()).thenReturn(true);

      Object result = percentColumn.calculate(mockContext, pairN);
      assertNull( result);
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

   /**
    * When the grand total is zero (all values are 0), getTotal returns 0 and result is null.
    */
   @Test
   void testZeroTotalReturnsNull() {
      DefaultTableLens zeroLens = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 0},
         {"b", 0}
      });
      vsDataSet = createVSDataSet(zeroLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setTotalField("id");
      percentColumn.setDim(null);
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);

      // row 0 value=0; total=0 → null
      Object result = percentColumn.calculate(vsDataSet, 0, false, false);
      assertNull(result);
   }

   /**
    * Grand total percent: each row is divided by the sum of all rows.
    */
   @Test
   void testGrandTotalPercent() {
      DefaultTableLens grandTotalLens = new DefaultTableLens(new Object[][]{
         {"name", "id"},
         {"a", 25},
         {"b", 75}
      });
      vsDataSet = createVSDataSet(grandTotalLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setTotalField("id");
      percentColumn.setDim(null);
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);

      // row 0: 25 / 100 = 0.25
      Object result = percentColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(0.25, (double) result, 1e-9);

      // row 1: 75 / 100 = 0.75
      result = percentColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(0.75, (double) result, 1e-9);
   }

   /**
    * Sub-total percent: each row is expressed as a proportion within its sub-group.
    * The sub-group is identified by the dim column value.
    */
   @Test
   void testSubTotalPercent() {
      // "b" group: 20 + 16 = 36; row 1 should be 20/36 ≈ 0.556
      vsDataSet = createVSDataSet(tableLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setTotalField("id");
      percentColumn.setDim("name");
      percentColumn.setLevel(PercentCalc.SUB_TOTAL);

      Object result = percentColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(0.56, roundToTwoDecimal(result));

      result = percentColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(0.44, roundToTwoDecimal(result));
   }

   /**
    * A null field value must return null (not zero or exception).
    */
   @Test
   void testNullFieldValueReturnsNull() {
      vsDataSet = createVSDataSet(tableLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setTotalField("id");
      percentColumn.setDim("name");
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);

      // row 4 is null
      Object result = percentColumn.calculate(vsDataSet, 4, false, false);
      assertNull(result);
   }

   /**
    * A non-numeric (invalid) field value must return ZERO.
    */
   @Test
   void testNonNumericFieldValueReturnsZero() {
      vsDataSet = createVSDataSet(tableLens, "name");
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setTotalField("id");
      percentColumn.setDim("name");
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);

      // row 5: value = false (Boolean, not Number) → ZERO
      Object result = percentColumn.calculate(vsDataSet, 5, false, false);
      assertEquals(0.0, result);
   }

   /**
    * isAsPercent() must always be true for PercentColumn.
    */
   @Test
   void testIsAsPercentAlwaysTrue() {
      percentColumn = new PercentColumn("id", "sum(id)");
      assertTrue(percentColumn.isAsPercent());
   }

   /**
    * isGrandTotal() mirrors the level setting.
    */
   @Test
   void testIsGrandTotal() {
      percentColumn = new PercentColumn("id", "sum(id)");
      percentColumn.setLevel(PercentCalc.GRAND_TOTAL);
      assertTrue(percentColumn.isGrandTotal());

      percentColumn.setLevel(PercentCalc.SUB_TOTAL);
      assertFalse(percentColumn.isGrandTotal());
   }

   private Double roundToTwoDecimal(Object value) {
      return  Math.round((double)value * 100) / 100.0;
   }

   DefaultTableLens tableLens = new DefaultTableLens(new Object[][]{
      {"name", "id"},
      {"a", 10},
      {"b", 20},
      {"b", 16},
      {"c", 0},
      {"d", null},
      {"e", false}
   });
}
