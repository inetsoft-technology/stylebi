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

import inetsoft.report.composition.VSTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSAssembly;
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
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79581: the "Current View.&lt;table&gt;" data sheet must store numbers, dates and
 * booleans as typed cells (a Double 14.0 reads 14, not the text "14.0"), while headers and
 * Strings stay text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelDataTabCellTypeTest {
   private XSSFWorkbook book;
   private PoiExcelVSExporter exporter;

   @BeforeEach
   void setUp() {
      book = new XSSFWorkbook();
      ExcelContext context = Mockito.mock(ExcelContext.class);
      Mockito.when(context.getWorkbook()).thenReturn(book);
      exporter = new PoiExcelVSExporter(context, new ByteArrayOutputStream());
      exporter.sheetName = "Current View";
   }

   @AfterEach
   void tearDown() throws Exception {
      book.close();
   }

   @Test
   void doubleIsNumericWithoutFraction() {
      Cell cell = write(new Object[][] {{"Amt"}, {14.0}}).getRow(1).getCell(0);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals(14.0, cell.getNumericCellValue());
      assertEquals("14", new DataFormatter().formatCellValue(cell));
   }

   @Test
   void integerLongAndBigDecimalAreNumeric() {
      Sheet sheet = write(new Object[][] {{"a", "b", "c"}, {15, 12345678901L, new BigDecimal("12.50")}});
      Row row = sheet.getRow(1);

      for(int c = 0; c < 3; c++) {
         assertEquals(CellType.NUMERIC, row.getCell(c).getCellType());
      }

      assertEquals(15.0, row.getCell(0).getNumericCellValue());
      assertEquals(12345678901.0, row.getCell(1).getNumericCellValue());
      assertEquals(12.5, row.getCell(2).getNumericCellValue());
   }

   @Test
   void largeLongStaysExactText() {
      Cell cell = write(new Object[][] {{"a"}, {Long.MAX_VALUE}}).getRow(1).getCell(0);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("9223372036854775807", cell.getStringCellValue());
   }

   @Test
   void twelveDigitLongIsNumericAndNotScientific() {
      Cell cell = write(new Object[][] {{"a"}, {123456789012L}}).getRow(1).getCell(0);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals("123456789012", new DataFormatter().formatCellValue(cell));
      assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
   }

   @Test
   void bigDecimalBeyondFifteenDigitsStaysText() {
      Cell cell = write(new Object[][] {{"a"}, {new BigDecimal("1234567890.123456789")}})
         .getRow(1).getCell(0);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("1234567890.123456789", cell.getStringCellValue());
   }

   @Test
   void shortAndFloatAreNumeric() {
      Row row = write(new Object[][] {{"a", "b"}, {(short) 7, 2.5f}}).getRow(1);
      assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
      assertEquals(CellType.NUMERIC, row.getCell(1).getCellType());
      assertEquals(2.5, row.getCell(1).getNumericCellValue());
   }

   @Test
   void longBoundaries() {
      Row row = write(new Object[][] {{"a", "b", "c", "d", "e"},
         {999999999999999L, 1000000000000000L, -999999999999999L, -100000000000L,
          Long.MIN_VALUE}}).getRow(1);
      DataFormatter fmt = new DataFormatter();
      assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
      assertEquals("999999999999999", fmt.formatCellValue(row.getCell(0)));
      assertEquals(CellType.STRING, row.getCell(1).getCellType());
      assertEquals("1000000000000000", row.getCell(1).getStringCellValue());
      assertEquals(CellType.NUMERIC, row.getCell(2).getCellType());
      assertEquals("-999999999999999", fmt.formatCellValue(row.getCell(2)));
      assertEquals(CellType.NUMERIC, row.getCell(3).getCellType());
      assertEquals("-100000000000", fmt.formatCellValue(row.getCell(3)));
      assertEquals(CellType.STRING, row.getCell(4).getCellType());
      assertEquals("-9223372036854775808", row.getCell(4).getStringCellValue());
   }

   @Test
   void integralDoubleOfThirteenDigitsIsNotScientific() {
      Cell cell = write(new Object[][] {{"a"}, {1.0E12}}).getRow(1).getCell(0);
      assertEquals(CellType.NUMERIC, cell.getCellType());
      assertEquals("1000000000000", new DataFormatter().formatCellValue(cell));
   }

   @Test
   void bigDecimalPrecisionBoundary() {
      Row row = write(new Object[][] {{"a", "b", "c"},
         {new BigDecimal("12.50"), new BigDecimal("123456789012345"),
          new BigDecimal("1234567890123456")}}).getRow(1);
      assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
      assertEquals(12.5, row.getCell(0).getNumericCellValue());
      assertEquals(CellType.NUMERIC, row.getCell(1).getCellType());
      assertEquals("123456789012345", new DataFormatter().formatCellValue(row.getCell(1)));
      assertEquals(CellType.STRING, row.getCell(2).getCellType());
      assertEquals("1234567890123456", row.getCell(2).getStringCellValue());
   }

   @Test
   void cellsWithTheSameFormatShareOneStyle() {
      Row row = write(new Object[][] {{"a", "b", "c", "d"},
         {123456789012L, 223456789012L, java.sql.Date.valueOf("2023-11-14"),
          java.sql.Date.valueOf("2024-01-02")}}).getRow(1);
      assertEquals(row.getCell(0).getCellStyle().getIndex(),
                   row.getCell(1).getCellStyle().getIndex());
      assertEquals(row.getCell(2).getCellStyle().getIndex(),
                   row.getCell(3).getCellStyle().getIndex());
      assertNotEquals(row.getCell(0).getCellStyle().getIndex(),
                      row.getCell(2).getCellStyle().getIndex());
   }

   @Test
   void nanAndInfinityStayText() {
      Row row = write(new Object[][] {{"a", "b"}, {Double.NaN, Double.POSITIVE_INFINITY}}).getRow(1);
      assertEquals(CellType.STRING, row.getCell(0).getCellType());
      assertEquals(CellType.STRING, row.getCell(1).getCellType());
   }

   @Test
   void dateIsDateCellWithDateStyle() {
      java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2023-11-14 17:13:20");
      java.sql.Date date = java.sql.Date.valueOf("2023-11-14");
      Row row = write(new Object[][] {{"t", "d"}, {ts, date}}).getRow(1);
      assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
      assertTrue(DateUtil.isCellDateFormatted(row.getCell(0)));
      assertTrue(DateUtil.isCellDateFormatted(row.getCell(1)));
      assertEquals("2023-11-14 17:13:20", new DataFormatter().formatCellValue(row.getCell(0)));
      assertEquals("2023-11-14", new DataFormatter().formatCellValue(row.getCell(1)));
      assertEquals(BorderStyle.THIN, row.getCell(1).getCellStyle().getBorderTop(),
         "the date style keeps the data tab borders");
   }

   @Test
   void booleanIsBooleanCell() {
      Cell cell = write(new Object[][] {{"b"}, {Boolean.TRUE}}).getRow(1).getCell(0);
      assertEquals(CellType.BOOLEAN, cell.getCellType());
      assertTrue(cell.getBooleanCellValue());
   }

   @Test
   void headerStaysTextEvenWhenNumeric() {
      Sheet sheet = write(new Object[][] {{2023, "x"}, {14.0, 15.0}}, 1, 0);
      assertEquals(CellType.STRING, sheet.getRow(0).getCell(0).getCellType());
      assertEquals("2023", sheet.getRow(0).getCell(0).getStringCellValue());
      assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(0).getCellType());
   }

   @Test
   void headerColumnStaysText() {
      Sheet sheet = write(new Object[][] {{"h", "v"}, {2023, 14.0}}, 1, 1);
      assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
      assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(1).getCellType());
   }

   @Test
   void numericLookingStringStaysText() {
      Cell cell = write(new Object[][] {{"zip"}, {"007"}}).getRow(1).getCell(0);
      assertEquals(CellType.STRING, cell.getCellType());
      assertEquals("007", cell.getStringCellValue());
   }

   @Test
   void nullStaysBlank() {
      Cell cell = write(new Object[][] {{"a"}, {null}}).getRow(1).getCell(0);
      assertEquals(CellType.BLANK, cell.getCellType());
   }

   @Test
   void longStringIsCapped() {
      Cell cell = write(new Object[][] {{"a"}, {"x".repeat(40000)}}).getRow(1).getCell(0);
      assertEquals(32767, cell.getStringCellValue().length());
   }

   @Test
   void dateStylesAreSharedAcrossRows() {
      int rows = 1201;
      Object[][] data = new Object[rows][3];
      data[0] = new Object[] {"d", "t", "s"};

      for(int i = 1; i < rows; i++) {
         data[i] = new Object[] {
            new java.sql.Date(1700000000000L + i * 86400000L), new java.sql.Time(i * 1000L),
            new java.sql.Timestamp(1700000000000L + i * 1000L)};
      }

      Sheet sheet = write(data);
      // one style per format (3) plus the border style and the workbook default, not per cell
      assertTrue(book.getNumCellStyles() <= 6, "styles: " + book.getNumCellStyles());
      assertEquals(CellType.NUMERIC, sheet.getRow(rows - 1).getCell(0).getCellType());
      assertEquals("hh:mm:ss", sheet.getRow(rows - 1).getCell(1).getCellStyle().getDataFormatString());
      assertEquals(sheet.getRow(1).getCell(2).getCellStyle().getIndex(),
                   sheet.getRow(rows - 1).getCell(2).getCellStyle().getIndex());
   }

   private Sheet write(Object[][] data) {
      return write(data, 1, 0);
   }

   private Sheet write(Object[][] data, int headerRows, int headerCols) {
      VSTableLens lens = Mockito.mock(VSTableLens.class);
      Mockito.when(lens.getRowCount()).thenReturn(data.length);
      Mockito.when(lens.getColCount()).thenReturn(data[0].length);
      Mockito.when(lens.getHeaderRowCount()).thenReturn(headerRows);
      Mockito.when(lens.getHeaderColCount()).thenReturn(headerCols);
      Mockito.when(lens.getObject(Mockito.anyInt(), Mockito.anyInt()))
         .thenAnswer(inv -> data[inv.getArgument(0, Integer.class)][inv.getArgument(1, Integer.class)]);
      VSAssembly assembly = Mockito.mock(VSAssembly.class);
      Mockito.when(assembly.getAbsoluteName()).thenReturn("T1");
      exporter.writeExpandTableToSheet(assembly, lens);
      return book.getSheet("Current View.T1");
   }
}
