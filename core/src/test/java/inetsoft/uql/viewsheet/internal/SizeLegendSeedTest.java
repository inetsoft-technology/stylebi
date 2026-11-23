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
 * The size legend is hidden on a marked treemap-family chart unless the author chose otherwise.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SizeLegendSeedTest {
   private static final int[] FAMILY = {
      GraphTypes.CHART_TREEMAP, GraphTypes.CHART_SUNBURST, GraphTypes.CHART_CIRCLE_PACKING,
      GraphTypes.CHART_ICICLE
   };

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
      info.setRTChartDescriptor((ChartDescriptor) info.getChartDescriptor().clone());
      bind(info, type);
      info.seedChromeDefaults(VizContext.of(info));
      return info;
   }

   private static void bindSize(ChartVSAssemblyInfo info) {
      VSChartAggregateRef units = new VSChartAggregateRef();
      units.setColumnValue("Units");
      VSAestheticRef sizeRef = new VSAestheticRef();
      sizeRef.setDataRef(units);
      info.getVSChartInfo().setSizeField(sizeRef);
   }

   private static void bind(ChartVSAssemblyInfo info, int type) {
      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef(null, "Region"));
      region.setDataType(XSchema.STRING);
      region.setGroupColumnValue("Region");
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      info.getVSChartInfo().addXField(region);
      info.getVSChartInfo().addYField(sales);
      info.getVSChartInfo().setChartType(type);
   }

   private static LegendDescriptor size(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor();
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo parse(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }

   @Test
   void theTreemapFamilyHidesIt() {
      for(int type : FAMILY) {
         ChartVSAssemblyInfo info = marked(type);
         assertFalse(size(info).isVisible(), "visible " + type);
         assertFalse(size(info).isMaxModeVisible(), "maxMode " + type);
         assertFalse(size(info).isUserVisible(), "flag " + type);

         assertNotNull(info.getRTChartDescriptor(), "premise");
         LegendDescriptor rsize =
            info.getRTChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor();
         assertFalse(rsize.isVisible(), "runtime " + type);
         assertFalse(rsize.isMaxModeVisible(), "runtime maxMode " + type);
      }
   }

   @Test
   void otherTypesShowIt() {
      ChartVSAssemblyInfo info = marked(FAMILY[0]);
      assertFalse(size(info).isVisible(), "premise");

      bindSize(info);
      info.getVSChartInfo().setChartType(GraphTypes.CHART_POINT);
      info.reseedIfResolvedTypeChanged();

      assertTrue(size(info).isVisible());
      assertTrue(size(info).isMaxModeVisible());
      LegendDescriptor rsize =
         info.getRTChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor();
      assertTrue(rsize.isVisible());
      assertTrue(size(marked(GraphTypes.CHART_BAR)).isVisible());
   }

   @Test
   void anAuthorsShownSizeLegendSurvives() {
      ChartVSAssemblyInfo info = marked(FAMILY[0]);
      size(info).setAuthorVisible(true);
      size(info).setAuthorMaxModeVisible(true);

      info.seedChromeDefaults(VizContext.of(info));
      info.reseedTypeDefaults(false);

      assertTrue(size(info).isVisible());
      assertTrue(size(info).isMaxModeVisible());
   }

   @Test
   void theScriptActionStamps() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vsSize");
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bind(info, FAMILY[0]);
      info.seedChromeDefaults(VizContext.of(info));

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vsSize");
      when(sandbox.getViewsheet()).thenReturn(vs);
      when(sandbox.getMode()).thenReturn(Viewsheet.SHEET_RUNTIME_MODE);
      ChartVSAScriptable scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");

      scriptable.setActionVisible("Size Legend", true);

      LegendDescriptor rsize =
         scriptable.getRTChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor();
      assertTrue(rsize.isVisible());
      assertTrue(rsize.isUserVisible());
      assertTrue(rsize.isMaxModeVisible());
   }

   private ChartVSAScriptable classicScriptable() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vsSize");
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getID()).thenReturn("vsSize");
      when(sandbox.getViewsheet()).thenReturn(vs);
      when(sandbox.getMode()).thenReturn(Viewsheet.SHEET_RUNTIME_MODE);
      ChartVSAScriptable scriptable = new ChartVSAScriptable(sandbox);
      scriptable.setAssembly("Chart1");
      return scriptable;
   }

   @Test
   void theScriptActionKeepsAnAuthorsMaxModeHide() {
      ChartVSAScriptable scriptable = classicScriptable();
      LegendDescriptor color =
         scriptable.getRTChartDescriptor().getLegendsDescriptor().getColorLegendDescriptor();
      color.setAuthorMaxModeVisible(false);

      scriptable.setActionVisible("Color Legend", true);

      assertTrue(color.isVisible());
      assertFalse(color.isMaxModeVisible());
      assertTrue(color.isUserVisible());
   }

   @Test
   void theScriptActionKeepsALegacyMaxModeHide() {
      ChartVSAScriptable scriptable = classicScriptable();
      LegendDescriptor color =
         scriptable.getRTChartDescriptor().getLegendsDescriptor().getColorLegendDescriptor();
      color.setVisible(true);
      color.setMaxModeVisible(false);
      color.setUserVisible(true);

      scriptable.setActionVisible("Color Legend", true);

      assertFalse(color.isMaxModeVisible());
   }

   @Test
   void aStoredFalseIsTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = newChart();
      size(info).setVisible(false);
      String released = writeXml(info).replace(" userVisible=\"false\"", "");
      assertFalse(released.contains("userVisible"), "premise: a file with no flag");

      ChartVSAssemblyInfo restored = parse(released);

      assertTrue(size(restored).isUserVisible());
      assertFalse(size(restored).isVisible());
   }

   @Test
   void aSeededHiddenLegendRoundTripsAsNotTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = marked(FAMILY[0]);
      String xml = writeXml(info);
      assertTrue(xml.contains("userVisible=\"false\""));

      ChartVSAssemblyInfo restored = parse(xml);

      assertFalse(size(restored).isVisible());
      assertFalse(size(restored).isUserVisible());
   }

   @Test
   void theResetCopyClearsTheFlag() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      bind(info, FAMILY[0]);
      info.seedChromeDefaults(VizContext.of(info));
      size(info).setAuthorVisible(true);
      size(info).setAuthorMaxModeVisible(true);
      assertTrue(size(info).isVisible(), "premise: the author showed it");

      VizModernizeUtil.resetToModern(vs);

      assertFalse(size(info).isUserVisible());
      assertFalse(size(info).isVisible());
      assertFalse(size(info).isMaxModeVisible());
   }

   @Test
   void classicChartsAreUnchanged() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      bind(info, FAMILY[0]);
      ChartVSAssemblyInfo never = newChart();
      bind(never, FAMILY[0]);
      info.seedChromeDefaults(VizContext.of(info));

      assertNull(info.getVizMark(), "premise: classic");
      assertTrue(size(info).isVisible());
      assertTrue(size(info).isMaxModeVisible());
      assertFalse(size(info).isUserVisible());
      assertEquals(writeXml(never), writeXml(info));
      assertFalse(writeXml(info).contains("userVisible"));
   }
}
