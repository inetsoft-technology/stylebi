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

import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValueOfColumnTest {
   private  ValueOfColumn valueOfColumn;
   private VSDataSet vsDataSet;

   @Test
   void testSetGet() {
      valueOfColumn = new ValueOfColumn("col1", "col1");

      assertEquals(ValueOfCalc.FIRST, valueOfColumn.getChangeType());
      valueOfColumn.setChangeType(ValueOfCalc.LAST);
      assertEquals(ValueOfCalc.LAST, valueOfColumn.getChangeType());

      valueOfColumn.setDim("dim1");
      assertEquals("dim1", valueOfColumn.getDim());
   }

   @Test
   void testCalculateWithDataSet() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");
      // brush is false, return row value
      valueOfColumn.setChangeType(ValueOfCalc.LAST);
      valueOfColumn.setDim("name");
      vsDataSet = createVSDataSet();
      Object result = valueOfColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(2, result);

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      result = valueOfColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(4, result);

      //check brush base dataset, return the frist value, note
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);
      result = valueOfColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(4, result);
   }

   //check return null, invalid, and BrushDataSet.ALL_HEADER_PREFIX, todo

   /**
    * check calculate with CrossTabFilter.CrosstabDataContext, unfinished
    */
   @Test
   void testCalculateWithCrosstabCell() {
      valueOfColumn = new ValueOfColumn("id", "sum(id)");

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(List.of(new String[]{"name", "sum(id)"}));

      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { "a" });
      CrossFilter.Tuple colTuple = new CrossFilter.Tuple(new Object[] { null });
      CrossTabFilter.PairN pairN = new CrossTabFilter.PairN(rowTuple, colTuple, 0);

      valueOfColumn.setDim("name");
      valueOfColumn.setChangeType(ValueOfCalc.FIRST);
      List<Object> names = Arrays.asList("a", "b", "c");
      when(mockContext.getValues(rowTuple, "", 0,true)).thenReturn(names);

      Object result = valueOfColumn.calculate(mockContext, pairN);
      assertEquals(0.0, result);

      valueOfColumn.setChangeType(ValueOfCalc.PREVIOUS);
      result = valueOfColumn.calculate(mockContext, pairN);
      assertEquals(4.9E-324, result);
   }

   private VSDataSet createVSDataSet() {
      VSDimensionRef mockDRef = mock(VSDimensionRef.class);
      when(mockDRef.getFullName()).thenReturn("name");
      vsDataSet = new VSDataSet(tb1, new VSDataRef[] { mockDRef });

      return vsDataSet;
   }

   DefaultTableLens tb1 = new DefaultTableLens ( new Object[][]{
      { "name", "id", "num" },
      { "a", 4, 5.0 },
      { "b", 3, 10.0 },
      { "b", 1, 2.5 },
      { "c", 2, 3.0 }
   });
}
