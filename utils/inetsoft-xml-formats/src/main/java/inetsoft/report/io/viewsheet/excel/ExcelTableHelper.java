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

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.VSTableHelper;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.viewsheet.service.VSExportService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Excel exporting helper for table.
 *
 * @version 8.5, 8/15/2006
 * @author InetSoft Technology Corp
 */
public class ExcelTableHelper extends VSTableHelper {
   /**
    * Constructor.
    * @param book the Workbook of the sheet.
    * @param sheet the Sheet to write the Table.
    */
   public ExcelTableHelper(Workbook book, Sheet sheet) {
      super();
      this.sheet = sheet;
      this.book = book;
   }

   /**
    * Constructor.
    * @param book the Workbook of the sheet.
    * @param sheet the Sheet to write the Table.
    */
   public ExcelTableHelper(Workbook book, Sheet sheet, VSAssembly assembly) {
      this(book, sheet);
      this.assembly = assembly;
      setViewsheet(assembly.getViewsheet());
   }

   /**
    * Calculate the columns start position and width.
    * @param info the specified table assembly info.
    */
   @Override
   protected int calculateColumnsPosition(TableDataVSAssemblyInfo info, VSTableLens lens) {
      int infoCols = super.calculateColumnsPosition(info, lens);
      initRows(info, lens);
      initExcelRowCount(info);
      return infoCols;
   }

   private void initExcelRowCount(TableDataVSAssemblyInfo info) {
      if(rowPixelH == null) {
         return;
      }

      int rowPixelHLength = rowPixelH.length;
      excelRows = new int[rowPixelHLength];

      for(int i = 0; i < rowPixelH.length; i++) {
         excelRows[i] = (int) Math.round(((double)rowPixelH[i]) / AssetUtil.defh);
         excelRows[i] = Math.max(1, excelRows[i]);
      }

      int titleH = PoiExcelVSUtil.getExcelTitleHeight(info);
      int viewHeight = info.getPixelSize().height;
      int dataHeight = viewHeight - titleH;
      int allExcelRows = dataHeight / AssetUtil.defh;
      int count = 0;
      dataRowCount = 0;

      for(int i = 0; i < excelRows.length; i++) {
         count += excelRows[i];

         if(count <= allExcelRows) {
            dataRowCount++;

            if(count == allExcelRows) {
               break;
            }
         }
         else {
            break;
         }
      }
   }

   protected boolean isBottom(int irow, int infoCols) {
      return dataRowCount - 1 == irow;
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to Excel.
    * @param startX the Crosstab's X coordinate in cells.
    * @param startY the Crosstab's Y coordinate in cells.
    * @param bounds the Dimension returned by getSpan().
    * @param irow the cell's row.
    * @param icol the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   @Override
   protected void writeTitleCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int irow, int icol,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec)
   {
      boolean shrink = VSUtil.isAssemblyShrink(assembly);
      Dimension titleBounds = new Dimension(bounds);
      int width = shrink ? PoiExcelVSUtil.getShrinkTitleWidth(bounds.width, columnPixelW) :
         bounds.width;
      bounds.width = shrink ? columnPixelW.length :
         (int) Math.round((double) bounds.width / AssetUtil.defw);
      bounds.height = (int) Math.round((double) bounds.height / AssetUtil.defh);
      titleBounds.height = PoiExcelVSUtil.getTableTitleHeight(titleBounds.height);
      titleBounds.width = width;
      startY = PoiExcelVSUtil.ceilY(startY);
      PoiExcelVSUtil.writeTableCell(startX, startY, bounds, irow, icol, format,
                                    dispText, dispObj, sheet, book,
                                    (PoiExcelVSExporter) getExporter(),
                                    parentformat, rec, false, null, null,
                                    shrink ? columnPixelW : null, titleBounds, excelRows,
                                    dataRowCount, hyperlink, true);
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to Excel.
    * @param startX the Crosstab's X coordinate in cells.
    * @param startY the Crosstab's Y coordinate in cells.
    * @param bounds the Dimension returned by getSpan().
    * @param irow the cell's row.
    * @param icol the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   @Override
   protected void writeTableCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int irow, int icol,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec,
                                 Insets padding)
   {
      PoiExcelVSUtil.writeTableCell(startX, startY, bounds, irow, icol, format,
                                    dispText, dispObj, sheet, book,
                                    (PoiExcelVSExporter) getExporter(),
                                    parentformat, rec,
                                    getAnnotationContent(irow, icol), hyperlink);
   }

   /**
    * Implementation of the method for writing table cells to Excel.
    * @param startX the Crosstab's X coordinate in cells.
    * @param startY the Crosstab's Y coordinate in cells.
    * @param bounds the Dimension returned by getSpan().
    * @param irow the cell's row.
    * @param icol the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    * @param hyperlink the link in the table cell.
    * @param parentformat cell parent format.
    */
   @Override
   protected void writeTableCell(int startX, int startY, Dimension bounds,
                                 Rectangle2D pixelbounds, int irow, int icol,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec,
                                 String fmtPattern, int[] columnPixelW,
                                 Insets padding)
   {
      if(dataRowCount != -1 && irow >= dataRowCount) {
         return;
      }

      if(dataRowCount != -1 && irow == dataRowCount - 1) {
         rec.height = dataRowCount;
      }

      boolean hasTitle = false;

      if(assembly instanceof TableVSAssembly) {
         hasTitle = ((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).isTitleVisible();
      }

      if(!VSUtil.isInTab(assembly)) {
         startY = PoiExcelVSUtil.ceilY(startY);
      }

      boolean dispValue = fmtPattern != null && !"General".equals(fmtPattern) &&
         !"@".equals(fmtPattern) && !(dispObj instanceof String) && !(dispObj instanceof Character);
      PoiExcelVSUtil.writeTableCell(startX, startY, bounds, irow, icol, format,
                                    dispText, dispObj, sheet, book,
                                    (PoiExcelVSExporter) getExporter(), parentformat,
                                    rec, dispValue, fmtPattern,
                                    getAnnotationContent(irow, icol),
                                    columnPixelW, null, excelRows, dataRowCount, hyperlink,
                                    hasTitle);
   }

   /**
    * Get the table range for export.
    */
   @Override
   protected Rectangle getTableRectangle(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      Rectangle rec = super.getTableRectangle(info, lens);
      VSExporter exporter = this.getExporter();
      int titleH = PoiExcelVSUtil.getExcelTitleHeight(info);
      rec.y = info.getPixelOffset().y + titleH;

      // @by stephenwebster, fix bug1393453777738
      // Treat table bounds for non-match-layout the same as using
      // the shrink option on a table.  The primary function is to show all
      // data.  This ensures that when printing the table output it doesn't
      // write over the existing column data with blank filled in cells
      if(info.isShrink() || !exporter.isMatchLayout()) {
         return rec;
      }

      rec.height = Math.max(rec.height,
         (int) Math.round((double) (info.getPixelSize().height - titleH) / AssetUtil.defh));
      rec.width = Math.max(rec.width,
         (int) Math.round((double) info.getPixelSize().width / AssetUtil.defw));

      if(exporter.isMatchLayout()) {
         rec.y = PoiExcelVSUtil.ceilY(info.getPixelOffset().y) + titleH;
      }

      return rec;
   }

   /**
    * Write the data for table assembly.
    *
    * @param info the specified table assembly info.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeData(TableDataVSAssemblyInfo info, VSTableLens lens) {
      super.writeData(info, lens);
      sheet.validateMergedRegions();
   }

   /**
    * Write table cells that lens not cover.
    */
   @Override
   protected void writeTableAssemblyCell(int irow, int icol,
                                         Rectangle tableRange,
                                         VSCompositeFormat parentformat,
                                         Rectangle rec)
   {
      if(dataRowCount != -1) {
         // If the table assembly cell is large than the table's range, do not paint it.
         if(irow >= dataRowCount) {
            return;
         }

         // Check if the row is the last row, change rec to the row, then it will paint bottom
         // border for the last row. There will be two cases:
         // 1. the last row is smaller than the excelRowCount-1, then using old logic. The table not
         // resized is in the case.
         // 2. the last row is equal to excelRowCount-1, the set the size to excelRowCount, then it
         // will paint the last row right border.
         if(irow == dataRowCount - 1) {
            rec.height = dataRowCount;
         }
      }

      writeTableCell(tableRange.x, tableRange.y, tableRange.getSize(), null,
                     irow, icol, null, "", null, null,
                     parentformat, rec, null, columnPixelW, null);
   }

   /**
    * Write the data for tab assembly.
    * @param info the specified CrossTableVSAssemblyInfo.
    */
   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly) {
   }

   /**
    * Excel don't use pixel bounds.
    */
   @Override
   protected Rectangle2D getPixelBounds(TableDataVSAssemblyInfo info,
                                        int r, int c, Dimension span,
                                        VSTableLens lens)
   {
      return null;
   }

   /**
    * Set exporter.
    */
   @Override
   public void setExporter(VSExporter exporter) {
      super.setExporter(exporter);

      initAnnotation();
   }

   @Override
   protected int getRowCount(VSTableLens lens) {
      int rcount = lens.getRowCount();

      return excelToCSV ? Math.min(rcount, VSExportService.EXCEL_LIMIT_ROW) : rcount;
   }

   public void setExcelToCSV(boolean excel) {
      this.excelToCSV = excel;
   }

   protected Sheet sheet = null;
   protected Workbook book = null;
   protected int[] excelRows = null;
   protected int dataRowCount = -1;
   private boolean excelToCSV = false;
}
