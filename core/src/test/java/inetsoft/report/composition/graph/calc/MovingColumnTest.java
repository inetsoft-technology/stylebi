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
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MovingColumnTest {
   private MovingColumn movingColumn;
   private VSDataSet vsDataSet;

   @Test
   void testCalculateWithVSDataSet() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "sum(id)");

      SumFormula sumFormula = new SumFormula();
      movingColumn.setFormula(sumFormula);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);

      assertEquals(sumFormula, movingColumn.getFormula());
      assertEquals(1, movingColumn.getPreCnt());
      assertEquals(1, movingColumn.getNextCnt());

      assertFalse(movingColumn.isIncludeCurrent());
      assertFalse(movingColumn.isShowNull());

      // check didn't include current value
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(3.0, result);  //null + 3

      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(1.0, result);  //1 + null

      //check include current value
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(true);
      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(4.0, result); // 1 + null + 3
   }

   @Test
   void testCalculateWithBrushData() {
      vsDataSet = createVSDataSet(tableLens, "group");
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);
      movingColumn = new MovingColumn("id", "average(id)");

      AverageFormula averageFormula = new AverageFormula();
      movingColumn.setFormula(averageFormula);
      movingColumn.setPreCnt(0);
      movingColumn.setNextCnt(1);
      movingColumn.setIncludeCurrent(true);

      Object result = movingColumn.calculate(brushDataSet, 0, false, false);
      assertEquals(2.0, result); // (1+3)/2=2

      result = movingColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(3.0, result);  //(null + 3)/1 = 3

      result = movingColumn.calculate(brushDataSet, 2, false, false);
      assertEquals(7.0, result); // (null + 7) /1 = 7

      movingColumn.setInnerDim("name");
      result = movingColumn.calculate(brushDataSet, 2, false, false);
      assertNull(result);  // The last value in group A is null, with no next value, return empty.

      assertEquals("name", movingColumn.getInnerDim());
   }

   /**
    * test row size > 1
    */
   @Test
   void  testCalculateWithCrosstabFilter() {
      movingColumn = new MovingColumn("id", "sum(id)");
      movingColumn.setInnerDim(AbstractCalc.ROW_INNER);
      movingColumn.setFormula(new SumFormula());

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("group", "name"));

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN("a", null);

      Object result = movingColumn.calculate(mockContext, pairN);
      assertEquals(CalcColumn.INVALID, result);   //isCalculateTotal is false

      movingColumn.setCalculateTotal(true);
      when(mockContext.getValue(pairN)).thenReturn(10);
      result = movingColumn.calculate(mockContext, pairN);
      assertEquals(10.0, result);  // check didn't set precnt and nextcnt

      movingColumn.setPreCnt(1);
      when(mockContext.getValue(pairN)).thenReturn(8);
      result = movingColumn.calculate(mockContext, pairN);
      assertEquals(8.0, result); // check set precnt and nextcnt
   }

   /**
    * test row size = 1
    */
   @Test
   void  testCalculateWithCrosstabFilter2() {
      movingColumn = new MovingColumn("id", "sum(id)");
      movingColumn.setInnerDim("name");
      movingColumn.setFormula(new SumFormula());
      movingColumn.setCalculateTotal(true);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);

      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { "a" });
      List<Object> values = Arrays.asList("b", "a", "c");
      CrossTabFilter.PairN pairA = createCrosstabFilterPairN("a", null);

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));
      when(mockContext.getValues(rowTuple, "", 0, true)).thenReturn(values);
      when(mockContext.getValue(
         createCrosstabFilterTuple("b"),
         createCrosstabFilterTuple(null), 0)).thenReturn(1);
      when(mockContext.getValue(pairA)).thenReturn(2);
      when(mockContext.getValue(
         createCrosstabFilterTuple("c"),
         createCrosstabFilterTuple(null), 0)).thenReturn(2);

      Object result = movingColumn.calculate(mockContext, pairA);
      assertEquals(3.0, result);  //1+2=3

      // check include current value
      movingColumn.setIncludeCurrent(true);
      result = movingColumn.calculate(mockContext, pairA);
      assertEquals(5.0, result); //1+2+2=3
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

   private CrossFilter.Tuple createCrosstabFilterTuple(Object value) {
      return new CrossFilter.Tuple(new Object[] { value });
   }

   DefaultTableLens tableLens = new DefaultTableLens(new Object[][]{
      {"group", "name", "id"},
      {"A", "a", 1},
      {"A", "b", 3},
      {"A", "c", null},
      {"B", "d", 7},
      {"B", "e", 1},
      {"B", "f", 9}
   });
}
