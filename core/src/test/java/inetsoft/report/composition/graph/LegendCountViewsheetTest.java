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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern legend's entries are counted once, on a composer draw that a seed asked for, and the
 * verdict is saved; a viewer, metadata or empty draw never counts. TableView1 has 30 years and
 * 2 storm types.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class LegendCountViewsheetTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theComposerCountsOnce() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      assertEquals(Viewsheet.SHEET_DESIGN_MODE, box.getMode(), "premise: a composer sheet");
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      assertEquals(LegendsDescriptor.TOP, layout(info), "premise: the seeded bar position");
      assertTrue(info.isLegendCountPending(), "premise: a count is requested");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertEquals(LegendsDescriptor.RIGHT, layout(info));
      assertTrue(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
      assertEquals(LegendsDescriptor.RIGHT, pair.getEGraph().getLegendLayout());
      // the rerun plotted
      assertTrue(pair.isCompleted());
      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertNotNull(pair.getRealSizeVGraph());
   }

   @Test
   void aMetadataDrawLeavesTheCountPending() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      box.getViewsheet().getViewsheetInfo().setMetadata(true);
      ChartVSAssemblyInfo info = addBar(box, "type", "year");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertTrue(LegendEntryCount.count(pair.getEGraph()) > 0, "premise: entries drawn");
      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertTrue(info.isLegendCountPending());
      assertEquals(LegendsDescriptor.TOP, pair.getEGraph().getLegendLayout());
   }

   @Test
   void aDrawWithNoRowsLeavesTheCountPending() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.setPreConditionList(noRows());

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(0, LegendEntryCount.count(pair.getEGraph()), "premise: no entries drawn");
      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertTrue(info.isLegendCountPending());
      assertEquals(LegendsDescriptor.TOP, pair.getEGraph().getLegendLayout());
   }

   @Test
   void aNarrowLegendStaysTop() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      ChartVSAssemblyInfo info = addBar(box, "year", "type");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertFalse(info.isLegendCountPending());
      assertEquals(LegendsDescriptor.TOP, pair.getEGraph().getLegendLayout());
   }

   @Test
   void theViewerNeverCounts() throws Exception {
      ViewsheetSandbox box = sandbox(viewer);
      assertEquals(Viewsheet.SHEET_RUNTIME_MODE, box.getMode(), "premise: a viewer sheet");
      ChartVSAssemblyInfo info = addBar(box, "type", "year");

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertTrue(info.isLegendCountPending());
      assertEquals(LegendsDescriptor.TOP, pair.getEGraph().getLegendLayout());
   }

   @Test
   void noCountWithoutARequest() throws Exception {
      ViewsheetSandbox box = sandbox(composer);
      ChartVSAssemblyInfo info = addBar(box, "type", "year");
      info.clearLegendCountPending();

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertEquals(LegendsDescriptor.TOP, layout(info));
      assertFalse(info.isLegendCrowded());
      assertEquals(LegendsDescriptor.TOP, pair.getEGraph().getLegendLayout());
   }

   private static ViewsheetSandbox sandbox(RuntimeViewsheetExtension sheet) {
      return sheet.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   // a marked bar of Sum(wind) by the x column, with the color column on Color
   private static ChartVSAssemblyInfo addBar(ViewsheetSandbox box, String x, String color) {
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(source);
      VSChartInfo cinfo = chart.getVSChartInfo();
      cinfo.addXField(dim(x));

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.addYField(agg);

      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(dim(color));
      colorRef.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(colorRef);
      cinfo.setChartType(GraphTypes.CHART_BAR);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      vs.addAssembly(chart);
      return info;
   }

   // a type no row has
   private static ConditionList noRows() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("no such type");
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "type")), cond, 0));
      return list;
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

   private static OpenViewsheetEvent openViewsheetEvent(boolean viewer) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(viewer);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension composer = new RuntimeViewsheetExtension(openViewsheetEvent(false));

   @RegisterExtension
   RuntimeViewsheetExtension viewer = new RuntimeViewsheetExtension(openViewsheetEvent(true));

   private static final String TABLE = "TableView1";
   private static final String CHART = "ChartLegendCount";
}
