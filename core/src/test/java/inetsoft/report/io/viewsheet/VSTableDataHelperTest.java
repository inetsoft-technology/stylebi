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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
   @Test
   void applyTableBordersKeepsStyleBordersOnStyledTable() {
      // #79754: the default 1px object border must not overwrite the style border
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      setDefaultBorder(info);
      VSFormat cellfmt = styleBorderFormat();

      helper().applyTableBorders(cellfmt, info, true, true, true, true);

      assertEquals(new Insets(4, 4, 4, 4), cellfmt.getBorders());
      assertEquals(Color.RED, cellfmt.getBorderColors().leftColor);
      assertEquals(Color.BLUE, cellfmt.getBorderColors().rightColor);
   }

   @Test
   void applyTableBordersUsesUserBorderOnStyledTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      setDefaultBorder(info);
      info.getFormat().getUserDefinedFormat().setBorders(new Insets(3, 3, 3, 3));
      info.getFormat().getUserDefinedFormat().setBorderColors(
         new BorderColors(Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN));
      VSFormat cellfmt = styleBorderFormat();

      helper().applyTableBorders(cellfmt, info, true, true, true, true);

      assertEquals(new Insets(3, 3, 3, 3), cellfmt.getBorders());
      assertEquals(Color.GREEN, cellfmt.getBorderColors().leftColor);
      assertEquals(Color.GREEN, cellfmt.getBorderColors().rightColor);
   }

   @Test
   void applyTableBordersOnlyOverridesUserSetSidesOnStyledTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      setDefaultBorder(info);
      info.getFormat().getUserDefinedFormat().setBordersValue(new Insets(0, 0, 3, 0));
      VSFormat cellfmt = styleBorderFormat();

      helper().applyTableBorders(cellfmt, info, true, true, true, true);

      assertEquals(new Insets(4, 4, 3, 4), cellfmt.getBorders());
   }

   @Test
   void tableStyleCssBorderIsNotUserObjectBorder() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      setDefaultBorder(info);
      // setDefaultFormat() writes a CSS TableStyle border color into the default layer
      info.getFormat().getDefaultFormat().setBorderColorsValue(
         new BorderColors(Color.ORANGE, Color.ORANGE, Color.ORANGE, Color.ORANGE));

      assertFalse(VSTableDataHelper.hasUserObjectBorder(info));
      VSFormat cellfmt = styleBorderFormat();
      helper().applyTableBorders(cellfmt, info, true, true, true, true);
      assertEquals(new Insets(4, 4, 4, 4), cellfmt.getBorders());
   }

   @Test
   void writeDrawsObjectBorderOverlayForStyledTableWithUserBorder() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      info.setTitleVisible(false);
      info.setPixelSize(new Dimension(400, 220));
      setDefaultBorder(info);
      info.getFormat().getUserDefinedFormat().setBorders(new Insets(3, 3, 3, 3));

      TableDataVSAssembly assembly = Mockito.mock(TableDataVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getInfo()).thenReturn(info);

      VSTableDataHelper helper = helper();
      helper.write(assembly, null);

      Mockito.verify(helper, Mockito.times(1)).drawObjectFormat(info, null, true);
   }

   @Test
   void writeOverlayForStyledTableWithPartialUserBorderOnlyHasThatSide() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      setDefaultBorder(info);
      info.getFormat().getUserDefinedFormat().setBordersValue(new Insets(0, 0, 3, 0));

      assertTrue(VSTableDataHelper.hasUserObjectBorder(info));
      assertEquals(new Insets(0, 0, 3, 0), info.getFormat().getBorders());
   }

   @Test
   void applyTableBordersMergesObjectBorderOnUnstyledTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle(null);
      info.getFormat().getUserDefinedFormat().setBorders(new Insets(1, 1, 1, 1));
      info.getFormat().getUserDefinedFormat().setBorderColors(
         new BorderColors(Color.GREEN, Color.GREEN, Color.GREEN, Color.GREEN));
      VSFormat cellfmt = styleBorderFormat();

      helper().applyTableBorders(cellfmt, info, true, false, true, false);

      // left/right flags set: replaced by the object border
      assertEquals(1, cellfmt.getBorders().left);
      assertEquals(1, cellfmt.getBorders().right);
      assertEquals(Color.GREEN, cellfmt.getBorderColors().leftColor);
      assertEquals(Color.GREEN, cellfmt.getBorderColors().rightColor);
      // top/bottom flags not set: left as the cell had them
      assertEquals(4, cellfmt.getBorders().top);
      assertEquals(4, cellfmt.getBorders().bottom);
   }

   @Test
   void writeSkipsObjectBorderOverlayForStyledTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle("style1");
      info.setTitleVisible(false);
      info.setPixelSize(new Dimension(400, 220));

      TableDataVSAssembly assembly = Mockito.mock(TableDataVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getInfo()).thenReturn(info);

      VSTableDataHelper helper = helper();
      helper.write(assembly, null);

      Mockito.verify(helper, Mockito.times(1)).drawObjectFormat(info, null, false);
      Mockito.verify(helper, Mockito.never()).drawObjectFormat(info, null, true);
   }

   @Test
   void writeDrawsObjectBorderOverlayForUnstyledTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setTableStyle(null);
      info.setTitleVisible(false);
      info.setPixelSize(new Dimension(400, 220));

      TableDataVSAssembly assembly = Mockito.mock(TableDataVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getInfo()).thenReturn(info);

      VSTableDataHelper helper = helper();
      helper.write(assembly, null);

      Mockito.verify(helper, Mockito.times(1)).drawObjectFormat(info, null, false);
      Mockito.verify(helper, Mockito.times(1)).drawObjectFormat(info, null, true);
   }

   private VSTableDataHelper helper() {
      return Mockito.mock(VSTableDataHelper.class, Mockito.CALLS_REAL_METHODS);
   }

   // the default object border (1px #dadada), written by hand because initDefaultFormat()
   // needs a LibManagerProvider bean that is not available here
   private void setDefaultBorder(VSAssemblyInfo info) {
      VSFormat def = info.getFormat().getDefaultFormat();
      def.setBordersValue(new Insets(1, 1, 1, 1));
      Color c = VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      def.setBorderColorsValue(new BorderColors(c, c, c, c));
   }

   private VSFormat styleBorderFormat() {
      VSFormat fmt = new VSFormat();
      fmt.setBorders(new Insets(4, 4, 4, 4));
      fmt.setBorderColors(new BorderColors(Color.RED, Color.RED, Color.RED, Color.BLUE));
      return fmt;
   }
}
