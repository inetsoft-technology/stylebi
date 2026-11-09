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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.vswizard.handler.SyncChartHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wizard copies the original chart's plot and formats onto the chart it keeps. A marked chart
 * that receives a classic chart's values must end up with its own mark's defaults, saved, and the
 * author's values must survive.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizardSyncReseedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aClassicOriginalSavedIntoAMarkedChartTakesTheModernPlotDefaults() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      gateOn();
      ChartVSAssembly target = pointOn(new Viewsheet(), "Chart2");
      VizContext ctx = VizContext.of(target.getChartInfo());
      Color gridline = VSChartChromeDefaults.gridlineColor(ctx);
      assertNull(original.getVSAssemblyInfo().getVizMark(), "premise: a classic original");
      assertNotNull(target.getVSAssemblyInfo().getVizMark(), "premise: a marked target");
      assertNotEquals(gridline, defaults(original).getXGridColor(),
                      "premise: the classic gridline differs");
      PlotDescriptor before = plot(target);

      save(original, target);

      assertNotSame(before, plot(target), "premise: the original's plot was swapped in");
      PlotDescriptor def = defaults(target);
      assertEquals(gridline, def.getXGridColor(), "x grid");
      assertEquals(gridline, def.getYGridColor(), "y grid");
      assertEquals(gridline, def.getFacetGridColor(), "facet");
      assertEquals(gridline, def.getDiagonalColor(), "diagonal");
      assertEquals(gridline, def.getQuadrantColor(), "quadrant");
      assertEquals(0.3, plot(target).getBarCornerRadius(), 0.0001, "corner radius");
      assertEquals(VSChartChromeDefaults.titleColor(ctx),
                   plot(target).getTextFormat().getDefaultFormat().getColor(), "label ink");

      String xml = xml(target.getChartDescriptor());
      String saved = "^^DEF^^" + gridline.getRGB();
      assertTrue(xml.contains("xGridColor=\"" + saved + "\""), "the saved x grid: " + xml);
      assertTrue(xml.contains("diagonalColor=\"" + saved + "\""), "the saved diagonal");
      assertTrue(xml.contains("quadrantColor=\"" + saved + "\""), "the saved quadrant");
   }

   @Test
   void theAuthorsValuesSurviveTheReseed() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      ChartVSAssemblyInfo from = original.getChartInfo();
      PlotDescriptor fromPlot = plot(original);
      fromPlot.setXGridColor(Color.RED, CompositeValue.Type.USER);
      from.setPadding(new Insets(25, 25, 25, 25));
      from.setUserPadding(true);
      from.setUserSmoothLines(true);
      fromPlot.setSmoothLines(false);
      from.setUserBarCornerRadius(true);
      fromPlot.setBarCornerRadius(0.5);
      gateOn();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");

      save(original, target);

      ChartVSAssemblyInfo info = target.getChartInfo();
      assertEquals(Color.RED, plot(target).getXGridColor(), "the author's gridline");
      assertEquals(new Insets(25, 25, 25, 25), info.getPadding(), "padding");
      assertTrue(info.isUserPadding(), "padding flag");
      assertFalse(plot(target).isSmoothLines(), "smoothing");
      assertTrue(info.isUserSmoothLines(), "smoothing flag");
      assertEquals(0.5, plot(target).getBarCornerRadius(), 0.0001, "corner radius");
      assertTrue(info.isUserBarCornerRadius(), "corner radius flag");
   }

   @Test
   void aClassicChartKeepsTheCopiedPlot() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      // a value no classic seed writes, so a re-seed would show
      plot(original).setXGridColor(Color.BLUE, CompositeValue.Type.DEFAULT);
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      assertNull(target.getVSAssemblyInfo().getVizMark(), "premise: a classic target");

      save(original, target);

      assertEquals(Color.BLUE, defaults(target).getXGridColor());
   }

   @Test
   void aPreviewSyncLeavesAMarkedChartsLabelInkModern() {
      gateOff();
      ChartVSAssembly original = barOn(new Viewsheet(), "Chart1");
      gateOn();
      ChartVSAssembly target = barOn(new Viewsheet(), "Chart2");
      Color ink = VSChartChromeDefaults.titleColor(VizContext.of(target.getChartInfo()));
      assertNotEquals(ink, plot(original).getTextFormat().getDefaultFormat().getColor(),
                      "premise: the classic ink differs");

      // the formats are copied too, after the plot
      new SyncChartHandler(new ChartRegionHandler())
         .syncChart(new VSTemporaryInfo(), original, target, true, true);

      assertEquals(ink, plot(target).getTextFormat().getDefaultFormat().getColor());
   }

   // ---- harness ------------------------------------------------------------------------------

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   // the wizard's Save syncs the original into the chart it keeps this way
   private static void save(ChartVSAssembly original, ChartVSAssembly target) {
      new SyncChartHandler(new ChartRegionHandler())
         .syncChart(new VSTemporaryInfo(), original, target, false, false);
   }

   private static ChartVSAssembly barOn(Viewsheet vs, String name) {
      return chartOn(vs, name, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
   }

   private static ChartVSAssembly pointOn(Viewsheet vs, String name) {
      return chartOn(vs, name, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
   }

   // seeded, and marked when the gate is on
   private static ChartVSAssembly chartOn(Viewsheet vs, String name, int type, ChartRef[] x,
                                          ChartRef[] y)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();

      for(ChartRef ref : x) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : y) {
         cinfo.addYField(ref);
      }

      cinfo.setChartType(type);
      info.seedChromeDefaults(VizContext.of(info));
      return chart;
   }

   private static PlotDescriptor plot(ChartVSAssembly chart) {
      return chart.getChartDescriptor().getPlotDescriptor();
   }

   // the DEFAULT tier, read off a copy with the higher tiers cleared
   private static PlotDescriptor defaults(ChartVSAssembly chart) {
      PlotDescriptor copy = (PlotDescriptor) plot(chart).clone();
      copy.resetCompositeValues(CompositeValue.Type.USER);
      copy.resetCompositeValues(CompositeValue.Type.CSS);
      return copy;
   }

   private static String xml(ChartDescriptor desc) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      desc.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartRef[] refs(ChartRef... refs) {
      return refs;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }
}
