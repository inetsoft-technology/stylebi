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
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalcGroupSelectorTest {
   private RuntimeCalcTableLens mockCalcTableLens;
   private HashMap<Object, Object> groupSpecs;
   private CalcCellContext mockContext;

   @BeforeEach
   void setUp() {
      mockCalcTableLens = mock(RuntimeCalcTableLens.class);
      groupSpecs = new HashMap<>();

      FormulaContext.pushCellLocation(new Point(1, 1));
      FormulaContext.pushTable(mockCalcTableLens);

      // Mock group spec
      NamedCellRange.GroupSpec mockSpec = mock(NamedCellRange.GroupSpec.class);
      when(mockSpec.isByValue()).thenReturn(true);
      when(mockSpec.getValue()).thenReturn("groupValue");
      groupSpecs.put("group1", mockSpec);

      // Mock RuntimeCalcTableLens to return a valid CalcCellContext
      mockContext = mock(CalcCellContext.class);
      when(mockCalcTableLens.getCellContext(1, 1)).thenReturn(mockContext);
   }

   /**
    * test constructor with valid context
    */
   @Test
   void testConstructorWithValidContext() {
      mockGroups("group1", "group2");

      CalcGroupSelector selector = new CalcGroupSelector(mockCalcTableLens, groupSpecs);

      assertNotNull(selector);
      assertFalse(groupSpecs.isEmpty());
   }

   /**
    * test match with valid group value
    */
   @Test
   void testMatchWithValidGroup() {
      mockGroups("group1", "group2");

      CalcGroupSelector selector = new CalcGroupSelector(mockCalcTableLens, groupSpecs);

      int result = selector.match(mockCalcTableLens, 0, 0);
      assertEquals(RangeProcessor.YES, result);
   }

   /**
    * test match with invalid group value
    */
   @Test
   void testMatchWithInvalidGroup() {
      mockGroupValue("group1", "wrongValue");

      CalcGroupSelector selector = new CalcGroupSelector(mockCalcTableLens, groupSpecs);

      int result = selector.match(mockCalcTableLens, 0, 0);
      assertEquals(RangeProcessor.YES, result);
   }

   /**
    * test match with empty group specs
    */
   @Test
   void testMatchWithEmptyGroupSpecs() {
      groupSpecs.clear();

      CalcGroupSelector selector = new CalcGroupSelector(mockCalcTableLens, groupSpecs);

      int result = selector.match(mockCalcTableLens, 0, 0);
      assertEquals(RangeProcessor.YES, result);
   }

   /**
    *  test match with null context
    */
   @Test
   void testMatchWithEmptyContext() {
      when(mockCalcTableLens.getCellContext(anyInt(), anyInt())).thenReturn(null);
      CalcGroupSelector selector = new CalcGroupSelector(mockCalcTableLens, groupSpecs);

      int result = selector.match(mockCalcTableLens, 0, 0);
      assertEquals(RangeProcessor.YES, result);
   }

   // Helper method to mock groups
   private void mockGroups(String... groupNames) {
      List<CalcCellContext.Group> mockGroups = new ArrayList<>();
      for (String name : groupNames) {
         CalcCellContext.Group mockGroup = mock(CalcCellContext.Group.class);
         when(mockGroup.getName()).thenReturn(name);
         mockGroups.add(mockGroup);
      }
      when(mockContext.getGroups()).thenReturn(mockGroups);
   }

   // Helper method to mock group values
   private void mockGroupValue(String groupName, String value) {
      CalcCellContext.Group mockGroup = mock(CalcCellContext.Group.class);
      when(mockGroup.getValue(any())).thenReturn(value);
      when(mockContext.getGroup(groupName)).thenReturn(mockGroup);
   }
}