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
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

public class RunningTotalColumnTest {
   private RunningTotalColumn runningTotalColumn;
   VSDataSet vsDataSet;

   /**
    * use date type dataset to calculate on reset column
    */
   @Test
   void tesCalculateWithVSDataSet() {
      DefaultTableLens tb2 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2021-01-01"), 1},
         { toDate("2021-02-01"), 3},
         { toDate("2021-03-01"), null},
         { toDate("2022-04-01"), 6},
         { toDate("2022-05-01"), 4},
         { toDate("2022-07-01"), 0}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.YEAR);

      vsDataSet = createVSDataSet(tb2, new String[] { "date" });

      Object result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(3, result);  // if formula is null, return value 3

      SumFormula sumFormula = new SumFormula();
      runningTotalColumn.setFormula(sumFormula);

      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(4.0, result); // 1+ 3 = 4

      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(4.0, result); // 1 +3 + null = 4, null treat as 0

      result = runningTotalColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(6.0, result); // reset by year, 2022 only have value 6, so 6 = 6

      result = runningTotalColumn.calculate(vsDataSet, 4, false, false);
      assertEquals(10.0, result); //reset by year, 2022  have value 6, 4, so 6 + 4 = 10

      assertEquals(sumFormula, runningTotalColumn.getFormula());
      assertEquals(RunningTotalColumn.YEAR, runningTotalColumn.getResetLevel());
   }

   /**
    * use brushDataSet to check calculate on break by column.
    */
   @Test
   void tesCalculateWithBrushData() {
      DefaultTableLens tableLens1 = new DefaultTableLens(new Object[][]{
         {"group", "name", "id"},
         {"A", "a", 1},
         {"A", "b", 3},
         {"A", "c", null},
         {"B", "d", 7},
         {"B", "e", 1},
         {"B", "f", 9}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setFormula(new SumFormula());
      runningTotalColumn.setInnerDim("name");

      vsDataSet = createVSDataSet(tableLens1, new String[] {"group", "name"});
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);

      //  unset break by column, same result with set  runningTotalColumn.setBreakBy("group");
      Object result = runningTotalColumn.calculate(brushDataSet, 2, false, false);
      assertEquals(4.0, result); //A: 1+3 = 4

      result = runningTotalColumn.calculate(brushDataSet, 3, false, false);
      assertEquals(7.0, result); //B: 7 = 7

      result = runningTotalColumn.calculate(brushDataSet, 4, false, false);
      assertEquals(8.0, result); //B: 7+1 =8

      runningTotalColumn.setBreakBy("name");
      result = runningTotalColumn.calculate(brushDataSet, 2, false, false);
      assertNull(result); // row-2 value is null

      result = runningTotalColumn.calculate(brushDataSet, 3, false, false);
      assertEquals(7.0, result); //row-2 value is 7

      assertEquals("name", runningTotalColumn.getBreakBy());
   }

   @Test
   void testCalculateWithInvalid() {
      DefaultTableLens tdata2 = new DefaultTableLens ( new Object[][]{
         { "date", "__all__"},
         { toDate("2021-01-01"), 1},
         { toDate("2021-03-01"), null},
         { toDate("2021-04-01"), 6},
         });
      VSDataSet dataSet2 = createVSDataSet(tdata2, new String[] {"date", "__all__"});
      BrushDataSet brushDataSet2= new BrushDataSet(dataSet2, dataSet2);

      runningTotalColumn = new RunningTotalColumn("__all__", "sum(__all__)");
      runningTotalColumn.setInnerDim("date");

      // if row <0, return null
      runningTotalColumn.setFormula(new SumFormula());
      Object result = runningTotalColumn.calculate(brushDataSet2, 2, false, false);
      assertNull(result);
   }

   /**
    * use crosstab filter to check calculate on break by filed.
    */
   @Test
   void testCalculateWithCrosstabFilter() {
      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setFormula(new SumFormula());
      runningTotalColumn.setInnerDim("name");
      runningTotalColumn.setBreakBy(AbstractCalc.ROW_INNER);

      CrossFilter.Tuple rowTuple = createCrosstabFilterTuple("b");
      List<Object> values = Arrays.asList("a", "b", "c");
      CrossTabFilter.PairN pairB = createCrosstabFilterPairN("b", null);

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));
      when(mockContext.getValues(rowTuple, "", 0, true)).thenReturn(values);

      when(mockContext.getValue(createCrosstabFilterTuple("a"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(1);
      when(mockContext.getValue(pairB)).thenReturn(3);
      when(mockContext.getValue(createCrosstabFilterTuple("b"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(3);
      when(mockContext.getValue(createCrosstabFilterTuple("c"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(4);

      Object result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(4.0, result);  // 1 + 3 = 4

      //check break by self.
      runningTotalColumn.setBreakBy("name");
      when(mockContext.getRowTupleByIndex(1)).thenReturn(rowTuple);
      when(mockContext.getRowTupleByIndex(0)).thenReturn(createCrosstabFilterTuple("a"));
      when(mockContext.getRowTupleIndex(rowTuple)).thenReturn(1);
      when(mockContext.getRowTupleIndex(createCrosstabFilterTuple("a"))).thenReturn(0);
      result = runningTotalColumn.calculate(mockContext, pairB);

      assertEquals(3.0, result);  // tuple-b value is 3
   }

   @Test
   void testCalculateWithCrosstabFilterOfInvalid() {
      CrossTabFilter.PairN pairB = createCrosstabFilterPairN("b", null);
      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");

      Object result = runningTotalColumn.calculate(mockContext, pairB);
      assertNull(result); // no formula, return null

      runningTotalColumn.setFormula(new SumFormula());
      result = runningTotalColumn.calculate(mockContext, pairB);
      assertNull(result); // no dim, return null

      runningTotalColumn.setInnerDim("name");
      runningTotalColumn.setBreakBy(AbstractCalc.ROW_INNER);
      when(mockContext.isGrandTotalTuple(createCrosstabFilterTuple("b"))).thenReturn(true);

      result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(CalcColumn.INVALID, result); // grand total is true, total row is false, return invalid

      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name", "group_total"));
      when(mockContext.getValue(pairB)).thenReturn(3);
      result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(3, result); // tuple.size() - 1 < dimIdx, return current value
   }

   private VSDataSet createVSDataSet(DefaultTableLens tableLens, String[] dNames) {
      VSDimensionRef[] mockDRefs = Arrays.stream(dNames)
         .map(name -> {
            VSDimensionRef mockDRef = mock(VSDimensionRef.class);
            when(mockDRef.getFullName()).thenReturn(name);
            return mockDRef;
         })
         .toArray(VSDimensionRef[]::new);

      vsDataSet = new VSDataSet(tableLens,  mockDRefs );

      return vsDataSet;
   }

   private java.util.Date toDate(String localDate) {
      ZoneId zoneId = ZoneId.systemDefault();
      return java.util.Date.from(LocalDate.parse(localDate)
                                    .atStartOfDay(zoneId)
                                    .toInstant());
   }

   private CrossTabFilter.PairN createCrosstabFilterPairN(Object rowValue, Object colValue) {
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { rowValue });
      CrossFilter.Tuple colTuple = new CrossFilter.Tuple(new Object[] { colValue });
      CrossTabFilter.PairN pairN = new CrossTabFilter.PairN(rowTuple, colTuple, 0);

      return pairN;
   }

   private CrossFilter.Tuple createCrosstabFilterTuple(Object value) {
      return new CrossFilter.Tuple(new Object[] { value });
   }
}
