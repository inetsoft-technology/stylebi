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
package inetsoft.report.io.excel;

/**
 * This is the excel constant class , contains some constant used in excel.
 *
 * @version 12.2, 9/16/2015
 * @author InetSoft Technology Corp
 */

public final class ExcelConstants {
   public ExcelConstants() {
   }

   /**
    * get excel generate type.
    */
   public static String getGenerateType() {
      return generateType;
   }

   /**
    * set excel generate type.
    */
   public static void setGenerateType(String type) {
      generateType = type;
   }

   public static final int EXCEL_MAX_ROW = 1048575;
   public static final int EXCEL_MAX_COL = 16383;
   public static final int SHEET_MAX_ROWS = 1048000;
   public static final short DEFCOLWIDTH = 0x55; // Default Width for Columns.
   private static String generateType = "";
}

