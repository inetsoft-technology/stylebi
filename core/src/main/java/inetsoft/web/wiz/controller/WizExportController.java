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
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.vs.controller.ExportControllerService;
import inetsoft.web.composer.vs.controller.ExportControllerServiceProxy;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.AssemblyImageServiceProxy;
import inetsoft.web.viewsheet.controller.dialog.ExportDialogServiceProxy;
import inetsoft.web.viewsheet.model.dialog.ExportDialogModel;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.ExportViewsheetRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Arrays;

/**
 * Wraps the same services that back the native /api/vs/export-dialog-model and
 * /export/viewsheet endpoints (ExportDialogService, ExportControllerService — no export logic
 * is reimplemented here) under wiz's own JWT-authenticated, CSRF-exempt /api/wiz namespace
 * (see WizServiceAuthenticationFilter / CSRFFilter#isWizApi), so wiz's browser never needs to
 * talk to StyleBI's session/CSRF-protected controllers directly, or replicate StyleBI's
 * ~_hex_~ runtime-id path escaping (Tool.byteEncode) for a plain query parameter.
 *
 * runtimeId here is always one wiz already has open (via ViewsheetRuntimeController), never an
 * asset path to resolve — mirrors ExportController's own "path doesn't parse as an asset entry"
 * fallback branch, so matchesAssetIdFormat is always false.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizExportController {
   public WizExportController(ExportDialogServiceProxy exportDialogServiceProxy,
                              ExportControllerServiceProxy exportControllerServiceProxy,
                              AssemblyImageServiceProxy assemblyImageServiceProxy,
                              BinaryTransferService binaryTransferService,
                              SecurityEngine securityEngine)
   {
      this.exportDialogServiceProxy = exportDialogServiceProxy;
      this.exportControllerServiceProxy = exportControllerServiceProxy;
      this.assemblyImageServiceProxy = assemblyImageServiceProxy;
      this.binaryTransferService = binaryTransferService;
      this.securityEngine = securityEngine;
   }

   @GetMapping("/viewsheet/export-dialog-model")
   public ExportDialogModel getExportDialogModel(@RequestParam("runtimeId") String runtimeId,
                                                 Principal principal) throws Exception
   {
      return exportDialogServiceProxy.getExportDialogModel(runtimeId, principal);
   }

   @GetMapping("/viewsheet/export-check")
   public MessageCommand checkExporting(@RequestParam("runtimeId") String runtimeId,
                                        Principal principal) throws Exception
   {
      return assemblyImageServiceProxy.checkExporting(runtimeId, principal);
   }

   // request is an unannotated bean parameter — Spring binds it implicitly from the same query
   // params the old per-field @RequestParam list declared (see ExportViewsheetRequest's own doc).
   @GetMapping("/viewsheet/export")
   public void exportViewsheet(ExportViewsheetRequest request, Principal principal,
                               HttpServletResponse response) throws Exception
   {
      if(!securityEngine.checkPermission(principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION,
         "Export", ResourceAction.READ))
      {
         throw new SecurityException("Permission denied: viewsheet export");
      }

      // The individual @RequestParam("runtimeId") this replaced was required (no defaultValue),
      // so Spring 400'd automatically on a missing value; the implicit-bean binding above does
      // not, so that same "fail loud on a missing required param" guarantee has to be explicit.
      if(Tool.isEmptyString(request.getRuntimeId())) {
         throw new IllegalArgumentException("Missing required parameter: runtimeId");
      }

      String bookmarksParam = request.getBookmarks();
      String tableAssembliesParam = request.getTableAssemblies();
      String[] bookmarks = bookmarksParam.isEmpty() ? new String[0] : bookmarksParam.split(",");
      String[] tables = tableAssembliesParam.isEmpty() ? new String[0] : tableAssembliesParam.split(",");

      int format = request.getFormat();
      boolean match = request.isMatch();

      // Mirrors ExportController.exportViewsheet0()'s override: CSV data must always be fully
      // expanded rather than clipped to the on-screen layout, regardless of what the caller asked
      // for via `match`.
      if(format == FileFormatInfo.EXPORT_TYPE_CSV) {
         match = false;
      }

      CSVConfig csvConfig = new CSVConfig();

      if(request.getDelimiter() != null) {
         csvConfig.setDelimiter(request.getDelimiter());
      }

      if(request.getQuote() != null) {
         csvConfig.setQuote(request.getQuote());
      }

      csvConfig.setKeepHeader(request.isKeepHeader());
      csvConfig.setTabDelimited(request.isTabDelimited());
      csvConfig.setExportAssemblies(Arrays.asList(tables));

      // type=null (no output-extension override), matchesAssetIdFormat=false (see class doc),
      // previewPrintLayout=false, print=false — wiz never sets either of the last two.
      ExportControllerService.ViewsheetExportResult result = exportControllerServiceProxy.exportViewsheet(
         request.getRuntimeId(), format, null, false, match, request.isExpandSelections(),
         request.isCurrent(), false, false, bookmarks, request.isOnlyDataComponents(),
         request.isExportAllTabbedTables(), csvConfig, principal);

      VSExportService.setResponseHeader(new ExportResponse(response), result.getSuffix(),
         "attachment", result.getFileName(), result.getMime());
      BinaryTransfer data = result.getData();
      binaryTransferService.writeData(data, response.getOutputStream());
   }

   private final ExportDialogServiceProxy exportDialogServiceProxy;
   private final ExportControllerServiceProxy exportControllerServiceProxy;
   private final AssemblyImageServiceProxy assemblyImageServiceProxy;
   private final BinaryTransferService binaryTransferService;
   private final SecurityEngine securityEngine;
}
