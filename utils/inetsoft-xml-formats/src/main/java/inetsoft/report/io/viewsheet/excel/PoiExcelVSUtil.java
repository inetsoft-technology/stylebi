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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.io.excel.SheetMaxRowsException;
import inetsoft.report.io.viewsheet.*;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetLineDashVal;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.sql.Time;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.report.io.viewsheet.excel.ExcelVSUtil.*;

/**
 * Common utility methods for excel export.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class PoiExcelVSUtil {

   /**
    * Get excel border style with the specified viewsheet border style.
    * @param borderValue the specified viewsheet border style.
    * @return the corresponding excel border style.
    */
   public static BorderStyle getBorderStyle(int borderValue) {

      for(Map.Entry<Integer, BorderStyle> entry: borderMap.entrySet()) {
         // if the border is double, change all unsupported doubleline style to
         // CellStyle.BORDER_DOUBLE.
         if((borderValue & StyleConstants.DOUBLE_MASK) != 0) {
            return BorderStyle.DOUBLE;
         }

         if(borderValue == entry.getKey()) {
            return entry.getValue();
         }
      }

      return BorderStyle.THIN;
   }

   /**
    * Get border style for textbox.
    *
    * XSSFShape.setLineStyle function will increate 1 for line style.
    */
   public static int getTextBoxBorderStyle(int borderValue) {
      return getBorderStyle(borderValue).getCode() - 1;
   }

   /**
    * Get excel line style (for XSSFShape) with the specified viewsheet line style.
    */
   public static int getLineStyle(int lineValue) {
      for(int i = 0; i < lineMap.length; i++) {
         if(lineValue == lineMap[i][0]) {
            return (short) lineMap[i][1];
         }
      }

      return EXCEL_NO_BORDER;
   }

   /**
    * Get horizontal align style with the specified viewsheet align style.
    * @param alignValue viewsheet align style.
    * @return the specified CellStyle align style.
    */
   public static HorizontalAlignment getHorizontalAlign(int alignValue) {
      int align = alignValue & 0x7;

      for(Map.Entry<Integer, HorizontalAlignment> entry: horizontalMap.entrySet()) {
         if(align == entry.getKey()) {
            return entry.getValue();
         }
      }

      return HorizontalAlignment.LEFT;
   }

   /**
    * Get vertical align style with the specified viewsheet align style.
    * @param alignValue viewsheet align style.
    * @return the specified CellStyle align style.
    */
   public static VerticalAlignment getVerticalAlign(int alignValue) {
      int align = alignValue & 0x78;

      VerticalAlignment vAlign = verticalMap.computeIfPresent(align, (k, v) -> v);

      // default to top same as viewsheet
      return vAlign == null ? VerticalAlignment.TOP : vAlign;
   }

   /**
    * Translate the StyleFont style and Color into Font style
    * @param txtFont the StyleFont to be translated
    * @param book the workbook.
    * @param isAdjust if isAdjust and the size is smaller than 9,
    * then adjust the size to 9
    * @return translated Font for output
    */
   public static XSSFFont translateFontStyle(java.awt.Font txtFont, Color color,
                                             Workbook book, boolean isAdjust)
   {
      int extstyle = txtFont.getStyle();

      // super/sub script
      short ssstyle = Font.SS_NONE;

      if((extstyle & StyleFont.SUPERSCRIPT) != 0) {
         ssstyle = Font.SS_SUPER;
      }
      else if((extstyle & StyleFont.SUBSCRIPT) != 0) {
         ssstyle = Font.SS_SUB;
      }

      // underline
      byte hustyle = Font.U_NONE;

      if((extstyle & StyleFont.UNDERLINE) != 0) {
         hustyle = Font.U_SINGLE;
      }

      boolean bStrikeOut = ((extstyle & StyleFont.STRIKETHROUGH) != 0);
      int size = VSFontHelper.getFontSize(txtFont);

      if(isAdjust && size < 9) {
         size = 9;
      }

      XSSFFont xfont = PoiExcelVSUtil.findFont(
         book, txtFont.isBold(), color, (short) (size * 20), txtFont.getFamily(),
         txtFont.isItalic(), bStrikeOut, ssstyle, hustyle);

      if(xfont == null) {
         xfont = (XSSFFont) book.createFont();
         xfont.setBold(txtFont.isBold());

         if(color != null) {
            xfont.setColor(new XSSFColor(color, null));
         }

         xfont.setFontHeight((short) (size * 20));
         xfont.setFontName(txtFont.getFamily());
         xfont.setItalic(txtFont.isItalic());
         xfont.setStrikeout(bStrikeOut);
         xfont.setTypeOffset(ssstyle);
         xfont.setUnderline(hustyle);
      }

      return xfont;
   }

   /**
     * Finds a font that matches the one with the supplied attributes.
     */
   private static XSSFFont findFont(Workbook book, boolean isBold,
      java.awt.Color color, short fontHeight, String name, boolean italic,
      boolean strikeout, short typeOffset, byte underline)
   {
      int fnum = book.getNumberOfFonts();

      for(short i = 0; i < fnum; i++) {
         XSSFFont xfont = (XSSFFont) book.getFontAt(i);
         XSSFColor xcolor = xfont.getXSSFColor();

         if(xfont.getBold() == isBold
            && equalsRGB(xcolor, color)
            && xfont.getFontHeight() == fontHeight
            && xfont.getFontName().equals(name)
            && xfont.getItalic() == italic
            && xfont.getStrikeout() == strikeout
            && xfont.getTypeOffset() == typeOffset
            && xfont.getUnderline() == underline)
         {
            return xfont;
         }
      }

      return null;
   }

   /**
     * whether XSSFColor rgb equals java.awt.Color rgb.
     */
   private static boolean equalsRGB(XSSFColor xcolor, java.awt.Color color) {
      boolean equals = false;

      if(xcolor == null && color == null) {
         equals = true;
      }
      else if(xcolor != null && color != null) {
         byte[] xrgb = xcolor.getRGB();
         byte[] nrgb = {(byte) color.getRed(), (byte) color.getGreen(),
                        (byte) color.getBlue()};
         equals = xrgb[0] == nrgb[0] && xrgb[1] == nrgb[1] && xrgb[2] == nrgb[2];
      }

      return equals;
   }

   /**
    * Get row from specified sheet.
    * @param rownum the specified row number.
    * @param sheet the sheet that the row is on.
    * @return the specified row.
    */
   public static Row getRow(int rownum, Sheet sheet) {
      Row row = sheet.getRow(rownum);

      if(row == null) {
         row = sheet.createRow(rownum);
      }

      return row;
   }

   /**
    * Get cell from specified row.
    * @param cellnum the specified cell number.
    * @param row the row that the cell is on.
    * @return the specified row.
    */
   public static Cell getCell(int cellnum, Row row) {
      Cell cell = row.getCell((short) cellnum);

      if(cell == null) {
         cell = row.createCell((short) cellnum);
      }

      return cell;
   }

   /**
    * Merge region and set region's style.
    * @param sheet the sheet which the region is on.
    */
   public static CellRangeAddress mergeRegion(Point position, Dimension size, Sheet sheet) {
      if(position == null || size == null) {
         return null;
      }

      int xStart = position.x;
      int xEnd = position.x + size.width - 1;
      int yStart = position.y;
      int yEnd = position.y + size.height - 1;

      CellRangeAddress cellRange = new CellRangeAddress(yStart, yEnd, xStart, xEnd);
      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

      return cellRange;
   }

   /**
    * Merge region and set region's style.
    * @param firstRow the first row index.
    * @param lastRow the last row index.
    * @param firstCol the first column index.
    * @param lastCol the last column index.
    * @sheet the specified sheet.
    */
   public static CellRangeAddress mergeRegion(int firstRow, int lastRow,
                                              int firstCol, int lastCol,
                                              Sheet sheet)
   {
      if(firstRow == lastRow && firstCol == lastCol) {
         return null;
      }

      CellRangeAddress rangAddress = new CellRangeAddress(firstRow, lastRow,
                                                          firstCol, lastCol);
      PoiExcelVSUtil.addMergedRegion(sheet, rangAddress);

      return rangAddress;
   }

   /**
    * Add merge cell to sheet.
    */
   public static void addMergedRegion(Sheet sheet, CellRangeAddress range) {
      int firstRow = range.getFirstRow();
      int lastRow = range.getLastRow();
      int firstCol = range.getFirstColumn();
      int lastCol = range.getLastColumn();

      if(firstRow != lastRow || firstCol != lastCol) {
         // @by stephenwebster, For Bug #9480
         // The call to addMergedRegion(range) is expensive since it validates
         // merged rows every time you add a new region.  This gets especially
         // worse when you have many merged regions.
         // See https://bz.apache.org/bugzilla/show_bug.cgi?id=60397
         // I have added validation to the end of the writeData methods for the
         // ExcelHelpers so that it happens in one pass.  The validation still
         // adds some significant time to the export, but I wasn't sure if that
         // might cause any other issue.
         // In general, the addMergedRegion implementation is expensive, I hope
         // to see this get optimized on the bug referenced above.
         //sheet.addMergedRegionUnsafe(range);

         // @by stephenwebster, For Bug #10011 @temp
         // I have temporarily moved the code from addMergedRegionUnsafe to this
         // class to workaround https://bz.apache.org/bugzilla/show_bug.cgi?id=60397
         // When this bug is fixed, we can revert this change.
         addMergedRegionUnsafePatch(sheet, range);
      }
   }

   private static void addMergedRegionUnsafePatch(Sheet sheet,
                                                  CellRangeAddress region)
   {
      if (region.getNumberOfCells() < 2) {
         throw new IllegalArgumentException("Merged region " +
                                            region.formatAsString() +
                                            " must contain 2 or more cells");
      }

      region.validate(SpreadsheetVersion.EXCEL2007);

      CTWorksheet worksheet = ((XSSFSheet) sheet).getCTWorksheet();
      CTMergeCells ctMergeCells = worksheet.isSetMergeCells() ?
         worksheet.getMergeCells() : worksheet.addNewMergeCells();
      CTMergeCell ctMergeCell = ctMergeCells.addNewMergeCell();
      ctMergeCell.setRef(region.formatAsString());
   }

   /**
    * Get the specified cell's merge region.
    */
   public static CellRangeAddress getCellMergeRegion(Cell cell) {
      Sheet sheet = cell.getSheet();
      int mergedCnt = sheet.getNumMergedRegions();

      int rowIdx = cell.getRowIndex();
      int colIdx = cell.getColumnIndex();

      for(int i = 0; i < mergedCnt; i++) {
         CellRangeAddress addr = sheet.getMergedRegion(i);

         if(addr.getFirstRow() == rowIdx && addr.getFirstColumn() == colIdx) {
            return addr;
         }
      }

      return null;
   }

   /**
    * Get text hssf font.
    * @param format the specified VSCompositeFormat.
    * @param book the workbook which the text is on.
    * @param isAdjust adjust the size to 9.
    */
   public static Font getPOIFont(VSCompositeFormat format, Workbook book,
                                 boolean isAdjust)
   {
      if(format == null) {
         format = new VSCompositeFormat();
      }

      java.awt.Font txtFont = format.getFont() == null ?
         VSFontHelper.getDefaultFont() : format.getFont();
      Color fc = format.getForeground();

      if(fc == null) {
         fc = new Color(0, 0, 0);
      }

      return translateFontStyle(txtFont, fc, book, isAdjust);
   }

   /**
    * Create Hyperlink.
    * @param book the workbook.
    * @param url the hyperlink address.
    */
   public static Hyperlink createHyperlink(Workbook book, String url) {
      try {
         CreationHelper helper = book.getCreationHelper();
         Hyperlink link = helper.createHyperlink(HyperlinkType.URL);
         link.setAddress(url);
         return link;
      }
      // ignore invalid url
      catch(IllegalArgumentException ex) {
         return null;
      }
   }

   /**
    * Create rich text string.
    * @param book the workbook of the rich text string.
    * @param value the rich text value.
    */
   public static RichTextString createRichTextString(Workbook book,
                                                     String value)
   {
      CreationHelper helper = book.getCreationHelper();

      return helper.createRichTextString(value == null ? "" : value);
   }

   /**
    * Create client anchor.
    * @param book the specified workbook.
    */
   public static ClientAnchor createClientAnchor(Workbook book) {
      CreationHelper helper = book.getCreationHelper();

      return helper.createClientAnchor();
   }

   /**
    * Create client anchor.
    */
   public static ClientAnchor createClientAnchor(Workbook book, int dx1,
      int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2)
   {
      ClientAnchor anchor = createClientAnchor(book);
      anchor.setDx1(dx1);
      anchor.setDy1(dy1);
      anchor.setDx2(dx2);
      anchor.setDy2(dy2);
      anchor.setCol1(col1);
      anchor.setRow1(row1);
      anchor.setCol2(col2);
      anchor.setRow2(row2);

      return anchor;
   }

   /**
    * Write the measure bar.
    */
   public static void writeBar(SelectionValue value, Point p1, Point p2,
                               PoiExcelVSExporter exporter, SelectionList slist,
                               SelectionBaseVSAssemblyInfo info, Workbook book)
      throws Exception
   {
      p2.x++;
      p2.y++;
      Rectangle2D bounds = exporter.getBounds(p1, new Point(p2.x, p2.y));
      double barsize = info.isShowBar() ? info.getBarSize() : 0;
      barsize = barsize <= 0 && info.isShowBar() ? Math.ceil(bounds.getWidth() / 4) : barsize;
      Point bp = new Point();
      Point cell = exporter.getRowCol((int) (bounds.getX() + bounds.getWidth() - barsize),
         (int) bounds.getY(), bp);
      ClientAnchor anchor = PoiExcelVSUtil.createClientAnchor(book, bp.x, 0, 0, 0,
                                                              cell.x, cell.y, p2.x, p2.y);

      if(barsize >= 1) {
         Image img = VSSelectionListHelper.paintBar(
            value, (int) barsize, (int) bounds.getHeight(), slist, info);

         exporter.writePicture((BufferedImage) img, anchor);
      }
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
    * @param sheet the Sheet to write the TableCell.
    * @param book the Workbook of the sheet.
    * @param exporter the exporter.
    * @param hyperlink the hyperlink ref.
    * @return excel cell range.
    */
   public static CellRangeAddress writeTableCell(int startX, int startY, Dimension bounds,
                                                 int irow, int icol,
                                                 VSCompositeFormat format,
                                                 String dispText, Object dispObj,
                                                 Sheet sheet, Workbook book,
                                                 PoiExcelVSExporter exporter,
                                                 VSCompositeFormat parentformat,
                                                 Rectangle tbounds, String annotation,
                                                 inetsoft.report.Hyperlink.Ref hyperlink)
   {
      return writeTableCell(startX, startY, bounds, irow, icol, format, dispText,
                     dispObj, sheet, book, exporter, parentformat,
                     tbounds, false, null, annotation, null, null, hyperlink, false);
   }

   public static CellRangeAddress writeTableCell(int startX, int startY, Dimension bounds, int irow,
                                                 int icol, VSCompositeFormat format,
                                                 String dispText, Object dispObj, Sheet sheet,
                                                 Workbook book, PoiExcelVSExporter exporter,
                                                 VSCompositeFormat parentformat,
                                                 Rectangle tbounds, boolean dispObjValue,
                                                 String cellFormat, String annotation,
                                                 int[] columnPixelW, Dimension cellBounds,
                                                 inetsoft.report.Hyperlink.Ref hyperlink, boolean hasTitle)
   {
      return writeTableCell(startX, startY, bounds, irow, icol, format, dispText, dispObj, sheet, book,
         exporter, parentformat, tbounds, dispObjValue, cellFormat, annotation, columnPixelW,
         cellBounds, null, tbounds.height, hyperlink, hasTitle);
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
    * @param sheet the Sheet to write the TableCell.
    * @param book the Workbook of the sheet.
    * @param exporter the exporter.
    * @param hyperlink the hyperlink ref.
    * @return excel cell range.
    */
   public static CellRangeAddress writeTableCell(int startX, int startY, Dimension bounds, int irow,
                                                 int icol, VSCompositeFormat format,
                                                 String dispText, Object dispObj,
                                                 Sheet sheet, Workbook book,
                                                 PoiExcelVSExporter exporter,
                                                 VSCompositeFormat parentformat,
                                                 Rectangle tbounds, boolean dispObjValue,
                                                 String cellFormat, String annotation,
                                                 int[] columnPixelW, Dimension cellBounds,
                                                 int[] excelRowCount, int dataRowCount,
                                                 inetsoft.report.Hyperlink.Ref hyperlink,
                                                 boolean hasTitle)
   {
      int rowtype = CELL_CONTENT;

      if(irow == tbounds.y) {
         rowtype |= CELL_HEADER;
      }

      if(irow == tbounds.height - bounds.height) {
         rowtype |= CELL_TAIL;
      }

      int coltype = CELL_CONTENT;

      if(icol == tbounds.x) {
         coltype |= CELL_HEADER;
      }

      if(icol == tbounds.width - bounds.width) {
         coltype |= CELL_TAIL;
      }

      int cellStartX = -1;
      int cellEndX = -1;

      if(columnPixelW != null && columnPixelW.length > 0) {
         // fix bug1423718125017, for unshrinked crosstab and calctable, the
         // last column will cover the remaining columns, and the width value
         // was setted to 0 for these "extra" columns, so we need considering
         // these extra columns when decide whether the coltype is CELL_TAIL
         // to make sure the right border will be painted.
         int count = 0;

         for(int i = columnPixelW.length - 1; i > -1; i--) {
            if(columnPixelW[i] == 0) {
               count++;
            }
            else {
               break;
            }
         }

         if(count != 0 && icol == tbounds.width - count - bounds.width) {
            coltype |= CELL_TAIL;
         }

         int totalWidth = 0;

         for(int i = 0; i <= icol && columnPixelW.length > 0; i++) {
            totalWidth += columnPixelW[Math.min(i, columnPixelW.length - 1)];
         }

         // fix bug1378867362721, get the right cellStartX when freehandtable
         // column is resized.
         int colw = icol < columnPixelW.length
            ? columnPixelW[icol] : columnPixelW[columnPixelW.length - 1];
         int offset = totalWidth - colw;
         cellStartX = exporter.getCol(new Point(startX, startY), offset);

         if(bounds.width > 1) {
            for(int i = icol + 1; i < icol + bounds.width; i++) {
               totalWidth += columnPixelW[Math.min(i, columnPixelW.length - 1)];
            }
         }

         cellEndX = exporter.getCol(new Point(startX, startY), totalWidth) - 1;

         if(cellStartX > cellEndX) {
            return null;
         }
      }

      int cellStartY = -1;
      int cellEndY = -1;

      if(excelRowCount != null) {
         int totalRowCount = 0;

         for(int r = 0; r <= irow; r++) {
            int rcount = r < excelRowCount.length ? excelRowCount[r] : 1;
            totalRowCount += rcount;
         }

         int totalHeight = totalRowCount * AssetUtil.defh;
         int rowh = irow < excelRowCount.length ?
            excelRowCount[irow] * AssetUtil.defh : AssetUtil.defh;
         int offset = totalHeight - rowh;
         cellStartY = exporter.getRow(new Point(startX, startY), offset);

         if(bounds.height > 1) {
            for(int i = irow + 1; i < irow + bounds.height; i++) {
               // For spanned cells, should not span to expand the total table's size.
               if(i <= dataRowCount - 1) {
                  totalHeight += i < excelRowCount.length  ? excelRowCount[i] * AssetUtil.defh :
                     AssetUtil.defh;
               }
            }
         }

         cellEndY = exporter.getRow(new Point(startX, startY), totalHeight) - 1;

         if(cellStartY > cellEndY) {
            if(irow == 0 && icol == 0 && rowtype == 7 && coltype == 3) {
               cellEndY = exporter.getRow(new Point(startX, startY), cellBounds.height);
            }

            if(cellStartY > cellEndY) {
               return null;
            }
        }
      }

      Dimension endOffset = cellBounds != null ? cellBounds :
         new Dimension((icol + bounds.width) * AssetUtil.defw,
                       (irow + bounds.height) * AssetUtil.defh);
      Point p1 = exporter.getRowCol(new Point(startX, startY));
      Point p2 = exporter.getRowCol(new Point(startX + endOffset.width,
                                              startY + endOffset.height));
      Point p3 = exporter.getRowCol(new Point(startX + (icol * AssetUtil.defw),
                                              startY + (irow * AssetUtil.defh)));

      if(bounds.width > 0 && p2.x == p3.x) {
         p2.x = p3.x + 1;
      }

      if(bounds.height > 0 && p2.y == p3.y) {
         p2.y = p3.y + 1;
      }

      startX = p1.x;
      startY = p1.y;
      bounds = new Dimension(p2.x - p3.x, p2.y - p3.y);

      if(bounds.width <= 0 || bounds.height <= 0) {
         return null;
      }

      irow = p3.y - p1.y;
      icol = p3.x - p1.x;

      int maxrows = getSheetMaxRows();

      if(maxrows > 0) {
         bounds.height = Math.min(bounds.height, maxrows - startY - irow + 2);
      }

      if(bounds.height <= 0) {
         return null;
      }

      CellRangeAddress cellRange = null;

      if(cellStartX != -1 && cellEndX != -1) {
         if(cellStartY != -1 && cellEndY != -1) {
            cellRange = new CellRangeAddress(cellStartY, cellEndY, cellStartX, cellEndX);
         }
         else {
            cellRange = new CellRangeAddress(startY + irow,
               startY + irow + bounds.height - 1, cellStartX, cellEndX);
         }
      }
      else {
         cellRange = new CellRangeAddress(startY + irow,
            startY + irow + bounds.height - 1, startX + icol,
            startX + icol + bounds.width - 1);
      }

      if(getSheetMaxRows() > 0 && cellRange.getFirstRow() > getSheetMaxRows()) {
         throw new SheetMaxRowsException(getSheetMaxRows());
      }

      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

      Row row = getRow(cellStartY != -1 ? cellStartY : (startY + irow), sheet);
      Cell cell = PoiExcelVSUtil.getCell(cellStartX != -1 ? (short) cellStartX :
                                      (short) (startX + icol), row);
      PoiExcelVSUtil.setHyperlinkToCell(book, cell, hyperlink);

      RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                  Tool.convertHTMLSymbol(dispText));

      if(dispObj != null && dispObjValue) {
         setCellValue(cell, dispObj, hrText);

         // if the cell type is date and do not set format, set default.
         if(dispObj instanceof Date &&
            ("General".equals(cellFormat) || "@".equals(cellFormat)))
         {
            cellFormat = dispObj instanceof Time ? "hh:mm:ss" : "yyyy-MM-dd";
         }
      }
      else {
         setCellValue(cell, dispText, hrText);
      }

      if(annotation != null) {
         exporter.drawComment(cell, annotation);
      }

      Font font = getPOIFont(format, book, false);

      if(format == null) {
         format = new VSCompositeFormat();
      }

      boolean wrapText = dispText != null && dispText.contains("\n") || format.isWrapping();
      setCellStyles(book, sheet, format, parentformat, cellRange, null, font,
                    format.getFont(), format.getForeground(),
                    true, wrapText, rowtype, coltype, cellFormat, exporter.stylecache,
                    hasTitle);

      if(dispObj instanceof Painter || dispObj instanceof Image) {
         try {
            // For table cell now will using default row height of excel. If cell's row height
            // is 30px, in excel it will show as 40px(2 excel grid).
            int rcount = bounds.height;

            if(excelRowCount != null && irow < excelRowCount.length) {
               rcount = excelRowCount[irow];
            }

            if(bounds.height > 1 && excelRowCount != null) {
               for(int i = irow + 1; i < irow + bounds.height; i++) {
                  if(i <= dataRowCount - 1) {
                     rcount += i < excelRowCount.length ? excelRowCount[i] : 1;
                  }
               }
            }

            int col1 = cellRange.getFirstColumn();
            int row1 = cellRange.getFirstRow();
            int col2 = col1 + bounds.width;

            if(cellEndX != -1) {
               col2 = cellEndX + 1;
            }

            int row2 = row1 + rcount;

            if(col1 == col2) {
               col2++;
            }

            if(row1 == row2) {
               row2++;
            }

            Rectangle2D pixelbounds = exporter.getBounds(
               new Point(col1, row1), new Point(col2, row2));
            // @by stephenwebster, in general when calculating the painter width
            // most code assumes that 1 pixel will be consumed for the border,
            // so - 1 is used to help prevent image scaling
            BufferedImage img = ExportUtil.getPainterImage(
               dispObj, (int) Math.abs(pixelbounds.getWidth()) - 1,
               (int) Math.abs(pixelbounds.getHeight()) - 1, format);
            // shift x/y a little to avoid covering border
            // @by stephenwebster, the number of units that 1 pixel consumes
            // depends on the width of the Excel column.  In order to get an
            // accurate starting point to avoid covering the border, you must
            // calculate the number of pixels per unit to get the correct start
            // unit.  The 1024 is the total number of units in an Excel column.
            // int startUnit = (1024 / Math.max(1,
            //   widthUnitsToPixel(sheet.getColumnWidth(Math.min(col1, col2)))));
            // Excel column units are in range 0-1023
            // startUnit = Math.min(1023, startUnit);

            int lb = 1;
            int rb = 1;
            int tb = 1;
            int bb = 1;

            Insets borders = format.getBorders();

            if(borders != null) {
               lb = (int) Math.max(lb, GTool.getLineWidth(borders.left));
               rb = (int) Math.max(rb, GTool.getLineWidth(borders.right));
               tb = (int) Math.max(tb, GTool.getLineWidth(borders.top));
               bb = (int) Math.max(bb, GTool.getLineWidth(borders.bottom));
            }

            ClientAnchor anchor = PoiExcelVSUtil.createClientAnchor(book,
                                                                    lb * Units.EMU_PER_PIXEL, tb * Units.EMU_PER_PIXEL,
                                                                    -rb * Units.EMU_PER_PIXEL, -bb * Units.EMU_PER_PIXEL,
                                                                    Math.min(col1, col2), Math.min(row1, row2),
                                                                    Math.max(col1, col2), Math.max(row1, row2));
            exporter.writePicture(img, anchor);
         }
         catch(Exception ex) {
            LOG.error("Failed to write picture for painter", ex);
         }
      }

      return cellRange;
   }

   /**
    * Add hyperlink to given cell.
    * @param cell      the excel grid cell
    * @param hyperlink the link info
    */
   public static void setHyperlinkToCell(Workbook book, Cell cell,
      inetsoft.report.Hyperlink.Ref hyperlink)
   {
      if(hyperlink != null && hyperlink.getLinkType() == inetsoft.report.Hyperlink.WEB_LINK) {
         String url = ExcelVSUtil.getURL(hyperlink);
         CreationHelper createHelper = book.getCreationHelper();
         org.apache.poi.ss.usermodel.Hyperlink link =
            createHelper.createHyperlink(HyperlinkType.URL);

         try {
            link.setAddress(url);
            cell.setHyperlink(link);
         }
         catch(Exception e) {
            LOG.debug("Invalidate hyperlink to cells: " + url, e);
         }
      }
   }

   /**
    * There are some general assumptions about this code, but is generally okay
    * for default font size.  Code is prevalent among POI users to convert Excel
    * column widths in points to pixels.
    * @param widthUnits Typically the number of units returned from
    *                   Sheet.getColumnWidths
    */
   private static int widthUnitsToPixel(int widthUnits) {
      int pixels =
         (widthUnits / EXCEL_COLUMN_WIDTH_FACTOR) * UNIT_OFFSET_LENGTH;
      int offsetWidthUnits = widthUnits % EXCEL_COLUMN_WIDTH_FACTOR;
      pixels +=
         Math.floor((float) offsetWidthUnits /
                     ((float) EXCEL_COLUMN_WIDTH_FACTOR / UNIT_OFFSET_LENGTH));
      return pixels;
   }

   /**
    * There are some general assumptions about this code, but is generally okay
    * for default font size.  Code is prevalent among POI users to convert pixel
    * column width into Excel column widths in points.
    * @param pxs The number in pixels.
    * @return The number of Excel width units.
    */
   public static int pixelToWidthUnits(int pxs) {
      int widthUnits = (EXCEL_COLUMN_WIDTH_FACTOR * (pxs / UNIT_OFFSET_LENGTH));
      widthUnits += UNIT_OFFSET_MAP[(pxs % UNIT_OFFSET_LENGTH)];
      return widthUnits;
   }

   /**
    * The maximum column width for an individual cell is 255 characters
    */
   public static int getValidColumnWidth(int widthUnits) {
      return Math.min(widthUnits, EXCEL_COLUMN_WIDTH_LIMIT);
   }

   /**
    * set a value for the cell.
    */
   public static void setCellValue(Cell cell, Object value,
      RichTextString defaultText)
   {
      if(value instanceof Double) {
         cell.setCellValue(((Double) value).doubleValue());
         return;
      }

      if(value instanceof Float) {
         cell.setCellValue(((Float) value).floatValue());
         return;
      }

      if(value instanceof Integer) {
         cell.setCellValue(((Integer) value).intValue());
         return;
      }

      if(value instanceof Long) {
         cell.setCellValue(((Long) value).longValue());
         return;
      }

      if(value instanceof Date) {
         long time = ((Date) value).getTime();
         Calendar calendar = CoreTool.calendar.get();
         calendar.set(1900, 0, 1);

         // excel doesn't support the date time earlier than 1900-01-1
         if(time > calendar.getTimeInMillis()) {
            cell.setCellValue(new Date(time));
         }
         else {
            cell.setCellValue(defaultText);
         }

         return;
      }

      if(value instanceof Boolean) {
         cell.setCellValue(((Boolean) value).booleanValue());
         return;
      }

      if(value instanceof Number) {
         cell.setCellValue(((Number) value).toString());
         return;
      }

      if(value == null || (value instanceof String) || value instanceof Object[]) {
         try {
            cell.setCellValue(defaultText);
         }
         catch(Exception e) {
            LOG.debug("Unable to apply text to cell value: " + defaultText, e);
         }

         return;
      }

      if(value instanceof DCMergeDatePartFilter.MergePartCell || value instanceof PresenterPainter) {
         cell.setCellValue(defaultText);
         return;
      }
   }

   /**
    * Write title cell in container.
    * @param pos the position of the title cell.
    * @param size the size of the title cell.
    * @param offset the offset from second row of the selection container.
    * @param title the title of the title cell.
    * @param displayValue the value of the title cell.
    * @param format the VSCompositeFormat for the cell.
    * @param sheet the Sheet to write the TableCell.
    * @param book the Workbook of the sheet.
    * @param exporter the exporter.
    */
   public static void writeTitleInContainer(Point pos, Dimension size,
                                            int offset, String title,
                                            String displayValue,
                                            VSCompositeFormat format,
                                            Sheet sheet,
                                            Workbook book,
                                            PoiExcelVSExporter exporter,
                                            ClientAnchor anchor,
                                            VSCompositeFormat parentformat,
                                            int titleHeight,
                                            boolean force, double titleRatio)
   {
      Point p1 = exporter.getRowCol(pos.x, pos.y + AssetUtil.defh*offset, new Point());
      Point p2 = exporter.getRowCol(pos.x + size.width,
         pos.y + AssetUtil.defh*offset + titleHeight, new Point());
      p2 = new Point(p2.x > p1.x ? p2.x - 1 : p2.x,
                     p2.y > p1.y ? p2.y - 1 : p2.y);

      CellRangeAddress cellRange = new CellRangeAddress(p1.y, p2.y, p1.x, p2.x);
      Font hf = getPOIFont(format, book, true);

      if(force) {
         PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

         Row row = PoiExcelVSUtil.getRow(p1.y, sheet);
         Cell mcell = PoiExcelVSUtil.getCell(p1.x, row);
         String text = Tool.convertHTMLSymbol(
            ExportUtil.getTextInTitleCell(title, displayValue,
                                          size.width, getAdjustFont(hf), titleRatio));

         RichTextString hrText = PoiExcelVSUtil.createRichTextString(book, text);

         hrText.applyFont(hf);
         mcell.setCellValue(hrText);
      }

      int rowstyle = CELL_CONTENT;

      if(offset == size.height / AssetUtil.defh - 1 || force) {
         rowstyle |= CELL_TAIL;
      }

      java.awt.Font font = (format != null) ? format.getFont() : null;
      Color fg = (format != null) ? format.getForeground() : null;

      setCellStyles(book, sheet, format, parentformat, cellRange, anchor, hf,
                    font, fg, true, rowstyle, CELL_HEADER | CELL_TAIL,
                    exporter.stylecache);
   }

   // For selection container and seletion list/selection tree, should only change title to smaller
   // not larger.
   // 1. For container, if change container larger, it can cover selection list in container.
   // 2. For selection in container, if change selection larger, it will cover other
   // selection in container.
   // 3. For selection in vs, if change selection larger, will cover other assemblies.
   public static int getSelectionTitleHeight(TitledVSAssemblyInfo info) {
      int titleHeight = info.getTitleHeight();
      titleHeight = (int)Math.floor((double)titleHeight / AssetUtil.defh) * AssetUtil.defh;
      titleHeight = Math.max(titleHeight, AssetUtil.defh);
      return titleHeight;
   }

   public static int getExcelTitleHeight(TitledVSAssemblyInfo info) {
      if(!info.isTitleVisible()) {
         return 0;
      }

      int titleHeight = info.getTitleHeight();
      titleHeight = (int)Math.round((double)titleHeight / AssetUtil.defh) * AssetUtil.defh;
      titleHeight = Math.max(titleHeight, AssetUtil.defh);
      return titleHeight;
   }

   public static int ceilY(int y) {
      return (int) Math.ceil((double) y / AssetUtil.defh) * AssetUtil.defh;
   }

   public static int floorY(int y) {
      return (int) Math.floor((double) y / AssetUtil.defh) * AssetUtil.defh;
   }

   public static int getTableTitleHeight(int titleHeight) {
      titleHeight = (int)Math.round((double)titleHeight / AssetUtil.defh) * AssetUtil.defh;
      titleHeight = Math.max(titleHeight, AssetUtil.defh);
      return titleHeight;
   }

   public static int getSelectionCellHeight(int cellHeight) {
      cellHeight = (int)Math.round((double)cellHeight / AssetUtil.defh) * AssetUtil.defh;
      cellHeight = Math.max(cellHeight, AssetUtil.defh);
      return cellHeight;
   }

   public static int getShrinkTitleWidth(int width, int[] columnWidths) {
      if(columnWidths == null) {
         return width;
      }

      int totalWidth = 0;

      for(int i = 0; i < columnWidths.length; i++) {
         totalWidth += columnWidths[i];
      }

      if(width > totalWidth) {
         return totalWidth;
      }

      return width;
   }

   /**
    * Get java font from hssf font.
    * @param hf the hssf font.
    */
   private static java.awt.Font getAdjustFont(Font hf) {
      int style = 0;

      if(hf.getBold() || hf.getItalic()) {
          style = 0;

          if(hf.getBold()) {
             style ^= java.awt.Font.BOLD;
          }

          if(hf.getItalic()) {
             style ^= java.awt.Font.ITALIC;
          }
      }
      else {
          style = java.awt.Font.PLAIN;
      }

      return new java.awt.Font(hf.getFontName(), style,
         Math.round(4 * hf.getFontHeightInPoints() / 3));
   }

   /**
    * Set cell style for all cells in a region.
    */
   public static void setCellStyles(Workbook workbook, Sheet sheet,
                                    VSCompositeFormat format,
                                    VSCompositeFormat parentformat,
                                    CellRangeAddress cellRange,
                                    ClientAnchor anchor, Font hfont,
                                    java.awt.Font jFont,
                                    Color jColor,
                                    boolean forceborder,
                                    int rowtype, int coltype,
                                    Map<XSSFFormatRecord, XSSFCellStyle> cache)
   {
      setCellStyles(workbook, sheet, format, parentformat, cellRange, anchor,
                    hfont, jFont, jColor, forceborder, false, rowtype,
                    coltype, null, cache);
   }

   public static void setCellStyles(Workbook workbook, Sheet sheet,
                                    VSCompositeFormat format,
                                    VSCompositeFormat parentformat,
                                    CellRangeAddress cellRange,
                                    ClientAnchor anchor, Font hfont,
                                    java.awt.Font jFont, Color jColor, boolean forceborder,
                                    boolean wrapText, int rowtype, int coltype, String cellFormat,
                                    Map<XSSFFormatRecord, XSSFCellStyle> cache)
   {
      setCellStyles(workbook, sheet, format, parentformat, cellRange, anchor,
              hfont, jFont, jColor, forceborder, false, rowtype,
              coltype, null, cache, false);
   }

   /**
    * Set cell style for all cells in a region.
    */
   public static void setCellStyles(Workbook workbook, Sheet sheet,
                                    VSCompositeFormat format,
                                    VSCompositeFormat parentformat,
                                    CellRangeAddress cellRange,
                                    ClientAnchor anchor, Font hfont,
                                    java.awt.Font jFont, Color jColor, boolean forceborder,
                                    boolean wrapText, int rowtype, int coltype, String cellFormat,
                                    Map<XSSFFormatRecord, XSSFCellStyle> cache, boolean hasTitle)
   {
      if(cellRange == null || workbook == null || sheet == null) {
         return;
      }

      if(parentformat == null) {
         parentformat = new VSCompositeFormat();
      }

      CellRangeAddress areaRegion = null;

      if(anchor != null) {
         int col2 = Math.max(anchor.getCol1(), anchor.getCol2() - 1);
         int row2 = Math.max(anchor.getRow1(), anchor.getRow2() - 1);
         areaRegion = new CellRangeAddress(anchor.getRow1(), row2, anchor.getCol1(), col2);
      }

      if(areaRegion == null) {
         areaRegion = cellRange;
      }

      if(hfont == null) {
         hfont = getPOIFont(format, workbook, true);
      }

      int rowStart =
         Math.min(areaRegion.getFirstRow(), cellRange.getFirstRow());
      int rowEnd = Math.max(areaRegion.getLastRow(), cellRange.getLastRow());
      int colStart = Math.min(areaRegion.getFirstColumn(),
                                        cellRange.getFirstColumn());
      int colEnd = Math.max(areaRegion.getLastColumn(),
                                      cellRange.getLastColumn());

      XSSFFormatRecord record = null;

      for(int i = rowStart; i <= rowEnd; i++) {
         for(int j = colStart; j <= colEnd; j++) {
            if(!cellRange.isInRange(i, j)) {
               continue;
            }

            record = new XSSFFormatRecord();
            record.setFont((XSSFFont) hfont, jFont, jColor);
            setCellBackground(record, parentformat, format);

            if(i == rowStart && j == colStart) {
               setCellAlignments(record, format);
            }

            if(i == rowStart) {
               boolean forceTop = hasTitle && rowtype == CELL_HEADER + CELL_CONTENT &&
                       coltype == CELL_HEADER + CELL_CONTENT ? false: forceborder;
               setTopBorderAndColor(record, parentformat, format,
                          rowtype, forceTop);
            }

            if(i == rowEnd || i == cellRange.getLastRow()) {
               boolean forceBottom =
                       hasTitle && rowtype == CELL_HEADER + CELL_CONTENT + CELL_TAIL &&
                       coltype == CELL_HEADER + CELL_CONTENT ? false: forceborder;
               setBottomBorderAndColor(record, parentformat, format,
                          rowtype, forceBottom);
            }

            if(j == colStart) {
               setLeftBorderAndColor(record, parentformat, format,
                                     coltype, forceborder);
            }

            if(j == colEnd) {
               setRightBorderAndColor(record, parentformat, format,
                                      coltype, forceborder);
            }

            if(cellFormat != null) {
               DataFormat df =  workbook.createDataFormat();
               record.setDataFormat(df.getFormat(cellFormat));
            }

            record.setWrapText(wrapText);

            Row row = getRow(i, sheet);
            Cell cell = getCell(j, row);
            cell.setCellStyle(createCellStyle(workbook, record, cache));
         }
      }
   }

   /**
    * Create cell style by XSSFFormatRecord.
    */
   public static XSSFCellStyle createCellStyle(
      Workbook workbook, XSSFFormatRecord record,
      Map<XSSFFormatRecord, XSSFCellStyle> cache)
   {
      XSSFCellStyle style = cache.get(record);

      if(style == null) {
         style = createCellStyle0(workbook, record);
         cache.put(new XSSFFormatRecord(record), style);
      }

      return style;
   }

   /**
    * Create cell style by XSSFFormatRecord.
    */
   private static XSSFCellStyle createCellStyle0(Workbook workbook,
                                                 XSSFFormatRecord record)
   {
      XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
      XSSFFont font = record.getFont();

      if(font != null) {
         style.setFont(font);
      }

      XSSFColor topBorderColor = record.getTopBorderColor();

      if(topBorderColor != null) {
         style.setTopBorderColor(topBorderColor);
      }

      XSSFColor leftBorderColor = record.getLeftBorderColor();

      if(leftBorderColor != null) {
         style.setLeftBorderColor(leftBorderColor);
      }

      XSSFColor rightBorderColor = record.getRightBorderColor();

      if(rightBorderColor != null) {
         style.setRightBorderColor(rightBorderColor);
      }

      XSSFColor bottomBorderColor = record.getBottomBorderColor();

      if(bottomBorderColor != null) {
         style.setBottomBorderColor(bottomBorderColor);
      }

      XSSFColor fillForegroundColor = record.getFillForegroundColor();

      if(fillForegroundColor != null) {
         style.setFillForegroundColor(fillForegroundColor);
         style.setFillPattern(record.getFillPattern());
      }

      short rotation = record.getRotation();

      if(rotation != 0) {
         style.setRotation(rotation);
      }

      style.setDataFormat(record.getDataFormat());
      style.setBorderTop(record.getBorderTop());
      style.setBorderLeft(record.getBorderLeft());
      style.setBorderRight(record.getBorderRight());
      style.setBorderBottom(record.getBorderBottom());
      style.setAlignment(record.getAlignment());
      style.setVerticalAlignment(record.getVerticalAlignment());
      style.setWrapText(record.isWrapText());

      return style;
   }

   /**
    * Set top border and color.
    */
   private static void setTopBorderAndColor(XSSFFormatRecord record,
      VSCompositeFormat pformat, VSCompositeFormat format, int celltype,
      boolean force)
   {
      BorderStyle originalBorder = BorderStyle.NONE;
      BorderStyle border = BorderStyle.NONE;

      if(format != null && format.getBorders() != null) {
         border = originalBorder = getBorderStyle(format.getBorders().top);
      }

      if(border == BorderStyle.NONE && force && pformat != null &&
         pformat.getBorders() != null && ((celltype &= CELL_HEADER) != 0)) {
         border = getBorderStyle(pformat.getBorders().top);
      }

      if(border != BorderStyle.NONE) {
         record.setBorderTop(border);
      }

      Color c = null;

      if(format != null && format.getBorderColors() != null) {
         c = format.getBorderColors().topColor;
      }

      if((c == null || originalBorder == BorderStyle.NONE) && force &&
         pformat != null && pformat.getBorderColors() != null &&
         ((celltype &= CELL_HEADER) != 0))
      {
         c = pformat.getBorderColors().topColor;
      }

      if(c != null) {
         record.setTopBorderColor(null, c);
      }
   }

   /**
    * Set bottom border and color.
    */
   private static void setBottomBorderAndColor(XSSFFormatRecord record,
      VSCompositeFormat pformat, VSCompositeFormat format, int celltype,
      boolean force)
   {
      BorderStyle originalBorder = BorderStyle.NONE;
      BorderStyle border = BorderStyle.NONE;

      if(format != null && format.getBorders() != null) {
         border = originalBorder = getBorderStyle(format.getBorders().bottom);
      }

      if(border == BorderStyle.NONE && force && pformat != null &&
         pformat.getBorders() != null && ((celltype &= CELL_TAIL) != 0)) {
         border = getBorderStyle(pformat.getBorders().bottom);
      }

      if(border != BorderStyle.NONE) {
         record.setBorderBottom(border);
      }

      Color c = null;

      if(format != null && format.getBorderColors() != null) {
         c = format.getBorderColors().bottomColor;
      }

      if((c == null || originalBorder == BorderStyle.NONE) && force &&
         pformat != null && pformat.getBorderColors() != null &&
         ((celltype &= CELL_TAIL) != 0))
      {
         c = pformat.getBorderColors().bottomColor;
      }

      if(c != null) {
         record.setBottomBorderColor(null, c);
      }
   }

   /**
    * Set left border and color.
    */
   private static void setLeftBorderAndColor(XSSFFormatRecord record,
      VSCompositeFormat pformat, VSCompositeFormat format, int celltype,
      boolean force)
   {
      BorderStyle originalBorder = BorderStyle.NONE;
      BorderStyle border = BorderStyle.NONE;

      if(format != null && format.getBorders() != null) {
         border = originalBorder = getBorderStyle(format.getBorders().left);
      }

      if(border == BorderStyle.NONE && force && pformat != null &&
         pformat.getBorders() != null && ((celltype &= CELL_HEADER) != 0)) {
         border = getBorderStyle(pformat.getBorders().left);
      }

      if(border != BorderStyle.NONE) {
         record.setBorderLeft(border);
      }

      Color c = null;

      if(format != null && format.getBorderColors() != null) {
         c = format.getBorderColors().leftColor;
      }

      if((c == null || originalBorder == BorderStyle.NONE) && force &&
         pformat != null && pformat.getBorderColors() != null &&
         ((celltype &= CELL_HEADER) != 0))
      {
         c = pformat.getBorderColors().leftColor;
      }

      if(c != null) {
         record.setLeftBorderColor(null, c);
      }
   }

   /**
    * Set right border and color.
    */
   private static void setRightBorderAndColor(XSSFFormatRecord record,
      VSCompositeFormat pformat, VSCompositeFormat format, int celltype,
      boolean force)
   {
      BorderStyle originalBorder = BorderStyle.NONE;
      BorderStyle border = BorderStyle.NONE;

      if(format != null && format.getBorders() != null) {
         border = originalBorder = getBorderStyle(format.getBorders().right);
      }

      if(border == BorderStyle.NONE && force && pformat != null &&
         pformat.getBorders() != null && ((celltype &= CELL_TAIL) != 0)) {
         border = getBorderStyle(pformat.getBorders().right);
      }

      if(border != BorderStyle.NONE) {
         record.setBorderRight(border);
      }

      Color c = null;

      if(format != null && format.getBorderColors() != null) {
         c = format.getBorderColors().rightColor;
      }

      if((c == null || originalBorder == BorderStyle.NONE) && force &&
         pformat != null && pformat.getBorderColors() != null &&
         ((celltype &= CELL_TAIL) != 0))
      {
         c = pformat.getBorderColors().rightColor;
      }

      if(c != null) {
         record.setRightBorderColor(null, c);
      }
   }

   /**
    * Set background for all the region.
    */
   private static void setCellBackground(XSSFFormatRecord record,
      VSCompositeFormat pformat, VSCompositeFormat format)
   {
      if(pformat != null || format != null) {
         Color bg = null;

         if(format != null) {
            bg = format.getBackground();
         }

         if(bg == null && pformat != null) {
            bg = pformat.getBackground();
         }

         if(bg != null) {
            record.setFillForegroundColor(null, bg);
            record.setFillPattern(FillPatternType.SOLID_FOREGROUND);
         }
      }
   }

   /**
    * Get correct color by the alpha.
    */
   public static Color getColorByAlpha(Color bg) {
      float red = bg.getRed() / 255f;
      float green = bg.getGreen() / 255f;
      float blue = bg.getBlue() / 255f;
      float alpha = bg.getAlpha() / 255f;

      int bgColor = 1;// a fake white background

      float r = bgColor * (1 - alpha) + red * alpha;
      float g = bgColor * (1 - alpha) + green * alpha;
      float b = bgColor * (1 - alpha) + blue * alpha;

      return new Color(r, g, b);
   }

   /**
    * Add drop down list for range cell.
    * @param firstRow the first row index.
    * @param lastRow the last row index.
    * @param firstCol the first column index.
    * @param lastCol the last column index.
    * @param cellName the refers cells name.
    * @param sheet the specified sheet.
    * @param showErr <tt>true</tt> if show error message, <tt>false</tt>
    */
   public static void addDropdownList(int firstRow, int lastRow, int firstCol,
      int lastCol, String cellName, Sheet sheet,
      boolean showErr, String errMeesage)
   {
      DataValidationHelper helper = sheet.getDataValidationHelper();
      DataValidationConstraint constraint =
         helper.createFormulaListConstraint(cellName);
      CellRangeAddressList rangList =
         new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
      DataValidation dataValid = helper.createValidation(constraint, rangList);
      dataValid.setEmptyCellAllowed(false);
      dataValid.setShowErrorBox(showErr);

      if(showErr) {
         dataValid.createErrorBox("Error Message", errMeesage);
      }

      sheet.addValidationData(dataValid);
   }

   /**
    * Set excel cell name.
    * @param rowIdx the cell's row index.
    * @param colIdx the cell's column index.
    * @paran cellName the specified cell's name.
    * @pram sheet the specified sheet.
    */
   public static void setCellName(int rowIdx, int colIdx, String cellName, Sheet sheet) {
      Row row = getRow(rowIdx, sheet);
      Cell cell = getCell(colIdx, row);
      setCellName(getCellReference(new Cell[] {cell}), cellName, sheet, true);
   }

   /**
    * Set excel cell name.
    */
   public static void setCellName(Cell cell, String cellName, Sheet sheet) {
      setCellName(getCellReference(new Cell[] {cell}), cellName, sheet, true);
   }

   /**
    * Set excel cell name.
    */
   public static void setCellName(int firstRow, int lastRow, int firstCol,
                                  int lastCol, String cellName, Sheet sheet)
   {
      // cell's row and col index is base 0, but the name reference's row and
      // col index is base 1, so need do change
      setCellName(getCellReference(firstRow + 1, lastRow + 1, firstCol + 1,
                  lastCol + 1), cellName, sheet, true);
   }

   /**
    * Set excel cell name.
    */
   public static void setCellName(String cellRef, String cellName, Sheet sheet,
                                  boolean includeSheet)
   {
      setCellName(cellRef, cellName, sheet, includeSheet, false);
   }

   /**
    * Set excel cell name.
    */
   public static void setCellName(String cellRef, String cellName, Sheet sheet,
                                  boolean includeSheet, boolean isWorkbookScope)
   {
      if("".equals(cellRef)) {
         return;
      }

      String sheetName = "";
      sheetName = sheet.getSheetName();
      boolean quoted = false;

      if(sheetName.contains(" ") || sheetName.contains("(") ||
         sheetName.contains(")"))
      {
         sheetName = "'" + sheetName + "'";
         quoted = true;
      }

      Workbook wb = sheet.getWorkbook();

      // @by skyf, Excel seems has a bug that if the number of cell names are
      // more than 65281, it will cause the excel error, and it won't have
      // so many data for offline form, so here we limit the cell names.
      if(wb.getNumberOfNames() >= 65281) {
         return;
      }

      //@by ankitmathur, For Bug #1176, Per POI syntax rules, we need to make
      //sure the name we are setting for the cell does not contain spaces. Since
      //underscore is a permitted character, we can use this as a replacement.
      if(cellName != null) {
         cellName = cellName.replaceAll("\\s+", "\\\\__\\\\");
         // @by clementwang, for bug #8859,
         // the new version of poi '.' is not allowed contains in cellName.
         cellName = cellName.replaceAll("\\.", "\\\\");
      }

      Name name = wb.createName();

      // @by stephenwebster, For Bug #9476
      // Cell Names must be unique within its scope.  Use the sheet index
      // as the scope so that the we don't get duplicate names.  I am not sure
      // in what cases you wouldn't want this, but for now, just moving this
      // check before the name gets created.
      if(includeSheet) {
         name.setSheetIndex(wb.getSheetIndex(sheet));
      }

      if(isWorkbookScope) {
         name.setSheetIndex(-1);
      }

      name.setNameName(Tool.toIdentifier(cellName));

      if(quoted) {
         // if the sheet name contains a space or parenthesis, it will have been quoted at the
         // beginning of this method
         name.setRefersToFormula(sheetName + cellRef);
      }
      else {
         name.setRefersToFormula("'" + sheetName + "'" + cellRef);
      }
   }

   /*
    * Get range cells's reference name
    */
   private static String getCellReference(Cell[] cells) {
      if(cells.length == 0) {
         return "";
      }

      int firstRow = -1;
      int lastRow = -1;
      int firstCol = -1;
      int lastCol = -1;

      for(int i = 0; i < cells.length; i++) {
         if(i == 0) {
            firstRow = cells[i].getRowIndex();
            lastRow = cells[i].getRowIndex();
            firstCol = cells[i].getColumnIndex();
            lastCol = cells[i].getColumnIndex();
         }

         firstRow = Math.min(firstRow, cells[i].getRowIndex());
         lastRow = Math.max(lastRow, cells[i].getRowIndex());
         firstCol = Math.min(firstCol, cells[i].getColumnIndex());
         lastCol = Math.max(lastCol, cells[i].getColumnIndex());
      }

      // cell's row and col index is base 0, but the name reference's row and
      // col index is base 1, so need do change
      return getCellReference(firstRow + 1, lastRow + 1, firstCol + 1,
                              lastCol + 1);
   }

   /*
    * Get range cells's reference name
    * @param firstRow the specified row index base on 1
    * @param lastRow the specified row index base on 1
    * @param firstCol the specified column index base on 1
    * @param lastCol the specified column index base on 1
    */
   public static String getCellReference(int firstRow, int lastRow,
                                          int firstCol, int lastCol)
   {
      String firstColRef = "!$";
      String lastColRef = "";

      if((firstCol / 26) != 0) {
         firstColRef = firstColRef + (char) ((firstCol / 26) - 1 + 65);
      }

      firstColRef += (char) ((firstCol % 26) - 1 + 65);

      if((lastCol / 26) != 0) {
         lastColRef = lastColRef + (char) ((lastCol / 26) - 1 + 65);
      }

      lastColRef += (char) ((lastCol % 26) - 1 + 65);

      if(firstRow == lastRow && firstCol == lastCol) {
         return firstColRef + "$" + firstRow;
      }

      return firstColRef + "$" + firstRow + ":$" + lastColRef + "$" + lastRow;
   }

   /**
    * Get all sheet names.
    */
   public static String[] getSheetNames(Workbook wb) {
      java.util.List<String> names = new ArrayList<>();
      int sheets = wb.getNumberOfSheets();

      for(int i = 0; i < sheets; i++) {
         Sheet sheet = wb.getSheetAt(i);
         String name = sheet.getSheetName();

         // @by stephenwebster, Standalone "chartsheet" sheets are not applicable
         if(!(sheet instanceof XSSFChartSheet)) {
            names.add(name);
         }
      }

      return names.toArray(new String[0]);
   }

   /**
    * Get all cell names of specified workbook sheet.
    */
   public static Map<String, Name> getSheetNames(Sheet sheet) {
      Map<String, Name> namesMap = new HashMap<>();
      Workbook wb = sheet.getWorkbook();
      List names = wb.getAllNames();
      String sheetName = sheet.getSheetName();

      for(int i = 0; i < names.size(); i++) {
         Name name = (Name) names.get(i);

         if(name.getRefersToFormula() != null && !name.isDeleted() &&
            sheetName.equals(name.getSheetName()))
         {
            String cellName = name.getNameName().replaceAll("\\\\__\\\\", " ");
            namesMap.put(cellName, name);
         }
      }

      return namesMap;
   }

   /**
    * Get scalar element's data by the cell name.
    */
   public static Object[] getDataByName(Name name, Sheet sheet) {
      List<Object> data = new ArrayList<>();
      String nameRef = name.getRefersToFormula();
      int idx = nameRef.indexOf(':');
      int[] firstCell = null;
      int[] lastCell = null;

      if(idx == -1) {
         firstCell = parseCellRef(nameRef);
      }
      else {
         firstCell = parseCellRef(nameRef.substring(0, idx));
         lastCell = parseCellRef(nameRef.substring(idx + 1));
      }

      Cell cell = null;

      if(lastCell == null) {
         cell = getCell(firstCell[1], getRow(firstCell[0], sheet));
         data.add(getValue(getCellValue(cell)));
      }
      else {
         for(int i = firstCell[0]; i <= lastCell[0]; i++) {
            Row row = getRow(i, sheet);

            for(int j = firstCell[1]; j <= lastCell[1]; j++) {
               cell = getCell(j, row);
               data.add(getValue(getCellValue(cell)));
            }
         }
      }

      return data.toArray();
   }

   /**
    * Get cell value by split special character like "25A1" and "221A".
    */
   private static Object getValue(Object obj) {
      if((obj instanceof String) && (((String) obj).startsWith("\u25A1") ||
         ((String) obj).startsWith("\u221A")))
      {
         if(((String) obj).startsWith("\u221A")) {
            String cval = (String) obj;
            cval = cval.substring(cval.indexOf("\u221A") + 3);

            return cval;
         }
         else {
            return "";
         }
      }

      return obj;
   }

   /**
    * Get cell by cell name.
    */
   public static Cell getCellByName(Name name, Sheet sheet) {
      String nameRef = name.getRefersToFormula();
      int[] p = parseCellRef(nameRef);

      return getCell(p[1], getRow(p[0], sheet));
   }

   /**
    * Get cell position by the cell reference.
    */
   private static int[] parseCellRef(String cellRef) {
      if(cellRef == null) {
         return null;
      }

      int startIdx = cellRef.indexOf('$') + 1;
      int endIdx = cellRef.indexOf('$', startIdx);
      String colRef = cellRef.substring(startIdx, endIdx);
      String rowRef = cellRef.substring(endIdx + 1);
      int colIdx = -1;
      int rowIdx = -1;

      if(colRef.length() == 1) {
         colIdx = (int) colRef.charAt(0) % 65;
         rowIdx = Integer.parseInt(rowRef) - 1;
      }
      else if(colRef.length() == 2) {
         colIdx = ((int) colRef.charAt(0) / 65) * 26 +
            ((int) colRef.charAt(1) % 65);
         rowIdx = Integer.parseInt(rowRef) - 1;
      }

      return new int[]{rowIdx, colIdx};
   }

   /**
    * Get cell value.
    */
   public static Object getCellValue(Cell cell) {
      if(cell == null) {
         return null;
      }

      CellType type = cell.getCellType();
      Object cellValue = null;

      switch(type) {
         case BLANK:
         cellValue = "";
         break;
      case BOOLEAN:
         cellValue = cell.getBooleanCellValue();
         break;
      case NUMERIC:
         if(DateUtil.isCellDateFormatted(cell)) {
            cellValue = cell.getDateCellValue();
         }
         else {
            cellValue = cell.getNumericCellValue();
         }
         break;
      case STRING:
         cellValue = cell.getStringCellValue();
         break;
      case FORMULA:
         cellValue = getFormulaValue(cell);
         break;
      case ERROR:
         cellValue = cell.getErrorCellValue();
         break;
      default:
         throw new IllegalStateException();
      }

      return cellValue;
   }

   /**
    * Get Excel formula value.
    */
   private static Object getFormulaValue(Cell cell) {
      Object value = null;
      Sheet sheet = cell.getSheet();
      Workbook wb = sheet.getWorkbook();
      FormulaEvaluator evaluator =
         wb.getCreationHelper().createFormulaEvaluator();
      CellValue cellValue = evaluator.evaluate(cell);

      switch(cellValue.getCellType()) {
         case BOOLEAN:
            value = cellValue.getBooleanValue();
            break;
         case NUMERIC:
            value = cellValue.getNumberValue();
            break;
         case STRING:
            value = cellValue.getStringValue();
            break;
         case BLANK:
         case ERROR:
            break;
         default:
            throw new IllegalStateException();
      }

      return value;
   }

   /**
    * Get cell name.
    */
   public static Name getCellName(Cell cell, Sheet sheet) {
      List<Name> sheetNames = new ArrayList<>();
      Workbook wb = sheet.getWorkbook();
      String sheetName = sheet.getSheetName();
      List names = wb.getAllNames();

      for(int i = 0; i < names.size(); i++) {
         Name name = (Name) names.get(i);

         if(name != null && name.getRefersToFormula() != null &&
            sheetName.equals(name.getSheetName()))
         {
            sheetNames.add(name);
         }
      }

      if(sheetName.contains(" ")) {
         sheetName = "'" + sheetName + "'";
      }

      String cellRef = sheetName + getCellReference(new Cell[] {cell});

      for(int i = 0; i < sheetNames.size(); i++) {
         if(sheetNames.get(i).getRefersToFormula().equals(cellRef)) {
            return sheetNames.get(i);
         }
      }

      return null;
   }

   /**
    * Compare the cells.
    */
   public static boolean compareCell(Cell cell1, Cell cell2) {
      Object obj1 = getCellValue(cell1);
      Object obj2 = getCellValue(cell2);

      // POI has a bug that HSSFDateUtil.isCellDateFormatted can not
      // the return correct result always, so the celVal may be get
      // incorrect double value by getCellValue when it's type is date.
      // we need to do something to deal with the situation.
      if(obj1 instanceof Date || obj2 instanceof Date) {
         if(obj1 instanceof Date && obj2 instanceof Double) {
            Object obj = DateUtil.getJavaDate((Double) obj2);

            if(obj1.equals(obj)) {
               return true;
            }
         }
         else if(obj2 instanceof Date && obj1 instanceof Double) {
            Object obj = DateUtil.getJavaDate((Double) obj1);

            if(obj2.equals(obj)) {
               return true;
            }
         }
      }
      else if(!obj1.getClass().equals(obj2.getClass())) {
         return false;
      }

      return obj1.equals(obj2);
   }

   /**
    * Get data sheet.
    */
   public static Sheet getDataSheet(Sheet sheet, String sheetName) {
      Workbook book = sheet.getWorkbook();
      Sheet dataSheet = null;

      for(int j = 0; j < book.getNumberOfSheets(); j++) {
         Sheet sheet0 = book.getSheetAt(j);

         // @by stephenwebster, For Bug #9476
         // Multiple bookmarks printed on same workbook will have multiple
         // sheets ending with "_Data", it should be qualified with the sheetName
         if(sheet0.getSheetName().endsWith(sheetName + "_Data")) {
            dataSheet = sheet0;
         }
      }

      if(dataSheet == null) {
         dataSheet = book.createSheet(sheetName + "_Data");
         // visible for editing
         // book.setSheetHidden(book.getSheetIndex(dataSheet),true);
      }

      return dataSheet;
   }

   /**
    * Add range validation for cell area.
    * @param option column option which contains the limiting imfomation of
    *  specified assembly.
    * @param firstRow the specified row index.
    * @param lastRow the specified row index.
    * @param firstCol the specified column index.
    * @param lastCol the specified column index.
    * @param optionFlag the unique option flag.
    * @param sheet the specified sheet.
    */
   public static void addRangeValid(ColumnOption option, int firstRow,
      int lastRow, int firstCol, int lastCol, String optionFlag, Sheet sheet)
   {
      addRangeValid(option, firstRow, lastRow, firstCol, lastCol, optionFlag,
         sheet, null, -1, -1, -1, null);
   }

   public static void addRangeValid(ColumnOption option, int firstRow,
                                    int lastRow, int firstCol, int lastCol, String optionFlag, Sheet sheet,
                                    PoiExcelVSExporter exporter, int startX, int startY, int icol,
                                    int[] columnPixelW)
   {
      int cellStartX = -1;
      int cellEndX = -1;

      if(columnPixelW != null) {
         int totalWidth = 0;

         for(int i = 0; i <= icol && i < columnPixelW.length; i++) {
            totalWidth += columnPixelW[i];
         }

         cellStartX = exporter.getCol(new Point(startX, startY),
            (int) (totalWidth - (icol < columnPixelW.length ?
            columnPixelW[icol] : 0)));
         cellEndX = exporter.getCol(new Point(startX, startY),
            (int) totalWidth) - 1;
      }

      int fcol = cellStartX >= 0 ? cellStartX : firstCol;
      lastCol = lastCol + (fcol - firstCol);
      firstCol = fcol;
      lastCol = cellEndX >= 0 && cellEndX >= firstCol ? cellEndX : lastCol;
      DataValidationHelper helper = sheet.getDataValidationHelper();
      DataValidationConstraint constraint = null;
      CellRangeAddressList rangList =
         new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
      int oType = DataValidationConstraint.OperatorType.BETWEEN;
      String errMsg = null;

      if(!option.isForm()) {
         for(int i = firstRow; i <= lastRow; i++) {
            Row row = sheet.getRow(i);

            if(row == null) {
               continue;
            }

            Cell cell = row.getCell((short) firstCol);

            if(cell == null) {
               continue;
            }

            Object value = getCellValue(cell);

            if(value == null) {
               continue;
            }

            addCellValid(sheet, i, firstCol, value);
         }

         return;
      }
      else if(ColumnOption.COMBOBOX.equals(option.getType())) {
         ComboBoxColumnOption comboboxOption = (ComboBoxColumnOption) option;
         String[] labels = comboboxOption.getListData().getLabels();
         Workbook book = sheet.getWorkbook();
         Sheet dataSheet =
            PoiExcelVSUtil.getDataSheet(sheet, sheet.getSheetName());
         int rowIdx = 0;
         int colIdx = 0;

         Row row = PoiExcelVSUtil.getRow(rowIdx, dataSheet);

         while(row.getCell(colIdx) != null) {
            colIdx++;
         }

         Cell titleCell =
            PoiExcelVSUtil.getCell(colIdx, PoiExcelVSUtil.getRow(rowIdx, dataSheet));
         titleCell.setCellValue(optionFlag);

         for(int j = 0; j < labels.length; j++) {
            rowIdx++;
            Cell cell = PoiExcelVSUtil.getCell(colIdx,
                                               PoiExcelVSUtil.getRow(rowIdx, dataSheet));
            cell.setCellValue(labels[j] == null ? "null" : labels[j]);
         }

         String cellRef = PoiExcelVSUtil.getCellReference(2, rowIdx + 1,
                                                          colIdx + 1, colIdx + 1);
         String sheetName = dataSheet.getSheetName();

         if(sheetName.indexOf(" ") != -1) {
            sheetName = "'" + sheetName + "'";
         }

         Name cellName = book.createName();
         cellName.setRefersToFormula(sheetName + cellRef);
         String nameStr = optionFlag + "_DATA";
         cellName.setNameName(nameStr.replace(".", "_"));

         PoiExcelVSUtil.addDropdownList(firstRow, lastRow, firstCol, lastCol,
                                        nameStr, sheet, true, option.getMessage());

         return;
      }
      else if(ColumnOption.TEXT.equals(option.getType())) {
         return;
      }
      else if(ColumnOption.DATE.equals(option.getType())) {
         DateColumnOption dOption = (DateColumnOption) option;

         String min = dOption.getMin();
         min = min == null || "".equals(min) ? "1900-1-1" : min;

         String max = dOption.getMax();
         max = max == null || "".equals(max) ? "2500-12-31" : max;

         // value must be a formula
         min = "DateValue(\"" + min + "\")";
         max = "DateValue(\"" + max + "\")";
         constraint = helper.createDateConstraint(oType, min, max, "yyyy-MM-dd");
      }
      else if(ColumnOption.INTEGER.equals(option.getType())) {
         IntegerColumnOption iOption = (IntegerColumnOption) option;

         constraint = helper.createNumericConstraint(
            DataValidationConstraint.ValidationType.INTEGER, oType,
            iOption.getMin() + "", iOption.getMax() + "");
      }
      else if(ColumnOption.FLOAT.equals(option.getType())) {
         FloatColumnOption fOption = (FloatColumnOption) option;

         String min = fOption.getMin();
         min = min == null || "".equals(min) ? -Float.MAX_VALUE + "" : min;

         String max = fOption.getMax();
         max = max == null || "".equals(max) ? Float.MAX_VALUE + "" : max;

         constraint = helper.createNumericConstraint(
            DataValidationConstraint.ValidationType.DECIMAL, oType, min, max);
      }
      else if(ColumnOption.BOOLEAN.equals(option.getType())) {
         constraint = helper.createExplicitListConstraint(
            new String[] {"TRUE", "FALSE"});
         errMsg = Catalog.getCatalog().getString("em.common.param.boolean");
      }

      DataValidation dataVali = helper.createValidation(constraint, rangList);
      dataVali.createErrorBox("Error Message",
         (errMsg != null ? errMsg : option.getMessage()));
      dataVali.setShowErrorBox(true);
      sheet.addValidationData(dataVali);
   }

   /**
    * Add data validation for each cell.
    */
   private static void addCellValid(Sheet sheet, int row, int col, Object value)
   {
      DataValidationHelper helper = sheet.getDataValidationHelper();
      DataValidationConstraint constraint =
         helper.createExplicitListConstraint(new String[] {value + ""});
      CellRangeAddressList rangList =
         new CellRangeAddressList(row, row, col, col);
      DataValidation dataVali = helper.createValidation(constraint, rangList);
      dataVali.createErrorBox("Error Message",
         Catalog.getCatalog().getString(
         "designer.design.uneditcrosstabDataFromQuery"));
      dataVali.setEmptyCellAllowed(false);
      dataVali.setSuppressDropDownArrow(true);

      sheet.addValidationData(dataVali);
   }

   /**
    * Get old row index by parsing the cell name.
    * for example: "table.1_TR"(cell name) -> 1(row index).
    */
   public static int getRowIndex(Name name) {
      String nameName = name.getNameName();
      int idx = nameName.lastIndexOf('\\');
      idx = idx > 0 ? idx : nameName.indexOf("_TR");

      if(idx == -1) {
         return -1;
      }

      int startIdx = nameName.lastIndexOf('.');

      if(startIdx > 0) {
         return Integer.parseInt(nameName.substring(startIdx + 1, idx));
      }

      return -1;
   }

   /**
    * Get specified table column option.
    */
   public static ColumnOption[] getTableOption(TableVSAssemblyInfo info) {
      ColumnSelection colSelection = info.getColumnSelection();
      int hiddenCount = colSelection.getHiddenColumnCount();
      ColumnOption[] options = new ColumnOption[colSelection.getAttributeCount() - hiddenCount];
      Enumeration e = colSelection.getAttributes();

      int i = 0;

      while(e.hasMoreElements()) {
         DataRef ref = (DataRef) e.nextElement();

         if(ref instanceof ColumnRef && !((ColumnRef) ref).isVisible()) {
            continue;
         }
         else if(ref instanceof FormRef) {
            options[i] = ((FormRef) ref).getOption();
         }
         else {
            options[i] = null;
         }

         i++;
      }

      return options;
   }

   /**
    * Calculate the pixel column widths.
    */
   public static int[] calculateColumnWidths(Viewsheet vs,
      TableDataVSAssemblyInfo info, VSTableLens lens, boolean isFillColumns,
      boolean matchLayout, boolean needDistributeWidth)
   {
      int totalWidth = 0;
      int totalPixelW = info.getPixelSize().width;
      int lensColumnCount = lens == null ? 0 : lens.getColCount();
      int[] ws = new int[lensColumnCount];
      int[] widths = lens == null ? new int[0] : lens.getColumnWidths();
      int firstTrunkCol = -1;

      // get user set column widths
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, lens);

         if(Double.isNaN(w) && widths != null && i < widths.length) {
            w = widths[i];

            // @by ankitmathur, 4-09-2015, track the column which is truncated
            // due to the size of the Assembly.
            if(firstTrunkCol < 0 && totalWidth + (int) w > totalPixelW) {
               firstTrunkCol = i - 1;
            }
         }
         else if(Double.isNaN(w)) {
            w = DEFAULT_COLWIDTH;
         }

         ws[i] = (int) w;
         totalWidth += w;
      }

      // if totalwidth expand the table pixel width, reset the cell width
      // fixed bug #29975 :In fact, it didn't expand totalPixelW when export html.
      if(!matchLayout && totalWidth > totalPixelW) {
         totalWidth = 0;
      }

      // distribute width
      if(totalWidth > 0) {
         if(!needDistributeWidth) {
            int[] nws = null;

            if(totalWidth < totalPixelW) {
               int remainWidth = totalPixelW - totalWidth;
               // For bug1418289518567, add the remaining width to the last
               // column for Tables as well as Crosstabs.
               if((info instanceof TableVSAssemblyInfo ||
                  info instanceof CalcTableVSAssemblyInfo ||
                  // @by yanie: bug1416909636463
                  // Crosstab in viewsheet is changed to be fill-columns, modify
                  // export logic accordingly
                  info instanceof CrosstabVSAssemblyInfo) && !info.isShrink())
               {
                  // @by ankitmathur, 4-09-2015, totalWidth represents the size
                  // of the table assembly. If the table has horizontal scroll
                  // bars, we need to set the width of the truncated column
                  // (last visible column within the assembly bounds) to the
                  // width of the next Column. If the table does not contain
                  // scrollbars, then the last column will need to expand to
                  // the remaining assembly width.
                  if(firstTrunkCol >= 0) {
                     int trunkColSize = ws[firstTrunkCol];
                     ws[firstTrunkCol] = AssetUtil.defw;

                     if(ws[ws.length - 1] == DEFAULT_COLWIDTH) {
                        ws[ws.length - 1] += trunkColSize;
                     }
                  }
                  // don't expand if set explicitly to 0
                  else if(ws[ws.length - 1] > 0){
                     ws[ws.length - 1] = ws[ws.length - 1] + remainWidth;
                  }
               }
               else if(!info.isShrink()) {
                  List<Integer> remainColWidth = new ArrayList<>();

                  for(int i = totalPixelW / AssetUtil.defw; i >= 0; i--) {
                     if(remainWidth > 0 &&
                        remainWidth >= AssetUtil.defw)
                     {
                        remainColWidth.add(AssetUtil.defw);
                        remainWidth -= AssetUtil.defw;
                     }
                     else if(remainWidth > 0 &&
                        remainWidth < AssetUtil.defw)
                     {
                        remainColWidth.add(remainWidth);
                        break;
                     }
                  }

                  nws = new int[ws.length + remainColWidth.size()];
                  System.arraycopy(ws, 0, nws, 0, ws.length);

                  for(int i = ws.length; i < nws.length; i++) {
                     nws[i] = remainColWidth.get(i - ws.length);
                  }
               }
            }
            // Account for the remaining columns only if "Match Layout"
            // export option is selected.
            else if(totalWidth > totalPixelW && matchLayout) {
               java.util.List<Integer> wsList = new ArrayList<>();
               int temp = 0;

               for(int width : ws) {
                  temp += width;

                  if(temp > totalPixelW) {
                     wsList.add(width - (temp - totalPixelW));
                     break;
                  }

                  wsList.add(width);
               }

               nws = new int[wsList.size()];

               for(int i = 0; i < wsList.size(); i++) {
                  nws[i] = wsList.get(i);
               }
            }

            // For bug1418289518567, For Crosstabs we need to accommodate the
            // for the "extra" columns determined by the Viewsheet grid. Since
            // the width of the last column of the Crosstab will have been
            // expanded to cover this area, we can set the width value to 0 for the
            // extra columns.
            // 1-06-2015, adding logic for Freehand tables as well because
            // the change for bug1418832574870, now makes Freehand tables
            // behave the same as Crosstabs.
            if((info instanceof CrosstabVSAssemblyInfo ||
               info instanceof CalcTableVSAssemblyInfo) &&
               nws == null && totalPixelW > totalWidth)
            {
               nws = new int[ws.length + ((totalPixelW - totalWidth) / AssetUtil.defw)];
               System.arraycopy(ws, 0, nws, 0, ws.length);

               for(int i = ws.length; i < nws.length; i++) {
                  nws[i] = 0;
               }
            }

            return nws != null ? nws : ws;
         }

         int consumed = 0;

         for(int i = 0; i < ws.length; i++) {
            ws[i] = ws[i] * totalPixelW / totalWidth;
            consumed += ws[i];
         }

         // rounding error
         ws[ws.length - 1] += totalPixelW - consumed;
      }
      // truncate to fit assembly width
      else {
         for(int i = 0; i < ws.length; i++) {
            double w = info.getColumnWidth2(i, lens);

            if(Double.isNaN(w) && widths != null && i < widths.length) {
               w = widths[i];
            }
            else if(Double.isNaN(w)) {
               w = DEFAULT_COLWIDTH;
            }

            if(totalWidth + w > totalPixelW) {
               w = totalPixelW - totalWidth;
               ws[i] = (int) w;
               break;
            }
            else {
               ws[i] = (int) w;
               totalWidth += w;
            }
         }
      }

      return ws;
   }

   /**
    * Calculate the pixel column widths.
    */
   public static int[] calculateRowHeights(Viewsheet vs,
      TableDataVSAssemblyInfo info, VSTableLens lens,
      boolean matchLayout, boolean needDistributeWidth)
   {
      int totalHeight = 0;
      int totalPixelH = info.getPixelSize().height;
      int lensRowCount = lens == null ? 0 : lens.getRowCount();
      int[] hs = new int[lensRowCount];
      int[] heights = lens == null ? new int[0] : lens.getRowHeights();

      // get user set column widths
      for(int i = 0; i < hs.length; i++) {
         int h = 0;

         if(i >= heights.length) {
            h = AssetUtil.defh;
         }
         else {
            h = lens.getWrappedHeight(i, true);
         }

         if(Double.isNaN(h) && heights != null && i < heights.length) {
            h = heights[i];
         }
         else if(Double.isNaN(h)) {
            h = DEFAULT_ROWWIDTH;
         }

         hs[i] = (int) h;
         totalHeight += h;
      }

      // if totalwidth expand the table pixel width, reset the cell width
      if(!matchLayout && totalHeight > totalPixelH) {
         totalHeight = 0;
      }

      return hs;
   }

   /**
    * Set align for all the region.
    */
   private static void setCellAlignments(XSSFFormatRecord record,
                                         VSCompositeFormat format)
   {
      if(format != null) {
         record.setWrapText(format.isWrapping());
         record.setAlignment(getHorizontalAlign(format.getAlignment()));
         record.setVerticalAlignment(getVerticalAlign(format.getAlignment()));
      }
      else {
         record.setAlignment(HorizontalAlignment.LEFT);
         record.setVerticalAlignment(VerticalAlignment.TOP);
      }
   }

   /**
    * Get the max number of rows to allow in sheet.
    */
   public static int getSheetMaxRows() {
      String maxRowsStr = SreeEnv.getProperty("excel.sheet.maxrows");
      return maxRowsStr != null ? Integer.parseInt(maxRowsStr) : -1;
   }

   /**
    * Try adjust overlapped bounds to avoid overlapping for elements placed on grid.
    */
   public static void processOverlap(Viewsheet vsheet) {
      List<Assembly> assemblies = Arrays.stream(vsheet.getAssemblies())
         .filter(obj -> {
            if(obj instanceof TextVSAssembly) {
               return false;
            }
            else {
               return true;
            }
         })
         .filter(obj -> obj instanceof TextInputVSAssembly ||
            obj instanceof TableDataVSAssembly)
         .filter(obj -> vsheet.isVisible(obj, AbstractSheet.SHEET_RUNTIME_MODE))
         .collect(Collectors.toList());

      for(int i = 0; i < assemblies.size(); i++) {
         Assembly assembly = assemblies.get(i);
         Rectangle bounds = assembly.getBounds();

         for(int j = i + 1; j < assemblies.size(); j++) {
            Rectangle bounds2 = assemblies.get(j).getBounds();

            if(bounds.intersects(bounds2)) {
               int top1 = bounds.y;
               int bottom1 = bounds.y + bounds.height;
               int top2 = bounds2.y;
               int bottom2 = bounds2.y + bounds2.height;
               int left1 = bounds.x;
               int right1 = bounds.x + bounds.width;
               int left2 = bounds2.x;
               int right2 = bounds2.x + bounds2.width;
               int hoverlap = 0;
               int voverlap = 0;

               if(top1 < top2 && bottom1 > top2) {
                  voverlap = bottom1 - top2;
               }
               else if(top2 < top1 && bottom2 > top1) {
                  voverlap = bottom2 - top1;
               }

               if(left1 < left2 && right1 > left2) {
                  hoverlap = right1 - left2;
               }
               else if(left2 < left1 && right2 > left1) {
                  hoverlap = right2 - left1;
               }

               if(voverlap <= hoverlap) {
                  if(top1 < top2 && bottom1 > top2) {
                     int half = (int) Math.ceil((bottom1 - top2) / 2.0);
                     bottom1 -= half;
                     top2 += half;
                  }
                  else if(top2 < top1 && bottom2 > top1) {
                     int half = (int) Math.ceil((bottom2 - top1) / 2.0);
                     bottom2 -= half;
                     top1 += half;
                  }
               }
               else {
                  if(left1 < left2 && right1 > left2) {
                     int half = (int) Math.ceil((right1 - left2) / 2.0);
                     right1 -= half;
                     left2 += half;
                  }
                  else if(left2 < left1 && right2 > left1) {
                     int half = (int) Math.ceil((right2 - left1) / 2.0);
                     right2 -= half;
                     left1 += half;
                  }
               }

               assembly.setPixelOffset(new Point(left1, top1));
               assembly.setPixelSize(new Dimension(right1 - left1, bottom1 - top1));

               assemblies.get(j).setPixelOffset(new Point(left2, top2));
               assemblies.get(j).setPixelSize(new Dimension(right2 - left2, bottom2 - top2));
            }
         }
      }

      Arrays.stream(vsheet.getAssemblies())
         .filter(obj -> obj instanceof Viewsheet)
         .forEach(vs -> processOverlap((Viewsheet) vs));
   }

   private static Map<Integer, BorderStyle> borderMap = new HashMap<>();

   // horizontal mapping. H_LEFT(1) -- ALIGN_LEFT(1);
   // H_CENTER(2) -- ALIGN_CENTER(2); H_RIGHT(4) -- ALIGN_RIGHT(3)
   private static Map<Integer, HorizontalAlignment> horizontalMap = new HashMap<>(5);

   // vertical mapping. V_TOP(8) -- VERTICAL_TOP(0);
   // V_CENTER(16) -- VERTICAL_CENTER(1); V_BOTTOM(32) -- VERTICAL_BOTTOM(2);
   private static Map<Integer, VerticalAlignment> verticalMap = new HashMap<>(5);

   static {
      // borderMap
      borderMap.put(StyleConstants.NO_BORDER, BorderStyle.NONE);
      borderMap.put(StyleConstants.THIN_LINE, BorderStyle.THIN);
      borderMap.put(StyleConstants.MEDIUM_LINE, BorderStyle.MEDIUM);
      borderMap.put(StyleConstants.DASH_LINE, BorderStyle.DASHED);
      borderMap.put(StyleConstants.THICK_LINE, BorderStyle.THICK);
      borderMap.put(StyleConstants.DOUBLE_LINE, BorderStyle.DOUBLE);
      borderMap.put(StyleConstants.DOT_LINE, BorderStyle.DOTTED);
      borderMap.put(StyleConstants.MEDIUM_DASH, BorderStyle.MEDIUM_DASHED);
      borderMap.put(StyleConstants.LARGE_DASH, BorderStyle.MEDIUM_DASHED);

      // horizontalMap
      horizontalMap.put(StyleConstants.H_LEFT, HorizontalAlignment.LEFT);
      horizontalMap.put(StyleConstants.H_CENTER, HorizontalAlignment.CENTER);
      horizontalMap.put(StyleConstants.H_RIGHT, HorizontalAlignment.RIGHT);

      // verticalMap
      verticalMap.put(StyleConstants.V_TOP, VerticalAlignment.TOP);
      verticalMap.put(StyleConstants.V_CENTER, VerticalAlignment.CENTER);
      verticalMap.put(StyleConstants.V_BOTTOM, VerticalAlignment.BOTTOM);
   }

   private static int[][] lineMap = new int[][] {
      {StyleConstants.NO_BORDER, EXCEL_NO_BORDER},
      {StyleConstants.THIN_LINE, EXCEL_SOLID_BORDER},
      {StyleConstants.MEDIUM_LINE, EXCEL_SOLID_BORDER},
      {StyleConstants.DASH_LINE, STPresetLineDashVal.INT_SOLID},
      {StyleConstants.THICK_LINE, EXCEL_SOLID_BORDER},
      {StyleConstants.DOUBLE_LINE, EXCEL_SOLID_BORDER},
      {StyleConstants.DOT_LINE, STPresetLineDashVal.INT_DOT},
      {StyleConstants.MEDIUM_DASH, STPresetLineDashVal.INT_LG_DASH_DOT},
      {StyleConstants.LARGE_DASH, STPresetLineDashVal.INT_LG_DASH_DOT}
   };

   private static final Logger LOG =
      LoggerFactory.getLogger(PoiExcelVSUtil.class);

   private static final short EXCEL_COLUMN_WIDTH_FACTOR = 256;
   private static final int EXCEL_COLUMN_WIDTH_LIMIT = 255 * EXCEL_COLUMN_WIDTH_FACTOR;
   private static final int UNIT_OFFSET_LENGTH = 7;
   private static final int[] UNIT_OFFSET_MAP = new int[] { 0, 36, 73, 109, 146, 182, 219 };
}
