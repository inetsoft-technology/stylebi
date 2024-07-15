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

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Encapsulate the workbook and related resources, to implement ExcelContext.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class ExcelExporter implements ExcelContext {
   /**
    * Get work book.
    * If other people got workbook, they can made uncontrollerable change to it.
    * @return the specified Workbook created in setUp method.
    */
   @Override
   public Workbook getWorkbook() {
      return book;
   }

   /**
    * Create a workbook.
    */
   public boolean setUp() {
      book = new XSSFWorkbook();

      return true;
   }

   private Workbook book = null;
}
