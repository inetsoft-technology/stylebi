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

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.DefaultComparer;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A scripted marked funnel coloured by its stage, drawn through the viewsheet: the script pass
 * re-sorts its colour values, and its legend still lists the top stage first. TableView1 has 2
 * storm types.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class FunnelColorOrderViewsheetTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aScriptedMarkedFunnelListsTheTopStageFirst() throws Exception {
      ViewsheetSandbox box = composer.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      ChartVSAssemblyInfo info = addFunnel(box, VizMark.MODERN_LIGHT, SCRIPT);

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertFalse(info.getScript().isEmpty(), "premise: a scripted chart");
      assertFalse(pair.getData().getComparator("type") instanceof DefaultComparer,
                  "premise: the script pass re-sorts the stage colours");
      List<String> topFirst = topFirst(pair.getData());
      assertEquals(2, topFirst.size(), "premise: two stages");
      assertEquals(topFirst, legend(pair.getRealSizeVGraph()));
   }

   @Test
   void anUnscriptedMarkedFunnelListsTheTopStageFirst() throws Exception {
      ViewsheetSandbox box = composer.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      addFunnel(box, VizMark.MODERN_LIGHT, null);

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      assertEquals(topFirst(pair.getData()), legend(pair.getRealSizeVGraph()));
   }

   @Test
   void aScriptedClassicFunnelIsUnchanged() throws Exception {
      ViewsheetSandbox box = composer.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      addFunnel(box, null, SCRIPT);

      VGraphPair pair = box.getVGraphPair(CHART, true, null);

      assertFalse(pair.isFailed(), () -> String.valueOf(pair.getFailure()));
      // the classic baseline: value ascending, reversed for the stacked element
      assertEquals(topFirst(pair.getData()), legend(pair.getRealSizeVGraph()));
   }

   // the stages by their Sum(wind) totals, largest first
   private static List<String> topFirst(DataSet data) {
      Map<String, Double> totals = new HashMap<>();

      for(int i = 0; i < data.getRowCount(); i++) {
         totals.merge(String.valueOf(data.getData("type", i)),
                      ((Number) data.getData("Sum(wind)", i)).doubleValue(), Double::sum);
      }

      List<String> stages = new ArrayList<>(totals.keySet());
      stages.sort(Comparator.comparingDouble((String s) -> totals.get(s)).reversed());
      return stages;
   }

   // top to bottom, then left to right
   private static List<String> legend(VGraph vgraph) {
      List<LegendItem> items = new ArrayList<>();
      LegendGroup group = vgraph.getLegendGroup();

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);

         for(int j = 0; j < legend.getVisualCount(); j++) {
            if(legend.getVisual(j) instanceof LegendItem) {
               items.add((LegendItem) legend.getVisual(j));
            }
         }
      }

      items.sort(Comparator.comparingDouble((LegendItem k) -> -k.getBounds().getY())
                    .thenComparingDouble(k -> k.getBounds().getX()));
      List<String> values = new ArrayList<>();
      items.forEach(k -> values.add(String.valueOf(k.getValue())));
      return values;
   }

   // a funnel of Sum(wind) by storm type, coloured by the type
   private static ChartVSAssemblyInfo addFunnel(ViewsheetSandbox box, VizMark mark,
                                                String script)
   {
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo(source);
      VSChartInfo cinfo = chart.getVSChartInfo();

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.addXField(agg);
      cinfo.addYField(dim("type"));

      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(dim("type"));
      colorRef.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(colorRef);
      cinfo.setChartType(GraphTypes.CHART_FUNNEL);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(mark);
      info.setScript(script);
      info.setScriptEnabled(script != null);
      info.initDefaultFormat();
      vs.addAssembly(chart);
      return info;
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

   private static final String TABLE = "TableView1";
   private static final String CHART = "ChartFunnelOrder";
   private static final String SCRIPT = "var unused = 1;";
}
