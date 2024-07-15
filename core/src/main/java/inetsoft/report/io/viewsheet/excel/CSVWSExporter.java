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

import java.io.*;
import java.util.List;

/**
 * The class exports data tables to csv files.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class CSVWSExporter implements WSExporter {
   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    * @param out the specified OutputStream.
    */
   @Override
   public void write(OutputStream output) throws IOException {
      String delim = ",";
      String prop = SreeEnv.getProperty("export.csv.delimiter");

      if(prop != null) {
         delim = prop;
      }

      try {
         CSVUtil.writeTableDataAssembly(table, output, delim);
      }
      catch(UnsupportedEncodingException ex) {
         throw new IOException(ex);
      }
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
   }

   /**
    * Write table assembly.
    * @param lens the specified VSTableLens.
    * @param cinfos the specified list of column infos
    */
   @Override
   public void writeTable(TableLens lens, List<ColumnInfo> cinfos) {
      this.table = lens;
   }

   private TableLens table;
}
