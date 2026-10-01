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

import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.*;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77513: NumberFormat.format(double) is final and tries the JDK fast path before the
 * overridden format(double, StringBuffer, FieldPosition), so the rounding option was skipped for
 * patterns such as "#,##0.###". The double results of the StringBuffer overload must not change.
 */
@Tag("core")
class RoundDecimalFormatRoundingModeTest {
   private static final DecimalFormatSymbols US = new DecimalFormatSymbols(Locale.US);

   private static final String[] MODES = {
      "ROUND_UP", "ROUND_DOWN", "ROUND_CEILING", "ROUND_FLOOR", "ROUND_HALF_UP",
      "ROUND_HALF_DOWN", "ROUND_HALF_EVEN", "ROUND_UNNECESSARY"
   };

   // patterns the JDK fast path accepts, and patterns it never accepts
   private static final String[] PATTERNS = {
      "#,##0.###", "$#,##0.###", "#,##0.###;(#,##0.###)", "#,##0.### units", "¤#,##0.00",
      "#,##0.##", "0.###", "#,##0", "0.0%", "#,##0.#%", "#,##0.###‰", "0.###E0", "##0.##E0"
   };

   private static final double[] VALUES = {
      0, 0.0005, -0.0005, 0.000426, 1.23456, -1.23456, 0.999999, -0.999999, 1.0005, 1.2355,
      -1.2355, 0.0625, 1.0625, -1.0625, 2.5, -2.5, 0.07, 0.12345, 1.0 / 3, 101.25, 12345.6789,
      -98765.4321,
      2147483646.98765, -2147483646.98765, 2147483647.98765, 1.0E10 + 0.12345
   };

   @Test
   void reporterExample() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");

      assertEquals("1.234", fmt.format(1.23456));
      assertEquals("1.234", fmt.format((Object) 1.23456));
      assertEquals("1.234", fmt.format(1.23456, new StringBuffer(), new FieldPosition(0)).toString());
      assertEquals("-1.234", fmt.format(-1.23456));
   }

   @Test
   void scriptFormatNumber() {
      assertEquals("1.234", JavaScriptEngine.formatNumber(1.23456, "#,##0.###", "ROUND_DOWN"));
      assertEquals("1.235", JavaScriptEngine.formatNumber(1.23456, "#,##0.###", "ROUND_HALF_EVEN"));
   }

   @Test
   void allConstructors() {
      RoundDecimalFormat noArg = new RoundDecimalFormat();
      noArg.setDecimalFormatSymbols(US);
      noArg.applyPattern("#,##0.###");
      RoundDecimalFormat pattern = new RoundDecimalFormat("#,##0.###");
      RoundDecimalFormat symbols = new RoundDecimalFormat("#,##0.###", US);

      for(RoundDecimalFormat fmt : new RoundDecimalFormat[] { noArg, pattern, symbols }) {
         fmt.setRounding(BigDecimal.ROUND_DOWN);
         assertEquals(fmt.format(0.999999, new StringBuffer(), new FieldPosition(0)).toString(),
                      fmt.format(0.999999));
      }

      assertEquals("0.999", symbols.format(0.999999));
      assertEquals("0.999", noArg.format(0.999999));
   }

   @Test
   void defaultConstructorPattern() {
      // the default pattern is the locale's ("#,##0.###" for most locales)
      RoundDecimalFormat fmt = new RoundDecimalFormat();
      fmt.setRounding(BigDecimal.ROUND_UP);
      assertEquals(fmt.format(1.0005, new StringBuffer(), new FieldPosition(0)).toString(),
                   fmt.format(1.0005));
   }

   @ParameterizedTest
   @CsvSource({
      "ROUND_DOWN, 1.23456, 1.234",
      "ROUND_FLOOR, 0.999999, 0.999",
      "ROUND_UP, 1.0005, 1.001",
      "ROUND_CEILING, -1.23456, -1.234",
      "ROUND_HALF_UP, 1.0625, 1.063",
      "ROUND_HALF_UP, -1.0625, -1.063",
      "ROUND_HALF_DOWN, 1.2355, 1.235",
      "ROUND_HALF_EVEN, 1.0625, 1.062",
      "ROUND_DOWN, 2147483646.98765, '2,147,483,646.987'",
      "ROUND_DOWN, 2147483647.98765, '2,147,483,647.987'"
   })
   void fastPathPatternHonoursRounding(String mode, double value, String expected) {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName(mode);
      assertEquals(expected, fmt.format(value));
   }

   @Test
   void currencyFastPathHonoursRounding() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("¤#,##0.00", US);
      fmt.setRoundingByName("ROUND_DOWN");
      assertEquals("$1.99", fmt.format(1.999));
   }

   @Test
   void roundUnnecessaryThrowsForDoubleThatNeedsRounding() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_UNNECESSARY");
      assertThrows(ArithmeticException.class, () -> fmt.format(1.23456));
      assertEquals("1.25", fmt.format(1.25));
   }

   /**
    * format(double) must match the StringBuffer overload, and the StringBuffer overload must
    * give the same result as before the fix: the value pre-rounded at the pattern text scale,
    * then formatted HALF_EVEN by a plain DecimalFormat. ROUND_HALF_EVEN keeps the JDK's own
    * format(double), whose fast path can differ from its StringBuffer overload (e.g. 0.0005).
    */
   @Test
   void doubleOutputMatchesPreviousBehaviour() {
      for(String pattern : PATTERNS) {
         for(String mode : MODES) {
            RoundDecimalFormat fmt = new RoundDecimalFormat(pattern, US);
            fmt.setRoundingByName(mode);

            for(double value : VALUES) {
               String expected = previous(pattern, fmt.getRounding(), value);
               String where = pattern + " " + mode + " " + value;
               String buffer;
               String plain;

               try {
                  buffer = fmt.format(value, new StringBuffer(), new FieldPosition(0)).toString();
               }
               catch(ArithmeticException ex) {
                  buffer = "ArithmeticException";
               }

               try {
                  plain = fmt.format(value);
               }
               catch(ArithmeticException ex) {
                  plain = "ArithmeticException";
               }

               assertEquals(expected, buffer, where);

               if(fmt.getRounding() == BigDecimal.ROUND_HALF_EVEN) {
                  // unchanged: the JDK's own format(double), fast path included
                  assertEquals(new DecimalFormat(pattern, US).format(value), plain, where);
               }
               else {
                  assertEquals(buffer, plain, where);
               }

               assertEquals(buffer, format(fmt, Double.valueOf(value)), where);
            }

            assertEquals(RoundingMode.valueOf(fmt.getRounding()), fmt.getRoundingMode(),
                         pattern + " " + mode);
         }
      }
   }

   @ParameterizedTest
   @CsvSource({
      "0.###E0, ROUND_DOWN, 101.25, 1.012E2",
      "0.###E0, ROUND_HALF_DOWN, 101.25, 1.012E2",
      "0.###E0, ROUND_FLOOR, 101.25, 1.012E2",
      "0.0%, ROUND_UP, 0.07, 7.0%",
      "0.0%, ROUND_UP, 0.13, 13.0%",
      "'#,##0.#%', ROUND_DOWN, 0.123456, 12%",
      "'#,##0.###;(#,##0.###)', ROUND_DOWN, 0.0005, 0",
      "'#,##0.###;(#,##0.###)', ROUND_HALF_DOWN, 0.0625, 0.062",
      "'#,##0.###;(#,##0.###)', ROUND_DOWN, -1.0625, (1.062)",
      "'#,##0.###;(#,##0.###)', ROUND_HALF_DOWN, -1.0625, (1.062)",
      "'#,##0.###;(#,##0.###)', ROUND_UP, 0.0625, 0.062"
   })
   void nonFastPathDoubleOutputUnchanged(String pattern, String mode, double value,
                                         String expected)
   {
      RoundDecimalFormat fmt = new RoundDecimalFormat(pattern, US);
      fmt.setRoundingByName(mode);
      assertEquals(expected,
                   fmt.format(value, new StringBuffer(), new FieldPosition(0)).toString());
      assertEquals(expected, format(fmt, Double.valueOf(value)));
   }

   @ParameterizedTest
   @CsvSource({
      "0.00, ROUND_DOWN, 1.005, 1.00",
      "0.00, ROUND_HALF_UP, 1.005, 1.01",
      "0.00, ROUND_HALF_DOWN, 1.005, 1.00",
      "0.00, ROUND_HALF_EVEN, 1.015, 1.02",
      "0.00, ROUND_UP, 1.001, 1.01",
      "0.00, ROUND_CEILING, -1.009, -1.00",
      "0.00, ROUND_FLOOR, -1.001, -1.01",
      "'#,##0.###', ROUND_DOWN, 2.0005, 2",
      "'#,##0.###', ROUND_HALF_UP, 2.0005, 2.001",
      "'#,##0.###;(#,##0.###)', ROUND_DOWN, -2.0005, (2)",
      "0.00, ROUND_UNNECESSARY, 1.5, 1.50"
   })
   void bigDecimalHonoursRounding(String pattern, String mode, String value, String expected) {
      RoundDecimalFormat fmt = new RoundDecimalFormat(pattern, US);
      fmt.setRoundingByName(mode);
      assertEquals(expected, format(fmt, new BigDecimal(value)));
   }

   @Test
   void bigDecimalRoundUnnecessaryThrowsWhenRoundingIsNeeded() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("0.00", US);
      fmt.setRoundingByName("ROUND_UNNECESSARY");
      assertThrows(ArithmeticException.class, () -> fmt.format(new BigDecimal("1.005")));
   }

   @Test
   void longOutputUnchangedWithoutRounding() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      assertEquals("1,234,567", fmt.format(1234567L));

      RoundDecimalFormat percent = new RoundDecimalFormat("#,##0.#%", US);
      percent.setRoundingByName("ROUND_UP");
      assertEquals("300%", percent.format(3L));
   }

   @Test
   void settingHalfEvenBackRestoresJdkMode() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      assertEquals(RoundingMode.DOWN, fmt.getRoundingMode());
      fmt.setRounding(BigDecimal.ROUND_HALF_EVEN);
      assertEquals(RoundingMode.HALF_EVEN, fmt.getRoundingMode());
      assertEquals("1.235", fmt.format(1.23456));
   }

   @Test
   void formatKeepsJdkModeAfterwards() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      fmt.format(1.23456);
      fmt.format(1.23456, new StringBuffer(), new FieldPosition(0));
      assertEquals(RoundingMode.DOWN, fmt.getRoundingMode());
   }

   @Test
   void fieldPositionIsReportedWithRounding() {
      // only the JDK's DontCareFieldPosition is replaced, a caller's FieldPosition is filled in
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      FieldPosition integer = new FieldPosition(NumberFormat.INTEGER_FIELD);
      FieldPosition fraction = new FieldPosition(NumberFormat.FRACTION_FIELD);
      FieldPosition grouping = new FieldPosition(NumberFormat.Field.GROUPING_SEPARATOR);

      assertEquals("1,234,567.898",
                   fmt.format(1234567.89876, new StringBuffer(), integer).toString());
      fmt.format(1234567.89876, new StringBuffer(), fraction);
      fmt.format(1234567.89876, new StringBuffer(), grouping);

      assertEquals(0, integer.getBeginIndex());
      assertEquals(9, integer.getEndIndex());
      assertEquals(10, fraction.getBeginIndex());
      assertEquals(13, fraction.getEndIndex());
      assertEquals(1, grouping.getBeginIndex());
      assertEquals(2, grouping.getEndIndex());
   }

   @ParameterizedTest
   @ValueSource(ints = { -1, 8, 100 })
   void setRoundingRejectsInvalidValue(int rounding) {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      assertThrows(IllegalArgumentException.class, () -> fmt.setRounding(rounding));
      // the previous option is kept
      assertEquals(BigDecimal.ROUND_DOWN, fmt.getRounding());
      assertEquals(RoundingMode.DOWN, fmt.getRoundingMode());
   }

   @Test
   void cloneKeepsRounding() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      RoundDecimalFormat copy = (RoundDecimalFormat) fmt.clone();
      assertEquals("1.234", copy.format(1.23456));
   }

   @Test
   void serializationKeepsRounding() throws Exception {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      RoundDecimalFormat copy = roundTrip(fmt);
      assertEquals(BigDecimal.ROUND_DOWN, copy.getRounding());
      assertEquals("1.234", copy.format(1.23456));
   }

   @Test
   void deserializedOldFormatFollowsRoundingOption() throws Exception {
      // a format serialized before the fix has the rounding option and a HALF_EVEN JDK mode
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.###", US);
      fmt.setRoundingByName("ROUND_DOWN");
      fmt.setRoundingMode(RoundingMode.HALF_EVEN);
      RoundDecimalFormat copy = roundTrip(fmt);
      assertEquals(RoundingMode.DOWN, copy.getRoundingMode());
      assertEquals("1.234", copy.format(1.23456));
   }

   // the double formatting before the fix: pre-round at the pattern text scale, then HALF_EVEN
   private static String previous(String pattern, int rounding, double num) {
      DecimalFormat fmt = new DecimalFormat(pattern, US);

      try {
         if(rounding != BigDecimal.ROUND_HALF_EVEN) {
            String fmtstr = fmt.toPattern();
            int idx = fmtstr.lastIndexOf(".");

            if(idx >= 0 || ("" + num).lastIndexOf(".") >= 0) {
               int scale = idx >= 0 ? fmtstr.length() - idx - 1 : 0;
               num = new BigDecimal(Double.toString(num)).setScale(scale, rounding).doubleValue();
            }
         }
      }
      catch(ArithmeticException ex) {
         return "ArithmeticException";
      }

      return fmt.format(num, new StringBuffer(), new FieldPosition(0)).toString();
   }

   private static String format(Format fmt, Object value) {
      try {
         return fmt.format(value);
      }
      catch(ArithmeticException ex) {
         return "ArithmeticException";
      }
   }

   private static RoundDecimalFormat roundTrip(RoundDecimalFormat fmt) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(fmt);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (RoundDecimalFormat) in.readObject();
      }
   }
}
