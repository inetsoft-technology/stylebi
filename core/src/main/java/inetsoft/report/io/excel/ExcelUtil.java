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
package inetsoft.report.io.excel;

import inetsoft.report.*;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.util.*;

import java.text.*;

/**
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ExcelUtil implements StyleConstants {
   /**
    * analyze the java.text.Format object.
    */
   public static String analyze(Format fmt) {
      String rgchStr = "";

      if(fmt instanceof DateFormat) {
         rgchStr = setDateFormat((SimpleDateFormat) fmt);
      }
      else if(fmt instanceof NumberFormat) {
         rgchStr = setNumberFormat((DecimalFormat) fmt);
      }

      return rgchStr;
   }

   /**
    *  analyze the java.text.DataFormat object.
    */
   private static String setDateFormat(SimpleDateFormat dfmt) {
      String pattern = dfmt.toPattern();
      // use user pattern instead of use a processed user pattern,
      // otherwise "EEE" or "yyyy QQQ" will be error when export out,
      // because they have been processed as "'EEE'" or "yyy '+#+QQQ+#+'",
      // and they both cannot be process correct in excel
      if(dfmt instanceof ExtendedDateFormat) {
         pattern = ((ExtendedDateFormat) dfmt).userPattern();
      }

      if(pattern == null) {
         return "";
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
               ++i;
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
            ++count;
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

      // java uses MMMMM for long month name, and excel uses MMMM
      return toAppendTo.toString().replace("MMMMM", "MMMM");
   }

   /**
    *  only used in the method setDataFormat().
    */
   private static String subSetDateFormat(char ch, int count) {
      StringBuilder current = new StringBuilder();
      int patternCharIndex;

      if((patternCharIndex = datePatternChars.indexOf(ch)) == -1) {
         for(int i = 0; i < count; i++) {
            current.append(ch);
         }

         return current.toString();
      }

      switch(patternCharIndex) {
      case 1: // 'y' - YEAR
      case 2: // 'M' - MONTH
      case 3: // 'd' - Date
      case 5: // case 5: // 'H'-HOUR_OF_DAY:0-based.eg, 23:59+1 hour=>>00:59
      case 6: // case 6: // 'm' - MINUTE
      case 7: // case 7: // 's' - SECOND
      case 8: // case 8: // 'S' - MILLISECOND
      case 15: // 'h' - HOUR:1-based.  eg, 11PM + 1 hour =>> 12 AM
         for(int i = 0; i < count; i++) {
            current.append(ch);
         }

         break;
      case 9: // 'E' - DAY_OF_WEEK
         // @by jasons, use correct date format codes for supported lengths;
         //             ignore others
         if(count == 3) {
            current.append("ddd");
         }
         else if(count == 4) {
            current.append("dddd");
         }

         break;
      case 14:    // 'a' - AM_PM
         current = new StringBuilder("AM/PM");
         break;
      case 4: // 'k' - HOUR_OF_DAY: 1-based.  eg, 23:59 + 1 hour =>> 24:59
      case 0: // 'G' - ERA
      case 17: // 'z' - ZONE_OFFSET
      default:
         // case 10: // 'D' - DAY_OF_YEAR
         // case 11: // 'F' - DAY_OF_WEEK_IN_MONTH
         // case 12: // 'w' - WEEK_OF_YEAR
         // case 13: // 'W' - WEEK_OF_MONTH
         // case 16: // 'K' - HOUR: 0-based.  eg, 11PM + 1 hour =>> 0 AM
         current = new StringBuilder();
         break;
      }

      return current.toString();
   }

   /**
    *  analyze the java.text.NumberFormat object.
    */
   private static String setNumberFormat(DecimalFormat nfmt) {
      // pattern;
      String pattern = nfmt instanceof ExtendedDecimalFormat ?
         ((ExtendedDecimalFormat) nfmt).getSimplePattern() : nfmt.toPattern();

      if(pattern == null) {
         return "";
      }

      char extSymbol = (nfmt instanceof ExtendedDecimalFormat) ?
         ((ExtendedDecimalFormat) nfmt).getExtendedDataSymbol() : ' ';

      String positivePrefix = nfmt.getPositivePrefix();
      String positiveSuffix = nfmt.getPositiveSuffix();
      String negativePrefix = nfmt.getNegativePrefix();
      String negativeSuffix = nfmt.getNegativeSuffix();

      positivePrefix = (positivePrefix == null) ? "" : positivePrefix;
      positiveSuffix = (positiveSuffix == null) ? "" : positiveSuffix;
      negativePrefix = (negativePrefix == null) ? "" : negativePrefix;
      negativeSuffix = (negativeSuffix == null) ? "" : negativeSuffix;

      int semiColon = pattern.indexOf(";");
      String positiveChars;
      String negativeChars;
      StringBuilder newPattern = new StringBuilder();

      if(semiColon != -1) {          // have semicolon;
         // append positivePrefix String;
         newPattern.append(setPrefixSuffix(positivePrefix, true));
         // append positive chars;
         positiveChars = pattern.substring(positivePrefix.length(),
            semiColon - positiveSuffix.length());

         newPattern.append(setPosiNegaChars(positiveChars, extSymbol));
         // append positiveSuffix string;
         newPattern.append(setPrefixSuffix(positiveSuffix, false));

         // append the sign ";" ;
         newPattern.append(";");

         if(negativePrefix.equals("(") && (negativeSuffix.equals(")") ||
            negativeSuffix.equals(")%")))
         {
            newPattern.append(pattern.substring(semiColon + 1));
         }
         else {
            // append negativePrefix string;
            newPattern.append(setPrefixSuffix(negativePrefix, true));
            // append negative chars;
            negativeChars = pattern.substring(semiColon + negativePrefix.length() + 1,
                                              pattern.length() - negativeSuffix.length());
            newPattern.append(setPosiNegaChars(negativeChars, extSymbol));
            // append negativeSuffix string;
            newPattern.append(setPrefixSuffix(negativeSuffix, false));
         }
      }
      else {
         positiveChars = pattern.substring(positivePrefix.length(),
                                           pattern.length() - positiveSuffix.length());
         // append positivePrefix String;
         newPattern.append(setPrefixSuffix(positivePrefix, true));
         // append positive chars;
         newPattern.append(setPosiNegaChars(positiveChars, extSymbol));
         // append positiveSuffix string;
         newPattern.append(setPrefixSuffix(positiveSuffix, false));
      }

      return newPattern.toString();
   }

   /**
    * only used in the method setNumberFormat().
    */
   private static String setPrefixSuffix(String prefixSuffix, boolean isPrefix) {
      if((prefixSuffix == null) || (prefixSuffix.equals(""))) {
         return "";
      }

      if(isPrefix) {
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
         if(prefixSuffix.startsWith("%")) {
            return buildPercentStringWithQuoteAfterPercent(prefixSuffix, "%");
         }
         // Some locales have the percent suffix ' %', e.g. fr_CH.
         else if(prefixSuffix.startsWith(" %")) {
            return buildPercentStringWithQuoteAfterPercent(prefixSuffix, " %");
         }
         else {
            return "\"" + prefixSuffix + "\"";
         }
      }
   }

   /**
    * Build a prefix/suffix string with the percent part unchanged but the rest quoted.
    * e.g. suffix '% increase' -> '% "increase"'
    *
    * @param str           the full suffix string.
    * @param percentPrefix the percent prefix that the string starts with.
    * @return the string with the non-percent part escaped.
    */
   private static String buildPercentStringWithQuoteAfterPercent(String str, String percentPrefix) {
      return str.substring(0, percentPrefix.length()) + (str.length() > percentPrefix.length() ?
         "\"" + str.substring(percentPrefix.length()) + "\"" : "");
   }

   /**
    *  only used in the method setNumberFormat().
    */
   private static String setPosiNegaChars(String PosiNegaChars, char extSymbol) {
      if(PosiNegaChars.equals("")) {
         return "";
      }

      StringBuilder bPC = new StringBuilder(PosiNegaChars);
      int ENum = 0;
      boolean decimalSeparator = false;

      for(int i = 0; i < PosiNegaChars.length(); i++) {
         char ch = PosiNegaChars.charAt(i);

         if(ch == 'E') {
            ENum++;
            bPC.insert(i + ENum, '+');
         }

         if(ch == '.') {
            decimalSeparator = true;

            switch(extSymbol) {
            case 'k':
            case 'K':
               bPC.insert(i, ',');
               ++i;
               break;
            case 'm':
            case 'M':
               bPC.insert(i, ",,");
               i += 2;
               break;
            case 'b':
            case 'B':
               bPC.insert(i, ",,,");
               i += 3;
               break;
            }
         }

         // Bug #56969, replace last # with 0 for formats like # or #,###
         if(!decimalSeparator && i == PosiNegaChars.length() - 1 && ch == '#') {
            bPC.replace(i + ENum, i + ENum + 1, "0");
         }
      }

      if(!decimalSeparator) {
         switch(extSymbol) {
         case 'k':
         case 'K':
            bPC.append(',');
            break;
         case 'm':
         case 'M':
            bPC.append(",,");
            break;
         case 'b':
         case 'B':
            bPC.append(",,,");
            break;
         }
      }

      if(extSymbol == 'k' || extSymbol == 'K' || extSymbol == 'm' || extSymbol == 'M' ||
         extSymbol == 'b' || extSymbol == 'B')
      {
         bPC.append('"').append(extSymbol).append('"');
      }

      return bPC.toString();
   }

   /*
    * Extract format from attribute table lens.
    */
   public static Format getFormat(TableLens tbl, int row, int col) {
      while(tbl instanceof TableFilter && row >= 0 && col >= 0) {
         TableFilter filter = (TableFilter) tbl;

         if(filter instanceof AttributeTableLens) {
            AttributeTableLens atbl = (AttributeTableLens) tbl;
            Format fmt = atbl.getFormat(row, col);

            if(fmt != null) {
               return fmt;
            }
         }

         row = filter.getBaseRowIndex(row);
         col = filter.getBaseColIndex(col);
         tbl = filter.getTable();
      }

      return null;
   }

   // ChrisS bug1402624552371 2014-6-16
   // Added trimSheetName() function, which truncates a sheet name to fit
   // Excels 31 character max limit.

   private static final String datePatternChars = "GyMdkHmsSEDFwWahKz";
}
