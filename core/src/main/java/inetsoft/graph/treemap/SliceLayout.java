/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.treemap;

/**
 * The original slice-and-dice layout for treemaps.
 */
public class SliceLayout extends AbstractMapLayout {
   public static final int BEST = 2, ALTERNATE = 3;
   private int orientation;

   public SliceLayout() {
      this(ALTERNATE);
   }

   public SliceLayout(int orientation) {
      this.orientation = orientation;
   }

   public void layout(Mappable[] items, Rect bounds) {
      if(items.length == 0) {
         return;
      }
      int o = orientation;
      if(o == BEST) {
         layoutBest(items, 0, items.length - 1, bounds);
      }
      else if(o == ALTERNATE) {
         layout(items, bounds, items[0].getDepth() % 2);
      }
      else {
         layout(items, bounds, o);
      }
   }

   public static void layoutBest(Mappable[] items, int start, int end, Rect bounds) {
      sliceLayout(items, start, end, bounds,
                  bounds.w > bounds.h ? HORIZONTAL : VERTICAL, ASCENDING);
   }

   public static void layout(Mappable[] items, Rect bounds, int orientation) {
      sliceLayout(items, 0, items.length - 1, bounds, orientation);
   }

   public String getName() {
      return "Slice-and-dice";
   }

   public String getDescription() {
      return "This is the original treemap algorithm, " +
         "which has excellent stability properies " +
         "but leads to high aspect ratios.";
   }
}
