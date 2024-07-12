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

import inetsoft.report.*;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.SparseMatrix;
import inetsoft.util.Tool;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.awt.*;
import java.awt.geom.Rectangle2D;

public class OfflineExcelTableHelper extends ExcelTableHelper {
   /**
    * Constructor.
    * @param book the Workbook of the sheet.
    * @param sheet the Sheet to write the Table.
    */
   public OfflineExcelTableHelper(Workbook book, Sheet sheet,
                                  OfflineExcelVSExporter exporter, VSAssembly assembly)
   {
      super(book, sheet, assembly);
      this.exporter = exporter;
   }

   /**
    * Write the title for table assembly.
    * @param info the specified table assembly info.
    */
   @Override
   protected void writeTitle(TableDataVSAssemblyInfo info, int totalColumnWidth) {
      if(!info.isTitleVisible()) {
         return;
      }

      Point position = info.getPixelOffset();
      FormatInfo finfo = info.getFormatInfo();

      VSCompositeFormat format =
         finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);

      // set the defaultfont to make the foreground take effect.
      if(format != null && format.getFont() == null) {
         format.getDefaultFormat().setFont(VSFontHelper.getDefaultFont());
      }

      if(info.isShrink()) {
         int totalWidth = 0;

         for(int i = 0; i < columnPixelW.length; i++) {
            totalWidth += columnPixelW[i];
         }

         totalColumnWidth = totalWidth;
      }

      int viewsheetX =
         (int) (getViewsheetBounds() != null ? getViewsheetBounds().getX() : 0);
      int viewsheetY =
         (int) (getViewsheetBounds() != null ? getViewsheetBounds().getY() : 0);
      Dimension bounds = new Dimension(totalColumnWidth, info.getTitleHeight());
      Dimension titleBounds = new Dimension(bounds);
      titleBounds.height = PoiExcelVSUtil.getTableTitleHeight(titleBounds.height);
      bounds.width = info.isShrink() ? columnPixelW.length :
         (int) Math.round((double) bounds.width / AssetUtil.defw);      bounds.height = (int) Math.round((double) bounds.height / AssetUtil.defh);
      int startY = PoiExcelVSUtil.ceilY(position.y - viewsheetY);
      setCellName(position.x - viewsheetX, startY, 0, 0, titleBounds,
         info.getAbsoluteName());
      CellRangeAddress cellRangeAddress = PoiExcelVSUtil.writeTableCell(position.x - viewsheetX,
                                                                        startY, bounds, 0, 0, format, Tool.localize(info.getTitle()), null,
                                                                        sheet, book, (PoiExcelVSExporter) getExporter(), info.getFormat(),
                                                                        new Rectangle(0, 0, 1, 1), false, null,
                                                                        getAnnotationContent(0, 0), null, titleBounds, null, true);

      if(cellRangeAddress != null &&
         cellRangeAddress.getFirstRow() <= cellRangeAddress.getLastRow())
      {
         titleExcelRowCount = cellRangeAddress.getLastRow() - cellRangeAddress.getFirstRow() + 1;
      }
   }

   /**
    * Write the data for table assembly.
    * @param info the specified table assembly info.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeData(TableDataVSAssemblyInfo info, VSTableLens lens) {
      Rectangle tableRange = getTableRectangle(info, lens);
      Rectangle vsBounds = getViewsheetBounds();

      if(vsBounds != null) {
         tableRange.x -= vsBounds.x;
         tableRange.y -= vsBounds.y;
      }

      tableRange.y = PoiExcelVSUtil.ceilY(tableRange.y);
      Object[] options = PoiExcelVSUtil.getTableOption((TableVSAssemblyInfo) info);
      boolean hasOptions = options != null && options.length > 0 &&
         !info.isShrink() && !sheet.getSheetName().endsWith("_Backup");

      if(hasOptions) {
         for(Object opt : options) {
            if(opt != null) {
               hasOptions = true;
               break;
            }
         }
      }

      if(hasOptions || sheet.getSheetName().endsWith("_Backup")) {
         int headerExcelRowCount = titleExcelRowCount >= 0 ? titleExcelRowCount : 1;
         tableRange.height = Math.max(tableRange.height,
            (int) Math.round((double) (info.getPixelSize().height / AssetUtil.defh)) - headerExcelRowCount);
      }

      int rowCount = getRowCount(lens);
      int colCount = lens.getColCount();
      colCount = Math.max(colCount, 1);
      SparseMatrix isWritten = new SparseMatrix();
      VSCompositeFormat parentformat = info.getFormat();
      Rectangle rec = new Rectangle(0, 0, tableRange.width, tableRange.height);
      int lastColStarts =
         columnStarts[colCount - 1] + columnWidths[colCount - 1];

      for(int irow = 0; irow < tableRange.height; irow++) {
         for(int icol = 0; icol < tableRange.width; icol++) {
            if(icol == 0) {
               int spanWidth = columnWidths[icol];
               int spanHeight = 1;
               int spanCount = 1;

               Dimension bounds = lens.getSpan(irow, icol);

               if(bounds != null) {
                  spanCount = bounds.width;

                  if(bounds.height > 1) {
                     spanHeight = bounds.height;
                  }

                  for(int i = 1; i < spanCount; i++) {
                     spanWidth += columnWidths[icol + i];
                  }
               }

               Dimension span = new Dimension(spanWidth, spanHeight);

               if(irow == 0) {
                  setCellName(tableRange.x, tableRange.y, irow, icol, span,
                     info.getAbsoluteName() + "_Header");
               }
               else {
                  FormTableLens flens = lens.getFormTableLens();
                  int state = flens != null ?
                     lens.getFormTableLens().getRowState(irow) : 0;

                  setCellName(tableRange.x, tableRange.y, irow, icol, span,
                     info.getAbsoluteName() + "." + irow +
                     (state == 0 ? "" : "\\" + state) + "_TR");
               }
            }

            if(irow < rowCount && icol < colCount) {
               writeTableDataCell(irow, icol, isWritten, info, lens, rowCount,
                                  colCount, tableRange, parentformat, rec);
            }
            else if(irow >= rowCount) {
            }
            else {
               int col = lens.getColCount() == 0 ? icol :
                         (icol < colCount ? columnStarts[icol] :
                         (lastColStarts + icol - colCount));
               Rectangle bounds = new Rectangle(tableRange);
               bounds.height = 1;
               bounds.width = lens.getColCount() != 0 && icol < colCount ?
                  (icol == colCount - 1 ?
                  tableRange.width - columnStarts[icol] :
                  columnStarts[icol + 1] - columnStarts[icol]) : 1;

               if(col < tableRange.width) {
                  if(hasOptions) {
                     writeTableAssemblyCell(
                        irow, col, bounds, parentformat, rec);
                  }
                  else {
                     super.writeTableAssemblyCell(
                        irow, col, bounds, parentformat, rec);
                  }
               }
            }
         }
      }

      if(sheet.getSheetName().endsWith("_Backup")) {
         return;
      }

      rowCount = Math.max(rowCount, info.getPixelSize().height - 1);
      setTableOption(options, tableRange.y, tableRange.x,
                     rowCount - 2, colCount, info.getAbsoluteName());
   }

   /**
    * Set cell name in excel.
    */
   private void setCellName(int startXPx, int startYPx, int irow, int icol,
                            Dimension span, String name)
   {
      final Point cellBottomRightPx =
         new Point(startXPx + getPixelOffsetRightColumn(icol),
                   startYPx + ((irow + span.height) * AssetUtil.defh));
      final Point cellTopLeftPx = new Point(startXPx + getPixelOffsetLeftColumn(icol),
                                            startYPx + (irow * AssetUtil.defh));

      final Point cellBottomRightGrid = exporter.getRowCol(cellBottomRightPx);
      final Point cellTopLeftGrid = exporter.getRowCol(cellTopLeftPx);

      final Dimension boundsGrid = new Dimension(cellBottomRightGrid.x - cellTopLeftGrid.x,
                                                 cellBottomRightGrid.y - cellTopLeftGrid.y);

      if(boundsGrid.width <= 0 || boundsGrid.height <= 0) {
         return;
      }

      final String nextName = exporter.getNextCellName(name);
      PoiExcelVSUtil.setCellName(cellTopLeftGrid.y, cellTopLeftGrid.x, nextName, sheet);
   }

   /**
    * Write table lens's data cell.
    */
   @Override
   protected void writeTableDataCell(int irow, int icol, SparseMatrix isWritten,
                                     TableDataVSAssemblyInfo info,
                                     VSTableLens lens,
                                     int rowCount, int colCount,
                                     Rectangle tableRange,
                                     VSCompositeFormat parentformat,
                                     Rectangle rec)
   {
      // Deprecated exporter logic, needs updating
      // @damianwysocki, Bug #9543
      // Removed grid, always use default value if column width not defined
      int spanWidth = columnWidths[icol];
      int spanHeight = 1;
      int spanCount = 1;

      if("T".equals(isWritten.get(irow, icol))) {
         return;
      }

      Dimension bounds = lens.getSpan(irow, icol);

      if(bounds != null) {
         for(int m = irow; m < irow + bounds.height && m < rowCount; m++) {
            for(int n = icol ; n < icol + bounds.width && n < colCount; n++) {
               isWritten.set(m, n, "T");
            }
         }

         spanCount = bounds.width;

         if(bounds.height > 1) {
            spanHeight = bounds.height;
         }

         for(int i = 1; i < spanCount; i++) {
            spanWidth += columnWidths[icol + i];
         }
      }
      else {
         isWritten.set(irow, icol, "T");
      }

      Dimension span = new Dimension(spanWidth, spanHeight);
      VSFormat format = lens.getFormat(irow, icol);
      boolean isForm = info instanceof TableVSAssemblyInfo &&
         ((TableVSAssemblyInfo) info).isForm();

      VSFormatTableLens vsformat =
         (VSFormatTableLens) Util.getNestedTable(lens, VSFormatTableLens.class);
      String cellFormat = ExportUtil.getCellFormat(lens, irow, icol,
         getExporter().isMatchLayout(), isForm);
      Object obj = getObject(lens, irow, icol, cellFormat);

      String v = Tool.toString(obj);

      if(v == null) {
         v = "";  // if more empty rows needed to be displayed.
      }

      Dimension gridsize = info.getPixelSize();
      boolean bottom = isBottom(irow, gridsize.height);
      boolean right = icol == columnPixelW.length - 1 || icol == colCount;
      applyTableBorders(format, info, icol == 0, bottom, right,
         irow == 0 && !info.isTitleVisible());

      VSFormat newformat = (VSFormat) format.clone();
      // bug1395799577545, add underline for hyperlink text
      if(newformat != null && newformat.getFont() != null &&
         getHyperLink(lens, irow, icol) != null)
      {
         StyleFont font = (StyleFont) newformat.getFont();
         font = (StyleFont) font.deriveFont(font.getStyle() | StyleFont.UNDERLINE);
         newformat.setFont(font);
         format = newformat;
      }

      VSCompositeFormat cfmt = new VSCompositeFormat();
      cfmt.setUserDefinedFormat(format);
      Insets padding = lens.getInsets(irow, icol);

      writeTableCell(tableRange.x, tableRange.y, span,
                     getPixelBounds(info, irow + lens.getHeaderRowCount(),
                                    icol, span, null), irow, columnStarts[icol],
                     cfmt, v, obj, getHyperLink(lens, irow, icol),
                     parentformat, rec, cellFormat, columnPixelW, padding);
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
                     irow, icol, null, "", null, null, parentformat, rec, null,
                     columnPixelW, null);
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
                                 String cellFormat, int[] columnPixelW, Insets padding)
   {
      boolean dispValue = cellFormat != null && !"General".equals(cellFormat) &&
         !"@".equals(cellFormat) && !(dispObj instanceof String) && !(dispObj instanceof Character);

      if(dispObj instanceof Painter && dispText != null) {
         dispObj = null;
         dispValue = true;
      }

      boolean hasTitle = false;

      if(assembly instanceof TableVSAssembly) {
         hasTitle = ((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).isTitleVisible();
      }

      startY = PoiExcelVSUtil.ceilY(startY);
      PoiExcelVSUtil.writeTableCell(startX, startY, bounds, irow, icol, format,
                                    dispText, dispObj, sheet, book,
                                    (PoiExcelVSExporter) getExporter(),
                                    parentformat, rec, dispValue, cellFormat,
                                    getAnnotationContent(irow, icol),
                                    columnPixelW, null, excelRows, dataRowCount, hyperlink,
                                    hasTitle);
   }

   /**
    * Set table column option.
    * @param options the table column options.
    * @param startRowIdx table start row index in excel.
    * @param startColIdx table start column index in excel.
    * @param rowCount the row count of specified table.
    * @param colCount the column count of specified table.
    * @param tableName the specified table's name.
    */
   private void setTableOption(Object[] options, int startRowIdx,
      int startColIdx, int rowCount, int colCount, String tableName)
   {
      if(options.length != colCount) {
         return;
      }

      int startY = startRowIdx;
      int startX = startColIdx;
      Point p1 = exporter.getRowCol(new Point(startColIdx, startRowIdx));
      startRowIdx = p1.y + 1;
      startColIdx = p1.x;

      for(int i = 0; i < colCount; i++) {
         ColumnOption option = (ColumnOption) options[i];

         if(options[i] == null) {
            continue;
         }

         PoiExcelVSUtil.addRangeValid(option, startRowIdx,
                                      startRowIdx + rowCount, startColIdx + i, startColIdx + i,
                                      tableName + i, sheet, (PoiExcelVSExporter) getExporter(),
                                      startX, startY, i, columnPixelW);
      }
   }

   protected boolean isBottom(int irow, int h) {
      if(rowPixelH == null) {
         return irow + 1 == (int) Math.round(((double) h / AssetUtil.defh)) - 1;
      }

      return irow + 1 == rowPixelH.length;
   }

   private final OfflineExcelVSExporter exporter;
   private int titleExcelRowCount = -1;
}
