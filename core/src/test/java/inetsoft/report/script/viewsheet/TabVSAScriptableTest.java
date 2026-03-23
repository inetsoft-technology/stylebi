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
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TabVSAScriptableTest {
   private Viewsheet viewsheet;
   private ViewsheetSandbox viewsheetSandbox;
   private TabVSAScriptable tabVSAScriptable;
   private TabVSAssemblyInfo tabVSAssemblyInfo;
   private TabVSAssembly tabVSAssembly;

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      tabVSAssembly = new TabVSAssembly();
      tabVSAssemblyInfo = (TabVSAssemblyInfo) tabVSAssembly.getVSAssemblyInfo();
      tabVSAssemblyInfo.setName("Tab1");
      viewsheet.addAssembly(tabVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      tabVSAScriptable = new TabVSAScriptable(viewsheetSandbox);
      tabVSAScriptable.setAssembly("Tab1");
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

   @Test
   void testSetSizeTopTabsShiftsChildrenY() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 50));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // Grow height by 20; in top-tabs mode the child should shift down by the same amount.
      tabVSAScriptable.setSize(new Dimension(180, 50));

      assertEquals(70, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetSizeBottomTabsDoesNotShiftChildrenY() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 50));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      tabVSAssemblyInfo.setBottomTabs(true);

      // Grow height by 20; in bottom-tabs mode the child Y must remain unchanged.
      tabVSAScriptable.setSize(new Dimension(180, 50));

      assertEquals(50, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsRepositionsChildren() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));  // flush below tab (tab Y=30 + tabHeight=30)
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      tabVSAScriptable.setBottomTabs(true);

      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      // Tab bar should have moved below the child's bottom edge (60 + 100 = 160)
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      // Child stays in place; bottom edge (60 + 100 = 160) flush with tab top
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsToTopRepositionsChildren() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 30));   // child above tab bar
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 130));  // tab bar below child (30 + 100)
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      tabVSAssemblyInfo.setBottomTabs(true);

      tabVSAScriptable.setBottomTabs(false);

      assertFalse(tabVSAssemblyInfo.isBottomTabs());
      // Tab bar should have moved above the child's top edge (30 - 30 = 0)
      assertEquals(0, tabVSAssemblyInfo.getPixelOffset().y);
      // Child stays in place; top edge (30) flush with tab bottom (0 + 30 = 30)
      assertEquals(30, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @ParameterizedTest
   @CsvSource({
      "labels, []",
      "visible, ''"
   })
   void testGetSuffix(String propertyName, String expectedValue) {
      assertEquals(expectedValue, tabVSAScriptable.getSuffix(propertyName));
   }
}
