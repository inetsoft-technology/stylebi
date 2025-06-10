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

import java.text.DecimalFormat;
import java.util.*;

/**
 * Implementation of all Financial functions for JavaScript
 *
 * @version 8.0, 6/30/2005
 * @author InetSoft Technology Corp
 */
public class CalcFinancial {
   /**
    * Get the accrued interest for a security that pays periodic interest
    * @param issue Security's issue date
    * @param firstCoupon Security's first interest date
    * @param settlement Security's settlement date
    * @param rate Security's annual coupon rate
    * @param par Security's par value
    * @param frequency Number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return accrued interest for a security that pays periodic interest
    */
   public static double accrint(Object issue, Object firstCoupon,
                             Object settlement, double rate, double par,
                             double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      issue = JavaScriptEngine.unwrap(issue);
      firstCoupon = JavaScriptEngine.unwrap(firstCoupon);
      settlement = JavaScriptEngine.unwrap(settlement);
      par = Math.floor(par);
      frequency = Math.floor(frequency);
      basis = Math.floor(basis);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      double days = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) settlement, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) issue);
      double year = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR),
         (int) basis);

      return par * rate / frequency * (days * frequency  / year);
   }

   /**
    * Get the accrued interest for a security that pays interest at maturity
    * @param issue Security's issue date
    * @param maturity Security's maturity date
    * @param rate Security's annual coupon rate
    * @param par Security's par value
    * @param basis Type of Day Count basis to use
    * @return accrued interest for a security that pays interest at maturity
    */
   public static double accrintm(Object issue, Object maturity,
                                 double rate, double par, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      issue = JavaScriptEngine.unwrap(issue);
      maturity = JavaScriptEngine.unwrap(maturity);
      par = Math.floor(par);
      basis = Math.floor(basis);

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      double days = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) issue);
      double year = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR),
         (int) basis);

      return par * rate * (days / year);
   }

   /**
    * Returns the depreciation for each accounting period
    * @param cost cost of the asset
    * @param date_purchased date of the purchase of the asset
    * @param first_period date of the end of the first period
    * @param salvage salvage value at the end of the life of the asset
    * @param period the period
    * @param rate rate of depreciation
    * @param basis Type of Day Count basis to use
    * @return depreciation for each accounting period
    */
   public static double amordegrc(double cost, Object date_purchased,
                                  Object first_period, double salvage,
                                  int period, double rate, int basis) {
      date_purchased = JavaScriptEngine.unwrap(date_purchased);
      first_period = JavaScriptEngine.unwrap(first_period);

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      double amorCoeff = 0;
      double nRate = 0;
      double rest = 0;
      double userPer = 1.0 / rate;

      if(userPer < 3.0) {
         amorCoeff = 1.0;
      }
      else if(userPer < 5.0) {
         amorCoeff = 1.5;
      }
      else if(userPer <= 6.0) {
         amorCoeff = 2.0;
      }
      else {
         amorCoeff = 2.5;
      }

      rate *= amorCoeff;
      nRate = Math.round(
         CalcDateTime.yearfrac(date_purchased, first_period, basis) * rate * cost);

      cost -= nRate;
      rest = cost - salvage;

      for(int n = 0; n < period; n++) {
         nRate = Math.round(rate * cost);
         rest -= nRate;

         if(rest < 0.0) {
            switch(period - n) {
            case 0:

            case 1:
               return Math.round(cost * 0.5);

            default:
               return 0.0;
            }
         }

         cost -= nRate;
      }

      return nRate;
   }

   /**
    * Returns the depreciation for each accounting period
    * @param cost cost of the asset
    * @param date_purchased date of the purchase of the asset
    * @param first_period date of the end of the first period
    * @param salvage salvage value at the end of the life of the asset
    * @param period the period
    * @param rate rate of depreciation
    * @param basis Type of Day Count basis to use
    * @return depreciation for each accounting period
    */
   public static double amorlinc(double cost, Object date_purchased,
                                 Object first_period, double salvage,
                                 int period, double rate, int basis) {
      date_purchased = JavaScriptEngine.unwrap(date_purchased);
      first_period = JavaScriptEngine.unwrap(first_period);

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      double oneRate = cost * rate;
      double costDelta = cost - salvage;
      double oRate = CalcDateTime.yearfrac(date_purchased, first_period,
                                           basis) * rate * cost;
      int numOfFullPeriods = (int) ((cost - salvage - oRate) / oneRate);

      double result = 0;

      if(period == 0) {
         result = oRate;
      }
      else if(period <= numOfFullPeriods) {
         result = oneRate;
      }
      else if(period == numOfFullPeriods + 1) {
         result = costDelta - oneRate * numOfFullPeriods - oRate;
      }

      return result;
   }

   /**
    * Get the number of days from the beginning of the coupon
    * period to the settlement date
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return number of days from the beginning of the coupon
    * period to the settlement date
    */
   public static int coupdaybs(Object settlement, Object maturity,
			       double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      Object previous_coupon = couppcd(settlement, maturity, frequency, basis);

      return CalcUtil.getDayCountBasisDays(
         (Date) previous_coupon, (Date) settlement, (int) basis);
   }

   /**
    * Get the number of days in the coupon period that contains
    * the settlement date.
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return number of days in the coupon period that contains
    * the settlement date.
    */
   public static int coupdays(Object settlement, Object maturity,
                              double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      Object previous_coupon = couppcd(settlement, maturity, frequency, basis);

      Object next_coupon = coupncd(settlement, maturity, frequency, basis);

      return CalcUtil.getDayCountBasisDays(
         (Date) previous_coupon, (Date) next_coupon, (int) basis);
   }

   /**
    * Get the number of days from the settlement date to the next coupon date
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return number of days from the settlement date to the next coupon date
    */
   public static int coupdaysnc(Object settlement, Object maturity,
                                   double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      Object next_coupon = coupncd(settlement, maturity, frequency, basis);

      return CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) next_coupon, (int) basis);
   }

   /**
    * Get the next coupon date after the settlement date
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return next coupon date after the settlement date
    */
   public static Object coupncd(Object settlement, Object maturity,
                                double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      int MONTHS = 12;

      maturity = couppcd(settlement, maturity, frequency, basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) maturity);
      cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) +
              MONTHS / (int) frequency);

      return cal.getTime();
   }

   /**
    * Get the number of coupons payable between the settlement
    * date and maturity date
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return number of coupons payable between the settlement
    * date and maturity date
    */
   public static int coupnum(Object settlement, Object maturity,
                                double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      Object start_coupon = couppcd(settlement, maturity, frequency, basis);
      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) start_coupon);

      int MONTHS = 12;
      int coupon_periods = 0;

      while(((Date) maturity).after((Date) start_coupon)) {
         cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) +
                 MONTHS / (int) frequency);
         start_coupon = cal.getTime();
         coupon_periods++;
      }

      return coupon_periods;
   }

   /**
    * Get the previous coupon date before the settlement date
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return previous coupon date before the settlement date
    */
   public static Object couppcd(Object settlement, Object maturity,
                                double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);
      frequency = Math.floor(frequency);

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) maturity);

      int MONTHS = 12;

      while(((Date) maturity).after((Date) settlement)) {
         cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) -
                 MONTHS / (int) frequency);
         maturity = cal.getTime();
      }

      return maturity;
   }

   /**
    * Returns the cumulative interest paid on a loan between start_period and
    * end_period
    * @param rate interest rate
    * @param nper total number of payment periods
    * @param pv present value
    * @param start_period first period in the calculation
    * @param end_period last period in the calculation
    * @param type timing of the payment
    * @return cumulative interest paid on a loan
    */
   public static double cumipmt(double rate, int nper, double pv,
                                double start_period, double end_period,
                                int type) {
      start_period = Math.floor(start_period);
      end_period = Math.floor(end_period);

      if(rate <= 0) {
         throw new RuntimeException("Rate should be greater than 0");
      }

      if(nper <= 0) {
         throw new RuntimeException("Payment periods should be greater than 0");
      }

      if(pv <= 0) {
         throw new RuntimeException("Present value should be greater than 0");
      }

      if(start_period < 1 || end_period < 1 || start_period > end_period) {
         throw new RuntimeException("Start and End periods should at least be " +
                                    "equal to 1 and End period should be "+
                                    "after the Start period");
      }

      double cumipmt = 0;

      for(int i = (int) start_period; i <= (int) end_period; i++) {
         cumipmt += ipmt(rate, i, nper, pv, 0.0, type);
      }

      return cumipmt;
   }

   /**
    * Returns the cumulative principal paid on a loan between start_period
    * and end_period
    * @param rate interest rate
    * @param nper total number of payment periods
    * @param pv present value
    * @param start_period first period in the calculation
    * @param end_period last period in the calculation
    * @param type timing of the payment
    * @return cumulative principal paid on a loan
    */
   public static double cumprinc(double rate, int nper, double pv,
                                 double start_period, double end_period,
                                 int type) {
      start_period = Math.floor(start_period);
      end_period = Math.floor(end_period);

      if(rate <= 0) {
         throw new RuntimeException("Rate should be greater than 0");
      }

      if(nper <= 0) {
         throw new RuntimeException("Payment periods should be greater than 0");
      }

      if(pv <= 0) {
         throw new RuntimeException("Present value should be greater than 0");
      }

      if(start_period < 1 || end_period < 1 || start_period > end_period) {
         throw new RuntimeException("Start and End periods should at least be " +
                                    "equal to 1 and End period should be "+
                                    "after the Start period");
      }

      double cumprinc = 0;

      for(int i = (int) start_period; i <= (int) end_period; i++) {
         cumprinc += ppmt(rate, i, nper, pv, 0.0, type);
      }

      return cumprinc;
   }

   /**
    * Get the depreciation of an asset for a specified period using
    * the fixed-declining balance method
    * @param cost initial cost of the asset
    * @param salvage value at the end of the depreciation
    * @param life number of periods over which the asset is being depreciated
    * @param period period for which you want to calculate the depreciation
    * @param month number of months in the first year. If month is omitted,
    * it is assumed to be 12.
    * @return depreciation of an asset for a specified period using
    * the fixed-declining balance method
    */
   public static double db(double cost, double salvage, double life,
                           double period, double month) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "month" optional parameter being MIA
      if(Double.valueOf(month).isNaN()) {
         month = 12;
      }

      // ChrisS bug1403015689632 2014-6-19
      // Enforce limits on period/life parameters
      enforcePeriodLifeLimits(period, life, true);

      double rate = 1 - Math.pow((salvage / cost), (1 / life));

      //Round to 3 decimals
      DecimalFormat df = new DecimalFormat("#.###");
      rate = Double.valueOf(df.format(rate));

      double db;

      if((int) period == 1) {
         db = cost * rate * month / 12;
      }
      else if((int) period == ((int) life + 1)) {
         double depreciations = 0.0;

         for(int i = ((int) period - 1); i > 0; i--) {
            depreciations += db(cost, salvage, life, i, month);
         }

         db = (cost - depreciations) * rate * (12 - month) / 12;
      }
      else {
         double depreciations = 0.0;

         for(int i = ((int) period - 1); i > 0; i--) {
            depreciations += db(cost, salvage, life, i, month);
         }

         db = (cost - depreciations) * rate;
      }

      return db;
   }

   /**
    * Returns the depreciation of an asset for a specified period using the
    * double-declining balance method or some other method you specify
    * @param cost initial cost of the asset
    * @param salvage value at the end of the depreciation
    * @param life number of periods over which the asset is being depreciated
    * @param period period for which you want to calculate the depreciation
    * @param factor rate at which the balance declines
    * @return depreciation of an asset for a specified period using
    * the fixed-declining balance method or some other method you specify
    */
   public static double ddb(double cost, double salvage, double life,
                            double period, double factor) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "factor" optional parameter being MIA
      if(Double.valueOf(factor).isNaN()) {
         factor = 2;
      }

      // ChrisS bug1403015689632 2014-6-19
      // Enforce limits on period/life parameters
      enforcePeriodLifeLimits(period, life, true);

      double total = 0;

      for(int i = 0; i < life - 1; i++) {
         double period_dep = (cost - total) * (factor / life);

         if(period - 1 == i) {
            return period_dep;
         }
         else {
            total += period_dep;
         }
      }

      return (cost - total - salvage);
   }

   /**
    * Get the discount rate for a security.
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param pr security's price per $100 face value
    * @param redemption security's redemption value per $100 face value
    * @param basis Type of Day Count basis to use
    * @return discount rate for a security.
    */
   public static double disc(Object settlement, Object maturity,
                             double pr, double redemption, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR),
         (int) basis);
      // NOTE: The formula for DISC has been documented as
      // ((redemption - pr) / pr) * (B / DSM)
      // But using redemption in the demoninator instead of pr fetches
      // correct result (matching with Excel)
      return ((redemption - pr) / redemption) * (B / DSM);
   }

   /**
    * Returns the Macauley duration for an assumed par value of $100
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param coupon security's annual coupon rate
    * @param yld security's annual yield
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return discount rate for a security.
    */
   public static double duration(Object settlement, Object maturity,
                                 double coupon, double yld, double frequency,
                                 double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      frequency = Math.floor(frequency);
      basis = Math.floor(basis);

      if(coupon < 0) {
         throw new RuntimeException("Coupon should be at least equal to 0");
      }

      if(yld < 0) {
         throw new RuntimeException("Yield should be at least equal to 0");
      }

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double dur = 0;
      double p = 0;
      double f100 = 100.0;

      coupon *= f100 / frequency;
      yld /= frequency;
      yld += 1.0;

      int numOfCoups = coupnum(settlement, maturity, frequency, basis);

      for(int t = 0; t < numOfCoups; t++) {
         dur += t * coupon / CalcUtil.intPow(yld, t);
      }

      dur += numOfCoups * (coupon + f100) / CalcUtil.intPow(yld, numOfCoups);

      for (int t = 1 ; t < numOfCoups; t++ ) {
         p += coupon / CalcUtil.intPow(yld, t);
      }

      p += (coupon + f100) / CalcUtil.intPow(yld, numOfCoups);

      dur /= p;
      dur /= frequency;

      return dur;
   }

   /**
    * Get the effective annual interest rate
    * @param nominal_rate Nominal Interest Rate
    * @param npery Number of compounding periods per year
    * @return effective annual interest rate
    */
   public static double effect(double nominal_rate, double npery) {
      npery = Math.floor(npery);

      if(nominal_rate <= 0) {
         throw new RuntimeException(
            "Nominal Rate should be a value greater than 0");
      }

      if(npery < 1) {
         throw new RuntimeException(
            "Number of compounding periods per year should be greater than " +
            "or equal to 1");
      }

      return (Math.pow((1 + (nominal_rate / npery)), npery) - 1);
   }

   /**
    * Get the future value of an investment.
    * @param rate interest rate for the loan
    * @param nper total number of payments for the loan
    * @param pmt payment made each period
    * @param pv past value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return present value of an investment
    */
   public static double fv(double rate, int nper, double pmt, double pv,
                           int type) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "pv" optional parameter being MIA
      if(Double.valueOf(pv).isNaN()) {
         pv = 0;
      }

      if(type != 0 && type != 1) {
         type = 0;
      }

      if(rate == 0.0) {
         return -1 * (pv + (pmt * nper));
      }

      return -1 * ((pv * CalcUtil.intPow((1 + rate), nper)) +
                   (pmt * (1 + rate * type) * ((CalcUtil.intPow((1 + rate), nper) - 1) /
                                               rate)));
   }

   /**
    * Get the future value of an initial principal after applying a
    * series of compound interest rates
    * @param principal present value
    * @param scheduleObj array of interest rates to apply
    * @return future value of an initial principal after applying a
    * series of compound interest rates
    */
   public static double fvschedule(double principal, Object scheduleObj) {
      Object[] schedule = JavaScriptEngine.split(scheduleObj);

      double[] rates = CalcUtil.convertToDoubleArray(schedule);

      double future = principal;

      for(int i = 0; i < rates.length; i++) {
         future += future * rates[i];
      }

      return future;
   }

   /**
    * Get the interest rate for a fully invested security
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param investment Amount invested in the security
    * @param redemption Amount to be received at maturity
    * @param basis Type of Day Count basis to use
    * @return interest rate for a fully invested security
    */
   public static double intrate(Object settlement, Object maturity,
                                double investment, double redemption,
                                double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);

      if(investment <= 0) {
         throw new RuntimeException(
            "Amount Invested should be a value greater than 0");
      }

      if(redemption <= 0) {
         throw new RuntimeException(
            "Redemption Amount should be a value greater than 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double days = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double year = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR),
         (int) basis);

      return (redemption - investment) / investment * (year / days);
   }

   /**
    * Returns the interest payment for a given period for an investment based
    * on periodic, constant payments and a constant interest rate
    * @param rate interest rate for the loan
    * @param per period for which you want to find the interest
    * @param nper total number of payments for the loan
    * @param pv past value
    * @param fv future value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return interest payment for a given period
    */
   public static double ipmt(double rate, int per, int nper, double pv,
                             double fv, int type) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "fv" optional parameter being MIA
      if(Double.valueOf(fv).isNaN()) {
         fv = 0;
      }

      if(per < 1 || per >= nper + 1) {
         throw new RuntimeException("Period should be between 1 and nper");
      }

      double pmt = pmt(rate, nper, pv, fv, type);
      double ipmt = 0;

      if(type == 0) {
         double d = CalcUtil.intPow(1 + rate, per - 1);
         ipmt = -1 * (pv * d * rate + pmt * (d - 1));
      }
      else {
         // ChrisS bug1401746265354 2014-6-10
         // Ipmt needs to be calculated differently for type 1
         // [for type 1, payment is at start of period, interest is at end]
         // While looping is not the most efficient solution, it does work.
         double principal = pv;
         for(int i = 1; i < per; i++) {
            ipmt = -(principal + pmt) * rate;
            principal += (pmt - ipmt);
         }
      }

      return ipmt;
   }

   /**
    * Calculates the interest paid during a specific period of an investment
    * @param rate interest rate for the loan
    * @param per period for which you want to find the interest
    * @param nper total number of payments for the loan
    * @param pv past value
    * @return interest paid during a specific period of an investment
    */
   public static double ispmt(double rate, int per, int nper, double pv) {
      if(per < 1 || per >= nper + 1) {
         throw new RuntimeException("Period should be between 1 and nper");
      }

      // ChrisS bug1402518720048 2014-6-13
      // Correct the calculation used in ispmt()
      return - pv * rate * (nper - per) / (1.0 * nper);
   }

   /**
    * Returns the Macauley duration for an assumed par value of $100
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param coupon security's annual coupon rate
    * @param yld security's annual yield
    * @param frequency number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return discount rate for a security.
    */
   public static double mduration(Object settlement, Object maturity,
                                  double coupon, double yld, double frequency,
                                  double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      double duration = duration(settlement, maturity, coupon, yld,
                                 frequency, basis);

      return duration / (1 + yld / frequency);
   }

   /**
    * Returns the modified internal rate of return for a series of periodic
    * cash flows
    * @param valuesObj series of payments (negative values) and income
    * (positive values) occurring at regular periods
    * @param finance_rate interest rate you pay on the money used in the
    * cash flows
    * @param reinvest_rate interest rate you receive on the cash flows as
    * you reinvest them
    * @return modified internal rate
    */
   // ChrisS bug1402523328088 2014-6-13
   // Change return type from String to double.
   public static double mirr(Object valuesObj, double finance_rate,
                             double reinvest_rate) {
      Object[] values = JavaScriptEngine.split(valuesObj);
      values = JavaScriptEngine.split(values);

      double[] vals = CalcUtil.convertToDoubleArray(values);

      ArrayList<Double> positive = new ArrayList<>();
      ArrayList<Double> negative = new ArrayList<>();

      for(int i = 0; i < vals.length; i++) {
         if(vals[i] >= 0) {
            positive.add(vals[i]);
         }
         else {
            negative.add(vals[i]);
         }
      }

      if(positive.isEmpty() || negative.isEmpty()) {
         throw new RuntimeException("Values must contain at least one " +
                                    "positive value and one negative value");
      }

      return Math.pow(
         (-1 * npv(reinvest_rate, positive.toArray()) *
            CalcUtil.intPow(1 + reinvest_rate, positive.size())) /
         (npv(finance_rate, negative.toArray()) *
          (1 + finance_rate)), 1.0 / positive.size()) - 1;
   }

   /**
    * Get the nominal annual interest rate
    * @param effect_rate Effective Interest Rate
    * @param npery Number of compounding periods per year
    * @return nominal annual interest rate
    */
   public static double nominal(double effect_rate, double npery) {
      npery = Math.floor(npery);

      if(effect_rate <= 0) {
         throw new RuntimeException(
            "Nominal Rate should be a value greater than 0");
      }

      if(npery < 1) {
         throw new RuntimeException(
            "Number of compounding periods per year should be greater than " +
            "or equal to 1");
      }

      return npery * (Math.pow((effect_rate + 1), (1 / npery)) - 1);
   }

   /**
    * Get the future value of an investment.
    * @param rate interest rate for the loan
    * @param pmt payment made each period
    * @param pv past value
    * @param fv future value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return present value of an investment
    */
   public static double nper(double rate, double pmt, double pv, double fv, int type) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(fv).isNaN()) {
         fv = 0;
      }

      if(type != 0 && type != 1) {
         type = 0;
      }

      if(rate == 0.0) {
         return -1 * (fv + pv) / pmt;
      }

      double numerator = (-1 * fv + pmt * (1 + rate * type) / rate) /
         (pv + pmt * (1 + rate * type) / rate);
      return Math.log(numerator) / Math.log(1 + rate);
   }

   /**
    * Get the net present value of an investment by using a discount rate and
    * a series of future payments and income
    * @param rate Effective Interest Rate
    * @param valuesObj Cash flow payments
    * @return nominal annual interest rate
    */
   public static double npv(double rate, Object valuesObj) {
      Object[] values = JavaScriptEngine.split(valuesObj);
      values = JavaScriptEngine.split(values);

      double[] payments_income = CalcUtil.convertToDoubleArray(values);
      double npv = 0.0;

      for(int i = 0; i < payments_income.length; i++) {
         npv += (payments_income[i] / CalcUtil.intPow((1 + rate), (i + 1)));
      }

      return npv;
   }

   /**
    * Get the payment for a loan based on constant payments and a
    * constant interest rate.
    * @param rate interest rate for the loan
    * @param nper total number of payments for the loan
    * @param pv present value
    * @param fv future value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return payment for a loan based on constant payments and a
    * constant interest rate.
    */
   public static double pmt(double rate, int nper, double pv, double fv,
                            int type) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "fv" optional parameter being MIA
      if(Double.valueOf(fv).isNaN()) {
         fv = 0;
      }

      if(type != 0 && type != 1) {
         type = 0;
      }

      if(rate == 0.0) {
         return -1 * (fv + pv) / nper;
      }

      return -1.0 * ((fv + pv * CalcUtil.intPow((1 + rate), nper)) * rate) /
         ((1 + rate * type) * (CalcUtil.intPow((1 + rate), nper) - 1));
   }

   /**
    * Returns the payment on the principal for a given period for an investment
    * based on periodic, constant payments and a constant interest rate
    * @param rate interest rate for the loan
    * @param nper total number of payments for the loan
    * @param pv past value
    * @param fv future value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return payment on the principal for a given period
    */
   public static double ppmt(double rate, int per, int nper, double pv,
                             double fv, int type) {
      return pmt(rate, nper, pv, fv, type) - ipmt(rate, per, nper, pv, fv, type);
   }

   /**
    * Get the price per $100 face value of a security that pays periodic interest
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param rate Security's annual coupon rate
    * @param yield Security's annual yield
    * @param redemption Security's redemption value per $100 face value
    * @param frequency Number of coupon payments per year
    * @param basis Type of Day Count basis to use
    * @return price per $100 face value of a security that pays periodic interest
    */
   public static double price(Object settlement, Object maturity,
                              double rate, double yield, double redemption,
                              double frequency, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);
      frequency = Math.floor(frequency);
      basis = Math.floor(basis);

      if(yield < 0) {
         throw new RuntimeException(
            "Annual Yield should be a value at least equal to 0");
      }

      if(rate < 0) {
         throw new RuntimeException(
            "Annual Coupon Rate should be a value at least equal to 0");
      }

      if(redemption <= 0) {
         throw new RuntimeException(
            "Redemption value should be a a value greater than 0");
      }

      if((int) frequency != 1 && (int) frequency != 2 && (int) frequency != 4) {
         throw new RuntimeException(
            "Frequency should be either 1 (Annual), 2 (SemiAnnual) or " +
            "4 (Quarterly)");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DSC = CalcFinancial.coupdaysnc(settlement, maturity, frequency, basis);
      double E = CalcFinancial.coupdays(settlement, maturity, frequency, basis);
      double N = CalcFinancial.coupnum(settlement, maturity, frequency, basis);
      double A = CalcFinancial.coupdaybs(settlement, maturity, frequency, basis);

      double summation = 0.0;

      for(int k = 1; k <= N; k++) {
         summation += (100 * rate / frequency) /
            (Math.pow((1 + yield / frequency), (k - 1 + DSC / E)));
      }

      return (redemption / Math.pow((1 + yield / frequency), (N - 1 + DSC / E)))
         + summation - (100 * rate / frequency * A / E);
   }

   /**
    * Get the price per $100 face value of a discounted security
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param discount Security's discount rate
    * @param redemption Security's redemption value per $100 face value
    * @param basis Type of Day Count basis to use
    * @return price per $100 face value of a discounted security
    */
   public static double pricedisc(Object settlement, Object maturity,
                                  double discount, double redemption,
                                  double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);
      basis = Math.floor(basis);

      if(discount <= 0) {
         throw new RuntimeException(
            "Discount should be a value greater than 0");
      }

      if(redemption <= 0) {
         throw new RuntimeException(
            "Redemption should be a value greater than 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR), (int) basis);

      return redemption - discount * redemption * DSM / B;
   }

   /**
    * Get the price per $100 face value of security that pays
    * interest at maturity
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param issue Security's issue date
    * @param rate Security's interest rate at date of issue
    * @param yield Security's annual yield
    * @param basis Type of Day Count basis to use
    * @return price per $100 face value of security that pays
    * interest at maturity
    */
   public static double pricemat(Object settlement, Object maturity,
                                 Object issue, double rate,
                                 double yield, double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);
      issue = JavaScriptEngine.unwrap(issue);
      basis = Math.floor(basis);

      if(rate < 0) {
         throw new RuntimeException(
            "Rate should be a value at least equal to 0");
      }

      if(yield < 0) {
         throw new RuntimeException(
            "Annual Yield should be a value at least equal to 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      double DIM = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) maturity, (int) basis);

      double A = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) settlement, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) issue);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR), (int) basis);

      return ((100 + (DIM / B * rate * 100)) / (1 + (DSM / B * yield))) -
         (A / B * rate * 100);
   }

   /**
    * Get the present value of an investment
    * @param rate interest rate for the loan
    * @param nper total number of payments for the loan
    * @param pmt payment made each period
    * @param fv future value
    * @param type number 0 (zero) or 1 and indicates when payments are due
    * @return present value of an investment
    */
   public static double pv(double rate, int nper, double pmt, double fv, int type) {
      if(type != 0 && type != 1) {
         type = 0;
      }

      if(rate == 0.0) {
         return -1 * (fv + (pmt * nper));
      }

      return -1 * (fv + pmt * (1 + rate * type) *
                   (CalcUtil.intPow((1 + rate), nper) - 1) / rate) /
         CalcUtil.intPow((1 + rate), nper);
   }

   /**
    * Get the amount received at maturity for a fully invested security
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param investment Amount invested in the security
    * @param discount Security's discount rate
    * @param basis Type of Day Count basis to use
    * @return amount received at maturity for a fully invested security
    */
   public static double received(Object settlement, Object maturity,
                                 double investment, double discount,
                                 double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);
      basis = Math.floor(basis);

      if(investment <= 0) {
         throw new RuntimeException(
            "Amount Invested should be a value greater than 0");
      }

      if(discount <= 0) {
         throw new RuntimeException(
            "Discount rate should be a value greater than 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DIM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR), (int) basis);

      return investment / (1 - discount * DIM / B);
   }

   /**
    * Get the depreciation of an asset using the straight line method
    * @param cost Initial cost of the asset
    * @param salvage Salvage value of the asset
    * @param life Useful life of the asset
    * @return depreciation of an asset using the straight line method
    */
   public static double sln(double cost, double salvage, int life) {
      return (cost - salvage) / life;
   }

   /**
    * Get the sum of years digits depreciation of an asset for a specified
    * period
    * @param cost Initial cost of the asset
    * @param salvage Salvage value of the asset
    * @param life Useful life of the asset
    * @param per Period
    * @return sum of years digits depreciation of an asset for a specified
    * period
    */
   public static double syd(double cost, double salvage, double life,
                            double per) {
      // ChrisS bug1403015689632 2014-6-19
      // Enforce limits on period/life parameters
      enforcePeriodLifeLimits(per, life);

      return ((cost - salvage) * (life - per + 1) * 2) / ((life) * (life + 1));
   }

   /**
    * Get the bond-equivalent yield for a Treasury bill
    * @param settlement Treasury Bill's settlement date
    * @param maturity Treasury Bill's maturity date
    * @param discount Treasury Bill's discount rate
    * @return bond-equivalent yield for a Treasury bill
    */
   public static double tbilleq(Object settlement, Object maturity, double discount) {
      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, CalcUtil.ACTUAL_360);

      return (365 * discount) / (360 - (discount * DSM));
   }

   /**
    * Get the price per $100 face value for a Treasury bill
    * @param settlement Treasury Bill's settlement date
    * @param maturity Treasury Bill's maturity date
    * @param discount Treasury Bill's discount rate
    * @return price per $100 face value for a Treasury bill
    */
   public static double tbillprice(Object settlement, Object maturity,
                                   double discount) {
      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      GregorianCalendar cal1 = CoreTool.calendar.get();
      cal1.setTime((Date) settlement);

      Calendar cal2 = CoreTool.calendar2.get();
      cal2.setTime((Date) maturity);

      int days;

      if(cal1.isLeapYear(cal1.get(Calendar.YEAR))) {
         days = 365;
      }
      else {
         days = 364;
      }

      cal1.set(Calendar.DATE, cal1.get(Calendar.DATE) + days);

      if(cal2.getTime().after(cal1.getTime())) {
         throw new RuntimeException("Maturity Date should be within a " +
                                    "calendar year of the Settlement Date");
      }

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, CalcUtil.ACTUAL_ACTUAL);

      return 100 * (1 - discount * DSM / 360);
   }

   /**
    * Get the yield for a Treasury bill
    * @param settlement Treasury Bill's settlement date
    * @param maturity Treasury Bill's maturity date
    * @param par Treasury Bill's price per $100 face value
    * @return yield for a Treasury bill
    */
   public static double tbillyield(Object settlement, Object maturity, double par) {
      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      GregorianCalendar cal1 = CoreTool.calendar.get();
      cal1.setTime((Date) settlement);

      Calendar cal2 = CoreTool.calendar2.get();
      cal2.setTime((Date) maturity);

      int days;

      if(cal1.isLeapYear(cal1.get(Calendar.YEAR))) {
         days = 365;
      }
      else {
         days = 364;
      }

      cal1.set(Calendar.DATE, cal1.get(Calendar.DATE) + days);

      if(cal2.getTime().after(cal1.getTime())) {
         throw new RuntimeException("Maturity Date should be within a " +
                                    "calendar year of the Settlement Date");
      }

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, CalcUtil.ACTUAL_ACTUAL);

      return (100 - par) / par * 360 / DSM;
   }

   /**
    * Returns the depreciation of an asset for any period you specify,
    * including partial periods, using the double-declining balance method
    * or some other method you specify. VDB stands for variable declining
    * balance
    * @param cost initial cost of the asset
    * @param salvage value at the end of the depreciation
    * @param life number of periods over which the asset is depreciated
    * @param start_period starting period for which you want to calculate the
    * depreciation
    * @param end_period ending period for which you want to calculate the
    * depreciation
    * @param factor rate at which the balance declines
    * @param flag logical value specifying whether to switch to straight-line
    * depreciation when depreciation is greater than the declining balance
    * calculation
    * @return variable declining balance
    */
   public static double vdb(double cost, double salvage, double life,
                            double start_period, double end_period,
                            double factor, boolean flag)
   {
      // ChrisS bug1403015689632 2014-6-19
      // Enforce limits on period/life parameters
      // ChrisS bug1403300241621 2014-6-23
      // .. in a manner consistent with Excel enforcement
      if(life < 1) {
         throw new RuntimeException("Life should be greater than 0");
      }
      if(start_period > end_period) {
         throw new RuntimeException("Start Period should be less than End Period");
      }
      if(start_period > life) {
         throw new RuntimeException("Start Period should be less than Life");
      }
      if(end_period > life) {
         throw new RuntimeException("End Period should be less than Life");
      }
      if(start_period < 0) {
         throw new RuntimeException("Start Period should be greater than or equal to zero");
      }
      if(end_period < 0) {
         throw new RuntimeException("End Period should be greater than or equal to zero");
      }

      double fVdb = 0.0;
      double fIntStart = Math.floor(start_period);
      double fIntEnd = Math.ceil(end_period);
      int nLoopStart = (int) fIntStart;
      int nLoopEnd = (int) fIntEnd;

      if(flag) {
         for (int i = nLoopStart + 1; i <= nLoopEnd; i++) {
            double fTerm;

            fTerm = ScGetGDA(cost, salvage, life, i, factor);

            if(i == nLoopStart + 1) {
               fTerm *= (Math.min(end_period, fIntStart + 1.0)
                         - start_period);
            }
            else if(i == nLoopEnd) {
               fTerm *= (end_period + 1.0 - fIntEnd);
            }

            fVdb += fTerm;
         }
      }
      else {
         double life1 = life;
         double fPart;

         if(start_period != Math.floor(start_period)) {
            if(factor > 1) {
               if(start_period >= life / 2) {
                  fPart = start_period - life / 2;
                  start_period = life / 2;
                  end_period -= fPart;
                  life1 += 1;
               }
            }
         }

         cost -= ScInterVDB(cost, salvage, life, life1, start_period,
                            factor);
         fVdb = ScInterVDB(cost, salvage, life, life - start_period,
                           end_period - start_period, factor);
      }

      return fVdb;
   }

   private static double ScGetGDA(double fWert, double fRest, double fDauer,
                                  double fPeriode, double fFaktor)
   {
      double fGda;
      double fZins;
      double fAlterWert;
      double fNeuerWert;

      fZins = fFaktor / fDauer;

      if(fZins >= 1.0) {
         fZins = 1.0;
         if(fPeriode == 1.0) {
            fAlterWert = fWert;
         }
         else {
            fAlterWert = 0.0;
         }
      }
      else {
         fAlterWert = fWert * Math.pow (1.0 - fZins, fPeriode - 1.0);
      }

      fNeuerWert = fWert * Math.pow (1.0 - fZins, fPeriode);

      if(fNeuerWert < fRest) {
         fGda = fAlterWert - fRest;
      }
      else {
         fGda = fAlterWert - fNeuerWert;
      }

      if(fGda < 0.0) {
         fGda = 0.0;
      }

      return fGda;
   }

   private static double ScInterVDB(double cost, double salvage, double life,
                                    double life1, double period, double factor)
   {
      double fVdb = 0;
      double fIntEnd = Math.ceil(period);
      int nLoopEnd = (int) fIntEnd;

      double fTerm;
      double fLia;
      double fRestwert  = cost - salvage;
      boolean bNowLia = false;

      double fGda;

      fLia = 0;

      for(int i = 1; i <= nLoopEnd; i++) {
         if(!bNowLia) {
            fGda = ScGetGDA(cost, salvage, life, i, factor);
            fLia = fRestwert / (life1 - (double) (i - 1));

            if(fLia > fGda) {
               fTerm = fLia;
               bNowLia = true;
            }
            else {
               fTerm = fGda;
               fRestwert -= fGda;
            }
         }
         else {
            fTerm = fLia;
         }

         if(i == nLoopEnd) {
            fTerm *= (period + 1.0 - fIntEnd);
         }

         fVdb += fTerm;
      }

      return fVdb;
   }

   /**
    * Get the net present value for a schedule of cash flows that is
    * not necessarily periodic
    * @param rate Discount rate to apply for cash flows
    * @param valuesObj Cash flow payments
    * @param datesObj Schedule of payments
    * @return net present value for a schedule of cash flows that is
    * not necessarily periodic
    */
   public static double xnpv(double rate, Object valuesObj, Object datesObj) {
      Object[] values = JavaScriptEngine.split(valuesObj);
      Object[] dates = JavaScriptEngine.split(datesObj);

      values = JavaScriptEngine.split(values);
      dates = JavaScriptEngine.split(dates);

      double[] payments = CalcUtil.convertToDoubleArray(values);

      if(payments.length != dates.length) {
         throw new RuntimeException("Every payment should have a payment " +
                                    "date associated with it");
      }

      double xnpv = 0.0;

      Object startDate = JavaScriptEngine.unwrap(dates[0]);

      for(int i = 0; i < payments.length; i++) {
         Date start = (Date) ((Date) startDate).clone();
         double date_diff = (double) CalcUtil.getDayCountBasisDays(
            start, (Date) JavaScriptEngine.unwrap(dates[i]), CalcUtil.ACTUAL_ACTUAL);

         xnpv += payments[i] / Math.pow((1 + rate), (date_diff / 365));
      }

      return xnpv;
   }

   /**
    * Get the internal rate of return for a schedule of cash flows that is
    * not necessarily periodic
    * @param valuesObj Cash flow payments
    * @param datesObj Schedule of payments
    * @param guess A number that you guess is close to the result of XIRR
    * @return The rate of return calculated is the interest rate corresponding to XNPV = 0.
    */
   public static double xirr(Object valuesObj, Object datesObj, Double guess) {
      Object[] values = JavaScriptEngine.split(valuesObj);
      Object[] dates = JavaScriptEngine.split(datesObj);

      values = JavaScriptEngine.split(values);
      dates = JavaScriptEngine.split(dates);

      if(guess == null) {
         guess = 0.1;
      }

      double[] payments = CalcUtil.convertToDoubleArray(values);

      if(payments.length != dates.length) {
         throw new RuntimeException("Every payment should have a payment " +
                                       "date associated with it");
      }

      if(payments.length < 2) {
         return Double.NaN;
      }

      Object startDate = JavaScriptEngine.unwrap(dates[0]);
      double resultRate = guess;

      // @by stephenwebster, using available solution using Newton's method
      // https://en.wikipedia.org/wiki/Newton%27s_method
      // https://gist.github.com/ghalimi/4669712
      double newRate;
      double epsRate;
      double resultValue;
      double resultDerivative;
      double attempt = 0;
      double r;
      // Set maximum tolerance for end of iteration
      double epsMax = 1E-6;
      // Set maximum number of iterations
      double maxAttempts = 100;
      boolean continueLoop;
      boolean hasPositive = payments[0] > 0;
      boolean hasNegative = payments[0] < 0;

      do {
         r = resultRate + 1;
         resultValue = payments[0];
         resultDerivative = 0.0;

         for(int i = 1; i < payments.length; i++) {
            if(payments[i] > 0) {
               hasPositive = true;
            }

            if(payments[i] < 0) {
               hasNegative = true;
            }

            Date start = (Date) ((Date) startDate).clone();
            double date_diff = CalcUtil.getDayCountBasisDays(
                                             start,
                                             (Date) JavaScriptEngine.unwrap(dates[i]),
                                             CalcUtil.ACTUAL_ACTUAL);

            resultValue += payments[i] / Math.pow(r, date_diff / 365);

            double fraction = date_diff / 365;
            resultDerivative -= fraction * payments[i] / Math.pow(r, fraction + 1);
         }

         if(!hasPositive || !hasNegative) {
            return Double.NaN;
         }

         newRate = resultRate - resultValue / resultDerivative;
         epsRate = Math.abs(newRate - resultRate);
         resultRate = newRate;
         continueLoop = (epsRate > epsMax) && (Math.abs(resultValue) > epsMax);

      } while(continueLoop && (++attempt < maxAttempts));

      if(continueLoop) {
         return Double.NaN;
      }

      return resultRate;
   }

   /**
    * Get the annual yield of a discount bond
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param pr security's price per $100 face value
    * @param redemption security's redemption value per $100 face value
    * @param basis Type of Day Count basis to use
    * @return  annual yield of a discount bond
    */
   public static double yielddisc(Object settlement, Object maturity,
                                  double pr, double redemption,
                                  double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);

      basis = Math.floor(basis);

      if(pr <= 0) {
         throw new RuntimeException(
            "Security's price should be a value greater than 0");
      }

      if(redemption <= 0) {
         throw new RuntimeException(
            "Security's Redemption should be a value greater than 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR), (int) basis);

      return (redemption - pr) / pr * (B / DSM);
   }

   /**
    * Get the annual yield of a security that pays interest at maturity
    * @param settlement Security's settlement date
    * @param maturity Security's maturity date
    * @param issue Security's issue date
    * @param rate security's interest rate at date of issue
    * @param pr security's price per $100 face value
    * @param basis Type of Day Count basis to use
    * @return annual yield of a security that pays interest at maturity
    */
   public static double yieldmat(Object settlement, Object maturity,
                                 Object issue, double rate, double pr,
                                 double basis) {
      // ChrisS bug1402087314865 2014-6-10
      // Handle the "basis" optional parameter being MIA
      if(Double.valueOf(basis).isNaN()) {
         basis = 0;
      }

      settlement = JavaScriptEngine.unwrap(settlement);
      maturity = JavaScriptEngine.unwrap(maturity);
      issue = JavaScriptEngine.unwrap(issue);
      basis = Math.floor(basis);

      if(rate < 0.0) {
         throw new RuntimeException(
            "Rate should be a value greater than or equals to 0");
      }

      if(pr <= 0) {
         throw new RuntimeException(
            "Security's price should be a value greater than 0");
      }

      if(basis < 0 || basis > 4) {
         throw new RuntimeException(
            "Basis should be a value between 0 and 4 (both inclusive)");
      }

      if(((Date) maturity).before((Date) settlement)) {
         throw new RuntimeException(
            "Maturity date should be post settlement date");
      }

      double DIM = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) maturity, (int) basis);
      double DSM = (double) CalcUtil.getDayCountBasisDays(
         (Date) settlement, (Date) maturity, (int) basis);
      double A = (double) CalcUtil.getDayCountBasisDays(
         (Date) issue, (Date) settlement, (int) basis);

      Calendar cal = CoreTool.calendar.get();
      cal.setTime((Date) settlement);
      double B = (double) CalcUtil.getDayCountBasisYear(
         cal.get(Calendar.YEAR), (int) basis);


      return ((1 + (DIM / B * rate)) - ((pr / 100) + (A / B * rate))) /
         ((pr / 100) + (A / B * rate)) * (B / DSM);
   }

   private static void enforcePeriodLifeLimits(double period, double life) {
      enforcePeriodLifeLimits(period, life, false);
   }

   // ChrisS bug1403015689632 2014-6-19
   // Enforce limits on period/life parameters
   private static void enforcePeriodLifeLimits(double period, double life, boolean periodGtEOne) {
      if(life < 1) {
         throw new RuntimeException("Life should be greater than 0");
      }

      if(periodGtEOne && period < 1) {
         throw new RuntimeException("Period must be greater than or equal to 1");
      }

      if(period < 0) {
         throw new RuntimeException("Period should be greater than 0");
      }

      if(period > life) {
         throw new RuntimeException("Period should be less than Life");
      }
   }
}
