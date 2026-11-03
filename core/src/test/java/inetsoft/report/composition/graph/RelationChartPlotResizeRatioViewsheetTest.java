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
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80095: for a tree/network chart the plot-size ratio is an absolute node scale, so the
 * unit ratio written back after layout must equal the stored percent (base 1), not
 * plot/min * percent. Ordinary charts keep the plot/min base.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class RelationChartPlotResizeRatioViewsheetTest {
   @Test
   void treeKeepsUnitRatioEqualToPercentAcrossLayouts() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      VSChartInfo info = addChart(box, "Tree1", true);
      info.setUnitWidthRatio(2);
      info.setUnitWidthRatioPercent(2);
      info.setUnitHeightRatio(2);
      info.setUnitHeightRatioPercent(2);

      for(int i = 0; i < 3; i++) {
         box.getVGraphPair("Tree1", true, null);
         assertEquals(2, info.getUnitWidthRatio(), 1E-9, "width ratio after layout " + i);
         assertEquals(2, info.getUnitHeightRatio(), 1E-9, "height ratio after layout " + i);
         assertEquals(1, info.getInitialWidthRatio(), 1E-9, "initial width ratio " + i);
         assertEquals(1, info.getInitialHeightRatio(), 1E-9, "initial height ratio " + i);
      }
   }

   @Test
   void barKeepsPlotOverMinBase() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      VSChartInfo info = addChart(box, "Bar1", false);
      info.setUnitWidthRatio(2);
      info.setUnitWidthRatioPercent(2);

      box.getVGraphPair("Bar1", true, null);
      assertEquals(info.getInitialWidthRatio() * 2, info.getUnitWidthRatio(), 1E-9,
                   "non-relation unit ratio is still initial (plot/min) * percent");
   }

   private VSChartInfo addChart(ViewsheetSandbox box, String name, boolean tree) {
      Viewsheet vs = box.getViewsheet();
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source);

      if(tree) {
         RelationVSChartInfo info = new RelationVSChartInfo();
         info.setChartType(GraphTypes.CHART_TREE);
         info.setRTChartType(GraphTypes.CHART_TREE);
         info.setSourceField(dim("type"));
         info.setTargetField(dim("name"));
         chart.setVSChartInfo(info);
      }
      else {
         VSChartInfo info = chart.getVSChartInfo();
         info.addXField(dim("type"));
         VSChartAggregateRef agg = new VSChartAggregateRef();
         agg.setColumnValue("wind");
         agg.setFormulaValue("Sum");
         info.addYField(agg);
      }

      vs.addAssembly(chart);
      return chart.getVSChartInfo();
   }

   private static VSChartDimensionRef dim(String column) {
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, column)));
      dim.setGroupColumnValue(column);
      return dim;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
}
