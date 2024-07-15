/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util.filereader;

import inetsoft.report.internal.Util;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.uql.text.TextOutput;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.*;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Class that handles the loading of data from an Excel spreadsheet. This class
 * separates the parsing logic from the format-specific reading details.
 *
 * @author InetSoft Technology
 * @since  11.2
 */
public abstract class ExcelLoader {
   /**
    * Creates a new instance of <tt>ExcelLoader</tt>.
    *
    * @param output the output descriptor.
    */
   public ExcelLoader(TextOutput output, boolean firstRowHeader) {
      this.output = output;
      this.localCache = new HashMap<>();
      this.formatCache = new ExcelFormatCache();
      this.styleTable = new ExcelStyleTable();
      this.firstRowHeader = firstRowHeader;
   }

   /**
    * Initializes the dimensions of the table.
    *
    * @param columnCount the number of columns.
    */
   protected void init(int columnCount) {
      this.columnCount = columnCount;
      this.table = new XSwappableTable();
      this.creators = new XTableColumnCreator[columnCount];

      for(int i = 0; i < creators.length; i++) {
         creators[i] = XObjectColumn.getCreator();
         creators[i].setDynamic(false);
      }

      table.init(creators);
   }

   public void finish() {
      for(int i = 0; i < columnCount; i++) {
         if(classes.get(i) == null) {
            setType(i, String.class, "string");
         }

         if(parseInfo != null && !ignoreColumnType(i) && XSchema.isNumericType(types.get(i)) &&
            columnsHasNotApplicableValues.containsKey(i) && columnsHasNotApplicableValues.get(i))
         {
            parseInfo.getProspectTypeMap().put(i, types.get(i));
         }
      }

      if(data != null && !skipRow(currentRow) && rowCount != maxRows) {
         ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

         // first row is header always
         if(output.getHeaderInfo() == null && info != null && info.getStartRow() == currentRow) {
            table.addRow(expandArray(data, table.getColCount(), table.getRowCount() == -1));
         }

         table.addRow(expandArray(data, table.getColCount(), table.getRowCount() == -1));
      }

      table.complete();
   }

   public boolean isHeaderRow(int row) {
      int minhrow = -1;
      int maxhrow = -1;
      boolean contains = output.getHeaderInfo() != null;

      if(contains) {
         ExcelFileInfo info = (ExcelFileInfo) output.getHeaderInfo();
         minhrow = info.getStartRow();
         maxhrow = info.getEndRow();
      }

      return contains && (minhrow >= 0 && row >= minhrow &&
         (maxhrow < 0 || row <= maxhrow));
   }

   public boolean skipRow(int row) {
      int minhrow = -1;
      int maxhrow = -1;
      int minbrow = -1;
      int maxbrow = -1;

      if(output.getHeaderInfo() != null) {
         ExcelFileInfo info = (ExcelFileInfo) output.getHeaderInfo();
         minhrow = info.getStartRow();
         maxhrow = info.getEndRow();
      }

      if(output.getBodyInfo() != null) {
         ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();
         minbrow = info.getStartRow();
         maxbrow = info.getEndRow();
      }

      return !(minhrow < 0 && minbrow < 0) &&
         !(minhrow >= 0 && row >= minhrow && (maxhrow < 0 || row <= maxhrow)) &&
         !(minbrow >= 0 && row >= minbrow && (maxbrow < 0 || row <= maxbrow));
   }

   /**
    * Check if the nextRow will be added in next addRow call.
    */
   public boolean isPending(int nextRow) {
      return rowCount != maxRows && nextRow > currentRow;
   }

   /**
    * Adds rows to the data handler as required.
    *
    * @param nextRow the index of the row of the next cell being processed.
    *
    * @return <tt>true</tt> to continue processing; <tt>false</tt> if the row
    * limit has been reached.
    */
   public boolean addRow(int nextRow) {
      if(maxRows == rowCount) {
         return false;
      }

      if(nextRow > currentRow) {
         data = data == null ? new Object[columnCount] : data;

         if(!skipRow(currentRow)) {
            if(firstRowHeader && table.getRowCount() == -1) {
               data = getHeader(data);
            }

            // first row is header always
            if(output.getHeaderInfo() == null) {
               ExcelFileInfo info = (ExcelFileInfo) output.getBodyInfo();

               if(info != null && info.getStartRow() == currentRow) {
                  table.addRow(expandArray(data, table.getColCount(), table.getRowCount() == -1));
               }
            }

            table.addRow(expandArray(data, table.getColCount(), table.getRowCount() == -1));
            ++rowCount;

            if(maxRows == rowCount) {
               return false;
            }
         }

         data = new Object[columnCount];

         for(int i = currentRow + 1; i < nextRow; i++) {
            // add null values for empty rows
            if(!skipRow(i)) {
               table.addRow(expandArray(data, table.getColCount(), table.getRowCount() == -1));
               ++rowCount;

               if(maxRows == rowCount) {
                  return false;
               }
            }

            data = new Object[columnCount];
         }

         currentRow = nextRow;
      }
      else if(data == null) {
         data = new Object[columnCount];
      }

      return true;
   }

   private static Object[] getHeader(Object[] arr) {
      Set<Object> existing = new HashSet<>();
      Object[] headers = new Object[arr.length];

      for(int i = 0; i < headers.length; i++) {
         String header = (arr[i] != null) ? arr[i].toString().trim() : "col" + i;
         headers[i] = header;

         for(int k = 1; existing.contains(headers[i]); k++) {
            headers[i] = header + " " + k;
         }

         existing.add(headers[i]);
      }

      return headers;
   }

   /**
    * Gets the parsed table node.
    *
    * @return the table node.
    */
   public XTableNode getTableNode() {
      final boolean unpivot = Boolean.parseBoolean(output.getAttribute("unpivot") + "");

      XTableNode node = new XTableTableNode(table) {
         @Override
         public String getName(int col) {
            if(table.getObject(0, col) != null &&
               (output.getHeaderInfo() != null || unpivot))
            {
               return String.valueOf(table.getObject(0, col));
            }

            return TextUtil.createColumnName(col);
         }

         @Override
         public Class<?> getType(int col) {
            return classes.get(col);
         }

         @Override
         public XTableColumnCreator getColumnCreator(int col) {
            return creators[col];
         }

         @Override
         public XTableColumnCreator[] getColumnCreators() {
            return creators;
         }
      };

      String[] formats = IntStream.range(0, columnCount)
         .mapToObj(i -> this.formats.get(i)).toArray(String[]::new);
      node.setAttribute("formats", formats);

      if(rowCount == maxRows) {
         node.setAppliedMaxRows(maxRows);
      }

      return node;
   }

   private CachedFormat getFormat(int exfmtIndex) {
      CachedFormat format = localCache.get(exfmtIndex);

      if(format == null) {
         String pattern = styleTable.getFormatCode(exfmtIndex);

         if(pattern != null && pattern.indexOf('"') >= 0) {
            // @by cehnw, There is a poi bug in v3.5 that it can not recognize
            // chinese data format, so remove string between "" first. We should
            // remove this logic if update to a new version.
            pattern = pattern.replaceAll("\"[^\"]*\"", "");
         }

         format = new CachedFormat(styleTable.getFormatId(exfmtIndex), pattern);
         localCache.put(exfmtIndex, format);
      }

      return format;
   }

   /**
    * Set the boolean value in the current row for a formula record.
    *
    * @param value  the cell value.
    * @param column the column index.
    */
   public void setBooleanFormulaValue(boolean value, int column) {
      if(skipRow(column)) {
         return;
      }

      if(column >= 0) {
         setType(column, boolean.class, "boolean");
         setDataValue(column, value);
      }
   }

   /**
    * Sets the string value in the current row for a formula record.
    *
    * @param value  the cell value.
    * @param column the column index of the formula record.
    */
   public void setStringFormulaValue(String value, int column) {
      if(skipRow(currentRow)) {
         return;
      }

      if(column >= 0) {
         setType(column, String.class, "string");
         setDataValue(column, value);
      }
   }

   protected abstract Date getJavaDate(double value);

   protected abstract boolean isADateFormat(int index, String pattern);

   private String getDefaultPattern(String type) {
      String defPattern = null;

      if(Tool.TIME.equals(type)) {
         defPattern = Tool.DEFAULT_TIME_PATTERN;
      }
      else if(Tool.DATE.equals(type)) {
         defPattern = Tool.DEFAULT_DATE_PATTERN;
      }
      else if(Tool.TIME_INSTANT.equals(type)) {
         defPattern = Tool.DEFAULT_DATETIME_PATTERN;
      }

      return defPattern;
   }

   /**
    * Sets the value in the current row for a cell record.
    *
    * @param value       the cell value.
    * @param column      the column index.
    * @param formatIndex the index of the format.
    */
   public void setValue(double value, int column, int formatIndex) {
      if(skipRow(currentRow) || data == null) {
         return;
      }

      if(column >= 0) {
         Object ovalue = null;
         CachedFormat fmt = getFormat(formatIndex);

         if(fmt.pattern != null) {
            Format format = formatCache.getFormat(value, fmt.index, fmt.pattern);

            if(fmt.date) {
               ovalue = getJavaDate(value);
               String dateType = TextUtil.getDateType((SimpleDateFormat) format);

               if(!isHeaderRow(currentRow)) {
                  if(classes.get(column) == null) {
                     String type = types.get(column) == null && dateType != null ?
                        dateType : types.get(column);
                     setType(column, Tool.getDataClass(type), dateType);
                  }
                  else if(!Date.class.isAssignableFrom(classes.get(column)) &&
                     !String.class.isAssignableFrom(classes.get(column)))
                  {
                     // data quality problem
                     LOG.debug("DATA QUALITY: date value in non-date column " +
                        ExcelFileInfo.getColumnLabel(column) +
                        " at row " + ExcelFileInfo.getRowLabel(currentRow));
                     return;
                  }
               }

               // @by jasonshobe, bug1311105790326, workaround for bad
               // formats in the excel file. using a bad format to convert
               // the value can drastically change the value. use the default
               // format for conversion instead
               String type = isHeaderRow(currentRow) || "string".equals(types.get(column)) ?
                  dateType : types.get(column);
               String defPattern = getDefaultPattern(type);

               try {
                  if(ovalue != null) {
                     String ostr = new SimpleDateFormat(defPattern).format(ovalue);
                     ovalue = Tool.getData(
                        "string".equals(types.get(column)) ? dateType : types.get(column), ostr);
                  }
               }
               catch(Exception ex) {
                  LOG.warn("Failed to parse date: " + ovalue + " as " + defPattern);
               }
            }

            if(!isHeaderRow(currentRow)) {
               if(format instanceof DecimalFormat) {
                  formats.put(column, ((DecimalFormat) format).toPattern());
               }
               else if(format instanceof SimpleDateFormat) {
                  formats.put(column, ((SimpleDateFormat) format).toPattern());
               }
            }
         }

         if(ovalue == null) {
            if(formats.get(column) == null ||
               new DecimalFormat(formats.get(column)).getMaximumFractionDigits() != 0)
            {
               ovalue = value;
            }
            else {
               // for long type and special format "# ?/8"
               if(((int) value) - value != 0) {
                  ovalue = value;
               }
               else {
                  ovalue = (int) value;
               }
            }

            if(!isHeaderRow(currentRow)) {
               if(classes.get(column) == null && !ignoreColumnType(column)) {
                  if(ovalue instanceof Integer) {
                     setType(column, Integer.class, "integer");
                  }
                  else {
                     setType(column, Double.class, "double");
                  }
               }
               else if(classes.get(column) == Integer.class && !(ovalue instanceof Integer)) {
                  setType(column, Double.class, "double");
               }
               else if(classes.containsKey(column) &&
                  !Number.class.isAssignableFrom(classes.get(column)) &&
                  !String.class.isAssignableFrom(classes.get(column)) &&
                  !boolean.class.isAssignableFrom(classes.get(column)))
               {
                  // data quality problem
                  LOG.debug("DATA QUALITY: number value in non-number column " +
                     ExcelFileInfo.getColumnLabel(column & 0xffff) +
                     " at row " + ExcelFileInfo.getRowLabel(currentRow));
                  return;
               }
            }
         }

         data = expandArray(data, column + 1, false);
         setDataValue(column, ovalue);
      }
   }

   private boolean ignoreColumnType(int column) {
      return parseInfo != null && parseInfo.getIgnoreTypeColumns().contains(column);
   }

   public void setString(String value, int column) {
      if(column >= 0) {
         String text = TextUtil.cleanUpString(value);

         if(!isHeaderRow(currentRow) && text.length() > Util.getOrganizationMaxCellSize()) {
            text = text.substring(0, Util.getOrganizationMaxCellSize());
            textExceedLimit = true;
         }

         setDataValue(column, text);
         boolean isNotApplicableValue = Util.isNotApplicableValue(value);

         if(isHeaderRow(currentRow) || classes != null && (classes.get(column) == null ||
            String.class.isAssignableFrom(classes.get(column))))
         {
            if(isHeaderRow(currentRow) && data[column] != null) {
               // don't allow new lines in column names
               setDataValue(column, ((String) data[column]).replace('\n', ' ') .replace('\r', ' '));
            }

            if(!isHeaderRow(currentRow)) {
               if(classes.get(column) == null && !ignoreColumnType(column)) {
                  boolean isDate = Tool.isDate(value);
                  String type = XSchema.STRING;

                  if(isDate) {
                     boolean defaultDmyOrder = Tool.getDateTimeConfig().isDmyOrder();
                     Date date = null;

                     try {
                        date = Tool.parseDateTime(value);
                     }
                     catch(ParseException ex) {
                        try {
                           date = DateTime.parse(value,
                              Tool.getDateTimeConfig(!defaultDmyOrder)).toDate();
                        }
                        catch(Exception ex0) {
                           isDate = false;

                           if(parseInfo != null) {
                              parseInfo.setDmyOrder(column, !defaultDmyOrder);
                           }
                        }
                     }

                     type = CoreTool.hasTimePart(date) ? XSchema.TIME_INSTANT : XSchema.DATE;
                  }

                  // detect date/time in the same way as csv
                  if(isDate) {
                     // use sql.Date for date type to make sure not be convert to timestamp
                     // when using Tool.getDataType
                     Class cls = XSchema.TIME_INSTANT.equals(type) ?
                        Date.class : java.sql.Date.class;
                     setType(column, cls, type);
                  }
                  else if(!isNotApplicableValue) {
                     setType(column, String.class, XSchema.STRING);
                  }
               }
            }
         }

         if(!isHeaderRow(currentRow) && isNotApplicableValue) {
            columnsHasNotApplicableValues.put(column, true);
         }

         if(!isHeaderRow(currentRow) && classes != null && classes.get(column) == null) {
            return;
         }

         if(classes != null && classes.get(column) != null && !ignoreColumnType(column)) {
            if(Date.class.isAssignableFrom(classes.get(column))) {
               try {
                  if(value == null || value.isEmpty()) {
                     setDataValue(column, null);
                  }
                  else {
                     Object val = parseDateTime(value, column);
                     String type = types.get(column);
                     String defPattern = getDefaultPattern(type);

                     if(!XSchema.TIME_INSTANT.equals(type) && val != null) {
                        String ostr = new SimpleDateFormat(defPattern).format(val);
                        val = Tool.getData(type, ostr);
                     }

                     setDataValue(column, val);
                  }
               }
               catch(ParseException e) {
                  String type = types.get(column);

                  if(parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(column)) {
                     parseInfo.getProspectTypeMap().put(column, type);
                     setType(column, String.class, XSchema.STRING);
                  }

                  LOG.info("Failed to parse data: " + value);
               }
            }
            else if(Number.class.isAssignableFrom(classes.get(column))) {
               Object obj = Tool.getData(classes.get(column), value);

               if(!StringUtils.isEmpty(value) && obj == null && !isNotApplicableValue) {
                  setType(column, String.class, XSchema.STRING);
               }
            }
         }
      }
   }

   public void setNull(int column) {
      if(column >= 0) {
         setDataValue(column, null);
      }
   }

   public void setType(int column, Class cls, String type) {
      if(column >= 0) {
         if(classes.get(column) != null && !classes.get(column).equals(cls)) {
            mixedTypeColumns = true;
         }

         if(mixedTypeColumns && (String.class.equals(classes.get(column))
         || (Double.class.equals(classes.get(column)) && Integer.class.equals(cls)))) {
            return;
         }
         else if(mixedTypeColumns) {
            cls = String.class;
            type = "string";
         }

         classes.put(column, cls);
         types.put(column, type);
      }
   }

   private static Object[] expandArray(Object[] arr, int n, boolean header) {
      if(arr == null) {
         arr = new Object[n];
      }
      else if(arr.length != n) {
         Object[] narr = new Object[n];
         System.arraycopy(arr, 0, narr, 0, Math.min(narr.length, arr.length));
         arr = narr;
      }

      if(header) {
         String[] headers = Arrays.stream(arr)
            .map(a -> a != null ? a.toString() : null).toArray(String[]::new);
         arr = CSVLoader.getHeaders(headers, true, Integer.MAX_VALUE, false);
      }

      return arr;
   }

   public ExcelStyleTable getStyleTable() {
      return styleTable;
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

   /**
    * Get whether there are columns with mixed type of data
    */
   public boolean isMixedTypeColumns() {
      return mixedTypeColumns;
   }

   public boolean isTextExceedLimit() {
      return textExceedLimit;
   }

   /**
    * Set whether there are columns with mixed type of data
    */
   public void setMixedTypeColumns(boolean mixedTypeColumns) {
      this.mixedTypeColumns = mixedTypeColumns;
   }

   public Map<Integer, String> getTypes() {
      return types;
   }

   public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
   }

   private void setDataValue(int column, Object value) {
      data = expandArray(data, column + 1, false);
      data[column] = value;
   }

   public void setDateParseInfo(DateParseInfo parseInfo) {
      this.parseInfo = parseInfo;
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public Date parseDateTime(String val, int column) throws ParseException {
      try {
         return Tool.parseDateTimeWithDefaultFormat(val);
      }
      catch(ParseException ex) {
         if(parseInfo == null) {
            throw ex;
         }
         try {
            Boolean isDmyOrder = parseInfo.isDmyOrder(column);
            boolean defaultDmyOrder = Tool.getDateTimeConfig().isDmyOrder();
            DateTime dt = null;

            if(isDmyOrder == null || Tool.equals(isDmyOrder, defaultDmyOrder)) {
               try {
                  dt = DateTime.parse(val, Tool.getDateTimeConfig());
               }
               catch(Exception ex0) {
                  dt = DateTime.parse(val, Tool.getDateTimeConfig(!defaultDmyOrder));
                  parseInfo.setDmyOrder(column, !defaultDmyOrder);
               }
            }
            else {
               dt = DateTime.parse(val, Tool.getDateTimeConfig(isDmyOrder));
            }

            return dt.toDate();
         }
         catch(Exception ex2) {
            throw new ParseException(ex2.getMessage(), 0);
         }
      }
   }

   private final TextOutput output;

   private int maxRows = 0;
   private int rowCount = 0;
   private int columnCount = 0;
   private int currentRow = 0;
   private Object[] data = null;

   private XSwappableTable table = null;
   private XTableColumnCreator[] creators = null;

   private DateParseInfo parseInfo = new DateParseInfo();
   private final Map<Integer, Boolean> columnsHasNotApplicableValues = new HashMap<>();
   private final Map<Integer, Class<?>> classes = new HashMap<>();
   private final Map<Integer, String> types = new HashMap<>();
   private final Map<Integer, String> formats = new HashMap<>();
   private final Map<Integer, CachedFormat> localCache;
   private final ExcelFormatCache formatCache;
   private final ExcelStyleTable styleTable;
   private boolean exceedLimit = false;
   private boolean textExceedLimit = false;
   private final boolean firstRowHeader;
   private boolean mixedTypeColumns = false;

   private static final Logger LOG = LoggerFactory.getLogger(ExcelLoader.class);

   final class CachedFormat {
      public CachedFormat(int index, String pattern) {
         this.index = index;
         this.pattern = pattern;
         this.date = pattern != null && isADateFormat(index, pattern);
      }

      @Override
      public String toString() {
         return String.format(
            "CachedFormat[index=%d, pattern=%s, date=%s]",
            index, pattern, date);
      }

      private final int index;
      private final String pattern;
      private final boolean date;
   }
}
