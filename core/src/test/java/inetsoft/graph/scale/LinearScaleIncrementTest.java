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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79661: the major increment must be honoured when the axis max is not positive.
 */
@Tag("core")
class LinearScaleIncrementTest {
   private static final int BAR = Scale.TICKS | Scale.ZERO;
   private static final int LINE = Scale.TICKS;

   private static double[] ticks(int option, double lo, double hi, Number inc,
                                 Double umin, Double umax)
   {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { lo }, { hi }
      });
      LinearScale scale = new LinearScale("v");
      scale.setScaleOption(option);

      if(inc != null) {
         scale.setIncrement(inc);
      }

      if(umin != null) {
         scale.setMin(umin);
      }

      if(umax != null) {
         scale.setMax(umax);
      }

      scale.init(data);
      return scale.getTicks();
   }

   private static void assertTicks(double[] expected, double[] actual) {
      assertArrayEquals(expected, actual, 1E-6, java.util.Arrays.toString(actual));
   }

   @Test
   void allNegativeBarHonoursIncrement() {
      assertTicks(new double[] { -100, -75, -50, -25, 0 }, ticks(BAR, -80, -10, 25, null, null));
   }

   @Test
   void allNegativeLineHonoursIncrement() {
      assertTicks(new double[] { -100, -75, -50, -25, 0 },
                  ticks(LINE, -80, -10, 25, null, null));
   }

   @Test
   void allNegativeSmallIncrements() {
      double[] t = ticks(BAR, -80, -10, 15, null, null);
      assertEquals(0, t[t.length - 1], 1E-9);
      assertEquals(15, t[1] - t[0], 1E-9);
      assertEquals(0, t[t.length - 1] % 15, 1E-9);
   }

   @Test
   void allNegativeUserMax() {
      // like positive axes, the last tick is the first grid tick at or above the user max
      assertTicks(new double[] { -100, -75, -50, -25, 0 },
                  ticks(BAR, -100, -10, 25, null, -20.0));
   }

   @Test
   void mixedSignLargeIncrementHasSaneTickCount() {
      double[] t = ticks(BAR, -1000, 1, 600, null, null);
      assertTicks(new double[] { -1200, -600, 0, 600 }, t);
   }

   @Test
   void mixedSignHonouredBeforeStaysHonoured() {
      double[] t = ticks(BAR, -100, 5, 20, null, null);
      assertEquals(20, t[1] - t[0], 1E-9);
   }

   @Test
   void positiveControlsUnchanged() {
      assertTicks(new double[] { 0, 25, 50, 75, 100 }, ticks(BAR, 10, 80, 25, null, null));
      assertTicks(new double[] { 0, 15, 30, 45, 60, 75, 90 },
                  ticks(BAR, 10, 80, 15, null, null));
      double[] t = ticks(BAR, 10, 80, 5, null, null);
      assertEquals(0, t[0], 1E-9);
      assertEquals(5, t[1] - t[0], 1E-9);
      assertEquals(80, t[t.length - 1], 1E-9);
   }

   @Test
   void offsetPositiveDataUnchanged() {
      // not part of the bug: an increment larger than the span of offset data keeps
      // being accepted on the max coordinate
      assertTicks(new double[] { 1000, 2000 }, ticks(LINE, 1000, 1010, 1000, null, null));
   }
}
