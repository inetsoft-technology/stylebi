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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Values shown and the funnel's value grid are seeded on a marked chart; the author's choice
 * of values stands.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ValuesVisibleSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static ChartVSAssemblyInfo marked(int type) {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bind(info, type);
      info.seedChromeDefaults(VizContext.of(info));
      return info;
   }

   private static void bind(ChartVSAssemblyInfo info, int type) {
      VSChartDimensionRef stage = new VSChartDimensionRef(new AttributeRef(null, "Stage"));
      stage.setDataType(XSchema.STRING);
      stage.setGroupColumnValue("Stage");
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      info.getVSChartInfo().addXField(stage);
      info.getVSChartInfo().addYField(sales);
      info.getVSChartInfo().setChartType(type);
   }

   private static void bindText(ChartVSAssemblyInfo info) {
      VSChartDimensionRef label = new VSChartDimensionRef(new AttributeRef(null, "Label"));
      label.setDataType(XSchema.STRING);
      label.setGroupColumnValue("Label");
      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(label);
      info.getVSChartInfo().setTextField(text);
   }

   private static PlotDescriptor plot(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor();
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }

   private static String chartDescriptorXml(String xml) {
      int start = xml.indexOf("<chartDescriptor");
      int end = xml.indexOf("</chartDescriptor>", start);
      assertTrue(start >= 0 && end > start, "a chart descriptor was written");
      return xml.substring(start, end + "</chartDescriptor>".length());
   }

   @Test
   void aMarkedFunnelAndWaterfallShowValues() {
      assertTrue(plot(marked(GraphTypes.CHART_FUNNEL)).isValuesVisible());
      assertTrue(plot(marked(GraphTypes.CHART_WATERFALL)).isValuesVisible());
      assertFalse(plot(marked(GraphTypes.CHART_BAR)).isValuesVisible());
   }

   @Test
   void aTextFieldGetsNoSeededValues() {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bind(info, GraphTypes.CHART_WATERFALL);
      bindText(info);
      info.seedChromeDefaults(VizContext.of(info));
      info.reseedIfResolvedTypeChanged();
      assertFalse(plot(info).isValuesVisible(), "a Text field labels the marks");

      info.getVSChartInfo().setTextField(null);
      info.reseedIfResolvedTypeChanged();
      assertTrue(plot(info).isValuesVisible());
      assertFalse(plot(info).isUserValuesVisible());
   }

   @Test
   void aTypeChangeFollows() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_FUNNEL);
      assertTrue(plot(info).isValuesVisible(), "premise");

      info.getVSChartInfo().setChartType(GraphTypes.CHART_BAR);
      info.reseedIfResolvedTypeChanged();

      assertFalse(plot(info).isValuesVisible());
   }

   @Test
   void anAuthorsChoiceSurvives() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bind(info, GraphTypes.CHART_WATERFALL);
      info.seedChromeDefaults(VizContext.of(info));
      assertTrue(plot(info).isValuesVisible(), "premise: seeded on");

      plot(info).setAuthorValuesVisible(false);
      info.seedChromeDefaults(VizContext.of(info));
      assertFalse(plot(info).isValuesVisible(), "a Modernize pass");

      info.seedChromeDefaults(VizContext.of((VizMark) null));
      assertFalse(plot(info).isValuesVisible(), "the legacy seed");

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertFalse(plot(info).isValuesVisible(), "the modern seed again");

      VizModernizeUtil.resetToModern(vs);
      assertFalse(plot(info).isValuesVisible(), "the reset copy");
      assertTrue(plot(info).isUserValuesVisible(), "the reset copy keeps the flag");
   }

   @Test
   void aCloneKeepsTheFlag() {
      PlotDescriptor plot = new PlotDescriptor();
      plot.setAuthorValuesVisible(false);
      PlotDescriptor copy = (PlotDescriptor) plot.clone();
      assertTrue(copy.isUserValuesVisible());
   }

   @Test
   void theLegacyBranchTurnsSeededValuesOff() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_FUNNEL);
      assertTrue(plot(info).isValuesVisible(), "premise");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertFalse(plot(info).isValuesVisible());
   }

   @Test
   void aStoredTrueIsTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = newChart();
      plot(info).setValuesVisible(true);
      String released = writeXml(info).replace(" userValuesVisible=\"false\"", "");
      assertTrue(released.contains("valuesVisible=\"true\""), "premise");
      assertFalse(released.contains("userValuesVisible"), "premise: a file with no flag");
      assertTrue(plot(parseInfo(released)).isUserValuesVisible());

      plot(info).setValuesVisible(false);
      String off = writeXml(info);
      assertFalse(off.contains("userValuesVisible"));
      assertFalse(plot(parseInfo(off)).isUserValuesVisible());
   }

   @Test
   void aSeededValueRoundTripsAsNotTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_FUNNEL);
      String xml = writeXml(info);
      assertTrue(xml.contains("userValuesVisible=\"false\""));

      ChartVSAssemblyInfo loaded = parseInfo(xml);
      assertTrue(plot(loaded).isValuesVisible());
      assertFalse(plot(loaded).isUserValuesVisible());
   }

   @Test
   void thePaneStampsOnlyAChange() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_BAR);
      assertFalse(plot(info).isValuesVisible(), "premise");

      ChartAdvancedPaneModel same = new ChartAdvancedPaneModel(info);
      same.updateChartAdvancedPaneModel(info);
      assertFalse(plot(info).isUserValuesVisible(), "an Apply with the current value");

      ChartAdvancedPaneModel changed = new ChartAdvancedPaneModel(info);
      changed.getChartPlotOptionsPaneModel().setShowValues(true);
      changed.updateChartAdvancedPaneModel(info);
      assertTrue(plot(info).isValuesVisible());
      assertTrue(plot(info).isUserValuesVisible(), "a different value");
   }

   @Test
   void theScriptStamps() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      for(String property : new String[] { "valueVisible", "value.visible" }) {
         Viewsheet vs = new Viewsheet();
         vs.getVSAssemblyInfo().setName("vsValues");
         ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
         vs.addAssembly(chart);
         chart.getVSAssemblyInfo().initDefaultFormat();
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         bind(info, GraphTypes.CHART_BAR);

         ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
         when(sandbox.getID()).thenReturn("vsValues");
         when(sandbox.getViewsheet()).thenReturn(vs);
         ChartVSAScriptable scriptable = new ChartVSAScriptable(sandbox);
         scriptable.setAssembly("Chart1");

         scriptable.putMember(property, true);

         PlotDescriptor runtime = scriptable.getRTChartDescriptor().getPlotDescriptor();
         assertTrue(runtime.isValuesVisible(), property);
         assertTrue(runtime.isUserValuesVisible(), property);
      }
   }

   @Test
   void theFunnelGridIsOff() {
      ChartVSAssemblyInfo funnel = marked(GraphTypes.CHART_FUNNEL);
      ChartVSAssemblyInfo bar = marked(GraphTypes.CHART_BAR);

      assertEquals(StyleConstants.NONE, plot(funnel).getYGridStyle());
      assertEquals(StyleConstants.THIN_LINE, plot(bar).getYGridStyle());
      assertTrue(chartDescriptorXml(writeXml(funnel)).contains(
         "yGridStyle=\"^^DEF^^" + StyleConstants.NONE + "\""), "the default tier is saved");
   }

   @Test
   void classicChartsAreUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_FUNNEL);
      ChartVSAssemblyInfo never = newChart();
      bind(never, GraphTypes.CHART_FUNNEL);
      info.seedChromeDefaults(VizContext.of(info));

      assertNull(info.getVizMark(), "premise: classic");
      assertFalse(plot(info).isValuesVisible());
      assertEquals(StyleConstants.THIN_LINE, plot(info).getYGridStyle());
      assertFalse(plot(info).isUserValuesVisible());

      String xml = chartDescriptorXml(writeXml(info));
      assertEquals(chartDescriptorXml(writeXml(never)), xml);
      assertFalse(xml.contains("yGridStyle=\"^^DEF^^"), "no yGridStyle default");
      assertFalse(xml.contains("userValuesVisible"));
   }
}
