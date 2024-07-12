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

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Descriptor for a Microsoft Excel file.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class ExcelFileInfo implements TextFileInfo {
   /**
    * Creates a new instance of <tt>ExcelFileInfo</tt>.
    */
   public ExcelFileInfo() {
      // default constructor
   }

   /**
    * Gets the name of the sheet that contains the data.
    * 
    * @return the sheet name.
    */
   public final String getSheet() {
      return selectedSheet;
   }
   
   /**
    * Sets the name of the sheet that contains the data.
    * 
    * @param sheet the sheet name.
    */
   public final void setSheet(String sheet) {
      this.selectedSheet = sheet;
   }

   /**
    * Get the list of sheet names.
    */
   public final ArrayList<String> getSheets() {
      return sheets;
   }

   /**
    * Sets the name of the sheets that contains in the file.
    * 
    * @param sheets the list of sheet names.
    */
   public final void setSheets(ArrayList<String> sheets) {
      this.sheets = sheets;
   }

   /**
    * Gets the index of the first data row.
    * 
    * @return the zero-based row index.
    */
   public final int getStartRow() {
      return startRow;
   }
   
   /**
    * Sets the index of the first data row.
    * 
    * @param startRow the zero-based row index.
    */
   public final void setStartRow(int startRow) {
      this.startRow = startRow;
   }

   /**
    * Gets the index of the last data row. If this value is less than zero, all
    * rows following the start row will be included.
    * 
    * @return the zero-based row index.
    */
   public final int getEndRow() {
      return endRow;
   }

   /**
    * Sets the index of the last data row. If this value is less than zero, all
    * rows following the start row will be included.
    * 
    * @param endRow the zero-based row index.
    */
   public final void setEndRow(int endRow) {
      this.endRow = endRow;
   }

   /**
    * Gets the index of the first data column.
    * 
    * @return the zero-based column index.
    */
   public final int getStartColumn() {
      return startColumn;
   }

   /**
    * Sets the index of the first data column.
    * 
    * @param startColumn the zero-based column index.
    */
   public final void setStartColumn(int startColumn) {
      this.startColumn = startColumn;
   }

   /**
    * Gets the index of the last data column. If this value is less than zero,
    * all columns following the start column will be included.
    * 
    * @return the zero-based row index.
    */
   public final int getEndColumn() {
      return endColumn;
   }

   /**
    * Sets the index of the last data column. If this value is less than zero,
    * all columns following the start column will be included.
    * 
    * @param endColumn the zero-based row index.
    */
   public final void setEndColumn(int endColumn) {
      this.endColumn = endColumn;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = null;

      selectedSheet = Tool.getChildValueByTagName(tag, "sheet");
      
      if((node = Tool.getChildNodeByTagName(tag, "startRow")) != null) {
         startRow = Integer.parseInt(Tool.getValue(node));
      }
      
      if((node = Tool.getChildNodeByTagName(tag, "endRow")) != null) {
         endRow = Integer.parseInt(Tool.getValue(node));
      }
      
      if((node = Tool.getChildNodeByTagName(tag, "startColumn")) != null) {
         startColumn = Integer.parseInt(Tool.getValue(node));
      }
      
      if((node = Tool.getChildNodeByTagName(tag, "endColumn")) != null) {
         endColumn = Integer.parseInt(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(tag, "sheets")) != null) {
         if(Tool.getValue(node) == null) {
            return;
         }

         String[] values = Tool.getValue(node).split(",");
         sheets = new ArrayList<>();

         for(int i = 0; i < values.length; i++) {
            sheets.add(values[i]);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<info>");
      writer.format("<sheet><![CDATA[%s]]></sheet>%n", selectedSheet);
      writer.format("<startRow>%d</startRow>%n", startRow);
      writer.format("<endRow>%d</endRow>%n", endRow);
      writer.format("<startColumn>%d</startColumn>%n", startColumn);
      writer.format("<endColumn>%d</endColumn>%n", endColumn);

      if(sheets != null) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < sheets.size(); i++) {
            sb.append(i == 0 ? sheets.get(i) : "," + sheets.get(i));
         }

         writer.format("<sheets><![CDATA[%s]]></sheets>%n", sb.toString());
      }

      writer.println("</info>");
   }

   /**
    * Gets the Excel row label for the specified row index.
    * 
    * @param index the row index.
    * 
    * @return the row label.
    */
   public static String getRowLabel(int index) {
      return index < 0 ? "" : Integer.toString(index + 1);
   }
   
   /**
    * Gets the row index for the specified Excel row label.
    * 
    * @param label the row label.
    * 
    * @return the row index.
    */
   public static int getRowIndex(String label) {
      int index = -1;
      
      if(label != null && label.trim().length() > 0) {
         index = Integer.parseInt(label.trim()) - 1;
      }
      
      return index;
   }
   
   /**
    * Gets the Excel column label for the specified column index.
    * 
    * @param index the column index.
    * 
    * @return the column label.
    */
   public static String getColumnLabel(int index) {
      int col = index;
      StringBuilder colstr = new StringBuilder();
      
      if(col == 0) {
         colstr.append('A');
      }
      else {
         while(col > 0) {
            int remainder = col % 26;
            char c = (char) (remainder + 65);
            colstr.insert(0, c);
            
            if(col > 25) {
               col -= remainder;
               col /= 26;
               col -= 1;
               
               if(col == 0) {
                  colstr.insert(0, 'A');
               }
            }
            else {
               col -= remainder;
            }
         }
      }
      
      return colstr.toString();
   }
   
   /**
    * Gets the column index for the specified Excel column label.
    * 
    * @param label the column label.
    * 
    * @return the column index.
    */
   public static int getColumnIndex(String label) {
      int index = -1;
      
      if(label != null && label.trim().length() > 0) {
         String chars = label.toUpperCase();
         index = 0;
         
         for(int i = chars.length() - 1; i >= 0; i--) {
            int n = (((int) chars.charAt(i)) & 0xffff) - 65;
            
            if(i < chars.length() - 1) {
               ++n;
            }
            
            int pow = chars.length() - i - 1;
            int offset = (int) Math.round(Math.pow(26, pow));
            n *= offset;
            index += n;
         }
      }
      
      return index;
   }

   private String selectedSheet = null;
   private int startRow = 0;
   private int endRow = -1;
   private int startColumn = 0;
   private int endColumn = -1;
   private ArrayList<String> sheets;
}
