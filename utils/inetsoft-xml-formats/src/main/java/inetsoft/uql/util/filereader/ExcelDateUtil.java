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

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.util.*;

/**
 * Converts Excel date serials to java dates. POI's {@code DateUtil.getJavaDate},
 * {@code DateUtil.getJavaCalendar} and {@code Cell.getDateCellValue} build the date in
 * {@code Calendar.getInstance(Locale.getDefault())}, which is a Buddhist calendar on a th_TH
 * JVM (543 years early) and a Japanese imperial calendar on ja_JP_JP (2018 years late). These
 * methods do the same conversion in a Gregorian calendar, so the result does not depend on
 * the JVM default locale.
 */
public final class ExcelDateUtil {
   private ExcelDateUtil() {
   }

   /**
    * Same as {@code DateUtil.getJavaDate(double, boolean)} in the default time zone, but
    * always in a Gregorian calendar.
    *
    * @param date             the Excel date serial.
    * @param use1904windowing true if the workbook uses the 1904 date system.
    *
    * @return the date, or null if the value is not a valid Excel date.
    */
   public static Date getJavaDate(double date, boolean use1904windowing) {
      if(!DateUtil.isValidExcelDate(date)) {
         return null;
      }

      int wholeDays = (int) Math.floor(date);
      int millisecondsInDay = (int) ((date - wholeDays) * DateUtil.DAY_MILLISECONDS + 0.5);
      Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
      DateUtil.setCalendar(calendar, wholeDays, millisecondsInDay, use1904windowing, false);
      return calendar.getTime();
   }

   /**
    * Same as {@code Cell.getDateCellValue()}, but always in a Gregorian calendar.
    */
   public static Date getDateCellValue(Cell cell) {
      if(cell.getCellType() == CellType.BLANK) {
         return null;
      }

      return getJavaDate(cell.getNumericCellValue(), isDate1904(cell.getSheet().getWorkbook()));
   }

   private static boolean isDate1904(Workbook workbook) {
      if(workbook instanceof HSSFWorkbook) {
         return ((HSSFWorkbook) workbook).getInternalWorkbook().isUsing1904DateWindowing();
      }
      else if(workbook instanceof SXSSFWorkbook) {
         return ((SXSSFWorkbook) workbook).getXSSFWorkbook().isDate1904();
      }

      return workbook instanceof Date1904Support && ((Date1904Support) workbook).isDate1904();
   }
}
