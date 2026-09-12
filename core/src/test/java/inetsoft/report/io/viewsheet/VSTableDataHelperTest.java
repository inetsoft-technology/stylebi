/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.io.viewsheet;

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class VSTableDataHelperTest {

   @Test
   void applyShrunkBottomTabsShiftShiftsBothOffsetAndLayoutPosition() {
      TableDataVSAssembly assembly = bottomTabsTable(/*shrink*/ true, /*maxSize*/ null);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));
      info.setLayoutPosition(new Point(40, 80));

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 100);

      // shift = designH - actualH = 220 - 100 = 120
      assertEquals(220, info.getPixelOffset().y);   // 100 + 120
      assertEquals(200, info.getLayoutPosition().y); // 80 + 120
   }

   @Test
   void applyShrunkBottomTabsShiftNoopWhenNotShrink() {
      TableDataVSAssembly assembly = bottomTabsTable(/*shrink*/ false, null);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 100);

      assertEquals(100, info.getPixelOffset().y);
   }

   @Test
   void applyShrunkBottomTabsShiftNoopWhenMaxMode() {
      TableDataVSAssembly assembly = bottomTabsTable(true, new Dimension(800, 600));
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 100);

      assertEquals(100, info.getPixelOffset().y);
   }

   @Test
   void applyShrunkBottomTabsShiftNoopWhenNotInBottomTabs() {
      TableDataVSAssembly assembly = topTabsTable(true);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 100);

      assertEquals(100, info.getPixelOffset().y);
   }

   @Test
   void applyShrunkBottomTabsShiftNoopWhenShiftNonPositive() {
      TableDataVSAssembly assembly = bottomTabsTable(true, null);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));

      // actual >= design → shift <= 0 → no-op
      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 220);
      assertEquals(100, info.getPixelOffset().y);

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 300);
      assertEquals(100, info.getPixelOffset().y);
   }

   @Test
   void applyShrunkBottomTabsShiftHandlesNullAssembly() {
      // does not throw
      VSTableDataHelper.applyShrunkBottomTabsShift((TableDataVSAssembly) null, 220, 100);
   }

   @Test
   void applyShrunkBottomTabsShiftHandlesNullLayoutPosition() {
      TableDataVSAssembly assembly = bottomTabsTable(true, null);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(40, 100));
      info.setLayoutPosition(null);

      VSTableDataHelper.applyShrunkBottomTabsShift(assembly, 220, 100);

      assertEquals(220, info.getPixelOffset().y);
      assertNull(info.getLayoutPosition());
   }

   private TableDataVSAssembly bottomTabsTable(boolean shrink, Dimension maxSize) {
      return tabChild(shrink, maxSize, /*bottomTabs*/ true);
   }

   private TableDataVSAssembly topTabsTable(boolean shrink) {
      return tabChild(shrink, null, /*bottomTabs*/ false);
   }

   private TableDataVSAssembly tabChild(boolean shrink, Dimension maxSize, boolean bottomTabs) {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setShrink(shrink);
      info.setMaxSize(maxSize);
      info.setPixelSize(new Dimension(400, 220));

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setBottomTabsValue(bottomTabs);
      TabVSAssembly tab = Mockito.mock(TabVSAssembly.class);
      when(tab.getVSAssemblyInfo()).thenReturn(tabInfo);

      TableDataVSAssembly assembly = Mockito.mock(TableDataVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getContainer()).thenReturn(tab);
      when(assembly.getAbsoluteName()).thenReturn("TableView1");

      return assembly;
   }
}
