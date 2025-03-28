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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class ComboBoxVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private ComboBoxVSAScriptable comboBoxVSAScriptable;
   private ComboBoxVSAssemblyInfo comboBoxVSAssemblyInfo;
   private ComboBoxVSAssembly comboBoxVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      comboBoxVSAssembly = new ComboBoxVSAssembly();
      comboBoxVSAssemblyInfo = (ComboBoxVSAssemblyInfo) comboBoxVSAssembly.getVSAssemblyInfo();
      comboBoxVSAssemblyInfo.setName("ComboBox1");
      viewsheet.addAssembly(comboBoxVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      comboBoxVSAScriptable = new ComboBoxVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      comboBoxVSAScriptable.setAssembly("ComboBox1");
      vsaScriptable.setAssembly("ComboBox1");
   }

   @Test
   void testGetClassName() {
      assertEquals("ComboBoxVSA", comboBoxVSAScriptable.getClassName());
   }

   @Test
   void tetGet() {
      assertEquals(false, comboBoxVSAScriptable.get("serverTimeZone", comboBoxVSAScriptable));
   }

   @Test
   void tetHas() {
      assertFalse(comboBoxVSAScriptable.has("property1", comboBoxVSAScriptable));
      assertTrue(comboBoxVSAScriptable.has("serverTimeZone", comboBoxVSAScriptable));
   }

   @Test
   void testAddProperties() {
      comboBoxVSAScriptable.addProperties();
      assertEquals(false, comboBoxVSAScriptable.get("serverTimeZone", comboBoxVSAScriptable));
   }

   @Test
   void testGetSetValues() {
      //check when no listdata
      comboBoxVSAScriptable.setValues(new Object[]{ "value1", "value2" });
      assertNull(comboBoxVSAScriptable.getValues());

      //check set string values
      comboBoxVSAssemblyInfo.setListData(new ListData());
      comboBoxVSAssemblyInfo.setRListData(new ListData());
      comboBoxVSAScriptable.setValues(new Object[]{ "value1", "value2" });
      assertArrayEquals(new Object[]{ "value1", "value2" }, comboBoxVSAScriptable.getValues());

      //check selected object values
      assertNull(comboBoxVSAScriptable.getSelectedObject());
      comboBoxVSAScriptable.setSelectedObject("value2");
      assertEquals("value2", comboBoxVSAScriptable.getSelectedObject());

      //calendar combobox
      comboBoxVSAScriptable.setDataType("Date");
      comboBoxVSAssemblyInfo.setCalendar(true);
      Object[] values = new Object[]{ new Date(125, 1, 20), new Date(125, 2, 20) };
      comboBoxVSAScriptable.setValues(values);
      assertArrayEquals(values, comboBoxVSAScriptable.getValues());
      comboBoxVSAScriptable.setSelectedObject(new Date(125, 1, 20));
      assertEquals(new Date(125, 1, 20), comboBoxVSAScriptable.getSelectedObject());
   }
}