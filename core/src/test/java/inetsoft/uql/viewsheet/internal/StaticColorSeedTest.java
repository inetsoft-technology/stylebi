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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.service.graph.aesthetic.ColorFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A measure's static colour follows the mark through Modernize and Revert.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StaticColorSeedTest {
   private static final Color LEGACY_1 = new Color(0x518DB9);
   private static final Color LEGACY_2 = new Color(0xB9DBF4);
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color MODERN_2 = new Color(0xFF5A35);
   private static final Color DARK_1 = new Color(0x4FA5FF);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.default.color", null);
      SreeEnv.setProperty("map.empty.color", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aNewModernChartsMeasureTakesSlotOne() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      seed(info);
      assertEquals(MODERN_1, frame(a).getDefaultColor());
   }

   @Test
   void aSecondMeasureTakesSlotTwo() {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      VSChartAggregateRef b = agg("b", LEGACY_2);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a, b));
      modernize(info);
      assertEquals(MODERN_1, frame(a).getDefaultColor());
      assertEquals(MODERN_2, frame(b).getDefaultColor());
   }

   @Test
   void aSecondSlotMeasureComesBackAfterRevert() {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_2);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      modernize(info);
      assertEquals(MODERN_2, frame(a).getDefaultColor());
      revert(info);
      assertEquals(LEGACY_2, frame(a).getDefaultColor());
   }

   @Test
   void anAuthorColourIsLeftAlone() {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      frame(a).setUserColor(Color.RED);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      modernize(info);
      assertEquals(Color.RED, frame(a).getUserColor());
      assertEquals(MODERN_1, frame(a).getDefaultColor());
      assertEquals(Color.RED, frame(a).getColor());
   }

   @Test
   void aDarkMarkTakesTheDarkSlot() {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      info.setVizMark(VizMark.MODERN_DARK);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(DARK_1, frame(a).getDefaultColor());
   }

   @Test
   void runtimeAndGanttMeasuresMoveToo() {
      ChartVSAssemblyInfo info = newChart();
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      info.setVSChartInfo(gantt);
      VSChartAggregateRef rt = agg("r", LEGACY_1);
      VSChartAggregateRef milestone = agg("m", LEGACY_2);
      gantt.setRTYFields(new ChartRef[] { rt });
      gantt.setMilestoneField(milestone);
      modernize(info);
      assertEquals(MODERN_1, frame(rt).getDefaultColor());
      assertEquals(MODERN_2, frame(milestone).getDefaultColor());
   }

   @Test
   void aClassicChartsXmlIsUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(),
           refs(agg("a", LEGACY_1), agg("b", LEGACY_2)));
      String before = writeXml(info);
      seed(info);
      assertEquals(before, writeXml(info));
   }

   @Test
   void resetThenRevertDrawsTheLegacyColour() {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      frame(a).setUserColor(Color.RED);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      modernize(info);

      StaticColorModel model = new StaticColorModel(frame(a));
      model.setColor(model.getDefaultColor());
      model.setChanged(false);
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.StaticColorFrameFactory());
      StaticColorFrameWrapper reset =
         new VisualFrameModelFactoryService(factories).updateVisualFrameWrapper(frame(a), model);
      a.setColorFrameWrapper(reset);
      assertNull(frame(a).getUserColor());

      revert(info);
      assertEquals(LEGACY_1, frame(a).getColor());
   }

   @Test
   void theSeededColourTravels() throws Exception {
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef a = agg("a", LEGACY_1);
      bind(info, GraphTypes.CHART_BAR, refs(), refs(a));
      modernize(info);
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(writeXml(info))).getDocumentElement());
      ChartRef y = restored.getVSChartInfo().getYFields()[0];
      assertEquals(MODERN_1, frame((ChartAggregateRef) y).getDefaultColor());
   }

   private static StaticColorFrameWrapper frame(ChartAggregateRef ref) {
      return (StaticColorFrameWrapper) ref.getColorFrameWrapper();
   }

   private static VSChartAggregateRef agg(String name, Color color) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      frame(agg).setDefaultColor(color);
      return agg;
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static void bind(ChartVSAssemblyInfo info, int type, ChartRef[] x, ChartRef[] y) {
      VSChartInfo cinfo = info.getVSChartInfo();

      for(ChartRef ref : x) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : y) {
         cinfo.addYField(ref);
      }

      cinfo.setChartType(type);
   }

   private static ChartRef[] refs(ChartRef... refs) {
      return refs;
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

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
