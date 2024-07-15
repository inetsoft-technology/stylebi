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
package inetsoft.util.algo;

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SreeHome()
class IntArrayDistinctMergeSortTest {
   @Test
   void testList() {
      int[] arr1 = {5, 1, 4, 4, 3, 5, 2, 2, 3, 3, 1, 1, 3, 4};
      int[] out1 = {1, 2, 3, 4, 5};
      int[] arr2 = {1, 4, 4, 3, 2, 4};
      int[] out2 = {1, 2, 3, 4};
      int[] arr3 = {5, 1, 4, 4, 3, 5, 2};
      int[] out3 = {1, 2, 3, 4, 5};
      int[] arr4 = {5, 5, 5, 5, 5, 5, 5};
      int[] out4 = {5};
      int[] arr5 = {5, 3, 2, 4, 4, 2, 2};
      int[] out5 = {2, 3, 4, 5};
      int[] arr6 = {0, 1, 2, 2, 3, 4, 5};
      int[] out6 = {0, 1, 2, 3, 4, 5};

      assertTrue(arr1, out1);
      assertTrue(arr2, out2);
      assertTrue(arr3, out3);
      assertTrue(arr4, out4);
      assertTrue(arr5, out5);
      assertTrue(arr6, out6);
   }

   static void assertTrue(int[] input, int[] expected) {
      IntArraySort sort = new IntArrayDistinctMergeSort();
      input = sort.sort(input, 0, input.length, new IntArraySort.IntComparator());
      assertArrayEquals(expected, input);
   }
}
