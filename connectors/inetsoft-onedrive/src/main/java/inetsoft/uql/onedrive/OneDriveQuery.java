/*
 * inetsoft-onedrive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.onedrive;

import inetsoft.report.composition.CSVInfo;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.filereader.ExcelFileReader;
import inetsoft.uql.util.filereader.ExcelFileSupport;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.util.List;

@View(vertical = false, value = {
   @View1(value = "path", align=ViewAlign.FILL),
   @View1(
      type = ViewType.BUTTON, text = "Load File",
      button = @Button(type = ButtonType.METHOD, method = "loadFile")),
   @View1(value = "excelSheet", row=2),
   @View1(value = "encoding", row=3),
   @View1(value = "delimiter", row=4),
   @View1(value = "tab"),
   @View1(value = "headerColumnCount", row=5),
   @View1("unpivotData"),
   @View1(value = "firstRowHeader", row=6),
   @View1("removeQuotation"),
   @View1(
      type = ViewType.BUTTON, text = "Refresh Column Definitions",
      row = 7, col = 1,
      button = @Button(type = ButtonType.METHOD, method = "loadColumns")),
   @View1(type = ViewType.EDITOR, value = "columns", row = 8, col = 1, colspan = 3)
})
public class OneDriveQuery extends SelectableTabularQuery  {
   public OneDriveQuery() {
      super(OneDriveDataSource.TYPE);
   }

   @Property(
      label = "Path",
      pattern = {".*", "^.*\\.(txt|csv|xls|xlsx)$"},
      required = true)
   public String getPath() {
      return path;
   }

   @SuppressWarnings("unused")
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Get the excel sheet names.
    */
   @Property(label = "Sheet")
   @PropertyEditor(
      dependsOn = {"fileFolder"},
      enabledMethod = "isExcel", tagsMethod = "getExcelSheetNames")
   public String getExcelSheet() {
      return excelSheet;
   }

   /**
    * Set the excel sheet.
    */
   public void setExcelSheet(String excelSheet) {
      this.excelSheet = excelSheet;

      try {
         this.loadColumns();
      }
      catch(Exception e) {

      }
   }

   /**
    * Get the text encoding types.
    */
   @Property(label = "Encoding")
   @PropertyEditor(
      enabledMethod = "isText",
      dependsOn = {"fileFolder"},
      tagsMethod = "getEncodingTypes")
   public String getEncoding() {
      return encoding;
   }

   /**
    * Set the text encoding type.
    */
   public void setEncoding(String encoding) {
      this.encoding = encoding;
   }

   /**
    * Get the text delimiter.
    */
   @Property(label = "Text Delimiter")
   @PropertyEditor(
      enabledMethod = "canDelimit",
      dependsOn = {"tab", "fileFolder"})
   public String getDelimiter() {
      if(this.delimiter == null || this.delimiter.isEmpty()) {
         initDelimiter();
      }

      return delimiter;
   }

   /**
    * Set the text delimiter
    */
   public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   /**
    * Check the Tab delimiter.
    */
   @Property(label = "Tab")
   @PropertyEditor(enabledMethod = "isText", dependsOn = {"fileFolder"})
   public boolean isTab() {
      return tab;
   }

   /**
    * Set the text delimiter to \t (Tab)
    */
   public void setTab(boolean tab) {
      this.tab = tab;
   }

   /**
    * Check to unpivot data.
    */
   @Property(label = "Unpivot Data")
   @PropertyEditor(enabledMethod = "isUnpivotDataEnabled", dependsOn = {"fileFolder", "delimeter", "tab"})
   public boolean isUnpivotData() {
      return unpivotData;
   }

   /**
    * Change unpivot data status.
    */
   public void setUnpivotData(boolean unpivotData) {
      this.unpivotData = unpivotData;
   }

   public boolean isUnpivotDataEnabled() {
      return this.getColumns() != null && this.getColumns().length > 1;
   }

   /**
    * Get the number of header columns. Only active when unpivot data is true.
    */
   @Property(label = "Header Columns", min = 1)
   @PropertyEditor(enabledMethod = "isUnpivotData", dependsOn = {"unpivotData"})
   public int getHeaderColumnCount() {
      return this.headerColumnCount;
   }

   /**
    * Set header column count.
    */
   public void setHeaderColumnCount(int headerColumnCount) {
      if(headerColumnCount < 0) {
         this.headerColumnCount = 0;
         return;
      }

      this.headerColumnCount = headerColumnCount;
   }

   /**
    * Check if first row is header.
    */
   @Property(label = "First Row as Header")
   @PropertyEditor(enabledMethod = "isUnpivot", dependsOn = {"unpivotData"})
   public boolean isFirstRowHeader() {
      return this.unpivotData || firstRowHeader;
   }

   /**
    * Set the first row as header.
    */
   public void setFirstRowHeader(boolean firstRowHeader) {
      this.firstRowHeader = firstRowHeader;
   }

   /**
    * Check if the text should remove quotation marks. Only enabled when the
    * file is a text file.
    */
   @Property(label = "Remove Quotation Marks")
   @PropertyEditor(enabledMethod = "isText", dependsOn = {"fileFolder"})
   public boolean isRemoveQuotation() {
      return removeQuotation;
   }

   /**
    * Set whether to remove quotation marks.
    */
   public void setRemoveQuotation(boolean removeQuotation) {
      this.removeQuotation = removeQuotation;
   }

   /**
    * Gets the column definitions that have been modified from the source data.
    *
    * @return the column definitions.
    */
   @Property(label = "Columns", required = true)
   @PropertyEditor(enabledMethod = "allowColumnDefinition")
   public ColumnDefinition[] getColumns() {
      return super.getColumns();
   }

   /**
    * Sets the column definitions that have been modified from the source data.
    *
    * @param columns the column definitions.
    */
   public void setColumns(ColumnDefinition[] columns) {
      super.setColumns(columns);
   }

   @Override
   public void loadOutputColumns(VariableTable vtable) throws Exception {
      ColumnDefinition[] columnDef = getColumns();
      XTypeNode[] cols = new XTypeNode[getColumns().length];
      int j = 0;

      for(int i = 0; i < cols.length; i++) {
         if(columnDef[i].isSelected()) {
            String alias = columnDef[i].getAlias();

            if(alias == null || alias.isEmpty()) {
               alias = columnDef[i].getName() == null || columnDef[i].getName().isEmpty() ?
                  "Column [" + i + "]" : columnDef[i].getName();
               columnDef[i].setAlias(alias);
            }

            cols[j] = XSchema.createPrimitiveType(
               alias, Tool.getDataClass(columnDef[i].getType().type()));

            j++;
         }
      }

      this.setOutputColumns(cols);
   }

   public File getTempFile() {
      return temp;
   }

   /**
    * Get the Excel sheet names.
    */
   public String[] getExcelSheetNames() {
      FileInputStream fileInput = null;
      String[] sheets = new String[1];

      if(temp == null || !temp.exists()) {
         return new String[]{""};
      }

      try {
         fileInput = new FileInputStream(temp.getAbsolutePath());

         if(path.endsWith(".xlsx")) {
            ExcelFileReader xlsx = ExcelFileSupport.getInstance().createXLSXReader();
            sheets = xlsx.getSheetNames(fileInput);
         }
         else if(path.endsWith("xls")) {
            ExcelFileReader xls = ExcelFileSupport.getInstance().createXLSReader();
            sheets = xls.getSheetNames(fileInput);
         }
      }
      catch(Exception e) {
         e.printStackTrace();
      }
      finally {
         if(fileInput != null) {
            try {
               fileInput.close();
            }
            catch(IOException e) {
               e.printStackTrace();
            }
         }

         if(this.excelSheet == null || this.excelSheet.isEmpty()) {
            this.excelSheet = sheets[0];
         }
      }

      return sheets;
   }

   /**
    * Get the different text encoding types.
    */
   public String[] getEncodingTypes() {
      if(path == null || temp == null) {
         return new String[0];
      }

      String[] types = new String[3];
      boolean encodingExists = true;
      String encoding = null;

      try {
         if(temp != null &&
            (this.encoding == null || this.encoding.isEmpty()))
         {
            encoding = CSVInfo.getFileEncode(temp);
         }

         if (encoding != null && !encoding.equalsIgnoreCase("unicode") &&
            !encoding.equalsIgnoreCase("utf8") &&
            !encoding.equalsIgnoreCase("gbk"))
         {
            types = new String[4];
            types[0] = encoding;
            encodingExists = false;
         }
      }
      catch(Exception e) {
         LOG.error("Failed to generate encoding", e);
      }
      finally {
         int i = types.length - 1;
         types[i - 2] = "UTF8";
         types[i - 1] = "GBK";
         types[i] = "Unicode";

         if(encodingExists && encoding != null) {
            if(encoding.equalsIgnoreCase("utf8")) {
               this.encoding = types[i - 2];
            }
            else if(encoding.equalsIgnoreCase("gbk")) {
               this.encoding = types[i - 1];
            }
            else if(encoding.equalsIgnoreCase("unicode")) {
               this.encoding = types[i];
            }
         }

         if(this.encoding == null) {
            this.encoding = types[0];
         }
      }

      return types;
   }

   /**
    * Check if the delimiter input is enabled
    */
   public boolean canDelimit() {
      return !isTab() && isText();
   }

   public void initDelimiter() {
      if(isText() && temp != null) {
         if(temp != null) {
            InputStreamReader reader = null;
            List<String> content = null;

            try {
               String encoding = getEncoding();

               if(encoding == null) {
                  encoding = CSVInfo.getFileEncode(temp);
               }

               reader = new InputStreamReader(
                  new FileInputStream(temp), encoding);
               content = IOUtils.readLines(reader);
            }
            catch(Exception exc) {
               LOG.warn("Failed to read FileFolder", exc);
            }
            finally {
               IOUtils.closeQuietly(reader);
            }

            assert content != null;
            String[] lines = content.toArray(new String[0]);
            CSVInfo csvInfo = CSVInfo.getCSVInfo(lines);

            String dlimit = "";

            if(csvInfo != null) {
               dlimit = csvInfo.getDelimiter() + "";
            }

            if(!dlimit.equals("\t")) {
               this.delimiter = dlimit;
            }
         }
      }

      if(this.delimiter == null || this.delimiter.isEmpty()) {
         this.delimiter = ",";
      }
   }

   /**
    * Check if the file is an Excel file.
    */
   public boolean isExcel() {
      if(path == null) {
         return false;
      }

      return OneDriveFileUtil.isExcel(path);
   }

   /**
    * Check if the file is a text file.
    */
   public boolean isText() {
      if(path == null) {
         return false;
      }

      return !OneDriveFileUtil.isExcel(path);
   }

   /**
    * Check if the file should be unpivoted.
    */
   public boolean isUnpivot() {
      return !unpivotData;
   }

   /**
    * Enable column definitions.
    */
   public boolean allowColumnDefinition() {
      return path != null && !(!isText() && !isExcel());
   }

   public void loadFile() {
      if(temp != null) {
         temp.delete();
      }

      if(path == null) {
         return;
      }

      try {
         temp = File.createTempFile("oneDrive", ".dat");
         InputStream inputStream = getFile();
         FileOutputStream outputStream = new FileOutputStream(temp);
         IOUtils.copy(inputStream, outputStream);
         outputStream.close();
         inputStream.close();
         temp.deleteOnExit();
      }
      catch(Exception e) {
         LOG.error("Failed to create temp file", e);
         temp.delete();
         temp = null;
      }
   }

   public void loadFile(String sessionId) throws Exception {
      boolean noCols = this.getColumns() == null;
      loadFile();

      if(noCols) {
         this.loadColumns();
      }
   }

   public InputStream getFile() {
      return OneDriveRuntime.getFile(this);
   }

   protected ColumnDefinition[] loadColumns() throws Exception {
      return loadColumns(null);
   }

   //buttons require public classes with String parameter
   public ColumnDefinition[] loadColumns(String sessionId) {
      ColumnDefinition[] loadedDefinitions =
         OneDriveFileUtil.getColumnDefinition(this);
      setColumns(loadedDefinitions);
      return loadedDefinitions;
   }

   public OneDriveQuery clone() {
      return (OneDriveQuery) super.clone();
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(path != null) {
         writer.format("<path><![CDATA[%s]]></path>%n", path);
      }

      if(excelSheet != null) {
         writer.println(
            "<excelSheet><![CDATA[" + excelSheet + "]]></excelSheet>");
      }

      if(encoding != null) {
         writer.println("<encoding><![CDATA[" + encoding + "]]></encoding>");
      }

      if(delimiter != null) {
         writer.println("<delimiter><![CDATA[" + delimiter + "]]></delimiter>");
      }

      writer.println("<tab><![CDATA[" + tab + "]]></tab>");
      writer.println(
         "<unpivotData><![CDATA[" + unpivotData + "]]></unpivotData>");
      writer.println(
         "<firstRowHeader><![CDATA[" + firstRowHeader + "]]></firstRowHeader>");
      writer.println(
         "<removeQuotation><![CDATA[" + removeQuotation +
            "]]></removeQuotation>");

      if(headerColumnCount >= 0) {
         writer.println(
            "<headerColumnCount><![CDATA[" + headerColumnCount +
               "]]></headerColumnCount>");
      }
   }

   @Override
   protected void parseContents(Element element) throws Exception {
      super.parseContents(element);
      path = Tool.getChildValueByTagName(element, "path");

      excelSheet = Tool.getChildValueByTagName(element, "excelSheet");

      encoding = Tool.getChildValueByTagName(element, "encoding");

      delimiter = Tool.getChildValueByTagName(element, "delimiter");

      tab = "true".equals(Tool.getChildValueByTagName(element, "tab"));

      String headerColCount = Tool.getChildValueByTagName(element, "headerColumnCount");

      if(headerColCount != null) {
         headerColumnCount = Integer.parseInt(headerColCount);
      }

      unpivotData = "true".equals(Tool.getChildValueByTagName(element, "unpivotData"));

      firstRowHeader = "true".equals(Tool.getChildValueByTagName(element, "firstRowHeader"));

      removeQuotation = "true".equals(Tool.getChildValueByTagName(element, "removeQuotation"));
   }

   private String path;
   private File temp;
   private String excelSheet = null;
   private String encoding;
   private String delimiter;
   private boolean tab = false;
   private boolean unpivotData = false;
   private int headerColumnCount = 1;
   private boolean firstRowHeader = true;
   private boolean removeQuotation = true;

   private static final Logger LOG = LoggerFactory.getLogger(OneDriveQuery.class.getName());
}
