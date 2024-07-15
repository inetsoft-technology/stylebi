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

import inetsoft.report.internal.Util;

import java.text.*;
import java.util.Locale;

/**
* Modified message format that does not require an object array as
* argument.
*/
public class MessageFormat extends Format {
   /**
    * Constructor.
    * @param pattern the pattern for this message format.
    */
   public MessageFormat(String pattern) {
      fmt = new java.text.MessageFormat(pattern);
      initFormats(fmt);
   }

   /**
    * Constructor.
    * @param pattern the pattern for this message format.
    * @param locale the locale for this message format
    */
   public MessageFormat(String pattern, Locale locale) {
      fmt = new java.text.MessageFormat(Util.localizeTextFormat(pattern));
      fmt.setLocale(locale);
      initFormats(fmt);
   }

   private void initFormats(java.text.MessageFormat fmt) {
      Format[] fmts = fmt.getFormats();

      for(int i = 0; i < fmts.length; i++) {
         if(fmts[i] instanceof DecimalFormat) {
            String pattern = ((DecimalFormat) fmts[i]).toPattern();

            if(ExtendedDecimalFormat.isExtendedFormat(pattern)) {
               fmt.setFormatByArgumentIndex(i, new ExtendedDecimalFormat(pattern));
            }
         }
      }
   }

   /**
    * Formats an object and appends the resulting text to a given string buffer.
    * @param obj The object to format.
    * @param toAppendTo - where the text is to be appended.
    * @param pos - A FieldPosition identifying a field in the formatted text.
    * @return the string buffer passed in as toAppendTo, with formatted text
    * appended.
    */
   @Override
   public StringBuffer format(Object obj, StringBuffer toAppendTo,
                              FieldPosition pos)
   {
      Object[] arr = (obj instanceof Object[]) ? (Object[]) obj
         : new Object[] {obj};
      return fmt.format(arr, toAppendTo, pos);
   }

   /**
    * The method attempts to parse text starting at the index given by pos.
    */
   @Override
   public Object parseObject(String source, ParsePosition pos) {
      return fmt.parseObject(source, pos);
   }

   /**
    * Get the formats used for the format elements.
    */
   public Format[] getFormats() {
      return fmt.getFormats();
   }

   /**
    * Set the format to use for the format element with the format element index.
    */
   public void setFormatByArgumentIndex(int idx, Format fmt) {
      this.fmt.setFormatByArgumentIndex(idx, fmt);
   }

   /**
    * Returns a pattern representing the current state of the message format.
    * @return a pattern representing the current state of the message format.
    */
   public String toPattern() {
      return fmt.toPattern();
   }

   public static boolean isMessageFormat(Format fmt) {
      return fmt instanceof MessageFormat || fmt instanceof java.text.MessageFormat;
   }

   public static Format[] getFormats(Format format) {
      if(format instanceof java.text.MessageFormat) {
         return ((java.text.MessageFormat) format).getFormats();
      }
      else if(format instanceof MessageFormat) {
         return ((MessageFormat) format).getFormats();
      }

      return new Format[0];
   }

   public static void setFormatByArgumentIndex(Format format, int idx, Format fmt) {
      if(format instanceof java.text.MessageFormat) {
         ((java.text.MessageFormat) format).setFormatByArgumentIndex(idx, fmt);
      }
      else if(format instanceof MessageFormat) {
         ((MessageFormat) format).setFormatByArgumentIndex(idx, fmt);
      }
   }

   private java.text.MessageFormat fmt;
}
