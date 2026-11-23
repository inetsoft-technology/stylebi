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
package inetsoft.graph.guide.form;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ConfidenceIntervalStrategyTest {
   private static final double N = -Double.MAX_VALUE;

   @Test
   void nullPlaceholdersAreIgnored() {
      double[] expected = new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[] { 1, 2, 3, 4, 5 });
      double[] actual = new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[] { N, 1, 2, 3, 4, 5, N });

      assertEquals(2, actual.length);
      assertEquals(1.0368, actual[0], 1e-3);
      assertEquals(4.9632, actual[1], 1e-3);
      assertArrayEquals(expected, actual, 1e-9);
   }

   @Test
   void sparseData() {
      double[] actual = new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[] { N, 10, N, 20 });

      assertEquals(2, actual.length);
      assertEquals(-48.5310, actual[0], 1e-3);
      assertEquals(78.5310, actual[1], 1e-3);
   }

   @Test
   void fewerThanTwoValuesGiveNoLines() {
      assertEquals(0, new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[] { N, N, 5 }).length);
      assertEquals(0, new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[] { N, N, N }).length);
      assertEquals(0, new ConfidenceIntervalStrategy(95)
         .calculateBoundaries(new double[0]).length);
   }
}
