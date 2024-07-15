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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

class PagedSeqListTest {
   @Test
   public void sorting() {
      final PagedSeqList<Integer> list = new PagedSeqList<>(2, Comparator.naturalOrder());

      try {
         list.add(5);
         list.add(4);
         list.add(3);
         list.add(2);
         list.add(1);

         Assertions.assertEquals(5, list.size());
         list.complete();
         Assertions.assertEquals(5, list.size());

         final Iterator<Integer> iterator = list.iterator();
         final ArrayList<Integer> result = new ArrayList<>();
         iterator.forEachRemaining(result::add);

         Assertions.assertEquals(Arrays.asList(1, 2, 3, 4, 5), result);
      }
      finally {
         list.dispose();
      }
   }

   @Test
   public void noSorting() {
      final PagedSeqList<Integer> list = new PagedSeqList<>(1);

      try {
         list.add(5);
         list.add(4);
         list.add(3);
         list.add(2);
         list.add(1);
         list.complete();

         final Iterator<Integer> iterator = list.iterator();
         final ArrayList<Integer> result = new ArrayList<>();
         iterator.forEachRemaining(result::add);

         Assertions.assertEquals(Arrays.asList(5, 4, 3, 2, 1), result);
      }
      finally {
         list.dispose();
      }
   }

   @Test
   public void iteratorRemove() {
      final PagedSeqList<Integer> list = new PagedSeqList<>(1);

      try {
         list.add(1);
         list.add(2);
         list.add(3);
         list.add(4);
         list.add(5);
         list.complete();

         final Iterator<Integer> iterator = list.iterator();
         iterator.next(); // 1
         iterator.remove(); // removed 1
         iterator.next(); // 2
         iterator.next(); // 3
         iterator.remove(); // removed 3
         iterator.next(); // 4
         iterator.next(); // 5
         iterator.remove(); // removed 5

         final Iterator<Integer> iterator2 = list.iterator();

         Assertions.assertEquals(2, iterator2.next());
         Assertions.assertEquals(4, iterator2.next());
         Assertions.assertFalse(iterator2.hasNext());
      }
      finally {
         list.dispose();
      }
   }

   @Test
   public void sortedMergeWhenInternalListIsEmpty() {
      final PagedSeqList<Integer> list = new PagedSeqList<>(1, Comparator.naturalOrder());

      try {
         list.add(2);
         list.add(3);
         list.add(1);
         list.add(5);
         list.add(4);
         list.complete();

         final Iterator<Integer> iterator = list.iterator();
         Assertions.assertEquals(1, (int) iterator.next());
         Assertions.assertEquals(2, (int) iterator.next());
      }
      finally {
         list.dispose();
      }
   }
}
