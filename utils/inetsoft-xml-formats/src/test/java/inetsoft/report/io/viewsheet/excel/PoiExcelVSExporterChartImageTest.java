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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.graph.VGraph;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The chart picture of an Excel export has a bounded size (Bug #81607). A chart expanded to
 * its full content in a match layout export is never sliced, and drawing it at twice its size
 * allocated an image of tens of millions of pixels: a scheduled export ran out of heap in a
 * task container while encoding it.
 */
class PoiExcelVSExporterChartImageTest {
   /**
    * An ordinary chart is still drawn at twice its size, so its picture stays sharp.
    */
   @Test
   void drawsOrdinaryChartAtTwiceItsSize() {
      TestExporter exporter = new TestExporter();

      exporter.writeChart(chart(new Dimension(400, 300)), null, null, true);

      assertEquals(2, exporter.scale, 0.0);
      assertEquals(800, exporter.image.getWidth());
      assertEquals(600, exporter.image.getHeight());
   }

   /**
    * A chart expanded far beyond the screen is drawn at a smaller scale, so its picture
    * does not exceed the maximum number of pixels, and the picture keeps the chart's
    * aspect ratio.
    */
   @Test
   void boundsPictureOfExpandedChart() {
      TestExporter exporter = new TestExporter();

      exporter.writeChart(chart(new Dimension(3000, 2000)), null, null, true);

      BufferedImage image = exporter.image;
      long pixels = (long) image.getWidth() * image.getHeight();
      assertTrue(pixels <= MAX_PIXELS, "chart picture has " + pixels + " pixels");
      assertTrue(exporter.scale < 2, "scale " + exporter.scale);
      assertEquals(3000 * exporter.scale, image.getWidth(), 1.0);
      assertEquals(2000 * exporter.scale, image.getHeight(), 1.0);
   }

   /**
    * The largest chart an export can expand to (10000 pixels on each side) is drawn below
    * its own size rather than at full or double size.
    */
   @Test
   void scalesLargestExpandedChartBelowItsOwnSize() {
      double scale = PoiExcelVSExporter.getChartImageScale(new Dimension(10000, 10000), 2);

      assertTrue(scale < 1, "scale " + scale);
      assertTrue(Math.floor(10000 * scale) * Math.floor(10000 * scale) <= MAX_PIXELS);
   }

   private static ChartVSAssembly chart(Dimension size) {
      ChartVSAssemblyInfo info = Mockito.mock(ChartVSAssemblyInfo.class);
      ChartVSAssembly chart = Mockito.mock(ChartVSAssembly.class);
      when(chart.getPixelSize()).thenReturn(size);
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      when(chart.getAbsoluteName()).thenReturn("Chart1");
      return chart;
   }

   private static final long MAX_PIXELS = 4000L * 4000L;

   /**
    * Records the scale the chart is drawn at and the picture written to the sheet, without
    * painting the graph or needing a workbook.
    */
   private static final class TestExporter extends PoiExcelVSExporter {
      TestExporter() {
         super(Mockito.mock(ExcelContext.class), new ByteArrayOutputStream());
      }

      @Override
      protected void writeChart(Graphics2D g2, ChartVSAssembly chartAsm, VGraph vgraph,
                                double scale)
      {
         this.scale = scale;
      }

      @Override
      public ClientAnchor getAnchorPosition(VSAssemblyInfo info) {
         return null;
      }

      @Override
      public org.apache.poi.xssf.usermodel.XSSFPicture writePicture(BufferedImage img,
                                                                    ClientAnchor anchor)
      {
         this.image = img;
         return null;
      }

      private double scale;
      private BufferedImage image;
   }
}
