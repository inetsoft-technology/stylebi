/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.util.Tool;

import java.math.BigDecimal;

/**
 * A strict string-to-typed-value parse for wiz tools that accept literal values.
 *
 * <p>{@link Tool#getData(String, String, boolean)} is built for lenient UI/data conversion: a
 * {@code NumberFormatException} becomes {@code null}, any non-boolean text becomes
 * {@code FALSE}, {@code "1.5"} for an integer is truncated to 1, and byte/short wrap. A wiz
 * tool that stores whatever that returns accepts bad input with {@code ok:true} and a silently
 * different value. This parser instead throws {@link IllegalArgumentException} naming the value
 * and the type, and otherwise returns the same typed value {@code Tool.getData} would.
 *
 * <p>{@code Tool.FAKE_NULL} ({@code "__null__"}) is a deliberate null and returns {@code null};
 * so does a Java {@code null}. A {@code null}/blank dataType means string.
 */
public final class WizStrictValueParser {
   private WizStrictValueParser() {
   }

   /**
    * @param value    the literal text.
    * @param dataType a StyleBI data type name (string, integer, double, float, boolean,
    *                 character/char, byte, short, long, date, time, timeInstant); case-insensitive.
    * @return the typed value, or {@code null} for a null/{@code __null__} input.
    * @throws IllegalArgumentException if {@code value} is not a valid {@code dataType}, or the
    *                                  type is unknown.
    */
   public static Object parse(String value, String dataType) {
      if(value == null || Tool.FAKE_NULL.equals(value)) {
         return null;
      }

      String type = dataType == null || dataType.isBlank() ? Tool.STRING : dataType.trim();
      String text = value.trim();

      try {
         if(type.equalsIgnoreCase(Tool.STRING)) {
            return value;
         }
         else if(type.equalsIgnoreCase(Tool.CHARACTER) || type.equalsIgnoreCase(Tool.CHAR)) {
            if(value.length() != 1) {
               throw new IllegalArgumentException("not a single character");
            }

            return value.charAt(0);
         }
         else if(type.equalsIgnoreCase(Tool.BOOLEAN)) {
            if(text.equalsIgnoreCase("true")) {
               return Boolean.TRUE;
            }
            else if(text.equalsIgnoreCase("false")) {
               return Boolean.FALSE;
            }

            throw new IllegalArgumentException("must be true or false");
         }
         else if(type.equalsIgnoreCase(Tool.INTEGER)) {
            return new BigDecimal(text).intValueExact();
         }
         else if(type.equalsIgnoreCase(Tool.SHORT)) {
            return new BigDecimal(text).shortValueExact();
         }
         else if(type.equalsIgnoreCase(Tool.BYTE)) {
            return new BigDecimal(text).byteValueExact();
         }
         else if(type.equalsIgnoreCase(Tool.LONG)) {
            return new BigDecimal(text).longValueExact();
         }
         else if(type.equalsIgnoreCase(Tool.DOUBLE)) {
            double d = Double.parseDouble(text);

            if(Double.isNaN(d) || Double.isInfinite(d)) {
               throw new IllegalArgumentException("not a finite number");
            }

            return d;
         }
         else if(type.equalsIgnoreCase(Tool.FLOAT)) {
            float f = Float.parseFloat(text);

            if(Float.isNaN(f) || Float.isInfinite(f)) {
               throw new IllegalArgumentException("not a finite float");
            }

            return f;
         }
         else if(type.equalsIgnoreCase(Tool.DATE)) {
            requireDate(text, Tool.DATE);
            return Tool.getData(Tool.DATE, text, true);
         }
         else if(type.equalsIgnoreCase(Tool.TIME_INSTANT)) {
            requireDate(text, Tool.TIME_INSTANT);
            return Tool.getData(Tool.TIME_INSTANT, text, true);
         }
         else if(type.equalsIgnoreCase(Tool.TIME)) {
            requireDate(text, Tool.TIME);
            return Tool.getData(Tool.TIME, text, true);
         }
      }
      catch(IllegalArgumentException | ArithmeticException ex) {
         throw new IllegalArgumentException(
            "'" + value + "' is not a valid " + type + " value.", ex);
      }

      throw new IllegalArgumentException(
         "Unknown data type '" + dataType + "' for value '" + value + "'.");
   }

   private static void requireDate(String text, String type) {
      try {
         if(Tool.DATE.equals(type)) {
            try {
               Tool.parseDate(text);
            }
            catch(Exception ex) {
               try {
                  Tool.parseDateTime(text);
               }
               catch(Exception ex2) {
                  Tool.parseTime(text);
               }
            }
         }
         else if(Tool.TIME_INSTANT.equals(type)) {
            try {
               Tool.parseDateTime(text);
            }
            catch(Exception ex) {
               try {
                  Tool.parseDate(text);
               }
               catch(Exception ex2) {
                  Tool.parseTime(text);
               }
            }
         }
         else {
            Tool.parseTime(text);
         }
      }
      catch(Exception ex) {
         throw new IllegalArgumentException("not a recognizable " + type, ex);
      }
   }
}
