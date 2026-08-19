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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The creation seeds, pinned. Written before the seedChromeDefaults extraction as its safety net:
 * every assertion here describes behaviour that must survive the refactor unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SeedChromeDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static VSFormat objectDefault(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getDefaultFormat();
   }

   private static VSCompositeFormat titleFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
   }

   private TableVSAssemblyInfo newTable() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      return (TableVSAssemblyInfo) table.getVSAssemblyInfo();
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private TextVSAssemblyInfo newText() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   private SelectionListVSAssemblyInfo newSelectionList() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      return (SelectionListVSAssemblyInfo) list.getVSAssemblyInfo();
   }

   private TabVSAssemblyInfo newTab() {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.getVSAssemblyInfo().initDefaultFormat();
      return (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
   }

   private TimeSliderVSAssemblyInfo newTimeSlider() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "TimeSlider1");
      slider.getVSAssemblyInfo().initDefaultFormat();
      return (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();
   }

   // ---- object border colour and card radius -------------------------------------------------

   @Test
   void gateOffTableTakesTheLegacyBorderAndNoRadius() {
      gateOff();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   fmt.getBorderColorsValue().topColor,
                   "gate off keeps the legacy 0xDADADA object border");
      assertEquals(0, fmt.getRoundCornerValue(), "gate off seeds square corners");
   }

   @Test
   void gateOnTableTakesTheModernBorderAndTheCardRadius() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat fmt = objectDefault(newTable());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }

   @Test
   void aTextAssemblyIsNotACornerSeedTargetEvenUnderTheGate() {
      gateOn();
      VSFormat fmt = objectDefault(newText());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   fmt.getBorderColorsValue().topColor,
                   "TextVSAssemblyInfo overrides setDefaultFormat entirely and never consults " +
                   "VizContext, so the border colour stays legacy even under the gate");
      assertEquals(0, fmt.getRoundCornerValue(),
                   "the entire base method is bypassed for Text, so isCornerSeedTarget() is " +
                   "never even reached");
   }

   @Test
   void aSelectionListIsACornerSeedTarget() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(newSelectionList()).getRoundCornerValue());
   }

   @Test
   void aFreshTabUnderAnOpenGateKeepsItsOwnRadiusAndBorder() {
      // Tab writes its own round corner and border colour after super(), overwriting whatever
      // the hook wrote; a fresh tab must come out unchanged by the gate being open
      gateOn();
      VSFormat fmt = objectDefault(newTab());
      assertEquals(4, fmt.getRoundCornerValue());
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor);
   }

   @Test
   void theHookDoesNothingForATabEvenUnderTheGate() {
      gateOn();
      TabVSAssemblyInfo info = newTab();
      VSFormat fmt = objectDefault(info);
      int radiusBefore = fmt.getRoundCornerValue();
      Color borderBefore = fmt.getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(radiusBefore, fmt.getRoundCornerValue());
      assertEquals(borderBefore, fmt.getBorderColorsValue().topColor);
   }

   // ---- the title border, and who wins the title composite -----------------------------------

   @Test
   void aTableTitleTakesTheModernBorderColour() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSCompositeFormat title = titleFormat(newTable());
      assertNotNull(title, "a table is titled, so a TITLEPATH composite is installed");
      assertNotNull(title.getDefaultFormat().getBordersValue(),
                    "a table creates with border = true, so the title carries border insets");
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   title.getDefaultFormat().getBorderColorsValue().topColor);
   }

   @Test
   void theHookLeavesATimeSlidersTitleBorderAlone() {
      // TimeSlider mutates the base's existing TITLEPATH composite in place after super(),
      // forcing a hardcoded bottom-only 0xc0c0c0 border; the hook must not recolour it
      gateOn();
      TimeSliderVSAssemblyInfo info = newTimeSlider();
      assertEquals(new Color(0xc0c0c0),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor);

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Color(0xc0c0c0),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor,
                   "the hook does not own this title composite");
   }

   @Test
   void aChartTitleCarriesNoBorderColourAtAll() {
      // Documents an ordering fact the extraction must not disturb: the base seeds a title border
      // colour, then ChartVSAssemblyInfo replaces the whole TITLEPATH composite, discarding it.
      gateOn();
      VSCompositeFormat title = titleFormat(newChart());
      assertNotNull(title);
      assertNull(title.getDefaultFormat().getBorderColorsValue(),
                 "the chart's own title composite wins and sets no borders");
   }

   // ---- backgrounds ---------------------------------------------------------------------------

   @Test
   void aChartCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newChart()).getBackgroundValue());
   }

   @Test
   void aTableCardTakesTheCardBackground() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(VizContext.ofGate()),
                   objectDefault(newTable()).getBackgroundValue());
   }

   @Test
   void theSheetTakesThePageBackgroundUnderTheGateAndTheLegacyGreyWithout() {
      gateOn();
      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(VizContext.ofGate()),
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());

      gateOff();
      assertEquals("#f5f5f5",
                   objectDefault(new Viewsheet().getVSAssemblyInfo()).getBackgroundValue());
   }

   // ---- the chart's plot seeds -----------------------------------------------------------------

   @Test
   void gateOnChartTakesTheFourPlotSeeds() {
      // the *Value() accessors read the stored seed; getBarCornerRadius()/isSmoothLines() are
      // effective getters that consult the gate themselves, which is a P4 concern, not this one
      gateOn();
      PlotDescriptor plot = newChart().getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadiusValue(), 0.0001);
      assertTrue(plot.isModernCornerSeed());
      assertTrue(plot.isSmoothLinesValue());
      assertTrue(plot.isModernSmoothSeed());
   }

   @Test
   void gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.0, plot.getBarCornerRadiusValue(), 0.0001);
      assertFalse(plot.isModernCornerSeed());
      assertFalse(plot.isSmoothLinesValue());
      assertFalse(plot.isModernSmoothSeed());
      assertTrue(info.getChartDescriptor().getLegendsDescriptor().isRoundCorners(),
                 "round legend corners are unconditional and must stay in setDefaultFormat");
      assertEquals(10, info.getPadding().top,
                   "chart padding is unconditional and must stay in setDefaultFormat");
   }

   // ---- the hook, called a second time on an assembly that already exists ---------------------

   @Test
   void theHookModernizesAnAssemblyCreatedUnderAClosedGate() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      assertEquals(0, objectDefault(info).getRoundCornerValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   objectDefault(info).getRoundCornerValue());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().topColor,
                   "the title border colour follows too");
   }

   @Test
   void theHookMutatesInPlaceAndLeavesTheUserTierAlone() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      VSCompositeFormat objBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat titleBefore = info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH);
      objBefore.getUserDefinedFormat().setBackgroundValue("0x123456");
      titleBefore.getUserDefinedFormat().setForegroundValue("0x654321");

      gateOn();
      info.seedChromeDefaults(VizContext.ofGate());

      assertSame(objBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH),
                 "the object composite is mutated, never replaced");
      assertSame(titleBefore, info.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH),
                 "the title composite is mutated, never replaced");
      assertEquals("0x123456", objBefore.getUserDefinedFormat().getBackgroundValue(),
                   "an author's object background survives");
      assertEquals("0x654321", titleBefore.getUserDefinedFormat().getForegroundValue(),
                   "an author's title foreground survives");
   }

   @Test
   void theHookIsIdempotent() {
      gateOn();
      TableVSAssemblyInfo info = newTable();
      int radius = objectDefault(info).getRoundCornerValue();
      Color border = objectDefault(info).getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(radius, objectDefault(info).getRoundCornerValue());
      assertEquals(border, objectDefault(info).getBorderColorsValue().topColor);
   }

   @Test
   void theHookLeavesATitleItDoesNotOwnAlone() {
      // a selection list installs its own title composite after super, with a hardcoded 0xc0c0c0
      // bottom border; the hook must not recolour it, or a modernized list would differ from a
      // freshly created one
      gateOn();
      SelectionListVSAssemblyInfo info = newSelectionList();
      assertEquals(new Color(0xc0c0c0),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor);

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(new Color(0xc0c0c0),
                   titleFormat(info).getDefaultFormat().getBorderColorsValue().bottomColor,
                   "the hook does not own this title composite");
   }

   @Test
   void theHookDoesNothingForATypeThatInstallsItsOwnFormat() {
      // Text builds its own object format and hardcodes the legacy border, so the base seeds never
      // reached it at creation and the hook must not reach it later either
      gateOn();
      TextVSAssemblyInfo info = newText();
      Color before = objectDefault(info).getBorderColorsValue().topColor;

      info.seedChromeDefaults(VizContext.ofGate());

      assertEquals(before, objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   objectDefault(info).getBorderColorsValue().topColor);
   }

   // ---- the per-type hook overrides, now that the seeds live there ---------------------------

   @Test
   void theHookModernizesALegacyChartCompletely() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      info.setPadding(new Insets(3, 3, 3, 3));

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadiusValue(), 0.0001, "the plot seeds are reachable");
      assertTrue(plot.isModernCornerSeed(), "and are recorded as seeds, not author choices");
      assertTrue(plot.isSmoothLinesValue());
      assertTrue(plot.isModernSmoothSeed());
      assertEquals(new Insets(3, 3, 3, 3), info.getPadding(),
                   "padding is not a gate-dependent seed and must not be re-applied");
   }

   @Test
   void theHookModernizesALegacyTableCard() {
      gateOff();
      TableVSAssemblyInfo info = newTable();
      String styleBefore = info.getTableStyleValue();

      gateOn();
      VizContext ctx = VizContext.ofGate();
      info.seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.cardBackgroundCss(ctx),
                   objectDefault(info).getBackgroundValue());
      assertEquals(styleBefore, info.getTableStyleValue(),
                   "the default table style is not a gate-dependent seed");
   }

   @Test
   void theHookModernizesALegacySheetBackground() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      assertEquals("#f5f5f5", objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());

      gateOn();
      VizContext ctx = VizContext.ofGate();
      vs.getVSAssemblyInfo().seedChromeDefaults(ctx);

      assertEquals(VSObjectChromeDefaults.pageBackgroundCss(ctx),
                   objectDefault(vs.getVSAssemblyInfo()).getBackgroundValue());
   }

   @Test
   void theHookRevertsChromeWhenGivenALegacyContext() {
      // the ternaries live in the hook, so a legacy context writes the legacy values. Nothing
      // calls it that way in P3 - this pins the contract P4's flip will depend on.
      gateOn();
      TableVSAssemblyInfo info = newTable();

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(0, objectDefault(info).getRoundCornerValue());
   }

   // ---- creation seeds from the assembly's own mark, not the org gate -------------------------

   @Test
   void creationOnAnUnmarkedHostSeedsNothingModern() {
      // the host is what decides: AbstractVSAssembly hands a new assembly the host's stored mark,
      // so an assembly added to a legacy dashboard must seed the values it will actually read
      gateOff();
      Viewsheet legacy = new Viewsheet();
      assertNull(legacy.getVSAssemblyInfo().getVizMark(), "gate off at construction: unmarked sheet");

      gateOn();
      TableVSAssembly table = new TableVSAssembly(legacy, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());

      assertNull(table.getVSAssemblyInfo().getVizMark(), "it inherits the host's absent mark");
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR, fmt.getBorderColorsValue().topColor,
                   "so it seeds the legacy border, matching what it will read");
      assertEquals(0, fmt.getRoundCornerValue(), "and no card radius");
   }

   @Test
   void creationOnAMarkedHostStillSeedsModern() {
      gateOn();
      Viewsheet modern = new Viewsheet();
      assertEquals(VizMark.MODERN_LIGHT, modern.getVSAssemblyInfo().getVizMark());

      TableVSAssembly table = new TableVSAssembly(modern, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      VSFormat fmt = objectDefault(table.getVSAssemblyInfo());
      VizContext ctx = VizContext.ofGate();

      assertEquals(VizMark.MODERN_LIGHT, table.getVSAssemblyInfo().getVizMark());
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx), fmt.getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(), fmt.getRoundCornerValue());
   }
}
