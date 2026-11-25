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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static inetsoft.graph.treemap.AbstractMapLayout.*;
import static inetsoft.graph.treemap.TreemapTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AbstractMapLayoutTest {
   @Test
   void totalSizeSumsAllItems() {
      assertEquals(6.0, totalSize(items(1, 2, 3)), 0);
   }

   @Test
   void totalSizeOfSubRangeExcludesOutsideItems() {
      assertEquals(4.0, totalSize(items(5, 1, 3), 1, 2), 0);
   }

   @Test
   void totalSizeOfEmptyArrayIsZero() {
      assertEquals(0.0, totalSize(new Mappable[0]), 0);
   }

   @Test
   void totalSizeWithStartAfterEndIsZero() {
      assertEquals(0.0, totalSize(items(1, 2, 3), 2, 1), 0);
   }

   @Test
   void sortDescendingReturnsSortedCopyAndKeepsInput() {
      MapItem[] in = items(1, 3, 2);
      Mappable[] out = new SliceLayout().sortDescending(in);

      assertEquals(3, out.length);
      assertEquals(3.0, out[0].getSize(), 0);
      assertEquals(2.0, out[1].getSize(), 0);
      assertEquals(1.0, out[2].getSize(), 0);
      assertNotSame(in, out);
      assertEquals(1.0, in[0].getSize(), 0);
      assertEquals(3.0, in[1].getSize(), 0);
      assertEquals(2.0, in[2].getSize(), 0);
   }

   @Test
   void sortDescendingIsStableForTiesAndHandlesTrivialArrays() {
      MapItem a = new MapItem(2, 0);
      MapItem b = new MapItem(2, 1);
      MapItem c = new MapItem(1, 2);
      SliceLayout layout = new SliceLayout();
      Mappable[] out = layout.sortDescending(new Mappable[] { c, a, b });

      assertSame(a, out[0]);
      assertSame(b, out[1]);
      assertSame(c, out[2]);

      assertEquals(0, layout.sortDescending(new Mappable[0]).length);
      Mappable[] single = layout.sortDescending(new Mappable[] { a });
      assertEquals(1, single.length);
      assertSame(a, single[0]);
   }

   @Test
   void sliceLayoutVerticalAscendingStacksAlongY() {
      MapItem[] items = items(1, 3);
      sliceLayout(items, 0, 1, new Rect(0, 0, 8, 4), VERTICAL, ASCENDING);
      assertRects(items, new double[] { 0, 0, 8, 1 }, new double[] { 0, 1, 8, 3 });
   }

   @Test
   void sliceLayoutHorizontalAscendingSplitsAlongX() {
      MapItem[] items = items(1, 3);
      sliceLayout(items, 0, 1, new Rect(0, 0, 8, 4), HORIZONTAL, ASCENDING);
      assertRects(items, new double[] { 0, 0, 2, 4 }, new double[] { 2, 0, 6, 4 });
   }

   @Test
   void sliceLayoutVerticalDescendingStartsFromBottom() {
      MapItem[] items = items(1, 3);
      sliceLayout(items, 0, 1, new Rect(0, 0, 8, 4), VERTICAL, DESCENDING);
      assertRects(items, new double[] { 0, 3, 8, 1 }, new double[] { 0, 0, 8, 3 });
   }

   @Test
   void sliceLayoutHorizontalDescendingStartsFromRight() {
      MapItem[] items = items(1, 3);
      sliceLayout(items, 0, 1, new Rect(0, 0, 8, 4), HORIZONTAL, DESCENDING);
      assertRects(items, new double[] { 6, 0, 2, 4 }, new double[] { 0, 0, 6, 4 });
   }

   @ParameterizedTest
   @ValueSource(ints = { VERTICAL, HORIZONTAL })
   void descendingLayoutMatchesAscendingLayoutOfReversedItems(int orientation) {
      MapItem[] desc = items(1, 3);
      sliceLayout(desc, 0, 1, new Rect(0, 0, 8, 4), orientation, DESCENDING);

      MapItem[] asc = items(3, 1);
      sliceLayout(asc, 0, 1, new Rect(0, 0, 8, 4), orientation, ASCENDING);

      assertRect("first item", asc[1].getBounds().x, asc[1].getBounds().y,
                 asc[1].getBounds().w, asc[1].getBounds().h, desc[0]);
      assertRect("second item", asc[0].getBounds().x, asc[0].getBounds().y,
                 asc[0].getBounds().w, asc[0].getBounds().h, desc[1]);
   }

   @ParameterizedTest
   @ValueSource(ints = { VERTICAL, HORIZONTAL })
   void sliceLayoutWithoutOrderIsAscending(int orientation) {
      MapItem[] five = items(1, 2, 3);
      MapItem[] six = items(1, 2, 3);
      sliceLayout(five, 0, 2, new Rect(1, 2, 9, 6), orientation);
      sliceLayout(six, 0, 2, new Rect(1, 2, 9, 6), orientation, ASCENDING);
      assertArrayEquals(snapshot(six), snapshot(five), 0);
   }

   @Test
   void sliceLayoutSubRangeLaysOutOnlyThoseItems() {
      MapItem[] items = items(5, 1, 3);
      sliceLayout(items, 1, 2, new Rect(0, 0, 8, 4), VERTICAL);
      assertRect("untouched", 0, 0, 1, 1, items[0]);
      assertRect("item 1", 0, 0, 8, 1, items[1]);
      assertRect("item 2", 0, 1, 8, 3, items[2]);
   }

   @Test
   void sliceLayoutHonoursBoundsOrigin() {
      MapItem[] items = items(1, 3);
      sliceLayout(items, 0, 1, new Rect(10, 20, 8, 4), VERTICAL);
      assertRects(items, new double[] { 10, 20, 8, 1 }, new double[] { 10, 21, 8, 3 });
   }

   @Test
   void sliceLayoutGivesZeroWeightItemAZeroHeightRect() {
      MapItem[] items = items(2, 0, 2);
      sliceLayout(items, 0, 2, new Rect(0, 0, 4, 4), VERTICAL);
      assertRects(items, new double[] { 0, 0, 4, 2 }, new double[] { 0, 2, 4, 0 },
                  new double[] { 0, 2, 4, 2 });
   }

   @ParameterizedTest
   @EnumSource(Combo.class)
   void sliceLayoutOfSingleItemFillsBounds(Combo combo) {
      MapItem[] items = items(7);
      sliceLayout(items, 0, 0, new Rect(3, 4, 10, 5), combo.orientation, combo.order);
      assertRects(items, new double[] { 3, 4, 10, 5 });
   }

   enum Combo {
      VERTICAL_ASC(VERTICAL, ASCENDING), VERTICAL_DESC(VERTICAL, DESCENDING),
      HORIZONTAL_ASC(HORIZONTAL, ASCENDING), HORIZONTAL_DESC(HORIZONTAL, DESCENDING);

      Combo(int orientation, int order) {
         this.orientation = orientation;
         this.order = order;
      }

      final int orientation;
      final int order;
   }

   @ParameterizedTest
   @ValueSource(strings = { "slice", "squarified", "binary" })
   void layoutWithModelMatchesLayoutWithItems(String kind) {
      AbstractMapLayout layout = switch(kind) {
         case "slice" -> new SliceLayout(SliceLayout.BEST);
         case "squarified" -> new SquarifiedLayout();
         default -> new BinaryTreeLayout();
      };

      MapItem[] direct = items(4, 2, 1, 3);
      layout.layout(direct, new Rect(0, 0, 8, 5));
      MapItem[] viaModel = items(4, 2, 1, 3);
      layout.layout(model(viaModel), new Rect(0, 0, 8, 5));

      assertArrayEquals(snapshot(direct), snapshot(viaModel), 0);
   }
}
