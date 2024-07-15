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
package inetsoft.uql.util.filereader;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;

import java.io.*;
import java.text.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for reading text data.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public final class TextUtil {
   /**
    * Creates a new instance of <tt>TextUtil</tt>.
    */
   private TextUtil() {
      // prevent instantiation
   }

   /**
    * Splits a delimited string into array of strings.
    *
    * @param str   the string to split.
    * @param delim the field delimiter.
    *
    * @return the field values.
    */
   public static String[] split(String str, String delim) {
      return split(str, delim, true);
   }

   /**
    * Splits a delimited string into array of strings.
    *
    * @param str          the string to split.
    * @param delim        the field delimiter.
    * @param removeEscape <tt>true</tt> to strip escape characters from the
    *                      values.
    *
    * @return the field values.
    */
   public static String[] split(String str, String delim, boolean removeEscape)
   {
      return split(str, delim, removeEscape, true);
   }

   /**
    * Splits a delimited string into array of strings.
    *
    * @param str          the string to split.
    * @param delim        the field delimiter.
    * @param removeEscape <tt>true</tt> to strip escape characters from the
    *                      values.
    * @param removeQuote  <tt>true</tt> to strip quotation marks from the
    *                     beginning and end of the values.
    *
    * @return the field values.
    */
   public static String[] split(String str, String delim,
                                boolean removeEscape, boolean removeQuote)
   {
      return split(str, delim, removeEscape, removeQuote, false);
   }

   /**
    * Splits a delimited string into array of strings.
    *
    * @param str          the string to split.
    * @param delim        the field delimiter.
    * @param removeEscape <tt>true</tt> to strip escape characters from the
    *                      values.
    * @param removeQuote  <tt>true</tt> to strip quotation marks from the
    *                     beginning and end of the values.
    * @param csvStr        <tt>true</tt> target string is csv line.
    * @return the field values.
    */
   public static String[] split(String str, String delim, boolean removeEscape,
                                boolean removeQuote, boolean csvStr)
   {
      if(str == null || str.length() == 0) {
         return new String[] {};
      }

      ArrayList<String> v = LISTS.get();
      int pos;

      v.clear();

      if(!delim.isEmpty()) {
         boolean tabDelim = delim.equals("\\t");
         boolean handleQuote = removeQuote || csvStr;
         int idx = 0;
         int len = str.length();

         while(idx < len) {
            int charOffset = 0;
            char c0;

            if(handleQuote && ((c0 = str.charAt(idx)) == '\'' || c0 == '"')) {
               pos = Tool.indexOfWithQuote(str, delim, '\\', idx, '\'', '"');
            }
            else if(tabDelim) {
               pos = str.indexOf('\t', idx);
               charOffset = 1;
            }
            else {
               pos = str.indexOf(delim, idx);
            }

            if(pos < 0) {
               break;
            }

            String val = str.substring(idx, pos);
            val = removeQuote ? stripOffQuote(val.trim()) : val;

            if(removeEscape) {
               val = removeEscape(val, '\\');
            }

            v.add(val);

            idx = pos + delim.length() - charOffset;
         }

         str = str.substring(idx);
      }

      String val = removeQuote ? stripOffQuote(str.trim()) : str;

      if(removeEscape) {
         val = removeEscape(val, '\\');
      }

      v.add(val);
      return v.toArray(new String[0]);
   }

   /**
    * Strips quotation marks from the beginning and end of a string.
    *
    * @param val the string to modify.
    *
    * @return the modified string.
    */
   public static String stripOffQuote(String val) {
      int len = val.length();

      if(len >= 2) {
         char c0 = val.charAt(0);

         if((c0 == '\'' || c0 == '"') && c0 == val.charAt(len - 1)) {
            StringBuilder sb = new StringBuilder();
            char quote = c0;

            for(int i = 1; i < len - 1; i++) {
               char c = val.charAt(i);

               if(c == quote) {
                  // "" -> ", '' -> ' if in quote
                  if(i + 1 < len - 1 && val.charAt(i + 1) == quote) {
                     i++;
                  }
               }

               sb.append(c);
            }

            return sb.toString();
         }
      }

      return val;
   }

   /**
    * Creates a valid header named based off the name provided. Special
    * characters are converted to usable characters.
    * @param name the header name to validate and fix
    * @return  the valid header name
    */
   public static String createValidHeaderName(String name) {
      return name.replaceAll("[()]", "_");
   }

   /**
    * Check if a real csv line.
    * @param line which need to be checked if a real csv line.
    */
   private static boolean isRealCSVLine(String line) {
      if(line == null || line.length() == 0) {
         return true;
      }

      int count = 0;
      int length = line.length();
      int index1 = 0;
      int index2 = line.indexOf(',');

      while(index1 < length && index2 >= 0) {
         if(countQuote(line, index1, index2 - index1) % 2 == 1) {
            ++count;
         }

         index1 = index2 + 1;

         if(index1 < length) {
            index2 = line.indexOf(',', index1);
         }
      }

      if(index1 < length) {
         if(countQuote(line, index1, length - index1) %2 == 1) {
            ++count;
         }
      }

      return count % 2 == 0;
   }

   private static int countQuote(String str, int offset, int length) {
      int count = 0;

      for(int i = 0; i < length; i++) {
         if(str.charAt(i + offset) == '"') {
            count++;
         }
      }

      return count;
   }

   /**
    * Read csv file line.
    * @param reader the reader to read csv file.
    */
   public static String readCSVLine(BufferedReader reader) throws Exception {
      String line = reader.readLine();

      while(!isRealCSVLine(line)) {
         String nline = reader.readLine();

         if(nline == null) {
            break;
         }

         line = line + (line.equals("") ? "" : "\n") + nline;
      }

      return line;
   }

   /**
    * Removes escape characters from a string.
    *
    * @param str    the string to clean.
    * @param escape the escape character.
    *
    * @return the modified string.
    */
   public static String removeEscape(String str, char escape) {
      if(str.indexOf(escape) < 0) {
         return str;
      }

      StringBuilder res = new StringBuilder(str.length());

      for(int i = 0; i < str.length(); i++) {
         char character = str.charAt(i);

         if(character == escape) {
            int w = i + 1;

            if(w < str.length()) {
               char next = str.charAt(w);

               switch(next) {
               case '\\':
                  res.append(character).append(escape);
                  break;
               case 'n':
                  res.append("\n");
                  break;
               case 'r':
                  res.append("\r");
                  break;
               case '\'':
                  res.append("'");
                  break;
               case '"':
                  res.append("\"");
                  break;
               default :
                  res.append(character).append(next);
               }
            }
            else {
               res.append(escape);
            }

            i = w;
         }
         else {
            res.append(character);
         }
      }

      return res.toString();
   }

   /**
    * Reads a line from a fixed-length file.
    *
    * @param line    the line to parse.
    * @param lengths the length of each column.
    *
    * @return the fields in the line.
    *
    * @throws Exception if the line could not be parsed.
    */
   public static String[] readFixedLengthLine(String line, int[] lengths) {
      // calculate real length of the string
      int len = line.length();

      for(int i = 0; i < line.length(); i++) {
         if(line.charAt(i) > 255) {
            len++;
         }
      }

      // reset the string to char array
      Character[] chars = new Character[len];
      int n = 0;

      for(int i = 0; i < line.length(); i++, n++) {
         char c = line.charAt(i);

         chars[n] = c;
         if(c > 255) {
            n++;
            chars[n] = null;
         }
      }

      // fill each column according the length limit
      int begin = 0;
      int end = 0;
      String[] contents = new String[lengths.length];

      for(int i = 0; i < lengths.length; i++) {
         end += lengths[i];

         if(end >= len) {
            end = len;
         }

         contents[i] = "";

         if(begin != len) {
            for(int j = begin; j < end; j++) {
               if(chars[j] != null) {
                  contents[i] += chars[j];
               }
            }
         }

         begin = end;
      }

      return contents;
   }

   /**
    * Gets the data type of the specified value object.
    *
    * @param data the value object.
    *
    * @return the data type;
    */
   public static TypeFormat getType(Object data) {
      String str = (String) data;

      if(str == null) {
         return new TypeFormat(XSchema.STRING);
      }

      // Date
      TypeFormat typeFormat = getDateType(str);

      if(typeFormat != null) {
         return typeFormat;
      }

      // Time
      typeFormat = getTimeType(str);

      if(typeFormat != null) {
         return typeFormat;
      }

      String type = XSchema.INTEGER;

      try {
         Integer.parseInt(str);
      }
      catch(Throwable ex) {
         type = XSchema.DOUBLE;
      }

      if(XSchema.DOUBLE.equals(type)) {
         NumberFormat fmt = new DecimalFormat("#,###.#");

         if(str.endsWith("%")) {
            fmt = NumberFormat.getPercentInstance();
         }
         else if(str.length() > 0 && str.charAt(0) == '$') {
            str = str.substring(1);
         }
         else if(str.startsWith("-$")) {
            str = "-" + str.substring(2);
         }
         else if(str.startsWith("($") && str.endsWith(")")) {
            str = "-" + str.substring(2, str.length() - 1);
         }

         try {
            ParsePosition pos = new ParsePosition(0);
            fmt.parseObject(str, pos);

            if(pos.getIndex() != str.length()) {
               throw new RuntimeException("failed");
            }
         }
         catch(Throwable ex) {
            type = XSchema.STRING;
         }
      }

      return new TypeFormat(type);
   }

   /**
    * Creates a column name for the specified column.
    *
    * @param columnIndex the zero-based index of the column.
    *
    * @return the default column name.
    */
   public static String createColumnName(int columnIndex) {
      return String.format("Column%d", columnIndex);
   }

   /**
    * Gets the date data type that corresponds to the specified format.
    *
    * @param format the format to inspect.
    *
    * @return the data type.
    */
   public static String getDateType(SimpleDateFormat format) {
      String type = null;

      if(format == null) {
         return type;
      }

      Matcher matcher = DATE_FIELDS.matcher(format.toPattern());
      boolean hasDate = matcher.find();

      matcher = TIME_FIELDS.matcher(format.toPattern());
      boolean hasTime = matcher.find();

      if(hasDate && hasTime) {
         type = "timeInstant";
      }
      else if(hasDate) {
         type = "date";
      }
      else if(hasTime) {
         type = "time";
      }

      return type;
   }

   /**
    * Attempts to detect the file type of the specified input stream. This
    * method reads from the input stream, so if it needs to be re-used, the
    * caller is responsible for buffering it. This method has no way to
    * differentiate between delimited and fixed-width text files, so any file
    * that is not {@link TextFileType#XLS} or {@link TextFileType#XLSX} will be
    * represented as {@link TextFileType#DELIMITED}.
    *
    * @param input the input stream to inspect.
    *
    * @return the type of file in the input stream.
    *
    * @throws IOException if the input stream could not be read.
    */
   public static TextFileType detectType(InputStream input) throws IOException {
      TextFileType type = TextFileType.DELIMITED;

      if(input != null) {
         byte[] data = new byte[8];
         int len = 0;

         while(len < 8) {
            int n = input.read(data, len, 8 - len);

            if(n < 0) {
               break;
            }

            len += n;
         }

         if(data[0] == (byte) 0x50 && data[1] == (byte) 0x4b &&
            data[2] == (byte) 0x03 && data[3] == (byte) 0x04)
         {
            type = TextFileType.XLSX;
         }
         if(data[0] == (byte) 0xd0 && data[1] == (byte) 0xcf &&
            data[2] == (byte) 0x11 && data[3] == (byte) 0xe0 &&
            data[4] == (byte) 0xa1 && data[5] == (byte) 0xb1 &&
            data[6] == (byte) 0x1a && data[7] == (byte) 0xe1)
         {
            type = TextFileType.XLS;
         }
      }

      return type;
   }

   /**
    * Determines whether a string is parsable as a Date, and generates an
    * appropriate Date Format string.
    * @param str  the string to determine the Date type from
    * @return  the type format that can be used to parse the string
    */
   public static TypeFormat getDateType(String str) {
      String format = getStructuredDateFormat(str);

      if(format != null) {
         return new TypeFormat(getDateType(new SimpleDateFormat(format)), format);
      }

      return null;
   }

   /**
    * Determines whether a string is parsable as a Time, and generates an
    * appropriate Time format string.
    * @param str  the string to determine the Time type from
    * @return  the type format that can be used to parse the string
    */
   private static TypeFormat getTimeType(String str) {
      String format = getTimeFormat(str);

      if(format != null) {
         return new TypeFormat(XSchema.TIME, format);
      }

      return null;
   }

   /**
    * Determines a date format for a structured Date string, for example
    * "MM/dd/yyyy" or "yyyy-MM-dd".
    * @param str  the string to create the date pattern for
    * @return  the format pattern that satisfies the string, or <tt>null</tt>
    * if the pattern could not be determined.
    */
   private static String getStructuredDateFormat(String str) {
      Matcher m = structuredDatePattern.matcher(str);

      if(!m.matches() || m.group(1) == null || m.group(2) == null ||
            m.group(3) == null || m.group(4) == null ||
            m.group(5) == null)
      {
         return null;
      }

      StringBuilder format = new StringBuilder();
      int len1 = m.group(1).length();
      int len3 = m.group(3).length();
      int len5 = m.group(5).length();

      // Starts with yyyy-?-? assume YYYY/Month/Day
      if(len1 > 2) {
         format.append("yyyy");
         format.append(m.group(2).charAt(0));
         format.append(len3 == 1 ? "M" : "MM");
         format.append(m.group(4).charAt(0));
         format.append(len5 == 1 ? "d" : "dd");
      }
      else {
         // Ends with ?-?-yyyy assume Month/Day/YYYY
         if(len5 > 2) {
            format.append(len1 == 1 ? "M" : "MM");
            format.append(m.group(2).charAt(0));
            format.append(len3 == 1 ? "d" : "dd");
            format.append(m.group(4).charAt(0));
            format.append("yyyy");
         }
         // Ends with ?-?-yy assume Month/Day/YY
         else {
            format.append(len1 == 1 ? "M" : "MM");
            format.append(m.group(2).charAt(0));
            format.append(len3 == 1 ? "d" : "dd");
            format.append(m.group(4).charAt(0));
            format.append("yy");
         }
      }

      // Detect Time Component
      if(m.group(7) != null) {
         String timeFormat = getTimeFormat(m.group(7));

         if(timeFormat != null) {
            format.append((m.group(6) == null ? "" : m.group(6)));
            format.append(timeFormat);
         }
      }

      return format.toString();
   }

   /**
    * Determines a time format pattern that matches a string
    * @param str  the string to create the time pattern for
    * @return  the format pattern that satisfies the string, or <tt>null</tt>
    * if the pattern could not be determined.
    */
   public static String getTimeFormat(String str) {
      Matcher m = timePattern.matcher(str);

      if(!m.matches() || m.group(1) == null) {
         return null;
      }

      boolean is24hour = m.group(7) == null;
      String hourDigit = is24hour ? "H" : "K";

      StringBuilder format = new StringBuilder();
      format.append(m.group(1).length() == 1 ? hourDigit : hourDigit+hourDigit);
      format.append(":mm");
      format.append(m.group(3) == null ? "" : ":ss");
      format.append(m.group(4) == null ? "" : ".S");
      format.append(m.group(5) == null ? "" : m.group(5));
      format.append(m.group(6) == null ? "" : m.group(6));
      format.append(is24hour ? "" : "a");

      return format.toString();
   }

   /**
    * Clean-up string so values appear the same in excel are treated as same.
    */
   public static String cleanUpString(String str) {
      if(str != null) {
         // trim trailing space
         Matcher matcher = TRAIL_SPACE.matcher(str);
         matcher = INVISIBLE_CHAR.matcher(matcher.replaceFirst(""));
         // remove invisible characters
         str = matcher.replaceAll("");
      }

      return str;
   }

   /**
    * Simple data structure for representing a Type and a Format
    */
   public static class TypeFormat {
      public TypeFormat(String type, String format) {
         this.type = type;
         this.format = format;
      }

      public TypeFormat(String type) {
         this.type = type;
      }

      public String getType() {
         return type;
      }

      public String getFormat() {
         return format;
      }

      @Override
      public String toString() {
         return "TypeFormat@" + Integer.toHexString(hashCode()) +
               "[" + type + "," + format + "]";
      }

      private final String type;
      private String format;
   }

   private static final ThreadLocal<ArrayList<String>> LISTS =
      ThreadLocal.withInitial(ArrayList::new);

   private static final Pattern timePattern = Pattern.compile("^([0-9]|[01][0-9]|2[0-3]){1}(:[0-5][0-9]){1}(:[0-5][0-9]){0,1}(\\.[0-9]{1,3}){0,1}(Z){0,1}(\\ )*([[aA]|[pP]][mM]){0,1}$");
   private static final Pattern structuredDatePattern = Pattern.compile("^([0-9]{1,4}){1}([\\.\\-/])([0-9]{1,2}){1}([\\.\\-/])([0-9]{1,4}){1}( *)(.*)$");
   private static final Pattern DATE_FIELDS = Pattern.compile("[GyMwWDdFE]");
   private static final Pattern TIME_FIELDS = Pattern.compile("[aHkKhmsSzA]");
   private static final Pattern TRAIL_SPACE = Pattern.compile("\\s+$");
   private static final Pattern INVISIBLE_CHAR = Pattern.compile("\\p{C}");
}
