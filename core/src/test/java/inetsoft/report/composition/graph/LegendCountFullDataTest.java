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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The composer's legend count judges the chart's own bound data: what the author does to the
 * view (selections, zoom, excluded marks, brushing) does not narrow it, and the chart's own
 * filter and Top-N still do. TableView1 has 30 years.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class LegendCountFullDataTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();
   }

   @AfterEach
   void reset() {
      ThreadContext.setContextPrincipal(oldPrincipal);
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aSelectionDoesNotNarrowTheCount() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      assertTrue(years.size() > LegendEntryCount.CROWDED_ABOVE, "premise: years crowd the legend");
      SelectionListVSAssembly list = addList(box, "YearList", "year");
      resetAll(box);
      click(box, list, years.get(0));
      ChartVSAssemblyInfo info = addBar(box, "type", "year");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(1, LegendEntryCount.count(pair.getEGraph()), "premise: one year drawn");
      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertTrue(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
      assertNotNull(box.getData(CHART, false, DataMap.BINDING), "the bound data was fetched");
   }

   @Test
   void aDrawTheViewDoesNotFilterFetchesNothingMore() throws Exception {
      ViewsheetSandbox box = sandbox();
      ChartVSAssemblyInfo info = addBar(box, "type", "year");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertFalse(info.isLegendCountPending());
      assertNull(box.getData(CHART, false, DataMap.BINDING), "no other query ran");
   }

   @Test
   void aChartWithoutALegendFetchesNothingMore() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      SelectionListVSAssembly list = addList(box, "YearList", "year");
      resetAll(box);
      click(box, list, years.get(0));
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.getVSChartInfo().setColorField(null);

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(0, LegendEntryCount.count(pair.getEGraph()), "premise: no legend drawn");
      assertTrue(info.isLegendCountPending());
      assertNull(box.getData(CHART, false, DataMap.BINDING), "no other query ran");
   }

   @Test
   void aBrushedChartCountsItsFullLegendWithoutAnotherQuery() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      Viewsheet vs = box.getViewsheet();
      ChartVSAssembly brusher = new ChartVSAssembly(vs, "Brusher");
      brusher.setSourceInfo(source(vs));
      brusher.getVSChartInfo().addXField(dim("year"));
      brusher.getVSChartInfo().addYField(sumWind());
      vs.addAssembly(brusher);
      brusher.setBrushSelection(selection(years.subList(0, 1)));
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      assertNotNull(box.getBrushingChart(CHART), "premise: the chart is brushed");
      assertTrue(rows(box.getData(CHART)) < rows(box.getData(CHART, true, DataMap.ZOOM)),
                 "premise: the brush narrows the chart's data");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertFalse(info.isLegendCountPending());
      assertNull(box.getData(CHART, false, DataMap.BINDING), "no other query ran");
   }

   @Test
   void theChartsOwnFilterStillCounts() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      SelectionListVSAssembly list = addList(box, "TypeList", "type");
      resetAll(box);
      click(box, list, list.getSelectionList().getSelectionValue(0).getValue());
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.setPreConditionList(oneOfYears(years.subList(0, 2)));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertNotNull(box.getData(CHART, false, DataMap.BINDING), "premise: the bound data counted");
      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void theChartsTopNStillCounts() throws Exception {
      ViewsheetSandbox box = sandbox();
      SelectionListVSAssembly list = addList(box, "TypeList", "type");
      resetAll(box);
      click(box, list, list.getSelectionList().getSelectionValue(0).getValue());
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      VSDimensionRef year = (VSDimensionRef) info.getVSChartInfo().getColorField().getDataRef();
      // the top 2 years of each type: at most 4 for both types
      year.setRankingOptionValue(XCondition.TOP_N + "");
      year.setRankingNValue("2");
      year.setRankingColValue("Sum(wind)");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(2, LegendEntryCount.count(pair.getEGraph()), "premise: two years drawn");
      assertNotNull(box.getData(CHART, false, DataMap.BINDING), "premise: the bound data counted");
      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void aZoomDoesNotNarrowTheCount() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.setZoomSelection(selection(years.subList(0, 1)));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(1, LegendEntryCount.count(pair.getEGraph()), "premise: one year drawn");
      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertTrue(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void excludedMarksDoNotNarrowTheCount() throws Exception {
      ViewsheetSandbox box = sandbox();
      List<String> years = years(box);
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.setExcludeSelection(selection(years.subList(2, years.size())));

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(2, LegendEntryCount.count(pair.getEGraph()), "premise: two years drawn");
      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertTrue(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
   }

   private ViewsheetSandbox sandbox() {
      ViewsheetSandbox box = composer.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      assertEquals(Viewsheet.SHEET_DESIGN_MODE, box.getMode(), "premise: a composer sheet");
      return box;
   }

   // a marked bar of Sum(wind) by the x column, with the color column on Color
   private static ChartVSAssemblyInfo addBar(ViewsheetSandbox box, String x, String color) {
      Viewsheet vs = box.getViewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(source(vs));
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.addXField(dim(x));
      cinfo.addYField(sumWind());

      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(dim(color));
      colorRef.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(colorRef);
      cinfo.setChartType(GraphTypes.CHART_BAR);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      vs.addAssembly(chart);
      assertTrue(info.isLegendCountPending(), "premise: a count is requested");
      return info;
   }

   private static VSChartAggregateRef sumWind() {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      return agg;
   }

   // the chart's own filter: year is one of the years
   private static ConditionList oneOfYears(List<String> years) {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.ONE_OF);

      for(String year : years) {
         cond.addValue(Integer.valueOf(year));
      }

      ColumnRef column = new ColumnRef(new AttributeRef(null, "year"));
      column.setDataType(XSchema.INTEGER);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column, cond, 0));
      return list;
   }

   private static int rows(Object data) {
      DataSet dataSet = (DataSet) data;
      return dataSet == null ? -1 : dataSet.getRowCount();
   }

   private static SourceInfo source(Viewsheet vs) {
      return (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
   }

   // one mark per year
   private static VSSelection selection(List<String> years) {
      VSSelection selection = new VSSelection();

      for(String year : years) {
         VSPoint point = new VSPoint();
         point.addValue(new VSFieldValue("year", year));
         selection.addPoint(point);
      }

      return selection;
   }

   // the distinct years of the bound rows, ascending
   private static List<String> years(ViewsheetSandbox box) {
      Object assembly = box.getViewsheet().getBaseWorksheet().getAssembly(
         source(box.getViewsheet()).getSource());

      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      XTable data = ((EmbeddedTableAssembly) assembly).getEmbeddedData();
      int col = -1;

      for(int c = 0; c < data.getColCount(); c++) {
         if("year".equals(String.valueOf(data.getObject(0, c)))) {
            col = c;
         }
      }

      SortedSet<String> years = new TreeSet<>();

      for(int r = 1; data.moreRows(r); r++) {
         Object value = data.getObject(r, col);

         if(value != null) {
            years.add(Tool.getDataString(value));
         }
      }

      return new ArrayList<>(years);
   }

   private static SelectionListVSAssembly addList(ViewsheetSandbox box, String name, String col) {
      Viewsheet vs = box.getViewsheet();
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, name);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) selection.getInfo();
      info.setTableName(source(vs).getSource());
      info.setDataRef(new ColumnRef(new AttributeRef(null, col)));
      vs.addAssembly(selection);
      return selection;
   }

   private static void resetAll(ViewsheetSandbox box) throws Exception {
      box.lockWrite();

      try {
         box.reset(null, box.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true,
                   true, null);
      }
      finally {
         box.unlockWrite();
      }
   }

   // a click on one value of a list, applied as VSSelectionService.applySelection does
   private static void click(ViewsheetSandbox box, SelectionListVSAssembly list, String value)
      throws Exception
   {
      box.lockWrite();

      try {
         SelectionList slist = (SelectionList) list.getSelectionList().clone();
         boolean found = false;

         for(SelectionValue svalue : slist.getSelectionValues()) {
            if(value.equals(svalue.getValue())) {
               svalue.setSelected(true);
               found = true;
            }
         }

         assertTrue(found, value + " not in " + list.getName());
         slist.setSelectionValues(
            SelectionVSUtil.shrinkSelectionValues(list, slist.getSelectionValues()));
         int hint = list.setStateSelectionList(slist);
         box.processChange(list.getAbsoluteName(), hint, new ChangedAssemblyList(true));
      }
      finally {
         box.unlockWrite();
      }
   }

   private static int layout(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getLegendsDescriptor().getLayout();
   }

   private static VSChartDimensionRef dim(String col) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, col)));
      dim.setGroupColumnValue(col);
      dim.setOrder(XConstants.SORT_ASC);
      return dim;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(false);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension composer = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal oldPrincipal;
   private static final String TABLE = "TableView1";
   private static final String CHART = "ChartLegendCount";
}
