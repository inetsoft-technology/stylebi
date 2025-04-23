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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class SelectionTreeVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SelectionTreeVSAScriptable selectionTreeVSAScriptable;
   private SelectionTreeVSAssemblyInfo selectionTreeVSAssemblyInfo;
   private SelectionTreeVSAssembly selectionTreeVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      selectionTreeVSAssembly = new SelectionTreeVSAssembly();
      selectionTreeVSAssemblyInfo = (SelectionTreeVSAssemblyInfo) selectionTreeVSAssembly.getVSAssemblyInfo();
      selectionTreeVSAssemblyInfo.setName("SelectionTree1");
      viewsheet.addAssembly(selectionTreeVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      selectionTreeVSAScriptable = new SelectionTreeVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      selectionTreeVSAScriptable.setAssembly("SelectionTree1");
      vsaScriptable.setAssembly("SelectionTree1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SelectionTreeVSA", selectionTreeVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      selectionTreeVSAScriptable.addProperties();
      String[] keys = {"dropdown", "singleSelection", "selectFirstItemOnLoad", "submitOnChange", "wrapping", "suppressBlank", "expandAll"};

      for (String key : keys) {
         assert selectionTreeVSAScriptable.get(key, selectionTreeVSAScriptable) instanceof Boolean;
      }

      assertEquals("SelectionTree", selectionTreeVSAScriptable.get("title", selectionTreeVSAScriptable));
      assertEquals(8, selectionTreeVSAScriptable.get("sortType", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
   }

   @Test
   void testGetSetCellValue(){
      selectionTreeVSAScriptable.setCellValue("value1");
      assertEquals("value1", selectionTreeVSAScriptable.getCellValue());
   }

   @Test
   void tetGet() {
      assertNull(selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
      selectionTreeVSAScriptable.setCellValue("value1");
      assertEquals("value1", selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
      assertEquals("SelectionTree", selectionTreeVSAScriptable.get("title", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("drillMember", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("drillMembers", selectionTreeVSAScriptable));
   }

   @Test
   void tetHas() {
      assertFalse(selectionTreeVSAScriptable.has("property1", selectionTreeVSAScriptable));
      selectionTreeVSAScriptable.setCellValue("value1");
      assertTrue(selectionTreeVSAScriptable.has("value", selectionTreeVSAScriptable));
      assertTrue(selectionTreeVSAScriptable.has("titleVisible", selectionTreeVSAScriptable));
   }

   @Test
   void testGetSetShowType() {
      selectionTreeVSAScriptable.setShowType(true);
      assertTrue(selectionTreeVSAScriptable.getShowType());
   }

   @Test
   void testSetSingleSelection() {
      assertFalse(selectionTreeVSAScriptable.isSingleSelection());
      selectionTreeVSAScriptable.setSingleSelection(true);
      assertTrue(selectionTreeVSAScriptable.isSingleSelection());
      selectionTreeVSAScriptable.setSingleSelection(false);
      assertFalse(selectionTreeVSAScriptable.isSingleSelection());
   }

   @Test
   void testGetSetFields() {
      selectionTreeVSAScriptable.setFields(new String[]{});
      assertArrayEquals(new String[] {}, selectionTreeVSAScriptable.getFields());

      selectionTreeVSAScriptable.setFields(new String[] {"field1", "field2"});
      assertArrayEquals(new String[] {"field1", "field2"}, selectionTreeVSAScriptable.getFields());

      ColumnRef columnRef = new ColumnRef();
      columnRef.setDataRef(new AttributeRef("entity", "attribute"));
      DataRef [] dataRefs = new DataRef[] {columnRef};
      selectionTreeVSAssemblyInfo.setDataRefs(dataRefs);
      selectionTreeVSAScriptable.setFields(new String[] {"attribute"});
      assertArrayEquals(new String[] {"attribute"}, selectionTreeVSAScriptable.getFields());

      ColumnRef columnRef1 = new ColumnRef();
      columnRef1.setDataRef(new AttributeRef());
      DataRef [] dataRefs1 = new DataRef[] {columnRef, columnRef1};
      selectionTreeVSAssemblyInfo.setDataRefs(dataRefs1);
      selectionTreeVSAScriptable.setFields(new String[] {"attribute", "field2"});
      assertArrayEquals(new String[] {"attribute", "field2"}, selectionTreeVSAScriptable.getFields());
   }
}