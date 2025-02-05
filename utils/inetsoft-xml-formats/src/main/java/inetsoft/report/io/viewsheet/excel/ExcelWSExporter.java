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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.io.viewsheet.WSTableHelper;
import inetsoft.report.lens.SubTableLens;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * The class is exporting to excel worksheet.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class ExcelWSExporter implements WSExporter {
   /**
    * Constructor.
    */
   public ExcelWSExporter() {
      ExcelExporter ec = new ExcelExporter();
      ec.setUp();
      this.book = ec.getWorkbook();
      this.ec = ec;
   }

   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    * @param out the specified OutputStream.
    */
   @Override
   public void write(OutputStream out) throws IOException {
      // write out
      book.write(out);
      // stream should be closed here because all have been written already.
      out.close();
   }

   /**
    * Specify the size and cell size of sheet.
    * @param sheetName the specified sheet name.
    * @param rws the RuntimeWorksheet to be exported.
    * @param rowCount the specified row count.
    * @param colCount the specified col count.
    */
   @Override
   public void prepareSheet(String sheetName, RuntimeWorksheet rws,
                            int rowCount, int colCount) throws Exception {
      int pages = Math.max(1, (int) Math.ceil(rowCount / 65530.0));
      int start = 0;
      int remain = rowCount;

      for(int i = 0; i < pages; i++) {
         int rcnt = Math.min(65530, remain);
         String sname = sheetName + (i == 0 && pages == 1 ? "" : i);
         prepareSheet0(sname, sheetName, rws, start, rcnt, colCount);
         start += rcnt;
         remain -= rcnt;
      }
   }

   private void prepareSheet0(String sheetName, String tname,
                              RuntimeWorksheet rws,
                              int start, int rcnt, int colCount)
      throws Exception
   {
      SheetInfo sheetInfo = createSheetInfo(sheetName, start, rcnt);
      sheetInfos.add(sheetInfo);

      int colnum = Math.max(colCount, 0);

      for(int i = 0; i < colnum; i++) {
         sheetInfo.sheet.setColumnWidth((short) i,
            (short) (AssetUtil.defw * ExcelVSUtil.EXCEL_PIXEL_WIDTH_FACTOR));
      }

      int adjust = 0;

      // create header row
      if(start != 0) {
         adjust = 1;
         Row row = sheetInfo.sheet.createRow(0);
         row.setHeight((short) (AssetUtil.defh * ExcelVSUtil.EXCEL_PIXEL_HEIGHT_FACTOR));

         for(int j = 0; j < colnum; j++) {
            Cell cell = row.createCell((short) j);
         }
      }

      for(int i = start; i < start + rcnt - 1; i++) {
         Row row = sheetInfo.sheet.createRow(i - start + adjust);
         row.setHeight((short) (AssetUtil.defh * ExcelVSUtil.EXCEL_PIXEL_HEIGHT_FACTOR));

         for(int j = 0; j < colnum; j++) {
            Cell cell = row.createCell((short) j);
         }
      }
   }

   /**
    * Create a workbook and a sheet, set the format of sheet.
    * @param sheetName the specified sheet name.
    */
   private SheetInfo createSheetInfo(String sheetName, int from, int rcnt)
      throws Exception
   {
      sheetName = sheetName.indexOf("/") != -1 ? sheetName.replaceAll("/", "_") : sheetName;
      Sheet sheet = book.createSheet(sheetName);
      XSSFDrawing patriarch = (XSSFDrawing) sheet.createDrawingPatriarch();
      return new SheetInfo(sheet, patriarch, from, rcnt);
   }

   /**
    * Write table assembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   public void writeTable(TableLens lens, List<ColumnInfo> cinfos, Class[] colTypes, Map<Integer, Integer> colMap) {
      for(int i = 0; i < sheetInfos.size(); i++) {
         SheetInfo sheetInfo = sheetInfos.get(i);

         try {
            WSTableHelper helper = new WSTableHelper(book, sheetInfo.sheet);
            TableLens sub = new SubTableLens(lens, sheetInfo.start, 0,
               sheetInfo.rcnt, lens.getColCount());
            helper.writeData(sub, cinfos, colTypes, colMap);
            int num = sheetInfo.sheet.getNumMergedRegions();

            for(int j = num - 1; num > 0 && j >= 0; j--) {
               sheetInfo.sheet.removeMergedRegion(j);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to write table", e);
         }
      }
   }

   private static class SheetInfo {
      public SheetInfo(Sheet sheet, XSSFDrawing patriarch,
                       int start, int rcnt)
      {
         this.sheet = sheet;
         this.patriarch = patriarch;
         this.start = start;
         this.rcnt = rcnt;
      }

      private Sheet sheet;
      private XSSFDrawing patriarch;
      private int start;
      private int rcnt;
   }

   protected ExcelContext ec;
   protected Workbook book;
   private ArrayList<SheetInfo> sheetInfos = new ArrayList();

   private static final Logger LOG =
      LoggerFactory.getLogger(ExcelWSExporter.class);
}
