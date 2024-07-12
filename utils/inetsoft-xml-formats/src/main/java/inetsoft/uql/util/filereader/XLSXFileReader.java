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
import inetsoft.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Text file reader for Microsoft Microsoft Excel Office Open XML (.xlsx) files.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class XLSXFileReader implements ExcelFileReader {
   /**
    * Creates a new instance of <tt>XLSXFileReader</tt>.
    */
   public XLSXFileReader() {
      // default constructor
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTableNode read(InputStream input, String encoding, final XQuery query,
                          final TextOutput output, final int rows, final int columns,
                          boolean firstRowHeader, String delimiter, boolean removeQuote)
      throws Exception
   {
      return read(input, encoding, query, output, rows, columns, firstRowHeader,
         delimiter, removeQuote, null);
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
      ExcelLoader loader = read(input, new ZipCallable<ExcelLoader>() {
         @Override
         public ExcelLoader call() throws Exception {
            ZipFile zip = getZipFile();

            String entry = ContentTypesHandler.getWorkbookEntry(zip);
            String sheet = ((ExcelFileInfo) output.getBodyInfo()).getSheet();
            String id = null;

            for(String[] pair : WorkbookHandler.getSheets(zip, entry)) {
               // if sheet is not set, use the first sheet
               if(StringUtils.isBlank(sheet) || sheet.equals(pair[0])) {
                  id = pair[1];
                  break;
               }
            }

            if(id == null) {
               throw new IllegalStateException("Sheet " + sheet + " not found");
            }

            int index = entry.lastIndexOf('/');
            entry = entry.substring(0, index) + "/_rels" + entry.substring(index) + ".rels";

            String[] entries = RelationshipsHandler.getEntries(zip, entry, id);

            entry = entry.substring(0, index + 1) + entries[1];
            final String sharedStringsEntry = entry;

            // (44863) like styleEntry below, if the entry cannot be found using relative path
            // and the entry starts with a slash, use the absolute path to find the workbook entry.
            if(entries[1] != null && entries[1].charAt(0) == '/' &&
               zip.stream().noneMatch(e -> e.getName().equals(sharedStringsEntry)))
            {
               entry = entries[1].substring(1);
            }

            String[] stringTable = StringTableHandler.getStrings(zip, entry);

            entry = entry.substring(0, index + 1) + entries[2];
            final String styleEntry = entry;

            // the leading slash suggests that it is an absolute path to the entry in the
            // workbook, so find it that way instead of relative to the workbook entry (office 2007)
            if(entries[2] != null && entries[2].charAt(0) == '/' &&
               zip.stream().noneMatch(e -> e.getName().equals(styleEntry)))
            {
               entry = entries[2].substring(1);
            }

            ExcelLoader loader = new ExcelLoader(output, firstRowHeader) {
               @Override
               protected Date getJavaDate(double value) {
                  return DateUtil.getJavaDate(value);
               }

               @Override
               protected boolean isADateFormat(int index, String pattern) {
                  return DateUtil.isADateFormat(index, pattern);
               }
            };

            loader.setDateParseInfo(parseInfo);
            StyleTableHandler.getStyles(zip, entry, loader);

            String prefix = styleEntry.substring(0, index + 1);
            String sheetEntry = prefix + entries[0];
            final String sheetEntry0 = sheetEntry;

            // if constructed name is not in zip, try to find it in zip by matching partial name
            if(!zip.stream().anyMatch(e -> ((ZipEntry) e).getName().equals(sheetEntry0))) {
               sheetEntry = zip.stream()
                  .map(e -> e.getName())
                  .filter(e -> entries[0].endsWith(e))
                  .findFirst()
                  .orElse(null);
            }

            return WorksheetHandler.read(zip, sheetEntry, stringTable, loader, output, rows,
               columns);
         }
      });

      this.exceedLimit = loader.isExceedLimit();
      this.mixedTypeColumns = loader.isMixedTypeColumns();
      this.textExceedLimit = loader.isTextExceedLimit();

      return loader.getTableNode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTypeNode importHeader(InputStream input, String encoding, TextOutput output,
                                 int rowLimit, int colLimit)
      throws Exception
   {
      return importHeader(input, encoding, output, rowLimit, colLimit, new DateParseInfo());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XTypeNode importHeader(InputStream input, String encoding, TextOutput output,
                                 int rowLimit, int colLimit, DateParseInfo parseInfo)
      throws Exception
   {
      XTypeNode meta = new XTypeNode("table");
      // read more rows to make sure that the type is correct for column
      rowLimit = rowLimit > 0 ? rowLimit : 50000;
      XTableNode data = read(input, encoding, null, output, rowLimit, colLimit,
                             true, null, false, parseInfo);

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
            Set<String> existing = new HashSet<>();

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

               if(isValid(Tool.getDataType(data.getType(i)), formats[i])) {
                  col.setAttribute("format", formats[i]);
               }

               meta.addChild(col);
            }
         }
      }

      data.close();
      return meta;
   }

   /**
    * Check whether the format is match to the type.
    *
    * @param type   the data type.
    * @param format the format pattern.
    *
    * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
    */
   private boolean isValid(String type, String format) {
      return !CoreTool.BOOLEAN.equals(type) ||
         format == null || format.length() == 0;
   }

   @Override
   public boolean isXLSX() {
      return true;
   }

   /**
    * whether the maximum row or maximum column limit is exceeded
    */
   @Override
   public boolean isExceedLimit() {
      return exceedLimit;
   }

   /**
    * Set the maximum row or maximum column limit to be exceeded
    */
   public void setExceedLimit(boolean exceedLimit) {
      this.exceedLimit = exceedLimit;
   }

   /**
    * Get whether there are columns with mixed type of data
    */
   @Override
   public boolean isMixedTypeColumns() {
      return mixedTypeColumns;
   }

   /**
    * Set whether there are columns with mixed type of data
    */
   public void setMixedTypeColumns(boolean mixedTypeColumns) {
      this.mixedTypeColumns = mixedTypeColumns;
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

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] getSheetNames(InputStream input) throws Exception {
      return read(input, new ZipCallable<String[]>() {
         @Override
         public String[] call() throws Exception {
            ZipFile zip = getZipFile();
            String wbentry = ContentTypesHandler.getWorkbookEntry(zip);
            String[][] sheets = WorkbookHandler.getSheets(zip, wbentry);

            String entry = wbentry;
            int index = entry.lastIndexOf('/');
            entry = entry.substring(0, index) + "/_rels" +
               entry.substring(index) + ".rels";

            List<String> sheetNames = new ArrayList<>();

            for(int i = 0; i < sheets.length; i++) {
               String sheet = sheets[i][0];
               String sheetId = sheets[i][1];
               String[] entries = RelationshipsHandler.getEntries(zip, entry, sheetId);

               // not a workbook sheet
               if(entries[0] == null) {
                  continue;
               }

               sheetNames.add(sheet);
            }

            return sheetNames.toArray(new String[0]);
         }
      });
   }

   private <T> T read(InputStream input, ZipCallable<T> callable)
      throws Exception
   {
      File file = FileSystemService.getInstance().getCacheTempFile("data", ".xlsx");

      try {
         OutputStream foutput = new FileOutputStream(file);
         byte[] buffer = new byte[1024];
         int len = 0;

         try {
            while((len = input.read(buffer)) >= 0) {
               foutput.write(buffer, 0, len);
            }
         }
         finally {
            foutput.close();
         }

         ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);

         try {
            callable.setZipFile(zip);
            return callable.call();
         }
         finally {
            try {
               zip.close();
            }
            catch(Throwable exc) {
               LOG.warn("Failed to close file", exc);
            }
         }
      }
      finally {
         IOUtils.closeQuietly(input);

         try {
            Files.delete(file.toPath());
         }
         catch(IOException e) {
            LOG.debug("Failed to delete file: {}", file, e);
         }
      }
   }

   // Per https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet#XMLReader
   private static void parseXML(ContentHandler handler, InputSource source)
      throws SAXException, IOException
   {
      XMLReader xmlReader = XMLReaderFactory.createXMLReader();
      xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
      xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      xmlReader.setContentHandler(handler);
      xmlReader.parse(source);
   }

   private boolean exceedLimit = false;
   private boolean mixedTypeColumns = false;
   private boolean textExceedLimit = false;
   private static final Logger LOG = LoggerFactory.getLogger(XLSXFileReader.class);

   private abstract static class ZipCallable<T> implements Callable<T> {
      protected final ZipFile getZipFile() {
         return zipFile;
      }

      protected final void setZipFile(ZipFile zipFile) {
         this.zipFile = zipFile;
      }

      private ZipFile zipFile = null;
   }

   private static final class ContentTypesHandler extends DefaultHandler {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(NS_URI.equals(uri) && "Override".equals(localName)) {
            if(CONTENT_TYPE.equals(attrs.getValue("ContentType"))) {
               workbookEntry = attrs.getValue("PartName");

               if(workbookEntry.charAt(0) == '/') {
                  workbookEntry = workbookEntry.substring(1);
               }
            }
         }
      }

      public static String getWorkbookEntry(ZipFile file) throws Exception {
         ZipEntry entry = file.getEntry("[Content_Types].xml");

         if(entry == null) {
            throw new RuntimeException("[Content_Types].xml missing in XLSX");
         }

         InputStream input = file.getInputStream(entry);
         InputSource source = new InputSource(input);

         ContentTypesHandler handler = new ContentTypesHandler();
         parseXML(handler, source);

         return handler.workbookEntry;
      }

      private String workbookEntry = "xl/workbook.xml"; // office 2007 default

      private static final String NS_URI =
         "http://schemas.openxmlformats.org/package/2006/content-types";
      private static final String CONTENT_TYPE =
         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet." +
            "main+xml";
   }

   private static final class WorkbookHandler extends DefaultHandler {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(MAIN_NS_URI.equals(uri) && "sheet".equals(localName)) {
            String name = attrs.getValue("name");
            String id = attrs.getValue(REL_NS_URI, "id");
            sheetMap.put(name, id);
         }
      }

      public static String[][] getSheets(ZipFile file, String entry)
         throws Exception
      {
         InputStream input = file.getInputStream(file.getEntry(entry));
         InputSource source = new InputSource(input);

         WorkbookHandler handler = new WorkbookHandler();
         parseXML(handler, source);

         String[][] sheets = new String[handler.sheetMap.size()][2];
         int i = 0;

         for(Map.Entry<String, String> e : handler.sheetMap.entrySet()) {
            sheets[i][0] = e.getKey();
            sheets[i++][1] = e.getValue();
         }

         return sheets;
      }

      private Map<String, String> sheetMap = new LinkedHashMap<>();

      private static final String MAIN_NS_URI =
         "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
      private static final String REL_NS_URI =
         "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
   }

   private static final class RelationshipsHandler extends DefaultHandler {
      public RelationshipsHandler(String sheetId) {
         this.sheetId = sheetId;
      }

      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(NS_URI.equals(uri) && "Relationship".equals(localName)) {
            String type = attrs.getValue("Type");

            if(SHEET_TYPE.equals(type) && sheetId.equals(attrs.getValue("Id"))) {
               sheetEntry = attrs.getValue("Target");
            }
            else if(STRINGS_TYPE.equals(type)) {
               stringsEntry = attrs.getValue("Target");
            }
            else if(STYLES_TYPE.equals(type)) {
               stylesEntry = attrs.getValue("Target");
            }
         }
      }

      public static String[] getEntries(ZipFile file, String entry, String id)
         throws Exception
      {
         InputStream input = file.getInputStream(file.getEntry(entry));
         InputSource source = new InputSource(input);

         RelationshipsHandler handler = new RelationshipsHandler(id);
         parseXML(handler, source);

         return new String[] {
            handler.sheetEntry, handler.stringsEntry, handler.stylesEntry
         };
      }

      private final String sheetId;
      private String sheetEntry = null;
      private String stylesEntry = null;
      private String stringsEntry = null;

      private static final String NS_URI =
         "http://schemas.openxmlformats.org/package/2006/relationships";
      private static final String SHEET_TYPE =
         "http://schemas.openxmlformats.org/officeDocument/2006/" +
            "relationships/worksheet";
      private static final String STRINGS_TYPE =
         "http://schemas.openxmlformats.org/officeDocument/2006/" +
            "relationships/sharedStrings";
      private static final String STYLES_TYPE =
         "http://schemas.openxmlformats.org/officeDocument/2006/" +
            "relationships/styles";
   }

   private static final class StringTableHandler extends DefaultHandler {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(NS_URI.equals(uri) && "sst".equals(localName)) {
            // fix bug1407514373804 A simple excel file created in Excel2013
            // can import into an embedded table assembly in a worksheet.
            String countStr;

            if(attrs.getValue("uniqueCount") == null) {
               countStr = attrs.getValue("count");
            }
            else {
               countStr = attrs.getValue("uniqueCount");
            }

            int count = countStr != null ? Integer.parseInt(countStr.trim()) : 0;
            strings = new ArrayList<>(count);
         }
         else if(NS_URI.equals(uri) && "si".equals(localName)) {
            currentString = new StringBuilder();
         }
         else if(NS_URI.equals(uri) && ("t".equals(localName) ||
            "rPr".equals(localName)))
         {
            currentString = index >= strings.size() || strings.get(index) == null ? new StringBuilder() :
               new StringBuilder(strings.get(index));
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName) {
         if(NS_URI.equals(uri) && "t".equals(localName)) {
            String decoded = decodeString();

            if(index >= strings.size()) {
               strings.add(TextUtil.cleanUpString(decoded));
            }
            else {
               strings.set(index, TextUtil.cleanUpString(decoded));
            }
         }
         else if(NS_URI.equals(uri) && "si".equals(localName) && index < strings.size()) {
            index++;
            currentString = null;
         }
      }

      @Override
      public void characters(char[] ch, int start, int length) {
         if(currentString != null) {
            currentString.append(ch, start, length);
         }
      }

      public static String[] getStrings(ZipFile file, String entry) throws Exception {
         if(file.getEntry(entry) == null) {
            return null;
         }

         InputStream input = file.getInputStream(file.getEntry(entry));
         InputSource source = new InputSource(input);

         StringTableHandler handler = new StringTableHandler();
         parseXML(handler, source);

         return handler.strings.toArray(new String[0]);
      }

      /**
       * OOXML uses a special encoding for characters that cannot be represented in XML. This method
       * decodes these strings back into characters.
       *
       * @return the decoded string.
       *
       * @see <a href="https://www.robweir.com/blog/2008/03/ooxmls-out-of-control-characters.html">OOXML Encoding</a>
       */
      private String decodeString() {
         StringBuilder builder = new StringBuilder();
         // find hex-encoded Unicode character sequences '_xNNNN_'
         Pattern pattern = Pattern.compile("_x([0-9A-F]{4})_");
         Matcher matcher = pattern.matcher(currentString);
         int start = 0;

         while(matcher.find()) {
            builder.append(currentString, start, matcher.start());
            char c = (char) Integer.parseInt(matcher.group(1), 16);
            builder.append(c);
            start = matcher.end();
         }

         builder.append(currentString, start, currentString.length());
         return builder.toString();
      }

      private int index = 0;
      private ArrayList<String> strings;
      private StringBuilder currentString = null;

      private static final String NS_URI =
         "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
   }

   private static final class StyleTableHandler extends DefaultHandler {
      public StyleTableHandler(ExcelLoader loader) {
         this.loader = loader;
      }

      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(NS_URI.equals(uri) && "cellXfs".equals(localName)) {
            inxfs = true;
         }
         else if(NS_URI.equals(uri) && "numFmts".equals(localName)) {
            inNumFmts = true;
         }
         else if(NS_URI.equals(uri) && "xf".equals(localName) && inxfs) {
            String numFmtId = attrs.getValue("numFmtId");

            if(numFmtId != null) {
               int fmtId = Integer.parseInt(attrs.getValue("numFmtId").trim());
               loader.getStyleTable().addFormatReference(index++, fmtId);
            }
         }
         else if(NS_URI.equals(uri) && "numFmt".equals(localName) && inNumFmts) {
            int fmtId = Integer.parseInt(attrs.getValue("numFmtId").trim());
            String fmt = attrs.getValue("formatCode");
            loader.getStyleTable().addFormat(fmtId, fmt);
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName) {
         if(NS_URI.equals(uri) && "cellXfs".equals(localName)) {
            inxfs = false;
         }
         else if(NS_URI.equals(uri) && "numFmts".equals(localName)) {
            inNumFmts = false;
         }
      }

      public static void getStyles(ZipFile file, String entry,
                                   ExcelLoader loader) throws Exception
      {
         ZipEntry zipEntry = file.getEntry(entry);

         if(zipEntry != null) {
            InputStream input = file.getInputStream(zipEntry);
            InputSource source = new InputSource(input);
            StyleTableHandler handler = new StyleTableHandler(loader);

            parseXML(handler, source);
         }
      }

      private final ExcelLoader loader;
      private boolean inNumFmts = false;
      private boolean inxfs = false;
      private int index = 0;

      private static final String NS_URI =
         "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
   }

   /* @by stephenwebster, fix bug1383930382967
    * This class will handle the parsing of an XLSX file and retrieve the
    * dimension of the data. i.e b1:b2
    * From observation Excel 2010, 2013 all contain the dimension tag when
    * saved.  It should be pretty rare that it doesn't exist, so assuming the
    * data is in a well formatted tabular format, this should be sufficient.
    *
    * e.g.
    *   A  B  C  ......Zn
    * 1 b1
    * 2
    * 3
    * .
    * .
    * n                b2
    */

   private static final class WorksheetDimensionHandler extends DefaultHandler {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes attrs) {
         if(this.dim == null) {
            // if dimension tag exists, use it.
            if("dimension".equals(localName)) {
               // don't terminate the search since the (poi export)
               // dimensions record defaults to A1 and may not be
               // accurate so relying on the data scan is better
               //this.dim = attrs.getValue("ref");
               //throw new DimensionFoundException();
            }
            // otherwise scan cells
            else if("c".equals(localName)) {
               String ref = attrs.getValue("r");

               if(ref != null) {
                  String[] colRow = getColRow(ref);

                  if(first == null) {
                     first = ref;
                     maxCol = colRow[0];
                     maxRow = Integer.parseInt(colRow[1]);
                  }

                  if(Integer.parseInt(colRow[1].trim()) > maxRow) {
                     maxRow = Integer.parseInt(colRow[1].trim());
                  }

                  if(getColIndex(colRow[0]) > getColIndex(maxCol)) {
                     maxCol = colRow[0];
                  }
               }
            }
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName)
         throws SAXException
      {
         if(this.dim == null) {
            // when sheetData is finally finished reading, set the value
            // for the dimension
            if("sheetData".equals(localName)) {
               dim = first + ":" + maxCol + maxRow;
               throw new DimensionFoundException();
            }
         }
      }

      // Returns the dimension of the XLSX file
      public String getDimension() {
         return dim;
      }

      // Return the cell as [col, row]
      private static String[] getColRow(String ref) {
         int idx = 0;

         for(idx = 1; idx < ref.length() && !Character.isDigit(ref.charAt(idx)); idx++) {
            // empty
         }

         return new String[] { ref.substring(0, idx), ref.substring(idx) };
      }

      // Get the numeric index for column name
      private static int getColIndex(String col) {
         int idx = 0;

         for(int i = 0; i < col.length(); i++) {
            idx = idx * 26 + (int) (col.charAt(i) - 'A') + 1;
         }

         return idx;
      }

      public String dim = null;
      private String first = null;
      private String maxCol = "A";
      private int maxRow = 0;
   }

   private static final class WorksheetHandler extends DefaultHandler {
      public WorksheetHandler(TextOutput output, String[] stringTable,
                              int maxColumns, ExcelLoader loader,
                              String dimension)
      {
         this.output = output;
         this.stringTable = stringTable;
         this.maxColumns = maxColumns;
         this.loader = loader;
         this.dimension = dimension;
      }

      /**
       * whether the maximum row or maximum column limit is exceeded
       */
      public boolean isExceedLimit() {
         return exceedLimit;
      }

      /**
       * Set the maximum row or maximum column limit to be exceeded
       */
      public void setExceedLimit(boolean exceedLimit) {
         this.exceedLimit = exceedLimit;
      }

      @Override
      public void startElement(String uri, String localName, String qName,
                               Attributes attrs) throws SAXException
      {
         if("row".equals(localName)) {
            String row = attrs.getValue("r");

            if(row != null) {
               int nextRow = Integer.parseInt(row.trim()) - 1;

               // hold off initLoader until the row is is populated, so the columnCnt
               // is know in case column info is missing (49578)
               if(loader.isPending(nextRow)) {
                  initLoader();
               }

               if(!loader.addRow(nextRow)) {
                  throw new LimitException();
               }
            }
            // The scan may find a case like xdr:row, but this is not the row tag we are
            // looking for so just skip it since the 'r' attribute is not defined.
            else if(!"xdr:row".equals(qName)) {
               int nextRow = currentRow++;
               currentColumn = -1;

               // hold off initLoader until the row is is populated, so the columnCnt
               // is know in case column info is missing (49578)
               if(loader.isPending(nextRow)) {
                  initLoader();
               }

               if(!loader.addRow(nextRow)) {
                  throw new LimitException();
               }
            }
            else {
               currentRow++;
               currentColumn = -1;

               // see above
               if(loader.isPending(currentRow)) {
                  initLoader();
               }

               if(!loader.addRow(currentRow)) {
                  throw new LimitException();
               }
            }
         }
         else if("c".equals(localName)) {
            String row = attrs.getValue("r");

            if(row != null) {
               currentColumn = getCoordinates(attrs.getValue("r"))[1];
            }
            // if 'r' record is missing, the 'c' should be sequential so we increment the index.
            else {
               currentColumn++;
               columnCnt = currentColumn + 1;
            }

            isString = "s".equals(attrs.getValue("t"));
            inlineStr = "inlineStr".equals(attrs.getValue("t"));
            isFormula = "str".equals(attrs.getValue("t"));
            isBoolean = "b".equals(attrs.getValue("t"));
            isError = "e".equals(attrs.getValue("t"));
            String styleIndex = attrs.getValue("s");

            if(styleIndex != null) {
               int xfId = Integer.parseInt(styleIndex.trim());
               String formatCode = loader.getStyleTable().getFormatCode(xfId);
               currentFormatIndex = formatCode == null ? -1 : xfId;
            }
            else {
               currentFormatIndex = 0;
            }
         }
         else if("v".equals(localName) || "is".equals(localName)) {
            inValue = true;
            currentValue = new StringBuilder();
         }
      }

      private void initLoader() {
         if(dimension != null) {
            int columnCount = this.columnCnt;

            // if dimension is not know, just use the true columnCnt (49578).
            if(!"null:A0".equals(dimension)) {
               int idx = dimension.indexOf(':');
               int[] br = getCoordinates(dimension.substring(idx + 1));
               columnCount = br[1] + 1;
            }

            ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

            if(info.getEndColumn() >= 0) {
               columnCount = Math.min(columnCount, info.getEndColumn() + 1);
            }

            if(info.getStartColumn() >= 0) {
               columnCount -= info.getStartColumn();
               columnCount = Math.max(columnCount, 0);
            }

            if(maxColumns >= 0) {
               if(columnCount > maxColumns) {
                  exceedLimit = true;
               }

               columnCount = Math.min(columnCount, maxColumns);
            }

            loader.init(columnCount);
            dimension = null; //initialize once only
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName) {
         if("v".equals(localName) || "is".equals(localName)) {
            int colno = getColumn(currentColumn);

            if(isError) {
               loader.setString(currentValue.toString(), colno);
            }
            else if(isFormula) {
               loader.setString(currentValue.toString(), colno);
            }
            else if(inlineStr) {
               loader.setString(currentValue.toString(), colno);
            }
            else if(isString) {
               int index = Integer.parseInt(currentValue.toString());
               final String str = stringTable[index];

               if(str == null || str.isEmpty()) {
                  loader.setNull(colno);
               }
               else {
                  loader.setString(str, colno);
               }
            }
            else {
               try {
                  String valueString = currentValue.toString();
                  double value = Double.parseDouble(currentValue.toString());
                  boolean success = false;

                  // long value may be showed as scientific notation,
                  // set the long value as string, using double may cause precision loss
                  if(Math.abs(value) > Integer.MAX_VALUE &&
                     (valueString.contains("e") || valueString.contains("E")))
                  {
                     BigDecimal bigDecimal = new BigDecimal(valueString);
                     String plainString = bigDecimal.toPlainString();

                     if(!plainString.contains(".")) {
                        loader.setString(plainString, colno);
                        success = true;
                     }
                  }

                  if(!success) {
                     if(isBoolean) {
                        loader.setBooleanFormulaValue(
                           "1".equals(currentValue.toString()), colno);
                     }
                     else {
                        loader.setValue(value, colno, currentFormatIndex);
                     }
                  }
               }
               catch(Exception ex) {
                  loader.setString(currentValue.toString(), colno);
               }
            }

            inValue = false;
            currentValue = null;
         }
      }

      @Override
      public void characters(char[] ch, int start, int length) {
         if(inValue) {
            currentValue.append(ch, start, length);
         }
      }

      private int[] getCoordinates(String address) {
         Pattern pattern = Pattern.compile("^([A-Z]+)([0-9]+)$");
         Matcher matcher = pattern.matcher(address);

         if(!matcher.find()) {
            throw new IllegalArgumentException(
               "Invalid cell address: " + address);
         }

         int row = Integer.parseInt(matcher.group(2)) - 1;
         String columnAddress = matcher.group(1);
         int column = 0;

         for(int i = 0; i < columnAddress.length(); i++) {
            int n = (((int) columnAddress.charAt(i)) & 0xffff) - (int) 'A';

            if(i < columnAddress.length() - 1) {
               n += 1;
            }

            double pow = columnAddress.length() - i - 1;
            int offset = (int) Math.round(Math.pow(26, pow));
            n *= offset;
            column += n;
         }

         return new int[] { row, column };
      }

      private int getColumn(int column) {
         int col = column;
         ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

         if(info.getEndColumn() >= 0 && col > info.getEndColumn()) {
            col = -1;
         }
         else if(info.getStartColumn() >= 0) {
            col -= info.getStartColumn();
         }

         return col;
      }

      public static ExcelLoader read(ZipFile file, String entry,
                                     String[] stringTable,
                                     ExcelLoader loader,
                                     TextOutput output, int maxRows,
                                     int maxColumns) throws Exception
      {
         int nrows = maxRows == 0 ? -1 : maxRows;
         InputStream input = file.getInputStream(file.getEntry(entry));
         InputSource source = new InputSource(input);

         // @by stephenwebster, fix bug1383930382967
         // The dimension bounds of the worksheet may not be readily available
         // in the markup.  We must do a scan of the file to obtain these bounds.
         WorksheetDimensionHandler dHandler = new WorksheetDimensionHandler();

         try {
            parseXML(dHandler, source);
         }
         catch(DimensionFoundException exc) {
            // ok, short circuit when dimension found
         }

         input = file.getInputStream(file.getEntry(entry));
         source = new InputSource(input);
         loader.setMaxRows(nrows);

         final WorksheetHandler wshandler = new WorksheetHandler(
            output, stringTable, maxColumns, loader, dHandler.getDimension());
         loader.setExceedLimit(wshandler.isExceedLimit());

         try {
            parseXML(wshandler, source);
         }
         catch(LimitException exc) {
            // max rows hit, ok
         }

         // if sheet is empty, we don't want to throw an exception. should
         // just show the column headers. (50001)
         wshandler.initLoader();

         loader.finish();
         return loader;
      }

      private ExcelLoader loader = null;
      private final TextOutput output;
      private int maxColumns = -1;
      private int columnCnt = 0;

      private final String[] stringTable;
      private int currentColumn = -1;
      private int currentRow = 0;
      private int currentFormatIndex = 0;
      private boolean isString = false;
      private boolean inlineStr = false;
      private boolean inValue = false;
      private boolean isBoolean = false;
      private boolean isFormula = false;
      private boolean isError = false;
      private StringBuilder currentValue = null;
      private String dimension = null;
      private boolean exceedLimit = false;
   }

   private static final class LimitException extends SAXException {
   }

   private static final class DimensionFoundException extends SAXException {
   }

   // check if this is a xlsb file.
   public static boolean isXLSB(File file) {
      try(ZipFile zip = new ZipFile(file)) {
         return zip.stream()
            .map(entry -> ((ZipEntry) entry).getName())
            .anyMatch(name -> name.endsWith("workbook.bin"));
      }
      catch(Exception e) {
         return false;
      }
   }
}