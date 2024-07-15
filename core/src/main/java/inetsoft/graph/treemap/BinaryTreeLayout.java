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
 * This layout uses a static binary tree for
 * dividing map items. It is not great with regard to
 * aspect ratios or ordering, but it has excellent
 * stability properties.
 */
public class BinaryTreeLayout extends AbstractMapLayout {
   public void layout(Mappable[] items, Rect bounds) {
      layout(items, 0, items.length - 1, bounds);
   }

   public void layout(Mappable[] items, int start, int end, Rect bounds) {
      layout(items, start, end, bounds, true);
   }

   public void layout(Mappable[] items, int start, int end, Rect bounds, boolean vertical) {
      if(start > end) {
         return;
      }
      //throw new IllegalArgumentException("start, end= "+start+", "+end);

      if(start == end) {
         items[start].setBounds(bounds);
         return;
      }

      int mid = (start + end) / 2;

      double total = sum(items, start, end);
      double first = sum(items, start, mid);

      double a = first / total;
      double x = bounds.x, y = bounds.y, w = bounds.w, h = bounds.h;

      if(vertical) {
         Rect b1 = new Rect(x, y, w * a, h);
         Rect b2 = new Rect(x + w * a, y, w * (1 - a), h);
         layout(items, start, mid, b1, !vertical);
         layout(items, mid + 1, end, b2, !vertical);
      }
      else {
         Rect b1 = new Rect(x, y, w, h * a);
         Rect b2 = new Rect(x, y + h * a, w, h * (1 - a));
         layout(items, start, mid, b1, !vertical);
         layout(items, mid + 1, end, b2, !vertical);
      }

   }

   private double normAspect(double a, double b) {
      return Math.max(a / b, b / a);
   }

   private double sum(Mappable[] items, int start, int end) {
      double s = 0;
      for(int i = start; i <= end; i++) {
         s += items[i].getSize();
      }
      return s;
   }

   private int findMax(Mappable[] items, int start, int end) {
      double m = 0;
      int n = -1;
      for(int i = start; i <= end; i++) {
         double s = items[i].getSize();
         if(s >= m) {
            m = s;
            n = i;
         }
      }
      return n;
   }

   public String getName() {
      return "Binary Tree";
   }

   public String getDescription() {
      return "Uses a static binary tree layout.";
   }


}
