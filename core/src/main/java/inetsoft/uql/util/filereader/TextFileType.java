/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util.filereader;

import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Enumeration of the types of text files supported by the text data source.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public enum TextFileType {
   /**
    * Indicates that the source file is a fixed-width text file.
    */
   FIXED("Fixed Width Text", FixedFileInfo.class, FixedFileReader::new),
   
   /**
    * Indicates that the source file is a delimited text file.
    */
   DELIMITED("Delimited Text", DelimitedFileInfo.class, DelimitedFileReader::new),
   
   /**
    * Indicates that the source file is a Microsoft Excel 97-2003 (.xls) file.
    */
   XLS("Microsoft Excel 97-2003 (.xls)", ExcelFileInfo.class, TextFileType::createXLSReader),

   /**
    * Indicates that the source file is a Microsoft Excel Office Open XML
    * (.xlsx) file.
    */
   XLSX("Microsoft Excel Office Open XML (.xlsx)", ExcelFileInfo.class,
        TextFileType::createXLSXReader);
   
   private final String label;
   private final Class<? extends TextFileInfo> fileInfoClass;
   private final Supplier<? extends TextFileReader> fileReaderFactory;

   /**
    * Creates a new instance of <tt>TextFileType</tt>.
    *
    * @param label             the display label for the file type.
    * @param fileInfoClass     the descriptor class for the file type.
    * @param fileReaderFactory the factory for the file type reader.
    */
   private TextFileType(String label,
                        Class<? extends TextFileInfo> fileInfoClass,
                        Supplier<? extends TextFileReader> fileReaderFactory)
   {
      this.label = label;
      this.fileInfoClass = fileInfoClass;
      this.fileReaderFactory = fileReaderFactory;
   }
   
   /**
    * Gets the display name for the file type.
    * 
    * @return the label.
    */
   public final String getLabel() {
      return Catalog.getCatalog().getString(label);
   }
   
   /**
    * Gets the descriptor class for the file type.
    * 
    * @return the descriptor class.
    */
   public final Class<? extends TextFileInfo> getFileInfoClass() {
      return fileInfoClass;
   }
   
   /**
    * Creates a new descriptor instance for the file type.
    * 
    * @return a new descriptor instance.
    */
   public final TextFileInfo createFileInfo() {
      TextFileInfo fileInfo = null;
      
      try {
         fileInfo = fileInfoClass.getConstructor().newInstance();
      }
      catch(Throwable exc) {
         LOG.error("Failed to instatiate file info", exc);
      }
      
      return fileInfo;
   }
   
   /**
    * Creates a new reader instance for the file type.
    * 
    * @return a new reader instance.
    */
   public final TextFileReader createFileReader() {
      return fileReaderFactory.get();
   }

   private static TextFileReader createXLSReader() {
      return ExcelFileSupport.getInstance().createXLSReader();
   }

   private static TextFileReader createXLSXReader() {
      return ExcelFileSupport.getInstance().createXLSXReader();
   }

   private static final Logger LOG = LoggerFactory.getLogger(TextFileType.class);
}
