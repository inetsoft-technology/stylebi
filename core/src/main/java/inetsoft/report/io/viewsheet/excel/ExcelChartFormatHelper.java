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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.util.ExtendedDateFormat;

import java.text.*;

/**
 * Translate java.text.Format to string used by POI Format.
 *
 * @version 8.5, 11/15/2006
 * @author InetSoft Technology Corp
 */
public class ExcelChartFormatHelper {
   /**
    * Get the RGCH value.
    */
   protected String getRgch() {
      return rgch;
   }

   /**
    * analyze the java.text.Format object , should be same as
    * analyze method in FormatBiffElement.
    */
   public String analyze(Format fmt) {
      String rgchStr = "";

      if(fmt instanceof DateFormat) {
         rgchStr = setDateFormat((SimpleDateFormat) fmt);
      }
      else if(fmt instanceof NumberFormat) {
         rgchStr = setNumberFormat((DecimalFormat) fmt);
      }
      else if(fmt instanceof MessageFormat) {
         rgchStr = setMessageFormat((MessageFormat) fmt);
      }

      return rgchStr;
   }

   /**
    * Set the message format.
    */
   private String setMessageFormat(MessageFormat mfmt) {
      return new String();
   }

   /**
    * Analyze the java.text.DataFormat object.
    */
   private String setDateFormat(SimpleDateFormat dfmt) {
      String pattern = dfmt.toPattern();

      // @see FormatBiffElement.setDateFormat
      if(dfmt instanceof ExtendedDateFormat) {
         pattern = ((ExtendedDateFormat) dfmt).userPattern();
      }

      if(pattern == null) {
         String rg = "";
         return rg;
      }

      StringBuilder toAppendTo = new StringBuilder();
      boolean inQuote = false;   // true when between single quotes
      char prevCh = 0;           // previous pattern character
      int count = 0;             // number of time prevCh repeated

      for(int i = 0; i < pattern.length(); ++i) {
         char ch = pattern.charAt(i);

         // Use subFormat() to format a repeated pattern character
         // when a different pattern or non-pattern character is seen
         if(ch != prevCh && count > 0) {
            toAppendTo.append(subSetDateFormat(prevCh, count));
            count = 0;
         }

         // @by henryh, add backward slash to avoid translating slash to
         // subtraction sign in excel.
         if(ch == '/') {
            toAppendTo.append('\\');
            toAppendTo.append(ch);
         }
         else if(ch == '\'') {
            // Consecutive single quotes are a single quote literal,
            // either outside of quotes or between quotes
            if((i + 1) < pattern.length() && pattern.charAt(i + 1) == '\'') {
               toAppendTo.append("\"");
               i++;
            }
            else {
               inQuote = !inQuote;
               toAppendTo.append("\"");
            }
         }
         else if(!inQuote) {
            // ch is a date-time pattern character to be interpreted
            // by subFormat(); count the number of times it is repeated
            prevCh = ch;
            count++;
         }
         else {
            // Append quoted characters and unquoted non-pattern characters
            toAppendTo.append(ch);
         }
      }

      // Format the last item in the pattern, if any
      if(count > 0) {
         toAppendTo.append(subSetDateFormat(prevCh, count));
      }

      return toAppendTo.toString();
   }

   /**
    *  only used in the method setDataFormat().
    */
   private String subSetDateFormat(char ch, int count) {
      String current = "";
      int patternCharIndex = -1;

      if((patternCharIndex = datePatternChars.indexOf(ch)) == -1) {
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         return current;
      }

      switch(patternCharIndex) {
      case 0: // 'G' - ERA
         current = "";
         break;
      case 1: // 'y' - YEAR
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 2: // 'M' - MONTH
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 3: // 'd' - Date
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 4: // 'k' - HOUR_OF_DAY: 1-based.  eg, 23:59 + 1 hour =>> 24:59
         current = "";
         break;
      case 5: // case 5: // 'H'-HOUR_OF_DAY:0-based.eg, 23:59+1 hour=>>00:59
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 6: // case 6: // 'm' - MINUTE
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 7: // case 7: // 's' - SECOND
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 8: // case 8: // 'S' - MILLISECOND
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 9: // 'E' - DAY_OF_WEEK
         for(int i = 0; i < count; i++) {
            current += "a";
         }

         break;
      case 14:    // 'a' - AM_PM
         current = "AM/PM";
         break;
      case 15: // 'h' - HOUR:1-based.  eg, 11PM + 1 hour =>> 12 AM
         for(int i = 0; i < count; i++) {
            current += "" + ch;
         }

         break;
      case 17: // 'z' - ZONE_OFFSET
         current = "";
         break;
      default:
         // case 10: // 'D' - DAY_OF_YEAR
         // case 11: // 'F' - DAY_OF_WEEK_IN_MONTH
         // case 12: // 'w' - WEEK_OF_YEAR
         // case 13: // 'W' - WEEK_OF_MONTH
         // case 16: // 'K' - HOUR: 0-based.  eg, 11PM + 1 hour =>> 0 AM
         current = "";
         break;
      }

      return current;
   }

   /**
    * Analyze the java.text.NumberFormat object.
    */
   private String setNumberFormat(DecimalFormat nfmt) {
      String pattern = nfmt.toPattern();

      if(pattern == null) {
         String rg = "";

         return rg;
      }

      String positivePrefix = nfmt.getPositivePrefix();
      String positiveSuffix = nfmt.getPositiveSuffix();
      String negativePrefix = nfmt.getNegativePrefix();
      String negativeSuffix = nfmt.getNegativeSuffix();

      positivePrefix = (positivePrefix == null) ? "" : positivePrefix;
      positiveSuffix = (positiveSuffix == null) ? "" : positiveSuffix;
      negativePrefix = (negativePrefix == null) ? "" : negativePrefix;
      negativeSuffix = (negativeSuffix == null) ? "" : negativeSuffix;

      int isSemicolon = pattern.indexOf(";");
      String positiveChars = "";
      String negativeChars = "";
      StringBuilder newPattern = new StringBuilder();

      if(isSemicolon != -1) {          // have semicolon;
         // append positivePresix String;
         newPattern.append(setPrefixSuffix(positivePrefix, true));
         // append positive chars;
         positiveChars = pattern.substring(positivePrefix.length(),
            isSemicolon - positiveSuffix.length());

         if(positiveChars == null) {
            positiveChars = "";
         }

         newPattern.append(setPosiNegaChars(positiveChars));
         // append positiveSuffix string;
         newPattern.append(setPrefixSuffix(positiveSuffix, false));

         // append the sign ";" ;
         newPattern.append(";");

         // append negativePrefix string;
         newPattern.append(setPrefixSuffix(negativePrefix, true));
         // append negative chars;
         negativeChars = pattern.substring(isSemicolon +
            negativePrefix.length() + 1,
            pattern.length() - negativeSuffix.length());

         if(negativeChars == null) {
            negativeChars = "";
         }

         newPattern.append(setPosiNegaChars(negativeChars));
         // append negativeSuffix string;
         newPattern.append(setPrefixSuffix(negativeSuffix, false));
      }
      else {
         positiveChars = pattern.substring(positivePrefix.length(),
            pattern.length() - positiveSuffix.length());

         if(positiveChars == null) {
            positiveChars = "";
         }

         // append positivePresix String;
         newPattern.append(setPrefixSuffix(positivePrefix, true));
         // append positive chars;
         newPattern.append(setPosiNegaChars(positiveChars));
         // append positiveSuffix string;
         newPattern.append(setPrefixSuffix(positiveSuffix, false));
      }

      return newPattern.toString();
   }

   /**
    * Only used in the method setNumberFormat().
    */
   private String setPrefixSuffix(String prefixSuffix, boolean isPS) {
      if((prefixSuffix == null) || (prefixSuffix.equals(""))) {
         return "";
      }

      if(isPS) {
         // have percent sign in the last bit;
         if(prefixSuffix.charAt((prefixSuffix.length() - 1)) == '%') {
            if(prefixSuffix.length() == 1) {// only have a sign;
               return prefixSuffix;
            }
            else {
               return "\"" +
                  prefixSuffix.substring(0, prefixSuffix.length() - 1) + "\"";
            }
         }
         else {
            return "\"" + prefixSuffix + "\"";
         }
      }
      else {
         // have percent sign in the fist bit;
         if(prefixSuffix.charAt(0) == '%') {
            if(prefixSuffix.length() == 1) {
               // only have a sign;
               return prefixSuffix;
            }
            else {
               return "\"" + prefixSuffix.substring(1, prefixSuffix.length()) +
                  "\"";
            }
         }
         else {
            return "\"" + prefixSuffix + "\"";
         }
      }
   }

   /**
    * Only used in the method setNumberFormat().
    */
   private String setPosiNegaChars(String PosiNegaChars) {
      if(PosiNegaChars.equals("")) {
         return "";
      }

      StringBuilder bPC = new StringBuilder(PosiNegaChars);
      int ENum = 0;

      for(int i = 0; i < PosiNegaChars.length(); i++) {
         char ch = PosiNegaChars.charAt(i);

         if(ch == 'E') {
            ENum++;
            bPC.insert(i + ENum, '+');
         }
      }

      return bPC.toString();
   }

   private String rgch = "";
   private static final String datePatternChars = "GyMdkHmsSEDFwWahKz";
   private static final char[] numberPatternChars = new char[] {'0', '#', '.',
      '-', ',', 'E', ';', '%', '\u2030', '\u00a4'};
   public static final int GENERAL = 0x00;
}
