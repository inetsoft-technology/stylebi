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
package inetsoft.report.internal.table;

import inetsoft.uql.util.XUtil;
import inetsoft.util.DurationFormat;
import inetsoft.util.Tool;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Invariants of user-defined format patterns, shared by {@code FormatSpecSeedTest} and the
 * enterprise fuzzer ({@code test/fuzzer}).
 * <p>
 * An input is the format type ({@code DateFormat}, {@code DecimalFormat}, ...) on the first
 * line and the pattern on the rest, as stored in a viewsheet. For each test locale,
 * {@link #check(String)} builds the format the way rendering does
 * ({@link TableFormat#getFormat(String, String, Locale)}), which may reject the pattern, and
 * then verifies that:
 * <ol>
 *    <li>formatting representative values of the matching type with
 *        {@link XUtil#format(Format, Object)} never throws, except for the JDK's own
 *        argument type check when a message format argument is, for example, a string
 *        given to {@code {0,number}};</li>
 *    <li>parsing the formatted text back with {@code parseObject(text, ParsePosition)}
 *        never throws (a parse failure is fine; duration formats do not parse);</li>
 *    <li>no single step takes longer than {@link #STEP_LIMIT_MS}.</li>
 * </ol>
 */
public final class FormatSpecProperties {
   private FormatSpecProperties() {
   }

   /**
    * @return false if the input does not name a known type and was skipped.
    */
   public static boolean check(String input) throws Exception {
      int newline = input.indexOf('\n');

      if(input.length() > MAX_INPUT || newline < 0) {
         return false;
      }

      String type = input.substring(0, newline).strip();
      String spec = input.substring(newline + 1);

      if(!TYPES.contains(type)) {
         return false;
      }

      for(Locale locale : LOCALES) {
         String where = type + " \"" + spec + "\" in " + locale;
         Format format;

         try {
            format = timed(where, () -> TableFormat.getFormat(type, spec, locale));
         }
         finally {
            // getFormat() reports rejected patterns as user messages, which would pile up
            Tool.clearUserMessage();
         }

         if(format == null) {
            continue;
         }

         for(Object value : valuesFor(format)) {
            String what = where + " formatting " + describe(value);
            String text;

            try {
               text = timed(what, () -> XUtil.format(format, value));
            }
            catch(AssertionError ex) {
               if(isArgumentTypeMismatch(format, ex.getCause())) {
                  continue;
               }

               throw ex;
            }

            if(text != null && !(format instanceof DurationFormat) &&
               (format instanceof DateFormat || format instanceof NumberFormat))
            {
               timed(what + " and parsing \"" + text + "\"",
                     () -> format.parseObject(text, new ParsePosition(0)));
            }
         }
      }

      return true;
   }

   private static List<Object> valuesFor(Format format) {
      if(format instanceof DateFormat) {
         // small integers are formatted as date parts, see XUtil.format()
         List<Object> values = new ArrayList<>(DATES);
         values.addAll(List.of(0, 5, 11, 999));
         return values;
      }
      else if(format instanceof NumberFormat) {
         return NUMBERS;
      }

      List<Object> values = new ArrayList<>();
      values.add("text");
      values.add(42);
      values.add(DATES.get(1));
      values.add(new Object[] { "a", 1.5, DATES.get(1) });
      return values;
   }

   private static boolean isArgumentTypeMismatch(Format format, Throwable ex) {
      return format instanceof inetsoft.util.MessageFormat &&
         ex instanceof IllegalArgumentException && ex.getMessage() != null &&
         ex.getMessage().startsWith("Cannot format given Object as a");
   }

   private static <T> T timed(String what, Callable<T> step) throws Exception {
      long start = System.nanoTime();
      T result;

      try {
         result = step.call();
      }
      catch(Throwable ex) {
         throw new AssertionError(what + " threw " + ex, ex);
      }

      long millis = (System.nanoTime() - start) / 1_000_000;

      if(millis > STEP_LIMIT_MS) {
         throw new AssertionError(what + " took " + millis + " ms");
      }

      return result;
   }

   private static String describe(Object value) {
      return value instanceof Object[] ? Arrays.toString((Object[]) value) :
         value.getClass().getSimpleName() + " " + value;
   }

   public static final int MAX_INPUT = 500;
   public static final long STEP_LIMIT_MS = 1000;

   private static final Set<String> TYPES = Set.of(
      TableFormat.DATE_FORMAT, TableFormat.TIME_FORMAT, TableFormat.TIMEINSTANT_FORMAT,
      TableFormat.DECIMAL_FORMAT, TableFormat.CURRENCY_FORMAT, TableFormat.PERCENT_FORMAT,
      TableFormat.MESSAGE_FORMAT, TableFormat.DURATION_FORMAT,
      TableFormat.DURATION_FORMAT_PAD_NON);

   private static final List<Locale> LOCALES = List.of(
      Locale.US, Locale.GERMANY, Locale.JAPAN, Locale.forLanguageTag("ar-EG"));

   private static final List<Object> NUMBERS = List.of(
      0, -1.5, 1234567.891, 1e300, -1e-300, Double.NaN, Double.POSITIVE_INFINITY,
      Long.MAX_VALUE, Integer.MIN_VALUE, new BigDecimal("12345678901234567890.123456789"));

   private static final List<Object> DATES = List.of(
      new Date(0),
      Timestamp.valueOf("2024-02-29 13:45:59.999"),
      java.sql.Date.valueOf("0001-01-01"),
      java.sql.Date.valueOf("9999-12-31"),
      java.sql.Time.valueOf("23:59:59"));
}
