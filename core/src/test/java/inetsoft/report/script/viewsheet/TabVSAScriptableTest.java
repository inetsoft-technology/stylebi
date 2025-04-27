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
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class TabVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private TabVSAScriptable tabVSAScriptable;
   private TabVSAssemblyInfo tabVSAssemblyInfo;
   private TabVSAssembly tabVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      tabVSAssembly = new TabVSAssembly();
      tabVSAssemblyInfo = (TabVSAssemblyInfo) tabVSAssembly.getVSAssemblyInfo();
      tabVSAssemblyInfo.setName("Tab1");
      viewsheet.addAssembly(tabVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      tabVSAScriptable = new TabVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      tabVSAScriptable.setAssembly("Tab1");
      vsaScriptable.setAssembly("Tab1");
   }

   @Test
   void testGetClassName() {
      assertEquals("TabVSA", tabVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      tabVSAScriptable.addProperties();
      assertEquals(true, tabVSAScriptable.get("visible", tabVSAScriptable));
   }

   @Test
   void testGetSetSelectedValue() {
      tabVSAScriptable.setSelectedValue("value1");
      assertEquals("value1", tabVSAScriptable.getSelected());
      tabVSAScriptable.setSelectedValue("value2.value3");
      assertEquals("value3", tabVSAScriptable.getSelected());
   }

   @Test
   void testGetSetSelectedIndex() {
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1", "Gauge1", "Chart1"});
      assertEquals(-1, tabVSAScriptable.getSelectedIndex());
      tabVSAScriptable.setSelectedIndex(1);
      assertEquals(1, tabVSAScriptable.getSelectedIndex());
      tabVSAScriptable.setSelectedIndexValue(2);
      assertEquals(2, tabVSAScriptable.getSelectedIndex());

      //invalid index
      tabVSAScriptable.setSelectedIndex(-3);
      assertEquals(0, tabVSAScriptable.getSelectedIndex());

      RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
         tabVSAScriptable.setSelectedIndex(3);
      });
      assertEquals("Index 3 out of bounds for length 3", runtimeException.getMessage());
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1", "Gauge1"});
      Dimension size1 = new Dimension(180, 70);
      tabVSAScriptable.setSize(size1);
      assertEquals(size1, tabVSAScriptable.getSize());
   }
}