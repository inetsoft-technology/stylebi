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

import inetsoft.report.filter.*;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableRow;
import inetsoft.test.*;
import inetsoft.util.script.graal.ScriptFunction;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static inetsoft.util.stall.StallTestSupport.assertStallOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CalcTableScope.
 * Some algorithms are used by the report script, which has been removed from stylebi, so only simple testing
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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
      assertNotNull(calcTableScope.getMember("field"));
      assertNotNull(calcTableScope.getMember("sum"));

      assertInstanceOf(ScriptFunction.class, calcTableScope.getMember("sum"));
      assertEquals("a", calcTableScope.getMember("$name"));
   }

   /**
    * test CalcTableScope initializes with RuntimeCalcTableLens
    */
   @Test
   void testCalcTableScopeInitializesWithRuntimeCalcTableLens() {
      RuntimeCalcTableLens runtimeCalcTableLens = new RuntimeCalcTableLens(calcTableLens);
      CalcTableScope calcTableScope = new CalcTableScope(runtimeCalcTableLens);

      assertInstanceOf(ScriptFunction.class, calcTableScope.getMember("sum"));
      assertInstanceOf(CalcRef.class, calcTableScope.getMember("$name"));
   }

   /**
    * test setRow
    */
   @Test
   void testSetRow() {
      calcTableScope.setRow(2);
      // Verify that the "row" property is updated
      assertEquals(2, calcTableScope.getMember("row"));

      // Verify that the "field" property reflects the correct row
      TableRow field = (TableRow) calcTableScope.getMember("field");
      assertEquals("c", field.getMember("name"));
      // field returns the native cell value (Integer) from the table. (#75423)
      assertEquals(2, field.getMember("id"));
   }

   /**
    * test none function
    */
   private static Stream<Arguments> provideNoneTestCases() {
      CalcTableLens calcTableLens1 = new CalcTableLens(objData);
      Object lens1 = FormulaFunctions.toList(calcTableLens1, "field=id,sort=desc");

      return Stream.of(
         // Test case 1: column is cell range, condition and group are useless
         // none returns the native cell value (Integer) from the table. (#75423)
         Arguments.of("[1,id]:[3,id]", null, null, 3),
         // Test case 2: column is lens, condition and group are useless
         Arguments.of(lens1, null, null, 1),
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
         Arguments.of(objData, "id", "id==3", 3.0),
         // Test case 3: test array obj, group  and condition are useless
         Arguments.of(new Object[]{3, 7, 8}, null, null, 6.0),
         // single row
         Arguments.of(new Object[]{5}, null, null, 5.0),
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
         // nothing to count is 0, as CountFormula/DistinctCountFormula on the crosstab path (#81084)
         Arguments.of(null, null, null, 0.0, 0.0)
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
         // single row
         Arguments.of(new Object[]{7}, null, null, 7, 7),
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
         // single row
         Arguments.of(new Object[]{4}, null, null, 4.0),
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
         Arguments.of("[1,id]:[3,id]", null, null, 1), // No mode, defaults to 1 (native Integer) (#75423)
         Arguments.of(objData, "id", null, 2),   // One mode (native Integer) (#75423)
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
         // Cell-range-only first()/last() returns null: unlike none()/sum()/etc,
         // first()/last() route through ReportGraalJavaScriptEngine.summarize()
         // (where the func arg is null and a range string is not a TableLens),
         // not the cell-range summarize(). This matches main exactly -- the case
         // was disabled on main with aspirational 1.0/2.0 values that never held.
         // Restored and corrected to the actual (regression-free) behavior. (#75423)
         Arguments.of("[1,id]:[3,id]", null, null, null, null, null),
         Arguments.of(null, null, null, null, null, null),      // Null input
         //for report script (native Integer cell values) (#75423)
         Arguments.of(objData, "id", "name", "id>1", 3, 2), // Valid case
         Arguments.of(objData, "nonexistent", null, null, null, null) // Invalid column
      );
   }

   @ParameterizedTest
   @MethodSource("provideFirstLastTestCases")
   void testFirstLast(Object table, Object column, Object group, Object condition, Object expectedFirst, Object expectedLast) {
      assertEquals(expectedFirst, calcTableScope.first(table, column, group, condition));
      assertEquals(expectedLast, calcTableScope.last(table, column, group, condition));
   }

   @Test
   void medianOfAnEvenCountIsTheMeanOfTheMiddlePair() {
      assertEquals(2.5, calcTableScope.median(new Object[] { 1, 2, 3, 4 }, null, null));
   }

   @Test
   void medianDoesNotReorderItsInputArray() {
      Object[] input = { 3, 1, 2 };

      assertEquals(2.0, calcTableScope.median(input, null, null));
      assertArrayEquals(new Object[] { 3, 1, 2 }, input);
   }

   @Test
   void sumWithAConditionAddsOnlyTheMatchingRows() {
      // the condition is a script expression; ids are 1, 2, 3, 2: the rows with id==2 add up to 4
      assertEquals(4.0, calcTableScope.sum(objData, "id", "id==2"));
   }

   @Disabled("Bug #81084: count and countDistinct of an empty array return null on the script " +
             "path; expected 0.0, as for a null input (testCountAndDistinct)")
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void countAndCountDistinctOfAnEmptyArrayAreZero(boolean fill) {
      calcTableLens.setFillBlankWithZero(fill);
      CalcTableScope scope = new CalcTableScope(calcTableLens);

      assertEquals(0.0, scope.count(new Object[0], null, null), "count");
      assertEquals(0.0, scope.countDistinct(new Object[0], null, null), "countDistinct");
   }

   private static Stream<Arguments> providePthPercentileTestCases() {
      return Stream.of(
         // Median for the range (native Integer cell value) (#75423)
         Arguments.of(50, "[1,id]:[4,id]", null, null, 2),
         // Minimum value in the range (native Integer cell value) (#75423)
         Arguments.of(0, "[1,id]:[4,id]", null, null, 1),
         // median for the range with group (native Integer cell value) (#75423)
         Arguments.of(50, "[1,id]:[4,id]", "name", "name='a'", 2),
         Arguments.of(50, "[1,name]:[4,name]", null, null, "a"),
         Arguments.of(0, null, null, null, null),
         Arguments.of(30, "[]", null, null, null),
         Arguments.of(objData, 60, "id", "id>1", 2)
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
         // native Integer cell values (#75423)
         Arguments.of(1, "[1,id]:[4,id]", null, null, 3, 1),
         Arguments.of(2, "[1,date]:[4,date]", null, "id>1", date_2025, date_2023),
         Arguments.of(-1, "[1,id]:[4,id]", null, null, 3, 1), // Invalid N defaults to 1
         Arguments.of(1, "[]", null, null, null, null),
         Arguments.of(1, null, null, null, null, null),
         //for report script (native Integer cell values) (#75423)
         Arguments.of(objData, 2, "id", "id>1", 2, 3)
      );
   }

   @ParameterizedTest
   @MethodSource("provideNthLargestSmallestTestCases")
   void testNthLargestSmallest(Object n, Object column, String group, String condition, Object expectedLargest, Object expectedSmallest) {
      assertEquals(expectedLargest, calcTableScope.nthLargest(n, column, group, condition));
      assertEquals(expectedSmallest, calcTableScope.nthSmallest(n, column, group, condition));
   }

   // ---- script path against crosstab path ----
   // A freehand table is evaluated either by the script path (CalcMath/CalcStat) or, when its
   // layout allows, by a crosstab (report.filter formulas); see CalcTableVSAQuery. The same
   // aggregate must give the same value on both paths, so the expected value is the other path's
   // result, not a hand written value.

   private interface ScriptAggregate {
      Object apply(CalcTableScope scope, Object values, String column, String condition);
   }

   private static final Date DATE_1 = new Date(2023 - 1900, 0, 10);
   private static final Date DATE_2 = new Date(2024 - 1900, 5, 20);
   private static final Date DATE_3 = new Date(2025 - 1900, 11, 31);

   private static final Object[] STRINGS_A_B_A = { "a", "b", "a" };
   private static final Object[] NUMERIC_STRINGS_1_2 = { "1", "2" };
   private static final Object[] NUMERIC_STRINGS_1_1_2 = { "1", "1", "2" };
   private static final Object[] ALL_NULL = { null, null };
   private static final Object[] DATES = { DATE_1, DATE_2, DATE_3 };
   private static final Object[] STRING_AMONG_NUMBERS = { "a", 1 };
   private static final Object[] NUMERIC_STRING_AND_NUMBER = { "1", 2 };

   private static final Object[][] AGGREGATE_INPUTS = {
      { 1, 2, 3, 4 }, { 1, null, 3 }, ALL_NULL, { 5 }, { -2, -2, 3 }, { 1.5, 2.5 },
      { 2, 2, 3, 3 }, STRINGS_A_B_A, { 0, 0 },
      // null next to numbers: both Modes drop nulls (#81084, #81181)
      { 1, null, null }, { 1, 1, null, null, null },
      // numeric looking strings (e.g. a CSV column read as strings)
      NUMERIC_STRINGS_1_2, NUMERIC_STRINGS_1_1_2,
      // strings with a null
      { "a", null, "a" },
      // dates
      DATES,
      // a type mix and an int overflow
      { 1L, 2.5 }, { Integer.MAX_VALUE, 1 },
      // a string among numbers
      NUMERIC_STRING_AND_NUMBER, STRING_AMONG_NUMBERS,
      { 0.0, 0.0 }
   };

   // The inputs on which the script path and the crosstab path still give different values
   // (Redmine #81084), by family. Each family is asserted by its own disabled test below and is
   // left out of scriptAndCrosstabPathsAgree. A key is built by caseKey.
   private static final Map<String, Set<String>> KNOWN_MISMATCH_FAMILIES = new HashMap<>();

   private static void mismatch(String family, String aggregate, Object[] values, boolean fill) {
      KNOWN_MISMATCH_FAMILIES.computeIfAbsent(family, f -> new HashSet<>())
                             .add(caseKey(aggregate, values, fill));
   }

   static {
      for(boolean fill : new boolean[] { false, true }) {
         mismatch("medianOfStrings", "median", STRINGS_A_B_A, fill);
         mismatch("medianOfDates", "median", DATES, fill);
         mismatch("modeOfNumericStrings", "mode", NUMERIC_STRINGS_1_2, fill);
         mismatch("modeOfNumericStrings", "mode", NUMERIC_STRINGS_1_1_2, fill);
         mismatch("medianOfAStringAmongNumbers", "median", STRING_AMONG_NUMBERS, fill);
         mismatch("modeOfANumericStringAndANumber", "mode", NUMERIC_STRING_AND_NUMBER, fill);
      }

      for(String name : new String[] { "max", "min", "median", "mode" }) {
         mismatch("fillBlankOfAnAllNullGroup", name, ALL_NULL, true);
      }
   }

   private static boolean isKnownMismatch(String key) {
      return KNOWN_MISMATCH_FAMILIES.values().stream().anyMatch(keys -> keys.contains(key));
   }

   /** Includes the class of each value, so that "1" and 1 are different inputs. */
   private static String caseKey(String name, Object[] values, boolean fill) {
      StringBuilder sb = new StringBuilder(fill ? "fill|" : "").append(name).append('|');

      for(Object value : values) {
         sb.append(value).append(':').append(value == null ? "" : value.getClass().getSimpleName())
            .append(';');
      }

      return sb.toString();
   }

   private static Map<String, Arguments> scriptCrosstabFormulas() {
      Map<String, Arguments> formulas = new LinkedHashMap<>();
      add(formulas, "sum", SumFormula::new, (s, v, g, c) -> s.sum(v, g, c));
      add(formulas, "average", AverageFormula::new, (s, v, g, c) -> s.average(v, g, c));
      add(formulas, "count", CountFormula::new, (s, v, g, c) -> s.count(v, g, c));
      add(formulas, "countDistinct", DistinctCountFormula::new,
          (s, v, g, c) -> s.countDistinct(v, g, c));
      add(formulas, "max", MaxFormula::new, (s, v, g, c) -> s.max(v, g, c));
      add(formulas, "min", MinFormula::new, (s, v, g, c) -> s.min(v, g, c));
      add(formulas, "product", ProductFormula::new, (s, v, g, c) -> s.product(v, g, c));
      add(formulas, "median", MedianFormula::new, (s, v, g, c) -> s.median(v, g, c));
      add(formulas, "mode", ModeFormula::new, (s, v, g, c) -> s.mode(v, g, c));
      add(formulas, "stdev", StandardDeviationFormula::new,
          (s, v, g, c) -> s.standardDeviation(v, g, c));
      add(formulas, "var", VarianceFormula::new, (s, v, g, c) -> s.variance(v, g, c));
      add(formulas, "popvar", PopulationVarianceFormula::new,
          (s, v, g, c) -> s.populationVariance(v, g, c));
      add(formulas, "popstdev", PopulationStandardDeviationFormula::new,
          (s, v, g, c) -> s.populationStandardDeviation(v, g, c));
      return formulas;
   }

   private static Stream<Arguments> provideScriptCrosstabCases() {
      return scriptCrosstabCases(false);
   }

   private static Stream<Arguments> provideKnownMismatchCases() {
      return scriptCrosstabCases(true);
   }

   private static Stream<Arguments> scriptCrosstabCases(boolean mismatches) {
      List<Arguments> list = new ArrayList<>();

      for(boolean fill : new boolean[] { false, true }) {
         for(Arguments a : scriptCrosstabFormulas().values()) {
            for(Object[] values : AGGREGATE_INPUTS) {
               String key = caseKey((String) a.get()[0], values, fill);

               if(isKnownMismatch(key) == mismatches) {
                  list.add(Arguments.of(a.get()[0], a.get()[1], a.get()[2], values, fill));
               }
            }
         }
      }

      return list.stream();
   }

   private static void add(Map<String, Arguments> map, String name, Supplier<Formula> formula,
                           ScriptAggregate script)
   {
      map.put(name, Arguments.of(name, formula, script));
   }

   /** The two paths' values of the same aggregate over {@code values}, as {script, crosstab}. */
   private String[] bothPaths(Supplier<Formula> formula, ScriptAggregate script, Object[] values,
                              boolean fill)
   {
      calcTableLens.setFillBlankWithZero(fill);
      String viaScript = format(script.apply(new CalcTableScope(calcTableLens), values.clone(),
                                             null, null));
      Formula crosstabFormula = formula.get();
      crosstabFormula.setDefaultResult(fill);
      String viaCrosstab = format(crosstabValue(crosstabFormula, values));
      return new String[] { viaScript, viaCrosstab };
   }

   @ParameterizedTest
   @MethodSource("provideScriptCrosstabCases")
   void scriptAndCrosstabPathsAgree(String name, Supplier<Formula> formula,
                                    ScriptAggregate script, Object[] values, boolean fill)
   {
      String[] paths = bothPaths(formula, script, values, fill);
      assertEquals(paths[0], paths[1], caseKey(name, values, fill));
   }

   /** Runs only the cases of {@code family}; the other cases of the shared provider are skipped. */
   private String[] bothPathsOfFamily(String family, String name, Supplier<Formula> formula,
                                      ScriptAggregate script, Object[] values, boolean fill)
   {
      Assumptions.assumeTrue(KNOWN_MISMATCH_FAMILIES.get(family)
                                .contains(caseKey(name, values, fill)));
      return bothPaths(formula, script, values, fill);
   }

   @Disabled("Bug #81084: median of strings: script path gives no value, crosstab path gives the middle " +
             "string ('b' for a,b,a); expected: both paths give no value")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void medianOfStringsGivesNoValueOnBothPaths(String name, Supplier<Formula> formula,
                                               ScriptAggregate script, Object[] values,
                                               boolean fill)
   {
      String[] paths = bothPathsOfFamily("medianOfStrings", name, formula, script, values, fill);
      assertEquals("null", paths[0], "script path");
      assertEquals("null", paths[1], "crosstab path");
   }

   @Disabled("Bug #81084: median of dates: script path gives no value, crosstab path gives the middle date; " +
             "expected: both paths give the middle date")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void medianOfDatesGivesTheMiddleDateOnBothPaths(String name, Supplier<Formula> formula,
                                                   ScriptAggregate script, Object[] values,
                                                   boolean fill)
   {
      String[] paths = bothPathsOfFamily("medianOfDates", name, formula, script, values, fill);
      assertEquals(DATE_2.toString(), paths[0], "script path");
      assertEquals(DATE_2.toString(), paths[1], "crosstab path");
   }

   @Disabled("Bug #81084: mode of numeric looking strings: script path gives the number (1.0000), crosstab " +
             "path gives the string (1); expected: the same text on both paths")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void modeOfNumericStringsAgreesOnBothPaths(String name, Supplier<Formula> formula,
                                              ScriptAggregate script, Object[] values,
                                              boolean fill)
   {
      assertPathsAgree("modeOfNumericStrings", name, formula, script, values, fill);
   }

   @Disabled("Bug #81084: median of a string among numbers ({a, 1}): script path gives 1.0000, crosstab path " +
             "gives no value; expected: the same on both paths")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void medianOfAStringAmongNumbersAgreesOnBothPaths(String name, Supplier<Formula> formula,
                                                     ScriptAggregate script, Object[] values,
                                                     boolean fill)
   {
      assertPathsAgree("medianOfAStringAmongNumbers", name, formula, script, values, fill);
   }

   @Disabled("Bug #81084: mode of {\"1\", 2}: script path gives 1.0000, crosstab path gives the string (1); " +
             "expected: the same on both paths")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void modeOfANumericStringAndANumberAgreesOnBothPaths(String name, Supplier<Formula> formula,
                                                        ScriptAggregate script, Object[] values,
                                                        boolean fill)
   {
      assertPathsAgree("modeOfANumericStringAndANumber", name, formula, script, values, fill);
   }

   @Disabled("Bug #81084: fill blank with zero, max/min/median/mode of an all-null group: script " +
             "path gives no value, crosstab path gives 0; expected: the same on both paths")
   @ParameterizedTest
   @MethodSource("provideKnownMismatchCases")
   void fillBlankOfAnAllNullGroupAgreesOnBothPaths(String name, Supplier<Formula> formula,
                                                   ScriptAggregate script, Object[] values,
                                                   boolean fill)
   {
      assertPathsAgree("fillBlankOfAnAllNullGroup", name, formula, script, values, fill);
   }

   private void assertPathsAgree(String family, String name, Supplier<Formula> formula,
                                 ScriptAggregate script, Object[] values, boolean fill)
   {
      String[] paths = bothPathsOfFamily(family, name, formula, script, values, fill);
      assertEquals(paths[0], paths[1], caseKey(name, values, fill));
   }

   private static final Object[][] GROUPED_ROWS = {
      { "G1", 1 }, { "G1", 2 }, { "G2", 3 }
   };

   @ParameterizedTest
   @MethodSource("provideGroupedCases")
   void scriptAndCrosstabPathsAgreePerGroup(String name, Supplier<Formula> formula,
                                            ScriptAggregate script, boolean fill)
   {
      calcTableLens.setFillBlankWithZero(fill);
      Formula crosstabFormula = formula.get();
      crosstabFormula.setDefaultResult(fill);
      Map<String, Object> crosstab = crosstabValuesByGroup(crosstabFormula, GROUPED_ROWS);
      DefaultTableLens table = groupedTable();

      for(String group : new String[] { "G1", "G2" }) {
         String viaScript = format(script.apply(new CalcTableScope(calcTableLens), table, "v",
                                                "g=='" + group + "'"));
         assertEquals(format(crosstab.get(group)), viaScript, name + " of group " + group);
      }
   }

   private static Stream<Arguments> provideGroupedCases() {
      List<Arguments> list = new ArrayList<>();

      for(boolean fill : new boolean[] { false, true }) {
         for(Arguments a : scriptCrosstabFormulas().values()) {
            list.add(Arguments.of(a.get()[0], a.get()[1], a.get()[2], fill));
         }
      }

      return list.stream();
   }

   private static DefaultTableLens groupedTable() {
      Object[][] data = new Object[GROUPED_ROWS.length + 1][];
      data[0] = new Object[] { "g", "v" };
      System.arraycopy(GROUPED_ROWS, 0, data, 1, GROUPED_ROWS.length);
      DefaultTableLens table = new DefaultTableLens(data);
      table.setHeaderRowCount(1);
      return table;
   }

   /** The value a crosstab with one row group computes for {@code values}. */
   private static Object crosstabValue(Formula formula, Object[] values) {
      Object[][] rows = new Object[values.length][];

      for(int i = 0; i < values.length; i++) {
         rows[i] = new Object[] { "G", values[i] };
      }

      Map<String, Object> byGroup = crosstabValuesByGroup(formula, rows);
      assertTrue(byGroup.containsKey("G"), "no crosstab row for the group");
      return byGroup.get("G");
   }

   /** The value per row group of a crosstab over {group, value} rows with {@code formula}. */
   private static Map<String, Object> crosstabValuesByGroup(Formula formula, Object[][] rows) {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[] { "g", "v" };
      System.arraycopy(rows, 0, data, 1, rows.length);

      DefaultTableLens base = new DefaultTableLens(data);
      base.setHeaderRowCount(1);
      CrossTabFilter crosstab = new CrossTabFilter(base, new int[] { 0 }, new int[0],
                                                   new int[] { 1 }, new Formula[] { formula });
      crosstab.moreRows(Integer.MAX_VALUE);
      Set<Object> groups = new HashSet<>();

      for(Object[] row : rows) {
         groups.add(row[0]);
      }

      Map<String, Object> result = new HashMap<>();

      for(int r = 0; r < crosstab.getRowCount(); r++) {
         Object key = crosstab.getObject(r, 0);

         if(groups.contains(key)) {
            result.put(key.toString(), crosstab.getObject(r, crosstab.getColCount() - 1));
         }
      }

      return result;
   }

   /**
    * NaN is the script path's "no value" (so that javascript does not read it as 0, #53008), null
    * is the crosstab's; both are shown as an empty cell, so they are the same here.
    */
   private static String format(Object value) {
      if(value instanceof Number) {
         double d = ((Number) value).doubleValue();
         return Double.isNaN(d) ? "null" : String.format(Locale.ROOT, "%.4f", d);
      }

      return value == null ? "null" : value.toString();
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

   /**
    * #77123: a cell-range summary over a stalled calc table has no value, so the stall
    * must reach the formula instead of becoming a null result.
    */
   @Test
   void stalledCellRangeSummaryThrowsTheStall() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      CalcTableScope scope = new CalcTableScope(failingCalcTable(stall));

      assertSame(stall, assertThrows(LockStallException.class,
         () -> scope.sum("[2,id]:[3,id]", null, null)));
   }

   @Test
   void failedCellRangeSummaryStillReturnsNull() {
      CalcTableScope scope =
         new CalcTableScope(failingCalcTable(new IllegalStateException("broken")));

      assertNull(scope.sum("[2,id]:[3,id]", null, null));
   }

   /**
    * #77123: a row condition that reads a stalled column must not turn into a failed
    * (null) condition, which selected every row and summed the wrong rows.
    */
   @Test
   void stalledRowConditionThrowsTheStall() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);

      // sanity: the condition selects rows 2..4, id2 = 3 + 2 + 4
      assertEquals(9.0, calcTableScope.sum(conditionTable(null), "id2", "id1>1"));

      assertStallOf(stall, assertThrows(LockStallException.class,
         () -> calcTableScope.sum(conditionTable(stall), "id2", "id1>1")));
   }

   /**
    * #77123: an expression column ({@code "=..."}) that reads a stalled column must not
    * turn into a null cell value, which the summary skipped and summed the wrong values.
    */
   @Test
   void stalledColumnExpressionThrowsTheStall() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);

      // sanity: the expression reads id1 on every row, 1 + 2 + 3 + 2
      assertEquals(8.0, calcTableScope.sum(conditionTable(null), "=id1", null));

      assertStallOf(stall, assertThrows(LockStallException.class,
         () -> calcTableScope.sum(conditionTable(stall), "=id1", null)));
   }

   /**
    * A calc table whose data cells (past the header row) fail with {@code failure}, as a
    * stalled lens under a formula cell would in FAIL stall mode.
    */
   private static CalcTableLens failingCalcTable(RuntimeException failure) {
      CalcTableLens lens = new CalcTableLens(objData) {
         @Override
         public Object getObject(int r, int c) {
            if(r > 0) {
               throw failure;
            }

            return super.getObject(r, c);
         }
      };
      lens.setCellName(1, 0, "name");
      return lens;
   }

   /**
    * objData2 with the id1 column (read only by the row condition) failing with
    * {@code failure}, or intact when it is null.
    */
   private static DefaultTableLens conditionTable(RuntimeException failure) {
      return new ConditionTable(failure);
   }

   // public, so TableRow can call getObject reflectively
   public static class ConditionTable extends DefaultTableLens {
      ConditionTable(RuntimeException failure) {
         super(objData2);
         this.failure = failure;
      }

      @Override
      public Object getObject(int r, int c) {
         if(failure != null && r > 0 && c == 1) {
            throw failure;
         }

         return super.getObject(r, c);
      }

      private final RuntimeException failure;
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

   /**
    * #77910: a cell range summary, a row condition, an expression column or a named range
    * condition that reads a table whose swap file is lost must reach the formula as the swap
    * file read failure instead of becoming a null result or a selected row.
    */
   @Test
   void lostSwapFileCellRangeSummaryThrowsTheSwapFailure() {
      try(LostSwapFile lost = new LostSwapFile()) {
         CalcTableLens lens = new CalcTableLens(objData) {
            @Override
            public Object getObject(int r, int c) {
               if(r > 0) {
                  lost.read();
               }

               return super.getObject(r, c);
            }
         };
         lens.setCellName(1, 0, "name");
         CalcTableScope scope = new CalcTableScope(lens);

         assertLost(lost, () -> scope.sum("[2,id]:[3,id]", null, null));
      }
   }

   @Test
   void lostSwapFileRowConditionThrowsTheSwapFailure() {
      try(LostSwapFile lost = new LostSwapFile()) {
         assertLost(lost, () -> calcTableScope.sum(new LostConditionTable(lost), "id2", "id1>1"));
      }
   }

   @Test
   void lostSwapFileColumnExpressionThrowsTheSwapFailure() {
      try(LostSwapFile lost = new LostSwapFile()) {
         assertLost(lost, () -> calcTableScope.sum(new LostConditionTable(lost), "=id1", null));
      }
   }

   @Test
   void lostSwapFileNamedRangeConditionThrowsTheSwapFailure() {
      // sanity: the condition selects rows 2..4, id2 = 3 + 2 + 4
      assertEquals(9.0, calcTableScope.sum(conditionTable(null), "id2?id1>1", null));

      try(LostSwapFile lost = new LostSwapFile()) {
         assertLost(lost,
            () -> calcTableScope.sum(new LostConditionTable(lost), "id2?id1>1", null));
      }
   }

   private static void assertLost(LostSwapFile lost, Executable summary) {
      assertEquals(lost.getFile(),
                   assertThrows(SwapFileReadException.class, summary).getFile());
   }

   /**
    * objData2 with the id1 column (read only by the condition or expression) in the lost
    * swap file. Public, so TableRow can call getObject reflectively.
    */
   public static class LostConditionTable extends DefaultTableLens {
      LostConditionTable(LostSwapFile lost) {
         super(objData2);
         this.lost = lost;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r > 0 && c == 1) {
            lost.read();
         }

         return super.getObject(r, c);
      }

      private final LostSwapFile lost;
   }
}
