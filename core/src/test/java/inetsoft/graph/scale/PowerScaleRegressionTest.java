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
package inetsoft.graph.scale;

import inetsoft.graph.data.DefaultDataSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for PowerScale tick generation (#82020, #82021) and NaN mapping (#82022).
 */
@Tag("core")
class PowerScaleRegressionTest {
   private static final double DELTA = 1e-9;

   // ---- #82020: a wide power-space range is thinned, not rejected ----

   @Test
   void wideUserRangeIsThinned() {
      PowerScale s = new PowerScale(0, 4e6); // power space 0..2000
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertAscending(ticks, 2, 10);
      assertEquals(0, ticks[0]);
      assertEquals(2000, ticks[ticks.length - 1], DELTA);
   }

   @Test
   void wideDataRangeIsThinned() {
      PowerScale s = new PowerScale("v");
      s.init(column(0, 1e6, 2e6));
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertAscending(ticks, 2, 10);
      assertTrue(ticks[ticks.length - 1] >= Math.sqrt(2e6), Arrays.toString(ticks));
      assertEquals(ticks[ticks.length - 1], s.getMax());
   }

   @Test
   void rangeAboveIntIsThinned() {
      PowerScale s = new PowerScale(0, 1e5); // power space 0..1e10, past Integer.MAX_VALUE
      s.setExponent(2);
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertAscending(ticks, 2, 10);
      assertEquals(1e10, ticks[ticks.length - 1]);
   }

   @Test
   void rangeAboveLongIsThinned() {
      PowerScale s = new PowerScale(0, 1e15); // power space 0..1e30, past Long.MAX_VALUE
      s.setExponent(2);
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertAscending(ticks, 2, 10);
      assertEquals(1e30, ticks[ticks.length - 1]);
   }

   @Test
   void infiniteRangeIsStillRejected() {
      PowerScale s = new PowerScale(0, 10);
      s.setExponent(1000);
      RuntimeException ex = assertThrows(RuntimeException.class, s::getTicks);
      assertTrue(ex.getMessage().contains("Exponent is too large"), ex.getMessage());
   }

   @Test
   void reversedRangeIsStillRejected() {
      PowerScale s = new PowerScale(0.001, 100); // power space 1000..0.01
      s.setExponent(-1);
      assertThrows(RuntimeException.class, s::getTicks);
   }

   // ---- #82021: an explicit min/max is kept as the end tick and the scale range ----

   @Test
   void userMaxIsLastTickAndMax() {
      PowerScale s = new PowerScale(0, 100);
      s.setExponent(1);
      double[] ticks = s.getTicks();
      assertArrayEquals(new double[] { 0, 17, 34, 51, 68, 85, 100 }, ticks, DELTA);
      // RectCoord compares the last tick with getMax() using ==
      assertEquals(100.0, s.getMax());
      assertEquals(s.getMax(), ticks[ticks.length - 1]);
   }

   @Test
   void userMinIsFirstTickAndMin() {
      PowerScale s = new PowerScale(3, 100);
      s.setExponent(1);
      double[] ticks = s.getTicks();
      assertEquals(3.0, ticks[0]);
      assertEquals(3.0, s.getMin());
      assertEquals(100.0, ticks[ticks.length - 1]);
      assertEquals(100.0, s.getMax());
   }

   @Test
   void userMaxIsComparedInPowerSpace() {
      PowerScale s = new PowerScale(0, 50);
      double[] ticks = s.getTicks();
      assertEquals(Math.sqrt(50), ticks[ticks.length - 1]);
      assertEquals(Math.sqrt(50), s.getMax());
   }

   @Test
   void userMaxOnReversedScale() {
      PowerScale s = new PowerScale(0, 100);
      s.setExponent(1);
      s.setReversed(true);
      double[] ticks = s.getTicks();
      assertEquals(100.0, ticks[0]);
      assertEquals(0.0, ticks[ticks.length - 1]);
      assertEquals(100.0, s.getMin());
   }

   @Test
   void ticksStayInRangeWithoutTicksOption() {
      PowerScale s = new PowerScale("v");
      s.setExponent(1);
      s.setScaleOption(0);
      s.init(column(3, 50, 97));
      double[] ticks = s.getTicks();

      for(double tick : ticks) {
         assertTrue(tick >= s.getMin() && tick <= s.getMax(),
                    Arrays.toString(ticks) + " outside " + s.getMin() + ".." + s.getMax());
      }

      assertEquals(3.0, ticks[0]);
      assertEquals(97.0, ticks[ticks.length - 1]);
   }

   @Test
   void dataRangeStillRoundsOutward() {
      PowerScale s = new PowerScale("v");
      s.setExponent(1);
      s.init(column(0, 100));
      assertArrayEquals(new double[] { 0, 17, 34, 51, 68, 85, 102 }, s.getTicks(), DELTA);
      assertEquals(102.0, s.getMax());
   }

   // ---- a bound end tick is never left next to a rounded tick it nearly equals ----

   @Test
   void wideUserRangeMinorTicksEnd() {
      PowerScale s = new PowerScale(0, 131077);
      s.setExponent(3);
      double[] ticks = assertDoesNotThrow(s::getTicks);
      // getMinorTicks() is on the default axis paint path; a near-zero end gap made it loop
      // until the heap ran out
      assertTimeoutPreemptively(Duration.ofSeconds(5), s::getMinorTicks);
      assertNoCloseTicks(ticks);
      assertEquals(s.getMax(), ticks[ticks.length - 1]);
   }

   @Test
   void wideUserRangeHasNoDuplicateEndLabel() {
      PowerScale s = new PowerScale(0, 2453);
      s.setExponent(2);
      Object[] values = assertDoesNotThrow(s::getValues);
      double last = (Double) values[values.length - 1];
      double prev = (Double) values[values.length - 2];
      assertNotEquals(Math.round(last), Math.round(prev), Arrays.toString(values));
      assertNoCloseTicks(s.getTicks());
   }

   @Test
   void closeUserMinTickIsMerged() {
      PowerScale s = new PowerScale(13, 100); // step 15
      s.setExponent(1);
      // the rounded tick 15 is 2 above the bound 13 and is dropped
      assertArrayEquals(new double[] { 13, 30, 45, 60, 75, 90, 100 }, s.getTicks(), DELTA);
      assertEquals(13.0, s.getMin());
      assertEquals(100.0, s.getMax());
   }

   @Test
   void closeUserMaxTickIsMerged() {
      PowerScale s = new PowerScale(16, 100); // step 14
      s.setExponent(1);
      // the rounded tick 98 is 2 below the bound 100 and is dropped
      assertArrayEquals(new double[] { 16, 28, 42, 56, 70, 84, 100 }, s.getTicks(), DELTA);
      assertEquals(16.0, s.getMin());
      assertEquals(100.0, s.getMax());
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0.5, 1.5, 2, 2.74, 2.93, 3 })
   void boundEndTicksAreNeverTooClose(double exponent) {
      assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
         for(int m = 1; m <= 300000; m += 37) {
            PowerScale user = new PowerScale(0, m);
            user.setExponent(exponent);
            checkEndTicks(user, "user max " + m);

            // TICKS off: the data range is the bound
            PowerScale data = new PowerScale("v");
            data.setExponent(exponent);
            data.setScaleOption(Scale.ZERO);
            data.init(column(0, m));
            checkEndTicks(data, "data max " + m);
         }
      });
   }

   // ---- an inverted power-space range (negative exponent) keeps today's output ----

   @Test
   void negativeExponentUserBoundsUnchanged() {
      PowerScale s = new PowerScale(379, 566344);
      s.setExponent(-1);
      assertArrayEquals(new double[] { 0.0, 1.0 }, s.getTicks());
      assertEquals(0.0, s.getMin());
      assertEquals(1.0, s.getMax());
   }

   // ---- #82022: NaN maps to NaN, as on LinearScale and LogScale ----

   @ParameterizedTest
   @ValueSource(doubles = { 0.5, 2, 0 })
   void mapNaNIsNaN(double exponent) {
      PowerScale s = new PowerScale();
      s.setExponent(exponent);
      assertTrue(Double.isNaN(s.map(Double.NaN)), "map(NaN) = " + s.map(Double.NaN));
      assertTrue(Double.isNaN(s.map((Object) null)));
   }

   @Test
   void addTreatsNaNAsZero() {
      PowerScale s = new PowerScale();
      assertEquals(2.0, s.add(2, Double.NaN), DELTA);
      assertEquals(2.0, s.add(Double.NaN, 2), DELTA);
      assertEquals(-2.0, s.add(-2, Double.NaN), DELTA);
      assertEquals(0.0, s.add(Double.NaN, Double.NaN), DELTA);
   }

   private static void assertAscending(double[] ticks, int minCount, int maxCount) {
      String msg = Arrays.toString(ticks);
      assertTrue(ticks.length >= minCount && ticks.length <= maxCount, msg);

      for(int i = 1; i < ticks.length; i++) {
         assertTrue(ticks[i] > ticks[i - 1], msg);
      }
   }

   private static void checkEndTicks(PowerScale s, String msg) {
      double[] ticks;

      try {
         ticks = s.getTicks();
      }
      catch(RuntimeException ex) {
         return; // still rejected, as before (e.g. an overflowing bound)
      }

      assertNoCloseTicks(ticks);
      assertEquals(s.getMax(), ticks[ticks.length - 1], msg);
      assertEquals(s.getMin(), ticks[0], msg);
      s.getMinorTicks();
   }

   // no two adjacent ticks closer than half the regular tick step
   private static void assertNoCloseTicks(double[] ticks) {
      double step = 0;

      for(int i = 1; i < ticks.length; i++) {
         step = Math.max(step, ticks[i] - ticks[i - 1]);
      }

      for(int i = 1; i < ticks.length; i++) {
         // an end gap may be up to 1.5 steps once a close tick is merged into the bound
         assertTrue(ticks[i] - ticks[i - 1] >= step / 3, Arrays.toString(ticks));
      }
   }

   private static DefaultDataSet column(double... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "v" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }
}
