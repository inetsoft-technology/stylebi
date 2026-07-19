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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizExportReportEvent;
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

   @Test
   void rejectsIdentifierOutsideComponentsFolder() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), mock(SecurityEngine.class));

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
         mock(VSExportService.class), sec);

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
         mock(VSExportService.class), mock(SecurityEngine.class));

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
         vs, wizVsService, builder, exportService, sec);

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
}
