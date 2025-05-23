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
import inetsoft.uql.viewsheet.ListData;
import inetsoft.uql.viewsheet.RadioButtonVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class RadioButtonVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private RadioButtonVSAScriptable radioButtonVSAScriptable;
   private RadioButtonVSAssemblyInfo radioButtonVSAssemblyInfo;
   private RadioButtonVSAssembly radioButtonVSAssembly;
   private VSAScriptable vsaScriptable;
   private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      radioButtonVSAssembly = new RadioButtonVSAssembly();
      radioButtonVSAssemblyInfo = (RadioButtonVSAssemblyInfo) radioButtonVSAssembly.getVSAssemblyInfo();
      radioButtonVSAssemblyInfo.setName("RadioButton1");
      viewsheet.addAssembly(radioButtonVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      radioButtonVSAScriptable = new RadioButtonVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      radioButtonVSAScriptable.setAssembly("RadioButton1");
      vsaScriptable.setAssembly("RadioButton1");
   }

   @Test
   void testGetClassName() {
      assertEquals("RadioButtonVSA", radioButtonVSAScriptable.getClassName());
   }

   @Test
   void testGetSetCellValue() {
      radioButtonVSAScriptable.setCellValue(new Date(125, 1, 20));
      assert simpleDateFormat.format(radioButtonVSAScriptable.getCellValue()).equals("2025-02-20");
   }

   @Test
   void testGet() {
      assertNull(radioButtonVSAScriptable.get("value", radioButtonVSAScriptable));
      radioButtonVSAScriptable.setCellValue("value1");
      assertEquals("value1", radioButtonVSAScriptable.get("value", radioButtonVSAScriptable));
      assertEquals("RadioButton", radioButtonVSAScriptable.get("title", radioButtonVSAScriptable));
   }

   @Test
   void testHas() {
      assertFalse(radioButtonVSAScriptable.has("property1", radioButtonVSAScriptable));
      radioButtonVSAScriptable.setCellValue("value1");
      assertTrue(radioButtonVSAScriptable.has("value", radioButtonVSAScriptable));
      assertTrue(radioButtonVSAScriptable.has("titleVisible", radioButtonVSAScriptable));
   }

   @Test
   void testAddProperties() {
      radioButtonVSAScriptable.addProperties();

      assertEquals("RadioButton", radioButtonVSAScriptable.get("title", radioButtonVSAScriptable));
      assertEquals(true, radioButtonVSAScriptable.get("titleVisible", radioButtonVSAScriptable));
      assertNull(radioButtonVSAScriptable.get("value", radioButtonVSAScriptable));
   }

   @Test
   void testGetSetDataType() {
      //get datatype when no listdata
      assertNull(radioButtonVSAScriptable.getDataType());

      radioButtonVSAssemblyInfo.setListData(new ListData());
      radioButtonVSAScriptable.setDataType("String");
      assertEquals("String", radioButtonVSAScriptable.getDataType());
   }

   @Test
   void testGetSetValues() {
      //set values when no listdata
      radioButtonVSAScriptable.setValues(new Object[] { "value1", "value2" });
      assertNull(radioButtonVSAScriptable.getValues());

      //set values with String type
      radioButtonVSAssemblyInfo.setListData(new ListData());
      radioButtonVSAssemblyInfo.setRListData(new ListData());
      radioButtonVSAScriptable.setValues(new Object[] { "value1", "value2" });
      assertArrayEquals(new Object[] { "value1", "value2" }, radioButtonVSAScriptable.getValues());

      //set values with Date type
      radioButtonVSAScriptable.setDataType("Date");
      radioButtonVSAScriptable.setValues(new Object[] {
         new Date(125, 1, 20), new Date(125, 2, 20) });
      Object [] values = radioButtonVSAScriptable.getValues();

      String[] expectedDates = { "2025-02-20", "2025-03-20" };
      String[] actualDates = Arrays.stream(values)
         .map(obj -> simpleDateFormat.format((Date) obj))
         .toArray(String[]::new);
      assertArrayEquals(expectedDates, actualDates);
   }

   @Test
   void testGetSetLabels() {
      //set values when no listdata
      radioButtonVSAScriptable.setLabels(new String[] { "label1", "label2" });
      assertNull(radioButtonVSAScriptable.getLabels());

      radioButtonVSAssemblyInfo.setListData(new ListData());
      radioButtonVSAssemblyInfo.setRListData(new ListData());
      radioButtonVSAScriptable.setLabels(new String[] { "label1", "label2" });
      assertArrayEquals(new String[] { "label1", "label2" }, radioButtonVSAScriptable.getLabels());
   }

   @Test
   void testGetDefaultValue() {
      assertNull(radioButtonVSAScriptable.getDefaultValue(String.class));
      radioButtonVSAScriptable.setSelectedObject("value1");
      assertEquals("value1", radioButtonVSAScriptable.getDefaultValue(String.class));

      //set default value with Date type
      radioButtonVSAScriptable.setDataType("Date");
      radioButtonVSAScriptable.setSelectedObject(new Date(125, 1, 20));
      assertEquals("2025-02-20",
                   simpleDateFormat.format(radioButtonVSAScriptable.getDefaultValue(String.class)));
   }
}