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
import inetsoft.uql.asset.internal.ColumnInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

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
   public void writeTable(TableLens lens, List<ColumnInfo> cinfos);
}
