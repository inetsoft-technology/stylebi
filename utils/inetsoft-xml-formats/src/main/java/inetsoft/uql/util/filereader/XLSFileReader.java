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

import inetsoft.uql.XQuery;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.text.TextOutput;
import inetsoft.util.Tool;
import org.apache.poi.hssf.eventusermodel.*;
import org.apache.poi.hssf.record.*;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * Text file reader for Microsoft Excel 97-2003 (.xls) files.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class XLSFileReader implements ExcelFileReader {
   /**
    * Creates a new instance of <tt>XLSFileReader</tt>.
    */
   public XLSFileReader() {
      // default constructor
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableNode read(InputStream input, String encoding, XQuery query,
                          TextOutput output, int rows, int columns,
                          boolean firstRowHeader, String delimiter,
                          boolean removeQuote)
      throws Exception
   {
      return read(input, encoding, query, output, rows, columns,
                  firstRowHeader, delimiter, removeQuote, null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableNode read(InputStream input, String encoding, final XQuery query,
                          final TextOutput output, final int rows, final int columns,
                          boolean firstRowHeader, String delimiter, boolean removeQuote,
                          DateParseInfo parseInfo)
      throws Exception
   {
      DataLoader loader =
         new DataLoader(input, output, rows, columns, firstRowHeader, parseInfo);
      setTextExceedLimit(loader.getExcelLoader().isTextExceedLimit());

      return loader.getTableNode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTypeNode importHeader(InputStream input, String encoding,
                                 TextOutput output, int rowLimit, int colLimit) throws Exception
   {
      XTypeNode meta = new XTypeNode("table");
      XTableNode data = read(input, encoding, null, output, 2, -1, true, null, false);

      if(data.next()) {
         String[] formats = (String[]) data.getAttribute("formats");

         if(output.getHeaderInfo() == null) {
            for(int i = 0; i < data.getColCount(); i++) {
               XTypeNode col = XSchema.createPrimitiveType(
                  Tool.getDataType(data.getType(i)));
               col.setName(String.format("Column%d", i));
               col.setAttribute("format", formats[i]);
               meta.addChild(col);
            }
         }
         else {
            Set existing = new HashSet();

            for(int i = 0; i < data.getColCount(); i++) {
               XTypeNode col = XSchema.createPrimitiveType(
                  Tool.getDataType(data.getType(i)));
               String name = data.getName(i);
               String header = name;

               for(int k = 1; existing.contains(header); k++) {
                  header = name + " " + k;
               }

               existing.add(header);
               col.setName(header);
               col.setAttribute("format", formats[i]);
               meta.addChild(col);
            }
         }
      }

      data.close();
      return meta;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] getSheetNames(InputStream input) throws Exception {
      return new SheetLoader().getSheetNames(input);
   }

   public boolean isTextExceedLimit() {
      return textExceedLimit;
   }

   /**
    * Set whether text is limit cell size
    */
   public void setTextExceedLimit(boolean textExceedLimit) {
      this.textExceedLimit = textExceedLimit;
   }

   private boolean textExceedLimit = false;
   private static final Logger LOG = LoggerFactory.getLogger(XLSFileReader.class);

   private static final class DataLoader extends AbortableHSSFListener {
      public DataLoader(InputStream input, TextOutput output, int rows,
                        int columns, boolean firstRowHeader,
                        DateParseInfo parseInfo)
         throws Exception
      {
         this.output = output;
         this.firstRowHeader = firstRowHeader;
         int maxRows = rows == 0 ? -1 : rows;

         this.currentSheet = -1;
         int columnCount = 0;
         this.dateFlag = false;
         this.extendedFormatIndex = 0;
         this.strings = null;

         InputStream dinput = null;

         POIFSFileSystem poifs = new POIFSFileSystem(input);
         HSSFEventFactory factory = new HSSFEventFactory();

         String sheet = ((ExcelFileInfo) output.getBodyInfo()).getSheet();

         try {
            try {
               ColumnCountListener listener = new ColumnCountListener(sheet);

               HSSFRequest request = new HSSFRequest();
               request.addListener(listener, BOFRecord.sid);
               request.addListener(listener, EOFRecord.sid);
               request.addListener(listener, BoundSheetRecord.sid);
               request.addListener(listener, BoolErrRecord.sid);
               request.addListener(listener, FormulaRecord.sid);
               request.addListener(listener, LabelSSTRecord.sid);
               request.addListener(listener, NumberRecord.sid);

               dinput = poifs.createDocumentInputStream("Workbook");
               factory.processEvents(request, dinput);

               columnCount = listener.getColumnCount();
               this.targetSheet = listener.getSheetIndex();
            }
            finally {
               if(dinput != null) {
                  try {
                     dinput.close();
                  }
                  catch(Throwable exc) {
                     LOG.warn(
                        "Failed to close document stream", exc);
                  }
               }
            }

            ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

            if(info.getEndColumn() >= 0) {
               columnCount = Math.min(columnCount, info.getEndColumn() + 1);
            }

            if(info.getStartColumn() >= 0) {
               columnCount -= info.getStartColumn();
               columnCount = Math.max(columnCount, 0);
            }

            if(columns >= 0) {
               columnCount = Math.min(columnCount, columns);
            }

            loader = new ExcelLoader(output, firstRowHeader) {
               @Override
               protected Date getJavaDate(double value) {
                  return DateUtil.getJavaDate(value, dateFlag);
               }

               @Override
               protected boolean isADateFormat(int index, String pattern) {
                  return DateUtil.isADateFormat(index, pattern);
               }
            };

            loader.setDateParseInfo(parseInfo);
            columnCount = Math.max(columnCount, 0);
            loader.setMaxRows(maxRows);
            loader.init(columnCount);
            dinput = null;

            try {
               HSSFRequest request = new HSSFRequest();
               request.addListener(this, BOFRecord.sid);
               request.addListener(this, EOFRecord.sid);
               request.addListener(this, DateWindow1904Record.sid);
               request.addListener(this, ExtendedFormatRecord.sid);
               request.addListener(this, FormatRecord.sid);
               request.addListener(this, SSTRecord.sid);
               request.addListener(this, BoolErrRecord.sid);
               request.addListener(this, FormulaRecord.sid);
               request.addListener(this, LabelSSTRecord.sid);
               request.addListener(this, NumberRecord.sid);
               request.addListener(this, StringRecord.sid);

               dinput = poifs.createDocumentInputStream("Workbook");
               factory.processEvents(request, dinput);

               loader.finish();
            }
            finally {
               if(dinput != null) {
                  try {
                     dinput.close();
                  }
                  catch(Throwable exc) {
                     LOG.warn(
                        "Failed to close document stream", exc);
                  }
               }
            }
         }
         finally {
            try {
               input.close();
            }
            catch(Throwable exc) {
               LOG.warn("Failed to close input stream", exc);
            }
         }
      }

      /**
       * Gets the parsed table node.
       *
       * @return the table node.
       */
      public XTableNode getTableNode() {
         return loader.getTableNode();
      }

      public Map<Integer, String> getProspectTypeMap() {
         return prospectTypeMap;
      }

      /**
       * Gets the excel loader.
       *
       * @return ExcelLoader loader.
       */
      public ExcelLoader getExcelLoader() {
         return this.loader;
      }

      /**
       * Gets the column index of a cell record.
       *
       * @param record the cell record.
       *
       * @return the column index.
       */
      private int getColumn(CellRecord record) {
         int col = ((int) record.getColumn()) & 0xffff;
         ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

         if(info.getEndColumn() >= 0 && col > info.getEndColumn()) {
            col = -1;
         }
         else if(info.getStartColumn() >= 0) {
            col -= info.getStartColumn();
         }

         return col;
      }

      /**
       * Set the boolean value in the current row for a formula record.
       *
       * @param value  the cell value.
       * @param record the cell record.
       */
      private void setBooleanFormulaValue(boolean value, CellRecord record) {
         loader.setBooleanFormulaValue(value, getColumn(record));
      }

      /**
       * Sets the value in the current row for a cell record.
       *
       * @param value  the cell value.
       * @param record the cell record.
       */
      private void setValue(double value, CellRecord record) {
         int cindex = getColumn(record);
         int exfmtIndex = ((int) record.getXFIndex()) & 0xffff;
         loader.setValue(value, cindex, exfmtIndex);
      }

      /**
       * Processes an HSSF record.
       *
       * @param record the record to process.
       *
       * @return result code of zero for continued processing.
       */
      @Override
      public short abortableProcessRecord(Record record) {
         short result = 0;

         switch(record.getSid()) {
            case BOFRecord.sid:
               BOFRecord bofrecord = (BOFRecord) record;
               int rtype = bofrecord.getType();

               if(rtype == BOFRecord.TYPE_WORKSHEET ||
                  rtype == BOFRecord.TYPE_CHART && fileStack == 0)
               {
                  ++currentSheet;

                  if(currentSheet > targetSheet) {
                     result = 1;
                  }
               }

               ++fileStack;
               break;

            case SSTRecord.sid:
               this.strings = (SSTRecord) record;
               break;

            case DateWindow1904Record.sid:
               this.dateFlag =
                  (((DateWindow1904Record) record).getWindowing() == 1);
               break;

            case ExtendedFormatRecord.sid:
               ExtendedFormatRecord exrecord = (ExtendedFormatRecord) record;

               if(exrecord.getFormatIndex() != 0) {
                  loader.getStyleTable().addFormatReference(
                     extendedFormatIndex,
                     ((int) exrecord.getFormatIndex()) & 0xffff);
               }

               ++extendedFormatIndex;
               break;

            case FormatRecord.sid:
               FormatRecord frecord = (FormatRecord) record;
               loader.getStyleTable().addFormat(
                  frecord.getIndexCode(), frecord.getFormatString());
               break;

            case BoolErrRecord.sid:
               if(currentSheet == targetSheet) {
                  BoolErrRecord erecord = (BoolErrRecord) record;

                  if(loader.addRow(erecord.getRow())) {
                     int cindex = getColumn(erecord);

                     if(erecord.isBoolean()) {
                        loader.setBooleanFormulaValue(
                           erecord.getBooleanValue(), cindex);
                     }
                  }
                  else {
                     result = 2;
                  }
               }

               break;

            case FormulaRecord.sid:
               if(currentSheet == targetSheet) {
                  FormulaRecord fxrecord = (FormulaRecord) record;

                  if(loader.addRow(fxrecord.getRow())) {
                     if(getColumn(fxrecord) >= 0) {
                        if(fxrecord.getCachedResultType() ==
                           CellType.BOOLEAN.getCode())
                        {
                           setBooleanFormulaValue(
                              fxrecord.getCachedBooleanValue(), fxrecord);
                        }
                        else if(fxrecord.getCachedResultType() ==
                           CellType.STRING.getCode())
                        {
                           nextStringRecord = true;
                           nextColumn = fxrecord.getColumn();
                        }
                        else {
                           setValue(fxrecord.getValue(), fxrecord);
                        }
                     }
                  }
                  else {
                     result = 2;
                  }
               }

               break;

            case StringRecord.sid:
               if(currentSheet == targetSheet && nextStringRecord) {
                  loader.setStringFormulaValue(
                     ((StringRecord) record).getString(), nextColumn);
                  nextStringRecord = false;
               }

               break;

            case LabelSSTRecord.sid:
               if(currentSheet == targetSheet) {
                  LabelSSTRecord lrecord = (LabelSSTRecord) record;

                  if(loader.addRow(lrecord.getRow())) {
                     int col = getColumn(lrecord);
                     loader.setString(strings.getString(lrecord.getSSTIndex()).getString(), col);
                  }
                  else {
                     result = 2;
                  }
               }

               break;

            case NumberRecord.sid:
               if(currentSheet == targetSheet) {
                  NumberRecord nrecord = (NumberRecord) record;

                  if(loader.addRow(nrecord.getRow())) {
                     setValue(nrecord.getValue(), nrecord);
                  }
                  else {
                     result = 2;
                  }
               }

               break;

            case EOFRecord.sid:
               --fileStack;
               break;

            default:
               break;
         }

         return result;
      }

      private ExcelLoader loader;
      private TextOutput output;

      private int currentSheet = -1;
      private int targetSheet = 0;
      private boolean dateFlag = false;
      private int extendedFormatIndex = 0;
      private SSTRecord strings = null;
      private int fileStack = 0;
      private boolean nextStringRecord = false;
      private int nextColumn = -1;
      private boolean firstRowHeader;
      private Map<Integer, String> prospectTypeMap = new HashMap<>();
   }

   /**
    * HSSF event listener that finds the number of columns in an Excel sheet.
    * This is used to handle invalid Excel files that have incorrect last column
    * fields in the row records.
    *
    * @author InetSoft Technology
    */
   private static final class ColumnCountListener extends AbortableHSSFListener
   {
      /**
       * Creates a new instance of <tt>ColumnCountListener</tt>.
       *
       * @param sheet the sheet of which to get the column count.
       */
      public ColumnCountListener(String sheet) {
         this.sheet = sheet;
      }

      /**
       * Processes an HSSF record.
       *
       * @param record the record to process.
       */
      @Override
      public short abortableProcessRecord(Record record) {
         switch(record.getSid()) {
            case BOFRecord.sid:
               BOFRecord brecord = (BOFRecord) record;
               int rtype = brecord.getType();

               if(rtype == BOFRecord.TYPE_WORKSHEET ||
                  rtype == BOFRecord.TYPE_CHART && fileStack == 0)
               {
                  ++currentSheet;

                  if(currentSheet > targetSheet) {
                     return 1;
                  }
               }

               ++fileStack;
               break;

            case BoundSheetRecord.sid:
               if(!sheetFound) {
                  ++targetSheet;

                  if(sheet.equals(((BoundSheetRecord) record).getSheetname())) {
                     sheetFound = true;
                  }
               }

               break;

            case BoolErrRecord.sid:
            case FormulaRecord.sid:
            case NumberRecord.sid:
            case LabelSSTRecord.sid:
               if(currentSheet == targetSheet) {
                  CellRecord crecord = (CellRecord) record;
                  maxColumn = Math.max(
                     maxColumn, ((int) crecord.getColumn()) & 0xffff);
               }

               break;

            case EOFRecord.sid:
               --fileStack;
               break;

            default:
               break;
         }

         return 0;
      }

      /**
       * Gets the number of columns in the sheet.
       *
       * @return the column count.
       */
      public int getColumnCount() {
         return maxColumn + 1;
      }

      /**
       * Gets the index of the named sheet.
       *
       * @return the sheet index.
       */
      public int getSheetIndex() {
         if(!sheetFound) {
            throw new IllegalStateException("Failed to find sheet: " + sheet);
         }

         return targetSheet;
      }

      private final String sheet;
      private boolean sheetFound = false;
      private int targetSheet = -1;
      private int currentSheet = -1;
      private int maxColumn = Integer.MIN_VALUE;
      private int fileStack = 0;
   }

   private static final class SheetLoader implements HSSFListener {
      public SheetLoader() {
         sheetNames = new ArrayList<>();
      }

      @Override
      public void processRecord(Record record) {
         if(record.getSid() == BoundSheetRecord.sid){
            BoundSheetRecord srecord = (BoundSheetRecord) record;
            sheetNames.add(srecord.getSheetname());
         }
      }

      public String[] getSheetNames(InputStream input) throws Exception {
         sheetNames.clear();
         InputStream dinput = null;

         try {
            HSSFRequest request = new HSSFRequest();
            request.addListener(this, BoundSheetRecord.sid);

            POIFSFileSystem poifs = new POIFSFileSystem(input);
            dinput = poifs.createDocumentInputStream("Workbook");
            HSSFEventFactory factory = new HSSFEventFactory();
            factory.processEvents(request, dinput);
         }
         finally {
            if(dinput != null) {
               dinput.close();
            }
         }

         return sheetNames.toArray(new String[sheetNames.size()]);
      }

      private final List<String> sheetNames;
   }
}