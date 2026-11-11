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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hiding and showing chart axes: a hide is the author's, a show hands the line back to the seed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartAxesVisibilityServiceTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void hidingAnAxisStampsItsLine() {
      for(boolean marked : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(marked);
         String kind = marked ? "modern" : "classic";
         AxisDescriptor value = valueAxis(info.getVSChartInfo());

         VSChartAxesVisibilityService.setVisible(value, false, false);

         assertFalse(value.isLineVisible(), kind);
         assertFalse(value.isLabelVisible(), kind);
         assertTrue(value.isUserLineVisible(), kind);
      }
   }

   @Test
   void hidingInMaxModeLeavesTheLineAndFlagAlone() {
      for(boolean marked : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(marked);
         String kind = marked ? "modern" : "classic";
         AxisDescriptor value = valueAxis(info.getVSChartInfo());
         boolean line = value.isLineVisible();

         VSChartAxesVisibilityService.setVisible(value, false, true);

         assertFalse(value.isMaxModeLineVisible(), kind);
         assertFalse(value.isMaxModeLabelVisible(), kind);
         assertEquals(line, value.isLineVisible(), kind);
         assertTrue(value.isLabelVisible(), kind);
         assertFalse(value.isUserLineVisible(), kind);
      }
   }

   @Test
   void showAllOnAModernChartRestoresTheSeededLines() {
      ChartVSAssemblyInfo info = bar(true);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor value = valueAxis(cinfo);
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      VSChartAxesVisibilityService.setVisible(value, false, false);
      VSChartAxesVisibilityService.setVisible(state, false, false);
      assertFalse(state.isLineVisible(), "premise: the baseline is hidden");

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, false);

      assertFalse(value.isLineVisible(), "the value axis");
      assertFalse(cinfo.getAxisDescriptor().isLineVisible(), "the chart's value axis");
      assertTrue(state.isLineVisible(), "the category baseline");

      for(AxisDescriptor axis : new AxisDescriptor[] { value, state }) {
         assertTrue(axis.isLabelVisible());
         assertFalse(axis.isUserLineVisible());
      }
   }

   @Test
   void showAllKeepsAValueLineTheAuthorTurnedOn() {
      ChartVSAssemblyInfo info = bar(true);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor value = valueAxis(cinfo);
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      value.setAuthorLineVisible(true);
      VSChartAxesVisibilityService.setVisible(state, false, false);

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, false);

      assertTrue(value.isLineVisible(), "the author's value line");
      assertTrue(value.isUserLineVisible(), "its flag");
      assertTrue(state.isLineVisible(), "the category baseline");
      assertFalse(state.isUserLineVisible(), "the shown axis's flag");
   }

   @Test
   void showAllOnAClassicChartTurnsEveryLineOn() {
      ChartVSAssemblyInfo info = bar(false);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor value = valueAxis(cinfo);
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      VSChartAxesVisibilityService.setVisible(value, false, false);
      VSChartAxesVisibilityService.setVisible(state, false, false);

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, false);

      for(AxisDescriptor axis : new AxisDescriptor[] { value, state }) {
         assertTrue(axis.isLineVisible());
         assertTrue(axis.isLabelVisible());
         assertTrue(axis.isMaxModeLineVisible());
         assertTrue(axis.isMaxModeLabelVisible());
         assertFalse(axis.isUserLineVisible());
      }
   }

   @Test
   void aClassicHideThenShowAllWritesWhatItWroteBefore() {
      ChartVSAssemblyInfo info = bar(false);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor value = valueAxis(cinfo);
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      String valueBefore = xml(value);
      String stateBefore = xml(state);

      VSChartAxesVisibilityService.setVisible(value, false, false);
      VSChartAxesVisibilityService.setVisible(state, false, false);
      assertNotEquals(valueBefore, xml(value), "premise: the hide changed the XML");

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, false);

      assertEquals(valueBefore, xml(value), "the value axis");
      assertEquals(stateBefore, xml(state), "the category axis");
   }

   @Test
   void showAllInMaxModeOnAClassicChartLeavesTheNormalAxisAlone() {
      ChartVSAssemblyInfo info = bar(false);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      VSChartAxesVisibilityService.setVisible(state, false, false);

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, true);

      assertTrue(state.isMaxModeLineVisible());
      assertTrue(state.isMaxModeLabelVisible());
      assertFalse(state.isLineVisible());
      assertFalse(state.isLabelVisible());
      assertTrue(state.isUserLineVisible());
   }

   @Test
   void showAllInMaxModeOnAModernChartShowsTheAxesInBothModes() {
      ChartVSAssemblyInfo info = bar(true);
      VSChartInfo cinfo = info.getVSChartInfo();
      AxisDescriptor value = valueAxis(cinfo);
      AxisDescriptor state = cinfo.getXField(0).getAxisDescriptor();
      VSChartAxesVisibilityService.setVisible(value, false, false);
      VSChartAxesVisibilityService.setVisible(state, false, false);

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, true);

      assertTrue(state.isLineVisible(), "the category baseline");
      assertFalse(value.isLineVisible(), "the value axis takes its seed");

      for(AxisDescriptor axis : new AxisDescriptor[] { value, state }) {
         assertTrue(axis.isLabelVisible());
         assertTrue(axis.isMaxModeLabelVisible());
         assertTrue(axis.isMaxModeLineVisible());
         assertFalse(axis.isUserLineVisible());
      }
   }

   @Test
   void showAllInMaxModeDrawsAHiddenCategoryAxisAgain() {
      for(boolean marked : new boolean[] { true, false }) {
         ChartVSAssemblyInfo info = bar(marked);
         String kind = marked ? "modern" : "classic";
         VSChartInfo cinfo = info.getVSChartInfo();
         VSChartAxesVisibilityService.setVisible(cinfo.getXField(0).getAxisDescriptor(), false,
                                                 false);
         AxisSpec hidden = maxModeAxes(info, BAR_ROWS)[0];
         assertFalse(hidden.isLineVisible(), "premise: the hidden baseline, " + kind);
         assertFalse(hidden.isLabelVisible(), "premise: the hidden labels, " + kind);

         VSChartAxesVisibilityService.showAllAxes(info, cinfo, true);

         AxisSpec shown = maxModeAxes(info, BAR_ROWS)[0];
         assertTrue(shown.isLineVisible(), "the baseline, " + kind);
         assertTrue(shown.isLabelVisible(), "the labels, " + kind);
      }
   }

   @Test
   void showAllInMaxModeDrawsAModernScattersHiddenLinesAgain() {
      ChartVSAssemblyInfo info = scatter();
      VSChartInfo cinfo = info.getVSChartInfo();

      for(AxisSpec axis : maxModeAxes(info, SCATTER_ROWS)) {
         assertTrue(axis.isLineVisible(), "premise: a modern scatter draws both lines");
      }

      for(AxisDescriptor axis : new AxisDescriptor[] {
         cinfo.getXField(0).getAxisDescriptor(), cinfo.getYField(0).getAxisDescriptor(),
         cinfo.getAxisDescriptor(), cinfo.getAxisDescriptor2() })
      {
         VSChartAxesVisibilityService.setVisible(axis, false, false);
      }

      for(AxisSpec axis : maxModeAxes(info, SCATTER_ROWS)) {
         assertFalse(axis.isLineVisible(), "premise: the hidden lines");
      }

      VSChartAxesVisibilityService.showAllAxes(info, cinfo, true);

      for(AxisSpec axis : maxModeAxes(info, SCATTER_ROWS)) {
         assertTrue(axis.isLineVisible(), "the line");
         assertTrue(axis.isLabelVisible(), "the labels");
      }
   }

   @Test
   void showAllOnAModernRadarTurnsItsLabelAxisLineBackOn() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      RadarVSChartInfo radar = new RadarVSChartInfo();
      info.setVSChartInfo(radar);
      bind(info, GraphTypes.CHART_RADAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      // no seed reaches the radar's label axis
      AxisDescriptor label = radar.getLabelAxisDescriptor();
      VSChartAxesVisibilityService.setVisible(label, false, false);

      VSChartAxesVisibilityService.showAllAxes(info, radar, false);

      assertTrue(label.isLineVisible());
      assertTrue(label.isLabelVisible());
      assertFalse(label.isUserLineVisible());
   }

   @Test
   void aRevertAfterHidingKeepsTheAxisHidden() {
      ChartVSAssemblyInfo info = bar(true);
      AxisDescriptor value = valueAxis(info.getVSChartInfo());
      VSChartAxesVisibilityService.setVisible(value, false, false);

      info.setVizMark(null);
      VizModernizeUtil.reseedAfterRestore(info);

      assertFalse(value.isLineVisible());
      assertFalse(value.isLabelVisible());
   }

   // ---- harness -----------------------------------------------------------------------------

   // a seeded bar of state by Sum(v), marked when the gate is on at creation
   private ChartVSAssemblyInfo bar(boolean marked) {
      SreeEnv.setProperty("viewsheet.modernVisualization", marked ? "true" : "false");
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);
      assertEquals(marked, modern(info), "premise: the chart's mark");
      return info;
   }

   // a seeded, marked scatter of Sum(w) by Sum(v): two continuous axes
   private ChartVSAssemblyInfo scatter() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);
      assertTrue(modern(info), "premise: the chart's mark");
      return info;
   }

   // the x and y axes a maximized render draws, after the service's runtime reset
   private static AxisSpec[] maxModeAxes(ChartVSAssemblyInfo info, Object[][] rows) {
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.clearRuntime();
      cinfo.updateChartType(false);
      info.getViewsheet().setMaxMode(true);
      EGraph graph = GraphGenerator.getGenerator(
         info, null, new DefaultDataSet(rows), new VariableTable(), null, XSourceInfo.NONE,
         null).createEGraph();
      RectCoord coord = (RectCoord) graph.getCoordinate();
      return new AxisSpec[] { coord.getXScale().getAxisSpec(), coord.getYScale().getAxisSpec() };
   }

   // the bar is separated, so a hide on the value axis picks the measure's own descriptor
   private static AxisDescriptor valueAxis(VSChartInfo cinfo) {
      assertTrue(cinfo.isSeparatedGraph(), "premise: a separated chart");
      return cinfo.getYField(0).getAxisDescriptor();
   }

   private static String xml(AxisDescriptor axis) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      axis.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static boolean modern(ChartVSAssemblyInfo info) {
      return VizContext.of(info).modern;
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private ChartVSAssemblyInfo newChart() {
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

   private static void seed(ChartVSAssemblyInfo info) {
      VizModernizeUtil.reseedAfterRestore(info);
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

   private static final Object[][] BAR_ROWS = {
      { "state", "Sum(v)" }, { "CA", 100.0 }, { "NY", 200.0 }, { "TX", 300.0 }
   };

   private static final Object[][] SCATTER_ROWS = {
      { "Sum(w)", "Sum(v)" }, { 1.0, 100.0 }, { 2.0, 200.0 }, { 3.0, 300.0 }
   };
}
