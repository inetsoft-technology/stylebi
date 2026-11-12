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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CovarianceFormula, Bug #80974: a blank string is no value; the double[] pair is members 0 and 1.
 */
@Tag("core")
class CovarianceFormulaTest {
   private static final Object[][] PAIRS = {
      { 1, 1 }, { 2, 2 }, { "", 3 }, { 4, 4 }, { 5, 5.5 }
   };

   private static void feed(Formula f, Object[][] pairs) {
      for(Object[] p : pairs) {
         f.addValue((Object) p);
      }
   }

   @Test
   void blankInPair_skipsWholePair() {
      CovarianceFormula f = new CovarianceFormula();
      feed(f, PAIRS);
      assertEquals(2.75, ((Number) f.getResult()).doubleValue(), 1e-4);
   }

   @Test
   void blankOnEitherSideOrWhitespace_skipsPair() {
      CovarianceFormula a = new CovarianceFormula();
      feed(a, new Object[][] { { 1, 1 }, { 2, 2 }, { 3, "" }, { 4, 4 }, { 5, 5.5 }, { " ", 9 } });
      CovarianceFormula b = new CovarianceFormula();
      feed(b, new Object[][] { { 1, 1 }, { 2, 2 }, { 4, 4 }, { 5, 5.5 } });
      assertEquals(((Number) b.getResult()).doubleValue(), ((Number) a.getResult()).doubleValue(), 1e-12);
   }

   @Test
   void allBlank_sameAsAllNull() {
      CovarianceFormula blank = new CovarianceFormula();
      CovarianceFormula nulls = new CovarianceFormula();
      blank.setDefaultResult(false);
      nulls.setDefaultResult(false);
      feed(blank, new Object[][] { { "", "" }, { "", 3 } });
      feed(nulls, new Object[][] { { null, null }, { null, 3 } });
      assertEquals(nulls.getResult(), blank.getResult());
      assertEquals(nulls.isNull(), blank.isNull());
   }

   @Test
   void doubleArrayPair_readsMembersZeroAndOne() {
      CovarianceFormula f = new CovarianceFormula();
      f.addValue(new double[] { 1, 1 });
      f.addValue(new double[] { 2, 2 });
      f.addValue(new double[] { 4, 4 });
      f.addValue(new double[] { 5, 5.5 });
      CovarianceFormula g = new CovarianceFormula();
      feed(g, new Object[][] { { 1, 1 }, { 2, 2 }, { 4, 4 }, { 5, 5.5 } });
      assertEquals(((Number) g.getResult()).doubleValue(), ((Number) f.getResult()).doubleValue(), 1e-12);
      // too short / null sentinel are skipped, not an exception
      f.addValue(new double[] { 9 });
      f.addValue(new double[] { 9, inetsoft.util.Tool.NULL_DOUBLE });
      assertEquals(((Number) g.getResult()).doubleValue(), ((Number) f.getResult()).doubleValue(), 1e-12);
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

   // gap:report.summary-formulas.bivariate#covariance-value
   @Test
   void nameResultTypeAndSecondaryColumn() {
      CovarianceFormula covar = new CovarianceFormula(2);
      assertEquals(XConstants.COVARIANCE_FORMULA, covar.getName());
      assertEquals(Double.class, covar.getResultType());
      assertArrayEquals(new int[] { 2 }, covar.getSecondaryColumns());
   }

   // gap:report.summary-formulas.bivariate#covariance-value
   @Test
   void populationCovariance_signTypesAndClone() {
      CovarianceFormula covar = new CovarianceFormula(2);

      // (1,2),(2,4),(3,6): the sum of (x-2)(y-4) is 4, over n = 3 for the population value
      pairs(covar, p(1, 2), p(2, 4), p(3, 6));
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);
      assertEquals(4.0 / 3, covar.getDoubleResult(), EPS);
      assertFalse(covar.isNull());

      // x = 1..6 against y = 2..7 is the population variance of 1..6, 35/12
      covar.reset();
      pairs(covar, p(1, 2), p(2, 3), p(3, 4), p(4, 5), p(5, 6), p(6, 7));
      assertEquals(35.0 / 12, (Double) covar.getResult(), EPS);

      // the sign follows the direction
      covar.reset();
      pairs(covar, p(1, 6), p(2, 4), p(3, 2));
      assertEquals(-4.0 / 3, (Double) covar.getResult(), EPS);

      // a constant column has no covariance with anything
      covar.reset();
      pairs(covar, p(1, 5), p(2, 5), p(3, 5));
      assertEquals(0.0, (Double) covar.getResult(), EPS);

      // numbers of any type and numeric text are the same pair
      covar.reset();
      pairs(covar, p(1, 2L), p(2.0, "4"), p("3", 6f));
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);

      // a clone keeps the pairs and the default-result option, and then goes its own way
      covar.setDefaultResult(true);
      CovarianceFormula copy = (CovarianceFormula) covar.clone();
      assertTrue(copy.isDefaultResult());
      assertEquals(4.0 / 3, (Double) copy.getResult(), EPS);
      copy.addValue(p(10, 20));
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);
      assertNotEquals((Double) covar.getResult(), (Double) copy.getResult());
   }

   // gap:report.summary-formulas.bivariate#covariance-no-numeric-input-null
   @Test
   void noNumericInput_isNull() {
      CovarianceFormula covar = new CovarianceFormula(2);

      assertNull(covar.getResult());
      assertTrue(covar.isNull());
      assertEquals(0, covar.getDoubleResult());

      covar.setDefaultResult(true);
      assertEquals(0.0, (Double) covar.getResult());
      assertTrue(covar.isNull());
      covar.setDefaultResult(false);

      pairs(covar, p(null, null), p(null, null), p(null, null), p(null, null));
      assertNull(covar.getResult());
      assertTrue(covar.isNull());

      covar.reset();
      Timestamp a = Timestamp.valueOf("2024-06-12 10:30:00");
      Timestamp b = Timestamp.valueOf("2024-07-01 08:00:00");
      pairs(covar, p(a, b), p(b, a), p(a, a));
      assertNull(covar.getResult());
      assertTrue(covar.isNull());

      covar.addValue((Object) null);
      covar.addValue((Object) "12");
      assertNull(covar.getResult());
      assertTrue(covar.isNull());

      pairs(covar, p(1, 2), p(2, 4));
      assertNotNull(covar.getResult());
      covar.reset();
      assertNull(covar.getResult());
      assertTrue(covar.isNull());
   }

   // gap:report.summary-formulas.bivariate#covariance-skips-incomplete-pairs
   @Test
   void nullOrTextMember_skipsPair() {
      CovarianceFormula whole = new CovarianceFormula(2);
      pairs(whole, p(1, 2), p(2, 4), p(3, 6));
      Double expected = (Double) whole.getResult();
      assertEquals(4.0 / 3, expected, EPS);

      CovarianceFormula mixed = new CovarianceFormula(2);
      pairs(mixed, p(null, null), p(1, 2), p(null, 9), p(2, 4), p(8, null), p("a", 5),
            p(3, 6), p(5, "b"), p("3", "a"), p(6, null));
      assertEquals(expected, mixed.getResult());
      assertFalse(mixed.isNull());
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path
   @Test
   void doubleArrayPair_nullOrTooShortAddsNothing() {
      CovarianceFormula covar = new CovarianceFormula(2);

      // a null array and an array with fewer than two members add nothing
      covar.addValue((double[]) null);
      covar.addValue(new double[0]);
      covar.addValue(new double[] { 3 });
      assertTrue(covar.isNull());
      assertNull(covar.getResult());
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path,
   // gap:report.summary-formulas.common#second-column-formulas-need-pairs
   @Test
   void singleNumberAddValue_isUnsupported() {
      CovarianceFormula covar = new CovarianceFormula(2);

      // the single-number forms are not supported by a two-column formula
      assertUnsupported(() -> covar.addValue(1.5));
      assertUnsupported(() -> covar.addValue(1));
      assertUnsupported(() -> covar.addValue(1L));
      assertUnsupported(() -> covar.addValue(1f));
      assertUnsupported(() -> covar.addValue((short) 1));
   }

   // gap:report.summary-formulas.bivariate#correlation-primitive-pair-path
   @Test
   void doubleArrayPair_nullMarkerInFirstMember_skipsPair() {
      CovarianceFormula covar = new CovarianceFormula(2);
      covar.addValue(new double[] { 1, 2 });
      covar.addValue(new double[] { 2, 4 });
      covar.addValue(new double[] { 3, 6 });

      // a pair with a null marker is skipped, as the Object form skips a null member
      covar.addValue(new double[] { Tool.NULL_DOUBLE, 9 });
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);
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

         CovarianceFormula withBlanks = new CovarianceFormula(2);
         CovarianceFormula noBlanks = new CovarianceFormula(2);
         all.forEach(withBlanks::addValue);
         clean.forEach(noBlanks::addValue);
         assertEquals(noBlanks.getResult(), withBlanks.getResult(),
                      Arrays.deepToString(all.toArray()));
      }
   }

   // Bug #80700: a materialized view row feeds Covariance one pair
   @Test
   void materializedViewRow_feedsOnePair() {
      // GroupedTableBlock builds FormulaInfo over the measure indexes of the MV row, and
      // FormulaInfo2 hands the formula {primary, secondary}
      FormulaInfo covar = FormulaInfo.create(new CovarianceFormula(), new int[] { 3, 1 });
      // measure columns of the row: 0 unused, 1 secondary, 2 unused, 3 primary
      double[][] rows = { { 0, 2, 0, 1 }, { 0, 7, 0, 2 }, { 0, 4, 0, 3 }, { 0, 5, 0, 4 },
                          { 0, 9, 0, Tool.NULL_DOUBLE } };

      for(double[] row : rows) {
         covar.addValue(row);
      }

      // covariance of x = 1..4 and y = 2, 7, 4, 5
      assertEquals(0.75, (Double) covar.getFormula().getResult(), EPS);
   }

   @Disabled("mismatch F2, unreachable, not filed")
   @Test
   @DisplayName("F2: an Object[] of length 1 is skipped like the double[] twin")
   void f2_objectArrayOfLengthOne_isSkippedWithoutException() {
      CovarianceFormula covar = new CovarianceFormula(2);
      assertDoesNotThrow(() -> covar.addValue(new Object[] { 5 }));
      assertTrue(covar.isNull());
   }

   @Test
   @DisplayName("F6: padded, exponent and signed text is the same pair as the number")
   void f6_numericTextPair_equalsTheNumericPair() {
      CovarianceFormula text = new CovarianceFormula(2);
      CovarianceFormula numeric = new CovarianceFormula(2);
      pairs(text, p(1, 2), p(" 8 ", 7), p("1e3", 5), p("+5", 3), p(4, "+6"));
      pairs(numeric, p(1, 2), p(8, 7), p(1000, 5), p(5, 3), p(4, 6));
      assertEquals(numeric.getResult(), text.getResult());
      assertNotNull(text.getResult());
   }

   @Test
   @DisplayName("PIN F6b (current behavior, pending dev #80953): separator, hex and Arabic " +
      "digit text pairs are skipped")
   void f6b_separatorHexAndArabicDigitText_pairIsSkipped() {
      CovarianceFormula clean = new CovarianceFormula(2);
      pairs(clean, p(1, 2), p(2, 7), p(3, 4));

      for(String text : new String[] { "1,000", "1,5", "0x10", "٣" }) {
         CovarianceFormula withText = new CovarianceFormula(2);
         pairs(withText, p(1, 2), p(2, 7), p(text, 9), p(3, 4));
         assertEquals(clean.getResult(), withText.getResult(), text);
      }
   }
}
