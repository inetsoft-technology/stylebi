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
package inetsoft.report.io.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSUtil;
import inetsoft.report.io.viewsheet.excel.XSSFFormatRecord;
import inetsoft.report.lens.SubTableLens;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.*;
import java.awt.Dimension;
import java.text.Format;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

/**
 * WSTable exporting helper.
 *
 * @version 8.5, 11/24/2006
 * @author InetSoft Technology Corp
 */
public class WSTableHelper {
   /**
    * Constructor.
    */
   public WSTableHelper(Workbook book, Sheet sheet) {
      this.sheet = sheet;
      this.book = book;
   }

   /**
    * Calculate the columns start position and width.
    * @param lens the source table lens object
    * @param colInfo column information
    */
   protected int calculateColumnsPosition(TableLens lens, List<ColumnInfo> colInfo) {
      int count = lens == null ? 1 : lens.getColCount();
      columnStarts = new int[count];
      columnWidths = new int[count];
      Arrays.fill(columnWidths, 70);
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);

      for(ColumnInfo info : colInfo) {
         int col = Util.findColumn(columnIndexMap, info, lens);

         if(col >= 0) {
            columnWidths[col] = info.getWidth();
         }
      }

      for(int icol = 0; icol < count; icol++) {
         for(int i = 0; i < icol; i++) {
            columnStarts[icol] += columnWidths[i];
         }

         totalColumnWidth += columnWidths[icol];
      }

      return totalColumnWidth;
   }

   /**
    * Write the data for table assembly.
    * @param lens the source table lens
    * @param colInfo column information
    */
   public void writeData(TableLens lens, List<ColumnInfo> colInfo, Class[] colTypes, Map<Integer,Integer> map) {
      calculateColumnsPosition(lens, colInfo);
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();
      SparseMatrix isWritten = new SparseMatrix();

      for(int irow = 0; irow < rowCount; irow++) {
         for(int icol = 0; icol < colCount; icol++) {
            Integer val = map.get(icol);

            if(!("T".equals(isWritten.get(irow, icol))) &&
               (val != null || map.size() == 0))
            {
               Dimension bounds;

               if(val != null) {
                  bounds = new Dimension(colInfo.get(val).getWidth(), 1);
               }
               else {
                  bounds = new Dimension(1, 1);
               }

               Object val0 = lens.getObject(irow, icol);
               String fmt = ExportUtil.getCellFormat(lens, irow, icol, false);
               Object obj = ExportUtil.getObject(lens, irow, icol, fmt, true);

               if(val0 instanceof DCMergeDatesCell) {
                  val0 = ((DCMergeDatesCell) val0).getFormatedOriginalDate();
               }

               if(obj instanceof DCMergeDatesCell) {
                  obj = ((DCMergeDatesCell) obj).getFormatedOriginalDate();
               }

               writeTableCell(bounds, irow, icol, val0, obj,
                              colTypes[icol], fmt, sheet, book);
            }
         }
      }
   }

   /**
    * Implementation of the abstract method for writing crosstab cells to Excel.
    * @param bounds the Dimension of the cell.
    * @param irow the cell's row.
    * @param icol the cell's col.
    * @param val the cell's value to be displayed.
    * @param obj the cell's rich text format object to be displayed.
    * @param fmt the cell's  format for displayed object.
    * @param sheet the Sheet to write the TableCell.
    * @param book the Workbook of the sheet.
    */
   private void writeTableCell(Dimension bounds, int irow, int icol,
                               Object val, Object obj, Class ctype,
                               String fmt, Sheet sheet, Workbook book)
   {
      CellRangeAddress cellRange = new CellRangeAddress(irow,
         irow + bounds.height - 1, icol, icol + bounds.width - 1);
      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);
      Row row = PoiExcelVSUtil.getRow(irow, sheet);
      Cell cell = PoiExcelVSUtil.getCell(icol, row);
      setCellValue(cell, val, obj, ctype, fmt, book);
   }

   /**
    * Set the cell formated value.
    */
   private void setCellValue(Cell cell, Object val, Object obj,
      Class ctype, String fmt, Workbook book)
   {
      boolean number = (ctype != null && Number.class.isAssignableFrom(ctype));

      if(obj == null || obj instanceof String) {
         if(number || (val instanceof Number)) {
            if(val instanceof Number) {
               Number nval = (Number) val;
               cell.setCellValue(nval.doubleValue());
            }
            else if(val != null) {
               String text = val == null ? "" : val.toString();
               RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                           Tool.convertHTMLSymbol(text));
               cell.setCellValue(hrText);
            }
         }
         else {
            String text = AssetUtil.format(val);
            RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                        Tool.convertHTMLSymbol(text));
            cell.setCellValue(hrText);
         }

         return;
      }

      String text = AssetUtil.format(val);
      RichTextString hrText = PoiExcelVSUtil.createRichTextString(book,
                                                                  Tool.convertHTMLSymbol(text));

      // Bug #58461, Excel changes any digit values past 15 to zero.
      // To prevent loss of information, switch the format to text.
      if(number && "@".equals(fmt) && text.length() > 15) {
         PoiExcelVSUtil.setCellValue(cell, text, hrText);
      }
      else {
         PoiExcelVSUtil.setCellValue(cell, obj, hrText);
      }

      if(obj instanceof java.sql.Time) {
         fmt = Tool.DEFAULT_TIME_PATTERN;
      }

      if(fmt == null && (obj instanceof Date) && ctype != null &&
         java.sql.Timestamp.class.isAssignableFrom(ctype))
      {
         fmt = Tool.DEFAULT_DATETIME_PATTERN;
      }

      if(fmt == null && (obj instanceof Date)) {
         fmt = "yyyy-MM-dd";
      }

      if(fmt != null) {
         XSSFFormatRecord record = new XSSFFormatRecord();
         DataFormat df = book.createDataFormat();

         record.setDataFormat(df.getFormat(fmt));
         cell.setCellStyle(PoiExcelVSUtil.createCellStyle(book, record, styleCache));
      }
   }

   private Sheet sheet;
   private Workbook book;
   private int[] columnStarts = null;
   private int[] columnWidths = null;
   private int totalColumnWidth = 0;
   private Map<XSSFFormatRecord, XSSFCellStyle> styleCache = new HashMap<>();
}
