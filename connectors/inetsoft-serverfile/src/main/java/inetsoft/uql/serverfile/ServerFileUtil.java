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
package inetsoft.uql.serverfile;

import inetsoft.uql.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.uql.tabular.DataType;
import inetsoft.uql.text.TextOutput;
import inetsoft.uql.util.filereader.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Util support methods for ServerFiles
 */
@SuppressWarnings("unused")
public class ServerFileUtil {
   /**
    * Default Constructor.
    */
   public ServerFileUtil() {
   }

   /**
    * Read the excel file using XLS(X)FileReader
    *
    * @param file excel file to be read.
    * @return XTableNode
    */
   protected static XTableNode readExcel(File file, ServerFileQuery query,
      ColumnDefinition[] columnDef)
   {
      TextOutput toutput = new TextOutput();
      FileInputStream input;
      ExcelFileReader excelReader = null;
      XTableNode node = null;
      String sheet =
         query.getExcelSheet() == null ? "Sheet1" : query.getExcelSheet();
      boolean firstRow = query.isFirstRowHeader();

      try {
         if(firstRow) {
            ExcelFileInfo headerInfo = new ExcelFileInfo();
            headerInfo.setSheet(sheet);
            headerInfo.setStartRow(0);
            headerInfo.setEndRow(0);
            headerInfo.setStartColumn(0);
            headerInfo.setEndColumn(-1);
            toutput.setHeaderInfo(headerInfo);
         }

         ExcelFileInfo bodyInfo = new ExcelFileInfo();
         bodyInfo.setSheet(sheet);
         bodyInfo.setStartRow(firstRow ? 1 : 0);
         bodyInfo.setEndRow(-1);
         bodyInfo.setStartColumn(0);
         bodyInfo.setEndColumn(-1);
         toutput.setBodyInfo(bodyInfo);

         XTypeNode header = getExcelHeader(file, query.getEncoding(),
            sheet, firstRow);

         // if couldn't read the header then skip this file
         if(header == null) {
            return null;
         }

         setupColumnTypes(query, toutput, columnDef, header);

         if(file.getAbsolutePath().endsWith(".xlsx")) {
            // use XLSXFileReader
            excelReader = ExcelFileSupport.getInstance().createXLSXReader();
         }
         else {
            // use XLSFileReader
            excelReader = ExcelFileSupport.getInstance().createXLSReader();
         }

         input = new FileInputStream(file);
         node = excelReader.read(input, "UTF8", null, toutput, -1,
            header.getChildCount(), query.isFirstRowHeader(),
            null, false);
      }
      catch(FileNotFoundException exc) {
         LOG.error("Failed to execute query. File Not Found", exc);
      }
      catch(Exception exc) {
         LOG.error("Failed to read excel file.", exc);
      }

      return node;
   }

   /**
    * Read text file using DelimitedFileReader
    *
    * @param file  text file to be read.
    * @param query ServerFileQuery
    * @return XTableNode
    */
   protected static XTableNode readText(File file, ServerFileQuery query,
      ColumnDefinition[] columnDef) throws Exception
   {
      XTableNode node = null;
      TextOutput toutput = new TextOutput();
      DelimitedFileInfo info = new DelimitedFileInfo();
      DelimitedFileReader filereader = new DelimitedFileReader();
      String delimiter;

      if(query.isTab()) {
         delimiter = "\t";
      }
      else {
         delimiter = query.getDelimiter();
      }

      XTypeNode header = getTextHeader(file, query.getEncoding(),
         delimiter, query.isRemoveQuotation(), query.isFirstRowHeader());
      setupColumnTypes(query, toutput, columnDef, header);

      try {
         FileInputStream input = new FileInputStream(file);
         String encoding = query.getEncoding();
         node = filereader.read(
            input, encoding, null, toutput, -1, header.getChildCount(),
            query.isFirstRowHeader(), delimiter, query.isRemoveQuotation());
         XSelection spec = toutput.getTableSpec();

         if(spec != null) {
            node = spec.select(node);
         }
      }
      catch(FileNotFoundException exc) {
         LOG.error("Failed to execute query. File Not Found", exc);
      }
      catch(Exception exc) {
         LOG.error("Failed to read text file.", exc);
      }

      return node;
   }

   /**
    * Helper method for generating file column definitions.
    *
    * @param query getting column definition.
    * @return array of column definitions.
    */
   protected static ColumnDefinition[] getColumnDefinition(XQuery query) {
      ServerFileQuery sfQuery = (ServerFileQuery) query;
      File file = getFirstFile(sfQuery.getFileFolder());

      if(file == null) {
         return null;
      }

      String delimiter = sfQuery.isTab() ? "\t" : sfQuery.getDelimiter();
      String encoding = sfQuery.getEncoding() == null ? "UTF8" : sfQuery
         .getEncoding();
      ColumnDefinition[] columnDefinitions = null;
      String filePath = file.getAbsolutePath();
      XTypeNode node = null;

      // get header information
      if(isExcel(filePath)) {
         try {
            String sheet = sfQuery.getExcelSheet();

            if(Tool.isEmptyString(sheet)) {
               sheet = sfQuery.getExcelSheetNames()[0];
            }

            node = getExcelHeader(file, encoding, sheet,
               sfQuery.isFirstRowHeader());
         }
         catch(Exception exc) {
            LOG.error("Failed to retrieve excel column definitions.", exc);
         }
      }
      // text file could end with any suffix (e.g. tbl)
      else { // if(isText(filePath)) {
         try {
            node = getTextHeader(file, encoding, delimiter,
               sfQuery.isRemoveQuotation(), sfQuery.isFirstRowHeader());
         }
         catch(Exception exc) {
            LOG.error("Failed to retrieve text column definitions.", exc);
         }
      }

      //populate column definitions with header information
      if(node != null && node.getChildCount() > 0) {
         if(sfQuery.isUnpivotData()) {
            columnDefinitions = getUnpivotedDefinitions(sfQuery, node);
         }
         else {
            columnDefinitions = new ColumnDefinition[node.getChildCount()];

            for(int i = 0; i < columnDefinitions.length; i++) {
               ColumnDefinition column = new ColumnDefinition();
               XTypeNode childNode = (XTypeNode) node.getChild(i);
               column.setType(DataType.fromType(childNode.getType()));
               column.setFormat(childNode.getFormat());
               column.setSelected(true);
               column.setName(sfQuery.isFirstRowHeader() ? childNode
                  .getName() : "Column" + i);
               column.setAlias(column.getName());
               columnDefinitions[i] = column;
            }
         }
      }

      return columnDefinitions;
   }

   protected static XTypeNode getTextHeader(File file, String encoding,
      String delimiter, boolean removeQuote, boolean firstRow)
   {
      TextOutput toutput = new TextOutput();

      if(firstRow) {
         DelimitedFileInfo headerFileInfo = new DelimitedFileInfo();
         headerFileInfo.setDelimiter(delimiter);
         headerFileInfo.setRemoveQuotation(removeQuote);
         toutput.setHeaderInfo(headerFileInfo);
      }

      DelimitedFileInfo bodyFileInfo = new DelimitedFileInfo();
      bodyFileInfo.setDelimiter(delimiter);
      bodyFileInfo.setRemoveQuotation(removeQuote);
      toutput.setBodyInfo(bodyFileInfo);

      DelimitedFileReader filereader = new DelimitedFileReader();
      FileInputStream input = null;
      XTypeNode header = null;

      try {
         input = new FileInputStream(file);

         if(file.getAbsolutePath().endsWith(".csv")) {
            header = filereader.importHeader(input, encoding, toutput, true, false);
         }
         else {
            header = filereader.importHeader(input, encoding, toutput, false, true);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to read the text header", ex);
      }
      finally {
         try {
            input.close();
         }
         catch(IOException ex) {
            LOG.warn("Failed to close file", ex);
         }
      }

      return header;
   }

   protected static XTypeNode getExcelHeader(File file, String encoding,
      String sheet, boolean firstRow)
   {
      String filepath = file.getAbsolutePath();
      ExcelFileReader fr;

      if(filepath.endsWith(".xlsx")) {
         fr = ExcelFileSupport.getInstance().createXLSXReader();
      }
      else {
         fr = ExcelFileSupport.getInstance().createXLSReader();
      }

      TextOutput toutput = new TextOutput();

      if(firstRow) {
         ExcelFileInfo headerInfo = new ExcelFileInfo();
         headerInfo.setSheet(sheet);
         headerInfo.setStartRow(0);
         headerInfo.setEndRow(0);
         headerInfo.setStartColumn(0);
         headerInfo.setEndColumn(-1);
         toutput.setHeaderInfo(headerInfo);
      }

      ExcelFileInfo bodyInfo = new ExcelFileInfo();
      bodyInfo.setSheet(sheet);
      bodyInfo.setStartRow(firstRow ? 1 : 0);
      bodyInfo.setEndRow(-1);
      bodyInfo.setStartColumn(0);
      bodyInfo.setEndColumn(-1);
      toutput.setBodyInfo(bodyInfo);

      FileInputStream input = null;
      XTypeNode header = null;

      try {
         input = new FileInputStream(file);
         header = fr.importHeader(input, encoding, toutput, 0, -1);
      }
      catch(Exception ex) {
         LOG.warn("Failed to read excel header", ex);
      }
      finally {
         try {
            input.close();
         }
         catch(IOException ex) {
            LOG.warn("Failed to close file", ex);
         }
      }

      return header;
   }

   /**
    * Method to recursively iterate through folders until it reaches
    * a text or excel file.
    */
   protected static File getFirstFile(File file) {
      return getFirstFile(file, null);
   }

   protected static File getFirstFile(File file, FileType type) {
      if(file == null || !file.isDirectory()) {
         return file;
      }

      File[] files = file.listFiles();
      assert files != null;
      Arrays.sort(files, new Comparator<File>() {
         @Override
         public int compare(File file1, File file2) {
            return file1.getName().compareTo(file2.getName());
         }
      });

      for(File child : files) {
         String filePath = child.getAbsolutePath();

         if(child.isDirectory()) {
            file = getFirstFile(child, type);

            if(file == null) {
               continue;
            }
            else {
               return file;
            }
         }

         if(type == FileType.TEXT && isText(filePath)) {
            return child;
         }
         else if(type == FileType.EXCEL && isExcel(filePath)) {
            return child;
         }
         else if(type == null && (isText(filePath) || isExcel(filePath))) {
            return child;
         }
      }

      return null;
   }

   public static boolean isText(String filePath) {
      return filePath.endsWith(".csv") || filePath.endsWith(".txt");
   }

   public static boolean isExcel(String filePath) {
      return filePath.endsWith(".xlsx") || filePath.endsWith(".xls");
   }

   public static FileType getFileType(File file) {
      if(isText(file.getAbsolutePath())) {
         return FileType.TEXT;
      }
      else if(isExcel(file.getAbsolutePath())) {
         return FileType.EXCEL;
      }

      return null;
   }

   public static List<File> getFileList(File dir, final FileType type) {
      List<File> list = new ArrayList<>();

      for(File file : dir.listFiles()) {
         String path = file.getAbsolutePath();

         if(file.isDirectory()) {
            list.addAll(getFileList(file, type));
         }
         else if(type == FileType.TEXT && isText(path)) {
            list.add(file);
         }
         else if(type == FileType.EXCEL && isExcel(path)) {
            list.add(file);
         }
         else if(type == null && (isText(path) || isExcel(path))) {
            list.add(file);
         }
      }

      return list;
   }

   /**
    * If query is unpivoted, alter column definitions to reflect this.
    *
    * @param node header information.
    * @return Column Definitions.
    */
   private static ColumnDefinition[] getUnpivotedDefinitions(XQuery xquery,
      XTypeNode node)
   {
      ServerFileQuery query = (ServerFileQuery) xquery;
      int definitionSize = Math.max(1, Math
         .min(node.getChildCount() - 1, query.getHeaderColumnCount()));

      ColumnDefinition[] columnDefinitions = new ColumnDefinition[definitionSize + 2];

      for(int i = 0; i < definitionSize; i++) {
         ColumnDefinition column = new ColumnDefinition();
         XTypeNode childNode = (XTypeNode) node.getChild(i);
         column.setType(DataType.fromType(childNode.getType()));
         column.setFormat(childNode.getFormat());
         column.setSelected(true);
         column.setAlias(childNode.getName());
         column.setName(childNode.getName());
         columnDefinitions[i] = column;
      }

      columnDefinitions[definitionSize] = new ColumnDefinition();
      columnDefinitions[definitionSize].setName("Dimension");
      columnDefinitions[definitionSize].setAlias("Dimension");
      columnDefinitions[definitionSize].setType(DataType.STRING);
      columnDefinitions[definitionSize].setSelected(true);

      columnDefinitions[definitionSize + 1] = new ColumnDefinition();
      columnDefinitions[definitionSize + 1].setName("Measure");
      columnDefinitions[definitionSize + 1].setAlias("Measure");
      columnDefinitions[definitionSize + 1].setType(DataType.STRING);
      columnDefinitions[definitionSize + 1].setSelected(true);

      return columnDefinitions;
   }

   private static void setupColumnTypes(ServerFileQuery query, TextOutput tout,
      ColumnDefinition[] columns, XTypeNode header)
   {
      XSelection spec = new XSelection();
      List<Integer> selList = new ArrayList<>();
      int headerColumnCount = Math.max(1, Math.min(query.getHeaderColumnCount(),
         header.getChildCount() - 1));

      for(int i = 0; i < header.getChildCount(); i++) {
         ColumnDefinition column;

         if(query.isUnpivotData() && i >= headerColumnCount) {
            column = columns[columns.length - 1];
         }
         else {
            if(i < columns.length) {
               column = columns[i];
            }
            else {
               break;
            }
         }

         XNode child = header.getChild(i);
         String name = "column" + i;
         String alias = column.getAlias() == null || column.getAlias()
            .isEmpty() ? column.getName() : column.getAlias();
         spec.addColumn(name);
         spec.setAlias(i, alias);
         spec.setConversion(name, column.getType().type(), column.getFormat());
         spec.setFormatFixed(name, true);

         if(column.isSelected()) {
            selList.add(i);
         }
      }

      int[] sel = new int[selList.size()];

      for(int i = 0; i < sel.length; i++) {
         sel[i] = selList.get(i);
      }

      tout.setTableSpec(spec);
      tout.setSelectedCols(sel);
   }

   public enum FileType {
      TEXT, EXCEL
   }

   private static final Logger LOG = LoggerFactory.getLogger(ServerFileRuntime.class.getName());
}
