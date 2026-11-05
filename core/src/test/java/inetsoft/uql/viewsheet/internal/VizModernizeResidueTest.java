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

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.ChartFormatDefaults;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.TextFormat;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VizModernizeResidueTest {
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

   private static TextVSAssemblyInfo addText(Viewsheet vs, String name) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(text);
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
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

   private static TextFormat axisUser(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getAxisDescriptor().getAxisLabelTextFormat()
         .getUserDefinedFormat();
   }

   /** The axis label font a render of this unmarked chart gives when no author font is set. */
   private static Font classicAxisFont(ChartVSAssemblyInfo info) {
      ChartVSAssemblyInfo probe = (ChartVSAssemblyInfo) info.clone();
      ChartFormatDefaults.fix(probe);
      return probe.getVSChartInfo().getAxisDescriptor().getAxisLabelTextFormat()
         .getDefaultFormat().getFont();
   }

   @Test
   void aTitleBackgroundEqualToTheClassicValueIsDropped() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat title = user(table, VSAssemblyInfo.TITLEPATH);
      title.setBackgroundValue(VSAssemblyInfo.DEFAULT_TITLE_BG);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertFalse(title.isBackgroundValueDefined(),
                  "the classic title band equals what Revert seeds, so it is residue");
   }

   @Test
   void aRealTitleBackgroundSurvives() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat title = user(table, VSAssemblyInfo.TITLEPATH);
      title.setBackgroundValue("0xff0000");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(title.isBackgroundValueDefined());
      assertEquals("0xff0000", title.getBackgroundValue());
   }

   @Test
   void todaysClassicGridlineIsDroppedAnOlderGreyIsKept() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo a = addChart(vs, "ChartA");
      ChartVSAssemblyInfo b = addChart(vs, "ChartB");
      Color grey = new Color(0xd2d2d2);
      plot(a).setXGridColor(new Color(0xeeeeee), CompositeValue.Type.USER);
      plot(b).setXGridColor(grey, CompositeValue.Type.USER);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertFalse(plot(a).chromeValue("xGridColor").hasUserValue(),
                  "today's classic gridline is residue");
      assertTrue(plot(b).chromeValue("xGridColor").hasUserValue(),
                 "an older release's grey is not today's classic value, so it is kept");
      assertEquals(grey, plot(b).chromeValue("xGridColor").get(CompositeValue.Type.USER));
   }

   @Test
   void anAxisFontEqualToItsClassicDefaultIsDropped() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      Font classic = classicAxisFont(chart);
      assertNotNull(classic);
      axisUser(chart).setFont(classic, true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertFalse(axisUser(chart).isFontDefined(),
                  "the axis label default is re-initialised at render, and this equals it");
   }

   @Test
   void anAxisFontIsKeptWhenTheObjectFontIsARealChoice() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      Font classic = classicAxisFont(chart);
      axisUser(chart).setFont(classic, true);
      VSFormat object = user(chart, VSAssemblyInfo.OBJECTPATH);
      object.setFontValue(new StyleFont("Arial", Font.BOLD, 14));

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(object.isFontValueDefined(), "a real object font is not residue");
      assertTrue(axisUser(chart).isFontDefined(),
                 "without its own font the axis would inherit the object's, so it is kept");
      assertEquals(classic, axisUser(chart).getFont());
   }

   @Test
   void aValueOverridingACssValueIsNotResidue() throws Exception {
      writeFormatCss("ChartPlot { line_x_color: #123456; }");
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      plot(chart).setXGridColor(new Color(0xeeeeee), CompositeValue.Type.USER);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(plot(chart).chromeValue("xGridColor").hasUserValue(),
                 "the classic look shows the CSS colour, which this value overrides");
   }

   @Test
   void anExpressionIsNeverResidue() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat title = user(table, VSAssemblyInfo.TITLEPATH);
      title.setForegroundValue("=1");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(title.isForegroundValueDefined());
      assertEquals("=1", title.getForegroundValue());
   }

   @Test
   void tableCellsAreNeverResidue() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      TableDataPath header = new TableDataPath(-1, TableDataPath.HEADER);
      VSFormat cell = user(table, header);
      table.getFormatInfo().getFormat(header).getDefaultFormat().setForegroundValue("0x123456");
      cell.setForegroundValue("0x123456");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(cell.isForegroundValueDefined(),
                 "a cell's classic value comes from the table style at render");
   }

   @Test
   void anObjectFontATitleInheritsIsKept() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat object = user(table, VSAssemblyInfo.OBJECTPATH);
      // the object's own classic font, which the title takes in place of its bold default
      object.setFontValue(VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11));

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(object.isFontValueDefined(),
                 "clearing it would turn the title bold, so it is not residue");
   }

   @Test
   void aTableKeepsItsObjectFontAndAlpha() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat object = user(table, VSAssemblyInfo.OBJECTPATH);
      object.setAlphaValue(100);
      object.setFontValue(VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11));
      // an own title font, so the title no longer reads the object's
      user(table, VSAssemblyInfo.TITLEPATH).setFontValue(new StyleFont("Arial", Font.BOLD, 12));
      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat textObject = user(text, VSAssemblyInfo.OBJECTPATH);
      textObject.setAlphaValue(100);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertFalse(textObject.isAlphaValueDefined(), "elsewhere an opaque alpha is residue");
      assertTrue(object.isAlphaValueDefined(), "without it cells defer to the table style");
      assertTrue(object.isFontValueDefined(), "without it cells defer to the table style");
   }

   @Test
   void aPaddingIsComparedWithTheCssPadding() throws Exception {
      writeFormatCss("Chart { padding: 5px; }");
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo other = addChart(vs, "ChartA");
      other.setPadding(new Insets(20, 20, 20, 20));
      other.setUserPadding(true);
      ChartVSAssemblyInfo same = addChart(vs, "ChartB");
      same.setPadding(new Insets(5, 5, 5, 5));
      same.setUserPadding(true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(other.isUserPadding(), "the classic padding is the CSS one, which this overrides");
      assertEquals(new Insets(20, 20, 20, 20), other.getPadding());
      assertFalse(same.isUserPadding(), "equal to the CSS padding, so residue");
   }

   @Test
   void aRangeSliderPaddingIsNeverResidue() throws Exception {
      writeFormatCss("RangeSlider { padding: 5px; }");
      Viewsheet vs = legacySheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      info.initDefaultFormat();
      vs.addAssembly(slider);
      info.setPadding(new Insets(5, 5, 5, 5));
      info.setUserPadding(true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(info.isUserPadding(), "an unmarked slider draws only a padding its author set");
   }

   @Test
   void aDataLabelColourEqualToTheClassicOneIsKept() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      ChartVSAssemblyInfo probe = (ChartVSAssemblyInfo) chart.clone();
      ChartFormatDefaults.fix(probe);
      assertEquals(GDefaults.DEFAULT_TEXT_COLOR, plot(probe).getTextFormat().getColor(),
                   "guards a pass on a value that is not the classic one");
      TextFormat label = plot(chart).getTextFormat().getUserDefinedFormat();
      label.setColor(GDefaults.DEFAULT_TEXT_COLOR, true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(label.isColorDefined(), "a set colour turns off the automatic contrast colour");
   }

   @Test
   void aTableObjectBorderColourEqualToTheClassicOneIsKept() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      BorderColors classic = table.getFormat().getDefaultFormat().getBorderColorsValue();
      assertNotNull(classic);
      VSFormat object = user(table, VSAssemblyInfo.OBJECTPATH);
      object.setBorderColorsValue(classic);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(object.isBorderColorsValueDefined(),
                 "cells read whether the object border colour is set");
   }

   @Test
   void theOptionsSwitchAlsoDropsResidue() {
      Viewsheet vs = legacySheet();
      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat title = user(table, VSAssemblyInfo.TITLEPATH);
      title.setBackgroundValue(VSAssemblyInfo.DEFAULT_TITLE_BG);

      VizModernizeUtil.applyMark(vs, VizMark.MODERN_LIGHT);

      assertEquals(VizMark.MODERN_LIGHT, table.getVizMark());
      assertFalse(title.isBackgroundValueDefined());
   }

   @Test
   void revertAndADarkFlipDoNotRunResidue() {
      gateOn();
      Viewsheet reverted = new Viewsheet();
      VSFormat revertedTitle = user(addTable(reverted, "Table1"), VSAssemblyInfo.TITLEPATH);
      revertedTitle.setBackgroundValue(VSAssemblyInfo.DEFAULT_TITLE_BG);
      Viewsheet flipped = new Viewsheet();
      TableVSAssemblyInfo flippedTable = addTable(flipped, "Table1");
      VSFormat flippedTitle = user(flippedTable, VSAssemblyInfo.TITLEPATH);
      flippedTitle.setBackgroundValue(VSAssemblyInfo.DEFAULT_TITLE_BG);
      assertEquals(VizMark.MODERN_LIGHT, flippedTable.getVizMark());

      VizModernizeUtil.revert(reverted);
      VizModernizeUtil.applyMark(flipped, VizMark.MODERN_DARK);

      assertTrue(revertedTitle.isBackgroundValueDefined(), "Revert keeps every author value");
      assertTrue(flippedTitle.isBackgroundValueDefined(), "so does a light-to-dark flip");
   }

   @Test
   void aClassicSimulationLeavesTheRuntimeAxesItSharesAlone() {
      Viewsheet vs = legacySheet();
      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      plot(chart).setXGridColor(new Color(0xeeeeee), CompositeValue.Type.USER);
      // a runtime axis is shared by a clone, and the render's CSS pass resets its gap
      AxisDescriptor runtime = new AxisDescriptor();
      runtime.setLabelGap(7, CompositeValue.Type.CSS);
      chart.getVSChartInfo().setRTAxisDescriptor(runtime);

      assertEquals(1, VizModernizeUtil.clearResidue(chart));

      assertSame(runtime, chart.getVSChartInfo().getRTAxisDescriptor());
      assertEquals(7, runtime.getLabelGap());
   }

   @Test
   void modernizeThenRevertResolvesLikeNeverModernized() {
      Viewsheet reverted = residueSheet();
      gateOn();
      VizModernizeUtil.modernize(reverted);
      VizModernizeUtil.revert(reverted);
      Viewsheet untouched = residueSheet();

      VSFormat title = ((VSAssembly) reverted.getAssembly("Table1")).getVSAssemblyInfo()
         .getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH).getUserDefinedFormat();
      assertFalse(title.isBackgroundValueDefined(), "guards a pass with no residue cleared");
      assertFalse(plot((ChartVSAssemblyInfo) ((VSAssembly) reverted.getAssembly("Chart1"))
         .getVSAssemblyInfo()).chromeValue("xGridColor").hasUserValue());
      assertFalse(((VSAssembly) reverted.getAssembly("Text1")).getVSAssemblyInfo().getFormatInfo()
         .getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat().isAlphaValueDefined(),
                  "an object-level match is cleared too");

      assertResolvesAlike(untouched.getVSAssemblyInfo(), reverted.getVSAssemblyInfo());

      for(String name : new String[] { "Table1", "Chart1", "Text1" }) {
         assertResolvesAlike(((VSAssembly) untouched.getAssembly(name)).getVSAssemblyInfo(),
                             ((VSAssembly) reverted.getAssembly(name)).getVSAssemblyInfo());
      }
   }

   /**
    * A legacy sheet whose author values mix residue, object-level residue other formats inherit,
    * and real choices.
    */
   private static Viewsheet residueSheet() {
      Viewsheet vs = legacySheet();
      Font objectFont = VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11);

      TableVSAssemblyInfo table = addTable(vs, "Table1");
      VSFormat tableObject = user(table, VSAssemblyInfo.OBJECTPATH);
      tableObject.setFontValue(objectFont);
      tableObject.setAlphaValue(100);
      VSFormat tableTitle = user(table, VSAssemblyInfo.TITLEPATH);
      tableTitle.setBackgroundValue(VSAssemblyInfo.DEFAULT_TITLE_BG);
      tableTitle.setForegroundValue("0xff0000");

      ChartVSAssemblyInfo chart = addChart(vs, "Chart1");
      Font axisFont = classicAxisFont(chart);
      VSFormat chartObject = user(chart, VSAssemblyInfo.OBJECTPATH);
      chartObject.setFontValue(objectFont);
      chartObject.setBackgroundValue(
         chart.getFormat().getDefaultFormat().getBackgroundValue());
      plot(chart).setXGridColor(new Color(0xeeeeee), CompositeValue.Type.USER);
      plot(chart).setYGridColor(new Color(0xd2d2d2), CompositeValue.Type.USER);
      axisUser(chart).setFont(axisFont, true);

      TextVSAssemblyInfo text = addText(vs, "Text1");
      VSFormat textObject = user(text, VSAssemblyInfo.OBJECTPATH);
      textObject.setAlphaValue(100);
      textObject.setForegroundValue(
         text.getFormat().getDefaultFormat().getForegroundValue());
      return vs;
   }

   /** Every slot of the untouched info resolves alike on both, chart text as a render sets it. */
   private static void assertResolvesAlike(VSAssemblyInfo untouched, VSAssemblyInfo reverted) {
      VSAssemblyInfo a = rendered(untouched);
      VSAssemblyInfo b = rendered(reverted);

      for(ChromeSlot slot : untouched.chromeSlots()) {
         assertEquals(slot.resolvedValue(a), slot.resolvedValue(b),
                      untouched.getName() + " " + slot.key());
      }
   }

   private static VSAssemblyInfo rendered(VSAssemblyInfo info) {
      VSAssemblyInfo copy = info.clone();

      if(copy instanceof ChartVSAssemblyInfo chart) {
         ChartFormatDefaults.fix(chart);
      }

      return copy;
   }
}
