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
package inetsoft.web.wiz.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizExportReportEvent;
import inetsoft.web.wiz.service.PptxDeckMerger;
import inetsoft.web.wiz.service.WizPrintLayoutBuilder;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizViewsheetExportControllerTest {

   private static String managedDashboardIdentifier() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/dash1", null);
      return entry.toIdentifier();
   }

   /** Deliberately outside VISUALIZATION_COMPONENTS_FOLDER_PATH — built the same way
    *  managedDashboardIdentifier() is, not a hand-rolled identifier string. */
   private static String unmanagedIdentifier() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "some/other/folder/x", null);
      return entry.toIdentifier();
   }

   private static WizExportReportEvent event(String dashboardId) {
      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setDashboardId(dashboardId);
      ev.setFormat("pdf");
      ev.setPageSize("a4");
      ev.setTitle("Q39 Board");
      ev.setCharts(List.of());
      return ev;
   }

   /** A ServletOutputStream backed by a plain ByteArrayOutputStream so tests can capture what
    *  the controller writes, matching the enterprise ViewsheetApiController's own direct-response
    *  pattern (no compatible HttpMessageConverter exists for a byte[]/Resource body declared as
    *  application/pdf in this app's WebConfig — confirmed live, see the controller's comment). */
   private static ServletOutputStream capturingOutputStream(ByteArrayOutputStream sink) {
      return new ServletOutputStream() {
         @Override
         public boolean isReady() {
            return true;
         }

         @Override
         public void setWriteListener(WriteListener writeListener) {
         }

         @Override
         public void write(int b) {
            sink.write(b);
         }
      };
   }

   private static String managedChartIdentifier(String suffix) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/" + suffix, null);
      return entry.toIdentifier();
   }

   private static WizExportReportEvent.ChartEntry chartEntry(String savedId, String title, String caption, int order) {
      WizExportReportEvent.ChartEntry c = new WizExportReportEvent.ChartEntry();
      c.setSavedId(savedId);
      c.setTitle(title);
      c.setCaption(caption);
      c.setOrder(order);
      return c;
   }

   private static WizExportReportEvent.ChartEntry chartEntry(
      String savedId, String title, String caption, int order, String insightsMarkdown)
   {
      WizExportReportEvent.ChartEntry c = chartEntry(savedId, title, caption, order);
      c.setInsightsMarkdown(insightsMarkdown);
      return c;
   }

   @Test
   void rejectsIdentifierOutsideComponentsFolder() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), mock(SecurityEngine.class), mock(PptxDeckMerger.class));

      ResponseEntity<?> resp = ctrl.exportReport(
         event(unmanagedIdentifier()), mock(Principal.class), mock(HttpServletResponse.class));

      assertEquals(400, resp.getStatusCode().value());
      verify(vs, never()).openViewsheet(any(), any(), anyBoolean());
   }

   @Test
   void deniesExportWhenPermissionMissing() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(vs.openViewsheet(any(), eq(principal), eq(true))).thenReturn("rt1");
      when(sec.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
         eq("Export"), eq(ResourceAction.READ))).thenReturn(false);

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), sec, mock(PptxDeckMerger.class));

      ResponseEntity<?> resp = ctrl.exportReport(
         event(managedDashboardIdentifier()), principal, mock(HttpServletResponse.class));

      assertEquals(403, resp.getStatusCode().value());
      // Permission is checked before getViewsheet() is ever called (see the controller's
      // check-then-fetch order), so no stub for getViewsheet is needed here.
      verify(vs, never()).getViewsheet(any(), any());
      verify(vs).closeViewsheet("rt1", principal); // always closed, even on 403
   }

   @Test
   void rejectsUnsupportedFormat() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), mock(SecurityEngine.class), mock(PptxDeckMerger.class));

      WizExportReportEvent ev = event(managedDashboardIdentifier());
      ev.setFormat("xlsx");

      ResponseEntity<?> resp = ctrl.exportReport(ev, mock(Principal.class), mock(HttpServletResponse.class));

      assertEquals(400, resp.getStatusCode().value());
      verify(vs, never()).openViewsheet(any(), any(), anyBoolean());
   }

   @Test
   void happyPathBuildsLayoutPersistsAndWritesExportedBytesDirectlyToServletResponse() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      WizPrintLayoutBuilder builder = mock(WizPrintLayoutBuilder.class);
      VSExportService exportService = mock(VSExportService.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet viewsheet = mock(Viewsheet.class);
      inetsoft.uql.viewsheet.vslayout.LayoutInfo layoutInfo = mock(inetsoft.uql.viewsheet.vslayout.LayoutInfo.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      ByteArrayOutputStream written = new ByteArrayOutputStream();
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(written));

      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);
      when(vs.openViewsheet(any(), eq(principal), eq(true))).thenReturn("rt1");
      when(vs.getViewsheet("rt1", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(sec.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
         eq("Export"), eq(ResourceAction.READ))).thenReturn(true);

      var fakeLayout = new inetsoft.uql.viewsheet.vslayout.PrintLayout();
      when(builder.build(eq(viewsheet), eq("letter"), eq("Q39 Board"), isNull(), anyList()))
         .thenReturn(fakeLayout);

      byte[] fakePdf = "%PDF-1.4 fake".getBytes();
      doAnswer(inv -> {
         inetsoft.web.viewsheet.service.ExportResponse resp = inv.getArgument(9);
         resp.getOutputStream().write(fakePdf);
         return null;
      }).when(exportService).exportViewsheet(eq(rvs), anyInt(), eq(false), eq(false), eq(true),
         eq(false), eq(false), any(String[].class), eq(false), any(), eq(principal));

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, wizVsService, builder, exportService, sec, mock(PptxDeckMerger.class));

      WizExportReportEvent ev = event(managedDashboardIdentifier());
      ev.setPageSize("letter");

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      // Success path writes directly to the servlet response and returns null — Spring MVC
      // treats a null return from a non-void handler as "response already fully handled."
      assertNull(resp);
      assertArrayEquals(fakePdf, written.toByteArray());
      verify(servletResponse).setContentType(MediaType.APPLICATION_PDF_VALUE);
      verify(servletResponse).setContentLength(fakePdf.length);
      ArgumentCaptor<String> dispositionCaptor = ArgumentCaptor.forClass(String.class);
      verify(servletResponse).setHeader(eq("Content-Disposition"), dispositionCaptor.capture());
      assertTrue(dispositionCaptor.getValue().contains("Q39 Board.pdf"));
      verify(layoutInfo).setPrintLayout(fakeLayout);
      verify(wizVsService).persistViewsheet(viewsheet, managedDashboardIdentifier(), principal);
      verify(vs).closeViewsheet("rt1", principal);
   }

   @Test
   void pdfPathForwardsChartInsightsMarkdownIntoChartCaptions() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      WizPrintLayoutBuilder builder = mock(WizPrintLayoutBuilder.class);
      VSExportService exportService = mock(VSExportService.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet viewsheet = mock(Viewsheet.class);
      inetsoft.uql.viewsheet.vslayout.LayoutInfo layoutInfo = mock(inetsoft.uql.viewsheet.vslayout.LayoutInfo.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);
      when(vs.openViewsheet(any(), eq(principal), eq(true))).thenReturn("rt1");
      when(vs.getViewsheet("rt1", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(sec.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
         eq("Export"), eq(ResourceAction.READ))).thenReturn(true);

      var fakeLayout = new inetsoft.uql.viewsheet.vslayout.PrintLayout();
      when(builder.build(any(), any(), any(), any(), anyList())).thenReturn(fakeLayout);

      doAnswer(inv -> {
         inetsoft.web.viewsheet.service.ExportResponse resp = inv.getArgument(9);
         resp.getOutputStream().write("%PDF".getBytes());
         return null;
      }).when(exportService).exportViewsheet(eq(rvs), anyInt(), eq(false), eq(false), eq(true),
         eq(false), eq(false), any(String[].class), eq(false), any(), eq(principal));

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, wizVsService, builder, exportService, sec, mock(PptxDeckMerger.class));

      WizExportReportEvent ev = event(managedDashboardIdentifier());
      ev.setCharts(List.of(chartEntry(managedChartIdentifier("c1"), "First", "cap one", 0, "**Bold** finding")));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp);
      verify(builder).build(eq(viewsheet), eq("a4"), eq("Q39 Board"), isNull(), argThat(list ->
         list.size() == 1 &&
         "**Bold** finding".equals(((WizPrintLayoutBuilder.ChartCaption) list.get(0)).insightsMarkdown())));
   }

   @Test
   void pptxPathForwardsChartInsightsMarkdownIntoChartSlides() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      WizPrintLayoutBuilder builder = mock(WizPrintLayoutBuilder.class);
      VSExportService exportService = mock(VSExportService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      String chartId = managedChartIdentifier("c1");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(chartId)), eq(principal), eq(true)))
         .thenReturn("rt-c1");
      when(vs.getViewsheet("rt-c1", principal)).thenReturn(rvs);
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write("chart-deck".getBytes()); return null; })
         .when(exportService).exportViewsheet(eq(rvs), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));

      when(merger.mergeSlides(any(), any(), argThat(list ->
         list.size() == 1 && "**Bold** finding".equals(list.get(0).insightsMarkdown())
      ))).thenReturn("%PPTX-fake".getBytes());

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, wizVsService, builder, exportService, sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Q39 Board");
      ev.setCharts(List.of(chartEntry(chartId, "First", "cap one", 0, "**Bold** finding")));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp);
      verify(merger).mergeSlides(any(), any(), argThat(list ->
         "**Bold** finding".equals(list.get(0).insightsMarkdown())));
   }

   @Test
   void failedPptxChartStillCarriesItsInsightsMarkdownIntoThePlaceholderSlide() throws Exception {
      // NOTE: adapted from the brief's literal single-chart test, which collides with the
      // all-charts-failed-aborts contract exactly like pptxChartOpenFailureBecomesPlaceholderNotAbort
      // and pptxChartOutsideComponentsFolderBecomesPlaceholderNotAbort above -- with only one chart
      // total, "the one chart failed" and "all charts failed" are the same event, so the
      // controller's allFailed guard 400s instead of merging. A second, successful chart is added
      // so this test isolates "the failed chart's insightsMarkdown survives into its placeholder
      // slide" without re-triggering the all-failed abort that pptxAllChartsFailedAbortsLoud
      // already covers on its own.
      ViewsheetService vs = mock(ViewsheetService.class);
      VSExportService exportService = mock(VSExportService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      String badId = managedChartIdentifier("broken");
      String goodId = managedChartIdentifier("ok");
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(badId)), eq(principal), eq(true)))
         .thenThrow(new RuntimeException("boom"));
      RuntimeViewsheet rvsGood = mock(RuntimeViewsheet.class);
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(goodId)), eq(principal), eq(true)))
         .thenReturn("rt-good");
      when(vs.getViewsheet("rt-good", principal)).thenReturn(rvsGood);
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write("good".getBytes()); return null; })
         .when(exportService).exportViewsheet(eq(rvsGood), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));

      when(merger.mergeSlides(any(), any(), argThat(list ->
         list.size() == 2 && list.get(0).failed() &&
         "Finding survives even though the chart failed to render.".equals(list.get(0).insightsMarkdown())
      ))).thenReturn("%PPTX-fake".getBytes());

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class), exportService, sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Board");
      ev.setCharts(List.of(
         chartEntry(badId, "Broken", null, 0, "Finding survives even though the chart failed to render."),
         chartEntry(goodId, "OK", null, 1)));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp);
      verify(merger).mergeSlides(any(), any(), argThat(list -> list.get(0).failed()));
   }

   @Test
   void pptxHappyPathExportsEachChartIndividuallyAndMerges() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      WizPrintLayoutBuilder builder = mock(WizPrintLayoutBuilder.class);
      VSExportService exportService = mock(VSExportService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      ByteArrayOutputStream written = new ByteArrayOutputStream();
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(written));
      when(sec.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
         eq("Export"), eq(ResourceAction.READ))).thenReturn(true);

      String chart1Id = managedChartIdentifier("c1");
      String chart2Id = managedChartIdentifier("c2");
      RuntimeViewsheet rvs1 = mock(RuntimeViewsheet.class);
      RuntimeViewsheet rvs2 = mock(RuntimeViewsheet.class);
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(chart1Id)), eq(principal), eq(true)))
         .thenReturn("rt-c1");
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(chart2Id)), eq(principal), eq(true)))
         .thenReturn("rt-c2");
      when(vs.getViewsheet("rt-c1", principal)).thenReturn(rvs1);
      when(vs.getViewsheet("rt-c2", principal)).thenReturn(rvs2);

      byte[] chart1Bytes = "chart1-deck".getBytes();
      byte[] chart2Bytes = "chart2-deck".getBytes();
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write(chart1Bytes); return null; })
         .when(exportService).exportViewsheet(eq(rvs1), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write(chart2Bytes); return null; })
         .when(exportService).exportViewsheet(eq(rvs2), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));

      byte[] fakeDeck = "%PPTX-fake".getBytes();
      when(merger.mergeSlides(eq("Q39 Board"), isNull(), argThat(list ->
         list.size() == 2 &&
         list.get(0).title().equals("First") && java.util.Arrays.equals(list.get(0).singleSlideDeckBytes(), chart1Bytes) &&
         list.get(1).title().equals("Second") && java.util.Arrays.equals(list.get(1).singleSlideDeckBytes(), chart2Bytes) &&
         !list.get(0).failed() && !list.get(1).failed()
      ))).thenReturn(fakeDeck);

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, wizVsService, builder, exportService, sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Q39 Board");
      ev.setCharts(List.of(
         chartEntry(chart1Id, "First", "cap one", 0),
         chartEntry(chart2Id, "Second", "cap two", 1)));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp);
      assertArrayEquals(fakeDeck, written.toByteArray());
      verify(servletResponse).setContentType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
      verify(vs).closeViewsheet("rt-c1", principal);
      verify(vs).closeViewsheet("rt-c2", principal);
      // pptx never touches the dashboard/print-layout machinery
      verify(builder, never()).build(any(), any(), any(), any(), any());
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void pptxChartOpenFailureBecomesPlaceholderNotAbort() throws Exception {
      // NOTE: adapted from the brief's literal test, which stubbed only a single chart that
      // fails to open and then asserted assertNull(resp) (success/merge). With exactly one
      // chart in the request, "one chart failed" and "all charts failed" are the same event,
      // which directly contradicts pptxAllChartsFailedAbortsLoud below (same single-failing-
      // chart setup, asserting a 400 abort instead). That is an internal contradiction in the
      // brief's two tests, not a real behavioral distinction, so this test adds a second,
      // successful chart -- now "one of two failed" genuinely exercises "some failed, not all
      // failed, don't abort" without colliding with the all-failed test.
      ViewsheetService vs = mock(ViewsheetService.class);
      VSExportService exportService = mock(VSExportService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      String badId = managedChartIdentifier("broken");
      String goodId = managedChartIdentifier("ok");
      RuntimeViewsheet rvsGood = mock(RuntimeViewsheet.class);
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(badId)), eq(principal), eq(true)))
         .thenThrow(new RuntimeException("boom"));
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(goodId)), eq(principal), eq(true)))
         .thenReturn("rt-good");
      when(vs.getViewsheet("rt-good", principal)).thenReturn(rvsGood);
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write("good".getBytes()); return null; })
         .when(exportService).exportViewsheet(eq(rvsGood), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));

      when(merger.mergeSlides(any(), any(), argThat(list ->
         list.size() == 2 &&
         list.get(0).failed() && list.get(0).title().equals("Broken") &&
         !list.get(1).failed() && list.get(1).title().equals("OK")
      ))).thenReturn("deck".getBytes());

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class), exportService, sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Board");
      ev.setCharts(List.of(chartEntry(badId, "Broken", null, 0), chartEntry(goodId, "OK", null, 1)));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp, "one failed chart among others still produces a deck, doesn't abort");
      verify(merger).mergeSlides(any(), any(), any());
   }

   @Test
   void pptxAllChartsFailedAbortsLoud() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(vs.openViewsheet(any(), eq(principal), eq(true))).thenThrow(new RuntimeException("boom"));

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Board");
      ev.setCharts(List.of(chartEntry(managedChartIdentifier("only"), "Only", null, 0)));

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, mock(HttpServletResponse.class));

      assertEquals(400, resp.getStatusCode().value());
      verify(merger, never()).mergeSlides(any(), any(), any());
   }

   @Test
   void pptxEmptyChartsRejectedUpfront() throws Exception {
      // Mirrors WizDashboardService.composeDashboard's upfront "identifiers are required" guard
      // (identifiers == null || identifiers.isEmpty() -> IllegalArgumentException before any
      // asset is touched). The pptx path only had the later "all failed to export" guard, which
      // short-circuits via !chartSlides.isEmpty() and silently lets an empty/null charts[] fall
      // through to pptxDeckMerger.mergeSlides(title, recap, List.of()) instead of being rejected.
      ViewsheetService vs = mock(ViewsheetService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), sec, merger);

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Board");
      ev.setCharts(null);

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, mock(HttpServletResponse.class));

      assertEquals(400, resp.getStatusCode().value());
      verify(merger, never()).mergeSlides(any(), any(), any());
      verify(vs, never()).openViewsheet(any(), any(), anyBoolean());
   }

   @Test
   void pptxChartOutsideComponentsFolderBecomesPlaceholderNotAbort() throws Exception {
      // Consistent with the "one bad chart doesn't abort" decision: an out-of-folder savedId is
      // still just "this one chart didn't work," same as an open/export failure -- NOT a
      // request-level 400 (unlike the PDF path's strict dashboardId guard, which gates the single
      // asset the whole export depends on).
      //
      // NOTE: adapted from the brief's literal single-chart version of this test. With only one
      // chart total, "the out-of-folder chart failed" and "all charts failed" are the same event,
      // which would make this test collide with the all-charts-failed-aborts contract (confirmed
      // by running it as originally written: it actually got a 400, "resolve the folder-guard
      // test/implementation disagreement" per the brief's own Step 5 note). A second, successful
      // chart is added so this test isolates "one bad (out-of-folder) chart among others doesn't
      // abort" without re-litigating the all-failed case, which pptxAllChartsFailedAbortsLoud
      // already covers on its own.
      ViewsheetService vs = mock(ViewsheetService.class);
      VSExportService exportService = mock(VSExportService.class);
      PptxDeckMerger merger = mock(PptxDeckMerger.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      HttpServletResponse servletResponse = mock(HttpServletResponse.class);
      when(servletResponse.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      String goodId = managedChartIdentifier("ok");
      RuntimeViewsheet rvsGood = mock(RuntimeViewsheet.class);
      when(vs.openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(goodId)), eq(principal), eq(true)))
         .thenReturn("rt-good");
      when(vs.getViewsheet("rt-good", principal)).thenReturn(rvsGood);
      doAnswer(inv -> { ((ExportResponse) inv.getArgument(9)).getOutputStream().write("good".getBytes()); return null; })
         .when(exportService).exportViewsheet(eq(rvsGood), eq(FileFormatInfo.EXPORT_TYPE_POWERPOINT),
            anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(String[].class), anyBoolean(), any(), eq(principal));

      when(merger.mergeSlides(any(), any(), argThat(list ->
         list.size() == 2 &&
         list.get(0).failed() && list.get(0).title().equals("Bad") &&
         !list.get(1).failed() && list.get(1).title().equals("OK")
      ))).thenReturn("deck".getBytes());

      WizExportReportEvent ev = new WizExportReportEvent();
      ev.setFormat("pptx");
      ev.setTitle("Board");
      AssetEntry outside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "some/other/folder/x", null);
      ev.setCharts(List.of(
         chartEntry(outside.toIdentifier(), "Bad", null, 0),
         chartEntry(goodId, "OK", null, 1)));

      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         exportService, sec, merger);

      ResponseEntity<?> resp = ctrl.exportReport(ev, principal, servletResponse);

      assertNull(resp, "out-of-folder chart is a placeholder, not a request-level abort");
      verify(vs, never()).openViewsheet(argThat(e -> e != null && e.toIdentifier().equals(outside.toIdentifier())), any(), anyBoolean());
      verify(merger).mergeSlides(any(), any(), any());
   }
}
