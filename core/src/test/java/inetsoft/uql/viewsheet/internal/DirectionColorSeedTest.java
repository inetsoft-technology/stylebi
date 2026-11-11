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
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked candle, stock or waterfall holds up and down frames and a neutral Total; a classic
 * chart holds none of them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DirectionColorSeedTest {
   private static final Color UP = new Color(0x1C857B);
   private static final Color DOWN = new Color(0xAD6119);
   private static final Color UP_DARK = new Color(0x3FBFB2);
   private static final Color DOWN_DARK = new Color(0xE8954A);
   private static final Color NEUTRAL = new Color(0x767676);
   private static final Color CLASSIC_TOTAL = new Color(0x7030A0);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aModernCandleGetsTheTokens() {
      gateOn();

      for(String kind : new String[]{ "candle", "stock", "waterfall" }) {
         ChartVSAssemblyInfo info = newChart();
         info.setVSChartInfo(chartOf(kind));
         seed(info);
         assertNotNull(up(info), kind);
         assertEquals(UP, up(info).getDefaultColor(), kind);
         assertEquals(DOWN, down(info).getDefaultColor(), kind);

         info.setVizMark(VizMark.MODERN_DARK);
         seed(info);
         assertEquals(UP_DARK, up(info).getDefaultColor(), kind);
         assertEquals(DOWN_DARK, down(info).getDefaultColor(), kind);
      }
   }

   @Test
   void aModernBarGetsNoFrames() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().addXField(dim("state"));
      info.getVSChartInfo().addYField(agg("v"));
      info.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      seed(info);
      assertNotNull(info.getVizMark(), "premise: a marked chart");
      assertNull(up(info));
      assertNull(down(info));
   }

   @Test
   void aCandleChangedToBarDropsSeedOnlyFrames() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(chartOf("candle"));
      seed(info);
      assertNotNull(up(info), "premise: seeded");

      info.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      info.reseedIfResolvedTypeChanged();

      assertNull(up(info));
      assertNull(down(info));
   }

   @Test
   void aBarChangedToCandleSeedsTheFrames() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.getVSChartInfo().addXField(dim("state"));
      info.getVSChartInfo().addYField(agg("v"));
      info.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      seed(info);
      assertNull(up(info), "premise: none on a bar");

      info.setVSChartInfo(chartOf("candle"));
      info.reseedIfResolvedTypeChanged();

      assertEquals(UP, up(info).getDefaultColor());
      assertEquals(DOWN, down(info).getDefaultColor());
   }

   @Test
   void revertDropsSeedOnlyFramesAndKeepsAnAuthorsUp() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(chartOf("candle"));
      modernize(info);
      assertNotNull(up(info), "premise: seeded");
      up(info).setUserColor(new Color(0x00AA00));

      revert(info);

      assertNotNull(up(info));
      assertEquals(new Color(0x00AA00), up(info).getUserColor());
      assertNull(down(info));
   }

   @Test
   void theWaterfallTotalTurnsNeutralAndBack() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(chartOf("waterfall"));
      assertEquals(CLASSIC_TOTAL, total(info).getDefaultColor(), "premise: the classic total");

      modernize(info);
      assertEquals(NEUTRAL, total(info).getDefaultColor());

      revert(info);
      assertEquals(CLASSIC_TOTAL, total(info).getDefaultColor());
   }

   @Test
   void anAuthorTotalColourIsLeftAlone() {
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(chartOf("waterfall"));
      total(info).setUserColor(Color.RED);

      modernize(info);
      assertEquals(Color.RED, total(info).getUserColor());
      assertEquals(NEUTRAL, total(info).getDefaultColor());

      revert(info);
      assertEquals(Color.RED, total(info).getUserColor());
      assertEquals(CLASSIC_TOTAL, total(info).getDefaultColor());
   }

   @Test
   void aLightToDarkSwitchMovesTheTokens() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      info.setVSChartInfo(chartOf("waterfall"));
      seed(info);
      assertEquals(NEUTRAL, total(info).getDefaultColor());

      info.setVizMark(VizMark.MODERN_DARK);
      seed(info);

      assertEquals(new Color(0xA8A5AD), total(info).getDefaultColor());
      assertEquals(UP_DARK, up(info).getDefaultColor());
   }

   @Test
   void aClassicCandlesXmlIsUnchanged() {
      gateOff();

      for(String kind : new String[]{ "candle", "stock", "waterfall" }) {
         ChartVSAssemblyInfo info = newChart();
         info.setVSChartInfo(chartOf(kind));
         String before = chartXml(info);
         seed(info);
         assertNull(info.getVizMark(), "premise: a classic chart");
         assertEquals(before, chartXml(info), kind);
      }
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "candle", "stock", "waterfall" })
   void modernizeThenRevertKeepsTheXml(String kind) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVSChartInfo(chartOf(kind));
      assertNull(info.getVizMark(), "premise: a classic chart");
      String before = chartXml(info);

      gateOn();
      VizModernizeUtil.modernize(vs);
      assertEquals(VizMark.MODERN_LIGHT, info.getVizMark(), "premise: modernized");
      assertNotEquals(before, chartXml(info), "premise: Modernize moved a colour");

      VizModernizeUtil.revert(vs);
      assertEquals(before, chartXml(info));
   }

   // ---- harness -----------------------------------------------------------------------------

   private static VSChartInfo chartOf(String kind) {
      switch(kind) {
      case "candle":
      case "stock":
         CandleVSChartInfo cinfo =
            kind.equals("candle") ? new CandleVSChartInfo() : new StockVSChartInfo();
         cinfo.addXField(dim("date"));
         cinfo.setHighField(agg("high"));
         cinfo.setLowField(agg("low"));
         cinfo.setOpenField(agg("open"));
         cinfo.setCloseField(agg("close"));
         cinfo.setChartType(kind.equals("candle") ? GraphTypes.CHART_CANDLE :
                               GraphTypes.CHART_STOCK);
         return cinfo;
      case "waterfall":
         DefaultVSChartInfo winfo = new DefaultVSChartInfo();
         winfo.addXField(dim("state"));
         winfo.addYField(agg("v"));
         winfo.setChartType(GraphTypes.CHART_WATERFALL);
         return winfo;
      default:
         throw new IllegalArgumentException(kind);
      }
   }

   private static StaticColorFrameWrapper up(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getUpColorFrameWrapper();
   }

   private static StaticColorFrameWrapper down(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getDownColorFrameWrapper();
   }

   private static StaticColorFrameWrapper total(ChartVSAssemblyInfo info) {
      ChartAggregateRef measure = (ChartAggregateRef) info.getVSChartInfo().getYFields()[0];
      return (StaticColorFrameWrapper) measure.getSummaryColorFrameWrapper();
   }

   private static String chartXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.getVSChartInfo().writeXML(writer);
      info.getChartDescriptor().writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static void seed(ChartVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void modernize(ChartVSAssemblyInfo info) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void revert(ChartVSAssemblyInfo info) {
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
   }
}
