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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static inetsoft.graph.treemap.OrderedTreemap.*;
import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderedTreemap and its Pivot* wrappers (no production caller, covered as a library class).
 */
@Tag("core")
class OrderedTreemapTest {
   private static final int[] PIVOTS = { PIVOT_BY_MIDDLE, PIVOT_BY_SPLIT_SIZE, PIVOT_BY_BIGGEST };

   private static OrderedTreemap ordered(int pivot) {
      OrderedTreemap layout = new OrderedTreemap();
      layout.setPivotType(pivot);
      return layout;
   }

   @ParameterizedTest
   @ValueSource(ints = { PIVOT_BY_MIDDLE, PIVOT_BY_SPLIT_SIZE, PIVOT_BY_BIGGEST })
   void singleItemFillsBounds(int pivot) {
      MapItem[] items = items(5);
      ordered(pivot).layout(model(items), new Rect(2, 3, 10, 4));
      assertRects(items, new double[] { 2, 3, 10, 4 });
   }

   @ParameterizedTest
   @ValueSource(ints = { PIVOT_BY_MIDDLE, PIVOT_BY_SPLIT_SIZE, PIVOT_BY_BIGGEST })
   void twoItemsSplitAlongTheLongSide(int pivot) {
      MapItem[] wide = items(3, 1);
      ordered(pivot).layout(model(wide), new Rect(0, 0, 4, 2));
      assertRects(wide, new double[] { 0, 0, 3, 2 }, new double[] { 3, 0, 1, 2 });

      MapItem[] tall = items(3, 1);
      ordered(pivot).layout(model(tall), new Rect(0, 0, 2, 4));
      assertRects(tall, new double[] { 0, 0, 2, 3 }, new double[] { 0, 3, 2, 1 });
   }

   @ParameterizedTest
   @ValueSource(ints = { PIVOT_BY_MIDDLE, PIVOT_BY_SPLIT_SIZE, PIVOT_BY_BIGGEST })
   void equalWeightsKeepInputOrderLeftToRight(int pivot) {
      MapItem[] items = items(1, 1, 1);
      ordered(pivot).layout(model(items), new Rect(0, 0, 3, 1));
      assertRects(items, new double[] { 0, 0, 1, 1 }, new double[] { 1, 0, 1, 1 },
                  new double[] { 2, 0, 1, 1 });
   }

   @Test
   void boundsOffsetIsAppliedOnce() {
      MapItem[] items = items(3, 1);
      new OrderedTreemap().layout(model(items), new Rect(10, 20, 4, 2));
      assertRects(items, new double[] { 10, 20, 3, 2 }, new double[] { 13, 20, 1, 2 });
   }

   static Stream<Arguments> invariantCases() {
      double[][] weights = {
         { 1, 1, 1, 1 }, { 1, 1, 1, 1, 1 }, { 1, 1, 1, 1, 1, 1 }, { 1, 1, 1, 1, 1, 1, 1, 1 },
         { 9, 1, 1, 1, 1 }
      };

      return IntStream.of(PIVOTS).boxed().flatMap(p -> Stream.of(weights).map(w -> Arguments.of(p, w)));
   }

   @ParameterizedTest
   @MethodSource("invariantCases")
   void rectsTileTheBoundsProportionallyWithoutOverlap(int pivot, double[] weights) {
      MapItem[] items = items(weights);
      ordered(pivot).layout(model(items), new Rect(0, 0, 12, 8));

      double sum = 0;
      double area = 0;

      for(double w : weights) {
         sum += w;
      }

      for(int i = 0; i < items.length; i++) {
         Rect r = items[i].getBounds();
         area += r.w * r.h;
         assertTrue(r.x >= -EPS && r.y >= -EPS && r.x + r.w <= 12 + EPS && r.y + r.h <= 8 + EPS,
                    r.toString());
         assertEquals(weights[i] / sum, r.w * r.h / 96, 1e-9, "item " + i + " " + r);

         for(int j = i + 1; j < items.length; j++) {
            Rect o = items[j].getBounds();
            double ow = Math.min(r.x + r.w, o.x + o.w) - Math.max(r.x, o.x);
            double oh = Math.min(r.y + r.h, o.y + o.h) - Math.max(r.y, o.y);
            assertFalse(ow > 1e-7 && oh > 1e-7, "items " + i + " and " + j + " overlap");
         }
      }

      assertEquals(96, area, 1e-8);
   }

   @Test
   void zeroWeightItemHasZeroAreaAndOthersAreProportional() {
      MapItem[] items = items(3, 0, 2, 1);
      new OrderedTreemap().layout(model(items), new Rect(0, 0, 6, 4));
      assertFinite(items);
      assertEquals(0, items[1].getBounds().w * items[1].getBounds().h, EPS);
      assertEquals(12, items[0].getBounds().w * items[0].getBounds().h, 1e-9);
      assertEquals(8, items[2].getBounds().w * items[2].getBounds().h, 1e-9);
      assertEquals(4, items[3].getBounds().w * items[3].getBounds().h, 1e-9);
   }

   @ParameterizedTest
   @MethodSource("wrapperCases")
   void pivotWrappersEqualOrderedTreemapWithTheirPivotType(MapLayout wrapper, int pivot,
                                                           double[] weights)
   {
      MapItem[] viaWrapper = items(weights);
      wrapper.layout(model(viaWrapper), new Rect(0, 0, 10, 6));
      MapItem[] viaOrdered = items(weights);
      ordered(pivot).layout(model(viaOrdered), new Rect(0, 0, 10, 6));
      assertArrayEquals(snapshot(viaOrdered), snapshot(viaWrapper), 0);
   }

   static Stream<Arguments> wrapperCases() {
      double[][] weights = { { 9, 1, 1, 1, 1 }, { 1, 2, 3, 4, 5, 6 } };
      Stream.Builder<Arguments> b = Stream.builder();

      for(double[] w : weights) {
         b.add(Arguments.of(Named.of("PivotByMiddle", new PivotByMiddle()), PIVOT_BY_MIDDLE, w));
         b.add(Arguments.of(Named.of("PivotBySize", new PivotBySize()), PIVOT_BY_BIGGEST, w));
         b.add(Arguments.of(Named.of("PivotBySplitSize", new PivotBySplitSize()),
                            PIVOT_BY_SPLIT_SIZE, w));
      }

      return b.build();
   }
}
