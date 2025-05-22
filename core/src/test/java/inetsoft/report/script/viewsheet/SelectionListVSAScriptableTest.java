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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class SelectionListVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SelectionListVSAScriptable selectionListVSAScriptable;
   private SelectionListVSAssemblyInfo selectionListVSAssemblyInfo;
   private SelectionListVSAssembly selectionListVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      selectionListVSAssembly = new SelectionListVSAssembly();
      selectionListVSAssemblyInfo =
         (SelectionListVSAssemblyInfo) selectionListVSAssembly.getVSAssemblyInfo();
      selectionListVSAssemblyInfo.setName("SelectionList1");
      viewsheet.addAssembly(selectionListVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      selectionListVSAScriptable = new SelectionListVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      selectionListVSAScriptable.setAssembly("SelectionList1");
      vsaScriptable.setAssembly("SelectionList1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SelectionListVSA", selectionListVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      selectionListVSAScriptable.addProperties();
      String[] keys = {"dropdown", "singleSelection", "selectFirstItemOnLoad",
                       "submitOnChange", "wrapping", "suppressBlank"};

      for (String key : keys) {
         assert selectionListVSAScriptable.get(key, selectionListVSAScriptable) instanceof Boolean;
      }

      assertEquals("SelectionList",
                   selectionListVSAScriptable.get("title", selectionListVSAScriptable));
      assertEquals(XConstants.SORT_SPECIFIC,
                   selectionListVSAScriptable.get("sortType", selectionListVSAScriptable));
      assertNull(selectionListVSAScriptable.get("value", selectionListVSAScriptable));
   }

   @Test
   void testGetSetCellValue(){
      selectionListVSAScriptable.setCellValue("value1");
      assertEquals("value1", selectionListVSAScriptable.getCellValue());
   }

   @Test
   void testGet() {
      assertNull(selectionListVSAScriptable.get("value", selectionListVSAScriptable));
      selectionListVSAScriptable.setCellValue("value1");
      assertEquals("value1",
                   selectionListVSAScriptable.get("value", selectionListVSAScriptable));
      assertEquals("SelectionList",
                   selectionListVSAScriptable.get("title", selectionListVSAScriptable));
   }

   @Test
   void testHas() {
      assertFalse(selectionListVSAScriptable.has("property1", selectionListVSAScriptable));
      selectionListVSAScriptable.setCellValue("value1");
      assertTrue(selectionListVSAScriptable.has("value", selectionListVSAScriptable));
      assertTrue(selectionListVSAScriptable.has("titleVisible", selectionListVSAScriptable));
   }

   @Test
   void testGetSetShowType() {
      selectionListVSAScriptable.setShowType(true);
      assertTrue(selectionListVSAScriptable.getShowType());
   }

   @Test
   void testSetSingleSelection() {
      selectionListVSAssemblyInfo.setSelectionList(new SelectionList());
      assertFalse(selectionListVSAScriptable.isSingleSelection());
      selectionListVSAScriptable.setSingleSelection(true);
      assertTrue(selectionListVSAScriptable.isSingleSelection());
      selectionListVSAScriptable.setSingleSelection(false);
      assertFalse(selectionListVSAScriptable.isSingleSelection());
   }

   @Test
   void testGetSetFields() {
      selectionListVSAScriptable.setFields(new String[]{});
      assertNull(selectionListVSAScriptable.getFields());

      selectionListVSAScriptable.setFields(new String[] {"field1", "field2"});
      assertArrayEquals(new String[] {"field1"}, selectionListVSAScriptable.getFields());

      ColumnRef columnRef = new ColumnRef();
      columnRef.setDataRef(new AttributeRef("entity", "attribute"));
      selectionListVSAssemblyInfo.setDataRef(columnRef);
      selectionListVSAScriptable.setFields(new String[] {"attribute", "field2"});
      assertArrayEquals(new String[] {"attribute"}, selectionListVSAScriptable.getFields());
      selectionListVSAScriptable.setFields(new String[] {"field1", "field2"});
      assertArrayEquals(new String[] {"field1"}, selectionListVSAScriptable.getFields());
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      //keep default size when set size is invalid
      Dimension defaultSize = new Dimension(100, 120);
      Dimension[] invalidDimensions = new Dimension[] {
         new Dimension(-1, 100),
         new Dimension(100, -1),
         new Dimension(-1, -1)
      };
      for (Dimension dim : invalidDimensions) {
         selectionListVSAScriptable.setSize(dim);
         assertEquals(defaultSize, selectionListVSAScriptable.getSize());
      }

      Dimension dim1 = new Dimension(80, 100);
      selectionListVSAScriptable.setSize(dim1);
      assertEquals(dim1, selectionListVSAScriptable.getSize());
      selectionListVSAScriptable.setShowTypeValue(false);
      assertFalse(selectionListVSAScriptable.getShowType());
   }

   @Test
   void testSetGetSelectedObjects() {
      SelectionList selectionList = new SelectionList();
      SelectionValue[] selectionValues = new SelectionValue[2];
      selectionValues[0] = new SelectionValue("label1", "value1");
      selectionValues[1] = new SelectionValue("label2", "value2");
      selectionValues[0].setSelected(true);
      selectionList.setSelectionValues(selectionValues);
      selectionListVSAssemblyInfo.setSelectionList(selectionList);

      //set selected objects when no dataref
      Object [] objects = new Object[2];
      objects[0] = "value1";
      objects[1] = "value2";
      selectionListVSAScriptable.setSelectedObjects(objects);
      assertArrayEquals(new Object[0], selectionListVSAScriptable.getSelectedObjects());

      //set selected objects when has dataref, meta data
      ColumnRef columnRef = new ColumnRef();
      columnRef.setDataRef(new AttributeRef("entity", "attribute"));
      selectionListVSAssemblyInfo.setDataRef(columnRef);
      selectionListVSAScriptable.setSelectedObjects(objects);
      assertArrayEquals(objects, selectionListVSAScriptable.getSelectedObjects());

      //meta data, select in single selection mode
      selectionListVSAssemblyInfo.setSingleSelection(true);
      selectionListVSAScriptable.setSelectedObjects(objects);
      assertArrayEquals(objects, selectionListVSAScriptable.getSelectedObjects());

      //not meta data
      selectionListVSAssemblyInfo.setUsingMetaData(false);
      selectionListVSAScriptable.setSelectedObjects(objects);
      assertArrayEquals(objects, selectionListVSAScriptable.getSelectedObjects());

      //clean selection, select the first one when single selection mode
      selectionListVSAScriptable.setSelectedObjects(null);
      assertArrayEquals(new Object[] {"value1"}, selectionListVSAScriptable.getSelectedObjects());
      selectionListVSAssemblyInfo.setSingleSelection(false);
      selectionListVSAScriptable.setSelectedObjects(null);
      assertArrayEquals(new Object[0], selectionListVSAScriptable.getSelectedObjects());
   }

   @Test
   void testSetSelectFirstItem() {
      assertFalse(selectionListVSAScriptable.isSelectFirstItem());
      selectionListVSAScriptable.setSelectFirstItem(true);
      assertTrue(selectionListVSAScriptable.isSelectFirstItem());
   }

   @Test
   void testGetSetQuery() {
      assertNull(selectionListVSAScriptable.getQuery());
      selectionListVSAScriptable.setQuery("query");
      assertEquals("query", selectionListVSAScriptable.getQuery());
      selectionListVSAScriptable.setQueries(new String[] {"query1", "query2"});
      assertArrayEquals(new String[] {"query1", "query2"},
                        selectionListVSAScriptable.getQueries());
   }

   @Test
   void testSetSingleSelectionValue() {
      assertFalse(selectionListVSAScriptable.isSingleSelection());
      assertFalse(selectionListVSAssemblyInfo.getSingleSelectionValue());

      selectionListVSAssemblyInfo.setSelectionList(new SelectionList());
      selectionListVSAScriptable.setSingleSelectionValue(true);
      assertTrue(selectionListVSAScriptable.isSingleSelection());
      assertTrue(selectionListVSAssemblyInfo.getSingleSelectionValue());
   }
}