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
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class CheckBoxVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox;
   private CheckBoxVSAScriptable checkBoxVSAScriptable;
   private CheckBoxVSAssemblyInfo checkBoxVSAssemblyInfo;
   private CheckBoxVSAssembly checkBoxVSAssembly;
   private VSAScriptable vsaScriptable;
   private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      checkBoxVSAssembly = new CheckBoxVSAssembly();
      checkBoxVSAssemblyInfo = (CheckBoxVSAssemblyInfo) checkBoxVSAssembly.getVSAssemblyInfo();
      checkBoxVSAssemblyInfo.setName("CheckBox1");
      viewsheet.addAssembly(checkBoxVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      checkBoxVSAScriptable = new CheckBoxVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      checkBoxVSAScriptable.setAssembly("CheckBox1");
      vsaScriptable.setAssembly("CheckBox1");
   }

   @Test
   void testGetClassName() {
      assertEquals("CheckBoxVSA", checkBoxVSAScriptable.getClassName());
   }

   @Test
   void testGetSetCellValue() {
      checkBoxVSAScriptable.setCellValue("value1");
      assertEquals("value1", checkBoxVSAScriptable.getCellValue());
   }

   @Test
   void testGet() {
      assertArrayEquals(new Object[0], (Object[]) checkBoxVSAScriptable.get("value", checkBoxVSAScriptable));
      checkBoxVSAScriptable.setCellValue(new Date(125, 1, 20));
      assert simpleDateFormat.format(checkBoxVSAScriptable.get("value", checkBoxVSAScriptable)).equals("2025-02-20");
      assertEquals("CheckBox", checkBoxVSAScriptable.get("title", checkBoxVSAScriptable));
   }

   @Test
   void testHas() {
      assertFalse(checkBoxVSAScriptable.has("property1", checkBoxVSAScriptable));
      checkBoxVSAScriptable.setCellValue("value1");
      assertTrue(checkBoxVSAScriptable.has("value", checkBoxVSAScriptable));
      assertTrue(checkBoxVSAScriptable.has("titleVisible", checkBoxVSAScriptable));
   }

   @Test
   void testAddProperties() {
      checkBoxVSAScriptable.addProperties();

      assertEquals("CheckBox", checkBoxVSAScriptable.get("title", checkBoxVSAScriptable));
      assertEquals(true, checkBoxVSAScriptable.get("titleVisible", checkBoxVSAScriptable));
      assertEquals(false, checkBoxVSAScriptable.get("selectFirstItemOnLoad", checkBoxVSAScriptable));
   }

   @Test
   void testGetSetSelectFirstItem() {
      assertFalse(checkBoxVSAScriptable.isSelectFirstItem());
      checkBoxVSAScriptable.setSelectFirstItem(true);
      assertTrue(checkBoxVSAScriptable.isSelectFirstItem());
   }

   @Test
   void testSetSelectedObjects() {
      checkBoxVSAScriptable.setSelectedObjects(new Object[]{ "value1", "value2" });
      assertArrayEquals(new Object[]{ "value1", "value2" }, (Object[]) checkBoxVSAScriptable.getDefaultValue(String.class));

      //set Date type objects
      checkBoxVSAScriptable.setDataType("Date");
      Object[] objects = new Object[]{ new Date(125, 1, 20), new Date(125, 2, 20) };
      checkBoxVSAScriptable.setSelectedObjects(objects);
      assertArrayEquals(objects, (Object[]) checkBoxVSAScriptable.getDefaultValue(Date.class));
      assertArrayEquals(objects, checkBoxVSAssemblyInfo.getSelectedObjects());
   }
}