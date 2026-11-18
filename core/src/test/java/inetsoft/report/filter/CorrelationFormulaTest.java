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

import inetsoft.mv.data.FormulaInfo;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CorrelationFormula. Bug #80950: the result must not depend on the magnitude of the
 * values (only on their spread).
 */
@Tag("core")
class CorrelationFormulaTest {
   private static CorrelationFormula fill(double offset, double slope, double intercept) {
      CorrelationFormula f = new CorrelationFormula(1);

      for(int i = 1; i <= 10; i++) {
         double x = offset + i;
         f.addValue(new Object[] { x, slope * x + intercept });
      }

      return f;
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0, 1e4, 1e6, 1e7, 1e8, 1e9, 1e12, -1e9 })
   void perfectPositive_independentOfOffset(double offset) {
      CorrelationFormula f = fill(offset, 3, 7);
      assertEquals(1.0, (Double) f.getResult(), 1e-9, "offset " + offset);
      assertEquals(1.0, f.getDoubleResult(), 1e-9, "offset " + offset);
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0, 1e6, 1e9 })
   void perfectNegative_independentOfOffset(double offset) {
      CorrelationFormula f = fill(offset, -2, 5);
      assertEquals(-1.0, (Double) f.getResult(), 1e-9, "offset " + offset);
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0, 1e9 })
   void doubleArrayPath_matchesObjectPath(double offset) {
      CorrelationFormula f = new CorrelationFormula(1);

      for(int i = 1; i <= 10; i++) {
         double x = offset + i;
         // the pair is members 0 and 1
         f.addValue(new double[] { x, 3 * x + 7 });
      }

      assertEquals(1.0, (Double) f.getResult(), 1e-9);
   }

   @Test
   void knownImperfectCorrelation() {
      // x = 1..5, y = 2,1,4,3,5 -> r = 0.8
      CorrelationFormula f = new CorrelationFormula(1);
      double[] y = { 2, 1, 4, 3, 5 };

      for(int i = 0; i < 5; i++) {
         f.addValue(new Object[] { (double) (i + 1), y[i] });
      }

      assertEquals(0.8, (Double) f.getResult(), 1e-12);

      // same data shifted by a huge offset in both columns
      f.reset();

      for(int i = 0; i < 5; i++) {
         f.addValue(new Object[] { 1e9 + i + 1, 1e9 + y[i] });
      }

      assertEquals(0.8, (Double) f.getResult(), 1e-6);
   }

   @Test
   void smallInputs_exactPlusMinusOne() {
      assertExact(-1.0, new double[] { 1, 2, 3 }, new double[] { 3, 2, 1 });
      assertExact(1.0, new double[] { 1, 2, 3 }, new double[] { 2, 4, 6 });
      assertExact(1.0, new double[] { 1, 2, 3, 4, 5 }, new double[] { 10, 20, 30, 40, 50 });
      assertExact(-1.0, new double[] { 1, 2, 3, 4 }, new double[] { 8, 6, 4, 2 });
   }

   private static void assertExact(double expected, double[] xs, double[] ys) {
      CorrelationFormula f = new CorrelationFormula(1);

      for(int i = 0; i < xs.length; i++) {
         f.addValue(new Object[] { xs[i], ys[i] });
      }

      assertEquals(expected, (Double) f.getResult());
      assertEquals(expected, f.getDoubleResult());
   }

   @Test
   void extremeMagnitudes_productOverflowAndUnderflow() {
      // m2x * m2y overflows to infinity, the separate roots must still give +-1
      CorrelationFormula f = new CorrelationFormula(1);

      for(int i = 1; i <= 5; i++) {
         f.addValue(new Object[] { i * 1e100, -i * 1e100 });
      }

      assertEquals(-1.0, (Double) f.getResult(), 1e-12);

      // m2x * m2y underflows to 0
      f.reset();

      for(int i = 1; i <= 5; i++) {
         f.addValue(new Object[] { i * 1e-100, i * 1e-100 });
      }

      assertEquals(1.0, (Double) f.getResult(), 1e-12);
   }

   @Test
   void zeroVariance_behaviorPreserved() {
      CorrelationFormula f = new CorrelationFormula(1);

      for(int i = 1; i <= 5; i++) {
         f.addValue(new Object[] { 4.0, (double) i });
      }

      assertNull(f.getResult());
      f.setDefaultResult(true);
      assertEquals(0.0, (Double) f.getResult());
   }

   // Bug #81182: a zero denominator is "no result" for every accessor
   @Test
   void zeroVariance_doubleResultAgreesWithResult() {
      for(boolean def : new boolean[] { false, true }) {
         CorrelationFormula f = new CorrelationFormula(1);
         f.setDefaultResult(def);

         for(int i = 1; i <= 5; i++) {
            f.addValue(new Object[] { 4.0, (double) i });
         }

         assertTrue(f.isNull());
         assertEquals(0.0, f.getDoubleResult());
         assertEquals(def ? Double.valueOf(0) : null, f.getResult());
      }
   }

   @Test
   void singlePair_isNoResult() {
      for(boolean def : new boolean[] { false, true }) {
         CorrelationFormula f = new CorrelationFormula(1);
         f.setDefaultResult(def);
         f.addValue(new Object[] { 1.0, 2.0 });
         assertTrue(f.isNull());
         assertEquals(0.0, f.getDoubleResult());
         assertEquals(def ? Double.valueOf(0) : null, f.getResult());

         CorrelationFormula g = new CorrelationFormula(1);
         g.setDefaultResult(def);
         g.addValue(new double[] { 1.0, 2.0 });
         assertTrue(g.isNull());
         assertEquals(0.0, g.getDoubleResult());
      }
   }

   @Test
   void realCorrelation_isNotNull() {
      CorrelationFormula f = fill(0, 1, 2);
      assertFalse(f.isNull());
      assertEquals(1.0, f.getDoubleResult(), 1e-9);
   }

   @Test
   void noValues() {
      CorrelationFormula f = new CorrelationFormula(1);
      assertTrue(f.isNull());
      assertNull(f.getResult());
      assertEquals(0, f.getDoubleResult());
   }

   @Test
   void resetAndClone() {
      CorrelationFormula f = fill(1e9, 3, 7);
      CorrelationFormula c = (CorrelationFormula) f.clone();
      f.reset();
      assertTrue(f.isNull());
      assertEquals(1.0, (Double) c.getResult(), 1e-9);
   }

   // Bug #80974: a blank string is no value, the whole pair is skipped
   @Test
   void blankInPair_skipsWholePair() {
      CorrelationFormula f = new CorrelationFormula(1);
      Object[][] pairs = { { 1, 1 }, { 2, 2 }, { "", 3 }, { 4, 4 }, { 5, 5.5 }, { 6, " " } };

      for(Object[] p : pairs) {
         f.addValue((Object) p);
      }

      assertEquals(0.9964037900472443, (Double) f.getResult(), 1e-9);
   }

   @Test
   void allBlank_sameAsAllNull() {
      CorrelationFormula blank = new CorrelationFormula(1);
      CorrelationFormula nulls = new CorrelationFormula(1);
      blank.setDefaultResult(false);
      nulls.setDefaultResult(false);
      blank.addValue((Object) new Object[] { "", "" });
      nulls.addValue((Object) new Object[] { null, null });
      assertEquals(nulls.getResult(), blank.getResult());
      assertEquals(nulls.isNull(), blank.isNull());
   }

   @Test
   void doubleArrayPair_twoElementsAndShortArray() {
      CorrelationFormula f = new CorrelationFormula(1);
      f.addValue(new double[] { 1, 2 });
      f.addValue(new double[] { 2, 4 });
      f.addValue(new double[] { 3, 6.5 });
      f.addValue(new double[] { 9 });
      f.addValue(new double[] { 9, inetsoft.util.Tool.NULL_DOUBLE });
      // x mean 2, sxx 2; y = 2, 4, 6.5 has syy 61/6 and sxy 4.5 by hand
      assertEquals(4.5 / Math.sqrt(2 * 61.0 / 6), (Double) f.getResult(), 1e-6);
   }

   private static final double EPS = 1e-12;

   private static void pairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue(pair);
      }
   }

   private static Object[] p(Object x, Object y) {
      return new Object[] { x, y };
   }

   private static void assertUnsupported(Executable call) {
      assertEquals("Unsupported method called!",
                   assertThrows(RuntimeException.class, call).getMessage());
   }

   private static boolean isBlank(Object value) {
      return value instanceof String && ((String) value).trim().isEmpty();
   }

   // gap:report.summary-formulas.bivariate#correlation-coefficient
   @Test
   void nameResultTypeAndSecondaryColumn() {
      CorrelationFormula correl = new CorrelationFormula(2);
      assertEquals(XConstants.CORRELATION_FORMULA, correl.getName());
      assertEquals(Double.class, correl.getResultType());
      assertArrayEquals(new int[] { 2 }, correl.getSecondaryColumns());
   }

   // gap:report.summary-formulas.bivariate#correlation-coefficient
   @Test
   void unrelatedGeneralAndMixedTypePairs() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // unrelated columns
      pairs(correl, p(1, 2), p(2, 5), p(3, 1), p(4, 5), p(5, 2));
      assertEquals(0.0, (Double) correl.getResult(), EPS);
      assertFalse(correl.isNull());

      // a general case: r = (n*sxy - sx*sy) / sqrt((n*sxx - sx^2) * (n*syy - sy^2)) = 12/sqrt(1040)
      correl.reset();
      pairs(correl, p(1, 2), p(2, 7), p(3, 4), p(4, 5));
      assertEquals(12 / Math.sqrt(1040), (Double) correl.getResult(), EPS);

      // numbers of any type and numeric text are the same pair
      correl.reset();
      pairs(correl, p(1, 2L), p(2.0, "4"), p("3", 6f));
      assertEquals(1.0, (Double) correl.getResult(), EPS);

      // reset starts over: the earlier pairs do not count
      correl.reset();
      pairs(correl, p(1, 3), p(2, 2), p(3, 1));
      assertEquals(-1.0, (Double) correl.getResult(), EPS);
   }

   // gap:report.summary-formulas.bivariate#correlation-no-numeric-input-null
   @Test
   void noNumericInput_isNull() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // the default-result option turns "no result" into 0.0
      correl.setDefaultResult(true);
      assertTrue(correl.isDefaultResult());
      assertEquals(0.0, (Double) correl.getResult());
      assertTrue(correl.isNull());
      correl.setDefaultResult(false);

      // only nulls
      pairs(correl, p(null, null), p(null, null), p(null, null), p(null, null));
      assertNull(correl.getResult());
      assertTrue(correl.isNull());

      // date pairs are not numbers
      correl.reset();
      Timestamp a = Timestamp.valueOf("2024-06-12 10:30:00");
      Timestamp b = Timestamp.valueOf("2024-07-01 08:00:00");
      pairs(correl, p(a, b), p(b, a), p(a, a));
      assertNull(correl.getResult());
      assertTrue(correl.isNull());

      // a value that is not a pair array is ignored
      correl.addValue((Object) null);
      correl.addValue((Object) "12");
      correl.addValue((Object) new double[] { 1.0, 2.0 });
      assertNull(correl.getResult());
      assertTrue(correl.isNull());

      // reset returns a used formula to no result
      pairs(correl, p(1, 2), p(2, 4));
      // two points on a line
      assertEquals(1.0, (Double) correl.getResult(), EPS);
      correl.reset();
      assertNull(correl.getResult());
      assertTrue(correl.isNull());
   }

   // gap:report.summary-formulas.bivariate#correlation-skips-incomplete-pairs
   @Test
   void nullOrTextMember_skipsPair() {
      CorrelationFormula whole = new CorrelationFormula(2);
      pairs(whole, p(1, 2), p(2, 7), p(3, 4), p(4, 5));
      Double expected = (Double) whole.getResult();
      assertEquals(12 / Math.sqrt(1040), expected, EPS);

      // pairs with a null or text member add nothing, wherever they sit
      CorrelationFormula mixed = new CorrelationFormula(2);
      pairs(mixed, p(null, null), p(1, 2), p(null, 9), p(2, 7), p(8, null), p("a", 5),
            p(3, 4), p(5, "b"), p(4, 5), p(null, null));
      assertEquals(expected, mixed.getResult());

      // a text member that is a number counts
      CorrelationFormula text = new CorrelationFormula(2);
      pairs(text, p("1", 2), p(2, "7"), p(3, 4), p("4", "5"));
      assertEquals(expected, text.getResult());
   }

   // gap:report.summary-formulas.bivariate#correlation-zero-variance
   @Test
   void zeroVariance_constantSecondColumnOrSinglePair() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // a constant second column: no variance, so no coefficient
      pairs(correl, p(1, 5), p(2, 5), p(3, 5));
      assertNull(correl.getResult());

      // a single pair has no variance either
      correl.reset();
      pairs(correl, p(2, 3));
      assertNull(correl.getResult());

      // the default-result option gives 0.0 instead
      correl.setDefaultResult(true);
      assertEquals(0.0, (Double) correl.getResult());
      correl.reset();
      pairs(correl, p(1, 5), p(2, 5), p(3, 5));
      assertEquals(0.0, (Double) correl.getResult());

      // with variance the option changes nothing
      correl.reset();
      pairs(correl, p(1, 2), p(2, 4), p(3, 6));
      assertEquals(1.0, (Double) correl.getResult(), EPS);
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path
   @Test
   void doubleArrayPair_nullOrTooShortAddsNothing() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // a null array and an array with fewer than two members add nothing
      correl.addValue((double[]) null);
      correl.addValue(new double[0]);
      correl.addValue(new double[] { 3 });
      assertTrue(correl.isNull());
      assertNull(correl.getResult());
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path,
   // gap:report.summary-formulas.common#second-column-formulas-need-pairs
   @Test
   void singleNumberAddValue_isUnsupported() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // the single-number forms are not supported by a two-column formula
      assertUnsupported(() -> correl.addValue(1.5));
      assertUnsupported(() -> correl.addValue(1));
      assertUnsupported(() -> correl.addValue(1L));
      assertUnsupported(() -> correl.addValue(1f));
      assertUnsupported(() -> correl.addValue((short) 1));
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path
   @Test
   void doubleArrayPair_nullMarkerInFirstMember_skipsPair() {
      // MVRow -> FormulaInfo2.addValue and CalcFieldFormula.addValue pass {primary, secondary}
      CorrelationFormula correl = new CorrelationFormula(2);
      correl.addValue(new double[] { 1, 2 });
      correl.addValue(new double[] { 2, 4 });
      correl.addValue(new double[] { 3, 6 });

      // a pair with a null marker is skipped, as the Object form skips a null member
      correl.addValue(new double[] { Tool.NULL_DOUBLE, 9 });
      assertEquals(1.0, (Double) correl.getResult(), EPS);
   }

   // Bug #80701: blank text pairs change nothing, and other lists are unchanged
   @Test
   void blankTextPairs_changeNothingInRandomLists() {
      Random random = new Random(80701L);
      Object[] pool = { 1, 2.5, -3, 10L, "4", "-7.5", " 8 ", "", "  ", "abc", null };

      for(int i = 0; i < 400; i++) {
         List<Object[]> all = new ArrayList<>();
         List<Object[]> clean = new ArrayList<>();
         int size = random.nextInt(8);

         for(int j = 0; j < size; j++) {
            Object[] pair = p(pool[random.nextInt(pool.length)], pool[random.nextInt(pool.length)]);
            all.add(pair);

            if(!isBlank(pair[0]) && !isBlank(pair[1])) {
               clean.add(pair);
            }
         }

         CorrelationFormula withBlanks = new CorrelationFormula(2);
         CorrelationFormula noBlanks = new CorrelationFormula(2);
         all.forEach(withBlanks::addValue);
         clean.forEach(noBlanks::addValue);
         assertEquals(noBlanks.getResult(), withBlanks.getResult(),
                      Arrays.deepToString(all.toArray()));
      }
   }

   // Bug #80700: the primitive pair of a summary column is added as one pair
   @Test
   void doubleArrayPair_sameAsObjectPairForImperfectData() {
      // FormulaInfo2 fills {primary, secondary} for the two column indexes the formula names
      double[][] rows = { { 1, 2 }, { 2, 7 }, { 3, 4 }, { 4, 5 } };
      CorrelationFormula primitive = new CorrelationFormula(2);
      CorrelationFormula object = new CorrelationFormula(2);

      for(double[] row : rows) {
         primitive.addValue(row);
         object.addValue(p(row[0], row[1]));
      }

      assertEquals(object.getResult(), primitive.getResult());
      assertEquals(0.3721, (Double) primitive.getResult(), 1e-4);

      // a short array is still ignored
      primitive.addValue(new double[] { 1 });
      assertEquals(object.getResult(), primitive.getResult());
   }

   @Disabled("Not filed (unreachable): a one-member Object[] pair is skipped, as the double[] path does")
   @Test
   @DisplayName("An Object[] of length 1 is skipped like the double[] twin")
   void objectArrayOfLengthOne_isSkippedWithoutException() {
      CorrelationFormula correl = new CorrelationFormula(2);
      assertDoesNotThrow(() -> correl.addValue(new Object[] { 5 }));
      assertTrue(correl.isNull());
   }

   @Test
   @DisplayName("Padded, exponent and signed text is the same pair as the number")
   void numericTextPair_equalsTheNumericPair() {
      CorrelationFormula text = new CorrelationFormula(2);
      CorrelationFormula numeric = new CorrelationFormula(2);
      pairs(text, p(1, 2), p(" 8 ", 7), p("1e3", 5), p("+5", 3), p(4, "+6"));
      pairs(numeric, p(1, 2), p(8, 7), p(1000, 5), p(5, 3), p(4, 6));
      assertEquals(numeric.getResult(), text.getResult());
      assertNotNull(text.getResult());
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): separator, hex and Arabic " +
      "digit text pairs are skipped")
   void separatorHexAndArabicDigitText_pairIsSkipped() {
      CorrelationFormula clean = new CorrelationFormula(2);
      pairs(clean, p(1, 2), p(2, 7), p(3, 4));

      for(String text : new String[] { "1,000", "1,5", "0x10", "٣" }) {
         CorrelationFormula withText = new CorrelationFormula(2);
         pairs(withText, p(1, 2), p(2, 7), p(text, 9), p(3, 4));
         assertEquals(clean.getResult(), withText.getResult(), text);
      }
   }

   private static double corr(double[] xs, double[] ys) {
      CorrelationFormula f = new CorrelationFormula(2);

      for(int i = 0; i < xs.length; i++) {
         f.addValue(p(xs[i], ys[i]));
      }

      return (Double) f.getResult();
   }

   @Test
   @DisplayName("Random data, including near-perfect and offset data, stays in [-1, 1]")
   void randomData_staysWithinBounds() {
      Random random = new Random(80957L);
      double[] offsets = { 0, 1e9 };

      for(int round = 0; round < 500; round++) {
         int n = 3 + random.nextInt(48);
         double offset = offsets[round % 2];
         double noise = round % 3 == 0 ? 0 : (round % 3 == 1 ? 1e-9 : 5.0);
         double[] xs = new double[n];
         double[] ys = new double[n];

         for(int i = 0; i < n; i++) {
            // distinct x so the variance is positive; y has a positive slope
            xs[i] = offset + i * 3 + random.nextDouble();
            ys[i] = offset + 2 * i + 1 + noise * random.nextDouble();
         }

         double r = corr(xs, ys);
         assertFalse(Double.isNaN(r), "round " + round);
         assertTrue(r >= -1.0 && r <= 1.0, "round " + round + " r=" + r);
      }
   }

   @Test
   @DisplayName("Correlation is symmetric and flips sign under a negative affine change")
   void symmetryAndAffineInvariance() {
      Random random = new Random(80958L);

      for(int round = 0; round < 50; round++) {
         int n = 3 + random.nextInt(20);
         double[] xs = new double[n];
         double[] ys = new double[n];
         double[] xs2 = new double[n];
         double[] ys2 = new double[n];

         for(int i = 0; i < n; i++) {
            xs[i] = random.nextGaussian() * 10;
            ys[i] = random.nextGaussian() * 10 + 0.5 * xs[i];
            xs2[i] = 2 * xs[i] + 3;
            ys2[i] = -4 * ys[i] + 1;
         }

         double r = corr(xs, ys);
         assertEquals(r, corr(ys, xs), 1e-9);
         assertEquals(-r, corr(xs2, ys2), 1e-9);
      }
   }

   @Test
   @DisplayName("One million pairs agree with a two-pass reference")
   void largeN_matchesTwoPassReference() {
      Random random = new Random(80959L);
      int n = 1_000_000;
      double[] xs = new double[n];
      double[] ys = new double[n];
      double mx = 0;
      double my = 0;

      for(int i = 0; i < n; i++) {
         xs[i] = 100 + random.nextGaussian() * 15;
         ys[i] = 0.3 * xs[i] + random.nextGaussian() * 5;
         mx += xs[i];
         my += ys[i];
      }

      mx /= n;
      my /= n;
      double sxy = 0;
      double sxx = 0;
      double syy = 0;

      for(int i = 0; i < n; i++) {
         sxy += (xs[i] - mx) * (ys[i] - my);
         sxx += (xs[i] - mx) * (xs[i] - mx);
         syy += (ys[i] - my) * (ys[i] - my);
      }

      assertEquals(sxy / Math.sqrt(sxx * syy), corr(xs, ys), 1e-9);
   }

   @Test
   @DisplayName("The materialized view correlation agrees with the report formula")
   void agreesWithMvCompositeCorrelation() {
      double[] xs = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
      double[] ys = { 2.1, 3.9, 6.2, 7.8, 10.3, 11.9, 14.2, 16.1, 17.8, 20.4 };
      inetsoft.mv.formula.CompositeCorrelationFormula mv =
         new inetsoft.mv.formula.CompositeCorrelationFormula(new int[] { 1 });
      // the partials: sumxy, sumx, sumy, cntx, sumsqx, cnty, sumsqy
      double sxy = 0;
      double sx = 0;
      double sy = 0;
      double sxx = 0;
      double syy = 0;

      for(int i = 0; i < xs.length; i++) {
         sxy += xs[i] * ys[i];
         sx += xs[i];
         sy += ys[i];
         sxx += xs[i] * xs[i];
         syy += ys[i] * ys[i];
      }

      mv.addValue(new double[] { sxy, sx, sy, xs.length, sxx, xs.length, syy });
      assertEquals(corr(xs, ys), ((Number) mv.getResult()).doubleValue(), 1e-9);
   }

   // Bug #80700: a materialized view row feeds Correlation one pair
   @Test
   void materializedViewRow_feedsOnePair() {
      // GroupedTableBlock builds FormulaInfo over the measure indexes of the MV row, and
      // FormulaInfo2 hands the formula {primary, secondary}
      FormulaInfo correl = FormulaInfo.create(new CorrelationFormula(), new int[] { 3, 1 });
      // measure columns of the row: 0 unused, 1 secondary, 2 unused, 3 primary
      double[][] rows = { { 0, 2, 0, 1 }, { 0, 7, 0, 2 }, { 0, 4, 0, 3 }, { 0, 5, 0, 4 },
                          { 0, 9, 0, Tool.NULL_DOUBLE } };

      for(double[] row : rows) {
         correl.addValue(row);
      }

      assertEquals(0.3721, (Double) correl.getFormula().getResult(), 1e-4);
   }
}
