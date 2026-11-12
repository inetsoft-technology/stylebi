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

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CalcFieldFormula.
 *
 * <p>CalcFieldFormula wraps one or more child formulas and evaluates a JavaScript expression
 * over their results. These tests cover the lifecycle methods (addValue, isNull, reset, clone)
 * and percentage-type behavior. Script evaluation is tested using a simple arithmetic expression.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcFieldFormulaTest {

   private AssetQuerySandbox box;

   @BeforeEach
   void setUp() throws Exception {
      box = new AssetQuerySandbox(new Worksheet());
   }

   // -----------------------------------------------------------------------
   // Helper: build a CalcFieldFormula with a single SumFormula child
   // using an expression that just returns the child aggregate value.
   // e.g. expression "SUM" where agg name is "SUM" and secondColumns=[1]
   // -----------------------------------------------------------------------

   private CalcFieldFormula buildSingleChildFormula(String aggName, Formula child,
                                                    String expression)
   {
      // secondColumns has one entry per column slot used.
      // For a non-Formula2 child the child occupies 1 slot (startPos[0]=1),
      // so secondColumns.length must equal 1 (position 1+1=2, length=2-1=1).
      return new CalcFieldFormula(
         expression,
         new String[]{ aggName },
         new Formula[]{ child },
         new int[]{ 0 },           // one secondary column placeholder
         box.getScriptEnv(),
         box.getScope()
      );
   }

   // -----------------------------------------------------------------------
   // Constructor — validation
   // -----------------------------------------------------------------------

   @Test
   void constructor_nullExpression_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () ->
         new CalcFieldFormula(null,
            new String[]{ "A" }, new Formula[]{ new SumFormula() },
            new int[]{ 0 }, box.getScriptEnv(), box.getScope()));
   }

   @Test
   void constructor_nullAggs_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () ->
         new CalcFieldFormula("A",
            null, new Formula[]{ new SumFormula() },
            new int[]{ 0 }, box.getScriptEnv(), box.getScope()));
   }

   @Test
   void constructor_nullFormulas_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () ->
         new CalcFieldFormula("A",
            new String[]{ "A" }, null,
            new int[]{ 0 }, box.getScriptEnv(), box.getScope()));
   }

   @Test
   void constructor_nullSecondColumns_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () ->
         new CalcFieldFormula("A",
            new String[]{ "A" }, new Formula[]{ new SumFormula() },
            null, box.getScriptEnv(), box.getScope()));
   }

   @Test
   void constructor_aggsAndFormulasMismatch_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () ->
         new CalcFieldFormula("A",
            new String[]{ "A", "B" }, new Formula[]{ new SumFormula() },
            new int[]{ 0 }, box.getScriptEnv(), box.getScope()));
   }

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(new Object[]{ null, Double.valueOf(5.0) });
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_addValueNullObject_doesNotIncrement() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — no values
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZeroDouble() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setDefaultResult(true);
      assertEquals(0.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) and addValue(Object) — null sentinel handling
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullSentinel_isIgnoredAndCountUnchanged() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // addValue(double) — non-null increments counter
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_normalValue_incrementsCount() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(5.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void addValueInt_normalValue_incrementsCount() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(7);
      assertFalse(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_afterAddingValues_isNullReturnsTrue() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(5.0d);
      formula.reset();
      formula.addValue(3.0d);
      assertFalse(formula.isNull());
   }

   // Bug #77321: an initializer-less let named like a CALC function (value) in a calc
   // field starts undefined for every group, and the CALC function stays usable
   @Test
   void initializerlessLetNamedLikeCalcFunctionStartsUndefinedEveryGroup() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(),
         "let value; SUM > 5 && (value = 'High'); value");
      CalcFieldFormula reader = buildSingleChildFormula("SUM", new SumFormula(),
         "value('7') + SUM");
      java.util.List<Object> out = new java.util.ArrayList<>();
      java.util.List<Object> read = new java.util.ArrayList<>();

      for(double v : new double[] { 10, 1, 1 }) {
         formula.reset();
         formula.addValue(new Object[]{ null, v });
         out.add(formula.getResult());
         reader.reset();
         reader.addValue(new Object[]{ null, v });
         read.add(reader.getResult());
      }

      assertEquals(java.util.Arrays.asList("High", null, null), out);
      assertEquals(java.util.Arrays.asList(17.0, 8.0, 8.0), read);
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_returnsCalcFieldFormulaInstance() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      Object cloned = formula.clone();
      assertInstanceOf(CalcFieldFormula.class, cloned);
   }

   @Test
   void clone_cloneIsNullWhenOriginalIsNull() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      CalcFieldFormula cloned = (CalcFieldFormula) formula.clone();
      assertTrue(cloned.isNull());
   }

   @Test
   void clone_preservesDefaultResult() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setDefaultResult(true);
      CalcFieldFormula cloned = (CalcFieldFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // percentage type
   // -----------------------------------------------------------------------

   @Test
   void setPercentageType_getPercentageType_roundTrip() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      assertEquals(StyleConstants.PERCENTAGE_NONE, formula.getPercentageType());
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
      assertEquals(StyleConstants.PERCENTAGE_OF_GRANDTOTAL, formula.getPercentageType());
   }

   @Test
   void setTotal_doesNotThrow() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setTotal(Double.valueOf(100.0));
      // no assertion needed — verifying no exception is thrown
   }

   // -----------------------------------------------------------------------
   // getOriginalResult() — percentage none
   // -----------------------------------------------------------------------

   @Test
   void getOriginalResult_noPercentage_noValues_returnsNullWhenDefaultFalse() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setDefaultResult(false);
      assertNull(formula.getOriginalResult());
   }

   /**
    * Regression test for a bug where a formula used as another cell's percentage
    * denominator (e.g. a crosstab's grand-total cell, queried via getOriginalResult()
    * while computing every other row's percentage) would permanently return its raw,
    * unpercentaged value from then on -- even after setTotal() was subsequently called
    * to compute its own percentage. getResult() caches its return value, and the old
    * getOriginalResult() implementation forced percentageType to NONE, called
    * getResult() (caching the raw value), then restored percentageType without
    * invalidating that cache.
    */
   @Test
   void getOriginalResult_thenGetResult_stillComputesPercentageNotStaleRawValue() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(new Object[]{ null, Double.valueOf(10.0) });
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);

      // simulate this formula being read as another cell's percentage denominator,
      // as CrossTabFilter.applyPercentage() does for every row sharing this grand total
      Object original = formula.getOriginalResult();
      assertEquals(10.0, ((Number) original).doubleValue(), 1e-10);

      // now compute this formula's own percentage (e.g. the grand-total cell's own
      // 100%, where its denominator is itself)
      formula.setTotal(10.0);

      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(1.0, ((Number) result).doubleValue(), 1e-10);
   }

   @Test
   void getOriginalResult_doesNotDisturbAlreadyCachedPercentageResult() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(new Object[]{ null, Double.valueOf(10.0) });
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
      formula.setTotal(20.0);

      // cache the percentage result (10/20 = 0.5) before getOriginalResult() runs
      Object result = formula.getResult();
      assertEquals(0.5, ((Number) result).doubleValue(), 1e-10);

      Object original = formula.getOriginalResult();
      assertEquals(10.0, ((Number) original).doubleValue(), 1e-10);

      // the previously-cached percentage result must still be intact afterward
      assertEquals(0.5, ((Number) formula.getResult()).doubleValue(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getSecondaryColumns()
   // -----------------------------------------------------------------------

   @Test
   void getSecondaryColumns_returnsSameArrayPassedToConstructor() {
      int[] cols = new int[]{ 42 };
      CalcFieldFormula formula = new CalcFieldFormula(
         "SUM",
         new String[]{ "SUM" },
         new Formula[]{ new SumFormula() },
         cols,
         box.getScriptEnv(),
         box.getScope()
      );
      assertArrayEquals(cols, formula.getSecondaryColumns());
   }

   // -----------------------------------------------------------------------
   // getExpression()
   // -----------------------------------------------------------------------

   @Test
   void getExpression_returnsExpressionPassedToConstructor() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      // expression is stored as-is, then runtime formula is generated via generateRuntimeScript
      assertNotNull(formula.getExpression());
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // Script evaluation: expression combining child formula results
   // -----------------------------------------------------------------------

   // -----------------------------------------------------------------------
   // Regression test for Bug #76566: a null ScriptEnv (e.g. AssetQuerySandbox
   // handed out a null senv snapshot) must fail as a clear, diagnosable
   // ScriptException, not an unhandled NullPointerException.
   // -----------------------------------------------------------------------

   @Test
   void getResult_nullScriptEnv_throwsScriptExceptionNotNPE() {
      // Warm the static script compile cache for this expression using a real
      // ScriptEnv first, so the null-senv instance below hits a cache hit
      // (see ScriptCache.get) instead of exercising the unrelated compile path.
      buildSingleChildFormula("SUM", new SumFormula(), "SUM");

      CalcFieldFormula formula = new CalcFieldFormula(
         "SUM",
         new String[]{ "SUM" },
         new Formula[]{ new SumFormula() },
         new int[]{ 0 },
         null,
         box.getScope()
      );
      formula.addValue(new Object[]{ null, Double.valueOf(5.0) });

      ScriptException ex = assertThrows(ScriptException.class, formula::getResult);
      assertTrue(ex.getMessage().contains("ScriptEnv is not available"));
      assertInstanceOf(NullPointerException.class, ex.getCause());
   }

   @Test
   void getResult_withSumChildFormula_scriptEvaluatesCorrectly() {
      // Use a sum formula as the child and the expression just references the agg name.
      // addValue(Object[]) dispatches to child formulas via the array path.
      SumFormula sumChild = new SumFormula();
      CalcFieldFormula formula = buildSingleChildFormula("SUM", sumChild, "SUM");

      // addValue(Object[]) with length >=2: index 0 is the CalcFieldFormula's own slot,
      // index 1 onwards are the child formula slots (startPos[0]=1).
      formula.addValue(new Object[]{ null, Double.valueOf(3.0) });
      formula.addValue(new Object[]{ null, Double.valueOf(7.0) });

      Object result = formula.getResult();
      assertNotNull(result);
      // SUM child accumulates 3+7=10, expression "SUM" returns that value
      assertEquals(10.0, ((Number) result).doubleValue(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // Expression evaluation in the runtime form (Test Case #80957, vscalc G3).
   // AssetQuery / AbstractCrosstabVSAQuery pass the aggregate names as
   // VSUtil.getAggregateString writes them, e.g. "field['Sum([a])']", and the
   // expression as the user wrote it. generateRuntimeScript replaces each name
   // (case-insensitive) with an identifier, so these tests use that form rather
   // than a bare "SUM". Expected values are computed by hand.
   // -----------------------------------------------------------------------

   private static final String SUM_A = "field['Sum([a])']";
   private static final String SUM_B = "field['Sum([b])']";

   /**
    * A calc formula over the given aggregates. The secondary column slots are derived as
    * AssetQuery does: one per child, two for a two-column (Formula2) child.
    */
   private CalcFieldFormula calc(String expression, String[] names, Formula... forms) {
      int slots = 0;

      for(Formula form : forms) {
         slots += form instanceof Formula2 ? 2 : 1;
      }

      return new CalcFieldFormula(expression, names.clone(), forms, new int[slots],
                                  box.getScriptEnv(), box.getScope());
   }

   /** One row: slot 0 is the calc field's own column, the child columns follow. */
   private static void addRows(CalcFieldFormula formula, Object[]... rows) {
      for(Object[] row : rows) {
         Object[] full = new Object[row.length + 1];
         System.arraycopy(row, 0, full, 1, row.length);
         formula.addValue(full);
      }
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   // multi-aggregate arithmetic (vscalc testQuery / testModel: Sum/Sum)
   @Test
   void expression_ratioOfTwoSums() {
      CalcFieldFormula formula = calc(SUM_A + " / " + SUM_B, new String[] { SUM_A, SUM_B },
                                      new SumFormula(), new SumFormula());
      // a: 3 + 6 = 9, b: 4 + 2 = 6
      addRows(formula, new Object[] { 3, 4 }, new Object[] { 6, 2 });
      assertEquals(1.5, asDouble(formula.getResult()), 1e-10);
   }

   // multi-aggregate arithmetic (vscalc: Average*Average)
   @Test
   void expression_productOfTwoAverages() {
      String avgA = "field['Average([a])']";
      String avgB = "field['Average([b])']";
      CalcFieldFormula formula = calc(avgA + " * " + avgB, new String[] { avgA, avgB },
                                      new AverageFormula(), new AverageFormula());
      // a: (2 + 4) / 2 = 3, b: (5 + 7) / 2 = 6
      addRows(formula, new Object[] { 2, 5 }, new Object[] { 4, 7 });
      assertEquals(18.0, asDouble(formula.getResult()), 1e-10);
   }

   // multi-aggregate arithmetic with two-column children (vscalc: Correlation+Correlation)
   @Test
   void expression_sumOfTwoCorrelations() {
      String corrXY = "field['Correlation([x], [y])']";
      String corrXZ = "field['Correlation([x], [z])']";
      CalcFieldFormula formula = calc(corrXY + " + " + corrXZ, new String[] { corrXY, corrXZ },
                                      new CorrelationFormula(2), new CorrelationFormula(4));
      // y = 2x gives +1, z = 4 - x gives -1; the row is {x, y, x, z}
      addRows(formula, new Object[] { 1, 2, 1, 3 }, new Object[] { 2, 4, 2, 2 },
              new Object[] { 3, 6, 3, 1 });
      assertEquals(0.0, asDouble(formula.getResult()), 1e-9);
   }

   // aggregate plus a constant (vscalc: Mode+10)
   @Test
   void expression_aggregatePlusConstant() {
      String mode = "field['Mode([a])']";
      CalcFieldFormula formula = calc(mode + " + 10", new String[] { mode }, new ModeFormula());
      addRows(formula, new Object[] { 5 }, new Object[] { 5 }, new Object[] { 7 });
      assertEquals(15.0, asDouble(formula.getResult()), 1e-10);
   }

   // aggregate times a constant, and the cached result is cleared by the next value
   @Test
   void expression_aggregateTimesConstant_recomputedAfterAddValue() {
      CalcFieldFormula formula = calc(SUM_A + " * 2", new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 4 });
      assertEquals(8.0, asDouble(formula.getResult()), 1e-10);

      addRows(formula, new Object[] { 1.5 });
      assertEquals(11.0, asDouble(formula.getResult()), 1e-10);
   }

   // if / else with a string result (vscalc: if (Count(Company) > 1500) ...)
   @Test
   void expression_ifElse_returnsTheStringOfTheBranch() {
      String count = "field['Count([Company])']";
      String expr = "if(" + count + " > 2) { 'High' } else { 'Low' }";
      CalcFieldFormula formula = calc(expr, new String[] { count }, new CountFormula());
      addRows(formula, new Object[] { "x" }, new Object[] { "y" }, new Object[] { "z" });
      assertEquals("High", formula.getResult());

      formula.reset();
      addRows(formula, new Object[] { "x" });
      assertEquals("Low", formula.getResult());
   }

   // a string result that parses as a number is returned as a Double, not as the string
   // (getResult0 tries getDouble on every result). Current behavior, not confirmed
   @Test
   void expression_numericStringResult_isReturnedAsDouble() {
      String count = "field['Count([Company])']";
      CalcFieldFormula formula = calc("'1' + " + count, new String[] { count },
                                      new CountFormula());
      addRows(formula, new Object[] { "x" }, new Object[] { "y" });
      // JavaScript '1' + 2 is the string "12"
      assertEquals(12.0, formula.getResult());
   }

   // the columns of a two-column child: Sum(a) + WeightedAverage(b, c) + Count(d).
   // The row is {a, b, c, d}: Sum reads slot 1, WeightedAverage slots 2-3, Count slot 4
   @Test
   void expression_twoColumnChildBetweenOneColumnChildren_readsItsOwnColumns() {
      String wavg = "field['WeightedAverage([b], [c])']";
      String count = "field['Count([d])']";
      CalcFieldFormula formula = calc(SUM_A + " + " + wavg + " + " + count,
                                      new String[] { SUM_A, wavg, count },
                                      new SumFormula(), new WeightedAverageFormula(3),
                                      new CountFormula());
      addRows(formula, new Object[] { 1, 10, 1, "p" }, new Object[] { 2, 20, 3, null });
      // Sum(a) = 3, WeightedAverage = (10 * 1 + 20 * 3) / 4 = 17.5, Count(d) = 1
      assertEquals(21.5, asDouble(formula.getResult()), 1e-10);
   }

   // a two-column child first: Correlation(x, y) then Sum(z); Sum must read slot 3, not 2
   @Test
   void expression_twoColumnChildFirst_nextChildReadsTheColumnAfterIt() {
      String corr = "field['Correlation([x], [y])']";
      String sumZ = "field['Sum([z])']";
      CalcFieldFormula formula = calc(sumZ + " * 10 + " + corr, new String[] { corr, sumZ },
                                      new CorrelationFormula(2), new SumFormula());
      // y = x gives 1; z: 100 + 200 = 300 (a Sum that read y or x instead of z would give 3)
      addRows(formula, new Object[] { 1, 1, 100 }, new Object[] { 2, 2, 200 });
      assertEquals(3001.0, asDouble(formula.getResult()), 1e-9);
   }

   // aggregate names: written in other case, with characters that are not legal in an
   // identifier (':' '(' ')' '[' ']' quotes), and the same aggregate twice
   @Test
   void expression_aggregateNamesWithOtherCaseAndSpecialCharacters() {
      String maxNo = "field['Max([orders:orderno])']";
      String expr = "field['max([orders:orderno])'] * " + maxNo + " - " +
         "FIELD['MAX([ORDERS:ORDERNO])']";
      CalcFieldFormula formula = calc(expr, new String[] { maxNo }, new MaxFormula());
      addRows(formula, new Object[] { 3 }, new Object[] { 5 }, new Object[] { 4 });
      // 5 * 5 - 5
      assertEquals(20.0, asDouble(formula.getResult()), 1e-10);
   }

   // a date result (vscalc: Max(Month(Date))) is returned as the date, not as a number
   @Test
   void expression_dateAggregate_returnsTheDate() {
      String maxDate = "field['Max([Month(Date)])']";
      CalcFieldFormula formula = calc(maxDate, new String[] { maxDate }, new MaxFormula());
      java.sql.Timestamp jan = java.sql.Timestamp.valueOf("2020-01-01 00:00:00");
      java.sql.Timestamp dec = java.sql.Timestamp.valueOf("2020-12-01 00:00:00");
      addRows(formula, new Object[] { jan }, new Object[] { dec });
      Object result = formula.getResult();
      assertInstanceOf(java.util.Date.class, result);
      assertEquals(dec.getTime(), ((java.util.Date) result).getTime());
   }

   // a child with no result is null in the script (JavaScript: 5 + null is 5).
   // Current behavior, not confirmed
   @Test
   void expression_childWithoutResult_isNullInTheScript() {
      String maxB = "field['Max([b])']";
      CalcFieldFormula formula = calc(SUM_A + " + " + maxB, new String[] { SUM_A, maxB },
                                      new SumFormula(), new MaxFormula());
      addRows(formula, new Object[] { 2, null }, new Object[] { 3, null });
      assertEquals(5.0, asDouble(formula.getResult()), 1e-10);

      // the null itself, when the expression returns it
      CalcFieldFormula only = calc(maxB, new String[] { maxB }, new MaxFormula());
      addRows(only, new Object[] { (Object) null });
      assertNull(only.getResult());
   }

   // a group whose rows all have null values: the rows are counted, so isNull is false
   // and the default-result flag does not apply, but the child Sum is null
   @Test
   void expression_groupOfOnlyNullValues_isNotNullButHasNoResult() {
      CalcFieldFormula formula = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      formula.setDefaultResult(true);
      addRows(formula, new Object[] { (Object) null }, new Object[] { (Object) null });
      assertFalse(formula.isNull());
      assertNull(formula.getResult());
   }

   // Sum(a) / Sum(b) with Sum(b) = 0. Pending #80953 question 3c. The script gives Infinity
   // for x / 0 and NaN for 0 / 0, and ScriptValueConverter.toHost turns both into null
   // ("legacy behavior"), so the cell is empty; the default-result flag does not apply
   @Test
   void expression_divideByZero_returnsNull() {
      CalcFieldFormula formula = calc(SUM_A + " / " + SUM_B, new String[] { SUM_A, SUM_B },
                                      new SumFormula(), new SumFormula());
      addRows(formula, new Object[] { 3, 0 });
      assertNull(formula.getResult());

      formula.reset();
      addRows(formula, new Object[] { 0, 0 });
      assertNull(formula.getResult());

      formula.setDefaultResult(true);
      assertNull(formula.getResult());
      assertFalse(formula.isNull());
   }
}
