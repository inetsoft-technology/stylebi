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
 * A JDK 1.0 - compatible rectangle class that
 * accepts double-valued parameters.
 */
public class Rect {
   public double x, y, w, h;

   public Rect() {
      this(0, 0, 1, 1);
   }

   public Rect(Rect r) {
      setRect(r.x, r.y, r.w, r.h);
   }

   public Rect(double x, double y, double w, double h) {
      setRect(x, y, w, h);
   }

   public void setRect(double x, double y, double w, double h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
   }

   public double aspectRatio() {
      return Math.max(w / h, h / w);
   }

   public double distance(Rect r) {
      return Math.sqrt((r.x - x) * (r.x - x) +
                          (r.y - y) * (r.y - y) +
                          (r.w - w) * (r.w - w) +
                          (r.h - h) * (r.h - h));
   }

   public Rect copy() {
      return new Rect(x, y, w, h);
   }

   public String toString() {
      return "Rect: " + x + ", " + y + ", " + w + ", " + h;
   }

}
