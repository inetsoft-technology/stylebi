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

package inetsoft.report.script.formula;

import inetsoft.report.internal.table.*;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CalcRefTest {
   private CalcRef calcRef;
   private CalcTableVSAScriptable mockCalcTableVSAScriptable;
   private RuntimeCalcTableLens mockRuntimeCalcTableLens;
   private CalcCellContext mockContext;

   private Point point0 = new Point(0,0);
   private Point point1 = new Point(0,1);

   @BeforeEach
   void setUp() {
      mockCalcTableVSAScriptable = mock(CalcTableVSAScriptable.class);
      mockRuntimeCalcTableLens = mock(RuntimeCalcTableLens.class);
      mockContext = mock(CalcCellContext.class);
   }

   /**
    * test get with #
    */
   @Test
   void testGetWithChar1() {
      FormulaContext.pushCellLocation(point0);
      provideMockGroup(mockContext, "cell1", 5,"value1");

      // Test case for id = "#"
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertEquals(5, calcRef.get("#", mockCalcTableVSAScriptable));

      // Test case for id = "#", but context is null
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(null);
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertEquals(0, calcRef.get("#", mockCalcTableVSAScriptable));
   }

   /**
    * test get with **
    */
   @Test
   void testGetWithChar2() {
      provideMockCalcCellMap("cell1", new Point[] {point0});

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertArrayEquals(new Object[] {null}, (Object[])(calcRef.get("**", mockCalcTableVSAScriptable)));
   }

   /**
    * test get with **
    */
   @Test
   void testGetWithChar3() {
      FormulaContext.pushCellLocation(point0);

      // test when context is null
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result = calcRef.get("*", mockCalcTableVSAScriptable);
      assertArrayEquals(new Object[0], (Object[])result);

      // test when context is not null
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      CalcCellContext.Group mockGroup = mock(CalcCellContext.Group.class);
      when(mockContext.getGroup("cell1")).thenReturn(mockGroup);

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result2 = calcRef.get("*", mockCalcTableVSAScriptable);
      assertNull(result2);
   }

   /**
    * test get with +
    */
   @Test
   void testGetWithChar4() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);

      provideMockCalcCellMap("cell1", new Point[] {point0});

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertArrayEquals(new Object[] {null}, (Object[])(calcRef.get("+", mockCalcTableVSAScriptable)));
   }

   /**
    * test get with .
    */
   @Test
   void testGetWithChar5() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);

      //check group != null
      provideMockGroup(mockContext, "cell1", 1, "value1");

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result = calcRef.get(".", mockCalcTableVSAScriptable);
      assertEquals("value1", result);

      //check group == null and location length is 1
      when(mockContext.getGroup("cell1")).thenReturn(null);
      CalcCellMap mockCalcCellMap = provideMockCalcCellMap("cell1", new Point[] {point0});
      when(mockRuntimeCalcTableLens.getObject(0, 0)).thenReturn(1);

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result1 = calcRef.get(".", mockCalcTableVSAScriptable);
      assertEquals(1, result1);

      //check group == null and location length > 1
      when(mockCalcCellMap.getLocations("cell1", mockContext)).thenReturn(new Point[] {point0, point1});
      when(mockRuntimeCalcTableLens.getObject(0, 0)).thenReturn(1);

      Object result2 = calcRef.get(".", mockCalcTableVSAScriptable);
      assertArrayEquals(new Object[] {1, null}, (Object[])result2);
   }

   /**
    * check get with number
    */
   @Test
   void testGetWithOther() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);

      provideMockGroup(mockContext, "cell1", 2, "value1");

      // Test positive positional reference
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result = calcRef.get("+1", mockCalcTableVSAScriptable);
      assertNull(result);

      // Test negative positional reference
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(null);
      provideMockCalcCellMap("cell1", new Point[] {point0});
      Object result2 = calcRef.get("2", mockCalcTableVSAScriptable);
      assertNull(result2);
   }

   /**
    * check get with idx
    */
   @Test
   void testGetWithNumber() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      provideMockCalcCellMap("cell1", new Point[] {point0});

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertNull(calcRef.get(0, mockCalcTableVSAScriptable));
   }


   private CalcCellMap provideMockCalcCellMap(String cellName, Point[] points) {
      CalcCellMap mockCalcCellMap = mock(CalcCellMap.class);
      when(mockRuntimeCalcTableLens.getCalcCellMap()).thenReturn(mockCalcCellMap);
      when(mockCalcCellMap.getLocations(cellName)).thenReturn(points);
      when(mockCalcCellMap.getLocations(cellName, mockContext)).thenReturn(points);

      return mockCalcCellMap;
   }

   private void provideMockGroup(CalcCellContext mockContext, String cellName, int position, Object value) {
      CalcCellContext.Group mockGroup = mock(CalcCellContext.Group.class);
      when(mockContext.getGroup(cellName)).thenReturn(mockGroup);
      when(mockGroup.getPosition()).thenReturn(position);
      when(mockGroup.getValue(mockContext)).thenReturn(value);
   }
}


