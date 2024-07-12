/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
