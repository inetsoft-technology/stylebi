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
         "rt1", FileFormatInfo.EXPORT_TYPE_EXCEL, true, false, true, "",
         false, false, null, null, true, false, "",
         principal, mock(HttpServletResponse.class)));

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
      ctrl.exportViewsheet("rt1", FileFormatInfo.EXPORT_TYPE_CSV, true, false, true, "",
         false, false, ",", "\"", true, false, "", principal, response);

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
      ctrl.exportViewsheet("rt1", FileFormatInfo.EXPORT_TYPE_EXCEL, true, false, true, "",
         false, false, null, null, true, false, "", principal, response);

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

      ctrl.exportViewsheet("rt1", FileFormatInfo.EXPORT_TYPE_CSV, true, false, true,
         "(Home),My Bookmark", false, false, ",", "\"", true, false,
         "Table1,Table2", principal, response);

      verify(exportControllerServiceProxy).exportViewsheet(
         eq("rt1"), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         anyBoolean(), anyBoolean(),
         eq(new String[] { "(Home)", "My Bookmark" }), anyBoolean(), anyBoolean(),
         argThat(cfg -> cfg.getExportAssemblies().equals(java.util.List.of("Table1", "Table2"))),
         eq(principal));
   }
}
