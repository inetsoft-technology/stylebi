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
import inetsoft.util.Catalog;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * The class is exporting to excel worksheet.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class LargeDataExcelWSExporter implements WSExporter {
   /**
    * Constructor.
    */
   public LargeDataExcelWSExporter() {
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
                            int rowCount, int colCount) throws Exception
   {
      double pageMaxRow = Math.ceil((WSExporter.getPageMaxCellCount() * 1.0) / colCount);

      if(pageMaxRow > 65530) {
         pageMaxRow = 65530;
      }

      ExcelExporter ec = new ExcelExporter((int) pageMaxRow);
      ec.setUp();
      this.book = ec.getWorkbook();
      this.ec = ec;

      int pages = Math.max(1, (int) Math.ceil(rowCount / pageMaxRow));
      int start = 0;
      int remain = rowCount;

      for(int i = 0; i < pages; i++) {
         int rcnt = Math.min((int) pageMaxRow, remain);
         String sname = sheetName + (i == 0 && pages == 1 ? "" : i);
         prepareSheet0(sname, start, rcnt, colCount);
         start += rcnt;
         remain -= rcnt;
      }
   }

   private void prepareSheet0(String sheetName, int start, int rcnt, int colCount) {
      SheetInfo sheetInfo = createSheetInfo(sheetName, start, rcnt, colCount);
      sheetInfos.add(sheetInfo);
   }

   /**
    * Create a workbook and a sheet, set the format of sheet.
    * @param sheetName the specified sheet name.
    */
   private SheetInfo createSheetInfo(String sheetName, int from, int rcnt, int ccnt) {
      sheetName = sheetName.indexOf("/") != -1 ?
         sheetName.replaceAll("/", "_") : sheetName;

      return new SheetInfo(sheetName, from, rcnt, ccnt);
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
            Sheet sheet = createSheet(sheetInfo);
            WSTableHelper helper = new WSTableHelper(book, sheet);
            TableLens sub = new SubTableLens(lens, sheetInfo.start, 0,
               sheetInfo.rcnt, lens.getColCount());
            helper.writeData(sub, cinfos, colTypes, colMap);
            int num = sheet.getNumMergedRegions();

            for(int j = num - 1; num > 0 && j >= 0; j--) {
               sheet.removeMergedRegion(j);
            }

            if(sheet instanceof SXSSFSheet) {
               ((SXSSFSheet) sheet).flushRows();
            }
         }
         catch(Exception e) {
            LOG.error("Failed to write table", e);
         }
      }
   }

   private void setColumnWidthsAsync(Sheet sheet, int colnum) {
      ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      short defWidth = (short) (AssetUtil.defw * ExcelVSUtil.EXCEL_PIXEL_WIDTH_FACTOR);
      CompletableFuture<Void>[] futures = new CompletableFuture[colnum];

      for(int i = 0; i < colnum; i++) {
         final int columnIndex = i;
         futures[i] = CompletableFuture.runAsync(() -> {
            sheet.setColumnWidth((short) columnIndex, defWidth);
         }, executor);
      }

      CompletableFuture.allOf(futures).join();
      executor.shutdown();
   }

   private Sheet createSheet(SheetInfo sheetInfo) {
      Sheet sheet = book.createSheet(sheetInfo.sheetName);
      int start = sheetInfo.start;
      int rcnt = sheetInfo.rcnt;
      int colCount = sheetInfo.ccnt;
      int colnum = Math.max(colCount, 0);
      setColumnWidthsAsync(sheet, colnum);
      int adjust = 0;

      // create header row
      if(start != 0) {
         adjust = 1;
         Row row = sheet.createRow(0);
         row.setHeight((short) (AssetUtil.defh * ExcelVSUtil.EXCEL_PIXEL_HEIGHT_FACTOR));

         for(int j = 0; j < colnum; j++) {
            row.createCell((short) j);
         }
      }

      for(int i = start; i < start + rcnt - 1; i++) {
         Row row = sheet.createRow(i - start + adjust);
         row.setHeight((short) (AssetUtil.defh * ExcelVSUtil.EXCEL_PIXEL_HEIGHT_FACTOR));

         for(int j = 0; j < colnum; j++) {
            row.createCell((short) j);
         }
      }

      return sheet;
   }

   private static class SheetInfo {
      public SheetInfo(String sheetName, int start, int rcnt, int ccnt) {
         this.sheetName = sheetName;
         this.start = start;
         this.rcnt = rcnt;
         this.ccnt = ccnt;
      }

      private int start;
      private int rcnt;
      private int ccnt;
      private String sheetName;
   }

   protected ExcelContext ec;
   protected Workbook book;
   private ArrayList<SheetInfo> sheetInfos = new ArrayList();

   private static final Logger LOG =
      LoggerFactory.getLogger(LargeDataExcelWSExporter.class);
}