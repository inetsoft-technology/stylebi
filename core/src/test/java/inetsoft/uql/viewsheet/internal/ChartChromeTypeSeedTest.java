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

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
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
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The per-type chrome seeds: what a chart's resolved type writes, and what it leaves to the author.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartChromeTypeSeedTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // ---- the value-axis line ---------------------------------------------------------------

   @Test
   void aModernBarChartsValueAxisHasNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(v));
      seed(info);

      VSChartInfo cinfo = info.getVSChartInfo();
      assertFalse(cinfo.getAxisDescriptor().isLineVisible(), "the value axis");
      assertFalse(cinfo.getAxisDescriptor2().isLineVisible(), "the secondary value axis");
      assertTrue(state.getAxisDescriptor().isLineVisible(), "the category baseline");
      assertFalse(v.getAxisDescriptor().isLineVisible(), "the measure's own axis");
      assertNoFlag(cinfo);
   }

   @Test
   void aHorizontalBarsValueAxisHasNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(v), refs(state));
      seed(info);

      assertFalse(info.getVSChartInfo().getAxisDescriptor().isLineVisible(), "the value axis");
      assertTrue(state.getAxisDescriptor().isLineVisible(), "the category baseline");
      assertFalse(v.getAxisDescriptor().isLineVisible(), "the measure's own axis");
   }

   @Test
   void aSeparatedChartsMeasureAxesHaveNoLine() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef v = agg("v");
      VSChartAggregateRef w = agg("w");
      info.getVSChartInfo().setSeparatedGraph(true);
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(v, w));
      seed(info);

      assertFalse(v.getAxisDescriptor().isLineVisible(), "Sum(v)");
      assertFalse(w.getAxisDescriptor().isLineVisible(), "Sum(w)");
   }

   @Test
   void aScatterKeepsBothLines() {
      gateOn();
      ChartVSAssemblyInfo info = newChart();
      VSChartAggregateRef w = agg("w");
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_POINT, refs(w), refs(v));
      seed(info);

      assertTrue(info.getVSChartInfo().getAxisDescriptor().isLineVisible(), "the chart's axis");
      assertTrue(w.getAxisDescriptor().isLineVisible(), "Sum(w)");
      assertTrue(v.getAxisDescriptor().isLineVisible(), "Sum(v)");
   }

   @Test
   void aClassicChartKeepsEveryLine() throws Exception {
      gateOff();
      ChartVSAssembly chart = chartOn(new Viewsheet());
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartAggregateRef v = agg("v");
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(v));

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);
      }

      // a legacy pass over the rendered chart, as a restore runs
      seed(info);

      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef rt = runtime(cinfo, v);
      assertNotNull(rt, "premise: the render built the runtime refs");
      assertNotSame(v, rt, "premise: the runtime ref is its own object");
      assertEveryLineOn(cinfo);
      assertNoFlag(cinfo);
   }

   @Test
   void anAuthorsLinesSurviveModernizeAndRevert() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(agg("v")));
      seed(info);
      AxisDescriptor chartAxis = info.getVSChartInfo().getAxisDescriptor();
      state.getAxisDescriptor().setAuthorLineVisible(false);
      chartAxis.setAuthorLineVisible(true);

      modernize(info);
      assertFalse(state.getAxisDescriptor().isLineVisible(), "Modernize, the author's off");
      assertTrue(chartAxis.isLineVisible(), "Modernize, the author's on");

      revert(info);
      assertFalse(state.getAxisDescriptor().isLineVisible(), "Revert, the author's off");
      assertTrue(chartAxis.isLineVisible(), "Revert, the author's on");
   }

   @Test
   void anOldFilesLineOffSurvivesModernizeAndRevert() throws Exception {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      VSChartDimensionRef state = dim("state");
      bind(info, GraphTypes.CHART_BAR, refs(state), refs(agg("v")));
      seed(info);
      state.getAxisDescriptor().setLineVisible(false);
      String xml = writeXml(info);
      String old = xml.replaceAll("\\s+userLineVisible=\"[a-z]*\"", "");
      assertNotEquals(xml, old, "premise: the line wrote its flag");

      ChartVSAssemblyInfo loaded = parseInfo(old);

      modernize(loaded);
      assertFalse(stateAxis(loaded).isLineVisible(), "Modernize keeps the old file's off");
      revert(loaded);
      assertFalse(stateAxis(loaded).isLineVisible(), "Revert keeps the old file's off");
   }

   @Test
   void modernizeThenRevertRestoresEveryLine() {
      gateOff();
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));
      seed(info);

      modernize(info);
      assertFalse(info.getVSChartInfo().getAxisDescriptor().isLineVisible(),
                  "premise: Modernize took the value line");
      revert(info);

      assertEveryLineOn(info.getVSChartInfo());
      assertNoFlag(info.getVSChartInfo());
   }

   @Test
   void aMeasureDroppedOnAMarkedChartIsSeeded() throws Exception {
      gateOn();
      ChartVSAssembly chart = chartOn(new Viewsheet());
      ChartVSAssemblyInfo info = chart.getChartInfo();
      assertNotNull(info.getVizMark(), "premise: the chart is marked");
      bind(info, GraphTypes.CHART_BAR, refs(dim("state")), refs(agg("v")));

      try(MockedStatic<GraphTypeUtil> types = permitChartTypes()) {
         render(chart);

         VSChartAggregateRef w = agg("w");
         info.getVSChartInfo().addYField(w);
         render(chart);

         ChartRef rt = runtime(info.getVSChartInfo(), w);
         assertNotNull(rt, "premise: the render built the runtime ref");
         assertNotSame(w.getAxisDescriptor(), rt.getAxisDescriptor(),
                       "premise: the runtime ref carries its own descriptor");
         assertFalse(w.getAxisDescriptor().isLineVisible(), "the design ref");
         assertFalse(rt.getAxisDescriptor().isLineVisible(), "the runtime ref");
      }
   }

   @Test
   void aTypeReseedKeepsTheRuntimeDescriptor() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      // a script may already have written it this render
      ChartDescriptor rdesc = new ChartDescriptor();
      marked.setRTChartDescriptor(rdesc);

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(marked.getVSChartInfo().getAxisDescriptor().isLineVisible(),
                  "premise: the per-type seed ran");
      assertSame(rdesc, marked.getRTChartDescriptor());
   }

   @Test
   void aTypeReseedWritesTheScriptsRuntimeAxisDescriptors() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      VSChartInfo cinfo = marked.getVSChartInfo();
      VSChartAggregateRef v = (VSChartAggregateRef) cinfo.getYField(0);
      // copies made as a script makes them, while the scatter's lines were on
      cinfo.setRTAxisDescriptor(cinfo.getAxisDescriptor().clone());
      cinfo.setRTAxisDescriptor2(cinfo.getAxisDescriptor2().clone());
      v.setRTAxisDescriptor(v.getAxisDescriptor().clone());
      assertTrue(cinfo.getRTAxisDescriptor().isLineVisible(), "premise: copied with the line on");

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(cinfo.getRTAxisDescriptor().isLineVisible(), "the chart's runtime axis");
      assertFalse(cinfo.getRTAxisDescriptor2().isLineVisible(), "the chart's runtime axis 2");
      assertFalse(v.getRTAxisDescriptor().isLineVisible(), "the measure's runtime axis");
   }

   @Test
   void aScriptsAuthoredRuntimeLineSurvivesATypeReseed() {
      gateOn();
      ChartVSAssemblyInfo marked = scatter();
      VSChartInfo cinfo = marked.getVSChartInfo();
      AxisDescriptor rt = cinfo.getAxisDescriptor().clone();
      rt.setAuthorLineVisible(true);
      cinfo.setRTAxisDescriptor(rt);

      toBar(marked);
      marked.reseedIfResolvedTypeChanged();

      assertFalse(cinfo.getAxisDescriptor().isLineVisible(), "premise: the per-type seed ran");
      assertTrue(rt.isLineVisible());
   }

   // ---- harness -----------------------------------------------------------------------------

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   private ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static ChartVSAssembly chartOn(Viewsheet vs) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      chart.getChartInfo().setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      return chart;
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

   private static ChartRef[] refs(ChartRef... refs) {
      return refs;
   }

   // a marked scatter, seeded, both lines on
   private ChartVSAssemblyInfo scatter() {
      ChartVSAssemblyInfo info = newChart();
      bind(info, GraphTypes.CHART_POINT, refs(agg("w")), refs(agg("v")));
      seed(info);
      return info;
   }

   // the X measure becomes a dimension, so the resolved type changes
   private static void toBar(ChartVSAssemblyInfo info) {
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(dim("state"));
      cinfo.setChartType(GraphTypes.CHART_BAR);
   }

   // without a principal every chart type is denied
   private static MockedStatic<GraphTypeUtil> permitChartTypes() {
      MockedStatic<GraphTypeUtil> types = mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS);
      types.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
      return types;
   }

   private static ChartRef runtime(VSChartInfo cinfo, ChartRef design) {
      return Arrays.stream(cinfo.getRTYFields())
         .filter(ref -> design.getFullName().equals(ref.getFullName()))
         .findFirst().orElse(null);
   }

   private static AxisDescriptor stateAxis(ChartVSAssemblyInfo info) {
      return info.getVSChartInfo().getXField(0).getAxisDescriptor();
   }

   // the chart's descriptors and every design and runtime ref's own, script copies included
   private static List<AxisDescriptor> descriptors(VSChartInfo cinfo) {
      List<AxisDescriptor> list = new ArrayList<>();
      list.add(cinfo.getAxisDescriptor());
      list.add(cinfo.getAxisDescriptor2());
      addIfPresent(list, cinfo.getRTAxisDescriptor());
      addIfPresent(list, cinfo.getRTAxisDescriptor2());

      for(ChartRef[] refs : new ChartRef[][] { cinfo.getXFields(), cinfo.getYFields(),
                                               cinfo.getRTXFields(), cinfo.getRTYFields() }) {
         for(ChartRef ref : refs) {
            list.add(ref.getAxisDescriptor());
            addIfPresent(list, ((VSChartRef) ref).getRTAxisDescriptor());
         }
      }

      return list;
   }

   private static void addIfPresent(List<AxisDescriptor> list, AxisDescriptor desc) {
      if(desc != null) {
         list.add(desc);
      }
   }

   private static void assertEveryLineOn(VSChartInfo cinfo) {
      List<AxisDescriptor> list = descriptors(cinfo);

      for(int i = 0; i < list.size(); i++) {
         assertTrue(list.get(i).isLineVisible(), "descriptor " + i);
      }
   }

   private static void assertNoFlag(VSChartInfo cinfo) {
      List<AxisDescriptor> list = descriptors(cinfo);

      for(int i = 0; i < list.size(); i++) {
         assertFalse(list.get(i).isUserLineVisible(), "descriptor " + i);
      }
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

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("v", XSchema.DOUBLE));
      columns.addAttribute(column("state", XSchema.STRING));
      columns.addAttribute(column("w", XSchema.DOUBLE));
      return columns;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   // the chart tree after the entry point needs a real runtime and NPEs here
   private static void render(ChartVSAssembly chart) throws Exception {
      try {
         chart.getChartInfo().update(chart.getViewsheet(), columns());
      }
      catch(NullPointerException e) {
         boolean chartTree = Arrays.stream(e.getStackTrace())
            .anyMatch(f -> f.getClassName().endsWith("ChartTree"));

         if(!chartTree) {
            throw e;
         }
      }
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
}
