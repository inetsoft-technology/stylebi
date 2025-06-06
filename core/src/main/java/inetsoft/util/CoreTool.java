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
package inetsoft.util;

import com.google.common.collect.ImmutableMap;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.web.viewsheet.command.MessageCommand.Type;
import org.apache.commons.lang3.math.NumberUtils;
import org.pojava.datetime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.stream.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.*;
import java.time.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static inetsoft.util.Tool.buildString;
import static inetsoft.util.Tool.isNumberClass;

/**
 * Common utility methods without dependency on any inetsoft packages.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CoreTool {
   private static final Logger LOG =
      LoggerFactory.getLogger(CoreTool.class);
   /**
    * Date format.
    */
   public static final ThreadLocal<DateFormat> yearFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'y' ''yyyy''}"));
   /**
    * Date format.
    */
   public static final ThreadLocal<DateFormat> monthFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'m' ''yyyy-MM''}"));
   /**
    * Date format.
    */
   public static final ThreadLocal<DateFormat> dayFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'d' ''yyyy-MM-ddd''}"));
   /**
    * Date format.
    */
   public static final ThreadLocal<DateFormat> dateFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'d' ''yyyy-MM-dd''}"));
   /**
    * Old time format.
    */
   public static final ThreadLocal<DateFormat> timeFmt_old =
      ThreadLocal.withInitial(() -> createDateFormat("{'d' ''HH:mm:ss''}"));
   /**
    * Old date time format.
    */
   public static final ThreadLocal<DateFormat> timeInstantFmt_old =
      ThreadLocal.withInitial(() -> createDateFormat("{'d' ''yyyy-MM-dd HH:mm:ss''}"));
   /**
    * Time format.
    */
   public static final ThreadLocal<DateFormat> timeFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'t' ''HH:mm:ss''}"));
   /**
    * Date time format.
    */
   public static final ThreadLocal<DateFormat> timeInstantFmt =
      ThreadLocal.withInitial(() -> createDateFormat("{'ts' ''yyyy-MM-dd HH:mm:ss''}"));

   /**
    * Shared thread local GregorianCalendar.
    */
   public static final ThreadLocal<GregorianCalendar> calendar =
      ThreadLocal.withInitial(GregorianCalendar::new);

   /**
    * Second shared thread local GregorianCalendar.
    */
   public static final ThreadLocal<GregorianCalendar> calendar2 =
      ThreadLocal.withInitial(GregorianCalendar::new);

   /**
    * Null type.
    */
   public static final String NULL = "null";
   /**
    * String type.
    */
   public static final String STRING = "string";
   /**
    * Boolean type.
    */
   public static final String BOOLEAN = "boolean";
   /**
    * Float type.
    */
   public static final String FLOAT = "float";
   /**
    * Double type.
    */
   public static final String DOUBLE = "double";
   /**
    * Decimal type.
    */
   public static final String DECIMAL = "decimal";
   /**
    * Character type.
    */
   public static final String CHAR = "char";
   /**
    * Character type.
    */
   public static final String CHARACTER = "character";
   /**
    * Byte type.
    */
   public static final String BYTE = "byte";
   /**
    * Short type.
    */
   public static final String SHORT = "short";
   /**
    * Integer type.
    */
   public static final String INTEGER = "integer";
   /**
    * Long type.
    */
   public static final String LONG = "long";
   /**
    * Time instant type.
    */
   public static final String TIME_INSTANT = "timeInstant";
   /**
    * Date type.
    */
   public static final String DATE = "date";
   /**
    * Time type.
    */
   public static final String TIME = "time";
   /**
    * Array type.
    */
   public static final String ARRAY = "array";
   /**
    * Enum type.
    */
   public static final String ENUM = "enum";
   /**
    * Color type.
    */
   public static final String COLOR = "color";

   /**
    * Date time format.
    */
   public static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
   /**
    * Time format.
    */
   public static final String DEFAULT_TIME_PATTERN = "HH:mm:ss";
   /**
    * Date format.
    */
   public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

   /**
    * Check if two object equals, null equals null.
    */
   public static boolean equals(Object obj, Object obj2) {
      return equals(obj, obj2, true);
   }

   /**
    * Check if two object equals, null equals null.
    */
   public static boolean equals(Object obj, Object obj2, boolean sensitive) {
      if(obj != null && obj2 != null) {
         // is array?
         if(obj.getClass().isArray()) {
            if(!obj2.getClass().isArray()) {
               return false;
            }

            if(obj instanceof Object[]) {
               return isObjectArrayEquals(obj, obj2, sensitive);
            }
            else if(obj instanceof boolean[]) {
               return Arrays.equals((boolean[]) obj, (boolean[]) obj2);
            }
            else if(obj instanceof byte[]) {
               return Arrays.equals((byte[]) obj, (byte[]) obj2);
            }
            else if(obj instanceof char[]) {
               return Arrays.equals((char[]) obj, (char[]) obj2);
            }
            else if(obj instanceof double[]) {
               return Arrays.equals((double[]) obj, (double[]) obj2);
            }
            else if(obj instanceof float[]) {
               return Arrays.equals((float[]) obj, (float[]) obj2);
            }
            else if(obj instanceof int[]) {
               return Arrays.equals((int[]) obj, (int[]) obj2);
            }
            else if(obj instanceof long[]) {
               return Arrays.equals((long[]) obj, (long[]) obj2);
            }
            else if(obj instanceof short[]) {
               return Arrays.equals((short[]) obj, (short[]) obj2);
            }
         }
         // is not array?
         else if(obj instanceof Number && obj2 instanceof Number) {
            return ((Number) obj).doubleValue() == ((Number) obj2).doubleValue();
         }
         else if(obj instanceof Date && obj2 instanceof Date) {
            return ((Date) obj).getTime() == ((Date) obj2).getTime();
         }
         else if(!sensitive && obj instanceof String &&
                 obj2 instanceof String)
         {
            return ((String) obj).equalsIgnoreCase((String) obj2);
         }

         return obj.equals(obj2);
      }

      return obj == obj2;
   }

   public static boolean isObjectArrayEquals(Object obj, Object obj2, boolean sensitive) {
      Object[] arr1 = (Object[]) obj;
      Object[] arr2 = (Object[]) obj2;

      if(arr1.length != arr2.length) {
         return false;
      }

      for(int i = 0; i < arr1.length; i++) {
         if(!equals(arr1[i], arr2[i], sensitive)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Utility method that takes care of the numeric convertion bug
    * in jdk. For example a Number representing 0.1 + 0.2 should not =
    * 0.30000000000000004.
    */
   public static String toString(Object obj) {
      if(obj == null) {
         return "";
      }

      // toString() for Timestamp and Date is slow, use format instead
      if(obj instanceof Date) {
         if(obj instanceof Time) {
            return formatTime((Date) obj);
         }
         else if(obj instanceof Timestamp) {
            return formatDateTime((Timestamp) obj);
         }
         else if(obj instanceof java.sql.Date) {
            return formatDate((Date) obj);
         }
         else {
            return formatDateTime((Date) obj);
         }
      }

      String txt = "";

      try {
         txt = obj.toString();
      }
      catch(Exception ex) {
         LOG.error("toString() failed: " + obj, ex);
      }

      // jdk bug,
      // make sure the string is not too long
      // in jdk (0.1 + 0.2) == 0.30000000000000004,
      // and (0.7 + 0.1) == 0.79999999999, JDK bug
      if(obj instanceof Number) {
         int p = txt.indexOf('.');

         if(p >= 0) {
            int e = txt.indexOf('E');
            boolean neg = txt.startsWith("-");

            // scientific notation
            if(p == (neg ? 2 : 1) && e > 0) {
               String s0 = txt.substring(0, e);
               // delete '.'
               StringBuilder str = new StringBuilder(s0.substring(p + 1));
               str.insert(0, s0.substring(0, p));
               int n = Integer.parseInt(txt.substring(e + 1));

               if(n > 0) {
                  n = neg ? n + 1 : n;

                  // insert dot
                  if(str.length() > n + 1) {
                     str.insert(n + 1, '.');
                  }
                  else {
                     while(str.length() < n + 1) {
                        str.append("0");
                     }
                  }
               }
               else {
                  // remove trailing 0
                  if(str.charAt(str.length() - 1) == '0') {
                     str.deleteCharAt(str.length() - 1);
                  }

                  String sign = null;

                  if(str.charAt(0) == '-') {
                     sign = "-";
                     str.deleteCharAt(0);
                  }

                  for(int i = 0; i < -n; i++) {
                     str.insert(0, "0");
                  }

                  str.insert(1, ".");

                  if(sign != null) {
                     str.insert(0, sign);
                  }
               }

               return str.toString();
            }

            String t = txt.substring(p + 1);
            int p2;

            if((p2 = t.indexOf("999999")) >= 0 &&
               t.lastIndexOf("999999") >= 1)
            {
               txt = toString((Number) obj, p2);
            }
            else if((p2 = t.indexOf("000000")) >= 0 &&
                    t.lastIndexOf("000000") >= 1)
            {
               p2 = Math.max(p2, 1);
               txt = txt.substring(0, p + p2 + 1);
            }

            int pidx = txt.lastIndexOf('.'); // point index

            if(pidx >= 0) {
               int len = txt.length();
               int zidx = len; // last zero index

               for(; zidx > pidx; zidx--) {
                  char c = txt.charAt(zidx - 1);

                  if(c != '0') {
                     break;
                  }
               }

               // .000 pattern? trim point as well
               if(zidx == pidx + 1) {
                  txt = txt.substring(0, pidx);
               }
               // .990 pattern?
               else if(zidx != len) {
                  txt = txt.substring(0, zidx);
               }
            }
         }
      }

      return txt;
   }

   /**
    * Synchronizely call dateformat format.
    */
   public static String formatDate(Date date) {
      return DATE_FORMAT_CACHE.format(date);
   }

   /**
    * Synchronizely call dateformat format.
    */
   public static String formatDateTime(Date date) {
      return DATETIME_FORMAT_CACHE.format(date);
   }

   /**
    * Synchronizely call dateformat format.
    */
   public static String formatTime(Date date) {
      return TIME_FORMAT_CACHE.format(date);
   }

   /**
    * Get long type value for localDateTime time
    * @param localDateTime that need convert
    */
   public static long getTimestampOfDateTime(LocalDateTime localDateTime) {
       ZoneId zone = ZoneId.systemDefault();
       Instant instant = localDateTime.atZone(zone).toInstant();
       return instant.toEpochMilli();
   }

   /**
    * Get LocalDateTime type time for timestamp
    * @param timestamp that is long type value
    */
   public static LocalDateTime getDateTimeOfTimestamp(long timestamp) {
       Instant instant = Instant.ofEpochMilli(timestamp);
       ZoneId zone = ZoneId.systemDefault();
       return LocalDateTime.ofInstant(instant, zone);
   }

   /**
    * Get the string representation of a number with specified precision.
    * @param n Number value.
    * @param precision precision as number of digits after decimal point.
    * @return string representation.
    */
   public static String toString(Number n, int precision) {
      return (n == null) ? "" : toString(n.doubleValue(), precision);
   }

   /**
    * Get the string representation of a number with specified precision.
    * @param val number value.
    * @param precision precision as number of digits after decimal point.
    * @return string representation.
    */
   public static String toString(double val, int precision) {
      if(Math.abs(val) > Long.MAX_VALUE) {
         return toString(Double.toString(val), precision);
      }

      // if integer and precision is zero, return
      if(0 == precision) {
         return Long.toString(Math.round(val));
      }

      String sign = (val < 0) ? "-" : "";
      long factor1 = (long) Math.pow(10, precision - 1);
      long factor = factor1 * 10;

      val = Math.abs(val);
      long ival = Math.round(val * factor);
      String istr = sign + (ival / factor);
      long rem = (factor == 0) ? 0 : ival % factor;

      if(rem == 0) {
         StringBuilder remstr = new StringBuilder();
         int i = precision;

         while(i > 0) {
            remstr.insert(0, "0");
            i--;
         }

         return istr + "." + remstr;
      }
      else {
         // if 0.02
         StringBuilder remstr = new StringBuilder(Long.toString(rem));

         while(rem < factor1) {
            rem *= 10;
            remstr.insert(0, "0");
         }

         return istr + "." + remstr;
      }
   }

   /**
    * Internal precision function.
    */
   private static String toString(String v, int precision) {
      int pos = v.indexOf('.');
      String rc = (pos > 0 && (v.length() - pos - 1) > precision) ?
         v.substring(0, pos + precision + 1) :
         v;

      return rc.endsWith(".0") ? rc.substring(0, rc.length() - 2) : rc;
   }

   /**
    * Create an Extended version of the SimpleDateFormatter
    * @return an instance of the ExtendedDateFormat
    */
   public static SimpleDateFormat createDateFormat() {
      return new ExtendedDateFormat();
   }

   /**
    * Create an Extended version of the SimpleDateFormatter.
    * @return an instance of the ExtendedDateFormat.
    */
   public static SimpleDateFormat createDateFormat(String pattern) {
      DateFormat fmt = null;

      if("FULL".equalsIgnoreCase(pattern)) {
         fmt = DateFormat.getDateInstance(DateFormat.FULL);
      }
      else if("LONG".equalsIgnoreCase(pattern)) {
         fmt = DateFormat.getDateInstance(DateFormat.LONG);
      }
      else if("MEDIUM".equalsIgnoreCase(pattern)) {
         fmt = DateFormat.getDateInstance(DateFormat.MEDIUM);
      }
      else if("SHORT".equalsIgnoreCase(pattern)) {
         fmt = DateFormat.getDateInstance(DateFormat.SHORT);
      }
      else if("yyyy".equalsIgnoreCase(pattern)) {
         fmt = new SimpleDateFormat("yyyy");
      }

      if(fmt instanceof SimpleDateFormat) {
         return (SimpleDateFormat) fmt;
      }

      return new ExtendedDateFormat(pattern);
   }

   /**
    * Create an Extended version of the SimpleDateFormatter.
    * @return an instance of the ExtendedDateFormat.
    */
   public static SimpleDateFormat createDateFormat(String pattern,
                     Locale locale)
   {
      if(locale == null) {
         return createDateFormat(pattern);
      }

      DateFormat fmt = null;

      if(pattern.equalsIgnoreCase("FULL")) {
         fmt = DateFormat.getDateInstance(DateFormat.FULL, locale);
      }
      else if(pattern.equalsIgnoreCase("LONG")) {
         fmt = DateFormat.getDateInstance(DateFormat.LONG, locale);
      }
      else if(pattern.equalsIgnoreCase("MEDIUM")) {
         fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
      }
      else if(pattern.equalsIgnoreCase("SHORT")) {
         fmt = DateFormat.getDateInstance(DateFormat.SHORT, locale);
      }
      else if(pattern.equalsIgnoreCase("yyyy")) {
         fmt = new SimpleDateFormat("yyyy", locale);
      }

      if(fmt instanceof SimpleDateFormat) {
         return (SimpleDateFormat) fmt;
      }

      return new ExtendedDateFormat(pattern, locale);
   }

   /**
    * Convert an array to comma separated string.
    */
   public static String arrayToString(Object array) {
      return arrayToString(array, ",");
   }

   /**
    * Convert an array to comma separated string.
    */
   public static String arrayToString(Object array, String delim) {
      return arrayToString(array, delim, false);
   }

   /**
    * Convert an array to comma separated string.
    */
   public static String arrayToString(Object array, String delim, boolean fakeNull) {
      if(array == null) {
         return "";
      }

      if(!array.getClass().isArray()) {
         return toString(array);
      }

      int len = Array.getLength(array);
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < len; i++) {
         if(i > 0) {
            buf.append(delim);
         }

         Object val = Array.get(array, i);

         if(val != null && val.getClass().isArray()) {
            buf.append("[").append(arrayToString(val, delim, fakeNull)).append("]");
         }
         else {
            val = val == null && fakeNull ? CoreTool.FAKE_NULL : val;
            buf.append(val);
         }
      }

      return buf.toString();
   }

   /**
    * Get the data of a data type from a value.
    * @param type the specified data type.
    * @param val the specified value.
    * @return the data transformed from the value.
    */
   public static Object getData(Class<?> type, Object val) {
      return getData(getDataType(type), val);
   }

   /**
    * Get the data of a data type from a value.
    * @param type the specified data type.
    * @param val the specified value.
    * @return the data transformed from the value.
    */
   public static Object getData(String type, Object val) {
      if(type == null) {
         return val;
      }

      int code = getTypeCode(type);

      try {
         switch(code) {
         case CODE_STRING:
            return getStringData(val);
         case CODE_DOUBLE:
            return getDoubleData(val);
         case CODE_INTEGER:
            return getIntegerData(val);
         case CODE_FLOAT:
            return getFloatData(val);
         case CODE_DATE:
            return getDateData(val);
         case CODE_BOOLEAN:
            return getBooleanData(val);
         case CODE_COLOR:
            return getColorData(val);
         case CODE_LONG:
            return getLongData(val);
         case CODE_SHORT:
            return getShortData(val);
         case CODE_TIME_INSTANT:
            return getTimeInstantData(val);
         case CODE_TIME:
            return getTimeData(val);
         case CODE_NULL:
            return null;
         case CODE_BYTE:
            return getByteData(val);
         case CODE_CHAR:
            return getCharData(val);
         case CODE_CHARACTER:
            return getCharacterData(val);
         default:
            return val;
         }
      }
      catch(Exception ignore) {
      }

      return null;
   }

   /**
    * Get data type of a class.
    * @param c the specified class.
    * @return the data type defined in <tt>XSchema</tt>.
    */
   public static String getDataType(Class<?> c) {
      String type;

      if(c == null) {
         type = STRING;
      }
      else if(String.class == c) {
         type = STRING;
      }
      else if(Integer.class == c || int.class == c) {
         type = INTEGER;
      }
      else if(Double.class == c || double.class == c) {
         type = DOUBLE;
      }
      else if(Float.class == c || float.class == c) {
         type = FLOAT;
      }
      else if(java.sql.Date.class == c) {
         type = DATE;
      }
      else if(java.sql.Time.class == c) {
         type = TIME;
      }
      else if(java.sql.Timestamp.class == c) {
         type = TIME_INSTANT;
      }
      else if(Character.class == c || char.class == c) {
         type = CHARACTER;
      }
      else if(Boolean.class == c || boolean.class == c) {
         type = BOOLEAN;
      }
      else if(Byte.class == c || byte.class == c) {
         type = BYTE;
      }
      else if(Short.class == c || short.class == c) {
         type = SHORT;
      }
      else if(Long.class == c || long.class == c) {
         type = LONG;
      }
      // @by billh, end user might use java.util.Date as datetime type
      else if(java.util.Date.class.isAssignableFrom(c)) {
         type = TIME_INSTANT;
      }
      else if(java.awt.Color.class == c) {
         type = COLOR;
      }
      // @by charvi 2004-02-11
      // Some of the column types are java.math.BigDecimal or
      // java.math.BigInteger. These are now mapped to
      // XSchema.DOUBLE and XSchema.INTEGER, respectively.
      else if(java.math.BigDecimal.class == c) {
         type = DOUBLE;
      }
      else if(java.math.BigInteger.class == c) {
         type = INTEGER;
      }
      else if(java.lang.Number.class.isAssignableFrom(c)) {
         type = DOUBLE;
      }
      else if(Object[].class.isAssignableFrom(c)) {
         type = ARRAY;
      }
      else {
         type = STRING;
      }

      return type;
   }

   /**
    * Check if the value is valid data type, and result the data by current data type.
    * @param type   the data type.
    * @param value  the current value.
    */
   public static Map<String, Object> checkAndGetData(String type, Object value) {
      if(type == null) {
         return null;
      }

      String validKey = "valid";
      String resultKey = "result";
      Map<String, Object> map = new HashMap<>();
      map.put(validKey, "true");

      if(NULL.equals(value) || value == null) {
         map.put(resultKey, value);
         return map;
      }

      int code = getTypeCode(type);
      String val = value + "";
      Object result;

      try {
         switch(code) {
         case CODE_BOOLEAN:
            if("true".equalsIgnoreCase(val) ||
               "false".equalsIgnoreCase(val))
            {
               map.put(resultKey, "true".equalsIgnoreCase(val));
               return map;
            }

            result = NumberParserWrapper.getDouble(val) != 0;
            map.put(resultKey, result);
            break;
         case CODE_BYTE:
            try {
               result = Byte.valueOf(val);
            }
            catch(Exception ex) {
               double dval = Double.parseDouble(val);
               result = (byte) dval;
            }

            map.put(resultKey, result);
            break;
         case CODE_CHARACTER:
            map.put(resultKey, val.charAt(0));
            break;
         case CODE_DOUBLE:
            map.put(resultKey, NumberParserWrapper.getDouble(val));
            break;
         case CODE_FLOAT:
            map.put(resultKey, (float) NumberParserWrapper.getDouble(val));
            break;
         case CODE_INTEGER:
            try {
               result = Integer.valueOf(val);
            }
            catch(Exception ex) {
               double dval = Double.parseDouble(val);
               result = (int) dval;
            }

            map.put(resultKey, result);
            break;
         case CODE_LONG:
            try {
               result = Long.valueOf(val);
            }
            catch(Exception ex) {
               result = Double.parseDouble(val);
            }

            map.put(resultKey, result);
            break;
         case CODE_SHORT:
            try {
               result = Short.valueOf(val);
            }
            catch(Exception ex) {
               double dval = Double.parseDouble(val);
               result = (short) dval;
            }

            map.put(resultKey, result);
            break;
         case CODE_DATE:
            Date date;

            try {
               date = parseDate(val);
            }
            catch(Exception ex) {
               try {
                  date = parseDateTime(val);
               }
               catch(Exception ex2) {
                  date = parseTime(val);
               }
            }

            result = new java.sql.Date(date.getTime());
            map.put(resultKey, result);
            break;
         case CODE_TIME_INSTANT:
            Date timeInstant;

            try {
               timeInstant = parseDateTime(val);
            }
            catch(Exception ex) {
               try {
                  timeInstant = parseDate(val);
               }
               catch(Exception ex2) {
                  timeInstant = parseTime(val);
               }
            }

            result = new java.sql.Timestamp(timeInstant.getTime());
            map.put(resultKey, result);
            break;
         case CODE_TIME:
            Date time;

            try {
               time = parseTime(val);
            }
            catch(Exception ex) {
               try {
                  time = parseDateTime(val);
               }
               catch(Exception ex2) {
                  time = parseDate(val);
               }
            }

            result = new java.sql.Time(time.getTime());
            map.put(resultKey, result);
            break;
         }
      }
      catch(Exception ex) {
         map.put(validKey, "false");
      }

      map.putIfAbsent(resultKey, value);
      return map;
   }

   /**
    * Get a typed value from its string representation which come from persistence data.
    * @param type String representation of the type.
    * @param val String representation of the value.
    * @return typed value.
    */
   public static Object getPersistentData(String type, String val) {
      return getData(type, val, true);
   }

   /**
    * Get a typed value from its string representation. For example
    * getData("Integer", "15") returns an Integer object with value 15.
    * @param type String representation of the type.
    * @param val String representation of the value.
    * @return typed value.
    */
   public static Object getData(String type, String val) {
      return getData(type, val, false);
   }

   /**
    * Get a typed value from its string representation. For example
    * getData("Integer", "15") returns an Integer object with value 15.
    * @param type String representation of the type.
    * @param val String representation of the value.
    * @param strictNull if true, null values were identified with FAKE_NULL so that
    *                   they can be strictly distinguished, else not.
    * @return typed value.
    */
   public static Object getData(String type, String val, boolean strictNull) {
      try {
         if(strictNull && FAKE_NULL.equals(val) || !strictNull && NULL.equals(val) || val == null) {
            return null;
         }
         else if(type == null) {
            return val;
         }

         int code = getTypeCode(type);

         switch(code) {
         case CODE_NULL:
            return null;
         case CODE_STRING:
            if(!strictNull && val.isEmpty()) {
               return null;
            }

            return val;
         case CODE_CHAR:
            return val;
         case CODE_BOOLEAN:
            if(val.equalsIgnoreCase("true")) {
               return Boolean.TRUE;
            }
            else if(val.equalsIgnoreCase("false")) {
               return Boolean.FALSE;
            }

            try {
               return NumberParserWrapper.getDouble(val) != 0;
            }
            catch(Exception e) {
               return Boolean.FALSE;
            }
         case CODE_BYTE:
            try {
               return Byte.valueOf(val);
            }
            catch(Exception ex) {
               double dval = parseDouble(val);
               return (byte) dval;
            }
         case CODE_CHARACTER:
            return val.charAt(0);
         case CODE_DOUBLE:
            return parseDouble(val);
         case CODE_FLOAT:
            return (float) parseDouble(val);
         case CODE_INTEGER:
            try {
               return Integer.valueOf(val);
            }
            catch(Exception ex) {
               double dval = parseDouble(val);
               return (int) dval;
            }
         case CODE_LONG:
            try {
               return Long.valueOf(val);
            }
            catch(Exception ex) {
               return parseDouble(val);
            }
         case CODE_SHORT:
            try {
               return Short.valueOf(val);
            }
            catch(Exception ex) {
               double dval = parseDouble(val);
               return (short) dval;
            }
         case CODE_DATE:
            Date date;

            try {
               date = parseDate(val);
            }
            catch(Exception ex) {
               try {
                  date = parseDateTime(val);
               }
               catch(Exception ex2) {
                  date = parseTime(val);
               }
            }

            return new java.sql.Date(date.getTime());
         case CODE_TIME_INSTANT:
            Date timeInstant;

            try {
               timeInstant = parseDateTime(val);
            }
            catch(Exception ex) {
               try {
                  timeInstant = parseDate(val);
               }
               catch(Exception ex2) {
                  timeInstant = parseTime(val);
               }
            }

            return new java.sql.Timestamp(timeInstant.getTime());
         case CODE_TIME:
            Date time;

            try {
               time = parseTime(val);
            }
            catch(Exception ex) {
               try {
                  time = parseDateTime(val);
               }
               catch(Exception ex2) {
                  time = parseDate(val);
               }
            }

            return new java.sql.Time(time.getTime());
         case CODE_COLOR:
            try {
               int ival = Integer.parseInt(val);
               return new Color(ival);
            }
            catch(Exception ex) {
               return null;
            }
         case CODE_ARRAY:
            String[] vals = split(val, '^');
            Object[] res = new Object[vals.length];

            for(int i = 0; i < vals.length; i++) {
               String[] temp = split(vals[i], '~');

               try {
                  res[i] = getData(temp[0], temp[1]);
               }
               catch(Exception ignore) {
               }
            }

            return res;
         }
      }
      catch(NumberFormatException ex) {
         // not a valid number, return null (instead of an empty string)
         return null;
      }
      catch(Exception ignore) {
      }

      return val;
   }

   private static double parseDouble(String str) {
      try {
         return Double.parseDouble(str);
      }
      catch(NumberFormatException ex) {
         if(str.endsWith("%")) {
            return parseDouble(str.substring(0, str.length() - 1)) / 100;
         }

         throw ex;
      }
   }

   /**
    * Get the java class of one data type.
    * @param type the specified data type.
    * @return the java class of the data type, <tt>null</tt> not found.
    */
   public static Class<?> getDataClass(String type) {
      if(type == null) {
         return String.class;
      }

      int code = getTypeCode(type);

      switch(code) {
      case CODE_NULL:
         return String.class;
      case CODE_STRING:
         return String.class;
      case CODE_BOOLEAN:
         return Boolean.class;
      case CODE_BYTE:
         return Byte.class;
      case CODE_CHAR:
         return String.class;
      case CODE_CHARACTER:
         return Character.class;
      case CODE_DOUBLE:
         return Double.class;
      case CODE_FLOAT:
         return Float.class;
      case CODE_INTEGER:
         return Integer.class;
      case CODE_LONG:
         return Long.class;
      case CODE_SHORT:
         return Short.class;
      case CODE_DATE:
         return java.sql.Date.class;
      case CODE_TIME_INSTANT:
         return java.sql.Timestamp.class;
      case CODE_TIME:
         return java.sql.Time.class;
      case CODE_COLOR:
         return java.awt.Color.class;
      default:
         return String.class;
      }
   }

   /**
    * Get the string representation of the data type of an object. For
    * example getDataType(Integer.valueOf(15)) returns "Integer".
    * @param val the object to inspect the type of.
    * @return string representation of the data type.
    */
   public static String getDataType(Object val) {
      if(val == null) {
         return NULL;
      }
      else {
         Class<?> cls = val.getClass();
         return getDataType(cls);
      }
   }

   /**
    * Get a string representation of a value.
    * @param val the object to get a string representation of
    */
   public static String getDataString(Object val, String dtype) {
      return getDataString(val, dtype, false);
   }

   /**
    * Get a string representation of a value.
    * @param val the object to get a string representation of
    */
   public static String getDataString(Object val, String dtype, boolean strictNull) {
      if(val instanceof String) {
         return (String) val;
      }
      else if(val instanceof Date) {
         if(DATE.equals(dtype)) {
            return formatDate((Date) val);
         }
         else if(TIME_INSTANT.equals(dtype)) {
            return formatDateTime((Date) val);
         }
         else if(TIME.equals(dtype)) {
            return formatTime((Date) val);
         }
      }
      else if(val instanceof Number) {
         if(BYTE.equals(dtype) || SHORT.equals(dtype) ||
            INTEGER.equals(dtype) || LONG.equals(dtype))
         {
            return Long.toString(((Number) val).longValue());
         }
      }

      return getDataString(val, true, strictNull);
   }

   /**
    *  Get a string representation of a value for persistence.
    * @param val the object to get a string representation of.
    * @param dtype the object type of the target value.
    */
   public static String getPersistentDataString(Object val, String dtype) {
      return getDataString(val, dtype, true);
   }

   /**
    *  Get a string representation of a value for persistence.
    * @param val the object to get a string representation of.
    */
   public static String getPersistentDataString(Object val) {
      return getDataString(val, true, true);
   }

   /**
    * Get a string representation of a value.
    * @param val the object to get a string representation of.
    */
   public static String getDataString(Object val) {
      return getDataString(val, true, false);
   }

   /**
    * Get a string representation of a value.
    * @param val the object to get a string representation of.
    */
   public static String getDataString(Object val, boolean keepBlank) {
      return getDataString(val, keepBlank, false);
   }

   /**
    * Get a string representation of a value.
    * @param val the object to get a string representation of.
    * @param keepBlank the flag to control display blank or not.
    * @param strictNull if true, null values should be identified with FAKE_NULL so that
    *                   they can be strictly distinguished, else not.
    */
   public static String getDataString(Object val, boolean keepBlank, boolean strictNull) {
      // for null, return null instead of blank
      if(val == null) {
         return strictNull ? FAKE_NULL : keepBlank ? NULL : null;
      }
      else if(val instanceof java.sql.Date) {
         return formatDate((Date) val);
      }
      else if(val instanceof java.sql.Time) {
         return formatTime((Date) val);
      }
      else if(val instanceof java.sql.Timestamp) {
         return formatDateTime((Date) val);
      }
      else if(val instanceof Date) {
         return formatDateTime((Date) val);
      }
      else if(val instanceof Number) {
         double dval = ((Number) val).doubleValue();

         if(dval == Math.ceil(dval) && Math.abs(dval) < Integer.MAX_VALUE) {
            return Integer.toString((int) dval);
         }

         return String.valueOf(val);
      }
      else if(val instanceof Object[]) {
         StringBuilder buffer = new StringBuilder();
         Object[] vals = (Object[]) val;

         for(int i = 0; i < vals.length; i++) {
            buffer.append(i != 0 ? "^" : "").append(getDataType(vals[i])).append("~")
               .append(getDataString(vals[i], true, strictNull));
         }

         return buffer.toString();
      }
      else if(val instanceof String) {
         return (String) val;
      }
      else {
         return val.toString();
      }
   }

   /**
    * Get the string data.
    * @param val the specified value.
    * @return the string data.
    */
   public static String getStringData(Object val) {
      if(val instanceof String) {
         return (String) val;
      }
      else if(val == null) {
         return null;
      }
      else {
         return val instanceof Number ? Tool.toString(val) : val.toString();
      }
   }

   /**
    * Get the boolean data.
    * @param val the specified value.
    * @return the boolean data.
    */
   public static Boolean getBooleanData(Object val) {
      if(val == null || val instanceof String && ((String) val).isEmpty()) {
         return null;
      }
      else if(val instanceof Boolean) {
         return (Boolean) val;
      }
      else if(val instanceof String || val instanceof Character ||
         val instanceof Integer || val instanceof Short ||
         val instanceof Double || val instanceof Float)
      {
         Object bl = getData(BOOLEAN, val.toString());

         if(bl instanceof Boolean) {
            return (Boolean) bl;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the byte data.
    * @param val the specified value.
    * @return the byte data.
    */
   public static Byte getByteData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Byte) {
         return (Byte) val;
      }
      else if(val instanceof String) {
         Object bt = getData(BYTE, (String) val);

         if(bt instanceof Byte) {
            return (Byte) bt;
         }
         else {
            return null;
         }
      }
      else if(val instanceof Number) {
         return ((Number) val).byteValue();
      }
      else {
         return null;
      }
   }

   /**
    * Get the char data.
    * @param val the specified value.
    * @return the char data.
    */
   public static String getCharData(Object val) {
      return getStringData(val);
   }

   /**
    * Get the character data.
    * @param val the specified value.
    * @return the character data.
    */
   public static Character getCharacterData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Character) {
         return (Character) val;
      }
      else if(val instanceof String) {
         Object ch = getData(CHARACTER, (String) val);

         if(ch instanceof Character) {
            return (Character) ch;
         }
         else {
            return null;
         }
      }
      else if(val instanceof Number) {
         String text = val.toString();
         return text.charAt(0);
      }
      else {
         return null;
      }
   }

   /**
    * Get the double data.
    * @param val the specified value.
    * @return the double data.
    */
   public static Double getDoubleData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Double) {
         return (Double) val;
      }
      else if(val instanceof Number) {
         return ((Number) val).doubleValue();
      }
      else if(val instanceof Character) {
         return Double.valueOf((Character) val);
      }
      else if(val instanceof String) {
         Object db = getData(DOUBLE, (String) val);

         if(db instanceof Double) {
            return (Double) db;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the float data.
    * @param val the specified value.
    * @return the float data.
    */
   public static Float getFloatData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Float) {
         return (Float) val;
      }
      else if(val instanceof Number) {
         return ((Number) val).floatValue();
      }
      else if(val instanceof Character) {
         return Float.valueOf((Character) val);
      }
      else if(val instanceof String) {
         Object fl = getData(FLOAT, (String) val);

         if(fl instanceof Float) {
            return (Float) fl;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the integer data.
    * @param val the specified value.
    * @return the integer data.
    */
   public static Integer getIntegerData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Integer) {
         return (Integer) val;
      }
      else if(val instanceof Number) {
         return ((Number) val).intValue();
      }
      else if(val instanceof Character) {
         return Integer.valueOf((Character) val);
      }
      else if(val instanceof String) {
         Object inte = getData(INTEGER, (String) val);

         if(inte instanceof Integer) {
            return (Integer) inte;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the long data.
    * @param val the specified value.
    * @return the long data.
    */
   public static Long getLongData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Long) {
         return (Long) val;
      }
      else if(val instanceof Number) {
         return ((Number) val).longValue();
      }
      else if(val instanceof Character) {
         return Long.valueOf((Character) val);
      }
      else if(val instanceof String) {
         Object lon = getData(LONG, (String) val);

         if(lon instanceof Long) {
            return (Long) lon;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the short data.
    * @param val the specified value.
    * @return the sort data.
    */
   public static Short getShortData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof Short) {
         return (Short) val;
      }
      else if(val instanceof Number) {
         return ((Number) val).shortValue();
      }
      else if(val instanceof Character) {
         int ival = (Character) val;
         return (short) ival;
      }
      else if(val instanceof String) {
         Object st = getData(SHORT, (String) val);

         if(st instanceof Short) {
            return (Short) st;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the date data.
    * @param val the specified value.
    * @return the date data.
    */
   @SuppressWarnings("deprecation")
   public static java.sql.Date getDateData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof java.sql.Date) {
         return (java.sql.Date) val;
      }
      else if(val instanceof Date) {
         Date date = (Date) val;
         date = new Date(date.getTime());
         date.setHours(0);
         date.setMinutes(0);
         date.setSeconds(0);
         return new java.sql.Date(date.getTime());
      }
      else if(val instanceof Number) {
         return new java.sql.Date(((Number) val).longValue());
      }
      else if(val instanceof String) {
         Object date = getData(DATE, (String) val);

         if(date instanceof java.sql.Date) {
            return (java.sql.Date) date;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the time instant data.
    * @param val the specified value.
    * @return the time instance data.
    */
   public static java.sql.Timestamp getTimeInstantData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof java.sql.Timestamp) {
         return (java.sql.Timestamp) val;
      }
      else if(val instanceof Date) {
         return new java.sql.Timestamp(((Date) val).getTime());
      }
      else if(val instanceof Number) {
         return new java.sql.Timestamp(((Number) val).longValue());
      }
      else if(val instanceof String) {
         Object date = getData(TIME_INSTANT, (String) val);

         if(date instanceof java.sql.Timestamp) {
            return (java.sql.Timestamp) date;
         }
         else {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the time data.
    * @param val the specified value.
    * @return the time data.
    */
   public static java.sql.Time getTimeData(Object val) {
      if(val == null) {
         return null;
      }
      else if(val instanceof java.sql.Time) {
         return (java.sql.Time) val;
      }
      else if(val instanceof Date) {
         return new java.sql.Time(((Date) val).getTime());
      }
      else if(val instanceof String) {
         Object time = getData(TIME, (String) val);

         if(time instanceof java.sql.Time) {
            return (java.sql.Time) time;
         }
         else {
            return null;
         }
      }
      else if(isNumberClass(val.getClass())) {
         try {
            return new java.sql.Time(Long.parseLong(val.toString()));
         }
         catch(Exception e) {
            return null;
         }
      }
      else {
         return null;
      }
   }

   /**
    * Get the color data.
    * @param val the specified value.
    * @return the time data.
    */
   public static Color getColorData(Object val) {
      if(val == null || "".equals(val)) {
         return null;
      }
      else if(val instanceof Color) {
         return (Color) val;
      }
      else if(val instanceof Number) {
         return new Color(((Number) val).intValue());
      }
      else if(val instanceof String) {
         try {
            int ival = Integer.decode((String) val);
            return new Color(ival);
         }
         catch(Exception ex) {
            // try as constant
            try {
               Field field = Color.class.getField((String) val);
               return (Color) field.get(null);
            }
            catch(Exception ex2) {
               LOG.debug("Invalid color value: " + val, ex2);
            }
         }
      }
      else if(val instanceof Object[]) {
         Object[] arr = (Object[]) val;

         if(arr.length > 0 && arr.length <= 3) {
            Integer v1 = getIntegerData(arr[0]);
            Integer v2 = (arr.length > 1) ? getIntegerData(arr[1]) : (Integer) 0;
            Integer v3 = (arr.length > 2) ? getIntegerData(arr[2]) : (Integer) 0;

            if(v1 != null && v2 != null && v3 != null) {
               return new Color(v1, v2, v3);
            }
         }
      }

      return null;
   }

   /**
    * Determines if a string is likely a date.
    *
    * @param s the string to check.
    *
    * @return {@code true} if a date or {@code false} if not.
    */
   public static boolean isDate(String s) {
      if(s == null || s.isEmpty() ||
         !Character.isDigit(s.charAt(0)) && !Character.isDigit(s.charAt(s.length() - 1)))
      {
         return false;
      }

      return matchesDateFormat(s, DAY_MONTH_YEAR_TIME, 2) ||
         matchesDateFormat(s, MONTH_DAY_YEAR_TIME, 2) ||
         matchesDateFormat(s, YEAR_MONTH_DAY_TIME, 4);
   }

   public static boolean isDateWithoutTime(String s) {
      if(s == null || s.isEmpty() ||
         !Character.isDigit(s.charAt(0)) && !Character.isDigit(s.charAt(s.length() - 1)))
      {
         return false;
      }

      return matchesDateFormat(s, DAY_MONTH_YEAR, 2) ||
         matchesDateFormat(s, MONTH_DAY_YEAR, 2) ||
         matchesDateFormat(s, YEAR_MONTH_DAY, 4);
   }

   private static boolean matchesDateFormat(String input, Pattern pattern, int monthGroupIdx) {
      final Matcher matcher = pattern.matcher(input);

      if(matcher.matches()) {
         // Matches the month identifier group. Null if the month is a number.
         final String monthGroup = matcher.group(monthGroupIdx);

         // Handling month identifier validity in a regex is not feasible, so instead rely on DateTimeConfig's monthMap.
         return monthGroup == null || DateTimeConfig.getGlobalDefault().lookupMonthIndex(monthGroup) != null;
      }
      else {
         return false;
      }
   }
   public static boolean isTime(String s) {
      if(s == null || s.isEmpty() || !s.contains(":")) {
         return false;
      }

      return TIME_PATTERN.matcher(s).matches();
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public static Date parseDate(String val) throws ParseException {
      return parseDate(val, null);
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public static Date parseDate(String val, Boolean isDmyOrder) throws ParseException {
      try {
         if(val.equals("1900-01-01")) {
            return new SimpleDateFormat("yyyy-MM-dd").parse("1900-01-01");
         }
         else if(Pattern.matches("^\\d\\d\\d\\d$", val)) {
            return new SimpleDateFormat("yyyy").parse(val);
         }

         return (Date) DATE_FORMAT_CACHE.parse(val);
      }
      catch(ParseException ex) {
         if(val != null && val.length() > 0 && val.charAt(0) == '{') {
            return dateFmt.get().parse(val);
         }

         try {
            return Tool.getDateFormat().parse(val);
         }
         catch(Exception ex2) {
            // ignore
         }

         try {
            return DateTime.parse(val, getDateTimeConfig(isDmyOrder)).toDate();
         }
         catch(Exception ex2) {
            throw new ParseException(ex2.getMessage(), 0);
         }
      }
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public static Date parseDateTime(String val) throws ParseException {
      try {
         return parseDateTimeWithDefaultFormat(val);
      }
      catch(ParseException ex) {
         try {
            return DateTime.parse(val, getDateTimeConfig()).toDate();
         }
         catch(Exception ex2) {
            throw new ParseException(ex2.getMessage(), 0);
         }
      }
   }

   public static Date parseDateTimeWithDefaultFormat(String val) throws ParseException {
      try {
         return (Date) DATETIME_FORMAT_CACHE.parse(val);
      }
      catch(ParseException ex) {
         if(val != null && val.length() > 0 && val.charAt(0) == '{') {
            return timeInstantFmt.get().parse(val);
         }

         return Tool.getDateTimeFormat().parse(val);
      }
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public static Date parseTime(String val) throws ParseException {
      try {
         return (Date) TIME_FORMAT_CACHE.parse(val);
      }
      catch(ParseException ex) {
         if(val != null && val.length() > 0 && val.charAt(0) == '{') {
            return timeFmt.get().parse(val);
         }

         try {
            return Tool.getTimeFormat().parse(val);
         }
         catch(Exception ex2) {
            // ignore
         }

         IDateTimeConfig config = getDateTimeConfig();

         try {
            return DateTime.parse(val, config).toDate();
         }
         catch(Exception ex2) {
            throw new ParseException(ex2.getMessage(), 0);
         }
      }
   }

   /**
    * Create date time config by current client locale.
    */
   public static IDateTimeConfig getDateTimeConfig() {
      return getDateTimeConfig(null);
   }

   /**
    * Create date time config by current client locale.
    *
    * @param isDmyOrder true if day is before month, else month is before day.
    */
   public static IDateTimeConfig getDateTimeConfig(Boolean isDmyOrder) {
      Locale locale = Locale.getDefault();
      Principal principal = ThreadContext.getPrincipal();

      if(principal instanceof SRPrincipal) {
         ClientInfo clientInfo = ((SRPrincipal) principal).getUser();
         locale = clientInfo.getLocale();
      }

      if(locale == null) {
         locale = Locale.getDefault();
      }

      String key = locale + "_" + isDmyOrder;
      IDateTimeConfig config = localeToConfig.get(key);

      if(config != null) {
         return config;
      }

      DateTimeConfigBuilder builder = DateTimeConfigBuilder.newInstance();
      TimeZone tz = TimeZone.getDefault();
      builder.addTzMap("Z", "UTC");
      builder.addTimeZone(tz.getID(), tz);
      builder.setLocale(locale);
      ArrayList<Locale> locales = new ArrayList<>();
      locales.add(locale);

      if(!Tool.equals(locale, Locale.ENGLISH)) {
         locales.add(Locale.ENGLISH);
      }

      builder.setMonthMap(getMonthMap(locales));

      if(isDmyOrder != null) {
         builder.setDmyOrder(isDmyOrder);
      }
      else {
         DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);

         if(df instanceof SimpleDateFormat) {
            String pattern = ((SimpleDateFormat) df).toPattern();
            builder.setDmyOrder(!pattern.startsWith("M") && !pattern.startsWith("y"));
         }
      }

      config = DateTimeConfig.fromBuilder(builder);
      localeToConfig = ImmutableMap.<String, IDateTimeConfig>builder()
         .putAll(localeToConfig)
         .put(key, config)
         .build();
      return config;
   }

   public static MonthMap getMonthMap(List<Locale> localeList) {
      MonthMap newMonthMap = new MonthMap();

      for(Locale locale : localeList) {
         DateFormatSymbols dfs = DateFormatSymbols.getInstance(locale);
         String[] longMonths = dfs.getMonths();
         String[] shortMonths = dfs.getShortMonths();

         for (int i = 0; i < 12; i++) {
            String shortMonth = shortMonths[i].toUpperCase();
            String shortMonth4 = shortMonths[i].toUpperCase().substring(0, Math.min(4, shortMonths[i].length()));
            String longMonth = longMonths[i].toUpperCase();
            if (longMonth.startsWith(shortMonth4) || shortMonth4.endsWith(".")) {
               // If a truncated month name matches its 4-char abbrev, we'll use it.
               // The goal is to support both standard and non-standard abbreviations like "Octob".
               newMonthMap.addMonth(shortMonth4, i);
               // Finland's 11th month starts with "MAR" which collides with EN on 3-char abbrevs.
               // Except for that, we'll recognize 3-char abbreviations
               if (shortMonth4.length() == 4 && !locale.toString().startsWith("fi")) {
                  newMonthMap.addMonth(shortMonth.substring(0, 3), i);
               }
            } else {
               // Otherwise, we'll strictly match the abbreviation and full name
               newMonthMap.addMonth(shortMonth, i);
               newMonthMap.addMonth(longMonth, i);
            }
         }
      }

      return newMonthMap;
   }

   public static boolean hasTimePart(Date date) {
      if(date == null) {
         return false;
      }

      Calendar calendar = new GregorianCalendar();
      calendar.setTime(date);

      return calendar.get(Calendar.HOUR_OF_DAY) > 0 || calendar.get(Calendar.MINUTE) > 0 ||
         calendar.get(Calendar.SECOND) > 0;
   }

   /**
    * Check whether the email format.
    * @param email email address
    */
   public static boolean matchEmail(String email) {
      String emailRegex = "^[\\w\\d\\-\\+_]+(\\.[\\w\\d\\-\\+_]+)*@[\\w\\d\\-\\+_]+(\\.[\\w\\d\\-\\+_]+)*$";
      return Pattern.compile(emailRegex).matcher(email.trim()).matches();
   }

   /**
    * A utility method that splits a delimited string into an array of Strings.
    * @param str the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    */
   public static String[] split(String str, char delim) {
      return split(str, delim, -1);
   }

   /**
    * A utility method that splits a delimited string into an array of Strings.
    * @param str the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    * @param count the number of substrings to be returned.
    */
   public static String[] split(String str, char delim, int count) {
      if(str == null || str.length() == 0) {
         return new String[0];
      }

      List<String> v = new ArrayList<>();
      int pos;
      boolean breakOnCount = false;

      while((pos = str.indexOf(delim)) >= 0) {
         v.add(str.substring(0, pos));
         str = str.substring(pos + 1);

         if(count > 0) { // -1 indicates optional parameter was not specified
            count--;

            if(count <= 0) {
               breakOnCount = true;
               break;
            }
         }
      }

      if(!breakOnCount) {
         v.add(str);
      }

      return v.toArray(new String[0]);
   }

   /**
    * A utility method that splits a delimited string into an array of Strings.
    * @param str the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    */
   public static String[] split(String str, String delim, int count) {
      if(str == null || str.length() == 0) {
         return new String[0];
      }

      List<String> v = new ArrayList<>();
      // make sure the first field is not empty, otherwise ignored by token
      StringTokenizer tok = new StringTokenizer("X" + str, delim);

      while(tok.hasMoreTokens()) {
         v.add(tok.nextToken());

         if(count > 0) { // -1 indicates optional parameter was not specified
            count--;

            if(count <= 0) {
               break;
            }
         }
      }

      String[] strs = v.toArray(new String[0]);

      // remove the extra 'X'
      strs[0] = strs[0].substring(1);

      return strs;
   }

   /**
    * A utility method that splits a delimited string into an array of Strings.
    * @param str the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    * @param isTokenSplit specifies if the a String Tokenizer should be used
    * to split the String, in which case each charactor in the string can be
    * a delimiter. Otherwise the entire string is used as ONE delimiter.
    */
   public static String[] split(String str, String delim, boolean isTokenSplit) {
      if(isTokenSplit) {
         return split(str, delim, -1);
      }

      if(str == null || str.length() == 0) {
         return new String[] {};
      }

      List<String> v = new ArrayList<>();
      int pos;

      while((pos = str.indexOf(delim)) >= 0) {
         v.add(str.substring(0, pos));
         str = str.substring(pos + delim.length());
      }

      v.add(str);

      return v.toArray(new String[0]);
   }

   /**
    * Get a Toolkit instance.
    */
   public static Toolkit getToolkit() {
      if(toolkit == null) {
         toolkit = Toolkit.getDefaultToolkit();
      }

      return toolkit;
   }

   /**
    * Replace the first occurance of a string.
    * @param str the original String.
    * @param old the string to be replaced.
    * @param news the string which will replace the old string.
    * @return a new String that has the first occurence of old replaced with
    * news.
    */
   public static String replace(String str, String old, String news) {
      if(str == null) {
         return str;
      }

      int idx = 0;

      if((idx = str.indexOf(old, idx)) >= 0) {
         str = str.substring(0, idx) + news + str.substring(idx + old.length());
      }

      return str;
   }

   /**
    * Replace all occurance of a string.
    * @param str the original String.
    * @param old the string to be replaced.
    * @param news the string which will replace the old string.
    * @return a new String that has the first occurence of old replaced with
    * news.
    */
   public static String replaceAll(String str, String old, String news) {
      if(str == null) {
         return str;
      }

      int begin = 0;
      int idx;
      int len = old.length();
      StringBuilder buf = new StringBuilder();

      while((idx = str.indexOf(old, begin)) >= 0) {
         buf.append(str, begin, idx);
         buf.append(news);
         begin = idx + len;
      }

      buf.append(str.substring(begin));
      return buf.toString();
   }

   // @by stevenkuo feature1413407457765 2014/12/11
   // uses the property sree.collator to instantiate a custom collator depending on
   // the user's needs
   private static boolean hasCollator = false;
   private static Collator getCollator() {
      if (!hasCollator) {
         String collatorName;

         if((collatorName = SreeEnv.getProperty("sree.collator")) != null) {
            try {
               collator = (Collator) Class.forName(collatorName).getConstructor().newInstance();
            }
            catch(Exception e) {
               //Collator does not exist, collator stays equal to null
            }
         }
         else {
            collator = Locale.getDefault().getLanguage().equals("en") ? null :
                    Collator_CN.getCollator();
         }

         hasCollator = true;
      }
      return collator;
   }

   /**
    * Compare two values and return >0, 0, or <0 for greater than, equal
    * to, and less than conditions.
    *
    * @param caseSensitive <code>true</code> if the comparison should be case
    *                      sensitive for strings.
    * @param parseNum true to parse a string to compare with a number. this can potentially
    * cause sorting error and should be turned off in that case.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public static int compare(Object v1, Object v2, boolean caseSensitive, boolean parseNum) {
      if(v1 == null || v2 == null) {
         return (v1 == null && v2 == null) ? 0 : ((v1 == null) ? -1 : 1);
      }

      if(!caseSensitive && v1 instanceof String && v2 instanceof String) {
         if(getCollator() != null) {
            try {
               String str1 = ((String) v1).toLowerCase();
               String str2 = ((String) v2).toLowerCase();
               return collator.compare(str1, str2);
            }
            catch(Exception ex) {
               // ignore it
            }
         }
         return ((String) v1).compareToIgnoreCase(((String) v2));
      }

      if(parseNum && v1.getClass() != v2.getClass()) {
         boolean num1 = v1 instanceof Number;
         boolean num2 = v2 instanceof Number;

         if(num1 != num2) {
            try {
               // check if number can be parsed to avoid creating NumberFormatException,
               // which can be quite expensive
               if(!num1) {
                  String str = v1.toString();

                  if(NumberUtils.isParsable(str)) {
                     try {
                        v1 = Double.parseDouble(str);
                     }
                     catch(NumberFormatException ex) {
                        v1 = NumberParserWrapper.getDouble(str);
                     }
                  }
               }

               if(!num2) {
                  String str = v2.toString();

                  if(NumberUtils.isParsable(str)) {
                     try {
                        v2 = Double.parseDouble(str);
                     }
                     catch(NumberFormatException ex) {
                        v2 = NumberParserWrapper.getDouble(str);
                     }
                  }
               }
            }
            catch(Exception ex) {
               // ignore it
            }
         }
      }

      if(v1.getClass() != v2.getClass()) {
         int rank1 = getClassRank(v1);
         int rank2 = getClassRank(v2);

         // sort mixed type: number, date, others
         if(rank1 != rank2) {
            return rank1 - rank2;
         }
      }

      // @by ChrisS bug1405007689729 2014-7-17
      // It really makes no sense to have useful class=class comparisons locked
      // inside the class!=class comparison above.
      if(v1 instanceof Number && v2 instanceof Number) {
         double d1 = ((Number) v1).doubleValue();
         double d2 = ((Number) v2).doubleValue();
         return Double.compare(d1, d2);
      }
      else if(v1 instanceof java.sql.Date && v2 instanceof java.sql.Date) {
         java.sql.Date date1 = (java.sql.Date) v1;
         java.sql.Date date2 = (java.sql.Date) v2;

         return date1.toString().compareToIgnoreCase(date2.toString());
      }
      else if(v1 instanceof java.sql.Time && v2 instanceof java.sql.Time) {
         java.sql.Time time1 = (java.sql.Time) v1;
         java.sql.Time time2 = (java.sql.Time) v2;

         return time1.toString().compareToIgnoreCase(time2.toString());
      }
      else if(v1 instanceof Date && v2 instanceof Date) {
         long d1 = ((Date) v1).getTime();
         long d2 = ((Date) v2).getTime();
         return Long.compare(d1, d2);
      }

      try {
         return ((Comparable) v1).compareTo(v2);
      }
      catch(Exception ex) {
         // ignore it
      }

      if(!caseSensitive) {
         return v1.toString().compareToIgnoreCase(v2.toString());
      }
      else {
         return v1.toString().compareTo(v2.toString());
      }
   }

   private static int getClassRank(Object value) {
      if("".equals(value)) {
         return 0;
      }

      Class<?> clz = value.getClass();

      if(Number.class.isAssignableFrom(clz)) {
         return 1;
      }
      else if(Date.class.isAssignableFrom(clz)) {
         return 2;
      }

      return 3;
   }

   /**
    * Write the image to PNG file.
    */
   public static BufferedImage getBufferedImage(Image img) {
      BufferedImage image;

      if(img instanceof BufferedImage) {
         image = (BufferedImage) img;
      }
      else {
    waitForImage(img);
         int w = img.getWidth(null);
         int h = img.getHeight(null);

         image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

         Graphics g = image.getGraphics();

         g.drawImage(img, 0, 0, null);
         g.dispose();
      }

      return image;
   }

   /**
    * Write the image to PNG file.
    */
   public static void writePNG(Image img, OutputStream output) throws IOException {
      ImageIO.write(getBufferedImage(img), "png", output);
   }

   /**
    * Draw string as glyph shapes.
    */
   public static void drawGlyph(Graphics2D g, String str, double x, double y) {
      if(g instanceof PDFDevice) {
         g.drawString(str, (float) x, (float) -y);
         g.dispose();
         return;
      }

      Graphics2D g2 = (Graphics2D) g.create();
      FontRenderContext fc = new FontRenderContext(new AffineTransform(),
                                                   false, true);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      GlyphVector glyphs = g2.getFont().createGlyphVector(fc, str);
      g2.translate((float) x, (float) y);

      Shape cshape = glyphs.getOutline();
      g2.setStroke(new BasicStroke(0.1f));
      g2.fill(cshape);
      g2.setColor(g2.getColor().brighter().brighter());
      g2.draw(cshape);

      g2.dispose();
   }

   /**
    * Make sure image is fully loaded.
    */
   public static boolean waitForImage(Image image) {
      int id;

      // @by jasons, MediaTracker seems to be misbehaving when used by multiple
      // threads with the same id. use different ids to avoid this problem.
      synchronized(tracker) {
         if(trackerId == Integer.MAX_VALUE) {
            id = trackerId = 0;
         }
         else {
            id = ++trackerId;
         }
      }

      boolean result = true;
      tracker.addImage(image, id);

      try {
         tracker.waitForID(id);

         if(tracker.isErrorID(id)) {
            result = false;
            LOG.warn("An error occurred while loading image");
         }
      }
      catch(Exception e) {
         LOG.warn(
                     "Image loading was interrupted, may be incomplete", e);
      }

      tracker.removeImage(image, id);
      return result;
   }

   /**
    * Copy the data from input stream to output stream. This method
    * reads each byte from an inputstream and writes it to the outputstream.
    * @param input input stream to copy from.
    * @param out output stream to write to.
    */
   public static void copyTo(InputStream input, OutputStream out) throws IOException {
      byte[] buf = new byte[4096];
      int cnt;

      while((cnt = input.read(buf)) > 0) {
         out.write(buf, 0, cnt);
      }

      out.flush();
   }

   /**
    * Parses an XML document.
    *
    * @param input the input stream containing the XML content.
    *
    * @return the parsed document.
    */
   public static Document parseXML(InputStream input)
      throws IOException, ParserConfigurationException
   {
      return parseXML(input, "UTF-8", false);
   }

   /**
    * Parses an XML document.
    *
    * @param input    the input stream containing the XML content.
    * @param encoding the character encoding of the XML content.
    *
    * @return the parsed document.
    */
   public static Document parseXML(InputStream input, String encoding)
      throws IOException, ParserConfigurationException
   {
      return parseXML(input, encoding, false);
   }

   /**
    * Parses an XML document.
    *
    * @param input                 the input stream containing the XML content.
    * @param encoding              the character encoding of the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    *
    * @return the parsed document.
    */
   public static Document parseXML(InputStream input, String encoding,
                                   boolean allowExternalEntities)
      throws IOException, ParserConfigurationException
   {
      BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
      return parseXML(reader, allowExternalEntities);
   }

   /**
    * Parses an XML document.
    *
    * @param input                 the input stream containing the XML content.
    * @param encoding              the character encoding of the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    * @param isCoalescing The property that requires the parser to coalesce adjacent
    *                     character data sections.
    *                     default: <tt>true</tt>
    * @return the parsed document.
    */
   public static Document parseXML(InputStream input, String encoding,
                                   boolean allowExternalEntities,
                                   Boolean isCoalescing)
      throws IOException, ParserConfigurationException
   {
      BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
      return parseXML(reader, allowExternalEntities, isCoalescing);
   }

   /**
    * Parses an XML document.
    *
    * @param reader the reader containing the XML content.
    *
    * @return the parsed document.
    */
   public static Document parseXML(Reader reader)
      throws ParserConfigurationException
   {
      return parseXMLByStAX(reader, false);
   }

   /**
    * Parses an XML document.
    *
    * @param reader                the reader containing the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    *
    * @return the parsed document.
    */
   public static Document parseXML(Reader reader, boolean allowExternalEntities)
      throws ParserConfigurationException
   {
      return parseXMLByStAX(reader, allowExternalEntities);
   }

   /**
    * Parses an XML document.
    *
    * @param reader                the reader containing the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    *
    * @return the parsed document.
    */
   public static Document parseXML(Reader reader, boolean allowExternalEntities, Boolean isCoalescing)
      throws ParserConfigurationException
   {
      return parseXMLByStAX(reader, allowExternalEntities, isCoalescing);
   }

   /**
    * Parse xml, use StAX to enumerate the whole xml and create a DOM tree,
    * for the CDATA section, if it is a large data, a swappable CDATA
    * will be created, otherwise a normal CDATA section will be created.
    *
    * @param inputReader the reader containing the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    *
    * @return the parsed document.
    */
   private static Document parseXMLByStAX(Reader inputReader,
                                          boolean allowExternalEntities)
      throws ParserConfigurationException
   {
      return parseXMLByStAX(inputReader, allowExternalEntities, null);
   }

   /**
    * Parse xml, use StAX to enumerate the whole xml and create a DOM tree,
    * for the CDATA section, if it is a large data, a swappable CDATA
    * will be created, otherwise a normal CDATA section will be created.
    *
    * @param inputReader the reader containing the XML content.
    * @param allowExternalEntities <tt>true</tt> to include external entities;
    *                              <tt>false</tt> otherwise.
    * @param isCoalescing The property that requires the parser to coalesce adjacent
    *                     character data sections.
    *                     default: <tt>true</tt>
    * @return the parsed document.
    */
   private static Document parseXMLByStAX(Reader inputReader,
                                          boolean allowExternalEntities,
                                          Boolean isCoalescing)
      throws ParserConfigurationException
   {
      XMLStreamReader reader;
      Document document;

      synchronized(Tool.class) {
         if(ifactory == null) {
            ifactory = XMLInputFactory.newInstance();

            try {
               ifactory.setProperty(
                  "http://java.sun.com/xml/stream/properties/report-cdata-event",
                  Boolean.TRUE);

               if(ifactory.isPropertySupported("report-cdata-event")) {
                  ifactory.setProperty("report-cdata-event", Boolean.TRUE);
               }
            }
            catch(IllegalArgumentException exc) {
               LOG.debug("Unsupported parser property", exc);
            }
         }

         // @by yuz, save resource about creating DocumentBuilder
         if(builder == null) {
            DocumentBuilderFactory bfactory = DocumentBuilderFactory.newInstance();
            builder = bfactory.newDocumentBuilder();
         }
         else {
            builder.reset();
         }
      }

      boolean originalCoalescing = (boolean) ifactory.getProperty(XMLInputFactory.IS_COALESCING);

      try {
         synchronized(ifactory) {
            ifactory.setProperty(
               XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
               allowExternalEntities);

            if(isCoalescing != null) {
               ifactory.setProperty(XMLInputFactory.IS_COALESCING, isCoalescing);
            }

            ifactory.setProperty(XMLInputFactory.SUPPORT_DTD, allowExternalEntities);
            reader = ifactory.createXMLStreamReader(inputReader);
         }

         Node cnode = document = builder.newDocument();
         int event;
         String ctext;
         Element element;
         int startPrefix = 0;
         StringBuilder charBuffer = null;

         while(reader.hasNext()) {
            event = reader.next();

            if(event != XMLStreamConstants.CHARACTERS && charBuffer != null) {
               appendChildNode( cnode, document.createTextNode(charBuffer.toString()), startPrefix);
               charBuffer = null;
            }

            switch(event) {
            case XMLStreamConstants.CDATA:
               ctext = reader.getText();
               appendChildNode(cnode, document.createCDATASection(ctext), startPrefix);
               break;
            case XMLStreamConstants.CHARACTERS:
               ctext = reader.getText();

               if(charBuffer == null) {
                  charBuffer = new StringBuilder();
               }

               if(ctext != null) {
                  charBuffer.append(ctext);
               }

               break;
            case XMLStreamConstants.COMMENT:
               appendChildNode(cnode, document.createComment(reader.getText()),
                  startPrefix);
               break;
            case XMLStreamConstants.DTD:
               break;
            case XMLStreamConstants.END_DOCUMENT:
               break;
            case XMLStreamConstants.END_ELEMENT:
               if(startPrefix == 0) {
                  cnode = cnode.getParentNode();
               }
               else {
                  startPrefix--;
               }

               break;
            case XMLStreamConstants.ENTITY_DECLARATION:
               break;
            case XMLStreamConstants.ENTITY_REFERENCE:
               appendChildNode(
                  cnode, document.createEntityReference(reader.getText()),
                  startPrefix);
               break;
            case XMLStreamConstants.NAMESPACE:
               break;
            case XMLStreamConstants.NOTATION_DECLARATION:
               break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
               appendChildNode(cnode, document.createProcessingInstruction(
                                  reader.getPITarget(), reader.getPIData()), startPrefix);
               break;
            case XMLStreamConstants.SPACE:
               break;
            case XMLStreamConstants.START_DOCUMENT:
               break;
            case XMLStreamConstants.START_ELEMENT:
               if(reader.getPrefix() == null || "".equals(reader.getPrefix())) {
                  element = document.createElement(reader.getLocalName());
               }
               else {
                  element = document.createElementNS(reader.getNamespaceURI(),
                     reader.getPrefix() + ":" + reader.getLocalName());
               }

               for(int i = 0; i < reader.getAttributeCount(); i++) {
                  String ns = reader.getAttributeNamespace(i);

                  if(ns == null || ns.isEmpty()) {
                     element.setAttribute(reader.getAttributeLocalName(i),
                                          reader.getAttributeValue(i));
                  }
                  else {
                     element.setAttributeNS(reader.getAttributeNamespace(i),
                        reader.getAttributePrefix(i) + ":"
                        + reader.getAttributeLocalName(i),
                        reader.getAttributeValue(i));
                  }
               }

               if(appendChildNode(cnode, element, startPrefix)) {
                  cnode = element;
               }
               else {
                  startPrefix++;
               }

               break;
            }
         }

         return document;
      }
      catch(XMLStreamException e) {
         throw new RuntimeException(e);
      }
      finally {
         // Reset property. The properties of a class variable cannot be changed by a single change.
         if(isCoalescing != null) {
            ifactory.setProperty(XMLInputFactory.IS_COALESCING, originalCoalescing);
         }
      }
   }

   /**
    * Safely parseXML with SAXParser
    * https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet
    * Calling code must cleanup input stream
    */
   public static void safeParseXMLBySAX(InputStream input, DefaultHandler handler)
      throws SAXException, ParserConfigurationException, IOException
   {
      if(saxParserFactory == null) {
         synchronized(CoreTool.class) {
            if(saxParserFactory == null) {
               saxParserFactory = SAXParserFactory.newInstance();

               try {
                  saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                  saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                  saxParserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                  saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                  saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
               }
               catch(Exception saxException) {
                  LOG.debug("Unable to create Safe SAXParser", saxException);
               }
            }
         }
      }

      saxParserFactory.newSAXParser().parse(input, handler);
   }

   /**
    * Safely parseXML using DocumentBuilder
    * https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet
    * Calling code must cleanup input stream
    */
   public static Document safeParseXMLByDocumentBuilder(InputStream input)
      throws SAXException, IOException, ParserConfigurationException
   {
      if(docFactory == null) {
         synchronized(CoreTool.class) {
            if(docFactory == null) {
               docFactory = DocumentBuilderFactory.newInstance();

               try {
                  docFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                  docFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                  docFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                  docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                  docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
               }
               catch(ParserConfigurationException parserException) {
                  LOG.debug("Unable to create Safe Document Builder", parserException);
               }
            }
         }
      }

      DocumentBuilder builder = docFactory.newDocumentBuilder();
      BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      return builder.parse(new InputSource(br));
   }

   /**
    * Append child nodes.
    */
   private static boolean appendChildNode(Node pnode, Node node, int starts) {
      if(starts > 0) {
         return false;
      }

      pnode.appendChild(node);
      return true;
   }

   public static Element getFirstElement(Document doc) {
      NodeList list = doc == null ? null : doc.getChildNodes();

      if(list == null || list.getLength() == 0) {
         return null;
      }

      for(int i = 0; i < list.getLength(); i++) {
         Node node = list.item(i);

         if(node.getNodeType() != Node.COMMENT_NODE) {
            return (Element) node;
         }
      }

      return null;
   }

   /**
    * Get the String value of the element. This method returns a null
    * if the element does not contain a value.
    */
   public static String getValue(Node elem) {
      return getValue(elem, false);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object.
    */
   public static String getValue(Node elem, boolean multiline) {
      return getValue(elem, multiline, true);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object.
    * @param trim true if trim newlines.
    */
   public static String getValue(Node elem, boolean multiline, boolean trim) {
      return getValue(elem, multiline, trim, false);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object.
    * @param trim true if trim newlines.
    * @param keepSpace true if keep blank spaces in a line.
    */
   public static String getValue(Node elem, boolean multiline, boolean trim, boolean keepSpace) {
      if(elem == null) {
         return null;
      }

      StringBuilder buffer = new StringBuilder();
      NodeList nlist = elem.getChildNodes();
      final int len = nlist.getLength(); // optimize

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         switch(child.getNodeType()) {
         case Element.TEXT_NODE:
            String sval = child.getNodeValue();

            if(!keepSpace && sval.trim().length() == 0) {
               sval = sval.trim();
            }

            // empty string in tag (not in cdata) is ignored
            if(sval.length() > 0) {
               buffer = multiline ? buffer.append(sval) : new StringBuilder(sval);
            }

            break;
         case Element.CDATA_SECTION_NODE:
            String nodeValue = child.getNodeValue();

            if(nodeValue != null) {
               buffer.append(nodeValue);
            }
            break;
         case Element.ENTITY_REFERENCE_NODE:
            String val = buildString("&", child.getNodeName(), ";");
            String eval = decoding.get(val);

            if(eval != null) {
               buffer = multiline ? buffer.append(eval) : new StringBuilder(eval);
            }

            break;
         }
      }

      String val = buffer.toString();
      int start = 0;
      int end = val.length();

      // remove surrounding newlines
      if(trim) {
         start = val.length();
         end = 0;

         for(int i = 0; i < val.length(); i++) {
            if(val.charAt(i) != '\n') {
               start = i;
               break;
            }
         }

         for(int i = val.length() - 1; i >= 0; i--) {
            if(val.charAt(i) != '\n') {
               end = i + 1;
               break;
            }
         }
      }

      return start < end ? val.substring(start, end) : null;
   }

   /**
    * Get a child element by its tag name. If more than one elements have the
    * same tag name, the first one will be returned.
    * @param elem the parent element.
    * @param name the tag name of the child element to retrieve.
    * @return the retrieved child element if exists, null otherwise.
    */
   public static Element getChildNodeByTagName(Node elem, String name) {
      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);

         if((node instanceof Element) && name.equals(node.getNodeName())) {
            return (Element) node;
         }
      }

      return null;
   }

   /**
    * Get the value of the specified child node.
    * @param name the tag name of the child element to retrieve.
    * @param elem the parent element.
    * @return the retrieved child element value if exists, null otherwise.
    */
   public static String getChildValueByTagName(Node elem, String name) {
      Element node = getChildNodeByTagName(elem, name);
      return (node != null) ? getValue(node) : null;
   }

   /**
    * Parses an object from the specified child node.
    *
    * @param parent   the parent element.
    * @param name     the name of the child element.
    * @param supplier the supplier for the instance of the returned object.
    *
    * @param <T> the type of the returned object.
    *
    * @return the object parsed from the matching element or {@code null} if no element matches.
    *
    * @throws Exception if a parsing error occurs.
    */
   public static <T extends XMLSerializable> T getChildObjectByTagName(Node parent, String name,
                                                                       Supplier<T> supplier)
      throws Exception
   {
      Element node = getChildNodeByTagName(parent, name);

      if(node == null) {
         return null;
      }

      T object = supplier.get();
      object.parseXML(node);
      return object;
   }

   /**
    * Get all the children of the element that has name as its tag name.
    * @param elem the parent element.
    * @param name the tag name of the child node to retrieve.
    * @return a NodeList of child nodes.
    */
   public static NodeList getChildNodesByTagName(Node elem, String name) {
      return getChildNodesByTagName(elem, name, false);
   }

   /**
    * Get all the children of the element that has name as its tag name.
    * @param elem the parent element.
    * @param name the tag name of the child node to retrieve.
    * @param recursive find childs recursively if <tt>ture</tt>.
    *
    * @return a NodeList of child nodes.
    *
    */
   public static NodeList getChildNodesByTagName(Node elem, String name, boolean recursive) {
      if(elem == null) {
         return new NodeListImpl(0);
      }

      NodeList nlist = elem.getChildNodes();
      final int len = nlist.getLength();

      if(len > 0) {
         NodeListImpl children = new NodeListImpl(len);

         for(int i = 0; i < len; i++) {
            if(nlist.item(i) instanceof Element) {
               // remove instanceof is slightly faster
               Element node = (Element) nlist.item(i);

               if(node.getTagName().equals(name)) {
                  children.addItem(node);
               }

               if(recursive) {
                  getAllChildNodes(node, children, name);
               }
            }
         }

         return children;
      }

      return nlist;
   }

   /**
    * Get child nodes recursively.
    */
   private static void getAllChildNodes(Node elem, NodeListImpl child, String name) {
      NodeList list = elem.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(list.item(i) instanceof Element) {
            Element node = (Element) list.item(i);

            if(node.getTagName().equals(name)) {
               child.addItem(node);
            }

            getAllChildNodes(node, child, name);
         }
      }
   }

   /**
    * Get the first node of an xml element.
    * @param elem the specified xml element.
    * @return the first node of the xml element.
    */
   public static Element getFirstChildNode(Element elem) {
      NodeList nodes = elem.getChildNodes();

      for(int i = 0; i < nodes.getLength(); i++) {
         if(!(nodes.item(i) instanceof Element)) {
            continue;
         }

         return (Element) nodes.item(i);
      }

      return null;
   }

   /**
    * Get the nth child node of an xml element.
    * @param elem the specified xml element.
    * @param n the specified nth.
    * @return the nth child node of the xml element.
    */
   public static Element getNthChildNode(Element elem, int n) {
      NodeList nodes = elem.getChildNodes();
      int counter = -1;

      for(int i = 0; i < nodes.getLength(); i++) {
         if(!(nodes.item(i) instanceof Element)) {
            continue;
         }

         counter++;

         if(counter == n) {
            return (Element) nodes.item(i);
         }
      }

      return null;
   }

   /**
    * Get the attribute of an element. If the attribute does not exist this
    * method returns a null value.
    * @param elem the Element that contains the attribute.
    * @param name the name of the Attribute to get.
    */
   public static String getAttribute(Element elem, String name) {
      Attr attr = elem.getAttributeNode(name);
      return (attr == null || attr.getValue().isEmpty()) ? null : attr.getValue();
   }

   /**
    * Validate the given string to xml data. Some characters are not supported
    * as xml text. @see http://www.w3.org/TR/xml/#charsets. Here we will discard
    * the unsupported characters.
    */
   public static String validateToXML(String in) {
      if(in == null) {
         return in;
      }

      int len = in.length();
      StringBuilder out = null;

      for(int i = 0; i < len; i++) {
         char c = in.charAt(i);

         // valid xml character?
         if(c >= 0x20 && c <= 0xD7FF  || c == 0x9 || c == 0xA || c == 0xD ||
            c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)
         {
            if(out != null) {
               out.append(c);
            }
         }
         // invalid xml character? ignore it
         else {
            if(out == null) {
               out = new StringBuilder();

               if(i > 0) {
                  out.append(in, 0, i);
               }
            }
         }
      }

      return out == null ? in : out.toString();
   }

   /**
    * Deep clone a collection.
    * <p>
    * For any value in the collection, if it's an instance of Cloneable, and
    * contains a public clone method, the value will be cloned, otherwise the
    * cloned collection will use the value directly.
    *
    * @param from the to be cloned collection.
    * @return the cloned collection, null means the to be cloned list is null,
    * or exception occurs.
    */
   @SuppressWarnings("unchecked")
   public static <E, T extends Collection<E>> T deepCloneCollection(T from) {
      if(from == null) {
         return null;
      }

      try {
         T to = (T) from.getClass().getConstructor().newInstance();

         for(E val : from) {
            if(val == null) {
               to.add(null);
            }

            else if(val instanceof Cloneable) {
               Method m = getClone(val.getClass());

               if(m == null) {
                  to.add(val);
               }
               else {
                  to.add((E) m.invoke(val));
               }
            }
            else {
               to.add(val);
            }
         }

         return to;
      }
      catch(Exception ex) {
         LOG.error("Failed to deepClone object: " + from, ex);
         return null;
      }
   }

   /**
    * Deep clone a collection.
    * <p>
    * For any value in the collection, if it's an instance of Cloneable, and
    * contains a public clone method, the value will be cloned, otherwise the
    * cloned collection will use the value directly.
    *
    * @param from the to be cloned collection.
    * @return the cloned collection, null means the to be cloned list is null,
    * or exception occurs.
    */
   @SuppressWarnings("unchecked")
   public static <E> List<E> deepCloneSynchronizedList(Collection<E> from, List<E> to) {
      if(from == null) {
         return null;
      }

      try {
         List<E> synList = Collections.synchronizedList(to);

         for(E val : from) {
            if(val == null) {
               synList.add(null);
            }
            else if(val instanceof Cloneable) {
               Method m = getClone(val.getClass());

               if(m == null) {
                  synList.add(val);
               }
               else {
                  synList.add((E) m.invoke(val));
               }
            }
            else {
               synList.add(val);
            }
         }

         return synList;
      }
      catch(ConcurrentModificationException ex) {
         return deepCloneSynchronizedList((Collection<E>) Arrays.asList(from.toArray()), to);
      }
      catch(Exception ex) {
         LOG.error("Failed to deepClone object: " + from, ex);
         return null;
      }
   }

   /**
    * Deep clone a map.
    * <p>
    * keys of the map will not be cloned. For any value, if it's an instance of
    * Cloneable, and contains a public clone method, the value will be cloned,
    * otherwise the cloned map will use the value directly.
    *
    * @param from the to be cloned map.
    * @return the cloned map, null means the to be cloned map is null,
    * or exception occurs.
    */
   @SuppressWarnings("unchecked")
   public static <K, V, M extends Map<K, V>> M deepCloneMap(M from) {
      if(from == null) {
         return null;
      }

      try {
         M to = (M) from.getClass().getConstructor().newInstance();
         copyMap(from, to);

         return to;
      }
      catch(Exception ex) {
         LOG.error("Failed to deep clone map: " + from, ex);
         return null;
      }
   }

   /**
    * Deep clone a map.
    * <p>
    * keys of the map will not be cloned. For any value, if it's an instance of
    * Cloneable, and contains a public clone method, the value will be cloned,
    * otherwise the cloned map will use the value directly.
    *
    * @param from the to be cloned values map.
    * @param to the to be cloned values map.
    */
   public static <K, V> void deepCloneMapValues(Map<K, V> from, Map<K, V> to) {
      if(from == null || to == null) {
         return;
      }

      try {
         copyMap(from, to);
      }
      catch(Exception ex) {
         LOG.error("Failed to deep clone map: " + from, ex);
      }
   }

   @SuppressWarnings("unchecked")
   private static <K, V> void copyMap(Map<K, V> from, Map<K, V> to) throws Exception {
      for(Map.Entry<K, V> entry : from.entrySet()) {
         K key = entry.getKey();
         V val = entry.getValue();

         if(val == null) {
            to.put(key, null);
         }
         else if(val instanceof Cloneable) {
            Method m = getClone(val.getClass());

            if(m == null) {
               to.put(key, val);
            }
            else {
               to.put(key, (V) m.invoke(val));
            }
         }
         else {
            to.put(key, val);
         }
      }
   }

   /**
    * Util clone method.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public static Object clone(Object v) {
      if(v instanceof Collection) {
         return deepCloneCollection((Collection) v);
      }
      else if(v instanceof Map) {
         return deepCloneMap((Map) v);
      }
      else if(v instanceof Object[][]) {
         Object[][] src = (Object[][]) v;

         if(src.length <= 0 || src[0].length <= 0) {
            return src;
         }

         Object[][] target = (Object[][]) Array.newInstance(
            getItemComponentType(src), new int[] { src.length, src[0].length });

         for(int i = 0; i < target.length; i++) {
            target[i] = (Object[]) clone(src[i]);
         }
      }
      else if(v instanceof Object[]) {
         Object[] src = (Object[]) v;

         if(src.length <= 0) {
            return src;
         }

         Object[] target = (Object[]) Array.newInstance(
            getItemComponentType(src), src.length);

         for(int i = 0; i < src.length; i++) {
            target[i] = clone(src[i]);
         }

         return target;
      }
      else if(v instanceof Cloneable) {
         Method cm = getClone(v.getClass());

         try {
            if(cm != null) {
               return cm.invoke(v);
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      return v;
   }

   private static Method getClone(Class<?> cls) {
      String key = cls.getName();
      Object cm = cloneFns.get(key);

      if(cm == NOCLONE) {
         return null;
      }

      try {
         if(cm == null) {
            cm = cls.getMethod("clone");
            cloneFns.put(key, cm);
         }
      }
      catch(Exception ex) {
         cloneFns.put(key, NOCLONE);
      }

      return (Method) cm;
   }

   /**
    * Get the item type of an array or multi-dimensional array.
    */
   private static Class<?> getItemComponentType(Object arr) {
      Class<?> cls = arr.getClass();

      while(cls.isArray()) {
         cls = cls.getComponentType();
      }

      return cls;
   }

   /**
    * Get a unique code for type string, for optimization.
    */
   private static int getTypeCode(String str) {
      char c = Character.toLowerCase(str.charAt(0));

      return str.length() | ((int) c << 8);
   }

   /**
    * Get the user message for this session. The user message is cleared by this
    * call.
    */
   public static UserMessage getUserMessage() {
      UserMessage message = USER_MESSAGE_LOCAL.get().stream()
         .reduce(UserMessage::merge)
         .orElse(null);
      USER_MESSAGE_LOCAL.remove();
      return message;
   }

   public static List<UserMessage> getUserMessages(Type level) {
      List<UserMessage> userMessages = USER_MESSAGE_LOCAL.get().stream()
         .filter(Objects::nonNull)
         .filter(msg -> Objects.equals(level.code(), msg.getLevel()))
         .collect(Collectors.toList());

      return getMergedUserMessages(userMessages);
   }

   /**
    * Get merged UserMessage list. For WS,userMessages under the same assembly will be merged.
    * @param userMessages the specified UserMessage list
    * @return merged UserMessage list
    */
   private static List<UserMessage> getMergedUserMessages(List<UserMessage> userMessages) {
      if(userMessages.size() == 0) {
         return userMessages;
      }

      List<UserMessage> messageList = new ArrayList<>();
      Map<String, UserMessage> messageMap = new HashMap<>();
      UserMessage mergedMessage = null;

      for(UserMessage message : userMessages) {
         if(Tool.isEmptyString(message.getAssemblyName())) {
            int newLevel = mergedMessage == null ? message.getLevel() :
               Math.max(mergedMessage.getLevel(), message.getLevel());
            String newMessage = mergedMessage == null ? message.getMessage() :
               String.format("%s\n%s", mergedMessage.getMessage(), message.getMessage());
            mergedMessage = new UserMessage(newMessage, newLevel);
         }
         else {
            if(messageMap.containsKey(message.getAssemblyName())) {
               UserMessage userMessage = messageMap.get(message.getAssemblyName());
               String newMessage = String.format("%s\n%s", userMessage.getMessage(), message.getMessage());
               UserMessage newUserMessage =
                  new UserMessage(newMessage, userMessage.getLevel(), userMessage.getAssemblyName());
               messageMap.replace(message.getAssemblyName(), newUserMessage);
            }
            else {
               messageMap.put(message.getAssemblyName(), message);
            }
         }
      }

      if(mergedMessage != null) {
         messageList.add(mergedMessage);
      }

      if(messageMap.size() > 0) {
         messageMap.forEach((key, value) -> messageList.add(value));
      }

      return messageList;
   }

   /**
    * Append the user message for this session. The user message is shown to
    * the end user at info level.
    */
   public static void addUserMessage(String msg) {
      addUserMessage(new UserMessage(msg, ConfirmException.INFO));
   }

   /**
    * Append the user message for this session. The user message is shown to
    * the end user as an alert.
    */
   public static void addUserMessage(String msg, int level) {
      addUserMessage(new UserMessage(msg, level));
   }

   /**
    * Append the user message for this session. The user message is shown to
    * the end user at warning level.
    */
   public static void addUserWarning(String msg) {
      addUserMessage(new UserMessage(msg, ConfirmException.WARNING));
   }

   public static void addUserMessage(UserMessage msg) {
      if(msg != null) {
         String assemblyName = USER_MESSAGE_ASSEMBLY_NAME.get();

         if(!Tool.isEmptyString(assemblyName)) {
            msg.setAssemblyName(assemblyName);
         }

         boolean found = existUserMessage(msg);

         if(!found) {
            USER_MESSAGE_LOCAL.get().add(msg);
         }
      }
   }

   public static boolean existUserMessage(String msg) {
      return existUserMessage(new UserMessage(msg, ConfirmException.INFO));
   }

   public static boolean existUserMessage(UserMessage msg) {
      if(msg != null) {
         final List<UserMessage> userMessages = USER_MESSAGE_LOCAL.get();
         boolean found = false;

         for(int i = 0; i < userMessages.size(); i++) {
            final UserMessage userMessage = userMessages.get(i);

            if(Objects.equals(userMessage.getMessage(), msg.getMessage()) &&
               Objects.equals(userMessage.getAssemblyName(), msg.getAssemblyName()))
            {
               found = true;

               if(userMessage.getLevel() < msg.getLevel()) {
                  userMessages.set(i, msg);
               }

               break;
            }
         }

         return found;
      }

      return false;
   }

   /**
    * Clear all user message.
    */
   public static void clearUserMessage() {
      final List<UserMessage> userMessages = USER_MESSAGE_LOCAL.get();

      if(userMessages != null) {
         userMessages.clear();
      }
   }

   /**
    * Get the confirm message.
    */
   public static String getConfirmMessage() {
      return confirmMsg;
   }

   /**
    * Get confirm message.
    */
   public static void addConfirmMessage(String msg) {
      if(confirmMsg == null) {
         confirmMsg = msg;
      }
   }

   /**
    * Set assemblyName for userMessages.
    */
   public static void setUserMessageAssemblyName(String assemblyName) {
      if(!Tool.isEmptyString(assemblyName)) {
         USER_MESSAGE_ASSEMBLY_NAME.set(assemblyName);
      }
   }

   /**
    * Clear assemblyName if used.
    */
   public static void clearUserMessageAssemblyName() {
      USER_MESSAGE_ASSEMBLY_NAME.set(null);
   }

   /**
    * Clear confirm message.
    */
   public static void clearConfirmMessage() {
      confirmMsg = null;
   }

   /**
    * Check if is compact.
    */
   public static boolean isCompact() {
      Boolean compact = slocal.get();
      return compact != null && compact;
   }

   /**
    * Set whether is compact thread.
    */
   public static void setCompact(boolean compact) {
      slocal.set(compact);
   }

   /**
    * Call a method on an object without causing exception if the class or
    * method is not in the jvm (e.g. jdk1.2 methods used in jdk1.1)
    */
   public static Object call(Object obj, String clsname, String method,
                             Class<?>[] params, Object[] args) {
      try {
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         Class<?> cls = loader.loadClass(clsname);
         Method func = cls.getMethod(method, params == null ? new Class[] {} : params);
         return func.invoke(obj, args == null ? new Object[] {} : args);
      }
      catch(ClassNotFoundException e) {
         try {
            if(obj == null || !(obj.getClass().getClassLoader() instanceof Plugin.PluginClassLoader)) {
               return null;
            }

            Class<?> cls = obj.getClass().getClassLoader().loadClass(clsname);
            Method func = cls.getMethod(method, params == null ? new Class[] {} : params);
            return func.invoke(obj, args == null ? new Object[] {} : args);
         }
         catch(Throwable ex) {
            return null;
         }
      }
      catch(Throwable e) {
         return null;
      }
   }

   // date time format cache
   static final FormatCache DATETIME_FORMAT_CACHE =
      new FormatCache(createDateFormat(DEFAULT_DATETIME_PATTERN));
   // time format cache
   static final FormatCache TIME_FORMAT_CACHE =
      new FormatCache(createDateFormat(DEFAULT_TIME_PATTERN));
   // date format cache
   static final FormatCache DATE_FORMAT_CACHE =
      new FormatCache(createDateFormat(DEFAULT_DATE_PATTERN));

   private static Toolkit toolkit = null;
   private static final Component component = new Component() {};
   private static final MediaTracker tracker = new MediaTracker(component);
   private static int trackerId = 0;

   // getTypeCode(NULL)
   private static final int CODE_NULL = 28164;
   // getTypeCode(STRING)
   private static final int CODE_STRING = 29446;
   // getTypeCode(BOOLEAN)
   private static final int CODE_BOOLEAN = 25095;
   // getTypeCode(FLOAT)
   private static final int CODE_FLOAT = 26117;
   // getTypeCode(DOUBLE)
   private static final int CODE_DOUBLE = 25606;
   // getTypeCode(CHAR)
   private static final int CODE_CHAR = 25348;
   // getTypeCode(CHARACTER)
   private static final int CODE_CHARACTER = 25353;
   // getTypeCode(BYTE)
   private static final int CODE_BYTE = 25092;
   // getTypeCode(SHORT)
   private static final int CODE_SHORT = 29445;
   // getTypeCode(INTEGER)
   private static final int CODE_INTEGER = 26887;
   // getTypeCode(LONG)
   private static final int CODE_LONG = 27652;
   // getTypeCode(TIME_INSTANT)
   private static final int CODE_TIME_INSTANT = 29707;
   // getTypeCode(DATE)
   private static final int CODE_DATE = 25604;
   // getTypeCode(TIME)
   private static final int CODE_TIME = 29700;
   // getTypeCode(ARRAY)
   private static final int CODE_ARRAY = 24837;
   // getTypeCode(ENUM)
   private static final int CODE_ENUM = 25860;
   // getTypeCode(COLOR)
   private static final int CODE_COLOR = 25349;

   static {
      ImageIO.setUseCache(false);
   }

   // html encoding mapping
   static final String[][] encodingXML = {
      {"&amp;", "&lt;", "&gt;", "&#39;", "&quot;"},
      {"&", "<", ">", "'", "\""}
   };

   // image data decoding map
   private static final Hashtable<String, String> decoding;

   static {
      decoding = new Hashtable<>();

      for(int i = 0; i < encodingXML[0].length; i++) {
         decoding.put(encodingXML[0][i], encodingXML[1][i]);
      }
   }

   // Cache patterns so that they aren't recompiled every method invocation.
   private static final Pattern DAY_MONTH_YEAR;
   private static final Pattern MONTH_DAY_YEAR;
   private static final Pattern YEAR_MONTH_DAY;
   private static final Pattern DAY_MONTH_YEAR_TIME;
   private static final Pattern MONTH_DAY_YEAR_TIME;
   private static final Pattern YEAR_MONTH_DAY_TIME;

   private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}:\\d{1,2}:\\d{1,2}$");

   static {
      final String timePart =
         "([\\sT]?" + // space or T that delimits the date part from the time part
         "\\d{1,2}:\\d{1,2}" + // H:M, hours:minutes
         "(:\\d{1,2}" + // :s, optional seconds part
         "(.\\d{1,9})?" + // .s, optional decimal seconds part
         "(Z|[+\\-]\\d{2}(:?\\d{2})?" + // optional time zone part, Z or +/-hh:mm (and variants)
         "|(\\s?[aApP]\\.?[mM]\\.?)?)?" + // optional AM/PM part
         ")?" + // everything after minutes optional
         ")?"; // whole time part optional

      final String day = "\\d{1,2}"; // day number
      final String month = "(\\d{1,2}|([a-zA-Z]{3,10}|[\\u4e00-\\u9fa5]{2,3}))"; // Month number or text id.
      final String year = "\\d{2,4}"; // year number

      final String dayMonthSeparator = "[\\s/\\-]"; // space, slash or hyphen
      final String yearSeparator = "([\\s,/\\-]|(,\\s))";// reasonable combination of space, comma, slash, and hyphen

      final String dayMonthYear = day + dayMonthSeparator + month + yearSeparator + year;
      final String monthDayYear = month + dayMonthSeparator + day + yearSeparator + year;
      final String yearMonthDay = year + yearSeparator + month + dayMonthSeparator + day;

      // @by anton, This does not seem like a common-enough pattern to justify testing for it.
//      final String yearDayMonth =
//         year +
//         "[/\\-]" + // year-day separator
//         day +
//         "[/\\-]" + // day-year separator
//         month;

      DAY_MONTH_YEAR = Pattern.compile("^" + dayMonthYear + "$");
      MONTH_DAY_YEAR = Pattern.compile("^" + monthDayYear + "$");
      YEAR_MONTH_DAY = Pattern.compile("^" + yearMonthDay + "$");
      DAY_MONTH_YEAR_TIME = Pattern.compile("^" + dayMonthYear + timePart + "$");
      MONTH_DAY_YEAR_TIME = Pattern.compile("^" + monthDayYear + timePart + "$");
      YEAR_MONTH_DAY_TIME = Pattern.compile("^" + yearMonthDay + timePart + "$");
//      YEAR_DAY_MONTH = Pattern.compile("^" + yearDayMonth + timePart + "$");
   }

   private static class NodeListImpl implements NodeList {
      public NodeListImpl(int length) {
         elems = new Vector<>(length);
      }

      @Override
      public int getLength() {
         return elems.size();
      }

      @Override
      public Node item(int index) {
         return elems.elementAt(index);
      }

      public void addItem(Node node) {
         elems.addElement(node);
      }

      Vector<Node> elems;
   }

   public static final String FAKE_NULL = "__null__";
   private final static ThreadLocal<List<UserMessage>> USER_MESSAGE_LOCAL = ThreadLocal.withInitial(ArrayList::new);
   private final static ThreadLocal<String> USER_MESSAGE_ASSEMBLY_NAME = ThreadLocal.withInitial(String::new);
   private static String confirmMsg = null;
   private static final ThreadLocal<Boolean> slocal = new ThreadLocal<>();
   private static XMLInputFactory ifactory;
   private static volatile DocumentBuilderFactory docFactory;
   private static volatile SAXParserFactory saxParserFactory;
   private static DocumentBuilder builder;
   private static Collator collator = null;
   private static ImmutableMap<String, IDateTimeConfig> localeToConfig = ImmutableMap.of();
   private static final Map<String, Object> cloneFns = new ConcurrentHashMap<>();
   private static final String NOCLONE = "noclone";
}
