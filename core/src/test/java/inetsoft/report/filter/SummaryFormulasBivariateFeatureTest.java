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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Correlation and Covariance summary formulas, driven directly through the formula API
 * (a value is an {@code Object[]} pair, x then y) and through a real table summary, where the
 * second column is named by the formula's column index.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasBivariateFeatureTest {
   private static final double EPS = 1e-12;

   private static void pairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue(pair);
      }
   }

   private static Object[] p(Object x, Object y) {
      return new Object[] { x, y };
   }

   /**
    * Summarize {g, x, y} rows by g: group rows 1.., then the grand total row.
    */
   private static SummaryFilter summarize(Formula formula, Object[][] rows) {
      Object[][] cells = new Object[rows.length + 1][];
      cells[0] = new Object[] { "g", "x", "y" };
      System.arraycopy(rows, 0, cells, 1, rows.length);
      TableLens data = new DefaultTableLens(cells);
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, formula, formula);
      summary.moreRows(Integer.MAX_VALUE);
      return summary;
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-coefficient")
   void correlationCoefficient() {
      CorrelationFormula correl = new CorrelationFormula(2);
      assertEquals(XConstants.CORRELATION_FORMULA, correl.getName());
      assertEquals(Double.class, correl.getResultType());
      assertArrayEquals(new int[] { 2 }, correl.getSecondaryColumns());

      // perfectly correlated, anti-correlated and unrelated columns
      pairs(correl, p(1, 2), p(2, 4), p(3, 6));
      assertEquals(1.0, (Double) correl.getResult(), EPS);
      assertFalse(correl.isNull());

      correl.reset();
      pairs(correl, p(1, 6), p(2, 4), p(3, 2));
      assertEquals(-1.0, (Double) correl.getResult(), EPS);

      correl.reset();
      pairs(correl, p(1, 2), p(2, 5), p(3, 1), p(4, 5), p(5, 2));
      assertEquals(0.0, (Double) correl.getResult(), EPS);

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

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-coefficient")
   void correlationInATableSummaryUsesTheSecondColumn() {
      Object[][] rows = {
         { "a", 1, 2 }, { "a", 2, 4 }, { "a", 3, 6 },
         { "b", 1, 6 }, { "b", 2, 4 }, { "b", 3, 2 }
      };
      SummaryFilter summary = summarize(new CorrelationFormula(2), rows);

      // one row for each group, then the grand total over all six pairs
      assertEquals(4, summary.getRowCount());
      assertEquals("a", summary.getObject(1, 0));
      assertEquals(1.0, (Double) summary.getObject(1, 1), EPS);
      assertEquals("b", summary.getObject(2, 0));
      assertEquals(-1.0, (Double) summary.getObject(2, 1), EPS);
      // x = 1,2,3,1,2,3 and y = 2,4,6,6,4,2: sxy = 2+8+18+6+8+6 = 48, sx = 12, sy = 24,
      // sxx = 28, syy = 112: r = (6*48 - 12*24) / sqrt((6*28 - 144) * (6*112 - 576)) = 0
      assertEquals(0.0, (Double) summary.getObject(3, 1), EPS);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#covariance-value")
   void covarianceValue() {
      CovarianceFormula covar = new CovarianceFormula(2);
      assertEquals(XConstants.COVARIANCE_FORMULA, covar.getName());
      assertEquals(Double.class, covar.getResultType());
      assertArrayEquals(new int[] { 2 }, covar.getSecondaryColumns());

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

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#covariance-value")
   void covarianceInATableSummaryUsesTheSecondColumn() {
      Object[][] rows = {
         { "a", 1, 2 }, { "a", 2, 4 }, { "a", 3, 6 }, { "b", 1, 6 }, { "b", 3, 2 }
      };
      SummaryFilter summary = summarize(new CovarianceFormula(2), rows);

      assertEquals(4, summary.getRowCount());
      assertEquals(4.0 / 3, (Double) summary.getObject(1, 1), EPS);
      // (1,6),(3,2): means 2 and 4, (-1)(2) + (1)(-2) = -4, over n = 2
      assertEquals(-2.0, (Double) summary.getObject(2, 1), EPS);
      // all five pairs: sx = 10, sy = 20, sxy = 2+8+18+6+6 = 40, so 40/5 - 2*4 = 0
      assertEquals(0.0, (Double) summary.getObject(3, 1), EPS);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-no-numeric-input-null")
   void correlationNoNumericInputNull() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // nothing added: no result
      assertNull(correl.getResult());
      assertTrue(correl.isNull());
      assertEquals(0, correl.getDoubleResult());

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
      assertNotNull(correl.getResult());
      correl.reset();
      assertNull(correl.getResult());
      assertTrue(correl.isNull());

      // a table group whose pairs are all null shows no result, the next group still computes
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", null, null }, { "a", null, null },
         { "b", 1, 1 }, { "b", 2, 2 }, { "b", 3, 3 }
      });
      assertEquals(4, summary.getRowCount());
      assertNull(summary.getObject(1, 1));
      assertEquals(1.0, (Double) summary.getObject(2, 1), EPS);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#covariance-no-numeric-input-null")
   void covarianceNoNumericInputNull() {
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

      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", null, null }, { "a", null, null }, { "b", 1, 2 }, { "b", 2, 4 }, { "b", 3, 6 }
      });
      assertEquals(4, summary.getRowCount());
      assertNull(summary.getObject(1, 1));
      assertEquals(4.0 / 3, (Double) summary.getObject(2, 1), EPS);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-skips-incomplete-pairs")
   void correlationSkipsIncompletePairs() {
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

      // a table summary skips them the same way, in the group and in the grand total
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", null, 9 }, { "a", 2, 7 }, { "a", "x", 5 },
         { "a", 3, 4 }, { "a", 4, null }, { "a", 4, 5 }
      });
      assertEquals(3, summary.getRowCount());
      assertEquals(expected, summary.getObject(1, 1));
      assertEquals(expected, summary.getObject(2, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#covariance-skips-incomplete-pairs")
   void covarianceSkipsIncompletePairs() {
      CovarianceFormula whole = new CovarianceFormula(2);
      pairs(whole, p(1, 2), p(2, 4), p(3, 6));
      Double expected = (Double) whole.getResult();
      assertEquals(4.0 / 3, expected, EPS);

      CovarianceFormula mixed = new CovarianceFormula(2);
      pairs(mixed, p(null, null), p(1, 2), p(null, 9), p(2, 4), p(8, null), p("a", 5),
            p(3, 6), p(5, "b"), p("3", "a"), p(6, null));
      assertEquals(expected, mixed.getResult());
      assertFalse(mixed.isNull());

      // a table summary skips them the same way
      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", null, 9 }, { "a", 2, 4 }, { "a", "x", 5 },
         { "a", 3, 6 }, { "a", 6, null }
      });
      assertEquals(3, summary.getRowCount());
      assertEquals(expected, summary.getObject(1, 1));
      assertEquals(expected, summary.getObject(2, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-zero-variance")
   void correlationZeroVariance() {
      CorrelationFormula correl = new CorrelationFormula(2);

      // a constant second column: no variance, so no coefficient
      pairs(correl, p(1, 5), p(2, 5), p(3, 5));
      assertNull(correl.getResult());

      // a constant first column too
      correl.reset();
      pairs(correl, p(4, 1), p(4, 2), p(4, 3));
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

      // a table group of one pair shows no value, and a constant group too
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 5 }, { "b", 1, 5 }, { "b", 2, 5 }, { "b", 3, 5 }
      });
      assertEquals(4, summary.getRowCount());
      assertNull(summary.getObject(1, 1));
      assertNull(summary.getObject(2, 1));
      assertNull(summary.getObject(3, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-primitive-pair-path")
   void primitivePairIgnoresNullAndTooShortArrays() {
      CorrelationFormula correl = new CorrelationFormula(2);
      CovarianceFormula covar = new CovarianceFormula(2);

      // a null array and an array with fewer than two members add nothing
      for(Formula formula : new Formula[] { correl, covar }) {
         formula.addValue((double[]) null);
         formula.addValue(new double[0]);
         formula.addValue(new double[] { 3 });
         assertTrue(formula.isNull());
         assertNull(formula.getResult());
      }

      // the single-number forms are not supported by a two-column formula
      for(Formula formula : new Formula[] { correl, covar }) {
         assertUnsupported(() -> formula.addValue(1.5));
         assertUnsupported(() -> formula.addValue(1));
         assertUnsupported(() -> formula.addValue(1L));
         assertUnsupported(() -> formula.addValue(1f));
         assertUnsupported(() -> formula.addValue((short) 1));
      }
   }

   private static void assertUnsupported(org.junit.jupiter.api.function.Executable call) {
      assertEquals("Unsupported method called!",
                   assertThrows(RuntimeException.class, call).getMessage());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-primitive-pair-path")
   void primitivePairIsTheSameAsTheObjectPair() {
      // MVRow -> FormulaInfo2.addValue and CalcFieldFormula.addValue pass {primary, secondary}
      CorrelationFormula correl = new CorrelationFormula(2);
      correl.addValue(new double[] { 1, 2 });
      correl.addValue(new double[] { 2, 4 });
      correl.addValue(new double[] { 3, 6 });
      assertEquals(1.0, (Double) correl.getResult(), EPS);

      // a pair with a null marker is skipped, as the Object form skips a null member
      correl.addValue(new double[] { Tool.NULL_DOUBLE, 9 });
      correl.addValue(new double[] { 9, Tool.NULL_DOUBLE });
      assertEquals(1.0, (Double) correl.getResult(), EPS);

      CovarianceFormula covar = new CovarianceFormula(2);
      covar.addValue(new double[] { 1, 2 });
      covar.addValue(new double[] { 2, 4 });
      covar.addValue(new double[] { 3, 6 });
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);

      covar.addValue(new double[] { Tool.NULL_DOUBLE, 9 });
      covar.addValue(new double[] { 9, Tool.NULL_DOUBLE });
      assertEquals(4.0 / 3, (Double) covar.getResult(), EPS);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#correlation-skips-incomplete-pairs")
   void correlationSkipsABlankTextPair() {
      CorrelationFormula whole = new CorrelationFormula(2);
      pairs(whole, p(1, 2), p(2, 7), p(3, 4), p(4, 5));

      CorrelationFormula blank = new CorrelationFormula(2);
      pairs(blank, p(1, 2), p("", ""), p(2, 7), p("", 5), p(3, 4), p(4, 5));
      assertEquals(whole.getResult(), blank.getResult());

      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", "", "" }, { "a", 2, 7 }, { "a", 3, 4 }, { "a", 4, 5 }
      });
      assertEquals(whole.getResult(), summary.getObject(1, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.bivariate#covariance-skips-incomplete-pairs")
   void covarianceSkipsABlankTextPair() {
      CovarianceFormula whole = new CovarianceFormula(2);
      pairs(whole, p(1, 2), p(2, 4), p(3, 6));

      CovarianceFormula blank = new CovarianceFormula(2);
      pairs(blank, p(1, 2), p("", ""), p(2, 4), p("", 5), p(3, 6));
      assertEquals(whole.getResult(), blank.getResult());

      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", "", "" }, { "a", 2, 4 }, { "a", 3, 6 }
      });
      assertEquals(whole.getResult(), summary.getObject(1, 1));
   }

   @Test
   @DisplayName("Bug #80701: blank text pairs change nothing, and other lists are unchanged")
   void blankTextPairsChangeNothing() {
      java.util.Random random = new java.util.Random(80701L);
      Object[] pool = { 1, 2.5, -3, 10L, "4", "-7.5", " 8 ", "", "  ", "abc", null };

      for(int i = 0; i < 400; i++) {
         java.util.List<Object[]> all = new java.util.ArrayList<>();
         java.util.List<Object[]> clean = new java.util.ArrayList<>();
         int size = random.nextInt(8);

         for(int j = 0; j < size; j++) {
            Object[] pair = p(pool[random.nextInt(pool.length)], pool[random.nextInt(pool.length)]);
            all.add(pair);

            if(!isBlank(pair[0]) && !isBlank(pair[1])) {
               clean.add(pair);
            }
         }

         Formula[][] formulas = {
            { new CorrelationFormula(2), new CorrelationFormula(2) },
            { new CovarianceFormula(2), new CovarianceFormula(2) } };

         for(Formula[] f : formulas) {
            all.forEach(f[0]::addValue);
            clean.forEach(f[1]::addValue);
            assertEquals(f[1].getResult(), f[0].getResult(), f[0].getName() + " "
               + java.util.Arrays.deepToString(all.toArray()));
         }
      }
   }

   private static boolean isBlank(Object value) {
      return value instanceof String && ((String) value).trim().isEmpty();
   }

   @Test
   @DisplayName("Bug #80700: the primitive pair of a summary column is added as one pair")
   void primitivePairFromTheSummaryColumnLayout() {
      // FormulaInfo2 fills {primary, secondary} for the two column indexes the formula names
      double[][] rows = { { 1, 2 }, { 2, 7 }, { 3, 4 }, { 4, 5 } };
      CorrelationFormula primitive = new CorrelationFormula(2);
      CorrelationFormula object = new CorrelationFormula(2);
      CovarianceFormula covPrimitive = new CovarianceFormula(2);
      CovarianceFormula covObject = new CovarianceFormula(2);

      for(double[] row : rows) {
         primitive.addValue(row);
         object.addValue(p(row[0], row[1]));
         covPrimitive.addValue(row);
         covObject.addValue(p(row[0], row[1]));
      }

      assertEquals(object.getResult(), primitive.getResult());
      assertEquals(covObject.getResult(), covPrimitive.getResult());
      assertEquals(0.3721, (Double) primitive.getResult(), 1e-4);

      // a short array is still ignored
      primitive.addValue(new double[] { 1 });
      covPrimitive.addValue(new double[] { 1 });
      assertEquals(object.getResult(), primitive.getResult());
      assertEquals(covObject.getResult(), covPrimitive.getResult());
   }

   @Test
   @DisplayName("Bug #80700: a materialized view row feeds Correlation and Covariance one pair")
   void materializedViewRowFeedsOnePair() {
      // GroupedTableBlock builds FormulaInfo over the measure indexes of the MV row, and
      // FormulaInfo2 hands the formula {primary, secondary}
      inetsoft.mv.data.FormulaInfo correl = inetsoft.mv.data.FormulaInfo.create(
         new CorrelationFormula(), new int[] { 3, 1 });
      inetsoft.mv.data.FormulaInfo covar = inetsoft.mv.data.FormulaInfo.create(
         new CovarianceFormula(), new int[] { 3, 1 });
      // measure columns of the row: 0 unused, 1 secondary, 2 unused, 3 primary
      double[][] rows = { { 0, 2, 0, 1 }, { 0, 7, 0, 2 }, { 0, 4, 0, 3 }, { 0, 5, 0, 4 },
                          { 0, 9, 0, Tool.NULL_DOUBLE } };

      for(double[] row : rows) {
         correl.addValue(row);
         covar.addValue(row);
      }

      assertEquals(0.3721, (Double) correl.getFormula().getResult(), 1e-4);
      // covariance of x = 1..4 and y = 2, 7, 4, 5
      assertEquals(0.75, (Double) covar.getFormula().getResult(), EPS);
   }
}
