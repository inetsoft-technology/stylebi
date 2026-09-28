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
import inetsoft.graph.data.DataSet;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.pdf.PDFVSExporter;
import inetsoft.report.io.viewsheet.svg.SVGVSExporter;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.DataSpace;
import inetsoft.util.FileSystemService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #77224: exporting a plot-resized chart (unit width/height ratio stored as a percent of
 * the initial ratio) with "Expand Components" painted a graph about {@code percent} times
 * larger than the expanded assembly, so Excel/PowerPoint showed a zoomed, clipped chart.
 *
 * <p>{@code expandChart} sizes the assembly to the expanded graph of the pair built at the
 * original size, then the export re-generates the pair at the new size. {@code VGraphPair}
 * re-applies the percent to the new (larger) plot, so the expanded graph never fits the
 * assembly. An expanded chart must be written with a graph that fits the expanded assembly
 * (the real size graph, same as PDF), in the normal path and in the slice path.</p>
 *
 * <p>The tests run the real {@code export(...)} (prepareSheet, expandChart and the chart
 * branch of the assembly loop) and only capture the graph handed to {@code writeChart} (or
 * the {@code writeSliceChart} arguments, since painting needs batik which core lacks).</p>
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
   // Excel/PowerPoint (supportChartSlices), chart small enough for the normal path
   @Test
   void expandedWidthResizedChartIsWrittenAtTheExpandedAssemblySize() throws Exception {
      ChartVSAssemblyInfo info = plotResize(true);
      int originalWidth = info.getPixelSize().width;
      CapturingExporter exporter = new CapturingExporter();

      export(exporter);

      assertTrue(exporter.slices.isEmpty(), "the chart should not be sliced");
      assertEquals(1, exporter.written.size(), "the chart should be written once");
      Written written = exporter.written.get(0);
      assertTrue(written.assemblySize.width > originalWidth,
         "precondition: the plot-resized chart is expanded horizontally: " + originalWidth +
         " -> " + written.assemblySize.width);
      assertFits(written);
   }

   // Excel/PowerPoint slice path, chart area > EXPORT_SIZE^2
   @Test
   void expandedSlicedChartIsSlicedFromTheRealSizeGraph() throws Exception {
      Slice slice = exportSlicedChart();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) slice.assembly.getVSAssemblyInfo();
      Dimension content = written(slice.assembly, slice.pair.getRealSizeVGraph()).contentSize;

      assertTrue(info.getPixelSize().width > LARGE_WIDTH,
         "precondition: the plot-resized chart is expanded horizontally: " +
         info.getPixelSize().width);
      assertTrue(slice.pair.getExpandedVGraph().getSize().getWidth() > content.width + 1,
         "precondition: the re-generated expanded graph is wider than the assembly");
      // match=true makes writeSliceChart slice (and paint) the real size graph, which
      // is laid out at the expanded assembly size
      assertTrue(slice.match, "the slices of an expanded chart should use the real size graph");
      assertFits(written(slice.assembly, slice.pair.getRealSizeVGraph()));
   }

   // SVG/PNG writeSliceChart override
   @Test
   void expandedSlicedChartFitsInSvgExport() throws Exception {
      Slice slice = exportSlicedChart();
      // SVGSupport (batik) is not available in core, skip the constructor and only run the
      // writeSliceChart override
      SVGVSExporter svg = mock(SVGVSExporter.class, CALLS_REAL_METHODS);
      AbstractVSExporter exporter = svg;
      VGraph[] graph = new VGraph[1];
      doAnswer(inv -> graph[0] = inv.getArgument(1)).when(exporter)
         .writeChart(any(ChartVSAssembly.class), any(VGraph.class), any(), anyBoolean());

      exporter.writeSliceChart(slice.assembly, slice.data, slice.pair, slice.match, false);

      assertNotNull(graph[0], "the chart should be written");
      assertFits(written(slice.assembly, graph[0]));
   }

   // PDF writeSliceChart override, full export
   @Test
   void expandedSlicedChartFitsInPdfExport() throws Exception {
      ChartVSAssemblyInfo info = plotResize(true);
      info.setPixelSize(new Dimension(LARGE_WIDTH, LARGE_HEIGHT));
      CapturingPdfExporter exporter = new CapturingPdfExporter();

      export(exporter);

      assertEquals(1, exporter.slices, "precondition: the slice path should be used");
      assertEquals(1, exporter.written.size());
      assertTrue(exporter.written.get(0).assemblySize.width > LARGE_WIDTH,
         "precondition: the plot-resized chart is expanded horizontally");
      assertFits(exporter.written.get(0));
   }

   // a horizontal chart (dimension on y) resized vertically with a height percent
   @Test
   void expandedHeightResizedChartIsWrittenAtTheExpandedAssemblySize() throws Exception {
      ChartVSAssemblyInfo info = plotResize(false);
      VSChartInfo cinfo = info.getVSChartInfo();
      ChartRef[] xrefs = cinfo.getXFields();
      ChartRef[] yrefs = cinfo.getYFields();
      cinfo.removeXFields();
      cinfo.removeYFields();

      for(ChartRef ref : yrefs) {
         cinfo.addXField(ref);
      }

      for(ChartRef ref : xrefs) {
         cinfo.addYField(ref);
      }

      info.setPixelSize(new Dimension(300, 500));
      int originalHeight = info.getPixelSize().height;
      CapturingExporter exporter = new CapturingExporter();

      export(exporter);

      assertEquals(1, exporter.written.size());
      Written written = exporter.written.get(0);
      assertTrue(written.assemblySize.height > originalHeight,
         "precondition: the plot-resized chart is expanded vertically: " + originalHeight +
         " -> " + written.assemblySize.height);
      assertFits(written);
   }

   // match layout is not changed: the real size graph at the assembly size
   @Test
   void matchLayoutWritesTheChartAtTheAssemblySize() throws Exception {
      ChartVSAssemblyInfo info = plotResize(true);
      int originalWidth = info.getPixelSize().width;
      CapturingExporter exporter = new CapturingExporter();
      exporter.setMatchLayout(true);

      export(exporter);

      assertEquals(1, exporter.written.size());
      Written written = exporter.written.get(0);
      assertEquals(originalWidth, written.assemblySize.width, "match layout does not expand");
      assertFits(written);
   }

   /**
    * Set a plot resize percent on the chart, same as VSChartPlotResizeService.
    */
   private ChartVSAssemblyInfo plotResize(boolean width) {
      ChartVSAssembly chart = (ChartVSAssembly) getBox().getViewsheet().getAssembly(CHART);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();

      if(width) {
         cinfo.setWidthResized(true);
         cinfo.setUnitWidthRatioPercent(PERCENT);
      }
      else {
         cinfo.setHeightResized(true);
         cinfo.setUnitHeightRatioPercent(PERCENT);
      }

      return info;
   }

   private ViewsheetSandbox getBox() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   // same as VSExportService, "Expand Components" is the default (not match layout)
   private void export(AbstractVSExporter exporter) throws Exception {
      exporter.export(getBox(), "Current View", 0, null);
   }

   // export a large plot-resized chart with an Excel/PowerPoint-like exporter
   private Slice exportSlicedChart() throws Exception {
      ChartVSAssemblyInfo info = plotResize(true);
      info.setPixelSize(new Dimension(LARGE_WIDTH, LARGE_HEIGHT));
      CapturingExporter exporter = new CapturingExporter();

      export(exporter);

      assertTrue(exporter.written.isEmpty(), "the chart should not be written unsliced");
      assertEquals(1, exporter.slices.size(), "precondition: the chart should be sliced");
      return exporter.slices.get(0);
   }

   /**
    * The written graph must fit the (expanded) assembly content, otherwise it is zoomed and
    * clipped (Excel/PowerPoint/PDF) or drawn over the neighbors (SVG).
    */
   private static void assertFits(Written written) {
      Dimension content = written.contentSize;
      double w = written.graph.getSize().getWidth();
      double h = written.graph.getSize().getHeight();
      assertTrue(w <= content.width + 1 && h <= content.height + 1,
         "the written graph (" + w + "x" + h + ") should fit the chart content (" +
         content.width + "x" + content.height + ")");
   }

   private static Written written(ChartVSAssembly chart, VGraph graph) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      Dimension size = new Dimension(info.getPixelSize());
      Insets padding = info.getPadding();
      int title = info.isTitleVisible() ? info.getTitleHeight() : 0;
      Dimension content = new Dimension(size.width - padding.left - padding.right,
                                        size.height - padding.top - padding.bottom - title);
      return new Written(graph, size, content);
   }

   private static boolean isChart(VSAssembly assembly) {
      return assembly != null && CHART.equals(assembly.getAbsoluteName());
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   private record Written(VGraph graph, Dimension assemblySize, Dimension contentSize) {
   }

   private record Slice(ChartVSAssembly assembly, DataSet data, VGraphPair pair,
                        boolean match)
   {
   }

   /**
    * Excel/PowerPoint-like exporter (writes the chart slices) capturing the chart graphs.
    */
   private static final class CapturingExporter extends CSVVSExporter {
      @Override
      protected boolean supportChartSlices() {
         return true;
      }

      @Override
      protected boolean needExport(VSAssembly assembly) {
         return super.needExport(assembly) && isChart(assembly);
      }

      @Override
      protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph, DataSet data,
                                boolean imgOnly)
      {
         written.add(written(chartAsm, vgraph));
      }

      // painting the slices needs SVGSupport (batik), which is not available in core
      @Override
      protected void writeSliceChart(ChartVSAssembly assembly, DataSet data, VGraphPair pair,
                                     boolean match, boolean imgOnly)
      {
         slices.add(new Slice(assembly, data, pair, match));
      }

      private final List<Written> written = new ArrayList<>();
      private final List<Slice> slices = new ArrayList<>();
   }

   private static final class CapturingPdfExporter extends PDFVSExporter {
      CapturingPdfExporter() {
         super(LibManagerProvider.getInstance(), Cluster.getInstance(),
               FileSystemService.getInstance(), DataSpace.getDataSpace(),
               new ByteArrayOutputStream());
      }

      @Override
      protected boolean needExport(VSAssembly assembly) {
         return super.needExport(assembly) && isChart(assembly);
      }

      @Override
      protected void writeSliceChart(ChartVSAssembly assembly, DataSet data, VGraphPair pair,
                                     boolean match, boolean imgOnly)
      {
         slices++;
         super.writeSliceChart(assembly, data, pair, match, imgOnly);
      }

      @Override
      protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph, DataSet data,
                                boolean imgOnly)
      {
         written.add(written(chartAsm, vgraph));
      }

      private final List<Written> written = new ArrayList<>();
      private int slices;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ASSET_ID = "1^128^__NULL__^TEST_GraphRender";
   private static final String CHART = "Chart1";
   private static final double PERCENT = 2.0;
   // the chart area is larger than EXPORT_SIZE^2 so the export slices it
   private static final int LARGE_WIDTH = 1000;
   private static final int LARGE_HEIGHT = 1100;
}
