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
package inetsoft.util;

import java.util.Objects;

public class Insets2D {
   public Insets2D(double top, double left, double bottom, double right) {
      this.top = top;
      this.left = left;
      this.bottom = bottom;
      this.right = right;
   }

   public void set(double top, double left, double bottom, double right) {
      this.top = top;
      this.left = left;
      this.bottom = bottom;
      this.right = right;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Insets2D insets2D = (Insets2D) o;
      return Double.compare(insets2D.top, top) == 0 &&
         Double.compare(insets2D.left, left) == 0 &&
         Double.compare(insets2D.bottom, bottom) == 0 &&
         Double.compare(insets2D.right, right) == 0;
   }

   @Override
   public int hashCode() {
      return Objects.hash(top, left, bottom, right);
   }

   @Override
   public String toString() {
      return "Insets2D{" +
         "top=" + top +
         ", left=" + left +
         ", bottom=" + bottom +
         ", right=" + right +
         '}';
   }

   public double top;
   public double left;
   public double bottom;
   public double right;
}
