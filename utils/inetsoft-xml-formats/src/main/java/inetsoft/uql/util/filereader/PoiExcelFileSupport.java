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

import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl;

import java.io.*;

public class PoiExcelFileSupport implements ExcelFileSupport {
   static {
      SchemaTypeSystemImpl.METADATA_PACKAGE_GEN = "inetsoft/xml/org/apache/xmlbeans/metadata";
   }

   @Override
   public String getBuiltinFormat(int id) {
      return BuiltinFormats.getBuiltinFormat(id);
   }

   @Override
   public boolean isDateFormat(double cellValue, int formatIndex, String formatStr) {
      return DateUtil.isADateFormat(formatIndex,formatStr) &&
         DateUtil.isValidExcelDate(cellValue);
   }

   @Override
   public String[] getSheetNames(File file) throws Exception {
      TextFileType fileType = getExcelFileType(file);

      try(BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
         String[] sheetNames;
         boolean xlsx = fileType == null ? file.getName().toLowerCase().endsWith(".xlsx") :
            TextFileType.XLSX == fileType;

         if(xlsx) {
            XLSXFileReader reader = new XLSXFileReader();
            sheetNames = reader.getSheetNames(input);
         }
         else {
            XLSFileReader reader = new XLSFileReader();
            sheetNames = reader.getSheetNames(input);
         }

         return sheetNames;
      }
      catch(EncryptedDocumentException encryptedException) {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.worksheet.importFilePasswordSupport"), LogLevel.WARN, false);
      }
   }

   @Override
   public boolean isXLSB(File file) {
      return XLSXFileReader.isXLSB(file);
   }

   @Override
   public ExcelFileReader createXLSReader() {
      return new XLSFileReader();
   }

   @Override
   public ExcelFileReader createXLSXReader() {
      return new XLSXFileReader();
   }

   /**
    * Get the excel file type by file.
    *
    * @param file file
    * @return type in TextFileType
    */
   private TextFileType getExcelFileType(File file) {
      try(InputStream input = new FileInputStream(file)) {
         return TextUtil.detectType(input);
      }
      catch(Exception e) {
         return null;
      }
   }
}
