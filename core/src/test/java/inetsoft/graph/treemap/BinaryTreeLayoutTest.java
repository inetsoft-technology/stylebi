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

import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class BinaryTreeLayoutTest {
   private final BinaryTreeLayout layout = new BinaryTreeLayout();

   @Test
   void fourEqualItemsFormAQuadrantGrid() {
      MapItem[] items = items(1, 1, 1, 1);
      layout.layout(items, new Rect(0, 0, 4, 4));
      assertRects(items, new double[] { 0, 0, 2, 2 }, new double[] { 0, 2, 2, 2 },
                  new double[] { 2, 0, 2, 2 }, new double[] { 2, 2, 2, 2 });
   }

   @Test
   void threeItemsSplitByCountThenAlternateOrientation() {
      MapItem[] items = items(1, 2, 3);
      layout.layout(items, new Rect(0, 0, 6, 2));
      assertRects(items, new double[] { 0, 0, 3, 2.0 / 3 }, new double[] { 0, 2.0 / 3, 3, 4.0 / 3 },
                  new double[] { 3, 0, 3, 2 });
   }

   @Test
   void twoItemsFirstSplitIsAlongX() {
      MapItem[] items = items(1, 3);
      layout.layout(items, new Rect(0, 0, 8, 4));
      assertRects(items, new double[] { 0, 0, 2, 4 }, new double[] { 2, 0, 6, 4 });
   }

   @Test
   void fiveArgLayoutWithVerticalFalseSplitsAlongY() {
      MapItem[] items = items(1, 3);
      layout.layout(items, 0, 1, new Rect(0, 0, 8, 4), false);
      assertRects(items, new double[] { 0, 0, 8, 1 }, new double[] { 0, 1, 8, 3 });
   }

   @Test
   void singleItemRectEqualsBounds() {
      MapItem[] items = items(5);
      layout.layout(items, new Rect(1, 2, 7, 3));
      assertRects(items, new double[] { 1, 2, 7, 3 });
   }

   @Test
   void subRangeLeavesOutsideItemsUntouched() {
      MapItem[] items = items(5, 1, 3);
      layout.layout(items, 1, 2, new Rect(0, 0, 8, 4));
      assertRect("untouched", 0, 0, 1, 1, items[0]);
      assertRect("item 1", 0, 0, 2, 4, items[1]);
      assertRect("item 2", 2, 0, 6, 4, items[2]);
   }

   @Test
   void emptyItemsAndStartAfterEndAreNoOps() {
      assertDoesNotThrow(() -> layout.layout(new Mappable[0], new Rect(0, 0, 4, 4)));

      MapItem[] items = items(1, 2);
      assertDoesNotThrow(() -> layout.layout(items, 1, 0, new Rect(0, 0, 4, 4)));
      assertRect("untouched 0", 0, 0, 1, 1, items[0]);
      assertRect("untouched 1", 0, 0, 1, 1, items[1]);
   }

   @Test
   void inputOrderIsKeptWithoutSorting() {
      MapItem[] items = items(3, 1);
      layout.layout(items, new Rect(0, 0, 8, 4));
      assertRects(items, new double[] { 0, 0, 6, 4 }, new double[] { 6, 0, 2, 4 });
   }

   @Test
   void leadingItemWithTrailingZeroWeightsFillsBoundsAndZeroItemsHaveNoArea() {
      MapItem[] items = items(1, 0, 0);
      layout.layout(items, new Rect(0, 0, 4, 4));
      assertFinite(items);
      assertRect("item 0", 0, 0, 4, 4, items[0]);

      for(int i = 1; i < 3; i++) {
         Rect r = items[i].getBounds();
         assertEquals(0, r.w * r.h, EPS, "item " + i + " " + r);
      }
   }

   @Test
   void fiveEqualItemsSplitByIndex() {
      MapItem[] items = items(1, 1, 1, 1, 1);
      layout.layout(items, new Rect(0, 0, 5, 1));
      assertRects(items, new double[] { 0, 0, 1.5, 2.0 / 3 }, new double[] { 1.5, 0, 1.5, 2.0 / 3 },
                  new double[] { 0, 2.0 / 3, 3, 1.0 / 3 }, new double[] { 3, 0, 2, 0.5 },
                  new double[] { 3, 0.5, 2, 0.5 });
   }
}
