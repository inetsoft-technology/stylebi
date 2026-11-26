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
package inetsoft.uql.util;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import inetsoft.util.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;

/**
 * A class to map a Json array to a table.
 */
abstract public class BaseJsonTable extends XTableNode {
   /**
    * Load json into this table.
    *
    * @param json a json string or json (java) object.
    */
   public void load(Object json) {
      beginStreamedLoading();
      loadStreamed(json);
      finishStreamedLoading();
   }

   public void applyQueryColumnTypes(TabularQuery query) {
      for(String col : query.getTypedColumns()) {
         setColumnType(col, query.getColumnType(col));
         setColumnFormat(col, query.getColumnFormat(col));
         setColumnFormatExtent(col, query.getColumnFormatExtent(col));
      }
   }

   /**
    * Prepares this table to receive a stream of data to load.
    */
   public abstract void beginStreamedLoading();

   /**
    * Loads streamed data into this table.
    *
    * @param json the JSON string or object to load.
    */
   public abstract void loadStreamed(Object json);

   /**
    * Finishes loading streamed data.
    */
   public abstract void finishStreamedLoading();

   /**
    * Merge the column type seen so far with the type of a newly seen value. Two numeric types
    * widen to a numeric type (both integral: Long; otherwise Double) so a column holding e.g.
    * Int32 and Double values stays numeric; any other mix falls back to String.
    */
   static Class<?> widenTypes(Class<?> prev, Class<?> cls) {
      if(prev == null || prev == cls) {
         return cls;
      }

      if(Number.class.isAssignableFrom(prev) && Number.class.isAssignableFrom(cls)) {
         return isIntegral(prev) && isIntegral(cls) ? Long.class : Double.class;
      }

      if((prev == java.sql.Date.class && cls == java.sql.Timestamp.class) ||
         (prev == java.sql.Timestamp.class && cls == java.sql.Date.class))
      {
         return java.sql.Timestamp.class;
      }

      return String.class;
   }

   private static boolean isIntegral(Class<?> cls) {
      return cls == Byte.class || cls == Short.class || cls == Integer.class ||
         cls == Long.class;
   }

   /**
    * Convert a cell to the column's final numeric type so every cell of a widened column has
    * the same class.
    */
   static Object coerceToColumnType(Object val, Class<?> type) {
      if(type == java.sql.Timestamp.class && val instanceof java.sql.Date &&
         !(val instanceof java.sql.Timestamp))
      {
         return new java.sql.Timestamp(((java.sql.Date) val).getTime());
      }

      if(val instanceof Number) {
         if(type == Double.class && val.getClass() != Double.class) {
            return ((Number) val).doubleValue();
         }
         else if(type == Long.class && val.getClass() != Long.class) {
            // never lose value: a later value wider than the sampled type stays as is
            Long lval = toExactLong((Number) val);
            return lval != null ? lval : val;
         }
      }

      return val;
   }

   private static Long toExactLong(Number num) {
      if(num instanceof Byte || num instanceof Short || num instanceof Integer) {
         return num.longValue();
      }

      if(num instanceof java.math.BigInteger) {
         return ((java.math.BigInteger) num).bitLength() <= 63 ? (Long) num.longValue() : null;
      }

      if(num instanceof java.math.BigDecimal) {
         try {
            return ((java.math.BigDecimal) num).longValueExact();
         }
         catch(ArithmeticException ex) {
            return null;
         }
      }

      double d = num.doubleValue();
      return d == Math.rint(d) && d >= -0x1p63 && d < 0x1p63 ? (Long) (long) d : null;
   }

   /**
    * Gets the number of rows currently loaded into this table.
    *
    * @return the row count.
    */
   public abstract int size();

   // find string value type, either a string or date/time.
   // number is handled by json parser.
   protected String getJavaType(String header, String val) {
      String type = colTypes.get(header);

      if(type != null) {
         return type;
      }

      // if a column is detected as date, always treat it as date until a parsing failed.
      type = colTypes2.get(header);

      if(type != null) {
         return type;
      }

      if(val == null || val.isEmpty()) {
         return XSchema.STRING;
      }

      type = checkDateTime(val);

      if(type != XSchema.STRING) {
         colTypes2.put(header, type);
      }

      return type;
   }

   // find date/time type
   private String checkDateTime(String str) {
      if(Tool.isTime(str)) {
         try {
            parseTime(str);
            return XSchema.TIME;
         }
         catch(Exception ex2) {
            return XSchema.STRING;
         }
      }

      if(!Tool.isDate(str)) {
         return XSchema.STRING;
      }

      if(str.indexOf(':') > 0) {
         return XSchema.TIME_INSTANT;
      }
      else {
         return XSchema.DATE;
      }
   }

   private LocalTime parseTime(String input) {
      return LocalTime.parse(input, DateTimeFormatter.ofPattern("H:mm:ss"));
   }

   private LocalDate parseDate(String input) {
      if(input.contains("/")) {
         return LocalDate.parse(input, getSlashDateFormatter());
      }

      return LocalDate.parse(input, DateTimeFormatter.ISO_DATE);
   }

   private LocalDateTime parseDateTime(String input) {
      if(input.contains("/")) {
         return LocalDateTime.parse(input, getSlashDateTimeFormatter());
      }

      try {
         return LocalDateTime.parse(input, DateTimeFormatter.ISO_DATE_TIME);
      }
      catch(DateTimeParseException ex) {
         return LocalDateTime.parse(input, DateTimeFormatter.ISO_INSTANT);
      }
   }

   // the formatter for slash separated dates: the declared order of the source if there is one,
   // otherwise the order of the server locale
   private DateTimeFormatter getSlashDateFormatter() {
      if(dmyOrder != null) {
         return dmyOrder ? DMY_DATE : MDY_DATE;
      }

      return usLocale ? US_DATE : EUROPE_DATE;
   }

   private DateTimeFormatter getSlashDateTimeFormatter() {
      if(dmyOrder != null) {
         return dmyOrder ? DMY_DATE_TIME : MDY_DATE_TIME;
      }

      return usLocale ? US_DATE_TIME : EUROPE_DATE_TIME;
   }

   // parses a date/time text the strict formatters did not accept, in the order of the source
   // if it declared one
   private DateTime parseFlexible(String str) {
      return dmyOrder == null ? DateTime.parse(str) :
         DateTime.parse(str, CoreTool.getDateTimeConfig(dmyOrder));
   }

   // removes the UTC offset (Z, +02:00, +0200, +02) that follows the time of a date-time text
   private static String stripOffset(String str) {
      return OFFSET_PATTERN.matcher(str).replaceFirst("$1");
   }

   // parse string as date/time if type is date/time
   protected Object parseValue(String header, Object obj) {
      String type = colTypes.get(header);
      Object val = parseValue0(header, obj);

      if(type != null && val != null) {
         return CoreTool.getData(type, val);
      }

      return val;
   }

   private Object parseValue0(String header, Object obj) {
      obj = JsonTable.getJavaValue(obj);

      if(!(obj instanceof String)) {
         return obj;
      }

      String str = (String) obj;

      if(str.isEmpty()) {
         return null;
      }

      // true if the date/time type is guessed from the text and not set by the user; the original
      // text is then kept with the value since the column may still end up a string column
      boolean inferred = colTypes.get(header) == null;
      String type = getJavaType(header, str);
      String formatType = getColumnFormat(header);
      String formatExtent = getColumnFormatExtent(header);

      // a cached date/time type is not trusted for later values: text that is no longer a date
      // of that type makes the column a string column, and a date-time in a date column is read
      // as a date-time so its time is not dropped
      if(inferred && formatType == null && type != XSchema.STRING &&
         type != XSchema.BOOLEAN)
      {
         String actual = checkDateTime(str);

         if(XSchema.STRING.equals(actual)) {
            colTypes2.remove(header);
            return str;
         }

         type = actual;
      }

      try {
         if(formatType != null) {
            final FormatSpec fmtSpec = new FormatSpec(formatType, formatExtent);
            Format fmt = fmtCache.get(fmtSpec);

            if(fmt == null) {
               fmt = TableFormat.getFormat(formatType, formatExtent);
               fmtCache.put(fmtSpec, fmt);
            }

            return fmt.parseObject(str);
         }

         try {
            if(XSchema.DATE.equals(type)) {
               try {
                  if(!isoDateFailed.contains(header + "|" + type)) {
                     java.sql.Date date = java.sql.Date.valueOf(parseDate(str));
                     return inferred ? new RawDate(date.getTime(), str) : date;
                  }
               }
               catch(DateTimeParseException ex) {
                  // don't try iso parsing again if failed.
                  isoDateFailed.add(header + "|" + type);
               }

               // a date column keeps the calendar date as written, so the offset of a
               // date-time text is dropped rather than converted to the server zone
               long millis = parseFlexible(stripOffset(str)).toMillis();
               return inferred ? new RawDate(millis, str) : new java.sql.Date(millis);
            }
            else if(XSchema.TIME_INSTANT.equals(type)) {
               try {
                  if(!isoDateFailed.contains(header + "|" + type)) {
                     java.sql.Timestamp ts = java.sql.Timestamp.valueOf(parseDateTime(str));
                     return inferred ? new RawTimestamp(ts, str) : ts;
                  }
               }
               catch(DateTimeParseException ex) {
                  isoDateFailed.add(header + "|" + type);
               }

               // like a date column, keep the wall clock as written instead of converting the
               // offset to the server zone
               java.sql.Timestamp ts = parseFlexible(stripOffset(str)).toTimestamp();
               return inferred ? new RawTimestamp(ts, str) : ts;
            }
            else if(XSchema.TIME.equals(type)) {
               java.sql.Time time = java.sql.Time.valueOf(parseTime(str));
               return inferred ? new RawTime(time.getTime(), str) : time;
            }
            else if(XSchema.BOOLEAN.equals(type)) {
               return Boolean.parseBoolean(str);
            }
         }
         catch(Exception e) {
            colTypes2.remove(header);
            // Failed to parse
            return str;
         }
      }
      catch(Exception ex) {
         LOG.trace("Failed to parse data: " + str, ex);
      }

      return str;
   }

   /**
    * Gets the value of a cell as the table exposes it. A date/time inferred from text is a
    * date/time only if its column is not a string column; otherwise the cell shows the original
    * text, not the text of the converted date.
    *
    * @param val         the stored cell value.
    * @param columnClass the final class of the cell's column.
    */
   static Object getCellValue(Object val, Class<?> columnClass) {
      if(val instanceof RawTemporal) {
         RawTemporal raw = (RawTemporal) val;
         return columnClass == String.class ? raw.getRawText() : raw.toPlain();
      }

      return val;
   }

   /**
    * Gets the class of a stored cell value for column type inference.
    */
   static Class<?> getValueClass(Object val) {
      return val instanceof RawTemporal ? ((RawTemporal) val).getBaseClass() : val.getClass();
   }

   // get parsed column name
   protected Class<?> getJavaType(String header) {
      return CoreTool.getDataClass(colTypes.get(header));
   }

   /**
    * Set the column type to use for data conversion.
    *
    * @param header column full header, e.g. path in json.
    * @param type   data type in XSchema.
    */
   public void setColumnType(String header, String type) {
      colTypes.put(header, type);
   }

   /**
    * Get the column type to use for data conversion.
    */
   public String getColumnType(String header) {
      return colTypes.get(header);
   }

   /**
    * Set the format for the column used for type conversion.
    *
    * @param header column full header.
    * @param format format type passed to TableFormat.getFormat().
    */
   public void setColumnFormat(String header, String format) {
      colFormats.put(header, format);
   }

   /**
    * Get the format for the column used for type conversion.
    */
   public String getColumnFormat(String header) {
      return colFormats.get(header);
   }

   /**
    * Set the format extent for the column used for type conversion.
    *
    * @param header     column full header.
    * @param formatSpec format extent passed to TableFormat.getFormat().
    */
   public void setColumnFormatExtent(String header, String formatSpec) {
      colExtents.put(header, formatSpec);
   }

   /**
    * Get the format extent for the column used for type conversion.
    */
   public String getColumnFormatExtent(String header) {
      return colExtents.get(header);
   }

   /**
    * Set the order in which the source writes the day and month of slash separated dates, e.g.
    * 5/10/2026. If not set, the order of the server locale is used.
    *
    * @param dmyOrder true if the day is before the month, false if the month is before the day,
    *                 or null if the source does not declare it.
    */
   public void setDmyOrder(Boolean dmyOrder) {
      this.dmyOrder = dmyOrder;
   }

   /**
    * Get the order in which the source writes the day and month of slash separated dates.
    *
    * @return true if the day is before the month, false if the month is before the day, or null
    * if the source does not declare it.
    */
   public Boolean getDmyOrder() {
      return dmyOrder;
   }

   public int getMaxRows() {
      return maxRows;
   }

   public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
   }

   /**
    * Whether data offered to this table was left out because it had reached
    * {@link #getMaxRows()}. A table that holds exactly {@code maxRows} rows and was offered no
    * more is not truncated.
    */
   public boolean isTruncated() {
      return truncated;
   }

   /**
    * Record that data was left out because of the row limit.
    */
   protected void setTruncated() {
      this.truncated = true;
   }

   /**
    * Declares the nested objects the query selects, so that an object that is null on every
    * row still produces its leaf columns instead of a single column named after the object.
    * Empty by default, which leaves the columns purely data-driven.
    *
    * @param declared object path (as this table names columns, relative to the row root) to the
    *                 recursive leaf paths under it, e.g. {@code folder -> [folder.id]}.
    */
   public void setDeclaredObjectColumns(Map<String, List<String>> declared) {
      this.declaredObjectColumns = declared == null ? Collections.emptyMap() : declared;
   }

   public Map<String, List<String>> getDeclaredObjectColumns() {
      return declaredObjectColumns;
   }

   /**
    * Maximum number of rows scanned when discovering columns and checking declared objects.
    */
   protected static final int HEADER_SCAN_ROWS = 10000;

   /**
    * Replaces the column of each declared object that is null on every scanned row with the
    * declared leaf columns. Leaves already present are not repeated, so rows that mix null and
    * non-null objects lose the stray object column. Non-null data is untouched.
    *
    * @param headers the discovered column names.
    * @param allNull tells whether a column is null on every scanned row.
    *
    * @return {@code headers} itself when nothing changed, otherwise the new column names.
    */
   protected List<String> applyDeclaredObjectColumns(List<String> headers,
                                                     Predicate<String> allNull)
   {
      if(declaredObjectColumns.isEmpty()) {
         return headers;
      }

      List<String> result = headers;

      for(Map.Entry<String, List<String>> e : declaredObjectColumns.entrySet()) {
         int idx = result.indexOf(e.getKey());

         if(idx < 0 || !allNull.test(e.getKey())) {
            continue;
         }

         if(result == headers) {
            result = new ArrayList<>(headers);
         }

         result.remove(idx);
         int at = idx;

         for(String leaf : e.getValue()) {
            if(!result.contains(leaf)) {
               result.add(at++, leaf);
            }
         }
      }

      return result;
   }

   public Object getJsonMetadata() {
      return jsonMetadata;
   }

   public void setJsonMetadata(Object jsonMetadata) {
      this.jsonMetadata = jsonMetadata;
   }

   /**
    * A date/time value inferred from text that remembers the text it was parsed from.
    */
   interface RawTemporal {
      String getRawText();

      Class<?> getBaseClass();

      Object toPlain();
   }

   private static final class RawDate extends java.sql.Date implements RawTemporal {
      RawDate(long millis, String raw) {
         super(millis);
         this.raw = raw;
      }

      @Override
      public String getRawText() {
         return raw;
      }

      @Override
      public Class<?> getBaseClass() {
         return java.sql.Date.class;
      }

      @Override
      public Object toPlain() {
         return new java.sql.Date(getTime());
      }

      private final String raw;
   }

   private static final class RawTimestamp extends java.sql.Timestamp implements RawTemporal {
      RawTimestamp(java.sql.Timestamp ts, String raw) {
         super(ts.getTime());
         setNanos(ts.getNanos());
         this.raw = raw;
      }

      @Override
      public String getRawText() {
         return raw;
      }

      @Override
      public Class<?> getBaseClass() {
         return java.sql.Timestamp.class;
      }

      @Override
      public Object toPlain() {
         java.sql.Timestamp ts = new java.sql.Timestamp(getTime());
         ts.setNanos(getNanos());
         return ts;
      }

      private final String raw;
   }

   private static final class RawTime extends java.sql.Time implements RawTemporal {
      RawTime(long millis, String raw) {
         super(millis);
         this.raw = raw;
      }

      @Override
      public String getRawText() {
         return raw;
      }

      @Override
      public Class<?> getBaseClass() {
         return java.sql.Time.class;
      }

      @Override
      public Object toPlain() {
         return new java.sql.Time(getTime());
      }

      private final String raw;
   }

   /**
    * Value class for specifying formats.
    */
   private static final class FormatSpec {
      FormatSpec(String type, String extent) {
         this.type = type;
         this.extent = extent;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         final FormatSpec that = (FormatSpec) o;

         return Objects.equals(type, that.type) &&
            Objects.equals(extent, that.extent);
      }

      @Override
      public int hashCode() {
         return Objects.hash(type, extent);
      }

      private final String type;
      private final String extent;
   }

   private int maxRows = -1;
   private boolean truncated;
   private final Map<String, String> colTypes = new Object2ObjectOpenHashMap<>();
   private final Map<String, String> colFormats = new Object2ObjectOpenHashMap<>();
   private final Map<String, String> colExtents = new Object2ObjectOpenHashMap<>();
   private final Map<FormatSpec, Format> fmtCache = new Object2ObjectOpenHashMap<>();
   // type detected from value during processing
   private final Map<String, String> colTypes2 = new Object2ObjectOpenHashMap<>();
   private final Set<String> isoDateFailed = new ObjectOpenHashSet<>();
   private final boolean usLocale = Locale.getDefault().equals(Locale.US);
   private Boolean dmyOrder;
   private Object jsonMetadata;
   private Map<String, List<String>> declaredObjectColumns = Collections.emptyMap();

   private static final java.util.regex.Pattern OFFSET_PATTERN = java.util.regex.Pattern.compile(
      "^(.*\\d:\\d{2}(?::\\d{2}(?:[.,]\\d+)?)?)\\s*(?:Z|[+-]\\d{2}(?::?\\d{2})?)$");
   private final static DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
   private final static DateTimeFormatter EUROPE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
   private final static DateTimeFormatter US_DATE_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
   private final static DateTimeFormatter EUROPE_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
   // a declared order does not fix the width of the day and month, e.g. 5/10/2026
   private final static DateTimeFormatter DMY_DATE = DateTimeFormatter.ofPattern("d/M/yyyy");
   private final static DateTimeFormatter MDY_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");
   private final static DateTimeFormatter DMY_DATE_TIME =
      DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss");
   private final static DateTimeFormatter MDY_DATE_TIME =
      DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss");

   private static final Logger LOG = LoggerFactory.getLogger(BaseJsonTable.class.getName());
}
