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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
      assertTrue(Double.isNaN(f.getDoubleResult()));
      f.setDefaultResult(true);
      assertEquals(0.0, (Double) f.getResult());
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
      assertEquals(0.9981, (Double) f.getResult(), 1e-3);
   }
}
