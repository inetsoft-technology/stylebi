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

package inetsoft.util.script;

import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CalcFinancialTest {

   @Test
   void testAccrint() {
      // Test valid input with annual frequency
      assertEquals(45.83, CalcFinancial.accrint(
         toDate("2023-01-01T00:00"),
         toDate("2023-07-01T00:00"),
         toDate("2023-12-01T00:00"),
         0.05,
         1000,
         1,
         0
      ), 0.01);

      // Test valid input with semi-annual frequency
      assertEquals(25.0, CalcFinancial.accrint(
         toDate("2023-01-01T00:00"),
         toDate("2023-04-01T00:00"),
         toDate("2023-07-01T00:00"),
         0.05,
         1000,
         2,
         0
      ), 0.01);

      // Test invalid frequency
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.accrint(
            toDate("2023-01-01T00:00"),
            toDate("2023-07-01T00:00"),
            toDate("2023-12-01T00:00"),
            0.05,
            1000,
            3,
            0
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception1.getMessage());

      // Test invalid basis
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.accrint(
            toDate("2023-01-01T00:00"),
            toDate("2023-07-01T00:00"),
            toDate("2023-12-01T00:00"),
            0.05,
            1000,
            1,
            5
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());

      // Test NaN basis (should default to 0)
      assertEquals(45.83, CalcFinancial.accrint(
         toDate("2023-01-01T00:00"),
         toDate("2023-07-01T00:00"),
         toDate("2023-12-01T00:00"),
         0.05,
         1000,
         1,
         Double.NaN
      ), 0.01);
   }

   @Test
   void testAccrintm() {
      // Test valid input
      assertEquals(50.0, CalcFinancial.accrintm(
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         0.05,
         1000,
         0
      ), 0.01);

      // Test invalid basis
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.accrintm(
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            0.05,
            1000,
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception.getMessage());

      // Test NaN basis (should default to 0)
      assertEquals(50.0, CalcFinancial.accrintm(
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         0.05,
         1000,
         Double.NaN // NaN basis
      ), 0.01);
   }

   @Test
   void testAmordegrc() {
      // Test case: userPer < 3.0 (amorCoeff = 1.0)
      assertEquals(2500.0, CalcFinancial.amordegrc(
         10000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         1000.0,
         1,
         0.5,
         0
      ), 0.01);

      // Test case: 3.0 <= userPer < 5.0 (amorCoeff = 1.5)
      assertEquals(2475.0, CalcFinancial.amordegrc(
         10000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         1000.0,
         1,
         0.3,
         0
      ), 0.01);

      // Test case: 5.0 <= userPer <= 6.0 (amorCoeff = 2.0)
      assertEquals(2400.0, CalcFinancial.amordegrc(
         10000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         1000.0,
         1,
         0.2,
         0
      ), 0.01);

      // Test case: userPer > 6.0 (amorCoeff = 2.5)
      assertEquals(2344.0, CalcFinancial.amordegrc(
         10000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         1000.0,
         1,
         0.15,
         0
      ), 0.01);

      // Test case: rest < 0.0, period - n = 1
      assertEquals(250.0, CalcFinancial.amordegrc(
         1000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         900.0,
         1,
         0.5,
         0
      ), 0.01);

      // Test case: rest < 0.0, period - n > 1
      assertEquals(0.0, CalcFinancial.amordegrc(
         1000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         900.0,
         2,
         0.5,
         0
      ), 0.01);

      // Test case: invalid basis (basis < 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.amordegrc(
            10000.0,
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            1000.0,
            1,
            0.5,
            -1
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception1.getMessage());

      // Test case: invalid basis (basis > 4)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.amordegrc(
            10000.0,
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            1000.0,
            1,
            0.5,
            5
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());
   }

   @Test
   void testAmorlinc() {
      // Test case: period == 0
      assertEquals(100.0, CalcFinancial.amorlinc(
         1000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         100.0,
         0,
         0.1,
         0
      ), 0.01);

      // Test case: period <= numOfFullPeriods
      assertEquals(100.0, CalcFinancial.amorlinc(
         1000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         100.0,
         5,
         0.1,
         0
      ), 0.01);

      // Test case: period == numOfFullPeriods + 1
      assertEquals(0.0, CalcFinancial.amorlinc(
         1000.0,
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         100.0,
         10,
         0.1,
         0
      ), 0.01);

      // Test case: invalid basis (basis < 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.amorlinc(
            1000.0,
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            100.0,
            1,
            0.1,
            -1
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception1.getMessage());

      // Test case: invalid basis (basis > 4)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.amorlinc(
            1000.0,
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            100.0,
            1,
            0.1,
            5
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());
   }

   @Test
   void testCoupdaybs() {
      // Test valid input with annual frequency, bug #71383
//      assertEquals(1, CalcFinancial.coupdaybs(
//         toDate("2023-01-01T00:00"),
//         toDate("2023-12-31T00:00"),
//         1,
//         0
//      ));

      // Test valid input with semi-annual frequency
//      assertEquals(1, CalcFinancial.coupdaybs(
//         toDate("2023-01-01T00:00"),
//         toDate("2023-06-30T00:00"),
//         2,
//         0
//      ));

      // Test invalid frequency
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaybs(
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            3, // Invalid frequency
            0
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception1.getMessage());

      // Test invalid basis
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaybs(
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            1,
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());

      // Test maturity date before settlement date
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaybs(
            toDate("2023-12-31T00:00"),
            toDate("2023-01-01T00:00"), // Maturity before settlement
            1,
            0
         )
      );
      assertEquals("Maturity date should be post settlement date", exception3.getMessage());
   }

   @Test
   void testCoupdays() {
      // Test valid input with annual frequency
      assertEquals(360, CalcFinancial.coupdays(
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00"),
         1,
         0
      ));

      // Test valid input with semi-annual frequency
      assertEquals(180, CalcFinancial.coupdays(
         toDate("2023-01-01T00:00"),
         toDate("2023-06-30T00:00"),
         2,
         0
      ));

      // Test invalid frequency
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdays(
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            3, // Invalid frequency
            0
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception1.getMessage());

      // Test invalid basis
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdays(
            toDate("2023-01-01T00:00"),
            toDate("2023-12-31T00:00"),
            1,
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());

      // Test maturity date before settlement date
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdays(
            toDate("2023-12-31T00:00"),
            toDate("2023-01-01T00:00"), // Maturity before settlement
            1,
            0
         )
      );
      assertEquals("Maturity date should be post settlement date", exception3.getMessage());
   }

   @Test
   void testCoupdaysnc() {
      // Test valid input with annual frequency
      assertEquals(209, CalcFinancial.coupdaysnc(
         toDate("2023-01-01T00:00"),
         toDate("2023-07-30T00:00"),
         1,
         0
      ));

      // Test valid input with semi-annual frequency
      assertEquals(119, CalcFinancial.coupdaysnc(
         toDate("2023-01-01T00:00"),
         toDate("2023-04-30T00:00"),
         2,
         0
      ));

      // Test invalid frequency
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaysnc(
            toDate("2023-01-01T00:00"),
            toDate("2023-07-30T00:00"),
            3, // Invalid frequency
            0
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception1.getMessage());

      // Test invalid basis
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaysnc(
            toDate("2023-01-01T00:00"),
            toDate("2023-07-30T00:00"),
            1,
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());

      // Test maturity date before settlement date
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupdaysnc(
            toDate("2023-07-30T00:00"),
            toDate("2023-01-01T00:00"), // Maturity before settlement
            1,
            0
         )
      );
      assertEquals("Maturity date should be post settlement date", exception3.getMessage());
   }

   @Test
   void testCoupnum() {
      // Test valid input with annual frequency
      assertEquals(1, CalcFinancial.coupnum(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2024-01-01T00:00"), // Maturity date
         1, // Annual frequency
         0  // Basis
      ));

      // Test valid input with semi-annual frequency
      assertEquals(2, CalcFinancial.coupnum(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2024-01-01T00:00"), // Maturity date
         2, // Semi-annual frequency
         0  // Basis
      ));

      // Test invalid frequency
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupnum(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2024-01-01T00:00"), // Maturity date
            3, // Invalid frequency
            0  // Basis
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception1.getMessage());

      // Test invalid basis
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupnum(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2024-01-01T00:00"), // Maturity date
            1, // Annual frequency
            5  // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception2.getMessage());

      // Test maturity date before settlement date
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.coupnum(
            toDate("2024-01-01T00:00"), // Settlement date
            toDate("2023-01-01T00:00"), // Maturity date
            1, // Annual frequency
            0  // Basis
         )
      );
      assertEquals("Maturity date should be post settlement date", exception3.getMessage());
   }

   @Test
   void testCumipmt() {
      // Test case: Valid input
      assertEquals(-27.2897, CalcFinancial.cumipmt(0.05 / 12, 12, 1000, 1, 12, 0), 0.01);

      // Test case: Invalid rate (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumipmt(0, 12, 1000, 1, 12, 0)
      );
      assertEquals("Rate should be greater than 0", exception1.getMessage());

      // Test case: Invalid nper (<= 0)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumipmt(0.05 / 12, 0, 1000, 1, 12, 0)
      );
      assertEquals("Payment periods should be greater than 0", exception2.getMessage());

      // Test case: Invalid present value (<= 0)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumipmt(0.05 / 12, 12, 0, 1, 12, 0)
      );
      assertEquals("Present value should be greater than 0", exception3.getMessage());

      // Test case: Invalid start and end periods
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumipmt(0.05 / 12, 12, 1000, 0, 12, 0)
      );
      assertEquals("Start and End periods should at least be equal to 1 and " +
                      "End period should be after the Start period", exception4.getMessage());
   }

   @Test
   void testCumprinc() {
      // Test case: Valid input
      assertEquals(-1000.0, CalcFinancial.cumprinc(0.05 / 12, 12, 1000, 1, 12, 0), 0.01);

      // Test case: Invalid rate (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumprinc(0, 12, 1000, 1, 12, 0)
      );
      assertEquals("Rate should be greater than 0", exception1.getMessage());

      // Test case: Invalid nper (<= 0)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumprinc(0.05 / 12, 0, 1000, 1, 12, 0)
      );
      assertEquals("Payment periods should be greater than 0", exception2.getMessage());

      // Test case: Invalid present value (<= 0)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumprinc(0.05 / 12, 12, 0, 1, 12, 0)
      );
      assertEquals("Present value should be greater than 0", exception3.getMessage());

      // Test case: Invalid start and end periods
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.cumprinc(0.05 / 12, 12, 1000, 0, 12, 0)
      );
      assertEquals("Start and End periods should at least be equal to 1 and " +
                      "End period should be after the Start period", exception4.getMessage());
   }

   @Test
   void testDb() {
      // Test case: Valid input, first period
      assertEquals(206.0, CalcFinancial.db(1000.0, 100.0, 10.0, 1.0, 12.0), 0.01);

      // Test case: Valid input, intermediate period
      assertEquals(163.56, CalcFinancial.db(1000.0, 100.0, 10.0, 2.0, 12.0), 0.01);

      // Test case: Invalid life (less than 1)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.db(1000.0, 100.0, 0.0, 1.0, 12.0)
      );
      assertEquals("Life should be greater than 0", exception1.getMessage());

      // Test case: Invalid period (greater than life)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.db(1000.0, 100.0, 10.0, 12.0, 12.0)
      );
      assertEquals("Period should be less than Life", exception2.getMessage());

      // Test case: Optional month parameter is NaN
      assertEquals(206.0, CalcFinancial.db(1000.0, 100.0, 10.0, 1.0, Double.NaN), 0.01);
   }

   @Test
   void testDdb() {
      // Test case: Valid input, first period
      assertEquals(400.0, CalcFinancial.ddb(1000.0, 100.0, 5.0, 1.0, 2.0), 0.01);

      // Test case: Valid input, intermediate period
      assertEquals(240.0, CalcFinancial.ddb(1000.0, 100.0, 5.0, 2.0, 2.0), 0.01);

      // Test case: Valid input, last period
      assertEquals(29.60, CalcFinancial.ddb(1000.0, 100.0, 5.0, 5.0, 2.0), 0.01);

      // Test case: Optional factor parameter is NaN (should default to 2)
      assertEquals(400.0, CalcFinancial.ddb(1000.0, 100.0, 5.0, 1.0, Double.NaN), 0.01);

      // Test case: Invalid life (less than 1)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ddb(1000.0, 100.0, 0.0, 1.0, 2.0)
      );
      assertEquals("Life should be greater than 0", exception1.getMessage());

      // Test case: Invalid period (less than 1)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ddb(1000.0, 100.0, 5.0, 0.0, 2.0)
      );
      assertEquals("Period should be greater than 0", exception2.getMessage());

      // Test case: Invalid period (greater than life)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ddb(1000.0, 100.0, 5.0, 6.0, 2.0)
      );
      assertEquals("Period should be less than Life", exception3.getMessage());
   }

   @Test
   void testDisc() {
      // Test case: Valid input
      assertEquals(0.05, CalcFinancial.disc(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2023-12-31T00:00"), // Maturity date
         95.0, // Price
         100.0, // Redemption
         0 // Basis
      ), 0.01);

      // Test case: Maturity date before settlement date
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.disc(
            toDate("2023-12-31T00:00"), // Settlement date
            toDate("2023-01-01T00:00"), // Maturity date (before settlement)
            95.0, // Price
            100.0, // Redemption
            0 // Basis
         )
      );
      assertEquals("Start Date falls after the End Date", exception5.getMessage());
   }

   @Test
   void testDuration() {
      // Test case: Valid input with annual frequency
      assertEquals(4.55708, CalcFinancial.duration(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2028-01-01T00:00"), // Maturity date
         0.05,                 // Coupon rate
         0.04,                 // Yield
         1,                    // Annual frequency
         0                     // Basis
      ), 0.01);

      // Test case: Invalid coupon rate (negative)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.duration(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2028-01-01T00:00"), // Maturity date
            -0.05,                // Invalid coupon rate
            0.04,                 // Yield
            1,                    // Annual frequency
            0                     // Basis
         )
      );
      assertEquals("Coupon should be at least equal to 0", exception1.getMessage());

      // Test case: Invalid yield (negative)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.duration(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2028-01-01T00:00"), // Maturity date
            0.05,                 // Coupon rate
            -0.04,                // Invalid yield
            1,                    // Annual frequency
            0                     // Basis
         )
      );
      assertEquals("Yield should be at least equal to 0", exception2.getMessage());

      // Test case: Invalid frequency
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.duration(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2028-01-01T00:00"), // Maturity date
            0.05,                 // Coupon rate
            0.04,                 // Yield
            3,                    // Invalid frequency
            0                     // Basis
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception3.getMessage());

      // Test case: Invalid basis
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.duration(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2028-01-01T00:00"), // Maturity date
            0.05,                 // Coupon rate
            0.04,                 // Yield
            1,                    // Annual frequency
            5                     // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception4.getMessage());

      // Test case: Maturity date before settlement date
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.duration(
            toDate("2028-01-01T00:00"), // Settlement date
            toDate("2023-01-01T00:00"), // Maturity date (before settlement)
            0.05,                 // Coupon rate
            0.04,                 // Yield
            1,                    // Annual frequency
            0                     // Basis
         )
      );
      assertEquals("Maturity date should be post settlement date", exception5.getMessage());
   }

   @Test
   void testEffect() {
      // Test case: Valid input
      assertEquals(0.10381289, CalcFinancial.effect(0.1, 4), 0.000001);

      // Test case: Nominal rate is zero or negative
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.effect(0, 4)
      );
      assertEquals("Nominal Rate should be a value greater than 0", exception1.getMessage());

      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.effect(-0.1, 4)
      );
      assertEquals("Nominal Rate should be a value greater than 0", exception2.getMessage());

      // Test case: Number of compounding periods per year is less than 1
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.effect(0.1, 0)
      );
      assertEquals("Number of compounding periods per year should be greater than or equal to 1", exception3.getMessage());
   }

   @Test
   void testFv() {
      // Test case: Valid input with a positive rate
      assertEquals(-2072.24, CalcFinancial.fv(0.05, 10, 100, 500, 0), 0.01);

      // Test case: Valid input with a zero rate
      assertEquals(-1500.0, CalcFinancial.fv(0.0, 10, 100, 500, 0), 0.01);

      // Test case: Valid input with type = 1 (payment at the beginning of the period)
      assertEquals(-2135.13, CalcFinancial.fv(0.05, 10, 100, 500, 1), 0.01);

      // Test case: Invalid type (should default to 0)
      assertEquals(-2072.24, CalcFinancial.fv(0.05, 10, 100, 500, 2), 0.01);

      // Test case: NaN present value (should default to 0)
      assertEquals(-1257.79, CalcFinancial.fv(0.05, 10, 100, Double.NaN, 0), 0.01);
   }

   @Test
   void testFvschedule() {
      // Test case: Valid input with positive rates
      assertEquals(115.5, CalcFinancial.fvschedule(100.0, new Object[]{ 0.1, 0.05 }), 0.01);

      // Test case: Valid input with mixed rates (positive and negative)
      assertEquals(104.5, CalcFinancial.fvschedule(100.0, new Object[]{ 0.1, -0.05 }), 0.01);

      // Test case: Valid input with zero rates
      assertEquals(100.0, CalcFinancial.fvschedule(100.0, new Object[]{ 0.0, 0.0 }), 0.01);

      // Test case: Empty schedule
      assertEquals(100.0, CalcFinancial.fvschedule(100.0, new Object[]{}), 0.01);

      // Test case: Negative principal
      assertEquals(-115.5, CalcFinancial.fvschedule(-100.0, new Object[]{ 0.1, 0.05 }), 0.01);
   }

   @Test
   void testIntrate() {
      // Test case: Valid input
      assertEquals(0.05, CalcFinancial.intrate(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2023-12-31T00:00"), // Maturity date
         100.0, // Investment
         105.0, // Redemption
         0 // Basis
      ), 0.01);

      // Test case: Invalid investment (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.intrate(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2023-12-31T00:00"), // Maturity date
            0.0, // Invalid investment
            105.0, // Redemption
            0 // Basis
         )
      );
      assertEquals("Amount Invested should be a value greater than 0", exception1.getMessage());

      // Test case: Invalid redemption (<= 0)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.intrate(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2023-12-31T00:00"), // Maturity date
            100.0, // Investment
            0.0, // Invalid redemption
            0 // Basis
         )
      );
      assertEquals("Redemption Amount should be a value greater than 0", exception2.getMessage());

      // Test case: Invalid basis (< 0)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.intrate(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2023-12-31T00:00"), // Maturity date
            100.0, // Investment
            105.0, // Redemption
            -1 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      // Test case: Invalid basis (> 4)
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.intrate(
            toDate("2023-01-01T00:00"), // Settlement date
            toDate("2023-12-31T00:00"), // Maturity date
            100.0, // Investment
            105.0, // Redemption
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception4.getMessage());

      // Test case: Maturity date before settlement date
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.intrate(
            toDate("2023-12-31T00:00"), // Settlement date
            toDate("2023-01-01T00:00"), // Maturity date (before settlement)
            100.0, // Investment
            105.0, // Redemption
            0 // Basis
         )
      );
      assertEquals("Maturity date should be post settlement date", exception5.getMessage());
   }

   @Test
   void testIpmt() {
      // Test case: Valid input with type = 0
      assertEquals(-50.0, CalcFinancial.ipmt(0.05, 1, 10, 1000, 0, 0), 0.01);

      // Test case: Valid input with type = 1
      assertEquals(-43.83, CalcFinancial.ipmt(0.05, 2, 10, 1000, 0, 1), 0.01);

      // Test case: Invalid period (less than 1)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ipmt(0.05, 0, 10, 1000, 0, 0)
      );
      assertEquals("Period should be between 1 and nper", exception1.getMessage());

      // Test case: Invalid period (greater than nper)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ipmt(0.05, 11, 10, 1000, 0, 0)
      );
      assertEquals("Period should be between 1 and nper", exception2.getMessage());

      // Test case: Valid input with future value (fv) as NaN
      assertEquals(-50.0, CalcFinancial.ipmt(0.05, 1, 10, 1000, Double.NaN, 0), 0.01);
   }

   @Test
   void testIspmt() {
      // Test case: Valid input
      assertEquals(-45.0, CalcFinancial.ispmt(0.05, 1, 10, 1000), 0.01);

      // Test case: Invalid period (less than 1)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ispmt(0.05, 0, 10, 1000)
      );
      assertEquals("Period should be between 1 and nper", exception1.getMessage());

      // Test case: Invalid period (greater than nper)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.ispmt(0.05, 11, 10, 1000)
      );
      assertEquals("Period should be between 1 and nper", exception2.getMessage());
   }

   @Test
   void testMduration() {
      // Test case: Valid input with annual frequency
      assertEquals(4.381814, CalcFinancial.mduration(
         toDate("2023-01-01T00:00"), // Settlement date
         toDate("2028-01-01T00:00"), // Maturity date
         0.05, // Coupon rate
         0.04, // Yield
         1,    // Frequency (Annual)
         0     // Basis
      ), 0.00001);

      // Test case: Invalid coupon rate (negative)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mduration(
            toDate("2023-01-01T00:00"),
            toDate("2028-01-01T00:00"),
            -0.05, // Invalid coupon rate
            0.04,
            1,
            0
         )
      );
      assertEquals("Coupon should be at least equal to 0", exception1.getMessage());

      // Test case: Invalid yield (negative)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mduration(
            toDate("2023-01-01T00:00"),
            toDate("2028-01-01T00:00"),
            0.05,
            -0.04, // Invalid yield
            1,
            0
         )
      );
      assertEquals("Yield should be at least equal to 0", exception2.getMessage());

      // Test case: Invalid frequency
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mduration(
            toDate("2023-01-01T00:00"),
            toDate("2028-01-01T00:00"),
            0.05,
            0.04,
            3, // Invalid frequency
            0
         )
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception3.getMessage());

      // Test case: Invalid basis
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mduration(
            toDate("2023-01-01T00:00"),
            toDate("2028-01-01T00:00"),
            0.05,
            0.04,
            1,
            5 // Invalid basis
         )
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception4.getMessage());

      // Test case: Maturity date before settlement date
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mduration(
            toDate("2028-01-01T00:00"), // Maturity date
            toDate("2023-01-01T00:00"), // Settlement date
            0.05,
            0.04,
            1,
            0
         )
      );
      assertEquals("Maturity date should be post settlement date", exception5.getMessage());
   }

   @Test
   void testMirr() {
      // Test case: Valid input with positive and negative cash flows
      Object[] cashFlows = new Object[]{ -1000.0, 300.0, 400.0, 500.0 };
      double financeRate = 0.1; // 10% finance rate
      double reinvestRate = 0.12; // 12% reinvestment rate

      // Expected result calculated manually or using a reliable financial tool
      assertEquals(0.098156692, CalcFinancial.mirr(cashFlows, financeRate, reinvestRate), 0.000001);

      // Test case: Invalid input with only positive cash flows
      Object[] invalidCashFlows1 = new Object[]{ 100.0, 200.0, 300.0 };
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mirr(invalidCashFlows1, financeRate, reinvestRate)
      );
      assertEquals("Values must contain at least one positive value and one negative value", exception1.getMessage());

      // Test case: Invalid input with only negative cash flows
      Object[] invalidCashFlows2 = new Object[]{ -100.0, -200.0, -300.0 };
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.mirr(invalidCashFlows2, financeRate, reinvestRate)
      );
      assertEquals("Values must contain at least one positive value and one negative value", exception2.getMessage());
   }

   @Test
   void testNominal() {
      // Test case: Valid input
      assertEquals(0.048889, CalcFinancial.nominal(0.05, 12), 0.00001);

      // Test case: Invalid effective rate (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.nominal(0.0, 12)
      );
      assertEquals("Nominal Rate should be a value greater than 0", exception1.getMessage());

      // Test case: Invalid number of compounding periods per year (< 1)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.nominal(0.05, 0)
      );
      assertEquals("Number of compounding periods per year should be greater than or equal to 1", exception2.getMessage());
   }

   @Test
   void testNper() {
      // Test case: Valid input with a positive rate
      assertEquals(14.2067, CalcFinancial.nper(0.05, 100, -1000, 0, 0), 0.0001);

      // Test case: Valid input with a zero rate
      assertEquals(10.0, CalcFinancial.nper(0.0, 100, -1000, 0, 0), 0.0001);

      // Test case: Valid input with type = 1 (payment at the beginning of the period)
      assertEquals(13.25323, CalcFinancial.nper(0.05, 100, -1000, 0, 1), 0.0001);

      // Test case: Invalid type (should default to 0)
      assertEquals(14.2067, CalcFinancial.nper(0.05, 100, -1000, 0, 2), 0.0001);

      // Test case: NaN future value (should default to 0)
      assertEquals(14.2067, CalcFinancial.nper(0.05, 100, -1000, Double.NaN, 0), 0.0001);
   }

   @Test
   void testNpv() {
      // Test case: Valid input with positive and negative cash flows
      Object[] cashFlows = new Object[]{ -1000.0, 300.0, 400.0, 500.0 };
      double rate = 0.1; // 10% discount rate

      assertEquals(-19.12437675, CalcFinancial.npv(rate, cashFlows), 0.0001);

      // Test case: Empty cash flow array
      assertEquals(0.0, CalcFinancial.npv(rate, new Object[]{}), 0.0001);

      // Test case: Zero discount rate
      assertEquals(200.0, CalcFinancial.npv(0.0, cashFlows), 0.0001);

      // Test case: null cash flows
      assertEquals(0.0, CalcFinancial.npv(rate, null));
   }

   @Test
   void testPmt() {
      // Test case: Valid input with non-zero rate
      double rate = 0.05; // 5% interest rate
      int nper = 10; // 10 payment periods
      double pv = 1000; // Present value
      double fv = 0; // Future value
      int type = 0; // Payment at the end of the period
      assertEquals(-129.5, CalcFinancial.pmt(rate, nper, pv, fv, type), 0.1);

      // Test case: Valid input with zero rate
      rate = 0.0;
      assertEquals(-100.0, CalcFinancial.pmt(rate, nper, pv, fv, type), 0.1);

      // Test case: Invalid type (should default to 0)
      type = 2;
      assertEquals(-129.5, CalcFinancial.pmt(0.05, nper, pv, fv, type), 0.1);

      // Test case: Future value as NaN (should default to 0)
      assertEquals(-129.5, CalcFinancial.pmt(0.05, nper, pv, Double.NaN, 0), 0.1);
   }

   @Test
   void testPrice() {
      // Test case: Valid input
      Object settlement = toDate("2023-01-01T00:00");
      Object maturity = toDate("2023-12-31T00:00");
      double rate = 0.05; // 5% annual coupon rate
      double yield = 0.04; // 4% annual yield
      double redemption = 100.0; // Redemption value per $100 face value
      double frequency = 2; // Semi-annual payments
      double basis = 0; // Actual/actual day count basis

      double result = CalcFinancial.price(settlement, maturity, rate, yield, redemption, frequency, basis);
      assertEquals(100.968, result, 0.01);

      // Test case: Invalid frequency
      Object finalMaturity = maturity;
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.price(settlement, finalMaturity, rate, yield, redemption, 3, basis)
      );
      assertEquals("Frequency should be either 1 (Annual), 2 (SemiAnnual) or 4 (Quarterly)", exception.getMessage());

      // Test case: Invalid basis
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.price(settlement, finalMaturity, rate, yield, redemption, frequency, 5)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception.getMessage());

      // Test case: Invalid rate (negative)
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.price(settlement, finalMaturity, -0.05, yield, redemption, frequency, basis)
      );
      assertEquals("Annual Coupon Rate should be a value at least equal to 0", exception.getMessage());

      // Test case: Invalid yield (negative)
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.price(settlement, finalMaturity, rate, -0.04, redemption, frequency, basis)
      );
      assertEquals("Annual Yield should be a value at least equal to 0", exception.getMessage());

      // Test case: Maturity date before settlement date
      Object finalMaturity1 = toDate("2022-12-31T00:00");
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.price(settlement, finalMaturity1, rate, yield, redemption, frequency, basis)
      );
      assertEquals("Maturity date should be post settlement date", exception.getMessage());
   }

   @Test
   void testPricedisc() {
      // Test case: Valid input
      Object settlement = toDate("2023-01-01T00:00");
      Object maturity = toDate("2023-12-31T00:00");
      double discount = 0.05; // 5% discount rate
      double redemption = 100.0; // Redemption value per $100 face value
      double basis = 0; // Actual/actual day count basis

      double result = CalcFinancial.pricedisc(settlement, maturity, discount, redemption, basis);
      assertEquals(95.0, result, 0.01);

      // Test case: Invalid discount (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricedisc(settlement, maturity, 0.0, redemption, basis)
      );
      assertEquals("Discount should be a value greater than 0", exception1.getMessage());

      // Test case: Invalid redemption (<= 0)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricedisc(settlement, maturity, discount, 0.0, basis)
      );
      assertEquals("Redemption should be a value greater than 0", exception2.getMessage());

      // Test case: Invalid basis (< 0 or > 4)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricedisc(settlement, maturity, discount, redemption, -1)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricedisc(settlement, maturity, discount, redemption, 5)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception4.getMessage());

      // Test case: Maturity date before settlement date
      Object invalidMaturity = toDate("2022-12-31T00:00");
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricedisc(settlement, invalidMaturity, discount, redemption, basis)
      );
      assertEquals("Maturity date should be post settlement date", exception5.getMessage());
   }

   @Test
   void testPricemat() {
      // Valid input test case
      Object settlement = toDate("2023-01-01T00:00"); // January 1, 2023
      Object maturity = toDate("2024-01-01T00:00");  // January 1, 2024
      Object issue = toDate("2022-01-01T00:00");     // January 1, 2022
      double rate = 0.05;                      // 5% interest rate
      double yield = 0.04;                     // 4% annual yield
      double basis = 0;                        // Actual/actual day count basis

      double result = CalcFinancial.pricemat(settlement, maturity, issue, rate, yield, basis);
      assertEquals(100.7692, result, 0.001);

      // Test case: Negative rate
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricemat(settlement, maturity, issue, -0.01, yield, basis)
      );
      assertEquals("Rate should be a value at least equal to 0", exception1.getMessage());

      // Test case: Negative yield
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricemat(settlement, maturity, issue, rate, -0.01, basis)
      );
      assertEquals("Annual Yield should be a value at least equal to 0", exception2.getMessage());

      // Test case: Invalid basis
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricemat(settlement, maturity, issue, rate, yield, 5)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      // Test case: Maturity date before settlement date
      Object invalidMaturity = toDate("2022-12-31T00:00"); // December 31, 2022
      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.pricemat(settlement, invalidMaturity, issue, rate, yield, basis)
      );
      assertEquals("Maturity date should be post settlement date", exception4.getMessage());
   }

   @Test
   void testPv() {
      // Test case: rate is 0
      assertEquals(-1500.0, CalcFinancial.pv(0.0, 10, 100.0, 500.0, 0), 0.001);

      // Test case: rate is positive, type is 0
      assertEquals(-1079.13, CalcFinancial.pv(0.05, 10, 100.0, 500.0, 0), 0.01);

      // Test case: rate is positive, type is 1
      assertEquals(-1117.74, CalcFinancial.pv(0.05, 10, 100.0, 500.0, 1), 0.01);

      // Test case: invalid type
      assertEquals(-1079.13, CalcFinancial.pv(0.05, 10, 100.0, 500.0, 2), 0.01); // Type defaults to 0
   }

   @Test
   void testReceived() {
      // Test case: Valid inputs
      Object settlement = toDate("2023-01-01T00:00"); // January 1, 2023
      Object maturity = toDate("2023-12-31T00:00"); // December 31, 2023
      double investment = 1000.0;
      double discount = 0.05;
      double basis = 0;

      double result = CalcFinancial.received(settlement, maturity, investment, discount, basis);
      assertEquals(1052.63, result, 0.01); // Expected value with a small delta

      // Test case: Invalid investment (<= 0)
      Exception exception1 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.received(settlement, maturity, 0.0, discount, basis)
      );
      assertEquals("Amount Invested should be a value greater than 0", exception1.getMessage());

      // Test case: Invalid discount (<= 0)
      Exception exception2 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.received(settlement, maturity, investment, 0.0, basis)
      );
      assertEquals("Discount rate should be a value greater than 0", exception2.getMessage());

      // Test case: Invalid basis (< 0 or > 4)
      Exception exception3 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.received(settlement, maturity, investment, discount, -1)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      Exception exception4 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.received(settlement, maturity, investment, discount, 5)
      );
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception4.getMessage());

      // Test case: Maturity date before settlement date
      Object invalidMaturity = toDate("2022-12-31T00:00"); // December 31, 2022
      Exception exception5 = assertThrows(RuntimeException.class, () ->
         CalcFinancial.received(settlement, invalidMaturity, investment, discount, basis)
      );
      assertEquals("Maturity date should be post settlement date", exception5.getMessage());
   }

   @Test
   void testSln() {
      double result = CalcFinancial.sln(1000.0, 100.0, 10);
      assertEquals(90.0, result, 0.01);
   }

   @Test
   void testSyd() {
      // Test case 1: Valid inputs
      assertEquals(163.64, CalcFinancial.syd(1000.0, 100.0, 10.0, 1.0), 0.01);

      // Test case 2: Period exceeds life
      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.syd(1000.0, 100.0, 10.0, 11.0);
      });
      assertEquals("Period should be less than Life", exception.getMessage());

      // Test case 3: Negative life
      Exception exception2 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.syd(1000.0, 100.0, -10.0, 1.0);
      });
      assertEquals("Life should be greater than 0", exception2.getMessage());

      // Test case 4: Period less than 1, bug #71399
//      assertEquals(171.82, CalcFinancial.syd(1000.0, 100.0, 10.0, 0.5), 0.01);
   }

   @Test
   void testTbilleq() {
      Date settlement = toDate("2023-01-01T00:00"); // January 1, 2023
      Date maturity = toDate("2023-06-30T00:00"); // June 30, 2023

      assertEquals(0.0519943, CalcFinancial.tbilleq(settlement, maturity, 0.05), 0.00001);
   }

   @Test
   void testTbillprice() {
      // Test case for leap year
      Date settlementLeapYear = toDate("2024-01-01T00:00"); // January 1, 2024
      Date maturityLeapYear = toDate("2024-06-30T00:00"); // June 30, 2024
      double discountLeapYear = 0.05;
      assertEquals(97.4861, CalcFinancial.tbillprice(settlementLeapYear, maturityLeapYear, discountLeapYear), 0.01);

      // Test case for non-leap year
      Date settlementNonLeapYear = toDate("2023-01-01T00:00"); // January 1, 2023
      Date maturityNonLeapYear = toDate("2023-06-30T00:00"); // June 30, 2023
      double discountNonLeapYear = 0.05;
      assertEquals(97.5, CalcFinancial.tbillprice(settlementNonLeapYear, maturityNonLeapYear, discountNonLeapYear), 0.01);

      // Test case: Maturity date before settlement date
      Object invalidMaturity = toDate("2022-12-31T00:00"); // December 31, 2022
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.tbillprice(settlementLeapYear, invalidMaturity, 0.05)
      );
      assertEquals("Start Date falls after the End Date", exception.getMessage());

      // Test case: Maturity date and settlement date in different years
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.tbillprice(settlementNonLeapYear, maturityLeapYear, 0.05)
      );
      assertEquals("Maturity Date should be within a calendar year of the Settlement Date", exception.getMessage());
   }

   @Test
   void testTbillyield() {
      // Test case: Valid input for a non-leap year
      Date settlementNonLeapYear = toDate("2023-01-01T00:00"); // January 1, 2023
      Date maturityNonLeapYear = toDate("2023-06-30T00:00"); // June 30, 2023
      double parNonLeapYear = 97.5;
      assertEquals(0.051282051, CalcFinancial.tbillyield(settlementNonLeapYear, maturityNonLeapYear, parNonLeapYear), 0.0001);

      // Test case: Valid input for a leap year
      Date settlementLeapYear = toDate("2024-01-01T00:00"); // January 1, 2024
      Date maturityLeapYear = toDate("2024-06-30T00:00"); // June 30, 2024
      double parLeapYear = 97.4861;
      assertEquals(0.05128959, CalcFinancial.tbillyield(settlementLeapYear, maturityLeapYear, parLeapYear), 0.0001);

      // Test case: Maturity date and settlement date in different years
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.tbillyield(settlementNonLeapYear, maturityLeapYear, parNonLeapYear)
      );
      assertEquals("Maturity Date should be within a calendar year of the Settlement Date", exception.getMessage());
   }

   @Test
   void testVdb() {
      // Test case: Valid input with flag set to true
      double cost = 1000.0;
      double salvage = 100.0;
      double life = 5.0;
      double startPeriod = 1.0;
      double endPeriod = 3.0;
      double factor = 2.0;
      boolean flag = true;

      double result = CalcFinancial.vdb(cost, salvage, life, startPeriod, endPeriod, factor, flag);
      assertEquals(384.0, result, 0.01);

      // Test case: Valid input with flag set to false
      result = CalcFinancial.vdb(cost, salvage, life, startPeriod, endPeriod, factor, false);
      assertEquals(384.0, result, 0.01);

      // Test case: Invalid life value
      Exception exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.vdb(cost, salvage, 0.0, startPeriod, endPeriod, factor, flag)
      );
      assertEquals("Life should be greater than 0", exception.getMessage());

      // Test case: Start period greater than end period
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.vdb(cost, salvage, life, 4.0, 3.0, factor, flag)
      );
      assertEquals("Start Period should be less than End Period", exception.getMessage());

      // Test case: Start period greater than life
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.vdb(cost, salvage, life, 6.0, 7.0, factor, flag)
      );
      assertEquals("Start Period should be less than Life", exception.getMessage());

      // Test case: End period greater than life
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.vdb(cost, salvage, life, startPeriod, 6.0, factor, flag)
      );
      assertEquals("End Period should be less than Life", exception.getMessage());

      // Test case: Negative start period
      exception = assertThrows(RuntimeException.class, () ->
         CalcFinancial.vdb(cost, salvage, life, -1.0, endPeriod, factor, flag)
      );
      assertEquals("Start Period should be greater than or equal to zero", exception.getMessage());
   }

   @Test
   void testXnpvCombined() {
      // Test case: Valid input
      double rate = 0.05;
      Object[] values = { 1000.0, -500.0 };
      Object[] dates = {
         toDate("2023-01-01T00:00"),
         toDate("2023-12-31T00:00")
      };

      assertEquals(523.745866, CalcFinancial.xnpv(rate, values, dates), 0.0001);

      // Test case: Invalid dates
      Object[] invalidDates = { toDate("2023-01-01T00:00") };

      Exception exception = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.xnpv(rate, values, invalidDates);
      });

      assertEquals("Every payment should have a payment date associated with it", exception.getMessage());
   }

   @Test
   void testXirr() {
      // Test case: Valid input
      Object[] valuesValid = { -1000.0, 500.0, 700.0 };
      Object[] datesValid = {
         toDate("2023-01-01T00:00"),
         toDate("2023-07-01T00:00"),
         toDate("2023-12-31T00:00")
      };
      assertEquals(0.2628756, CalcFinancial.xirr(valuesValid, datesValid, 0.1), 0.00001);

      // Test case: Invalid dates length
      Object[] valuesInvalidDates = { -1000.0, 500.0 };
      Object[] datesInvalidDates = { toDate("2023-01-01T00:00") };
      Exception exceptionDates = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.xirr(valuesInvalidDates, datesInvalidDates, 0.1);
      });
      assertEquals("Every payment should have a payment date associated with it", exceptionDates.getMessage());

      // Test case: Insufficient payments
      Object[] valuesInsufficient = { -1000.0 };
      Object[] datesInsufficient = { toDate("2023-01-01T00:00") };
      double resultInsufficient = CalcFinancial.xirr(valuesInsufficient, datesInsufficient, 0.1);
      assertTrue(Double.isNaN(resultInsufficient));

      // Test case: No positive or negative payments
      Object[] valuesNoPosNeg = { 0.0, 0.0 };
      Object[] datesNoPosNeg = {
         toDate("2023-01-01T00:00"),
         toDate("2023-07-01T00:00")
      };
      double resultNoPosNeg = CalcFinancial.xirr(valuesNoPosNeg, datesNoPosNeg, 0.1);
      assertTrue(Double.isNaN(resultNoPosNeg));

      // Test case: Max iterations exceeded
      double resultMaxIter = CalcFinancial.xirr(valuesValid, datesValid, 100.0);
      assertTrue(Double.isNaN(resultMaxIter));
   }

   @Test
   void testYieldDisc() {
      // Test case: Valid input
      Object settlement = toDate("2023-01-01T00:00"); // January 1, 2023
      Object maturity = toDate("2023-12-31T00:00"); // December 31, 2023
      double pr = 95.0;
      double redemption = 100.0;
      double basis = 0;

      double result = CalcFinancial.yielddisc(settlement, maturity, pr, redemption, basis);
      assertEquals(0.05263, result, 0.00001); // Expected value with tolerance

      // Test case: Invalid price
      Exception exception1 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yielddisc(settlement, maturity, -1.0, redemption, basis);
      });
      assertEquals("Security's price should be a value greater than 0", exception1.getMessage());

      // Test case: Invalid redemption
      Exception exception2 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yielddisc(settlement, maturity, pr, -1.0, basis);
      });
      assertEquals("Security's Redemption should be a value greater than 0", exception2.getMessage());

      // Test case: Invalid basis
      Exception exception3 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yielddisc(settlement, maturity, pr, redemption, -1.0);
      });
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      // Test case: Invalid maturity date
      Exception exception4 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yielddisc(maturity, settlement, pr, redemption, basis);
      });
      assertEquals("Maturity date should be post settlement date", exception4.getMessage());
   }

   @Test
   void testYieldmat() {
      // Test case: Valid input
      Object settlement = toDate("2023-01-01T00:00"); // January 1, 2023
      Object maturity = toDate("2023-12-31T00:00"); // December 31, 2023
      Object issue = toDate("2023-01-01T00:00"); // January 1, 2023
      double rate = 0.05;
      double pr = 95.0;
      double basis = 0;

      double result = CalcFinancial.yieldmat(settlement, maturity, issue, rate, pr, basis);
      assertEquals(0.105263, result, 0.00001);

      // Test case: Invalid rate
      Exception exception1 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yieldmat(settlement, maturity, issue, -0.01, pr, basis);
      });
      assertEquals("Rate should be a value greater than or equals to 0", exception1.getMessage());

      // Test case: Invalid price
      Exception exception2 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yieldmat(settlement, maturity, issue, rate, 0, basis);
      });
      assertEquals("Security's price should be a value greater than 0", exception2.getMessage());

      // Test case: Invalid basis
      Exception exception3 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yieldmat(settlement, maturity, issue, rate, pr, 5);
      });
      assertEquals("Basis should be a value between 0 and 4 (both inclusive)", exception3.getMessage());

      // Test case: Invalid maturity date
      Exception exception4 = assertThrows(RuntimeException.class, () -> {
         CalcFinancial.yieldmat(maturity, settlement, issue, rate, pr, basis);
      });
      assertEquals("Maturity date should be post settlement date", exception4.getMessage());
   }

   /**
    * @param localDateTime an ISO-8601 datetime string, e.g. 2007-12-03T10:15:30
    *
    * @return the corresponding date in the default time zone.
    */
   private java.util.Date toDate(String localDateTime) {
      return java.util.Date.from(LocalDateTime.parse(localDateTime)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant());
   }
}