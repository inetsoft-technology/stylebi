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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            requireDecimal(text);
            double d = Double.parseDouble(text);

            if(Double.isNaN(d) || Double.isInfinite(d)) {
               throw new IllegalArgumentException("not a finite number");
            }

            return d;
         }
         else if(type.equalsIgnoreCase(Tool.FLOAT)) {
            requireDecimal(text);
            float f = Float.parseFloat(text);

            if(Float.isNaN(f) || Float.isInfinite(f)) {
               throw new IllegalArgumentException("not a finite float");
            }

            if(f == 0f && new BigDecimal(text).signum() != 0) {
               throw new IllegalArgumentException("too small for a float");
            }

            return f;
         }
         else if(type.equalsIgnoreCase(Tool.DATE)) {
            requireDate(text, Tool.DATE);
            return requireTemporal(Tool.getData(Tool.DATE, text, true));
         }
         else if(type.equalsIgnoreCase(Tool.TIME_INSTANT)) {
            requireDate(text, Tool.TIME_INSTANT);
            return requireTemporal(Tool.getData(Tool.TIME_INSTANT, text, true));
         }
         else if(type.equalsIgnoreCase(Tool.TIME)) {
            requireDate(text, Tool.TIME);
            return requireTemporal(Tool.getData(Tool.TIME, text, true));
         }
      }
      catch(IllegalArgumentException | ArithmeticException ex) {
         throw new IllegalArgumentException(
            "'" + value + "' is not a valid " + type + " value.", ex);
      }

      throw new IllegalArgumentException(
         "Unknown data type '" + dataType + "' for value '" + value + "'.");
   }

   private static final Pattern DECIMAL =
      Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
   private static final Pattern DATE_PART =
      Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T].*)?$");
   private static final Pattern TIME_ONLY =
      Pattern.compile("(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2})(?:\\.\\d+)?)?");
   private static final Pattern TIME_PART = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");

   /** Java's own double grammar also takes hex floats, "NaN", and "1d"/"1f" suffixes. */
   private static void requireDecimal(String text) {
      if(!DECIMAL.matcher(text).matches()) {
         throw new IllegalArgumentException("not a plain decimal number");
      }
   }

   /**
    * {@code Tool.getData} hands back the raw input string when a date will not parse, and its
    * formats roll impossible values over ({@code 2026-02-30} -> Feb 28, {@code 25:61:00} -> 02:01)
    * rather than rejecting them.
    */
   private static Object requireTemporal(Object parsed) {
      if(!(parsed instanceof java.util.Date)) {
         throw new IllegalArgumentException("not a recognizable date/time");
      }

      return parsed;
   }

   private static void requireCalendar(String text) {
      Matcher date = DATE_PART.matcher(text);

      try {
         if(date.matches()) {
            java.time.LocalDate.of(Integer.parseInt(date.group(1)),
                                   Integer.parseInt(date.group(2)),
                                   Integer.parseInt(date.group(3)));
         }

         Matcher time = TIME_PART.matcher(text);

         if(time.find() && (Integer.parseInt(time.group(1)) > 23 ||
            Integer.parseInt(time.group(2)) > 59 || Integer.parseInt(time.group(3)) > 59))
         {
            throw new IllegalArgumentException("not a real clock time");
         }
      }
      catch(java.time.DateTimeException ex) {
         throw new IllegalArgumentException("not a real calendar date", ex);
      }
   }

   /**
    * Each temporal type accepts only its own shape, so {@code Tool}'s cross-type fallbacks cannot
    * turn {@code 10:30:00} into a date or {@code 2026-01-31} into midnight.
    */
   private static void requireDate(String text, String type) {
      boolean hasDate = DATE_PART.matcher(text).matches();

      if(Tool.TIME.equals(type)) {
         Matcher m = TIME_ONLY.matcher(text);

         if(!m.matches()) {
            throw new IllegalArgumentException("not a time of day (hh:mm[:ss[.fff]])");
         }

         if(Integer.parseInt(m.group(1)) > 23 || Integer.parseInt(m.group(2)) > 59 ||
            m.group(3) != null && Integer.parseInt(m.group(3)) > 59)
         {
            throw new IllegalArgumentException("not a real clock time");
         }

         return;
      }
      else if(!hasDate) {
         throw new IllegalArgumentException("not a date (yyyy-MM-dd[ hh:mm:ss])");
      }

      requireCalendar(text);
   }
}
