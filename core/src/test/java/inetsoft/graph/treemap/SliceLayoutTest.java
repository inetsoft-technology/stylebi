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
import org.junit.jupiter.params.provider.ValueSource;

import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class SliceLayoutTest {
   private static final double[] SPLIT_X_1 = { 0, 0, 2, 4 };
   private static final double[] SPLIT_X_2 = { 2, 0, 6, 4 };
   private static final double[] STACK_Y_1 = { 0, 0, 8, 1 };
   private static final double[] STACK_Y_2 = { 0, 1, 8, 3 };

   @Test
   void bestSplitsAlongXInWideBounds() {
      MapItem[] items = items(1, 3);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(0, 0, 8, 4));
      assertRects(items, SPLIT_X_1, SPLIT_X_2);
   }

   @Test
   void bestStacksAlongYInTallBounds() {
      MapItem[] items = items(1, 3);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(0, 0, 4, 8));
      assertRects(items, new double[] { 0, 0, 4, 2 }, new double[] { 0, 2, 4, 6 });
   }

   @Test
   void bestStacksAlongYInSquareBounds() {
      MapItem[] items = items(1, 3);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(0, 0, 4, 4));
      assertRects(items, new double[] { 0, 0, 4, 1 }, new double[] { 0, 1, 4, 3 });
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 2, 3 })
   void alternateUsesDepthParityOfFirstItem(int depth) {
      MapItem[] items = items(1, 3);
      items[0].setDepth(depth);
      items[1].setDepth(depth);
      new SliceLayout(SliceLayout.ALTERNATE).layout(items, new Rect(0, 0, 8, 4));

      if(depth % 2 == 1) {
         assertRects(items, SPLIT_X_1, SPLIT_X_2);
      }
      else {
         assertRects(items, STACK_Y_1, STACK_Y_2);
      }
   }

   @Test
   void alternateReadsOrientationFromFirstItemOnly() {
      MapItem[] items = items(1, 3);
      items[0].setDepth(1);
      items[1].setDepth(0);
      new SliceLayout(SliceLayout.ALTERNATE).layout(items, new Rect(0, 0, 8, 4));
      assertRects(items, SPLIT_X_1, SPLIT_X_2);
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void defaultConstructorBehavesLikeAlternate(int depth) {
      MapItem[] expected = items(1, 3);
      MapItem[] actual = items(1, 3);
      expected[0].setDepth(depth);
      actual[0].setDepth(depth);
      new SliceLayout(SliceLayout.ALTERNATE).layout(expected, new Rect(0, 0, 8, 4));
      new SliceLayout().layout(actual, new Rect(0, 0, 8, 4));
      assertArrayEquals(snapshot(expected), snapshot(actual), 0);
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1 })
   void explicitOrientationIgnoresDepth(int depth) {
      MapItem[] vertical = items(1, 3);
      MapItem[] horizontal = items(1, 3);
      vertical[0].setDepth(depth);
      horizontal[0].setDepth(depth);
      new SliceLayout(AbstractMapLayout.VERTICAL).layout(vertical, new Rect(0, 0, 8, 4));
      new SliceLayout(AbstractMapLayout.HORIZONTAL).layout(horizontal, new Rect(0, 0, 8, 4));
      assertRects(vertical, STACK_Y_1, STACK_Y_2);
      assertRects(horizontal, SPLIT_X_1, SPLIT_X_2);
   }

   @ParameterizedTest
   @ValueSource(ints = { SliceLayout.BEST, SliceLayout.ALTERNATE, AbstractMapLayout.VERTICAL,
                         AbstractMapLayout.HORIZONTAL })
   void emptyItemsAreANoOp(int orientation) {
      assertDoesNotThrow(() -> new SliceLayout(orientation)
         .layout(new Mappable[0], new Rect(0, 0, 4, 4)));
   }

   @ParameterizedTest
   @ValueSource(ints = { SliceLayout.BEST, SliceLayout.ALTERNATE, AbstractMapLayout.VERTICAL,
                         AbstractMapLayout.HORIZONTAL })
   void singleItemFillsBounds(int orientation) {
      MapItem[] items = items(4);
      new SliceLayout(orientation).layout(items, new Rect(0, 0, 6, 3));
      assertRects(items, new double[] { 0, 0, 6, 3 });
   }

   @Test
   void equalWeightsKeepInputOrder() {
      MapItem[] items = items(1, 1, 1);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(0, 0, 3, 1));
      assertRects(items, new double[] { 0, 0, 1, 1 }, new double[] { 1, 0, 1, 1 },
                  new double[] { 2, 0, 1, 1 });
   }

   @Test
   void bestDoesNotSortTheItems() {
      MapItem[] items = items(4, 1);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(3, 4, 10, 5));
      assertRects(items, new double[] { 3, 4, 8, 5 }, new double[] { 11, 4, 2, 5 });
   }

   @Test
   void zeroWidthBoundsGiveZeroWidthRectsInsideBounds() {
      MapItem[] items = items(1, 2, 3);
      new SliceLayout(SliceLayout.BEST).layout(items, new Rect(0, 0, 0, 5));
      assertFinite(items);

      for(MapItem item : items) {
         Rect r = item.getBounds();
         assertEquals(0, r.w, EPS);
         assertEquals(0, r.x, EPS);
         assertTrue(r.y >= -EPS && r.y + r.h <= 5 + EPS, r.toString());
      }
   }
}
