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
import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      assertEquals(5, calcRef.getMember("#"));

      // Test case for id = "#", but context is null
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(null);
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertEquals(0, calcRef.getMember("#"));
   }

   /**
    * test get with **
    */
   @Test
   void testGetWithChar2() {
      provideMockCalcCellMap("cell1", new Point[] {point0});

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      assertArrayEquals(new Object[] {null}, (Object[])(calcRef.getMember("**")));
   }

   /**
    * test get with **
    */
   @Test
   void testGetWithChar3() {
      FormulaContext.pushCellLocation(point0);

      // test when context is null
      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result = calcRef.getMember("*");
      assertArrayEquals(new Object[0], (Object[])result);

      // test when context is not null
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      CalcCellContext.Group mockGroup = mock(CalcCellContext.Group.class);
      when(mockContext.getGroup("cell1")).thenReturn(mockGroup);

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result2 = calcRef.getMember("*");
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
      assertArrayEquals(new Object[] {null}, (Object[])(calcRef.getMember("+")));
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
      Object result = calcRef.getMember(".");
      assertEquals("value1", result);

      //check group == null and location length is 1
      when(mockContext.getGroup("cell1")).thenReturn(null);
      CalcCellMap mockCalcCellMap = provideMockCalcCellMap("cell1", new Point[] {point0});
      when(mockRuntimeCalcTableLens.getObject(0, 0)).thenReturn(1);

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object result1 = calcRef.getMember(".");
      assertEquals(1, result1);

      //check group == null and location length > 1
      when(mockCalcCellMap.getLocations("cell1", mockContext)).thenReturn(new Point[] {point0, point1});
      when(mockRuntimeCalcTableLens.getObject(0, 0)).thenReturn(1);

      Object result2 = calcRef.getMember(".");
      assertArrayEquals(new Object[] {1, null}, (Object[])result2);
   }

   /**
    * test get with valueOf (GraalJS ToPrimitive coercion)
    */
   @Test
   void testGetWithValueOf() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      provideMockGroup(mockContext, "cell1", 1, "value1");

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object member = calcRef.getMember("valueOf");
      assertInstanceOf(ProxyExecutable.class, member);
      assertEquals("value1", ((ProxyExecutable) member).execute());
   }

   /**
    * test get with toString (GraalJS ToPrimitive coercion)
    */
   @Test
   void testGetWithToString() {
      FormulaContext.pushCellLocation(point0);
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
      provideMockGroup(mockContext, "cell1", 1, "value1");

      calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");
      Object member = calcRef.getMember("toString");
      assertInstanceOf(ProxyExecutable.class, member);
      assertEquals("value1", ((ProxyExecutable) member).execute());
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
      Object result = calcRef.getMember("+1");
      assertNull(result);

      // Test negative positional reference
      when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(null);
      provideMockCalcCellMap("cell1", new Point[] {point0});
      Object result2 = calcRef.getMember("2");
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
      assertNull(calcRef.getArrayElement(0L));
   }


   /**
    * Bug #75738: when a $name reference is consumed as plain data by host
    * utilities (JSObject.split/splitN, called from CALC aggregates such as
    * sum($x)/nthLargest($x)), ScriptUtil.unwrap must resolve the live CalcRef to
    * its referenced scalar value instead of leaving the wrapper to fall back to
    * Object.toString() (which produced "...CalcRef@<hash>" and 0).
    */
   @Test
   void testScriptUtilUnwrapScalarRef() {
      FormulaContext.pushCellLocation(point0);

      try {
         when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
         provideMockGroup(mockContext, "cell1", 1, 42.0);

         calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");

         assertEquals(42.0, inetsoft.util.script.ScriptUtil.unwrap(calcRef));
         // split() string-splits a scalar (Tool.split), so a numeric ref reads
         // back as its string form; splitN then parses it to the real number so
         // numeric aggregates (sum/average/...) compute correctly.
         assertArrayEquals(new Object[] { "42.0" },
                           inetsoft.util.script.JSObject.split(calcRef));
         assertArrayEquals(new double[] { 42.0 },
                           inetsoft.util.script.JSObject.splitN(calcRef));
      }
      finally {
         FormulaContext.popCellLocation();
      }
   }

   /**
    * Bug #75738: an array-valued group reference (multiple cell locations) must
    * unwrap to the underlying Object[] of cell values, so JSObject.split/splitN
    * see real data. This is the case the issue's proposed CalcRef.toString()
    * fix would have broken (String.valueOf(Object[]) yields "[Ljava...@<hash>").
    */
   @Test
   void testScriptUtilUnwrapArrayRef() {
      FormulaContext.pushCellLocation(point0);

      try {
         when(mockRuntimeCalcTableLens.getCellContext(0, 0)).thenReturn(mockContext);
         // group == null so unwrap() falls to the cmap multi-location branch
         when(mockContext.getGroup("cell1")).thenReturn(null);
         CalcCellMap mockCalcCellMap = provideMockCalcCellMap("cell1", new Point[] { point0, point1 });
         when(mockRuntimeCalcTableLens.getObject(0, 0)).thenReturn(100.0);
         when(mockRuntimeCalcTableLens.getObject(1, 0)).thenReturn(200.0);

         calcRef = new CalcRef(mockRuntimeCalcTableLens, "cell1");

         assertArrayEquals(new Object[] { 100.0, 200.0 },
                           (Object[]) inetsoft.util.script.ScriptUtil.unwrap(calcRef));
         assertArrayEquals(new Object[] { 100.0, 200.0 },
                           inetsoft.util.script.JSObject.split(calcRef));
         assertArrayEquals(new double[] { 100.0, 200.0 },
                           inetsoft.util.script.JSObject.splitN(calcRef));
      }
      finally {
         FormulaContext.popCellLocation();
      }
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


