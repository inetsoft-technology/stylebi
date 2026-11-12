/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.mv.formula;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CompositeCorrelationFormulaTest {

   private CompositeCorrelationFormula formula;

   @BeforeEach
   void setUp() {
      formula = new CompositeCorrelationFormula();
   }

   @Test
   void testReset() {
      formula.reset();
      assertEquals(0, formula.getDoubleResult());
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
   }

   @Test
   void testAddValueInvalid() {
      //invalid object array
      Object[] values = {"invalid", 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
      formula.addValue(values);
      assertTrue(formula.isNull());

      //double array, 5 value
      formula.reset();
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
      assertTrue(formula.isNull());

      // Test addValue(double)
      Exception exceptionDouble = assertThrows(RuntimeException.class, () -> formula.addValue(1.0));
      assertEquals("Unsupported method called!", exceptionDouble.getMessage());

      // Test addValue(float)
      Exception exceptionFloat = assertThrows(RuntimeException.class, () -> formula.addValue(1.0f));
      assertEquals("Unsupported method called!", exceptionFloat.getMessage());

      // Test addValue(long)
      Exception exceptionLong = assertThrows(RuntimeException.class, () -> formula.addValue(1L));
      assertEquals("Unsupported method called!", exceptionLong.getMessage());

      // Test addValue(int)
      Exception exceptionInt = assertThrows(RuntimeException.class, () -> formula.addValue(1));
      assertEquals("Unsupported method called!", exceptionInt.getMessage());

      // Test addValue(short)
      Exception exceptionShort = assertThrows(RuntimeException.class, () -> formula.addValue((short) 1));
      assertEquals("Unsupported method called!", exceptionShort.getMessage());
   }

   @Test
   void testAddValueGetResult() {
      //double array, 7 value
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0});
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());

      //double array, 4 value
      formula.reset();
      formula.addValue(new double[]{1.0, 2.0, 3.0, 4.0});
      assertFalse(formula.isNull());
      assertEquals(-0.125, formula.getResult());
      assertEquals(-0.125, formula.getDoubleResult());

      //zero standard deviation
      formula.reset();
      formula.addValue(new double[]{1.0, 1.0, 1.0, 1, 1.0, 1, 1.0}); // Values that lead to zero standard deviation
      assertNull(formula.getResult());
      assertEquals(0, formula.getDoubleResult());
   }

   @Test
   void testAddValueWithObjectArray() {
      //number value
      Object[] values = {1, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
      formula.addValue(values);
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());
      formula.reset();
      assertTrue(formula.isNull());

      //string value
      values = new Object[]{"1.0", "2.0", "3.0", "4.0", "5.0", "6.0", "7.0"};
      formula.addValue(values);
      assertFalse(formula.isNull());
      assertEquals(-0.13055824196677338, formula.getResult());
      assertEquals(-0.13055824196677338, formula.getDoubleResult());
   }

   @Test
   void testSetDefaultResult() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
   }

   @Test
   void testClone() {
      CompositeCorrelationFormula clonedFormula = (CompositeCorrelationFormula) formula.clone();
      assertNotNull(clonedFormula);
   }

   @Test
   void testGetDisplayName() {
      assertEquals("CorrelationFormula", formula.getDisplayName());
   }

   @Test
   void testGetName() {
      assertEquals(XConstants.CORRELATION_FORMULA, formula.getName());
   }

   @Test
   void testGetResultType() {
      assertEquals(Double.class, formula.getResultType());
   }

   // ---- Bug #81182: no result (zero denominator) is null / isNull / 0, never NaN ----

   /** Feeds one row per call, as the per-group sub-aggregates the MV merge stage sums up. */
   private static void feed(CompositeCorrelationFormula f, double[][] rows) {
      for(double[] r : rows) {
         f.addValue(new double[] { r[0] * r[1], r[0], r[1], 1, r[0] * r[0], 1, r[1] * r[1] });
      }
   }

   private static void assertNoResult(CompositeCorrelationFormula f, String msg) {
      assertNull(f.getResult(), msg);
      assertTrue(f.isNull(), msg);
      assertEquals(0.0, f.getDoubleResult(), msg);
   }

   @Test
   void zeroVarianceIsNullInAllThreeAccessors() {
      feed(formula, new double[][] { { 5, 1 }, { 5, 2 }, { 5, 3 } });
      assertNoResult(formula, "constant x");

      formula.reset();
      feed(formula, new double[][] { { 1, 7 }, { 2, 7 }, { 3, 7 } });
      assertNoResult(formula, "constant y");

      formula.reset();
      feed(formula, new double[][] { { 4, 6 } });
      assertNoResult(formula, "one pair");
   }

   @Test
   void defaultResultFlagKeepsItsMeaning() {
      formula.setDefaultResult(true);

      // no rows at all: the default result, unchanged
      assertEquals(0.0, formula.getResult());
      assertTrue(formula.isNull());

      // zero variance: still null, the flag does not newly change it
      feed(formula, new double[][] { { 5, 1 }, { 5, 2 }, { 5, 3 } });
      assertNull(formula.getResult());
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   void roundoffNeverProducesNanOrNoise() {
      Random random = new Random(81182);

      for(int g = 0; g < 3000; g++) {
         double c = 0.001 + random.nextDouble() * 1000;
         int n = 2 + random.nextInt(30);
         double[][] rows = new double[n][];

         for(int i = 0; i < n; i++) {
            rows[i] = new double[] { c, random.nextGaussian() * 10 };
         }

         formula.reset();
         feed(formula, rows);
         assertNoResult(formula, "constant x=" + c + " n=" + n);

         // the same with the constant on y
         formula.reset();
         feed(formula, Arrays.stream(rows).map(r -> new double[] { r[1], r[0] })
            .toArray(double[][]::new));
         assertNoResult(formula, "constant y=" + c + " n=" + n);
      }
   }

   @Test
   void smallButRealVarianceStillCorrelates() {
      // large offset, small spread: variance is about 1e-8 of the squared mean
      double[][] rows = new double[10][];

      for(int i = 0; i < 10; i++) {
         rows[i] = new double[] { 10000 + i, 2.0 * i + (i % 2) };
      }

      feed(formula, rows);
      assertFalse(formula.isNull());
      Object r = formula.getResult();
      assertNotNull(r);
      assertEquals(pearson(rows), (Double) r, 1.0E-6);
      assertEquals((Double) r, formula.getDoubleResult(), 0.0);

      // a tiny absolute scale is not mistaken for zero variance
      formula.reset();
      double[][] small = new double[5][];

      for(int i = 0; i < 5; i++) {
         small[i] = new double[] { 1.0E-6 * (i + 1), 1.0E-6 * (i * i) };
      }

      feed(formula, small);
      assertEquals(pearson(small), (Double) formula.getResult(), 1.0E-6);
   }

   private static double pearson(double[][] rows) {
      int n = rows.length;
      double mx = 0;
      double my = 0;

      for(double[] r : rows) {
         mx += r[0] / n;
         my += r[1] / n;
      }

      double sxy = 0;
      double sxx = 0;
      double syy = 0;

      for(double[] r : rows) {
         sxy += (r[0] - mx) * (r[1] - my);
         sxx += (r[0] - mx) * (r[0] - mx);
         syy += (r[1] - my) * (r[1] - my);
      }

      return sxy / Math.sqrt(sxx * syy);
   }
}