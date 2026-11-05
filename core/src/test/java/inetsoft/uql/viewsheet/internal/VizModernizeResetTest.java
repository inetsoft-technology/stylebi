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

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.ChartFormatDefaults;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.TextFormat;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizModernizeResetTest {
   private static final int STRIP = 722;

   @BeforeEach
   void setUp() throws Exception {
      removeFormatCss();
   }

   @AfterEach
   void reset() throws Exception {
      removeFormatCss();
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private static void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private static void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static void writeFormatCss(String content) throws IOException {
      CSSDictionary.resetDictionaryCache();
      DataSpace.getDataSpace().withOutputStream(
         "portal", "format.css", out -> out.write(content.getBytes(StandardCharsets.UTF_8)));
      CSSDictionary.resetDictionaryCache();
   }

   // reset before deleting, so the dictionary's file listener cannot recreate it
   private static void removeFormatCss() throws Exception {
      CSSDictionary.resetDictionaryCache();
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      CSSDictionary.resetDictionaryCache();
   }

   /** An empty sheet as a legacy asset loads: nothing marked. */
   private static Viewsheet legacySheet() {
      gateOff();
      return new Viewsheet();
   }

   private static TextVSAssemblyInfo addText(Viewsheet vs, String name) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(text);
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   private static TableVSAssemblyInfo addTable(Viewsheet vs, String name) {
      TableVSAssembly table = new TableVSAssembly(vs, name);
      table.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(table);
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   private static ChartVSAssemblyInfo addChart(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static VSFormat user(VSAssemblyInfo info, TableDataPath path) {
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(path);

      if(fmt == null) {
         fmt = new VSCompositeFormat();
         info.getFormatInfo().setFormat(path, fmt);
      }

      return fmt.getUserDefinedFormat();
   }

   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   private static void authorLook(VSAssemblyInfo info) {
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setForegroundValue("0xff0000");
      user.setBackgroundValue("0x00ff00");
      user.setBordersValue(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                      StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
   }

   private static List<VSAssemblyInfo> ownContent(Viewsheet vs) {
      List<VSAssemblyInfo> infos = new java.util.ArrayList<>();
      infos.add(vs.getVSAssemblyInfo());

      for(Assembly assembly : vs.getAssemblies(true)) {
         infos.add(((VSAssembly) assembly).getVSAssemblyInfo());
      }

      return infos;
   }

   @Test
   void resetClearsAuthorLookOnMarkedAndUnmarkedAssemblies() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo legacy = addText(vs, "Text1");
      TextVSAssemblyInfo modern = addText(vs, "Text2");
      modern.setVizMark(VizMark.MODERN_LIGHT);
      authorLook(legacy);
      authorLook(modern);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      for(TextVSAssemblyInfo info : List.of(legacy, modern)) {
         VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
         assertFalse(user.isForegroundValueDefined(), info.getName());
         assertFalse(user.isBackgroundValueDefined(), info.getName());
         assertFalse(user.isBordersValueDefined(), info.getName());
      }
   }

   @Test
   void resetRefreshesAnOldReleaseDefaultFont() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      StyleFont old = new StyleFont("Arial", Font.PLAIN, 10);
      text.getFormat().getDefaultFormat().setFontValue(old);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      Font today = ChromeReset.freshDefaults(text, VizMark.MODERN_LIGHT).getFormat()
         .getDefaultFormat().getFontValue();
      assertNotEquals(old, today, "premise: today's default differs from the old one");
      assertEquals(today, text.getFormat().getDefaultFormat().getFontValue());
      assertEquals(today, text.getFormat().getDefaultFormat().getFont(),
                   "and nothing at the runtime tier shadows it");
   }

   @Test
   void aRenderCopyLeftInATitleDefaultDoesNotShadowTheRefresh() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      BorderColors red = new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED);
      user(table, VSAssemblyInfo.OBJECTPATH).setBorderColorsValue(red);
      // a render copies the object value into both tiers of the title default
      assertEquals(red, table.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH, false)
         .getBorderColors(), "premise");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      BorderColors today = ChromeReset.freshDefaults(table, VizMark.MODERN_LIGHT).getFormatInfo()
         .getFormat(VSAssemblyInfo.TITLEPATH, false).getBorderColors();
      assertNotEquals(red, today, "premise: a new table's title differs");
      assertEquals(today, table.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH, false)
         .getBorderColors());
   }

   @Test
   void aDefaultATypeWritesOnlyAtRuntimeSurvivesTheRefresh() {
      Viewsheet vs = legacySheet();
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TextInput1");
      input.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(input);
      VSAssemblyInfo info = input.getVSAssemblyInfo();

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      VSFormat fresh = ChromeReset.freshDefaults(info, VizMark.MODERN_LIGHT).getFormat()
         .getDefaultFormat();
      assertNull(fresh.getBorderColorsValue(), "premise: only the runtime value is written");
      assertNotNull(fresh.getBorderColors());
      assertEquals(fresh.getBorderColors(), info.getFormat().getDefaultFormat().getBorderColors());
   }

   @Test
   void resetKeepsWhatItMustKeep() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat user = user(table, VSAssemblyInfo.OBJECTPATH);
      user.setFormatValue("DecimalFormat", true);
      user.setFormatExtentValue("#,##0.00", true);
      user.setAlignmentValue(StyleConstants.H_RIGHT, true);
      user.setWrappingValue(true, true);
      table.setPixelOffset(new Point(40, 60));
      table.setPixelSize(new Dimension(321, 234));
      table.setTitleHeightValue(44);
      table.setUserTitleHeight(true);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertEquals("DecimalFormat", user.getFormatValue());
      assertEquals("#,##0.00", user.getFormatExtentValue());
      assertTrue(user.isAlignmentValueDefined());
      assertEquals(StyleConstants.H_RIGHT, user.getAlignmentValue() & StyleConstants.H_RIGHT);
      assertTrue(user.isWrappingValueDefined());
      assertEquals(new Point(40, 60), table.getPixelOffset());
      assertEquals(new Dimension(321, 234), table.getPixelSize());
      assertTrue(table.isUserTitleHeight());
      assertEquals(44, table.getTitleHeightValue());
   }

   @Test
   void resetKeepsExpressionBoundValues() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat user = user(text, VSAssemblyInfo.OBJECTPATH);
      user.setForegroundValue("=1");
      user.setBackgroundValue("0x00ff00");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertTrue(user.isForegroundValueDefined());
      assertEquals("=1", user.getForegroundValue());
      assertFalse(user.isBackgroundValueDefined(), "a plain value beside it is still cleared");
   }

   @Test
   void resetUsesTheSheetsOwnMark() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TextVSAssemblyInfo legacy = addText(vs, "Text1");
      legacy.setVizMark(null);
      addTable(vs, "Table1");
      vs.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);
      assertEquals(VizMark.MODERN_LIGHT, VizMark.fromGate(), "premise: the gate gives light");

      VizModernizeUtil.resetToModern(vs);

      for(VSAssemblyInfo info : ownContent(vs)) {
         assertEquals(VizMark.MODERN_DARK, info.getVizMark(), info.getName());
      }
   }

   @Test
   void resetFallsBackToTheGateAndDoesNothingWithItOff() {
      Viewsheet off = legacySheet();
      TextVSAssemblyInfo kept = addText(off, "Text1");
      authorLook(kept);

      assertEquals(0, VizModernizeUtil.resetToModern(off));
      assertTrue(user(kept, VSAssemblyInfo.OBJECTPATH).isForegroundValueDefined());
      assertNull(kept.getVizMark());
      assertNull(off.getVSAssemblyInfo().getVizMark());

      Viewsheet on = legacySheet();
      addText(on, "Text1");
      gateOn();

      assertEquals(2, VizModernizeUtil.resetToModern(on), "the text and the sheet itself");

      for(VSAssemblyInfo info : ownContent(on)) {
         assertEquals(VizMark.MODERN_LIGHT, info.getVizMark(), info.getName());
      }
   }

   @Test
   void resetLeavesEmbeddedAssembliesAlone() {
      Viewsheet vs = legacySheet();
      addText(vs, "Text1");
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setVizMark(null);
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      TextVSAssembly inner = new TextVSAssembly(embedded, "InnerText");
      inner.getVSAssemblyInfo().initDefaultFormat();
      embedded.addAssembly(inner);
      inner.getVSAssemblyInfo().setVizMark(null);
      vs.addAssembly(embedded);
      VSFormat innerUser = user(inner.getVSAssemblyInfo(), VSAssemblyInfo.OBJECTPATH);
      innerUser.setForegroundValue("0xff0000");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertEquals(VizMark.MODERN_LIGHT, embedded.getVSAssemblyInfo().getVizMark(),
                   "the container is this sheet's content");
      assertNull(inner.getVSAssemblyInfo().getVizMark());
      assertTrue(innerUser.isForegroundValueDefined(), "the embedded asset's content is kept");
      assertEquals("0xff0000", innerUser.getForegroundValue());
   }

   @Test
   void resetLeavesAShapesLookAlone() {
      Viewsheet vs = legacySheet();
      RectangleVSAssembly rect = new RectangleVSAssembly(vs, "Rect1");
      rect.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(rect);
      VSAssemblyInfo info = rect.getVSAssemblyInfo();
      info.getFormat().getDefaultFormat().setBackgroundValue("0x123456");
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setForegroundValue("0xff0000");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertEquals(VizMark.MODERN_LIGHT, info.getVizMark(), "stamped as Modernize does");
      assertEquals("0x123456", info.getFormat().getDefaultFormat().getBackgroundValue());
      assertEquals("0xff0000", user.getForegroundValue());
   }

   @Test
   void resetKeepsBottomTabsChildrenOnTheStrip() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, "SelA");
      vs.addAssembly(assembly);
      SelectionListVSAssemblyInfo list =
         (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
      list.initDefaultFormat();
      list.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      list.setPixelSize(new Dimension(100, 20));
      int legacyLane = list.getTitleHeight();
      list.setPixelOffset(new Point(78, STRIP - legacyLane));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setPixelOffset(new Point(78, STRIP));
      tabInfo.setPixelSize(new Dimension(400, 24));
      tabInfo.setBottomTabsValue(true);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[] { "SelA" });

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertNotEquals(legacyLane, list.getTitleHeight(), "premise: the lane moved");
      assertEquals(STRIP, list.getPixelOffset().y +
         TabVSAssemblyInfo.getBottomTabChildHeight(list, list.getPixelSize()));
   }

   @Test
   void aDataLabelKeepsItsSizeAndStyleInTodaysFamily() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      user(chart, VSAssemblyInfo.OBJECTPATH).setFontValue(new StyleFont("Arial", Font.PLAIN, 12));
      plot(chart).getTextFormat().getUserDefinedFormat()
         .setFont(new StyleFont("Arial", Font.BOLD, 16), true);
      // as a render leaves it: the object font copied into the label default
      ChartFormatDefaults.fix(chart);
      assertEquals("Arial", FormatChromeSlots.familyOf(
         plot(chart).getTextFormat().getDefaultFormat().getFont()), "premise");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      TextFormat label = plot(chart).getTextFormat().getUserDefinedFormat();
      Font font = label.getFont();
      String today = FormatChromeSlots.familyOf(
         plot(chart).getTextFormat().getDefaultFormat().getFont());
      assertFalse(user(chart, VSAssemblyInfo.OBJECTPATH).isFontValueDefined(),
                  "a chart's object font resets whole");
      assertTrue(label.isFontDefined());
      assertNotEquals("Arial", FormatChromeSlots.familyOf(font));
      assertEquals(today, FormatChromeSlots.familyOf(font));
      assertEquals(Font.BOLD, font.getStyle());
      assertEquals(16, font.getSize());
   }

   @Test
   void aTypeWhoseSeedWritesNoPaddingTakesTheFreshPadding() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      Insets author = new Insets(20, 20, 20, 20);
      text.setPadding(author);
      text.setUserPadding(true);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      Insets fresh = ChromeReset.freshDefaults(text, VizMark.MODERN_LIGHT).getPadding();
      assertNotEquals(author, fresh, "premise");
      assertFalse(text.isUserPadding());
      assertEquals(fresh, text.getPadding());
   }

   @Test
   void aCssClassPaddingStillAppliesAfterTheReset() throws Exception {
      writeFormatCss("Text.card { padding: 7px; }");
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      text.getFormat().getCSSFormat().setCSSClass("card");
      text.setPadding(new Insets(20, 20, 20, 20));
      text.setUserPadding(true);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertFalse(text.isUserPadding());
      assertEquals(new Insets(7, 7, 7, 7), text.getPadding());
   }

   @Test
   void aRuntimeFontLeftByALoadFollowsTheReset() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat user = user(text, VSAssemblyInfo.OBJECTPATH);
      StyleFont author = new StyleFont("Arial", Font.BOLD, 14);
      user.setFontValue(author);
      user.setFont(author, true);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      Font design = user.getFontValue();
      Font shown = text.getFormat().getFont();
      assertNotEquals("Arial", FormatChromeSlots.familyOf(design), "premise: the family moved");
      assertEquals(FormatChromeSlots.familyOf(design), FormatChromeSlots.familyOf(shown));
      assertEquals(Font.BOLD, shown.getStyle());
      assertEquals(14, shown.getSize());
   }

   @Test
   void aRuntimeOnlyAuthorFontIsClearedOnBothTiers() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat user = user(text, VSAssemblyInfo.OBJECTPATH);
      user.setFont(new StyleFont("Arial", Font.BOLD, 14), true);
      assertFalse(user.isFontValueDefined(), "premise: no design font");
      assertEquals("Arial", FormatChromeSlots.familyOf(text.getFormat().getFont()), "premise");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      Font today = text.getFormat().getDefaultFormat().getFontValue();
      Font shown = text.getFormat().getFont();
      assertFalse(user.isFontDefined());
      assertFalse(user.isFontValueDefined());
      assertEquals(FormatChromeSlots.familyOf(today), FormatChromeSlots.familyOf(shown));
      assertEquals(today.getStyle(), shown.getStyle());
      assertEquals(today.getSize(), shown.getSize());
   }

   @Test
   void theRefreshKeepsDefaultAlignmentAndNumberFormat() {
      Viewsheet vs = legacySheet();
      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat def = text.getFormat().getDefaultFormat();
      int align = StyleConstants.H_RIGHT | StyleConstants.V_BOTTOM;
      def.setAlignmentValue(align);
      def.setFormatValue("DecimalFormat");
      def.setFormatExtentValue("#,##0.00");
      VSFormat fresh = ChromeReset.freshDefaults(text, VizMark.MODERN_LIGHT).getFormat()
         .getDefaultFormat();
      assertNotEquals(align, fresh.getAlignmentValue(), "premise");
      assertNotEquals("DecimalFormat", fresh.getFormatValue(), "premise");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      VSFormat after = text.getFormat().getDefaultFormat();
      assertEquals(align, after.getAlignmentValue());
      assertEquals("DecimalFormat", after.getFormatValue());
      assertEquals("#,##0.00", after.getFormatExtentValue());
   }

   @Test
   void aPathTheAssemblyLacksGetsACompositeWithOnlyTheLook() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      table.getFormatInfo().setFormat(VSAssemblyInfo.TITLEPATH, null);
      VSCompositeFormat object = table.getFormat();

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      VSCompositeFormat fresh = ChromeReset.freshDefaults(table, VizMark.MODERN_LIGHT)
         .getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      VSCompositeFormat title = table.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      assertNotNull(title, "created");
      assertNotNull(fresh.getDefaultFormat().getFontValue(), "premise");
      assertEquals(fresh.getDefaultFormat().getFontValue(),
                   title.getDefaultFormat().getFontValue());
      assertTrue(fresh.getDefaultFormat().isAlignmentValueDefined(), "premise");
      assertFalse(title.getDefaultFormat().isAlignmentValueDefined(), "not on the allowlist");
      assertNull(title.getCSSFormat().getCSSType(), "no CSS type copied");
      assertFalse(title.getUserDefinedFormat().isDefined(), "no author value");
      assertSame(object, table.getFormat(), "an existing composite is never replaced");
   }

   @Test
   void aDropdownCalendarKeepsItsDropdownDefaults() {
      Viewsheet vs = legacySheet();
      CalendarVSAssembly assembly = new CalendarVSAssembly(vs, "Calendar1");
      vs.addAssembly(assembly);
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.initDefaultFormat();
      // as the property dialog switches a new calendar to a dropdown
      CalendarVSAssemblyInfo dropdown = (CalendarVSAssemblyInfo) info.clone();
      dropdown.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.copyViewInfo(dropdown, true);
      assertEquals("0xffffff", info.getFormat().getDefaultFormat().getBackgroundValue(),
                   "premise: the dropdown prototype");
      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue(), "premise");

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      VSFormat object = info.getFormat().getDefaultFormat();
      assertEquals("0xffffff", object.getBackgroundValue());
      assertEquals(0, object.getRoundCornerValue());

      for(TableDataPath path : new TableDataPath[] {
         CalendarVSAssemblyInfo.CALENDAR_TITLE_PATH, CalendarVSAssemblyInfo.CALENDAR_YEAR_PATH,
         CalendarVSAssemblyInfo.CALENDAR_MONTH_PATH })
      {
         VSCompositeFormat fmt = info.getFormatInfo().getFormat(path);
         assertTrue(fmt == null || fmt.getDefaultFormat().getFontValue() == null &&
                    fmt.getDefaultFormat().getBordersValue() == null,
                    "no normal-calendar look at " + path);
      }
   }

   @Test
   void aSelectionInAContainerKeepsItsChildBorder() {
      Viewsheet vs = legacySheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(container);
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelA");
      vs.addAssembly(list);
      VSAssemblyInfo info = list.getVSAssemblyInfo();
      info.initDefaultFormat();
      container.setAssemblies(new String[] { "SelA" });
      // as grouping into the container writes it
      CurrentSelectionVSAssemblyInfo.setChildBorder(info);

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      assertEquals(new Insets(1, 0, 1, 0), info.getFormat().getDefaultFormat().getBordersValue());
   }

   @Test
   void aChartObjectFontResetsWholeSoChartTextTakesTodaysDefault() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      user(chart, VSAssemblyInfo.OBJECTPATH).setFontValue(new StyleFont("Arial", Font.BOLD, 16));
      ChartVSAssemblyInfo control = addChart(vs, "Chart2");

      gateOn();
      VizModernizeUtil.resetToModern(vs);
      // as the next render does
      ChartFormatDefaults.fix(chart);
      ChartFormatDefaults.fix(control);

      assertNotEquals(16, axisLabelFont(control).getSize(), "premise: not the author's size");
      assertSameFont(axisLabelFont(control), axisLabelFont(chart), "axis labels");
      assertSameFont(legendTitleFont(control), legendTitleFont(chart), "legend title");
      assertSameFont(titleFont(control), titleFont(chart), "chart title");
      assertFalse(user(chart, VSAssemblyInfo.OBJECTPATH).isFontValueDefined());
   }

   @Test
   void aTableTitleTakesTodaysFontWhileCellsKeepTheObjectSize() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      user(table, VSAssemblyInfo.OBJECTPATH).setFontValue(new StyleFont("Arial", Font.BOLD, 16));

      gateOn();
      VizModernizeUtil.resetToModern(vs);

      VSAssemblyInfo fresh = ChromeReset.freshDefaults(table, VizMark.MODERN_LIGHT);
      Font freshTitle = fresh.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH)
         .getDefaultFormat().getFontValue();
      assertNotEquals(16, freshTitle.getSize(), "premise");
      assertSameFont(freshTitle, titleFont(table), "title");

      Font cell = table.getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.DETAIL), false).getFont();
      String today =
         FormatChromeSlots.familyOf(fresh.getFormat().getDefaultFormat().getFontValue());
      assertEquals(today, FormatChromeSlots.familyOf(cell));
      assertEquals(Font.BOLD, cell.getStyle());
      assertEquals(16, cell.getSize());
   }

   private static Font axisLabelFont(ChartVSAssemblyInfo chart) {
      return chart.getVSChartInfo().getAxisDescriptor().getAxisLabelTextFormat().getFont();
   }

   private static Font legendTitleFont(ChartVSAssemblyInfo chart) {
      return chart.getChartDescriptor().getLegendsDescriptor().getTitleTextFormat().getFont();
   }

   private static Font titleFont(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH, false).getFont();
   }

   private static void assertSameFont(Font expected, Font actual, String what) {
      assertEquals(FormatChromeSlots.familyOf(expected), FormatChromeSlots.familyOf(actual), what);
      assertEquals(expected.getStyle(), actual.getStyle(), what);
      assertEquals(expected.getSize(), actual.getSize(), what);
   }
}
