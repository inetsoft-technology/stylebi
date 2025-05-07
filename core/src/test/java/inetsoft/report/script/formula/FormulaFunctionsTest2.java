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

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.awt.*;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SreeHome(importResources = "FormulaFunctionsTest.vso")
public class FormulaFunctionsTest2 {
   /**
    * test rowList will throw exception when not tablelens
    */
   @Test
   void testRowListIsNotTableLens() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         FormulaFunctions.rowList(new Object[]{1, 4, 7, 2},"","distinct=false"));

      assert exception.getMessage().contains("TableLens require for rowList");
   }

   /**
    * test rowList with a conditions.
    */
   @Test
   void testRowListWithCondition() throws Exception {
      Object data = XUtil.runQuery("ws:global:FormulaContext", null, org_admin, null);
      FormulaContext.pushScope(mock(CalcTableScope.class));

      // test sort=desc options
      TableValueList res1 = (TableValueList)FormulaFunctions.rowList(data, "Employee?Total>20000",
                                                                     "sort=desc,distinct=true");
      assertArrayEquals(new Object[]{"Sue", "Eric"}, res1.getValues());

      //test maxrow and sortColumn options
      TableValueList res2 = (TableValueList)FormulaFunctions.rowList(data, "Employee?Total>10000",
                                                                     "sort=desc,sortcolumn=Total,maxrows=3");
      assertArrayEquals(new Object[]{"Sue", "Eric", "Eric"}, res2.getValues());
   }

   /**
    * Test rowList with group table,
    * @TODO, Unfinished, how to set row group? if unset, always return 0
    * rowList(data, 'Company@Employee:$Employee', 'sort=desc')
    */
   @Test
   void testRowListWithGroupTable() throws Exception {
      Object data1 = XUtil.runQuery("ws:global:FormulaContext", null, org_admin, null);
      FormulaContext.pushScope(mock(CalcTableScope.class));
      FormulaContext.pushCellLocation(new Point(0,1));

      TableValueList res1 = (TableValueList)FormulaFunctions.rowList(data1, "Company@Employee:$Employee",
                                                                     "sort=desc,distinct=true");
      // no row group, so didn't get any value
      assertEquals(0,  res1.getValues().length);
      //assertArrayEquals(new Object[]{"Sue", "Eric"}, res1.getValues());
   }

   private static Stream<Arguments> provideToArrayTestCases() {
      return Stream.of(
         Arguments.of("test", new Object[]{"test"}), // String input
         Arguments.of(new Object[]{"value1", "value2"}, new Object[]{"value1", "value2"}), // Object array input
         Arguments.of(null, new Object[]{}), // Null input
         Arguments.of(mock(TableLens.class), new Object[]{}) // Mock TableLens input
      );
   }

   /**
    * test toArray with object and TableArray
    */
   @ParameterizedTest
   @MethodSource("provideToArrayTestCases")
   void testToArrayWithArrayAndTablelens(Object input, Object[] expected) {
      Object result = FormulaFunctions.toArray(input);

      if(input instanceof TableLens) {
          assertInstanceOf(TableArray.class, result);
      } else {
         assertArrayEquals(expected, (Object[])result);
      }
   }

   private static Stream<Arguments> provideInArrayTestCases() {
      return Stream.of(
         // Test case 1: The array contains the value
         Arguments.of(new Object[]{"apple", null, "cherry"}, null, true),
         // Test case 2: The array contains the value
         Arguments.of(new Object[]{"apple", "banana", "cherry"}, "banana", true),
         // Test case 3: The array does not contain the value
         Arguments.of(new Object[]{"apple", "banana", "cherry"}, "grape", false),
         // Test case 4: The array is empty
         Arguments.of(new Object[]{}, "value", false),
         // Test case 5: The array is null
         Arguments.of(null, "value", false)
      );
   }

   @ParameterizedTest
   @MethodSource("provideInArrayTestCases")
   void testInArrayWithArray(Object[] arrayInput, Object value, boolean expected) {
      boolean result = FormulaFunctions.inArray(arrayInput, value);
      assertEquals(expected, result);
   }

   /**
    * test inArray with XTable
    */
   @Test
   void testInArrayWithXTable() {
      Object q1 = XUtil.runQuery("ws:global:FormulaContext", null, org_admin, null);

      assertTrue(FormulaFunctions.inArray(q1, "Annie"));
      assertFalse(FormulaFunctions.inArray(q1, "test"));
   }

   /**
    * test inGroup with a invalid objects
    */
   @Test
   void testInGroupWithInvalidObject() {
      Object data1 = new Object[]{"name", "a"};
      FormulaContext.pushTable(objData);
      boolean res = FormulaFunctions.inGroups(data1, "Others");

      assertFalse(res);
   }

   /**
    * test inGroup with tablelens
    * @todo mock too many objects, will be improvement in future.
    */
   @Test
   void testInGroupWithTablelens() {
      // Mock RuntimeCalcTableLens and CalcTableLens
      RuntimeCalcTableLens mockRuntimeCalcTableLens = mock(RuntimeCalcTableLens.class);
      mockRuntimeCalcTableLens.setCellName(0,0,"group1");
      CalcTableLens mockCalcTableLens = mock(CalcTableLens.class);
      when(mockRuntimeCalcTableLens.getCalcTableLens()).thenReturn(mockCalcTableLens);
      CalcCellMap mockCalcCellMap = mock(CalcCellMap.class);
      when(mockRuntimeCalcTableLens.getCalcCellMap()).thenReturn(mockCalcCellMap);

      when(mockRuntimeCalcTableLens.getCalcCellMap().getLocations("group1")).thenReturn(new Point[] { new Point(0, 0) });

      CalcCellContext.Group group1 = mock(CalcCellContext.Group.class);
      CalcCellContext mockCalcCellContext = mock(CalcCellContext.class);
      when(mockCalcCellContext.getGroupCount()).thenReturn(1);
      when(mockCalcCellContext.getGroup("group1")).thenReturn(group1);
      when(group1.getValues()).thenReturn(new Object[] { "value1" });
      when(group1.getValue(mockCalcCellContext)).thenReturn("Others");

      when(mockRuntimeCalcTableLens.getCellContext(0,0)).thenReturn(mockCalcCellContext);

      when(mockCalcTableLens.getRowCount()).thenReturn(2);
      when(mockCalcTableLens.getColCount()).thenReturn(2);
      when(mockCalcTableLens.getCellName(0, 0)).thenReturn("group1");
      when(mockCalcTableLens.getCellLocation("group1")).thenReturn(new Point(0, 0));
      when(mockCalcTableLens.getExpansion(0,0)).thenReturn(CalcTableLens.EXPAND_HORIZONTAL);

      FormulaContext.pushTable(mockRuntimeCalcTableLens);
      Object param = new Object[] { "group1", "value1", "group2", "value2" };

      boolean result1 = FormulaFunctions.inGroups(param, "Others");
      assertFalse(result1);
   }
   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };
   SRPrincipal org_admin = new SRPrincipal(new IdentityID("admin", "host-org"),
                                           new IdentityID[] { new IdentityID("Organization Administrator", null)},
                                           new String[0], "host-org",
                                           Tool.getSecureRandom().nextLong());
}
