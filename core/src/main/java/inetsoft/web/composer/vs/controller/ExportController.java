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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.EGraph;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.OfficeExporterFactory;
import inetsoft.report.io.viewsheet.excel.CSVWSExporter;
import inetsoft.report.io.viewsheet.excel.WSExporter;
import inetsoft.report.lens.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.InGroupedThread;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.OutputStream;
import java.security.Principal;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.stream.IntStream;

@Controller
public class ExportController {
   @Autowired
   public ExportController(ViewsheetService viewsheetService,
                           VSLifecycleService lifecycleService,
                           AssemblyImageService assemblyImageService,
                           VSExportService exportService)
   {
      this.viewsheetService = viewsheetService;
      this.lifecycleService = lifecycleService;
      this.assemblyImageService = assemblyImageService;
      this.exportService = exportService;
   }

   @GetMapping("/export/check/**")
   @ResponseBody
   public MessageCommand checkExporting(@RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return checkExporting0(runtimeId, principal);
   }

   @PostMapping("/export/check/**")
   @ResponseBody
   public MessageCommand checkExportingPost(@RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return checkExporting0(runtimeId, principal);
   }

   private MessageCommand checkExporting0(String runtimeId, Principal principal) throws Exception {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      MessageCommand messageCommand = new MessageCommand();

      if("true".equals(rvs.getProperty("__EXPORTING__"))) {
         messageCommand.setType(MessageCommand.Type.WARNING);
         messageCommand.setMessage(Catalog.getCatalog(principal)
                                      .getString("viewer.viewsheet.exporting"));
      }
      else {
         messageCommand.setType(MessageCommand.Type.OK);
      }

      return messageCommand;
   }

   @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
   @GetMapping("/export/viewsheet/**")
   public void exportViewsheet(@RemainingPath @ViewsheetPath String path,
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
      boolean match = matchParam.orElse(true);
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
      exportService.exportViewsheet(
         path, format, match, expandSelections, current, previewPrintLayout, print, bookmarks,
         type, onlyDataComponents, csvConfig, exportAllTabbedTables,
         new ExportResponse(response), request.getParameterMap(), request.getSession().getId(),
         request.getHeader("user-agent"), principal);
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

         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
         RuntimeWorksheet worksheet = rvs.getRuntimeWorksheet();
         ViewsheetSandbox vbox = rvs.getViewsheetSandbox();

         if(vbox == null) {
            return;
         }

         TableLens lens = vbox.getVSTableLens(assemblyName, false);

         if(lens == null) {
            lens = new DefaultTableLens();
         }

         Tool.localizeHeader(lens);
         List<ColumnInfo> columns = new ArrayList<>();

         String fileName = VSExportService.getViewsheetFileName(rvs.getEntry());
         fileName = Tool.normalizeFileName(fileName + "_" + assemblyName);

         VSAssembly tableAssembly = (VSAssembly) rvs.getViewsheet().getAssembly(assemblyName);

         // @by stephenwebster, For Bug #26629
         // The exportExcel method is expecting the table name to be the name of the worksheet.
         // For viewsheet table export, get the source of the table instead.
         if(tableAssembly instanceof TableDataVSAssembly &&
            !"true".equals(SreeEnv.getProperty("table.export.hiddenColumns")))
         {
            TableDataVSAssembly tassembly = (TableDataVSAssembly) tableAssembly;
            SourceInfo srcInfo = tassembly.getSourceInfo();
            TableDataVSAssemblyInfo info = tassembly.getTableDataVSAssemblyInfo();
            assemblyName = srcInfo != null ? srcInfo.getSource() : assemblyName;

            TableLens finalLens = lens;
            int[] visCols = IntStream.range(0, lens.getColCount())
               .filter(c -> info.getColumnWidth2(c, finalLens) > 0 ||
                  Double.isNaN(info.getColumnWidth2(c, finalLens)))
               .toArray();

            // ignore hidden column when exporting data from a table. (56152)
            if(visCols.length != lens.getColCount()) {
               lens = new ColumnMapFilter(lens, visCols);
            }
         }

         exportExcel(request, response, VSUtil.getVSAssemblyBinding(assemblyName),
                     lens, columns, worksheet, fileName);
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);

      Dimension chartSize = assemblyImageService.calculateDownloadSize(width, height, rvs, chartId);
      assemblyImageService.downloadAssemblyImage(rvs.getID(), chartId, chartSize.getWidth(),
                                                 chartSize.getHeight(),chartSize.getWidth(),
                                                 chartSize.getHeight(), svg,
                                                 principal, request, response);
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

      RuntimeWorksheet rws = viewsheetService.getWorksheet(runtimeId, principal);
      Worksheet worksheet = rws.getWorksheet();
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      TableLens lens = box.getTableLens(tableAssemblyName, AssetQuerySandbox.RUNTIME_MODE);
      boolean isPreviewWsTable = path.startsWith("__PREVIEW_WORKSHEET__");
      List<ColumnInfo> columns =
         box.getColumnInfos(tableAssemblyName, AssetQuerySandbox.RUNTIME_MODE);

      if(isPreviewWsTable) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         ViewsheetSandbox vsBox = rvs.getViewsheetSandbox();
         DataTableAssembly tableAssembly =
            (DataTableAssembly) worksheet.getAssembly(tableAssemblyName);
         FormatTableLens2 formatLens = null;
         boolean appliedDC = false;

         if(assembly instanceof ChartVSAssembly) {
            appliedDC = ((ChartVSAssembly) assembly).getVSChartInfo().isAppliedDateComparison();
         }

         if(appliedDC && tableAssembly != null) {
            formatLens = new DcFormatTableLens(
               new MaxRowsTableLens(lens, 5002), getDcFormat(vsBox, assemblyName));
            formatLens.addTableFormat((DataVSAssembly) assembly, tableAssembly,
               tableAssembly.getColumnSelection(), null, null);
         }
         else {
            formatLens = new FormatTableLens2(lens);
            applyTheShowDetailFormat(formatLens, rws.getWorksheet(), tableAssemblyName);
         }

         lens = formatLens;
      }

      String fileName = fileNameParam.orElse(Tool.normalizeFileName(
         getWorksheetFileName(rws.getEntry()) + "_" + tableAssemblyName));

      boolean exporting = "true".equals(request.getSession().getAttribute("vs_table_export"));

      if(exporting) {
         response.getWriter().println("__exporting__");
         return;
      }

      request.getSession().setAttribute("vs_table_export", "true");

      try {
         exportExcel(request, response, tableAssemblyName, lens, columns, rws, fileName);
      }
      finally {
         request.getSession().removeAttribute("vs_table_export");
      }
   }

   private DateComparisonFormat getDcFormat(ViewsheetSandbox box, String assemblyName)
      throws Exception
   {
      if(box == null) {
         return null;
      }

      VGraphPair pair = box.getVGraphPair(assemblyName);
      EGraph graph = pair.getEGraph();

      return (DateComparisonFormat)
         GraphUtil.getAllScales(graph.getCoordinate()).stream()
            .map(s -> s.getAxisSpec().getTextSpec().getFormat())
            .filter(f -> f instanceof DateComparisonFormat).findFirst().orElse(null);
   }

   /**
    * Apply the format for show worksheet preview table.
    * @param formatLens format table lens.
    * @param worksheet worksheet
    * @param assemblyName table that contains format.
    */
   private void applyTheShowDetailFormat(FormatTableLens2 formatLens, Worksheet worksheet,
                                         String assemblyName)
   {
      TableAssemblyInfo tableAssemblyInfo = null;
      TableAssembly tableAssembly = null;

      if(worksheet != null && worksheet.getAssembly(assemblyName) instanceof TableAssembly) {
         tableAssembly = (TableAssembly) worksheet.getAssembly(assemblyName);

         if(tableAssembly == null || tableAssembly.getTableInfo() == null) {
            return;
         }

         tableAssemblyInfo = tableAssembly.getTableInfo();

         if(!Tool.equals(tableAssemblyInfo.getClassName(), "WSPreviewTable")) {
            return;
         }
      }

      if(tableAssemblyInfo != null && tableAssembly != null) {
         Map<String, VSCompositeFormat> formatMap = tableAssemblyInfo.getFormatMap();

         if(formatMap == null) {
            return;
         }

         for(String key : formatMap.keySet()) {
            if(formatMap.get(key) == null) {
               continue;
            }

            formatLens.addColumnFormat(key, tableAssembly.getColumnSelection(), formatMap.get(key));
         }
      }
   }

   private void exportExcel(HttpServletRequest request, HttpServletResponse response,
                            String assemblyName, TableLens lens, List<ColumnInfo> columns,
                            RuntimeWorksheet worksheet, String fileName)
      throws Exception
   {
      String mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      String suffix = "xlsx";
      String disposition = "attachment";
      String name = fileName == null || "".equals(fileName) ? "ExportedWorksheet" : fileName;
      WSExporter exporter;
      String format = SreeEnv.getProperty("table.export.format");
      String threshold = SreeEnv.getProperty("table.export.cell.threshold.forcetocsv");
      int cellThreshold = -1;

      if(threshold != null) {
         try {
            cellThreshold = Integer.parseInt(threshold);
         }
         catch(NumberFormatException ignore) {
         }
      }

      int row = lens == null ? 1 : lens.getRowCount();
      int col = lens == null ? 1 : lens.getColCount();

      // if the table has a lot of rows, use csv format to save memory
      if(lens != null && (lens.moreRows(5000) && format == null ||
         // force to use csv if cell numbers larger than the cell threshold setted by property.
         cellThreshold != -1 && lens.getColCount() * lens.getRowCount() > cellThreshold) ||
         "csv".equals(format))
      {
         exporter = new CSVWSExporter();
         suffix = "csv";
         String agent = request.getHeader("User-Agent");

         if(agent != null && agent.contains("Safari")) {
            mime = "application/octet-stream";
         }
         else {
            // For Bug #9026, if output is CSV override the mime type as some
            // systems might use this to determine what the file type should be
            mime = "text/csv";
         }
      }
      else {
         exporter = OfficeExporterFactory.getInstance().createWorksheetExporter(row, col);
      }

      try(OutputStream out = response.getOutputStream()) {
         VSExportService.setResponseHeader(new ExportResponse(response), suffix, disposition, name, mime);

         if(lens != null) {
            exporter.prepareSheet(assemblyName, worksheet, row, col);
            Class[] colTypes = exporter.getColTypes(lens);
            HashMap<Integer, Integer> map = getColumnMap(lens, columns);
            exporter.writeTable(lens, columns, colTypes, map);
         }

         exporter.write(out);
      }
   }

   private HashMap<Integer, Integer> getColumnMap(TableLens lens, List<ColumnInfo> colInfo) {
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);
      HashMap<Integer, Integer> map = new HashMap<>();

      for(int i = 0; i < colInfo.size(); i++) {
         ColumnInfo info = colInfo.get(i);
         String attr = "null";
         ColumnRef ref = new ColumnRef(new AttributeRef(attr));

         // avoid the value at the specified cell is null.
         if(!Tool.equals(ref, info.getColumnRef())) {
            map.put(Util.findColumn(columnIndexMap, info, lens), i);
         }
         else {
            map.put(i, i);
         }
      }

      return map;
   }

   private String getWorksheetFileName(AssetEntry entry) {
      return VSExportService.getFileName(entry, "ExportedWorksheet");
   }

   private final ViewsheetService viewsheetService;
   private final VSLifecycleService lifecycleService;
   private final AssemblyImageService assemblyImageService;
   private final VSExportService exportService;
}
