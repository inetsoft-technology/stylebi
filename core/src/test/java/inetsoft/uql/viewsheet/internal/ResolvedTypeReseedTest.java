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
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.report.internal.graph.SwapXYBindingProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The per-type seed re-runs when a marked chart's resolved type changes, through the one entry
 * point every binding and type change ends in, and never on a plain re-execution.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ResolvedTypeReseedTest {
   // without a principal every chart type is denied, so the auto type would never resolve
   @BeforeEach
   void stubChartStylePermission() {
      graphTypeUtil = mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS);
      graphTypeUtil.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
   }

   @AfterEach
   void reset() {
      graphTypeUtil.close();
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aDropThatResolvesTheTypeReseeds() throws Exception {
      ChartVSAssembly chart = probeChart(new Viewsheet(), true);
      bind(chart);

      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_BAR, probe.seeded.get(0).type());
      assertTrue(probe.seeded.get(0).measureOnY());
      assertTrue(probe.modern.get(0), "the re-run resolves against the chart's own mark");
   }

   @Test
   void theFullSeedRecordsTheTypeItSeeded() {
      ChartVSAssembly chart = probeChart(new Viewsheet(), true, false);
      ChartVSAssemblyInfo info = chart.getChartInfo();
      ProbeInfo probe = (ProbeInfo) info;

      assertFalse(probe.seeded.isEmpty(), "the full seed runs the per-type seed");
      assertNotNull(info.getSeededType());
      assertEquals(ResolvedChartType.of(info.getVSChartInfo()), info.getSeededType());
   }

   @Test
   void theFirstUpdateAfterLoadRecordsWithoutSeeding() throws Exception {
      ChartVSAssembly chart = boundChart();
      String xml = writeXml(chart.getChartInfo());

      Viewsheet vs2 = new Viewsheet();
      ChartVSAssembly loaded = probeChart(vs2, false);
      ProbeInfo probe = (ProbeInfo) loaded.getChartInfo();
      probe.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      assertNotNull(probe.getVizMark(), "precondition: the loaded chart is marked");
      assertNull(probe.getSeededType(), "precondition: a load records nothing");

      render(loaded);

      assertTrue(probe.seeded.isEmpty(), "seeded " + probe.seeded);
      assertNotNull(probe.getSeededType());
   }

   @Test
   void anUnchangedBindingDoesNotReseed() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      render(chart);

      assertTrue(probe.seeded.isEmpty(), "seeded " + probe.seeded);
   }

   @Test
   void aClassicChartRecordsButNeverReseeds() throws Exception {
      ChartVSAssembly chart = probeChart(new Viewsheet(), true);
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      probe.setVizMark(null);
      bind(chart);

      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(agg("w"));
      render(chart);

      assertTrue(probe.seeded.isEmpty(), "seeded " + probe.seeded);
      assertEquals(GraphTypes.CHART_POINT, probe.getSeededType().type());
   }

   @Test
   void aSecondMeasureOnTheSameTypeReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      // the type stays, but the new field brings its own axis descriptor
      chart.getVSChartInfo().addYField(agg("w"));
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_BAR, probe.seeded.get(0).type());
   }

   @Test
   void convertingTheXDimensionToAMeasureReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(agg("w"));
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_POINT, probe.seeded.get(0).type());
      assertTrue(probe.seeded.get(0).measureOnX());
   }

   @Test
   void swappingXAndYReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      new SwapXYBindingProcessor(chart.getVSChartInfo(), probe.getChartDescriptor()).process();
      render(chart);

      assertEquals(1, probe.seeded.size());
      // the type stays, so only the binding moved the key
      assertEquals(GraphTypes.CHART_BAR, probe.seeded.get(0).type());
      assertTrue(probe.seeded.get(0).measureOnX());
      assertFalse(probe.seeded.get(0).measureOnY());
   }

   @Test
   void aChartTypeChangeReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      probe.setVSChartInfo((VSChartInfo) new ChangeChartTypeProcessor(
         GraphTypes.CHART_AUTO, GraphTypes.CHART_LINE, false, false, null,
         chart.getVSChartInfo(), false, probe.getChartDescriptor(), VizContext.of(probe))
         .process());
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_LINE, probe.seeded.get(0).type());
   }

   @Test
   void aTypeChangeOnAReplacedChartInfoStillReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_LINE);
      probe.setVSChartInfo((VSChartInfo) cinfo.clone());
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_LINE, probe.seeded.get(0).type());
   }

   @Test
   void aBindingCopiedInWholesaleReseeds() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();

      VSChartInfo other = new DefaultVSChartInfo();
      other.addXField(agg("w"));
      other.addYField(agg("v"));
      probe.setVSChartInfo(other);
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_POINT, probe.seeded.get(0).type());
   }

   @Test
   void aMultiStyleChartKeysOnItsFirstMeasure() throws Exception {
      ChartVSAssembly chart = probeChart(new Viewsheet(), true);
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.setMultiStyles(true);
      cinfo.addXField(dim("state", XSchema.STRING));
      VSChartAggregateRef first = agg("v");
      first.setChartType(GraphTypes.CHART_LINE);
      VSChartAggregateRef second = agg("w");
      second.setChartType(GraphTypes.CHART_BAR);
      cinfo.addYField(first);
      cinfo.addYField(second);
      render(chart);
      probe.clear();

      // a re-render keeps the old runtime refs' types, so clear them as a type change does
      cinfo.clearRuntime();
      second.setChartType(GraphTypes.CHART_POINT);
      render(chart);

      assertEquals(GraphTypes.CHART_POINT,
                   ((ChartAggregateRef) cinfo.getRTYFields()[1]).getRTChartType(),
                   "precondition: the second measure's drawn type moved");
      assertTrue(probe.seeded.isEmpty(), "seeded " + probe.seeded);

      cinfo.clearRuntime();
      first.setChartType(GraphTypes.CHART_AREA);
      render(chart);

      assertEquals(1, probe.seeded.size());
      assertEquals(GraphTypes.CHART_AREA, probe.seeded.get(0).type());
   }

   @Test
   void theTypeReseedRunsOnlyThePerTypeSeed() throws Exception {
      ChartVSAssembly chart = boundChart();
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      PlotDescriptor plot = probe.getChartDescriptor().getPlotDescriptor();
      plot.setXGridColor(Color.MAGENTA, CompositeValue.Type.DEFAULT);

      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.removeXField(0);
      cinfo.addXField(agg("w"));
      render(chart);

      assertEquals(1, probe.seeded.size(), "precondition: the re-seed ran");
      // a full seed would have written the modern gridline over it
      assertEquals(Color.MAGENTA, plot.getXGridColor());
   }

   @Test
   void aDateComparisonChartDoesNotReseedOnEveryUpdate() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = probeChart(vs, true);
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_LINE);
      VSChartDimensionRef date = dim("date", XSchema.DATE);
      date.setDateLevel(XConstants.MONTH_DATE_GROUP);
      cinfo.addXField(date);
      cinfo.addYField(agg("v"));
      probe.setVSChartInfo(cinfo);
      probe.setDateComparisonEnabledValue("true");
      probe.setDateComparisonInfo(dc(DateComparisonInfo.CHANGE_VALUE));
      render(chart);
      probe.clear();

      render(chart);

      assertTrue(DateComparisonUtil.appliedDateComparison(chart.getChartInfo()),
                 "precondition: the date comparison path ran");
      assertTrue(probe.seeded.isEmpty(), "seeded " + probe.seeded);
   }

   /** Records every per-type seed it is asked to run. */
   static class ProbeInfo extends ChartVSAssemblyInfo {
      final List<ResolvedChartType> seeded = new ArrayList<>();
      final List<Boolean> modern = new ArrayList<>();

      @Override
      protected void seedTypeDefaults(VizContext ctx, ResolvedChartType type) {
         seeded.add(type);
         modern.add(ctx.modern);
         super.seedTypeDefaults(ctx, type);
      }

      void clear() {
         seeded.clear();
         modern.clear();
      }
   }

   private static ChartVSAssembly probeChart(Viewsheet vs, boolean created) {
      return probeChart(vs, created, true);
   }

   private static ChartVSAssembly probeChart(Viewsheet vs, boolean created, boolean clear) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1") {
         @Override
         protected VSAssemblyInfo createInfo() {
            return new ProbeInfo();
         }
      };
      vs.addAssembly(chart);
      ProbeInfo probe = (ProbeInfo) chart.getChartInfo();
      probe.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      probe.setVizMark(VizMark.MODERN_LIGHT);

      // a load never calls this
      if(created) {
         probe.initDefaultFormat();
      }

      if(clear) {
         probe.clear();
      }

      return chart;
   }

   private static void bind(ChartVSAssembly chart) throws Exception {
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.addXField(dim("state", XSchema.STRING));
      cinfo.addYField(agg("v"));
      render(chart);
   }

   // a bound marked chart with the probe cleared
   private static ChartVSAssembly boundChart() throws Exception {
      ChartVSAssembly chart = probeChart(new Viewsheet(), true);
      bind(chart);
      ((ProbeInfo) chart.getChartInfo()).clear();
      return chart;
   }

   private static VSChartDimensionRef dim(String name, String type) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(type);
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

   private static DateComparisonInfo dc(int option) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(true);
      periods.setToDate(true);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.MONTH);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(option);
      return dcInfo;
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("date", XSchema.DATE));
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

   private MockedStatic<GraphTypeUtil> graphTypeUtil;
}
