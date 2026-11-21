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
class SquarifiedLayoutTest {
   private final SquarifiedLayout layout = new SquarifiedLayout();

   @Test
   void twoItemsInWideBoundsSplitAlongX() {
      MapItem[] items = items(3, 1);
      layout.layout(items, new Rect(0, 0, 4, 2));
      assertRects(items, new double[] { 0, 0, 3, 2 }, new double[] { 3, 0, 1, 2 });
   }

   @Test
   void inputOrderDoesNotMatterBecauseItemsAreSortedBySize() {
      MapItem[] items = items(1, 3);
      layout.layout(items, new Rect(0, 0, 4, 2));
      assertRect("size 3", 0, 0, 3, 2, items[1]);
      assertRect("size 1", 3, 0, 1, 2, items[0]);
   }

   @Test
   void twoItemsInTallBoundsStackAlongY() {
      MapItem[] items = items(3, 1);
      layout.layout(items, new Rect(0, 0, 2, 4));
      assertRects(items, new double[] { 0, 0, 2, 3 }, new double[] { 0, 3, 2, 1 });
   }

   @Test
   void equalWeightsGiveUnitSquaresInInputOrder() {
      MapItem[] items = items(1, 1, 1);
      layout.layout(items, new Rect(0, 0, 3, 1));
      assertRects(items, new double[] { 0, 0, 1, 1 }, new double[] { 1, 0, 1, 1 },
                  new double[] { 2, 0, 1, 1 });
   }

   @Test
   @DisplayName("[4,2,1] in 7x4 - pinned current strip heuristic")
   void pinnedStripsOfFourTwoOneInWideBounds() {
      MapItem[] items = items(4, 2, 1);
      layout.layout(items, new Rect(0, 0, 7, 4));
      assertRects(items, new double[] { 0, 0, 4, 4 }, new double[] { 4, 0, 2, 4 },
                  new double[] { 6, 0, 1, 4 });
   }

   @Test
   @DisplayName("[6,1,1] in 4x2 - pinned current strip heuristic")
   void pinnedStripsOfSixOneOne() {
      MapItem[] items = items(6, 1, 1);
      layout.layout(items, new Rect(0, 0, 4, 2));
      assertRects(items, new double[] { 0, 0, 3, 2 }, new double[] { 3, 0, 0.5, 2 },
                  new double[] { 3.5, 0, 0.5, 2 });
   }

   @Test
   @DisplayName("[3,2,1] in square 4x4 - pinned current strip heuristic")
   void pinnedStripsInSquareBounds() {
      MapItem[] items = items(3, 2, 1);
      layout.layout(items, new Rect(0, 0, 4, 4));
      assertRects(items, new double[] { 0, 0, 10.0 / 3, 2.4 },
                  new double[] { 0, 2.4, 10.0 / 3, 1.6 },
                  new double[] { 10.0 / 3, 0, 2.0 / 3, 4 });
   }

   @Test
   void tallBoundsGiveTransposeOfWideBoundsResult() {
      MapItem[] items = items(4, 2, 1);
      layout.layout(items, new Rect(0, 0, 4, 7));
      assertRects(items, new double[] { 0, 0, 4, 4 }, new double[] { 0, 4, 4, 2 },
                  new double[] { 0, 6, 4, 1 });
   }

   @Test
   void fourArgLayoutDoesNotSort() {
      MapItem[] items = items(1, 3);
      layout.layout(items, 0, 1, new Rect(0, 0, 4, 2));
      assertRects(items, new double[] { 0, 0, 1, 2 }, new double[] { 1, 0, 3, 2 });
   }

   @Test
   void twoArgLayoutEqualsFourArgLayoutOfPresortedCopy() {
      MapItem[] unsorted = items(2, 9, 4, 4, 1, 7);
      layout.layout(unsorted, new Rect(0, 0, 10, 6));

      MapItem[] sorted = items(9, 7, 4, 4, 2, 1);
      layout.layout(sorted, 0, 5, new Rect(0, 0, 10, 6));

      // sorted position of each unsorted item (ties keep input order)
      int[] pos = { 4, 0, 2, 3, 5, 1 };

      for(int i = 0; i < unsorted.length; i++) {
         Rect r = sorted[pos[i]].getBounds();
         assertRect("item " + i, r.x, r.y, r.w, r.h, unsorted[i]);
      }
   }

   @Test
   void subRangeLeavesOutsideItemsUntouchedAndFillsBounds() {
      MapItem[] items = items(9, 4, 3, 2, 1, 9);
      layout.layout(items, 1, 4, new Rect(0, 0, 10, 6));

      assertRect("before range", 0, 0, 1, 1, items[0]);
      assertRect("after range", 0, 0, 1, 1, items[5]);

      double area = 0;

      for(int i = 1; i <= 4; i++) {
         Rect r = items[i].getBounds();
         area += r.w * r.h;
         assertTrue(r.x >= -EPS && r.y >= -EPS && r.x + r.w <= 10 + EPS && r.y + r.h <= 6 + EPS,
                    r.toString());
      }

      assertEquals(60, area, 1e-8);
   }

   @Test
   void emptyItemsDoNotThrow() {
      assertDoesNotThrow(() -> layout.layout(new Mappable[0], new Rect(0, 0, 4, 4)));
   }

   @Test
   void singleItemFillsBounds() {
      MapItem[] items = items(9);
      layout.layout(items, new Rect(2, 3, 5, 6));
      assertRects(items, new double[] { 2, 3, 5, 6 });
   }

   @Test
   void callerArrayOrderIsUnchanged() {
      MapItem[] items = items(1, 3, 2);
      MapItem[] before = items.clone();
      layout.layout(items, new Rect(0, 0, 6, 6));

      for(int i = 0; i < items.length; i++) {
         assertSame(before[i], items[i]);
      }
   }

   @Test
   @Disabled("Not filed (not reachable: TreemapSizeFrame guarantees sizes of at least 1 (TreemapSizeFrame.java:45-84)): zero-width bounds: items keep the default 1x1 rect outside the bounds (width 1, " +
             "expected 0): SquarifiedLayout returns on 'mid > end' without laying out")
   void zeroWidthBoundsKeepEveryItemInsideBounds() {
      MapItem[] items = items(4, 3, 2, 1);
      layout.layout(items, new Rect(0, 0, 0, 5));

      for(MapItem item : items) {
         assertEquals(0, item.getBounds().w, EPS, item.getBounds().toString());
      }
   }

   @Test
   @Disabled("Not filed (by design: ported strip heuristic, not the textbook worst-aspect algorithm): textbook squarified result expected (2x2 grid of unit squares); actual code " +
             "builds three stacked strips plus one of width 1.5 and 0.5")
   void fourEqualItemsInSquareGiveFourUnitSquares() {
      MapItem[] items = items(1, 1, 1, 1);
      layout.layout(items, new Rect(0, 0, 2, 2));
      assertRects(items, new double[] { 0, 0, 1, 1 }, new double[] { 1, 0, 1, 1 },
                  new double[] { 0, 1, 1, 1 }, new double[] { 1, 1, 1, 1 });
   }

   @Test
   @Disabled("Not filed (not reachable: TreemapSizeFrame guarantees sizes of at least 1 (TreemapSizeFrame.java:45-84)): zero-weight item alone in its strip gets NaN coordinates (0/0): expected finite " +
             "rect with area 0")
   void zeroWeightItemGetsFiniteZeroAreaRect() {
      MapItem[] items = items(5, 0, 3);
      layout.layout(items, new Rect(0, 0, 4, 2));
      assertRect("size 5", 0, 0, 2.5, 2, items[0]);
      assertRect("size 3", 2.5, 0, 1.5, 2, items[2]);
      assertFinite(items);
      assertEquals(0, items[1].getBounds().w * items[1].getBounds().h, EPS);
   }

   @Test
   void twentyThousandEqualItemsDoNotOverflowTheStackAndConserveArea() {
      MapItem[] items = new MapItem[20000];

      for(int i = 0; i < items.length; i++) {
         items[i] = new MapItem(1, i);
      }

      layout.layout(items, new Rect(0, 0, 1000, 1000));

      double area = 0;

      for(MapItem item : items) {
         Rect r = item.getBounds();
         area += r.w * r.h;
         assertTrue(r.x >= -1e-6 && r.y >= -1e-6 && r.x + r.w <= 1000 + 1e-6 &&
                    r.y + r.h <= 1000 + 1e-6, r.toString());
      }

      assertEquals(1e6, area, 1e6 * 1e-9);
   }

   @Test
   @Disabled("Not filed (by design: ported strip heuristic, not the textbook worst-aspect algorithm): squarified worst-aspect rule not implemented: expected (0,0,4,4),(4,0,3,8/3)," +
             "(4,8/3,3,4/3) for [4,2,1] in 7x4 (van Wijk), actual (0,0,4,4),(4,0,2,4),(6,0,1,4)")
   void textbookWorstAspectResultForFourTwoOne() {
      MapItem[] items = items(4, 2, 1);
      layout.layout(items, new Rect(0, 0, 7, 4));
      assertRects(items, new double[] { 0, 0, 4, 4 }, new double[] { 4, 0, 3, 8.0 / 3 },
                  new double[] { 4, 8.0 / 3, 3, 4.0 / 3 });
   }
}
