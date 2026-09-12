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
package inetsoft.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatrixOperationTest {

   // ---- toSize(int[], int nrow) ----

   @Test
   void toSizeSameSizeReturnsSameArray() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.toSize(arr, 3);
      assertSame(arr, result);
   }

   @Test
   void toSizeGrowHasCorrectLength() {
      int[] arr = {10, 20};
      int[] result = MatrixOperation.toSize(arr, 4);
      assertEquals(4, result.length);
   }

   @Test
   void toSizeGrowPreservesExistingValues() {
      int[] arr = {10, 20};
      int[] result = MatrixOperation.toSize(arr, 4);
      assertEquals(10, result[0]);
      assertEquals(20, result[1]);
   }

   @Test
   void toSizeGrowInitializesNewItemsToNegativeOne() {
      int[] arr = {10, 20};
      int[] result = MatrixOperation.toSize(arr, 4);
      assertEquals(-1, result[2]);
      assertEquals(-1, result[3]);
   }

   @Test
   void toSizeShrinkHasCorrectLength() {
      int[] arr = {10, 20, 30, 40};
      int[] result = MatrixOperation.toSize(arr, 2);
      assertEquals(2, result.length);
   }

   @Test
   void toSizeShrinkPreservesValues() {
      int[] arr = {10, 20, 30, 40};
      int[] result = MatrixOperation.toSize(arr, 2);
      assertEquals(10, result[0]);
      assertEquals(20, result[1]);
   }

   @Test
   void toSizeToZeroReturnsEmptyArray() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.toSize(arr, 0);
      assertEquals(0, result.length);
   }

   // ---- toSize(T[][], int nrow, int ncol) ----

   @Test
   void toSize2DSameSizeReturnsSameArray() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 2, 2);
      assertSame(arr, result);
   }

   @Test
   void toSize2DGrowHasCorrectRowCount() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 3, 3);
      assertEquals(3, result.length);
   }

   @Test
   void toSize2DGrowHasCorrectColCount() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 3, 3);
      assertEquals(3, result[0].length);
   }

   @Test
   void toSize2DGrowPreservesExistingValues() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 3, 3);
      assertEquals("a", result[0][0]);
      assertEquals("b", result[0][1]);
      assertEquals("c", result[1][0]);
      assertEquals("d", result[1][1]);
   }

   @Test
   void toSize2DGrowNewRowCellsAreNull() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 3, 3);
      assertNull(result[2][0]);
      assertNull(result[2][1]);
      assertNull(result[2][2]);
   }

   @Test
   void toSize2DGrowNewColumnCellsAreNull() {
      String[][] arr = {{"a", "b"}, {"c", "d"}};
      String[][] result = MatrixOperation.toSize(arr, 3, 3);
      assertNull(result[0][2]);
      assertNull(result[1][2]);
   }

   @Test
   void toSize2DShrinkRowsHasCorrectLength() {
      String[][] arr = {{"a", "b"}, {"c", "d"}, {"e", "f"}};
      String[][] result = MatrixOperation.toSize(arr, 2, 2);
      assertEquals(2, result.length);
   }

   @Test
   void toSize2DShrinkColumnsHasCorrectColLength() {
      String[][] arr = {{"a", "b", "c"}, {"d", "e", "f"}};
      String[][] result = MatrixOperation.toSize(arr, 2, 2);
      assertEquals(2, result[0].length);
   }

   @Test
   void toSize2DShrinkColumnsPreservesValues() {
      String[][] arr = {{"a", "b", "c"}, {"d", "e", "f"}};
      String[][] result = MatrixOperation.toSize(arr, 2, 2);
      assertEquals("a", result[0][0]);
      assertEquals("b", result[0][1]);
   }

   // ---- insertRow(int[], int row) ----

   @Test
   void insertRowAtBeginningHasCorrectLength() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertRow(arr, 0);
      assertEquals(4, result.length);
   }

   @Test
   void insertRowAtBeginningInsertsNegativeOne() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertRow(arr, 0);
      assertEquals(-1, result[0]);
   }

   @Test
   void insertRowAtBeginningShiftsExistingValues() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertRow(arr, 0);
      assertEquals(1, result[1]);
      assertEquals(2, result[2]);
      assertEquals(3, result[3]);
   }

   @Test
   void insertRowAtMiddle() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertRow(arr, 1);
      assertEquals(4, result.length);
      assertEquals(1, result[0]);
      assertEquals(-1, result[1]);
      assertEquals(2, result[2]);
      assertEquals(3, result[3]);
   }

   @Test
   void insertRowAtEnd() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertRow(arr, 3);
      assertEquals(4, result.length);
      assertEquals(1, result[0]);
      assertEquals(2, result[1]);
      assertEquals(3, result[2]);
      assertEquals(-1, result[3]);
   }

   @Test
   void insertRowIntoSingleElementArray() {
      int[] arr = {5};
      int[] result = MatrixOperation.insertRow(arr, 0);
      assertEquals(2, result.length);
      assertEquals(-1, result[0]);
      assertEquals(5, result[1]);
   }

   // ---- removeRow(int[], int row) ----

   @Test
   void removeRowAtBeginningHasCorrectLength() {
      int[] arr = {10, 20, 30};
      int[] result = MatrixOperation.removeRow(arr, 0);
      assertEquals(2, result.length);
   }

   @Test
   void removeRowAtBeginningShiftsValues() {
      int[] arr = {10, 20, 30};
      int[] result = MatrixOperation.removeRow(arr, 0);
      assertEquals(20, result[0]);
      assertEquals(30, result[1]);
   }

   @Test
   void removeRowAtMiddle() {
      int[] arr = {10, 20, 30};
      int[] result = MatrixOperation.removeRow(arr, 1);
      assertEquals(2, result.length);
      assertEquals(10, result[0]);
      assertEquals(30, result[1]);
   }

   @Test
   void removeRowAtEnd() {
      int[] arr = {10, 20, 30};
      int[] result = MatrixOperation.removeRow(arr, 2);
      assertEquals(2, result.length);
      assertEquals(10, result[0]);
      assertEquals(20, result[1]);
   }

   @Test
   void removeRowFromSingleElementArray() {
      int[] arr = {99};
      int[] result = MatrixOperation.removeRow(arr, 0);
      assertEquals(0, result.length);
   }

   // ---- insertColumn / removeColumn (int[]) delegate to row operations ----

   @Test
   void insertColumnDelegatesToInsertRow() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.insertColumn(arr, 1);
      int[] expected = MatrixOperation.insertRow(new int[]{1, 2, 3}, 1);
      assertArrayEquals(expected, result);
   }

   @Test
   void removeColumnDelegatesToRemoveRow() {
      int[] arr = {1, 2, 3};
      int[] result = MatrixOperation.removeColumn(arr, 1);
      int[] expected = MatrixOperation.removeRow(new int[]{1, 2, 3}, 1);
      assertArrayEquals(expected, result);
   }

   // ---- insertRow / removeRow (Object[]) ----

   @Test
   void insertRowObjectArrayAtStartHasCorrectLength() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.insertRow(arr, 0);
      assertEquals(4, result.length);
   }

   @Test
   void insertRowObjectArrayInsertsNull() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.insertRow(arr, 0);
      assertNull(result[0]);
      assertEquals("a", result[1]);
   }

   @Test
   void removeRowObjectArrayAtEndHasCorrectLength() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.removeRow(arr, 2);
      assertEquals(2, result.length);
   }

   @Test
   void removeRowObjectArrayPreservesRemaining() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.removeRow(arr, 2);
      assertEquals("a", result[0]);
      assertEquals("b", result[1]);
   }

   // ---- insertColumn / removeColumn (Object[]) ----

   @Test
   void insertColumnObjectArrayHasCorrectLength() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.insertColumn(arr, 1);
      assertEquals(4, result.length);
   }

   @Test
   void insertColumnObjectArrayInsertsNull() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.insertColumn(arr, 1);
      assertEquals("a", result[0]);
      assertNull(result[1]);
      assertEquals("b", result[2]);
   }

   @Test
   void removeColumnObjectArrayRemovesElement() {
      String[] arr = {"a", "b", "c"};
      Object[] result = MatrixOperation.removeColumn(arr, 0);
      assertEquals(2, result.length);
      assertEquals("b", result[0]);
      assertEquals("c", result[1]);
   }

   // ---- clone(int[]) ----

   @Test
   void cloneIntArrayHasSameValues() {
      int[] arr = {5, 10, 15};
      int[] cloned = MatrixOperation.clone(arr);
      assertArrayEquals(arr, cloned);
   }

   @Test
   void cloneIntArrayProducesIndependentCopy() {
      int[] arr = {1, 2, 3};
      int[] cloned = MatrixOperation.clone(arr);
      cloned[0] = 99;
      assertEquals(1, arr[0]);
   }
}
