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
package inetsoft.util.script;

import inetsoft.util.CoreTool;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.*;

/**
 * Implementation of all Text and Data functions for JavaScript
 *
 * @version 8.0, 7/22/2005
 * @author InetSoft Technology Corp
 */
public class CalcTextData {
   /**
    * Returns the character specified by a number
    * @param number number between 1 and 255 specifying which character you want
    * @return character specified by a number
    */
   public static char character(int number) {
      return (char) number;
   }

   /**
    * Returns a numeric code for the first character in a text string
    * @param text text for which you want the code of the first character
    * @return numeric code for the first character in a text string
    */
   public static int code(String text) {
      char[] chars = text.toCharArray();

      return (int) chars[0];
   }

   /**
    * Joins several text strings into one text string
    * @param dataObj text items to be joined into a single text item
    * @return concatenated string
    */
   public static String concatenate(Object dataObj) {
      Object[] data = JavaScriptEngine.split(dataObj);

      StringBuilder concData = new StringBuilder();

      for(int i = 0; i < data.length; i++) {
         concData.append(data[i].toString());
      }

      return concData.toString();
   }

   /**
    * Converts a number to text format and applies a currency symbol
    * @param number number to be converted
    * @param decimals number of digits to the right of the decimal point
    * @return converted number
    */
   public static String dollar(double number, int decimals) {
      number = CalcMath.round(number, decimals);

      // @by stephenwebster, For bug1431032162714
      // Seems illogical to negate a negative number
      /*
      if(number < 0) {
         number *= -1;
      }
      */

      // @by stephenwebster, For bug1431032162714
      // Support negative number formatting same as Excel
      DecimalFormat fmt = null;

      if(decimals > 0) {
         StringBuilder sb = new StringBuilder("$#,##0.");

         for(int i = 0; i < decimals; i++) {
            sb.append("0");
         }

         sb.append(";(" + sb.toString() + ")");
         fmt = new java.text.DecimalFormat(sb.toString());
      }
      else {
         fmt = new java.text.DecimalFormat("$#,##0;($#,##0)");
      }

      if(((int) number) == number) {
         return fmt.format((int) number);
      }

      return fmt.format(number);
   }

   /**
    * Compares two text strings and returns TRUE if they are exactly the same,
    * FALSE otherwise
    * @param text1 first string
    * @param text2 second string
    * @return true if strings are exactly same, false otherwise
    */
   public static boolean exact(String text1, String text2) {
      if(text1 != null && text2 != null) {
         return text1.equals(text2);
      }

      return false;
   }

   /**
    * Locate one text string within a second text string, and return the
    * number of the starting position of the first text string from the
    * first character of the second text string (Case-Sensitive)
    * @param find_text text you want to find
    * @param within_text text in which you want to search for find_text
    * @param start_num character number in within_text at which you want to
    * start searching
    * @return searched index
    */
   public static int find(String find_text, String within_text, int start_num) {
      if(start_num <= 0 || start_num > within_text.length()) {
         throw new RuntimeException("Invalid search index !");
      }

      if("".equals(find_text)) {
         return start_num;
      }

      int index = 1 + within_text.indexOf(find_text, start_num - 1);

      return index;
   }

   /**
    * Rounds a number to the specified number of decimals, formats the number
    * in decimal format using a period and commas, and returns the result as
    * text
    * @param number number to be converted
    * @param decimals number of digits to the right of the decimal point
    * @param no_commas if TRUE, prevents FIXED from including commas in
    * the returned text
    * @return converted number
    */
   public static String fixed(double number, int decimals, boolean no_commas) {
      number = CalcMath.round(number, decimals);

      DecimalFormat fmt = null;

      if(no_commas) {
         fmt = new java.text.DecimalFormat("###0.###############");
      }
      else {
         fmt = new java.text.DecimalFormat("#,##0.###############");
      }

      if(((int) number) == number) {
         return fmt.format((int) number);
      }

      return fmt.format(number);
   }

   /**
    * Returns the first character or characters in a text string, based on
    * the number of characters you specify
    * @param text text string that contains the characters you want to extract
    * @param num_chars number of characters to extract
    * @return extracted string
    */
   public static String left(String text, int num_chars) {
      if(num_chars < 0) {
         throw new RuntimeException("Number of characters cannot be less than 0");
      }
      // Default value indicates that just one argument is specified
      // and hence use the Excel default 1 character
      else if(num_chars == 0) {
         num_chars = 1;
      }

      if(num_chars > text.length()) {
         num_chars = text.length();
      }

      return text.substring(0, num_chars);
   }

   /**
    * Returns the number of characters in a text string
    * @param text text string for which to find the length
    * @return length of the string
    */
   public static int len(String text) {
      if(text != null) {
         return text.length();
      }

      return 0;
   }

   /**
    * Converts all uppercase letters in a text string to lowercase
    * @param text text string to convert to lower case
    * @return lower cased string
    */
   public static String lower(String text) {
      if(text != null) {
         return text.toLowerCase();
      }

      return "";
   }

   /**
    * Returns a specific number of characters from a text string,
    * starting at the position you specify, based on the number of
    * characters you specify
    * @param text text string that contains the characters you want to extract
    * @param start_num starting index (Starts with 1, unlike in Java which begins
    * with 0)
    * @param num_chars number of characters to extract
    * @return extracted string
    */
   public static String mid(String text, int start_num, int num_chars) {
      if(num_chars < 0) {
         throw new RuntimeException("Number of characters cannot be negative");
      }

      if(start_num < 1) {
         throw new RuntimeException("start_num cannot be less than 1");
      }

      if(start_num > text.length()) {
         return "";
      }

      if(start_num + num_chars > text.length()) {
         return text.substring(start_num - 1);
      }

      return text.substring(start_num - 1, (start_num - 1) + num_chars);
   }

   /**
    * Capitalizes the first letter in a text string and any other letters in
    * text that follow any character other than a letter.
    * Converts all other letters to lowercase letters.
    * @param text text you want to partially capitalize
    * @return formatted string
    */
   public static String proper(String text) {
      StringTokenizer st = new StringTokenizer(text, " ");
      StringBuilder data = new StringBuilder();

      String txt = null;

      while(st.hasMoreTokens()) {
         txt = (String) st.nextToken();
         char[] chars = txt.toCharArray();

         boolean charFound = false;
         boolean prevChar = false;

         for(int i = 0; i < chars.length; i++) {
            if(Character.isLetter(chars[i])) {
               if(!charFound || !prevChar) {
                  chars[i] = Character.toUpperCase(chars[i]);
                  charFound = true;
                  prevChar = true;
               }
               else {
                  chars[i] = Character.toLowerCase(chars[i]);
               }
            }
            else {
               prevChar = false;
            }
         }

         data.append(new String(chars) + " ");
      }

      return data.toString().trim();
   }


   /**
    * Replaces part of a text string, based on the number of characters you
    * specify, with a different text string
    * @param text text string that contains the characters you want to extract
    * @param start_num starting index (Starts with 1, unlike in Java which begins
    * with 0)
    * @param num_chars number of characters to extract
    * @param new_text text that will replace characters in old_text
    * @return replaced string
    */
   public static String replace(String text, int start_num,
                                int num_chars, String new_text) {
      if(num_chars < 0) {
         throw new RuntimeException("Number of characters cannot be negative");
      }

      if(start_num < 1) {
         throw new RuntimeException("start_num cannot be less than 1");
      }

      if(start_num > text.length()) {
         return "";
      }

      if(start_num + num_chars > text.length()) {
         return text.substring(0, start_num - 1) + new_text;
      }

      return text.substring(0, start_num - 1) + new_text +
         text.substring((start_num - 1) + num_chars);
   }

   /**
    * Repeats text a given number of times
    * @param text text string to repeat
    * @param times number of times to repeat
    * @return repeated string
    */
   public static String rept(String text, int times) {
      StringBuilder rept = new StringBuilder();

      for(int i = 0; i < times; i++) {
         rept.append(text);
      }

      if(rept.toString().length() > 32767) {
         throw new RuntimeException("Result of the function is too long to " +
                                    "be displayed");
      }

      return rept.toString();
   }

   /**
    * Returns the last character or characters in a text string, based on
    * the number of characters you specify
    * @param text text string that contains the characters you want to extract
    * @param num_chars number of characters to extract
    * @return extracted string
    */
   public static String right(String text, int num_chars) {
      if(num_chars < 0) {
         throw new RuntimeException("Number of characters cannot be less " +
                                    "than 0");
      }
      // Default value indicates that just one argument is specified
      // and hence use the Excel default 1 character
      else if(num_chars == 0) {
         num_chars = 1;
      }

      if(num_chars > text.length()) {
         num_chars = text.length();
      }

      return text.substring(text.length() - num_chars, text.length());
   }

   /**
    * Locate one text string within a second text string, and return the
    * number of the starting position of the first text string from the
    * first character of the second text string (Case-Insensitive)
    * @param find_text text you want to find
    * @param within_text text in which you want to search for find_text
    * @param start_num character number in within_text at which you want to
    * start searching
    * @return searched index
    */
   public static int search(String find_text, String within_text, int start_num) {
      if(start_num <= 0 || start_num > within_text.length()) {
         throw new RuntimeException("Invalid search index !");
      }

      within_text = within_text.toLowerCase();
      find_text = find_text.toLowerCase();

      Matcher matcher = null;

      try {
         Pattern pattern = Pattern.compile(find_text, Pattern.CASE_INSENSITIVE);
         matcher = pattern.matcher(within_text.substring(start_num - 1));
      }
      catch(PatternSyntaxException e) {
         throw new RuntimeException("Invalid Search Pattern !");
      }

      int index = 0;

      if(matcher.find() && matcher.end() - matcher.start() > 0) {
         index += matcher.start();
      }
      else {
         return -1;
      }

      return start_num + index;
   }

   /**
    * Substitutes new_text for old_text in a text string
    * @param text text for which you want to substitute characters
    * @param old_text text you want to replace
    * @param new_text text you want to replace old_text with
    * @param instance_num specifies which occurrence of old_text you want to
    * replace with new_text
    * @return modified string
    */
   public static String substitute(String text, String old_text,
                                   String new_text, int instance_num) {
      StringBuilder data = new StringBuilder();

      String txt = text;

      int index = -1;
      int count = 0;

      while((index = txt.indexOf(old_text)) > -1) {
         count++;

         if(count == instance_num) {
            data.append(txt.substring(0, index) + new_text);
         }
         else {
            data.append(txt.substring(0, index) + old_text);
         }

         txt = txt.substring(index + old_text.length());
      }

      data.append(txt);
      return data.toString();
   }

   /**
    * Returns the text referred to by value
    * @param value value you want to test
    * @return tested value
    */
   public static String t(String value) {
      try {
         Double elem = Double.valueOf(value);
         return "";
      }
      catch(Exception e) {
         try {
            String val = new String(value);

            if("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
               return "";
            }
         }
         catch(Exception ex) {
         }
      }

      return value;
   }

   /**
    * Converts a value to text in a specific number format
    * @param text numeric value
    * @param format_text Java Number Formatting string
    * @return formatted string
    */
   public static String text(double text, String format_text) {
      String formattedString;

      try {
         DecimalFormat df = new DecimalFormat(format_text);
         formattedString = df.format(text);
      }
      catch(Exception e) {
         throw new RuntimeException(e.getMessage());
      }

      return formattedString;
   }

   /**
    * Removes all spaces from text except for single spaces between words
    * @param text text from which you want spaces removed
    * @return trimmed string
    */
   public static String trim(String text) {
      StringTokenizer st = new StringTokenizer(text.trim(), " ");
      StringBuilder sb = new StringBuilder();

      while(st.hasMoreTokens()) {
         String token = st.nextToken();
         sb.append(token + " ");
      }

      return sb.toString().trim();
   }

   /**
    * Converts all lowercased letters in a text string to uppercase
    * @param text text string to convert to upper case
    * @return upper cased string
    */
   public static String upper(String text) {
      if(text != null) {
         return text.toUpperCase();
      }

      return "";
   }

   /**
    * Converts a text string that represents a number to a number
    * @param text text you want to convert
    * @return converted text
    */
   public static double value(String text) {
      try {
         //Date type string
         if(text.contains("/")) {
            Scanner scanner = new Scanner(text);
            scanner.useDelimiter("/");
            int month = scanner.nextInt();
            int date = scanner.nextInt();
            int year = scanner.nextInt();
            Calendar cal = CoreTool.calendar.get();
            cal.clear();
            cal.set(Calendar.DATE, date);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.YEAR, year);
            return CalcUtil.getSerialDays(
               CalcUtil.getSerialStart().getTime(), cal.getTime());
         }
         //Time type string
         else if(text.contains(":")) {
            Scanner scanner = new Scanner(text);
            scanner.useDelimiter(":");
            int hours = scanner.nextInt();
            int mins = scanner.nextInt();
            int secs = scanner.nextInt();
            return (hours * 60.0 * 60.0 + mins * 60.0 + secs) /
               (24.0 * 60.0 * 60.0);
         }
         //Other, handled as number
         else {
            StringBuilder data = new StringBuilder();
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();

            for(char c : text.toCharArray()) {
               // @by jasonshobe, also include decimal separator - this is
               // consistent with the Excel function
               if(Character.isDigit(c) || c == symbols.getDecimalSeparator()) {
                  data.append(c);
               }
            }

            return Double.valueOf(data.toString());
         }
      }
      catch(Exception ex) {
         throw new RuntimeException("Invalid Data !");
      }
   }
}
