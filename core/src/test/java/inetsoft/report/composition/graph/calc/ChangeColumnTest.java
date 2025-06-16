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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChangeColumnTest {
   private ChangeColumn changeColumn;
   private VSDataSet vsDataSet;

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

   DefaultTableLens tableLens = new DefaultTableLens(new Object[][]{
      {"name", "id"},
      {"a", 10},
      {"b", 20},
      {"c", 0},
      {"d", null}
   });
}
