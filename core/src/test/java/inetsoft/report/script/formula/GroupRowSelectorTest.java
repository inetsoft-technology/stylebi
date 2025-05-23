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

import inetsoft.report.filter.GroupedTable;
import inetsoft.report.internal.table.DefaultGroupedTable;
import inetsoft.report.internal.table.DefaultSummaryTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import inetsoft.uql.XTable;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroupRowSelectorTest {
   private GroupRowSelector groupRowSelector;
   private DefaultSummaryTable defaultSummaryTable;

   @BeforeEach
   void setUp() {
      defaultSummaryTable = new DefaultSummaryTable(new DefaultTableLens(objData),
                                                    new int[] {1});
   }

   @Test
   void  testGroupRowSelectorConstructor() {
      Map<String, Object> groupSpecs = provideMockGroupSpecs(
         Map.of("name", "a", "=id", "1")
      );

      groupRowSelector = new GroupRowSelector(defaultSummaryTable, groupSpecs);
      assertNotNull(groupRowSelector);
      assertFalse(groupRowSelector.isWildCard());

      groupRowSelector.setSummary(true);
      assertTrue(groupRowSelector.isSummary());
   }


   @Test
   void testMatchWithNo() {
      // Mock the GroupedTable
      GroupedTable mockGroupedTable = mock(GroupedTable.class);

      // Stub methods for GroupedTable
      when(mockGroupedTable.isSummaryRow(1)).thenReturn(false);
      when(mockGroupedTable.isGroupHeaderCell(1, 0)).thenReturn(true);
      when(mockGroupedTable.getGroupLastRow(1)).thenReturn(3);
      when(mockGroupedTable.isAddGroupHeader()).thenReturn(true);

      // Set up the GroupRowSelector with mock data
      Map<String, Object> groupSpecs = provideMockGroupSpecs(Map.of("name", "a", "=field['id']", "1"));
      groupRowSelector = new GroupRowSelector(defaultSummaryTable, groupSpecs);

      FormulaContext.pushScope(mock(CalcTableScope.class));
      // Call the match method
      int result = groupRowSelector.match(mockGroupedTable, 1, 0);

      // Assert the expected result
      assertEquals(RangeProcessor.NO, result);
   }
   @Test
   void testMatchWithInvalidInput() {
      GroupedTable mockGroupedTable = mock(GroupedTable.class);

      // Stub methods for GroupedTable
      when(mockGroupedTable.isSummaryRow(1)).thenReturn(false);
      when(mockGroupedTable.isGroupHeaderCell(1, 0)).thenReturn(true);
      when(mockGroupedTable.getGroupLastRow(1)).thenReturn(3);
      when(mockGroupedTable.isAddGroupHeader()).thenReturn(true);

      groupRowSelector = new GroupRowSelector(defaultSummaryTable, new HashMap<>());
      assertEquals(RangeProcessor.YES, groupRowSelector.match(mockGroupedTable, 1, 0));

      // Test with a wildcard group spec
      Map<String, Object> groupSpecs = provideMockGroupSpecs(Map.of("name", "*"));
      groupRowSelector = new GroupRowSelector(defaultSummaryTable, groupSpecs);
      assertEquals(RangeProcessor.YES, groupRowSelector.match(mockGroupedTable, 1, 0));
   }

   private Map<String, Object> provideMockGroupSpecs(Map<String, String> groupSpecMap) {
      Map<String, Object> groupSpecs = new HashMap<>();
      for (Map.Entry<String, String> entry : groupSpecMap.entrySet()) {
         NamedCellRange.GroupSpec mockSpec = mock(NamedCellRange.GroupSpec.class);
         when(mockSpec.isByValue()).thenReturn(false);
         when(mockSpec.getValue()).thenReturn(entry.getValue());
         groupSpecs.put(entry.getKey(), mockSpec);
      }
      return groupSpecs;
   }

   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };
}
