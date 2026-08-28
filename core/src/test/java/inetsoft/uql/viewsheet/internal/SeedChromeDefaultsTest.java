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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
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

   @Test
   void anUnmarkedTableGetsNoDefaultBackgroundFromTheHook() {
      // tables and crosstabs are created with fill=false precisely so they have no DEFAULT
      // background (see the "do not set default background" comment in initDefaultFormat), and
      // an unconditional write here would put one onto every pre-branch, unmarked table the
      // first time its chrome is re-seeded
      gateOff();
      TableVSAssemblyInfo info = newTable();
      assertNull(objectDefault(info).getBackgroundValue(),
                 "precondition: a fresh legacy table has no DEFAULT background");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertNull(objectDefault(info).getBackgroundValue(),
                 "an unmarked table must not gain a DEFAULT background from the hook");
   }

   // ---- the chart's plot seeds -----------------------------------------------------------------

   @Test
   void gateOnChartTakesItsPlotSeeds() {
      gateOn();
      PlotDescriptor plot = newChart().getChartDescriptor().getPlotDescriptor();
      assertEquals(0.3, plot.getBarCornerRadius(), 0.0001);
      assertTrue(plot.isSmoothLines());
   }

   @Test
   void gateOffChartTakesNoPlotSeedsButKeepsTheUnconditionalOnes() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      assertEquals(0.0, plot.getBarCornerRadius(), 0.0001);
      assertFalse(plot.isSmoothLines());
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
      assertEquals(0.3, plot.getBarCornerRadius(), 0.0001, "the plot seeds are reachable");
      assertTrue(plot.isSmoothLines());
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
      // the ternaries live in the hook, so a legacy context writes the legacy values. Revert is
      // what calls it that way.
      gateOn();
      TableVSAssemblyInfo info = newTable();

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   objectDefault(info).getBorderColorsValue().topColor);
      assertEquals(0, objectDefault(info).getRoundCornerValue());
   }

   @Test
   void chartPlotSeedsAreWrittenOnTheLegacyBranchToo() {
      // the hook has to be able to un-seed, not only seed: Revert calls it with an unmarked
      // context and expects the legacy values written rather than left alone
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      plot.setBarCornerRadius(0.4);
      plot.setSmoothLines(true);

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(0.0, plot.getBarCornerRadius(), 0.0001,
                   "an unmarked context writes the legacy radius");
      assertFalse(plot.isSmoothLines(), "and the legacy smoothing");
   }

   @Test
   void chartColorPaletteIsWrittenOnTheLegacyBranchToo() {
      // Revert calls this on a chart that was already rendered modern at least once, so the
      // frame holds modern colours coming in; the hook must overwrite them, not leave them alone
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "the render-time applier leaves the modern palette on the frame");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSChartPaletteDefaults.legacyPalette()[0], frame.getDefaultColor(0),
                   "an unmarked context must re-seed the classic legacy palette onto the colour frame");
   }

   @Test
   void chartColorPaletteIsWrittenOnTheModernBranchToo() {
      // nothing else in this file exercises the hook writing the modern direction; a seed that
      // dropped the modern write (or had its ternary inverted) would still pass every other
      // palette test here
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "a modern context must seed the modern palette onto the colour frame");
   }

   @Test
   void multiStylesChartColorPaletteIsWrittenOnTheLegacyBranchToo() {
      // a multi-styles chart stores its aesthetics on each aggregate, not on the info; the hook
      // must reach the same set AbstractChartInfo.getAggregateAestheticRefs() does, or an
      // aggregate's colour frame would keep rendering modern after Revert
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().setMultiStyles(true);

      VSChartAggregateRef aggr = new VSChartAggregateRef();
      aggr.setColumnValue("Measure1");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      aggr.setColorField(colorRef);
      info.getVSChartInfo().addYField(aggr);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getDefaultColor(0),
                   "the render-time applier leaves the modern palette on the aggregate's frame");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(VSChartPaletteDefaults.legacyPalette()[0], frame.getDefaultColor(0),
                   "an unmarked context must re-seed the legacy palette onto the aggregate-level " +
                   "frame too");
   }

   /**
    * A render derives a color per value from the palette, and a per-value color outranks the
    * palette - so re-seeding the palette alone leaves the old colors rendering for the rest of the
    * session. This is what a live composer session, its preview and its exports all showed.
    */
   @Test
   void revertAlsoDropsThePerValueColorsARenderDerived() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);

      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.of(VizMark.MODERN_LIGHT));
      frame.setDerivedColor("Business", frame.getColor(0));
      assertEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getColor("Business"),
                   "precondition: the render derived the modern color for this value");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertFalse(frame.isDerived("Business"), "the derived color is dropped with the palette");
      assertNotEquals(VSChartPaletteDefaults.modernPalette()[0], frame.getColor("Business"),
                      "so it no longer shadows the legacy palette");
   }

   @Test
   void revertKeepsAPerValueColorSomebodyAssigned() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setVisualFrame(frame);
      info.getVSChartInfo().setColorField(colorRef);
      frame.setColor("Business", Color.RED);

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(Color.RED, frame.getColor("Business"),
                   "an author's own per-value color is not collateral damage");
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

   // ---- malformed restore data: the hook must not NPE on a missing OBJECTPATH format ---------

   @Test
   void theChartHookToleratesAMissingObjectFormat() {
      // the hook used to run only on freshly constructed objects, which always have an
      // OBJECTPATH format; it now runs on data parsed from a blob, where one malformed assembly
      // missing OBJECTPATH must not turn a restore into an NPE that aborts the whole sheet.
      // setFormat(null) is how OBJECTPATH actually goes missing (FormatInfo.setFormat removes
      // the map entry), standing in for a blob whose formatInfo node never carried one.
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
   }

   @Test
   void theTableDataHookToleratesAMissingObjectFormatUnderTheGate() {
      gateOn();
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
   }

   @Test
   void theSheetHookToleratesAMissingObjectFormat() {
      ViewsheetVSAssemblyInfo info = new ViewsheetVSAssemblyInfo();
      info.setFormat(null);
      assertNull(info.getFormat(), "precondition: no OBJECTPATH format installed");
      assertDoesNotThrow(() -> info.seedChromeDefaults(VizContext.ofGate()));
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
