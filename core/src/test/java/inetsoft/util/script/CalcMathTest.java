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
package inetsoft.util.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CalcMathTest {

   @Test
   void testAbs() {
      assertEquals(5.0, CalcMath.abs(-5.0));
      assertEquals(0.0, CalcMath.abs(0.0));
      assertEquals(5.0, CalcMath.abs(5.0));
   }

   @Test
   void testAcos() {
      // Test valid input within range [-1, 1]
      assertEquals(0.0, CalcMath.acos(1.0), 0.0001);
      assertEquals(Math.PI / 2, CalcMath.acos(0.0), 0.0001);
      assertEquals(Math.PI, CalcMath.acos(-1.0), 0.0001);

      // Test invalid input less than -1
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.acos(-1.1));
      assertEquals("Number should be between -1 and 1 (both inclusive)", exception.getMessage());

      // Test invalid input greater than 1
      exception = assertThrows(RuntimeException.class, () -> CalcMath.acos(1.1));
      assertEquals("Number should be between -1 and 1 (both inclusive)", exception.getMessage());
   }

   @Test
   void testAcosh() {
      // Test valid input
      assertEquals(0.0, CalcMath.acosh(1.0), 0.0001);
      assertEquals(1.3169578969248166, CalcMath.acosh(2.0), 0.0001);

      // Test invalid input (less than 1)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.acosh(0.5));
      assertEquals("Number should at least be equal to 1", exception.getMessage());
   }

   @Test
   void testAsin() {
      // Test valid input within range [-1, 1]
      assertEquals(0.0, CalcMath.asin(0.0), 0.0001);
      assertEquals(Math.PI / 2, CalcMath.asin(1.0), 0.0001);
      assertEquals(-Math.PI / 2, CalcMath.asin(-1.0), 0.0001);

      // Test invalid input less than -1
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.asin(-1.1));
      assertEquals("Number should be between -1 and 1 (both inclusive)", exception.getMessage());

      // Test invalid input greater than 1
      exception = assertThrows(RuntimeException.class, () -> CalcMath.asin(1.1));
      assertEquals("Number should be between -1 and 1 (both inclusive)", exception.getMessage());
   }

   @Test
   void testAsinh() {
      assertEquals(0.0, CalcMath.asinh(0.0));
      assertEquals(1.4436354751788103, CalcMath.asinh(2.0), 0.0001);
   }

   @Test
   void testAtan() {
      assertEquals(0.0, CalcMath.atan(0.0));
      assertEquals(Math.PI / 4, CalcMath.atan(1.0));
   }

   @Test
   void testAtan2() {
      // Test with valid inputs
      assertEquals(Math.PI / 4, CalcMath.atan2(1.0, 1.0), 0.0001);
      assertEquals(1.5707963267948966, CalcMath.atan2(0.0, 1.0), 0.0001);
      assertEquals(Math.PI * 3 / 4, CalcMath.atan2(-1.0, 1.0), 0.0001);

      // Test with zero inputs
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.atan2(0.0, 0.0));
      assertEquals("Divide by zero error, both arguments cannot be zero", exception.getMessage());
   }

   @Test
   void testAtanh() {
      // Test valid inputs
      assertEquals(0.0, CalcMath.atanh(0.0), 0.0001);
      assertEquals(0.5493, CalcMath.atanh(0.5), 0.0001);
      assertEquals(-0.5493, CalcMath.atanh(-0.5), 0.0001);

      // Test invalid inputs (less than or equal to -1)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.atanh(-1.0));
      assertEquals("Number should between -1 and 1 (both exclusive)", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.atanh(-1.1));
      assertEquals("Number should between -1 and 1 (both exclusive)", exception.getMessage());

      // Test invalid inputs (greater than or equal to 1)
      exception = assertThrows(RuntimeException.class, () -> CalcMath.atanh(1.0));
      assertEquals("Number should between -1 and 1 (both exclusive)", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.atanh(1.1));
      assertEquals("Number should between -1 and 1 (both exclusive)", exception.getMessage());
   }

   @Test
   void testCeiling() {
      // Test with positive number and positive significance
      assertEquals(10.0, CalcMath.ceiling(7.5, 5.0));
      assertEquals(15.0, CalcMath.ceiling(12.3, 5.0));

      // Test with negative number and negative significance
      assertEquals(-10.0, CalcMath.ceiling(-7.5, -5.0));
      assertEquals(-15.0, CalcMath.ceiling(-12.3, -5.0));

      // Test with zero
      assertEquals(0.0, CalcMath.ceiling(0.0, 5.0));

      // Test with invalid input (different signs for number and significance)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.ceiling(-7.5, 5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.ceiling(7.5, -5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());
   }

   @Test
   void testCombin() {
      // Test valid inputs
      assertEquals(10.0, CalcMath.combin(5, 2));
      assertEquals(1.0, CalcMath.combin(5, 0));
      assertEquals(1.0, CalcMath.combin(5, 5));

      // Test invalid inputs
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.combin(-1, 2));
      String expectedMessage = "number should be at least equal to the number_chosen and " +
         "both these variables should hold non-negative values";
      assertEquals(expectedMessage, exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.combin(5, -2));
      assertEquals(expectedMessage, exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.combin(2, 5));
      assertEquals(expectedMessage, exception.getMessage());
   }

   @Test
   void testCos() {
      assertEquals(1.0, CalcMath.cos(0.0));
      assertEquals(0.0, CalcMath.cos(Math.PI / 2), 0.0001);
   }

   @Test
   void testCosh() {
      assertEquals(1.5430806348152437, CalcMath.cosh(1.0), 0.0001);
   }

   @Test
   void testDegrees() {
      assertEquals(180.0, CalcMath.degrees(Math.PI));
   }

   @Test
   void testEven() {
      assertEquals(2, CalcMath.even(1.5));
      assertEquals(-2, CalcMath.even(-1.5));
      assertEquals(0, CalcMath.even(0.0));
   }

   @Test
   void testExp() {
      assertEquals(Math.E, CalcMath.exp(1.0), 0.0001);
   }

   @Test
   void testFact() {
      // Test valid input
      assertEquals(120.0, CalcMath.fact(5)); // 5! = 120
      assertEquals(1.0, CalcMath.fact(0));   // 0! = 1

      // Test invalid input (negative number)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.fact(-1));
      assertEquals("Number should be non-negative", exception.getMessage());
   }

   @Test
   void testFactDouble() {
      // Test valid input
      assertEquals(15.0, CalcMath.factdouble(5)); // Double factorial of 5: 5 * 3 * 1
      assertEquals(8.0, CalcMath.factdouble(4)); // Double factorial of 4: 4 * 2
//      assertEquals(1.0, CalcMath.factdouble(0)); // Double factorial of 0: 1, bug #71328

      // Test invalid input (negative number)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.factdouble(-1));
      assertEquals("Number should be non-negative", exception.getMessage());
   }

   @Test
   void testFloor() {
      // Test with positive number and positive significance
      assertEquals(5.0, CalcMath.floor(7.5, 5.0));
      assertEquals(10.0, CalcMath.floor(12.3, 5.0));

      // Test with negative number and negative significance
//      assertEquals(-10.0, CalcMath.floor(-7.5, -5.0));//bug #71318
//      assertEquals(-15.0, CalcMath.floor(-12.3, -5.0));

      // Test with zero
      assertEquals(0.0, CalcMath.floor(0.0, 5.0));

      // Test with invalid input (different signs for number and significance)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.floor(-7.5, 5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.floor(7.5, -5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());
   }

   @Test
   void testGcd() {
      // Test with positive integers
      assertEquals(6, CalcMath.gcd(new Object[]{12, 18}));
      assertEquals(12, CalcMath.gcd(new Object[]{12}));
      assertEquals(6, CalcMath.gcd(new Object[]{24, 18, 30}));

      // Test with an empty array
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.gcd(new Object[]{}));
      assertEquals("Array should at least contain one number", exception.getMessage());

      // Test with negative numbers, bug #71329
//      exception = assertThrows(RuntimeException.class, () -> CalcMath.gcd(new Object[]{-12, 18}));
//      assertEquals("All parameters to gcd() must be non-negative", exception.getMessage());
   }

   @Test
   void testInteger() {
      assertEquals(5, CalcMath.integer(5.9));
      assertEquals(-6, CalcMath.integer(-5.9));
   }

   @Test
   void testLcm() {
      // Test with two positive integers
      assertEquals(36, CalcMath.lcm(new Object[]{12, 18}));

      // Test with a single positive integer
      assertEquals(12, CalcMath.lcm(new Object[]{12}));

      // Test with multiple positive integers
      assertEquals(2520, CalcMath.lcm(new Object[]{7, 8, 9, 10}));

      // Test with an empty array
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.lcm(new Object[]{}));
      assertEquals("Array should at least contain one number", exception.getMessage());

      // Test with a negative number, bug #71317
//      exception = assertThrows(RuntimeException.class, () -> CalcMath.lcm(new Object[]{-12, 18}));
//      assertEquals("All parameters to lcm() must be greater than zero, parameter 1 value is -12.0",
//                   exception.getMessage());

      // Test with zero, bug #71317
//      exception = assertThrows(RuntimeException.class, () -> CalcMath.lcm(new Object[]{0, 18}));
//      assertEquals("All parameters to lcm() must be greater than zero, parameter 1 value is 0",
//                   exception.getMessage());
   }

   @Test
   void testLn() {
      // Test valid input
      assertEquals(0.0, CalcMath.ln(1.0), 0.0001);
      assertEquals(Math.log(10.0), CalcMath.ln(10.0), 0.0001);

      // Test invalid input (zero and negative numbers)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.ln(0.0));
      assertEquals("Number should be greater than zero", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.ln(-5.0));
      assertEquals("Number should be greater than zero", exception.getMessage());
   }

   @Test
   void testLog() {
      // Test valid inputs
      assertEquals(2.0, CalcMath.log(100.0, 10.0), 0.0001);
      assertEquals(3.0, CalcMath.log(27.0, 3.0), 0.0001);

      // Test invalid input: number <= 0
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.log(0.0, 10.0));
      assertEquals("Number should be greater than zero", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.log(-5.0, 10.0));
      assertEquals("Number should be greater than zero", exception.getMessage());

      // Test invalid input: base <= 0
      exception = assertThrows(RuntimeException.class, () -> CalcMath.log(100.0, 0.0));
      assertEquals("Base should be greater than zero", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.log(100.0, -2.0));
      assertEquals("Base should be greater than zero", exception.getMessage());

      // Test invalid input: base == 1
      exception = assertThrows(RuntimeException.class, () -> CalcMath.log(100.0, 1.0));
      assertEquals("Base cannot be one", exception.getMessage());
   }

   @Test
   void testLog10() {
      // Test valid input
      assertEquals(2.0, CalcMath.log10(100.0), 0.0001);
      assertEquals(3.0, CalcMath.log10(1000.0), 0.0001);

      // Test invalid input (zero and negative numbers)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.log10(0.0));
      assertEquals("Number should be greater than zero", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.log10(-10.0));
      assertEquals("Number should be greater than zero", exception.getMessage());
   }

   @Test
   void testPi() {
      assertEquals(3.14159265358979, CalcMath.pi(), 0.0001);
   }

   @Test
   void testPower() {
      assertEquals(8.0, CalcMath.power(2.0, 3.0));
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.46",
      "123.456, 1, 123.5",
      "123.456, 0, 123.0",
      "123.456, -1, 120.0",
      "123.456, -2, 100.0",
      "-123.456, 2, -123.46",
      "-123.456, -1, -120.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testRound(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.round(number, precision), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.45",
      "123.456, 0, 123.0",
      "123.456, -1, 120.0",
      "-123.456, 1, -123.4",
      "-123.456, 0, -123.0",
      "-123.456, -2, -100.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testMathRound(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.mathRound(number, precision), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.45",
      "123.456, 0, 123.0",
      "123.456, -1, 120.0",
      "-123.456, 2, -123.45",
      "-123.456, 0, -123.0",
      "-123.456, -1, -120.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testRounddown(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.rounddown(number, precision), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.45",
      "123.456, 0, 123.0",
      "123.456, -1, 120.0",
      "-123.456, 2, -123.45",
      "-123.456, 0, -123.0",
      "-123.456, -1, -120.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testMathRoundDown(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.mathRoundDown(number, precision), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.46",
      "123.456, 0, 124.0",
      "123.456, -1, 130.0",
      "-123.456, 2, -123.46",
      "-123.456, 0, -124.0",
      "-123.456, -1, -130.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testRoundup(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.roundup(number, precision), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.456, 2, 123.46",
      "123.456, 0, 124.0",
      "123.456, -1, 130.0",
      "-123.456, 2, -123.46",
      "-123.456, 0, -124.0",
      "-123.456, -1, -130.0",
      "0.0, 2, 0.0",
      "0.0, -2, 0.0"
   })
   void testMathRoundUp(double number, int precision, double expected) {
      assertEquals(expected, CalcMath.mathRoundUp(number, precision), 0.0001);
   }

   @ParameterizedTest
   @MethodSource("provideSeriessumTestData")
   void testSeriessum(double x, double n, double m, Object[] coefficients, double expected) {
      assertEquals(expected, CalcMath.seriessum(x, n, m, coefficients), 0.0001);
   }

   private static Stream<Arguments> provideSeriessumTestData() {
      return Stream.of(
         Arguments.of(2.0, 1.0, 1.0, new Object[]{1.0, 2.0, 3.0}, 34.0),
         Arguments.of(2.0, 1.0, 1.0, new Object[]{}, 0.0),
         Arguments.of(2.0, 1.0, 1.0, new Object[]{-1.0, -2.0, -3.0}, -34.0),
         Arguments.of(0.0, 1.0, 1.0, new Object[]{1.0, 2.0, 3.0}, 0.0),
         Arguments.of(2.0, 0.0, 0.0, new Object[]{1.0, 2.0, 3.0}, 6.0)
      );
   }

   @ParameterizedTest
   @CsvSource({
      "0.0, 0.0",
      "1.0, 1.1752",
      "-1.0, -1.1752",
      "2.0, 3.6269"
   })
   void testSinh(double input, double expected) {
      assertEquals(expected, CalcMath.sinh(input), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "0.0, 0.0",
      "1.5708, 1.0",
      "-1.5708, -1.0",
      "3.1416, 0.0"
   })
   void testSin(double input, double expected) {
      assertEquals(expected, CalcMath.sin(input), 0.0001);
   }

   @Test
   void testSqrt() {
      // Test valid input
      assertEquals(4.0, CalcMath.sqrt(16.0), 0.0001);
      assertEquals(0.0, CalcMath.sqrt(0.0), 0.0001);

      // Test invalid input (negative number)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.sqrt(-1.0));
      assertEquals("Number should be positive", exception.getMessage());
   }

   @Test
   void testSqrtpi() {
      // Test with valid input
      assertEquals(Math.sqrt(4.0 * Math.PI), CalcMath.sqrtpi(4.0), 0.0001);
      assertEquals(Math.sqrt(0.0 * Math.PI), CalcMath.sqrtpi(0.0), 0.0001);

      // Test with invalid input (negative number)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.sqrtpi(-1.0));
      assertEquals("Number should be positive", exception.getMessage());
   }

   @ParameterizedTest
   @CsvSource({
      "1, '1.0, 2.0, 3.0', 2.0",  // Average function
      "2, '1.0, 2.0, 3.0', 3.0",  // Count function
      "3, '1.0, null, 3.0', 2.0", // Counta function
      "4, '1.0, 2.0, 3.0', 3.0",  // Max function
      "5, '1.0, 2.0, 3.0', 1.0",  // Min function
      "6, '1.0, 2.0, 3.0', 6.0",  // Product function
      "7, '1.0, 2.0, 3.0', 1.0",  // Stdev function
      "8, '1.0, 2.0, 3.0', 0.8165", // Stdevp function
      "9, '1.0, 2.0, 3.0', 6.0",  // Sum function
      "10, '1.0, 2.0, 3.0', 1.0", // Varn function
      "11, '1.0, 2.0, 3.0', 0.6667" // Varp function
   })
   void testSubtotal(int functionNumber, String refString, double expected) {
      Object[] ref = Arrays.stream(refString.split(","))
         .map(s -> s.trim().equals("null") ? null : Double.valueOf(s.trim()))
         .toArray();
      assertEquals(expected, CalcMath.subtotal(functionNumber, ref), 0.0001);
   }

   @Test
   void testSubtotalInvalidFunctionNumber() {
      Object[] finalRef = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.subtotal(12, finalRef));
      assertEquals("Function number should be an integer from 1 to 11", exception.getMessage());
   }

   @Test
   void testSum() {
      assertEquals(10.0, CalcMath.sum(new Object[]{ 1.0, 2.0, 3.0, 4.0 }));
   }

   @Test
   void testTan() {
      assertEquals(0.0, CalcMath.tan(0.0));
   }

   @ParameterizedTest
   @CsvSource({
      "1.0, 0.7615941559557649",
      "-1.0, -0.7615941559557649",
      "0.0, 0.0",
      "100.0, 1.0",
      "-100.0, -1.0"
   })
   void testTanh(double input, double expected) {
      assertEquals(expected, CalcMath.tanh(input), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "123.4567, 2, 123.45",   // Positive number with positive num_digits
      "123.4567, -1, 120.0",   // Positive number with negative num_digits
      "123.4567, 0, 123.0",    // Positive number with zero num_digits
      "-123.4567, 2, -123.45", // Negative number with positive num_digits
      "-123.4567, -1, -120.0", // Negative number with negative num_digits
      "-123.4567, 0, -123.0"   // Negative number with zero num_digits
   })
   void testTrunc(double number, int numDigits, double expected) {
      assertEquals(expected, CalcMath.trunc(number, numDigits), 0.0001);
   }

   @Test
   void testMdeterm() {
      // Test with a 1x1 matrix
      Object[][] matrix1x1 = {{5.0}};
      assertEquals(5.0, CalcMath.mdeterm(matrix1x1), 0.0001);

      // Test with a 3x3 matrix
      Object[][] matrix3x3 = {
         {6.0, 1.0, 1.0},
         {4.0, -2.0, 5.0},
         {2.0, 8.0, 7.0}
      };
      assertEquals(-306.0, CalcMath.mdeterm(matrix3x3), 0.0001);

      // Test with an empty matrix
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.mdeterm(new Object[][]{}));
      assertEquals("Array should be at least of order 1", exception.getMessage());

      // Test with a non-square matrix
      Object[][] nonSquareMatrix = {
         {1.0, 2.0, 3.0},
         {4.0, 5.0, 6.0}
      };
      exception = assertThrows(RuntimeException.class, () -> CalcMath.mdeterm(nonSquareMatrix));
      assertEquals("Array should have identical numbers of rows and columns", exception.getMessage());

      // Test with a matrix larger than 73x73
      Object[][] largeMatrix = new Object[74][74];
      for (int i = 0; i < 74; i++) {
         for (int j = 0; j < 74; j++) {
            largeMatrix[i][j] = 1.0;
         }
      }
      exception = assertThrows(RuntimeException.class, () -> CalcMath.mdeterm(largeMatrix));
      assertEquals("Maximum array odrer allowed is 73", exception.getMessage());
   }

   @Test
   void testMinverse() {
      // Test with a valid 3x3 matrix
      Object[][] matrix3x3 = {
         {3.0, 0.0, 2.0},
         {2.0, 0.0, -2.0},
         {0.0, 1.0, 1.0}
      };
      double[][] expectedInverse3x3 = {
         {0.2, 0.2, 0.0},
         {-0.2, 0.3, 1.0},
         {0.2, -0.3, 0.0}
      };
      for (int i = 0; i < expectedInverse3x3.length; i++) {
         assertArrayEquals(expectedInverse3x3[i], CalcMath.minverse(matrix3x3)[i], 0.00001);
      }

      // Test with an empty matrix
      Object[][] emptyMatrix = {};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.minverse(emptyMatrix));
      assertEquals("Array is empty", exception.getMessage());

      // Test with a non-square matrix
      Object[][] nonSquareMatrix = {
         {1.0, 2.0, 3.0},
         {4.0, 5.0, 6.0}
      };
      exception = assertThrows(RuntimeException.class, () -> CalcMath.minverse(nonSquareMatrix));
      assertEquals("non square (2x3) matrix", exception.getMessage());

      // Test with a matrix larger than 52x52
      Object[][] largeMatrix = new Object[53][53];
      for (int i = 0; i < 53; i++) {
         for (int j = 0; j < 53; j++) {
            largeMatrix[i][j] = 1.0;
         }
      }
      exception = assertThrows(RuntimeException.class, () -> CalcMath.minverse(largeMatrix));
      assertEquals("Array can have a maximum size of 52 X 52", exception.getMessage());
   }

   @Test
   void testMmult() {
      // Test with valid matrices
      Object[][] matrix1 = {
         {1.0, 2.0},
         {3.0, 4.0}
      };
      Object[][] matrix2 = {
         {5.0, 6.0},
         {7.0, 8.0}
      };
      double[][] expected = {
         {19.0, 22.0},
         {43.0, 50.0}
      };
      assertArrayEquals(expected, CalcMath.mmult(matrix1, matrix2));

      // Test with empty matrices
      Object[][] emptyMatrix = {};
      assertNull(CalcMath.mmult(emptyMatrix, matrix2));

      // Test with incompatible matrices
      Object[][] incompatibleMatrix = {
         {1.0, 2.0, 3.0}
      };
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.mmult(matrix1, incompatibleMatrix));
      assertEquals("Number of columns in array1 must be the same " +
                      "as the number of rows in array2", exception.getMessage());
   }

   @Test
   void testMround() {
      // Test with positive number and positive multiple
      assertEquals(10.0, CalcMath.mround(8.5, 5.0), 0.0001);
      assertEquals(15.0, CalcMath.mround(12.7, 5.0), 0.0001);

      // Test with negative number and negative multiple
      assertEquals(-10.0, CalcMath.mround(-8.5, -5.0), 0.0001);
      assertEquals(-15.0, CalcMath.mround(-12.7, -5.0), 0.0001);

      // Test with zero
      assertEquals(0.0, CalcMath.mround(0.0, 5.0), 0.0001);

      // Test with invalid input (different signs for number and multiple)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.mround(-8.5, 5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcMath.mround(8.5, -5.0));
      assertEquals("Number and Significance should have same signs", exception.getMessage());
   }

   @Test
   void testMultinomial() {
      // Test with valid inputs
      assertEquals(10.0, CalcMath.multinomial(new Object[]{2.0, 3.0}), 0.0001);
      assertEquals(1.0, CalcMath.multinomial(new Object[]{0.0, 0.0}), 0.0001);
      assertEquals(2520.0, CalcMath.multinomial(new Object[]{5.0, 3.0, 2.0}), 0.0001);

      // Test with an empty array
      assertEquals(1.0, CalcMath.multinomial(new Object[]{}), 0.0001);

      // Test with invalid input (negative number)
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.multinomial(new Object[]{2.0, -1.0}));
      assertEquals("MULTINOMIAL() requires inputs greater then one, " +
                      "but one of the values entered is -1.0", exception.getMessage());
   }

   @Test
   void testQuotient() {
      // Test with valid inputs
      assertEquals(2, CalcMath.quotient(5.0, 2.0));
      assertEquals(0, CalcMath.quotient(1.0, 3.0));
      assertEquals(-2, CalcMath.quotient(-5.0, 2.0));
      assertEquals(-2, CalcMath.quotient(5.0, -2.0));
      assertEquals(2, CalcMath.quotient(-5.0, -2.0));

      // Test with denominator as zero
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.quotient(5.0, 0.0));
      assertEquals("Denominator cannot be zero", exception.getMessage());
   }

   @ParameterizedTest
   @CsvSource({
      "1, I",
      "4, IV",
      "9, IX",
      "40, XL",
      "90, XC",
      "400, CD",
      "900, CM",
      "1000, M",
      "3999, MMMCMXCIX", // Maximum valid input
      "0, ''" // No Roman numeral for 0
   })
   void testRoman(int number, String expected) {
      assertEquals(expected, CalcMath.roman(number));
   }

   @Test
   void testSumif() {
      // Test with valid inputs
      Object range = new Object[]{1.0, 2.0, 3.0, 4.0};
      Object sumRange = new Object[]{10.0, 20.0, 30.0, 40.0};
      String criteria = ">2";
      assertEquals(70.0, CalcMath.sumif(range, criteria, sumRange), 0.0001);

      // Test with mismatched range and sumRange lengths
      Object invalidSumRange = new Object[]{10.0, 20.0};//bug #71337
   }

   @Test
   void testSumproduct() {
      // Test with valid inputs
      Object arrays = new Object[]{
         new Object[][]{{1.0, 2.0}, {3.0, 4.0}},
         new Object[][]{{5.0, 6.0}, {7.0, 8.0}}
      };
      assertEquals(70.0, CalcMath.sumproduct(arrays), 0.0001);

      // Test with arrays of different dimensions
      Object invalidArrays = new Object[]{
         new Object[][]{{1.0, 2.0}, {3.0, 4.0}},
         new Object[][]{{5.0, 6.0}}
      };
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcMath.sumproduct(invalidArrays)
      );
      assertEquals("Arrays should have same dimensions", exception.getMessage());

      // Test with empty arrays
      Object emptyArrays = new Object[]{};
      exception = assertThrows(IndexOutOfBoundsException.class, () ->
         CalcMath.sumproduct(emptyArrays)
      );
   }

   @ParameterizedTest
   @MethodSource("provideSumsqTestData")
   void testSumsq(Object[] numbers, double expected) {
      assertEquals(expected, CalcMath.sumsq(numbers), 0.0001);
   }

   private static Stream<Arguments> provideSumsqTestData() {
      return Stream.of(
         Arguments.of(new Object[]{1.0, 2.0, 3.0}, 14.0), // Test with positive numbers
         Arguments.of(new Object[]{-1.0, -2.0, -3.0}, 14.0), // Test with negative numbers
         Arguments.of(new Object[]{1.0, -2.0, 3.0}, 14.0), // Test with a mix of positive and negative numbers
         Arguments.of(new Object[]{0.0, 0.0, 0.0}, 0.0), // Test with zero
         Arguments.of(new Object[]{}, 0.0), // Test with an empty array
         Arguments.of(new Object[]{5.0}, 25.0) // Test with a single number
      );
   }

   @Test
   void testSumx2my2() {
      // Test with valid input arrays
      Object arrayX = new Object[]{1.0, 2.0, 3.0};
      Object arrayY = new Object[]{4.0, 5.0, 6.0};
      assertEquals(-63.0, CalcMath.sumx2my2(arrayX, arrayY), 0.0001);

      // Test with arrays of different lengths
      Object invalidArrayX = new Object[]{1.0, 2.0};
      Object invalidArrayY = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcMath.sumx2my2(invalidArrayX, invalidArrayY)
      );
      assertEquals("array_x and array_y have a different number of values", exception.getMessage());

      // Test with empty arrays
      Object emptyArrayX = new Object[]{};
      Object emptyArrayY = new Object[]{};
      assertEquals(0.0, CalcMath.sumx2my2(emptyArrayX, emptyArrayY), 0.0001);

      // Test with negative values
      Object negativeArrayX = new Object[]{-1.0, -2.0, -3.0};
      Object negativeArrayY = new Object[]{-3.0, -2.0, -1.0};
      assertEquals(0.0, CalcMath.sumx2my2(negativeArrayX, negativeArrayY), 0.0001);
   }

   // Unit test for sumx2py2
   @Test
   void testSumx2py2() {
      // Test with valid input arrays
      Object arrayX = new Object[]{1.0, 2.0, 3.0};
      Object arrayY = new Object[]{4.0, 5.0, 6.0};
      assertEquals(91.0, CalcMath.sumx2py2(arrayX, arrayY), 0.0001);

      // Test with arrays of different lengths
      Object invalidArrayX = new Object[]{1.0, 2.0};
      Object invalidArrayY = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcMath.sumx2py2(invalidArrayX, invalidArrayY));
      assertEquals("array_x and array_y have a different number of values", exception.getMessage());

      // Test with empty arrays
      Object emptyArrayX = new Object[]{};
      Object emptyArrayY = new Object[]{};
      assertEquals(0.0, CalcMath.sumx2py2(emptyArrayX, emptyArrayY), 0.0001);

      // Test with negative values
      Object negativeArrayX = new Object[]{-1.0, -2.0, -3.0};
      Object negativeArrayY = new Object[]{-4.0, -5.0, -6.0};
      assertEquals(91.0, CalcMath.sumx2py2(negativeArrayX, negativeArrayY), 0.0001);
   }

   @Test
   void testSumxmy2() {
      // Test with valid input arrays
      Object arrayX = new Object[]{1.0, 2.0, 3.0};
      Object arrayY = new Object[]{4.0, 5.0, 6.0};
      assertEquals(27.0, CalcMath.sumxmy2(arrayX, arrayY), 0.0001);

      // Test with arrays of different lengths
      Object invalidArrayX = new Object[]{1.0, 2.0};
      Object invalidArrayY = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcMath.sumxmy2(invalidArrayX, invalidArrayY));
      assertEquals("array_x and array_y have a different number of values", exception.getMessage());

      // Test with empty arrays
      Object emptyArrayX = new Object[]{};
      Object emptyArrayY = new Object[]{};
      assertEquals(0.0, CalcMath.sumxmy2(emptyArrayX, emptyArrayY), 0.0001);

      // Test with negative values
      Object negativeArrayX = new Object[]{-1.0, -2.0, -3.0};
      Object negativeArrayY = new Object[]{-4.0, -5.0, -6.0};
      assertEquals(27.0, CalcMath.sumxmy2(negativeArrayX, negativeArrayY), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "2.0, 3",
      "4.0, 5",
      "1.0, 1",
      "-2.0, -3",
      "-4.0, -5",
      "-1.0, -1",
      "0.0, 1"
   })
   void testOdd(double input, int expected) {
      assertEquals(expected, CalcMath.odd(input));
   }

   @Test
   void testMod() {
      // Test with positive numbers
      assertEquals(1.0, CalcMath.mod(10.0, 3.0), 0.0001);
      assertEquals(0.0, CalcMath.mod(9.0, 3.0), 0.0001);

      // Test with negative numbers
      assertEquals(2.0, CalcMath.mod(-10.0, 3.0), 0.0001);
      assertEquals(-2.0, CalcMath.mod(10.0, -3.0), 0.0001);
      assertEquals(-1.0, CalcMath.mod(-10.0, -3.0), 0.0001);

      // Test with zero numerator
      assertEquals(0.0, CalcMath.mod(0.0, 3.0), 0.0001);

      // Test with divisor as zero
      Exception exception = assertThrows(RuntimeException.class, () -> CalcMath.mod(10.0, 0.0));
      assertEquals("/ by zero, divisor is zero !", exception.getMessage());
   }

   @Test
   void testNone() {
      // Test with an array of numbers
      Object[] numbers = {1.0, 2.0, 3.0};
      assertEquals(3.0, CalcMath.none(numbers));

      // Test with an empty array
      Object[] emptyNumbers = {};
      assertEquals(Double.NaN, CalcMath.none(emptyNumbers));

      // Test with a single element
      Object[] singleNumber = {42.0};
      assertEquals(42.0, CalcMath.none(singleNumber));
   }

   @Test
   void testSign() {
      assertEquals(1, CalcMath.sign(5.0));
      assertEquals(-1, CalcMath.sign(-3.0));
      assertEquals(0, CalcMath.sign(0.0));
   }

   @Test
   void testRand() {
      double result = CalcMath.rand();
      assertTrue(result >= 0.0 && result < 1.0,
                 "Random number should be between 0 (inclusive) and 1 (exclusive)");
   }

   @Test
   void testRandbetween() {
      // Test with valid range
      int result = CalcMath.randbetween(1, 10);
      assertTrue(result >= 1 && result <= 10, "Result should be between 1 and 10");

      // Test with a single value range
      result = CalcMath.randbetween(5, 5);
      assertEquals(5, result, "Result should be 5 when range is the same");

      // Test with negative range
      result = CalcMath.randbetween(-10, -1);
      assertTrue(result >= -10 && result <= -1, "Result should be between -10 and -1");

      // Test with mixed range
      result = CalcMath.randbetween(-5, 5);
      assertTrue(result >= -5 && result <= 5, "Result should be between -5 and 5");
   }

   @Test
   void testRadians() {
      // Test with positive angle
      assertEquals(Math.PI, CalcMath.radians(180.0), 0.0001);

      // Test with negative angle
      assertEquals(-Math.PI, CalcMath.radians(-180.0), 0.0001);

      // Test with zero
      assertEquals(0.0, CalcMath.radians(0.0), 0.0001);

      // Test with fractional angle
      assertEquals(Math.PI / 2, CalcMath.radians(90.0), 0.0001);
   }
}