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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BidiMapTest {

   private BidiMap<String, Integer> map;

   @BeforeEach
   void setUp() {
      map = new BidiMap<>();
   }

   @Test
   void putAndGetForwardLookup() {
      map.put("alpha", 1);
      assertEquals(1, map.get("alpha"));
   }

   @Test
   void getKeyReverseeLookup() {
      map.put("alpha", 1);
      assertEquals("alpha", map.getKey(1));
   }

   @Test
   void overwriteExistingKeyUpdatesForwardAndNewReverseEntry() {
      map.put("alpha", 1);
      map.put("alpha", 2);

      // Forward lookup reflects the new value
      assertEquals(2, map.get("alpha"));
      // New value reverse-maps back to the key
      assertEquals("alpha", map.getKey(2));
      // NOTE: BidiMap.put() does not remove the stale old-value→key reverse entry;
      // the old value 1 still has a reverse mapping to "alpha" in the value map.
      assertEquals("alpha", map.getKey(1));
   }

   @Test
   void removeByKeyRemovesBothDirections() {
      map.put("alpha", 1);
      map.remove("alpha");

      assertNull(map.get("alpha"));
      assertNull(map.getKey(1));
      assertFalse(map.containsKey("alpha"));
      assertFalse(map.containsValue(1));
   }

   @Test
   void removeValueRemovesBothDirections() {
      map.put("alpha", 1);
      map.removeValue(1);

      assertNull(map.get("alpha"));
      assertNull(map.getKey(1));
      assertFalse(map.containsKey("alpha"));
      assertFalse(map.containsValue(1));
   }

   @Test
   void containsKeyAndContainsValue() {
      map.put("alpha", 1);

      assertTrue(map.containsKey("alpha"));
      assertTrue(map.containsValue(1));
      assertFalse(map.containsKey("beta"));
      assertFalse(map.containsValue(99));
   }

   @Test
   void sizeConsistencyAfterOperations() {
      assertEquals(0, map.size());

      map.put("a", 1);
      map.put("b", 2);
      assertEquals(2, map.size());

      map.remove("a");
      assertEquals(1, map.size());

      map.removeValue(2);
      assertEquals(0, map.size());
   }

   @Test
   void clearRemovesAllEntries() {
      map.put("a", 1);
      map.put("b", 2);
      map.clear();

      assertEquals(0, map.size());
      assertTrue(map.isEmpty());
      assertNull(map.get("a"));
      assertNull(map.getKey(1));
   }

   @Test
   void keySetContainsAllKeys() {
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      Set<String> keys = map.keySet();
      assertEquals(3, keys.size());
      assertTrue(keys.contains("a"));
      assertTrue(keys.contains("b"));
      assertTrue(keys.contains("c"));
   }

   @Test
   void valuesContainsAllValues() {
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      Collection<Integer> values = map.values();
      assertEquals(3, values.size());
      assertTrue(values.contains(1));
      assertTrue(values.contains(2));
      assertTrue(values.contains(3));
   }

   @Test
   void getMissingKeyReturnsNull() {
      assertNull(map.get("nonexistent"));
   }

   @Test
   void getMissingValueReturnsNull() {
      assertNull(map.getKey(99));
   }
}
