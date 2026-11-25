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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PowerScaleTest {
   private static final double DELTA = 1e-9;

   private static PowerScale scale(double exponent) {
      PowerScale s = new PowerScale();
      s.setExponent(exponent);
      return s;
   }

   private static DefaultDataSet column(double... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "v" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { values[i] };
      }

      return new DefaultDataSet(rows);
   }

   private static double[] doubles(Object[] values) {
      return Arrays.stream(values).mapToDouble(o -> (Double) o).toArray();
   }

   private static void assertArray(double[] expected, double[] actual) {
      assertArrayEquals(expected, actual, DELTA, "actual " + Arrays.toString(actual));
   }

   // ---- exponent and map ----

   @Test
   void exponentDefaultsToHalfAndIsSettable() {
      PowerScale s = new PowerScale();
      assertEquals(0.5, s.getExponent(), DELTA);
      s.setExponent(2);
      assertEquals(2.0, s.getExponent(), DELTA);
   }

   @ParameterizedTest(name = "exponent 0.5: map({0}) = {1}")
   @CsvSource({ "4.0, 2.0", "0.0, 0.0", "2.25, 1.5", "16.0, 4.0" })
   void mapWithDefaultExponent(double value, double expected) {
      assertEquals(expected, new PowerScale().map(value), DELTA);
   }

   @Test
   @DisplayName("negative values keep their sign")
   void mapNegativeKeepsSign() {
      assertEquals(-2.0, scale(0.5).map(-4.0), DELTA);
      assertEquals(-9.0, scale(2).map(-3.0), DELTA);
   }

   @ParameterizedTest(name = "exponent 1: map and unmap of {0} are the identity")
   @ValueSource(doubles = { -7, -0.25, 0, 3.5, 1e6 })
   void exponentOneIsIdentityLikeLinearScale(double value) {
      PowerScale s = scale(1);
      assertEquals(value, s.map(value), DELTA);
      assertEquals(value, s.unmap(value), DELTA);
      assertEquals(new LinearScale().map(value), s.map(value), DELTA);
   }

   @ParameterizedTest(name = "exponent {0}: map({1}) = {2}, unmap({3}) = {4}")
   @CsvSource({ "2, 3, 9, 9, 3", "3, 2, 8, 8, 2" })
   void integerExponents(double exp, double v, double mapped, double u, double unmapped) {
      PowerScale s = scale(exp);
      assertEquals(mapped, s.map(v), DELTA);
      assertEquals(unmapped, s.unmap(u), DELTA);
   }

   @Test
   @DisplayName("unmap(map(x)) returns x for several exponents and values")
   void roundTrip() {
      double[] exps = { 0.5, 2, 3, 1.0 / 3, 1, 0.25 };
      double[] xs = { -100, -1, -0.25, 0, 0.25, 1, 7, 1e6 };

      for(double exp : exps) {
         PowerScale s = scale(exp);

         for(double x : xs) {
            assertEquals(x, s.unmap(s.map(x)), 1e-9 * Math.max(1, Math.abs(x)),
                         "exponent " + exp + " x " + x);
         }
      }
   }

   @Test
   @DisplayName("map is monotonic across zero")
   void mapMonotonic() {
      double[] xs = { -50, -9, -1, -0.01, 0, 0.01, 1, 9, 50 };

      for(double exp : new double[] { 0.5, 2, 3 }) {
         PowerScale s = scale(exp);

         for(int i = 1; i < xs.length; i++) {
            assertTrue(s.map(xs[i - 1]) <= s.map(xs[i]), "exponent " + exp + " at " + xs[i]);
         }
      }
   }

   @Test
   @DisplayName("map of Object: null and non-numbers are NaN, Integer and Long are mapped")
   void mapObject() {
      PowerScale s = new PowerScale();
      assertTrue(Double.isNaN(s.map((Object) null)));
      assertTrue(Double.isNaN(s.map("abc")));
      assertEquals(2.0, s.map(Integer.valueOf(4)), DELTA);
      assertEquals(3.0, s.map(Long.valueOf(9)), DELTA);
   }

   @Test
   @DisplayName("map(NaN) is NaN like null, LinearScale and LogScale")
   void mapNaN() {
      assertTrue(Double.isNaN(new PowerScale().map(Double.NaN)));
   }

   @Test
   void mapInfinity() {
      PowerScale s = new PowerScale();
      assertEquals(Double.POSITIVE_INFINITY, s.map(Double.POSITIVE_INFINITY));
      assertEquals(Double.NEGATIVE_INFINITY, s.map(Double.NEGATIVE_INFINITY));
   }

   @Test
   void negativeExponent() {
      PowerScale s = scale(-1);
      assertEquals(0.5, s.map(2.0), DELTA);
      assertEquals(-0.5, s.map(-2.0), DELTA);
      assertEquals(2.0, s.unmap(0.5), DELTA);
   }

   // ---- add ----

   @Test
   @DisplayName("add of mapped values equals the map of the summed raw values")
   void add() {
      PowerScale half = scale(0.5);
      assertEquals(half.map(13.0), half.add(half.map(4.0), half.map(9.0)), DELTA);
      assertEquals(half.map(5.0), half.add(half.map(-4.0), half.map(9.0)), DELTA);
      assertEquals(half.map(5.0), half.add(half.map(0.0), half.map(5.0)), DELTA);
      PowerScale two = scale(2);
      assertEquals(two.map(7.0), two.add(two.map(3.0), two.map(4.0)), DELTA);
   }

   @Test
   @DisplayName("add treats NaN as 0 like LogScale.add")
   void addNaN() {
      PowerScale s = new PowerScale();
      assertEquals(3.0, s.add(Double.NaN, s.map(9.0)), DELTA);
   }

   // ---- ticks ----

   @Test
   void ticksAndValuesWithUserRange() {
      PowerScale s = new PowerScale(0, 16);
      double[] ticks = s.getTicks();
      assertArray(new double[] { 0, 1, 2, 3, 4 }, ticks);
      double[] values = doubles(s.getValues());
      assertArray(new double[] { 0, 1, 4, 9, 16 }, values);

      for(int i = 0; i < values.length; i++) {
         assertEquals(ticks[i], s.map(values[i]), DELTA);
      }

      assertEquals(0, s.getMin(), DELTA);
      assertEquals(4, s.getMax(), DELTA);
   }

   @Test
   void ticksAroundZero() {
      PowerScale s = new PowerScale(-16, 16);
      assertArray(new double[] { -4, -2, 0, 2, 4 }, s.getTicks());
      assertArray(new double[] { -16, -4, 0, 4, 16 }, doubles(s.getValues()));
   }

   @Test
   @DisplayName("ticks are thinned to about six")
   void tickThinning() {
      PowerScale s = new PowerScale(0, 60);
      s.setExponent(1);
      assertArray(new double[] { 0, 10, 20, 30, 40, 50, 60 }, s.getTicks());
   }

   @Test
   @DisplayName("scale without a range returns the single tick min")
   void uninitializedScale() {
      PowerScale s = new PowerScale();
      assertArray(new double[] { 0 }, s.getTicks());
      assertArray(new double[] { 0 }, doubles(s.getValues()));

      PowerScale same = new PowerScale(4, 4);
      assertArray(new double[] { 2 }, same.getTicks());
      assertArray(new double[] { 4 }, doubles(same.getValues()));
   }

   @Test
   void initFromData() {
      PowerScale s = new PowerScale("v");
      s.init(column(4, 9, 16));
      assertEquals(0, s.getMin(), DELTA);
      assertEquals(4, s.getMax(), DELTA);
      assertArray(new double[] { 0, 1, 2, 3, 4 }, s.getTicks());
      assertArray(new double[] { 0, 1, 4, 9, 16 }, doubles(s.getValues()));
      assertEquals(4, s.getUnitCount());
   }

   @Test
   @DisplayName("init rounds the mapped maximum up to a whole tick")
   void initRoundsUp() {
      PowerScale s = new PowerScale("v");
      s.init(column(5, 10));
      assertEquals(4, s.getMax(), DELTA);
      assertEquals(0, s.getMin(), DELTA);
      double[] values = doubles(s.getValues());
      assertEquals(16, values[values.length - 1], DELTA);
   }

   @Test
   void initNegativeData() {
      PowerScale s = new PowerScale("v");
      s.init(column(-9, 4));
      assertArray(new double[] { -3, -2, -1, 0, 1, 2 }, s.getTicks());
      assertArray(new double[] { -9, -4, -1, 0, 1, 4 }, doubles(s.getValues()));
   }

   @Test
   @DisplayName("without the ZERO option the ticks start at the data minimum")
   void noZeroOption() {
      PowerScale s = new PowerScale("v");
      s.setScaleOption(0);
      s.init(column(4, 9, 16));
      assertArray(new double[] { 2, 3, 4 }, s.getTicks());
      assertArray(new double[] { 4, 9, 16 }, doubles(s.getValues()));
   }

   @Test
   @DisplayName("an explicit maximum is not exceeded by any tick")
   void userMaxRespected() {
      PowerScale s = new PowerScale(0, 100);
      s.setExponent(1);
      double[] ticks = s.getTicks();
      assertTrue(ticks[ticks.length - 1] <= 100, "ticks " + Arrays.toString(ticks));
   }

   @Test
   @DisplayName("large range with the default exponent gives a short tick list")
   void largeRange() {
      PowerScale s = new PowerScale(0, 4e6);
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertTrue(ticks.length <= 10, "tick count " + ticks.length);

      for(int i = 1; i < ticks.length; i++) {
         assertTrue(ticks[i] > ticks[i - 1]);
      }

      assertTrue(ticks[ticks.length - 1] >= 2000);
   }

   @Test
   @DisplayName("large data with the default exponent does not throw")
   void largeData() {
      PowerScale s = new PowerScale("v");
      s.init(column(0, 1e6, 2e6));
      double[] ticks = assertDoesNotThrow(s::getTicks);
      assertTrue(ticks[ticks.length - 1] >= Math.sqrt(2e6));
   }

   @Test
   @DisplayName("absurd exponent is rejected with a message about the exponent")
   void absurdExponent() {
      PowerScale s = new PowerScale(0, 10);
      s.setExponent(1000);
      RuntimeException ex = assertThrows(RuntimeException.class, s::getTicks);
      assertTrue(ex.getMessage().contains("Exponent is too large"), ex.getMessage());
   }

   @Test
   @DisplayName("minor ticks sit at the midpoints of the raw value intervals")
   void minorTicks() {
      PowerScale s = new PowerScale(0, 16);
      double[] ticks = s.getTicks();
      double[] minor = s.getMinorTicks();
      assertArray(new double[] { Math.sqrt(0.5), Math.sqrt(2.5), Math.sqrt(6.5),
                                 Math.sqrt(12.5) }, minor);

      for(int i = 0; i < minor.length; i++) {
         assertTrue(minor[i] > ticks[i] && minor[i] < ticks[i + 1]);
      }
   }

   @Test
   void minorTicksNullForSingleTick() {
      assertNull(new PowerScale().getMinorTicks());
   }

   @Test
   void reversedTicks() {
      PowerScale forward = new PowerScale(0, 16);
      PowerScale reversed = new PowerScale(0, 16);
      reversed.setReversed(true);
      double[] f = forward.getTicks();
      double[] expected = new double[f.length];

      for(int i = 0; i < f.length; i++) {
         expected[i] = f[f.length - 1 - i];
      }

      assertArray(new double[] { 4, 3, 2, 1, 0 }, reversed.getTicks());
      assertArray(expected, reversed.getTicks());
   }

   // ---- equals and clone ----

   @Test
   void cloneKeepsExponent() {
      PowerScale s = scale(3);
      PowerScale copy = assertInstanceOf(PowerScale.class, s.clone());
      assertEquals(3.0, copy.getExponent(), DELTA);
   }
}
