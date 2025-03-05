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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.snapshot.SnapshotVSExporter;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VsToReportConverter;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VSExportService {
   @Autowired
   public VSExportService(ViewsheetService viewsheetService, CoreLifecycleService coreLifecycleService,
                          ParameterService parameterService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.parameterService = parameterService;
   }

   public void exportViewsheet(String path, int format, boolean match, boolean expandSelections,
                               boolean current, boolean previewPrintLayout, boolean print,
                               String[] bookmarks, String type, ExportResponse response,
                               Map<String, String[]> parameters, String sessionId, String userAgent,
                               Principal principal) throws Exception
   {
      exportViewsheet(path, format, match, expandSelections, current, previewPrintLayout, print,
         bookmarks, type, false, response, parameters, sessionId, userAgent, principal);
   }

   public void exportViewsheet(String path, int format, boolean match, boolean expandSelections,
                               boolean current, boolean previewPrintLayout, boolean print,
                               String[] bookmarks, String type, boolean onlyDataComponents,
                               ExportResponse response, Map<String, String[]> parameters,
                               String sessionId, String userAgent,
                               Principal principal) throws Exception
   {
      exportViewsheet(path, format, match, expandSelections, current, previewPrintLayout,
         print, bookmarks, type, onlyDataComponents, (CSVConfig) null, response, parameters, sessionId, userAgent, principal);
   }

   public void exportViewsheet(String path, int format, boolean match, boolean expandSelections,
                               boolean current, boolean previewPrintLayout, boolean print,
                               String[] bookmarks, String type, boolean onlyDataComponents,
                               CSVConfig csvConfig, ExportResponse response,
                               Map<String, String[]> parameters, String sessionId, String userAgent,
                               Principal principal) throws Exception
   {
      exportViewsheet(path, format, match, expandSelections, current, previewPrintLayout, print,
         bookmarks, type, onlyDataComponents, csvConfig, false, response,
         parameters, sessionId, userAgent, principal);
   }

   public void exportViewsheet(String path, int format, boolean match, boolean expandSelections,
                               boolean current, boolean previewPrintLayout, boolean print,
                               String[] bookmarks, String type, boolean onlyDataComponents,
                               CSVConfig csvConfig, boolean exportAllTabbedTables,
                               ExportResponse response, Map<String, String[]> parameters,
                               String sessionId, String userAgent, Principal principal)
      throws Exception
   {
      String runtimeId;
      boolean exportEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ);

      if(!previewPrintLayout && !exportEnabled) {
         LOG.error(
            "Failed to export viewsheet since {} have no permission for viewsheet export.",
            principal.getName());
         return;
      }

      //if there is an outtype param, its value overrides the format value
      if(type != null) {
         format = getFormatNumberFromExtension(type);

         if(format == FileFormatInfo.EXPORT_TYPE_CSV) {
            match = false;
         }
      }

      RuntimeViewsheet rvs;
      AssetEntry entry = getPathAssetEntry(path, principal);
      boolean matchesAssetIdFormat = true;

      if(SUtil.isDefaultVSGloballyVisible() && entry != null) {
         entry = handleAttemptExportGloballyVisibleAsset(entry, format);
      }

      if(entry != null) {
         runtimeId = openViewsheet(entry, principal, parameters, sessionId, userAgent);
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         // disable mv on demand for exporting
         rvs.getViewsheet().getViewsheetInfo().setMVOnDemand(false);

         CommandDispatcher.withDummyDispatcher(principal, d -> {
            ChangedAssemblyList clist = this.coreLifecycleService.createList(false, d, rvs,
                                                                             null);
            coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), null, d, false,
                                                  true, true, clist);

            return null;
         });
      }
      else {
         runtimeId = path;
         matchesAssetIdFormat = false;
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
      }

      // Tables need to be reset as they may contain old format.
      // The tables will be reloaded after the css is updated in AbstractVSExporter.
      Viewsheet vs = rvs.getViewsheet();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof TableDataVSAssembly) {
            rvs.getViewsheetSandbox().resetDataMap(assembly.getAbsoluteName());
         }
      }

      boolean embedded = "embed".equalsIgnoreCase(SreeEnv.getProperty("pdf.output.attachment")) &&
         !matchesAssetIdFormat;

      try {
         exportViewsheet(
            rvs, format, match, expandSelections, current, previewPrintLayout, print, bookmarks,
            embedded, onlyDataComponents, csvConfig, exportAllTabbedTables, response, principal);
      }
      catch(Exception ex) {
         LOG.warn("Unable to complete export for {}", runtimeId);
         throw ex;
      }
      finally {
         if(matchesAssetIdFormat ||
            rvs != null && "true".equals(rvs.getProperty("_CLOSE_AFTER_EXPORT_")))
         {
            viewsheetService.closeViewsheet(runtimeId, principal);
         }
      }
   }

   public String openViewsheet(AssetEntry entry, Principal principal,
                               Map<String, String[]> paramMap, String sessionId, String userAgent)
      throws Exception
   {
      OpenViewsheetEvent openViewsheetEvent = new OpenViewsheetEvent();
      openViewsheetEvent.setViewer(true);
      openViewsheetEvent.setEntryId(entry.toIdentifier());
      openViewsheetEvent.setUserAgent(userAgent);

      StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.create(StompCommand.SEND);
      stompHeaderAccessor.setUser(principal);
      stompHeaderAccessor.setSessionId(sessionId);
      VariableTable vt = parameterService.readParameters(paramMap);
      String execSessionId =
         XSessionService.createSessionID(XSessionService.EXPORE_VIEW, entry.getName());

      return CommandDispatcher.withDummyDispatcher(principal, d -> coreLifecycleService.openViewsheet(
         viewsheetService, openViewsheetEvent, principal, null, null, entry, d, null,
         null, true, openViewsheetEvent.getDrillFrom(), vt,
         openViewsheetEvent.getFullScreenId(), execSessionId));
   }

   public void exportViewsheet(RuntimeViewsheet rvs, int format, boolean match,
                               boolean expandSelections, boolean current,
                               boolean previewPrintLayout, boolean print, String[] bookmarks,
                               boolean embedded, ExportResponse response, Principal principal)
      throws Exception
   {
      exportViewsheet(rvs, format, match, expandSelections, current, previewPrintLayout, print,
         bookmarks, embedded, false, response, principal);
   }

   public void exportViewsheet(RuntimeViewsheet rvs, int format, boolean match,
                               boolean expandSelections, boolean current,
                               boolean previewPrintLayout, boolean print, String[] bookmarks,
                               boolean embedded, boolean onlyDataComponents,
                               ExportResponse response, Principal principal)
      throws Exception
   {
      exportViewsheet(rvs, format, match, expandSelections, current, previewPrintLayout, print,
         bookmarks, embedded, onlyDataComponents, null, response, principal);
   }

   public void exportViewsheet(RuntimeViewsheet rvs, int format, boolean match,
                               boolean expandSelections, boolean current,
                               boolean previewPrintLayout, boolean print, String[] bookmarks,
                               boolean embedded, boolean onlyDataComponents, CSVConfig csvConfig,
                               ExportResponse response, Principal principal)
      throws Exception
   {
      exportViewsheet(rvs, format, match, expandSelections, current, previewPrintLayout, print,
         bookmarks, embedded, onlyDataComponents, csvConfig, false, response, principal);
   }

   public void exportViewsheet(RuntimeViewsheet rvs, int format, boolean match,
                               boolean expandSelections, boolean current,
                               boolean previewPrintLayout, boolean print, String[] bookmarks,
                               boolean embedded, boolean onlyDataComponents, CSVConfig csvConfig,
                               boolean exportAllTabbedCrosstab, ExportResponse response,
                               Principal principal)
      throws Exception
   {
      rvs.setProperty("__EXPORTING__", "true");

      try {
         boolean expandEnabled = SecurityEngine.getSecurity().checkPermission(
            principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "ExportExpandComponents",
            ResourceAction.READ);

         if(!match && !expandEnabled) {
            match = true;
         }

         doExportViewsheet(
            rvs, format, match, expandSelections, current, previewPrintLayout, print, bookmarks,
            embedded, onlyDataComponents, csvConfig, exportAllTabbedCrosstab, response, principal);
      }
      finally {
         rvs.setProperty("__EXPORTING__", null);
      }
   }

   private void doExportViewsheet(RuntimeViewsheet rvs, int format, boolean match,
                                  boolean expandSelections, boolean current,
                                  boolean previewPrintLayout, boolean print, String[] bookmarks,
                                  boolean embedded, boolean onlyDataComponents, CSVConfig csvConfig,
                                  boolean exportAllTabbedCrosstab, ExportResponse response,
                                  Principal principal)
      throws Exception
   {
      PrintLayout tempLayout = rvs.getViewsheet().getLayoutInfo().getPrintLayout();
      boolean excelToCSV = false;

      // update css format before executing the vs
      rvs.getViewsheet().updateCSSFormat(AbstractVSExporter.getFileType(format), null,
                                         rvs.getViewsheetSandbox());

      // execute the vs to ensure script variable is available(variable is isolated for threads).
      // see code VSAScriptable#varMap
      CommandDispatcher.withDummyDispatcher(principal, d -> {
         ChangedAssemblyList clist = this.coreLifecycleService.createList(false, d, rvs, null);
         // do not reset the form table.
         rvs.getViewsheetSandbox().exportRefresh.set(true);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), null, d, false, true, true, clist);
         rvs.getViewsheetSandbox().exportRefresh.set(false);
         return null;
      });

      if(format == FileFormatInfo.EXPORT_TYPE_EXCEL && CSVUtil.hasLargeDataTable(rvs)) {
         format = FileFormatInfo.EXPORT_TYPE_CSV;
         excelToCSV = true;
      }

      if(VsToReportConverter.getTempLayout(rvs.getID()) != null) {
         VsToReportConverter.removeTempLayout(rvs.getID());
      }

      VsToReportConverter.addTempLayout(rvs.getID(), tempLayout);

      String mime = getMime(format);
      String suffix = getSuffix(format);

      PrintLayout printLayout = null;

      try {
         if(previewPrintLayout) {
            Viewsheet vs = rvs.getViewsheet();
            printLayout = vs.getLayoutInfo().getPrintLayout();
            PrintLayout temp = VsToReportConverter.getTempLayout(rvs.getID());
            vs.getLayoutInfo().setPrintLayout(temp);
         }

         if(response.isHttp()) {
            writeViewsheetExport(
               rvs, response, principal, format, previewPrintLayout, print,
               match, expandSelections, current, embedded, mime, suffix, bookmarks, onlyDataComponents,
               excelToCSV, csvConfig, exportAllTabbedCrosstab);
         }
         else {
            writeViewsheetExport(
               rvs, response.getOutputStream(), principal, format, previewPrintLayout, print, match,
               expandSelections, current, bookmarks, csvConfig, excelToCSV);
         }
      }
      finally {
         // set back the vs printlayout, and remove the temp printlayout
         // of the rvs viewsheet.
         if(printLayout != null && previewPrintLayout) {
            rvs.getViewsheet().getLayoutInfo().setPrintLayout(printLayout);
            VsToReportConverter.removeTempLayout(rvs.getID());
         }

         if(!rvs.isDisposed()) {
            rvs.getViewsheet().updateCSSFormat(null, null,
                                               rvs.getViewsheetSandbox());
         }
      }
   }

   /**
    * Attempting to export globally visible viewsheets requires passing underlying assetEntry
    */
   private AssetEntry handleAttemptExportGloballyVisibleAsset(AssetEntry entry, int format) throws MessageException {
      String curOrg = OrganizationManager.getInstance().getCurrentOrgID();
      boolean isSnapshot = Tool.equals(format, FileFormatInfo.EXPORT_TYPE_SNAPSHOT);
      boolean snapShotProhibited = false;

      try {
         if(Tool.equals(entry.getOrgID(), curOrg)) {
            if(!viewsheetService.getAssetRepository().containsEntry(entry)) {
               String defOrgFolder = OrganizationManager.getGlobalDefOrgFolderName();

               if(entry.getPath().contains(defOrgFolder)) {
                  String defOrgPath = entry.getPath()
                     .replace(defOrgFolder + "/", "");

                  AssetEntry globallyVisibleAssetEntry = new AssetEntry(
                     entry.getScope(), entry.getType(), defOrgPath, null, Organization.getDefaultOrganizationID());

                  if(viewsheetService.getAssetRepository().containsEntry(globallyVisibleAssetEntry)) {
                     if(!isSnapshot) {
                        return globallyVisibleAssetEntry;
                     }
                     else {
                        snapShotProhibited = true;
                     }
                  }
               }
            }

         }
      }
      catch(Exception e){
         //ignore
      }

      if(snapShotProhibited) {
         throw new MessageException(Catalog.getCatalog().getString(
            "deny.access.export.vso.globally.visible", entry), LogLevel.INFO, false);
      }

      return entry;
   }

   public static String getViewsheetFileName(AssetEntry entry) {
      return getFileName(entry, "ExportedViewsheet");
   }

   public static String getFileName(AssetEntry entry, String defaultName) {
      String name;

      if(entry != null && entry.getAlias() != null && !entry.getAlias().trim().isEmpty()) {
         name = entry.getAlias();
      }
      else if(entry != null && entry.getName() != null && !entry.getName().trim().isEmpty()) {
         name = entry.getName();
      }
      else {
         name = defaultName;
      }

      return name;
   }

   public static void setResponseHeader(ExportResponse response, String suffix,
                                  String disposition, String name, String mime)
   {
      String name0 = name;
      String fileName = Tool.encodeURL(Tool.normalizeFileName(name0 + "." + suffix));
      fileName = fileName.replaceAll("\'", "%27");

      // file name too long will cause problem in office 2010
      // @see bug1393467998696
      while(fileName.length() > 60) {
         if(name0.length() <= 1) {
            break;
         }

         name0 = name0.substring(0, name0.length() - 1);
         fileName = Tool.encodeURL(Tool.normalizeFileName(name0 + "." + suffix));
      }

      response.setHeader("Content-disposition", StringUtils.normalizeSpace(disposition) +
         "; filename*=utf-8''" + StringUtils.normalizeSpace(fileName) +
         "; filename=\"" + StringUtils.normalizeSpace(fileName) + "\"");

      response.setHeader("extension", StringUtils.normalizeSpace(suffix));
      // @by yuz, avoid IE bugs for MS Office
      response.setHeader("Cache-Control", "");
      response.setHeader("Pragma", "");
      response.setContentType(mime);
   }

   /**
    * Test of path matches one of the path patterns:
    * /assetId
    * /user/{username}/path
    * /global/path
    * @param path the path to be tested and used to create AssetEntry
    * @return new AssetEntry if path matches specified format otherwise null
    */
   public static AssetEntry getPathAssetEntry(String path, Principal principal) {
      AssetEntry entry = null;
      Matcher matcher;

      if(path.matches("[14]\\Q^\\E128\\Q^\\E.+\\Q^\\E.+")) {
         //get user from the runtimeId
         entry = AssetEntry.createAssetEntry(path, ((XPrincipal) principal).getOrgId());
      }
      else if((matcher = GLOBAL_PATH_PATTERN.matcher(path)).matches()) { // NOSONAR
         entry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, matcher.group(1), null);
      }
      else if((matcher = USER_PATH_PATTERN.matcher(path)).matches()) { // NOSONAR
         // must be 'user', {username}, and {path} components
         entry = new AssetEntry(
            AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, matcher.group(2),
            IdentityID.getIdentityIDFromKey(matcher.group(1)));
      }

      return entry;
   }


   private String getMime(int format) {
      String mime = "application/octet-stream";

      switch(format) {
      case FileFormatInfo.EXPORT_TYPE_EXCEL:
         return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      case FileFormatInfo.EXPORT_TYPE_POWERPOINT:
         return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
      case FileFormatInfo.EXPORT_TYPE_PDF:
         return "application/pdf";
      case FileFormatInfo.EXPORT_TYPE_PNG:
         return "image/png";
      case FileFormatInfo.EXPORT_TYPE_SNAPSHOT:
         break;
      case FileFormatInfo.EXPORT_TYPE_HTML:
         break;
      case FileFormatInfo.EXPORT_TYPE_CSV:
            break;
      default:
         throw new RuntimeException("Unsupport file format: " + format);
      }

      return mime;
   }

   private String getSuffix(int format) {
      switch(format) {
      case FileFormatInfo.EXPORT_TYPE_EXCEL:
         return "xlsx";
      case FileFormatInfo.EXPORT_TYPE_POWERPOINT:
         return "pptx";
      case FileFormatInfo.EXPORT_TYPE_PDF:
         return "pdf";
      case FileFormatInfo.EXPORT_TYPE_PNG:
         return "png";
      case FileFormatInfo.EXPORT_TYPE_SNAPSHOT:
         return "vso";
      case FileFormatInfo.EXPORT_TYPE_HTML:
         return "html";
      case FileFormatInfo.EXPORT_TYPE_CSV:
         return "zip";
      default:
         throw new RuntimeException("Unsupport file format: " + format);
      }
   }

   private void writeViewsheetExport(RuntimeViewsheet rvs, ExportResponse response,
                                     Principal principal, int format, boolean previewPrintLayout,
                                     boolean print, boolean match, boolean expandSelections,
                                     boolean current, boolean embedded, String mime, String suffix,
                                     String[] bookmarks, boolean onlyDataComponents,
                                     boolean excelToCSV, CSVConfig csvConfig,
                                     boolean exportAllTabbedCrosstab)
      throws Exception
   {
      String name = getViewsheetFileName(rvs.getEntry());
      String disposition = !rvs.isPreview() &&
         FileFormatInfo.EXPORT_TYPE_PDF == format &&
         embedded || print ? "inline" : "attachment";

      if(!print) {
         setResponseHeader(response, suffix, disposition, name, mime);
      }
      else {
         response.setContentType(mime);
      }

      if(format == FileFormatInfo.EXPORT_TYPE_SNAPSHOT) {
         // @by Chris Spagnoli, for Bug #7141
         // Set Content-disposition for snapshot exports, otherwise certain browsers
         // (Chrome, Safari, older IE) will mangle any special characters in file name.
         response.setHeader("extension", "vso");
      }

      // When excel data is large, export excel first and add it to csv zip.
      if(excelToCSV) {
         int fmt = FileFormatInfo.EXPORT_TYPE_EXCEL;
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File tmpDir = this.createTmpDir();
         String excel = rvs.getEntry().getName() + ".xlsx";
         File excelFile = fileSystemService.getFile(tmpDir.getPath(), excel);

         // export to excel.
         try(OutputStream out = new FileOutputStream(excelFile)) {
            writeViewsheetExport(rvs, out, principal, fmt, previewPrintLayout, print, match,
               expandSelections,  current, bookmarks, onlyDataComponents, csvConfig, null, true,
               false);
         }

         // export to csv. add the excel file to csv zip.
         try(TempFile tempFile = new TempFile(rvs.getID())) {
            tempFile.write(out -> {
               writeViewsheetExport(rvs, out, principal, format,  previewPrintLayout, print,
                  false, expandSelections, current, bookmarks, onlyDataComponents, csvConfig,
                     excelFile, true, false);
            });

            response.setContentLength(tempFile.getLength());
            tempFile.copyTo(response);
         }

         return;
      }

      try(TempFile tempFile = new TempFile(rvs.getID())) {
         tempFile.write(out -> {
            writeViewsheetExport(
                    rvs, out, principal, format,  previewPrintLayout, print, match,
                    expandSelections, current, bookmarks, onlyDataComponents, csvConfig,
                    null, false, exportAllTabbedCrosstab);
         });

         response.setContentLength(tempFile.getLength());
         tempFile.copyTo(response);
      }
   }

   private File createTmpDir() throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String uuid =  UUID.randomUUID().toString();
      String dir = fileSystemService.getCacheDirectory() + File.separator + uuid;
      File tmpDir = fileSystemService.getFile(dir);

      if(!tmpDir.mkdir()) {
         LOG.warn(
                 "Failed to create temporary directory: " + tmpDir);
      }

      return tmpDir;
   }

   private void writeViewsheetExport(RuntimeViewsheet rvs, OutputStream out, Principal principal,
                                     int format, boolean previewPrintLayout, boolean print,
                                     boolean match, boolean expandSelections, boolean current,
                                     String[] bookmarks, CSVConfig csvConfig,
                                     boolean excelToCSV) throws Exception
   {
      writeViewsheetExport(rvs, out, principal, format, previewPrintLayout, print, match,
         expandSelections, current, bookmarks, false, csvConfig, null, excelToCSV, false);
   }

   private void writeViewsheetExport(RuntimeViewsheet rvs, OutputStream out, Principal principal,
                                     int format, boolean previewPrintLayout, boolean print,
                                     boolean match, boolean expandSelections, boolean current,
                                     String[] bookmarks, boolean onlyDataComponents,
                                     CSVConfig csvConfig, File excelFile, boolean excelToCSV,
                                     boolean exportAllTabbedTables)
      throws Exception
   {
      if(format == FileFormatInfo.EXPORT_TYPE_SNAPSHOT) {
         writeSnapshotExport(rvs, out);
      }
      else {
         VSExporter exporter = AbstractVSExporter.getVSExporter(
                    format, PortalThemesManager.getColorTheme(), out, print, csvConfig);

         if(exporter instanceof ExcelVSExporter) {
            ((ExcelVSExporter) exporter).setExcelToCSV(excelToCSV);
            ((ExcelVSExporter) exporter).setExportAllTabbedTables(exportAllTabbedTables);
         }

         if(!onlyDataComponents && exporter instanceof CSVVSExporter) {
            ((CSVVSExporter) exporter).setExcelFile(excelFile);
         }

         writeViewsheetExport(rvs, exporter, principal, match, expandSelections, current, bookmarks,
            onlyDataComponents, previewPrintLayout);
      }
   }

   private void writeSnapshotExport(RuntimeViewsheet rvs, OutputStream out) throws Exception {
      SnapshotVSExporter sexporter = new SnapshotVSExporter(rvs);
      sexporter.setLogExport(true);
      sexporter.write(out);
   }

   private void writeViewsheetExport(RuntimeViewsheet rvs, VSExporter exporter, Principal principal,
                                     boolean match, boolean expandSelections,
                                     boolean current, String[] bookmarks,
                                     boolean onlyDataComponents, boolean previewPrintLayout)
      throws Exception
   {
      exporter.setExpandSelections(expandSelections);
      exporter.setMatchLayout(match);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setOnlyDataComponents(onlyDataComponents && !match);
      exporter.setSandbox(rvs.getViewsheetSandbox());

      int vmode = Viewsheet.SHEET_RUNTIME_MODE;
      ViewsheetSandbox rbox = rvs.getViewsheetSandbox();

      if(rbox == null) {
         return;
      }

      Viewsheet viewsheet = rvs.getViewsheet();

      if(viewsheet == null) {
         throw new RuntimeException("Can not find viewsheet: " + rvs.getID());
      }

      exporter.setLogExecution(true);
      exporter.setLogExport(true);

      if(current) {
         Viewsheet cviewsheet = viewsheet.clone();
         rvs.setViewsheet(cviewsheet);

         try {
            // don't use the scale-to-screen size for export
            VSEventUtil.clearScale(cviewsheet);
            Assembly[] assemblies = cviewsheet.getAssemblies(false);

            for(int i = 0; rbox != null && i < assemblies.length; i++) {
               VSAssembly assembly = (VSAssembly) assemblies[i];

               if(assembly instanceof CalcTableVSAssembly) {
                  continue;
               }

               AnnotationVSUtil.refreshAllAnnotations(rvs, assembly, null, null);
            }

            ViewsheetSandbox exportBox = rbox;

            if(previewPrintLayout && rbox.getMode() == AbstractSheet.SHEET_DESIGN_MODE) {
               exportBox =
                  new ViewsheetSandbox(cviewsheet, vmode, rbox.getUser(), rbox.getAssetEntry());
               exportBox.prepareMVCreation();

               for(int i = 0; exportBox != null && i < assemblies.length; i++) {
                  VSAssembly assembly = (VSAssembly) assemblies[i];
                  exportBox.executeScript(assembly);
               }

               final AssetQuerySandbox assetQuerySandbox = exportBox.getAssetQuerySandbox();

               if(assetQuerySandbox != null) {
                  assetQuerySandbox.refreshVariableTable(rbox.getVariableTable());
               }
            }
            else {
               exportBox.setViewsheet(cviewsheet, false);
            }
            Catalog catalog = Catalog.getCatalog(principal);
            exporter.setSandbox(exportBox);

            if(exporter instanceof AbstractVSExporter) {
               ((AbstractVSExporter) exporter).setRuntimeViewsheet(rvs);
            }

            exporter.export(exportBox, catalog.getString("Current View"), new VSPortalHelper());
         }
         finally {
            rbox.setViewsheet(viewsheet, false);
            rvs.setViewsheet(viewsheet);
         }
      }

      Catalog catalog = Catalog.getCatalog(principal);
      ViewsheetSandbox sandbox = null;

      for(int i = 0; i < bookmarks.length; i++) {
         String bookmark = bookmarks[i];

         if(catalog.getString("(Home)").equals(bookmark)) {
            bookmark = "(Home)";
         }

         rvs.getViewsheet().getRuntimeEntry().setProperty("keepAnnoVis", "true");
         Viewsheet vs = rvs.getOriginalBookmark(bookmark);
         rvs.getViewsheet().getRuntimeEntry().setProperty("keepAnnoVis", null);
         VSEventUtil.clearScale(vs);
         sandbox = new ViewsheetSandbox(vs, vmode, principal, false, rvs.getEntry());
         AssetQuerySandbox abox = sandbox.getAssetQuerySandbox();

         if(abox != null) {
            abox.refreshVariableTable(rbox.getVariableTable());
         }

         try {
            sandbox.processOnInit();
            sandbox.reset(null, vs.getAssemblies(),
                          new ChangedAssemblyList(), true, true, null);
         }
         catch(Exception ex) {
            LOG.error("Failed to execute onInit() and onLoad() scripts", ex);
         }

         bookmarks[i] = Tool.replaceAll(bookmarks[i], ":", "-");

         if(exporter instanceof AbstractVSExporter) {
            ((AbstractVSExporter) exporter).setRuntimeViewsheet(rvs);
         }

         exporter.export(sandbox, bookmarks[i], (i + 1), new VSPortalHelper());
      }

      exporter.setLogExecution(false);
      exporter.write();

      if(sandbox != null) {
         sandbox.dispose();
      }
   }

   private int getFormatNumberFromExtension(String ext) {
      switch(ext.toLowerCase()) {
      case "xlsx":
      case "xls":
         return FileFormatInfo.EXPORT_TYPE_EXCEL;
      case "pptx":
      case "ppt":
         return FileFormatInfo.EXPORT_TYPE_POWERPOINT;
      case "pdf":
         return FileFormatInfo.EXPORT_TYPE_PDF;
      case "vso":
         return FileFormatInfo.EXPORT_TYPE_SNAPSHOT;
      case "html":
         return FileFormatInfo.EXPORT_TYPE_HTML;
      case "png":
         return FileFormatInfo.EXPORT_TYPE_PNG;
      case "csv":
         return FileFormatInfo.EXPORT_TYPE_CSV;
      default:
         throw new RuntimeException("Unsupported output type: " + ext);
      }
   }

   public static final int EXCEL_MAX_ROW = 50000;
   public static final int EXCEL_LIMIT_ROW = 5000;
   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final ParameterService parameterService;

   private static final Logger LOG = LoggerFactory.getLogger(VSExportService.class);
   private static final Pattern USER_PATH_PATTERN = Pattern.compile("^user/([^/]+)/(.+)$");
   private static final Pattern GLOBAL_PATH_PATTERN = Pattern.compile("^global/(.+)$");

   private static final class TempFile implements Closeable {
      TempFile(String id) {
         file = FileSystemService.getInstance()
            .getCacheTempFile(Tool.normalizeFileName(id), "export");
      }

      public void write(IOConsumer fn) throws Exception {
         try(OutputStream output = new FileOutputStream(file)) {
            fn.write(output);
         }
      }

      public OutputStream getOutputStream() throws IOException {
         return new FileOutputStream(file);
      }

      public int getLength() {
         return (int) file.length();
      }

      public void copyTo(ExportResponse response) throws IOException {
         try(OutputStream output = response.getOutputStream()) {
            Files.copy(file.toPath(), output);
         }
         catch(IOException ioe) {
            String msg = ioe.getMessage();

            if(msg != null &&
               !msg.contains("ClientAbortException") &&
               !ioe.getClass().getName().contains("ClientAbortException"))
            {
               LOG.debug("Failed to write response data", ioe);
            }
            else {
               throw ioe;
            }
         }
      }

      @Override
      public void close() throws IOException {
         try {
            FileSystemService.getInstance().deleteFile(file.getAbsolutePath());
         }
         catch(NoSuchFileException ex) {
            // ignore
         }
      }

      private final File file;
   }

   @FunctionalInterface
   private interface IOConsumer {
      void write(OutputStream output) throws Exception;
   }
}
