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
import inetsoft.report.internal.Util;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
@SreeHome
@Tag("core")
public class CalcFieldFormulaTest {

   private AssetQuerySandbox box;

   @BeforeEach
   void setUp() throws Exception {
      box = new AssetQuerySandbox(new Worksheet());
   }

   @AfterEach
   void tearDown() {
      box.dispose();
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
      // the value reaches the child
      assertEquals(5.0, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   void addValueInt_normalValue_incrementsCount() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(7);
      assertFalse(formula.isNull());
      // the value reaches the child
      assertEquals(7.0, asDouble(formula.getResult()), 1e-10);
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
   @DisplayName("SetTotal sets the denominator of the percentage")
   void setTotal_setsTheDenominatorOfThePercentage() {
      CalcFieldFormula formula = buildSingleChildFormula("SUM", new SumFormula(), "SUM");
      formula.addValue(new Object[]{ null, 10.0 });
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
      formula.setTotal(Double.valueOf(40.0));
      assertEquals(0.25, asDouble(formula.getResult()), 1e-10);
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
      // the expression is stored as given
      assertEquals("SUM", formula.getExpression());
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
      // a quotient is not commutative
      CalcFieldFormula formula = calc(avgA + " / " + avgB, new String[] { avgA, avgB },
                                      new AverageFormula(), new AverageFormula());
      // a: (2 + 4) / 2 = 3, b: (5 + 7) / 2 = 6
      addRows(formula, new Object[] { 2, 5 }, new Object[] { 4, 7 });
      assertEquals(0.5, asDouble(formula.getResult()), 1e-10);
   }

   // multi-aggregate arithmetic with two-column children (vscalc: Correlation+Correlation)
   @Test
   void expression_sumOfTwoCorrelations() {
      String corrXY = "field['Correlation([x], [y])']";
      String corrXZ = "field['Correlation([x], [z])']";
      // a weighted expression, so that a swapped name-to-child mapping is seen
      CalcFieldFormula formula = calc(corrXY + " * 10 + " + corrXZ, new String[] { corrXY, corrXZ },
                                      new CorrelationFormula(2), new CorrelationFormula(4));
      // y = 2x gives +1, z = 4 - x gives -1; the row is {x, y, x, z}
      addRows(formula, new Object[] { 1, 2, 1, 3 }, new Object[] { 2, 4, 2, 2 },
              new Object[] { 3, 6, 3, 1 });
      assertEquals(9.0, asDouble(formula.getResult()), 1e-9);
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
      // a weight per child, so that a swapped slot is seen
      CalcFieldFormula formula = calc(SUM_A + " * 100 + " + wavg + " * 10 + " + count,
                                      new String[] { SUM_A, wavg, count },
                                      new SumFormula(), new WeightedAverageFormula(3),
                                      new CountFormula());
      addRows(formula, new Object[] { 1, 10, 1, "p" }, new Object[] { 2, 20, 3, null });
      // Sum(a) = 3, WeightedAverage = (10 * 1 + 20 * 3) / 4 = 17.5, Count(d) = 1
      assertEquals(300 + 175 + 1, asDouble(formula.getResult()), 1e-10);
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

   // -----------------------------------------------------------------------
   // Test Case #80957 hardening (vscalc matrix rows C*, T*, A*)
   // -----------------------------------------------------------------------

   private static CalcFieldFormula withTotal(CalcFieldFormula formula, int type, Object total) {
      formula.setPercentageType(type);
      formula.setTotal(total);
      return formula;
   }

   @Test
   @DisplayName("A clone is independent of the original after values were added")
   void clone_isIndependentAfterValues() {
      CalcFieldFormula formula = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 4 }, new Object[] { 6 });
      assertEquals(10.0, asDouble(formula.getResult()), 1e-10);

      CalcFieldFormula copy = (CalcFieldFormula) formula.clone();
      addRows(copy, new Object[] { 5 });
      assertEquals(15.0, asDouble(copy.getResult()), 1e-10);
      assertEquals(10.0, asDouble(formula.getResult()), 1e-10);

      // the same when the original was not read before the clone, so it has no cached result
      CalcFieldFormula fresh = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(fresh, new Object[] { 4 }, new Object[] { 6 });
      CalcFieldFormula freshCopy = (CalcFieldFormula) fresh.clone();
      addRows(freshCopy, new Object[] { 5 });
      assertEquals(10.0, asDouble(fresh.getResult()), 1e-10);
      assertEquals(15.0, asDouble(freshCopy.getResult()), 1e-10);
   }

   @Test
   @DisplayName("A percentage of a zero total is null, or 0 with the default result")
   void percentageOfZeroTotal() {
      CalcFieldFormula formula = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 10 });
      withTotal(formula, StyleConstants.PERCENTAGE_OF_GRANDTOTAL, 0.0);
      assertNull(formula.getResult());

      CalcFieldFormula withDefault = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(withDefault, new Object[] { 10 });
      withDefault.setDefaultResult(true);
      withTotal(withDefault, StyleConstants.PERCENTAGE_OF_GRANDTOTAL, 0.0);
      assertEquals(0.0, asDouble(withDefault.getResult()), 1e-10);
   }

   @Test
   @DisplayName("A group percentage divides by a numeric total only")
   void percentageOfGroup_numericTotalOnly() {
      CalcFieldFormula formula = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 10 });
      withTotal(formula, StyleConstants.PERCENTAGE_OF_GROUP, 40.0);
      assertEquals(0.25, asDouble(formula.getResult()), 1e-10);

      CalcFieldFormula nullTotal = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(nullTotal, new Object[] { 10 });
      withTotal(nullTotal, StyleConstants.PERCENTAGE_OF_GROUP, null);
      assertEquals(10.0, asDouble(nullTotal.getResult()), 1e-10);

      CalcFieldFormula textTotal = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(textTotal, new Object[] { 10 });
      withTotal(textTotal, StyleConstants.PERCENTAGE_OF_GROUP, "40");
      assertEquals(10.0, asDouble(textTotal.getResult()), 1e-10);
   }

   @Test
   @DisplayName("A text result is returned unchanged under a percentage")
   void textResult_unchangedUnderPercentage() {
      String expr = "if(" + SUM_A + " > 5) { 'High' } else { 'Low' }";
      CalcFieldFormula formula = calc(expr, new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 10 });
      withTotal(formula, StyleConstants.PERCENTAGE_OF_GRANDTOTAL, 10.0);
      assertEquals("High", formula.getResult());
   }

   @Disabled("Bug #81212: CalcFieldFormula.setTotal does not clear the cached result")
   @Test
   @DisplayName("SetTotal after getResult invalidates the cached result")
   void setTotalAfterGetResult_recomputes() {
      CalcFieldFormula formula = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 10 });
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GRANDTOTAL);
      formula.setTotal(10.0);
      assertEquals(1.0, asDouble(formula.getResult()), 1e-10);
      formula.setTotal(40.0);
      assertEquals(0.25, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("An unknown aggregate name fails with a ScriptException")
   void unknownIdentifier_throwsScriptException() {
      CalcFieldFormula formula = calc("field['Sum([zzz])']", new String[] { SUM_A },
                                      new SumFormula());
      addRows(formula, new Object[] { 1 });
      ScriptException ex = assertThrows(ScriptException.class, formula::getResult);
      assertTrue(ex.getMessage().startsWith("Script error"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Script failed:"), ex.getMessage());
   }

   @Disabled("Bug #81202: Aggregate-name mapping: lower-case name wrong value / a b and a_b collide / ß throws")
   @Test
   @DisplayName("Aggregate names that differ only in space and underscore stay separate")
   void nameCollision_spaceAndUnderscore() {
      String spaced = "field['Sum([a b])']";
      String underscored = "field['Sum([a_b])']";
      CalcFieldFormula formula = calc(spaced + " - " + underscored,
                                      new String[] { spaced, underscored },
                                      new SumFormula(), new SumFormula());
      addRows(formula, new Object[] { 5, 2 });
      assertEquals(3.0, asDouble(formula.getResult()), 1e-10);
   }

   @Disabled("Bug #81202: Aggregate-name mapping: lower-case name wrong value / a b and a_b collide / ß throws")
   @Test
   @DisplayName("An aggregate name whose upper case is longer keeps its value")
   void lengthChangingUpperCase() {
      String name = "field['Sum([straße])']";
      CalcFieldFormula formula = calc(name, new String[] { name }, new SumFormula());
      addRows(formula, new Object[] { 4 });
      assertEquals(4.0, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("A comparison of an aggregate is a Boolean")
   void booleanResult() {
      CalcFieldFormula formula = calc(SUM_A + " > 5", new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 3 }, new Object[] { 4 });
      assertEquals(Boolean.TRUE, formula.getResult());

      formula.reset();
      addRows(formula, new Object[] { 1 });
      assertEquals(Boolean.FALSE, formula.getResult());
   }

   @Test
   @DisplayName("A date function over a date aggregate")
   void dateFunctionOverDateAggregate() {
      String maxDate = "field['Max([d])']";
      CalcFieldFormula formula = calc("CALC.year(" + maxDate + ")", new String[] { maxDate },
                                      new MaxFormula());
      addRows(formula, new Object[] { java.sql.Timestamp.valueOf("2020-01-01 00:00:00") },
              new Object[] { java.sql.Timestamp.valueOf("2020-12-01 00:00:00") });
      assertEquals(2020.0, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("DistinctCount over Count")
   void distinctCountOverCount() {
      String distinct = "field['DistinctCount([a])']";
      String count = "field['Count([a])']";
      CalcFieldFormula formula = calc(distinct + " / " + count, new String[] { distinct, count },
                                      new DistinctCountFormula(), new CountFormula());
      addRows(formula, new Object[] { "x", "x" }, new Object[] { "x", "x" },
              new Object[] { "y", "y" }, new Object[] { "z", "z" });
      assertEquals(0.75, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("A First child between two Sum children reads its own columns")
   void firstChildBetweenSums() {
      String sumX = "field['Sum([x])']";
      String first = "field['First([v], [o])']";
      String sumY = "field['Sum([y])']";
      CalcFieldFormula formula = calc(sumX + " * 100 + " + first + " * 10 + " + sumY,
                                      new String[] { sumX, first, sumY }, new SumFormula(),
                                      Util.createFormula(null, "First(2)"), new SumFormula());
      // First is the value at the smallest order: (10, 3), (20, 1), (30, 2) gives 20
      addRows(formula, new Object[] { 1, 10, 3, 4 }, new Object[] { 2, 20, 1, 5 },
              new Object[] { 3, 30, 2, 6 });
      // Sum(x) 6, First 20, Sum(y) 15
      assertEquals(600 + 200 + 15, asDouble(formula.getResult()), 1e-9);
   }

   @Test
   @DisplayName("A Covariance child followed by a Sum reads its own columns")
   void covarianceChildBeforeSum() {
      String cov = "field['Covariance([x], [y])']";
      String sumZ = "field['Sum([z])']";
      CalcFieldFormula formula = calc(sumZ + " * 100 + " + cov, new String[] { cov, sumZ },
                                      new CovarianceFormula(2), new SumFormula());
      addRows(formula, new Object[] { 1, 2, 1 }, new Object[] { 2, 4, 2 },
              new Object[] { 3, 6, 3 });
      // the population covariance of x = 1, 2, 3 and y = 2, 4, 6 is 4/3, Sum(z) is 6
      assertEquals(600 + 4.0 / 3, asDouble(formula.getResult()), 1e-9);
   }

   @Test
   @DisplayName("A serialized formula keeps its result without the script environment")
   void serialization_keepsTheResult() throws Exception {
      CalcFieldFormula formula = calc(SUM_A + " * 2", new String[] { SUM_A }, new SumFormula());
      addRows(formula, new Object[] { 3 }, new Object[] { 7 });
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

      try(java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
         out.writeObject(formula);
      }

      try(java.io.ObjectInputStream in =
             new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())))
      {
         CalcFieldFormula copy = (CalcFieldFormula) in.readObject();
         assertEquals(20.0, asDouble(copy.getResult()), 1e-10);
      }
   }

   @ParameterizedTest(name = "restricted script: {0}")
   @ValueSource(strings = { "java.lang.System.getProperty('user.dir')",
                            "new java.io.File('x').exists()" })
   void restrictedScript_isRefusedAndFlagRestored(String expression) {
      boolean before = FormulaContext.isRestricted();

      try {
         for(boolean flag : new boolean[] { false, true }) {
            FormulaContext.setRestricted(flag);
            CalcFieldFormula formula = calc(expression, new String[] { SUM_A },
                                            new SumFormula());
            addRows(formula, new Object[] { 1 });
            ScriptException ex = assertThrows(ScriptException.class, formula::getResult);
            assertTrue(ex.getMessage().contains("Class not permitted in scripts"),
                       ex.getMessage());
            assertEquals(flag, FormulaContext.isRestricted());
         }
      }
      finally {
         FormulaContext.setRestricted(before);
      }
   }

   @Test
   @DisplayName("Two children without a result add up to 0")
   void bothChildrenWithoutResult() {
      String maxB = "field['Max([b])']";
      CalcFieldFormula formula = calc(SUM_A + " + " + maxB, new String[] { SUM_A, maxB },
                                      new SumFormula(), new MaxFormula());
      addRows(formula, new Object[] { null, null });
      assertFalse(formula.isNull());
      assertEquals(0.0, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("Ten thousand rows")
   void bigGroup() {
      CalcFieldFormula formula = calc(SUM_A + " / 10000", new String[] { SUM_A },
                                      new SumFormula());

      for(int i = 0; i < 10000; i++) {
         addRows(formula, new Object[] { 1 });
      }

      assertEquals(1.0, asDouble(formula.getResult()), 1e-10);
   }

   @Test
   @DisplayName("The Java type of the result follows the kind of the expression")
   void resultTypeByExpressionKind() {
      CalcFieldFormula number = calc(SUM_A, new String[] { SUM_A }, new SumFormula());
      addRows(number, new Object[] { 2 });
      assertEquals(Double.class, number.getResult().getClass());

      String count = "field['Count([a])']";
      CalcFieldFormula numericText = calc("'1' + " + count, new String[] { count },
                                          new CountFormula());
      addRows(numericText, new Object[] { "x" });
      // current behavior (class B): numeric text is converted to a Double
      assertEquals(Double.class, numericText.getResult().getClass());

      CalcFieldFormula text = calc("'High'", new String[] { SUM_A }, new SumFormula());
      addRows(text, new Object[] { 1 });
      assertEquals("High", text.getResult());

      String maxDate = "field['Max([d])']";
      CalcFieldFormula date = calc(maxDate, new String[] { maxDate }, new MaxFormula());
      java.sql.Timestamp stamp = java.sql.Timestamp.valueOf("2020-05-06 07:08:09");
      addRows(date, new Object[] { stamp });
      assertInstanceOf(java.util.Date.class, date.getResult());
      assertEquals(stamp.getTime(), ((java.util.Date) date.getResult()).getTime());
   }

   @Test
   @DisplayName("A date aggregate stays a date and a comparison stays a Boolean")
   void dateAndBooleanResultsKeepTheirType() {
      String maxDate = "field['Max([d])']";
      CalcFieldFormula date = calc(maxDate, new String[] { maxDate }, new MaxFormula());
      java.sql.Timestamp early = java.sql.Timestamp.valueOf("2020-01-01 00:00:00");
      java.sql.Timestamp late = java.sql.Timestamp.valueOf("2020-12-01 00:00:00");
      addRows(date, new Object[] { early }, new Object[] { late });
      assertEquals(java.sql.Timestamp.class, date.getResult().getClass());
      assertEquals(late.getTime(), ((java.util.Date) date.getResult()).getTime());

      CalcFieldFormula bool = calc(SUM_A + " > 5", new String[] { SUM_A }, new SumFormula());
      addRows(bool, new Object[] { 9 });
      assertEquals(Boolean.TRUE, bool.getResult());
   }

   // -----------------------------------------------------------------------
   // A rows: every user aggregate as the child of an aggregate calc field
   // -----------------------------------------------------------------------

   private static final double[] D = { 2, 4, 4, 4, 5, 5, 7, 9 };

   private static String childName(String spec) {
      String name = spec.contains("(") ? spec.substring(0, spec.indexOf('(')) : spec;
      return "field['" + name + "([a])']";
   }

   private static Object[][] one(Object... values) {
      Object[][] rows = new Object[values.length][];

      for(int i = 0; i < values.length; i++) {
         rows[i] = new Object[] { values[i] };
      }

      return rows;
   }

   private static Object[] dataD(boolean withNulls) {
      java.util.List<Object> list = new java.util.ArrayList<>();

      for(int i = 0; i < D.length; i++) {
         list.add(D[i]);

         if(withNulls && (i == 2 || i == 5)) {
            list.add(null);
         }
      }

      return list.toArray();
   }

   private CalcFieldFormula aggregateCalc(String spec) {
      String name = childName(spec);
      return calc(name + " + 0", new String[] { name }, Util.createFormula(null, spec));
   }

   static java.util.stream.Stream<Arguments> oneColumnOverD() {
      return java.util.stream.Stream.of(
         Arguments.of("Sum", "Sum", 40.0, 1e-10),
         Arguments.of("Average", "Average", 5.0, 1e-10),
         Arguments.of("Count", "Count", 8.0, 1e-10),
         Arguments.of("Max", "Max", 9.0, 1e-10),
         Arguments.of("Min", "Min", 2.0, 1e-10),
         Arguments.of("DistinctCount", "DistinctCount", 5.0, 1e-10),
         Arguments.of("Mode", "Mode", 4.0, 1e-10),
         Arguments.of("NthMostFrequent(2)", "NthMostFrequent(2)", 5.0, 1e-10),
         Arguments.of("Median", "Median", 4.5, 1e-10),
         Arguments.of("Product", "Product", 201600.0, 1e-6),
         Arguments.of("NthLargest(2)", "NthLargest(2)", 7.0, 1e-10),
         Arguments.of("NthSmallest(2)", "NthSmallest(2)", 4.0, 1e-10),
         Arguments.of("Variance", "Variance", 32.0 / 7, 1e-9),
         Arguments.of("StandardDeviation", "StandardDeviation", Math.sqrt(32.0 / 7), 1e-9),
         Arguments.of("PopulationVariance", "PopulationVariance", 4.0, 1e-9),
         Arguments.of("PopulationStandardDeviation", "PopulationStandardDeviation", 2.0,
                      1e-9));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("oneColumnOverD")
   void a4_oneColumnAggregate_overD(String label, String spec, double expected, double tol) {
      CalcFieldFormula formula = aggregateCalc(spec);
      addRows(formula, one(dataD(false)));
      assertEquals(expected, asDouble(formula.getResult()), tol, label);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("oneColumnOverD")
   void nullValuesInD_doNotChangeTheResult(String label, String spec, double expected,
                                               double tol)
   {
      CalcFieldFormula formula = aggregateCalc(spec);
      addRows(formula, one(dataD(true)));
      assertEquals(expected, asDouble(formula.getResult()), tol, label);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("oneColumnOverD")
   void emptyGroup_nullOrDefaultZero(String label, String spec, double expected, double tol) {
      CalcFieldFormula formula = aggregateCalc(spec);
      formula.setDefaultResult(false);
      assertNull(formula.getResult(), label);

      formula.setDefaultResult(true);
      assertEquals(0.0, asDouble(formula.getResult()), 1e-10, label);
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): PthPercentile uses the " +
      "nearest rank, Median averages the two middle values")
   void pin_percentileNearestRank() {
      for(Object[] row : new Object[][] { { "PthPercentile(50)", 5.0 },
                                          { "PthPercentile(25)", 4.0 } })
      {
         CalcFieldFormula formula = aggregateCalc((String) row[0]);
         addRows(formula, one(dataD(false)));
         assertEquals((Double) row[1], asDouble(formula.getResult()), 1e-10, (String) row[0]);
      }

      // the median of the same data is 4.5
      CalcFieldFormula median = aggregateCalc("Median");
      addRows(median, one(dataD(false)));
      assertEquals(4.5, asDouble(median.getResult()), 1e-10);
   }

   @Test
   @DisplayName("Two-column children over D")
   void twoColumnChildren() {
      Object[][] ab = new Object[D.length][];
      Object[][] aw = new Object[D.length][];

      for(int i = 0; i < D.length; i++) {
         ab[i] = new Object[] { D[i], 2 * D[i] + 1 };
         aw[i] = new Object[] { D[i], 1 };
      }

      String corr = "field['Correlation([a], [b])']";
      CalcFieldFormula correlation = calc(corr + " + 0", new String[] { corr },
                                          Util.createFormula(null, "Correlation(2)"));
      addRows(correlation, ab);
      assertEquals(1.0, asDouble(correlation.getResult()), 1e-9);

      String cov = "field['Covariance([a], [b])']";
      CalcFieldFormula covariance = calc(cov + " + 0", new String[] { cov },
                                         Util.createFormula(null, "Covariance(2)"));
      addRows(covariance, ab);
      // the population covariance of a and 2a + 1 is 2 * the population variance 4
      assertEquals(8.0, asDouble(covariance.getResult()), 1e-9);

      String wavg = "field['WeightedAverage([a], [w])']";
      CalcFieldFormula weighted = calc(wavg + " + 0", new String[] { wavg },
                                       Util.createFormula(null, "WeightedAverage(2)"));
      addRows(weighted, aw);
      assertEquals(5.0, asDouble(weighted.getResult()), 1e-9);
   }

   @Test
   @DisplayName("First and Last read the value at the smallest and largest order")
   void firstAndLast() {
      Object[][] rows = { { 10, 3 }, { 20, 1 }, { 30, 2 } };
      String first = "field['First([v], [o])']";
      CalcFieldFormula firstCalc = calc(first + " + 0", new String[] { first },
                                        Util.createFormula(null, "First(2)"));
      addRows(firstCalc, rows);
      assertEquals(20.0, asDouble(firstCalc.getResult()), 1e-9);

      String last = "field['Last([v], [o])']";
      CalcFieldFormula lastCalc = calc(last + " + 0", new String[] { last },
                                       Util.createFormula(null, "Last(2)"));
      addRows(lastCalc, rows);
      assertEquals(10.0, asDouble(lastCalc.getResult()), 1e-9);
   }

   @Test
   @DisplayName("Concat joins the values with a comma")
   void concat() {
      String concat = "field['Concat([a])']";
      CalcFieldFormula formula = calc(concat, new String[] { concat },
                                      Util.createFormula(null, "Concat"));
      addRows(formula, one("a", "b", "c"));
      assertEquals("a,b,c", formula.getResult());
   }
}
