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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.SubmitVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.RelationVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TipVSAssemblyInfo;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportInProgressException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

   /** Builds a viewsheet with a single SelectionList assembly set to the given show type. */
   private RuntimeViewsheet viewsheetWithSelectionList(String name, int showType) {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly selectionList = new SelectionListVSAssembly(vs, name);
      ((SelectionListVSAssemblyInfo) selectionList.getVSAssemblyInfo()).setShowType(showType);
      vs.addAssembly(selectionList);

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

   /**
    * Bug #76647 / VCA-003 gap (b): a tree/network/circular chart with a bound table but no
    * 'source'/'target' can never produce a plotted graph — {@code AssemblyImageService}'s
    * {@code ChartVSAssembly} branch treats that identically to "still computing" and returns
    * {@code retryAfter=1} forever, so without this refusal the render loop below burns all
    * {@code RENDER_MAX_ATTEMPTS} attempts and then throws {@link RenderNotReadyException} —
    * advising a retry that can never succeed. Same shape as
    * {@link #refusesAnUnboundChartWithoutAttemptingARender}: refused up front, before the
    * {@code AssemblyImageService} mock is ever touched.
    */
   @Test
   void refusesARelationChartMissingSourceAndTargetWithoutAttemptingARender() {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1");
      RelationVSChartInfo relationInfo = new RelationVSChartInfo();
      relationInfo.setChartType(GraphTypes.CHART_TREE);
      chart.setVSChartInfo(relationInfo);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      PairingException ex = assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("'source' and 'target'"),
                 "must name both missing shelves, got: [" + ex.getMessage() + "]");
      assertTrue(ex.getMessage().contains("set_chart_single_shelf"),
                 "the refusal must name how to bind them, got: [" + ex.getMessage() + "]");
      verifyNoInteractions(imageService);
   }

   /** Same as above, but only 'target' is missing — the message names only the missing one. */
   @Test
   void refusesARelationChartMissingOnlyTargetWithoutAttemptingARender() {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1");
      RelationVSChartInfo relationInfo = new RelationVSChartInfo();
      relationInfo.setChartType(GraphTypes.CHART_NETWORK);
      relationInfo.setSourceField(new VSChartDimensionRef(new AttributeRef("NODE")));
      chart.setVSChartInfo(relationInfo);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      PairingException ex = assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("'target'"),
                 "must name the missing shelf, got: [" + ex.getMessage() + "]");
      assertFalse(ex.getMessage().contains("'source' and 'target'"),
                 "must not claim 'source' is also missing, got: [" + ex.getMessage() + "]");
      verifyNoInteractions(imageService);
   }

   /**
    * A relation chart with BOTH 'source' and 'target' bound is unaffected by the new guard — it
    * proceeds to the normal render path exactly like a non-relation chart does.
    */
   @Test
   void rendersARelationChartWithBothSourceAndTargetBound() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1");
      RelationVSChartInfo relationInfo = new RelationVSChartInfo();
      relationInfo.setChartType(GraphTypes.CHART_TREE);
      relationInfo.setSourceField(new VSChartDimensionRef(new AttributeRef("PARENT")));
      relationInfo.setTargetField(new VSChartDimensionRef(new AttributeRef("CHILD")));
      chart.setVSChartInfo(relationInfo);

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
   }

   /**
    * A non-relation chart type (e.g. bar) has no 'source'/'target' concept at all — the default
    * {@code viewsheetWithChart} builds a plain {@code VSChartInfo}, which is not a
    * {@code RelationChartInfo}, so the new guard's {@code instanceof} check never fires and the
    * existing "no table bound" / render behavior is unaffected. Regression guard for bug #76647 /
    * VCA-003 gap (b) not over-reaching into unrelated chart types.
    */
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
    * Bug #76541 round 2: {@code getAssemblyImage}'s own fallback branch was constructing a brand
    * new {@code ChartImage} with only its own "can't be rendered on its own" text, discarding
    * whatever note the internal {@code getViewsheetImage} call had already computed — so a caller
    * targeting a dropdown-mode SelectionList by name directly (the most natural way to
    * investigate this bug) got no disclosure at all. SelectionList is never one of the types
    * {@code AssemblyImageService} supports directly, so it always takes this fallback branch.
    */
   @Test
   void assemblyFallbackNoteIncludesTheDropdownDisclosureWhenTargetIsADropdownSelectionList() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithSelectionList(
         "Selection1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

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

      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(imageService, binaryTransferService, exportService);
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Selection1", null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("can't be rendered on its own"),
                 "must keep the fallback's own explanation, got: [" + img.note() + "]");
      assertTrue(img.note().contains("dropdown-mode selection list") && img.note().contains("empty box"),
                 "must include the dropdown disclosure, got: [" + img.note() + "]");
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

   /**
    * Bug #76541: {@code VSSelectionListHelper.write} only draws a selection list's rows/values
    * when {@code getShowType() == SelectionVSAssemblyInfo.LIST_SHOW_TYPE} — no export format
    * (SVG/PNG/HTML/PDF) substitutes any dropdown chrome, so a dropdown-mode selection list renders
    * as a blank box with no warning. Composer/Viewer render it correctly; only static exports are
    * affected.
    */
   @Test
   void attachesANoteWhenTheViewsheetHasADropdownSelectionList() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithSelectionList(
         "Selection1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("Selection1"));
   }

   /**
    * Regression guard for the note added for bug #76541 — a list-mode SelectionList is drawn
    * correctly by the export pipeline, so it must not start firing this note too.
    */
   @Test
   void doesNotAttachANoteWhenTheSelectionListIsInListMode() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithSelectionList(
         "Selection1", SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNull(img.note());
   }

   /**
    * Multiple dropdown-mode SelectionLists in one viewsheet must all be named, comma-joined, with
    * plural grammar ("are ... lists" / "them") rather than the singular phrasing used for one.
    */
   @Test
   void notesAllDropdownSelectionListsWithPluralPhrasingWhenThereAreTwoOrMore() throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly first = new SelectionListVSAssembly(vs, "Selection1");
      ((SelectionListVSAssemblyInfo) first.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      vs.addAssembly(first);
      SelectionListVSAssembly second = new SelectionListVSAssembly(vs, "Selection2");
      ((SelectionListVSAssemblyInfo) second.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      vs.addAssembly(second);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("Selection1"));
      assertTrue(img.note().contains("Selection2"));
      assertTrue(img.note().contains("are dropdown-mode selection lists"));
      assertTrue(img.note().contains("them"));
   }

   /**
    * A list-mode SelectionList renders correctly and must not be swept into the note just because
    * a sibling dropdown-mode SelectionList is also on the sheet — only the dropdown one is named,
    * with singular phrasing.
    */
   @Test
   void notesOnlyTheDropdownSelectionListWhenAListModeOneIsAlsoPresent() throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly listMode = new SelectionListVSAssembly(vs, "ListMode1");
      ((SelectionListVSAssemblyInfo) listMode.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      vs.addAssembly(listMode);
      SelectionListVSAssembly dropdown = new SelectionListVSAssembly(vs, "Dropdown1");
      ((SelectionListVSAssemblyInfo) dropdown.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      vs.addAssembly(dropdown);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("Dropdown1"));
      assertFalse(img.note().contains("ListMode1"));
      assertTrue(img.note().contains("is a dropdown-mode selection list"));
   }

   private static SelectionListVSAssembly dropdownList(Viewsheet vs, String name) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      ((SelectionListVSAssemblyInfo) list.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      vs.addAssembly(list);
      return list;
   }

   private static SelectionTreeVSAssembly tree(Viewsheet vs, String name, int showType) {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);
      tree.getSelectionTreeInfo().setShowType(showType);
      vs.addAssembly(tree);
      return tree;
   }

   private String wholeSheetNote(Viewsheet vs) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      return svc.getViewsheetImage(rvs, null, null, TestPrincipals.user("alice", "host-org"))
         .note();
   }

   @Test
   void notesADropdownSelectionTreeOnTheWholeSheet() throws Exception {
      Viewsheet vs = new Viewsheet();
      tree(vs, "Tree1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"Tree1\" is a dropdown-mode selection tree"), note);
      assertFalse(note.contains("empty box"), note);
   }

   @Test
   void notesADropdownSelectionTreeOnTheTargetFallbackPath() throws Exception {
      Viewsheet vs = new Viewsheet();
      tree(vs, "Tree1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(new AssemblyImageService.ImageRenderResult(true, transfer, 1, 1));
      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      when(binaryTransferService.getData(transfer)).thenReturn(fakePng(1, 1));
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(imageService, binaryTransferService, exportService);
      ScriptImageService.ChartImage img = svc.getAssemblyImage(
         rvs, "Tree1", null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("dropdown-mode selection tree"), img.note());
   }

   @Test
   void doesNotNoteAListModeSelectionTree() throws Exception {
      Viewsheet vs = new Viewsheet();
      tree(vs, "Tree1", SelectionVSAssemblyInfo.LIST_SHOW_TYPE);

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void doesNotNoteAHiddenDropdownSelectionList() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "Shown1");
      dropdownList(vs, "Hidden1").getVSAssemblyInfo().setVisibleValue("false");

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("Shown1"), note);
      assertFalse(note.contains("Hidden1"), note);
   }

   @Test
   void doesNotNoteAHiddenDropdownSelectionTree() throws Exception {
      Viewsheet vs = new Viewsheet();
      tree(vs, "Tree1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
         .getVSAssemblyInfo().setVisibleValue("false");

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void doesNotNoteADropdownInANonSelectedTab() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "TabA");
      dropdownList(vs, "TabB");
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setAssemblies(new String[] {"TabA", "TabB"});
      ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setSelected("TabA");
      vs.addAssembly(tab);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("TabA"), note);
      assertFalse(note.contains("TabB"), note);
   }

   @Test
   void doesNotNoteADropdownInAHiddenContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "Inner1");
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setAssemblies(new String[] {"Inner1"});
      group.getVSAssemblyInfo().setVisibleValue("false");
      vs.addAssembly(group);

      assertNull(wholeSheetNote(vs));
   }

   // -- Bug 79193: a list in a Selection Container is drawn as a title row, not an empty box --

   private static CurrentSelectionVSAssembly selectionContainer(Viewsheet vs, String name,
                                                                String... children)
   {
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, name);
      container.setAssemblies(children);
      vs.addAssembly(container);
      return container;
   }

   @Test
   void standaloneDropdownListIsStillNamedWhenAContainerExists() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "Alone");
      selectionContainer(vs, "Cont");

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"Alone\" is a dropdown-mode selection list"), note);
   }

   @Test
   void doesNotNoteDropdownListsInASelectionContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "InCont1");
      dropdownList(vs, "InCont2");
      selectionContainer(vs, "Cont", "InCont1", "InCont2");

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void notesOnlyTheStandaloneListWhenAContainerListIsAlsoPresent() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "InCont");
      dropdownList(vs, "Alone");
      selectionContainer(vs, "Cont", "InCont");

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"Alone\" is a dropdown-mode selection list"), note);
      assertFalse(note.contains("InCont"), note);
   }

   @Test
   void stillNotesDropdownListsInATabOrGroupContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "TabList");
      dropdownList(vs, "GroupList");
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setAssemblies(new String[] {"TabList"});
      vs.addAssembly(tab);
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setAssemblies(new String[] {"GroupList"});
      vs.addAssembly(group);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"TabList\""), note);
      assertTrue(note.contains("\"GroupList\""), note);
   }

   @Test
   void doesNotNoteAListInASelectionContainerNestedInAGroupContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "L1");
      selectionContainer(vs, "Cont", "L1");
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setAssemblies(new String[] {"Cont"});
      vs.addAssembly(group);

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void doesNotNoteAListInAHiddenSelectionContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "L1");
      selectionContainer(vs, "Cont", "L1").getVSAssemblyInfo().setVisibleValue("false");

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void stillNotesAnInContainerDropdownTreeWhileSkippingTheContainerList() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "ListIn");
      tree(vs, "TreeIn", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      selectionContainer(vs, "Cont", "ListIn", "TreeIn");

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"TreeIn\" is a dropdown-mode selection tree"), note);
      assertFalse(note.contains("ListIn"), note);
   }

   @Test
   void notesAMixedListAndTreeWithCombinedWording() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "List1");
      tree(vs, "Tree1", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"List1\", \"Tree1\" are dropdown-mode selection lists/trees"),
                 note);
      assertTrue(note.contains("them"), note);
   }


   // ── Bug 78704: the notes cover assemblies inside embedded viewsheets ──

   private static Viewsheet embedded(Viewsheet parent, String name) {
      Viewsheet emb = new Viewsheet();
      emb.getVSAssemblyInfo().setName(name);
      parent.addAssembly(emb);
      return emb;
   }

   @Test
   void notesADropdownListAndSubmitInsideAnEmbeddedViewsheetByAbsoluteName() throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet emb = embedded(vs, "EmbVS");
      dropdownList(emb, "EmbList");
      emb.addAssembly(new SubmitVSAssembly(emb, "EmbSubmit"));

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"EmbVS.EmbList\" is a dropdown-mode selection list"), note);
      assertTrue(note.contains("\"EmbVS.EmbSubmit\" is a Submit assembly"), note);
   }

   @Test
   void notesATopLevelAndAnEmbeddedDropdownTogether() throws Exception {
      Viewsheet vs = new Viewsheet();
      dropdownList(vs, "Top1");
      dropdownList(embedded(vs, "EmbVS"), "EmbList");

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("Top1") && note.contains("EmbVS.EmbList"), note);
      assertTrue(note.contains("are dropdown-mode selection lists"), note);
   }

   @Test
   void notesAChildOfATwoLevelEmbeddedViewsheet() throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet outerEmb = embedded(vs, "Outer");
      Viewsheet innerEmb = embedded(outerEmb, "Inner");
      dropdownList(innerEmb, "DeepList");
      innerEmb.addAssembly(new SubmitVSAssembly(innerEmb, "DeepSubmit"));

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("DeepList"), note);
      assertTrue(note.contains("DeepSubmit"), note);
   }

   @Test
   void doesNotNoteEmbeddedChildrenOfAHiddenEmbeddedViewsheet() throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet emb = embedded(vs, "HiddenVS");
      emb.getVSAssemblyInfo().setVisibleValue("false");
      dropdownList(emb, "EmbList");
      emb.addAssembly(new SubmitVSAssembly(emb, "EmbSubmit"));

      assertNull(wholeSheetNote(vs));
   }

   @Test
   void doesNotNoteAnEmbeddedDropdownInANonSelectedTab() throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet emb = embedded(vs, "EmbVS");
      dropdownList(emb, "TabA");
      dropdownList(emb, "TabB");
      TabVSAssembly tab = new TabVSAssembly(emb, "Tab1");
      tab.setAssemblies(new String[] {"TabA", "TabB"});
      ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setSelected("TabA");
      emb.addAssembly(tab);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("EmbVS.TabA"), note);
      assertFalse(note.contains("EmbVS.TabB"), note);
   }

   @Test
   void doesNotNoteAnEmbeddedTipViewOrPopComponent() throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet emb = embedded(vs, "EmbVS");
      dropdownList(emb, "Shown1");
      dropdownList(emb, "TipList");
      dropdownList(emb, "PopList");

      TableVSAssembly tipSource = new TableVSAssembly(emb, "TipSource");
      TipVSAssemblyInfo tip = (TipVSAssemblyInfo) tipSource.getInfo();
      tip.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
      tip.setTipViewValue("TipList");
      emb.addAssembly(tipSource);

      TextVSAssembly popSource = new TextVSAssembly(emb, "PopSource");
      PopVSAssemblyInfo pop = (PopVSAssemblyInfo) popSource.getInfo();
      pop.setPopOptionValue(PopVSAssemblyInfo.POP_OPTION);
      pop.setPopComponentValue("PopList");
      emb.addAssembly(popSource);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("EmbVS.Shown1"), note);
      assertFalse(note.contains("TipList"), note);
      assertFalse(note.contains("PopList"), note);
   }
   /**
    * Bug #76717 / VOF-006: every export format's {@code writeSubmit} is a deliberate no-op — a
    * Submit assembly's reserved footprint renders as an empty area in every format, including
    * StyleBI's own Export toolbar button. Note the gap rather than let it pass as a plausible
    * result, mirroring the dropdown-selection-list disclosure above.
    */
   @Test
   void attachesANoteWhenTheViewsheetHasASubmitAssembly() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new SubmitVSAssembly(vs, "Submit1"));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("Submit1"));
      assertTrue(img.note().contains("is a Submit assembly"));
   }

   /**
    * Regression guard: a viewsheet with no Submit assembly at all must not get a spurious note.
    */
   @Test
   void doesNotAttachANoteWhenTheViewsheetHasNoSubmitAssembly() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNull(img.note());
   }

   /**
    * The dropdown-selection-list note and the Submit note are independent checks over the same
    * viewsheet — when both apply, neither should silently overwrite the other.
    */
   @Test
   void combinesTheDropdownAndSubmitNotesWhenBothApply() throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly dropdown = new SelectionListVSAssembly(vs, "Dropdown1");
      ((SelectionListVSAssemblyInfo) dropdown.getVSAssemblyInfo())
         .setShowType(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      vs.addAssembly(dropdown);
      vs.addAssembly(new SubmitVSAssembly(vs, "Submit1"));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSExportService exportService = mock(VSExportService.class);
      stubExport(exportService, fakePng(400, 300));

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      ScriptImageService.ChartImage img = svc.getViewsheetImage(
         rvs, null, null, TestPrincipals.user("alice", "host-org"));

      assertNotNull(img.note());
      assertTrue(img.note().contains("Dropdown1"));
      assertTrue(img.note().contains("Submit1"));
   }

   /**
    * Bug #78568: the Submit note must use the exporter's visibility rule
    * ({@code AbstractVSExporter.needExport}), like the dropdown note — a Submit in a non-selected
    * tab is not in the image, so it must not be named.
    */
   @Test
   void doesNotNoteASubmitInANonSelectedTab() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new SubmitVSAssembly(vs, "SubA"));
      vs.addAssembly(new SubmitVSAssembly(vs, "SubB"));
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setAssemblies(new String[] {"SubA", "SubB"});
      ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setSelected("SubA");
      vs.addAssembly(tab);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"SubA\" is a Submit assembly"), note);
      assertFalse(note.contains("SubB"), note);
   }

   @Test
   void doesNotNoteASubmitHiddenByScript() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new SubmitVSAssembly(vs, "Shown1"));
      SubmitVSAssembly hidden = new SubmitVSAssembly(vs, "Hidden1");
      hidden.getVSAssemblyInfo().setVisible("false");
      vs.addAssembly(hidden);

      String note = wholeSheetNote(vs);

      assertNotNull(note);
      assertTrue(note.contains("\"Shown1\" is a Submit assembly"), note);
      assertFalse(note.contains("Hidden1"), note);
   }

   @Test
   void doesNotNoteASubmitInAHiddenContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new SubmitVSAssembly(vs, "Inner1"));
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      group.setAssemblies(new String[] {"Inner1"});
      group.getVSAssemblyInfo().setVisibleValue("false");
      vs.addAssembly(group);

      assertNull(wholeSheetNote(vs));
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

   /**
    * Bug #77597: the export {@code getViewsheetImage} stopped waiting for keeps running and keeps
    * the Bug #77227 export claim. The next render of the same runtime viewsheet must be rejected
    * as {@link ExportInProgressException}, which the wiz advice reports as a retryable 503, not a
    * generic error. Uses the real {@link VSExportService#beginExport} rather than mocking the
    * export wholesale, which is what hid this interaction from the test above.
    */
   @Test
   void aRenderWhileAnAbandonedExportStillRunsIsRejectedAsExportInProgress() throws Exception {
      RuntimeViewsheet rvs = claimableViewsheetWithChart("Chart1");
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch firstEnded = new CountDownLatch(1);
      AtomicInteger exports = new AtomicInteger();
      VSExportService exportService = mock(VSExportService.class);

      doAnswer(invocation -> {
         Principal p = invocation.getArgument(10);
         VSExportService.beginExport(rvs, p);

         try {
            if(exports.incrementAndGet() == 1) {
               release.await(30, TimeUnit.SECONDS);
            }

            ExportResponse response = invocation.getArgument(9);
            response.getOutputStream().write(fakePng(200, 150));
            return null;
         }
         finally {
            rvs.endExport();

            if(exports.get() == 1) {
               firstEnded.countDown();
            }
         }
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      Principal user = TestPrincipals.user("alice", "host-org");

      try {
         assertThrows(RenderNotReadyException.class,
                      () -> svc.getViewsheetImage(rvs, null, null, user));
         // the first export is still blocked and still holds the claim
         assertThrows(ExportInProgressException.class,
                      () -> svc.getViewsheetImage(rvs, null, null, user));
      }
      finally {
         release.countDown();
      }

      assertTrue(firstEnded.await(30, TimeUnit.SECONDS), "the first export did not end");
      ScriptImageService.ChartImage img = svc.getViewsheetImage(rvs, null, null, user);
      assertEquals(200, img.width());
   }

   /**
    * Bug #77597: a browser or Composer export ({@code ExportControllerService}) claims the same
    * runtime viewsheet through {@link VSExportService#beginExport}. A wiz render during it must be
    * rejected as {@link ExportInProgressException}, the retryable type.
    */
   @Test
   void aRenderDuringABrowserExportIsRejectedAsExportInProgress() throws Exception {
      RuntimeViewsheet rvs = claimableViewsheetWithChart("Chart1");
      VSExportService exportService = mock(VSExportService.class);

      doAnswer(invocation -> {
         VSExportService.beginExport(rvs, invocation.getArgument(10));

         try {
            ExportResponse response = invocation.getArgument(9);
            response.getOutputStream().write(fakePng(200, 150));
            return null;
         }
         finally {
            rvs.endExport();
         }
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);
      Principal user = TestPrincipals.user("alice", "host-org");

      VSExportService.beginExport(rvs, user);

      try {
         assertThrows(ExportInProgressException.class,
                      () -> svc.getViewsheetImage(rvs, null, null, user));
      }
      finally {
         rvs.endExport();
      }

      assertEquals(200, svc.getViewsheetImage(rvs, null, null, user).width());
   }

   /** Bug #78662 (P-B): a spy viewsheet whose base is a worksheet that has no table. */
   private RuntimeViewsheet viewsheetOnWorksheetWithChart(String chartName, boolean withField) {
      Viewsheet vs = spy(new Viewsheet());
      ChartVSAssembly chart = new ChartVSAssembly(vs, chartName);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ORDERS"));

      if(withField) {
         chart.getVSChartInfo().addXField(new VSChartDimensionRef(new AttributeRef("EMPLOYEE_ID")));
      }

      vs.addAssembly(chart);
      AssetEntry base = mock(AssetEntry.class);
      when(base.isWorksheet()).thenReturn(true);
      doReturn(base).when(vs).getBaseEntry();
      doReturn(new Worksheet()).when(vs).getBaseWorksheet();

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private AssemblyImageService imageServiceReturning(RuntimeViewsheet rvs,
                                                      AssemblyImageService.ImageRenderResult r)
      throws Exception
   {
      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(r);
      return imageService;
   }

   private PairingException renderExpectingRefusal(RuntimeViewsheet rvs,
                                                   AssemblyImageService imageService)
   {
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));
      return assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));
   }

   @Test
   void terminalNoGraphNamesTheMissingTableAndDoesNotRetry() throws Exception {
      RuntimeViewsheet rvs = viewsheetOnWorksheetWithChart("Chart1", true);
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_GRAPH));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertTrue(ex.getMessage().contains("Chart1") && ex.getMessage().contains("'ORDERS'") &&
                 ex.getMessage().contains("no longer exists in its base worksheet"),
                 "got: [" + ex.getMessage() + "]");
      verify(imageService, times(1)).processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true));
   }

   @Test
   void terminalNoGraphWithoutAWorksheetBaseIsGenericNotAMissingTableClaim() throws Exception {
      // viewsheetWithChart has no base worksheet (logical-model/query/embedded style source)
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_GRAPH));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertFalse(ex.getMessage().contains("no longer exists"), "got: [" + ex.getMessage() + "]");
      assertTrue(ex.getMessage().contains("Chart1"), "got: [" + ex.getMessage() + "]");
      assertFalse(ex.getMessage().contains("retry"), "got: [" + ex.getMessage() + "]");
   }

   @Test
   void terminalNoDataOverABoundChartReportsNoData() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1");
      chart.getVSChartInfo().addXField(new VSChartDimensionRef(new AttributeRef("EMPLOYEE_ID")));
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_DATA));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertTrue(ex.getMessage().toLowerCase().contains("no data"), "got: [" + ex.getMessage() + "]");
   }

   @Test
   void terminalWithNoFieldsBoundSaysSoInsteadOfNoData() throws Exception {
      RuntimeViewsheet rvs = viewsheetWithChart("Chart1");
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_DATA));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertTrue(ex.getMessage().contains("no x/y/group fields"), "got: [" + ex.getMessage() + "]");
   }

   @Test
   void nonTerminalNotReadyStillRetriesThenThrowsRenderNotReady() throws Exception {
      // in-progress and MV-pending both come back as a plain retryAfter result
      RuntimeViewsheet rvs = viewsheetOnWorksheetWithChart("Chart1", true);
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.NOT_TERMINAL));
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      assertThrows(RenderNotReadyException.class, () -> svc.getAssemblyImage(
         rvs, "Chart1", null, null, TestPrincipals.user("alice", "host-org")));
      verify(imageService, times(4)).processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true));
   }

   @Test
   void terminalFlagKeepsRetryAfterSoTheBrowserPathIsUnchanged() {
      AssemblyImageService.ImageRenderResult r = new AssemblyImageService.ImageRenderResult(1,
         AssemblyImageService.ImageRenderResult.TERMINAL_NO_GRAPH);

      assertEquals(1, r.getRetryAfter());
      assertTrue(r.isTerminal());
      assertFalse(new AssemblyImageService.ImageRenderResult(1).isTerminal());
   }

   @Test
   void embeddedChartIsCheckedAgainstItsOwnViewsheetBaseNotTheTopLevelOne() throws Exception {
      // top-level base lacks SALES; the embedded chart's owning viewsheet's base has it
      Viewsheet top = spy(new Viewsheet());
      AssetEntry topBase = mock(AssetEntry.class);
      when(topBase.isWorksheet()).thenReturn(true);
      doReturn(topBase).when(top).getBaseEntry();
      doReturn(new Worksheet()).when(top).getBaseWorksheet();

      Viewsheet owner = spy(new Viewsheet());
      Worksheet ownerWs = mock(Worksheet.class);
      when(ownerWs.getAssembly("SALES")).thenReturn(mock(inetsoft.uql.asset.Assembly.class));
      AssetEntry ownerBase = mock(AssetEntry.class);
      when(ownerBase.isWorksheet()).thenReturn(true);
      doReturn(ownerBase).when(owner).getBaseEntry();
      doReturn(ownerWs).when(owner).getBaseWorksheet();

      ChartVSAssembly chart = new ChartVSAssembly(owner, "Emb.Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "SALES"));
      doReturn(chart).when(top).getAssembly("Emb.Chart1");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(top);

      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_GRAPH));
      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      PairingException ex = assertThrows(PairingException.class, () -> svc.getAssemblyImage(
         rvs, "Emb.Chart1", null, null, TestPrincipals.user("alice", "host-org")));
      assertFalse(ex.getMessage().contains("no longer exists"), "got: [" + ex.getMessage() + "]");
   }

   @Test
   void noDataWhoseBoundTableIsMissingFromTheBaseWorksheetStillNamesTheTable() throws Exception {
      // live: a dangling-table chart's pair carries a non-null data set, so it is NO_DATA
      RuntimeViewsheet rvs = viewsheetOnWorksheetWithChart("Chart1", true);
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_DATA));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertTrue(ex.getMessage().contains("'ORDERS'") &&
                 ex.getMessage().contains("no longer exists in its base worksheet"),
                 "got: [" + ex.getMessage() + "]");
   }

   @Test
   void noDataOverAViewsheetAssemblySourceDoesNotClaimTheTableIsGone() throws Exception {
      // a VS_ASSEMBLY source names a viewsheet assembly, which a worksheet lookup can never find
      RuntimeViewsheet rvs = viewsheetOnWorksheetWithChart("Chart1", true);
      ((ChartVSAssembly) rvs.getViewsheet().getAssembly("Chart1")).setSourceInfo(
         new SourceInfo(SourceInfo.VS_ASSEMBLY, null, "Table1"));
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_DATA));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertFalse(ex.getMessage().contains("no longer exists"), "got: [" + ex.getMessage() + "]");
      assertTrue(ex.getMessage().toLowerCase().contains("no data"), "got: [" + ex.getMessage() + "]");
   }

   @Test
   void noDataWhoseTableExistsInTheBaseWorksheetReportsNoData() throws Exception {
      Viewsheet vs = spy(new Viewsheet());
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ORDERS"));
      chart.getVSChartInfo().addXField(new VSChartDimensionRef(new AttributeRef("EMPLOYEE_ID")));
      vs.addAssembly(chart);
      AssetEntry base = mock(AssetEntry.class);
      when(base.isWorksheet()).thenReturn(true);
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly("ORDERS")).thenReturn(mock(inetsoft.uql.asset.Assembly.class));
      doReturn(base).when(vs).getBaseEntry();
      doReturn(ws).when(vs).getBaseWorksheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      AssemblyImageService imageService = imageServiceReturning(rvs,
         new AssemblyImageService.ImageRenderResult(1,
            AssemblyImageService.ImageRenderResult.TERMINAL_NO_DATA));

      PairingException ex = renderExpectingRefusal(rvs, imageService);

      assertFalse(ex.getMessage().contains("no longer exists"), "got: [" + ex.getMessage() + "]");
      assertTrue(ex.getMessage().toLowerCase().contains("no data"), "got: [" + ex.getMessage() + "]");
   }

   /** Like {@link #viewsheetWithChart}, but with the real export claim of a RuntimeViewsheet. */
   private RuntimeViewsheet claimableViewsheetWithChart(String chartName) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, chartName);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Table1"));
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = spy(new RuntimeViewsheet());
      doReturn(vs).when(rvs).getViewsheet();
      return rvs;
   }
}
