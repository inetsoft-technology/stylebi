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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class TabVSAssemblyInfoTest {

   @Test
   void repositionForBottomTabsUsesDropdownCalendarTitleHeight() {
      Viewsheet vs = Mockito.mock(Viewsheet.class);

      // non-dropdown child at y=200 with height=100, bottom=300
      VSAssembly normalChild = mockChild("Normal1",
         new SelectionListVSAssemblyInfo(), new Point(50, 200), new Dimension(200, 100),
         SelectionVSAssemblyInfo.LIST_SHOW_TYPE);

      // dropdown calendar at y=282 with pixel height=18, title height=20
      CalendarVSAssemblyInfo calInfo = new CalendarVSAssemblyInfo();
      calInfo.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      calInfo.setTitleHeightValue(20);
      calInfo.setPixelOffset(new Point(50, 282));
      calInfo.setPixelSize(new Dimension(200, 18));
      VSAssembly calChild = Mockito.mock(VSAssembly.class);
      when(calChild.getVSAssemblyInfo()).thenReturn(calInfo);
      when(calChild.getPixelOffset()).thenReturn(new Point(50, 282));
      when(calChild.getPixelSize()).thenReturn(new Dimension(200, 18));

      when(vs.getAssembly("Normal1")).thenReturn(normalChild);
      when(vs.getAssembly("Calendar1")).thenReturn(calChild);

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setAssemblies(new String[]{"Normal1", "Calendar1"});
      tabInfo.setPixelOffset(new Point(50, 200));
      tabInfo.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo.repositionForBottomTabs(tabInfo, vs, true);

      // effective calendar height = titleHeight(20), so its "bottom" = 282 + 20 = 302
      // normal child bottom = 200 + 100 = 300
      // maxChildBottom = 302, so tab bar moves to y=302
      assertEquals(302, tabInfo.getPixelOffset().y);
      // calendar repositioned: 302 - 20 (title height) = 282
      assertEquals(282, calInfo.getPixelOffset().y);
   }

   @Test
   void repositionForBottomTabsUsesDropdownSelectionTitleHeight() {
      Viewsheet vs = Mockito.mock(Viewsheet.class);

      // dropdown selection list at y=380, pixel height=18, title height=25
      SelectionListVSAssemblyInfo selInfo = new SelectionListVSAssemblyInfo();
      selInfo.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      selInfo.setTitleHeightValue(25);
      selInfo.setPixelOffset(new Point(50, 380));
      selInfo.setPixelSize(new Dimension(200, 18));
      VSAssembly selChild = Mockito.mock(VSAssembly.class);
      when(selChild.getVSAssemblyInfo()).thenReturn(selInfo);
      when(selChild.getPixelOffset()).thenReturn(new Point(50, 380));
      when(selChild.getPixelSize()).thenReturn(new Dimension(200, 18));

      when(vs.getAssembly("Selection1")).thenReturn(selChild);

      TabVSAssemblyInfo tabInfo = new TabVSAssemblyInfo();
      tabInfo.setAssemblies(new String[]{"Selection1"});
      tabInfo.setPixelOffset(new Point(50, 380));
      tabInfo.setPixelSize(new Dimension(200, 20));

      TabVSAssemblyInfo.repositionForBottomTabs(tabInfo, vs, true);

      // effective height = titleHeight(25), bottom = 380 + 25 = 405
      // tab bar moves to y=405
      assertEquals(405, tabInfo.getPixelOffset().y);
      // selection repositioned: 405 - 25 = 380
      assertEquals(380, selInfo.getPixelOffset().y);
   }

   @Test
   void getBottomTabChildHeightReturnsPixelHeightForNonDropdown() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      Dimension size = new Dimension(200, 150);

      assertEquals(150, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));
   }

   @Test
   void getBottomTabChildHeightReturnsTitleHeightForDropdownCalendar() {
      CalendarVSAssemblyInfo info = new CalendarVSAssemblyInfo();
      info.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setTitleHeightValue(22);
      Dimension size = new Dimension(200, 18);

      assertEquals(22, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));
   }

   @Test
   void getBottomTabChildHeightReturnsTitleHeightForDropdownSelection() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setTitleHeightValue(25);
      Dimension size = new Dimension(200, 18);

      assertEquals(25, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));
   }

   @Test
   void getBottomTabChildHeightReturnsZeroForNullSize() {
      VSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      assertEquals(0, TabVSAssemblyInfo.getBottomTabChildHeight(info, null));
   }

   @Test
   void getBottomTabChildHeightIncludesTopLabelHeight() {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      LabelInfo labelInfo = info.getLabelInfo();
      labelInfo.setLabelVisibleValue("true");
      labelInfo.setLabelPositionValue(LabelInfo.TOP);
      labelInfo.setLabelGapValue(5);
      Dimension size = new Dimension(200, 20);

      int height = TabVSAssemblyInfo.getBottomTabChildHeight(info, size);
      int labelHeight = labelInfo.getRenderedHeight();
      assertEquals(20 + labelHeight + 5, height);
   }

   @Test
   void getBottomTabChildHeightIncludesBottomLabelHeight() {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      LabelInfo labelInfo = info.getLabelInfo();
      labelInfo.setLabelVisibleValue("true");
      labelInfo.setLabelPositionValue(LabelInfo.BOTTOM);
      labelInfo.setLabelGapValue(8);
      Dimension size = new Dimension(200, 20);

      int height = TabVSAssemblyInfo.getBottomTabChildHeight(info, size);
      int labelHeight = labelInfo.getRenderedHeight();
      assertEquals(20 + labelHeight + 8, height);
   }

   @Test
   void getBottomTabChildHeightIgnoresLeftRightLabel() {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      LabelInfo labelInfo = info.getLabelInfo();
      labelInfo.setLabelVisibleValue("true");
      labelInfo.setLabelPositionValue(LabelInfo.LEFT);
      Dimension size = new Dimension(200, 20);

      assertEquals(20, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));

      labelInfo.setLabelPositionValue(LabelInfo.RIGHT);
      assertEquals(20, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));
   }

   @Test
   void getBottomTabChildHeightIgnoresHiddenLabel() {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      LabelInfo labelInfo = info.getLabelInfo();
      labelInfo.setLabelVisibleValue("false");
      labelInfo.setLabelPositionValue(LabelInfo.TOP);
      Dimension size = new Dimension(200, 20);

      assertEquals(20, TabVSAssemblyInfo.getBottomTabChildHeight(info, size));
   }

   private VSAssembly mockChild(String name, SelectionBaseVSAssemblyInfo info,
                                Point offset, Dimension size, int showType)
   {
      info.setShowTypeValue(showType);
      info.setPixelOffset(offset);
      info.setPixelSize(size);
      VSAssembly child = Mockito.mock(VSAssembly.class);
      when(child.getVSAssemblyInfo()).thenReturn(info);
      when(child.getPixelOffset()).thenReturn(offset);
      when(child.getPixelSize()).thenReturn(size);
      return child;
   }
}
