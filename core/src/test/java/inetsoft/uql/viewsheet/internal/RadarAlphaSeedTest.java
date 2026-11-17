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

import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern filled radar fills at 30%: the plot alpha's DEFAULT tier follows the resolved type,
 * travels with the chart, and leaves an author's alpha alone; only a filled radar's reset copy
 * clears an author's alpha.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarAlphaSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aModernFilledRadarSeeds03() throws Exception {
      gateOn();
      ChartVSAssemblyInfo info = filledRadar(newChart());
      assertNotNull(info.getVizMark(), "premise: a marked chart");

      assertEquals(0.3, defaultAlpha(info.getChartDescriptor()), 1e-9);
      assertEquals(0.3, plot(info).getAlpha(), 1e-9);
      assertFalse(alpha(info).hasUserValue());

      // it travels
      String xml = writeXml(info);
      assertEquals("^^DEF^^0.3", plotAlphaAttribute(xml));
      ChartVSAssemblyInfo loaded = parseInfo(xml);
      assertEquals(0.3, defaultAlpha(loaded.getChartDescriptor()), 1e-9);
      assertFalse(alpha(loaded).hasUserValue());
   }

   @Test
   void aClassicFilledRadarKeepsAuto() {
      gateOff();
      ChartVSAssemblyInfo info = filledRadar(newChart());
      assertNull(info.getVizMark(), "premise: a classic chart");

      assertEquals(-1, defaultAlpha(info.getChartDescriptor()), 1e-9);
      assertEquals(-1, plot(info).getAlpha(), 1e-9);
      assertFalse(alpha(info).hasUserValue());
      // what a classic chart always wrote
      assertEquals("", plotAlphaAttribute(writeXml(info)));
   }

   @Test
   void anAuthorsAlphaWins() {
      gateOn();
      ChartVSAssemblyInfo info = filledRadar(newChart());
      plot(info).setAlpha(0.5);

      seed(info);
      assertEquals(0.5, plot(info).getAlpha(), 1e-9, "a re-seed");

      toBar(info);
      assertEquals(GraphTypes.CHART_BAR, info.getSeededType().type(), "premise: the re-seed ran");
      assertEquals(0.5, plot(info).getAlpha(), 1e-9, "a type change");
   }

   @Test
   void aTypeChangeAwayFromFilledRadarDropsTheSeed() {
      gateOn();
      ChartVSAssemblyInfo info = filledRadar(newChart());
      assertEquals(0.3, defaultAlpha(info.getChartDescriptor()), 1e-9, "premise: seeded");
      // a script-made runtime copy
      ChartDescriptor rdesc = (ChartDescriptor) info.getChartDescriptor().clone();
      info.setRTChartDescriptor(rdesc);

      toBar(info);

      assertEquals(GraphTypes.CHART_BAR, info.getSeededType().type(), "premise: the re-seed ran");
      assertEquals(-1, defaultAlpha(info.getChartDescriptor()), 1e-9);
      assertEquals(-1, plot(info).getAlpha(), 1e-9);
      assertEquals(-1, defaultAlpha(rdesc), 1e-9, "the runtime copy");
   }

   @Test
   void theResetCopyResetsAFilledRadarsAlpha() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo info = filledRadar(chartOn(vs));
      plot(info).setAlpha(0.5);
      assertTrue(alpha(info).hasUserValue(), "premise: the author's alpha");

      VizModernizeUtil.resetToModern(vs);

      assertFalse(alpha(info).hasUserValue());
      assertEquals(0.3, plot(info).getAlpha(), 1e-9);
   }

   @Test
   void theResetCopyKeepsAnAuthorsAlphaOffRadar() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo info = chartOn(vs);
      bindBar(info);
      seed(info);
      plot(info).setAlpha(0.5);

      VizModernizeUtil.resetToModern(vs);

      assertTrue(alpha(info).hasUserValue());
      assertEquals(0.5, plot(info).getAlpha(), 1e-9);
   }

   @Test
   void keepOnAClassicFilledRadarKeepsAnAuthorsAlpha() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssemblyInfo info = filledRadar(chartOn(vs));
      plot(info).setAlpha(0.5);
      assertNull(info.getVizMark(), "premise: a classic chart");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertNotNull(info.getVizMark(), "premise: modernized");
      assertTrue(alpha(info).hasUserValue());
      assertEquals(0.5, plot(info).getAlpha(), 1e-9);
   }

   // ---- harness -----------------------------------------------------------------------------

   private static void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private static void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   // a chart on a dashboard
   private static ChartVSAssemblyInfo chartOn(Viewsheet vs) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   // the chart made a filled radar of three measures, seeded
   private static ChartVSAssemblyInfo filledRadar(ChartVSAssemblyInfo info) {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      radar.addYField(agg("A"));
      radar.addYField(agg("B"));
      radar.addYField(agg("C"));
      radar.setChartType(GraphTypes.CHART_FILL_RADAR);
      info.setVSChartInfo(radar);
      seed(info);
      return info;
   }

   // a type change swaps the radar's chart info for a bar chart's
   private static void toBar(ChartVSAssemblyInfo info) {
      info.setVSChartInfo(new DefaultVSChartInfo());
      bindBar(info);
      info.reseedIfResolvedTypeChanged();
   }

   private static void bindBar(ChartVSAssemblyInfo info) {
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.addXField(dim("State"));
      cinfo.addYField(agg("Sales"));
      cinfo.setChartType(GraphTypes.CHART_BAR);
   }

   private static void seed(ChartVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   private static CompositeValue<?> alpha(ChartVSAssemblyInfo info) {
      return plot(info).chromeValue("alpha");
   }

   // the DEFAULT tier, read off a copy with the higher tiers cleared
   private static double defaultAlpha(ChartDescriptor desc) {
      PlotDescriptor copy = (PlotDescriptor) desc.getPlotDescriptor().clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy.getAlpha();
   }

   private static String plotAlphaAttribute(String xml) {
      Matcher m = Pattern.compile("<plotDescriptor [^>]*?\\balpha=\"([^\"]*)\"").matcher(xml);
      assertTrue(m.find(), "premise: the plot writes its alpha");
      return m.group(1);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
