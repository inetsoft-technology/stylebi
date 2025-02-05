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
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.internal.ColumnInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The class defines the API for exporting a data table.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public interface WSExporter {
   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    * @param out the specified OutputStream.
    */
   public void write(OutputStream out) throws IOException;

   /**
    * Specify the size and cell size of sheet.
    * @param sheetName the specified sheet name.
    * @param rws the RuntimeWorksheet to be exported.
    * @param row the specified row count.
    * @param col the specified col count.
    */
   public void prepareSheet(String sheetName, RuntimeWorksheet rws,
                            int row, int col) throws Exception;

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   public void writeTable(TableLens lens, List<ColumnInfo> cinfos, Class[] colTypes, Map<Integer, Integer> colMap);

   /**
    * Get the max cell count of per page.
    */
   static int getPageMaxCellCount() {
      String maxCountStr = SreeEnv.getProperty("ws.export.page.max.cell");
      int defaultMaxCellCount = 500000;

      try {
         int maxCount = Integer.parseInt(maxCountStr);

         return maxCount > 0 ? maxCount : defaultMaxCellCount;
      }
      catch(Exception e) {
         return defaultMaxCellCount;
      }
   }

   default Class[] getColTypes(TableLens lens) {
      int colCount = lens.getColCount();
      final Class[] colTypes = new Class[colCount];
      ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      CompletableFuture<Void>[] futures = new CompletableFuture[colCount];

      for(int i = 0; i < colCount; i++) {
         final int colIdx = i;
         futures[i] = CompletableFuture.runAsync(() -> {
            colTypes[colIdx] = lens.getColType(colIdx);
         }, executor);
      }

      CompletableFuture.allOf(futures).join();
      executor.shutdown();

      return colTypes;
   }
}
