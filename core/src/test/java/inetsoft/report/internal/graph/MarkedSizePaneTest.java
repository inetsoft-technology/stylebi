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
package inetsoft.report.internal.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.visual.PointVO;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.DefaultTextFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.binding.model.graph.aesthetic.LinearSizeModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticSizeModel;
import inetsoft.web.binding.service.graph.aesthetic.SizeFrameModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The size frame the Size pane of a marked chart shows and commits. The binding services fix a
 * chart's default sizes and send the binding model before the chart draws again, so they must
 * pick the sizes the chart draws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MarkedSizePaneTest {
   // without a principal every chart type is denied, so the processors would lose the type
   @BeforeEach
   void stubChartStylePermission() {
      graphTypeUtil = mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS);
      graphTypeUtil.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
   }

   @AfterEach
   void reset() {
      graphTypeUtil.close();
   }

   @Test
   void aMarkedBubbleChartShowsLargest8() {
      ChartVSAssemblyInfo info = bubbleChart(VizMark.MODERN_LIGHT);
      bind(info);

      assertEquals(8, paneModel(info).getLargest());
   }

   // the drop, ref, type, separate and wizard services call the processor directly
   @Test
   void aServicesSizeFixShowsLargest8() {
      ChartVSAssemblyInfo marked = bubbleChart(VizMark.MODERN_LIGHT);
      new ChangeChartProcessor().fixSizeFrame(marked.getVSChartInfo(), VizContext.of(marked));
      ChartVSAssemblyInfo classic = bubbleChart(null);
      new ChangeChartProcessor().fixSizeFrame(classic.getVSChartInfo(), VizContext.of(classic));

      assertEquals(8, paneModel(marked).getLargest());
      assertEquals(15, paneModel(classic).getLargest());
   }

   // no measure on X or Y, so the chart is forced to points
   @Test
   void aMarkedDimensionOnlyPointChartShowsLargest8() {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, null }) {
         ChartVSAssemblyInfo info = chart(new DefaultVSChartInfo(), GraphTypes.CHART_POINT, mark);
         VSChartInfo cinfo = info.getVSChartInfo();
         cinfo.addXField(dim("State"));
         cinfo.addYField(dim("Type"));
         cinfo.setSizeField(sizeRef(agg("Qty", "Sum")));
         cinfo.updateChartType(false);
         bind(info);

         assertTrue(ChangeChartProcessor.isFake(cinfo), "premise: a forced point chart");
         assertEquals(mark != null ? 8 : 15, paneModel(info).getLargest(), "mark " + mark);
      }
   }

   @Test
   void aSmallestOnlyEditKeepsLargest8AfterRevert() {
      ChartVSAssemblyInfo info = bubbleChart(VizMark.MODERN_LIGHT);
      bind(info);
      editSmallest(info, 3);

      LinearSizeFrameWrapper size = sizeWrapper(info);
      assertTrue(size.isChanged(), "premise: the edit makes the frame the author's");
      assertEquals(3, size.getSmallest());
      assertEquals(8, size.getLargest());

      // Revert
      info.setVizMark(null);
      assertEquals(20, widestBubble(info), 1);
   }

   @Test
   void aClassicBubbleChartKeeps15() {
      ChartVSAssemblyInfo info = bubbleChart(null);
      bind(info);

      assertEquals(15, paneModel(info).getLargest());

      editSmallest(info, 3);
      assertEquals(15, sizeWrapper(info).getLargest());
      assertEquals(34, widestBubble(info), 1);
   }

   @Test
   void anAuthorsLargestIsKept() {
      ChartVSAssemblyInfo info = bubbleChart(VizMark.MODERN_LIGHT);
      sizeWrapper(info).setLargest(12);
      bind(info);

      assertEquals(12, paneModel(info).getLargest());
   }

   @Test
   void aMarkedWordCloudKeeps15() {
      VSChartDimensionRef state = dim("State");
      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(state);
      text.setVisualFrameWrapper(new DefaultTextFrameWrapper());
      ChartVSAssemblyInfo info = chart(new DefaultVSChartInfo(), GraphTypes.CHART_POINT,
                                       VizMark.MODERN_LIGHT);
      info.getVSChartInfo().setTextField(text);
      info.getVSChartInfo().setSizeField(sizeRef(agg("Qty", "Sum")));
      info.getVSChartInfo().updateChartType(false);
      bind(info);

      assertTrue(GraphTypeUtil.isWordCloud(info.getVSChartInfo()), "premise: a word cloud");
      assertEquals(15, paneModel(info).getLargest());
   }

   @Test
   void aMarkedTreeShowsEdgeLargest6() {
      assertEquals(6, paneModel(bound(treeChart(VizMark.MODERN_LIGHT))).getLargest());
      assertEquals(15, paneModel(bound(treeChart(null))).getLargest());
   }

   @Test
   void aMarkedRadarLineShowsSize3() {
      for(int type : new int[] { GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR }) {
         assertEquals(3, chartSize(bound(radarChart(type, VizMark.MODERN_LIGHT))), "type " + type);
         assertEquals(1, chartSize(bound(radarChart(type, null))), "type " + type);
      }
   }

   @Test
   void aMarkedMilestoneShowsSize3() {
      assertEquals(3, milestoneSize(bound(ganttChart(VizMark.MODERN_LIGHT))));
      assertEquals(9, milestoneSize(bound(ganttChart(null))));
   }

   @Test
   void aMarkedGanttBarShowsSize15() {
      assertEquals(15, ganttBarSize(bound(ganttChart(VizMark.MODERN_LIGHT))));
      assertEquals(20, ganttBarSize(bound(ganttChart(null))));
   }

   // what a binding change does before the binding model is sent
   private static void bind(ChartVSAssemblyInfo info) {
      VSChartInfo cinfo = info.getVSChartInfo();
      new ChangeChartDataProcessor(cinfo, VizContext.of(info)).process();
      assertEquals(cinfo.getChartType(), cinfo.getRTChartType(), "premise: the type resolves");
   }

   private static ChartVSAssemblyInfo bound(ChartVSAssemblyInfo info) {
      bind(info);
      return info;
   }

   // the Size pane's frame, as the binding model carries it
   private static LinearSizeModel paneModel(ChartVSAssemblyInfo info) {
      return new SizeFrameModelFactory.LinearSizeFrameModelFactory()
         .createVisualFrameModel(sizeWrapper(info));
   }

   // the author moves only the smallest slider and applies, as changeChartAesthetic does
   private static void editSmallest(ChartVSAssemblyInfo info, double smallest) {
      LinearSizeModel model = paneModel(info);
      model.setField(info.getVSChartInfo().getSizeField().getFullName());
      model.setSmallest(smallest);
      model.setChanged(true);
      new SizeFrameModelFactory.LinearSizeFrameModelFactory()
         .updateVisualFrameWrapper(sizeWrapper(info), model);
      new ChangeChartDataProcessor(info.getVSChartInfo(), false, VizContext.of(info)).process();
   }

   private static LinearSizeFrameWrapper sizeWrapper(ChartVSAssemblyInfo info) {
      return (LinearSizeFrameWrapper) info.getVSChartInfo().getSizeField().getVisualFrameWrapper();
   }

   private static double chartSize(ChartVSAssemblyInfo info) {
      return staticModel((StaticSizeFrameWrapper) info.getVSChartInfo().getSizeFrameWrapper())
         .getSize();
   }

   private static double milestoneSize(ChartVSAssemblyInfo info) {
      ChartRef milestone = ((GanttChartInfo) info.getVSChartInfo()).getMilestoneField();
      return staticModel(((ChartAggregateRef) milestone).getSizeFrameWrapper()).getSize();
   }

   private static double ganttBarSize(ChartVSAssemblyInfo info) {
      ChartRef start = ((GanttChartInfo) info.getVSChartInfo()).getStartField();
      return staticModel(((ChartAggregateRef) start).getSizeFrameWrapper()).getSize();
   }

   private static StaticSizeModel staticModel(Object wrapper) {
      assertInstanceOf(StaticSizeFrameWrapper.class, wrapper, "premise: a static size");
      return new SizeFrameModelFactory.StaticSizeFrameModelFactory()
         .createVisualFrameModel((StaticSizeFrameWrapper) wrapper);
   }

   // the width of the largest bubble the chart draws
   private static double widestBubble(ChartVSAssemblyInfo info) {
      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef total = cinfo.getYField(0);
      VSAestheticRef size = (VSAestheticRef) cinfo.getSizeField();
      size.setRTDataRef((VSDataRef) size.getDataRef());
      Object[][] rows = {
         { "State", GraphUtil.getName(total), GraphUtil.getName(size.getDataRef()) },
         { "CA", 200.0, 3.0 },
         { "NY", 50.0, 7.0 },
         { "TX", 1200.0, 11.0 },
      };
      GraphGenerator gen = GraphGenerator.getGenerator(
         info, null, new DefaultDataSet(rows), new VariableTable(), null, XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);
      double widest = 0;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof PointVO vo) {
            widest = Math.max(widest, vo.getShapes()[0].getBounds2D().getWidth());
         }
      }

      return widest;
   }

   // State on X, Sum(Total) on Y and Sum(Qty) on Size
   private static ChartVSAssemblyInfo bubbleChart(VizMark mark) {
      ChartVSAssemblyInfo info = chart(new DefaultVSChartInfo(), GraphTypes.CHART_POINT, mark);
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.addXField(dim("State"));
      cinfo.addYField(agg("Total", "Sum"));
      cinfo.setSizeField(sizeRef(agg("Qty", "Sum")));
      cinfo.updateChartType(false);
      return info;
   }

   private static ChartVSAssemblyInfo treeChart(VizMark mark) {
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setSourceField(dim("Parent"));
      cinfo.setTargetField(dim("Child"));
      cinfo.setSizeField(sizeRef(agg("Qty", "Sum")));
      ChartVSAssemblyInfo info = chart(cinfo, GraphTypes.CHART_TREE, mark);
      cinfo.updateChartType(false);
      return info;
   }

   private static ChartVSAssemblyInfo radarChart(int chartType, VizMark mark) {
      RadarVSChartInfo cinfo = new RadarVSChartInfo();
      cinfo.addYField(agg("A", "Sum"));
      cinfo.addYField(agg("B", "Sum"));
      cinfo.addYField(agg("C", "Sum"));
      ChartVSAssemblyInfo info = chart(cinfo, chartType, mark);
      cinfo.updateChartType(false);
      return info;
   }

   private static ChartVSAssemblyInfo ganttChart(VizMark mark) {
      GanttVSChartInfo cinfo = new GanttVSChartInfo();
      cinfo.addYField(dim("Task"));
      cinfo.setStartField(agg("Start", "Min"));
      cinfo.setEndField(agg("End", "Max"));
      cinfo.setMilestoneField(agg("Due", "Max"));
      return chart(cinfo, GraphTypes.CHART_GANTT, mark);
   }

   private static ChartVSAssemblyInfo chart(VSChartInfo cinfo, int chartType, VizMark mark) {
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(cinfo);
      info.setVizMark(mark);
      return info;
   }

   private static VSAestheticRef sizeRef(VSChartAggregateRef measure) {
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(measure);
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());
      return size;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue(formula);
      return agg;
   }

   private MockedStatic<GraphTypeUtil> graphTypeUtil;
}
