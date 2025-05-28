/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.snapshot.SnapshotVSExporter;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.security.Principal;

@Service
@ClusterProxy
public class ExportControllerService {

   public ExportControllerService(ViewsheetService viewsheetService, CoreLifecycleService coreLifecycleService) {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ViewsheetExportResult exportViewsheet(@ClusterProxyKey String runtimeId, String type, boolean matchesAssetIdFormat,
                                                boolean match, boolean expandSelections, boolean current,
                                                boolean previewPrintLayout, boolean print, String[] bookmarks,
                                                boolean onlyDataComponents, boolean exportAllTabbedTables,
                                                CSVConfig csvConfig, Principal principal) throws Exception
   {

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      rvs.getViewsheet().getViewsheetInfo().setMVOnDemand(false);

      CommandDispatcher.withDummyDispatcher(principal, d -> {
         ChangedAssemblyList clist = this.coreLifecycleService.createList(false, d, rvs, null);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), null, d, false, true, true, clist);
         return null;
      });

      Viewsheet vs = rvs.getViewsheet();

      if("CSV".equalsIgnoreCase(type) && vs != null) {
         boolean foundTable = VSUtil.getTableDataAssemblies(vs, true).stream()
            .anyMatch(CSVUtil::needExport);

         if(!foundTable) {
            throw new MessageException(Catalog.getCatalog().getString("common.repletAction.exportFailed.cvs"));
         }
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof TableDataVSAssembly) {
            rvs.getViewsheetSandbox().resetDataMap(assembly.getAbsoluteName());
         }
      }

      boolean embedded = "embed".equalsIgnoreCase(SreeEnv.getProperty("pdf.output.attachment"))
         && !matchesAssetIdFormat;

      int format = VSExportService.getFormatNumberFromExtension(type);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      writeViewsheetExport(rvs, out, principal, format, previewPrintLayout, print, match,
                           expandSelections, current, bookmarks, onlyDataComponents,
                           csvConfig, null, false, exportAllTabbedTables);

      String fileName = VSExportService.getViewsheetFileName(rvs.getEntry());
      String suffix = VSExportService.getSuffix(format);
      String mime = VSExportService.getMime(format);

      try {
         return new ViewsheetExportResult(out.toByteArray(), fileName, mime, suffix);
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
         Viewsheet vs = rvs.getOriginalBookmark(bookmark, rvs.getEntry().getOrgID());
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

   private void writeSnapshotExport(RuntimeViewsheet rvs, OutputStream out) throws Exception {
      SnapshotVSExporter sexporter = new SnapshotVSExporter(rvs);
      sexporter.setLogExport(true);
      sexporter.write(out);
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private static final Logger LOG = LoggerFactory.getLogger(ExportControllerService.class);

   public static final class ViewsheetExportResult implements Serializable {
      private final byte[] data;
      private final String fileName;
      private final String mime;
      private final String suffix;

      public ViewsheetExportResult(byte[] data, String fileName, String mime, String suffix) {
         this.data = data;
         this.fileName = fileName;
         this.mime = mime;
         this.suffix = suffix;
      }

      public byte[] getData() { return data; }
      public String getFileName() { return fileName; }
      public String getMime() { return mime; }
      public String getSuffix() { return suffix; }
   }
}
