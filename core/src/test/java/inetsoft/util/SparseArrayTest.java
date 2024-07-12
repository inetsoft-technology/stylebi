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
package inetsoft.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SparseArrayTest {
   @Test
   public void typicalBehavior() {
      final SparseArray<String> strings = new SparseArray<>();
      strings.set(0, "zero");
      strings.set(2, "two");
      strings.set(1, "one");
      strings.set(100, "hundred");

      assertEquals("zero", strings.get(0));
      assertEquals("one", strings.get(1));
      assertEquals("two", strings.get(2));
      assertEquals("hundred", strings.get(100));

      for(int i = 3; i < 100; i++) {
         assertNull(strings.get(i));
      }

      assertNull(strings.get(200));

      final List<String> expected = Arrays.asList("zero", "one", "two", "hundred");
      final ArrayList<String> actual = new ArrayList<>();
      strings.forEach(actual::add);

      assertEquals(expected, actual);
   }

   @Test
   public void outOfBounds() {
      final SparseArray<String> strings = new SparseArray<>();

      assertThrows(ArrayIndexOutOfBoundsException.class, () -> strings.set(-1, ""));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> strings.get(-1));
   }
}
