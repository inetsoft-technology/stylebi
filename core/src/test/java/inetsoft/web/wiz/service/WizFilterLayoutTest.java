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
package inetsoft.web.wiz.service;

import inetsoft.uql.asset.AbstractSheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WizFilterLayout.pack is pure and static — no RuntimeViewsheet/server/DB needed, per the design
 * doc's own test plan (section 9).
 */
@Tag("core")
class WizFilterLayoutTest {

   @Test
   void oneControlPlacedDirectlyBelowTheChartAtItsPreferredSize() {
      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET));

      assertEquals(1, rects.size());
      assertEquals(new Rectangle(0, 250, 100, 120), rects.get(0));
   }

   @Test
   void oneControlIsOffsetByTheChartsOwnPixelOffset() {
      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(50, 30), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET));

      assertEquals(1, rects.size());
      // x is chartOffset.x + 0 (local); y is chartOffset.y + chartSize.height + GAP.
      assertEquals(new Rectangle(50, 280, 100, 120), rects.get(0));
   }

   @Test
   void threeControlsOfDifferentShapesPackIntoTwoShelvesSortedByHeightDescending() {
      // Input order deliberately NOT height-sorted, to prove the algorithm sorts internally but
      // still returns results zipped back to the ORIGINAL input order (see the last test below).
      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET, AbstractSheet.TIME_SLIDER_ASSET, AbstractSheet.CALENDAR_ASSET));

      assertEquals(3, rects.size());
      // Shelf 1 (y=250): Calendar (220 tall, placed first) then SelectionList (120 tall) fits beside it
      // (0+200+10=210, 210+100=310 <= 400). Shelf 2 (y=250+220+10=480): TimeSlider, which does not fit
      // beside Calendar+SelectionList (210+100+10=320, 320+280=600 > 400).
      assertEquals(new Rectangle(210, 250, 100, 120), rects.get(0), "SelectionList");
      assertEquals(new Rectangle(0, 480, 280, 50), rects.get(1), "TimeSlider");
      assertEquals(new Rectangle(0, 250, 200, 220), rects.get(2), "Calendar");
   }

   @Test
   void aControlWiderThanTheChartIsClampedAndItsHeightScaledByTheSameRatio() {
      // Chart narrower than SelectionList's 100px preferred width.
      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(50, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET));

      assertEquals(1, rects.size());
      // ratio = 50/100 = 0.5; height = round(120 * 0.5) = 60.
      assertEquals(new Rectangle(0, 250, 50, 60), rects.get(0));
   }

   @Test
   void packIsDeterministicAcrossRepeatedCalls() {
      Point offset = new Point(0, 0);
      Dimension chartSize = new Dimension(400, 240);
      List<Integer> types = List.of(
         AbstractSheet.SELECTION_LIST_ASSET, AbstractSheet.TIME_SLIDER_ASSET, AbstractSheet.CALENDAR_ASSET);

      List<Rectangle> first = WizFilterLayout.pack(offset, chartSize, types);
      List<Rectangle> second = WizFilterLayout.pack(offset, chartSize, types);

      assertEquals(first, second);
   }

   @Test
   void outputOrderMatchesInputOrderRegardlessOfInternalHeightSort() {
      // Already-height-descending input (Calendar, SelectionList, TimeSlider) should still come
      // back in that exact order, not the algorithm's internal sort order.
      List<Integer> types = List.of(
         AbstractSheet.CALENDAR_ASSET, AbstractSheet.SELECTION_LIST_ASSET, AbstractSheet.TIME_SLIDER_ASSET);
      List<Rectangle> rects = WizFilterLayout.pack(new Point(0, 0), new Dimension(400, 240), types);

      assertEquals(200, rects.get(0).width, "Calendar first, matching input order");
      assertEquals(100, rects.get(1).width, "SelectionList second, matching input order");
      assertEquals(280, rects.get(2).width, "TimeSlider third, matching input order");
   }

   @Test
   void unsupportedControlTypeFailsLoud() {
      assertThrows(IllegalArgumentException.class, () -> WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240), List.of(AbstractSheet.SELECTION_TREE_ASSET)));
   }

   // ── packing around already-placed controls (06-review-r1.md Important finding) ─────────────

   @Test
   void withNoOccupiedRectanglesMatchesTheThreeArgOverloadExactly() {
      Point offset = new Point(0, 0);
      Dimension chartSize = new Dimension(400, 240);
      List<Integer> types = List.of(AbstractSheet.SELECTION_LIST_ASSET, AbstractSheet.CALENDAR_ASSET);

      List<Rectangle> viaThreeArg = WizFilterLayout.pack(offset, chartSize, types);
      List<Rectangle> viaFourArg = WizFilterLayout.pack(offset, chartSize, types, List.of());

      assertEquals(viaThreeArg, viaFourArg);
   }

   @Test
   void aSecondCallWithDifferentFieldsPacksBesideTheFirstCallsControlNotOnTopOfIt() {
      // Call 1: one SelectionList, placed at its usual spot.
      List<Rectangle> first = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240), List.of(AbstractSheet.SELECTION_LIST_ASSET));
      Rectangle regionControl = first.get(0);
      assertEquals(new Rectangle(0, 250, 100, 120), regionControl);

      // Call 2 (separate, later call): two NEW fields, REGION's control still live and occupying
      // its rectangle. Must not overlap it.
      List<Rectangle> second = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.TIME_SLIDER_ASSET, AbstractSheet.SELECTION_LIST_ASSET),
         List.of(regionControl));

      for(Rectangle r : second) {
         assertFalse(r.intersects(regionControl), "new control " + r + " must not overlap " + regionControl);
      }
   }

   @Test
   void newControlsAppendToTheSameShelfRowWhenThereIsRoom() {
      // A 100-wide SelectionList already sits at the start of shelf 1 in a 400-wide chart -- plenty
      // of room left on that row (400 - 100 - GAP = 290) for another SelectionList (100 wide).
      Rectangle existing = new Rectangle(0, 250, 100, 120);

      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET), List.of(existing));

      assertEquals(new Rectangle(110, 250, 100, 120), rects.get(0), "appends beside, same shelf row");
   }

   @Test
   void newControlsWrapToANewShelfWhenTheExistingRowHasNoRoomLeft() {
      // Existing control already occupies the row almost edge-to-edge for a 400-wide chart -- not
      // enough room left for another 100-wide SelectionList (400 - 350 - GAP = 40 < 100).
      Rectangle existing = new Rectangle(0, 250, 350, 120);

      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET), List.of(existing));

      assertEquals(new Rectangle(0, 380, 100, 120), rects.get(0), "wraps to a new shelf row below");
   }

   @Test
   void neverRepositionsAnythingInOccupied() {
      // pack() only returns rectangles for orderedTypes -- occupied is read-only input. Assert this
      // by confirming the returned rectangles are disjoint from occupied across a few shapes/sizes,
      // i.e. nothing in the return value silently echoes back a moved copy of `existing`.
      Rectangle existing = new Rectangle(50, 260, 200, 220);
      List<Rectangle> rects = WizFilterLayout.pack(
         new Point(0, 0), new Dimension(400, 240),
         List.of(AbstractSheet.SELECTION_LIST_ASSET, AbstractSheet.TIME_SLIDER_ASSET), List.of(existing));

      for(Rectangle r : rects) {
         assertNotEquals(existing, r);
         assertFalse(r.intersects(existing));
      }
   }
}
