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
package inetsoft.graph.guide.form;

import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Target line parameter: a constant or a formula over the post-aggregate data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TargetParameterTest {
   private static final double N = Tool.NULL_DOUBLE;

   @Test
   @DisplayName("constant parameter returns its value for empty and non-empty data, no formula")
   void constantParameter() {
      TargetParameter parameter = new TargetParameter(3.5);
      assertEquals(3.5, parameter.getRuntimeValue(new double[0]));
      assertEquals(3.5, parameter.getRuntimeValue(new double[] { 1, 2 }));
      assertNull(parameter.getFormula());
   }

   @Test
   @DisplayName("default parameter is the constant 0.0 without formula")
   void defaultParameter() {
      TargetParameter parameter = new TargetParameter();
      assertEquals(0.0, parameter.getConstantValue());
      assertNull(parameter.getFormula());
      assertEquals("0.0", parameter.toString());
   }

   @Test
   @DisplayName("formula parameter is recalculated from scratch on every call")
   void formulaIsResetBetweenCalls() {
      TargetParameter parameter = new TargetParameter(new SumFormula());
      double[] data = { 1, 2, 3 };
      assertEquals(6.0, parameter.getRuntimeValue(data));
      assertEquals(6.0, parameter.getRuntimeValue(data));
   }

   @Test
   @DisplayName("average ignores null cells")
   void averageIgnoresNulls() {
      assertEquals(3.0, new TargetParameter(new AverageFormula())
         .getRuntimeValue(new double[] { N, 2, 4 }), 1e-12);
   }

   static Stream<Arguments> formulas() {
      return Stream.of(
         Arguments.of("Sum", (Supplier<Formula>) SumFormula::new, 6.0),
         Arguments.of("Max", (Supplier<Formula>) MaxFormula::new, 3.0),
         Arguments.of("Min", (Supplier<Formula>) MinFormula::new, 1.0),
         Arguments.of("Median", (Supplier<Formula>) MedianFormula::new, 2.0),
         Arguments.of("Count", (Supplier<Formula>) CountFormula::new, 3.0));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("formulas")
   @DisplayName("formulas give the result of the non-null values when null cells are present")
   void formulasIgnoreNulls(String name, Supplier<Formula> formula, double expected) {
      assertEquals(expected,
                   new TargetParameter(formula.get()).getRuntimeValue(new double[] { 1, 2, 3 }), 1e-12);
      assertEquals(expected,
                   new TargetParameter(formula.get()).getRuntimeValue(new double[] { N, 1, 2, 3, N }),
                   1e-12);
   }

   @Test
   @DisplayName("formula wins over the constant when both are set")
   void formulaWinsOverConstant() {
      assertEquals(3.0, new TargetParameter(new SumFormula(), 7)
         .getRuntimeValue(new double[] { 1, 2 }));
   }

   @Test
   @DisplayName("pinned current behavior: Sum, Average and Max of no data are 0.0")
   void emptyDataFormulasGiveZero() {
      for(Formula formula : new Formula[] { new SumFormula(), new AverageFormula(),
                                            new MaxFormula() })
      {
         assertEquals(0.0, new TargetParameter(formula).getRuntimeValue(new double[0]),
                      formula.getName());
      }
   }

   @Test
   @DisplayName("pinned current behavior: Max of only null cells is 0.0, not the placeholder")
   void maxOfNullsIsZero() {
      assertEquals(0.0, new TargetParameter(new MaxFormula()).getRuntimeValue(new double[] { N, N }));
   }

   @Test
   @DisplayName("removing the formula falls back to the constant")
   void removingFormulaFallsBack() {
      TargetParameter parameter = new TargetParameter(new SumFormula(), 1);
      parameter.setFormula(null);
      parameter.setConstantValue(4.25);
      assertEquals(4.25, parameter.getRuntimeValue(new double[] { 1, 2 }));
   }

   @Test
   @DisplayName("NaN, infinities and MAX_VALUE constants pass through unchanged")
   void constantsPassThrough() {
      for(double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY,
                                        Double.NEGATIVE_INFINITY, Double.MAX_VALUE })
      {
         assertEquals(value, new TargetParameter(value).getRuntimeValue(new double[] { 1 }));
      }
   }

   @ParameterizedTest(name = "{0} shows as {1}")
   @MethodSource("constantTexts")
   @DisplayName("constant parameter text is its Double.toString")
   void constantText(double value, String expected) {
      assertEquals(expected, new TargetParameter(value).toString());
   }

   static Stream<Arguments> constantTexts() {
      return Stream.of(Arguments.of(5.0, "5.0"), Arguments.of(-2.5, "-2.5"),
                       Arguments.of(0.0, "0.0"), Arguments.of(1e-5, "1.0E-5"));
   }

   @Test
   @DisplayName("formula parameter text is the formula display name")
   void formulaText() {
      assertEquals("Max", new TargetParameter(new MaxFormula()).toString());
   }

   private static TargetParameter named(String name) {
      Formula formula = mock(Formula.class);
      when(formula.getDisplayName()).thenReturn(name);
      return new TargetParameter(formula);
   }

   @ParameterizedTest(name = "{0} characters")
   @org.junit.jupiter.params.provider.ValueSource(ints = { 20, 21, 25 })
   @DisplayName("text longer than 20 characters is cut to 17 characters plus an ellipsis")
   void longTextIsTrimmed(int length) {
      String name = "abcdefghijklmnopqrstuvwxyz".substring(0, length);
      String expected = length <= 20 ? name : name.substring(0, 17) + "...";
      assertEquals(expected, named(name).toString());
   }

   @Test
   @DisplayName("long constant text is trimmed to 20 characters ending with an ellipsis")
   void longConstantIsTrimmed() {
      String text = new TargetParameter(-1.2345678901234567E-100).toString();
      assertEquals(20, text.length());
      assertTrue(text.endsWith("..."), text);
   }
}
