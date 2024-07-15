/*
 * inetsoft-tabular-util - StyleBI is a business intelligence web application.
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
package inetsoft.uql.util;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

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
         return LocalDate.parse(input, usLocale ? US_DATE : EUROPE_DATE);
      }

      return LocalDate.parse(input, DateTimeFormatter.ISO_DATE);
   }

   private LocalDateTime parseDateTime(String input) {
      if(input.contains("/")) {
         return LocalDateTime.parse(input, usLocale ? US_DATE_TIME : EUROPE_DATE_TIME);
      }

      try {
         return LocalDateTime.parse(input, DateTimeFormatter.ISO_DATE_TIME);
      }
      catch(DateTimeParseException ex) {
         return LocalDateTime.parse(input, DateTimeFormatter.ISO_INSTANT);
      }
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

      String type = getJavaType(header, str);
      String formatType = getColumnFormat(header);
      String formatExtent = getColumnFormatExtent(header);

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
                  if(!isoDateFailed.contains(header)) {
                     return java.sql.Date.valueOf(parseDate(str));
                  }
               }
               catch(DateTimeParseException ex) {
                  // don't try iso parsing again if failed.
                  isoDateFailed.add(header);
               }

               return new java.sql.Date(DateTime.parse(str).toMillis());
            }
            else if(XSchema.TIME_INSTANT.equals(type)) {
               try {
                  if(!isoDateFailed.contains(header)) {
                     return java.sql.Timestamp.valueOf(parseDateTime(str));
                  }
               }
               catch(DateTimeParseException ex) {
                  isoDateFailed.add(header);
               }

               return DateTime.parse(str).toTimestamp();
            }
            else if(XSchema.TIME.equals(type)) {
               return java.sql.Time.valueOf(parseTime(str));
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

   public int getMaxRows() {
      return maxRows;
   }

   public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
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
   private final Map<String, String> colTypes = new Object2ObjectOpenHashMap<>();
   private final Map<String, String> colFormats = new Object2ObjectOpenHashMap<>();
   private final Map<String, String> colExtents = new Object2ObjectOpenHashMap<>();
   private final Map<FormatSpec, Format> fmtCache = new Object2ObjectOpenHashMap<>();
   // type detected from value during processing
   private final Map<String, String> colTypes2 = new Object2ObjectOpenHashMap<>();
   private final Set<String> isoDateFailed = new ObjectOpenHashSet<>();
   private final boolean usLocale = Locale.getDefault().equals(Locale.US);

   private final static DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
   private final static DateTimeFormatter EUROPE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
   private final static DateTimeFormatter US_DATE_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
   private final static DateTimeFormatter EUROPE_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

   private static final Logger LOG = LoggerFactory.getLogger(BaseJsonTable.class.getName());
}
