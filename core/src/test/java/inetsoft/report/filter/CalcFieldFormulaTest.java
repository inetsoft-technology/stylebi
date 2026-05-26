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
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CalcFieldFormula.
 *
 * <p>CalcFieldFormula wraps one or more child formulas and evaluates a JavaScript expression
 * over their results. These tests cover the lifecycle methods (addValue, isNull, reset, clone)
 * and percentage-type behavior. Script evaluation is tested using a simple arithmetic expression.
 */
@SreeHome
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
}
