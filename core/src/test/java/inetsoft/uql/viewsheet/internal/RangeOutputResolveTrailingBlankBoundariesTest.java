/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78270 (P2): pure tests for
 * {@code RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries}.
 */
@Tag("core")
class RangeOutputResolveTrailingBlankBoundariesTest {
   private static final double NAN = Double.NaN;

   @Test
   void trailingNaNBecomesMax() {
      assertArrayEquals(new double[] { 60, 90, 100, 100 },
                        RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(
                           new double[] { 60, 90, NAN, NAN }, 100));
   }

   @Test
   void interiorNaNIsLeftAlone() {
      double[] r = RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(
         new double[] { 60, NAN, 90, NAN }, 100);
      assertEquals(60, r[0]);
      assertTrue(Double.isNaN(r[1]));
      assertEquals(90, r[2]);
      assertEquals(100, r[3]);
   }

   @Test
   void allNaNBecomesMax() {
      assertArrayEquals(new double[] { 100, 100 },
                        RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(
                           new double[] { NAN, NAN }, 100));
   }

   @Test
   void noNaNIsUnchangedAndInputIsNotMutated() {
      double[] in = { 60, 90, 100 };
      double[] out = RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(in, 100);
      assertArrayEquals(in, out);
      assertNotSame(in, out);

      double[] withNaN = { 60, NAN };
      RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(withNaN, 100);
      assertTrue(Double.isNaN(withNaN[1]));
   }

   @Test
   void emptyAndNullAreSafe() {
      assertEquals(0, RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(
         new double[0], 100).length);
      assertNull(RangeOutputVSAssemblyInfo.resolveTrailingBlankBoundaries(null, 100));
   }
}
