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
package inetsoft.report.filter;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PthPercentileFormula.
 *
 * <p>The formula uses nearest-rank method: pos = round(p/100.0 * (n-1)).
 * isNull() returns true only when no double values have been added.
 * Object, float, long, int, short are stored in separate lists from double.
 */
@Tag("core")
public class PthPercentileFormulaTest {

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddDoubleValue_returnsFalse() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddObjectValueOnly_remainsTrue() {
      // isNull() checks only dlist != null — Object-only values populate the vs
      // Vector, not dlist, so isNull() remains true even with values present.
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Double.valueOf(5.0));
      assertTrue(formula.isNull());
   }

   @Test
   void getResult_afterAddObjectValueOnly_returnsValueViaObjectBranch() {
      // Even though isNull() returns true for Object-only input, getResult()
      // accesses the vs Vector (not dlist) and does return the computed percentile.
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Double.valueOf(5.0));
      assertEquals(Double.valueOf(5.0), formula.getResult());
   }

   @Test
   void isNull_afterReset_returnsTrue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsNull() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(false);
      assertNull(formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsZeroDouble() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      assertEquals(0.0, (double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // percentile clamping
   // -----------------------------------------------------------------------

   @Test
   void constructor_percentileAbove100_clampsTo100() {
      PthPercentileFormula formula = new PthPercentileFormula(150);
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      // clamped to 100 → should return maximum = 3.0
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void constructor_percentileBelowZero_clampsToZero() {
      PthPercentileFormula formula = new PthPercentileFormula(-10);
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      formula.addValue(3.0d);
      // clamped to 0 → should return minimum = 1.0
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — 0th, 50th, 100th percentile
   // -----------------------------------------------------------------------

   @Test
   void getResult_0thPercentile_returnsMinimum() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.addValue(3.0d);
      // 0th percentile → pos=0, minimum after sort
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_100thPercentile_returnsMaximum() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(9.0d);
      formula.addValue(3.0d);
      // 100th percentile → pos=n-1, maximum after sort
      assertEquals(9.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_50thPercentile_returnsMedianIndex() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.5 * 4) = 2 → value 3
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(3.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_25thPercentile_returnsCorrectValue() {
      PthPercentileFormula formula = new PthPercentileFormula(25);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.25 * 4) = round(1.0) = 1 → value 2
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(2.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_75thPercentile_returnsCorrectValue() {
      PthPercentileFormula formula = new PthPercentileFormula(75);
      // {1, 2, 3, 4, 5} sorted → pos = round(0.75 * 4) = round(3.0) = 3 → value 4
      formula.addValue(3.0d);
      formula.addValue(1.0d);
      formula.addValue(5.0d);
      formula.addValue(2.0d);
      formula.addValue(4.0d);
      assertEquals(4.0, (Double) formula.getResult(), 1e-10);
   }

   @Test
   void getResult_singleDoubleValue_returnsItForAnyPercentile() {
      PthPercentileFormula f0 = new PthPercentileFormula(0);
      PthPercentileFormula f50 = new PthPercentileFormula(50);
      PthPercentileFormula f100 = new PthPercentileFormula(100);
      f0.addValue(7.0d);
      f50.addValue(7.0d);
      f100.addValue(7.0d);
      assertEquals(7.0, (Double) f0.getResult(), 1e-10);
      assertEquals(7.0, (Double) f50.getResult(), 1e-10);
      assertEquals(7.0, (Double) f100.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(double) — null sentinel
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_nullSentinelInFirstPosition_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(new double[]{Tool.NULL_DOUBLE, 5.0});
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDoubleArray_normalValue_addsFirstElement() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(new double[]{3.0, 99.0});
      formula.addValue(new double[]{1.0, 99.0});
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_0thPercentile_returnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(3.0f);
      formula.addValue(1.0f);
      formula.addValue(5.0f);
      assertEquals(1.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(3.0f);
      formula.addValue(1.0f);
      formula.addValue(5.0f);
      assertEquals(5.0f, ((Number) formula.getResult()).floatValue(), 1e-6f);
   }

   @Test
   void addValueFloat_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_FLOAT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_0thPercentile_returnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(30L);
      formula.addValue(10L);
      formula.addValue(20L);
      assertEquals(10L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(30L);
      formula.addValue(10L);
      formula.addValue(20L);
      assertEquals(30L, ((Number) formula.getResult()).longValue());
   }

   @Test
   void addValueLong_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_LONG);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_50thPercentile_returnsMiddleValue() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      // {10, 20, 30} sorted → pos = round(0.5 * 2) = 1 → 20
      formula.addValue(30);
      formula.addValue(10);
      formula.addValue(20);
      assertEquals(20, ((Number) formula.getResult()).intValue());
   }

   @Test
   void addValueInt_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_INTEGER);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_100thPercentile_returnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue((short) 5);
      formula.addValue((short) 15);
      formula.addValue((short) 10);
      assertEquals((short) 15, ((Number) formula.getResult()).shortValue());
   }

   @Test
   void addValueShort_nullSentinel_isIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(Tool.NULL_SHORT);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object)
   // -----------------------------------------------------------------------

   @Test
   void addValueObject_nullIsIgnored() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue((Object) null);
      assertNull(formula.getResult());
   }

   @Test
   void addValueObject_numericValues_0thPercentileReturnsMin() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      // Object path uses NumericComparer, result should be min
      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(10.0, ((Number) result).doubleValue(), 1e-10);
   }

   @Test
   void addValueObject_numericValues_100thPercentileReturnsMax() {
      PthPercentileFormula formula = new PthPercentileFormula(100);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      Object result = formula.getResult();
      assertNotNull(result);
      assertEquals(30.0, ((Number) result).doubleValue(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getDoubleResult()
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_noDoubleValues_returnsZero() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      assertEquals(0.0, formula.getDoubleResult(), 1e-10);
   }

   @Test
   void getDoubleResult_withDoubleValues_returnsPercentileValue() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(5.0d);
      formula.addValue(1.0d);
      formula.addValue(3.0d);
      assertEquals(1.0, formula.getDoubleResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsAllValues() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.addValue(5.0d);
      formula.reset();
      assertTrue(formula.isNull());
      assertNull(formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(99.0d);
      formula.reset();
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      assertEquals(1.0, (Double) formula.getResult(), 1e-10);
   }

   // -----------------------------------------------------------------------
   // getResult() — date values (Bug #80583)
   // -----------------------------------------------------------------------

   private static Object percentileOf(int p, Object... values) {
      PthPercentileFormula formula = new PthPercentileFormula(p);

      for(Object v : values) {
         formula.addValue(v);
      }

      return formula.getResult();
   }

   @Test
   void getResult_sqlDatesOutOfOrder_picksByTime() {
      java.sql.Date jan = java.sql.Date.valueOf("2024-01-01");
      java.sql.Date feb = java.sql.Date.valueOf("2024-02-01");
      java.sql.Date mar = java.sql.Date.valueOf("2024-03-01");

      assertEquals(mar, percentileOf(100, feb, mar, jan));
      assertEquals(jan, percentileOf(0, mar, jan, feb));
      assertEquals(feb, percentileOf(50, mar, jan, feb));
   }

   @Test
   void getResult_utilDatesOutOfOrder_picksByTime() {
      Date d1 = new Date(1_000_000L);
      Date d2 = new Date(2_000_000L);
      Date d3 = new Date(3_000_000L);

      assertEquals(d3, percentileOf(100, d2, d3, d1));
      assertEquals(d1, percentileOf(0, d3, d1, d2));
      assertEquals(d2, percentileOf(50, d3, d1, d2));
   }

   @Test
   void getResult_timestampsOutOfOrder_picksByTime() {
      Timestamp t1 = Timestamp.valueOf("2024-01-01 08:00:00");
      Timestamp t2 = Timestamp.valueOf("2024-01-01 09:00:00");
      Timestamp t3 = Timestamp.valueOf("2024-01-01 10:00:00");

      assertEquals(t3, percentileOf(100, t2, t3, t1));
      assertEquals(t1, percentileOf(0, t3, t1, t2));
      assertEquals(t2, percentileOf(50, t3, t1, t2));
   }

   @Test
   void getResult_timesOutOfOrder_picksByTime() {
      Time t1 = Time.valueOf("08:00:00");
      Time t2 = Time.valueOf("09:00:00");
      Time t3 = Time.valueOf("10:00:00");

      assertEquals(t3, percentileOf(100, t2, t3, t1));
      assertEquals(t1, percentileOf(0, t3, t1, t2));
      assertEquals(t2, percentileOf(50, t3, t1, t2));
   }

   @Test
   void getResult_manyDatesInReverseOrder_picksByTime() {
      // 40 values so the sort goes past TimSort's binary-insertion threshold
      Object[] values = new Object[40];

      for(int i = 0; i < values.length; i++) {
         values[i] = new java.sql.Date((values.length - i) * 86_400_000L);
      }

      assertEquals(new java.sql.Date(86_400_000L), percentileOf(0, values));
      assertEquals(new java.sql.Date(40 * 86_400_000L), percentileOf(100, values));
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_preservesDefaultResult() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_copiesObjectValues() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(30.0));
      formula.addValue(Double.valueOf(10.0));
      formula.addValue(Double.valueOf(20.0));
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      Object result = cloned.getResult();
      assertNotNull(result);
      assertEquals(10.0, ((Number) result).doubleValue(), 1e-10);
   }

   @Test
   void clone_isIndependentOfOriginal() {
      PthPercentileFormula formula = new PthPercentileFormula(0);
      formula.addValue(Double.valueOf(10.0));
      PthPercentileFormula cloned = (PthPercentileFormula) formula.clone();
      formula.reset();
      // cloned still has its value
      Object result = cloned.getResult();
      assertNotNull(result);
   }

   // -----------------------------------------------------------------------
   // defaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      PthPercentileFormula formula = new PthPercentileFormula(50);
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   @Test
   void getResult_blankStrings_areSkipped() {
      PthPercentileFormula withBlank = new PthPercentileFormula(50);
      PthPercentileFormula without = new PthPercentileFormula(50);

      for(String v : new String[] { "1", "", "  ", "3", "5" }) {
         withBlank.addValue((Object) v);
      }

      for(String v : new String[] { "1", "3", "5" }) {
         without.addValue((Object) v);
      }

      assertEquals(without.getResult(), withBlank.getResult());
   }

   // -----------------------------------------------------------------------
   // ported from 13.8 (gap:report.summary-formulas.statistical)
   // -----------------------------------------------------------------------

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   // gap:report.summary-formulas.statistical#pth-percentile-position
   @Test
   void getResult_objectValues_positionRoundsHalfUp() {
      // the position is round(p / 100 * (n - 1)) in the ascending order
      Object[] values = { 3.6, -9.3, -9.3, 10.0, 10.0, 6.0, 10.0 };
      int[] percentiles = { 0, 10, 25, 50, 60, 75, 100 };
      // positions 0, 1 (0.6), 2 (1.5 rounds up), 3, 4 (3.6), 5 (4.5 rounds up), 6
      Object[] atPosition = { -9.3, -9.3, 3.6, 6.0, 10.0, 10.0, 10.0 };

      for(int i = 0; i < percentiles.length; i++) {
         PthPercentileFormula pth = new PthPercentileFormula(percentiles[i]);
         addAll(pth, values);
         assertEquals(atPosition[i], pth.getResult(), "P" + percentiles[i]);
      }

      // a fraction below one half rounds down: P20 is position round(1.2) = 1, not 2
      PthPercentileFormula p20 = new PthPercentileFormula(20);
      addAll(p20, values);
      assertEquals(-9.3, p20.getResult());
      assertEquals(3.6, percentileOf(30, values), "P30 is position round(1.8) = 2");

      // four values: P50 is position round(1.5) = 2, the third value
      PthPercentileFormula p50 = new PthPercentileFormula(50);
      addAll(p50, 40, 10, 30, 20);
      assertEquals(30, p50.getResult());

      // P0 and P100 are the extremes, and the percentage is limited to 0..100
      assertEquals(10, percentileOf(0, 40, 10, 30, 20));
      assertEquals(40, percentileOf(100, 40, 10, 30, 20));
      assertEquals(40, percentileOf(250, 40, 10, 30, 20));
      assertEquals(10, percentileOf(-30, 40, 10, 30, 20));

      // one value is every percentile
      assertEquals(5, percentileOf(37, 5));

      // numeric strings are ordered by value, not by text
      assertEquals("9", percentileOf(0, "10", "9", "100"));
      assertEquals("10", percentileOf(50, "10", "9", "100"));
      assertEquals("100", percentileOf(100, "10", "9", "100"));
   }

   // Pth Percentile of dates that are already in order
   @Test
   void getResult_datesInOrder_nullIgnored() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      assertEquals(late, percentileOf(100, early, middle, late, null));
      assertEquals(early, percentileOf(20, early, late, late));
      assertEquals(early, percentileOf(0, early, middle, late));
   }

   // gap:report.summary-formulas.statistical#pth-percentile-null-and-text
   @Test
   void getResult_nullAndText() {
      // nulls are ignored: seven values remain, P10 is position round(0.6) = 1
      PthPercentileFormula pth = new PthPercentileFormula(10);
      addAll(pth, "a", "A", "3", "7", "", "A", "3", null);
      assertEquals("A", pth.getResult());

      // only nulls
      PthPercentileFormula nulls = new PthPercentileFormula(50);
      addAll(nulls, null, null, null, null);
      assertNull(nulls.getResult());
      nulls.setDefaultResult(true);
      assertEquals(0.0, nulls.getResult());

      // a null does not take a position among the values
      assertEquals(30, percentileOf(100, 10, null, 20, null, 30));
      assertEquals(10, percentileOf(0, null, 10, 20, 30));
   }

   // gap:report.summary-formulas.statistical#pth-percentile-primitive-values
   @Test
   void getResult_primitiveValues_keepTypeAndOrder() {
      int[] ints = { 50, 10, 40, 20, 30 };

      for(int percentile : new int[] { 0, 25, 50, 75, 100 }) {
         PthPercentileFormula typed = new PthPercentileFormula(percentile);
         PthPercentileFormula boxed = new PthPercentileFormula(percentile);

         for(int value : ints) {
            typed.addValue(value);
            boxed.addValue(Integer.valueOf(value));
         }

         assertEquals(boxed.getResult(), typed.getResult(), "P" + percentile);
      }

      PthPercentileFormula p75 = new PthPercentileFormula(75);

      for(int value : ints) {
         p75.addValue(value);
      }

      assertEquals(40, p75.getResult());

      // each primitive type keeps its own type and sorts its values
      PthPercentileFormula longs = new PthPercentileFormula(100);
      longs.addValue(5L);
      longs.addValue(9_000_000_000L);
      longs.addValue(7L);
      assertEquals(9_000_000_000L, longs.getResult());

      PthPercentileFormula shorts = new PthPercentileFormula(0);
      shorts.addValue((short) 5);
      shorts.addValue((short) -3);
      shorts.addValue((short) 7);
      assertEquals((short) -3, shorts.getResult());

      PthPercentileFormula floats = new PthPercentileFormula(50);
      floats.addValue(2.5f);
      floats.addValue(0.5f);
      floats.addValue(1.5f);
      assertEquals(1.5f, floats.getResult());

      PthPercentileFormula doubles = new PthPercentileFormula(50);
      doubles.addValue(new double[] { 3.0 });
      doubles.addValue(new double[] { 1.0 });
      doubles.addValue(new double[] { 2.0 });
      assertEquals(2.0, doubles.getResult());

      // the null markers of the primitive types are not added
      PthPercentileFormula nulls = new PthPercentileFormula(0);
      nulls.addValue(Tool.NULL_INTEGER);
      nulls.addValue(Tool.NULL_LONG);
      nulls.addValue(Tool.NULL_SHORT);
      nulls.addValue(Tool.NULL_FLOAT);
      nulls.addValue(Tool.NULL_DOUBLE);
      nulls.addValue(new double[] { Tool.NULL_DOUBLE });
      assertNull(nulls.getResult());
      nulls.addValue(4);
      nulls.addValue(Tool.NULL_INTEGER);
      assertEquals(4, nulls.getResult());

      // reset drops the typed lists
      p75.reset();
      assertNull(p75.getResult());
   }

   // Bug #80539: Pth(0) and Pth(100) of unordered dates are the extremes
   @Test
   void getResult_dateExtremes_keepTypeAndCompareByTime() {
      Date a = new Date(1_000_000L);
      Timestamp ta = new Timestamp(10_000L);
      Timestamp tb = new Timestamp(20_000L);
      Timestamp tc = new Timestamp(30_000L);
      assertInstanceOf(Timestamp.class, percentileOf(100, tb, tc, ta));
      // a Date and a Timestamp compare by time
      assertEquals(a, percentileOf(100, tc, a, tb));
   }

   // Bug #80697: Pth Percentile skips a blank string like Sum
   @Test
   void getResult_blankStrings_skippedAtExtremes() {
      PthPercentileFormula p100 = new PthPercentileFormula(100);
      addAll(p100, "1", "", "3");
      assertEquals("3", p100.getResult());

      PthPercentileFormula p0 = new PthPercentileFormula(0);
      addAll(p0, "", "4", " ", "2");
      assertEquals("2", p0.getResult());

      // a clone keeps the values that were kept
      PthPercentileFormula copy = (PthPercentileFormula) p0.clone();
      assertEquals("2", copy.getResult());

      // only blank strings leave nothing to calculate
      PthPercentileFormula blanks = new PthPercentileFormula(50);
      addAll(blanks, "", " ");
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0.0, blanks.getResult());
   }

   // Bug #80697: blank strings change nothing, and other values are unchanged
   @Test
   void getResult_blankStrings_changeNothingInRandomLists() {
      Object[] pool = { null, "", " ", "1", "2.5", "-4", "abc", 3, 7.5, 2L };
      Random random = new Random(80697L);

      for(int i = 0; i < 3000; i++) {
         List<Object> values = new ArrayList<>();
         List<Object> noBlanks = new ArrayList<>();
         int size = random.nextInt(8);

         for(int j = 0; j < size; j++) {
            Object value = pool[random.nextInt(pool.length)];
            values.add(value);

            if(!(value instanceof String) || !((String) value).trim().isEmpty()) {
               noBlanks.add(value);
            }
         }

         int percentile = random.nextInt(101);
         PthPercentileFormula pth = new PthPercentileFormula(percentile);
         PthPercentileFormula pthNoBlanks = new PthPercentileFormula(percentile);
         values.forEach(pth::addValue);
         noBlanks.forEach(pthNoBlanks::addValue);
         assertEquals(pthNoBlanks.getResult(), pth.getResult(), values + " p" + percentile);
      }
   }
}
