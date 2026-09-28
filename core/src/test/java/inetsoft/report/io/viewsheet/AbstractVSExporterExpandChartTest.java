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
package inetsoft.report.io.viewsheet;

import inetsoft.graph.VGraph;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77224: exporting a plot-resized chart (unit width ratio stored as a percent of the
 * initial ratio) with "Expand Components" painted a graph about {@code percent} times
 * wider than the expanded assembly, so Excel/PowerPoint showed a zoomed, clipped chart.
 *
 * <p>{@code expandChart} sizes the assembly to the expanded graph of the pair built at the
 * original size, then the export re-generates the pair at the new size. {@code VGraphPair}
 * re-applies the percent to the new (larger) plot, so the expanded graph never fits the
 * assembly. The fix writes the real size graph, which is laid out at the expanded assembly
 * size (same as PDF), for a chart that has been expanded.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class AbstractVSExporterExpandChartTest {
   @Test
   void expandedPlotResizedChartIsWrittenAtTheExpandedAssemblySize() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet origViewsheet = box.getViewsheet();
      // same as AbstractVSExporter.export(), expand a copy of the viewsheet
      Viewsheet vs = origViewsheet.clone();
      box.setViewsheet(vs, false);

      try {
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(CHART);
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         VSChartInfo cinfo = info.getVSChartInfo();
         // the user widened the bars with plot resize (see VSChartPlotResizeService)
         cinfo.setWidthResized(true);
         cinfo.setUnitWidthRatioPercent(PERCENT);
         int originalWidth = info.getPixelSize().width;

         TestExporter exporter = new TestExporter(vs, box);
         assertFalse(exporter.isRealSizeChart(CHART));

         // same as prepareSheet() with "Expand Components"
         exporter.expandChart(chart, true);
         exporter.expandChart(chart, false);

         int expandedWidth = info.getPixelSize().width;
         assertTrue(expandedWidth > originalWidth,
            "the plot-resized chart should be expanded horizontally: " + originalWidth +
            " -> " + expandedWidth);

         // the pair used by writeAssemblies(), re-generated at the expanded size
         VGraphPair pair = box.getVGraphPair(CHART, true, null, true, 1);
         Insets padding = info.getPadding();
         int contentWidth = expandedWidth - padding.left - padding.right;

         // the plot resize percent is applied to the new plot size again, so the expanded
         // graph of the new pair never fits the expanded assembly (it is clipped)
         assertTrue(pair.getExpandedVGraph().getSize().getWidth() > contentWidth + 1,
            "precondition: the re-generated expanded graph is wider than the assembly");

         VGraph graph = exporter.getChartGraph(CHART, pair);

         assertSame(pair.getRealSizeVGraph(), graph,
            "Excel/PowerPoint should write the real size graph of an expanded chart");
         assertEquals(contentWidth, graph.getSize().getWidth(), 1,
            "the written graph (" + graph.getSize().getWidth() + ") should fit the " +
            "expanded assembly content width (" + contentWidth + ")");
      }
      finally {
         box.setViewsheet(origViewsheet, false);
      }
   }

   @Test
   void notExpandedChartKeepsTheExpandedGraph() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TestExporter exporter = new TestExporter(box.getViewsheet(), box);
      VGraphPair pair = box.getVGraphPair(CHART, true, null, true, 1);

      // a chart that was not expanded keeps the existing (scroll) behavior
      assertFalse(exporter.isRealSizeChart(CHART));
      assertSame(pair.getExpandedVGraph(), exporter.getChartGraph(CHART, pair));

      // match layout always writes the real size graph
      exporter.setMatchLayout(true);
      assertTrue(exporter.isRealSizeChart(CHART));
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   private static final class TestExporter extends CSVVSExporter {
      TestExporter(Viewsheet vs, ViewsheetSandbox box) {
         this.viewsheet = vs;
         this.box = box;
      }

      // Excel/PowerPoint write the chart slices (expanded graph)
      @Override
      protected boolean supportChartSlices() {
         return true;
      }

      void expandChart(VSAssembly chart, boolean expandRow) throws Exception {
         Method method = AbstractVSExporter.class.getDeclaredMethod(
            "expandChart", VSAssembly.class, ViewsheetSandbox.class, boolean.class);
         method.setAccessible(true);
         method.invoke(this, chart, box, expandRow);
      }
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ASSET_ID = "1^128^__NULL__^TEST_GraphRender";
   private static final String CHART = "Chart1";
   private static final double PERCENT = 2.0;
}
