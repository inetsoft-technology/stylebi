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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DefaultDataSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for 76647/VCA-002: a LinearSizeFrame bound to a field whose value is
 * identical across every row (a degenerate, min == max domain) must render every mark at
 * the same fixed, neutral size, instead of the axis-style zero-based "nice tick" widened
 * ratio LinearScale otherwise produces (which is a real, distinct defect from the
 * originally-reported per-mark divergent-size symptom -- see 03-fix.md).
 */
@Tag("core")
class LinearSizeFrameTest {
   private static final double DELTA = 1e-6;

   @Test
   void degenerateNonZeroDomainCollapsesToFixedNeutralSizeForEveryMark() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         {"val"},
         {35.0},
         {35.0},
         {35.0},
      });

      LinearSizeFrame frame = new LinearSizeFrame("val");
      frame.init(data);

      double expected = (frame.getSmallest() + frame.getLargest()) / 2;

      for(int row = 0; row < 3; row++) {
         assertEquals(expected, frame.getSize(data, "val", row), DELTA,
            "every mark should get the same fixed neutral size for a degenerate domain");
      }
   }

   @Test
   void degenerateZeroDomainAlsoCollapsesToFixedNeutralSize() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         {"val"},
         {0.0},
         {0.0},
      });

      LinearSizeFrame frame = new LinearSizeFrame("val");
      frame.init(data);

      double expected = (frame.getSmallest() + frame.getLargest()) / 2;

      assertEquals(expected, frame.getSize(data, "val", 0), DELTA);
      assertEquals(expected, frame.getSize(data, "val", 1), DELTA);
   }

   @Test
   void nonDegenerateDomainStillProducesRatioBasedVaryingSizes() {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         {"val"},
         {0.0},
         {50.0},
         {100.0},
      });

      LinearSizeFrame frame = new LinearSizeFrame("val");
      frame.init(data);

      double sizeMin = frame.getSize(data, "val", 0);
      double sizeMid = frame.getSize(data, "val", 1);
      double sizeMax = frame.getSize(data, "val", 2);

      assertTrue(sizeMin < sizeMid, "size should strictly increase with the bound value");
      assertTrue(sizeMid < sizeMax, "size should strictly increase with the bound value");
   }
}
