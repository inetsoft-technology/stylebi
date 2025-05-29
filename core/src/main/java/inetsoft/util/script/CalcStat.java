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

import inetsoft.report.filter.*;
import inetsoft.util.Tool;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.mozilla.javascript.*;

import java.io.StringReader;
import java.util.*;

/**
 * Implementation of all Statistical functions for JavaScript
 *
 * @version 8.0, 7/12/2005
 * @author InetSoft Technology Corp
 */
public class CalcStat {
   /**
    * Returns the average of the absolute deviations of data points from
    * their mean
    * @param numbers arguments for which you want the average of the
    * absolute deviations
    * @return average of the absolute deviations of data points from their mean
    */
   public static double avedev(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double avedev = 0;

      for(int i = 0; i < nos.length; i++) {
         avedev += Math.abs(nos[i] - avg);
      }

      return avedev / nos.length;
   }

   /**
    * Returns the average (arithmetic mean) of the arguments
    * @param numbers arguments for which you want the average
    * @return average (arithmetic mean) of the arguments
    */
   public static double average(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      return avg(nos);
   }

   /**
    * Returns the average (arithmetic mean) of the arguments
    * considers non-numeric
    * @param numbers arguments for which you want the average
    * @return average (arithmetic mean) of the arguments considers non-numeric
    */
   public static double averagea(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers, false);

      return avg(nos);
   }

   /**
    * Internal function that calculates average
    */
   private static double avg(double[] nos) {
      double sum = 0;

      for(int i = 0; i < nos.length; i++) {
         sum += nos[i];
      }

      return sum / nos.length;
   }

   /**
    * Returns the individual term binomial distribution probability
    * @param number_s number of successes in trials
    * @param trials number of independent trials
    * @param probability_s probability of success on each trial
    * @return binomial distribution probability
    */
   public static double binomdist(double number_s, double trials,
                                  double probability_s, boolean cummulative) {
      number_s = Math.floor(number_s);
      trials = Math.floor(trials);

      if(probability_s < 0 || probability_s > 1) {
         throw new RuntimeException("Probability should be between 0 and 1");
      }

      if(number_s < 0 || number_s > trials) {
         throw new RuntimeException(
            "number_s should be between 0 and trials (both inclusive)");
      }

      double binomdist = 0;

      if(!cummulative) {
         return CalcMath.combin(trials, number_s) *
            Math.pow(probability_s, number_s) *
            Math.pow(probability_s, (trials - number_s));
      }
      else {
         for(int y = 0; y <= number_s; y++) {
            binomdist += CalcMath.combin(trials, y) *
               CalcUtil.intPow(probability_s, y) *
               Math.pow(probability_s, (trials - y));
         }
      }

      return binomdist;
   }

   /**
    * Returns the correlation coefficient
    * @param array1 first range of values
    * @param array2 second range of values
    * @return correlation coefficient
    */
   public static double correl(Object array1Obj, Object array2Obj) {
      Object[] array1 = JavaScriptEngine.split(array1Obj);
      Object[] array2 = JavaScriptEngine.split(array2Obj);
      double[] arr1 = CalcUtil.convertToDoubleArray(array1);
      double[] arr2 = CalcUtil.convertToDoubleArray(array2);

      if(arr1.length == 0 || arr2.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(arr1.length != arr2.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avg1 = 0;
      double avg2 = 0;

      for(int i = 0; i < arr1.length; i++) {
         avg1 += arr1[i];
         avg2 += arr2[i];
      }

      avg1 /= arr1.length;
      avg2 /= arr1.length;

      double term1 = 0;
      double term2 = 0;
      double term3 = 0;

      for(int i = 0; i < arr1.length; i++) {
         term1 += (arr1[i] - avg1) * (arr2[i] - avg2);
         term2 += CalcUtil.intPow((arr1[i] - avg1), 2);
         term3 += CalcUtil.intPow((arr2[i] - avg2), 2);
      }

      return term1 / Math.sqrt(term2 * term3);
   }

   /**
    * Counts the number of values in the cell. This function is different from
    * excel. All values, regardless of whether they are numerical, are counted.
    * @param array values
    * @return count
    */
   public static int count(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);

      int len = 0;

      for(Object val : array) {
         if(val != null) {
            len++;
         }
      }

      return len;
   }

   /**
    * Counts the number of distinct values in the cell.
    * @param array values
    * @return number of distinct values.
    */
   public static int countDistinct(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      Set set = new HashSet();

      for(int i = 0; i < array.length; i++) {
         Object val = array[i];

         if(val != null) {
            set.add(array[i]);
         }
      }

      return set.size();
   }

   /**
    * Counts the number of numeric values in the cell.
    * @param array values
    * @return count
    */
   public static int countn(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      return nos.length;
   }

   /**
    * Counts the number of values in the cell considers non-numeric
    * @param array values
    * @return count considers non-numeric
    */
   public static int counta(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array, false);

      // ChrisS bug1403899061216 2014-6-27
      // Compensate for any "empty" parameters in the list
      int notFoundCount = 0;
      for (int i=0; i<array.length; i++) {
         if(array[i] == org.mozilla.javascript.UniqueTag.NOT_FOUND) {
            notFoundCount++;
         }
      }

      return nos.length - notFoundCount;
   }

   /**
    * Counts the number of blanks
    * @param array values
    * @return count blanks
    */
   public static int countblank(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);

      int blanks = 0;

      for(int i = 0; i < array.length; i++) {
         if(array[i] == null ||
            (array[i] == null && "".equals(array[i].toString())))
         {
            blanks++;
         }
      }

      return blanks;
   }

   /**
    * Counts the number of cells within a range that meet the given criteria
    * @param range range of cells from which you want to count cells
    * @param criteria criteria in the form of a number, expression
    * @return count of qualifying cells
    */
   public static int countif(Object rangeObj, String criteria) {
      Object[] range = JavaScriptEngine.split(rangeObj);

      String cond = "_value_" + criteria;
      Script condScript = null;
      Scriptable scope = new ScriptableObject() {
         @Override
         public String getClassName() {
            return "countif";
         }
         };

      Object result = null;
      Context cx = null;

      try {
         cx = TimeoutContext.enter();
         condScript = cx.compileReader(scope, new StringReader(cond),
                                             "<condition>", 1, null);
      }
      catch(Exception e) {
         throw new RuntimeException("Invalid Search Criteria specified !");
      }

      int count = 0;

      for(int i = 0; i < range.length; i++) {
         if(cond != null) {
            try {
               scope.put("_value_", scope, range[i].toString());
               TimeoutContext.startClock();
               result = condScript.exec(cx, scope);

               if("true".equals(result.toString())) {
                  count++;
               }
            }
            catch(Exception ex) {
            }
         }
      }

      Context.exit();

      return count;
   }

   /**
    * Returns the weighted average of the values.
    * @param array1 range of values
    * @param array2 weight values.
    * @return weighted average
    */
   public static double weightedavg(Object array1Obj, Object array2Obj) {
      Object[] array1 = JavaScriptEngine.split(array1Obj);
      Object[] array2 = JavaScriptEngine.split(array2Obj);
      double[] arr1 = CalcUtil.convertToDoubleArray(array1);
      double[] arr2 = CalcUtil.convertToDoubleArray(array2);

      if(arr1.length == 0 || arr2.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(arr1.length != arr2.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double sum1 = 0;
      double sum2 = 0;

      for(int i = 0; i < arr1.length; i++) {
         sum1 += arr1[i] * arr2[i];
         sum2 += arr2[i];
      }

      return sum1 / sum2;
   }

   /**
    * Returns the first of the values.
    * @param array1 range of values
    * @param array2 group values.
    */
   public static Object first(Object array1Obj, Object array2Obj) {
      return firstLast(array1Obj, array2Obj, 1);
   }

   /**
    * Returns the first of the values.
    * @param array1 range of values
    * @param array2 group values.
    */
   public static Object last(Object array1Obj, Object array2Obj) {
      return firstLast(array1Obj, array2Obj, -1);
   }

   private static Object firstLast(Object array1Obj, Object array2Obj, int sign) {
      Object[] arr1 = JavaScriptEngine.split(array1Obj);
      Object[] arr2 = JavaScriptEngine.split(array2Obj);

      if(arr1.length == 0 || arr2.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(arr1.length != arr2.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      FirstLastCalc first = new FirstLastCalc(sign);

      for(int i = 0; i < arr1.length; i++) {
         first.addValue(new Object[] {arr1[i], arr2[i]});
      }

      return first.getResult();
   }

   private static class FirstLastCalc {
      public FirstLastCalc(int sign) {
         this.sign = sign;
      }

      public void addValue(Object v) {
         if(v == null || !(v instanceof Object[])) {
            return;
         }

         try {
            Object[] pair = (Object[]) v;

            if(pair[0] == null) {
               return;
            }

            Object dV = pair[1];
            Object mV = pair[0];
            int c = compare(dV, dval);

            // first time
            if(cnt == 0 || c <= 0) {
               if(c != 0) {
                  mvals.clear();
               }

               dval = dV;
               mvals.add(mV);
            }

            cnt++;
         }
         catch(NumberFormatException e) {
         }
      }

      public Object getResult() {
         if(mvals.size() <= 0) {
            return null;
         }

         double result = 0;

         for(Object v : mvals) {
            if(v == null) {
               continue;
            }

            if(v instanceof Number) {
               result += ((Number) v).doubleValue();
               continue;
            }

            // first? return first
            if(sign > 0) {
               return mvals.get(0);
            }
            // last? return last
            else {
               return mvals.get(mvals.size() - 1);
            }
         }

         return result;
      }

      private int compare(Object v1, Object v2) {
         return sign * Tool.compare(v1, v2);
      }

      private Object dval = null;
      private ArrayList mvals = new ArrayList();
      private int cnt = 0;
      private int sign = 1;
   }

   /**
    * Returns covariance, the average of the products of deviations for
    * each data point pair
    * @param array1 first range of values
    * @param array2 second range of values
    * @return covariance
    */
   public static double covar(Object array1Obj, Object array2Obj) {
      Object[] array1 = JavaScriptEngine.split(array1Obj);
      Object[] array2 = JavaScriptEngine.split(array2Obj);
      double[] arr1 = CalcUtil.convertToDoubleArray(array1);
      double[] arr2 = CalcUtil.convertToDoubleArray(array2);

      if(arr1.length == 0 || arr2.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(arr1.length != arr2.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avg1 = 0;
      double avg2 = 0;

      for(int i = 0; i < arr1.length; i++) {
         avg1 += arr1[i];
         avg2 += arr2[i];
      }

      avg1 /= arr1.length;
      avg2 /= arr1.length;

      double term1 = 0;

      for(int i = 0; i < arr1.length; i++) {
         term1 += (arr1[i] - avg1) * (arr2[i] - avg2);
      }

      return term1 / arr1.length;
   }

   /**
    * Returns the sum of squares of deviations of data points from their
    * sample mean
    * @param numbers arguments for which you want to calculate the sum of
    * squared deviations
    * @return sum of squares of deviations of data points from their
    * sample mean
    */
   public static double devsq(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double devsq = 0;

      for(int i = 0; i < nos.length; i++) {
         devsq += CalcUtil.intPow((nos[i] - avg), 2);
      }

      return devsq;
   }

   /**
    * Returns the exponential distribution
    * @param x value of the function
    * @param lambda parameter value
    * @param cumulative logical value that indicates which form of the
    * exponential function to provide. If cumulative is TRUE, EXPONDIST
    * returns the cumulative distribution function; if FALSE, it returns
    * the probability density function
    * @return exponential distribution
    */
   public static double expondist(double x, double lambda,
                                  boolean cumulative) {
      if(x < 0) {
         throw new RuntimeException("X should be at least equal to 0");
      }

      if(lambda <= 0) {
         throw new RuntimeException("Lambda should be greater than 0");
      }

      if(cumulative) {
         return 1 - Math.exp(-1 * lambda * x);
      }
      else {
         return lambda * Math.exp(-1 * lambda * x);
      }
   }

   /**
    * Returns the Fisher transformation at x
    * @param x numeric value for which you want the transformation
    * @return Fisher transformation at x
    */
   public static double fisher(double x) {
      if(x <= -1 || x >= 1) {
         throw new RuntimeException("X should be between -1 and 1");
      }

      return 0.5 * Math.log((1 + x) / (1 - x));
   }

   /**
    * Returns the inverse of the Fisher transformation
    * @param x value for which you want to perform the inverse of the
    * transformation
    * @return inverse of the Fisher transformation
    */
   public static double fisherinv(double y) {
      return (Math.exp(2 * y) - 1) / (Math.exp(2 * y) + 1);
   }

   /**
    * Calculates, or predicts, a future value by using existing values.
    * The predicted value is a y-value for a given x-value
    * @param x data point for which you want to predict a value
    * @param known_ys dependent array or range of data
    * @param known_xs independent array or range of data
    * @return future value by using existing values
    */
   public static double forecast(double x, Object known_ysObj,
                                 Object known_xsObj) {
      Object[] known_ys = JavaScriptEngine.split(known_ysObj);
      Object[] known_xs = JavaScriptEngine.split(known_xsObj);
      double[] ys = CalcUtil.convertToDoubleArray(known_ys);
      double[] xs = CalcUtil.convertToDoubleArray(known_xs);

      if(ys.length == 0 || xs.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(ys.length != xs.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avgy = 0;
      double avgx = 0;

      for(int i = 0; i < ys.length; i++) {
         avgy += ys[i];
         avgx += xs[i];
      }

      avgy /= ys.length;
      avgx /= ys.length;

      double b = 0;
      double a = 0;

      double term1 = 0;
      double term2 = 0;

      for(int i = 0; i < ys.length; i++) {
         term1 += (xs[i] - avgx) * (ys[i] - avgy);
         term2 += CalcUtil.intPow((xs[i] - avgx), 2);
      }

      b = term1 / term2;
      a = avgy - b * avgx;

      return a + b * x;
   }

   /**
    * Calculates how often values occur within a range of values,
    * and then returns a vertical array of numbers
    * @param data_array set of values for which you want to count frequencies
    * @param bins_array intervals into which you want to group the values in
    * data_array
    * @return frequency
    */
   public static int[] frequency(Object data_arrayObj, Object bins_arrayObj) {
      Object[] data_array = JavaScriptEngine.split(data_arrayObj);
      Object[] bins_array = JavaScriptEngine.split(bins_arrayObj);
      double[] data = CalcUtil.convertToDoubleArray(data_array);
      double[] bins = CalcUtil.convertToDoubleArray(bins_array);

      double[] sorted_bins = new double[bins.length];

      for(int i = 0; i < bins.length; i++) {
         sorted_bins[i] = bins[i];
      }

      Arrays.sort(data);
      Arrays.sort(sorted_bins);

      int[] frequency = new int[bins.length + 1];

      for(int i = 0; i < frequency.length; i++) {
         frequency[i] = Integer.MIN_VALUE;
      }

      int count = 0;
      double x = Double.NEGATIVE_INFINITY;
      double y = 0;
      for(int i = 0; i < sorted_bins.length; i++) {
         y = sorted_bins[i];

         for(int j = 0; j < data.length; j++) {
            if(data[j] > x && data[j] <= y) {
               count++;
            }
         }

         x = y;

         for(int k = 0; k < bins.length; k++) {
            if(frequency[k] == Integer.MIN_VALUE &&
               sorted_bins[i] == bins[k]) {
               frequency[k] = count;
               break;
            }
         }

         count = 0;

         if(i == (sorted_bins.length - 1)) {
            for(int p = 0; p < data.length; p++) {
               if(data[p] > sorted_bins[i]) {
                  count++;
               }
            }

            frequency[frequency.length - 1] = count;
         }
      }

      return frequency;
   }

   /**
    * Returns the geometric mean of an array or range of positive data
    * @param numbers arguments for which you want to calculate the mean
    * @return geometric mean of an array or range of positive data
    */
   public static double geomean(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double geomean = 1;

      for(int i = 0; i < nos.length; i++) {
         if(nos[i] <= 0) {
            throw new RuntimeException("Values should be greater than zero");
         }

         geomean *= nos[i];
      }

      return Math.pow(geomean, 1.0 / nos.length);
   }

   /**
    * Returns the harmonic mean of a data set.
    * The harmonic mean is the reciprocal of the arithmetic mean of reciprocals
    * @param numbers arguments for which you want to calculate the mean
    * @return harmonic mean of a data set.
    */
   public static double harmean(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double harmean = 0;

      for(int i = 0; i < nos.length; i++) {
         if(nos[i] <= 0) {
            throw new RuntimeException("Values should be greater than zero");
         }

         harmean += 1.0 / nos[i];
      }

      return 1.0 / (harmean / nos.length);
   }

   /**
    * Returns the hypergeometric distribution
    * @param sample_s number of successes in the sample
    * @param number_sample size of the sample
    * @param population_s number of successes in the population
    * @param number_population population size
    * @return hypergeometric distribution
    */
   public static double hypgeomdist(double sample_s, double number_sample,
                                    double population_s,
                                    double number_population) {
      return CalcMath.combin(population_s, sample_s) *
         CalcMath.combin((number_population - population_s),
                         (number_sample - sample_s)) /
         CalcMath.combin(number_population, number_sample);
   }

   /**
    * Calculates the point at which a line will intersect the y-axis by
    * using existing x-values and y-values
    * @param known_ys dependent array or range of data
    * @param known_xs independent array or range of data
    * @return point at which a line will intersect the y-axis by
    * using existing x-values and y-values
    */
   public static double intercept(Object known_ysObj, Object known_xsObj) {
      Object[] known_ys = JavaScriptEngine.split(known_ysObj);
      Object[] known_xs = JavaScriptEngine.split(known_xsObj);
      double[] ys = CalcUtil.convertToDoubleArray(known_ys);
      double[] xs = CalcUtil.convertToDoubleArray(known_xs);

      if(ys.length == 0 || xs.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(ys.length != xs.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avgy = 0;
      double avgx = 0;

      for(int i = 0; i < ys.length; i++) {
         avgy += ys[i];
         avgx += xs[i];
      }

      avgy /= ys.length;
      avgx /= ys.length;

      double term1 = 0;
      double term2 = 0;

      for(int i = 0; i < ys.length; i++) {
         term1 += (xs[i] - avgx) * (ys[i] - avgy);
         term2 += CalcUtil.intPow((xs[i] - avgx), 2);
      }

      return avgy - (term1 / term2) * avgx;
   }

   /**
    * Returns the kurtosis of a data set
    * @param numbers arguments for which you want to calculate kurtosis
    * @return kurtosis of a data set
    */
   public static double kurt(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      double avg = avg(nos);

      double term1 = 0;
      double stddev = sdv(nos);

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg) / stddev, 4);
      }

      int n = nos.length;

      return (((1.0 * n * (n + 1)) / ((n - 1) * (n - 2) * (n - 3))) *
              term1) - ((3 * CalcUtil.intPow((n - 1), 2))) / ((n - 2) * (n - 3));
   }

   /**
    * Returns the k-th largest value in a data set
    * @param array array for which you want to determine the k-th largest value.
    * @param k position
    * @return k-th largest value in a data set
    */
   public static double large(Object arrayObj, int k) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(k <= 0) {
         throw new RuntimeException("K should be greater than zero");
      }

      if(k > nos.length) {
         throw new RuntimeException("K should be less than or equal to the " +
                                    "number of data points");
      }

      Arrays.sort(nos);
      return nos[nos.length - k];
   }

   /**
    * Returns the largest value in a set of values
    * @param array array for which you want to determine the largest value.
    * @return largest value
    */
   public static double max(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);
      double mvalue = Double.MIN_VALUE;

      for(int i = 0; i < nos.length; i++) {
         mvalue = i == 0 ? nos[i] : Math.max(nos[i], mvalue);
      }

      return (mvalue == Double.MIN_VALUE) ? Double.NaN : mvalue;
   }

   /**
    * Returns the largest value in a set of values considers non-numeric
    * @param array array for which you want to determine the largest value.
    * @return largest value considers non-numeric
    */
   public static double maxa(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array, false);
      double mvalue = Double.MIN_VALUE;

      for(int i = 0; i < nos.length; i++) {
         mvalue = i == 0 ? nos[i] : Math.max(nos[i], mvalue);
      }

      return (mvalue == Double.MIN_VALUE) ? Double.NaN : mvalue;
   }

   /**
    * Returns the median of the given numbers
    * @param array numbers for which you want the median
    * @return median of the given numbers
    */
   public static double median(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      return med(nos);
   }

   private static double med(double[] nos) {
      if(nos.length == 0) {
         return Double.NaN;
      }

      Arrays.sort(nos);

      if(nos.length % 2 != 0) {
         return nos[nos.length / 2];
      }

      return (nos[nos.length / 2 - 1] + nos[nos.length / 2]) / 2;
   }

   /**
    * Returns the smallest value in a set of values
    * @param array array for which you want to determine the smallest value.
    * @return smallest value
    */
   public static double min(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);
      double mvalue = Double.MAX_VALUE;

      for(int i = 0; i < nos.length; i++) {
         mvalue = i == 0 ? nos[i] : Math.min(nos[i], mvalue);
      }

      return (mvalue == Double.MAX_VALUE) ? Double.NaN : mvalue;
   }

   /**
    * Returns the smallest value in a set of values considers non-numeric
    * @param array array for which you want to determine the smallest value.
    * @return smallest value considers non-numeric
    */
   public static double mina(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array, false);
      double mvalue = Double.MAX_VALUE;

      for(int i = 0; i < nos.length; i++) {
         mvalue = i == 0 ? nos[i] : Math.min(nos[i], mvalue);
      }

      return (mvalue == Double.MAX_VALUE) ? Double.NaN : mvalue;
   }

   /**
    * Returns the most frequently occurring, or repetitive
    * @param array array for which you want to determine the mode
    * @return most frequently occurring, or repetitive
    */
   public static double mode(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      Arrays.sort(nos);

      double mode = Double.NEGATIVE_INFINITY;
      double mode_count = Integer.MIN_VALUE;

      int count = 0;
      double curr_mode = 0;

      for(int i = 0; i < nos.length; i++) {
         if(curr_mode != nos[i]) {
            count = 1;
            curr_mode = nos[i];
         }
         else {
            count++;
         }

         if(count > mode_count) {
            mode_count = count;
            mode = curr_mode;
         }
      }

      return mode;
   }

   /**
    * Returns the negative binomial distribution
    * @param number_f number of failures
    * @param number_s threshold number of successes
    * @param probability_s probability of a success
    * @return negative binomial distribution
    */
   public static double negbinomdist(double number_f, double number_s,
                                     double probability_s) {
      number_f = Math.floor(number_f);
      number_s = Math.floor(number_s);

      if(probability_s < 0 || probability_s > 1) {
         throw new RuntimeException("Probability should be between 0 and 1");
      }

      if(number_f < 0 || number_s < 1) {
         throw new RuntimeException(
            "number_f should be at least equal to zero and number_s " +
            "should be greater than 1");
      }

      return CalcMath.combin((number_f + number_s - 1), (number_s - 1)) *
         Math.pow(probability_s, number_s) *
         Math.pow((1 - probability_s),  number_f);
   }

   public static Object nthlargest(Object arrayObj, int n) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      NthLargestFormula formula = new NthLargestFormula(n);

      for(int i = 0; i < nos.length; i++) {
         formula.addValue(nos[i]);
      }

      return formula.getResult();
   }

   public static Object nthsmallest(Object arrayObj, int n) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      NthSmallestFormula formula = new NthSmallestFormula(n);

      for(int i = 0; i < nos.length; i++) {
         formula.addValue(nos[i]);
      }

      return formula.getResult();
   }

   public static Object nthmostfrequent(Object arrayObj, int n) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      NthMostFrequentFormula formula = new NthMostFrequentFormula(n);

      for(int i = 0; i < nos.length; i++) {
         formula.addValue(nos[i]);
      }

      return formula.getResult();
   }

   /**
    * Returns the Pearson product moment correlation coefficient
    * @param array1 set of independent values
    * @param array2 set of dependent values
    * @return Pearson product moment correlation coefficient
    */
   public static double pearson(Object array1Obj, Object array2Obj) {
      Object[] array1 = JavaScriptEngine.split(array1Obj);
      Object[] array2 = JavaScriptEngine.split(array2Obj);
      double[] arr1 = CalcUtil.convertToDoubleArray(array1);
      double[] arr2 = CalcUtil.convertToDoubleArray(array2);

      if(arr1.length != arr2.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avg1 = 0;
      double avg2 = 0;

      for(int i = 0; i < arr1.length; i++) {
         avg1 += arr1[i];
         avg2 += arr2[i];
      }

      avg1 /= arr1.length;
      avg2 /= arr2.length;

      double term1 = 0;
      double term2 = 0;
      double term3 = 0;

      for(int i = 0; i < arr1.length; i++) {
         term1 += (arr1[i] - avg1) * (arr2[i] - avg2);
         term2 += CalcUtil.intPow((arr1[i] - avg1), 2);
         term3 += CalcUtil.intPow((arr2[i] - avg2), 2);
      }

      return term1 / Math.sqrt(term2 * term3);
   }

   /**
    * Returns the k-th percentile of values in a range
    * @param arrayObj data that defines relative standing
    * @param k percentile value in the range 0..1, inclusive
    * @return k-th percentile
    */
   public static double percentile(Object arrayObj, double k) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(nos.length == 0) {
         throw new RuntimeException("Array should not be empty");
      }

      if(nos.length > 8191) {
         throw new RuntimeException("Array should have less than 8191 " +
                                    "data points");
      }

      if(k <= 0 || k > 1) {
         throw new RuntimeException("K should be a value large than 0 and less than or equal to 1");
      }

      /*
      Arrays.sort(nos);

      double m = 1 + k * (nos.length - 1);

      if(m % 2 == 0) {
         return nos[((int) Math.floor(m)) - 1];
      }
      else {
         int index = ((int) Math.floor(m)) - 1;
         return nos[index] + (m % 1) * (nos[index + 1] - nos[index]);
      }
      */

      // @by yanie: fix #375, use standard Percentile to keep consistent
      // to chart target
      Percentile p = new Percentile();
      p.setData(nos);

      return p.evaluate(k * 100);
   }

   /**
    * Calculate the value at the specified percentile.
    */
   public static Object pthpercentile(Object arrayObj, int p) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      PthPercentileFormula formula = new PthPercentileFormula(p);

      for(int i = 0; i < nos.length; i++) {
         formula.addValue(nos[i]);
      }

      return formula.getResult();
   }

   /**
    * Returns the rank of a value in a data set as a percentage of the data set
    * @param arrayObj array of data with numeric values that defines relative
    * standing
    * @param x value for which you want to know the rank
    * @param significance x  identifies the number of significant digits for
    * the returned percentage value
    * @return rank of a value in a data set as a percentage of the data set
    */
   public static double percentrank(Object arrayObj, double x,
                                    double significance) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      // ChrisS bug1406125214890 2014-7-30
      // Handle the "significance" optional parameter being MIA
      if(Double.valueOf(significance).isNaN()) {
         significance = 3;
      }

      if(nos.length == 0) {
         throw new RuntimeException("Array of numbers cannot be empty");
      }

      if(significance < 1) {
         throw new RuntimeException("Significance should be at least equal " +
                                    "to one");
      }

      Arrays.sort(nos);

      double perrank = 0;

      if(Arrays.binarySearch(nos, x) >= 0) {
         perrank = perrank(nos, x);
      }
      else if(x > nos[0] && x < nos[nos.length - 1]) {
         double less = 0;
         double greater = 0;
         double prev = 0;
         for(int i = 0; i < nos.length; i++) {
            if(nos[i] > x && prev < x) {
               less = prev;
               greater = nos[i];
               break;
            }

            prev = nos[i];
         }

         perrank = perrank(nos, less) + (1.0 * (x - less)) /
            (greater - less) * (perrank(nos, greater) - perrank(nos, less));
      }
      else if(x > nos[nos.length - 1]) {
            perrank = 1.0;
      }

      return ((int) (Math.pow(10, significance) * perrank)) /
         Math.pow(10, significance);
   }

   /**
    * Internal function that calculates the percentrank
    */
   private static double perrank(double[] nos, double x) {
      int lessthan = 0;
      int greaterthan = 0;

      for(int i = 0; i < nos.length; i++) {
         if(nos[i] < x) {
            lessthan++;
         }
         else if(nos[i] > x) {
            greaterthan++;
         }
      }

      return (1.0 * lessthan) / (lessthan + greaterthan);
   }

   /**
    * Get the number of combinations for a given number of items
    * @param number number of items
    * @param number_chosen number of items in each combination
    * @return number of combinations for a given number of items
    */
   public static double permut(double number, double number_chosen) {
      if(number < 0 || number_chosen < 0 || number < number_chosen) {
         throw new RuntimeException("number should be at least equal to the " +
                                    "number_chosen and both these variables " +
                                    "should hold non-negative values");
      }

      return CalcUtil.factorial((long) number) /
         CalcUtil.factorial((long) number - (long) number_chosen);
   }

   /**
    * Returns the Poisson distribution
    * @param x number of events
    * @param mean expected numeric value
    * @param cumulative logical value that determines the form of the
    * probability distribution returned
    * @return Poisson distribution
    */
   public static double poisson(double x, double mean, boolean cumulative) {
      if(!cumulative) {
         return Math.exp(-1 * mean) * Math.pow(mean, x) /
            CalcUtil.factorial((long) x);
      }

      double poisson = 0;

      for(int i = 0; i <= x; i++) {
         poisson += Math.exp(-1 * mean) * CalcUtil.intPow(mean, i) /
            CalcUtil.factorial((long) i);
      }

      return poisson;
   }

   /**
    * Returns the probability that values in a range are between two limits
    * @param x_range range of numeric values of x with which there are
    * associated probabilities
    * @param prob_range set of probabilities associated with values in x_range
    * @param lower_limit lower bound on the value for which you want a
    * probability
    * @param upper_limit optional upper bound on the value for which you
    * want a probability
    * @return probability that values in a range are between two limits
    */
   public static double prob(Object x_rangeObj, Object prob_rangeObj,
                             double lower_limit, double upper_limit) {
      Object[] x_range = JavaScriptEngine.split(x_rangeObj);
      Object[] prob_range = JavaScriptEngine.split(prob_rangeObj);
      double[] x = CalcUtil.convertToDoubleArray(x_range);
      double[] prob = CalcUtil.convertToDoubleArray(prob_range);

      // ChrisS bug1406142978198 2014-7-30
      // Handle the "upper_limit" optional parameter being MIA
      if(Double.valueOf(upper_limit).isNaN()) {
         upper_limit = 0;
      }

      if(x.length != prob.length) {
         throw new RuntimeException("x_range and prob_range contain different " +
                                    "number of data points");
      }


      double probs = 0;

      for(int i = 0; i < prob.length; i++) {
         if(prob[i] <= 0 || prob[i] > 1) {
            throw new RuntimeException("Probability should be greater than 0 " +
                                       "and less than or equal to 1");
         }

         probs += prob[i];
      }

      if(probs > 1) {
         throw new RuntimeException("Sum of probabilities cannot be " +
                                    "greater than 1");
      }

      if(upper_limit == 0) {
         for(int i = 0; i < x.length; i++) {
            if(x[i] == lower_limit) {
               return prob[i];
            }
         }
      }
      else {
         double count = 0;

         for(int i = 0; i < x.length; i++) {
            if(x[i] >= lower_limit && x[i] <= upper_limit) {
               count += prob[i];
            }
         }

         return count;
      }

      return 0;
   }

   /**
    * Returns the quartile of a data set
    * @param array numeric values for which you want the quartile value
    * @param quart value to return
    * @return quartile of a data set
    */
   public static double quartile(Object arrayObj, double quart) {
      // @by ChrisS bug1406144758863 2014-7-30
      // Validate quartile bounds
      if(quart < 0) {
         throw new RuntimeException("quartile cannot be negative");
      }
      if(quart > 4) {
         throw new RuntimeException("quartile cannot be greater than 4");
      }

      Object[] array = JavaScriptEngine.split(arrayObj);
      quart = Math.floor(quart);

      if(quart == 0) {
         return min(array);
      }
      else if(quart == 2) {
         return median(array);
      }
      else if(quart == 4) {
         return max(array);
      }

//      double m = 0;
//      double multiplier = 1;
//
//      //Excel uses a different method for the 1st and 3rd quartile
//      //Regular text book method indicates 1st quartile = median of
//      //first half of the array and 3rd quartile is the median of the second half
//      if(quart == 1) {
//         multiplier = 0.25;
//         m = (nos.length + 3) / 4.0;
//      }
//      else if(quart == 3) {
//         multiplier = 0.75;
//         m = (3 * nos.length + 1) / 4.0;
//      }
//
//      if(m % 2 == 0) {
//         return nos[((int) Math.floor(m)) - 1];
//      }
//      else {
//         int index = ((int) Math.floor(m)) - 1;
//         return nos[index] + multiplier * (nos[index + 1] - nos[index]);
//      }

      array = JavaScriptEngine.split(array);
      // @by stone, fix bug1310769160877 use the same algorithm as Excel
      // to calculate the 1st and 3rd quartile
      if(quart == 1) {
         Tool.qsort(array, true);
      }
      // quart == 3
      else {
         Tool.qsort(array, false);
      }

      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(nos.length == 0) {
         return 0;
      }
      else if(nos.length == 1) {
         return nos[0];
      }

      int quotient = (nos.length - 1) / 4;
      int remainder = (nos.length - 1) % 4;

      return (nos[quotient] * (4 - remainder)) / 4 +
             (nos[quotient + 1] * remainder) / 4;
   }

   /**
    * Returns the rank of a number in a list of numbers
    * @param number number whose rank you want to find
    * @param ref array of a list of numbers
    * @param order number specifying how to rank number
    * If order is 0 (zero) or omitted, this function ranks number as if
    * ref were a list sorted in descending order.
    * If order is any nonzero value, this function ranks number as if ref
    * were a list sorted in ascending order.
    * @return rank of a number in a list of numbers
    */
   public static int rank(double number, Object refObj, double order) {
      Object[] ref = JavaScriptEngine.split(refObj);
      double[] nos = CalcUtil.convertToDoubleArray(ref);
      order = Math.floor(order);

      Arrays.sort(nos);

      if(order != 0) {
         order = 1;
      }

      int rank = 0;
      double val = Double.NEGATIVE_INFINITY;
      boolean found = false;

      if(order > 0) {
         for(int i = 0; i < nos.length; i++) {
            rank++;
            val = nos[i];

            if(number == nos[i]) {
               found = true;
               break;
            }
         }
      }
      else {

         for(int i = (nos.length - 1); i >= 0; i--) {
            rank++;
            val = nos[i];

            if(number == nos[i]) {
               found = true;
               break;
            }
         }
      }

      if(!found) {
         throw new RuntimeException("number not found in the ref list");
      }

      return rank;
   }

   /**
    * Returns the square of the Pearson product moment correlation coefficient
    * through data points in known_y's and known_x's
    * @param array1 array of data points
    * @param array2 array of data points
    * @return square of the Pearson product moment correlation coefficient
    */
   public static double rsq(Object array1Obj, Object array2Obj) {
      Object[] array1 = JavaScriptEngine.split(array1Obj);
      Object[] array2 = JavaScriptEngine.split(array2Obj);
      double rsq = CalcStat.pearson(array1, array2);
      return rsq * rsq;
   }

   /**
    * Returns the skewness of a distribution
    * @param array arguments for which you want to calculate skewness
    * @return skewness of a distribution
    */
   public static double skew(Object arrayObj) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(nos.length < 3) {
         throw new RuntimeException("DataSet should contain at least 3 points");
      }

      double stdev = sdv(nos);

      if(stdev == 0) {
         throw new RuntimeException("Standard Deviation of the dataset is zero");
      }

      double avg = avg(nos);

      double term1 = 0;

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg) / stdev, 3);
      }

      int n = nos.length;

      return (1.0 * n) / ((n - 1) * (n - 2)) * term1;
   }

   /**
    * Returns the slope of the linear regression line through data points
    * in known_y's and known_x's
    * @param known_ys array of numeric dependent data points
    * @param known_xs independent array of data points
    * @return slope of the linear regression line
    */
   public static double slope(Object known_ysObj, Object known_xsObj) {
      Object[] known_ys = JavaScriptEngine.split(known_ysObj);
      Object[] known_xs = JavaScriptEngine.split(known_xsObj);
      double[] ys = CalcUtil.convertToDoubleArray(known_ys);
      double[] xs = CalcUtil.convertToDoubleArray(known_xs);

      if(ys.length == 0 || xs.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(ys.length != xs.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      double avgy = 0;
      double avgx = 0;

      for(int i = 0; i < ys.length; i++) {
         avgy += ys[i];
         avgx += xs[i];
      }

      avgy /= ys.length;
      avgx /= ys.length;

      double term1 = 0;
      double term2 = 0;

      for(int i = 0; i < ys.length; i++) {
         term1 += (xs[i] - avgx) * (ys[i] - avgy);
         term2 += CalcUtil.intPow((xs[i] - avgx), 2);
      }

      return term1 / term2;
   }

   /**
    * Returns the k-th smallest value in a data set
    * @param array array for which you want to determine the k-th smallest value.
    * @param k position
    * @return k-th smallest value in a data set
    */
   public static double small(Object arrayObj, int k) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(k <= 0) {
         throw new RuntimeException("K should be greater than zero");
      }

      if(k > nos.length) {
         throw new RuntimeException("K should be less than or equal to the " +
                                    "number of data points");
      }

      Arrays.sort(nos);

      return nos[k - 1];
   }

   /**
    * Returns a normalized value from a distribution characterized by mean
    * and standard_dev
    * @param x value you want to normalize
    * @param mean arithmetic mean of the distribution
    * @param standard_dev standard deviation of the distribution
    * @return normalized value from a distribution characterized by mean
    * and standard_dev
    */
   public static double standardize(double x, double mean, double standard_dev) {
      return (x - mean) / standard_dev;
   }

   /**
    * Estimates standard deviation based on a sample
    * @param numbers number arguments corresponding to a sample of a population
    * @return standard deviation
    */
   public static double stdev(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      return sdv(nos);
   }

   /**
    * Estimates standard deviation based on a sample considers non-numeric
    * @param numbers number arguments corresponding to a sample of a population
    * @return standard deviation considers non-numeric
    */
   public static double stdeva(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers, false);

      return sdv(nos);
   }

   /**
    * Internal function that calculates Standard Deviation
    */
   private static double sdv(double[] nos) {
      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double term1 = 0;

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg), 2);
      }

      return Math.sqrt(term1 / (nos.length - 1));
   }

   /**
    * Calculates standard deviation based on the entire population given
    * as arguments
    * @param numbers number arguments corresponding to a sample of a population
    * @return standard deviation based on the entire population
    */
   public static double stdevp(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      return sdvp(nos);
   }

   /**
    * Calculates standard deviation based on the entire population given
    * as arguments considers non-numeric
    * @param numbers number arguments corresponding to a sample of a population
    * @return standard deviation based on the entire population
    * considers non-numeric
    */
   public static double stdevpa(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers, false);

      return sdvp(nos);
   }

   /**
    * Internal function that calculates standard deviation based on the
    * entire population
    */
   private static double sdvp(double[] nos) {
      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double term1 = 0;

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg), 2);
      }

      return Math.sqrt(term1 / nos.length);
   }

   /**
    * Returns the standard error of the predicted y-value for each x in
    * the regression
    * @param known_ys dependent data points
    * @param known_xs independent data points
    * @return standard error
    */
   public static double steyx(Object known_ysObj, Object known_xsObj) {
      Object[] known_ys = JavaScriptEngine.split(known_ysObj);
      Object[] known_xs = JavaScriptEngine.split(known_xsObj);
      double[] ys = CalcUtil.convertToDoubleArray(known_ys);
      double[] xs = CalcUtil.convertToDoubleArray(known_xs);

      if(ys.length == 0 || xs.length == 0) {
         throw new RuntimeException("Arrays cannot be empty, / by zero");
      }

      if(ys.length != xs.length) {
         throw new RuntimeException("Arrays should have same number of values");
      }

      if(ys.length < 3) {
         throw new RuntimeException("Arrays should have at least 3 data " +
                                    "points, / by zero");
      }

      double avgy = 0;
      double avgx = 0;

      for(int i = 0; i < ys.length; i++) {
         avgy += ys[i];
         avgx += xs[i];
      }

      avgy /= ys.length;
      avgx /= ys.length;

      double term1 = 0;
      double term2 = 0;
      double term3 = 0;

      for(int i = 0; i < ys.length; i++) {
         term1 += CalcUtil.intPow((ys[i] - avgy), 2);
         term2 += (xs[i] - avgx) * (ys[i] - avgy);
         term3 += CalcUtil.intPow((xs[i] - avgx), 2);
      }

      return Math.sqrt((term1 - (CalcUtil.intPow(term2, 2) / term3)) / (ys.length - 2));
   }

   /**
    * Returns the mean of the interior of a data set
    * @param array array of values to trim and average
    * @param percent fractional number of data points to exclude from the
    * calculation
    * @return mean of the interior of a data set
    */
   public static double trimmean(Object arrayObj, double percent) {
      Object[] array = JavaScriptEngine.split(arrayObj);
      double[] nos = CalcUtil.convertToDoubleArray(array);

      if(percent < 0 || percent > 1) {
         throw new RuntimeException("Percentage should be between 0 and 1");
      }

      Arrays.sort(nos);

      int exclude_each_end = (int) (nos.length * percent / 2);

      double sum = 0;

      for(int i = exclude_each_end; i < (nos.length - exclude_each_end); i++) {
         sum += nos[i];
      }

      return sum / (nos.length - (2.0 * exclude_each_end));
   }

   /**
    * Estimates variance based on a sample
    * @param numbers number arguments corresponding to a sample of a population
    * @return variance
    */
   public static double varn(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      return vr(nos);
   }

   /**
    * Estimates variance based on a sample considers non-numeric
    * @param numbers number arguments corresponding to a sample of a population
    * @return variance considers non-numeric
    */
   public static double vara(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers, false);

      return vr(nos);
   }

   /**
    * Internal function that calculates variance
    */
   private static double vr(double[] nos) {
      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double term1 = 0;

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg), 2);
      }

      return term1 / (nos.length - 1);
   }

   /**
    * Calculates variance based on the entire population
    * @param numbers number arguments corresponding to a sample of a population
    * @return variance based on the entire population
    */
   public static double varp(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers);

      return vrp(nos);
   }

   /**
    * Calculates variance based on the entire population considers non-numeric
    * @param numbers number arguments corresponding to a sample of a population
    * @return variance based on the entire population considers non-numeric
    */
   public static double varpa(Object numbersObj) {
      Object[] numbers = JavaScriptEngine.split(numbersObj);
      double[] nos = CalcUtil.convertToDoubleArray(numbers, false);

      return vrp(nos);
   }

   /**
    * Internal function that calculates variance based on the entire population
    */
   private static double vrp(double[] nos) {
      double avg = 0;

      for(int i = 0; i < nos.length; i++) {
         avg += nos[i];
      }

      avg /= nos.length;

      double term1 = 0;

      for(int i = 0; i < nos.length; i++) {
         term1 += CalcUtil.intPow((nos[i] - avg), 2);
      }

      return term1 / nos.length;
   }

   public static double weibull(double x, double alpha,
                                double beta, boolean cumulative) {
      if(x < 0) {
         throw new RuntimeException("X should at least be equal to 0");
      }

      if(alpha <= 0) {
         throw new RuntimeException("Alpha should be greater than 0");
      }

      if(beta <= 0) {
         throw new RuntimeException("Beta should be greater than 0");
      }

      if(cumulative) {
         return 1 - Math.exp(-1 * Math.pow((x / beta), alpha));
      }
      else {
         return alpha / Math.pow(beta, alpha) * Math.pow(x, alpha - 1) *
            Math.exp(-1 * Math.pow((x / beta), alpha));
      }
   }
}
