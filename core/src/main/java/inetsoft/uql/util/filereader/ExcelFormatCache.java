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

import java.text.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that converts Excel format strings to Java format strings.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class ExcelFormatCache {
   /**
    * Creates a new instance of <tt>ExcelFormatCache</tt>.
    */
   public ExcelFormatCache() {
      formats = new HashMap<>();

      // init built-in formats
      
      Format zipFormat = ZipPlusFourFormat.instance;
      formats.put("00000\\-0000", zipFormat);
      formats.put("00000-0000", zipFormat);

      Format phoneFormat = PhoneFormat.instance;
      // allow for format string variations
      formats.put("[<=9999999]###\\-####;\\(###\\)\\ ###\\-####", phoneFormat);
      formats.put("[<=9999999]###-####;(###) ###-####", phoneFormat);
      formats.put("###\\-####;\\(###\\)\\ ###\\-####", phoneFormat);
      formats.put("###-####;(###) ###-####", phoneFormat);

      Format ssnFormat = SSNFormat.instance;
      formats.put("000\\-00\\-0000", ssnFormat);
      formats.put("000-00-0000", ssnFormat);
   }

   /**
    * Gets the Java format for the specified Excel format.
    * 
    * @param cellValue   the value of the formatted cell.
    * @param formatIndex the Excel format index.
    * @param formatStr   the Excel format pattern.
    * 
    * @return the Java format.
    */
   public Format getFormat(double cellValue, int formatIndex, String formatStr)
   {
      Format format = (Format) formats.get(formatStr);

      if(format != null) {
         return format;
      }

      if(formatStr.equals("General") || formatStr.equals("@")) {
         if(isWholeNumber(cellValue)) {
            return generalWholeNumFormat;
         }

         return generalDecimalNumFormat;
      }

      format = createFormat(cellValue, formatIndex, formatStr);
      formats.put(formatStr, format);
      return format;
   }

   /**
    * Creates a Java format from an Excel format.
    * 
    * @param cellValue   the value of the formatted cell.
    * @param formatIndex the Excel format index.
    * @param sFormat     the Excel format pattern.
    * 
    * @return the Java format object.
    */
   private Format createFormat(double cellValue, int formatIndex,
                               String sFormat)
   {
      // remove color formatting if present
      String formatStr = sFormat.replaceAll("\\[[a-zA-Z]*\\]", "");

      // try to extract special characters like currency
      Matcher m = specialPatternGroup.matcher(formatStr);

      while(m.find()) {
         String match = m.group();
         String symbol =
            match.substring(match.indexOf('$') + 1, match.indexOf('-'));

         if(symbol.indexOf('$') > -1) {
            StringBuilder sb = new StringBuilder();
            sb.append(symbol.substring(0, symbol.indexOf('$')));
            sb.append('\\');
            sb.append(symbol.substring(symbol.indexOf('$'), symbol.length()));
            symbol = sb.toString();
         }

         formatStr = m.replaceAll(symbol);
         m = specialPatternGroup.matcher(formatStr);
      }

      if(formatStr == null || formatStr.trim().length() == 0) {
         return getDefaultFormat(cellValue);
      }

      if(ExcelFileSupport.getInstance().isDateFormat(cellValue, formatIndex, formatStr)) {
         return createDateFormat(formatStr, cellValue);
      }

      if(numPattern.matcher(formatStr).find()) {
         return createNumberFormat(formatStr, cellValue);
      }

      return null;
   }

   /**
    * Creates a Java date format from an Excel date format.
    * 
    * @param pFormatStr the Excel format pattern.
    * @param cellValue  the value of the formatted cell.
    * 
    * @return the Java date format object.
    */
   private Format createDateFormat(String pFormatStr, double cellValue) {
      String formatStr = pFormatStr;
      formatStr = formatStr.replaceAll("\\\\-","-");
      formatStr = formatStr.replaceAll("\\\\,",",");
      formatStr = formatStr.replaceAll("\\\\ "," ");
      formatStr = formatStr.replaceAll(";@", "");
      boolean hasAmPm = false;
      Matcher amPmMatcher = amPmPattern.matcher(formatStr);

      while(amPmMatcher.find()) {
         formatStr = amPmMatcher.replaceAll("@");
         hasAmPm = true;
         amPmMatcher = amPmPattern.matcher(formatStr);
      }

      formatStr = formatStr.replaceAll("@", "a");
      Matcher dateMatcher = daysAsText.matcher(formatStr);

      if(dateMatcher.find()) {
         String match = dateMatcher.group(0);
         formatStr =
            dateMatcher.replaceAll(match.toUpperCase().replaceAll("D", "E"));
      }

      // Convert excel date format to SimpleDateFormat.
      // Excel uses lower case 'm' for both minutes and months.
      // From Excel help:
      /*
        The "m" or "mm" code must appear immediately after the "h" or"hh"
        code or immediately before the "ss" code; otherwise, Microsoft
        Excel displays the month instead of minutes.
       */
      StringBuilder sb = new StringBuilder();
      char[] chars = formatStr.toCharArray();
      boolean mIsMonth = true;
      List<Integer> ms = new ArrayList<>();

      for(int j=0; j<chars.length; j++) {
         char c = chars[j];

         if(c == 'h' || c == 'H') {
            mIsMonth = false;

            if(hasAmPm) {
               sb.append('h');
            }
            else {
               sb.append('H');
            }
         }
         else if(c == 'm' || c == 'M') {
            if(mIsMonth) {
               sb.append('M');
               ms.add(Integer.valueOf(sb.length() -1));
            }
            else {
               sb.append('m');
            }
         }
         else if(c == 's' || c == 'S') {
            sb.append('s');

            // if 'M' precedes 's' it should be minutes ('m')
            for(int i = 0; i < ms.size(); i++) {
               int index = ((Integer)ms.get(i)).intValue();

               if(sb.charAt(index) == 'M') {
                  sb.replace(index, index+1, "m");
               }
            }

            mIsMonth = true;
            ms.clear();
         }
         else if(Character.isLetter(c)) {
            mIsMonth = true;
            ms.clear();

            if(c == 'y' || c == 'Y') {
               sb.append('y');
            }
            else if(c == 'd' || c == 'D') {
               sb.append('d');
            }
            else {
               sb.append(c);
            }
         }
         else {
            sb.append(c);
         }
      }

      formatStr = sb.toString();

      try {
         return new SimpleDateFormat(formatStr);
      }
      catch(IllegalArgumentException iae) {
         // the pattern could not be parsed correctly,
         // so fall back to the default number format
         return getDefaultFormat(cellValue);
      }
   }

   /**
    * Creates a Java number format from an Excel number format.
    * 
    * @param formatStr the Excel format pattern.
    * @param cellValue the value of the formatted cell.
    * 
    * @return the Java number format object.
    */
   private Format createNumberFormat(String formatStr, double cellValue) {
      StringBuilder sb = new StringBuilder(formatStr);

      for(int i = 0; i < sb.length(); i++) {
         char c = sb.charAt(i);

         //handle (#,##0_);
         if(c == '(') {
            int idx = sb.indexOf(")", i);

            if(idx > -1 && sb.charAt(idx -1) == '_') {
               sb.deleteCharAt(idx);
               sb.deleteCharAt(idx - 1);
               sb.deleteCharAt(i);
               i--;
            }
         }
         else if(c == ')' && i > 0 && sb.charAt(i - 1) == '_') {
            sb.deleteCharAt(i);
            sb.deleteCharAt(i - 1);
            i--;
         }
         // remove quotes and back slashes
         else if(c == '\\' || c == '"') {
            sb.deleteCharAt(i);
            i--;
         }
         // for scientific/engineering notation
         else if(c == '+' && i > 0 && sb.charAt(i - 1) == 'E') {
            sb.deleteCharAt(i);
            i--;
         }
      }

      try {
         return new DecimalFormat(sb.toString());
      }
      catch(IllegalArgumentException iae) {
         // the pattern could not be parsed correctly,
         // so fall back to the default number format
         return getDefaultFormat(cellValue);
      }
   }
   
   /**
    * Gets the default Java format for a value.
    * 
    * @param cellValue the value of the formatted cell.
    * 
    * @return the Java format object.
    */
   private Format getDefaultFormat(double cellValue) {
      if(isWholeNumber(cellValue)){
         return generalWholeNumFormat;
      }

      return generalDecimalNumFormat;
  }

   /**
    * Return true if the double value represents a whole number.
    * 
    * @param d the double value to check
    * 
    * @return <code>true</code> if d is a whole number
    */
   private static boolean isWholeNumber(double d) {
      return d == Math.floor(d);
   }

   /**
    * Gets a format with the parse integer only flag set to true.
    * 
    * @param fmt the format pattern.
    * 
    * @return a the format object.
    */
   private static DecimalFormat createIntegerOnlyFormat(String fmt) {
       DecimalFormat result = new DecimalFormat(fmt);
       result.setParseIntegerOnly(true);
       return result;
   }

   // Pattern to find a number format: "0" or  "#"
   private static final Pattern numPattern = Pattern.compile("[0#]+");

   // Pattern to find days of week as text "ddd...."
   private static final Pattern daysAsText =
      Pattern.compile("([d]{3,})", Pattern.CASE_INSENSITIVE);

   // Pattern to find "AM/PM" marker
   private static final Pattern amPmPattern =
      Pattern.compile("((A|P)[M/P]*)", Pattern.CASE_INSENSITIVE);

   // A regex to find patterns like [$$-1009] and [$?-452].
   private static final Pattern specialPatternGroup =
      Pattern.compile("(\\[\\$[^-\\]]*-[0-9A-Z]+\\])");

   // General format for whole numbers.
   private static final Format generalWholeNumFormat = new DecimalFormat("#");

   // General format for decimal numbers.
   private static final Format generalDecimalNumFormat =
      new DecimalFormat("#.##########");

   // A map to cache formats
   private final Map<String, Format> formats;

   /**
    * Format class for Excel's SSN format. This class mimics Excel's built-in
    * SSN formatting.
    */
   private static final class SSNFormat extends Format {
      private SSNFormat() {
         // enforce singleton
      }

      /** Format a number as an SSN */
      public static String format(Number num) {
         String result = df.format(num);
         StringBuilder sb = new StringBuilder();
         sb.append(result.substring(0, 3)).append('-');
         sb.append(result.substring(3, 5)).append('-');
         sb.append(result.substring(5, 9));
         return sb.toString();
      }

      @Override
      public StringBuffer format(Object obj, StringBuffer toAppendTo,
            FieldPosition pos)
      {
         return toAppendTo.append(format((Number)obj));
      }

      @Override
      public Object parseObject(String source, ParsePosition pos) {
         return df.parseObject(source, pos);
      }

      public static final Format instance = new SSNFormat();
      private static final DecimalFormat df =
         createIntegerOnlyFormat("000000000");
   }

   /**
    * Format class for Excel Zip + 4 format. This class mimics Excel's
    * built-in formatting for Zip + 4.
    * @author James May
    */
   private static final class ZipPlusFourFormat extends Format {
      private ZipPlusFourFormat() {
         // enforce singleton
      }

      /** Format a number as Zip + 4 */
      public static String format(Number num) {
         String result = df.format(num);
         StringBuilder sb = new StringBuilder();
         sb.append(result.substring(0, 5)).append('-');
         sb.append(result.substring(5, 9));
         return sb.toString();
      }

      @Override
      public StringBuffer format(Object obj, StringBuffer toAppendTo,
            FieldPosition pos)
      {
         return toAppendTo.append(format((Number)obj));
      }

      @Override
      public Object parseObject(String source, ParsePosition pos) {
         return df.parseObject(source, pos);
      }

      public static final Format instance = new ZipPlusFourFormat();
      private static final DecimalFormat df =
         createIntegerOnlyFormat("000000000");
   }

   /**
    * Format class for Excel phone number format. This class mimics Excel's
    * built-in phone number formatting.
    * @author James May
    */
   private static final class PhoneFormat extends Format {
      private PhoneFormat() {
         // enforce singleton
      }

      /** Format a number as a phone number */
      public static String format(Number num) {
         String result = df.format(num);
         StringBuilder sb = new StringBuilder();
         String seg1, seg2, seg3;
         int len = result.length();
         if(len <= 4) {
            return result;
         }

         seg3 = result.substring(len - 4, len);
         seg2 = result.substring(Math.max(0, len - 7), len - 4);
         seg1 = result.substring(Math.max(0, len - 10), Math.max(0, len - 7));

         if(seg1 != null && seg1.trim().length() > 0) {
            sb.append('(').append(seg1).append(") ");
         }
         if(seg2 != null && seg2.trim().length() > 0) {
            sb.append(seg2).append('-');
         }
         sb.append(seg3);
         return sb.toString();
      }

      @Override
      public StringBuffer format(Object obj, StringBuffer toAppendTo,
            FieldPosition pos)
      {
         return toAppendTo.append(format((Number)obj));
      }

      @Override
      public Object parseObject(String source, ParsePosition pos) {
         return df.parseObject(source, pos);
      }

      public static final Format instance = new PhoneFormat();
      private static final DecimalFormat df =
         createIntegerOnlyFormat("##########");
   }
}
