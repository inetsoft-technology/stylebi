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
package inetsoft.uql.util.filereader;

import java.io.File;

public interface ExcelFileSupport {
   String getBuiltinFormat(int id);

   boolean isDateFormat(double cellValue, int formatIndex, String formatStr);

   String[] getSheetNames(File file) throws Exception;

   boolean isXLSB(File file);

   ExcelFileReader createXLSReader();

   ExcelFileReader createXLSXReader();

   static ExcelFileSupport getInstance() {
      try {
         Class<?> clazz = ExcelFileSupport.class.getClassLoader()
            .loadClass("inetsoft.uql.util.filereader.PoiExcelFileSupport");
         return (ExcelFileSupport) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create support instance", e);
      }
   }
}
