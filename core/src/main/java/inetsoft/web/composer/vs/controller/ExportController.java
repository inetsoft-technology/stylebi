/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.util.log.LogLevel;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.controller.AssemblyImageServiceProxy;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.OutputStream;
import java.security.Principal;
import java.util.*;

import static inetsoft.web.viewsheet.controller.GetImageController.processImageRenderResult;

@Controller
public class ExportController {
   @Autowired
   public ExportController(VSLifecycleService lifecycleService,
                           AssemblyImageServiceProxy assemblyImageServiceProxy,
                           VSExportService exportService,
                           ExportControllerServiceProxy exportControllerServiceProxy)
   {
      this.lifecycleService = lifecycleService;
      this.serviceProxy = assemblyImageServiceProxy;
      this.exportService = exportService;
      this.exportControllerServiceProxy = exportControllerServiceProxy;
   }

   @GetMapping("/export/check/**")
   @ResponseBody
   public MessageCommand checkExporting(@RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return serviceProxy.checkExporting(Tool.byteDecode(runtimeId), principal);
   }

   @PostMapping("/export/check/**")
   @ResponseBody
   public MessageCommand checkExportingPost(@RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return serviceProxy.checkExporting(Tool.byteDecode(runtimeId), principal);
   }

   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @GetMapping("/export/viewsheet/**")
   @SwitchOrg
   public void exportViewsheet(@RemainingPath @ViewsheetPath String path,
                               @OrganizationID String organizationId,
                               @RequestParam("format") Optional<Integer> formatParam,
                               @RequestParam("match") Optional<Boolean> matchParam,
                               @RequestParam("expandSelections") Optional<Boolean> expandSelectionsParam,
                               @RequestParam("current") Optional<Boolean> currentParam,
                               @RequestParam("previewPrintLayout") Optional<Boolean> previewPrintLayoutParam,
                               @RequestParam("print") Optional<Boolean> printParam,
                               @RequestParam("bookmarks") Optional<String> bookmarksParam,
                               @RequestParam("outtype") Optional<String> outtypeParam,
                               @RequestParam("onlyDataComponents") Optional<Boolean> onlyDataComponentsParam,
                               @RequestParam("delimiter") Optional<String> delimiterParam,
                               @RequestParam("quote") Optional<String> quoteParam,
                               @RequestParam("keepHeader") Optional<Boolean> keepHeaderParam,
                               @RequestParam("tabDelimited") Optional<Boolean> tabDelimitedParam,
                               @RequestParam("tableAssemblies") Optional<String> tablesParam,
                               @RequestParam("exportAllTabbedTables") Optional<Boolean> exportAllTabbedTablesParam,
                               HttpServletResponse response, HttpServletRequest request, Principal principal)
      throws Exception
   {
      path = Tool.byteDecode(path);
      int format = formatParam.orElse(FileFormatInfo.EXPORT_TYPE_PDF);
      String type = outtypeParam.orElse(null);
      boolean exportAllTabbedTables = exportAllTabbedTablesParam.orElse(false);
      boolean expandSelections = expandSelectionsParam.orElse(false);
      boolean current = currentParam.orElse(true);
      boolean previewPrintLayout = previewPrintLayoutParam.orElse(false);
      boolean print = printParam.orElse(false);
      String bookmarkString = bookmarksParam.orElse("");
      String[] bookmarks = bookmarkString.length() > 0 ?
         bookmarkString.split(",") : new String[0];
      boolean onlyDataComponents = onlyDataComponentsParam.orElse(false);
      CSVConfig csvConfig = new CSVConfig();

      //expand by default when exporting directly for pdf if match
      boolean match = matchParam.orElse(!"pdf".equalsIgnoreCase(type));

      // always expand tables when export to csv.
      if(format == FileFormatInfo.EXPORT_TYPE_CSV) {
         match = false;
      }

      if(delimiterParam.isPresent()) {
         csvConfig.setDelimiter(delimiterParam.get());
      }

      if(quoteParam.isPresent()) {
         csvConfig.setQuote(quoteParam.get());
      }

      if(keepHeaderParam.isPresent()) {
         csvConfig.setKeepHeader(keepHeaderParam.get());
      }

      if(tabDelimitedParam.isPresent()) {
         csvConfig.setTabDelimited(tabDelimitedParam.get());
      }

      String tablesString = tablesParam.orElse("");
      String[] tables = tablesString.length() > 0 ? tablesString.split(",") : new String[0];
      csvConfig.setExportAssemblies(Arrays.asList(tables));


      exportViewsheet0(path, match, format, type, request.getParameterMap(), request.getSession().getId(),
                       request.getHeader("user-agent"), previewPrintLayout, expandSelections, current,
                       print, bookmarks, onlyDataComponents, exportAllTabbedTables, csvConfig, response, principal);
   }

   private void exportViewsheet0(String path, boolean match, int format, String type,
                                 Map<String, String[]> parameters, String sessionId, String userAgent,
                                 boolean previewPrintLayout, boolean expandSelections, boolean current,
                                 boolean print, String[] bookmarks, boolean onlyDataComponents, boolean exportAllTabbedTables,
                                 CSVConfig csvConfig, HttpServletResponse response, Principal principal) throws Exception {
      String runtimeId;
      boolean exportEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ);

      if(!previewPrintLayout && !exportEnabled) {
         LOG.error(
            "Failed to export viewsheet since {} have no permission for viewsheet export.",
            principal.getName());
         IdentityID identityID = IdentityID.getIdentityIDFromKey(principal.getName());
         String user = identityID != null ? identityID.getName() : principal.getName();
         throw new MessageException(Catalog.getCatalog().getString(
            "viewer.viewsheet.exporting.failed", user), LogLevel.INFO, false);
      }

      //if there is an outtype param, its value overrides the format value
      if(type != null) {
         format = VSExportService.getFormatNumberFromExtension(type);

         if(format == FileFormatInfo.EXPORT_TYPE_CSV) {
            match = false;
         }
      }

      AssetEntry entry = exportService.getPathAssetEntry(path, principal);
      boolean matchesAssetIdFormat = true;

      if(SUtil.isDefaultVSGloballyVisible() && entry != null) {
         entry = exportService.handleAttemptExportGloballyVisibleAsset(entry, format);
      }

      if(entry != null) {
         runtimeId = exportService.openViewsheet(entry, principal, parameters, sessionId, userAgent);

         ExportControllerService.ViewsheetExportResult result = exportControllerServiceProxy.exportViewsheet(
            runtimeId, type, matchesAssetIdFormat,
            match, expandSelections, current, previewPrintLayout, print,
            bookmarks, onlyDataComponents, exportAllTabbedTables, csvConfig, principal);

         VSExportService.setResponseHeader(
            new ExportResponse(response), result.getSuffix(), "attachment", result.getFileName(), result.getMime());

         BinaryTransfer data = result.getData();
         data.writeData(response.getOutputStream());
      }
   }

   @GetMapping("/export/vs-table/**")
   public void exportViewsheetTable(@RemainingPath String path,
                                    HttpServletRequest request,
                                    HttpServletResponse response, Principal principal)
      throws Exception
   {
      boolean exporting = "true".equals(request.getSession().getAttribute("vs_table_export"));

      if(exporting) {
         response.getWriter().println("__exporting__");
         return;
      }

      request.getSession().setAttribute("vs_table_export", "true");

      try {
         int index = path.lastIndexOf('/');
         String runtimeId = path.substring(0, index);
         String assemblyName = path.substring(index + 1);
         String agent = request.getHeader("User-Agent");

         AssemblyImageService.SheetExportResult result = serviceProxy.exportViewsheetTable(runtimeId, assemblyName, agent, principal);

         if(result == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Export failed.");
            return;
         }

         VSExportService.setResponseHeader(
            new ExportResponse(response),
            result.getSuffix(),
            "attachment",
            result.getFileName(),
            result.getMime());

         OutputStream out = response.getOutputStream();
         out.write(result.getData());
      }
      finally {
         request.getSession().removeAttribute("vs_table_export");
      }
   }

   @GetMapping("/export/vs-chart/**")
   @InGroupedThread
   public void exportViewsheetChart(
      @RemainingPath String path,
      @RequestParam("chartId") String chartId,
      @RequestParam(value = "width", required = false, defaultValue = "0") int width,
      @RequestParam(value = "height", required = false, defaultValue = "0") int height,
      @RequestParam(value = "svg", required = false, defaultValue = "false") boolean svg,
      HttpServletRequest request,
      HttpServletResponse response,
      Principal principal,
      @LinkUri String linkUri)
      throws Exception
   {
      AssetEntry entry = VSExportService.getPathAssetEntry(path, principal);
      OpenViewsheetEvent openVSEvent = new OpenViewsheetEvent();
      openVSEvent.setEntryId(entry.toIdentifier());

      String id = lifecycleService.openViewsheet(openVSEvent, principal, linkUri);

      Dimension chartSize = serviceProxy.getChartSize(id, width, height, chartId, principal);

      AssemblyImageService.ImageRenderResult result = serviceProxy.downloadAssemblyImage(id, chartId, chartSize.getWidth(),
                                                                                         chartSize.getHeight(), chartSize.getWidth(),
                                                                                         chartSize.getHeight(), svg, principal);

      processImageRenderResult(result, request, response);
   }

   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @GetMapping("/export/worksheet/**")
   public void exportWorksheet(@RemainingPath String path,
                               @RequestParam("fileName") Optional<String> fileNameParam,
                               @RequestParam("viewsheetId") Optional<String> viewsheetId,
                               HttpServletRequest request,
                               HttpServletResponse response, Principal principal)
      throws Exception
   {
      int index = path.lastIndexOf('/');
      String runtimeId = path.substring(0, index);
      String tableAssemblyName = path.substring(index + 1);
      String assemblyName = null;
      String vsId = null;

      if(viewsheetId.isPresent()) {
         vsId = viewsheetId.get();
      }

      if(fileNameParam.isPresent()) {
         String fileName = fileNameParam.get();
         assemblyName = fileName.substring(0, fileName.lastIndexOf("_Data_ExportedData"));
      }

      AssemblyImageService.SheetExportResult exportResult = serviceProxy.exportWorksheet(runtimeId, vsId, path,
                                                                                                 assemblyName, tableAssemblyName,
                                                                                                 fileNameParam.orElse(null), principal);

      if("true".equals(request.getSession().getAttribute("vs_table_export"))) {
         response.getWriter().println("__exporting__");
         return;
      }

      request.getSession().setAttribute("vs_table_export", "true");

      try {
         VSExportService.setResponseHeader(new ExportResponse(response), exportResult.getSuffix(),
                                           "attachment", exportResult.getFileName(), exportResult.getMime());

         OutputStream out = response.getOutputStream();
         out.write(exportResult.getData());
      }
      finally {
         request.getSession().removeAttribute("vs_table_export");
      }
   }

   private final VSLifecycleService lifecycleService;
   private final AssemblyImageServiceProxy serviceProxy;
   private final VSExportService exportService;
   private final ExportControllerServiceProxy exportControllerServiceProxy;

   private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);
}
