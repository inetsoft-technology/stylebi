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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ObjectStreamClass;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.util.Locale;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class RoundDecimalFormatTest {

   // ---- Constructor tests ----

   @Test
   void defaultConstructorUsesHalfEven() {
      RoundDecimalFormat fmt = new RoundDecimalFormat();
      assertEquals(BigDecimal.ROUND_HALF_EVEN, fmt.getRounding());
   }

   @Test
   void patternConstructorUsesHalfEven() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      assertEquals(BigDecimal.ROUND_HALF_EVEN, fmt.getRounding());
   }

   // ---- getRounding / setRounding tests ----

   @Test
   void setAndGetRounding() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      assertEquals(BigDecimal.ROUND_HALF_UP, fmt.getRounding());
   }

   // ---- setRoundingByName tests ----

   @ParameterizedTest
   @CsvSource({
      "ROUND_UP," + BigDecimal.ROUND_UP,
      "ROUND_DOWN," + BigDecimal.ROUND_DOWN,
      "ROUND_CEILING," + BigDecimal.ROUND_CEILING,
      "ROUND_FLOOR," + BigDecimal.ROUND_FLOOR,
      "ROUND_HALF_UP," + BigDecimal.ROUND_HALF_UP,
      "ROUND_HALF_DOWN," + BigDecimal.ROUND_HALF_DOWN,
      "ROUND_HALF_EVEN," + BigDecimal.ROUND_HALF_EVEN,
      "ROUND_UNNECESSARY," + BigDecimal.ROUND_UNNECESSARY
   })
   void setRoundingByName(String name, int expected) {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      fmt.setRoundingByName(name);
      assertEquals(expected, fmt.getRounding());
   }

   @Test
   void setRoundingByNameInvalidThrows() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      assertThrows(RuntimeException.class, () -> fmt.setRoundingByName("INVALID"));
   }

   // ---- Default HALF_EVEN rounding (same as DecimalFormat) ----

   @Test
   void defaultRoundingMatchesDecimalFormat() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      DecimalFormat base = new DecimalFormat("#.##");
      // Both should behave identically with ROUND_HALF_EVEN
      assertEquals(base.format(2.555), fmt.format(2.555));
      assertEquals(base.format(2.545), fmt.format(2.545));
   }

   @Test
   void defaultRoundingZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      assertEquals("0", fmt.format(0.0));
   }

   @Test
   void defaultRoundingNegative() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      DecimalFormat base = new DecimalFormat("#.##");
      assertEquals(base.format(-2.555), fmt.format(-2.555));
   }

   // ---- ROUND_HALF_UP tests ----

   @Test
   void halfUpRoundsHalfAwayFromZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      // 2.25 -> 2.3 with ROUND_HALF_UP
      assertEquals("2.3", fmt.format(2.25));
   }

   @Test
   void halfUpRoundsNegativeHalf() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      // -2.25 -> -2.3 with ROUND_HALF_UP (away from zero means more negative)
      assertEquals("-2.3", fmt.format(-2.25));
   }

   @Test
   void halfUpTwoDecimalPlaces() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      assertEquals("1.24", fmt.format(1.235));
   }

   // ---- ROUND_HALF_DOWN tests ----

   @Test
   void halfDownRoundsHalfTowardZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_HALF_DOWN);
      // 2.25 -> 2.2 with ROUND_HALF_DOWN (toward zero)
      assertEquals("2.2", fmt.format(2.25));
   }

   // ---- ROUND_UP tests ----

   @Test
   void roundUpAlwaysRoundsAwayFromZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_UP);
      assertEquals("2.3", fmt.format(2.21));
      assertEquals("2.3", fmt.format(2.29));
   }

   @Test
   void roundUpNegative() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_UP);
      assertEquals("-2.3", fmt.format(-2.21));
   }

   // ---- ROUND_DOWN tests ----

   @Test
   void roundDownAlwaysTruncates() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_DOWN);
      assertEquals("2.2", fmt.format(2.29));
      assertEquals("2.2", fmt.format(2.21));
   }

   @Test
   void roundDownNegative() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_DOWN);
      assertEquals("-2.2", fmt.format(-2.29));
   }

   // ---- ROUND_CEILING tests ----

   @Test
   void roundCeilingPositiveRoundsUp() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_CEILING);
      assertEquals("2.3", fmt.format(2.21));
   }

   @Test
   void roundCeilingNegativeRoundsTowardZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_CEILING);
      assertEquals("-2.2", fmt.format(-2.29));
   }

   // ---- ROUND_FLOOR tests ----

   @Test
   void roundFloorPositiveRoundsDown() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_FLOOR);
      assertEquals("2.2", fmt.format(2.29));
   }

   @Test
   void roundFloorNegativeRoundsAwayFromZero() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.#");
      fmt.setRounding(BigDecimal.ROUND_FLOOR);
      assertEquals("-2.3", fmt.format(-2.21));
   }

   // ---- Integer patterns (no decimal) ----

   @Test
   void integerPatternWithDecimalInput() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      // 2.5 rounds to 3
      assertEquals("3", fmt.format(2.5));
   }

   @Test
   void integerPatternWithDecimalInputHalfEven() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0");
      // ROUND_HALF_EVEN is default - delegates to super.format without BigDecimal rounding
      // since idx < 0 and no custom rounding is applied
      String result = fmt.format(2.5);
      assertNotNull(result);
   }

   // ---- Large numbers ----

   @Test
   void largeNumberFormatting() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.00");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      assertEquals("1,234,567.89", fmt.format(1234567.891));
   }

   @Test
   void largeNegativeNumberFormatting() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#,##0.00");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      assertEquals("-1,234,567.89", fmt.format(-1234567.891));
   }

   // ---- Zero ----

   @Test
   void zeroWithDecimals() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("0.000");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      assertEquals("0.000", fmt.format(0.0));
   }

   // ---- StringBuffer/FieldPosition form ----

   @Test
   void formatWithStringBufferAndFieldPosition() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("#.##");
      fmt.setRounding(BigDecimal.ROUND_HALF_UP);
      StringBuffer sb = new StringBuffer();
      FieldPosition fp = new FieldPosition(0);
      StringBuffer result = fmt.format(1.235, sb, fp);
      assertSame(sb, result);
      assertEquals("1.24", result.toString());
   }

   // ---- Bug #77500, an empty pattern must not leave unlimited fraction digits ----

   private static final int DOUBLE_FRACTION_DIGITS = 340;
   private static final String[] ROUNDINGS = {
      "ROUND_UP", "ROUND_DOWN", "ROUND_CEILING", "ROUND_FLOOR", "ROUND_HALF_UP",
      "ROUND_HALF_DOWN", "ROUND_HALF_EVEN", "ROUND_UNNECESSARY"
   };
   private static final double[] VALUES = {
      1.23456, 0.3, 0.0625, 1.9999, 2.0005, 1234.5, -1.23456, 1234567.891, 1e-300, 1e300
   };

   // the fraction digits are checked before toPattern() so a regression fails instead of
   // exhausting the heap
   private static void assertBounded(RoundDecimalFormat fmt) {
      assertEquals(DOUBLE_FRACTION_DIGITS, fmt.getMaximumFractionDigits());
      assertTrue(fmt.toPattern().length() < 400, "toPattern() length " + fmt.toPattern().length());
      assertTrue(fmt.toLocalizedPattern().length() < 400);
   }

   @Test
   void emptyPatternConstructorBoundsFractionDigits() {
      assertBounded(new RoundDecimalFormat(""));
   }

   @Test
   void emptyPatternSymbolsConstructorBoundsFractionDigits() {
      assertBounded(new RoundDecimalFormat("", new DecimalFormatSymbols(Locale.US)));
   }

   @Test
   void applyEmptyPatternBoundsFractionDigits() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("0.00");
      fmt.applyPattern("");
      assertBounded(fmt);
   }

   @Test
   void applyEmptyLocalizedPatternBoundsFractionDigits() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));
      fmt.applyLocalizedPattern("");
      assertBounded(fmt);
   }

   /**
    * An empty pattern has no fixed scale, so with any rounding option it must format the same as a
    * plain DecimalFormat(""). A "#,##0.###" substitute would fail here: it rounds to 3 digits, and
    * the JDK fast path for that exact pattern ignores the rounding option.
    */
   @Test
   void emptyPatternWithRoundingMatchesPlainEmptyFormat() {
      for(String rounding : ROUNDINGS) {
         RoundDecimalFormat fmt = new RoundDecimalFormat("");
         fmt.setRoundingByName(rounding);
         assertEquals(DOUBLE_FRACTION_DIGITS, fmt.getMaximumFractionDigits());

         for(double value : VALUES) {
            assertEquals(new DecimalFormat("").format(value), fmt.format(value),
                         rounding + " " + value);
         }
      }

      RoundDecimalFormat down = new RoundDecimalFormat("");
      down.setRoundingByName("ROUND_DOWN");
      assertEquals("1.23456", down.format(1.23456));
      assertEquals(".3", down.format(0.3));
   }

   @Test
   void roundingModesStillApplyToThreeDigitPattern() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("0.000");
      fmt.setRoundingByName("ROUND_DOWN");
      assertEquals("1.234", fmt.format(1.2349));
      fmt.setRoundingByName("ROUND_UP");
      assertEquals("1.235", fmt.format(1.2341));
      // 0.0625 is an exact binary tie at 3 digits
      fmt.setRoundingByName("ROUND_HALF_UP");
      assertEquals("0.063", fmt.format(0.0625));
      fmt.setRoundingByName("ROUND_HALF_EVEN");
      assertEquals("0.062", fmt.format(0.0625));
   }

   @Test
   void nonEmptyPatternAfterEmptyPatternIsUnchanged() {
      RoundDecimalFormat fmt = new RoundDecimalFormat("");
      fmt.applyPattern("0.00");
      assertEquals(2, fmt.getMaximumFractionDigits());
      assertEquals(new DecimalFormat("0.00").toPattern(), fmt.toPattern());
   }

   @Test
   void nonEmptyPatternsAreUnchanged() {
      RoundDecimalFormat fmt = new RoundDecimalFormat(" ");
      assertEquals(new DecimalFormat(" ").toPattern(), fmt.toPattern());
      assertEquals(0, fmt.getMaximumFractionDigits());
      fmt.applyPattern("0.0000");
      assertEquals(new DecimalFormat("0.0000").toPattern(), fmt.toPattern());
      assertThrows(NullPointerException.class, () -> new RoundDecimalFormat((String) null));
   }

   @Test
   void serialVersionUIDIsUnchanged() {
      assertEquals(3176001257543027570L,
                   ObjectStreamClass.lookup(RoundDecimalFormat.class).getSerialVersionUID());
   }
}
