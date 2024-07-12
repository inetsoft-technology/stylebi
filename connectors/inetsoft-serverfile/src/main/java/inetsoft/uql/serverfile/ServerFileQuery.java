/*
 * inetsoft-serverfile - StyleBI is a business intelligence web application.
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
package inetsoft.uql.serverfile;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SuppressWarnings("unused")
@View(vertical = false, value = {
   @View1(value = "fileFolder", align=ViewAlign.FILL),
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
public class ServerFileQuery extends SelectableTabularQuery {
   /**
    * Creates a new instance of <tt>ServerFileQuery</tt>.
    */
   public ServerFileQuery() {
      super(ServerFileDataSource.TYPE);
   }

   /**
    * Get the file or folder to be opened. By default, the value should be the
    * same as the file/folder in ServerFileDataSource.
    */
   @Property(
      label = "File/Folder",
      pattern = {".*", "^.*\\.(txt|csv|xls|xlsx)$"},
      required = true)
   @PropertyEditor(
      editorProperties = {
         @EditorProperty(name = "relativeTo", method = "getRootFolder"),
         @EditorProperty(name = "acceptTypes", value = ".txt,.csv,.xls,.xlsx")
      }
   )
   public File getFileFolder() {
      if(relativeFilePath == null) {
         return null;
      }

      if(getDataSource() != null) {
         ServerFileDataSource ds = (ServerFileDataSource) getDataSource();

         if(ds.getFile() != null) {
            return new File(ds.getFile(), relativeFilePath);
         }
      }

      return new File(relativeFilePath);
   }

   /**
    * Set the file/folder.
    */
   public void setFileFolder(File file) {
      if(file == null) {
         relativeFilePath = null;
      }
      else if(getDataSource() != null) {
         ServerFileDataSource ds = (ServerFileDataSource) getDataSource();

         if(ds.getFile() != null) {
            Path root = ds.getFile().toPath().toAbsolutePath();
            Path path = file.toPath().toAbsolutePath();
            relativeFilePath = root.relativize(path).toString();
         }
         else {
            relativeFilePath = file.getPath();
         }
      }
      else {
         relativeFilePath = file.getPath();
      }
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

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      if(relativeFilePath != null) {
         writer.println(
            "<fileFolder><![CDATA[" + relativeFilePath + "]]></fileFolder>");
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

      super.writeContents(writer);
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element node = Tool.getChildNodeByTagName(root, "fileFolder");

      if(node != null) {
         String path = Tool.getValue(node);

         if(!(path == null || path.isEmpty())) {
            Path oldPath = Paths.get(path);

            if(oldPath.isAbsolute() && getDataSource() != null &&
               ((ServerFileDataSource) getDataSource()).getFile() != null)
            {
               Path rootPath = ((ServerFileDataSource) getDataSource())
                  .getFile().toPath().toAbsolutePath();
               relativeFilePath = rootPath.relativize(oldPath).toString();
            }
            else {
               relativeFilePath = path;
            }
         }
      }

      node = Tool.getChildNodeByTagName(root, "excelSheet");
      excelSheet = Tool.getValue(node);

      node = Tool.getChildNodeByTagName(root, "encoding");
      encoding = Tool.getValue(node);

      node = Tool.getChildNodeByTagName(root, "delimiter");
      delimiter = Tool.getValue(node);

      node = Tool.getChildNodeByTagName(root, "tab");
      tab = "true".equals(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(root, "headerColumnCount");
      headerColumnCount = Integer.parseInt(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(root, "unpivotData");
      unpivotData = "true".equals(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(root, "firstRowHeader");
      firstRowHeader = "true".equals(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(root, "removeQuotation");
      removeQuotation = "true".equals(Tool.getValue(node));
   }

   /**
    * Get the root folder set in ServerFileDataSource.
    */
   public String getRootFolder() {
      ServerFileDataSource datasource = (ServerFileDataSource) getDataSource();
      return datasource.getFile().getAbsolutePath();
   }

   /**
    * Get the Excel sheet names.
    */
   public String[] getExcelSheetNames() {
      FileInputStream fileInput = null;
      String[] sheets = new String[1];
      File firstFile = ServerFileUtil.getFirstFile(getFileFolder(), ServerFileUtil.FileType.EXCEL);

      if(firstFile == null || !firstFile.exists()) {
         return new String[]{""};
      }

      try {
         fileInput = new FileInputStream(firstFile.getAbsolutePath());

         if(firstFile.getName().endsWith(".xlsx")) {
            ExcelFileReader xlsx = ExcelFileSupport.getInstance().createXLSXReader();
            sheets = xlsx.getSheetNames(fileInput);
         }
         else if(firstFile.getName().endsWith("xls")) {
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
      if(relativeFilePath == null) {
         return new String[0];
      }

      String[] types = new String[3];
      boolean encodingExists = true;
      String encoding = null;
      File firstFile = ServerFileUtil.getFirstFile(getFileFolder(), ServerFileUtil.FileType.TEXT);

      try {
         if(firstFile != null &&
            (this.encoding == null || this.encoding.isEmpty()))
         {
            encoding = CSVInfo.getFileEncode(firstFile);
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
      if(isText()) {
         File firstFile = ServerFileUtil.getFirstFile(getFileFolder(), ServerFileUtil.FileType.TEXT);

         if(firstFile != null) {
            InputStreamReader reader = null;
            List<String> content = null;

            try {
               String encoding = getEncoding();

               if(encoding == null) {
                  encoding = CSVInfo.getFileEncode(firstFile);
               }

               reader = new InputStreamReader(
                  new FileInputStream(firstFile), encoding);
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
      if(relativeFilePath == null) {
         return false;
      }

      File file = getFileFolder();
      return file.isDirectory() || ServerFileUtil.isExcel(file.getAbsolutePath());
   }

   /**
    * Check if the file is a text file.
    */
   public boolean isText() {
      if(relativeFilePath == null) {
         return false;
      }

      File file = getFileFolder();
      return file.isDirectory() || !ServerFileUtil.isExcel(file.getAbsolutePath());
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
      return getFileFolder() != null && !(!isText() && !isExcel());
   }

   protected ColumnDefinition[] loadColumns() throws Exception {
      return loadColumns(null);
   }

   //buttons require public classes with String parameter
   public ColumnDefinition[] loadColumns(String sessionId) {
      ColumnDefinition[] loadedDefinitions =
         ServerFileUtil.getColumnDefinition(this);
      setColumns(loadedDefinitions);
      return loadedDefinitions;
   }

   public ServerFileQuery clone() {
      return (ServerFileQuery) super.clone();
   }

   private String relativeFilePath;
   private String excelSheet = null;
   private String encoding;
   private String delimiter;
   private boolean tab = false;
   private boolean unpivotData = false;
   private int headerColumnCount = 1;
   private boolean firstRowHeader = true;
   private boolean removeQuotation = true;

   private static final Logger LOG = LoggerFactory.getLogger(ServerFileQuery.class.getName());
}
