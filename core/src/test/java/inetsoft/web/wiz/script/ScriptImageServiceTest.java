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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.service.RenderNotReadyException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptImageServiceTest {

   private static byte[] fakePng(int w, int h) throws Exception {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
   }

   private RuntimeViewsheet viewsheetWithChart(String chartName) {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new ChartVSAssembly(vs, chartName));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   /** Stubs a VSExportService mock to write {@code fullPng} into whatever ExportResponse it's given. */
   private static void stubExport(VSExportService exportService, byte[] fullPng) throws Exception {
      doAnswer(invocation -> {
         ExportResponse response = invocation.getArgument(9);
         response.getOutputStream().write(fullPng);
         return null;
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());
   }

   @Test
   void rejectsANonExistentAssembly() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), mock(VSExportService.class));

      assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "NoSuchAssembly", null, null, TestPrincipals.user("alice", "host-org")));
   }

   @Test
   void returnsThePngOnASuccessfulRender() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      byte[] png = fakePng(800, 600);
      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService.ImageRenderResult result =
         new AssemblyImageService.ImageRenderResult(true, transfer, 800, 600);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), eq(800.0), eq(600.0), eq(800.0), eq(600.0),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(result);

      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      when(binaryTransferService.getData(transfer)).thenReturn(png);

      ScriptImageService svc = new ScriptImageService(
         imageService, binaryTransferService, mock(VSExportService.class));
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org"));

      assertArrayEquals(png, img.pngBytes());
      assertTrue(img.isPng());
      assertEquals(800, img.width());
      assertEquals(600, img.height());
   }

   @Test
   void clampsCallerSuppliedSizeToTheMaxDimension() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      byte[] png = fakePng(1600, 1600);
      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService.ImageRenderResult result =
         new AssemblyImageService.ImageRenderResult(true, transfer, 1600, 1600);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      // 3000x3000 requested; should be clamped to 1600x1600 before reaching the image service.
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), eq(1600.0), eq(1600.0), eq(1600.0), eq(1600.0),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(result);

      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      when(binaryTransferService.getData(transfer)).thenReturn(png);

      ScriptImageService svc = new ScriptImageService(
         imageService, binaryTransferService, mock(VSExportService.class));
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Chart1", 3000, 3000, TestPrincipals.user("alice", "host-org"));

      assertEquals(1600, img.width());
      verify(imageService).processGetAssemblyImage(
         eq(rvs), anyString(), eq(1600.0), eq(1600.0), eq(1600.0), eq(1600.0),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true));
   }

   @Test
   void throwsRenderNotReadyAfterExhaustingRetries() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      AssemblyImageService.ImageRenderResult notReady = new AssemblyImageService.ImageRenderResult(1);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(notReady);

      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      RenderNotReadyException ex = assertThrows(RenderNotReadyException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));
      assertEquals(1, ex.getRetryAfter());
      // 4 attempts per the mirrored WizVisualizationService retry constant.
      verify(imageService, times(4)).processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true));
   }

   @Test
   void fallsBackToTheWholeViewsheetWhenTheLightweightPathReturnsAPlaceholder() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Table1");
      ChartVSAssembly assembly =
         (ChartVSAssembly) rvs.getViewsheet().getAssembly("Table1");
      assembly.setPixelOffset(new Point(10, 20));
      assembly.setPixelSize(new Dimension(200, 100));

      byte[] placeholder = fakePng(1, 1);
      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService.ImageRenderResult result =
         new AssemblyImageService.ImageRenderResult(true, transfer, 1, 1);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(result);

      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      when(binaryTransferService.getData(transfer)).thenReturn(placeholder);

      // Deliberately does NOT match the assembly's pixelOffset/pixelSize above — the fallback no
      // longer crops, so the full export is returned (scaled to fit) regardless of assembly geometry.
      VSExportService exportService = mock(VSExportService.class);
      byte[] fullSheetPng = fakePng(400, 300);
      stubExport(exportService, fullSheetPng);

      ScriptImageService svc = new ScriptImageService(imageService, binaryTransferService, exportService);
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Table1", null, null, TestPrincipals.user("alice", "host-org"));

      assertTrue(img.isPng());
      assertEquals(400, img.width());
      assertEquals(300, img.height());
      assertNotNull(img.note());
      assertTrue(img.note().contains("Table1"));
   }

   @Test
   void successfulRenderHasNoNote() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      byte[] png = fakePng(800, 600);
      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService.ImageRenderResult result =
         new AssemblyImageService.ImageRenderResult(true, transfer, 800, 600);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), eq(800.0), eq(600.0), eq(800.0), eq(600.0),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(result);

      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      when(binaryTransferService.getData(transfer)).thenReturn(png);

      ScriptImageService svc = new ScriptImageService(
         imageService, binaryTransferService, mock(VSExportService.class));
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org"));

      assertNull(img.note());
   }

   @Test
   void getViewsheetImageExportsTheWholeSheetAndScalesToFit() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);
      byte[] fullSheetPng = fakePng(3200, 2400);
      stubExport(exportService, fullSheetPng);

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertTrue(img.isPng());
      // Downscaled to fit the default 800x600 cap, preserving the 4:3 aspect ratio.
      assertEquals(800, img.width());
      assertEquals(600, img.height());
   }

   @Test
   void getViewsheetImageDoesNotUpscaleASmallExport() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);
      byte[] fullSheetPng = fakePng(200, 150);
      stubExport(exportService, fullSheetPng);

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertEquals(200, img.width());
      assertEquals(150, img.height());
   }
}
