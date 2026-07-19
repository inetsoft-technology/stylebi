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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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

   @Test
   void rejectsIdentifierOutsideComponentsFolder() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      WizViewsheetExportController ctrl = new WizViewsheetExportController(
         vs, mock(WizVsService.class), mock(WizPrintLayoutBuilder.class),
         mock(VSExportService.class), mock(SecurityEngine.class));

      ResponseEntity<?> resp = ctrl.exportReport(event(unmanagedIdentifier()), mock(Principal.class));

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

      ResponseEntity<?> resp = ctrl.exportReport(event(managedDashboardIdentifier()), principal);

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

      ResponseEntity<?> resp = ctrl.exportReport(ev, mock(Principal.class));

      assertEquals(400, resp.getStatusCode().value());
      verify(vs, never()).openViewsheet(any(), any(), anyBoolean());
   }
}
