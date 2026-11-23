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

/**
 * LayoutCalculations and LayoutDifference (no production caller, covered as library classes).
 */
@Tag("core")
class LayoutCalculationsTest {
   private static MapItem[] placed(int[]... rects) {
      MapItem[] items = new MapItem[rects.length];

      for(int i = 0; i < rects.length; i++) {
         items[i] = new MapItem(1, i);
         items[i].setBounds(rects[i][0], rects[i][1], rects[i][2], rects[i][3]);
      }

      return items;
   }

   private static MapItem[] straightLine() {
      return placed(new int[] { 0, 0, 1, 1 }, new int[] { 1, 0, 1, 1 }, new int[] { 2, 0, 1, 1 });
   }

   private static MapItem[] lShape() {
      return placed(new int[] { 0, 0, 1, 1 }, new int[] { 1, 0, 1, 1 }, new int[] { 1, 1, 1, 1 });
   }

   @Test
   void averageAspectRatioIsTheMeanOfTheRects() {
      MapItem[] items = placed(new int[] { 0, 0, 2, 1 }, new int[] { 0, 0, 1, 1 });
      assertEquals(1.5, LayoutCalculations.averageAspectRatio(items), 1e-12);
   }

   @Test
   void averageAspectRatioOfEmptyArrayIsZero() {
      assertEquals(0.0, LayoutCalculations.averageAspectRatio(new Mappable[0]), 0);
   }

   @Test
   @Disabled("Bug #82108 (unused code, delete it or fix it: no caller in the project): averageAspectRatio(null) throws NullPointerException (m.length is read before the " +
             "null check); expected 0.0")
   void averageAspectRatioOfNullIsZero() {
      assertEquals(0.0, LayoutCalculations.averageAspectRatio((Mappable[]) null), 0);
   }

   @Test
   void readabilityOfItemsOnALineIsOne() {
      assertEquals(1.0, LayoutCalculations.getReadability(model(straightLine())), 1e-12);
   }

   @Test
   void readabilityCountsOneTurnOfThreeItems() {
      assertEquals(2.0 / 3, LayoutCalculations.getReadability(model(lShape())), 1e-12);
   }

   @Test
   @Disabled("Bug #82108 (unused code, delete it or fix it: no caller in the project): getReadability of a model with 0 items is NaN (0/0); expected 1.0 like the array " +
             "overload")
   void readabilityOfEmptyModelIsOne() {
      assertEquals(1.0, LayoutCalculations.getReadability(model(new Mappable[0])), 1e-12);
   }

   @Test
   void weightedReadabilityOfSeveralModels() {
      MapModel[] models = { model(straightLine()), model(lShape()) };
      assertEquals(5.0 / 6, LayoutCalculations.getReadability(models), 1e-12);
   }

   @Test
   void itemIndexIsThePositionOfTheItemWithThatOrder() {
      MapItem[] items = { new MapItem(1, 2), new MapItem(1, 0), new MapItem(1, 1) };
      MapModel model = model(items);
      assertEquals(1, LayoutCalculations.getItemIndex(0, model));
      assertEquals(2, LayoutCalculations.getItemIndex(1, model));
      assertEquals(0, LayoutCalculations.getItemIndex(2, model));
   }

   @Test
   void averageDistanceAfterMovingOneOfTwoItems() {
      MapItem[] items = placed(new int[] { 0, 0, 1, 1 }, new int[] { 5, 5, 1, 1 });
      LayoutDifference diff = new LayoutDifference();
      diff.recordLayout(items);
      items[1].setBounds(8, 9, 1, 1);
      assertEquals(2.5, diff.averageDistance(items), 1e-12);
   }

   @Test
   void averageDistanceWithoutChangeIsZero() {
      MapItem[] items = placed(new int[] { 0, 0, 1, 1 }, new int[] { 5, 5, 1, 1 });
      LayoutDifference diff = new LayoutDifference();
      diff.recordLayout(items);
      assertEquals(0.0, diff.averageDistance(items), 0);
   }

   @Test
   void averageDistanceIsZeroWithoutRecordingOrWithDifferentItemCount() {
      MapItem[] two = placed(new int[] { 0, 0, 1, 1 }, new int[] { 5, 5, 1, 1 });
      LayoutDifference diff = new LayoutDifference();
      assertEquals(0.0, diff.averageDistance(two), 0);

      diff.recordLayout(two);
      MapItem[] three = placed(new int[] { 0, 0, 1, 1 }, new int[] { 5, 5, 1, 1 },
                               new int[] { 7, 7, 1, 1 });
      assertEquals(0.0, diff.averageDistance(three), 0);
   }

   @Test
   void recordedLayoutIsACopy() {
      MapItem[] items = placed(new int[] { 0, 0, 1, 1 });
      LayoutDifference diff = new LayoutDifference();
      diff.recordLayout(items);
      items[0].getBounds().x = 3;
      items[0].getBounds().y = 4;
      assertEquals(5.0, diff.averageDistance(items), 1e-12);
   }
}
