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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.CSVInfo;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.*;
import inetsoft.uql.text.TextOutput;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.uql.util.filereader.*;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.web.composer.model.ws.ImportCSVDialogModel;
import inetsoft.web.composer.model.ws.ImportCSVDialogModelValidator;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.ForceNotCloseWorksheetCommand;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.*;
import java.security.Principal;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * Controller that provides the endpoints for the importing a data file into the worksheet.
 *
 * @since 12.3
 */
@Controller
public class ImportCSVDialogController extends WorksheetController {
   public ImportCSVDialogController(PlaceholderService placeholderService) {
      this.placeholderService = placeholderService;
   }

   private static String getLimitMessage(boolean colLimit, boolean textLimit) {
      if(colLimit && textLimit) {
         return getTextColumnLimitMessage();
      }
      else if(colLimit) {
         return Util.getColumnLimitMessage();
      }
      else if(textLimit) {
         return Util.getTextLimitMessage();
      }

      return null;
   }

   private static String getTextColumnLimitMessage() {
      return String.format("%s\n%s", Util.getColumnLimitMessage(), Util.getTextLimitMessage());
   }

   /**
    * Gets the top-level descriptor of the rectangle.
    *
    * @param runtimeId the runtime identifier of the rectangle.
    *
    * @return the rectangle descriptor.
    */
   @GetMapping("/api/composer/ws/import-csv-dialog-model/{runtimeId}")
   @ResponseBody
   public ImportCSVDialogModel getImportCSVDialogModel(@PathVariable("runtimeId") String runtimeId)
   {
      return ImportCSVDialogModel.builder()
         .encodingSelected("UTF-8")
         .delimiter(',')
         .delimiterTab(false)
         .unpivotCB(false)
         .headerCols(1)
         .firstRowCB(true)
         .removeQuotesCB(true)
         .build();
   }

   @PostMapping(
      value = "/api/composer/ws/import-csv-dialog-model/upload/{runtimeId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseBody
   public HashMap<String, Object> getUploadFile(
      @RequestParam("uploads[]") MultipartFile mpf,
      @PathVariable("runtimeId") String runtimeId) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      processUploadCSV(mpf, runtimeId);
      HashMap<String, Object> res = process(runtimeId);
      CSVInfo csvinfo = (CSVInfo) res.get("model");
      String[] sheets = new String[0];

      if(csvinfo.getSheets() != null) {
         sheets = csvinfo.getSheets().split("__\\^__");
      }

      ImportCSVDialogModel model = ImportCSVDialogModel.builder()
         .delimiter(csvinfo.getDelimiter())
         .encodingSelected(csvinfo.getEncode())
         .fileType(csvinfo.getFileType().name())
         .firstRowCB(csvinfo.isFirstRowAsHeader())
         .removeQuotesCB(csvinfo.isRemoveQuotationMark())
         .sheetSelected(csvinfo.getSheet())
         .sheetsList(sheets)
         .unpivotCB(csvinfo.isUnpivot())
         .build();

      res.put("model", model);
      XEmbeddedTable preview = (XEmbeddedTable) res.get("previewTable");
      res.remove("previewTable");
      populateResultMap(res, preview, true);
      String limitMessage = getLimitMessage(Boolean.TRUE.equals(res.get("columnLimit")),
         Boolean.TRUE.equals(res.get("textExceedLimit")));

      if(limitMessage != null) {
         res.put("limitMessage", limitMessage);
      }

      return res;
   }

   @PostMapping("/api/composer/ws/import-csv-dialog-model/preview/{runtimeId}")
   @ResponseBody
   public HashMap<String, Object> getPreviewTable(
      @RequestBody ImportCSVDialogModel model,
      @PathVariable("runtimeId") String runtimeId) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      String rid = Tool.normalizeFileName(runtimeId);
      File csvTemp = FileSystemService.getInstance().getCacheFile(rid + "_csv");
      CSVInfo csvInfo = null;
      FileInputStream input = null;
      ImportCSVDialogModelValidator.Builder validator = ImportCSVDialogModelValidator.builder();

      try {
         if(!csvTemp.exists()) {
            String msg = Catalog.getCatalog().getString("Upload Timeout");
            validator.message(msg);
         }
         else {
            input = new FileInputStream(csvTemp);
            csvInfo = convertModelToCSVInfo(model, TextUtil.detectType(input));
         }
      }
      finally {
         IOUtils.closeQuietly(input);
      }

      HashMap<String, Object> resultMap = new HashMap<>();

      if(csvInfo != null) {
         int maxRow = Util.getOrganizationMaxRow();
         maxRow = csvInfo.isFirstRowAsHeader()  && maxRow != 0 ? maxRow + 1 : maxRow;
         Map<String, Object> result = loadData(csvTemp.getName(), csvInfo, new HashMap<>(),
            maxRow, Util.getOrganizationMaxColumn(), null, validator, model.detectType());
         XSwappableTable preview = null;

         if(result != null && result.containsKey("dataTable")) {
            preview = (XSwappableTable) result.get("dataTable");
         }

         if(preview == null) {
            preview = new XSwappableTable(1, true);
            preview.complete();
         }

         if(result != null && result.containsKey("prospectTypeMap")) {
            resultMap.put("prospectTypeMap", result.get("prospectTypeMap"));
         }

         populateResultMap(resultMap, new XEmbeddedTable(preview), csvInfo.isUnpivot());
      }

      String limitMessage = getLimitMessage(Boolean.TRUE.equals(resultMap.get("columnLimit")),
         Boolean.TRUE.equals(resultMap.get("textExceedLimit")));

      if(limitMessage != null) {
         resultMap.put("limitMessage", limitMessage);
      }

      resultMap.put("validator", validator.build());

      return resultMap;
   }

   @PutMapping("/api/composer/ws/import-csv-dialog-model/touch-file/{runtimeId}")
   @ResponseBody
   public void touchFile(@PathVariable("runtimeId") String runtimeId) throws IOException {
      String cdir = FileSystemService.getInstance().getCacheDirectory();
      fileCache.get(cdir + Tool.byteDecode(runtimeId) + "_csv");
   }

   @MessageMapping("/ws/dialog/import-csv-dialog-model")
   public void setImportCSVDialogModel(
      @Payload ImportCSVDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final String rid = Tool.normalizeFileName(getRuntimeId());
      final File csvTemp = FileSystemService.getInstance().getCacheFile(rid + "_csv");
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final CommandDispatcher dispatcher = commandDispatcher.detach();
      dispatcher.sendCommand(new ShowLoadingMaskCommand());
      dispatcher.sendCommand(new ForceNotCloseWorksheetCommand(true));
      final MessageAttributes messageContext = MessageContextHolder.getMessageAttributes();

      new GroupedThread(() -> {
         try {
            importCSV(model, csvTemp, rws, dispatcher, principal);
            MessageContextHolder.setMessageAttributes(messageContext);
            placeholderService.makeUndoable(rws, commandDispatcher, null);
         }
         catch(Exception ex) {
            // ignore exception if ws is disposed.
            if(rws.getAssetQuerySandbox() != null) {
               throw ex;
            }
         }

         if(model.mashUpData()) {
            rws.getWorksheet().getWorksheetInfo().setMashupMode();
         }
      }, principal).start();
   }

   private void importCSV(ImportCSVDialogModel model, File csvTemp, RuntimeWorksheet rws,
                          CommandDispatcher commandDispatcher, Principal principal)
   {
      try {
         EmbeddedTableAssembly assembly = null;
         Worksheet ws = rws.getWorksheet();
         boolean isNewTable = true;
         String dateFormatSpec = null;

         if(model.tableName() != null) {
            isNewTable = false;
            assembly = (EmbeddedTableAssembly) ws.getAssembly(model.tableName());

            if(assembly == null) {
               return;
            }

            dateFormatSpec = assembly.getProperty("default.dateFormatSpec");
         }

         InputStream input = null;
         CSVInfo csvInfo;

         try {
            input = new FileInputStream(csvTemp);
            csvInfo = convertModelToCSVInfo(model, TextUtil.detectType(input));
         }
         finally {
            IOUtils.closeQuietly(input);
         }

         final int priority = Thread.currentThread().getPriority();

         try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            final Map<Object, String> oldTypes =
               isNewTable ? new HashMap<>() : getOldTypes(assembly);

            Map<String, Object> result = loadData(csvTemp.getName(), csvInfo, oldTypes,
               getMaxRow(isNewTable, assembly, csvInfo.isFirstRowAsHeader()), getMaxCol(isNewTable, assembly),
               dateFormatSpec, null, model.detectType());

            final XSwappableTable table = result != null && result.containsKey("dataTable") ?
               (XSwappableTable) result.get("dataTable") : null;

            if(table == null) {
               return;
            }

            // set the new headers here
            Map<Integer, String> headerNames = model.headerNames();

            if(headerNames != null) {
               for(Integer colNum : headerNames.keySet()) {
                  table.setObject(0, colNum, headerNames.get(colNum));
               }
            }

            if(isNewTable) {
               final String name;

               if(model.newTableName() != null) {
                  name = AssetUtil.getNextName(ws, model.newTableName(), model.newTableName());
               }
               else {
                  name = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
               }

               assembly = setUpNewTable(ws, table, name);
               assembly.setEditMode(false);
               assembly.setLiveData(true);
               WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
               WorksheetEventUtil.focusAssembly(assembly.getName(), commandDispatcher);

            }
            else if(table.isExceedLimit() && !(assembly instanceof SnapshotEmbeddedTableAssembly))
            {
               MessageCommand cmd = new MessageCommand();
               cmd.setType(MessageCommand.Type.INFO);
               cmd.setMessage(Catalog.getCatalog().getString("common.worksheet.largeUpload"));
               cmd.setAssemblyName(assembly.getName());
               commandDispatcher.sendCommand(cmd);
            }

            // force column selection to be updated
            if(!isSameColumns(assembly.getColumnSelection(false), table)) {
               assembly.setColumnSelection(new ColumnSelection(), false);
               assembly.setAggregate(assembly.getAggregateInfo() != null &&
                  !assembly.getAggregateInfo().isEmpty());
            }

            XEmbeddedTable data = new XEmbeddedTable(table);
            assembly.setEmbeddedData(data);
            assembly.refreshColumnType(data);

            if(assembly instanceof SnapshotEmbeddedTableAssembly) {
               ((SnapshotEmbeddedTableAssembly) assembly).setOriginalSTable(table);
            }

            AssetEventUtil.initColumnSelection(rws, assembly);

            if(!isNewTable) {
               try {
                  WorksheetEventUtil.syncDataTypes(rws, assembly, commandDispatcher, principal, model.confirmed());
               }
               catch(MessageException | ConfirmException e) {
                  if(!model.confirmed()) {
                     MessageCommand cmd = new MessageCommand();
                     cmd.setMessage(e.getMessage());
                     cmd.setAssemblyName(assembly.getName());

                     if(e.getCause() instanceof CrossJoinException || e instanceof ConfirmException) {
                        rws.rollback();
                        cmd.setType(MessageCommand.Type.CONFIRM);
                        cmd.addEvent("/events/ws/dialog/import-csv-dialog-model", model);
                        commandDispatcher.sendCommand(cmd);
                        return;
                     }
                     else {
                        cmd.setType(MessageCommand.Type.ERROR);
                        commandDispatcher.sendCommand(cmd);
                     }
                  }
               }
            }

            WorksheetEventUtil.loadTableData(rws, assembly.getAbsoluteName(), true, true);
            AssetDataCache.removeCacheDependence(assembly);
            WorksheetEventUtil.refreshAssembly(
               rws, assembly.getAbsoluteName(), true, commandDispatcher, principal);
            WorksheetEventUtil.layout(rws, commandDispatcher);
            AssetEventUtil.refreshTableLastModified(ws, assembly.getAbsoluteName(), true);
         }
         finally {
            Thread.currentThread().setPriority(priority);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to import CSV file", e);
      }
      finally {
         commandDispatcher.sendCommand(new ForceNotCloseWorksheetCommand(false));
         commandDispatcher.sendCommand(new ClearLoadingCommand());
      }
   }

   // check if existing columns are same as newly imported data
   private static boolean isSameColumns(ColumnSelection cols, XTable table) {
      if(cols.getAttributeCount() != table.getColCount()) {
         return false;
      }

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         if(!Objects.equals(table.getObject(0, i), col.getDataRef().getName())) {
            return false;
         }
      }

      return true;
   }

   private EmbeddedTableAssembly setUpNewTable(Worksheet ws, XTable table, String name) {
      final SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, name);
      assembly.setPixelOffset(new Point(25, 25));
      AssetEventUtil.adjustAssemblyPosition(assembly, ws);
      final int twidth = table.getColCount();
      final int theight = 3 * AssetUtil.defw;
      assembly.setPixelSize(new Dimension(twidth * AssetUtil.defw, theight));
      ws.addAssembly(assembly);
      return assembly;
   }

   // Ripped from AssetWebHandler

   /**
    * Process upload CSV request.
    */
   private void processUploadCSV(MultipartFile mpf, String rid) throws Exception {
      rid = Tool.normalizeFileName(rid);
      FileSystemService fileSystemService = FileSystemService.getInstance();
      String cdir = fileSystemService.getCacheDirectory();
      File temp = fileSystemService.getCacheFile(rid + "_csv");
      String filePath = cdir + rid +"_csv";
      fileCache.put(filePath, temp, 120000L);

      try(FileOutputStream fos = new FileOutputStream(temp);
          InputStream minput = mpf.getInputStream())
      {
         Tool.fileCopy(minput, false, fos, false);
      }
   }

   /**
    * Process this worksheet event.
    *
    * @param runtimeId the specified runtime worksheet as the context.
    */
   private HashMap<String, Object> process(String runtimeId) throws Exception {
      HashMap<String, Object> res = new HashMap<>();

      String vid = runtimeId;
      vid = Tool.normalizeFileName(vid);
      String sheets = "";
      String firstSheet = null;

      String cdir = FileSystemService.getInstance().getCacheDirectory();
      File csvTemp = fileCache.get(cdir + vid + "_csv");

      TextFileType fileType;
      InputStream input = null;

      try {
         input = new FileInputStream(csvTemp);
         fileType = TextUtil.detectType(input);
      }
      finally {
         IOUtils.closeQuietly(input);
      }

      if(fileType == TextFileType.XLS || fileType == TextFileType.XLSX) {
         if(!isAboveMaxFileSize(csvTemp, null, false)) {
            try {
               String[] sheetNames = ExcelFileSupport.getInstance().getSheetNames(csvTemp);

               if(sheetNames.length > 0) {
                  firstSheet = sheetNames[0];
               }

               StringBuilder sheetsBuilder = new StringBuilder();

               for(int i = 0; i < sheetNames.length; i++) {
                  sheetsBuilder.append(i > 0 ? "__^__" : "").append(sheetNames[i]);
               }

               sheets = sheetsBuilder.toString();
            }
            catch(MessageException e) {
               throw e;
            }
            catch(Exception ex) {
               String msg = ex.getMessage();

               if(msg != null && !msg.isEmpty()) {
                  throw new MessageException("Failed to parse XLS: " + msg, ex);
               }
               else {
                  throw new MessageException("Failed to parse XLS: " + ex, ex);
               }
            }
         }
      }

      String encode = CSVInfo.getFileEncode(csvTemp);
      CSVInfo csvInfo = null;

      if(fileType == TextFileType.DELIMITED) {
         List<String> content;
         Reader reader = null;

         try {
            reader = new InputStreamReader(new FileInputStream(csvTemp), encode);
            reader = new BufferedReader(reader);
            content = readLines(reader, 10000);
         }
         finally {
            IOUtils.closeQuietly(reader);
         }

         String[] lines = content.toArray(new String[0]);
         csvInfo = CSVInfo.getCSVInfo(lines);
      }

      if(csvInfo == null) {
         csvInfo = new CSVInfo();
         csvInfo.setFirstRowAsHeader(true);
      }

      csvInfo.setFileType(fileType);
      csvInfo.setEncode(encode);

      if(sheets.length() > 0) {
         csvInfo.setSheets(sheets);

         if(csvInfo.getSheet() == null) {
            csvInfo.setSheet(firstSheet);
         }
      }

      int rowMax = csvInfo.isFirstRowAsHeader()  && Util.getOrganizationMaxRow() != 0 ?
         Util.getOrganizationMaxRow() + 1 : Util.getOrganizationMaxRow();
      Map<String, Object> result = loadData(csvTemp.getName(), csvInfo, new HashMap<>(),
         rowMax, Util.getOrganizationMaxCellSize(),null, null, true);
      XSwappableTable preview = null;

      if(result != null && result.containsKey("dataTable")) {
         preview = (XSwappableTable) result.get("dataTable");
      }

      if(preview == null) {
         preview = new XSwappableTable(1, true);
         preview.complete();
      }

      if(result != null && result.containsKey("prospectTypeMap")) {
         res.put("prospectTypeMap", result.get("prospectTypeMap"));
      }

      res.put("model", csvInfo);
      res.put("previewTable", new XEmbeddedTable(preview));

      if(result != null && result.containsKey("textExceedLimit")) {
         res.put("textExceedLimit", result.get("textExceedLimit"));
      }

      return res;
   }

   private static List<String> readLines(Reader input, int limit) throws IOException {
      BufferedReader reader = IOUtils.toBufferedReader(input);
      List<String> list = new ArrayList<>();
      int cnt = 0;

      for(String line = reader.readLine(); line != null && cnt < limit;
          line = reader.readLine(), cnt++)
      {
         list.add(line);
      }

      return list;
   }

   private CSVInfo convertModelToCSVInfo(ImportCSVDialogModel model, TextFileType type) {
      StringBuilder sheets = new StringBuilder();

      if(model.sheetsList() != null) {
         String[] sheetsList = model.sheetsList();

         for(int i = 0; i < Objects.requireNonNull(sheetsList).length - 1; i++) {
            sheets.append(sheetsList[i]).append("__^__");
         }
         if(sheetsList.length > 0) {
            sheets.append(sheetsList[sheetsList.length - 1]);
         }
      }

      char delimiter;

      if(model.delimiterTab()) {
         delimiter = '\t';
      }
      else {
         delimiter = model.delimiter();
      }

      CSVInfo info = new CSVInfo();
      info.setDelimiter(delimiter);
      info.setEncode(model.encodingSelected());
      info.setFileType(type);
      info.setFirstRowAsHeader(model.firstRowCB());
      info.setRemoveQuotationMark(model.removeQuotesCB());
      info.setSheet(model.sheetSelected());
      info.setSheets(sheets.toString());
      info.setUnpivot(model.unpivotCB());
      info.setHeaderColCount(model.headerCols());
      info.setIgnoreTypeColumns(model.ignoreTypeColumns());

      return info;
   }

   /**
    * Checks if a file exceeds the maximum allowed by the csv.import.max property.
    *
    * @param file File to check
    * @param validator Validator for the CSVDialog if desired.
    * @return <true>When file exceeds the maximum</true><false>otherwise</false>
    * @throws MessageException if no validator is supplied and the file exceeds the maximum size
    */
   private boolean isAboveMaxFileSize(File file, ImportCSVDialogModelValidator.Builder validator,
                                      boolean csv)
   {
      String excelImportMax = SreeEnv.getProperty("excel.import.max");
      String csvImportMax = SreeEnv.getProperty("csv.import.max");
      String csvmax = csv ? csvImportMax : excelImportMax;

      // csv max applied to excel if excel max is not defined
      if(csvmax == null && !csv) {
         csvmax = SreeEnv.getProperty("csv.import.max");
      }

      if(csvmax != null && file.length() > Long.parseLong(csvmax)) {
         long sizeM = Long.parseLong(csvmax) / 1024 / 1024;
         String msg = Catalog.getCatalog().getString("common.csvmax", sizeM + "M");

         // when using excel max for excel file, hint to the user to use CSV instead
         if(!csv && csvImportMax != null && excelImportMax != null) {
            long csvSizeM = Long.parseLong(csvImportMax) / 1024 / 1024;
            msg = Catalog.getCatalog().getString("common.excelmax", sizeM, csvSizeM);
         }

         if(validator == null) {
            throw new MessageException(msg, LogLevel.WARN, false);
         }
         else {
            validator.message(msg);
         }

         return true;
      }

      return false;
   }

   /**
    * Loads the data from the data file.
    *
    * @param fileName the data file name.
    * @param csvInfo  the import parameters.
    * @param oldTypes the old column data types.
    * @param rowLimit the maximum number of rows to load if greater than 0.
    * @param dateFormatSpec the format spec to use if column data is date type
    *
    * @return the data.
    */
   private Map<String, Object> loadData(String fileName, CSVInfo csvInfo,
                                    Map<Object, String> oldTypes,
                                    int rowLimit, int colLimit, String dateFormatSpec,
                                    ImportCSVDialogModelValidator.Builder validator,
                                    boolean detectType)
   {
      String encode = csvInfo.getEncode();
      String delim = csvInfo.getDelimiter() + "";
      boolean firstRow = csvInfo.isFirstRowAsHeader();
      boolean unpivot = csvInfo.isUnpivot();
      boolean removeQuote = csvInfo.isRemoveQuotationMark();
      int hcol = csvInfo.getHeaderColCount();
      String cdir = Tool.getCacheDirectory();
      File csvTemp;
      // data types
      List<String> types = new ArrayList<>();
      int nrow;
      int ncol = 0;
      Map<String, Object> result = new HashMap<>();
      XSwappableTable dataTable = null;
      Map<Integer, String> prospectTypeMap = new HashMap<>();

      csvTemp = fileCache.get(cdir + fileName);

      if(csvTemp == null || !csvTemp.exists()) {
         String msg = Catalog.getCatalog().getString("Upload Timeout");

         if(validator == null) {
            LOG.warn(msg);
         }
         else {
            validator.message(msg);
         }

         return null;
      }

      if(ExcelFileSupport.getInstance().isXLSB(csvTemp)) {
         throw new MessageException(
            Catalog.getCatalog().getString("common.xlsb.support"));
      }

      if(isAboveMaxFileSize(csvTemp, validator, csvInfo.getFileType() == TextFileType.DELIMITED)) {
         return null;
      }

      try {
         int rowCount = 0;
         final int typeRows = rowLimit == 0 ? 50000 : Math.max(rowLimit, 50000);
         rowLimit = rowLimit == 0 ? (firstRow ? 101 : 100) : rowLimit;
         DateParseInfo parseInfo = new DateParseInfo();
         int maxCellSize = Util.getOrganizationMaxCellSize();

         if(csvInfo.getIgnoreTypeColumns() != null) {
            parseInfo.setIgnoreTypeColumns(csvInfo.getIgnoreTypeColumns());
         }

         if(csvInfo.getFileType() == TextFileType.DELIMITED) {
            dataTable = CSVLoader.readCSV(csvTemp, encode, removeQuote, delim, firstRow, unpivot,
                                          oldTypes, types, detectType, dateFormatSpec, typeRows,
                                          rowLimit, colLimit, parseInfo);
            result.put("textExceedLimit", dataTable.isTextExceedLimit());
            ncol = dataTable.getColCount();
         }
         else {
            // double type data format
            Map<String, Format> fmtMap = new Object2ObjectOpenHashMap<>();
            dataTable = new XSwappableTable();
            InputStream ins = new FileInputStream(csvTemp);
            TextOutput toutput = new TextOutput();

            if(firstRow) {
               ExcelFileInfo headerInfo = new ExcelFileInfo();
               headerInfo.setSheet(csvInfo.getSheet());
               headerInfo.setStartRow(0);
               headerInfo.setEndRow(0);
               headerInfo.setStartColumn(0);
               headerInfo.setEndColumn(-1);
               toutput.setHeaderInfo(headerInfo);
            }

            ExcelFileInfo bodyInfo = new ExcelFileInfo();
            bodyInfo.setSheet(csvInfo.getSheet());
            bodyInfo.setStartRow(firstRow ? 1 : 0);
            bodyInfo.setEndRow(-1);
            bodyInfo.setStartColumn(0);
            bodyInfo.setEndColumn(-1);
            toutput.setBodyInfo(bodyInfo);
            toutput.setAttribute("unpivot", unpivot);

            ExcelFileReader excelReader;

            if(csvInfo.getFileType() == TextFileType.XLS) {
               excelReader = ExcelFileSupport.getInstance().createXLSReader();
            }
            else {
               excelReader = ExcelFileSupport.getInstance().createXLSXReader();
            }

            XTypeNode meta = excelReader.importHeader(ins, csvInfo.getEncode(), toutput, typeRows,
                                                      colLimit, parseInfo);
            boolean mixedTypeColumns = excelReader.isMixedTypeColumns();
            result.put("textExceedLimit", excelReader.isTextExceedLimit());

            if(excelReader.isXLSX()) {
               dataTable.setExceedLimit(excelReader.isExceedLimit());
            }

            ins.close();

            if(firstRow) {
               ((ExcelFileInfo) toutput.getHeaderInfo()).setEndColumn(meta.getChildCount() - 1);
            }

            ((ExcelFileInfo) toutput.getBodyInfo()).setEndColumn(meta.getChildCount() - 1);

            XSelection spec = new XSelection();
            int[] sel = new int[meta.getChildCount()];

            for(int i = 0; i < meta.getChildCount(); i++) {
               XTypeNode colMeta = (XTypeNode) meta.getChild(i);
               spec.addColumn(colMeta.getName());
               spec.setConversion(colMeta.getName(), colMeta.getType(),
                                  (String) colMeta.getAttribute("format"));
               spec.setFormatFixed(colMeta.getName(), true);
               sel[i] = i;
            }

            toutput.setTableSpec(spec);
            toutput.setSelectedCols(sel);

            ins = new FileInputStream(csvTemp);

            XTableNode excelData = excelReader.read(
               ins, csvInfo.getEncode(), null, toutput, rowLimit,
               meta.getChildCount(), firstRow, null, false, parseInfo);

            boolean found = false;

            // optimization, reuse swap table if possible
            if(excelData instanceof XTableTableNode && firstRow && !unpivot && !mixedTypeColumns) {
               XTable xtable = ((XTableTableNode) excelData).getXTable();

               if(xtable instanceof XSwappableTable) {
                  dataTable = (XSwappableTable) xtable;
                  ncol = dataTable.getColCount();
                  found = true;
               }
            }

            if(!found) {
               try {
                  // copy the type information from datasource so if the type is
                  // changed, it will be reflected in the query (the type
                  // setting on query has been removed from the gui)
                  spec = updateType(spec, toutput);
                  excelData = spec.select(excelData);

                  ncol = excelData.getColCount();
                  Object[] header = new Object[ncol];
                  Object[] rdata;

                  for(int i = 0; i < header.length; i++) {
                     header[i] = excelData.getName(i);
                     types.add(((XTypeNode) meta.getChild(i)).getType());
                  }

                  AssetUtil.initDefaultTypes(header, oldTypes, types, true);

                  XTableColumnCreator[] creators = new XTableColumnCreator[ncol];

                  for(int i = 0; i < ncol; i++) {
                     // string is the default and may contain other type values, so just
                     // use object
                     creators[i] = (types.get(i) == null || types.get(i).equals(XSchema.STRING))
                        ? XObjectColumn.getCreator()
                        : XObjectColumn.getCreator(Tool.getDataClass(types.get(i)));
                     creators[i].setDynamic(false);
                  }

                  dataTable.init(creators);

                  if(!unpivot) {
                     dataTable.addRow(header);
                  }

                  prospectTypeMap = parseInfo.getProspectTypeMap();

                  if(prospectTypeMap.size() != 0) {
                     dataTable.complete();
                     result.put("dataTable", dataTable);
                     result.put("prospectTypeMap", prospectTypeMap);
                     return result;
                  }

                  List<Object[]> emptyRows = new ArrayList<>();

                  while(excelData.next()) {
                     rdata = new Object[ncol];

                     for(int i = 0; i < ncol; i++) {
                        rdata[i] =
                           CSVLoader.parseData(excelData.getObject(i), types, i, dateFormatSpec);
                     }

                     CSVLoader.parseRow(rdata, types, header, fmtMap, null, maxCellSize);

                     if(isEmptyRow(rdata)) {
                        emptyRows.add(rdata);

                        if(emptyRows.size() >= 10) {
                           break;
                        }
                     }
                     else {
                        emptyRows.add(rdata);

                        for(Object[] row : emptyRows) {
                           dataTable.addRow(row);

                           if(rowLimit > 0 && ++rowCount == rowLimit) {
                              dataTable.setExceedLimit(true);
                              emptyRows.clear();
                              break;
                           }
                        }

                        emptyRows.clear();
                     }
                  }

                  if(emptyRows.size() < 10) {
                     for(Object[] row : emptyRows) {
                        dataTable.addRow(row);

                        if(rowLimit > 0 && ++rowCount == rowLimit) {
                           dataTable.setExceedLimit(true);
                           break;
                        }
                     }
                  }
               }
               finally {
                  excelData.close();
               }

               dataTable.complete();
            }
         }

         prospectTypeMap = parseInfo.getProspectTypeMap();
      }
      catch(IllegalArgumentException illegalArgumentException) {
         if("Sheet not found".equals(illegalArgumentException.getMessage())) {
            LOG.warn("The sheet was not found in the data file");
         }
         else {
            LOG.warn("Failed to import data file", illegalArgumentException);
         }
      }
      catch(Exception e) {
         if(e instanceof MessageException &&
            "Incompatible Excel Metadata".equals(e.getMessage()))
         {
            String msg = Catalog.getCatalog().getString("composer.ws.import.incompatibleExcelFile");

            if(validator == null) {
               LOG.warn(msg);
            }
            else {
               validator.message(msg);
            }

            return null;
         }
         else {
            LOG.warn("Failed to import data file: " + csvTemp, e);
         }
      }

      assert dataTable != null;
      nrow = dataTable.getRowCount();

      if(!(ncol > 0 && nrow > 0)) {
         String msg = Catalog.getCatalog().getString("Please check the csv file content");

         if(csvInfo.getSheet() != null &&
            !Objects.equals(csvInfo.getSheet(), csvInfo.getSheets()))
         {
            msg = Catalog.getCatalog().getString("viewer.worksheet.import.emptySheet",
                                                 csvInfo.getSheet());
         }

         if(validator == null) {
            LOG.warn(msg);
         }
         else {
            validator.message(msg);
         }

         return null;
      }

      if(unpivot) {
         XSwappableTable oDataTable = dataTable;
         dataTable = AssetUtil.unpivot(dataTable, hcol);

         if(dataTable != oDataTable) {
            prospectTypeMap.clear();
            oDataTable.dispose();
         }
      }

      result.put("dataTable", dataTable);
      result.put("prospectTypeMap", prospectTypeMap);

      return result;
   }

   private boolean isEmptyRow(Object[] rdata) {
      return rdata == null || Arrays.stream(rdata).allMatch(c -> c == null);
   }

   /**
    * Get EmbeddedTableAssembly's current data types of of each column.
    * @param table EmbeddedTableAssembly.
    * @return Map which contains data types of of each column.
    */
   private static Map<Object, String> getOldTypes(EmbeddedTableAssembly table) {
      Map<Object, String> oldTypes = new HashMap<>();

      try {
         XTable tableData = table.getEmbeddedData();

         for(int i = 0; i < tableData.getColCount(); i++) {
            oldTypes.put(tableData.getObject(0, i), Tool.getDataType(tableData.getColType(i)));
         }
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.debug("Failed to get column types: " + ex, ex);
         }
      }

      return oldTypes;
   }

   /**
    * If exist some columns which are seems to date/time/datetime type, but some values
    * are not convertible to these types, give user options to decide if CANCEL and
    * re-upload a cleaned file. Or you can CONTINUE to keep these columns as string type.
    */
   private static boolean populateMixedTypeInfo(HashMap<String, Object> resultMap,
                                                XEmbeddedTable preview)
   {
      if(resultMap == null || !resultMap.containsKey("prospectTypeMap")) {
         return false;
      }

      Map<Integer, String> prospectTypeMap = (Map<Integer, String>) resultMap.get("prospectTypeMap");
      Map<String, List<Integer>> map = new HashMap<>();
      List<Integer> columns = new ArrayList<>();

      if(prospectTypeMap.isEmpty()) {
         return false;
      }

      for(Map.Entry<Integer, String> entry : prospectTypeMap.entrySet()) {
         Integer col = entry.getKey();

         if(col < preview.getColCount()) {
            String type = entry.getValue();

            if(!map.containsKey(type)) {
               map.put(type, new ArrayList<>());
            }

            map.get(type).add(col);
            columns.add(col);
         }
      }

      StringBuffer buffer = new StringBuffer();
      int count = 0;
      Catalog catalog = Catalog.getCatalog();
      buffer.append(catalog.getString("Columns") + " ");

      for(Map.Entry<String, List<Integer>> entry : map.entrySet()) {
         List<Integer> list = entry.getValue();
         list.sort(Comparator.naturalOrder());

         StringBuffer buffer0 = new StringBuffer();

         for(int i = 0; i < list.size(); i++) {
            buffer0.append("\"");
            buffer0.append(preview.getObject(0, list.get(i)));
            buffer0.append("\"");

            if(i < list.size() - 1) {
               buffer0.append(", ");
            }
         }

         buffer.append(catalog.getString(
            "common.worksheet.prospectTypeWarn.part0", buffer0.toString(), entry.getKey()));

         if(count < map.keySet().size() - 1) {
            buffer.append(", ");
         }

         count++;
      }

      buffer.append(". ");
      buffer.append(catalog.getString("common.worksheet.prospectTypeWarn.part1"));

      resultMap.put("warnMsg", buffer.toString());
      resultMap.put("mixedIndexes", columns);

      return true;
   }

   private static void populateResultMap(HashMap<String, Object> resultMap,
                                         XEmbeddedTable preview, boolean checkColumnlimit)
   {
      if(preview != null) {
         int start = 0;
         int end = Math.min(preview.getRowCount(), 500);

         if(Util.getOrganizationMaxRow() > 0) {
            end = Math.min(end, Util.getOrganizationMaxRow() + 1);
         }

         int colCount = preview.getColCount();

         if(checkColumnlimit && Util.getOrganizationMaxColumn() != 0 &&
            colCount > Util.getOrganizationMaxColumn())
         {
            resultMap.put("columnLimit", true);
         }

         if(Util.getOrganizationMaxColumn() > 0) {
            colCount = Math.min(colCount, Util.getOrganizationMaxColumn());
         }

         BaseTableCellModel[][] tableCells = new BaseTableCellModel[end][colCount];

         if(populateMixedTypeInfo(resultMap, preview)) {
            return;
         }

         for(int row = start; row < end; row++) {
            for(int col = 0; col < colCount; col++) {
               tableCells[row][col] = BaseTableCellModel.createSimpleCell(preview, row, col);
            }
         }

         resultMap.put("previewTable", tableCells);
      }
   }

   private final DataCache<String, File> fileCache =
      new DataCache<>(100, 120000L) {
         @Override
         protected boolean demote(CacheEntry<String, File> entry) {
            File value = entry.getData();

            if(value != null) {
               Tool.deleteFile(value);
            }

            return super.demote(entry);
         }
      };

   private int getMaxRow(boolean isNewTable, EmbeddedTableAssembly assembly, boolean isHeader) {
      if(Util.getOrganizationMaxRow() > 0) {
         return isHeader ? Util.getOrganizationMaxRow() + 1 : Util.getOrganizationMaxRow();
      }

      return isNewTable || assembly instanceof SnapshotEmbeddedTableAssembly ?
         -1 : ROW_LIMIT;
   }

   private int getMaxCol(boolean isNewTable, EmbeddedTableAssembly assembly) {
      if(Util.getOrganizationMaxColumn() > 0) {
         return Util.getOrganizationMaxColumn();
      }

      return isNewTable || assembly instanceof SnapshotEmbeddedTableAssembly ?
         -1 : COL_LIMIT;
   }

   /**
    * Updates the type/format information in the selection from the type tree.
    */
   private static XSelection updateType(XSelection spec, TextOutput type) {
      if(type == null) {
         return spec;
      }

      XSelection ospec = type.getTableSpec();
      spec = (XSelection) spec.clone();

      for(int i = 0; i < spec.getColumnCount(); i++) {
         String name = spec.getColumn(i);
         int idx = ospec == null ? -1 : ospec.indexOf(name);

         if(idx >= 0) {
            spec.setType(name, ospec.getType(name));
            spec.setFormat(name, ospec.getFormat(name));
         }
      }

      return spec;
   }

   private final PlaceholderService placeholderService;

   private static final Logger LOG = LoggerFactory.getLogger(ImportCSVDialogController.class);
}
