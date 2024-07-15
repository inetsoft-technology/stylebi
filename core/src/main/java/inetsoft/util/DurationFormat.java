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
package inetsoft.util;


import inetsoft.uql.XConstants;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.text.*;

public class DurationFormat extends NumberFormat {
   public DurationFormat() {
      super();
   }

   public DurationFormat(String format) {
      this();

      if(!Tool.isEmptyString(format)) {
         this.format = format;
      }
   }

   public DurationFormat(String format, boolean padWithZeros) {
      this(format);
      this.padWithZeros = padWithZeros;
   }

   @Override
   public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
      return format(Double.valueOf(number).longValue(), toAppendTo, pos);
   }

   @Override
   public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
      return new StringBuffer(DurationFormatUtils.formatDuration(number, format, padWithZeros));
   }

   @Override
   public StringBuffer format(Object number, StringBuffer toAppendTo, FieldPosition pos) {
      if(number == null || number instanceof String && Tool.isEmptyString((String) number)) {
         return toAppendTo;
      }

      if(number instanceof String) {
         try {
            number = Long.parseLong(number.toString());
         }
         catch(Exception ignore) {
         }
      }

      if(!(number instanceof Number)) {
         throw new NumberFormatException("Only number value is supported by Duration format!");
      }

      return super.format(number, toAppendTo, pos);
   }

   @Override
   public Number parse(String source, ParsePosition parsePosition) {
      throw new UnsupportedOperationException();
   }

   public String toPattern() {
      return format;
   }

   public String getFormatType() {
      return padWithZeros ? XConstants.DURATION_FORMAT : XConstants.DURATION_FORMAT_PAD_NON;
   }

   private String format = DEFAULT;
   private boolean padWithZeros = true;

   public static  final String DEFAULT = "dd HH:mm:ss";
}
