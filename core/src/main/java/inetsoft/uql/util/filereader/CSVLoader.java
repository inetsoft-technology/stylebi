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
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.table.*;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.pojava.datetime.DateTime;
import org.pojava.datetime.IDateTimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Timestamp;
import java.text.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * This is used for parsing an uploaded file. It has more type detection than the
 * simple delimited reader.
 *
 * @author InetSoft Technology
 * @since  13.1
 */
public final class CSVLoader {
   /**
    * @param csvTemp csv data file.
    * @param encode encoding, e.g. UTF-8
    * @param removeQuote remove surrounding quotes.
    * @param delim delimiter, e.g. ","
    * @param firstRow true if first row is the header.
    * @param unpivot true to unpivot the table.
    * @param oldTypes default types.
    * @param types list of column types to be populated during loading.
    * @param detectType true to detect column type.
    * @param dateFormatSpec default date format.
    * @param typeRows number of rows to read to checking types.
    * @param rowLimit max rows.
    */
   public static XSwappableTable readCSV(File csvTemp, String encode, boolean removeQuote,
                                         String delim, boolean firstRow, boolean unpivot,
                                         Map<Object, String> oldTypes, List<String> types,
                                         boolean detectType, String dateFormatSpec,
                                         int typeRows, int rowLimit, int colLimit,
                                         DateParseInfo parseInfo)
         throws Exception
   {
      Map<String, Format> fmtMap = new Object2ObjectOpenHashMap<>();
      XSwappableTable dataTable = new XSwappableTable();
      InputStream input = new FileInputStream(csvTemp);
      int rowCount = 0;

      if("UTF-8".equals(encode)) {
         input = consumeBOM(input);
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(input, encode));
      String line;
      Object[] header = {};
      int ncol = 0;
      IDateTimeConfig config = CoreTool.getDateTimeConfig();
      boolean isDmyOrder = config.isDmyOrder();
      Map<Integer, Boolean> hasRecognizedTypes = new HashMap<>();
      Map<Integer, Boolean> hasNotApplicableValue = new HashMap<>();
      int maxTextSize = Util.getOrganizationMaxCellSize();

      // scan a limited number of lines to find column type
      for(int r = 0; r < typeRows && (line = TextUtil.readCSVLine(reader)) != null; r++) {
         String[] lines = splitLine(line, delim);

         if(colLimit > 0 && lines.length > colLimit) {
            dataTable.setExceedLimit(true);
         }

         if(r == 0) {
            header = getHeaders(lines, firstRow || unpivot, colLimit, removeQuote);
            ncol = header.length;

            if(parseInfo != null) {
               parseInfo.setDmyOrderArr(new Boolean[ncol]);
            }

            AssetUtil.initDefaultTypes(header, oldTypes, types, false);

            for(int i = 0; i < ncol; i++) {
               hasNotApplicableValue.put(i, false);
               hasRecognizedTypes.put(i, false);
            }
         }

         if(!detectType) {
            for(int i = 0; i < ncol; i++) {
               types.set(i, XSchema.STRING);
            }

            break;
         }
         else if(parseInfo != null && !parseInfo.getIgnoreTypeColumns().isEmpty()) {
            parseInfo.getIgnoreTypeColumns().stream().forEach(c -> types.set(c, XSchema.STRING));
         }

         // ignore header
         if((firstRow || unpivot) && r == 0) {
            continue;
         }

         for(int i = 0; i < ncol && i < lines.length; i++) {
            String currentType = types.get(i);
            String currentString = trim(lines[i], removeQuote);

            if(!dataTable.isTextExceedLimit() && XSchema.STRING.equals(currentType) &&
               currentString.length() > maxTextSize)
            {
               dataTable.setTextExceedLimit(true);
            }

            Object data = parseData(currentString, types, i, dateFormatSpec, parseInfo);

            if(data == null) {
               continue;
            }

            String newType = null;

            if(XSchema.BOOLEAN.equals(types.get(i)) && !"".equals(data) &&
               (!(data instanceof String) || !"true".equalsIgnoreCase((String) data) &&
               !"false".equalsIgnoreCase((String) data)))
            {
               newType = XSchema.STRING;
            }
            else {
               newType = AssetUtil.getType(header[i], types.get(i), data, fmtMap, isDmyOrder);
            }

            // @by stephenwebster, For Bug #16268
            // If different types are detected from row to row, default
            // the column type to String to avoid loss of information.
            if(currentType != null && hasRecognizedTypes.get(i) && !currentType.equals(newType)) {
               if(XSchema.isNumericType(currentType) && XSchema.isNumericType(newType)) {
                  newType = XSchema.DOUBLE;
               }
               else {
                  newType = XSchema.STRING;
                  fmtMap.remove(header[i]);
               }
            }

            if(XSchema.isDateType(types.get(i)) && !XSchema.isDateType(newType) &&
               parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(i))
            {
               parseInfo.getProspectTypeMap().put(i, types.get(i));
            }

            boolean isNotApplicableValue = Util.isNotApplicableValue(data);

            if(isNotApplicableValue) {
               hasNotApplicableValue.put(i, true);
            }

            if(data != null && !isNotApplicableValue) {
               hasRecognizedTypes.put(i, true);
            }

            types.set(i, newType);
         }
      }

      reader.close();

      input = new FileInputStream(csvTemp);

      if("UTF-8".equals(encode)) {
         input = consumeBOM(new FileInputStream(csvTemp));
      }

      reader = new BufferedReader(new InputStreamReader(input, encode));
      boolean firstLine = true;

      for(int i = 0; i < ncol; i++) {
         if(XSchema.isNumericType(types.get(i)) && hasNotApplicableValue.get(i)) {
            if(parseInfo == null || !parseInfo.getProspectTypeMap().containsKey(i)) {
               if(parseInfo != null) {
                  parseInfo.getProspectTypeMap().put(i, types.get(i));
               }

               types.set(i, XSchema.STRING);
            }
         }
      }

      // feature1295898876750, call TextUtil.readCSVLine to get csv line
      // to avoid getting the wrong line which caused by newlines
      // characters in quotes.
      // read line, parse line
      while((line = TextUtil.readCSVLine(reader)) != null) {
         String[] lines = splitLine(line, delim);

         if(colLimit > 0 && lines.length > colLimit) {
            dataTable.setExceedLimit(true);
         }

         Object[] rdata;

         if(firstLine) {
            firstLine = false;
            header = getHeaders(lines, firstRow || unpivot, colLimit, removeQuote);

            XTableColumnCreator[] creators = new XTableColumnCreator[ncol];

            for(int i = 0; i < ncol; i++) {
               // in case the auto-detect result is not what the user wants, then they can set
               // back column type to string without missing N/A, NA data.
               creators[i] = XObjectColumn.getCreator(types.get(i));
               creators[i].setDynamic(false);
            }

            dataTable.init(creators);
            dataTable.addRow(header);

            // first row as header?
            if(firstRow || unpivot) {
               continue;
            }
         }

         rdata = new Object[ncol];

         for(int i = 0; i < ncol; i++) {
            if(lines.length > i) {
               rdata[i] = trim(lines[i], removeQuote);
            }
            else {
               rdata[i] = null;
            }
         }

         parseRow(rdata, types, header, fmtMap, parseInfo, maxTextSize);
         dataTable.addRow(rdata);
         rowCount++;

         if(rowLimit > 0 && rowCount == rowLimit) {
            dataTable.setExceedLimit(true);
            break;
         }
      }

      dataTable.complete();
      reader.close();

      return dataTable;
   }

   private static String trim(String str, boolean removeQuote) {
      str = str.trim();
      return removeQuote ? TextUtil.stripOffQuote(str) : str;
   }

   // consume the BOM marker
   private static InputStream consumeBOM(InputStream input) throws IOException {
      input = new PushbackInputStream(input, 3);
      byte[] bom = new byte[3];
      int len = input.read(bom, 0, 3);

      if(!(len == 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF)) {
         ((PushbackInputStream) input).unread(bom);
      }

      return input;
   }

   /**
    * Split the line.
    * @param line the specified String.
    * @return the name of the asset event.
    */
   private static String[] splitLine(String line, String delim) {
      return TextUtil.split(line, delim, false, false, true);
   }

   /**
    * Fill in unique column headers.
    */
   public static Object[] getHeaders(String[] line, boolean firstRow, int colLimit, boolean removeQuote) {
      int headerCount = colLimit == -1 ? line.length : Math.min(line.length, colLimit);
      String[] headers = new String[headerCount];
      Set<Object> existing = new HashSet<>();

      for(int i = 0; i < headers.length; i++) {
         String header = (firstRow && line[i] != null &&
            !"".equals(line[i].trim())) ? line[i].trim() : "col" + i;
         headers[i] = toValidHeader(header);

         if(removeQuote) {
            headers[i] = TextUtil.stripOffQuote(headers[i]);
         }

         for(int k = 1; existing.contains(headers[i]); k++) {
            headers[i] = header + " " + k;
         }

         existing.add(headers[i]);
      }

      return headers;
   }

   public static String toValidHeader(String str) {
      StringBuilder buf = new StringBuilder();
      str = str.replace("\r\n", "\n");

      for(int i = 0; i < str.length(); i++) {
         if(Character.isISOControl(str.charAt(i))) {
            buf.append('_');
         }
         else {
            buf.append(str.charAt(i));
         }
      }

      return buf.toString();
   }

   // try to parse data and set types to correct type if able to parse as date
   public static Object parseData(Object data, List<String> types, int col, String dateFormatSpec) {
      return parseData(data, types, col, dateFormatSpec, null);
   }

   // try to parse data and set types to correct type if able to parse as date
   public static Object parseData(Object data, List<String> types, int col, String dateFormatSpec,
                                  DateParseInfo parseInfo)
   {
      if("".equals(data)) {
         return null;
      }

      // @by stephenwebster, For Bug #9642
      // Reformat data based on existing types when loaded.
      // @TODO Same logic exists in XEmbeddedTable, so we can factor out this code
      // I would rather not touch XEmbeddedTable in the middle of a release.
      // @by stephenwebster For Bug #9997
      // The changes for Bug #9642 had farther reaching consequences so
      // I am backing out the part of the formatting code (related to formatting
      // to number) since Bug #9642 is only supposed to support a very narrow case.
      // Looking forward, I think we can come up with a better solution.

      String type = types.get(col);
      Object oval = data;
      boolean defaultDmyOrder = CoreTool.getDateTimeConfig().isDmyOrder();

      if(oval != null && (XSchema.isDateType(type) || oval != null && Tool.isDate(oval.toString()))
         && parseInfo != null && !parseInfo.getIgnoreTypeColumns().contains(col))
      {
         Format format = null;
         Object nval = null;

         if(dateFormatSpec != null) {
            int colon = dateFormatSpec.indexOf(':');
            String formatType = dateFormatSpec.substring(0, colon);
            String pattern = dateFormatSpec.substring(colon + 1);
            format = TableFormat.getFormat(formatType, pattern);
         }

         try {
            try {
               if(data instanceof java.util.Date) {
                  nval = data;
               }
               else if(!"".equals(data) && format != null) {
                  nval = format.parseObject(data.toString());
               }
            }
            catch(Exception ex) {
               // ignore, try DateTime
            }
            finally {
               if(nval == null && !"".equals(data)) {
                  if(type.equals(XSchema.TIME)) {
                     LocalTime time = LocalTime.parse(data.toString(),
                                                      DateTimeFormatter.ofPattern("HH:mm:ss"));
                     nval = new java.sql.Time(time.getHour(), time.getMinute(), time.getSecond());
                  }
                  else {
                     try {
                        DateTime dt = null;

                        Boolean isDmyOrder = isDmyOrder(parseInfo, col);

                        try {
                           if(isDmyOrder == null || isDmyOrder != null &&
                              Tool.equals(isDmyOrder, defaultDmyOrder))
                           {
                              dt = DateTime.parse(data.toString(), CoreTool.getDateTimeConfig());

                              if(parseInfo != null) {
                                 parseInfo.setDmyOrder(col, defaultDmyOrder);
                              }
                           }
                           else {
                              dt = DateTime.parse(data.toString(),
                                 CoreTool.getDateTimeConfig(isDmyOrder(parseInfo, col)));
                           }
                        }
                        catch(Exception ex) {
                           dt = DateTime.parse(data.toString(),
                              CoreTool.getDateTimeConfig(!defaultDmyOrder));

                           if(parseInfo != null) {
                              parseInfo.setDmyOrder(col, !defaultDmyOrder);
                           }
                        }

                        nval = isTimestamp(type, dt) ? dt.toTimestamp() : dt.toDate();
                     }
                     catch(Exception ex) {
                        // joda time has trouble parsing dd-MM-yyyy (47216).
                        SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy");
                        nval = fmt.parse(data.toString());
                     }
                  }
               }
            }

            if(nval instanceof Date) {
               if(XSchema.INTEGER.equals(type) && nval instanceof Timestamp) {
                  types.set(col, XSchema.TIME_INSTANT);
               }
               else if(XSchema.DATE.equals(type)) {
                  nval = new java.sql.Date(((Date) nval).getTime());
               }
               else if(XSchema.TIME_INSTANT.equals(type)) {
                  nval = new java.sql.Timestamp(((Date) nval).getTime());
               }
               else if(XSchema.TIME.equals(type)) {
                  nval = new java.sql.Time(((Date) nval).getTime());
               }
               else {
                  types.set(col, XSchema.DATE);
               }
            }

            data = nval;
         }
         catch(Exception ex) {
            LOG.debug("Failed to parse date: " + oval, ex);

            if(parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(col)) {
               parseInfo.getProspectTypeMap().put(col, type);
            }

            data = oval.toString();
            types.set(col, XSchema.STRING);
         }
      }

      return data;
   }

   /**
    * Transform string data to real type data.
    *
    * @param parseInfo info about date parse.
    * @param maxTextSize
    */
   public static void parseRow(Object[] rdata, List<String> types, Object[] header,
                               Map<String, Format> fmtMap, DateParseInfo parseInfo,
                               int maxTextSize)
   {
      for(int c = 0; c < rdata.length; c++) {
         Object val = rdata[c];
         Format fmt;
         String type = types.get(c);

         if(val == null || "".equals(val)) {
            rdata[c] = null;
            continue;
         }

         if(Util.isNotApplicableValue(val)) {
            if(!XSchema.STRING.equals(type)) {
               rdata[c] = null;
            }

            continue;
         }

         try {
            //noinspection SuspiciousMethodCalls
            fmt = fmtMap.get(header[c]);

            if(fmt != null) {
               val = rdata[c] = fmt.parseObject((String) val, new ParsePosition(0));
            }

            if(XSchema.STRING.equals(type)) {
               String str = val.toString();
               rdata[c] = str.length() > maxTextSize ? str.substring(0, maxTextSize) : str;
            }
            else if(XSchema.BOOLEAN.equals(type)) {
               if(!(val instanceof Boolean)) {
                  rdata[c] = Boolean.valueOf((String) val);
               }
            }
            else if(XSchema.FLOAT.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).floatValue();
               }
               else if(val != null) {
                  rdata[c] = (float) NumberParserWrapper.getDouble(val.toString());
               }
            }
            else if(XSchema.DOUBLE.equals(type) || XSchema.DECIMAL.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).doubleValue();
               }
               else if(val != null) {
                  rdata[c] = NumberParserWrapper.getDouble(val.toString());
               }
            }
            else if(XSchema.BYTE.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).byteValue();
               }
               else if(val != null) {
                  rdata[c] = (byte) NumberParserWrapper.getInteger(val.toString());
               }
            }
            else if(XSchema.SHORT.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).shortValue();
               }
               else if(val != null) {
                  rdata[c] = (short) NumberParserWrapper.getInteger(val.toString());
               }
            }
            else if(XSchema.INTEGER.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).intValue();
               }
               else if(val != null) {
                  rdata[c] = NumberParserWrapper.getInteger(val.toString());
               }
            }
            else if(XSchema.LONG.equals(type)) {
               if(val instanceof Number) {
                  rdata[c] = ((Number) val).longValue();
               }
               else if(val != null) {
                  rdata[c] = NumberParserWrapper.getLong(val.toString());
               }
            }
            else if(XSchema.DATE.equals(type) && !isIgnoreTypeColumns(parseInfo, c)) {
               if(!(val instanceof Date)) {
                  try {
                     DateTime dt = DateTime.parse(val.toString(),
                        CoreTool.getDateTimeConfig(isDmyOrder(parseInfo, c)));
                     rdata[c] = dt.toDate();
                  }
                  catch(IllegalArgumentException ex) {
                     rdata[c] = null;

                     if(parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(c)) {
                        parseInfo.getProspectTypeMap().put(c, type);
                     }
                  }
               }
            }
            else if(XSchema.TIME.equals(type) && !isIgnoreTypeColumns(parseInfo, c)) {
               if(!(val instanceof java.sql.Time)) {
                  try {
                     LocalTime time = LocalTime.parse(val.toString(),
                                                      DateTimeFormatter.ofPattern("HH:mm:ss"));
                     rdata[c] = new java.sql.Time(time.getHour(), time.getMinute(),
                                                  time.getSecond());
                  }
                  catch(IllegalArgumentException ex) {
                     rdata[c] = null;

                     if(parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(c)) {
                        parseInfo.getProspectTypeMap().put(c, type);
                     }
                  }
               }
            }
            else if(XSchema.TIME_INSTANT.equals(type) && !isIgnoreTypeColumns(parseInfo, c)) {
               if(!(val instanceof Timestamp)) {
                  try {
                     DateTime dt = DateTime.parse(val.toString(),
                        CoreTool.getDateTimeConfig(isDmyOrder(parseInfo, c)));
                     rdata[c] = dt.toTimestamp();
                  }
                  catch(IllegalArgumentException ex) {
                     rdata[c] = null;

                     if(parseInfo != null && !parseInfo.getProspectTypeMap().containsKey(c)) {
                        parseInfo.getProspectTypeMap().put(c, type);
                     }
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.debug("Error parsing data: " + rdata[c], ex);
            rdata[c] = null;
         }
      }
   }

   private static boolean isIgnoreTypeColumns(DateParseInfo parseInfo, int column) {
      return parseInfo != null && parseInfo.getIgnoreTypeColumns().contains(column);
   }

   private static Boolean isDmyOrder(DateParseInfo parseInfo, int column) {
      if(parseInfo == null) {
         return null;
      }

      return parseInfo.isDmyOrder(column);
   }

   private static boolean isTimestamp(String type, DateTime dt) {
      if(XSchema.TIME_INSTANT.equals(type)) {
         return true;
      }

      // not default
      if(!XSchema.INTEGER.equals(type)) {
         return false;
      }

      return CoreTool.hasTimePart(dt.toDate());
   }

   private static final Logger LOG = LoggerFactory.getLogger(CSVLoader.class);
}
