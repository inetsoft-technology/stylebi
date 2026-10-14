/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.util.filereader;

import inetsoft.test.*;
import inetsoft.uql.text.TextOutput;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78526: the wiz agent's {@code importExcel} reports a column cap by comparing the header
 * it read with the column limit against one read without it ({@code colLimit} -1, on a fresh
 * {@link TextOutput}). These pin the reader behaviour that comparison relies on: the XLSX reader
 * caps at the limit and counts the sheet's full cell range when uncapped, and the XLS reader
 * ignores the limit altogether.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class ExcelImportColumnLimitTest {
   @Test
   void xlsxHeaderWiderThanTheLimitIsCappedAndCountedInFullWithoutIt() throws Exception {
      byte[] bytes = workbook(true, 210, 210);

      assertEquals(200, headerWidth(new XLSXFileReader(), bytes, 200));
      assertEquals(210, headerWidth(new XLSXFileReader(), bytes, -1));
   }

   /**
    * The XLSX reader sizes the table from the sheet's dimension, the range of every cell, so one
    * wide data row under a narrow header imports as wide columns too: 200 of 250 kept.
    */
   @Test
   void xlsxWideDataRowUnderANarrowHeaderCountsTheWholeCellRange() throws Exception {
      byte[] bytes = workbook(true, 10, 250);

      assertEquals(200, headerWidth(new XLSXFileReader(), bytes, 200));
      assertEquals(250, headerWidth(new XLSXFileReader(), bytes, -1));
   }

   @Test
   void xlsIgnoresTheLimit() throws Exception {
      byte[] bytes = workbook(false, 210, 210);

      assertEquals(210, headerWidth(new XLSFileReader(), bytes, 200));
      assertEquals(210, headerWidth(new XLSFileReader(), bytes, -1));
   }

   /** The same header/body layout {@code WorksheetAgentController.importExcel} reads with. */
   private static int headerWidth(ExcelFileReader reader, byte[] bytes, int colLimit)
      throws Exception
   {
      TextOutput output = new TextOutput();
      output.setHeaderInfo(info(0, 0));
      output.setBodyInfo(info(1, -1));
      return reader.importHeader(new ByteArrayInputStream(bytes), "UTF-8", output, 0, colLimit)
         .getChildCount();
   }

   private static ExcelFileInfo info(int startRow, int endRow) {
      ExcelFileInfo info = new ExcelFileInfo();
      info.setSheet(SHEET);
      info.setStartRow(startRow);
      info.setEndRow(endRow);
      info.setStartColumn(0);
      info.setEndColumn(-1);
      return info;
   }

   /** A header row of {@code headers} cells and one data row of {@code rowCells} cells. */
   private static byte[] workbook(boolean xlsx, int headers, int rowCells) throws Exception {
      try(Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
          ByteArrayOutputStream out = new ByteArrayOutputStream())
      {
         Sheet sheet = workbook.createSheet(SHEET);
         Row header = sheet.createRow(0);
         Row data = sheet.createRow(1);

         for(int c = 0; c < headers; c++) {
            header.createCell(c).setCellValue("C" + (c + 1));
         }

         for(int c = 0; c < rowCells; c++) {
            data.createCell(c).setCellValue("v" + c);
         }

         workbook.write(out);
         return out.toByteArray();
      }
   }

   private static final String SHEET = "Data";
}
