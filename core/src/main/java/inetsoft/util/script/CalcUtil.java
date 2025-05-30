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

import inetsoft.util.CoreTool;
import org.mozilla.javascript.NativeArray;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Utility functions used in Calc classes.
 *
 * @version 8.0, 6/30/2005
 * @author InetSoft Technology Corp
 */
public class CalcUtil {
   /**
    * Utility method to get the Calendar instance
    */
   public static Calendar getCalendar() {
      return Calendar.getInstance();
   }

   /**
    * Utility method to get Excel start Date
    * The reference value is the exact moment before January 1, 1900
    * so that when getting serial days, it properly adds an additional day
    * the moment it becomes a new day, time=00:00:00.
    */
   public static Calendar getSerialStart() {
      if(date1900 == null) {
         Calendar cal = getCalendar();
         cal.set(Calendar.AM_PM, Calendar.PM);
         cal.set(Calendar.HOUR, 11);
         cal.set(Calendar.MINUTE, 59);
         cal.set(Calendar.SECOND, 59);
         cal.set(Calendar.DATE, 31);
         cal.set(Calendar.MONTH, Calendar.DECEMBER);
         cal.set(Calendar.YEAR, 1899);
         // ChrisS bug1403765636783 2014-6-26
         // fix edge case of CALC.datevalue(new Date(1900,0,1)) ;
         cal.set(Calendar.MILLISECOND, 999);

         date1900 = cal;
      }

      return date1900;
   }

   /**
    * Utility method to get number of days between two dates
    */
   public static int getSerialDays(Date start_date, Date end_date) {
      LocalDate d1;
      LocalDate d2;

      if(start_date instanceof java.sql.Date) {
         d1 = ((java.sql.Date) start_date).toLocalDate();
      }
      else {
         d1 = start_date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      }

      if(end_date instanceof java.sql.Date) {
         d2 = ((java.sql.Date) end_date).toLocalDate();
      }
      else {
         d2 = end_date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      }

      return (int) ChronoUnit.DAYS.between(d1, d2);
   }

   /**
    * Convert a start end date pair to US NASD format
    */
   public static void convertToUsNasd(Calendar start, Calendar end) {
      //U.S. (NASD) method.
      //If the starting date is the 31st of a month, it becomes
      //equal to the 30th of the same month. If the ending date is
      //the 31st of a month and the starting date is earlier than
      //the 30th of a month, the ending date becomes equal to the
      //1st of the next month; otherwise the ending date becomes equal
      //to the 30th of the same month.
      if(start.get(Calendar.DATE) == 31) {
         start.set(Calendar.DATE, 30);
      }

      if((end.get(Calendar.DATE) == 31) &&
         (start.get(Calendar.DATE) < 30)) {
         end.set(Calendar.MONTH, end.get(Calendar.MONTH) + 1);
         end.set(Calendar.DATE, 1);
      }
      else if(end.get(Calendar.DATE) == 31) {
         end.set(Calendar.DATE, 30);
      }
   }

   /**
    * Wrapper function to accept date instead of Calendar
    */
   public static void convertToUsNasd(Date start_date, Date end_date) {
      Calendar start = getCalendar();
      start.setTime(start_date);

      Calendar end = getCalendar();
      end.setTime(end_date);

      convertToUsNasd(start, end);
   }

   /**
    * Convert a start end date pair to European format
    */
   public static void convertToEuropean(Calendar start, Calendar end) {
      //European method.
      //Starting dates and ending dates that occur on the 31st of
      //a month become equal to the 30th of the same month.
      if(start.get(Calendar.DATE) == 31) {
         start.set(Calendar.DATE, 30);
      }

      if(end.get(Calendar.DATE) == 31) {
         end.set(Calendar.DATE, 30);
      }
   }

   /**
    * Wrapper function to accept date instead of Calendar
    */
   public static void convertToEuropean(Date start_date, Date end_date) {
      Calendar start = getCalendar();
      start.setTime(start_date);

      Calendar end = getCalendar();
      end.setTime(end_date);

      convertToEuropean(start, end);
   }

   /**
    * Get number of days as per 30 days in one month and 360 days in a year
    * calendar
    */
   public static int get30_360Days(Calendar start, Calendar end) {
      int endYear = end.get(Calendar.YEAR);
      int endMonth = end.get(Calendar.MONTH);
      int endDay = end.get(Calendar.DAY_OF_MONTH);
      int startYear = start.get(Calendar.YEAR);
      int startMonth = start.get(Calendar.MONTH);
      int startDay = start.get(Calendar.DAY_OF_MONTH);
      int days = 0;

      if(startYear != endYear || startMonth != endMonth || startDay != endDay) {
         // year and month with 30 days per month
         days += ((endYear - startYear) * 12 + (endMonth - startMonth)) * 30;
         // day of month difference
         days += endDay - startDay;
      }

      return days;
   }

   /**
    * Wrapper function to the one above which accepts Calendar objects
    */
   public static int get30_360Days(Date start_date, Date end_date) {
      Calendar start = getCalendar();
      start.setTime(start_date);

      Calendar end = getCalendar();
      end.setTime(end_date);

      return get30_360Days(start, end);
   }

   /**
    * Calculate the fraction of year based on the basis count type value
    */
   public static double calculateBasisDayCountFraction(Date start_date,
						       Date end_date,
						       int basis)
   {
      Calendar start = getCalendar();
      start.setTime(start_date);

      Calendar end = getCalendar();
      end.setTime(end_date);

      int days = 0;
      Float divisor = Float.valueOf(0);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime(start.getTime());

      if(basis == US_NASD_30_360) {
         CalcUtil.convertToUsNasd(start, end);
         days = CalcUtil.get30_360Days(start, end);
         divisor = Float.valueOf(360);
      }
      else if(basis == ACTUAL_ACTUAL) {
         days = CalcUtil.getSerialDays(start.getTime(), end.getTime());

         // @by ChrisS bug1401736146952 2014-6-9
         // The divisor is the number of days in the year, combining leap-year
         // with non-leap-year using a day-weighted averaging.

         // Set up the accumulators, and the range bounds, for the calculation.
         int leapYearDays = 0;
         int yearDays = 0;
         GregorianCalendar startCal = new GregorianCalendar();
         GregorianCalendar endCal = new GregorianCalendar();

         if(start_date.before(end_date)) {
            startCal.setTime(start_date);
            endCal.setTime(end_date);
         }
         else {
            startCal.setTime(end_date);
            endCal.setTime(start_date);
         }
         GregorianCalendar testCal = (GregorianCalendar)startCal.clone();

         // Examine each year within the date range for leap year.
         do {
            // For each year within the date range, determine how many days are
            // within the date range.
            int daysWithinTestYear;

            // If year being tested is the last year of the range, then
            // calculate the days within the date range simply as end-start.
            if(testCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) {
               daysWithinTestYear = CalcUtil.getSerialDays(testCal.getTime(),
                  endCal.getTime());
            }
            // If year being tested is the first year of the range [but is not
            // the last year of the range] then calculate the days within the
            // date range as Dec31-start.
            else if(testCal.get(Calendar.YEAR) == startCal.get(Calendar.YEAR)) {
               testCal.set(Calendar.MONTH, Calendar.DECEMBER);
               testCal.set(Calendar.DAY_OF_MONTH, 31);
               daysWithinTestYear = CalcUtil.getSerialDays(startCal.getTime(),
                  testCal.getTime())+1;
            }
            // Year being tested is completely within the range, so the days within
            // the date range is the whole year.
            else {
               if(testCal.isLeapYear(testCal.get(Calendar.YEAR))) {
                  daysWithinTestYear = 366;
               }
               else {
                  daysWithinTestYear = 365;
               }
            }

            // Add the "days within range" (for year being tested)
            // to the leapYearDays or yearDays accumulators.
            if(testCal.isLeapYear(testCal.get(Calendar.YEAR))) {
               leapYearDays += daysWithinTestYear;
            }
            else {
               yearDays += daysWithinTestYear;
            }

            // Set the next year to be tested to Jan1 of "year+1", and loop.
            testCal.set(Calendar.MONTH, Calendar.JANUARY);
            testCal.set(Calendar.DAY_OF_MONTH, 1);
            testCal.add(Calendar.YEAR, 1);
         }
         while(testCal.before(endCal));

         // Finally, calculate the divisor as 365 + the "day weighted average"
         // of leap-year days within the range divided by total days within the range.
         divisor = 365 + (float) leapYearDays / (float) (yearDays + leapYearDays);
      }
      else if(basis == ACTUAL_360) {
         days = CalcUtil.getSerialDays(start.getTime(), end.getTime());
         divisor = Float.valueOf(360);
      }
      else if(basis == ACTUAL_365) {
         days = CalcUtil.getSerialDays(start.getTime(), end.getTime());
         divisor = Float.valueOf(365);
      }
      else if(basis == EUROPEAN_30_360) {
         CalcUtil.convertToEuropean(start, end);
         days = CalcUtil.get30_360Days(start, end);
         divisor = Float.valueOf(360);
      }

      return ((float) days) / divisor;

   }

   /**
    * Calculate the number of days between two dates depending on the day
    * count basis
    */
   public static int getDayCountBasisDays(Date start_date,
                                          Date end_date,
                                          int basis)
   {
      if(start_date.after(end_date)) {
         throw new RuntimeException("Start Date falls after the End Date");
      }

      int days = 0;

      if(basis == US_NASD_30_360) {
         CalcUtil.convertToUsNasd(start_date, end_date);
         days = CalcUtil.get30_360Days(start_date, end_date);
      }
      else if(basis == ACTUAL_ACTUAL) {
         days = CalcUtil.getSerialDays(start_date, end_date);
      }
      else if(basis == ACTUAL_360) {
         days = CalcUtil.getSerialDays(start_date, end_date);
      }
      else if(basis == ACTUAL_365) {
         days = CalcUtil.getSerialDays(start_date, end_date);
      }
      else if(basis == EUROPEAN_30_360) {
         CalcUtil.convertToEuropean(start_date, end_date);
         days = CalcUtil.get30_360Days(start_date, end_date);
      }

      return days;
   }

   /**
    * Calculate the number of days in a year depending on the day
    * count basis
    */
   public static int getDayCountBasisYear(int year, int basis) {
      int days = 0;

      if(basis == US_NASD_30_360) {
         days = 360;
      }
      else if(basis == ACTUAL_ACTUAL) {
         if(CoreTool.calendar.get().isLeapYear(year)) {
            days = 366;
         }
         else {
            days = 365;
         }
      }
      else if(basis == ACTUAL_360) {
         days = 360;
      }
      else if(basis == ACTUAL_365) {
         days = 365;
      }
      else if(basis == EUROPEAN_30_360) {
         days = 360;
      }

      return days;
   }

   /**
    * Converts an Object array to double array by ignoring non-numeric entries
    */
   public static double[] convertToDoubleArray(Object[] array) {
      return convertToDoubleArray(array, true);
   }

   /**
    * Converts an Object array to double array
    * @param array array of elements
    * @param ignore true to ignore non-numeric false otherwise
    * Text  = 0
    * Boolean true = 1; false = 0
    */
   public static double[] convertToDoubleArray(Object[] array, boolean ignore) {
      if(array == null) {
         return new double[0];
      }

      List<Double> holder = new ArrayList<>();

      for(int i = 0; i < array.length; i++) {
         try {
            if(array[i] == null) {
               continue;
            }

            Double elem = array[i] instanceof Number
               ? Double.valueOf(((Number) array[i]).doubleValue())
               : Double.valueOf(array[i].toString());

            holder.add(elem);
         }
         catch(Exception e) {
            if(!ignore) {
               try {
                  boolean val = Boolean.parseBoolean(array[i].toString());

                  if(val) {
                     holder.add(1.0);
                  }
                  else {
                     holder.add(0.0);
                  }
               }
               catch(Exception ex) {
               }
            }
         }
      }

      return holder.stream().mapToDouble(d -> d).toArray();
   }

   /**
    * Converts an Object array to 2D double array
    */
   public static double[][] convertToDoubleArray2D(Object[] array) {
      if(array == null) {
         return new double[0][0];
      }

      // If just an vanilla array, force it to be a 2-D array containing 1 row
      if(array.length > 0 && !(array[0] instanceof Object[])) {
         double[][] arr = new double[1][array.length];

         for(int i = 0; i < array.length; i++) {
            arr[0][i] = Double.parseDouble(array[i].toString());
         }

         return arr;
      }

      ArrayList<double[]> holder = new ArrayList<>();
      int arrLength = 0;

      for(int i = 0; i < array.length; i++) {
         double[] arr = convertToDoubleArray((Object[]) array[i]);
         arrLength = arr.length;
         holder.add(arr);
      }

      double[][] doubleArr = new double[array.length][arrLength];

      for(int i = 0; i < holder.size(); i++) {
         double[] arr = holder.get(i);

         for(int j = 0; j < arr.length; j++) {
            doubleArr[i][j] = arr[j];
         }
      }

      return doubleArr;
   }

   /**
    * Calculate the factorial of n.
    * @param n the number to calculate the factorial of.
    * @return n! - the factorial of n.
    */
   public static double factorial(long n) {
      BigInteger fact = BigInteger.ONE;

      for (int i = 1; i <= n; i++) {
         fact = fact.multiply(BigInteger.valueOf(i));
      }

      return fact.doubleValue();
   }

   /**
    * Calculate the double factorial of n.
    * If number is even:
    * n! = n(n-2)(n-4)...(4)(2)
    * If number is odd:
    * n! = n(n-2)(n-4)...(3)(1)
    * @param n the number to calculate the factorial of.
    * @return the double factorial of n.
    */
   public static long factorialdouble(long n) {
      // Base Case:
      // If n == 0 then n! = 1
      if(n == 0) {
         return 1;
      }
      // If n == 1 or n ==2 then n! = n.
      else if(n == 1 || n == 2) {
         return n;
      }
      // Recursive Case:
      // If n > 1 then n! = n * (n-2)!
      else {
         return n * factorialdouble(n-2);
      }
   }

   /**
    * Calculate the GCD of two numbers
    * @param m first number
    * @param n second number
    * @return GCD
    */
   public static int gcd(int m, int n) {
      if(m == 0) {
         return n;
      }
      else if(n == 0) {
         return m;
      }


      if(m < n) {
         int t = m;
         m = n;
         n = t;
      }

      int r = m % n;

      if(r == 0) {
         return n;
      }
      else {
         return gcd(n, r);
      }
   }

   /**
    * Calculate the LCM of two numbers
    * @param m first number
    * @param n second number
    * @return LCM
    */
   public static int lcm(int m, int n) {
      return (m * n) / CalcUtil.gcd(m , n);
   }


   /**
    * Raise a double to an integer power
    * Faster than Math.pow(double, double) with integer exponents
    * @param base
    * @param exp
    * @return base raised to the power of exp
    */
   static double intPow(double base, int exp) {
      // If exponent is negative, return 1 / b^e
      if(exp < 0) {
         return 1.0 / intPow(base, -exp);
      }

      double result = 1.0;

      // multiply b by itself e times
      for(int i = 0; i < exp; i++) {
         result *= base;
      }

      return result;
   }

   /**
    * Split into 2 dimensional array.
    */
   public static Object[] split2D(Object obj) {
      // [[],[]] Array of 1-D arrays
      // [[[],[]], [[],[]]] Array of 2-D arrays
      // Hence we split upto the third depth
      Object[] arr = JavaScriptEngine.split(obj);

      for(int i = 0; i < arr.length; i++) {
         if(arr[i] instanceof NativeArray) {
            arr[i] = JavaScriptEngine.split(arr[i]);

            if(arr[i] instanceof Object[]) {
               for(int j = 0; j < ((Object[]) arr[i]).length; j++) {
                  if(((Object[]) arr[i])[j] instanceof NativeArray) {
                     ((Object[]) arr[i])[j] =
                        JavaScriptEngine.split(((Object[]) arr[i])[j]);
                  }
               }
            }
         }
      }

      return arr;
   }

   static final int ACTUAL_ACTUAL = 1;
   static final int ACTUAL_360 = 2;

   private static final int US_NASD_30_360 = 0;
   private static final int ACTUAL_365 = 3;
   private static final int EUROPEAN_30_360 = 4;
   private static Calendar date1900;
}
