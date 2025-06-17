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

import org.apache.commons.math3.linear.*;
import org.mozilla.javascript.*;

import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of all Mathematical functions for JavaScript
 *
 * @version 8.0, 6/30/2005
 * @author InetSoft Technology Corp
 */
public class CalcMath {
   /**
    * Get the absolute value of a number
    * @param number real number of which you want the absolute value
    * @return absolute value of a number
    */
   public static double abs(double number) {
      return Math.abs(number);
   }

   /**
    * Get the inverse cosine of a number
    * @param number cosine of the angle you want and must be from -1 to 1.
    * @return inverse cosine of a number
    */
   public static double acos(double number) {
      if(number < -1 || number > 1) {
         throw new RuntimeException("Number should be between -1 " +
                                    "and 1 (both inclusive)");
      }

      return Math.acos(number);
   }

   /**
    * Get the inverse hyperbolic cosine of a number
    * @param number any real number equal to or greater than 1
    * @return inverse hyperbolic cosine
    */
   public static double acosh(double number) {
      if(number < 1) {
         throw new RuntimeException("Number should at least be equal to 1");
      }

      return Math.log(number + Math.sqrt(number * number - 1));
   }

   /**
    * Get the inverse sine of a number
    * @param number sine of the angle you want and must be from -1 to 1.
    * @return inverse sine of a number
    */
   public static double asin(double number) {
      if(number < -1 || number > 1) {
         throw new RuntimeException("Number should be between -1 " +
                                    "and 1 (both inclusive)");
      }

      return Math.asin(number);
   }

   /**
    * Get the inverse hyperbolic sine of a number
    * @param number any real number
    * @return inverse hyperbolic sine
    */
   public static double asinh(double number) {
      return Math.log(number + Math.sqrt(number * number + 1));
   }

   /**
    * Get the inverse tangent of a number
    * @param number sine of the angle you want,
    * @return inverse tangent of a number
    */
   public static double atan(double number) {
      return Math.atan(number);
   }

   /**
    * Get the arctangent, or inverse tangent, of the specified
    * x- and y-coordinates.
    * @param x_num x-coordinate of the point
    * @param y_num y-coordinate of the point
    * @return arctangent, or inverse tangent, of the specified
    * x- and y-coordinates.
    */
   public static double atan2(double x_num, double y_num) {
      if(x_num == 0 && y_num == 0) {
         throw new RuntimeException("Divide by zero error, both arguments " +
                                    "cannot be zero");
      }

      return Math.atan2(y_num, x_num);
   }

   /**
    * Get the inverse hyperbolic tangent of a number
    * @param number any real number between 1 and -1
    * @return inverse hyperbolic tangent
    */
   public static double atanh(double number) {
      if(number <= -1 || number >= 1) {
         throw new RuntimeException("Number should between -1 and 1 (both " +
                                    "exclusive)");
      }

      return 0.5 * Math.log((1 + number) / (1 - number));
   }

   /**
    * Get the number rounded up, away from zero, to the nearest multiple
    * of significance.
    * @param number value you want to round
    * @param significance multiple to which you want to round
    * @return number rounded up, away from zero, to the nearest multiple
    * of significance.
    */
   public static double ceiling(double number, double significance) {
      if((number < 0 && significance > 0) ||
         (number > 0 && significance < 0)) {
         throw new RuntimeException("Number and Significance should have " +
                                    "same signs");
      }

      // ChrisS bug1403104829810 2014-6-19
      // Correct calculations of ceiling()
      return Math.ceil(number / significance) * significance;
   }

   /**
    * Get the number of combinations for a given number of items
    * @param number number of items
    * @param number_chosen number of items in each combination
    * @return number of combinations for a given number of items
    */
   public static double combin(double number, double number_chosen) {
      if(number < 0 || number_chosen < 0 || number < number_chosen) {
         throw new RuntimeException("number should be at least equal to the " +
                                    "number_chosen and both these variables " +
                                    "should hold non-negative values");
      }

      return CalcUtil.factorial((long) number) /
         (CalcUtil.factorial((long) number_chosen) *
          CalcUtil.factorial((long) number - (long) number_chosen));
   }

   /**
    * Get the cosine of a number
    * @param number any real number for which you want to find the cosine
    * @return cosine of a number
    */
   public static double cos(double number) {
      return Math.cos(number);
   }

   /**
    * Get the hyperbolic cosine of the given angle
    * @param number angle in radians for which you want the hyperbolic cosine
    * @return hyperbolic cosine of the given angle
    */
   public static double cosh(double number) {
      return (Math.exp(number) + Math.exp(-1 * number)) / 2;
   }

   /**
    * Converts radians into degrees
    * @param number angle in radians that you want to convert
    * @return angle in degrees
    */
   public static double degrees(double number) {
      return Math.toDegrees(number);
   }

   /**
    * Gets number rounded up to the nearest even integer
    * @param number value to round
    * @return number rounded up to the nearest even integer
    */
   // ChrisS bug1403185653133 2014-6-19
   // Make even() work like odd()
   public static int even(double number) {
      int sign = 1;

      if(number < 0) {
         sign = -1;
      }

      int intVal = (int) Math.ceil(Math.abs(number));

      if(intVal % 2 != 0) {
         intVal++;
      }

      return intVal * sign;
   }

   /**
    * Gets e raised to the power of number
    * @param number exponent applied to the base e
    * @return e raised to the power of number
    */
   public static double exp(double number) {
      return Math.exp(number);
   }

   /**
    * Get factorial of a number
    * @param number nonnegative number you want the factorial of
    * @return factorial of a number
    */
   public static double fact(double number) {
      if(number < 0) {
         throw new RuntimeException("Number should be non-negative");
      }

      return CalcUtil.factorial((int) number);
   }

   /**
    * Get double factorial of a number
    * @param number value for which to return the double factorial
    * @return double factorial of a number
    */
   public static double factdouble(double number) {
      if(number < 0) {
         throw new RuntimeException("Number should be non-negative");
      }

      return CalcUtil.factorialdouble((long) number);
   }

   /**
    * Get the number rounded down, towards zero, to the nearest multiple
    * of significance.
    * @param number value you want to round
    * @param significance multiple to which you want to round
    * @return number rounded down, towards zero, to the nearest multiple
    * of significance.
    */
   public static double floor(double number, double significance) {
      if((number < 0 && significance > 0) || (number > 0 && significance < 0)) {
         throw new RuntimeException("Number and Significance should have same signs");
      }

      if(significance == 0.0) {
         return 0;
      }

      double adjusted = number / significance;
      double factor = (significance > 0) ? Math.floor(adjusted) : Math.ceil(adjusted);
      return factor * significance;
   }

   /**
    * Returns the greatest common divisor of two or more integers
    * @param numbersObj array of values
    * @return gcd
    */
   public static int gcd(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      if(nos.length == 0) {
         throw new RuntimeException("Array should at least contain one number");
      }

      for(int i = 0; i < nos.length; i++) {
         if(nos[i] < 0) {
            throw new RuntimeException("All numbers should be non-negative");
         }
      }

      int gcd;

      if(nos.length == 1) {
         return (int) nos[0];
      }
      else if(nos.length == 2) {
         return CalcUtil.gcd((int) nos[0], (int) nos[1]);
      }
      else {
         gcd = CalcUtil.gcd((int) nos[0], (int) nos[1]);

         for(int i = 2; i < nos.length; i++) {
            gcd = CalcUtil.gcd(gcd, (int) nos[i]);
         }
      }

      return gcd;
   }

   /**
    * Rounds a number down to the nearest integer
    * @param number real number you want to round down to an integer
    * @return number rounded down to the nearest integer
    */
   public static int integer(double number) {
      number = Math.floor(number);
      return (int) number;
   }

   /**
    * Returns the least common multiple of two or more integers
    * @param numbersObj array of values
    * @return lcm
    */
   public static int lcm(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      if(nos.length == 0) {
         throw new RuntimeException("Array should at least contain one number");
      }
      // ChrisS bug1403120525095 2014-6-19
      // Validate input parameters are > 0
      for(int i = 0; i < nos.length; i++) {
         if(nos[i] < 0) {
            throw new RuntimeException("All parameters to lcm() must be "+
               "non-negative, parameter " + (i+1) +" value is " + nos[i]);
         }
      }

      int lcm;

      if(nos.length == 1) {
         return (int) nos[0];
      }
      else if(nos.length == 2) {
         return CalcUtil.lcm((int) nos[0], (int) nos[1]);
      }
      else {
         lcm = CalcUtil.lcm((int) nos[0], (int) nos[1]);

         for(int i = 2; i < nos.length; i++) {
            lcm = CalcUtil.lcm(lcm, (int) nos[i]);
         }
      }

      return lcm;
   }

   /**
    * Gets the natural logarithm of a number
    * @param number positive real number for which you want the natural logarithm
    * @return natural logarithm of a number
    */
   public static double ln(double number) {
      // ChrisS bug1403121510863 2014-6-24
      // Validate number is greater than zero.
      if(number <= 0) {
         throw new RuntimeException("Number should be greater than zero");
      }
      return Math.log(number);
   }

   /**
    * Gets the logarithm of a number to the base you specify
    * @param number positive real number for which you want the logarithm
    * @return logarithm of a number to the base you specify
    */
   public static double log(double number, double base) {
      // ChrisS bug1403121510863 2014-6-24
      // Validate number and base is greater than zero, and base is not one.
      if(number <= 0) {
         throw new RuntimeException("Number should be greater than zero");
      }
      if(base <= 0) {
         throw new RuntimeException("Base should be greater than zero");
      }
      if(base == 1) {
         throw new RuntimeException("Base cannot be one");
      }
      return Math.log(number) / Math.log(base);
   }

   /**
    * Gets the base-10 logarithm of a number
    * @param number positive real number for which you want the base-10 logarithm
    * @return base-10 logarithm of a number
    */
   public static double log10(double number) {
      // ChrisS bug1403121510863 2014-6-24
      // Validate number is greater than zero.
      if(number <= 0) {
         throw new RuntimeException("Number should be greater than zero");
      }
      return CalcMath.log(number, 10);
   }

   /**
    * Returns the matrix determinant of an array
    * @param numbersObj numeric array with an equal number of rows and columns
    * @return matrix determinant of an array
    */
   public static double mdeterm(Object numbersObj) {
      Object[] numbers = CalcUtil.split2D(numbersObj);
      double[][] arr = CalcUtil.convertToDoubleArray2D(numbers);

      if(arr.length == 0) {
         throw new RuntimeException("Array should be at least of order 1");
      }

      if(arr.length != arr[0].length) {
         throw new RuntimeException("Array should have identical numbers of " +
                                    "rows and columns");
      }

      if(arr.length > 73) {
         throw new RuntimeException("Maximum array odrer allowed is 73");
      }

      if(arr.length == 1) {
         return arr[0][0];
      }
      else {
         int determinant = 0;
         //For every first rowth element
         for(int k = 0; k < arr[0].length; k++) {
            //new array for recursive call
            Object[][] detarr = new Object[arr.length - 1][arr[0].length - 1];

            int row = 0;
            int col = 0;
            for(int i = 1; i < arr.length; i++) {
               for(int j = 0; j < arr[i].length; j++) {
                  //eliminate the first row and the jth column from the
                  //new matrix
                  if(j != k) {
                     detarr[row][col] = "" + arr[i][j];
                     col++;
                  }
               }

               row++;
               col = 0;
            }

            determinant += CalcUtil.intPow(-1, k) * arr[0][k] *
               CalcMath.mdeterm(detarr);
         }

         return determinant;
      }
   }

   /**
    * Returns the inverse of a matrix
    * @param numbersObj numeric array
    * @return matrix inverse
    */
   public static double[][] minverse(Object numbersObj) {
      Object[] numbers = CalcUtil.split2D(numbersObj);
      double[][] arr = CalcUtil.convertToDoubleArray2D(numbers);

      if(arr.length == 0) {
         throw new RuntimeException("Array is empty");
      }

      if(arr.length > 52 || arr[0].length > 52) {
         throw new RuntimeException("Array can have a maximum size of 52 X 52");
      }

      RealMatrix matrix = new Array2DRowRealMatrix(arr, false);
      matrix = new LUDecomposition(matrix).getSolver().getInverse();
      return matrix.getData();
   }

   /**
    * Gets the matrix product of two arrays
    * @param array1Obj first array
    * @param array2Obj second array
    * @return matrix product of two arrays
    */
   public static Object[] mmult(Object array1Obj, Object array2Obj) {
      Object[] array1 = CalcUtil.split2D(array1Obj);
      Object[] array2 = CalcUtil.split2D(array2Obj);
      double[][] matrix1 = CalcUtil.convertToDoubleArray2D(array1);
      double[][] matrix2 = CalcUtil.convertToDoubleArray2D(array2);

      if(matrix1.length == 0 || matrix2.length == 0) {
         return null;
      }

      if(matrix1[0].length != matrix2.length) {
         throw new RuntimeException("Number of columns in array1 must be " +
                                    "the same as the number of rows in array2");
      }

      RealMatrix m1 = new Array2DRowRealMatrix(matrix1, false);
      RealMatrix m2 = new Array2DRowRealMatrix(matrix2, false);
      RealMatrix m3 = m1.multiply(m2);
      return m3.getData();
   }

   /**
    * Gets the remainder after number is divided by divisor
    * @param number number for which you want to find the remainder
    * @param divisor number by which you want to divide number
    * @return remainder after number is divided by divisor
    */
   public static double mod(double number, double divisor) {
      if(divisor == 0.0) {
         throw new RuntimeException("/ by zero, divisor is zero !");
      }

      return number - divisor * integer(number / divisor);
   }

   /**
    * Gets the remainder after number is divided by divisor
    * @param number number for which you want to find the remainder
    * @return remainder after number is divided by divisor
    */
   public static double mround(double number, double multiple) {
      if((number < 0 && multiple > 0) ||
         (number > 0 && multiple < 0)) {
         throw new RuntimeException("Number and Significance should have " +
                                    "same signs");
      }

      double roundup = 0;

      if(Math.abs(number % multiple) >= Math.abs(multiple / 2)) {
         roundup = multiple;
      }

      return roundup + multiple * (int) (number / multiple);
   }

   /**
    * Gets the ratio of the factorial of a sum of values to the
    * product of factorials.
    * @param numbersObj values for which you want the multinomial
    * @return ratio of the factorial of a sum of values to the
    * product of factorials
    */
   public static double multinomial(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      numbers = JavaScriptEngine.split(numbers);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double sum = 0;
      double factorial = 1;

      for(double no : nos) {
         // ChrisS bug1403128014528 2014-6-19
         // Validate input parameters to multinomial().
         if(no < 0) {
            throw new RuntimeException(
               "MULTINOMIAL() requires inputs greater then one, but one of the values " +
               "entered is " + no);
         }

         sum += no;
         factorial *= CalcUtil.factorial((int) no);
      }

      return CalcUtil.factorial((int) sum) / factorial;
   }

   /**
    * Gets the number rounded up to the nearest odd integer
    * @param number value to round
    * @return number rounded up to the nearest odd integer
    */
   public static int odd(double number) {
      int sign = 1;

      if(number < 0) {
         sign = -1;
      }

      int intVal = (int) Math.ceil(Math.abs(number));

      if(intVal % 2 == 0) {
         intVal++;
      }

      return intVal * sign;
   }

   /**
    * Gets the number 3.14159265358979, the mathematical constant pi,
    * accurate to 15 digits
    * @return 3.14159265358979
    */
   public static double pi() {
      return 3.14159265358979;
   }

   /**
    * Gets the result of a number raised to a power
    * @param number base number
    * @param power exponent to which the base number is raised
    * @return result of a number raised to a power
    */
   public static double power(double number, double power) {
      return Math.pow(number, power);
   }

   /**
    * Multiplies all the numbers given as arguments and returns the product
    * product of factorials.
    * @param numbersObj numbers that you want to multiply
    * @return product off all numbers
    */
   public static double product(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);
      double product = 1;

      for(double no : nos) {
         product *= no;
      }

      return product;
   }

   /**
    * Gets the integer portion of a division
    * @param numerator dividend
    * @param denominator divisor
    * @return integer portion of a division
    */
   public static int quotient(double numerator, double denominator) {
      // ChrisS bug1403190564209 2014-6-19
      // Validate denominator is non-zero.
      if(denominator == 0) {
         throw new RuntimeException("Denominator cannot be zero");
      }

      return (int) (numerator / denominator);
   }

   /**
    * Converts degrees to radians
    * @param angle angle in degrees
    * @return angle in radians
    */
   public static double radians(double angle) {
      return Math.toRadians(angle);
   }

   /**
    * Gets the evenly distributed random real number greater than or equal
    * to 0 and less than 1
    * @return evenly distributed random real number greater than or equal
    * to 0 and less than 1
    */
   public static double rand() {
      return Math.random();
   }

   /**
    * Gets the random integer number between the numbers you specify
    * @param bottom smallest integer
    * @param top largest integer
    * @return evenly distributed random real number greater than or equal
    * to 0 and less than 1
    */
   public static int randbetween(int bottom, int top) {
      return (int) (rand() * (top - bottom) + bottom);
   }

   /**
    * Converts an arabic numeral to roman, as text.
    * @param number integer that needs to be converted
    * @return roman equaivalent
    */
   public static String roman(int number) {
      int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

      String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX",
                          "V", "IV", "I" };

      StringBuilder roman = new StringBuilder();

      int N = number;
      for (int i = 0; i < numbers.length; i++) {
         while (N >= numbers[i]) {
            roman.append(letters[i]);
            N -= numbers[i];
         }
      }

      return roman.toString();
   }

   /**
    * Rounds a number to a specified number of digits
    * @param number number you want to round
    * @param num_digits number of digits to which you want to round number
    * @return Rounded a number
    */
   public static double round(double number, int num_digits) {
      double multiplier = 1.0;

      if(num_digits < 0) {
         multiplier = 1.0 / CalcUtil.intPow(10, -1 * num_digits);
      }
      else if(num_digits > 0) {
         multiplier = CalcUtil.intPow(10, num_digits);
      }

      number *= multiplier;
      DecimalFormat df = new DecimalFormat("#");
      number = Double.valueOf(df.format(number));
      number /= multiplier;

      return number;
   }

   /**
    * Rounds a number to a specified number of digits, this function
    * implementation is not same as CalcMath.round, this function use
    * java.math.BigDecimal implementation, same as:
    * java.math.BigDecimal.setScale(num_digits, java.math.RoundingMode.DOWN)
    * @hidden
    */
   public static double mathRound(double number, int num_digits) {
      return java.math.BigDecimal.valueOf(number).setScale(num_digits,
         java.math.RoundingMode.DOWN).doubleValue();
   }

   /**
    * Rounds a number down, toward zero
    * @param number number you want to round
    * @param num_digits number of digits to which you want to round number
    * @return rounded number
    */
   public static double rounddown(double number, int num_digits) {
      double multiplier = 1.0;

      if(num_digits < 0) {
         multiplier = 1.0 / CalcUtil.intPow(10, -1 * num_digits);
      }
      else if(num_digits > 0) {
         multiplier = CalcUtil.intPow(10, num_digits);
      }

      number *= multiplier;

      if(number < 0) {
         number = Math.ceil(number);
      }
      else {
         number = Math.floor(number);
      }

      number /= multiplier;

      return number;
   }

   /**
    * Use BigDecimal to round down a number, same as:
    * java.math.BigDecimal.setScale(num_digits, java.math.RoundingMode.DOWN)
    * @hidden
    */
   public static double mathRoundDown(double number, int num_digits) {
      return java.math.BigDecimal.valueOf(number).setScale(num_digits,
         java.math.RoundingMode.DOWN).doubleValue();
   }

   /**
    * Rounds a number up, away from zero
    * @param number number you want to round
    * @param num_digits number of digits to which you want to round number
    * @return rounded number
    */
   public static double roundup(double number, int num_digits) {
      double multiplier = 1.0;

      if(num_digits < 0) {
         multiplier = 1.0 / CalcUtil.intPow(10, -1 * num_digits);
      }
      else if(num_digits > 0) {
         multiplier = CalcUtil.intPow(10, num_digits);
      }

      number *= multiplier;

      if(number < 0) {
         number = Math.floor(number);
      }
      else {
         number = Math.ceil(number);
      }

      number /= multiplier;

      return number;
   }

   /**
    * Use BigDecimal to round up a value, same as:
    * java.math.BigDecimal.setScale(num_digits, java.math.RoundingMode.UP)
    * @hidden
    */
   public static double mathRoundUp(double number, int num_digits) {
      return java.math.BigDecimal.valueOf(number).setScale(num_digits,
         java.math.RoundingMode.UP).doubleValue();
   }

   /**
    * Gets the sum of a power series
    * @param x input value to the power series
    * @param n initial power to which you want to raise x
    * @param m step by which to increase n for each term in the series
    * @param coefficientsObj set of coefficients by which each successive
    * power of x is multiplied
    * @return sum of a power series
    */
   public static double seriessum(double x, double n,
                                  double m, Object coefficientsObj) {
      Object[] coefficients = JavaScriptEngine.split(coefficientsObj);
      double[] coeffs = CalcUtil.convertToDoubleArray(coefficients);

      double seriessum = 0.0;

      for(int i = 0; i < coeffs.length; i++) {
         seriessum += coeffs[i] * Math.pow(x, n + i * m);
      }

      return seriessum;
   }

   /**
    * Determines the sign of a number. Returns 1 if the number is positive,
    * zero (0) if the number is 0, and -1 if the number is negative
    * @param number real number
    * @return sign of a number
    */
   public static int sign(double number) {
      if(number < 0) {
         return -1;
      }
      else if(number > 0) {
         return 1;
      }

      return 0;
   }

   /**
    * Get the sine of a number
    * @param number any real number for which you want to find the sine
    * @return sine of a number
    */
   public static double sin(double number) {
      return Math.sin(number);
   }

   /**
    * Get the hyperbolic sine of the given angle
    * @param number angle in radians for which you want the hyperbolic sine
    * @return hyperbolic sine of the given angle
    */
   public static double sinh(double number) {
      return (Math.exp(number) - Math.exp(-1 * number)) / 2;
   }

   /**
    * Get the positive square root
    * @param number number for which you want the square root
    * @return positive square root
    */
   public static double sqrt(double number) {
      if(number < 0) {
         throw new RuntimeException("Number should be positive");
      }

      return Math.sqrt(number);
   }

   /**
    * Get the square root of (number * pi)
    * @param number number by which pi is multiplied
    * @return square root of (number * pi)
    */
   public static double sqrtpi(double number) {
      if(number < 0) {
         throw new RuntimeException("Number should be positive");
      }

      return Math.sqrt(number * pi());
   }

   /**
    * Returns a subtotal in a list
    * @param function_num function to use in calculating subtotals within a list
    * @param refObj values to calculate the subtotal
    * @return subtotal
    */
   public static double subtotal(int function_num, Object refObj) {
      Object[] ref = JavaScriptEngine.split(refObj);
      double subtotal;

      switch(function_num) {
      case 1:
         subtotal = CalcStat.average(ref);
         break;
      case 2:
         subtotal = CalcStat.count(ref);
         break;
      case 3:
         subtotal = CalcStat.counta(ref);
         break;
      case 4:
         subtotal = CalcStat.max(ref);
         break;
      case 5:
         subtotal = CalcStat.min(ref);
         break;
      case 6:
         subtotal = CalcMath.product(ref);
         break;
      case 7:
         subtotal = CalcStat.stdev(ref);
         break;
      case 8:
         subtotal = CalcStat.stdevp(ref);
         break;
      case 9:
         subtotal = CalcMath.sum(ref);
         break;
      case 10:
         subtotal = CalcStat.varn(ref);
         break;
      case 11:
         subtotal = CalcStat.varp(ref);
         break;
      default:
         throw new RuntimeException("Function number should be an integer from 1 to 11");
      }

      return subtotal;
   }

   /**
    * Adds all the numbers given as arguments and returns the none
    * @param numbersObj numbers that you want to add
    * @return sum of all numbers
    */
   public static Object none(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);
      // use last, same as NonFormula
      return nos.length > 0 ? nos[nos.length - 1] : Double.NaN;
   }

   /**
    * Adds all the numbers given as arguments and returns the sum
    * @param numbersObj numbers that you want to add
    * @return sum of all numbers
    */
   public static double sum(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);
      double sum = 0;

      for(double no : nos) {
         sum += no;
      }

      return sum;
   }

   /**
    * Adds the cells specified by a given criteria
    * @param rangeObj array to be evaluated
    * @param criteria criteria in the form of a number, expression, or text
    * that defines which cells will be added
    * @param sum_rangeObj actual cells to sum
    * @return sum of qualifying numbers
    */
   public static double sumif(Object rangeObj, String criteria,
                              Object sum_rangeObj) {
      Object[] range = JavaScriptEngine.split(rangeObj);
      Object[] sum_range = JavaScriptEngine.split(sum_rangeObj);
      double[] rge = CalcUtil.convertToDoubleArray(range);
      double[] srge = CalcUtil.convertToDoubleArray(sum_range);

      String cond = "_value_" + criteria;
      Script condScript;
      Scriptable scope = new ScriptableObject() {
         @Override
         public String getClassName() {
            return "sumif";
         }
      };

      Object result;
      Context cx;

      try {
         cx = TimeoutContext.enter();
         condScript = cx.compileReader(scope, new StringReader(cond), "<condition>", 1, null);
      }
      catch(Exception e) {
         throw new RuntimeException("Invalid Search Criteria specified !", e);
      }

      double sum = 0;

      for(int i = 0; i < rge.length; i++) {
         try {
            scope.put("_value_", scope, rge[i]);
            TimeoutContext.startClock();
            result = condScript.exec(cx, scope);

            if("true".equals(result.toString())) {
               try {
                  if(srge.length > 0 && i < srge.length) {
                     sum += srge[i];
                  }
                  else if(srge.length == 0) {
                     sum += rge[i];
                  }
               }
               catch(Exception e) {
                  sum += rge[i];
               }
            }
         }
         catch(Exception ignore) {
         }
      }

      Context.exit();
      return sum;
   }

   /**
    * Multiplies corresponding components in the given arrays, and returns
    * the sum of those products
    * @param arraysObj array that holds other 2-D arrays
    * @return sum of products
    */
   public static double sumproduct(Object arraysObj) {
      Object[] arrays = CalcUtil.split2D(arraysObj);
      List<double[][]> holder = new ArrayList<>();

      for(int i = 0; i < arrays.length; i++) {
         holder.add(CalcUtil.convertToDoubleArray2D((Object[]) arrays[i]));
      }

      int rows = holder.get(0).length;
      int cols = holder.get(0)[0].length;

      double sum = 0;

      for(int i = 0; i < rows; i++) {
         for(int j = 0; j < cols; j++) {
            double prod = 1;

            try {
               for(double[][] row : holder) {
                  prod *= row[i][j];
               }
            }
            catch(ArrayIndexOutOfBoundsException ex) {
               throw new RuntimeException("Arrays should have same dimensions");
            }
            catch(Exception e) {
               throw new RuntimeException(e.getMessage());
            }

            sum += prod;
         }
      }

      return sum;
   }

   /**
    * Returns the sum of the squares of the arguments
    * @param numbersObj numbers whose squares are to be added
    * @return sum of the squares of the arguments
    */
   public static double sumsq(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double sumsq = 0;

      for(double no : nos) {
         sumsq += CalcUtil.intPow(no, 2);
      }

      return sumsq;
   }

   /**
    * Returns the sum of the difference of squares of corresponding values
    * in two arrays.
    * @param array_xObj first array or range of values
    * @param array_yObj second array or range of values
    * @return sum of the difference of squares of corresponding values
    * in two arrays.
    */
   public static double sumx2my2(Object array_xObj, Object array_yObj) {
      Object[] array_x = JavaScriptEngine.split(array_xObj);
      Object[] array_y = JavaScriptEngine.split(array_yObj);
      double[] x = CalcUtil.convertToDoubleArray(array_x);
      double[] y = CalcUtil.convertToDoubleArray(array_y);

      if(x.length != y.length) {
         throw new RuntimeException("array_x and array_y have a different " +
                                    "number of values");
      }

      double sumx2my2 = 0;

      for(int i = 0; i < x.length; i++) {
         sumx2my2 += CalcUtil.intPow(x[i], 2) - CalcUtil.intPow(y[i], 2);
      }

      return sumx2my2;
   }

   /**
    * Returns the sum of the sum of squares of corresponding values in two arrays
    * @param array_xObj first array or range of values
    * @param array_yObj second array or range of values
    * @return sum of the sum of squares of corresponding values in two arrays
    */
   public static double sumx2py2(Object array_xObj, Object array_yObj) {
      Object[] array_x = JavaScriptEngine.split(array_xObj);
      Object[] array_y = JavaScriptEngine.split(array_yObj);
      double[] x = CalcUtil.convertToDoubleArray(array_x);
      double[] y = CalcUtil.convertToDoubleArray(array_y);

      if(x.length != y.length) {
         throw new RuntimeException("array_x and array_y have a different " +
                                    "number of values");
      }

      double sumx2py2 = 0;

      for(int i = 0; i < x.length; i++) {
         sumx2py2 += CalcUtil.intPow(x[i], 2) + CalcUtil.intPow(y[i], 2);
      }

      return sumx2py2;
   }

   /**
    * Returns the sum of squares of differences of corresponding values in
    * two arrays
    * @param array_xObj first array or range of values
    * @param array_yObj second array or range of values
    * @return sum of squares of differences of corresponding values in
    * two arrays
    */
   public static double sumxmy2(Object array_xObj, Object array_yObj) {
      Object[] array_x = JavaScriptEngine.split(array_xObj);
      Object[] array_y = JavaScriptEngine.split(array_yObj);
      double[] x = CalcUtil.convertToDoubleArray(array_x);
      double[] y = CalcUtil.convertToDoubleArray(array_y);

      if(x.length != y.length) {
         throw new RuntimeException("array_x and array_y have a different " +
                                    "number of values");
      }

      double sumxmy2 = 0;

      for(int i = 0; i < x.length; i++) {
         sumxmy2 += CalcUtil.intPow(x[i] - y[i], 2);
      }

      return sumxmy2;
   }

   /**
    * Get the tangent of a number
    * @param number angle in radians for which you want the tangent
    * @return tangent of a number
    */
   public static double tan(double number) {
      return Math.tan(number);
   }

   /**
    * Get the hyperbolic tangent of the given angle
    * @param number angle in radians for which you want the hyperbolic tangent
    * @return hyperbolic tangent of the given angle
    */
   public static double tanh(double number) {
      return sinh(number) / cosh(number);
   }

   /**
    * Truncates a number to an integer by removing the fractional part
    * of the number
    * @param number number you want to truncate
    * @param num_digits number specifying the precision of the truncation
    * @return truncated number
    */
   public static double trunc(double number, int num_digits) {
      double multiplier = 1.0;

      if(num_digits < 0) {
         multiplier = 1.0 / CalcUtil.intPow(10, -1 * num_digits);
      }
      else if(num_digits > 0) {
         multiplier = CalcUtil.intPow(10, num_digits);
      }

      number *= multiplier;
      number = (int) number;
      number /= multiplier;

      return number;
   }

}
