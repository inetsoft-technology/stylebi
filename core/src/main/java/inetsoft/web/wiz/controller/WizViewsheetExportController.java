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
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizExportReportEvent;
import inetsoft.web.wiz.service.WizPrintLayoutBuilder;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wiz")
public class WizViewsheetExportController {
   public WizViewsheetExportController(ViewsheetService viewsheetService,
                                       WizVsService wizVsService,
                                       WizPrintLayoutBuilder printLayoutBuilder,
                                       VSExportService exportService,
                                       SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.wizVsService = wizVsService;
      this.printLayoutBuilder = printLayoutBuilder;
      this.exportService = exportService;
      this.securityEngine = securityEngine;
   }

   @PostMapping("/viewsheet/export-report")
   public ResponseEntity<?> exportReport(@RequestBody WizExportReportEvent event, Principal principal,
                                         HttpServletResponse servletResponse)
   {
      if(!"pdf".equalsIgnoreCase(event.getFormat())) {
         return ResponseEntity.badRequest().body(Map.of(
            "error", "Unsupported format: " + event.getFormat() + " (only pdf is supported in Phase 1)"));
      }

      AssetEntry entry;

      try {
         entry = Tool.isEmptyString(event.getDashboardId())
            ? null : AssetEntry.createAssetEntry(event.getDashboardId());
      }
      catch(RuntimeException e) {
         return ResponseEntity.badRequest().body(Map.of(
            "error", "dashboardId is not a valid asset identifier: " + event.getDashboardId()));
      }

      if(entry == null || entry.getPath() == null ||
         !entry.getPath().startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/"))
      {
         return ResponseEntity.badRequest().body(Map.of(
            "error", "dashboardId is not in the managed visualizations folder: " + event.getDashboardId()));
      }

      String runtimeId;

      try {
         runtimeId = viewsheetService.openViewsheet(entry, principal, true);
      }
      catch(Exception e) {
         LOG.error("Failed to open composed dashboard for export: {}", event.getDashboardId(), e);
         return ResponseEntity.status(404).body(Map.of("error", "Dashboard not found: " + event.getDashboardId()));
      }

      try {
         if(!securityEngine.checkPermission(principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION,
            "Export", ResourceAction.READ))
         {
            return ResponseEntity.status(403).body(Map.of(
               "error", "Permission denied: viewsheet export"));
         }

         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
         Viewsheet viewsheet = rvs.getViewsheet();

         List<WizPrintLayoutBuilder.ChartCaption> charts = event.getCharts() == null
            ? List.of()
            : event.getCharts().stream()
               .map(c -> new WizPrintLayoutBuilder.ChartCaption(c.getTitle(), c.getCaption(), c.getOrder()))
               .collect(Collectors.toList());

         var printLayout = printLayoutBuilder.build(
            viewsheet, event.getPageSize(), event.getTitle(), event.getRecap(), charts);
         viewsheet.getLayoutInfo().setPrintLayout(printLayout);
         wizVsService.persistViewsheet(viewsheet, event.getDashboardId(), principal);

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         exportService.exportViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PDF, false, false, true,
            false, false, new String[0], false, new ExportResponse(out), principal);

         byte[] bytes = out.toByteArray();
         String filename = (event.getTitle() == null ? "board" : event.getTitle()) + ".pdf";
         // Write directly to the servlet response rather than returning a ResponseEntity<byte[]>
         // (or ResponseEntity<ByteArrayResource>, also confirmed failing live). WebConfig's
         // registered converters have no combination that can write an arbitrary byte[]/Resource
         // body as application/pdf: the app's ByteArrayHttpMessageConverter bean is restricted to
         // octet-stream/text-plain/openmetrics-text, and ResourceHttpMessageConverter (which does
         // declare application/pdf) only writes Resource bodies — neither matches, and Spring
         // throws HttpMessageNotWritableException for both (confirmed against a real running
         // StyleBI instance exporting a real board). This direct-response pattern mirrors the
         // enterprise ViewsheetApiController.exportViewsheet, which sidesteps the converter
         // pipeline entirely and is proven to work for exactly this kind of binary export in this
         // codebase. Returning null tells Spring MVC the response is already fully handled.
         servletResponse.setContentType(MediaType.APPLICATION_PDF_VALUE);
         // NOT setContentDispositionFormData equivalent — "attachment; filename=..." is what a
         // file download needs, not "form-data; name=...".
         servletResponse.setHeader("Content-Disposition",
            ContentDisposition.attachment().filename(filename).build().toString());
         servletResponse.setContentLength(bytes.length);
         servletResponse.getOutputStream().write(bytes);
         servletResponse.getOutputStream().flush();
         return null;
      }
      catch(SecurityException e) {
         return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
      }
      catch(IllegalArgumentException | IllegalStateException e) {
         return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
      }
      catch(Exception e) {
         LOG.error("Failed to export board PDF for dashboard {}", event.getDashboardId(), e);
         return ResponseEntity.status(500).body(Map.of("error", "Failed to export board PDF"));
      }
      finally {
         try {
            viewsheetService.closeViewsheet(runtimeId, principal);
         }
         catch(Exception ignore) {
            LOG.warn("Failed to close runtime [{}] after export", runtimeId);
         }
      }
   }

   private final ViewsheetService viewsheetService;
   private final WizVsService wizVsService;
   private final WizPrintLayoutBuilder printLayoutBuilder;
   private final VSExportService exportService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(WizViewsheetExportController.class);
}
