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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OrderedMap} keeps keys in insertion order. The oracle for the ordinary
 * operations is {@link LinkedHashMap}; keys are never null in the oracle runs.
 */
@Tag("core")
class OrderedMapTest {
   private static void assertSameAs(LinkedHashMap<String, Integer> expected,
                                    OrderedMap<String, Integer> actual, String step)
   {
      assertEquals(expected.size(), actual.size(), step + ": size");
      assertEquals(expected.isEmpty(), actual.isEmpty(), step + ": isEmpty");
      assertEquals(new ArrayList<>(expected.keySet()), actual.keyList(), step + ": key order");
      assertEquals(new ArrayList<>(expected.values()), new ArrayList<>(actual.values()),
                   step + ": value order");

      int i = 0;

      for(Map.Entry<String, Integer> e : expected.entrySet()) {
         assertEquals(e.getKey(), actual.getKey(i), step + ": getKey " + i);
         assertEquals(e.getValue(), actual.getValue(i), step + ": getValue " + i);
         assertEquals(i, actual.indexOf(e.getKey()), step + ": indexOf " + e.getKey());
         assertEquals(e.getValue(), actual.get(e.getKey()), step + ": get " + e.getKey());
         assertTrue(actual.containsKey(e.getKey()), step + ": containsKey");
         i++;
      }

      List<String> viaEntries = new ArrayList<>();

      for(Map.Entry<String, Integer> e : actual.entrySet()) {
         viaEntries.add(e.getKey() + "=" + e.getValue());
      }

      List<String> expectedEntries = new ArrayList<>();

      for(Map.Entry<String, Integer> e : expected.entrySet()) {
         expectedEntries.add(e.getKey() + "=" + e.getValue());
      }

      assertEquals(expectedEntries, viaEntries, step + ": entrySet order");
   }

   /** Random puts and removes, compared with the oracle after every step. */
   @Test
   void putGetRemoveMatchLinkedHashMap() {
      Random rnd = new Random(20261124);
      OrderedMap<String, Integer> map = new OrderedMap<>();
      LinkedHashMap<String, Integer> oracle = new LinkedHashMap<>();

      for(int step = 0; step < 600; step++) {
         String key = "k" + rnd.nextInt(25);
         int op = rnd.nextInt(10);

         if(op < 6) {
            // a null value is allowed and must be kept apart from "absent"
            Integer value = rnd.nextInt(8) == 0 ? null : rnd.nextInt(1000);
            map.put(key, value);
            oracle.put(key, value);
         }
         else if(op < 9) {
            Integer removed = map.remove((Object) key);
            Integer expected = oracle.remove(key);
            assertEquals(expected, removed, "step " + step + ": removed value");
         }
         else if(rnd.nextInt(4) == 0) {
            map.clear();
            oracle.clear();
         }

         assertSameAs(oracle, map, "step " + step);
      }
   }

   @Test
   void indexOfAnAbsentKeyIsMinusOne() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);

      assertEquals(-1, map.indexOf("nope"));
      assertEquals(0, map.indexOf("a"));
   }

   @Test
   void updatingAnExistingKeyKeepsItsPosition() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      map.put("a", 10);

      assertEquals(Arrays.asList("a", "b", "c"), map.keyList());
      assertEquals(Integer.valueOf(10), map.get("a"));
      assertEquals(3, map.size());
   }

   @Test
   void nullValueIsStoredAndDistinguishedFromAbsent() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", null);

      assertTrue(map.containsKey("a"));
      assertNull(map.get("a"));
      assertTrue(map.containsValue(null));
      assertFalse(map.containsKey("b"));
      assertEquals(1, map.size());
   }

   @Test
   void removeByIndexReturnsTheValueAndClosesTheGap() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      assertEquals(Integer.valueOf(2), map.remove(1));

      assertEquals(Arrays.asList("a", "c"), map.keyList());
      assertFalse(map.containsKey("b"));
      assertEquals(2, map.size());
      assertEquals(1, map.indexOf("c"));
   }

   @Test
   void putAtIndexInsertsAtThatPosition() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("c", 3);

      map.put(1, "b", 2);

      assertEquals(Arrays.asList("a", "b", "c"), map.keyList());
      assertEquals(Integer.valueOf(2), map.get("b"));
      assertEquals(3, map.size());
   }

   @Test
   void exchangeSwapsTwoEntriesAndKeepsLookupConsistent() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      map.exchange(0, 2);

      assertEquals(Arrays.asList("c", "b", "a"), map.keyList());
      assertEquals(Arrays.asList(3, 2, 1), new ArrayList<>(map.values()));
      assertEquals(Integer.valueOf(1), map.get("a"));
      assertEquals(2, map.indexOf("a"));
   }

   @Test
   void sortOrdersKeysNaturallyAndKeepsValuesWithTheirKeys() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("pear", 3);
      map.put("apple", 1);
      map.put("fig", 2);

      map.sort();

      assertEquals(Arrays.asList("apple", "fig", "pear"), map.keyList());
      assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(map.values()));
      assertEquals(Integer.valueOf(3), map.get("pear"));
   }

   @Test
   void sortWithComparatorUsesTheComparatorOnKeys() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("bb", 2);
      map.put("a", 1);
      map.put("ccc", 3);

      map.sort(Comparator.comparing(Object::toString, Comparator.reverseOrder()));

      assertEquals(Arrays.asList("ccc", "bb", "a"), map.keyList());
      assertEquals(Arrays.asList(3, 2, 1), new ArrayList<>(map.values()));
   }

   @Test
   void cloneIsIndependentOfTheOriginal() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);

      @SuppressWarnings("unchecked")
      OrderedMap<String, Integer> copy = (OrderedMap<String, Integer>) map.clone();
      copy.put("c", 3);
      copy.remove((Object) "a");

      assertEquals(Arrays.asList("a", "b"), map.keyList());
      assertEquals(Arrays.asList("b", "c"), copy.keyList());
      assertNotEquals(map, copy);
   }

   @Test
   void equalsAndHashCodeDependOnOrderAndContent() {
      OrderedMap<String, Integer> a = new OrderedMap<>();
      a.put("x", 1);
      a.put("y", 2);
      OrderedMap<String, Integer> same = new OrderedMap<>(a);
      OrderedMap<String, Integer> reordered = new OrderedMap<>();
      reordered.put("y", 2);
      reordered.put("x", 1);

      assertEquals(a, same);
      assertEquals(a.hashCode(), same.hashCode());
      assertNotEquals(a, reordered, "same entries in another order are a different ordered map");
   }

   @Test
   void putAllKeepsTheSourceIterationOrder() {
      LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
      source.put("z", 1);
      source.put("m", 2);
      source.put("a", 3);
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("q", 0);

      map.putAll(source);

      assertEquals(Arrays.asList("q", "z", "m", "a"), map.keyList());
   }

   /** The entry iterator supports remove and keeps the three structures in step. */
   @Test
   void entryIteratorRemoveKeepsTheMapConsistent() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
      it.next();
      it.next();
      it.remove();

      assertEquals(Arrays.asList("a", "c"), map.keyList());
      assertEquals(Arrays.asList(1, 3), new ArrayList<>(map.values()));
      assertFalse(map.containsKey("b"));
      assertEquals(2, map.size());
   }

   @Test
   void appendOfAnExistingKeyMovesItToTheEnd() {
      OrderedMap<String, Integer> map = new OrderedMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      Integer previous = map.append("a", 10);

      assertEquals(Integer.valueOf(1), previous);
      assertEquals(Arrays.asList("b", "c", "a"), map.keyList());
      assertEquals(Arrays.asList(2, 3, 10), new ArrayList<>(map.values()));
   }
}
