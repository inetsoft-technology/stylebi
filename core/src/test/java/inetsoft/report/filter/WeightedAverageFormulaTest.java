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
package inetsoft.report.filter;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeightedAverageFormula}, Bug #80584.
 */
@Tag("core")
class WeightedAverageFormulaTest {
   private static final double DELTA = 1e-9;

   @Test
   void addValue_numericStrings_computesWeightedAverage() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { "10", "0.5" });
      f.addValue(new Object[] { "20", "0.5" });
      assertEquals(15.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }

   @Test
   void addValue_blankStringInEitherPosition_pairIsSkipped() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { "", "0.5" });
      f.addValue(new Object[] { "  ", "0.5" });
      f.addValue(new Object[] { "10", "" });
      f.addValue(new Object[] { "20", "0.2" });
      assertEquals(20.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }

   @Test
   void addValue_nullAndNonNumericText_pairIsSkipped() {
      WeightedAverageFormula f = new WeightedAverageFormula();
      f.addValue(new Object[] { null, "0.5" });
      f.addValue(new Object[] { "abc", "0.5" });
      f.addValue(new Object[] { 4, 2 });
      assertEquals(4.0, ((Number) f.getResult()).doubleValue(), 0.0001);
   }

   // gap:report.summary-formulas.arithmetic#weighted-average-weights-second-column
   @Test
   void getResult_valuesWeightedBySecondColumn_returnsWeightedAverage() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(2);
      assertArrayEquals(new int[] { 2 }, weighted.getSecondaryColumns());
      assertTrue(weighted.isNull());

      // sum(value * weight) / sum(weight)
      addPairs(weighted, pair(-12, 0.5), pair(1, 0.1), pair(20, 0.2), pair(150, 0.01),
               pair(10, 0.19));
      assertEquals(1.5, asDouble(weighted.getResult()), DELTA);
      assertEquals(1.5, weighted.getDoubleResult(), DELTA);
      assertFalse(weighted.isNull());

      // the weight, not the count, decides
      weighted.reset();
      addPairs(weighted, pair(10, 1), pair(20, 3));
      assertEquals(17.5, weighted.getResult());

      // numeric strings in the pair
      weighted.reset();
      addPairs(weighted, pair("10", "1"), pair("20", "3"), pair(30, "4"));
      assertEquals(23.75, weighted.getResult());

      // the double array path: {value, weight}
      weighted.reset();
      weighted.addValue(new double[] { 10.0, 1.0 });
      weighted.addValue(new double[] { 20.0, 3.0 });
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, 5.0 });
      weighted.addValue(new double[] { 99.0, Tool.NULL_DOUBLE });
      assertEquals(17.5, weighted.getResult());
      assertEquals(17.5, weighted.getDoubleResult());

      // a clone keeps the column
      WeightedAverageFormula copy = (WeightedAverageFormula) weighted.clone();
      assertArrayEquals(new int[] { 2 }, copy.getSecondaryColumns());
      assertEquals(17.5, copy.getResult());

      // a value alone is not a pair
      assertThrows(RuntimeException.class, () -> weighted.addValue(5.0));
   }

   // Bug #80540: gap:report.summary-formulas.arithmetic#weighted-average-blank-text-gives-nan
   @Test
   void addValue_blankOrTextInPair_pairIsSkipped() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair("", "0.5"), pair("1", "a"), pair("20", "0.2"),
               pair("150", "0.01"), pair("10", "0.19"));
      // the blank pair and the text weight are skipped
      assertEquals(18.5, asDouble(weighted.getResult()), DELTA);

      weighted.reset();
      addPairs(weighted, pair(10, " "), pair(20, 2));
      assertEquals(20.0, weighted.getResult());

      weighted.reset();
      addPairs(weighted, pair(" ", 3), pair("", ""));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
   }

   // gap:report.summary-formulas.arithmetic#weighted-average-non-numeric-input-null
   @Test
   void getResult_noNumericPair_returnsNull() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // date pairs are not numbers
      addPairs(weighted, pair(new Date(1_000_000L), new Date(2_000_000L)),
               pair(new Date(3_000_000L), new Date(4_000_000L)));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // a list of nulls, and nulls inside the pairs, and a null list
      weighted.reset();
      addAll(weighted, null, null, null, null);
      addPairs(weighted, pair(null, null), pair(null, 2), pair(3, null));
      weighted.addValue((double[]) null);
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, Tool.NULL_DOUBLE });
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());

      // a value that is not a pair is ignored
      weighted.addValue((Object) "text");
      weighted.addValue((Object) 5);
      assertTrue(weighted.isNull());

      // the default-result flag turns null into 0
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      weighted.setDefaultResult(false);

      // a pair ends it
      addPairs(weighted, pair(4, 1));
      assertFalse(weighted.isNull());
      assertEquals(4.0, weighted.getResult());
      weighted.reset();
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
   }

   // gap:report.summary-formulas.arithmetic#weighted-average-skips-incomplete-pairs
   @Test
   void addValue_incompletePairs_skipped() {
      // the pair with a null value is skipped
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(null, 0.5), pair(1, 0.1), pair(20, 0.2), pair(150, 0.01),
               pair(10, 0.19));
      assertEquals(15.0, asDouble(weighted.getResult()), DELTA);

      // a text value, and a null weight
      weighted.reset();
      addPairs(weighted, pair("a", "0.5"), pair("1", null), pair("20", "0.2"),
               pair("150", "0.01"), pair("10", "0.19"));
      assertEquals(18.5, asDouble(weighted.getResult()), DELTA);

      // a text weight
      weighted.reset();
      addPairs(weighted, pair(5, "heavy"), pair(10, 2), pair(20, 2));
      assertEquals(15.0, weighted.getResult());

      // only skipped pairs leave nothing
      weighted.reset();
      addPairs(weighted, pair(null, 1), pair(1, null), pair("a", 1), pair(1, "b"));
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());

      // the double array form skips the NULL markers
      weighted.reset();
      weighted.addValue(new double[] { Tool.NULL_DOUBLE, 1.0 });
      weighted.addValue(new double[] { 1.0, Tool.NULL_DOUBLE });
      weighted.addValue(new double[] { 6.0, 2.0 });
      assertEquals(6.0, weighted.getResult());
      assertEquals(6.0, weighted.getDoubleResult());
   }

   // gap:report.summary-formulas.arithmetic#weighted-average-zero-weight-sum
   @Test
   void getResult_zeroWeightSum_returnsNull() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(5, 0), pair(7, 0));
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());

      // the default-result flag turns null into 0
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());
      weighted.setDefaultResult(false);

      // weights that cancel out
      weighted.reset();
      addPairs(weighted, pair(5, 1), pair(7, -1));
      assertNull(weighted.getResult());
      assertEquals(0.0, weighted.getDoubleResult());
      weighted.setDefaultResult(true);
      assertEquals(0.0, weighted.getResult());
      weighted.setDefaultResult(false);

      // the double array form
      weighted.reset();
      weighted.addValue(new double[] { 5.0, 0.0 });
      weighted.addValue(new double[] { 7.0, 0.0 });
      assertNull(weighted.getResult());

      // a weight that is not 0 gives a result again
      weighted.addValue(new double[] { 9.0, 3.0 });
      assertEquals(9.0, weighted.getResult());
   }

   // gap:report.summary-formulas.common#default-result-returns-zero
   @Test
   void getResult_noValues_defaultResultTurnsNullIntoZero() {
      WeightedAverageFormula weighted = new WeightedAverageFormula();

      // nothing to aggregate: null by default, 0 once the default result is on
      assertNull(weighted.getResult());
      weighted.setDefaultResult(true);
      assertTrue(weighted.isDefaultResult());
      assertEquals(Double.valueOf(0), weighted.getResult());
      weighted.setDefaultResult(false);
      assertNull(weighted.getResult());
   }

   // gap:report.summary-formulas.common#second-column-formulas-need-pairs
   @Test
   void addValue_singleValueWithoutPair_throws() {
      WeightedAverageFormula secondColumn = new WeightedAverageFormula(3);
      assertArrayEquals(new int[] { 3 }, secondColumn.getSecondaryColumns());

      // one value alone has no second column to go with it
      for(Runnable single : new Runnable[] {
         () -> secondColumn.addValue(1.0), () -> secondColumn.addValue(1.0f),
         () -> secondColumn.addValue(1L), () -> secondColumn.addValue(1),
         () -> secondColumn.addValue((short) 1)})
      {
         RuntimeException ex = assertThrows(RuntimeException.class, single::run);
         assertEquals("Unsupported method called!", ex.getMessage());
      }

      // the value and its second column value as a pair are taken
      WeightedAverageFormula weighted = new WeightedAverageFormula(3);
      weighted.addValue(new Object[] { 10, 1 });
      weighted.addValue(new Object[] { 40, 2 });
      assertEquals(30.0, ((Number) weighted.getResult()).doubleValue(), DELTA);

      // a pair of nulls, or no pair, adds nothing
      WeightedAverageFormula empty = new WeightedAverageFormula(3);
      empty.addValue(new Object[] { null, null });
      empty.addValue("not a pair");
      empty.addValue((Object) null);
      assertNull(empty.getResult());

      // the default constructors use the first column after the primary one
      assertArrayEquals(new int[] { 0 }, new WeightedAverageFormula().getSecondaryColumns());
   }

   // gap:report.summary-formulas.common#formula-reused-after-reset
   @Test
   void reset_afterPair_startsOver() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      weighted.addValue(new Object[] { 10, 1 });
      assertFalse(weighted.isNull());
      weighted.reset();
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
      weighted.addValue(new Object[] { 20, 1 });
      assertEquals(20.0, ((Number) weighted.getResult()).doubleValue(), DELTA);
   }

   @Disabled("mismatch F1, unreachable, not filed")
   @Test
   @DisplayName("F1: an Object[] of length 1 is skipped like the double[] twin")
   void f1_objectArrayOfLengthOne_isSkippedWithoutException() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      assertDoesNotThrow(() -> weighted.addValue(new Object[] { 5 }));
      assertTrue(weighted.isNull());

      // the double[] twin skips a short array
      WeightedAverageFormula twin = new WeightedAverageFormula(1);
      assertDoesNotThrow(() -> twin.addValue(new double[] { 5.0 }));
      assertTrue(twin.isNull());
   }

   @Test
   @DisplayName("F5: Number subclasses other than Double and Integer weigh like numbers")
   void f5_otherNumberTypes_areWeighted() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(new java.math.BigDecimal("10"), 0.5f), pair(20L, (short) 1));
      // (10 * 0.5 + 20 * 1) / (0.5 + 1)
      assertEquals(25.0 / 1.5, asDouble(weighted.getResult()), DELTA);
   }

   @Test
   @DisplayName("F6: padded, exponent and signed numeric text parses as a number")
   void f6_numericTextFormats_parse() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(" 8 ", 1));
      assertEquals(8.0, asDouble(weighted.getResult()), DELTA);

      weighted.reset();
      addPairs(weighted, pair("1e3", 1));
      assertEquals(1000.0, asDouble(weighted.getResult()), DELTA);

      weighted.reset();
      addPairs(weighted, pair("+5", 1));
      assertEquals(5.0, asDouble(weighted.getResult()), DELTA);
   }

   @Test
   @DisplayName("PIN F6b (current behavior, pending dev #80953): locale-formatted or hex text " +
      "is skipped silently")
   void f6b_separatorHexAndArabicDigitText_isSkipped() {
      for(String text : new String[] { "1,000", "1,5", "0x10", "٣" }) {
         WeightedAverageFormula weighted = new WeightedAverageFormula(1);
         addPairs(weighted, pair(text, 1));
         assertTrue(weighted.isNull(), text);
         assertNull(weighted.getResult(), text);
      }
   }

   @Test
   @DisplayName("F11: large values with unit weights keep their precision")
   void f11_largeValues_keepPrecision() {
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);

      for(int i = 1; i <= 10; i++) {
         addPairs(weighted, pair(1e9 + i, 1));
      }

      assertEquals(1e9 + 5.5, asDouble(weighted.getResult()), DELTA);
   }

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   // Test Case #80957: negative weights (not covered by the 13.8 tests). The weights are summed
   // with their sign, so the result is sum(value * weight) / sum(weight)
   @Test
   void getResult_negativeWeights_dividesBySignedWeightSum() {
      // (10 * 3 + 20 * -1) / (3 - 1) = 10 / 2
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      addPairs(weighted, pair(10, 3), pair(20, -1));
      assertEquals(5.0, weighted.getResult());
      assertEquals(5.0, weighted.getDoubleResult());

      // only negative weights: (10 * -1 + 20 * -3) / (-1 - 3) = -70 / -4, as with 1 and 3
      weighted.reset();
      addPairs(weighted, pair(10, -1), pair(20, -3));
      assertEquals(17.5, weighted.getResult());

      // a negative weight sum: (10 * 1 + 20 * -3) / (1 - 3) = -50 / -2, outside the values
      weighted.reset();
      addPairs(weighted, pair(10, 1), pair(20, -3));
      assertEquals(25.0, weighted.getResult());

      // the double array form
      weighted.reset();
      weighted.addValue(new double[] { 10.0, 3.0 });
      weighted.addValue(new double[] { 20.0, -1.0 });
      assertEquals(5.0, weighted.getResult());
   }

   private static void addPairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue((Object) pair);
      }
   }

   private static Object[] pair(Object value, Object weight) {
      return new Object[] { value, weight };
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }
}
