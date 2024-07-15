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
package inetsoft.util.algo;

/**
 * A merge sort algorithm that sort an integer array using a comparactor.
 * @since 12.3
 */
public abstract class IntArraySort {
   public abstract int[] sort(int[] a, int lo, int hi, IntComparator c);

   public void cancel() {
      cancelled = true;
   }

   public static class IntComparator {
      public int compare(int v1, int v2) {
         return v1 - v2;
      }
   }

   protected boolean cancelled = false;
}
