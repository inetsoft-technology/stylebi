/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

/**
 * "Squarified" treemap layout invented by
 * J.J. van Wijk.
 */
public class SquarifiedLayout extends AbstractMapLayout {
   public void layout(Mappable[] items, Rect bounds) {
      layout(sortDescending(items), 0, items.length - 1, bounds);
   }

   public void layout(Mappable[] items, int start, int end, Rect bounds) {
      if(start > end) {
         return;
      }

      if(end - start < 2) {
         SliceLayout.layoutBest(items, start, end, bounds);
         return;
      }

      final double x = bounds.x, y = bounds.y, w = bounds.w, h = bounds.h;

      double total = sum(items, start, end);
      int mid = start;
      double a = items[start].getSize() / total;
      double b = 0;

      if(w < h) {
         // height/width
         while(mid <= end) {
            double aspect = normAspect(h, w, a, b);
            double q = items[mid].getSize() / total;
            b += q;
            if(normAspect(h, w, a, b + q) > aspect) {
               break;
            }
            mid++;
         }

         if(mid > end) {
            return;
         }

         SliceLayout.layoutBest(items, start, mid, new Rect(x, y, w, h * b));
         layout(items, mid + 1, end, new Rect(x, y + h * b, w, h * (1 - b)));
      }
      else {
         // width/height
         while(mid <= end) {
            double aspect = normAspect(w, h, a, b);
            double q = items[mid].getSize() / total;
            b += q;
            if(normAspect(w, h, a, b + q) > aspect) {
               break;
            }
            mid++;
         }

         if(mid > end) {
            return;
         }

         SliceLayout.layoutBest(items, start, mid, new Rect(x, y, w * b, h));
         layout(items, mid + 1, end, new Rect(x + w * b, y, w * (1 - b), h));
      }
   }

   private double aspect(double big, double small, double a, double b) {
      return (big * b) / (small * a / b);
   }

   private double normAspect(double big, double small, double a, double b) {
      double x = aspect(big, small, a, b);
      if(x < 1) {
         return 1 / x;
      }
      return x;
   }

   private double sum(Mappable[] items, int start, int end) {
      double s = 0;
      for(int i = start; i <= end; i++) {
         s += items[i].getSize();
      }
      return s;
   }

   public String getName() {
      return "Squarified";
   }

   public String getDescription() {
      return "Algorithm used by J.J. van Wijk.";
   }


}
