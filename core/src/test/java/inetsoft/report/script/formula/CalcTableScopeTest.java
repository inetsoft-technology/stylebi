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

package inetsoft.report.script.formula;

import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.TableRow;
import inetsoft.util.script.FunctionObject2;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for CalcTableScope.
 * Some algorithms are used by the report script, which has been removed from stylebi, so only simple testing
 */
public class CalcTableScopeTest {
   private static CalcTableLens calcTableLens;
   private static CalcTableScope calcTableScope;

   @BeforeEach
   void setUp() {
      calcTableLens = new CalcTableLens(objData);
      calcTableLens.setCellName(1,0,"name");
      calcTableScope = new CalcTableScope(calcTableLens);
   }

   /**
    * test CalcTableScope initializes with CalcTableLens
    */
   @Test
   void testCalcTableScopeInitializesWithCalcTableLens() {
      assertNotNull(calcTableScope.get("field", calcTableScope));
      assertNotNull(calcTableScope.get("sum", calcTableScope));

      assertInstanceOf(FunctionObject2.class, calcTableScope.get("sum", null));
      assertEquals("a", calcTableScope.get("$name", null));
   }

   /**
    * test CalcTableScope initializes with RuntimeCalcTableLens
    */
   @Test
   void testCalcTableScopeInitializesWithRuntimeCalcTableLens() {
      RuntimeCalcTableLens runtimeCalcTableLens = new RuntimeCalcTableLens(calcTableLens);
      CalcTableScope calcTableScope = new CalcTableScope(runtimeCalcTableLens);

      assertInstanceOf(FunctionObject2.class, calcTableScope.get("sum", null));
      assertInstanceOf(CalcRef.class, calcTableScope.get("$name", null));
   }

   /**
    * test setRow
    */
   @Test
   void testSetRow() {
      calcTableScope.setRow(2);
      // Verify that the "row" property is updated
      assertEquals(2, calcTableScope.get("row", null));

      // Verify that the "field" property reflects the correct row
      TableRow field = (TableRow) calcTableScope.get("field", null);
      assertEquals("c", field.get("name"));
      assertEquals(2.0, field.get("id"));
   }

   /**
    * test none function
    */
   private static Stream<Arguments> provideNoneTestCases() {
      CalcTableLens calcTableLens1 = new CalcTableLens(objData);
      Object lens1 = FormulaFunctions.toList(calcTableLens1, "field=id,sort=desc");

      return Stream.of(
         // Test case 1: column is cell range, condition and group are useless
         Arguments.of("[1,id]:[3,id]", null, null, 3.0),
         // Test case 2: column is lens, condition and group are useless
         Arguments.of(lens1, null, null, 1.0),
         // Test case 3: test lens， it use for report script which didn't support, so only check simple usage
         Arguments.of(calcTableLens1, "name", "id=2", "b"),
         // Test case 4: The array is empty
         Arguments.of(null, null, null, null)
      );
   }

   @ParameterizedTest
   @MethodSource("provideNoneTestCases")
   void testNone(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.none(obj, group, condition));
   }

   private static Stream<Arguments> provideSumTestCases() {
      return Stream.of(
         // Test case 1: column is cell range, condition and group are useless
         Arguments.of("[1,id]:[3,id]", null, null, 6.0),
         // Test case 2: column is lens, used for report script
         Arguments.of(objData, "id", "id=2", 8.0),   //why is 8.0, should be 4.0
         // Test case 3: test array obj, group  and condition are useless
         Arguments.of(new Object[]{3, 7, 9}, null, null, 19.0),
         // Test case 4: The array is empty
         Arguments.of(null, null, null, null)
      );
   }

   /**
    * test sum
    */
   @ParameterizedTest
   @MethodSource("provideSumTestCases")
   void testSum(Object obj, String group, String condition, Object expected) {
      assertEquals(expected,  calcTableScope.sum(obj, group, condition));
   }

   private static Stream<Arguments> provideAverageTestCases() {
      return Stream.of(
         // Test case 1: column is cell range, condition and group are useless
         Arguments.of("[2,id]:[3,id]", null, null, 2.5),
         // Test case 2: column is lens, used for report script
         Arguments.of(objData, "id", "id=3", 3.0),
         // Test case 3: test array obj, group  and condition are useless
         Arguments.of(new Object[]{3, 7, 8}, null, null, 6.0),
         // Test case 4: The array is empty
         Arguments.of(null, null, null, Double.NaN)
      );
   }

   /**
    * test average
    */
   @ParameterizedTest
   @MethodSource("provideAverageTestCases")
   void testAverage(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.average(obj, group, condition));
   }

   private static Stream<Arguments> provideCountAndDistinctTestCases() {
      return Stream.of(
         Arguments.of("[1,name]:[3,name]", null, null, 3.0, 2.0),
         Arguments.of(objData, "id", null, 4.0, 3.0),
         Arguments.of(null, null, null, null, null)
      );
   }

   /**
    * test count and distinct
    */
   @ParameterizedTest
   @MethodSource("provideCountAndDistinctTestCases")
   void testCountAndDistinct(Object obj, String group, String condition, Object expectCount, Object expectDistinct) {
      assertEquals(expectCount, calcTableScope.count(obj, group, condition));
      assertEquals(expectDistinct, calcTableScope.countDistinct(obj, group, condition));
   }

   private static Stream<Arguments> provideMaxAndMinTestCases() {
      Date date_2021 = new Date(2021 - 1900, 0, 1);
      Date date_2025 = new Date(2025 - 1900, 11, 31);
      Date date_2026 = new Date(2026 - 1900, 9, 20);

      return Stream.of(
         Arguments.of("[1,date]:[3,date]", null, null, date_2025, date_2021),
         Arguments.of(objData, "date", null, date_2026, date_2021),
         Arguments.of(new Object[]{3, 1, 9}, null, null, 9, 1),
         Arguments.of(null, null, null, null, null)
      );
   }

   @ParameterizedTest
   @MethodSource("provideMaxAndMinTestCases")
   void testMaxAndMin(Object obj, String group, String condition, Object expectMax, Object expectMin) {
      assertEquals(expectMax, calcTableScope.max(obj, group, condition));
      assertEquals(expectMin, calcTableScope.min(obj, group, condition));
   }

   private static Stream<Arguments> provideProductTestCases() {
      return Stream.of(
         Arguments.of("[1,id]:[3,id]", null, null, 6.0),
         Arguments.of(objData, "id", "id < 3", 4.0),
         Arguments.of(new Object[]{3, 7, 9}, null, null, 189.0),
         Arguments.of(null, null, null, Double.NaN)
      );
   }

   @ParameterizedTest
   @MethodSource("provideProductTestCases")
   void testProduct(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.product(obj, group, condition));
   }

   private static Stream<Arguments> provideConcatTestCases() {
      return Stream.of(
         Arguments.of("[1,name]:[3,name]", null, null, "a,c,a"),
         Arguments.of(objData, "name", "id < 3", "a,c,b"),
         Arguments.of(new Object[]{"x", "y", "z"}, null, null, "x,y,z"),
         Arguments.of(null, null, null, null)
      );
   }

   @ParameterizedTest
   @MethodSource("provideConcatTestCases")
   void testConcat(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.concat(obj, group, condition));
   }

   private static Stream<Arguments> provideStandardDeviationTestCases() {
      return Stream.of(
         Arguments.of("[2,id]:[4,id]", null, null, "0.5774", "0.4714"),
         Arguments.of(objData, "id", null, "0.8165", "0.7071"),
         Arguments.of(new Object[]{1, 2, 4}, null, null,"1.5275", "1.2472"),
         Arguments.of(null, null, null, "NaN", "NaN")
      );
   }

   @ParameterizedTest
   @MethodSource("provideStandardDeviationTestCases")
   void testStandardDeviationWithPandS(Object obj, String group, String condition, String expectSD, String expectPSD) {
      String resSD = doubleToString(calcTableScope.standardDeviation(obj, group, condition));
      assertEquals(expectSD, resSD);
      String resPSD = doubleToString(calcTableScope.populationStandardDeviation(obj, group, condition));
      assertEquals(expectPSD, resPSD);
   }

   private static Stream<Arguments> provideVarianceTestCases() {
      return Stream.of(
         Arguments.of("[2,id]:[4,id]", null, null, "0.3333", "0.2222"),
         Arguments.of(objData, "id", null, "0.6667", "0.5000"),
         Arguments.of(new Object[]{1, 2, 4}, null, null,"2.3333", "1.5556"),
         Arguments.of(null, null, null, "NaN", "NaN")
      );
   }

   @ParameterizedTest
   @MethodSource("provideVarianceTestCases")
   void testVarianceWithPandS(Object obj, String group, String condition, String expectVar, String expectPVar) {
      String resVar = doubleToString(calcTableScope.variance(obj, group, condition));
      assertEquals(expectVar, resVar);
      String resPVar = doubleToString(calcTableScope.populationVariance(obj, group, condition));
      assertEquals(expectPVar, resPVar);
   }

   private static Stream<Arguments> provideMedianTestCases() {
      return Stream.of(
         Arguments.of("[1,id]:[3,id]", null, null, 2.0),
         Arguments.of(objData, "id", "id<3", 2.0),
         Arguments.of(null, null, null, null)
      );
   }

   @ParameterizedTest
   @MethodSource("provideMedianTestCases")
   void testMedian(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.median(obj, group, condition));
   }

   private static Stream<Arguments> provideModeTestCases() {
      return Stream.of(
         Arguments.of("[1,id]:[3,id]", null, null, 1.0), // No mode, defaults to 1
         Arguments.of(objData, "id", null, 2.0),   // One mode
         Arguments.of(new Object[]{1, 1, 2, 2}, null, null, 1.0), // Multiple modes, returns first
         Arguments.of(null, null, null, null)         // Null mode
      );
   }

   @ParameterizedTest
   @MethodSource("provideModeTestCases")
   void testMode(Object obj, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.mode(obj, group, condition));
   }

   private static Stream<Arguments> provideCorrelationTestCases() {
      return Stream.of(
         Arguments.of(new Integer[]{1, 2, 3}, new Integer[]{3, 2, 1}, null, null, -1.0),
         Arguments.of(new Object[]{1, 2, 3}, new Object[]{4, 5, 6}, null, null, 1.0),
         Arguments.of(objData2, "id1", "id2", null, 1.0),
         Arguments.of(null, null, null, null, Double.NaN)
      );
   }

   @ParameterizedTest
   @MethodSource("provideCorrelationTestCases")
   void testCorrelation(Object x, Object y, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.correlation(x, y, group, condition));
   }

   private static Stream<Arguments> provideCovarianceTestCases() {
      return Stream.of(
         Arguments.of(new Integer[]{1, 2, 3, 4, 5}, new Integer[]{2, 4, 6, 8, 10}, null, null, 4.0),
         Arguments.of(new Object[]{1, 2, 3, 4, 5}, new Object[]{5, 4, 3, 2, 1}, null, null, -2.0),
         Arguments.of(new Object[]{1, 1, 1, 1, 1}, new Object[]{2, 2, 2, 2, 2}, null, null, 0.0),
         Arguments.of(objData2, "id1", "id2", null, 0.5),
         Arguments.of(null, null, null, null, Double.NaN)
      );
   }

   @ParameterizedTest
   @MethodSource("provideCovarianceTestCases")
   void testCovariance(Object x, Object y, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.covariance(x, y, group, condition));
   }

   private static Stream<Arguments> provideWeightedAverageTestCases() {
      return Stream.of(
         Arguments.of(new Object[]{10, 20, 30}, new Object[]{1, 1, 1}, null, null, 20.0),
         Arguments.of(new Object[]{5, 10, 15}, new Object[]{0, 0, 1}, null, null, 15.0),
         Arguments.of(new Object[]{3, 7}, new Object[]{0.3, 0.7}, null, null, 5.799999999999999),
         Arguments.of(objData2, "id1", "id2", null, 2.25),
         Arguments.of(null, null, null, null, Double.NaN)
      );
   }

   @ParameterizedTest
   @MethodSource("provideWeightedAverageTestCases")
   void testWeightedAverage(Object values, Object weights, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.weightedAverage(values, weights, group, condition));
   }

   private static Stream<Arguments> provideFirstLastTestCases() {
      return Stream.of(
         //Arguments.of("[1,id]:[3,id]", null, null, null, 1.0, 2.0),
         Arguments.of(null, null, null, null, null, null),      // Null input
         //for report script
         Arguments.of(objData, "id", "name", "id>1", 3.0, 2.0), // Valid case
         Arguments.of(objData, "nonexistent", null, null, null, null) // Invalid column
      );
   }

   @ParameterizedTest
   @MethodSource("provideFirstLastTestCases")
   void testFirstLast(Object table, Object column, Object group, Object condition, Object expectedFirst, Object expectedLast) {
      assertEquals(expectedFirst, calcTableScope.first(table, column, group, condition));
      assertEquals(expectedLast, calcTableScope.last(table, column, group, condition));
   }

   private static Stream<Arguments> providePthPercentileTestCases() {
      return Stream.of(
         // Median for the range
         Arguments.of(50, "[1,id]:[4,id]", null, null, 2.0),
         // Minimum value in the range,
         Arguments.of(0, "[1,id]:[4,id]", null, null, 1.0),
         // median for the range with group
         Arguments.of(50, "[1,id]:[4,id]", "name", "name='a'", 2.0),
         Arguments.of(50, "[1,name]:[4,name]", null, null, "a"),
         Arguments.of(0, null, null, null, null),
         Arguments.of(30, "[]", null, null, null),
         Arguments.of(objData, 60, "id", "id>1", 2.0)
      );
   }

   @ParameterizedTest
   @MethodSource("providePthPercentileTestCases")
   void testPthPercentile(Object p, Object column, String group, String condition, Object expected) {
      assertEquals(expected, calcTableScope.pthPercentile(p, column, group, condition));
   }

   private static Stream<Arguments> provideNthLargestSmallestTestCases() {
      Date date_2023 = new Date(2023 - 1900, 5, 15);
      Date date_2025 = new Date(2025 - 1900, 11, 31);

      return Stream.of(
         Arguments.of(1, "[1,id]:[4,id]", null, null, 3.0, 1.0),
         Arguments.of(2, "[1,date]:[4,date]", null, "id>1", date_2025, date_2023),
         Arguments.of(-1, "[1,id]:[4,id]", null, null, 3.0, 1.0), // Invalid N defaults to 1
         Arguments.of(1, "[]", null, null, null, null),
         Arguments.of(1, null, null, null, null, null),
         //for report script
         Arguments.of(objData, 2, "id", "id>1", 2.0, 3.0)
      );
   }

   @ParameterizedTest
   @MethodSource("provideNthLargestSmallestTestCases")
   void testNthLargestSmallest(Object n, Object column, String group, String condition, Object expectedLargest, Object expectedSmallest) {
      assertEquals(expectedLargest, calcTableScope.nthLargest(n, column, group, condition));
      assertEquals(expectedSmallest, calcTableScope.nthSmallest(n, column, group, condition));
   }

   private static Stream<Arguments> provideNthMostFrequentTestCases() {
      return Stream.of(
         Arguments.of(1, "[1,id]:[4,id]", null, null, 2),
         Arguments.of(2, "[1,id]:[4,id]", "name", "name='a'", 1),
         Arguments.of(1, null, null, null, null),
         Arguments.of(1, "[]", null, null, null),

         //for report script
         Arguments.of(objData, 2, "id", "id>1", 3)
      );
   }

   private String doubleToString(Number value) {
      if (value != null) {
         return String.format("%.4f", value.doubleValue());
      }

      return null;
   }

   static Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };

   static Object[][] objData2 = new Object[][]{
      {"name", "id1", "id2", "date"},
      {"a", 1, 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, 3, new Date(2023 - 1900, 5, 15)},
      {"a", 3, 2, new Date(2025 - 1900, 11, 31)},
      {"b", 2, 4, new Date(2026 - 1900, 9, 20)}
   };
}
