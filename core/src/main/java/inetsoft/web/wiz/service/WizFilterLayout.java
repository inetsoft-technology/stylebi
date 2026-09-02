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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Packs 1-3 filter control assemblies into a shelf grid below a chart, per
 * docs/superpowers/specs/2026-09-01-visualization-scoped-filters-design.md section 2.4. Pure and
 * stateless: no RuntimeViewsheet/DB/network dependency, so it is independently unit-testable
 * (WizFilterLayoutTest).
 */
public final class WizFilterLayout {
   private WizFilterLayout() {
   }

   /** Fixed gap, in pixels, between packed controls and between the chart and the first shelf. */
   private static final int GAP = 10;

   /**
    * Preferred (width, height) per control type. SelectionList (100x120) was measured live against
    * a real StyleBI checkout (see the design doc's section 10.1); Calendar (200x220) and TimeSlider
    * (280x50) are still estimates as of this build (design doc section 8).
    */
   private static final Map<Integer, Dimension> PREFERRED_SIZES = Map.of(
      AbstractSheet.SELECTION_LIST_ASSET, new Dimension(100, 120),
      AbstractSheet.CALENDAR_ASSET, new Dimension(200, 220),
      AbstractSheet.TIME_SLIDER_ASSET, new Dimension(280, 50)
   );

   /**
    * Computes packed placement rectangles for {@code orderedTypes}, one per input entry, in the
    * SAME order as the input (callers zip the result back against their own field list by index)
    * even though placement internally sorts by preferred height descending (shelf-packing: tallest
    * items anchor each shelf).
    *
    * <p>Equivalent to {@link #pack(Point, Dimension, List, List)} with an empty occupied list — no
    * already-placed controls to pack around (a single-call sequence, or the first call ever).
    *
    * @param chartOffset  the chart assembly's own pixel offset (packed controls' x is relative to
    *                     {@code chartOffset.x}, so the filter zone aligns under the chart).
    * @param chartSize    the chart assembly's own pixel size (controls are packed starting at
    *                     {@code chartOffset.y + chartSize.height + GAP}, width-limited to
    *                     {@code chartSize.width}).
    * @param orderedTypes one {@link AbstractSheet} asset-type code
    *                     (SELECTION_LIST_ASSET/TIME_SLIDER_ASSET/CALENDAR_ASSET) per requested
    *                     control, in the caller's original order.
    * @return one {@link Rectangle} per input entry, same order as {@code orderedTypes}.
    */
   public static List<Rectangle> pack(Point chartOffset, Dimension chartSize, List<Integer> orderedTypes) {
      return pack(chartOffset, chartSize, orderedTypes, List.of());
   }

   /**
    * Same as {@link #pack(Point, Dimension, List)}, but packs the new controls AROUND rectangles
    * already occupied by controls a previous {@code add_visualization_filters} call placed (06-
    * review-r1.md Important finding: without this, every call restarted at
    * {@code chartOffset.y + chartSize.height + GAP} regardless of what was already there, so a
    * second call with different fields landed directly on top of the first call's controls).
    * Never repositions anything in {@code occupied} — those rectangles are read-only input, purely
    * to avoid new placements overlapping them.
    *
    * @param occupied  pixel rectangles already occupied by live, still-present filter controls (in
    *                  the SAME coordinate space as the returned rectangles — i.e. already offset by
    *                  the chart's own pixel offset). Empty when there is nothing to pack around.
    */
   public static List<Rectangle> pack(
      Point chartOffset, Dimension chartSize, List<Integer> orderedTypes, List<Rectangle> occupied)
   {
      int n = orderedTypes.size();
      // A chart width of 0/negative (never expected in practice) would otherwise divide-by-zero
      // below when clamping; 1px is not a meaningful "minimum layout width" the way the design's
      // illustrative "e.g. 300px" floor was — that value exceeds every current preferred width
      // (max 280, TimeSlider), which would make the clamp branch below unreachable and untestable.
      int availableWidth = Math.max(chartSize.width, 1);

      Dimension[] sizes = new Dimension[n];

      for(int i = 0; i < n; i++) {
         int type = orderedTypes.get(i);
         Dimension preferred = PREFERRED_SIZES.get(type);

         if(preferred == null) {
            throw new IllegalArgumentException("Unsupported filter control type: " + type);
         }

         if(preferred.width > availableWidth) {
            double ratio = (double) availableWidth / preferred.width;
            sizes[i] = new Dimension(availableWidth, (int) Math.round(preferred.height * ratio));
         }
         else {
            sizes[i] = new Dimension(preferred.width, preferred.height);
         }
      }

      // Indices sorted by preferred height descending; Arrays.sort on a reference-type array with a
      // Comparator is a stable sort, so ties keep the original input order.
      Integer[] order = new Integer[n];

      for(int i = 0; i < n; i++) {
         order[i] = i;
      }

      Arrays.sort(order, Comparator.comparingInt((Integer i) -> sizes[i].height).reversed());

      // Seed the shelf cursor from whatever is already occupied, instead of always starting fresh
      // at the chart's own bottom edge. Rows are derived from `occupied` by grouping rectangles
      // whose y-ranges overlap (this mirrors how this same method laid them out originally, one
      // call at a time) -- the LAST such row (greatest y) is where a new item can still append, at
      // its rightmost occupied edge; anything short of that starts a fresh row below it.
      int x = 0;
      int y = chartOffset.y + chartSize.height + GAP;
      int shelfHeight = 0;

      if(!occupied.isEmpty()) {
         List<Rectangle> sorted = new ArrayList<>(occupied);
         sorted.sort(Comparator.comparingInt(r -> r.y));

         int rowTop = sorted.get(0).y;
         int rowBottom = sorted.get(0).y + sorted.get(0).height;
         int rowRight = sorted.get(0).x + sorted.get(0).width;

         for(int i = 1; i < sorted.size(); i++) {
            Rectangle r = sorted.get(i);

            if(r.y < rowBottom) {
               // Same shelf row as the running group (y-ranges overlap).
               rowBottom = Math.max(rowBottom, r.y + r.height);
               rowRight = Math.max(rowRight, r.x + r.width);
            }
            else {
               rowTop = r.y;
               rowBottom = r.y + r.height;
               rowRight = r.x + r.width;
            }
         }

         x = rowRight - chartOffset.x + GAP;
         y = rowTop;
         shelfHeight = rowBottom - rowTop;
      }

      Rectangle[] result = new Rectangle[n];

      for(int idx : order) {
         Dimension size = sizes[idx];

         if(x > 0 && x + size.width > availableWidth) {
            y += shelfHeight + GAP;
            x = 0;
            shelfHeight = 0;
         }

         result[idx] = new Rectangle(chartOffset.x + x, y, size.width, size.height);
         x += size.width + GAP;
         shelfHeight = Math.max(shelfHeight, size.height);
      }

      return Arrays.asList(result);
   }
}
