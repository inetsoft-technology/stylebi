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
package inetsoft.util;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ListWithFastLookupTest {
   @Before
   public void before() {
      list = new ListWithFastLookup<>(Arrays.asList("a", "b", "c"));
   }

   @Test
   public void add() {
      list.add("d");
      assertEquals("d", list.get(3));
      assertEquals(4, list.size());
   }

   @Test
   public void set() {
      list.set(0, "d");
      assertEquals("d", list.get(0));
      assertEquals(3, list.size());
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void setOutOfRange() {
      list.set(3, "d");
   }

   @Test
   public void indexedAdd() {
      list.add(1, "d");
      assertEquals(Arrays.asList("a", "d", "b", "c"), list);
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void indexAddOutOfRange() {
      list.add(4, "d");
   }

   @Test
   public void removeIndex() {
      list.remove(1);
      assertEquals(Arrays.asList("a", "c"), list);
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void removeIndexOutOfRange() {
      list.remove(3);
   }

   @Test
   public void indexOfFound() {
      assertEquals(1, list.indexOf("b"));
   }

   @Test
   public void indexOfNotFound() {
      assertEquals(-1, list.indexOf("d"));
   }

   @Test
   public void clear() {
      list.clear();
      assertTrue(list.isEmpty());
   }

   @Test
   public void indexedAddAll() {
      list.addAll(1, Arrays.asList("d", "e"));
      assertEquals(Arrays.asList("a", "d", "e", "b", "c"), list);
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void indexAddAllOutOfRange() {
      list.addAll(4, Collections.emptyList());
   }

   @Test
   public void contains() {
      assertTrue(list.contains("a"));
      assertFalse(list.contains("d"));
   }

   @Test
   public void removeObject() {
      assertTrue(list.remove("a"));
      assertFalse(list.remove("a"));
      assertEquals(2, list.size());
   }

   @Test
   public void containsAll() {
      assertTrue(list.containsAll(Arrays.asList("a", "b")));
      assertFalse(list.containsAll(Arrays.asList("a", "d")));
   }

   @Test
   public void addAll() {
      list.addAll(Arrays.asList("d", "e"));
      assertEquals(Arrays.asList("a", "b", "c", "d", "e"), list);
   }

   @Test
   public void removeAll() {
      list.removeAll(Arrays.asList("a", "a", "d", "e", "c"));
      assertEquals(Collections.singletonList("b"), list);
   }

   @Test
   public void retainAll() {
      list.retainAll(Arrays.asList("a", "a", "c", "d"));
      assertEquals(Arrays.asList("a", "c"), list);
   }

   @Test
   public void get() {
      assertEquals("a", list.get(0));
      assertEquals("b", list.get(1));
      assertEquals("c", list.get(2));
   }

   @Test(expected = IndexOutOfBoundsException.class)
   public void getOutOfRange() {
      list.get(3);
   }

   @Test
   public void size() {
      assertEquals(3, list.size());
      list.add("d");
      assertEquals(4, list.size());
      list.clear();
      assertEquals(0, list.size());
   }

   private ListWithFastLookup<String> list;
}
