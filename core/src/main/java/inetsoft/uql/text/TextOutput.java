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
package inetsoft.uql.text;

import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.filereader.*;

/**
 * TextOutput stores output attributes of text datasource.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextOutput extends XTypeNode implements Cloneable {
   /**
    * Constructor
    */
   public TextOutput() {
      setName("output");
   }

   /**
    * Get the table column specification.
    */
   public XSelection getTableSpec() {
      return tablespec;
   }

   /**
    * Set the table column specification.
    */
   public void setTableSpec(XSelection tablespec) {
      if(tablespec == null) {
         this.tablespec = new XSelection();
      }
      else {
         this.tablespec = tablespec;
      }
   }

   /**
    * Get the selected (shown) column indices.
    */
   public int[] getSelectedCols() {
      return selcols;
   }

   /**
    * Set the selected columns.
    */
   public void setSelectedCols(int[] cols) {
      selcols = cols;
   }

   /**
    * Gets the type of the source file.
    *
    * @return the file type.
    */
   public TextFileType getFileType() {
      return type;
   }

   /**
    * Sets the type of the source file.
    *
    * @param fileType the file type.
    */
   public void setFileType(TextFileType fileType) {
      TextFileType otype = this.type;
      this.type = fileType;

      if(otype != fileType) {
         headerInfo = null;
         bodyInfo = fileType.createFileInfo();
      }
   }

   /**
    * Gets the descriptor for the file header.
    *
    * @return the header descriptor.
    */
   public TextFileInfo getHeaderInfo() {
      return headerInfo;
   }

   /**
    * Sets the descriptor for the file header.
    *
    * @param headerInfo the header descriptor.
    */
   public void setHeaderInfo(TextFileInfo headerInfo) {
      this.headerInfo = headerInfo;
   }

   /**
    * Gets the descriptor for the file body.
    *
    * @return the body descriptor.
    */
   public TextFileInfo getBodyInfo() {
      return bodyInfo;
   }

   /**
    * Sets the descriptor for the file body.
    *
    * @param bodyInfo the body descriptor.
    */
   public void setBodyInfo(TextFileInfo bodyInfo) {
      this.bodyInfo = bodyInfo;
   }

   /**
    * Get the delimiters used in the text data file.
    */
   @Deprecated
   public String getDelimiter() {
      if(type != TextFileType.DELIMITED) {
         throw new IllegalStateException("Wrong file type");
      }

      DelimitedFileInfo info = (DelimitedFileInfo) bodyInfo;
      return info == null ? null : info.getDelimiter();
   }

   /**
    * Set the delimiters used in the text data file.
    */
   @Deprecated
   public void setDelimiter(String delim) {
      if(type != TextFileType.DELIMITED) {
         throw new IllegalStateException("Wrong file type");
      }

      DelimitedFileInfo info = (DelimitedFileInfo) bodyInfo;
      info.setDelimiter(delim == null ? "," : delim);
   }

   /**
    * Set whether the datasource should be auto import.
    */
   public void setAutoImportText(boolean autoImportText) {
      this.autoImportText = autoImportText;
   }

   /**
    * Check if datasource is auto import.
    */
   public boolean isAutoImportText() {
      return autoImportText;
   }

   /**
    * Check if the first row should be treated as a header row.
    */
   @Deprecated
   public boolean isFirstHeaderRow() {
      return headerInfo != null;
   }

   /**
    * Set whether the first row should be treated as a header row.
    */
   @Deprecated
   public void setFirstHeaderRow(boolean first) {
      if(!first) {
         headerInfo = null;
      }
      else if(headerInfo == null) {
         headerInfo = type.createFileInfo();
      }
   }

   /**
    * Check if the header row has fixed length.
    */
   @Deprecated
   public boolean isHeaderFixed() {
      return type == TextFileType.FIXED;
   }

   /**
    * Set whether the header row has fixed length.
    */
   @Deprecated
   public void setHeaderFixed(boolean headerFixed) {
      setFileType(headerFixed ? TextFileType.FIXED : TextFileType.DELIMITED);

      if(headerInfo == null) {
         headerInfo = type.createFileInfo();
      }
   }

   /**
    * Get header row's fixed length.
    */
   @Deprecated
   public int getHeaderFixedLength() {
      if(type != TextFileType.FIXED) {
         throw new IllegalStateException("Wrong file type");
      }

      FixedFileInfo info = (FixedFileInfo) headerInfo;
      return info == null ? 0 : info.getLength();
   }

   /**
    * Set the header row's fixed length.
    */
   @Deprecated
   public void setHeaderFixedLength(int headerLength) {
      if(type != TextFileType.FIXED) {
         throw new IllegalStateException("Wrong file type");
      }

      if(headerInfo == null) {
         headerInfo = type.createFileInfo();
      }

      FixedFileInfo info = (FixedFileInfo) headerInfo;
      info.setLength(headerLength);

      info = (FixedFileInfo) bodyInfo;
      info.setLength(headerLength);
   }

   /**
    * Get the header delimiters used in the text data file.
    */
   @Deprecated
   public String getHeaderDelimiter() {
      if(type != TextFileType.DELIMITED) {
         throw new IllegalStateException("Wrong file type");
      }

      DelimitedFileInfo info = (DelimitedFileInfo) headerInfo;
      return info == null ? null : info.getDelimiter();
   }

   /**
    * Set the header delimiters used in the text data file.
    */
   @Deprecated
   public void setHeaderDelimiter(String hdelim) {
      if(type != TextFileType.DELIMITED) {
         throw new IllegalStateException("Wrong file type");
      }

      if(headerInfo == null) {
         headerInfo = type.createFileInfo();
      }

      DelimitedFileInfo info = (DelimitedFileInfo) headerInfo;
      info.setDelimiter(hdelim == null ? "," : hdelim);
   }

   /**
    * Check if the column length is fixed.
    */
   @Deprecated
   public boolean isLengthFixed() {
      return type == TextFileType.FIXED;
   }

   /**
    * Set whether the column length is fixed.
    */
   @Deprecated
   public void setLengthFixed(boolean fixed) {
      setFileType(fixed ? TextFileType.FIXED : TextFileType.DELIMITED);
   }

   /**
    * Get the length of each column.
    */
   @Deprecated
   public int[] getFixedLengths() {
      if(type != TextFileType.FIXED) {
         throw new IllegalStateException("Wrong file type");
      }

      FixedFileInfo info = (FixedFileInfo) bodyInfo;
      return info == null ? null : info.getLengths();
   }

   /**
    * Set the length of each column.
    */
   @Deprecated
   public void setFixedLengths(int[] lengths) {
      if(type != TextFileType.FIXED) {
         throw new IllegalStateException("Wrong file type");
      }

      FixedFileInfo info = (FixedFileInfo) bodyInfo;
      info.setLengths(lengths);
   }

   /**
    * Clone a an output (XTypeNode) node of a datasource. The type node is
    * cloned by writing the type to XML and parse it back to handle
    * recursive type dependency.
    */
   @Override
   public XTypeNode cloneType() throws Exception {
      return (XTypeNode) clone();
   }

   /**
    * Make a copy of the text output.
    */
   @Override
   public Object clone() {
      TextOutput output = null;

      try {
         output = (TextOutput) super.clone();
      }
      catch(Exception e) {
         return null;
      }

      if(tablespec != null) {
         output.setTableSpec((XSelection) tablespec.clone());
      }

      return output;
   }

   private TextFileType type = TextFileType.DELIMITED;
   private TextFileInfo headerInfo = null;
   private TextFileInfo bodyInfo = null;
   private XSelection tablespec = new XSelection();
   private int[] selcols = null;
   private boolean autoImportText = false;
}

