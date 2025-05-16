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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.filter.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class CrosstabGroupSelectorTest {
   private CrossFilter mockCrossFilter;
   private Map<String, NamedCellRange.GroupSpec> groupSpecs;

   @BeforeEach
   void setUp() {
      mockCrossFilter = mock(CrossFilter.class);
      groupSpecs = new HashMap<>();
   }

   @Test
   void testGetSelectorWithGroupSpec() {
      // Mock group spec
      setupGroupSpec("group1", "groupValue", true);
      // Mock CrossFilter behavior
      mockCrossFilterBehavior(3, Map.of("group1", "groupValue"));
      // Mock wildcard group spec
      setupWildcardGroupSpec("group2");
      // mock header row
      mockHeaderRow("col1");

      CrosstabGroupSelector selector = CrosstabGroupSelector.getSelector(
         "col1", mockCrossFilter, groupSpecs);

      assertNotNull(selector);
      assertEquals(6, selector.getGroupCells().size());
   }

   /**
    * test getSelector with null group spec and value
    */
   @Test
   void testGetSelectorWithNullGroup() {
      // Mock null group spec
      setupGroupSpec("group1", null, true);
      // Mock null value
      Map<Object, Object> keyValuePairs = new HashMap<>();
      keyValuePairs.put("group1", null);
      mockCrossFilterBehavior(2, keyValuePairs);

      CrosstabGroupSelector selector = CrosstabGroupSelector.getSelector(
         "col1", mockCrossFilter, groupSpecs);

      assertNotNull(selector);
   }

   /**
    * Test that the init method throws an exception when the group spec is not found.
    */
   @Test
   void testInitThrownException() {
      setupGroupSpec("group11", "value1", false);

      RuntimeException exception = assertThrows(RuntimeException.class, () -> {
         CrosstabGroupSelector.getSelector("col1", mockCrossFilter, groupSpecs);
      });

      assertTrue(exception.getMessage().contains("Group positional reference not allowed in table reference"));
   }

   /**
    * test match with null， total cell，check not at the deep enough level
    */
   @Test
   void testMatchWithNullandTotalCell() {
      setupGroupSpec("group1", "groupValue", true);
      mockCrossFilterBehavior(3, Map.of("group1", "groupValue"));
      mockHeaderRow("col1");

      CrosstabGroupSelector selector = CrosstabGroupSelector.getSelector(
         "col1", mockCrossFilter, groupSpecs);
      TableDataDescriptor mockTableDataDescriptor = mock(TableDataDescriptor.class);

      //check when unknown cell, tpath = null
      when(mockTableDataDescriptor.getCellDataPath(anyInt(), anyInt())).thenReturn(null);
      when(mockCrossFilter.getDescriptor()).thenReturn(mockTableDataDescriptor);
      assertEquals(RangeProcessor.NO, selector.match(mockCrossFilter, 0, 0));

      //check ignore 'Total' header cell
      when(mockCrossFilter.getDescriptor()).thenReturn(mockTableDataDescriptor);
      when(mockTableDataDescriptor.getCellDataPath(anyInt(),anyInt())).thenReturn(new TableDataPath());
      assertEquals(RangeProcessor.NO, selector.match(mockCrossFilter, 0, 0));

      //check not at the deep enough level
      TableDataPath mockTPath = mock(TableDataPath.class);
      when(mockTPath.getType()).thenReturn(TableDataPath.GROUP_HEADER);
      when(mockTPath.getPath()).thenReturn(new String[]{"col1"});
      when(mockTableDataDescriptor.getCellDataPath(anyInt(), anyInt())).thenReturn(mockTPath);
      assertEquals(RangeProcessor.NO, selector.match(mockCrossFilter, 0, 0));
   }

   @Test
   void testMatchWithWrongAggregateColumn() {
      // Mock CrossCalcFilter behavior
      mockCrossFilterBehavior(3, Map.of("group1", "groupValue"));

      mockHeaderRow("col1");

      CrosstabGroupSelector selector = CrosstabGroupSelector.getSelector(
         "col1", mockCrossFilter, groupSpecs);

      TableDataDescriptor mockTableDataDescriptor = mock(TableDataDescriptor.class);
      when(mockCrossFilter.getDescriptor()).thenReturn(mockTableDataDescriptor);

      TableDataPath mockTPath = mock(TableDataPath.class);
      when(mockTPath.getType()).thenReturn(TableDataPath.GROUP_HEADER);
      when(mockTPath.getPath()).thenReturn(new String[]{"col0"});
      when(mockTableDataDescriptor.getCellDataPath(anyInt(), anyInt())).thenReturn(mockTPath);

      assertEquals(RangeProcessor.NO, selector.match(mockCrossFilter, 0, 0));
   }

   @Test
   void testMatchWithValidPath() {
      setupGroupSpec("group1", "groupValue", true);
      mockCrossFilterBehavior(3, Map.of("group1", "groupValue"));
      mockHeaderRow("col1");

      CrosstabGroupSelector selector = CrosstabGroupSelector.getSelector(
         "col1", mockCrossFilter, groupSpecs);

      TableDataDescriptor mockTableDataDescriptor = mock(TableDataDescriptor.class);
      when(mockCrossFilter.getDescriptor()).thenReturn(mockTableDataDescriptor);

      TableDataPath mockTPath = mock(TableDataPath.class);

      //check col in groups
      when(mockTPath.getType()).thenReturn(TableDataPath.GROUP_HEADER);
      when(mockTPath.getPath()).thenReturn(new String[]{"group1", "col1"});
      when(mockTableDataDescriptor.getCellDataPath(anyInt(), anyInt())).thenReturn(mockTPath);

      assertEquals(RangeProcessor.YES, selector.match(mockCrossFilter, 0, 0));
   }

   // Helper methods
   private void setupGroupSpec(String groupName, String value, boolean isByValue) {
      NamedCellRange.GroupSpec mockSpec = mock(NamedCellRange.GroupSpec.class);
      when(mockSpec.getValue()).thenReturn(value);
      when(mockSpec.isByValue()).thenReturn(isByValue);
      groupSpecs.put(groupName, mockSpec);
   }

   private void setupWildcardGroupSpec(String groupName) {
      NamedCellRange.GroupSpec mockSpec = mock(NamedCellRange.GroupSpec.class);
      when(mockSpec.getValue()).thenReturn("*");
      when(mockSpec.isByValue()).thenReturn(true);
      groupSpecs.put(groupName, mockSpec);
   }

   private void mockCrossFilterBehavior(int colCount, Map<Object, Object> keyValuePairs) {
      when(mockCrossFilter.moreRows(anyInt())).thenReturn(true, true, false);
      when(mockCrossFilter.getColCount()).thenReturn(colCount);
      when(mockCrossFilter.getKeyValuePairs(anyInt(), anyInt(), any())).thenReturn(keyValuePairs);
   }

   private void mockHeaderRow(String headerName) {
      when(mockCrossFilter.getRowHeaderCount()).thenReturn(1);
      when(mockCrossFilter.getRowHeader(0)).thenReturn(headerName);
   }
}
