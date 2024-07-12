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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.excel.SheetMaxRowsException;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ListInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.OutputStream;
import java.util.*;

public class OfflineExcelVSExporter extends PoiExcelVSExporter {
   /**
    * Checkbox value selected.
    */
   public static final String CHECKBOX_SELECTED = "\u221A";

   /**
    * Checkbox value not selected.
    */
   public static final String CHECKBOX_UNSELECTED = "\u25A1";

   /**
    * Construtor
    */
   public OfflineExcelVSExporter(ExcelContext ec, OutputStream stream) {
      super(ec, stream);
   }

   /**
    * Specify the size and cell size of sheet.
    * @param vsheet the Viewsheet to be exported.
    * @param sheetName the specified sheet name.
    * @param box the specified viewsheet sandbox.
    */
   @Override
   protected void prepareSheet(Viewsheet vsheet, String sheetName, ViewsheetSandbox box)
      throws Exception
   {
      super.prepareSheet(vsheet, sheetName, box);

      if(isMatchLayout()) {
         return;
      }

      Assembly[] objs = viewsheet.getAssemblies();
      boolean hasMoveCheckBox = false;
      boolean rePreparedSheet = false;

      for(Assembly assembly : objs) {
         VSAssembly obj = (VSAssembly) assembly;
         Dimension size = obj.getPixelSize();

         if((obj instanceof ComboBoxVSAssembly ||
            obj instanceof TextInputVSAssembly) && size.width > AssetUtil.defw)
         {
            Point pos = obj.getPixelOffset();

            if(isOverlaps(obj, viewsheet, false)) {
               obj.setPixelOffset(new Point(pos.x + AssetUtil.defw, pos.y));
            }
         }

         //Fixed bug #24335 that should prepareSheet after moveTableAssembly
         if(hasMoveCheckBox && obj instanceof TableVSAssembly) {
            rePreparedSheet = true;
            hasMoveCheckBox = false;
         }
      }

      if(rePreparedSheet) {
         super.prepareSheet(viewsheet, sheetName, box);
      }
   }

   protected int getExpandTableHeight(TableDataVSAssembly obj, XTable table) {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) obj.getInfo();
      int oldHeight = super.getExpandTableHeight(obj, table);
      VSTableLens lens = (VSTableLens)table;
      int[] hs = new int[lens.getRowCount()];
      int[] heights = lens.getRowHeights();
      int totalHeight = 0;

      for(int i = 0; i < hs.length; i++) {
         int h = 0;

         if(i >= heights.length) {
            h = AssetUtil.defh;
         }
         else {
            h = lens.getWrappedHeight(i, true);
         }

         h = (int)Math.round(((double)h) / AssetUtil.defh) * AssetUtil.defh;
         totalHeight += h;
      }

      totalHeight += PoiExcelVSUtil.getExcelTitleHeight(info);

      return Math.max(oldHeight, totalHeight);
   }

   /**
    * Write radiobutton or checkbox title.
    */
   private void writeTitle(int firstRow, int lastRow, int firstCol, int lastCol,
                           String title)
   {
      PoiExcelVSUtil.mergeRegion(firstRow, lastRow, firstCol, lastCol, sheet);
      Cell titleCell =
         PoiExcelVSUtil.getCell(firstCol, PoiExcelVSUtil.getRow(firstRow, sheet));
      titleCell.setCellValue(title);
   }

   /**
    * Write a dropdown list in excel to take the place of RadioButton and
    * Combobox assembly.
    * @param info the specified assebmlyinfo.
    */
   public void writeContent(ListInputVSAssemblyInfo info, int firstRow,
      int lastRow, int firstCol, int lastCol, boolean showErr)
   {
      // @by stephenwebster, For Bug #9476
      // Multiple bookmarks/views printed on same workbook will have multiple
      // sheets ending with "_Data".  We should qualify the dataSheet with the
      // currently printing view, otherwise, you write out data on the wrong
      // sheet
      if(dataSheet == null ||
         !dataSheet.getSheetName().endsWith(sheet.getSheetName() + "_Data"))
      {
         dataSheet = PoiExcelVSUtil.getDataSheet(sheet, getSheetName(sheetName));
      }

      String assemblyName = info.getAbsoluteName();
      String[] labels = info.getLabels();

      // write data to the data sheet
      int rowIdx = 0;
      int colIdx = 0;

      Row row = PoiExcelVSUtil.getRow(rowIdx, dataSheet);

      while(row.getCell(colIdx) != null) {
         colIdx++;
      }

      Cell titleCell =
         PoiExcelVSUtil.getCell(colIdx, PoiExcelVSUtil.getRow(rowIdx, dataSheet));
      titleCell.setCellValue(assemblyName);

      for(String label : labels) {
         rowIdx++;
         Cell cell =
            PoiExcelVSUtil.getCell(colIdx, PoiExcelVSUtil.getRow(rowIdx, dataSheet));
         cell.setCellValue(label == null ? "null" : Tool.localize(label));
      }

      // add cell name in the data sheet
      String cellRef = PoiExcelVSUtil.getCellReference(2, rowIdx == 0 ? 2 :
         rowIdx + 1, colIdx + 1, colIdx + 1);
      String validName = assemblyName;

      if(assemblyName != null) {
         validName = assemblyName.replaceAll("\\.", "_");
      }

      PoiExcelVSUtil.setCellName(cellRef, getNextCellName(validName + "_DATA"), dataSheet,
                                 true, true);

      // add dropdown list in current sheet
      PoiExcelVSUtil.mergeRegion(firstRow, lastRow, firstCol, lastCol, sheet);
      PoiExcelVSUtil.addDropdownList(firstRow, lastRow, firstCol, lastCol,
                                     validName + "_DATA", sheet, showErr, "");
      CellStyle cs = sheet.getWorkbook().createCellStyle();
      cs.setDataFormat(HSSFDataFormat.getBuiltinFormat("@"));

      Cell cell = PoiExcelVSUtil.getCell(firstCol, PoiExcelVSUtil.getRow(firstRow, sheet));
      cell.setCellStyle(cs);
      cell.setCellValue(info.getSelectedLabel() == null ? "null" :
                        Tool.localize(info.getSelectedLabel()));

      PoiExcelVSUtil.setCellName(cell, getNextCellName(assemblyName), sheet);
   }

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      if(isMatchLayout()) {
         super.writeTable(assembly, lens);
         return;
      }

      try {
         if(!onlyDataComponents) {
            OfflineExcelTableHelper helper = new OfflineExcelTableHelper(
               book, sheet, this, assembly);
            helper.setExcelToCSV(excelToCSV);
            helper.setExporter(this);
            helper.write(assembly, lens);
         }

         writeExpandTableToSheet(assembly, lens);

         if(shouldCreateBackup()) {
            cloneSheet = book.createSheet(getBackupName());
            book.setSheetHidden(book.getSheetIndex(cloneSheet), true);
            //bug #21260 that form table binding large data, vs can't export successful.
            //This bug still reproduce.
            //And prepare clonesheet ,the table data will be lost.
            //bug #24346 need cloneSheet.
            // write clone sheet
         }

         if(cloneSheet != null && lens.getFormTableLens() != null) {
            OfflineExcelTableHelper helper0 = new OfflineExcelTableHelper(
               book, cloneSheet, this, assembly);
            helper0.setExporter(this);
            helper0.write(assembly, lens);
         }
      }
      catch(SheetMaxRowsException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to write table: " + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Should create a new backup sheet when sheet is changed, just create backup for Current View.
    * @return
    */
   private boolean shouldCreateBackup() {
      return StringUtils.equals(Catalog.getCatalog().getString("Current View"), sheetName) &&
         (cloneSheet == null || !StringUtils.equals(getBackupName(), cloneSheet.getSheetName()));
   }

   /**
    * Gets the backup sheet name.
    * @return
    */
   private String getBackupName() {
      String backupSheetName = sheetName;

      if(sheetName.length() > 24) {
         backupSheetName = sheetName.substring(0, 24);
      }

      return getSheetName(backupSheetName) + "_Backup";
   }

   /**
    * Paint assembly outer border.
    */
   private void paintBoxBorder(int firstRow, int lastRow, int firstCol,
      int lastCol, Workbook wb, Sheet sheet)
   {
      CellStyle cs;

      for(int i = firstRow; i <= lastRow; i++) {
         for(int j = firstCol; j <= lastCol; j++) {
            cs = wb.createCellStyle();
            cs.cloneStyleFrom(PoiExcelVSUtil.getCell(j,
                                                     PoiExcelVSUtil.getRow(i, sheet)).getCellStyle());

            if(i == firstRow) {
               cs.setBorderTop(BorderStyle.THIN);
            }

            if(i == lastRow) {
               cs.setBorderBottom(BorderStyle.THIN);
            }

            if(j == firstCol) {
               cs.setBorderLeft(BorderStyle.THIN);
            }

            if(j == lastCol) {
               cs.setBorderRight(BorderStyle.THIN);
            }

            Row row = PoiExcelVSUtil.getRow(i, sheet);
            Cell cell = PoiExcelVSUtil.getCell(j, row);
            cell.setCellStyle(cs);
         }
      }
   }

   /**
    * Set scalar element format.
    */
   private void setScalarStyles(VSCompositeFormat format,
      CellRangeAddress cellRange, String cellFormat)
   {
      PoiExcelVSUtil.setCellStyles(book, sheet, format, null,
                                   cellRange, null,
                                   PoiExcelVSUtil.getPOIFont(format, book, true),
                                   format.getFont(), format.getForeground(),
                                   true, false, ExcelVSUtil.CELL_CONTENT,
                                   ExcelVSUtil.CELL_CONTENT, cellFormat, stylecache);
   }

   public String getNextCellName(String name) {
      String oname = name;

      for(int i = 1; cellNames.contains(name); i++) {
         name = oname + "_" + i;
      }

      cellNames.add(name);
      return name;
   }

   private Sheet cloneSheet = null;
   private Sheet dataSheet = null;
   private Map<XSSFFormatRecord, XSSFCellStyle> stylecache = new HashMap<>();
   private Set<String> cellNames = new HashSet<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(PoiExcelVSExporter.class);
}
