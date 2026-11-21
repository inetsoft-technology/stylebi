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

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Properties every layout algorithm must satisfy: the rectangles tile the bounds, stay inside
 * them, do not overlap and are proportional to the weights.
 */
@Tag("core")
class MapLayoutPropertyTest {
   private static final double REL = 1e-9;

   static Stream<Named<MapLayout>> layouts() {
      return Stream.of(
         Named.of("slice-best", new SliceLayout(SliceLayout.BEST)),
         Named.of("slice-vertical", new SliceLayout(AbstractMapLayout.VERTICAL)),
         Named.of("slice-horizontal", new SliceLayout(AbstractMapLayout.HORIZONTAL)),
         Named.of("slice-alternate", new SliceLayout(SliceLayout.ALTERNATE)),
         Named.of("squarified", new SquarifiedLayout()),
         Named.of("binary-tree", new BinaryTreeLayout()));
   }

   static final double[][] WEIGHTS = {
      { 1, 2, 3, 4 }, { 5, 5, 5, 5, 5 }, { 10, 1, 1, 1 }, { 3, 1, 4, 1, 5, 9, 2, 6 }
   };

   static final double[][] BOUNDS = {
      { 0, 0, 100, 60 }, { 0, 0, 60, 100 }, { 0, 0, 50, 50 }
   };

   static Stream<Arguments> cases() {
      return layouts().flatMap(l -> Stream.of(WEIGHTS).flatMap(
         w -> Stream.of(BOUNDS).map(b -> Arguments.of(l, w, b))));
   }

   private static Rect rect(double[] b) {
      return new Rect(b[0], b[1], b[2], b[3]);
   }

   private static void layout(MapLayout layout, Mappable[] items, Rect bounds) {
      layout.layout(model(items), bounds);
   }

   @ParameterizedTest
   @MethodSource("cases")
   void areasSumToBoundsArea(MapLayout layout, double[] weights, double[] bounds) {
      MapItem[] items = items(weights);
      layout(layout, items, rect(bounds));

      double area = 0;

      for(MapItem item : items) {
         area += item.getBounds().w * item.getBounds().h;
      }

      double total = bounds[2] * bounds[3];
      assertEquals(total, area, total * REL);
   }

   @ParameterizedTest
   @MethodSource("cases")
   void everyRectIsInsideBounds(MapLayout layout, double[] weights, double[] bounds) {
      MapItem[] items = items(weights);
      layout(layout, items, rect(bounds));

      for(MapItem item : items) {
         Rect r = item.getBounds();
         assertTrue(r.x >= bounds[0] - EPS && r.y >= bounds[1] - EPS &&
                    r.x + r.w <= bounds[0] + bounds[2] + EPS &&
                    r.y + r.h <= bounds[1] + bounds[3] + EPS, r.toString());
      }
   }

   @ParameterizedTest
   @MethodSource("cases")
   void noTwoRectsOverlap(MapLayout layout, double[] weights, double[] bounds) {
      MapItem[] items = items(weights);
      layout(layout, items, rect(bounds));

      for(int i = 0; i < items.length; i++) {
         for(int j = i + 1; j < items.length; j++) {
            Rect a = items[i].getBounds();
            Rect b = items[j].getBounds();
            double ow = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
            double oh = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
            assertFalse(ow > 1e-7 && oh > 1e-7, "items " + i + " and " + j + " overlap: " + a +
                        " / " + b);
         }
      }
   }

   @ParameterizedTest
   @MethodSource("cases")
   void areaIsProportionalToWeight(MapLayout layout, double[] weights, double[] bounds) {
      MapItem[] items = items(weights);
      layout(layout, items, rect(bounds));

      double sum = 0;

      for(double w : weights) {
         sum += w;
      }

      double total = bounds[2] * bounds[3];

      for(int i = 0; i < items.length; i++) {
         Rect r = items[i].getBounds();
         assertEquals(weights[i] / sum, r.w * r.h / total, REL, "item " + i + " " + r);
      }
   }

   @ParameterizedTest
   @MethodSource("extremeCases")
   void extremeWeightsGiveFiniteNonNegativeRects(MapLayout layout, double[] weights) {
      MapItem[] items = items(weights);
      layout(layout, items, new Rect(0, 0, 100, 60));
      assertFinite(items);

      for(MapItem item : items) {
         assertTrue(item.getBounds().w >= 0 && item.getBounds().h >= 0,
                    item.getBounds().toString());
      }
   }

   static Stream<Arguments> extremeCases() {
      return layouts().flatMap(l -> Stream.of(new double[] { 1e-6, 1, 1e6 },
                                              new double[] { 1e6, 1e-6, 1e-6, 1, 1 })
         .map(w -> Arguments.of(l, w)));
   }

   @ParameterizedTest
   @MethodSource("cases")
   void layingOutTwiceGivesTheSameRects(MapLayout layout, double[] weights, double[] bounds) {
      MapItem[] items = items(weights);
      layout(layout, items, rect(bounds));
      double[] first = snapshot(items);
      layout(layout, items, rect(bounds));
      assertArrayEquals(first, snapshot(items), 1e-9);
   }

   @ParameterizedTest
   @MethodSource("cases")
   void multiplyingAllWeightsDoesNotChangeTheLayout(MapLayout layout, double[] weights,
                                                    double[] bounds)
   {
      MapItem[] base = items(weights);
      layout(layout, base, rect(bounds));

      double[] scaled = new double[weights.length];

      for(int i = 0; i < weights.length; i++) {
         scaled[i] = weights[i] * 1000;
      }

      MapItem[] big = items(scaled);
      layout(layout, big, rect(bounds));
      assertArrayEquals(snapshot(base), snapshot(big), 1e-8);
   }

   @ParameterizedTest
   @MethodSource("cases")
   void nonOriginBoundsGiveTheOriginResultShifted(MapLayout layout, double[] weights,
                                                  double[] bounds)
   {
      MapItem[] origin = items(weights);
      layout(layout, origin, new Rect(0, 0, bounds[2], bounds[3]));

      MapItem[] shifted = items(weights);
      layout(layout, shifted, new Rect(13, -5, bounds[2], bounds[3]));

      double[] expected = snapshot(origin);

      for(int i = 0; i < expected.length; i += 4) {
         expected[i] += 13;
         expected[i + 1] -= 5;
      }

      assertArrayEquals(expected, snapshot(shifted), 1e-8);
   }

   @Test
   void squarifiedResultDoesNotDependOnInputOrder() {
      double[] weights = { 8, 3, 5, 1, 13, 2 };
      int[] permutation = { 3, 0, 5, 2, 4, 1 };

      MapItem[] straight = items(weights);
      new SquarifiedLayout().layout(straight, new Rect(0, 0, 100, 60));

      MapItem[] permuted = new MapItem[weights.length];

      for(int i = 0; i < permutation.length; i++) {
         permuted[i] = new MapItem(weights[permutation[i]], permutation[i]);
      }

      new SquarifiedLayout().layout(permuted, new Rect(0, 0, 100, 60));

      for(MapItem item : permuted) {
         Rect expected = straight[item.getOrder()].getBounds();
         assertRect("weight " + item.getSize(), expected.x, expected.y, expected.w, expected.h,
                    item);
      }
   }
}
