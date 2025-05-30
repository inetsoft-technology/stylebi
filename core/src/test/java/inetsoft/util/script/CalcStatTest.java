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

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CalcStatTest {
   @Test
   void testCount() {
      Object inputArray = new Object[] {1, false, 1, "abc", null, 5, ""};
      assertEquals(6, CalcStat.count(inputArray));
      assertEquals(5, CalcStat.countDistinct(inputArray));
      assertEquals(3, CalcStat.countn(inputArray));
      assertEquals(6, CalcStat.counta(inputArray));
      assertEquals(1, CalcStat.countblank(inputArray));//Counts null elements
      assertEquals(1, CalcStat.countif(inputArray, ">1"));
   }

   @Test
   void testFirstLast() {
      // Test case for 'first'
      Object array1 = new Object[] {10, 20, 30, 40};
      Object array2 = new Object[] {1, 2, 3, 4};
      assertEquals(10.0, CalcStat.first(array1, array2));

      // Test case for 'last'
      assertEquals(40.0, CalcStat.last(array1, array2));

      // Edge case: Empty arrays
      Object emptyArray1 = new Object[] {};
      Object emptyArray2 = new Object[] {};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.first(emptyArray1, emptyArray2);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());

      // Edge case: Arrays of different lengths
      Object array3 = new Object[] {10, 20};
      Object array4 = new Object[] {1, 2, 3};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.first(array3, array4);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());
   }

   @Test
   void testMaxAndMin() {
      // Test case for max and min
      Object inputArray1 = new Object[] {1.5, 3.2, 7.8, 2.1};
      assertEquals(7.8, CalcStat.max(inputArray1));
      assertEquals(1.5, CalcStat.min(inputArray1));

      Object inputArray2 = new Object[] {-5.0, -1.2, -3.4};
      assertEquals(-1.2, CalcStat.max(inputArray2));
      assertEquals(-5.0, CalcStat.min(inputArray2));

      Object inputArray3 = new Object[] {};
      assertEquals(Double.NaN, CalcStat.max(inputArray3));
      assertEquals(Double.NaN, CalcStat.min(inputArray3));

      // Test case for maxa and mina
      Object inputArray4 = new Object[] {1.5, "3.2", 7.8, false};
      assertEquals(7.8, CalcStat.maxa(inputArray4));
      assertEquals(0.0, CalcStat.mina(inputArray4));

      Object inputArray5 = new Object[] {null, "abc", 5.0, ""};
      assertEquals(5.0, CalcStat.maxa(inputArray5));
      assertEquals(0.0, CalcStat.mina(inputArray5));

      assertEquals(Double.NaN, CalcStat.maxa(inputArray3));
      assertEquals(Double.NaN, CalcStat.mina(inputArray3));
   }

   @ParameterizedTest
   @MethodSource("provideMedianTestCases")
   void testMedian(Object[] inputArray, double expectedMedian) {
      assertEquals(expectedMedian, CalcStat.median(inputArray));
   }

   private static Stream<Arguments> provideMedianTestCases() {
      return Stream.of(
         // Test case: Odd number of elements
         Arguments.of(new Object[] {3.0, 1.0, 4.0, 2.0, 5.0}, 3.0),
         // Test case: Even number of elements
         Arguments.of(new Object[] {10.0, 20.0, 30.0, 40.0}, 25.0),
         // Test case: Empty array
         Arguments.of(new Object[] {}, Double.NaN),
         // Test case: Array with duplicate values
         Arguments.of(new Object[] {1.0, 2.0, 2.0, 3.0, 4.0}, 2.0),
         // Test case: Array with a single element
         Arguments.of(new Object[] {7.0}, 7.0)
      );
   }

   @Test
   void testFrequency() {
      // Test case: Normal case
      Object[] dataArray = new Object[] {1.0, 2.0, 2.0, 3.0, 3.0, 3.0};
      Object[] binsArray = new Object[] {1.5, 2.5};
      int[] expected = new int[] {1, 2, 3}; // Frequencies for bins: [1.0-1.5), [1.5-2.5), [2.5-∞)
      assertArrayEquals(expected, CalcStat.frequency(dataArray, binsArray));

      // Test case: Empty data array
      Object[] emptyDataArray = new Object[] {};
      Object[] binsArray2 = new Object[] {1.5, 2.5};
      int[] expectedEmpty = new int[] {0, 0, 0};
      assertArrayEquals(expectedEmpty, CalcStat.frequency(emptyDataArray, binsArray2));

      // Test case: Data with negative values
      Object[] dataArray3 = new Object[] {-3.0, -2.0, -1.0, 0.0, 1.0};
      Object[] binsArray3 = new Object[] {-2.5, 0.5};
      int[] expectedNegative = new int[] {1, 3, 1}; // Frequencies for bins: [-∞--2.5), [-2.5-0.5), [0.5-∞)
      assertArrayEquals(expectedNegative, CalcStat.frequency(dataArray3, binsArray3));
   }

   @Test
   void testQuartile() {
      // Test case: Normal case
      Object[] inputArray = new Object[] {1.0, 2.0, 3.0, 4.0, 5.0};
      assertEquals(1.0, CalcStat.quartile(inputArray, 0)); // Minimum
      assertEquals(2.0, CalcStat.quartile(inputArray, 1)); // Q1
      assertEquals(3.0, CalcStat.quartile(inputArray, 2)); // Median
      assertEquals(4.0, CalcStat.quartile(inputArray, 3)); // Q3
      assertEquals(5.0, CalcStat.quartile(inputArray, 4)); // Maximum

      // Test case: Array with duplicate values
      Object[] inputArrayWithDuplicates = new Object[] {1.0, 2.0, 2.0, 3.0, 4.0};
      assertEquals(2.0, CalcStat.quartile(inputArrayWithDuplicates, 1)); // Q1
      assertEquals(2.0, CalcStat.quartile(inputArrayWithDuplicates, 2)); // Median

      // Test case: Invalid quart value
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.quartile(inputArray, 5);
      });
      assertEquals("quartile cannot be greater than 4", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.quartile(inputArray, -1);
      });
      assertEquals("quartile cannot be negative", exception.getMessage());
   }

   @Test
   void testProb() {
      // Test case: Normal case with valid inputs
      Object[] xRange = new Object[] {1.0, 2.0, 3.0, 4.0};
      Object[] probRange = new Object[] {0.1, 0.2, 0.3, 0.4};
      double lowerLimit = 2.0;
      double upperLimit = 3.0;
      assertEquals(0.5, CalcStat.prob(xRange, probRange, lowerLimit, upperLimit), 0.0001);

      // Test case: Single value match
      upperLimit = 0; // Optional parameter not provided
      assertEquals(0.2, CalcStat.prob(xRange, probRange, lowerLimit, upperLimit), 0.0001);

      // Test case: Invalid probability range
      Object[] invalidProbRange = new Object[] {0.1, 0.2, 1.5, 0.4};
      double finalLowerLimit = lowerLimit;
      double finalUpperLimit = upperLimit;
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.prob(xRange, invalidProbRange, finalLowerLimit, finalUpperLimit);
      });
      assertEquals("Probability should be greater than 0 and less than or equal to 1",
                   exception.getMessage());

      // Test case: Sum of probabilities exceeds 1
      Object[] exceedingProbRange = new Object[] {0.5, 0.5, 0.5, 0.5};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.prob(xRange, exceedingProbRange, finalLowerLimit, finalUpperLimit);
      });
      assertEquals("Sum of probabilities cannot be greater than 1", exception.getMessage());

      // Test case: Mismatched array lengths
      Object[] shorterProbRange = new Object[] {0.1, 0.2};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.prob(xRange, shorterProbRange, finalLowerLimit, finalUpperLimit);
      });
      assertEquals("x_range and prob_range contain different number of data points",
                   exception.getMessage());

      // Test case: No match for lower limit
      lowerLimit = 5.0;
      upperLimit = 0;
      assertEquals(0.0, CalcStat.prob(xRange, probRange, lowerLimit, upperLimit), 0.0001);
   }

   @Test
   void testPercentRank() {
      // Test case: Normal case
      Object inputArray = new Object[] {10.0, 20.0, 30.0, 40.0, 50.0};
      double x = 30.0;
      double significance = 2;
      assertEquals(0.5, CalcStat.percentrank(inputArray, x, significance));

      // Test case: Value not in the array but within range
      x = 25.0;
      assertEquals(0.375, CalcStat.percentrank(inputArray, x, significance), 0.01);

      // Test case: Value greater than the largest element
      x = 60.0;
      assertEquals(1.0, CalcStat.percentrank(inputArray, x, significance));

      // Test case: Empty array
      Object emptyArray = new Object[] {};
      double finalX = x;
       Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.percentrank(emptyArray, finalX, significance);
      });
      assertEquals("Array of numbers cannot be empty", exception.getMessage());

      // Test case: Invalid significance
      double significance1 = 0;
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.percentrank(inputArray, finalX, significance1);
      });
      assertEquals("Significance should be at least equal to one", exception.getMessage());
   }

   @Test
   void testSteyx() {
      // Test case: Valid input
      Object knownYs = new Object[]{1.0, 2.0, 3.0};
      Object knownXs = new Object[]{2.0, 4.0, 6.0};
      assertEquals(0.0, CalcStat.steyx(knownYs, knownXs), 0.0001);

      // Test case: Empty arrays
      Object emptyYs = new Object[]{};
      Object emptyXs = new Object[]{};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.steyx(emptyYs, emptyXs);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());

      // Test case: Arrays with different lengths
      Object differentLengthYs = new Object[]{1.0, 2.0};
      Object differentLengthXs = new Object[]{2.0, 4.0, 6.0};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.steyx(differentLengthYs, differentLengthXs);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Arrays with less than 3 data points
      Object insufficientYs = new Object[]{1.0, 2.0};
      Object insufficientXs = new Object[]{2.0, 4.0};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.steyx(insufficientYs, insufficientXs);
      });
      assertEquals("Arrays should have at least 3 data points, / by zero", exception.getMessage());
   }

   @Test
   void testForecast() {
      // Test case: Normal case
      Object knownYs = new Object[]{1.0, 2.0, 3.0, 4.0};
      Object knownXs = new Object[]{1.0, 2.0, 3.0, 4.0};
      double x = 5.0;
      assertEquals(5.0, CalcStat.forecast(x, knownYs, knownXs), 0.0001);

      // Test case: Arrays of different lengths
      Object mismatchedYs = new Object[]{1.0, 2.0};
      Object mismatchedXs = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.forecast(x, mismatchedYs, mismatchedXs);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Empty arrays
      Object emptyYs = new Object[]{};
      Object emptyXs = new Object[]{};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.forecast(x, emptyYs, emptyXs);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());
   }

   @Test
   void testCorrel() {
      // Test case: Normal case
      Object array1 = new Object[] {1.0, 2.0, 3.0};
      Object array2 = new Object[] {2.0, 4.0, 6.0};
      assertEquals(1.0, CalcStat.correl(array1, array2), 0.0001);

      // Test case: Arrays with no correlation
      Object array3 = new Object[] {1.0, 2.0, 3.0};
      Object array4 = new Object[] {3.0, 2.0, 1.0};
      assertEquals(-1.0, CalcStat.correl(array3, array4), 0.0001);

      // Test case: Arrays with different lengths
      Object array5 = new Object[] {1.0, 2.0};
      Object array6 = new Object[] {2.0, 4.0, 6.0};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.correl(array5, array6);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Empty arrays
      Object emptyArray1 = new Object[] {};
      Object emptyArray2 = new Object[] {};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.correl(emptyArray1, emptyArray2);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());
   }

   @Test
   void testIntercept() {
      // Test case: Normal case
      Object[] knownYs = new Object[]{1.0, 2.0, 3.0};
      Object[] knownXs = new Object[]{1.0, 2.0, 3.0};
      assertEquals(0.0, CalcStat.intercept(knownYs, knownXs), 0.0001);

      // Test case: Arrays with different lengths
      Object[] mismatchedYs = new Object[]{1.0, 2.0};
      Object[] mismatchedXs = new Object[]{1.0, 2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.intercept(mismatchedYs, mismatchedXs);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Empty arrays
      Object[] emptyYs = new Object[]{};
      Object[] emptyXs = new Object[]{};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.intercept(emptyYs, emptyXs);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());

      // Test case: Negative slope
      Object[] negativeSlopeYs = new Object[]{3.0, 2.0, 1.0};
      Object[] negativeSlopeXs = new Object[]{1.0, 2.0, 3.0};
      assertEquals(4.0, CalcStat.intercept(negativeSlopeYs, negativeSlopeXs), 0.0001);
   }

   @Test
   void testPearson() {
      // Test case: Normal case
      Object[] array1 = new Object[] {1.0, 2.0, 3.0};
      Object[] array2 = new Object[] {2.0, 4.0, 6.0};
      assertEquals(1.0, CalcStat.pearson(array1, array2), 0.0001);

      // Test case: Arrays with no correlation
      Object[] array3 = new Object[] {1.0, 2.0, 3.0};
      Object[] array4 = new Object[] {3.0, 2.0, 1.0};
      assertEquals(-1.0, CalcStat.pearson(array3, array4), 0.0001);

      // Test case: Arrays with different lengths
      Object[] array5 = new Object[] {1.0, 2.0};
      Object[] array6 = new Object[] {2.0, 4.0, 6.0};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.pearson(array5, array6);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());
   }

   @Test
   void testSlope() {
      // Test case: Valid input
      Object[] knownYs = new Object[]{8,11,14,17};
      Object[] knownXs = new Object[]{1,2,3,4};
      double expectedSlope = 3.0;
      assertEquals(expectedSlope, CalcStat.slope(knownYs, knownXs), 1e-6);

      // Test case: Arrays of different lengths
      Object[] invalidYs = new Object[]{1, 2, 3};
      Object[] invalidXs = new Object[]{1, 2, 3, 4};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.slope(invalidYs, invalidXs));
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Empty arrays
      Object[] emptyYs = new Object[]{};
      Object[] emptyXs = new Object[]{};
      exception = assertThrows(RuntimeException.class, () -> CalcStat.slope(emptyYs, emptyXs));
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());
   }

   @Test
   void testCovar() {
      // Test case: Normal case
      Object[] array1 = new Object[] {1.0, 2.0, 3.0};
      Object[] array2 = new Object[] {4.0, 5.0, 6.0};
      assertEquals(0.6667, CalcStat.covar(array1, array2), 0.0001);

      // Test case: Arrays with negative values
      Object[] array3 = new Object[] {-1.0, -2.0, -3.0};
      Object[] array4 = new Object[] {-4.0, -5.0, -6.0};
      assertEquals(0.6667, CalcStat.covar(array3, array4), 0.0001);

      // Test case: Arrays with different ranges
      Object[] array5 = new Object[] {1.0, 2.0, 3.0};
      Object[] array6 = new Object[] {10.0, 20.0, 30.0};
      assertEquals(6.6667, CalcStat.covar(array5, array6), 0.0001);

      // Test case: Empty arrays
      Object[] emptyArray1 = new Object[] {};
      Object[] emptyArray2 = new Object[] {};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.covar(emptyArray1, emptyArray2);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());

      // Test case: Arrays with different lengths
      Object[] array7 = new Object[] {1.0, 2.0};
      Object[] array8 = new Object[] {3.0, 4.0, 5.0};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.covar(array7, array8);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());
   }

   @Test
   void testRank() {
      // Test case: Ascending order
      Object[] ref = {10, 20, 30, 40, 50, 60};
      double order = 1; // Ascending order
      int result = CalcStat.rank(30, ref, order);
      assertEquals(3, result);

      // Test case: Descending order
      order = 0; // Descending order
      result = CalcStat.rank(30, ref, order);
      assertEquals(4, result);

      // Test case: Number not found
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.rank(70, ref, 0);
      });
      assertEquals("number not found in the ref list", exception.getMessage());

      // Test case: Empty array
      Object[] emptyRef = {};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.rank(10, emptyRef, 0);
      });
      assertEquals("number not found in the ref list", exception.getMessage());
   }

   @Test
   void testBinomdist() {
      // Test case: Valid inputs, non-cumulative
      assertEquals(0.3125, CalcStat.binomdist(2, 5, 0.5, false), 0.0001);

      // Test case: Valid inputs, cumulative
      assertEquals(0.5, CalcStat.binomdist(2, 5, 0.5, true), 0.0001);

      // Test case: Probability out of range
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.binomdist(2, 5, 1.5, false);
      });
      assertEquals("Probability should be between 0 and 1", exception.getMessage());

      // Test case: number_s out of range
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.binomdist(-1, 5, 0.5, false);
      });
      assertEquals("number_s should be between 0 and trials (both inclusive)", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.binomdist(6, 5, 0.5, false);
      });
      assertEquals("number_s should be between 0 and trials (both inclusive)", exception.getMessage());

      // Test case: Edge case with number_s = 0
      assertEquals(0.03125, CalcStat.binomdist(0, 5, 0.5, false), 0.0001);

      // Test case: Edge case with number_s = trials
      assertEquals(0.03125, CalcStat.binomdist(5, 5, 0.5, false), 0.0001);
   }

   @Test
   void testKurt() {
      Object[] inputArray = new Object[] {4,5,8,7,8,4,5,3,7};
      assertEquals(-1.6851311953352766, CalcStat.kurt(inputArray), 0.01);
   }

   @Test
   void testSkew() {
      Object[] inputArray = new Object[] {3,4,5,2,3,4,5,6,4,7};
      assertEquals(0.3595430714067974, CalcStat.skew(inputArray), 1e-6);

      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.skew(new Object[]{1}));
      assertEquals("DataSet should contain at least 3 points", exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcStat.skew(new Object[]{1, 1, 1}));
      assertEquals("Standard Deviation of the dataset is zero", exception.getMessage());
   }

   @Test
   void testWeightedAvg() {
      // Test case: Normal case
      Object[] array1 = new Object[]{50,76,80,98};
      Object[] array2 = new Object[]{0.15,0.2,0.2,0.45};
      assertEquals(82.8, CalcStat.weightedavg(array1, array2), 0.0001);

      // Test case: Arrays with different lengths
      Object[] mismatchedArray1 = new Object[]{1.0, 2.0};
      Object[] mismatchedArray2 = new Object[]{4.0, 5.0, 6.0};
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.weightedavg(mismatchedArray1, mismatchedArray2);
      });
      assertEquals("Arrays should have same number of values", exception.getMessage());

      // Test case: Empty arrays
      Object[] emptyArray1 = new Object[]{};
      Object[] emptyArray2 = new Object[]{};
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.weightedavg(emptyArray1, emptyArray2);
      });
      assertEquals("Arrays cannot be empty, / by zero", exception.getMessage());

      // Test case: Single element arrays
      Object[] singleArray1 = new Object[]{10.0};
      Object[] singleArray2 = new Object[]{2.0};
      assertEquals(10.0, CalcStat.weightedavg(singleArray1, singleArray2), 0.0001);
   }

   @Test
   void testWeibull() {
      // Test cumulative case
      double cumulativeResult = CalcStat.weibull(2.0, 1.5, 3.0, true);
      assertEquals(0.41977020402532716, cumulativeResult, 1e-6);

      // Test non-cumulative case
      double nonCumulativeResult = CalcStat.weibull(2.0, 1.5, 3.0, false);
      assertEquals(0.2368778222828562, nonCumulativeResult, 1e-6);

      // Test invalid x value
      Exception xException = assertThrows(RuntimeException.class, () -> CalcStat.weibull(-1.0, 1.5, 3.0, true));
      assertEquals("X should at least be equal to 0", xException.getMessage());

      // Test invalid alpha value
      Exception alphaException = assertThrows(RuntimeException.class, () -> CalcStat.weibull(2.0, 0.0, 3.0, true));
      assertEquals("Alpha should be greater than 0", alphaException.getMessage());

      // Test invalid beta value
      Exception betaException = assertThrows(RuntimeException.class, () -> CalcStat.weibull(2.0, 1.5, 0.0, true));
      assertEquals("Beta should be greater than 0", betaException.getMessage());
   }

   @Test
   void testPercentile() {
      // Test case: Normal case
      Object[] inputArray = new Object[]{1.0, 2.0, 3.0, 4.0, 5.0};
      // 50th percentile (median)
      assertEquals(3.0, CalcStat.percentile(inputArray, 0.5), 0.0001);

      // Test case: Edge case with k = 1 (maximum value)
      assertEquals(5.0, CalcStat.percentile(inputArray, 1.0), 0.0001);

      // Test case: Empty array
      Object[] emptyArray = new Object[]{};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.percentile(emptyArray, 0.5));
      assertEquals("Array should not be empty", exception.getMessage());

      // Test case: Array with more than 8191 elements
      Object[] largeArray = new Object[8192];
      for (int i = 0; i < largeArray.length; i++) {
         largeArray[i] = (double) i;
      }
      exception = assertThrows(RuntimeException.class, () -> CalcStat.percentile(largeArray, 0.5));
      assertEquals("Array should have less than 8191 data points", exception.getMessage());

      // Test case: Invalid k (less than or equal to 0), bug #71297
      exception = assertThrows(RuntimeException.class, () -> CalcStat.percentile(inputArray, 0));
      assertEquals("K should be a value large than 0 and less than or equal to 1", exception.getMessage());
      exception = assertThrows(RuntimeException.class, () -> CalcStat.percentile(inputArray, -0.1));
      assertEquals("K should be a value large than 0 and less than or equal to 1", exception.getMessage());

      // Test case: Invalid k (greater than 1)
      exception = assertThrows(RuntimeException.class, () -> CalcStat.percentile(inputArray, 1.1));
      assertEquals("K should be a value large than 0 and less than or equal to 1", exception.getMessage());
   }

   @Test
   void testTrimmean() {
      // Test with a valid array and percentage
      Object[] array = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
      double percent = 0.2;
      assertEquals(5.5, CalcStat.trimmean(array, percent), 0.001);

      // Test with a percentage less than 0
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.trimmean(array, -0.1));
      assertEquals("Percentage should be between 0 and 1", exception.getMessage());

      // Test with a percentage greater than 1
      exception = assertThrows(RuntimeException.class, () -> CalcStat.trimmean(array, 1.1));
      assertEquals("Percentage should be between 0 and 1", exception.getMessage());

      // Test with a single-element array
      Object[] singleElementArray = {5};
      assertEquals(5.0, CalcStat.trimmean(singleElementArray, percent), 0.001);
   }

   @Test
   void testNegbinomdist() {
      // Test case: Valid inputs
      assertEquals(0.1875, CalcStat.negbinomdist(2, 3, 0.5), 0.0001);

      // Test case: Probability out of range
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.negbinomdist(2, 3, 1.5);
      });
      assertEquals("Probability should be between 0 and 1", exception.getMessage());

      // Test case: number_f less than 0
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.negbinomdist(-1, 3, 0.5);
      });
      assertEquals("number_f should be at least equal to zero and number_s should be greater than 1",
                   exception.getMessage());

      // Test case: number_s less than 1
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.negbinomdist(2, 0, 0.5);
      });
      assertEquals("number_f should be at least equal to zero and number_s should be greater than 1",
                   exception.getMessage());

      // Test case: Edge case with number_f = 0
      assertEquals(0.125, CalcStat.negbinomdist(0, 3, 0.5), 0.0001);

      // Test case: Edge case with number_s = 1
      assertEquals(0.125, CalcStat.negbinomdist(2, 1, 0.5), 0.0001);
   }

   @Test
   void testAvedev() {
      // Test case: Normal case
      Object inputArray = new Object[] {1.0, 2.0, 3.0, 4.0, 5.0};
      assertEquals(1.2, CalcStat.avedev(inputArray), 0.0001);

      // Test case: Array with negative values
      Object inputArrayWithNegatives = new Object[] {-1.0, -2.0, -3.0, -4.0, -5.0};
      assertEquals(1.2, CalcStat.avedev(inputArrayWithNegatives), 0.0001);

      // Test case: Array with a single element
      Object singleElementArray = new Object[] {10.0};
      assertEquals(0.0, CalcStat.avedev(singleElementArray), 0.0001);
   }

   @Test
   void testDevsq() {
      // Test case: Normal case
      Object[] inputArray = new Object[] {1.0, 2.0, 3.0, 4.0, 5.0};
      assertEquals(10.0, CalcStat.devsq(inputArray), 0.0001);

      // Test case: Array with negative values
      Object[] inputArrayWithNegatives = new Object[] {-1.0, -2.0, -3.0, -4.0, -5.0};
      assertEquals(10.0, CalcStat.devsq(inputArrayWithNegatives), 0.0001);

      // Test case: Array with a single element
      Object[] singleElementArray = new Object[] {10.0};
      assertEquals(0.0, CalcStat.devsq(singleElementArray), 0.0001);
   }

   @Test
   void testExpondist() {
      // Test case: Cumulative distribution
      assertEquals(0.6321205588285577, CalcStat.expondist(2.0, 0.5, true), 1e-6);

      // Test case: Probability density function
      assertEquals(0.18393972058572117, CalcStat.expondist(2.0, 0.5, false), 1e-6);

      // Test case: Invalid x (negative value)
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.expondist(-1.0, 0.5, true);
      });
      assertEquals("X should be at least equal to 0", exception.getMessage());

      // Test case: Invalid lambda (zero value)
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.expondist(2.0, 0.0, true);
      });
      assertEquals("Lambda should be greater than 0", exception.getMessage());

      // Test case: Invalid lambda (negative value)
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.expondist(2.0, -0.5, true);
      });
      assertEquals("Lambda should be greater than 0", exception.getMessage());
   }

   @Test
   void testFisher() {
      // Test case: Valid input within range
      assertEquals(0.5493, CalcStat.fisher(0.5), 0.0001);

      // Test cases: Edge cases and invalid inputs for Fisher function
      double[] invalidInputs = {-1, 1, -1.5, 1.5};
      for (double input : invalidInputs) {
         Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.fisher(input));
         assertEquals("X should be between -1 and 1", exception.getMessage());
      }
   }

   @Test
   void testVarn() {
      Object numbers = new Object[]{1, 2, 3, 4, 5};
      double result = CalcStat.varn(numbers);
      assertEquals(2.5, result, 0.0001);
   }

   @Test
   void testVara() {
      Object numbers = new Object[]{1, 2, 3, 4, "5"};
      double result = CalcStat.vara(numbers);
      assertEquals(2.5, result, 0.0001);
   }

   @Test
   void testVarp() {
      Object[] numbers = {1, 2, 3, 4, 5};
      double result = CalcStat.varp(numbers);
      assertEquals(2.0, result, 0.0001);
   }

   @Test
   void testPermut() {
      // Test case: Normal case
      assertEquals(20.0, CalcStat.permut(5, 2), 0.0001);

      // Test case: Edge case where number == number_chosen
      assertEquals(120.0, CalcStat.permut(5, 5), 0.0001);

      // Test case: Edge case where number_chosen == 0
      assertEquals(1.0, CalcStat.permut(5, 0), 0.0001);

      // Test case: Invalid cases for CalcStat.permut
      int[][] invalidInputs = {
         {3, 5},  // number < number_chosen
         {-1, 2}, // number is negative
         {5, -2}  // number_chosen is negative
      };

      for (int[] input : invalidInputs) {
         Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.permut(input[0], input[1]));
         assertEquals("number should be at least equal to the number_chosen and both these " +
                         "variables should hold non-negative values", exception.getMessage());
      }
   }

   @Test
   void testVarpa() {
      Object[] numbers = {1, 2, 3, 4, 5};
      double result = CalcStat.varpa(numbers);
      assertEquals(2.0, result, 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "0.5, 0.4621171572600098",
      "0.0, 0.0",
      "-0.5, -0.4621171572600098",
      "20.0, 0.9999999999999998",
      "-20.0, -0.9999999999999998"
   })
   void testFisherInv(double input, double expected) {
      assertEquals(expected, CalcStat.fisherinv(input), 0.0001);
   }

   @ParameterizedTest
   @CsvSource({
      "5.0, 5.0, 1.0, 0.0",  // Normal case
      "7.0, 5.0, 1.0, 2.0",  // Positive deviation
      "3.0, 5.0, 1.0, -2.0"  // Negative deviation
   })
   void testStandardize(double x, double mean, double stddev, double expected) {
      assertEquals(expected, CalcStat.standardize(x, mean, stddev), 0.0001);
   }

   @Test
   void testStdev() {
      Object[] input = {1, 2, 3, 4, 5};
      double expected = Math.sqrt(2.5); // Standard deviation of the sample
      assertEquals(expected, CalcStat.stdev(input), 0.0001);
   }

   @Test
   void testStdeva() {
      Object[] input = {1, 2, "3", null, 4, 5};
      double expected = Math.sqrt(2.5); // Standard deviation of the sample considering non-numeric
      assertEquals(expected, CalcStat.stdeva(input), 0.0001);
   }

   @Test
   void testStdevp() {
      Object[] numbers = {1, 2, 3, 4, 5};
      double result = CalcStat.stdevp(numbers);
      assertEquals(1.4142, result, 0.0001);
   }

   @Test
   void testAveragea() {
      // Test case: Normal case with numeric and non-numeric values
      Object[] inputArray = new Object[] {1, 2, "3", null, 4, 5};
      assertEquals(3.0, CalcStat.averagea(inputArray), 0.0001);

      // Test case: Array with only non-numeric values
      Object[] nonNumericArray = new Object[] {"a", null, false};
      assertEquals(0.0, CalcStat.averagea(nonNumericArray), 0.0001);

      // Test case: Empty array
      Object[] emptyArray = new Object[] {};
      assertEquals(Double.NaN, CalcStat.averagea(emptyArray), 0.0001);

      // Test case: Array with a single numeric value
      Object[] singleElementArray = new Object[] {10};
      assertEquals(10.0, CalcStat.averagea(singleElementArray), 0.0001);

      // Test case: Array with mixed numeric and boolean values
      Object[] mixedArray = new Object[] {1, true, false, 3};
      assertEquals(1.25, CalcStat.averagea(mixedArray), 0.0001);
   }

   @Test
   void testStdevpa() {
      Object[] numbers = {1, 2, 3, 4, 5, "non-numeric"};
      double result = CalcStat.stdevpa(numbers);
      assertEquals(1.707825127659933, result, 0.0001);
   }

   @Test
   void testRsq() {
      Object[] array1 = {1.0, 2.0, 3.0};
      Object[] array2 = {2.0, 4.0, 6.0};

      assertEquals(1.0, CalcStat.rsq(array1, array2), 0.0001);
   }

   @Test
   void testGeomean() {
      // Test case: Normal case with positive numbers
      Object[] inputArray = new Object[]{1.0, 2.0, 3.0, 4.0};
      assertEquals(2.213363839400643, CalcStat.geomean(inputArray), 0.0001);

      // Test case: Array with a single positive number
      Object[] singleElementArray = new Object[]{5.0};
      assertEquals(5.0, CalcStat.geomean(singleElementArray), 0.0001);

      // Test case: Array with zero or negative values (should throw exception)
      Object[] invalidArray = new Object[]{1.0, -2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.geomean(invalidArray));
      assertEquals("Values should be greater than zero", exception.getMessage());
   }

   @Test
   void testHarmean() {
      // Test case: Normal case with positive numbers
      Object[] inputArray = new Object[]{1.0, 2.0, 4.0};
      assertEquals(1.7142857142857142, CalcStat.harmean(inputArray), 0.0001);

      // Test case: Array with a single positive number
      Object[] singleElementArray = new Object[]{5.0};
      assertEquals(5.0, CalcStat.harmean(singleElementArray), 0.0001);

      // Test case: Array with zero or negative values (should throw exception)
      Object[] invalidArray = new Object[]{1.0, -2.0, 3.0};
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.harmean(invalidArray));
      assertEquals("Values should be greater than zero", exception.getMessage());
   }

   @Test
   void testHypgeomdist() {
      // Test case: Normal case
      assertEquals(0.4166666666666667, CalcStat.hypgeomdist(2, 5, 3, 10), 0.0001);

      // Test case: Edge case where sample_s = 0
      assertEquals(0.08333333333333333, CalcStat.hypgeomdist(0, 5, 3, 10), 0.0001);

      // Test case: Edge case where sample_s = population_s
      assertEquals(0.08333333333333333, CalcStat.hypgeomdist(3, 5, 3, 10), 0.0001);

      // Test case: Invalid case where sample_s > population_s
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.hypgeomdist(4, 5, 3, 10);
      });
      assertEquals("number should be at least equal to the number_chosen and both these variables" +
                      " should hold non-negative values", exception.getMessage());

      // Test case: Invalid case where number_sample > number_population
      exception = assertThrows(RuntimeException.class, () -> {
         CalcStat.hypgeomdist(2, 11, 3, 10);
      });
      assertEquals("number should be at least equal to the number_chosen and both these variables" +
                      " should hold non-negative values", exception.getMessage());
   }

   @ParameterizedTest
   @CsvSource({
      "2, 3.0, false, 0.22404180765538775", // Non-cumulative Poisson distribution
      "2, 3.0, true, 0.42319008112684353",  // Cumulative Poisson distribution
      "0, 3.0, false, 0.0498",              // Edge case with x = 0
      "0, 0.0, false, 1.0"                  // Edge case with mean = 0
   })
   void testPoisson(int x, double mean, boolean cumulative, double expected) {
      assertEquals(expected, CalcStat.poisson(x, mean, cumulative), 0.0001);
   }

   @Test
   void testPthPercentile() {
      // Test case: Normal case
      Object[] inputArray = new Object[]{1.0, 2.0, 3.0, 4.0, 5.0};
      assertEquals(3.0, CalcStat.pthpercentile(inputArray, 50));

      // Test case: Edge case with p = 0 (minimum value)
      assertEquals(1.0, CalcStat.pthpercentile(inputArray, 0));

      // Test case: Edge case with p = 100 (maximum value)
      assertEquals(5.0, CalcStat.pthpercentile(inputArray, 100));
   }

   @Test
   void testNthMostFrequent() {
      // Test case: Normal case with null value
      Object[] inputArray = new Object[] {1, 2, 2, 3, 3, 3, 4, 4, 4, 4, null, null};
      assertEquals(4.0, CalcStat.nthmostfrequent(inputArray, 1)); // Most frequent
      assertEquals(3.0, CalcStat.nthmostfrequent(inputArray, 2)); // Second most frequent
      assertEquals(2.0, CalcStat.nthmostfrequent(inputArray, 3)); // Third most frequent
      assertEquals(1.0, CalcStat.nthmostfrequent(inputArray, 4)); // Fourth most frequent
      assertNull(CalcStat.nthmostfrequent(inputArray, 5)); // Out of bounds

      // Test case: Array with all unique values
      assertEquals(5.0, CalcStat.nthmostfrequent(new Object[] {5, 6, 7}, 1));

      // Test case: Empty array
      assertNull(CalcStat.nthmostfrequent(new Object[] {}, 1)); // No elements to process
   }

   @Test
   void testNthLargestAndSmallest() {
      // Test case: Normal case
      Object[] inputArray = new Object[] {3.5, 1.2, 7.8, 2.1};
      assertEquals(3.5, CalcStat.nthlargest(inputArray, 2));
      assertEquals(2.1, CalcStat.nthsmallest(inputArray, 2));

      // Test case: Array with duplicate values
      Object[] inputArrayWithDuplicates = new Object[] {5.0, 3.0, 5.0, 1.0};
      assertEquals(5.0, CalcStat.nthlargest(inputArrayWithDuplicates, 1));
      assertNull(CalcStat.nthlargest(inputArrayWithDuplicates, 4));
      assertEquals(1.0, CalcStat.nthsmallest(inputArrayWithDuplicates, 1));
      assertNull(CalcStat.nthsmallest(inputArrayWithDuplicates, 4));
   }

   @Test
   void testLargeSmall() {
      // Test case: Normal case
      Object[] inputArray = new Object[] {3.5, 1.2, 7.8, 2.1};
      assertLargeAndSmall(inputArray, new double[] {7.8, 3.5, 2.1, 1.2}, new double[] {1.2, 2.1, 3.5, 7.8});

      // Test case: Invalid k
      assertInvalidK(inputArray, 0, "K should be greater than zero");
      assertInvalidK(inputArray, 5, "K should be less than or equal to the number of data points");

      // Test case: Array with duplicate values
      Object[] inputArrayWithDuplicates = new Object[] {5.0, 3.0, 5.0, 1.0};
      assertLargeAndSmall(inputArrayWithDuplicates, new double[] {5.0, 5.0, 3.0, 1.0}, new double[] {1.0, 3.0, 5.0, 5.0});

      // Test case: Array with a single element
      Object[] singleElementArray = new Object[] {10.0};
      assertLargeAndSmall(singleElementArray, new double[] {10.0}, new double[] {10.0});
   }

   private void assertLargeAndSmall(Object[] array, double[] expectedLarge, double[] expectedSmall) {
      for (int k = 1; k <= expectedLarge.length; k++) {
         assertEquals(expectedLarge[k - 1], CalcStat.large(array, k));
         assertEquals(expectedSmall[k - 1], CalcStat.small(array, k));
      }
   }

   private void assertInvalidK(Object[] array, int k, String expectedMessage) {
      Exception exception = assertThrows(RuntimeException.class, () -> CalcStat.large(array, k));
      assertEquals(expectedMessage, exception.getMessage());

      exception = assertThrows(RuntimeException.class, () -> CalcStat.small(array, k));
      assertEquals(expectedMessage, exception.getMessage());
   }
}