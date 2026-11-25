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
package inetsoft.graph.data;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82041: {@link SubDataSet#getRowFromBase(int)} takes a row of the root data set. On a
 * sub data set nested in another sub data set (multi-level facet) it must answer with the row of
 * this set whose root row is the argument, or {@code -(insertion point) - 1} in this set's own
 * row space when the root row is not in this set.
 * <p>
 * Uses a Spring context with an {@code XSwapper} because mappings of more than 1000 rows are
 * stored in an {@code XIntFragment}. Mappings stay below 65536 rows (see #82040).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubDataSetNestedRowTest {
   // header {k, g}; row i = {i, "A" for even i, "B" for odd i}
   private static DefaultDataSet base(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "k", "g" };

      for(int i = 0; i < rows; i++) {
         data[i + 1] = new Object[] { i, i % 2 == 0 ? "A" : "B" };
      }

      return new DefaultDataSet(data);
   }

   private static int[] range(int start, int end) {
      int[] arr = new int[end - start];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = start + i;
      }

      return arr;
   }

   // brute force: walk the SubDataSet levels down to the root row of each row of sub
   private static int expected(SubDataSet sub, int root) {
      int below = 0;

      for(int r = 0; r < sub.getRowCount(); r++) {
         int rr = r;
         DataSet ds = sub;

         while(ds instanceof SubDataSet) {
            rr = ((SubDataSet) ds).getBaseRow(rr);
            ds = ((SubDataSet) ds).getDataSet();
         }

         if(rr == root) {
            return r;
         }

         if(rr < root) {
            below++;
         }
      }

      return -below - 1;
   }

   private static void assertAllRoots(SubDataSet sub, int rootCount) {
      for(int x = -1; x <= rootCount; x++) {
         assertEquals(expected(sub, x), sub.getRowFromBase(x), "root row " + x);
      }
   }

   @Test
   void flatSubDataSetKeepsBinarySearchResult() {
      SubDataSet flat = new SubDataSet(base(5), new int[] { 2, 3 });
      int[] expected = { -1, -1, 0, 1, -3 };

      for(int x = 0; x < expected.length; x++) {
         assertEquals(expected[x], flat.getRowFromBase(x), "root row " + x);
      }

      assertAllRoots(flat, 5);
   }

   @Test
   void nestedIntArrayMapsRootRowThroughParent() {
      SubDataSet sub1 = new SubDataSet(base(5), new int[] { 1, 2, 3, 4 });
      // sub2 rows 0,1 = sub1 rows 1,2 = root rows 2,3
      SubDataSet sub2 = new SubDataSet(sub1, new int[] { 1, 2 });
      int[] expected = { -1, -1, 0, 1, -3 };

      assertEquals(2, sub2.getData("k", 0));
      assertEquals(3, sub2.getData("k", 1));

      for(int x = 0; x < expected.length; x++) {
         assertEquals(expected[x], sub2.getRowFromBase(x), "root row " + x);
      }

      assertAllRoots(sub2, 5);
   }

   @Test
   void nestedFragmentMappingMapsRootRowThroughParent() {
      // both levels have more than 1000 rows, so both mappings are XIntFragment (mapping2)
      SubDataSet sub1 = new SubDataSet(base(3000), range(1000, 3000));
      // sub2 rows = sub1 rows 500..1999 = root rows 1500..2999
      SubDataSet sub2 = new SubDataSet(sub1, range(500, 2000));

      assertEquals(-1, sub2.getRowFromBase(500));
      assertEquals(-1, sub2.getRowFromBase(1000));
      assertEquals(-1, sub2.getRowFromBase(1499));
      assertEquals(0, sub2.getRowFromBase(1500));
      assertEquals(1, sub2.getRowFromBase(1501));
      assertEquals(1499, sub2.getRowFromBase(2999));
      assertEquals(-1501, sub2.getRowFromBase(3000));

      for(int x : new int[] { -1, 0, 999, 1000, 1499, 1500, 2000, 2500, 2999, 3000 }) {
         assertEquals(expected(sub2, x), sub2.getRowFromBase(x), "root row " + x);
      }
   }

   @Test
   void nestedIdenticalSubDataSetMapsRootRowThroughParent() {
      Object[][] data = { { "k", "g" }, { 0, "Y" }, { 1, "X" }, { 2, "X" }, { 3, "X" }, { 4, "X" } };
      SubDataSet sub1 = new SubDataSet(new DefaultDataSet(data), new int[] { 1, 2, 3, 4 });
      // every sub1 row matches, so sub2 passes through sub1 (identical)
      SubDataSet sub2 = new SubDataSet(sub1, Collections.singletonMap("g", "X"));
      int[] expected = { -1, 0, 1, 2, 3 };

      assertEquals(4, sub2.getRowCount());

      for(int x = 0; x < expected.length; x++) {
         assertEquals(expected[x], sub2.getRowFromBase(x), "root row " + x);
      }

      assertAllRoots(sub2, 5);
   }

   @Test
   void threeLevelsMapRootRowThroughEveryParent() {
      SubDataSet sub1 = new SubDataSet(base(10), new int[] { 1, 2, 4, 6, 8, 9 });
      SubDataSet sub2 = new SubDataSet(sub1, new int[] { 1, 2, 3 }); // roots 2, 4, 6
      SubDataSet sub3 = new SubDataSet(sub2, new int[] { 1, 2 });    // roots 4, 6

      assertEquals(0, sub3.getRowFromBase(4));
      assertEquals(1, sub3.getRowFromBase(6));
      assertEquals(-1, sub3.getRowFromBase(2));
      assertEquals(-2, sub3.getRowFromBase(5));
      assertEquals(-3, sub3.getRowFromBase(9));

      assertAllRoots(sub3, 10);
   }
}
