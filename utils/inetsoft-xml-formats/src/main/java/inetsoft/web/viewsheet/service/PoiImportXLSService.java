/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSUtil;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.controller.table.BaseTableLoadDataController;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PoiImportXLSService implements ImportXLSService {
   static {
      SchemaTypeSystemImpl.METADATA_PACKAGE_GEN = "inetsoft/xml/org/apache/xmlbeans/metadata";
   }

   @Override
   public void updateViewsheet(File excelFile, String type, RuntimeViewsheet rvs, String linkUri,
                               CommandDispatcher dispatcher, PlaceholderService placeholderService,
                               Catalog catalog, List<String> assemblies, Set<String> notInRange)
      throws Exception
   {
      Workbook wb = initWorkbook(excelFile, type);
      Map<String, Name> sheetNames;
      Map<String, Name> bSheetNames;

      String sheetName = catalog.getString("Current View");
      Sheet sheet = null;
      Sheet bSheet = null;

      if(wb != null) {
         sheet = wb.getSheet(sheetName);
         bSheet = wb.getSheet(sheetName + "_Backup");
      }

      if(sheet == null) {
         throw new FileNotFoundException();
      }

      sheetNames = PoiExcelVSUtil.getSheetNames(sheet);
      bSheetNames = bSheet == null ? null : PoiExcelVSUtil.getSheetNames(bSheet);

      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      updateViewsheet(rvs, vs, box, assemblies, linkUri, dispatcher, placeholderService,
                      sheet, bSheet, sheetNames, bSheetNames, notInRange);
   }

   /**
    * Update whole viewsheet.
    */
   private void updateViewsheet(RuntimeViewsheet rvs, Viewsheet vs, ViewsheetSandbox box,
                                List<String> assemblies, String linkUri,
                                CommandDispatcher dispatcher, PlaceholderService placeholderService,
                                Sheet sheet, Sheet bSheet, Map<String, Name> sheetNames,
                                Map<String, Name> bSheetNames, Set<String> notInRange)
      throws Exception
   {
      Assembly[] vsAssemblies = vs.getAssemblies();
      boolean reset = false;

      for(Assembly assembly : vsAssemblies) {
         if(assembly instanceof Viewsheet) {
            updateViewsheet(rvs, (Viewsheet) assembly, box, assemblies, linkUri, dispatcher,
                            placeholderService, sheet, bSheet, sheetNames, bSheetNames, notInRange);
            continue;
         }

         VSAssembly vsAssembly = (VSAssembly) assembly;
         String name = vsAssembly.getAbsoluteName();

         VSAssemblyInfo vsInfo = vsAssembly.getVSAssemblyInfo();

         if(!vsInfo.isVisible(true)) {
            continue;
         }

         if(!isAssemblyExist(vsAssembly, sheetNames)) {
            if(!VSUtil.isInTab(vsAssembly) ||
               VSUtil.isVisibleInTab(vsAssembly, vs, rvs != null && rvs.isRuntime())) {
               assemblies.add(name);
            }

            continue;
         }

         if(vsAssembly instanceof TableVSAssembly) {
            TableVSAssemblyInfo tinfo =
               (TableVSAssemblyInfo) vsAssembly.getVSAssemblyInfo();

            FormTableLens table = box.getFormTableLens(name);

            if(table != null) {
               List<FormTableRow> data = getTableData((TableVSAssembly) vsAssembly, sheet,
                                                      bSheet, sheetNames, bSheetNames, notInRange, table, tinfo);

               if(notInRange.contains(name)) {
                  continue;
               }

               // reload table data by the excel data
               table.init(data);
               table.addDeleteRows(
                  getDeleteRows((TableVSAssembly) vsAssembly, bSheet, bSheetNames, table));
               AttributeTableLens atbl = new AttributeTableLens(table);
               atbl.setRowBorderColor(VSAssemblyInfo.DEFAULT_BORDER_COLOR);
               atbl.setColBorderColor(VSAssemblyInfo.DEFAULT_BORDER_COLOR);
               TableLens temp = atbl;

               String sname = tinfo.getTableStyle();
               inetsoft.report.style.TableStyle style = VSUtil.getTableStyle(sname);

               if(style != null) {
                  style = (TableStyle) style.clone();
                  style.setTable(temp);
                  temp = style;
               }

               FormatInfo finfo = vsAssembly.getFormatInfo();

               if(finfo != null && !finfo.isEmpty()) {
                  temp = new VSFormatTableLens(box, name, temp, true);
               }

               TableHighlightAttr highlight = tinfo.getHighlightAttr();

               if(highlight != null) {
                  highlight.replaceVariables(box.getAllVariables());
                  highlight.setConditionTable(atbl);
                  temp = highlight.createFilter(temp);
               }

               VSAssemblyInfo info = VSEventUtil.getAssemblyInfo(rvs, vsAssembly);
               BaseTableLoadDataController.loadTableData(rvs, name, 0, 0,
                                                         table.getRowCount(), linkUri,
                                                         dispatcher);
               placeholderService.refreshVSAssembly(rvs, vsAssembly.getAbsoluteName(), dispatcher);

               // TODO Refresh scripts
//               for(int r = 1; r < table.getRowCount(); r ++) {
//                  if(table.row(r).getRowState() != FormTableRow.CHANGED &&
//                     table.row(r).getRowState() != FormTableRow.ADDED)
//                  {
//                     continue;
//                  }
//
//                  for(int l = 0; l < table.getColCount(); l++) {
//                     EditFormTableEvent.TableScriptEvent event =
//                        new EditFormTableEvent().new TableScriptEvent(name, r, l);
//                     box.dispatchEvent(event, command, rvs);
//                  }
//               }
            }
         }
      }

      if(reset) {
         placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
      }
   }

   /**
    * Init workbook by the file.
    */
   private Workbook initWorkbook(File excelFile, String fileType) {
      Workbook wb = null;
      InputStream ifile;

      try {
         ifile = new FileInputStream(excelFile);
         ifile = new BufferedInputStream(ifile);

         if("xls".equals(fileType) || "xlsx".equals(fileType)) {
            wb = WorkbookFactory.create(ifile);
         }
         else {
            throw new Exception("Not a Excel file.");
         }
      }
      catch(Exception ex) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to initialize Excel workbook", ex);
         }
         else {
            LOG.warn("Failed to initialize Excel workbook: {}", ex.getMessage());
         }
      }

      return wb;
   }

   /**
    * Check the assembly whether exists in current excel.
    */
   private boolean isAssemblyExist(VSAssembly assembly, Map<String, Name> sheetNames) {
      if(sheetNames == null) {
         return false;
      }

      if(assembly instanceof TableVSAssembly) {
         String assemblyName = getValidCellName(assembly);
         Name name = sheetNames.get(assemblyName);

         if(name == null && (assembly instanceof TableVSAssembly)) {
            name = sheetNames.get(assemblyName + "_Header");

            if(name == null) {
               Set<String> set = sheetNames.keySet();

               for(String keyName : set) {
                  if(keyName.matches(assemblyName + "\\.\\d*_TR")) {
                     return true;
                  }
               }
            }
         }

         if(name == null || name.isDeleted()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get related table data from the excel for target table.
    * @param assembly the specified table assembly.
    * @param sheet the edit excel sheet.
    * @param bSheet the backup sheet of excel.
    * @param tableLens the spcified form table.
    */
   private List<FormTableRow> getTableData(TableVSAssembly assembly, Sheet sheet, Sheet bSheet,
                                           Map<String, Name> sheetNames,
                                           Map<String, Name> bSheetNames,
                                           Set<String> notInRange,
                                           FormTableLens tableLens,
                                           TableVSAssemblyInfo tinfo)
   {
      String tName = getValidCellName(assembly);
      ColumnOption[] options = PoiExcelVSUtil.getTableOption(tinfo);
      Name tableName = sheetNames.get(tName);
      Name tableHeaderName = sheetNames.get(tName + "_Header");

      Cell tableTitleCell = tableName == null ?
         null : PoiExcelVSUtil.getCellByName(tableName, sheet);
      Cell tableHeaderCell = tableHeaderName == null ?
         null : PoiExcelVSUtil.getCellByName(tableHeaderName, sheet);
      int colCount = tableLens.getColCount();
      int firstRow = tableHeaderCell != null ? tableHeaderCell.getRowIndex() :
         tableTitleCell != null ? tableTitleCell.getRowIndex() + 1 : -1;
      int firstCol = tableTitleCell != null ? tableTitleCell.getColumnIndex() :
         tableHeaderCell != null ? tableHeaderCell.getColumnIndex() : -1;

      if(firstRow == -1 || firstCol == -1) {
         Name rowName = getTableFirstRowName(tName, sheetNames);

         if(rowName == null) {
            return null;
         }

         Cell rowCell = PoiExcelVSUtil.getCellByName(rowName, sheet);
         firstRow = rowCell.getRowIndex();
         firstCol = rowCell.getColumnIndex();
      }

      boolean end = false;

      boolean noHeader = tableHeaderName == null;
      int rowIdx = noHeader ? 0 : 1;
      int rowRefOffset = 0;

      List<FormTableRow> tableData = new ArrayList<>();

      if(colCount == 0) {
         return tableData;
      }

      // get table header from tablelens
      Object[] titleData = new Object[colCount];

      for(int k = 0; k < colCount; k++) {
         titleData[k] = tableLens.getObject(0, k);
      }

      FormTableRow titleRow = new FormTableRow(titleData, 0);
      titleRow.setRowState(FormTableRow.OLD);
      tableData.add(titleRow);

      int[] realColIdx = getRealColIdx(firstRow, firstCol, colCount, sheet);

      while(!end) {
         if(isTableEnd(firstRow, firstCol, rowIdx, colCount, sheet)) {
            end = true;
            continue;
         }

         if(isBlank(firstRow, firstCol, rowIdx, colCount, sheet)) {
            rowIdx++;
            rowRefOffset ++;
            continue;
         }

         Cell firstColCell = getCell(firstRow + rowIdx, firstCol, sheet);
         Name name = PoiExcelVSUtil.getCellName(firstColCell, sheet);
         Object[] data = new Object[colCount];

         for(int i = 0; i < colCount; i++) {
            Cell rowCell = getCell(firstRow + rowIdx, realColIdx[i], sheet);
            Object cellVal = PoiExcelVSUtil.getCellValue(rowCell);

            if(rowIdx > 0) {
               Class<?> cls = getColumnType(tableLens, i);

               if(cls == null && Tool.isDateClass(cellVal.getClass())) {
                  CellStyle style = rowCell.getCellStyle();
                  String pattern = style.getDataFormatString();
                  // if the date format is "yyyy-mm-dd" in excel,
                  // but we will get "yyyy\-mm\-dd" formt form the cell use POI,
                  // so here we remove the backslash.
                  pattern = Tool.replaceAll(pattern, "\\", "");

                  //fix bug1320054253476
                  if(pattern.contains("-mm")) {
                     pattern = pattern.replace("-mm", "-MM");
                  }
                  else if(pattern.contains("mm-")) {
                     pattern = pattern.replace("mm-", "MM-");
                  }
                  else if(pattern.contains("/mm")) {
                     pattern = pattern.replace("/mm", "/MM");
                  }
                  else if(pattern.contains("mm/")) {
                     pattern = pattern.replace("mm/", "MM/");
                  }

                  SimpleDateFormat sformat = Tool.createDateFormat(pattern);
                  cellVal = sformat.format(cellVal);
               }
               else {
                  cls = Tool.isDateClass(cls) ? cls :
                     cellVal != null && Tool.isDateClass(cellVal.getClass()) ?
                        cellVal.getClass() : cls;

                  Object ncellVal = Tool.getData(cls, cellVal);
                  cellVal = ncellVal != null ? ncellVal :
                     (cellVal != null && !Tool.NULL.equals(cellVal)) ?
                        cellVal : null;
               }

               // POI has a bug that HSSFDateUtil.isCellDateFormatted can not
               // the return correct result always, so the celVal may be get
               // incorrect double value by getCellValue when it's type is date.
               // we need to do something to deal with the situation.
               if(Tool.isDateClass(cls) && cellVal == null) {
                  cellVal = PoiExcelVSUtil.getCellValue(rowCell);

                  if(cellVal instanceof Number) {
                     cellVal = Tool.getData(cls,
                                            DateUtil.getJavaDate((Double) cellVal));
                  }
               }
            }

            if(cellVal instanceof Double && Double.isNaN((Double) cellVal)) {
               cellVal = null;
            }

            data[i] = cellVal;

            if(i < options.length && options[i].isForm() && !options[i].validate(data[i])) {
               notInRange.add(assembly.getAbsoluteName());
            }
         }

         int state = -1;
         Cell stateCell = getCell(firstColCell.getRowIndex() - rowRefOffset,
                                  firstCol, sheet);
         Name stateName = PoiExcelVSUtil.getCellName(stateCell, sheet);

         if(stateName != null) {
            String[] tokens = stateName.getNameName().split("_");

            if(tokens.length == 4) {
               state = Integer.parseInt(tokens[2]);
            }
         }

         FormTableRow ftr;
         Name bName = null;

         if(name != null) {
            // import same sheet shouldn't add new cell.(This "_1" is for _backup sheet)
            String nname = name.getNameName() + "_1";
            bName = bSheetNames.get(nname);
            bSheetNames.remove(nname);
         }

         // no cell name, must be added row
         if(name == null || bName == null || state == FormTableRow.ADDED) {
            ftr = new FormTableRow(data, -1);
            ftr.setRowState(FormTableRow.ADDED);
            tableData.add(ftr);
            rowIdx++;

            continue;
         }
         // has cell name, compare with the backup table to check the row
         // whether is changed
         else {
            int oidx = PoiExcelVSUtil.getRowIndex(name);

            // now table not include this row for input changed
            if(!tableLens.moreRows(oidx)) {
               oidx = -1;
            }

            ftr = new FormTableRow(data, oidx);
            Cell rCell = PoiExcelVSUtil.getCellByName(bName, bSheet);

            // If need to compare cell value in sheet, should use real column index in sheet instead of
            // column count of tablelens to verify. Because the cell maybe is merge span in sheet. (57280)
            for(int i = 0; i < realColIdx.length; i++) {
               Cell cCell = getCell(firstRow + rowIdx, realColIdx[i], sheet);
               Cell bCell = getCell(rCell.getRowIndex(), realColIdx[i], bSheet);

               if(!PoiExcelVSUtil.compareCell(cCell, bCell) || state == FormTableRow.CHANGED) {
                  ftr.setRowState(FormTableRow.CHANGED);
                  break;
               }
            }
         }

         tableData.add(ftr);
         rowIdx++;
      }

      return tableData;
   }

   /**
    * Get deleted table rows.
    * @param assembly the specified table assembly.
    * @param bSheet the backup sheet.
    * @param tableLens the specified table.
    */
   private List<FormTableRow> getDeleteRows(TableVSAssembly assembly, Sheet bSheet,
                                            Map<String, Name> bSheetNames,
                                            FormTableLens tableLens)
   {
      String name = getValidCellName(assembly);
      int colCount = tableLens.getColCount();
      List<FormTableRow> delRows = new ArrayList<>();
      Set<String> keySet = bSheetNames.keySet();

      for(String str : keySet) {
         if(str.startsWith(name) && str.endsWith("_TR")) {
            FormTableRow delRow = new FormTableRow(colCount);
            Name delName = bSheetNames.get(str);
            Cell rCell = PoiExcelVSUtil.getCellByName(delName, bSheet);
            int rowIdx = rCell.getRowIndex();
            int colIdx = rCell.getColumnIndex();

            for(int i = 0; i < colCount; i++) {
               Cell cell = getCell(rowIdx, colIdx + i, bSheet);
               delRow.set(i, PoiExcelVSUtil.getCellValue(cell));
            }

            delRow.setRowState(FormTableRow.DELETED);
            delRows.add(delRow);
         }
      }

      return delRows;
   }

   /**
    *  Poi not allowed cellName contains ".",  so "." was changed to "\"
    *  in ExcelVSUtil.setCellName function, and "\" will be "_" by Tool.toIdentifier function,
    *  so when import the excel, we need to convert "." to "_" for embedded assembly name to
    *  match the cell names.
    */
   private String getValidCellName(VSAssembly assembly) {
      if(assembly == null) {
         return null;
      }

      String assemblyName = assembly.getAbsoluteName();

      if(assembly.isEmbedded()) {
         assemblyName = assemblyName.replace(".", "_");
      }

      return assemblyName;
   }

   /**
    * If the table has no title and header, need find out the first content row
    * in Excel.
    */
   private Name getTableFirstRowName(String name, Map<String, Name> sheetNames) {
      Name rowName = null;
      int min = Integer.MAX_VALUE;
      Pattern p = Pattern.compile(name + "\\.(\\d*)_TR");

      for(Map.Entry<String, Name> e : sheetNames.entrySet()) {
         String keyName = e.getKey();
         Matcher m = p.matcher(keyName);

         if(m.find()) {
            int d = Integer.parseInt(m.group(1));

            if(d < min) {
               min = d;
               rowName  = e.getValue();
            }
         }
      }

      return rowName;
   }

   /**
    * Get the specified cell's next column.
    */
   private int[] getRealColIdx(int rowIdx, int firstColIdx, int colCount,
                               Sheet sheet)
   {
      int[] realColIdx = new int[colCount];
      realColIdx[0] = firstColIdx;

      for(int i = 0 ; i < colCount; i++) {
         if(i == colCount - 1) {
            break;
         }

         Cell cell = getCell(rowIdx, realColIdx[i], sheet);
         CellRangeAddress addr = PoiExcelVSUtil.getCellMergeRegion(cell);

         if(addr == null) {
            realColIdx[i + 1] = realColIdx[i] + 1;
            continue;
         }

         realColIdx[i + 1] = addr.getLastColumn() + 1;
      }

      return realColIdx;
   }

   /**
    * Return <tt>true</tt> if read the row the the rowIdx specified is last row,
    * otherwise, <tt>false</tt>
    */
   private boolean isTableEnd(int firstRow, int firstCol, int rowIdx,
                              int colCount, Sheet sheet)
   {
      boolean end = false;

      if(sheet.getRow(firstRow + rowIdx) == null ||
         getCell(firstRow + rowIdx, firstCol, sheet) == null)
      {
         end = true;
      }
      else {
         boolean blank = true;

         // if table row cells all blank, then the table end
         for(int i = 0; i < colCount; i++) {
            Cell cell = getCell(firstRow + rowIdx, firstCol + i, sheet);
            Name name = null;

            if(i == 0) {
               name = PoiExcelVSUtil.getCellName(cell, sheet);
            }

            if(!"".equals(PoiExcelVSUtil.getCellValue(cell)) || name != null) {
               blank = false;
               break;
            }
         }

         // if row is merged to another row, check the first row in the merged region for data
         if(blank) {
            Optional<CellRangeAddress> mergedCell = sheet.getMergedRegions().stream()
               .filter(region -> region.containsRow(firstRow + rowIdx))
               .findFirst();

            if(mergedCell.isPresent()) {
               int dataRowIndex = mergedCell.get().getFirstRow() - firstRow;

               if(dataRowIndex != rowIdx) {
                  blank = isTableEnd(firstRow, firstCol, dataRowIndex, colCount, sheet);
               }
            }
         }

         end = blank || end;
      }

      return end;
   }

   private boolean isBlank(int firstRow, int firstCol, int rowIdx, int colCount, Sheet sheet) {
      boolean blank = true;

      //check all cells in the row for their values
      for(int i = 0; i < colCount; i++) {
         Cell cell = getCell(firstRow + rowIdx, firstCol + i, sheet);

         if(!"".equals(PoiExcelVSUtil.getCellValue(cell))) {
            blank = false;
            break;
         }
      }

      return blank;
   }

   /**
    * Get cell of speified sheet by the specified row index and column index.
    * @param rowIdx the row index of cell.
    * @param colIdx the column index of cell.
    * @param sheet the specified sheet.
    */
   private Cell getCell(int rowIdx, int colIdx, Sheet sheet) {
      Row row = PoiExcelVSUtil.getRow(rowIdx, sheet);

      return PoiExcelVSUtil.getCell(colIdx, row);
   }

   /**
    * Get table column type by data.
    * @param tableLens the specified tablelens.
    * @param colIdx the specified column index.
    */
   private Class<?> getColumnType(FormTableLens tableLens, int colIdx) {
      Class<?> cls = null;

      for(int i = 1; i < tableLens.getRowCount(); i++) {
         if(tableLens.getObject(i, colIdx) != null) {
            cls = tableLens.getObject(i, colIdx).getClass();
            break;
         }
      }

      return cls;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PoiImportXLSService.class);
}
