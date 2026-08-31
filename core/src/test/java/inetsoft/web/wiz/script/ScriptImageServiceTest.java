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
import inetsoft.uql.asset.SourceInfo;
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

   /**
    * The chart is given a source because {@code getAssemblyImage} refuses an unbound one up front,
    * before any render is attempted — see the pre-check there. A bare {@code new ChartVSAssembly}
    * has no {@code SourceInfo}, so leaving it unbound would make every test below assert against
    * that refusal rather than the render path it means to cover.
    */
   private RuntimeViewsheet viewsheetWithChart(String chartName) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, chartName);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Table1"));
      vs.addAssembly(chart);

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

   /**
    * The refusal has to happen <em>before</em> the render loop, not after it. A chart with no
    * source never produces a graph, so every attempt comes back not-ready and the loop ends in a
    * RenderNotReadyException advising a retry that cannot succeed — the defect this PR exists to
    * remove. {@code verifyNoInteractions} is the assertion that matters: it is what distinguishes
    * "refused up front" from "refused after burning through four attempts and 2s of sleep".
    *
    * <p>Note the chart is built inline rather than through {@code viewsheetWithChart}, which binds
    * a source precisely so the render-path tests do not land here.
    */
   @Test
   void refusesAnUnboundChartWithoutAttemptingARender() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      PairingException ex = assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("set_chart_source"),
                 "the refusal must name how to bind a source, got: [" + ex.getMessage() + "]");
      verifyNoInteractions(imageService);
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

   /**
    * Charts default to {@code titleVisible=true} ({@code ChartVSAssemblyInfo}'s own
    * {@code titleInfo} field init), so this hides the title explicitly — otherwise it would be
    * indistinguishable from {@link #attachesATitleNoteWhenTheChartsTitleIsVisible}.
    */
   @Test
   void successfulRenderHasNoNoteWhenTheTitleIsHidden() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1");
      // The runtime setter, not ChartVSAssembly.setTitleVisible (which only sets the design-time
      // value) — isTitleVisible() reads the runtime value, which nothing here executes/syncs.
      chart.getChartInfo().setTitleVisible(false);
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

   /**
    * Bug #76325 item 4: a chart's own title bar is drawn by the live browser as a DOM sibling of
    * the tile image, never inside the tile bytes — the single-assembly render path
    * ({@code AssemblyImageService.getChartImage}) has no title-drawing code at all, so a caller
    * asking for just the chart would otherwise get a plausible-but-incomplete image with no
    * warning that the (visible, correctly-configured) title was left out.
    */
   @Test
   void attachesATitleNoteWhenTheChartsTitleIsVisible() throws Exception {
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

      assertNotNull(img.note());
      assertTrue(img.note().contains("Chart1"));
      assertTrue(img.note().contains("get_viewsheet_image"));
   }

   /**
    * The whole-viewsheet/export path (no {@code target}) already paints the title via
    * {@code AbstractVSExporter} — it has nothing to warn about, regardless of title visibility.
    */
   @Test
   void getViewsheetImageNeverAttachesATitleNote() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

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

   /**
    * Bug #76331: a table/crosstab whose query hasn't run yet in this runtime made the export
    * pipeline block synchronously with no bound of its own, so the caller saw a plain 30s network
    * timeout instead of the "not ready yet, retry" signal a chart's own render path already had.
    * Simulates that with an export call that blocks past the bound {@code getViewsheetImage} now
    * enforces via {@link inetsoft.web.wiz.service.RenderWaitSupport} and asserts it surfaces as
    * {@link RenderNotReadyException} instead of hanging for the caller's full client timeout.
    */
   @Test
   void getViewsheetImageThrowsRenderNotReadyWhenTheExportTakesTooLong() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);

      doAnswer(invocation -> {
         // Longer than the 4x500ms bound getViewsheetImage waits before giving up.
         Thread.sleep(3_000);
         ExportResponse response = invocation.getArgument(9);
         response.getOutputStream().write(fakePng(200, 150));
         return null;
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);

      RenderNotReadyException ex = assertThrows(RenderNotReadyException.class,
         () -> svc.getViewsheetImage(rvs, null, null, TestPrincipals.user("alice", "host-org")));
      assertTrue(ex.getRetryAfter() > 0);
   }
}
