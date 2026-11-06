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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ExtendedDecimalFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.text.*;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79581: the "Current View.&lt;table&gt;" data sheet is built from a lens wrapped by
 * VSFormatTableLens, which turns a formatted number/date into its display text. The data sheet
 * must still hold typed cells (number / date) whose Excel data format reproduces the display
 * text, while text formats stay text. Unlike {@link ExcelDataTabCellTypeTest} this uses the real
 * format layer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelDataTabFormatTest {
   private static final int C_CURRENCY = 0;
   private static final int C_K_SCALED = 1;
   private static final int C_PERCENT = 2;
   private static final int C_DATE_FORMAT = 3;
   private static final int C_DATE_PLAIN = 4;
   private static final int C_DATE_1850 = 5;
   private static final int C_MESSAGE = 6;
   private static final int C_NULL_CELL = 7;
   private static final int C_NUMBER_PLAIN = 8;
   private static final int C_DATE_1850_FORMAT = 9;
   private static final int COLS = 10;

   private XSSFWorkbook book;
   private Locale oldLocale;

   @BeforeEach
   void setUp() {
      oldLocale = Locale.getDefault();
      book = new XSSFWorkbook();
   }

   @AfterEach
   void tearDown() throws Exception {
      Locale.setDefault(oldLocale);
      book.close();
   }

   @Test
   void currencyFormatIsNumberWithCurrencyPattern() {
      Cell cell = write(true).getRow(1).getCell(C_CURRENCY);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals(20250.0, cell.getNumericCellValue());
      assertEquals("\"$\"#,##0.00", cell.getCellStyle().getDataFormatString());
      assertEquals("$20,250.00", new DataFormatter().formatCellValue(cell));
      assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
   }

   @Test
   void scaledFormatIsNumberWithScaledPattern() {
      Cell cell = write(true).getRow(1).getCell(C_K_SCALED);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals(317000.0, cell.getNumericCellValue());
      assertEquals("\"$\"#,##0,\"K\"", cell.getCellStyle().getDataFormatString());
      // POI DataFormatter does not apply the scaling comma before a literal; Excel does
   }

   @Test
   void percentFormatIsNumber() {
      Cell cell = write(true).getRow(1).getCell(C_PERCENT);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals(0.256, cell.getNumericCellValue(), 1E-9);
      assertEquals("25.6%", new DataFormatter().formatCellValue(cell));
   }

   @Test
   void dateFormatIsDateCellWithThePattern() {
      Cell cell = write(true).getRow(1).getCell(C_DATE_FORMAT);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertTrue(DateUtil.isCellDateFormatted(cell));
      assertEquals("2024/01/15", new DataFormatter().formatCellValue(cell));
   }

   @Test
   void unformattedDateIsDateCell() {
      Cell cell = write(true).getRow(1).getCell(C_DATE_PLAIN);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals("2024-01-15 00:00:00", new DataFormatter().formatCellValue(cell));
   }

   @Test
   void unformattedPre1900DateIsTextNotMinusOne() {
      Cell cell = write(true).getRow(1).getCell(C_DATE_1850);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("1850-06-01 00:00:00", cell.getStringCellValue());
   }

   @Test
   void formattedPre1900DateStaysDisplayText() {
      Cell cell = write(true).getRow(1).getCell(C_DATE_1850_FORMAT);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("1850/06/01", cell.getStringCellValue());
   }

   @Test
   void messageFormatOnNumberStaysText() {
      Cell cell = write(true).getRow(1).getCell(C_MESSAGE);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("42 units", cell.getStringCellValue());
   }

   @Test
   void nullStaysBlankAndPlainNumberIsNumber() {
      Row row = write(true).getRow(1);
      assertEquals(CellType.BLANK, row.getCell(C_NULL_CELL).getCellType());
      assertEquals(CellType.NUMERIC, row.getCell(C_NUMBER_PLAIN).getCellType());
   }

   @Test
   void headersStayText() {
      Row row = write(true).getRow(0);

      for(int c = 0; c < COLS; c++) {
         assertEquals(CellType.STRING, row.getCell(c).getCellType());
      }
   }

   @Test
   void withoutFormatLayerCellsAreStillTyped() {
      Row row = write(false).getRow(1);
      assertEquals(CellType.NUMERIC, row.getCell(C_CURRENCY).getCellType());
      assertEquals(CellType.NUMERIC, row.getCell(C_DATE_FORMAT).getCellType());
   }

   @Test
   void dateCellsDoNotDependOnDefaultLocale() throws Exception {
      Row base = write(true).getRow(1);
      double plain = base.getCell(C_DATE_PLAIN).getNumericCellValue();
      double formatted = base.getCell(C_DATE_FORMAT).getNumericCellValue();

      for(Locale locale : new Locale[] { new Locale("th", "TH"), new Locale("ja", "JP", "JP") }) {
         Locale.setDefault(locale);
         book.close();
         book = new XSSFWorkbook();
         Row row = write(true).getRow(1);
         assertEquals(plain, row.getCell(C_DATE_PLAIN).getNumericCellValue(), "plain " + locale);
         assertEquals(formatted, row.getCell(C_DATE_FORMAT).getNumericCellValue(),
                      "formatted " + locale);
         assertEquals(CellType.STRING, row.getCell(C_DATE_1850).getCellType(), "1850 " + locale);
      }
   }

   private Sheet write(boolean formatLayer) {
      ExcelContext context = Mockito.mock(ExcelContext.class);
      Mockito.when(context.getWorkbook()).thenReturn(book);
      PoiExcelVSExporter exporter = new PoiExcelVSExporter(context, new ByteArrayOutputStream());
      exporter.sheetName = "Current View";
      VSTableLens lens = new VSTableLens(formatLayer ?
         new VSFormatTableLens(box("T1"), "T1", new Base(), true) : new Base());
      VSAssembly assembly = Mockito.mock(VSAssembly.class);
      Mockito.when(assembly.getAbsoluteName()).thenReturn("T1");
      exporter.writeExpandTableToSheet(assembly, lens);
      return book.getSheet("Current View.T1");
   }

   private static ViewsheetSandbox box(String name) {
      Viewsheet vs = Mockito.mock(Viewsheet.class);
      TableVSAssembly assembly = Mockito.mock(TableVSAssembly.class);
      Mockito.when(assembly.getName()).thenReturn(name);
      Mockito.when(assembly.getFormatInfo()).thenReturn(new FormatInfo());
      Mockito.when(assembly.getColumnSelection()).thenReturn(new ColumnSelection());
      Mockito.when(vs.getAssembly(name)).thenReturn(assembly);
      ViewsheetSandbox box = Mockito.mock(ViewsheetSandbox.class);
      Mockito.when(box.getViewsheet()).thenReturn(vs);
      Mockito.when(box.getScope()).thenReturn(Mockito.mock(ViewsheetScope.class));
      return box;
   }

   private static class Base extends DefaultTableLens {
      Base() {
         super(2, COLS);
         setHeaderRowCount(1);

         for(int c = 0; c < COLS; c++) {
            setObject(0, c, "h" + c);
         }

         setObject(1, C_CURRENCY, 20250.0);
         setObject(1, C_K_SCALED, 317000.0);
         setObject(1, C_PERCENT, 0.256);
         setObject(1, C_DATE_FORMAT, java.sql.Timestamp.valueOf("2024-01-15 00:00:00"));
         setObject(1, C_DATE_PLAIN, java.sql.Timestamp.valueOf("2024-01-15 00:00:00"));
         setObject(1, C_DATE_1850, java.sql.Timestamp.valueOf("1850-06-01 00:00:00"));
         setObject(1, C_MESSAGE, 42.0);
         setObject(1, C_NULL_CELL, null);
         setObject(1, C_NUMBER_PLAIN, 7.5);
         setObject(1, C_DATE_1850_FORMAT, java.sql.Timestamp.valueOf("1850-06-01 00:00:00"));
      }

      @Override
      public Class<?> getColType(int c) {
         return c == C_DATE_FORMAT || c == C_DATE_PLAIN || c == C_DATE_1850 || c == C_DATE_1850_FORMAT ?
            java.sql.Timestamp.class : Double.class;
      }

      @Override
      public Format getDefaultFormat(TableDataPath path) {
         if(path == null || path.getType() != TableDataPath.DETAIL || path.getPath().length == 0) {
            return null;
         }

         int col = Integer.parseInt(path.getPath()[path.getPath().length - 1].substring(1));

         switch(col) {
         case C_CURRENCY:
            return new DecimalFormat("$#,##0.00");
         case C_K_SCALED:
            return new ExtendedDecimalFormat("$#,##0K");
         case C_PERCENT:
            return new DecimalFormat("0.0%");
         case C_DATE_FORMAT:
         case C_DATE_1850_FORMAT:
            return new SimpleDateFormat("yyyy/MM/dd");
         case C_MESSAGE:
            return new MessageFormat("{0,number,#} units");
         case C_NULL_CELL:
            return new DecimalFormat("$#,##0.00");
         default:
            return null;
         }
      }
   }
}
