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

import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.vs.controller.ExportControllerService;
import inetsoft.web.composer.vs.controller.ExportControllerServiceProxy;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.AssemblyImageServiceProxy;
import inetsoft.web.viewsheet.controller.dialog.ExportDialogServiceProxy;
import inetsoft.web.viewsheet.model.dialog.ExportDialogModel;
import inetsoft.web.wiz.model.ExportViewsheetRequest;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizExportControllerTest {

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

   /** Mirrors the field defaults ExportViewsheetRequest itself declares (i.e. what an absent
    *  query param would leave in place), so each test only sets what it actually cares about. */
   private static ExportViewsheetRequest request(String runtimeId, int format) {
      ExportViewsheetRequest request = new ExportViewsheetRequest();
      request.setRuntimeId(runtimeId);
      request.setFormat(format);
      return request;
   }

   @Test
   void getExportDialogModelDelegatesToProxy() throws Exception {
      ExportDialogServiceProxy exportDialogServiceProxy = mock(ExportDialogServiceProxy.class);
      Principal principal = mock(Principal.class);
      ExportDialogModel model = mock(ExportDialogModel.class);
      when(exportDialogServiceProxy.getExportDialogModel("rt1", principal)).thenReturn(model);

      WizExportController ctrl = new WizExportController(
         exportDialogServiceProxy, mock(ExportControllerServiceProxy.class),
         mock(AssemblyImageServiceProxy.class), mock(BinaryTransferService.class),
         mock(SecurityEngine.class));

      assertSame(model, ctrl.getExportDialogModel("rt1", principal));
   }

   @Test
   void checkExportingDelegatesToProxy() throws Exception {
      AssemblyImageServiceProxy assemblyImageServiceProxy = mock(AssemblyImageServiceProxy.class);
      Principal principal = mock(Principal.class);
      MessageCommand command = mock(MessageCommand.class);
      when(assemblyImageServiceProxy.checkExporting("rt1", principal)).thenReturn(command);

      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), mock(ExportControllerServiceProxy.class),
         assemblyImageServiceProxy, mock(BinaryTransferService.class), mock(SecurityEngine.class));

      assertSame(command, ctrl.checkExporting("rt1", principal));
   }

   @Test
   void deniesExportWhenPermissionMissing() throws Exception {
      ExportControllerServiceProxy exportControllerServiceProxy = mock(ExportControllerServiceProxy.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(eq(principal), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
         eq("Export"), eq(ResourceAction.READ))).thenReturn(false);

      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), exportControllerServiceProxy,
         mock(AssemblyImageServiceProxy.class), mock(BinaryTransferService.class), sec);

      assertThrows(SecurityException.class, () -> ctrl.exportViewsheet(
         request("rt1", FileFormatInfo.EXPORT_TYPE_EXCEL), principal, mock(HttpServletResponse.class)));

      verifyNoInteractions(exportControllerServiceProxy);
   }

   @Test
   void csvFormatForcesMatchFalseRegardlessOfRequestedMatch() throws Exception {
      // Regression test: mirrors ExportController.exportViewsheet0()'s
      // `if(format == EXPORT_TYPE_CSV) { match = false; }` override, which this controller
      // originally omitted -- a caller-supplied match=true for CSV must still reach the service
      // as false, or CSV exports come back clipped to the on-screen layout instead of expanded.
      ExportControllerServiceProxy exportControllerServiceProxy = mock(ExportControllerServiceProxy.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      BinaryTransfer data = mock(BinaryTransfer.class);
      ExportControllerService.ViewsheetExportResult result =
         new ExportControllerService.ViewsheetExportResult(data, "export.csv", "text/csv", "csv");
      when(exportControllerServiceProxy.exportViewsheet(
         eq("rt1"), eq(FileFormatInfo.EXPORT_TYPE_CSV), isNull(), eq(false), eq(false),
         anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(String[].class),
         anyBoolean(), anyBoolean(), any(CSVConfig.class), eq(principal)))
         .thenReturn(result);

      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));

      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), exportControllerServiceProxy,
         mock(AssemblyImageServiceProxy.class), mock(BinaryTransferService.class), sec);

      // match=true is explicitly requested, but format=CSV must override it to false.
      ExportViewsheetRequest request = request("rt1", FileFormatInfo.EXPORT_TYPE_CSV);
      request.setMatch(true);
      request.setDelimiter(",");
      request.setQuote("\"");
      ctrl.exportViewsheet(request, principal, response);

      verify(exportControllerServiceProxy).exportViewsheet(
         eq("rt1"), eq(FileFormatInfo.EXPORT_TYPE_CSV), isNull(), eq(false), eq(false),
         anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(String[].class),
         anyBoolean(), anyBoolean(), any(CSVConfig.class), eq(principal));
   }

   @Test
   void nonCsvFormatPassesRequestedMatchThrough() throws Exception {
      ExportControllerServiceProxy exportControllerServiceProxy = mock(ExportControllerServiceProxy.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      BinaryTransfer data = mock(BinaryTransfer.class);
      ExportControllerService.ViewsheetExportResult result = new ExportControllerService.ViewsheetExportResult(
         data, "export.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
      when(exportControllerServiceProxy.exportViewsheet(
         eq("rt1"), eq(FileFormatInfo.EXPORT_TYPE_EXCEL), isNull(), eq(false), eq(true),
         anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(String[].class),
         anyBoolean(), anyBoolean(), any(CSVConfig.class), eq(principal)))
         .thenReturn(result);

      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));

      BinaryTransferService binaryTransferService = mock(BinaryTransferService.class);
      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), exportControllerServiceProxy,
         mock(AssemblyImageServiceProxy.class), binaryTransferService, sec);

      // match=true (Match Layout) requested for a non-CSV format must reach the service
      // unchanged -- only CSV forces an override to false, so this must NOT be flipped.
      ExportViewsheetRequest request = request("rt1", FileFormatInfo.EXPORT_TYPE_EXCEL);
      request.setMatch(true);
      ctrl.exportViewsheet(request, principal, response);

      verify(exportControllerServiceProxy).exportViewsheet(
         eq("rt1"), eq(FileFormatInfo.EXPORT_TYPE_EXCEL), isNull(), eq(false), eq(true),
         anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(String[].class),
         anyBoolean(), anyBoolean(), any(CSVConfig.class), eq(principal));
      verify(binaryTransferService).writeData(eq(data), any());
   }

   @Test
   void bookmarksAndTableAssembliesAreSplitOnComma() throws Exception {
      ExportControllerServiceProxy exportControllerServiceProxy = mock(ExportControllerServiceProxy.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      BinaryTransfer data = mock(BinaryTransfer.class);
      ExportControllerService.ViewsheetExportResult result =
         new ExportControllerService.ViewsheetExportResult(data, "export.csv", "text/csv", "csv");
      when(exportControllerServiceProxy.exportViewsheet(
         any(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         anyBoolean(), anyBoolean(), any(String[].class), anyBoolean(), anyBoolean(),
         any(CSVConfig.class), any()))
         .thenReturn(result);

      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getOutputStream()).thenReturn(capturingOutputStream(new ByteArrayOutputStream()));

      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), exportControllerServiceProxy,
         mock(AssemblyImageServiceProxy.class), mock(BinaryTransferService.class), sec);

      ExportViewsheetRequest request = request("rt1", FileFormatInfo.EXPORT_TYPE_CSV);
      request.setBookmarks("(Home),My Bookmark");
      request.setDelimiter(",");
      request.setQuote("\"");
      request.setTableAssemblies("Table1,Table2");
      ctrl.exportViewsheet(request, principal, response);

      verify(exportControllerServiceProxy).exportViewsheet(
         eq("rt1"), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         anyBoolean(), anyBoolean(),
         eq(new String[] { "(Home)", "My Bookmark" }), anyBoolean(), anyBoolean(),
         argThat(cfg -> cfg.getExportAssemblies().equals(java.util.List.of("Table1", "Table2"))),
         eq(principal));
   }

   @Test
   void throwsWhenRuntimeIdIsMissing() throws Exception {
      // Switching from individual @RequestParam("runtimeId") (required, auto-400) to a single
      // bound request object loses Spring's automatic "missing required parameter" rejection --
      // this must fail just as loudly instead of silently calling the proxy with a null runtimeId.
      SecurityEngine sec = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ExportControllerServiceProxy exportControllerServiceProxy = mock(ExportControllerServiceProxy.class);

      WizExportController ctrl = new WizExportController(
         mock(ExportDialogServiceProxy.class), exportControllerServiceProxy,
         mock(AssemblyImageServiceProxy.class), mock(BinaryTransferService.class), sec);

      assertThrows(IllegalArgumentException.class, () -> ctrl.exportViewsheet(
         request(null, FileFormatInfo.EXPORT_TYPE_EXCEL), principal, mock(HttpServletResponse.class)));

      verifyNoInteractions(exportControllerServiceProxy);
   }
}
