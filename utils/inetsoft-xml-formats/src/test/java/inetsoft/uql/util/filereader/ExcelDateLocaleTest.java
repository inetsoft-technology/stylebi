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

import inetsoft.report.io.viewsheet.excel.PoiExcelVSUtil;
import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.text.TextOutput;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78225: POI's {@code DateUtil.getJavaDate} and {@code Cell.getDateCellValue} build the
 * date in {@code Calendar.getInstance(Locale.getDefault())}, so on a th_TH JVM an Excel date
 * cell came out 543 years early (2024-02-29 became 1481-02-28) and on ja_JP_JP 2018 years late.
 * The axis is the JVM default locale's calendar system; the user locale does not reach POI.
 * Runs the real upload readers and {@link PoiExcelVSUtil.getCellValue(Cell)}, and checks that
 * {@link ExcelDateUtil} returns exactly what POI returns on an en_US JVM, including time-only
 * cells and DST-overlap values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class ExcelDateLocaleTest {
   @BeforeEach
   void saveDefaults() {
      originalLocale = Locale.getDefault();
      originalTimeZone = TimeZone.getDefault();
   }

   @AfterEach
   void restoreDefaults() {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
   }

   @Test
   void xlsxUploadKeepsGregorianDatesOnThaiAndJapaneseLocales() throws Exception {
      for(Locale locale : LOCALES) {
         Locale.setDefault(locale);
         assertUploadedDates(true, locale);
      }
   }

   @Test
   void xlsUploadKeepsGregorianDatesOnThaiAndJapaneseLocales() throws Exception {
      for(Locale locale : LOCALES) {
         Locale.setDefault(locale);
         assertUploadedDates(false, locale);
      }
   }

   @Test
   void cellValueKeepsGregorianDatesOnThaiAndJapaneseLocales() throws Exception {
      for(Locale locale : LOCALES) {
         Locale.setDefault(locale);

         for(boolean xlsx : new boolean[] { true, false }) {
            try(Workbook workbook = createWorkbook(xlsx)) {
               Sheet sheet = workbook.getSheetAt(0);

               for(int i = 0; i < DATES.length; i++) {
                  Object value = PoiExcelVSUtil.getCellValue(sheet.getRow(i + 1).getCell(0));
                  assertEquals(Date.class, value.getClass());
                  assertEquals(DATES[i], toLocalDate((Date) value), locale + " xlsx=" + xlsx);
               }
            }
         }
      }
   }

   /**
    * A time-only cell is 1899-12-31 in Excel, where java.time and java.util.TimeZone disagree
    * about the Asia/Bangkok offset (local mean time), so a ZonedDateTime-based conversion would
    * move it by 17:56. It must match what POI returns on an en_US JVM.
    */
   @Test
   void timeOnlyCellMatchesEnglishLocaleOnBangkok() throws Exception {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));
      double serial = 0.2370486111; // 05:41:21
      Date expected = englishJavaDate(serial, false);
      assertEquals("05:41:21", format("HH:mm:ss", expected));

      for(Locale locale : new Locale[] { Locale.US, THAI }) {
         Locale.setDefault(locale);
         assertEquals(expected, ExcelDateUtil.getJavaDate(serial, false), locale.toString());

         for(boolean xlsx : new boolean[] { true, false }) {
            try(Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
               Cell cell = workbook.createSheet().createRow(0).createCell(0);
               cell.setCellValue(serial);
               cell.setCellStyle(createStyle(workbook, "hh:mm:ss"));
               assertEquals(expected, PoiExcelVSUtil.getCellValue(cell), locale + " xlsx=" + xlsx);
            }
         }
      }
   }

   /**
    * 2020-11-01 01:24:11 happens twice in America/New_York. The result must be the same instant
    * POI returns on an en_US JVM.
    */
   @Test
   void dstOverlapMatchesEnglishLocale() {
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      double serial = 44136.05846064815;
      Date expected = englishJavaDate(serial, false);
      assertEquals("2020-11-01 01:24:11", format("yyyy-MM-dd HH:mm:ss", expected));

      for(Locale locale : LOCALES) {
         Locale.setDefault(locale);
         assertEquals(expected, ExcelDateUtil.getJavaDate(serial, false), locale.toString());
      }
   }

   @Test
   void serialsMatchEnglishLocaleInEveryWindowing() {
      double[] serials = { 0.5, 0.99999, 1, 59, 60, 61, 18278, 36525, 45351, 45351.75, 46037,
                           46037.123456789, 2958465.99 };

      for(String zone : new String[] { "Asia/Bangkok", "Asia/Tokyo", "America/Sao_Paulo" }) {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));

         for(boolean use1904 : new boolean[] { false, true }) {
            for(double serial : serials) {
               Date expected = englishJavaDate(serial, use1904);

               for(Locale locale : LOCALES) {
                  Locale.setDefault(locale);
                  assertEquals(expected, ExcelDateUtil.getJavaDate(serial, use1904),
                               zone + " " + locale + " 1904=" + use1904 + " " + serial);
               }
            }
         }
      }

      assertNull(ExcelDateUtil.getJavaDate(-1, false));
   }

   @Test
   void cellValueHonorsXlsx1904Windowing() throws Exception {
      Locale.setDefault(THAI);

      try(XSSFWorkbook workbook = new XSSFWorkbook()) {
         if(workbook.getCTWorkbook().isSetWorkbookPr()) {
            workbook.getCTWorkbook().getWorkbookPr().setDate1904(true);
         }
         else {
            workbook.getCTWorkbook().addNewWorkbookPr().setDate1904(true);
         }

         assertTrue(workbook.isDate1904());
         Cell cell = workbook.createSheet().createRow(0).createCell(0);
         cell.setCellValue(LocalDate.of(2024, 2, 29));
         cell.setCellStyle(createStyle(workbook, "yyyy-mm-dd"));

         assertEquals(LocalDate.of(2024, 2, 29),
                      toLocalDate((Date) PoiExcelVSUtil.getCellValue(cell)));
      }
   }

   private static void assertUploadedDates(boolean xlsx, Locale locale) throws Exception {
      byte[] bytes;

      try(Workbook workbook = createWorkbook(xlsx);
          ByteArrayOutputStream out = new ByteArrayOutputStream())
      {
         workbook.write(out);
         bytes = out.toByteArray();
      }

      // the same sequence as ImportCSVDialogService: sniff the types, then read the data
      TextOutput output = new TextOutput();
      output.setHeaderInfo(createInfo(0, 0));
      output.setBodyInfo(createInfo(1, -1));
      DateParseInfo parseInfo = new DateParseInfo();
      ExcelFileReader reader = xlsx ? new XLSXFileReader() : new XLSFileReader();
      XTypeNode meta = reader.importHeader(
         new ByteArrayInputStream(bytes), null, output, 50000, 100, parseInfo);
      assertEquals("date", ((XTypeNode) meta.getChild(0)).getType(), locale.toString());

      ((ExcelFileInfo) output.getHeaderInfo()).setEndColumn(meta.getChildCount() - 1);
      ((ExcelFileInfo) output.getBodyInfo()).setEndColumn(meta.getChildCount() - 1);
      XSelection spec = new XSelection();
      int[] sel = new int[meta.getChildCount()];

      for(int i = 0; i < meta.getChildCount(); i++) {
         XTypeNode column = (XTypeNode) meta.getChild(i);
         spec.addColumn(column.getName());
         spec.setConversion(column.getName(), column.getType(),
                            (String) column.getAttribute("format"));
         spec.setFormatFixed(column.getName(), true);
         sel[i] = i;
      }

      output.setTableSpec(spec);
      output.setSelectedCols(sel);
      XTableNode table = reader.read(new ByteArrayInputStream(bytes), null, null, output, 101,
                                     meta.getChildCount(), true, null, false, parseInfo);
      List<Object> excel = new ArrayList<>();
      List<Object> iso = new ArrayList<>();

      while(table.next()) {
         excel.add(table.getObject(0));
         iso.add(table.getObject(1));
      }

      // the reporter's check: the date cells must equal the ISO text column, which goes
      // through the same Gregorian parse. Comparing the two columns keeps the check free of
      // the time zone cached in CoreTool's thread-local formats by an earlier test class.
      assertEquals(DATES.length, excel.size());
      assertEquals(iso, excel, locale + " xlsx=" + xlsx);
   }

   private static ExcelFileInfo createInfo(int startRow, int endRow) {
      ExcelFileInfo info = new ExcelFileInfo();
      info.setSheet(SHEET);
      info.setStartRow(startRow);
      info.setEndRow(endRow);
      info.setStartColumn(0);
      info.setEndColumn(-1);
      return info;
   }

   private static Workbook createWorkbook(boolean xlsx) {
      Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
      Sheet sheet = workbook.createSheet(SHEET);
      CellStyle style = createStyle(workbook, "yyyy-mm-dd");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Date Excel");
      header.createCell(1).setCellValue("Date ISO");

      for(int i = 0; i < DATES.length; i++) {
         Row row = sheet.createRow(i + 1);
         Cell cell = row.createCell(0);
         cell.setCellValue(DATES[i]);
         cell.setCellStyle(style);
         row.createCell(1).setCellValue(DATES[i].toString());
      }

      return workbook;
   }

   private static CellStyle createStyle(Workbook workbook, String pattern) {
      CellStyle style = workbook.createCellStyle();
      style.setDataFormat(workbook.createDataFormat().getFormat(pattern));
      return style;
   }

   // what POI returns on an en_US JVM, in the current default time zone
   private static Date englishJavaDate(double serial, boolean use1904) {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.US);
         return DateUtil.getJavaDate(serial, use1904);
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   private static LocalDate toLocalDate(Date date) {
      return LocalDate.parse(format("yyyy-MM-dd", date));
   }

   private static String format(String pattern, Date date) {
      SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
      format.setCalendar(new GregorianCalendar(TimeZone.getDefault(), Locale.US));
      return format.format(date);
   }

   private Locale originalLocale;
   private TimeZone originalTimeZone;

   private static final String SHEET = "Sheet1";
   private static final Locale THAI = Locale.of("th", "TH");
   private static final Locale[] LOCALES = { Locale.US, THAI, Locale.of("ja", "JP", "JP") };
   private static final LocalDate[] DATES = {
      LocalDate.of(2026, 1, 15), LocalDate.of(2024, 2, 29), LocalDate.of(1999, 12, 31),
      LocalDate.of(1950, 1, 15)
   };
}
