/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.algo;

import java.util.Arrays;

/**
 * A merge sort algorithm that sort an integer array using a comparactor.
 *
 * @since 12.3
 */
public class IntArrayDistinctMergeSort extends IntArraySort {
   public int[] sort(int[] arr, int low, int high, IntComparator comp) {
      // avoid infinite recursion
      if(low >= high) {
         return arr;
      }

      int[] aux = arr.clone();
      int cnt = mergeSort(aux, arr, comp, low, high);
      aux = null;

      return (low + cnt == arr.length) ? arr : Arrays.copyOf(arr, low + cnt);
   }

   /**
    * Perform a merge sort.
    * @return number of items in the dest.
    */
   private int mergeSort(int[] src, int[] dest, IntComparator comp, int low, int high) {
      int length = high - low;

      if(cancelled || length == 0) {
         return 0;
      }

      // insertion sort small array
      if(length == 1) {
         return 1;
      }
      else if(length == 2) {
         int rc = comp.compare(src[low], src[low + 1]);

         if(rc == 0) {
            dest[low] = src[low];
            return 1;
         }
         else {
            if(rc > 0) {
               dest[low] = src[low + 1];
               dest[low + 1] = src[low];
            }

            return 2;
         }
      }

      int mid = (low + high) / 2;

      int cnt1 = mergeSort(dest, src, comp, low, mid);
      int cnt2 = mergeSort(dest, src, comp, mid, high);

      if(cancelled) {
         return 0;
      }

      // already sorted, copy from src to dest
      if(comp.compare(src[low + cnt1 - 1], src[mid]) < 0) {
         System.arraycopy(src, low, dest, low, cnt1);
         System.arraycopy(src, mid, dest, low + cnt1, cnt2);
         return cnt1 + cnt2;
      }

      // merge into dest
      int n = low;
      int pmax = low + cnt1;
      int qmax = mid + cnt2;

      for(int p = low, q = mid; p < pmax || q < qmax; ) {
         int next;

         if(q >= qmax || p < pmax && comp.compare(src[p], src[q]) <= 0) {
            next = src[p++];
         }
         else {
            next = src[q++];
         }

         if(n == low || comp.compare(next, dest[n - 1]) != 0) {
            dest[n++] = next;
         }
      }

      return n - low;
   }

   /**
    * Swaps x[a] with x[b].
    */
   private static final void swap(int[] x, int a, int b) {
      int t = x[a];

      x[a] = x[b];
      x[b] = t;
   }
}
