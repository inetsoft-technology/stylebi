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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LogScaleTest {

   private static final double DELTA = 1e-6;

   // ---- setBase validation ----

   @Test
   void setBaseRejectsZero() {
      LogScale scale = new LogScale();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> scale.setBase(0));
      assertTrue(ex.getMessage().contains("positive"),
         "Expected message about positive number, got: " + ex.getMessage());
   }

   @Test
   void setBaseRejectsNegative() {
      LogScale scale = new LogScale();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> scale.setBase(-5));
      assertTrue(ex.getMessage().contains("positive"),
         "Expected message about positive number, got: " + ex.getMessage());
   }

   @Test
   void setBase_guardOnlyRejectsNonPositiveSoOneIsAccepted() {
      // base=1 is mathematically undefined (log₁(x) is undefined for all x),
      // but the implementation guard only rejects base <= 0, so 1 is accepted.
      // This test documents the current (lax) guard behavior. The guard should
      // ideally reject base <= 1.
      LogScale scale = new LogScale();
      scale.setBase(1);
      assertEquals(1, scale.getBase());
   }

   @ParameterizedTest
   @ValueSource(ints = {2, 10, 100})
   void setBaseAcceptsPositiveValues(int base) {
      LogScale scale = new LogScale();
      scale.setBase(base);
      assertEquals(base, scale.getBase());
   }

   @Test
   void defaultBaseIsTen() {
      LogScale scale = new LogScale();
      assertEquals(10, scale.getBase());
   }

   // ---- mapValue (via mapValue(Object)) ----

   @Test
   void mapValueBase10Returns1ForValue10() {
      LogScale scale = new LogScale();
      // log10(10) = 1.0
      assertEquals(1.0, scale.mapValue(Double.valueOf(10.0)), DELTA);
   }

   @Test
   void mapValueBase10Returns2ForValue100() {
      LogScale scale = new LogScale();
      // log10(100) = 2.0
      assertEquals(2.0, scale.mapValue(Double.valueOf(100.0)), DELTA);
   }

   @Test
   void mapValueBase10Returns3ForValue1000() {
      LogScale scale = new LogScale();
      assertEquals(3.0, scale.mapValue(Double.valueOf(1000.0)), DELTA);
   }

   @Test
   void mapValueBase2Returns3ForValue8() {
      LogScale scale = new LogScale();
      scale.setBase(2);
      // log2(8) = 3.0
      assertEquals(3.0, scale.mapValue(Double.valueOf(8.0)), DELTA);
   }

   @Test
   void mapValueBase2Returns4ForValue16() {
      LogScale scale = new LogScale();
      scale.setBase(2);
      assertEquals(4.0, scale.mapValue(Double.valueOf(16.0)), DELTA);
   }

   @Test
   void mapValueValueBetweenZeroAndOneIsClamped() {
      // Values in [0, 1] are treated as 1.0 before log: log10(1) = 0
      LogScale scale = new LogScale();
      assertEquals(0.0, scale.mapValue(Double.valueOf(0.5)), DELTA);
      assertEquals(0.0, scale.mapValue(Double.valueOf(0.0)), DELTA);
   }

   @Test
   void mapValueNegativeInputReturnsNegativeLog() {
      LogScale scale = new LogScale();
      // sign=-1, v=10.0, log10(10)=1.0, result=-1.0
      assertEquals(-1.0, scale.mapValue(Double.valueOf(-10.0)), DELTA);
   }

   @Test
   void mapValueNegativeSmallValueIsClamped() {
      LogScale scale = new LogScale();
      // v = -0.5: sign=-1, v=0.5 which is in [0,1] → clamped to 1.0 → log10(1)=0, result=-0
      assertEquals(-0.0, scale.mapValue(Double.valueOf(-0.5)), DELTA);
   }

   @Test
   void mapValueNullReturnsNaN() {
      LogScale scale = new LogScale();
      assertTrue(Double.isNaN(scale.mapValue((Object) null)));
   }

   @Test
   void mapValueNonNumberReturnsNaN() {
      LogScale scale = new LogScale();
      assertTrue(Double.isNaN(scale.mapValue("hello")));
   }

   // ---- unmap (getPowerValue) ----

   @Test
   void unmapBase10Exponent1Returns10() {
      LogScale scale = new LogScale();
      assertEquals(10.0, scale.unmap(1.0), DELTA);
   }

   @Test
   void unmapBase10Exponent2Returns100() {
      LogScale scale = new LogScale();
      assertEquals(100.0, scale.unmap(2.0), DELTA);
   }

   @Test
   void unmapBase10Exponent0Returns1() {
      LogScale scale = new LogScale();
      assertEquals(1.0, scale.unmap(0.0), DELTA);
   }

   @Test
   void unmapBase2Exponent3Returns8() {
      LogScale scale = new LogScale();
      scale.setBase(2);
      assertEquals(8.0, scale.unmap(3.0), DELTA);
   }

   @Test
   void unmapNegativeExponentBase10ReturnsNegativePower() {
      LogScale scale = new LogScale();
      // sign=-1, v=1.0, 10^1=10, result=-10
      assertEquals(-10.0, scale.unmap(-1.0), DELTA);
   }

   @Test
   void mapAndUnmapAreInverses() {
      LogScale scale = new LogScale();
      double original = 1000.0;
      double mapped = scale.mapValue(Double.valueOf(original));
      double unmapped = scale.unmap(mapped);
      assertEquals(original, unmapped, DELTA);
   }

   // ---- add() ----

   @Test
   void addTwoBase10LogValuesUnmapsAddsAndRemaps() {
      LogScale scale = new LogScale();
      // add(1.0, 2.0): unmap(1.0)=10, unmap(2.0)=100 → sum=110 → map(110) = log10(110)
      double result = scale.add(1.0, 2.0);
      assertEquals(Math.log10(110.0), result, DELTA);
   }

   @Test
   void addWithZeroV1ReturnsV2Mapped() {
      LogScale scale = new LogScale();
      // v1=0 is treated as 0; v2=unmap(2.0)=100; map(0+100) = map(100) = 2.0
      double result = scale.add(0.0, 2.0);
      assertEquals(2.0, result, DELTA);
   }

   @Test
   void addWithNaNTreatsItAsZero() {
      LogScale scale = new LogScale();
      // NaN treated as 0; result = map(0 + unmap(2.0)) = map(100) = 2.0
      double result = scale.add(Double.NaN, 2.0);
      assertEquals(2.0, result, DELTA);
   }

   @Test
   void addIsSymmetric() {
      LogScale scale = new LogScale();
      double r1 = scale.add(1.0, 2.0);
      double r2 = scale.add(2.0, 1.0);
      assertEquals(r1, r2, DELTA);
   }

   @Test
   void addBothZeroReturnsZero() {
      LogScale scale = new LogScale();
      // map(0+0) = map(0.0): 0 is in [0,1] → clamped to 1 → log10(1)=0
      double result = scale.add(0.0, 0.0);
      assertEquals(0.0, result, DELTA);
   }

   // ---- Initialized scale (with min/max) ----

   @Test
   void getValuesReturnsBaseRaisedToTickPowers() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         {"val"},
         {1.0},
         {10.0},
         {100.0},
         {1000.0}
      });
      LogScale scale = new LogScale("val");
      scale.init(data);
      Object[] values = scale.getValues();
      assertNotNull(values);
      assertTrue(values.length > 0);
      // All values should be powers of 10 (log10 is an integer)
      for(Object v : values) {
         double d = ((Number) v).doubleValue();
         double log = Math.log10(Math.abs(d));
         assertEquals(Math.round(log), log, DELTA,
            "Expected " + d + " to be an exact power of 10");
      }
   }

   // ---- Edge cases ----

   @Test
   void mapValueExactlyOne() {
      LogScale scale = new LogScale();
      // log10(1) = 0
      assertEquals(0.0, scale.mapValue(Double.valueOf(1.0)), DELTA);
   }

   @Test
   void mapValueLargeNumber() {
      LogScale scale = new LogScale();
      // log10(1_000_000) = 6
      assertEquals(6.0, scale.mapValue(Double.valueOf(1_000_000.0)), DELTA);
   }
}
