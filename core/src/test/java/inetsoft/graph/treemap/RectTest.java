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
package inetsoft.graph.treemap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class RectTest {
   @ParameterizedTest
   @CsvSource({ "2, 4, 2.0", "4, 2, 2.0", "3, 3, 1.0" })
   void aspectRatioIsAlwaysAtLeastOne(double w, double h, double expected) {
      assertEquals(expected, new Rect(0, 0, w, h).aspectRatio(), 1e-12);
   }

   @Test
   void distanceIsEuclideanOverAllFourComponents() {
      assertEquals(5.0, new Rect(0, 0, 0, 0).distance(new Rect(3, 4, 0, 0)), 1e-12);
      Rect r = new Rect(1, 2, 3, 4);
      assertEquals(0.0, r.distance(new Rect(1, 2, 3, 4)), 0);
   }

   @Test
   void copyAndCopyConstructorAreIndependent() {
      Rect original = new Rect(1, 2, 3, 4);
      Rect copy = original.copy();
      Rect viaCtor = new Rect(original);
      copy.x = 10;
      viaCtor.w = 20;

      assertRect("original", 1, 2, 3, 4, original);
      assertRect("copy", 10, 2, 3, 4, copy);
      assertRect("ctor copy", 1, 2, 20, 4, viaCtor);
   }

   @Test
   void mapItemDefaults() {
      MapItem item = new MapItem();
      assertEquals(1.0, item.getSize(), 0);
      assertEquals(0, item.getOrder());
      assertRect("bounds", 0, 0, 1, 1, item);
   }

   @Test
   void mapItemSetBoundsWritesIntoTheItemsCurrentRect() {
      MapItem item = new MapItem();
      Rect r = new Rect(5, 5, 5, 5);
      item.setBounds(r);
      assertSame(r, item.getBounds());

      item.setBounds(1, 2, 3, 4);
      // pinned current behavior: the four-argument overload mutates the instance set before
      assertRect("r mutated in place", 1, 2, 3, 4, r);
      assertSame(r, item.getBounds());
   }
}
